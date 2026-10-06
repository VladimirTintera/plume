package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Dock živé polohy: které polohy v něm jsou a kolik ze zprávy schovaly pruhy nebo pole.
 */
class LiveLocationDockTest {

    private fun message(id: Long, media: MessageMedia?, outgoing: Boolean = false) = ChatMessage(
        id = id,
        isOutgoing = outgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = "",
        entities = emptyList(),
        media = media,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
    )

    private fun live(id: Long, until: Long, outgoing: Boolean = false) =
        message(id, MessageMedia.Location(50.0, 14.0, liveUntil = until), outgoing)

    @Test
    fun sdilenaPolohaPribudeANahradiSe() {
        val docked = emptyList<ChatMessage>().withLiveLocation(live(5, until = 100), now = 10)
        assertEquals(listOf(5L), docked.map { it.id })
        val moved = docked.withLiveLocation(live(5, until = 200), now = 10)
        assertEquals(listOf(5L), moved.map { it.id })
        assertEquals(200L, (moved.single().media as MessageMedia.Location).liveUntil)
    }

    @Test
    fun zastavenaPolohaZDockuZmizi() {
        val docked = listOf(live(5, until = 100))
        // Zastavení přijde úpravou s koncem v minulosti.
        assertEquals(emptyList(), docked.withLiveLocation(live(5, until = 10), now = 10))
    }

    @Test
    fun zpravaBezZivePolohySeznamNemeni() {
        val docked = listOf(live(5, until = 100))
        assertEquals(docked, docked.withLiveLocation(message(6, null), now = 10))
        assertEquals(docked, docked.withLiveLocation(message(7, MessageMedia.Location(50.0, 14.0)), now = 10))
    }

    @Test
    fun vlastniPolohaJdeNapredPakOdNejnovejsi() {
        val docked = emptyList<ChatMessage>()
            .withLiveLocation(live(3, until = 100), now = 0)
            .withLiveLocation(live(9, until = 100), now = 0)
            .withLiveLocation(live(1, until = 100, outgoing = true), now = 0)
        assertEquals(listOf(1L, 9L, 3L), docked.map { it.id })
    }

    @Test
    fun prosleSeZahodiANejblizsiKonec() {
        val docked = listOf(live(1, until = 50), live(2, until = 200), live(3, until = Long.MAX_VALUE))
        assertEquals(listOf(2L, 3L), docked.withoutEnded(now = 60).map { it.id })
        // „Dokud ji nevypnu" nekončí nikdy, a proto se na ni nečeká.
        assertEquals(50L, docked.nextLiveEnd())
        assertNull(listOf(live(3, until = Long.MAX_VALUE)).nextLiveEnd())
    }

    @Test
    fun prubehPodleSchovaneCasti() {
        // Obrácený seznam: offset roste odspodu, horní okraj je na 1000, pole na 0.
        assertEquals(0f, dockProgress(offset = 100, size = 200, top = 1000f, bottom = 0f))
        assertEquals(0.5f, dockProgress(offset = 900, size = 200, top = 1000f, bottom = 0f))
        assertEquals(1f, dockProgress(offset = 1100, size = 200, top = 1000f, bottom = 0f))
        assertEquals(0.25f, dockProgress(offset = -50, size = 200, top = 1000f, bottom = 0f))
        assertEquals(1f, dockProgress(offset = 0, size = 0, top = 1000f, bottom = 0f))
    }
}
