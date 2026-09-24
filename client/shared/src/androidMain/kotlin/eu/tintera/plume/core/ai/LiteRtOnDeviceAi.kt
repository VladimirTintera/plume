package eu.tintera.plume.core.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import eu.tintera.plume.core.connectivity.NetworkKind
import eu.tintera.plume.core.connectivity.NetworkMonitor
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gemma přes LiteRT-LM: model si stáhne a spravuje aplikace sama.
 *
 * Na rozdíl od Gemini Nano ([MlKitOnDeviceAi]) tu není systémová služba — soubor modelu (2,6 až
 * 3,7 GB) leží v `filesDir/ai-models` a počítá ho engine uvnitř našeho procesu.
 *
 * - **Stahuje se do `.part` a přejmenovává až hotový.** Přítomnost výsledného souboru tedy
 *   znamená „kompletní model" a nic dalšího se kontrolovat nemusí. Přerušené stahování
 *   navazuje (`Range`) — u gigabajtů by začínat od nuly bylo nepoužitelné — a po výpadku
 *   sítě naváže samo, když se připojení vrátí ([fetch]).
 * - **Engine je v paměti nejvýš jeden** a po [IDLE_UNLOAD_MS] bez použití se uvolní. Drží
 *   gigabajty RAM; nechat ho viset po jednom shrnutí by znamenalo, že systém dřív zabije
 *   aplikaci v pozadí. Načtení trvá do deseti sekund (dokumentace LiteRT-LM), proto se neuvolňuje
 *   hned po odpovědi — návrhy odpovědí přicházejí často po sobě.
 * - **Generování je jedno po druhém** ([engineLock]). Shrnutí a návrh odpovědi se můžou
 *   potkat a dvě konverzace nad jedním enginem LiteRT-LM výslovně neslibuje.
 * - **Počítání tokenů LiteRT-LM v Kotlinu nemá** ([countTokens] = `null`), takže se délka
 *   přepisu jen odhaduje z limitu. Limit je velikost KV keše ([MAX_NUM_TOKENS]) minus místo
 *   na odpověď ([OUTPUT_RESERVE_TOKENS]) — vstup a výstup se do ní vejít musí SPOLU.
 * - **Strop výstupu hlídáme sami** ([MAX_OUTPUT_CHARS]). Konfigurace konverzace žádný nemá;
 *   model skončí sám koncem odpovědi, ale kdyby se zacyklil, psal by do zaplnění keše.
 *
 * Nenabízí se pod Androidem 12 ([MIN_SDK]), bez arm64 a pod [GemmaModel.minMemoryGb].
 */
