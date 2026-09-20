package eu.tintera.plume.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.chats_search_section_global
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.new_chat_contacts
import eu.tintera.plume.resources.new_chat_empty
import eu.tintera.plume.resources.new_chat_search
import eu.tintera.plume.resources.new_chat_title
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Obrazovka pro zahájení nové konverzace:
 * - Hledání v kontaktech, existujících chatech a globálně na Telegramu (@username).
 * - Karta Uložené zprávy na začátku.
 * - Seznam kontaktů s avatary a stavem dostupnosti (online, naposledy před...).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    viewModel: NewChatViewModel,
    onBack: () -> Unit,
    onChatSelected: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorText = state.error?.resolve()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.consumeError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.new_chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(Res.drawable.arrow_back_24dp),
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.new_chat_search)) },
                leadingIcon = { Icon(painterResource(Res.drawable.search_24dp), contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                painterResource(Res.drawable.close_24dp),
                                contentDescription = stringResource(Res.string.chats_search_clear),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.loading && state.contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                return@Column
            }

            val savedTitle = stringResource(Res.string.saved_messages)
            val showSaved = state.savedMessagesChatId != null &&
                (state.query.isBlank() || savedTitle.contains(state.query.trim(), ignoreCase = true))

            val hasAnyResults = state.contacts.isNotEmpty() ||
                state.chatResults.isNotEmpty() ||
                state.globalResults.isNotEmpty() ||
                showSaved

            if (!hasAnyResults) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(if (state.query.isBlank()) Res.string.new_chat_empty else Res.string.chats_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize()) {
                if (showSaved && state.savedMessagesChatId != null) {
                    item(key = "saved-messages") {
                        val savedId = state.savedMessagesChatId!!
                        Surface(
                            onClick = { onChatSelected(savedId) },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            ListItem(
                                headlineContent = { Text(savedTitle, style = MaterialTheme.typography.titleMedium) },
                                leadingContent = {
                                    Avatar(
                                        chatId = savedId,
                                        title = savedTitle,
                                        avatarFileId = null,
                                        size = 40.dp,
                                        isSavedMessages = true,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }

                if (state.contacts.isNotEmpty()) {
                    item(key = "contacts-header") {
                        SectionHeader(stringResource(Res.string.new_chat_contacts))
                    }
                    items(state.contacts, key = { it.id }) { contact ->
                        ContactRow(
                            chat = contact,
                            onClick = { onChatSelected(contact.id) },
                        )
                    }
                }

                if (state.chatResults.isNotEmpty()) {
                    item(key = "chats-header") {
                        SectionHeader(stringResource(Res.string.chats_search_section_chats))
                    }
                    items(state.chatResults, key = { "chat-${it.id}" }) { chat ->
                        ContactRow(
                            chat = chat,
                            onClick = { onChatSelected(chat.id) },
                        )
                    }
                }

                if (state.globalResults.isNotEmpty()) {
                    item(key = "global-header") {
                        SectionHeader(stringResource(Res.string.chats_search_section_global))
                    }
                    items(state.globalResults, key = { "global-${it.id}" }) { chat ->
                        ContactRow(
                            chat = chat,
                            onClick = { onChatSelected(chat.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ContactRow(
    chat: ChatSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = chatTitle(chat.id, chat.title)
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = chat.presence?.describe()?.let { presenceText ->
            {
                Text(
                    text = presenceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chat.presence is Presence.Online) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Avatar(
                chatId = chat.id,
                title = chat.title,
                avatarFileId = chat.avatarFileId,
                size = 40.dp,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
