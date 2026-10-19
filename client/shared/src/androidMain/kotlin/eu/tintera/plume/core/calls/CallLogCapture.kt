package eu.tintera.plume.core.calls

import android.content.Context
import android.os.Process
import co.touchlab.kermit.Logger
import java.io.File

/**
 * Podrobný výpis přenosu k hovoru na Androidu: vlastní logcat procesu (`logcat --pid`) po dobu
 * přenosu do [CallDetailLog]. Aplikace smí číst svůj logcat bez oprávnění — `logd` jí dá jen
 * řádky vlastního uid (ověřeno na Androidu 14 a 17).
 *
 * **Jen tagy hovorů**: nativní WebRTC (`*.cc`, `*.cpp`) a naše tagy kolem hovoru. Zbytek procesu
 * (notifikace, seznam chatů) by do souboru, který uživatel posílá ven, mohl zanést názvy chatů
 * a jiná osobní data.
 *
 * Výpis vlastní vlákno, které logcat čte — zapisuje do něj a po konci proudu ho zavře; [stop]
 * jen ukončí logcat. Viz `docs/poznatky/hovory.md`, „Deník hovorů".
 */
internal class CallLogCapture(private val journalDirectory: String) {

    constructor(context: Context) : this(File(context.filesDir, CALL_JOURNAL_DIRECTORY).absolutePath)

    private val logger = Logger.withTag("CallLogCapture")

    @Volatile private var process: java.lang.Process? = null

    fun start(callId: Long) {
        stop()
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
        Thread({ copy(started, callId) }, "call-log-capture").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    private fun copy(source: java.lang.Process, callId: Long) {
        val log = CallDetailLog.open(journalDirectory, callId) ?: return source.destroy()
        try {
            source.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val match = THREADTIME.matchEntire(line) ?: continue
                    val tag = match.groupValues[1].trim()
                    if (relevant(tag)) log.write(tag, match.groupValues[2], line)
                }
            }
        } catch (failure: Exception) {
            if (process === source) logger.w(failure) { "Výpis hovoru skončil chybou." }
        } finally {
            log.close()
        }
    }

    private fun relevant(tag: String): Boolean =
        tag.endsWith(".cc") || tag.endsWith(".cpp") || tag.endsWith(".h") || tag in CALL_TAGS

    private companion object {
        /** Naše tagy kolem hovoru (`Logger.withTag`); jiné části aplikace do výpisu nejdou. */
        val CALL_TAGS = setOf(
            "Calls", "NtgCallMedia", "TelecomCalls", "TelecomAudio", "CallAudioRouter", "CallService",
            "CallServiceControls", "CallRinger", "CallPictureInPicture", "ScreenCapture", "Push", "Telegram",
        )

        /** `10-19 18:50:38.109  9448  9785 I connection.cc: (line 268): …` → tag, zpráva. */
        val THREADTIME = Regex("""^\S+ \S+\s+\d+\s+\d+ \w (.+?): (.*)$""")
    }
}
