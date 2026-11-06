package eu.tintera.plume.core.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import co.touchlab.kermit.Logger
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gemma a Qwen přes LiteRT-LM: model si stáhne a spravuje aplikace sama. Který soubor, říká
 * [ModelVariant.model] ([LiteRtModel]); runtime je pro oba modely tentýž.
 *
 * Na rozdíl od Gemini Nano ([MlKitOnDeviceAi]) tu není systémová služba — soubor modelu (2,2 až
 * 3,7 GB) leží v `filesDir/ai-models` a počítá ho engine uvnitř našeho procesu.
 *
 * - **Stahuje se do `.part` a přejmenovává až hotový.** Přítomnost výsledného souboru tedy
 *   znamená „kompletní model" a nic dalšího se kontrolovat nemusí. Přerušené stahování
 *   navazuje (`Range`) — u gigabajtů by začínat od nuly bylo nepoužitelné — a po výpadku
 *   sítě naváže samo, když se připojení vrátí ([fetch]).
 * - **Stahuje úloha WorkManageru ([ModelDownloadWorker]), ne korutina téhle třídy.** Stahuje
 *   se minuty a proces aplikace v pozadí systém klidně zabije; úloha přežije i to a naváže
 *   na `.part`. Tahle třída drží jen stav pro obrazovku ([downloads]) — úloha běží v témže
 *   procesu a průběh do něj zapisuje přes [runDownload].
 * - **Engine je v paměti nejvýš jeden**, i když jsou zapnuté oba modely — přechod z Qwenu na
 *   Gemmu ten první zavře. Po chvíli bez použití se uvolní ([idleUnloadMs]).
 *   Drží gigabajty RAM; nechat ho viset po jednom shrnutí by znamenalo, že systém dřív zabije
 *   aplikaci v pozadí. Načtení trvá do deseti sekund (dokumentace LiteRT-LM), proto se neuvolňuje
 *   hned po odpovědi — návrhy odpovědí přicházejí často po sobě. Když Gemma dělá jen zvuk
 *   a obraz a text počítá Nano, uvolní se dřív ([gemmaIdleUnloadMs]).
 * - **Generování je jedno po druhém** ([engineLock]). Shrnutí a návrh odpovědi se můžou
 *   potkat a dvě konverzace nad jedním enginem LiteRT-LM výslovně neslibuje.
 * - **Počítání tokenů LiteRT-LM v Kotlinu nemá** ([countTokens] = `null`), takže se délka
 *   přepisu jen odhaduje z limitu. Limit je velikost KV keše ([MAX_NUM_TOKENS]) minus místo
 *   na odpověď ([OUTPUT_RESERVE_TOKENS]) — vstup a výstup se do ní vejít musí SPOLU.
 * - **Strop výstupu hlídáme sami** ([MAX_OUTPUT_CHARS]). Konfigurace konverzace žádný nemá;
 *   model skončí sám koncem odpovědi, ale kdyby se zacyklil, psal by do zaplnění keše.
 *
 * Nenabízí se pod Androidem 12 ([MIN_SDK]), bez arm64 a pod [LiteRtModel.minMemoryGb].
 * Selhání nese poskytovatele z varianty, takže hláška jmenuje model, který selhal.
 */
