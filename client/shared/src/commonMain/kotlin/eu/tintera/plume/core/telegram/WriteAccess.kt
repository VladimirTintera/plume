package eu.tintera.plume.core.telegram

/**
 * Jestli uživatel smí do chatu psát, a když ne, proč — podle toho konverzace místo vstupního
 * pole ukáže, co se dělat dá.
 */
enum class WriteAccess {
    Allowed,

    /** Kanál, ve kterém uživatel nemá právo publikovat. Tady se jen čte (a ztlumuje). */
    ChannelReadOnly,

    /** Skupina, kde psaní zakázal správce — celé skupině, nebo jen tomuhle uživateli. */
    Restricted,

    /** Uživatel ve skupině nebo kanálu není (odešel, byl vyhozen, skupina se převedla). */
    NotMember,

    /** Uzavřené téma ve skupině s tématy — psát do něj smí jen vlastník a správce témat. */
    TopicClosed,

    /**
     * Téma Uložených zpráv uložené z jiného chatu: TDLib do něj odesílat nedovolí („Messages
     * can't be explicitly sent to a Saved Messages topic"), psát se dá jen do Mých poznámek.
     */
    SavedTopic,

    /** Tajný chat čeká na protějšek: dokud se nepřipojí, klíč není dohodnutý a psát nejde. */
    SecretPending,

    /** Tajný chat je ukončený (jednou ze stran, nebo vypršel); psát do něj už nejde nikdy. */
    SecretClosed,

    /**
     * Protějšek přijímá nové chaty jen od kontaktů a uživatelů s Premium
     * (`canSendMessageToUserResultUserRestrictsNewChats`).
     */
    PremiumOnly,

    /** Smazaný nebo neznámý účet (`canSendMessageToUserResultUserIsDeleted`). */
    DeletedAccount,

    /**
     * Zprávy protějšku se platí hvězdami (`canSendMessageToUserResultUserHasPaidMessages`).
     * Placení Plume nemá (`docs/roadmap.md`, „Vědomě mimo"), takže zpráva by skončila chybou.
     */
    PaidMessages,
}

/** Postavení uživatele ve skupině nebo kanálu, zúžené na to, co z něj aplikace potřebuje. */
internal sealed interface OwnMemberStatus {
    data class Creator(val isMember: Boolean) : OwnMemberStatus
    data class Admin(
        val canPostMessages: Boolean,
        /** Právo měnit název, popis a fotku chatu. */
        val canChangeInfo: Boolean = false,
        /** Právo měnit výchozí práva členů (`setChatPermissions` ho vyžaduje), omezovat a vyhazovat. */
        val canRestrictMembers: Boolean = false,
        /** Právo jmenovat další správce — jen v rozsahu vlastních práv, to hlídá server. */
        val canPromoteMembers: Boolean = false,
        /** Právo měnit vlastní štítky členů (`setChatMemberTag`). */
        val canManageTags: Boolean = false,
        /** Právo přidávat členy (a tím i boty). */
        val canInviteUsers: Boolean = false,
        /** Právo spravovat témata — mimo jiné psát do uzavřeného tématu. */
        val canManageTopics: Boolean = false,
        /** Právo mazat cizí zprávy, a tím i cizí téma (`deleteForumTopic`). */
        val canDeleteMessages: Boolean = false,
    ) : OwnMemberStatus
    data object Member : OwnMemberStatus
    data class Restricted(
        val isMember: Boolean,
        val canSendMessages: Boolean,
        /** Vlastní práva omezeného člena na jednotlivé druhy obsahu. */
        val content: ContentAccess,
        val canChangeInfo: Boolean = false,
        val canInviteUsers: Boolean = false,
        /** Vlastní právo zakládat témata (`canCreateTopics`); výchozí právo skupiny pro něj neplatí. */
        val canCreateTopics: Boolean = false,
    ) : OwnMemberStatus
    data object Left : OwnMemberStatus
    data object Banned : OwnMemberStatus
}

