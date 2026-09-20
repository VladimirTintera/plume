package eu.tintera.plume.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
 * Práh čitelnosti — WCAG AA pro běžný text.
 *
 * Akcenty v aplikaci nesou drobné písmo (jméno v citaci, název webu), takže mírnější práh pro
 * velký text (3:1) by na ně byl málo. V běžných paletách (materialkolor Expressive, Material You
 * ze systému) má `tertiary` proti `tertiaryContainer` kontrast přes 5, takže se pro ně nic
 * nemění — viz `AccentColorTest`.
 */
private const val MIN_ACCENT_CONTRAST = 4.5f

/** Posun ve vzorci WCAG, aby ani černá nedělila nulou. */
private const val CONTRAST_OFFSET = 0.05f
