package eu.tintera.plume.features.forward

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.reply_elsewhere_title
import org.jetbrains.compose.resources.stringResource

/**
 * Kam odpovědět: tentýž [ChatPicker] jako přeposílání, ale jeden cíl — ťuknutí na chat ho
 * rovnou vybere ([onPicked]), odpověď se dopíše až v něm.
 */
@Composable
fun ReplyElsewhereScreen(
    viewModel: ReplyElsewhereViewModel,
    onBack: () -> Unit,
    onPicked: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChatPicker(
        title = stringResource(Res.string.reply_elsewhere_title),
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.forward_search),
        ),
        loading = state.loading,
        sections = listOf(PickerSection(key = "chats", chats = state.chats)),
        enabled = true,
        onPick = onPicked,
        savedMessagesChatId = state.savedMessagesChatId,
        onBack = onBack,
        modifier = modifier,
    )
}
