package eu.tintera.plume.features.webapp

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.title ?: stringResource(Res.string.webapp_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (state.backVisible) {
                        IconButton(onClick = viewModel::back) {
                            Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                        }
                    } else {
                        IconButton(onClick = viewModel::requestClose) {
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
            val button = state.mainButton
            if (button.visible) {
                val container = button.color?.let(::rgbColor) ?: MaterialTheme.colorScheme.primary
                val content = button.textColor?.let(::rgbColor) ?: contentOn(container)
                Button(
                    onClick = viewModel::pressMainButton,
                    enabled = button.active && !button.progress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = container,
                        contentColor = content,
                        disabledContainerColor = container.copy(alpha = 0.5f),
                        disabledContentColor = content.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (button.progress) {
                        CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text(button.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val background = state.backgroundColor?.let(::rgbColor) ?: MaterialTheme.colorScheme.surface
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(background)
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

    if (state.consent) {
        WebAppConsentDialog(
            botName = state.title ?: stringResource(Res.string.webapp_title),
            onOpen = viewModel::confirmConsent,
            onDismiss = viewModel::dismissConsent,
        )
    }
    state.popup?.let { popup ->
        WebAppPopupDialog(popup, onClose = viewModel::popupClosed)
    }
    if (state.closeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCloseDialog,
            title = { Text(stringResource(Res.string.webapp_close_confirm_title)) },
            text = { Text(stringResource(Res.string.webapp_close_confirm_text)) },
            confirmButton = { TextButton(onClick = viewModel::confirmClose) { Text(stringResource(Res.string.common_close)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissCloseDialog) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/**
 * Souhlas před prvním otevřením mini aplikace u bota. TDLib ho předepisuje: mini aplikace
 * dostane údaje o účtu a co v ní uživatel udělá, může dostat i bot. Ptá se jednou na bota.
 */
@Composable
internal fun WebAppConsentDialog(botName: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.webapp_consent_title)) },
        text = { Text(stringResource(Res.string.webapp_consent_text, botName)) },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(Res.string.webapp_consent_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

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
                TextButton(onClick = { onClose(button.id) }) {
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
