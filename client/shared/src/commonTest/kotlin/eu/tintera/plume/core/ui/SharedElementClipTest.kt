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
