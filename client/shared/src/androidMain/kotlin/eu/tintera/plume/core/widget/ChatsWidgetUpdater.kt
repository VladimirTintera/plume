package eu.tintera.plume.core.widget

import eu.tintera.plume.core.accounts.AccountSlot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emitAll
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountSession
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.lock.AppLockSettings
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.ui.resolveNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.saved_messages
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File

/**
 * Drží podklady widgetu s chaty ([ChatsWidget]) aktuální, dokud proces běží: seznam chatů se
 * převede na [WidgetSnapshot], uloží do souboru a widget se překreslí.
 *
 * - **Soubor, ne dotaz při kreslení.** Widget kreslí launcher i tehdy, když proces neběží; TDLib
 *   se v tu chvíli ptát nedá, a i kdyby, start klienta kvůli widgetu by trval sekundy.
 * - **Po vteřině klidu** (`debounce`): seznam chatů se při startu skládá po dávkách a každá
 *   zpráva ho přeskládá — překreslovat widget po každém updatu by jen pálilo baterii.
 * - **Se zámkem aplikace bez náhledů zpráv.** Widget je na ploše vidět komukoli, kdo drží
 *   telefon; zámek, který chrání obsah chatů, by jinak měl díru hned vedle ikony aplikace.
 * - **Odhlášení widget vyprázdní** (jen věta „přihlas se"), ať po účtu na ploše nic nezůstane.
 *   Zapisuje se jen v jasných stavech (přihlášeno a seznam načtený, nebo odhlášeno) — při startu,
 *   než se TDLib rozběhne, zůstává poslední známý stav.
 * - **Jeden přes všechny účty** (`docs/navrh-vice-uctu.md`, krok 16), ne za každý účet: soubor
 *   i stav widgetu jsou jedny a updatery po účtech si je přepisovaly — widget ukazoval chaty
 *   toho účtu, který se změnil naposled, a miniatury ostatních účtů mazal. Ukazuje chaty podle
 *   filtru seznamu: v režimu Všechny účty všech účtů ([mergedWidgetChats]), jinak výchozího.
 */
