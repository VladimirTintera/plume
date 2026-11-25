package eu.tintera.plume.core.navigation

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.plume.core.telegram.PrivacySetting
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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

/**
 * Verze uložených zásobníků, klíč v `rememberNavBackStack`. Zásobník uložený před změnou tvaru
 * rout (povinný `slot`) by po aktualizaci šel přečíst jen s výjimkou a shodil by aplikaci. S jinou
 * verzí se uložený stav nenajde a začne se od kořene. Zvednout s každou změnou, kterou stará data
 * nepřečtou; přidání rout nebo parametru s výchozí hodnotou ji nepotřebuje.
 */
const val SAVED_ROUTES_VERSION = 2

/**
 * Routa s obsahem jednoho účtu: chat, zpráva, soubor nebo jiné id, které platí jen v jeho
 * klientovi. Shell obsah takové routy skládá v kontejneru účtu [slot] (`AccountScope`), takže
 * obrazovka dostane repository správného účtu a po zabití procesu se otevře zase v něm.
 *
 * Stránky bez id (archiv, seznam hovorů) slot nemají a skládají se v účtu shellu. Routy Nastavení
 * slot taky nenesou: celý jejich zásobník skládá shell v účtu, který je v Nastavení vybraný
 * (`AppNavigation.settingsAccount`, `inSettingsAccount`).
 */
sealed interface AccountRoute : Route {
    val slot: AccountSlot
}

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
 * Ukládá se **názvem**, takže přidání hodnoty doprostřed nezmění význam uloženého zásobníku.
 * Pořadí je pořadí v seznamu sekcí; první se otevírá v dvoupanelu, když žádná vybraná není.
 * [Ai] seznam ukazuje, jen kde je model dostupný, [WhatsNew] a [About] stojí ve skupině Pomoc.
 *
 * Zrušené jméno v uloženém stavu (dřív `General`) obnovu neshodí — viz [SettingsSectionSerializer].
 */
@Serializable(with = SettingsSectionSerializer::class)
enum class SettingsSection { Privacy, Notifications, Chats, Appearance, Storage, Devices, Ai, WhatsNew, About }

/**
 * Sekce podle jména; jméno, které výčet nezná, je první sekce. Výchozí serializer výčtu by na
 * něm vyhodil `SerializationException` a obnova zásobníku po zabití procesu by shodila aplikaci —
 * stačí, aby mezi uložením a obnovou přišla verze, která sekci zrušila nebo přejmenovala (tak
 * zmizelo `General` při přeskupení Nastavení).
 */
object SettingsSectionSerializer : KSerializer<SettingsSection> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("eu.tintera.plume.core.navigation.SettingsSection", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SettingsSection) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): SettingsSection {
        val name = decoder.decodeString()
        return SettingsSection.entries.firstOrNull { it.name == name } ?: SettingsSection.entries.first()
    }
}

/**
 * Jedna sekce nastavení. V list-detail rozvržení je to detail panel vedle seznamu sekcí,
 * na telefonu samostatná obrazovka, ze které se chodí zpět.
 */
@Serializable
data class SettingsSectionRoute(val section: SettingsSection) : Route

/**
 * Úprava vlastního profilu, z karty účtu nahoře v seznamu sekcí. Stojí přímo nad seznamem, místo sekce: na telefonu
 * samostatná obrazovka, v dvoupanelu je v pravém panelu místo sekce (karta účtu je pak vybraná).
 */
@Serializable
data object EditProfileRoute : Route

/**
 * Seznam blokovaných. Vrší se nad sekci Soukromí: na telefonu
 * samostatná obrazovka, v dvoupanelu nahradí sekci vpravo a zpět vede do ní.
 */
@Serializable
data object BlockedSendersRoute : Route

/**
 * Dvoufázové ověření (cloud heslo). Vrší se nad sekci Soukromí stejně jako [BlockedSendersRoute].
 * Průvodce (zapnutí, změna, obnova) je stav obrazovky, ne další routy: hesla do zásobníku
 * (a tím do uloženého stavu) nepatří.
 */
@Serializable
data object TwoStepRoute : Route

