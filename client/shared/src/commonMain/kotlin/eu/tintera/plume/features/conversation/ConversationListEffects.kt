package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.accounts.AccountSwitchSnackbar
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.conversation.items.ConversationItem
import eu.tintera.plume.features.conversation.items.DayDividerItem
import eu.tintera.plume.features.conversation.items.SPONSORED_KEY_PREFIX
import eu.tintera.plume.features.conversation.items.UnreadDividerItem
import eu.tintera.plume.features.proxy.ProxyLinkHost
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_undo
import eu.tintera.plume.resources.conversation_unpinned
import eu.tintera.plume.resources.media_video_from_start
import eu.tintera.plume.resources.media_video_resumed
import eu.tintera.plume.resources.spam_bar_contact_added
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlášky ve snackbaru: chyba po akci, oznámení od ViewModelu, odepnutí a přidaný kontakt
 * s „Vrátit", nahlášení a odkaz na proxy.
 */
@Composable
internal fun ConversationSnackbars(
    state: ConversationState,
    viewModel: ConversationViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val haptics = LocalHapticFeedback.current
    ReportHost(viewModel.reportFlow, snackbarHostState)
    ReportHost(viewModel.sponsoredReportFlow, snackbarHostState)
    ProxyLinkHost(viewModel.proxyLinkFlow, snackbarHostState)
    AccountSwitchSnackbar(viewModel.chatId, snackbarHostState)

    // Snackbar o odepnutí se skládá v efektu, kde `stringResource` volat nejde — text se proto
    // přečte tady v kompozici.
    val unpinnedMessage = stringResource(Res.string.conversation_unpinned)
    val contactAddedMessage = stringResource(Res.string.spam_bar_contact_added)
    val undoLabel = stringResource(Res.string.conversation_undo)
    val fromStartLabel = stringResource(Res.string.media_video_from_start)

    // Chyby se dřív zahazovaly: `state.error` se vykreslovalo jen u prázdné konverzace, takže
    // neúspěšná reakce nebo odeslání zmizely bez jediné stopy.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        // Chyba po akci uživatele je jediné místo, kde odezva hlásí NEúspěch — snackbar se dá
        // přehlédnout, hlavně u odeslání, které mezitím vypadá jako hotové.
        haptics.reject()
        snackbarHostState.showSnackbar(message.resolveNow())
        viewModel.dismissError()
    }

    // Snackbar běží v `snackbarScope`, ne v těle efektu. Vynulování `unpinned` změní klíč efektu,
    // ten se restartuje a zrušil by i snackbar, který se právě ukazuje — takže se neukázal nikdy.
    val snackbarScope = rememberCoroutineScope()

    // Stejná past jako u `unpinned` níž: vynulování mění klíč efektu, takže snackbar běží mimo něj.
    // I text se čte AŽ TAM: `getString` je `suspend`, a dokud se četl v těle efektu, restart po
    // vynulování ho zrušil — hláška se ukázala, jen když byl text náhodou už načtený.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        snackbarScope.launch {
            val text = notice.resolveNow()
            snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(state.unpinned) {
        val unpinned = state.unpinned ?: return@LaunchedEffect
        // Vynulovat hned, ne až po zavření snackbaru — jinak by se po otočení ukázal znovu.
        viewModel.consumeUnpinned()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = unpinnedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoUnpin(unpinned)
        }
    }

    // Dlouhá nahrávka pokračuje z pamatované pozice; „Od začátku" ji vrátí na nulu. Stejný vzor
    // jako odepnutí — vynulovat hned, snackbar mimo efekt.
    LaunchedEffect(state.resumedAudio) {
        val resumed = state.resumedAudio ?: return@LaunchedEffect
        viewModel.consumeResumedAudio()
        snackbarScope.launch {
            val text = UiText(Res.string.media_video_resumed, formatDuration((resumed.fromMs / 1000).toInt())).resolveNow()
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = fromStartLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restartAudio(resumed.messageId)
        }
    }

    // Přidaný kontakt z karty ve zprávě: stejný vzor jako odepnutí — vynulovat hned, snackbar mimo efekt.
    LaunchedEffect(state.addedContact) {
        val userId = state.addedContact ?: return@LaunchedEffect
        viewModel.consumeAddedContact()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = contactAddedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoAddContact(userId)
        }
    }
}

