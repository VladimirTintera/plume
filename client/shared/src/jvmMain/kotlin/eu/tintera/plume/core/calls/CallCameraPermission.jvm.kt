package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.tintera.plume.core.audio.RecordPermission
import javax.swing.SwingUtilities

/**
 * macOS (zabalený `Plume.app`, [MacCamera]): systémový dotaz na kameru a mikrofon hned při
 * vytočení nebo přijetí. Dřív se ptala až knihovna hovoru při spojování — přenos pak minutu
 * čekal na odpověď, spojení se obnovovalo a jednou při tom knihovna spadla.
 *
 * Jinde (Windows, Linux, ladicí běh) se aplikace neptá: odmítnutá kamera jen nedá obraz.
 */
@Composable
actual fun rememberCallCameraPermission(): RecordPermission = remember { MacPermission(video = true) }

@Composable
actual fun rememberCallMicrophonePermission(): RecordPermission = remember { MacPermission(video = false) }

private class MacPermission(private val video: Boolean) : RecordPermission {
    override fun request(onResult: (granted: Boolean) -> Unit) {
        if (!MacCamera.available) {
            onResult(true)
            return
        }
        // Dotaz čeká na odpověď uživatele, takže mimo vlákno UI; výsledek zpátky do něj.
        Thread({
            val microphone = MacCamera.requestAccess(video = false)
            // U videohovoru rozhoduje kamera; bez mikrofonu by šel obraz bez zvuku, ten se
            // přesto přijme (protistrana uvidí, že je ztlumený).
            val granted = if (video) MacCamera.requestAccess(video = true) else microphone
            SwingUtilities.invokeLater { onResult(granted) }
        }, "call-permission").apply { isDaemon = true }.start()
    }
}
