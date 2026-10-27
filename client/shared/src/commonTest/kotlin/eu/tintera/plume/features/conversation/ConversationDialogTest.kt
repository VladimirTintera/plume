package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationDialogTest {

    @Test
    fun zavreJenDialogSvehoDruhu() {
        // Upozornění od bota dorazilo, zatímco byl otevřený odkaz z tlačítka; zavření odkazu
        // (odpověď na něj přišla pozdě) upozornění smazat nesmí.
        val state = ConversationState(dialog = ConversationDialog.BotAlert("Pozor!"))
        assertEquals(ConversationDialog.BotAlert("Pozor!"), state.closing<ConversationDialog.ConfirmLink>().dialog)
        assertNull(state.closing<ConversationDialog.BotAlert>().dialog)
    }

    @Test
    fun cteJenDialogSvehoDruhu() {
        val state = ConversationState(dialog = ConversationDialog.StopPoll(7))
        assertEquals(7L, state.dialog<ConversationDialog.StopPoll>()?.messageId)
        assertNull(state.dialog<ConversationDialog.AddPollOption>())
    }
}
