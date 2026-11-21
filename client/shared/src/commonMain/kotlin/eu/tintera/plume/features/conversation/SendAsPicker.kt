package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.MessageSenderChoice
import eu.tintera.plume.core.telegram.MessageSenderOption
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.send_as_account
import eu.tintera.plume.resources.send_as_anonymous
import eu.tintera.plume.resources.send_as_channel
import eu.tintera.plume.resources.send_as_current
import eu.tintera.plume.resources.send_as_group
import eu.tintera.plume.resources.send_as_locked
import eu.tintera.plume.resources.send_as_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Avatar toho, kým se do skupiny píše, vlevo u vstupního pole; ťuknutí otevře výběr
 * ([SendAsSheet]). Stav sheetu si drží tlačítko samo — patří jen jemu, ne obrazovce.
 *
 * **Bez `IconButtonDefaults.shapes()`**, stejně jako fotka účtu v pilulce: obsahem je avatar
 * v jeho vlastním tvaru (kruh člověka, mnohoúhelník kanálu) a měnící se vlnka by kolem něj trčela.
 */
@Composable
internal fun SendAsButton(
    choice: MessageSenderChoice,
    /** Výběr se otevírá — nabídka se čte znovu (kanál mohl přibýt). */
    onOpen: () -> Unit,
    onChoose: (MessageSenderOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = choice.current
    val label = stringResource(Res.string.send_as_current, current.title)
    IconButton(
        onClick = {
            onOpen()
            open = true
        },
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Avatar(
            chatId = current.id,
            title = current.title,
            avatarFileId = current.avatarFileId,
            size = 32.dp,
            shape = current.kind.avatarShape(),
        )
    }
    if (open) {
        SendAsSheet(
            choice = choice,
            onChoose = { option ->
                open = false
                onChoose(option)
            },
            onDismiss = { open = false },
        )
    }
}

/**
 * Výběr odesílatele: vlastní účet, kanály, skupina jako anonymní správce. Zamčená volba (chce
 * Premium, které účet nemá) je vidět, ale nejde vybrat — řádek říká proč.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendAsSheet(
    choice: MessageSenderChoice,
    onChoose: (MessageSenderOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            SheetTitle(stringResource(Res.string.send_as_title), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
            // Volby jsou blok zaoblených řádků jako výběr cíle jinde v aplikaci.
            GroupedColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                choice.options.forEach { option ->
                    val selected = option.id == choice.current.id
                    ListItem(
                        // Plochu nese blok kolem; řádek vlastní nekreslí.
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Avatar(
                                chatId = option.id,
                                title = option.title,
                                avatarFileId = option.avatarFileId,
                                size = 40.dp,
                                shape = option.kind.avatarShape(),
                                modifier = Modifier.alpha(if (option.locked) DISABLED_ALPHA else 1f),
                            )
                        },
                        supportingContent = { Text(option.supportingText()) },
                        trailingContent = when {
                            selected -> {
                                { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) }
                            }
                            option.locked -> {
                                { Icon(painterResource(Res.drawable.lock_24dp), contentDescription = null) }
                            }
                            else -> null
                        },
                        modifier = Modifier
                            .selectable(
                                selected = selected,
                                enabled = !option.locked,
                                role = Role.RadioButton,
                                onClick = {
                                    // Sheet nejdřív odjede, pak se zavře — jako ostatní výběry.
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { onChoose(option) }
                                },
                            ),
                    ) { Text(option.title, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun MessageSenderOption.supportingText(): String = stringResource(
    when {
        locked -> Res.string.send_as_locked
        kind == ChatKind.Private -> Res.string.send_as_account
        isChat -> Res.string.send_as_anonymous
        kind == ChatKind.Channel -> Res.string.send_as_channel
        else -> Res.string.send_as_group
    },
)

private const val DISABLED_ALPHA = 0.38f
