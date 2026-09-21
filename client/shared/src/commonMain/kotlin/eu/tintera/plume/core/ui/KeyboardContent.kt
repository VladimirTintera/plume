package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.OutgoingAnimation
import eu.tintera.plume.core.telegram.OutgoingAttachment

/** Obsah, který klávesnice vložila do pole místo textu (GIF, samolepka, obrázek). */
sealed interface KeyboardContent {
    /** GIF nebo MP4 bez zvuku — odchází hned, jako animace. */
    data class Animation(val animation: OutgoingAnimation) : KeyboardContent

    /** Statický obrázek (i samolepka z klávesnice) — čeká nad polem jako fotka z výběru. */
    data class Image(val photo: OutgoingAttachment.Photo) : KeyboardContent
}

/**
 * Řekne klávesnici, že pole v [content] přijme obrázky a GIFy, a vložený obsah připraví k odeslání.
 * Totéž platí pro obrázek nebo GIF ve schránce: kontextová nabídka pole dostane položku
 * „Vložit GIF / obrázek" (systémové „Vložit" umí jen text).
 *
 * Bez toho Gboard u GIFu hlásí „tato aplikace nepodporuje animované GIFy": co pole přijme, čte
 * z `EditorInfo.contentMimeTypes` a to naše pole nevyplňovalo. `Modifier.contentReceiver` z Compose
 * tu nejde — funguje jen s polem nad `TextFieldState`, a náš `BasicTextField` stojí na
 * `TextFieldValue` (viz „Koncept"). Android proto obaluje vstupní spojení přes
 * `InterceptPlatformTextInput`. Na iOS klávesnice obsah tímhle způsobem neposílá; [content] se
 * jen složí.
 *
 * [acceptAnimations] a [acceptImages] se nabízejí klávesnici — co chat nepřijme, klávesnice
 * nenabídne. Bez obojího se nic neobaluje. Obsah se kopíruje do cache hned (oprávnění k URI od
 * klávesnice je dočasné), mimo hlavní vlákno; [onError] dostane hlášku, když se to nepovede.
 */
@Composable
expect fun KeyboardContentReceiver(
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    content: @Composable () -> Unit,
)
