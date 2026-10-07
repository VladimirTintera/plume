package eu.tintera.plume.core.navigation

import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.plume.core.telegram.PrivacySetting
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Navigační klíče.
 *
 * `@Serializable` je tu kvůli `rememberNavBackStack`, který zásobník ukládá přes SavedState —
 * bez toho by se po zabití procesu uživatel vrátil na kořen místo tam, kde byl.
 */
sealed interface Route : NavKey

/** Sekce aplikace, každá s vlastním zásobníkem. Zásobník má vždycky právě jednu na dně. */
sealed interface TopLevelRoute : Route

@Serializable
data object ChatsRoute : TopLevelRoute

@Serializable
data object SettingsRoute : TopLevelRoute

/**
 * Sekce nastavení. Je v navigaci, ne ve feature balíčku, protože ji nese [SettingsSectionRoute] —
 * a co je v routě, musí umět serializace.
 *
 * Ukládá se **názvem** (kotlinx.serialization tak výčty serializuje), takže přidání hodnoty
 * doprostřed nezmění význam uloženého zásobníku.
 */
@Serializable
enum class SettingsSection { General, Notifications, Appearance, Devices, WhatsNew }

/**
 * Jedna sekce nastavení. V list-detail rozvržení je to detail panel vedle seznamu sekcí,
 * na telefonu samostatná obrazovka, ze které se chodí zpět.
 */
@Serializable
data class SettingsSectionRoute(val section: SettingsSection) : Route

/**
 * Úprava vlastního profilu. Vrší se nad sekci Obecné (odkud se otevírá kartou účtu): na telefonu
 * samostatná obrazovka, v dvoupanelu nahradí sekci v pravém panelu a zpět vede do ní.
 */
@Serializable
data object EditProfileRoute : Route

/**
 * Seznam blokovaných. Vrší se nad sekci Obecné stejně jako [EditProfileRoute]: na telefonu
 * samostatná obrazovka, v dvoupanelu nahradí sekci vpravo a zpět vede do ní.
 */
@Serializable
data object BlockedSendersRoute : Route

/**
 * Dvoufázové ověření (cloud heslo). Vrší se nad sekci Obecné stejně jako [BlockedSendersRoute].
 * Průvodce (zapnutí, změna, obnova) je stav obrazovky, ne další routy: hesla do zásobníku
 * (a tím do uloženého stavu) nepatří.
 */
@Serializable
data object TwoStepRoute : Route

/** Výběr, koho zablokovat. Vrší se nad seznam blokovaných a po zablokování se vrací do něj. */
@Serializable
data object BlockSenderRoute : Route

/** Soukromí účtu (kdo vidí co). Vrší se nad sekci Obecné stejně jako seznam blokovaných. */
@Serializable
data object AccountPrivacyRoute : Route

/** Jedno nastavení soukromí s výjimkami. Vrší se nad [AccountPrivacyRoute]. */
@Serializable
data class PrivacySettingRoute(val setting: PrivacySetting) : Route

/** Proxy — vrší se nad sekci Obecné stejně jako složky chatů. */
@Serializable
data object ProxySettingsRoute : Route

/** Složky chatů — vrší se nad sekci Obecné stejně jako seznam blokovaných. */
@Serializable
data object ChatFoldersRoute : Route

/** Editor složky nad seznamem složek; [folderId] `0` = nová složka. */
@Serializable
data class ChatFolderEditRoute(val folderId: Int) : Route

/**
 * Archivované chaty. V list-detail rozvržení je to LIST panel scény archivu:
 * má vlastní detail panel [ArchiveConversationRoute], takže neovlivňuje otevřenou konverzaci
 * v hlavním seznamu.
 */
@Serializable
data object ArchiveRoute : Route

/**
 * Konverzace v archivu. V list-detail rozvržení je to detail panel scény archivu.
 */
@Serializable
data class ArchiveConversationRoute(
    val chatId: Long,
    val messageId: Long? = null,
    /** Vlákno komentářů v diskusní skupině [chatId] (kořenová zpráva); `null` = celý chat. */
    val threadId: Long? = null,
    /** Téma skupiny s tématy (viz [ForumTopicsRoute]); `null` = celý chat. */
    val topicId: Int? = null,
) : Route

