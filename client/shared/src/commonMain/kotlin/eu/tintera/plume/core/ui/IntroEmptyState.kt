package eu.tintera.plume.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource

/**
 * Prázdný stav s úvodní animací: uvítací tvar ([IntroShape]) a pod ním text, který naskočí za
 * ním. Běží při každém zobrazení a pak se nic nehýbe — prázdný stav, který pořád pulzuje,
 * by táhl pozornost od toho, co se na obrazovce dá dělat.
 *
 * Sdílí ho placeholder detailu konverzace a prázdný archiv — jeden vzhled pro „tady nic není",
 * ať se dva prázdné stavy vedle sebe nepřou. Samotný tvar sdílí i přihlášení.
 *
 * Plocha je `surface` jako konverzace i seznam, takže výměna za obsah nemění pozadí.
 */
@Composable
fun IntroEmptyState(
    icon: DrawableResource,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    /** Plocha pod stavem. Výchozí `surface` sedí na konverzaci a seznam; hledání má vlastní. */
    containerColor: Color = MaterialTheme.colorScheme.surface,
    /** Tlačítko pod textem, když stav nabízí cestu dál (varování před citlivým chatem). */
    action: (@Composable () -> Unit)? = null,
) {
    val textReveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(TEXT_DELAY_MILLIS)
        textReveal.animateTo(1f, tween(TEXT_MILLIS, easing = EmphasizedDecelerate))
    }

    Surface(modifier = modifier.fillMaxSize(), color = containerColor) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                IntroShape {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(INTRO_ICON_SIZE),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = textReveal.value
                        translationY = (1f - textReveal.value) * TEXT_RISE.toPx()
                    },
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (action != null) Box(Modifier.padding(top = 16.dp)) { action() }
                }
            }
        }
    }
}

private val TEXT_RISE = 16.dp

private const val TEXT_DELAY_MILLIS = 650L
private const val TEXT_MILLIS = 500
