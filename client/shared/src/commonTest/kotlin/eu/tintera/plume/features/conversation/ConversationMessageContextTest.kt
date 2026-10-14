package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.calendar.CalendarEvent
import eu.tintera.plume.core.contacts.NewContact
import eu.tintera.plume.core.currency.RateTable
import eu.tintera.plume.core.location.mapsSearchUrl
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.wifi.WifiJoin
import eu.tintera.plume.features.conversation.items.ContextVerb
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bot_text_copied
import eu.tintera.plume.resources.message_context_no_calendar
import eu.tintera.plume.resources.message_context_no_contacts
import eu.tintera.plume.resources.message_context_wifi_failed
import eu.tintera.plume.resources.message_context_wifi_settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationMessageContextTest {

    private class FakeSettings(enabled: Boolean) : UnimplementedAppSettings() {
        override val messageContextEnabled = MutableStateFlow(enabled)
    }

    private var menusClosed = 0
    private val links = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val copied = mutableListOf<String>()
    private val reminded = mutableListOf<Long>()
    private val events = mutableListOf<CalendarEvent>()
    private val contacts = mutableListOf<NewContact>()
    private val joined = mutableListOf<Pair<String, String>>()
    private var ratesLoaded = 0

    private fun TestScope.context(
        state: MutableStateFlow<ConversationState>,
        settings: FakeSettings = FakeSettings(enabled = true),
        calendar: Boolean = true,
        contactsApp: Boolean = true,
        wifi: WifiJoin? = WifiJoin.Dialog,
        rates: (suspend () -> RateTable?)? = { RATES },
    ) = ConversationMessageContext(
        chatId = TEST_CHAT,
        state = state,
        scope = detachedScope(),
        settings = settings,
        openUrl = { opened += it },
        copyToClipboard = { copied += it },
        clipboardConfirmsCopy = false,
        scamWarnings = null,
        messageContexts = null,
        addCalendarEvent = { events += it; calendar },
        saveNewContact = { contacts += it; contactsApp },
        joinWifiNetwork = wifi?.let { result -> { ssid, password -> joined += ssid to password; result } },
        canJoinWifi = wifi != null,
        loadRates = rates?.let { load -> { ratesLoaded++; load() } },
        homeCurrency = "CZK",
        metricUnits = true,
        closeMenus = { menusClosed++ },
        openLink = { links += it },
        requestRemind = { reminded += it },
        logger = testLogger,
    )

    private fun stateWithMenu(findings: List<ContextFinding>) =
        testState(testMessage(1)).apply {
            value = value.copy(
                messageMenu = MessageMenu(
                    messageId = 1,
                    timestamp = 0,
                    isOutgoing = false,
                    emoji = emptyList(),
                    chosenEmoji = emptySet(),
                    actions = MessageActions(
                        canReply = true,
                        canCopy = true,
                        canDeleteForSelf = true,
                        canDeleteForAll = false,
                        canEdit = false,
                        canPin = false,
                        canForward = true,
                        canSave = true,
                        canReport = false,
                    ),
                    items = emptyList(),
                    favoriteSticker = null,
                    contextFindings = findings,
                ),
            )
        }

    @Test
    fun bezNalezuSeSheetNeotevre() = runTest {
        val context = context(testState(testMessage(1)))
        context.openMessageContext(1, emptyList())
        assertNull(context.contextSheet.value)
    }

    @Test
    fun kurzSeStahujeJenKCastceVeZprave() = runTest {
        val context = context(testState(testMessage(1)))
        context.openMessageContext(1, listOf(ADDRESS))
        advanceUntilIdle()
        assertNull(context.contextSheet.value?.rates)
        assertEquals(0, ratesLoaded)

        context.openMessageContext(1, listOf(MONEY))
        assertEquals(RatesState.Loading, context.contextSheet.value?.rates)
        assertEquals("CZK", context.contextSheet.value?.homeCurrency)
        advanceUntilIdle()
        assertEquals(RatesState.Loaded(RATES), context.contextSheet.value?.rates)
    }

    @Test
    fun nedostupnyKurzSeUkazeJakoChyba() = runTest {
        val context = context(testState(testMessage(1)), rates = { null })
        context.openMessageContext(1, listOf(MONEY))
        advanceUntilIdle()
        assertEquals(RatesState.Failed, context.contextSheet.value?.rates)
    }

    @Test
    fun kurzPatriJenSheetuKteryONejZadal() = runTest {
        val tables = mutableListOf<CompletableDeferred<RateTable?>>()
        val context = context(
            testState(testMessage(1), testMessage(2)),
            rates = { CompletableDeferred<RateTable?>().also { tables += it }.await() },
        )
        context.openMessageContext(1, listOf(MONEY))
        runCurrent()
        context.closeMessageContext()
        context.openMessageContext(2, listOf(MONEY, ADDRESS))
        runCurrent()

        tables.first().complete(RATES)
        advanceUntilIdle()
        val sheet = assertNotNull(context.contextSheet.value)
        assertEquals(2L, sheet.messageId)
        assertEquals(RatesState.Loading, sheet.rates, "kurz pro zavřený sheet zprávy 1 se do nového nedostane")
    }

    @Test
    fun polozkaZNabidkySeProvedeRovnou() = runTest {
        val parcel = ContextFinding.Parcel(ParcelCarrier.CzechPost, "RR123456785CZ")
        val state = stateWithMenu(listOf(ADDRESS, ContextFinding.Code("4711"), parcel))
        val context = context(state)

        context.runContextItem(1, MessageMenuAction.ContextItem(ContextVerb.Maps, 0))
        assertEquals(listOf(mapsSearchUrl(ADDRESS.text)), opened)

        context.runContextItem(1, MessageMenuAction.ContextItem(ContextVerb.CopyCode, 1))
        advanceUntilIdle()
        assertEquals(listOf("4711"), copied)
        assertEquals(UiText(Res.string.bot_text_copied), state.value.notice)

        context.runContextItem(1, MessageMenuAction.ContextItem(ContextVerb.TrackParcel, 2))
        assertEquals(1, links.size)
        assertTrue("RR123456785CZ" in links.single())

        assertEquals(3, menusClosed)
        assertNull(context.contextSheet.value, "nic z toho sheet neotevírá")
    }

    @Test
    fun prevodZNabidkyOtevreSheetSeVsemiNalezy() = runTest {
        val findings = listOf(ADDRESS, MONEY)
        val context = context(stateWithMenu(findings))
        context.runContextItem(1, MessageMenuAction.ContextItem(ContextVerb.Money, 1))
        assertEquals(findings, context.contextSheet.value?.findings)
        assertEquals(1, menusClosed)
    }

    @Test
    fun polozkaKJineZpraveNeboSNesedicimNalezemNicNeudela() = runTest {
        val context = context(stateWithMenu(listOf(ADDRESS)))
        context.runContextItem(2, MessageMenuAction.ContextItem(ContextVerb.Maps, 0))
        context.runContextItem(1, MessageMenuAction.ContextItem(ContextVerb.CopyCode, 0))
        context.runContextItem(1, MessageMenuAction.ContextItem(ContextVerb.Maps, 5))
        advanceUntilIdle()
        assertTrue(opened.isEmpty())
        assertTrue(copied.isEmpty())
    }

    @Test
    fun terminDoKalendareZavreSheetAChybejiciKalendarSeOhlasi() = runTest {
        val event = ContextFinding.Event(
            start = LocalDateTime(2026, 10, 20, 18, 0),
            allDay = false,
            title = "Večeře",
            place = "U Fleků",
            description = "Večeře v sedm",
        )
        val state = testState(testMessage(1))
        val context = context(state)
        context.openMessageContext(1, listOf(event))
        context.addToCalendar(event)
        assertEquals(listOf(CalendarEvent("Večeře", "Večeře v sedm", "U Fleků", event.start, allDay = false)), events)
        assertNull(context.contextSheet.value)

        val missing = context(state, calendar = false)
        missing.openMessageContext(1, listOf(event))
        missing.addToCalendar(event)
        assertNotNull(missing.contextSheet.value)
        assertEquals(UiText(Res.string.message_context_no_calendar), state.value.notice)
    }

    @Test
    fun letVKalendariMaTrasuNadZpravouALetisteOdletuJakoMisto() {
        val flight = ContextFinding.Flight(
            airline = "OK",
            number = "123",
            at = LocalDateTime(2026, 10, 20, 7, 30),
            from = "PRG",
            to = "CDG",
        )
        val event = assertNotNull(flight.calendarEvent("Let OK 123", "  Odlet zítra, rezervace ABC123  ", TimeZone.UTC))
        assertEquals("Let OK 123", event.title)
        assertEquals("PRG \u2192 CDG\n\nOdlet zítra, rezervace ABC123", event.description)
        assertEquals(AIRPORTS.getValue("PRG").label, event.place)
        assertEquals(flight.at, event.start)
        assertEquals(flight.at?.toInstant(TimeZone.of("Europe/Prague")), event.begin, "čas je v pásmu letiště, ne v pásmu telefonu")

        val arrival = flight.copy(arrival = true).calendarEvent("Přílet OK 123", "", TimeZone.UTC)
        assertEquals(AIRPORTS.getValue("CDG").label, arrival?.place, "u příletu je místem cílové letiště")
        assertEquals("PRG \u2192 CDG", arrival?.description)
    }

    @Test
    fun letBezCasuDoKalendareNejde() = runTest {
        val flight = ContextFinding.Flight(airline = "OK", number = "123", from = "PRG", to = "CDG")
        assertNull(flight.calendarEvent("Let OK 123", "", TimeZone.UTC))

        context(testState(testMessage(1))).addFlightToCalendar(1, flight)
        advanceUntilIdle()
        assertTrue(events.isEmpty())
    }

    @Test
    fun wifiSeDialogemZavreSheet() = runTest {
        val state = testState(testMessage(1))
        val context = context(state)
        assertTrue(context.canJoinWifi)
        context.openMessageContext(1, listOf(WIFI))
        context.joinWifi(WIFI)
        assertEquals(listOf("Plume_Home" to "tajne1234"), joined)
        assertNull(context.contextSheet.value)
        assertTrue(copied.isEmpty())
    }

    @Test
    fun wifiPresNastaveniDaHesloDoSchranky() = runTest {
        val state = testState(testMessage(1))
        val context = context(state, wifi = WifiJoin.Settings)
        context.openMessageContext(1, listOf(WIFI))
        context.joinWifi(WIFI)
        advanceUntilIdle()
        assertNull(context.contextSheet.value)
        assertEquals(listOf("tajne1234"), copied)
        assertEquals(UiText(Res.string.message_context_wifi_settings, "Plume_Home"), state.value.notice)
    }

    @Test
    fun nepovedenaWifiNechaSheetOtevrenyAOhlasiSe() = runTest {
        val state = testState(testMessage(1))
        val context = context(state, wifi = WifiJoin.Failed)
        context.openMessageContext(1, listOf(WIFI))
        context.joinWifi(WIFI)
        assertNotNull(context.contextSheet.value)
        assertEquals(UiText(Res.string.message_context_wifi_failed), state.value.notice)
    }

    @Test
    fun telefonBezWifiJiNenabizi() = runTest {
        val context = context(testState(testMessage(1)), wifi = null)
        context.joinWifi(WIFI)
        assertTrue(!context.canJoinWifi)
        assertTrue(joined.isEmpty())
    }

    @Test
    fun kontaktJdeDoEditoruAChybejiciAplikaceSeOhlasi() = runTest {
        val contact = ContextFinding.Contact(name = "Jana", phone = "+420 777 123 456", email = null)
        val state = testState(testMessage(1))
        val context = context(state)
        context.openMessageContext(1, listOf(contact))
        context.saveContact(contact)
        assertEquals(listOf(NewContact("Jana", "+420 777 123 456", null)), contacts)
        assertNull(context.contextSheet.value)

        val missing = context(state, contactsApp = false)
        missing.openMessageContext(1, listOf(contact))
        missing.saveContact(contact)
        assertNotNull(missing.contextSheet.value)
        assertEquals(UiText(Res.string.message_context_no_contacts), state.value.notice)
    }

    @Test
    fun pripomenutiZAkciZavreSheetAOtevreTenZNabidky() = runTest {
        val context = context(testState(testMessage(1)))
        context.openMessageContext(1, listOf(ADDRESS))
        context.remindFromContext(1)
        assertNull(context.contextSheet.value)
        assertEquals(listOf(1L), reminded)
    }

    @Test
    fun nalezyVTextuZpravyAleNeVChranenemChatu() = runTest {
        val message = testMessage(1, text = "Wi-Fi: Plume_Home, heslo: tajne1234")
        val state = testState(message)
        val context = context(state)
        assertTrue(WIFI in context.contextFindingsOf(message))

        state.value = state.value.copy(info = testChatInfo().copy(protectsContent = true))
        assertTrue(context.contextFindingsOf(message).isEmpty(), "z chráněného chatu nesmí nic ven")
    }

    @Test
    fun posudkyTerminuNejsouVypnuteNeboVChranenemChatu() = runTest {
        val state = testState(testMessage(1))
        val settings = FakeSettings(enabled = true)
        val context = context(state, settings)
        backgroundScope.launch { context.contextVerdicts.collect {} }
        runCurrent()
        assertEquals(emptyMap(), context.contextVerdicts.value)

        settings.messageContextEnabled.value = false
        runCurrent()
        assertNull(context.contextVerdicts.value)

        settings.messageContextEnabled.value = true
        state.value = state.value.copy(info = testChatInfo().copy(protectsContent = true))
        runCurrent()
        assertNull(context.contextVerdicts.value)
    }

    private companion object {
        val ADDRESS = ContextFinding.Address("Václavské náměstí 1, Praha")
        val MONEY = ContextFinding.Money(shown = "20 €", amount = 20.0, currency = "EUR")
        val WIFI = ContextFinding.Wifi("Plume_Home", "tajne1234")
        val RATES = RateTable(date = null, czkPerUnit = mapOf("EUR" to 25.0))
    }
}
