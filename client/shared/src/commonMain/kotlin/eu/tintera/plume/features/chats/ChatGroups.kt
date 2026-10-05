package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary

/**
 * Rozdělení seznamu chatů na skupiny se společným zaoblením.
 *
 * Připnuté mají vlastní blok vždycky — jsou nahoře, protože si je tam uživatel dal. Nad nimi je
 * jen sponzorovaný chat ([ChatSummary.sponsor]), v bloku sám.
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
    // Sponzorovaný chat (proxy, oznámení) stojí nahoře zvlášť, nad připnutými, jako u oficiálních
    // klientů. Mezi vlastními chaty by se pletl s tím, co si uživatel do seznamu dal sám.
    val sponsored = visible.filter { it.sponsor != null }
    val own = visible.filter { it.sponsor == null }
    val pinned = own.filter { it.isPinned }
    val rest = own.filter { !it.isPinned }

    val groups = if (separateUnread) {
        listOf(sponsored, pinned, rest.filter { it.unreadCount > 0 }, rest.filter { it.unreadCount == 0 })
    } else {
        listOf(sponsored, pinned, rest)
    }

    return groups.filter { it.isNotEmpty() }
}

/**
 * Filtr seznamu chatů z plovoucího toolbaru dole. Je jen lokální, nad načteným seznamem — vlastní
 * složky Telegramu (`ChatListFolder`) zatím nejsou a tohle je jejich nejčastější náhrada.
 */
enum class ChatFilter { All, Unread, Private, Groups, Channels }

/**
 * Chaty, které filtr pustí dál. Pořadí zůstává, skupiny ([chatGroups]) se dělají až nad výsledkem.
 *
 * Nepřečtené počítají i ručně označené ([hasUnread]) — přesně ty, které ukazuje odznak.
 */
fun List<ChatSummary>.filteredBy(filter: ChatFilter): List<ChatSummary> = when (filter) {
    ChatFilter.All -> this
    ChatFilter.Unread -> filter { it.hasUnread }
    ChatFilter.Private -> filter { it.kind == ChatKind.Private }
    ChatFilter.Groups -> filter { it.kind == ChatKind.Group }
    ChatFilter.Channels -> filter { it.kind == ChatKind.Channel }
}

/**
 * Filtry, které má smysl nabízet v toolbaru.
 *
 * - **Vše a Nepřečtené jsou vždycky.** Nepřečtené jsou STAV, který se mění každou chvíli; ikona,
 *   která by se objevovala a mizela s každou zprávou, by toolbar pod palcem přeskládávala.
 *   Prázdný výsledek („všechno přečteno") je u nich odpověď, ne slepá ulička.
 * - **Filtr podle druhu jen tehdy, když něco odfiltruje a něco nechá.** Bez kanálů by Kanály
 *   ukázaly prázdno, a když jsou všechny chaty lidé, jsou Lidé totéž co Vše. Druh chatu se mění
 *   zřídka, takže toolbar zůstává klidný.
 * - **Vybraný filtr nemizí nikdy** ([selected]). Když uživatel opustí poslední kanál, zůstane
 *   na Kanálech s prázdným stavem; ikona by mu jinak zmizela pod prstem i s výběrem.
 *
 * Počítá se nad týmiž chaty, které seznam ukazuje — bez nepřipnutých Uložených zpráv, jinak by
 * jimi Lidé „existovali" a ukázali prázdno. [chats] je `null`, dokud se seznam načítá: pak se
 * nabízí jen to, co je vždycky, a filtry podle druhu naběhnou, až je jasné, že mají co ukázat.
 */
fun availableFilters(
    chats: List<ChatSummary>?,
    selected: ChatFilter,
    savedMessagesChatId: Long? = null,
): List<ChatFilter> {
    val kinds = chats.orEmpty()
        .filter { it.id != savedMessagesChatId || it.isPinned }
        .mapTo(mutableSetOf()) { it.kind }
    return ChatFilter.entries.filter { filter ->
        val kind = when (filter) {
            ChatFilter.All, ChatFilter.Unread -> return@filter true
            ChatFilter.Private -> ChatKind.Private
            ChatFilter.Groups -> ChatKind.Group
            ChatFilter.Channels -> ChatKind.Channel
        }
        filter == selected || (kind in kinds && kinds.size > 1)
    }
}
