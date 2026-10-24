package eu.tintera.plume.wear

import android.content.Context
import androidx.wear.phone.interactions.notifications.BridgingConfig
import androidx.wear.phone.interactions.notifications.BridgingManager
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val logger = Logger.withTag("WearBridging")

/**
 * Zrcadlení notifikací telefonní aplikace na hodinky: vypnuté, dokud jsou hodinky přihlášené —
 * notifikace pak chodí vlastním pushem a zrcadlené by přišly podruhé. Po odhlášení se zapne
 * zpátky, ať uživatel o notifikace na hodinkách nepřijde. Týká se aplikace se stejným id na
 * spárovaném telefonu (`BridgingManager`).
 */
class PhoneBridging(private val context: Context, private val scope: CoroutineScope) {

    fun start(session: TelegramSession) {
        scope.launch {
            session.authStage
                .map { it == AuthStage.Ready }
                .distinctUntilChanged()
                .collect { loggedIn ->
                    runCatching {
                        BridgingManager.fromContext(context).setConfig(BridgingConfig.Builder(context, !loggedIn).build())
                    }.onSuccess { logger.d { "Zrcadlení notifikací z telefonu: ${if (loggedIn) "vypnuto" else "zapnuto"}." } }
                        .onFailure { logger.w(it) { "Zrcadlení notifikací se nepodařilo nastavit." } }
                }
        }
    }
}
