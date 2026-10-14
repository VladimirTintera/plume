package eu.tintera.plume.core.calls

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import co.touchlab.kermit.Logger
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.ScreenCapturerAndroid

/**
 * Snímání obrazovky pro sdílení v hovoru: mezi souhlasem uživatele (aktivita), službou
 * hovoru a knihovnou.
 *
 * - **[permission]** je výsledek systémového dialogu (`createScreenCaptureIntent`). Platí na
 *   jedno použití (Android 14+), takže každé sdílení = nový dialog.
 * - **[serviceReady]**: služba hovoru běží s typem `mediaProjection`. Od Androidu 14 musí
 *   běžet DŘÍV, než knihovna (`ScreenCapturerAndroid`) zavolá `getMediaProjection`, jinak
 *   `SecurityException` — `NtgCallMedia` na to proto čeká.
 */
internal object ScreenCapture {

    @Volatile
    var permission: Intent? = null

    val serviceReady = MutableStateFlow(false)

    /**
     * Rozměr snímání: poměr stran displeje, delší strana [LONG_SIDE] (víc protistrana na
     * telefonu nevyužije a kodér by se zbytečně dřel), sudé rozměry kvůli I420.
     */
    fun size(context: Context): Pair<Int, Int> {
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }.let { (w, h) -> w.coerceAtLeast(1) to h.coerceAtLeast(1) }
        val scale = LONG_SIDE.toFloat() / max(width, height)
        fun even(value: Float) = (value.roundToInt() / 2 * 2).coerceAtLeast(2)
        return even(width * scale) to even(height * scale)
    }

    /**
     * Ohlásí, když snímání ukončí systém — tlačítko „Zastavit sdílení" ve stavovém řádku,
     * zamčení u některých verzí. Knihovna to nepozná: `SmartVideoSource` dává
     * `ScreenCapturerAndroid` prázdný `MediaProjection.Callback` a snímek pak prostě přestane
     * chodit. `MediaProjection` ale bere víc callbacků, takže se přidá vlastní — k projekci se
     * jde reflexí přes soukromé pole `videoCapturer` (zdroj obrazovky má index 1, kamera 0).
     *
     * Projekce vzniká až s otevřením zdroje (asynchronně na vlákně knihovny), proto se zkouší
     * několikrát po sobě. `false` z [stillWanted] pokusy zastaví (sdílení mezitím skončilo).
     */
    fun watchStop(stillWanted: () -> Boolean, onStop: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var attempts = 0
        fun attempt() {
            if (!stillWanted()) return
            val projection = runCatching { currentProjection() }
                .onFailure { logger.w(it) { "Ke snímání obrazovky se nejde připojit." } }
                .getOrNull()
            if (projection != null) {
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() = onStop()
                }, handler)
                return
            }
            if (++attempts < WATCH_ATTEMPTS) handler.postDelayed(::attempt, WATCH_DELAY_MS)
            else logger.w { "Snímání obrazovky se nepodařilo najít; konec ze systému se nepozná." }
        }
        handler.post(::attempt)
    }

    private fun currentProjection(): MediaProjection? {
        val source = Class.forName("io.github.pytgcalls.devices.SmartVideoSource")
        val instances = source.getDeclaredField("instance").apply { isAccessible = true }.get(null) as Array<*>
        val screen = instances.getOrNull(1) ?: return null
        val capturer = source.getDeclaredField("videoCapturer").apply { isAccessible = true }.get(screen)
        return (capturer as? ScreenCapturerAndroid)?.mediaProjection
    }

    private val logger = Logger.withTag("ScreenCapture")
    private const val LONG_SIDE = 1280
    private const val WATCH_ATTEMPTS = 20
    private const val WATCH_DELAY_MS = 250L
}
