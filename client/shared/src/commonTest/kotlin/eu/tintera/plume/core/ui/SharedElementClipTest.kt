package eu.tintera.plume.core.ui

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ořez sdíleného prvku v překryvu.
 *
 * Fotka v konverzaci zpola mizí pod hlavičkou, takže obrazovka je vysoká 2000, hlavička končí
 * na 300 a bublina sahá od 200 do 700 — tedy 100 pod hlavičku.
 */
class SharedElementClipTest {
    private val content = Rect(left = 0f, top = 300f, right = 1000f, bottom = 2000f)
    private val fullscreen = Rect(left = 0f, top = 0f, right = 1000f, bottom = 2000f)
    private val bubble = Rect(left = 20f, top = 200f, right = 980f, bottom = 700f)

    @Test
    fun naZacatkuLetuNeorezavaNic() {
        // Celoobrazovková fotka se v prvním snímku nesmí useknout o hlavičku obrazovky pod ní.
        val clip = sharedElementClipRect(content, fullscreen, settled = 0f)
        assertTrue(clip.top <= fullscreen.top && clip.bottom >= fullscreen.bottom, "$clip usekne $fullscreen")
        assertTrue(clip.left <= fullscreen.left && clip.right >= fullscreen.right, "$clip usekne $fullscreen")
    }

    @Test
    fun naKonciLetuOrezavaPrestePlochaSeznamu() {
        assertEquals(content, sharedElementClipRect(content, bubble, settled = 1f))
    }

    @Test
    fun uprostredLetuPodjizdiFotkaPodHlavicku() {
        // Ořez je mezi oběma krajnostmi, takže fotka pod hlavičku zajíždí, neskáče pod ni.
        val clip = sharedElementClipRect(content, Rect(0f, 100f, 1000f, 1400f), settled = 0.5f)
        assertEquals(200f, clip.top)
        assertEquals(0f, clip.left)
        assertEquals(1000f, clip.right)
    }

    @Test
    fun prvekMimoPlochuOrezJenStahuje() {
        // Prvek nikdy neroste za plochu: ořez se z obalu prvku stahuje na plochu, ne naopak.
        val wide = Rect(left = -200f, top = 0f, right = 1200f, bottom = 2500f)
        val open = sharedElementClipRect(content, wide, settled = 0f)
        assertEquals(wide, open)
        assertEquals(content, sharedElementClipRect(content, wide, settled = 1f))
    }

    @Test
    fun prubehMimoRozsahSeOreze() {
        assertEquals(sharedElementClipRect(content, bubble, 0f), sharedElementClipRect(content, bubble, -0.4f))
        assertEquals(content, sharedElementClipRect(content, bubble, 1.4f))
    }
}

/** Viditelná část plochy: seznam bez pásů, které přes něj obrazovka kreslí (pruh nahoře, pole dole). */
class VisibleAreaTest {
    private val list = Rect(left = 0f, top = 300f, right = 1000f, bottom = 2000f)

    @Test
    fun odecteVstupniPoleIPruhNahore() {
        assertEquals(Rect(0f, 420f, 1000f, 1750f), visibleArea(list, obscuredTop = 120f, obscuredBottom = 250f))
    }

    @Test
    fun fotkaPodPolemSeNaKonciLetuOrizneOPole() {
        // Fotka leží od 1600 do 2100 — zpola pod polem, které začíná na 1750.
        val clip = sharedElementClipRect(visibleArea(list, 0f, 250f), Rect(20f, 1600f, 980f, 2100f), settled = 1f)
        assertEquals(1750f, clip.bottom)
    }

    @Test
    fun prekryvajiciSePasyNeprevratiObdelnik() {
        val area = visibleArea(list, obscuredTop = 1500f, obscuredBottom = 1500f)
        assertTrue(area.bottom >= area.top, "$area")
    }

    @Test
    fun pasPodPolemSeOteviraJenPrimoPodSeznamem() {
        val clip = SharedElementClip()
        val visible = visibleArea(list, 0f, 250f)
        // Bez pásu a bez změřeného tvaru v něm platí ořez rovnou čarou.
        assertEquals(null, clip.openBand(visible))
        clip.band = Rect(0f, 1750f, 1000f, 2000f)
        assertEquals(null, clip.openBand(visible))
        clip.occluders += Occluder().apply { bounds = Rect(24f, 1770f, 880f, 1960f) }
        assertEquals(clip.band, clip.openBand(visible))
        // Nad polem je panel (návrhy odpovědí): přes něj se letět nesmí.
        clip.band = Rect(0f, 1850f, 1000f, 2000f)
        assertEquals(null, clip.openBand(visible))
    }
}
