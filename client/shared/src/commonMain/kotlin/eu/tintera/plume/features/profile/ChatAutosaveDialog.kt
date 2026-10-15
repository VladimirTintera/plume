package eu.tintera.plume.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.files.autosavesToGallery
import eu.tintera.plume.core.files.rememberSavePermission
import eu.tintera.plume.core.telegram.AutosaveChatType
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.settings.AutosaveScopeDialog
import eu.tintera.plume.features.settings.autosaveSummary
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.autosave_chat_default
import eu.tintera.plume.resources.autosave_chat_hint
import eu.tintera.plume.resources.autosave_group
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * ViewModel ukládání do galerie pro profil chatu; `null` tam, kde aplikace do galerie
 * automaticky neukládá (desktop) — řádek se pak v profilu neukáže.
 */
@Composable
internal fun rememberChatAutosaveViewModel(chatId: Long): ChatAutosaveViewModel? =
    if (autosavesToGallery) {
        koinViewModel(key = "autosave-$chatId", parameters = { parametersOf(chatId) })
    } else {
        null
    }

/** Hodnota řádku v profilu: „Fotky", u výchozího pravidla „Fotky (výchozí)". */
@Composable
internal fun chatAutosaveText(autosave: ChatAutosave): String {
    val summary = autosaveSummary(autosave.settings)
    return if (autosave.isException) summary else stringResource(Res.string.autosave_chat_default, summary)
}

/** Popisek řádku v profilu. */
@Composable
internal fun chatAutosaveLabel(): String = stringResource(Res.string.autosave_group)

/**
 * Dialog ukládání do galerie pro jeden chat a hláška při neúspěchu. Volby jsou tytéž jako
 * u výchozích pravidel v Úložišti; „Odebrat výjimku" jen tam, kde výjimka je.
 */
@Composable
internal fun ChatAutosaveHost(
    viewModel: ChatAutosaveViewModel,
    chatId: Long,
    type: AutosaveChatType?,
    open: Boolean,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        viewModel.consumeError()
        scope.launch { snackbarHostState.showSnackbar(error.resolveNow()) }
    }
    val permission = rememberSavePermission()
    val current = type?.let { state.settings?.chatAutosave(chatId, it) }
    if (!open || type == null || current == null) return
    AutosaveScopeDialog(
        title = stringResource(Res.string.autosave_group),
        initial = current.settings,
        canRemove = current.isException,
        hint = stringResource(Res.string.autosave_chat_hint),
        onConfirm = { changed ->
            onClose()
            // Zapnutí potřebuje zápis do galerie (iOS, Android do verze 9); nastavení účtu se
            // změní tak jako tak — patří i jiným zařízením. Stejně jako v Úložišti.
            if (changed.enabled && !current.settings.enabled) permission.request { }
            viewModel.save(changed, type)
        },
        onRemove = {
            onClose()
            viewModel.removeException()
        },
        onDismiss = onClose,
    )
}
