package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Immutable
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.onThisDayKey
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Pruh „Před rokem" nad konverzací ([ConversationState.onThisDay]): v tentýž den před [yearsAgo]
 * lety se v chatu psalo — [count] zpráv, první s textem jako ukázka. Ťuknutí skočí na začátek
 * toho dne ([dayStart]).
 */
@Immutable
data class OnThisDay(
    val yearsAgo: Int,
    /** Začátek toho dne (sekundy epochy, místní půlnoc) — cíl skoku. */
    val dayStart: Int,
    val count: Int,
    /** Den se nedočetl (strop stránek) — zpráv je aspoň [count]. */
    val capped: Boolean,
    /** Ukázka: první zpráva dne s textem, jinak první vůbec. */
    val preview: ChatMessage,
)

/**
 * **„Před rokem"**: při otevření chatu se podívá, jestli se v něm psalo v tentýž den před
 * rokem, a když ne, před dvěma až [MAX_YEARS] lety; najde-li, ukáže pruh. Telegram nic takového
 * nemá a kalendář zpráv to nenabídne sám od sebe.
 *
 * - **Jednou za otevření, s odstupem** ([START_DELAY_MS]): dotaz jde přes server a nemá
 *   zdržet úvodní načtení historie, kvůli kterému uživatel chat otevřel.
 * - **Den čte [messagesBetween]** (tatáž cesta jako shrnutí dne v kalendáři), nejvýš [MAX_PAGES]
 *   stránek — na počet a ukázku stačí, u rušného dne je počet „N+".
 * - **Chat, který začal později, se dál neprohledává** ([MessagesBetween.chatStartsAfter]):
 *   chat starý půl roku by jinak stál pět zbytečných dotazů.
 * - **Zavření i ťuknutí platí do konce dne** (`AppSettings.dismissOnThisDay`) a vypnout se to
 *   dá v Nastavení → Chaty.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationOnThisDay(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val settings: AppSettings,
    /** Skok na začátek dne (sekundy epochy) — jako výběr data v hledání. */
    private val jumpToDate: (Int) -> Unit,
    private val logger: Logger,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val today: () -> LocalDate = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
) {
    /** Spustí hledání; volá ViewModel jednou při otevření chatu (jen celého chatu). */
    fun check() {
        if (!settings.onThisDay.value) return
        val day = today()
        if (onThisDayKey(day.toEpochDays(), chatId) in settings.onThisDayDismissed.value) return
        scope.launch {
            delay(START_DELAY_MS)
            for (years in 1..MAX_YEARS) {
                val date = day.minus(DatePeriod(years = years))
                val start = date.atStartOfDayIn(zone()).epochSeconds.toInt()
                val end = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone()).epochSeconds.toInt()
                val found = repository.messagesBetween(chatId, start, end, cap = MAX_PAGES * PAGE_SIZE, pages = MAX_PAGES, pageSize = PAGE_SIZE)
                val messages = found.messages
                if (messages.isNotEmpty()) {
                    val preview = messages.firstOrNull { it.text.isNotBlank() || it.description != null } ?: messages.first()
                    logger.d { "Před rokem: chat $chatId, před $years lety ${messages.size} zpráv." }
                    state.update {
                        it.copy(
                            onThisDay = OnThisDay(
                                yearsAgo = years,
                                dayStart = start,
                                count = messages.size,
                                capped = !found.complete,
                                preview = preview,
                            ),
                        )
                    }
                    return@launch
                }
                if (found.chatStartsAfter) return@launch
            }
        }
    }

    /** Ťuknutí na pruh: skočit na začátek toho dne. Pruh tím zmizí do konce dne. */
    fun open() {
        val shown = state.value.onThisDay ?: return
        dismiss()
        jumpToDate(shown.dayStart)
    }

    /** Křížek: pruh pryč do konce dne. */
    fun dismiss() {
        state.value.onThisDay ?: return
        settings.dismissOnThisDay(today().toEpochDays(), chatId)
        state.update { it.copy(onThisDay = null) }
    }
}

/** Nejdál kolik let zpátky se hledá. */
private const val MAX_YEARS = 5

/** Odstup od otevření chatu — ať dotaz nezdrží úvodní historii. */
internal const val START_DELAY_MS = 1_500L

private const val PAGE_SIZE = 100
private const val MAX_PAGES = 2
