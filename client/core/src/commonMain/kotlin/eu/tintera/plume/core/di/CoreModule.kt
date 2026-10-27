package eu.tintera.plume.core.di

import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.calls.CallMedia
import eu.tintera.plume.core.calls.CallPointerDisplay
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.AiTextRepository
import eu.tintera.plume.core.telegram.AutoDownloadPresets
import eu.tintera.plume.core.telegram.AutosaveRepository
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatCreation
import eu.tintera.plume.core.telegram.ChatFolderRepository
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ContactSync
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.CustomEmojiRepository
import eu.tintera.plume.core.telegram.DefaultAutoDeleteSettings
import eu.tintera.plume.core.telegram.DeviceRepository
import eu.tintera.plume.core.telegram.DirectTopicsRepository
import eu.tintera.plume.core.telegram.DownloadsRepository
import eu.tintera.plume.core.telegram.EmojiRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.ForumRepository
import eu.tintera.plume.core.telegram.GifRepository
import eu.tintera.plume.core.telegram.InstantViewRepository
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.telegram.LiveLocationRepository
import eu.tintera.plume.core.telegram.LiveStoryRepository
import eu.tintera.plume.core.telegram.LiveUploads
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.NotificationSettingsRepository
import eu.tintera.plume.core.telegram.NotificationSoundRepository
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.PlaceRepository
import eu.tintera.plume.core.telegram.PrivacyRepository
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.ProxyRepository
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.core.telegram.RevealedSpoilers
import eu.tintera.plume.core.telegram.SavedTopicsRepository
import eu.tintera.plume.core.telegram.SelfDestructTimers
import eu.tintera.plume.core.telegram.SensitiveContentSettings
import eu.tintera.plume.core.telegram.StatisticsRepository
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.StoryRepository
import eu.tintera.plume.core.telegram.TdlAccountNotices
import eu.tintera.plume.core.telegram.TdlAccountRepository
import eu.tintera.plume.core.telegram.TdlAiTextRepository
import eu.tintera.plume.core.telegram.TdlAutoDownloadPresets
import eu.tintera.plume.core.telegram.TdlAutosaveRepository
import eu.tintera.plume.core.telegram.TdlCallRepository
import eu.tintera.plume.core.telegram.TdlChatCreation
import eu.tintera.plume.core.telegram.TdlChatFolderRepository
import eu.tintera.plume.core.telegram.TdlChatRepository
import eu.tintera.plume.core.telegram.TdlConnectionRepository
import eu.tintera.plume.core.telegram.TdlContactSync
import eu.tintera.plume.core.telegram.TdlConversationRepository
import eu.tintera.plume.core.telegram.TdlCustomEmojiRepository
import eu.tintera.plume.core.telegram.TdlDefaultAutoDeleteSettings
import eu.tintera.plume.core.telegram.TdlDeviceRepository
import eu.tintera.plume.core.telegram.TdlDirectTopicsRepository
import eu.tintera.plume.core.telegram.TdlDownloadsRepository
import eu.tintera.plume.core.telegram.TdlEmojiRepository
import eu.tintera.plume.core.telegram.TdlFileRepository
import eu.tintera.plume.core.telegram.TdlForumRepository
import eu.tintera.plume.core.telegram.TdlGifRepository
import eu.tintera.plume.core.telegram.TdlInstantViewRepository
import eu.tintera.plume.core.telegram.TdlInviteLinkRepository
import eu.tintera.plume.core.telegram.TdlLiveLocationRepository
import eu.tintera.plume.core.telegram.TdlLiveStoryRepository
import eu.tintera.plume.core.telegram.TdlLiveUploads
import eu.tintera.plume.core.telegram.TdlNotificationRepository
import eu.tintera.plume.core.telegram.TdlNotificationSettingsRepository
import eu.tintera.plume.core.telegram.TdlNotificationSoundRepository
import eu.tintera.plume.core.telegram.TdlPasswordRepository
import eu.tintera.plume.core.telegram.TdlPlaceRepository
import eu.tintera.plume.core.telegram.TdlPrivacyRepository
import eu.tintera.plume.core.telegram.TdlProfileRepository
import eu.tintera.plume.core.telegram.TdlProxyRepository
import eu.tintera.plume.core.telegram.TdlQuickReaction
import eu.tintera.plume.core.telegram.TdlSavedTopicsRepository
import eu.tintera.plume.core.telegram.TdlSensitiveContentSettings
import eu.tintera.plume.core.telegram.TdlStatisticsRepository
import eu.tintera.plume.core.telegram.TdlStickerRepository
import eu.tintera.plume.core.telegram.TdlStorageRepository
import eu.tintera.plume.core.telegram.TdlStoryRepository
import eu.tintera.plume.core.telegram.TdlTelegramSession
import eu.tintera.plume.core.telegram.TdlThreadDrafts
import eu.tintera.plume.core.telegram.TdlTopicDrafts
import eu.tintera.plume.core.telegram.ThreadDrafts
import eu.tintera.plume.core.telegram.TdlVideoChatRepository
import eu.tintera.plume.core.telegram.TdlWebAppRepository
import eu.tintera.plume.core.telegram.TelegramClientHolder
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.TopicDrafts
import eu.tintera.plume.core.telegram.VideoChatRepository
import eu.tintera.plume.core.telegram.WebAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Scope pro věci, které přežívají obrazovky (TDLib session, seznam chatů). Vědomě není vázaný
 * na `viewModelScope` — kdyby byl, seznam chatů by se rušil a znovu skládal při každé navigaci
 * a přišli bychom o živé updaty.
 */
