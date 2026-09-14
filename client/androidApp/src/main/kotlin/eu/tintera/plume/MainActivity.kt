package eu.tintera.plume

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    /**
     * Od Androidu 13 je zobrazení notifikací běhové oprávnění.
     *
     * Ptáme se rovnou při startu, ne až u první zprávy: notifikace přicházejí asynchronně a
     * vyskočit s dialogem uprostřed čtení konverzace by bylo horší než se zeptat hned.
     */
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Odmítnutí nic nerozbije — notifikace se prostě nezobrazí. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge je M3 default; insety si nanáší ten, kdo kreslí chrome (viz konvence v mlogu).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        askForNotificationPermission()

        setContent { App() }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
