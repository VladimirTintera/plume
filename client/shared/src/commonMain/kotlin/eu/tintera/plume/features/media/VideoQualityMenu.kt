package eu.tintera.plume.features.media

import eu.tintera.plume.core.ui.menuGroupShape
import eu.tintera.plume.core.ui.menuScroll
import eu.tintera.plume.core.ui.MenuColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.core.telegram.VideoQuality
import eu.tintera.plume.core.ui.MenuPopupProperties
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.core.video.VideoQualityChoice
import eu.tintera.plume.features.conversation.LabelChip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.media_quality_auto
import eu.tintera.plume.resources.media_quality_auto_hint
import eu.tintera.plume.resources.media_quality_description
import eu.tintera.plume.resources.media_quality_lines
import eu.tintera.plume.resources.media_quality_original
import eu.tintera.plume.resources.media_quality_with_size
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Nabídka kvality pro jedno video. [options] od nejlepší, první je původní soubor
 * (`videoQualities`); [playing] je ta, kterou přehrávač dostal, [autoPick] ta, kterou by
 * vybrala automatika (ukazuje se u volby Automaticky, ať je vidět, co znamená).
 */
internal class VideoQualityMenu(
    val options: List<VideoQuality>,
    val choice: VideoQualityChoice,
    val playing: VideoQuality,
    val autoPick: VideoQuality,
    val onChoose: (VideoQualityChoice) -> Unit,
)

/**
 * Čip kvality v liště videa vedle rychlosti, tentýž vzhled ([LabelChip]). Nese kvalitu, která
 * hraje („720p"); ťuknutí otevře nabídku: Automaticky (s tím, co podle sítě vybrala) a pak
 * jednotlivé kvality s velikostí souboru — na mobilních datech je velikost to, podle čeho se
 * vybírá.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoQualityChip(
    menu: VideoQualityMenu,
    /** Otevřenou nabídku drží stránka: ovládání se mezitím nesmí schovat, zmizela by s ním. */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val label = qualityLabel(menu.playing)
    Box {
        LabelChip(
            label = label,
            accent = Color.White,
            onAccent = Color.Black,
            onClick = { onExpandedChange(true) },
            description = stringResource(Res.string.media_quality_description, label),
        )
        DropdownMenuPopup(
            modifier = Modifier.menuScroll(),
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            properties = MenuPopupProperties,
        ) {
            MenuColors {
                val choose = { choice: VideoQualityChoice ->
                    onExpandedChange(false)
                    menu.onChoose(choice)
                }
                DropdownMenuGroup(shapes = menuGroupShape(0, 2)) {
                    CheckableDropdownMenuItem(
                        checked = menu.choice == VideoQualityChoice.Auto,
                        onCheckedChange = { choose(VideoQualityChoice.Auto) },
                        text = { Text(stringResource(Res.string.media_quality_auto)) },
                        supportingText = { Text(stringResource(Res.string.media_quality_auto_hint, qualityLabel(menu.autoPick))) },
                        shapes = MenuDefaults.itemShape(0, 1),
                        checkedLeadingIcon = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) },
                    )
                }
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = menuGroupShape(1, 2)) {
                    menu.options.forEachIndexed { index, option ->
                        val size = option.sizeBytes.takeIf { it > 0 }?.let { fileSizeText(it) }
                        val supporting = when {
                            option.isOriginal && size != null ->
                                stringResource(Res.string.media_quality_with_size, stringResource(Res.string.media_quality_original), size)
                            option.isOriginal -> stringResource(Res.string.media_quality_original)
                            else -> size
                        }
                        CheckableDropdownMenuItem(
                            checked = menu.choice != VideoQualityChoice.Auto && option == menu.playing,
                            onCheckedChange = {
                                choose(if (option.isOriginal) VideoQualityChoice.Original else VideoQualityChoice.Lines(option.lines))
                            },
                            text = { Text(qualityLabel(option)) },
                            supportingText = supporting?.let { { Text(it) } },
                            shapes = MenuDefaults.itemShape(index, menu.options.size),
                            checkedLeadingIcon = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

/** „720p" — kratší strana, viz [VideoQuality.lines]. */
@Composable
private fun qualityLabel(quality: VideoQuality): String = stringResource(Res.string.media_quality_lines, quality.lines)

/**
 * Volba kvality přežije otočení obrazovky (prohlížeč ji drží v `rememberSaveable`): −1 =
 * automaticky, 0 = původní, jinak počet řádků.
 */
internal val VideoQualityChoiceSaver = Saver<VideoQualityChoice, Int>(
    save = { choice ->
        when (choice) {
            VideoQualityChoice.Auto -> -1
            VideoQualityChoice.Original -> 0
            is VideoQualityChoice.Lines -> choice.lines
        }
    },
    restore = { saved ->
        when (saved) {
            -1 -> VideoQualityChoice.Auto
            0 -> VideoQualityChoice.Original
            else -> VideoQualityChoice.Lines(saved)
        }
    },
)
