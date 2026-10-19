package eu.tintera.plume.core.calls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepeatFilterTest {

    private val filter = RepeatFilter(windowMs = 60_000)

    private fun accept(message: String, atMs: Long, tag: String = "connection.cc") =
        filter.accept(tag, message, "L $message", atMs)

    @Test
    fun sameKindPassesOncePerWindowWithCount() {
        val ping = "(line 1649): Conn[f6ffcd20:transport:Net[rmnet1:10.62.219.x/32:Cellular:id=3]]: Sent STUN BINDING request, id=4a39723077"
        assertEquals("L $ping", accept(ping, 0))
        assertNull(accept(ping.replace("f6ffcd20", "f7027fd0").replace("id=3", "id=4"), 500))
        assertNull(accept(ping, 30_000))
        assertEquals("L $ping  (+2 similar)", accept(ping, 60_000))
        assertNull(accept(ping, 61_000))
    }

    @Test
    fun newKindPassesImmediately() {
        accept("(line 1649): Conn[a]: Sent STUN BINDING request, id=1", 0)
        val pruned = "(line 879): Conn[a]: Connection pruned"
        assertEquals("L $pruned", accept(pruned, 100))
    }

    @Test
    fun sameTextUnderOtherTagIsOtherKind() {
        accept("Removed port: Port[a] 5 remaining", 0, tag = "p2p_transport_channel.cc")
        assertEquals("L Removed port: Port[a] 5 remaining", accept("Removed port: Port[a] 5 remaining", 10, tag = "other.cc"))
    }

    @Test
    fun repeatWithoutSuppressionHasNoCount() {
        assertEquals("L x 1", accept("x 1", 0))
        assertEquals("L x 2", accept("x 2", 70_000))
    }

    @Test
    fun normalizeStripsNumbersAddressesAndBrackets() {
        assertEquals(
            ": Port{}: REFLECTOR sending ping to N.N.N.N:N",
            RepeatFilter.normalize("(line 217): Port[d6e15660::0:0:relay:Net[rmnet1:10.62.219.x/32:Cellular:id=3]]: REFLECTOR sending ping to 91.108.9.86:598"),
        )
    }

    @Test
    fun oldestKindIsForgottenOverLimit() {
        val small = RepeatFilter(windowMs = 60_000, maxKinds = 2)
        small.accept("t", "a", "a", 0)
        small.accept("t", "b", "b", 0)
        small.accept("t", "c", "c", 0)
        assertEquals("a", small.accept("t", "a", "a", 1))
    }
}
