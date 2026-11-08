package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatSummary

/**
 * Rozdělení seznamu chatů na skupiny se společným zaoblením.
 *
 * Připnuté mají vlastní blok vždycky — jsou nahoře, protože si je tam uživatel dal. Nad nimi je
 * jen sponzorovaný chat ([ChatSummary.sponsor]), v bloku sám. Nepřečtené vlastní blok nemají
 * (jako v oficiálních klientech): chat by po přečtení „utekl" dolů z místa, na které se ťuklo.
 *
 * Pořadí uvnitř skupiny zůstává takové, jaké dává TDLib — je to pořadí, na které je uživatel
 * zvyklý z ostatních klientů, a přeskládávat ho by znamenalo rozhodovat za něj.
 *
 * Prázdné skupiny vypadnou, aby po nich nezbyla mezera. Bez připnutých je z celého seznamu
 * zase jeden souvislý blok.
 *
 * **Uložené zprávy ([savedMessagesChatId]) v seznamu nejsou, pokud nejsou připnuté.** Otevírají
 * se z menu v horní liště, jako v oficiálních klientech; připnutí je ale výslovné přání a schovat
 * připnutý chat by působilo jako chyba.
 *
 * **Vynořené odložené chaty ([due]) jsou nad připnutými, ve vlastním bloku.** Uživatel si je
 * nechal připomenout a připomínka, která by zůstala tam, kde chat stál, by se ztratila. Blok
 * zmizí, jakmile je otevře (viz `ChatSnoozes.opened`).
 *
 * Je to čistá funkce mimo composable, aby šla otestovat — uvnitř `@Composable` by se neověřila
 * ani jedna z hraničních kombinací (samé připnuté, samé odložené, prázdný seznam).
 */
fun chatGroups(
    chats: List<ChatSummary>,
    savedMessagesChatId: Long? = null,
    due: Set<Long> = emptySet(),
): List<List<ChatSummary>> {
    val visible = chats.filter { it.id != savedMessagesChatId || it.isPinned }
    // Sponzorovaný chat (proxy, oznámení) stojí nahoře zvlášť, nad připnutými, jako u oficiálních
    // klientů. Mezi vlastními chaty by se pletl s tím, co si uživatel do seznamu dal sám.
    val sponsored = visible.filter { it.sponsor != null }
    val reminded = visible.filter { it.sponsor == null && it.id in due }
    val own = visible.filter { it.sponsor == null && it.id !in due }
    val pinned = own.filter { it.isPinned }
    val rest = own.filter { !it.isPinned }

    return listOf(sponsored, reminded, pinned, rest).filter { it.isNotEmpty() }
}
