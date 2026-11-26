package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Soubory, které účet právě přenáší: nahrává na server (přílohy rozesílaných zpráv, fotka profilu,
 * samolepka, příběh) a stahuje ze seznamu Stahování. Podle toho aplikace drží proces naživu, dokud
 * přenos neskončí — na Androidu službou v popředí, na iOS úlohou na pozadí
 * (`docs/poznatky/konverzace-psani.md`, „Nahrávání a stahování na pozadí").
 */
interface TransferMonitor {

    /** Souhrn nahrávání; [TransferActivity.Idle], když se nic nenahrává. */
    val uploads: StateFlow<TransferActivity>

    /**
     * Souhrn stahování souborů ze seznamu Stahování (`addFileToDownloads`). Náhledy, fotky
     * v konverzaci ani streamované video se nepočítají — proces by držel každý přehrávaný klip.
     */
    val downloads: StateFlow<TransferActivity>
}

/** Souhrn přenášených souborů jednoho účtu, nebo součet přes účty ([plus]). */
data class TransferActivity(
    val files: Int,
    val transferredBytes: Long,
    /** Součet velikostí, které TDLib zná; soubor bez známé velikosti nepřispívá. */
    val totalBytes: Long,
    /**
     * Kolik přenosů kdy začalo — roste s každým souborem, který se dal do přenosu. Podle změny
     * se pozná nový přenos i tehdy, když počet [files] zůstal stejný (jeden soubor dojel,
     * další začal) nebo když v něm visí soubor, o jehož konci TDLib neřekl.
     */
    val started: Long,
) {
    val isIdle: Boolean get() = files == 0

    /** Podíl přeneseného, `null`, dokud velikost není známá. */
    val fraction: Float?
        get() = if (totalBytes > 0) (transferredBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null

    operator fun plus(other: TransferActivity): TransferActivity = TransferActivity(
        files = files + other.files,
        transferredBytes = transferredBytes + other.transferredBytes,
        totalBytes = totalBytes + other.totalBytes,
        started = started + other.started,
    )

    companion object {
        val Idle = TransferActivity(files = 0, transferredBytes = 0L, totalBytes = 0L, started = 0L)
    }
}

/**
 * Sčítá průběh přenosu po souborech z `updateFile`. Čistá logika bez TDLib, aby šla testovat;
 * vlákna řeší volající ([TdlTransferMonitor]).
 *
 * S [onlyListed] se počítají jen soubory ohlášené přes [listed] (seznam Stahování). Členství
 * a průběh se vedou zvlášť a soubor se počítá, až když platí obojí: update souboru a update
 * seznamu chodí různými odběry a pořadí mezi nimi neplatí.
 */
class TransferTracker(private val onlyListed: Boolean = false) {

    private class Progress(val transferred: Long, val total: Long)

    /** Všechny soubory, které TDLib právě přenáší (u stahování i náhledy a streamované video). */
    private val active = mutableMapOf<Int, Progress>()
    private val inList = mutableSetOf<Int>()
    /** Soubory v posledním souhrnu; nový mezi nimi zvedne [started]. */
    private var counted = emptySet<Int>()
    private var started = 0L

    /**
     * Zapíše stav souboru z updatu a vrátí nový souhrn. Soubor, který se přestal přenášet
     * (hotovo, zrušeno, pozastaveno, smazaná zpráva), ze souhrnu zmizí.
     */
    fun update(fileId: Int, transferring: Boolean, transferredBytes: Long, totalBytes: Long): TransferActivity {
        if (transferring) {
            active[fileId] = Progress(transferredBytes.coerceAtLeast(0L), totalBytes.coerceAtLeast(0L))
        } else {
            active.remove(fileId)
        }
        return summary()
    }

    /** Soubor přibyl do seznamu Stahování, nebo z něj (či z rozestahovaných) odešel. */
    fun listed(fileId: Int, listed: Boolean): TransferActivity {
        if (listed) inList += fileId else inList -= fileId
        return summary()
    }

    /** Zapomene všechno (výměna klienta: `fileId` platí jen v jedné databázi). */
    fun clear(): TransferActivity {
        active.clear()
        inList.clear()
        return summary()
    }

    private fun summary(): TransferActivity {
        val files = if (onlyListed) active.filterKeys { it in inList } else active
        started += files.keys.count { it !in counted }
        counted = files.keys.toSet()
        if (files.isEmpty()) return TransferActivity.Idle.copy(started = started)
        var transferred = 0L
        var total = 0L
        for (progress in files.values) {
            if (progress.total <= 0) continue
            transferred += progress.transferred.coerceAtMost(progress.total)
            total += progress.total
        }
        return TransferActivity(files = files.size, transferredBytes = transferred, totalBytes = total, started = started)
    }
}