class LiteRtOnDeviceAi(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    /**
     * Za jak dlouho po poslední práci engine modelu (podle poskytovatele) uvolnit. Čte se po
     * každém použití, ne jednou: zapnutí Nano nebo jiné přiřazení úloh to mění za běhu.
     */
    private val idleUnloadMs: suspend (AiProvider) -> Long = { GEMMA_IDLE_TEXT_MS },
) : OnDeviceAi {

    private val logger = Logger.withTag("OnDeviceAi")

    /** Stahování a úklid enginu přežívají obrazovky; model je jeden na proces. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val modelsDir: File get() = File(context.filesDir, MODELS_DIR)
    private fun LiteRtModel.file() = File(modelsDir, fileName)
    private fun LiteRtModel.partFile() = File(modelsDir, "$fileName.part")

    private val downloads = ConcurrentHashMap<LiteRtModel, MutableStateFlow<DownloadState>>()

    private fun stateOf(model: LiteRtModel) = downloads.getOrPut(model) { MutableStateFlow(DownloadState.Idle) }

    private val engineLock = Mutex()
    private var loaded: LoadedEngine? = null
    private var idleUnload: Job? = null

    override suspend fun status(variant: ModelVariant): OnDeviceAiStatus {
        val model = variant.model ?: return OnDeviceAiStatus.Unsupported
        return when {
            !deviceCanRun(model) -> OnDeviceAiStatus.Unsupported
            model.file().exists() -> OnDeviceAiStatus.Ready
            // Úloha naplánovaná z minulého běhu procesu (zabitého uprostřed stahování) se
            // rozběhne sama, jen ještě nezapsala stav — pořád je to stahování.
            stateOf(model).value is DownloadState.Running ||
                ModelDownloadWorker.isPending(context, model) -> OnDeviceAiStatus.Downloading
            model.partFile().exists() -> OnDeviceAiStatus.Paused
            else -> OnDeviceAiStatus.Downloadable
        }
    }

    override suspend fun info(variant: ModelVariant): ModelInfo? {
        val model = variant.model ?: return null
        if (!model.file().exists()) return null
        return ModelInfo(name = model.displayName, tokenLimit = MAX_NUM_TOKENS - OUTPUT_RESERVE_TOKENS)
    }

    /**
     * Naplánuje stahování a sleduje jeho průběh. Běžící stahování (i úloha čekající na síť)
     * se jen sleduje; `KEEP` při plánování zaručí, že se nespustí podruhé.
     */
    override fun download(variant: ModelVariant): Flow<ModelDownload> = flow {
        val model = variant.file()
        val state = stateOf(model)
        if (state.value !is DownloadState.Running) {
            // Než se úloha rozběhne, ukazuje se, co už je stažené — a bez sítě rovnou čekání:
            // úloha se bez připojení vůbec nespustí (podmínka sítě), takže by stav jinak tvrdil,
            // že se stahuje.
            val offline = withTimeoutOrNull(NETWORK_CHECK_MS) {
                networkMonitor.status.first().kind == NetworkKind.None
            } == true
            state.value = DownloadState.Running(
                ModelDownload(model.partFile().length(), model.sizeBytes, waitingForNetwork = offline),
            )
            ModelDownloadWorker.enqueue(context, model)
        }
        state.transformWhile { current ->
            when (current) {
                is DownloadState.Running -> {
                    emit(current.progress)
                    true
                }
                DownloadState.Done -> false
                is DownloadState.Failed -> throw current.error
                DownloadState.Stopped -> throw DownloadStoppedException()
                DownloadState.Idle -> true
            }
        }.collect { emit(it) }
    }

    override suspend fun stopDownload(variant: ModelVariant) {
        val model = variant.file()
        ModelDownloadWorker.cancel(context, model)
        // Úloha to ohlásí taky ([onWorkerStopped]), jenže až doběhne zrušení; obrazovka má
        // odpovědět hned. Úloha, která ještě nezačala, neohlásí nic.
        stateOf(model).value = DownloadState.Stopped
        logger.i { "Stahování zastavené (${model.displayName})" }
    }

    /** Průběh stahování pro notifikaci úlohy. */
    internal fun downloadProgress(model: LiteRtModel): Flow<ModelDownload> =
        stateOf(model).filterIsInstance<DownloadState.Running>().map { it.progress }

    /**
     * Stáhne [model]; volá ho [ModelDownloadWorker]. Vyhodí, když se stáhnout nepodařilo —
     * stav pro obrazovku je pak už zapsaný. Zrušení (zastavení úlohy) jde dál beze změny
     * stavu; co znamená, ví až úloha podle důvodu zastavení ([onWorkerStopped]).
     */
    internal suspend fun runDownload(model: LiteRtModel) {
        val state = stateOf(model)
        if (model.file().exists()) {
            state.value = DownloadState.Done
            return
        }
        // Vždycky načisto: úloha se spouští jen s připojením, takže čekání na síť ze zastavené
        // úlohy už neplatí — a první hlášení průběhu přijde až po pár megabajtech.
        state.value = DownloadState.Running(ModelDownload(model.partFile().length(), model.sizeBytes))
        try {
            fetch(model, state)
            state.value = DownloadState.Done
            logger.i { "Model stažený (${model.displayName})" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.value = DownloadState.Failed(e)
            throw e
        }
    }

    /**
     * Úloha stahování skončila dřív, než model stáhla. Zastavil ji uživatel ([byUser]) →
     * stahování stojí; jinak ji zastavil systém (ztráta sítě, kvóta úloh, časový limit služby
     * v popředí) a WorkManager ji spustí znovu — pro obrazovku je to pořád stahování, jen při
     * [waitingForNetwork] s větou, že se čeká na připojení.
     */
    internal fun onWorkerStopped(model: LiteRtModel, byUser: Boolean, waitingForNetwork: Boolean) {
        val state = stateOf(model)
        val current = state.value
        when {
            byUser -> state.value = DownloadState.Stopped
            current is DownloadState.Running ->
                state.value = DownloadState.Running(current.progress.copy(waitingForNetwork = waitingForNetwork))
        }
    }

    override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? = null

    override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> = flow {
        withEngine(variant.file()) { engine ->
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
        .catch { e ->
            // LiteRT-LM chybové kódy nemá; cokoli z ní je selhání modelu. Zrušení `catch` nechytá.
            throw if (e is Exception && e !is OnDeviceAiException) {
                OnDeviceAiException(AiFailureReason.ModelError, variant.provider, cause = e)
            } else {
                e
            }
        }

    /** Gemma 4 (E2B i E4B) zvuk umí, Qwen ne; stačí, aby byla stažená a zařízení ji uneslo. */
    override suspend fun canTranscribe(variant: ModelVariant): Boolean =
        variant.model?.multimodal == true && status(variant) == OnDeviceAiStatus.Ready

    /**
     * Přepis řeči: zvuk po kusech ([AudioClips], WAV 16 kHz mono, nejvýš 30 s — tak ho modelu dává
     * Gallery), každý kus vlastní konverzací, text se skládá za sebe. Engine musí mít zvukový
     * backend (`audioBackend`, CPU — pro Gemmu 3n „must be CPU" i v Gallery), proto se načte
     * se zvukem; pak poslouží i textovým funkcím, aby se nepřenačítal.
     */
    override fun transcribe(variant: ModelVariant, audioPath: String, languageCode: String): Flow<Generated> = flow {
        val clips = withContext(Dispatchers.IO) { AudioClips.wavClips(audioPath) }
        if (clips.isEmpty()) throw IOException("No audio in $audioPath")
        withEngine(variant.file(), audio = true) { engine ->
            val done = StringBuilder()
            for (clip in clips) {
                val conversation = engine.createConversation(
                    ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)),
                )
                var finished = false
                try {
                    val part = StringBuilder()
                    conversation.sendMessageAsync(
                        Contents.of(Content.AudioBytes(clip), Content.Text(transcriptionPrompt(languageCode))),
                        extraContext = mapOf("enable_thinking" to false),
                    ).collect { message ->
                        part.append(message.toString())
                        emit(Generated(joinTranscript(done, part)))
                    }
                    finished = true
                    if (done.isNotEmpty() && part.isNotBlank()) done.append(' ')
                    done.append(part.trim())
                } finally {
                    if (!finished) quietly { conversation.cancelProcess() }
                    quietly { conversation.close() }
                }
            }
            emit(Generated(done.toString()))
        }
    }.flowOn(Dispatchers.Default)
        .catch { e ->
            throw if (e is Exception && e !is OnDeviceAiException) {
                OnDeviceAiException(AiFailureReason.ModelError, variant.provider, cause = e)
            } else {
                e
            }
        }

    /** Gemma 4 (E2B i E4B) obrázky umí, stejně jako zvuk; stačí připravený model. */
    override suspend fun canDescribeImage(variant: ModelVariant): Boolean =
        variant.model?.multimodal == true && status(variant) == OnDeviceAiStatus.Ready

    /**
     * Popis fotky: obrázek zmenšený na [MAX_IMAGE_SIDE] ([ImageInput]) a pokyn vedle něj, jedna
     * konverzace. Engine musí mít obrazový backend (`visionBackend`), proto se načte s ním;
     * pak poslouží i textovým funkcím.
     */
    override fun describeImage(variant: ModelVariant, imagePath: String, languageCode: String, prompt: String): Flow<Generated> = flow {
        val image = withContext(Dispatchers.IO) { ImageInput.png(imagePath, MAX_IMAGE_SIDE) }
            ?: throw IOException("Image $imagePath cannot be decoded")
        withEngine(variant.file(), vision = true) { engine ->
            val conversation = engine.createConversation(
                ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)),
            )
            var finished = false
            try {
                val text = StringBuilder()
                try {
                    conversation.sendMessageAsync(
                        Contents.of(Content.ImageBytes(image), Content.Text(prompt)),
                        extraContext = mapOf("enable_thinking" to false),
                    ).collect { message ->
                        text.append(message.toString())
                        if (text.length >= MAX_OUTPUT_CHARS) {
                            emit(Generated(text.substring(0, MAX_OUTPUT_CHARS), truncated = true))
                            throw OutputLimitReached
                        }
                        emit(Generated(text.toString().trimStart()))
                    }
                    finished = true
                } catch (e: OutputLimitReached) {
                    // Useknutí je platný výsledek, ne chyba — `truncated` už odešlo.
                }
            } finally {
                if (!finished) quietly { conversation.cancelProcess() }
                quietly { conversation.close() }
            }
        }
    }.flowOn(Dispatchers.Default)
        .catch { e ->
            throw if (e is Exception && e !is OnDeviceAiException) {
                OnDeviceAiException(AiFailureReason.ModelError, variant.provider, cause = e)
            } else {
                e
            }
        }

    /** Soubor modelu; varianta systémového modelu se sem dostat nemá (směrovač ji pošle jinam). */
    private fun ModelVariant.file(): LiteRtModel =
        model ?: throw OnDeviceAiException(AiFailureReason.Unavailable, provider)

    private fun joinTranscript(done: StringBuilder, part: StringBuilder): String =
        if (done.isEmpty()) part.toString().trimStart() else "$done ${part.toString().trimStart()}"

    override suspend fun delete(variant: ModelVariant) {
        val model = variant.file()
        ModelDownloadWorker.cancel(context, model)
        downloads.remove(model)
        engineLock.withLock {
            if (loaded?.model == model) unloadLocked()
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
    private suspend fun <T> withEngine(
        model: LiteRtModel,
        audio: Boolean = false,
        vision: Boolean = false,
        block: suspend (Engine) -> T,
    ): T = engineLock.withLock {
        idleUnload?.cancel()
        // Engine se zvukem (obrazem) poslouží i textu; bez něj se pro přepis (popis) musí načíst
        // znovu. Při přenačtení si nechá i to, co uměl dosud, ať se přepis a popis nestřídají
        // přenačítáním.
        val current = loaded?.takeIf { it.model == model }
        val engine = current?.takeIf { (it.audio || !audio) && (it.vision || !vision) }?.engine ?: run {
            val withAudio = audio || current?.audio == true
            val withVision = vision || current?.vision == true
            unloadLocked()
            if (!model.file().exists()) throw IOException("Model ${model.displayName} is not downloaded")
            createEngine(model, withAudio, withVision).also { loaded = LoadedEngine(model, withAudio, withVision, it) }
        }
        try {
            block(engine)
        } finally {
            idleUnload = scope.launch {
                delay(runCatching { idleUnloadMs(model.provider) }.getOrDefault(GEMMA_IDLE_TEXT_MS))
                engineLock.withLock { unloadLocked() }
            }
        }
    }

    private fun unloadLocked() {
        loaded?.engine?.let { engine -> quietly { engine.close() } }
        loaded = null
    }

    /**
     * GPU, a když nejde, CPU. GPU backend potřebuje `libOpenCL.so` (manifest aplikace,
     * `uses-native-library`), kterou některá zařízení nemají — na CPU je model pomalejší, ale
     * funguje. `LinkageError` se chytá kvůli nativní knihovně, stejně jako u ML Kitu: na zařízení,
     * kde se nenačte, nesmí aplikace spadnout jen proto, že si někdo zapnul volitelnou funkci.
     */
    private suspend fun createEngine(model: LiteRtModel, audio: Boolean, vision: Boolean): Engine = withContext(Dispatchers.Default) {
        fun start(backend: Backend) = Engine(
            EngineConfig(
                modelPath = model.file().absolutePath,
                backend = backend,
                // Obraz počítá týž backend jako text (Gallery: GPU, když jde) — na CPU se
                // spadne spolu s ním.
                visionBackend = if (vision) backend else null,
                audioBackend = if (audio) Backend.CPU() else null,
                maxNumImages = if (vision) 1 else null,
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
    private suspend fun fetch(model: LiteRtModel, state: MutableStateFlow<DownloadState>) {
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
    }

    /**
     * Stáhne soubor do `.part` s navázáním na to, co už je staženo, a hotový přejmenuje.
     *
     * Hugging Face přesměrovává na CDN; `HttpURLConnection` přesměrování v rámci HTTPS následuje
     * sám a hlavičku `Range` přenese. Server, který rozsah nevezme (200 místo 206), dostane
     * soubor od začátku. **401/403 znamená model za souhlasem s licencí** — to prototyp neumí
     * (chtělo by to přihlášení k Hugging Face) a hlásí se jako obyčejné selhání.
     */
    private suspend fun fetchFile(model: LiteRtModel, onProgress: (ModelDownload) -> Unit) = withContext(Dispatchers.IO) {
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
    private fun moveFinished(model: LiteRtModel) {
        check(model.partFile().renameTo(model.file())) { "Cannot move ${model.partFile().name}" }
    }

    private fun deviceCanRun(model: LiteRtModel): Boolean {
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

        /** Zastavil uživatel; stažená část zůstává. */
        data object Stopped : DownloadState
    }

    /** Server odpověděl jinak než daty. Čekáním na síť se to nespraví, takže se neopakuje. */
    private class HttpStatusException(code: Int, model: LiteRtModel) :
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

        /** Delší strana obrázku pro model. Kodér si ho zmenší sám; větší jen zdrží dekódování. */
        const val MAX_IMAGE_SIDE = 1_024

        /** Pokusy o navázání, během kterých nepřibyl ani bajt; pak se stahování vzdá. */
        const val MAX_RETRIES_WITHOUT_PROGRESS = 5

        /** Prodleva před dalším pokusem, násobená počtem pokusů bez pokroku. */
        const val RETRY_DELAY_MS = 2_000L

        const val HTTP_RANGE_NOT_SATISFIABLE = 416

        /** Jak dlouho se při plánování čeká na první hlášení sítě; monitor odpovídá hned. */
        const val NETWORK_CHECK_MS = 1_000L

        const val TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 256 * 1024
        const val REPORT_EVERY_BYTES = 4L * 1024 * 1024
        const val FREE_SPACE_MARGIN = 500L * 1024 * 1024
        const val GIB = 1024.0 * 1024 * 1024
        const val MEMORY_TOLERANCE = 0.85
    }
}

/** Načtený engine: který soubor a jestli má zvukový backend (jen ten umí přepis). */
private class LoadedEngine(val model: LiteRtModel, val audio: Boolean, val vision: Boolean, val engine: Engine)
