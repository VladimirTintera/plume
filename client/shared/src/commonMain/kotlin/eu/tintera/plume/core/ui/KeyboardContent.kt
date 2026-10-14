package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    /**
     * Víc příloh naráz (přetažené nebo zkopírované soubory na desktopu) — čekají nad polem
     * jako z výběru. Jedním kusem, ne po jedné: další příloha jiného druhu by předchozí nahradila.
     */
    data class Attachments(val attachments: List<OutgoingAttachment>) : KeyboardContent
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
 * Na iOS zatím nic.
 *
 * **Desktop** klávesnici obsahu nemá: Cmd/Ctrl+V v poli vloží obrázek ze schránky (snímek
 * obrazovky) nebo zkopírované soubory, a přetažené soubory přijme celá [ContentDropArea] kolem.
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

/**
 * Plocha, na kterou jde pustit soubory pro pole uvnitř ([KeyboardContentReceiver]) — na desktopu
 * celá konverzace, ne jen pole: z Finderu nebo Průzkumníka se táhne myší přes velké okno a trefovat
 * se do jednoho řádku by bylo pracné. Přijímá jen, dokud je pole složené, a podle JEHO práv; bez
 * pole (kanál jen ke čtení, výběr zpráv) přetažení odmítne. Během tažení kreslí výzvu přes celou
 * plochu ([ContentDropOverlay]).
 *
 * Android a iOS jen skládají obsah: Android bere přetažení přímo na pole (na mobilu se táhne
 * prstem z vedlejší aplikace a pole je cíl dost velký), iOS zatím nic.
 */
@Composable
expect fun ContentDropArea(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
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
