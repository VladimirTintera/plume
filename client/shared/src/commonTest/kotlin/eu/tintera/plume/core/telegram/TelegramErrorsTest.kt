package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_failed
import eu.tintera.plume.resources.error_ai_compose_premium
import eu.tintera.plume.resources.error_call_outdated
import eu.tintera.plume.resources.error_call_privacy
import eu.tintera.plume.resources.error_flood_wait_minutes
import eu.tintera.plume.resources.error_flood_wait_seconds
import eu.tintera.plume.resources.error_phone_code_invalid
import eu.tintera.plume.resources.error_pin_limit
import eu.tintera.plume.resources.error_channels_too_much
import eu.tintera.plume.resources.error_verification_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramErrorsTest {

    private val fallback = Res.string.chats_action_failed

    @Test
    fun `neznama hlaska od TDLib se uzivateli neukaze`() {
        assertEquals(UiText(fallback), IllegalStateException("CHANNEL_PRIVATE").userMessage(fallback))
    }

    @Test
    fun `vyjimka bez hlasky spadne na zalozni vetu`() {
        assertEquals(UiText(fallback), IllegalStateException().userMessage(fallback))
    }

    @Test
    fun `konstanta od serveru dostane vlastni vetu`() {
        assertEquals(
            UiText(Res.string.error_phone_code_invalid),
            IllegalStateException("PHONE_CODE_INVALID").userMessage(fallback),
        )
    }

    @Test
    fun `veta od TDLib dostane vlastni vetu`() {
        assertEquals(
            UiText(Res.string.error_pin_limit),
            IllegalStateException("The maximum number of pinned chats exceeded").userMessage(fallback),
        )
    }

    @Test
    fun `odmitnute overeni aplikace rekne proc a kudy dal`() {
        // Plume ověření odmítá samo (`declineVerification`), TDLib pak dotaz ukončí touhle konstantou.
        assertEquals(
            UiText(Res.string.error_verification_failed),
            IllegalStateException("VERIFICATION_FAILED").userMessage(fallback),
        )
    }

    @Test
    fun `vycerpane ai upravy odkazou na premium`() {
        // `composeTextWithAi` / `fixTextWithAi` bez Premium po vyčerpání bezplatných dotazů.
        assertEquals(
            UiText(Res.string.error_ai_compose_premium),
            IllegalStateException("AICOMPOSE_FLOOD_PREMIUM").userMessage(fallback),
        )
    }

    @Test
    fun `limit skupin a kanalu se pozna a ma vlastni vetu`() {
        val limit = IllegalStateException("CHANNELS_TOO_MUCH")
        assertTrue(limit.isChannelLimit())
        assertFalse(IllegalStateException("CHANNEL_PRIVATE").isChannelLimit())
        assertEquals(UiText(Res.string.error_channels_too_much), limit.userMessage(fallback))
    }

    @Test
    fun `tyz limit prichazi i jako konstanta od serveru`() {
        assertEquals(
            IllegalStateException("The maximum number of pinned chats exceeded").userMessage(fallback),
            IllegalStateException("PINNED_DIALOGS_TOO_MUCH").userMessage(fallback),
        )
    }

    @Test
    fun `flood wait nese cas cekani jako argument`() {
        assertEquals(
            UiText(Res.string.error_flood_wait_seconds, 23),
            IllegalStateException("FLOOD_WAIT_23").userMessage(fallback),
        )
    }

    @Test
    fun `flood wait z omezovace TDLib se cte taky`() {
        assertEquals(
            UiText(Res.string.error_flood_wait_seconds, 5),
            IllegalStateException("Too Many Requests: retry after 5").userMessage(fallback),
        )
    }

    @Test
    fun `dlouhe cekani se rika v minutach a zaokrouhluje nahoru`() {
        assertEquals(
            UiText(Res.string.error_flood_wait_minutes, 4),
            IllegalStateException("FLOOD_WAIT_181").userMessage(fallback),
        )
    }

    @Test
    fun `flood wait bez cisla nesmi spadnout`() {
        assertEquals(UiText(fallback), IllegalStateException("FLOOD_WAIT_").userMessage(fallback))
    }

    @Test
    fun `hovor zablokovany protejskem se tvari jako soukromi`() {
        // Zablokování by se nemělo prozradit; oficiální klienti říkají totéž.
        assertEquals(UiText(Res.string.error_call_privacy), errorText("USER_PRIVACY_RESTRICTED", fallback))
        assertEquals(UiText(Res.string.error_call_privacy), errorText("USER_IS_BLOCKED", fallback))
    }

    @Test
    fun `protejsek se starou aplikaci ma vlastni vetu`() {
        assertEquals(UiText(Res.string.error_call_outdated), errorText("PARTICIPANT_VERSION_OUTDATED", fallback))
    }

    @Test
    fun `omezeni poctu hovoru z CallStateError rika minuty`() {
        // Tak přišla chyba hovoru po desítkách zkušebních hovorů (`CallStateError`, kód 429).
        assertEquals(UiText(Res.string.error_flood_wait_minutes, 27), errorText("Too Many Requests: retry after 1579", fallback))
    }
}
