package eu.tintera.plume.features.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_section_appearance
import eu.tintera.plume.resources.settings_section_appearance_summary
import eu.tintera.plume.resources.settings_section_general
import eu.tintera.plume.resources.settings_section_general_summary
import eu.tintera.plume.resources.settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Seznam sekcí nastavení — v list-detail rozvržení levý panel.
 *
 * Sám nedrží žádný stav, takže nemá ViewModel: obsah sekcí (a s ním i účet, notifikace nebo
 * motiv) žije v [SettingsSectionScreen]. Kdyby si účet načítal i tenhle seznam, ptali bychom
 * se na něj v dvoupanelu dvakrát.
 */
@Composable
fun SettingsScreen(
    selectedSection: SettingsSection?,
    onSectionClick: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { LargeFlexibleTopAppBar(title = { Text(stringResource(Res.string.settings_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            // Rozestup uvnitř bloku, stejný jazyk jako seznam chatů: ze sekcí je jeden blok
            // se společným zaoblením, ne řada samostatných karet.
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            SettingsSection.entries.forEachIndexed { index, section ->
                SectionRow(
                    section = section,
                    selected = section == selectedSection,
                    firstInGroup = index == 0,
                    lastInGroup = index == SettingsSection.entries.lastIndex,
                    onClick = { onSectionClick(section) },
                )
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: SettingsSection,
    selected: Boolean,
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    onClick: () -> Unit,
) {
    /*
     * Výběr v list-detail je navigační stav, ne zvýraznění obsahu → `secondaryContainer`,
     * stejná role, jakou nese vybraná položka v nav railu i vybraný chat v seznamu.
     */
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // Popis sekce je doplněk k názvu, takže je tlumený — na vybraném řádku ale nesmí zůstat
    // `onSurfaceVariant`, ten je počítaný na jinou plochu.
    val supportingColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Zaoblení nese krajní položka bloku. Animuje se ze stejného důvodu jako u seznamu chatů —
    // sekcí bude přibývat a blok se přeskládá.
    val topCorner by animateDpAsState(
        // Vybraná položka se z bloku vyčlení: zaoblená celá, jako skupina sama (M3 Expressive).
        targetValue = if (firstInGroup || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "settings-corner-top",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (lastInGroup || selected) GROUP_CORNER else INNER_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "settings-corner-bottom",
    )
    val shape = RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            supportingContentColor = supportingColor,
        ),
        headlineContent = { Text(stringResource(section.title)) },
        supportingContent = { Text(stringResource(section.summary)) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    )
}

/** Název sekce. Nese ho jedno místo, protože stojí i v hlavičce otevřené sekce. */
internal val SettingsSection.title: StringResource
    get() = when (this) {
        SettingsSection.General -> Res.string.settings_section_general
        SettingsSection.Appearance -> Res.string.settings_section_appearance
    }

/** Co v sekci je. Bez toho by ze seznamu dvou slov nebylo poznat, kde co hledat. */
private val SettingsSection.summary: StringResource
    get() = when (this) {
        SettingsSection.General -> Res.string.settings_section_general_summary
        SettingsSection.Appearance -> Res.string.settings_section_appearance_summary
    }

private val GROUP_CORNER = 20.dp
private val INNER_CORNER = 4.dp
private val GROUP_ITEM_SPACING = 2.dp
