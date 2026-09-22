package eu.tintera.plume.features.chats

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.resources.profile_notif_mute_forever
import eu.tintera.plume.resources.profile_notif_mute_8h
import eu.tintera.plume.resources.profile_notif_mute_2d
import eu.tintera.plume.resources.profile_notif_mute_1h
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.expand_more_24dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.isPrivate
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.unarchive_24dp
import eu.tintera.plume.resources.chats_action_delete
import eu.tintera.plume.resources.chats_action_leave
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mark_unread
import eu.tintera.plume.resources.chats_action_mute
import eu.tintera.plume.resources.chats_action_pin
import eu.tintera.plume.resources.chats_action_unmute
import eu.tintera.plume.resources.chats_action_unpin
import eu.tintera.plume.resources.chats_delete_body
import eu.tintera.plume.resources.chats_delete_title
import eu.tintera.plume.resources.chats_leave_body
import eu.tintera.plume.resources.chats_leave_title
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Akce nad chatem po dlouhém stisku.
 *
 * Bottom sheet, ne kontextové menu u prstu: na telefonu je spodní okraj v dosahu palce a nabídka
 * má místo na popisky, ze kterých je poznat, co se stane — u nevratné akce to není kosmetika.
 */
@Composable
fun ChatActionsSheet(
    chat: ChatSummary,
    onTogglePinned: () -> Unit,
    /** Ztlumení na dobu; `null` = navždy. */
    onMute: (Duration?) -> Unit,
    onUnmute: () -> Unit,
    onToggleUnread: () -> Unit,
    /** `null` = chat archivovat nejde (Uložené zprávy), položka se nenabídne. */
    onToggleArchived: (() -> Unit)?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Modální sheet začíná skrytý a rozjede se sám. Náhrada za `rememberModalBottomSheetState`,
    // které je od Compose 1.12 zastaralé.
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = chatTitle(chat.id, chat.title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SheetActionRow(
                icon = Res.drawable.push_pin_filled_24dp,
                // Popisek říká, co se STANE, ne jaký je stav — jinak si uživatel musí domýšlet,
                // jestli „Připnuto" je tvrzení, nebo tlačítko.
                label = stringResource(if (chat.isPinned) Res.string.chats_action_unpin else Res.string.chats_action_pin),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                onClick = onTogglePinned,
            )

            if (chat.isMuted) {
                SheetActionRow(
                    icon = Res.drawable.notifications_24dp,
                    label = stringResource(Res.string.chats_action_unmute),
                    shape = RoundedCornerShape(4.dp),
                    onClick = onUnmute,
                )
            } else {
                /*
                 * Doby ztlumení se rozbalí na místě, ne v dalším sheetu — jsou to varianty jedné
                 * položky a nabídka pod prstem zůstane stejná. Dřív „Ztlumit" znamenalo navždy,
                 * a na hodinu šlo chat ztlumit jen z notifikace.
                 */
                var expanded by rememberSaveable { mutableStateOf(false) }
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "mute-expand",
                )
                SheetActionRow(
                    icon = Res.drawable.notifications_off_24dp,
                    label = stringResource(Res.string.chats_action_mute),
                    shape = RoundedCornerShape(4.dp),
                    onClick = { expanded = !expanded },
                    trailing = {
                        Icon(
                            painterResource(Res.drawable.expand_more_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                        )
                    },
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MUTE_OPTIONS.forEach { (label, duration) ->
                            SheetActionRow(
                                icon = null,
                                label = stringResource(label),
                                shape = RoundedCornerShape(4.dp),
                                // Tónovaná plocha o stupeň níž: varianty patří k položce nad nimi.
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                onClick = { onMute(duration) },
                            )
                        }
                    }
                }
            }

            SheetActionRow(
                icon = Res.drawable.mark_chat_unread_24dp,
                label = stringResource(if (chat.hasUnread) Res.string.chats_action_mark_read else Res.string.chats_action_mark_unread),
                shape = RoundedCornerShape(4.dp),
                onClick = onToggleUnread,
            )

            onToggleArchived?.let { onClick ->
                val archived = chat.folder == ChatFolder.Archive
                SheetActionRow(
                    icon = if (archived) Res.drawable.unarchive_24dp else Res.drawable.archive_24dp,
                    label = stringResource(if (archived) Res.string.chats_action_unarchive else Res.string.chats_action_archive),
                    shape = RoundedCornerShape(4.dp),
                    onClick = onClick,
                )
            }

            SheetActionRow(
                icon = Res.drawable.delete_24dp,
                // Ze skupiny se odchází, soukromý chat se maže. Jeden popisek pro obojí by lhal.
                label = stringResource(if (chat.isPrivate) Res.string.chats_action_delete else Res.string.chats_action_leave),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                // Jediná nevratná akce v nabídce — dostane chybovou barvu, aby se nespletla
                // s ostatními.
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = onDelete,
            )
        }
    }
}

/**
 * Potvrzení nevratné akce.
 *
 * Text pojmenovává **konkrétní následek**, ne obecné „opravdu?". U soukromého chatu se maže
 * historie jen u nás, ze skupiny se odchází — to jsou dvě různé věci a uživatel musí vědět,
 * kterou zrovna dělá.
 */
@Composable
fun ChatDeleteDialog(
    chat: ChatSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (chat.isPrivate) Res.string.chats_delete_title else Res.string.chats_leave_title)) },
        text = {
            Text(
                stringResource(
                    if (chat.isPrivate) Res.string.chats_delete_body else Res.string.chats_leave_body,
                    chatTitle(chat.id, chat.title),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(if (chat.isPrivate) Res.string.common_delete else Res.string.chats_action_leave),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** Doby ztlumení v nabídce; `null` = navždy. Stejné jako v profilu chatu. */
private val MUTE_OPTIONS: List<Pair<StringResource, Duration?>> = listOf(
    Res.string.profile_notif_mute_1h to 1.hours,
    Res.string.profile_notif_mute_8h to 8.hours,
    Res.string.profile_notif_mute_2d to 2.days,
    Res.string.profile_notif_mute_forever to null,
)
