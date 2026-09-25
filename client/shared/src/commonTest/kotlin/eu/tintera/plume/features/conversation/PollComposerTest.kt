package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatPoll
import eu.tintera.plume.core.telegram.PollChatKind
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.pollsAllowedIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Formulář nové ankety: pole odpovědí, co se pošle a kam anketa smí. */
class PollComposerTest {

    private fun draft(
        question: String = "Kam na výlet?",
        answers: List<String> = listOf("Sněžka", "Český ráj", ""),
        quiz: Boolean = false,
        multiple: Boolean = false,
        correct: Set<Int> = emptySet(),
        explanation: String = "",
        max: Int = 10,
    ) = pollDraftOf(
        question = question,
        answers = answers,
        anonymous = true,
        multipleAnswers = multiple,
        shuffle = false,
        quiz = quiz,
        correct = correct,
        explanation = explanation,
        maxAnswers = max,
    )

    @Test
    fun naKonciJeVzdyckyJednoPrazdnePole() {
        assertEquals(listOf("A", "B", ""), normalizedAnswers(listOf("A", "B"), max = 10))
        // Dvě prázdná na konci — jedno zmizí.
        assertEquals(listOf("A", "B", ""), normalizedAnswers(listOf("A", "B", "", ""), max = 10))
    }

    @Test
    fun nejmeneDveVzdyckyZustavaji() {
        assertEquals(listOf("", ""), normalizedAnswers(listOf(""), max = 10))
        assertEquals(listOf("A", ""), normalizedAnswers(listOf("A", "", ""), max = 10))
    }

    @Test
    fun uStropuSePrazdnePoleNepridava() {
        assertEquals(listOf("A", "B", "C"), normalizedAnswers(listOf("A", "B", "C"), max = 3))
    }

    @Test
    fun prazdnePoleUprostredZustava() {
        // Uživatel ho mohl vymazat jen proto, aby ho přepsal.
        assertEquals(listOf("A", "", "C", ""), normalizedAnswers(listOf("A", "", "C"), max = 10))
    }

    @Test
    fun spravnaOdpovedSeDrziTextuPoOdebrani() {
        assertEquals(setOf(1), correctAfterRemoval(setOf(2), removed = 0))
        assertEquals(emptySet(), correctAfterRemoval(setOf(1), removed = 1))
        assertEquals(setOf(0), correctAfterRemoval(setOf(0), removed = 1))
    }

    @Test
    fun prazdnaPoleSeNeposilaji() {
        val poll = assertNotNull(draft(answers = listOf(" Sněžka ", "", "Český ráj", "")))
        assertEquals(listOf("Sněžka", "Český ráj"), poll.answers)
        assertNull(poll.correctAnswers)
    }

    @Test
    fun bezOtazkyNeboSJedinouOdpovediNeniHotovo() {
        assertNull(draft(question = "  "))
        assertNull(draft(answers = listOf("Sněžka", "")))
    }

    @Test
    fun presStropNeniHotovo() {
        assertNull(draft(question = "a".repeat(256)))
        assertNull(draft(answers = listOf("a".repeat(101), "B")))
        assertNull(draft(answers = listOf("A", "B", "C"), max = 2))
    }

    @Test
    fun kvizBezSpravneOdpovediNeniHotovo() {
        assertNull(draft(quiz = true))
    }

    @Test
    fun spravnaOdpovedSePrepocitaNaOdeslaneOdpovedi() {
        // Pole 2 je „Český ráj"; po vynechání prázdného pole 1 je to odpověď 1.
        val poll = assertNotNull(draft(answers = listOf("Sněžka", "", "Český ráj"), quiz = true, correct = setOf(2)))
        assertEquals(setOf(1), poll.correctAnswers)
    }

    @Test
    fun viceSpravnychJenSViceOdpovedmi() {
        assertNull(draft(quiz = true, correct = setOf(0, 1)))
        assertEquals(setOf(0, 1), draft(quiz = true, multiple = true, correct = setOf(0, 1))?.correctAnswers)
    }