/** Přihlašovací e-mail. Vrší se nad sekci Soukromí jako [TwoStepRoute]; kroky jsou stav obrazovky. */
@Serializable
data object LoginEmailRoute : Route

/** Přenesená data (statistika sítě). Vrší se nad sekci Úložiště. */
@Serializable
data object NetworkUsageRoute : Route

/** Licence knihoven. Vrší se nad sekci O aplikaci. */
@Serializable
data object LicensesRoute : Route

/** Přístupové klíče (seznam a odebrání). Vrší se nad sekci Soukromí jako [TwoStepRoute]. */
@Serializable
data object PasskeysRoute : Route

/** Změna telefonního čísla. Vrší se nad úpravou profilu (i z doporučené akce). */
@Serializable
data object ChangePhoneRoute : Route

/**
 * Zámek aplikace (PIN a biometrie). Vrší se nad sekci Soukromí stejně jako [TwoStepRoute].
 */
@Serializable
data object AppLockRoute : Route

/** Výběr, koho zablokovat. Vrší se nad seznam blokovaných a po zablokování se vrací do něj. */
@Serializable
data object BlockSenderRoute : Route

/** Soukromí účtu (kdo vidí co). Vrší se nad sekci Soukromí stejně jako seznam blokovaných. */
@Serializable
data object AccountPrivacyRoute : Route

/** Smazání účtu. Vrší se nad [AccountPrivacyRoute], kde je samosmazání po neaktivitě. */
@Serializable
data object DeleteAccountRoute : Route

/** Jedno nastavení soukromí s výjimkami. Vrší se nad [AccountPrivacyRoute]. */
@Serializable
data class PrivacySettingRoute(val setting: PrivacySetting) : Route

/** Proxy — vrší se nad sekci Úložiště (skupina Připojení). */
@Serializable
data object ProxySettingsRoute : Route

/** Složky chatů — vrší se nad sekci Chaty. */
@Serializable
data object ChatFoldersRoute : Route

/** Vyzkoušet model (volný rozhovor s modelem v zařízení) — vrší se nad sekci AI v zařízení. */
@Serializable
data object AiPlaygroundRoute : Route

/** Sady samolepek (pořadí, archiv) — vrší se nad sekci Chaty stejně jako složky chatů. */
@Serializable
data object StickerSetsRoute : Route

/** Sady vlastních emoji — táž obrazovka jako [StickerSetsRoute], vlastní pořadí a archiv. */
@Serializable
data object EmojiSetsRoute : Route

/** Editor složky nad seznamem složek; [folderId] `0` = nová složka. */
@Serializable
data class ChatFolderEditRoute(override val slot: AccountSlot, val folderId: Int) : AccountRoute

/**
 * Archivované chaty. V list-detail rozvržení je to LIST panel scény archivu:
 * má vlastní detail panel [ArchiveConversationRoute], takže neovlivňuje otevřenou konverzaci
 * v hlavním seznamu.
 */
@Serializable
data object ArchiveRoute : Route

/** Historie hovorů napříč chaty (menu účtu → Hovory); konverzace z ní jde vedle, jako ze seznamu. */
@Serializable
data object CallsRoute : Route

/** Moje příběhy (menu účtu): v profilu a archiv i vypršelých; prohlížeč přes celou plochu. */
@Serializable
data object MyStoriesRoute : Route

/** Seznam stahovaných souborů (menu účtu → Stahování); zpráva se souborem jde vedle. */
@Serializable
data object DownloadsRoute : Route

/**
 * Konverzace v archivu. V list-detail rozvržení je to detail panel scény archivu.
 */
@Serializable
data class ArchiveConversationRoute(
    override val slot: AccountSlot,
    val chatId: Long,
    val messageId: Long? = null,
    /** Vlákno komentářů v diskusní skupině [chatId] (kořenová zpráva); `null` = celý chat. */
    val threadId: Long? = null,
    /** Téma skupiny s tématy (viz [ForumTopicsRoute]); `null` = celý chat. */
    val topicId: Int? = null,
    /** Vlákno přímých zpráv kanálu (viz [ArchiveDirectTopicsRoute]); `null` = celý chat. */
    val directTopicId: Long? = null,
) : AccountRoute

