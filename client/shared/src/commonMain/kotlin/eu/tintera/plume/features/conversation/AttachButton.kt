package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.conversation_attach
import eu.tintera.plume.resources.conversation_attach_file
import eu.tintera.plume.resources.conversation_attach_poll
import eu.tintera.plume.resources.ballot_24dp
import eu.tintera.plume.resources.conversation_attach_media
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.perm_media_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Tlačítko příloh s nabídkou Fotka / Soubor / Anketa.
 *
 * Dvě položky, ne rovnou výběr: Photo Picker ukazuje jen fotky a výběr dokumentu nemá náhledy, takže
 * jeden systémový výběr pro obojí by byl horší v obou případech. Klávesnice se zavře, než se
 * otevře systémový výběr — jinak by po návratu vyskočila přes bublinu s nahráváním.
 *
 * Co skupina zakazuje, se nenabízí ([canPickMedia], [canPickDocument]). Bez jediné položky se
 * tlačítko nekreslí vůbec — o to se stará volající (`ContentAccess.anyAttachment`), protože
 * prázdná nabídka by byla horší než chybějící sponka.
 *
 * **Nabídka má vlastní umístění** ([AboveAnchorStartPositionProvider]). Výchozí umístění M3 menu
 * drží od okraje okna povinný odstup 48 dp (`MenuVerticalMargin`) a tlačítko u spodku obrazovky
 * v tom pásu leží — „spodkem k tlačítku" tím neprošlo a menu se posadilo k oknu, viditelně nad
 * tlačítko s mezerou. Vzhled je jinak tatáž Expressive nabídka jako všude ([ExpressiveMenu]).
 */
@Composable
internal fun AttachButton(
    onPickMedia: () -> Unit,
    onPickDocument: () -> Unit,
    /** Skupina smí zakázat jen některé druhy; zakázaná položka se nenabízí. */
    canPickMedia: Boolean = true,
    canPickDocument: Boolean = true,
    /** Založit anketu; `null`, kde anketa nejde (soukromý chat s člověkem, zakázané právo). */
    onCreatePoll: (() -> Unit)? = null,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun pick(action: () -> Unit) {
        keyboard?.hide()
        action()
    }

    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
        ) {
            Icon(painterResource(Res.drawable.attach_file_24dp), contentDescription = stringResource(Res.string.conversation_attach))
        }

        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            popupPositionProvider = remember { AboveAnchorStartPositionProvider() },
            groups = listOf(
                listOfNotNull(
                    MenuAction(stringResource(Res.string.conversation_attach_media), Res.drawable.perm_media_24dp) { pick(onPickMedia) }
                        .takeIf { canPickMedia },
                    MenuAction(stringResource(Res.string.conversation_attach_file), Res.drawable.description_24dp) { pick(onPickDocument) }
                        .takeIf { canPickDocument },
                    onCreatePoll?.let { create ->
                        MenuAction(stringResource(Res.string.conversation_attach_poll), Res.drawable.ballot_24dp) { pick(create) }
                    },
                ),
            ),
        )
    }
}

/**
 * Nabídka spodkem nad kotvu, levou hranou u její levé hrany; nevejde-li se nad ni, pod ni.
 * Vodorovně se drží uvnitř okna. [transformOrigin] je roh u tlačítka — z něj nabídka vyroste.
 */
private class AboveAnchorStartPositionProvider : DropdownMenuPopupPositionProvider {
    override var transformOrigin: TransformOrigin = TransformOrigin(0f, 1f)
        private set

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
        val above = anchorBounds.top - popupContentSize.height
        val placedAbove = above >= 0
        val originX = if (layoutDirection == LayoutDirection.Ltr) 0f else 1f
        transformOrigin = TransformOrigin(originX, if (placedAbove) 1f else 0f)
        return IntOffset(x, if (placedAbove) above else anchorBounds.bottom)
    }
}
