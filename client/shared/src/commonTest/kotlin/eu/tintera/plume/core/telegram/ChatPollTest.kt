package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.Location
import dev.g000sha256.tdl.dto.PollMediaLink
import dev.g000sha256.tdl.dto.PollMediaLocation
import dev.g000sha256.tdl.dto.PollMediaVenue
import dev.g000sha256.tdl.dto.Venue
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.Poll
import dev.g000sha256.tdl.dto.PollOption
import dev.g000sha256.tdl.dto.PollTypeQuiz
import dev.g000sha256.tdl.dto.PollTypeRegular
import dev.g000sha256.tdl.dto.PollVoteRestrictionReasonClosed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kdy se v anketě hlasuje, kdy se ukazují výsledky a jak se převádí z TDLib. */
class ChatPollTest {

    private fun answer(index: Int, chosen: Boolean = false, pending: Boolean = false) =
        PollAnswer(index = index, text = "odpověď $index", voters = 0, percentage = 0, isChosen = chosen, isBeingChosen = pending)

    private fun poll(
        vararg answers: PollAnswer,
        closed: Boolean = false,
        canSeeResults: Boolean = true,
        revoting: Boolean = true,
        quiz: PollQuiz? = null,
        restriction: PollRestriction? = null,
    ) = ChatPoll(
        question = "Kam na výlet?",
        options = answers.toList(),
        totalVoters = 3,
        isAnonymous = false,
        multipleAnswers = false,
        allowsRevoting = revoting,
        isClosed = closed,
        canSeeResults = canSeeResults,
        canGetVoters = true,
        quiz = quiz,
        restriction = restriction,
    )

    @Test
    fun predHlasovanimSeHlasujeAVysledkyNejsou() {
        val poll = poll(answer(0), answer(1))
        assertTrue(poll.canVote)
        assertFalse(poll.showsResults)
        assertFalse(poll.canRetract)
    }

    @Test
    fun poHlasovaniVysledkyAZruseniHlasu() {
        val poll = poll(answer(0, chosen = true), answer(1))
        assertFalse(poll.canVote)
        assertTrue(poll.showsResults)
        assertTrue(poll.canRetract)
    }

    @Test
    fun behemOdesilaniHlasuSeZnovuNehlasuje() {
        // `isBeingChosen`: hlas odešel a čeká na server — druhé ťuknutí by poslalo druhý.
        val poll = poll(answer(0, pending = true), answer(1))
        assertTrue(poll.isVoting)
        assertFalse(poll.canVote)
    }

    @Test
    fun uzavrenaAnketaUkazeVysledkyIBezHlasu() {
        val poll = poll(answer(0), answer(1), closed = true, restriction = PollRestriction.Closed)
        assertTrue(poll.showsResults)
        assertFalse(poll.canVote)
        assertFalse(poll.canRetract)
    }

    @Test
    fun anketaSeSchovanymiVysledkyPoHlasovaniUkazeJenVolbu() {
        val poll = poll(answer(0, chosen = true), answer(1), canSeeResults = false)
        assertFalse(poll.showsResults)
        assertFalse(poll.canVote)
    }

    @Test
    fun omezenyUzivatelNehlasuje() {
        val poll = poll(answer(0), answer(1), restriction = PollRestriction.MembershipRequired)
        assertFalse(poll.canVote)
    }

    @Test
    fun hlasBezPravaZmenyZrusitNejde() {
        assertFalse(poll(answer(0, chosen = true), answer(1), revoting = false).canRetract)
    }

    @Test
    fun odpovedNaKvizZrusitNejde() {
        val quiz = poll(answer(0, chosen = true), answer(1), quiz = PollQuiz(correct = setOf(1)))
        assertFalse(quiz.canRetract)
    }

    @Test
    fun kvizOznaciSpravnouVzdyASpatnouJenVybranou() {
        val chosenWrong = answer(0, chosen = true)
        val correct = answer(1)
        val other = answer(2)
        val quiz = poll(chosenWrong, correct, other, quiz = PollQuiz(correct = setOf(1)))

        assertEquals(QuizMark.Wrong, quiz.quizMark(chosenWrong))
        assertEquals(QuizMark.Correct, quiz.quizMark(correct))
        assertNull(quiz.quizMark(other))
    }

    @Test
    fun kvizBezZnameSpravneOdpovediNicNeoznaci() {
        // Před odpovědí server správnou odpověď nevydá — nic se nesmí obarvit.
        val quiz = poll(answer(0), answer(1), quiz = PollQuiz(correct = emptySet()))
        assertNull(quiz.quizMark(quiz.options[0]))
        assertNull(poll(answer(0, chosen = true)).quizMark(answer(0, chosen = true)))
    }

    // --- převod z TDLib ---

    private fun text(value: String) = FormattedText(text = value, entities = emptyArray())

    private fun option(text: String, chosen: Boolean = false, percentage: Int = 0) = PollOption(
        id = "",
        text = text(text),
        media = null,
        voterCount = 0,
        votePercentage = percentage,
        recentVoterIds = emptyArray(),
        isChosen = chosen,
        isBeingChosen = false,
        author = null,
        additionDate = 0,
    )

