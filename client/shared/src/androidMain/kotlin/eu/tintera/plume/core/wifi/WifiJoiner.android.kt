package eu.tintera.plume.core.wifi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import co.touchlab.kermit.Logger

actual class WifiJoiner(private val context: Context) {

    private val logger = Logger.withTag("WifiJoiner")

    actual val canJoin: Boolean = true

    actual fun join(ssid: String, password: String): WifiJoin {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Návrh sítě jako WPA2; síť jen s WPA3 by se takhle nepřipojila, ty jsou zatím vzácné.
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()
            val add = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (start(add)) return WifiJoin.Dialog
        }
        return if (start(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            WifiJoin.Settings
        } else {
            WifiJoin.Failed
        }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        logger.w(e) { "Nastavení Wi-Fi nejde otevřít: ${intent.action}" }
        false
    } catch (e: IllegalArgumentException) {
        // Neplatné SSID nebo heslo pro WifiNetworkSuggestion (třeba moc dlouhé).
        logger.w(e) { "Síť nejde navrhnout" }
        false
    }
}
