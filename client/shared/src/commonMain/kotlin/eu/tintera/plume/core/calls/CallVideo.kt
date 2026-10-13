package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Obraz hovoru: náhled naší kamery ([CallVideoSource.Local]), nebo protistrany. Kreslí se,
 * dokud je v kompozici; [mirror] u přední kamery, ať se náhled chová jako zrcadlo.
 * [onAspectRatio] hlásí poměr stran obrazu (šířka / výška, po otočení), když se změní —
 * podle něj se tvaruje okénko obrazu v obraze.
 *
 * Jen na Androidu — jinde hovory nejsou a nic se nekreslí.
 */
@Composable
expect fun CallVideo(
    source: CallVideoSource,
    mirror: Boolean,
    modifier: Modifier = Modifier,
    onAspectRatio: (Float) -> Unit = {},
)