/** Dotahování historie na obou koncích seznamu podle toho, kam uživatel doroloval. */
@Composable
internal fun ConversationPaging(
    listState: LazyListState,
    viewModel: ConversationViewModel,
    /** Seznam končí u přítomnosti; jinak je to okno v historii a dotahuje se i dolů. */
    reachedEnd: Boolean,
) {
    // `reverseLayout` obrací i význam indexů: index 0 je nejnovější zpráva dole, vyšší indexy
    // jsou starší nahoře. Stránkování do minulosti se tedy spouští na KONCI seznamu.
    val shouldLoadOlder by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadOlder }.collect { if (it) viewModel.loadOlder() }
    }

    /*
     * Okno v historii (po skoku na starou zprávu) se dotahuje i k PŘÍTOMNOSTI — na začátku
     * seznamu, tedy u spodní hrany. Jestli je okno v historii, ví ViewModel (`reachedEnd`);
     * obrazovka jen hlásí, že je uživatel blízko.
     */
    val shouldLoadNewer by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex < LOAD_MORE_THRESHOLD }
    }
    LaunchedEffect(listState, reachedEnd) {
        if (reachedEnd) return@LaunchedEffect
        snapshotFlow { shouldLoadNewer }.collect { if (it) viewModel.loadNewer() }
    }
}

/**
 * Nové zprávy dole: ukotvení seznamu u spodní hrany, když zpráva přijde (nebo „píše…"),
 * a počet cizích zpráv, které přišly, zatímco uživatel četl historii (odznak na tlačítku dolů).
 *
 * Vrací hodnotu, takže se nepřeskakuje a běží uvnitř kompozice obrazovky na tomtéž místě jako
 * dřív její kód — na tom stojí `requestScrollToItem` v kompozici (viz níž).
 */
