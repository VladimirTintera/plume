package eu.tintera.plume.core.video

import eu.tintera.plume.core.audio.FfmpegAudioDecoder
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Přehrávač videa na desktopu se zvukem, ve skutečném čase nad skutečnými soubory: 2 s
 * `testsrc` 160 × 120, 25 snímků za vteřinu, h264, se sinusem v AAC (`sound.mp4`) a bez zvuku
 * (`silent.mp4`), vyrobené `ffmpeg -f lavfi`.
 *
 * Testy se zvukem potřebují zvukový výstup; na stroji bez něj přehrávač hraje jen obraz
 * a ty testy se přeskočí (vypíšou proč).
 */
class DesktopVideoSoundTest {

    @Test
    fun soundVideoIsClockedBySound() = play("sound.mp4") { player ->
        if (!soundOutput()) return@play
        assertTrue(player.playsSound, "video se zvukem hraje bez zvuku")
        player.play()
        assertAdvancesBySecond(player, "pozice po vteřině")
        awaitEnd(player, 2500)
    }

    @Test
    fun silentVideoPlaysByWallClock() = play("silent.mp4") { player ->
        assertEquals(false, player.playsSound, "video bez zvukové stopy")
        player.play()
        assertAdvancesBySecond(player, "pozice po vteřině")
        awaitEnd(player, 2500)
    }

    @Test
    fun mutedSoundKeepsTheClock() = play("sound.mp4") { player ->
        if (!soundOutput()) return@play
        player.setMuted(true)
        player.play()
        assertAdvancesBySecond(player, "pozice ztlumeného videa po vteřině")
        awaitEnd(player, 2500)
    }

    @Test
    fun seekAndSpeedFollowTheSound() = play("sound.mp4") { player ->
        if (!soundOutput()) return@play
        player.seekTo(1000)
        player.setSpeed(2f)
        val started = System.nanoTime()
        player.play()
        awaitEnd(player, 2000)
        // Zbývá vteřina záznamu dvojnásobnou rychlostí: půl vteřiny a buffer linky.
        val elapsed = (System.nanoTime() - started) / 1_000_000
        assertNear(500, elapsed, 350, "doba dohrání od 1 s při 2×")
    }

    private fun play(name: String, block: suspend (VideoPlayer) -> Unit) = runBlocking {
        val file = File.createTempFile("video", ".$name")
        file.writeBytes(checkNotNull(javaClass.getResourceAsStream("/video/$name")).readBytes())
        val player = VideoPlayer()
        try {
            player.load(file.path, loop = false, muted = false)
            // Připravené video ukáže první snímek; tím je i zvuk otevřený.
            awaitUntil(5000, "první snímek") { player.image != null || player.isFailed }
            assertEquals(false, player.isFailed, "video nejde otevřít")
            block(player)
        } finally {
            player.release()
            file.delete()
        }
    }

    private fun soundOutput(): Boolean {
        val format = AudioFormat(FfmpegAudioDecoder.OUTPUT_RATE.toFloat(), 16, 2, true, false)
        val supported = AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, format))
        if (!supported) println("Přeskočeno: systém nemá zvukový výstup")
        return supported
    }

    /**
     * Za vteřinu od rozběhu se pozice posune o vteřinu. Měří se od prvního posunu, ne od
     * [VideoPlayer.play]: pod zátěží celé sady testů se zvuk rozbíhal o stovky ms později
     * (pozice 600 ms po vteřině) a test padal, i když hodiny šly správně.
     */
    private suspend fun assertAdvancesBySecond(player: VideoPlayer, what: String) {
        awaitUntil(3000, "rozběh přehrávání") { player.positionMs > 0 }
        val from = player.positionMs
        delay(1000)
        assertNear(1000, player.positionMs - from, 350, what)
    }

    private suspend fun awaitEnd(player: VideoPlayer, timeoutMs: Long) =
        awaitUntil(timeoutMs, "konec videa") { player.isEnded }

    private suspend fun awaitUntil(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Nedočkal jsem se: $what" }
            delay(10)
        }
    }

    private fun assertNear(expected: Long, actual: Long, tolerance: Long, what: String) =
        assertTrue(actual in expected - tolerance..expected + tolerance, "$what: $actual, čekáno $expected ± $tolerance")
}
