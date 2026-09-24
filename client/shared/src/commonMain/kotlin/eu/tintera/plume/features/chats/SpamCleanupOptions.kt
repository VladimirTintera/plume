package eu.tintera.plume.features.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import eu.tintera.plume.resources.chats_delete_title
import eu.tintera.plume.resources.chats_leave_title
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_delete
import eu.tintera.plume.resources.chats_action_leave
import eu.tintera.plume.resources.chats_delete_block
import eu.tintera.plume.resources.chats_delete_body
import eu.tintera.plume.resources.chats_delete_report
import eu.tintera.plume.resources.chats_delete_report_reason
import eu.tintera.plume.resources.chats_leave_body
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.logout_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/** Zaškrtávací volba v podmenu; [onChange] dostane novou hodnotu. */
class CleanupToggle(val label: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

/**
 * Podmenu nevratné akce uvnitř sheetu: věta s následkem, zaškrtávací volby a tlačítko, které
 * akci provede. Nahrazuje potvrzovací dialog — potvrzením je to, že uživatel podmenu rozbalil
 * a ťukl na tlačítko v něm.
 *
 * **Proč ne dialog:** dialog nad sheetem zavřel nabídku, ze které vyšel, a volby v něm stály
 * mimo to, čeho se týkaly. V podmenu je vidět nad sebou chat, co se s ním stane i čím to
 * odstartovat — stejně jako doby ztlumení rozbalené pod „Ztlumit".
 *
 * Tvar je tentýž jazyk jako položky nad ním: malá zaoblení uvnitř, velké jen dole u tlačítka
 * (a nahoře u první položky, když podmenu stojí samo — [roundTop]).
 */
@Composable
fun CleanupOptions(
    body: String,
    toggles: List<CleanupToggle>,
    confirmLabel: String,
    confirmIcon: DrawableResource,
    onConfirm: () -> Unit,
    roundTop: Boolean = false,
) {
    val top = if (roundTop) 20.dp else 4.dp
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = 4.dp, bottomEnd = 4.dp),
            // Tónovaná plocha o stupeň níž: podmenu patří k položce nad ním, jako doby ztlumení.
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
        toggles.forEach { toggle ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                /*
                 * Klikání nese celý řádek a zaškrtávátko má `onCheckedChange = null`: do malého
                 * kolečka se nemá trefovat a čtečka obrazovky má číst jednu položku, ne dvě.
                 */
                Row(
                    modifier = Modifier
                        .toggleable(value = toggle.checked, role = Role.Checkbox, onValueChange = toggle.onChange)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(
                        checked = toggle.checked,
                        onCheckedChange = null,
                        modifier = Modifier.padding(12.dp),
                    )
                    Text(toggle.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        SheetActionRow(
            icon = confirmIcon,
            label = confirmLabel,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
            // Plná chybová barva: tohle je to jediné tlačítko, po kterém už není cesta zpátky.
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            onClick = onConfirm,
        )
    }
}

/**
 * Podmenu smazání chatu: s ním jde zablokovat a nahlásit, protože spam se řeší všemi třemi
 * naráz. Nabídka nad chatem i profil kreslí totéž — dvě podoby téhož by se rozešly.
 *
 * - **Zablokovat** jen soukromý chat s někým, kdo ještě blokovaný není.
 * - **Nahlásit** kdekoli, kde to server dovolí (`canBeReported`, nebo spamový action bar). Kde
 *   ho vezme rovnou jako spam, říká popisek „Nahlásit spam"; jinak se průvodce zeptá na důvod,
 *   a popisek to musí říct dopředu — jinak by po „Smazat" vyskočila otázka, se kterou nikdo
 *   nepočítal.
 *
 * Volby jsou výchozí vypnuté: zablokovat někoho omylem při mazání chatu je horší než ťuknutí navíc.
 */
@Composable
fun DeleteChatOptions(
    isPrivate: Boolean,
    title: String,
    spam: SpamActions?,
    onConfirm: (SpamCleanup) -> Unit,
    roundTop: Boolean = false,
) {
    val canBlock = spam?.canBlock == true && !spam.isBlocked
    val canReport = spam?.canReport == true
    var block by rememberSaveable { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf(false) }

    CleanupOptions(
        body = stringResource(if (isPrivate) Res.string.chats_delete_body else Res.string.chats_leave_body, title),
        toggles = listOfNotNull(
            CleanupToggle(stringResource(Res.string.chats_delete_block), block) { block = it }.takeIf { canBlock },
            CleanupToggle(
                label = stringResource(
                    if (spam?.reportsSpamDirectly == true) Res.string.chats_delete_report else Res.string.chats_delete_report_reason,
                ),
                checked = report,
                onChange = { report = it },
            ).takeIf { canReport },
        ),
        confirmLabel = stringResource(if (isPrivate) Res.string.chats_action_delete else Res.string.chats_action_leave),
        confirmIcon = if (isPrivate) Res.drawable.delete_24dp else Res.drawable.logout_24dp,
        onConfirm = { onConfirm(SpamCleanup(report = canReport && report, block = canBlock && block)) },
        roundTop = roundTop,
    )
}

/**
 * Podmenu smazání jako samostatný sheet — pro profil, kde nabídka ⋮ je obyčejné menu a podmenu
 * se do ní rozbalit nedá. Obsah je tentýž jako v nabídce nad chatem ([DeleteChatOptions]).
 */
@Composable
fun ChatDeleteSheet(
    isPrivate: Boolean,
    title: String,
    spam: SpamActions?,
    onConfirm: (SpamCleanup) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(if (isPrivate) Res.string.chats_delete_title else Res.string.chats_leave_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp),
            )
            DeleteChatOptions(isPrivate = isPrivate, title = title, spam = spam, onConfirm = onConfirm, roundTop = true)
        }
    }
}
