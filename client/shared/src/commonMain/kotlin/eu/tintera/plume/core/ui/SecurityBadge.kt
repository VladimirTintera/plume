package eu.tintera.plume.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * **Odznak zabezpečení** nad nadpisem obrazovky: zámek aplikace, obrazovka zámku, dvoufázové
 * ověření. Tvar `Gem` (bez morfu) jako štít — dřív tu byl kruh, tedy tvar bez výrazu, a každá
 * ze tří obrazovek měla jinou velikost i vlastní kopii. `Gem` jinde nic neznamená (viz
 * [ShapeMorphs]), takže si tím nic neplete. Ikona je dekorace: co obrazovka chce, říká nadpis.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecurityBadge(icon: DrawableResource, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Gem.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size / 2),
        )
    }
}
