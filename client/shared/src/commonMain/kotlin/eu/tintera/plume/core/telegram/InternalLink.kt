package eu.tintera.plume.core.telegram

/**
 * Odkaz na Telegram, který aplikace otevře sama, místo aby ho poslala do prohlížeče
 * ([ChatRepository.resolveLink]). Co odkaz znamená, rozhoduje TDLib (`getInternalLinkType`) —
 * zná všechny podoby (`t.me/…`, `telegram.me/…`, `tg://…`), my jen vybíráme, co umíme ukázat.
 */
sealed interface InternalLink {
    /**
     * Chat: veřejný (`t.me/jméno`), člověk podle čísla (`t.me/+420…`) nebo z kontaktního odkazu
     * (`t.me/contact/…`), Uložené zprávy, případně chat z pozvánky, kde už uživatel je.
     *
     * - [draftText] je text, který odkaz předvyplní do pole (`?text=…`). Jen se VLOŽÍ, neodešle —
     *   napsal ho autor odkazu, ne uživatel.
     * - [openProfile] = odkaz chce profil, ne konverzaci (`?profile`).
     */
    data class Chat(
        val chatId: Long,
        val draftText: String? = null,
        val openProfile: Boolean = false,
    ) : InternalLink

    /**
     * Text ke sdílení (`t.me/share/url?url=…&text=…`): otevře výběr chatu jako sdílení ze
     * systému, text pak čeká nad polem vybraného chatu. Nic se neposílá samo.
     */
    data class ShareText(val text: String) : InternalLink

    /**
     * Odkaz na zprávu. [messageId] je `null`, když chat otevřít jde, ale zprávu samotnou server
     * nevydal (smazaná, soukromý chat bez přístupu ke starší historii) — otevře se aspoň chat.
     */
    data class Message(
        val chatId: Long,
        val messageId: Long?,
        /** Téma skupiny s tématy, ve kterém zpráva je; `null` mimo témata. */
        val topicId: Int? = null,
    ) : InternalLink

    /** Pozvánka do chatu, ve kterém uživatel není. Nejdřív náhled, přidává se až na potvrzení. */
    data class Invite(val link: String, val preview: InvitePreview) : InternalLink

    /** Sada samolepek (`t.me/addstickers/…`). */
    data class StickerSet(val setId: Long) : InternalLink

    /**
     * Spuštění bota s parametrem (`t.me/bot?start=…`). [autostart] = pustit hned, bez tlačítka
     * Start (`InternalLinkTypeBotStart.autostart`); jinak chat nabídne Start a parametr pošle
     * až ono.
     */
    data class BotStart(val chatId: Long, val parameter: String, val autostart: Boolean) : InternalLink

    /**
     * Přidání bota do skupiny (`t.me/bot?startgroup=…`). Skupinu vybírá uživatel; [asAdmin] =
     * bot chce být rovnou správcem (odkaz nese práva, `administratorRights`). Odkaz ([url]) se
     * nese celý: práva i parametr si při přidání přečte znovu hranice TDLib ([ChatRepository.addBotToGroup]).
     */
    data class BotStartInGroup(val url: String, val botChatId: Long, val asAdmin: Boolean) : InternalLink

    /**
     * Hlavní mini aplikace bota (`t.me/bot?startapp=…`). Soukromý chat s botem má id bota, takže
     * [botUserId] je zároveň chat. Bot, který hlavní mini aplikaci nemá, se z odkazu otevře jako
     * chat ([Chat]) — tak to dělají i oficiální klienti.
     */
    data class MainWebApp(val botUserId: Long, val startParameter: String) : InternalLink

    /**
     * Pojmenovaná mini aplikace bota (`t.me/bot/app?startapp=…`). Zda ji bot opravdu má, se pozná
     * až z `searchWebApp` na obrazovce mini aplikace — jako odkaz se rozpozná jen tvarem.
     */
    data class WebApp(val botUserId: Long, val shortName: String, val startParameter: String) : InternalLink

    /**
     * Pozvánka do sdílené složky (`t.me/addlist/…`). Jako u pozvánky do chatu nejdřív náhled,
     * přidává se až na potvrzení — do chatů složky se uživatel přidá.
     */
    data class FolderInvite(val link: String, val preview: FolderInvitePreview) : InternalLink
}

/**
 * Odkaz vede na člověka podle čísla nebo kontaktního odkazu, ale takový účet na Telegramu není
 * (nebo číslo skrývá a v kontaktech není). Vlastní věta, ne obecné „odkaz nejde otevřít".
 */
class LinkUserNotFoundException : IllegalStateException("Uživatel z odkazu na Telegramu není.")

/**
 * Co řekne pozvánka do sdílené složky (`checkChatFolderInviteLink`).
 *
 * - [folderId] je `0`, dokud složku uživatel nemá; s nenulovým id pozvánka jen doplňuje chaty
 *   do složky, kterou už má.
 * - [missingChats] jsou chaty, ve kterých uživatel ještě není — přidáním složky se do nich
 *   přidá. Ty, ve kterých už je, jdou do složky samy a počítá je [alreadyJoined].
 */
data class FolderInvitePreview(
    val title: String,
    val titleEntities: List<MessageEntity>,
    val iconName: String?,
    val folderId: Int,
    val missingChats: List<ChatSummary>,
    val alreadyJoined: Int,
)

/** Co o chatu řekne pozvánka, než se do něj uživatel přidá (`checkChatInviteLink`). */
data class InvitePreview(
    val title: String,
    val description: String,
    val memberCount: Int,
    val isChannel: Boolean,
    val avatarFileId: Int?,
    /** Přidat se nejde hned — správce musí žádost schválit. */
    val createsJoinRequest: Boolean,
)

/** Výsledek přidání se přes pozvánku. */
sealed interface InviteJoinResult {
    data class Joined(val chatId: Long) : InviteJoinResult

    /** Pozvánka vyžaduje schválení; žádost odešla a čeká na správce. */
    data object Requested : InviteJoinResult
}
