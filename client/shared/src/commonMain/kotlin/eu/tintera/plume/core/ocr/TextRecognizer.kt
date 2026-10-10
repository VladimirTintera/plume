package eu.tintera.plume.core.ocr

/**
 * Rozpoznání textu ve fotce, celé v zařízení: ML Kit z Play služeb na Androidu, Vision na iOS.
 * Obrázek ani text nikam neodchází.
 *
 * Jediné místo v projektu, kde smí být knihovna na rozpoznávání textu; zbytek aplikace vidí jen
 * [RecognizedText], stejně jako u `core/ai` a TDLib.
 */
interface TextRecognizer {
    /**
     * Text v obrázku [path]. Prázdný výsledek = v obrázku žádný text není; `null` = rozpoznání
     * selhalo (model se ještě stahuje, soubor nejde přečíst) — to se hlásí jinou větou.
     */
    suspend fun recognize(path: String): RecognizedText?
}

/**
 * Blok textu a kde v obrázku leží. Souřadnice jsou PODÍLY rozměrů obrázku (0–1, počátek vlevo
 * nahoře), takže se kreslí stejně přes fotku jakékoli velikosti i přiblížení.
 */
data class RecognizedBlock(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class RecognizedText(val blocks: List<RecognizedBlock>) {
    /**
     * Celý text v pořadí čtení — to jde do schránky. Bloky na jednom řádku (položka a cena
     * na účtence) spojí mezera, řádky zalomení. Prázdný řádek mezi každým blokem rozsypal účtenku
     * na dvakrát tolik odstavců, co měla řádků.
     */
    val text: String get() = readingRows(blocks).joinToString("\n") { row -> row.joinToString(" ") { it.text } }

    val isEmpty: Boolean get() = blocks.isEmpty()
}

/** Bloky v pořadí čtení: po řádcích shora dolů, v řádku zleva doprava (viz [readingRows]). */
fun inReadingOrder(blocks: List<RecognizedBlock>): List<RecognizedBlock> = readingRows(blocks).flatten()

/**
 * Bloky po řádcích shora dolů, v řádku zleva doprava.
 *
 * ML Kit bloky v pořadí čtení vrací, Vision ale ne (jeho řádky chodí, jak je model najde). Řádek
 * jsou bloky, jejichž svislý střed leží v rozsahu prvního bloku řádku — srovnání jen podle horní
 * hrany by dva bloky vedle sebe s pár body rozdílu přehodilo.
 */
fun readingRows(blocks: List<RecognizedBlock>): List<List<RecognizedBlock>> {
    val rows = mutableListOf<MutableList<RecognizedBlock>>()
    for (block in blocks.sortedBy { it.top }) {
        val center = (block.top + block.bottom) / 2
        val row = rows.lastOrNull()?.takeIf { row -> center in row.first().top..row.first().bottom }
        if (row != null) row += block else rows += mutableListOf(block)
    }
    return rows.map { row -> row.sortedBy { it.left } }
}
