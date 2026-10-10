package eu.tintera.plume.core.widget

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
import kotlinx.coroutines.flow.mapNotNull
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
 */
class ChatsWidgetUpdater(
    private val context: Context,
    private val chats: ChatRepository,
    private val authStage: StateFlow<AuthStage>,
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

    private fun snapshots(): Flow<WidgetSnapshot> =
        combine(authStage, chats.chats(ChatFolder.Main), chats.savedMessagesChatId, lockSettings) { stage, state, saved, lock ->
            when {
                stage is AuthStage.WaitPhoneNumber || stage is AuthStage.LoggingOut -> Input.LoggedOut
                stage is AuthStage.Ready && state is ChatListState.Ready -> Input.Chats(state, saved, showPreviews = lock == null)
                else -> null
            }
        }.mapNotNull { input ->
            when (input) {
                null -> null
                Input.LoggedOut -> WidgetSnapshot(loggedIn = false)
                is Input.Chats -> WidgetSnapshot(
                    loggedIn = true,
                    chats = widgetChats(input.state.chats, input.savedMessagesChatId).map { chat ->
                        WidgetChat(
                            id = chat.id,
                            title = chat.title,
                            // Text se překládá teď, v jazyce platném při zápisu; po změně jazyka ho
                            // přepíše další změna seznamu.
                            preview = if (input.showPreviews) chat.lastMessagePreview?.resolveNow() else null,
                            unread = chat.unreadCount,
                            muted = chat.isMuted,
                            timestamp = chat.lastMessageTimestamp,
                        )
                    },
                )
            }
        }

    private sealed interface Input {
        data object LoggedOut : Input
        data class Chats(val state: ChatListState.Ready, val savedMessagesChatId: Long?, val showPreviews: Boolean) : Input
    }

    private companion object {
        const val SETTLE_MS = 1_000L
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
