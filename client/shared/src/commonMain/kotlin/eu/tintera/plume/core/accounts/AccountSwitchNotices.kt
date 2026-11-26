package eu.tintera.plume.core.accounts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.account_switched_to
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Výchozí účet se přepnul kvůli otevření chatu zvenčí (notifikace, zástupce na ploše) a konverzace
 * to má jednou ohlásit ([AccountSwitchSnackbar]). Mimo režim Všechny účty takové otevření účet
 * přepne potichu (`AppShell`) a uživatel je rovnou v konverzaci, takže by nepoznal, že teď
 * píše za jiný účet než před chvílí. Pole „Zpráva jako …" to dnes neříká u každého chatu
 * (`rememberConversationAccount`), proto ohlášení.
 *
 * Jedno na proces: přepnutí složí shell znovu od kořene a konverzaci otevře shell nového účtu.
 */
class AccountSwitchNotices(private val time: TimeSource = TimeSource.Monotonic) {

    private val pending = MutableStateFlow<Notice?>(null)

    internal val notice: StateFlow<Notice?> = pending.asStateFlow()

    /** Výchozím účtem se stal [slot], aby se otevřel jeho chat [chatId]. */
    fun switchedFor(slot: AccountSlot, chatId: Long) {
        pending.value = Notice(slot, chatId, time.markNow())
    }

    /**
     * Patří ohlášení konverzaci [chatId] účtu [slot]? Vyzvedne ho, takže se ukáže jednou. Staré
     * ohlášení propadne: chat se otevřít nemusel (účet se mezitím odhlásil) a pozdější otevření
     * téhož chatu by jinak hlásilo přepnutí, které se nestalo.
     */
    internal fun consume(slot: AccountSlot, chatId: Long): Boolean {
        val current = pending.value ?: return false
        if (current.slot != slot || current.chatId != chatId) return false
        if (!pending.compareAndSet(current, null)) return false
        return current.at.elapsedNow() < NOTICE_TTL
    }

    internal data class Notice(val slot: AccountSlot, val chatId: Long, val at: TimeMark)
}

/** Ohlášení, že se kvůli téhle konverzaci přepnul výchozí účet ([AccountSwitchNotices]). */
@Composable
fun AccountSwitchSnackbar(chatId: Long, snackbarHostState: SnackbarHostState) {
    val account = LocalAccount.current
    val notices = koinInject<AccountSwitchNotices>()
    val notice by notices.notice.collectAsStateWithLifecycle()
    // Snackbar běží mimo efekt: vyzvednutí mění klíč a restart efektu by ho zrušil.
    val scope = rememberCoroutineScope()
    LaunchedEffect(notice) {
        if (!notices.consume(account.slot, chatId)) return@LaunchedEffect
        // `me()` je dotaz do místní databáze TDLib, ne na síť.
        val name = account.session.me().getOrNull()?.displayName?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val message = getString(Res.string.account_switched_to, name)
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
}

private val NOTICE_TTL = 15.seconds
