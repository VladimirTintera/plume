package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.telegram.platformDeviceModel
import eu.tintera.plume.core.telegram.platformSystemVersion
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.ask_question_confirm
import eu.tintera.plume.resources.ask_question_failed
import eu.tintera.plume.resources.ask_question_faq
import eu.tintera.plume.resources.ask_question_plume
import eu.tintera.plume.resources.ask_question_text
import eu.tintera.plume.resources.ask_question_title
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.bug_report_24dp
import eu.tintera.plume.resources.contact_support_24dp
import eu.tintera.plume.resources.help_24dp
import eu.tintera.plume.resources.settings_ask_question
import eu.tintera.plume.resources.settings_ask_question_summary
import eu.tintera.plume.resources.settings_call_diagnostics
import eu.tintera.plume.resources.settings_call_diagnostics_empty
import eu.tintera.plume.resources.settings_call_diagnostics_failed
import eu.tintera.plume.resources.settings_call_diagnostics_summary
import eu.tintera.plume.resources.settings_faq
import eu.tintera.plume.resources.settings_faq_summary
import eu.tintera.plume.resources.settings_help
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * „Pomoc" pod sekcemi Nastavení: chat s podporou Telegramu, jeho časté dotazy, odeslání deníku
 * hovorů a Co je nového.
 *
 * **„Zeptat se" napřed řekne, komu se píše**: podporu dělají dobrovolníci Telegramu (tak to
 * oficiální klienti říkají taky, s odkazem na časté dotazy) — a Plume je neoficiální aplikace,
 * takže s ní podpora Telegramu neporadí. Bez té věty by se tam uživatel ptal na chyby Plume.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HelpGroup(
    onOpenChat: (Long) -> Unit,
    /** Vedle seznamu je otevřené „Co je nového" — řádek je vybraný jako sekce. */
    whatsNewSelected: Boolean,
    onOpenWhatsNew: () -> Unit,
) {
    val chats = koinInject<ChatRepository>()
    val urlOpener = koinInject<UrlOpener>()
    val scope = rememberCoroutineScope()
    var asking by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    // Deník hovorů jen tam, kde hovory jsou (Android, desktop); `docs/poznatky/hovory.md`, „Deník hovorů".
    val koin = getKoin()
    val journal = remember(koin) { koin.getOrNull<CallJournal>() }
    val sharer = koinInject<MediaSharer>()
    val paths = koinInject<TelegramPaths>()
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
        Text(
            stringResource(Res.string.settings_help),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        SettingsListRow(
            icon = Res.drawable.contact_support_24dp,
            title = stringResource(Res.string.settings_ask_question),
            summary = stringResource(Res.string.settings_ask_question_summary),
            first = true,
            last = false,
            onClick = {
                failed = false
                asking = true
            },
        )
        SettingsListRow(
            icon = Res.drawable.help_24dp,
            title = stringResource(Res.string.settings_faq),
            summary = stringResource(Res.string.settings_faq_summary),
            first = false,
            last = false,
            onClick = { urlOpener.open(FAQ_URL) },
        )
        if (journal != null) {
            val hasCalls by journal.hasEntries.collectAsState()
            SettingsListRow(
                icon = Res.drawable.bug_report_24dp,
                title = stringResource(Res.string.settings_call_diagnostics),
                summary = stringResource(
                    when {
                        exportFailed -> Res.string.settings_call_diagnostics_failed
                        hasCalls -> Res.string.settings_call_diagnostics_summary
                        else -> Res.string.settings_call_diagnostics_empty
                    },
                ),
                first = false,
                last = false,
                onClick = {
                    if (!hasCalls || exporting) return@SettingsListRow
                    exporting = true
                    scope.launch {
                        val header = "Plume ${paths.applicationVersion}, $platformDeviceModel, $platformSystemVersion"
                        val path = journal.export(header)
                        exportFailed = path == null || !sharer.share(path, journal.exportFileName())
                        exporting = false
                    }
                },
            )
        }
        // Co je nového je čtení o aplikaci, ne nastavení — patří k pomoci, ne mezi sekce.
        SettingsListRow(
            icon = SettingsSection.WhatsNew.icon,
            title = stringResource(SettingsSection.WhatsNew.title),
            summary = stringResource(SettingsSection.WhatsNew.summary),
            first = false,
            last = true,
            selected = whatsNewSelected,
            onClick = onOpenWhatsNew,
        )
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { if (!busy) asking = false },
            title = { Text(stringResource(Res.string.ask_question_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.ask_question_text))
                    Text(stringResource(Res.string.ask_question_plume))
                    if (failed) Text(stringResource(Res.string.ask_question_failed), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        failed = false
                        scope.launch {
                            chats.supportChat()
                                .onSuccess { chatId ->
                                    asking = false
                                    onOpenChat(chatId)
                                }
                                .onFailure { failed = true }
                            busy = false
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    if (busy) LoadingIndicator(Modifier.size(24.dp)) else Text(stringResource(Res.string.ask_question_confirm))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { urlOpener.open(FAQ_URL) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.ask_question_faq)) }
                    TextButton(onClick = { asking = false }, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
                }
            },
        )
    }
}

/** Časté dotazy Telegramu; stránka si jazyk vybere sama podle prohlížeče. */
private const val FAQ_URL = "https://telegram.org/faq"
