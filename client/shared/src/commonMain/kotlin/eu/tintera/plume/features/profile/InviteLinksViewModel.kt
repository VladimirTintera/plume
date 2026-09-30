package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.invite_links_share_failed
import eu.tintera.plume.core.files.MediaSharer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.InviteLink
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.telegram.InviteLinks
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.invite_links_copied
import eu.tintera.plume.resources.invite_links_created
import eu.tintera.plume.resources.invite_links_delete_failed
import eu.tintera.plume.resources.invite_links_load_failed
import eu.tintera.plume.resources.invite_links_revoke_failed
import eu.tintera.plume.resources.invite_links_revoked
import eu.tintera.plume.resources.invite_links_save_failed
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Pozvánky jednoho chatu: hlavní odkaz, další odkazy s limitem, platností a schvalováním,
 * zrušené odkazy.
 *
 * **Po každé změně se seznam čte znovu** ([reload]), nic se nepřepisuje lokálně — zrušení
 * hlavního odkazu vyrobí na serveru nový a jeho adresu zná jen server.
 */
class InviteLinksViewModel(
    private val chatId: Long,
    private val repository: InviteLinkRepository,
    private val clipboard: TextClipboard,
    private val sharer: MediaSharer,
) : ViewModel() {

    private val logger = Logger.withTag("InviteLinks")

    var links by mutableStateOf<InviteLinks?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    /** Běží vytvoření, úprava nebo zrušení — tlačítka se nesmí zmáčknout podruhé. */
    var busy by mutableStateOf(false)
        private set

    /** Otevřený formulář odkazu; `null` = zavřený. */
    var editor by mutableStateOf<InviteLinkEditor?>(null)
        private set

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    val now: Long get() = Clock.System.now().epochSeconds

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            repository.links(chatId)
                .onSuccess {
                    links = it
                    loadFailed = false
                }
                .onFailure { failure ->
                    // Načtený seznam zůstává; chyba se jen ohlásí.
                    if (links == null) loadFailed = true
                    notice = failure.reported(Res.string.invite_links_load_failed)
                }
        }
    }

    fun copy(link: InviteLink) {
        viewModelScope.launch {
            clipboard.copy(link.url)
            notice = UiText(Res.string.invite_links_copied)
        }
    }

    /** Systémové sdílení odkazu — poslat ho někomu mimo Telegram (zpráva, e-mail). */
    fun share(link: InviteLink) {
        if (!sharer.shareText(link.url)) notice = UiText(Res.string.invite_links_share_failed)
    }

    fun openCreate() {
        editor = InviteLinkEditor()
    }

    fun openEdit(link: InviteLink) {
        editor = InviteLinkEditor.of(link)
    }

    fun updateEditor(transform: (InviteLinkEditor) -> InviteLinkEditor) {
        editor = editor?.let(transform)
    }

    fun closeEditor() {
        editor = null
    }

    fun saveEditor() {
        val current = editor ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val draft = current.toDraft(now)
            val result = current.url?.let { repository.edit(chatId, it, draft) } ?: repository.create(chatId, draft)
            result
                .onSuccess { link ->
                    editor = null
                    // Nový odkaz se rovnou zkopíruje — vytváří se proto, aby se poslal dál.
                    if (current.isNew) {
                        clipboard.copy(link.url)
                        notice = UiText(Res.string.invite_links_created)
                    }
                    reload()
                }
                .onFailure { notice = it.reported(Res.string.invite_links_save_failed) }
            busy = false
        }
    }

    fun revoke(link: InviteLink) = act(Res.string.invite_links_revoke_failed, Res.string.invite_links_revoked) {
        repository.revoke(chatId, link.url)
    }

    fun deleteRevoked(link: InviteLink) = act(Res.string.invite_links_delete_failed) {
        repository.deleteRevoked(chatId, link.url)
    }

    fun deleteAllRevoked() = act(Res.string.invite_links_delete_failed) {
        repository.deleteAllRevoked(chatId)
    }

    private fun act(failed: StringResource, done: StringResource? = null, action: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            action()
                .onSuccess {
                    done?.let { notice = UiText(it) }
                    reload()
                }
                .onFailure { notice = it.reported(failed) }
            busy = false
        }
    }

    /** Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`. */
    private fun Throwable.reported(fallback: StringResource): UiText {
        logger.w(this) { "Pozvánky chatu $chatId: akce selhala." }
        return userMessage(fallback)
    }
}
