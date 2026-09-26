package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.PaddingValues
import eu.tintera.plume.resources.poll_add_option
import eu.tintera.plume.resources.add_24dp
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatPoll
import eu.tintera.plume.core.telegram.PollAnswer
import eu.tintera.plume.core.telegram.PollRestriction
import eu.tintera.plume.core.telegram.QuizMark
import eu.tintera.plume.core.telegram.quizMark
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.cancel_filled_24dp
import eu.tintera.plume.resources.check_box_filled_24dp
import eu.tintera.plume.resources.check_box_outline_blank_24dp
import eu.tintera.plume.resources.check_circle_filled_24dp
import eu.tintera.plume.resources.lightbulb_24dp
import eu.tintera.plume.resources.poll_answer_chosen
import eu.tintera.plume.resources.poll_answer_correct
import eu.tintera.plume.resources.poll_answer_wrong
import eu.tintera.plume.resources.poll_answers
import eu.tintera.plume.resources.poll_explanation
import eu.tintera.plume.resources.poll_kind_anonymous
import eu.tintera.plume.resources.poll_kind_anonymous_quiz
import eu.tintera.plume.resources.poll_kind_closed
import eu.tintera.plume.resources.poll_closes_at
import eu.tintera.plume.core.ui.asUpcomingTime
import kotlin.time.Clock
import eu.tintera.plume.resources.poll_kind_public
import eu.tintera.plume.resources.poll_kind_quiz
import eu.tintera.plume.resources.poll_multiple
import eu.tintera.plume.resources.poll_no_answers
import eu.tintera.plume.resources.poll_no_votes
import eu.tintera.plume.resources.poll_percent
import eu.tintera.plume.resources.poll_restricted_country
import eu.tintera.plume.resources.poll_restricted_membership
import eu.tintera.plume.resources.poll_restricted_other
import eu.tintera.plume.resources.poll_results_hidden
import eu.tintera.plume.resources.poll_show_voters
import eu.tintera.plume.resources.poll_subtitle_joined
import eu.tintera.plume.resources.poll_vote_button
import eu.tintera.plume.resources.poll_votes
import eu.tintera.plume.resources.radio_button_unchecked_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Anketa nebo kvíz v bublině: otázka, odpovědi k hlasování, a po hlasování (nebo uzavření)
 * výsledky s pruhy a procenty.
 *
 * Text zprávy ([ChatMessage.text], „Anketa: otázka") se tu NEKRESLÍ — je to jen popis pro místa,
 * kde se anketa kreslit nedá (citace, připnutá zpráva, náhled v seznamu chatů).
 */
@Immutable
internal data class PollMessageItem(
    override val frame: MessageFrameData,
    val poll: ChatPoll,
    val quoted: ChatMessage?,
    /** Koho citace uvede v soukromém chatu, kde TDLib jméno odesílatele nevyplňuje. */
    val partnerName: String?,
) : MessageItem {

    @Composable
    override fun MessageBodyScope.Body() {
        val quote = quoted ?: return Bubble { PollContent(poll) }
        QuotedBubble(
            quote = { ReplyQuote(quote, partnerName) },
            content = { PollContent(poll) },
        )
    }
}

/**
 * Obsah ankety. **Šířka je pevná, ne podle obsahu**: krátká otázka s krátkými odpověďmi by
 * udělala úzkou bublinu, ve které pruhy výsledků nejsou k ničemu — rozdíl mezi 30 a 40 %
 * na pár desítkách bodů není vidět.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageBodyScope.PollContent(poll: ChatPoll) {
    val haptics = LocalHapticFeedback.current
    val muted = onBubbleColor.copy(alpha = MUTED_ALPHA)
    /*
     * Rozepsaný výběr u ankety s víc odpověďmi — hlasuje se až tlačítkem. Patří zprávě, ne
     * obrazovce: odchází s ní, a přežije scroll i otočení (`rememberSaveable`).
     */
    var selection by rememberSaveable(message.id, stateSaver = IntSetSaver) { mutableStateOf(emptySet<Int>()) }
    // Pruhy vyjedou od nuly jen tehdy, když se výsledky odkryly PŘED OČIMA (hlas, uzavření) —
    // ne při každém složení položky při scrollu.
    val resultsAtStart = remember(message.id) { poll.showsResults }

    Column(Modifier.widthIn(max = POLL_MAX_WIDTH).fillMaxWidth()) {
        SenderName(background = bubbleColor)
        OriginLabel()

        poll.media?.let { PollQuestionMedia(it) }
        Text(
            text = poll.question,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = pollSubtitle(poll),
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (poll.description.isNotBlank()) {
            RichText(
                text = poll.description,
                entities = poll.descriptionEntities,
                style = MaterialTheme.typography.bodyMedium,
                linkColor = accent,
                onLinkClick = { act(MessageAction.OpenLink(it)) },
                onLongClick = ::requestMenu,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(ANSWER_SPACING),
            modifier = Modifier.padding(top = 10.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            poll.options.forEach { answer ->
                if (poll.showsResults) {
                    ResultRow(poll, answer, animateFromZero = !resultsAtStart)
                } else {
                    VoteRow(
                        poll = poll,
                        answer = answer,
                        selected = answer.index in selection,
                        onClick = {
                            if (poll.multipleAnswers) {
                                val on = answer.index !in selection
                                selection = if (on) selection + answer.index else selection - answer.index
                                haptics.toggle(on)
                            } else {
                                haptics.confirm()
                                act(MessageAction.Vote(listOf(answer.index)))
                            }
                        },
                    )
                }
            }
        }

        // Vlastní odpověď jde přidat, jen když to anketa dovolí (rozhoduje server). Řádek sedí
        // pod odpověďmi, kam nová přibude.
        if (poll.canAddOption) {
            TextButton(
                onClick = { act(MessageAction.AddPollOption) },
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.add_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                )
                Text(stringResource(Res.string.poll_add_option))
            }
        }

        if (poll.multipleAnswers && poll.canVote) {
            VoteButton(
                enabled = selection.isNotEmpty(),
                haptics = haptics,
                onClick = {
                    // Pořadí odpovědí v požadavku je jedno, ale stabilní se lépe čte v logu.
                    act(MessageAction.Vote(selection.sorted()))
                    selection = emptySet()
                },
            )
        }

        poll.restrictionText()?.let { reason ->
            Text(
                text = stringResource(reason),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Explanation(poll)
        Footer(poll, muted)
    }
}

/** „Anonymní kvíz · víc odpovědí", u uzavřené „Konečné výsledky". */
@Composable
private fun pollSubtitle(poll: ChatPoll): String {
    if (poll.isClosed) return stringResource(Res.string.poll_kind_closed)
    val kind = stringResource(
        when {
            poll.quiz != null && poll.isAnonymous -> Res.string.poll_kind_anonymous_quiz
            poll.quiz != null -> Res.string.poll_kind_quiz
            poll.isAnonymous -> Res.string.poll_kind_anonymous
            else -> Res.string.poll_kind_public
        },
    )
    val withMultiple = if (poll.multipleAnswers) {
        stringResource(Res.string.poll_subtitle_joined, kind, stringResource(Res.string.poll_multiple))
    } else {
        kind
    }
    // Konec jen dokud nenastal: po něm server anketu uzavře a podtitul řekne „Konečné výsledky".
    // Čas se nepřepočítává každou vteřinu — hodina a den stačí a uzavření přijde updatem.
    val closesAt = poll.closeDate.takeIf { it > nowSeconds() } ?: return withMultiple
    return stringResource(
        Res.string.poll_subtitle_joined,
        withMultiple,
        stringResource(Res.string.poll_closes_at, closesAt.toLong().asUpcomingTime()),
    )
}

private fun nowSeconds(): Long = Clock.System.now().epochSeconds

/**
 * Odpověď k hlasování: kolečko (jedna odpověď) nebo zaškrtávátko (víc) a text.
 *
 * Kde hlasovat nejde (omezení, anketa, která po hlasování schovává výsledky), řádek ťuknout
 * nejde a vlastní volba je označená fajfkou. Dlouhý stisk se opakuje — řádek má vlastní
 * klikání a bez toho by nad odpovědí nešla otevřít nabídka zprávy.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageBodyScope.VoteRow(
    poll: ChatPoll,
    answer: PollAnswer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val enabled = poll.canVote
    val chosenLabel = stringResource(Res.string.poll_answer_chosen)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ANSWER_MIN_HEIGHT)
            .clip(RoundedCornerShape(ANSWER_CORNER))
            // Zakázaný řádek NESMÍ být `enabled = false`: vzal by s sebou i dlouhý stisk a nad
            // odpovědí v anketě, kde se hlasovat nedá, by nešla otevřít nabídka zprávy.
            .combinedClickable(
                interactionSource = null,
                indication = if (enabled) ripple() else null,
                onLongClick = ::requestMenu,
                onClick = { if (enabled) onClick() },
            )
            .semantics {
                role = if (poll.multipleAnswers) Role.Checkbox else Role.RadioButton
                this.selected = selected || answer.isChosen
                if (answer.isChosen) contentDescription = chosenLabel
            }
            .padding(vertical = 6.dp),
    ) {
        Box(Modifier.size(ANSWER_ICON), contentAlignment = Alignment.Center) {
            when {
                answer.isBeingChosen -> LoadingIndicator(Modifier.size(ANSWER_ICON), color = accent)
                else -> Icon(
                    painter = painterResource(
                        when {
                            answer.isChosen -> Res.drawable.check_circle_filled_24dp
                            poll.multipleAnswers && selected -> Res.drawable.check_box_filled_24dp
                            poll.multipleAnswers -> Res.drawable.check_box_outline_blank_24dp
                            else -> Res.drawable.radio_button_unchecked_24dp
                        },
                    ),
                    contentDescription = null,
                    tint = if (enabled || answer.isChosen) accent else onBubbleColor.copy(alpha = MUTED_ALPHA),
                )
            }
        }
        answer.media?.let { AnswerMedia(it, answer.index, Modifier.padding(start = 12.dp)) }
        Text(
            text = answer.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Výsledek odpovědi: procenta, text, značka (vlastní volba, u kvízu správně / špatně) a pod tím
 * pruh.
 *
 * Pruh vlastní volby nese akcent, ostatní jen barvu popředí ztlumenou — je vidět, jak dopadla
 * ta, pro kterou uživatel hlasoval. U kvízu barvu nese výsledek: správná odpověď „úspěchem"
 * (terciární role, jako jinde v aplikaci), vybraná špatná chybovou. Na odchozí `primary`
 * bublině se obojí může potkat s bublinou na téže světlosti; pak ustoupí barvě popředí
 * ([accentOn]) a rozdíl nese ikona.
 */
@Composable
private fun MessageBodyScope.ResultRow(poll: ChatPoll, answer: PollAnswer, animateFromZero: Boolean) {
    val colors = MaterialTheme.colorScheme
    val mark = poll.quizMark(answer)
    val color = when (mark) {
        QuizMark.Correct -> accentOn(bubbleColor, colors.tertiary, onBubbleColor)
        QuizMark.Wrong -> accentOn(bubbleColor, colors.error, onBubbleColor)
        null -> if (answer.isChosen) accent else onBubbleColor.copy(alpha = RESULT_BAR_ALPHA)
    }
    val icon: Pair<DrawableResource, StringResource>? = when {
        mark == QuizMark.Correct -> Res.drawable.check_circle_filled_24dp to Res.string.poll_answer_correct
        mark == QuizMark.Wrong -> Res.drawable.cancel_filled_24dp to Res.string.poll_answer_wrong
        answer.isChosen -> Res.drawable.check_circle_filled_24dp to Res.string.poll_answer_chosen
        else -> null
    }
    val fraction = answer.percentage / 100f
    val progress = remember { Animatable(if (animateFromZero) 0f else fraction) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(fraction) { progress.animateTo(fraction, spec) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ANSWER_CORNER))
            // Ťuknutí nic nedělá, ale nabídka zprávy nad výsledkem otevřít jít musí.
            .combinedClickable(onLongClick = ::requestMenu, onClick = {})
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.poll_percent, answer.percentage),
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(PERCENT_WIDTH),
            )
            answer.media?.let { AnswerMedia(it, answer.index, Modifier.padding(start = 10.dp)) }
            Text(
                text = answer.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            icon?.let { (drawable, label) ->
                Icon(
                    painter = painterResource(drawable),
                    contentDescription = stringResource(label),
                    tint = if (mark == null) accent else color,
                    modifier = Modifier.padding(start = 8.dp).size(RESULT_ICON),
                )
            }
        }
        ResultBar(
            progress = { progress.value },
            color = color,
            track = onBubbleColor.copy(alpha = TRACK_ALPHA),
            modifier = Modifier.padding(start = PERCENT_WIDTH + 10.dp, top = 4.dp),
        )
    }
}

/**
 * Pruh výsledku. Délka se čte až v layoutu, takže animace nerekomponuje řádek. Nenulový výsledek
 * má aspoň výšku pruhu na délku (tečku) — jinak by jeden hlas ze stovky nebyl vidět vůbec.
 */
@Composable
private fun ResultBar(progress: () -> Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Layout(
        content = { Box(Modifier.clip(CircleShape).background(color)) },
        modifier = modifier
            .fillMaxWidth()
            .height(RESULT_BAR_HEIGHT)
            .clip(CircleShape)
            .background(track),
    ) { measurables, constraints ->
        val height = constraints.maxHeight
        val value = progress().coerceIn(0f, 1f)
        val width = if (value <= 0f) 0 else (constraints.maxWidth * value).toInt().coerceAtLeast(height)
        val fill = measurables.first().measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = height))
        layout(constraints.maxWidth, height) { fill.place(0, 0) }
    }
}

