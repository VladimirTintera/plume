package eu.tintera.plume.features.calls

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.features.conversation.CallRequest
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_start
import eu.tintera.plume.resources.call_start_video
import eu.tintera.plume.resources.videocam_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Hovor z hlavičky konverzace: sluchátko a kamera jako hlavní akce lišty, zbytek je za ⋮
 * (`ConversationMenu`). Kamera jen tehdy, když videohovor umí obě strany; jinak zbude samotné
 * sluchátko. Jen v soukromém a tajném chatu s člověkem, který hovory přijímá
 * ([ChatInfo.callUserId]), a jen když zrovna žádný hovor neběží.
 *
 * Vyřizuje i zavolání zpátky ze zprávy o hovoru ([request]) — vytočení potřebuje dotaz na
 * mikrofon a kameru, a to umí jen kompozice, ne ViewModel. Je ve vlastní funkci, ne
 * v `ConversationScreen`: ta je v dexu na hraně (viz CLAUDE.md, Gotchas).
 */
@Composable
fun ConversationCallButtons(info: ChatInfo?, request: CallRequest?, onRequestHandled: () -> Unit) {
    val userId = info?.callUserId
    val start = rememberStartCall(userId) ?: return
    val calls = koinInject<CallRepository>()
    val active by calls.active.collectAsStateWithLifecycle()
    val busy = active?.phase?.let { it != CallPhase.Ended && it != CallPhase.Failed } == true

    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        onRequestHandled()
        if (!busy) start(request.video)
    }
    if (busy) return

    if (info?.callSupportsVideo == true && calls.supportsVideo) {
        IconButton(onClick = { start(true) }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.videocam_24dp), contentDescription = stringResource(Res.string.call_start_video))
        }
    }
    IconButton(onClick = { start(false) }, shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(Res.drawable.call_24dp), contentDescription = stringResource(Res.string.call_start))
    }
}
