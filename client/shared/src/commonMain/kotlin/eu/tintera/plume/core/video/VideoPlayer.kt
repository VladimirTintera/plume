package eu.tintera.plume.core.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Přehrávač videa ze souboru na disku.
 *
 * Android: Media3 ExoPlayer, iOS: AVPlayer. Vzniká v kompozici ([rememberVideoPlayer]) a s ní
 * se i uvolní — přehrávač drží dekodér a odchod ze stránky ho musí vrátit.
 *
 * Stav (pozice, délka, hraje, dohráno) se **čte opakovaně**, přehrávače průběh samy nehlásí
 * v podobě, kterou by šlo sdílet. Volá se jen z hlavního vlákna.
 */
expect class VideoPlayer {
    /**
     * Načte soubor.
     *
     * [loop] dohrané video pustí znovu — pro GIF, který má běžet pořád dokola.
     * [muted] ztlumí zvuk a **nesahá na zvukovou relaci systému**: GIF nemá co ztišit hudbu
     * uživatele, kdežto puštěné video ano.
     */
    fun load(path: String, loop: Boolean = false, muted: Boolean = false)

    /** Pustí; u dohraného videa od začátku. */
    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    val positionMs: Long

    /** Délka v ms; `0`, dokud ji přehrávač nezjistí. */
    val durationMs: Long

    val isPlaying: Boolean

    val isEnded: Boolean
}

@Composable
expect fun rememberVideoPlayer(): VideoPlayer

/** Obraz přehrávače, vepsaný do plochy se zachovaným poměrem stran. Gesta nepřijímá. */
@Composable
expect fun VideoSurface(player: VideoPlayer, modifier: Modifier = Modifier)