@Composable
internal fun rememberNewMessages(listState: LazyListState, state: ConversationState): Int {
    // „Jsem dole" = vidím nejnovější zprávu. Při `reverseLayout` je to index 0; kontroluje se
    // i offset, aby se za dole nepovažoval stav, kdy je poslední bublina jen z půlky vidět.
    val atBottom by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    // Kolik cizích zpráv přišlo, zatímco uživatel četl historii.
    var pendingNewMessages by rememberSaveable { mutableIntStateOf(0) }
    var lastSeenMessageId by rememberSaveable { mutableStateOf<Long?>(null) }

    val newest = state.messages.firstOrNull()

    /*
     * Nová zpráva dole: seznam se ukotví u spodní hrany UŽ PŘI JEJÍM VLOŽENÍ.
     *
     * `LazyColumn` drží pozici podle klíče první viditelné položky. Nová zpráva na indexu 0 se
     * tedy vložila POD spodní hranu a teprve efekt o snímek později k ní dojížděl — bublina
     * nejdřív nebyla vidět, pak k ní seznam poskočil, a let ze vstupního pole mířil na cíl,
     * který se mu pod rukama posouval. `requestScrollToItem` platí pro nejbližší přeměření,
     * tedy pro tentýž layout, ve kterém se zpráva objeví; proto se volá v kompozici, ne v efektu.
     *
     * Hlídá se klíč, ne id: potvrzení odeslání mění id, ale novou zprávu nepřináší.
     */
    val anchor = remember { NewestItem() }
    if (newest != null) {
        val key = state.keyOf(newest)
        if (key != anchor.key) {
            // První naplnění seznamu není „nová zpráva", jen se zobrazila historie. Smazání
            // nejnovější zprávy klíč taky změní, ale id přitom klesne.
            // Dotažená stránka k přítomnosti (okno v historii) taky posune nejnovější klíč, ale
            // nic nepřišlo — `live` je, jestli předchozí stav končil u přítomnosti.
            val arrived = anchor.key != null && newest.id > anchor.id && anchor.live
            anchor.lastWasArrival = arrived
            // Vlastní zpráva dolů vždycky — jinak by odeslání vypadalo, že se nic nestalo.
            // Cizí jen když je uživatel dole; při čtení historie by ho to vytrhlo ze čtení.
            if (arrived && (newest.isOutgoing || atBottom)) listState.requestScrollToItem(0)
            anchor.key = key
            anchor.id = newest.id
        }
        anchor.live = state.reachedEnd
    }

    /*
     * „Píše…" se vkládá pod nejnovější zprávu, a seznam drží pozici podle první viditelné
     * položky — tou je zpráva, takže by se indikátor vložil POD spodní hranu a nebyl by vidět.
     * Kdo je dole, tomu se seznam ukotví i s ním; kdo čte historii, toho nevytrhne.
     */
    // Rozepsaná odpověď bota stojí na tomtéž místě a platí pro ni totéž.
    val typingShown = (state.typing != null || state.pendingReply != null) && state.reachedEnd
    val typingAnchor = remember { booleanArrayOf(false) }
    if (typingShown != typingAnchor[0]) {
        if (typingShown && atBottom) listState.requestScrollToItem(0)
        typingAnchor[0] = typingShown
    }

    val newestMessageId = newest?.id

    LaunchedEffect(newestMessageId) {
        val newestId = newestMessageId ?: return@LaunchedEffect
        val previous = lastSeenMessageId
        lastSeenMessageId = newestId

        // První naplnění seznamu není „nová zpráva", jen se zobrazila historie.
        if (previous == null) return@LaunchedEffect
        if (newestId <= previous) return@LaunchedEffect

        // Stránka dotažená k přítomnosti není „nové zprávy" — odznak by lhal.
        if (!anchor.lastWasArrival) return@LaunchedEffect

        // Id zpráv v chatu rostou, takže „nové" jsou ty nad posledním viděným.
        val arrived = state.messages.takeWhile { it.id > previous }

        // Scroll už obstaral `requestScrollToItem` výš; tady se jen počítá odznak.
        when {
            arrived.any { it.isOutgoing } || atBottom -> pendingNewMessages = 0

            // Čtu historii: NEscrollovat, vytrhlo by mě to ze čtení. Místo toho odznak dole.
            else -> pendingNewMessages += arrived.count { !it.isOutgoing }
        }
    }

    // Jakmile se uživatel dostane dolů, odznak nemá co hlásit.
    LaunchedEffect(atBottom) {
        if (atBottom) pendingNewMessages = 0
    }

    return pendingNewMessages
}

