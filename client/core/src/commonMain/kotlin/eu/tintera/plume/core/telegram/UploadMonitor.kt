package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Soubory, které účet právě nahrává na server (přílohy rozesílaných zpráv, fotka profilu,
 * samolepka, příběh). Podle toho aplikace drží proces naživu, dokud nahrávání neskončí —
 * na Androidu službou v popředí, na iOS úlohou na pozadí (`docs/poznatky/konverzace-psani.md`,
 * „Nahrávání na pozadí").
 */
interface UploadMonitor {

    /** Souhrn nahrávání; [UploadActivity.Idle], když se nic nenahrává. */
    val activity: StateFlow<UploadActivity>
}

/** Souhrn nahrávaných souborů jednoho účtu, nebo součet přes účty ([plus]). */
data class UploadActivity(
    val files: Int,
    val uploadedBytes: Long,
    /** Součet velikostí, které TDLib zná; soubor bez známé velikosti nepřispívá. */
    val totalBytes: Long,
    /**
     * Kolik nahrávání kdy začalo — roste s každým souborem, který se dal do nahrávání. Podle změny
     * se pozná nové nahrávání i tehdy, když počet [files] zůstal stejný (jeden soubor dojel,
     * další začal) nebo když v něm visí soubor, o jehož konci TDLib neřekl.
     */
    val started: Long,
) {
    val isIdle: Boolean get() = files == 0

    /** Podíl nahraného, `null`, dokud velikost není známá. */
    val fraction: Float?
        get() = if (totalBytes > 0) (uploadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null

    operator fun plus(other: UploadActivity): UploadActivity = UploadActivity(
        files = files + other.files,
        uploadedBytes = uploadedBytes + other.uploadedBytes,
        totalBytes = totalBytes + other.totalBytes,
        started = started + other.started,
    )

    companion object {
        val Idle = UploadActivity(files = 0, uploadedBytes = 0L, totalBytes = 0L, started = 0L)
    }
}

/**
 * Sčítá průběh nahrávání po souborech z `updateFile`. Čistá logika bez TDLib, aby šla testovat;
 * vlákna řeší volající ([TdlUploadMonitor]).
 */
class UploadTracker {

    private class Progress(val uploaded: Long, val total: Long)

    private val uploading = mutableMapOf<Int, Progress>()
    private var started = 0L

    /**
     * Zapíše stav souboru z updatu a vrátí nový souhrn. Soubor, který se přestal nahrávat
     * (hotovo, zrušeno, smazaná zpráva), ze souhrnu zmizí.
     */
    fun update(fileId: Int, uploading: Boolean, uploadedBytes: Long, totalBytes: Long): UploadActivity {
        if (uploading) {
            if (fileId !in this.uploading) started++
            this.uploading[fileId] = Progress(uploadedBytes.coerceAtLeast(0L), totalBytes.coerceAtLeast(0L))
        } else {
            this.uploading.remove(fileId)
        }
        return summary()
    }

    /** Zapomene všechno (výměna klienta: `fileId` platí jen v jedné databázi). */
    fun clear(): UploadActivity {
        uploading.clear()
        return summary()
    }

    private fun summary(): UploadActivity {
        if (uploading.isEmpty()) return UploadActivity.Idle.copy(started = started)
        var uploaded = 0L
        var total = 0L
        for (progress in uploading.values) {
            if (progress.total <= 0) continue
            uploaded += progress.uploaded.coerceAtMost(progress.total)
            total += progress.total
        }
        return UploadActivity(files = uploading.size, uploadedBytes = uploaded, totalBytes = total, started = started)
    }
}
