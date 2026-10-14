package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.location.GeoPoint
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.InlineBotInfo
import eu.tintera.plume.core.telegram.InlineQueryDraft
import eu.tintera.plume.core.telegram.InlineQueryState
import eu.tintera.plume.core.telegram.InlineResult
import eu.tintera.plume.core.telegram.InlineResultsButton
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.MentionCandidate
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.inlineQueryOf
import eu.tintera.plume.core.telegram.inlineQueryText
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_send_failed
import eu.tintera.plume.resources.conversation_via_bot_draft_busy
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Napovídání nad vstupním polem: zmínky členů (i jménem, bez uživatelského jména) a inline
 * boti (`@gif kočka`) s výsledky po stránkách, polohou pro boty, kteří o ni stojí, a tlačítkem
 * nad výsledky.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationSuggestions(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val media: MediaSender,
    /** Rozepsaný text pole — inline bot se do něj vkládá jen do prázdného. */
    private val composedDraft: () -> ComposedText,
    private val onDraftChanged: (ComposedText) -> Unit,
    private val openBotStart: (InternalLink.BotStart) -> Unit,
    private val requestWebApp: (WebAppRequest) -> Unit,
    private val logger: Logger,
) {
    /** Běžící inline dotaz; další úhoz ho zruší a pustí nový (s pauzou). */
    private var inlineJob: Job? = null

    /** Běžící hledání členů ke zmínce; další úhoz ho zruší. */
    private var mentionJob: Job? = null

    /**
     * Zmínky jménem vložené z napovídání a čekající na odeslání (viz [NameMention]). Pole je holý
     * text, takže entitu s id uživatele nese tohle; s vyprázdněným polem nebo odesláním mizí.
     */
    val nameMentions = mutableListOf<NameMention>()

    /** Poslední dotaz z pole — kvůli zopakování, až dorazí poloha. */
    private var inlineDraft: InlineQueryDraft? = null

    /** Poloha pro boty, kteří o ni stojí ([InlineBotInfo.needLocation]); zjišťuje ji obrazovka. */
    private var inlineLocation: GeoPoint? = null

    /**
     * Na polohu se u bota, který o ni stojí, už jednou počkalo (zjistila se, nebo ji uživatel
     * nedovolil či nepřišla do limitu). Do té doby se dotaz NEPOSÍLÁ: bez polohy by bot odpověděl
     * něčím jiným a výsledky by se po jejím příchodu vyměnily pod prstem.
     */
    private var inlineLocationResolved = false

    /**
     * Boti, kteří na inline dotaz odpověděli „neumím" — dál se jich s každým písmenem neptáme.
     * Jen po dobu konverzace: bot si inline režim může zapnout.
     */
    private val notInlineBots = mutableSetOf<Long>()

    /**
     * Rozepsaná zmínka u kurzoru ([mentionQueryAt]); `null` = u kurzoru se zmínka nepíše.
     *
     * Jen ve skupině (v soukromém chatu je koho zmínit jen jeden, v kanálu nikdo nepíše) a mimo
     * úpravu zprávy — úprava posílá holý text a zmínka jménem by z ní zmizela. Hledá se po krátké
     * pauze a dalším písmenem se zahodí; dosavadní návrhy mezitím zůstávají, panel neproblikne.
     */
    fun mentionQuery(query: String?) {
        mentionJob?.cancel()
        val info = state.value.info
        val allowed = info != null && info.isGroup && !info.isChannel && state.value.canWrite && state.value.editing == null
        if (query == null || !allowed) {
            if (state.value.mentionSuggestions != null) state.update { it.copy(mentionSuggestions = null) }
            return
        }
        mentionJob = scope.launch {
            if (query.isNotEmpty()) delay(MENTION_QUERY_DELAY)
            repository.mentionCandidates(chatId, query)
                .onSuccess { found -> state.update { it.copy(mentionSuggestions = MentionSuggestions(query, found)) } }
                .onFailure { failure -> logger.i { "Členy ke zmínce se nepodařilo najít: ${failure.message}" } }
        }
    }

    /**
     * Vybraný člen z napovídání. Pole přepisuje obrazovka ([mentionInsertion]); tady se jen
     * zapamatuje zmínka jménem — s uživatelským jménem odkaz vyrobí server sám.
     */
    fun mentionPicked(candidate: MentionCandidate) {
        mentionJob?.cancel()
        state.update { it.copy(mentionSuggestions = null) }
        if (candidate.username == null) nameMentions += NameMention(candidate.firstName, candidate.userId)
    }

    /** Zmínky jménem pro odesílanou zprávu; s odesláním se zapomenou. */
    fun takeNameMentions(): List<NameMention> = nameMentions.toList().also { nameMentions.clear() }

    /**
     * Rozepsaný text z pole jako dotaz na inline bota ([inlineQueryOf]); `null` = text dotaz není.
     *
     * Volá ho obrazovka při každé změně pole — i té, která jde mimo [onDraftChanged] (vrácený
     * koncept, „přes @bot" ťuknuté u zprávy). Dotaz jde po pauze v psaní ([INLINE_QUERY_DELAY]):
     * bot odpovídá ze serveru a dotaz na každé písmeno by ho zahltil. Předchozí výsledky mezitím
     * zůstávají vidět ([InlineQueryState.withQuery]).
     *
     * Kde se psát nesmí nebo se upravuje zpráva, se nic neptá — výsledek by nebylo kam poslat.
     */
    fun inlineQuery(draft: InlineQueryDraft?) {
        if (draft == inlineDraft) return
        inlineDraft = draft
        inlineJob?.cancel()
        if (draft == null || !state.value.canWrite || state.value.editing != null) {
            if (state.value.inline != null) state.update { it.copy(inline = null) }
            return
        }
        inlineJob = scope.launch {
            delay(INLINE_QUERY_DELAY)
            val bot = repository.inlineBot(draft.username).getOrNull()?.takeIf { it.userId !in notInlineBots }
            if (bot == null) {
                state.update { it.copy(inline = null) }
                return@launch
            }
            state.update { latest ->
                val current = latest.inline?.takeIf { it.bot.userId == bot.userId }
                latest.copy(inline = current?.withQuery(draft.query) ?: InlineQueryState(bot, draft.query))
            }
            // Polohu zjišťuje obrazovka (podle `inline.bot.needLocation`) a dotaz pošle
            // [setInlineLocation], až ji má — nebo ví, že nebude.
            if (bot.needLocation && !inlineLocationResolved) return@launch
            loadInlinePage(bot, draft.query, offset = "")
        }
    }

    /**
     * „přes @bot" ťuknuté u zprávy: do pole se dá `@bot `, a výsledky se tím rovnou načtou.
     *
     * Rozepsanou zprávu to NEPŘEPÍŠE — ťuknutí na značku není důvod přijít o text. Přepíše se jen
     * prázdné pole nebo jiný rozepsaný dotaz na bota; jinak hláška, proč se nic nestalo.
     */
    fun useInlineBot(username: String, query: String = "") {
        if (!state.value.canWrite || state.value.editing != null) return
        if (composedDraft().text.isNotBlank() && inlineQueryOf(composedDraft().text) == null) {
            state.update { it.copy(notice = UiText(Res.string.conversation_via_bot_draft_busy)) }
            return
        }
        // Dotaz, ne text pro čtenáře: `@bot ` a mezera, za kterou se píše; u tlačítka bota
        // rovnou i jeho dotaz.
        val text = inlineQueryText(username, query)
        state.update { it.copy(inputFill = InputFill(text, onlyIfEmpty = false)) }
        onDraftChanged(ComposedText(text))
    }

    /** Další stránka výsledků — dotáhne ji panel, když dojede na konec. */
    fun loadMoreInline() {
        val inline = state.value.inline?.takeIf { it.canLoadMore } ?: return
        state.update { it.copy(inline = inline.copy(loading = true)) }
        inlineJob = scope.launch { loadInlinePage(inline.bot, inline.query, inline.nextOffset) }
    }

    private suspend fun loadInlinePage(bot: InlineBotInfo, query: String, offset: String) {
        val location = inlineLocation.takeIf { bot.needLocation }
        repository.inlineResults(bot.userId, chatId, query, offset, location)
            .onSuccess { page ->
                state.update { current ->
                    current.copy(inline = current.inline?.withPage(query, page, append = offset.isNotEmpty()))
                }
            }
            .onFailure { failure ->
                // Bot, který inline dotazy neumí, odpoví chybou — to není selhání, jen „tohle
                // není inline bot". Panel se schová, jako by `@jméno` byla obyčejná zmínka.
                logger.i { "Inline dotaz na @${bot.username} selhal: ${failure.message}" }
                // Jediné znění, podle kterého jde „neumím" poznat (ověřeno `strings libtdjsonjava.so`).
                if (failure.message?.contains("doesn't support inline queries") == true) notInlineBots += bot.userId
                state.update { current ->
                    val inline = current.inline ?: return@update current
                    if (inline.resultsQuery == null) current.copy(inline = null) else current.copy(inline = inline.withFailure(query))
                }
            }
    }

    /**
     * Poloha pro bota, který o ni stojí; `null` = nedovolená nebo nezjištěná. Čekající dotaz
     * odejde (s polohou, nebo bez ní), a se změněnou polohou se zopakuje.
     */
    fun setInlineLocation(location: GeoPoint?) {
        val waiting = !inlineLocationResolved
        inlineLocationResolved = true
        val changed = location != null && location != inlineLocation
        if (location != null) inlineLocation = location
        if (!waiting && !changed) return
        val draft = inlineDraft ?: return
        inlineDraft = null
        inlineQuery(draft)
    }

    /**
     * Odešle výsledek inline dotazu. Rozepsaný dotaz v poli tím byl spotřebovaný — pole vyprázdní
     * obrazovka (přes [onDraftChanged], takže se smaže i koncept), citace se spotřebuje jako
     * u samolepky. Odchází hned, bez letu z pole.
     */
    fun sendInlineResult(result: InlineResult) {
        val inline = state.value.inline ?: return
        if (!state.value.canWrite || state.value.editing != null) return
        inlineJob?.cancel()
        inlineDraft = null
        state.update { it.copy(inline = null) }
        media.sendMedia(Res.string.conversation_send_failed) { replyTo, quote ->
            repository.sendInlineResult(chatId, inline.queryId, result.id, replyTo, quote)
        }
    }

    /**
     * Tlačítko nad výsledky. „Nastavit u bota" otevře chat s botem a rovnou ho spustí s parametrem
     * od bota — tak to dělají oficiální klienti (Bot API `switch_pm`); mini aplikaci Plume neumí.
     */
    fun pressInlineButton() {
        val inline = state.value.inline ?: return
        when (val button = inline.button) {
            is InlineResultsButton.StartBot ->
                openBotStart(InternalLink.BotStart(inline.bot.userId, button.parameter, autostart = true))
            is InlineResultsButton.WebApp ->
                requestWebApp(WebAppRequest(inline.bot.userId, button.url, launch = false))
            null -> Unit
        }
    }
}

/**
 * Pauza v psaní, po které jde inline dotaz na bota. Kratší než u náhledu odkazu: výsledky se
 * ukazují průběžně a čekání je znát, delší by dotaz na každé písmeno jen oddálilo.
 */
private val INLINE_QUERY_DELAY = 400.milliseconds

/** Pauza v psaní, po které se hledají členové ke zmínce. Hledání je lokální, stačí krátká. */
private val MENTION_QUERY_DELAY = 150.milliseconds
