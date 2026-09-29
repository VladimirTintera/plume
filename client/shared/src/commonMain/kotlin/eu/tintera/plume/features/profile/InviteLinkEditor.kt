package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.InviteLink
import eu.tintera.plume.core.telegram.InviteLinkDraft

/** Platnost odkazu, jak se vybírá ve formuláři. [Keep] = ponechat konec, který odkaz už má. */
enum class InviteExpiry(val seconds: Long) {
    Keep(-1),
    Never(0),
    Hour(3_600),
    Day(86_400),
    Week(604_800),
}

/**
 * Formulář odkazu: nový ([url] `null`), nebo úprava existujícího. Platnost a limit jsou volby
 * z nabídky, ne čísla — na koncept pro server je převede až [toDraft] v okamžiku uložení, aby
 * „za hodinu" platilo od uložení, ne od otevření formuláře.
 */
data class InviteLinkEditor(
    val url: String? = null,
    val name: String = "",
    val expiry: InviteExpiry = InviteExpiry.Never,
    /** Konec, který odkaz už má (unix, s); 0 = žádný. Jen u úpravy, pro [InviteExpiry.Keep]. */
    val currentExpiresAt: Int = 0,
    /** 0 = bez omezení. */
    val memberLimit: Int = 0,
    val createsJoinRequest: Boolean = false,
) {
    val isNew: Boolean get() = url == null

    /** Limity k výběru; neobvyklý limit, který odkaz už má (nastavený jinde), se přidá. */
    val limitOptions: List<Int>
        get() = if (memberLimit in LIMIT_OPTIONS) LIMIT_OPTIONS else (LIMIT_OPTIONS + memberLimit).sorted()

    /** Volby platnosti; „Ponechat" jen u odkazu, který konec má. */
    val expiryOptions: List<InviteExpiry>
        get() = InviteExpiry.entries.filter { it != InviteExpiry.Keep || currentExpiresAt != 0 }

    fun toDraft(now: Long): InviteLinkDraft = InviteLinkDraft(
        name = name,
        expiresAt = when (expiry) {
            InviteExpiry.Keep -> currentExpiresAt
            InviteExpiry.Never -> 0
            else -> (now + expiry.seconds).toInt()
        },
        memberLimit = memberLimit,
        createsJoinRequest = createsJoinRequest,
    ).normalized()

    companion object {
        fun of(link: InviteLink): InviteLinkEditor = InviteLinkEditor(
            url = link.url,
            name = link.name,
            expiry = if (link.expiresAt == 0) InviteExpiry.Never else InviteExpiry.Keep,
            currentExpiresAt = link.expiresAt,
            memberLimit = link.memberLimit,
            createsJoinRequest = link.createsJoinRequest,
        )
    }
}

/** 0 = bez omezení. Víc voleb se na telefon do jedné řady nevejde. */
private val LIMIT_OPTIONS = listOf(0, 1, 10, 100)

/** Adresa bez schématu — v řádku je `t.me/+…` čitelnější a `https://` nic neříká. */
fun InviteLink.shortUrl(): String = url.removePrefix("https://").removePrefix("http://")
