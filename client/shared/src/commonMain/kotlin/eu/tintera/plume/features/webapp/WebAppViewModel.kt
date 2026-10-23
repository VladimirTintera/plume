package eu.tintera.plume.features.webapp

import eu.tintera.plume.core.webapp.MiniAppStorage
import eu.tintera.plume.core.webapp.MiniAppStorageFull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.files.WebFileDownloader
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.PreparedButton
import eu.tintera.plume.core.telegram.PreparedMessage
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
    /** Mini aplikace žádá, aby bot smí uživateli psát (`requestWriteAccess`); bot to zatím nesmí. */
    val writeAccessDialog: Boolean = false,
    /** Soubor, který chce mini aplikace stáhnout (`downloadFile`); čeká na potvrzení. */
    val download: WebAppDownload? = null,
    /** Zpráva připravená botem ke sdílení (`shareMessage`); přes mini aplikaci je výběr chatů. */
    val share: PreparedShare? = null,
    /** Výběr chatu nebo lidí pro bota (`requestChat`); přes mini aplikaci je výběr. */
    val chatRequest: WebAppChatRequest? = null,
    /** Mini aplikace přes celou plochu (`requestFullscreen`): bez hlavičky aplikace. */
    val fullscreen: Boolean = false,
    /** Mini aplikace chce polohu a bot ji ještě znát nesmí: dialog se souhlasem. */
    val locationConsent: Boolean = false,
)

/** Stažení, o které mini aplikace žádá; [fileName] už očištěné od nebezpečných znaků. */
data class WebAppDownload(val url: String, val fileName: String)

data class PreparedShare(val id: String, val message: PreparedMessage)

/** [button] je jen [PreparedButton.Users] nebo [PreparedButton.Chat]; nepodporované se rovnou odmítne. */
data class WebAppChatRequest(val requestId: String, val button: PreparedButton)

