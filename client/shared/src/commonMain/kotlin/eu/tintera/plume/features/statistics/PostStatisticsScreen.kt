package eu.tintera.plume.features.statistics

import eu.tintera.plume.core.adaptive.paneScaffoldColor
import eu.tintera.plume.core.adaptive.PaneBody
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.profile.ProfileSubpageTopBar
import eu.tintera.plume.features.profile.profileSubpageScroll
import eu.tintera.plume.features.profile.profileSubpageScrollBehavior
import eu.tintera.plume.features.profile.rememberChatTitle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bar_chart_24dp
import eu.tintera.plume.resources.boosts_load_more
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.empty_state_error_title
import eu.tintera.plume.resources.post_statistics_forwards
import eu.tintera.plume.resources.post_statistics_public_shares
import eu.tintera.plume.resources.post_statistics_reactions
import eu.tintera.plume.resources.post_statistics_title
import eu.tintera.plume.resources.post_statistics_views
import eu.tintera.plume.resources.statistics_story
import eu.tintera.plume.resources.statistics_views_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Statistika příspěvku v kanálu: náhled, počty, grafy zhlédnutí a reakcí a kam ho veřejně
 * přeposlali. Ťuknutí na přeposlání otevře tu zprávu v cizím kanálu (příběh otevřít nejde,
 * ukáže se jen kanál, který ho sdílel).
 *
 * **Soukromá přeposlání se nevypisují** — server o nich vydá jen počet (v [post_statistics_forwards]
 * je i s veřejnými).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostStatisticsScreen(
    viewModel: PostStatisticsViewModel,
    onBack: () -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subpageScroll = profileSubpageScrollBehavior()
    Scaffold(
        containerColor = paneScaffoldColor(),
        modifier = modifier.profileSubpageScroll(subpageScroll),
        topBar = {
            ProfileSubpageTopBar(
                title = stringResource(Res.string.post_statistics_title),
                subtitle = rememberChatTitle(viewModel.chatId),
                onBack = onBack,
                scrollBehavior = subpageScroll,
            )
        },
    ) { scaffoldPadding ->
        PaneBody(scaffoldPadding) { padding ->
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
                    item(key = "post") {
                        Column(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(GROUP_CORNER))
                                .background(MaterialTheme.colorScheme.groupRow)
                                .padding(16.dp),
                        ) {
                            Text(statistics.preview.resolve(), style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Text(
                                statistics.date.asDate(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    item(key = "overview") {
                        StatTiles(
                            listOf(
                                StatTile(stringResource(Res.string.post_statistics_views), compactCountText(statistics.views)),
                                StatTile(stringResource(Res.string.post_statistics_forwards), compactCountText(statistics.forwards)),
                                StatTile(stringResource(Res.string.post_statistics_reactions), compactCountText(statistics.reactions)),
                                StatTile(stringResource(Res.string.post_statistics_public_shares), compactCountText(viewModel.forwardsTotal)),
                            ),
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                    graphItems(viewModel.graphs)

                    val forwards = viewModel.forwards.orEmpty()
                    if (forwards.isNotEmpty()) {
                        item(key = "forwards-title") { StatSectionTitle(stringResource(Res.string.post_statistics_public_shares)) }
                        itemsIndexed(forwards, key = { _, it -> "forward-${it.chat.id}-${it.messageId}-${it.date}" }) { index, forward ->
                            val messageId = forward.messageId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .groupedItem(index, forwards.size, MaterialTheme.colorScheme.groupRow, pressable = messageId != null)
                                    .then(if (messageId != null) Modifier.clickable { onOpenMessage(forward.chat.id, messageId) } else Modifier)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(chatId = forward.chat.id, title = forward.chat.title, avatarFileId = forward.chat.avatarFileId, size = 40.dp)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(forward.chat.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val views = pluralStringResource(Res.plurals.statistics_views_count, forward.views, compactCountText(forward.views))
                                    Text(
                                        listOfNotNull(stringResource(Res.string.statistics_story).takeIf { messageId == null }, forward.date.asDate(), views)
                                            .joinToString(SEPARATOR),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (viewModel.canLoadMore) {
                            item(key = "forwards-more") {
                                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                                    if (viewModel.loadingMore) {
                                        LoadingIndicator()
                                    } else {
                                        TextButton(onClick = viewModel::loadMore, shapes = ButtonDefaults.shapes()) {
                                            Text(stringResource(Res.string.boosts_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    viewModel.forwardsFailure?.let { error ->
                        item(key = "forwards-failed") {
                            Text(
                                error.resolve(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
