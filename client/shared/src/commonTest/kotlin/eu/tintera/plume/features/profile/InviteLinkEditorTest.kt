package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.InviteLink
import eu.tintera.plume.core.telegram.InviteLinkDraft
import eu.tintera.plume.core.telegram.InviteLinkStatus
import eu.tintera.plume.core.telegram.inviteLinkStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteLinkEditorTest {

    private val now = 1_800_000_000L

    private fun link(
        expiresAt: Int = 0,
        memberLimit: Int = 0,
        memberCount: Int = 0,
        isRevoked: Boolean = false,
        createsJoinRequest: Boolean = false,
    ) = InviteLink(
        url = "https://t.me/+abc",
        name = "Web",
        isPrimary = false,
        isRevoked = isRevoked,
        createdAt = 0,
        expiresAt = expiresAt,
        memberLimit = memberLimit,
        memberCount = memberCount,
        pendingRequests = 0,
        createsJoinRequest = createsJoinRequest,
    )

    @Test
    fun zrusenyMaPrednostPredVyprsenim() {
        assertEquals(InviteLinkStatus.Revoked, inviteLinkStatus(link(expiresAt = 1, isRevoked = true), now))
    }

    @Test
    fun vyprselyANaplneny() {
        assertEquals(InviteLinkStatus.Expired, inviteLinkStatus(link(expiresAt = (now - 1).toInt()), now))
        assertEquals(InviteLinkStatus.Full, inviteLinkStatus(link(memberLimit = 10, memberCount = 10), now))
        // Bez limitu a bez konce platí napořád.
        assertEquals(InviteLinkStatus.Active, inviteLinkStatus(link(memberCount = 500), now))
        assertEquals(InviteLinkStatus.Active, inviteLinkStatus(link(expiresAt = (now + 60).toInt(), memberLimit = 10, memberCount = 3), now))
    }

    @Test
    fun seSchvalovanimBezLimitu() {
        // TDLib limit u odkazu se žádostmi odmítne — koncept ho musí zahodit sám.
        val draft = InviteLinkDraft(memberLimit = 10, createsJoinRequest = true).normalized()
        assertEquals(0, draft.memberLimit)
    }

    @Test
    fun jmenoSeOrizne() {
        assertEquals("Web", InviteLinkDraft(name = "  Web  ").normalized().name)
        assertEquals(32, InviteLinkDraft(name = "x".repeat(40)).normalized().name.length)
    }

    @Test
    fun platnostSePocitaOdUlozeni() {
        val editor = InviteLinkEditor(expiry = InviteExpiry.Day)
        assertEquals((now + 86_400).toInt(), editor.toDraft(now).expiresAt)
        assertEquals(0, editor.copy(expiry = InviteExpiry.Never).toDraft(now).expiresAt)
    }

    @Test
    fun upravaPonechaKonecKteryOdkazMa() {
        val editor = InviteLinkEditor.of(link(expiresAt = 1_900_000_000))
        assertEquals(InviteExpiry.Keep, editor.expiry)
        assertEquals(1_900_000_000, editor.toDraft(now).expiresAt)
        assertTrue(InviteExpiry.Keep in editor.expiryOptions)
        // Bez konce není co ponechat.
        assertFalse(InviteExpiry.Keep in InviteLinkEditor.of(link()).expiryOptions)
    }

    @Test
    fun neobvyklyLimitSePridaDoVoleb() {
        assertEquals(listOf(0, 1, 10, 100), InviteLinkEditor().limitOptions)
        assertEquals(listOf(0, 1, 10, 25, 100), InviteLinkEditor.of(link(memberLimit = 25)).limitOptions)
    }
}
