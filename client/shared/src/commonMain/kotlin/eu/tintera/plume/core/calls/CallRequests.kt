package eu.tintera.plume.core.calls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Požadavky na hovor zvenčí — z notifikace („Přijmout", ťuknutí, celoobrazovková výzva).
 *
 * Stav, ne událost, ze stejného důvodu jako `ChatOpenRequests`: ve chvíli ťuknutí ještě nemusí
 * existovat shell, který by je vyřídil (proces probuzený pushem, aplikace za zámkem).
 */
class CallRequests {

    private val pendingAnswer = MutableStateFlow<Int?>(null)

    /** Hovor, který má shell přijmout (po dotazu na mikrofon). */
    val answer: StateFlow<Int?> = pendingAnswer.asStateFlow()

    private val showCount = MutableStateFlow(0)

    /** Každé zvýšení = ukázat obrazovku hovoru, i když ji uživatel zmenšil do pruhu. */
    val show: StateFlow<Int> = showCount.asStateFlow()

    fun answer(callId: Int) {
        pendingAnswer.value = callId
        show()
    }

    fun consumeAnswer(callId: Int) {
        pendingAnswer.compareAndSet(callId, null)
    }

    fun show() {
        showCount.update { it + 1 }
    }
}
