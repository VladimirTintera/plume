package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.AudioEngine
import eu.tintera.plume.core.telegram.DEFAULT_SOUND_ID
import eu.tintera.plume.core.telegram.NO_SOUND_ID
import eu.tintera.plume.core.telegram.NotificationTone
import eu.tintera.plume.core.telegram.ToneLimits
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.notif_sound_default
import eu.tintera.plume.resources.notif_sound_default_summary
import eu.tintera.plume.resources.notif_sound_follow_default
import eu.tintera.plume.resources.notif_sound_delete
import eu.tintera.plume.resources.notif_sound_delete_confirm
import eu.tintera.plume.resources.notif_sound_delete_text
import eu.tintera.plume.resources.notif_sound_delete_title
import eu.tintera.plume.resources.notif_sound_none
import eu.tintera.plume.resources.notif_sound_play
import eu.tintera.plume.resources.notif_sound_seconds
import eu.tintera.plume.resources.notif_sound_tones
import eu.tintera.plume.resources.notif_sound_upload
import eu.tintera.plume.resources.notif_sound_upload_hint
import eu.tintera.plume.resources.play_arrow_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Výběr zvuku rozsahu: výchozí (systémový kanál, jak si ho uživatel nastavil), bez zvuku
 * a tóny účtu s ukázkou. Tón jde nahrát i smazat — tóny jsou na serveru, takže změna platí
 * i v ostatních aplikacích účtu. Vybraný zvuk se zapíše hned.
 *
 * Ukázku přehrává tentýž přehrávač jako hlasovky ([AudioEngine]); s výběrem se uvolní.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NotificationSoundSheet(
    title: StringResource,
    selected: Long,
    tones: List<NotificationTone>,
    limits: ToneLimits?,
    uploading: Boolean,
    onSelect: (Long) -> Unit,
    onUpload: (String) -> Unit,
    onDelete: (Long) -> Unit,
    tonePath: suspend (Long) -> String?,
    onError: (eu.tintera.plume.core.ui.UiText) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Popisek volby „Podle výchozího nastavení" (u jednoho chatu: co zní v jeho rozsahu);
     * `null` = volba se nenabízí (rozsah sám). Vybraná je, když [selected] je [FOLLOW_DEFAULT_SOUND_ID].
     */
    followDefault: String? = null,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val engine = koinInject<AudioEngine>()
    DisposableEffect(engine) { onDispose { engine.release() } }
    val scope = rememberCoroutineScope()
    // Smazání tónu platí pro celý účet (všechna zařízení i rozsahy, které ho používají) — proto potvrzení.
    var deleting by remember { mutableStateOf<NotificationTone?>(null) }
    val picker = rememberAttachmentPicker(
        onPicked = { picked -> picked.firstOrNull()?.let { onUpload(it.path) } },
        onError = onError,
    )

    deleting?.let { tone ->
        DeleteToneDialog(
            tone = tone,
            onConfirm = {
                deleting = null
                onDelete(tone.id)
            },
            onDismiss = { deleting = null },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
            )
            if (followDefault != null) {
                SoundOption(
                    label = stringResource(Res.string.notif_sound_follow_default),
                    summary = followDefault,
                    selected = selected == FOLLOW_DEFAULT_SOUND_ID,
                    onClick = { onSelect(FOLLOW_DEFAULT_SOUND_ID) },
                )
            }
            SoundOption(
                label = stringResource(Res.string.notif_sound_default),
                summary = stringResource(Res.string.notif_sound_default_summary),
                // Tón, který účet už nemá (smazaný jinde), nevybere nic — a TDLib ho stejně
                // zahraje jako výchozí.
                selected = selected == DEFAULT_SOUND_ID,
                onClick = { onSelect(DEFAULT_SOUND_ID) },
            )
            SoundOption(
                label = stringResource(Res.string.notif_sound_none),
                selected = selected == NO_SOUND_ID,
                onClick = { onSelect(NO_SOUND_ID) },
            )
            if (tones.isNotEmpty()) {
                Text(
                    stringResource(Res.string.notif_sound_tones),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            tones.forEach { tone ->
                SoundOption(
                    label = tone.title,
                    summary = pluralStringResource(Res.plurals.notif_sound_seconds, tone.durationSeconds, tone.durationSeconds),
                    selected = selected == tone.id,
                    onClick = { onSelect(tone.id) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val path = tonePath(tone.id) ?: return@launch
                                        runCatching { engine.load(path) }.onSuccess { engine.start() }
                                    }
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) { Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = stringResource(Res.string.notif_sound_play)) }
                            IconButton(onClick = { deleting = tone }, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.notif_sound_delete))
                            }
                        }
                    },
                )
            }
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.primary,
                    leadingIconColor = MaterialTheme.colorScheme.primary,
                ),
                leadingContent = {
                    if (uploading) {
                        LoadingIndicator(Modifier.size(24.dp))
                    } else {
                        Icon(painterResource(Res.drawable.add_24dp), contentDescription = null)
                    }
                },
                headlineContent = { Text(stringResource(Res.string.notif_sound_upload)) },
                supportingContent = limits?.let { { Text(stringResource(Res.string.notif_sound_upload_hint, (it.maxBytes / 1024).toInt(), it.maxSeconds)) } },
                modifier = Modifier.clickable(enabled = !uploading) { picker.pickDocument() },
            )
        }
    }
}

/**
 * Volba „podle výchozího nastavení" ve výběru zvuku jednoho chatu — chat vlastní zvuk nemá.
 * Do TDLib nejde nikdy, je to jen značka výběru (TDLib zná −1, 0 a kladná id tónů).
 */
internal const val FOLLOW_DEFAULT_SOUND_ID: Long = Long.MIN_VALUE

/** Řádek nese klikání celý, kolečko má `onClick = null` (viz „Obrazovka nastavení"). */
@Composable
private fun DeleteToneDialog(tone: NotificationTone, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.notif_sound_delete_title, tone.title)) },
        text = { Text(stringResource(Res.string.notif_sound_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.notif_sound_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

@Composable
private fun SoundOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}
