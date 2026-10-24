package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kdy se dokončení staženého souboru ověřuje dotazem (TDLib po sloučení souborů update nepošle). */
class LooksFinishedTest {

    @Test
    fun celyStazenyAPoradStahujici() {
        // Přesně tenhle poslední update přišel po sloučení skladby s dřívějšími kopiemi.
        assertTrue(looksFinished(active = true, completed = false, downloadedSize = 73_531, total = 73_531))
    }

    @Test
    fun rozestavenyNe() {
        assertFalse(looksFinished(active = true, completed = false, downloadedSize = 1_000, total = 73_531))
    }

    @Test
    fun hotovyNe() {
        assertFalse(looksFinished(active = false, completed = true, downloadedSize = 73_531, total = 73_531))
    }

    @Test
    fun neznamaVelikostNe() {
        assertFalse(looksFinished(active = true, completed = false, downloadedSize = 0, total = 0))
    }

    @Test
    fun nestahujiciNe() {
        // Nečinný soubor nikdo nestahuje — na jeho dokončení se nečeká.
        assertFalse(looksFinished(active = false, completed = false, downloadedSize = 73_531, total = 73_531))
    }
}
