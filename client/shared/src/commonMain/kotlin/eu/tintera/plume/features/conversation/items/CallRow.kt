package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.background
import androidx.compose.material3.ShapeDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.CallOutcome
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.isFailure
import eu.tintera.plume.core.telegram.title
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_made_24dp
import eu.tintera.plume.resources.call_missed_24dp
import eu.tintera.plume.resources.call_missed_outgoing_24dp
import eu.tintera.plume.resources.call_received_24dp
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.resources.call_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zpráva o hovoru: kolečko se šipkou směru, pod názvem délka hovoru.
 *
 * Stejná stavba jako řádek souboru (kolečko v akcentu bubliny, text vedle), aby se v konverzaci
 * nevymýšlel další tvar. **Ťuknutí zavolá zpátky** ([onCallBack]), dlouhý stisk otevře nabídku
 * nad zprávou. Kde hovory nejsou (iOS, desktop), je [onCallBack] `null` a ťuknutí i dlouhý
 * stisk dostane bublina jako u textu — řádek by sliboval něco, co se nestane.
 *
 * Zmeškaný a odmítnutý hovor má šipku v chybové barvě: je to jediný stav, který po uživateli
 * něco chce (zavolat zpátky). Barva je jen doplněk — název ho říká slovy.
 */
@Composable
internal fun CallRow(call: MessageMedia.Call, accent: Color, onCallBack: (() -> Unit)? = null, onRequestMenu: () -> Unit = {}) {
    val contentAccent = accent.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    val iconTint = if (call.outcome.isFailure) {
        // Zmeškaný i odmítnutý je vždycky příchozí, tedy na světlé bublině příchozí zprávy.
        // Kontrast se přesto ověřuje — v paletě se zvýšeným kontrastem nemusí `error` vyjít.
        accentOn(
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
            accent = MaterialTheme.colorScheme.error,
            fallback = contentAccent,
        )
    } else {
        contentAccent
    }

    val callBackLabel = stringResource(Res.string.call_back)
    Row(
        modifier = Modifier
            .then(
                if (onCallBack != null) {
                    Modifier
                        .clip(ShapeDefaults.Medium)
                        .contextClickable(onClick = onCallBack, onLongClick = onRequestMenu, onClickLabel = callBackLabel)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(CALL_ICON_CONTAINER)
                .clip(CircleShape)
                .background(contentAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(call.outcome.icon()),
                contentDescription = null,
                tint = iconTint,
            )
        }
        Column {
            Text(
                text = stringResource(call.outcome.title(call.isVideo, call.isGroup, call.isActive)),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (call.durationSeconds > 0) {
                Text(
                    text = formatDuration(call.durationSeconds),
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                )
            }
        }
    }
}

/** Šipka ven u odchozího, dovnitř u příchozího; zalomená, když se nikdo nedovolal. */
internal fun CallOutcome.icon() = when (this) {
    CallOutcome.Outgoing -> Res.drawable.call_made_24dp
    CallOutcome.Incoming -> Res.drawable.call_received_24dp
    CallOutcome.Missed, CallOutcome.Declined -> Res.drawable.call_missed_24dp
    CallOutcome.Cancelled -> Res.drawable.call_missed_outgoing_24dp
}

/** Jako kolečko u souboru — oba řádky stojí v konverzaci vedle sebe. */
private val CALL_ICON_CONTAINER = 48.dp
