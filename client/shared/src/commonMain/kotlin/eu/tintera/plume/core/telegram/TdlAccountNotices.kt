package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [AccountNotices] nad TDLib. **Musí vznikat eagerně** (`createdAtStart`): zmrazení TDLib pošle
 * hned při načtení účtu (`UserManager` ho má v binlogu) a podmínky po prvním dotazu na server —
 * odběr, který by vznikl až s obrazovkou, by je minul a TDLib je do dalšího startu nezopakuje.
 */
class TdlAccountNotices(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : AccountNotices {

    private val logger = Logger.withTag("AccountNotices")

    private val _serviceNotices = MutableStateFlow<List<ServiceNotice>>(emptyList())
    override val serviceNotices: StateFlow<List<ServiceNotice>> = _serviceNotices.asStateFlow()

    private val _termsOfService = MutableStateFlow<TermsOfServiceUpdate?>(null)
    override val termsOfService: StateFlow<TermsOfServiceUpdate?> = _termsOfService.asStateFlow()

    private val _freeze = MutableStateFlow<AccountFreeze?>(null)
    override val freeze: StateFlow<AccountFreeze?> = _freeze.asStateFlow()

    private var nextNoticeId = 0L

    init {
        scope.launch {
            holder.updates { serviceNotificationUpdates }.collect { update ->
                logger.i { "Oznámení od služby: ${update.type}" }
                val body = update.content.noticeBody()
                val notice = ServiceNotice(
                    id = nextNoticeId++,
                    text = body.text,
                    entities = body.entities,
                    offersLogOut = update.type.startsWith(AUTH_KEY_DROP_PREFIX),
                )
                _serviceNotices.update { it + notice }
            }
        }
        scope.launch {
            holder.updates { termsOfServiceUpdates }.collect { update ->
                val terms = update.termsOfService
                val body = terms.text.toBody()
                _termsOfService.value = TermsOfServiceUpdate(
                    id = update.termsOfServiceId,
                    text = body.text,
                    entities = body.entities,
                    minUserAge = terms.minUserAge,
                    blocking = terms.showPopup,
                )
            }
        }
        scope.launch {
            holder.updates { freezeStateUpdates }.collect { update ->
                _freeze.value = if (update.isFrozen) {
                    AccountFreeze(
                        frozenAt = update.freezingDate,
                        deletionAt = update.deletionDate,
                        appealLink = update.appealLink.takeIf { it.isNotBlank() },
                    )
                } else {
                    null
                }
            }
        }
        // Odhlášení: nový účet nic z toho starého nezdědí.
        scope.launch {
            holder.replaced.collect {
                _serviceNotices.value = emptyList()
                _termsOfService.value = null
                _freeze.value = null
            }
        }
    }

    override fun dismissServiceNotice(id: Long) {
        _serviceNotices.update { notices -> notices.filterNot { it.id == id } }
    }

    override suspend fun acceptTermsOfService(): Result<Unit> {
        val terms = _termsOfService.value ?: return Result.success(Unit)
        return holder.client.acceptTermsOfService(termsOfServiceId = terms.id)
            .toResult()
            .map { _termsOfService.value = null }
    }

    override fun postponeTermsOfService() {
        _termsOfService.value = null
    }

    private companion object {
        const val AUTH_KEY_DROP_PREFIX = "AUTH_KEY_DROP_"
    }
}

/**
 * Text oznámení. Bývá to holý text, výjimečně médium s popiskem — médium samo se v popupu
 * neukazuje, popisek ano. Bez textu zůstane jen nadpis dialogu, což je pořád lepší než nic.
 */
private fun MessageContent.noticeBody(): MessageBody = when (this) {
    is MessageText -> text.toBody()
    is MessagePhoto -> caption.toBody()
    is MessageVideo -> caption.toBody()
    is MessageAnimation -> caption.toBody()
    is MessageDocument -> caption.toBody()
    else -> MessageBody(text = "")
}
