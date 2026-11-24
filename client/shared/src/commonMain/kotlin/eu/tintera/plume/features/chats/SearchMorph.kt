package eu.tintera.plume.features.chats

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * Přechod mezi pilulkou v hlavičce seznamu chatů a polem hledání (`ChatSearchScreen`,
 * `docs/navrh-hledani.md`, krok 4).
 *
 * **Proč ručně, ne sdíleným prvkem:** na široké ploše se seznam chatů a hledání střídají v témže
 * panelu (`ThreePaneScaffoldScene` kreslí panel přes `AnimatedPane`), a výměnu položky v panelu
 * knihovna neanimuje a navigační `AnimatedVisibilityScope` tam není — sdílený prvek nemá z čeho
 * běžet. Na telefonu by šel, ale dva různé přechody pro tutéž věc by se rozcházely. Navíc sdílený
 * prvek bere cíl z rozložení „napřed" a v panelu s táhlem už jednou letěl jinam (viz „Mezi plným
 * seznamem a sbaleným sloupcem se přelétá" v `docs/poznatky/navigace.md`).
 *
 * Každá strana proto animuje svůj příchod sama: pole hledání začne v poloze a šířce pilulky
 * ([pill]) a roztáhne se, výsledky se prolnou; zavření šipkou nebo Esc pole napřed stáhne zpět
 * do pilulky a seznam chatů se pak objeví ([returning]). Odcházející strana zmizí hned — jako
 * první polovina fade through.
 */
@Stable
internal class SearchMorph {
    /** Kde naposledy stála pilulka (souřadnice kořene); `null`, dokud se nerozložila. */
    var pill: Rect? = null

    /** Hledání se zavřelo vlastním přechodem; seznam chatů má svůj obsah prolnout, ne ukázat. */
    private var returning = false

    fun markReturning() {
        returning = true
    }

    /** Vrací se seznam chatů z hledání? Platí jednou — přečtení příznak smaže. */
    fun consumeReturning(): Boolean = returning.also { returning = false }
}

/** Dává ho shell (jeden na okno); mimo shell `null` a přechod se neděje. */
internal val LocalSearchMorph = staticCompositionLocalOf<SearchMorph?> { null }
