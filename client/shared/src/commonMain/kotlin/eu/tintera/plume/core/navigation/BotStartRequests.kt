package eu.tintera.plume.core.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Parametr ze startovacího odkazu bota (`t.me/bot?start=…`), který čeká na tlačítko Start.
 *
 * Je to STAV, ne událost — jako [ChatOpenRequests]. Odkaz se ťukne v jedné konverzaci a Start
 * je v jiné, která ještě nemusí existovat; parametr si počká, až si ho chat bota vyzvedne,
 * a platí, dokud ho Start neodešle. Nový odkaz na tentýž bot ten starší přepíše.
 */
class BotStartRequests {

    private val pending = MutableStateFlow<Map<Long, String>>(emptyMap())

    fun request(chatId: Long, parameter: String) {
        pending.update { it + (chatId to parameter) }
    }

    fun parameter(chatId: Long): Flow<String?> = pending.map { it[chatId] }.distinctUntilChanged()

    fun consume(chatId: Long) {
        pending.update { it - chatId }
    }
}
