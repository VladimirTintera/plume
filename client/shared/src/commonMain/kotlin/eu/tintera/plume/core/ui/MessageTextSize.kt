package eu.tintera.plume.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.settings.DEFAULT_MESSAGE_TEXT_SIZE

/**
 * Velikost písma zpráv z Nastavení → Vzhled (sp). Dodává ho kořen aplikace; výchozí hodnota platí
 * pro náhledy a obrazovky mimo něj.
 *
 * Statický: mění se jen v Nastavení, a pak má překreslit všechny zprávy.
 */
val LocalMessageTextSize = staticCompositionLocalOf { DEFAULT_MESSAGE_TEXT_SIZE }

/**
 * Písmo textu zpráv: `bodyLarge` zvětšený na [LocalMessageTextSize].
 *
 * Výška řádku jde ve STEJNÉM poměru (16/22), ne pevná — větší písmo s výškou řádku pro 16 sp by
 * se v řádcích dotýkalo, menší by mělo řádky daleko od sebe. Systémové zvětšení písma (sp) se
 * uplatní navíc, nastavení ho nenahrazuje.
 */
@Composable
@ReadOnlyComposable
fun messageTextStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyLarge
    val size = LocalMessageTextSize.current
    if (size == DEFAULT_MESSAGE_TEXT_SIZE) return base
    val scale = size.toFloat() / DEFAULT_MESSAGE_TEXT_SIZE
    return base.copy(
        fontSize = size.sp,
        lineHeight = base.lineHeight * scale,
    )
}
