package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageChatBoost
import dev.g000sha256.tdl.dto.MessageChatChangeTitle
import dev.g000sha256.tdl.dto.MessageContactRegistered
import dev.g000sha256.tdl.dto.MessageForumTopicEdited
import dev.g000sha256.tdl.dto.MessageForumTopicIsClosedToggled
import dev.g000sha256.tdl.dto.MessageGameScore
import dev.g000sha256.tdl.dto.MessagePinMessage
import dev.g000sha256.tdl.dto.MessageScreenshotTaken
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.month_2
import eu.tintera.plume.resources.month_10
import eu.tintera.plume.resources.date_day_month
import eu.tintera.plume.resources.date_day_month_year
import eu.tintera.plume.resources.service_suggested_birthdate
import eu.tintera.plume.resources.service_you_suggested_birthdate
import eu.tintera.plume.resources.service_actor_suggested_birthdate
import eu.tintera.plume.resources.service_actor_boost
import eu.tintera.plume.resources.service_actor_boost_count
import eu.tintera.plume.resources.service_actor_contact_joined
import eu.tintera.plume.resources.service_actor_game_score
import eu.tintera.plume.resources.service_actor_title_changed
import eu.tintera.plume.resources.service_actor_topic_closed
import eu.tintera.plume.resources.service_actor_topic_icon_removed
import eu.tintera.plume.resources.service_actor_topic_renamed
import eu.tintera.plume.resources.service_you_boost_count
import eu.tintera.plume.resources.service_you_screenshot
import eu.tintera.plume.resources.service_you_title_changed
import eu.tintera.plume.resources.service_you_topic_reopened
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Oznámení se jménem aktéra: cizí jméno, nebo věta ve 2. osobě, když to byl účet sám. */
class ActorServiceTextTest {

    private val jana = ServicePerson("Jana")
    private val me = ServicePerson(name = "", isMe = true)

    @Test
    fun zmenaNazvu() {
        assertEquals(UiText(Res.string.service_actor_title_changed, "Jana", "Výlet"), MessageChatChangeTitle("Výlet").actorServiceText(jana))
        assertEquals(UiText(Res.string.service_you_title_changed, "Výlet"), MessageChatChangeTitle("Výlet").actorServiceText(me))
    }

    @Test
    fun snimekObrazovky() {
        assertEquals(UiText(Res.string.service_you_screenshot), MessageScreenshotTaken().actorServiceText(me))
    }

    @Test
    fun temaRikaCoSeZmenilo() {
        assertEquals(
            UiText(Res.string.service_actor_topic_renamed, "Jana", "Výlet"),
            MessageForumTopicEdited(name = "Výlet", editIconCustomEmojiId = false, iconCustomEmojiId = 0L).actorServiceText(jana),
        )
        assertEquals(
            UiText(Res.string.service_actor_topic_icon_removed, "Jana"),
            MessageForumTopicEdited(name = "", editIconCustomEmojiId = true, iconCustomEmojiId = 0L).actorServiceText(jana),
        )
        assertEquals(UiText(Res.string.service_actor_topic_closed, "Jana"), MessageForumTopicIsClosedToggled(isClosed = true).actorServiceText(jana))
        assertEquals(UiText(Res.string.service_you_topic_reopened), MessageForumTopicIsClosedToggled(isClosed = false).actorServiceText(me))
    }

    @Test
    fun boostSPoctem() {
        assertEquals(UiText(Res.string.service_actor_boost, "Jana"), MessageChatBoost(boostCount = 1).actorServiceText(jana))
        assertEquals(UiText(Res.plurals.service_actor_boost_count, 3, "Jana", 3), MessageChatBoost(boostCount = 3).actorServiceText(jana))
        assertEquals(UiText(Res.plurals.service_you_boost_count, 3, 3), MessageChatBoost(boostCount = 3).actorServiceText(me))
    }

    @Test
    fun skore() {
        assertEquals(
            UiText(Res.string.service_actor_game_score, "Jana", 1500.grouped()),
            MessageGameScore(gameMessageId = 1L, gameId = 2L, score = 1500).actorServiceText(jana),
        )
    }

    @Test
    fun kontaktNaTelegramu() {
        assertEquals(UiText(Res.string.service_actor_contact_joined, "Jana"), MessageContactRegistered().actorServiceText(jana))
        // Vlastní registrace k nám nechodí; kdyby přišla, zůstane bezejmenná věta.
        assertNull(MessageContactRegistered().actorServiceText(me))
    }

    @Test
    fun pripnutiMaVlastniFunkci() {
        assertFalse(MessagePinMessage(messageId = 1L).hasActorServiceText)
        assertNull(MessagePinMessage(messageId = 1L).actorServiceText(jana))
    }

    @Test
    fun navrhDataNarozeni() {
        val content = dev.g000sha256.tdl.dto.MessageSuggestBirthdate(dev.g000sha256.tdl.dto.Birthdate(day = 12, month = 10, year = 1990))
        val date = UiText(Res.string.date_day_month_year, 12, UiText(Res.string.month_10), 1990)
        assertEquals(UiText(Res.string.service_actor_suggested_birthdate, "Jana", date), content.actorServiceText(jana))
        assertEquals(UiText(Res.string.service_you_suggested_birthdate, date), content.actorServiceText(me))
        // Bez jména zůstane datum v obecné větě.
        assertEquals(UiText(Res.string.service_suggested_birthdate, date), content.serviceText(isChannelPost = false))
        // Bez roku jen den a měsíc.
        assertEquals(
            UiText(Res.string.service_suggested_birthdate, UiText(Res.string.date_day_month, 1, UiText(Res.string.month_2))),
            dev.g000sha256.tdl.dto.MessageSuggestBirthdate(dev.g000sha256.tdl.dto.Birthdate(day = 1, month = 2, year = 0)).serviceText(false),
        )
    }
}
