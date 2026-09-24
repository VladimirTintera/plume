package eu.tintera.plume.features.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.features.chats.ChatSearch
import eu.tintera.plume.features.chats.pickerChats
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.ChatPicker
import eu.tintera.plume.core.ui.PickerSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.share_files
import eu.tintera.plume.resources.share_preparing
import eu.tintera.plume.resources.share_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kam poslat obsah sdílený do aplikace ze systému.
 *
 * Tentýž výběr jako u přeposílání ([ChatPicker]), jen se po výběru nic neposílá: otevře se chat
 * a obsah čeká nad vstupním polem (soubory) a v něm (text). Uživatel k tomu může napsat popisek
 * a vidí, co odejde — odeslat rovnou z výběru by bylo odeslání naslepo.
 */
class ShareViewModel(
    private val requests: ShareRequests,
    chatRepository: ChatRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** Hledání ve všech chatech, i v archivu — stejně jako u přeposílání. */
    private val search = ChatSearch(chatRepository, viewModelScope)

    val state: StateFlow<ShareState> = combine(
        combine(chatRepository.chats(), query, search.state) { list, text, found -> pickerChats(text, list, found) },
        query,
        requests.incoming,
        chatRepository.savedMessagesChatId,
    ) { picker, text, content, saved ->
        ShareState(
            query = text,
            loading = picker.loading,
            chats = picker.chats,
            content = content,
            savedMessagesChatId = saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareState(content = requests.incoming.value))

    fun onQueryChange(text: String) {
        query.value = text
        search.setQuery(text)
    }

    /**
     * Sdílení, které výběr ukazuje: poslední bez vybraného chatu. Direct Share, které přijde
     * mezitím (už s chatem), sem nepatří — kvůli němu shell výběr nahrazuje, a jeho odchod ho
     * proto zahodit nesmí.
     */
    private var shownId: Long? = requests.incoming.value?.takeIf { it.targetChatId == null }?.id

    init {
        viewModelScope.launch {
            requests.incoming.collect { content ->
                if (content != null && content.targetChatId == null) shownId = content.id
            }
        }
    }

    fun choose(chatId: Long) = requests.choose(chatId)

    fun abandon() {
        shownId?.let(requests::abandon)
    }
}

data class ShareState(
    val query: String = "",
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val content: SharedContent? = null,
    val savedMessagesChatId: Long? = null,
)

@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    /**
     * ✕: sdílení se zahodí (až výběr zmizí, viz `DisposableEffect` níž) a uživatel se vrátí do
     * aplikace, ze které sdílel. Šipka zpět by slibovala návrat do Plume, kde by obsah zmizel.
     */
    onClose: () -> Unit,
    onChosen: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state.content

    // Obsah zmizel (po zabití procesu se routa obnoví, obsah ne) — výběr nemá co nabízet.
    LaunchedEffect(content == null) { if (content == null) onClose() }

    /*
     * Sdílení se zahodí, když výběr odejde BEZ volby chatu — ať už šipkou, systémovým gestem zpět
     * (to jde přes `NavDisplay`, ne přes tuhle obrazovku), nebo přepnutím sekce. Kdyby se rušilo
     * jen na šipce, zůstalo by po gestu viset a výběr by se po obnově aktivity otevřel znovu.
     */
    DisposableEffect(Unit) { onDispose { viewModel.abandon() } }

    ChatPicker(
        title = stringResource(Res.string.share_title),
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        searchPlaceholder = stringResource(Res.string.forward_search),
        loading = state.loading,
        sections = listOf(PickerSection(key = "chats", chats = state.chats)),
        enabled = content != null,
        onPick = { chatId ->
            viewModel.choose(chatId)
            onChosen(chatId)
        },
        onBack = onClose,
        closeIcon = true,
        savedMessagesChatId = state.savedMessagesChatId,
        header = { content?.let { SharedContentCard(it) } },
        modifier = modifier,
    )
}

/**
 * Co se sdílí — karta, ne holý řádek textu na pozadí.
 *
 * Je to **cizí vložený obsah**, tedy totéž co citace nad vstupním polem nebo popis webu u náhledu
 * odkazu: proto `tertiaryContainer`. Jako holý text byla tahle informace jediný prvek obrazovky,
 * který nestál na ploše — a přitom je to to, podle čeho uživatel pozná, že posílá správnou věc.
 *
 * **Ikona říká DRUH obsahu** (soubory / odkaz / text), dokud se soubory připravují, je místo ní
 * indikátor — „Připravuji soubory…" je průběh, ne popis.
 */
@Composable
private fun SharedContentCard(content: SharedContent, modifier: Modifier = Modifier) {
    val attachments = content.attachments
    val text = when {
        attachments == null -> stringResource(Res.string.share_preparing)
        attachments.isNotEmpty() ->
            pluralStringResource(Res.plurals.share_files, attachments.size, attachments.size)
        else -> content.text.orEmpty()
    }
    if (text.isBlank()) return

    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        // Vodorovné odsazení dává seznam (karta je jeho první položka); mezeru pod sebou
        // nese karta, takže bez sdíleného textu po ní nezbude prázdné místo.
        modifier = modifier.fillMaxWidth().padding(bottom = GROUP_SPACING - GROUP_ITEM_SPACING),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (attachments == null) {
                LoadingIndicator(
                    color = LocalContentColor.current,
                    modifier = Modifier.size(ICON_SIZE),
                )
            } else {
                Icon(
                    painterResource(sharedContentIcon(content)),
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Soubory mají sponku, sdílený odkaz řetěz, holý text stránku — ikona má říct, co odejde. */
private fun sharedContentIcon(content: SharedContent) = when {
    !content.attachments.isNullOrEmpty() -> Res.drawable.attach_file_24dp
    content.text?.contains("://") == true -> Res.drawable.link_24dp
    else -> Res.drawable.description_24dp
}

/** Aby indikátor při přípravě souborů zabral přesně tolik co ikona a text nepopojel. */
private val ICON_SIZE = 24.dp
