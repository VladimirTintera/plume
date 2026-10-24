package eu.tintera.plume.core.connectivity

import eu.tintera.plume.core.platform.DesktopWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Změny sítě JVM nehlásí. Desktop je skoro vždycky na pevné síti nebo Wi-Fi a TDLib si výpadek
 * pozná sám; hlásí se proto jednou „jiná síť" a dál nic. Ztrátu spojení ukáže stav spojení
 * z TDLib, jen se po návratu sítě neobnoví hned, ale až po vlastním opakování TDLib.
 */
actual class NetworkMonitor {
    actual val status: Flow<NetworkStatus> = flowOf(NetworkStatus(kind = NetworkKind.Other, generation = 0))

    actual val saveData: Flow<Boolean> = flowOf(false)
}

actual val detectsRoaming: Boolean = false

/**
 * Popředí = okno je na obrazovce ([DesktopWindow.shown]). Aplikace schovaná do oznamovací
 * oblasti, do Docku nebo minimalizovaná běží dál (systém ji neuspí), ale je „v pozadí":
 * server nás přestane vidět online — telefon pak zase upozorňuje (TDLib odkládá oznámení, dokud
 * je účet online jinde) — a zámek aplikace se po zvolené době zamkne. Fokus rozhoduje jen
 * o oznámeních: okno vedle jiného je pořád vidět.
 */
actual class AppForeground {
    actual val isForeground: Flow<Boolean> = DesktopWindow.shown
}
