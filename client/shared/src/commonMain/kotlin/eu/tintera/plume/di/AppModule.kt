package eu.tintera.plume.di

import eu.tintera.plume.features.profile.ChatProfileViewModel
import eu.tintera.plume.core.telegram.TdlProfileRepository
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.DeviceRepository
import eu.tintera.plume.core.telegram.TdlDeviceRepository
import eu.tintera.plume.features.devices.DevicesViewModel
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionController
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.TdlConnectionRepository
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.telegram.EmojiRepository
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.GifRepository
import eu.tintera.plume.core.telegram.TdlGifRepository
import eu.tintera.plume.core.telegram.TdlEmojiRepository
import eu.tintera.plume.core.telegram.TdlStickerRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.RevealedSpoilers
import eu.tintera.plume.core.telegram.SelfDestructTimers
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.notifications.NotificationActions
import eu.tintera.plume.core.telegram.TdlChatRepository
import eu.tintera.plume.core.telegram.TdlConversationRepository
import eu.tintera.plume.core.telegram.TdlFileRepository
import eu.tintera.plume.core.telegram.TdlNotificationRepository
import eu.tintera.plume.core.telegram.TdlTelegramSession
import eu.tintera.plume.core.telegram.TelegramClientHolder
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.features.auth.AuthViewModel
import eu.tintera.plume.features.chats.ChatsViewModel
import eu.tintera.plume.features.chats.NewChatViewModel
import eu.tintera.plume.features.conversation.ConversationViewModel
import eu.tintera.plume.features.conversation.StickerPickerViewModel
import eu.tintera.plume.features.conversation.GifPickerViewModel
import eu.tintera.plume.features.conversation.StickerSetViewModel
import eu.tintera.plume.features.media.MediaViewerViewModel
import eu.tintera.plume.features.share.ShareViewModel
import eu.tintera.plume.features.settings.AppearanceViewModel
import eu.tintera.plume.features.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import eu.tintera.plume.core.audio.VoicePlayer
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
    single<ChatRepository>(createdAtStart = true) {
        TdlChatRepository(
            holder = get(),
            session = get(),
            scope = get(named(APP_SCOPE)),
        )
    }

    // Bezstavová — na rozdíl od seznamu chatů nemusí vznikat dopředu, stránkovací stav
    // drží ConversationViewModel.
    single<ConversationRepository> { TdlConversationRepository(holder = get()) }

    // Odpověď z notifikace. Nad ConversationRepository, ne v něm: jde o pořadí kroků (odeslat,
    // dočkat se potvrzení, pak teprve přečíst), ne o další volání TDLib.
    single { NotificationActions(session = get(), conversations = get(), chats = get()) }

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
    single<GifRepository> { TdlGifRepository(holder = get()) }
    single<EmojiRepository> { TdlEmojiRepository(holder = get()) }

    // Eagerně: nastavení se načte při startu, aby ho první obrazovka měla hned (viz AppSettings).
    single<AppSettings>(createdAtStart = true) {
        DataStoreAppSettings(dataStore = get(), scope = get(named(APP_SCOPE)))
    }

    // Jeden přehrávač hlasovek na aplikaci: hraje nejvýš jedna. Na hlavním vlákně, protože
    // systémové přehrávače se ovládají z něj.
    single { VoicePlayer(scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)) }

    // Most mezi platformním vstupem (ťuknutí na notifikaci) a navigací.
    single { ChatOpenRequests() }
    single { ConversationSearchRequests() }
    single { RevealedSpoilers() }
    single { SelfDestructTimers() }
    single<ProfileRepository> { TdlProfileRepository(holder = get()) }
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
    // Složka je parametr: hlavní seznam i archiv jsou tentýž ViewModel. Bez parametru hlavní.
    viewModel { parameters ->
        ChatsViewModel(
            folder = parameters.getOrNull<ChatFolder>() ?: ChatFolder.Main,
            repository = get(),
            connection = get(),
            settings = get(),
            appUpdates = get(),
            devices = get(),
            session = get(),
        )
    }
    viewModelOf(::StickerPickerViewModel)
    viewModel { params -> GifPickerViewModel(chatId = params.get(), repository = get()) }
    viewModel { parameters -> StickerSetViewModel(setId = parameters.get(), repository = get()) }
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AppearanceViewModel)
    viewModelOf(::DevicesViewModel)

    // chatId přichází z navigační routy, ne z grafu → runtime parametr. Druhý parametr je zpráva,
    // na kterou se po otevření skočí; bývá `null`, a podle typu by se spletl s chatId — proto index.
    viewModel { parameters ->
        ConversationViewModel(
            chatId = parameters.get(),
            initialMessageId = parameters.values.getOrNull(1) as Long?,
            repository = get(),
            chatRepository = get(),
            stickerRepository = get(),
            settings = get(),
            appScope = get(named(APP_SCOPE)),
            urlOpener = get(),
            clipboard = get(),
            fileRepository = get(),
            voicePlayer = get(),
            audioRecorder = get(),
            fileOpener = get(),
            sharer = get(),
            shareRequests = get(),
            shareTargets = get(),
            connection = get(),
            searchRequests = get(),
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
        )
    }

    viewModel { ShareViewModel(requests = get(), chatRepository = get()) }

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

    // Dva parametry z routy. `get()` bez indexu je bere podle pořadí, ve kterém přišly.
    viewModel { parameters ->
        MediaViewerViewModel(
            chatId = parameters.get(),
            messageId = parameters.get(),
            repository = get(),
            files = get(),
            saver = get(),
            sharer = get(),
            timers = get(),
        )
    }

    viewModelOf(::NewChatViewModel)
}
