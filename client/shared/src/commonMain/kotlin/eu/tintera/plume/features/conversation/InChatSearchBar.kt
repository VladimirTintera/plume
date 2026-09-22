package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_search
import eu.tintera.plume.resources.conversation_search_by_member
import eu.tintera.plume.resources.conversation_search_clear
import eu.tintera.plume.resources.conversation_search_close
import eu.tintera.plume.resources.conversation_search_members
import eu.tintera.plume.resources.conversation_search_members_none
import eu.tintera.plume.resources.conversation_search_newer
import eu.tintera.plume.resources.conversation_search_none
import eu.tintera.plume.resources.conversation_search_older
import eu.tintera.plume.resources.conversation_search_position
import eu.tintera.plume.resources.conversation_search_sender_remove
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.person_search_24dp
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlavička konverzace při hledání: **vyhledávací pilulka** místo názvu chatu, uvnitř ní šipka
 * zpět (zavírá hledání), pole a ✕ na smazání dotazu; výběr člena je ikona vedle pilulky.
 *
 * **Je to tentýž prvek jako hledání v seznamu chatů**, ne holé pole v liště. Dokud pole žádnou
 * plochu nemělo (`TextField` s průhledným kontejnerem i indikátorem), byl z hledání jen šedý
 * text na stránce a nešlo poznat, že se hlavička vůbec přepnula — jediná změna oproti klidovému
 * stavu byl zmizelý avatar. Pilulka to říká tvarem, ještě než si uživatel přečte placeholder,
 * a na rozloženém Foldu vypadá hlavička konverzace při hledání stejně jako hlavička seznamu
 * vedle ní.
 *
 * **Vlastní řádek, ne `TopAppBar`.** Ten měří titulek na celou šířku, ale umísťuje ho na
 * 16 dp od kraje, takže by pilulka o ten kus přetekla za pravý okraj. Insety a výška jsou proto
 * ruční — výška musí lícovat s hlavičkou konverzace, mezi kterými se přepíná.
 *
 * Text drží pole samo, ne stav ViewModelu. Stav se vrací přes `StateFlow` o dispatch později
 * a pole napojené na zpožděnou hodnotu přeskakuje kurzorem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InChatSearchTopBar(
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    /** Vybraný člen, jehož zprávy se hledají; `null` = všechny. */
    sender: SenderInfo? = null,
    /** Výběr člena; `null` mimo skupiny, kde odesílatel není jen jeden protějšek. */
    onPickSender: (() -> Unit)? = null,
    onRemoveSender: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Kdo ťukne na lupu, chce psát — pole se fokusuje samo.
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .height(SEARCH_BAR_HEIGHT)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(SEARCH_PILL_HEIGHT),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Zpět je UVNITŘ pilulky, jako v rozbaleném hledání seznamu chatů: zavírá
                    // hledání, ne obrazovku, takže patří k němu.
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(Res.drawable.arrow_back_24dp),
                            contentDescription = stringResource(Res.string.conversation_search_close),
                        )
                    }
                    // Vybraný člen je čip před polem: je vidět, že se hledá jen v jeho zprávách,
                    // a ťuknutím se filtr zruší.
                    sender?.let {
                        InputChip(
                            selected = true,
                            onClick = onRemoveSender,
                            label = { Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            avatar = {
                                Avatar(
                                    chatId = it.id,
                                    title = it.title,
                                    avatarFileId = it.avatarFileId,
                                    size = InputChipDefaults.AvatarSize,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    painterResource(Res.drawable.close_24dp),
                                    contentDescription = stringResource(Res.string.conversation_search_sender_remove),
                                    modifier = Modifier.size(InputChipDefaults.IconSize),
                                )
                            },
                            modifier = Modifier.widthIn(max = SENDER_CHIP_MAX_WIDTH),
                        )
                    }
                    /*
                     * `BasicTextField` s dekorací, ne `TextField`: ten si bere minimální výšku
                     * 56 dp i s vlastním odsazením 16 dp napevno, takže by se do pilulky vedle
                     * ikony nevešel a text by od ní začínal skoro 30 dp. Kontejner nese pilulka,
                     * pole samo nekreslí nic — stejně jako vstupní pole konverzace.
                     */
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onQueryChange(it)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge
                            .copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        interactionSource = interaction,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { innerTextField ->
                            TextFieldDefaults.DecorationBox(
                                value = query,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = interaction,
                                placeholder = {
                                    Text(
                                        stringResource(Res.string.conversation_search),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = searchFieldColors(),
                                // Glyf ikony má kolem sebe vlastní vzduch, 16 dp by bylo moc.
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                container = {},
                            )
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            onQueryChange("")
                            focus.requestFocus()
                        }) {
                            Icon(
                                painterResource(Res.drawable.close_24dp),
                                contentDescription = stringResource(Res.string.conversation_search_clear),
                            )
                        }
                    }
                }
            }
            // Výběr člena stojí VEDLE pilulky: je to akce nad hledáním, ne součást dotazu —
            // a tři ikony uvnitř pilulky by na úzkém telefonu nenechaly místo na text.
            if (onPickSender != null && sender == null) {
                IconButton(onClick = onPickSender) {
                    Icon(
                        painterResource(Res.drawable.person_search_24dp),
                        contentDescription = stringResource(Res.string.conversation_search_by_member),
                    )
                }
            }
        }
    }
}

