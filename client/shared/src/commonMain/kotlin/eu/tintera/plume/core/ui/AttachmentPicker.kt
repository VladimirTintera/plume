package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Výběr fotek, videí nebo souborů k odeslání — víc najednou, nejvýš [PICK_LIMIT].
 *
 * Video se nepřekódovává, jen se zkopíruje (iOS ho přebalí do MP4 bez překódování) a přečtou
 * se z něj rozměry, délka a první snímek jako náhled ([VIDEO_THUMBNAIL_EDGE]).
 *
 * Platforma vybere soubor systémovým výběrem (Android Photo Picker / výběr dokumentu, iOS PHPicker /
 * UIDocumentPicker) a **zkopíruje ho do cache aplikace** — TDLib posílá podle cesty a systémové URI
 * neotevře. Fotka se přitom zmenší a uloží jako JPEG (viz [PHOTO_MAX_EDGE]); soubor zůstane beze
 * změny. Žádné oprávnění to nepotřebuje: oba systémové výběry dávají přístup jen k tomu, co
 * uživatel vybral.
 */
interface AttachmentPicker {
    /** Fotky a videa. */
    fun pickMedia()
    fun pickDocument()

    /**
     * Jen zvukové soubory (hudba do profilu). Kde systémový výběr filtr nemá (desktop), je to
     * obyčejný výběr souboru — co není skladba, odmítne volající.
     */
    fun pickAudio() = pickDocument()

    /**
     * Zařízení má fotoaparát a platforma umí vyfotit nebo natočit rovnou do zprávy ([capturePhoto],
     * [captureVideo]). Desktop ne; iOS simulátor kameru nemá, takže tam taky ne.
     */
    val canCapture: Boolean get() = false

    /**
     * Vyfotit systémovým fotoaparátem. Fotka přijde do `onPicked` stejně jako vybraná z galerie,
     * takže čeká nad polem na popisek a jde upravit, odebrat nebo přidat k albu.
     */
    fun capturePhoto() {}

    /** Natočit video systémovým fotoaparátem; přijde do `onPicked` jako vybrané video. */
    fun captureVideo() {}
}

/**
 * [onPicked] dostane přílohy připravené k odeslání **v pořadí výběru** (připravují se souběžně),
 * [onError] hlášku, když se některý soubor nepodařilo připravit — ostatní se přesto předají.
 * Zrušení výběru nevolá nic.
 */
@Composable
expect fun rememberAttachmentPicker(
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onError: (UiText) -> Unit,
): AttachmentPicker

/**
 * Delší strana odesílané fotky.
 *
 * TDLib přijme fotku nejvýš 10 MB a do 10 000 px; fotka z foťáku bývá větší. Telegram ji stejně
 * zmenšuje na 2560 px, takže větší rozlišení by jen prodloužilo nahrávání.
 */
const val PHOTO_MAX_EDGE = 2560

/** Kvalita JPEG odesílané fotky. Pod ní jsou vidět artefakty, nad ní roste velikost bez přínosu. */
const val PHOTO_JPEG_QUALITY = 87

/** Delší strana náhledu videa. TDLib přijme náhled nejvýš 320 × 320. */
const val VIDEO_THUMBNAIL_EDGE = 320

/**
 * Kolik souborů jde vybrat najednou. Album v Telegramu má nejvýš 10 položek, víc se při odeslání
 * rozdělí do několika alb (`attachmentBatches`). Sto je i strop systémového výběru fotek na
 * Androidu (`MediaStore.getPickImagesMaxLimit`).
 */
const val PICK_LIMIT = 100

/**
 * Na čem se vybrané přílohy připravují (kopie, zmenšení fotky, náhled videa): **nejvýš čtyři
 * naráz**. Se stropem 10 stačilo `Dispatchers.IO`, jenže to pustí až 64 souběžných dekódování
 * a sto fotek z foťáku po ~50 MB v bitmapě by na telefonu skončilo pádem na paměť. Pořadí výběru
 * dál drží `awaitAll`.
 */
val AttachmentPrepareDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

/** Podadresář cache pro připravené přílohy. */
const val OUTGOING_DIRECTORY = "outgoing"
