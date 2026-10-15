package eu.tintera.plume.features.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.localImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.share_chip_discard
import eu.tintera.plume.resources.share_chip_failed
import eu.tintera.plume.resources.share_chip_insert
import eu.tintera.plume.resources.share_chip_label
import eu.tintera.plume.resources.share_files
import eu.tintera.plume.resources.share_preparing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh s čipem „Sdílíte: …" nahoře v shellu — obsah sdílený bez vybraného chatu, který se
 * s uživatelem nese po aplikaci (seznam chatů, archiv, konverzace, profil).
 *
 * **Pruh zabírá vlastní místo, nic nepřekrývá.** Leží pod stavovou lištou a obrazovky pod ním
 * se posunou (shell jim horní inset zkonzumuje), takže se nepere ani s hlavičkou s pilulkou
 * seznamu chatů, ani s hlavičkou a vstupním polem konverzace — dole by se potkal s plovoucí
 * lištou složek a s polem, nahoře přes hlavičku by zakryl hledání. Ve dvoupanelu je jeden přes
 * celou šířku, ne dva v obou panelech.
 *
 * [visibleState] řídí shell: pruh se rozbalí a sbalí animací, ale místo pod stavovou lištou
 * (`Spacer`) drží po celou dobu, kdy je ve stromu — obrazovka pod ním tak na začátku ani na konci
 * animace neposkočí o výšku stavové lišty.
 *
 * [insertChatId] je konverzace, která je zrovna vidět: jen tam má čip „Vložit sem" ([onInsert]).
 * Jinde jen připomíná, co se sdílí. ✕ ([onDiscard]) obsah zahodí i s kopiemi souborů.
 */
@Composable
fun SharedContentStrip(
    visibleState: MutableTransitionState<Boolean>,
    content: SharedContent?,
    insertChatId: Long?,
    onInsert: (chatId: Long) -> Unit,
    onDiscard: (id: Long) -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
) {
    // Při sbalování (✕, vloženo) už obsah není, ale pruh ho má ukazovat, dokud nezmizí.
    val last = remember { arrayOfNulls<SharedContent>(1) }
    if (content != null) last[0] = content
    val shown = last[0] ?: return
    val lastInsert = remember { arrayOfNulls<Long>(1) }
    if (insertChatId != null) lastInsert[0] = insertChatId

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Column(modifier.fillMaxWidth().background(background)) {
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.safeDrawing))
        AnimatedVisibility(
            visibleState = visibleState,
            enter = expandVertically(spatial, expandFrom = Alignment.Top) + fadeIn(effects),
            exit = shrinkVertically(spatial, shrinkTowards = Alignment.Top) + fadeOut(effects),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                SharedContentChip(
                    content = shown,
                    canInsert = insertChatId != null,
                    onInsert = { lastInsert[0]?.let(onInsert) },
                    onDiscard = { onDiscard(shown.id) },
                    modifier = Modifier.widthIn(max = ContentWidth.Sheet).fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Čip samotný. Je to **cizí vložený obsah**, tedy totéž co citace nad vstupním polem nebo popis
 * webu u náhledu odkazu: proto `tertiaryContainer`, jako dřív karta ve výběru chatu.
 *
 * **Náhled nebo ikona říká DRUH obsahu** (fotka / soubory / odkaz / text); dokud se soubory
 * připravují, je místo nich indikátor a vložit ještě nejde — „Připravuji soubory…" je průběh,
 * ne popis.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedContentChip(
    content: SharedContent,
    canInsert: Boolean,
    onInsert: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val attachments = content.attachments
    // Bez souborů i bez textu (všechno selhalo) není co vložit — zbývá jen ✕.
    val insertable = attachments != null && (attachments.isNotEmpty() || !content.text.isNullOrBlank())

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SharedContentPreview(content)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.share_chip_label),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Text(
                    text = sharedContentDescription(content),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = canInsert && insertable,
                enter = expandHorizontally(spatial) + fadeIn(effects),
                exit = shrinkHorizontally(spatial) + fadeOut(effects),
            ) {
                Button(
                    onClick = {
                        haptics.confirm()
                        onInsert()
                    },
                    shapes = ButtonDefaults.shapes(),
                    // Hlavní akce uvnitř tónované plochy téže rodiny barev.
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Text(stringResource(Res.string.share_chip_insert), maxLines = 1)
                }
            }
            IconButton(onClick = onDiscard, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.share_chip_discard),
                )
            }
        }
    }
}

/** Náhled první fotky nebo videa; jinak ikona druhu, při přípravě indikátor. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedContentPreview(content: SharedContent) {
    val attachments = content.attachments
    val image = when (val first = attachments?.firstOrNull()) {
        is OutgoingAttachment.Photo -> first.path
        is OutgoingAttachment.Video -> first.thumbnail?.path
        is OutgoingAttachment.Audio -> first.cover?.path
        else -> null
    }
    Box(Modifier.size(PREVIEW_SIZE), contentAlignment = Alignment.Center) {
        when {
            attachments == null -> LoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(PREVIEW_SIZE),
            )
            image != null -> AsyncImage(
                model = localImage(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(PREVIEW_SIZE).clip(MaterialTheme.shapes.small),
            )
            else -> Icon(painterResource(sharedContentIcon(content)), contentDescription = null)
        }
    }
}

/**
 * Co se sdílí, jedním řádkem: jméno souboru (jen jeden), počet souborů, nebo text sám.
 * Popisek k souborům se tu neukazuje — je to jen doprovod, podle souborů se pozná, co to je.
 */
@Composable
private fun sharedContentDescription(content: SharedContent): String {
    val attachments = content.attachments ?: return stringResource(Res.string.share_preparing)
    return when {
        attachments.size == 1 -> attachments.single().path.substringAfterLast('/').substringAfterLast('\\')
        attachments.size > 1 -> pluralStringResource(Res.plurals.share_files, attachments.size, attachments.size)
        !content.text.isNullOrBlank() -> content.text.lineSequence().first { it.isNotBlank() }.trim()
        else -> stringResource(Res.string.share_chip_failed)
    }
}

/** Soubory mají sponku, sdílený odkaz řetěz, holý text stránku — ikona má říct, co odejde. */
private fun sharedContentIcon(content: SharedContent) = when {
    !content.attachments.isNullOrEmpty() -> Res.drawable.attach_file_24dp
    content.text?.contains("://") == true -> Res.drawable.link_24dp
    else -> Res.drawable.description_24dp
}

/** Náhled zabere tolik co indikátor při přípravě — text vedle něj nepopojede. */
private val PREVIEW_SIZE = 40.dp
