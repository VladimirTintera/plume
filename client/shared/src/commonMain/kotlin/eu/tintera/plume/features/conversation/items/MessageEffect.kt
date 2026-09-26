package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.MessageEffect
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.StickerPlayback
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.message_effect
import eu.tintera.plume.resources.message_effect_play
import org.jetbrains.compose.resources.stringResource

/**
 * Přehrávání efektu jedné zprávy. Žije v položce seznamu, ne v rámu: dokud efekt hraje, dostane
 * položka vyšší `zIndex` (animace přesahuje zprávu a starší zprávy nad ní by ji jinak přikryly),
 * a ten se nastavuje na kořeni položky.
 */
@Stable
internal class EffectPlayback {
    /** Kolikáté přehrání; každé nové složí animaci znovu od začátku. */
    var run by mutableIntStateOf(0)
        private set

    var playing by mutableStateOf(false)
        private set

    fun play() {
        run++
        playing = true
    }

    fun finished() {
        playing = false
    }
}

/**
 * Značka efektu u zprávy: emoji efektu v kolečku, na VOLNÉ straně zprávy vedle spodku bubliny.
 * Ťuknutí efekt přehraje znovu — po příchodu hraje sám jen jednou.
 *
 * Vedle zprávy, ne pod ní: zpráva s efektem je stejně vysoká jako bez něj a animace z téhož
 * místa vybuchne do volného pruhu, kde nic nepřekrývá.
 */
@Composable
internal fun EffectMark(
    effect: MessageEffect,
    playback: EffectPlayback,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.message_effect, effect.emoji)
    val playLabel = stringResource(Res.string.message_effect_play)
    Box(modifier.size(EFFECT_MARK_SIZE), contentAlignment = Alignment.Center) {
        /*
         * Animace je DÍTĚ značky, ale do layoutu nezasahuje: měří se na svou velikost a hlásí
         * nulovou, takže zprávu nezvětší.
         *
         * **Zdroj výbuchu NENÍ ve středu plátna.** Animace je navržená pro odchozí zprávu:
         * zdroj leží asi ve třech čtvrtinách šířky a výbuch letí doleva přes konverzaci. Na
         * značku se proto kotví ten bod, ne střed, a u příchozí zprávy (značka vpravo od
         * bubliny) se animace zrcadlí, aby mířila od bubliny, ne přes ni. Nahoru přes sousední
         * zprávy efekt letí tak jako tak — i v oficiálních klientech.
         */
        if (playback.playing && effect.animationFileId != null) {
            key(playback.run) {
                AnimatedSticker(
                    fileId = effect.animationFileId,
                    contentDescription = null,
                    playback = StickerPlayback.Once,
                    onFinished = playback::finished,
                    placeholder = {},
                    modifier = Modifier
                        .layout { measurable, _ ->
                            val size = (if (effect.premium) PREMIUM_EFFECT_SIZE else EFFECT_SIZE).roundToPx()
                            val placeable = measurable.measure(Constraints.fixed(size, size))
                            val sourceX = (size * (if (isOutgoing) EFFECT_SOURCE_X else 1f - EFFECT_SOURCE_X)).toInt()
                            layout(0, 0) { placeable.place(-sourceX, -size / 2) }
                        }
                        .graphicsLayer { if (!isOutgoing) scaleX = -1f },
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .size(EFFECT_MARK_SIZE)
                .clip(CircleShape)
                .clickable(
                    enabled = effect.animationFileId != null,
                    onClickLabel = playLabel,
                    onClick = playback::play,
                )
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Výška řádku rovná písmu, jinak emoji v kolečku sedí níž (jako u čipu reakce).
                Text(effect.emoji, style = TextStyle(fontSize = 13.sp, lineHeight = 13.sp))
            }
        }
    }
}

/**
 * Přehraje se efekt zprávy sám? U zprávy, která přišla po otevření chatu ([openedAt] s tolerancí
 * na hodiny serveru), a u PŘÍCHOZÍ zprávy za hranicí nepřečtených — tu uživatel ještě neviděl,
 * takže efekt, se kterým ji odesílatel poslal, má vidět taky. Tak to dělají i oficiální klienti.
 * Přečtená historie efekt nepřehrává, jen značku; hranice je `null`, když chat nepřečtené neměl.
 */
internal fun effectAutoplays(
    timestamp: Int,
    openedAt: Long,
    isOutgoing: Boolean,
    messageId: Long,
    unreadBoundary: Long?,
): Boolean =
    timestamp >= openedAt - ARRIVAL_TOLERANCE_SECONDS ||
        (!isOutgoing && unreadBoundary != null && messageId > unreadBoundary)

/** O kolik starší zpráva ještě platí za „právě přišlou" — hodiny serveru a telefonu se liší. */
internal const val ARRIVAL_TOLERANCE_SECONDS = 5

/** Kolečko se značkou efektu. */
internal val EFFECT_MARK_SIZE = 24.dp

/** Mezera mezi bublinou a značkou. */
internal val EFFECT_MARK_GAP = 6.dp

/** Jak velká je animace efektu reakce kolem značky. */
private val EFFECT_SIZE = 240.dp

/** Kde na šířce plátna leží zdroj výbuchu (u odchozí zprávy; u příchozí zrcadlově). */
private const val EFFECT_SOURCE_X = 0.75f

/** Prémiová animace samolepky je navržená na větší plochu, samolepka v ní zabírá jen střed. */
private val PREMIUM_EFFECT_SIZE = 280.dp
