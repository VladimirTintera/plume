package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.UiText
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionTranscriptTest {

    private val zone = TimeZone.UTC

    /** Prostředky Compose na JVM nejsou: datum „d. m. rrrr" podle argumentů, ostatní klíčem. */
    private val resolve: suspend (UiText) -> String = { text ->
        when (text) {
            is UiText.Raw -> text.text
            is UiText.Resource ->
                if (text.resource.key == "date_numeric") text.args.joinToString(". ") else text.resource.key
            is UiText.Plural -> text.resource.key
        }
    }

    private fun at(hour: Int, minute: Int) =
        LocalDateTime(2026, 10, 10, hour, minute).toInstant(zone).epochSeconds.toInt()

    private fun message(
        id: Long,
        text: String,
        time: Int,
        outgoing: Boolean = false,
        sender: String? = "Jana",
        media: MessageMedia? = null,
    ) = ChatMessage(
        id = id,
        isOutgoing = outgoing,
        senderName = sender,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = emptyList(),
        media = media,
        linkPreview = null,
        reactions = emptyList(),
        sendState = SendState.Sent,
        replyToMessageId = null,
        isService = false,
        timestamp = time,
        isEdited = false,
    )

    private suspend fun transcript(vararg messages: ChatMessage) =
        selectionTranscript(messages.toList(), me = "Já", chatTitle = "Petr", zone = zone, resolve = resolve)

    @Test
    fun radekNaZpravuOdNejstarsi() = runTest {
        assertEquals(
            "[10. 10. 2026 09:05] Jana: Ahoj\n[10. 10. 2026 09:07] Já: Čau",
            transcript(message(2, "Čau", at(9, 7), outgoing = true), message(1, "Ahoj", at(9, 5))),
        )
    }

    /** V soukromém chatu TDLib jméno odesílatele nevyplňuje — podepíše ho název chatu. */
    @Test
    fun bezJmenaPodepiseNazevChatu() = runTest {
        assertEquals("[10. 10. 2026 18:00] Petr: Jdu", transcript(message(1, "Jdu", at(18, 0), sender = null)))
    }

    @Test
    fun prilohaMaPopisVZavorkach() = runTest {
        val photo = MessageMedia.Photo(fileId = 1, thumbnailFileId = null, fullFileId = 1, width = 10, height = 10)
        assertEquals(
            "[10. 10. 2026 12:00] Jana: [media_photo] Pláž\n[10. 10. 2026 12:01] Jana: [media_photo]",
            transcript(message(1, "Pláž", at(12, 0), media = photo), message(2, "", at(12, 1), media = photo)),
        )
    }

    @Test
    fun zpravaBezObsahuSeVynecha() = runTest {
        assertEquals("[10. 10. 2026 12:01] Jana: B", transcript(message(1, "", at(12, 0)), message(2, "B", at(12, 1))))
    }

    /** Víc řádků zprávy zůstane pod sebou; přepis se nerozpadne na falešné zprávy, jen se nečte řádek po řádku. */
    @Test
    fun viceradkovaZpravaZustane() = runTest {
        assertEquals("[10. 10. 2026 12:00] Jana: a\nb", transcript(message(1, "a\nb", at(12, 0))))
    }

    @Test
    fun markdownSHlavickouAFormatovanim() = runTest {
        val bold = message(1, "Ahoj světe", at(9, 5)).copy(
            entities = listOf(eu.tintera.plume.core.telegram.MessageEntity(5, 5, eu.tintera.plume.core.telegram.MessageEntityType.Bold)),
        )
        assertEquals(
            "**Jana** · 10. 10. 2026 09:05\nAhoj **světe**\n\n**Já** · 10. 10. 2026 09:07\nČau",
            selectionMarkdown(
                listOf(message(2, "Čau", at(9, 7), outgoing = true), bold),
                me = "Já",
                chatTitle = "Petr",
                zone = zone,
                resolve = resolve,
            ),
        )
    }
}
