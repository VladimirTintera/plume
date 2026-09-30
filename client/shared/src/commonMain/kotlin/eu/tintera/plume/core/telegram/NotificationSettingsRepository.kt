package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Nastavení notifikací, které drží TDLib — synchronizuje se přes server na ostatní zařízení.
 *
 * Zvuk a vibrace tu nejsou schválně: na Androidu patří systémovému kanálu (viz
 * [NotificationChannelKind]) a aplikace je po jeho založení stejně přepsat nemůže.
 */
interface NotificationSettingsRepository {

    /** Výchozí nastavení rozsahu, živě. `null`, dokud se nenačte (nebo když čtení selže). */
    fun scopeSettings(scope: NotificationScope): Flow<ScopeSettings?>

    suspend fun setScopeSettings(scope: NotificationScope, settings: ScopeSettings): Result<Unit>

    /** Chaty rozsahu s vlastním nastavením upozornění — jediné místo, kde je vidět, co kde je ztlumené. */
    suspend fun exceptions(scope: NotificationScope): Result<List<NotificationException>>

    /** Chat se vrátí k výchozímu nastavení svého rozsahu. */
    suspend fun resetToDefault(chatId: Long): Result<Unit>

    /** Upozornit, když se ke Telegramu připojí někdo z kontaktů? */
    suspend fun contactJoinedEnabled(): Result<Boolean>

    suspend fun setContactJoinedEnabled(enabled: Boolean): Result<Unit>

    /**
     * Upozornění jednoho chatu, živě — i s tím, co chat přebírá z výchozího nastavení rozsahu.
     * `null`, dokud se nenačte.
     */
    fun chatSettings(chatId: Long): Flow<ChatNotificationPrefs?>

    /** Změní jednu volbu chatu; ostatní (i zvuk nastavený jinde) zůstanou. */
    suspend fun setChatOption(chatId: Long, option: ChatNotificationOption, value: Boolean): Result<Unit>

    /**
     * Upozornění tématu ve skupině, živě. Co téma nemá vlastní, přebírá ze SKUPINY (a ta
     * případně z výchozího nastavení skupin) — viz [ChatNotificationPrefs.defaults].
     */
    fun topicSettings(chatId: Long, topicId: Int): Flow<ChatNotificationPrefs?>

    /** Ztlumení tématu: `null` = navždy, [Duration.ZERO] = zapnout upozornění. */
    suspend fun setTopicMute(chatId: Long, topicId: Int, duration: Duration?): Result<Unit>

    suspend fun setTopicOption(chatId: Long, topicId: Int, option: ChatNotificationOption, value: Boolean): Result<Unit>

    /** Téma se vrátí k nastavení skupiny. */
    suspend fun resetTopic(chatId: Long, topicId: Int): Result<Unit>
}

/**
 * Upozornění chatu. Hodnoty jsou ÚČINNÉ: kde chat jede podle rozsahu, je tu hodnota rozsahu —
 * přepínač má ukazovat, co se opravdu stane, ne co leží v nepoužitém poli.
 */
data class ChatNotificationPrefs(
    val mute: MuteState,
    /** Ztlumení nemá chat (téma) vlastní, bere ho odjinud — viz [defaults]. */
    val muteIsDefault: Boolean,
    val showPreview: Boolean,
    val pinnedMessages: Boolean,
    val mentions: Boolean,
    /** Chat nemá nic vlastního — obnovení výchozího nastavení by nic nezměnilo. */
    val isDefault: Boolean,
    val scope: NotificationScope,
    /** Odkud se berou hodnoty, které chat (téma) nemá vlastní. */
    val defaults: NotificationDefaults = NotificationDefaults.Scope,
)

enum class NotificationDefaults {
    /** Výchozí nastavení rozsahu (soukromé chaty / skupiny / kanály) — u chatu. */
    Scope,

    /** Nastavení skupiny — u tématu. */
    Chat,
}

sealed interface MuteState {
    data object Off : MuteState
    data object Forever : MuteState

    /** Ztlumeno do [epochSeconds]; pak se upozornění samo vrátí. */
    data class Until(val epochSeconds: Long) : MuteState
}

enum class ChatNotificationOption { ShowPreview, PinnedMessages, Mentions }

/**
 * Ztlumení z počtu zbývajících sekund, jak ho posílá TDLib. Nad rok se bere jako „navždy" —
 * klienti navždy zapisují `Int.MAX_VALUE`, ale starší a jiní klienti i roky.
 */
fun muteState(muteForSeconds: Int, nowEpochSeconds: Long): MuteState = when {
    muteForSeconds <= 0 -> MuteState.Off
    muteForSeconds > FOREVER_THRESHOLD_SECONDS -> MuteState.Forever
    else -> MuteState.Until(nowEpochSeconds + muteForSeconds)
}

private const val FOREVER_THRESHOLD_SECONDS = 366 * 24 * 60 * 60

/** Rozsah výchozího nastavení v TDLib. Odpovídá jedna k jedné kanálu [channel]. */
enum class NotificationScope(val channel: NotificationChannelKind) {
    Private(NotificationChannelKind.Private),
    Groups(NotificationChannelKind.Groups),
    Channels(NotificationChannelKind.Channels),
}

data class ScopeSettings(
    /** Upozornění zapnutá (`muteFor == 0`). Dočasné ztlumení celého rozsahu se čte jako vypnuté. */
    val enabled: Boolean,
    val showPreview: Boolean,
    val pinnedMessages: Boolean,
    /** Zmínky a odpovědi na mě — projdou i ztlumeným chatem. */
    val mentions: Boolean,
)

data class NotificationException(
    val chatId: Long,
    val title: String,
    val avatarFileId: Int?,
    val kind: ChatKind,
    val state: ExceptionState,
)

enum class ExceptionState {
    Muted,
    Enabled,
    /** Ztlumení podle rozsahu, liší se jiná volba (náhled, zvuk, zmínky…). */
    Custom,
}
