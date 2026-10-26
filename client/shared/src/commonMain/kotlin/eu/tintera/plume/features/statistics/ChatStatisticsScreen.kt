package eu.tintera.plume.features.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatStatistics
import eu.tintera.plume.core.telegram.RecentInteraction
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.StatGraphEntry
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.profile.ProfileSubpageTopBar
import eu.tintera.plume.features.profile.profileSubpageScroll
import eu.tintera.plume.features.profile.profileSubpageScrollBehavior
import eu.tintera.plume.features.profile.rememberChatTitle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bar_chart_24dp
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.empty_state_error_title
import eu.tintera.plume.resources.statistics_admin_banned
import eu.tintera.plume.resources.statistics_admin_deleted
import eu.tintera.plume.resources.statistics_admin_restricted
import eu.tintera.plume.resources.statistics_inviter_added
import eu.tintera.plume.resources.statistics_mean_reactions
import eu.tintera.plume.resources.statistics_mean_shares
import eu.tintera.plume.resources.statistics_mean_story_reactions
import eu.tintera.plume.resources.statistics_mean_story_shares
import eu.tintera.plume.resources.statistics_mean_story_views
import eu.tintera.plume.resources.statistics_mean_views
import eu.tintera.plume.resources.statistics_members
import eu.tintera.plume.resources.statistics_messages
import eu.tintera.plume.resources.statistics_notifications
import eu.tintera.plume.resources.statistics_overview
import eu.tintera.plume.resources.statistics_period
import eu.tintera.plume.resources.statistics_post_unavailable
import eu.tintera.plume.resources.statistics_recent_posts
import eu.tintera.plume.resources.statistics_sender_characters
import eu.tintera.plume.resources.statistics_sender_messages
import eu.tintera.plume.resources.statistics_senders
import eu.tintera.plume.resources.statistics_story
import eu.tintera.plume.resources.statistics_subscribers
import eu.tintera.plume.resources.statistics_title
import eu.tintera.plume.resources.statistics_top_admins
import eu.tintera.plume.resources.statistics_top_inviters
import eu.tintera.plume.resources.statistics_top_senders
import eu.tintera.plume.resources.statistics_viewers
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Statistiky kanálu nebo skupiny: přehled čísel se změnou proti předchozímu období, grafy
 * a u kanálu nedávné příspěvky (ťuknutí otevře statistiku příspěvku), u skupiny žebříčky
 * členů, správců a těch, kdo zvali.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatStatisticsScreen(
    viewModel: ChatStatisticsViewModel,
    onBack: () -> Unit,
    onOpenPost: (messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subpageScroll = profileSubpageScrollBehavior()
    Scaffold(
        modifier = modifier.profileSubpageScroll(subpageScroll),
        topBar = {
            ProfileSubpageTopBar(
                title = stringResource(Res.string.statistics_title),
                subtitle = rememberChatTitle(viewModel.chatId),
                onBack = onBack,
                scrollBehavior = subpageScroll,
            )
        },
    ) { padding ->
        val statistics = viewModel.statistics
        val failure = viewModel.failure
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
            statistics == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "period") {
                    Text(
                        stringResource(Res.string.statistics_period, statistics.period.start.asDate(), statistics.period.end.asDate()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                }
                item(key = "overview-title") { StatSectionTitle(stringResource(Res.string.statistics_overview)) }
                item(key = "overview") { StatTiles(overviewTiles(statistics), Modifier.padding(horizontal = 16.dp)) }
                graphItems(viewModel.graphs)
                when (statistics) {
                    is ChatStatistics.Channel -> recentPosts(statistics.recent, onOpenPost)
                    is ChatStatistics.Group -> groupLeaders(statistics)
                }
            }
        }
    }
}

