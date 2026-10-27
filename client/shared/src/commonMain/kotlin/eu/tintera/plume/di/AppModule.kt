package eu.tintera.plume.di

import eu.tintera.plume.features.settings.FolderSuggestions
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.di.coreModule
import eu.tintera.plume.core.webapp.MiniAppStorage
import eu.tintera.plume.core.webapp.DataStoreMiniAppStorage
import eu.tintera.plume.core.webapp.MiniAppSecureStorage
import eu.tintera.plume.core.webapp.SecretMiniAppSecureStorage
import eu.tintera.plume.core.secrets.MiniAppSecretStore
import eu.tintera.plume.features.stories.StoryEditTarget
import eu.tintera.plume.features.profile.EventLogViewModel
import eu.tintera.plume.features.statistics.ChatStatisticsViewModel
import eu.tintera.plume.features.statistics.PostStatisticsViewModel
import eu.tintera.plume.features.settings.ChangePhoneViewModel
import eu.tintera.plume.features.instantview.InstantViewViewModel
import eu.tintera.plume.features.settings.AutosaveViewModel
import eu.tintera.plume.features.settings.NotificationSoundsViewModel
import eu.tintera.plume.features.settings.NetworkUsageViewModel
import eu.tintera.plume.features.settings.PasskeysViewModel
import eu.tintera.plume.features.settings.DeleteAccountViewModel
import eu.tintera.plume.features.settings.LoginEmailViewModel
import eu.tintera.plume.features.chats.NaturalSearchModel
import eu.tintera.plume.features.conversation.LocalTranscripts
import eu.tintera.plume.features.conversation.OnDeviceTranscription
import eu.tintera.plume.features.media.PhotoDescriptions
import eu.tintera.plume.core.ai.BackgroundPhotoDescription
import eu.tintera.plume.features.media.BackgroundPhotoDescriber
import eu.tintera.plume.features.conversation.ScamWarnings
import eu.tintera.plume.features.chats.ChatTriage
import eu.tintera.plume.features.conversation.MessageContexts
import eu.tintera.plume.core.currency.ExchangeRates
import eu.tintera.plume.core.currency.TextDownloader
import eu.tintera.plume.features.conversation.ReminderTimeModel
import eu.tintera.plume.features.conversation.ConversationJumpRequests
import eu.tintera.plume.features.profile.ProfileTabRequests
import eu.tintera.plume.features.conversation.ChatSwitcher
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.drafts.CommentDraftTracking
import eu.tintera.plume.core.drafts.CommentDrafts
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.video.VideoPositions
import eu.tintera.plume.core.chatcolor.ChatColors
import eu.tintera.plume.features.forward.ShareContactViewModel
import eu.tintera.plume.features.profile.AddMembersViewModel
import eu.tintera.plume.features.forward.BotShareViewModel
import eu.tintera.plume.core.telegram.BotShareSource
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.features.profile.JoinRequestsViewModel
import eu.tintera.plume.features.profile.RemovedMembersViewModel
import eu.tintera.plume.features.profile.InviteLinksViewModel
import eu.tintera.plume.features.settings.EditProfileViewModel
import eu.tintera.plume.features.settings.OwnProfileMusicViewModel
import eu.tintera.plume.core.ui.ProfilePhotoCrop
import eu.tintera.plume.features.profile.ChatProfileViewModel
import eu.tintera.plume.features.profile.ChatAutosaveViewModel
import eu.tintera.plume.features.forum.ForumTopicsViewModel
import eu.tintera.plume.features.saved.SavedTopicsViewModel
import eu.tintera.plume.features.webapp.WebAppViewModel
import eu.tintera.plume.features.webapp.PreparedShareViewModel
import eu.tintera.plume.features.profile.EditChatViewModel
import eu.tintera.plume.features.profile.MemberRightsViewModel
import eu.tintera.plume.features.settings.AccountPrivacyViewModel
import eu.tintera.plume.features.settings.PrivacySettingViewModel
import eu.tintera.plume.features.devices.DevicesViewModel
import eu.tintera.plume.features.settings.StorageViewModel
import eu.tintera.plume.core.storage.StorageCleaner
import eu.tintera.plume.core.settings.AutoDownloadSync
import eu.tintera.plume.features.conversation.ConversationReplyRequests
import eu.tintera.plume.features.conversation.SponsoredLinkRequests
import eu.tintera.plume.features.conversation.InlineQueryRequests
import eu.tintera.plume.features.conversation.SwitchInlinePickerViewModel
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.navigation.InstantViewRequests
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.navigation.NO_STORY
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.navigation.ChannelLimitRequests
import eu.tintera.plume.core.navigation.ContactOffers
import eu.tintera.plume.features.channellimit.ChannelLimitViewModel
import eu.tintera.plume.features.channellimit.PublicLinkLimitViewModel
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.features.conversation.StagedAttachmentsStore
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.calls.CallRequests
import eu.tintera.plume.core.notifications.NotificationActions
import eu.tintera.plume.features.direct.DirectTopicsViewModel
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.features.auth.AuthViewModel
import eu.tintera.plume.features.chats.ChatsViewModel
import eu.tintera.plume.features.notices.AccountNoticesViewModel
import eu.tintera.plume.features.chats.NowPlayingViewModel
import eu.tintera.plume.features.chats.AddBotToGroupViewModel
import eu.tintera.plume.features.chats.NewChatViewModel
import eu.tintera.plume.features.links.ExternalLinkViewModel
import eu.tintera.plume.features.calls.CallsViewModel
import eu.tintera.plume.features.downloads.DownloadsViewModel
import eu.tintera.plume.features.stories.MyStoriesViewModel
import eu.tintera.plume.features.settings.MyQrCodeViewModel
import eu.tintera.plume.features.chats.NewContactViewModel
import eu.tintera.plume.features.chats.CreateChatViewModel
import eu.tintera.plume.features.conversation.ConversationViewModel
import eu.tintera.plume.core.telegram.ThreadConversationRepository
import eu.tintera.plume.features.conversation.StickerPickerViewModel
import eu.tintera.plume.features.conversation.GifPickerViewModel
import eu.tintera.plume.features.conversation.StickerSetViewModel
import eu.tintera.plume.features.conversation.StickerSuggestionsViewModel
import eu.tintera.plume.features.conversation.PollVotersViewModel
import eu.tintera.plume.features.conversation.PollComposerViewModel
import eu.tintera.plume.features.conversation.ChecklistComposerViewModel
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.features.media.MediaViewerViewModel
import eu.tintera.plume.features.stories.StoriesViewModel
import eu.tintera.plume.features.videochat.VideoChatSession
import eu.tintera.plume.features.videochat.VideoChatViewModel
import eu.tintera.plume.features.stories.StoryComposerViewModel
import eu.tintera.plume.features.stickers.OwnedStickerSetViewModel
import eu.tintera.plume.features.stickers.StickerComposerViewModel
import eu.tintera.plume.features.share.ShareToChatViewModel
import eu.tintera.plume.features.settings.AppearanceViewModel
import eu.tintera.plume.features.settings.AutoDownloadViewModel
import eu.tintera.plume.core.connectivity.AutoDownloadPolicy
import eu.tintera.plume.core.files.AutosaveWorker
import eu.tintera.plume.core.files.MediaSaver
import eu.tintera.plume.core.files.autosavesToGallery
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.features.settings.ChatSwipeSettingsViewModel
import eu.tintera.plume.features.settings.QuickReactionSettingsViewModel
import eu.tintera.plume.features.settings.SettingsViewModel
import eu.tintera.plume.features.settings.BlockSenderViewModel
import eu.tintera.plume.features.settings.BlockedSendersViewModel
import eu.tintera.plume.features.settings.ChatFolderEditViewModel
import eu.tintera.plume.features.settings.ChatFoldersViewModel
import eu.tintera.plume.features.settings.StickerSetsViewModel
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
import eu.tintera.plume.features.conversation.DraftRewriteViewModel
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


