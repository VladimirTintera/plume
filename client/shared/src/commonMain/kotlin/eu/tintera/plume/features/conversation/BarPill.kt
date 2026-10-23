package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Jednořádkový pruh nad konverzací ve skladbě pruhu připnutých: pilulka ze skla, vlevo odznak
 * soustředný se zaoblením, nadpis v barvě akcentu, pod ním řádek textu a vpravo případně
 * tlačítko ([trailing]). Tak vypadají videochat, žádosti o vstup a oznámení o chatu
 * ([ChatNoticeBarCard]) — co plave nad zprávami a jen něco hlásí, má jeden tvar.
 *
 * Pruh proti spamu a reklama u bota mají víc řádků a tlačítek, takže zůstávají kartou
 * ([ChatBarSurface]).
 */
@Composable
internal fun BarPill(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    /**
     * Plovoucí pruh nad konverzací má odsazení od okrajů a stín; jako blok seznamu (seznam
     * témat) ne — tam ho drží odsazení seznamu jako každý jiný řádek.
     */
    floating: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        shadowElevation = if (floating) 3.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (floating) Modifier.padding(horizontal = 12.dp, vertical = 6.dp) else Modifier),
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .then(glass)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = BAR_PILL_HEIGHT)
                // Odznak soustředný se zaoblením pilulky, jako u připnutých.
                .padding(start = BAR_BADGE_INSET, end = if (trailing != null) 4.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
}

/** Ikona v kruhu na začátku [BarPill]. */
@Composable
internal fun BarBadge(
    icon: DrawableResource,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(color = container, shape = CircleShape, modifier = Modifier.size(BAR_BADGE_SIZE)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        }
    }
}

private val BAR_PILL_HEIGHT = 56.dp
private val BAR_BADGE_SIZE = 40.dp
private val BAR_BADGE_INSET = (BAR_PILL_HEIGHT - BAR_BADGE_SIZE) / 2
