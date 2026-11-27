package eu.tintera.plume.features.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.PaneBody
import eu.tintera.plume.core.adaptive.paneScaffoldColor
import eu.tintera.plume.core.location.mapsUrl
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.MapRequest
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.LocalPanesSideBySide
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.StaticMap
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.profile.ProfileSubpageTopBar
import eu.tintera.plume.features.profile.profileSubpageScroll
import eu.tintera.plume.features.profile.profileSubpageScrollBehavior
import eu.tintera.plume.features.profile.rememberChatTitle
import eu.tintera.plume.features.statistics.StatSectionTitle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.chat_places_empty
import eu.tintera.plume.resources.chat_places_empty_title
import eu.tintera.plume.resources.chat_places_list
import eu.tintera.plume.resources.chat_places_live
import eu.tintera.plume.resources.chat_places_load_older
import eu.tintera.plume.resources.chat_places_local_note
import eu.tintera.plume.resources.chat_places_location
import eu.tintera.plume.resources.chat_places_older_failed
import eu.tintera.plume.resources.chat_places_overview
import eu.tintera.plume.resources.chat_places_overview_far
import eu.tintera.plume.resources.chat_places_scanning
import eu.tintera.plume.resources.chat_places_title
import eu.tintera.plume.resources.chat_places_whole_history
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.empty_state_error_title
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.location_on_filled_24dp
import eu.tintera.plume.resources.media_show_in_chat
import eu.tintera.plume.resources.sender_you
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * **Místa v chatu**: přehledová mapa se špendlíkem za každé místo (když se vejdou do jednoho
 * výřezu) a pod ní seznam — malá mapa, název nebo adresa, kdo a kdy. Ťuknutí na řádek otevře
 * mapy telefonu, ikona vedle skočí na zprávu v konverzaci.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatPlacesScreen(
    viewModel: ChatPlacesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val urlOpener = koinInject<UrlOpener>()
    // Vedle konverzace panel zůstane a konverzace se posune; stojí-li sám (telefon), skok by
    // proběhl pod ním — zavře se, jako kalendář zpráv.
    val sideBySide = LocalPanesSideBySide.current
    val subpageScroll = profileSubpageScrollBehavior()
    Scaffold(
        containerColor = paneScaffoldColor(),
        modifier = modifier.profileSubpageScroll(subpageScroll),
        topBar = {
            ProfileSubpageTopBar(
                title = stringResource(Res.string.chat_places_title),
                subtitle = rememberChatTitle(viewModel.chatId),
                onBack = onBack,
                scrollBehavior = subpageScroll,
            )
        },
    ) { scaffoldPadding ->
        PaneBody(scaffoldPadding) { padding ->
            val places = state.places
            val failure = state.failure
            when {
                failure != null -> IntroEmptyState(
                    icon = Res.drawable.location_on_24dp,
                    title = stringResource(Res.string.empty_state_error_title),
                    text = failure.resolve(),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    action = {
                        FilledTonalButton(onClick = viewModel::load, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.common_try_again))
                        }
                    },
                )
                places == null || (places.isEmpty() && (state.scanning || state.loadingOlder)) ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "scope") { ScopeHeader(state, places, viewModel::loadOlder) }
                    if (places.isEmpty()) {
                        item(key = "empty") {
                            IntroEmptyState(
                                icon = Res.drawable.location_on_24dp,
                                title = stringResource(Res.string.chat_places_empty_title),
                                text = stringResource(Res.string.chat_places_empty),
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    } else {
                        item(key = "overview-title") { StatSectionTitle(stringResource(Res.string.chat_places_overview)) }
                        item(key = "overview") { OverviewMap(places, viewModel.chatId, Modifier.padding(horizontal = 16.dp)) }
                        item(key = "list-title") { StatSectionTitle(stringResource(Res.string.chat_places_list)) }
                        itemsIndexed(places, key = { _, it -> it.id }) { index, message ->
                            PlaceRow(
                                message = message,
                                index = index,
                                count = places.size,
                                chatId = viewModel.chatId,
                                onOpenMap = { location -> urlOpener.open(mapsUrl(location.latitude, location.longitude)) },
                                onShowInChat = {
                                    viewModel.jumpToMessage(message.id)
                                    if (!sideBySide) onBack()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Kolik zpráv prošlo a odkud, a jak dostat víc (jako statistika chatu). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScopeHeader(state: ChatPlacesState, places: List<ChatMessage>, onLoadOlder: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
        if (state.reachedStart) {
            Text(
                pluralStringResource(Res.plurals.chat_places_whole_history, places.size, places.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state.scanning || state.loadingOlder -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(32.dp))
                Text(
                    stringResource(Res.string.chat_places_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            !state.reachedStart -> {
                Text(
                    stringResource(if (state.olderFailed) Res.string.chat_places_older_failed else Res.string.chat_places_local_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.olderFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onLoadOlder, shapes = ButtonDefaults.shapes(), enabled = state.canLoadOlder) {
                    Text(stringResource(Res.string.chat_places_load_older))
                }
            }
        }
    }
}

/**
 * Přehled: jedna mapa od Telegramu kolem středu všech míst, s přiblížením, do kterého se vejdou
 * ([fitMap]), a špendlíky promítnutými do obrázku ([pointOnMap]). Mapa se žádá PŘESNĚ na velikost,
 * ve které se kreslí — bez ořezu (`StaticMap` ořezává), jinak by špendlíky ujely. Když jsou místa
 * daleko od sebe (Telegram vzdálenější výřez než přiblížení 13 nevydá), řekne to věta místo mapy.
 */
@Composable
private fun OverviewMap(places: List<ChatMessage>, chatId: Long, modifier: Modifier = Modifier) {
    val points = places.mapNotNull { (it.media as? MessageMedia.Location)?.let { location -> location.latitude to location.longitude } }
        .distinct()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GROUP_CORNER))
            .background(MaterialTheme.colorScheme.groupRow),
    ) {
        val width = ((maxWidth.value / SIZE_STEP).roundToInt() * SIZE_STEP).coerceIn(MIN_SIDE, MAX_SIDE)
        val height = (width * OVERVIEW_RATIO).roundToInt().coerceIn(MIN_SIDE, MAX_SIDE)
        val fit = fitMap(points, width, height, margin = PIN_MARGIN)
        if (fit == null) {
            Text(
                stringResource(Res.string.chat_places_overview_far),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@BoxWithConstraints
        }
        val density = LocalDensity.current.density
        val request = MapRequest(
            latitude = fit.latitude,
            longitude = fit.longitude,
            zoom = fit.zoom,
            width = width,
            height = height,
            scale = density.roundToInt().coerceIn(1, 3),
            chatId = chatId,
        )
        val repository = koinInject<FileRepository>()
        val fileId by produceState<Int?>(initialValue = null, request) { value = repository.mapThumbnail(request) }
        Box(Modifier.size(width.dp, height.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest).align(Alignment.Center)) {
            fileId?.let { id ->
                RemoteImage(fileId = id, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
            val colors = MaterialTheme.colorScheme
            // Mapa je vždycky světlá — špendlík tmavší z dvojice, jako u `StaticMap`.
            val pin = if (colors.primary.luminance() < colors.inversePrimary.luminance()) colors.primary else colors.inversePrimary
            points.forEach { (lat, lon) ->
                val (x, y) = pointOnMap(lat, lon, fit, width, height).let { (x, y) -> clampToMap(x, y, width, height) }
                Icon(
                    painterResource(Res.drawable.location_on_filled_24dp),
                    contentDescription = null,
                    tint = pin,
                    // Hrot špendlíku (11/12 výšky ikony) na bodě.
                    modifier = Modifier.offset(x = (x - PIN / 2).dp, y = (y - PIN * 11 / 12).dp).size(PIN.dp),
                )
            }
        }
        Spacer(Modifier.height(height.dp))
    }
}

@Composable
private fun PlaceRow(
    message: ChatMessage,
    index: Int,
    count: Int,
    chatId: Long,
    onOpenMap: (MessageMedia.Location) -> Unit,
    onShowInChat: () -> Unit,
) {
    val location = message.media as? MessageMedia.Location ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .groupedItem(index, count, MaterialTheme.colorScheme.groupRow)
            .clickable { onOpenMap(location) }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StaticMap(
            latitude = location.latitude,
            longitude = location.longitude,
            chatId = chatId,
            pinSize = 20.dp,
            modifier = Modifier.size(64.dp).clip(ShapeDefaults.Medium),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val title = location.title?.takeIf { it.isNotBlank() }
                ?: stringResource(if (location.isLive) Res.string.chat_places_live else Res.string.chat_places_location)
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            location.address?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val sender = if (message.isOutgoing) stringResource(Res.string.sender_you) else message.senderName
            Text(
                listOfNotNull(sender?.takeIf { it.isNotBlank() }, message.timestamp.asDate()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val label = stringResource(Res.string.media_show_in_chat)
        WithTooltip(label) {
            IconButton(onClick = onShowInChat, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.chat_24dp), contentDescription = label)
            }
        }
    }
}

private const val SIZE_STEP = 8
private const val MIN_SIDE = 16
private const val MAX_SIDE = 1024
private const val OVERVIEW_RATIO = 0.6f

/** Okraj kolem krajních bodů, ať špendlík nepřečuhuje přes hranu mapy. */
private const val PIN_MARGIN = 32
private const val PIN = 28
