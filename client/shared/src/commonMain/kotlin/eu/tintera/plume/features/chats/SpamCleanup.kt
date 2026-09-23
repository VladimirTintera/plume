package eu.tintera.plume.features.chats

/**
 * Co se má s chatem stát při potvrzení smazání — nad rámec samotného smazání.
 *
 * Volby jsou v potvrzení proto, že spam se řeší obojím naráz: bot, který se jen smaže, napíše
 * zítra znovu, a bot, který se jen zablokuje, zůstane v seznamu. Obě jsou vypnuté, dokud si je
 * uživatel nezapne — zablokovat někoho omylem při mazání chatu by bylo horší než ťuknutí navíc.
 */
data class SpamCleanup(
    val report: Boolean = false,
    val block: Boolean = false,
)

/**
 * Vyřídí spam v pořadí **nahlásit → zablokovat → smazat** a na první chybě se ZASTAVÍ.
 *
 * Pořadí není libovolné: nahlásit jde jen chat, který ještě existuje (a spamový action bar
 * s ním zmizí), takže mazání musí být poslední.
 *
 * Zastavení na chybě je to hlavní, co se na tomhle dá rozbít. Kdyby se chat smazal a blokování
 * selhalo, bot by dál psal, uživatel by si myslel, že je vyřízený — a chat, na kterém by šlo akci
 * zkusit znovu, by byl pryč. Takhle zbyde chat i s chybou, kterou je vidět.
 *
 * Je to funkce mimo ViewModel, ne jeho privátní metoda: pořadí kroků a to, co se stane po chybě,
 * se uvnitř ViewModelu s živým TDLib neotestuje.
 */
internal suspend fun cleanUpSpam(
    cleanup: SpamCleanup,
    report: suspend () -> Result<Unit>,
    block: suspend () -> Result<Unit>,
    delete: suspend () -> Result<Unit>,
): Result<Unit> {
    if (cleanup.report) {
        val reported = report()
        if (reported.isFailure) return reported
    }
    if (cleanup.block) {
        val blocked = block()
        if (blocked.isFailure) return blocked
    }
    return delete()
}
