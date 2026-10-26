package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.contact_page_24dp
import eu.tintera.plume.resources.conversation_attach_contact
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.rememberAfterKeyboardHidden
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.history_24dp
import eu.tintera.plume.resources.recent_files_title
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.conversation_attach
import eu.tintera.plume.resources.conversation_attach_file
import eu.tintera.plume.resources.conversation_attach_poll
import eu.tintera.plume.resources.conversation_attach_checklist
import eu.tintera.plume.resources.checklist_24dp
import eu.tintera.plume.resources.conversation_attach_location
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.ballot_24dp
import eu.tintera.plume.resources.conversation_attach_media
import eu.tintera.plume.resources.conversation_attach_record_video
import eu.tintera.plume.resources.conversation_attach_take_photo
import eu.tintera.plume.resources.photo_camera_24dp
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.perm_media_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Tlačítko příloh s nabídkou Fotka / Vyfotit / Natočit video / Soubor / Anketa / Poloha.
 *
 * Dvě položky, ne rovnou výběr: Photo Picker ukazuje jen fotky a výběr dokumentu nemá náhledy, takže
 * jeden systémový výběr pro obojí by byl horší v obou případech. Klávesnice se zavře, než se
 * otevře systémový výběr — jinak by po návratu vyskočila přes bublinu s nahráváním — a výběr se
 * otevře až po jejím sjetí ([rememberAfterKeyboardHidden]).
 *
 * Co skupina zakazuje, se nenabízí ([canPickMedia], [canPickDocument]). Bez jediné položky se
 * tlačítko nekreslí vůbec — o to se stará volající (`ContentAccess.anyAttachment`), protože
 * prázdná nabídka by byla horší než chybějící sponka.
 *
 * **Nabídka má vlastní umístění** ([AboveAnchorStartPositionProvider]). Výchozí umístění M3 menu
 * drží od okraje okna povinný odstup 48 dp (`MenuVerticalMargin`) a tlačítko u spodku obrazovky
 * v tom pásu leží — „spodkem k tlačítku" tím neprošlo a menu se posadilo k oknu, viditelně nad
 * tlačítko s mezerou. Vzhled je jinak tatáž Expressive nabídka jako všude ([ExpressiveMenu]).
 *
 * Klávesnice pod nabídkou zůstane (okno nabídky si ji nebere, viz `MenuPopupProperties`);
 * schová se až s volbou položky, animovaně.
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
    /** Založit seznam úkolů; `null`, kde nejde (kanál, tajný chat, účet bez Premium). */
    onCreateChecklist: (() -> Unit)? = null,
    /** Poslat polohu; `null`, kde nejde. */
    onSendLocation: (() -> Unit)? = null,
    /** Poslat kontakt; `null`, kde nejde. */
    onShareContact: (() -> Unit)? = null,
    /** Znovu poslat nedávno odeslaný soubor; `null`, kde soubory nejdou. */
    onPickRecentFiles: (() -> Unit)? = null,
    /**
     * Vyfotit (`video = false`) nebo natočit video systémovým fotoaparátem; `null`, kde zařízení
     * fotoaparát nemá. Položka se nabídne, jen když chat přijme ten druh ([canPickMedia] nestačí,
     * skupina smí povolit fotky a zakázat videa).
     */
    onCapture: ((video: Boolean) -> Unit)? = null,
    canTakePhoto: Boolean = canPickMedia,
    canRecordVideo: Boolean = canPickMedia,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Sheet i systémový výběr jsou další okno; otevřené hned by klávesnici utrhly skokem.
    val afterKeyboardHidden = rememberAfterKeyboardHidden()

    fun pick(action: () -> Unit) = afterKeyboardHidden(action)

    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painterResource(Res.drawable.attach_file_24dp), contentDescription = stringResource(Res.string.conversation_attach))
        }

        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            popupPositionProvider = remember { MenuAbovePositionProvider(alignEnd = false) },
            groups = listOf(
                listOfNotNull(
                    MenuAction(stringResource(Res.string.conversation_attach_media), Res.drawable.perm_media_24dp) { pick(onPickMedia) }
                        .takeIf { canPickMedia },
                    // Hned za galerií: je to tentýž druh přílohy, jen z jiného zdroje.
                    onCapture?.let { capture ->
                        MenuAction(stringResource(Res.string.conversation_attach_take_photo), Res.drawable.photo_camera_24dp) {
                            pick { capture(false) }
                        }.takeIf { canTakePhoto }
                    },
                    onCapture?.let { capture ->
                        MenuAction(stringResource(Res.string.conversation_attach_record_video), Res.drawable.videocam_24dp) {
                            pick { capture(true) }
                        }.takeIf { canRecordVideo }
                    },
                    MenuAction(stringResource(Res.string.conversation_attach_file), Res.drawable.description_24dp) { pick(onPickDocument) }
                        .takeIf { canPickDocument },
                    onPickRecentFiles?.let { open ->
                        MenuAction(stringResource(Res.string.recent_files_title), Res.drawable.history_24dp) { pick(open) }
                    },
                    onCreatePoll?.let { create ->
                        MenuAction(stringResource(Res.string.conversation_attach_poll), Res.drawable.ballot_24dp) { pick(create) }
                    },
                    onCreateChecklist?.let { create ->
                        MenuAction(stringResource(Res.string.conversation_attach_checklist), Res.drawable.checklist_24dp) { pick(create) }
                    },
                    onSendLocation?.let { send ->
                        MenuAction(stringResource(Res.string.conversation_attach_location), Res.drawable.location_on_24dp) { pick(send) }
                    },
                    onShareContact?.let { share ->
                        MenuAction(stringResource(Res.string.conversation_attach_contact), Res.drawable.contact_page_24dp) { pick(share) }
                    },
                ),
            ),
        )
    }
}

/**
 * Nabídka spodkem nad kotvu, začátkem u jejího začátku (s [alignEnd] koncem u konce — pro
 * tlačítko u pravého okraje); nevejde-li se nad ni, pod ni. Vodorovně se drží uvnitř okna.
 * [transformOrigin] je roh u tlačítka — z něj nabídka vyroste.
 */
internal class MenuAbovePositionProvider(private val alignEnd: Boolean) : DropdownMenuPopupPositionProvider {
    override var transformOrigin: TransformOrigin = TransformOrigin(0f, 1f)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val leftAligned = (layoutDirection == LayoutDirection.Ltr) != alignEnd
        val start = if (leftAligned) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val x = start.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height
        val placedAbove = above >= 0
        val originX = if (leftAligned) 0f else 1f
        transformOrigin = TransformOrigin(originX, if (placedAbove) 1f else 0f)
        return IntOffset(x, if (placedAbove) above else anchorBounds.bottom)
    }
}