/** Témata skupiny otevřené z archivu — obdoba [ForumTopicsRoute] v archivní větvi. */
@Serializable
data class ArchiveForumTopicsRoute(val chatId: Long) : Route

/**
 * Profil chatu otevřený z archivní konverzace.
 */
@Serializable
data class ArchiveChatProfileRoute(val chatId: Long) : Route

/** Úprava skupiny otevřená z profilu v archivní větvi. */
@Serializable
data class ArchiveEditChatRoute(val chatId: Long) : Route

/** Práva člena otevřená z profilu v archivní větvi. */
@Serializable
data class ArchiveMemberRightsRoute(val chatId: Long, val userId: Long, val admin: Boolean) : Route

/** Pozvánky otevřené z úpravy skupiny v archivní větvi. */
@Serializable
data class ArchiveInviteLinksRoute(val chatId: Long) : Route

/** Žádosti o vstup otevřené z úpravy skupiny v archivní větvi. */
@Serializable
data class ArchiveJoinRequestsRoute(val chatId: Long) : Route

/**
 * Konverzace. V list-detail rozvržení je to detail panel vedle seznamu.
 *
 * [messageId]: zpráva, na kterou má konverzace po otevření skočit (nalezená v hledání).
 * Výchozí `null` drží stará uložená zásobníková data čitelná.
 */
@Serializable
data class ConversationRoute(
    val chatId: Long,
    val messageId: Long? = null,
    /**
     * Vlákno komentářů k příspěvku kanálu: kořenová zpráva v diskusní skupině [chatId].
     * `null` = celý chat. Komentáře jsou tak obyčejná konverzace skupiny, jen zúžená.
     */
    val threadId: Long? = null,
    /** Téma skupiny s tématy (viz [ForumTopicsRoute]); `null` = celý chat. */
    val topicId: Int? = null,
    /** Téma Uložených zpráv (viz [SavedTopicsRoute]); `null` = všechny uložené zprávy. */
    val savedTopicId: Long? = null,
) : Route

/**
 * Uložené zprávy rozdělené podle chatu, ze kterého se zpráva uložila. Stojí na místě konverzace
 * Uložených zpráv, když si to uživatel zvolil („Zobrazit jako chaty"); téma se otevře jako
 * [ConversationRoute] se `savedTopicId` nad ním. Jen v hlavní větvi — Uložené zprávy archivovat
 * nejdou.
 */
@Serializable
data class SavedTopicsRoute(val chatId: Long) : Route

/**
 * Seznam témat skupiny s tématy (fórum). Ťuknutí na takovou skupinu v seznamu chatů otevře
 * tohle místo konverzace; téma se pak otevře jako [ConversationRoute] s `topicId` nad ním.
 * Detailový panel jako konverzace: na širokém rozvržení stojí vpravo vedle seznamu chatů.
 */
@Serializable
data class ForumTopicsRoute(val chatId: Long) : Route

/**
 * Profil chatu (ťuknutí na hlavičku konverzace). Detailový panel: na telefonu překryje konverzaci,
 * na širokém rozvržení ji v pravém panelu nahradí a zpět vede do ní.
 */
@Serializable
data class ChatProfileRoute(val chatId: Long) : Route

/**
 * Úprava skupiny nebo kanálu (název, popis, fotka, práva členů). Vrší se nad profil a je to
 * taky detailový panel — na širokém rozvržení profil vpravo nahradí.
 */
@Serializable
data class EditChatRoute(val chatId: Long) : Route

/**
 * Práva jednoho člena: správcovská ([admin] `true`), nebo jeho omezení. Vrší se nad profil
 * stejně jako úprava skupiny.
 */
@Serializable
data class MemberRightsRoute(val chatId: Long, val userId: Long, val admin: Boolean) : Route

/** Pozvánky skupiny nebo kanálu. Vrší se nad úpravu, taky jako detailový panel. */
@Serializable
data class InviteLinksRoute(val chatId: Long) : Route

/** Žádosti o vstup. Vrší se nad úpravu, taky jako detailový panel. */
@Serializable
data class JoinRequestsRoute(val chatId: Long) : Route

/**
 * Prohlížeč média přes celou obrazovku.
 *
 * Vlastní routa, ne overlay uvnitř konverzace: takhle se ven chodí zpět (včetně predictive back),
 * přežije to otočení displeje i zabití procesu a nemusí se řešit vlastní `BackHandler`, který
 * by predictive back stejně zabil.
 *
 * Nese jen id, ne obsah — média se dotahují z repository podle zprávy.
 */
