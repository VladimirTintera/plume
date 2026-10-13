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
 * - **Náhled před spojením**: kamera se otevře už při vytáčení a snímky jdou do náhledu;
 *   zdroj knihovny ji po `Ready` převezme bez nového otevření.
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
    private val softRelease by lazy { method("softRelease") }

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

    /** Uvolní kameru, kterou si nikdo nevzal (za 200 ms, převzetím se uvolnění zruší). */
    fun release(source: Any) {
        runCatching { softRelease?.invoke(source) }
    }
}
