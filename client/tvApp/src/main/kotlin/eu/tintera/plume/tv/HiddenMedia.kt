package eu.tintera.plume.tv

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.Minithumbnail
import eu.tintera.plume.core.telegram.RevealedSpoilers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_self_destruct_photo
import eu.tintera.plume.resources.media_self_destruct_video
import eu.tintera.plume.resources.media_sensitive
import eu.tintera.plume.resources.media_spoiler
import eu.tintera.plume.resources.timer_24dp
import eu.tintera.plume.resources.tv_media_reveal_hint
import eu.tintera.plume.resources.tv_media_self_destruct
import eu.tintera.plume.resources.tv_media_self_destruct_hint
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Proč fotka nebo video ve zprávě není vidět (docs/poznatky/android-tv.md, „Spoilery, citlivý
 * obsah a mizející média").
 */
internal enum class MediaCover {
    /** Odesílatel médium skryl; OK na zprávě ho odkryje. */
    Spoiler,

    /** Citlivý obsah podle nastavení účtu; odkrývá se jako spoiler. */
    Sensitive,

    /** Mizející nebo tajné médium: televize ho neukazuje vůbec, ani po OK. */
    SelfDestruct,
}

/** Mizející nebo tajné médium (časovač, jednou zobrazit, tajný chat). */
internal val MessageMedia?.isSelfDestruct: Boolean
    get() = when (this) {
        is MessageMedia.Photo -> isSecret || selfDestructType != null
        is MessageMedia.Video -> isSecret || selfDestructType != null
        else -> false
    }

/**
 * Mizející zpráva: médium s časovačem, nebo jednorázová hlasovka a kolečko (ty nesou časovač
 * jen na zprávě, ne v médiu).
 */
internal val ChatMessage.isSelfDestruct: Boolean
    get() = selfDestructType != null || media.isSelfDestruct

/**
 * Spoiler od odesílatele nebo citlivý obsah. `core` vede citlivé médium (`hideSensitive`) i jako
 * spoiler; [isSensitive] tu je kvůli placenému médiu a pro jistotu, kdyby to přestalo platit.
 */
internal val MessageMedia?.hasSpoilerCover: Boolean
    get() = when (this) {
        is MessageMedia.Photo -> hasSpoiler || isSensitive
        is MessageMedia.Video -> hasSpoiler || isSensitive
        is MessageMedia.Animation -> hasSpoiler || isSensitive
        else -> false
    }

/** Rozmazaná zmenšenina, která přišla se zprávou (fotka, video, GIF). */
internal val MessageMedia?.minithumbnail: Minithumbnail?
    get() = when (this) {
        is MessageMedia.Photo -> minithumbnail
        is MessageMedia.Video -> minithumbnail
        is MessageMedia.Animation -> minithumbnail
        else -> null
    }

private val MessageMedia?.isSensitive: Boolean
    get() = when (this) {
        is MessageMedia.Photo -> isSensitive
        is MessageMedia.Video -> isSensitive
        is MessageMedia.Animation -> isSensitive
        else -> false
    }

/**
 * Smí se médium ukázat tam, kde ho uživatel neodkrývá (pozadí náhledu chatu, fronta videí po
 * sobě)? Mizející nikdy, se spoilerem ani citlivé taky ne — odkrytí je vědomá akce u zprávy.
 */
internal val MessageMedia?.showsUncovered: Boolean
    get() = !isSelfDestruct && !hasSpoilerCover

/** Id zpráv s odkrytým spoilerem; sdílené s celým během aplikace ([RevealedSpoilers] z `core`). */
@Composable
internal fun revealedSpoilers(): Set<Long> {
    val ids by koinInject<RevealedSpoilers>().revealed.collectAsState()
    return ids
}

/**
 * Čím je médium zprávy [messageId] zakryté, `null` = je vidět. Mizející vždycky, spoiler
 * a citlivé, dokud ho uživatel neodkryl.
 */
@Composable
internal fun mediaCover(messageId: Long, media: MessageMedia?, selfDestruct: Boolean = media.isSelfDestruct): MediaCover? = when {
    selfDestruct -> MediaCover.SelfDestruct
    !media.hasSpoilerCover -> null
    messageId in revealedSpoilers() -> null
    media.isSensitive -> MediaCover.Sensitive
    else -> MediaCover.Spoiler
}

/**
 * Zakrytí přes plochu média: rozmazaná zmenšenina ze zprávy ([minithumbnail], u mizejícího ani
 * ta ne) pod tmavým závojem a uprostřed proč je skryté. [hint]: pod štítkem i co dělat (stačí
 * tam, kde je místo — dlaždice alba ho nemá). Velikost dává volající přes [modifier].
 */
@Composable
internal fun MediaCoverBox(
    cover: MediaCover,
    minithumbnail: Minithumbnail?,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    hint: Boolean = true,
    iconSize: Dp = 32.dp,
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (cover != MediaCover.SelfDestruct && minithumbnail != null) BlurredMinithumbnail(minithumbnail)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_SCRIM)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = if (hint) 14.dp else 8.dp, vertical = 6.dp),
            ) {
                Icon(
                    painterResource(if (cover == MediaCover.SelfDestruct) Res.drawable.timer_24dp else Res.drawable.visibility_off_24dp),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize),
                )
                if (hint) {
                    Spacer(Modifier.width(8.dp))
                    Text(coverLabel(cover, isVideo), style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
            }
            if (hint) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (cover == MediaCover.SelfDestruct) Res.string.tv_media_self_destruct_hint else Res.string.tv_media_reveal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        }
    }
}

@Composable
private fun coverLabel(cover: MediaCover, isVideo: Boolean): String = stringResource(
    when (cover) {
        MediaCover.Spoiler -> Res.string.media_spoiler
        MediaCover.Sensitive -> Res.string.media_sensitive
        MediaCover.SelfDestruct -> if (isVideo) Res.string.media_self_destruct_video else Res.string.media_self_destruct_photo
    },
)

/**
 * Mizející hlasovka nebo kolečko: místo přehrávače jen oznámení, že se zobrazí v telefonu.
 * Přehrání na televizi by zprávu spotřebovalo (časovač běží od otevření).
 */
@Composable
internal fun SelfDestructNotice(content: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(Res.drawable.timer_24dp), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stringResource(Res.string.tv_media_self_destruct), style = MaterialTheme.typography.titleSmall, color = content)
            Text(stringResource(Res.string.tv_media_self_destruct_hint), style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
        }
    }
}

/**
 * Zmenšenina ze zprávy (kolem 40 px) přes celou plochu. `blur` rozostří až od Androidu 12, na
 * starší televizi se vypne a zbude zvětšený náhled o pár desítkách pixelů, který je rozmazaný
 * sám (stejná úvaha jako `BlurredPlaceholder` na telefonu).
 */
@Composable
private fun BlurredMinithumbnail(minithumbnail: Minithumbnail) {
    val image = remember(minithumbnail) {
        BitmapFactory.decodeByteArray(minithumbnail.jpeg, 0, minithumbnail.jpeg.size)?.asImageBitmap()
    } ?: return
    Image(
        image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(COVER_BLUR, BlurredEdgeTreatment.Rectangle),
    )
}

private val COVER_BLUR = 24.dp
private const val COVER_SCRIM = 0.35f
