package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveContentTest {

    @Test
    fun citliveSeSkryva() {
        assertTrue(hidesSensitive(hasSensitiveContent = true, ignoresRestrictions = false))
    }

    /** TDLib příznak hlásí i tomu, kdo si citlivý obsah povolil — rozhodnout musí aplikace. */
    @Test
    fun povolenyCitlivyObsahSeNeskryva() {
        assertFalse(hidesSensitive(hasSensitiveContent = true, ignoresRestrictions = true))
    }

    @Test
    fun beznyObsahSeNeskryva() {
        assertFalse(hidesSensitive(hasSensitiveContent = false, ignoresRestrictions = false))
    }
}
