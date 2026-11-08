package eu.tintera.plume.core.di

import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.calls.CallMedia
import eu.tintera.plume.core.calls.CallPointerDisplay
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.secrets.DatabaseKeyStore
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
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
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
 * - **Skládá se z globální části a kontejnerů účtů** (`docs/navrh-vice-uctu.md`): globální
 *   část ([coreGlobalModule]) je v tomhle Koinu, část účtu ([accountModule]) v izolovaném
 *   kontejneru každého účtu ([Accounts]). Zatím je účet jen primární a zbytek aplikace ho
 *   dostává přes most ([accountBridgeModule]), dokud routy nenesou slot.
 */
fun coreModule(): Module = module {
    includes(coreGlobalModule(), accountBridgeModule())

    // Eager: v kontejneru účtu vznikají eager repository, které musí odebírat updaty dřív, než
    // TDLib pošle první dávku (viz `Accounts`).
    single(createdAtStart = true) {
        val global = getKoin()
        Accounts { slot -> koinApplication(createEagerInstances = true) { modules(accountModule(slot, global)) } }
    }
}

/**
 * Co je v procesu jednou, ať je účtů kolik chce: scope aplikace, sledování sítě a popředí.
 * Nic odsud nesmí sáhnout na definici z [accountModule] — globální část nemá účet, ke kterému
 * by ji vztáhla.
 */
internal fun coreGlobalModule(): Module = module {
    includes(corePlatformModule())

    single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}

/**
 * Všechno, co patří jednomu účtu ([slot]): jeho klient TDLib, cesty a klíč databáze, přihlášení
 * a všechny repository. Žije v izolovaném kontejneru účtu (`Accounts`); z globálního Koinu
 * ([global]) bere jen to, co mu podá [globalBridgeModule].
 *
 * `ConnectionController` je tady, ne v globální části: typ sítě a příznak online se posílají
 * klientovi TDLib, a ten je u každého účtu jiný. Stejně tak `RevealedSpoilers`
 * a `SelfDestructTimers` — drží id zpráv a souborů, a ta jsou čísla jednoho klienta.
 */
internal fun accountModule(slot: AccountSlot, global: Koin): Module = module {
    includes(globalBridgeModule(global), accountPlatformModule(slot, global))

    // Jeden TDLib klient na účet; sdílí ho session i chat repository.
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

/** Platformní část [coreGlobalModule]: sledování sítě a popředí. */
internal expect fun corePlatformModule(): Module

/**
 * Platformní část [accountModule]: cesty TDLib a úložiště klíče databáze účtu [slot]. Co
 * potřebuje z platformy (na Androidu `Context`), bere z globálního Koinu ([global]).
 */
internal expect fun accountPlatformModule(slot: AccountSlot, global: Koin): Module

/**
 * Co kontejner účtu bere z globálního Koinu: scope aplikace, síť, popředí a mediální vrstvu
 * hovorů, kterou registruje aplikace. Izolovaný kontejner globální definice nevidí, takže
 * každou, kterou [accountModule] potřebuje, musí podat tenhle modul.
 *
 * **Volitelné definice hovorů se přebírají jen, když je aplikace má** (`getOrNull` hned při
 * skládání): definice, která by v kontejneru existovala a vracela chybu, by pokazila
 * `getOrNull` v `TdlCallRepository`, podle kterého se hovory nabízejí.
 */
internal fun globalBridgeModule(global: Koin): Module = module {
    single(named(APP_SCOPE)) { global.get<CoroutineScope>(named(APP_SCOPE)) }
    single { global.get<NetworkMonitor>() }
    single { global.get<AppForeground>() }
    global.getOrNull<CallMedia>()?.let { media -> single<CallMedia> { media } }
    global.getOrNull<CallPointerDisplay>()?.let { display -> single<CallPointerDisplay> { display } }
    global.getOrNull<CallJournal>()?.let { journal -> single { journal } }
}

/**
 * **Dočasný most** z globálního Koinu do kontejneru primárního účtu: zbytek aplikace (ViewModely,
 * `koinInject`, služby) se dnes ptá globálního Koinu a slot zatím nenese. Zmizí, až ho ponesou
 * routy a vstupní body (návrh, „Účet nese routa", krok 4 v „Postup").
 *
 * **Musí zrcadlit VŠECHNY definice [accountModule]** kromě těch z [globalBridgeModule]; chybějící
 * by se projevila až za běhu („No definition found"). Hlídá to `AccountBridgeTest`.
 */
internal fun accountBridgeModule(): Module = module {
    single<TelegramPaths> { primary().get() }
    single<DatabaseKeyStore> { primary().get() }
    single<TelegramClientHolder> { primary().get() }
    single<TelegramSession> { primary().get() }
    single<ContactSync> { primary().get() }
    single<AiTextRepository> { primary().get() }
    single<LiveUploads> { primary().get() }
    single<ChatRepository> { primary().get() }
    single<LiveStoryRepository> { primary().get() }
    single<VideoChatRepository> { primary().get() }
    single<StoryRepository> { primary().get() }
    single<AccountNotices> { primary().get() }
    single<AutosaveRepository> { primary().get() }
    single<NotificationSoundRepository> { primary().get() }
    single<DownloadsRepository> { primary().get() }
    single<CallRepository> { primary().get() }
    single<TopicDrafts> { primary().get() }
    single<ThreadDrafts> { primary().get() }
    single<ConversationRepository> { primary().get() }
    single<ForumRepository> { primary().get() }
    single<SavedTopicsRepository> { primary().get() }
    single<DirectTopicsRepository> { primary().get() }
    single<WebAppRepository> { primary().get() }
    single<FileRepository> { primary().get() }
    single<NotificationRepository> { primary().get() }
    single<ConnectionRepository> { primary().get() }
    single<ConnectionController> { primary().get() }
    single<StickerRepository> { primary().get() }
    single<CustomEmojiRepository> { primary().get() }
    single<GifRepository> { primary().get() }
    single<PlaceRepository> { primary().get() }
    single<LiveLocationRepository> { primary().get() }
    single<EmojiRepository> { primary().get() }
    single<QuickReaction> { primary().get() }
    single<InstantViewRepository> { primary().get() }
    single<RevealedSpoilers> { primary().get() }
    single<SelfDestructTimers> { primary().get() }
    single<ProfileRepository> { primary().get() }
    single<InviteLinkRepository> { primary().get() }
    single<StatisticsRepository> { primary().get() }
    single<ChatFolderRepository> { primary().get() }
    single<NotificationSettingsRepository> { primary().get() }
    single<SensitiveContentSettings> { primary().get() }
    single<PrivacyRepository> { primary().get() }
    single<DefaultAutoDeleteSettings> { primary().get() }
    single<PasswordRepository> { primary().get() }
    single<ChatCreation> { primary().get() }
    single<AccountRepository> { primary().get() }
    single<ProxyRepository> { primary().get() }
    single<StorageRepository> { primary().get() }
    single<AutoDownloadPresets> { primary().get() }
    single<DeviceRepository> { primary().get() }
}

/** Kontejner primárního účtu pro [accountBridgeModule]. */
private fun Scope.primary(): AccountSession = get<Accounts>().primary
