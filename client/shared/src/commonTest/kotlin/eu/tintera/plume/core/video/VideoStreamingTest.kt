package eu.tintera.plume.core.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoStreamingTest {

    private fun box(type: String, payload: Int): ByteArray {
        val size = 8 + payload
        return byteArrayOf(
            (size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte(),
        ) + type.encodeToByteArray() + ByteArray(payload)
    }

    /** Box s 64bitovou velikostí (`size` = 1), jak ho má `mdat` přes 4 GB. */
    private fun largeBox(type: String, payload: Int): ByteArray {
        val size = 16L + payload
        return byteArrayOf(0, 0, 0, 1) + type.encodeToByteArray() +
            ByteArray(8) { i -> (size ushr (56 - 8 * i)).toByte() } + ByteArray(payload)
    }

    private fun indexFirst(file: ByteArray): Boolean {
        val reads = mutableListOf<Long>()
        val result = mp4IndexBeforeData(file.size.toLong()) { offset, count ->
            reads += offset
            file.copyOfRange(offset.toInt(), minOf(file.size, offset.toInt() + count))
        }
        // Čtou se jen hlavičky, ne celý soubor.
        assertTrue(reads.size <= 8, "čtení: $reads")
        return result
    }

    @Test
    fun indexNaZacatku() {
        assertTrue(indexFirst(box("ftyp", 16) + box("moov", 200) + box("mdat", 5000)))
    }

    @Test
    fun indexNaKonci() {
        // Tak ukládá video foťák: data napřed, index až po dokončení záznamu.
        assertFalse(indexFirst(box("ftyp", 16) + box("free", 8) + box("mdat", 5000) + box("moov", 200)))
    }

    @Test
    fun velkyBoxSeSPreskoci() {
        assertTrue(indexFirst(box("ftyp", 16) + largeBox("free", 3000) + box("moov", 20) + box("mdat", 40)))
        assertFalse(indexFirst(box("ftyp", 16) + largeBox("mdat", 3000) + box("moov", 20)))
    }

    @Test
    fun fragmentovaneMp4() {
        assertTrue(indexFirst(box("ftyp", 16) + box("moov", 100) + box("moof", 30) + box("mdat", 300)))
    }

    @Test
    fun rozbityNeboCiziSoubor() {
        assertFalse(indexFirst(ByteArray(0)))
        assertFalse(indexFirst(ByteArray(5)))
        // Velikost menší než hlavička by cyklus zastavila na místě.
        assertFalse(indexFirst(byteArrayOf(0, 0, 0, 4) + "ftyp".encodeToByteArray() + box("moov", 10)))
        // Box „do konce souboru" (velikost 0): dál už nic není.
        assertFalse(indexFirst(byteArrayOf(0, 0, 0, 0) + "free".encodeToByteArray() + box("moov", 10)))
        // Useknutý soubor bez moov i mdat.
        assertFalse(indexFirst(box("ftyp", 16) + box("free", 100).copyOf(50)))
        // WebM / Matroska: první „box" je nesmysl.
        assertFalse(indexFirst(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) + ByteArray(40)))
    }

    @Test
    fun streamujeMp4IBezPriznaku() {
        assertTrue(streamOriginalVideo(supportsStreaming = false, mimeType = "video/mp4"))
        assertTrue(streamOriginalVideo(supportsStreaming = false, mimeType = "video/quicktime"))
        assertTrue(streamOriginalVideo(supportsStreaming = false, mimeType = "Video/MP4; codecs=avc1"))
        assertFalse(streamOriginalVideo(supportsStreaming = false, mimeType = "video/webm"))
        assertFalse(streamOriginalVideo(supportsStreaming = false, mimeType = ""))
        // Příznak od odesílatele platí dál, ať je kontejner jakýkoli.
        assertTrue(streamOriginalVideo(supportsStreaming = true, mimeType = "video/x-matroska"))
    }
}
