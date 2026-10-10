package eu.tintera.plume.di

import eu.tintera.plume.features.conversation.ConversationJumpRequests
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.features.forward.ShareContactViewModel
import eu.tintera.plume.features.profile.AddMembersViewModel
import eu.tintera.plume.features.forward.BotShareViewModel
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.features.profile.JoinRequestsViewModel
import eu.tintera.plume.features.profile.InviteLinksViewModel
import eu.tintera.plume.features.settings.EditProfileViewModel
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.core.telegram.TdlAccountRepository
import eu.tintera.plume.core.telegram.TdlProxyRepository
import eu.tintera.plume.core.telegram.ProxyRepository
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.features.profile.ChatProfileViewModel
import eu.tintera.plume.features.forum.ForumTopicsViewModel
import eu.tintera.plume.features.saved.SavedTopicsViewModel
import eu.tintera.plume.features.webapp.WebAppViewModel
import eu.tintera.plume.core.telegram.WebAppRepository
import eu.tintera.plume.core.telegram.TdlWebAppRepository
import eu.tintera.plume.features.profile.EditChatViewModel
import eu.tintera.plume.features.profile.MemberRightsViewModel
import eu.tintera.plume.core.telegram.TdlProfileRepository
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.telegram.ChatFolderRepository
import eu.tintera.plume.core.telegram.TdlChatFolderRepository
import eu.tintera.plume.core.telegram.TdlInviteLinkRepository
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.DeviceRepository
import eu.tintera.plume.core.telegram.PasswordRepository
import eu.tintera.plume.core.telegram.TdlPasswordRepository
import eu.tintera.plume.core.telegram.NotificationSettingsRepository
import eu.tintera.plume.core.telegram.PrivacyRepository
import eu.tintera.plume.core.telegram.SensitiveContentSettings
import eu.tintera.plume.core.telegram.TdlPrivacyRepository
import eu.tintera.plume.features.settings.AccountPrivacyViewModel
import eu.tintera.plume.features.settings.PrivacySettingViewModel
import eu.tintera.plume.core.telegram.DefaultAutoDeleteSettings
import eu.tintera.plume.core.telegram.TdlDefaultAutoDeleteSettings
import eu.tintera.plume.core.telegram.TdlSensitiveContentSettings
import eu.tintera.plume.core.telegram.TdlNotificationSettingsRepository
import eu.tintera.plume.core.telegram.TdlDeviceRepository
import eu.tintera.plume.features.devices.DevicesViewModel
import eu.tintera.plume.features.settings.StorageViewModel
import eu.tintera.plume.core.storage.StorageCleaner
import eu.tintera.plume.core.settings.AutoDownloadSync
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.TdlStorageRepository
import eu.tintera.plume.features.conversation.ConversationReplyRequests
import eu.tintera.plume.features.conversation.InlineQueryRequests
import eu.tintera.plume.features.conversation.SwitchInlinePickerViewModel
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.navigation.NO_STORY
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.TdlConnectionRepository
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.telegram.EmojiRepository
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.CustomEmojiRepository
import eu.tintera.plume.core.telegram.TdlCustomEmojiRepository
import eu.tintera.plume.core.telegram.GifRepository
import eu.tintera.plume.core.telegram.PlaceRepository
import eu.tintera.plume.core.telegram.TdlPlaceRepository
import eu.tintera.plume.core.telegram.LiveLocationRepository
import eu.tintera.plume.core.telegram.TdlLiveLocationRepository
import eu.tintera.plume.core.telegram.TdlGifRepository
import eu.tintera.plume.core.telegram.TdlEmojiRepository
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.core.telegram.TdlQuickReaction
import eu.tintera.plume.core.telegram.TdlTopicDrafts
import eu.tintera.plume.core.telegram.TopicDrafts
import eu.tintera.plume.core.telegram.AutoDownloadPresets
import eu.tintera.plume.core.telegram.TdlAutoDownloadPresets
import eu.tintera.plume.core.telegram.TdlStickerRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.RevealedSpoilers
import eu.tintera.plume.core.telegram.SelfDestructTimers
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.notifications.NotificationActions
import eu.tintera.plume.core.telegram.TdlChatRepository
import eu.tintera.plume.core.telegram.TdlConversationRepository
import eu.tintera.plume.core.telegram.TdlForumRepository
import eu.tintera.plume.core.telegram.TdlSavedTopicsRepository
import eu.tintera.plume.core.telegram.SavedTopicsRepository
import eu.tintera.plume.core.telegram.ForumRepository
import eu.tintera.plume.core.telegram.TdlFileRepository
import eu.tintera.plume.core.telegram.TdlNotificationRepository
import eu.tintera.plume.core.telegram.TdlTelegramSession
import eu.tintera.plume.core.telegram.TelegramClientHolder
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.features.auth.AuthViewModel
import eu.tintera.plume.features.chats.ChatsViewModel
import eu.tintera.plume.features.chats.NowPlayingViewModel
import eu.tintera.plume.features.chats.AddBotToGroupViewModel
import eu.tintera.plume.features.chats.NewChatViewModel
import eu.tintera.plume.features.chats.CreateChatViewModel
import eu.tintera.plume.core.telegram.ChatCreation
import eu.tintera.plume.core.telegram.TdlChatCreation
import eu.tintera.plume.features.conversation.ConversationViewModel
import eu.tintera.plume.core.telegram.ThreadConversationRepository
import eu.tintera.plume.features.conversation.StickerPickerViewModel
import eu.tintera.plume.features.conversation.GifPickerViewModel
import eu.tintera.plume.features.conversation.StickerSetViewModel
import eu.tintera.plume.features.conversation.PollVotersViewModel
import eu.tintera.plume.features.conversation.PollComposerViewModel
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.features.media.MediaViewerViewModel
import eu.tintera.plume.features.share.ShareToChatViewModel
import eu.tintera.plume.features.share.ShareViewModel
import eu.tintera.plume.features.settings.AppearanceViewModel
import eu.tintera.plume.features.settings.AutoDownloadViewModel
import eu.tintera.plume.core.connectivity.AutoDownloadPolicy
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.features.settings.ChatSwipeSettingsViewModel
import eu.tintera.plume.features.settings.QuickReactionSettingsViewModel
import eu.tintera.plume.features.settings.SettingsViewModel
import eu.tintera.plume.features.settings.BlockSenderViewModel
import eu.tintera.plume.features.settings.BlockedSendersViewModel
import eu.tintera.plume.features.settings.ChatFolderEditViewModel
import eu.tintera.plume.features.settings.ChatFoldersViewModel
import eu.tintera.plume.features.proxy.ProxySettingsViewModel
import eu.tintera.plume.features.proxy.ProxyStatusViewModel
import eu.tintera.plume.features.settings.SensitiveContentViewModel
import eu.tintera.plume.features.settings.DefaultAutoDeleteViewModel
import eu.tintera.plume.features.settings.TwoStepViewModel
import eu.tintera.plume.features.settings.AppLockViewModel
import eu.tintera.plume.features.settings.NotificationSettingsViewModel
import eu.tintera.plume.features.settings.OnDeviceAiSettingsViewModel
import eu.tintera.plume.features.conversation.ChatSummaryViewModel
import eu.tintera.plume.features.conversation.ReplySuggestionsViewModel
import eu.tintera.plume.features.conversation.EmojiSuggestionsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.audio.MusicQueue
import eu.tintera.plume.features.forward.ForwardViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Platformní část grafu — na Androidu potřebuje Context, na iOS ne. */
expect fun Module.platformModule()