class LiteRtOnDeviceAi(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
) : OnDeviceAi {

    private val logger = Logger.withTag("OnDeviceAi")

    /** Stahování a úklid enginu přežívají obrazovky; model je jeden na proces. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val modelsDir: File get() = File(context.filesDir, MODELS_DIR)
    private fun GemmaModel.file() = File(modelsDir, fileName)
    private fun GemmaModel.partFile() = File(modelsDir, "$fileName.part")

    private val downloads = ConcurrentHashMap<GemmaModel, MutableStateFlow<DownloadState>>()
    private val downloadJobs = ConcurrentHashMap<GemmaModel, Job>()

    private val engineLock = Mutex()
    private var loaded: Pair<GemmaModel, Engine>? = null
    private var idleUnload: Job? = null

    override suspend fun status(variant: ModelVariant): OnDeviceAiStatus {
        val model = variant.gemma
        return when {
            !deviceCanRun(model) -> OnDeviceAiStatus.Unsupported
            downloadJobs[model]?.isActive == true -> OnDeviceAiStatus.Downloading
            model.file().exists() -> OnDeviceAiStatus.Ready
            else -> OnDeviceAiStatus.Downloadable
        }
    }

    override suspend fun info(variant: ModelVariant): ModelInfo? {
        val model = variant.gemma
        if (!model.file().exists()) return null
        return ModelInfo(name = model.displayName, tokenLimit = MAX_NUM_TOKENS - OUTPUT_RESERVE_TOKENS)
    }

    override fun download(variant: ModelVariant): Flow<ModelDownload> = flow {
        val model = variant.gemma
        val state = downloads.getOrPut(model) { MutableStateFlow<DownloadState>(DownloadState.Idle) }
        // Běžící stahování se jen sleduje; nové se spustí, když žádné neběží.
        synchronized(downloadJobs) {
            if (downloadJobs[model]?.isActive != true) {
                state.value = DownloadState.Running(ModelDownload(0, model.sizeBytes))
                downloadJobs[model] = scope.launch { fetch(model, state) }
            }
        }
        state.transformWhile { current ->
            when (current) {
                is DownloadState.Running -> {
                    emit(current.progress)
                    true
                }
                DownloadState.Done -> false
                is DownloadState.Failed -> throw current.error
                DownloadState.Idle -> true
            }
        }.collect { emit(it) }
    }

    override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? = null

    override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> = flow {
        withEngine(variant.gemma) { engine ->
            val conversation = engine.createConversation(
                ConversationConfig(
                    // Shrnutí má držet fakta, ne vymýšlet — nízká teplota, jako u Gemini Nano.
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2),
                ),
            )
            var finished = false
            try {
                val text = StringBuilder()
                try {
                    // Každá zpráva streamu je PŘÍRŮSTEK (tak ho vypisuje i ukázka LiteRT-LM),
                    // rozhraní slibuje celý text. Přemýšlení (`enable_thinking`) je vypnuté:
                    // šlo by do samostatného kanálu a jen by prodloužilo čekání.
                    conversation.sendMessageAsync(prompt, extraContext = mapOf("enable_thinking" to false))
                        .collect { message ->
                            text.append(message.toString())
                            if (text.length >= MAX_OUTPUT_CHARS) {
                                emit(Generated(text.substring(0, MAX_OUTPUT_CHARS), truncated = true))
                                throw OutputLimitReached
                            }
                            emit(Generated(text.toString()))
                        }
                    finished = true
                } catch (e: OutputLimitReached) {
                    // Useknutí je platný výsledek, ne chyba — `truncated` už odešlo.
                }
            } finally {
                // Zrušené nebo useknuté generování by nativní strana jinak dopočítala do konce.
                if (!finished) quietly { conversation.cancelProcess() }
                quietly { conversation.close() }
            }
        }
    }.flowOn(Dispatchers.Default) // Zakládání konverzace a úklid jsou nativní volání; ne na hlavním vlákně.

    override suspend fun delete(variant: ModelVariant) {
        val model = variant.gemma
        downloadJobs.remove(model)?.cancel()
        downloads.remove(model)
        engineLock.withLock {
            if (loaded?.first == model) unloadLocked()
        }
        withContext(Dispatchers.IO) {
            model.file().delete()
            model.partFile().delete()
        }
        logger.i { "Model smazán (${model.displayName})" }
    }

    /**
     * Engine pro [model]: načtený se použije, jiný se nejdřív zavře (dva by se do paměti
     * nevešly). Po doběhnutí se naplánuje uvolnění — příští použití ho zase zruší.
     */
    private suspend fun <T> withEngine(model: GemmaModel, block: suspend (Engine) -> T): T = engineLock.withLock {
        idleUnload?.cancel()
        val engine = loaded?.takeIf { it.first == model }?.second ?: run {
            unloadLocked()
            if (!model.file().exists()) throw IOException("Model ${model.displayName} is not downloaded")
            createEngine(model).also { loaded = model to it }
        }
        try {
            block(engine)
        } finally {
            idleUnload = scope.launch {
                delay(IDLE_UNLOAD_MS)
                engineLock.withLock { unloadLocked() }
            }
        }
    }

    private fun unloadLocked() {
        loaded?.second?.let { engine -> quietly { engine.close() } }
        loaded = null
    }

    /**
     * GPU, a když nejde, CPU. GPU backend potřebuje `libOpenCL.so` (manifest aplikace,
     * `uses-native-library`), kterou některá zařízení nemají — na CPU je model pomalejší, ale
     * funguje. `LinkageError` se chytá kvůli nativní knihovně, stejně jako u ML Kitu: na zařízení,
     * kde se nenačte, nesmí aplikace spadnout jen proto, že si někdo zapnul volitelnou funkci.
     */
    private suspend fun createEngine(model: GemmaModel): Engine = withContext(Dispatchers.Default) {
        fun start(backend: Backend) = Engine(
            EngineConfig(
                modelPath = model.file().absolutePath,
                backend = backend,
                maxNumTokens = MAX_NUM_TOKENS,
                // Keš zkompilovaného modelu zrychlí druhé načtení; do `cacheDir`, ať ji systém
                // smí uklidit — soubor modelu vedle ní je drahý, keš ne.
                cacheDir = context.cacheDir.absolutePath,
            ),
        ).also { it.initialize() }

        try {
            try {
                start(Backend.GPU())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.i(e) { "GPU backend nejde, zkouším CPU (${model.displayName})" }
                start(Backend.CPU())
            }
        } catch (e: LinkageError) {
            // Error, ne výjimka: `catch (Exception)` volajících by ho nechytil a aplikace by spadla.
            throw IOException("LiteRT-LM native library failed to load", e)
        }
    }

    /**
     * Stáhne model a po výpadku sítě naváže SÁM, jakmile se připojení vrátí.
     *
     * Gigabajty se stahují minuty, a přejít z Wi-Fi na data nebo projet tunelem je za tu dobu
     * běžné. Dokud výpadek končil hláškou „nepodařilo se stáhnout", musel se uživatel vrátit
     * do Nastavení a ťuknout znovu, přestože se vlastně nic nepokazilo.
     *
     * Opakuje se jen chyba spojení ([IOException]). Odpověď serveru ([HttpStatusException])
     * ani nedostatek místa se čekáním nespraví. Pokusů bez jediného nového bajtu je nejvýš
     * [MAX_RETRIES_WITHOUT_PROGRESS] — server, který spojení pokaždé shodí, jinak nekončí.
     *
     * Selhání se tu NELOGUJE, loguje ho volající ([OnDeviceAiSettingsViewModel]), který ho
     * dostane i od Gemini Nano. Ve dvou místech by v logu bylo dvakrát.
     */
    private suspend fun fetch(model: GemmaModel, state: MutableStateFlow<DownloadState>) {
        try {
            var failures = 0
            while (true) {
                val before = model.partFile().length()
                try {
                    fetchFile(model) { progress -> state.value = DownloadState.Running(progress) }
                    break
                } catch (e: HttpStatusException) {
                    throw e
                } catch (e: IOException) {
                    failures = if (model.partFile().length() > before) 1 else failures + 1
                    if (failures > MAX_RETRIES_WITHOUT_PROGRESS) throw e
                    logger.i { "Stahování přerušené (${e.message}), čekám na síť (${model.displayName})" }
                    state.value = DownloadState.Running(
                        ModelDownload(model.partFile().length(), model.sizeBytes, waitingForNetwork = true),
                    )
                    // Nejdřív síť, pak prodleva: po návratu sítě chvíli trvá, než opravdu vede
                    // ven, a výpadek, o kterém systém ještě neví, se tím nepřepálí na pokusy.
                    networkMonitor.status.first { it.kind != NetworkKind.None }
                    delay(RETRY_DELAY_MS * failures)
                }
            }
            state.value = DownloadState.Done
            logger.i { "Model stažený (${model.displayName})" }
        } catch (e: CancellationException) {
            state.value = DownloadState.Failed(e)
            throw e
        } catch (e: Exception) {
            state.value = DownloadState.Failed(e)
        }
    }

    /**
     * Stáhne soubor do `.part` s navázáním na to, co už je staženo, a hotový přejmenuje.
     *
     * Hugging Face přesměrovává na CDN; `HttpURLConnection` přesměrování v rámci HTTPS následuje
     * sám a hlavičku `Range` přenese. Server, který rozsah nevezme (200 místo 206), dostane
     * soubor od začátku. **401/403 znamená model za souhlasem s licencí** — to prototyp neumí
     * (chtělo by to přihlášení k Hugging Face) a hlásí se jako obyčejné selhání.
     */
    private suspend fun fetchFile(model: GemmaModel, onProgress: (ModelDownload) -> Unit) = withContext(Dispatchers.IO) {
        modelsDir.mkdirs()
        val part = model.partFile()
        var offset = if (part.exists()) part.length() else 0L

        val connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = connection.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE && part.length() == model.sizeBytes) {
                // `.part` je celý, jen se nestihl přejmenovat (proces zabitý mezi posledním
                // zápisem a přejmenováním). Bez tohohle by každý další pokus skončil 416.
                moveFinished(model)
                onProgress(ModelDownload(model.sizeBytes, model.sizeBytes))
                return@withContext
            }
            if (code == HttpURLConnection.HTTP_OK) offset = 0L
            else if (code != HttpURLConnection.HTTP_PARTIAL) throw HttpStatusException(code, model)

            val remaining = connection.contentLengthLong.takeIf { it > 0 } ?: (model.sizeBytes - offset)
            val total = offset + remaining
            // Rezerva nad velikost souboru: plný disk v půli stahování je horší než odmítnutí předem.
            if (StatFs(modelsDir.absolutePath).availableBytes < remaining + FREE_SPACE_MARGIN) {
                throw NotEnoughSpaceException(remaining + FREE_SPACE_MARGIN)
            }

            var written = offset
            var reported = -1L
            connection.inputStream.use { input ->
                java.io.FileOutputStream(part, offset > 0).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        // Hlásí se po megabajtech — každý buffer by zahlcoval UI.
                        if (written - reported >= REPORT_EVERY_BYTES) {
                            reported = written
                            onProgress(ModelDownload(written, total))
                        }
                    }
                }
            }
            if (written != total) throw IOException("Incomplete download: $written of $total B")
            moveFinished(model)
            onProgress(ModelDownload(total, total))
        } finally {
            connection.disconnect()
        }
    }

    /** Hotový `.part` přejmenuje na model. Není to chyba sítě, takže ne [IOException]. */
    private fun moveFinished(model: GemmaModel) {
        check(model.partFile().renameTo(model.file())) { "Cannot move ${model.partFile().name}" }
    }

    private fun deviceCanRun(model: GemmaModel): Boolean {
        if (Build.VERSION.SDK_INT < MIN_SDK) return false
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) return false
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        // `totalMem` hlásí o kus méně než udává výrobce (část si vezme jádro), proto rezerva.
        return memory.totalMem >= model.minMemoryGb * GIB * MEMORY_TOLERANCE
    }

    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.i(e) { "LiteRT-LM úklid selhal" }
        }
    }

    private sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val progress: ModelDownload) : DownloadState
        data object Done : DownloadState
        data class Failed(val error: Throwable) : DownloadState
    }

    /** Server odpověděl jinak než daty. Čekáním na síť se to nespraví, takže se neopakuje. */
    private class HttpStatusException(code: Int, model: GemmaModel) :
        IOException("HTTP $code for ${model.displayName}")

    /** Vnitřní signál, že výstup dosáhl stropu; ven se nedostane. */
    private object OutputLimitReached : RuntimeException()

    private companion object {
        const val MODELS_DIR = "ai-models"

        /** Gallery, ze které je katalog modelů, jede od Androidu 12; níž to nikdo neověřil. */
        const val MIN_SDK = 31

        /**
         * Velikost KV keše = vstup a výstup DOHROMADY. Gemma 4 unese víc (Gallery uvádí kontext
         * 32 000), ale keš stojí paměť úměrně délce, a 8 192 je totéž, co dává Gemini Nano —
         * shrnutí se tak u obou poskytovatelů chová stejně.
         */
        const val MAX_NUM_TOKENS = 8_192

        /** Kolik z keše zůstane na odpověď. Víc než [MAX_OUTPUT_CHARS] v tokenech s rezervou. */
        const val OUTPUT_RESERVE_TOKENS = 1_536

        /** Strop výstupu; zhruba stejně dlouhé shrnutí, jaké unese Gemini Nano (800 tokenů). */
        const val MAX_OUTPUT_CHARS = 4_000

        const val IDLE_UNLOAD_MS = 120_000L

        /** Pokusy o navázání, během kterých nepřibyl ani bajt; pak se stahování vzdá. */
        const val MAX_RETRIES_WITHOUT_PROGRESS = 5

        /** Prodleva před dalším pokusem, násobená počtem pokusů bez pokroku. */
        const val RETRY_DELAY_MS = 2_000L

        const val HTTP_RANGE_NOT_SATISFIABLE = 416

        const val TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 256 * 1024
        const val REPORT_EVERY_BYTES = 4L * 1024 * 1024
        const val FREE_SPACE_MARGIN = 500L * 1024 * 1024
        const val GIB = 1024.0 * 1024 * 1024
        const val MEMORY_TOLERANCE = 0.85
    }
}
