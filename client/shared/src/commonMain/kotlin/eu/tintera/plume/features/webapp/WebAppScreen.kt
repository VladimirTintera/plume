package eu.tintera.plume.features.webapp

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalIconButton
import eu.tintera.plume.resources.webapp_location_title
import eu.tintera.plume.resources.webapp_location_text
import eu.tintera.plume.resources.webapp_location_allow
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.core.location.rememberDeviceLocation
import eu.tintera.plume.core.webapp.rememberHomeScreenShortcuts
import androidx.compose.material3.Surface
import eu.tintera.plume.core.telegram.BotShareSource
import eu.tintera.plume.core.telegram.PreparedButton
import eu.tintera.plume.features.forward.BotShareScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.download_24dp
import eu.tintera.plume.resources.webapp_write_access_title
import eu.tintera.plume.resources.webapp_write_access_text
import eu.tintera.plume.resources.webapp_write_access_allow
import eu.tintera.plume.resources.webapp_download_title
import eu.tintera.plume.resources.webapp_download_text
import eu.tintera.plume.resources.webapp_download_confirm
import eu.tintera.plume.core.ui.DialogCheckRow
import eu.tintera.plume.resources.apps_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import eu.tintera.plume.core.ui.MediumButtonHeight
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.material3.IconButtonDefaults
import eu.tintera.plume.resources.bot_login_write_access
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.telegram.WebAppTheme
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.threshold
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.webview.WebAppViewController
import eu.tintera.plume.core.webview.WebAppWebView
import eu.tintera.plume.features.conversation.ChatTarget
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.webapp_close_confirm_text
import eu.tintera.plume.resources.webapp_consent_open
import eu.tintera.plume.resources.webapp_consent_text
import eu.tintera.plume.resources.webapp_consent_title
import eu.tintera.plume.resources.webapp_close_confirm_title
import eu.tintera.plume.resources.webapp_popup_ok
import eu.tintera.plume.resources.webapp_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Mini aplikace přes celou obrazovku: hlavička s botem, WebView a hlavní tlačítko.
 *
 * - **Hlavička a hlavní tlačítko jsou naše, ne stránky** (Telegram je kreslí taky nativně).
 *   Stránka si smí přebarvit hlavičku (`web_app_set_header_color`); popředí se pak volí podle
 *   světlosti, jinak by na tmavé barvě zůstal tmavý text.
 * - **Zpět (šipka i gesto) jde napřed do mini aplikace**, když má vlastní „zpět" (`web_app_setup_back_button`);
 *   `NavigationBackHandler`, ne `BackHandler` — ten by predictive back zabil jinde v aplikaci.
 * - **Výška pro `viewport_changed` je výška WebView v dp** (= CSS pixelech). Hlavička a tlačítko
 *   jsou mimo něj, takže okraje (`safe_area`) hlásíme nulové.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebAppScreen(
    viewModel: WebAppViewModel,
    onClose: () -> Unit,
    onOpenChat: (ChatTarget) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = webAppTheme(MaterialTheme.colorScheme)
    val controller = remember { WebAppViewController() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var heightDp by remember { mutableIntStateOf(0) }
    val currentHeight by rememberUpdatedState(heightDp)
    val currentClose by rememberUpdatedState(onClose)
    val currentOpenChat by rememberUpdatedState(onOpenChat)
    // Poloha pro bota: přesná, o oprávnění se žádá až teď (souhlas s botem už padl ve VM).
    val location = rememberDeviceLocation(precise = true)
    val shortcuts = rememberHomeScreenShortcuts()

    LaunchedEffect(Unit) { viewModel.start(theme) }
    LaunchedEffect(theme) { viewModel.themeChanged(theme) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WebAppEffect.Post -> controller.run(effect.event.script())
                WebAppEffect.ReportViewport -> controller.run(WebAppEvents.viewportChanged(currentHeight).script())
                is WebAppEffect.Haptic -> when (effect.kind) {
                    WebAppHaptic.Light, WebAppHaptic.Selection -> haptics.tick()
                    WebAppHaptic.Heavy -> haptics.threshold()
                    WebAppHaptic.Success -> haptics.confirm()
                    WebAppHaptic.Error -> haptics.reject()
                }
                WebAppEffect.Close -> currentClose()
                is WebAppEffect.OpenChat -> currentOpenChat(effect.target)
                WebAppEffect.RequestLocation -> location.request { point -> viewModel.locationResult(point?.latitude, point?.longitude) }
                is WebAppEffect.HomeScreen -> {
                    val status = if (effect.add) {
                        if (shortcuts.add(effect.id, effect.label, effect.url)) "added" else shortcuts.status(effect.id)
                    } else {
                        shortcuts.status(effect.id)
                    }
                    viewModel.homeScreenResult(effect.add, status)
                }
            }
        }
    }
    // Změna výšky (klávesnice, otočení) se stránce hlásí sama, ne až na požádání.
    LaunchedEffect(heightDp, state.url) {
        if (heightDp > 0 && state.url != null) controller.run(WebAppEvents.viewportChanged(heightDp).script())
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = viewModel::back,
    )

    val headerColor = state.headerColor?.let(::rgbColor) ?: MaterialTheme.colorScheme.surface
    val headerContent = state.headerColor?.let { contentOn(rgbColor(it)) } ?: MaterialTheme.colorScheme.onSurface
    // Hlavička v barvě od stránky leží pod stavovou lištou; na tmavé by tmavé ikony lišty zmizely
    // (BotFather si nastaví tmavou hlavičku i ve světlém motivu aplikace).
    if (headerContent == Color.White) DarkSystemBars()
    val botName = state.title ?: stringResource(Res.string.webapp_title)
    // Box kvůli výběrům přes mini aplikaci (sdílení zprávy, chat pro bota): WebView zůstane
    // pod nimi naživu, stránka čeká na odpověď a po návratu je tam, kde byla.
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // Na celé ploše hlavička zmizí; zavřít jde tlačítkem přes stránku (níž).
                if (!state.fullscreen) TopAppBar(
                    title = {
                        Text(
                            state.title ?: stringResource(Res.string.webapp_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (state.backVisible) {
                            IconButton(onClick = viewModel::back, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                            }
                        } else {
                            IconButton(onClick = viewModel::requestClose, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = headerColor,
                        scrolledContainerColor = headerColor,
                        titleContentColor = headerContent,
                        navigationIconContentColor = headerContent,
                    ),
                )
            },
            bottomBar = {
                WebAppBottomBar(
                    main = state.mainButton,
                    secondary = state.secondaryButton,
                    position = state.secondaryPosition,
                    color = state.bottomBarColor?.let(::rgbColor) ?: MaterialTheme.colorScheme.surface,
                    onMain = viewModel::pressMainButton,
                    onSecondary = viewModel::pressSecondaryButton,
                )
            },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            val background = state.backgroundColor?.let(::rgbColor) ?: MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(background)
                    // Bez hlavičky by stránka zajela pod stavovou lištu; okraje hlásíme nulové.
                    .then(if (state.fullscreen) Modifier.statusBarsPadding() else Modifier)
                    .onSizeChanged { size -> heightDp = with(density) { size.height.toDp().value.toInt() } },
                contentAlignment = Alignment.Center,
            ) {
                state.url?.let { address ->
                    WebAppWebView(
                        url = address,
                        controller = controller,
                        backgroundColor = state.backgroundColor ?: theme.background,
                        onEvent = viewModel::onEvent,
                        onPageLoaded = viewModel::pageLoaded,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.fullscreen) {
                    // Zavřít / zpět přes stránku; stránka má nahoře volno (`content_safe_area`).
                    FilledTonalIconButton(
                        onClick = viewModel::back,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    ) {
                        Icon(
                            painterResource(if (state.backVisible) Res.drawable.arrow_back_24dp else Res.drawable.close_24dp),
                            contentDescription = stringResource(if (state.backVisible) Res.string.common_back else Res.string.common_close),
                        )
                    }
                }
                when {
                    state.error != null -> Text(
                        state.error!!.resolve(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                    state.consent -> Unit
                    state.loading -> LoadingIndicator()
                }
            }
        }

        state.share?.let { share ->
            WebAppOverlay(onBack = { viewModel.shareFinished(sent = false) }) {
                PreparedShareScreen(
                    viewModel = koinViewModel(key = "web-app-share-${share.id}", parameters = { parametersOf(share.message) }),
                    message = share.message,
                    bot = botName,
                    onBack = { viewModel.shareFinished(sent = false) },
                    onSent = { viewModel.shareFinished(sent = true) },
                )
            }
        }
        state.chatRequest?.let { request ->
            val users = (request.button as? PreparedButton.Users)?.request
            val chat = (request.button as? PreparedButton.Chat)?.request
            WebAppOverlay(onBack = { viewModel.chatRequestFinished(shared = false) }) {
                BotShareScreen(
                    viewModel = koinViewModel(
                        key = "web-app-chat-${request.requestId}",
                        parameters = { parametersOf(BotShareSource.WebApp(viewModel.botUserId, request.requestId), users, chat) },
                    ),
                    users = users,
                    chat = chat,
                    onBack = { viewModel.chatRequestFinished(shared = false) },
                    onShared = { viewModel.chatRequestFinished(shared = true) },
                )
            }
        }
    }

    if (state.consent) {
        WebAppConsentDialog(
            botName = state.title ?: stringResource(Res.string.webapp_title),
            writeAccess = state.consentWriteAccess,
            onOpen = viewModel::confirmConsent,
            onDismiss = viewModel::dismissConsent,
        )
    }
    state.popup?.let { popup ->
        WebAppPopupDialog(popup, onClose = viewModel::popupClosed)
    }
    if (state.locationConsent) {
        ConfirmDialog(
            title = stringResource(Res.string.webapp_location_title, botName),
            text = stringResource(Res.string.webapp_location_text),
            confirmLabel = stringResource(Res.string.webapp_location_allow),
            onConfirm = { viewModel.answerLocationConsent(true) },
            onDismiss = { viewModel.answerLocationConsent(false) },
            icon = Res.drawable.location_on_24dp,
        )
    }
    if (state.writeAccessDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.webapp_write_access_title, botName),
            text = stringResource(Res.string.webapp_write_access_text),
            confirmLabel = stringResource(Res.string.webapp_write_access_allow),
            onConfirm = viewModel::allowWriteAccess,
            onDismiss = viewModel::declineWriteAccess,
            icon = Res.drawable.chat_24dp,
        )
    }
    state.download?.let { download ->
        ConfirmDialog(
            title = stringResource(Res.string.webapp_download_title),
            text = stringResource(Res.string.webapp_download_text, botName, download.fileName, urlHost(download.url)),
            confirmLabel = stringResource(Res.string.webapp_download_confirm),
            onConfirm = viewModel::confirmDownload,
            onDismiss = viewModel::dismissDownload,
            icon = Res.drawable.download_24dp,
        )
    }
    if (state.closeDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.webapp_close_confirm_title),
            text = stringResource(Res.string.webapp_close_confirm_text),
            confirmLabel = stringResource(Res.string.common_close),
            onConfirm = viewModel::confirmClose,
            onDismiss = viewModel::dismissCloseDialog,
            icon = Res.drawable.close_24dp,
        )
    }
}

