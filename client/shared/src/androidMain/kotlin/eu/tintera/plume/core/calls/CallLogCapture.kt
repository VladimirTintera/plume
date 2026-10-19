package eu.tintera.plume.core.calls

import android.os.Process
import android.os.SystemClock
import co.touchlab.kermit.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Podrobný výpis přenosu k hovoru: vlastní logcat procesu (`logcat --pid`) po dobu přenosu, do
 * souboru vedle deníku hovorů ([CallJournal] ho přibalí k exportu). Aplikace smí číst svůj
 * logcat bez oprávnění — `logd` jí dá jen řádky vlastního uid.
 *
 * - **Jen tagy hovorů**: nativní WebRTC (`*.cc`, `*.cpp`) a naše tagy kolem hovoru. Zbytek
 *   procesu (notifikace, seznam chatů) by do souboru, který uživatel posílá ven, mohl zanést
 *   názvy chatů a jiná osobní data.
 * - **Opakování prořezává [RepeatFilter]**: bez něj má dvouapůlhodinový hovor 19 MB.
 * - **Strop [MAX_BYTES] na hovor a posledních [KEEP_FILES] hovorů**; nad stropem jen poznámka.
 *
 * Viz `docs/poznatky/hovory.md`, „Deník hovorů".
 */
internal class CallLogCapture(private val directory: File) {

    constructor(context: android.content.Context) : this(File(context.filesDir, "$CALL_JOURNAL_DIRECTORY/$CALL_DETAIL_DIRECTORY"))

    private val logger = Logger.withTag("CallLogCapture")

    @Volatile private var process: java.lang.Process? = null
    private var reader: Thread? = null

    fun start(callId: Long) {
        stop()
        val file = runCatching {
            directory.mkdirs()
            prune()
            File(directory, "${STAMP.format(Date())}-call$callId.log")
        }.getOrElse {
            logger.w(it) { "Výpis hovoru nejde založit." }
            return
        }
        val started = runCatching {
            // `-T 1`: od teď (poslední řádek), ne celý buffer — v něm je i to, co k hovoru nepatří.
            ProcessBuilder("logcat", "-v", "threadtime", "--pid=${Process.myPid()}", "-T", "1")
                .redirectErrorStream(true)
                .start()
        }.getOrElse {
            logger.w(it) { "Logcat nejde spustit." }
            return
        }
        process = started
        reader = Thread({ copy(started, file) }, "call-log-capture").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        process?.destroy()
        process = null
        reader = null
    }

    private fun copy(source: java.lang.Process, file: File) {
        val filter = RepeatFilter()
        var written = 0L
        var capped = false
        runCatching {
            file.bufferedWriter().use { out ->
                source.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val match = THREADTIME.matchEntire(line) ?: continue
                        val tag = match.groupValues[1].trim()
                        if (!relevant(tag)) continue
                        val kept = filter.accept(tag, match.groupValues[2], line, SystemClock.elapsedRealtime()) ?: continue
                        if (written + kept.length > MAX_BYTES) {
                            if (!capped) {
                                out.write("… log reached the $MAX_BYTES B cap, nothing more is written\n")
                                out.flush()
                                capped = true
                            }
                            continue
                        }
                        out.write(kept)
                        out.write("\n")
                        written += kept.length + 1
                        // Proces může skončit kdykoli (zabití uprostřed hovoru); co je zapsané, zůstane.
                        out.flush()
                    }
                }
            }
        }.onFailure { if (process === source) logger.w(it) { "Výpis hovoru skončil chybou." } }
    }

    /** Nejnovější hovory zůstanou, starší pryč — včetně místa pro ten, který právě začíná. */
    private fun prune() {
        directory.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_FILES - 1)
            ?.forEach { it.delete() }
    }

    private fun relevant(tag: String): Boolean =
        tag.endsWith(".cc") || tag.endsWith(".cpp") || tag.endsWith(".h") || tag in CALL_TAGS

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
        const val KEEP_FILES = 3

        /** Naše tagy kolem hovoru (`Logger.withTag`); jiné části aplikace do výpisu nejdou. */
        private val CALL_TAGS = setOf(
            "Calls", "NtgCallMedia", "TelecomCalls", "TelecomAudio", "CallAudioRouter", "CallService",
            "CallServiceControls", "CallRinger", "CallPictureInPicture", "ScreenCapture", "Push", "Telegram",
        )

        /** `10-19 18:50:38.109  9448  9785 I connection.cc: (line 268): …` → tag, zpráva. */
        private val THREADTIME = Regex("""^\S+ \S+\s+\d+\s+\d+ \w (.+?): (.*)$""")

        private val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
    }
}
