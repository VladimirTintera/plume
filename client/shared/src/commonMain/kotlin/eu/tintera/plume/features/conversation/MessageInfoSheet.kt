package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageInfo
import eu.tintera.plume.core.telegram.MessageReadState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.menu_sent_at
import eu.tintera.plume.resources.message_info_auto_delete
import eu.tintera.plume.resources.message_info_edited
import eu.tintera.plume.resources.message_info_link
import eu.tintera.plume.resources.message_info_read
import eu.tintera.plume.resources.message_info_read_hidden_by_me
import eu.tintera.plume.resources.message_info_read_hidden_by_other
import eu.tintera.plume.resources.message_info_read_too_old
import eu.tintera.plume.resources.message_info_sent
import eu.tintera.plume.resources.message_info_title
import eu.tintera.plume.resources.message_info_unread
import eu.tintera.plume.resources.message_info_viewers
import org.jetbrains.compose.resources.stringResource

/**
 * Informace o zprávě: kdy odešla, kdy ji kdo přečetl, kdo ji zhlédl a odkaz na ni.
 *
 * Bottom sheet, ne popup ani řádek v nabídce: údajů je proměnlivý počet a u zhlédnutí je to celý
 * seznam lidí, takže se v tom musí dát listovat.
 *
 * **Vypisuje se jen to, co server vydal.** Skoro žádná zpráva nemá všechna tři práva
 * (`canGetReadDate`, `canGetViewers`, `canGetLink`) a prázdný řádek „—" by nebyl informace, jen
 * šum; čas odeslání má naopak každá zpráva, takže sheet nikdy není prázdný.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInfoSheet(
    sheet: MessageInfoSheet,
    onCopyLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Modální sheet začíná skrytý a rozjede se sám. Náhrada za `rememberModalBottomSheetState`,
    // které je od Compose 1.12 zastaralé.
    val sheetState = rememberExpandedSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SheetTitle(
            stringResource(Res.string.message_info_title),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp),
        )

        val info = sheet.info
        if (info == null) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return@ModalBottomSheet
        }

        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            InfoRow(stringResource(Res.string.message_info_sent), info.sentAt.fullTime())

            info.editedAt?.let { edited ->
                InfoRow(stringResource(Res.string.message_info_edited), edited.fullTime())
            }

            // Kdy zprávu smaže automatické mazání chatu — jinak zmizí bez varování.
            sheet.message.autoDeleteAt?.let { at ->
                InfoRow(stringResource(Res.string.message_info_auto_delete), at.asUpcomingTime())
            }

            /*
             * Čas přečtení má tolik podob, kolik má Telegram důvodů ho nevydat. Každá z nich je
             * vlastní věta — „—" by uživateli neřeklo, jestli zprávu nikdo nepřečetl, nebo jestli
             * je čas jen skrytý.
             */
            when (val read = info.readState) {
                is MessageReadState.Read ->
                    InfoRow(stringResource(Res.string.message_info_read), read.date.fullTime())
                MessageReadState.Unread ->
                    InfoRow(stringResource(Res.string.message_info_read), stringResource(Res.string.message_info_unread))
                MessageReadState.TooOld ->
                    InfoRow(stringResource(Res.string.message_info_read), stringResource(Res.string.message_info_read_too_old))
                MessageReadState.HiddenByOther ->
                    InfoRow(stringResource(Res.string.message_info_read), stringResource(Res.string.message_info_read_hidden_by_other))
                MessageReadState.HiddenByMe ->
                    InfoRow(stringResource(Res.string.message_info_read), stringResource(Res.string.message_info_read_hidden_by_me))
                MessageReadState.Unavailable -> Unit
            }

            info.link?.let { link ->
                // Ťuknutí kopíruje; otevírat odkaz na zprávu, u které uživatel stojí, nedává smysl.
                InfoRow(
                    label = stringResource(Res.string.message_info_link),
                    value = link,
                    onClick = { onCopyLink(link) },
                )
            }

            if (info.viewers.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

                Text(
                    text = stringResource(Res.string.message_info_viewers),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )

                LazyColumn(Modifier.heightIn(max = VIEWERS_MAX_HEIGHT)) {
                    items(info.viewers, key = { it.sender.id }) { viewer ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(
                                chatId = viewer.sender.id,
                                title = viewer.sender.title,
                                avatarFileId = viewer.sender.avatarFileId,
                                size = 36.dp,
                            )
                            Text(
                                text = viewer.sender.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = viewer.viewedAt.asTimeOfDay(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Řádek „popisek / hodnota". S [onClick] se dá ťuknout — dnes jen u odkazu, který se kopíruje. */
@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Den a hodina pohromadě — v sheetu je místo na obojí, na rozdíl od bubliny. */
@Composable
private fun Int.fullTime(): String =
    stringResource(Res.string.menu_sent_at, asDayLabel(), asTimeOfDay())

/** Strop výšky seznamu zhlédnutí. U příspěvku se stovkami zhlédnutí by sheet zabral celou obrazovku. */
private val VIEWERS_MAX_HEIGHT = 320.dp
