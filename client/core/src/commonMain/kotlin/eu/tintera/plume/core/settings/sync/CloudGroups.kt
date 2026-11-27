package eu.tintera.plume.core.settings.sync

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.video.MAX_VIDEO_POSITIONS
import eu.tintera.plume.core.video.VideoPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * Formát synchronizace přes úložiště bota: skupiny, sloučení, kusy v úložišti a rozkoukaná
 * videa. V `core`, protože ho sdílí telefon a desktop (`CloudSync` ve `shared`, všechny skupiny)
 * s televizí (jen rozkoukaná videa). Data, která zná jen `shared` (záložky, barvy chatů, štítky,
 * zkratky, volby `AppSettings`), mají své funkce v `CloudSyncData.kt` ve `shared`.
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

    /**
     * Vlastní štítky účtu a jejich přiřazení k chatům ([StoredChatLabel], [StoredLabelAssignment]).
     * Jedna skupina, ale položka na štítek a na dvojici chat–štítek: smazání štítku nebo
     * odebrání z chatu na jednom zařízení se tak slévá po kusech, ne přepsáním celého seznamu.
     */
    ChatLabels("plume_chat_labels"),

    /**
     * Vlastní textové zkratky ([TextShortcut]). Společné pro všechny účty, takže se do úložiště
     * každého účtu píše totéž (jako volby vzhledu); slévá se po zkratkách podle jejich id.
     */
    TextShortcuts("plume_text_shortcuts"),
}

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


/** Skupina, jak leží v úložišti. */
sealed interface CloudRemoteGroup {
    data class Present(val entries: SyncEntries, val parts: Int) : CloudRemoteGroup

    /** Novější formát, nebo rozepsaný zápis: nepřepisovat, nebrat. */
    data object Unsupported : CloudRemoteGroup
}

private val logger = Logger.withTag("CloudSync")

/** Skupiny [groups] z úložiště: záhlaví všech naráz, pak kusy těch, které tam jsou. */
suspend fun CloudStore.readGroups(groups: List<CloudSyncGroup>): Result<Map<CloudSyncGroup, CloudRemoteGroup>> = runCatching {
    val headers = get(groups.map { it.cloudKey }).getOrThrow()
    groups.mapNotNull { group ->
        val header = headers[group.cloudKey]?.let(::decodeHeader) ?: return@mapNotNull null
        if (header.v > CLOUD_FORMAT_VERSION) return@mapNotNull group to CloudRemoteGroup.Unsupported
        val keys = (0 until header.n.coerceIn(0, MAX_CLOUD_PARTS)).map { cloudPartKey(group, it) }
        val parts = get(keys).getOrThrow()
        // Chybějící kus (zápis jiného zařízení skončil uprostřed): skupina se tentokrát přeskočí.
        if (keys.any { it !in parts }) return@mapNotNull group to CloudRemoteGroup.Unsupported
        val entries = decodeEntries(keys.joinToString("") { parts.getValue(it) }) ?: return@mapNotNull group to CloudRemoteGroup.Unsupported
        group to CloudRemoteGroup.Present(entries, header.n)
    }.toMap()
}

/**
 * Zapíše skupinu: kusy napřed, záhlaví nakonec — čtení uprostřed zápisu vidí buď staré, nebo
 * celé nové. [previousParts] je počet kusů v úložišti před zápisem; přebývající se smažou.
 */
suspend fun CloudStore.writeGroup(group: CloudSyncGroup, entries: SyncEntries, previousParts: Int) {
    val chunks = chunkForCloud(encodeEntries(entries))
    if (chunks.size > MAX_CLOUD_PARTS) {
        logger.w { "Skupina ${group.name} se do úložiště nevejde (${chunks.size} kusů)." }
        return
    }
    chunks.forEachIndexed { index, chunk -> set(cloudPartKey(group, index), chunk).getOrThrow() }
    set(group.cloudKey, encodeHeader(CloudHeader(n = chunks.size))).getOrThrow()
    if (previousParts > chunks.size) delete((chunks.size until previousParts).map { cloudPartKey(group, it) })
}

private fun cloudPartKey(group: CloudSyncGroup, index: Int) = "${group.cloudKey}_$index"