/** Témata skupiny otevřené z archivu — obdoba [ForumTopicsRoute] v archivní větvi. */
@Serializable
data class ArchiveForumTopicsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Vlákna přímých zpráv kanálu otevřené z archivu — obdoba [DirectTopicsRoute] v archivní větvi. */
@Serializable
data class ArchiveDirectTopicsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Profil chatu otevřený z archivní konverzace.
 */
@Serializable
data class ArchiveChatProfileRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Úprava skupiny otevřená z profilu v archivní větvi. */
@Serializable
data class ArchiveEditChatRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Práva člena otevřená z profilu v archivní větvi. */
@Serializable
data class ArchiveMemberRightsRoute(override val slot: AccountSlot, val chatId: Long, val userId: Long, val admin: Boolean) : AccountRoute

/** Pozvánky otevřené z úpravy skupiny v archivní větvi. */
@Serializable
data class ArchiveInviteLinksRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Žádosti o vstup otevřené z úpravy skupiny v archivní větvi. */
@Serializable
data class ArchiveJoinRequestsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Omezení nebo vyhození členové otevření z úpravy skupiny v archivní větvi. */
@Serializable
data class ArchiveRemovedMembersRoute(override val slot: AccountSlot, val chatId: Long, val banned: Boolean) : AccountRoute

/** Poslední akce otevřené z úpravy skupiny v archivní větvi. */
@Serializable
data class ArchiveEventLogRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Statistiky otevřené z úpravy kanálu nebo skupiny v archivní větvi. */
@Serializable
data class ArchiveChatStatisticsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Statistika příspěvku otevřená v archivní větvi. */
@Serializable
data class ArchivePostStatisticsRoute(override val slot: AccountSlot, val chatId: Long, val messageId: Long) : AccountRoute

/**
 * Konverzace. V list-detail rozvržení je to detail panel vedle seznamu.
 *
 * [messageId]: zpráva, na kterou má konverzace po otevření skočit (nalezená v hledání).
 * Výchozí `null` drží stará uložená zásobníková data čitelná.
 */
@Serializable
data class ConversationRoute(
    override val slot: AccountSlot,
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
    /** Vlákno přímých zpráv kanálu (viz [DirectTopicsRoute]); `null` = celý chat. */
    val directTopicId: Long? = null,
) : AccountRoute

/**
 * Uložené zprávy rozdělené podle chatu, ze kterého se zpráva uložila. Stojí na místě konverzace
 * Uložených zpráv, když si to uživatel zvolil („Zobrazit jako chaty"); téma se otevře jako
 * [ConversationRoute] se `savedTopicId` nad ním. Jen v hlavní větvi — Uložené zprávy archivovat
 * nejdou.
 */
@Serializable
data class SavedTopicsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Seznam témat skupiny s tématy (fórum). Ťuknutí na takovou skupinu v seznamu chatů otevře
 * tohle místo konverzace; téma se pak otevře jako [ConversationRoute] s `topicId` nad ním.
 * Detailový panel jako konverzace: na širokém rozvržení stojí vpravo vedle seznamu chatů.
 */
@Serializable
data class ForumTopicsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Přímé zprávy kanálu, jak je vidí správce: vlákno na každého, kdo kanálu napsal. Stojí na místě
 * konverzace jako [ForumTopicsRoute]; vlákno se otevře jako [ConversationRoute]
 * s `directTopicId` nad ním.
 */
@Serializable
data class DirectTopicsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Profil chatu (ťuknutí na hlavičku konverzace). Třetí panel: na telefonu překryje konverzaci,
 * na široké ploše stojí vedle ní a při přepnutí chatu přejde na nový (`followProfile`).
 */
@Serializable
data class ChatProfileRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Kalendář zpráv. Třetí panel jako profil: na telefonu přes celou obrazovku nad konverzací, na
 * velké ploše vedle seznamu a konverzace, na střední vedle konverzace, a při přepnutí chatu
 * přejde na kalendář nového (`followProfile`). [anchor] je zpráva, na které konverzace stála —
 * vstup do ostrůvku historie (`ConversationCalendar.open`).
 */
