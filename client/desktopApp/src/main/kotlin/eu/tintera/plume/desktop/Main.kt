package eu.tintera.plume.desktop

import androidx.compose.runtime.DisposableEffect
import eu.tintera.plume.core.locale.DesktopAppLanguage
import eu.tintera.plume.core.logging.DesktopLogs
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import eu.tintera.plume.core.platform.SavedWindowBounds
import eu.tintera.plume.core.platform.DesktopWindowBounds
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import co.touchlab.kermit.Logger
import java.lang.management.ManagementFactory
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import eu.tintera.plume.App
import eu.tintera.plume.core.platform.DesktopDevMark
import eu.tintera.plume.core.platform.DesktopInstance
import eu.tintera.plume.core.platform.DesktopStartup
import eu.tintera.plume.core.platform.DesktopTitleBar
import eu.tintera.plume.core.platform.DesktopTrayIcon
import eu.tintera.plume.core.platform.DesktopWindow
import eu.tintera.plume.core.platform.LaunchAtLogin
import eu.tintera.plume.core.platform.desktopTrainingRun
import eu.tintera.plume.core.platform.desktopClosesToTray
import eu.tintera.plume.core.ui.DesktopImageLoader
import eu.tintera.plume.core.ui.rememberDesktopShortcutHandler
import eu.tintera.plume.core.ui.DesktopMenuBar
import eu.tintera.plume.core.accounts.CallAccountScope
import eu.tintera.plume.di.startKoin
import eu.tintera.plume.core.video.DesktopPictureInPictureWindow
import eu.tintera.plume.features.calls.IncomingCallWindow
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import java.awt.Desktop
import java.awt.desktop.AppReopenedListener
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.imageio.ImageIO

/**
 * Vstupní bod desktopové aplikace. Stejně tenký jako `:client:androidApp`: všechno ostatní je
 * v `:client:shared`.
 *
 * Pořadí je povinné: napřed jediná instance (druhá by narazila na zamčenou databázi TDLib),
 * teprve pak `startKoin`, který TDLib hned spouští.
 */
