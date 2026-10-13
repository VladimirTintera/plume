package eu.tintera.plume.features.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SuggestedAction
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.waitUiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.lightbulb_24dp
import eu.tintera.plume.resources.password_24dp
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.smartphone_24dp
import eu.tintera.plume.resources.suggestion_archive_action
import eu.tintera.plume.resources.suggestion_archive_text
import eu.tintera.plume.resources.suggestion_archive_title
import eu.tintera.plume.resources.suggestion_check_password_no
import eu.tintera.plume.resources.suggestion_check_password_text
import eu.tintera.plume.resources.suggestion_check_password_title
import eu.tintera.plume.resources.suggestion_check_password_yes
import eu.tintera.plume.resources.suggestion_check_phone_text
import eu.tintera.plume.resources.suggestion_check_phone_title
import eu.tintera.plume.resources.suggestion_check_phone_title_unknown
import eu.tintera.plume.resources.suggestion_check_phone_yes
import eu.tintera.plume.resources.suggestion_check_phone_change
import eu.tintera.plume.resources.suggestion_custom_action
import eu.tintera.plume.resources.suggestion_later
import eu.tintera.plume.resources.suggestion_login_email_action
import eu.tintera.plume.resources.suggestion_login_email_text
import eu.tintera.plume.resources.suggestion_login_email_title
import eu.tintera.plume.resources.suggestion_profile_photo_action
import eu.tintera.plume.resources.suggestion_profile_photo_text
import eu.tintera.plume.resources.suggestion_profile_photo_title
import eu.tintera.plume.resources.suggestion_set_password_action
import eu.tintera.plume.resources.suggestion_set_password_text
import eu.tintera.plume.resources.suggestion_set_password_text_delay
import eu.tintera.plume.resources.suggestion_set_password_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Doporučená akce od Telegramu nahoře v seznamu chatů — vždy jen jedna, první v pořadí. Tvar jako
 * ostatní karty nad seznamem, v `secondaryContainer`: je to rada, ne poplach (chybová role patří
 * zmrazení) ani novinka aplikace (`primaryContainer` má aktualizace).
 *
 * „Teď ne" akci schová na serveru (`hideSuggestedAction`), takže se neukáže ani v jiném klientovi.
 * Přihlašovací e-mail, který Telegram vyžaduje ([SuggestedAction.SetLoginEmail.canBeHidden]
 * `false`), schovat nejde — jen nastavit.
 */
@Composable
fun SuggestedActionCard(
    action: SuggestedAction,
    /** Telefon účtu pro „Je … pořád tvoje číslo?"; `null`, dokud není známý. */
    phone: String?,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = action.content(phone)
    val colors = MaterialTheme.colorScheme
    val accent = accentOn(colors.secondaryContainer, colors.secondary, colors.onSecondaryContainer)
    val onAccent = if (accent == colors.secondary) colors.onSecondary else colors.secondaryContainer

    Surface(
        shape = RoundedCornerShape(CARD_CORNER),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(content.icon), contentDescription = null, tint = onAccent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(content.title.resolve(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        content.text.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            if (content.primary != null || content.secondary != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    content.secondary?.let { label ->
                        TextButton(onClick = onSecondary, colors = ButtonDefaults.textButtonColors(contentColor = accent)) {
                            Text(stringResource(label))
                        }
                    }
                    content.primary?.let { label ->
                        Button(
                            onClick = onPrimary,
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                        ) {
                            Text(stringResource(label))
                        }
                    }
                }
            } else {
                // Bez tlačítek by spodní okraj (8 dp, počítaný s tlačítky) byl moc těsný.
                Box(Modifier.size(8.dp))
            }
        }
    }
}

/** Co karta ukazuje; [primary] a [secondary] jsou popisky tlačítek, `null` = tlačítko není. */
private data class SuggestionContent(
    val icon: DrawableResource,
    val title: UiText,
    val text: UiText,
    val primary: StringResource?,
    val secondary: StringResource?,
)

/**
 * Obsah karty podle akce. Co tlačítko udělá, rozhoduje seznam chatů (`onSuggestionPrimary`) —
 * tady jen to, jak vypadá. `when` bez `else`: nová akce musí dostat vlastní kartu.
 */
private fun SuggestedAction.content(phone: String?): SuggestionContent = when (this) {
    SuggestedAction.CheckPassword -> SuggestionContent(
        icon = Res.drawable.password_24dp,
        title = UiText(Res.string.suggestion_check_password_title),
        text = UiText(Res.string.suggestion_check_password_text),
        primary = Res.string.suggestion_check_password_yes,
        secondary = Res.string.suggestion_check_password_no,
    )
    SuggestedAction.CheckPhoneNumber -> SuggestionContent(
        icon = Res.drawable.smartphone_24dp,
        title = phone?.let { UiText(Res.string.suggestion_check_phone_title, it) }
            ?: UiText(Res.string.suggestion_check_phone_title_unknown),
        text = UiText(Res.string.suggestion_check_phone_text),
        primary = Res.string.suggestion_check_phone_yes,
        secondary = Res.string.suggestion_check_phone_change,
    )
    is SuggestedAction.SetPassword -> SuggestionContent(
        icon = Res.drawable.password_24dp,
        title = UiText(Res.string.suggestion_set_password_title),
        text = if (authorizationDelaySeconds > 0) {
            UiText(Res.string.suggestion_set_password_text_delay, waitUiText(authorizationDelaySeconds))
        } else {
            UiText(Res.string.suggestion_set_password_text)
        },
        primary = Res.string.suggestion_set_password_action,
        secondary = Res.string.suggestion_later,
    )
    is SuggestedAction.SetLoginEmail -> SuggestionContent(
        icon = Res.drawable.alternate_email_24dp,
        title = UiText(Res.string.suggestion_login_email_title),
        text = UiText(Res.string.suggestion_login_email_text),
        primary = Res.string.suggestion_login_email_action,
        secondary = Res.string.suggestion_later.takeIf { canBeHidden },
    )
    SuggestedAction.SetProfilePhoto -> SuggestionContent(
        icon = Res.drawable.person_24dp,
        title = UiText(Res.string.suggestion_profile_photo_title),
        text = UiText(Res.string.suggestion_profile_photo_text),
        primary = Res.string.suggestion_profile_photo_action,
        secondary = Res.string.suggestion_later,
    )
    SuggestedAction.ArchiveUnknownChats -> SuggestionContent(
        icon = Res.drawable.archive_24dp,
        title = UiText(Res.string.suggestion_archive_title),
        text = UiText(Res.string.suggestion_archive_text),
        primary = Res.string.suggestion_archive_action,
        secondary = Res.string.suggestion_later,
    )
    is SuggestedAction.Custom -> SuggestionContent(
        icon = Res.drawable.lightbulb_24dp,
        title = UiText.Raw(title),
        text = UiText.Raw(description),
        primary = Res.string.suggestion_custom_action.takeIf { url.isNotBlank() },
        secondary = Res.string.suggestion_later,
    )
}

private val CARD_CORNER = 20.dp
