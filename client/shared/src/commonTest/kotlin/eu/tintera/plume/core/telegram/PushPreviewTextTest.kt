package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.PushMessageContentGameScore
import dev.g000sha256.tdl.dto.PushMessageContentHidden
import dev.g000sha256.tdl.dto.PushMessageContentText
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_new_message
import eu.tintera.plume.resources.notification_pinned
import eu.tintera.plume.resources.service_message_pinned
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Připnutí chodí v pushi jako týž typ obsahu s `isPinned` — notifikace ho nesmí ukázat jako
 * novou zprávu.
 */
class PushPreviewTextTest {

    @Test
    fun pripnutyTextNeniNovaZprava() {
        assertEquals(
            UiText(Res.string.notification_pinned, UiText.Raw("Sraz v 8")),
            PushMessageContentText(text = "Sraz v 8", isPinned = true).previewText(),
        )
        assertEquals(UiText.Raw("Sraz v 8"), PushMessageContentText(text = "Sraz v 8", isPinned = false).previewText())
    }

    @Test
    fun pripnutiBezPopisuJeHolaVeta() {
        // Skrytý obsah ani skóre popsat neumíme; „Připnuto: Nová zpráva" by nedávalo smysl.
        assertEquals(UiText(Res.string.service_message_pinned), PushMessageContentHidden(isPinned = true).previewText())
        assertEquals(
            UiText(Res.string.service_message_pinned),
            PushMessageContentGameScore(title = "Hra", score = 10, isPinned = true).previewText(),
        )
        assertEquals(UiText(Res.string.notification_new_message), PushMessageContentHidden(isPinned = false).previewText())
    }
}
