package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Deník hovorů pro diagnostiku (s podrobnými výpisy přenosu v [CALL_DETAIL_DIRECTORY], kde je
 * platforma umí): časová osa událostí (push, stavy hovoru, spojení, síť, výstup
 * zvuku, popředí) a po vteřinách přenesená data ([CallStatsSampler]). Žádný obsah zpráv, jména
 * ani id účtů — jen to, z čeho jde poznat, KDE se hovor zasekl. Celý ven jde jen tak, že ho
 * uživatel sám pošle ([export], Nastavení → Pomoc). Řádky jednoho hovoru a jeho výpis dostane
 * Telegram, když o ladicí data k tomu hovoru sám požádá ([lastCallLines], [detailLog]).
 *
 * Proč vůbec: logcat se ze skutečného telefonu dostane jen přes adb, a když hovor sekal nebo
 * propadl u někoho, kdo vývojář není, nezbylo nic. Viz `docs/poznatky/hovory.md`, „Deník hovorů".
 *
 * Zápis jde přes frontu do jedné korutiny — volá se z vláken knihovny, Firebase i hlavního —
 * a soubor se při [MAX_FILE_BYTES] přesune na `.1` (dva soubory, zhruba deset hovorů).
 */
class CallJournal(
    directory: String,
    scope: CoroutineScope,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    private val logger = Logger.withTag("CallJournal")
    private val dir = directory.toPath()
    private val current = dir / FILE_NAME
    private val previous = dir / PREVIOUS_FILE_NAME

    private val queue = Channel<Op>(Channel.UNLIMITED)

    private val _hasEntries = MutableStateFlow(false)

    /** Je co poslat — podle toho Nastavení položku nabídne, nebo řekne, že tu zatím nic není. */
    val hasEntries: StateFlow<Boolean> = _hasEntries.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            runCatching {
                fileSystem.createDirectories(dir)
                _hasEntries.value = size(current) > 0 || size(previous) > 0
            }.onFailure { logger.w(it) { "Adresář deníku nejde připravit." } }
            for (op in queue) {
                when (op) {
                    is Op.Line -> runCatching { append(op.text) }.onFailure { logger.w(it) { "Do deníku nejde zapsat." } }
                    is Op.Barrier -> op.done.complete(Unit)
                }
            }
        }
    }

    /** Zapíše řádek s časem; [callId] = ke kterému hovoru patří (`null` před ním, např. push). */
    fun record(text: String, callId: Int? = null) {
        val stamp = stamp(now().toLocalDateTime(timeZone()))
        val prefix = if (callId != null) "[$callId] " else ""
        queue.trySend(Op.Line("$stamp $prefix$text"))
    }

    /**
     * Složí soubor k odeslání: [header] (verze aplikace, telefon, systém) a oba díly deníku
     * od nejstaršího. Vrací cestu, `null`, když to nešlo.
     */
    suspend fun export(header: String): String? {
        val barrier = CompletableDeferred<Unit>()
        queue.send(Op.Barrier(barrier))
        barrier.await()
        return runCatching {
            val target = dir / EXPORT_FILE_NAME
            fileSystem.write(target) {
                writeUtf8(header.trimEnd())
                writeUtf8("\n\n")
                for (part in listOf(previous, current)) {
                    if (fileSystem.exists(part)) fileSystem.read(part) { writeAll(this) }
                }
                // Podrobné výpisy přenosu k posledním hovorům (Android, `CallLogCapture`).
                val details = dir / CALL_DETAIL_DIRECTORY
                fileSystem.listOrNull(details).orEmpty().filter { it.name.endsWith(".log") }.sortedBy { it.name }.forEach { log ->
                    writeUtf8("\n===== ${log.name} =====\n")
                    fileSystem.read(log) { writeAll(this) }
                }
            }
            target.toString()
        }.onFailure { logger.w(it) { "Deník nejde složit k odeslání." } }.getOrNull()
    }

    /**
     * Řádky deníku k právě skončenému hovoru [callId] — pro ladicí data, o která požádá server
     * (`sendCallDebugInformation`). TDLib čísluje hovory v každém spuštění od jedničky, takže
     * v deníku může být víc hovorů s týmž číslem; bere se jen poslední úsek (od předchozího
     * „Over" po poslední).
     */
    suspend fun lastCallLines(callId: Int): List<String> {
        val barrier = CompletableDeferred<Unit>()
        queue.send(Op.Barrier(barrier))
        barrier.await()
        val mine = runCatching {
            listOf(previous, current).filter { fileSystem.exists(it) }.flatMap { part ->
                fileSystem.read(part) { readUtf8() }.lines()
            }.filter { " [$callId] " in it }
        }.onFailure { logger.w(it) { "Deník nejde přečíst." } }.getOrDefault(emptyList())
        return lastCall(mine)
    }

    /**
     * Podrobný výpis přenosu hovoru [callId] (`CallDetailLog`) pro `sendCallLog`; `null`, když
     * není. Jen čerstvý — starší soubor s týmž číslem je z jiného spuštění, ne z tohohle hovoru.
     */
    fun detailLog(callId: Int): String? = runCatching {
        val oldest = now().toEpochMilliseconds() - DETAIL_LOG_FRESH_MS
        fileSystem.listOrNull(dir / CALL_DETAIL_DIRECTORY).orEmpty()
            .filter { it.name.endsWith("-call$callId.log") }
            .filter { (fileSystem.metadataOrNull(it)?.lastModifiedAtMillis ?: 0) >= oldest }
            .maxByOrNull { it.name }
            ?.toString()
    }.getOrNull()

    /** Jméno souboru k odeslání: `plume-calls-20261019-2051.txt`. */
    fun exportFileName(): String {
        val time = now().toLocalDateTime(timeZone())
        fun Int.pad() = toString().padStart(2, '0')
        return "plume-calls-${time.year}${time.month.number.pad()}${time.day.pad()}-${time.hour.pad()}${time.minute.pad()}.txt"
    }

    private fun append(line: String) {
        if (size(current) >= MAX_FILE_BYTES) {
            fileSystem.atomicMove(current, previous)
        }
        fileSystem.appendingSink(current).buffer().use { it.writeUtf8(line).writeUtf8("\n") }
        _hasEntries.value = true
    }

    private fun size(path: Path): Long = fileSystem.metadataOrNull(path)?.size ?: 0

    private sealed interface Op {
        class Line(val text: String) : Op
        class Barrier(val done: CompletableDeferred<Unit>) : Op
    }

    companion object {
        /** Za jak dlouho po posledním zápisu se výpis přenosu ještě bere jako ten z právě skončeného hovoru. */
        private const val DETAIL_LOG_FRESH_MS = 10 * 60 * 1000L

        /** Poslední úsek řádků jednoho čísla hovoru (viz [lastCallLines]). */
        internal fun lastCall(lines: List<String>): List<String> {
            val overs = lines.indices.filter { "Over:" in lines[it] }
            val end = overs.lastOrNull() ?: lines.lastIndex
            val start = overs.lastOrNull { it < end }?.plus(1) ?: 0
            return if (lines.isEmpty()) emptyList() else lines.subList(start, end + 1)
        }

        /** Kolik má jeden díl, než se přesune na `.1`. Dva díly ~ deset běžných hovorů. */
        const val MAX_FILE_BYTES = 256L * 1024

        private const val FILE_NAME = "calls.log"
        private const val PREVIOUS_FILE_NAME = "calls.1.log"
        private const val EXPORT_FILE_NAME = "calls-export.txt"

        /** `2026-10-19 18:50:38.109` — místní čas, jak ho uvidí ten, kdo hovor zažil. */
        internal fun stamp(time: LocalDateTime): String {
            fun Int.pad(n: Int) = toString().padStart(n, '0')
            return "${time.year}-${time.month.number.pad(2)}-${time.day.pad(2)} " +
                "${time.hour.pad(2)}:${time.minute.pad(2)}:${time.second.pad(2)}.${(time.nanosecond / 1_000_000).pad(3)}"
        }
    }
}

/** Adresář deníku v datech aplikace; cestu k datům dodává platforma. */
const val CALL_JOURNAL_DIRECTORY = "call-journal"

/** Podadresář deníku s podrobnými výpisy přenosu, jeden soubor na hovor. */
const val CALL_DETAIL_DIRECTORY = "detail"
