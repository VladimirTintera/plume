package eu.tintera.plume.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.wear_complication_signed_out
import eu.tintera.plume.resources.wear_complication_unread

/**
 * Komplikace na ciferník: ikona a počet nepřečtených chatů, ťuknutí otevře aplikaci. Jen krátký
 * text (`SHORT_TEXT`) — do malého políčka ciferníku se vejde číslo, nic víc. Obnovu si vyžádá
 * [UnreadCountPublisher], pravidelná není (`UPDATE_PERIOD_SECONDS` = 0 v manifestu).
 */
class UnreadComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        return when (val state = UnreadState.read(this)) {
            is UnreadState.Chats ->
                shortText(state.count.toString(), getPluralString(Res.plurals.wear_complication_unread, state.count, state.count))
            // Bez přihlášení pomlčka: ikona zůstane vidět a ťuknutí vede k přihlášení.
            UnreadState.LoggedOut, UnreadState.Unknown -> shortText(NO_COUNT, getString(Res.string.wear_complication_signed_out))
        }
    }

    /** Náhled ve výběru komplikací; bez popisu, texty z prostředků se tu číst nedají (není suspend). */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) shortText(PREVIEW_COUNT, null) else null

    private fun shortText(text: String, description: String?): ComplicationData =
        ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(text).build(),
            description?.let { PlainComplicationText.Builder(it).build() } ?: ComplicationText.EMPTY,
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_complication_unread)).build(),
            )
            .setTapAction(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private companion object {
        const val NO_COUNT = "–"
        const val PREVIEW_COUNT = "3"
    }
}
