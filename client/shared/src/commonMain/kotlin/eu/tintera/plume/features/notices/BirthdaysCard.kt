package eu.tintera.plume.features.notices

import androidx.compose.foundation.background
import eu.tintera.plume.core.ui.GROUP_CORNER
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.BirthdayWhen
import eu.tintera.plume.core.telegram.CloseBirthday
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.birthday_today
import eu.tintera.plume.resources.birthday_today_age
import eu.tintera.plume.resources.birthday_tomorrow
import eu.tintera.plume.resources.birthday_tomorrow_age
import eu.tintera.plume.resources.birthday_yesterday
import eu.tintera.plume.resources.birthday_yesterday_age
import eu.tintera.plume.resources.birthdays_hide
import eu.tintera.plume.resources.birthdays_title
import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.resources.close_24dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Narozeniny kontaktů nahoře v hlavním seznamu: kdo měl včera, má dnes nebo bude mít zítra.
 * Ťuknutí na člověka otevře chat s ním, křížek kartu schová na 24 hodin (na serveru).
 *
 * `tertiaryContainer`, ne `secondaryContainer` doporučené akce: je to oslava, ne rada — a obě
 * karty můžou stát pod sebou.
 *
 * **Lidé stojí na vlastních dlaždicích v `surfaceContainerLowest`**, ne přímo na kartě: barvy
 * avatarů jsou container role včetně `tertiaryContainer`, takže avatar v barvě karty na ní
 * zmizel (ověřeno na emulátoru — z „PS" zůstala jen písmena).
 */
@Composable
fun BirthdaysCard(
    birthdays: List<CloseBirthday>,
    onOpen: (userId: Long) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = accentOn(colors.tertiaryContainer, colors.tertiary, colors.onTertiaryContainer)
    val onAccent = if (accent == colors.tertiary) colors.onTertiary else colors.tertiaryContainer
    // Den karty se počítá jednou za složení; přes půlnoc ji TDLib stejně pošle znovu.
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    // Dnešní napřed, pak zítřejší, včerejší nakonec — co ještě jde stihnout, je nahoře.
    val sorted = remember(birthdays, today) {
        birthdays.sortedBy { (it.birthdate.closeTo(today) ?: BirthdayWhen.Yesterday).order }
    }

    Surface(
        shape = RoundedCornerShape(CARD_CORNER),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.cake_24dp), contentDescription = null, tint = onAccent)
                }
                Text(
                    stringResource(Res.string.birthdays_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                IconButton(onClick = onHide, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.birthdays_hide))
                }
            }
            Column(Modifier.padding(top = 8.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sorted.forEachIndexed { index, birthday ->
                    BirthdayRow(birthday, today, shape = tileShape(index, sorted.size), onClick = { onOpen(birthday.userId) })
                }
            }
        }
    }
}

@Composable
private fun BirthdayRow(birthday: CloseBirthday, today: LocalDate, shape: Shape, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chatId = birthday.userId, title = birthday.name, avatarFileId = birthday.photoFileId, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                birthday.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                birthdayText(birthday, today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** „Dnes slaví 30. narozeniny", bez roku „Dnes má narozeniny". */
@Composable
private fun birthdayText(birthday: CloseBirthday, today: LocalDate): String {
    val age = birthday.birthdate.turningOn(today)
    return when (birthday.birthdate.closeTo(today)) {
        BirthdayWhen.Today, null ->
            if (age != null) stringResource(Res.string.birthday_today_age, age) else stringResource(Res.string.birthday_today)
        BirthdayWhen.Tomorrow ->
            if (age != null) stringResource(Res.string.birthday_tomorrow_age, age) else stringResource(Res.string.birthday_tomorrow)
        BirthdayWhen.Yesterday ->
            if (age != null) stringResource(Res.string.birthday_yesterday_age, age) else stringResource(Res.string.birthday_yesterday)
    }
}

private val BirthdayWhen.order: Int
    get() = when (this) {
        BirthdayWhen.Today -> 0
        BirthdayWhen.Tomorrow -> 1
        BirthdayWhen.Yesterday -> 2
    }

/** Krajní dlaždice zaoblené víc, vnitřní míň — jako skupiny řádků v seznamu chatů. */
private fun tileShape(index: Int, count: Int): Shape = RoundedCornerShape(
    topStart = if (index == 0) OUTER_CORNER else INNER_CORNER,
    topEnd = if (index == 0) OUTER_CORNER else INNER_CORNER,
    bottomStart = if (index == count - 1) OUTER_CORNER else INNER_CORNER,
    bottomEnd = if (index == count - 1) OUTER_CORNER else INNER_CORNER,
)

private val CARD_CORNER = GROUP_CORNER
private val OUTER_CORNER = 16.dp
private val INNER_CORNER = 4.dp
