package eu.tintera.plume.core.navigation

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
enum class SettingsSection { General, Notifications, Appearance, Devices }

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
data class ArchiveConversationRoute(val chatId: Long, val messageId: Long? = null) : Route

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

/**
 * Konverzace. V list-detail rozvržení je to detail panel vedle seznamu.
 *
 * [messageId]: zpráva, na kterou má konverzace po otevření skočit (nalezená v hledání).
 * Výchozí `null` drží stará uložená zásobníková data čitelná.
 */
@Serializable
data class ConversationRoute(val chatId: Long, val messageId: Long? = null) : Route

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
data class MediaRoute(val chatId: Long, val messageId: Long) : Route

/**
 * Výběr chatu, do kterého se přeposílá.
 *
 * Nese id zpráv, ne jejich obsah — přeposílá se na serveru podle id a album jich má víc.
 */
@Serializable
data class ForwardRoute(val fromChatId: Long, val messageIds: List<Long>) : Route

/**
 * Výběr chatu pro obsah sdílený do aplikace ze systému.
 *
 * Nenese nic — sdílený obsah (soubory v cache) je v `ShareRequests`, ne v zásobníku. Po zabití
 * procesu se routa obnoví, ale obsah ne; obrazovka pak sama odejde.
 */
@Serializable
data object ShareRoute : Route

/**
 * Zahájení nové konverzace (výběr kontaktu nebo hledání).
 */
@Serializable
data object NewChatRoute : Route

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
            subclass(ArchiveRoute::class, ArchiveRoute.serializer())
            subclass(ArchiveConversationRoute::class, ArchiveConversationRoute.serializer())
            subclass(ArchiveChatProfileRoute::class, ArchiveChatProfileRoute.serializer())
            subclass(ArchiveEditChatRoute::class, ArchiveEditChatRoute.serializer())
            subclass(ArchiveMemberRightsRoute::class, ArchiveMemberRightsRoute.serializer())
            subclass(ConversationRoute::class, ConversationRoute.serializer())
            subclass(ChatProfileRoute::class, ChatProfileRoute.serializer())
            subclass(EditChatRoute::class, EditChatRoute.serializer())
            subclass(MemberRightsRoute::class, MemberRightsRoute.serializer())
            subclass(MediaRoute::class, MediaRoute.serializer())
            subclass(ForwardRoute::class, ForwardRoute.serializer())
            subclass(ShareRoute::class, ShareRoute.serializer())
            subclass(NewChatRoute::class, NewChatRoute.serializer())
        }
    }
}