/** Grafy, které je co kreslit; každý ve vlastní kartě. */
internal fun LazyListScope.graphItems(graphs: List<StatGraphEntry>) {
    graphs.filter { it.graph.isWorthShowing() }.forEach { entry ->
        item(key = "graph-${entry.kind}") {
            StatGraphCard(entry, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
    }
}

@Composable
private fun overviewTiles(statistics: ChatStatistics): List<StatTile> = when (statistics) {
    is ChatStatistics.Channel -> buildList {
        add(statTile(stringResource(Res.string.statistics_subscribers), statistics.members))
        add(StatTile(stringResource(Res.string.statistics_notifications), percentText(statistics.notificationsPercent)))
        add(statTile(stringResource(Res.string.statistics_mean_views), statistics.meanViews))
        add(statTile(stringResource(Res.string.statistics_mean_shares), statistics.meanShares))
        add(statTile(stringResource(Res.string.statistics_mean_reactions), statistics.meanReactions))
        // Příběhy jen u kanálu, který je zveřejňuje — jinak tři nuly navíc.
        if (statistics.meanStoryViews.value > 0 || statistics.meanStoryViews.previous > 0) {
            add(statTile(stringResource(Res.string.statistics_mean_story_views), statistics.meanStoryViews))
            add(statTile(stringResource(Res.string.statistics_mean_story_shares), statistics.meanStoryShares))
            add(statTile(stringResource(Res.string.statistics_mean_story_reactions), statistics.meanStoryReactions))
        }
    }
    is ChatStatistics.Group -> listOf(
        statTile(stringResource(Res.string.statistics_members), statistics.members),
        statTile(stringResource(Res.string.statistics_messages), statistics.messages),
        statTile(stringResource(Res.string.statistics_viewers), statistics.viewers),
        statTile(stringResource(Res.string.statistics_senders), statistics.senders),
    )
}

private fun LazyListScope.recentPosts(recent: List<RecentInteraction>, onOpenPost: (Long) -> Unit) {
    if (recent.isEmpty()) return
    item(key = "recent-title") { StatSectionTitle(stringResource(Res.string.statistics_recent_posts)) }
    itemsIndexed(recent, key = { _, it -> "recent-${it.messageId}-${it.storyId}" }) { index, post ->
        val messageId = post.messageId
        val color = MaterialTheme.colorScheme.surfaceContainerLowest
        Column(
            Modifier
                .fillMaxWidth()
                .groupedItem(index, recent.size, color, pressable = messageId != null)
                .then(if (messageId != null) Modifier.clickable { onOpenPost(messageId) } else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val title = when {
                post.storyId != null -> stringResource(Res.string.statistics_story)
                else -> post.preview?.resolve() ?: stringResource(Res.string.statistics_post_unavailable)
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val counts = interactionLine(post.views, post.forwards, post.reactions)
            Text(
                listOfNotNull(post.date?.asDate(), counts).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.groupLeaders(statistics: ChatStatistics.Group) {
    leaderList("senders", Res.string.statistics_top_senders, statistics.topSenders.map { it.user }) { i ->
        val sender = statistics.topSenders[i]
        listOf(
            pluralStringResource(Res.plurals.statistics_sender_messages, sender.messages, sender.messages),
            pluralStringResource(Res.plurals.statistics_sender_characters, sender.averageCharacters, sender.averageCharacters),
        ).joinToString(SEPARATOR)
    }
    leaderList("admins", Res.string.statistics_top_admins, statistics.topAdmins.map { it.user }) { i ->
        val admin = statistics.topAdmins[i]
        listOfNotNull(
            pluralStringResource(Res.plurals.statistics_admin_deleted, admin.deletedMessages, admin.deletedMessages)
                .takeIf { admin.deletedMessages > 0 },
            pluralStringResource(Res.plurals.statistics_admin_banned, admin.bannedUsers, admin.bannedUsers)
                .takeIf { admin.bannedUsers > 0 },
            pluralStringResource(Res.plurals.statistics_admin_restricted, admin.restrictedUsers, admin.restrictedUsers)
                .takeIf { admin.restrictedUsers > 0 },
        ).joinToString(SEPARATOR)
    }
    leaderList("inviters", Res.string.statistics_top_inviters, statistics.topInviters.map { it.user }) { i ->
        val inviter = statistics.topInviters[i]
        pluralStringResource(Res.plurals.statistics_inviter_added, inviter.addedMembers, inviter.addedMembers)
    }
}

/** Žebříček lidí s jedním řádkem čísel pod jménem. */
private fun LazyListScope.leaderList(
    key: String,
    title: StringResource,
    users: List<SenderInfo>,
    detail: @Composable (index: Int) -> String,
) {
    if (users.isEmpty()) return
    item(key = "$key-title") { StatSectionTitle(stringResource(title)) }
    itemsIndexed(users, key = { _, user -> "$key-${user.id}" }) { index, user ->
        Row(
            Modifier
                .fillMaxWidth()
                .groupedItem(index, users.size, MaterialTheme.colorScheme.surfaceContainerLowest, pressable = false)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(chatId = user.id, title = user.title, avatarFileId = user.avatarFileId, size = 40.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(user.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail(index),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
