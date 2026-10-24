package eu.tintera.plume.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import eu.tintera.plume.core.telegram.ChatPoll
import eu.tintera.plume.core.telegram.PollAnswer
import eu.tintera.plume.core.telegram.PollRestriction
import eu.tintera.plume.core.telegram.QuizMark
import eu.tintera.plume.core.telegram.quizMark
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.poll_kind_anonymous
import eu.tintera.plume.resources.poll_kind_anonymous_quiz
import eu.tintera.plume.resources.poll_kind_closed
import eu.tintera.plume.resources.poll_kind_public
import eu.tintera.plume.resources.poll_kind_quiz
import eu.tintera.plume.resources.poll_no_votes
import eu.tintera.plume.resources.poll_percent
import eu.tintera.plume.resources.poll_restricted_country
import eu.tintera.plume.resources.poll_restricted_membership
import eu.tintera.plume.resources.poll_restricted_other
import eu.tintera.plume.resources.poll_results_hidden
import eu.tintera.plume.resources.poll_vote_button
import eu.tintera.plume.resources.poll_votes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Anketa nebo kvíz v bublině. Ťuknout na odpověď je akce na pár vteřin, ke které se hodinky
 * hodí — proto hlasování, ne jen výsledky.
 *
 * - **Jedna odpověď**: ťuknutí rovnou hlasuje ([onVote]), bez potvrzení — jako na telefonu.
 * - **Víc odpovědí**: zaškrtávací tlačítka a pod nimi „Hlasovat".
 * - **Výsledky** (po hlasování nebo po skončení, [ChatPoll.showsResults]): pruh s procenty za
 *   textem odpovědi, vybraná s fajfkou; u kvízu správná v barvě `primary`, špatně vybraná v `error`.
 * - Nový stav (hlas, cizí hlasy, ukončení) přijde od TDLib jako upravená zpráva — tu konverzace
 *   už odebírá, takže se nic nepřepočítává tady.
 */
@Composable
fun MessagePoll(poll: ChatPoll, outgoing: Boolean, onVote: (List<Int>) -> Unit) {
    val onBubble = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(pollKind(poll), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(poll.question, style = MaterialTheme.typography.titleSmall, color = onBubble)
        when {
            poll.isVoting -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            poll.canVote && poll.multipleAnswers -> MultipleChoice(poll, onVote)
            // Obrysová tlačítka: tónová výplň má barvu příchozí bubliny a tlačítka v ní zmizela
            // (ověřeno na emulátoru, stejná past jako přehrát u hlasovky).
            poll.canVote -> poll.options.forEach { option ->
                OutlinedButton(
                    onClick = { onVote(listOf(option.index)) },
                    label = { Text(option.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            poll.showsResults -> poll.options.forEach { option -> ResultRow(poll, option, onBubble) }
            else -> poll.options.forEach { option ->
                Text("• ${option.text}", style = MaterialTheme.typography.bodySmall, color = onBubble)
            }
        }
        restrictionText(poll)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (poll.totalVoters > 0) pluralStringResource(Res.plurals.poll_votes, poll.totalVoters, poll.totalVoters)
            else stringResource(Res.string.poll_no_votes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MultipleChoice(poll: ChatPoll, onVote: (List<Int>) -> Unit) {
    var selected by remember(poll.question, poll.options.size) { mutableStateOf(emptySet<Int>()) }
    poll.options.forEach { option ->
        CheckboxButton(
            checked = option.index in selected,
            onCheckedChange = { checked -> selected = if (checked) selected + option.index else selected - option.index },
            label = { Text(option.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(
        onClick = { onVote(selected.sorted()) },
        enabled = selected.isNotEmpty(),
        label = { Text(stringResource(Res.string.poll_vote_button)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Odpověď s výsledkem: pruh za textem v šířce procent (kreslený `drawBehind`, ne prvek — výška
 * řádku je výška textu), procenta vpravo, vybraná s fajfkou.
 */
@Composable
private fun ResultRow(poll: ChatPoll, option: PollAnswer, onBubble: Color) {
    val mark = poll.quizMark(option)
    val barColor = when (mark) {
        QuizMark.Correct -> MaterialTheme.colorScheme.primary
        QuizMark.Wrong -> MaterialTheme.colorScheme.error
        null -> onBubble
    }.copy(alpha = 0.25f)
    val fraction = option.percentage.coerceIn(0, 100) / 100f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .drawBehind { drawRect(barColor, size = Size(size.width * fraction, size.height)) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (option.isChosen) "✓ ${option.text}" else option.text,
            style = MaterialTheme.typography.bodySmall,
            color = onBubble,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Text(stringResource(Res.string.poll_percent, option.percentage), style = MaterialTheme.typography.labelSmall, color = onBubble)
    }
}

@Composable
private fun pollKind(poll: ChatPoll): String = stringResource(
    when {
        poll.isClosed -> Res.string.poll_kind_closed
        poll.quiz != null && poll.isAnonymous -> Res.string.poll_kind_anonymous_quiz
        poll.quiz != null -> Res.string.poll_kind_quiz
        poll.isAnonymous -> Res.string.poll_kind_anonymous
        else -> Res.string.poll_kind_public
    },
)

/** Proč hlasovat nejde (omezení serveru), nebo že výsledky přijdou až po skončení. */
@Composable
private fun restrictionText(poll: ChatPoll): String? = when {
    // Uzavřenou ukazuje druh ankety, neodeslanou (vlastní odesílanou) nic — tam se hlasovat nečeká.
    poll.restriction != null && poll.restriction != PollRestriction.Closed &&
        poll.restriction != PollRestriction.NotSent && !poll.hasVoted && !poll.isClosed -> stringResource(
        when (poll.restriction) {
            PollRestriction.MembershipRequired -> Res.string.poll_restricted_membership
            PollRestriction.CountryRestricted -> Res.string.poll_restricted_country
            else -> Res.string.poll_restricted_other
        },
    )
    poll.hasVoted && !poll.canSeeResults -> stringResource(Res.string.poll_results_hidden)
    else -> null
}
