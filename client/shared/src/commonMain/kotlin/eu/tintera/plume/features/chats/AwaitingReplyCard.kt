package eu.tintera.plume.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.awaiting_reply_hide
import eu.tintera.plume.resources.awaiting_reply_more
import eu.tintera.plume.resources.chats_awaiting_reply
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.reply_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * „Co jsem zmeškal": nahoře v hlavním seznamu chaty, ve kterých na uživatele čeká odpověď
 * (`ChatTriage`). V seznamu je mají i ony samy (odznak se šipkou), jenže mezi desítkami
 * nepřečtených se ztratí — karta je vytáhne na jedno místo hned po otevření aplikace.
 *
 * - **Ťuknutí na chat ho otevře**, přečtením z karty zmizí (už nečeká na triáž, ale na uživatele).
 * - **Křížek kartu schová**, dokud nepřibude chat, který na kartě nebyl
 *   (`ChatsViewModel.hideAwaitingReply`). Schovat ji natrvalo jde vypnutím volby v AI v zařízení.
 * - **Nejvýš [MAX_ROWS] chatů**, zbytek jako „a další N" — karta nemá vytlačit seznam
 *   z obrazovky, a ostatní jsou v seznamu pod ní s odznakem.
 * - Řádky na dlaždicích v `surfaceContainerLowest` ze stejného důvodu jako u narozenin: avatar
 *   v barvě karty by na ní zmizel.
 */
@Composable
fun AwaitingReplyCard(
    chats: List<ChatSummary>,
    onOpen: (ChatSummary) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = accentOn(colors.primaryContainer, colors.primary, colors.onPrimaryContainer)
    val onAccent = if (accent == colors.primary) colors.onPrimary else colors.primaryContainer
    val shown = chats.take(MAX_ROWS)

    Surface(
        shape = RoundedCornerShape(CARD_CORNER),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.reply_24dp), contentDescription = null, tint = onAccent)
                }
                Text(
                    stringResource(Res.string.chats_awaiting_reply),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                IconButton(onClick = onHide, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.awaiting_reply_hide))
                }
            }
            Column(Modifier.padding(top = 8.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEachIndexed { index, chat ->
                    AwaitingChatRow(chat, shape = tileShape(index, shown.size), onClick = { onOpen(chat) })
                }
            }
            val more = chats.size - shown.size
            if (more > 0) {
                Text(
                    pluralStringResource(Res.plurals.awaiting_reply_more, more, more),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AwaitingChatRow(chat: ChatSummary, shape: Shape, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chatId = chat.avatarColorId, title = chat.title, avatarFileId = chat.avatarFileId, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                chat.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chat.lastMessagePreview?.let { preview ->
                Text(
                    // Jednořádkový náhled: zalomení by ho useklo hned na začátku.
                    preview.resolve().replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Krajní dlaždice zaoblené víc, vnitřní míň — jako skupiny řádků v seznamu chatů. */
private fun tileShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) OUTER_CORNER else INNER_CORNER,
    topEnd = if (index == 0) OUTER_CORNER else INNER_CORNER,
    bottomStart = if (index == count - 1) OUTER_CORNER else INNER_CORNER,
    bottomEnd = if (index == count - 1) OUTER_CORNER else INNER_CORNER,
)

/** Kolik chatů karta ukáže; zbytek je v seznamu pod ní. */
private const val MAX_ROWS = 4

private val CARD_CORNER = 20.dp
private val OUTER_CORNER = 16.dp
private val INNER_CORNER = 4.dp
