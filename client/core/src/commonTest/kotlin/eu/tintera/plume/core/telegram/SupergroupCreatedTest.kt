package eu.tintera.plume.core.telegram

import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_channel_created
import eu.tintera.plume.resources.service_group_created
import kotlin.test.Test
import kotlin.test.assertEquals

/** `MessageSupergroupChatCreate` chodí pro skupinu i kanál; rozliší je až zpráva. */
class SupergroupCreatedTest {

    @Test
    fun skupinaNeniKanal() {
        assertEquals(Res.string.service_group_created, supergroupCreatedText(isChannelPost = false))
    }

    @Test
    fun prispevekKanaluJeKanal() {
        assertEquals(Res.string.service_channel_created, supergroupCreatedText(isChannelPost = true))
    }
}