fun main(args: Array<String>) {
    // JavaCPP (FFmpeg pro zvuk a video) odmítne alokovat, když FYZICKÁ paměť celého procesu
    // přesáhne dvojnásobek haldy — a do ní se počítá i načtený model v zařízení (Gemma ~3 GB,
    // mimo haldu). Na počítači s 8 GB by po prvním shrnutí přestaly hrát hlasovky i video
    // (`OutOfMemoryError: Physical memory usage is too high`). Musí být před první třídou JavaCPP.
    System.setProperty("org.bytedeco.javacpp.maxPhysicalBytes", "0")
    // Plynulá změna velikosti okna (Compose 1.13, Skiko): bez toho Metal na macOS a Direct3D
    // na Windows při tahu za okraj dokreslují obsah opožděně a okno „plave". Plume při zúžení
    // přepíná jeden a dva panely, takže je to vidět. Výchozí to má být až v pozdější verzi;
    // vlastnost z příkazové řádky (`-D…=false`) má přednost. Musí být před vznikem okna.
    for (property in LIVE_RESIZE_PROPERTIES) {
        if (System.getProperty(property) == null) System.setProperty(property, "true")
    }
    // Tréninkový běh při balení (AOT cache): vlastní dočasná data, sám skončí (TrainingRun).
    if (desktopTrainingRun) TrainingRun.prepare()
    // Plume už běží (třeba schovaná v oznamovací oblasti): ta vytáhne okno, tahle končí.
    if (!DesktopInstance.acquire()) return
    // Log, záznamy o pádech a log TDLib do souborů (O aplikaci → Záznamy aplikace). Před
    // `startKoin`: zachytí i pád při startu a log TDLib přesměruje dřív, než vznikne klient.
    DesktopLogs.install()
    // Jazyk aplikace, jestli si ho uživatel zvolil — dřív, než se přečte první text.
    DesktopAppLanguage.applyAtStartup()
    // macOS: titulek podle systémového vzhledu do chvíle, než ho `WindowTitleBarAppearance`
    // přebarví podle motivu aplikace (jinak by do té doby svítil bílý). Musí být před vznikem okna.
    setMacOsAdaptiveTitleBar()
    startKoin()
    // Obrázky zmenšované s filtrováním; výchozí dekodér Coilu na Skie bere nejbližší pixel.
    DesktopImageLoader.install()
    // Spouštění po přihlášení míří na spouštěč; po přeinstalaci jinam se cesta opraví.
    // Tréninkový běh ne — zapsal by do registru cestu ke spouštěči v adresáři buildu.
    if (!desktopTrainingRun) LaunchAtLogin().refreshCommand()
    // Po přihlášení (`--autostart`) rovnou do oznamovací oblasti — okno nemá vyskakovat.
    val startHidden = DesktopStartup.startsHidden(args)

    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("plume-icon.png")
        ?.use { BitmapPainter(DesktopDevMark.icon(ImageIO.read(it)).toComposeImageBitmap()) }
    val mac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    application {
        var visible by remember { mutableStateOf(!startHidden) }
        // Změna jazyka aplikace složí obsah okna a ikonu v oznamovací oblasti znovu (DesktopAppLanguage).
        val language by DesktopAppLanguage.changes.collectAsState()
        // Okno se otevře, kde a jak velké bylo naposledy (DesktopWindowBounds).
        val saved = remember { DesktopWindowBounds.restore() }
        val state = rememberWindowState(
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (saved?.x != null && saved.y != null) {
                WindowPosition(saved.x!!.dp, saved.y!!.dp)
            } else {
                WindowPosition.PlatformDefault
            },
            size = DpSize((saved?.width ?: DEFAULT_WIDTH).dp, (saved?.height ?: DEFAULT_HEIGHT).dp),
        )
        RememberWindowBounds(state, saved)

        // Stav celé obrazovky se hlásí i po přepnutí tlačítkem okna (zelené na macOS).
        LaunchedEffect(state) {
            snapshotFlow { state.placement == WindowPlacement.Fullscreen }.collect(DesktopWindow::setFullscreen)
        }

        // Ťuknutí na ikonu v Docku vrátí schované okno (viz `onCloseRequest`).
        LaunchedEffect(Unit) {
            if (mac && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().addAppEventListener(AppReopenedListener { visible = true })
            }
        }

        // Popředí aplikace (online, zámek): okno je vidět, ne schované ani minimalizované.
        LaunchedEffect(Unit) {
            snapshotFlow { visible && !state.isMinimized }.collect(DesktopWindow::setShown)
        }

        // Windows: ikona v oznamovací oblasti, kam se okno schovává (viz `onCloseRequest`).
        key(language) { DesktopTrayIcon(onOpen = DesktopWindow::activate, onQuit = { exitApplication() }) }

        // Příchozí hovor, když hlavní okno není vpředu: okénko navrchu s Přijmout / Odmítnout.
        CallAccountScope { IncomingCallWindow() }

        // Video v okénku obrazu v obraze; přežije zavření prohlížeče, ze kterého přišlo.
        CallAccountScope { DesktopPictureInPictureWindow() }

        // Klávesové zkratky (Ctrl/⌘+K, Alt+šipky…) dřív, než klávesu dostane zaostřený prvek.
        val shortcuts = rememberDesktopShortcutHandler()

        Window(
            onPreviewKeyEvent = shortcuts,
            // Na macOS a Windows zavření okna aplikaci neukončí, jen okno schová — jako
            // Telegram nebo Zprávy. TDLib běží dál a oznámení chodí. Vrací ho Dock, ikona
            // v oznamovací oblasti, klik na oznámení nebo další spuštění; ukončuje se Cmd+Q,
            // resp. z nabídky ikony. Na Linuxu oznamovací oblast být nemusí, tam zavření = konec.
            onCloseRequest = { if (mac || desktopClosesToTray) visible = false else exitApplication() },
            visible = visible,
            // Ladicí běh z Gradle se značkou v názvu i ikoně, ať se nesplete s nainstalovanou aplikací.
            title = DesktopDevMark.name(APP_NAME),
            icon = icon,
            state = state,
        ) {
            /*
             * Celá obrazovka na žádost (video v prohlížeči). Po ukončení se okno vrátí, jak bylo,
             * maximalizované zůstane maximalizované. Compose ji ale ruší JEN přechodem na Floating
             * (`ComposeWindow.placement`: Maximized jen nastaví maximalizaci a celou obrazovku
             * nechá) a návrat z ní je na macOS animace, takže maximalizace přijde až po ní.
             */
            LaunchedEffect(Unit) {
                var before = state.placement
                DesktopWindow.fullscreenRequests.collect { on ->
                    if (on && state.placement != WindowPlacement.Fullscreen) {
                        before = state.placement
                        state.placement = WindowPlacement.Fullscreen
                    } else if (!on && state.placement == WindowPlacement.Fullscreen) {
                        state.placement = WindowPlacement.Floating
                        if (before == WindowPlacement.Maximized) {
                            withTimeoutOrNull(FULLSCREEN_EXIT_TIMEOUT_MS) {
                                while (window.placement == WindowPlacement.Fullscreen) delay(FULLSCREEN_EXIT_POLL_MS)
                            }
                            state.placement = WindowPlacement.Maximized
                        }
                    }
                }
            }
            // Doba startu do prvního snímku: jak pomáhá AOT cache, se ověřuje z logu.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                Logger.withTag("Startup").i { "První snímek okna ${ManagementFactory.getRuntimeMXBean().uptime} ms od startu JVM." }
                if (desktopTrainingRun) TrainingRun.finishAfterStartup()
            }
            // Klik na oznámení: okno dopředu, i když bylo schované nebo minimalizované.
            LaunchedEffect(Unit) {
                DesktopWindow.activations.collect {
                    visible = true
                    state.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            DisposableEffect(window) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent) = DesktopWindow.setFocused(true)

                    override fun windowLostFocus(event: WindowEvent) = DesktopWindow.setFocused(false)
                }
                window.addWindowFocusListener(listener)
                DesktopWindow.setFocused(window.isFocused)
                // Tlačítko okna na hlavním panelu: odznak nepřečtených a blikání (DesktopTaskbar).
                DesktopWindow.attach(window)
                onDispose {
                    window.removeWindowFocusListener(listener)
                    DesktopWindow.setFocused(false)
                    DesktopWindow.attach(null)
                }
            }
            // macOS: nabídka „Chaty" v liště se zkratkami (jinde nic, viz DesktopMenuBar).
            key(language) {
                DesktopMenuBar()
                // Obsah až k hornímu kraji okna, tlačítka okna přes něj (jen na JBR, jinak beze změny).
                DesktopTitleBar.Content(window) { App() }
            }
        }
    }
}

