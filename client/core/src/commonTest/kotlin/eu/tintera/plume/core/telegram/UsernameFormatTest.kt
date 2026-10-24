package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsernameFormatTest {

    @Test
    fun platneJmenoProjde() {
        assertNull(usernameFormatError("plume_app"))
        assertNull(usernameFormatError("Abc12"))
    }

    /** Prázdné jméno = odebrat ho, to je platná volba. */
    @Test
    fun prazdneJeOdebrani() {
        assertNull(usernameFormatError(""))
    }

    @Test
    fun pravidlaTelegramu() {
        assertEquals(UsernameFormatError.Characters, usernameFormatError("jméno"))
        assertEquals(UsernameFormatError.Characters, usernameFormatError("a.bcde"))
        assertEquals(UsernameFormatError.Start, usernameFormatError("1abcde"))
        assertEquals(UsernameFormatError.Start, usernameFormatError("_abcde"))
        assertEquals(UsernameFormatError.TooShort, usernameFormatError("abcd"))
        assertEquals(UsernameFormatError.TooLong, usernameFormatError("a".repeat(33)))
        assertEquals(UsernameFormatError.End, usernameFormatError("abcde_"))
    }
}
