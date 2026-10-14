package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.location.GeoPoint
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.LoginUrlPrompt
import eu.tintera.plume.core.telegram.Place
import eu.tintera.plume.core.telegram.PollDraftMedia
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import eu.tintera.plume.core.telegram.UnsupportedBotButton
import eu.tintera.plume.core.telegram.UnsupportedKeyboardButton
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_answer
import eu.tintera.plume.resources.bot_button_failed
import eu.tintera.plume.resources.bot_location_unavailable
import eu.tintera.plume.resources.bot_login_failed
import eu.tintera.plume.resources.bot_text_copied
import eu.tintera.plume.resources.bot_unsupported_buy
import eu.tintera.plume.resources.bot_unsupported_game
import eu.tintera.plume.resources.bot_unsupported_managed_bot
import eu.tintera.plume.resources.bot_unsupported_password
import eu.tintera.plume.resources.bot_unsupported_switch_inline
import eu.tintera.plume.resources.conversation_bot_start_failed
import eu.tintera.plume.resources.conversation_location_send_failed
import eu.tintera.plume.resources.conversation_send_failed
import eu.tintera.plume.resources.poll_add_option_failed
import eu.tintera.plume.resources.poll_retract_failed
import eu.tintera.plume.resources.poll_stop_failed
import eu.tintera.plume.resources.poll_vote_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource

