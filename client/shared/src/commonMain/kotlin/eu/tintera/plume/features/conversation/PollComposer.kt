package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.Country
import eu.tintera.plume.core.telegram.DEFAULT_POLL_DESCRIPTION_MAX
import eu.tintera.plume.core.telegram.POLL_MIN_CLOSE_AHEAD_SECONDS
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.MIN_POLL_ANSWERS
import eu.tintera.plume.core.telegram.POLL_ANSWER_MAX
import eu.tintera.plume.core.telegram.POLL_EXPLANATION_MAX
import eu.tintera.plume.core.telegram.POLL_EXPLANATION_MAX_LINE_FEEDS
import eu.tintera.plume.core.telegram.POLL_QUESTION_MAX
import eu.tintera.plume.core.telegram.PollDraft
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.durationText
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.schedule_send_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.conversation_schedule_past
import eu.tintera.plume.resources.conversation_schedule_time
import eu.tintera.plume.resources.conversation_send_schedule
import eu.tintera.plume.resources.conversation_send_silent
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_box_filled_24dp
import eu.tintera.plume.resources.check_box_outline_blank_24dp
import eu.tintera.plume.resources.check_circle_filled_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.poll_compose_add_answer
import eu.tintera.plume.resources.poll_compose_answer
import eu.tintera.plume.resources.poll_compose_answers
import eu.tintera.plume.resources.poll_compose_anonymous
import eu.tintera.plume.resources.poll_compose_anonymous_channel
import eu.tintera.plume.resources.poll_compose_counter
import eu.tintera.plume.resources.poll_compose_explanation
import eu.tintera.plume.resources.poll_compose_explanation_hint
import eu.tintera.plume.resources.poll_compose_mark_correct
import eu.tintera.plume.resources.poll_compose_multiple
import eu.tintera.plume.resources.poll_compose_question
import eu.tintera.plume.resources.poll_compose_quiz
import eu.tintera.plume.resources.poll_compose_quiz_hint
import eu.tintera.plume.resources.poll_compose_remove_answer
import eu.tintera.plume.resources.poll_compose_send
import eu.tintera.plume.resources.poll_compose_send_failed
import eu.tintera.plume.resources.poll_compose_settings
import eu.tintera.plume.resources.poll_compose_shuffle
import eu.tintera.plume.resources.poll_compose_title
import eu.tintera.plume.resources.poll_compose_title_quiz
import eu.tintera.plume.resources.poll_compose_too_many
import eu.tintera.plume.resources.radio_button_unchecked_24dp
import androidx.compose.runtime.remember
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.features.conversation.items.QuotedMessage
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.poll_compose_drop_reply
import eu.tintera.plume.resources.poll_compose_duration
import eu.tintera.plume.resources.poll_compose_hide_results
import eu.tintera.plume.resources.poll_duration_none
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.text.TextStyle
import eu.tintera.plume.core.telegram.flagEmoji
import eu.tintera.plume.core.telegram.localizedCountryName
import eu.tintera.plume.core.ui.CountryPickerSheet
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.poll_compose_close_invalid
import eu.tintera.plume.resources.poll_compose_close_time
import eu.tintera.plume.resources.poll_compose_countries
import eu.tintera.plume.resources.poll_compose_countries_all
import eu.tintera.plume.resources.poll_compose_countries_count
import eu.tintera.plume.resources.poll_compose_countries_hint
import eu.tintera.plume.resources.poll_compose_countries_title
import eu.tintera.plume.resources.poll_compose_description
import eu.tintera.plume.resources.poll_compose_members_only
import eu.tintera.plume.resources.poll_compose_voters
import eu.tintera.plume.resources.poll_duration_custom
import eu.tintera.plume.resources.search_filter_apply
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.POLL_QUESTION_MEDIA
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.resources.add_photo_alternate_24dp
import eu.tintera.plume.resources.poll_compose_add_media
import eu.tintera.plume.resources.poll_compose_remove_media
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.saveable.rememberSaveable
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.location.GeoPoint
import eu.tintera.plume.core.telegram.PlaceRepository
import eu.tintera.plume.core.telegram.PollDraftMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.StaticMap
import eu.tintera.plume.resources.gif_box_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.poll_link_add
import eu.tintera.plume.resources.poll_link_hint
import eu.tintera.plume.resources.poll_link_title
import eu.tintera.plume.resources.poll_media_gif
import eu.tintera.plume.resources.poll_media_link
import eu.tintera.plume.resources.poll_media_photo
import eu.tintera.plume.resources.poll_media_place
import eu.tintera.plume.resources.poll_place_title
import eu.tintera.plume.resources.poll_media_sticker
import kotlinx.coroutines.Job
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Pravidla formuláře jsou čisté funkce (testy `PollComposerTest`): co se pošle, kdy je formulář
 * hotový a jak se chovají pole odpovědí. Uvnitř `@Composable` by se hraniční případy — prázdná
 * pole uprostřed, správná odpověď, která se vymazala, strop odpovědí — neověřily.
 */

/**
 * Pole odpovědí po úpravě: **na konci je vždycky jedno prázdné**, do kterého se píše další
 * odpověď, dokud se nedojde ke stropu [max]. Přebytečná prázdná pole na konci zmizí, ale
 * nejméně [MIN_POLL_ANSWERS] polí zůstává vždycky.
 *
 * Tak to dělají i oficiální klienti: tlačítko „Přidat odpověď" by bylo ťuknutí navíc u každé
 * odpovědi. Prázdné pole UPROSTŘED se nechává — uživatel ho mohl vymazat jen proto, aby ho
 * přepsal; odebírá se křížkem.
 */
internal fun normalizedAnswers(answers: List<String>, max: Int): List<String> {
    val list = answers.toMutableList()
    while (list.size < MIN_POLL_ANSWERS) list += ""
    while (list.size > MIN_POLL_ANSWERS && list.last().isBlank() && list[list.lastIndex - 1].isBlank()) {
        list.removeAt(list.lastIndex)
    }
    if (list.last().isNotBlank() && list.size < max) list += ""
    return list
}

/**
 * Správné odpovědi kvízu po odebrání pole [removed]: jeho index zmizí a vyšší se posunou —
 * správná odpověď musí zůstat u TÉHOŽ textu, ne na téže pozici.
 */
internal fun <T> Map<Int, T>.afterRemoval(removed: Int): Map<Int, T> =
    entries.mapNotNull { (index, value) ->
        when {
            index == removed -> null
            index > removed -> (index - 1) to value
            else -> index to value
        }
    }.toMap()

/** Přílohy odpovědí po odebrání pole: jako u správných odpovědí patří TEXTU, ne pozici. */
internal fun correctAfterRemoval(correct: Set<Int>, removed: Int): Set<Int> =
    correct.mapNotNull { index ->
        when {
            index == removed -> null
            index > removed -> index - 1
            else -> index
        }
    }.toSet()