@Serializable
data class MediaRoute(
    val chatId: Long,
    val messageId: Long,
    /**
     * Otevřel se prohlížeč z mřížky sdílených médií v profilu?
     *
     * Rozhoduje o klíči sdíleného prvku (viz `photoKey`): bublina v konverzaci a dlaždice
     * v profilu jsou dvě různá místa téže fotky a se společným klíčem se spárovaly mezi sebou.
     */
    val fromSharedMedia: Boolean = false,
    /**
     * Příloha ankety místo přílohy zprávy: `POLL_QUESTION_MEDIA` (otázka), nebo PŮVODNÍ index
     * odpovědi; `NO_POLL_MEDIA` = běžná zpráva. Anketa nese víc obrázků pod jedním id zprávy.
     */
    val pollMedia: Int = NO_POLL_MEDIA,
    /**
     * Čas v médiu („1:23" v textu): video se otevře rovnou na celé obrazovce a hraje od téhle
     * vteřiny. `NO_START` = běžné otevření do galerie.
     */
    val startSeconds: Int = NO_START,
    /**
     * Historie profilových fotek chatu (ťuknutí na fotku v profilu) místo médií ze zpráv;
     * [messageId] je pak 0 — stránky nejsou zprávy.
     */
    val profilePhotos: Boolean = false,
    /**
     * Médium článku (`ArticleMedia.index`) místo přílohy zprávy; `NO_ARTICLE_MEDIA` = běžná
     * zpráva. Článek nese víc médií pod jedním id zprávy, stejně jako anketa.
     */
    val articleMedia: Int = NO_ARTICLE_MEDIA,
    /**
     * Příběh podle autora a id (odpověď na příběh, odkaz `t.me/jméno/s/N`): [chatId] je autor,
     * [messageId] 0 — stránka není zpráva. `NO_STORY` = běžná zpráva.
     */
    val storyId: Int = NO_STORY,
) : Route

/** [MediaRoute.startSeconds] bez času v médiu. */
const val NO_START = -1

/** [MediaRoute.storyId] bez příběhu. Id příběhu jsou kladná. */
const val NO_STORY = 0

/**
 * Výběr chatu, do kterého se přeposílá.
 *
 * Nese id zpráv, ne jejich obsah — přeposílá se na serveru podle id a album jich má víc.
 */
@Serializable
data class ForwardRoute(val fromChatId: Long, val messageIds: List<Long>) : Route

/**
 * Výběr pro tlačítko klávesnice bota „vyber lidi" ([users] `true`) nebo „vyber chat" ve zprávě
 * [messageId] chatu [chatId]. Omezení tlačítka nese routa rozložená do polí — domácí typy
 * (`BotUsersRequest`, `BotChatRequest`) serializovatelné nejsou a do `core/telegram` anotace
 * navigace nepatří. Bez metadat scény: jde přes celou plochu z obou větví, jako formulář ankety.
 */
@Serializable
data class BotShareRoute(
    val chatId: Long,
    val messageId: Long,
    val buttonId: Int,
    val users: Boolean,
    /** Lidé: `true` jen boti, `false` jen lidé, `null` kdokoli. */
    val bots: Boolean? = null,
    val maxQuantity: Int = 1,
    /** Chat: kanál, jinak skupina. */
    val channel: Boolean = false,
    val forum: Boolean? = null,
    val botIsMember: Boolean = false,
    val botAdministrator: Boolean = false,
) : Route

/**
 * Výběr lidí, které přidat do skupiny nebo kanálu [chatId] (pruh „Pozvat členy", ⋮ v profilu).
 * Bez metadat scény: jde přes celou plochu z obou větví, jako sdílení kontaktu.
 */
@Serializable
data class AddMembersRoute(val chatId: Long) : Route

/** Výběr chatů, do kterých poslat kontakt uživatele [userId] (z profilu, ⋮ → Sdílet kontakt). */
@Serializable
data class ShareContactRoute(val userId: Long) : Route

/**
 * Výběr skupiny, do které přidat bota ze startovacího odkazu (`t.me/bot?startgroup=…`). Bez
 * metadat scény, jako přeposílání; po přidání ho nahradí konverzace skupiny.
 */
