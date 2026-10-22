package eu.tintera.plume.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.audio.MediaCapture
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.telegram.OutgoingVideoNote

/**
 * Kamera pro video kolečka — platformní část. Android CameraX, iOS AVFoundation.
 *
 * `core/camera` je hranice jako `core/audio`: `androidx.camera.*` a snímání z kamery
 * v `platform.AVFoundation` smí být jen tady (čtečka QR kódů na iOS má vlastní relaci).
 *
 * - **Kamera se otevírá až se [start]**, ne s náhledem. Otevřená kamera svítí v systémové liště
 *   jako indikátor soukromí; kdyby se otevírala s konverzací, svítil by pořád.
 * - **Výsledek [stop] je čtvercové MP4** (Telegram kolečka neořezává, kreslí je do kruhu, a
 *   obdélník by každý klient ořízl jinak), nejvýš [VIDEO_NOTE_MAX_LENGTH] px na stranu.
 * - **Žije s kompozicí**, ne s ViewModelem — potřebuje vlastníka životního cyklu a plochu pro
 *   náhled. Otočení displeje proto nahrávání ukončí; ViewModel se o tom dozví přes [CaptureSlot].
 */
interface VideoNoteCamera : MediaCapture {

    /** Náhled z kamery. Kreslí se jen během nahrávání; do kruhu ho ořízne volající. */
    @Composable
    fun Preview(modifier: Modifier)

    /** Zařízení má přední i zadní kameru — jde přepínat, i během nahrávání. */
    val canSwitch: Boolean

    fun switchCamera()

    /** Kolečka jsou MP4 ([stop]); spojují se kodérem platformy, ne po paketech jako Ogg. */
    override fun canJoin(path: String): Boolean = path.endsWith(".mp4")

    /**
     * Připojí kolečko [next] za [previous] do NOVÉHO souboru vedle [next] (nahrávání po
     * poslechu náhledu). Obě kolečka už jsou čtvercová a nejvýš [VIDEO_NOTE_MAX_LENGTH] px,
     * výsledek taky. Vstupní soubory se smažou až po úspěchu — selhání nechá [previous] celé.
     */
    override suspend fun join(previous: String, next: String): String
}

/** Kamera pro kolečka; `null`, když zařízení kameru nemá (simulátor iOS, některé tablety). */
@Composable
expect fun rememberVideoNoteCamera(): VideoNoteCamera?

/** Oprávnění ke kameře I mikrofonu — kolečko bez zvuku není kolečko. */
@Composable
expect fun rememberVideoNotePermission(): RecordPermission

/** Z nahraného souboru přečte délku, stranu a první snímek jako náhled. */
expect suspend fun prepareVideoNote(path: String): OutgoingVideoNote

/** Nejdelší kolečko, které Telegram přijme; nahrávání pak skončí a kolečko odejde samo. */
const val VIDEO_NOTE_MAX_DURATION_MS = 60_000L

/**
 * Strana kolečka. Oficiální klienti posílají 384–480 px; víc v 240dp kruhu nikdo nevidí.
 * TDLib bere stranu jen pod 640 px, víc skončí „Wrong video note length".
 */
const val VIDEO_NOTE_MAX_LENGTH = 480

/**
 * Místo pro kameru ve ViewModelu. Kamera žije s kompozicí (viz [VideoNoteCamera]), nahrávání
 * s ViewModelem; obrazovka ji sem připojí, dokud je složená, a odpojením rozpracované nahrávání
 * zahodí — jinak by ViewModel držel kameru, kterou kompozice už uvolnila.
 */
class CaptureSlot : MediaCapture {

    var camera: VideoNoteCamera? = null
        private set

    private var onDetached: (() -> Unit)? = null

    fun attach(camera: VideoNoteCamera, onDetached: () -> Unit) {
        this.camera = camera
        this.onDetached = onDetached
    }

    fun detach(camera: VideoNoteCamera) {
        if (this.camera !== camera) return
        onDetached?.invoke()
        this.camera = null
        onDetached = null
    }

    private fun current(): VideoNoteCamera = camera ?: error("Kamera není připojená.")

    override fun start() = current().start()

    override val level: Float get() = camera?.level ?: 0f

    override val livePath: String? get() = camera?.livePath

    override suspend fun stop(): String = current().stop()

    override fun cancel() {
        camera?.cancel()
    }

    override fun pause() {
        camera?.pause()
    }

    override fun resume() {
        camera?.resume()
    }

    override fun canJoin(path: String): Boolean = camera?.canJoin(path) ?: false

    override suspend fun join(previous: String, next: String): String = current().join(previous, next)
}
