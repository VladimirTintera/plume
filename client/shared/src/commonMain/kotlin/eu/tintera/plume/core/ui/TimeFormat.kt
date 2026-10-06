package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.date_day_month
import eu.tintera.plume.resources.date_day_month_year
import eu.tintera.plume.resources.date_short
import eu.tintera.plume.resources.date_today
import eu.tintera.plume.resources.date_today_short
import eu.tintera.plume.resources.date_tomorrow
import eu.tintera.plume.resources.date_upcoming_time
import eu.tintera.plume.resources.date_yesterday
import eu.tintera.plume.resources.date_yesterday_short
import eu.tintera.plume.resources.day_long_1
import eu.tintera.plume.resources.day_long_2
import eu.tintera.plume.resources.day_long_3
import eu.tintera.plume.resources.day_long_4
import eu.tintera.plume.resources.day_long_5
import eu.tintera.plume.resources.day_long_6
import eu.tintera.plume.resources.day_long_7
import eu.tintera.plume.resources.day_on_1
import eu.tintera.plume.resources.day_on_2
import eu.tintera.plume.resources.day_on_3
import eu.tintera.plume.resources.day_on_4
import eu.tintera.plume.resources.day_on_5
import eu.tintera.plume.resources.day_on_6
import eu.tintera.plume.resources.day_on_7
import eu.tintera.plume.resources.day_short_1
import eu.tintera.plume.resources.day_short_2
import eu.tintera.plume.resources.day_short_3
import eu.tintera.plume.resources.day_short_4
import eu.tintera.plume.resources.day_short_5
import eu.tintera.plume.resources.day_short_6
import eu.tintera.plume.resources.day_short_7
import eu.tintera.plume.resources.month_1
import eu.tintera.plume.resources.month_10
import eu.tintera.plume.resources.month_11
import eu.tintera.plume.resources.month_12
import eu.tintera.plume.resources.month_2
import eu.tintera.plume.resources.month_3
import eu.tintera.plume.resources.month_4
import eu.tintera.plume.resources.month_5
import eu.tintera.plume.resources.month_6
import eu.tintera.plume.resources.month_7
import eu.tintera.plume.resources.month_8
import eu.tintera.plume.resources.month_9
import eu.tintera.plume.resources.presence_last_seen_at
import eu.tintera.plume.resources.presence_last_seen_on
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Čas událostí starších než „dnes".
 *
 * Používá se tam, kde jde o **stáří**, ne o pořadí během dne: v seznamu chatů u poslední zprávy
 * a u „naposledy viděn". Samotné hodiny by u události z loňska nic neřekly, a naopak plné datum
 * u dnešní zprávy je zbytečně upovídané, takže se podrobnost s věkem snižuje.
 *
 * V konverzaci se naopak vypisují jen hodiny — tam je datum v hlavičce dne a zprávy jdou
 * chronologicky za sebou.
 */
@Composable
fun Int.asRelativeTime(): String {
    val zone = TimeZone.currentSystemDefault()
    val moment = Instant.fromEpochSeconds(toLong()).toLocalDateTime(zone)
    val today = Clock.System.now().toLocalDateTime(zone).date

    val days = (today.toEpochDays() - moment.date.toEpochDays()).toInt()

    return when {
        // Záporná hodnota znamená čas v budoucnosti — u posunutých hodin na zařízení se to stane.
        days <= 0 -> "${moment.hour.pad()}:${moment.minute.pad()}"
        days == 1 -> stringResource(Res.string.date_yesterday_short)
        days < 7 -> stringResource(moment.date.dayOfWeek.shortName())
        else -> stringResource(Res.string.date_short, moment.date.day, moment.date.month.number)
    }
}

/**
 * „Naposledy viděn" — CELÁ VĚTA, ne jen čas vsazený do „naposledy %s".
 *
 * [asRelativeTime] je dělaný na SLOUPEC v seznamu chatů, kde čas stojí sám a zkratka dne se
 * přečte jako zkratka. Uvnitř věty se z ní stane hláška „naposledy ne", u které to vypadá,
 * že se text usekl. Datum a den se proto skládají jinak než tam.
 *
 * **Den nese předložku, protože v češtině není jedna** (`day_on_*`): „v pondělí", ale
 * „ve středu". Kdyby se předložka lepila v kódu, jazyk by si ji neuhlídal — tentýž důvod,
 * proč je pořadí dne a měsíce formátovací řetězec, ne slepenec.
 *
 * Starší než týden dostane datum bez času: hodina u něčeho, co bylo před měsícem, nic neříká.
 */