const val APP_SCOPE = "appScope"

/**
 * Vrstva kolem TDLib pro Koin: klient, přihlášení a všechny repository `Tdl…`, i s eager odběry.
 *
 * - **Vstupní bod ji jen zahrne** (`includes(coreModule())`) a dostane hotovou vrstvu kolem TDLib
 *   bez UI telefonu — tak ji použije i `wearApp`. Platformní část (cesty TDLib, síť, popředí,
 *   klíč databáze) dodá [corePlatformModule] sám.
 * - **Hovory jsou volitelné:** `CallMedia`, `CallPointerDisplay` a `CallJournal` registruje
 *   aplikace (ntgcalls do `core` nepatří); bez nich `CallRepository.supported` říká, že ne.
 * - **Eager definice (`createdAtStart`) jsou nutnost, ne optimalizace** — viz komentáře u nich
 *   a „Repository seznamu chatů MUSÍ vznikat eagerně" v CLAUDE.md.
 */
fun coreModule(): Module = module {
    includes(corePlatformModule())

    single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Jeden TDLib klient na proces; sdílí ho session i chat repository.
    single { TelegramClientHolder() }

    single<TelegramSession> {
        TdlTelegramSession(
            holder = get(),
            paths = get(),
            keys = get(),
            scope = get(named(APP_SCOPE)),
        )
    }

    // `createdAtStart` je tu nutnost, ne optimalizace. Repository se v konstruktoru přihlašuje
    // k odběru chat updatů; kdyby vznikla líně (až se poprvé složí obrazovka se seznamem),
    // stihne to o stovky milisekund později než `AuthorizationStateReady` a první dávku
    // `UpdateNewChat` by TDLib rozeslal do prázdna.
    // Eagerně: styly psaní chodí jen updatem po přihlášení (viz `TdlAiTextRepository`).
    single<ContactSync> { TdlContactSync(holder = get()) }

    single<AiTextRepository>(createdAtStart = true) { TdlAiTextRepository(holder = get(), scope = get(named(APP_SCOPE))) }

    // Eagerně: odběr `updateFileGenerationStart` musí běžet dřív než první nahrávání (viz `TdlLiveUploads`).
    single<LiveUploads>(createdAtStart = true) { TdlLiveUploads(holder = get(), scope = get(named(APP_SCOPE))) }

    single<ChatRepository>(createdAtStart = true) {
        TdlChatRepository(
            holder = get(),
            session = get(),
            scope = get(named(APP_SCOPE)),
        )
    }

    // Eagerně: kdo má příběh, TDLib pošle spolu s chaty hned po přihlášení (viz `TdlStoryRepository`).
    single<LiveStoryRepository> { TdlLiveStoryRepository(holder = get()) }

    single<VideoChatRepository> { TdlVideoChatRepository(holder = get()) }

    single<StoryRepository>(createdAtStart = true) {
        TdlStoryRepository(holder = get(), session = get(), scope = get(named(APP_SCOPE)))
    }

    // Eager ze stejného důvodu: zmrazení účtu a podmínky služby TDLib pošle hned po startu
    // a oznámení od služby jen jednou — odběr, který vznikne až s obrazovkou, by je minul.
    single<AccountNotices>(createdAtStart = true) {
        TdlAccountNotices(holder = get(), scope = get(named(APP_SCOPE)))
    }

    // Eager ze stejného důvodu: nové zprávy chodí updatem, ukládání do galerie je nesmí minout.
    single<AutosaveRepository>(createdAtStart = true) {
        TdlAutosaveRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    single<NotificationSoundRepository> { TdlNotificationSoundRepository(holder = get()) }

    single<DownloadsRepository> { TdlDownloadsRepository(holder = get()) }

    // Eager: příchozí hovor chodí updatem a nesmí se minout. Mediální vrstva je jen tam,
    // kde ji platformní modul aplikace zaregistruje (`CallMedia`); jinde jsou hovory nedostupné.
    single<CallRepository>(createdAtStart = true) {
        TdlCallRepository(
            holder = get(),
            media = getOrNull<CallMedia>(),
            scope = get(named(APP_SCOPE)),
            network = getOrNull<NetworkMonitor>()?.status,
            pointerDisplay = getOrNull<CallPointerDisplay>(),
            journal = getOrNull<CallJournal>(),
            foreground = getOrNull<AppForeground>()?.isForeground,
            applicationVersion = getOrNull<TelegramPaths>()?.applicationVersion.orEmpty(),
        )
    }

    // Eager ze stejného důvodu: `UpdateForumTopic` s konceptem chodí i bez otevřeného přehledu.
    single<TopicDrafts>(createdAtStart = true) {
        TdlTopicDrafts(holder = get(), chats = get(), scope = get(named(APP_SCOPE)))
    }
    // Koncepty ve vláknech komentářů updatem nechodí, takže eager být nemusí.
    single<ThreadDrafts> { TdlThreadDrafts(holder = get()) }

    // Bezstavová — na rozdíl od seznamu chatů nemusí vznikat dopředu, stránkovací stav
    // drží ConversationViewModel.
    single<ConversationRepository> { TdlConversationRepository(holder = get(), savedTopics = get(), directTopics = get()) }

    single<ForumRepository> { TdlForumRepository(holder = get()) }

    // Eagerně: témata Uložených zpráv chodí jen updaty, dotaz na seznam TDLib nemá.
    single<SavedTopicsRepository>(createdAtStart = true) {
        TdlSavedTopicsRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    // Eagerně: vlákna přímých zpráv kanálu chodí taky jen updaty.
    single<DirectTopicsRepository>(createdAtStart = true) {
        TdlDirectTopicsRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    single<WebAppRepository> { TdlWebAppRepository(holder = get()) }

    // Eagerně: v konstruktoru se přihlašuje k `fileUpdates` a průběh stahování chodí pro všechny
    // soubory. Při líné konstrukci by se ztratily updaty k souborům, jejichž stahování už běží.
    single<FileRepository>(createdAtStart = true) {
        TdlFileRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    // Eagerně: v konstruktoru se odebírá `havePendingNotificationsUpdates`, který chodí jen na
    // změnu. Presenter si ho sice vyžádá ze `startKoin` taky, ale na tom pořadí nemá co viset.
    single<NotificationRepository>(createdAtStart = true) {
        TdlNotificationRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    // Eagerně ze stejného důvodu jako FileRepository: v konstruktoru se přihlašuje k odběru
    // `connectionStateUpdates`. Kdyby vznikla až s první obrazovkou, prošvihla by přesně ty
    // změny stavu, které se dějí při startu.
    single<ConnectionRepository>(createdAtStart = true) {
        TdlConnectionRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    single {
        ConnectionController(
            connection = get(),
            network = get(),
            foreground = get(),
            scope = get(named(APP_SCOPE)),
        )
    }

    // Bezstavová — seznamy jsou toky nad updaty, drží se jen po dobu otevřeného výběru.
    single<StickerRepository> { TdlStickerRepository(holder = get()) }

    single<CustomEmojiRepository> { TdlCustomEmojiRepository(holder = get(), scope = get(named(APP_SCOPE))) }

    single<GifRepository> { TdlGifRepository(holder = get()) }

    single<PlaceRepository> { TdlPlaceRepository(holder = get()) }

    // Eagerně: seznam živých poloh ke sdílení posílá TDLib jednou po startu (a pak na změnu).
    single<LiveLocationRepository>(createdAtStart = true) {
        TdlLiveLocationRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    single<EmojiRepository> { TdlEmojiRepository(holder = get()) }

    // Eagerně: výchozí reakci i seznam emoji TDLib ohlásí jen updatem po přihlášení.
    single<QuickReaction>(createdAtStart = true) {
        TdlQuickReaction(holder = get(), scope = get(named(APP_SCOPE)))
    }

    single<InstantViewRepository> { TdlInstantViewRepository(holder = get()) }

    single { RevealedSpoilers() }

    single { SelfDestructTimers() }

    single<ProfileRepository> { TdlProfileRepository(holder = get()) }

    single<InviteLinkRepository> { TdlInviteLinkRepository(holder = get()) }

    single<StatisticsRepository> { TdlStatisticsRepository(holder = get()) }

    single<ChatFolderRepository> { TdlChatFolderRepository(holder = get()) }

    single<NotificationSettingsRepository> { TdlNotificationSettingsRepository(holder = get()) }

    single<SensitiveContentSettings> { TdlSensitiveContentSettings(holder = get()) }

    single<PrivacyRepository> { TdlPrivacyRepository(holder = get()) }

    single<DefaultAutoDeleteSettings> { TdlDefaultAutoDeleteSettings(holder = get()) }

    single<PasswordRepository> { TdlPasswordRepository(holder = get()) }

    single<ChatCreation> { TdlChatCreation(holder = get()) }

    single<AccountRepository> { TdlAccountRepository(holder = get()) }

    single<ProxyRepository> { TdlProxyRepository(holder = get()) }

    single<StorageRepository> { TdlStorageRepository(holder = get()) }

    single<AutoDownloadPresets> { TdlAutoDownloadPresets(holder = get()) }

    // Eager: nepotvrzené přihlášení TDLib ohlásí hned po startu (viz TdlDeviceRepository).
    single<DeviceRepository>(createdAtStart = true) {
        TdlDeviceRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }
}

/** Platformní část [coreModule]: cesty TDLib, sledování sítě a popředí, úložiště klíče databáze. */
internal expect fun corePlatformModule(): Module
