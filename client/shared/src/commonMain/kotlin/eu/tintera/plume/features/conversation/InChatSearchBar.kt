package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_search
import eu.tintera.plume.resources.conversation_search_clear
import eu.tintera.plume.resources.conversation_search_close
import eu.tintera.plume.resources.conversation_search_newer
import eu.tintera.plume.resources.conversation_search_none
import eu.tintera.plume.resources.conversation_search_older
import eu.tintera.plume.resources.conversation_search_position
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlavička konverzace při hledání: pole místo názvu chatu, šipka zpět hledání zavírá.
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
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Kdo ťukne na lupu, chce psát — pole se fokusuje samo.
    LaunchedEffect(Unit) { focus.requestFocus() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(Res.drawable.arrow_back_24dp),
                    contentDescription = stringResource(Res.string.conversation_search_close),
                )
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                placeholder = { Text(stringResource(Res.string.conversation_search)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                // Pole je nadpis lišty, ne formulářový prvek: bez vlastní plochy a podtržení.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
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
        },
    )
}

/**
 * Místo vstupního pole při hledání: pozice ve výsledcích a šipky mezi nimi.
 *
 * Nahoru = starší výsledek, dolů = novější — tak, jak leží v konverzaci nad sebou.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InChatSearchNavigator(
    search: InChatSearch,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .heightIn(min = 64.dp)
                .padding(start = 20.dp, end = 8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                when {
                    search.loading && search.results.isEmpty() -> LoadingIndicator(Modifier.size(32.dp))
                    search.nothingFound -> Text(
                        stringResource(Res.string.conversation_search_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    search.current != null -> Text(
                        stringResource(Res.string.conversation_search_position, search.index + 1, search.count),
                        style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                    )
                }
            }
            IconButton(onClick = onOlder, enabled = search.hasOlder && !search.loading) {
                Icon(
                    painterResource(Res.drawable.keyboard_arrow_up_24dp),
                    contentDescription = stringResource(Res.string.conversation_search_older),
                )
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
