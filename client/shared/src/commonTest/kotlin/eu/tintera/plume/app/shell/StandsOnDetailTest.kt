package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val slot = AccountSlot.Primary

/**
 * Kořen bočního panelu ([standsOnDetail]) nemá šipku zpět ani ✕ — zavírá ho přepínač v hlavičce
 * vedle. Profil nad profilem šipku mít musí, jinak by se do profilu skupiny nedalo vrátit.
 */
class StandsOnDetailTest {

    @Test
    fun profilNadKonverzaciJeKorenPanelu() {
        val profile = ChatProfileRoute(slot, 2)
        assertTrue(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 2), profile).standsOnDetail(profile))
    }

    /** Profil odesílatele otevřený z konverzace skupiny je taky kořen: stojí na konverzaci. */
    @Test
    fun profilClenaNadKonverzaciJeKorenPanelu() {
        val member = ChatProfileRoute(slot, 9)
        assertTrue(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 2), member).standsOnDetail(member))
    }

    @Test
    fun profilNadSeznamemTematJeKorenPanelu() {
        val profile = ChatProfileRoute(slot, 3)
        assertTrue(listOf<NavKey>(ChatsRoute, ForumTopicsRoute(slot, 3), profile).standsOnDetail(profile))
    }

    @Test
    fun archivniProfilNadArchivniKonverzaciJeKorenPanelu() {
        val profile = ArchiveChatProfileRoute(slot, 4)
        assertTrue(listOf<NavKey>(ChatsRoute, ArchiveRoute, ArchiveConversationRoute(slot, 4), profile).standsOnDetail(profile))
    }

    @Test
    fun profilNadProfilemNeniKorenPanelu() {
        val member = ChatProfileRoute(slot, 9)
        assertFalse(listOf<NavKey>(ChatsRoute, ConversationRoute(slot, 2), ChatProfileRoute(slot, 2), member).standsOnDetail(member))
    }

    /** Profil přímo nad seznamem (bez konverzace) by bez šipky nešel zavřít. */
    @Test
    fun profilNadSeznamemNeniKorenPanelu() {
        val profile = ChatProfileRoute(slot, 2)
        assertFalse(listOf<NavKey>(ChatsRoute, profile).standsOnDetail(profile))
    }
}