    private fun message(
        options: List<PollOption>,
        order: IntArray = intArrayOf(),
        quiz: PollTypeQuiz? = null,
        closed: Boolean = false,
    ) = MessagePoll(
        poll = Poll(
            id = 1,
            question = text("Kam?"),
            options = options.toTypedArray(),
            totalVoterCount = 0,
            recentVoterIds = emptyArray(),
            canGetVoters = false,
            canSeeResults = true,
            isAnonymous = true,
            allowsMultipleAnswers = false,
            allowsRevoting = true,
            membersOnly = false,
            countryCodes = emptyArray(),
            optionOrder = order,
            type = quiz ?: PollTypeRegular(),
            openPeriod = 0,
            closeDate = 0,
            isClosed = closed,
            voteRestrictionReason = if (closed) PollVoteRestrictionReasonClosed() else null,
        ),
        description = text(""),
        media = null,
        canAddOption = false,
    )

    @Test
    fun odpovediJsouVPoradiOdServeruAleSIndexemPuvodnim() {
        val poll = message(listOf(option("A"), option("B"), option("C")), order = intArrayOf(2, 0, 1)).toChatPoll()
        assertEquals(listOf("C", "A", "B"), poll.options.map { it.text })
        // Hlasuje se PŮVODNÍM indexem — „C" je v odpovědi pořád třetí.
        assertEquals(listOf(2, 0, 1), poll.options.map { it.index })
    }

    @Test
    fun neuplnePoradiNeztratiZadnouOdpoved() {
        // Neplatný index se zahodí, chybějící odpověď se připojí na konec.
        val poll = message(listOf(option("A"), option("B"), option("C")), order = intArrayOf(1, 7, 1)).toChatPoll()
        assertEquals(listOf("B", "A", "C"), poll.options.map { it.text })
    }

    @Test
    fun bezPoradiZustavaPuvodni() {
        val poll = message(listOf(option("A"), option("B"))).toChatPoll()
        assertEquals(listOf(0, 1), poll.options.map { it.index })
    }

    @Test
    fun kvizNeseSpravneOdpovediAVysvetleni() {
        val quiz = PollTypeQuiz(correctOptionIds = intArrayOf(1), explanation = text("Protože."), explanationMedia = null)
        val poll = message(listOf(option("A", chosen = true), option("B")), quiz = quiz).toChatPoll()
        assertEquals(setOf(1), poll.quiz?.correct)
        assertEquals("Protože.", poll.quiz?.explanation)
    }

    @Test
    fun uzavrenaAnketaNeseDuvod() {
        val poll = message(listOf(option("A")), closed = true).toChatPoll()
        assertEquals(PollRestriction.Closed, poll.restriction)
        assertTrue(poll.isClosed)
    }

    // --- přílohy ---

    @Test
    fun odkazMistoAPolohaMajiVlastniTvar() {
        val link = PollMediaLink(url = "https://example.com", linkPreview = null).toAttachment()
        assertEquals(PollAttachment.Link(url = "https://example.com", title = null, photo = null), link)

        val venue = PollMediaVenue(
            Venue(Location(50.0, 14.0, 0.0), title = "Náměstí", address = "Praha", provider = "", id = "", type = ""),
        ).toAttachment() as PollAttachment.Place
        assertEquals("Náměstí", venue.title)
        assertEquals("Praha", venue.address)

        // Holá poloha název nemá — nakreslí se jako „Poloha".
        val place = PollMediaLocation(Location(50.7, 15.7, 0.0)).toAttachment() as PollAttachment.Place
        assertNull(place.title)
        assertEquals("https://maps.google.com/?q=50.7,15.7", place.mapUrl)
    }

    @Test
    fun prilohaOdpovediZustavaUSveOdpovediIPoPrehazeni() {
        val withLink = option("B").let {
            PollOption(
                id = it.id, text = it.text, media = PollMediaLink(url = "https://b.example", linkPreview = null),
                voterCount = 0, votePercentage = 0, recentVoterIds = emptyArray(), isChosen = false,
                isBeingChosen = false, author = null, additionDate = 0,
            )
        }
        val poll = message(listOf(option("A"), withLink), order = intArrayOf(1, 0)).toChatPoll()
        assertEquals("B", poll.options.first().text)
        assertTrue(poll.options.first().media is PollAttachment.Link)
        // Prohlížeč hledá přílohu podle PŮVODNÍHO indexu, ne podle pozice na obrazovce.
        assertTrue(poll.attachmentAt(1) is PollAttachment.Link)
        assertNull(poll.attachmentAt(0))
        assertNull(poll.attachmentAt(POLL_QUESTION_MEDIA))
    }

    @Test
    fun doProhlizeceJenFotkaAVideo() {
        val photo = PollAttachment.Media(MessageMedia.Photo(fileId = 1, thumbnailFileId = null, fullFileId = 1, width = 1, height = 1))
        val gif = PollAttachment.Media(MessageMedia.Animation(fileId = 2, thumbnailFileId = null, width = 1, height = 1, sizeBytes = 1))
        assertEquals(photo.media, photo.viewable)
        assertNull(gif.viewable)
        assertNull(PollAttachment.Link("https://x", null, null).viewable)
    }
}
