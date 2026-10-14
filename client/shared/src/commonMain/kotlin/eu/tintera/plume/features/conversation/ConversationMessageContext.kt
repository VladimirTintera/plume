package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.core.calendar.CalendarEvent
import eu.tintera.plume.core.contacts.NewContact
import eu.tintera.plume.core.currency.RateTable
import eu.tintera.plume.core.location.mapsSearchUrl
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.wifi.WifiJoin
import eu.tintera.plume.features.conversation.items.ContextVerb
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_text_copied
import eu.tintera.plume.resources.message_context_flight_arrival_title
import eu.tintera.plume.resources.message_context_flight_title
import eu.tintera.plume.resources.message_context_no_calendar
import eu.tintera.plume.resources.message_context_no_contacts
import eu.tintera.plume.resources.message_context_wifi_failed
import eu.tintera.plume.resources.message_context_wifi_settings
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Akce u zprávy (`MessageContextSheet`): termín do kalendáře, let, zásilka, Wi-Fi, kontakt,
 * mapa, převod měny a jednotek, připomínka. K tomu posudky modelu v zařízení, které se čtou
 * u zpráv — termíny (ikonka akcí) a varování před podvodem.
 *
 * Část [ConversationViewModel] — ten na ni deleguje a drží její toky ve veřejném API.
 */
