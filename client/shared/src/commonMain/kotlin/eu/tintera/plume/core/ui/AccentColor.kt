package eu.tintera.plume.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Kontrastní poměr dvou barev podle WCAG — 1 (stejné) až 21 (černá proti bílé).
 *
 * Poloprůhledná barva se nejdřív složí na tu druhou; jinak by se počítalo s barvou, která se
 * nikde nekreslí.
 */
fun contrastRatio(foreground: Color, background: Color): Float {
    val front = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
    val a = front.luminance() + CONTRAST_OFFSET
    val b = background.luminance() + CONTRAST_OFFSET
    return if (a > b) a / b else b / a
}

/**
 * Akcentní barva, kterou je na ploše [background] ještě vidět — jinak [fallback].
 *
 * **M3 garantuje kontrast jen mezi plochou a její `on*` rolí.** Že je `tertiary` čitelná na
 * `tertiaryContainer` (jméno v citaci, název webu u náhledu odkazu, odkaz ve zvýrazněné
 * bublině), je vizuální zkratka, která ve většině palet vyjde — ale ne ve všech. V paletě se
 * zvýšeným kontrastem (Android: Usnadnění → Kontrast) jsou v tmavém režimu container role
 * SVĚTLÉ a jejich `on*` tmavé, takže se `tertiary` a `tertiaryContainer` potkají na téže
 * světlosti. Změřeno na zařízení: text odkazu #8AE9B3–#B3FFD6 na ploše #96F5BF, tedy kontrast
 * kolem 1,05 — odkaz nebyl vidět vůbec.
 *
 * Akcent se proto použije, jen když je na té konkrétní ploše čitelný; jinak spadne na barvu
 * popředí, kterou plocha nese, a odlišení nese řez písma (u odkazu podtržení, viz `RichText`).
 * Tomu, kde se akcent bere, to nechává volnou ruku: totéž pravidlo platí pro [senderColor]
 * i pro role z tématu.
 */
fun accentOn(background: Color, accent: Color, fallback: Color): Color =
    if (contrastRatio(accent, background) >= MIN_ACCENT_CONTRAST) accent else fallback

/**
 * Akcent pro DROBNOU KRESBU na ploše [background] — z [candidates] ten, který je na ní nejlíp
 * vidět.
 *
 * [accentOn] řeší jinou otázku: jestli akcent na ploše ZMIZÍ. Ikona sponky a výběru emoji u
 * vstupního pole ale nezmizela — `primary` má na `surfaceContainerHigh` kontrast kolem 5, tedy
 * nad prahem AA — a přesto vypadala rozmazaně. Změřeno na emulátoru: olivová #576417 na kartě
 * #E5E8F1 dává 5,3 : 1, zatímco text hned vedle (`onSurfaceVariant`) má 7,6 a ostatní ikony
 * v aplikaci přes 10. Obrysová ikona nese tah 2 dp a její hrana proti kartě leží skoro celá
 * v barvě, ne ve světlosti (modrý kanál 23 proti 241) — a chromatickou hranu vidí oko měkčeji
 * než luminanční. Práh „ještě čitelné" je na tohle málo; u kresby z tenkých tahů rozhoduje, jak
 * je vidět, ne jestli.
 *
 * Nevybírá se proto pevná role, ale ta z dvojice, která na té konkrétní ploše vyhraje. Pevná
 * volba by v jiné paletě spadla do stejné pasti obráceně: `onPrimaryContainer` je v paletě se
 * zvýšeným kontrastem v tmavém režimu TMAVÝ (viz [accentOn]) a na tmavé kartě by byl horší než
 * `primary`.
 */
fun strongestAccentOn(background: Color, accent: Color, alternative: Color): Color =
    if (contrastRatio(alternative, background) > contrastRatio(accent, background)) {
        alternative
    } else {
        accent
    }

/**
 * Plocha vloženého obsahu ([container]), která se od plochy pod ní ([background]) pozná — jinak
 * posunutá o kus k [toward], tedy k barvě popředí, kterou sama nese.
 *
 * Citace a popis webu leží v `tertiaryContainer` na bublině. Že se ty dvě plochy odliší, je
 * stejná nezaručená zkratka jako u [accentOn]: zvýrazněná příchozí zpráva sama přejde do
 * `tertiaryContainer` (tedy do téže barvy), a v paletě se zvýšeným kontrastem se `tertiary`
 * s `tertiaryContainer` potkají na téže světlosti, takže se karta slije v jednu plochu i u
 * zvýrazněné ODCHOZÍ zprávy. Dřív se posouvalo k `tertiary`, což je přesně ta barva, na kterou
 * se nedá spolehnout.
 *
 * Posouvá se proto k `on*` roli plochy: tu M3 garantuje jako kontrastní, takže posun funguje
 * v každé paletě a v obou režimech míří na stejnou stranu jako dřív (v tmavém zesvětluje,
 * ve světlém ztmavuje).
 */
fun surfaceApart(background: Color, container: Color, toward: Color): Color =
    if (contrastRatio(container, background) >= MIN_SURFACE_CONTRAST) {
        container
    } else {
        lerp(container, toward, SURFACE_SHIFT)
    }

/**
 * Práh čitelnosti — WCAG AA pro běžný text.
 *
 * Akcenty v aplikaci nesou drobné písmo (jméno v citaci, název webu), takže mírnější práh pro
 * velký text (3:1) by na ně byl málo. V běžných paletách (materialkolor Expressive, Material You
 * ze systému) má `tertiary` proti `tertiaryContainer` kontrast přes 5, takže se pro ně nic
 * nemění — viz `AccentColorTest`.
 */
private const val MIN_ACCENT_CONTRAST = 4.5f

/**
 * Odkdy jsou dvě plochy pod sebou k rozeznání. Text by potřeboval mnohem víc, ale tady jde jen
 * o to, aby hranice mezi citací a bublinou byla vidět — M3 sám drží sousední surface role
 * v podobném rozestupu.
 */
private const val MIN_SURFACE_CONTRAST = 1.2f

/**
 * O kolik se plocha posune, když se od té pod sebou neliší.
 *
 * Vyšlo z porovnání s dřívějším posunem o 0,22 k `tertiary`: ten dával odstup 1,51 (tmavé) a 1,35
 * (světlé), 0,18 k `on*` dává 1,49 a 1,35. Vzhled se tedy v běžné paletě nemění a text na
 * posunuté ploše zůstává nad AA (4,87 a 5,39) — viz `AccentColorTest`.
 */
private const val SURFACE_SHIFT = 0.18f

/** Posun ve vzorci WCAG, aby ani černá nedělila nulou. */
private const val CONTRAST_OFFSET = 0.05f
