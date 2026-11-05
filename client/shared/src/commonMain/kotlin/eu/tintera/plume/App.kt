package eu.tintera.plume

import eu.tintera.plume.features.notices.AccountNoticesHost
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.app.root.presentation.AppTheme
import eu.tintera.plume.app.root.presentation.supportsDynamicColor
import eu.tintera.plume.app.shell.AppShell
import eu.tintera.plume.core.lock.AppLock
import eu.tintera.plume.core.lock.AppLockState
import eu.tintera.plume.core.notifications.NotificationPermissionPrompt
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.settings.ThemeMode
import eu.tintera.plume.core.share.rememberReturnToCaller
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.AppIconBadgeColors
import eu.tintera.plume.core.ui.PrivacyCover
import eu.tintera.plume.core.ui.SecureScreen
import eu.tintera.plume.core.ui.forgetKnownImagePaths
import eu.tintera.plume.core.ui.forgetStickerAnimations
import eu.tintera.plume.features.auth.AuthScreen
import eu.tintera.plume.features.auth.AuthViewModel
import eu.tintera.plume.features.lock.LockScreen
import eu.tintera.plume.features.lock.LockWindow
import org.koin.compose.koinInject
import eu.tintera.plume.core.connectivity.AutoDownloadPolicy
import eu.tintera.plume.core.ui.LocalAutoDownload
import eu.tintera.plume.core.ui.LocalChatPreviewLines
import eu.tintera.plume.core.ui.LocalMessageTextSize
import eu.tintera.plume.core.ui.LocalStickerSize
import org.koin.compose.viewmodel.koinViewModel

/**
 * Obsah pod zámkem je zakrytý, ale pořád složený — bez tohohle by ho přečetla čtečka obrazovky
 * a fyzická klávesnice by do něj přesunula fokus (Tab) a psala do pole pod zámkem.
 */
private val Inert = Modifier
    .clearAndSetSemantics { }
    .focusProperties { onEnter = { cancelFocusChange() } }
    .focusGroup()

/**
 * Kořen aplikace.
 *
 * Rozhodnutí „přihlašovací tok vs. aplikace" řídí stavový automat TDLib, ne navigační zásobník —
 * do přihlášení se nedá „vrátit zpět" a stav je vždycky jen jeden. Navigace uvnitř přihlášené
 * části je Navigation3, viz [AppShell].
 */
@Composable
fun App() {
    AppRoot {
        AppShell()
        // Až tady, ne nad přihlášením — tam uživatel ještě netuší, co aplikace dělá.
        NotificationPermissionPrompt()
    }
}

/**
 * Motiv podle nastavení (režim, barvy z tapety, barva). Kromě [AppRoot] ho bere i samostatné
 * okno mimo hlavní (desktop: okénko příchozího hovoru), ať má tytéž barvy jako aplikace.
 */
@Composable
fun SettingsTheme(content: @Composable () -> Unit) {
    /*
     * Motiv se čte z nastavení, ne z výchozí hodnoty parametru. `null` znamená „uživatel si
     * nevybral" — teprve tehdy rozhoduje platforma (`supportsDynamicColor`), takže se do úložiště
     * nezapíše hodnota, která by na jiném zařízení znamenala něco jiného.
     */
    val settings = koinInject<AppSettings>()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val themeColor by settings.themeColor.collectAsStateWithLifecycle()
    AppTheme(
        themeMode = themeMode ?: ThemeMode.SYSTEM,
        dynamicColor = dynamicColor ?: supportsDynamicColor,
        themeColor = themeColor ?: ThemeColor.BLUE,
        content = content,
    )
}

/**
 * Společný kořen aplikace a bubliny konverzace (`BubbleShell`): motiv z nastavení, zámek
 * aplikace, přihlášení a hodnoty, které přihlášená část čte (`LocalAutoDownload`, velikost
 * písma). [content] je obsah přihlášené části — v aplikaci shell, v bublině jedna konverzace.
 * Zámek tak platí i v bublině: jinak by obešla PIN.
 */
