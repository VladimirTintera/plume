package eu.tintera.plume.core.calls

import eu.tintera.plume.core.accounts.AccountSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Požadavky na hovor zvenčí — z notifikace („Přijmout", ťuknutí, celoobrazovková výzva,
 * „Zavolat zpět").
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

    private val pendingCallBack = MutableStateFlow<CallBackRequest?>(null)

    /** Komu má shell zavolat zpátky (po dotazu na mikrofon, u videohovoru i na kameru). */
    val callBack: StateFlow<CallBackRequest?> = pendingCallBack.asStateFlow()

    private var callBackSerial = 0L

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

    /** „Zavolat zpět" z notifikace o zmeškaném hovoru účtu [slot]. */
    fun callBack(slot: AccountSlot, userId: Long, video: Boolean) {
        pendingCallBack.value = CallBackRequest(slot, userId, video, serial = ++callBackSerial)
    }

    fun consumeCallBack(request: CallBackRequest) {
        pendingCallBack.compareAndSet(request, null)
    }
}

/**
 * Zavolat zpátky [userId] z účtu [slot]. [serial] odliší dvě ťuknutí na tutéž notifikaci —
 * jinak by druhý požadavek byl roven prvnímu a shell by ho za nový nepovažoval.
 */
data class CallBackRequest(
    val slot: AccountSlot,
    val userId: Long,
    val video: Boolean,
    val serial: Long,
)