/**
 * Hotová anketa z formuláře, nebo `null`, dokud se poslat nedá (tlačítko Odeslat je pak
 * zašedlé). Prázdná pole odpovědí se vynechají; [correct] jsou indexy POLÍ a přepočítají se na
 * indexy odpovědí, které opravdu odejdou.
 *
 * Konec ankety je buď doba ([openPeriodSeconds]), nebo čas ([closeDate]); čas, který mezitím
 * vypršel nebo leží za stropem serveru ([closeDateValid]), formulář neodešle — server by ho
 * odmítl až po odeslání.
 */
internal fun pollDraftOf(
    question: String,
    answers: List<String>,
    anonymous: Boolean,
    multipleAnswers: Boolean,
    shuffle: Boolean,
    quiz: Boolean,
    correct: Set<Int>,
    explanation: String,
    maxAnswers: Int,
    openPeriodSeconds: Int = 0,
    hideResultsUntilClose: Boolean = false,
    description: String = "",
    maxDescription: Int = DEFAULT_POLL_DESCRIPTION_MAX,
    closeDate: Long = 0,
    now: Long = 0,
    maxOpenPeriodSeconds: Int = 0,
    membersOnly: Boolean = false,
    countryCodes: List<String> = emptyList(),
    maxCountries: Int = 0,
    media: PollDraftMedia? = null,
    /** Přílohy odpovědí podle indexu POLE, jako [correct]. */
    answerMedia: Map<Int, PollDraftMedia> = emptyMap(),
): PollDraft? {
    val text = question.trim()
    if (text.isEmpty() || text.length > POLL_QUESTION_MAX) return null

    val filled = answers.mapIndexedNotNull { index, answer -> answer.trim().takeIf { it.isNotEmpty() }?.let { index to it } }
    if (filled.size !in MIN_POLL_ANSWERS..maxAnswers) return null
    if (filled.any { (_, answer) -> answer.length > POLL_ANSWER_MAX }) return null

    val correctAnswers = if (quiz) {
        filled.mapIndexedNotNull { position, (field, _) -> position.takeIf { field in correct } }.toSet()
    } else {
        null
    }
    if (correctAnswers != null) {
        if (correctAnswers.isEmpty()) return null
        // Bez víc odpovědí je správná jen jedna — výběr to hlídá, tohle je pojistka.
        if (!multipleAnswers && correctAnswers.size > 1) return null
    }
    val why = if (quiz) explanation.trim() else ""
    if (why.length > POLL_EXPLANATION_MAX || why.count { it == '\n' } > POLL_EXPLANATION_MAX_LINE_FEEDS) return null

    val about = description.trim()
    if (about.length > maxDescription) return null

    if (closeDate > 0 && !closeDateValid(closeDate, now, maxOpenPeriodSeconds)) return null
    // Doba i čas jsou dvě podoby téže volby; čas má přednost.
    val period = if (closeDate > 0) 0 else openPeriodSeconds.coerceAtLeast(0)
    val closes = closeDate > 0 || period > 0

    if (countryCodes.size > maxCountries) return null
    // Samolepku a odkaz otázka nesmí — server by odmítl celou anketu. Formulář je u otázky
    // nenabízí, tohle je pojistka.
    if (media != null && !media.allowedForQuestion) return null

    return PollDraft(
        question = text,
        answers = filled.map { it.second },
        anonymous = anonymous,
        multipleAnswers = multipleAnswers,
        shuffle = shuffle,
        correctAnswers = correctAnswers,
        explanation = why,
        openPeriodSeconds = period,
        closeDate = closeDate.coerceAtLeast(0),
        // Výsledky až po uzavření jen s koncem ankety — bez něj by je nikdo nikdy neviděl.
        hideResultsUntilClose = hideResultsUntilClose && closes,
        description = about,
        membersOnly = membersOnly,
        countryCodes = countryCodes.distinct(),
        media = media,
        // Příloha prázdného pole neodejde: pole bez textu se vynechává, a s ním i ona.
        answerMedia = filled.map { (field, _) -> answerMedia[field] }.takeIf { list -> list.any { it != null } }.orEmpty(),
    )
}

/**
 * Smí anketa skončit v [closeDate]? Nejdřív za [POLL_MIN_CLOSE_AHEAD_SECONDS] od [now]
 * a nejpozději za strop serveru; bez stropu (`0`) vlastní čas nejde vůbec.
 */
internal fun closeDateValid(closeDate: Long, now: Long, maxOpenPeriodSeconds: Int): Boolean =
    maxOpenPeriodSeconds > 0 && closeDate - now in POLL_MIN_CLOSE_AHEAD_SECONDS..maxOpenPeriodSeconds.toLong()

/**
 * Čas uzavření z výběru data a času. Výběr data vrací **půlnoc vybraného dne v UTC**
 * ([dateUtcMillis], viz filtry hledání) — den se z ní vezme v UTC a hodina s minutou platí
 * v MÍSTNÍM pásmu [zone], protože tak je uživatel zadal.
 */
internal fun closeDateOf(dateUtcMillis: Long, hour: Int, minute: Int, zone: TimeZone): Long {
    val day = Instant.fromEpochMilliseconds(dateUtcMillis).toLocalDateTime(TimeZone.UTC).date
    return LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone).epochSeconds
}

/**
 * Dny, které výběr data nabídne: od dneška po den, kdy vyprší strop serveru — obojí v MÍSTNÍM
 * pásmu. [utcTimeMillis] je půlnoc dne v UTC, jak ji výběr data posílá.
 */
internal fun closeDaySelectable(utcTimeMillis: Long, now: Long, maxOpenPeriodSeconds: Int, zone: TimeZone): Boolean =
    futureDaySelectable(utcTimeMillis, now, maxOpenPeriodSeconds.toLong(), zone)

/**
 * Adresa odkazu u odpovědi, nebo `null`, když to adresa není. Bez schématu dostane `https://`
 * (tak se odkazy píšou); jiné schéma než http(s) se nebere — `tg://` ani `javascript:` do
 * ankety nepatří. Mezera uvnitř znamená, že jde o text, ne o adresu.
 */
internal fun pollLinkOf(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val schemeEnd = trimmed.indexOf("://")
    val url = if (schemeEnd < 0) "https://$trimmed" else trimmed
    val scheme = url.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    // Doména s tečkou a něčím před i za ní; „localhost" ani holé slovo adresou pro anketu nejsou.
    if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
    return url
}

/**
 * Nabízené doby trvání ankety v sekundách, jen do stropu serveru ([maxOpenPeriodSeconds]).
 * Bez stropu (`0`) se nenabízí žádná — volba doby se pak nekreslí.
 */
