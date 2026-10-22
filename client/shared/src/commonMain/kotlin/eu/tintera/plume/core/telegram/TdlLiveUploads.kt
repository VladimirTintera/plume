package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.Error
import dev.g000sha256.tdl.dto.FileTypeVoiceNote
import dev.g000sha256.tdl.dto.InputFileGenerated
import dev.g000sha256.tdl.dto.UpdateFileGenerationStart
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toPath

/**
 * [LiveUploads] nad TDLib: rostoucí soubor jde do `preliminaryUploadFile` jako GENEROVANÝ
 * (`inputFileGenerated`). TDLib si o něj řekne updatem `updateFileGenerationStart` s cestou, kam
 * ho máme vytvářet; nahrávku tam průběžně kopírujeme ([GrowingFileCopy]) a hlásíme, kolik je
 * hotovo (`setFileGenerationProgress`). TDLib nahrává hotový začátek, zatímco přibývá zbytek,
 * a po puštění tlačítka zbývá dopsat poslední vteřiny (`finishFileGeneration`).
 *
 * Lokální soubor (`inputFileLocal`) by nešel: TDLib si velikost přečte jednou na začátku.
 *
 * **Vzniká eagerně** (`createdAtStart`): odběr `updateFileGenerationStart` musí běžet dřív než
 * první nahrávání. Generování s naší konverzí, na které nikdo nečeká (aplikace spadla a TDLib po
 * restartu dělá, kde přestal), se hned ukončí chybou — nahrávka, ze které se generovalo, už není.
 */
class TdlLiveUploads(
    private val holder: TelegramClientHolder,
    private val scope: CoroutineScope,
) : LiveUploads {

    private val client: TdlClient get() = holder.client
    private val logger = Logger.withTag("LiveUploads")

    private val lock = Mutex()
    private val waiting = mutableMapOf<String, CompletableDeferred<UpdateFileGenerationStart>>()
    private val stopped = mutableSetOf<Long>()

    init {
        scope.launch {
            holder.updates { fileGenerationStartUpdates }.collect { update ->
                if (!update.conversion.startsWith(CONVERSION_PREFIX)) return@collect
                val upload = lock.withLock { waiting.remove(update.conversion) }
                if (upload == null) {
                    client.finishFileGeneration(generationId = update.generationId, error = Error(code = 400, message = "Recording is gone"))
                } else {
                    upload.complete(update)
                }
            }
        }
        scope.launch {
            holder.updates { fileGenerationStopUpdates }.collect { update -> lock.withLock { stopped += update.generationId } }
        }
    }

    override fun startVoiceNote(path: String): LiveUpload = Upload(path, CONVERSION_PREFIX + Random.nextLong().toULong().toString(16))

    override fun discard(fileId: Int) {
        scope.launch { client.cancelPreliminaryUploadFile(fileId = fileId) }
    }

    private inner class Upload(private val path: String, private val conversion: String) : LiveUpload {
        private val generation = CompletableDeferred<UpdateFileGenerationStart>()
        private val fileId = CompletableDeferred<Int?>()
        private var copy: GrowingFileCopy? = null

        /** Rozjede nahrávání a pak kopíruje, co nahrávač dopsal, dokud ho [complete] nezastaví. */
        private val job = scope.launch {
            lock.withLock { waiting[conversion] = generation }
            val file = client.preliminaryUploadFile(
                file = InputFileGenerated(originalPath = path, conversion = conversion, expectedSize = EXPECTED_SIZE),
                fileType = FileTypeVoiceNote(),
                priority = UPLOAD_PRIORITY,
            ).toResult()
                .onFailure { logger.w(it) { "Předběžné nahrávání hlasovky nejde spustit." } }
                .getOrNull()
            fileId.complete(file?.id)
            if (file == null) return@launch
            val started = generation.await()
            val growing = GrowingFileCopy(path.toPath(), started.destinationPath.toPath())
            copy = growing
            var reported = 0L
            while (true) {
                val copied = withContext(Dispatchers.Default) { runCatching { growing.copyAvailable() }.getOrDefault(reported) }
                if (copied > reported) {
                    client.setFileGenerationProgress(generationId = started.generationId, expectedSize = maxOf(EXPECTED_SIZE, copied * 2), localPrefixSize = copied)
                    reported = copied
                }
                delay(COPY_INTERVAL_MS)
            }
        }

        override suspend fun complete(finalPath: String): Int? {
            val id = withTimeoutOrNull(START_TIMEOUT_MS) { fileId.await() }
            val started = withTimeoutOrNull(START_TIMEOUT_MS) { generation.await() }
            job.cancelAndJoin()
            val growing = copy
            if (id == null || started == null || growing == null || lock.withLock { started.generationId in stopped }) {
                logger.i { "Předem nahranou hlasovku nejde použít (soubor $id, generování ${started?.generationId})." }
                abandon()
                return null
            }
            val total = withContext(Dispatchers.Default) { runCatching { growing.finish(finalPath.toPath()) }.getOrNull() }
            if (total == null) {
                logger.i { "Hotová hlasovka se liší od předem nahrané; posílá se celá." }
                abandon()
                return null
            }
            client.setFileGenerationProgress(generationId = started.generationId, expectedSize = total, localPrefixSize = total)
            return client.finishFileGeneration(generationId = started.generationId, error = null).toResult()
                .onFailure { logger.w(it) { "Generování předem nahrané hlasovky nejde dokončit." } }
                .map { id }
                .getOrElse {
                    abandon()
                    null
                }
        }

        override fun abandon() {
            scope.launch {
                job.cancel()
                lock.withLock { waiting.remove(conversion) }
                if (fileId.isCompleted) fileId.await()?.let { client.cancelPreliminaryUploadFile(fileId = it) }
                if (generation.isCompleted) {
                    client.finishFileGeneration(
                        generationId = generation.await().generationId,
                        error = Error(code = 400, message = "Recording abandoned"),
                    )
                }
            }
        }
    }

    private companion object {
        /** Naše generování; po restartu podle ní poznáme osiřelé (viz třída). Nesmí začínat `#`. */
        const val CONVERSION_PREFIX = "plume-live-voice#"

        /** Hlasovka je to, na co uživatel čeká — přednost před stahováním médií v pozadí. */
        const val UPLOAD_PRIORITY = 16

        const val COPY_INTERVAL_MS = 300L

        /**
         * Odhad velikosti pro TDLib. **Bez odhadu (`expectedSize = 0`) TDLib během nahrávání
         * nenahrál nic** (změřeno: 35 kB nahrávky, `uploadedSize` celou dobu 0) — díl volí podle
         * velikosti a u neznámé čeká na velký. S odhadem nahrává po dílech 64 kB (menší nedělá
         * ani při menším odhadu), takže minutová hlasovka (~240 kB) je při puštění skoro celá
         * nahraná. Jak nahrávka roste, odhad se zvedá (`setFileGenerationProgress`).
         */
        const val EXPECTED_SIZE = 64L * 1024

        /** Na rozjezd nahrávání se po puštění tlačítka čeká nejvýš takhle; pak se posílá celý soubor. */
        const val START_TIMEOUT_MS = 3_000L
    }
}
