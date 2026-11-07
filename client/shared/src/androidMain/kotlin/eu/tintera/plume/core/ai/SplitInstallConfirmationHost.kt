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
import eu.tintera.ai.core.SplitInstallConfirmations
import org.koin.compose.koinInject

/**
 * Ukáže dialog Play pro čekající žádost ([SplitInstallConfirmations]). Patří do kořene aktivity,
 * ne do obrazovky Nastavení: instalace běží dál, i když uživatel odejde, a dialog se ukáže tam,
 * kde zrovna je.
 *
 * Výsledek dialogu: souhlas pokračuje sám (Play instalaci rozběhne a `PlayLiteRtRuntime` dostane
 * stahování přes listener), odmítnutí stahování zastaví — jinak by Nastavení ukazovalo stahování,
 * které nikdy nezačne.
 */
@Composable
fun SplitInstallConfirmationHost(confirmations: SplitInstallConfirmations = koinInject()) {
    val context = LocalContext.current
    val manager = remember(context) { SplitInstallManagerFactory.create(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val request = confirmations.takeShown() ?: return@rememberLauncherForActivityResult
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
            confirmations.takeShown()
            request.failed()
        }
    }
}
