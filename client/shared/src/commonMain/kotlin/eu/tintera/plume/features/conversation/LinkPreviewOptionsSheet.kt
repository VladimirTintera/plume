package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.link_preview_above
import eu.tintera.plume.resources.link_preview_above_desc
import eu.tintera.plume.resources.link_preview_below
import eu.tintera.plume.resources.link_preview_below_desc
import eu.tintera.plume.resources.link_preview_large
import eu.tintera.plume.resources.link_preview_large_desc
import eu.tintera.plume.resources.link_preview_remove
import eu.tintera.plume.resources.link_preview_remove_desc
import eu.tintera.plume.resources.link_preview_section_media
import eu.tintera.plume.resources.link_preview_section_position
import eu.tintera.plume.resources.link_preview_small
import eu.tintera.plume.resources.link_preview_small_desc
import eu.tintera.plume.resources.link_preview_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Volby náhledu odkazu v rozepsané zprávě: velikost obrázku, poloha, nebo bez náhledu.
 *
 * **Tatáž anatomie jako [AttachmentModeSheet]** (sekce s popisky, řádky s ikonou a fajfkou):
 * je to totéž o patro vedle — „jak má odejít to, co píšu" —, a dva vzhledy by se učily zvlášť.
 *
 * **Výběr sheet nezavírá**, na rozdíl od režimu přílohy. Velikost a poloha jsou dvě nezávislé
 * volby a kdo mění obě, by sheet otevíral dvakrát. Zavře ho jen „Bez náhledu".
 *
 * Velikost se nabízí jen tam, kde ji server umí ([LinkPreview.canChangeSize]) a náhled obrázek
 * má; jinak by volba nic nezměnila. Nevybraná velikost ukazuje jako zvolenou tu, kterou vybral
 * server — prázdný výběr by vypadal jako chyba.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkPreviewOptionsSheet(
    preview: LinkPreview,
    options: LinkPreviewOptions,
    onChange: (LinkPreviewOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    /*
     * Celý, bez napůl rozbaleného stavu: s oběma sekcemi je sheet vyšší než polovina obrazovky
     * a napůl rozbalený schoval „Poslat bez náhledu" pod okraj — ověřeno na emulátoru.
     */
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val large = options.largeMedia ?: preview.largePhoto

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.link_preview_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = preview.title ?: preview.siteName ?: preview.url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (preview.canChangeSize && preview.photo != null) {
                Section(Res.string.link_preview_section_media) {
                    ModeOptionRow(
                        title = stringResource(Res.string.link_preview_large),
                        description = stringResource(Res.string.link_preview_large_desc),
                        icon = Res.drawable.image_24dp,
                        selected = large,
                        shape = GroupTop,
                        onClick = { onChange(options.copy(largeMedia = true)) },
                    )
                    ModeOptionRow(
                        title = stringResource(Res.string.link_preview_small),
                        description = stringResource(Res.string.link_preview_small_desc),
                        icon = Res.drawable.link_24dp,
                        selected = !large,
                        shape = GroupBottom,
                        onClick = { onChange(options.copy(largeMedia = false)) },
                    )
                }
            }

            Section(Res.string.link_preview_section_position) {
                ModeOptionRow(
                    title = stringResource(Res.string.link_preview_below),
                    description = stringResource(Res.string.link_preview_below_desc),
                    icon = Res.drawable.keyboard_arrow_down_24dp,
                    selected = !options.aboveText,
                    shape = GroupTop,
                    onClick = { onChange(options.copy(aboveText = false)) },
                )
                ModeOptionRow(
                    title = stringResource(Res.string.link_preview_above),
                    description = stringResource(Res.string.link_preview_above_desc),
                    icon = Res.drawable.keyboard_arrow_up_24dp,
                    selected = options.aboveText,
                    shape = GroupBottom,
                    onClick = { onChange(options.copy(aboveText = true)) },
                )
            }

            ModeOptionRow(
                title = stringResource(Res.string.link_preview_remove),
                description = stringResource(Res.string.link_preview_remove_desc),
                icon = Res.drawable.close_24dp,
                selected = false,
                shape = RoundedCornerShape(20.dp),
                onClick = {
                    onChange(options.copy(disabled = true))
                    onDismiss()
                },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(title: StringResource, content: @Composable () -> Unit) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
    Spacer(Modifier.height(16.dp))
}

private val GroupTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val GroupBottom = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
