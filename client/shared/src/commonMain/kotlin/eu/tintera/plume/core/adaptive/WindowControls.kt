package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Kde v okně leží tlačítka okna, která systém kreslí PŘES obsah aplikace: semafory na macOS
 * vlevo, minimalizace / maximalizace / zavření na Windows vpravo. Strana je fyzická (vlevo /
 * vpravo), ne logická — tlačítka se s jazykem aplikace nestěhují.
 *
 * Na desktopu s vlastním titulkem (JetBrains Runtime, `DesktopTitleBar` v `jvmMain`) se výška
 * titulku hlásí jako horní inset (`WindowInsets.statusBars`), stejně jako stavová lišta na
 * telefonu. Na rozdíl od ní ale titulek nepotřebuje celou šířku okna: [PaneCard] podle téhle
 * strany pozná, jestli tlačítka leží nad jeho panelem. Panel, nad kterým nejsou, inset
 * zkonzumuje a jeho hlavička vyjede až k hornímu kraji okna. Panel seznamu vedle dalších panelů
 * ho zkonzumuje i pod semafory a hlavička jim nechá místo ([LocalWindowControlsClearance]) —
 * pilulka hledání i hlavička konverzace tak leží v jednom řádku se semafory. Úzké okno s jedním
 * panelem má pruh titulku přes celou šířku.
 *
 * [None] = tlačítka přes obsah nejsou (telefon, systémový titulek); horní inset pak patří všem.
 */
enum class WindowControlsSide { None, Left, Right }

/** Tlačítka okna přes obsah: strana a šířka od kraje okna, kterou zabírají. */
@Immutable
data class WindowControls(val side: WindowControlsSide = WindowControlsSide.None, val width: Dp = 0.dp)

val LocalWindowControls = staticCompositionLocalOf { WindowControls() }

/**
 * Kolik místa má hlavička obrazovky nechat na začátku řádku pro tlačítka okna. Nenulové jen
 * v panelu seznamu, který vedle dalších panelů inset titulku zkonzumoval a hlavičku vytáhl do
 * řádku se semafory ([PaneCard]); všude jinde nula a [WindowControlsSpace] nedělá nic.
 */
val LocalWindowControlsClearance = compositionLocalOf { 0.dp }

/**
 * Navigační ikona horní lišty s místem pro tlačítka okna před sebou: patří do slotu
 * `navigationIcon` lišt, které mohou stát v levém panelu (seznam chatů, archiv, Nastavení…).
 * Ve slotu, ne v `windowInsets` lišty: u `LargeFlexibleTopAppBar` by inset odsunul i velký
 * nadpis pod řádkem se semafory. Prázdná ikona (`{}`) dostane místo taky — nadpis malé lišty by
 * jinak začínal pod semafory.
 */
@Composable
fun WindowControlsSpace(navigationIcon: @Composable () -> Unit = {}) {
    val clearance = LocalWindowControlsClearance.current
    if (clearance == 0.dp) {
        navigationIcon()
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(clearance))
            navigationIcon()
        }
    }
}

/**
 * Leží tlačítka okna nad panelem, jehož okraje jsou [atStart] (začátek řádku) a [atEnd] (konec)?
 * Bez tlačítek přes obsah ([WindowControlsSide.None]) vždycky ano: horní inset (stavová lišta)
 * jde přes celou šířku.
 */
@Composable
fun windowControlsAbove(atStart: Boolean, atEnd: Boolean): Boolean {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return when (LocalWindowControls.current.side) {
        WindowControlsSide.None -> true
        WindowControlsSide.Left -> if (rtl) atEnd else atStart
        WindowControlsSide.Right -> if (rtl) atStart else atEnd
    }
}
