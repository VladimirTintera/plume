package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Otisk klíče se porovnává s oficiálními aplikacemi, takže pořadí bitů musí sedět přesně
 * s dokumentací TDLib — jinak by se „neshodoval" i správný klíč.
 */
class SecretChatKeyTest {

    @Test
    fun bodyJsouDvojiceBituOdNejnizsich() {
        // 0b11_10_01_00: první bod nejnižší dva bity (0), pak 1, 2, 3.
        val key = SecretChatKey(listOf(0b11_10_01_00) + List(35) { 0 })
        assertEquals(listOf(0, 1, 2, 3, 0), key.pixels.take(5))
        assertEquals(12, key.side)
        assertEquals(144, key.pixels.size)
    }

    @Test
    fun hexJakoOficialniKlient() {
        val key = SecretChatKey(List(36) { it })
        assertEquals(
            listOf(
                "00 01 02 03  04 05 06 07",
                "08 09 0A 0B  0C 0D 0E 0F",
                "10 11 12 13  14 15 16 17",
                "18 19 1A 1B  1C 1D 1E 1F",
            ),
            key.hexLines,
        )
    }

    @Test
    fun prazdnyHashNicNeukaze() {
        assertNull(SecretChatKey.of(ByteArray(0)))
        assertEquals(8, SecretChatKey.of(ByteArray(16))?.side)
    }
}
