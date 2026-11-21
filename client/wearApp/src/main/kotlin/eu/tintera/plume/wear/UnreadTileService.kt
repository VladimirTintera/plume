package eu.tintera.plume.wear

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.wear_tile_open
import eu.tintera.plume.resources.wear_tile_signed_out
import eu.tintera.plume.resources.wear_tile_unknown
import eu.tintera.plume.resources.wear_tile_unread_chats
import kotlinx.coroutines.Dispatchers

/**
 * Dlaždice s počtem nepřečtených chatů: velké číslo uprostřed, dole tlačítko, které otevře
 * aplikaci. Kreslí se ze stavu v souboru ([UnreadState.read]), obnovu si o změně počtu vyžádá
 * [UnreadCountPublisher] — proto žádná pravidelná obnova (`freshnessInterval`).
 *
 * Dlaždice není Compose: systém ji kreslí sám z popisu rozvržení (ProtoLayout, M3 pro dlaždice),
 * aplikace jen ten popis dodá.
 */
class UnreadTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val content = tileContent(UnreadState.read(this@UnreadTileService))
            Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(Timeline.fromLayoutElement(layout(requestParams.deviceConfiguration, content)))
                .build()
        }

    private fun layout(device: DeviceParameters, content: TileContent): LayoutElement =
        materialScope(this, device) {
            primaryLayout(
                titleSlot = { text(getString(R.string.app_name).layoutString) },
                mainSlot = { mainSlot(content) },
                bottomSlot = {
                    textEdgeButton(onClick = clickable(openApp(), id = "open")) {
                        text(content.open.layoutString)
                    }
                },
            )
        }

    private fun MaterialScope.mainSlot(content: TileContent): LayoutElement {
        val count = content.count ?: return text(content.label.layoutString, maxLines = 3)
        return column(
            text(
                count.toString().layoutString,
                typography = Typography.NUMERAL_EXTRA_LARGE,
                color = colorScheme.primary,
            ),
            text(content.label.layoutString, typography = Typography.TITLE_MEDIUM),
        )
    }

    private fun openApp(): ActionBuilders.LaunchAction =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build(),
            )
            .build()

    /** Texty dlaždice; překládají se předem, rozvržení se skládá mimo Compose Resources. */
    private class TileContent(val count: Int?, val label: String, val open: String)

    private suspend fun tileContent(state: UnreadState): TileContent {
        val open = getString(Res.string.wear_tile_open)
        return when (state) {
            is UnreadState.Chats ->
                TileContent(state.count, getPluralString(Res.plurals.wear_tile_unread_chats, state.count), open)
            UnreadState.LoggedOut -> TileContent(null, getString(Res.string.wear_tile_signed_out), open)
            UnreadState.Unknown -> TileContent(null, getString(Res.string.wear_tile_unknown), open)
        }
    }

    private companion object {
        /** Dlaždice nemá obrázky, verze prostředků se nemění. */
        const val RESOURCES_VERSION = "1"
    }
}
