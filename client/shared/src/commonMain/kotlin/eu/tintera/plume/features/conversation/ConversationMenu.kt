package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import eu.tintera.plume.resources.clear_history_all_topic_text
import eu.tintera.plume.resources.clear_history_all_text
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.features.chats.ChatDeleteSheet
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.chats_action_delete
import eu.tintera.plume.resources.conversation_switch_chat
import eu.tintera.plume.resources.conversation_switch_chat_hint
import eu.tintera.plume.resources.clear_history
import eu.tintera.plume.resources.delete_sweep_24dp
import eu.tintera.plume.resources.chats_action_report
import eu.tintera.plume.resources.chats_delete_report
import eu.tintera.plume.resources.conversation_search
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.resources.logout_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_leave_channel
import eu.tintera.plume.resources.profile_leave_group
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.profile_mute
import eu.tintera.plume.resources.profile_unmute
import eu.tintera.plume.resources.saved_view_as_chats
import eu.tintera.plume.resources.forum_view_as_topics
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.secret_chat_close
import eu.tintera.plume.resources.secret_chat_close_text
import eu.tintera.plume.resources.secret_chat_close_title
import eu.tintera.plume.resources.summarize_24dp
import eu.tintera.plume.resources.summary_action
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * ⋮ v hlavičce konverzace: všechno kromě hovoru (ten je v liště, `ConversationCallButtons`).
 *
 * Skupiny podle významu: přepnutí na jiný chat (otevře přepínač pod šipkou zpět), práce s obsahem chatu (hledat, shrnout, Uložené po chatech, ztlumit),
 * nahlášení a nakonec nevratné akce v chybové barvě — ukončit tajný chat, smazat chat / opustit
 * skupinu nebo kanál. Nahlášení a smazání sem přešly z ⋮ profilu: týkají se konverzace, ne
 * člověka, a v profilu otevřeném z cizí zprávy ve skupině nedávaly smysl.
 *
 * [showsContent] je `false` u prázdného chatu s botem (je to jeho profil) a u skrytého obsahu —
 * pak není co hledat ani shrnout. Ve vlákně a tématu zůstávají jen akce nad obsahem; ztlumení
 * i smazání by se týkaly celého chatu, ne toho, co hlavička ukazuje.
 *
 * Ve vlastní funkci a souboru, ne v `ConversationScreen`: ta je v dexu na hraně (CLAUDE.md, Gotchas).
 */
