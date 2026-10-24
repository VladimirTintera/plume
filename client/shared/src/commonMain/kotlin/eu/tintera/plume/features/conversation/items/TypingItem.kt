package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.resources.visibility_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

/**
 * „Píše…" v místě, kam zpráva naskočí: tvář toho, kdo píše, a skákající trojtečka. Tak to dělají
 * Google Zprávy; v hlavičce to dřív zabíralo místo dostupnosti a od konverzace bylo daleko.
 * Ostatní činnosti mají místo trojtečky vlastní ikonku ([activityIcon]).
 *
 * **Bez bubliny.** Je to informace o dění v chatu, ne zpráva — stejně jako předěly a servisní
 * oznámení, které se taky kreslí holé na ploše (viz [CenteredNote]).
 *
 * [face] je `null`, když se odesílatele nepodařilo zjistit; ve skupině pak zůstane místo po
 * tváři, aby trojtečka stála tam, kde začínají bubliny.
 */
internal data class TypingItem(
    val activity: ChatActivity,
    val face: TypingFace?,
    /** Skupina: tvář je stejně velká a stejně odsazená jako u zpráv, aby trojtečka lícovala s bublinami. */
    val inGroup: Boolean,
) : ConversationItem {
    override val key: Any get() = TYPING_KEY

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        TypingIndicator(
            activity = activity,
            face = face,
            inGroup = inGroup,
            modifier = Modifier
                .animateItem()
                // Při odkrývání časů stojí jako příchozí zprávy (viz RevealAnchor): je krátký
                // a u levého okraje, čas se vejde vedle.
                .gathered(key, scope),
        )
    }
}

/** Tvář toho, kdo píše. */
internal data class TypingFace(val id: Long, val title: String, val avatarFileId: Int?)

/** Klíč bloku. Jeden na konverzaci — při výměně toho, kdo píše, se jen překreslí obsah. */
internal const val TYPING_KEY = "typing"

@Composable
private fun TypingIndicator(
    activity: ChatActivity,
    face: TypingFace?,
    inGroup: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = activity.label()
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    /*
     * Naskočí od tváře (vlevo dole) s jemným přeskokem, jako nová zpráva (`arrival` v rámu).
     * Odchod obstará `animateItem` prolnutím — zpráva, která přijde, pak pružně naskočí na tomtéž
     * místě.
     */
    val scale = remember { Animatable(ARRIVAL_SCALE) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { scale.animateTo(1f, spec) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Je to nový blok (jiný odesílatel, nebo pauza), takže vzduch jako mezi skupinami.
            .padding(top = GROUP_SPACING)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(0f, 1f)
            }
            // Čtečka dostane jednu větu místo tváře a tří teček; změnu oznámí sama.
            .clearAndSetSemantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarSize = if (inGroup) SENDER_AVATAR_SIZE else PRIVATE_AVATAR_SIZE
        val avatarGap = if (inGroup) SENDER_AVATAR_SLOT - SENDER_AVATAR_SIZE else PRIVATE_AVATAR_GAP
        if (face != null) {
            Avatar(
                chatId = face.id,
                title = face.title,
                avatarFileId = face.avatarFileId,
                size = avatarSize,
                modifier = Modifier.padding(end = avatarGap),
            )
        } else {
            Spacer(Modifier.width(avatarSize + avatarGap))
        }

        // Trojtečka znamená „píše"; kdo nahrává hlasovku nebo kouká na náš efekt, nepíše nic.
        val icon = activityIcon(activity)
        when {
            activity is ChatActivity.WatchingAnimation -> BlinkingEye(color)
            icon != null -> BreathingIcon(icon, color)
            else -> BouncingDots(color)
        }

        // Trojtečka sama říká „píše". U ostatních činností ikonka napoví, slova upřesní
        // („natáčí video" a „posílá video" mají tutéž kameru).
        if (activity != ChatActivity.Typing) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Tři tečky, které postupně povyskočí a zase dosednou.
 *
 * Fáze se čte až ve vrstvě (`graphicsLayer`), takže animace nic nerekomponuje — překresluje se
 * jen trojice teček.
 */
@Composable
private fun BouncingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    val phase: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DOTS_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "typing-dots-phase",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING),
        verticalAlignment = Alignment.CenterVertically,
        // Místo na výskok, aby tečky při skoku nevyjely z řádku.
        modifier = Modifier.height(DOT_SIZE + DOT_JUMP * 2),
    ) {
        repeat(DOT_COUNT) { index ->
            Box(
                Modifier
                    .graphicsLayer {
                        val lift = dotLift(phase.value, index)
                        translationY = -lift * DOT_JUMP.toPx()
                        alpha = DOT_REST_ALPHA + (1f - DOT_REST_ALPHA) * lift
                    }
                    .size(DOT_SIZE)
                    .background(color, CircleShape),
            )
        }
    }
}

/**
 * Ikonka činnosti, nebo `null` pro trojtečku: u psaní, a u neznámé činnosti („něco chystá…"),
 * pro kterou žádný obrázek není. Oko u sledování kreslí [BlinkingEye].
 */
