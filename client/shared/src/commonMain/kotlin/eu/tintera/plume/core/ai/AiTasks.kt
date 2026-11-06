package eu.tintera.plume.core.ai

import eu.tintera.plume.core.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Co model v aplikaci dělá. Každá funkce s modelem se ptá na model PRO SVOU ÚLOHU
 * ([variantFor]), ne na jeden model celé aplikace: Gemini Nano je lehký, ale zvuk ani obraz
 * nebere, Gemma je umí, jenže drží gigabajty paměti. Kdo má oba, chce Nano na text a Gemmu
 * jen na to, co Nano neumí.
 *
 * @property input co úloha modelu dává; rozhoduje, který model ji vůbec zvládne ([AiProvider.handles]).
 */
enum class AiTask(val input: AiInput) {
    /** Shrnutí konverzace (`ChatSummaryViewModel.summarize`). */
    Summary(AiInput.Text),

    /** Vysvětlení jedné dlouhé zprávy (`ChatSummaryViewModel.explain`). */
    Explain(AiInput.Text),

    /** Návrhy odpovědí pod rozhovorem. */
    Replies(AiInput.Text),

    /** Přepis konceptu a kontrola jeho tónu před odesláním. */
    Rewrite(AiInput.Text),

    /** Termíny ze zpráv: připomenutí i akce u zpráv (události, schůzky). */
    Dates(AiInput.Text),

    /** Varování před podvodem. */
    Scam(AiInput.Text),

    /** Triáž chatů („čeká na odpověď"). */
    Triage(AiInput.Text),

    /** Hledání zpráv přirozeným jazykem. */
    Search(AiInput.Text),

    /** Návrh složek podle obsahu chatů (Nastavení → Složky chatů). */
    Folders(AiInput.Text),

    /** Přepis hlasovek a koleček. */
    Transcription(AiInput.Audio),

    /** Popis fotky v prohlížeči i popisy fotek předem na pozadí. */
    PhotoDescription(AiInput.Image),
}

/** Co úloha modelu předává vedle instrukcí. */
enum class AiInput { Text, Audio, Image }

/**
 * Zvládne model úlohu? Statické — co runtime UMÍ, ne jestli je zrovna připravený (to říká
 * `status`). Gemini Nano přes ML Kit Prompt API bere jen text, Apple Intelligence zatím taky
 * (zvuk a obraz by chtěly vlastní Swift most).
 */
fun AiProvider.handles(task: AiTask): Boolean = when (task.input) {
    AiInput.Text -> true
    AiInput.Audio, AiInput.Image -> this == AiProvider.Gemma
}

/**
 * Zapnuté modely, jejich volby a co čím počítat.
 *
 * Zapnutých může být víc najednou — model v paměti stojí jen tehdy, když zrovna počítá (Gemma
 * se po dvou minutách nečinnosti uvolní, Nano běží v procesu AICore). Volby modelů ([preview],
 * [fast], [gemma]) jsou tytéž jako dřív v [ModelVariant], jen se už nevybírá jediný poskytovatel.
 *
 * @property enabled zapnuté modely. Nikdy prázdné: aspoň jeden musí zůstat (Nastavení vypnutí
 *   posledního nedovolí, [supportedModels] doplní dostupný).
 * @property assigned ruční přiřazení úloh. Co tu NENÍ, je „Automaticky" ([chooseProvider]) —
 *   výchozí se neukládá, aby šlo pravidlo později zlepšit bez migrace.
 */
data class AiModels(
    val enabled: Set<AiProvider> = setOf(AiProvider.GeminiNano),
    val preview: Boolean = false,
    val fast: Boolean = false,
    val gemma: LiteRtModel = LiteRtModel.E2B,
    val qwen: LiteRtModel = LiteRtModel.Qwen3_4B,
    val assigned: Map<AiTask, AiProvider> = emptyMap(),
) {
    /** Varianta, se kterou se volá [OnDeviceAi] pro model [provider]. */
    fun variant(provider: AiProvider) =
        ModelVariant(provider = provider, preview = preview, fast = fast, gemma = gemma, qwen = qwen)

    /** Zapnuté modely, které úlohu zvládnou, v pořadí, ve kterém je bere „Automaticky". */
    fun candidates(task: AiTask): List<AiProvider> =
        AUTOMATIC_ORDER.filter { it in enabled && it.handles(task) }

    /**
     * Ruční přiřazení, které ještě platí: model je zapnutý a úlohu zvládne. Přiřazení k modelu,
     * který uživatel mezitím vypnul, se nepočítá — úloha jede automaticky, ne nikam.
     */
    fun assignment(task: AiTask): AiProvider? =
        assigned[task]?.takeIf { it in enabled && it.handles(task) }
}

