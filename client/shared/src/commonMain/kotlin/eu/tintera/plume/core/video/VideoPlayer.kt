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
     * Volá se jen tam, kde to platforma umí ([videoStreamingSupported]), a u původního souboru
     * jen s příznakem `supportsStreaming` nebo u MP4 / MOV ([streamOriginalVideo]). Index na konci
     * souboru nevadí: přehrávač si o něj řekne a [StreamingSource.requestFrom] tam přesune
     * stahování.
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

    /**
     * Přehrávání skončilo chybou (soubor zmizel, nejde dekódovat). Přehrávač pak stojí a sám se
     * nehne; [play] ho zkusí připravit znovu.
     */
    val isFailed: Boolean

    /**
     * Poměr stran obrazu, jak se má ukázat — s poměrem pixelu (SAR) a otočením z metadat
     * souboru; `null`, dokud ho přehrávač nezná.
     *
     * **Rozměry ve zprávě nestačí**: Telegram uvádí, co poslal odesílatel, a u videa
     * s nečtvercovými pixely to jsou uložené rozměry (720 × 720 u obrazu, který se ukazuje
     * 9 : 16). Karta prohlížeče se podle nich kreslila jako čtverec.
     */
    val aspectRatio: Float?
}

@Composable
expect fun rememberVideoPlayer(): VideoPlayer

/** Obraz přehrávače, vepsaný do plochy se zachovaným poměrem stran. Gesta nepřijímá. */
@Composable
expect fun VideoSurface(player: VideoPlayer, modifier: Modifier = Modifier)

/**
 * Umí tahle platforma přehrát rozestavěný soubor?
 *
 * Android i iOS ano: vlastní zdroj nad souborem TDLib (`PartialFileDataSource` pro ExoPlayer,
 * `StreamingResourceLoader` pro AVPlayer). Desktop taky (`StreamingIo`, vlastní vstup FFmpegu).
 */
expect val videoStreamingSupported: Boolean

/**
 * Umí platforma streamovat i FRAGMENTOVANÉ MP4 — přepočítané kvality videa
 * (`MessageMedia.Video.alternatives`, server je dělá pro HLS)? Android ano (ExoPlayer čte
 * `mehd` i `sidx`), iOS přes HLS ([videoFragmentedStreamingNeedsHls]). Kde ne (desktop), stahuje
 * se přepočítaná kvalita celá a automatika na síti bez stropu dá přednost původnímu souboru.
 */
expect val videoFragmentedStreamingSupported: Boolean

/**
 * Streamuje platforma přepočítanou kvalitu jen jako HLS ([StreamingSource.hlsPlaylist])? iOS
 * ano: AVPlayer přes vlastní zdroj fragmentované MP4 nezvládne (délku skládá z přečtených
 * fragmentů, skok za ně skončí koncem videa), HLS seznam s rozsahy bajtů ano. Seznam se před
 * streamováním stáhne; Android ho nepotřebuje a nestahuje.
 */
expect val videoFragmentedStreamingNeedsHls: Boolean

/**
 * Čím zařízení dekóduje video v kodeku [codec] (`h264`, `h265`, `av01`… jak ho jmenuje TDLib
 * u přepočítané kvality) a v rozměrech [width] × [height].
 *
 * Server přepočítává každou velikost do h264 i AV1; kvalitu, kterou telefon nepřehraje, nabízet
 * nemá, a softwarové dekódování (AV1 na telefonu bez podpory v čipu) pálí baterii. Na Androidu
 * se ptá `MediaCodecList`, na iOS je to pevná tabulka (AVPlayer se zeptat nedá) a desktop
 * bere jen h264 (FFmpeg v softwaru).
 */
expect fun videoDecoder(codec: String, width: Int, height: Int): VideoDecoder

/** Výsledek [videoDecoder]; pořadí je pořadí přednosti. */
enum class VideoDecoder { None, Software, Hardware }
