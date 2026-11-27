package eu.tintera.plume.core.settings.sync

import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.chatcolor.StoredChatColor
import eu.tintera.plume.core.chatlabels.StoredChatLabel
import eu.tintera.plume.core.chatlabels.StoredLabelAssignment
import eu.tintera.plume.core.shortcuts.TextShortcut
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/*
 * Data synchronizace přes úložiště bota (`CloudSync`) jako čisté funkce: co se slučuje, jak
 * a v jakém tvaru to leží v úložišti. Bez TDLib i bez DataStore, ať jde všechno testovat.
 * Skupiny, sloučení, kusy a rozkoukaná videa jsou v `core` (`CloudGroups.kt`), sdílí je televize.
 */

private val syncJson = Json { ignoreUnknownKeys = true }

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

/** Barvy konverzací účtu [accountId] jako položky: id chatu → název barvy. */
fun chatColorEntries(colors: List<StoredChatColor>, accountId: Long): SyncEntries =
    colors.filter { it.accountId == accountId }.associate { it.chatId.toString() to it.color }

/** Celý seznam barev po sloučení: barvy účtu ze [entries] a barvy ostatních účtů. */
fun chatColorsWith(entries: SyncEntries, current: List<StoredChatColor>, accountId: Long): List<StoredChatColor> =
    current.filterNot { it.accountId == accountId } +
        entries.mapNotNull { (chat, color) -> chat.toLongOrNull()?.let { StoredChatColor(accountId, it, color) } }

/** Štítek v úložišti: id je v klíči položky, účet dává úložiště samo. */
@kotlinx.serialization.Serializable
private data class SyncedLabel(val name: String, val color: String, val order: Int = 0)

private const val LABEL_PREFIX = "label:"
private const val ASSIGNMENT_PREFIX = "chat:"

/** Štítky a přiřazení účtu [accountId] jako položky: `label:<id>` → štítek, `chat:<chat>:<štítek>` → „1". */
fun chatLabelEntries(labels: List<StoredChatLabel>, assignments: List<StoredLabelAssignment>, accountId: Long): SyncEntries =
    labels.filter { it.accountId == accountId }.associate {
        LABEL_PREFIX + it.id to syncJson.encodeToString(SyncedLabel.serializer(), SyncedLabel(it.name, it.color, it.order))
    } + assignments.filter { it.accountId == accountId }.associate { "$ASSIGNMENT_PREFIX${it.chatId}:${it.labelId}" to "1" }

/** Štítky po sloučení: štítky účtu ze [entries] a štítky ostatních účtů. */
fun chatLabelsWith(entries: SyncEntries, current: List<StoredChatLabel>, accountId: Long): List<StoredChatLabel> =
    current.filterNot { it.accountId == accountId } + entries.mapNotNull { (key, value) ->
        val id = key.removePrefix(LABEL_PREFIX).takeIf { key.startsWith(LABEL_PREFIX) && it.isNotEmpty() } ?: return@mapNotNull null
        runCatching { syncJson.decodeFromString(SyncedLabel.serializer(), value) }.getOrNull()
            ?.let { StoredChatLabel(accountId, id, it.name, it.color, it.order) }
    }

/** Přiřazení po sloučení: přiřazení účtu ze [entries] a přiřazení ostatních účtů. */
fun labelAssignmentsWith(entries: SyncEntries, current: List<StoredLabelAssignment>, accountId: Long): List<StoredLabelAssignment> =
    current.filterNot { it.accountId == accountId } + entries.keys.mapNotNull { key ->
        if (!key.startsWith(ASSIGNMENT_PREFIX)) return@mapNotNull null
        val parts = key.removePrefix(ASSIGNMENT_PREFIX).split(":", limit = 2)
        val chat = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val label = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        StoredLabelAssignment(accountId, chat, label)
    }

/** Zkratka v úložišti; id je klíč položky. */
@kotlinx.serialization.Serializable
private data class SyncedShortcut(val name: String, val text: String, val order: Int = 0)

/** Textové zkratky jako položky: id → název, text a pořadí. */
fun textShortcutEntries(shortcuts: List<TextShortcut>): SyncEntries =
    shortcuts.associate { it.id to syncJson.encodeToString(SyncedShortcut.serializer(), SyncedShortcut(it.name, it.text, it.order)) }

/** Zkratky po sloučení; poškozená položka se zahodí. */
fun textShortcutsWith(entries: SyncEntries): List<TextShortcut> =
    entries.mapNotNull { (id, value) ->
        runCatching { syncJson.decodeFromString(SyncedShortcut.serializer(), value) }.getOrNull()
            ?.takeIf { id.isNotEmpty() && it.name.isNotEmpty() }
            ?.let { TextShortcut(id, it.name, it.text, it.order) }
    }.sortedWith(compareBy({ it.order }, { it.name }))

/** Záložky účtu [accountId] jako položky (klíč je chat a zpráva). */
fun bookmarkEntries(bookmarks: List<MessageBookmark>, accountId: Long): SyncEntries =
    bookmarks.filter { it.accountId == accountId }
        .associate { "${it.chatId}:${it.messageId}" to syncJson.encodeToString(MessageBookmark.serializer(), it) }

/** Celý seznam záložek po sloučení: záložky účtu ze [entries] a záložky ostatních účtů. */
fun bookmarksWith(entries: SyncEntries, current: List<MessageBookmark>, accountId: Long): List<MessageBookmark> {
    val mine = entries.values.mapNotNull { runCatching { syncJson.decodeFromString<MessageBookmark>(it) }.getOrNull() }
    return current.filterNot { it.accountId == accountId } + mine.sortedBy { it.savedAt }
}
