package eu.tintera.plume.core.telegram

/**
 * Anketa nebo kvíz ve zprávě (`MessagePoll`).
 *
 * Zpráva s anketou má dál i [ChatMessage.text] — „Anketa: otázka". Tím anketa nepřijde o popis
 * tam, kde se kreslit nedá (citace, připnutá zpráva, náhled v seznamu, shrnutí); blok ankety
 * ten text jen nekreslí.
 *
 * [options] jsou v POŘADÍ, V JAKÉM SE UKAZUJÍ (`Poll.optionOrder`, když ho server posílá).
 * Hlasuje se ale indexem v původním pořadí, a ten nese [PollAnswer.index] — viz
 * [ConversationRepository.vote].
 */
data class ChatPoll(
    val question: String,
    /** Popis pod otázkou (TDLib 15); prázdný je běžný stav. */
    val description: String = "",
    val descriptionEntities: List<MessageEntity> = emptyList(),
    val options: List<PollAnswer>,
    val totalVoters: Int,
    val isAnonymous: Boolean,
    /** Smí se vybrat víc odpovědí naráz — pak se hlasuje tlačítkem, ne ťuknutím na odpověď. */
    val multipleAnswers: Boolean,
    /** Hlas jde vzít zpátky (a hlasovat znovu). */
    val allowsRevoting: Boolean,
    val isClosed: Boolean,
    /** Kdy se anketa sama uzavře (unixový čas); `0` = sama se neuzavře. */
    val closeDate: Int = 0,
    /**
     * Smí uživatel vidět výsledky. Anketa si je může schovat do uzavření — pak po hlasování
     * zůstane vidět jen vlastní volba.
     */
    val canSeeResults: Boolean,
    /** Jde zjistit, kdo jak hlasoval (`getPollVoters`) — jen u neanonymní ankety s výsledky. */
    val canGetVoters: Boolean,
    val quiz: PollQuiz? = null,
    /** Proč uživatel hlasovat nemůže; `null` = může. */
    val restriction: PollRestriction? = null,
) {
    val hasVoted: Boolean get() = options.any { it.isChosen }

    /** Hlas odešel a server ho ještě nepotvrdil (`isBeingChosen`). */
    val isVoting: Boolean get() = options.any { it.isBeingChosen }

    /**
     * Ukazují se výsledky (pruhy a procenta), nebo odpovědi k hlasování.
     *
     * Po hlasování a v uzavřené anketě výsledky — ale jen když je server vydá: anketa, která je
     * schovává do uzavření, ukáže po hlasování jen vlastní volbu.
     */
    val showsResults: Boolean get() = canSeeResults && (hasVoted || isClosed)

    /** Jde teď hlasovat: nikdo nebrání, a ještě se nehlasovalo (změna hlasu jde přes zrušení). */
    val canVote: Boolean get() = restriction == null && !isClosed && !hasVoted && !isVoting

    /**
     * „Zrušit hlas" v nabídce. U kvízu nikdy — odpověď na kvíz je odpověď, ne názor, a server
     * by zrušení stejně odmítl.
     */
    val canRetract: Boolean get() = hasVoted && allowsRevoting && quiz == null && !isClosed && restriction == null
}

/**
 * Jedna odpověď. [index] je pozice v PŮVODNÍM pořadí odpovědí — tím se hlasuje
 * (`setPollAnswer` bere 0-based identifikátory) i ptá na hlasující.
 *
 * [voters] a [percentage] vydá server jen tam, kde se výsledky smí vidět; jinak jsou nulové.
 * Procenta se berou od serveru, ne dopočítávají: server je zaokrouhluje tak, aby dala dohromady
 * sto, což obyčejné zaokrouhlení nezaručí.
 */
data class PollAnswer(
    val index: Int,
    val text: String,
    val voters: Int,
    val percentage: Int,
    val isChosen: Boolean,
    val isBeingChosen: Boolean = false,
)

/** Kvíz: správné odpovědi a vysvětlení. Obojí server vydá až po odpovědi. */
data class PollQuiz(
    /** Indexy správných odpovědí v PŮVODNÍM pořadí; prázdné, dokud se neodpovědělo. */
    val correct: Set<Int>,
    val explanation: String = "",
    val explanationEntities: List<MessageEntity> = emptyList(),
)

/** Proč hlasovat nejde. */
enum class PollRestriction {
    /** Anketa je uzavřená. */
    Closed,

    /** Hlasovat smí jen ten, kdo je v chatu aspoň den. */
    MembershipRequired,

    /** Anketa je jen pro některé země. */
    CountryRestricted,

    /** Anketa ještě neodešla (vlastní odesílaná nebo naplánovaná zpráva). */
    NotSent,

    Other,
}

/**
 * Jak dopadla odpověď v kvízu — podle ní se odpověď obarví. `null` = nic zvláštního (běžná
 * anketa, nebo kvíz, u kterého server správnou odpověď ještě nevydal).
 */
enum class QuizMark { Correct, Wrong }

/**
 * Značka odpovědi v kvízu: správná zeleně vždycky (i když ji uživatel nevybral — ať vidí, co
 * bylo správně), vybraná špatná červeně. Ostatní bez značky.
 */
fun ChatPoll.quizMark(answer: PollAnswer): QuizMark? {
    val correct = quiz?.correct?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        answer.index in correct -> QuizMark.Correct
        answer.isChosen -> QuizMark.Wrong
        else -> null
    }
}

/** Hlasující u jedné odpovědi; [total] je odhad serveru, [voters] co se zatím načetlo. */
data class PollVotersPage(val voters: List<SenderInfo>, val total: Int)

/**
 * Anketa k odeslání ([ConversationRepository.sendPoll]). Platnost (délky, počet odpovědí,
 * správná odpověď u kvízu) hlídá formulář; tohle už jsou hotová data.
 */
data class PollDraft(
    val question: String,
    val answers: List<String>,
    val anonymous: Boolean,
    val multipleAnswers: Boolean,
    /** Odpovědi se každému ukážou v jiném pevném pořadí (`shuffleOptions`). */
    val shuffle: Boolean,
    /** Kvíz: indexy správných odpovědí v [answers]; `null` = běžná anketa. */
    val correctAnswers: Set<Int>? = null,
    val explanation: String = "",
)

/** Nejméně odpovědí. TDLib by vzal i jednu, ale anketa s jedinou možností nic nezjistí. */
const val MIN_POLL_ANSWERS = 2

/** Délka otázky (TDLib: 1–255 znaků, víc jen boti). */
const val POLL_QUESTION_MAX = 255

/** Délka odpovědi (TDLib: 1–100 znaků). */
const val POLL_ANSWER_MAX = 100

/** Vysvětlení kvízu (TDLib: 0–200 znaků a nejvýš dva konce řádku). */
const val POLL_EXPLANATION_MAX = 200
const val POLL_EXPLANATION_MAX_LINE_FEEDS = 2

/**
 * Co smí formulář nové ankety založit. Tlačítko bota „vytvořit anketu"
 * (`KeyboardButtonTypeRequestPoll`) si může vynutit jen kvíz, nebo jen běžnou anketu —
 * přepínač kvízu se pak nekreslí.
 */
enum class PollComposerMode { Any, RegularOnly, QuizOnly }

