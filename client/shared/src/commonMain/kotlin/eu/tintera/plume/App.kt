package eu.tintera.plume

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.app.root.presentation.AppTheme
import eu.tintera.plume.app.shell.AppShell
import eu.tintera.plume.core.notifications.NotificationPermissionPrompt
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.features.auth.AuthScreen
import eu.tintera.plume.features.auth.AuthViewModel
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
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            val authViewModel: AuthViewModel = koinViewModel()
            val stage by authViewModel.stage.collectAsStateWithLifecycle()

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
