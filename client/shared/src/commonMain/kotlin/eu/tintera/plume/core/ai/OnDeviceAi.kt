package eu.tintera.plume.core.ai

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * Jazykový model běžící v zařízení.
 *
 * Hranice stejně jako `core/telegram` nebo `core/logging`: za ní jsou na Androidu DVA runtime —
 * Gemini Nano v AICore přes ML Kit Prompt API (`com.google.mlkit.genai.*`) a Gemma přes
 * LiteRT-LM (`com.google.ai.edge.litertlm.*`); obojí smí být importované jen v
 * `androidMain/core/ai`. Který se použije, určuje [ModelVariant.provider] a rozhoduje o tom
 * [RoutingOnDeviceAi]. Variantu si funkce bere pro SVOU úlohu ([variantFor], [AiTask]): zapnuté
 * mohou být oba modely a každá úloha jde jinému — Nano na text, Gemma na zvuk a obraz.
 *
 * Rozhraní, ne `expect class`: ViewModel se pak dá testovat s podstrčeným modelem.
 *
 * Každé volání bere [ModelVariant] — varianty jsou různé modely, každá se stahuje zvlášť a má
 * vlastní stav i limit.
 */
interface OnDeviceAi {

    /** V jakém stavu je model. Levný dotaz na systém, nic nestahuje. */
    suspend fun status(variant: ModelVariant): OnDeviceAiStatus

    /** Název a limit vstupu; `null`, když model v zařízení není (limit zná až stažený model). */
    suspend fun info(variant: ModelVariant): ModelInfo?

    /**
     * Stáhne model a hlásí průběh. Tok skončí po dokončení, selhání se vyhodí jako výjimka,
     * zastavení ([stopDownload]) jako [DownloadStoppedException]. Volání ve stavu
     * [OnDeviceAiStatus.Downloading] se připojí k běžícímu stahování, ve stavu
     * [OnDeviceAiStatus.Paused] naváže na staženou část.
     */
    fun download(variant: ModelVariant): Flow<ModelDownload>

    /**
     * Zastaví stahování; stažená část zůstává a příští [download] na ni naváže. Jen tam, kde
     * model stahuje aplikace sama ([ModelVariant.appManaged]) — stahování AICore řídí systém.
     */
    suspend fun stopDownload(variant: ModelVariant) {}

    /** Kolik tokenů zabere [prompt] na vstupu; `null`, když to model neumí říct. */
    suspend fun countTokens(variant: ModelVariant, prompt: String): Int?

    /**
     * Vygeneruje odpověď na [prompt] a vydává ji postupně.
     *
     * Každá hodnota nese CELÝ dosavadní text, ne přírůstek — obrazovka ho jen vykreslí a nemusí
     * nic skládat.
     */
    fun generate(variant: ModelVariant, prompt: String): Flow<Generated>

    /**
     * Umí model vrátit odpověď rovnou jako typ ([generateStructured]), ne jako text? Jen
     * PŘIPRAVENÝ Gemini Nano, jehož AICore strukturovaný výstup hlásí; Gemma přes LiteRT-LM ho
     * nemá. Kdo dostane `false`, pokračuje textem a vlastním parserem.
     */
    suspend fun supportsStructuredOutput(variant: ModelVariant): Boolean = false

    /**
     * Vygeneruje odpověď na [prompt] rovnou jako instanci [type] — tvar drží model, ne parser.
     *
     * **Typ musí mít v platformě popis tvaru** (na Androidu `GenerableProvider` v `core/ai`
     * a řádek v `META-INF/services`); neznámý typ selže. Nestreamuje se: polovina objektu
     * nic neznamená. Kde strukturovaný výstup není ([supportsStructuredOutput] `false`), selže.
     */
    suspend fun <T : Any> generateStructured(variant: ModelVariant, prompt: String, type: KClass<T>): T =
        throw OnDeviceAiException(AiFailureReason.Unavailable, variant.provider)

    /**
     * Umí model přepsat řeč ze zvuku ([transcribe])? Jen PŘIPRAVENÝ model se zvukovým vstupem
     * (dnes Gemma 4; Gemini Nano přes ML Kit zvuk nebere). Levný dotaz, nic nenačítá.
     */
    suspend fun canTranscribe(variant: ModelVariant): Boolean = false

