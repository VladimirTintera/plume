package eu.tintera.plume.core.settings.sync

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AccountSettings
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.AuthStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Synchronizace nastavení, rozkoukaných videí, záložek a barev chatů mezi zařízeními přes úložiště bota
 * ([CloudStore], `@PlumeSyncBot`). Bez vlastního serveru: data leží u Telegramu na účtu.
 *
 * - **Kdy**: po každém `Ready` (start aplikace i přihlášení), po místní změně (s prodlevou
 *   [debounceMs], pozice se za přehrávání zapisuje každých 10 s) a každých [periodMs] — server
 *   změny neohlašuje, desktop otevřený celý den by jinak změnu z telefonu neviděl.
 * - **Jak**: trojcestně ([merge3]) proti základu z poslední synchronizace (`AccountSettings.cloudSyncBase`).
 *   Volby a barvy chatů změněné na obou stranách vezme úložiště, pozice a záložky novější zápis.
 * - **Za účet**: každý účet má úložiště vlastní. Volby (vzhled, přehrávání) jdou přes všechny
 *   účty, rozkoukaná videa, záložky a barvy chatů jen ty, které k účtu patří — do úložiště cizího účtu
 *   nepatří, co si uživatel prohlížel jinde.
 * - **Chyba úložiště** (bez sítě, bot nenalezen) jen zapíše do logu; zkusí se při další příležitosti.
 */
