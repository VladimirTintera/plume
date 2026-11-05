package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/**
 * Napovídání při psaní, které PLAVE nad vstupním polem: emoji ke slovu, příkazy bota, zmínky
 * a výsledky inline bota.
 *
 * **Místo v rozvržení nezabírá** ([floatAbove]). Pole je `bottomBar` a seznam zpráv se odsazuje
 * o jeho výšku; dokud tahle napovídání stála v jeho sloupci, každé objevení, zmizení i změna
 * výšky (další písmeno zúží seznam příkazů) zvedly a spustily celou konverzaci a oči při psaní
 * skákaly za ní. Teď leží přes konec seznamu jako nabídka.
 *
 * **Sklo je v INVERZNÍ roli, jako snackbar** (`inverseSurface` s
 * [FLOATING_SUGGESTION_TINT_ALPHA], popředí [FloatingSuggestionColors]): ve světlém motivu tmavé,
 * v tmavém světlé. Je to dočasný prvek nad obsahem a má od něj vystoupit v obou motivech. Světlé
 * sklo v barvě plochy (první verze) se ve světlém motivu slévalo s bublinami; natvrdo tmavé
 * (druhá verze) by se stejně slilo s tmavou konverzací.
 *
 * Vyrůstá od pole ([origin]) zvětšením, ne rozbalením do výšky — rozbalení by u plovoucího
 * prvku vypadalo, jako by se pod ním něco odsouvalo.
 */
@Composable
internal fun FloatingSuggestion(
    visible: Boolean,
    shape: Shape,
    glass: Modifier,
    modifier: Modifier = Modifier,
    origin: TransformOrigin = TransformOrigin(0.5f, 1f),
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.9f, transformOrigin = origin) +
            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.9f, transformOrigin = origin) +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        // Barvu i rozostření nese sklo, plocha je průhledná (viz `glassStyle`). Tvar se proto
        // ořezává zvlášť, jinak by sklo přeteklo přes zaoblení.
        Surface(
            shape = shape,
            color = Color.Transparent,
            contentColor = FloatingSuggestionColors.content,
            shadowElevation = 3.dp,
            // Na šířku jako pole pod ní (`MessageInput`), ať pilulka nestojí vedle něj přesazená.
            modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(Modifier.clip(shape).then(glass)) { content() }
        }
    }
}

/**
 * Nahlásí nulovou výšku a obsah posadí NAD své místo — spodní hranou na něj.
 *
 * Rodič (sloupec nad polem v `bottomBar`) se tím nezvětší, takže se nezmění ani odsazení seznamu
 * zpráv. Obsah mimo hranice rodiče Compose kreslí i nechá ťukat, dokud nic neořezává.
 */
internal fun Modifier.floatAbove(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
}

/**
 * Popředí na skle [FloatingSuggestion] — inverzní role, jako snackbar.
 */
internal object FloatingSuggestionColors {
    val content: Color
        @Composable get() = MaterialTheme.colorScheme.inverseOnSurface

    val muted: Color
        @Composable get() = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f)

    /** Akcent (text příkazu, indikátor): `inversePrimary`, stejně jako akce snackbaru. */
    val accent: Color
        @Composable get() = MaterialTheme.colorScheme.inversePrimary

    /** `ListItem` si text barví sám (`onSurface`), barva popředí se mu musí předat. */
    @Composable
    fun listItem(): ListItemColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = content,
        supportingContentColor = muted,
        leadingContentColor = content,
        trailingContentColor = content,
    )
}
