package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * Podrobný výpis přenosu k JEDNOMU hovoru: soubor `call-journal/detail/<čas>-call<id>.log`,
 * který export deníku ([CallJournal.export]) přibalí. Řádky dodává platforma — Android z vlastního
 * logcatu (`CallLogCapture`), desktop z log callbacku ntgcalls (`DesktopCallMedia`).
 *
 * - **Opakování prořezává [RepeatFilter]** (dvouapůlhodinový hovor: 35 MB → 1,1 MB).
 * - **Strop [MAX_BYTES]** na hovor; nad ním jedna poznámka a dál nic.
 * - **Posledních [KEEP_FILES] hovorů**; starší se smažou při založení nového.
 * - **Adresy a hesla maskované** ([maskAddresses]): WebRTC na Androidu adresy maskuje samo
 *   (`10.61.43.x`), log callback ntgcalls na desktopu dává celé — a soubor jde ven. ICE heslo
 *   hovoru (`pwd: …`, i uvnitř `Cand[…]`) píše WebRTC všude celé.
 *
 * **Není vláknově bezpečný**: píše jedno vlákno (Android: čtení logcatu), nebo volající zamyká
 * (desktop: callback chodí z vláken knihovny).
 */
class CallDetailLog private constructor(private val sink: BufferedSink) {

    private val logger = Logger.withTag("CallJournal")
    private val filter = RepeatFilter()
    private val started = TimeSource.Monotonic.markNow()
    private var written = 0L
    private var capped = false
    private var closed = false

    /** [line] je celý řádek k zápisu, [tag] a [message] slouží jen k rozpoznání opakování. */
    fun write(tag: String, message: String, line: String) {
        if (closed) return
        val kept = filter.accept(tag, message, line, started.elapsedNow().inWholeMilliseconds)?.let(::maskAddresses) ?: return
        runCatching {
            if (written + kept.length > MAX_BYTES) {
                if (!capped) {
                    sink.writeUtf8("… log reached the $MAX_BYTES B cap, nothing more is written\n").flush()
                    capped = true
                }
                return
            }
            // Po řádcích: proces může skončit kdykoli (zabití uprostřed hovoru) a co je zapsané, zůstane.
            sink.writeUtf8(kept).writeUtf8("\n").flush()
            written += kept.length + 1
        }.onFailure {
            logger.w(it) { "Do výpisu hovoru nejde zapsat." }
            close()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { sink.close() }
    }

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
        const val KEEP_FILES = 3

        /**
         * Založí výpis hovoru [callId] v adresáři deníku [journalDirectory]; `null`, když to nejde
         * (výpis je doplněk, hovor kvůli němu nesmí nic ztratit).
         */
        fun open(journalDirectory: String, callId: Long, fileSystem: FileSystem = FileSystem.SYSTEM): CallDetailLog? =
            runCatching {
                val dir = journalDirectory.toPath() / CALL_DETAIL_DIRECTORY
                fileSystem.createDirectories(dir)
                // Místo i pro ten, který právě začíná.
                fileSystem.list(dir).filter { it.name.endsWith(".log") }.sortedByDescending { it.name }
                    .drop(KEEP_FILES - 1).forEach { fileSystem.delete(it) }
                CallDetailLog(fileSystem.sink(dir / "${fileStamp()}-call$callId.log").buffer())
            }.onFailure { Logger.withTag("CallJournal").w(it) { "Výpis hovoru nejde založit." } }.getOrNull()

        private val secret = Regex("""(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{20,}(?![A-Za-z0-9+/])""")
        private val ipv4 = Regex("""\b(\d{1,3}\.\d{1,3}\.\d{1,3})\.\d{1,3}\b""")
        private val ipv6 = Regex(
            """\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b|\b(?:[0-9a-fA-F]{1,4}:){1,6}:(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4}){0,5})?(?![0-9a-zA-Z:])""",
        )

        /**
         * Poslední část IPv4 a všechno za třetí skupinou IPv6 pryč, jako to dělá WebRTC na
         * Androidu: síť se z výpisu pozná (mobilní, domácí, server Telegramu), konkrétní adresa ne.
         */
        internal fun maskAddresses(line: String): String {
            val v6 = ipv6.replace(line) { match ->
                val groups = match.value.split(':').filter { it.isNotEmpty() }
                if (groups.size < 3) match.value else groups.take(3).joinToString(":") + ":x:x:x:x:x"
            }
            val v4 = ipv4.replace(v6) { "${it.groupValues[1]}.x" }
            // ICE heslo (24 znaků base64) a jiné dlouhé náhodné řetězce: písmena I číslice, ať
            // zůstanou jména tříd a funkcí (`WebRtcVoiceMediaChannel`).
            return secret.replace(v4) { if (it.value.any(Char::isDigit) && it.value.any(Char::isLetter)) "***" else it.value }
        }

        /** `20261019-213700` — řadí se podle jména od nejstaršího. */
        private fun fileStamp(): String {
            val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            fun Int.pad() = toString().padStart(2, '0')
            return "${time.year}${time.month.number.pad()}${time.day.pad()}-${time.hour.pad()}${time.minute.pad()}${time.second.pad()}"
        }
    }
}
