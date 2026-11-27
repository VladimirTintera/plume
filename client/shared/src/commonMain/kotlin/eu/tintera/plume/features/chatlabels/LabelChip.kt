package eu.tintera.plume.features.chatlabels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.app.root.presentation.LocalIsDarkTheme
import eu.tintera.plume.app.root.presentation.theme.chatColorScheme
import eu.tintera.plume.app.root.presentation.theme.seed
import eu.tintera.plume.core.chatlabels.ChatLabel
import eu.tintera.plume.core.settings.ThemeColor

/**
 * Štítek jako čip v řádku chatu: název na ploše `secondaryContainer` schématu jeho barvy (jako
 * barva chatu, `chatColorScheme`), ne natvrdo hex. Dvojice plocha + popředí je z téhož schématu,
 * takže je čitelná ve světlém i tmavém motivu.
 */
@Composable
internal fun LabelChip(label: ChatLabel, modifier: Modifier = Modifier) {
    val scheme = labelScheme(label.color)
    Box(
        modifier
            .clip(ShapeDefaults.ExtraSmall)
            .background(scheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            label.name,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Barevná tečka štítku (v seznamu štítků vedle názvu). */
@Composable
internal fun LabelDot(color: ThemeColor, modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp).clip(CircleShape).background(labelScheme(color).primary))
}

@Composable
internal fun labelScheme(color: ThemeColor) = chatColorScheme(LocalIsDarkTheme.current, color.seed)
