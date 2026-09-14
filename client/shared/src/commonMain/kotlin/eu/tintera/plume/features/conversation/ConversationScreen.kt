package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.send_24dp
import kotlinx.coroutines.flow.debounce
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Instant

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // `reverseLayout` obrací i význam indexů: index 0 je nejnovější zpráva dole, vyšší indexy
    // jsou starší nahoře. Stránkování do minulosti se tedy spouští na KONCI seznamu.
    val shouldLoadOlder by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadOlder }.collect { if (it) viewModel.loadOlder() }
    }

    // Přečtení se hlásí až když se scrollování uklidní — jinak by při rychlém průletu odešla
    // jedna žádost na každý snímek.
    LaunchedEffect(listState, state.messages) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .debounce(READ_DEBOUNCE_MS)
            .collect { indices ->
                val visible = indices.mapNotNull { state.messages.getOrNull(it) }
                viewModel.markRead(visible.filterNot { it.isOutgoing }.map { it.id })
            }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.info?.title ?: "Konverzace") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = "Zpět")
                    }
                },
            )
        },
        bottomBar = { MessageInput(onSend = viewModel::send) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.messages.isEmpty() && state.loading ->
                    LoadingIndicator(Modifier.align(Alignment.Center))

                state.messages.isEmpty() -> Text(
                    text = state.error ?: "Zatím tu nejsou žádné zprávy.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val index = state.messages.indexOf(message)
                        MessageBubble(
                            message = message,
                            // Seznam jde od nejnovější, takže „předchozí zpráva v čase"
                            // je ta s VYŠŠÍM indexem.
                            previous = state.messages.getOrNull(index + 1),
                            modifier = Modifier.animateItem(),
                        )
                    }

                    if (state.loading) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bublina jedné zprávy.
 *
 * Zprávy téhož odesílatele v řadě se **seskupují**: jméno se ukáže jen u první a nároží u sebe
 * navazujících bublin se zaoblí míň. Bez toho vypadá delší monolog jako dvacet nesouvisejících
 * kartiček.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    previous: ChatMessage?,
    modifier: Modifier = Modifier,
) {
    val continuesGroup = previous != null &&
        previous.isOutgoing == message.isOutgoing &&
        previous.senderId == message.senderId

    // Odchozí = primaryContainer, příchozí = surfaceContainerHigh. Vrstvení řeší surface role,
    // ne elevation — viz konvence v CLAUDE.md.
    val containerColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val corner = 18.dp
    val tight = 4.dp
    val shape = if (message.isOutgoing) {
        RoundedCornerShape(corner, if (continuesGroup) tight else corner, tight, corner)
    } else {
        RoundedCornerShape(if (continuesGroup) tight else corner, corner, corner, tight)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (!continuesGroup) {
                    message.senderName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Text(text = message.text, style = MaterialTheme.typography.bodyLarge)

                Text(
                    text = buildString {
                        append(message.timestamp.asTimeOfDay())
                        if (message.isEdited) append(" · upraveno")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun MessageInput(onSend: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Zpráva") },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    // Podtržení u pole uvnitř vlastního chrome ruší; tvar nese zaoblení.
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onSend(draft)
                        draft = ""
                    },
                ),
            )

            FilledIconButton(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(painterResource(Res.drawable.send_24dp), contentDescription = "Odeslat")
            }
        }
    }
}

/** Unixový čas v sekundách → „14:32" v místní zóně. */
private fun Int.asTimeOfDay(): String {
    val local = Instant.fromEpochSeconds(this.toLong())
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

/**
 * Co se ukáže v detail panelu, když je dost místa na dva panely, ale žádný chat není vybraný.
 * Bez tohohle by tam zela prázdná plocha, což vypadá jako chyba vykreslení.
 */
@Composable
fun ConversationPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Vyber chat vlevo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5
private const val READ_DEBOUNCE_MS = 400L
