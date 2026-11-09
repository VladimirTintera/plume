package eu.tintera.plume.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /**
     * Chat, který se má otevřít (ťuknutí na chat v dlaždici nebo na notifikaci). Aktivita je `singleTop`: když už
     * běží, systém ji jen přivede dopředu a nový intent dorazí do [onNewIntent] — jinak by
     * extra zůstalo nepřečtené a otevřel by se poslední stav.
     */
    private val openChat = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Po obnově procesu (savedInstanceState) už chat otevřený je, podruhé se nenaviguje.
        if (savedInstanceState == null) openChat.value = chatIdOf(intent)
        setContent { PrimaryAccountScope { WearApp(openChat = openChat.value, onChatOpened = { openChat.value = null }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openChat.value = chatIdOf(intent)
    }

    private fun chatIdOf(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_CHAT_ID, 0L)?.takeIf { it != 0L }

    companion object {
        /** Id chatu k otevření, `Long`; plní ho dlaždice s chaty (`RecentChatsTileService`) a notifikace (`WearNotifications`). */
        const val EXTRA_CHAT_ID = "eu.tintera.plume.wear.CHAT_ID"
    }
}