/** Hlasovat u ankety s víc odpověďmi. Barvy obráceně k bublině, jako zvýrazněné slovo. */
@Composable
private fun MessageBodyScope.VoteButton(enabled: Boolean, haptics: HapticFeedback, onClick: () -> Unit) {
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = bubbleColor,
            disabledContainerColor = onBubbleColor.copy(alpha = TRACK_ALPHA),
            disabledContentColor = onBubbleColor.copy(alpha = MUTED_ALPHA),
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Text(stringResource(Res.string.poll_vote_button))
    }
}

/**
 * Vysvětlení kvízu — server ho vydá až po odpovědi. Řádek s žárovkou, žádná vložená karta:
 * plocha v ploše bubliny je jazyk, kterého se aplikace vzdala (citace, náhled odkazu).
 */
@Composable
private fun MessageBodyScope.Explanation(poll: ChatPoll) {
    val quiz = poll.quiz ?: return
    if (!poll.showsResults || quiz.explanation.isBlank()) return
    val label = stringResource(Res.string.poll_explanation)
    Row(modifier = Modifier.padding(top = 10.dp)) {
        Icon(
            painter = painterResource(Res.drawable.lightbulb_24dp),
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        RichText(
            text = quiz.explanation,
            entities = quiz.explanationEntities,
            style = MaterialTheme.typography.bodyMedium,
            linkColor = accent,
            onLinkClick = { act(MessageAction.OpenLink(it)) },
            onLongClick = ::requestMenu,
        )
    }
}

/**
 * Počet hlasů a „Kdo hlasoval". Počet je vidět i před hlasováním — kolik lidí už hlasovalo,
 * server prozradí vždycky, jen ne jak.
 */
@Composable
private fun MessageBodyScope.Footer(poll: ChatPoll, muted: Color) {
    val count = poll.totalVoters
    val text = when {
        count == 0 -> stringResource(if (poll.quiz != null) Res.string.poll_no_answers else Res.string.poll_no_votes)
        poll.quiz != null -> pluralStringResource(Res.plurals.poll_answers, count, count.grouped())
        else -> pluralStringResource(Res.plurals.poll_votes, count, count.grouped())
    }
    val showVoters = poll.showsResults && poll.canGetVoters && count > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Výšku tlačítka drží řádek jen tehdy, když tlačítko je — jinak by pod samotným počtem
        // hlasů zůstala mezera, která vypadá jako chybějící obsah.
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showVoters) 4.dp else 10.dp)
            .then(if (showVoters) Modifier.heightIn(min = FOOTER_MIN_HEIGHT) else Modifier),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = text },
        )
        if (poll.hasVoted && !poll.canSeeResults) {
            Text(
                text = stringResource(Res.string.poll_results_hidden),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }
        if (showVoters) {
            TextButton(
                onClick = { act(MessageAction.ShowPollVoters) },
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
            ) {
                Text(stringResource(Res.string.poll_show_voters))
            }
        }
    }
}

