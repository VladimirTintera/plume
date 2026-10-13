package eu.tintera.plume.core.calls

import org.webrtc.CapturerObserver
import java.lang.reflect.Method

/**
 * Sdílená kamera ntgcalls (`io.github.pytgcalls.devices.SmartVideoSource`): jeden Camera2
 * capturer na proces, který si zdroj kamery v knihovně (`JavaVideoCapturerModule`) bere
 * přes `getInstance` — se stejným rozměrem ho PŘEVEZME („Reusing video source") a jiné id
 * kamery na něm jen přepne (`switchCamera`).
 *
 * Toho se využívá dvakrát:
 * - **Přepnutí přední ↔ zadní** bez výměny zdroje, která by kameru zhasla (viz
 *   `docs/poznatky/hovory.md`, „Videohovory").
 * - **Náhled před spojením**: kamera se otevře už při vytáčení a snímky jdou do náhledu.
 *
 * **Zdroj knihovny NESMÍ převzít kameru, která už běží** — zablokuje to celý přenos (viz
 * `docs/poznatky/hovory.md`, „Zablokování při převzetí běžící kamery"). Náhled se proto
 * před spuštěním přenosu napevno uvolní ([releaseNow]) a stejně tak kamera po vypnutí za
 * hovoru ([releaseCurrent]); přepnutí přední ↔ zadní nový zdroj nezakládá, to smí.
 *
 * Třída je v knihovně neveřejná, volá se reflexí (keep pravidla ntgcalls drží jména). Když
 * to po aktualizaci knihovny nepůjde, metody vrátí `null` / `false` a volající se zařídí
 * bez sdílené kamery (náhled až po spojení, přepnutí výměnou zdroje).
 */
internal object SharedCamera {
    private val type: Class<*>? by lazy { runCatching { Class.forName("io.github.pytgcalls.devices.SmartVideoSource") }.getOrNull() }

    private fun method(name: String, vararg parameters: Class<*>?): Method? =
        runCatching { type?.getDeclaredMethod(name, *parameters)?.apply { isAccessible = true } }.getOrNull()

    private val getInstance by lazy {
        method("getInstance", Boolean::class.javaPrimitiveType, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    }
    private val setCapturerObserver by lazy { method("setCapturerObserver", CapturerObserver::class.java) }
    private val open by lazy { method("open") }
    private val release by lazy { method("release") }
    private val instances by lazy { runCatching { type?.getDeclaredField("instance")?.apply { isAccessible = true } }.getOrNull() }
    private val pendingRelease by lazy { runCatching { type?.getDeclaredField("releaseRunnable")?.apply { isAccessible = true } }.getOrNull() }
    private val cancelOnUiThread by lazy {
        runCatching { Class.forName("io.github.pytgcalls.AndroidUtils").getMethod("cancelRunOnUIThread", Runnable::class.java) }.getOrNull()
    }

    /**
     * Kamera [id] v rozměru zdroje knihovny: založí ji, nebo převezme běžící a přepne na [id].
     * `null`, když sdílenou kameru použít nejde.
     */
    fun use(id: String, width: Int, height: Int, fps: Int): Any? =
        runCatching { getInstance?.invoke(null, false, id, width, height, fps) }.getOrNull()

    /** Spustí kameru a snímky pošle [observer] (dokud si ji nevezme zdroj knihovny). */
    fun preview(source: Any, observer: CapturerObserver): Boolean = runCatching {
        val setObserver = setCapturerObserver ?: return false
        val start = open ?: return false
        setObserver.invoke(source, observer)
        start.invoke(source)
        true
    }.getOrDefault(false)

    /**
     * Napevno uvolní kameru [source]: zastaví ji hned, ne za 200 ms jako knihovna
     * (`softRelease`). Zruší i takové odložené uvolnění — to by jinak po 200 ms vynulovalo
     * odkaz na kameru, kterou si mezitím založil nový zdroj, a dvě by běžely naráz.
     */
    fun releaseNow(source: Any) {
        runCatching {
            val scheduled = pendingRelease?.get(source) as? Runnable
            if (scheduled != null) cancelOnUiThread?.invoke(null, scheduled)
        }
        runCatching { release?.invoke(source) }
    }

    /** Napevno uvolní sdílenou kameru, ať ji drží kdokoli (po vypnutí kamery za hovoru). */
    fun releaseCurrent() {
        val current = runCatching { (instances?.get(null) as? Array<*>)?.getOrNull(0) }.getOrNull() ?: return
        releaseNow(current)
    }
}
