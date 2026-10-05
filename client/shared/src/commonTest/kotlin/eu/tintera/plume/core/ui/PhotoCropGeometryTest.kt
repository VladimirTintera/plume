package eu.tintera.plume.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoCropGeometryTest {

    /** Fotka na šířku 2000 × 1000, kruh 500 px: při přiblížení 1 je na obrazovce 1000 × 500. */
    private val landscape = PhotoCropGeometry(Size(2000f, 1000f), circle = 500f)

    @Test
    fun bezPosunuJeVyrezZeStredu() {
        assertEquals(centerCropRegion(2000f, 1000f), landscape.region(CropPlacement()))
        assertEquals(CropRegion(0.25f, 0f, 0.5f, 1f), landscape.region(CropPlacement()))
    }

    @Test
    fun posunDolevaNeniPovolenyPresOkraj() {
        // Fotka posunutá doprava o celou šířku by kruhu nechala prázdno vlevo.
        val placement = landscape.clamped(CropPlacement(offset = Offset(1000f, 0f)))
        assertEquals(Offset(250f, 0f), placement.offset)
        assertEquals(0f, landscape.region(placement).left)
    }

    @Test
    fun posunDolevaUkazePravyKraj() {
        val region = landscape.region(CropPlacement(offset = Offset(-250f, 0f)))
        assertEquals(0.5f, region.left)
        assertEquals(0.5f, region.width)
    }

    @Test
    fun kratsiStranuPosunoutNejde() {
        // Výška fotky je přesně průměr kruhu, svisle tedy není kam.
        val placement = landscape.clamped(CropPlacement(offset = Offset(0f, 80f)))
        assertEquals(0f, placement.offset.y)
    }

    @Test
    fun priblizeniZmensiVyrez() {
        val region = landscape.region(CropPlacement(scale = 2f))
        assertEquals(0.25f, region.width)
        assertEquals(0.5f, region.height)
        // Střed výřezu zůstává uprostřed fotky.
        assertEquals(0.5f, region.left + region.width / 2)
        assertEquals(0.5f, region.top + region.height / 2)
    }

    @Test
    fun priblizeniJeSevreneMezi1aMaximem() {
        assertEquals(1f, landscape.transformed(CropPlacement(), Offset.Zero, Offset.Zero, zoom = 0.2f).scale)
        assertEquals(MAX_CROP_SCALE, landscape.transformed(CropPlacement(), Offset.Zero, Offset.Zero, zoom = 50f).scale)
    }

    @Test
    fun priblizeniKolemPrstuNechaBodPodPrstyNaMiste() {
        val square = PhotoCropGeometry(Size(1000f, 1000f), circle = 500f)
        // Prsty 100 px vpravo od středu: bod fotky pod nimi musí po přiblížení zůstat tam.
        val centroid = Offset(100f, 0f)
        val placement = square.transformed(CropPlacement(), centroid, Offset.Zero, zoom = 2f)
        assertEquals(Offset(-100f, 0f), placement.offset)
    }

    @Test
    fun fotkaNaVyskuMaVyrezPresCelouSirku() {
        val portrait = PhotoCropGeometry(Size(1000f, 3000f), circle = 400f)
        assertEquals(CropRegion(0f, 1f / 3, 1f, 1f / 3), portrait.region(CropPlacement()))
    }

    @Test
    fun bezRozmeruJeVyrezCelaFotka() {
        assertEquals(CropRegion(0f, 0f, 1f, 1f), centerCropRegion(0f, 0f))
    }
}
