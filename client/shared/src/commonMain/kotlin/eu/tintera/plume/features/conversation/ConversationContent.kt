package eu.tintera.plume.features.conversation

import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import eu.tintera.plume.features.conversation.items.LocalConversationTopInset
import eu.tintera.plume.features.media.LocalPhotoDescriptions
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.empty_state_error_title
import eu.tintera.plume.resources.conversation_empty_text
import eu.tintera.plume.core.ui.bubbleTextSelectable
import eu.tintera.plume.core.ui.fileDragSupported
import eu.tintera.plume.features.conversation.items.LocalMediaDragOut
import eu.tintera.plume.features.conversation.items.MediaDragOut
import eu.tintera.plume.features.conversation.items.LocalVideoDownload
import eu.tintera.plume.features.conversation.items.VideoDownload
import eu.tintera.plume.features.conversation.items.MessageAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.LocalSharedElementClip
import eu.tintera.plume.core.ui.SharedElementClip
import eu.tintera.plume.core.ui.SwipeDirection
import eu.tintera.plume.core.ui.detectHorizontalSwipe
import eu.tintera.plume.core.ui.rememberShownConnection
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedElementClipArea
import eu.tintera.plume.features.conversation.items.ConversationItem
import eu.tintera.plume.features.conversation.items.ConversationItemContent
import eu.tintera.plume.features.conversation.items.FloatingDayBadge
import eu.tintera.plume.features.conversation.items.MESSAGE_SPACING
import eu.tintera.plume.features.conversation.items.TIME_COLUMN_WIDTH
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.conversation_chat_restricted
import eu.tintera.plume.resources.conversation_chat_sensitive_show
import eu.tintera.plume.resources.conversation_chat_sensitive_text
import eu.tintera.plume.resources.conversation_chat_sensitive_title
import eu.tintera.plume.resources.conversation_empty
import eu.tintera.plume.resources.visibility_off_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Hlavní plocha konverzace: seznam zpráv, nebo to, co stojí místo něj (omezený chat, varování
 * u citlivého, načítání, úvod bota, prázdný chat), a plovoucí datum při rolování.
 */
