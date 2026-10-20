package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Nabídka ikony v oznamovací oblasti jako nativní menu Windows (`TrackPopupMenuEx`), ne AWT
 * `PopupMenu`. AWT si položky kreslí sám (owner-draw), takže jeho nabídka vypadá jako
 * z Windows 95 a motiv systému ji mine. Obyčejné menu z user32 kreslí systém — na Windows 11
 * se zaoblením, písmem a odsazením jako nabídky Průzkumníka.
 *
 * - **Vlastní vlákno a skryté okno.** `TrackPopupMenuEx` blokuje ve vlastní modální smyčce, dokud
 *   se nabídka nezavře, a vlastníkem musí být okno téhož vlákna. Okno musí být v popředí
 *   (`SetForegroundWindow`), jinak nabídka po kliknutí jinam nezmizí; `WM_NULL` po zavření je
 *   z dokumentace téže funkce (jinak se nabídka napodruhé hned zavře).
 * - **Tmavý režim** podle motivu aplikace přes nedokumentované exporty uxtheme podle pořadového
 *   čísla: 135 `SetPreferredAppMode`, 136 `FlushMenuThemes`, 133 `AllowDarkModeForWindow`
 *   (Windows 10 1903, build 18362, a novější; na starších je pod 135 jiná funkce, proto kontrola
 *   buildu). Stejně to dělají Notepad++ nebo Windows Terminal. Režim platí pro celý proces, jiné
 *   nativní menu ale aplikace nemá.
 */
internal object WindowsTrayMenu {

    class Item(val label: String, val default: Boolean = false, val onClick: () -> Unit)

    private val logger = Logger.withTag("WindowsTrayMenu")
    private val showing = AtomicBoolean(false)

    /** `false` = nativní menu nejde (není Windows, JNA se nenačetla) a zůstává AWT `PopupMenu`. */
    val available: Boolean by lazy {
        desktopOs == DesktopOs.Windows && runCatching { User32.INSTANCE }.onFailure {
            logger.w(it) { "user32 přes JNA nejde načíst, nabídka zůstane AWT." }
        }.isSuccess
    }

    /**
     * Ukáže nabídku u kurzoru; skupiny položek oddělí čarou. [dark] je motiv aplikace, `null` =
     * podle systému. Akce zvolené položky běží na vlákně AWT.
     */
    fun show(groups: List<List<Item>>, dark: Boolean?) {
        if (!showing.compareAndSet(false, true)) return
        thread(name = "PlumeTrayMenu", isDaemon = true) {
            try {
                val chosen = runCatching { track(groups, dark) }
                    .onFailure { logger.w(it) { "Nabídku ikony nejde ukázat." } }
                    .getOrNull()
                chosen?.let { EventQueue.invokeLater(it.onClick) }
            } finally {
                showing.set(false)
            }
        }
    }

    private fun track(groups: List<List<Item>>, dark: Boolean?): Item? {
        val user32 = User32.INSTANCE
        val owner = user32.CreateWindowExW(
            WS_EX_TOOLWINDOW, WString("STATIC"), null, WS_POPUP, 0, 0, 0, 0, null, null, null, null,
        ) ?: return null
        val menu = user32.CreatePopupMenu()
        try {
            menu ?: return null
            applyTheme(owner, dark)
            val items = mutableListOf<Item>()
            groups.filter { it.isNotEmpty() }.forEachIndexed { index, group ->
                if (index > 0) user32.AppendMenuW(menu, MF_SEPARATOR, null, null)
                group.forEach { item ->
                    items += item
                    user32.AppendMenuW(menu, MF_STRING, Pointer.createConstant(items.size.toLong()), WString(item.label))
                    // Položka, kterou spustí i kliknutí na ikonu, je tučně — konvence nabídek v oznamovací oblasti.
                    if (item.default) user32.SetMenuDefaultItem(menu, items.size, 0)
                }
            }
            val cursor = IntArray(2)
            user32.GetCursorPos(cursor)
            val horizontal = if (user32.GetSystemMetrics(SM_MENUDROPALIGNMENT) != 0) TPM_RIGHTALIGN else TPM_LEFTALIGN
            user32.SetForegroundWindow(owner)
            val command = user32.TrackPopupMenuEx(
                menu,
                TPM_RETURNCMD or TPM_NONOTIFY or TPM_RIGHTBUTTON or TPM_BOTTOMALIGN or horizontal,
                cursor[0],
                cursor[1],
                owner,
                null,
            )
            user32.PostMessageW(owner, WM_NULL, null, null)
            return items.getOrNull(command - 1)
        } finally {
            menu?.let(user32::DestroyMenu)
            user32.DestroyWindow(owner)
        }
    }

