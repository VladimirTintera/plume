package eu.tintera.plume.features.forward

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.forward_empty
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.forward_title
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kam přeposlat: hledání a seznam chatů.
 *
 * Obyčejná obrazovka, ne bottom sheet — seznam chatů je dlouhý a listuje se v něm; sheet by
 * navíc při hledání zápasil s klávesnicí. Po odeslání se otevře cílový chat, takže je hned vidět,
 * že zpráva dorazila.
 */
@Composable
fun ForwardScreen(
    viewModel: ForwardViewModel,
    onBack: () -> Unit,
    onForwarded: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatPicker(
        title = stringResource(Res.string.forward_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        loading = state.loading,
        chats = state.chats,
        // Dokud odesílání běží, další ťuknutí by poslalo zprávu dvakrát.
        enabled = !state.sending,
        onPick = { chatId -> viewModel.forward(chatId, onForwarded) },
        onBack = onBack,
        error = state.error,
        onErrorShown = viewModel::consumeError,
        modifier = modifier,
    )
}

/**
 * Výběr chatu: hledání nad načteným seznamem a seznam chatů. Sdílí ho přeposílání a sdílení
 * do aplikace ze systému — obojí je „vyber, kam s tím".
 *
 * [subtitle] je pod hledáním: co se sdílí, nebo že se to ještě připravuje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPicker(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    chats: List<ChatSummary>,
    enabled: Boolean,
    onPick: (chatId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    error: UiText? = null,
    onErrorShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val errorText = error?.resolve()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.forward_search)) },
                leadingIcon = { Icon(painterResource(Res.drawable.search_24dp), contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }

            if (chats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.forward_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { chat ->
                    ListItem(
                        headlineContent = { Text(chat.title) },
                        leadingContent = {
                            Avatar(chatId = chat.id, title = chat.title, avatarFileId = chat.avatarFileId, size = 40.dp)
                        },
                        modifier = Modifier.clickable(enabled = enabled) { onPick(chat.id) },
                    )
                }
            }
        }
    }
}
