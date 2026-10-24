package eu.tintera.plume.wear

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WearUnread")

/**
 * Co ukazuje dlaždice a komplikace. Systém se na ně ptá kdykoli, i když proces hodinek neběží,
 * a TDLib se za tu chvíli zeptat nedá — proto se stav drží v souboru ([read]) a zapisuje ho
 * [UnreadCountPublisher], dokud proces běží.
 */
sealed interface UnreadState {
    /** Ještě nikdy nezapsáno: čerstvá instalace, aplikace se nespustila. */
    data object Unknown : UnreadState

    data object LoggedOut : UnreadState

    /** Nepřečtené chaty v hlavním seznamu bez ztlumených. */
    data class Chats(val count: Int) : UnreadState

    companion object {
        fun read(context: Context): UnreadState {
            val count = prefs(context).getInt(KEY_COUNT, UNKNOWN)
            return when {
                count == UNKNOWN -> Unknown
                count == LOGGED_OUT -> LoggedOut
                else -> Chats(count)
            }
        }

        internal fun write(context: Context, state: UnreadState) {
            val count = when (state) {
                Unknown -> UNKNOWN
                LoggedOut -> LOGGED_OUT
                is Chats -> state.count
            }
            prefs(context).edit { putInt(KEY_COUNT, count) }
        }

        private fun prefs(context: Context) = context.getSharedPreferences("wear_unread", Context.MODE_PRIVATE)

        private const val KEY_COUNT = "count"
        private const val UNKNOWN = -1
        private const val LOGGED_OUT = -2
    }
}

/**
 * Zapisuje počet nepřečtených a žádá systém o obnovu dlaždice a komplikace.
 *
 * - **Počítá TDLib** (`ChatRepository.mainUnread`), ne součet nad seznamem chatů: ten se dotahuje
 *   po stránkách a nepřečtený chat hluboko v něm by chyběl.
 * - **Ztlumené chaty se nepočítají** — hodinky nemají připomínat, co uživatel umlčel. Stejně
 *   jako záhlaví widgetu na telefonu.
 * - **Zapisuje se jen v jasných stavech** (přihlášeno a seznam načtený, nebo odhlášeno), jako
 *   widget telefonu: při startu, než se TDLib rozběhne, zůstává poslední známý počet, jinak by
 *   dlaždice při každém studeném startu problikla nulou.
 * - **Po vteřině klidu**: při načítání seznamu a hromadném čtení se počet mění v rychlém sledu
 *   a systém obnovu dlaždice stejně omezuje.
 */
class UnreadCountPublisher(private val context: Context, private val scope: CoroutineScope) {

    @OptIn(FlowPreview::class)
    fun start(session: TelegramSession, chats: ChatRepository) {
        scope.launch {
            combine(session.authStage, chats.chats(), chats.mainUnread) { stage, list, unread ->
                when {
                    stage == AuthStage.Ready && list is ChatListState.Ready -> UnreadState.Chats(unread.unmutedChats)
                    stage.isLoggedOut() -> UnreadState.LoggedOut
                    else -> null
                }
            }
                .filterNotNull()
                .debounce(1.seconds)
                .distinctUntilChanged()
                .collect { state ->
                    if (state == UnreadState.read(context)) return@collect
                    UnreadState.write(context, state)
                    requestUpdates()
                }
        }
    }

    private fun requestUpdates() {
        runCatching {
            TileService.getUpdater(context).requestUpdate(UnreadTileService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, UnreadComplicationService::class.java))
                .requestUpdateAll()
        }.onFailure { logger.w(it) { "Obnova dlaždice a komplikace se nepodařila." } }
    }
}

/** Přihlášení neběží: čeká se na QR kód, heslo, nebo se odhlašuje. */
internal fun AuthStage.isLoggedOut(): Boolean = when (this) {
    AuthStage.Connecting, AuthStage.Ready, is AuthStage.Failed -> false
    else -> true
}
