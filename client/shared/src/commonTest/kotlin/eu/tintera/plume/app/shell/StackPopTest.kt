package eu.tintera.plume.app.shell

import eu.tintera.plume.core.accounts.AccountSlot
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.MediaRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val slot = AccountSlot.Primary

/**
 * Zavření obrazovky z její vlastní šipky zpět ([pop]).
 *
 * Obrazovka na odchodu dál reaguje na ťuknutí, takže šipka zpět může přijít podruhé, když už
 * obrazovka v zásobníku není. Dřív to byl `removeLastOrNull()` a druhé ťuknutí odebralo seznam
 * chatů — `NavDisplay` s prázdným zásobníkem shodil aplikaci.
 */
class StackPopTest {

    private val conversation = ConversationRoute(slot, chatId = 7)

    @Test
    fun druheZavreniOdchazejiciKonverzaceKorenNeodebere() {
        val stack = mutableListOf<NavKey>(ChatsRoute, conversation)

        assertTrue(stack.pop(conversation))
        assertFalse(stack.pop(conversation))

        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }

    /** Konverzace pod prohlížečem médií nesmí zavřít prohlížeč místo sebe. */
    @Test
    fun odebiraSvouPolozkuNePosledni() {
        val media = MediaRoute(slot, chatId = 7, messageId = 1)
        val stack = mutableListOf<NavKey>(ChatsRoute, conversation, media)

        assertTrue(stack.pop(conversation))

        assertEquals(listOf<NavKey>(ChatsRoute, media), stack)
    }

    @Test
    fun korenNeodebereNikdy() {
        val stack = mutableListOf<NavKey>(ChatsRoute)

        assertFalse(stack.pop(ChatsRoute))

        assertEquals(listOf<NavKey>(ChatsRoute), stack)
    }
}
