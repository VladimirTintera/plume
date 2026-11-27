package eu.tintera.plume.features.digest

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountTopAppBar
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.digest_ai_missing
import eu.tintera.plume.resources.digest_ai_note
import eu.tintera.plume.resources.digest_ai_off
import eu.tintera.plume.resources.digest_empty
import eu.tintera.plume.resources.digest_empty_title
import eu.tintera.plume.resources.digest_open_settings
import eu.tintera.plume.resources.digest_subtitle
import eu.tintera.plume.resources.digest_summarizing
import eu.tintera.plume.resources.digest_title
import eu.tintera.plume.resources.digest_unread
import eu.tintera.plume.resources.digest_waiting
import eu.tintera.plume.resources.summarize_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * **Přehled kanálů**: ztlumené kanály s novými příspěvky za posledních 24 hodin, u každého dvě
 * věty od modelu v zařízení, jak postupně dobíhají. Nahoře věta, že shrnutí píše model (a že se
 * může splést), nebo kde model zapnout či stáhnout. Ťuknutí na kanál ho otevře.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChannelDigestScreen(
    viewModel: ChannelDigestViewModel,
    onBack: () -> Unit,
    onOpenChannel: (chatId: Long) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            AccountTopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.digest_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(Res.string.digest_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val content = Modifier.widthIn(max = ContentWidth.Reading)
            val channels = state.channels
            when {
                channels == null -> Box(content.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                channels.isEmpty() -> IntroEmptyState(
                    icon = Res.drawable.summarize_24dp,
                    title = stringResource(Res.string.digest_empty_title),
                    text = stringResource(Res.string.digest_empty),
                    modifier = content.padding(top = 48.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = content.fillMaxSize(),
                ) {
                    item(key = "ai") { AiNote(state.ai, onOpenAiSettings) }
                    itemsIndexed(channels, key = { _, channel -> channel.chatId }) { index, channel ->
                        DigestRow(channel, index, channels.size, onClick = { onOpenChannel(channel.chatId) })
                    }
                }
            }
        }
    }
}

/** Věta nad seznamem: kdo shrnutí píše, nebo co chybí, aby je psal. */
@Composable
private fun AiNote(ai: DigestAi, onOpenAiSettings: () -> Unit) {
    val text = when (ai) {
        DigestAi.Ready -> Res.string.digest_ai_note
        DigestAi.Off -> Res.string.digest_ai_off
        DigestAi.Missing -> Res.string.digest_ai_missing
        DigestAi.Unsupported -> return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(Res.drawable.auto_awesome_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (ai == DigestAi.Off || ai == DigestAi.Missing) {
            TextButton(onClick = onOpenAiSettings, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.digest_open_settings))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DigestRow(channel: DigestChannel, index: Int, count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .groupedItem(index, count, MaterialTheme.colorScheme.groupRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(chatId = channel.chatId, title = channel.title, avatarFileId = channel.avatarFileId, size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    channel.lastTimestamp.asRelativeTime(),
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                pluralStringResource(Res.plurals.digest_unread, channel.unread, channel.unread),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(4.dp))
            when (val summary = channel.summary) {
                is DigestSummary.Done -> SummaryText(summary.text)
                is DigestSummary.Running -> if (summary.text.isBlank()) {
                    Pending(stringResource(Res.string.digest_summarizing))
                } else {
                    SummaryText(summary.text)
                }
                DigestSummary.Waiting -> Pending(stringResource(Res.string.digest_waiting))
                DigestSummary.Failed, DigestSummary.None -> Text(
                    channel.lastPreview?.resolve().orEmpty().replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SummaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = SUMMARY_MAX_LINES, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Pending(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Dvě věty se do čtyř řádků vejdou i v úzkém panelu; delší odpověď modelu se usekne. */
private const val SUMMARY_MAX_LINES = 4
