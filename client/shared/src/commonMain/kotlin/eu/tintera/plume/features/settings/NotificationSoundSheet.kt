package eu.tintera.plume.features.settings

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
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
    val sheetState = rememberExpandedSheetState()
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
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SheetTitle(stringResource(title), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp))
            // Vestavěné volby jako jeden blok, vlastní tóny jako druhý pod nadpisem.
            val base = buildList {
                if (followDefault != null) {
                    add(Triple(FOLLOW_DEFAULT_SOUND_ID, stringResource(Res.string.notif_sound_follow_default), followDefault))
                }
                add(Triple(DEFAULT_SOUND_ID, stringResource(Res.string.notif_sound_default), stringResource(Res.string.notif_sound_default_summary)))
                add(Triple(NO_SOUND_ID, stringResource(Res.string.notif_sound_none), null))
            }
            Column(Modifier.selectableGroup()) {
                base.forEachIndexed { index, (id, label, summary) ->
                    ChoiceListItem(
                        label = label,
                        // Tón, který účet už nemá (smazaný jinde), nevybere nic — a TDLib ho stejně
                        // zahraje jako výchozí.
                        selected = selected == id,
                        index = index,
                        count = base.size,
                        onClick = { onSelect(id) },
                        supporting = summary,
                    )
                }
            }
            if (tones.isNotEmpty()) {
                Text(
                    stringResource(Res.string.notif_sound_tones),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
                )
                Column(Modifier.selectableGroup()) {
                    tones.forEachIndexed { index, tone ->
                        ChoiceListItem(
                            label = tone.title,
                            selected = selected == tone.id,
                            index = index,
                            count = tones.size,
                            onClick = { onSelect(tone.id) },
                            supporting = pluralStringResource(Res.plurals.notif_sound_seconds, tone.durationSeconds, tone.durationSeconds),
                            actions = {
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
                            },
                        )
                    }
                }
            }
            // Nahrání je akce, ne volba — vlastní blok pod výběrem.
            Spacer(Modifier.height(GROUP_SPACING))
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
                modifier = Modifier
                    .groupedItem(index = 0, count = 1, color = MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable(enabled = !uploading) { picker.pickDocument() },
            )
        }
    }
}

/**
 * Volba „podle výchozího nastavení" ve výběru zvuku jednoho chatu — chat vlastní zvuk nemá.
 * Do TDLib nejde nikdy, je to jen značka výběru (TDLib zná −1, 0 a kladná id tónů).
 */
internal const val FOLLOW_DEFAULT_SOUND_ID: Long = Long.MIN_VALUE

@Composable
private fun DeleteToneDialog(tone: NotificationTone, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.notif_sound_delete_title, tone.title),
        text = stringResource(Res.string.notif_sound_delete_text),
        confirmLabel = stringResource(Res.string.notif_sound_delete_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = Res.drawable.delete_24dp,
        destructive = true,
    )
}
