package eu.tintera.plume.features.webapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.NamedWebApp
import eu.tintera.plume.core.telegram.WebAppRepository
import eu.tintera.plume.core.telegram.WebAppTheme
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.features.conversation.ChatTarget
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.webapp_load_failed
import eu.tintera.plume.resources.webapp_not_found
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebAppState(
    /** Jméno bota do hlavičky; `null`, dokud se nenačte. */
    val title: String? = null,
    /** Adresa pro WebView; `null`, dokud ji TDLib nevydá. */
    val url: String? = null,
    /** Stránka ještě nehlásí `web_app_ready` ani není načtená. */
    val loading: Boolean = true,
    val error: UiText? = null,
    val mainButton: WebAppButton = WebAppButton(),
    val secondaryButton: WebAppButton = WebAppButton(),
    val secondaryPosition: WebAppButtonPosition = WebAppButtonPosition.Left,
    /** Barva spodní lišty s tlačítky; `null` = barva aplikace. RGB. */
    val bottomBarColor: Int? = null,
    /** Mini aplikace má vlastní „zpět" — šipka a systémové zpět jdou do ní, ne ven. */
    val backVisible: Boolean = false,
    /** Mini aplikace chce, aby se zavření potvrzovalo (rozepsaný formulář). */
    val confirmClose: Boolean = false,
    val closeDialog: Boolean = false,
    /** Barvy, které si stránka vyžádala; `null` = barvy aplikace. RGB. */
    val headerColor: Int? = null,
    val backgroundColor: Int? = null,
    val popup: WebAppPopup? = null,
    /** Bot se ještě nikdy neotevíral: před načtením se ukáže souhlas ([WebAppViewModel.confirmConsent]). */
    val consent: Boolean = false,
    /** Pojmenovaná mini aplikace chce psát uživateli: souhlas nabídne zaškrtávátko. */
    val consentWriteAccess: Boolean = false,
)

/** Co má udělat obrazovka — WebView a navigace jsou její. */
sealed interface WebAppEffect {
    data class Post(val event: WebAppOutgoing) : WebAppEffect
    data object ReportViewport : WebAppEffect
    data class Haptic(val kind: WebAppHaptic) : WebAppEffect
    data object Close : WebAppEffect
    data class OpenChat(val target: ChatTarget) : WebAppEffect
}

/**
 * Mini aplikace bota: získá adresu od TDLib a obslouží události stránky ([parseWebAppEvent]).
 *
 * - **Poprvé u každého bota se ptá**, než se cokoli načte — TDLib to u mini aplikací předepisuje:
 *   stránka dostane údaje o účtu a co v ní uživatel udělá, může dostat i bot. Ptá se tady, ne
 *   u tlačítka, které mini aplikaci otevřelo: vstupů je víc (tlačítko pod zprávou, klávesnice,
 *   nabídka bota, profil, odkaz) a každý by na souhlas musel myslet zvlášť. Potvrzení se pamatuje
 *   ([AppSettings.confirmedWebAppBots]).
 * - **Spuštění se musí zavřít** (`closeWebApp`) — v `onCleared`, přes [appScope], ať proběhne
 *   i s odchodem obrazovky.
 * - **Data botovi** (`web_app_data_send`) smí poslat jen mini aplikace z vlastní klávesnice
 *   ([keyboardButtonText]); jinde TDLib volání odmítne a Telegram ho tam taky ignoruje.
 * - **Odkazy**: web do prohlížeče (jen http/https), odkaz na Telegram se rozpozná jako v konverzaci
 *   a chat se otevře; ostatní druhy odkazů jdou do prohlížeče.
 * - **Po odeslání zprávy se zavře** (`updateWebAppMessageSent` s naším `launchId`) — bot
 *   odpověděl na dotaz mini aplikace a ta je hotová; TDLib zavření přikazuje.
 * - **Strážní bot** ([guardQueryId]) schvaluje vstup do chatu. Jakmile rozhodne, mini aplikace
 *   se zavře sama (TDLib to u `updateChatJoinResult` přikazuje); co dál (otevřít chat, hláška),
 *   řeší ten, kdo vstup začal — ví, odkud uživatel přišel.
 */
