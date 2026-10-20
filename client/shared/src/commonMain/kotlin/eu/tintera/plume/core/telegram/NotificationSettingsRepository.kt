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

    /** Zvuk rozsahu: [DEFAULT_SOUND_ID], [NO_SOUND_ID], nebo id tónu účtu. */
    suspend fun setScopeSound(scope: NotificationScope, soundId: Long): Result<Unit> = Result.failure(UnsupportedOperationException())

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
     * Zvuk chatu: [DEFAULT_SOUND_ID], [NO_SOUND_ID], id tónu účtu, nebo `null` = podle výchozího
     * nastavení rozsahu. Ostatní volby chatu zůstanou.
     */
    suspend fun setChatSound(chatId: Long, soundId: Long?): Result<Unit> = Result.failure(UnsupportedOperationException())

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

    /**
     * Zvuk tématu: id tónu, [NO_SOUND_ID], výchozí −1, nebo `null` = jako skupina
     * (`useDefaultSound`). Obdoba [setChatSound] o vrstvu výš.
     */
    suspend fun setTopicSound(chatId: Long, topicId: Int, soundId: Long?): Result<Unit> =
        Result.failure(UnsupportedOperationException())
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
    /** Zvuk, kterým chat opravdu zní: [DEFAULT_SOUND_ID], [NO_SOUND_ID], nebo id tónu účtu. */
    val soundId: Long = DEFAULT_SOUND_ID,
    /** Zvuk nemá chat (téma) vlastní, bere ho odjinud — viz [defaults]. */
    val soundIsDefault: Boolean = true,
    /** Zvuk, který by chat (téma) měl bez vlastního — co nabízí volba „podle výchozího nastavení". */
    val defaultSoundId: Long = DEFAULT_SOUND_ID,
    /**
     * Upozornění na nové příběhy chatu. Bez vlastní volby platí rozsah — a u „nejčastějších
     * kontaktů" ([StoryAlerts.TopContacts]) se neví, jestli mezi ně chat patří; bere se jako zapnuté.
     */
    val stories: Boolean = true,
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

enum class ChatNotificationOption {
    ShowPreview,
    PinnedMessages,
    Mentions,

    /** Upozornit na nové příběhy chatu (`muteStories`). */
    Stories,
}

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
    /** Zvuk: [DEFAULT_SOUND_ID], [NO_SOUND_ID], nebo id tónu účtu. */
    val soundId: Long = DEFAULT_SOUND_ID,
    /** Na čí nové příběhy upozorňovat (`useDefaultMuteStories` / `muteStories`). */
    val storyAlerts: StoryAlerts = StoryAlerts.TopContacts,
    /** Jméno autora v upozornění na příběh (`showStoryPoster`). */
    val showStoryPoster: Boolean = true,
)

/**
 * Upozornění na nové příběhy v rozsahu. Výchozí je [TopContacts]: Telegram pak upozorňuje jen
 * na prvních pět nejčastějších kontaktů (`useDefaultMuteStories`) — na všechny by to byl šum.
 */
enum class StoryAlerts { TopContacts, All, None }

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
