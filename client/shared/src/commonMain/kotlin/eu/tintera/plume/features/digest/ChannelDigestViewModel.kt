package eu.tintera.plume.features.digest

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ai.channelDigestPrompt
import eu.tintera.plume.core.ai.digestLine
import eu.tintera.plume.core.ai.transcriptCharsFor
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolveNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/** Co ukáže řádek kanálu místo shrnutí, nebo shrnutí samo. */
sealed interface DigestSummary {
    /** Čeká ve frontě na model. */
    data object Waiting : DigestSummary

    /** Model píše; [text] přibývá. */
    data class Running(val text: String) : DigestSummary

    data class Done(val text: String) : DigestSummary

    /** Shrnout nešlo (chyba, vypršel čas) — řádek ukáže začátek posledního příspěvku. */
    data object Failed : DigestSummary

    /** Bez modelu, nebo v příspěvcích není text (samé fotky) — začátek posledního příspěvku. */
    data object None : DigestSummary
}

/** Kanál v přehledu. */
@Immutable
data class DigestChannel(
    val chatId: Long,
    val title: String,
    val avatarFileId: Int?,
    /** Nepřečtené příspěvky (může být víc než těch za 24 h). */
    val unread: Int,
    val lastTimestamp: Int,
    /** Náhled poslední zprávy ze seznamu chatů — když shrnutí není. */
    val lastPreview: UiText?,
    val summary: DigestSummary,
)

/** Jak je na tom model v zařízení. */
enum class DigestAi {
    /** Shrnuje se. */
    Ready,

    /** AI v zařízení je vypnutá v Nastavení. */
    Off,

    /** Model telefon umí, ale není stažený. */
    Missing,

    /** Telefon žádný model neumí — přehled je jen seznam, bez nabídky. */
    Unsupported,
}

@Immutable
data class ChannelDigestState(
    /** `null` = čte se seznam chatů. */
    val channels: List<DigestChannel>? = null,
    val ai: DigestAi = DigestAi.Ready,
)

/**
 * Shrnutí, která už v tomto běhu aplikace vznikla, podle (kanál, poslední zpráva). Přehled se
 * otevírá opakovaně a Gemma na jeden kanál potřebuje i desítky sekund; nová zpráva v kanálu
 * klíč změní a shrnutí se spočítá znovu. Jen v paměti — ráno má být přehled čerstvý.
 */
class ChannelDigestCache {
    private val summaries = mutableMapOf<Pair<Long, Long>, String>()

    fun get(chatId: Long, lastMessageId: Long?): String? = lastMessageId?.let { summaries[chatId to it] }

    fun put(chatId: Long, lastMessageId: Long?, text: String) {
        if (lastMessageId != null) summaries[chatId to lastMessageId] = text
    }
}

/**
 * **Ranní přehled kanálů**: ztlumené kanály s novými příspěvky za posledních 24 hodin, u každého
 * dvě věty od modelu v zařízení (`channelDigestPrompt`). Telegram nic takového nemá; ztlumené
 * kanály se jinak čtou po jednom, nebo vůbec.
 *
 * - **Počítá se při otevření, kanál po kanálu** a výsledek se streamuje do řádku. AICore pustí
 *   inferenci jen aplikaci v popředí, takže přes noc to nejde; a víc modelů naráz nejede tak jako tak.
 * - **Úloha na ťuknutí** (`variantFor(AiTask.Summary)`), ne automatická: přehled otevřel uživatel,
 *   takže se počítá i v úsporném režimu (jako shrnutí chatu). Nic se kvůli němu nestahuje.
 * - **Nejvýš [MAX_CHANNELS] kanálů a [MAX_POSTS] příspěvků na kanál** — na emulátoru Gemma
 *   trvala na jedno shrnutí desítky sekund.
 * - **Bez modelu** přehled zůstane seznamem s počty a náhledem poslední zprávy ([DigestAi]).
 * - **Jen hlavní seznam**: archivované kanály by chtěly vlastní načtení archivu; zamčené chaty
 *   v seznamu chatů nejsou (filtruje je core).
 */
