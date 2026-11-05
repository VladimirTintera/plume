package eu.tintera.plume.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ProfileMember
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty_hint
import eu.tintera.plume.resources.edit_chat_removed
import eu.tintera.plume.resources.edit_chat_restricted
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.member_action_unrestrict
import eu.tintera.plume.resources.member_restricted_forever
import eu.tintera.plume.resources.member_restricted_until
import eu.tintera.plume.resources.person_remove_24dp
import eu.tintera.plume.resources.profile_members_search
import eu.tintera.plume.resources.profile_members_search_empty
import eu.tintera.plume.resources.removed_members_empty
import eu.tintera.plume.resources.removed_members_empty_help
import eu.tintera.plume.resources.removed_members_forever
import eu.tintera.plume.resources.removed_members_unban
import eu.tintera.plume.resources.removed_members_until
import eu.tintera.plume.resources.restricted_members_empty
import eu.tintera.plume.resources.restricted_members_empty_help
import eu.tintera.plume.resources.search_off_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Omezení, nebo vyhození členové: kdo, do kdy, a tlačítko, které to zruší.
 *
 * - **Zrušení se nepotvrzuje.** Obojí jde vzít zpátky z profilu skupiny (omezit, vyhodit znovu)
 *   a nikomu tím nic nezmizí — na rozdíl od vyhození, které potvrzení má.
 * - **Ťuknutí na omezeného otevře jeho omezení** ([onOpenRestriction]), aby šlo změnit, co smí,
 *   ne jen omezení zrušit. Vyhozený žádná práva nemá, jeho řádek nese jen tlačítko.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemovedMembersScreen(
    viewModel: RemovedMembersViewModel,
    onBack: () -> Unit,
    onOpenRestriction: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notice = viewModel.notice
    val noticeText = notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.notice = null
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    val banned = viewModel.banned
    val shown = viewModel.shown
    val list = viewModel.list

    val subpageScroll = profileSubpageScrollBehavior()

    Scaffold(
        modifier = modifier.profileSubpageScroll(subpageScroll),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProfileSubpageTopBar(
                title = stringResource(if (banned) Res.string.edit_chat_removed else Res.string.edit_chat_restricted),
                subtitle = rememberChatTitle(viewModel.chatId),
                onBack = onBack,
                scrollBehavior = subpageScroll,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (viewModel.searchAvailable) {
                SearchPill(
                    initialQuery = viewModel.query,
                    onQueryChange = viewModel::search,
                    placeholder = stringResource(Res.string.profile_members_search),
                    clearDescription = stringResource(Res.string.chats_search_clear),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!viewModel.loadFailed) LoadingIndicator()
                }
                shown.isEmpty() && list.query.isNotEmpty() -> IntroEmptyState(
                    icon = Res.drawable.search_off_24dp,
                    title = stringResource(Res.string.profile_members_search_empty),
                    text = stringResource(Res.string.chats_search_empty_hint),
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxSize(),
                )
                shown.isEmpty() -> IntroEmptyState(
                    icon = if (banned) Res.drawable.person_remove_24dp else Res.drawable.lock_24dp,
                    title = stringResource(if (banned) Res.string.removed_members_empty else Res.string.restricted_members_empty),
                    text = stringResource(if (banned) Res.string.removed_members_empty_help else Res.string.restricted_members_empty_help),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(shown, key = { _, member -> member.sender.id }) { index, member ->
                        if (list.nextOffset != null) LoadMoreAtEnd(index, shown.size, viewModel::loadMore)
                        RemovedMemberRow(
                            member = member,
                            banned = banned,
                            onRelease = { viewModel.release(member) },
                            onOpen = if (banned) null else ({ onOpenRestriction(member.sender.id) }),
                            modifier = Modifier.animateItem().clip(groupItemShape(index, shown.size)),
                        )
                    }
                    if (list.loadingMore) {
                        item(key = LOADING_KEY) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemovedMemberRow(
    member: ProfileMember,
    banned: Boolean,
    onRelease: () -> Unit,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val until = member.until
    ListItem(
        headlineContent = { Text(member.sender.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (until == null) {
                    stringResource(if (banned) Res.string.removed_members_forever else Res.string.member_restricted_forever)
                } else {
                    stringResource(
                        if (banned) Res.string.removed_members_until else Res.string.member_restricted_until,
                        until.toLong().asUpcomingTime(),
                    )
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Avatar(chatId = member.sender.id, title = member.sender.title, avatarFileId = member.sender.avatarFileId)
        },
        trailingContent = {
            TextButton(onClick = onRelease, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (banned) Res.string.removed_members_unban else Res.string.member_action_unrestrict))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
        modifier = if (onOpen != null) modifier.clickable(onClick = onOpen) else modifier,
    )
}

private const val LOADING_KEY = "removed-members-loading"
