package eu.tintera.plume.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccentColorTest {

    @Test
    fun cernaNaBileMaNejvyssiKontrast() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
    }

    @Test
    fun stejneBarvyMajiKontrastJedna() {
        assertEquals(1f, contrastRatio(Color(0xFF96F5BF), Color(0xFF96F5BF)), 0.001f)
    }

    /**
     * Přesně ten případ ze zařízení: zvýrazněná bublina v tmavém tématu s paletou, kde se
     * `tertiary` a `tertiaryContainer` potkaly na téže světlosti. Barvy odečtené ze snímku.
     */
    @Test
    fun akcentSplyvajiciSPlochouSpadneNaBarvuPopredi() {
        val plocha = Color(0xFF96F5BF)
        val tertiary = Color(0xFFA8F7C6)
        val onPlocha = Color(0xFF004B1B)

        assertTrue(contrastRatio(tertiary, plocha) < 1.2f, "barvy se mají potkat na téže světlosti")
        assertEquals(onPlocha, accentOn(plocha, accent = tertiary, fallback = onPlocha))
    }

    /**
     * Běžná paleta (materialkolor Expressive, tmavé téma) se opravou nemění — akcent projde.
     */
    @Test
    fun citelnyAkcentZustane() {
        val tertiaryContainer = Color(0xFF26486F)
        val tertiary = Color(0xFFA8C9F6)
        val onTertiaryContainer = Color(0xFFD2E4FF)

        assertTrue(contrastRatio(tertiary, tertiaryContainer) > 4.5f)
        assertEquals(
            tertiary,
            accentOn(tertiaryContainer, accent = tertiary, fallback = onTertiaryContainer),
        )
    }

    /** Poloprůhledný akcent se posuzuje až složený na ploše, ne podle své vlastní barvy. */
    @Test
    fun pruhlednyAkcentSePosuzujeSlozeny() {
        val plocha = Color(0xFF101010)
        val skoroPruhlednaBila = Color.White.copy(alpha = 0.06f)

        assertTrue(contrastRatio(skoroPruhlednaBila, plocha) < 1.5f)
        assertEquals(
            Color.Yellow,
            accentOn(plocha, accent = skoroPruhlednaBila, fallback = Color.Yellow),
        )
    }
}