@OptIn(FlowPreview::class)
class CloudSync(
    private val settings: AppSettings,
    private val account: AccountSettings,
    private val store: CloudStore,
    /** Id uživatele účtu (`ChatRepository.savedMessagesChatId`); `null`, dokud není znám. */
    private val accountId: StateFlow<Long?>,
    /** `TelegramSession.authStage`. */
    private val authStage: StateFlow<AuthStage>,
    private val scope: CoroutineScope,
    private val debounceMs: Long = CHANGE_DEBOUNCE_MS,
    private val periodMs: Long = PULL_PERIOD_MS,
) {
    private val logger = Logger.withTag("CloudSync")

    /** Synchronizace se nesmí prolnout (místní změna, pravidelné čtení, nové `Ready`). */
    private val mutex = Mutex()

    /** Místní stav po poslední synchronizaci; jeho opětovný výskyt (náš vlastní zápis) nic nespouští. */
    private var synced: Map<CloudSyncGroup, SyncEntries>? = null

    fun start() {
        scope.launch {
            authStage.collectLatest { stage ->
                if (stage == AuthStage.Ready) watch()
            }
        }
    }

    private suspend fun watch() = coroutineScope {
        val accountId = accountId.filterNotNull().first()
        sync(accountId)
        launch {
            while (true) {
                delay(periodMs)
                sync(accountId)
            }
        }
        combine(
            settings.syncedPreferences.filterNotNull(),
            settings.videoPositions,
            settings.messageBookmarks,
            settings.chatColors,
            // Štítky a přiřazení jako jeden tok: `combine` se pěti toky typově končí.
            combine(settings.chatLabels, settings.labelAssignments, settings.textShortcuts, ::Triple),
        ) { preferences, positions, bookmarks, colors, (labels, assignments, shortcuts) ->
            localEntries(preferences, positions, bookmarks, colors, labels, assignments, shortcuts, accountId)
        }
            .distinctUntilChanged()
            .filter { it != synced }
            .debounce(debounceMs)
            .collect { sync(accountId) }
    }

    /** Jedna synchronizace; vrací `false`, když úložiště nešlo přečíst. Pro testy veřejná. */
    suspend fun sync(accountId: Long): Boolean = mutex.withLock {
        runCatching { syncOnce(accountId) }
            .onFailure { logger.w(it) { "Synchronizace přes úložiště bota selhala." } }
            .getOrDefault(false)
    }

    private suspend fun syncOnce(accountId: Long): Boolean {
        val remote = store.readGroups(CloudSyncGroup.entries).getOrElse { return false }
        val base = decodeBase(account.cloudSyncBase())?.takeIf { it.accountId == accountId } ?: CloudSyncBase(accountId)
        val preferences = settings.syncedPreferences.filterNotNull().first()
        val local = localEntries(
            preferences,
            settings.videoPositions.value,
            settings.messageBookmarks.value,
            settings.chatColors.value,
            settings.chatLabels.value,
            settings.labelAssignments.value,
            settings.textShortcuts.value,
            accountId,
        )

        val merged = CloudSyncGroup.entries.filterNot { remote[it] is CloudRemoteGroup.Unsupported }.associateWith { group ->
            val theirs = (remote[group] as? CloudRemoteGroup.Present)?.entries.orEmpty()
            merge3(base.groups[group].orEmpty(), local.getValue(group), theirs, resolverFor(group))
        }

        // Sem: volby, pozice a záložky, které se slučováním změnily.
        val preferenceChanges = SYNCED_PREFERENCE_GROUPS.flatMap { (group, names) ->
            merged[group]?.let { preferenceChanges(it, preferences, names).entries } ?: emptyList()
        }.associate { it.key to it.value }
        if (preferenceChanges.isNotEmpty()) settings.applySyncedPreferences(preferenceChanges)
        merged[CloudSyncGroup.Positions]?.takeIf { it != local[CloudSyncGroup.Positions] }?.let { entries ->
            val snapshot = local.getValue(CloudSyncGroup.Positions)
            settings.updateVideoPositions { current -> positionsWith(entries, current, accountId, snapshot) }
        }
        merged[CloudSyncGroup.Bookmarks]?.takeIf { it != local[CloudSyncGroup.Bookmarks] }?.let { entries ->
            settings.setMessageBookmarks(bookmarksWith(entries, settings.messageBookmarks.value, accountId))
        }
        merged[CloudSyncGroup.ChatColors]?.takeIf { it != local[CloudSyncGroup.ChatColors] }?.let { entries ->
            settings.setChatColors(chatColorsWith(entries, settings.chatColors.value, accountId))
        }
        merged[CloudSyncGroup.ChatLabels]?.takeIf { it != local[CloudSyncGroup.ChatLabels] }?.let { entries ->
            settings.setChatLabels(chatLabelsWith(entries, settings.chatLabels.value, accountId))
            settings.setLabelAssignments(labelAssignmentsWith(entries, settings.labelAssignments.value, accountId))
        }
        merged[CloudSyncGroup.TextShortcuts]?.takeIf { it != local[CloudSyncGroup.TextShortcuts] }?.let { entries ->
            settings.setTextShortcuts(textShortcutsWith(entries))
        }

        // Tam: skupiny, které se v úložišti liší.
        merged.forEach { (group, entries) ->
            val present = remote[group] as? CloudRemoteGroup.Present
            val theirs = present?.entries.orEmpty()
            if (entries == theirs && (present != null || entries.isEmpty())) return@forEach
            store.writeGroup(group, entries, previousParts = present?.parts ?: 0)
        }

        account.setCloudSyncBase(encodeBase(CloudSyncBase(accountId, base.groups + merged)))
        synced = merged
        logger.i { "Synchronizováno: " + merged.entries.joinToString { (group, entries) -> "${group.name} ${entries.size}" } }
        return true
    }

    private fun localEntries(
        preferences: Map<String, kotlinx.serialization.json.JsonPrimitive>,
        positions: List<eu.tintera.plume.core.video.VideoPosition>,
        bookmarks: List<eu.tintera.plume.core.bookmarks.MessageBookmark>,
        colors: List<eu.tintera.plume.core.chatcolor.StoredChatColor>,
        labels: List<eu.tintera.plume.core.chatlabels.StoredChatLabel>,
        assignments: List<eu.tintera.plume.core.chatlabels.StoredLabelAssignment>,
        shortcuts: List<eu.tintera.plume.core.shortcuts.TextShortcut>,
        accountId: Long,
    ): Map<CloudSyncGroup, SyncEntries> = buildMap {
        SYNCED_PREFERENCE_GROUPS.forEach { (group, names) -> put(group, preferenceEntries(preferences, names)) }
        put(CloudSyncGroup.Positions, positionEntries(positions, accountId))
        put(CloudSyncGroup.Bookmarks, bookmarkEntries(bookmarks, accountId))
        put(CloudSyncGroup.ChatColors, chatColorEntries(colors, accountId))
        put(CloudSyncGroup.ChatLabels, chatLabelEntries(labels, assignments, accountId))
        put(CloudSyncGroup.TextShortcuts, textShortcutEntries(shortcuts))
    }

    private fun resolverFor(group: CloudSyncGroup): (String, String) -> String = when (group) {
        CloudSyncGroup.Appearance, CloudSyncGroup.Playback, CloudSyncGroup.ChatColors, CloudSyncGroup.ChatLabels,
        CloudSyncGroup.TextShortcuts -> ::resolvePreference
        CloudSyncGroup.Positions -> ::resolvePosition
        CloudSyncGroup.Bookmarks -> ::resolveBookmark
    }

    companion object {
        const val CHANGE_DEBOUNCE_MS = 15_000L
        const val PULL_PERIOD_MS = 15 * 60_000L
    }
}
