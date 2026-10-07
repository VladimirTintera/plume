package eu.tintera.plume.core.telegram

import kotlin.time.Duration

/**
 * Správa jednoho člena skupiny: povýšení na správce, omezení, vyhození a převod vlastnictví.
 *
 * Doménové typy a **pravidla jako čisté funkce** ([memberActions], [untilDate]) — stejně jako
 * [writeAccess] a [chatEditRights]. Kombinací postavení, druhu chatu a role protějšku je dost
 * a každá se na zařízení ověřuje jiným účtem, takže uvnitř repository by se neotestovaly.
 */

/** Druh chatu z pohledu správy členů. TDLib u každého dovolí něco jiného, viz [memberActions]. */
enum class MemberGroupKind {
    /** Základní skupina. Omezovat ani převádět vlastnictví v ní nejde, vyhodit ano. */
    BasicGroup,
    Supergroup,
    Channel,
}

/**
 * Co uživatel s tímhle členem smí. Co nesmí, se v nabídce vůbec neukáže — položka, která skončí
 * chybou od serveru, je horší než položka, která tam není.
 */
data class MemberActions(
    /** Povýšit na správce, upravit jeho práva, nebo ho ze správcovství sesadit. */
    val canPromote: Boolean = false,
    /** Omezit, co smí posílat, nebo omezení zrušit. */
    val canRestrict: Boolean = false,
    /** Vyhodit ze skupiny. */
    val canBan: Boolean = false,
    /** Změnit vlastní štítek člena (`ChatMember.tag`). */
    val canEditTag: Boolean = false,
    /** Předat vlastnictví skupiny nebo kanálu. Nevratné a chce heslo účtu. */
    val canTransferOwnership: Boolean = false,
) {
    /** Nabídka má co ukázat; jinak je z členů pořád jen seznam. */
    val any: Boolean get() = canPromote || canRestrict || canBan || canEditTag || canTransferOwnership
}

/**
 * Člen, kterého se akce týká — zúžený na to, co pravidlo potřebuje.
 *
 * [canBeEdited] je `ChatMemberStatusAdministrator.canBeEdited` od serveru: správce smí měnit
 * jen toho správce, kterého sám povýšil. Bez něj by se nabídla položka, po které přijde
 * „Can't edit chat administrators" (ověřeno v `libtdjsonjava.so`).
 */
data class TargetMember(
    val role: MemberRole,
    val canBeEdited: Boolean = false,
    val isSelf: Boolean = false,
    val isBot: Boolean = false,
)

/**
 * Práva správce, zúžená na ta, která mají v Telegramu vlastní význam a uživatel jim rozumí.
 *
 * Zapisují se **celým objektem** jako [GroupPermissions], takže co aplikace nenabízí (videohovory,
 * stories, témata, přímé zprávy kanálu), se u stávajícího správce přenese z jeho současných práv —
 * nastavit mu to mohl jiný klient a tiše mu to sebrat by bylo horší než to nenabízet.
 *
 * Skupina a kanál ukazují každý jinou podmnožinu: v kanálu nikdo nepíše, takže se nepřipíná
 * a neomezuje, zato se publikuje a upravují cizí příspěvky.
 */
data class AdminRights(
    /** Měnit název, popis a fotku. Skupina i kanál. */
    val changeInfo: Boolean = false,
    /** Publikovat příspěvky. Jen kanál. */
    val postMessages: Boolean = false,
    /** Upravovat cizí příspěvky. Jen kanál. */
    val editMessages: Boolean = false,
    /** Mazat cizí zprávy. Skupina i kanál. */
    val deleteMessages: Boolean = false,
    /** Omezovat a vyhazovat členy. Jen skupina. */
    val restrictMembers: Boolean = false,
    /** Zvát další lidi (v kanálu přidávat odběratele). */
    val inviteUsers: Boolean = false,
    /** Připínat zprávy. Jen skupina. */
    val pinMessages: Boolean = false,
    /** Jmenovat další správce — jen v rozsahu vlastních práv, to hlídá server. */
    val promoteMembers: Boolean = false,
    /** Vystupovat ve skupině anonymně, jménem skupiny. Jen skupina. */
    val anonymous: Boolean = false,
) {
    /**
     * Správce má aspoň jedno právo. Prázdná práva znamenají sesazení na běžného člena — tak to
     * bere i TDLib, takže „Sesadit" není vlastní volání, jen uložení bez jediného práva.
     */
    val any: Boolean
        get() = changeInfo || postMessages || editMessages || deleteMessages ||
            restrictMembers || inviteUsers || pinMessages || promoteMembers || anonymous

    companion object {
        /** Co dostane nově povýšený správce, dokud si uživatel nevybere: běžná moderátorská sada. */
        val Default = AdminRights(
            changeInfo = true,
            postMessages = true,
            editMessages = true,
            deleteMessages = true,
            restrictMembers = true,
            inviteUsers = true,
            pinMessages = true,
        )
    }
}

/** Jeden člen otevřený ke správě: co je zač, jak na tom teď je a co s ním jde dělat. */
data class MemberDetail(
    val chatId: Long,
    val userId: Long,
    val name: String,
    val avatarFileId: Int?,
    val isBot: Boolean,
    val role: MemberRole,
    val kind: MemberGroupKind,
    /** Vlastní štítek člena; prázdný od serveru → `null`. */
    val tag: String?,
    /** Práva správce; u toho, kdo správce není, výchozí nabídka pro povýšení. */
    val adminRights: AdminRights,
    /** Co člen smí posílat — jeho vlastní práva, u neomezeného výchozí práva skupiny. */
    val permissions: GroupPermissions,
    /** Člen je omezený (má vlastní práva místo výchozích). */
    val isRestricted: Boolean,
    /** Do kdy omezení platí (unix); `null` = navždy nebo není omezený. */
    val restrictedUntil: Int?,
    val actions: MemberActions,
)