private fun ChatPoll.restrictionText(): StringResource? = when (restriction) {
    // Uzavřená anketa to říká podtitulem, neodeslaná nic neříká — obojí je vidět jinak.
    null, PollRestriction.Closed, PollRestriction.NotSent -> null
    PollRestriction.MembershipRequired -> Res.string.poll_restricted_membership
    PollRestriction.CountryRestricted -> Res.string.poll_restricted_country
    PollRestriction.Other -> Res.string.poll_restricted_other
}.takeUnless { hasVoted }

private val IntSetSaver = listSaver<Set<Int>, Int>(save = { it.sorted() }, restore = { it.toSet() })

/** Víc by na rozloženém Foldu z ankety udělalo pruh přes půl obrazovky. */
private val POLL_MAX_WIDTH = 360.dp
private val ANSWER_MIN_HEIGHT = 44.dp
private val ANSWER_SPACING = 2.dp
private val ANSWER_CORNER = 12.dp
private val ANSWER_ICON = 24.dp
private val RESULT_ICON = 20.dp
private val PERCENT_WIDTH = 44.dp
private val RESULT_BAR_HEIGHT = 6.dp
private val FOOTER_MIN_HEIGHT = 40.dp
private const val MUTED_ALPHA = 0.7f
private const val TRACK_ALPHA = 0.12f
private const val RESULT_BAR_ALPHA = 0.45f