/**
 * Scope pro věci, které přežívají obrazovky (TDLib session, seznam chatů). Vědomě není vázaný
 * na `viewModelScope` — kdyby byl, seznam chatů by se rušil a znovu skládal při každé navigaci
 * a přišli bychom o živé updaty.
 */
internal const val APP_SCOPE = "appScope"

fun appModule() = module {
    platformModule()

    single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { KeyboardShortcuts() }
    single(createdAtStart = true) { AutoDownloadPolicy(settings = get(), network = get(), scope = get(named(APP_SCOPE))) }

    // Jeden TDLib klient na proces; sdílí ho session i chat repository.
    single { TelegramClientHolder() }
    // Ořez nové profilovky drží stav pro jednu obrazovku, proto factory.
    factory { ProfilePhotoCrop(squarePhoto = get(), profileVideo = get()) }

    single<TelegramSession> {
        TdlTelegramSession(
            holder = get(),
            paths = get(),
            keys = get(),
            scope = get(named(APP_SCOPE)),
        )
    }

    // Zámek aplikace musí znát stav dřív, než se složí první obrazovka, a sledovat popředí
    // i bez UI — proto eager. Klíč k databázi s ním nesouvisí, push jde dál bez ověření.
    single(createdAtStart = true) {
        AppLock(store = get(), scope = get(named(APP_SCOPE))).also {
            it.start(foreground = get<AppForeground>().isForeground, authStage = get<TelegramSession>().authStage)
        }
    }

    // `createdAtStart` je tu nutnost, ne optimalizace. Repository se v konstruktoru přihlašuje
    // k odběru chat updatů; kdyby vznikla líně (až se poprvé složí obrazovka se seznamem),
    // stihne to o stovky milisekund později než `AuthorizationStateReady` a první dávku
    // `UpdateNewChat` by TDLib rozeslal do prázdna.
    single<ChatRepository>(createdAtStart = true) {
        TdlChatRepository(
            holder = get(),
            session = get(),
            scope = get(named(APP_SCOPE)),
        )
    }
    // Eager ze stejného důvodu: `UpdateForumTopic` s konceptem chodí i bez otevřeného přehledu.
    single<TopicDrafts>(createdAtStart = true) {
        TdlTopicDrafts(holder = get(), chats = get(), scope = get(named(APP_SCOPE)))
    }

    // Bezstavová — na rozdíl od seznamu chatů nemusí vznikat dopředu, stránkovací stav
    // drží ConversationViewModel.
    single<ConversationRepository> { TdlConversationRepository(holder = get(), savedTopics = get()) }
    single<ForumRepository> { TdlForumRepository(holder = get()) }
    // Eagerně: témata Uložených zpráv chodí jen updaty, dotaz na seznam TDLib nemá.
    single<SavedTopicsRepository>(createdAtStart = true) {
        TdlSavedTopicsRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }
    single<WebAppRepository> { TdlWebAppRepository(holder = get()) }

    // Odpověď z notifikace. Nad ConversationRepository, ne v něm: jde o pořadí kroků (odeslat,
    // dočkat se potvrzení, pak teprve přečíst), ne o další volání TDLib.
    single { NotificationActions(session = get(), conversations = get(), chats = get(), notifications = get()) }

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

    // Eagerně: nastavení se načte při startu, aby ho první obrazovka měla hned (viz AppSettings).
    single<AppSettings>(createdAtStart = true) {
        DataStoreAppSettings(dataStore = get(), scope = get(named(APP_SCOPE)))
    }

    // Jeden přehrávač hlasovek na aplikaci: hraje nejvýš jedna. Na hlavním vlákně, protože
    // systémové přehrávače se ovládají z něj.
    single {
        VoicePlayer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            // Nový engine na každou hlasovku; platformní konstruktor dodá Koin (Android chce Context).
            newEngine = { get() },
            settings = get(),
            earSensor = get(),
        )
    }

    // Hudba: fronta skladeb a systémové ovládání. Na týchž vláknech jako přehrávač — spoléhá na
    // to, že konec skladby a spuštění další uvidí ve stejném pořadí, v jakém se staly.
    single {
        MusicQueue(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            player = get(),
            conversations = get(),
            files = get(),
        )
    }

    // Most mezi platformním vstupem (ťuknutí na notifikaci) a navigací.
    single { ChatOpenRequests() }
    single { BotStartRequests() }
    single { ConversationSearchRequests() }
    single { ConversationJumpRequests() }
    single { MessageBookmarks(settings = get(), chats = get()) }
    single { ChatSnoozes(settings = get(), chats = get(), reminders = getOrNull()) }
    single { ConversationReplyRequests() }
    single { InlineQueryRequests() }
    single { RevealedSpoilers() }
    single { SelfDestructTimers() }
    single<ProfileRepository> { TdlProfileRepository(holder = get()) }
    single<InviteLinkRepository> { TdlInviteLinkRepository(holder = get()) }
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
    single {
        AutoDownloadSync(
            settings = get(),
            presets = get(),
            authStage = get<TelegramSession>().authStage,
            scope = get(named(APP_SCOPE)),
        )
    }
    single {
        StorageCleaner(
            repository = get(),
            settings = get(),
            session = get(),
            foreground = get(),
            scope = get(named(APP_SCOPE)),
        )
    }
    // Eager: nepotvrzené přihlášení TDLib ohlásí hned po startu (viz TdlDeviceRepository).
    single<DeviceRepository>(createdAtStart = true) {
        TdlDeviceRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }
    single { ShareRequests() }
    // Eager: po přihlášení zaregistruje Uložené zprávy jako cíl sdílení (viz init).
    single(createdAtStart = true) {
        ShareTargets(repository = get(), chats = get(), files = get(), publisher = get(), scope = get(named(APP_SCOPE)))
    }

    viewModelOf(::AuthViewModel)
    viewModelOf(::NowPlayingViewModel)
    // Složka je parametr: hlavní seznam i archiv jsou tentýž ViewModel. Bez parametru hlavní.
    viewModel { parameters ->
        ChatsViewModel(
            folder = parameters.getOrNull<ChatFolder>() ?: ChatFolder.Main,
            repository = get(),
            connection = get(),
            settings = get(),
            appUpdates = get(),
            devices = get(),
            accounts = get(),
            savedState = get(),
            folderRepository = get(),
            topicDrafts = get(),
            snoozes = get(),
        )
    }
    viewModelOf(::StickerPickerViewModel)
    viewModel { params -> GifPickerViewModel(chatId = params.get(), repository = get()) }
    viewModel { parameters -> StickerSetViewModel(setId = parameters.get(), repository = get()) }
    viewModel { parameters ->
        // Indexem: odpověď (`Long?`) by se podle typu zaměnila s chatId.
        PollComposerViewModel(
            chatId = parameters.values[0] as Long,
            mode = parameters.values.getOrNull(1) as? PollComposerMode ?: PollComposerMode.Any,
            replyToMessageId = parameters.values.getOrNull(2) as? Long,
            replyQuote = parameters.values.getOrNull(3) as? ReplyQuote,
            repository = get(),
            session = get(),
            placeRepository = get(),
            replyRequests = get(),
        )
    }
    // Dvě `Long` za sebou: parametry se čtou INDEXEM, podle typu by se chatId s messageId zaměnily.
    viewModel { parameters ->
        PollVotersViewModel(
            chatId = parameters.values[0] as Long,
            messageId = parameters.values[1] as Long,
            answerIndexes = @Suppress("UNCHECKED_CAST") (parameters.values[2] as List<Int>),
            repository = get(),
        )
    }
    viewModelOf(::SettingsViewModel)
    viewModelOf(::BlockedSendersViewModel)
    viewModelOf(::ChatFoldersViewModel)
    viewModelOf(::ProxySettingsViewModel)
    viewModelOf(::ProxyStatusViewModel)
    // Id složky je Int a nic jiného téhož typu parametry nemají, `get()` ho nezamění.
    viewModel { parameters -> ChatFolderEditViewModel(folderId = parameters.get(), chats = get(), folders = get(), clipboard = get(), sharer = get()) }
    viewModelOf(::BlockSenderViewModel)
    viewModelOf(::SensitiveContentViewModel)
    viewModelOf(::AccountPrivacyViewModel)
    viewModel { parameters ->
        PrivacySettingViewModel(setting = parameters.get(), privacy = get(), chats = get())
    }
    viewModelOf(::DefaultAutoDeleteViewModel)
    viewModelOf(::TwoStepViewModel)
    viewModelOf(::AppLockViewModel)
    viewModelOf(::AppearanceViewModel)
    viewModelOf(::ChatSwipeSettingsViewModel)
    viewModel { QuickReactionSettingsViewModel(settings = get(), quickReaction = get(), appScope = get(named(APP_SCOPE))) }
    viewModelOf(::DevicesViewModel)
    viewModelOf(::StorageViewModel)
    viewModelOf(::AutoDownloadViewModel)
    viewModelOf(::NotificationSettingsViewModel)
    viewModelOf(::EditProfileViewModel)
    // Model v zařízení (core/ai). Stahování modelu běží v aplikačním rozsahu — trvá minuty.
    viewModel { OnDeviceAiSettingsViewModel(settings = get(), ai = get(), appScope = get(named(APP_SCOPE))) }
    viewModelOf(::ChatSummaryViewModel)
    viewModelOf(::ReplySuggestionsViewModel)
    viewModelOf(::EmojiSuggestionsViewModel)

    // chatId přichází z navigační routy, ne z grafu → runtime parametr. Druhý parametr je zpráva,
    // na kterou se po otevření skočí; bývá `null`, a podle typu by se spletl s chatId — proto index.
    // Třetí parametr je vlákno komentářů (kořenová zpráva); s ním dostane konverzace repository
    // zúžené na vlákno.
    viewModel { parameters ->
        val threadRootId = parameters.values.getOrNull(2) as Long?
        val forumTopicId = parameters.values.getOrNull(3) as Int?
        // Pátý: téma Uložených zpráv (podle zdrojového chatu); `null` = všechny uložené zprávy.
        val savedTopicId = parameters.values.getOrNull(4) as Long?
        ConversationViewModel(
            chatId = parameters.get(),
            initialMessageId = parameters.values.getOrNull(1) as Long?,
            threadRootId = threadRootId,
            forumTopicId = forumTopicId,
            savedTopicId = savedTopicId,
            repository = when {
                threadRootId != null -> ThreadConversationRepository(delegate = get(), rootMessageId = threadRootId)
                forumTopicId != null -> get<ConversationRepository>().inForumTopic(forumTopicId)
                savedTopicId != null -> get<ConversationRepository>().inSavedTopic(savedTopicId)
                else -> get()
            },
            chatRepository = get(),
            stickerRepository = get(),
            settings = get(),
            appScope = get(named(APP_SCOPE)),
            urlOpener = get(),
            clipboard = get(),
            languageDetector = get(),
            fileRepository = get(),
            voicePlayer = get(),
            musicQueue = get(),
            audioRecorder = get(),
            fileOpener = get(),
            sharer = get(),
            shareRequests = get(),
            shareTargets = get(),
            connection = get(),
            searchRequests = get(),
            placeRepository = get(),
            botStartRequests = get(),
            replyRequests = get(),
            inlineQueryRequests = get(),
            inviteLinks = get(),
            proxies = get(),
            liveLocations = get(),
            quickReaction = get(),
            bookmarks = get(),
            jumpRequests = get(),
            snoozes = get(),
            photoMarkup = get(),
        )
    }

    viewModel { parameters ->
        ForumTopicsViewModel(
            chatId = parameters.get(),
            forums = get(),
            conversations = get(),
            notifications = get(),
            inviteLinks = get(),
        )
    }

    viewModel { parameters ->
        SavedTopicsViewModel(chatId = parameters.get(), repository = get(), settings = get())
    }

    // Parametry INDEXEM: chat a bot jsou dvě `Long` a podle typu by se zaměnily.
    viewModel { parameters ->
        WebAppViewModel(
            chatId = parameters.values[0] as Long,
            botUserId = parameters.values[1] as Long,
            url = parameters.values[2] as String,
            launch = parameters.values[3] as Boolean,
            keyboardButtonText = parameters.values.getOrNull(4) as? String,
            main = parameters.values.getOrNull(5) as? Boolean ?: false,
            startParameter = parameters.values.getOrNull(6) as? String ?: "",
            shortName = parameters.values.getOrNull(7) as? String ?: "",
            repository = get(),
            settings = get(),
            chats = get(),
            urlOpener = get(),
            appScope = get(named(APP_SCOPE)),
        )
    }

    viewModel { parameters ->
        ChatProfileViewModel(
            chatId = parameters.get(),
            profiles = get(),
            chats = get(),
            conversations = get(),
            files = get(),
            fileOpener = get(),
            urlOpener = get(),
            notificationSettings = get(),
            notificationPermission = get(),
            clipboard = get(),
            voicePlayer = get(),
            musicQueue = get(),
            bookmarks = get(),
        )
    }

    viewModel { parameters ->
        EditChatViewModel(
            chatId = parameters.get(),
            repository = get(),
            photoCrop = get(),
            inviteLinks = get(),
            forums = get(),
            chats = get(),
        )
    }

    viewModel { parameters ->
        InviteLinksViewModel(chatId = parameters.get(), repository = get(), clipboard = get(), sharer = get())
    }

    viewModel { parameters ->
        JoinRequestsViewModel(chatId = parameters.get(), repository = get())
    }

    /*
     * Tři parametry z routy, a dva z nich jsou `Long` — proto se čtou INDEXEM. `get()` bere
     * parametr podle typu, takže by chatId a userId zaměnilo (tatáž past jako u `messageId`
     * konverzace).
     */
    viewModel { parameters ->
        MemberRightsViewModel(
            chatId = parameters.values[0] as Long,
            userId = parameters.values[1] as Long,
            admin = parameters.values[2] as Boolean,
            repository = get(),
        )
    }

    viewModel { ShareViewModel(requests = get(), chatRepository = get()) }

    // Potvrzení sdílení do jednoho chatu (Direct Share): chatId je z routy, tedy runtime parametr.
    viewModel { parameters ->
        ShareToChatViewModel(
            chatId = parameters.get(),
            requests = get(),
            repository = get(),
            shareTargets = get(),
            appScope = get(named(APP_SCOPE)),
            photoMarkup = get(),
        )
    }

    // Zprávy k přeposlání jdou z routy, takže runtime parametry — stejně jako chatId konverzace.
    viewModel { parameters ->
        ForwardViewModel(
            fromChatId = parameters.get(),
            messageIds = parameters.get(),
            repository = get(),
            chatRepository = get(),
            shareTargets = get(),
        )
    }

    viewModel { parameters -> AddMembersViewModel(chatId = parameters.get(), profiles = get(), chats = get()) }

    viewModel { parameters ->
        ShareContactViewModel(userId = parameters.get(), repository = get(), chatRepository = get(), shareTargets = get())
    }

    // Parametry INDEXEM: chat a zpráva jsou dvě `Long`, požadavky bývají `null`.
    viewModel { parameters ->
        BotShareViewModel(
            chatId = parameters.values[0] as Long,
            messageId = parameters.values[1] as Long,
            users = parameters.values[2] as? BotUsersRequest,
            chat = parameters.values[3] as? BotChatRequest,
            repository = get(),
            chatRepository = get(),
        )
    }

    // Parametry z routy. `get()` bez indexu je bere podle pořadí, ve kterém přišly; třetí
    // (příloha ankety) se čte indexem, protože u starších volání chybí.
    viewModel { parameters ->
        MediaViewerViewModel(
            chatId = parameters.get(),
            messageId = parameters.get(),
            pollMedia = parameters.values.getOrNull(2) as? Int ?: NO_POLL_MEDIA,
            profilePhotos = parameters.values.getOrNull(3) as? Boolean ?: false,
            articleMedia = parameters.values.getOrNull(4) as? Int ?: NO_ARTICLE_MEDIA,
            storyId = parameters.values.getOrNull(5) as? Int ?: NO_STORY,
            repository = get(),
            profileRepository = get(),
            files = get(),
            saver = get(),
            sharer = get(),
            timers = get(),
            appScope = get(named(APP_SCOPE)),
        )
    }

    viewModelOf(::NewChatViewModel)

    // Druh a členové jdou z routy; indexem, `get()` by bral podle typu.
    viewModel { parameters ->
        @Suppress("UNCHECKED_CAST")
        CreateChatViewModel(
            channel = parameters.values[0] as Boolean,
            memberChatIds = parameters.values[1] as List<Long>,
            creation = get(),
            profiles = get(),
            photoCrop = get(),
            conversations = get(),
        )
    }

    // Odkaz, bot a jestli chce správce jdou z routy; indexem, `get()` by bral podle typu.
    viewModel { parameters ->
        AddBotToGroupViewModel(
            url = parameters.values[0] as String,
            botChatId = parameters.values[1] as Long,
            asAdmin = parameters.values[2] as Boolean,
            chatRepository = get(),
            conversationRepository = get(),
        )
    }

    viewModel { parameters ->
        SwitchInlinePickerViewModel(target = parameters.get(), chatRepository = get())
    }
}
