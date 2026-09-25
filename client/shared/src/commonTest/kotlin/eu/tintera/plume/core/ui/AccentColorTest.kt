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

    /**
     * Citace uvnitř zvýrazněné PŘÍCHOZÍ zprávy: bublina sama přejde do `tertiaryContainer`, takže
     * plocha citace je s ní totožná a musí se posunout.
     */
    @Test
    fun plochaTotoznaSBublinouSePosune() {
        val bublina = Color(0xFF26486F)
        val posunuta = surfaceApart(
            background = bublina,
            container = Color(0xFF26486F),
            toward = Color(0xFFD2E4FF),
        )

        assertTrue(
            contrastRatio(posunuta, bublina) > 1.2f,
            "plochy se musí poznat, je to ${contrastRatio(posunuta, bublina)}",
        )
        assertTrue(
            contrastRatio(Color(0xFFD2E4FF), posunuta) > 4.5f,
            "text na posunuté ploše musí zůstat čitelný",
        )
    }

    /**
     * Paleta ze zařízení (zvýšený kontrast, tmavý režim): `tertiary` i `tertiaryContainer` jsou
     * světlé, takže se karta slila v jednu plochu i u zvýrazněné ODCHOZÍ zprávy — bublina
     * `tertiary`, citace `tertiaryContainer`.
     */
    @Test
    fun plochaSplyvajiciSJinouRoliSePosuneTaky() {
        val bublinaTertiary = Color(0xFF96F5BF)
        val posunuta = surfaceApart(
            background = bublinaTertiary,
            container = Color(0xFF9AF6C2),
            toward = Color(0xFF004B1B),
        )

        assertTrue(contrastRatio(posunuta, bublinaTertiary) > 1.2f)
    }

    /** Plocha, která se od bubliny pozná sama, zůstává — běžná paleta se nemění. */
    @Test
    fun odlisenaPlochaZustane() {
        val bublina = Color(0xFF262A31)
        val container = Color(0xFF26486F)

        assertEquals(container, surfaceApart(bublina, container, toward = Color(0xFFD2E4FF)))
    }

    /**
     * Přesně ten případ ze zařízení: ikony u vstupního pole v Material You z tapety. `primary`
     * na kartě projde prahem čitelnosti (5,3 : 1), a přesto je slabší než text vedle ní —
     * vybrat se má `onPrimaryContainer`.
     */
    @Test
    fun proDrobnouKresbuVyhrajeSilnejsiAkcent() {
        val karta = Color(0xFFE5E8F1)
        val primary = Color(0xFF576417)
        val onPrimaryContainer = Color(0xFF181E00)

        assertTrue(contrastRatio(primary, karta) >= 4.5f, "primary sám o sobě čitelný je")
        assertEquals(onPrimaryContainer, strongestAccentOn(karta, primary, onPrimaryContainer))
    }

    /**
     * Tmavý režim se zvýšeným kontrastem: tam je `onPrimaryContainer` TMAVÝ a na tmavé kartě by
     * byl horší než `primary`. Proto se role nevybírá pevně, ale měřením.
     */
    @Test
    fun naTmavePlosVyhrajeSvetlejsiRole() {
        val karta = Color(0xFF2A2E35)
        val primary = Color(0xFFBFD47A)
        val onPrimaryContainer = Color(0xFF181E00)

        assertEquals(primary, strongestAccentOn(karta, primary, onPrimaryContainer))
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

    /**
     * Úchyty výběru u odchozí zprávy v tmavém režimu (tóny palety Expressive): bublina `primary`
     * je světlá, plocha tmavá. `primary` splyne s bublinou, `onSurface` je jí moc blízko —
     * vyhraje `inversePrimary`, střední tón, který se odliší od obou.
     */
    @Test
    fun uchytyNaTmaveOdchoziBublineVezmouInversePrimary() {
        val primary = Color(0xFFAAC7FF)
        val plocha = Color(0xFF111318)
        val inversePrimary = Color(0xFF415F91)
        val onSurface = Color(0xFFE2E2E9)

        assertEquals(
            inversePrimary,
            distinctOnBoth(primary, listOf(inversePrimary, onSurface), listOf(primary, plocha)),
        )
    }

    /** Totéž ve světlém režimu: bublina střední, plocha světlá — vyhraje tmavý `onSurface`. */
    @Test
    fun uchytyNaSvetleOdchoziBublineVezmouOnSurface() {
        val primary = Color(0xFF415F91)
        val plocha = Color(0xFFF9F9FF)
        val inversePrimary = Color(0xFFAAC7FF)
        val onSurface = Color(0xFF191C20)

        assertEquals(
            onSurface,
            distinctOnBoth(primary, listOf(inversePrimary, onSurface), listOf(primary, plocha)),
        )
    }

    /** U příchozí bubliny `primary` vidět je, a tak zůstane, i když by jiná role měřila víc. */
    @Test
    fun uchytyNaPrichoziBublineZustanouPrimary() {
        val primary = Color(0xFF415F91)
        val bublina = Color(0xFFE7E8EE)
        val plocha = Color(0xFFF9F9FF)
        val onSurface = Color(0xFF191C20)

        assertEquals(primary, distinctOnBoth(primary, listOf(onSurface), listOf(bublina, plocha)))
    }
}
