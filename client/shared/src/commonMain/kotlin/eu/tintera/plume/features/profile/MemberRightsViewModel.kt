package eu.tintera.plume.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AdminRights
import eu.tintera.plume.core.telegram.GroupPermissions
import eu.tintera.plume.core.telegram.MemberDetail
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.member_load_failed
import eu.tintera.plume.resources.profile_action_failed
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Práva jednoho člena: buď správcovská ([admin] `true`), nebo jeho omezení.
 *
 * Jedna obrazovka pro obojí, protože je to tatáž věc — seznam přepínačů nad jedním člověkem —
 * a dvě skoro stejné obrazovky by se rozešly.
 *
 * **Ukládá se tlačítkem, ne po jednom přepínači** — na rozdíl od výchozích práv skupiny
 * v [EditChatViewModel]. Důvody jsou dva: každý přepínač by byl vlastní `setChatMemberStatus`,
 * tedy série změn postavení na serveru, a u omezení se zároveň vybírá doba, která bez přepínačů
 * nedává smysl.
 */
class MemberRightsViewModel(
    private val chatId: Long,
    private val userId: Long,
    val admin: Boolean,
    private val repository: ProfileRepository,
) : ViewModel() {

    private val logger = Logger.withTag("MemberRights")

    var member by mutableStateOf<MemberDetail?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    var rights by mutableStateOf(AdminRights())
        private set
    var permissions by mutableStateOf<GroupPermissions?>(null)
        private set

    /** Doba omezení; `null` = navždy. U práv správce se neuplatní. */
    var duration by mutableStateOf<Duration?>(null)
        private set

    /**
     * Kolik z už běžícího omezení zbývá. Nabídne se jako volba „ponechat", aby změna jednoho
     * přepínače člověku omezenému na týden nepřepsala konec na „navždy".
     */
    var remaining by mutableStateOf<Duration?>(null)
        private set

    /**
     * Uživatel běžící omezení nechává, jak je. Vlastní příznak, ne porovnání [duration]
     * s [remaining]: zbytek omezení může vyjít přesně na některou z nabízených dob a obě volby
     * by se pak tvářily jako vybrané.
     */
    var keepsCurrentEnd by mutableStateOf(false)
        private set

    var saving by mutableStateOf(false)
        private set

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    /** Jednorázová událost: uloženo, obrazovka se zavře. */
    var saved by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repository.member(chatId, userId)
                .onSuccess { detail ->
                    member = detail
                    rights = detail.adminRights
                    permissions = detail.permissions
                    remaining = detail.restrictedUntil?.let { end ->
                        (end - Clock.System.now().epochSeconds).coerceAtLeast(0L).seconds
                    }
                    duration = remaining
                    keepsCurrentEnd = remaining != null
                }
                .onFailure { failure ->
                    loadFailed = true
                    notice = failure.reported(Res.string.member_load_failed)
                }
        }
    }

    fun setRights(change: (AdminRights) -> AdminRights) {
        rights = change(rights)
    }

    fun setPermissions(change: (GroupPermissions) -> GroupPermissions) {
        permissions = permissions?.let(change)
    }

    fun chooseDuration(value: Duration?) {
        duration = value
        keepsCurrentEnd = false
    }

    /** Ponechat konec, který omezení už má. */
    fun keepCurrentEnd() {
        duration = remaining
        keepsCurrentEnd = true
    }

    /**
     * Je co ukládat. U správce se počítá i změna z „žádná práva" na nějaká (povýšení) a zpátky
     * (sesazení); u omezení i změna samotné doby.
     */
    val changed: Boolean
        get() {
            val detail = member ?: return false
            return if (admin) {
                rights != detail.adminRights
            } else {
                permissions != detail.permissions || !keepsCurrentEnd && duration != remaining
            }
        }

    val canSave: Boolean get() = changed && !saving

    fun save() {
        val detail = member ?: return
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            val result = if (admin) {
                repository.setAdminRights(chatId, userId, rights)
            } else {
                repository.restrictMember(
                    chatId = chatId,
                    userId = userId,
                    permissions = permissions ?: detail.permissions,
                    duration = duration,
                )
            }
            saving = false
            result
                .onSuccess { saved = true }
                .onFailure { notice = it.reported(Res.string.profile_action_failed) }
        }
    }

    fun noticeShown() {
        notice = null
    }

    /** Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`. */
    private fun Throwable.reported(fallback: StringResource): UiText {
        logger.w(this) { "Práva člena $userId v chatu $chatId selhala." }
        return userMessage(fallback)
    }
}
