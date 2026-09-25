package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Zkrácené počty zhlédnutí u příspěvku. */
class CompactCountTest {

    @Test
    fun podTisicCeleCislo() {
        assertEquals(CompactCount(0, null, CountScale.Units), compactCount(0))
        assertEquals(CompactCount(999, null, CountScale.Units), compactCount(999))
    }

    @Test
    fun doDesetiJednotekSDesetinou() {
        assertEquals(CompactCount(1, 2, CountScale.Thousands), compactCount(1_234))
        assertEquals(CompactCount(9, 9, CountScale.Thousands), compactCount(9_999))
        assertEquals(CompactCount(3, 4, CountScale.Millions), compactCount(3_456_789))
    }

    @Test
    fun nulovaDesetinaSeNepise() {
        assertEquals(CompactCount(1, null, CountScale.Thousands), compactCount(1_050))
        assertEquals(CompactCount(2, null, CountScale.Millions), compactCount(2_000_000))
    }

    @Test
    fun odDesetiJednotekBezDesetiny() {
        assertEquals(CompactCount(12, null, CountScale.Thousands), compactCount(12_987))
        assertEquals(CompactCount(10, null, CountScale.Millions), compactCount(10_704_668))
    }

    @Test
    fun zaokrouhlujeSeDolu() {
        // Příspěvek nemá ukazovat víc zhlédnutí, než kolik jich má.
        assertEquals(CompactCount(999, null, CountScale.Thousands), compactCount(999_999))
        assertEquals(CompactCount(1, 9, CountScale.Thousands), compactCount(1_999))
    }
}