@Composable
internal fun BoxScope.ConversationContent(
    state: ConversationState,
    viewModel: ConversationViewModel,
    items: List<ConversationItem>,
    itemScope: ScreenItemScope,
    listState: LazyListState,
    jumpTransition: JumpTransition,
    gestureHints: GestureHints,
    /** Odkrytí sloupce časů tahem doleva; čte se až při kreslení vrstvy. */
    timeReveal: Animatable<Float, AnimationVector1D>,
    listHaze: HazeState,
    listClip: SharedElementClip,
    /** Citlivý chat, u kterého uživatel ještě neřekl „přesto zobrazit". */
    sensitiveWarning: Boolean,
    onShowSensitive: () -> Unit,
    /** Výška pruhů nad seznamem (připnuté, spam…) — seznam pod ně zajíždí. */
    topPadding: Dp,
    /** Výška vstupního pole — seznam pod něj zajíždí. */
    bottomPadding: Dp,
    floatingDay: FloatingDay?,
) {
    val scope = rememberCoroutineScope()
    val selectionTargets = remember { SelectionDragTargets() }
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val photoDescriptions by viewModel.photoDescriptions.collectAsStateWithLifecycle()
    val revealMax = with(LocalDensity.current) { TIME_COLUMN_WIDTH.toPx() }
    val revealSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    when {
        /*
         * Omezený celý chat: místo zpráv jen důvod od serveru. Stojí PŘED načítáním —
         * historie se sice načte, ale ukazovat se nesmí, a točící se indikátor by
         * sliboval, že přijde.
         */
        state.info?.restriction != null -> IntroEmptyState(
            icon = Res.drawable.block_24dp,
            title = stringResource(Res.string.conversation_chat_restricted),
            text = state.info?.restriction.orEmpty(),
            modifier = Modifier.fillMaxSize(),
        )

        // Citlivý chat: napřed varování, obsah až po výslovném „přesto zobrazit".
        sensitiveWarning -> IntroEmptyState(
            icon = Res.drawable.visibility_off_24dp,
            title = stringResource(Res.string.conversation_chat_sensitive_title),
            text = stringResource(Res.string.conversation_chat_sensitive_text),
            modifier = Modifier.fillMaxSize(),
            action = {
                FilledTonalButton(onClick = onShowSensitive, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.conversation_chat_sensitive_show))
                }
            },
        )

        state.messages.isEmpty() && state.loading ->
            LoadingIndicator(Modifier.align(Alignment.Center))

        showsBotIntro(state) -> {
            val shownConnection = rememberShownConnection(connection)
            BotIntroPage(
                intro = state.botIntro ?: return,
                chatId = viewModel.chatId,
                title = state.info?.title.orEmpty(),
                avatarFileId = state.info?.avatarFileId,
                online = shownConnection == null && state.presence is Presence.Online,
                onAction = { itemScope.onAction(0L, it) },
                // Nad stránkou bota stojí pruhy (hlavně reklama u bota): jejich sklo bere
                // obraz z `listHaze` a stránka pod ně musí zajet, jinak by je překryla.
                // Podklad v barvě stránky: bez něj sklo rozostřilo jen obsah stránky a jeho
                // hranice prosvítala pruhem jako světlejší obdélník.
                modifier = Modifier.hazeSource(listHaze).background(MaterialTheme.colorScheme.background),
                topPadding = topPadding,
            )
        }

        showsBusinessIntro(state) -> {
            val shownConnection = rememberShownConnection(connection)
            BusinessIntroPage(
                intro = state.businessIntro ?: return,
                chatId = viewModel.chatId,
                title = state.info?.title.orEmpty(),
                avatarFileId = state.info?.avatarFileId,
                online = shownConnection == null && state.presence is Presence.Online,
                // Pod pruhy nad konverzací musí stránka zajet, jako stránka bota.
                modifier = Modifier.hazeSource(listHaze).background(MaterialTheme.colorScheme.background),
                topPadding = topPadding,
            )
        }

        // Prázdná konverzace a chyba bez zpráv: tentýž stav jako omezený nebo citlivý chat výš.
        state.messages.isEmpty() -> {
            val error = state.error
            IntroEmptyState(
                icon = if (error != null) Res.drawable.error_24dp else Res.drawable.chat_bubble_24dp,
                title = stringResource(if (error != null) Res.string.empty_state_error_title else Res.string.conversation_empty),
                text = error?.resolve() ?: stringResource(Res.string.conversation_empty_text),
                modifier = Modifier.fillMaxSize(),
                // Prázdný chat s člověkem nabídne samolepku na pozdrav, jako oficiální klienti.
                action = if (showsGreetingSticker(state)) {
                    { GreetingSticker(onSend = viewModel::sendSticker) }
                } else {
                    null
                },
            )
        }

        /*
         * Gesto patří SEZNAMU, ne položce.
         *
         * Bublina si bere jen tah doprava (odpověď) a doleva nic nespotřebuje, takže
         * gesto propadne sem. Svislý scroll má přednost: seznam je potomek, dostane
         * události dřív a jeho spotřebování tenhle detektor zruší — a detektor se
         * rozhoduje až po delším a výrazně vodorovném pohybu (`detectHorizontalSwipe`).
         */
        else -> Box(
            Modifier
                .fillMaxSize()
                /*
                 * `LazyColumn` ořezává jen ve směru scrollu, tedy svisle. Sloupec časů
                 * čekající za pravým okrajem byl schovaný jen proto, že seznam končil
                 * u okraje obrazovky; s bočním insetem (výřez vpravo) prosvítal. Ořez
                 * drží i odtaženou bublinu v jejím panelu.
                 */
                .clipToBounds()
                // Kam ořezává seznam, tam se ořezává i fotka letící z prohlížeče zpátky
                // (viz `SharedElementClip`) — jinak by na konci letu ležela přes hlavičku.
                .sharedElementClipArea(listClip)
                .hazeSource(listHaze)
                // Výběr tahem (podržet a táhnout přes zprávy), jen v režimu výběru.
                .selectionDrag(
                    enabled = state.selection != null,
                    targets = selectionTargets,
                    listState = listState,
                    // Plocha začíná pod hlavičkou; zajíždí jen pod pruhy (připnuté…) a pole.
                    topInset = topPadding,
                    bottomInset = bottomPadding,
                    onStart = viewModel::startDragSelection,
                    onMove = viewModel::extendDragSelection,
                    onEnd = viewModel::endDragSelection,
                )
                .pointerInput(revealMax) {
                    detectHorizontalSwipe(
                        direction = SwipeDirection.Left,
                        // Na desktopu patří tah myší výběru textu; čas ukáže najetí na zprávu.
                        mouse = !bubbleTextSelectable,
                        onEnd = {
                            gestureHints.used(GestureHint.RevealTimes)
                            scope.launch { timeReveal.animateTo(0f, revealSpec) }
                        },
                    ) { delta ->
                        scope.launch {
                            // Jen doleva a jen po šířku sloupce s časy; dál už není co
                            // odkrývat a gumové tahání by jen mátlo.
                            timeReveal.snapTo((timeReveal.value - delta).coerceIn(0f, revealMax))
                        }
                    }
                },
        ) {
          // Popisy fotek z „Popsat fotku" dostane čtečka obrazovky místo slova „Fotka".
          // Přílohy jdou myší vytáhnout ven (desktop), jen z chatu, který obsah nechrání.
          val currentMessages by rememberUpdatedState(state.messages)
          val protectsContent = state.info?.protectsContent == true
          val mediaDragOut = remember(protectsContent) {
              if (!fileDragSupported || protectsContent) null else MediaDragOut { id -> currentMessages.firstOrNull { it.id == id }?.timestamp }
          }
          // Stažení videa tlačítkem v náhledu jde do ViewModelu jako akce zprávy.
          val videoDownload = remember(itemScope) {
              VideoDownload(
                  onDownload = { id -> itemScope.onAction(id, MessageAction.DownloadVideo) },
                  onCancel = { id -> itemScope.onAction(id, MessageAction.CancelVideoDownload) },
              )
          }
          CompositionLocalProvider(
              LocalSharedElementClip provides listClip,
              LocalPhotoDescriptions provides photoDescriptions,
              LocalMediaDragOut provides mediaDragOut,
              LocalVideoDownload provides videoDownload,
              LocalSelectionDragTargets provides selectionTargets.takeIf { state.selection != null },
          ) {
          // Na široké ploše zprávy ve sloupci uprostřed; scroll zůstává přes celou plochu.
          ConversationColumn { inset ->
          // Dlouhá zpráva po rozbalení ukáže místo ořezu pod lištou, ne pod ní (`LongText`).
          CompositionLocalProvider(LocalConversationTopInset provides 8.dp + topPadding) {
          LazyColumn(
            state = listState,
            reverseLayout = true,
            // Odjezd při přechodu na vzdálenou zprávu; čte se až ve vrstvě.
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = jumpTransition.listAlpha
                translationY = jumpTransition.listShift
            },
            // Dole se odsazuje o výšku `bottomBar` (vstupního pole), aby nejnovější
            // zpráva v klidu nekončila pod ním. Při scrollu obsah pod pole zajede.
            contentPadding = PaddingValues(
                start = 12.dp + inset,
                end = 12.dp + inset,
                top = 8.dp + topPadding,
                bottom = 8.dp + bottomPadding,
            ),
            // Rozestup UVNITŘ skupiny zpráv od jednoho odesílatele. Mezera mezi skupinami
            // se přidává až v bublině — tady na to není informace o sousedech.
            verticalArrangement = Arrangement.spacedBy(MESSAGE_SPACING),
        ) {
            items(items, key = { it.key }, contentType = { it.contentType }) { item ->
                ConversationItemContent(item, itemScope)
            }
        }
          }
          }
          }
          // Nápověda k odkrývání časů ukazuje k pravému okraji, odkud časy přijedou.
          RevealTimesHintAnchor(gestureHints, Modifier.align(Alignment.CenterEnd))
        }
    }

    /*
     * Plovoucí datum.
     *
     * Ukáže se jen při scrollu a zase zmizí. V dlouhé historii je předěl často mimo
     * obrazovku a bez tohohle by se při hledání starší zprávy nedalo poznat, kde zrovna
     * jsem. Je to overlay, ne `stickyHeader`: ten se v obráceném seznamu lepí na jeho
     * začátek, tedy dolů, což je přesně naopak.
     */
    /*
     * Po zastavení ještě chvíli zůstane: na datum jde ťuknout a otevřít kalendář, a ťuknutí
     * samo rolování zastaví — se zmizením hned po něm by se trefit nedalo.
     */
    var dayShown by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            dayShown = true
        } else {
            delay(FLOATING_DAY_LINGER_MS)
            dayShown = false
        }
    }
    AnimatedVisibility(
        visible = dayShown && floatingDay != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        FloatingDayBadge(
            text = floatingDay?.label.orEmpty(),
            onClick = itemScope.datePicker()?.let { open -> { floatingDay?.let { open(it.dayStart) } } },
            glass = Modifier.hazeBlur(
                input = HazeInput.Sources(listHaze),
                style = glassStyle(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    alpha = GLASS_TEXT_TINT_ALPHA,
                    fallbackAlpha = GLASS_FALLBACK_ALPHA,
                ),
            ),
            modifier = Modifier.padding(top = 8.dp + topPadding),
        )
    }

    // Kalendář z předělu dne nebo plovoucího data; skok je týž jako z hledání.
    itemScope.datePickerDay.value?.let { day ->
        JumpToDateDialog(
            initialDay = day,
            onPick = { seconds ->
                itemScope.datePickerDay.value = null
                viewModel.jumpToDate(seconds)
            },
            onDismiss = { itemScope.datePickerDay.value = null },
        )
    }

    // Kalendář zpráv z ⋮. Stav si čte sám, aby dotahování počtů neskládalo celý obsah; otevře
    // se na dni, který je zrovna vidět.
    ChatCalendarHost(viewModel, visibleDay = { floatingDay?.dayStart })

    // Příchozí zprávu ohlásí čtečce obrazovky — bublina se do seznamu jen tiše vloží. Až
    // na konci plochy: uzel zakrytý seznamem nebo lištou by Compose čtečce vůbec nedal.
    NewMessageAnnouncer(messages = state.messages, live = state.reachedEnd, chatTitle = state.info?.title)
}

@Composable
private fun ChatCalendarHost(viewModel: ConversationViewModel, visibleDay: () -> Int?) {
    val state = viewModel.calendar.state.collectAsStateWithLifecycle().value ?: return
    // Den se vezme při otevření; rolování konverzace pod sheetem už nic nemění.
    val initialDay = remember { visibleDay() }
    ChatCalendarSheet(
        state = state,
        calendar = viewModel.calendar,
        initialDay = initialDay,
        onJumpToDay = { seconds ->
            viewModel.calendar.close()
            viewModel.jumpToDate(seconds)
        },
        onJumpToMessage = { messageId ->
            viewModel.calendar.close()
            viewModel.jumpToMessage(messageId)
        },
    )
}

/** Plovoucí datum: popisek a začátek dne, na kterém po ťuknutí otevře kalendář. */
internal data class FloatingDay(val label: String, val dayStart: Int)

/** Jak dlouho plovoucí datum po zastavení rolování zůstane, aby se na něj dalo ťuknout. */
private const val FLOATING_DAY_LINGER_MS = 1500L
