package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

        Text(
            text = timestamp.asTimeOfDay(),
            // Časy stojí ve sloupci pod sebou; proporční číslice by je rozházely.
            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(TIME_COLUMN_WIDTH)
                // Čas čeká za pravým okrajem a přijíždí s posunem. Seznam ořezává na svoje
                // hranice, takže dokud se netáhne, není ho vidět.
                .graphicsLayer { translationX = TIME_COLUMN_WIDTH.toPx() - reveal() },
        )
    }
}

/**
 * Předěl „Nepřečtené zprávy".
 *
 * Akcentní barva, ne šedá jako u data: je to jediná věc na obrazovce, kterou uživatel po otevření
 * chatu opravdu hledá.
 */
@Composable
internal fun UnreadDivider(modifier: Modifier = Modifier) {
    CenteredNote(
        text = stringResource(Res.string.conversation_unread_divider),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.padding(top = DAY_DIVIDER_SPACING, bottom = DIVIDER_BOTTOM_SPACING),
    )
}

/**
 * Systémové oznámení o dění v chatu.
 *
 * Čas se u něj nevypisuje: u „někdo se připojil" je minuta k ničemu a řádek by jen zdvojnásobil.
 */
@Composable
internal fun ServiceMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    CenteredNote(text = message.text, modifier = modifier)
}

/**
 * Předěl dne.
 *
 * Bez něj se v delší historii nedá poznat, kde končí včerejšek — u zprávy se vypisují jen hodiny,
 * protože datum má nést právě tohle.
 *
 * Vypadá stejně jako systémové oznámení schválně: obojí je poznámka na okraji konverzace, ne
 * zpráva od někoho. Dvě různá pojetí téhož by seznam jen roztříštila.
 */
@Composable
internal fun DayDivider(timestamp: Int, modifier: Modifier = Modifier) {
    CenteredNote(
        // S časem první zprávy dne, jako v Google Zprávách („Včera · 21:00"). V bublině čas není,
        // takže tohle je vedle časových předělů jediné místo, kde je vidět bez tahu doleva.
        text = stringResource(Res.string.date_with_time, timestamp.asDayLabel(), timestamp.asTimeOfDay()),
        // Víc místa než u oznámení: předěl odděluje bloky, takže potřebuje kolem sebe vzduch.
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
 * Poznámka na střed konverzace.
 *
 * Malé písmo v `surfaceContainer` — nemá to soupeřit s bublinami, jen říct, co se mezi nimi děje.
 */
@Composable
internal fun CenteredNote(
    text: String,
    modifier: Modifier = Modifier,
    /** Nenulová jen u plovoucí varianty, která leží nad zprávami místo mezi nimi. */
    shadowElevation: Dp = 0.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(BUBBLE_CORNER),
            color = color,
            contentColor = contentColor,
            shadowElevation = shadowElevation,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Vzduch kolem předělu dne. Odděluje bloky, takže potřebuje víc než rozestup mezi zprávami. */
private val DAY_DIVIDER_SPACING = 6.dp

/**
 * Spodní vzduch předělu. Předěl je samostatná položka seznamu, takže mezi ním a zprávou pod ním
 * leží ještě [MESSAGE_SPACING] z `verticalArrangement` — o ten se tady ubírá, aby zpráva seděla
 * pod předělem stejně těsně, jako když byl předěl kreslený uvnitř její položky.
 */
private val DIVIDER_BOTTOM_SPACING = DAY_DIVIDER_SPACING - MESSAGE_SPACING
