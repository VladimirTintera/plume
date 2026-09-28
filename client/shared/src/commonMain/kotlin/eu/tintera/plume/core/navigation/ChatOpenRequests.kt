package eu.tintera.plume.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Požadavek na otevření konkrétního chatu zvenčí — dnes ťuknutí na notifikaci.
 *
 * Proč stav a ne událost: v okamžiku ťuknutí nemusí být komu doručit. Proces mohl být mrtvý a
 * skládá se teprve UI, nebo aplikace visí na přihlášení a [eu.tintera.plume.app.shell.AppShell]
 * vůbec neexistuje. Událost by se v obou případech zahodila a notifikace by jen otevřela seznam.
 * Takhle si požadavek počká, než bude mít kdo ho vyřídit.
 */
class ChatOpenRequests {

    private val pending = MutableStateFlow<ChatOpenRequest?>(null)

    val request: StateFlow<ChatOpenRequest?> = pending.asStateFlow()

    /** [topicId]: téma skupiny s tématy, které se má otevřít místo celé skupiny. */
    fun request(chatId: Long, topicId: Int? = null) {
        pending.value = ChatOpenRequest(chatId, topicId)
    }

    /**
     * Vyřízeno. Porovnává se s hodnotou, kterou volající opravdu otevřel — mezi vyzvednutím
     * a vyřízením mohl přijít požadavek na jiný chat a ten by se zahodil nepřečtený.
     */
    fun consume(request: ChatOpenRequest) {
        pending.compareAndSet(request, null)
    }
}

/** Co se má otevřít: chat, u skupiny s tématy případně rovnou jedno téma. */
data class ChatOpenRequest(val chatId: Long, val topicId: Int? = null)
