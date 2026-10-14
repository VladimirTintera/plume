package eu.tintera.plume.core.telegram

/**
 * Nastavení skupiny a kanálu nad rámec názvu, popisu a práv členů: pomalý režim, reakce,
 * ochrana obsahu a přepínače supergroupy a kanálu.
 *
 * Co se komu nabídne, je **čistá funkce** [groupSettingsOffer] — stejně jako [chatEditRights]
 * a [memberActions]: každá kombinace (vlastník, správce s jiným právem, veřejná skupina, diskuse
 * kanálu) se na zařízení ověřuje jiným chatem, takže uvnitř repository by se neotestovaly.
 */

/** Přepínač nastavení; pořadí je pořadí na obrazovce. */
enum class GroupToggle {
    /** Nový člen uvidí starší zprávy (`toggleSupergroupIsAllHistoryAvailable`). */
    AllHistory,

    /** Do diskuse kanálu smí psát jen členové (`toggleSupergroupJoinToSendMessages`). */
    JoinToSend,

    /** Kdo se přidá sám, čeká na schválení (`toggleSupergroupJoinByRequest`). */
    JoinByRequest,

    /** Seznam členů uvidí jen správci (`toggleSupergroupHasHiddenMembers`). */
    HiddenMembers,

    /** Přísnější antispam od Telegramu (`toggleSupergroupHasAggressiveAntiSpamEnabled`). */
    AggressiveAntiSpam,

    /** Příspěvky kanálu nesou jméno správce (`toggleSupergroupSignMessages`). */
    SignMessages,

    /** Jméno pod příspěvkem vede na profil autora; jen s [SignMessages]. */
    ShowMessageSender,

    /** Obsah nejde přeposílat, kopírovat ani ukládat (`toggleChatHasProtectedContent`). */
    ProtectedContent,
}

/**
 * Reakce povolené v chatu. [all] `true` = všechny; jinak jen [emojis], a prázdné = žádné.
 * Vlastní emoji reakce (jen kanál s boosty) se tu nevedou — repository je při uložení přenese
 * beze změny, aby je aplikace, která je neumí vybrat, tiše nesmazala.
 */
data class ChatReactions(val all: Boolean, val emojis: List<String> = emptyList()) {
    val none: Boolean get() = !all && emojis.isEmpty()
}

/**
 * Nastavení, jak ho uživatel vidí a smí měnit. Co v něm chybí (`null`, chybějící klíč), se
 * nenabízí.
 */
data class GroupSettings(
    /** Pomalý režim v sekundách (0 = vypnutý); `null` = nenabízí se. */
    val slowModeDelay: Int? = null,
    /** Nabízené přepínače a jejich současná hodnota. */
    val toggles: Map<GroupToggle, Boolean> = emptyMap(),
    /** Povolené reakce; `null` = nenabízí se. */
    val reactions: ChatReactions? = null,
) {
    val any: Boolean get() = slowModeDelay != null || toggles.isNotEmpty() || reactions != null
}

/** Co se nabídne — výsledek [groupSettingsOffer], bez hodnot. */
data class GroupSettingsOffer(
    val slowMode: Boolean = false,
    val toggles: Set<GroupToggle> = emptySet(),
    val reactions: Boolean = false,
)

/**
 * Co smí uživatel u chatu nastavit. Meze jsou z dokumentace TDLib, ne odhad:
 *
 * - **Pomalý režim, schvalování, „psát jen členové"** chtějí právo omezovat (`canRestrictMembers`),
 *   **historie, reakce a podpisy** právo měnit údaje (`canChangeInfo`), **ochrana obsahu**
 *   vlastníka („Requires owner privileges").
 * - **Přepínače supergroupy jen v supergroupě**, ne v kanálu a ne v základní skupině — ta by se
 *   musela napřed převést. Reakce a ochrana obsahu jdou i v základní skupině.
 * - **Historie pro nové členy jen v soukromé skupině bez kanálu**: veřejná skupina i diskuse
 *   kanálu ji mají vždycky (oficiální klienti volbu u nich neukazují).
 * - **„Psát jen členové" jen v diskusi kanálu** ([hasLinkedChat]) — jinde do skupiny nečlen
 *   psát nemůže. **Schvalování** ve veřejné skupině, nebo v diskusi s „psát jen členové"; do
 *   soukromé se bez pozvánky nikdo nedostane a pozvánky schvalování mají vlastní.
 * - **Skrýt členy a antispam rozhoduje server** ([canHideMembers], [canToggleAntiSpam] ze
 *   `SupergroupFullInfo`) — závisí na velikosti skupiny, ne jen na právech.
 * - **Podpisy a profily autorů jen v kanálu.**
 */
internal fun groupSettingsOffer(
    kind: MemberGroupKind,
    own: OwnMemberStatus?,
    isPublic: Boolean,
    hasLinkedChat: Boolean,
    joinToSendMessages: Boolean,
    canHideMembers: Boolean,
    canToggleAntiSpam: Boolean,
): GroupSettingsOffer {
    val owner = own is OwnMemberStatus.Creator && own.isMember
    val admin = own as? OwnMemberStatus.Admin
    val changeInfo = owner || admin?.canChangeInfo == true
    val restrict = owner || admin?.canRestrictMembers == true
    if (!owner && admin == null) return GroupSettingsOffer()

    val supergroup = kind == MemberGroupKind.Supergroup
    val channel = kind == MemberGroupKind.Channel
    val toggles = buildSet {
        if (supergroup && changeInfo && !isPublic && !hasLinkedChat) add(GroupToggle.AllHistory)
        if (supergroup && restrict && hasLinkedChat) add(GroupToggle.JoinToSend)
        if (supergroup && restrict && (isPublic || (hasLinkedChat && joinToSendMessages))) add(GroupToggle.JoinByRequest)
        if (supergroup && restrict && canHideMembers) add(GroupToggle.HiddenMembers)
        if (supergroup && canToggleAntiSpam) add(GroupToggle.AggressiveAntiSpam)
        if (channel && changeInfo) {
            add(GroupToggle.SignMessages)
            add(GroupToggle.ShowMessageSender)
        }
        if (owner) add(GroupToggle.ProtectedContent)
    }
    return GroupSettingsOffer(
        slowMode = supergroup && restrict,
        toggles = toggles,
        reactions = changeInfo,
    )
}

/** Doby pomalého režimu, které TDLib přijme (`setChatSlowModeDelay`), v sekundách. */
val SLOW_MODE_DELAYS = listOf(0, 5, 10, 30, 60, 300, 900, 3600)