@Serializable
data class ChatCalendarRoute(
    override val slot: AccountSlot,
    val chatId: Long,
    val savedTopicId: Long? = null,
    val directTopicId: Long? = null,
    val anchor: Long? = null,
) : AccountRoute

/** [ChatCalendarRoute] konverzace z archivu — panel archivní scény. Uložené zprávy v archivu nejsou. */
@Serializable
data class ArchiveChatCalendarRoute(
    override val slot: AccountSlot,
    val chatId: Long,
    val directTopicId: Long? = null,
    val anchor: Long? = null,
) : AccountRoute

/** Čtečka Instant View stránky [url] (z náhledu odkazu). Vrší se nad konverzací v zásobníku Chatů. */
@Serializable
data class InstantViewRoute(override val slot: AccountSlot, val url: String) : AccountRoute

/**
 * Úprava skupiny nebo kanálu (název, popis, fotka, práva členů). Vrší se nad profil a je to
 * taky třetí panel — na široké ploše profil v něm nahradí.
 */
@Serializable
data class EditChatRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Práva jednoho člena: správcovská ([admin] `true`), nebo jeho omezení. Vrší se nad profil
 * stejně jako úprava skupiny.
 */
@Serializable
data class MemberRightsRoute(override val slot: AccountSlot, val chatId: Long, val userId: Long, val admin: Boolean) : AccountRoute

/** Pozvánky skupiny nebo kanálu. Vrší se nad úpravu, taky jako detailový panel. */
@Serializable
data class InviteLinksRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Žádosti o vstup. Vrší se nad úpravu, taky jako detailový panel. */
@Serializable
data class JoinRequestsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Omezení ([banned] `false`), nebo vyhození členové. Vrší se nad úpravu, taky jako detailový
 * panel; ťuknutí na omezeného nad ně vrší [MemberRightsRoute].
 */
@Serializable
data class RemovedMembersRoute(override val slot: AccountSlot, val chatId: Long, val banned: Boolean) : AccountRoute

/** Poslední akce ve skupině nebo kanálu. Vrší se nad úpravu, taky jako detailový panel. */
@Serializable
data class EventLogRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Statistiky kanálu nebo skupiny. Vrší se nad úpravu, taky jako detailový panel. */
@Serializable
data class ChatStatisticsRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Statistika příspěvku v kanálu — ze statistik kanálu, nebo z „Informace o zprávě" nad
 * konverzací. Taky jako detailový panel.
 */
@Serializable
data class PostStatisticsRoute(override val slot: AccountSlot, val chatId: Long, val messageId: Long) : AccountRoute

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
    override val slot: AccountSlot,
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
    /**
     * Médium sponzorované zprávy [messageId] místo přílohy zprávy (video reklamy na celou
     * obrazovku). Reklama není v historii chatu, prohlížeč ji načte ze seznamu reklam.
     */
    val sponsored: Boolean = false,
) : AccountRoute

/**
 * Prohlížeč příběhů přes celou obrazovku, otevřený u autora [chatId] (kroužek v řádku chatu,
 * hromádka v pilulce). Tahem do strany jde na další autory téhož seznamu příběhů.
 */
@Serializable
data class StoriesRoute(
    override val slot: AccountSlot,
    val chatId: Long,
    /** Příběhy z profilu (mřížka „Příběhy"); prázdné = aktivní příběhy a přechod na další autory. */
    val storyIds: List<Int> = emptyList(),
    val startStoryId: Int = 0,
) : AccountRoute

/**
 * Videochat chatu [chatId] na poslech (ťuknutí na pruh nad konverzací). Přes celou plochu;
 * odchodem z obrazovky se z hovoru odchází.
 */
@Serializable
data class VideoChatRoute(override val slot: AccountSlot, val chatId: Long, val groupCallId: Int) : AccountRoute

/**
 * Nový vlastní příběh z fotky nebo videa [path] (z výběru příloh, v cache aplikace). Vrší se
 * nad seznam chatů přes celou plochu; po zveřejnění se zavře. S [editStoryId] vymění médium
 * zveřejněného příběhu autora [editPosterChatId] (popisek [editCaption] se předvyplní).
 */
