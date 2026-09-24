package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.EmbeddedMediaPicker
import eu.tintera.plume.core.ui.EmbeddedMediaSelection
import eu.tintera.plume.core.ui.UiText
import kotlinx.coroutines.launch

/**
 * Galerie v sheetu nad vstupním polem: vestavěný systémový výběr fotek
 * ([eu.tintera.plume.core.ui.EmbeddedMediaPicker]).
 *
 * Proti systémovému výběru, který konverzaci na dobu vybírání nahradí celou obrazovkou, je tady
 * pořád vidět, do jakého chatu se posílá, a **vybrané fotky naskakují rovnou nad vstupní pole** —
 * odznačení v galerii je zase odebere. Kde vestavěný výběr není (starší Android, iOS), otevře
 * sponka dál systémový; rozhoduje o tom volající.
 *
 * Tentýž sheet jako výběr samolepek: celý rozbalený (`Hidden, Expanded`), s pevnou výškou plochy,
 * aby se nenatahoval a nesmršťoval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerSheet(
    selection: EmbeddedMediaSelection,
    pickedPaths: List<String>,
    allowPhotos: Boolean,
    allowVideos: Boolean,
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onUnpicked: (List<String>) -> Unit,
    onError: (UiText) -> Unit,
    onDismiss: () -> Unit,
) {
    /*
     * Rovnou celý, bez napůl rozbaleného stavu — jako výběr samolepek: galerie je vyšší než
     * polovina obrazovky a napůl rozbalený sheet by ji uřízl.
     */
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()

    /*
     * Výška galerie: víc než mřížka samolepek, ale nad sheetem musí zůstat kus konverzace —
     * jinak není poznat, kam se posílá, a to je celý smysl vestavěného výběru. Proto podíl
     * okna se stropem: na telefonu vyjde podíl, na tabletu by půlka obrazovky byla zbytečně
     * velká plocha na jednu mřížku.
     */
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val pickerHeight = minOf(windowHeight * MEDIA_PICKER_WINDOW_FRACTION, MEDIA_PICKER_MAX_HEIGHT)

    /*
     * Galerii kreslí cizí okno (`SurfaceView`), které se s posunem vrstvy NEHÝBE — tatáž
     * vlastnost, kvůli které video v prohlížeči jede do `TextureView` a ne přes `PlayerView`.
     * Během vjezdu sheetu by proto stálo na místě a doskočilo až na konci; picker se zapne až
     * po dosednutí. Do té doby drží místo prázdná plocha, aby sheet nepřeskládal výšku.
     */
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.Expanded) settled = true
    }

    fun close() {
        // Nejdřív schovat, pak teprve ohlásit: zmizení bez animace vypadá jako pád.
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { expressionSheetInsets() },
    ) {
        Box(Modifier.fillMaxWidth().height(pickerHeight + sheetBottomInset())) {
            if (settled) {
                EmbeddedMediaPicker(
                    selection = selection,
                    pickedPaths = pickedPaths,
                    allowPhotos = allowPhotos,
                    allowVideos = allowVideos,
                    // Sheet je celý rozbalený, takže galerie taky — sbalená by nešla posouvat.
                    expanded = true,
                    onPicked = onPicked,
                    onUnpicked = onUnpicked,
                    onError = onError,
                    onDone = ::close,
                    modifier = Modifier.fillMaxWidth().height(pickerHeight + sheetBottomInset()),
                )
            }
        }
    }
}

private const val MEDIA_PICKER_WINDOW_FRACTION = 0.55f
private val MEDIA_PICKER_MAX_HEIGHT = 520.dp
