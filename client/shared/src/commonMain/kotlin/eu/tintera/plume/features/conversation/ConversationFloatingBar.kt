package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Plovoucí lišta na spodku konverzace: místo vstupního pole při výběru zpráv (akce nad výběrem)
 * a při hledání (pozice ve výsledcích a šipky mezi nimi).
 *
 * **Jeden kus pro obě použití.** Byly to dvě kopie téhož `Box` + `AnimatedVisibility` +
 * toolbaru a první doladění odsazení by je rozešlo — rozdílná výška slotu přitom znamená, že
 * se spodek obrazovky mezi režimy hýbe.
 *
 * **Plovoucí pilulka, ne pruh přes celou šířku.** `surfaceContainer` na ploše stránky je
 * v tmavém tématu rozdíl na hranici rozlišitelnosti, takže z celé lišty byly vidět jen ikony
 * zavěšené v prázdnu. Sytá plovoucí plocha (`vibrantFloatingToolbarColors`) se od pozadí odliší
 * sama a je to tentýž prvek jako toolbar filtrů v seznamu chatů.
 *
 * **Naskočit smí, protože nemá v layoutu pevné místo** — výšku drží `Box` kolem ní, takže se
 * seznam zpráv pod ní nepřeskládá, ať je lišta vidět, nebo ne.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConversationFloatingBar(
    visible: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .height(FLOATING_BAR_HEIGHT)
            .padding(bottom = FLOATING_BAR_CLEARANCE),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.8f),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                content = content,
            )
        }
    }
}

/**
 * Odstup od spodku obrazovky. O 8 dp víc než `FloatingToolbarDefaults.ScreenOffset` (16 dp):
 * nad klávesnicí, která z insetu spolkne i gesture bar, jich 16 dp vizuálně zbývá sotva 10
 * a lišta se o klávesnici opírá.
 */
private val FLOATING_BAR_CLEARANCE = 24.dp

/** Slot, který si lišta bere ve `bottomBar`: pilulka (`FloatingToolbarDefaults.ContainerSize`) a odstup. */
private val FLOATING_BAR_HEIGHT = 64.dp + FLOATING_BAR_CLEARANCE
