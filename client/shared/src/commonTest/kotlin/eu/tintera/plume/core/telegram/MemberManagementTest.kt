package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class MemberManagementTest {

    private val owner = OwnMemberStatus.Creator(isMember = true)
    private val fullAdmin = OwnMemberStatus.Admin(
        canPostMessages = true,
        canChangeInfo = true,
        canRestrictMembers = true,
        canPromoteMembers = true,
        canManageTags = true,
    )
    private val member = TargetMember(role = MemberRole.Member)

    @Test
    fun samSeSebouNicNedela() {
        val self = TargetMember(role = MemberRole.Member, isSelf = true)
        assertEquals(MemberActions(), memberActions(owner, self, MemberGroupKind.Supergroup))
        // Ani vlastník sám sobě nepředá vlastnictví.
        val ownerSelf = TargetMember(role = MemberRole.Owner, isSelf = true)
        assertEquals(MemberActions(), memberActions(owner, ownerSelf, MemberGroupKind.Supergroup))
    }

    @Test
    fun vlastnikaNemeniNikdo() {
        val target = TargetMember(role = MemberRole.Owner)
        assertEquals(MemberActions(), memberActions(owner, target, MemberGroupKind.Supergroup))
        assertEquals(MemberActions(), memberActions(fullAdmin, target, MemberGroupKind.Supergroup))
    }

    @Test
    fun vlastnikSmiVSupergroupeVsechno() {
        val actions = memberActions(owner, member, MemberGroupKind.Supergroup)
        assertEquals(
            MemberActions(
                canPromote = true,
                canRestrict = true,
                canBan = true,
                canEditTag = true,
                canTransferOwnership = true,
            ),
            actions,
        )
    }

    /** „Can't restrict users in basic group chats" a „Can't change owner in basic group chats". */
    @Test
    fun vZakladniSkupineSeNeomezujeAniNepredavaVlastnictvi() {
        val actions = memberActions(owner, member, MemberGroupKind.BasicGroup)
        assertFalse(actions.canRestrict)
        assertFalse(actions.canTransferOwnership)
        // Vyhodit ze základní skupiny jde — ban uživatele tam TDLib dovolí.
        assertTrue(actions.canBan)
        assertTrue(actions.canPromote)
    }

    /** V kanálu nikdo nepíše, takže není co omezovat; štítky tam taky nejsou. */
    @Test
    fun vKanaluSeNeomezujeAniNestitkuje() {
        val actions = memberActions(owner, member, MemberGroupKind.Channel)
        assertFalse(actions.canRestrict)
        assertFalse(actions.canEditTag)
        assertTrue(actions.canBan)
        assertTrue(actions.canTransferOwnership)
    }

    @Test
    fun vlastnictviNedostaneBot() {
        val bot = TargetMember(role = MemberRole.Member, isBot = true)
        assertFalse(memberActions(owner, bot, MemberGroupKind.Supergroup).canTransferOwnership)
        // Povýšit bota na správce ale jde.
        assertTrue(memberActions(owner, bot, MemberGroupKind.Supergroup).canPromote)
    }

    @Test
    fun spravceSmiJenToCoMaVPravech() {
        val onlyRestrict = OwnMemberStatus.Admin(canPostMessages = true, canRestrictMembers = true)
        val actions = memberActions(onlyRestrict, member, MemberGroupKind.Supergroup)
        assertTrue(actions.canRestrict)
        assertTrue(actions.canBan)
        assertFalse(actions.canPromote)
        assertFalse(actions.canEditTag)

        val onlyPromote = OwnMemberStatus.Admin(canPostMessages = true, canPromoteMembers = true)
        val promoting = memberActions(onlyPromote, member, MemberGroupKind.Supergroup)
        assertTrue(promoting.canPromote)
        assertFalse(promoting.canBan)
    }

    /** Správce nikdy nepředává vlastnictví — to smí jen vlastník. */
    @Test
    fun spravceVlastnictviNepredava() {
        assertFalse(memberActions(fullAdmin, member, MemberGroupKind.Supergroup).canTransferOwnership)
    }

    /** „Can't edit chat administrators": cizího správce mění jen vlastník. */
    @Test
    fun cizihoSpravceMeniJenVlastnik() {
        val foreign = TargetMember(role = MemberRole.Admin, canBeEdited = false)
        val mine = TargetMember(role = MemberRole.Admin, canBeEdited = true)

        assertFalse(memberActions(fullAdmin, foreign, MemberGroupKind.Supergroup).any)
        assertTrue(memberActions(fullAdmin, mine, MemberGroupKind.Supergroup).canPromote)
        assertTrue(memberActions(fullAdmin, mine, MemberGroupKind.Supergroup).canBan)
        // Vlastníka `canBeEdited` neomezuje.
        assertTrue(memberActions(owner, foreign, MemberGroupKind.Supergroup).canPromote)
    }

    @Test
    fun beznyClenNespravujeNikoho() {
        assertEquals(MemberActions(), memberActions(OwnMemberStatus.Member, member, MemberGroupKind.Supergroup))
        assertEquals(
            MemberActions(),
            memberActions(
                OwnMemberStatus.Restricted(isMember = true, canSendMessages = true, content = ContentAccess.All),
                member,
                MemberGroupKind.Supergroup,
            ),
        )
        assertEquals(MemberActions(), memberActions(OwnMemberStatus.Left, member, MemberGroupKind.Supergroup))
        assertEquals(MemberActions(), memberActions(OwnMemberStatus.Banned, member, MemberGroupKind.Supergroup))
        // Soukromý chat: není koho spravovat.
        assertEquals(MemberActions(), memberActions(null, member, MemberGroupKind.Supergroup))
    }

    /** Vlastník, který ze skupiny odešel, ji dál nespravuje — stejně jako u psaní a úprav. */
    @Test
    fun vlastnikMimoSkupinuNespravuje() {
        val left = OwnMemberStatus.Creator(isMember = false)
        assertEquals(MemberActions(), memberActions(left, member, MemberGroupKind.Supergroup))
    }

    // --- Doba omezení ---

    @Test
    fun bezDobyJeToNavzdy() {
        assertEquals(FOREVER, untilDate(duration = null, nowEpochSeconds = 1_800_000_000))
    }

    @Test
    fun dobaSePricitaKSoucasnemuCasu() {
        val now = 1_800_000_000L
        assertEquals((now + 3600).toInt(), untilDate(1.hours, now))
        assertEquals((now + 7 * 86_400).toInt(), untilDate(7.days, now))
    }

    /** Přetečení `Int` by z konce omezení udělalo čas v minulosti; bereme ho jako „navždy". */
    @Test
    fun prilisDalekyKonecJeNavzdy() {
        assertEquals(FOREVER, untilDate(365.days, nowEpochSeconds = Int.MAX_VALUE.toLong()))
    }

    // --- Práva správce ---

    @Test
    fun prazdnaPravaZnamenajiSesazeni() {
        assertFalse(AdminRights().any)
        assertTrue(AdminRights(pinMessages = true).any)
        assertTrue(AdminRights.Default.any)
    }

    @Test
    fun ponechanyKonecOmezeniSePosilaPresne() {
        val now = 1_800_000_000L
        // Přesně původní konec, ne „teď + zbytek" — to by konec posunulo o čas na obrazovce.
        assertEquals((now + 86_000).toInt(), keptUntilDate((now + 86_000).toInt(), now))
    }

    @Test
    fun ponechanyKonecTesneDoKonceNeniNavzdy() {
        val now = 1_800_000_000L
        // Do konce zbývá 10 s: TDLib by to vzal jako navždy, posune se těsně za mez.
        val kept = keptUntilDate((now + 10).toInt(), now)
        assertEquals((now + MIN_RESTRICTION_SECONDS + 1).toInt(), kept)
        assertTrue(kept != FOREVER)
    }
}
