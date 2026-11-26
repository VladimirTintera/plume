package eu.tintera.plume.core.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import eu.tintera.plume.core.adaptive.LocalWindowControls
import eu.tintera.plume.core.adaptive.WindowControls
import eu.tintera.plume.core.adaptive.WindowControlsSide
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Vlastní titulek okna přes JetBrains Runtime (`com.jetbrains.WindowDecorations`): obsah aplikace
 * sahá až k hornímu kraji okna a tlačítka okna kreslí systém přes něj — semafory na macOS,
 * minimalizace / maximalizace / zavření na Windows (i s nabídkou rozložení po najetí na
 * maximalizaci). Okno zůstává systémové, se stínem, zaoblením a přichytáváním ke krajům;
 * `undecorated` okno by o tohle všechno přišlo.
 *
 * - **Výška titulku jde do UI jako horní inset** (`statusBars`, `captionBar`, `systemBars`),
 *   stejně jako stavová lišta na telefonu, a strana a šířka tlačítek jako [LocalWindowControls].
 *   Panely inset zkonzumují (`PaneCard`) a hlavičky vyjedou nahoru; panel pod tlačítky (seznam
 *   na Macu, nejpravější panel na Windows, i jediný panel úzkého okna) nechá v hlavičce místo
 *   vedle nich. Pilulka hledání i hlavička konverzace tak leží v jednom řádku se semafory, jako
 *   ve Zprávách; sbalený seznam jen s avatary je na to úzký a jde pod ně.
 * - **Tah oknem a klik** se rozlišují v každé události myši v pruhu titulku ([hitTest]): co si
 *   prvek Compose vzal (tlačítko, pole, avatar), je obsah, zbytek titulek — tah posune oknem,
 *   dvojklik ho maximalizuje, jak to má systém nastavené.
 * - **Jen na JetBrains Runtime a jen na macOS a Windows.** Jinde (jiné JDK, Linux) zůstává
 *   systémový titulek přebarvený motivem (`WindowTitleBarAppearance`) a nic se nehlásí.
 *
 * Viz „Vlastní titulek okna (JBR)" v docs/poznatky/desktop.md.
 */
object DesktopTitleBar {

    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val mac = "mac" in osName
    private val windows = "windows" in osName

    /** Běží aplikace na JBR, které vlastní titulek umí (a na systému, kde ho chceme)? */
    val supported: Boolean by lazy {
        if (!mac && !windows) return@lazy false
        runCatching { JBR.isAvailable() && JBR.isWindowDecorationsSupported() }
            .onFailure { Logger.withTag(TAG).w(it) { "JBR API nejde zjistit, zůstává systémový titulek" } }
            .getOrDefault(false)
            .also { Logger.withTag(TAG).i { "Vlastní titulek okna: $it (JBR ${runCatching { JBR.getImplVersion() }.getOrNull()})" } }
    }

    /**
     * Náhled rozvržení pro Windows na Macu (`-Dplume.windowControlsPreview=windows`): aplikace
     * počítá s tlačítky okna vpravo jako na Windows 11, i když systém kreslí semafory vlevo.
     * Jen pro kontrolu hlaviček v pravém panelu, když Windows po ruce není.
     */
    private val windowsPreview = System.getProperty("plume.windowControlsPreview") == "windows"

    /** Tři tlačítka okna Windows 11 po 46 dp. */
    private val WINDOWS_PREVIEW_WIDTH = 138.dp

    /** Motiv aplikace: na Windows podle něj systém kreslí tlačítka okna světlá, nebo tmavá. */
    internal val dark = MutableStateFlow(false)

    /**
     * Výška titulku. Na Macu je to výška hlavičky (64 dp, hlavička konverzace i pilulka hledání),
     * takže semafory leží uprostřed řádku s hlavičkou konverzace. Na Windows jako titulek
     * aplikací pro Windows 11 a IDE od JetBrains — tlačítka okna jsou vysoká jako titulek
     * a 64 dp by z nich udělalo sloupy.
     */
    private val height: Dp = if (mac) 64.dp else 40.dp

