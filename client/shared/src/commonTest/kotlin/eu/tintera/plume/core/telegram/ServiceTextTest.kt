package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageChatSetMessageAutoDeleteTime
import dev.g000sha256.tdl.dto.MessageCustomServiceAction
import dev.g000sha256.tdl.dto.MessageSupergroupChatCreate
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_delete_summary
import eu.tintera.plume.resources.duration_days
import eu.tintera.plume.resources.service_auto_delete_off
import eu.tintera.plume.resources.service_group_created
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Servisní věta je ODKAZ do prostředků, ne hotový řetězec: převedená zpráva přežije změnu
 * jazyka a přeložit se musí až tam, kde se kreslí.
 */
class ServiceTextTest {

    @Test
    fun nazevJeArgumentVety() {
        assertEquals(
            UiText(Res.string.service_group_created, "Plume"),
            MessageSupergroupChatCreate(title = "Plume").serviceText(isChannelPost = false),
        )
    }

    @Test
    fun delkaMazaniJeVnorenyPlural() {
        assertEquals(
            UiText(Res.string.auto_delete_summary, UiText(Res.plurals.duration_days, 1, 1)),
            MessageChatSetMessageAutoDeleteTime(messageAutoDeleteTime = 86_400, fromUserId = 0L)
                .serviceText(isChannelPost = false),
        )
        assertEquals(
            UiText(Res.string.service_auto_delete_off),
            MessageChatSetMessageAutoDeleteTime(messageAutoDeleteTime = 0, fromUserId = 0L)
                .serviceText(isChannelPost = false),
        )
    }

    @Test
    fun textOdServeruSeNeprekladá() {
        assertEquals(UiText.Raw("Hotovo"), MessageCustomServiceAction(text = "Hotovo").serviceText(isChannelPost = false))
    }
}