/**
 * Pořadí pro „Automaticky": napřed systémový model (Nano, Apple), stahované až za ním. Systémový
 * model nic nestojí — nestahuje se, paměť drží AICore —, kdežto Gemma zabere gigabajty RAM, než
 * se po dvou minutách uvolní. Úlohu, kterou umí jen Gemma (zvuk, obraz), dostane tak jako tak.
 *
 * **Qwen před Gemmou:** umí jen text, takže kdo si ho vedle Gemmy zapne, chce ho na text — Gemma
 * pak zůstane hlasovkám a fotkám a v paměti se drží kratší dobu ([gemmaIdleUnloadMs]).
 */
private val AUTOMATIC_ORDER = listOf(AiProvider.GeminiNano, AiProvider.Apple, AiProvider.Qwen, AiProvider.Gemma)

/**
 * Kdo úlohu počítá.
 *
 * - **Ruční přiřazení platí, i když model není připravený** (Gemma se ještě stahuje): funkce pak
 *   řekne, že model chybí, a Nastavení u řádku proč. Kdyby se tiše vzal jiný model, nevěděl by
 *   uživatel, k čemu volba je.
 * - **„Automaticky" bere první PŘIPRAVENÝ model** z [AiModels.candidates], jinak první, který
 *   zařízení aspoň umí (a funkce nabídne stažení). Záloha je jen podle stavu modelu, ne podle
 *   chyby jednoho volání: `Busy` u Nano nemá kvůli jedné odpovědi načíst gigabajty Gemmy.
 * - **Bez kandidáta** (úloha zvuku a Gemma vypnutá) vrátí první zapnutý model. Ten úlohu neumí
 *   (`canTranscribe` dá `false`), takže se funkce nenabídne — volající nemusí řešit `null`.
 */
suspend fun chooseProvider(
    task: AiTask,
    models: AiModels,
    status: suspend (AiProvider) -> OnDeviceAiStatus,
): AiProvider {
    models.assignment(task)?.let { return it }
    val candidates = models.candidates(task)
    val statuses = candidates.associateWith { status(it) }
    return candidates.firstOrNull { statuses[it] == OnDeviceAiStatus.Ready }
        ?: candidates.firstOrNull { statuses[it] != OnDeviceAiStatus.Unsupported }
        ?: candidates.firstOrNull()
        ?: AUTOMATIC_ORDER.firstOrNull { it in models.enabled }
        ?: AiProvider.GeminiNano
}

/** Varianta pro [task] podle [models]: s ní funkce volá [OnDeviceAi] jako dřív. */
suspend fun OnDeviceAi.variantFor(task: AiTask, models: AiModels): ModelVariant {
    val provider = chooseProvider(task, models) { provider ->
        runCatching { status(models.variant(provider)) }.getOrDefault(OnDeviceAiStatus.Unsupported)
    }
    return models.variant(provider)
}

/** Varianta pro [task] podle uloženého nastavení, platná TEĎ (stav modelu se čte při volání). */
suspend fun OnDeviceAi.variantFor(task: AiTask, settings: AppSettings): ModelVariant =
    variantFor(task, settings.onDeviceAiModels.value)

/**
 * Varianta pro [task] jako tok: nová s každou změnou nastavení. Na rozhodnutí „nabídnout
 * funkci?" — dostahovaný model tok sám nepozná, proto se pro samotné volání bere [variantFor].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun OnDeviceAi.variantsFor(task: AiTask, settings: AppSettings): Flow<ModelVariant> =
    settings.onDeviceAiModels.mapLatest { variantFor(task, it) }
