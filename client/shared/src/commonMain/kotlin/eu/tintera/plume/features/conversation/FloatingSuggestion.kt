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
 * **Sklo je tatáž pilulka jako pruhy nad konverzací** ([pillGlass]: v tmavém motivu
 * `surfaceContainerHigh`, ve světlém bílá, světlá hrana, stín 3 dp; popředí [FloatingSuggestionColors]). Dřív byla v inverzní roli jako
 * snackbar, takže nad polem visela jiná plocha než nahoře u připnutých. Inverzní sklo vzniklo,
 * když se světlé sklo v barvě plochy (`surface`) ve světlém motivu slévalo s bublinami; pilulku
 * od nich teď odliší bílá plocha, hrana a stín, jako u pruhů nahoře.
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
            Box(Modifier.clip(shape).then(glass).glassRim(shape)) { content() }
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
 * Popředí na skle [FloatingSuggestion] — tytéž role jako na pruzích nad konverzací.
 */
internal object FloatingSuggestionColors {
    val content: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    val muted: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** Akcent (text příkazu, indikátor), jako popisek „Připnutá zpráva“. */
    val accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    /** `ListItem` si text barví sám, barva popředí se mu musí předat (plocha je sklo). */
    @Composable
    fun listItem(): ListItemColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = content,
        supportingContentColor = muted,
        leadingContentColor = content,
        trailingContentColor = content,
    )
}
