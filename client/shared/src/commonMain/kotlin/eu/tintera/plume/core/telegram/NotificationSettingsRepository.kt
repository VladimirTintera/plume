package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

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
}

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
