package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageInviteVideoChatParticipants
import dev.g000sha256.tdl.dto.MessagePaymentSuccessful
import dev.g000sha256.tdl.dto.MessageVideoChatScheduled
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.date_day_month_year
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.month_2
import eu.tintera.plume.resources.product_price
import eu.tintera.plume.resources.product_price_stars
import eu.tintera.plume.resources.service_live_stream_scheduled_at
import eu.tintera.plume.resources.service_names_two
import eu.tintera.plume.resources.service_payment
import eu.tintera.plume.resources.service_payment_amount
import eu.tintera.plume.resources.service_payment_amount_for
import eu.tintera.plume.resources.service_payment_recurring
import eu.tintera.plume.resources.service_video_chat_invited
import eu.tintera.plume.resources.service_video_chat_invited_you
import eu.tintera.plume.resources.service_video_chat_scheduled
import eu.tintera.plume.resources.service_you_video_chat_invited
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Naplánovaný videochat s časem, pozvaní do videochatu jménem a platba s částkou. */
class ServiceVideoChatPaymentTest {

    @Test
    fun naplanovanyVideochatMaDatumACas() {
        // Rok jiný než letošek, ať je v datu rok — a test nezávisí na tom, kdy běží.
        val start = LocalDateTime(2001, 2, 5, 18, 30).toInstant(TimeZone.currentSystemDefault()).epochSeconds.toInt()
        assertEquals(
            UiText(
                Res.string.service_live_stream_scheduled_at,
                UiText(Res.string.date_day_month_year, 5, UiText(Res.string.month_2), 2001),
                start.asTimeOfDay(),
            ),
            MessageVideoChatScheduled(groupCallId = 1, startDate = start).serviceText(isChannelPost = true),
        )
        assertEquals(
            UiText(Res.string.service_video_chat_scheduled),
            MessageVideoChatScheduled(groupCallId = 1, startDate = 0).serviceText(isChannelPost = false),
        )
    }

    @Test
    fun pozvaniDoVideochatu() {
        val me = ServicePerson(name = "", isMe = true)
        val people = mapOf(1L to ServicePerson("Alice"), 2L to ServicePerson("Bob"), 3L to me)
        assertEquals(
            UiText(Res.string.service_video_chat_invited, "Alice", UiText(Res.string.service_names_two, UiText.Raw("Bob"), UiText.Raw("Alice"))),
            invite(2L, 1L).memberServiceText(actorId = 1L, people = people),
        )
        assertEquals(UiText(Res.string.service_video_chat_invited_you, "Alice"), invite(3L).memberServiceText(1L, people))
        assertEquals(UiText(Res.string.service_you_video_chat_invited, UiText.Raw("Bob")), invite(2L).memberServiceText(3L, people))
    }

    @Test
    fun platbaSCastkou() {
        val eur = UiText(Res.string.product_price, UiText(Res.string.decimal_number, "12", "50"), "EUR")
        assertEquals(UiText(Res.string.service_payment_amount, eur), payment(1250, "EUR", name = "").serviceText(false))
        assertEquals(UiText(Res.string.service_payment_amount_for, eur, "Káva"), payment(1250, "EUR", name = "Káva").serviceText(false))
        assertEquals(
            UiText(Res.string.service_payment_recurring, UiText(Res.string.service_payment_amount, eur)),
            payment(1250, "EUR", name = "", recurring = true).serviceText(false),
        )
        assertEquals(
            UiText(Res.string.service_payment_amount, UiText(Res.plurals.product_price_stars, 150, UiText.Raw("150"))),
            payment(150, "XTR", name = "").serviceText(false),
        )
        assertEquals(UiText(Res.string.service_payment), payment(0, "EUR", name = "").serviceText(false))
    }

    private fun invite(vararg ids: Long) = MessageInviteVideoChatParticipants(groupCallId = 1, userIds = ids)

    private fun payment(amount: Long, currency: String, name: String, recurring: Boolean = false) = MessagePaymentSuccessful(
        invoiceChatId = 0L,
        invoiceMessageId = 0L,
        currency = currency,
        totalAmount = amount,
        subscriptionUntilDate = 0,
        isRecurring = recurring,
        isFirstRecurring = false,
        invoiceName = name,
    )
}
