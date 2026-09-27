package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Kdy si tah nad svislým seznamem vezme gesto do strany a kdy ho nechá scrollu. */
class HorizontalSwipeTest {

    private val slop = 16f

    private fun decide(dx: Float, dy: Float, direction: SwipeDirection) =
        horizontalSwipeDecision(dx, dy, slop, direction)

    @Test
    fun kratkyPohybSeJesteNerozhoduje() {
        assertEquals(SwipeDecision.Undecided, decide(dx = -12f, dy = 0f, SwipeDirection.Left))
        assertEquals(SwipeDecision.Undecided, decide(dx = 12f, dy = 6f, SwipeDirection.Right))
        assertEquals(SwipeDecision.Undecided, decide(dx = 10f, dy = 5f, SwipeDirection.Either))
    }

    @Test
    fun cistyTahSiVezmeGesto() {
        assertEquals(SwipeDecision.Swipe, decide(dx = -20f, dy = 3f, SwipeDirection.Left))
        assertEquals(SwipeDecision.Swipe, decide(dx = 20f, dy = -3f, SwipeDirection.Right))
        assertEquals(SwipeDecision.Swipe, decide(dx = -20f, dy = 3f, SwipeDirection.Either))
        assertEquals(SwipeDecision.Swipe, decide(dx = 20f, dy = 3f, SwipeDirection.Either))
    }

    /** Palec jde na začátku scrollu obloukem do strany — a právě tenhle tah patří seznamu. */
    @Test
    fun sikmyTahPatriSeznamu() {
        assertEquals(SwipeDecision.Ignore, decide(dx = -14f, dy = -12f, SwipeDirection.Left))
        assertEquals(SwipeDecision.Ignore, decide(dx = 16f, dy = 10f, SwipeDirection.Right))
        assertEquals(SwipeDecision.Ignore, decide(dx = 14f, dy = -12f, SwipeDirection.Right))
        assertEquals(SwipeDecision.Ignore, decide(dx = 5f, dy = 40f, SwipeDirection.Either))
    }

    /** Doleva odkrývá časy, doprava odpovídá — jedno gesto nesmí vzít tah tomu druhému. */
    @Test
    fun opacnySmerSeNebere() {
        assertEquals(SwipeDecision.Ignore, decide(dx = 20f, dy = 0f, SwipeDirection.Left))
        assertEquals(SwipeDecision.Ignore, decide(dx = -20f, dy = 0f, SwipeDirection.Right))
    }
}
