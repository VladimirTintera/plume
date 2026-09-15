package eu.tintera.plume.di

import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.TdlChatRepository
import eu.tintera.plume.core.telegram.TdlConversationRepository
import eu.tintera.plume.core.telegram.TdlFileRepository
import eu.tintera.plume.core.telegram.TdlNotificationRepository
import eu.tintera.plume.core.telegram.TdlTelegramSession
import eu.tintera.plume.core.telegram.TelegramClientHolder
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.features.auth.AuthViewModel
import eu.tintera.plume.features.chats.ChatsViewModel
import eu.tintera.plume.features.conversation.ConversationViewModel
import eu.tintera.plume.features.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
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

    // Eagerně: v konstruktoru se přihlašuje k `fileUpdates` a průběh stahování chodí pro všechny
    // soubory. Při líné konstrukci by se ztratily updaty k souborům, jejichž stahování už běží.
    single<FileRepository>(createdAtStart = true) {
        TdlFileRepository(holder = get(), scope = get(named(APP_SCOPE)))
    }

    single<NotificationRepository> { TdlNotificationRepository(holder = get()) }

    viewModelOf(::AuthViewModel)
    viewModelOf(::ChatsViewModel)
    viewModelOf(::SettingsViewModel)

    // chatId přichází z navigační routy, ne z grafu → runtime parametr.
    viewModel { parameters ->
        ConversationViewModel(chatId = parameters.get(), repository = get(), urlOpener = get())
    }
}
