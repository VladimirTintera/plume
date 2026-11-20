package eu.tintera.plume.features.chats

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.AccountFreeze
import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.CloseBirthday
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.telegram.UnconfirmedDevice
import eu.tintera.plume.core.telegram.opensAsTopics
import eu.tintera.plume.core.update.AppUpdateState
import eu.tintera.plume.features.devices.UnconfirmedLoginCard
import eu.tintera.plume.features.notices.BirthdaysCard
import eu.tintera.plume.features.notices.FrozenAccountCard
import eu.tintera.plume.features.notices.SuggestedActionCard

/**
 * Karty nad seznamem chatů — co se týká účtu nebo aplikace, ne jednoho chatu. Pořadí je
 * naléhavost: zmrazení a cizí přihlášení napřed (týkají se účtu samotného), narozeniny (platí
 * jen dnes), čeká na odpověď, rada od Telegramu, nové chaty ve sdílené složce a nakonec
 * aktualizace. Seznam ([ChatListContent]) je kreslí jako vlastní bloky nad chaty.
 */
@Immutable
internal data class ChatListCards(
    val freeze: AccountFreeze?,
    val unconfirmedLogin: UnconfirmedDevice?,
    val birthdays: List<CloseBirthday>,
    val awaitingReply: List<ChatSummary>,
    val suggestion: SuggestedAction?,
    /** Číslo účtu do rady ověřit telefonní číslo (`SuggestedAction.CheckPhoneNumber`). */
    val accountPhone: String?,
    val newFolderChats: NewFolderChats?,
    val update: AppUpdateState?,
) {
    /** Kolik položek karty v seznamu zaberou — `headerItemCount` seznamu. */
    val count: Int
        get() = listOfNotNull(
            freeze,
            unconfirmedLogin,
            birthdays.takeIf { it.isNotEmpty() },
            awaitingReply.takeIf { it.isNotEmpty() },
            suggestion,
            update,
            newFolderChats,
        ).size
}

/** Karty jako položky seznamu, každá vlastním blokem se stejnou mezerou jako mezi skupinami. */
internal fun LazyListScope.chatListHeaderCards(
    cards: ChatListCards,
    viewModel: ChatsViewModel,
    navigation: ChatListNavigation,
    openNoticeLink: (String) -> Unit,
    startUpdate: () -> Unit,
    /** Karta nových chatů sdílené složky otevře jejich sheet; ten drží obrazovka. */
    onShowNewFolderChats: () -> Unit,
) {
    // Nad chaty, jako vlastní bloky se stejnou mezerou jako mezi skupinami. Zmrazení
    // a přihlášení napřed: týkají se účtu samotného, aktualizace počká.
    cards.freeze?.let { frozen ->
        item(key = FROZEN_ACCOUNT_KEY, contentType = FROZEN_ACCOUNT_KEY) {
            FrozenAccountCard(
                freeze = frozen,
                onAppeal = openNoticeLink,
                modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
            )
        }
    }
    cards.unconfirmedLogin?.let { login ->
        item(key = UNCONFIRMED_LOGIN_KEY, contentType = UNCONFIRMED_LOGIN_KEY) {
            UnconfirmedLoginCard(
                device = login,
                enabled = true,
                onConfirm = { viewModel.confirmLogin(login.sessionId) },
                onTerminate = { viewModel.terminateLogin(login.sessionId) },
                modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
            )
        }
    }
    // Narozeniny před radou: platí jen dnes (a včera, zítra), rada počká.
    if (cards.birthdays.isNotEmpty()) {
        item(key = BIRTHDAYS_KEY, contentType = BIRTHDAYS_KEY) {
            BirthdaysCard(
                birthdays = cards.birthdays,
                onOpen = { userId -> viewModel.openPrivateChat(userId, navigation.onChatClick) },
                onHide = viewModel::hideBirthdays,
                modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
            )
        }
    }
    // Za narozeninami: ty platí jen dnes, odpověď počká o kus níž.
    if (cards.awaitingReply.isNotEmpty()) {
        item(key = AWAITING_REPLY_KEY, contentType = AWAITING_REPLY_KEY) {
            AwaitingReplyCard(
                chats = cards.awaitingReply,
                onOpen = { chat ->
                    viewModel.opened(chat.id)
                    if (chat.opensAsTopics) navigation.onForumClick(chat.id) else navigation.onChatClick(chat.id)
                },
                onHide = viewModel::hideAwaitingReply,
                modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
            )
        }
    }
    // Za přihlášením: rada od Telegramu je méně naléhavá než cizí zařízení v účtu.
    cards.suggestion?.let { action ->
        item(key = SUGGESTION_KEY, contentType = SUGGESTION_KEY) {
            SuggestedActionCard(
                action = action,
                phone = cards.accountPhone,
                onPrimary = {
                    when (action) {
                        SuggestedAction.CheckPassword, SuggestedAction.CheckPhoneNumber ->
                            viewModel.hideSuggestion(action)
                        is SuggestedAction.SetPassword -> navigation.onOpenScreen(AppScreen.TwoStepVerification)
                        is SuggestedAction.SetLoginEmail -> navigation.onOpenScreen(AppScreen.LoginEmail)
                        SuggestedAction.SetProfilePhoto, SuggestedAction.SetBirthdate -> navigation.onOpenScreen(AppScreen.EditProfile)
                        SuggestedAction.ArchiveUnknownChats -> viewModel.enableArchiveUnknownChats(action)
                        is SuggestedAction.Custom -> openNoticeLink(action.url)
                    }
                },
                onSecondary = {
                    // „Zapomněl(a) jsem" vede k heslu (změna, obnova e-mailem), „Změnit" ke změně
                    // čísla; jinde „Teď ne".
                    when (action) {
                        SuggestedAction.CheckPassword -> navigation.onOpenScreen(AppScreen.TwoStepVerification)
                        SuggestedAction.CheckPhoneNumber -> navigation.onOpenScreen(AppScreen.ChangePhone)
                        else -> viewModel.hideSuggestion(action)
                    }
                },
                modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
            )
        }
    }
    cards.newFolderChats?.let { folderChats ->
        item(key = NEW_FOLDER_CHATS_KEY, contentType = NEW_FOLDER_CHATS_KEY) {
            NewFolderChatsCard(
                state = folderChats,
                onShow = onShowNewFolderChats,
                onHide = { viewModel.processNewFolderChats(join = false) },
                modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
            )
        }
    }
    cards.update?.let { update ->
        item(key = UPDATE_CARD_KEY, contentType = UPDATE_CARD_KEY) {
            UpdateCard(
                state = update,
                onUpdate = startUpdate,
                onDismiss = viewModel::dismissUpdate,
                onRestart = viewModel::completeUpdate,
                modifier = Modifier.animateItem().padding(bottom = GROUP_SPACING),
            )
        }
    }
}

/** Klíč karty s aktualizací. */
internal const val UPDATE_CARD_KEY = "update-card"

internal const val FROZEN_ACCOUNT_KEY = "frozen-account"

internal const val SUGGESTION_KEY = "suggested-action"

internal const val BIRTHDAYS_KEY = "close-birthdays"

internal const val AWAITING_REPLY_KEY = "awaiting-reply"

internal const val NEW_FOLDER_CHATS_KEY = "new-folder-chats"

internal const val UNCONFIRMED_LOGIN_KEY = "unconfirmed-login"
