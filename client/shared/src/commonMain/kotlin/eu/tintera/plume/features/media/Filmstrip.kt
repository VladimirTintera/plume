package eu.tintera.plume.features.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.wheelScrollsHorizontally
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_spoiler
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh náhledů pod snímkem v galerii (široké okno, viz [filmstripFits]): přes desítky fotek se
 * jde přeskočit jedním kliknutím místo listování po jedné.
 *
 * - **Aktuální náhled stojí uprostřed** — odsazení pruhu je půl šířky minus půl náhledu, takže
 *   posun na položku ji vycentruje i na krajích. Jede za pagerem: listování posune pruh, klik
 *   na náhled posune pager ([onSelect]).
 * - **Velký skok se neanimuje.** Při otevření má výřez jen otevřenou fotku a sousedé přibudou
 *   až pod ní; animace přes dvacet náhledů by při každém otevření projížděla celým pruhem.
 * - **Ukazuje NAČTENÝ výřez** a u jeho okraje řekne, ať se dotáhne další ([onVisibleRange]);
 *   prodloužení zleva pruhem nepohne, `LazyRow` drží položku podle klíče.
 * - **Kolečko myši pruhem posouvá do strany** (`wheelScrollsHorizontally`, zvenku — `LazyRow`
 *   svislé kolečko nespotřebuje).
 * - **Zakrytý náhled se nekreslí** (spoiler a citlivý obsah, dokud se neodkryje): rozostření
 *   přes `Modifier.blur` funguje až od Androidu 12, pod ním by obsah ukázalo. Místo něj plocha
 *   s ikonou.
 */
@Composable
internal fun Filmstrip(
    items: List<ViewerItem>,
    currentIndex: Int,
    /** Zprávy s odkrytým spoilerem (`RevealedSpoilers`). */
    revealed: Set<Long>,
    onSelect: (index: Int) -> Unit,
    onVisibleRange: (first: Int, last: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(FILMSTRIP_HEIGHT)) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
        val side = ((maxWidth - THUMB_SIZE) / 2).coerceAtLeast(0.dp)

        LaunchedEffect(currentIndex) {
            if (currentIndex !in items.indices) return@LaunchedEffect
            val distance = abs(currentIndex - listState.firstVisibleItemIndex)
            if (distance > ANIMATED_JUMP) listState.scrollToItem(currentIndex) else listState.animateScrollToItem(currentIndex)
        }

        val currentOnVisibleRange by rememberUpdatedState(onVisibleRange)
        LaunchedEffect(listState) {
            snapshotFlow {
                val visible = listState.layoutInfo.visibleItemsInfo
                (visible.firstOrNull()?.index ?: -1) to (visible.lastOrNull()?.index ?: -1)
            }
                .distinctUntilChanged()
                .collect { (first, last) -> if (first >= 0) currentOnVisibleRange(first, last) }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(THUMB_GAP),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().wheelScrollsHorizontally(listState),
        ) {
            itemsIndexed(items, key = { _, item -> item.messageId }) { index, item ->
                FilmstripThumb(
                    item = item,
                    selected = index == currentIndex,
                    hidden = item.media.covered && item.messageId !in revealed,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun FilmstripThumb(item: ViewerItem, selected: Boolean, hidden: Boolean, onClick: () -> Unit) {
    // Vybraný náhled je celý a jasný, ostatní menší a ztlumené — jako u pageru, kde aktuální
    // karta převažuje nad vykukujícími sousedy.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else UNSELECTED_SCALE,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "filmstrip-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else UNSELECTED_ALPHA,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "filmstrip-alpha",
    )
    val video = item.media is MessageMedia.Video
    val label = stringResource(
        when {
            hidden -> Res.string.media_spoiler
            video -> Res.string.media_video
            else -> Res.string.media_photo
        },
    )
    Box(
        Modifier
            .size(THUMB_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(ShapeDefaults.Small)
            .then(if (selected) Modifier.border(SELECTED_BORDER, ON_SCRIM, ShapeDefaults.Small) else Modifier)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val fileId = item.media.thumbnailFile
        if (hidden || fileId == null) {
            Box(Modifier.fillMaxSize().background(ON_SCRIM.copy(alpha = PLACEHOLDER_ALPHA)))
        } else {
            RemoteImage(
                fileId = fileId,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                background = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            hidden -> ThumbBadge(Res.drawable.visibility_off_24dp)
            video -> ThumbBadge(Res.drawable.play_arrow_24dp)
        }
    }
}

@Composable
private fun ThumbBadge(icon: org.jetbrains.compose.resources.DrawableResource) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = ON_SCRIM,
        modifier = Modifier.size(BADGE_SIZE),
    )
}

/** Zakryté spoilerem nebo jako citlivé; náhled se nekreslí, dokud se neodkryje. */
private val MessageMedia.covered: Boolean
    get() = when (this) {
        is MessageMedia.Photo -> hasSpoiler || isSensitive
        is MessageMedia.Video -> hasSpoiler || isSensitive
        else -> false
    }

/** Malý náhled: ten kreslila konverzace, takže je skoro vždycky na disku. */
private val MessageMedia.thumbnailFile: Int?
    get() = when (this) {
        is MessageMedia.Photo -> thumbnailFileId ?: fileId
        is MessageMedia.Video -> thumbnailFileId
        else -> null
    }

/**
 * Jestli se pruh do okna vejde: široké (Expanded, od 840 dp) a zároveň dost vysoké. Telefon na
 * šířku je přes 840 dp široký, ale vysoký kolem 400 dp — pruh by ukrojil další kus z fotky,
 * která už tak má mezi lištami málo místa.
 */
internal fun filmstripFits(width: Dp, height: Dp): Boolean = width >= FILMSTRIP_MIN_WIDTH && height >= FILMSTRIP_MIN_HEIGHT

/** Výška pruhu i se vzduchem nad a pod náhledy; o tolik se zmenší místo pro kartu. */
internal val FILMSTRIP_HEIGHT = 72.dp

private val THUMB_SIZE = 56.dp
private val THUMB_GAP = 6.dp
private val SELECTED_BORDER = 2.dp
private val BADGE_SIZE = 20.dp
private const val UNSELECTED_SCALE = 0.86f
private const val UNSELECTED_ALPHA = 0.6f
private const val PLACEHOLDER_ALPHA = 0.16f

/** Skok o víc náhledů se neanimuje (otevření, Home / End). */
private const val ANIMATED_JUMP = 12

private val FILMSTRIP_MIN_WIDTH = 840.dp
private val FILMSTRIP_MIN_HEIGHT = 600.dp
