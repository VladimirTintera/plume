package eu.tintera.plume.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bricolage_grotesque_bold
import eu.tintera.plume.resources.bricolage_grotesque_semibold
import eu.tintera.plume.resources.figtree_bold
import eu.tintera.plume.resources.figtree_medium
import eu.tintera.plume.resources.figtree_regular
import eu.tintera.plume.resources.figtree_semibold
import org.jetbrains.compose.resources.Font

/**
 * Písma aplikace na škále tv-material: Figtree na všechno čtené, Bricolage Grotesque na role
 * `display*` a `headline*` — tytéž dvě rodiny a řezy jako telefon (`theme/Typography.kt` ve
 * `shared`, CLAUDE.md „Typografie"). Proložení je ladění telefonu: výchozí škála je dělaná
 * na Roboto a s Figtree je řídká.
 */
@Composable
fun tvTypography(): Typography {
    val primary = FontFamily(
        Font(Res.font.figtree_regular, FontWeight.Normal),
        Font(Res.font.figtree_medium, FontWeight.Medium),
        Font(Res.font.figtree_semibold, FontWeight.SemiBold),
        Font(Res.font.figtree_bold, FontWeight.Bold),
    )
    val display = FontFamily(
        Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
        Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
    )
    val base = Typography()

    fun TextStyle.primary(weight: FontWeight, tracking: TextUnit) =
        copy(fontFamily = primary, fontWeight = weight, letterSpacing = tracking)

    // Bricolage ukáže charakter až polotučně, v řezu Medium je z ní obyčejný grotesk.
    fun TextStyle.display(tracking: TextUnit) =
        copy(fontFamily = display, fontWeight = FontWeight.SemiBold, letterSpacing = tracking)

    return Typography(
        displayLarge = base.displayLarge.display((-0.5).sp),
        displayMedium = base.displayMedium.display((-0.3).sp),
        displaySmall = base.displaySmall.display((-0.2).sp),
        headlineLarge = base.headlineLarge.display((-0.1).sp),
        headlineMedium = base.headlineMedium.display(0.sp),
        headlineSmall = base.headlineSmall.display(0.sp),
        titleLarge = base.titleLarge.primary(FontWeight.SemiBold, 0.sp),
        titleMedium = base.titleMedium.primary(FontWeight.Medium, 0.1.sp),
        titleSmall = base.titleSmall.primary(FontWeight.Medium, 0.1.sp),
        bodyLarge = base.bodyLarge.primary(FontWeight.Normal, 0.1.sp),
        bodyMedium = base.bodyMedium.primary(FontWeight.Normal, 0.1.sp),
        bodySmall = base.bodySmall.primary(FontWeight.Normal, 0.2.sp),
        labelLarge = base.labelLarge.primary(FontWeight.Medium, 0.1.sp),
        labelMedium = base.labelMedium.primary(FontWeight.Medium, 0.2.sp),
        labelSmall = base.labelSmall.primary(FontWeight.Medium, 0.3.sp),
    )
}