internal fun pollDurations(maxOpenPeriodSeconds: Int): List<Int> =
    POLL_DURATION_PRESETS.filter { it <= maxOpenPeriodSeconds }

private val POLL_DURATION_PRESETS = listOf(
    5 * 60,
    60 * 60,
    24 * 60 * 60,
    3 * 24 * 60 * 60,
    7 * 24 * 60 * 60,
)

/** Stav odesílání formuláře; text polí je Compose stav přímo ve ViewModelu. */
data class PollComposerStatus(
    /** V kanálu je anketa vždycky anonymní — přepínač se pak nekreslí. */
    val isChannel: Boolean = false,
    /** V Uložených zprávách nemá „bez zvuku" komu ušetřit zvuk. */
    val isSavedMessages: Boolean = false,
    val maxAnswers: Int = DEFAULT_MAX_ANSWERS,
    /** Nabízené doby trvání; prázdné = volba doby se nekreslí. */
    val durations: List<Int> = emptyList(),
    /** Strop doby od serveru — i pro vlastní čas uzavření; `0` = konec se nastavit nedá. */
    val maxOpenPeriodSeconds: Int = 0,
    val maxDescription: Int = DEFAULT_POLL_DESCRIPTION_MAX,
    /** Kolik zemí smí anketa v kanálu omezit; `0` = volba se nenabízí. */
    val maxCountries: Int = 0,
    /** Země pro výběr; načtou se až při prvním otevření výběru. */
    val countries: List<Country> = emptyList(),
    /** Citovaná zpráva, když se anketou odpovídá; `null`, dokud se nenačte, nebo bez odpovědi. */
    val replyTo: ChatMessage? = null,
    /** Název chatu — citace ho ukáže v soukromém chatu, kde TDLib jméno odesílatele nevyplňuje. */
    val chatTitle: String? = null,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: UiText? = null,
)

/**
 * Formulář nové ankety.
 *
 * **Text polí je Compose stav, ne `StateFlow`** — hodnota vrácená o dispatch později by
 * poskakovala kurzorem (viz „Úprava profilu" v `CLAUDE.md`). Ve ViewModelu, ne v obrazovce,
 * aby rozepsaná anketa přežila otočení.
 */
