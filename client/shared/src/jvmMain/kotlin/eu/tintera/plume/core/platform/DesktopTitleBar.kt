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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
 *   Panely vedle sebe inset zkonzumují (`PaneCard`) a hlavičky vyjedou nahoru; panel pod
 *   tlačítky (seznam na Macu) nechá v hlavičce místo vedle nich. Pilulka hledání i hlavička
 *   konverzace tak leží v jednom řádku se semafory, jako ve Zprávách.
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
            // vlastní odsazení začátku; bez ubrání vycházela mezera ~36 b místo ~20 jako u aplikací
            // pro Mac (Zprávy, Finder). Změřeno ze snímku okna, viz docs/poznatky/desktop.md.
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
                windowsPreview -> WindowControls(WindowControlsSide.Right, WINDOWS_PREVIEW_WIDTH)
                mac -> WindowControls(WindowControlsSide.Left, controlsWidth)
                else -> WindowControls(WindowControlsSide.Right, controlsWidth)
            },
        ) {
            Box(Modifier.fillMaxSize().hitTest(titleBar, heightPx)) { content() }
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
     */
    private fun Modifier.hitTest(titleBar: WindowDecorations.CustomTitleBar, heightPx: Int): Modifier =
        pointerInput(titleBar, heightPx) {
            awaitPointerEventScope {
                var inControl = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    // Kolečko a odchod z okna se podle JBR neodpovídají.
                    if (event.type == PointerEventType.Scroll || event.type == PointerEventType.Exit) continue
                    val consumed = event.changes.any { it.isConsumed }
                    if (event.type == PointerEventType.Press && consumed) inControl = true
                    val y = event.changes.firstOrNull()?.position?.y ?: continue
                    val inTitle = y < heightPx
                    titleBar.forceHitTest(!inTitle || consumed || inControl)
                    if (event.type == PointerEventType.Release) inControl = false
                }
            }
        }

    private const val TAG = "TitleBar"

    /** Kolik z `leftInset` na Macu ubrat, viz `controlsWidth`. */
    private const val MAC_CONTROLS_TRIM = 16f
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
