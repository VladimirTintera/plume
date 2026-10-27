package eu.tintera.plume.features.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationSheetTest {

    @Test
    fun pozdniDataNePrepisouJinySheet() {
        // Dohledávání hlasujících doběhlo, ale uživatel mezitím otevřel naplánované zprávy.
        val state = ConversationState(sheet = ScheduledSheet(emptyList()))
        assertEquals(state, state.updatingSheet<PollVotersSheet> { it.copy(messageId = 9) })
        assertEquals(state, state.closingSheet<PollVotersSheet>())
    }

    @Test
    fun zmenaSvehoDruhuAZavreniNull() {
        val state = ConversationState(sheet = PollVotersSheet(1))
        assertEquals(PollVotersSheet(2), state.updatingSheet<PollVotersSheet> { it.copy(messageId = 2) }.sheet)
        assertNull(state.updatingSheet<PollVotersSheet> { null }.sheet)
        assertNull(state.closingSheet<PollVotersSheet>().sheet)
    }

    @Test
    fun dialogASheetSeNevylucuji() {
        val state = ConversationState(sheet = PollVotersSheet(1)).copy(dialog = ConversationDialog.StopPoll(1))
        assertEquals(PollVotersSheet(1), state.sheet)
        assertEquals(ConversationDialog.StopPoll(1), state.closingSheet<ScheduledSheet>().dialog)
    }
}
