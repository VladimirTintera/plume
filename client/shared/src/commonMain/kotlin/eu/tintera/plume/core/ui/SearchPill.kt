package eu.tintera.plume.core.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Vyhledávací pilulka — **jeden prvek pro hledání v celé aplikaci**: hlavička seznamu chatů,
 * hledání v konverzaci, výběr chatu (přeposlání, sdílení) i hledání emoji, samolepek a GIFů.
 * Různě vypadající pole by znamenala, že uživatel u každé obrazovky poznává hledání znovu.
 *
 * **Text drží pole samo** ([initialQuery] je jen výchozí hodnota). Hodnota vrácená zvenku přes
 * `StateFlow` by se do pole dostala o dispatch později a kurzor by poskakoval. Kde se text mění
 * i zvenku, je řízená varianta [ControlledSearchPill].
 *
 * **Uvnitř je `BasicTextField` s dekorací, ne `TextField`.** Ten si bere minimální výšku 56 dp
 * a vnitřní odsazení 16 dp napevno, takže by se vedle ikony do pilulky nevešel a text by od ní
 * začínal skoro 30 dp daleko. Kontejner nese pilulka, pole samo nekreslí nic.
 */
@Composable
fun SearchPill(
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
    /** Kdo se dostal na obrazovku kvůli hledání, chce rovnou psát. */
    autoFocus: Boolean = false,
    /**
     * Začátek pilulky: výchozí je lupa, hledání v konverzaci tam má zpět. Odsazení si nese
     * slot sám — tlačítko má kolem glyfu svoje, holá ikona ne.
     */
    leading: @Composable () -> Unit = { SearchPillIcon() },
    /** Mezi začátkem a polem — čip vybraného člena při hledání v konverzaci. */
    prefix: @Composable () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    ControlledSearchPill(
        query = query,
        onQueryChange = {
            query = it
            onQueryChange(it)
        },
        placeholder = placeholder,
        clearDescription = clearDescription,
        modifier = modifier,
        autoFocus = autoFocus,
        leading = leading,
        prefix = prefix,
    )
}

/**
 * **Řízená** pilulka: text drží volající ([query]). Pro hledání, které se mění i zvenku — výběr
 * kategorie samolepek pole vymaže. Hodnota musí být Compose stav na obrazovce, ne `StateFlow`
 * z ViewModelu: ten by do pole dorazil o dispatch později a kurzor by poskakoval (proto má
 * běžná varianta text u sebe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledSearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    leading: @Composable () -> Unit = { SearchPillIcon() },
    prefix: @Composable () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(SEARCH_PILL_HEIGHT),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading()
            prefix()
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
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
                            Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        container = {},
                    )
                },
            )
            if (query.isNotEmpty()) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                    onQueryChange("")
                    focus.requestFocus()
                }) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = clearDescription)
                }
            }
        }
    }
}

/** Výchozí začátek pilulky: lupa s odsazením od kraje. */
@Composable
private fun SearchPillIcon() {
    Icon(
        painterResource(Res.drawable.search_24dp),
        contentDescription = null,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
    )
}

/** Jako vstupní pole M3 hledání. */
val SEARCH_PILL_HEIGHT = 56.dp
