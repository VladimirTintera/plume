package eu.tintera.plume.core.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * Coil musí soubor z [localImage] najít i pod absolutní cestou daného systému — na Windows
 * s písmenem disku (`C:\…`), které se z `"file://$path"` ztrácelo. Na Windows to ověřuje přesně
 * ten případ, jinde aspoň to, že se nic nerozbilo. Dekódování se netestuje, jen načtení souboru.
 */
class LocalImageTest {

    @Test
    fun coilReadsFileFromAbsolutePath() = runTest {
        val file = File.createTempFile("plume", ".jpg").apply {
            writeText("plume")
            deleteOnExit()
        }
        val context = PlatformContext.INSTANCE
        val loader = ImageLoader(context)
        val options = Options(context)

        val data = loader.components.map(localImage(file.absolutePath), options)
        val (fetcher, _) = checkNotNull(loader.components.newFetcher(data, options, loader))
        val result = fetcher.fetch() as SourceFetchResult

        assertEquals("plume", result.source.source().readUtf8())
    }
}
