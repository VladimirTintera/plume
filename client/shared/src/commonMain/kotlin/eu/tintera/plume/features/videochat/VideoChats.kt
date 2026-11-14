package eu.tintera.plume.features.videochat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.Accounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.koinInject

/** Běžící videochat ([chat]) a účet, ve kterém posluchač je. */
data class AccountVideoChat(val account: AccountSession, val chat: ActiveVideoChat) {
    val session: VideoChatSession get() = account.get()
}

/**
 * Videochat přes všechny účty (`docs/navrh-vice-uctu.md`, krok 14). Sezení má každý účet vlastní
 * ([VideoChatSession], hovor jde přes jeho klienta TDLib), přenos (`LiveStreamMedia`) i služba
 * v popředí jsou ale v procesu jednou — běží proto nejvýš jeden videochat: připojení v jednom
 * účtu opustí videochat v ostatních (`VideoChatSession.leaveElsewhere`).
 *
 * [running] je ten běžící, s účtem: podle něj se skládá pruh nad aplikací ([VideoChatAccountScope])
 * a řídí služba s notifikací, která tak žádnému účtu nepatří a přepnutí účtu ji neukončí.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoChats(
    /** Až za běhu: v globálním Koinu se na účty při vzniku definice sahat nesmí. */
    accounts: () -> Accounts,
    scope: CoroutineScope,
) {
    val running: StateFlow<AccountVideoChat?> = flow { emitAll(accounts().all) }
        .flatMapLatest { all ->
            if (all.isEmpty()) {
                flowOf(null)
            } else {
                combine(
                    all.map { account ->
                        account.get<VideoChatSession>().active.map { chat -> chat?.takeIf { it.running }?.let { AccountVideoChat(account, it) } }
                    },
                ) { chats -> chats.firstNotNullOfOrNull { it } }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)
}

/**
 * [content] v účtu, ve kterém posluchač je ve videochatu; bez videochatu ve výchozím. Pro pruh
 * videochatu nad aplikací, jako `CallAccountScope` pro pruh hovoru.
 */
@Composable
fun VideoChatAccountScope(content: @Composable () -> Unit) {
    val running by koinInject<VideoChats>().running.collectAsStateWithLifecycle()
    val default by koinInject<Accounts>().default.collectAsStateWithLifecycle()
    val account = running?.account ?: default
    key(account.slot) { AccountScope(account, content) }
}
