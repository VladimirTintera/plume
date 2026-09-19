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
import eu.tintera.plume.core.update.AppUpdateState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.update_action_later
import eu.tintera.plume.resources.update_action_restart
import eu.tintera.plume.resources.update_action_update
import eu.tintera.plume.resources.update_available_text
import eu.tintera.plume.resources.update_available_title
import eu.tintera.plume.resources.update_downloaded_text
import eu.tintera.plume.resources.update_downloaded_title
import eu.tintera.plume.resources.update_downloading_title
import eu.tintera.plume.resources.upgrade_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Karta s aktualizací nahoře v seznamu chatů.
 *
 * Informuje, nic neblokuje: není to dialog ani snackbar, stojí v seznamu jako další blok se
 * stejným zaoblením jako skupiny chatů. `primaryContainer` ji od světlých řádků odliší, aniž by
 * křičela — sytou barvu nese jen tlačítko.
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
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.upgrade_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
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
                    TextButton(onClick = { onDismiss(state.versionCode) }) {
                        Text(stringResource(Res.string.update_action_later))
                    }
                    Button(onClick = onUpdate) { Text(stringResource(Res.string.update_action_update)) }
                }

                // Průběh místo tlačítek: stahování nejde z karty zrušit (to umí jen obchod)
                // a nabízet „Teď ne" u něčeho, co už běží, by lhalo.
                is AppUpdateState.Downloading -> {
                    val progress = state.progress
                    val modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    if (progress == null) {
                        LinearWavyProgressIndicator(modifier)
                    } else {
                        LinearWavyProgressIndicator(progress = { progress }, modifier = modifier)
                    }
                }

                is AppUpdateState.Downloaded -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onRestart) { Text(stringResource(Res.string.update_action_restart)) }
                }

                AppUpdateState.None -> Unit
            }
        }
    }
}

