package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.changelog.Changelog
import eu.tintera.plume.core.changelog.ChangelogNote
import eu.tintera.plume.core.changelog.ChangelogNoteType
import eu.tintera.plume.core.changelog.ChangelogRelease
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.bug_report_24dp
import eu.tintera.plume.resources.changelog_empty
import eu.tintera.plume.resources.changelog_minor_changes
import eu.tintera.plume.resources.changelog_note_feature
import eu.tintera.plume.resources.changelog_note_fix
import eu.tintera.plume.resources.changelog_note_language
import eu.tintera.plume.resources.changelog_release_title
import eu.tintera.plume.resources.changelog_show_older
import eu.tintera.plume.resources.rocket_launch_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.TitledGroup

/**
 * Sekce „Co je nového": poznámky k vydání zapečené při buildu ([Changelog]), od nejnovější verze.
 *
 * Jen ke čtení a sama se nikde neukazuje — po aktualizaci nic nevyskakuje, novinky jsou tu, kdo
 * je hledá. Vydání je skupina, poznámka řádek; novinku od opravy odliší TVAR ikony (raketa,
 * brouk), ne barva. Vydání bez poznámky pro tuhle platformu (tag Androidu vyšel kvůli hodinkám,
 * desktopu nebo iOS) má jediný řádek „Drobné opravy a vylepšení", jako v Google Play — bez něj by
 * seznam končil o pár verzí pod tou nainstalovanou a vypadal by rozbitý.
 *
 * Vydání se ukazuje po [PAGE] — sekce je sloupec, ne lazy seznam, a skládat naráz stovky řádků
 * za celou historii by zdrželo otevření kvůli něčemu, co skoro nikdo nedočte.
 */
@Composable
fun WhatsNewSettings() {
    val releases = Changelog.releases
    if (releases.isEmpty()) {
        Text(
            stringResource(Res.string.changelog_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }

    // Jazyk poznámek z prostředků, ne z locale: musí sedět s tím, v čem je zbytek obrazovky.
    val czech = stringResource(Res.string.changelog_note_language) == "cs"
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }

    releases.take(shown).forEach { release ->
        ReleaseGroup(release, czech)
    }
    if (shown < releases.size) {
        FilledTonalButton(
            onClick = { shown += PAGE },
            modifier = Modifier.fillMaxWidth().wrapContentWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(Res.string.changelog_show_older))
        }
    }
}

@Composable
private fun ReleaseGroup(release: ChangelogRelease, czech: Boolean) {
    TitledGroup(stringResource(Res.string.changelog_release_title, release.version, release.date.asDate())) {
        if (release.notes.isEmpty()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                // Věta sama říká, co v řádku je; slovo „Oprava" navíc by čtečka jen zopakovala.
                leadingContent = { Icon(painterResource(ChangelogNoteType.FIX.icon), contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.changelog_minor_changes)) },
            )
        }
        release.notes.forEach { note ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        painterResource(note.type.icon),
                        // Čtečka obrazovky tvar ikony nevidí, druh poznámky jí musí říct slovo.
                        contentDescription = stringResource(note.type.label),
                    )
                },
                headlineContent = { Text(note.text(czech)) },
            )
        }
    }
}

private fun ChangelogNote.text(czech: Boolean): String = if (czech) cs else en

private val ChangelogNoteType.icon
    get() = when (this) {
        ChangelogNoteType.FEAT -> Res.drawable.rocket_launch_24dp
        ChangelogNoteType.FIX -> Res.drawable.bug_report_24dp
    }

private val ChangelogNoteType.label
    get() = when (this) {
        ChangelogNoteType.FEAT -> Res.string.changelog_note_feature
        ChangelogNoteType.FIX -> Res.string.changelog_note_fix
    }

private const val PAGE = 10
