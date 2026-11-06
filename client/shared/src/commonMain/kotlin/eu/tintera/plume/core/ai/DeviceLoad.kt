package eu.tintera.plume.core.ai

import eu.tintera.plume.core.settings.AppSettings

/**
 * Je telefon zrovna pod tlakem — úsporný režim, nebo přehřátí? Pak Gemma SAMA OD SEBE nic
 * nepočítá ([proactiveVariant]): načtení modelu bere gigabajty paměti a desítky vteřin procesoru
 * (GPU), a to je přesně to, co úsporný režim i přehřátý telefon chtějí omezit.
 *
 * Čte se při volání, ne jako tok: proaktivní úloha se ptá těsně před tím, než by model pustila,
 * a když zrovna nejde, zkusí to s další změnou (nová zpráva, otevřený chat) jako u nepřipraveného
 * modelu.
 */
interface DeviceLoad {
    val strained: Boolean
}

/** Platforma bez Gemmy (iOS, desktop) a testy: zátěž se neřeší. */
object NoDeviceLoad : DeviceLoad {
    override val strained: Boolean = false
}

/**
 * Varianta pro úlohu, která běží SAMA (návrhy odpovědí, varování před podvodem, triáž, termíny
 * ve zprávách, popisy fotek na pozadí), nebo `null`, když ji teď pouštět nemá.
 *
 * - **Pod zátěží jen bez Gemmy** ([avoidUnderLoad]) — i ručně přiřazené. Úsporný režim je taky
 *   volba uživatele, a o úlohu, kterou si nevyžádal ťuknutím, nepřijde: projeví se s další
 *   změnou, až zátěž pomine.
 * - **Úlohy na ťuknutí** (shrnutí, vysvětlení, přepis hlasovky, přepis konceptu, hledání, popis
 *   fotky v prohlížeči) jdou dál přes [variantFor]: o výsledek si uživatel právě řekl.
 * - **Gemini Nano a Apple jedou i pod zátěží** — počítá je systém (AICore, Foundation Models),
 *   který si úsporu řídí sám, a aplikace kvůli nim nic nenačítá.
 */
suspend fun OnDeviceAi.proactiveVariant(task: AiTask, settings: AppSettings, load: DeviceLoad): ModelVariant? =
    avoidUnderLoad(variantFor(task, settings), load.strained)

/** [variant], pokud ji zátěž nevylučuje; Gemma nebo Qwen pod zátěží ([strained]) = `null`. */
fun avoidUnderLoad(variant: ModelVariant, strained: Boolean): ModelVariant? =
    variant.takeUnless { strained && it.appManaged }
