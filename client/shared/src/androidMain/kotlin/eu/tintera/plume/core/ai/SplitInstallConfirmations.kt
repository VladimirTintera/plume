package eu.tintera.plume.core.ai

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject

/**
 * Souhlas, který Google Play chce před instalací součásti (`REQUIRES_USER_CONFIRMATION`), mezi
 * [PlayLiteRtRuntime] a aktivitou. Runtime běží mimo obrazovku (instalaci může spustit Nastavení
 * i doinstalace u už staženého modelu), dialog Play ale potřebuje aktivitu — ukáže ho
 * [SplitInstallConfirmationHost] v kořeni `MainActivity`.
 *
 * Play o souhlas žádá podle dokumentace u aplikace, kterou nenainstaloval on, a u větší součásti
 * na mobilních datech; dřív to aplikace brala jako chybu a hned po „Stáhnout model" ukázala
 * „Model se nepodařilo stáhnout".
 */
class SplitInstallConfirmations {

    /**
     * Žádost o souhlas. [declined] ji ukončí jako zastavené stahování (uživatel řekl ne),
     * [failed] jako chybu (dialog nešel ukázat).
     */
    class Request(val state: SplitInstallSessionState, val declined: () -> Unit, val failed: () -> Unit)

    private val _pending = MutableStateFlow<Request?>(null)

    /** Žádost, která čeká na ukázání dialogu. */
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    /** Žádost, jejíž dialog je právě na obrazovce — na ni se vztahuje výsledek. */
    @Volatile
    internal var shown: Request? = null

    fun request(request: Request) {
        _pending.value = request
    }

    /** Instalace skončila jinak (zastavená, zrušená): neukázaný dialog už nemá smysl. */
    fun withdraw(sessionId: Int) {
        _pending.update { current -> current?.takeUnless { it.state.sessionId() == sessionId } }
    }

    internal fun markShown(request: Request) {
        shown = request
        _pending.update { current -> current?.takeUnless { it === request } }
    }
}

/**
 * Ukáže dialog Play pro čekající žádost ([SplitInstallConfirmations]). Patří do kořene aktivity,
 * ne do obrazovky Nastavení: instalace běží dál, i když uživatel odejde, a dialog se ukáže tam,
 * kde zrovna je.
 *
 * Výsledek dialogu: souhlas pokračuje sám (Play instalaci rozběhne a [PlayLiteRtRuntime] dostane
 * stahování přes listener), odmítnutí stahování zastaví — jinak by Nastavení ukazovalo stahování,
 * které nikdy nezačne.
 */
@Composable
fun SplitInstallConfirmationHost(confirmations: SplitInstallConfirmations = koinInject()) {
    val context = LocalContext.current
    val manager = remember(context) { SplitInstallManagerFactory.create(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val request = confirmations.shown ?: return@rememberLauncherForActivityResult
        confirmations.shown = null
        if (result.resultCode != Activity.RESULT_OK) request.declined()
    }
    val pending by confirmations.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val request = pending ?: return@LaunchedEffect
        confirmations.markShown(request)
        val started = runCatching { manager.startConfirmationDialogForResult(request.state, launcher) }
            .onFailure { Logger.withTag("OnDeviceAi").w(it) { "Dialog Play se nepodařilo ukázat" } }
            .getOrDefault(false)
        if (!started) {
            confirmations.shown = null
            request.failed()
        }
    }
}
