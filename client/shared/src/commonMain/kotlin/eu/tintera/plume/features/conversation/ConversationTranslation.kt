package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageBody
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.translation.OnDeviceTranslator
import eu.tintera.plume.core.translation.deviceTranslationCode
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.server_summary_copied
import eu.tintera.plume.resources.server_summary_failed
import eu.tintera.plume.resources.translation_copied
import eu.tintera.plume.resources.translation_device_download_failed
import eu.tintera.plume.resources.translation_device_same
import eu.tintera.plume.resources.translation_device_unknown
import eu.tintera.plume.resources.translation_device_unsupported
import eu.tintera.plume.resources.translation_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Překlad zprávy (na serveru, nebo v telefonu se stažením jazykových balíčků) a shrnutí od
 * serveru v témž sheetu. Jazyk překladu se pamatuje v nastavení.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationTranslation(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val settings: AppSettings,
    /** Překlad v telefonu; bez něj (iOS) přepínač v sheetu není. */
    private val onDeviceTranslator: OnDeviceTranslator?,
    private val languageDetector: LanguageDetector,
    private val copyToClipboard: suspend (String) -> Unit,
    /** Potvrdí zkopírování sám systém (`TextClipboard.confirmsCopy`)? Pak hláška od nás není. */
    private val clipboardConfirmsCopy: Boolean,
    private val logger: Logger,
) {
    /**
     * Přeloží text zprávy do zvoleného jazyka ([AppSettings.translationLanguage], jinak jazyk
     * aplikace, [platformLanguageCode]) a ukáže ho v sheetu.
     *
     * Sheet se otevře hned s načítáním — překlad jde přes server a trvá. Selhání zůstane v sheetu
     * jako věta, ne snackbar: pod modálním sheetem by snackbar nebyl vidět.
     */
    fun showTranslation(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        state.update { it.copy(messageMenu = null) }
        translate(message, translationTarget())
    }

    /** Jazyk překladu: zvolený v nastavení, jinak jazyk aplikace. */
    fun translationTarget(): String =
        settings.translationLanguage.value ?: normalizedLanguageCode(platformLanguageCode)

    /**
     * Shrnutí zprávy od serveru (`summarizeMessage`) v témž sheetu jako překlad a ve stejném
     * jazyce — kdo si překlady nastavil do češtiny, chce v ní i shrnutí. Nabízí se jen u zprávy,
     * u které ho server dovolí ([ChatMessage.summaryLanguage]).
     */
    fun showSummary(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val canCopy = state.value.messageMenu?.actions?.canCopy ?: false
        state.update { it.copy(messageMenu = null) }
        translate(
            message,
            translationTarget(),
            summary = true,
            canCopy = canCopy,
        )
    }

    /**
     * Přeloží otevřenou zprávu do jiného jazyka a jazyk si zapamatuje pro další překlady. Jazyk
     * aplikace se neukládá jako kód, ale jako „výchozí" (`null`) — po změně jazyka aplikace se
     * pak překládá do nového.
     */
    fun changeTranslationLanguage(code: String) {
        val open = state.value.sheet<TranslationSheet>() ?: return
        settings.setTranslationLanguage(code.takeUnless { it == normalizedLanguageCode(platformLanguageCode) })
        translate(open.message, code, open.summary, open.canCopy)
    }

    private fun translate(
        message: ChatMessage,
        language: String,
        summary: Boolean = false,
        canCopy: Boolean = true,
        forceServer: Boolean = false,
    ) {
        // Shrnutí dělá jen server; překlad v telefonu, když si ho uživatel zapnul.
        val translator = onDeviceTranslator?.takeUnless { summary }
        val onDevice = translator != null && settings.translateOnDevice.value && !forceServer
        state.update {
            it.copy(
                sheet = TranslationSheet(
                    message,
                    language,
                    summary = summary,
                    canCopy = canCopy,
                    onDevice = if (translator != null) onDevice else null,
                ),
            )
        }
        if (onDevice) {
            translateOnDevice(message, language, download = false)
            return
        }

        scope.launch {
            val result = if (summary) {
                // Ve vlastním jazyce zprávy se shrnutí nepřekládá — server by překládal sám do sebe.
                val target = language.takeUnless { it == message.summaryLanguage?.let(::normalizedLanguageCode) }
                repository.summarizeMessage(chatId, message.id, target)
            } else {
                repository.translateMessage(chatId, message.id, language)
            }
            state.update { current ->
                // Mezitím mohl uživatel sheet zavřít, otevřít u jiné zprávy nebo vybrat jiný jazyk.
                val open = current.sheet<TranslationSheet>()
                    ?.takeIf { it.message.id == message.id && it.languageCode == language && it.summary == summary && it.onDevice != true }
                    ?: return@update current
                current.copy(
                    sheet = result.fold(
                        onSuccess = { open.copy(body = it) },
                        onFailure = { failure ->
                            logger.w(failure) { if (summary) "Zprávu se nepodařilo shrnout." else "Zprávu se nepodařilo přeložit." }
                            open.copy(
                                error = failure.userMessage(
                                    if (summary) Res.string.server_summary_failed else Res.string.translation_failed,
                                ),
                            )
                        },
                    ),
                )
            }
        }
    }

    fun dismissTranslation() {
        state.update { it.closingSheet<TranslationSheet>() }
    }

    /**
     * Přepínač „Překládat v telefonu" v sheetu: zapamatuje se pro další překlady a otevřená
     * zpráva se přeloží znovu tou cestou, kterou si uživatel právě vybral.
     */
    fun setTranslateOnDevice(onDevice: Boolean) {
        settings.setTranslateOnDevice(onDevice)
        val open = state.value.sheet<TranslationSheet>() ?: return
        if (onDevice) {
            state.update { it.updatingSheet<TranslationSheet> { sheet -> sheet.copy(body = null, error = null, onDevice = true, needsDownload = false) } }
            translateOnDevice(open.message, open.languageCode, download = false)
        } else {
            translate(open.message, open.languageCode, open.summary, open.canCopy, forceServer = true)
        }
    }

    /** Uživatel potvrdil stažení jazykových balíčků. */
    fun downloadTranslationModels() {
        val open = state.value.sheet<TranslationSheet>()?.takeIf { it.needsDownload } ?: return
        translateOnDevice(open.message, open.languageCode, download = true)
    }

    /** Překlad v telefonu nejde (neznámý jazyk, nepodporovaný pár) — jednou přes server. */
    fun translateOnServer() {
        val open = state.value.sheet<TranslationSheet>() ?: return
        translate(open.message, open.languageCode, open.summary, open.canCopy, forceServer = true)
    }

    /**
     * Překlad v telefonu. Jazyk zprávy rozpozná zařízení ([languageDetector]) — nic se kvůli
     * tomu neposílá. Chybějící jazykové balíčky se nestahují samy: sheet se zeptá
     * ([TranslationSheet.needsDownload]) a teprve [download] je stáhne.
     */
    private fun translateOnDevice(message: ChatMessage, language: String, download: Boolean) {
        val translator = onDeviceTranslator ?: return
        fun update(change: (TranslationSheet) -> TranslationSheet) = state.update { current ->
            val open = current.sheet<TranslationSheet>()
                ?.takeIf { it.message.id == message.id && it.languageCode == language && it.onDevice == true }
                ?: return@update current
            current.copy(sheet = change(open))
        }
        update { it.copy(body = null, error = null, needsDownload = false, downloading = download) }

        scope.launch {
            val text = message.text
            val source = languageDetector.detect(text)?.let(::deviceTranslationCode)
            val target = deviceTranslationCode(language)
            val problem = when {
                source == null -> Res.string.translation_device_unknown
                target == null || !translator.supports(source) || !translator.supports(target) ->
                    Res.string.translation_device_unsupported
                source == target -> Res.string.translation_device_same
                else -> null
            }
            if (problem != null || source == null || target == null) {
                update { it.copy(error = UiText(problem ?: Res.string.translation_failed), downloading = false) }
                return@launch
            }
            if (!translator.hasModels(source, target)) {
                if (!download) {
                    update { it.copy(needsDownload = true) }
                    return@launch
                }
                if (translator.download(source, target).isFailure) {
                    update { it.copy(error = UiText(Res.string.translation_device_download_failed), downloading = false) }
                    return@launch
                }
            }
            val result = translator.translate(text, source, target)
            update { open ->
                result.fold(
                    onSuccess = { open.copy(body = MessageBody(it), downloading = false) },
                    onFailure = { open.copy(error = UiText(Res.string.translation_failed), downloading = false) },
                )
            }
        }
    }

    /**
     * Zkopíruje překlad a sheet zavře. Potvrzení jde snackbarem až po zavření — pod modálním
     * sheetem by vidět nebylo (stejně jako u odkazu v „Informace").
     */
    fun copyTranslation() {
        val open = state.value.sheet<TranslationSheet>()?.takeIf { it.canCopy } ?: return
        val text = open.body?.text ?: return
        scope.launch { copyToClipboard(text) }
        val notice = if (open.summary) Res.string.server_summary_copied else Res.string.translation_copied
        state.update { it.closingSheet<TranslationSheet>().copy(notice = if (clipboardConfirmsCopy) it.notice else UiText(notice)) }
    }
}
