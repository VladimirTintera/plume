package eu.tintera.plume.features.forward

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ForwardCapabilities
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSearch
import eu.tintera.plume.core.ui.PickerSelection
import eu.tintera.plume.core.ui.PickerError
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.forward_hide_captions
import eu.tintera.plume.resources.forward_hide_sender
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.forward_title
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.KeyboardCapitalization
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.resources.chats_search_section_chats
import eu.tintera.plume.resources.forward_comment
import eu.tintera.plume.resources.forward_frequent
import eu.tintera.plume.resources.forward_silent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kam přeposlat: hledání a seznam chatů ([ChatPicker], společný se sdílením i novou konverzací).
 *
 * Obyčejná obrazovka, ne bottom sheet — seznam chatů je dlouhý a listuje se v něm; sheet by
 * navíc při hledání zápasil s klávesnicí. Po odeslání se otevře cílový chat, takže je hned vidět,
 * že zpráva dorazila.
 */
@Composable
fun ForwardScreen(
    viewModel: ForwardViewModel,
    onBack: () -> Unit,
    onForwarded: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatPicker(
        title = stringResource(Res.string.forward_title),
        search = PickerSearch(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(Res.string.forward_search),
        ),
        loading = state.loading,
        sections = forwardSections(
            frequent = state.frequent,
            chats = state.chats,
            frequentTitle = stringResource(Res.string.forward_frequent),
            chatsTitle = stringResource(Res.string.chats_search_section_chats),
        ),
        // Dokud odesílání běží, další ťuknutí by poslalo zprávu dvakrát.
        enabled = !state.sending,
        onPick = viewModel::toggle,
        selection = PickerSelection(
            // Přeposílá se do víc chatů naráz, takže ťuknutí cíl VYBERE a odesílá se až tlačítkem.
            selected = state.selected,
            onConfirm = { viewModel.send(onForwarded) },
        ),
        savedMessagesChatId = state.savedMessagesChatId,
        onBack = onBack,
        error = state.error?.let { PickerError(it, viewModel::consumeError) },
        header = {
            ForwardOptionsRow(
                capabilities = state.capabilities,
                options = state.options,
                enabled = !state.sending,
                onHideSender = viewModel::setHideSender,
                onHideCaptions = viewModel::setHideCaptions,
                onSilent = viewModel::setSilent,
            )
        },
        bottomBar = {
            ForwardComment(
                // Komentář patří k odeslání: bez vybraného cíle není komu ho psát.
                visible = state.selected.isNotEmpty(),
                comment = state.comment,
                enabled = !state.sending,
                onChange = viewModel::setComment,
            )
        },
        modifier = modifier,
    )
}

/**
 * Bloky výběru: bez častých cílů jeden blok bez nadpisu (cíle se nečlení), s nimi „Časté" nahoře
 * a pod nimi všechny chaty. Častý chat je v obou — dole by chyběl tam, kde ho uživatel hledá
 * podle pořadí ze seznamu chatů; výběr platí pro oba řádky (podle id).
 */
internal fun forwardSections(
    frequent: List<ChatSummary>,
    chats: List<ChatSummary>,
    frequentTitle: String,
    chatsTitle: String,
): List<PickerSection> =
    if (frequent.isEmpty()) {
        listOf(PickerSection(key = "chats", chats = chats))
    } else {
        listOf(
            PickerSection(key = "frequent", chats = frequent, title = frequentTitle),
            PickerSection(key = "chats", chats = chats, title = chatsTitle),
        )
    }

/**
 * Komentář k přeposlání — pole pod seznamem, nad klávesnicí. Odejde do každého cíle před
 * zprávami. Ukáže se, až je vybraný cíl; insety si dává samo (viz `ChatPicker.bottomBar`).
 */
@Composable
private fun ForwardComment(visible: Boolean, comment: String, enabled: Boolean, onChange: (String) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            FormTextField(
                value = comment,
                onValueChange = onChange,
                enabled = enabled,
                placeholder = { Text(stringResource(Res.string.forward_comment)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = COMMENT_MAX_LINES,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Delší komentář se v poli posouvá; pole by jinak vytlačilo seznam. */
private const val COMMENT_MAX_LINES = 4

/**
 * Jak přeposlat: „Bez zvuku", „Skrýt odesílatele" (kopie bez „Přeposláno od …") a s ní
 * „Skrýt popisky".
 *
 * Dvě NEZÁVISLÁ přepínací tlačítka, ne connected group — ta je pro výběr z jedné sady (druh
 * chatu v hledání), tady jsou to dvě volby, z nichž druhá platí jen s první. Proto se druhá
 * ukáže, až je první zapnutá, a jen když zprávy nějaký popisek nesou.
 *
 * Co server nedovolí (`canBeCopied`), se nenabízí vůbec — stejně jako položky nabídky nad zprávou.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ForwardOptionsRow(
    capabilities: ForwardCapabilities,
    options: ForwardOptions,
    enabled: Boolean,
    onHideSender: (Boolean) -> Unit,
    onHideCaptions: (Boolean) -> Unit,
    onSilent: (Boolean) -> Unit,
) {
    /*
     * Volby se zalamují: s „Bez zvuku" jsou tři a na úzkém telefonu by se do řádku nevešly.
     * Mezeru pod sebou nese tenhle řádek — vodorovné odsazení dává seznam (volby jsou jeho
     * první položka).
     */
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
    ) {
        // Bez zvuku jde vždycky; skrýt odesílatele jen, když zprávy jde zkopírovat.
        OptionToggle(
            checked = options.silent,
            onCheckedChange = onSilent,
            enabled = enabled,
            label = stringResource(Res.string.forward_silent),
        )
        AnimatedVisibility(
            visible = capabilities.canHideSender,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            OptionToggle(
                checked = options.hideSender,
                onCheckedChange = onHideSender,
                enabled = enabled,
                label = stringResource(Res.string.forward_hide_sender),
            )
        }
        AnimatedVisibility(
            visible = options.hideSender && capabilities.hasCaptions,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            OptionToggle(
                checked = options.hideCaptions,
                onCheckedChange = onHideCaptions,
                enabled = enabled,
                label = stringResource(Res.string.forward_hide_captions),
            )
        }
    }
}

/** Přepínací tlačítko s fajfkou, když je zapnuté — jako filtry hledání zpráv. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, label: String) {
    ToggleButton(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) {
        AnimatedVisibility(visible = checked) {
            Icon(
                painterResource(Res.drawable.check_24dp),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
        }
        Text(label)
    }
}
