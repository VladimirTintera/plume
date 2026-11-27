package eu.tintera.plume.core.settings.sync

import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.chatcolor.StoredChatColor
import eu.tintera.plume.core.video.MAX_VIDEO_POSITIONS
import eu.tintera.plume.core.video.VideoPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * Data synchronizace přes úložiště bota (`CloudSync`) jako čisté funkce: co se slučuje, jak
 * a v jakém tvaru to leží v úložišti. Bez TDLib i bez DataStore, ať jde všechno testovat.
 */

/**
 * Skupina synchronizovaných dat. Každá má v úložišti vlastní záhlaví ([cloudKey]) a kusy
 * (`<cloudKey>_<n>`), takže se při změně jedné nemusí přepisovat ostatní.
 */
enum class CloudSyncGroup(val cloudKey: String) {
    /**
     * Mezery v seznamu, písmo zpráv a samolepek, řádky náhledu. Motiv a barva aplikace ne:
     * na desktopu vždy tmavý, na telefonu podle tapety — to je volba zařízení.
     */
    Appearance("plume_appearance"),

    /** Rychlost hlasovek a videa, ticho, rychlá reakce, odkazy, tahy v seznamu, viděné nápovědy. */
    Playback("plume_playback"),

    /** Rozkoukaná videa účtu ([VideoPosition] se zdrojem tohoto účtu). */
    Positions("plume_positions"),

    /** Záložky zpráv účtu ([MessageBookmark]). */
    Bookmarks("plume_bookmarks"),

    /** Barvy, které si uživatel dal ke konverzacím účtu ([StoredChatColor]). */
    ChatColors("plume_chat_colors"),
}

/** Názvy klíčů `AppSettings` po skupinách; zapisovat jde jen ty, které `AppSettings` zná. */
val SYNCED_PREFERENCE_GROUPS: Map<CloudSyncGroup, Set<String>> = mapOf(
    CloudSyncGroup.Appearance to setOf(
        "chat_row_gaps",
        "message_text_size",
        "chat_preview_lines",
        "sticker_size",
    ),
    CloudSyncGroup.Playback to setOf(
        "voice_speed",
        "video_speed",
        "skip_silence",
        "double_tap_reaction",
        "on_this_day",
        "open_links_in_app",
        "strip_tracking_parameters",
        "chat_swipe_right",
        "chat_swipe_left",
        "seen_gesture_hints",
    ),
)

/** Položky skupiny: id → hodnota jako text JSON. Rovnost textu = táž hodnota. */
typealias SyncEntries = Map<String, String>

/**
 * Trojcestné sloučení: [base] je stav při poslední synchronizaci, [local] a [remote] co je teď
 * tady a v úložišti. Změnu jedné strany oproti základu převezme druhá, smazání taky — a smazání
 * proti změně prohraje (rozkoukané video, které druhé zařízení mezitím sledovalo dál, nezmizí).
 * Změnily-li se obě strany, rozhodne [resolve].
 *
 * Bez základu (první synchronizace zařízení) se položky sjednotí a u týchž rozhoduje [resolve].
 */
fun merge3(
    base: SyncEntries,
    local: SyncEntries,
    remote: SyncEntries,
    resolve: (local: String, remote: String) -> String,
): SyncEntries = buildMap {
    for (id in local.keys + remote.keys) {
        val original = base[id]
        val mine = local[id]
        val theirs = remote[id]
        val merged = when {
            mine == theirs -> mine
            mine == null -> theirs.takeUnless { original != null && original == theirs }
            theirs == null -> mine.takeUnless { original != null && original == mine }
            mine == original -> theirs
            theirs == original -> mine
            else -> resolve(mine, theirs)
        }
        if (merged != null) put(id, merged)
    }
}

/** Volba změněná na obou zařízeních: vyhraje úložiště (zapsalo ho zařízení, které bylo dřív). */
fun resolvePreference(@Suppress("UNUSED_PARAMETER") local: String, remote: String): String = remote

private val syncJson = Json { ignoreUnknownKeys = true }

/** Táž rozkoukaná pozice na dvou zařízeních: vyhraje novější zápis ([VideoPosition.at]). */
fun resolvePosition(local: String, remote: String): String {
    val mine = runCatching { syncJson.decodeFromString<VideoPosition>(local) }.getOrNull() ?: return remote
    val theirs = runCatching { syncJson.decodeFromString<VideoPosition>(remote) }.getOrNull() ?: return local
    return if (mine.at > theirs.at) local else remote
}

/** Táž záložka na dvou zařízeních: vyhraje později uložená (nový otisk zprávy). */
fun resolveBookmark(local: String, remote: String): String {
    val mine = runCatching { syncJson.decodeFromString<MessageBookmark>(local) }.getOrNull() ?: return remote
    val theirs = runCatching { syncJson.decodeFromString<MessageBookmark>(remote) }.getOrNull() ?: return local
    return if (mine.savedAt > theirs.savedAt) local else remote
}

/** Volby skupiny jako položky; hodnota je primitivum JSON ve tvaru textu. */
fun preferenceEntries(values: Map<String, JsonPrimitive>, names: Set<String>): SyncEntries =
    values.filterKeys { it in names }.mapValues { (_, value) -> value.toString() }

/** Změny voleb, které z položek plynou pro `AppSettings.applySyncedPreferences`; `null` = smazat. */
fun preferenceChanges(entries: SyncEntries, current: Map<String, JsonPrimitive>, names: Set<String>): Map<String, JsonPrimitive?> =
    names.mapNotNull { name ->
        val wanted = entries[name]?.let { runCatching { syncJson.parseToJsonElement(it).jsonPrimitive }.getOrNull() }
        val now = current[name]
        if (wanted?.toString() == now?.toString()) null else name to wanted
    }.toMap()

