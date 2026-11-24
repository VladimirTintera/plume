package eu.tintera.plume.core.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Kde v okně leží tlačítka okna, která systém kreslí PŘES obsah aplikace: semafory na macOS
 * vlevo, minimalizace / maximalizace / zavření na Windows vpravo. Strana je fyzická (vlevo /
 * vpravo), ne logická — tlačítka se s jazykem aplikace nestěhují.
 *
 * Na desktopu s vlastním titulkem (JetBrains Runtime, `DesktopTitleBar` v `jvmMain`) se výška
 * titulku hlásí jako horní inset (`WindowInsets.statusBars`), stejně jako stavová lišta na
 * telefonu. Na rozdíl od ní ale titulek nepotřebuje celou šířku okna: [PaneCard] podle téhle
 * strany pozná, jestli tlačítka leží nad jeho panelem. Panel, nad kterým nejsou, inset
 * zkonzumuje a jeho hlavička vyjede až k hornímu kraji okna — konverzace vedle seznamu tak má
 * hlavičku v jedné řadě se semafory, jako Zprávy nebo Mail.
 *
 * [None] = tlačítka přes obsah nejsou (telefon, systémový titulek); horní inset pak patří všem.
 */
enum class WindowControlsSide { None, Left, Right }

val LocalWindowControlsSide = staticCompositionLocalOf { WindowControlsSide.None }

/**
 * Leží tlačítka okna nad panelem, jehož okraje jsou [atStart] (začátek řádku) a [atEnd] (konec)?
 * Bez tlačítek přes obsah ([WindowControlsSide.None]) vždycky ano: horní inset (stavová lišta)
 * jde přes celou šířku.
 */
@Composable
fun windowControlsAbove(atStart: Boolean, atEnd: Boolean): Boolean {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return when (LocalWindowControlsSide.current) {
        WindowControlsSide.None -> true
        WindowControlsSide.Left -> if (rtl) atEnd else atStart
        WindowControlsSide.Right -> if (rtl) atStart else atEnd
    }
}
