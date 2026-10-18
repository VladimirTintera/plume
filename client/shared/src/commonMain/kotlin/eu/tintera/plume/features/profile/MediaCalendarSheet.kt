package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.CalendarDayMedia
import eu.tintera.plume.core.telegram.CalendarMonth
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.calendarMonths
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.monthTitle
import eu.tintera.plume.core.ui.name
import eu.tintera.plume.core.ui.shortName
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.date_day_month
import eu.tintera.plume.resources.profile_calendar
import eu.tintera.plume.resources.profile_calendar_day
import eu.tintera.plume.resources.profile_calendar_day_items
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kalendář fotek a videí chatu po měsících, od nejnovějšího (`getChatMessageCalendar`). Den,
 * ve kterém médium přišlo, nese náhled své první fotky; ťuknutí zavře kalendář a otevře na ní
 * prohlížeč — v něm se pak listuje dál jako z mřížky. Měsíce bez médií se vynechávají
 * ([calendarMonths]); starší se dotahují, když se k nim dojede.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaCalendarSheet(
    calendar: MediaCalendarState,
    onPick: (messageId: Long) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        SheetTitle(stringResource(Res.string.profile_calendar), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        val months = remember(calendar.days) { calendarMonths(calendar.days) }
        val byDate = remember(calendar.days) { calendar.days.associateBy { it.date } }
        if (months.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                if (calendar.loading) LoadingIndicator()
            }
            return@ModalBottomSheet
        }
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(months, key = { _, month -> month.month.toString() }) { index, month ->
                // Měsíců na stránku přijde pár; dotahuje se, už když se ukáže předposlední.
                LoadMoreAtEnd(index, months.size, onLoadMore)
                CalendarMonthGrid(month, byDate, onPick)
            }
            if (calendar.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: CalendarMonth,
    byDate: Map<LocalDate, CalendarDayMedia>,
    onPick: (messageId: Long) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            monthTitle(month.month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    stringResource(day.shortName()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        month.cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (date != null) CalendarDay(date, byDate[date], onPick)
                    }
                }
            }
        }
    }
}

/**
 * Políčko dne. Den s médiem je kruh s náhledem první fotky a číslem dne přes ni, den bez média
 * jen tlumené číslo. Skryté médium (spoiler, citlivý obsah) náhled neukáže — kruh zůstane
 * tónovaný, jako by se ještě načítal; odkrývá se až v prohlížeči.
 */
@Composable
private fun CalendarDay(date: LocalDate, media: CalendarDayMedia?, onPick: (messageId: Long) -> Unit) {
    if (media == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                date.day.toString(),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EMPTY_DAY_ALPHA),
            )
        }
        return
    }
    val photo = media.message.media as? MessageMedia.Photo
    val video = media.message.media as? MessageMedia.Video
    val hidden = photo?.hasSpoiler == true || video?.hasSpoiler == true
    val thumbnail = (photo?.thumbnailFileId ?: video?.thumbnailFileId).takeUnless { hidden }
    val description = stringResource(
        Res.string.profile_calendar_day,
        stringResource(Res.string.date_day_month, date.day, stringResource(date.month.name())),
        pluralStringResource(Res.plurals.profile_calendar_day_items, media.count, media.count),
    )
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onPick(media.message.id) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            RemoteImage(
                fileId = thumbnail,
                placeholder = (photo?.minithumbnail ?: video?.minithumbnail).takeUnless { hidden },
                modifier = Modifier.fillMaxSize(),
            )
            // Bílé číslo na fotce potřebuje podklad — na světlé fotce by zaniklo.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = DAY_SCRIM_ALPHA)))
        }
        Text(
            date.day.toString(),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = if (thumbnail != null) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private const val DAYS_IN_WEEK = 7

/** Den bez médií je jen orientační; plnou barvu mají dny, na které jde ťuknout. */
private const val EMPTY_DAY_ALPHA = 0.6f

/** Ztmavení náhledu pod číslem dne. */
private const val DAY_SCRIM_ALPHA = 0.3f
