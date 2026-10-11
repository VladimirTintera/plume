package eu.tintera.plume.core.calendar

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import java.io.File
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/**
 * Android: `ACTION_INSERT` otevře editor nové události v kalendáři, který si uživatel vybral
 * (bez oprávnění ke kalendáři — zapisuje kalendář, ne my). Kde žádný kalendář není, otevře se
 * soubor `.ics` (`ACTION_VIEW`, `text/calendar`) — převezme ho jiná aplikace, třeba pošta.
 */
actual class CalendarEvents(private val context: Context) {

    private val logger = Logger.withTag("CalendarEvents")

    actual fun add(event: CalendarEvent): Boolean {
        val zone = TimeZone.currentSystemDefault()
        val (begin, end) = event.span(zone)
        val insert = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, event.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.toEpochMilliseconds())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilliseconds())
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        event.place?.let { insert.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        return try {
            context.startActivity(insert)
            true
        } catch (e: ActivityNotFoundException) {
            logger.i(e) { "Kalendář pro novou událost není, zkouším soubor .ics" }
            openIcs(event, zone)
        }
    }

    private fun openIcs(event: CalendarEvent, zone: TimeZone): Boolean {
        val file = runCatching {
            // Stejný adresář jako kopie ke sdílení (`MediaSharer`), FileProvider ho už vydává.
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            val now = Clock.System.now()
            File(directory, "event.ics").apply {
                writeText(event.toIcs(zone, uid = "${now.toEpochMilliseconds()}@plume", stamp = now))
            }
        }.onFailure { logger.w(it) { "Soubor .ics se nepodařilo zapsat" } }.getOrNull() ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, ICS_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        view.clipData = ClipData.newRawUri(file.name, uri)
        return try {
            context.startActivity(view)
            true
        } catch (e: ActivityNotFoundException) {
            logger.w(e) { "Soubor .ics nemá kdo otevřít" }
            false
        }
    }

    private companion object {
        const val ICS_MIME = "text/calendar"
    }
}