    @Test
    fun vysvetleniMaStropDelkyIRadku() {
        assertNull(draft(quiz = true, correct = setOf(0), explanation = "a".repeat(201)))
        assertNull(draft(quiz = true, correct = setOf(0), explanation = "a\nb\nc\nd"))
        assertEquals("a\nb", draft(quiz = true, correct = setOf(0), explanation = " a\nb ")?.explanation)
    }

    @Test
    fun vysvetleniBezKvizuNeodchazi() {
        assertEquals("", draft(explanation = "Protože.")?.explanation)
    }

    @Test
    fun anketaNejdeDoSoukromehoChatuSClovekem() {
        assertTrue(pollsAllowedIn(PollChatKind.Group))
        assertTrue(pollsAllowedIn(PollChatKind.Channel))
        assertTrue(pollsAllowedIn(PollChatKind.Bot))
        assertTrue(pollsAllowedIn(PollChatKind.SavedMessages))
        assertFalse(pollsAllowedIn(PollChatKind.Private))
        assertFalse(pollsAllowedIn(PollChatKind.Secret))
    }

    @Test
    fun dobySeNabizejiJenDoStropuServeru() {
        assertEquals(emptyList(), pollDurations(0))
        assertEquals(listOf(300, 3600), pollDurations(3600))
        assertEquals(5, pollDurations(Int.MAX_VALUE).size)
    }

    @Test
    fun vysledkyAzPoSkonceniJenSDobou() {
        val bez = pollDraftOf("Kam?", listOf("A", "B"), true, false, false, false, emptySet(), "", 10, hideResultsUntilClose = true)
        assertEquals(false, bez?.hideResultsUntilClose)
        val sDobou = pollDraftOf(
            "Kam?", listOf("A", "B"), true, false, false, false, emptySet(), "", 10,
            openPeriodSeconds = 3600,
            hideResultsUntilClose = true,
        )
        assertEquals(3600, sDobou?.openPeriodSeconds)
        assertEquals(true, sDobou?.hideResultsUntilClose)
    }

    // --- odpověď anketou ---

    private fun message(id: Long, poll: Boolean = false, replyTo: Long? = null, sendState: SendState? = SendState.Sending) =
        ChatMessage(
            id = id,
            isOutgoing = sendState != null,
            senderName = null,
            senderAvatarFileId = null,
            senderId = 1L,
            text = "Anketa: Kam?",
            entities = emptyList(),
            media = null,
            linkPreview = null,
            reactions = emptyList(),
            sendState = sendState,
            replyToMessageId = replyTo,
            isService = false,
            timestamp = 1,
            isEdited = false,
            poll = if (poll) {
                ChatPoll(
                    question = "Kam?",
                    options = emptyList(),
                    totalVoters = 0,
                    isAnonymous = true,
                    multipleAnswers = false,
                    allowsRevoting = true,
                    isClosed = false,
                    canSeeResults = true,
                    canGetVoters = false,
                )
            } else {
                null
            },
        )

    private val replying = ConversationState(replyingTo = message(7, sendState = null))

    @Test
    fun odeslanaAnketaSpotrebujeOdpoved() {
        assertTrue(replying.pollConsumesReply(message(100, poll = true, replyTo = 7)))
    }

    @Test
    fun jinaZpravaOdpovedNespotrebuje() {
        // Text odpověď uklízí při odeslání sám; tady jde jen o anketu z formuláře.
        assertFalse(replying.pollConsumesReply(message(100, poll = false, replyTo = 7)))
        // Anketa jako odpověď na jinou zprávu.
        assertFalse(replying.pollConsumesReply(message(100, poll = true, replyTo = 8)))
        // Z jiného zařízení přijde už odeslaná — rozepsanou odpověď tady nechá být.
        assertFalse(replying.pollConsumesReply(message(100, poll = true, replyTo = 7, sendState = SendState.Sent)))
        // Bez rozepsané odpovědi není co spotřebovat.
        assertFalse(ConversationState().pollConsumesReply(message(100, poll = true, replyTo = 7)))
    }
}

