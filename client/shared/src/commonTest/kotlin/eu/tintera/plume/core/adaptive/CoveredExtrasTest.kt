package eu.tintera.plume.core.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Profil ve třetím panelu patří ke konverzaci pod sebou ([coveredExtras]). Otevře-li se nad ním
 * jiná konverzace, musí ze zásobníku pryč — jinak by scéna kreslila profil vedle cizího chatu.
 * Co ho nahradí, testuje `FollowProfileTest`.
 */
class CoveredExtrasTest {

    private val archive = "archive"

    private val pane = mapOf(
        "chats" to PaneInfo(PaneRole.List, Unit),
        "convA" to PaneInfo(PaneRole.Detail, Unit),
        "convB" to PaneInfo(PaneRole.Detail, Unit),
        "profileA" to PaneInfo(PaneRole.Extra, Unit),
        "editA" to PaneInfo(PaneRole.Extra, Unit),
        "newChat" to PaneInfo(PaneRole.List, Unit),
        "archive" to PaneInfo(PaneRole.List, archive),
        "archConvY" to PaneInfo(PaneRole.Detail, archive),
        "archProfileY" to PaneInfo(PaneRole.Extra, archive),
        // "media" je přes celou plochu a metadata panelu nemá.
    )

    private fun List<String>.normalized(): List<String> {
        val covered = coveredExtras { pane[it] }.toSet()
        return filterIndexed { index, _ -> index !in covered }
    }

    @Test
    fun profilNadKonverzaciZustava() {
        val stack = listOf("chats", "convA", "profileA", "editA")
        assertEquals(stack, stack.normalized())
    }

    @Test
    fun jinaKonverzaceNadProfilemHoZavre() {
        assertEquals(
            listOf("chats", "convA", "convB"),
            listOf("chats", "convA", "profileA", "editA", "convB").normalized(),
        )
    }

    @Test
    fun prohlizecNadProfilemHoNezavre() {
        val stack = listOf("chats", "convA", "profileA", "media")
        assertEquals(stack, stack.normalized())
    }

    @Test
    fun seznamNadProfilemHoNezavre() {
        val stack = listOf("chats", "convA", "profileA", "newChat")
        assertEquals(stack, stack.normalized())
    }

    @Test
    fun archivNadProfilemHlavnihoSeznamuHoNezavre() {
        // Po zavření archivu se má vrátit konverzace i s profilem, jak je uživatel nechal.
        val stack = listOf("chats", "convA", "profileA", "archive", "archConvY", "archProfileY")
        assertEquals(stack, stack.normalized())
    }

    @Test
    fun indexyJdouOdKonce() {
        val stack = listOf("chats", "convA", "profileA", "convB", "profileA", "editA", "convA")
        // Odebíráním po řadě se indexy neposunou.
        assertEquals(listOf(5, 4, 2), stack.coveredExtras { pane[it] })
    }
}
