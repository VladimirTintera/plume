package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.features.conversation.ContextFinding
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.message_context_event
import eu.tintera.plume.resources.message_context_maps
import eu.tintera.plume.resources.message_context_open
import eu.tintera.plume.resources.message_context_parcel
import eu.tintera.plume.resources.message_context_payment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Ikonka „s tím jde něco udělat" vedle zprávy, ve které se našel termín, účet nebo zásilka
 * (`findMessageContext`). Ťuknutí otevře sheet s akcemi (`MessageContextSheet`).
 *
 * - **Na volné straně zprávy u spodní hrany**, jako značka efektu ([EffectMark]): nic
 *   neposouvá a k bublině patří na první pohled. Do bubliny nepatří — je to nabídka aplikace,
 *   ne obsah od odesílatele, stejně jako varování před podvodem pod ní.
 * - **Terciární kontejner**: v M3 role pro doplňkový akcent, který nesoupeří s odchozí
 *   bublinou (`primary`) ani s výběrem (`secondaryContainer`).
 * - **Čtečka slyší, CO se našlo** („Přidat do kalendáře, QR platba"), ne jen „akce" — jinak by se
 *   musela do sheetu proťukat, aby zjistila, jestli o to stojí.
 */
@Composable
internal fun MessageContextMark(
    findings: List<ContextFinding>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = findings.map {
        when (it) {
            is ContextFinding.Event -> Res.string.message_context_event
            is ContextFinding.Payment -> Res.string.message_context_payment
            is ContextFinding.Address -> Res.string.message_context_maps
            is ContextFinding.Parcel -> Res.string.message_context_parcel
        }
    }.distinct().map { stringResource(it) }
    val description = stringResource(Res.string.message_context_open, kinds.joinToString(", "))
    Box(modifier.size(CONTEXT_MARK_SIZE), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .size(CONTEXT_MARK_SIZE)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(Res.drawable.auto_awesome_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(CONTEXT_MARK_ICON),
                )
            }
        }
    }
}

/** Velikost ikonky; o kus větší než značka efektu, je to tlačítko, ne štítek. */
internal val CONTEXT_MARK_SIZE = 28.dp

private val CONTEXT_MARK_ICON = 16.dp
