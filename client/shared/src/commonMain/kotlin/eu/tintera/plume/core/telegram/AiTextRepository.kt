package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * AI úpravy textu od SERVERU Telegramu (`fixTextWithAi`, `composeTextWithAi`) — protějšek
 * přepisu modelem v zařízení (`core/ai`). Text odchází na server, proto se volá jen na
 * výslovné ťuknutí a nikdy v tajném chatu (dokumentace obou metod to zakazuje).
 *
 * Bez Premium má účet omezený počet dotazů; po jeho vyčerpání vrátí server
 * `AICOMPOSE_FLOOD_PREMIUM` a [userMessage] z toho udělá větu o Premium.
 */
interface AiTextRepository {
    /**
     * Styly psaní, které server nabízí (`updateTextCompositionStyles`), v jeho pořadí. Prázdné,
     * dokud update nedorazí — dotaz na ně TDLib nemá, chodí jen updatem.
     */
    val styles: StateFlow<List<TextStyleOption>>

    /** Opraví překlepy, gramatiku a interpunkci; výsledek i s vyznačenými změnami. */
    suspend fun fix(text: String): Result<FixedDraft>

    /** Přepíše text stylem [styleName] ([TextStyleOption.name]). */
    suspend fun compose(text: String, styleName: String): Result<String>
}

/** Styl psaní od serveru: [name] se posílá zpátky, [title] je v jazyce aplikace od serveru. */
data class TextStyleOption(val name: String, val title: String)

/** Opravený text ([text]) a tentýž text se změnami vyznačenými proti původnímu ([diff]). */
data class FixedDraft(val text: String, val diff: DraftDiff)

/**
 * Text, ve kterém jsou vidět změny: [text] obsahuje i SMAZANÉ kusy (označené [DraftChange.Kind.Delete]),
 * aby šlo ukázat, co zmizelo. Změny se nepřekrývají.
 */
data class DraftDiff(val text: String, val changes: List<DraftChange>)

/** Jedna změna v [DraftDiff.text]: rozsah v UTF-16 jako u entit zpráv. */
data class DraftChange(val offset: Int, val length: Int, val kind: Kind, val oldText: String? = null) {
    enum class Kind { Insert, Delete, Replace }
}