/** Skok na zprávu ze stavu (`pendingJump`, `scrollTarget`): dojetí, nebo přejezd přes [JumpTransition]. */
@Composable
internal fun ConversationJumps(
    state: ConversationState,
    items: List<ConversationItem>,
    listState: LazyListState,
    jumpTransition: JumpTransition,
    viewModel: ConversationViewModel,
) {
    /*
     * Skok na zprávu — první nepřečtenou, nebo citovanou.
     *
     * Zprávu hledáme podle id, ne podle indexu — než efekt doběhne, může seznam narůst o novou
     * zprávu a index by ukazoval jinam.
     */
    val jumpSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val leaveMoveSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val leaveFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val gatherSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val restoreSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val leaveDistance = with(LocalDensity.current) { JUMP_LEAVE_DISTANCE.toPx() }
    val gatherDistance = with(LocalDensity.current) { JUMP_GATHER_DISTANCE.toPx() }
    // Seznam se během dojíždění mění (dotahují se stránky), takže se cíl hledá v AKTUÁLNÍM seznamu.
    val currentItems by rememberUpdatedState(items)
    val indexByKey = remember(items) { items.withIndex().associate { (index, item) -> item.key to index } }
    jumpTransition.indexOf = { indexByKey[it] ?: -1 }

    /*
     * Skok na nenačtenou zprávu: seznam začne odjíždět HNED, načítání okna kolem zprávy běží
     * mezitím. Když se zpráva nenajde, seznam se vrátí.
     */
    LaunchedEffect(state.pendingJump) {
        val pending = state.pendingJump
        when {
            state.scrollTarget != null -> Unit
            pending != null -> jumpTransition.leave(
                distance = if (pending.older) leaveDistance else -leaveDistance,
                moveSpec = leaveMoveSpec,
                fadeSpec = leaveFadeSpec,
            )
            else -> jumpTransition.restore(restoreSpec)
        }
    }

    LaunchedEffect(state.scrollTarget) {
        val target = state.scrollTarget ?: return@LaunchedEffect
        // Hledá se BLOK, ne zpráva: fotka z alba je v seznamu součástí jedné položky.
        var index = items.indexOfFirst { it.containsMessage(target.messageId) }
        // Nahoru se posazuje i předěl nad zprávou — u první nepřečtené je to právě to, co má
        // uživatel vidět. Předěl je v obráceném seznamu ZA zprávou.
        if (index >= 0 && target.anchor == ScrollAnchor.Top) {
            while (items.getOrNull(index + 1).let { it is UnreadDividerItem || it is DayDividerItem }) index++
        }
        /*
         * Na citaci a mezi výsledky hledání se DOJÍŽDÍ (`animateToAnchor`), ke vzdálené nebo
         * nenačtené zprávě se PŘEJEDE (`JumpTransition`). První nepřečtená při otevření chatu
         * a návrat dolů skočí hned.
         */
        val findCurrent = { currentItems.indexOfFirst { it.containsMessage(target.messageId) } }
        when {
            index < 0 -> jumpTransition.reset()
            target.anchor != ScrollAnchor.Center -> {
                jumpTransition.reset()
                listState.scrollToAnchor(index, target.anchor)
            }
            target.animate && listState.itemsAwayFrom(index) <= MAX_ANIMATED_JUMP_ITEMS -> {
                jumpTransition.reset()
                listState.animateToAnchor(index, target.anchor, jumpSpec, findCurrent)
            }
            else -> {
                // Starší zprávy jsou nahoře, obsah tedy odjíždí dolů.
                val older = target.older ?: (index > listState.firstVisibleItemIndex)
                jumpTransition.leave(
                    distance = if (older) leaveDistance else -leaveDistance,
                    moveSpec = leaveMoveSpec,
                    fadeSpec = leaveFadeSpec,
                )
                val now = findCurrent().takeIf { it >= 0 } ?: index
                listState.scrollToAnchor(now, target.anchor)
                currentItems.getOrNull(now)?.let {
                    jumpTransition.arrive(it.key, now, gatherDistance, gatherSpec)
                }
            }
        }
        viewModel.consumeScrollTarget()
    }
}

/**
 * Co uživatel na obrazovce viděl: přečtené zprávy a celé reklamy. Stav se čte přes
 * [stateHolder], ne jako hodnota — efekty běží přes mnoho změn stavu a musí vidět ten aktuální.
 */
