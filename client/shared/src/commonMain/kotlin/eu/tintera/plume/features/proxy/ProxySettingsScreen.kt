package eu.tintera.plume.features.proxy

import eu.tintera.plume.features.settings.SettingsTopBar
import eu.tintera.plume.features.settings.settingsScroll
import eu.tintera.plume.features.settings.settingsScrollBehavior
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ProxyConfig
import eu.tintera.plume.core.telegram.ProxyKind
import eu.tintera.plume.core.telegram.ProxyProblem
import eu.tintera.plume.core.telegram.SavedProxy
import eu.tintera.plume.core.telegram.problem
import eu.tintera.plume.core.telegram.shareLink
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.proxy_add
import eu.tintera.plume.resources.proxy_connected_via
import eu.tintera.plume.resources.proxy_copy_link
import eu.tintera.plume.resources.proxy_delete
import eu.tintera.plume.resources.proxy_delete_body
import eu.tintera.plume.resources.proxy_delete_title
import eu.tintera.plume.resources.proxy_direct
import eu.tintera.plume.resources.proxy_direct_help
import eu.tintera.plume.resources.proxy_edit
import eu.tintera.plume.resources.proxy_editor_add_title
import eu.tintera.plume.resources.proxy_editor_edit_title
import eu.tintera.plume.resources.proxy_field_http_only
import eu.tintera.plume.resources.proxy_field_password
import eu.tintera.plume.resources.proxy_field_port
import eu.tintera.plume.resources.proxy_field_secret
import eu.tintera.plume.resources.proxy_field_server
import eu.tintera.plume.resources.proxy_field_username
import eu.tintera.plume.resources.proxy_help
import eu.tintera.plume.resources.proxy_measure
import eu.tintera.plume.resources.proxy_menu
import eu.tintera.plume.resources.proxy_problem_port
import eu.tintera.plume.resources.proxy_problem_secret
import eu.tintera.plume.resources.proxy_problem_server
import eu.tintera.plume.resources.proxy_save
import eu.tintera.plume.resources.proxy_state_on
import eu.tintera.plume.resources.proxy_title
import eu.tintera.plume.resources.refresh_24dp
import eu.tintera.plume.resources.vpn_lock_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Proxy v Nastavení: „Přímé připojení" a pod ním uložené proxy jako jeden blok zaoblených
 * řádků — vybraná je ta, přes kterou se aplikace připojuje. Ťuknutí na řádek vybere, ⋮ nabídne
 * úpravu, odkaz a smazání. U každé proxy je odezva (`pingProxy`), aby šlo poznat, která funguje,
 * dřív než se přes ni aplikace přepne.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    viewModel: ProxySettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (past „Vrátit").
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.noticeShown()
        val message = notice.resolveNow()
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = stringResource(Res.string.proxy_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (!state.proxies.isNullOrEmpty()) {
                        IconButton(onClick = viewModel::measure, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.refresh_24dp), contentDescription = stringResource(Res.string.proxy_measure))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val proxies = state.proxies
        if (proxies == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        // Přidat, přímé připojení a proxy jsou jeden blok.
        val count = proxies.size + 2
        val direct = proxies.none { it.enabled }
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
            item(key = "help") {
                Text(
                    stringResource(Res.string.proxy_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            item(key = "add") {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.proxy_add)) },
                    leadingContent = { RowIcon(Res.drawable.add_24dp, accent = true) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        headlineColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.animateItem().clip(groupItemShape(0, count)).clickable(onClick = viewModel::add),
                )
            }
            item(key = "direct") {
                ProxyRow(
                    title = stringResource(Res.string.proxy_direct),
                    subtitle = stringResource(Res.string.proxy_direct_help),
                    selected = direct,
                    enabled = !state.busy,
                    shapeIndex = 1,
                    count = count,
                    onClick = { if (!direct) viewModel.disable() },
                    modifier = Modifier.animateItem(),
                )
            }
            itemsIndexed(proxies, key = { _, it -> it.id }) { index, proxy ->
                val ping = state.pings[proxy.id]
                val subtitle = buildList {
                    add(proxyKindLabel(proxy.config.kind))
                    if (proxy.enabled) add(stringResource(Res.string.proxy_state_on))
                    ping?.let { add(proxyPingLabel(it)) }
                }.joinToString(" · ")
                ProxyRow(
                    title = "${proxy.config.server}:${proxy.config.port}",
                    subtitle = subtitle,
                    selected = proxy.enabled,
                    enabled = !state.busy,
                    shapeIndex = index + 2,
                    count = count,
                    onClick = { viewModel.toggle(proxy) },
                    menu = { ProxyMenu(proxy, viewModel) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (!direct) {
                item(key = "via") {
                    val enabled = proxies.first { it.enabled }
                    Text(
                        stringResource(Res.string.proxy_connected_via, enabled.config.server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateItem().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
            }
        }
    }

    state.editing?.let { target ->
        ProxyEditorSheet(
            initial = (target as? ProxyEditTarget.Existing)?.proxy?.config,
            saving = state.saving,
            error = state.editorError?.resolve(),
            onSave = viewModel::save,
            onDismiss = viewModel::closeEditor,
        )
    }

    state.pendingRemove?.let { proxy ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemove,
            title = { Text(stringResource(Res.string.proxy_delete_title)) },
            text = { Text(stringResource(Res.string.proxy_delete_body, proxy.config.server)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemove, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.proxy_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissRemove, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/**
 * Řádek volby připojení. Vybraná volba má fajfku a `secondaryContainer`, jako vybraný filtr —
 * výběr je selekční stav, ne zvýraznění obsahu.
 */
@Composable
private fun ProxyRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    shapeIndex: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: (@Composable () -> Unit)? = null,
) {
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val stateText = stringResource(Res.string.proxy_state_on)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { RowIcon(if (selected) Res.drawable.check_24dp else Res.drawable.vpn_lock_24dp, accent = selected) },
        trailingContent = menu,
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = content,
            supportingContentColor = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            trailingContentColor = content,
        ),
        modifier = modifier
            .clip(groupItemShape(shapeIndex, count))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = stateText },
    )
}

@Composable
private fun ProxyMenu(proxy: SavedProxy, viewModel: ProxySettingsViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.proxy_menu))
        }
        val edit = stringResource(Res.string.proxy_edit)
        val copy = stringResource(Res.string.proxy_copy_link)
        val delete = stringResource(Res.string.proxy_delete)
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                buildList {
                    add(MenuAction(edit, Res.drawable.edit_24dp) { open = false; viewModel.edit(proxy) })
                    if (proxy.config.shareLink() != null) {
                        add(MenuAction(copy, Res.drawable.content_copy_24dp) { open = false; viewModel.copyLink(proxy) })
                    }
                },
                listOf(MenuAction(delete, Res.drawable.delete_24dp, destructive = true) { open = false; viewModel.askRemove(proxy) }),
            ),
        )
    }
}

