package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.GestureHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Která nápověda gesta se ukáže. Každá jednou, jen nad obrazovkou, na které nic jiného nečeká,
 * a jen tam, kde gesto jde použít.
 */
class GestureHintTest {

    private val idle = GestureHintContext(idle = true, sendOptions = false, recording = true, hasMessages = true)

    @Test
    fun prazdnePoleVysvetliNahravani() {
        assertEquals(GestureHint.HoldToRecord, nextGestureHint(emptySet(), idle))
    }

    @Test
    fun priPsaniMajiPrednostVolbyOdeslani() {
        val typing = idle.copy(sendOptions = true, recording = false)
        assertEquals(GestureHint.SendOptions, nextGestureHint(emptySet(), typing))
    }

    @Test
    fun videneSePreskoci() {
        assertEquals(GestureHint.RevealTimes, nextGestureHint(setOf(GestureHint.HoldToRecord), idle))
        assertNull(nextGestureHint(GestureHint.entries.toSet(), idle))
    }

    @Test
    fun casyJenSeZpravami() {
        val empty = idle.copy(recording = false, hasMessages = false)
        assertNull(nextGestureHint(emptySet(), empty))
    }

    @Test
    fun nadRozdelanouVeciNic() {
        val busy = idle.copy(idle = false)
        assertNull(nextGestureHint(emptySet(), busy))
        // Ukázaná nápověda se při nabídce nebo sheetu schová, i když ji uživatel ještě neviděl celou.
        assertFalse(busy.allows(GestureHint.HoldToRecord))
    }

    @Test
    fun volbyOdeslaniJenKdyzJePodrzeniNabidne() {
        // Bez voleb (Uložené zprávy bez plánování…) by nápověda slibovala nabídku, která nepřijde.
        val typingNoMenu = idle.copy(sendOptions = false, recording = false)
        assertEquals(GestureHint.RevealTimes, nextGestureHint(emptySet(), typingNoMenu))
    }
}
