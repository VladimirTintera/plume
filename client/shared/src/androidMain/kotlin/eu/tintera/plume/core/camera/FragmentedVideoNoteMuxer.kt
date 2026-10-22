package eu.tintera.plume.core.camera

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.camera.video.internal.muxer.Muxer
import androidx.camera.video.internal.muxer.MuxerException
import androidx.camera.video.internal.muxer.MuxerFactory
import androidx.media3.common.C
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.FragmentedMp4Muxer
import androidx.media3.muxer.MuxerUtil
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Muxer pro CameraX, který kolečko zapisuje jako FRAGMENTOVANÉ MP4 (`FragmentedMp4Muxer`
 * z Media3): napřed `ftyp` a `moov` bez vzorků, pak dvojice `moof` + `mdat` po vteřinách.
 *
 * **Soubor se jen připisuje, nic v něm se zpětně nepřepisuje** — proto jde kolečko nahrávat do
 * Telegramu už během nahrávání (`LiveUploads`). Výchozí muxer CameraX (`Media3MuxerImpl` nad
 * `Mp4Muxer`) si na začátku nechá místo na index (`free`) a velikost `mdat`, obojí dopíše až po
 * skončení — a začátek souboru už by byl na serveru: TDLib nahrává jen souvislý začátek
 * (`setFileGenerationProgress`) a nahraný díl znovu neposílá.
 *
 * `setMuxerFactory` a rozhraní `Muxer` jsou v CameraX `@RestrictTo(LIBRARY)`. Kdyby se při
 * bumpu CameraX rozhraní změnilo, ozve se překladač, ne až aplikace za běhu.
 */
@SuppressLint("RestrictedApi")
@OptIn(UnstableApi::class)
internal class FragmentedVideoNoteMuxer : Muxer {

    private var muxer: FragmentedMp4Muxer? = null
    private var started = false

    /** Stopy se zvukem — jejich vzorky jsou všechny synchronní (viz [writeSampleData]). */
    private val audioTracks = mutableSetOf<Int>()

    override fun setOutput(path: String, format: Int) {
        muxer = create(FileOutputStream(path))
    }

    override fun setOutput(parcelFileDescriptor: ParcelFileDescriptor, format: Int) {
        // Kolečko jde vždycky do souboru (`FileOutputOptions`); kdyby přece ne, kanál nad deskriptorem.
        muxer = create(FileOutputStream(parcelFileDescriptor.fileDescriptor))
    }

    private fun create(output: FileOutputStream): FragmentedMp4Muxer =
        FragmentedMp4Muxer.Builder(output.channel)
            .setFragmentDurationMs(FRAGMENT_DURATION_MS)
            .build()

    private fun current(): FragmentedMp4Muxer = muxer ?: error("Muxer nemá výstup.")

    override fun setOrientationDegrees(degrees: Int) {
        current().addMetadataEntry(Mp4OrientationData(degrees))
    }

    override fun setLocation(latitude: Double, longitude: Double) {
        current().addMetadataEntry(Mp4LocationData(latitude.toFloat(), longitude.toFloat()))
    }

    // Snímková frekvence záznamu je jen u zpomalených videí; kolečko ji nenese.
    override fun setCaptureFps(captureFps: Int) = Unit

    override fun addTrack(format: MediaFormat): Int =
        wrap { current().addTrack(MediaFormatUtil.createFormatFromMediaFormat(format)) }
            .also { track -> if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) audioTracks += track }

    override fun start() {
        started = true
    }

    /**
     * **Zvukové vzorky se označí jako synchronní.** Kodér zvuku je jako klíčové neoznačuje
     * a fragmentovaný muxer to přenese do `trun` — zvuk by byl samé nesynchronní vzorky. Běžné
     * MP4 to nevidí (bez `stss` je synchronní všechno), ExoPlayer ale vzorky před prvním
     * synchronním zahazuje: AMR z emulátoru nehrál vůbec (jen obraz) a Transformer ho při
     * spojování koleček ztratil. U AAC to novější ExoPlayer opravuje sám („Overriding unexpected
     * non-sync sample"), starší přehrávače ne. Rámec AAC i AMR se dekóduje samostatně.
     */
    override fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        check(started) { "Muxer neběží." }
        val info = MuxerUtil.getMuxerBufferInfoFromMediaCodecBufferInfo(bufferInfo).let {
            if (trackIndex in audioTracks) BufferInfo(it.presentationTimeUs, it.size, it.flags or C.BUFFER_FLAG_KEY_FRAME) else it
        }
        wrap { current().writeSampleData(trackIndex, byteBuffer, info) }
    }

    override fun stop() {
        if (!started) return
        started = false
        wrap { muxer?.close() }
        muxer = null
    }

    override fun release() {
        runCatching { muxer?.close() }
        muxer = null
        started = false
    }

    /** Fragmentované MP4 je čitelné i useknuté — každý fragment nese vlastní index. */
    override fun isInterruptionResilient(): Boolean = true

    private fun <T> wrap(block: () -> T): T =
        try {
            block()
        } catch (failure: Exception) {
            throw MuxerException("Fragmented MP4 muxer failed", failure)
        }

    companion object {
        /**
         * Nejkratší fragment; skutečně končí až na klíčovém snímku (emulátor ho dává po 2 s).
         * Co je ve fragmentu, se zapíše najednou — delší fragment by nechával víc na konec.
         */
        private const val FRAGMENT_DURATION_MS = 1_000L

        /** Pro `Recorder.Builder.setMuxerFactory`: MP4 jde přes nás, jiný formát (WebM) CameraX nepoužije. */
        @SuppressLint("RestrictedApi")
        val factory = MuxerFactory { FragmentedVideoNoteMuxer() }
    }
}
