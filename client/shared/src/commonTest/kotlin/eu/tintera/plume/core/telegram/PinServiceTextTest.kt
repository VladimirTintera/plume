package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_message_pinned
import eu.tintera.plume.resources.service_pinned_named
import eu.tintera.plume.resources.service_pinned_named_message
import eu.tintera.plume.resources.service_pinned_quote
import eu.tintera.plume.resources.service_pinned_you
import eu.tintera.plume.resources.service_pinned_you_message
import kotlin.test.Test
import kotlin.test.assertEquals

/** Připnutí: kdo a co, a když něco z toho chybí, věta bez toho — nikdy prázdné místo. */
class PinServiceTextTest {

    @Test
    fun jmenoATextPripnuteZpravy() {
        assertEquals(
            UiText(Res.string.service_pinned_named, "Jana", "Sraz v 8"),
            pinServiceText(ServicePerson("Jana"), "Sraz v 8"),
        )
        assertEquals(
            UiText(Res.string.service_pinned_you, "Sraz v 8"),
            pinServiceText(ServicePerson(name = "", isMe = true), "Sraz v 8"),
        )
    }

    @Test
    fun bezTextuJenZprava() {
        assertEquals(UiText(Res.string.service_pinned_named_message, "Jana"), pinServiceText(ServicePerson("Jana"), null))
        assertEquals(UiText(Res.string.service_pinned_you_message), pinServiceText(ServicePerson("", isMe = true), "  "))
    }

    @Test
    fun bezJmenaJenText() {
        assertEquals(UiText(Res.string.service_pinned_quote, "Sraz v 8"), pinServiceText(null, "Sraz v 8"))
        assertEquals(UiText(Res.string.service_message_pinned), pinServiceText(null, null))
    }

    @Test
    fun dlouhyTextSeZkrati() {
        val long = "a".repeat(100)
        assertEquals(
            UiText(Res.string.service_pinned_quote, "a".repeat(40) + "…"),
            pinServiceText(null, long),
        )
    }
}
