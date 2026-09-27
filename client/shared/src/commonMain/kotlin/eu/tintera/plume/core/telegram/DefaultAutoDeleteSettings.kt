package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.MessageAutoDeleteTime
import dev.g000sha256.tdl.toResult

/**
 * Výchozí doba automatického mazání (`getDefaultMessageAutoDeleteTime`): nastaví se v každém
 * chatu, který uživatel NOVĚ založí. Na chaty, které už existují, nesahá.
 *
 * **Je to nastavení ÚČTU**, jako citlivý obsah: zapisuje se na server a platí na všech
 * zařízeních, takže ho aplikace čte vždycky znovu a nedrží ho u sebe.
 */
interface DefaultAutoDeleteSettings {

    /** Doba v sekundách; `0` = vypnuto. */
    suspend fun defaultAutoDeleteTime(): Result<Int>

    suspend fun setDefaultAutoDeleteTime(seconds: Int): Result<Unit>
}

class TdlDefaultAutoDeleteSettings(private val holder: TelegramClientHolder) : DefaultAutoDeleteSettings {

    private val client: TdlClient get() = holder.client

    private val logger = Logger.withTag("DefaultAutoDelete")

    override suspend fun defaultAutoDeleteTime(): Result<Int> =
        client.getDefaultMessageAutoDeleteTime().toResult().map { it.time }
            .onFailure { logger.w(it) { "Výchozí dobu automatického mazání se nepodařilo zjistit" } }

    override suspend fun setDefaultAutoDeleteTime(seconds: Int): Result<Unit> =
        client.setDefaultMessageAutoDeleteTime(messageAutoDeleteTime = MessageAutoDeleteTime(time = seconds))
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Výchozí dobu automatického mazání se nepodařilo změnit" } }
}
