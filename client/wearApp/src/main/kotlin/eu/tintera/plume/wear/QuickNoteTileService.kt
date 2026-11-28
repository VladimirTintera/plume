package eu.tintera.plume.wear

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.common.util.concurrent.ListenableFuture
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.wear_complication_note
import eu.tintera.plume.resources.wear_note_label
import eu.tintera.plume.resources.wear_tile_note_action
import eu.tintera.plume.resources.wear_tile_note_text
import eu.tintera.plume.resources.wear_tile_note_title
import kotlinx.coroutines.Dispatchers

/**
 * Dlaždice rychlé poznámky: krátká věta a dole tlačítko Nadiktovat, které otevře
 * [QuickNoteActivity] rovnou s diktováním. Obsah se nemění, obnovu nepotřebuje.
 */
class QuickNoteTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val title = getString(Res.string.wear_tile_note_title)
            val text = getString(Res.string.wear_tile_note_text)
            val action = getString(Res.string.wear_tile_note_action)
            Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(Timeline.fromLayoutElement(layout(requestParams.deviceConfiguration, title, text, action)))
                .build()
        }

    private fun layout(device: DeviceParameters, title: String, text: String, action: String): LayoutElement =
        materialScope(this, device, defaultColorScheme = WearColors.tile) {
            primaryLayout(
                titleSlot = { text(title.layoutString) },
                mainSlot = { text(text.layoutString, maxLines = 4, color = colorScheme.onSurface) },
                bottomSlot = {
                    textEdgeButton(onClick = clickable(openNote(), id = "note")) { text(action.layoutString) }
                },
            )
        }

    private fun openNote(): ActionBuilders.LaunchAction =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(QuickNoteActivity::class.java.name)
                    .build(),
            )
            .build()

    private companion object {
        /** Dlaždice nemá obrázky, verze prostředků se nemění. */
        const val RESOURCES_VERSION = "1"
    }
}

/**
 * Komplikace rychlé poznámky: ikona (a u krátkého textu „Pozn."), ťuknutí otevře diktování
 * ([QuickNoteActivity]). Jen zkratka bez dat — obsah se nemění, obnovu nepotřebuje
 * (`UPDATE_PERIOD_SECONDS` = 0 v manifestu).
 */
class QuickNoteComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        data(request.complicationType, getString(Res.string.wear_complication_note), getString(Res.string.wear_note_label))

    /** Náhled ve výběru komplikací; texty z prostředků se tu číst nedají (není suspend). */
    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, null, null)

    private fun data(type: ComplicationType, label: String?, description: String?): ComplicationData? {
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_quick_note)).build()
        val contentDescription = description?.let { PlainComplicationText.Builder(it).build() } ?: ComplicationText.EMPTY
        val tap = PendingIntent.getActivity(this, 0, QuickNoteActivity.intent(this), PendingIntent.FLAG_IMMUTABLE)
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(image, contentDescription).setTapAction(tap).build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(label.orEmpty()).build(), contentDescription)
                    .setMonochromaticImage(image)
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }
}