@Composable
fun Int.asLastSeen(): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochSeconds(toLong()).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date

    val days = (today.toEpochDays() - date.toEpochDays()).toInt()

    // Záporná hodnota znamená čas v budoucnosti — u posunutých hodin na zařízení se to stane.
    val day = when {
        days <= 0 -> stringResource(Res.string.date_today_short)
        days == 1 -> stringResource(Res.string.date_yesterday_short)
        days < 7 -> stringResource(date.dayOfWeek.onName())
        date.year == today.year ->
            return stringResource(
                Res.string.presence_last_seen_on,
                stringResource(Res.string.date_day_month, date.day, stringResource(date.month.name())),
            )
        else -> return stringResource(
            Res.string.presence_last_seen_on,
            stringResource(
                Res.string.date_day_month_year,
                date.day,
                stringResource(date.month.name()),
                date.year,
            ),
        )
    }
    return stringResource(Res.string.presence_last_seen_at, day, asTimeOfDay(zone))
}

/** Den s předložkou („v neděli", „on Sunday") — do věty, ne do sloupce. */
private fun DayOfWeek.onName(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.day_on_1
    DayOfWeek.TUESDAY -> Res.string.day_on_2
    DayOfWeek.WEDNESDAY -> Res.string.day_on_3
    DayOfWeek.THURSDAY -> Res.string.day_on_4
    DayOfWeek.FRIDAY -> Res.string.day_on_5
    DayOfWeek.SATURDAY -> Res.string.day_on_6
    else -> Res.string.day_on_7
}

/**
 * Hodiny a minuty samotné.
 *
 * V konverzaci je datum v předělu dne a zprávy jdou chronologicky za sebou, takže u jednotlivé
 * zprávy nese informaci jen čas. Vypisuje se při odkrývání časů tahem a v nabídce nad zprávou.
 *
 * Není `@Composable` — nepotřebuje prostředky a ve 24hodinovém zápisu se pořadí mezi jazyky nemění.
 */
fun Int.asTimeOfDay(zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val moment = Instant.fromEpochSeconds(toLong()).toLocalDateTime(zone)
    return "${moment.hour.pad()}:${moment.minute.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')

private fun DayOfWeek.shortName(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.day_short_1
    DayOfWeek.TUESDAY -> Res.string.day_short_2
    DayOfWeek.WEDNESDAY -> Res.string.day_short_3
    DayOfWeek.THURSDAY -> Res.string.day_short_4
    DayOfWeek.FRIDAY -> Res.string.day_short_5
    DayOfWeek.SATURDAY -> Res.string.day_short_6
    else -> Res.string.day_short_7
}

/**
 * Začátek místního dne, do kterého čas (sekundy od epochy) patří — v sekundách od epochy.
 *
 * Pro `derivedStateOf`: všechny zprávy téhož dne dají stejnou hodnotu, takže se odvozený stav
 * změní jen s přechodem na jiný den. [asDayLabel] je composable a do odvozeného stavu nesmí.
 */
fun Int.startOfLocalDay(zone: TimeZone = TimeZone.currentSystemDefault()): Int =
    Instant.fromEpochSeconds(toLong()).toLocalDateTime(zone).date.atStartOfDayIn(zone).epochSeconds.toInt()

/**
 * Datum pro předěl dne v konverzaci.
 *
 * Podrobnost se s věkem **zvyšuje**, ne snižuje jako u [asRelativeTime]: předěl je nadpis, pod
 * kterým následuje celý blok zpráv, takže musí říct dost na to, aby se v historii dalo
 * zorientovat. „po" by u zprávy z loňska bylo k ničemu.
 *
 * Rok se dopisuje jen tehdy, když nesedí s letoškem — u letošních dat je to šum.
 */
@Composable
fun Int.asDayLabel(): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochSeconds(toLong()).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date

    val days = (today.toEpochDays() - date.toEpochDays()).toInt()

    return when {
        // Záporná hodnota znamená čas v budoucnosti — u posunutých hodin na zařízení se to stane.
        days <= 0 -> stringResource(Res.string.date_today)
        days == 1 -> stringResource(Res.string.date_yesterday)
        days < 7 -> stringResource(date.dayOfWeek.longName())
        date.year == today.year ->
            stringResource(Res.string.date_day_month, date.day, stringResource(date.month.name()))
        else -> stringResource(
            Res.string.date_day_month_year,
            date.day,
            stringResource(date.month.name()),
            date.year,
        )
    }
}