@Composable
fun AppRoot(content: @Composable () -> Unit) {
    val settings = koinInject<AppSettings>()
    val messageTextSize by settings.messageTextSize.collectAsStateWithLifecycle()
    val chatPreviewLines by settings.chatPreviewLines.collectAsStateWithLifecycle()
    val stickerSize by settings.stickerSize.collectAsStateWithLifecycle()

    val appLock = koinInject<AppLock>()
    val lockSettings by appLock.settings.collectAsStateWithLifecycle()
    val lockState by appLock.state.collectAsStateWithLifecycle()

    // Skrytí obsahu v přehledu úloh: FLAG_SECURE se zapne, pokud je zámek aplikace zapnutý
    SecureScreen(enabled = lockSettings != null)

    SettingsTheme {
        Surface(Modifier.fillMaxSize()) {
            // Na iOS přehled úloh skrývá kryt nad oknem, FLAG_SECURE tam není.
            PrivacyCover(enabled = lockSettings != null, color = MaterialTheme.colorScheme.background)
            // Desktop: odznak nepřečtených na hlavním panelu v barvách motivu.
            AppIconBadgeColors()
            val authViewModel: AuthViewModel = koinViewModel()
            val stage by authViewModel.stage.collectAsStateWithLifecycle()

            // Přihlášená část je pryč z kompozice, takže je chvíle uklidit, co přežívá mimo ni.
            LaunchedEffect(stage is AuthStage.Ready) {
                if (stage !is AuthStage.Ready) {
                    forgetKnownImagePaths()
                    forgetStickerAnimations()
                }
            }

            // Jazykový balíček Telegramu podle jazyka aplikace — po přihlášení i po změně jazyka
            // (proces ji přežije, mění se jen konfigurace a s ní `Locale.current`).
            val session = koinInject<TelegramSession>()
            val language = androidx.compose.ui.text.intl.Locale.current.toLanguageTag()
            LaunchedEffect(stage is AuthStage.Ready, language) {
                if (stage is AuthStage.Ready) session.syncLanguagePack(language)
            }

            AnimatedContent(
                targetState = stage is AuthStage.Ready,
                label = "root",
            ) { signedIn ->
                if (signedIn) {
                    Box(Modifier.fillMaxSize()) {
                        val covered = lockState is AppLockState.Locked || lockState == AppLockState.Unknown
                        // Fokus, který už v obsahu je (rozepsaná zpráva), `Inert` nevyžene.
                        val focusManager = LocalFocusManager.current
                        LaunchedEffect(covered) { if (covered) focusManager.clearFocus(force = true) }
                        val autoDownload by koinInject<AutoDownloadPolicy>().limits.collectAsStateWithLifecycle()
                        Box(Modifier.fillMaxSize().then(if (covered) Inert else Modifier)) {
                            CompositionLocalProvider(
                                LocalAutoDownload provides autoDownload,
                                LocalMessageTextSize provides messageTextSize,
                                LocalChatPreviewLines provides chatPreviewLines,
                                LocalStickerSize provides stickerSize,
                            ) {
                                content()
                            }
                        }

                        // Dialogy od Telegramu (oznámení, podmínky) až nad odemčený obsah — pod
                        // zámkem by z nich byl vidět text, který zámek chrání.
                        if (!covered) AccountNoticesHost()

                        if (covered) {
                            // Kryt v hlavním okně: okno zámku se ukáže až s animací a během
                            // prvního čtení záznamu (Unknown) žádné není. Obsah pod ním se
                            // nesmí mihnout.
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {}
                        }
                        if (lockState is AppLockState.Locked) {
                            val returnToCaller = rememberReturnToCaller()
                            LockWindow(onBack = returnToCaller) {
                                LockScreen(appLock = appLock)
                            }
                        }
                    }
                } else {
                    AuthScreen(viewModel = authViewModel)
                }
            }
        }
    }
}