@Serializable
data class AddBotToGroupRoute(val url: String, val botChatId: Long, val asAdmin: Boolean) : Route

/**
 * Výběr chatu pro tlačítko bota s inline dotazem do jiného chatu. Druhy chatu, které bot
 * dovolil, nese routa rozložené — `SwitchInlineTarget` je doména a serializovat se nemá.
 */
@Serializable
data class SwitchInlineRoute(
    val bot: String,
    val query: String,
    val users: Boolean,
    val bots: Boolean,
    val groups: Boolean,
    val channels: Boolean,
) : Route

/**
 * Nová anketa do chatu. Bez metadat scény, jako výběr pro přeposílání: jde přes celou plochu
 * z hlavního seznamu i z archivu a po odeslání odejde — pod ní je konverzace s novou anketou.
 */
@Serializable
data class PollComposerRoute(
    val chatId: Long,
    /** Tlačítko bota si vynutilo jen kvíz ([quizOnly]), nebo jen běžnou anketu ([regularOnly]). */
    val quizOnly: Boolean = false,
    val regularOnly: Boolean = false,
    /** Anketa odejde jako odpověď na tuhle zprávu (rozepsaná odpověď v konverzaci). */
    val replyToMessageId: Long? = null,
    /** Citovaný úryvek té odpovědi; `null` = celá zpráva. */
    val quoteText: String? = null,
    val quotePosition: Int = 0,
) : Route

/**
 * Mini aplikace bota přes celou obrazovku. Bez metadat scény, jako formulář ankety: jde z hlavního
 * seznamu i z archivu ([archive] říká, kam vést odkaz na chat, který mini aplikace otevře).
 * Po zabití procesu se spustí znovu — stav stránky si drží mini aplikace sama.
 */
@Serializable
data class WebAppRoute(
    val chatId: Long,
    val botUserId: Long,
    val url: String,
    /** Spuštění přes `openWebApp` (tlačítko pod zprávou); jinak jen adresa (`getWebAppUrl`). */
    val launch: Boolean,
    /** Text tlačítka vlastní klávesnice — jen s ním smí mini aplikace poslat data botovi. */
    val keyboardButtonText: String? = null,
    val archive: Boolean = false,
    /** Hlavní mini aplikace bota (`getMainWebApp`); [url] je pak prázdná. */
    val main: Boolean = false,
    /** Parametr z odkazu `t.me/bot?startapp=…` / `t.me/bot/app?startapp=…`; jinak prázdný. */
    val startParameter: String = "",
    /** Pojmenovaná mini aplikace z odkazu `t.me/bot/app` (`getWebAppLinkUrl`); [url] je pak prázdná. */
    val shortName: String = "",
) : Route

/**
 * Výběr chatu pro obsah sdílený do aplikace ze systému.
 *
 * Nenese nic — sdílený obsah (soubory v cache) je v `ShareRequests`, ne v zásobníku. Po zabití
 * procesu se routa obnoví, ale obsah ne; obrazovka pak sama odejde.
 */
@Serializable
data object ShareRoute : Route

/**
 * Potvrzení sdílení do JEDNOHO chatu, vybraného už v systémové nabídce (Direct Share).
 *
 * Nese jen id chatu, sdílený obsah je jako u [ShareRoute] v `ShareRequests` — po zabití procesu
 * se routa obnoví, obsah ne a obrazovka sama odejde.
 */
@Serializable
data class ShareToChatRoute(val chatId: Long) : Route

/**
 * Zahájení nové konverzace (výběr kontaktu nebo hledání).
 */
@Serializable
data object NewChatRoute : Route

/** Výběr členů nové skupiny; „Další" vede na [CreateChatRoute]. */
@Serializable
data object NewGroupMembersRoute : Route

/**
 * Založení skupiny ([channel] = `false`, s vybranými [memberChatIds]) nebo kanálu: název, fotka,
 * u kanálu popis.
 */
@Serializable
data class CreateChatRoute(val channel: Boolean, val memberChatIds: List<Long> = emptyList()) : Route

