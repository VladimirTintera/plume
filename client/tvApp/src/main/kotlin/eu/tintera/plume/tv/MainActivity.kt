package eu.tintera.plume.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /**
     * Chat, který se má otevřít zvenčí ([EXTRA_CHAT_ID]; dnes pro ověřování, později z karty
     * „Pokračovat" na Google TV). Když aktivita už běží, nový intent dorazí do [onNewIntent].
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

    private fun chatIdOf(intent: Intent?): Long? = intent?.getLongExtra(EXTRA_CHAT_ID, 0L)?.takeIf { it != 0L }

    companion object {
        /** Id chatu k otevření, `Long`. */
        const val EXTRA_CHAT_ID = "eu.tintera.plume.tv.CHAT_ID"
    }
}
