package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Okno historie, které má konverzace právě načtené: nejnovější a nejstarší zpráva v něm.
 * [savedTopicId] / [directTopicId] říkají, čí historie to je (téma Uložených zpráv, vlákno
 * přímých zpráv) — kalendář tématu nesmí počítat z okna celého chatu a naopak.
 */
data class LoadedWindow(
    val chatId: Long,
    val savedTopicId: Long?,
    val directTopicId: Long?,
    val newestMessageId: Long,
    val oldestMessageId: Long,
)

/**
 * Konverzace hlásí, co má načtené, kalendář v panelu vedle to čte.
 *
 * **TDLib o uložení zpráv nedává vědět.** Co si konverzace stáhne (skok na staré datum, dotažení
 * stránky při rolování), uloží do databáze, ale žádný update o tom nepošle — updaty chodí jen na
 * změny v chatu (nová zpráva, smazání, úprava). Kalendář v panelu, který po ťuknutí na den
 * zůstává otevřený, by tak o nově načtených dnech nevěděl. Ví o nich konverzace: nese je v okně.
 *
 * Stav, ne událost: panel otevřený až po skoku si přečte okno, které už je.
 */
class LoadedHistory {
    private val current = MutableStateFlow<LoadedWindow?>(null)

    val window: StateFlow<LoadedWindow?> = current.asStateFlow()

    fun report(window: LoadedWindow) {
        current.value = window
    }
}
