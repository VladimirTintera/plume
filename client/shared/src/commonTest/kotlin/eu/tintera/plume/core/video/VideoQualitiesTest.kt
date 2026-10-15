package eu.tintera.plume.core.video

import eu.tintera.plume.core.telegram.VideoQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoQualitiesTest {

    private val original = VideoQuality(1, 1920, 1080, codec = null, sizeBytes = 300_000_000, isOriginal = true)
    private val p720 = VideoQuality(2, 1280, 720, codec = "h264", sizeBytes = 60_000_000, isOriginal = false)
    private val p720hevc = VideoQuality(3, 1280, 720, codec = "h265", sizeBytes = 40_000_000, isOriginal = false)
    private val p480 = VideoQuality(4, 854, 480, codec = "h264", sizeBytes = 30_000_000, isOriginal = false)
    private val p360av1 = VideoQuality(5, 640, 360, codec = "av1", sizeBytes = 10_000_000, isOriginal = false)

    private val hardware: (VideoQuality) -> VideoDecoder = { VideoDecoder.Hardware }

    private val all = videoQualities(original, listOf(p480, p720, p360av1, p720hevc), hardware)

    @Test
    fun nabidkaOdNejlepsiBezDvojic() {
        // Dvě 720p (h264 a h265) vypadají stejně — zůstane ta menší.
        assertEquals(listOf(original, p720hevc, p480, p360av1), all)
    }

    @Test
    fun hardwarovyDekoderPredMensimSouborem() {
        // AV1 je menší, ale jen softwarově: zůstane h264 s dekodérem v čipu.
        val p720av1 = VideoQuality(6, 1280, 720, codec = "av01", sizeBytes = 20_000_000, isOriginal = false)
        val qualities = videoQualities(original, listOf(p720av1, p720)) {
            if (it.codec == "av01") VideoDecoder.Software else VideoDecoder.Hardware
        }
        assertEquals(listOf(original, p720), qualities)
        // Kde h264 není, zůstává softwarové AV1 — lepší než nic.
        assertEquals(listOf(original, p720av1), videoQualities(original, listOf(p720av1)) { VideoDecoder.Software })
    }

    @Test
    fun nedekodovatelnaKvalitaVNabidceNeni() {
        val noAv1 = videoQualities(original, listOf(p480, p360av1)) {
            if (it.codec == "av1") VideoDecoder.None else VideoDecoder.Hardware
        }
        assertEquals(listOf(original, p480), noAv1)
    }

    @Test
    fun puvodniSouborSeNefiltruje() {
        assertEquals(listOf(original), videoQualities(original, listOf(p480)) { VideoDecoder.None })
    }

    @Test
    fun automatikaBezStropuNesnizujeRozliseni() {
        // Přepočítané jsou jen 720p a menší, původní 1080p — na Wi-Fi se rozlišení nesnižuje.
        assertEquals(original, pickVideoQuality(all, VideoQualityChoice.Auto, Int.MAX_VALUE, originalDownloaded = false))
    }

    @Test
    fun automatikaBezStropuBerePrepocitanouVeStejnemRozliseni() {
        val original720 = VideoQuality(1, 1280, 720, codec = null, sizeBytes = 655_000_000, isOriginal = true)
        val qualities = videoQualities(original720, listOf(p720, p480), hardware)
        assertEquals(p720, pickVideoQuality(qualities, VideoQualityChoice.Auto, Int.MAX_VALUE, originalDownloaded = false))
        // Stažený původní soubor pořád vyhrává — nic nestojí.
        assertEquals(original720, pickVideoQuality(qualities, VideoQualityChoice.Auto, Int.MAX_VALUE, originalDownloaded = true))
        // Kde přepočítaná streamovat nejde (iOS), hraje na Wi-Fi původní — ten se rozběhne hned.
        assertEquals(original720, pickVideoQuality(qualities, VideoQualityChoice.Auto, Int.MAX_VALUE, originalDownloaded = false, convertedStreams = false))
        // Strop podle sítě ale platí i tam: na datech je menší soubor důležitější než rozběhnutí.
        assertEquals(p480, pickVideoQuality(qualities, VideoQualityChoice.Auto, 480, originalDownloaded = false, convertedStreams = false))
        // Ruční volba původního platí dál.
        assertEquals(original720, pickVideoQuality(qualities, VideoQualityChoice.Original, Int.MAX_VALUE, originalDownloaded = false))
    }

    @Test
    fun automatikaBereNejlepsiDoStropu() {
        assertEquals(p720hevc, pickVideoQuality(all, VideoQualityChoice.Auto, 720, originalDownloaded = false))
        assertEquals(p480, pickVideoQuality(all, VideoQualityChoice.Auto, 480, originalDownloaded = false))
        assertEquals(p360av1, pickVideoQuality(all, VideoQualityChoice.Auto, 360, originalDownloaded = false))
    }

    @Test
    fun podStropemNicNejmensi() {
        assertEquals(p360av1, pickVideoQuality(all, VideoQualityChoice.Auto, 240, originalDownloaded = false))
    }

    @Test
    fun stazenyPuvodniVyhravaAutomatiku() {
        assertEquals(original, pickVideoQuality(all, VideoQualityChoice.Auto, 360, originalDownloaded = true))
    }

    @Test
    fun bezPrepocitanychJenPuvodni() {
        val only = listOf(original)
        assertEquals(original, pickVideoQuality(only, VideoQualityChoice.Auto, 360, originalDownloaded = false))
        assertEquals(original, pickVideoQuality(only, VideoQualityChoice.Lines(480), 360, originalDownloaded = false))
    }

    @Test
    fun priShodeRozmeruPrepocitanaPredPuvodni() {
        val small = VideoQuality(1, 1280, 720, codec = null, sizeBytes = 90_000_000, isOriginal = true)
        val qualities = videoQualities(small, listOf(p720, p480), hardware)
        assertEquals(p720, pickVideoQuality(qualities, VideoQualityChoice.Auto, 720, originalDownloaded = false))
    }

    @Test
    fun malePuvodniPodStropem() {
        // Původní 640×360 se do stropu 720p vejde a přepočítaných menších není — hraje původní.
        val small = VideoQuality(1, 640, 360, codec = null, sizeBytes = 5_000_000, isOriginal = true)
        val qualities = videoQualities(small, emptyList(), hardware)
        assertEquals(small, pickVideoQuality(qualities, VideoQualityChoice.Auto, 720, originalDownloaded = false))
    }

    @Test
    fun rucniVolbaBereTuNeboNejblizsiNizsi() {
        assertEquals(p480, pickVideoQuality(all, VideoQualityChoice.Lines(480), Int.MAX_VALUE, originalDownloaded = true))
        assertEquals(p480, pickVideoQuality(all, VideoQualityChoice.Lines(540), Int.MAX_VALUE, originalDownloaded = false))
        assertEquals(p360av1, pickVideoQuality(all, VideoQualityChoice.Lines(240), Int.MAX_VALUE, originalDownloaded = false))
        assertEquals(original, pickVideoQuality(all, VideoQualityChoice.Original, 360, originalDownloaded = false))
    }

    @Test
    fun videoNaVyskuSeMeriKratsiStranou() {
        val portrait = VideoQuality(6, 720, 1280, codec = "h264", sizeBytes = 1, isOriginal = false)
        assertEquals(720, portrait.lines)
    }
}