@Serializable
data class StoryComposerRoute(
    override val slot: AccountSlot,
    val path: String,
    val video: Boolean = false,
    val editPosterChatId: Long? = null,
    val editStoryId: Int? = null,
    val editCaption: String = "",
    /** Jen obálka videa zveřejněného příběhu; [path] je stažené video toho příběhu. */
    val editCoverOnly: Boolean = false,
) : AccountRoute

/**
 * Tvorba samolepky ze stažené fotky [path] ([width] × [height]) — z prohlížeče médií.
 * Přes celou plochu nad prohlížečem; po přidání do sady se zavře.
 */
@Serializable
data class StickerComposerRoute(
    override val slot: AccountSlot,
    val path: String,
    val width: Int,
    val height: Int,
    /** Sada, do které má samolepka jít (z úpravy vlastní sady); `null` = výběr jako obvykle. */
    val targetSetId: Long? = null,
) : AccountRoute

/** Úprava vlastní sady samolepek [setId] (Nastavení → Sady samolepek → ⋮ → Upravit sadu). */
@Serializable
data class OwnedStickerSetRoute(override val slot: AccountSlot, val setId: Long) : AccountRoute

/** [MediaRoute.startSeconds] bez času v médiu. */
const val NO_START = -1

/** [MediaRoute.storyId] bez příběhu. Id příběhu jsou kladná. */
const val NO_STORY = 0

/**
 * Výběr chatu, do kterého se přeposílá.
 *
 * Nese id zpráv, ne jejich obsah — přeposílá se na serveru podle id a album jich má víc.
 * S [storyId] se posílá příběh autora [fromChatId] a [messageIds] je prázdné.
 */
@Serializable
data class ForwardRoute(override val slot: AccountSlot, val fromChatId: Long, val messageIds: List<Long>, val storyId: Int = NO_STORY) : AccountRoute

/** „Odpovědět v jiném chatu": výběr chatu, kde se odpoví na zprávu [messageId] z chatu [fromChatId]. */
@Serializable
data class ReplyElsewhereRoute(override val slot: AccountSlot, val fromChatId: Long, val messageId: Long) : AccountRoute

/**
 * Výběr pro tlačítko klávesnice bota „vyber lidi" ([users] `true`) nebo „vyber chat" ve zprávě
 * [messageId] chatu [chatId]. Omezení tlačítka nese routa rozložená do polí — domácí typy
 * (`BotUsersRequest`, `BotChatRequest`) serializovatelné nejsou a do `core/telegram` anotace
 * navigace nepatří. Bez metadat scény: jde přes celou plochu z obou větví, jako formulář ankety.
 */
@Serializable
data class BotShareRoute(
    override val slot: AccountSlot,
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
) : AccountRoute

/**
 * Výběr lidí, které přidat do skupiny nebo kanálu [chatId] (pruh „Pozvat členy", ⋮ v profilu).
 * Bez metadat scény: jde přes celou plochu z obou větví, jako sdílení kontaktu.
 */
@Serializable
data class AddMembersRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/** Výběr chatů, do kterých poslat kontakt uživatele [userId] (z profilu, ⋮ → Sdílet kontakt). */
@Serializable
data class ShareContactRoute(override val slot: AccountSlot, val userId: Long) : AccountRoute

/**
 * Výběr skupiny, do které přidat bota ze startovacího odkazu (`t.me/bot?startgroup=…`). Bez
 * metadat scény, jako přeposílání; po přidání ho nahradí konverzace skupiny.
 */
@Serializable
data class AddBotToGroupRoute(override val slot: AccountSlot, val url: String, val botChatId: Long, val asAdmin: Boolean) : AccountRoute

/**
 * Výběr chatu pro tlačítko bota s inline dotazem do jiného chatu. Druhy chatu, které bot
 * dovolil, nese routa rozložené — `SwitchInlineTarget` je doména a serializovat se nemá.
 */
@Serializable
data class SwitchInlineRoute(
    override val slot: AccountSlot,
    val bot: String,
    val query: String,
    val users: Boolean,
    val bots: Boolean,
    val groups: Boolean,
    val channels: Boolean,
) : AccountRoute

