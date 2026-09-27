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
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.NavigationEventInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.rememberAfterKeyboardHidden
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.conversation_attach
import eu.tintera.plume.resources.conversation_attach_file
import eu.tintera.plume.resources.conversation_attach_poll
import eu.tintera.plume.resources.conversation_attach_location
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.ballot_24dp
import eu.tintera.plume.resources.conversation_attach_media
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.perm_media_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Tlačítko příloh s nabídkou Fotka / Soubor / Anketa / Poloha.
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
 * **Nabídka si nebere fokus** (`PopupProperties(focusable = false)`). Popup je na Androidu
 * samostatné okno; fokusovatelné okno převzalo fokus od pole a systém klávesnici schoval — jenže
 * animaci insetů dostává okno, které klávesnici řídí, a to už bylo okno nabídky. Hlavní okno
 * dostalo rovnou konečné insety a celá konverzace skokem spadla dolů. Teď klávesnice zůstane,
 * nabídka vyroste nad sponkou a zavírá se až s volbou položky, animovaně.
 * Nefokusovatelné okno nedostane zpět, proto [NavigationBackHandler].
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
    /** Poslat polohu; `null`, kde nejde. */
    onSendLocation: (() -> Unit)? = null,
    /** Poslat kontakt; `null`, kde nejde. */
    onShareContact: (() -> Unit)? = null,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Kdy se nabídka naposledy zavřela; obyčejné pole, ne stav — nic se podle něj nekreslí.
    val dismissedAt = remember { arrayOfNulls<TimeMark>(1) }
    // Sheet i systémový výběr jsou další okno; otevřené hned by klávesnici utrhly skokem.
    val afterKeyboardHidden = rememberAfterKeyboardHidden()

    fun pick(action: () -> Unit) = afterKeyboardHidden(action)

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = expanded,
        onBackCompleted = { expanded = false },
    )

    Box(modifier) {
        IconButton(
            onClick = {
                // Nefokusovatelná nabídka se zavře už DOTYKEM mimo sebe — i na sponce — a klik
                // po puštění by ji hned otevřel znovu. Druhé ťuknutí na sponku má nabídku zavřít.
                val justDismissed = dismissedAt[0]?.let { it.elapsedNow() < REOPEN_GUARD } == true
                expanded = !expanded && !justDismissed
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
        ) {
            Icon(painterResource(Res.drawable.attach_file_24dp), contentDescription = stringResource(Res.string.conversation_attach))
        }

        ExpressiveMenu(
            expanded = expanded,
            onDismiss = {
                expanded = false
                dismissedAt[0] = TimeSource.Monotonic.markNow()
            },
            popupPositionProvider = remember { MenuAbovePositionProvider(alignEnd = false) },
            properties = remember { PopupProperties(focusable = false) },
            groups = listOf(
                listOfNotNull(
                    MenuAction(stringResource(Res.string.conversation_attach_media), Res.drawable.perm_media_24dp) { pick(onPickMedia) }
                        .takeIf { canPickMedia },
                    MenuAction(stringResource(Res.string.conversation_attach_file), Res.drawable.description_24dp) { pick(onPickDocument) }
                        .takeIf { canPickDocument },
                    onCreatePoll?.let { create ->
                        MenuAction(stringResource(Res.string.conversation_attach_poll), Res.drawable.ballot_24dp) { pick(create) }
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

/** Klik na sponku tak brzy po zavření nabídky patří k témuž ťuknutí, které ji zavřelo. */
private val REOPEN_GUARD = 500.milliseconds

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
