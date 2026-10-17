package eu.tintera.plume.features.conversation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.QrCode
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.asUpcomingDay
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.bot_text_copied
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.event_24dp
import eu.tintera.plume.resources.local_shipping_24dp
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.menu_copy
import eu.tintera.plume.resources.menu_remind
import eu.tintera.plume.resources.message_context_account
import eu.tintera.plume.resources.message_context_contact
import eu.tintera.plume.resources.message_context_flight
import eu.tintera.plume.resources.message_context_wifi
import eu.tintera.plume.resources.message_context_code
import eu.tintera.plume.resources.message_context_booking
import eu.tintera.plume.resources.confirmation_number_24dp
import eu.tintera.plume.resources.message_context_money_failed
import eu.tintera.plume.resources.message_context_money_assumed
import eu.tintera.plume.resources.message_context_units_result
import eu.tintera.plume.resources.straighten_24dp
import eu.tintera.plume.resources.message_context_money_loading
import eu.tintera.plume.resources.message_context_money_rate_date
import eu.tintera.plume.resources.message_context_money_result
import eu.tintera.plume.resources.message_context_money_unsupported
import eu.tintera.plume.resources.currency_exchange_24dp
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.date_day_month_year
import eu.tintera.plume.core.currency.CZK
import eu.tintera.plume.core.ui.name
import eu.tintera.plume.resources.redeem_24dp
import eu.tintera.plume.resources.message_context_wifi_network
import eu.tintera.plume.resources.message_context_wifi_password
import eu.tintera.plume.resources.message_context_wifi_qr
import eu.tintera.plume.resources.wifi_24dp
import eu.tintera.plume.resources.password_24dp
import eu.tintera.plume.resources.message_context_flight_track
import eu.tintera.plume.resources.message_context_flight_duration
import eu.tintera.plume.resources.message_context_flight_estimated
import eu.tintera.plume.resources.message_context_flight_calendar
import eu.tintera.plume.resources.message_context_flight_arrival_calendar
import eu.tintera.plume.resources.flight_24dp
import eu.tintera.plume.resources.message_context_email
import eu.tintera.plume.resources.message_context_phone
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.person_add_24dp
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.mail_24dp
import eu.tintera.plume.resources.message_context_address
import eu.tintera.plume.resources.message_context_maps
import eu.tintera.plume.resources.map_24dp
import eu.tintera.plume.resources.message_context_all_day
import eu.tintera.plume.resources.message_context_amount
import eu.tintera.plume.resources.message_context_carrier_czech_post
import eu.tintera.plume.resources.message_context_carrier_international
import eu.tintera.plume.resources.message_context_carrier_packeta
import eu.tintera.plume.resources.message_context_carrier_ppl
import eu.tintera.plume.resources.message_context_carrier_dpd
import eu.tintera.plume.resources.message_context_event
import eu.tintera.plume.resources.message_context_from_model
import eu.tintera.plume.resources.message_context_parcel
import eu.tintera.plume.resources.message_context_payment
import eu.tintera.plume.resources.message_context_payment_scan
import eu.tintera.plume.resources.message_context_title
import eu.tintera.plume.resources.message_context_variable_symbol
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.open_in_new_24dp
import eu.tintera.plume.resources.payments_24dp
import eu.tintera.plume.resources.share_24dp
import eu.tintera.plume.resources.message_context_payment_share
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sheet akcí u zprávy, když je otevřený. Sbírá si stav sám, takže `ConversationScreen` (v dexu
 * na hraně, viz „Gotchas" v CLAUDE.md) přidá jediné volání bez vlastního `collectAsState`.
 */
@Composable
internal fun MessageContextHost(viewModel: ConversationViewModel) {
    val sheet by viewModel.contextSheet.collectAsStateWithLifecycle()
    val open = sheet ?: return
    MessageContextSheet(
        findings = open.findings,
        homeCurrency = open.homeCurrency,
        rates = open.rates,
        onAddToCalendar = viewModel::addToCalendar,
        onRemind = { viewModel.remindFromContext(open.messageId) },
        onCopy = viewModel::copyInSheet,
        onTrack = viewModel::trackParcel,
        onAddFlight = { flight -> viewModel.addFlightToCalendar(open.messageId, flight) },
        onOpenMaps = viewModel::openMaps,
        onSaveContact = viewModel::saveContact,
        onJoinWifi = viewModel::joinWifi.takeIf { viewModel.canJoinWifi },
        onSharePaymentQr = viewModel::sharePaymentQr.takeIf { viewModel.canSharePaymentQr },
        onDismiss = viewModel::closeMessageContext,
    )
}

/**
 * Co se ve zprávě našlo a co s tím jde udělat: termín → kalendář nebo připomínka (a mapy, když
 * model našel místo), účet → QR platba a kopírování údajů, adresa → mapy, kontakt → uložit do
 * kontaktů, let → stav letu, Wi-Fi → připojit a QR kód sítě, kód → zkopírovat, cizí měna →
 * převod kurzem ČNB, jednotky → převod, kód rezervace → zkopírovat, zásilka → sledování.
 *
 * - **Nic se nestane samo.** Kalendář otevře editor události, kde ji uloží uživatel; platbu
 *   provede banka. Sheet jen předvyplní, co ve zprávě stálo.
 * - **Je vidět, co navrhl model** (název a místo události): s ikonou AI a větou pod tím, stejně
 *   jako termín od modelu u připomínky.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageContextSheet(
    findings: List<ContextFinding>,
    homeCurrency: String?,
    rates: RatesState?,
    onAddToCalendar: (ContextFinding.Event) -> Unit,
    onRemind: () -> Unit,
    onCopy: (String) -> Unit,
    onTrack: (url: String) -> Unit,
    onAddFlight: (ContextFinding.Flight) -> Unit,
    onOpenMaps: (query: String) -> Unit,
    onSaveContact: (ContextFinding.Contact) -> Unit,
    /** `null`, když se k Wi-Fi z aplikace připojit nedá (iOS) — zbude heslo a QR kód. */
    onJoinWifi: ((ContextFinding.Wifi) -> Unit)?,
    /** `null`, když sdílet soubor nejde — QR kód pak jen na obrazovce. */
    onSharePaymentQr: ((ContextFinding.Payment) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    // Potvrzení zkopírování nese řádek sám (fajfka místo ikony kopírování): snackbar obrazovky
    // leží POD sheetem a vidět není, a na iOS a Androidu do 12 to nepotvrdí ani systém.
    var justCopied by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(justCopied) {
        if (justCopied != null) {
            delay(COPIED_MILLIS)
            justCopied = null
        }
    }
    val copy: (String) -> Unit = { value ->
        onCopy(value)
        justCopied = value
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CompositionLocalProvider(LocalJustCopied provides justCopied) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.message_context_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                findings.forEach { finding ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                    ) {
                        when (finding) {
                            is ContextFinding.Event -> EventSection(
                                finding,
                                onAdd = { onAddToCalendar(finding) },
                                onRemind = onRemind,
                                onOpenMaps = onOpenMaps,
                            )
                            is ContextFinding.Address -> AddressSection(finding, onCopy = copy, onOpenMaps = onOpenMaps)
                            is ContextFinding.Contact -> ContactSection(finding, onCopy = copy, onSave = { onSaveContact(finding) })
                            is ContextFinding.Flight -> FlightSection(finding, onCopy = copy, onTrack = onTrack, onAdd = { onAddFlight(finding) })
                            is ContextFinding.Money -> MoneySection(finding, homeCurrency, rates)
                            is ContextFinding.Measure -> MeasureSection(finding)
                            is ContextFinding.Booking -> CopyRow(
                                label = null,
                                title = stringResource(Res.string.message_context_booking),
                                value = finding.value,
                                copied = finding.value,
                                onCopy = copy,
                                icon = Res.drawable.confirmation_number_24dp,
                            )
                            is ContextFinding.Code -> CopyRow(
                                label = null,
                                title = stringResource(Res.string.message_context_code),
                                value = finding.value,
                                copied = finding.value,
                                onCopy = copy,
                                icon = Res.drawable.redeem_24dp,
                            )
                            is ContextFinding.Wifi -> WifiSection(finding, onCopy = copy, onJoin = onJoinWifi?.let { join -> { join(finding) } })
                            is ContextFinding.Payment -> PaymentSection(finding, onCopy = copy, onShareQr = onSharePaymentQr)
                            is ContextFinding.Parcel -> ParcelSection(finding, onCopy = copy, onTrack = onTrack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSection(
    event: ContextFinding.Event,
    onAdd: () -> Unit,
    onRemind: () -> Unit,
    onOpenMaps: (String) -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val time = if (event.allDay) {
        stringResource(Res.string.message_context_all_day, event.start.date.asUpcomingDay())
    } else {
        event.start.toInstant(zone).epochSeconds.asUpcomingTime()
    }
    DetailCard(icon = if (event.fromModel) Res.drawable.auto_awesome_24dp else Res.drawable.event_24dp) {
        Text(event.title, style = MaterialTheme.typography.titleMedium)
        Text(
            time,
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        event.place?.let { place ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painterResource(Res.drawable.location_on_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (event.fromModel) {
            Text(
                stringResource(Res.string.message_context_from_model),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SheetActionRow(
        icon = Res.drawable.event_24dp,
        label = stringResource(Res.string.message_context_event),
        shape = RoundedCornerShape(ROW_CORNER),
        onClick = onAdd,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    SheetActionRow(
        icon = Res.drawable.notifications_24dp,
        label = stringResource(Res.string.menu_remind),
        shape = RoundedCornerShape(ROW_CORNER),
        onClick = onRemind,
    )
    // Místo od modelu je ze zprávy, jak stálo („na Černém Mostě u kina") — mapy si s tvarem
    // poradí, hledání nemusí být v prvním pádu.
    event.place?.let { place ->
        SheetActionRow(
            icon = Res.drawable.map_24dp,
            label = stringResource(Res.string.message_context_maps),
            shape = RoundedCornerShape(ROW_CORNER),
            onClick = { onOpenMaps(place) },
        )
    }
}

/**
 * Wi-Fi: název a heslo ke zkopírování, připojení (kde jde) a QR kód sítě. QR je pro DRUHÝ
 * telefon — naskenuje ho fotoaparát a připojí se bez opisování hesla.
 */
@Composable
private fun ColumnScope.WifiSection(wifi: ContextFinding.Wifi, onCopy: (String) -> Unit, onJoin: (() -> Unit)?) {
    CopyRow(
        label = null,
        title = stringResource(Res.string.message_context_wifi_network),
        value = wifi.ssid,
        copied = wifi.ssid,
        onCopy = onCopy,
        icon = Res.drawable.wifi_24dp,
    )
    CopyRow(
        label = null,
        title = stringResource(Res.string.message_context_wifi_password),
        value = wifi.password,
        copied = wifi.password,
        onCopy = onCopy,
        icon = Res.drawable.password_24dp,
    )
    onJoin?.let { join ->
        SheetActionRow(
            icon = Res.drawable.wifi_24dp,
            label = stringResource(Res.string.message_context_wifi),
            shape = RoundedCornerShape(ROW_CORNER),
            onClick = join,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
    QrCode(
        content = wifi.qrText(),
        contentDescription = stringResource(Res.string.message_context_wifi_qr),
        modifier = Modifier.align(Alignment.CenterHorizontally).width(QR_SIZE),
    )
    Text(
        stringResource(Res.string.message_context_wifi_qr),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

/**
 * Převod cizí měny na měnu uživatele kurzem ČNB. Kurz se stahuje až s otevřením sheetu, takže
 * napřed „Načítám kurz…"; pod výsledkem stojí, z kterého dne kurz je.
 */
@Composable
private fun MoneySection(money: ContextFinding.Money, homeCurrency: String?, rates: RatesState?) {
    val target = homeCurrency ?: CZK
    DetailCard(icon = Res.drawable.currency_exchange_24dp) {
        // Odhadnutá měna (¥, „pounds") se řekne: „¥500 (JPY)" a vedle druhá karta „¥500 (CNY)".
        val title = if (money.assumed) stringResource(Res.string.message_context_money_assumed, money.shown, money.currency) else money.shown
        Text(title, style = MaterialTheme.typography.titleMedium.tabularNumbers())
        when (rates) {
            null, RatesState.Loading -> Text(
                stringResource(Res.string.message_context_money_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RatesState.Failed -> Text(
                stringResource(Res.string.message_context_money_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is RatesState.Loaded -> {
                val converted = rates.table.convert(money.amount, money.currency, target)
                if (converted == null) {
                    Text(
                        stringResource(Res.string.message_context_money_unsupported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val (whole, fraction) = moneyParts(converted)
                    Text(
                        stringResource(Res.string.message_context_money_result, stringResource(Res.string.decimal_number, whole, fraction), target),
                        style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                    )
                    rates.table.date?.let { date ->
                        Text(
                            stringResource(
                                Res.string.message_context_money_rate_date,
                                stringResource(Res.string.date_day_month_year, date.day, stringResource(date.month.name()), date.year),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Převod jednotek; počítá se na místě, kurz ani síť nepotřebuje. */
@Composable
private fun MeasureSection(measure: ContextFinding.Measure) {
    val converted = measure.unit.convert(measure.value)
    val (whole, fraction) = measureParts(converted)
    val number = fraction?.let { stringResource(Res.string.decimal_number, whole, it) } ?: whole
    DetailCard(icon = Res.drawable.straighten_24dp) {
        Text(measure.shown, style = MaterialTheme.typography.titleMedium.tabularNumbers())
        Text(
            stringResource(Res.string.message_context_units_result, number, measure.unit.target.symbol),
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
        )
    }
}

/**
 * Převedená míra: od stovky celá čísla („165 cm" by s desetinou nic nového neřekl), pod ní jedno
 * desetinné místo, a to jen když není nula („3,1 mi", „20 °C"). Celá část po tisících.
 */
internal fun measureParts(value: Double): Pair<String, String?> {
    val negative = value < 0
    val absolute = kotlin.math.abs(value)
    val tenths = if (absolute >= 100) kotlin.math.round(absolute).toLong() * 10 else kotlin.math.round(absolute * 10).toLong()
    val whole = (tenths / 10).toString().reversed().chunked(3).joinToString("\u202F").reversed()
    val fraction = (tenths % 10).takeIf { it != 0L }?.toString()
    return (if (negative && tenths != 0L) "\u2212$whole" else whole) to fraction
}

/** Částka na dvě desetinná místa: celá část po tisících (úzká mezera), haléře zvlášť. */
internal fun moneyParts(value: Double): Pair<String, String> {
    val cents = kotlin.math.round(value * 100).toLong()
    val whole = (cents / 100).toString().reversed().chunked(3).joinToString("\u202F").reversed()
    val fraction = (cents % 100).toString().padStart(2, '0')
    return whole to fraction
}

@Composable
private fun FlightSection(flight: ContextFinding.Flight, onCopy: (String) -> Unit, onTrack: (String) -> Unit, onAdd: () -> Unit) {
    CopyRow(
        label = null,
        title = stringResource(Res.string.message_context_flight),
        value = "${flight.airline} ${flight.number}",
        copied = flight.code,
        onCopy = onCopy,
        icon = Res.drawable.flight_24dp,
    )
    // Trasa a délka hned pod číslem letu, ke kterému patří.
    val zone = remember { TimeZone.currentSystemDefault() }
    val schedule = remember(flight) { flight.schedule(zone) }
    FlightRoute(flight, schedule)
    SheetActionRow(
        icon = Res.drawable.open_in_new_24dp,
        label = stringResource(Res.string.message_context_flight_track),
        shape = RoundedCornerShape(ROW_CORNER),
        onClick = { onTrack(flight.trackingUrl()) },
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    // Do kalendáře jen s časem ze zprávy; stav letu odjinud nezjistíme.
    flight.at?.let { at ->
        // Čas v pásmu TELEFONU — odlet z New Yorku v 9:40 se ukáže tak, jak ho uživatel zažije.
        val time = when {
            flight.allDay -> at.date.asUpcomingDay()
            schedule != null -> schedule.start.epochSeconds.asUpcomingTime()
            else -> at.toInstant(zone).epochSeconds.asUpcomingTime()
        }
        SheetActionRow(
            icon = Res.drawable.event_24dp,
            label = stringResource(if (flight.arrival) Res.string.message_context_flight_arrival_calendar else Res.string.message_context_flight_calendar),
            shape = RoundedCornerShape(ROW_CORNER),
            onClick = onAdd,
            trailing = {
                Text(
                    time,
                    style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/**
 * Trasa a délka letu pod číslem letu („PRG → LHR · 2 h 15 min"); odhad z trasy to řekne.
 * Bez trasy i délky nic.
 */
@Composable
private fun FlightRoute(flight: ContextFinding.Flight, schedule: FlightSchedule?) {
    val route = listOfNotNull(flight.from, flight.to).takeIf { it.size == 2 }?.joinToString(" \u2192 ")
    val minutes = schedule?.minutes
    val duration = minutes?.let {
        val text = stringResource(Res.string.message_context_flight_duration, it / 60, (it % 60).toString().padStart(2, '0'))
        if (schedule.estimated) stringResource(Res.string.message_context_flight_estimated, text) else text
    }
    val line = listOfNotNull(route, duration).joinToString(" \u00B7 ").takeIf { it.isNotEmpty() } ?: return
    Text(
        line,
        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun ContactSection(contact: ContextFinding.Contact, onCopy: (String) -> Unit, onSave: () -> Unit) {
    contact.name?.let { name ->
        DetailCard(icon = Res.drawable.person_24dp) {
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
    }
    contact.phone?.let { phone ->
        CopyRow(
            label = null,
            title = stringResource(Res.string.message_context_phone),
            value = phone,
            copied = phone,
            onCopy = onCopy,
            icon = Res.drawable.call_24dp,
        )
    }
    contact.email?.let { email ->
        CopyRow(
            label = null,
            title = stringResource(Res.string.message_context_email),
            value = email,
            copied = email,
            onCopy = onCopy,
            icon = Res.drawable.mail_24dp,
        )
    }
    SheetActionRow(
        icon = Res.drawable.person_add_24dp,
        label = stringResource(Res.string.message_context_contact),
        shape = RoundedCornerShape(ROW_CORNER),
        onClick = onSave,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

@Composable
private fun AddressSection(address: ContextFinding.Address, onCopy: (String) -> Unit, onOpenMaps: (String) -> Unit) {
    CopyRow(
        label = null,
        title = stringResource(Res.string.message_context_address),
        value = address.text,
        copied = address.text,
        onCopy = onCopy,
        icon = Res.drawable.location_on_24dp,
    )
    SheetActionRow(
        icon = Res.drawable.map_24dp,
        label = stringResource(Res.string.message_context_maps),
        shape = RoundedCornerShape(ROW_CORNER),
        onClick = { onOpenMaps(address.text) },
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

@Composable
private fun ColumnScope.PaymentSection(
    payment: ContextFinding.Payment,
    onCopy: (String) -> Unit,
    onShareQr: ((ContextFinding.Payment) -> Unit)?,
) {
    val qrLabel = stringResource(Res.string.message_context_payment)
    DetailCard(icon = Res.drawable.payments_24dp) {
        Text(qrLabel, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.message_context_payment_scan),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    QrCode(
        content = payment.spayd(),
        contentDescription = qrLabel,
        modifier = Modifier.align(Alignment.CenterHorizontally).width(QR_SIZE),
    )
    // Z obrazovky telefonu QR týž telefon nenaskenuje; bankovní aplikace ho přečte z obrázku.
    onShareQr?.let { share ->
        SheetActionRow(
            icon = Res.drawable.share_24dp,
            label = stringResource(Res.string.message_context_payment_share),
            shape = RoundedCornerShape(ROW_CORNER),
            onClick = { share(payment) },
        )
    }
    CopyRow(Res.string.message_context_account, payment.account, copied = payment.account.filterNot { it == ' ' }, onCopy)
    payment.amount?.let { amount ->
        val shown = listOfNotNull(amount, payment.currency).joinToString(" ")
        CopyRow(Res.string.message_context_amount, shown, copied = amount, onCopy)
    }
    payment.variableSymbol?.let { CopyRow(Res.string.message_context_variable_symbol, it, copied = it, onCopy) }
}

@Composable
private fun ParcelSection(parcel: ContextFinding.Parcel, onCopy: (String) -> Unit, onTrack: (String) -> Unit) {
    val carrier = stringResource(
        when (parcel.carrier) {
            ParcelCarrier.CzechPost -> Res.string.message_context_carrier_czech_post
            ParcelCarrier.Packeta -> Res.string.message_context_carrier_packeta
            ParcelCarrier.International -> Res.string.message_context_carrier_international
            ParcelCarrier.Ppl -> Res.string.message_context_carrier_ppl
            ParcelCarrier.Dpd -> Res.string.message_context_carrier_dpd
        },
    )
    val language = Locale.current.language
    CopyRow(label = null, title = carrier, value = parcel.number, copied = parcel.number, onCopy = onCopy, icon = Res.drawable.local_shipping_24dp)
    SheetActionRow(
        icon = Res.drawable.open_in_new_24dp,
        label = stringResource(Res.string.message_context_parcel),
        shape = RoundedCornerShape(ROW_CORNER),
        onClick = { onTrack(parcel.trackingUrl(language)) },
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

/** Karta s tím, co se ve zprávě našlo; ikona vlevo, text pod sebou. */
@Composable
private fun DetailCard(icon: DrawableResource, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(ROW_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        }
    }
}

@Composable
private fun CopyRow(label: StringResource, value: String, copied: String, onCopy: (String) -> Unit) =
    CopyRow(label = label, title = null, value = value, copied = copied, onCopy = onCopy, icon = null)

/**
 * Údaj se štítkem a kopírováním na ťuknutí. Celý řádek je tlačítko — ikona kopírování vpravo
 * jen říká, co ťuknutí udělá; samostatné malé tlačítko by se trefovalo hůř.
 */
@Composable
private fun CopyRow(
    label: StringResource?,
    title: String?,
    value: String,
    copied: String,
    onCopy: (String) -> Unit,
    icon: DrawableResource?,
) {
    val copyLabel = stringResource(Res.string.menu_copy)
    val copiedLabel = stringResource(Res.string.bot_text_copied)
    Surface(
        onClick = { onCopy(copied) },
        shape = RoundedCornerShape(ROW_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    title ?: label?.let { stringResource(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyLarge.tabularNumbers())
            }
            // Fajfka po ťuknutí je jediné potvrzení, které je nad sheetem vidět; čtečce ho
            // oznámí živá oblast.
            Crossfade(LocalJustCopied.current == copied, label = "copied") { done ->
                Icon(
                    painterResource(if (done) Res.drawable.check_24dp else Res.drawable.content_copy_24dp),
                    contentDescription = if (done) copiedLabel else copyLabel,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** Hodnota, kterou sheet právě zkopíroval; její řádek ukáže fajfku (viz [MessageContextSheet]). */
private val LocalJustCopied = compositionLocalOf<String?> { null }

/** Jak dlouho řádek po zkopírování ukazuje fajfku. */
private const val COPIED_MILLIS = 2_000L

private val ROW_CORNER = 20.dp
private val SECTION_GAP = 8.dp
private val QR_SIZE = 200.dp