class WebAppViewModel(
    private val chatId: Long,
    private val botUserId: Long,
    private val url: String,
    private val launch: Boolean,
    private val keyboardButtonText: String?,
    /** Hlavní mini aplikace bota (`getMainWebApp`), [url] je prázdná. */
    private val main: Boolean,
    private val startParameter: String,
    /** Pojmenovaná mini aplikace z odkazu `t.me/bot/app` (`getWebAppLinkUrl`); jinak prázdné. */
    private val shortName: String,
    /** Strážní bot schvalující vstup do chatu (`getGuardBotWebAppUrl`): id dotazu, jinak 0. */
    private val guardQueryId: Long,
    private val repository: WebAppRepository,
    private val settings: AppSettings,
    private val chats: ChatRepository,
    private val urlOpener: UrlOpener,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val logger = Logger.withTag("WebApp")

    private val _state = MutableStateFlow(WebAppState())
    val state: StateFlow<WebAppState> = _state.asStateFlow()

    private val effectChannel = Channel<WebAppEffect>(Channel.BUFFERED)
    val effects: Flow<WebAppEffect> = effectChannel.receiveAsFlow()

    private var launchId: Long? = null
    private var theme: WebAppTheme? = null
    private var started = false

    private val nameLoaded = viewModelScope.launch { _state.update { it.copy(title = repository.botName(botUserId)) } }

    init {
        // Zprávu z mini aplikace odeslal bot (`answerWebAppQuery`): TDLib přikazuje zavřít.
        // Týká se jen spuštění přes `openWebApp` — jen to má `launchId`.
        viewModelScope.launch {
            repository.messagesSent.collect { sent -> if (sent == launchId) effect(WebAppEffect.Close) }
        }
        if (guardQueryId != 0L) {
            viewModelScope.launch {
                chats.awaitGuardDecision(guardQueryId)
                effect(WebAppEffect.Close)
            }
        }
    }

    /** Spustí mini aplikaci s barvami aplikace. Volá obrazovka jednou; další volání nic nedělají. */
    fun start(theme: WebAppTheme) {
        this.theme = theme
        if (started) return
        started = true
        viewModelScope.launch {
            // Pojmenovanou mini aplikaci napřed najít: jestli ji bot má a jestli se smí otevřít
            // bez ptaní, ví jen server.
            val app = if (shortName.isNotEmpty()) {
                repository.findWebApp(botUserId, shortName).getOrElse {
                    _state.update { it.copy(loading = false, error = UiText(Res.string.webapp_not_found)) }
                    return@launch
                }
            } else {
                null
            }
            if (needsWebAppConsent(botUserId in settings.confirmedWebAppBots.value, app)) {
                // Souhlas jmenuje bota; s ukázáním se počká na jméno (lokální dotaz, chvilka).
                nameLoaded.join()
                _state.update { it.copy(consent = true, consentWriteAccess = app?.requestWriteAccess == true) }
            } else {
                load()
            }
        }
    }

    /** [allowWriteAccess] jen u pojmenované mini aplikace, která o psaní žádá. */
    fun confirmConsent(allowWriteAccess: Boolean = false) {
        settings.confirmWebAppBot(botUserId)
        _state.update { it.copy(consent = false) }
        load(allowWriteAccess)
    }

    /** Souhlas odmítnutý: mini aplikace se nenačte a obrazovka odejde. */
    fun dismissConsent() {
        _state.update { it.copy(consent = false) }
        effect(WebAppEffect.Close)
    }

    private fun load(allowWriteAccess: Boolean = false) {
        val theme = theme ?: return
        viewModelScope.launch {
            val result = if (guardQueryId != 0L) {
                repository.guardUrl(guardQueryId, theme)
            } else if (shortName.isNotEmpty()) {
                repository.linkUrl(chatId, botUserId, shortName, startParameter, allowWriteAccess, theme)
            } else if (main) {
                repository.mainUrl(chatId, botUserId, startParameter, theme)
            } else if (launch) {
                repository.open(chatId, botUserId, url, theme).map { launched ->
                    launchId = launched.launchId
                    launched.url
                }
            } else {
                repository.keyboardUrl(botUserId, url, theme)
            }
            result
                .onSuccess { address -> _state.update { it.copy(url = address) } }
                .onFailure { _state.update { it.copy(loading = false, error = UiText(Res.string.webapp_load_failed)) } }
        }
    }

    /** Motiv aplikace se změnil (světlý / tmavý): stránka dostane nové barvy. */
    fun themeChanged(theme: WebAppTheme) {
        if (this.theme == theme) return
        this.theme = theme
        if (_state.value.url != null) post(WebAppEvents.themeChanged(theme))
    }

    fun pageLoaded() = _state.update { it.copy(loading = false) }

    fun onEvent(type: String, data: String?) {
        when (val event = parseWebAppEvent(type, data)) {
            WebAppEvent.Ready -> _state.update { it.copy(loading = false) }
            WebAppEvent.Close -> effect(WebAppEffect.Close)
            WebAppEvent.RequestTheme -> theme?.let { post(WebAppEvents.themeChanged(it)) }
            WebAppEvent.RequestViewport -> effect(WebAppEffect.ReportViewport)
            WebAppEvent.RequestSafeArea -> post(WebAppEvents.safeAreaChanged("safe_area_changed"))
            WebAppEvent.RequestContentSafeArea -> post(WebAppEvents.safeAreaChanged("content_safe_area_changed"))
            is WebAppEvent.MainButton -> _state.update { it.copy(mainButton = event.button) }
            is WebAppEvent.SecondaryButton -> _state.update {
                it.copy(secondaryButton = event.button, secondaryPosition = event.position)
            }
            is WebAppEvent.BottomBarColor -> _state.update {
                it.copy(bottomBarColor = event.color ?: theme?.let { current -> themeColor(current, event.key) })
            }
            is WebAppEvent.BackButton -> _state.update { it.copy(backVisible = event.visible) }
            is WebAppEvent.ClosingConfirmation -> _state.update { it.copy(confirmClose = event.needed) }
            is WebAppEvent.OpenLink -> openExternal(event.url)
            is WebAppEvent.OpenTelegramLink -> openTelegramLink(event.url)
            is WebAppEvent.SendData -> sendData(event.data)
            is WebAppEvent.Haptic -> effect(WebAppEffect.Haptic(event.kind))
            is WebAppEvent.HeaderColor -> _state.update {
                it.copy(headerColor = event.color ?: theme?.let { current -> themeColor(current, event.key) })
            }
            is WebAppEvent.BackgroundColor -> _state.update { it.copy(backgroundColor = event.color) }
            is WebAppEvent.Popup -> _state.update { it.copy(popup = event.popup) }
            is WebAppEvent.CustomMethod -> viewModelScope.launch {
                repository.customRequest(botUserId, event.method, event.parameters)
                    .onSuccess { result -> post(WebAppEvents.customMethodInvoked(event.requestId, result, null)) }
                    .onFailure { failure -> post(WebAppEvents.customMethodInvoked(event.requestId, null, failure.message)) }
            }
            is WebAppEvent.ReadClipboard -> post(WebAppEvents.clipboardTextReceived(event.requestId))
            WebAppEvent.RequestPhone -> post(WebAppEvents.phoneRequested())
            WebAppEvent.RequestWriteAccess -> post(WebAppEvents.writeAccessRequested())
            is WebAppEvent.OpenInvoice -> post(WebAppEvents.invoiceClosed(event.slug))
            WebAppEvent.RequestFullscreen -> post(WebAppEvents.fullscreenFailed())
            WebAppEvent.CheckHomeScreen -> post(WebAppEvents.homeScreenChecked())
            is WebAppEvent.Unknown -> logger.i { "Mini aplikace poslala neznámou událost ${event.type}." }
        }
    }

    fun pressMainButton() {
        val button = _state.value.mainButton
        if (button.visible && button.active && !button.progress) post(WebAppEvents.mainButtonPressed())
    }

    fun pressSecondaryButton() {
        val button = _state.value.secondaryButton
        if (button.visible && button.active && !button.progress) post(WebAppEvents.secondaryButtonPressed())
    }

    /** Šipka v hlavičce i systémové zpět: napřed „zpět" mini aplikace, pak zavření. */
    fun back() {
        if (_state.value.backVisible) post(WebAppEvents.backButtonPressed()) else requestClose()
    }

    /** ✕ — zavřít, s potvrzením, když si ho mini aplikace přeje. */
    fun requestClose() {
        if (_state.value.confirmClose) _state.update { it.copy(closeDialog = true) } else effect(WebAppEffect.Close)
    }

    fun confirmClose() {
        _state.update { it.copy(closeDialog = false) }
        effect(WebAppEffect.Close)
    }

    fun dismissCloseDialog() = _state.update { it.copy(closeDialog = false) }

    /** Popup zavřený tlačítkem [buttonId], nebo bez volby (`null`). */
    fun popupClosed(buttonId: String?) {
        _state.update { it.copy(popup = null) }
        post(WebAppEvents.popupClosed(buttonId))
    }

    private fun openExternal(url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) urlOpener.open(url)
    }

    private fun openTelegramLink(url: String) {
        viewModelScope.launch {
            when (val link = chats.resolveLink(url).getOrNull()) {
                is InternalLink.Chat -> effect(WebAppEffect.OpenChat(ChatTarget(link.chatId)))
                is InternalLink.Message -> effect(WebAppEffect.OpenChat(ChatTarget(link.chatId, link.messageId, topicId = link.topicId)))
                // Jiná mini aplikace z mini aplikace: otevře se chat jejího bota (Start, nabídka,
                // profil s „Otevřít aplikaci"). Vrstvit mini aplikace přes sebe by zpět mátlo.
                is InternalLink.MainWebApp -> effect(WebAppEffect.OpenChat(ChatTarget(link.botUserId)))
                is InternalLink.WebApp -> effect(WebAppEffect.OpenChat(ChatTarget(link.botUserId)))
                // Přihlašovací odkaz se z ťuknutí neotevírá nikde — ani z mini aplikace přes
                // prohlížeč do oficiálního klienta (viz InternalLink.LoginWarning).
                is InternalLink.LoginWarning -> Unit
                // Pozvánky, samolepky, spuštění bota… obsluhuje konverzace; mini aplikace je
                // otevírá zřídka, a prohlížeč s odkazem na Telegram je pořád lepší než nic.
                else -> urlOpener.open(url)
            }
        }
    }

    private fun sendData(data: String) {
        val buttonText = keyboardButtonText ?: return
        viewModelScope.launch {
            repository.sendData(botUserId, buttonText, data)
            effect(WebAppEffect.Close)
        }
    }

    private fun post(event: WebAppOutgoing) = effect(WebAppEffect.Post(event))

    private fun effect(effect: WebAppEffect) {
        effectChannel.trySend(effect)
    }

    override fun onCleared() {
        launchId?.let { id -> appScope.launch { repository.close(id) } }
    }
}

/**
 * Ptát se před otevřením? Poprvé u bota vždycky ([confirmedBot]). Pojmenovaná mini aplikace
 * z odkazu ([app]) i potom, když server potvrzení vyžaduje (`skipConfirmation` = false — odkaz
 * může poslat kdokoli), nebo když chce psát uživateli: to se rozhoduje pokaždé zaškrtávátkem.
 */
internal fun needsWebAppConsent(confirmedBot: Boolean, app: NamedWebApp?): Boolean =
    !confirmedBot || (app != null && (!app.skipConfirmation || app.requestWriteAccess))

/** Barva z motivu podle klíče, který mini aplikace pošle místo barvy. */
private fun themeColor(theme: WebAppTheme, key: String?): Int? = when (key) {
    "bg_color" -> theme.background
    "secondary_bg_color" -> theme.secondaryBackground
    "bottom_bar_bg_color" -> theme.bottomBarBackground
    else -> null
}
