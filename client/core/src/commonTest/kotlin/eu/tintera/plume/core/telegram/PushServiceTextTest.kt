package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.PushMessageContentChatAddMembers
import dev.g000sha256.tdl.dto.PushMessageContentChatChangeTitle
import dev.g000sha256.tdl.dto.PushMessageContentChatDeleteMember
import dev.g000sha256.tdl.dto.PushMessageContentGameScore
import dev.g000sha256.tdl.dto.PushMessageContentInviteVideoChatParticipants
import dev.g000sha256.tdl.dto.PushMessageContentMessageForwards
import dev.g000sha256.tdl.dto.PushMessageContentPollOptionAdded
import dev.g000sha256.tdl.dto.PushMessageContentRecurringPayment
import dev.g000sha256.tdl.dto.PushMessageContentScreenshotTaken
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_pinned
import eu.tintera.plume.resources.push_added_you
import eu.tintera.plume.resources.push_forwarded_messages
import eu.tintera.plume.resources.push_game_score
import eu.tintera.plume.resources.push_member_added
import eu.tintera.plume.resources.push_member_left
import eu.tintera.plume.resources.push_member_removed
import eu.tintera.plume.resources.push_member_returned
import eu.tintera.plume.resources.push_recurring_payment
import eu.tintera.plume.resources.push_removed_you
import eu.tintera.plume.resources.push_screenshot
import eu.tintera.plume.resources.push_title_changed
import eu.tintera.plume.resources.push_video_chat_invited_you
import eu.tintera.plume.resources.service_poll_option_added_named
import eu.tintera.plume.resources.service_video_chat_invite
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Servisní události z pushe říkají, co se stalo — dřív byly všechny „Nová zpráva". Věty jsou
 * bez podmětu, jméno odesílatele nese řádek notifikace.
 */
class PushServiceTextTest {

    @Test
    fun zmenaNazvu() {
        assertEquals(UiText(Res.string.push_title_changed, "Výlet"), PushMessageContentChatChangeTitle("Výlet").previewText())
    }

    @Test
    fun clenove() {
        assertEquals(UiText(Res.string.push_member_added, "Bob"), PushMessageContentChatAddMembers("Bob", isCurrentUser = false, isReturned = false).previewText())
        assertEquals(UiText(Res.string.push_added_you), PushMessageContentChatAddMembers("Ty", isCurrentUser = true, isReturned = false).previewText())
        assertEquals(UiText(Res.string.push_member_returned), PushMessageContentChatAddMembers("Bob", isCurrentUser = false, isReturned = true).previewText())
        assertEquals(UiText(Res.string.push_member_left), PushMessageContentChatDeleteMember("Bob", isCurrentUser = false, isLeft = true).previewText())
        assertEquals(UiText(Res.string.push_removed_you), PushMessageContentChatDeleteMember("Ty", isCurrentUser = true, isLeft = false).previewText())
        assertEquals(UiText(Res.string.push_member_removed, "Bob"), PushMessageContentChatDeleteMember("Bob", isCurrentUser = false, isLeft = false).previewText())
    }

    @Test
    fun preposlaneZpravySPoctem() {
        assertEquals(UiText(Res.plurals.push_forwarded_messages, 5, 5), PushMessageContentMessageForwards(totalCount = 5).previewText())
    }

    @Test
    fun videochat() {
        assertEquals(UiText(Res.string.push_video_chat_invited_you), PushMessageContentInviteVideoChatParticipants(isCurrentUser = true).previewText())
        assertEquals(UiText(Res.string.service_video_chat_invite), PushMessageContentInviteVideoChatParticipants(isCurrentUser = false).previewText())
    }

    @Test
    fun ostatni() {
        assertEquals(UiText(Res.string.push_screenshot), PushMessageContentScreenshotTaken().previewText())
        assertEquals(UiText(Res.string.push_recurring_payment, "$5"), PushMessageContentRecurringPayment(amount = "$5").previewText())
        assertEquals(UiText(Res.string.service_poll_option_added_named, "Pizza"), PushMessageContentPollOptionAdded(text = "Pizza").previewText())
    }

    @Test
    fun pripnuteSkoreSeTvariJakoPripnuti() {
        val score = UiText(Res.string.push_game_score, 1500.grouped(), "Had")
        assertEquals(score, PushMessageContentGameScore(title = "Had", score = 1500, isPinned = false).previewText())
        assertEquals(
            UiText(Res.string.notification_pinned, score),
            PushMessageContentGameScore(title = "Had", score = 1500, isPinned = true).previewText(),
        )
    }
}
