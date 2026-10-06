package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.GiveawayPrize
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.flagEmoji
import eu.tintera.plume.core.telegram.localizedCountryName
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.features.conversation.StickerPreview
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.celebration_24dp
import eu.tintera.plume.resources.giveaway_countries
import eu.tintera.plume.resources.giveaway_details
import eu.tintera.plume.resources.giveaway_drawn_at
import eu.tintera.plume.resources.giveaway_draws_at
import eu.tintera.plume.resources.giveaway_extra
import eu.tintera.plume.resources.giveaway_more_channels
import eu.tintera.plume.resources.giveaway_only_new
import eu.tintera.plume.resources.giveaway_participants
import eu.tintera.plume.resources.giveaway_premium_months
import eu.tintera.plume.resources.giveaway_prize_premium
import eu.tintera.plume.resources.giveaway_prize_premium_plain
import eu.tintera.plume.resources.giveaway_prize_stars
import eu.tintera.plume.resources.giveaway_refunded
import eu.tintera.plume.resources.giveaway_results_title
import eu.tintera.plume.resources.giveaway_show_announcement
import eu.tintera.plume.resources.giveaway_stars_split
import eu.tintera.plume.resources.giveaway_title
import eu.tintera.plume.resources.giveaway_unclaimed
import eu.tintera.plume.resources.giveaway_winners
import eu.tintera.plume.resources.giveaway_winners_drawn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Soutěž v kanálu a její výsledky: výhra, kanály, podmínky a datum losování, u výsledků výherci.
 *
 * Kanály a výherci jsou samostatné čipy (otevřou chat); ťuknutí jinam do karty dostane bublina
 * a ukáže stav účasti (`ShowGiveaway`), stejně jako tlačítko Podrobnosti — to je tu proto, že
 * ťuknutí na bublinu by jinak nikdo nehledal. Zapojit se v soutěži nejde ničím jiným než
 * odebíráním kanálu, takže karta nic dalšího nenabízí.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GiveawayCard(giveaway: MessageMedia.Giveaway, accent: Color, onAction: (MessageAction) -> Unit) {
    val contentAccent = accent.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    val muted = LocalContentColor.current.copy(alpha = MUTED_ALPHA)
    val results = giveaway.results
    Column(
        modifier = Modifier.widthIn(min = CARD_MIN_WIDTH, max = CARD_MAX_WIDTH),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GiveawayHeader(giveaway, contentAccent)
        Text(
            text = stringResource(if (results == null) Res.string.giveaway_title else Res.string.giveaway_results_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PrizeLine(giveaway, contentAccent)
        if (giveaway.prizeDescription.isNotBlank()) {
            Text(
                text = stringResource(Res.string.giveaway_extra, giveaway.prizeDescription),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (giveaway.chats.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.giveaway_participants),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(top = 6.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                giveaway.chats.forEach { chat ->
                    GiveawayChip(chat.title, contentAccent) { onAction(MessageAction.OpenChat(chat.chatId)) }
                }
            }
            val more = results?.additionalChatCount ?: 0
            if (more > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.giveaway_more_channels, more, more),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }

        Conditions(giveaway, muted)

        if (results != null) {
            if (results.winners.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.giveaway_winners),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    results.winners.forEach { winner ->
                        // Výherce, kterého TDLib nezná, nemá jméno — čip bez jména by nic neřekl.
                        if (winner.name.isNotBlank()) {
                            GiveawayChip(winner.name, contentAccent) { onAction(MessageAction.OpenUser(winner.userId)) }
                        }
                    }
                }
            } else {
                Text(
                    text = pluralStringResource(Res.plurals.giveaway_winners_drawn, giveaway.winnerCount, giveaway.winnerCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (results.unclaimedCount > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.giveaway_unclaimed, results.unclaimedCount, results.unclaimedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            if (results.wasRefunded) {
                Text(
                    text = stringResource(Res.string.giveaway_refunded),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }

        // Pod sebou, ne vedle sebe: „Zobrazit soutěž" se do půlky karty nevešlo a zkrátilo se.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (results != null && results.giveawayMessageId != 0L) {
                CardButton(stringResource(Res.string.giveaway_show_announcement), contentAccent, Modifier.fillMaxWidth()) {
                    onAction(MessageAction.JumpTo(results.giveawayMessageId))
                }
            }
            CardButton(stringResource(Res.string.giveaway_details), contentAccent, Modifier.fillMaxWidth()) {
                onAction(MessageAction.ShowGiveaway)
            }
        }
    }
}

/** Samolepka dárku od Telegramu; u výsledků (ty ji nenesou) ikona oslavy v kolečku. */
@Composable
private fun GiveawayHeader(giveaway: MessageMedia.Giveaway, accent: Color) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val sticker = giveaway.sticker
        if (sticker != null) {
            StickerPreview(sticker = sticker, playing = true, modifier = Modifier.size(STICKER_SIZE))
        } else {
            Surface(shape = CircleShape, color = accent.copy(alpha = ICON_BACKDROP_ALPHA), contentColor = accent) {
                Icon(
                    painterResource(Res.drawable.celebration_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun PrizeLine(giveaway: MessageMedia.Giveaway, accent: Color) {
    val text = when (val prize = giveaway.prize) {
        is GiveawayPrize.Premium -> if (prize.months > 0) {
            stringResource(
                Res.string.giveaway_prize_premium,
                giveaway.winnerCount.grouped(),
                pluralStringResource(Res.plurals.giveaway_premium_months, prize.months, prize.months),
            )
        } else {
            stringResource(Res.string.giveaway_prize_premium_plain, giveaway.winnerCount.grouped())
        }
        is GiveawayPrize.Stars -> pluralStringResource(
            Res.plurals.giveaway_prize_stars,
            prize.count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            prize.count.grouped(),
        ) + "\n" + pluralStringResource(Res.plurals.giveaway_stars_split, giveaway.winnerCount, giveaway.winnerCount)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Kdo smí soutěžit a kdy se losuje — jedna řádka na podmínku, ztlumeně. */
@Composable
private fun Conditions(giveaway: MessageMedia.Giveaway, muted: Color) {
    val lines = buildList {
        if (giveaway.onlyNewMembers) add(stringResource(Res.string.giveaway_only_new))
        if (giveaway.countryCodes.isNotEmpty()) {
            val countries = giveaway.countryCodes.joinToString(", ") { code ->
                listOfNotNull(flagEmoji(code), localizedCountryName(code) ?: code).joinToString(" ")
            }
            add(stringResource(Res.string.giveaway_countries, countries))
        }
        if (giveaway.selectionDate > 0) {
            // Losování, které už mělo proběhnout, ale výsledky nedorazily, se hlásí jako minulé.
            val now = remember { Clock.System.now().epochSeconds }
            add(
                if (giveaway.results == null && giveaway.selectionDate > now) {
                    stringResource(Res.string.giveaway_draws_at, giveaway.selectionDate.asUpcomingTime())
                } else {
                    stringResource(Res.string.giveaway_drawn_at, giveaway.selectionDate.toInt().asDate())
                },
            )
        }
    }
    lines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

/**
 * Čip kanálu nebo výherce. Plocha je tónovaná barvou popředí bubliny, ne `primary` — v odchozí
 * `primary` bublině by zmizela (stejná past jako u citace).
 */
@Composable
private fun GiveawayChip(label: String, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = accent.copy(alpha = CHIP_ALPHA),
        contentColor = accent,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).widthIn(max = CHIP_MAX_WIDTH),
        )
    }
}

@Composable
private fun CardButton(label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(accent.copy(alpha = BORDER_ALPHA))),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val CARD_MIN_WIDTH = 240.dp
private val CARD_MAX_WIDTH = 320.dp
private val STICKER_SIZE = 112.dp
private val CHIP_MAX_WIDTH = 200.dp
private const val MUTED_ALPHA = 0.72f
private const val CHIP_ALPHA = 0.14f
private const val ICON_BACKDROP_ALPHA = 0.14f
private const val BORDER_ALPHA = 0.5f
