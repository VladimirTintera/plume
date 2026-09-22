package eu.tintera.plume.features.chats

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.update.AppUpdateState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.system_update_24dp
import eu.tintera.plume.resources.update_action_later
import eu.tintera.plume.resources.update_action_restart
import eu.tintera.plume.resources.update_action_update
import eu.tintera.plume.resources.update_available_text
import eu.tintera.plume.resources.update_available_title
import eu.tintera.plume.resources.update_downloaded_text
import eu.tintera.plume.resources.update_downloaded_title
import eu.tintera.plume.resources.update_downloading_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Karta s aktualizací nahoře v seznamu chatů.
 *
 * Informuje, nic neblokuje: není to dialog ani snackbar, stojí v seznamu jako další blok se
 * stejným zaoblením jako skupiny chatů. `primaryContainer` ji od světlých řádků odliší, aniž by
 * křičela — sytou barvu nese jen tlačítko.
 *
 * **Syté prvky na kartě nesmí brát `primary` natvrdo** ([accentOn]). V paletě se zvýšeným
 * kontrastem je `primaryContainer` v tmavém režimu SVĚTLÝ, stejně jako `primary` — „Teď ne"
 * pak bylo bílé na světle modré a kolečko ikony i tlačítko s kartou splývaly. Když `primary`
 * na kartě vidět není, přebírá jeho roli `onPrimaryContainer` (a obsah na něm barvu karty).
 *
 * Jedna karta pro všechny tři stavy, s `animateContentSize`: nabídka → stahování → hotovo je
 * tatáž věc v čase, a výměna celé karty by v seznamu vypadala jako nová položka.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateCard(
    state: AppUpdateState,
    onUpdate: () -> Unit,
    onDismiss: (versionCode: Int) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = accentOn(colors.primaryContainer, colors.primary, colors.onPrimaryContainer)
    val onAccent = if (accent == colors.primary) colors.onPrimary else colors.primaryContainer
    val buttonColors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)

    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).background(accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.system_update_24dp),
                        contentDescription = null,
                        tint = onAccent,
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            when (state) {
                                is AppUpdateState.Downloading -> Res.string.update_downloading_title
                                is AppUpdateState.Downloaded -> Res.string.update_downloaded_title
                                else -> Res.string.update_available_title
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val text = when (state) {
                        is AppUpdateState.Available -> Res.string.update_available_text
                        is AppUpdateState.Downloaded -> Res.string.update_downloaded_text
                        else -> null
                    }
                    text?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            when (state) {
                is AppUpdateState.Available -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = { onDismiss(state.versionCode) },
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                    ) {
                        Text(stringResource(Res.string.update_action_later))
                    }
                    Button(onClick = onUpdate, colors = buttonColors) { Text(stringResource(Res.string.update_action_update)) }
                }

                // Průběh místo tlačítek: stahování nejde z karty zrušit (to umí jen obchod)
                // a nabízet „Teď ne" u něčeho, co už běží, by lhalo.
                is AppUpdateState.Downloading -> {
                    val progress = state.progress
                    val modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    if (progress == null) {
                        LinearWavyProgressIndicator(modifier, color = accent)
                    } else {
                        LinearWavyProgressIndicator(progress = { progress }, modifier = modifier, color = accent)
                    }
                }

                is AppUpdateState.Downloaded -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onRestart, colors = buttonColors) { Text(stringResource(Res.string.update_action_restart)) }
                }

                AppUpdateState.None -> Unit
            }
        }
    }
}

