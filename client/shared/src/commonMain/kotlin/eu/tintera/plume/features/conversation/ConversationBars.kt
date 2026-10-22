package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.InviteMembersBar
import eu.tintera.plume.core.telegram.JoinRequestBar
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.SharePhoneBar
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.SoundingVideoNote
import eu.tintera.plume.features.conversation.items.mapUrl

/**
 * Pruhy plovoucí nad seznamem: proti spamu a další oznámení o chatu, reklama u bota, žádosti
 * o vstup, videochat, připnuté zprávy, dock živé polohy a mini přehrávač. Seznam se odsazuje
 * o jejich změřenou výšku ([onPinnedHeight]).
 *
 * Stav pruhů (který je vidět, co je schované, otevřený sheet z pruhu) si drží sám.
 */
@Composable
internal fun BoxScope.ConversationBars(
    state: ConversationState,
    viewModel: ConversationViewModel,
    navigation: ConversationNavigation,
    sheets: ConversationSheets,
    chatHidden: Boolean,
    listHaze: HazeState,
    /**
     * Klíč nejvyšší viditelné zprávy, podle kterého pruh připnutých volí zprávu. Lambda, ne
     * hodnota: mění se s každou zprávou, která nahoře přejede, a čtená při volání by skládala
     * všechno kolem pruhu.
     */
    topVisibleKey: () -> Long?,
    /** Živé polohy, jejichž zpráva odjela z obrazovky. */
    liveLocations: List<ChatMessage>,
    dockReveal: () -> Float,
    dockExpanded: Boolean,
    onDockExpandedChange: (Boolean) -> Unit,
    playerDock: PlayerDock,
    voicePlayback: State<VoicePlayback?>,
    soundingVideoNote: State<SoundingVideoNote?>,
    onPinnedHeight: (Dp) -> Unit,
    /** Výška pruhů BEZ řádku docku živé polohy — od jejich spodku se měří, kolik ze zprávy zajelo. */
    onBarsHeight: (Dp) -> Unit,
) {
    val chatBar by viewModel.chatBar.collectAsStateWithLifecycle()
    val videoChat by viewModel.videoChat.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    // ✕ schová pruh do té doby, než přibude další žádost — pamatuje se počet, u kterého se
    // schoval. Napořád by správce o nové žádosti nevěděl; při každém otevření chatu by zase
    // otravoval tím, co už viděl.
    var joinRequestsHiddenAt by rememberSaveable(viewModel.chatId) { mutableIntStateOf(0) }
    val spamBar = chatBar as? SpamBar
    var confirmSharePhone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(chatBar) { if (chatBar !is SharePhoneBar) confirmSharePhone = false }
    if (confirmSharePhone) {
        ContactSharePhoneDialog(
            name = state.info?.title.orEmpty(),
            onConfirm = {
                confirmSharePhone = false
                viewModel.sharePhoneNumber()
            },
            onDismiss = { confirmSharePhone = false },
        )
    }
    /** Otevřený sheet z pruhu proti spamu; `null` = zavřený. */
    var spamBarAction by rememberSaveable { mutableStateOf<SpamBarAction?>(null) }



    // Pruh mezitím zmizel (přidán kontakt, zavřeno jinde) — sheet nemá čeho se týkat.
    LaunchedEffect(spamBar) { if (spamBar == null) spamBarAction = null }
    val openSpamBar = spamBar
    val openSpamAction = spamBarAction
    if (openSpamBar != null && openSpamAction != null) {
        SpamBarSheet(
            action = openSpamAction,
            bar = openSpamBar,
            title = state.info?.let { chatTitle(it.id, it.title) }.orEmpty(),
            onConfirm = { cleanup ->
                spamBarAction = null
                viewModel.resolveSpamBar(cleanup)
            },
            onDismiss = { spamBarAction = null },
        )
    }

    /*
     * Připnuté zprávy.
     *
     * Plavou nad seznamem jako vstupní pole, ne v hlavičce Scaffoldu: seznam pod nimi zajíždí
     * a odsazuje se o jejich ZMĚŘENOU výšku. Kdyby byly součástí `topBar`, ukusovaly by místo
     * i ve chvíli, kdy odjíždějí, a obsah by pod nimi na konci animace poskočil.
     *
     * Ukazuje se jedna zpráva (viz `PinnedMessagesBar`), a **která, rozhoduje scroll**
     * (`pinnedForScroll`): ta, kterou uživatel právě míjí. Pruh tím slouží i jako
     * orientace v historii. Nedrží se proto žádný stav — odvozuje se z klíče nejvyšší
     * viditelné položky, takže nová připnutá zpráva výběr nerozhodí.
     */

    val density = LocalDensity.current
    /*
     * Pruh proti spamu a připnuté zprávy plavou nad seznamem spolu a měří se spolu: seznam
     * se odsazuje o výšku obou. Spam stojí nahoře — je důležitější než připnutá zpráva.
     */
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .onSizeChanged { onPinnedHeight(with(density) { it.height.toDp() }) },
    ) {
        Column(Modifier.onSizeChanged { onBarsHeight(with(density) { it.height.toDp() }) }) {
            // Při odjezdu se musí dál kreslit poslední pruh, ne nic (stejně jako u připnutých).
            // Při odjezdu se kreslí poslední pruh — stav je v tu chvíli už prázdný.
            val lastChatBar = remember { arrayOf<ChatBar?>(null) }
            if (chatBar != null) lastChatBar[0] = chatBar
            AnimatedVisibility(
                visible = chatBar != null,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                when (val bar = lastChatBar[0]) {
                    is SpamBar -> SpamBarCard(
                        bar = bar,
                        onBlock = { spamBarAction = SpamBarAction.Block },
                        onReport = { spamBarAction = SpamBarAction.Report },
                        onAddContact = viewModel::addSpamBarContact,
                        onUnarchive = viewModel::unarchiveFromSpamBar,
                        onDismiss = viewModel::dismissChatBar,
                        glass = Modifier.hazeBlur(
                            input = HazeInput.Sources(listHaze),
                            style = glassStyle(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                alpha = GLASS_TEXT_TINT_ALPHA,
                                fallbackAlpha = GLASS_FALLBACK_ALPHA,
                            ),
                        ),
                    )
                    SharePhoneBar, InviteMembersBar, is JoinRequestBar -> ChatNoticeBarCard(
                        bar = bar,
                        onSharePhone = { confirmSharePhone = true },
                        onInviteMembers = navigation.onAddMembers,
                        onDismiss = viewModel::dismissChatBar,
                        glass = Modifier.hazeBlur(
                            input = HazeInput.Sources(listHaze),
                            style = glassStyle(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                alpha = GLASS_TEXT_TINT_ALPHA,
                                fallbackAlpha = GLASS_FALLBACK_ALPHA,
                            ),
                        ),
                    )
                    null -> Unit
                }
            }
            // Reklama u bota hned pod spamem — u bota je to jeho „akční lišta" (viz `SponsoredBar`).
            // Omezený chat ji neukazuje, stejně jako ostatní pruhy o obsahu.
            val botAd = state.sponsored.firstOrNull { it.inBotChat }
            val lastBotAd = remember { arrayOf<SponsoredMessage?>(null) }
            if (botAd != null) lastBotAd[0] = botAd
            AnimatedVisibility(
                visible = botAd != null && !chatHidden,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                lastBotAd[0]?.let { ad ->
                    // Zhlédnutí jako u karty v kanálu: až pruh chvíli stojí celý na obrazovce
                    // (text se neořezává), jednou za relaci — hlídá `reportSponsoredSeen`.
                    if (botAd != null) {
                        LaunchedEffect(ad.messageId) {
                            delay(SPONSORED_BAR_VIEW_DELAY_MS)
                            viewModel.reportSponsoredSeen(ad.messageId)
                        }
                    }
                    SponsoredBar(
                        sponsored = ad,
                        onClick = { viewModel.onSponsoredClick(ad) },
                        onReport = { viewModel.reportSponsored(ad) },
                        glass = Modifier.hazeBlur(
                            input = HazeInput.Sources(listHaze),
                            style = glassStyle(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                alpha = GLASS_TEXT_TINT_ALPHA,
                                fallbackAlpha = GLASS_FALLBACK_ALPHA,
                            ),
                        ),
                    )
                }
            }
            // Žádosti o vstup hned pod spamem: čekají na rozhodnutí, jako on.
            val shownJoinRequests = joinRequests?.takeIf { it.count > joinRequestsHiddenAt }
            val lastJoinRequests = remember { arrayOf<PendingJoinRequests?>(null) }
            if (shownJoinRequests != null) lastJoinRequests[0] = shownJoinRequests
            AnimatedVisibility(
                visible = shownJoinRequests != null && !chatHidden,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                lastJoinRequests[0]?.let { requests ->
                    JoinRequestsBar(
                        requests = requests,
                        onClick = navigation.onOpenJoinRequests,
                        onHide = { joinRequestsHiddenAt = requests.count },
                        glass = Modifier.hazeBlur(
                            input = HazeInput.Sources(listHaze),
                            style = glassStyle(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                alpha = GLASS_TEXT_TINT_ALPHA,
                                fallbackAlpha = GLASS_FALLBACK_ALPHA,
                            ),
                        ),
                    )
                }
            }
            // Videochat mezi spamem a připnutými: o dění v chatu, ne o obsahu.
            val lastVideoChat = remember { arrayOf<VideoChatInfo?>(null) }
            if (videoChat != null) lastVideoChat[0] = videoChat
            AnimatedVisibility(
                visible = videoChat != null && !chatHidden,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                lastVideoChat[0]?.let { info ->
                    VideoChatBar(
                        info = info,
                        onClick = viewModel::explainVideoChat,
                        glass = Modifier.hazeBlur(
                            input = HazeInput.Sources(listHaze),
                            style = glassStyle(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                alpha = GLASS_TEXT_TINT_ALPHA,
                                fallbackAlpha = GLASS_FALLBACK_ALPHA,
                            ),
                        ),
                    )
                }
            }
            AnimatedVisibility(
                // Omezený chat: připnutá zpráva by ukázala obsah, který se ukázat nesmí.
                visible = state.pinned.isNotEmpty() && !chatHidden,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                // Při odjezdu se musí dál kreslit poslední zpráva, ne prázdný pruh.
                val last = remember { arrayOf(emptyList<ChatMessage>()) }
                if (state.pinned.isNotEmpty()) last[0] = state.pinned
                val pinned = last[0]

                PinnedMessagesBar(
                    messages = pinned,
                    index = pinnedIndex(pinned, pinnedForScroll(pinned, topVisibleKey())),
                    canUnpin = state.canUnpin,
                    // Ťuknutí skočí na ukázanou zprávu, a to KOTVOU NAHORU: zpráva pak stojí nad
                    // viditelnou částí, takže pruh přejde na předchozí připnutou a další ťuknutí
                    // vede o krok zpět. Se středem se nad cílem odkryje kus historie a pruh
                    // přeskočí rovnou k nejstarší.
                    onOpen = { message -> viewModel.jumpToMessage(message.id, anchor = ScrollAnchor.Top) },
                    onUnpin = viewModel::unpin,
                    onShowAll = { sheets.allPinned = true },
                    glass = Modifier.hazeBlur(
                        input = HazeInput.Sources(listHaze),
                        style = glassStyle(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            alpha = GLASS_TEXT_TINT_ALPHA,
                            fallbackAlpha = GLASS_FALLBACK_ALPHA,
                        ),
                    ),
                )
            }
        }

        /*
         * Dock živé polohy: poslední řádek pod pruhy, vysouvá se tím, jak zpráva s polohou
         * odjíždí z obrazovky. Ve vlákně komentářů polohy nejsou (`liveLocations` prázdné).
         */
        if (!chatHidden && liveLocations.isNotEmpty()) {
            LiveLocationDockBar(
                locations = liveLocations,
                reveal = dockReveal,
                expanded = dockExpanded,
                onToggle = { onDockExpandedChange(!dockExpanded) },
                chatTitle = state.info?.title.orEmpty(),
                onOpenMap = { message ->
                    (message.media as? MessageMedia.Location)?.let {
                        viewModel.onAction(message.id, MessageAction.OpenLink(it.mapUrl))
                    }
                },
                onShowMessage = { message ->
                    onDockExpandedChange(false)
                    viewModel.jumpToMessage(message.id)
                },
                onStop = { message -> viewModel.onAction(message.id, MessageAction.StopLiveLocation) },
                onProximityAlert = { message -> viewModel.onAction(message.id, MessageAction.SetProximityAlert) },
                glass = Modifier.hazeBlur(
                    input = HazeInput.Sources(listHaze),
                    style = glassStyle(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        alpha = GLASS_TEXT_TINT_ALPHA,
                        fallbackAlpha = GLASS_FALLBACK_ALPHA,
                    ),
                ),
            )
        }

        // Skladbu z jiného chatu ukazuje i omezený chat — neprozradí z něj nic a zastavit ji jinde nejde.
        ConversationPlayerDock(
            dock = playerDock,
            chatHidden = chatHidden,
            viewModel = viewModel,
            voicePlayback = voicePlayback,
            soundingVideoNote = soundingVideoNote,
            onOpenChat = navigation.onOpenChat,
            glass = Modifier.hazeBlur(
                input = HazeInput.Sources(listHaze),
                style = glassStyle(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GLASS_TEXT_TINT_ALPHA,
                    fallbackAlpha = GLASS_FALLBACK_ALPHA,
                ),
            ),
        )
    }
}

/** Jak dlouho musí pruh reklamy u bota stát na obrazovce, než se zhlédnutí nahlásí (jako u karty). */
private const val SPONSORED_BAR_VIEW_DELAY_MS = 1000L
