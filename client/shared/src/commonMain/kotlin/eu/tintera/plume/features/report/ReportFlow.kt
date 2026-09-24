package eu.tintera.plume.features.report

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ReportOption
import eu.tintera.plume.core.telegram.ReportRequest
import eu.tintera.plume.core.telegram.ReportStep
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.report_done
import eu.tintera.plume.resources.report_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Otevřený průvodce nahlášením: [step] je otázka, na kterou server čeká. Nikdy [ReportStep.Done]
 * — po něm se průvodce zavře.
 */
data class ReportWizard(
    val chatId: Long,
    val step: ReportStep,
    /** Krok je odeslaný a čeká se na server; tlačítka jsou neaktivní, aby nešla ťuknout dvakrát. */
    val busy: Boolean = false,
)

/**
 * Průvodce nahlášením chatu nebo zpráv (`reportChat`), společný pro seznam chatů, profil
 * i konverzaci.
 *
 * Nahlášení NENÍ jedno volání: úvodní prázdný požadavek server buď rovnou vezme (chat se
 * spamovým action barem), nebo se zeptá na důvod, pak případně na text nebo zprávy. Tahle třída
 * drží, kde v tom uživatel je, a co se má stát po posledním kroku ([start] `then`) — mazání
 * a blokování musí počkat, až je nahlášeno: nahlásit jde jen chat, který ještě existuje.
 *
 * Je to obyčejná třída nad [CoroutineScope], ne ViewModel: ViewModel ji má každá obrazovka svůj
 * a průvodce je jen jeho část. Volání serveru dostává jako funkci, aby šel pořadí kroků otestovat
 * bez TDLib.
 */
class ReportFlow(
    private val scope: CoroutineScope,
    private val report: suspend (chatId: Long, request: ReportRequest) -> Result<ReportStep>,
) {
    private val logger = Logger.withTag("Report")

    private val _wizard = MutableStateFlow<ReportWizard?>(null)
    val wizard: StateFlow<ReportWizard?> = _wizard.asStateFlow()

    /**
     * Jednorázová hláška pro snackbar — nahlášeno, nebo proč ne. Nuluje ji obrazovka
     * ([consumeOutcome]) hned při zobrazení, jinak by se po otočení ukázala znovu.
     */
    private val _outcome = MutableStateFlow<UiText?>(null)
    val outcome: StateFlow<UiText?> = _outcome.asStateFlow()

    /** Odeslaný stav průvodce: každý další krok nese všechno, co už uživatel vybral. */
    private var request = ReportRequest()
    private var then: (suspend () -> Unit)? = null

    /**
     * Pořadí průvodce. Zavřený průvodce nesmí ožít odpovědí na krok, který byl na cestě v okamžiku
     * zavření — sheet by se otevřel znovu, jako by si ho uživatel nezavřel.
     */
    private var generation = 0

    /**
     * Začne nahlášení chatu [chatId], případně konkrétních zpráv [messageIds]. [then] se spustí
     * až po úspěšném nahlášení — po zrušení průvodce nebo chybě NE.
     */
    fun start(chatId: Long, messageIds: List<Long> = emptyList(), then: (suspend () -> Unit)? = null) {
        generation++
        request = ReportRequest(messageIds = messageIds)
        this.then = then
        send(chatId)
    }

    /**
     * Spustí [then] — s [report] až po úspěšném nahlášení chatu [chatId], jinak hned. Takhle
     * vypadá mazání s volbou „nahlásit": co má přijít po nahlášení, na průvodce počká.
     */
    fun thenAfterReport(report: Boolean, chatId: Long, then: suspend () -> Unit) {
        if (report) start(chatId, then = then) else scope.launch { then() }
    }

    /** Vybraný důvod. Text z předchozího kroku k novému důvodu nepatří, proto se nuluje. */
    fun choose(option: ReportOption) {
        val chatId = _wizard.value?.chatId ?: return
        request = request.copy(optionId = option.id, text = "")
        send(chatId)
    }

    /** Podrobnosti k důvodu; důvod bere ze serveru, ne z předchozího kroku — ten ho zopakoval. */
    fun submitText(text: String) {
        val wizard = _wizard.value ?: return
        val step = wizard.step as? ReportStep.EnterText ?: return
        request = request.copy(optionId = step.optionId, text = text.trim())
        send(wizard.chatId)
    }

    /** Zavřený průvodce nic nenahlásil, takže ani to, co mělo přijít po něm, neproběhne. */
    fun cancel() {
        generation++
        _wizard.value = null
        then = null
    }

    fun consumeOutcome() {
        _outcome.value = null
    }

    private fun send(chatId: Long) {
        val sent = request
        val sentIn = generation
        _wizard.update { it?.copy(busy = true) }
        scope.launch {
            val result = report(chatId, sent)
            if (sentIn != generation) return@launch
            result
                .onSuccess { step ->
                    if (step is ReportStep.Done) {
                        _wizard.value = null
                        _outcome.value = UiText(Res.string.report_done)
                        val next = then
                        then = null
                        next?.invoke()
                    } else {
                        _wizard.value = ReportWizard(chatId = chatId, step = step)
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Nahlášení chatu $chatId selhalo." }
                    _wizard.value = null
                    then = null
                    _outcome.value = failure.userMessage(Res.string.report_failed)
                }
        }
    }
}
