package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_selection_limit
import eu.tintera.plume.resources.summary_me
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Výběr víc zpráv: začátek z nabídky, přidávání a odebírání (album celé), práva k výběru jako
 * celku a hromadné akce — kopírování (i se jmény a jako Markdown), přeposlání, nahlášení, smazání.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationSelection(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    /** Zápis do schránky (`TextClipboard.copy`); funkce, aby šla v testu nahradit. */
    private val copyToClipboard: suspend (text: String) -> Unit,
    private val reportFlow: ReportFlow,
    private val logger: Logger,
) {
    /**
     * Vstup do režimu výběru z nabídky nad zprávou — s tou zprávou rovnou vybranou.
     *
     * Nabídka se tím zavře: výběr je jiný režim celé konverzace, ne další akce nad jednou zprávou.
     */
    fun startSelection(messageId: Long) {
        state.update { it.copy(messageMenu = null, selection = MessageSelection()) }
        toggleSelection(messageId)
    }

    /**
     * Přidá blok do výběru, nebo ho z něj vyndá. Poslední odebraný výběr ukončí — zůstat
     * v režimu s nulou vybraných by byl stav, ve kterém nejde nic dělat a není jasné proč.
     */
    fun toggleSelection(messageId: Long) {
        val snapshot = state.value
        val selection = snapshot.selection ?: return
        val key = snapshot.blockKeyOf(messageId) ?: return

        if (key in selection.keys) {
            val keys = selection.keys - key
            state.update {
                it.copy(
                    selection = if (keys.isEmpty()) {
                        null
                    } else {
                        selection.copy(keys = keys, rights = selection.rights - key, withText = selection.withText - key)
                    },
                )
            }
            return
        }

        if (selection.count >= MAX_SELECTED_MESSAGES) {
            state.update { it.copy(notice = UiText(Res.string.conversation_selection_limit, listOf(MAX_SELECTED_MESSAGES))) }
            return
        }

        // Popisek alba nemusí být na ťuknuté fotce, takže se text hledá v celém bloku.
        val hasText = snapshot.albumMemberIds(messageId)
            .mapNotNull { id -> snapshot.messages.firstOrNull { it.id == id } }
            .any { it.copyableText().isNotBlank() }
        state.update {
            it.copy(
                selection = selection.copy(
                    keys = selection.keys + key,
                    withText = if (hasText) selection.withText + key else selection.withText,
                ),
            )
        }

        // Práva se ptáme na každý vybraný blok zvlášť — dopředu pro celou historii by to byl
        // dotaz na každou zprávu, a výběr má strop, takže jich nikdy nebude moc.
        scope.launch {
            val actions = repository.messageActions(chatId, messageId).getOrElse { MessageActions.Fallback }
            state.update { latest ->
                val current = latest.selection ?: return@update latest
                // Než odpověď dorazila, mohl uživatel blok z výběru vyndat nebo výběr zavřít.
                if (key !in current.keys) return@update latest
                latest.copy(selection = current.copy(rights = current.rights + (key to actions)))
            }
        }
    }

    fun closeSelection() {
        state.update { it.copy(selection = null) }
    }

    /** Texty vybraných zpráv pod sebe, od nejstarší. Zprávy bez textu se vynechají. */
    fun copySelection() {
        val snapshot = state.value
        val text = snapshot.selectedMessageIds()
            .mapNotNull { id -> snapshot.messages.firstOrNull { it.id == id } }
            .mapNotNull { it.copyableText().takeIf(String::isNotBlank) }
            .joinToString(SELECTION_COPY_SEPARATOR)
        if (text.isBlank()) return

        closeSelection()
        scope.launch { copyToClipboard(text) }
    }

    /**
     * Vybrané zprávy jako přepis s časem a jménem, od nejstarší — pro poznámky nebo jazykový
     * model (viz [selectionTranscript]). Překládá se teď (`resolveNow`), schránka jazyk neudrží.
     */
    fun copySelectionWithNames() = copySelectionAs { messages, me, title -> selectionTranscript(messages, me, title) }

    /** Vybrané zprávy jako Markdown pro poznámky, i s formátováním (viz [selectionMarkdown]). */
    fun copySelectionAsMarkdown() = copySelectionAs { messages, me, title -> selectionMarkdown(messages, me, title) }

    private fun copySelectionAs(format: suspend (List<ChatMessage>, me: String, chatTitle: String) -> String) {
        val snapshot = state.value
        val messages = snapshot.selectedMessageIds().mapNotNull { id -> snapshot.messages.firstOrNull { it.id == id } }
        if (messages.isEmpty()) return
        val chatTitle = snapshot.info?.title.orEmpty()

        closeSelection()
        scope.launch {
            val text = format(messages, UiText(Res.string.summary_me).resolveNow(), chatTitle)
            if (text.isNotBlank()) copyToClipboard(text)
        }
    }

    /** Výběr do přeposlání; cíl si vyžádá obrazovka, stejně jako u jedné zprávy. */
    fun forwardSelection() {
        val ids = state.value.selectedMessageIds()
        if (ids.isEmpty()) return
        state.update { it.copy(selection = null, forwardRequest = ids) }
    }

    /**
     * Nahlásit celý výběr moderátorům: tentýž průvodce jako u jedné zprávy, jen s víc zprávami.
     * Server se zeptá na důvod; zavření průvodce nic neodešle.
     */
    fun reportSelection() {
        val ids = state.value.selectedMessageIds()
        if (ids.isEmpty() || state.value.selection?.canReport != true) return
        state.update { it.copy(selection = null) }
        reportFlow.start(chatId, messageIds = ids)
    }

    /** Potvrzení smazání celého výběru. Práva jsou už načtená, takže se dialog zeptá rovnou. */
    fun deleteSelection() {
        val snapshot = state.value
        val selection = snapshot.selection ?: return
        val ids = snapshot.selectedMessageIds()
        if (ids.isEmpty()) return

        state.update {
            it.copy(
                selection = null,
                pendingDelete = PendingDelete(
                    messageId = ids.first(),
                    messageIds = ids,
                    canDeleteForSelf = selection.canDeleteForSelf,
                    canDeleteForAll = selection.canDeleteForAll,
                    count = selection.count,
                ),
            )
        }
    }
}

/** Mezi zprávami ve schránce zůstane prázdný řádek — jinak by dlouhé zprávy splynuly v jednu. */
private const val SELECTION_COPY_SEPARATOR = "\n\n"
