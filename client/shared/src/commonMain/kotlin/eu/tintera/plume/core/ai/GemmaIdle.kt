package eu.tintera.plume.core.ai

import eu.tintera.ai.core.AiProvider
import eu.tintera.ai.core.OnDeviceAi

/**
 * Jak dlouho držet engine Gemmy v paměti po textové úloze. Návrhy odpovědí, triáž a podvod
 * chodí po sobě (každá nová zpráva), a načtení trvá i deset vteřin — uvolnit hned by znamenalo
 * načítat pořád dokola.
 */
const val GEMMA_IDLE_TEXT_MS = 120_000L

/**
 * Jak dlouho ho držet, když Gemma dělá JEN zvuk a obraz (přepis hlasovky, popis fotky) a text
 * počítá jiný model. Ty se pouštějí ťuknutím, ne s každou zprávou; 30 s pokryje pár hlasovek
 * přepsaných za sebou (na emulátoru ~11 s na jednu i s načtením) a gigabajty paměti pak
 * nevisí další minutu a půl jen kvůli tomu, že se možná ťukne znovu.
 */
const val GEMMA_IDLE_MEDIA_MS = 30_000L

/**
 * Počítá Gemma nějakou TEXTOVOU úlohu ([AiInput.Text])? Rozhoduje totéž co funkce samy
 * ([variantFor]: ruční přiřazení, jinak první připravený model), takže Gemma, která text
 * převzala, protože se Nano teprve stahuje, se počítá jako textová.
 */
suspend fun OnDeviceAi.gemmaServesText(models: AiModels): Boolean =
    AiProvider.Gemma in models.enabled &&
        AiTask.entries.filter { it.input == AiInput.Text }.any { variantFor(it, models).provider == AiProvider.Gemma }

/** Prodleva před uvolněním enginu Gemmy podle toho, jestli počítá text ([gemmaServesText]). */
suspend fun OnDeviceAi.gemmaIdleUnloadMs(models: AiModels): Long =
    if (gemmaServesText(models)) GEMMA_IDLE_TEXT_MS else GEMMA_IDLE_MEDIA_MS