/**
 * Konfigurace pro ukládání navigačního zásobníku.
 *
 * **Každá nová routa se MUSÍ zaregistrovat níže.** `NavKey` je otevřená polymorfní hierarchie
 * z cizí knihovny, takže si kotlinx.serialization podtypy sama neodvodí — `rememberNavBackStack`
 * bez registrace **spadne hned při první kompozici** hláškou „You must pass a
 * `SavedStateConfiguration.serializersModule` configured to handle `NavKey` open polymorphism".
 *
 * Pozor, je to past: pád nastane až za běhu, kompilace projde. Nová routa bez řádku níž
 * tedy projde buildem i review a shodí appku až na zařízení.
 *
 * `SerializersModule` je vložený přímo tady, ne v samostatné `private val` nad tímhle — top-level
 * hodnoty se inicializují v pořadí deklarace, takže odkaz na property deklarovanou níž by tu byl
 * `null` a konstrukce by spadla na `NullPointerException`.
 */
val NavSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ChatsRoute::class, ChatsRoute.serializer())
            subclass(SettingsRoute::class, SettingsRoute.serializer())
            subclass(SettingsSectionRoute::class, SettingsSectionRoute.serializer())
            subclass(EditProfileRoute::class, EditProfileRoute.serializer())
            subclass(BlockedSendersRoute::class, BlockedSendersRoute.serializer())
            subclass(BlockSenderRoute::class, BlockSenderRoute.serializer())
            subclass(AccountPrivacyRoute::class, AccountPrivacyRoute.serializer())
            subclass(PrivacySettingRoute::class, PrivacySettingRoute.serializer())
            subclass(TwoStepRoute::class, TwoStepRoute.serializer())
            subclass(ChatFoldersRoute::class, ChatFoldersRoute.serializer())
            subclass(ProxySettingsRoute::class, ProxySettingsRoute.serializer())
            subclass(ChatFolderEditRoute::class, ChatFolderEditRoute.serializer())
            subclass(ArchiveRoute::class, ArchiveRoute.serializer())
            subclass(ArchiveConversationRoute::class, ArchiveConversationRoute.serializer())
            subclass(ArchiveForumTopicsRoute::class, ArchiveForumTopicsRoute.serializer())
            subclass(ForumTopicsRoute::class, ForumTopicsRoute.serializer())
            subclass(SavedTopicsRoute::class, SavedTopicsRoute.serializer())
            subclass(ArchiveChatProfileRoute::class, ArchiveChatProfileRoute.serializer())
            subclass(ArchiveEditChatRoute::class, ArchiveEditChatRoute.serializer())
            subclass(ArchiveMemberRightsRoute::class, ArchiveMemberRightsRoute.serializer())
            subclass(ArchiveInviteLinksRoute::class, ArchiveInviteLinksRoute.serializer())
            subclass(ArchiveJoinRequestsRoute::class, ArchiveJoinRequestsRoute.serializer())
            subclass(ConversationRoute::class, ConversationRoute.serializer())
            subclass(ChatProfileRoute::class, ChatProfileRoute.serializer())
            subclass(EditChatRoute::class, EditChatRoute.serializer())
            subclass(MemberRightsRoute::class, MemberRightsRoute.serializer())
            subclass(InviteLinksRoute::class, InviteLinksRoute.serializer())
            subclass(JoinRequestsRoute::class, JoinRequestsRoute.serializer())
            subclass(MediaRoute::class, MediaRoute.serializer())
            subclass(ForwardRoute::class, ForwardRoute.serializer())
            subclass(ShareContactRoute::class, ShareContactRoute.serializer())
            subclass(AddMembersRoute::class, AddMembersRoute.serializer())
            subclass(BotShareRoute::class, BotShareRoute.serializer())
            subclass(PollComposerRoute::class, PollComposerRoute.serializer())
            subclass(WebAppRoute::class, WebAppRoute.serializer())
            subclass(AddBotToGroupRoute::class, AddBotToGroupRoute.serializer())
            subclass(SwitchInlineRoute::class, SwitchInlineRoute.serializer())
            subclass(ShareRoute::class, ShareRoute.serializer())
            subclass(ShareToChatRoute::class, ShareToChatRoute.serializer())
            subclass(NewChatRoute::class, NewChatRoute.serializer())
            subclass(NewGroupMembersRoute::class, NewGroupMembersRoute.serializer())
            subclass(CreateChatRoute::class, CreateChatRoute.serializer())
        }
    }
}
