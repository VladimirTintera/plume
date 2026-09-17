package eu.tintera.plume.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.Channel

/**
 * Stav sítě z `ConnectivityManager`. Potřebuje oprávnění `ACCESS_NETWORK_STATE` (normální,
 * nežádá se za běhu).
 */
actual class NetworkMonitor(context: Context) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    actual val status: Flow<NetworkStatus> = callbackFlow {
        /*
         * Číslo se zvedá jen v `onAvailable` / `onLost`, tedy při skutečné výměně výchozí sítě.
         * `onCapabilitiesChanged` chodí i několikrát za minutu (kvalita signálu, ověření
         * připojení) a vynucovat kvůli tomu znovuotevření spojení by bylo horší než nedělat nic.
         */
        var generation = 0L

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                generation++
                trySend(NetworkStatus(kindOf(manager.getNetworkCapabilities(network)), generation))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(NetworkStatus(kindOf(capabilities), generation))
            }

            override fun onLost(network: Network) {
                generation++
                trySend(NetworkStatus(NetworkKind.None, generation))
            }
        }

        /*
         * Výchozí síť, ne všechny: TDLib zajímá ta, kterou by provoz opravdu šel. Callback se
         * po registraci ozve sám, pokud nějaká síť je — proto se počáteční hodnota nečte zvlášť.
         * Když žádná není, `onAvailable` nepřijde a první hodnotu vydá až připojení; do té doby
         * si vystačí `ConnectionController` se svou výchozí.
         */
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        // Zajímá nás poslední stav, ne fronta minulých. Callback přitom nesmí čekat na odběratele.
        .buffer(Channel.CONFLATED)
}

private fun kindOf(capabilities: NetworkCapabilities?): NetworkKind = when {
    capabilities == null -> NetworkKind.None
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WiFi
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> mobileKind(capabilities)
    else -> NetworkKind.Other
}

/** `NET_CAPABILITY_NOT_ROAMING` je až od API 28; níž se roaming nepozná a bere se jako běžná data. */
private fun mobileKind(capabilities: NetworkCapabilities): NetworkKind = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> NetworkKind.Mobile
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> NetworkKind.Mobile
    else -> NetworkKind.MobileRoaming
}
