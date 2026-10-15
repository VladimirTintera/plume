package eu.tintera.plume

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import eu.tintera.plume.core.calls.CallRequests
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.share.ShareIntake
import eu.tintera.plume.core.ui.KeyboardShortcutOverview
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.core.ui.handle
import eu.tintera.plume.core.ui.handleEscape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val chatOpenRequests: ChatOpenRequests by inject()
    private val linkOpenRequests: LinkOpenRequests by inject()
    private val shareIntake: ShareIntake by inject()
    private val keyboardShortcuts: KeyboardShortcuts by inject()
    private val callRequests: CallRequests by inject()
    private val calls: CallRepository by inject()

    /** Texty pro systémový přehled zkratek; do načtení přehled Plume neukáže nic. */
    private var shortcutOverview: KeyboardShortcutOverview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge je M3 default; insety si nanáší ten, kdo kreslí chrome (viz konvence v mlogu).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Studený start z notifikace: chatId přijde v intentu, kterým se activity zakládá.
        openChatFrom(intent)
        receiveCall(intent)
        showOverLockScreenDuringCalls()
        // Studený start ze sdílení. Po otočení se `onCreate` spustí nad týmž intentem znovu —
        // proto se sdílení z intentu po zpracování maže, jinak by se výběr chatu ukázal podruhé.
        // `savedInstanceState` navíc značí obnovu: tehdy už sdílení jednou zpracované bylo.
        if (savedInstanceState == null) receiveShare(intent)
        // Odkaz na Telegram z jiné aplikace; jednorázový ze stejného důvodu jako sdílení.
        if (savedInstanceState == null) receiveLink(intent)

        // O oprávnění k notifikacím se žádá až v přihlášené části aplikace, viz
        // NotificationPermissionPrompt — ne odsud, kde by dialog vyskočil nad přihlášením.
        setContent { App() }

        lifecycleScope.launch { shortcutOverview = KeyboardShortcutOverview.load() }
    }

    /** Systémový přehled zkratek (Meta+/ na fyzické klávesnici). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        shortcutOverview?.let { data += it.groups() }
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
        receiveCall(intent)
        receiveShare(intent)
        receiveLink(intent)
    }

    /** „Přijmout" nebo ťuknutí na hovor v notifikaci — vyřídí shell (mikrofon, přijetí). */
    private fun receiveCall(intent: Intent) {
        val answer = intent.getIntExtra(NotificationIntents.EXTRA_ANSWER_CALL, NO_CALL)
        if (answer != NO_CALL) {
            callRequests.answer(answer)
            intent.removeExtra(NotificationIntents.EXTRA_ANSWER_CALL)
        }
        if (intent.getBooleanExtra(NotificationIntents.EXTRA_SHOW_CALL, false)) {
            callRequests.show()
            intent.removeExtra(NotificationIntents.EXTRA_SHOW_CALL)
        }
    }

    /**
     * Po dobu hovoru (zvonění i hovor) se aplikace ukáže i nad zamčenou obrazovkou a rozsvítí
     * displej — celoobrazovková výzva hovoru by jinak otevřela jen zámek. Po hovoru zpět, ať se
     * Plume nad zámkem neukazuje jindy.
     */
    private fun showOverLockScreenDuringCalls() {
        lifecycleScope.launch {
            calls.active.map { it != null }.distinctUntilChanged().collect { inCall ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(inCall)
                    setTurnScreenOn(inCall)
                } else {
                    @Suppress("DEPRECATION")
                    val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    if (inCall) window.addFlags(flags) else window.clearFlags(flags)
                }
            }
        }
    }

    /**
     * Klávesové zkratky (Ctrl+K, Alt+šipky…) a Esc jako Zpět dřív, než klávesu dostane Compose —
     * viz [KeyboardShortcuts] a [handleEscape]. Ostatní klávesy jdou dál beze změny.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyboardShortcuts.handle(event) ||
            handleEscape(event) { onBackPressedDispatcher.onBackPressed() } ||
            super.dispatchKeyEvent(event)

    /**
     * Sdílení z jiné aplikace. Soubory se musí zkopírovat hned, dokud platí oprávnění k jejich
     * URI — to dělá [ShareIntake]; co se sdílí, pak nese čip v shellu.
     */
    private fun receiveShare(intent: Intent) {
        if (shareIntake.handle(intent)) intent.action = null
    }

    /**
     * Odkaz `t.me/…` nebo `tg://…` (filtry v manifestu). Co znamená, rozhodne až TDLib v shellu
     * ([LinkOpenRequests]); tady se jen předá a z intentu smaže, aby ho otočení displeje
     * neotevřelo podruhé.
     */
    private fun receiveLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        linkOpenRequests.request(url)
        intent.action = null
        intent.data = null
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
        const val NO_CALL = 0
    }
}
