package eu.tintera.plume.wear

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox

/**
 * Otevřená fotka: soubor, rozměry (kvůli poměru stran) a co ukázat hned — bitmapa, kterou už
 * kreslí bublina. Větší dekódování na displej přijde po ní; bez náhledu by sdílený prvek letěl
 * jako prázdný obdélník.
 */
class OpenedPhoto(val fileId: Int, val width: Int, val height: Int, val preview: ImageBitmap?)

/** Sdílený přechod fotky z bubliny přes celý displej, jak ho vidí bublina ([MessagePhoto]). */
@OptIn(ExperimentalSharedTransitionApi::class)
class PhotoTransition(val scope: SharedTransitionScope, val opened: OpenedPhoto?, val open: (OpenedPhoto) -> Unit)

val LocalPhotoTransition = compositionLocalOf<PhotoTransition?> { null }

/** Klíč sdíleného prvku: bublina a prohlížeč s touž fotkou se přes něj najdou. */
fun photoElementKey(fileId: Int): String = "photo-$fileId"

/**
 * Konverzace s prohlížečem fotky jako vrstvou nad ní: fotka se rozletí ze zmenšeniny v bublině
 * přes celý displej a při zavření se do ní vrátí (sdílený prvek).
 *
 * **Vrstva, ne cesta navigace.** `SwipeDismissableNavHost` sdílené prvky neumí (cíle nedostávají
 * `AnimatedVisibilityScope`), a dokud byla fotka vlastní cestou, konverzace navíc opouštěla
 * kompozici. Konverzace teď zůstává pod fotkou celou dobu, i se svou pozicí.
 *
 * Zavírá se tahem doprava (`SwipeToDismissBox`, jako všude na hodinkách) i tlačítkem zpět;
 * do bubliny se fotka vrací letem jen po tlačítku — po tahu už je pryč z displeje.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoViewerHost(content: @Composable () -> Unit) {
    var opened by remember { mutableStateOf<OpenedPhoto?>(null) }
    // Zavřeno tahem: fotka už odjela z displeje, zpáteční let do bubliny by ji vrátil doprostřed
    // a teprve pak smrskl (sdílený prvek startuje z místa PŘED tahem — ověřeno na emulátoru).
    var swiped by remember { mutableStateOf(false) }
    // Naposledy otevřená fotka zůstává pro odchodovou animaci, i když už `opened` je `null`.
    // Obyčejný držák, ne stav: zápis do stavu během kompozice by ji spouštěl znovu.
    val last = remember { arrayOfNulls<OpenedPhoto>(1) }
    opened?.let { last[0] = it }
    SharedTransitionLayout {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalPhotoTransition provides PhotoTransition(this@SharedTransitionLayout, opened) {
                swiped = false
                opened = it
            }) {
                content()
            }
            val motion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = opened != null,
                enter = fadeIn(motion.defaultEffectsSpec()),
                // Po tahu hned pryč: `SwipeToDismissBox` po zavření vrátí obsah doprostřed a při
                // doznívání by fotka znovu probleskla.
                exit = if (swiped) ExitTransition.None else fadeOut(motion.fastEffectsSpec()),
            ) {
                val photo = opened ?: last[0] ?: return@AnimatedVisibility
                BackHandler { opened = null }
                SwipeToDismissBox(
                    onDismissed = {
                        swiped = true
                        opened = null
                    },
                    // Pod fotkou je živá konverzace, ne obrazovka navigace — závoj netřeba.
                    backgroundScrimColor = Color.Transparent,
                    contentScrimColor = Color.Transparent,
                ) { isBackground ->
                    if (!isBackground) {
                        val shared = if (swiped) Modifier else Modifier.sharedElement(rememberSharedContentState(photoElementKey(photo.fileId)), this@AnimatedVisibility)
                        PhotoViewer(photo, shared)
                    }
                }
            }
        }
    }
}

/**
 * Fotka přes celý displej na černém pozadí (fotka je obsah, ne prvek rozhraní). Bere tutéž
 * variantu jako bublina, jen dekódovanou na velikost displeje — po bublině je obvykle stažená.
 * Velikost v poměru stran fotky (ne `Fit` přes celý displej): sdílený prvek pak letí mezi dvěma
 * obdélníky téhož tvaru a obraz se cestou nedeformuje.
 */
@Composable
private fun PhotoViewer(photo: OpenedPhoto, imageModifier: Modifier) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val ratio = if (photo.width > 0 && photo.height > 0) photo.width.toFloat() / photo.height else 1f
        val width = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
        val height = width / ratio
        val screenPx = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }
        val bitmap = rememberPhotoBitmap(photo.fileId, screenPx) ?: photo.preview
        Box(imageModifier.size(width, height)) {
            bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }
}
