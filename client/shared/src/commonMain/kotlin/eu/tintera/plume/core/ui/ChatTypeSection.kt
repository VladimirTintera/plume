package eu.tintera.plume.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.USERNAME_MAX_LENGTH
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_type_link_released
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.create_channel_private
import eu.tintera.plume.resources.create_channel_private_help
import eu.tintera.plume.resources.create_channel_public
import eu.tintera.plume.resources.create_channel_public_help
import eu.tintera.plume.resources.create_chat_link
import eu.tintera.plume.resources.create_chat_link_help
import eu.tintera.plume.resources.create_chat_link_prefix
import eu.tintera.plume.resources.create_chat_username_too_many
import eu.tintera.plume.resources.create_group_private
import eu.tintera.plume.resources.create_group_private_help
import eu.tintera.plume.resources.create_group_public
import eu.tintera.plume.resources.create_group_public_help
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Soukromý, nebo veřejný chat, při založení (`CreateChatScreen`) i v úpravě (`EditChatScreen`) —
 * spojená přepínací tlačítka jako filtry hledání, pod nimi věta,
 * co volba znamená, a u veřejného pole s odkazem. Jméno se kontroluje stejně jako vlastní
 * uživatelské jméno (`usernameMessage`): místní pravidla hned, dostupnost po pauze v psaní.
 */
@Composable
fun ChatTypeSection(
    channel: Boolean,
    isPublic: Boolean,
    onPublicChange: (Boolean) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    status: UsernameStatus,
    enabled: Boolean,
    /** Současné veřejné jméno upravovaného chatu; u nového prázdné. Beze změny se nic nehlásí. */
    original: String = "",
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            listOf(false, true).forEachIndexed { index, public ->
                val checked = public == isPublic
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onPublicChange(public) },
                    enabled = enabled,
                    shapes = if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    if (checked) {
                        Icon(
                            painterResource(Res.drawable.check_24dp),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(ButtonDefaults.IconSize),
                        )
                    }
                    Text(
                        stringResource(
                            when {
                                channel && public -> Res.string.create_channel_public
                                channel -> Res.string.create_channel_private
                                public -> Res.string.create_group_public
                                else -> Res.string.create_group_private
                            },
                        ),
                    )
                }
            }
        }
        TypeHint(
            stringResource(
                when {
                    channel && isPublic -> Res.string.create_channel_public_help
                    channel -> Res.string.create_channel_private_help
                    isPublic -> Res.string.create_group_public_help
                    else -> Res.string.create_group_private_help
                },
            ),
        )
        // Soukromý chat odkaz uvolní — a vrátit ho pak nemusí jít, když si ho mezitím vezme
        // někdo jiný. Řekne se to dřív, než se uloží.
        if (!isPublic && original.isNotEmpty()) {
            Text(
                stringResource(Res.string.chat_type_link_released, original),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
        // Pole se objeví jen u veřejného chatu, a s ním i místo — přepnutí na soukromý
        // rozepsané jméno nemaže, jen se nepošle.
        AnimatedVisibility(
            visible = isPublic,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            val message = usernameMessage(
                username = username.trim(),
                original = original,
                status = status,
                tooManyPublic = Res.string.create_chat_username_too_many,
            )
            OutlinedTextField(
                value = username,
                // Zavináč a adresa napsané ze zvyku se zahodí — prefix pole je `t.me/`.
                onValueChange = { onUsernameChange(it.removePrefix("@").removePrefix("t.me/").take(USERNAME_MAX_LENGTH)) },
                label = { Text(stringResource(Res.string.create_chat_link)) },
                prefix = { Text(stringResource(Res.string.create_chat_link_prefix)) },
                singleLine = true,
                enabled = enabled,
                isError = message?.second == true,
                supportingText = { Text(message?.first ?: stringResource(Res.string.create_chat_link_help)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TypeHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}
