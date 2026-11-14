package eu.tintera.plume.features.links

import eu.tintera.plume.features.conversation.DeepLinkInfoDialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.navigation.LinkOpenRequest
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.conversation.FolderInviteSheet
import eu.tintera.plume.features.conversation.InviteSheet
import eu.tintera.plume.features.conversation.LoginLinkWarningDialog
import eu.tintera.plume.features.conversation.StickerSetSheet
import eu.tintera.plume.features.proxy.ProxyLinkHost
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Co odkaz zvenčí ukáže nad aplikací ([ExternalLinkViewModel]): náhled pozvánky a sdílené
 * složky, sadu samolepek, proxy, varování před přihlašovacím odkazem a hlášky. Kam odkaz vede,
 * řeší [onNavigate] — navigace je věc shellu.
 *
 * Snackbar má vlastní, nad celým shellem: odkaz může přijít nad kteroukoli obrazovkou, a ta
 * o něm neví. Sada samolepek se jen prohlíží — poslat samolepku není kam.
 *
 * Host je za každý přihlášený účet ([viewModel] z jeho kontejneru) a vyzvedne jen odkazy, které
 * mu patří ([accepts]): se slotem svého účtu, výchozí účet i ty bez slotu (zvenčí).
 */
@Composable
fun ExternalLinkHost(
    viewModel: ExternalLinkViewModel,
    accepts: (LinkOpenRequest) -> Boolean,
    onNavigate: (ExternalDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requests = koinInject<LinkOpenRequests>()
    val request by requests.request.collectAsStateWithLifecycle()
    val currentAccepts by rememberUpdatedState(accepts)
    LaunchedEffect(request) {
        request?.takeIf(currentAccepts)?.let(viewModel::open)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentOnNavigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(state.destination) {
        val destination = state.destination ?: return@LaunchedEffect
        viewModel.consumeDestination()
        currentOnNavigate(destination)
    }

    // Snackbar mimo efekt: vynulování mění klíč efektu a zrušilo by ho (past „Vrátit").
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        val text = message.resolveNow()
        snackbarScope.launch { snackbarHostState.showSnackbar(text) }
    }

    state.invite?.let { invite ->
        InviteSheet(invite = invite, onJoin = viewModel::joinInvite, onDismiss = viewModel::dismissInvite)
    }
    state.folderInvite?.let { invite ->
        FolderInviteSheet(
            invite = invite,
            onToggle = viewModel::toggleFolderInviteChat,
            onAdd = viewModel::addFolderInvite,
            onDismiss = viewModel::dismissFolderInvite,
        )
    }
    state.stickerSet?.let { setId ->
        StickerSetSheet(setId = setId, onPick = null, onDismiss = viewModel::dismissStickerSet)
    }
    state.loginWarning?.let { kind ->
        LoginLinkWarningDialog(kind = kind, onDismiss = viewModel::dismissLoginWarning)
    }
    state.deepLinkInfo?.let { info ->
        DeepLinkInfoDialog(info = info, onLinkClick = viewModel::openDeepLinkInfoLink, onDismiss = viewModel::dismissDeepLinkInfo)
    }
    if (state.accountChoice != null) {
        AccountChoiceSheet(
            current = LocalAccount.current.slot,
            onChoose = viewModel::chooseAccount,
            onDismiss = viewModel::dismissAccountChoice,
        )
    }
    ProxyLinkHost(viewModel.proxyLinkFlow, snackbarHostState)

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    )
}
