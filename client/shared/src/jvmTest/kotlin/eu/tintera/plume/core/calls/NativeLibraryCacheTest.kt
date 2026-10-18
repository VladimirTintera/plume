package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Vybalení `libntgcalls` do dat aplikace se skutečnou knihovnou z resources. Na systému, pro
 * který Gradle knihovnu nestáhl (bez sítě, Intel Mac), test nemá co ověřit a skončí.
 */
class NativeLibraryCacheTest {

    @Test
    fun knihovnaSeVybaliJednouANacteSe() {
        val data = Files.createTempDirectory("plume-natives").toFile()
        try {
            val first = cachedNativeLibrary(NtgNative.LIBRARY, data, Logger) ?: return
            assertTrue(first.isFile && first.startsWith(File(data, "natives")))
            val modified = first.lastModified()

            // Druhé spuštění: tentýž soubor, nic se nekopíruje znovu.
            Thread.sleep(1_100)
            val second = assertNotNull(cachedNativeLibrary(NtgNative.LIBRARY, data, Logger))
            assertEquals(first, second)
            assertEquals(modified, second.lastModified())

            // Vybalená knihovna se dá načíst a odpoví.
            val version = NtgNative.load(second).ntg_get_version()
            assertTrue(version.isNotBlank(), "verze: $version")

            // Stará verze z dřívějška se při vybalení smaže.
            val stale = File(data, "natives/stara/${second.name}").apply { parentFile.mkdirs(); writeText("x") }
            cachedNativeLibrary(NtgNative.LIBRARY, data, Logger)
            assertTrue(!stale.exists())
        } finally {
            data.deleteRecursively()
        }
    }
}
