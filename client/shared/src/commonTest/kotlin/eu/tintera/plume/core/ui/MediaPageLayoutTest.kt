package eu.tintera.plume.core.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vepsání snímku do místa mezi lištami. */
class MediaPageLayoutTest {

    @Test
    fun vysokaFotkaNaraziNaVysku() {
        assertEquals(IntSize(300, 600), fitInto(maxWidth = 800, maxHeight = 600, aspectRatio = 0.5f))
    }

    @Test
    fun sirokaFotkaNaraziNaSirku() {
        assertEquals(IntSize(800, 400), fitInto(maxWidth = 800, maxHeight = 1000, aspectRatio = 2f))
    }

    @Test
    fun neznamyPomerJeCtverec() {
        assertEquals(IntSize(500, 500), fitInto(maxWidth = 500, maxHeight = 900, aspectRatio = Float.NaN))
        assertEquals(1f, aspectRatioOf(0, 0))
    }

    @Test
    fun zadneMistoZadnaKarta() {
        assertEquals(IntSize.Zero, fitInto(maxWidth = 500, maxHeight = 0, aspectRatio = 1f))
    }

    @Test
    fun vGaleriiJeKartaMeziListamiANadPopiskem() {
        // Stránka 400 × 1000, lišty 100 + 100, popisek 50 s mezerou 10 → na kartu zbývá 740.
        val frame = pageCardFrame(IntSize(400, 1000), 100, 100, captionHeight = 50, captionGap = 10, aspectRatio = 1f)
        // Čtverec 400 × 400 je na středu místa mezi lištami i s popiskem pod sebou.
        assertEquals(IntRect(IntOffset(0, 100 + (800 - 400 - 60) / 2), IntSize(400, 400)), frame)
    }

    @Test
    fun naCelouObrazovkuJdeFotkaPresCelouStranku() {
        // Bez lišt a popisku se vysoká fotka vepíše do celé výšky stránky.
        val frame = pageCardFrame(IntSize(400, 1000), 0, 0, captionHeight = 0, captionGap = 0, aspectRatio = 0.25f)
        assertEquals(IntRect(IntOffset(75, 0), IntSize(250, 1000)), frame)
    }

    @Test
    fun naSkladacceNaStoleJeVideoNadPantem() {
        // Rozložený Fold na šířku 2000 × 1800, pant uprostřed: video 16 : 9 se vepíše do horní
        // půlky (výška 900 → 1600 × 900) a leží na jejím středu, nic pod pantem.
        val frame = tabletopCardFrame(IntSize(2000, 1800), split = 900, aspectRatio = 16f / 9f)
        assertEquals(IntRect(IntOffset(200, 0), IntSize(1600, 900)), frame)
    }

    @Test
    fun naSkladacceNaStoleSeSirokeVideoCentrujeVHorniPulce() {
        // Úzká stránka 1000 × 1800: video na šířku narazí na šířku a svisle sedí na středu půlky.
        val frame = tabletopCardFrame(IntSize(1000, 1800), split = 900, aspectRatio = 2f)
        assertEquals(IntRect(IntOffset(0, (900 - 500) / 2), IntSize(1000, 500)), frame)
    }

    @Test
    fun pantMimoStrankuNicNemeni() {
        val whole = pageCardFrame(IntSize(1000, 1800), 0, 0, captionHeight = 0, captionGap = 0, aspectRatio = 1f)
        assertEquals(whole, tabletopCardFrame(IntSize(1000, 1800), split = 0, aspectRatio = 1f))
        assertEquals(whole, tabletopCardFrame(IntSize(1000, 1800), split = 1800, aspectRatio = 1f))
    }

    @Test
    fun malaStrankaNedaZapornouVelikost() {
        val frame = pageCardFrame(IntSize(400, 100), 80, 80, captionHeight = 50, captionGap = 10, aspectRatio = 1f)
        assertEquals(IntSize.Zero, frame.size)
    }
}
