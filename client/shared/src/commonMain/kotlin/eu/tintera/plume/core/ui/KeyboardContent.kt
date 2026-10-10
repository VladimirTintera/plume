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

    /** Soubor přetažený z jiné aplikace — čeká nad polem jako soubor z výběru. */
    data class File(val document: OutgoingAttachment.Document) : KeyboardContent
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
 *
 * **Totéž přijme přetažením** z jiné aplikace (rozdělená obrazovka na Foldu nebo tabletu):
 * obrázek a GIF stejnou cestou jako ze schránky, s [acceptFiles] i jiný soubor jako přílohu.
 * Jen Android; na iOS zatím nic.
 */
@Composable
expect fun KeyboardContentReceiver(
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    acceptFiles: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    content: @Composable () -> Unit,
)

/** Co leží ve schránce — podle toho se návrh vložení popíše. */
enum class ClipboardMediaKind { Animation, Image }

/**
 * Návrh vložit obrázek nebo GIF ze schránky tlačítkem, které kreslí SYSTÉM — na iOS
 * `UIPasteControl`. Jen tak se vložení obejde bez dotazu „Povolit vložení?": iOS ho neklade,
 * když uživatel ťukne na jeho vlastní tlačítko, kdežto položka „Vložit GIF" v kontextové nabídce
 * ([KeyboardContentReceiver]) je naše akce a dotaz vyvolá.
 *
 * [container] dostane druh obsahu a tlačítko k umístění; volá se jen tehdy, když je [active]
 * (pole má fokus) a schránka nese typ, který chat přijme, a který se ještě nevložil. Android
 * nekreslí nic — tam se na vložení nikdo neptá a stačí položka v nabídce.
 */
@Composable
expect fun ClipboardPasteSuggestion(
    active: Boolean,
    acceptAnimations: Boolean,
    acceptImages: Boolean,
    onContent: (KeyboardContent) -> Unit,
    onError: (UiText) -> Unit,
    container: @Composable (kind: ClipboardMediaKind, pasteButton: @Composable () -> Unit) -> Unit,
)
