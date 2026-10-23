package eu.tintera.plume.features.profile

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.ProfileMainTab
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.ui.ChoiceRow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.main_tab
import eu.tintera.plume.resources.main_tab_auto
import eu.tintera.plume.resources.main_tab_note
import eu.tintera.plume.resources.profile_tab_files
import eu.tintera.plume.resources.profile_tab_gifs
import eu.tintera.plume.resources.profile_tab_gifts
import eu.tintera.plume.resources.profile_tab_links
import eu.tintera.plume.resources.profile_tab_media
import eu.tintera.plume.resources.profile_tab_music
import eu.tintera.plume.resources.profile_tab_stories
import eu.tintera.plume.resources.profile_tab_voice
import eu.tintera.plume.resources.tab_24dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Záložka profilu v Plume, která odpovídá hlavní záložce od Telegramu. */
internal fun ProfileMainTab.toProfileTab(): ProfileTab = when (this) {
    ProfileMainTab.Posts -> ProfileTab.Stories
    ProfileMainTab.Gifts -> ProfileTab.Gifts
    ProfileMainTab.Media -> ProfileTab.Shared(SharedMediaKind.Media)
    ProfileMainTab.Files -> ProfileTab.Shared(SharedMediaKind.Files)
    ProfileMainTab.Links -> ProfileTab.Shared(SharedMediaKind.Links)
    ProfileMainTab.Music -> ProfileTab.Shared(SharedMediaKind.Music)
    ProfileMainTab.Voice -> ProfileTab.Shared(SharedMediaKind.Voice)
    ProfileMainTab.Gifs -> ProfileTab.Shared(SharedMediaKind.Gifs)
}

/** Tytéž názvy jako záložky v profilu — volba má říkat, kde se profil otevře. */
private fun ProfileMainTab.label(): StringResource = when (this) {
    ProfileMainTab.Posts -> Res.string.profile_tab_stories
    ProfileMainTab.Gifts -> Res.string.profile_tab_gifts
    ProfileMainTab.Media -> Res.string.profile_tab_media
    ProfileMainTab.Files -> Res.string.profile_tab_files
    ProfileMainTab.Links -> Res.string.profile_tab_links
    ProfileMainTab.Music -> Res.string.profile_tab_music
    ProfileMainTab.Voice -> Res.string.profile_tab_voice
    ProfileMainTab.Gifs -> Res.string.profile_tab_gifs
}

/**
 * Řádek „Hlavní záložka profilu" s nabídkou záložek — v úpravě kanálu i vlastního profilu.
 * „Automaticky" se nabízí, jen dokud nic vybrané není: zrušit volbu TDLib neumí
 * (`setMainProfileTab` bere jen konkrétní záložku).
 */
@Composable
internal fun MainTabRow(current: ProfileMainTab?, onSelect: (ProfileMainTab) -> Unit) {
    val options: List<ProfileMainTab?> = (if (current == null) listOf(null) else emptyList()) + ProfileMainTab.entries
    ChoiceRow(
        headline = stringResource(Res.string.main_tab),
        options = options,
        selected = current,
        label = { tab -> tab?.let { stringResource(it.label()) } ?: stringResource(Res.string.main_tab_auto) },
        onSelect = { tab -> tab?.let(onSelect) },
        icon = Res.drawable.tab_24dp,
        note = stringResource(Res.string.main_tab_note),
    )
}