    /**
     * Obsah okna s vlastním titulkem. Bez [supported] jen [content], beze změny.
     * [window] je hlavní okno aplikace; jiná okna (hovor, obraz v obraze) titulek nemají.
     */
    @OptIn(InternalComposeUiApi::class)
    @Composable
    fun Content(window: ComposeWindow, content: @Composable () -> Unit) {
        if (!supported) {
            content()
            return
        }
        val decorations = remember { JBR.getWindowDecorations() }
        val titleBar = remember(window) { decorations.createCustomTitleBar() }
        val isDark by dark.collectAsState()
        // Šířka tlačítek okna od kraje (dp = logické body AWT); JBR ji zná až po nastavení titulku.
        var controlsWidth by remember { mutableStateOf(0.dp) }
        LaunchedEffect(titleBar, isDark) {
            titleBar.height = height.value
            if (windows) titleBar.putProperty("controls.dark", isDark)
            decorations.setCustomTitleBar(window, titleBar)
            // Na Macu je v `leftInset` i okraj ZA semafory (stejný jako před nimi) a hlavičky mají
            // vlastní odsazení začátku; bez ubrání vycházela mezera ~36 b k pilulce a ~45 b k holé ikoně místo ~20 jako u aplikací
            // pro Mac (Zprávy, Finder). Měří se k viditelnému tvaru, tedy u holé ikony ke glyfu,
            // ne k hraně tlačítka; plocha (pilulka) si přidá `WindowControlsSpace(extra)`.
            // Změřeno ze snímku okna, viz docs/poznatky/desktop.md.
            controlsWidth = if (mac) (titleBar.leftInset - MAC_CONTROLS_TRIM).coerceAtLeast(0f).dp else titleBar.rightInset.dp
            Logger.withTag(TAG).i { "Tlačítka okna: left ${titleBar.leftInset}, right ${titleBar.rightInset}" }
        }
        // Na celé obrazovce titulek není (na Macu sjede jen s lištou nabídek), inset tedy taky ne.
        val fullscreen by DesktopWindow.fullscreen.collectAsState()
        val heightPx = with(LocalDensity.current) { if (fullscreen) 0 else height.roundToPx() }
        val base = LocalPlatformWindowInsets.current
        val insets = remember(base, heightPx) { TitleBarWindowInsets(base, heightPx) }
        CompositionLocalProvider(
            LocalPlatformWindowInsets provides insets,
            LocalWindowControls provides when {
                // Na celé obrazovce tlačítka přes obsah nejsou.
                fullscreen -> WindowControls(if (mac) WindowControlsSide.Left else WindowControlsSide.Right)
                windowsPreview -> WindowControls(WindowControlsSide.Right, WINDOWS_PREVIEW_WIDTH, height)
                mac -> WindowControls(WindowControlsSide.Left, controlsWidth, height)
                else -> WindowControls(WindowControlsSide.Right, controlsWidth, height)
            },
        ) {
            Box(Modifier.fillMaxSize().hitTest(window, titleBar, heightPx)) { content() }
        }
    }