/**
 * Výběr přes celou mini aplikaci. Zpět ho zavře (a stránka dostane „odmítnuto"), ne mini
 * aplikaci — handler je složený později než ten obrazovky, takže má přednost.
 */
@Composable
private fun WebAppOverlay(onBack: () -> Unit, content: @Composable () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onBack,
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
}

/** Doména z adresy pro větu u stahování — odkud soubor je, ne celá adresa s parametry. */
internal fun urlHost(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?').substringAfter('@')

/**
 * Souhlas před prvním otevřením mini aplikace u bota. TDLib ho předepisuje: mini aplikace
 * dostane údaje o účtu a co v ní uživatel udělá, může dostat i bot. Ptá se jednou na bota.
 */
@Composable
internal fun WebAppConsentDialog(
    botName: String,
    /** Pojmenovaná mini aplikace chce psát: zaškrtávátko, ve výchozím stavu zapnuté (jako přihlášení na web). */
    writeAccess: Boolean,
    onOpen: (allowWriteAccess: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var allowWrite by rememberSaveable { mutableStateOf(true) }
    ConfirmDialog(
        title = stringResource(Res.string.webapp_consent_title),
        text = stringResource(Res.string.webapp_consent_text, botName),
        confirmLabel = stringResource(Res.string.webapp_consent_open),
        onConfirm = { onOpen(writeAccess && allowWrite) },
        onDismiss = onDismiss,
        icon = Res.drawable.apps_24dp,
        content = if (writeAccess) {
            {
                DialogCheckRow(
                    label = stringResource(Res.string.bot_login_write_access, botName),
                    checked = allowWrite,
                    onChange = { allowWrite = it },
                )
            }
        } else {
            null
        },
    )
}

/**
 * Spodní lišta s hlavním a druhým tlačítkem mini aplikace.
 *
 * - **Druhé tlačítko stojí, kde si stránka řekne** ([WebAppButtonPosition]): vlevo a vpravo
 *   sdílí řádek napůl, nahoře a dole jsou pod sebou přes celou šířku. Viditelné jen jedno =
 *   přes celou šířku, ať je kterékoli.
 * - **Bez barvy od stránky je hlavní plné (`primary`) a druhé tonální (`secondaryContainer`)** —
 *   dvě plná tlačítka vedle sebe by neřekla, které je hlavní. Telegram dává druhému taky
 *   tlumenější výchozí barvu.
 * - **Barvu lišty smí stránka změnit** (`web_app_set_bottom_bar_color`), jinak je to plocha
 *   aplikace.
 */
@Composable
private fun WebAppBottomBar(
    main: WebAppButton,
    secondary: WebAppButton,
    position: WebAppButtonPosition,
    color: Color,
    onMain: () -> Unit,
    onSecondary: () -> Unit,
) {
    if (!main.visible && !secondary.visible) return
    val mainButton: @Composable (Modifier) -> Unit = { modifier ->
        WebAppBottomButton(
            main,
            defaultContainer = MaterialTheme.colorScheme.primary,
            defaultContent = MaterialTheme.colorScheme.onPrimary,
            onClick = onMain,
            modifier = modifier,
        )
    }
    val secondaryButton: @Composable (Modifier) -> Unit = { modifier ->
        WebAppBottomButton(
            secondary,
            defaultContainer = MaterialTheme.colorScheme.secondaryContainer,
            defaultContent = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onSecondary,
            modifier = modifier,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(color)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        when {
            !secondary.visible -> mainButton(Modifier.fillMaxWidth())
            !main.visible -> secondaryButton(Modifier.fillMaxWidth())
            position == WebAppButtonPosition.Left || position == WebAppButtonPosition.Right ->
                Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                    if (position == WebAppButtonPosition.Left) secondaryButton(Modifier.weight(1f))
                    mainButton(Modifier.weight(1f))
                    if (position == WebAppButtonPosition.Right) secondaryButton(Modifier.weight(1f))
                }
            else -> Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                if (position == WebAppButtonPosition.Top) secondaryButton(Modifier.fillMaxWidth())
                mainButton(Modifier.fillMaxWidth())
                if (position == WebAppButtonPosition.Bottom) secondaryButton(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Jedno tlačítko lišty. Barvu dodá stránka; bez ní [defaultContainer] / [defaultContent]. */
@Composable
private fun WebAppBottomButton(
    button: WebAppButton,
    defaultContainer: Color,
    defaultContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = button.color?.let(::rgbColor) ?: defaultContainer
    val content = button.textColor?.let(::rgbColor) ?: if (button.color != null) contentOn(container) else defaultContent
    // Hlavní akce mini aplikace, proto velikost hlavních tlačítek; pevná výška kvůli indikátoru.
    MediumButton(
        onClick = onClick,
        enabled = button.active && !button.progress,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
        modifier = modifier.height(MediumButtonHeight),
    ) {
        if (button.progress) {
            LoadingIndicator(color = content, modifier = Modifier.size(24.dp))
        } else {
            Text(button.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val BUTTON_GAP = 8.dp

/**
 * Popup mini aplikace (`web_app_open_popup`). Tlačítek smí být až tři; `destructive` je v chybové
 * barvě, `ok` / `close` / `cancel` bez textu dostanou náš. Zavření vedle = zavřený bez volby.
 */
@Composable
private fun WebAppPopupDialog(popup: WebAppPopup, onClose: (String?) -> Unit) {
    val buttons = popup.buttons.ifEmpty { listOf(WebAppPopupButton("", "close", "")) }.take(3)
    AlertDialog(
        onDismissRequest = { onClose(null) },
        title = popup.title.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        text = { Text(popup.message) },
        confirmButton = {
            buttons.forEach { button ->
                TextButton(onClick = { onClose(button.id) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        button.text.ifBlank {
                            when (button.type) {
                                "cancel" -> stringResource(Res.string.common_cancel)
                                "close" -> stringResource(Res.string.common_close)
                                else -> stringResource(Res.string.webapp_popup_ok)
                            }
                        },
                        color = if (button.type == "destructive") MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
            }
        },
    )
}

/**
 * Barvy aplikace pro mini aplikaci. Stránka je stránka (`surface`), sekce jsou karty
 * (`surfaceContainerLowest`, jako bloky seznamů v Plume), tlačítka `primary`.
 */
private fun webAppTheme(colors: ColorScheme) = WebAppTheme(
    background = colors.surface.rgb(),
    secondaryBackground = colors.surfaceContainerHigh.rgb(),
    headerBackground = colors.surface.rgb(),
    bottomBarBackground = colors.surface.rgb(),
    sectionBackground = colors.surfaceContainerLowest.rgb(),
    sectionSeparator = colors.outlineVariant.rgb(),
    text = colors.onSurface.rgb(),
    accentText = colors.primary.rgb(),
    sectionHeaderText = colors.primary.rgb(),
    subtitleText = colors.onSurfaceVariant.rgb(),
    destructiveText = colors.error.rgb(),
    hint = colors.onSurfaceVariant.rgb(),
    link = colors.primary.rgb(),
    button = colors.primary.rgb(),
    buttonText = colors.onPrimary.rgb(),
)

private fun Color.rgb(): Int = toArgb() and 0xFFFFFF

private fun rgbColor(rgb: Int): Color = Color(0xFF000000.toInt() or rgb)

/** Popředí na barvu od stránky: podle světlosti, jiné role k ní nemáme. */
private fun contentOn(background: Color): Color = if (background.luminance() > 0.5f) Color.Black else Color.White
