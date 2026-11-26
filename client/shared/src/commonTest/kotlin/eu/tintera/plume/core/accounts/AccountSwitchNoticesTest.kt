package eu.tintera.plume.core.accounts

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class AccountSwitchNoticesTest {

    private val time = TestTimeSource()
    private val notices = AccountSwitchNotices(time)
    private val second = AccountSlot("fs2gc9")

    @Test
    fun noticeIsShownOnceForItsChat() {
        notices.switchedFor(second, chatId = 42)
        assertTrue(notices.consume(second, chatId = 42))
        assertFalse(notices.consume(second, chatId = 42))
    }

    @Test
    fun otherChatOrAccountLeavesNoticeWaiting() {
        notices.switchedFor(second, chatId = 42)
        assertFalse(notices.consume(second, chatId = 7))
        assertFalse(notices.consume(AccountSlot.Primary, chatId = 42))
        assertTrue(notices.consume(second, chatId = 42))
    }

    @Test
    fun staleNoticeExpires() {
        notices.switchedFor(second, chatId = 42)
        time += 30.seconds
        assertFalse(notices.consume(second, chatId = 42))
        // Propadlé ohlášení je pryč, další otevření téhož chatu nic nehlásí.
        assertFalse(notices.consume(second, chatId = 42))
    }
}
