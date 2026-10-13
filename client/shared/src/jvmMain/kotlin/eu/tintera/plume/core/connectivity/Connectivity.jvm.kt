package eu.tintera.plume.core.connectivity

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
 * Desktopová aplikace běží, dokud je otevřené okno, a systém ji na pozadí neuspí — „popředí"
 * tu tedy nemá co měnit spojení ani úklid úložiště.
 */
actual class AppForeground {
    actual val isForeground: Flow<Boolean> = flowOf(true)
}
