package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.EmojiRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Rozepsané slovo u kurzoru, ke kterému se hledají emoji: `text[start until end] == word`. */
data class EmojiWord(val start: Int, val end: Int, val word: String)

/**
 * Slovo, které končí u kurzoru, nebo `null`, když se k němu emoji nabízet nemají.
 *
 * Jen písmena (i s diakritikou) a jen celé slovo: kurzor uprostřed slova („srd|ce") nic nenabídne,
 * stejně jako slovo, které pokračuje číslicí. Nejméně [MIN_EMOJI_WORD] znaků — u jednoho písmene
 * by „a" nebo „i" nabízelo nesmysly. Slovo za `@`, `#`, `/` a v adrese (`.`, `:`) je zmínka,
 * hashtag, příkaz nebo odkaz, ne slovo.
 */
fun emojiWordAt(text: String, cursor: Int): EmojiWord? {
    if (cursor !in 0..text.length) return null
    if (cursor < text.length && text[cursor].isLetterOrDigit()) return null
    var start = cursor
    while (start > 0 && text[start - 1].isLetter()) start--
    if (cursor - start !in MIN_EMOJI_WORD..MAX_EMOJI_WORD) return null
    val before = text.getOrNull(start - 1)
    if (before != null && (before.isLetterOrDigit() || before in NOT_A_WORD_BEFORE)) return null
    return EmojiWord(start, cursor, text.substring(start, cursor))
}

/** Pole po výběru emoji: slovo nahradí emoji a kurzor stojí za ním. */
fun emojiReplacement(text: String, word: EmojiWord, emoji: String): Pair<String, Int> =
    text.replaceRange(word.start, word.end, emoji) to word.start + emoji.length

/** Emoji ke slovu; [word] je slovo, ke kterému patří — pomalá odpověď na starší se zahodí. */
data class EmojiSuggestions(val word: String, val emojis: List<String>)

/**
 * Návrhy emoji podle rozepsaného slova — „srdce" nabídne ❤️, jako u oficiálních klientů.
 *
 * Samostatný ViewModel jako návrhy odpovědí: obrazovka mu podává slovo u kurzoru a on vrací
 * emoji. Hledá se PŘESNÁ shoda klíčového slova (`getKeywordEmojis`), ne začátek: se začátkem by
 * panel vyskakoval u každého druhého slova. Výsledky se zúží na katalog (novější emoji by písmo
 * nenakreslilo) a dostanou zvolený odstín pleti, stejně jako ve výběru emoji.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmojiSuggestionsViewModel(
    private val emojiRepository: EmojiRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private data class Request(val word: String?, val languageCodes: List<String>)

    private val request = MutableStateFlow(Request(null, emptyList()))

    val suggestions: StateFlow<EmojiSuggestions?> = combine(
        request
            .debounce { if (it.word == null) 0L else LOOKUP_DELAY_MS }
            .distinctUntilChanged()
            .mapLatest { request ->
                val word = request.word ?: return@mapLatest null
                val found = emojiRepository.keywordEmojis(word.lowercase(), request.languageCodes)
                    .mapNotNull(::catalogEmoji)
                    .distinct()
                    .take(MAX_EMOJI_SUGGESTIONS)
                EmojiSuggestions(word, found).takeIf { found.isNotEmpty() }
            },
        settings.emojiSkinTones,
    ) { found, tones ->
        found?.copy(emojis = found.emojis.map { tones[baseEmoji(it)] ?: it })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Slovo u kurzoru, nebo `null`, když se nemá nabízet nic. */
    fun lookup(word: String?, languageCodes: List<String>) {
        request.value = Request(word, languageCodes)
    }

    /** Vybrané emoji jde mezi nedávné, jako by ho uživatel ťukl ve výběru. */
    fun picked(emoji: String) {
        settings.recordRecentEmoji(emoji)
        request.value = request.value.copy(word = null)
    }
}

internal const val MIN_EMOJI_WORD = 2

/** Delší slovo klíčovým slovem pro emoji nebývá a dotaz by byl zbytečný. */
private const val MAX_EMOJI_WORD = 24

private const val NOT_A_WORD_BEFORE = "@#/_.:\\-"

private const val MAX_EMOJI_SUGGESTIONS = 12

/** Pauza v psaní, než se slovo hledá — rozepsané „srd" se nemá ptát zbytečně. */
private const val LOOKUP_DELAY_MS = 200L

private const val STOP_TIMEOUT_MS = 5_000L
