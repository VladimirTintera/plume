package eu.tintera.plume

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.share.ShareIntake
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val chatOpenRequests: ChatOpenRequests by inject()
    private val shareIntake: ShareIntake by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge je M3 default; insety si nanáší ten, kdo kreslí chrome (viz konvence v mlogu).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Studený start z notifikace: chatId přijde v intentu, kterým se activity zakládá.
        openChatFrom(intent)
        // Studený start ze sdílení. Po otočení se `onCreate` spustí nad týmž intentem znovu —
        // proto se sdílení z intentu po zpracování maže, jinak by se výběr chatu ukázal podruhé.
        // `savedInstanceState` navíc značí obnovu: tehdy už sdílení jednou zpracované bylo.
        if (savedInstanceState == null) receiveShare(intent)

        // O oprávnění k notifikacím se žádá až v přihlášené části aplikace, viz
        // NotificationPermissionPrompt — ne odsud, kde by dialog vyskočil nad přihlášením.
        setContent { App() }
    }

    /**
     * Ťuknutí na notifikaci nebo sdílení, když aplikace už běží. Bez `launchMode="singleTask"` by
     * se sem nedostalo — systém by activity složil znovu a `onCreate` by dostal nový intent,
     * jenže za cenu ztráty stavu obrazovek.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Bez tohohle by `getIntent()` dál vracelo ten, kterým se activity zakládala.
        setIntent(intent)
        openChatFrom(intent)
        receiveShare(intent)
    }

    /**
     * Sdílení z jiné aplikace. Soubory se musí zkopírovat hned, dokud platí oprávnění k jejich
     * URI — to dělá [ShareIntake]; výběr chatu pak ukáže shell.
     */
    private fun receiveShare(intent: Intent) {
        if (shareIntake.handle(intent)) intent.action = null
    }

    private fun openChatFrom(intent: Intent) {
        val chatId = intent.getLongExtra(NotificationIntents.EXTRA_CHAT_ID, NO_CHAT)
        if (chatId == NO_CHAT) return

        val topicId = NotificationIntents.EXTRA_TOPIC_ID
            .takeIf { intent.hasExtra(it) }
            ?.let { intent.getIntExtra(it, 0) }
        chatOpenRequests.request(chatId, topicId)

        // Jednorázové: po otočení displeje se `onCreate` spustí znovu nad týmž intentem a chat
        // by se otevřel podruhé — i kdyby se mezitím uživatel proklikal jinam.
        intent.removeExtra(NotificationIntents.EXTRA_CHAT_ID)
        intent.removeExtra(NotificationIntents.EXTRA_TOPIC_ID)
    }

    private companion object {
        const val NO_CHAT = 0L
    }
}