/** Rozkoukaná videa účtu [accountId] jako položky (klíč je soubor). */
fun positionEntries(positions: List<VideoPosition>, accountId: Long): SyncEntries =
    positions.filter { it.source?.accountId == accountId }
        .associate { it.key to syncJson.encodeToString(VideoPosition.serializer(), it) }

/**
 * Celý seznam pozic po sloučení: rozkoukaná videa účtu ze [entries] a k nim, co patří jinam
 * (jiný účet, zvuk, záznamy bez zdroje). Nejnověji zapsané první, nejvýš [MAX_VIDEO_POSITIONS].
 */
fun positionsWith(
    entries: SyncEntries,
    current: List<VideoPosition>,
    accountId: Long,
    /** Položky účtu, ze kterých se slučovalo; co se od nich v [current] změnilo, mezitím zapsal přehrávač. */
    snapshot: SyncEntries = positionEntries(current, accountId),
): List<VideoPosition> {
    val fresh = current.filter { it.source?.accountId == accountId }
        .filter { snapshot[it.key] != syncJson.encodeToString(VideoPosition.serializer(), it) }
    val merged = entries.filterKeys { key -> fresh.none { it.key == key } }.values
        .mapNotNull { runCatching { syncJson.decodeFromString<VideoPosition>(it) }.getOrNull() }
    val others = current.filterNot { it.source?.accountId == accountId }
    return (fresh + merged + others).sortedByDescending { it.at }.take(MAX_VIDEO_POSITIONS)
}

/** Barvy konverzací účtu [accountId] jako položky: id chatu → název barvy. */
fun chatColorEntries(colors: List<StoredChatColor>, accountId: Long): SyncEntries =
    colors.filter { it.accountId == accountId }.associate { it.chatId.toString() to it.color }

/** Celý seznam barev po sloučení: barvy účtu ze [entries] a barvy ostatních účtů. */
fun chatColorsWith(entries: SyncEntries, current: List<StoredChatColor>, accountId: Long): List<StoredChatColor> =
    current.filterNot { it.accountId == accountId } +
        entries.mapNotNull { (chat, color) -> chat.toLongOrNull()?.let { StoredChatColor(accountId, it, color) } }

/** Záložky účtu [accountId] jako položky (klíč je chat a zpráva). */
fun bookmarkEntries(bookmarks: List<MessageBookmark>, accountId: Long): SyncEntries =
    bookmarks.filter { it.accountId == accountId }
        .associate { "${it.chatId}:${it.messageId}" to syncJson.encodeToString(MessageBookmark.serializer(), it) }

/** Celý seznam záložek po sloučení: záložky účtu ze [entries] a záložky ostatních účtů. */
fun bookmarksWith(entries: SyncEntries, current: List<MessageBookmark>, accountId: Long): List<MessageBookmark> {
    val mine = entries.values.mapNotNull { runCatching { syncJson.decodeFromString<MessageBookmark>(it) }.getOrNull() }
    return current.filterNot { it.accountId == accountId } + mine.sortedBy { it.savedAt }
}

/**
 * Úložiště bota bere hodnotu do [CLOUD_VALUE_LIMIT] znaků, takže se skupina dělí na kusy.
 * Hranice nesmí rozpůlit náhradní dvojici UTF-16 (emoji v otisku záložky): kus s osamělou
 * polovinou by se na serveru nezakódoval.
 */
fun chunkForCloud(text: String, limit: Int = CLOUD_CHUNK): List<String> {
    if (text.isEmpty()) return listOf("")
    val parts = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + limit, text.length)
        if (end < text.length && text[end - 1].isHighSurrogate()) end--
        parts += text.substring(start, end)
        start = end
    }
    return parts
}

/** Záhlaví skupiny v úložišti: verze formátu a počet kusů. */
@Serializable
data class CloudHeader(val v: Int = CLOUD_FORMAT_VERSION, val n: Int)

fun encodeEntries(entries: SyncEntries): String =
    syncJson.encodeToString(JsonObject.serializer(), JsonObject(entries.entries.sortedBy { it.key }.associate { it.key to JsonPrimitive(it.value) }))

fun decodeEntries(text: String): SyncEntries? = runCatching {
    syncJson.parseToJsonElement(text).let { it as JsonObject }
        .mapNotNull { (id, value) -> value.jsonPrimitive.contentOrNull?.let { id to it } }.toMap()
}.getOrNull()

fun encodeHeader(header: CloudHeader): String = syncJson.encodeToString(CloudHeader.serializer(), header)

fun decodeHeader(text: String): CloudHeader? = runCatching { syncJson.decodeFromString<CloudHeader>(text) }.getOrNull()

/** Základ slučování uložený v zařízení: pro který účet a stav skupin při poslední synchronizaci. */
@Serializable
data class CloudSyncBase(val accountId: Long, val groups: Map<CloudSyncGroup, Map<String, String>> = emptyMap())

fun encodeBase(base: CloudSyncBase): String = syncJson.encodeToString(CloudSyncBase.serializer(), base)

fun decodeBase(text: String?): CloudSyncBase? = text?.let { runCatching { syncJson.decodeFromString<CloudSyncBase>(it) }.getOrNull() }

/** Úložiště bota: nejvýš 4096 znaků na hodnotu; kus má rezervu. */
const val CLOUD_VALUE_LIMIT = 4096
const val CLOUD_CHUNK = 4000
const val CLOUD_FORMAT_VERSION = 1

/** Kusy skupiny jsou nejvýš tolik — 1024 klíčů na bota je strop úložiště, čtyři skupiny ho nevyčerpají. */
const val MAX_CLOUD_PARTS = 64
