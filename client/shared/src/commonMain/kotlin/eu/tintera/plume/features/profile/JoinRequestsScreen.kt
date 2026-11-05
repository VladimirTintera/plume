package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.GroupActionText
import androidx.compose.foundation.layout.fillMaxWidth
import eu.tintera.plume.core.ui.GroupAction
import eu.tintera.plume.core.ui.ActionButtonGroup
import androidx.compose.foundation.layout.Arrangement
import eu.tintera.plume.resources.person_remove_24dp
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.how_to_reg_24dp
import eu.tintera.plume.resources.join_requests_approve
import eu.tintera.plume.resources.join_requests_approve_all
import eu.tintera.plume.resources.join_requests_approve_all_body
import eu.tintera.plume.resources.join_requests_approve_all_title
import eu.tintera.plume.resources.join_requests_decline
import eu.tintera.plume.resources.join_requests_decline_all
import eu.tintera.plume.resources.join_requests_decline_all_body
import eu.tintera.plume.resources.join_requests_decline_all_title
import eu.tintera.plume.resources.join_requests_empty
import eu.tintera.plume.resources.join_requests_empty_help
import eu.tintera.plume.resources.join_requests_requested
import eu.tintera.plume.resources.join_requests_title
import eu.tintera.plume.resources.more_vert_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Žádosti o vstup: kdo, kdy a jeho bio, pod tím Přijmout / Odmítnout. Hromadné vyřízení je
 * za ⋮ a potvrzuje se — přijmout desítky lidí jedním ťuknutím omylem nejde vzít zpátky.
 *
 * Přijmout je tonální tlačítko, odmítnout textové: přijetí je to, kvůli čemu správce přišel,
 * a vedle sebe stejně výrazné by se pletly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JoinRequestsScreen(viewModel: JoinRequestsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notice = viewModel.notice
    val noticeText = notice?.resolve()
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.notice = null
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    // `true` = přijmout všechny, `false` = odmítnout všechny.
    var confirmAll by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val shown = viewModel.shown

    val subpageScroll = profileSubpageScrollBehavior()

    Scaffold(
        modifier = modifier.profileSubpageScroll(subpageScroll),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProfileSubpageTopBar(
                title = stringResource(Res.string.join_requests_title),
                subtitle = rememberChatTitle(viewModel.chatId),
                onBack = onBack,
                scrollBehavior = subpageScroll,
                actions = {
                    if (!shown.isNullOrEmpty()) {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                enabled = !viewModel.busy,
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.profile_more))
                            }
                            ExpressiveMenu(
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                groups = listOf(
                                    listOf(
                                        MenuAction(stringResource(Res.string.join_requests_approve_all)) {
                                            menuOpen = false
                                            confirmAll = true
                                        },
                                    ),
                                    listOf(
                                        MenuAction(stringResource(Res.string.join_requests_decline_all), destructive = true) {
                                            menuOpen = false
                                            confirmAll = false
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            shown == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (!viewModel.loadFailed) LoadingIndicator()
            }
            shown.isEmpty() -> IntroEmptyState(
                icon = Res.drawable.how_to_reg_24dp,
                title = stringResource(Res.string.join_requests_empty),
                text = stringResource(Res.string.join_requests_empty_help),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(shown, key = { _, request -> request.userId }) { index, request ->
                    Surface(
                        shape = groupItemShape(index, shown.size),
                        color = MaterialTheme.colorScheme.groupRow,
                        modifier = Modifier.animateItem(),
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text(request.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Column {
                                        Text(stringResource(Res.string.join_requests_requested, request.date.asDayLabel()))
                                        if (request.bio.isNotBlank()) Text(request.bio, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                leadingContent = {
                                    Avatar(chatId = request.userId, title = request.name, avatarFileId = request.avatarFileId)
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
                            )
                            val approve = stringResource(Res.string.join_requests_approve)
                            val decline = stringResource(Res.string.join_requests_decline)
                            val onApprove = { viewModel.process(request, approve = true) }
                            val onDecline = { viewModel.process(request, approve = false) }
                            ActionButtonGroup(
                                listOf(
                                    GroupAction(approve, onApprove) { modifier, source ->
                                        FilledTonalButton(onClick = onApprove, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                                            GroupActionText(approve)
                                        }
                                    },
                                    GroupAction(decline, onDecline, compressionLimit = ButtonDefaults.TextButtonContentPadding) { modifier, source ->
                                        TextButton(onClick = onDecline, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                                            GroupActionText(decline)
                                        }
                                    },
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                // Tlačítka lícují s textem, ne s avatarem (16 + 48 + 16 dp).
                                modifier = Modifier.fillMaxWidth().padding(start = 80.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    confirmAll?.let { approve ->
        // Schválit všechny není nevratná ztráta, odmítnout ano — barvu a ikonu nese, co se stane.
        ConfirmDialog(
            title = stringResource(if (approve) Res.string.join_requests_approve_all_title else Res.string.join_requests_decline_all_title),
            text = stringResource(if (approve) Res.string.join_requests_approve_all_body else Res.string.join_requests_decline_all_body),
            confirmLabel = stringResource(if (approve) Res.string.join_requests_approve_all else Res.string.join_requests_decline_all),
            onConfirm = {
                confirmAll = null
                viewModel.processAll(approve)
            },
            onDismiss = { confirmAll = null },
            icon = if (approve) Res.drawable.how_to_reg_24dp else Res.drawable.person_remove_24dp,
            destructive = !approve,
        )
    }
}
