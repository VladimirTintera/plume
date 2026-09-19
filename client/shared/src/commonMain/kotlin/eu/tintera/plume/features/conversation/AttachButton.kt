package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.conversation_attach
import eu.tintera.plume.resources.conversation_attach_file
import eu.tintera.plume.resources.conversation_attach_media
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.perm_media_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Tlačítko příloh s nabídkou Fotka / Soubor.
 *
 * Dvě položky, ne rovnou výběr: Photo Picker ukazuje jen fotky a výběr dokumentu nemá náhledy, takže
 * jeden systémový výběr pro obojí by byl horší v obou případech. Klávesnice se zavře, než se otevře
 * systémový výběr — jinak by po návratu vyskočila přes bublinu s nahráváním.
 *
 * **Nabídka NENÍ `DropdownMenu`.** Ten drží od okraje okna povinný odstup 48 dp
 * (`MenuVerticalMargin`) a tlačítko u spodku obrazovky v tom pásu leží — „spodkem k tlačítku"
 * tím neprošlo a menu se posadilo k oknu, viditelně nad tlačítko s mezerou. Vlastní `Popup`
 * se přichytí přímo nad tlačítko; vzhled bere z `MenuDefaults`, položky jsou standardní
 * `DropdownMenuItem`.
 */
@Composable
internal fun AttachButton(
    onPickMedia: () -> Unit,
    onPickDocument: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    // Popup žije, dokud nedoběhne odchodová animace — jinak by nabídka zmizela natvrdo.
    val visible = remember { MutableTransitionState(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun pick(action: () -> Unit) {
        visible.targetState = false
        keyboard?.hide()
        action()
    }

    Box(modifier) {
        IconButton(
            onClick = { visible.targetState = true },
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
        ) {
            Icon(painterResource(Res.drawable.attach_file_24dp), contentDescription = stringResource(Res.string.conversation_attach))
        }

        if (visible.currentState || visible.targetState) {
            Popup(
                popupPositionProvider = remember { AboveAnchorStartPositionProvider() },
                onDismissRequest = { visible.targetState = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    // Roste z rohu u tlačítka, jako M3 menu ze své kotvy.
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f, transformOrigin = TransformOrigin(0f, 1f)) +
                        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f, transformOrigin = TransformOrigin(0f, 1f)) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Surface(
                        shape = MenuDefaults.shape,
                        color = MenuDefaults.containerColor,
                        tonalElevation = MenuDefaults.TonalElevation,
                        shadowElevation = MenuDefaults.ShadowElevation,
                        // Místo na stín, jinak by ho okraj popupu uřízl.
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Column(Modifier.width(IntrinsicSize.Max).padding(vertical = 8.dp)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.conversation_attach_media)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.perm_media_24dp), contentDescription = null) },
                                onClick = { pick(onPickMedia) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.conversation_attach_file)) },
                                leadingIcon = { Icon(painterResource(Res.drawable.description_24dp), contentDescription = null) },
                                onClick = { pick(onPickDocument) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nabídka spodkem nad kotvu, levou hranou u její levé hrany; nevejde-li se nad ni, pod ni.
 * Vodorovně se drží uvnitř okna.
 */
private class AboveAnchorStartPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val start = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val x = start.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        // Mezeru od tlačítka dělá okraj na stín kolem nabídky.
        val above = anchorBounds.top - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom
        return IntOffset(x, y)
    }
}
