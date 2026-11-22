package eu.tintera.plume

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.os.Bundle
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.locale.LegacyAppLanguage
import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import eu.tintera.plume.core.ai.SplitInstallConfirmationHost
import eu.tintera.plume.core.calls.CallRequests
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.navigation.isTelegramLink
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.share.ShareIntake
import eu.tintera.plume.core.ui.KeyboardShortcutOverview
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.core.ui.handle
import eu.tintera.plume.core.ui.handleEscape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val chatOpenRequests: ChatOpenRequests by inject()
    private val linkOpenRequests: LinkOpenRequests by inject()
    private val shareIntake: ShareIntake by inject()
    private val keyboardShortcuts: KeyboardShortcuts by inject()
    private val callRequests: CallRequests by inject()
    private val accounts: Accounts by lazy { getKoin().get() }

    /** Texty pro systémový přehled zkratek; do načtení přehled Plume neukáže nic. */
    private var shortcutOverview: KeyboardShortcutOverview? = null

    // Jazyk aplikace do Androidu 12 (od 13 ho aktivitě dává systém). Viz LegacyAppLanguage.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        LegacyAppLanguage.overrideConfiguration(newBase)?.let(::applyOverrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge je M3 default; insety si nanáší ten, kdo kreslí chrome.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Změna jazyka do Androidu 12: přeloží se až nově vytvořená aktivita (od 13 to dělá systém).
        lifecycleScope.launch { LegacyAppLanguage.changes.collect { recreate() } }

        // Studený start z notifikace: chatId přijde v intentu, kterým se activity zakládá.
        // „Zavolat zpět" dřív než chat: otevření chatu z intentu maže účet, který oba potřebují.
        // Akce hovoru jen při prvním založení: po smrti procesu systém aktivitu obnoví
        // s PŮVODNÍM intentem (smazání extras se nepamatuje) a hovor by se vytočil znovu.
        receiveCallBack(intent, act = savedInstanceState == null)
        openChatFrom(intent)
        receiveCall(intent, act = savedInstanceState == null)
        showOverLockScreenDuringCalls()
        // Studený start ze sdílení. Po otočení se `onCreate` spustí nad týmž intentem znovu —
        // proto se sdílení z intentu po zpracování maže, jinak by se výběr chatu ukázal podruhé.
        // `savedInstanceState` navíc značí obnovu: tehdy už sdílení jednou zpracované bylo.
        if (savedInstanceState == null) receiveShare(intent)
        // Odkaz na Telegram z jiné aplikace; jednorázový ze stejného důvodu jako sdílení.
        if (savedInstanceState == null) receiveLink(intent)

        // O oprávnění k notifikacím se žádá až v přihlášené části aplikace, viz
        // NotificationPermissionPrompt — ne odsud, kde by dialog vyskočil nad přihlášením.
        setContent {
            App()
            // Dialog Play, když si před instalací runtime modelu v zařízení řekne o souhlas.
            SplitInstallConfirmationHost()
        }

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
        receiveCallBack(intent)
        openChatFrom(intent)
        receiveCall(intent)
        receiveShare(intent)
        receiveLink(intent)
    }

    /**
     * „Přijmout" nebo ťuknutí na hovor v notifikaci — vyřídí shell (mikrofon, přijetí).
     * Přijetí jen z vlastní notifikace ([NotificationIntents.isCallAction]); ukázat obrazovku
     * hovoru smí kdokoli, nic se tím nestane.
     */
    private fun receiveCall(intent: Intent, act: Boolean = true) {
        val answer = intent.getIntExtra(NotificationIntents.EXTRA_ANSWER_CALL, NO_CALL)
        if (answer != NO_CALL) {
            if (act && NotificationIntents.isCallAction(intent)) callRequests.answer(answer)
            intent.removeExtra(NotificationIntents.EXTRA_ANSWER_CALL)
        }
        if (intent.getBooleanExtra(NotificationIntents.EXTRA_SHOW_CALL, false)) {
            callRequests.show()
            intent.removeExtra(NotificationIntents.EXTRA_SHOW_CALL)
        }
    }

    /**
     * „Zavolat zpět" z notifikace o zmeškaném hovoru — vytočí shell (`CallBackHost`), protože
     * o mikrofon se umí zeptat jen obrazovka. Jednorázové jako otevření chatu. Jen z vlastní
     * notifikace ([NotificationIntents.isCallAction]) — od cizí aplikace by to byl hovor bez
     * jediného potvrzení.
     */
    private fun receiveCallBack(intent: Intent, act: Boolean = true) {
        val userId = intent.getLongExtra(NotificationIntents.EXTRA_CALL_BACK_USER, NO_USER)
        if (userId == NO_USER) return
        NotificationIntents.accountOf(intent)?.takeIf { act && NotificationIntents.isCallAction(intent) }?.let { slot ->
            callRequests.callBack(slot, userId, video = intent.getBooleanExtra(NotificationIntents.EXTRA_IS_VIDEO, false))
        }
        intent.removeExtra(NotificationIntents.EXTRA_CALL_BACK_USER)
        intent.removeExtra(NotificationIntents.EXTRA_IS_VIDEO)
    }

    /**
     * Po dobu hovoru (zvonění i hovor) se aplikace ukáže i nad zamčenou obrazovkou a rozsvítí
     * displej — celoobrazovková výzva hovoru by jinak otevřela jen zámek. Po hovoru zpět, ať se
     * Plume nad zámkem neukazuje jindy.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun showOverLockScreenDuringCalls() {
        lifecycleScope.launch {
            // Hovor může mít kterýkoli účet.
            accounts.all
                .flatMapLatest { all -> combine(all.map { account -> account.get<CallRepository>().active }) { calls -> calls.any { it != null } } }
                .distinctUntilChanged()
                .collect { inCall ->
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
        // Jen odkaz na Telegram. Vlastní intenty chodí s `ACTION_VIEW` taky — řádek widgetu nese
        // data `plume-widget://chat/…`, ať se jeho `PendingIntent` neslije s ostatními řádky —
        // a jako odkaz by skončily hláškou „odkaz nejde otevřít" nad otevřeným chatem.
        if (!isTelegramLink(url)) return
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
        NotificationIntents.accountOf(intent)?.let { slot -> chatOpenRequests.request(slot, chatId, topicId) }

        // Jednorázové: po otočení displeje se `onCreate` spustí znovu nad týmž intentem a chat
        // by se otevřel podruhé — i kdyby se mezitím uživatel proklikal jinam.
        intent.removeExtra(NotificationIntents.EXTRA_CHAT_ID)
        intent.removeExtra(NotificationIntents.EXTRA_ACCOUNT)
        intent.removeExtra(NotificationIntents.EXTRA_TOPIC_ID)
    }

    private companion object {
        const val NO_CHAT = 0L
        const val NO_CALL = 0
        const val NO_USER = 0L
    }
}