    private fun applyTheme(owner: Pointer, dark: Boolean?) {
        val theme = UxTheme.functions ?: return
        runCatching {
            val mode = when (dark) {
                true -> APP_MODE_FORCE_DARK
                false -> APP_MODE_FORCE_LIGHT
                null -> APP_MODE_ALLOW_DARK
            }
            theme.setPreferredAppMode.invokeInt(arrayOf(mode))
            theme.allowDarkModeForWindow.invokeInt(arrayOf(owner, dark != false))
            theme.flushMenuThemes.invokeVoid(emptyArray())
        }.onFailure { logger.w(it) { "Motiv nabídky nejde nastavit." } }
    }

    private class UxTheme(
        val allowDarkModeForWindow: Function,
        val setPreferredAppMode: Function,
        val flushMenuThemes: Function,
    ) {
        companion object {
            val functions: UxTheme? by lazy {
                runCatching {
                    if (windowsBuild() < BUILD_1903) return@runCatching null
                    val kernel32 = Kernel32.INSTANCE
                    val module = kernel32.LoadLibraryW(WString("uxtheme.dll")) ?: return@runCatching null
                    fun ordinal(number: Long) = kernel32.GetProcAddress(module, Pointer.createConstant(number))
                        ?.let(Function::getFunction)
                    UxTheme(
                        allowDarkModeForWindow = ordinal(133) ?: return@runCatching null,
                        setPreferredAppMode = ordinal(135) ?: return@runCatching null,
                        flushMenuThemes = ordinal(136) ?: return@runCatching null,
                    )
                }.onFailure { Logger.withTag("WindowsTrayMenu").w(it) { "uxtheme nejde načíst." } }.getOrNull()
            }

            /** `os.version` je na Windows 11 pořád „10.0" a build neobsahuje; `RtlGetVersion` neklame jako `GetVersionEx`. */
            private fun windowsBuild(): Int {
                val info = Memory(OSVERSIONINFOW_SIZE.toLong()).apply {
                    clear()
                    setInt(0, OSVERSIONINFOW_SIZE)
                }
                if (Ntdll.INSTANCE.RtlGetVersion(info) != 0) return 0
                return info.getInt(12)
            }
        }
    }

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun CreateWindowExW(
            exStyle: Int, className: WString, windowName: WString?, style: Int,
            x: Int, y: Int, width: Int, height: Int,
            parent: Pointer?, menu: Pointer?, instance: Pointer?, param: Pointer?,
        ): Pointer?

        fun DestroyWindow(hwnd: Pointer): Boolean
        fun CreatePopupMenu(): Pointer?
        fun DestroyMenu(menu: Pointer): Boolean
        fun AppendMenuW(menu: Pointer, flags: Int, id: Pointer?, text: WString?): Boolean
        fun SetMenuDefaultItem(menu: Pointer, item: Int, byPosition: Int): Boolean
        fun GetCursorPos(point: IntArray): Boolean
        fun GetSystemMetrics(index: Int): Int
        fun SetForegroundWindow(hwnd: Pointer): Boolean
        fun TrackPopupMenuEx(menu: Pointer, flags: Int, x: Int, y: Int, hwnd: Pointer, params: Pointer?): Int
        fun PostMessageW(hwnd: Pointer, message: Int, wParam: Pointer?, lParam: Pointer?): Boolean

        companion object {
            val INSTANCE: User32 by lazy { Native.load("user32", User32::class.java) }
        }
    }

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun LoadLibraryW(name: WString): Pointer?
        fun GetProcAddress(module: Pointer, name: Pointer): Pointer?

        companion object {
            val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }
        }
    }

    @Suppress("FunctionName")
    private interface Ntdll : Library {
        fun RtlGetVersion(info: Pointer): Int

        companion object {
            val INSTANCE: Ntdll by lazy { Native.load("ntdll", Ntdll::class.java) }
        }
    }

    private const val WS_POPUP = 0x80000000.toInt()
    private const val WS_EX_TOOLWINDOW = 0x80
    private const val MF_STRING = 0x0
    private const val MF_SEPARATOR = 0x800
    private const val TPM_LEFTALIGN = 0x0
    private const val TPM_RIGHTBUTTON = 0x2
    private const val TPM_RIGHTALIGN = 0x8
    private const val TPM_BOTTOMALIGN = 0x20
    private const val TPM_NONOTIFY = 0x80
    private const val TPM_RETURNCMD = 0x100
    private const val SM_MENUDROPALIGNMENT = 40
    private const val WM_NULL = 0x0
    private const val APP_MODE_ALLOW_DARK = 1
    private const val APP_MODE_FORCE_DARK = 2
    private const val APP_MODE_FORCE_LIGHT = 3
    private const val BUILD_1903 = 18362
    private const val OSVERSIONINFOW_SIZE = 276
}