class PollComposerViewModel(
    val chatId: Long,
    /** Vynucený druh od tlačítka bota; přepínač kvízu se pak nekreslí. */
    val mode: PollComposerMode,
    /**
     * Odpověď rozepsaná v konverzaci, když se formulář otevřel; anketa pak odejde jako odpověď.
     * Konverzace ji po odeslání ankety zahodí sama (viz `pollConsumesReply`).
     */
    replyToMessageId: Long?,
    private val replyQuote: ReplyQuote?,
    private val repository: ConversationRepository,
    /** Jen kvůli seznamu zemí (`getCountries`) — tentýž, ze kterého bere přihlášení. */
    private val session: TelegramSession,
    private val placeRepository: PlaceRepository,
) : ViewModel() {

    /** Odpověď, se kterou anketa odejde; `null` = bez odpovědi (nebo ji uživatel zahodil). */
    var replyToMessageId by mutableStateOf(replyToMessageId)
        private set

    var question by mutableStateOf("")
    val answers = mutableStateListOf("", "")
    var anonymous by mutableStateOf(true)
    var multipleAnswers by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var quiz by mutableStateOf(mode == PollComposerMode.QuizOnly)

    /** Správné odpovědi kvízu jako indexy POLÍ v [answers]. */
    var correct by mutableStateOf(emptySet<Int>())
    var explanation by mutableStateOf("")

    var description by mutableStateOf("")

    /** Za kolik sekund se anketa uzavře; `0` = bez omezení (nebo vlastní čas). */
    var openPeriod by mutableStateOf(0)
        private set

    /** Vlastní čas uzavření (unixový); `0` = podle [openPeriod]. */
    var closeDate by mutableStateOf(0L)
        private set
    var hideResults by mutableStateOf(false)

    /**
     * Teď, jak ho vidí formulář. Obnovuje ho obrazovka, dokud je nastavený vlastní čas — čas,
     * který mezitím prošel, musí Odeslat zašednout, ne skončit chybou od serveru.
     */
    var now by mutableStateOf(currentEpochSeconds())

    var membersOnly by mutableStateOf(false)
    var countryCodes by mutableStateOf(emptyList<String>())
        private set

    /** Příloha otázky. */
    var questionMedia by mutableStateOf<PollDraftMedia?>(null)
        private set

    /** Přílohy odpovědí podle indexu POLE v [answers]. */
    var answerMedia by mutableStateOf(emptyMap<Int, PollDraftMedia>())
        private set

    /** Hledání místa k otázce nebo odpovědi (sheet „Místo"). */
    val places = PlaceSearcher(viewModelScope, placeRepository, chatId)

    /**
     * Ke kterému poli se vybírá příloha: [POLL_QUESTION_MEDIA], nebo index odpovědi. Výběr je
     * systémová obrazovka a výsledek přijde až po návratu, proto si pole pamatuje ViewModel.
     */
    private var mediaTarget = POLL_QUESTION_MEDIA

    private val _status = MutableStateFlow(PollComposerStatus())
    val status: StateFlow<PollComposerStatus> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            val info = repository.chatInfo(chatId).getOrNull()
            val limits = repository.pollLimits()
            _status.update {
                it.copy(
                    isChannel = info?.isChannel == true,
                    isSavedMessages = info?.isSavedMessages == true,
                    chatTitle = info?.title,
                    maxAnswers = limits.maxAnswers,
                    durations = pollDurations(limits.maxOpenPeriodSeconds),
                    maxOpenPeriodSeconds = limits.maxOpenPeriodSeconds,
                    maxDescription = limits.maxDescription,
                    maxCountries = limits.maxCountries,
                )
            }
        }
        replyToMessageId?.let { id ->
            viewModelScope.launch {
                // Citovaná zpráva jen pro náhled nad otázkou; když ji načíst nejde, anketa
                // odejde jako odpověď i tak — server zprávu zná.
                repository.message(chatId, id).onSuccess { message -> _status.update { it.copy(replyTo = message) } }
            }
        }
    }

    val draft: PollDraft?
        get() = pollDraftOf(
            question = question,
            answers = answers,
            anonymous = anonymous || _status.value.isChannel,
            multipleAnswers = multipleAnswers,
            shuffle = shuffle,
            quiz = quiz,
            correct = correct,
            explanation = explanation,
            maxAnswers = _status.value.maxAnswers,
            openPeriodSeconds = openPeriod,
            hideResultsUntilClose = hideResults,
            description = description,
            maxDescription = _status.value.maxDescription,
            closeDate = closeDate,
            now = now,
            maxOpenPeriodSeconds = _status.value.maxOpenPeriodSeconds,
            // Omezení hlasujících jsou jen pro kanál; jinde se neposílají, ani kdyby zůstala
            // nastavená.
            media = questionMedia,
            answerMedia = answerMedia,
            membersOnly = membersOnly && _status.value.isChannel,
            countryCodes = countryCodes.takeIf { _status.value.isChannel }.orEmpty(),
            maxCountries = _status.value.maxCountries,
        )

    /** Konec ankety podle doby; vlastní čas se tím zahodí. */
    fun chooseOpenPeriod(seconds: Int) {
        openPeriod = seconds
        closeDate = 0
        if (seconds == 0) hideResults = false
    }

    /** Konec ankety v konkrétní čas; doba se tím zahodí. */
    fun chooseCloseDate(epochSeconds: Long) {
        closeDate = epochSeconds
        openPeriod = 0
        now = currentEpochSeconds()
    }

    /** Vlastní čas uzavření, který formulář neodešle — prošel, nebo je za stropem serveru. */
    val closeDateInvalid: Boolean
        get() = closeDate > 0 && !closeDateValid(closeDate, now, _status.value.maxOpenPeriodSeconds)

    /** Příští vybraná příloha patří poli [target] (otázka, nebo index odpovědi). */
    fun pickMediaFor(target: Int) {
        mediaTarget = target
    }

    /** Příloha z výběru; bere se první fotka nebo video, výběr jich vydá víc naráz. */
    fun attachMedia(picked: List<OutgoingAttachment>) {
        val media = picked.firstOrNull { it is OutgoingAttachment.Photo || it is OutgoingAttachment.Video } ?: return
        setMedia(PollDraftMedia.File(media))
    }

    /** Příloha z kteréhokoli výběru (GIF, samolepka, odkaz, místo) pro pole z [pickMediaFor]. */
    fun setMedia(media: PollDraftMedia) {
        val target = mediaTarget
        when {
            target == POLL_QUESTION_MEDIA -> if (media.allowedForQuestion) questionMedia = media
            // Pole mohlo mezitím zmizet nebo se vyprázdnit — příloha bez textu by neodešla.
            answers.getOrNull(target)?.isNotBlank() == true -> answerMedia = answerMedia + (target to media)
        }
    }

    fun removeQuestionMedia() {
        questionMedia = null
    }

    fun removeAnswerMedia(index: Int) {
        answerMedia = answerMedia - index
    }

    /** Soubor se nepodařilo připravit (výběr ho nezkopíroval, video nešlo přečíst), čas už minul. */
    fun reportError(message: UiText) {
        _status.update { it.copy(error = message) }
    }

    /** Přidá nebo odebere zemi; nad strop serveru nic nepřidá. */
    fun toggleCountry(code: String) {
        countryCodes = when {
            code in countryCodes -> countryCodes - code
            countryCodes.size >= _status.value.maxCountries -> countryCodes
            else -> countryCodes + code
        }
    }

    /** Seznam zemí pro výběr — jednou, až ho uživatel poprvé otevře. */
    fun loadCountries() {
        if (_status.value.countries.isNotEmpty()) return
        viewModelScope.launch {
            session.countries()
                .onSuccess { list -> _status.update { it.copy(countries = list) } }
                .onFailure { logger.w(it) { "Seznam zemí pro anketu se nepodařilo načíst." } }
        }
    }

    fun setAnswer(index: Int, text: String) {
        if (index !in answers.indices) return
        answers[index] = text.take(POLL_ANSWER_MAX + ANSWER_OVERFLOW_ALLOWANCE)
        // Vymazaná odpověď nemůže být správná — v kvízu by jinak zůstala označená prázdná.
        // Ani nést přílohu: pole bez textu se neposílá a příloha by se ztratila potichu.
        if (text.isBlank()) {
            correct = correct - index
            answerMedia = answerMedia - index
        }
        normalize()
    }

    fun removeAnswer(index: Int) {
        if (index !in answers.indices) return
        answers.removeAt(index)
        correct = correctAfterRemoval(correct, index)
        answerMedia = answerMedia.afterRemoval(index)
        normalize()
    }

    /** Označí správnou odpověď. Bez víc odpovědí je správná jen jedna, jako u rádia. */
    fun toggleCorrect(index: Int) {
        if (answers.getOrNull(index).isNullOrBlank()) return
        correct = when {
            index in correct -> correct - index
            multipleAnswers -> correct + index
            else -> setOf(index)
        }
    }

    fun changeMultipleAnswers(on: Boolean) {
        multipleAnswers = on
        // Z víc správných odpovědí se při vypnutí nechá ta první — rádio jich víc neukáže.
        if (!on && correct.size > 1) correct = setOf(correct.min())
    }

    /** Citovaný úryvek, když odpověď necituje celou zprávu. */
    val replyQuoteText: String? get() = replyQuote?.text

    /** Anketa odejde bez odpovědi; rozepsaná odpověď v konverzaci zůstane, jak byla. */
    fun dropReply() {
        replyToMessageId = null
        _status.update { it.copy(replyTo = null) }
    }

    /** [options] z nabídky nad Odeslat (bez zvuku, naplánovat); ťuknutí posílá výchozí. */
    fun send(options: SendOptions = SendOptions()) {
        val draft = draft ?: return
        if (_status.value.sending) return
        _status.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val replyTo = replyToMessageId
            repository.sendPoll(chatId, draft, replyToMessageId = replyTo, quote = replyQuote.takeIf { replyTo != null }, options = options)
                .onSuccess { _status.update { it.copy(sending = false, sent = true) } }
                .onFailure { failure ->
                    logger.w(failure) { "Anketu se nepodařilo odeslat." }
                    _status.update { it.copy(sending = false, error = failure.userMessage(Res.string.poll_compose_send_failed)) }
                }
        }
    }

    private fun normalize() {
        val normalized = normalizedAnswers(answers, _status.value.maxAnswers)
        if (normalized != answers.toList()) {
            answers.clear()
            answers.addAll(normalized)
        }
    }

    private companion object {
        val logger = Logger.withTag("PollComposer")
    }
}