internal fun activityIcon(activity: ChatActivity): DrawableResource? = when (activity) {
    ChatActivity.Typing, ChatActivity.Other -> null
    ChatActivity.RecordingVoice -> Res.drawable.mic_24dp
    ChatActivity.RecordingVideo, ChatActivity.SendingVideo -> Res.drawable.videocam_24dp
    ChatActivity.SendingPhoto -> Res.drawable.image_24dp
    ChatActivity.SendingFile -> Res.drawable.description_24dp
    ChatActivity.ChoosingSticker -> Res.drawable.sticker_24dp
    is ChatActivity.WatchingAnimation -> Res.drawable.visibility_24dp
}

/**
 * Ikonka činnosti, která pomalu dýchá průhledností — jako trojtečka dává najevo, že se něco děje
 * právě teď, a neposkakuje, aby se nepletla s psaním.
 */
@Composable
private fun BreathingIcon(icon: DrawableResource, color: Color) {
    val transition = rememberInfiniteTransition(label = "activity-icon")
    val alpha: State<Float> = transition.animateFloat(
        initialValue = 1f,
        targetValue = ICON_REST_ALPHA,
        animationSpec = infiniteRepeatable(tween(ICON_BREATH_MS), RepeatMode.Reverse),
        label = "activity-icon-alpha",
    )

    ActivityIconSlot {
        Icon(
            painter = painterResource(icon),
            // Popis nese celý řádek (`clearAndSetSemantics` výš).
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(ICON_SIZE)
                .graphicsLayer { this.alpha = alpha.value },
        )
    }
}

/**
 * Ikonka stojí nejmíň tak vysoko jako trojtečka s výskokem a sama není vyšší než řádek popisku
 * vedle, takže výměna činnosti indikátorem nepohne.
 */
@Composable
private fun ActivityIconSlot(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = DOT_SIZE + DOT_JUMP * 2),
        content = { content() },
    )
}

/**
 * Oko u „sleduje animaci": stojí otevřené a jednou za periodu mrkne.
 *
 * Mrknutí je stažení na výšku ke středu; fázi čte až vrstva, stejně jako u teček.
 */
@Composable
private fun BlinkingEye(color: Color) {
    val transition = rememberInfiniteTransition(label = "watching-eye")
    val phase: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(EYE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "watching-eye-phase",
    )

    ActivityIconSlot {
        Icon(
            painter = painterResource(Res.drawable.visibility_24dp),
            // Popis nese celý řádek (`clearAndSetSemantics` výš).
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(ICON_SIZE)
                .graphicsLayer { scaleY = 1f - (1f - EYE_CLOSED_SCALE) * eyeClosure(phase.value) },
        )
    }
}

/**
 * Jak moc je oko ve fázi [phase] (0..1 za periodu) zavřené: 0 otevřené, 1 zavřené. Mrkne jen na
 * konci periody ([EYE_BLINK_SHARE]), jinak kouká.
 */
internal fun eyeClosure(phase: Float): Float {
    val t = phase - (1f - EYE_BLINK_SHARE)
    if (t <= 0f) return 0f
    return sin(PI * t / EYE_BLINK_SHARE).toFloat()
}

/**
 * Jak vysoko je tečka [index] ve fázi [phase] (0..1 za jednu periodu), jako podíl výskoku.
 *
 * Každá tečka skáče jen v části periody ([DOT_JUMP_SHARE]) a o kus později než ta před ní;
 * zbytek periody všechny stojí. Bez té pauzy by trojtečka vlnila bez ustání a působila neklidně.
 */
internal fun dotLift(phase: Float, index: Int): Float {
    val t = ((phase - index * DOT_STAGGER) % 1f + 1f) % 1f
    if (t >= DOT_JUMP_SHARE) return 0f
    return sin(PI * t / DOT_JUMP_SHARE).toFloat()
}

private const val DOT_COUNT = 3
private const val DOTS_PERIOD_MS = 1200

/** O kolik periody se každá další tečka opozdí. */
private const val DOT_STAGGER = 0.15f

/** Jakou část periody tečka skáče. */
private const val DOT_JUMP_SHARE = 0.4f

/** Průhlednost stojící tečky; při výskoku doroste do plné barvy. */
private const val DOT_REST_ALPHA = 0.45f

private val DOT_SIZE = 7.dp
private val DOT_SPACING = 4.dp
private val DOT_JUMP = 4.dp

private const val EYE_PERIOD_MS = 2400

/** Jakou část periody oko mrká. */
private const val EYE_BLINK_SHARE = 0.1f

/** Na kolik se oko při mrknutí stáhne — úplně zavřené by na okamžik zmizelo. */
private const val EYE_CLOSED_SCALE = 0.1f

/** Ikonka činnosti, velká jako řádek popisku (`labelMedium`). */
private val ICON_SIZE = 16.dp

/** Jak dlouho ikonka vydechne (a zase stejně dlouho nadechne). */
private const val ICON_BREATH_MS = 900

/** Průhlednost ikonky na dně výdechu. */
private const val ICON_REST_ALPHA = 0.45f

/** V soukromém chatu zprávy tvář nemají, takže je tu jen malá ikonka, ne tvář velikosti skupiny. */
private val PRIVATE_AVATAR_SIZE = 24.dp
private val PRIVATE_AVATAR_GAP = 8.dp

/** Z jakého měřítka indikátor naskočí — stejně jako nová zpráva. */
private const val ARRIVAL_SCALE = 0.85f
