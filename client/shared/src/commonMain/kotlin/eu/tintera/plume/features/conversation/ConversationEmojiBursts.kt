package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.features.conversation.items.EmojiBurst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Efekty animovaných emoji, které vidí obě strany: ťuknutí na emoji pošle efekt protějšku
 * (`clickAnimatedEmojiMessage`) a jeho ťuknutí se přehraje u nás (`animatedEmojiMessageClicked`).
 *
 * Efekt čeká v [bursts], dokud si ho nevyzvedne zpráva na obrazovce ([shown]); TDLib chce, ať se
 * přehraje, jen když je zpráva vidět. Vyzvednout se dá jen chvíli ([FRESH_MILLIS]) — zpráva, na
 * kterou se odscrolluje až potom, už nevybuchne: efekt patří k okamžiku ťuknutí.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationEmojiBursts(
    private val chatId: Long,
    private val state: StateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val logger: Logger,
) {
    private val _bursts = MutableStateFlow<Map<Long, EmojiBurst>>(emptyMap())

    /** Efekty, které čekají, až si je zpráva vyzvedne; klíč je id zprávy. */
    val bursts: StateFlow<Map<Long, EmojiBurst>> = _bursts.asStateFlow()

    private var runs = 0L

    init {
        scope.launch {
            repository.emojiInteractions(chatId).collect { interaction ->
                publish(interaction.messageId, interaction.animationFileId, fromPartner = true)
            }
        }
    }

    /**
     * Ťuknutí na animované emoji. Odezvu (poskočení, hmatový tep) dělá zpráva sama hned při
     * ťuknutí; tady se jen čeká, jestli emoji má velký efekt — ten pak dorazí přes [bursts].
     */
    fun tap(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        // Odesílaná zpráva na serveru ještě není (dočasné id) a TDLib by ťuknutí odmítl.
        if (message.sendState == SendState.Sending || message.sendState == SendState.Failed) return
        scope.launch {
            repository.clickAnimatedEmoji(chatId, messageId)
                .onSuccess { fileId -> if (fileId != null) publish(messageId, fileId, fromPartner = false) }
                // Bez efektu se ťuknutí neztratí — emoji poskočilo; hláška by tu byla navíc.
                .onFailure { failure -> logger.w(failure) { "clickAnimatedEmojiMessage selhalo" } }
        }
    }

    /**
     * Zpráva efekt přehrává. Efekt od protějšku se mu nahlásí („sleduje animaci"), jak TDLib
     * žádá u každého přehraného efektu.
     */
    fun shown(burst: EmojiBurst) {
        _bursts.update { if (it[burst.messageId] == burst) it - burst.messageId else it }
        if (!burst.fromPartner) return
        val emoji = (state.value.messages.firstOrNull { it.id == burst.messageId }?.media as? MessageMedia.AnimatedEmoji)
            ?.emoji ?: return
        scope.launch {
            repository.sendWatchingAnimation(chatId, emoji)
                .onFailure { failure -> logger.d(failure) { "chatActionWatchingAnimations selhalo" } }
        }
    }

    private fun publish(messageId: Long, animationFileId: Int, fromPartner: Boolean) {
        val burst = EmojiBurst(messageId = messageId, run = ++runs, animationFileId = animationFileId, fromPartner = fromPartner)
        _bursts.update { it + (messageId to burst) }
        scope.launch {
            delay(FRESH_MILLIS)
            _bursts.update { if (it[messageId] == burst) it - messageId else it }
        }
    }

    private companion object {
        /** Jak dlouho efekt čeká na zprávu, která ho přehraje. */
        const val FRESH_MILLIS = 1_500L
    }
}
