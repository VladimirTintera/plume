package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.adaptive.paneHeaderContainerColor
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.lazy.itemsIndexed
import eu.tintera.plume.resources.search_off_24dp
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.groupedItem
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_search_list
import eu.tintera.plume.resources.format_list_bulleted_24dp
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_jump_to_date
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
import eu.tintera.plume.resources.event_24dp
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.person_search_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace
import eu.tintera.plume.core.adaptive.WindowControlsSpace
import eu.tintera.plume.core.adaptive.FilledShapeGap

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
    /** Skok na začátek vybraného dne (sekundy epochy); `null` = kalendář se nenabízí (téma, vlákno). */
    onJumpToDate: ((Int) -> Unit)? = null,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    if (pickingDate && onJumpToDate != null) {
        JumpToDateDialog(
            onPick = { seconds ->
                pickingDate = false
                onJumpToDate(seconds)
            },
            onDismiss = { pickingDate = false },
        )
    }
    Surface(color = paneHeaderContainerColor()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .height(SEARCH_BAR_HEIGHT)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            // Desktop: místo pro tlačítka okna, když hlavička leží v řádku s nimi. Pilulka je
            // plocha, ne holá ikona, a k semaforům by přilehla.
            WindowControlsSpace(extra = FilledShapeGap)
            SearchPill(
                initialQuery = initialQuery,
                onQueryChange = onQueryChange,
                placeholder = stringResource(Res.string.conversation_search),
                clearDescription = stringResource(Res.string.conversation_search_clear),
                // Kdo ťukne na lupu, chce psát.
                autoFocus = true,
                leading = {
                    // Zpět je UVNITŘ pilulky, jako v rozbaleném hledání seznamu chatů: zavírá
                    // hledání, ne obrazovku, takže patří k němu.
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(Res.drawable.arrow_back_24dp),
                            contentDescription = stringResource(Res.string.conversation_search_close),
                        )
                    }
                },
                prefix = {
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
                            shapes = InputChipDefaults.shapes(),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            // Výběr člena stojí VEDLE pilulky: je to akce nad hledáním, ne součást dotazu —
            // a tři ikony uvnitř pilulky by na úzkém telefonu nenechaly místo na text.
            if (onPickSender != null && sender == null) {
                IconButton(onClick = onPickSender, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.person_search_24dp),
                        contentDescription = stringResource(Res.string.conversation_search_by_member),
                    )
                }
            }
            // Kalendář vedle výběru člena, jako v oficiálních klientech: skok na den je jiný
            // způsob, jak v historii něco najít.
            if (onJumpToDate != null) {
                IconButton(onClick = { pickingDate = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.event_24dp),
                        contentDescription = stringResource(Res.string.conversation_jump_to_date),
                    )
                }
            }
            WindowControlsEndSpace()
        }
    }
}

/** Musí lícovat s hlavičkou konverzace, se kterou se hledání střídá. */
private val SEARCH_BAR_HEIGHT = 64.dp

/** Čip vybraného člena nesmí vytlačit pole — dlouhé jméno se zkrátí. */
private val SENDER_CHIP_MAX_WIDTH = 160.dp

/**
 * Místo vstupního pole při hledání: **plovoucí lišta** (`ConversationFloatingBar`) s pozicí ve
 * výsledcích a šipkami mezi nimi. Nahoru = starší výsledek, dolů = novější — tak, jak leží
 * v konverzaci nad sebou.
 *
 * **Bez kritérií se neukazuje.** Prázdný dotaz nemá co počítat a šipky by byly jen zašedlé.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InChatSearchNavigator(
    search: InChatSearch,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    /** Výsledky jako seznam (úryvek a datum) — při stovkách se šipkami nedá nic projít. */
    onShowList: () -> Unit,
) {
    ConversationFloatingBar(visible = search.hasCriteria) {
        IconButton(onClick = onShowList, enabled = search.results.isNotEmpty(), shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(Res.drawable.format_list_bulleted_24dp),
                contentDescription = stringResource(Res.string.conversation_search_list),
            )
        }
        IconButton(
            onClick = onOlder,
            enabled = search.hasOlder && !search.loading,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painterResource(Res.drawable.keyboard_arrow_up_24dp),
                contentDescription = stringResource(Res.string.conversation_search_older),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                /*
                 * `HorizontalFloatingToolbar` centruje na výšku jen Row se svým vlastním
                 * leading/trailing obsahem; ten NÁŠ skládá do vnořeného `Row` BEZ
                 * `verticalAlignment`, tedy s výchozím `Alignment.Top`. Výšku lišty určí 48dp
                 * `IconButton`y a cokoli nižšího se přilepí k horní hraně — změřeno na zařízení:
                 * „1 z 52" sedělo o 14 dp výš než šipky vedle něj. Každý nižší prvek v téhle
                 * liště se proto musí vystředit sám.
                 */
                .align(Alignment.CenterVertically)
                /*
                 * „Nic nenalezeno" je širší než „3 z 128"; bez animace by lišta poskakovala.
                 * Animuje se popisek, ne celá lišta: `animateContentSize` na toolbaru ho
                 * `clipToBounds` ořízne i o stín a s výchozím `Alignment.TopStart` by se obsah
                 * po dobu animace lepil doleva — tedy mimo střed, kvůli kterému tu je.
                 */
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    alignment = Alignment.Center,
                )
                // Ať mezi indikátorem načítání a první pozicí lišta viditelně neposkočí.
                .widthIn(min = STATUS_MIN_WIDTH)
                .padding(horizontal = 12.dp),
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
        IconButton(onClick = onNewer, enabled = search.hasNewer, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(Res.drawable.keyboard_arrow_down_24dp),
                contentDescription = stringResource(Res.string.conversation_search_newer),
            )
        }
    }
}

/** Zhruba „12 z 128"; kratší popisek se v ní jen vystředí. */
private val STATUS_MIN_WIDTH = 72.dp

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
    val sheetState = rememberExpandedSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SearchPill(
            initialQuery = picker.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(Res.string.conversation_search_members),
            clearDescription = stringResource(Res.string.conversation_search_clear),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        // Pevná výška: sheet se při psaní nenatahuje a nesmršťuje podle počtu nalezených.
        Box(Modifier.fillMaxWidth().height(MEMBER_LIST_HEIGHT).padding(top = 8.dp)) {
            when {
                picker.loading && picker.members.isEmpty() -> LoadingIndicator(Modifier.align(Alignment.Center))
                picker.members.isEmpty() -> GroupEmptyRow(
                    stringResource(Res.string.conversation_search_members_none),
                    icon = Res.drawable.search_off_24dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                // Členové jsou blok zaoblených řádků jako výběr cíle jinde v aplikaci.
                else -> LazyColumn {
                    itemsIndexed(picker.members, key = { _, member -> member.id }) { index, member ->
                        ListItem(
                            leadingContent = {
                                Avatar(chatId = member.id, title = member.title, avatarFileId = member.avatarFileId, size = 40.dp)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .groupedItem(index, picker.members.size, MaterialTheme.colorScheme.groupRow)
                                .clickable { onPick(member) },
                        ) { Text(member.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

private val MEMBER_LIST_HEIGHT = 420.dp