/** Ikona v tónovaném kolečku, jako u složek a pozvánek. */
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

private enum class KindChoice { Mtproto, Socks5, Http }

/**
 * Editor proxy. Pole drží sheet sám ([rememberSaveable]) a ViewModel dostane hotovou
 * [ProxyConfig] — psaní přes `StateFlow` by poskakovalo kurzorem. Co chybí, říká [problem]
 * hned pod polem a Uložit zašedne; jestli proxy opravdu funguje, se pozná až z odezvy v seznamu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProxyEditorSheet(
    initial: ProxyConfig?,
    saving: Boolean,
    error: String?,
    onSave: (ProxyConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val initialKind = initial?.kind
    var kind by rememberSaveable {
        mutableStateOf(
            when (initialKind) {
                is ProxyKind.Socks5 -> KindChoice.Socks5
                is ProxyKind.Http -> KindChoice.Http
                else -> KindChoice.Mtproto
            },
        )
    }
    var server by rememberSaveable { mutableStateOf(initial?.server.orEmpty()) }
    var port by rememberSaveable { mutableStateOf(initial?.port?.toString().orEmpty()) }
    var secret by rememberSaveable { mutableStateOf((initialKind as? ProxyKind.Mtproto)?.secret.orEmpty()) }
    var username by rememberSaveable {
        mutableStateOf(
            when (initialKind) {
                is ProxyKind.Socks5 -> initialKind.username
                is ProxyKind.Http -> initialKind.username
                else -> ""
            },
        )
    }
    var password by rememberSaveable {
        mutableStateOf(
            when (initialKind) {
                is ProxyKind.Socks5 -> initialKind.password
                is ProxyKind.Http -> initialKind.password
                else -> ""
            },
        )
    }
    var httpOnly by rememberSaveable { mutableStateOf((initialKind as? ProxyKind.Http)?.httpOnly ?: false) }
    // Chyby se ukazují až u pole, na které uživatel sáhl, ne hned v prázdném formuláři.
    var touched by rememberSaveable { mutableStateOf(initial != null) }

    val config = ProxyConfig(
        server = server.trim(),
        port = port.trim().toIntOrNull() ?: 0,
        kind = when (kind) {
            KindChoice.Mtproto -> ProxyKind.Mtproto(secret.trim())
            KindChoice.Socks5 -> ProxyKind.Socks5(username, password)
            KindChoice.Http -> ProxyKind.Http(username, password, httpOnly)
        },
    )
    val problem = config.problem()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(if (initial == null) Res.string.proxy_editor_add_title else Res.string.proxy_editor_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                KindChoice.entries.forEachIndexed { index, choice ->
                    val checked = choice == kind
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { kind = choice },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            KindChoice.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (checked) {
                            Icon(
                                painterResource(Res.drawable.check_24dp),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize),
                            )
                        }
                        Text(
                            proxyKindLabel(
                                when (choice) {
                                    KindChoice.Mtproto -> ProxyKind.Mtproto("")
                                    KindChoice.Socks5 -> ProxyKind.Socks5()
                                    KindChoice.Http -> ProxyKind.Http()
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it; touched = true },
                    label = { Text(stringResource(Res.string.proxy_field_server)) },
                    singleLine = true,
                    isError = touched && problem == ProxyProblem.MissingServer,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter(Char::isDigit).take(5); touched = true },
                    label = { Text(stringResource(Res.string.proxy_field_port)) },
                    singleLine = true,
                    isError = touched && problem == ProxyProblem.InvalidPort,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }
            if (kind == KindChoice.Mtproto) {
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it; touched = true },
                    label = { Text(stringResource(Res.string.proxy_field_secret)) },
                    singleLine = true,
                    isError = touched && problem == ProxyProblem.MissingSecret,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.proxy_field_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.proxy_field_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(kind == KindChoice.Http) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Checkbox) { httpOnly = !httpOnly },
                ) {
                    Checkbox(checked = httpOnly, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(stringResource(Res.string.proxy_field_http_only), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val message = error ?: problem?.takeIf { touched }?.let {
                stringResource(
                    when (it) {
                        ProxyProblem.MissingServer -> Res.string.proxy_problem_server
                        ProxyProblem.InvalidPort -> Res.string.proxy_problem_port
                        ProxyProblem.MissingSecret -> Res.string.proxy_problem_secret
                    },
                )
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = { onSave(config) },
                enabled = problem == null && !saving,
                modifier = Modifier.align(Alignment.End),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(Res.string.proxy_save))
            }
        }
    }
}
