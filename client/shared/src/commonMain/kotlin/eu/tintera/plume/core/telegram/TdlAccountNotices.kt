package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.SuggestedActionAddLoginPasskey
import dev.g000sha256.tdl.dto.SuggestedActionCheckPassword
import dev.g000sha256.tdl.dto.SuggestedActionCheckPhoneNumber
import dev.g000sha256.tdl.dto.SuggestedActionConvertToBroadcastGroup
import dev.g000sha256.tdl.dto.SuggestedActionCustom
import dev.g000sha256.tdl.dto.SuggestedActionEnableArchiveAndMuteNewChats
import dev.g000sha256.tdl.dto.SuggestedActionExtendPremium
import dev.g000sha256.tdl.dto.SuggestedActionExtendStarSubscriptions
import dev.g000sha256.tdl.dto.SuggestedActionGiftPremiumForChristmas
import dev.g000sha256.tdl.dto.SuggestedActionRestorePremium
import dev.g000sha256.tdl.dto.SuggestedActionSetBirthdate
import dev.g000sha256.tdl.dto.SuggestedActionSetLoginEmailAddress
import dev.g000sha256.tdl.dto.SuggestedActionSetPassword
import dev.g000sha256.tdl.dto.SuggestedActionSetProfilePhoto
import dev.g000sha256.tdl.dto.SuggestedActionSubscribeToAnnualPremium
import dev.g000sha256.tdl.dto.SuggestedActionUpgradePremium
import dev.g000sha256.tdl.dto.SuggestedActionViewChecksHint
import dev.g000sha256.tdl.dto.SuggestedAction as TdlSuggestedAction
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

    private val _closeBirthdays = MutableStateFlow<List<CloseBirthday>>(emptyList())
    override val closeBirthdays: StateFlow<List<CloseBirthday>> = _closeBirthdays.asStateFlow()

    private val _suggestedActions = MutableStateFlow<List<SuggestedAction>>(emptyList())
    override val suggestedActions: StateFlow<List<SuggestedAction>> = _suggestedActions.asStateFlow()

    /** Objekt od TDLib ke každé akci — `hideSuggestedAction` chce zpátky přesně ten. */
    private val tdlActions = mutableMapOf<SuggestedAction, TdlSuggestedAction>()

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
        // Přidané a odebrané akce: TDLib po startu pošle všechny aktuální jako přidané.
        scope.launch {
            holder.updates { suggestedActionsUpdates }.collect { update ->
                val removed = update.removedActions.mapNotNull { it.toDomain() }.toSet()
                val added = update.addedActions.mapNotNull { tdl -> tdl.toDomain()?.also { tdlActions[it] = tdl } }
                removed.forEach { tdlActions.remove(it) }
                _suggestedActions.update { current -> (current - removed + added).distinct() }
            }
        }
        // Narozeniny kontaktů: TDLib pošle seznam po startu a pak při každé změně (i po schování).
        // Jméno a fotka se dočítají lokálně — kontakt TDLib zná.
        scope.launch {
            holder.updates { contactCloseBirthdaysUpdates }.collect { update ->
                _closeBirthdays.value = update.closeBirthdayUsers.mapNotNull { close ->
                    val user = holder.client.getUser(userId = close.userId).toResult()
                        .onFailure { logger.w(it) { "Kontakt s narozeninami se nepodařilo dočíst." } }
                        .getOrNull() ?: return@mapNotNull null
                    CloseBirthday(
                        userId = user.id,
                        name = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
                        photoFileId = user.profilePhoto?.small?.id,
                        birthdate = close.birthdate.toDomain(),
                    )
                }
            }
        }
        // Odhlášení: nový účet nic z toho starého nezdědí.
        scope.launch {
            holder.replaced.collect {
                _serviceNotices.value = emptyList()
                _termsOfService.value = null
                _freeze.value = null
                _suggestedActions.value = emptyList()
                _closeBirthdays.value = emptyList()
                tdlActions.clear()
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

    /**
     * Schová akci na serveru. Ze seznamu ji vyřadí až update, který po tom TDLib pošle — kdyby
     * server schování odmítl, karta zůstane, jak má.
     */
    override suspend fun hideSuggestedAction(action: SuggestedAction): Result<Unit> {
        val tdl = tdlActions[action] ?: return Result.success(Unit)
        return holder.client.hideSuggestedAction(action = tdl).toResult().map { }
    }

    /** Ze seznamu je vyřadí až update od TDLib, jako u doporučených akcí. */
    override suspend fun hideCloseBirthdays(): Result<Unit> =
        holder.client.hideContactCloseBirthdays().toResult().map { }
            .onFailure { logger.w(it) { "Narozeniny se nepodařilo schovat." } }

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

/**
 * Akce, kterou Plume umí nabídnout, nebo `null`. `when` je bez `else`: nová akce v novější TDLib
 * rozbije překlad a někdo rozhodne, jestli ji nabízet — tiše vynechaná by mohla být právě ta,
 * kterou Telegram vyžaduje.
 */
internal fun TdlSuggestedAction.toDomain(): SuggestedAction? = when (this) {
    is SuggestedActionCheckPassword -> SuggestedAction.CheckPassword
    is SuggestedActionCheckPhoneNumber -> SuggestedAction.CheckPhoneNumber
    is SuggestedActionSetPassword -> SuggestedAction.SetPassword(authorizationDelay.coerceAtLeast(0))
    is SuggestedActionSetLoginEmailAddress -> SuggestedAction.SetLoginEmail(canBeHidden)
    is SuggestedActionSetProfilePhoto -> SuggestedAction.SetProfilePhoto
    is SuggestedActionSetBirthdate -> SuggestedAction.SetBirthdate
    is SuggestedActionEnableArchiveAndMuteNewChats -> SuggestedAction.ArchiveUnknownChats
    is SuggestedActionCustom -> SuggestedAction.Custom(name = name, title = title.text, description = description.text, url = url)
    // Placené služby Plume nepropaguje (Premium, hvězdy, dárky); převod skupiny na kanál
    // nastavit neumí; nápověda k fajfkám je v aplikaci jinde. Přístupový klíč přidat
    // NEJDE: Android a iOS ho k doméně telegram.org dovolí vytvořit jen aplikacím, které
    // Telegram uvádí na webu (assetlinks.json, apple-app-site-association).
    is SuggestedActionAddLoginPasskey, is SuggestedActionConvertToBroadcastGroup,
    is SuggestedActionExtendPremium, is SuggestedActionExtendStarSubscriptions,
    is SuggestedActionGiftPremiumForChristmas, is SuggestedActionRestorePremium,
    is SuggestedActionSubscribeToAnnualPremium,
    is SuggestedActionUpgradePremium, is SuggestedActionViewChecksHint,
        -> null
}

