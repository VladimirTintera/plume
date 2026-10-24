package eu.tintera.plume.wear

import android.graphics.BitmapFactory
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.avatarButton
import androidx.wear.protolayout.material3.avatarImage
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.background
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.clip
import androidx.wear.protolayout.types.dp
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import eu.tintera.plume.core.ui.readPluralString
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.wear_chats_empty
import eu.tintera.plume.resources.wear_chats_title
import eu.tintera.plume.resources.wear_tile_all_chats
import eu.tintera.plume.resources.wear_tile_new_messages
import eu.tintera.plume.resources.wear_tile_signed_out
import eu.tintera.plume.resources.wear_tile_unknown
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Dlaždice s chaty ze začátku hlavního seznamu: fotka, název a pod ním počet nových zpráv, nebo
 * náhled poslední. Ťuknutí otevře rovnou konverzaci ([MainActivity.EXTRA_CHAT_ID]), tlačítko
 * dole seznam chatů. Kreslí se ze souboru ([RecentChatsState]), obnovu si vyžádá
 * [RecentChatsPublisher].
 *
 * **Fotky dodává [onTileResourcesRequest] sám, ne automatická registrace** (`Material3TileService`
 * / `ProtoLayoutScope`): s rendererem 1.300 (emulátor Wear OS 5) prostředky ze scope do balíčku
 * nedorazily — „Resource avatar-… is not defined in resources bundle" a místo fotky prázdno,
 * knihovna sama nic nezalogovala. Verze prostředků je otisk miniatur, takže si je systém vyžádá
 * znovu jen se změnou fotky.
 */
class RecentChatsTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val state = RecentChatsState.read(this@RecentChatsTileService)
            val content = tileContent(state, rowsFor(requestParams.deviceConfiguration))
            Tile.Builder()
                .setResourcesVersion(resourcesVersion(state))
                .setTileTimeline(Timeline.fromLayoutElement(layout(requestParams.deviceConfiguration, content)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
            val resources = Resources.Builder().setVersion(requestParams.version)
            val chats = (RecentChatsState.read(this@RecentChatsTileService) as? RecentChatsState.Chats)?.chats.orEmpty()
            if (chats.any { it.savedMessages }) {
                resources.addIdToImageMapping(
                    SAVED_MESSAGES_ICON,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(R.drawable.ic_saved_messages).build(),
                        )
                        .build(),
                )
            }
            chats.forEach { chat ->
                val path = chat.avatarPath ?: return@forEach
                val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: return@forEach
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) return@forEach
                resources.addIdToImageMapping(
                    avatarId(path),
                    ResourceBuilders.ImageResource.Builder()
                        .setInlineResource(
                            // Formát neurčený = komprimovaný obrázek (PNG), renderer si ho dekóduje.
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(bytes)
                                .setWidthPx(bounds.outWidth)
                                .setHeightPx(bounds.outHeight)
                                .build(),
                        )
                        .build(),
                )
            }
            resources.build()
        }

    private fun layout(device: DeviceParameters, content: TileContent): LayoutElement =
        materialScope(this, device) {
            primaryLayout(
                titleSlot = { text(content.title.layoutString) },
                mainSlot = {
                    if (content.rows.isEmpty()) text(content.message.orEmpty().layoutString, maxLines = 3)
                    else chatColumn(content.rows)
                },
                bottomSlot = {
                    textEdgeButton(onClick = clickable(open(chatId = null), id = "all")) { text(content.all.layoutString) }
                },
            )
        }

    private fun MaterialScope.chatColumn(rows: List<Row>): LayoutElement {
        val elements = rows.flatMapIndexed { index, row ->
            val button = avatarButton(
                onClick = clickable(open(row.chat.id), id = "chat-${row.chat.id}"),
                labelContent = { text(row.title.layoutString, maxLines = 1) },
                secondaryLabelContent = row.secondary?.let { line -> { text(line.layoutString, maxLines = 1) } },
                avatarContent = { avatar(row.chat) },
            )
            if (index == 0) listOf(button) else listOf(spacer(height = ROW_GAP_DP.dp), button)
        }
        // Tlačítka mají šířku `expand`: sloupec s výchozím `wrap` nejde změřit a renderer ho
        // zahodí celý („Column set to wrap but contents are unmeasurable").
        return column(*elements.toTypedArray(), width = expand())
    }

    /** Miniatura z prostředků dlaždice, a když není (chat bez fotky, nestažená), iniciála na barvách motivu. */
    @Suppress("DEPRECATION") // Varianta s ImageResource chce ProtoLayoutScope, viz KDoc třídy.
    private fun MaterialScope.avatar(chat: RecentChat): LayoutElement {
        chat.avatarPath?.takeIf { File(it).exists() }?.let { return avatarImage(avatarId(it)) }
        // Rozměr dává tlačítko (obal avataru), iniciála nebo záložka ho jen vyplní.
        val content = if (chat.savedMessages) {
            icon(SAVED_MESSAGES_ICON, tintColor = colorScheme.onPrimaryContainer)
        } else {
            val initial = chat.title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()
            text(initial.layoutString, typography = Typography.LABEL_MEDIUM, color = colorScheme.onPrimaryContainer)
        }
        return box(
            content,
            width = expand(),
            height = expand(),
            modifier = LayoutModifier.background(colorScheme.primaryContainer).clip(shapes.full),
        )
    }

    private class Row(val chat: RecentChat, val title: String, val secondary: String?)

    /** Texty dlaždice; překládají se předem, rozvržení se skládá mimo Compose Resources. */
    private class TileContent(val title: String, val all: String, val rows: List<Row>, val message: String?)

    private suspend fun tileContent(state: RecentChatsState, maxRows: Int): TileContent {
        val savedTitle = readString(Res.string.saved_messages)
        val rows = (state as? RecentChatsState.Chats)?.chats?.take(maxRows)?.map { chat ->
            Row(chat, if (chat.savedMessages) savedTitle else chat.title, chat.unread.takeIf { it > 0 }?.let { readPluralString(Res.plurals.wear_tile_new_messages, it, it) } ?: chat.preview)
        }.orEmpty()
        val message = when (state) {
            RecentChatsState.Unknown -> readString(Res.string.wear_tile_unknown)
            RecentChatsState.LoggedOut -> readString(Res.string.wear_tile_signed_out)
            is RecentChatsState.Chats -> readString(Res.string.wear_chats_empty).takeIf { rows.isEmpty() }
        }
        return TileContent(readString(Res.string.wear_chats_title), readString(Res.string.wear_tile_all_chats), rows, message)
    }

    /** Na malém displeji se tři řádky s tlačítkem dole nevejdou. */
    private fun rowsFor(device: DeviceParameters): Int = if (device.screenHeightDp >= LARGE_SCREEN_DP) 3 else 2

    private fun open(chatId: Long?): ActionBuilders.LaunchAction =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .apply {
                        if (chatId != null) {
                            addKeyToExtraMapping(MainActivity.EXTRA_CHAT_ID, ActionBuilders.AndroidLongExtra.Builder().setValue(chatId).build())
                        }
                    }
                    .build(),
            )
            .build()

    private companion object {
        const val ROW_GAP_DP = 4f
        const val LARGE_SCREEN_DP = 260

        /** Jméno prostředku podle souboru (fileId): nová fotka chatu je nový prostředek. */
        fun avatarId(path: String) = "avatar-${File(path).nameWithoutExtension}"

        /** Záložka Uložených zpráv mezi prostředky dlaždice (`R.drawable.ic_saved_messages`). */
        const val SAVED_MESSAGES_ICON = "saved-messages"

        fun resourcesVersion(state: RecentChatsState): String =
            (state as? RecentChatsState.Chats)?.chats
                ?.mapNotNull { chat -> if (chat.savedMessages) SAVED_MESSAGES_ICON else chat.avatarPath?.let(::avatarId) }
                ?.joinToString(",")?.hashCode()?.toString() ?: "0"
    }
}