/**
 * Pravidlo, kdo smí psát. Čistá funkce kvůli testům (`WriteAccessTest`) — kombinací je dost
 * a na zařízení se každá ověřuje jiným účtem.
 *
 * - [status] `null` = soukromý chat, tam se píše vždycky.
 * - V kanálu smí publikovat jen vlastník a správce s `canPostMessages`; členové a omezení čtou.
 * - Ve skupině rozhoduje [membersCanSend] (výchozí práva celé skupiny, `Chat.permissions`),
 *   u omezeného člena jeho vlastní práva — ta výchozí přebíjejí. Správce a vlastník píšou vždy.
 * - [nonMembersCanSend]: diskusní skupina kanálu může psaní dovolit i těm, kdo do ní nevstoupili
 *   (`Supergroup.joinToSendMessages == false`) — komentovat příspěvek pak jde bez „Přidat se".
 *   Platí pro ně výchozí práva skupiny jako pro člena; vyhozenému to nepomůže.
 */
internal fun writeAccess(
    isChannel: Boolean,
    status: OwnMemberStatus?,
    membersCanSend: Boolean,
    nonMembersCanSend: Boolean = false,
): WriteAccess =
    when (status) {
        null -> WriteAccess.Allowed
        is OwnMemberStatus.Creator -> if (status.isMember) WriteAccess.Allowed else WriteAccess.NotMember
        is OwnMemberStatus.Admin -> when {
            !isChannel || status.canPostMessages -> WriteAccess.Allowed
            else -> WriteAccess.ChannelReadOnly
        }
        OwnMemberStatus.Member -> when {
            isChannel -> WriteAccess.ChannelReadOnly
            membersCanSend -> WriteAccess.Allowed
            else -> WriteAccess.Restricted
        }
        is OwnMemberStatus.Restricted -> when {
            !status.isMember && !(nonMembersCanSend && !isChannel) -> WriteAccess.NotMember
            isChannel -> WriteAccess.ChannelReadOnly
            status.canSendMessages -> WriteAccess.Allowed
            else -> WriteAccess.Restricted
        }
        OwnMemberStatus.Left -> when {
            !nonMembersCanSend || isChannel -> WriteAccess.NotMember
            membersCanSend -> WriteAccess.Allowed
            else -> WriteAccess.Restricted
        }
        OwnMemberStatus.Banned -> WriteAccess.NotMember
    }

/**
 * Psaní do tématu: uzavřené téma přebíjí všechno kromě vlastníka a správce s právem spravovat
 * témata (tak to hlídá server). Kdo psát nesmí z jiného důvodu (není členem, je omezený), dostane
 * ten důvod — ten se řeší dřív než téma.
 */
internal fun topicWriteAccess(chatAccess: WriteAccess, status: OwnMemberStatus?, topicClosed: Boolean): WriteAccess =
    when {
        chatAccess != WriteAccess.Allowed || !topicClosed -> chatAccess
        status is OwnMemberStatus.Creator -> WriteAccess.Allowed
        status is OwnMemberStatus.Admin && status.canManageTopics -> WriteAccess.Allowed
        else -> WriteAccess.TopicClosed
    }

/**
 * Co konkrétně se do chatu smí poslat.
 *
 * Uplatní se, teprve když se smí psát vůbec ([WriteAccess.Allowed]) — jinak vstupní pole není
 * a druhy obsahu nemá co omezovat. Skupina může mít zakázané jen některé druhy: text dovolený,
 * fotky ne. Bez tohohle rozlišení nabídla sponka výběr, který skončil chybou AŽ PO odeslání.
 *
 * Druhy jsou jen ty, které aplikace umí poslat.
 */
