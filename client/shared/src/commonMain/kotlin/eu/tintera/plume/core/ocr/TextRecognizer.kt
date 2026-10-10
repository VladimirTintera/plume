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
 * Řádek textu a kde v obrázku leží. Souřadnice jsou PODÍLY rozměrů obrázku (0–1, počátek vlevo
 * nahoře), takže se kreslí stejně přes fotku jakékoli velikosti i přiblížení.
 *
 * Řádek, ne blok: řádek je nejmenší kus, který jde na fotce vybrat. Blok z ML Kitu je celý
 * odstavec — na účtence by jedno ťuknutí vybralo pět položek i s cenami.
 */
data class RecognizedLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Rozpoznaný text; [lines] jsou v pořadí čtení a výběr na ně odkazuje indexem. */
data class RecognizedText(val lines: List<RecognizedLine>) {
    /** Celý text v pořadí čtení — to jde do schránky (viz [joinInReadingOrder]). */
    val text: String get() = joinInReadingOrder(lines)

    /** Text vybraných řádků (indexy do [lines]); nic vybraného = celý text. */
    fun text(selected: Set<Int>): String =
        if (selected.isEmpty()) text else joinInReadingOrder(lines.filterIndexed { index, _ -> index in selected })

    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Řádky poskládané do textu: řádky vedle sebe (položka a cena na účtence) spojí mezera, řádky
 * pod sebou zalomení. Prázdný řádek mezi každým kusem rozsypal účtenku na dvakrát tolik
 * odstavců, co měla řádků.
 */
private fun joinInReadingOrder(lines: List<RecognizedLine>): String =
    readingRows(lines).joinToString("\n") { row -> row.joinToString(" ") { it.text } }

/** Řádky v pořadí čtení: shora dolů, vedle sebe zleva doprava (viz [readingRows]). */
fun inReadingOrder(lines: List<RecognizedLine>): List<RecognizedLine> = readingRows(lines).flatten()

/**
 * Řádky seskupené podle výšky shora dolů, v každé skupině zleva doprava.
 *
 * ML Kit bloky v pořadí čtení vrací, Vision ale ne (jeho řádky chodí, jak je model najde),
 * a řádky z různých bloků ML Kitu (položka vlevo, cena vpravo) leží v různých blocích. Do
 * skupiny patří řádky, jejichž svislý střed leží v rozsahu prvního řádku skupiny — srovnání jen
 * podle horní hrany by dva řádky vedle sebe s pár body rozdílu přehodilo.
 */
fun readingRows(lines: List<RecognizedLine>): List<List<RecognizedLine>> {
    val rows = mutableListOf<MutableList<RecognizedLine>>()
    for (line in lines.sortedBy { it.top }) {
        val center = (line.top + line.bottom) / 2
        val row = rows.lastOrNull()?.takeIf { row -> center in row.first().top..row.first().bottom }
        if (row != null) row += line else rows += mutableListOf(line)
    }
    return rows.map { row -> row.sortedBy { it.left } }
}
