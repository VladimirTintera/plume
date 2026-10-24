package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationChannelTest {

    @Test
    fun druhChatuUrcujeKanal() {
        assertEquals(NotificationChannelKind.Private, notificationChannelFor(isMention = false, kind = ChatKind.Private))
        assertEquals(NotificationChannelKind.Groups, notificationChannelFor(isMention = false, kind = ChatKind.Group))
        assertEquals(NotificationChannelKind.Channels, notificationChannelFor(isMention = false, kind = ChatKind.Channel))
    }

    /** Zmínka ve ztišené skupině musí zvonit — proto vlastní kanál bez ohledu na druh chatu. */
    @Test
    fun zminkaMaPrednostPredDruhemChatu() {
        ChatKind.entries.forEach { kind ->
            assertEquals(NotificationChannelKind.Mentions, notificationChannelFor(isMention = true, kind = kind))
        }
    }
}
