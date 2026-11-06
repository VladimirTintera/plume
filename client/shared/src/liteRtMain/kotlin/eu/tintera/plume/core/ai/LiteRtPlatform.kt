package eu.tintera.plume.core.ai

import java.io.File

/**
 * Co runtime LiteRT-LM ([LiteRtOnDeviceAi]) potřebuje od platformy: kde leží modely, jestli je
 * zařízení unese, jak dostat zvuk a obraz do tvaru pro model a co drží stahování naživu. Engine,
 * stahování s navazováním a stav pro obrazovku jsou společné pro Android i desktop.
 */
interface LiteRtPlatform {

    /** Adresář stažených modelů. Soubory v něm jsou drahé, systém je mazat nemá. */
    val modelsDir: File

    /** Keš zkompilovaného modelu; tu smí systém klidně uklidit. */
    val cacheDir: File

    /** Unese zařízení [model]? Paměť, systém, nativní knihovna runtime. Levný dotaz. */
    fun canRun(model: LiteRtModel): Boolean

    /** PNG s delší stranou nejvýš [maxSide]; `null`, když soubor obrázek není. */
    fun png(path: String, maxSide: Int): ByteArray?

    /** Zvuk jako kusy WAV 16 kHz mono 16 bitů, každý nejvýš 30 s; prázdné, když v souboru zvuk není. */
    fun wavClips(path: String): List<ByteArray>

    /**
     * Spustí stahování [model], které přežije obrazovku, a vrátí se hned. [run] model stáhne
     * (`LiteRtOnDeviceAi.runDownload`); platforma ho buď pustí sama (desktop), nebo spustí něco,
     * co ho zavolá (Android: úloha WorkManageru si runtime vezme z Koinu). Běžící stahování se
     * nespouští podruhé.
     */
    fun startDownload(model: LiteRtModel, run: suspend () -> Unit)

    /**
     * Text, který jde do modelu (pokyn i přepis zpráv), upravený tak, aby ho nativní strana
     * přečetla. Výchozí beze změny.
     */
    fun modelText(text: String): String = text

    /** Zastaví stahování [model]; stažená část zůstává. */
    fun cancelDownload(model: LiteRtModel)

    /** Je stahování [model] naplánované nebo běží (i z minulého běhu procesu)? */
    suspend fun downloadPending(model: LiteRtModel): Boolean
}
