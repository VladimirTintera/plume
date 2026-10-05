package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.SavedTopicHeader
import eu.tintera.plume.core.telegram.SavedTopicKind
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.saved_topic_author_hidden
import eu.tintera.plume.resources.saved_topic_my_notes
import eu.tintera.plume.resources.saved_topic_unknown_chat
import org.jetbrains.compose.resources.stringResource

/**
 * Název tématu Uložených zpráv: zdrojový chat, nebo pojmenování od nás (Moje poznámky, skrytý
 * autor). Chat, který TDLib nezná (smazaný, opuštěný), má obecný název.
 */
@Composable
fun savedTopicTitle(topic: SavedTopicHeader): String = when (topic.kind) {
    SavedTopicKind.MyNotes -> stringResource(Res.string.saved_topic_my_notes)
    SavedTopicKind.AuthorHidden -> stringResource(Res.string.saved_topic_author_hidden)
    is SavedTopicKind.FromChat -> topic.title ?: stringResource(Res.string.saved_topic_unknown_chat)
}

/**
 * Tvář tématu: fotka zdrojového chatu v tvaru podle jeho druhu, u Mých poznámek záložka
 * Uložených zpráv (jsou to ony), u skrytého autora iniciála z pojmenování.
 */
@Composable
fun SavedTopicAvatar(topic: SavedTopicHeader, size: Dp, modifier: Modifier = Modifier) {
    val chatId = (topic.kind as? SavedTopicKind.FromChat)?.chatId ?: topic.id
    Avatar(
        chatId = chatId,
        title = savedTopicTitle(topic),
        avatarFileId = topic.avatarFileId,
        size = size,
        isSavedMessages = topic.kind == SavedTopicKind.MyNotes,
        shape = (topic.chatKind ?: ChatKind.Private).avatarShape(),
        modifier = modifier,
    )
}
