package eu.tintera.plume.core.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Obraz v obraze pro video v prohlížeči médií: video hraje dál v okénku nad jinými aplikacemi.
 *
 * - **Android** posílá do okénka CELOU aktivitu (tak PiP na Androidu funguje), takže prohlížeč
 *   během [active] schová ovládání a nechá jen obraz. Akce v okénku (přehrát / pozastavit)
 *   obsluhuje [rememberPictureInPicture] přes `onTogglePlay`.
 * - **iOS** posílá do okénka jen vrstvu přehrávače (`AVPictureInPictureController`); aplikace
 *   zůstává, jak je.
 * - **Desktop** předá přehrávač vlastnímu okénku vždy navrchu a prohlížeč zavře ([leavesViewer]).
 *
 * **Do okénka se jde samo při odchodu z aplikace, ale jen když video HRAJE** (`playing`) — jako
 * YouTube. Pozastavené video okénko nepotřebuje. Ručně tlačítkem v liště videa ([enter]).
 */
@Stable
interface PictureInPicture {
    /** Zařízení obraz v obraze umí (tablet s vypnutou funkcí, starší iPad ne). */
    val supported: Boolean

    /** Video právě hraje v okénku. */
    val active: Boolean

    /**
     * Po přechodu do okénka se má prohlížeč zavřít — desktop: okénko je samostatné okno
     * a video v něm hraje dál, zatímco se v aplikaci píše. Android a iOS nechávají prohlížeč být.
     */
    val leavesViewer: Boolean get() = false

    fun enter()
}

/**
 * Obraz v obraze pro [player] s videem v poměru [aspectRatio] (šířka / výška). Platí, dokud je
 * v kompozici — prohlížeč ho skládá jen pro video, které se přehrává na celé obrazovce.
 */
@Composable
expect fun rememberPictureInPicture(
    player: VideoPlayer,
    aspectRatio: Float,
    playing: Boolean,
    onTogglePlay: () -> Unit,
): PictureInPicture
