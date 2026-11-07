package eu.tintera.plume.core.ai

import eu.tintera.ai.core.DeviceLoad
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.avoidUnderLoad
import eu.tintera.plume.core.settings.AppSettings

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