/**
 * Obrazovka nové ankety: otázka, blok odpovědí, blok voleb. Odeslat je v liště (je to hlavní
 * akce obrazovky a s klávesnicí nahoře musí zůstat vidět); po odeslání obrazovka odejde a pod
 * ní je konverzace s novou anketou.
 *
 * Tentýž jazyk jako Nastavení a profil: bloky zaoblených řádků (`groupItemShape`), nadpis nad
 * blokem, přepínače, které nese celý řádek.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PollComposerScreen(
    viewModel: PollComposerViewModel,
    onClose: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    if (status.sent) {
        // Jednorázově: obrazovka odchází, stav s ní.
        LaunchedEffect(Unit) { onClose() }
    }
    val canSend = viewModel.draft != null && !status.sending
    val mediaPicker = rememberAttachmentPicker(onPicked = viewModel::attachMedia, onError = viewModel::reportError)
    /** Otevřený výběr přílohy (GIF, samolepka, odkaz, místo); fotku a video vybírá systém. */
    var choosing by remember { mutableStateOf<PollMediaKind?>(null) }
    val chooseMedia: (Int, PollMediaKind) -> Unit = { target, kind ->
        viewModel.pickMediaFor(target)
        if (kind == PollMediaKind.PhotoOrVideo) mediaPicker.pickMedia() else choosing = kind
    }
    var pickingCloseDay by remember { mutableStateOf(false) }
    var pickedCloseDay by remember { mutableStateOf<Long?>(null) }
    var pickingCountries by remember { mutableStateOf(false) }
    var sendMenuOpen by remember { mutableStateOf(false) }
    // Naplánování ankety: napřed den, pak čas — tytéž dialogy jako u zprávy.
    var pickingScheduleDay by remember { mutableStateOf(false) }
    var pickedScheduleDay by remember { mutableStateOf<Long?>(null) }

    // Vlastní čas uzavření stárne i bez jediného úhozu: čas, který mezitím prošel, musí
    // Odeslat zašednout a obarvit, dřív než by ho odmítl server.
    LaunchedEffect(viewModel.closeDate) {
        while (viewModel.closeDate > 0) {
            viewModel.now = Clock.System.now().epochSeconds
            delay(CLOSE_DATE_TICK_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.quiz) Res.string.poll_compose_title_quiz else Res.string.poll_compose_title))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                    }
                },
                actions = {
                    if (status.sending) {
                        LoadingIndicator(Modifier.padding(end = 16.dp).size(32.dp))
                    } else {
                        Box {
                            Button(
                                onClick = {
                                    haptics.confirm()
                                    viewModel.send()
                                },
                                enabled = canSend,
                                // Podržení nabídne odeslat bez zvuku a naplánovat, jako u zprávy.
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .longPressInstead(enabled = canSend) { sendMenuOpen = true },
                            ) { Text(stringResource(Res.string.poll_compose_send)) }
                            ExpressiveMenu(
                                expanded = sendMenuOpen,
                                onDismiss = { sendMenuOpen = false },
                                groups = listOf(
                                    listOfNotNull(
                                        MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                                            sendMenuOpen = false
                                            haptics.confirm()
                                            viewModel.send(SendOptions(silent = true))
                                        }.takeIf { !status.isSavedMessages },
                                        MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                                            sendMenuOpen = false
                                            pickingScheduleDay = true
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            status.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = error.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }

            status.replyTo?.takeIf { viewModel.replyToMessageId != null }?.let { quoted ->
                item(key = "reply") {
                    ReplyHeader(
                        message = quoted,
                        partnerName = status.chatTitle,
                        quoteText = viewModel.replyQuoteText,
                        onDrop = viewModel::dropReply,
                    )
                }
            }

            // Otázka a popis jsou jeden blok: popis patří k otázce, ne k odpovědím.
            item(key = "question") {
                BlockField(
                    value = viewModel.question,
                    onValueChange = { viewModel.question = it.take(POLL_QUESTION_MAX + ANSWER_OVERFLOW_ALLOWANCE) },
                    label = stringResource(Res.string.poll_compose_question),
                    max = POLL_QUESTION_MAX,
                    shape = groupItemShape(0, 2),
                    singleLine = false,
                    trailing = {
                        MediaSlot(
                            media = viewModel.questionMedia,
                            kinds = PollMediaKind.forQuestion,
                            onChoose = { kind -> chooseMedia(POLL_QUESTION_MEDIA, kind) },
                            onRemove = viewModel::removeQuestionMedia,
                        )
                    },
                )
            }
            item(key = "description") {
                BlockField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it.take(status.maxDescription + ANSWER_OVERFLOW_ALLOWANCE) },
                    label = stringResource(Res.string.poll_compose_description),
                    max = status.maxDescription,
                    shape = groupItemShape(1, 2),
                    singleLine = false,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item(key = "answers-title") {
                SectionTitle(stringResource(Res.string.poll_compose_answers))
            }
            itemsIndexed(viewModel.answers, key = { index, _ -> "answer-$index" }) { index, answer ->
                AnswerField(
                    index = index,
                    value = answer,
                    count = viewModel.answers.size,
                    quiz = viewModel.quiz,
                    multiple = viewModel.multipleAnswers,
                    correct = index in viewModel.correct,
                    removable = viewModel.answers.size > MIN_POLL_ANSWERS && answer.isNotBlank(),
                    media = viewModel.answerMedia[index],
                    onChooseMedia = { kind -> chooseMedia(index, kind) },
                    onRemoveMedia = { viewModel.removeAnswerMedia(index) },
                    onValueChange = { viewModel.setAnswer(index, it) },
                    onToggleCorrect = {
                        haptics.toggle(index !in viewModel.correct)
                        viewModel.toggleCorrect(index)
                    },
                    onRemove = { viewModel.removeAnswer(index) },
                )
            }
            item(key = "answers-hint") {
                val filled = viewModel.answers.count { it.isNotBlank() }
                val hint = when {
                    viewModel.quiz && viewModel.correct.isEmpty() -> stringResource(Res.string.poll_compose_quiz_hint)
                    filled >= status.maxAnswers -> stringResource(Res.string.poll_compose_too_many, status.maxAnswers)
                    else -> (status.maxAnswers - filled).let { left ->
                        pluralStringResource(Res.plurals.poll_compose_add_answer, left, left)
                    }
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (viewModel.quiz && viewModel.correct.isEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            item(key = "settings-title") {
                SectionTitle(stringResource(Res.string.poll_compose_settings))
            }
            val settings = buildList {
                if (!status.isChannel) add(SettingRow.Anonymous)
                add(SettingRow.Multiple)
                if (viewModel.mode == PollComposerMode.Any) add(SettingRow.Quiz)
                add(SettingRow.Shuffle)
                if (status.durations.isNotEmpty()) add(SettingRow.Duration)
                // Výsledky až po skončení mají smysl jen s koncem ankety.
                if (viewModel.openPeriod > 0 || viewModel.closeDate > 0) add(SettingRow.HideResults)
            }
            itemsIndexed(settings, key = { _, row -> "setting-$row" }) { index, row ->
                val shape = groupItemShape(index, settings.size)
                when (row) {
                    SettingRow.Anonymous -> SwitchRow(stringResource(Res.string.poll_compose_anonymous), viewModel.anonymous, shape) {
                        haptics.toggle(it)
                        viewModel.anonymous = it
                    }
                    SettingRow.Multiple -> SwitchRow(stringResource(Res.string.poll_compose_multiple), viewModel.multipleAnswers, shape) {
                        haptics.toggle(it)
                        viewModel.changeMultipleAnswers(it)
                    }
                    SettingRow.Quiz -> SwitchRow(stringResource(Res.string.poll_compose_quiz), viewModel.quiz, shape) {
                        haptics.toggle(it)
                        viewModel.quiz = it
                    }
                    SettingRow.Shuffle -> SwitchRow(stringResource(Res.string.poll_compose_shuffle), viewModel.shuffle, shape) {
                        haptics.toggle(it)
                        viewModel.shuffle = it
                    }
                    SettingRow.Duration -> DurationRow(
                        selected = viewModel.openPeriod.takeIf { viewModel.closeDate == 0L },
                        value = if (viewModel.closeDate > 0) viewModel.closeDate.asUpcomingTime() else durationLabel(viewModel.openPeriod),
                        valueIsError = viewModel.closeDateInvalid,
                        options = status.durations,
                        shape = shape,
                        onSelect = viewModel::chooseOpenPeriod,
                        onCustom = { pickingCloseDay = true },
                    )
                    SettingRow.HideResults -> SwitchRow(stringResource(Res.string.poll_compose_hide_results), viewModel.hideResults, shape) {
                        haptics.toggle(it)
                        viewModel.hideResults = it
                    }
                }
            }
            if (viewModel.closeDateInvalid) {
                item(key = "close-invalid") {
                    Text(
                        text = stringResource(
                            Res.string.poll_compose_close_invalid,
                            (viewModel.now + status.maxOpenPeriodSeconds).asUpcomingTime(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (status.isChannel) {
                item(key = "channel-anonymous") {
                    Text(
                        text = stringResource(Res.string.poll_compose_anonymous_channel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Kdo smí hlasovat: obojí jen v kanálu („for channel chats only").
            if (status.isChannel) {
                val voterRows = buildList {
                    add(VoterRow.MembersOnly)
                    if (status.maxCountries > 0) add(VoterRow.Countries)
                }
                item(key = "voters-title") { SectionTitle(stringResource(Res.string.poll_compose_voters)) }
                itemsIndexed(voterRows, key = { _, row -> "voters-$row" }) { index, row ->
                    val shape = groupItemShape(index, voterRows.size)
                    when (row) {
                        VoterRow.MembersOnly -> SwitchRow(stringResource(Res.string.poll_compose_members_only), viewModel.membersOnly, shape) {
                            haptics.toggle(it)
                            viewModel.membersOnly = it
                        }
                        VoterRow.Countries -> ValueRow(
                            label = stringResource(Res.string.poll_compose_countries),
                            value = countriesLabel(viewModel.countryCodes),
                            shape = shape,
                            onClick = {
                                viewModel.loadCountries()
                                pickingCountries = true
                            },
                        )
                    }
                }
            }

            item(key = "explanation") {
                AnimatedVisibility(visible = viewModel.quiz) {
                    Column(Modifier.animateContentSize()) {
                        SectionTitle(stringResource(Res.string.poll_compose_explanation))
                        BlockField(
                            value = viewModel.explanation,
                            onValueChange = { viewModel.explanation = it.take(POLL_EXPLANATION_MAX + ANSWER_OVERFLOW_ALLOWANCE) },
                            label = stringResource(Res.string.poll_compose_explanation_hint),
                            max = POLL_EXPLANATION_MAX,
                            shape = groupItemShape(0, 1),
                            singleLine = false,
                        )
                    }
                }
            }
        }
    }

    if (pickingCloseDay) {
        FutureDayDialog(
            now = viewModel.now,
            maxAheadSeconds = status.maxOpenPeriodSeconds.toLong(),
            onPick = { day ->
                pickingCloseDay = false
                pickedCloseDay = day
            },
            onDismiss = { pickingCloseDay = false },
        )
    }
    pickedCloseDay?.let { day ->
        TimeOfDayDialog(
            title = stringResource(Res.string.poll_compose_close_time),
            // Nabídne se čas, který zbyl z dosavadní volby — jinak za hodinu od teď.
            initial = viewModel.closeDate.takeIf { it > 0 } ?: (Clock.System.now().epochSeconds + HOUR),
            onPick = { hour, minute ->
                pickedCloseDay = null
                viewModel.chooseCloseDate(closeDateOf(day, hour, minute, TimeZone.currentSystemDefault()))
            },
            onDismiss = { pickedCloseDay = null },
        )
    }
    if (pickingScheduleDay) {
        FutureDayDialog(
            now = Clock.System.now().epochSeconds,
            maxAheadSeconds = MAX_SCHEDULE_AHEAD_SECONDS,
            onPick = { day ->
                pickingScheduleDay = false
                pickedScheduleDay = day
            },
            onDismiss = { pickingScheduleDay = false },
        )
    }
    pickedScheduleDay?.let { day ->
        TimeOfDayDialog(
            title = stringResource(Res.string.conversation_schedule_time),
            initial = Clock.System.now().epochSeconds + HOUR,
            onPick = { hour, minute ->
                pickedScheduleDay = null
                val at = closeDateOf(day, hour, minute, TimeZone.currentSystemDefault())
                if (!scheduleValid(at, Clock.System.now().epochSeconds)) {
                    haptics.reject()
                    viewModel.reportError(UiText(Res.string.conversation_schedule_past))
                    return@TimeOfDayDialog
                }
                haptics.confirm()
                viewModel.send(SendOptions(schedule = MessageSchedule.At(at)))
            },
            onDismiss = { pickedScheduleDay = null },
        )
    }
    when (choosing) {
        null, PollMediaKind.PhotoOrVideo -> Unit
        // Tentýž výběr jako v konverzaci, jen s jedinou částí: GIF a samolepka se tu nevkládají
        // do zprávy, ale k otázce nebo odpovědi.
        PollMediaKind.Gif, PollMediaKind.Sticker -> {
            val mode = if (choosing == PollMediaKind.Gif) ExpressionMode.Gifs else ExpressionMode.Stickers
            StickerPickerSheet(
                mode = mode,
                onModeChange = {},
                stickersAvailable = true,
                draft = TextFieldValue(),
                onDraftEdit = {},
                onPick = { sticker ->
                    choosing = null
                    viewModel.setMedia(PollDraftMedia.Sticker(sticker))
                },
                chatId = viewModel.chatId,
                onPickGif = { gif ->
                    choosing = null
                    viewModel.setMedia(PollDraftMedia.Gif(gif.animation))
                },
                onDismiss = { choosing = null },
                modes = listOf(mode),
            )
        }
        PollMediaKind.Link -> PollLinkDialog(
            onConfirm = { url ->
                choosing = null
                viewModel.setMedia(PollDraftMedia.Link(url))
            },
            onDismiss = { choosing = null },
        )
        PollMediaKind.Place -> {
            PlacePickerHost(
                searcher = viewModel.places,
                title = stringResource(Res.string.poll_place_title),
                onPick = { place ->
                    choosing = null
                    viewModel.setMedia(PollDraftMedia.Location(place))
                },
                onDismiss = { choosing = null },
            )
        }
    }

    if (pickingCountries) {
        CountryPickerSheet(
            countries = status.countries,
            selectedCodes = viewModel.countryCodes.toSet(),
            onPick = { viewModel.toggleCountry(it.code) },
            onDismiss = { pickingCountries = false },
            title = stringResource(Res.string.poll_compose_countries_title),
            supportingText = pluralStringResource(Res.plurals.poll_compose_countries_hint, status.maxCountries, status.maxCountries),
            multiSelect = true,
            canPick = { viewModel.countryCodes.size < status.maxCountries },
        )
    }
}

private enum class SettingRow { Anonymous, Multiple, Quiz, Shuffle, Duration, HideResults }

private enum class VoterRow { MembersOnly, Countries }

/** „Všechny", jedna země jménem s vlajkou, víc zemí počtem — do řádku se víc jmen nevejde. */
@Composable
private fun countriesLabel(codes: List<String>): String = when (codes.size) {
    0 -> stringResource(Res.string.poll_compose_countries_all)
    1 -> codes.single().let { code -> listOfNotNull(flagEmoji(code), localizedCountryName(code) ?: code).joinToString(" ") }
    else -> pluralStringResource(Res.plurals.poll_compose_countries_count, codes.size, codes.size)
}

/** Řádek s hodnotou vpravo; ťuknutí otevře výběr. */
@Composable
private fun ValueRow(label: String, value: String, shape: Shape, onClick: () -> Unit, valueIsError: Boolean = false) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (valueIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = GROUP_SPACING + 4.dp, bottom = 6.dp),
    )
}

/**
 * Pole v bloku: tónovaná plocha řádku, bez podtržení — podtržení `TextField`u je jazyk, kterého
 * se aplikace vzdala (viz přihlášení a „Jeden prvek pro hledání"). Počítadlo se ukáže až
 * v posledních dvaceti znacích a přes strop zčervená; text se nad strop kousek pustí, aby šlo
 * vložit delší text a zkrátit ho, a odeslání je do té doby zašedlé.
 */
@Composable
private fun BlockField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    shape: Shape,
    singleLine: Boolean,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            placeholder = { Text(label, style = style) },
            singleLine = singleLine,
            leadingIcon = leading,
            trailingIcon = trailing,
            supportingText = counterText(value.length, max)?.let { { it() } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Počítadlo znaků — až v posledních [COUNTER_FROM] znacích, přes strop chybovou barvou. */
private fun counterText(length: Int, max: Int): (@Composable () -> Unit)? {
    if (length < max - COUNTER_FROM) return null
    return {
        Text(
            text = stringResource(Res.string.poll_compose_counter, length, max),
            color = if (length > max) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Pole odpovědi. V kvízu vlevo kolečko (jedna správná) nebo zaškrtávátko (víc správných), kterým
 * se označuje správná odpověď; vpravo křížek na odebrání.
 */
@Composable
private fun AnswerField(
    index: Int,
    value: String,
    count: Int,
    quiz: Boolean,
    multiple: Boolean,
    correct: Boolean,
    removable: Boolean,
    media: PollDraftMedia?,
    onChooseMedia: (PollMediaKind) -> Unit,
    onRemoveMedia: () -> Unit,
    onValueChange: (String) -> Unit,
    onToggleCorrect: () -> Unit,
    onRemove: () -> Unit,
) {
    val markLabel = stringResource(Res.string.poll_compose_mark_correct)
    BlockField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(Res.string.poll_compose_answer, index + 1),
        max = POLL_ANSWER_MAX,
        shape = groupItemShape(index, count),
        singleLine = true,
        leading = if (quiz) {
            {
                IconButton(onClick = onToggleCorrect, enabled = value.isNotBlank()) {
                    Icon(
                        painter = painterResource(
                            when {
                                correct && multiple -> Res.drawable.check_box_filled_24dp
                                correct -> Res.drawable.check_circle_filled_24dp
                                multiple -> Res.drawable.check_box_outline_blank_24dp
                                else -> Res.drawable.radio_button_unchecked_24dp
                            },
                        ),
                        contentDescription = markLabel,
                        tint = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        // Příloha jen u odpovědi s textem: prázdné pole se neposílá a příloha by se s ním ztratila.
        trailing = if (removable || value.isNotBlank()) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotBlank()) {
                        MediaSlot(media = media, kinds = PollMediaKind.entries, onChoose = onChooseMedia, onRemove = onRemoveMedia)
                    }
                    if (removable) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                painterResource(Res.drawable.close_24dp),
                                contentDescription = stringResource(Res.string.poll_compose_remove_answer),
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

/** Druh přílohy v nabídce u pole. */
internal enum class PollMediaKind {
    PhotoOrVideo, Gif, Sticker, Link, Place;

    companion object {
        /** Co otázka smí (viz `PollDraftMedia.allowedForQuestion`) — samolepku a odkaz ne. */
        val forQuestion = listOf(PhotoOrVideo, Gif, Place)
    }
}

/**
 * Příloha pole: bez ní ikona, která otevře nabídku druhů, s ní náhled s křížkem v rohu. Ťuknutí
 * na náhled přílohu odebere — jiná akce nad ním není a vybrat jinou jde po odebrání.
 */
@Composable
private fun MediaSlot(
    media: PollDraftMedia?,
    kinds: List<PollMediaKind>,
    onChoose: (PollMediaKind) -> Unit,
    onRemove: () -> Unit,
) {
    if (media == null) {
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) {
                Icon(
                    painterResource(Res.drawable.add_photo_alternate_24dp),
                    contentDescription = stringResource(Res.string.poll_compose_add_media),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpressiveMenu(
                expanded = open,
                onDismiss = { open = false },
                groups = listOf(
                    kinds.map { kind ->
                        MenuAction(label = stringResource(kind.label()), icon = kind.icon()) { onChoose(kind) }
                    },
                ),
            )
        }
        return
    }
    val removeLabel = stringResource(Res.string.poll_compose_remove_media)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(MEDIA_SLOT)
            .clip(RoundedCornerShape(MEDIA_SLOT_CORNER))
            .background(if (media is PollDraftMedia.Sticker) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = removeLabel, onClick = onRemove)
            .semantics { contentDescription = removeLabel },
    ) {
        MediaSlotPicture(media)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = MEDIA_BADGE_SCRIM)),
        ) {
            Icon(painterResource(Res.drawable.close_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/** Obsah náhledu přílohy: obrázek, kde nějaký je, jinak ikona druhu. */
@Composable
private fun MediaSlotPicture(media: PollDraftMedia) {
    val icon: @Composable (DrawableResource) -> Unit = { drawable ->
        Icon(painterResource(drawable), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    when (media) {
        is PollDraftMedia.File -> {
            val image = when (val file = media.attachment) {
                is OutgoingAttachment.Photo -> file.path
                is OutgoingAttachment.Video -> file.thumbnail?.path
                is OutgoingAttachment.Document -> null
            }
            image?.let {
                AsyncImage(model = "file://$it", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        is PollDraftMedia.Gif -> media.animation.thumbnailFileId
            ?.let { RemoteImage(fileId = it, modifier = Modifier.fillMaxSize()) }
            ?: icon(Res.drawable.gif_box_24dp)
        is PollDraftMedia.Sticker -> (media.sticker.preview.thumbnailFileId ?: media.sticker.preview.fileId)
            ?.let {
                RemoteImage(fileId = it, contentScale = ContentScale.Fit, background = Color.Transparent, modifier = Modifier.fillMaxSize())
            }
            ?: icon(Res.drawable.sticker_24dp)
        is PollDraftMedia.Link -> icon(Res.drawable.link_24dp)
        is PollDraftMedia.Location -> StaticMap(media.place.latitude, media.place.longitude, pinSize = 18.dp, modifier = Modifier.fillMaxSize())
    }
}

private fun PollMediaKind.label(): StringResource = when (this) {
    PollMediaKind.PhotoOrVideo -> Res.string.poll_media_photo
    PollMediaKind.Gif -> Res.string.poll_media_gif
    PollMediaKind.Sticker -> Res.string.poll_media_sticker
    PollMediaKind.Link -> Res.string.poll_media_link
    PollMediaKind.Place -> Res.string.poll_media_place
}

private fun PollMediaKind.icon(): DrawableResource = when (this) {
    PollMediaKind.PhotoOrVideo -> Res.drawable.image_24dp
    PollMediaKind.Gif -> Res.drawable.gif_box_24dp
    PollMediaKind.Sticker -> Res.drawable.sticker_24dp
    PollMediaKind.Link -> Res.drawable.link_24dp
    PollMediaKind.Place -> Res.drawable.location_on_24dp
}

/**
 * Odkaz u odpovědi. Přidat jde jen adresa, kterou [pollLinkOf] uzná — bez schématu dostane
 * `https://`, jako když se odkaz píše do zprávy.
 */
@Composable
private fun PollLinkDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val url = pollLinkOf(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.poll_link_title)) },
        text = {
            // Jako pole v ostatních dialozích (heslo při převodu vlastnictví, štítek člena).
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(Res.string.poll_link_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = url != null, onClick = { url?.let(onConfirm) }) {
                Text(stringResource(Res.string.poll_link_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** Přepínač, který nese celý řádek (viz „Obrazovka nastavení"). */
@Composable
private fun SwitchRow(label: String, checked: Boolean, shape: Shape, onChange: (Boolean) -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** Kdyby server strop nevydal. */
private const val DEFAULT_MAX_ANSWERS = 10

private fun currentEpochSeconds(): Long = Clock.System.now().epochSeconds

/** O kolik se text smí dostat přes strop, než ho pole utne — aby šlo vložit a zkrátit. */
private const val ANSWER_OVERFLOW_ALLOWANCE = 50

/** Počítadlo se ukáže, až zbývá tolik znaků. */
private const val COUNTER_FROM = 20

private val MEDIA_SLOT = 40.dp
private val MEDIA_SLOT_CORNER = 8.dp
private const val MEDIA_BADGE_SCRIM = 0.55f

/** Jak často se přepočítává, jestli vlastní čas uzavření ještě platí. */
private const val CLOSE_DATE_TICK_MS = 15_000L

/**
 * Citovaná zpráva nad otázkou, když se anketou odpovídá — tentýž prvek jako nad vstupním polem,
 * v terciární ploše (cizí obsah). Křížek odpověď zahodí; anketa pak odejde samostatně.
 */
@Composable
private fun ReplyHeader(message: ChatMessage, partnerName: String?, quoteText: String?, onDrop: () -> Unit) {
    Surface(
        shape = groupItemShape(0, 1),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = GROUP_SPACING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuotedMessage(
                message = message,
                partnerName = partnerName,
                accent = MaterialTheme.colorScheme.onTertiaryContainer,
                contained = false,
                quoteText = quoteText,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDrop) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.poll_compose_drop_reply))
            }
        }
    }
}

/**
 * Doba trvání: řádek s vybranou hodnotou, ťuknutí otevře nabídku (Expressive, jako výběr
 * období v hledání). Vybraná doba má v nabídce fajfku; poslední položka otevře výběr data
 * a času — [selected] je pak `null` a hodnota řádku je čas konce.
 */
@Composable
private fun DurationRow(
    selected: Int?,
    value: String,
    valueIsError: Boolean,
    options: List<Int>,
    shape: Shape,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val check: @Composable () -> Unit = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) }
    Box {
        ValueRow(
            // „Doba trvání: zítra 18:48" by nesedělo — s konkrétním časem jde o konec ankety.
            label = stringResource(if (selected == null) Res.string.poll_compose_close_time else Res.string.poll_compose_duration),
            value = value,
            valueIsError = valueIsError,
            shape = shape,
            onClick = { expanded = true },
        )
        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            groups = listOf(
                (listOf(0) + options).map { seconds ->
                    MenuAction(label = durationLabel(seconds), trailing = check.takeIf { seconds == selected }) { onSelect(seconds) }
                },
                // Vlastní čas je jiný druh volby než doba — vlastní skupina.
                listOf(MenuAction(label = stringResource(Res.string.poll_duration_custom), trailing = check.takeIf { selected == null }) { onCustom() }),
            ),
        )
    }
}

/** „Bez omezení", jinak délka v největší celé jednotce ([durationText]). */
@Composable
private fun durationLabel(seconds: Int): String =
    if (seconds <= 0) stringResource(Res.string.poll_duration_none) else durationText(seconds)

private const val HOUR = 60 * 60

/**
 * Co konverzace předá formuláři: vynucený druh ankety (tlačítko bota) a rozepsanou odpověď.
 * Odpověď se PŘEDÁVÁ, neodebírá: když uživatel formulář zavře, zůstane v konverzaci, jak byla.
 */
data class PollComposerRequest(
    val mode: PollComposerMode,
    val replyToMessageId: Long?,
    val quote: ReplyQuote?,
)