/** Co má udělat obrazovka — WebView a navigace jsou její. */
sealed interface WebAppEffect {
    data class Post(val event: WebAppOutgoing) : WebAppEffect
    data object ReportViewport : WebAppEffect
    data class Haptic(val kind: WebAppHaptic) : WebAppEffect
    data object Close : WebAppEffect
    data class OpenChat(val target: ChatTarget) : WebAppEffect
    /** Zjistit polohu zařízení a vrátit ji do [WebAppViewModel.locationResult]. */
    data object RequestLocation : WebAppEffect
    /** Zástupce na ploše: zjistit stav ([add] `false`), nebo přidat — výsledek do [WebAppViewModel.homeScreenResult]. */
    data class HomeScreen(val id: String, val label: String, val url: String, val add: Boolean) : WebAppEffect
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
    val botUserId: Long,
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
    private val downloader: WebFileDownloader,
    private val appScope: CoroutineScope,
    private val storage: MiniAppStorage,
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
            WebAppEvent.RequestContentSafeArea -> post(contentSafeArea())
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
            WebAppEvent.RequestWriteAccess -> requestWriteAccess()
            is WebAppEvent.SharePreparedMessage -> sharePreparedMessage(event.id)
            is WebAppEvent.RequestChat -> requestChat(event.requestId)
            is WebAppEvent.DownloadFile -> requestDownload(event.url, event.fileName)
            is WebAppEvent.OpenInvoice -> post(WebAppEvents.invoiceClosed(event.slug))
            WebAppEvent.RequestFullscreen -> setFullscreen(true)
            WebAppEvent.ExitFullscreen -> setFullscreen(false)
            WebAppEvent.CheckHomeScreen -> homeScreen(add = false)
            WebAppEvent.AddToHomeScreen -> homeScreen(add = true)
            is WebAppEvent.DeviceStorageSave -> deviceStorage(event.requestId) { account ->
                storage.set(account, botUserId, event.key, event.value)
                WebAppEvents.deviceStorageSaved(event.requestId)
            }
            is WebAppEvent.DeviceStorageGet -> deviceStorage(event.requestId) { account ->
                WebAppEvents.deviceStorageReceived(event.requestId, storage.get(account, botUserId, event.key))
            }
            is WebAppEvent.DeviceStorageClear -> deviceStorage(event.requestId) { account ->
                storage.clear(account, botUserId)
                WebAppEvents.deviceStorageCleared(event.requestId)
            }
            is WebAppEvent.SecureStorage -> post(WebAppEvents.secureStorageFailed(event.requestId))
            WebAppEvent.CheckLocation -> checkLocation()
            WebAppEvent.RequestLocation -> requestLocation()
            // Vlastní nastavení polohy pro boty Plume nemá; souhlas se mění u dalšího dotazu.
            WebAppEvent.OpenLocationSettings -> logger.i { "Mini aplikace chce nastavení polohy, Plume ho nemá." }
            is WebAppEvent.Unknown -> logger.i { "Mini aplikace poslala neznámou událost ${event.type}." }
        }
    }

    private fun contentSafeArea() =
        WebAppEvents.safeAreaChanged("content_safe_area_changed", top = if (_state.value.fullscreen) FULLSCREEN_CONTROLS_DP else 0)

    private fun setFullscreen(fullscreen: Boolean) {
        _state.update { it.copy(fullscreen = fullscreen) }
        post(WebAppEvents.fullscreenChanged(fullscreen))
        post(contentSafeArea())
        // Bez hlavičky má stránka víc místa — výšku si musí přepočítat.
        effect(WebAppEffect.ReportViewport)
    }

    /**
     * Úložiště v zařízení pro účet a bota; chyba (plné úložiště, neznámý účet) jde stránce jako
     * `device_storage_failed`, jinak by na odpověď čekala navždy.
     */
    private fun deviceStorage(requestId: String, action: suspend (account: Long) -> WebAppOutgoing) {
        viewModelScope.launch {
            val account = chats.savedMessagesChatId.value
            val reply = if (account == null) {
                WebAppEvents.deviceStorageFailed(requestId, WebAppErrors.UNKNOWN_ERROR)
            } else {
                runCatching { action(account) }.getOrElse { failure ->
                    logger.w(failure) { "DeviceStorage mini aplikace selhalo." }
                    WebAppEvents.deviceStorageFailed(
                        requestId,
                        if (failure is MiniAppStorageFull) WebAppErrors.QUOTA_EXCEEDED else WebAppErrors.UNKNOWN_ERROR,
                    )
                }
            }
            post(reply)
        }
    }

    private fun checkLocation() {
        viewModelScope.launch {
            val allowed = chats.savedMessagesChatId.value?.let { storage.locationAllowed(it, botUserId) }
            post(WebAppEvents.locationChecked(available = true, requested = allowed != null, granted = allowed == true))
        }
    }

    /**
     * Polohu dostane bot jen se souhlasem — poprvé (a po odmítnutí znovu) se ptá dialog, jako
     * u oficiálních klientů. Souhlas se pamatuje pro účet a bota.
     */
    private fun requestLocation() {
        viewModelScope.launch {
            val allowed = chats.savedMessagesChatId.value?.let { storage.locationAllowed(it, botUserId) }
            if (allowed == true) effect(WebAppEffect.RequestLocation) else _state.update { it.copy(locationConsent = true) }
        }
    }

    fun answerLocationConsent(allow: Boolean) {
        _state.update { it.copy(locationConsent = false) }
        viewModelScope.launch {
            chats.savedMessagesChatId.value?.let { storage.setLocationAllowed(it, botUserId, allow) }
            if (allow) effect(WebAppEffect.RequestLocation) else post(WebAppEvents.locationRequested(null, null))
        }
    }

    /** Poloha od zařízení, nebo `null` (oprávnění v systému odmítnuto, nezjištěno). */
    fun locationResult(latitude: Double?, longitude: Double?) {
        post(WebAppEvents.locationRequested(latitude, longitude))
    }

    /**
     * Zástupce otevírá hlavní mini aplikaci bota odkazem `t.me/<bot>?startapp` — tudy vede
     * i odkaz zvenčí, takže souhlas a ostatní pravidla platí stejně. Bot bez uživatelského jména
     * odkaz nemá a zástupce nejde.
     */
    private fun homeScreen(add: Boolean) {
        viewModelScope.launch {
            val username = repository.botUsername(botUserId)
            if (username == null) {
                post(if (add) WebAppEvents.homeScreenFailed(WebAppErrors.UNSUPPORTED) else WebAppEvents.homeScreenChecked("unsupported"))
                return@launch
            }
            effect(
                WebAppEffect.HomeScreen(
                    id = "webapp-$botUserId",
                    label = _state.value.title?.removePrefix("@") ?: username,
                    url = "https://t.me/$username?startapp",
                    add = add,
                ),
            )
        }
    }

    /** Výsledek od platformy: stav `added` / `missing` / `unsupported`, u přidání `added` = povedlo se. */
    fun homeScreenResult(add: Boolean, status: String) {
        post(
            when {
                !add -> WebAppEvents.homeScreenChecked(status)
                status == "added" -> WebAppEvents.homeScreenAdded()
                else -> WebAppEvents.homeScreenFailed(if (status == "unsupported") WebAppErrors.UNSUPPORTED else WebAppErrors.UNKNOWN_ERROR)
            },
        )
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

    /**
     * Bot, který uživateli psát smí, dostane „dovoleno" hned (`canBotSendMessages`); jinak se
     * Plume zeptá. Tak to dělají oficiální klienti — mini aplikace se ptá při každém spuštění
     * a uživatel by jinak potvrzoval pořád dokola.
     */
    private fun requestWriteAccess() {
        if (_state.value.writeAccessDialog) return
        viewModelScope.launch {
            if (repository.canBotWrite(botUserId)) {
                post(WebAppEvents.writeAccessRequested(allowed = true))
            } else {
                _state.update { it.copy(writeAccessDialog = true) }
            }
        }
    }

    fun allowWriteAccess() {
        _state.update { it.copy(writeAccessDialog = false) }
        viewModelScope.launch {
            val allowed = repository.allowBotToWrite(botUserId).isSuccess
            post(WebAppEvents.writeAccessRequested(allowed))
        }
    }

    fun declineWriteAccess() {
        _state.update { it.copy(writeAccessDialog = false) }
        post(WebAppEvents.writeAccessRequested(allowed = false))
    }

    /** Napřed se zeptá serveru, jestli bot soubor nabízet smí; teprve pak uživatele. */
    private fun requestDownload(url: String, fileName: String) {
        if (_state.value.download != null) {
            post(WebAppEvents.fileDownloadRequested(downloading = false))
            return
        }
        viewModelScope.launch {
            repository.checkDownload(botUserId, fileName, url)
                .onSuccess { clean -> _state.update { it.copy(download = WebAppDownload(url, clean)) } }
                .onFailure { post(WebAppEvents.fileDownloadRequested(downloading = false)) }
        }
    }

    fun confirmDownload() {
        val download = _state.value.download ?: return
        _state.update { it.copy(download = null) }
        post(WebAppEvents.fileDownloadRequested(downloader.download(download.url, download.fileName)))
    }

    fun dismissDownload() {
        _state.update { it.copy(download = null) }
        post(WebAppEvents.fileDownloadRequested(downloading = false))
    }

    /** Zprávu, kterou TDLib nevydá, server už zapomněl (připravená zpráva platí omezeně dlouho). */
    private fun sharePreparedMessage(id: String) {
        if (_state.value.share != null) return
        viewModelScope.launch {
            repository.preparedMessage(botUserId, id)
                .onSuccess { message -> _state.update { it.copy(share = PreparedShare(id, message)) } }
                .onFailure { post(WebAppEvents.preparedMessageFailed(WebAppErrors.MESSAGE_EXPIRED)) }
        }
    }

    /** Výběr chatů skončil: [sent] = zpráva odešla, jinak ho uživatel zavřel. */
    fun shareFinished(sent: Boolean) {
        _state.update { it.copy(share = null) }
        post(if (sent) WebAppEvents.preparedMessageSent() else WebAppEvents.preparedMessageFailed(WebAppErrors.USER_DECLINED))
    }

    private fun requestChat(requestId: String) {
        if (_state.value.chatRequest != null) return
        viewModelScope.launch {
            repository.preparedButton(botUserId, requestId)
                .onSuccess { button ->
                    if (button is PreparedButton.Unsupported) {
                        post(WebAppEvents.requestedChatFailed(requestId, WebAppErrors.UNSUPPORTED))
                    } else {
                        _state.update { it.copy(chatRequest = WebAppChatRequest(requestId, button)) }
                    }
                }
                .onFailure { post(WebAppEvents.requestedChatFailed(requestId, WebAppErrors.UNKNOWN_ERROR)) }
        }
    }

    /** Výběr pro bota skončil: [shared] = bot dostal chat nebo lidi, jinak ho uživatel zavřel. */
    fun chatRequestFinished(shared: Boolean) {
        val request = _state.value.chatRequest ?: return
        _state.update { it.copy(chatRequest = null) }
        post(
            if (shared) {
                WebAppEvents.requestedChatSent(request.requestId)
            } else {
                WebAppEvents.requestedChatFailed(request.requestId, WebAppErrors.USER_DECLINED)
            },
        )
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

/** Výška pruhu se zavíracím tlačítkem přes stránku na celé ploše, v CSS pixelech (= dp). */
internal const val FULLSCREEN_CONTROLS_DP = 56