data class ContentAccess(
    val photos: Boolean,
    val videos: Boolean,
    val documents: Boolean,
    /**
     * Hudba jako skladba (`canSendAudios`). Bez ní se skladba pošle jako soubor, když soubory
     * smějí — uživatel vybíral „soubor", a ten chat přijme.
     */
    val audios: Boolean,
    val voiceNotes: Boolean,
    /**
     * Video kolečka (`canSendVideoNotes`). Bez výchozí hodnoty, stejně jako [polls]: každé
     * místo, které práva skládá, musí o kolečkách rozhodnout výslovně.
     */
    val videoNotes: Boolean,
    /** Samolepky, animovaná emoji a GIFy — TDLib je vede pod jedním `canSendOtherMessages`. */
    val stickers: Boolean,
    /**
     * Ankety. Kromě práva skupiny (`canSendPolls`) záleží i na DRUHU chatu — viz
     * [pollsAllowedIn]; do soukromého chatu s člověkem anketa nejde.
     */
    val polls: Boolean,
    /**
     * Seznamy úkolů. Právo je totéž jako u anket (`canSendPolls` — „polls and checklists"), druh
     * chatu ale jiný: do soukromého chatu s člověkem seznam jde, do kanálu ne — viz
     * [checklistsAllowedIn]. Že je seznam jen pro Premium, se tu neřeší: to není právo v chatu.
     */
    val checklists: Boolean,
) {
    /** Sponka má co nabídnout. Bez jediného dovoleného druhu se nekreslí vůbec. */
    val anyAttachment: Boolean get() = photos || videos || documents || polls || checklists

    companion object {
        /** Nic se neomezuje: soukromý chat, správce, vlastník i neznámé postavení. */
        val All = ContentAccess(
            photos = true,
            videos = true,
            documents = true,
            audios = true,
            voiceNotes = true,
            videoNotes = true,
            stickers = true,
            polls = true,
            checklists = true,
        )
    }
}

/**
 * Pravidlo, co se smí poslat. Čistá funkce kvůli testům (`WriteAccessTest`) — stejně jako
 * [writeAccess], se kterým se musí shodovat v tom, koho omezení míjejí.
 *
 * - [status] `null` = soukromý chat, tam se posílá všechno.
 * - **Správce a vlastník nejsou omezení nikdy** — jako u [writeAccess].
 * - Členovi platí výchozí práva skupiny ([groupDefaults] z `Chat.permissions`), omezenému
 *   členovi jeho vlastní (`ChatMemberStatusRestricted.permissions`) — ta výchozí přebíjejí.
 * - Kdo v chatu není, nepíše vůbec; hodnota se neuplatní, tak se neomezuje.
 */
internal fun contentAccess(status: OwnMemberStatus?, groupDefaults: ContentAccess): ContentAccess =
    when (status) {
        null -> ContentAccess.All
        is OwnMemberStatus.Creator, is OwnMemberStatus.Admin -> ContentAccess.All
        OwnMemberStatus.Member -> groupDefaults
        is OwnMemberStatus.Restricted -> status.content
        OwnMemberStatus.Left, OwnMemberStatus.Banned -> ContentAccess.All
    }

/**
 * Co smí uživatel v chatu upravovat. Prázdné = obrazovka úpravy se vůbec nenabízí.
 *
 * Dvě různá práva, ne jedno: název, popis a fotka stojí na `canChangeInfo`, výchozí práva členů
 * na `canRestrictMembers` (to vyžaduje `setChatPermissions`). Správce může mít jen jedno z nich.
 */
data class ChatEditRights(
    val canChangeInfo: Boolean = false,
    val canRestrictMembers: Boolean = false,
    /**
     * Pozvánky a žádosti o vstup: vlastník, nebo správce s `canInviteUsers`. Členové smějí
     * zvát (výchozí práva), ale odkazy spravovat ne — `createChatInviteLink` chce správce.
     */
    val canManageInvites: Boolean = false,
) {
    val any: Boolean get() = canChangeInfo || canRestrictMembers || canManageInvites
}

/**
 * Pravidlo, kdo smí chat upravovat. Čistá funkce kvůli testům (`WriteAccessTest`) — stejně jako
 * [writeAccess], protože kombinací postavení a výchozích práv je dost a každá se na zařízení
 * ověřuje jiným účtem.
 *
 * - Vlastník smí všechno.
 * - Správce jen to, co má v právech.
 * - Běžný člen může měnit informace, když to dovolují výchozí práva skupiny; práva členů nikdy.
 * - Omezený člen se řídí svými vlastními právy — ta výchozí přebíjejí, stejně jako u psaní.
 * - V kanálu i u toho, kdo v chatu není, se neupravuje nic. Soukromý chat ([status] `null`)
 *   taky ne: nemá co upravovat.
 */
