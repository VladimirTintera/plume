package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rychlá reakce účtu — ta, kterou přidá dvojité ťuknutí na zprávu.
 *
 * **Je to nastavení ÚČTU, ne aplikace** (`setDefaultReactionType`): Telegram ho drží na serveru,
 * takže platí i v oficiálních klientech a na ostatních zařízeních. Plume si lokálně pamatuje jen
 * to, jestli dvojité ťuknutí reaguje vůbec (`AppSettings.doubleTapReaction`).
 */
interface QuickReaction {

    /** Klíč reakce (viz [customEmojiReaction]); `null`, dokud ji TDLib neohlásí. */
    val reaction: StateFlow<String?>

    /** Emoji, která jdou jako reakce použít (`updateActiveEmojiReactions`), v pořadí od serveru. */
    val choices: StateFlow<List<String>>

    suspend fun set(reaction: String): Result<Unit>
}

/**
 * [QuickReaction] nad TDLib.
 *
 * **Musí vznikat eagerně.** Getter TDLib nemá — výchozí reakci i seznam emoji ohlásí jen updatem,
 * jednou po přihlášení (a pak na změnu). Při líné konstrukci by oba updaty odešly do prázdna.
 */
class TdlQuickReaction(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : QuickReaction {

    private val logger = Logger.withTag("Telegram")

    private val _reaction = MutableStateFlow<String?>(null)
    override val reaction: StateFlow<String?> = _reaction.asStateFlow()

    private val _choices = MutableStateFlow<List<String>>(emptyList())
    override val choices: StateFlow<List<String>> = _choices.asStateFlow()

    init {
        // Přes holder: po odhlášení vzniká nový klient a odběr se na něj přepne sám.
        scope.launch {
            holder.updates { defaultReactionTypeUpdates }.collect { update ->
                _reaction.value = update.reactionType.toReactionKey()
            }
        }
        // Nový účet ohlásí svoje; do té doby nesmí platit reakce toho odhlášeného.
        scope.launch {
            holder.replaced.collect {
                _reaction.value = null
                _choices.value = emptyList()
            }
        }
        scope.launch {
            holder.updates { activeEmojiReactionsUpdates }.collect { update ->
                _choices.value = update.emojis.toList()
            }
        }
    }

    override suspend fun set(reaction: String): Result<Unit> {
        val before = _reaction.value
        // Lokálně hned: update od TDLib potvrdí, při selhání se vrátí původní.
        _reaction.value = reaction
        return holder.client.setDefaultReactionType(reactionType = reaction.toReactionType())
            .toResult()
            .map { }
            .onFailure { failure ->
                logger.w(failure) { "setDefaultReactionType selhalo." }
                _reaction.value = before
            }
    }
}