    /**
     * Přepíše řeč ze zvukového souboru [audioPath] (hlasovka OGG/Opus, kolečko MP4) do textu;
     * [languageCode] je jen nápověda, mluví-li se jinak, přepíše se, jak se mluví. Vydává celý
     * dosavadní text jako [generate]. Kde přepis není ([canTranscribe] `false`), selže.
     */
    fun transcribe(variant: ModelVariant, audioPath: String, languageCode: String): Flow<Generated> =
        flow { throw OnDeviceAiException(AiFailureReason.Unavailable, variant.provider) }

    /**
     * Umí model popsat obrázek ([describeImage])? Jen PŘIPRAVENÝ model s obrazovým vstupem
     * (dnes Gemma 4; Gemini Nano přes Prompt API bere jen text). Levný dotaz, nic nenačítá.
     */
    suspend fun canDescribeImage(variant: ModelVariant): Boolean = false

    /**
     * Popíše fotku ze souboru [imagePath] několika větami v jazyce [languageCode] — pro toho,
     * kdo ji nevidí. Vydává celý dosavadní text jako [generate]. Kde popis není
     * ([canDescribeImage] `false`), selže.
     */
    fun describeImage(variant: ModelVariant, imagePath: String, languageCode: String): Flow<Generated> =
        flow { throw OnDeviceAiException(AiFailureReason.Unavailable, variant.provider) }

    /**
     * Smaže stažený model a uvolní místo. Jen tam, kde model stahuje aplikace sama
     * ([ModelVariant.appManaged]); systémový model (AICore) spravuje telefon a tohle je u něj
     * no-op.
     */
    suspend fun delete(variant: ModelVariant) {}
}

/**
 * Kterou podobu modelu chceme. Volby jsou PŘÁNÍ — co opravdu běží, řekne [ModelInfo.name].
 *
 * @property provider který runtime model počítá. Vybírá ho [chooseProvider] podle úlohy.
 * @property preview (jen Gemini Nano) nejnovější verze v předběžném vydání místo stabilní.
 *   Bývá jen na novějších zařízeních a stahuje se zvlášť.
 * @property fast (jen Gemini Nano) rychlejší a méně přesná varianta místo plné.
 * @property gemma (jen Gemma) který model si aplikace stáhne.
 */
data class ModelVariant(
    val provider: AiProvider = AiProvider.GeminiNano,
    val preview: Boolean = false,
    val fast: Boolean = false,
    val gemma: GemmaModel = GemmaModel.E2B,
) {
    /** Model stahuje a maže aplikace sama, ne systém. */
    val appManaged: Boolean get() = provider == AiProvider.Gemma
}

/**
 * Kdo model počítá.
 *
 * - [GeminiNano]: systémový model v AICore. Stahuje a aktualizuje ho telefon, aplikace nic
 *   nebalí, ale je jen na některých zařízeních a ML Kit nepustí k větším variantám.
 * - [Gemma]: otevřený model, který si stáhne aplikace (gigabajty) a počítá ho přes LiteRT-LM.
 *   Běží na každém dost silném telefonu a jde vybrat i větší E4B.
 */
enum class AiProvider {
    GeminiNano,
    Gemma,

    /**
     * Apple Intelligence (Foundation Models) na iOS 26+: systémový model jako Gemini Nano —
     * stahuje a aktualizuje ho systém, aplikace ho jen používá (`AppleOnDeviceAi`, Swift most).
     */
    Apple,
}

/**
 * Modely Gemma, které si aplikace umí stáhnout.
 *
 * Soubor, verze (commit na Hugging Face) i velikost jsou převzaté z katalogu Google AI Edge
 * Gallery (`model_allowlists/1_0_19.json`). **Commit je součást adresy**, takže se stahuje
 * pořád tentýž soubor, i když autor na Hugging Face nahraje novější — nová verze = nový řádek
 * tady, vědomě. [minMemoryGb] je doporučení Gallery; pod ním se model nenabízí, protože by
 * systém aplikaci při načítání zabil.
 */