internal fun chatEditRights(
    isChannel: Boolean,
    status: OwnMemberStatus?,
    membersCanChangeInfo: Boolean,
): ChatEditRights = when (status) {
    null -> ChatEditRights()
    is OwnMemberStatus.Creator ->
        if (status.isMember) {
            ChatEditRights(canChangeInfo = true, canRestrictMembers = !isChannel, canManageInvites = true)
        } else {
            ChatEditRights()
        }
    is OwnMemberStatus.Admin -> ChatEditRights(
        canChangeInfo = status.canChangeInfo,
        // Kanál výchozí práva členů nemá — nikdo do něj nepíše, takže není co omezovat.
        canRestrictMembers = status.canRestrictMembers && !isChannel,
        canManageInvites = status.canInviteUsers,
    )
    OwnMemberStatus.Member ->
        ChatEditRights(canChangeInfo = !isChannel && membersCanChangeInfo)
    is OwnMemberStatus.Restricted ->
        if (status.isMember && !isChannel) ChatEditRights(canChangeInfo = status.canChangeInfo) else ChatEditRights()
    OwnMemberStatus.Left, OwnMemberStatus.Banned -> ChatEditRights()
}

/** Druh chatu z pohledu anket a seznamů úkolů — viz [pollsAllowedIn] a [checklistsAllowedIn]. */
enum class PollChatKind { Group, Channel, Bot, SavedMessages, Private, Secret }

/**
 * Smí do chatu anketa? TDLib: *„Polls can't be sent to secret chats … Polls can be sent to
 * a private chat only if the chat is a chat with a bot or the Saved Messages chat."* Do
 * soukromého chatu s člověkem ji tedy server odmítne — a to až po odeslání, proto se tam
 * vůbec nenabízí.
 */
fun pollsAllowedIn(kind: PollChatKind): Boolean = when (kind) {
    PollChatKind.Group, PollChatKind.Channel, PollChatKind.Bot, PollChatKind.SavedMessages -> true
    PollChatKind.Private, PollChatKind.Secret -> false
}

/**
 * Smí do chatu seznam úkolů? TDLib: *„Checklists can't be sent to secret chats, channel chats and
 * channel direct messages chats."* Na rozdíl od ankety jde i do soukromého chatu s člověkem —
 * seznam na nákup je přesně ten případ.
 */
fun checklistsAllowedIn(kind: PollChatKind): Boolean = when (kind) {
    PollChatKind.Group, PollChatKind.Bot, PollChatKind.SavedMessages, PollChatKind.Private -> true
    PollChatKind.Channel, PollChatKind.Secret -> false
}

/**
 * Smí uživatel do skupiny přidat bota (`t.me/bot?startgroup=…`)? Čistá funkce kvůli testům
 * (`BotInGroupTest`).
 *
 * - Vlastník vždycky, dokud ve skupině je.
 * - Správce s právem zvát; když odkaz chce bota jako správce ([asAdmin]), musí mít právo
 *   jmenovat správce — jinak by server přidání odmítl až po výběru skupiny.
 * - Běžný člen podle výchozích práv skupiny ([membersCanInvite]), omezený podle svých;
 *   správcem nejmenuje nikdo z nich.
 */
internal fun canAddBot(status: OwnMemberStatus?, membersCanInvite: Boolean, asAdmin: Boolean): Boolean =
    when (status) {
        null -> false
        is OwnMemberStatus.Creator -> status.isMember
        is OwnMemberStatus.Admin -> if (asAdmin) status.canPromoteMembers else status.canInviteUsers
        OwnMemberStatus.Member -> !asAdmin && membersCanInvite
        is OwnMemberStatus.Restricted -> !asAdmin && status.isMember && status.canInviteUsers
        OwnMemberStatus.Left, OwnMemberStatus.Banned -> false
    }

/**
 * Smí uživatel do skupiny nebo kanálu přidávat lidi? Totéž pravidlo jako u bota bez práv správce
 * ([canAddBot]); v kanálu členové zvát nesmí nikdy ([isChannel]), tam je to jen pro správce.
 */
internal fun canInviteMembers(status: OwnMemberStatus?, isChannel: Boolean, membersCanInvite: Boolean): Boolean =
    canAddBot(status, membersCanInvite = !isChannel && membersCanInvite, asAdmin = false)
