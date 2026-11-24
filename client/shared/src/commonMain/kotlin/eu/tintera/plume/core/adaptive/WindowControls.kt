package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
 * zkonzumuje a jeho hlavička vyjede až k hornímu kraji okna. Panel pod tlačítky ho zkonzumuje
 * taky a hlavička jim nechá místo ([LocalWindowControlsClearance]) — pilulka hledání i hlavička
 * konverzace tak leží v jednom řádku se semafory, i v úzkém okně s jedním panelem. Pruh titulku
 * zůstává jen obrazovkám přes celé okno mimo panely (prohlížeč, editory) a dialogům.
 *
 * [None] = tlačítka přes obsah nejsou (telefon, systémový titulek); horní inset pak patří všem.
 */
enum class WindowControlsSide { None, Left, Right }

/** Tlačítka okna přes obsah: strana, šířka od kraje okna a výška pruhu titulku. */
@Immutable
data class WindowControls(
    val side: WindowControlsSide = WindowControlsSide.None,
    val width: Dp = 0.dp,
    val height: Dp = 0.dp,
)

val LocalWindowControls = staticCompositionLocalOf { WindowControls() }

/**
 * Kolik místa má hlavička obrazovky nechat pro tlačítka okna na začátku a na konci řádku.
 * Nenulové jen v panelu pod tlačítky, když panely stojí vedle sebe: všechny pak inset titulku
 * zkonzumují a hlavičky vyjedou do řádku s tlačítky ([PaneCard]). Na Macu je to začátek
 * panelu seznamu (semafory), na Windows konec nejpravějšího panelu (minimalizace / zavření).
 * Všude jinde nula a [WindowControlsSpace] / [WindowControlsEndSpace] nedělají nic.
 */
@Immutable
data class WindowControlsClearance(val start: Dp = 0.dp, val end: Dp = 0.dp, val height: Dp = 0.dp)

val LocalWindowControlsClearance = compositionLocalOf { WindowControlsClearance() }

/**
 * Navigační ikona horní lišty s místem pro tlačítka okna před sebou (semafory na Macu nad
 * panelem seznamu): patří do slotu `navigationIcon` každé horní lišty, u vlastní hlavičky na
 * začátek jejího řádku.
 * Ve slotu, ne v `windowInsets` lišty: u `LargeFlexibleTopAppBar` by inset odsunul i velký
 * nadpis pod řádkem se semafory. Prázdná ikona (`{}`) dostane místo taky — nadpis malé lišty by
 * jinak začínal pod semafory. Každá horní lišta ho má, i když dnes v levém panelu nestojí
 * (`verifyWindowControls`): obrazovka se do jiného panelu přestěhuje snadno, kontrola ne.
 */
@Composable
fun WindowControlsSpace(
    /**
     * Místo navíc za tlačítky okna, jen když tam jsou. Šířka je laděná na holou ikonu, jejíž
     * glyf má v tlačítku vzduch; plocha, která začíná hned na kraji (pilulka hledání), by
     * k semaforům přilehla — ta si přidá [FilledShapeGap].
     */
    extra: Dp = 0.dp,
    navigationIcon: @Composable () -> Unit = {},
) {
    val clearance = LocalWindowControlsClearance.current.start
    if (clearance == 0.dp) {
        navigationIcon()
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(clearance + extra))
            navigationIcon()
        }
    }
}

/**
 * O kolik dál od tlačítek okna stojí plocha (pilulka) než holá ikona: glyf 24 dp má ve 40dp
 * tlačítku 8 dp vzduchu, pilulka žádný. Bez toho by pilulka za semafory stála blíž než ikona.
 */
val FilledShapeGap = 8.dp

/**
 * Místo pro tlačítka okna na konci řádku akcí horní lišty (Windows: minimalizace, maximalizace,
 * zavření nad nejpravějším panelem). Patří na KONEC slotu `actions` každé horní lišty
 * (`verifyWindowControls`); mimo panel pod tlačítky nezabere nic.
 */
@Composable
fun RowScope.WindowControlsEndSpace() {
    val clearance = LocalWindowControlsClearance.current.end
    if (clearance > 0.dp) Spacer(Modifier.width(clearance))
}

/**
 * Obsah příliš úzký na to, aby stál VEDLE tlačítek okna (sbalený seznam chatů jen s avatary):
 * odsadí se POD ně, o výšku titulku. Mimo panel pod tlačítky nedělá nic.
 */
@Composable
fun Modifier.belowWindowControls(): Modifier = padding(top = LocalWindowControlsClearance.current.height)

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