/** Výsledek dotazu, jestli se dá předat vlastnictví — TDLib ho vrací ještě před zadáním hesla. */
sealed interface OwnershipTransferCheck {
    /** Vlastnictví jde předat; zbývá zadat heslo účtu. */
    data object Ready : OwnershipTransferCheck

    /** Účet nemá zapnuté dvoufázové ověření. Bez něj Telegram vlastnictví předat nedovolí. */
    data object PasswordNeeded : OwnershipTransferCheck

    /** Heslo nebo relace jsou čerstvé; Telegram čeká, než se dá předávat. [retryAfter] je v sekundách. */
    data class TooFresh(val retryAfter: Int) : OwnershipTransferCheck
}

/**
 * Pravidlo, co uživatel s členem smí.
 *
 * - **Sám se sebou nic** — „Can't promote or demote self" i „Can't restrict self" jsou vlastní
 *   kontroly TDLib, takže by na ně došlo ještě před dotazem na server.
 * - **Vlastníka nemění nikdo.** Jediná cesta je [canTransferOwnership] u toho, kdo vlastníkem je.
 * - **Vlastník smí všechno ostatní.** Správce jen to, co má ve svých právech — a cizího správce
 *   nesmí vůbec, dokud ho sám nepovýšil ([TargetMember.canBeEdited]).
 * - **Omezovat jde jen v supergroupě**: „Can't restrict users in basic group chats" a v kanálu
 *   není co omezovat, nikdo do něj nepíše. Vyhodit jde i ze základní skupiny.
 * - **Vlastnictví se v základní skupině nepředává**: „Can't change owner in basic group chats".
 *   Ani botovi — ten vlastníkem být nemůže.
 * - [own] `null` = soukromý chat nebo neznámé postavení; tam se nespravuje nic.
 */
internal fun memberActions(
    own: OwnMemberStatus?,
    target: TargetMember,
    kind: MemberGroupKind,
): MemberActions {
    // Vlastnictví se předává někomu jinému, takže ani na sobě samém není co nabídnout.
    if (target.isSelf || target.role == MemberRole.Owner) return MemberActions()
    // Omezení nese vlastní práva jednoho člověka; jinde to TDLib odmítne.
    val restrictable = kind == MemberGroupKind.Supergroup
    return when (own) {
        null, OwnMemberStatus.Member, is OwnMemberStatus.Restricted,
        OwnMemberStatus.Left, OwnMemberStatus.Banned,
        -> MemberActions()

        is OwnMemberStatus.Creator -> if (!own.isMember) {
            MemberActions()
        } else {
            MemberActions(
                canPromote = true,
                canRestrict = restrictable,
                canBan = true,
                canEditTag = kind != MemberGroupKind.Channel,
                // Vlastníkem se stane člověk, ne bot, a ne v základní skupině.
                canTransferOwnership = kind != MemberGroupKind.BasicGroup && !target.isBot,
            )
        }

        is OwnMemberStatus.Admin -> {
            // Cizího správce (toho, koho jsem nepovýšil) neupravuje ani nesesazuje nikdo kromě vlastníka.
            val reachable = target.role != MemberRole.Admin || target.canBeEdited
            MemberActions(
                canPromote = own.canPromoteMembers && reachable,
                canRestrict = own.canRestrictMembers && restrictable && reachable,
                canBan = own.canRestrictMembers && reachable,
                canEditTag = own.canManageTags && kind != MemberGroupKind.Channel && reachable,
            )
        }
    }
}

/**
 * Okamžik, kdy omezení nebo vyhození skončí, jak ho chce TDLib: unix čas, nebo **0 = navždy**.
 *
 * Jde o tentýž údaj pro `restrictedUntilDate` i `bannedUntilDate`. TDLib bere všechno delší než
 * 366 dní (a kratší než 30 sekund) jako navždy, takže se nabízené doby drží pod tím.
 */
internal fun untilDate(duration: Duration?, nowEpochSeconds: Long): Int {
    if (duration == null) return FOREVER
    val until = nowEpochSeconds + duration.inWholeSeconds
    return if (until > Int.MAX_VALUE) FOREVER else until.toInt()
}

/**
 * Konec běžícího omezení, které si člověk při úpravě práv NECHÁVÁ. Posílá se přesně původní
 * konec — jenže když z něj zbývá míň než [MIN_RESTRICTION_SECONDS], vzal by ho TDLib jako
 * navždy, a z omezení, které za chvíli skončí, by bylo trvalé. Takové se proto posune těsně za
 * tu mez: skončí skoro hned, jak mělo.
 */
internal fun keptUntilDate(end: Int, nowEpochSeconds: Long): Int =
    maxOf(end.toLong(), nowEpochSeconds + MIN_RESTRICTION_SECONDS + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** Kratší omezení (nebo vyhození) bere TDLib jako navždy. */
internal const val MIN_RESTRICTION_SECONDS = 30L

/** Nula znamená „bez konce" v `restrictedUntilDate` i `bannedUntilDate`. */
internal const val FOREVER = 0
