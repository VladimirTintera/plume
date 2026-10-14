package eu.tintera.plume.core.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals

/** Co je ve třetím panelu vidět ([shownExtras]) — podle toho se pozná, že se panel zavřel. */
class ShownExtrasTest {

    private val pane = mapOf(
        "list" to PaneInfo(PaneRole.List, Unit),
        "section" to PaneInfo(PaneRole.Detail, Unit),
        "privacy" to PaneInfo(PaneRole.Extra, Unit),
        "setting" to PaneInfo(PaneRole.Extra, Unit),
        // "media" je přes celou plochu, roli panelu nemá.
    )

    private fun List<String>.shown() = shownExtras { pane[it] }

    @Test
    fun podstrankyNavrchu() {
        assertEquals(listOf("privacy", "setting"), listOf("list", "section", "privacy", "setting").shown())
    }

    @Test
    fun bezPodstrankyNicNeni() {
        assertEquals(emptyList(), listOf("list", "section").shown())
    }

    @Test
    fun prohlizecNadPodstrankouJiNeschova() {
        assertEquals(listOf("privacy"), listOf("list", "section", "privacy", "media").shown())
    }
}
