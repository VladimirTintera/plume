package eu.tintera.plume.features.chatinsights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.PaneBody
import eu.tintera.plume.core.adaptive.paneScaffoldColor
import eu.tintera.plume.core.telegram.ReactionCount
import eu.tintera.plume.core.telegram.SharedMediaCounts
import eu.tintera.plume.core.telegram.StatChart
import eu.tintera.plume.core.telegram.StatChartKind
import eu.tintera.plume.core.telegram.StatSeries
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.CustomEmojiIcon
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.onName
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.shortName
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.profile.ProfileSubpageTopBar
import eu.tintera.plume.features.profile.profileSubpageScroll
import eu.tintera.plume.features.profile.profileSubpageScrollBehavior
import eu.tintera.plume.features.profile.rememberChatTitle
import eu.tintera.plume.features.statistics.SEPARATOR
import eu.tintera.plume.features.statistics.StatChartCard
import eu.tintera.plume.features.statistics.StatSectionTitle
import eu.tintera.plume.features.statistics.StatTile
import eu.tintera.plume.features.statistics.StatTiles
import eu.tintera.plume.features.statistics.percentText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bar_chart_24dp
import eu.tintera.plume.resources.chat_insights_active_days
import eu.tintera.plume.resources.chat_insights_busiest_day
import eu.tintera.plume.resources.chat_insights_by_month
import eu.tintera.plume.resources.chat_insights_content
import eu.tintera.plume.resources.chat_insights_current_streak
import eu.tintera.plume.resources.chat_insights_days
import eu.tintera.plume.resources.chat_insights_empty
import eu.tintera.plume.resources.chat_insights_empty_text
import eu.tintera.plume.resources.chat_insights_files
import eu.tintera.plume.resources.chat_insights_gifs
import eu.tintera.plume.resources.chat_insights_hours
import eu.tintera.plume.resources.chat_insights_hours_minutes
import eu.tintera.plume.resources.chat_insights_links
import eu.tintera.plume.resources.chat_insights_load_older
import eu.tintera.plume.resources.chat_insights_local_note
import eu.tintera.plume.resources.chat_insights_longest_streak
import eu.tintera.plume.resources.chat_insights_media
import eu.tintera.plume.resources.chat_insights_messages
import eu.tintera.plume.resources.chat_insights_minutes
import eu.tintera.plume.resources.chat_insights_music
import eu.tintera.plume.resources.chat_insights_older_failed
import eu.tintera.plume.resources.chat_insights_others
import eu.tintera.plume.resources.chat_insights_overview
import eu.tintera.plume.resources.chat_insights_peak
import eu.tintera.plume.resources.chat_insights_per_day
import eu.tintera.plume.resources.chat_insights_period
import eu.tintera.plume.resources.chat_insights_period_all
import eu.tintera.plume.resources.chat_insights_range
import eu.tintera.plume.resources.chat_insights_reactions
import eu.tintera.plume.resources.chat_insights_replies
import eu.tintera.plume.resources.chat_insights_replies_note
import eu.tintera.plume.resources.chat_insights_reply_time_them
import eu.tintera.plume.resources.chat_insights_reply_time_you
import eu.tintera.plume.resources.chat_insights_scanning
import eu.tintera.plume.resources.chat_insights_senders
import eu.tintera.plume.resources.chat_insights_series_messages
import eu.tintera.plume.resources.chat_insights_starts_them
import eu.tintera.plume.resources.chat_insights_starts_you
import eu.tintera.plume.resources.chat_insights_title
import eu.tintera.plume.resources.chat_insights_under_minute
import eu.tintera.plume.resources.chat_insights_video_notes
import eu.tintera.plume.resources.chat_insights_voice
import eu.tintera.plume.resources.chat_insights_when
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.empty_state_error_title
import eu.tintera.plume.resources.history_24dp
import eu.tintera.plume.resources.sender_you
import eu.tintera.plume.resources.statistics_sender_messages
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * **Statistika chatu** — co Telegram nepočítá: přehled, kdo kolik píše, v soukromém chatu jak
 * rychle kdo odpovídá a kdo začíná, kdy se píše (týden × hodina), zprávy po měsících, reakce
 * a počty obsahu za celou historii. Nahoře je, z čeho je spočtená, a „Načíst starší".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatInsightsScreen(
    viewModel: ChatInsightsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subpageScroll = profileSubpageScrollBehavior()
    Scaffold(
        containerColor = paneScaffoldColor(),
        modifier = modifier.profileSubpageScroll(subpageScroll),
        topBar = {
            ProfileSubpageTopBar(
                title = stringResource(Res.string.chat_insights_title),
                subtitle = rememberChatTitle(viewModel.chatId),
                onBack = onBack,
                scrollBehavior = subpageScroll,
            )
        },
    ) { scaffoldPadding ->
        PaneBody(scaffoldPadding) { padding ->
            val insights = state.insights
            val failure = state.failure
            when {
                failure != null -> IntroEmptyState(
                    icon = Res.drawable.bar_chart_24dp,
                    title = stringResource(Res.string.empty_state_error_title),
                    text = failure.resolve(),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    action = {
                        FilledTonalButton(onClick = viewModel::load, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.common_try_again))
                        }
                    },
                )
                insights == null || (insights.isEmpty && (state.scanning || state.loadingOlder)) ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                insights.isEmpty -> IntroEmptyState(
                    icon = Res.drawable.bar_chart_24dp,
                    title = stringResource(Res.string.chat_insights_empty),
                    text = stringResource(Res.string.chat_insights_empty_text),
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "scope") { ScopeHeader(state, insights, viewModel::loadOlder) }
                    item(key = "overview-title") { StatSectionTitle(stringResource(Res.string.chat_insights_overview)) }
                    item(key = "overview") { StatTiles(overviewTiles(insights), Modifier.padding(horizontal = 16.dp)) }
                    senderItems(insights, state)
                    insights.replies?.let { replies -> replyItems(replies, partnerName(insights, state)) }
                    item(key = "when-title") { StatSectionTitle(stringResource(Res.string.chat_insights_when)) }
                    item(key = "when") { WeekHeatmap(insights, Modifier.padding(horizontal = 16.dp)) }
                    if (insights.months.size >= 2) {
                        item(key = "months") { MonthChart(insights, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
                    }
                    if (insights.reactions.isNotEmpty()) {
                        item(key = "reactions-title") { StatSectionTitle(stringResource(Res.string.chat_insights_reactions)) }
                        item(key = "reactions") { ReactionRow(insights.reactions, Modifier.padding(horizontal = 16.dp)) }
                    }
                    state.mediaCounts?.let { counts ->
                        if (contentCounts(counts).isNotEmpty()) {
                            item(key = "content-title") { StatSectionTitle(stringResource(Res.string.chat_insights_content)) }
                            item(key = "content") { StatTiles(contentTiles(counts), Modifier.padding(horizontal = 16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/** Z čeho je statistika spočtená, a jak dostat víc. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScopeHeader(state: ChatInsightsState, insights: ChatInsights, onLoadOlder: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
        val count = compactCountText(insights.messageCount)
        val scope = if (state.reachedStart) {
            pluralStringResource(Res.plurals.chat_insights_period_all, insights.messageCount, count)
        } else {
            pluralStringResource(Res.plurals.chat_insights_period, insights.messageCount, count, insights.oldestTimestamp?.asDate().orEmpty())
        }
        Text(scope, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.scanning || state.loadingOlder -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.height(32.dp).width(32.dp))
                Text(
                    stringResource(Res.string.chat_insights_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            !state.reachedStart -> {
                Text(
                    stringResource(if (state.olderFailed) Res.string.chat_insights_older_failed else Res.string.chat_insights_local_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.olderFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Tónové tlačítko s ikonou, ne TextButton: textové má vnitřní odsazení, takže jeho
                // popisek stál o kus vpravo od textu nad ním a vypadalo to jako chyba zarovnání.
                FilledTonalButton(
                    onClick = onLoadOlder,
                    shapes = ButtonDefaults.shapes(),
                    enabled = state.canLoadOlder,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(painterResource(Res.drawable.history_24dp), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.chat_insights_load_older))
                }
            }
        }
    }
}

@Composable
private fun overviewTiles(insights: ChatInsights): List<StatTile> = buildList {
    add(StatTile(stringResource(Res.string.chat_insights_messages), compactCountText(insights.messageCount)))
    add(StatTile(stringResource(Res.string.chat_insights_active_days), compactCountText(insights.activeDays)))
    if (insights.activeDays > 0) {
        val perDay = insights.messageCount.toDouble() / insights.activeDays
        add(StatTile(stringResource(Res.string.chat_insights_per_day), decimalText(perDay)))
    }
    insights.busiestDay?.let { day ->
        add(StatTile(stringResource(Res.string.chat_insights_busiest_day), compactCountText(day.messages), growth = day.day.asDateText()))
    }
    insights.longestStreak?.let { streak ->
        add(
            StatTile(
                stringResource(Res.string.chat_insights_longest_streak),
                pluralStringResource(Res.plurals.chat_insights_days, streak.days, streak.days),
                growth = stringResource(Res.string.chat_insights_range, streak.start.asDateText(), streak.end.asDateText()),
            ),
        )
    }
    if (insights.currentStreak >= 2) {
        add(
            StatTile(
                stringResource(Res.string.chat_insights_current_streak),
                pluralStringResource(Res.plurals.chat_insights_days, insights.currentStreak, insights.currentStreak),
            ),
        )
    }
}

/** Žebříček „Kdo píše": jen když píše víc než jeden (kanál ho nemá). */
private fun LazyListScope.senderItems(insights: ChatInsights, state: ChatInsightsState) {
    if (insights.senders.size < 2) return
    val shown = insights.senders.take(MAX_SENDERS)
    val rest = insights.senders.drop(MAX_SENDERS)
    val rows = shown.size + if (rest.isEmpty()) 0 else 1
    item(key = "senders-title") { StatSectionTitle(stringResource(Res.string.chat_insights_senders)) }
    itemsIndexed(shown, key = { _, sender -> "sender-${sender.senderId}" }) { index, sender ->
        val info = state.senders[sender.senderId]
        val name = if (sender.isOutgoing) stringResource(Res.string.sender_you) else info?.title.orEmpty()
        SenderRow(
            index = index,
            count = rows,
            avatar = { Avatar(chatId = sender.senderId, title = info?.title.orEmpty(), avatarFileId = info?.avatarFileId, size = 40.dp) },
            name = name,
            messages = sender.messages,
            share = sender.messages.toFloat() / insights.messageCount,
        )
    }
    if (rest.isNotEmpty()) {
        item(key = "senders-rest") {
            val messages = rest.sumOf { it.messages }
            SenderRow(
                index = rows - 1,
                count = rows,
                avatar = { Spacer(Modifier.width(40.dp)) },
                name = pluralStringResource(Res.plurals.chat_insights_others, rest.size, rest.size),
                messages = messages,
                share = messages.toFloat() / insights.messageCount,
            )
        }
    }
}

@Composable
private fun SenderRow(
    index: Int,
    count: Int,
    avatar: @Composable () -> Unit,
    name: String,
    messages: Int,
    share: Float,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .groupedItem(index, count, MaterialTheme.colorScheme.groupRow, pressable = false)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    listOf(
                        pluralStringResource(Res.plurals.statistics_sender_messages, messages, messages),
                        shareText(share),
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            LinearProgressIndicator(
                progress = { share },
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** Soukromý chat: doba odpovědi a kdo začíná, pro obě strany. */
private fun LazyListScope.replyItems(replies: ReplyHabits, partner: String) {
    item(key = "replies-title") { StatSectionTitle(stringResource(Res.string.chat_insights_replies)) }
    item(key = "replies") {
        val starts = replies.mine.starts + replies.theirs.starts
        // Vlevo vždycky vy, vpravo protějšek: chybějící údaj je pomlčka, ne díra, kterou by
        // dlaždice pod ním zaplnily a strany by se přestaly krýt (viděno na emulátoru u bota,
        // který na zprávy neodpovídá).
        val tiles = buildList {
            if (replies.mine.medianReplySeconds != null || replies.theirs.medianReplySeconds != null) {
                add(StatTile(stringResource(Res.string.chat_insights_reply_time_you), replies.mine.medianReplySeconds?.let { durationText(it) } ?: NO_VALUE))
                add(StatTile(stringResource(Res.string.chat_insights_reply_time_them, partner), replies.theirs.medianReplySeconds?.let { durationText(it) } ?: NO_VALUE))
            }
            if (starts > 0) {
                add(StatTile(stringResource(Res.string.chat_insights_starts_you), shareText(replies.mine.starts.toFloat() / starts)))
                add(StatTile(stringResource(Res.string.chat_insights_starts_them, partner), shareText(replies.theirs.starts.toFloat() / starts)))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (tiles.isNotEmpty()) StatTiles(tiles)
            Text(
                stringResource(Res.string.chat_insights_replies_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Jméno druhé strany soukromého chatu (jediný cizí odesílatel). */
private fun partnerName(insights: ChatInsights, state: ChatInsightsState): String =
    insights.senders.firstOrNull { !it.isOutgoing }?.let { state.senders[it.senderId]?.title }.orEmpty()

/**
 * Týden × hodina: sedm řádků (pondělí nahoře), dvacet čtyři sloupců, sytost buňky proti
 * nejrušnější hodině týdne. Pod mřížkou hodiny po šesti, nad ní věta s nejrušnější chvílí —
 * ta nese obsah i pro čtečku obrazovky (mřížka sama je pro ni jen obrázek).
 */
@Composable
private fun WeekHeatmap(insights: ChatInsights, modifier: Modifier = Modifier) {
    val max = remember(insights.heatmap) { insights.heatmap.maxOf { it.max() }.coerceAtLeast(1) }
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val full = MaterialTheme.colorScheme.primary
    val peakText = insights.peak?.let { (day, hour) ->
        stringResource(Res.string.chat_insights_peak, stringResource(DayOfWeek.entries[day].onName()), "${hour.pad()}:00")
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GROUP_CORNER))
            .background(MaterialTheme.colorScheme.groupRow)
            .padding(16.dp),
    ) {
        peakText?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp)) }
        Row {
            Column(Modifier.padding(end = 8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    Box(Modifier.height(CELL_ROW), contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(day.shortName()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(CELL_ROW * DayOfWeek.entries.size)
                        .semantics { contentDescription = peakText.orEmpty() },
                ) {
                    val gap = 2.dp.toPx()
                    val cellWidth = (size.width - gap * 23) / 24
                    val cellHeight = CELL_ROW.toPx() - gap
                    insights.heatmap.forEachIndexed { day, hours ->
                        hours.forEachIndexed { hour, count ->
                            val level = if (count == 0) 0f else 0.15f + 0.85f * count / max
                            drawRoundRect(
                                color = lerp(empty, full, level),
                                topLeft = Offset(hour * (cellWidth + gap), day * CELL_ROW.toPx() + gap / 2),
                                size = Size(cellWidth, cellHeight),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf(0, 6, 12, 18).forEach { hour ->
                        Text(
                            hour.pad(),
                            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Zprávy po měsících sloupcovým grafem ze statistik kanálu (výběr bodu, popisky). */
@Composable
private fun MonthChart(insights: ChatInsights, modifier: Modifier = Modifier) {
    val seriesName = stringResource(Res.string.chat_insights_series_messages)
    val chart = remember(insights.months, seriesName) {
        StatChart(
            // Začátky měsíců v UTC: karta grafu popisuje body v UTC, takže se měsíc neposune.
            x = insights.months.map { LocalDate(it.month.year, it.month.month, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() },
            series = listOf(StatSeries(seriesName, insights.months.map { it.messages.toDouble() })),
            kind = StatChartKind.Bar,
            stacked = false,
            percentage = false,
            separateScales = false,
        )
    }
    StatChartCard(stringResource(Res.string.chat_insights_by_month), chart, monthly = true, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionRow(reactions: List<ReactionCount>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GROUP_CORNER))
            .background(MaterialTheme.colorScheme.groupRow)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        reactions.forEach { reaction ->
            Row(
                Modifier
                    .clip(ShapeDefaults.Large)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val customId = customEmojiIdOf(reaction.key)
                if (customId != null) {
                    CustomEmojiIcon(customEmojiId = customId, size = 20.dp)
                } else {
                    Text(reaction.key, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    compactCountText(reaction.count),
                    style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/** Počty obsahu za celou historii (server) s popiskem; nuly se vynechají. */
private fun contentCounts(counts: SharedMediaCounts) = listOf(
    Res.string.chat_insights_media to counts.media,
    Res.string.chat_insights_files to counts.files,
    Res.string.chat_insights_links to counts.links,
    Res.string.chat_insights_voice to counts.voice,
    Res.string.chat_insights_video_notes to counts.videoNotes,
    Res.string.chat_insights_gifs to counts.gifs,
    Res.string.chat_insights_music to counts.music,
).filter { it.second > 0 }

@Composable
private fun contentTiles(counts: SharedMediaCounts): List<StatTile> =
    contentCounts(counts).map { (label, count) -> StatTile(stringResource(label), compactCountText(count)) }

/**
 * Podíl v celých procentech, zaokrouhlený. `percentText` ze statistik kanálu desetiny usekává
 * (40 ze 45 = „88 %"), takže dva podíly dávaly dohromady 99 %.
 */
@Composable
private fun shareText(share: Float): String = percentText((share * 100).roundToInt().toDouble())

/** Doba odpovědi: „do minuty", „4 min", „2 h", „1 h 20 min". */
@Composable
private fun durationText(seconds: Long): String {
    val minutes = (seconds / 60).toInt()
    return when {
        minutes < 1 -> stringResource(Res.string.chat_insights_under_minute)
        minutes < 60 -> stringResource(Res.string.chat_insights_minutes, minutes)
        minutes % 60 == 0 -> stringResource(Res.string.chat_insights_hours, minutes / 60)
        else -> stringResource(Res.string.chat_insights_hours_minutes, minutes / 60, minutes % 60)
    }
}

/** Číslo na jedno desetinné místo s desetinným oddělovačem jazyka. */
@Composable
private fun decimalText(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return if (tenths % 10 == 0) {
        (tenths / 10).toString()
    } else {
        stringResource(Res.string.decimal_number, (tenths / 10).toString(), (tenths % 10).toString())
    }
}

/** Den jako „5. února" (rok jen mimo letošek), jako jinde v aplikaci. */
@Composable
private fun LocalDate.asDateText(): String = atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds.toInt().asDate()

private fun Int.pad(): String = toString().padStart(2, '0')

/** Údaj, který se spočítat nedal (protějšek nikdy neodpověděl). */
private const val NO_VALUE = "–"

/** Výška řádku mřížky týdne (buňka + mezera). */
private val CELL_ROW = 14.dp