    /**
     * Rozhodne o každé události myši, jestli patří obsahu, nebo titulku
     * (`CustomTitleBar.forceHitTest`). JBR se ptá na KAŽDOU událost zvlášť a bez odpovědi bere
     * celé plátno Compose jako obsah (má posluchače myši), takže by oknem nešlo táhnout vůbec.
     *
     * Ptá se v průchodu `Main` na kořeni: ten jde od listů ke kořeni, takže tu už je vidět, jestli
     * si událost vzal nějaký prvek pod kurzorem — tlačítka, `clickable` i pole stisk spotřebují.
     * Co si stisk vzalo, drží obsah až do puštění (tah po posuvníku přes pruh titulku nesmí
     * přejít v tah oknem). Odpověď platí pro tutéž událost, protože Compose ji na desktopu
     * zpracuje synchronně uvnitř posluchače AWT, ze kterého se JBR ptá.
     *
     * - **Na Macu JBR rozhoduje podle PŘEDCHOZÍ odpovědi.** Tah začne pohledem do pole
     *   `customTitleBarHitTest` při prvním `mouseDragged` (`AWTWindowDragView` v `AWTWindow.m`),
     *   a to na vlákně AppKitu, zatímco stisk teprve čeká ve frontě AWT. Byla-li poslední
     *   zpracovaná událost nad obsahem (právě otevřený chat zdržel frontu a pohyb do pruhu se
     *   nestihl zpracovat), tah se odmítl a stisk šel obsahu. Pohled do pole ale dělá jen
     *   pohled v pruhu titulku, takže pod pruhem na odpovědi nezáleží: na Macu se tam hlásí
     *   titulek a zastaralá odpověď tah nezablokuje. Obsah je jen to, co si v pruhu vzal prvek.
     * - **Tah oknem obsah nevidí.** Při tahu na Macu chodí pohyby myši dál i obsahu (okno se
     *   hýbe za kurzorem, ale ne přesně), takže lišta s hledáním v seznamu chatů se tahem
     *   odsunula (`enterAlways`) a zůstala schovaná — nad seznamem pak nebylo za co chytit
     *   a semafory ležely přes první chat. Po stisku v pruhu, který si nic nevzalo, proto kořen
     *   pohyby spotřebuje v průchodu `Initial`, dřív než je uvidí lišta, až do puštění.
     * - **Oknem jde táhnout i za prvky v pruhu** (tlačítka, avatar, název chatu), jako za
     *   tlačítka v nástrojové liště aplikací pro Mac: stisk bez pohybu je klik, pohyb přes práh
     *   tahu začne tah oknem a prvek o klik přijde (puštění se mu spotřebuje). Tah začíná náš
     *   most ([MacTitleBar.startDrag]), protože JBR stisk, který si vzal prvek, oknem nehne.
     *   Textové pole si tah nechá (výběr textu myší); pozná se podle kurzoru, viz [overTextField].
     *   Podle toho, jestli pohyb spotřeboval prvek, to nejde: lišta s hledáním v seznamu chatů
     *   ho spotřebuje taky (táhnout jde i za ni), a ikona hledání v ní by pak oknem nehnula.
     *   Týž most dotáhne i prázdné místo, když JBR tah přesto odmítl a okno stojí.
     */
    private fun Modifier.hitTest(window: ComposeWindow, titleBar: WindowDecorations.CustomTitleBar, heightPx: Int): Modifier =
        pointerInput(window, titleBar, heightPx) {
            // Vlastní tah oknem umí jen most na Macu; na Windows prvky v pruhu táhnout nejde.
            val ownDragAvailable = mac && MacNative.loaded
            awaitPointerEventScope {
                var inControl = false
                // Stisk na prázdném místě pruhu: táhne JBR, obsah pohyby nevidí.
                var windowDrag = false
                // Stisk na prvku v pruhu: po prahu tahu se z něj stane tah oknem.
                var controlDrag = false
                // Tah oknem začal náš most: prvek už nedostane ani puštění.
                var ownDrag = false
                var pressPosition = Offset.Zero
                var pressWindowLocation: Point? = null
                while (true) {
                    val initial = awaitPointerEvent(PointerEventPass.Initial)
                    if (windowDrag || ownDrag) {
                        initial.changes.forEach { if (it.positionChanged() || (ownDrag && it.changedToUp())) it.consume() }
                    }
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    // Kolečko a odchod z okna se podle JBR neodpovídají.
                    if (event.type == PointerEventType.Scroll || event.type == PointerEventType.Exit) continue
                    val change = event.changes.firstOrNull() ?: continue
                    val inTitle = change.position.y < heightPx
                    // Pohyby spotřebované kvůli tahu oknem si nevzal prvek.
                    val consumed = !windowDrag && !ownDrag && event.changes.any { it.isConsumed }
                    when (event.type) {
                        PointerEventType.Press -> {
                            if (consumed) {
                                inControl = true
                                controlDrag = ownDragAvailable && inTitle && event.buttons.isPrimaryPressed &&
                                    !event.overTextField()
                            } else if (inTitle) {
                                windowDrag = true
                            }
                            pressPosition = change.position
                            pressWindowLocation = window.location
                        }
                        PointerEventType.Move -> if ((controlDrag || windowDrag) && change.pressed) {
                            val beyondSlop = (change.position - pressPosition).getDistance() > viewConfiguration.touchSlop
                            when {
                                controlDrag && beyondSlop -> {
                                    controlDrag = false
                                    ownDrag = true
                                    MacTitleBar.startDrag(window)
                                }
                                // Prázdné místo a okno stojí: JBR tah odmítl, dotáhne ho most.
                                windowDrag && beyondSlop && ownDragAvailable && pressWindowLocation != null -> {
                                    if (window.location == pressWindowLocation) MacTitleBar.startDrag(window)
                                    pressWindowLocation = null
                                }
                            }
                        }
                    }
                    val control = inTitle && (consumed || inControl) && !ownDrag
                    // Na Windows pod pruhem obsah jako dřív — jak se tam JBR ptá, ověřené není.
                    titleBar.forceHitTest(control || (!mac && !inTitle))
                    if (event.type == PointerEventType.Release) {
                        inControl = false
                        windowDrag = false
                        controlDrag = false
                        ownDrag = false
                    }
                }
            }
        }

    /**
     * Je pod myší textové pole? Pole si nastavuje textový kurzor (`pointerHoverIcon`) už při
     * najetí, takže při stisku ho má komponenta okna, které událost patří. Označovat pole
     * v hlavičkách jednotlivě (hledání v konverzaci, hledání chatů, výběr chatu) by se
     * u dalšího pole zapomnělo.
     */
    private fun PointerEvent.overTextField(): Boolean =
        (nativeEvent as? MouseEvent)?.component?.cursor?.type == Cursor.TEXT_CURSOR

    private const val TAG = "TitleBar"

    /** Kolik z `leftInset` na Macu ubrat, viz `controlsWidth`. */
    private const val MAC_CONTROLS_TRIM = 24f
}

/**
 * Insety okna s pruhem titulku nahoře. Systémové lišty na desktopu nejsou, takže titulek je
 * jejich obdobou: `statusBars` (tak se ptá většina obrazovek), `captionBar` (tak mu říká
 * Android u oken s titulkem) i `systemBars`, ze kterých se skládá `safeDrawing`.
 */
@OptIn(InternalComposeUiApi::class)
private class TitleBarWindowInsets(private val base: PlatformWindowInsets, top: Int) : PlatformWindowInsets by base {
    private val bar = TopInsets(top)
    override val statusBars: PlatformInsets get() = bar
    override val captionBar: PlatformInsets get() = bar
    override val systemBars: PlatformInsets get() = bar
}

@OptIn(InternalComposeUiApi::class)
private class TopInsets(override val top: Int) : PlatformInsets {
    override val left: Int get() = 0
    override val right: Int get() = 0
    override val bottom: Int get() = 0

    override fun equals(other: Any?): Boolean = other is TopInsets && other.top == top

    override fun hashCode(): Int = top
}
