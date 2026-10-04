package eu.tintera.plume.features.profile

import androidx.compose.material3.FilledTonalIconButton
import eu.tintera.plume.resources.invite_links_share
import eu.tintera.plume.resources.share_24dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.AdminInviteLinks
import eu.tintera.plume.core.telegram.InviteLinkMember
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.invite_links_admin_active
import eu.tintera.plume.resources.invite_links_admin_empty
import eu.tintera.plume.resources.invite_links_admin_help
import eu.tintera.plume.resources.invite_links_admin_bot_help
import eu.tintera.plume.resources.invite_links_keep
import eu.tintera.plume.resources.invite_links_admin_revoked
import eu.tintera.plume.resources.invite_links_admin_title
import eu.tintera.plume.resources.invite_links_member_joined
import eu.tintera.plume.resources.invite_links_member_via_folder
import eu.tintera.plume.resources.invite_links_members
import eu.tintera.plume.resources.invite_links_members_empty
import eu.tintera.plume.resources.invite_links_members_title
import eu.tintera.plume.resources.invite_links_others
import eu.tintera.plume.core.telegram.INVITE_LINK_NAME_MAX
import eu.tintera.plume.core.telegram.InviteLink
import eu.tintera.plume.core.telegram.InviteLinkStatus
import eu.tintera.plume.core.telegram.inviteLinkStatus
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.edit_profile_save
import eu.tintera.plume.resources.invite_editor_approval
import eu.tintera.plume.resources.invite_editor_approval_help
import eu.tintera.plume.resources.invite_editor_create
import eu.tintera.plume.resources.invite_editor_edit
import eu.tintera.plume.resources.invite_editor_expiry
import eu.tintera.plume.resources.invite_editor_limit
import eu.tintera.plume.resources.invite_editor_name
import eu.tintera.plume.resources.invite_editor_name_help
import eu.tintera.plume.resources.invite_editor_new
import eu.tintera.plume.resources.invite_expiry_day
import eu.tintera.plume.resources.invite_expiry_hour
import eu.tintera.plume.resources.invite_expiry_keep
import eu.tintera.plume.resources.invite_expiry_never
import eu.tintera.plume.resources.invite_expiry_week
import eu.tintera.plume.resources.invite_limit_unlimited
import eu.tintera.plume.resources.invite_link_approval
import eu.tintera.plume.resources.invite_link_expires
import eu.tintera.plume.resources.invite_link_joined
import eu.tintera.plume.resources.invite_link_limit
import eu.tintera.plume.resources.invite_link_pending
import eu.tintera.plume.resources.invite_link_status_expired
import eu.tintera.plume.resources.invite_link_status_full
import eu.tintera.plume.resources.invite_link_subscription
import eu.tintera.plume.resources.invite_links_additional
import eu.tintera.plume.resources.invite_links_additional_help
import eu.tintera.plume.resources.invite_links_copy
import eu.tintera.plume.resources.invite_links_create
import eu.tintera.plume.resources.invite_links_delete
import eu.tintera.plume.resources.invite_links_delete_all
import eu.tintera.plume.resources.invite_links_delete_all_body
import eu.tintera.plume.resources.invite_links_delete_all_title
import eu.tintera.plume.resources.invite_links_edit
import eu.tintera.plume.resources.invite_links_primary
import eu.tintera.plume.resources.invite_links_primary_help
import eu.tintera.plume.resources.invite_links_replace_primary
import eu.tintera.plume.resources.invite_links_revoke
import eu.tintera.plume.resources.invite_links_revoke_body
import eu.tintera.plume.resources.invite_links_revoke_primary_body
import eu.tintera.plume.resources.invite_links_revoke_title
import eu.tintera.plume.resources.invite_links_revoked_section
import eu.tintera.plume.resources.invite_links_title
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.link_off_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pozvánky chatu. Tři bloky zaoblených řádků, jako profil: hlavní odkaz s kopírováním a výměnou,
 * další odkazy (vytvoření, úprava, zrušení) a zrušené odkazy (smazání).
 *
 * - **Ťuknutí na odkaz otevře nabídku**, ne rovnou kopírování — u odkazu je víc akcí a zrušení
 *   je nevratné; kopírovat je v nabídce první.
 * - **Nevratné akce se potvrzují** a pojmenovávají následek: zrušený odkaz už nikoho nepustí,
 *   výměna hlavního odkazu zneplatní ten, který lidé mají.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InviteLinksScreen(viewModel: InviteLinksViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
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

    var actionsFor by remember { mutableStateOf<InviteLink?>(null) }
    var confirmRevoke by remember { mutableStateOf<InviteLink?>(null) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAdminRevoked by remember { mutableStateOf<AdminInviteLinks?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.invite_links_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        val links = viewModel.links
        if (links == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (!viewModel.loadFailed) LoadingIndicator()
            }
            return@Scaffold
        }
        val now = viewModel.now

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            modifier = Modifier.fillMaxSize(),
        ) {
            links.primary?.let { primary ->
                item(key = "primary-header") { SectionHeader(Res.string.invite_links_primary, first = true) }
                item(key = "primary") {
                    PrimaryLinkCard(
                        link = primary,
                        busy = viewModel.busy,
                        onCopy = { viewModel.copy(primary) },
                        onShare = { viewModel.share(primary) },
                        onReplace = { confirmRevoke = primary },
                        onMembers = { viewModel.openMembers(primary) },
                    )
                }
            }

            item(key = "additional-header") { SectionHeader(Res.string.invite_links_additional, first = links.primary == null) }
            val additional = links.additional
            item(key = "create") {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.invite_links_create)) },
                    supportingContent = { Text(stringResource(Res.string.invite_links_additional_help)) },
                    leadingContent = { RowIcon(Res.drawable.add_24dp, accent = true) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        headlineColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .animateItem()
                        .clip(groupItemShape(0, additional.size + 1))
                        .clickable(enabled = !viewModel.busy, onClick = viewModel::openCreate),
                )
            }
            itemsIndexed(additional, key = { _, link -> "link-${link.url}" }) { index, link ->
                LinkRow(
                    link = link,
                    now = now,
                    shape = groupItemShape(index + 1, additional.size + 1),
                    onClick = { actionsFor = link },
                    modifier = Modifier.animateItem(),
                )
            }

            val others = viewModel.otherAdmins
            if (others.isNotEmpty()) {
                item(key = "others-header") { SectionHeader(Res.string.invite_links_others) }
                itemsIndexed(others, key = { _, admin -> "admin-${admin.userId}" }) { index, admin ->
                    AdminRow(
                        admin = admin,
                        shape = groupItemShape(index, others.size),
                        onClick = { viewModel.openAdmin(admin) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (links.revoked.isNotEmpty()) {
                item(key = "revoked-header") {
                    SectionHeader(Res.string.invite_links_revoked_section) {
                        TextButton(onClick = { confirmDeleteAll = true }, enabled = !viewModel.busy) {
                            Text(stringResource(Res.string.invite_links_delete_all))
                        }
                    }
                }
                itemsIndexed(links.revoked, key = { _, link -> "revoked-${link.url}" }) { index, link ->
                    LinkRow(
                        link = link,
                        now = now,
                        shape = groupItemShape(index, links.revoked.size),
                        onClick = { actionsFor = link },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    actionsFor?.let { link ->
        LinkActionsSheet(
            link = link,
            now = viewModel.now,
            onDismiss = { actionsFor = null },
            onCopy = { viewModel.copy(link) },
            onShare = { viewModel.share(link) },
            onEdit = { viewModel.openEdit(link) },
            onRevoke = { confirmRevoke = link },
            onDelete = { viewModel.deleteRevoked(link) },
            onMembers = { viewModel.openMembers(link) },
            // Odkaz jiného správce jen zrušit a smazat — nastavení patří tomu, kdo ho založil.
            // Odkaz bota ani to: smí ho měnit jen bot sám (`EDIT_BOT_INVITE_FORBIDDEN`).
            canEdit = viewModel.adminView == null,
            canManage = viewModel.adminView?.admin?.isBot != true,
        )
    }

    confirmRevoke?.let { link ->
        AlertDialog(
            onDismissRequest = { confirmRevoke = null },
            title = { Text(stringResource(Res.string.invite_links_revoke_title)) },
            text = {
                Text(
                    stringResource(
                        if (link.isPrimary) Res.string.invite_links_revoke_primary_body else Res.string.invite_links_revoke_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevoke = null
                    viewModel.revoke(link)
                }) {
                    Text(
                        stringResource(if (link.isPrimary) Res.string.invite_links_replace_primary else Res.string.invite_links_revoke),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = null }) { Text(stringResource(Res.string.invite_links_keep)) }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(Res.string.invite_links_delete_all_title)) },
            text = { Text(stringResource(Res.string.invite_links_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAllRevoked()
                }) {
                    Text(stringResource(Res.string.invite_links_delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }

    // Sheety se vrší v pořadí, v jakém se otevírají: odkazy správce, nabídka nad odkazem a nad
    // ní „Kdo se přidal". Každý je vlastní okno přidané později, takže leží nahoře.
    viewModel.adminView?.let { view ->
        AdminLinksSheet(
            view = view,
            now = viewModel.now,
            busy = viewModel.busy,
            onOpen = { actionsFor = it },
            onDeleteAllRevoked = { confirmDeleteAdminRevoked = view.admin },
            onDismiss = viewModel::closeAdmin,
        )
    }

    viewModel.membersView?.let { view ->
        LinkMembersSheet(view = view, onDismiss = viewModel::closeMembers)
    }

    confirmDeleteAdminRevoked?.let { admin ->
        AlertDialog(
            onDismissRequest = { confirmDeleteAdminRevoked = null },
            title = { Text(stringResource(Res.string.invite_links_delete_all_title)) },
            text = { Text(stringResource(Res.string.invite_links_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAdminRevoked = null
                    viewModel.deleteAllRevoked(admin)
                }) {
                    Text(stringResource(Res.string.invite_links_delete_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAdminRevoked = null }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }

    viewModel.editor?.let { editor ->
        InviteLinkEditorSheet(
            editor = editor,
            busy = viewModel.busy,
            onChange = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::closeEditor,
        )
    }
}

/** Nadpis bloku; nad prvním blokem bez mezery navíc (tu dává odsazení seznamu). */
@Composable
private fun SectionHeader(
    title: StringResource,
    first: Boolean = false,
    action: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = if (first) 0.dp else 16.dp, bottom = 6.dp),
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/**
 * Hlavní odkaz: adresa, kolik lidí přes něj přišlo, sdílení, kopírování a výměna za nový.
 * Sdílení je hlavní tlačítko — odkaz se zakládá proto, aby šel někomu poslat; kopírování je
 * ikona vedle, tři textová tlačítka by se v češtině na telefon nevešla.
 */
@Composable
private fun PrimaryLinkCard(
    link: InviteLink,
    busy: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReplace: () -> Unit,
    onMembers: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                link.shortUrl(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                // Kolik lidí se přidalo, nese tlačítko „Kdo se přidal" níž — přilepené k téhle větě
                // vycházelo „…může se přidat. přidali se 2".
                stringResource(Res.string.invite_links_primary_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(
                        painterResource(Res.drawable.share_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                    )
                    Text(stringResource(Res.string.invite_links_share), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = onReplace, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.invite_links_replace_primary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalIconButton(onClick = onCopy) {
                    Icon(painterResource(Res.drawable.content_copy_24dp), contentDescription = stringResource(Res.string.invite_links_copy))
                }
            }
            // Hlavním odkazem se přidává nejvíc lidí, a nabídku nad ním (jako u dalších odkazů)
            // karta nemá — proto vlastní tlačítko.
            if (link.memberCount > 0) {
                TextButton(onClick = onMembers, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                    Icon(
                        painterResource(Res.drawable.group_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                    )
                    Text(listOf(stringResource(Res.string.invite_links_members), link.memberCount.grouped()).joinToString(" · "))
                }
            }
        }
    }
}

@Composable
private fun LinkRow(link: InviteLink, now: Long, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = inviteLinkStatus(link, now)
    val usable = status == InviteLinkStatus.Active
    ListItem(
        headlineContent = {
            Text(link.name.ifBlank { link.shortUrl() }, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        },
        supportingContent = { Text(linkSummary(link, status), maxLines = 2) },
        leadingContent = { RowIcon(if (usable) Res.drawable.link_24dp else Res.drawable.link_off_24dp, accent = usable) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.clip(shape).clickable(onClick = onClick),
    )
}

/** Ikona v tónovaném kolečku; platný odkaz v akcentu, zrušený a propadlý tlumeně. */
@Composable
private fun RowIcon(icon: DrawableResource, accent: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Podtitul odkazu: kolik lidí se přidalo, co čeká, limit a platnost — co z toho má smysl.
 * Stav (vypršel, naplněný) jde před údaje, protože rozhoduje, jestli odkaz vůbec k něčemu je.
 */
@Composable
private fun linkSummary(link: InviteLink, status: InviteLinkStatus): String {
    val parts = mutableListOf<String>()
    when (status) {
        InviteLinkStatus.Expired -> parts += stringResource(Res.string.invite_link_status_expired)
        InviteLinkStatus.Full -> parts += stringResource(Res.string.invite_link_status_full)
        else -> Unit
    }
    parts += pluralStringResource(Res.plurals.invite_link_joined, link.memberCount, link.memberCount.grouped())
    if (link.pendingRequests > 0) {
        parts += pluralStringResource(Res.plurals.invite_link_pending, link.pendingRequests, link.pendingRequests.grouped())
    }
    if (link.memberLimit > 0) parts += stringResource(Res.string.invite_link_limit, link.memberLimit.grouped())
    if (link.createsJoinRequest) parts += stringResource(Res.string.invite_link_approval)
    if (link.isSubscription) parts += stringResource(Res.string.invite_link_subscription)
    if (status == InviteLinkStatus.Active && link.expiresAt != 0) {
        parts += stringResource(Res.string.invite_link_expires, link.expiresAt.toLong().asUpcomingTime())
    }
    return parts.joinToString(" · ")
}

/**
 * Nabídka nad odkazem. Platný: kopírovat, upravit, zrušit. Zrušený: jen smazat.
 * Placené předplatné upravit nejde (měnit se smí jen jeho jméno, a to aplikace neumí).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkActionsSheet(
    link: InviteLink,
    now: Long,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRevoke: () -> Unit,
    onDelete: () -> Unit,
    onMembers: () -> Unit,
    canEdit: Boolean,
    canManage: Boolean,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
            Text(
                link.name.ifBlank { link.shortUrl() },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            Text(
                linkSummary(link, inviteLinkStatus(link, now)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            val actions = buildList {
                // Kdo se přidal, má smysl i u zrušeného odkazu — lidé ve skupině zůstávají.
                val members = Triple(Res.string.invite_links_members, Res.drawable.group_24dp, onMembers)
                if (link.isRevoked) {
                    if (link.memberCount > 0) add(members)
                    if (canManage) add(Triple(Res.string.invite_links_delete, Res.drawable.delete_24dp, onDelete))
                } else {
                    add(Triple(Res.string.invite_links_share, Res.drawable.share_24dp, onShare))
                    add(Triple(Res.string.invite_links_copy, Res.drawable.content_copy_24dp, onCopy))
                    if (link.memberCount > 0) add(members)
                    if (canEdit && !link.isSubscription) add(Triple(Res.string.invite_links_edit, Res.drawable.edit_24dp, onEdit))
                    if (canManage) add(Triple(Res.string.invite_links_revoke, Res.drawable.link_off_24dp, onRevoke))
                }
            }
            actions.forEachIndexed { index, (label, icon, action) ->
                val destructive = icon == Res.drawable.delete_24dp || icon == Res.drawable.link_off_24dp
                val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        headlineColor = color,
                        leadingIconColor = color,
                    ),
                    modifier = Modifier
                        .clip(groupItemShape(index, actions.size))
                        .clickable {
                            onDismiss()
                            action()
                        },
                )
            }
        }
    }
}

/**
 * Formulář odkazu: název, schvalování, platnost a limit. Limit se se schvalováním schová —
 * TDLib ho u odkazu se žádostmi nepřijme.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InviteLinkEditorSheet(
    editor: InviteLinkEditor,
    busy: Boolean,
    onChange: ((InviteLinkEditor) -> InviteLinkEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Celý rozbalený: napůl rozbalený by tlačítko uložení schoval pod okraj.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (editor.isNew) Res.string.invite_editor_new else Res.string.invite_editor_edit),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = { value -> onChange { it.copy(name = value.take(INVITE_LINK_NAME_MAX)) } },
                label = { Text(stringResource(Res.string.invite_editor_name)) },
                supportingText = { Text(stringResource(Res.string.invite_editor_name_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                shape = RoundedCornerShape(GROUP_CORNER),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Column {
                    SwitchRow(Res.string.invite_editor_approval, editor.createsJoinRequest) { value ->
                        onChange { it.copy(createsJoinRequest = value) }
                    }
                    Text(
                        stringResource(Res.string.invite_editor_approval_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            ChoiceGroup(
                title = Res.string.invite_editor_expiry,
                options = editor.expiryOptions,
                selected = editor.expiry,
                label = { expiry ->
                    when (expiry) {
                        InviteExpiry.Keep -> stringResource(Res.string.invite_expiry_keep)
                        InviteExpiry.Never -> stringResource(Res.string.invite_expiry_never)
                        InviteExpiry.Hour -> stringResource(Res.string.invite_expiry_hour)
                        InviteExpiry.Day -> stringResource(Res.string.invite_expiry_day)
                        InviteExpiry.Week -> stringResource(Res.string.invite_expiry_week)
                    }
                },
                onSelect = { expiry -> onChange { it.copy(expiry = expiry) } },
            )
            if (editor.expiry == InviteExpiry.Keep && editor.currentExpiresAt != 0) {
                Text(
                    stringResource(Res.string.invite_link_expires, editor.currentExpiresAt.toLong().asUpcomingTime()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

            if (!editor.createsJoinRequest) {
                ChoiceGroup(
                    title = Res.string.invite_editor_limit,
                    options = editor.limitOptions,
                    selected = editor.memberLimit,
                    label = { limit -> if (limit == 0) stringResource(Res.string.invite_limit_unlimited) else limit.grouped() },
                    onSelect = { limit -> onChange { it.copy(memberLimit = limit) } },
                )
            }

            Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (editor.isNew) Res.string.invite_editor_create else Res.string.edit_profile_save))
            }
        }
    }
}

/** Nadpis a spojená skupina přepínacích tlačítek — vybrané je vždy právě jedno. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ChoiceGroup(
    title: StringResource,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.forEachIndexed { index, option ->
                val checked = option == selected
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onSelect(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (checked) {
                        Icon(
                            painterResource(Res.drawable.check_24dp),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize),
                        )
                    }
                    Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Správce s odkazy: tvář, jméno a kolik jich má (platných a zrušených). */
@Composable
private fun AdminRow(admin: AdminInviteLinks, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(admin.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(adminSummary(admin)) },
        leadingContent = { Avatar(chatId = admin.userId, title = admin.name, avatarFileId = admin.avatarFileId, size = 40.dp) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.clip(shape).clickable(onClick = onClick),
    )
}

@Composable
private fun adminSummary(admin: AdminInviteLinks): String = listOfNotNull(
    admin.active.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.invite_links_admin_active, it, it.grouped()) },
    admin.revoked.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.invite_links_admin_revoked, it, it.grouped()) },
).joinToString(" · ")

/**
 * Odkazy jednoho z ostatních správců: platné, pod nimi zrušené. Ťuknutí otevře tutéž nabídku
 * jako u vlastních odkazů, jen bez úpravy ([onOpen]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AdminLinksSheet(
    view: AdminLinksView,
    now: Long,
    busy: Boolean,
    onOpen: (InviteLink) -> Unit,
    onDeleteAllRevoked: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Odkazů bývá víc než na půl obrazovky; napůl rozbalený sheet by zrušené schoval pod okraj.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            item(key = "title") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text(
                        stringResource(Res.string.invite_links_admin_title, view.admin.name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(if (view.admin.isBot) Res.string.invite_links_admin_bot_help else Res.string.invite_links_admin_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // Snackbar obrazovky by zůstal pod sheetem, takže selhaná akce se hlásí tady.
                    view.error?.let { error ->
                        Text(
                            error.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            val links = view.links
            when {
                links == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        if (!view.failed) LoadingIndicator()
                    }
                }
                links.active.isEmpty() && links.revoked.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(Res.string.invite_links_admin_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    itemsIndexed(links.active, key = { _, link -> "active-${link.url}" }) { index, link ->
                        LinkRow(link = link, now = now, shape = groupItemShape(index, links.active.size), onClick = { onOpen(link) })
                    }
                    if (links.revoked.isNotEmpty()) {
                        item(key = "revoked-header") {
                            SectionHeader(Res.string.invite_links_revoked_section, first = links.active.isEmpty()) {
                                if (!view.admin.isBot) {
                                    TextButton(onClick = onDeleteAllRevoked, enabled = !busy) {
                                        Text(stringResource(Res.string.invite_links_delete_all))
                                    }
                                }
                            }
                        }
                        itemsIndexed(links.revoked, key = { _, link -> "revoked-${link.url}" }) { index, link ->
                            LinkRow(link = link, now = now, shape = groupItemShape(index, links.revoked.size), onClick = { onOpen(link) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kdo se přidal odkazem, od nejnovějšího. Server vrací jen ty, kdo ve skupině pořád jsou —
 * počet u odkazu (`memberCount`) počítá i ty, kdo mezitím odešli, takže seznam může být kratší.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkMembersSheet(view: LinkMembersView, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            item(key = "title") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text(stringResource(Res.string.invite_links_members_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        view.link.name.ifBlank { view.link.shortUrl() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            val members = view.members
            when {
                members == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        if (!view.failed) LoadingIndicator()
                    }
                }
                members.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(Res.string.invite_links_members_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> itemsIndexed(members, key = { _, member -> "member-${member.userId}" }) { index, member ->
                    MemberRow(member, groupItemShape(index, members.size))
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: InviteLinkMember, shape: Shape) {
    val joined = stringResource(Res.string.invite_links_member_joined, member.joinedAt.asDayLabel())
    val via = if (member.viaFolder) stringResource(Res.string.invite_links_member_via_folder) else null
    ListItem(
        headlineContent = { Text(member.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(joined, via).joinToString(" · ")) },
        leadingContent = { Avatar(chatId = member.userId, title = member.name, avatarFileId = member.avatarFileId, size = 40.dp) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clip(shape),
    )
}
