package eu.tintera.plume.core.location

import eu.tintera.plume.core.telegram.ActiveLiveLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveShareSpanTest {

    private fun share(until: Long, period: Int) = ActiveLiveLocation(chatId = 1, messageId = 1, livePeriod = period, until = until)

    @Test
    fun beztSdileniNicNeni() {
        assertNull(liveShareSpan(emptyList()))
    }

    @Test
    fun sdileniDokudNevypnuNemaKonec() {
        assertNull(liveShareSpan(listOf(share(until = 1_000, period = 900), share(until = Long.MAX_VALUE, period = Int.MAX_VALUE))))
    }

    @Test
    fun berePosledniKonec() {
        val span = liveShareSpan(listOf(share(until = 1_000, period = 900), share(until = 4_000, period = 3_600)))
        assertEquals(LiveShareSpan(startSeconds = 400, endSeconds = 4_000), span)
    }

    @Test
    fun prubehVMezich() {
        val span = LiveShareSpan(startSeconds = 0, endSeconds = 900)
        assertEquals(0, span.progress(nowSeconds = -10, max = 1000))
        assertEquals(500, span.progress(nowSeconds = 450, max = 1000))
        assertEquals(1000, span.progress(nowSeconds = 5_000, max = 1000))
    }
}