/** Kontejner i podtržení nese pilulka; pole samo nekreslí nic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun searchFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

/** Musí lícovat s hlavičkou konverzace, se kterou se hledání střídá. */
private val SEARCH_BAR_HEIGHT = 64.dp

/** Jako vstupní pole M3 hledání; ve 64dp liště zbydou 4 dp nad a pod. */
private val SEARCH_PILL_HEIGHT = 56.dp

/** Čip vybraného člena nesmí vytlačit pole — dlouhé jméno se zkrátí. */
private val SENDER_CHIP_MAX_WIDTH = 160.dp

/**
 * Místo vstupního pole při hledání: **plovoucí toolbar** s pozicí ve výsledcích a šipkami mezi
 * nimi. Nahoru = starší výsledek, dolů = novější — tak, jak leží v konverzaci nad sebou.
 *
 * **Plovoucí pilulka, ne pruh přes celou šířku.** `surfaceContainer` na ploše stránky je
 * v tmavém tématu rozdíl na hranici rozlišitelnosti, takže z celého navigátoru byly vidět jen
 * dvě šipky zavěšené v prázdnu. Sytá plovoucí plocha (`vibrantFloatingToolbarColors`) se od
 * pozadí odliší sama a je to tentýž prvek jako toolbar filtrů v seznamu chatů.
 *
 * **Bez kritérií se neukazuje.** Prázdný dotaz nemá co počítat a šipky by byly jen zašedlé.
 * Plovoucí prvek smí naskočit, protože nemá v layoutu pevné místo — výšku drží `Box` kolem něj,
 * takže se seznam zpráv pod ním nepřeskládá.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InChatSearchNavigator(
    search: InChatSearch,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .height(NAVIGATOR_HEIGHT)
            .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
    ) {
        AnimatedVisibility(
            visible = search.hasCriteria,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.8f),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                // „Nic nenalezeno" je širší než „3 z 128"; bez animace by toolbar poskakoval.
                modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                IconButton(onClick = onOlder, enabled = search.hasOlder && !search.loading) {
                    Icon(
                        painterResource(Res.drawable.keyboard_arrow_up_24dp),
                        contentDescription = stringResource(Res.string.conversation_search_older),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    when {
                        search.loading && search.results.isEmpty() ->
                            LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
                        search.nothingFound -> Text(
                            stringResource(Res.string.conversation_search_none),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        search.current != null -> Text(
                            stringResource(Res.string.conversation_search_position, search.index + 1, search.count),
                            style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                        )
                    }
                }
                IconButton(onClick = onNewer, enabled = search.hasNewer) {
                    Icon(
                        painterResource(Res.drawable.keyboard_arrow_down_24dp),
                        contentDescription = stringResource(Res.string.conversation_search_newer),
                    )
                }
            }
        }
    }
}

/** Výška toolbaru (`FloatingToolbarDefaults.ContainerSize`) a jeho odstup od spodní hrany. */
private val NAVIGATOR_HEIGHT = 80.dp

/**
 * Výběr člena skupiny pro hledání podle odesílatele: pole na jméno a seznam členů. Server u velké
 * skupiny vrátí jen část, takže se hledá podle jména, ne listuje (`searchChatMembers`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MemberPickerSheet(
    picker: MemberPicker,
    onQueryChange: (String) -> Unit,
    onPick: (SenderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var query by rememberSaveable { mutableStateOf(picker.query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            placeholder = { Text(stringResource(Res.string.conversation_search_members)) },
            leadingIcon = { Icon(painterResource(Res.drawable.search_24dp), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        // Pevná výška: sheet se při psaní nenatahuje a nesmršťuje podle počtu nalezených.
        Box(Modifier.fillMaxWidth().height(MEMBER_LIST_HEIGHT).padding(top = 8.dp)) {
            when {
                picker.loading && picker.members.isEmpty() -> LoadingIndicator(Modifier.align(Alignment.Center))
                picker.members.isEmpty() -> Text(
                    stringResource(Res.string.conversation_search_members_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn {
                    items(picker.members, key = { it.id }) { member ->
                        ListItem(
                            headlineContent = { Text(member.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Avatar(chatId = member.id, title = member.title, avatarFileId = member.avatarFileId, size = 40.dp)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPick(member) },
                        )
                    }
                }
            }
        }
    }
}

private val MEMBER_LIST_HEIGHT = 420.dp
