package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.settings.AutoDownloadLimits
import kotlinx.coroutines.flow.Flow

/**
 * Automatické ukládání fotek a videí do galerie (`AutosaveSettings`).
 *
 * **Nastavení drží Telegram na serveru** — je společné s oficiálními aplikacemi na všech
 * zařízeních účtu. Ukládat ale musí každá aplikace sama: TDLib jen řekne, co se ukládat má.
 */
interface AutosaveRepository {

    /** Nastavení; po prvním načtení z paměti, změny (i z jiného zařízení) ho obnoví. */
    suspend fun settings(): Result<AutosaveSettings>

    /** Změní výchozí pravidlo nebo výjimku; `null` u výjimky ji odebere. */
    suspend fun set(scope: AutosaveScope, settings: AutosaveScopeSettings?): Result<Unit>

    /** Odebere všechny výjimky naráz (`clearAutosaveSettingsExceptions`); výchozí pravidla zůstanou. */
    suspend fun clearExceptions(): Result<Unit> = Result.failure(UnsupportedOperationException())

    /**
     * Fotky a videa z nově příchozích zpráv, která se uložit SMĚJÍ (ne tajný chat, chráněný
     * obsah, mizející médium ani vlastní zpráva). Zda se uložit MAJÍ, rozhoduje [autosaveAction].
     */
    val incoming: Flow<AutosaveCandidate>
}

/**
 * Pravidlo pro jeden druh chatů nebo jeden chat.
 *
 * @property maxVideoBytes největší video, které se uloží; TDLib bere 512 kB až 4000 MB.
 */
data class AutosaveScopeSettings(
    val photos: Boolean,
    val videos: Boolean,
    val maxVideoBytes: Long,
) {
    val enabled: Boolean get() = photos || videos
}

data class AutosaveException(val chatId: Long, val settings: AutosaveScopeSettings)

data class AutosaveSettings(
    val privateChats: AutosaveScopeSettings,
    val groups: AutosaveScopeSettings,
    val channels: AutosaveScopeSettings,
    val exceptions: List<AutosaveException>,
) {
    /** Co platí v chatu: výjimka, jinak výchozí pravidlo jeho druhu. */
    fun forChat(chatId: Long, type: AutosaveChatType): AutosaveScopeSettings =
        exceptions.firstOrNull { it.chatId == chatId }?.settings ?: when (type) {
            AutosaveChatType.Private -> privateChats
            AutosaveChatType.Group -> groups
            AutosaveChatType.Channel -> channels
        }
}

enum class AutosaveChatType { Private, Group, Channel }

sealed interface AutosaveScope {
    data object PrivateChats : AutosaveScope
    data object Groups : AutosaveScope
    data object Channels : AutosaveScope
    data class Chat(val chatId: Long) : AutosaveScope
}

/**
 * Fotka nebo video z příchozí zprávy.
 *
 * @property fileId soubor, který se uloží — u fotky největší velikost.
 * @property size velikost souboru v bajtech (0 = neznámá).
 * @property date čas zprávy; podle něj dostane soubor v galerii jméno.
 */
data class AutosaveCandidate(
    val chatId: Long,
    val chatType: AutosaveChatType,
    val fileId: Int,
    val isVideo: Boolean,
    val size: Long,
    val date: Int,
    val originalName: String? = null,
)

enum class AutosaveAction {
    /** Ukládat se nemá. */
    Skip,

    /** Stáhnout a uložit — automatické stahování na téhle síti to unese. */
    Download,

    /**
     * Ukládat se má, ale stahovat kvůli tomu nic nebudeme: automatické stahování na téhle síti
     * by to samo nestáhlo. Uloží se, jakmile soubor stáhne někdo jiný (otevřený chat, prohlížeč).
     */
    WhenDownloaded,
}

/**
 * Co s médiem udělat. **Ukládání nestahuje víc, než by stáhlo automatické stahování** — kdo
 * na mobilních datech stahování vypnul, nečeká, že mu je snědí ukládání do galerie. Fotka se
 * stáhne, když se fotky stahují samy; video, když se vejde do limitu souborů dané sítě
 * ([AutoDownloadLimits.maxFileBytes]). Jinak se počká, až se stáhne jinak.
 */
fun autosaveAction(candidate: AutosaveCandidate, settings: AutosaveScopeSettings, limits: AutoDownloadLimits): AutosaveAction {
    if (candidate.isVideo) {
        if (!settings.videos) return AutosaveAction.Skip
        if (candidate.size > settings.maxVideoBytes) return AutosaveAction.Skip
        return if (candidate.size in 1..limits.maxFileBytes) AutosaveAction.Download else AutosaveAction.WhenDownloaded
    }
    if (!settings.photos) return AutosaveAction.Skip
    return if (limits.photos) AutosaveAction.Download else AutosaveAction.WhenDownloaded
}

private const val MB = 1024L * 1024

/** Volby největšího videa v nastavení (v rozsahu, který TDLib bere). */
val AUTOSAVE_VIDEO_SIZES: List<Long> = listOf(
    10L * MB, 50L * MB, 100L * MB, 500L * MB, 1000L * MB, 4000L * MB,
)

/** Předvolba největšího videa, když se videa zapínají poprvé. */
const val AUTOSAVE_DEFAULT_VIDEO_BYTES: Long = 100L * MB