/**
 * Nová anketa do chatu. Bez metadat scény, jako výběr pro přeposílání: jde přes celou plochu
 * z hlavního seznamu i z archivu a po odeslání odejde — pod ní je konverzace s novou anketou.
 */
@Serializable
data class PollComposerRoute(
    override val slot: AccountSlot,
    val chatId: Long,
    /** Tlačítko bota si vynutilo jen kvíz ([quizOnly]), nebo jen běžnou anketu ([regularOnly]). */
    val quizOnly: Boolean = false,
    val regularOnly: Boolean = false,
    /** Anketa odejde jako odpověď na tuhle zprávu (rozepsaná odpověď v konverzaci). */
    val replyToMessageId: Long? = null,
    /** Citovaný úryvek té odpovědi; `null` = celá zpráva. */
    val quoteText: String? = null,
    val quotePosition: Int = 0,
) : AccountRoute

/**
 * Nový seznam úkolů do chatu. Jako formulář ankety: bez metadat scény, přes celou plochu
 * z hlavního seznamu i z archivu, po odeslání odejde.
 */
@Serializable
data class ChecklistComposerRoute(
    override val slot: AccountSlot,
    val chatId: Long,
    /** Seznam odejde jako odpověď na tuhle zprávu (rozepsaná odpověď v konverzaci). */
    val replyToMessageId: Long? = null,
    /** Citovaný úryvek té odpovědi; `null` = celá zpráva. */
    val quoteText: String? = null,
    val quotePosition: Int = 0,
    /** Upravit vlastní seznam v téhle zprávě; `null` = nový seznam. */
    val editMessageId: Long? = null,
) : AccountRoute

/**
 * Mini aplikace bota přes celou obrazovku. Bez metadat scény, jako formulář ankety: jde z hlavního
 * seznamu i z archivu ([archive] říká, kam vést odkaz na chat, který mini aplikace otevře).
 * Po zabití procesu se spustí znovu — stav stránky si drží mini aplikace sama.
 */
@Serializable
data class WebAppRoute(
    override val slot: AccountSlot,
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
    /**
     * Mini aplikace strážního bota, který schvaluje vstup do chatu (`getGuardBotWebAppUrl`);
     * id dotazu na vstup, jinak 0. [url] je pak prázdná, [botUserId] je strážní bot.
     */
    val guardQueryId: Long = 0,
) : AccountRoute

/**
 * Potvrzení sdílení do JEDNOHO chatu, vybraného už v systémové nabídce (Direct Share).
 *
 * Nese jen id chatu, sdílený obsah (soubory v cache) je v `ShareRequests`, ne v zásobníku. Po
 * zabití procesu se routa obnoví, obsah ne (sdílení s vybraným chatem se neukládá) a obrazovka
 * sama odejde. Sdílení BEZ vybraného chatu obrazovku nemá — nese ho čip v shellu.
 */
@Serializable
data class ShareToChatRoute(override val slot: AccountSlot, val chatId: Long) : AccountRoute

/**
 * Zahájení nové konverzace (výběr kontaktu nebo hledání) z účtu [slot]; účet jde na obrazovce
 * změnit (řádek „Psát jako"), routa se pak vymění za routu jiného účtu.
 *
 * `slot` má výchozí hodnotu, ne kvůli pohodlí: dřív to byl `data object` a zásobník uložený
 * starou verzí se tak přečte bez zvednutí `SAVED_ROUTES_VERSION`.
 */
@Serializable
data class NewChatRoute(override val slot: AccountSlot = AccountSlot.Primary) : AccountRoute

/**
 * Hledání v seznamu chatů účtu [slot] (chaty, veřejné chaty, zprávy) jako vlastní obrazovka
 * v panelu seznamu (`docs/navrh-hledani.md`). Na široké ploše nahradí seznam chatů a konverzace
 * vpravo zůstane: v zásobníku leží POD konverzací (`AppNavigation.openSearch`). [query] je dotaz
 * zvenku (hashtag ťuknutý v konverzaci).
 */
@Serializable
data class ChatSearchRoute(override val slot: AccountSlot = AccountSlot.Primary, val query: String = "") : AccountRoute

/** Nový kontakt podle telefonního čísla; po přidání se otevře konverzace s ním. */
@Serializable
data class NewContactRoute(override val slot: AccountSlot = AccountSlot.Primary) : AccountRoute

