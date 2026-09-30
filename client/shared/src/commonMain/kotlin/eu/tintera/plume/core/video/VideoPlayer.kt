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

    /**
     * Načte soubor, který se ještě stahuje, a **čeká na data místo aby skončil**.
     *
     * Volá se jen tam, kde to platforma umí ([videoStreamingSupported]) a kde má video index na
     * začátku (`MessageMedia.Video.supportsStreaming`) — jinak z rozestavěného souboru přehrávač
     * nepozná nic.
     */
    fun loadStreaming(source: StreamingSource, loop: Boolean = false, muted: Boolean = false)

    /** Pustí; u dohraného videa od začátku. */
    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /**
     * Rychlost přehrávání (1×, 1,5×, 2×). Platí hned, i uprostřed hrajícího videa, a přežije
     * pauzu i přehrání od začátku — iOS `play()` rychlost jinak vrací na 1×.
     */
    fun setSpeed(rate: Float)

    /**
     * Ztlumí nebo zapne zvuk za běhu. Na rozdíl od `muted` v [load] **na zvukovou relaci nesahá**:
     * video, které uživatel pustil a jen ztišil, drží audio focus dál (hudba se nerozezní pod
     * obrazem, který pořád běží).
     */
    fun setMuted(muted: Boolean)

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

/**
 * Rozestavěný soubor pro streamování.
 *
 * [path] je soubor TDLib, který na disku roste. Číst se z něj smí **jen uvnitř [downloaded]** —
 * mimo ten úsek jsou díry: TDLib stahuje od `downloadOffset` a souvisle má jen
 * `downloadedPrefixSize` bajtů.
 *
 * [requestFrom] přesune stahování jinam. **Bez toho streamování nestačí:** přehrávač si hned na
 * začátku řekne o `moov` atom, a ten u většiny videí leží na KONCI souboru — příznak
 * `supportsStreaming` od serveru říká jen to, co tvrdí odesílatel, ne kde index doopravdy je.
 *
 * Obě funkce se volají z vlákna přehrávače, ne z kompozice.
 */
class StreamingSource(
    val path: String,
    val totalSize: Long,
    val downloaded: () -> LongRange,
    val requestFrom: (Long) -> Unit,
)

/**
 * Umí tahle platforma přehrát rozestavěný soubor?
 *
 * Android ano (vlastní `DataSource` nad souborem TDLib). iOS zatím ne — `AVPlayer` by chtěl
 * `AVAssetResourceLoaderDelegate`, což je samostatný kus práce; tam se čeká na celý soubor
 * jako dosud.
 */
expect val videoStreamingSupported: Boolean