/**
 * Samotné datum, bez „dnes" a názvu dne — u údaje, který se čte jako záznam (vydání aplikace),
 * ne jako pozice v konverzaci. Rok se dopisuje jen tehdy, když nesedí s letoškem.
 */
@Composable
fun Int.asDate(): String {
    val date = localDate()
    val month = stringResource(date.month.name())
    return if (date.year == localNowYear()) {
        stringResource(Res.string.date_day_month, date.day, month)
    } else {
        stringResource(Res.string.date_day_month_year, date.day, month, date.year)
    }
}

private fun localNowYear(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

/**
 * Čas v BUDOUCNOSTI — konec ztlumení: dnes jen hodina, zítra slovem, jindy datum.
 *
 * [asDayLabel] je dělaný na minulost a budoucí den by vydával za „dnes".
 */
@Composable
fun Long.asUpcomingTime(): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochSeconds(this).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date
    val time = toInt().asTimeOfDay(zone)
    val day = when ((date.toEpochDays() - today.toEpochDays()).toInt()) {
        in Int.MIN_VALUE..0 -> return time
        1 -> stringResource(Res.string.date_tomorrow)
        in 2..6 -> stringResource(date.dayOfWeek.longName())
        else -> stringResource(Res.string.date_day_month, date.day, stringResource(date.month.name()))
    }
    return stringResource(Res.string.date_upcoming_time, day, time)
}

/**
 * Jsou oba časy ve stejném dni?
 *
 * Porovnává se **místní datum**, ne rozdíl sekund: den má 24 hodin jen někdy — na přechodu
 * letního času je kratší nebo delší a půlnoc se posouvá podle časového pásma.
 */
fun Int.isSameDayAs(other: Int): Boolean = localDate() == other.localDate()

internal fun Int.localDate(): LocalDate = Instant.fromEpochSeconds(toLong())
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date

/** Dlouhý název dne. V předělu je na zkratku dost místa a celé slovo se čte líp. */
private fun DayOfWeek.longName(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.day_long_1
    DayOfWeek.TUESDAY -> Res.string.day_long_2
    DayOfWeek.WEDNESDAY -> Res.string.day_long_3
    DayOfWeek.THURSDAY -> Res.string.day_long_4
    DayOfWeek.FRIDAY -> Res.string.day_long_5
    DayOfWeek.SATURDAY -> Res.string.day_long_6
    else -> Res.string.day_long_7
}

/**
 * Název měsíce. Tvar si řeší každý jazyk sám: čeština ho má ve 2. pádu, protože stojí za dnem
 * („5. února", ne „5. únor"), angličtina v základním. Proto je i samotné pořadí dne a měsíce
 * formátovací řetězec (`date_day_month`), ne slepenec tady v kódu.
 */
/** Název měsíce ve tvaru za dnem („5. února"), tedy i za „od" („od března 2021"). */
internal fun Month.name(): StringResource = when (this) {
    Month.JANUARY -> Res.string.month_1
    Month.FEBRUARY -> Res.string.month_2
    Month.MARCH -> Res.string.month_3
    Month.APRIL -> Res.string.month_4
    Month.MAY -> Res.string.month_5
    Month.JUNE -> Res.string.month_6
    Month.JULY -> Res.string.month_7
    Month.AUGUST -> Res.string.month_8
    Month.SEPTEMBER -> Res.string.month_9
    Month.OCTOBER -> Res.string.month_10
    Month.NOVEMBER -> Res.string.month_11
    else -> Res.string.month_12
}