class ChannelDigestViewModel(
    private val chats: ChatRepository,
    private val conversations: ConversationRepository,
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
    private val cache: ChannelDigestCache,
    private val now: () -> Int = { Clock.System.now().epochSeconds.toInt() },
) : ViewModel() {

    private val logger = Logger.withTag("ChannelDigest")

    private val _state = MutableStateFlow(ChannelDigestState())
    val state: StateFlow<ChannelDigestState> = _state.asStateFlow()

    init {
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        val since = now() - DAY_SECONDS
        val list = (chats.chats().first { it !is ChatListState.Loading } as? ChatListState.Ready)?.chats.orEmpty()
        val picked = digestChannels(list, since)
        val variant = readyVariant()
        _state.value = ChannelDigestState(
            channels = picked.map { chat ->
                val cached = cache.get(chat.id, chat.lastMessageId)
                DigestChannel(
                    chatId = chat.id,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    unread = chat.unreadCount,
                    lastTimestamp = chat.lastMessageTimestamp ?: 0,
                    lastPreview = chat.lastMessagePreview,
                    summary = when {
                        cached != null -> DigestSummary.Done(cached)
                        variant != null -> DigestSummary.Waiting
                        else -> DigestSummary.None
                    },
                )
            },
            ai = aiState(variant),
        )
        if (variant == null) return
        for (chat in picked) {
            if (cache.get(chat.id, chat.lastMessageId) != null) continue
            summarize(chat, since, variant)
        }
    }

    /** Model připravený ke shrnutí, nebo `null` (stav pro obrazovku řekne [aiState]). */
    private var aiStatus: DigestAi = DigestAi.Ready

    private suspend fun readyVariant(): ModelVariant? {
        if (!settings.onDeviceAiEnabled.value) {
            aiStatus = DigestAi.Off
            return null
        }
        return try {
            val variant = ai.variantFor(AiTask.Summary, settings)
            when (ai.status(variant)) {
                OnDeviceAiStatus.Ready -> variant
                OnDeviceAiStatus.Unsupported -> null.also { aiStatus = DigestAi.Unsupported }
                else -> null.also { aiStatus = DigestAi.Missing }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Stav modelu se nepodařilo zjistit." }
            aiStatus = DigestAi.Unsupported
            null
        }
    }

    private fun aiState(variant: ModelVariant?): DigestAi = if (variant != null) DigestAi.Ready else aiStatus

    private suspend fun summarize(chat: ChatSummary, since: Int, variant: ModelVariant) {
        val posts = conversations.history(chat.id, 0, limit = chat.unreadCount.coerceIn(1, MAX_POSTS)).getOrNull().orEmpty()
            .filter { !it.isService && it.timestamp >= since }
            .sortedBy { it.id }
            .map { post -> post.text.ifBlank { post.description?.resolveNow().orEmpty() } }
            .filter { it.isNotBlank() }
        if (posts.isEmpty()) {
            setSummary(chat.id, DigestSummary.None)
            return
        }
        setSummary(chat.id, DigestSummary.Running(""))
        val result = try {
            val maxChars = transcriptCharsFor(ai.info(variant)?.tokenLimit)
            val prompt = channelDigestPrompt(chat.title, posts, platformLanguageCode, maxChars)
            var last = ""
            withTimeoutOrNull(TIMEOUT_MS) {
                ai.generate(variant, prompt).collect { generated ->
                    last = digestLine(generated.text)
                    setSummary(chat.id, DigestSummary.Running(last))
                }
                last
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Kanál ${chat.id} se nepodařilo shrnout." }
            null
        }
        if (result.isNullOrBlank()) {
            setSummary(chat.id, DigestSummary.Failed)
        } else {
            cache.put(chat.id, chat.lastMessageId, result)
            setSummary(chat.id, DigestSummary.Done(result))
        }
    }

    private fun setSummary(chatId: Long, summary: DigestSummary) {
        _state.update { current ->
            current.copy(channels = current.channels?.map { if (it.chatId == chatId) it.copy(summary = summary) else it })
        }
    }
}

/**
 * Kanály do přehledu: ztlumené, s nepřečteným a s poslední zprávou od [since]; nejnovější
 * napřed, nejvýš [MAX_CHANNELS]. Neztlumené kanály dávají vědět samy, přehled je pro ty, které
 * uživatel ztišil, ale číst je chce.
 */
internal fun digestChannels(chats: List<ChatSummary>, since: Int): List<ChatSummary> =
    chats.filter { it.kind == ChatKind.Channel && it.isMuted && it.unreadCount > 0 && (it.lastMessageTimestamp ?: 0) >= since }
        .sortedByDescending { it.lastMessageTimestamp ?: 0 }
        .take(MAX_CHANNELS)

internal const val MAX_CHANNELS = 8
internal const val MAX_POSTS = 20
private const val DAY_SECONDS = 24 * 60 * 60

/** Jedno shrnutí nejvýš minutu a půl — pak se řádek vzdá a ukáže náhled. */
private const val TIMEOUT_MS = 90_000L
