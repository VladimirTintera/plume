package eu.tintera.plume.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/** Příkaz v `Run`: cesta v uvozovkách (Program Files, mezery v profilu) a argument pro schování. */
class LaunchAtLoginTest {

    @Test
    fun commandQuotesPathAndAddsArgument() {
        assertEquals(
            "\"C:\\Users\\Jan Novák\\AppData\\Local\\Plume\\Plume.exe\" --autostart",
            autostartCommand("C:\\Users\\Jan Novák\\AppData\\Local\\Plume\\Plume.exe"),
        )
    }
}