/** DataStore úložiště mini aplikací (`DeviceStorage`), vedle nastavení aplikace. */
internal const val MINI_APP_STORAGE = "miniAppStorage"

fun appModule() = module {
    // Vrstva kolem TDLib (klient, přihlášení, repository, APP_SCOPE) — client/core, `coreModule()`.
    includes(coreModule())
    platformModule()

    single { KeyboardShortcuts() }
    single(createdAtStart = true) { AutoDownloadPolicy(settings = get(), network = get(), scope = get(named(APP_SCOPE))) }

    // Ořez nové profilovky drží stav pro jednu obrazovku, proto factory.
    factory { ProfilePhotoCrop(squarePhoto = get(), profileVideo = get()) }


    // Zámek aplikace musí znát stav dřív, než se složí první obrazovka, a sledovat popředí
    // i bez UI — proto eager. Klíč k databázi s ním nesouvisí, push jde dál bez ověření.
    single(createdAtStart = true) {
        AppLock(store = get(), scope = get(named(APP_SCOPE))).also {
            it.start(foreground = get<AppForeground>().isForeground, authStage = get<TelegramSession>().authStage)
        }
    }

    single { CallRequests() }
    if (autosavesToGallery) {
        single(createdAtStart = true) {
            val saver = get<MediaSaver>()
            val policy = get<AutoDownloadPolicy>()
            AutosaveWorker(
                autosave = get(),
                files = get(),
                save = { path, fileName, isVideo -> saver.save(path, fileName, isVideo) != null },
                limits = { policy.limits.value },
                scope = get(named(APP_SCOPE)),
            ).also { it.start() }
        }
    }


    // Odpověď z notifikace. Nad ConversationRepository, ne v něm: jde o pořadí kroků (odeslat,
    // dočkat se potvrzení, pak teprve přečíst), ne o další volání TDLib.
    single { NotificationActions(session = get(), conversations = get(), chats = get(), notifications = get()) }






    // Eagerně: nastavení se načte při startu, aby ho první obrazovka měla hned (viz AppSettings).
    single<AppSettings>(createdAtStart = true) {
        DataStoreAppSettings(dataStore = get(), scope = get(named(APP_SCOPE)))
    }
    single<MiniAppStorage> {
        DataStoreMiniAppStorage(get(named(MINI_APP_STORAGE)))
    }
    // Trezor dodává platforma (`MiniAppSecretStore` v platformním modulu).
    single<MiniAppSecureStorage> { SecretMiniAppSecureStorage(store = get<MiniAppSecretStore>()) }

    // Jeden přehrávač hlasovek na aplikaci: hraje nejvýš jedna. Na hlavním vlákně, protože
    // systémové přehrávače se ovládají z něj.
    single {
        VoicePlayer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            // Nový engine na každou hlasovku; platformní konstruktor dodá Koin (Android chce Context).
            newEngine = { get() },
            settings = get(),
            earSensor = get(),
            positions = get(),
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
    single { LinkOpenRequests() }
    single { InstantViewRequests() }
    single { BotStartRequests() }
    single { ContactOffers() }
    single { ChannelLimitRequests() }
    viewModelOf(::ChannelLimitViewModel)
    viewModelOf(::PublicLinkLimitViewModel)
    single { ConversationSearchRequests() }
    single { ConversationJumpRequests() }
    single { ProfileTabRequests() }
    single { LocalTranscripts(settings = get(), chats = get()) }
    // Přepis v telefonu i pro hodinky (WatchTranscriptionService); AI a systémový přepis jen na Androidu.
    single { OnDeviceTranscription(settings = get(), files = get(), local = get(), onDeviceAi = getOrNull(), speechToText = getOrNull()) }
    single { PhotoDescriptions(settings = get(), chats = get()) }
    single<BackgroundPhotoDescription> { BackgroundPhotoDescriber(settings = get(), chats = get(), files = get(), ai = get(), descriptions = get(), load = get()) }
    factory { ReminderTimeModel(ai = get(), settings = get()) }
    factory { NaturalSearchModel(ai = get(), settings = get()) }
    single { ScamWarnings(ai = get(), settings = get(), appScope = get(named(APP_SCOPE)), load = get()) }
    single { ChatTriage(ai = get(), settings = get(), conversations = get(), appScope = get(named(APP_SCOPE)), load = get()) }
    single { MessageContexts(ai = get(), settings = get(), appScope = get(named(APP_SCOPE)), load = get()) }
    single { ExchangeRates(TextDownloader()) }
    single { ChatSwitcher(chats = get()) }
    single { MessageBookmarks(settings = get(), chats = get()) }
    single { CommentDrafts(settings = get(), chats = get(), threads = get()) }
    single { ChatSnoozes(settings = get(), chats = get(), reminders = getOrNull()) }
    single { VideoPositions(settings = get()) }
    single { ChatColors(settings = get(), chats = get()) }
    single { ConversationReplyRequests() }
    single { SponsoredLinkRequests() }
    single { InlineQueryRequests() }
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
    // Na Androidu s úložištěm (`ShareStore`): sdílení na čipu přežije zabití procesu.
    single { ShareRequests(store = getOrNull(), scope = get(named(APP_SCOPE))) }
    // Přílohy nad polem mezi odchodem z chatu a návratem; jedno úložiště na proces.
    single { StagedAttachmentsStore() }
    // Eager: po přihlášení zaregistruje Uložené zprávy jako cíl sdílení (viz init).
    single(createdAtStart = true) {
        ShareTargets(repository = get(), chats = get(), files = get(), publisher = get(), scope = get(named(APP_SCOPE)))
    }

    viewModelOf(::AuthViewModel)
    viewModelOf(::NowPlayingViewModel)
    viewModelOf(::AccountNoticesViewModel)
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
            commentDrafts = get(),
            snoozes = get(),
            chatColorStore = get(),
            naturalSearch = get(),
            accountNotices = get(),
            session = get(),
            triage = get(),
            bookmarks = get(),
            photoDescriptions = get(),
            stories = get(),
        )
    }
    viewModelOf(::StickerPickerViewModel)
    viewModel { params -> GifPickerViewModel(chatId = params.get(), repository = get()) }
    viewModel { parameters ->
        StickerSetViewModel(
            setId = parameters.get(),
            repository = get(),
            clipboard = get(),
            sharer = get(),
            shareRequests = get(),
        )
    }
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
    viewModel { parameters ->
        // Indexem: odpověď (`Long?`) by se podle typu zaměnila s chatId.
        ChecklistComposerViewModel(
            chatId = parameters.values[0] as Long,
            replyToMessageId = parameters.values.getOrNull(1) as? Long,
            replyQuote = parameters.values.getOrNull(2) as? ReplyQuote,
            repository = get(),
            replyRequests = get(),
            editMessageId = parameters.values.getOrNull(3) as? Long,
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
    single { FolderSuggestions(ai = get(), settings = get()) }
    viewModelOf(::ChatFoldersViewModel)
    viewModel { parameters -> StickerSetsViewModel(kind = parameters.get(), repository = get()) }
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
    viewModel { LoginEmailViewModel(passwords = get(), notices = get()) }
    viewModel { ChangePhoneViewModel(accounts = get(), session = get(), notices = get()) }
    viewModel { PasskeysViewModel(accounts = get()) }
    viewModel { NetworkUsageViewModel(storage = get()) }
    viewModel { AutosaveViewModel(autosave = get(), chats = get()) }
    viewModel { parameters -> InstantViewViewModel(url = parameters.get(), repository = get(), urlOpener = get(), linkRequests = get()) }
    viewModel { NotificationSoundsViewModel(sounds = get(), settings = get(), permission = get()) }
    viewModel { DeleteAccountViewModel(accounts = get(), passwords = get()) }
    viewModelOf(::AppLockViewModel)
    // Widget na plochu jen tam, kde ho platforma má (Android).
    viewModel { AppearanceViewModel(settings = get(), homeWidgets = getOrNull()) }
    viewModelOf(::ChatSwipeSettingsViewModel)
    viewModel { QuickReactionSettingsViewModel(settings = get(), quickReaction = get(), appScope = get(named(APP_SCOPE))) }
    viewModelOf(::DevicesViewModel)
    // Překladač výslovně `getOrNull`: na iOS v DI není.
    viewModel { StorageViewModel(repository = get(), cleaner = get(), settings = get(), ai = get(), translator = getOrNull()) }
    viewModelOf(::AutoDownloadViewModel)
    viewModelOf(::NotificationSettingsViewModel)
    viewModelOf(::EditProfileViewModel)
    viewModelOf(::OwnProfileMusicViewModel)
    // Model v zařízení (core/ai). Stahování modelu běží v aplikačním rozsahu — trvá minuty.
    viewModel { OnDeviceAiSettingsViewModel(settings = get(), ai = get(), appScope = get(named(APP_SCOPE))) }
    viewModelOf(::ChatSummaryViewModel)
    viewModelOf(::ReplySuggestionsViewModel)
    // Překladač v telefonu je jen na Androidu; jinde `null` a sheet překlad v telefonu nenabídne.
    viewModel {
        DraftRewriteViewModel(
            ai = get(),
            settings = get(),
            aiText = get(),
            translator = getOrNull(),
            languageDetector = get(),
        )
    }
    viewModelOf(::EmojiSuggestionsViewModel)
    viewModelOf(::StickerSuggestionsViewModel)

    // chatId přichází z navigační routy, ne z grafu → runtime parametr. Druhý parametr je zpráva,
    // na kterou se po otevření skočí; bývá `null`, a podle typu by se spletl s chatId — proto index.
    // Třetí parametr je vlákno komentářů (kořenová zpráva); s ním dostane konverzace repository
    // zúžené na vlákno.
    viewModel { parameters ->
        val threadRootId = parameters.values.getOrNull(2) as Long?
        val forumTopicId = parameters.values.getOrNull(3) as Int?
        // Pátý: téma Uložených zpráv (podle zdrojového chatu); `null` = všechny uložené zprávy.
        val savedTopicId = parameters.values.getOrNull(4) as Long?
        // Šestý: vlákno přímých zpráv kanálu (správce s jedním odběratelem).
        val directTopicId = parameters.values.getOrNull(5) as Long?
        ConversationViewModel(
            chatId = parameters.get(),
            initialMessageId = parameters.values.getOrNull(1) as Long?,
            threadRootId = threadRootId,
            forumTopicId = forumTopicId,
            savedTopicId = savedTopicId,
            directTopicId = directTopicId,
            repository = when {
                // Koncept vlákna se hlásí evidenci přehledu konceptů — TDLib ho nehlásí (`CommentDrafts`).
                threadRootId != null -> CommentDraftTracking(
                    delegate = ThreadConversationRepository(delegate = get(), rootMessageId = threadRootId),
                    threadId = threadRootId,
                    drafts = get(),
                )
                forumTopicId != null -> get<ConversationRepository>().inForumTopic(forumTopicId)
                savedTopicId != null -> get<ConversationRepository>().inSavedTopic(savedTopicId)
                directTopicId != null -> get<ConversationRepository>().inDirectTopic(directTopicId)
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
            contactOffers = get(),
            channelLimit = get(),
            replyRequests = get(),
            sponsoredLinks = get(),
            inlineQueryRequests = get(),
            inviteLinks = get(),
            proxies = get(),
            liveLocations = get(),
            quickReaction = get(),
            bookmarks = get(),
            profiles = get(),
            jumpRequests = get(),
            snoozes = get(),
            photoMarkup = get(),
            onDeviceTranslator = getOrNull(),
            reminderTimeModel = get(),
            onDeviceAi = getOrNull(),
            localTranscripts = get(),
            photoDescriptions = get(),
            scamWarnings = get(),
            messageContexts = get(),
            calendarEvents = getOrNull(),
            contactSaver = getOrNull(),
            wifiJoiner = getOrNull(),
            exchangeRates = get(),
            currency = getOrNull(),
            speechToText = getOrNull(),
            instantViewRequests = get(),
            downloads = get(),
            liveUploads = get(),
            stagedAttachments = get(),
            liveStoriesSupported = getOrNull<LiveStreamMedia>() != null,
        )
    }

    viewModel { parameters ->
        ForumTopicsViewModel(
            chatId = parameters.get(),
            forums = get(),
            conversations = get(),
            notifications = get(),
            chats = get(),
            inviteLinks = get(),
            clipboard = get(),
        )
    }

    viewModel { parameters ->
        SavedTopicsViewModel(chatId = parameters.get(), repository = get(), settings = get())
    }

    viewModel { parameters ->
        DirectTopicsViewModel(chatId = parameters.get(), repository = get(), conversations = get())
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
            guardQueryId = parameters.values.getOrNull(8) as? Long ?: 0,
            repository = get(),
            settings = get(),
            chats = get(),
            urlOpener = get(),
            downloader = get(),
            appScope = get(named(APP_SCOPE)),
            storage = get(),
            secureStorage = get(),
        )
    }

    viewModel { parameters ->
        PreparedShareViewModel(message = parameters.get(), conversations = get(), chatRepository = get())
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
            chatColors = get(),
            contactOffers = get(),
            photoCrop = get(),
            stories = get(),
        )
    }

    viewModel { parameters -> ChatAutosaveViewModel(chatId = parameters.get(), autosave = get()) }

    viewModel { parameters ->
        EditChatViewModel(
            chatId = parameters.get(),
            repository = get(),
            photoCrop = get(),
            inviteLinks = get(),
            forums = get(),
            chats = get(),
            stickers = get(),
            quickReaction = get(),
            clipboard = get(),
            sharer = get(),
            notices = get(),
        )
    }

    viewModel { parameters ->
        InviteLinksViewModel(chatId = parameters.get(), repository = get(), clipboard = get(), sharer = get())
    }

    viewModel { parameters ->
        JoinRequestsViewModel(chatId = parameters.get(), repository = get())
    }

    viewModel { parameters ->
        EventLogViewModel(chatId = parameters.get(), repository = get())
    }

    viewModel { parameters ->
        ChatStatisticsViewModel(chatId = parameters.get(), repository = get())
    }

    viewModel { parameters ->
        PostStatisticsViewModel(
            chatId = parameters.values[0] as Long,
            messageId = parameters.values[1] as Long,
            repository = get(),
        )
    }

    viewModel { parameters ->
        RemovedMembersViewModel(
            chatId = parameters.values[0] as Long,
            banned = parameters.values[1] as Boolean,
            repository = get(),
        )
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

    // Parametry INDEXEM: požadavky bývají `null`.
    viewModel { parameters ->
        BotShareViewModel(
            source = parameters.values[0] as BotShareSource,
            users = parameters.values[1] as? BotUsersRequest,
            chat = parameters.values[2] as? BotChatRequest,
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
            sponsored = parameters.values.getOrNull(6) as? Boolean ?: false,
            repository = get(),
            profileRepository = get(),
            files = get(),
            saver = get(),
            sharer = get(),
            timers = get(),
            appScope = get(named(APP_SCOPE)),
            textRecognizer = getOrNull(),
            stickerMaker = getOrNull(),
            clipboard = get(),
            onDeviceAi = getOrNull(),
            settings = get(),
            photoDescriptions = get(),
            stickers = get(),
        )
    }

    viewModel { parameters ->
        StoryComposerViewModel(
            path = parameters.get(),
            isVideo = parameters.get(),
            repository = get(),
            storyPhoto = get(),
            storyVideo = get(),
            edit = parameters.values.getOrNull(2) as? StoryEditTarget,
        )
    }
    viewModel { parameters ->
        StoriesViewModel(
            startChatId = parameters.get(),
            storyIds = (parameters.values.getOrNull(1) as? List<*>)?.filterIsInstance<Int>().orEmpty(),
            startStoryId = parameters.values.getOrNull(2) as? Int ?: 0,
            repository = get(),
            appScope = get(named(APP_SCOPE)),
            quickReaction = get(),
            liveRepository = get(),
            liveMedia = getOrNull(),
        )
    }

    viewModelOf(::NewChatViewModel)
    viewModelOf(::NewContactViewModel)
    viewModelOf(::MyQrCodeViewModel)
    viewModelOf(::ExternalLinkViewModel)
    viewModelOf(::CallsViewModel)
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::MyStoriesViewModel)
    viewModel { parameters ->
        StickerComposerViewModel(
            sourcePath = parameters.get(),
            width = parameters.values[1] as Int,
            height = parameters.values[2] as Int,
            stickers = get(),
            maker = get(),
            markupRenderer = getOrNull(),
            targetSetId = parameters.values.getOrNull(3) as? Long,
        )
    }
    viewModel { parameters ->
        OwnedStickerSetViewModel(setId = parameters.get(), stickers = get(), files = get(), maker = getOrNull())
    }
    // Videochat žije s aplikací, ne s obrazovkou: posluchač v něm zůstává i mimo ni.
    single { VideoChatSession(repository = get(), media = getOrNull(), appScope = get(named(APP_SCOPE))) }
    viewModel { parameters ->
        VideoChatViewModel(
            chatId = parameters.get(),
            groupCallId = parameters.values[1] as Int,
            session = get(),
        )
    }

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
            channelLimit = get(),
            chats = get(),
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
