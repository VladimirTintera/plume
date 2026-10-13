package eu.tintera.plume.core.files

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AutoDownloadLimits
import eu.tintera.plume.core.telegram.AutosaveAction
import eu.tintera.plume.core.telegram.AutosaveCandidate
import eu.tintera.plume.core.telegram.AutosaveRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.autosaveAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours

/**
 * Ukládá fotky a videa z příchozích zpráv do galerie podle nastavení účtu ([AutosaveRepository]).
 *
 * Kdy stahovat, rozhoduje [autosaveAction] podle automatického stahování: co by se samo
 * nestáhlo, na to se jen čeká ([FileRepository.observe]) — uloží se, až soubor stáhne otevřený
 * chat nebo prohlížeč. Čeká se nejvýš [WAIT_LIMIT] a jen v běžícím procesu; po restartu se
 * na staré zprávy nečeká (nedorazí znovu jako nové).
 *
 * Najednou se stahují nejvýš [PARALLEL_DOWNLOADS] soubory, aby rušný kanál nezahltil síť.
 */
class AutosaveWorker(
    private val autosave: AutosaveRepository,
    private val files: FileRepository,
    /** Uloží soubor do galerie ([MediaSaver.save]); funkce kvůli testu, platformní třída podvrhnout nejde. */
    private val save: suspend (path: String, fileName: String, isVideo: Boolean) -> Boolean,
    private val limits: () -> AutoDownloadLimits,
    private val scope: CoroutineScope,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val logger = Logger.withTag("Autosave")
    private val downloads = Semaphore(PARALLEL_DOWNLOADS)

    fun start() {
        scope.launch {
            autosave.incoming.collect { candidate -> scope.launch { handle(candidate) } }
        }
    }

    private suspend fun handle(candidate: AutosaveCandidate) {
        val settings = autosave.settings().getOrNull() ?: return
        val action = autosaveAction(candidate, settings.forChat(candidate.chatId, candidate.chatType), limits())
        val path = when (action) {
            AutosaveAction.Skip -> return
            AutosaveAction.Download -> downloads.withPermit { awaitFile { files.file(candidate.fileId) } }
            AutosaveAction.WhenDownloaded -> withTimeoutOrNull(WAIT_LIMIT) { awaitFile { files.observe(candidate.fileId) } }
        } ?: return
        val name = savedFileName(candidate.originalName, path, candidate.date, zone())
        val saved = save(path, name, candidate.isVideo)
        logger.i { "Do galerie ${if (saved) "uloženo" else "NEULOŽENO"}: chat ${candidate.chatId}, ${if (candidate.isVideo) "video" else "fotka"} ($action)" }
    }

    /** Cesta k hotovému souboru, nebo `null`, když stažení selhalo. */
    private suspend fun awaitFile(state: () -> kotlinx.coroutines.flow.Flow<FileState>): String? =
        when (val done = state().first { it is FileState.Ready || it is FileState.Failed }) {
            is FileState.Ready -> done.path
            else -> null
        }

    private companion object {
        const val PARALLEL_DOWNLOADS = 2
        val WAIT_LIMIT = 6.hours
    }
}