class ChatsWidgetUpdater(
    private val context: Context,
    /** Až za běhu: v globálním Koinu se na účty při vzniku definice sahat nesmí. */
    private val accounts: () -> Accounts,
    private val allAccounts: StateFlow<Boolean>,
    private val lockSettings: StateFlow<AppLockSettings?>,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("ChatsWidget")

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            snapshots()
                .debounce(SETTLE_MS)
                .distinctUntilChanged()
                .collect { snapshot ->
                    runCatching {
                        writeSnapshot(context, snapshot)
                        // Každý widget na ploše dostane podklady do svého stavu a překreslí se;
                        // bez widgetu stačí soubor pro první kreslení.
                        val json = encodeSnapshot(snapshot)
                        GlanceAppWidgetManager(context).getGlanceIds(ChatsWidget::class.java).forEach { id ->
                            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
                                state.toMutablePreferences().apply { this[SNAPSHOT_KEY] = json }
                            }
                            ChatsWidget().update(context, id)
                        }
                    }.onFailure { logger.w(it) { "Widget s chaty se nepodařilo obnovit." } }
                }
        }
    }

    /** Účty, jejichž chaty widget ukazuje: všechny v režimu Všechny účty, jinak výchozí. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun shownAccounts(): Flow<List<AccountSession>> =
        flow { emitAll(accounts().all) }
            .flatMapLatest { all ->
                combine(accounts().default, allAccounts) { default, showAll -> if (showAll && all.size > 1) all else listOf(default) }
            }
            .distinctUntilChanged()

    /** Stav jednoho účtu pro widget; `null` = ještě se neví (TDLib startuje, seznam se načítá). */
    private fun accountPart(account: AccountSession): Flow<Part?> {
        val chats = account.get<ChatRepository>()
        return combine(account.session.authStage, chats.chats(ChatFolder.Main), chats.savedMessagesChatId) { stage, state, saved ->
            when {
                stage is AuthStage.WaitPhoneNumber || stage is AuthStage.LoggingOut -> Part.LoggedOut
                stage is AuthStage.Ready && state is ChatListState.Ready ->
                    Part.Chats(WidgetAccountChats(account.slot, state.chats, saved), account)
                else -> null
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun snapshots(): Flow<WidgetSnapshot> =
        shownAccounts()
            .flatMapLatest { shown -> combine(shown.map(::accountPart)) { parts -> parts.toList() } }
            .combine(lockSettings) { parts, lock ->
                when {
                    // Účet, který se ještě načítá: zapsat až s ním, jinak by widget na chvíli
                    // ukázal jen část chatů.
                    parts.any { it == null } -> null
                    parts.all { it == Part.LoggedOut } -> Input.LoggedOut
                    else -> Input.Chats(parts.filterIsInstance<Part.Chats>(), showPreviews = lock == null)
                }
            }
            .filterNotNull()
            .mapLatest { input ->
                // `mapLatest`: příprava fotek může chvíli trvat a novější seznam tu starší přebíjí.
                when (input) {
                    Input.LoggedOut -> {
                        WidgetAvatars.prune(context, keep = emptySet())
                        WidgetSnapshot(loggedIn = false)
                    }
                    is Input.Chats -> {
                        val savedTitle = getString(Res.string.saved_messages)
                        val bySlot = input.parts.associateBy { it.chats.slot }
                        // Odznak účtu jen s víc účty ve widgetu; s jedním by jen zabíral avatar.
                        val badges = if (input.parts.size > 1) {
                            input.parts.associate { part -> part.chats.slot to accountBadge(part) }
                        } else {
                            emptyMap()
                        }
                        val widgetChats = coroutineScope {
                            mergedWidgetChats(input.parts.map { it.chats }).map { (slot, chat) ->
                                async {
                                    val part = bySlot.getValue(slot)
                                    val saved = chat.id == part.chats.savedMessagesChatId
                                    WidgetChat(
                                        slot = slot.id,
                                        id = chat.id,
                                        // Uložené zprávy jménem, ne jménem účtu — jako v seznamu chatů.
                                        title = if (saved) savedTitle else chat.title,
                                        // Text se překládá teď, v jazyce platném při zápisu; po změně
                                        // jazyka ho přepíše další změna seznamu.
                                        preview = if (input.showPreviews) chat.lastMessagePreview?.resolveNow() else null,
                                        unread = chat.unreadCount,
                                        muted = chat.isMuted,
                                        timestamp = chat.lastMessageTimestamp,
                                        avatar = if (saved) null else chat.avatarFileId?.let { avatar(part.files, slot, it) },
                                        savedMessages = saved,
                                        accountInitial = badges[slot]?.initial,
                                        accountAvatar = badges[slot]?.avatar,
                                    )
                                }
                            }.awaitAll()
                        }
                        // Miniatury všech zobrazených účtů najednou — po účtech by se mazaly navzájem.
                        WidgetAvatars.prune(context, keep = widgetChats.flatMap { listOfNotNull(it.avatar, it.accountAvatar) }.toSet())
                        WidgetSnapshot(loggedIn = true, chats = widgetChats)
                    }
                }
            }

    /**
     * Miniatura fotky chatu. Fotku stáhne TDLib, pokud ještě není na disku (je malá); na stažení
     * se čeká jen chvíli — widget s iniciálou je lepší než widget, který se neobnoví.
     */
    private suspend fun avatar(files: FileRepository, slot: AccountSlot, fileId: Int): String? {
        val state = withTimeoutOrNull(AVATAR_WAIT_MS) {
            files.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
        }
        val path = (state as? FileState.Ready)?.path ?: return null
        return WidgetAvatars.thumbnail(context, slot, fileId, path)
    }

    /** Iniciála a miniatura fotky účtu pro odznak na avataru řádku. */
    private suspend fun accountBadge(part: Part.Chats): Badge {
        val me = part.account.session.me().getOrNull()
        val photo = me?.photoFileId?.let { avatar(part.files, part.chats.slot, it) }
        return Badge(initial = me?.displayName?.trim()?.firstOrNull()?.uppercase() ?: "?", avatar = photo)
    }

    private data class Badge(val initial: String, val avatar: String?)

    private sealed interface Part {
        data object LoggedOut : Part
        data class Chats(val chats: WidgetAccountChats, val account: AccountSession) : Part {
            val files: FileRepository get() = account.get()
        }
    }

    private sealed interface Input {
        data object LoggedOut : Input
        data class Chats(val parts: List<Part.Chats>, val showPreviews: Boolean) : Input
    }

    private companion object {
        const val SETTLE_MS = 1_000L
        const val AVATAR_WAIT_MS = 5_000L
    }
}

private val snapshotJson = Json { ignoreUnknownKeys = true }

/** Klíč podkladů ve stavu widgetu (Glance `PreferencesGlanceStateDefinition`). */
internal val SNAPSHOT_KEY = stringPreferencesKey("chats_snapshot")

internal fun encodeSnapshot(snapshot: WidgetSnapshot): String = snapshotJson.encodeToString(snapshot)

/** Poškozený zápis se zahodí — widget pak spadne na soubor, nikdy na pád. */
internal fun decodeSnapshot(json: String): WidgetSnapshot? = runCatching { snapshotJson.decodeFromString<WidgetSnapshot>(json) }.getOrNull()

private fun snapshotFile(context: Context) = File(context.filesDir, "widget/chats.json")

/** Zapíše podklady widgetu; přes dočasný soubor, ať launcher nikdy nečte rozepsaný. */
internal suspend fun writeSnapshot(context: Context, snapshot: WidgetSnapshot) = withContext(Dispatchers.IO) {
    val file = snapshotFile(context)
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "chats.json.tmp")
    temp.writeText(encodeSnapshot(snapshot))
    temp.renameTo(file)
}

/** Poslední zapsané podklady; bez souboru (ještě nic nezapsáno) prázdné a nepřihlášené. */
internal suspend fun readSnapshot(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
    runCatching { snapshotJson.decodeFromString<WidgetSnapshot>(snapshotFile(context).readText()) }.getOrDefault(WidgetSnapshot())
}
