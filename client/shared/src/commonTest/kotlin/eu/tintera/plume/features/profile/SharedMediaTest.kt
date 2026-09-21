package eu.tintera.plume.features.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedMediaTest {

    @Test
    fun emailSeUkazeBezSchematu() {
        assertEquals("kdo@example.com", displayUrl("mailto:kdo@example.com"))
    }

    @Test
    fun ostatniSchemataZustavaji() {
        // `https` od `tg` odlišit potřeba je — zahazuje se jen to, co nic neříká.
        assertEquals("https://example.com", displayUrl("https://example.com"))
        assertEquals("tg://resolve?domain=x", displayUrl("tg://resolve?domain=x"))
    }
}
