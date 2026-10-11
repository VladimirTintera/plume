package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_send_options
import eu.tintera.plume.resources.history_toggle_off_24dp
import eu.tintera.plume.resources.stickers_recent_remove
import eu.tintera.plume.resources.menu_favorite_add
import eu.tintera.plume.resources.menu_favorite_remove
import eu.tintera.plume.resources.schedule_send_24dp
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.star_filled_24dp
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Náhled podržené samolepky přes celou obrazovku: samolepka velká a pod ní akce.
 *
 * Buňka mřížky má 76 dp a samolepka se v ní dá jen tušit; kdo ji podrží, chce si ji prohlédnout.
 * Akce (poslat, oblíbené, volby odeslání) jsou proto tady, pod velkou samolepkou, a ne jako
 * drobná tlačítka v rozích buňky — na ta se špatně trefovalo a zakrývala samolepku.
 *
 * Ťuknutí kamkoli mimo tlačítka (i na samolepku) a zpět náhled zavřou. Sheet pod ním zůstává.
 */
@Composable
internal fun StickerPreviewDialog(
    sticker: StickerItem,
    favorite: Boolean,
    /**
     * Popisek hlavního tlačítka: „Poslat", ve formuláři ankety „Vybrat". `null` = tlačítko není
     * (sada otevřená z Nastavení, kde se nikam neposílá).
     */
    pickLabel: String?,
    /** Volby odeslání (bez zvuku, naplánovat…); `null` = tlačítko není (sada, anketa). */
    sendMenu: SendMenuOffer?,
    onPick: () -> Unit,
    onPickWith: (SendMenuChoice) -> Unit,
    /** `null` = hvězdička není (výsledek inline bota: posílá se výsledek, ne samolepka). */
    onToggleFavorite: (() -> Unit)?,
    /** Samolepka je mezi nedávnými — náhled nabídne odebrání. */
    recent: Boolean,
    onRemoveRecent: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Odchod se nejdřív dohraje, teprve pak dialog zmizí — zmizení bez animace vypadá jako pád.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { shown.targetState = false }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(shown) {
        snapshotFlow { !shown.targetState && !shown.currentState && shown.isIdle }.first { it }
        dismiss()
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = close),
        ) {
            AnimatedVisibility(
                visibleState = shown,
                // Vyroste z menšího jako buňka, ze které přišla; bez přeběhu u průhlednosti.
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = PREVIEW_START_SCALE) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = PREVIEW_START_SCALE) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    StickerPreview(sticker.preview, playing = true, modifier = Modifier.size(PREVIEW_SIZE))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Poslat zavírá i sheet, náhled se proto neanimuje — odjede s ním.
                        pickLabel?.let { Button(onClick = onPick) { Text(it) } }
                        if (onToggleFavorite != null) FilledTonalIconToggleButton(
                            checked = favorite,
                            onCheckedChange = { onToggleFavorite() },
                            shapes = IconButtonDefaults.toggleableShapes(),
                        ) {
                            Icon(
                                painterResource(if (favorite) Res.drawable.star_filled_24dp else Res.drawable.star_24dp),
                                contentDescription = stringResource(
                                    if (favorite) Res.string.menu_favorite_remove else Res.string.menu_favorite_add,
                                ),
                            )
                        }
                        sendMenu?.let { offer -> SendOptionsIconButton(offer, onPickWith) }
                        /*
                         * Odebrání náhled ZAVŘE, na rozdíl od hvězdičky: samolepka z mřížky pod
                         * ním odejde a náhled samolepky, která v nedávných už není, by visel ve
                         * vzduchu. Nevratné to není — příští odeslání ji tam vrátí —, proto
                         * bez potvrzení.
                         */
                        if (recent) {
                            FilledTonalIconButton(
                                onClick = {
                                    onRemoveRecent()
                                    close()
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    painterResource(Res.drawable.history_toggle_off_24dp),
                                    contentDescription = stringResource(Res.string.stickers_recent_remove),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tlačítko voleb odeslání v náhledu; nic, když [offer] žádnou volbu nedovolí. */
@Composable
internal fun SendOptionsIconButton(offer: SendMenuOffer, onChoice: (SendMenuChoice) -> Unit) {
    if (!offer.hasSendOptions()) return
    Box {
        var menuOpen by remember { mutableStateOf(false) }
        FilledTonalIconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(Res.drawable.schedule_send_24dp),
                contentDescription = stringResource(Res.string.conversation_send_options),
            )
        }
        SendOptionsMenu(
            offer = offer,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onChoice = onChoice,
        )
    }
}

/**
 * Náhled nad mřížkou samolepek ([StickerCells]), když nějaká je podržená. Poslat a volby odeslání
 * náhled zavřou rovnou (zavírá se i sheet), odebrání z nedávných animací, hvězdička ho nechá otevřený — stav oblíbení se
 * přepíše, až ho potvrdí TDLib.
 */
@Composable
internal fun StickerPreviewHost(
    sticker: StickerItem?,
    cells: StickerCells,
    pickLabel: String?,
    onClose: () -> Unit,
) {
    sticker ?: return
    StickerPreviewDialog(
        sticker = sticker,
        favorite = sticker.sendFileId in cells.favoriteFileIds,
        pickLabel = pickLabel,
        sendMenu = cells.sendMenu,
        onPick = {
            onClose()
            cells.onPick(sticker)
        },
        onPickWith = { choice ->
            onClose()
            cells.onPickWith(sticker, choice)
        },
        onToggleFavorite = { cells.onToggleFavorite(sticker) },
        recent = sticker.sendFileId in cells.recentFileIds,
        onRemoveRecent = { cells.onRemoveRecent(sticker) },
        onDismiss = onClose,
    )
}

/** Na telefonu přes dvě třetiny šířky — dost, aby bylo vidět, co samolepka dělá. */
private val PREVIEW_SIZE = 240.dp

private const val PREVIEW_START_SCALE = 0.4f
