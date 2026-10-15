package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.StickerPlayback
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.pulse
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.animated_emoji_play
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Velký efekt kolem animovaného emoji, který vidí obě strany: buď jsme na emoji ťukli my, nebo
 * protějšek ([fromPartner]). [run] rozlišuje dvě ťuknutí na tutéž zprávu — každé složí animaci
 * znovu od začátku.
 */
@Immutable
internal data class EmojiBurst(
    val messageId: Long,
    val run: Long,
    val animationFileId: Int,
    val fromPartner: Boolean,
)

/**
 * Obsluha efektů animovaných emoji pro zprávy v seznamu (viz `ConversationEmojiBursts`).
 * `null` místo ní znamená emoji, které na ťuknutí nereaguje — kopie v nabídce nad zprávou.
 */
@Stable
internal interface EmojiBursts {
    /** Efekt, který čeká na zprávu [messageId]; skládací, tok efektů čte až položka. */
    @Composable
    fun burst(messageId: Long): EmojiBurst?

    fun tap(messageId: Long)

    /** Zpráva si efekt vyzvedla a přehrává ho. */
    fun shown(burst: EmojiBurst)
}

/**
 * Animované emoji.
 *
 * Než se animace načte (a když se načíst nepodaří), stojí na jejím místě tentýž znak velkým písmem,
 * jak se emoji kreslilo dřív — přechod na animaci je tak výměna obrázku za obrázek, ne naskočení
 * z prázdna. Velikost místa je pevná, aby se bublina při výměně nepřeskládala.
 *
 * **Ťuknutí pošle efekt i protějšku** ([bursts]): emoji hned poskočí a ozve se hmatový tep, velký
 * efekt kolem něj vybuchne, až ho TDLib vydá. Stejně se přehraje efekt, na který ťukl protějšek —
 * i s tepem, protože je to gesto člověka mířené na nás a přehrává se před očima.
 */
@Composable
internal fun AnimatedEmojiImage(
    emoji: MessageMedia.AnimatedEmoji,
    messageId: Long,
    bursts: EmojiBursts?,
    isOutgoing: Boolean,
    onRequestMenu: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val coroutines = rememberCoroutineScope()
    val jump = remember { Animatable(1f) }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    // Odezva gesta: tep a poskočení. Mimo efekt položky — ten se po vyzvednutí efektu zruší.
    val respond = remember(coroutines, haptics, spring) {
        {
            coroutines.launch { haptics.pulse() }
            coroutines.jump(jump, spring)
        }
    }
    var playing by remember { mutableStateOf<EmojiBurst?>(null) }
    val raise = LocalRaiseItem.current

    val waiting = bursts?.burst(messageId)
    LaunchedEffect(waiting) {
        val burst = waiting ?: return@LaunchedEffect
        bursts?.shown(burst)
        playing = burst
        // Efekt přesahuje zprávu; novější zprávy pod ní by ho jinak přikryly.
        raise(true)
        // Vlastní ťuknutí odpovědělo už při ťuknutí.
        if (burst.fromPartner) respond()
    }

    val finish = { burst: EmojiBurst ->
        if (playing == burst) {
            playing = null
            raise(false)
        }
    }
    /*
     * Efekt je třikrát větší než emoji a obsah zprávy je ořezaný jejím obrysem — na místě by se
     * uřízl. Kreslí se proto ve vrstvě rámu ([MessageOverlay]), ukotvený ve středu emoji.
     */
    val overlay = LocalMessageOverlay.current
    // Umístění emoji; kotva se z něj počítá až při přehrání, to už je rám i emoji umístěné.
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val current = playing
    if (overlay != null && current != null) {
        DisposableEffect(overlay, current, isOutgoing) {
            val coordinates = placed[0]
            val at = coordinates?.let { overlay.anchorOf(it, Offset(it.size.width / 2f, it.size.height / 2f)) }
            val drawing: @Composable () -> Unit = {
                EmojiBurstAnimation(burst = current, isOutgoing = isOutgoing, onFinished = { finish(current) })
            }
            if (at != null) overlay.show(at, drawing) else finish(current)
            onDispose { overlay.hide(drawing) }
        }
    }

    val playLabel = stringResource(Res.string.animated_emoji_play)
    Box(
        modifier = Modifier
            .size(ANIMATED_EMOJI_SIZE)
            .onPlaced { placed[0] = it }
            .then(
                if (bursts == null) {
                    Modifier
                } else {
                    // Bez vlnky, jako samolepka: průhledný tvar a obdélníková vlnka přes něj by
                    // vypadala jako chyba. Dlouhý stisk se opakuje — potomek s vlastním klikáním
                    // ho k zprávě nepustí.
                    Modifier.contextClickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = playLabel,
                        onLongClick = onRequestMenu,
                        onClick = {
                            respond()
                            bursts.tap(messageId)
                        },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedSticker(
            fileId = emoji.animationFileId,
            contentDescription = emoji.emoji,
            modifier = Modifier.matchParentSize().graphicsLayer {
                scaleX = jump.value
                scaleY = jump.value
            },
            placeholder = {
                Text(text = emoji.emoji, style = MaterialTheme.typography.displayLarge)
            },
        )
        val burst = playing
        if (burst != null && overlay == null) {
            // Mimo rám zprávy (náhledy) není kam přesahovat; kreslí se na místě.
            EmojiBurstAnimation(burst = burst, isOutgoing = isOutgoing, onFinished = { finish(burst) })
        }
    }
}

/** Emoji poskočí: rychle se zvětší a pružně dosedne zpátky. */
private fun CoroutineScope.jump(
    jump: Animatable<Float, *>,
    spring: FiniteAnimationSpec<Float>,
) = launch {
    jump.animateTo(JUMP_SCALE, spring)
    jump.animateTo(1f, spring)
}

/**
 * Velká animace kolem emoji. Plochu nezabírá (měří se na nulu), stojí ve středu emoji.
 *
 * **Plátno je třikrát větší než emoji a emoji v něm leží u kraje**, efekt letí přes zbytek —
 * navržené je, stejně jako efekt zprávy, pro ODCHOZÍ zprávu: emoji vpravo, výbuch doleva do
 * volného pruhu. U příchozí zprávy se plátno přiloží levým krajem a zrcadlí, aby mířilo od
 * kraje obrazovky. Tak to kreslí i oficiální klienti (Telegram Desktop: plátno 3× emoji,
 * svisle na střed).
 */
@Composable
private fun EmojiBurstAnimation(burst: EmojiBurst, isOutgoing: Boolean, onFinished: () -> Unit) {
    key(burst.run) {
        AnimatedSticker(
            fileId = burst.animationFileId,
            contentDescription = null,
            playback = StickerPlayback.Once,
            onFinished = onFinished,
            placeholder = {},
            modifier = Modifier
                .layout { measurable, _ ->
                    val emojiSize = ANIMATED_EMOJI_SIZE.roundToPx()
                    val size = emojiSize * BURST_SIZE_MULTIPLIER
                    val placeable = measurable.measure(Constraints.fixed(size, size))
                    val x = if (isOutgoing) emojiSize / 2 - size else -emojiSize / 2
                    layout(0, 0) { placeable.place(x, -size / 2) }
                }
                .graphicsLayer { if (!isOutgoing) scaleX = -1f },
        )
    }
}

/** Animované emoji v konverzaci. */
internal val ANIMATED_EMOJI_SIZE = 112.dp

/** Kolikrát je plátno efektu větší než emoji. */
private const val BURST_SIZE_MULTIPLIER = 3

/** Jak moc emoji při ťuknutí poskočí. */
private const val JUMP_SCALE = 1.15f
