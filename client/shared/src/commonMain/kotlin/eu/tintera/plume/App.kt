package eu.tintera.plume

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.app.root.presentation.AppTheme
import eu.tintera.plume.app.root.presentation.supportsDynamicColor
import eu.tintera.plume.app.shell.AppShell
import eu.tintera.plume.core.notifications.NotificationPermissionPrompt
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.settings.ThemeMode
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.ui.forgetKnownImagePaths
import eu.tintera.plume.core.ui.forgetStickerAnimations
import eu.tintera.plume.features.auth.AuthScreen
import eu.tintera.plume.features.auth.AuthViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Kořen aplikace.
 *
 * Rozhodnutí „přihlašovací tok vs. aplikace" řídí stavový automat TDLib, ne navigační zásobník —
 * do přihlášení se nedá „vrátit zpět" a stav je vždycky jen jeden. Navigace uvnitř přihlášené
 * části je Navigation3, viz [AppShell].
 */
@Composable
fun App() {
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
    ) {
        Surface(Modifier.fillMaxSize()) {
            val authViewModel: AuthViewModel = koinViewModel()
            val stage by authViewModel.stage.collectAsStateWithLifecycle()

            // Přihlášená část je pryč z kompozice, takže je chvíle uklidit, co přežívá mimo ni.
            LaunchedEffect(stage is AuthStage.Ready) {
                if (stage !is AuthStage.Ready) {
                    forgetKnownImagePaths()
                    forgetStickerAnimations()
                }
            }

            AnimatedContent(
                targetState = stage is AuthStage.Ready,
                label = "root",
            ) { signedIn ->
                if (signedIn) {
                    AppShell()
                    // Až tady, ne nad přihlášením — tam uživatel ještě netuší, co aplikace dělá.
                    NotificationPermissionPrompt()
                } else {
                    AuthScreen(viewModel = authViewModel)
                }
            }
        }
    }
}
