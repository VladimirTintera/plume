package eu.tintera.plume.features.chats

import eu.tintera.plume.features.report.ReportFlow

/**
 * Co se má s chatem stát při smazání — nad rámec samotného smazání.
 *
 * Volby jsou u mazání proto, že spam se řeší všemi třemi naráz: bot, který se jen smaže, napíše
 * zítra znovu, a bot, který se jen zablokuje, zůstane v seznamu. Obě jsou vypnuté, dokud si je
 * uživatel nezapne — zablokovat někoho omylem při mazání chatu by bylo horší než ťuknutí navíc.
 */
data class SpamCleanup(
    val report: Boolean = false,
    val block: Boolean = false,
)

/**
 * Pořadí je **nahlásit → zablokovat → smazat** a na první chybě se ZASTAVÍ.
 *
 * Nahlášení je průvodce ([ReportFlow]) a ten běží PŘED touhle funkcí: nahlásit jde jen chat,
 * který ještě existuje, a server se může ptát na důvod — blokování a mazání proto čekají, až
 * průvodce skončí úspěchem ([ReportFlow.start] `then`). Zrušený průvodce nesmaže nic.
 *
 * Tady zbývá zablokovat → smazat. Kdyby se chat smazal a blokování selhalo, bot by dál psal,
 * uživatel by si myslel, že je vyřízený — a chat, na kterém by šlo akci zkusit znovu, by byl
 * pryč. Takhle zbyde chat i s chybou, kterou je vidět.
 *
 * Je to funkce mimo ViewModel: pořadí kroků a to, co se stane po chybě, se uvnitř ViewModelu
 * s živým TDLib neotestuje.
 */
internal suspend fun cleanUpSpam(
    cleanup: SpamCleanup,
    block: suspend () -> Result<Unit>,
    delete: suspend () -> Result<Unit>,
): Result<Unit> {
    if (cleanup.block) {
        val blocked = block()
        if (blocked.isFailure) return blocked
    }
    return delete()
}
