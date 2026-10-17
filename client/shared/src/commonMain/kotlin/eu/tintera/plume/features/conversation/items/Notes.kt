package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.ui.resolve
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.service_upgraded_open_old
import eu.tintera.plume.resources.service_upgraded_open_new
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_unread_divider
import eu.tintera.plume.resources.date_with_time
import org.jetbrains.compose.resources.stringResource

/**
 * Položka konverzace s časem, který se odkryje tahem doleva.
 *
 * Obsah se posouvá vlevo a v uvolněném pruhu vpravo se ukáže čas zprávy. Předěly dne se
 * neposouvají — jsou to nadpisy nad blokem, ne zprávy, a čas u nich nedává smysl.
 *
 * [reveal] je lambda, ne hodnota: posun se čte až při kreslení vrstvy, takže gesto překresluje
 * jen vrstvu s bublinou a nepřeskládává celou konverzaci.
 */
@Composable
internal fun RevealingTime(
    timestamp: Int,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.graphicsLayer { translationX = -reveal() }) { content() }

        /*
         * Čas čeká za pravým okrajem položky a přijíždí s posunem. Ořezává ho jeho vlastní pruh,
         * ne seznam: na široké ploše je položka jen sloupec uprostřed seznamu (okraj
         * v `contentPadding`, viz ConversationColumn), takže čas „za okrajem" ležel v okraji
         * vedle sloupce a byl vidět pořád — při okraji užším než pruh useknutý.
         */
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(TIME_COLUMN_WIDTH)
                .clipToBounds(),
        ) {
            Text(
                text = timestamp.asTimeOfDay(),
                // Časy stojí ve sloupci pod sebou; proporční číslice by je rozházely.
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = TIME_COLUMN_WIDTH.toPx() - reveal() },
            )
        }
    }
}

/**
 * Předěl „Nepřečtené zprávy".
 */
@Composable
internal fun UnreadDivider(modifier: Modifier = Modifier) {
    CenteredNote(
        text = stringResource(Res.string.conversation_unread_divider),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = DAY_DIVIDER_SPACING, bottom = DIVIDER_BOTTOM_SPACING),
    )
}

/**
 * Systémové oznámení o dění v chatu.
 *
 * Čas se u něj nevypisuje: u „někdo se připojil" je minuta k ničemu a řádek by jen zdvojnásobil.
 */
@Composable
internal fun ServiceMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onOpenChat: (Long) -> Unit = {},
    onJumpTo: (Long) -> Unit = {},
) {
    // Text oznámení je [ChatMessage.description] a překládá se až tady — převedená zpráva
    // žije v paměti déle než jazyk aplikace.
    val text = message.description?.resolve() ?: message.text
    val link = message.upgradeLink
    val target = message.serviceTargetMessageId
    if (link == null && target != null) {
        // Připnutí, změna ankety, konec soutěže, skóre: ťuknutí skočí na zprávu, o které
        // oznámení mluví. Odkaz pod textem by tu byl navíc — věta sama říká, o co jde.
        CenteredNote(
            text = text,
            // Odsazení dává poznámka sama, takže stojí přesně jako oznámení bez ťuknutí.
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onJumpTo(target) },
        )
        return
    }
    if (link == null) {
        CenteredNote(text = text, modifier = modifier)
        return
    }
    // Převod skupiny: dosavadní zprávy zůstaly ve staré skupině, a tohle je jediná cesta k nim
    // (a ze staré skupiny k nové). Ťuká se na celé oznámení, akce je pod ním jako odkaz.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onOpenChat(link.chatId) }
            .padding(vertical = 4.dp),
    ) {
        CenteredNote(text = text)
        Text(
            text = stringResource(if (link.toNewGroup) Res.string.service_upgraded_open_new else Res.string.service_upgraded_open_old),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Předěl dne.
 *
 * Bez něj se v delší historii nedá poznat, kde končí včerejšek — u zprávy se vypisují jen hodiny,
 * protože datum má nést právě tohle.
 */
@Composable
internal fun DayDivider(timestamp: Int, modifier: Modifier = Modifier) {
    CenteredNote(
        text = stringResource(Res.string.date_with_time, timestamp.asDayLabel(), timestamp.asTimeOfDay()),
        modifier = modifier.padding(top = DAY_DIVIDER_SPACING, bottom = DIVIDER_BOTTOM_SPACING),
    )
}

/**
 * Časový předěl uvnitř dne: nad zprávou, od které uplynula od předchozí aspoň
 * [TIME_DIVIDER_GAP_SECONDS] (viz `conversationItems`).
 *
 * V bublině čas není, takže bez tohohle se čas dal zjistit jen tahem doleva nebo v nabídce.
 * Google Zprávy to řeší stejně: souvislý rozhovor čas nepřerušuje, nová dávka zpráv po pauze
 * ho dostane. Vypadá jako předěl dne, jen nese samotné hodiny.
 */
@Composable
internal fun TimeDivider(timestamp: Int, modifier: Modifier = Modifier) {
    CenteredNote(
        text = timestamp.asTimeOfDay(),
        modifier = modifier.padding(top = DAY_DIVIDER_SPACING, bottom = DIVIDER_BOTTOM_SPACING),
    )
}

/**
 * Plovoucí odznak dne při scrollování nad zprávami.
 *
 * Má zaoblenou pilulku a stín, aby byl čitelný nad projíždějícím obsahem.
 */
@Composable
internal fun FloatingDayBadge(
    text: String,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(BUBBLE_CORNER)
    Surface(
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Box(Modifier.clip(shape).then(glass)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Informativní systémová poznámka na střed konverzace (předěly času, data, oznámení apod.).
 *
 * Zobrazuje se čistě textově bez pilulky v decentní barvě [color].
 */
@Composable
internal fun CenteredNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/** Vzduch kolem předělu dne. Odděluje bloky, takže potřebuje víc než rozestup mezi zprávami. */
private val DAY_DIVIDER_SPACING = 6.dp

/**
 * Spodní vzduch předělu. Předěl je samostatná položka seznamu, takže mezi ním a zprávou pod ním
 * leží ještě [MESSAGE_SPACING] z `verticalArrangement` — o ten se tady ubírá, aby zpráva seděla
 * pod předělem stejně těsně, jako když byl předěl kreslený uvnitř její položky.
 */
private val DIVIDER_BOTTOM_SPACING = DAY_DIVIDER_SPACING - MESSAGE_SPACING
