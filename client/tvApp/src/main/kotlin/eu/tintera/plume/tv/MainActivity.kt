package eu.tintera.plume.tv

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /**
     * Chat, který se má otevřít zvenčí ([EXTRA_CHAT_ID]). Když aktivita už běží, nový intent
     * dorazí do [onNewIntent].
     *
     * **Jen v ladicím buildu.** Aktivita je exportovaná (launcher) a extra by mohla poslat
     * kterákoli aplikace v televizi — otevřením chatu se zprávy označí jako přečtené. Slouží
     * dnes jen k ověřování (`am start … --el eu.tintera.plume.tv.CHAT_ID`); až chat bude otevírat
     * notifikace nebo karta Google TV, půjde to přes `PendingIntent` a neexportovanou cestu.
     */
    private val openChat = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Po obnově procesu už chat otevřený je, podruhé se nenaviguje.
        if (savedInstanceState == null) openChat.value = chatIdOf(intent)
        setContent { PrimaryAccountScope { TvApp(openChat = openChat.value, onChatOpened = { openChat.value = null }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openChat.value = chatIdOf(intent)
    }

    private fun chatIdOf(intent: Intent?): Long? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return intent?.getLongExtra(EXTRA_CHAT_ID, 0L)?.takeIf { it != 0L }
    }

    companion object {
        /** Id chatu k otevření, `Long`. */
        const val EXTRA_CHAT_ID = "eu.tintera.plume.tv.CHAT_ID"
    }
}
