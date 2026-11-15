package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.customEmojiIdOf
import androidx.compose.runtime.mutableStateOf
import eu.tintera.plume.features.conversation.items.EmojiBurst
import eu.tintera.plume.features.conversation.items.EmojiBursts
import eu.tintera.plume.core.ui.bubbleTextSelectable
import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.PendingChecklistMark
import androidx.compose.ui.input.key.key
import eu.tintera.plume.core.telegram.TranscribeOffer
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.features.conversation.items.ARRIVAL_TOLERANCE_SECONDS
import eu.tintera.plume.features.conversation.items.effectAutoplays
import eu.tintera.plume.features.conversation.items.SoundingVideoNote
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.features.conversation.items.ConversationItemScope
import eu.tintera.plume.features.conversation.items.MenuAnchor
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import kotlin.time.Clock

/**
 * [ConversationItemScope] nad ViewModelem obrazovky.
 *
 * Stav se čte přes [State] až uvnitř volání, takže se k jeho změnám přihlásí jen to UI, které
 * hodnotu opravdu kreslí (průběh stahování, pozice hlasovky), ne celá položka.
 */
internal class ScreenItemScope(
    private val viewModel: ConversationViewModel,
    private val state: State<ConversationState>,
    private val voicePlayback: State<VoicePlayback?>,
    private val voiceSpeed: State<VoiceSpeed>,
    private val transcribeContext: State<TranscribeContext>,
    private val contentTop: State<Float?>,
    private val onMenuAnchor: (MenuAnchor) -> Unit,
    private val menuKey: State<Long?>,
    override val timeReveal: () -> Float,
    private val onOpenMedia: (messageId: Long) -> Unit,
    private val onOpenPollMedia: (messageId: Long, index: Int) -> Unit,
    private val onOpenArticleMedia: (messageId: Long, index: Int) -> Unit,
    private val onOpenSponsoredMedia: (messageId: Long) -> Unit,
    private val onOpenStickerSet: (setId: Long) -> Unit,
    private val onSearch: (query: String) -> Unit,
    private val onSaveFileAs: (messageId: Long) -> Unit,
    private val jumpTransition: JumpTransition,
    private val searchHighlight: State<String?>,
    private val profileFlight: MutableState<Long?>,
    private val canSendCommands: State<Boolean>,
    private val soundingVideoNote: State<SoundingVideoNote?>,
    /** Vysvětlení zprávy jde do sheetu shrnutí ([ChatSummaryViewModel.explain]). */
    private val summary: ChatSummaryViewModel,
) : ConversationItemScope {
    override fun isProfileFlight(messageId: Long): Boolean = profileFlight.value == messageId

    /**
     * Den, na kterém je otevřený kalendář po ťuknutí na předěl dne nebo plovoucí datum; `null`
     * = zavřený. Dialog kreslí `ConversationContent`. Otočení obrazovky ho zavře — je to jedno
     * ťuknutí, ne rozdělaná práce.
     */
    val datePickerDay = mutableStateOf<Int?>(null)

    private val openDatePicker: (Int) -> Unit = { datePickerDay.value = it }

    override fun datePicker(): ((timestamp: Int) -> Unit)? = openDatePicker.takeIf { viewModel.canJumpToDate }

    override fun pressingBotButton(messageId: Long): Pair<Int, Int>? =
        state.value.pressingBotButton?.takeIf { it.messageId == messageId }?.let { it.row to it.column }

    override fun pendingChecklistMarks(messageId: Long): List<PendingChecklistMark> =
        state.value.pendingChecklistMarks[messageId].orEmpty()

    override fun searchHighlight(): String? = searchHighlight.value

    override fun canSendCommands(): Boolean = canSendCommands.value

    override fun doubleTapReaction(): Boolean = state.value.doubleTapReaction

    override fun quickReactionEmoji(): String? =
        viewModel.quickReactionKey.value?.takeIf { customEmojiIdOf(it) == null }

    // Odpovědět i poslat příkaz jde jen tam, kam se smí psát — tentýž stav (`canWrite`).
    override fun canReply(): Boolean = canSendCommands.value

    override fun selectableText(): Boolean = bubbleTextSelectable && state.value.info?.protectsContent != true

    override fun gatherShift(key: Any): Float = jumpTransition.shiftOf(key)

    override fun gatherAlpha(key: Any): Float = jumpTransition.alphaOf(key)

    // Celý klíč, ne jen id zprávy: hudba hraje dál i po odchodu z chatu a id zprávy se potká
    // se zprávou jiného chatu nebo téže skupiny v jiném účtu.
    override fun voicePlayback(messageId: Long, part: Int): VoicePlayback? =
        voicePlayback.value?.takeIf { it.key == VoiceKey(viewModel.slot, viewModel.chatId, messageId, part) }

    override fun voiceSpeed(): VoiceSpeed = voiceSpeed.value

    override fun transcribeOffer(durationSeconds: Int): TranscribeOffer = transcribeContext.value.let {
        eu.tintera.plume.core.telegram.transcribeOffer(it.isPremium, it.trial, durationSeconds, it.onDevice, it.onDeviceFix)
    }

    override fun localTranscript(messageId: Long): SpeechTranscript? = transcribeContext.value.local[messageId]

    @Composable
    override fun scamWarning(messageId: Long): ScamKind? {
        val verdicts by viewModel.scamVerdicts.collectAsStateWithLifecycle()
        return verdicts[messageId]
    }

    override fun dismissScamWarning(messageId: Long) = viewModel.dismissScamWarning(messageId)

    /** Rozbor textu zpráv pravidly ([findMessageContext]); čte a píše se jen na hlavním vlákně. */
    private val contextCache = HashMap<ContextKey, MessageContext>()

    private data class ContextKey(val text: String, val entities: List<MessageEntity>, val timestamp: Int)

    @Composable
    override fun messageContext(message: ChatMessage): List<ContextFinding> {
        val verdicts by viewModel.contextVerdicts.collectAsStateWithLifecycle()
        val current = verdicts ?: return emptyList()
        if (!message.offersContext()) return emptyList()
        // Rozbor pravidly je drahý (desítky regulárních výrazů) a v kompozici brzdil první snímky
        // každé nové položky — i té, která zrovna přilétá ze vstupního pole. Počítá se proto mimo
        // hlavní vlákno a pamatuje pro celou obrazovku; ikonka stojí mimo rozvržení
        // (`offset`), takže když naskočí o snímek později, nic se neposune.
        val key = ContextKey(message.text, message.entities, message.timestamp)
        val found = produceState(contextCache[key], key) {
            if (value != null) return@produceState
            val zone = TimeZone.currentSystemDefault()
            val sentAt = Instant.fromEpochSeconds(message.timestamp.toLong()).toLocalDateTime(zone)
            val homeCurrency = viewModel.homeCurrency
            val metricUnits = viewModel.metricUnits
            val result = withContext(Dispatchers.Default) {
                findMessageContext(message.text, message.entities, sentAt, zone, homeCurrency, metricUnits)
            }
            if (contextCache.size >= CONTEXT_CACHE_LIMIT) contextCache.clear()
            contextCache[key] = result
            value = result
        }.value ?: return emptyList()
        if (found.isEmpty) return emptyList()
        val verdict = current[message.id to message.text.hashCode()]
        return remember(found, verdict) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            found.contextFindings(message.text, verdict, now)
        }
    }

    override fun openMessageContext(messageId: Long, findings: List<ContextFinding>) =
        viewModel.openMessageContext(messageId, findings)

    override fun videoNoteSounding(messageId: Long): Boolean = state.value.soundingVideoNote == messageId

    override fun soundingVideoNote(messageId: Long): SoundingVideoNote? =
        soundingVideoNote.value?.takeIf { it.key.messageId == messageId && it.key.isIn(viewModel.slot, viewModel.chatId) }

    override fun documentProgress(fileId: Int): Float? = state.value.documentDownloads[fileId]

    override fun articleLoading(messageId: Long): Boolean = messageId in state.value.loadingArticles

    override fun contentTop(): Float? = contentTop.value

    override fun onMenuAnchor(anchor: MenuAnchor) = onMenuAnchor.invoke(anchor)

    override fun isInMenu(key: Long): Boolean = menuKey.value == key

    /** Kdy se konverzace otevřela; starší zprávy jsou historie, ne příchod. */
    private val openedAt = Clock.System.now().epochSeconds
    private val arrived = HashSet<Long>()

    override fun claimArrival(key: Long, timestamp: Int): Boolean =
        timestamp >= openedAt - ARRIVAL_TOLERANCE_SECONDS && arrived.add(key) && key != state.value.pendingReplacedBy

    private val diceThrown = HashSet<Long>()

    override fun claimDiceThrow(key: Long, timestamp: Int): Boolean =
        timestamp >= openedAt - ARRIVAL_TOLERANCE_SECONDS && diceThrown.add(key)

    private val effectsPlayed = HashSet<Long>()

    override fun claimEffect(key: Long, message: ChatMessage, unreadBoundary: Long?): Boolean =
        effectAutoplays(message.timestamp, openedAt, message.isOutgoing, message.id, unreadBoundary) &&
            effectsPlayed.add(key)

    private val unreadBoundaryState = derivedStateOf { state.value.unreadBoundaryId }

    override fun unreadBoundary(): Long? = unreadBoundaryState.value

    private val emojiBursts = object : EmojiBursts {
        @Composable
        override fun burst(messageId: Long): EmojiBurst? {
            val waiting by viewModel.emojiBursts.collectAsStateWithLifecycle()
            return waiting[messageId]
        }

        override fun tap(messageId: Long) = viewModel.tapAnimatedEmoji(messageId)

        override fun shown(burst: EmojiBurst) = viewModel.emojiBurstShown(burst)
    }

    override fun emojiBursts(): EmojiBursts = emojiBursts

    /**
     * Navigace, sheet nad obrazovkou a systémový výběr umístění jsou věc obrazovky; všechno
     * ostatní jde do ViewModelu.
     */
    override fun onAction(messageId: Long, action: MessageAction) = when (action) {
        MessageAction.OpenMedia -> onOpenMedia(messageId)
        is MessageAction.OpenPollMedia -> onOpenPollMedia(messageId, action.index)
        is MessageAction.OpenArticleMedia -> onOpenArticleMedia(messageId, action.index)
        is MessageAction.OpenStickerSet -> onOpenStickerSet(action.setId)
        is MessageAction.SearchHashtag -> onSearch(action.tag)
        MessageMenuAction.SaveFileAs -> onSaveFileAs(messageId)
        MessageMenuAction.Explain -> {
            state.value.messages.firstOrNull { it.id == messageId }?.let(summary::explain)
            viewModel.onAction(messageId, action)
        }
        MessageMenuAction.SummarizeVoice -> {
            state.value.messages.firstOrNull { it.id == messageId }?.let { message ->
                val transcript = message.doneTranscript(localTranscript(messageId))
                // Kdo mluví: v soukromém chatu jméno odesílatele TDLib nevyplňuje, pak název chatu.
                val speaker = message.senderName ?: state.value.info?.title.orEmpty()
                if (transcript != null) summary.summarizeVoice(message, transcript, speaker)
            }
            viewModel.onAction(messageId, action)
        }
        is MessageAction.OpenSenderProfile -> {
            // Klíč letu musí stát dřív, než se profil otevře; chat se hledá asynchronně.
            profileFlight.value = messageId
            viewModel.onAction(messageId, action)
        }
        else -> viewModel.onAction(messageId, action)
    }

    override fun openSponsored(sponsored: SponsoredMessage) = viewModel.onSponsoredClick(sponsored)
    override fun reportSponsored(sponsored: SponsoredMessage) = viewModel.reportSponsored(sponsored)

    override fun openSponsoredMedia(sponsored: SponsoredMessage) {
        if (sponsored.mediaOpensLink) {
            viewModel.onSponsoredClick(sponsored, isMediaClick = true)
        } else {
            viewModel.onSponsoredVideoOpened(sponsored)
            onOpenSponsoredMedia(sponsored.messageId)
        }
    }

    override fun stopPendingReply() = viewModel.stopPendingReply()
}