@Composable
internal fun ConversationViewReporting(
    listState: LazyListState,
    stateHolder: State<ConversationState>,
    viewModel: ConversationViewModel,
) {
    // Zhlédnutí reklamy se hlásí, teprve až je karta na obrazovce CELÁ. TDLib to vyžaduje
    // („když je vidět celý text"); hlásit ji při prvním pixelu by znamenalo vykazovat
    // inzerentovi zhlédnutí, která se nestala. Beru přísnější podmínku — celá karta včetně
    // tlačítka — protože rozměr samotného textu odsud nezjistím.
    val sponsored = stateHolder.value.sponsored
    LaunchedEffect(listState, sponsored) {
        if (sponsored.isEmpty()) return@LaunchedEffect

        snapshotFlow {
            val info = listState.layoutInfo
            // Bez odsazení seznamu: pod pruhy, polem ani hlavičkou na skle karta vidět není.
            val start = info.viewportStartOffset + info.beforeContentPadding
            val end = info.viewportEndOffset - info.afterContentPadding
            info.visibleItemsInfo
                .filter { item -> item.offset >= start && item.offset + item.size <= end }
                .mapNotNull { it.key as? String }
                .filter { it.startsWith(SPONSORED_KEY_PREFIX) }
                .toSet()
        }
            .debounce(SPONSORED_VIEW_DEBOUNCE_MS)
            .collect { keys ->
                keys.forEach { key ->
                    key.removePrefix(SPONSORED_KEY_PREFIX).toLongOrNull()
                        ?.let(viewModel::reportSponsoredSeen)
                }
            }
    }

    /*
     * Hlášení přečtení.
     *
     * Bere se **klíč položky, ne její index**. Index do `state.messages` nesedí: před zprávami
     * stojí sponzorované položky, takže v kanálu s reklamou byl výběr posunutý o jejich počet
     * a hlásily se za přečtené zprávy, které uživatel neviděl. Klíč zpráv je rovnou jejich id,
     * takže se nemá co rozejít, ani až seznam další položku přibere.
     *
     * Odpočet čeká, až se scrollování uklidní — jinak by při rychlém průletu odešla jedna žádost
     * na každý snímek. Efekt proto **nesmí záviset na `state.messages`**: s ním se restartoval
     * při každé nové zprávě a odpočet začínal znovu od nuly.
     *
     * Hlásí se ZMĚNA, a proto se vedle viditelných položek čtou i zprávy s nepřečtenou reakcí
     * nebo hlasem v anketě (`unreadMarkedIds`). Reakce na zprávu, která už je vidět, viditelné
     * položky nezmění — bez toho by zůstala nepřečtená a tlačítko „❤" by viselo nad zprávou,
     * na kterou se uživatel právě dívá, dokud by nezaroloval.
     */
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } to stateHolder.value.unreadMarkedIds()
        }
            .debounce(READ_DEBOUNCE_MS)
            .collect { (keys, _) -> viewModel.markRead(keys) }
    }

    /*
     * Kde uživatel stojí, pro přepnutí účtu ve spojené společné skupině
     * (`ConversationViewModel.positionForAccountSwitch`): nejvyšší CELÁ viditelná zpráva (při
     * `reverseLayout` je nahoře nejvyšší index), jinak ta nahoře useknutá. Klíč zprávy je její id;
     * jiné položky (reklama, předěl) id nemají.
     */
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val messages = info.visibleItemsInfo.filter { it.key is Long }
            val top = messages.lastOrNull { it.offset + it.size <= info.viewportEndOffset } ?: messages.lastOrNull()
            (top?.key as Long?) to (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
        }
            .distinctUntilChanged()
            .collect { (top, atBottom) -> viewModel.reportVisibleTop(top, atBottom) }
    }

    /*
     * Body návratu po skoku na citaci (`JumpReturns.kt`): které zprávy, ke kterým by tlačítko
     * dolů vracelo, uživatel znovu vidí nebo minul. Jen MIMO skok — v okamžiku ťuknutí na citaci
     * je zdrojová zpráva vidět vždycky a během jízdy jí seznam projíždí; bod by zmizel dřív, než
     * by se kamkoli jelo. `scrollTarget` se maže až po doskočení (`consumeScrollTarget`).
     */
    LaunchedEffect(listState) {
        snapshotFlow {
            val state = stateHolder.value
            if (state.returnPoints.isEmpty() || state.scrollTarget != null || state.pendingJump != null) {
                null
            } else {
                // Vidět = aspoň kus mimo odsazení seznamu. Pod vstupním polem a lištou je položka
                // pro seznam „viditelná", ale uživatel ji nevidí — jako u metrik zobrazení.
                val info = listState.layoutInfo
                val start = info.viewportStartOffset + info.beforeContentPadding
                val end = info.viewportEndOffset - info.afterContentPadding
                info.visibleItemsInfo
                    .filter { it.offset + it.size > start && it.offset < end }
                    .mapNotNull { it.key as? Long }
                    .maxOrNull()
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect(viewModel::passReturnPoints)
    }

    ConversationViewMetrics(listState, viewModel)
}

/**
 * Metriky zobrazení zpráv (`sendMessageViewMetrics`), které TDLib PŘIKAZUJE posílat. Kdy a co,
 * počítá [MessageViewMetricsTracker]; tady se mu jen podávají snímky seznamu a čas.
 *
 * - **Viewport zaniká i odchodem aplikace do pozadí**, ne jen zavřením obrazovky: kompozice
 *   tam žije dál, ale nikdo se nedívá. Efekt proto běží jen ve stavu STARTED a při zastavení
 *   pošle rozpracované (`finally`).
 * - **Akcí uživatele je posun seznamu** (`isScrollInProgress`) — čtení je hlavně posouvání.
 *   Ťuknutí a psaní se nepočítají; zpráva čtená bez dotyku přes 15 s je tedy „neaktivní", což
 *   odpovídá tomu, k čemu TDLib aktivní čas chce (poznat odložený telefon).
 * - **Klíč položky není id zprávy** (u odeslaných se liší); na id ho převede ViewModel.
 */
@Composable
private fun ConversationViewMetrics(listState: LazyListState, viewModel: ConversationViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val started = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(listState, started) {
        if (!started) return@LaunchedEffect
        val tracker = MessageViewMetricsTracker()
        val clock = TimeSource.Monotonic.markNow()
        val now = { clock.elapsedNow().inWholeMilliseconds }
        try {
            launch {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val messages = info.visibleItemsInfo.mapNotNull { item ->
                        (item.key as? Long)?.let { VisibleMessage(it, item.offset, item.offset + item.size) }
                    }
                    // Pod lištou a vstupním polem (odsazení seznamu) zprávu nikdo nečte.
                    ViewportFrame(
                        messages = messages,
                        start = info.viewportStartOffset + info.beforeContentPadding,
                        end = info.viewportEndOffset - info.afterContentPadding,
                        scrolling = listState.isScrollInProgress,
                    )
                }.collect { frame ->
                    if (frame.scrolling) tracker.userAction(now())
                    viewModel.reportViewMetrics(tracker.frame(now(), frame.messages, frame.start, frame.end))
                }
            }
            // Seznam v klidu snímky nevydává, a zmizení ani pět minut se bez nich nepoznají.
            while (true) {
                delay(VIEW_METRICS_TICK_MS)
                viewModel.reportViewMetrics(tracker.tick(now()))
            }
        } finally {
            viewModel.reportViewMetrics(tracker.flush(now()))
        }
    }
}