/**
 * Ukládá polohu a velikost okna po každé změně (s krátkým zpožděním — tah oknem jich pošle
 * desítky). U maximalizovaného okna se pamatuje i velikost PŘED maximalizací, ať se na ni po
 * dalším spuštění dá vrátit; minimalizované okno a celá obrazovka nemění nic.
 */
@OptIn(FlowPreview::class)
@Composable
private fun RememberWindowBounds(state: WindowState, saved: SavedWindowBounds?) {
    LaunchedEffect(state) {
        var floating = saved
        snapshotFlow { Triple(state.placement, state.position, state.size) to state.isMinimized }
            // Celá obrazovka je chvilková (video), po dalším spuštění se do ní nevrací.
            .filter { (window, minimized) -> !minimized && window.first != WindowPlacement.Fullscreen }
            .debounce(WINDOW_SAVE_DELAY_MS)
            .collect { (window, _) ->
                val (placement, position, size) = window
                if (placement == WindowPlacement.Floating && position is WindowPosition.Absolute) {
                    floating = SavedWindowBounds(
                        x = position.x.value.roundToInt(),
                        y = position.y.value.roundToInt(),
                        width = size.width.value.roundToInt(),
                        height = size.height.value.roundToInt(),
                        maximized = false,
                    )
                }
                val bounds = floating ?: return@collect
                DesktopWindowBounds.save(bounds.copy(maximized = placement != WindowPlacement.Floating))
            }
    }
}

private const val DEFAULT_WIDTH = 1200
private const val DEFAULT_HEIGHT = 800
private const val WINDOW_SAVE_DELAY_MS = 500L
private const val FULLSCREEN_EXIT_TIMEOUT_MS = 2_000L
private const val FULLSCREEN_EXIT_POLL_MS = 50L

/** Jméno aplikace, ne text k překladu — stejné v každém jazyce. */
private const val APP_NAME = "Plume"

/** Synchronní překreslení při tahu za okraj okna, viz `main`. */
private val LIVE_RESIZE_PROPERTIES = listOf(
    "skiko.rendering.macos.metalSynchronousLiveResize",
    "skiko.rendering.windows.direct3DSynchronousLiveResize",
)
