package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatSummary

/**
 * Rozdělení seznamu chatů na skupiny se společným zaoblením.
 *
 * Připnuté mají vlastní blok vždycky — jsou nahoře, protože si je tam uživatel dal.
 * Nepřečtené jen tehdy, když si o to řekl v nastavení ([separateUnread]): vyčlenění pomáhá,
 * když je chatů hodně, jinak seznam rozbíjí a chat po přečtení „uteče" dolů z místa,
 * na které se ťuklo.
 *
 * Pořadí uvnitř skupiny zůstává takové, jaké dává TDLib — je to pořadí, na které je uživatel
 * zvyklý z ostatních klientů, a přeskládávat ho by znamenalo rozhodovat za něj.
 *
 * Prázdné skupiny vypadnou, aby po nich nezbyla mezera. Bez připnutých a bez vyčleněných
 * nepřečtených je z celého seznamu zase jeden souvislý blok.
 *
 * **Uložené zprávy ([savedMessagesChatId]) v seznamu nejsou, pokud nejsou připnuté.** Otevírají
 * se z menu v horní liště, jako v oficiálních klientech; připnutí je ale výslovné přání a schovat
 * připnutý chat by působilo jako chyba.
 *
 * Je to čistá funkce mimo composable, aby šla otestovat — uvnitř `@Composable` by se neověřila
 * ani jedna z hraničních kombinací (samé připnuté, samé nepřečtené, prázdný seznam).
 */
fun chatGroups(
    chats: List<ChatSummary>,
    separateUnread: Boolean,
    savedMessagesChatId: Long? = null,
): List<List<ChatSummary>> {
    val visible = chats.filter { it.id != savedMessagesChatId || it.isPinned }
    val pinned = visible.filter { it.isPinned }
    val rest = visible.filter { !it.isPinned }

    val groups = if (separateUnread) {
        listOf(pinned, rest.filter { it.unreadCount > 0 }, rest.filter { it.unreadCount == 0 })
    } else {
        listOf(pinned, rest)
    }

    return groups.filter { it.isNotEmpty() }
}