/**
 * Boti a ankety: Start, tlačítka pod zprávou (odkaz, callback, přihlášení, kopírování,
 * přepnutí do inline), vlastní klávesnice (i jednorázová a vynucená odpověď), příkazy, menu
 * bota, sdílení čísla a polohy botovi, hlasování a ukončení ankety, hlasující, soutěž.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationBots(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settings: AppSettings,
    private val urlOpener: UrlOpener,
    private val clipboard: TextClipboard,
    private val botStartRequests: BotStartRequests,
    /** Odkaz z tlačítka, zmínka a profil — jdou touž cestou jako z textu zprávy. */
    private val links: ConversationLinks,
    /** Tlačítko „přepnout do inline" v tomhle chatu vloží dotaz do pole. */
    private val suggestions: ConversationSuggestions,
    /** Vynucená odpověď přepne pole na odpověď botovi. */
    private val replyTo: (messageId: Long) -> Unit,
    private val logger: Logger,
) {
    /** Vynucené odpovědi, na které se pole už přepnulo — jednou na zprávu. */
    private val forceReplied = mutableSetOf<Long>()

    /** Tlačítko Start: pošle „/start" s parametrem z odkazu, bez odkazu prázdný. */
    fun startBot() {
        if (state.value.startingBot) return
        val parameter = state.value.botStartParameter.orEmpty()
        state.update { it.copy(startingBot = true) }
        scope.launch {
            repository.startBot(chatId, parameter)
                .onSuccess { pending ->
                    botStartRequests.consume(chatId)
                    state.update { it.withMessage(pending).copy(startingBot = false) }
                }
                .onFailure { failure ->
                    state.update {
                        it.copy(startingBot = false, error = failure.reported(logger, Res.string.conversation_bot_start_failed))
                    }
                }
        }
    }

    /**
     * Tlačítko „přihlásit na web" (`getLoginUrlInfo`).
     *
     * - **Web se dozví jméno, uživatelské jméno a fotku účtu**, takže se přihlášení potvrzuje
     *   dialogem s doménou ([LoginRequest]) — tlačítko popisuje bot a vést může kamkoli.
     * - Když server řekne „otevřít rovnou", jde to cestou obyčejného odkazu, i s potvrzením
     *   adresy, pokud ho server nevypne (`skipConfirmation`).
     * - **Chyba = obyčejný odkaz** (tak to předepisuje TDLib): tlačítko nezůstane mrtvé.
     */
    private fun loginWithButton(messageId: Long, button: BotButtonKind.LoginUrl) {
        scope.launch {
            repository.loginUrlInfo(chatId, messageId, button.buttonId)
                .onSuccess { prompt ->
                    when (prompt) {
                        is LoginUrlPrompt.Open ->
                            if (prompt.skipConfirmation) urlOpener.open(prompt.url) else links.openLink(prompt.url, confirmExternal = true)
                        is LoginUrlPrompt.Confirm ->
                            state.update { it.copy(loginRequest = LoginRequest(messageId, button.buttonId, prompt)) }
                    }
                }
                .onFailure { links.openLink(button.url, confirmExternal = true) }
        }
    }

    /** Přihlášení potvrzené v dialogu; [allowWriteAccess] = bot smí uživateli psát. */
    fun confirmLogin(allowWriteAccess: Boolean) {
        val request = state.value.loginRequest ?: return
        state.update { it.copy(loginRequest = null) }
        scope.launch {
            repository.loginUrl(chatId, request.messageId, request.buttonId, allowWriteAccess)
                .onSuccess(urlOpener::open)
                // Přihlásit se nepovedlo, ale web uživatel chtěl — otevře se bez přihlášení a řekne se proč.
                .onFailure {
                    state.update { it.copy(notice = UiText(Res.string.bot_login_failed)) }
                    urlOpener.open(request.prompt.url)
                }
        }
    }

    fun dismissLogin() = state.update { it.copy(loginRequest = null) }

    fun dismissBotAlert() = state.update { it.copy(botAlert = null) }

    /**
     * Tlačítko bota pod zprávou.
     *
     * - **Odkaz mimo Telegram se napřed potvrzuje.** U odkazu v textu uživatel adresu vidí,
     *   na tlačítku ne — popisek píše bot a „Ověřit účet" může vést kamkoli.
     * - **Na odpověď bota se čeká jen u jednoho tlačítka naráz.** Druhé ťuknutí během čekání by
     *   poslalo zpětné volání podruhé a u kontrolní otázky s jedním pokusem spálilo odpověď.
     * - Co Plume neumí, se VYSVĚTLÍ, tlačítko nezmizí (viz [BotButtonKind.Unsupported]).
     */
    fun pressBotButton(messageId: Long, row: Int, column: Int) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val button = message.keyboard?.rows?.getOrNull(row)?.getOrNull(column) ?: return
        when (val kind = button.kind) {
            BotButtonKind.Callback -> askBot(messageId, row, column, button.text)
            is BotButtonKind.Url -> links.openLink(kind.url, confirmExternal = true)
            is BotButtonKind.CopyText -> scope.launch {
                clipboard.copy(kind.text)
                if (!clipboard.confirmsCopy) state.update { it.copy(notice = UiText(Res.string.bot_text_copied)) }
            }
            is BotButtonKind.User -> links.openMentioned { chatRepository.privateChat(kind.userId) }
            is BotButtonKind.WebApp -> links.requestWebApp(
                WebAppRequest(botOf(message), kind.url, launch = true),
            )
            BotButtonKind.Disabled -> Unit
            is BotButtonKind.LoginUrl -> loginWithButton(messageId, kind)
            is BotButtonKind.SwitchInline -> when (val target = kind.target) {
                SwitchInlineTarget.Current -> suggestions.useInlineBot(kind.bot, kind.query)
                is SwitchInlineTarget.Chosen ->
                    state.update { it.copy(switchInline = SwitchInlinePick(kind.bot, kind.query, target)) }
            }
            is BotButtonKind.Unsupported -> state.update { it.copy(notice = UiText(kind.feature.explanation())) }
        }
    }

    private fun askBot(messageId: Long, row: Int, column: Int, text: String) {
        if (state.value.pressingBotButton != null) return
        state.update { it.copy(pressingBotButton = BotButtonRef(messageId, row, column)) }
        scope.launch {
            repository.pressBotButton(chatId, messageId, row, column, text)
                .onSuccess { answer ->
                    state.update { current ->
                        when {
                            answer.text.isEmpty() -> current
                            answer.alert -> current.copy(botAlert = answer.text)
                            else -> current.copy(notice = UiText(Res.string.bot_answer, answer.text))
                        }
                    }
                    // Hra nebo start jiného bota; odkaz od bota je stejně skrytý jako na tlačítku.
                    if (answer.url.isNotEmpty()) links.openLink(answer.url, confirmExternal = true)
                }
                .onFailure { failure ->
                    val message = failure.reported(logger, Res.string.bot_button_failed)
                    state.update { it.copy(error = message) }
                }
            state.update { it.copy(pressingBotButton = null) }
        }
    }

    /**
     * Hlas v anketě; prázdný seznam hlas zruší („Zrušit hlas" v nabídce).
     *
     * Nic se lokálně nepřepisuje: TDLib po `setPollAnswer` sám změní obsah zprávy — napřed
     * s `isBeingChosen` (u odpovědi se točí indikátor), po potvrzení se skutečnými počty.
     * Obojí dorazí přes [ConversationRepository.editedMessages] jako každá úprava.
     */
    fun vote(messageId: Long, answerIndexes: List<Int>) {
        val poll = state.value.messages.firstOrNull { it.id == messageId }?.poll ?: return
        // Hlas, který by server odmítl, se neposílá: uzavřená anketa, druhý hlas bez zrušení.
        val allowed = if (answerIndexes.isEmpty()) poll.canRetract else poll.canVote
        if (!allowed) return
        scope.launch {
            repository.vote(chatId, messageId, answerIndexes).onFailure { failure ->
                val message = failure.reported(logger, 
                    if (answerIndexes.isEmpty()) Res.string.poll_retract_failed else Res.string.poll_vote_failed,
                )
                state.update { it.copy(error = message) }
            }
        }
    }

    fun cancelStopPoll() {
        state.update { it.copy(pendingStopPoll = null) }
    }

    /** Uzavření ankety se ukáže samo — TDLib změní obsah zprávy a výsledky se odkryjí. */
    fun confirmStopPoll() {
        val messageId = state.value.pendingStopPoll ?: return
        state.update { it.copy(pendingStopPoll = null) }
        scope.launch {
            repository.stopPoll(chatId, messageId).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.poll_stop_failed)) }
            }
        }
    }

    /** Bot, kterému zpráva s tlačítkem patří: odesílatel (kladné id = uživatel), jinak chat s botem. */
    private fun botOf(message: ChatMessage?): Long = message?.senderId?.takeIf { it > 0 } ?: chatId

    fun consumeOpenBotShare() = state.update { it.copy(openBotShare = null) }

    fun consumeOpenPollComposer() {
        state.update { it.copy(openPollComposer = null) }
    }

    fun dismissPollVoters() {
        state.update { it.copy(pollVoters = null) }
    }

    /**
     * Stav účasti v soutěži. Sheet se otevře hned a stav dorazí ze serveru (`getGiveawayInfo`);
     * kdyby se čekalo s otevřením, ťuknutí by na chvíli vypadalo, že nic neudělalo.
     */
    fun showGiveaway(messageId: Long) {
        state.update { it.copy(giveaway = GiveawaySheet(messageId), messageMenu = null) }
        scope.launch {
            val result = repository.giveawayStatus(chatId, messageId)
                .onFailure { logger.w(it) { "Stav soutěže $messageId se nepodařilo zjistit" } }
            state.update { current ->
                val sheet = current.giveaway?.takeIf { it.messageId == messageId } ?: return@update current
                current.copy(giveaway = sheet.copy(status = result.getOrNull(), failed = result.isFailure))
            }
        }
    }

    fun dismissGiveaway() {
        state.update { it.copy(giveaway = null) }
    }

    /** Dárkový kód Premium do schránky; potvrzení ukáže sheet sám — snackbar by byl pod ním. */
    fun copyGiftCode(code: String) {
        scope.launch { clipboard.copy(code) }
        state.update { current -> current.copy(giveaway = current.giveaway?.copy(codeCopied = true)) }
    }

    /**
     * Ťuknutí na hlasujícího: uživatel otevře soukromý chat, chat (anonymní správce, kanál)
     * sám sebe. Id drží konvenci odesílatele — kladné uživatel, záporné chat.
     */
    fun openPollVoter(senderId: Long) {
        state.update { it.copy(pollVoters = null) }
        if (senderId > 0) {
            links.openMentioned { chatRepository.privateChat(senderId) }
        } else if (senderId != chatId) {
            links.openTarget(ChatTarget(senderId))
        }
    }

    /**
     * Vynucená odpověď: pole se samo přepne na odpověď na zprávu bota — jednou na zprávu, a jen
     * když se zrovna neupravuje ani neodpovídá na něco jiného. Rozepsanou odpověď by jinak
     * přepsala pod rukama.
     *
     * Zpráva bota do seznamu dorazí `newMessages`, klidně až po tomhle updatu, takže se na ni
     * chvíli počká.
     */
    fun applyForceReply(messageId: Long) {
        if (!forceReplied.add(messageId)) return
        scope.launch {
            withTimeoutOrNull(FORCE_REPLY_WAIT) { state.first { current -> current.messages.any { it.id == messageId } } }
                ?: return@launch
            val current = state.value
            if (current.editing == null && current.replyingTo == null) replyTo(messageId)
        }
    }

    /**
     * Tlačítko vlastní klávesnice bota.
     *
     * Ve skupině jde text jako ODPOVĚĎ na zprávu s klávesnicí — tak to dělá oficiální klient
     * a bot mezi desítkami zpráv pozná, že patří jemu. V soukromém chatu je citace šum.
     */
    fun pressReplyKeyboardButton(row: Int, column: Int) {
        val keyboard = state.value.replyMarkup as? ChatReplyMarkup.Keyboard ?: return
        val button = keyboard.rows.getOrNull(row)?.getOrNull(column) ?: return
        when (val kind = button.kind) {
            ReplyKeyboardButtonKind.Text -> scope.launch {
                repository.sendKeyboardText(chatId, button.text, keyboard.replyTarget())
                    .onSuccess { keyboardUsed(keyboard) }
                    .onFailure { failure ->
                        state.update { it.copy(error = failure.reported(logger, Res.string.conversation_send_failed)) }
                    }
            }
            // Číslo je údaj o uživateli; odejde až po potvrzení.
            ReplyKeyboardButtonKind.PhoneNumber -> state.update { it.copy(confirmSharePhone = true) }
            // Poloha taky; zjišťuje ji až obrazovka po potvrzení (oprávnění a GPS jsou její).
            ReplyKeyboardButtonKind.Location -> state.update { it.copy(confirmShareLocation = true) }
            // Výběr lidí a chatu je obrazovka nad konverzací; tlačítko patří zprávě s klávesnicí.
            is ReplyKeyboardButtonKind.Users ->
                state.update { it.copy(openBotShare = BotShareRequest(keyboard.messageId, users = kind.request)) }
            is ReplyKeyboardButtonKind.Chat ->
                state.update { it.copy(openBotShare = BotShareRequest(keyboard.messageId, chat = kind.request)) }
            // Formulář je obrazovka nad konverzací; otevírá ho obrazovka, ViewModel jen řekne jaký.
            is ReplyKeyboardButtonKind.Poll -> state.update { it.copy(openPollComposer = kind.mode) }
            is ReplyKeyboardButtonKind.WebApp -> {
                val owner = state.value.messages.firstOrNull { it.id == keyboard.messageId }
                links.requestWebApp(
                    WebAppRequest(botOf(owner), kind.url, launch = false, keyboardButtonText = button.text),
                )
            }
            is ReplyKeyboardButtonKind.Unsupported ->
                state.update { it.copy(notice = UiText(kind.feature.explanation())) }
        }
    }

    /**
     * Tlačítko nabídky bota: mini aplikace přes `openWebApp`, jako z tlačítka pod zprávou —
     * TDLib bere adresu z `botMenuButton` právě tam. Soukromý chat s botem má id bota.
     */
    fun openBotMenuApp() {
        val app = state.value.botMenuApp ?: return
        closeCommands()
        // Odkaz `t.me/bot/app` se zpracuje jako odkaz (TDLib to u tlačítka nabídky předepisuje).
        if (app.isLink) links.openLink(app.url) else links.requestWebApp(WebAppRequest(chatId, app.url, launch = true))
    }

    /** Tlačítko příkazů u pole: otevře seznam, druhé ťuknutí ho zavře. */
    fun toggleCommands() = state.update { it.copy(commandsOpen = !it.commandsOpen) }

    fun closeCommands() = state.update { it.copy(commandsOpen = false) }

    /**
     * `/příkaz` ťuknutý v textu zprávy. Jde jako samostatná zpráva a **rozepsaný koncept nechá
     * být** (`sendKeyboardText`, jako tlačítko vlastní klávesnice) — ťuknutí na příkaz v historii
     * není odeslání toho, co je v poli. Kam se psát nesmí, tam se nepošle nic; příkaz se tam ani
     * neukazuje jako klikatelný.
     */
    fun sendCommand(command: String) = sendBotCommand(command)

    fun sendBotCommand(command: String) {
        if (!state.value.canWrite) return
        scope.launch {
            repository.sendKeyboardText(chatId, command, replyToMessageId = null).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.conversation_send_failed)) }
            }
        }
    }

    fun sharePhone() {
        val keyboard = state.value.replyMarkup as? ChatReplyMarkup.Keyboard
        state.update { it.copy(confirmSharePhone = false) }
        scope.launch {
            repository.shareOwnPhone(chatId, keyboard?.replyTarget())
                .onSuccess { keyboard?.let(::keyboardUsed) }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_send_failed)) }
                }
        }
    }

    fun dismissSharePhone() = state.update { it.copy(confirmSharePhone = false) }

    fun dismissShareLocation() = state.update { it.copy(confirmShareLocation = false) }

    /**
     * Poloha z tlačítka klávesnice bota, už zjištěná obrazovkou. `null` = uživatel nedovolil
     * polohu, nebo ji zařízení nezjistilo — to musí být vidět, jinak by ťuknutí vypadalo, že nic
     * neudělalo. Jako odpověď ve skupině, stejně jako text tlačítka.
     */
    fun sendKeyboardLocation(point: GeoPoint?) {
        state.update { it.copy(confirmShareLocation = false) }
        if (point == null) {
            state.update { it.copy(notice = UiText(Res.string.bot_location_unavailable)) }
            return
        }
        val keyboard = state.value.replyMarkup as? ChatReplyMarkup.Keyboard
        scope.launch {
            repository.sendLocation(chatId, Place("", "", point.latitude, point.longitude), keyboard?.replyTarget(), quote = null)
                .onSuccess { keyboard?.let(::keyboardUsed) }
                .onFailure { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.conversation_location_send_failed)) }
                }
        }
    }

    private fun ChatReplyMarkup.replyTarget(): Long? = messageId.takeIf { state.value.info?.isGroup == true }

    /**
     * Jednorázová klávesnice se po použití ohlásí TDLib (`deleteChatReplyMarkup`) — chce to po
     * klientovi výslovně. Klávesnice tím NEZMIZÍ: podle Bot API se jen schová a zůstává
     * dostupná, což dělá obrazovka sbalením panelu.
     */
    private fun keyboardUsed(keyboard: ChatReplyMarkup.Keyboard) {
        if (!keyboard.oneTime) return
        settings.markOneTimeKeyboardUsed(chatId, keyboard.messageId)
        scope.launch {
            repository.dismissReplyMarkup(chatId, keyboard.messageId).onFailure { failure ->
                logger.w(failure) { "Jednorázovou klávesnici bota se nepodařilo odebrat." }
            }
        }
    }

    /** Přidá do ankety odpověď z dialogu. Nová odpověď přijde úpravou obsahu zprávy. */
    fun addPollOption(text: String, media: PollDraftMedia?) {
        val messageId = state.value.addingPollOption ?: return
        val trimmed = text.trim()
        state.update { it.copy(addingPollOption = null) }
        if (trimmed.isEmpty()) return
        scope.launch {
            repository.addPollOption(chatId, messageId, trimmed, media).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.poll_add_option_failed)) }
            }
        }
    }

    fun dismissAddPollOption() {
        state.update { it.copy(addingPollOption = null) }
    }
}

private fun UnsupportedBotButton.explanation(): StringResource = when (this) {
    UnsupportedBotButton.SwitchInline -> Res.string.bot_unsupported_switch_inline
    UnsupportedBotButton.Buy -> Res.string.bot_unsupported_buy
    UnsupportedBotButton.Game -> Res.string.bot_unsupported_game
    UnsupportedBotButton.Password -> Res.string.bot_unsupported_password
}

private fun UnsupportedKeyboardButton.explanation(): StringResource = when (this) {
    UnsupportedKeyboardButton.ManagedBot -> Res.string.bot_unsupported_managed_bot
}

/** Jak dlouho se po vynucené odpovědi čeká, než zpráva bota dorazí do seznamu. */
private const val FORCE_REPLY_WAIT = 3_000L
