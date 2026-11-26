package eu.tintera.plume.app.shell

import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Co scéna Chatů z položek kreslí ([selectChatPanes]): seznam, detail a boční sloupec, který
 * patří k detailu pod sebou. Zakrytý profil jiného chatu se nekreslí, ať je v zásobníku cokoli.
 */
class ChatScenePanesTest {

    private val archive = "archive"
    private val settings = "settings"

    private val pane = mapOf(
        "chats" to PaneInfo(PaneRole.List, Unit),
        "search" to PaneInfo(PaneRole.List, Unit),
        "convA" to PaneInfo(PaneRole.Detail, Unit),
        "convB" to PaneInfo(PaneRole.Detail, Unit),
        "profileA" to PaneInfo(PaneRole.Extra, Unit),
        "editA" to PaneInfo(PaneRole.Extra, Unit),
        "archive" to PaneInfo(PaneRole.List, archive),
        "archConvY" to PaneInfo(PaneRole.Detail, archive),
        "archProfileY" to PaneInfo(PaneRole.Extra, archive),
        "settings" to PaneInfo(PaneRole.List, settings),
        "section" to PaneInfo(PaneRole.Detail, settings),
        // "media" je přes celou plochu a metadata panelu nemá.
    )

    private fun select(vararg stack: String) = selectChatPanes(stack.toList(), { pane[it] }, { it != settings })

    @Test
    fun jenSeznamMaPlaceholder() {
        val panes = select("chats")!!
        assertEquals("chats", panes.list)
        assertNull(panes.detail)
        assertNull(panes.side)
    }

    @Test
    fun profilNadKonverzaciJeBocniSloupec() {
        val panes = select("chats", "convA", "profileA")!!
        assertEquals("convA", panes.detail)
        assertEquals("profileA", panes.side)
    }

    @Test
    fun podstrankaSloupceNahradiProfil() {
        assertEquals("editA", select("chats", "convA", "profileA", "editA")!!.side)
    }

    @Test
    fun jinaKonverzaceNadProfilemSloupecZavre() {
        val panes = select("chats", "convA", "profileA", "convB")!!
        assertEquals("convB", panes.detail)
        assertNull(panes.side)
        // Zakrytý profil ve scéně zůstává (zpět, ukládání), jen se nekreslí.
        assertEquals(listOf("chats", "convA", "profileA", "convB"), panes.sceneEntries)
    }

    @Test
    fun hledaniNadKonverzaciNecháChatVedle() {
        val panes = select("chats", "convA", "search")!!
        assertEquals("search", panes.list)
        assertEquals("convA", panes.detail)
    }

    @Test
    fun archivJeVlastniScena() {
        val panes = select("chats", "convA", "archive", "archConvY", "archProfileY")!!
        assertEquals(archive, panes.sceneKey)
        assertEquals("archive", panes.list)
        assertEquals("archConvY", panes.detail)
        assertEquals("archProfileY", panes.side)
        assertEquals(listOf("archive", "archConvY", "archProfileY"), panes.sceneEntries)
    }

    @Test
    fun nastaveniNechavaKnihovne() {
        assertNull(select("settings", "section"))
    }

    @Test
    fun obrazovkaPresCelouPlochuNeniScena() {
        assertNull(select("chats", "convA", "media"))
    }

    @Test
    fun konverzaceNadProhlizecemMaSeznamZpodNej() {
        // Prohlížeč uprostřed se přeskočí: konverzace nad ním stojí vedle seznamu zpod něj,
        // knihovní třípanel se do Chatů nevrací.
        val panes = select("chats", "convA", "media", "convB")!!
        assertEquals("chats", panes.list)
        assertEquals("convB", panes.detail)
        assertNull(panes.side)
        assertEquals(listOf("chats", "convA", "convB"), panes.sceneEntries)
    }
}