enum class GemmaModel(
    val displayName: String,
    val repository: String,
    val fileName: String,
    val commit: String,
    val sizeBytes: Long,
    val minMemoryGb: Int,
) {
    E2B(
        displayName = "Gemma 4 E2B",
        repository = "litert-community/gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        commit = "6e5c4f1e395deb959c494953478fa5cec4b8008f",
        sizeBytes = 2_588_147_712,
        minMemoryGb = 8,
    ),
    E4B(
        displayName = "Gemma 4 E4B",
        repository = "litert-community/gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        commit = "28299f30ee4d43294517a4ac93abd6163412f07f",
        sizeBytes = 3_659_530_240,
        minMemoryGb = 12,
    ),
    ;

    val downloadUrl: String
        get() = "https://huggingface.co/$repository/resolve/$commit/$fileName?download=true"
}

/**
 * Model odpověděl, ale ne v požadovaném tvaru ([OnDeviceAi.generateStructured]): JSON, který
 * do typu nesedí, nebo useknutý strop výstupu. **Není to rozbitý model** ([OnDeviceAiException]
 * by šel do Crashlytics jako problém) — volající má jít textem, jako by strukturovaný výstup
 * nebyl.
 *
 * @property code kód runtime (ML Kit `STRUCTURED_OUTPUT_…`), když nějaký je.
 */
class StructuredOutputException(val code: Int?, cause: Throwable? = null) :
    Exception("Structured output failed" + (code?.let { " (code $it)" } ?: ""), cause)

/**
 * Na model není v telefonu dost místa. Hlásí se ZVLÁŠŤ, ne jako obecné selhání stahování:
 * u modelu o gigabajtech je to nejčastější důvod a uživatel s ním jako jediným může něco dělat.
 */
class NotEnoughSpaceException(val neededBytes: Long) : Exception("Not enough space: $neededBytes B")

/**
 * Stahování zastavil uživatel ([OnDeviceAi.stopDownload]). Není to selhání: stažená část
 * zůstává a obrazovka nabídne pokračování. Schválně ne `CancellationException` — tu volající
 * posílají dál a o zastavení by se nedozvěděli.
 */
class DownloadStoppedException : Exception("Model download stopped")

/**
 * Kus generované odpovědi.
 *
 * [text] je celý dosavadní text. [truncated] znamená, že model NEDOMLUVIL: došel mu strop
 * výstupu a text prostě končí, klidně uprostřed věty. Není to chyba — odpověď je platná, jen
 * neúplná —, takže se nevyhazuje výjimka; uživateli to ale musí UI říct, jinak si useknuté
 * shrnutí přečte jako úplné. Model to hlásí sám (ML Kit `Candidate.FinishReason.MAX_TOKENS`),
 * nepoznává se to odhadem z délky textu.
 */
data class Generated(val text: String, val truncated: Boolean = false)

/** Co o modelu víme. [tokenLimit] je strop VSTUPU v tokenech. */
data class ModelInfo(val name: String?, val tokenLimit: Int?)

enum class OnDeviceAiStatus {
    /** Zařízení model (v téhle variantě) nemá a mít nebude (bez AICore, málo paměti, iOS). */
    Unsupported,

    /** Model jde stáhnout. */
    Downloadable,

    /** Model se právě stahuje (nebo na stahování čeká, třeba na síť). */
    Downloading,

    /**
     * Model je stažený zčásti a stahování neběží — uživatel ho zastavil. Příští stažení naváže
     * na to, co už je v telefonu. Jen u modelu, který stahuje aplikace.
     */
    Paused,

    /** Model je v zařízení a jde hned použít. */
    Ready,
}

/**
 * Průběh stahování modelu. [totalBytes] je `null`, dokud ho systém neohlásí.
 *
 * @property waitingForNetwork stahování se přerušilo se sítí a naváže samo, až se připojení
 *   vrátí. Pořád je to stahování, ne selhání: uživatel nemá nic dělat.
 */
data class ModelDownload(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val waitingForNetwork: Boolean = false,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

/** Platforma (nebo runtime) bez modelu v zařízení. Funkce se pak v UI vůbec nenabízí. */
object NoopOnDeviceAi : OnDeviceAi {
    override suspend fun status(variant: ModelVariant) = OnDeviceAiStatus.Unsupported
    override suspend fun info(variant: ModelVariant): ModelInfo? = null
    override fun download(variant: ModelVariant): Flow<ModelDownload> = emptyFlow()
    override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? = null
    override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> = emptyFlow()
}
