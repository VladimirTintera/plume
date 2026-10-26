package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.ai.AiTask
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.scamVerdictOf
import eu.tintera.plume.core.ai.generateAnswer
import eu.tintera.plume.core.ai.ScamAnswer
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.core.ai.parseScamVerdict
import eu.tintera.plume.core.ai.scamPrompt
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.Verification
import eu.tintera.plume.core.ui.foldDiacritics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stojí zpráva za to, aby ji posoudil model? Levný předfiltr, který rozhoduje, kdy model vůbec
 * běží: odkaz a aspoň jedno typické slovo, nebo aspoň dvě typická slova. Běžná zpráva („v kolik
 * přijdeš?") se k modelu nedostane, takže ho nestojí baterii a nemůže u ní zmýlit.
 *
 * Slova se porovnávají bez diakritiky, většinou jako ZAČÁTEK slova („prihlas" chytí „přihlásit"
 * i „přihlášení"); krátká slova celá, jinak by „won" chytilo „wonderful".
 */
fun isScamCandidate(message: ChatMessage): Boolean {
    if (message.isOutgoing || message.isService || message.text.isBlank()) return false
    // Ověřený odesílatel (Telegram sám, banka s fajfkou) se neposuzuje: „Kód pro přihlášení"
    // od služby Telegramu je přesně ta zpráva, u které by varování bylo nejhorší.
    if (message.senderVerification is Verification.Verified) return false
    val words = WORD.findAll(foldDiacritics(message.text).lowercase()).map { it.value }.toList()
    val hits = SCAM_STEMS.count { stem -> words.any { it.startsWith(stem) } } +
        SCAM_WORDS.count { it in words }
    val hasLink = message.entities.any { it.type is MessageEntityType.Link } || LINK.containsMatchIn(message.text)
    return hits >= 2 || (hasLink && hits >= 1)
}

/**
 * Posuzuje se v tomhle chatu vůbec? Ne ve vlastních Uložených zprávách, v kanálech (píše tam
 * jen správce, kterého si uživatel sám vybral) a v ověřených chatech.
 */
fun scamCheckApplies(info: ChatInfo): Boolean =
    !info.isSavedMessages && !info.isChannel && info.verification !is Verification.Verified

/**
 * Varování u příchozích zpráv, které vypadají jako podvod — posouzení modelem v zařízení.
 *
 * - **Obsah zprávy nikam neodchází**: posuzuje model v telefonu, nic se neposílá na server.
 * - **Jen se zapnutou volbou, zapnutou AI a modelem, který je PŘIPRAVENÝ.** Kvůli varování se
 *   nic nestahuje.
 * - **Model se ptá po jedné zprávě**, frontou v [appScope]: na emulátoru trvá posouzení vteřiny
 *   a souběžné dotazy by se jen předbíhaly. Fronta přežije zavření konverzace, takže rozjeté
 *   posouzení doběhne a výsledek se při dalším otevření rovnou ukáže.
 * - **Výsledek se drží v paměti procesu**, podle (chat, zpráva). Po restartu se kandidáti
 *   posoudí znovu — je jich málo díky předfiltru ([isScamCandidate]).
 * - **Ve sporném případě se mlčí** ([parseScamVerdict]); chyba modelu = žádné varování.
 */
class ScamWarnings(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
    appScope: CoroutineScope,
) {
    private val logger = Logger.withTag("ScamWarnings")

    private val _verdicts = MutableStateFlow<Map<Pair<Long, Long>, ScamKind>>(emptyMap())

    /** Zprávy, u kterých se varuje, podle (chat, zpráva). Skryté uživatelem tu nejsou. */
    val verdicts: StateFlow<Map<Pair<Long, Long>, ScamKind>> = _verdicts.asStateFlow()

    /**
     * Zařazené nebo posouzené zprávy (i ty v pořádku), aby se model neptal dvakrát. `StateFlow`
     * kvůli atomické změně: [check] volá ViewModel na hlavním vlákně, [judge] běží ve frontě.
     */
    private val seen = MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())
    private val queue = Channel<Request>(Channel.UNLIMITED)

    private class Request(val chatId: Long, val messageId: Long, val text: String)

    init {
        appScope.launch {
            for (request in queue) judge(request)
        }
    }

    /** Zařadí zprávu k posouzení, pokud je to kandidát a ještě se neposuzovala. Levné. */
    fun check(chatId: Long, message: ChatMessage) {
        if (!enabled() || !isScamCandidate(message)) return
        val key = chatId to message.id
        var added = false
        seen.update { current ->
            added = key !in current
            current + key
        }
        if (!added) return
        queue.trySend(Request(chatId, message.id, message.text))
    }

    /** Uživatel varování skryl; znovu se neukáže, dokud proces běží. */
    fun dismiss(chatId: Long, messageId: Long) {
        _verdicts.update { it - (chatId to messageId) }
    }

    private fun enabled(): Boolean = settings.scamWarningsEnabled.value && settings.onDeviceAiEnabled.value

    private suspend fun judge(request: Request) {
        val key = request.chatId to request.messageId
        val variant = ai.variantFor(AiTask.Scam, settings)
        val ready = enabled() && runCatching { ai.status(variant) == OnDeviceAiStatus.Ready }.getOrDefault(false)
        if (!ready) {
            // Model zatím není: zpráva se posoudí, až se konverzace otevře znovu s hotovým modelem.
            seen.update { it - key }
            return
        }
        val kind = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                ai.generateAnswer(
                    variant,
                    ScamAnswer::class,
                    prompt = { structured -> scamPrompt(request.text, structured) },
                    fromStructured = ::scamVerdictOf,
                    fromText = ::parseScamVerdict,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i(e) { "Zprávu se nepodařilo posoudit" }
            null
        }
        if (kind != null) _verdicts.update { it + (key to kind) }
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}

private val WORD = Regex("[\\p{L}\\p{N}]+")

private val LINK = Regex("https?://|www\\.|t\\.me/", RegexOption.IGNORE_CASE)

/** Začátky slov typických pro podvody, česky (bez diakritiky) a anglicky. */
private val SCAM_STEMS = listOf(
    // čeština
    "overit", "overen", "overte", "overuj", "ucet", "uctu", "heslo", "hesla", "prihlas", "vyhr",
    "vyher", "platb", "zaplat", "bank", "kart", "investi", "krypto", "bitcoin", "zablok",
    "pozastav", "darek", "darku", "zdarma", "balik", "zasilk", "doplat", "vyzvedn", "odmen",
    // angličtina
    "verif", "account", "card", "password", "login", "winner", "prize", "gift", "payment", "invest",
    "crypto", "suspend", "claim", "wallet", "parcel", "deliver", "reward", "urgent",
)

/** Krátká slova, která se porovnávají celá. */
private val SCAM_WORDS = setOf("kod", "kodu", "kodem", "won", "free", "code")