@Composable
internal fun ConversationMenu(
    state: ConversationState,
    viewModel: ConversationViewModel,
    showsContent: Boolean,
    /** Shrnutí modelem v zařízení; `null`, když tu není. */
    onSummary: (() -> Unit)?,
    /** Uložené zprávy po zdrojových chatech; `null` mimo celé Uložené zprávy. */
    onViewAsChats: (() -> Unit)?,
    /** Skupina s tématy jako seznam témat; `null` mimo celou skupinu s tématy. */
    onViewAsTopics: (() -> Unit)?,
    /** Rychlý přepínač chatů pod šipkou zpět; `null`, když není na co přepnout. */
    onSwitchChat: (() -> Unit)?,
) {
    val info = state.info
    val wholeChat = info != null && !state.narrowed && !info.isSavedMessages
    val isPrivate = info != null && !info.isGroup && !info.isChannel
    // Odejít jde jen odtud, kde uživatel je — chat otevřený z hledání ho ještě nemá.
    val canDeleteOrLeave = wholeChat && (isPrivate || (!info.canJoin && info.writeAccess != WriteAccess.NotMember))
    val canCloseSecret = wholeChat && info.isSecret && info.writeAccess != WriteAccess.SecretClosed
    val muted = state.muted.takeIf { wholeChat }
    val spam = state.spam

    var expanded by remember { mutableStateOf(false) }
    var confirmCloseSecret by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var clearHistory by rememberSaveable { mutableStateOf(false) }
    // Vyprázdnit jde celý chat (kde to TDLib dovolí), téma Uložených zpráv a vlákno přímých zpráv
    // kanálu — ty mají vlastní mazání. Vlákno komentářů a téma skupiny ne.
    val inOwnTopic = state.savedTopicId != null || state.directTopicId != null
    val clearRights = info?.clearHistory?.takeIf { wholeChat || inOwnTopic }

    val groups = listOf(
        // Přepnutí je pohyb mezi chaty, ne práce s tímhle, proto skupina sama. Je tu hlavně kvůli
        // tomu, že podržení šipky nikdo nenajde: nápověda pod položkou tu cestu prozradí
        // a nabídka se otevře pod šipkou, ne tady.
        listOfNotNull(
            onSwitchChat?.let {
                MenuAction(
                    stringResource(Res.string.conversation_switch_chat),
                    Res.drawable.chat_24dp,
                    supporting = stringResource(Res.string.conversation_switch_chat_hint),
                    onClick = it,
                )
            },
        ),
        listOfNotNull(
            if (showsContent) {
                MenuAction(stringResource(Res.string.conversation_search), Res.drawable.search_24dp, onClick = viewModel::openSearch)
            } else {
                null
            },
            if (showsContent && onSummary != null) {
                MenuAction(stringResource(Res.string.summary_action), Res.drawable.summarize_24dp, onClick = onSummary)
            } else {
                null
            },
            onViewAsChats?.let { MenuAction(stringResource(Res.string.saved_view_as_chats), Res.drawable.forum_24dp, onClick = it) },
            onViewAsTopics?.let { MenuAction(stringResource(Res.string.forum_view_as_topics), Res.drawable.forum_24dp, onClick = { viewModel.viewAsTopics(it) }) },
            muted?.let {
                MenuAction(
                    stringResource(if (it) Res.string.profile_unmute else Res.string.profile_mute),
                    if (it) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp,
                    onClick = viewModel::toggleMute,
                )
            },
        ),
        // Nahlášení se ptá na důvod (průvodce), proto „…". Jen u spamového action baru ho
        // server vezme rovnou jako spam, a pak to popisek řekne.
        listOfNotNull(
            if (wholeChat && spam?.canReport == true) {
                MenuAction(
                    stringResource(if (spam.reportsSpamDirectly) Res.string.chats_delete_report else Res.string.chats_action_report),
                    Res.drawable.flag_24dp,
                    onClick = viewModel::reportChat,
                )
            } else {
                null
            },
        ),
        listOfNotNull(
            clearRights?.let {
                MenuAction(
                    stringResource(Res.string.clear_history),
                    Res.drawable.delete_sweep_24dp,
                    destructive = true,
                    onClick = { clearHistory = true },
                )
            },
            if (canCloseSecret) {
                MenuAction(
                    stringResource(Res.string.secret_chat_close),
                    Res.drawable.lock_open_24dp,
                    destructive = true,
                    onClick = { confirmCloseSecret = true },
                )
            } else {
                null
            },
            if (canDeleteOrLeave) {
                MenuAction(
                    stringResource(
                        when {
                            info.isChannel -> Res.string.profile_leave_channel
                            info.isGroup -> Res.string.profile_leave_group
                            else -> Res.string.chats_action_delete
                        },
                    ),
                    if (isPrivate) Res.drawable.delete_24dp else Res.drawable.logout_24dp,
                    destructive = true,
                    onClick = { confirmDelete = true },
                )
            } else {
                null
            },
        ),
    )
    if (groups.all { it.isEmpty() }) return

    Box {
        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.profile_more))
        }
        ExpressiveMenu(expanded = expanded, onDismiss = { expanded = false }, groups = groups)
    }

    if (confirmCloseSecret) {
        ConfirmDialog(
            title = stringResource(Res.string.secret_chat_close_title),
            text = stringResource(Res.string.secret_chat_close_text),
            confirmLabel = stringResource(Res.string.secret_chat_close),
            onConfirm = {
                confirmCloseSecret = false
                viewModel.closeSecretChat()
            },
            onDismiss = { confirmCloseSecret = false },
            icon = Res.drawable.close_24dp,
            destructive = true,
        )
    }
    if (clearHistory && clearRights != null && info != null) {
        ClearHistorySheet(
            rights = clearRights,
            peerName = info.title.takeIf { isPrivate },
            allText = if (inOwnTopic) Res.string.clear_history_all_topic_text else Res.string.clear_history_all_text,
            onConfirm = viewModel::clearHistory,
            onDismiss = { clearHistory = false },
        )
    }
    // Tentýž sheet s volbami proti spamu jako v seznamu chatů — dvě podoby téhož kroku by se rozešly.
    if (confirmDelete && info != null) {
        ChatDeleteSheet(
            isPrivate = isPrivate,
            title = info.title,
            spam = spam,
            onConfirm = { cleanup ->
                confirmDelete = false
                viewModel.deleteOrLeave(cleanup)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