/** Výběr členů nové skupiny; „Další" vede na [CreateChatRoute]. */
@Serializable
data class NewGroupMembersRoute(override val slot: AccountSlot = AccountSlot.Primary) : AccountRoute

/**
 * Založení skupiny ([channel] = `false`, s vybranými [memberChatIds]) nebo kanálu: název, fotka,
 * u kanálu popis.
 */
@Serializable
data class CreateChatRoute(override val slot: AccountSlot, val channel: Boolean, val memberChatIds: List<Long> = emptyList()) : AccountRoute

/**
 * Routa, kterou shell kreslí jako modální sheet NAD tím, co je pod ní (`SheetSceneStrategy`),
 * ne jako obrazovku nebo panel. Sheet je destinací, jen když je místem sám o sobě: otevírá se
 * z víc míst, jeho obsah popíše klíč a výsledek nevrací. Kontextová nabídka nebo krok akce
 * destinací není — viz „Otevřený sheet je stav obrazovky" v `docs/poznatky/seznam-chatu.md`.
 *
 * V zásobníku stojí vždycky navrchu; pravidla nad zásobníkem (panely, čip sdílení) ho
 * přeskakují (`withoutSheets`), jako by tam nebyl. Sheet, nad který se přesto něco položilo,
 * se nekreslí a shell ho ze zásobníku odklidí (`dropBuriedSheets`).
 */
sealed interface SheetRoute : Route

/** Můj QR kód — z menu účtu v seznamu chatů i z karty účtu v Nastavení. Účet shellu, jako stránky Nastavení. */
@Serializable
data object MyQrCodeRoute : SheetRoute

/** Přehled konceptů (menu účtu → Koncepty). */
@Serializable
data class DraftsRoute(override val slot: AccountSlot) : AccountRoute, SheetRoute

