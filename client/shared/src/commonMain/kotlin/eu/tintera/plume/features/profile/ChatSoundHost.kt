package eu.tintera.plume.features.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatNotificationPrefs
import eu.tintera.plume.core.telegram.NotificationTone
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.settings.FOLLOW_DEFAULT_SOUND_ID
import eu.tintera.plume.features.settings.NotificationSoundSheet
import eu.tintera.plume.features.settings.NotificationSoundsViewModel
import eu.tintera.plume.features.settings.soundLabel
import eu.tintera.plume.features.settings.title
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notif_sound_row
import eu.tintera.plume.resources.notif_sound_scope_value
import eu.tintera.plume.resources.profile_notif_default
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Tóny účtu a zápis zvuku chatu — tentýž ViewModel jako výběr zvuku rozsahu v Nastavení. */
@Composable
internal fun rememberChatSounds(chatId: Long): NotificationSoundsViewModel = koinViewModel(key = "chat-sounds-$chatId")

/** „Výchozí · výchozí nastavení", „Bez zvuku", název tónu — do sheetu upozornění v profilu. */
@Composable
internal fun chatSoundLabel(prefs: ChatNotificationPrefs, tones: List<NotificationTone>): String {
    val label = soundLabel(prefs.soundId, tones)
    return if (prefs.soundIsDefault) stringResource(Res.string.profile_notif_default, label) else label
}

/**
 * Výběr zvuku jednoho chatu: navíc proti rozsahu volba „Podle výchozího nastavení" (chat
 * vlastní zvuk nemá a mění se s rozsahem). Volba se zapíše hned, jako v Nastavení.
 */
@Composable
internal fun ChatSoundHost(
    viewModel: NotificationSoundsViewModel,
    chatId: Long,
    prefs: ChatNotificationPrefs?,
    open: Boolean,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tones by viewModel.tones.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        scope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }
    if (!open || prefs == null) return
    // Co zní v rozsahu: „Soukromé chaty: Výchozí" — aby bylo vidět, co volba znamená.
    val followSummary = stringResource(
        Res.string.notif_sound_scope_value,
        stringResource(prefs.scope.title),
        soundLabel(prefs.defaultSoundId, tones),
    )
    NotificationSoundSheet(
        title = Res.string.notif_sound_row,
        selected = if (prefs.soundIsDefault) FOLLOW_DEFAULT_SOUND_ID else prefs.soundId,
        tones = tones,
        limits = state.limits,
        uploading = state.uploading,
        onSelect = { soundId -> viewModel.selectForChat(chatId, soundId.takeIf { it != FOLLOW_DEFAULT_SOUND_ID }) },
        onUpload = viewModel::upload,
        onDelete = viewModel::delete,
        tonePath = viewModel::tonePath,
        onError = { message -> scope.launch { snackbarHostState.showSnackbar(message.resolveNow()) } },
        onDismiss = onClose,
        followDefault = followSummary,
    )
}
