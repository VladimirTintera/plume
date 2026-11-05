package eu.tintera.plume.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Prvky formuláře a nastavení, které mluví jazykem bloku zaoblených řádků ([GroupedColumn]):
 * nadpis bloku, blok s nadpisem a řádek s přepínačem. Dřív měla každá obrazovka vlastní kopii —
 * sedm `SwitchRow`/`ToggleRow`, přes deset ručně psaných řádků s přepínačem (část bez haptiky,
 * dva bez role přepínače pro čtečku) a deset nadpisů nad blokem s trochu jiným odsazením.
 */

/**
 * **Nadpis nad blokem řádků.** Lícuje s okrajem bloku (16 dp od kraje obsahu). Svislé odsazení
 * nedává: v [TitledGroup] mezeru drží sloupec, v lazy seznamu ji volající přidá modifikátorem.
 * [action] stojí vpravo a lícuje s okrajem bloku, ne s textem (tlačítko má odsazení samo).
 */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = if (action == null) 16.dp else 0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke(this)
    }
}

/**
 * **Blok řádků s nadpisem** ([GroupTitle] nad [GroupedColumn]).
 *
 * Nadpis a blok jsou JEDEN kus, ne dva sousedi ve sloupci obrazovky — s rozestupem obrazovky by
 * nadpis visel stejně daleko od svého bloku jako od toho předchozího a nebylo by poznat, k čemu
 * patří. Každé dítě [content] je jeden řádek bloku; co k sobě patří (vzorky barev a poznámka pod
 * nimi), musí být v jednom `Column`. Dělicí čáru nahrazuje [GroupBreak].
 */
@Composable
fun TitledGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Hledaná volba může být celý blok (Motiv, Automatické stahování): zvýrazní se s nadpisem.
    Column(modifier.highlightAnchor(title), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle(title)
        GroupedColumn(Modifier.fillMaxWidth(), content = content)
    }
}

/** [TitledGroup] s pevným nadpisem z prostředků. */
@Composable
fun TitledGroup(title: StringResource, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    TitledGroup(stringResource(title), modifier, content)

/**
 * Podklad a ořez řádku bloku tam, kde ho nekreslí [GroupedColumn]: lazy seznam formuláře, ve
 * kterém rozestupy dává seznam a tvar podle pozice volající ([groupItemShape]).
 */
@Composable
fun Modifier.groupRowBackground(shape: Shape): Modifier =
    clip(shape).background(MaterialTheme.colorScheme.groupRow)

/**
 * **Řádek s přepínačem.** Klikání nese celý řádek (`toggleable(role = Switch)`), [IconSwitch] je
 * jen ukazatel stavu — čtečka obrazovky čte stav z řádku a ťuknutí kamkoli na něj přepne.
 * Přepnutí hlásí haptika ([toggle]); volající ji už nepřidává, jinak by se zdvojila.
 *
 * Podklad je průhledný: řádek leží v [GroupedColumn], který mu dá tvar i barvu, nebo v ploše, kterou
 * si volající nakreslí sám ([groupRowBackground] ve formuláři ankety).
 */
@Composable
fun SwitchListItem(
    headline: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    /** Podtitul je hláška o selhání — chybovou barvou. */
    supportingIsError: Boolean = false,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
    /** Poslední, ať jde psát `SwitchListItem(název, stav) { … }` jako dřív u `SwitchRow`. */
    onCheckedChange: (Boolean) -> Unit,
) = SwitchListItem(
    checked = checked,
    onCheckedChange = onCheckedChange,
    headlineContent = { Text(headline) },
    modifier = modifier.highlightAnchor(headline),
    supportingContent = supporting?.let {
        { Text(it, color = if (supportingIsError) MaterialTheme.colorScheme.error else Color.Unspecified) }
    },
    leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
    enabled = enabled,
)

/** [SwitchListItem] s vlastním obsahem (ikona složky, podtitul na víc řádků). */
@Composable
fun SwitchListItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = leadingContent,
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        trailingContent = { IconSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { on ->
                haptics.toggle(on)
                onCheckedChange(on)
            },
        ),
    )
}