internal class ConversationMessageContext(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    private val openUrl: (String) -> Unit,
    private val copyToClipboard: suspend (String) -> Unit,
    /** Potvrdí zkopírování sám systém (`TextClipboard.confirmsCopy`)? Pak hláška od nás není. */
    private val clipboardConfirmsCopy: Boolean,
    private val scamWarnings: ScamWarnings?,
    private val messageContexts: MessageContexts?,
    /** Událost do editoru kalendáře (`CalendarEvents.add`); `false` = kalendář chybí. Bez něj se nenabízí. */
    private val addCalendarEvent: ((CalendarEvent) -> Boolean)?,
    /** Kontakt do editoru kontaktů (`ContactSaver.save`); `false` = aplikace kontaktů chybí. */
    private val saveNewContact: ((NewContact) -> Boolean)?,
    /** Připojení k Wi-Fi (`WifiJoiner.join`). */
    private val joinWifiNetwork: ((ssid: String, password: String) -> WifiJoin)?,
    /** Umí telefon nabídnout připojení k Wi-Fi ze zprávy? Na iOS ne (viz `WifiJoiner`). */
    val canJoinWifi: Boolean,
    /** Kurzovní lístek (`ExchangeRates.table`); `null` = převod měny bez kurzu. */
    private val loadRates: (suspend () -> RateTable?)?,
    /** Měna uživatele podle regionu telefonu; částky v ní se v akcích u zprávy nepřevádějí. */
    val homeCurrency: String?,
    /** Používá uživatel metrické jednotky? Převádějí se jen jednotky té druhé soustavy. */
    val metricUnits: Boolean,
    private val closeMenus: () -> Unit,
    /** Odkaz z akce (sledování zásilky) — jde touž cestou jako odkaz ve zprávě. */
    private val openLink: (url: String) -> Unit,
    /** „Připomenout" z akcí otevře týž sheet jako z nabídky nad zprávou. */
    private val requestRemind: (messageId: Long) -> Unit,
    private val logger: Logger,
) {
    /**
     * Zprávy tohohle chatu, u kterých se varuje před podvodem, podle id. Samostatný tok, ne
     * součást [ConversationState]: přibývá zřídka, nezávisle na zprávách, a čte se až v položce,
     * ne v celé obrazovce.
     */
    val scamVerdicts: StateFlow<Map<Long, ScamKind>> = (
        scamWarnings?.verdicts?.combine(settings.scamWarningsEnabled) { all, on ->
            if (on) all.filterKeys { it.first == chatId }.mapKeys { it.key.second } else emptyMap()
        } ?: flowOf(emptyMap())
        ).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Posudky termínů modelem pro zprávy tohohle chatu podle (zpráva, otisk textu), nebo `null`,
     * když se akce u zpráv nenabízejí (vypnuté v nastavení, chat s chráněným obsahem — z něj
     * nesmí nic ven, ani do kalendáře). Samostatný tok ze stejného důvodu jako [scamVerdicts].
     */
    val contextVerdicts: StateFlow<Map<Pair<Long, Int>, EventVerdict>?> = combine(
        messageContexts?.verdicts ?: flowOf(emptyMap()),
        settings.messageContextEnabled,
        state.map { it.info?.protectsContent == true }.distinctUntilChanged(),
    ) { all, on, protected ->
        if (!on || protected) {
            null
        } else {
            all.filterKeys { it.chatId == chatId }.mapKeys { (key, _) -> key.messageId to key.textHash }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _contextSheet = MutableStateFlow<ContextSheet?>(null)

    /** Otevřený sheet akcí u zprávy (`MessageContextSheet`); `null` = zavřený. */
    val contextSheet: StateFlow<ContextSheet?> = _contextSheet.asStateFlow()

    /** Ťuknutí na ikonku akcí u zprávy: sheet s tím, co se ve zprávě našlo. */
    fun openMessageContext(messageId: Long, findings: List<ContextFinding>) {
        if (findings.isEmpty()) return
        val rates = loadRates?.takeIf { findings.any { it is ContextFinding.Money } }
        _contextSheet.value = ContextSheet(messageId, findings, homeCurrency, if (rates != null) RatesState.Loading else null)
        rates ?: return
        scope.launch {
            val table = rates()
            _contextSheet.update { open ->
                // Sheet mezitím mohl zavřít nebo otevřít jiný; kurz patří jen tomu, kdo o něj žádal.
                if (open?.messageId != messageId) open else open.copy(rates = table?.let(RatesState::Loaded) ?: RatesState.Failed)
            }
        }
    }

    /**
     * Položka akce v nabídce nad zprávou: co nic neukazuje, se provede rovnou, převody a QR platba
     * otevřou sheet (tentýž jako ikonka).
     */
    fun runContextItem(messageId: Long, item: MessageMenuAction.ContextItem) {
        val findings = state.value.messageMenu?.takeIf { it.messageId == messageId }?.contextFindings.orEmpty()
        val finding = findings.getOrNull(item.index)
        closeMenus()
        when (item.verb) {
            ContextVerb.Calendar -> (finding as? ContextFinding.Event)?.let(::addToCalendar)
            ContextVerb.FlightCalendar, ContextVerb.ArrivalCalendar ->
                (finding as? ContextFinding.Flight)?.let { addFlightToCalendar(messageId, it) }
            ContextVerb.FlightStatus -> (finding as? ContextFinding.Flight)?.let { trackParcel(it.trackingUrl()) }
            ContextVerb.Maps -> (finding as? ContextFinding.Address)?.let { openMaps(it.text) }
            ContextVerb.SaveContact -> (finding as? ContextFinding.Contact)?.let(::saveContact)
            ContextVerb.JoinWifi -> (finding as? ContextFinding.Wifi)?.let(::joinWifi)
            ContextVerb.CopyCode -> (finding as? ContextFinding.Code)?.let { copyFromContext(it.value) }
            ContextVerb.CopyBooking -> (finding as? ContextFinding.Booking)?.let { copyFromContext(it.value) }
            ContextVerb.TrackParcel -> (finding as? ContextFinding.Parcel)?.let { trackParcel(it.trackingUrl(platformLanguageCode)) }
            ContextVerb.Payment, ContextVerb.Money, ContextVerb.Units, ContextVerb.Wifi -> openMessageContext(messageId, findings)
        }
    }

    /** „Akce ze zprávy…" v nabídce nad zprávou: tentýž sheet jako ikonka. */
    fun openContextFromMenu(messageId: Long) {
        val findings = state.value.messageMenu?.takeIf { it.messageId == messageId }?.contextFindings.orEmpty()
        closeMenus()
        openMessageContext(messageId, findings)
    }

    /**
     * Akce z textu zprávy pro nabídku nad ní — totéž, co ukazuje ikonka (`messageContext`
     * v položce), jen bez ohledu na vypínač ikonky. Termín, který model posoudil, se bere
     * podle jeho posudku; nejistý bez posudku se nenabízí.
     */
    fun contextFindingsOf(message: ChatMessage): List<ContextFinding> {
        if (!message.offersContext() || state.value.info?.protectsContent == true) return emptyList()
        // U zprávy s varováním před podvodem ne — QR platba k podvodu je to, co podvodník chce.
        if (scamVerdicts.value.containsKey(message.id)) return emptyList()
        val zone = TimeZone.currentSystemDefault()
        val sentAt = Instant.fromEpochSeconds(message.timestamp.toLong()).toLocalDateTime(zone)
        val found = findMessageContext(message.text, message.entities, sentAt, zone, homeCurrency, metricUnits)
        if (found.isEmpty) return emptyList()
        val verdict = messageContexts?.verdicts?.value?.get(MessageContexts.Key(chatId, message.id, message.text.hashCode()))
        return found.contextFindings(message.text, verdict, Clock.System.now().toLocalDateTime(zone))
    }

    fun closeMessageContext() {
        _contextSheet.value = null
    }

    /** Událost do kalendáře; editor kalendáře ji ukáže a uloží až uživatel. */
    fun addToCalendar(event: ContextFinding.Event) {
        val calendar = addCalendarEvent ?: return
        val added = calendar(
            CalendarEvent(
                title = event.title,
                description = event.description,
                place = event.place,
                start = event.start,
                allDay = event.allDay,
            ),
        )
        if (added) {
            closeMessageContext()
        } else {
            state.update { it.copy(notice = UiText(Res.string.message_context_no_calendar)) }
        }
    }

    /**
     * Let do kalendáře: „Let OK 123" (nebo „Přílet OK 123") s časem ze zprávy v pásmu letiště,
     * koncem podle přistání (nebo odhadu z trasy), letištěm jako místem a trasou a zprávou
     * v popisu, kde bývá i kód rezervace.
     */
    fun addFlightToCalendar(messageId: Long, flight: ContextFinding.Flight) {
        val calendar = addCalendarEvent ?: return
        if (flight.at == null) return
        val text = state.value.messages.firstOrNull { it.id == messageId }?.text.orEmpty()
        scope.launch {
            val name = "${flight.airline} ${flight.number}"
            val title = readString(if (flight.arrival) Res.string.message_context_flight_arrival_title else Res.string.message_context_flight_title, name)
            val event = flight.calendarEvent(title, text, TimeZone.currentSystemDefault()) ?: return@launch
            val added = calendar(event)
            if (added) {
                closeMessageContext()
            } else {
                state.update { it.copy(notice = UiText(Res.string.message_context_no_calendar)) }
            }
        }
    }

    /** Údaj z akcí u zprávy (číslo účtu, částka, číslo zásilky) do schránky. */
    fun copyFromContext(text: String) {
        scope.launch {
            copyToClipboard(text)
            if (!clipboardConfirmsCopy) state.update { it.copy(notice = UiText(Res.string.bot_text_copied)) }
        }
    }

    /** Sledování zásilky u dopravce nebo stav letu. Adresu skládáme sami, takže se na odkaz neptá. */
    fun trackParcel(url: String) {
        closeMessageContext()
        openLink(url)
    }

    /**
     * Připojení k Wi-Fi ze zprávy. Kde systém dialog „Uložit síť?" nemá a otevře se jen nastavení,
     * heslo jde do schránky — v nastavení se pak jen vloží.
     */
    fun joinWifi(wifi: ContextFinding.Wifi) {
        val join = joinWifiNetwork ?: return
        when (join(wifi.ssid, wifi.password)) {
            WifiJoin.Dialog -> closeMessageContext()
            WifiJoin.Settings -> {
                closeMessageContext()
                scope.launch {
                    copyToClipboard(wifi.password)
                    state.update { it.copy(notice = UiText(Res.string.message_context_wifi_settings, wifi.ssid)) }
                }
            }
            WifiJoin.Failed -> state.update { it.copy(notice = UiText(Res.string.message_context_wifi_failed)) }
        }
    }

    /** Kontakt ze zprávy do systémového editoru kontaktů; uloží ho až uživatel. */
    fun saveContact(contact: ContextFinding.Contact) {
        val save = saveNewContact ?: return
        if (save(NewContact(name = contact.name, phone = contact.phone, email = contact.email))) {
            closeMessageContext()
        } else {
            state.update { it.copy(notice = UiText(Res.string.message_context_no_contacts)) }
        }
    }

    /** Adresa nebo místo z akcí u zprávy v mapách platformy (hledání textem). */
    fun openMaps(query: String) {
        closeMessageContext()
        openUrl(mapsSearchUrl(query))
    }

    /** „Připomenout…" z akcí u termínu — tentýž sheet jako z nabídky nad zprávou. */
    fun remindFromContext(messageId: Long) {
        closeMessageContext()
        requestRemind(messageId)
    }

    /** Uživatel skryl varování před podvodem u zprávy. */
    fun dismissScamWarning(messageId: Long) {
        scamWarnings?.dismiss(chatId, messageId)
    }
}

/**
 * Let jako událost kalendáře: čas ze zprávy v pásmu letiště, konec podle přistání (nebo odhadu
 * z trasy), místem letiště, odkud se letí (u příletu kam), a v popisu trasa a pod ní text
 * zprávy, kde bývá i kód rezervace. Bez času letu `null`.
 *
 * Mimo [ConversationMessageContext.addFlightToCalendar], aby šla otestovat bez názvu z prostředků
 * (`readString` na JVM v testech nejde, `Resources` tam nejsou).
 */
internal fun ContextFinding.Flight.calendarEvent(title: String, messageText: String, zone: TimeZone): CalendarEvent? {
    val start = at ?: return null
    val schedule = schedule(zone)
    val airport = (if (arrival) to else from)?.let(AIRPORTS::get)
    val route = listOfNotNull(from, to).takeIf { it.size == 2 }?.joinToString(" \u2192 ")
    val text = messageText.trim()
    val description = listOfNotNull(route, text.takeIf { it.isNotEmpty() }).joinToString("\n\n")
    return CalendarEvent(
        title = title,
        description = description,
        place = airport?.label,
        start = start,
        allDay = allDay,
        begin = schedule?.start,
        end = schedule?.end,
    )
}
