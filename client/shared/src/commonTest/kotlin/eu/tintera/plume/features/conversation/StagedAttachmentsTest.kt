package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.sendableTogether
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StagedAttachmentsTest {

    private fun photo(n: Int) = OutgoingAttachment.Photo(path = "/p$n.jpg", width = 1, height = 1)
    private fun document(n: Int) = OutgoingAttachment.Document(path = "/d$n.pdf")

    @Test
    fun sameKindAppends() {
        val staged = stageAttachments(listOf(photo(1)), listOf(photo(2), photo(3)))
        assertEquals(listOf(photo(1), photo(2), photo(3)), staged.attachments)
        assertFalse(staged.overflow)
    }

    private fun video(n: Int) = OutgoingAttachment.Video(
        path = "/v$n.mp4", width = 1, height = 1, durationSeconds = 1, thumbnail = null, streamable = false,
    )

    @Test
    fun photosAndVideosMix() {
        val staged = stageAttachments(listOf(photo(1)), listOf(video(1)))
        assertEquals(listOf(photo(1), video(1)), staged.attachments)
    }

    @Test
    fun documentReplacesVideos() {
        val staged = stageAttachments(listOf(video(1)), listOf(document(1)))
        assertEquals(listOf(document(1)), staged.attachments)
    }

    @Test
    fun otherKindReplaces() {
        val staged = stageAttachments(listOf(photo(1), photo(2)), listOf(document(1)))
        assertEquals(listOf(document(1)), staged.attachments)
        assertFalse(staged.overflow)
    }

    @Test
    fun overLimitIsTrimmedAndReported() {
        val current = (1..8).map(::photo)
        val staged = stageAttachments(current, (9..12).map(::photo))
        assertEquals((1..MAX_ATTACHMENTS).map(::photo), staged.attachments)
        assertTrue(staged.overflow)
    }

    @Test
    fun mixedPickKeepsOnlyFirstKind() {
        val staged = stageAttachments(emptyList(), listOf(document(1), photo(1), document(2)))
        assertEquals(listOf(document(1), document(2)), staged.attachments)
    }

    @Test
    fun emptyPickKeepsCurrent() {
        val staged = stageAttachments(listOf(photo(1)), emptyList())
        assertEquals(listOf(photo(1)), staged.attachments)
    }

    @Test
    fun mizeniJenVSoukromemChatuSJednouPrilohou() {
        assertTrue(selfDestructAllowed(isPrivateChat = true, isSavedMessages = false, attachmentCount = 1))
        // Skupina a kanál: TDLib „Messages can self-destruct only in private chats".
        assertFalse(selfDestructAllowed(isPrivateChat = false, isSavedMessages = false, attachmentCount = 1))
        assertFalse(selfDestructAllowed(isPrivateChat = true, isSavedMessages = true, attachmentCount = 1))
        assertFalse(selfDestructAllowed(isPrivateChat = true, isSavedMessages = false, attachmentCount = 2))
    }

    @Test
    fun zAlbaMizeniViditelneZmizi() {
        val viewOnce = photo(1).copy(mode = AttachmentMode.ViewOnce)
        val timer = video(1).copy(mode = AttachmentMode.Timer(10))
        val spoiler = photo(2).copy(mode = AttachmentMode.Spoiler)

        val dropped = dropForbiddenSelfDestruct(listOf(viewOnce, timer, spoiler), allowed = false)

        // Mizení zmizí, spoiler s mizením nesouvisí a zůstane.
        assertEquals(listOf(photo(1), video(1), spoiler), dropped)
    }

    @Test
    fun bezMizeniNeniCoHlasit() {
        assertNull(dropForbiddenSelfDestruct(listOf(photo(1), photo(2)), allowed = false))
        assertNull(dropForbiddenSelfDestruct(listOf(photo(1).copy(mode = AttachmentMode.ViewOnce)), allowed = true))
    }

    // --- Druhy obsahu, které chat přijme ---

    private val jenFotky = ContentAccess(
        photos = true,
        videos = false,
        documents = false,
        audios = false,
        voiceNotes = false,
        videoNotes = false,
        stickers = false,
        polls = false,
    )

    @Test
    fun vyberVydaObojiAleProjdouJenDovolene() {
        // Systémový výběr fotek vydá fotky i videa jedním tahem.
        val picked = listOf(photo(1), video(1))
        assertEquals<List<OutgoingAttachment>>(listOf(photo(1)), allowedByContent(picked, jenFotky))
    }

    @Test
    fun nicDovolenehoZnamenaPrazdnySeznam() {
        assertEquals(emptyList<OutgoingAttachment>(), allowedByContent(listOf(video(1)), jenFotky))
    }

    @Test
    fun fotkaJakoSouborSeRidiPravemNaSoubory() {
        // Režim „Odeslat jako soubor" posílá fotku přes InputMessageDocument.
        val jakoSoubor = photo(1).copy(mode = AttachmentMode.AsFile)
        assertEquals(emptyList<OutgoingAttachment>(), allowedByContent(listOf(jakoSoubor), jenFotky))
        assertEquals<List<OutgoingAttachment>>(
            listOf(jakoSoubor),
            allowedByContent(listOf(jakoSoubor), jenFotky.copy(documents = true)),
        )
    }

    @Test
    fun beznaFotkaSePravemNaSouboryNeridi() {
        assertEquals(
            emptyList<OutgoingAttachment>(),
            allowedByContent(listOf(photo(1)), jenFotky.copy(photos = false, documents = true)),
        )
    }
}

class FormatDurationTest {
    @Test
    fun formats() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:42", formatDuration(42))
        assertEquals("12:05", formatDuration(725))
        assertEquals("1:02:03", formatDuration(3723))
    }

    // --- Hudba jako skladba ---

    private val skladba = OutgoingAttachment.Audio(path = "/a/song.mp3", title = "Píseň", performer = null, durationSeconds = 180, cover = null)

    /** Skladba se vybírá jako soubor, takže se k souborům přidává, ne je nahrazuje. */
    @Test
    fun skladbaSeSkladaSeSoubory() {
        val soubor = OutgoingAttachment.Document("/a/doc.pdf")
        assertEquals(listOf(soubor, skladba), stageAttachments(listOf(soubor), listOf(skladba)).attachments)
    }

    @Test
    fun skladbaBezPravaNaHudbuOdejdeJakoSoubor() {
        val nic = ContentAccess.All.copy(documents = false, audios = false)
        val jenSoubory = nic.copy(documents = true)
        assertEquals<List<OutgoingAttachment>>(listOf(skladba.asDocument()), allowedByContent(listOf(skladba), jenSoubory))
        assertEquals<List<OutgoingAttachment>>(listOf(skladba), allowedByContent(listOf(skladba), jenSoubory.copy(audios = true)))
        assertEquals(emptyList<OutgoingAttachment>(), allowedByContent(listOf(skladba), nic))
    }

    /** TDLib: skladby jdou do alba jen se skladbami — smíšený výběr odejde celý jako soubory. */
    @Test
    fun smisenyVyberOdejdeJakoSoubory() {
        val soubor = OutgoingAttachment.Document("/a/doc.pdf")
        assertEquals(listOf(soubor, skladba.asDocument()), sendableTogether(listOf(soubor, skladba)))
        assertEquals(listOf(skladba, skladba), sendableTogether(listOf(skladba, skladba)))
    }
}
