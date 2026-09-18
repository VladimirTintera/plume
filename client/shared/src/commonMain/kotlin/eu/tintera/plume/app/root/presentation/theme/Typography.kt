package eu.tintera.plume.app.root.presentation.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bricolage_grotesque_bold
import eu.tintera.plume.resources.bricolage_grotesque_semibold
import eu.tintera.plume.resources.figtree_bold
import eu.tintera.plume.resources.figtree_medium
import eu.tintera.plume.resources.figtree_regular
import eu.tintera.plume.resources.figtree_semibold

/*
 * Dvě rodiny, každá se svou rolí.
 *
 * - Figtree nese všechno čtené: zprávy, seznam, nabídky, popisky. Přátelský geometrický grotesk,
 *   čitelný i v malých velikostech.
 * - Bricolage Grotesque jen display a headline — velké nadpisy obrazovek, přihlášení. Kde je jí
 *   víc, přestává být výrazná.
 *
 * Rozdělení je v rolích škály, ne v obrazovkách: obrazovka si vybere roli a písmo dostane s ní.
 * Výměna dvojice je proto jen tenhle soubor a soubory v `composeResources/font`.
 */

@Composable
private fun primaryFamily() = FontFamily(
    Font(Res.font.figtree_regular, FontWeight.Normal),
    Font(Res.font.figtree_medium, FontWeight.Medium),
    Font(Res.font.figtree_semibold, FontWeight.SemiBold),
    Font(Res.font.figtree_bold, FontWeight.Bold),
)

@Composable
private fun displayFamily() = FontFamily(
    Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun appTypography(): Typography {
    val primary = primaryFamily()
    val display = displayFamily()
    return remember(primary, display) { buildTypography(primary, display) }
}

/**
 * Škála vychází z výchozí M3 (velikosti a výšky řádků drží), mění se písmo, řez a proložení.
 *
 * **Proložení výchozí škály je laděné na Roboto** — `bodyLarge` má +0,5 sp, což je s Figtree
 * řídké. Text jde proto skoro bez proložení, velké nadpisy mírně do minusu: čím větší písmo, tím
 * víc vzduchu mezi znaky působí jako díra.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun buildTypography(primary: FontFamily, display: FontFamily): Typography {
    val base = Typography()

    fun TextStyle.primary(weight: FontWeight, tracking: TextUnit, lineHeight: TextUnit = this.lineHeight) =
        copy(fontFamily = primary, fontWeight = weight, letterSpacing = tracking, lineHeight = lineHeight)

    fun TextStyle.display(weight: FontWeight, tracking: TextUnit) =
        copy(fontFamily = display, fontWeight = weight, letterSpacing = tracking)

    // Bricolage s `opsz=96` má těsné proložení už sama od sebe, takže do minusu jde jen lehce —
    // s -0,5 sp u 36 sp se písmena v „General" dotýkala.
    // Polotučně i ve velkém: Bricolage ukáže charakter (zúžené tahy, zářezy v ohybech) až
    // s dostatečnou tloušťkou tahu. V řezu Medium z ní byl obyčejný grotesk a nadpis „Chaty"
    // splýval se vším pod ním.
    val displayLarge = base.displayLarge.display(FontWeight.SemiBold, (-0.5).sp)
    val displayMedium = base.displayMedium.display(FontWeight.SemiBold, (-0.3).sp)
    val displaySmall = base.displaySmall.display(FontWeight.SemiBold, (-0.2).sp)
    val headlineLarge = base.headlineLarge.display(FontWeight.SemiBold, (-0.1).sp)
    val headlineMedium = base.headlineMedium.display(FontWeight.SemiBold, 0.sp)
    val headlineSmall = base.headlineSmall.display(FontWeight.SemiBold, 0.sp)

    // Jen `titleLarge` je polotučný — nese název obrazovky a chatu. Menší tituly zůstávají na
    // Medium z výchozí škály, jsou jich řádky (položky sheetů, nadpisy skupin).
    // `titleLarge` je název chatu v hlavičce konverzace a sbalený nadpis obrazovky. Zůstává
    // v primární rodině: název letí ze seznamu chatů (`sharedBounds`) a změna písma by uprostřed
    // letu skočila.
    val titleLarge = base.titleLarge.primary(FontWeight.SemiBold, 0.sp)
    val titleMedium = base.titleMedium.primary(FontWeight.Medium, 0.1.sp)
    val titleSmall = base.titleSmall.primary(FontWeight.Medium, 0.1.sp)

    // Text zpráv: 16/22 místo 16/24. Bublin je na obrazovce hodně a o řádek hustší sazba je znát,
    // přitom Figtree s velkou střední výškou čitelnost neztrácí.
    val bodyLarge = base.bodyLarge.primary(FontWeight.Normal, 0.1.sp, lineHeight = 22.sp)
    val bodyMedium = base.bodyMedium.primary(FontWeight.Normal, 0.1.sp)
    val bodySmall = base.bodySmall.primary(FontWeight.Normal, 0.2.sp)

    val labelLarge = base.labelLarge.primary(FontWeight.Medium, 0.1.sp)
    val labelMedium = base.labelMedium.primary(FontWeight.Medium, 0.2.sp)
    val labelSmall = base.labelSmall.primary(FontWeight.Medium, 0.3.sp)

    // Zvýrazněné varianty (M3 Expressive) jsou tatáž role o stupeň tučnější — na to „důležité"
    // uvnitř běžného obsahu. Rodina se nemění, jinak by zvýraznění vypadalo jako cizí prvek.
    fun TextStyle.emphasized(weight: FontWeight) = copy(fontWeight = weight)

    return Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
        displayLargeEmphasized = displayLarge.emphasized(FontWeight.Bold),
        displayMediumEmphasized = displayMedium.emphasized(FontWeight.Bold),
        displaySmallEmphasized = displaySmall.emphasized(FontWeight.Bold),
        headlineLargeEmphasized = headlineLarge.emphasized(FontWeight.Bold),
        headlineMediumEmphasized = headlineMedium.emphasized(FontWeight.Bold),
        headlineSmallEmphasized = headlineSmall.emphasized(FontWeight.Bold),
        titleLargeEmphasized = titleLarge.emphasized(FontWeight.Bold),
        titleMediumEmphasized = titleMedium.emphasized(FontWeight.SemiBold),
        titleSmallEmphasized = titleSmall.emphasized(FontWeight.SemiBold),
        bodyLargeEmphasized = bodyLarge.emphasized(FontWeight.Medium),
        bodyMediumEmphasized = bodyMedium.emphasized(FontWeight.Medium),
        bodySmallEmphasized = bodySmall.emphasized(FontWeight.Medium),
        labelLargeEmphasized = labelLarge.emphasized(FontWeight.SemiBold),
        labelMediumEmphasized = labelMedium.emphasized(FontWeight.SemiBold),
        labelSmallEmphasized = labelSmall.emphasized(FontWeight.SemiBold),
    )
}

