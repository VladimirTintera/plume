package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stav konverzace kolem výměny id po odeslání.
 *
 * `newMessages` a `sentMessageUpdates` jsou dva samostatné odběry, takže se jejich pořadí může
 * prohodit. Obojí pořadí proto musí skončit stejně — a hlavně s jedinečnými klíči položek.
 */
class ConversationStateTest {

    /** Dočasné id, jaké TDLib dává rozepsané zprávě: není to násobek 2²⁰. */
    private val tempId = 382754357249L

    /** Skutečné id od serveru. */
    private val sentId = 365_000L shl 20

    private fun message(id: Long, sendState: SendState) = ChatMessage(
        id = id,
        isOutgoing = true,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = "ahoj",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = sendState,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
        mediaAlbumId = 0L,
    )

    private val pending = message(tempId, SendState.Sending)
    private val confirmed = message(sentId, SendState.Sent)

    private fun ConversationState.keys() = messages.map { keyOf(it) }

    @Test
    fun obvyklePoradiNejdrivZpravaPotomPotvrzeni() {
        val state = ConversationState()
            .withMessage(pending)
            .withSentMessage(oldId = tempId, message = confirmed)

        assertEquals(listOf(sentId), state.messages.map { it.id })
        // Klíč zůstává dočasný, aby položka `LazyColumn` nepřeskočila na jinou.
        assertEquals(listOf(tempId), state.keys())
    }

    @Test
    fun prohozenePoradiNezdvojiPolozku() {
        // Přesně ten případ, na kterém aplikace spadla: potvrzení předběhlo rozepsanou zprávu.
        val state = ConversationState()
            .withSentMessage(oldId = tempId, message = confirmed)
            .withMessage(pending)

        assertEquals(listOf(sentId), state.messages.map { it.id })
        assertEquals(listOf(tempId), state.keys())
    }

    @Test
    fun klicePolozekJsouJedinecne() {
        val state = ConversationState()
            .withSentMessage(oldId = tempId, message = confirmed)
            .withMessage(pending)
            // Opakované doručení téže rozepsané zprávy nesmí seznam rozbít ani podruhé.
            .withMessage(pending)

        assertEquals(state.keys().distinct(), state.keys())
    }

    @Test
    fun beznaZpravaSePridava() {
        val incoming = message(sentId + (1L shl 20), SendState.Sent)

        val state = ConversationState()
            .withSentMessage(oldId = tempId, message = confirmed)
            .withMessage(incoming)

        assertEquals(listOf(incoming.id, sentId), state.messages.map { it.id })
        assertTrue(state.keys().distinct() == state.keys())
    }

    @Test
    fun zpravySeDrziOdNejnovejsi() {
        val older = message(sentId - (1L shl 20), SendState.Sent)

        val state = ConversationState().withMessage(older).withMessage(confirmed)

        assertEquals(listOf(confirmed.id, older.id), state.messages.map { it.id })
    }

    @Test
    fun potvrzenaZpravaSeAktualizujeBezeZmenyKlice() {
        val state = ConversationState()
            .withMessage(pending)
            .withSentMessage(oldId = tempId, message = confirmed)
            .withMessage(confirmed.copy(text = "upraveno"))

        assertEquals(1, state.messages.size)
        assertEquals("upraveno", state.messages.single().text)
        assertEquals(listOf(tempId), state.keys())
    }

    /*
     * Okno v historii (skok na starou zprávu). Id jsou tu malá čísla — na řazení a porovnání
     * záleží jen pořadí.
     */
    private fun at(id: Long, outgoing: Boolean = false, sendState: SendState? = SendState.Sent) =
        message(id, sendState ?: SendState.Sent).copy(isOutgoing = outgoing, sendState = sendState)

    private val window = ConversationState().withWindow(listOf(at(10), at(12), at(11)))

    @Test
    fun oknoNahradiSeznamAKonceNezna() {
        val state = ConversationState().withMessage(at(99)).withWindow(listOf(at(10), at(12), at(11)))

        assertEquals(listOf(12L, 11L, 10L), state.messages.map { it.id })
        assertEquals(false, state.reachedStart)
        assertEquals(false, state.reachedEnd)
    }

    /** Cizí nová zpráva by za oknem udělala díru — zahodí se, dorazí se stránkováním. */
    @Test
    fun ciziNovaZpravaSeDoOknaNepridava() {
        assertEquals(listOf(12L, 11L, 10L), window.withMessage(at(99)).messages.map { it.id })
    }

    /** Vlastní odesílaná zpráva: seznam začne od ní a obrazovka sjede dolů. */
    @Test
    fun odeslaniZOknaVratiSeznamKPritomnosti() {
        val state = window.withMessage(at(99, outgoing = true, sendState = SendState.Sending))

        assertEquals(listOf(99L), state.messages.map { it.id })
        assertTrue(state.reachedEnd)
        assertEquals(ScrollTarget(99L, ScrollAnchor.Bottom), state.scrollTarget)
    }

    @Test
    fun strankaKNovejsimSePripojiAKonecSePoznaPodlePrazdne() {
        // TDLib vrací i výchozí zprávu (12) — ta se nezdvojí.
        val next = window.withNewerPage(newestId = 12, page = listOf(at(12), at(13), at(14)))
        assertEquals(listOf(14L, 13L, 12L, 11L, 10L), next.messages.map { it.id })
        assertEquals(false, next.reachedEnd)

        val end = next.withNewerPage(newestId = 14, page = listOf(at(14)))
        assertTrue(end.reachedEnd)
    }

    /** Stránka ze starého okna se do nového nevmíchá (mezitím skok jinam nebo odeslání). */
    @Test
    fun opozdenaStrankaSeZahodi() {
        val moved = window.withWindow(listOf(at(50), at(51)))

        assertEquals(moved, moved.withNewerPage(newestId = 12, page = listOf(at(13))))
        assertEquals(moved, moved.withOlderPage(oldestId = 10, page = listOf(at(9))))
    }

    @Test
    fun strankaKeStarsimNavazePodOknem() {
        val state = window.withOlderPage(oldestId = 10, page = listOf(at(9), at(8)))
        assertEquals(listOf(12L, 11L, 10L, 9L, 8L), state.messages.map { it.id })
        assertTrue(window.withOlderPage(oldestId = 10, page = emptyList()).reachedStart)
    }
}