/** Jeden snímek seznamu pro [MessageViewMetricsTracker]. */
private data class ViewportFrame(val messages: List<VisibleMessage>, val start: Int, val end: Int, val scrolling: Boolean)

/**
 * Naposledy zpracovaná nejnovější zpráva, pro ukotvení seznamu dole.
 *
 * Obyčejná třída, ne `mutableStateOf`: zapisuje se v kompozici a zápis do snapshot stavu by
 * obrazovku jen znovu rekomponoval, aniž by se tím cokoli změnilo.
 */
private class NewestItem {
    var key: Long? = null
    var id: Long = 0L

    /** Předchozí stav končil u přítomnosti — jen pak je novější nejnovější zpráva příchozí. */
    var live: Boolean = true

    /** Poslední změna nejnovější zprávy byla příchod, ne dotažená stránka. Čte ho odznak dole. */
    var lastWasArrival: Boolean = false
}

private const val LOAD_MORE_THRESHOLD = 5

private const val READ_DEBOUNCE_MS = 400L

/**
 * Jak často se metrikám zobrazení připíše čas, když se seznam nehýbe. Zmizení se tak nahlásí
 * nejpozději o tolik po 300 ms, které TDLib dává.
 */
private const val VIEW_METRICS_TICK_MS = 100L

/** Jak dlouho musí být reklama v klidu na obrazovce, než se zhlédnutí nahlásí. */
private const val SPONSORED_VIEW_DEBOUNCE_MS = 1000L