/** Záložky zpráv: [chatId] `null` = ze všech chatů (menu účtu), jinak jednoho chatu (profil; [archive] = profil z větve archivu). */
@Serializable
data class BookmarksRoute(override val slot: AccountSlot, val chatId: Long? = null, val archive: Boolean = false) : AccountRoute, SheetRoute

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
            subclass(DeleteAccountRoute::class, DeleteAccountRoute.serializer())
            subclass(PrivacySettingRoute::class, PrivacySettingRoute.serializer())
            subclass(TwoStepRoute::class, TwoStepRoute.serializer())
            subclass(LoginEmailRoute::class, LoginEmailRoute.serializer())
            subclass(PasskeysRoute::class, PasskeysRoute.serializer())
            subclass(NetworkUsageRoute::class, NetworkUsageRoute.serializer())
            subclass(LicensesRoute::class, LicensesRoute.serializer())
            subclass(ChangePhoneRoute::class, ChangePhoneRoute.serializer())
            subclass(AppLockRoute::class, AppLockRoute.serializer())
            subclass(ChatFoldersRoute::class, ChatFoldersRoute.serializer())
            subclass(AiPlaygroundRoute::class, AiPlaygroundRoute.serializer())
            subclass(StickerSetsRoute::class, StickerSetsRoute.serializer())
            subclass(EmojiSetsRoute::class, EmojiSetsRoute.serializer())
            subclass(ProxySettingsRoute::class, ProxySettingsRoute.serializer())
            subclass(ChatFolderEditRoute::class, ChatFolderEditRoute.serializer())
            subclass(ArchiveRoute::class, ArchiveRoute.serializer())
            subclass(CallsRoute::class, CallsRoute.serializer())
            subclass(DownloadsRoute::class, DownloadsRoute.serializer())
            subclass(MyStoriesRoute::class, MyStoriesRoute.serializer())
            subclass(ArchiveConversationRoute::class, ArchiveConversationRoute.serializer())
            subclass(ArchiveForumTopicsRoute::class, ArchiveForumTopicsRoute.serializer())
            subclass(ArchiveDirectTopicsRoute::class, ArchiveDirectTopicsRoute.serializer())
            subclass(ForumTopicsRoute::class, ForumTopicsRoute.serializer())
            subclass(SavedTopicsRoute::class, SavedTopicsRoute.serializer())
            subclass(DirectTopicsRoute::class, DirectTopicsRoute.serializer())
            subclass(ArchiveChatProfileRoute::class, ArchiveChatProfileRoute.serializer())
            subclass(ArchiveEditChatRoute::class, ArchiveEditChatRoute.serializer())
            subclass(ArchiveMemberRightsRoute::class, ArchiveMemberRightsRoute.serializer())
            subclass(ArchiveInviteLinksRoute::class, ArchiveInviteLinksRoute.serializer())
            subclass(ArchiveJoinRequestsRoute::class, ArchiveJoinRequestsRoute.serializer())
            subclass(ArchiveRemovedMembersRoute::class, ArchiveRemovedMembersRoute.serializer())
            subclass(ArchiveEventLogRoute::class, ArchiveEventLogRoute.serializer())
            subclass(ArchiveChatStatisticsRoute::class, ArchiveChatStatisticsRoute.serializer())
            subclass(ArchivePostStatisticsRoute::class, ArchivePostStatisticsRoute.serializer())
            subclass(ConversationRoute::class, ConversationRoute.serializer())
            subclass(ChatProfileRoute::class, ChatProfileRoute.serializer())
            subclass(ChatCalendarRoute::class, ChatCalendarRoute.serializer())
            subclass(ArchiveChatCalendarRoute::class, ArchiveChatCalendarRoute.serializer())
            subclass(InstantViewRoute::class, InstantViewRoute.serializer())
            subclass(EditChatRoute::class, EditChatRoute.serializer())
            subclass(MemberRightsRoute::class, MemberRightsRoute.serializer())
            subclass(InviteLinksRoute::class, InviteLinksRoute.serializer())
            subclass(JoinRequestsRoute::class, JoinRequestsRoute.serializer())
            subclass(RemovedMembersRoute::class, RemovedMembersRoute.serializer())
            subclass(EventLogRoute::class, EventLogRoute.serializer())
            subclass(ChatStatisticsRoute::class, ChatStatisticsRoute.serializer())
            subclass(PostStatisticsRoute::class, PostStatisticsRoute.serializer())
            subclass(MediaRoute::class, MediaRoute.serializer())
            subclass(StoriesRoute::class, StoriesRoute.serializer())
            subclass(StickerComposerRoute::class, StickerComposerRoute.serializer())
            subclass(OwnedStickerSetRoute::class, OwnedStickerSetRoute.serializer())
            subclass(VideoChatRoute::class, VideoChatRoute.serializer())
            subclass(StoryComposerRoute::class, StoryComposerRoute.serializer())
            subclass(ForwardRoute::class, ForwardRoute.serializer())
            subclass(ReplyElsewhereRoute::class, ReplyElsewhereRoute.serializer())
            subclass(ShareContactRoute::class, ShareContactRoute.serializer())
            subclass(AddMembersRoute::class, AddMembersRoute.serializer())
            subclass(BotShareRoute::class, BotShareRoute.serializer())
            subclass(PollComposerRoute::class, PollComposerRoute.serializer())
            subclass(ChecklistComposerRoute::class, ChecklistComposerRoute.serializer())
            subclass(WebAppRoute::class, WebAppRoute.serializer())
            subclass(AddBotToGroupRoute::class, AddBotToGroupRoute.serializer())
            subclass(SwitchInlineRoute::class, SwitchInlineRoute.serializer())
            subclass(ShareToChatRoute::class, ShareToChatRoute.serializer())
            subclass(MyQrCodeRoute::class, MyQrCodeRoute.serializer())
            subclass(DraftsRoute::class, DraftsRoute.serializer())
            subclass(BookmarksRoute::class, BookmarksRoute.serializer())
            subclass(NewChatRoute::class, NewChatRoute.serializer())
            subclass(ChatSearchRoute::class, ChatSearchRoute.serializer())
            subclass(NewContactRoute::class, NewContactRoute.serializer())
            subclass(NewGroupMembersRoute::class, NewGroupMembersRoute.serializer())
            subclass(CreateChatRoute::class, CreateChatRoute.serializer())
        }
    }
}
