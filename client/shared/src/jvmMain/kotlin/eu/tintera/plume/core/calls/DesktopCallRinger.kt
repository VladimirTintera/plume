package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopTaskbar
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Zvonění hovoru na desktopu, protějšek `CallRinger` z Androidu.
 *
 * - **Příchozí hovor zvoní ve smyčce** ([CallTones.ringtone]), dokud se nepřijme, neodmítne
 *   nebo nezruší — i s aktivním oknem, kde oznámení nepřijde. Oznámení hovoru je proto tiché.
 *   Tlačítko okna k tomu bliká (v Docku poskakuje ikona), když okno není vpředu.
 * - **Odchozí hovor má tón protistrany** ([CallTones.ringback]) od chvíle, kdy hovor protějšku
 *   došel ([ActiveCall.received]), dokud nezvedne.
 * - **Přehrává Java Sound do výchozího výstupu systému**, nezávisle na ntgcalls: ta se rozjede
 *   až se spojováním, kdy už tón dávno utichl.
 * - Tiché hodiny aplikace zvonění neberou (stejně jako na Androidu); kdo nechce být rušen,
 *   má na to režim soustředění systému — ten ale Java nepřečte.
 */
class DesktopCallRinger(
    private val calls: CallRepository,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("CallRinger")
    private val ringtone by lazy { CallTones.ringtone().toBytes() }
    private val ringback by lazy { CallTones.ringback().toBytes() }
    private var player: LoopPlayer? = null

    fun start() {
        scope.launch {
            calls.active
                // Tón protistrany až ve chvíli, kdy hovor protějšku opravdu zvoní (`received`).
                .map { call -> call?.phase?.takeIf { it == CallPhase.Ringing || it == CallPhase.Calling && call.received } }
                .distinctUntilChanged()
                .collect { phase ->
                    player?.stop()
                    player = when (phase) {
                        CallPhase.Ringing -> {
                            DesktopTaskbar.requestAttention()
                            LoopPlayer(ringtone, "ringtone", logger)
                        }
                        CallPhase.Calling -> LoopPlayer(ringback, "ringback", logger)
                        else -> null
                    }
                }
        }
    }

    private fun ShortArray.toBytes(): ByteArray =
        ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN).also { it.asShortBuffer().put(this) }.array()
}

/**
 * Smyčka jednoho tónu na vlastním vlákně. Píše se po kouscích ([CHUNK_BYTES]), aby [stop]
 * utišil tón do desetiny vteřiny, ne až po celé periodě.
 */
private class LoopPlayer(private val pcm: ByteArray, name: String, private val logger: Logger) {

    @Volatile
    private var running = true

    init {
        Thread(::play, "call-$name").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
    }

    private fun play() {
        val line = runCatching {
            AudioSystem.getSourceDataLine(FORMAT).apply {
                open(FORMAT, CHUNK_BYTES * 2)
                start()
            }
        }.onFailure { logger.w(it) { "Tón hovoru nejde přehrát." } }.getOrNull() ?: return
        try {
            while (running) {
                var offset = 0
                while (running && offset < pcm.size) {
                    val length = minOf(CHUNK_BYTES, pcm.size - offset)
                    offset += line.write(pcm, offset, length)
                }
            }
        } finally {
            // Co zbylo ve vyrovnávací paměti, se zahodí — tón má přestat hned.
            line.stop()
            line.flush()
            line.close()
        }
    }

    private companion object {
        val FORMAT = AudioFormat(CallTones.SAMPLE_RATE.toFloat(), 16, 1, true, false)

        /** Desetina vteřiny zvuku (16 bitů mono). */
        const val CHUNK_BYTES = CallTones.SAMPLE_RATE / 10 * 2
    }
}
