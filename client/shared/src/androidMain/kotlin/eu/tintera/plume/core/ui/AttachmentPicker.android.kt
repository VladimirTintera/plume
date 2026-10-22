package eu.tintera.plume.core.ui

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.OutgoingAnimation
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.OutgoingThumbnail
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_prepare_failed
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Android: Photo Picker (`PickMultipleVisualMedia`) na fotky a videa, `OpenMultipleDocuments` na soubory.
 *
 * Photo Picker nepotřebuje žádné oprávnění ke čtení médií a na starších Androidech ho dodává
 * aktualizace Google Play služeb, jinak spadne na systémový výběr dokumentů.
 */
@Composable
actual fun rememberAttachmentPicker(
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onError: (UiText) -> Unit,
): AttachmentPicker {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)

    fun prepare(uris: List<Uri>, block: (Context, Uri) -> OutgoingAttachment) {
        if (uris.isEmpty()) return
        scope.launch {
            // Kopie a zmenšení fotek jsou práce na disku a procesoru — mimo hlavní vlákno, souběžně.
            // `awaitAll` drží pořadí výběru.
            val results = uris
                .map { uri -> async(Dispatchers.IO) { runCatching { block(context, uri) } } }
                .awaitAll()
            results.forEach { result ->
                result.exceptionOrNull()?.let { failure ->
                    Logger.withTag("Attachments").w(failure) { "Přílohu se nepodařilo připravit." }
                }
            }
            val prepared = results.mapNotNull { it.getOrNull() }
            if (prepared.isNotEmpty()) picked(prepared)
            if (prepared.size < results.size) failed(UiText(Res.string.attachment_prepare_failed))
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = PICK_LIMIT),
    ) { uris -> prepare(uris, ::prepareVisual) }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        prepare(uris.take(PICK_LIMIT), ::prepareFile)
    }
    val audioLauncher = rememberLauncherForActivityResult(OpenAudio()) { uri ->
        uri?.let { prepare(listOf(it), ::prepareFile) }
    }

    return remember(photoLauncher, documentLauncher, audioLauncher) {
        object : AttachmentPicker {
            override fun pickMedia() =
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

            override fun pickDocument() = documentLauncher.launch(arrayOf("*/*"))

            override fun pickAudio() = audioLauncher.launch(arrayOf("audio/*"))
        }
    }
}

/**
 * Výběr jedné skladby. S filtrem na zvukové typy ukazuje systémový výběr v úvodních „Nedávných"
 * prázdno (ověřeno na emulátoru, Android 16) a ke skladbám by se muselo bočním menu; otevře se
 * proto rovnou v kořeni Audio poskytovatele médií (`EXTRA_INITIAL_URI`). Kde ho systém nemá,
 * extra ignoruje a začne, kde je zvyklý.
 */
private class OpenAudio : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildRootUri("com.android.providers.media.documents", "audio_root"),
        )
}

/** Photo Picker vrací fotky i videa pomíchané; rozhoduje typ obsahu. */
internal fun prepareVisual(context: Context, uri: Uri): OutgoingAttachment =
    if (context.contentResolver.getType(uri)?.startsWith("video/") == true) {
        prepareVideo(context, uri)
    } else {
        preparePhoto(context, uri)
    }

/**
 * Zkopíruje video beze změny a přečte z něj, co server sám nezjistí: rozměry, délku a první snímek.
 *
 * **Rozměry se otáčejí podle metadat.** Video z foťáku na výšku je uložené naležato s příznakem
 * otočení; bez prohození by bublina měla poměr stran na šířku. Snímek z `getFrameAtTime` už
 * otočený je.
 *
 * Nepřekódovává se (to by znamenalo MediaCodec a minuty práce u delšího videa). `streamable` je
 * `false`: u MP4 z foťáku bývá index na konci souboru, takže přehrávání během stahování nejde.
 */
private fun prepareVideo(context: Context, uri: Uri): OutgoingAttachment.Video {
    val name = displayName(context, uri) ?: "video.mp4"
    val target = outgoingFile(context, name)
    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        target.outputStream().use { input.copyTo(it) }
    }

    val video = videoMetadata(target)
    return OutgoingAttachment.Video(
        path = target.absolutePath,
        width = video.width,
        height = video.height,
        durationSeconds = video.durationSeconds,
        thumbnail = video.thumbnail,
        streamable = false,
    )
}

/**
 * Délka podle posledního vzorku. **Fragmentované MP4 délku v hlavičce nemá** (`mvhd` 0, bez
 * `mehd` — kolečko z kamery, viz `FragmentedVideoNoteMuxer`) a `MediaMetadataRetriever` pak
 * vrátí nulu; kolečko by se tvářilo jako vteřinové. `MediaExtractor` projde fragmenty a čas
 * posledního vzorku zná. Projde se jen hlavička vzorků, ne obsah — u minuty kolečka zlomek vteřiny.
 */
private fun lastSampleTimeMs(target: File): Long {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(target.absolutePath)
        for (track in 0 until extractor.trackCount) extractor.selectTrack(track)
        var lastUs = 0L
        while (extractor.sampleTime >= 0) {
            lastUs = maxOf(lastUs, extractor.sampleTime)
            if (!extractor.advance()) break
        }
        lastUs / 1000
    } catch (failure: Exception) {
        Logger.withTag("VideoMetadata").w(failure) { "Délku videa nejde dopočítat ze vzorků." }
        0L
    } finally {
        extractor.release()
    }
}

internal class VideoMetadata(val width: Int, val height: Int, val durationSeconds: Int, val thumbnail: OutgoingThumbnail?)

/** Rozměry (po otočení), délka a první snímek jako JPEG náhled vedle souboru. */
internal fun videoMetadata(target: File): VideoMetadata {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(target.absolutePath)
        fun metadata(key: Int) = retriever.extractMetadata(key)?.toIntOrNull() ?: 0
        val rawWidth = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val rawHeight = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val rotated = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: lastSampleTimeMs(target)

        val thumbnail = runCatching {
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@runCatching null
            val scale = minOf(1f, VIDEO_THUMBNAIL_EDGE.toFloat() / max(frame.width, frame.height))
            val scaled = Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).roundToInt().coerceAtLeast(1),
                (frame.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            val file = File(target.parentFile, "thumbnail.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
            OutgoingThumbnail(path = file.absolutePath, width = scaled.width, height = scaled.height)
        }.getOrNull()

        return VideoMetadata(
            width = if (rotated) rawHeight else rawWidth,
            height = if (rotated) rawWidth else rawHeight,
            durationSeconds = ((durationMs + 500) / 1000).toInt(),
            thumbnail = thumbnail,
        )
    } finally {
        retriever.release()
    }
}

/**
 * Zkopíruje GIF (nebo MP4 bez zvuku) z klávesnice beze změny. Nic se nepřevádí: skutečný GIF
 * převede na MP4 server. Z GIFu stačí rozměry (hlavička, bez dekódování snímků); z MP4 se jako
 * u videa čte i délka a první snímek.
 *
 * **Přípona musí sedět s obsahem.** Jméno od klávesnice bývá prázdné nebo bez přípony a TDLib
 * podle ní pozná, co nahrává.
 */
internal fun prepareAnimation(context: Context, uri: Uri, isVideo: Boolean): OutgoingAnimation {
    val extension = if (isVideo) "mp4" else "gif"
    val name = displayName(context, uri)
        ?.let { if (it.endsWith(".$extension", ignoreCase = true)) it else "$it.$extension" }
        ?: "animation.$extension"
    val target = outgoingFile(context, name)
    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        target.outputStream().use { input.copyTo(it) }
    }

    if (!isVideo) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "GIF nejde přečíst." }
        return OutgoingAnimation(
            path = target.absolutePath,
            width = bounds.outWidth,
            height = bounds.outHeight,
            durationSeconds = 0,
            thumbnail = null,
        )
    }

    val video = videoMetadata(target)
    return OutgoingAnimation(
        path = target.absolutePath,
        width = video.width,
        height = video.height,
        durationSeconds = video.durationSeconds,
        thumbnail = video.thumbnail,
    )
}

internal fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?.takeIf { it.isNotBlank() }

/**
 * Zmenší fotku na [PHOTO_MAX_EDGE] a uloží jako JPEG.
 *
 * **Orientace se musí použít, ne zahodit.** Fotka z foťáku je uložená naležato a otočení nese EXIF;
 * přepsaný JPEG EXIF nemá, takže bez otočení pixelů by odešla bokem. `ImageDecoder` (Android 9+)
 * orientaci použije sám, pro starší se otáčí podle `ExifInterface`.
 */
internal fun preparePhoto(context: Context, uri: Uri): OutgoingAttachment.Photo {
    val bitmap = decodeScaled(context, uri).opaque()
    val target = outgoingFile(context, "photo.jpg")
    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
    return OutgoingAttachment.Photo(path = target.absolutePath, width = bitmap.width, height = bitmap.height)
}

/**
 * JPEG průhlednost nemá a průhledné pixely z něj vyjdou ČERNÉ — samolepka z klávesnice nebo PNG
 * s průhledným pozadím by odešly na černém čtverci. Proto se průhledný obrázek skládá na bílou.
 */
private fun Bitmap.opaque(): Bitmap {
    if (!hasAlpha()) return this
    val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(this@opaque, 0f, 0f, null)
    }
    return flattened
}

private fun decodeScaled(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (width, height) = fitInto(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            // Softwarová bitmapa: hardwarová se nedá zkomprimovat do JPEG.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    // Nejbližší mocnina dvou nad cílem: načte se menší bitmapa a doškáluje se přesně níž.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_EDGE) sample *= 2
    val decoded = resolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Obrázek nejde dekódovat.")

    val rotation = resolver.openInputStream(uri).use { stream ->
        when (ExifInterface(requireNotNull(stream)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }
    val (width, height) = fitInto(decoded.width, decoded.height)
    val matrix = Matrix().apply {
        postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        postRotate(rotation)
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

/** Rozměry zmenšené tak, aby delší strana nepřesáhla [PHOTO_MAX_EDGE]. Menší fotka se nezvětšuje. */
private fun fitInto(width: Int, height: Int): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= PHOTO_MAX_EDGE) return width to height
    val scale = PHOTO_MAX_EDGE.toFloat() / longest
    return (width * scale).roundToInt() to (height * scale).roundToInt()
}

/** Zkopíruje soubor beze změny a se jménem, pod kterým ho uživatel vybral — to jméno TDLib pošle. */
internal fun prepareDocument(context: Context, uri: Uri): OutgoingAttachment.Document {
    val name = displayName(context, uri) ?: "soubor"
    val target = outgoingFile(context, name)
    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        target.outputStream().use { input.copyTo(it) }
    }
    return OutgoingAttachment.Document(path = target.absolutePath)
}

/**
 * Soubor ze sponky „Soubor": hudba odejde jako skladba (s názvem, interpretem a obalem ze
 * souboru), všechno ostatní beze změny jako soubor. Rozhoduje typ obsahu od systému.
 */
internal fun prepareFile(context: Context, uri: Uri): OutgoingAttachment {
    val document = prepareDocument(context, uri)
    if (context.contentResolver.getType(uri)?.startsWith("audio/") != true) return document
    // Soubor, který se jako skladba přečíst nedá, odejde aspoň jako soubor.
    return runCatching { audioMetadata(File(document.path)) }.getOrNull() ?: document
}

/** Název, interpret, délka a obal alba (JPEG do 320 px vedle souboru) ze značek skladby. */
private fun audioMetadata(target: File): OutgoingAttachment.Audio {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(target.absolutePath)
        fun text(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: lastSampleTimeMs(target)
        val cover = runCatching {
            val bytes = retriever.embeddedPicture ?: return@runCatching null
            val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val scale = minOf(1f, VIDEO_THUMBNAIL_EDGE.toFloat() / max(picture.width, picture.height))
            val scaled = Bitmap.createScaledBitmap(
                picture,
                (picture.width * scale).roundToInt().coerceAtLeast(1),
                (picture.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            val file = File(target.parentFile, "cover.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
            OutgoingThumbnail(path = file.absolutePath, width = scaled.width, height = scaled.height)
        }.getOrNull()
        return OutgoingAttachment.Audio(
            path = target.absolutePath,
            title = text(MediaMetadataRetriever.METADATA_KEY_TITLE),
            performer = text(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            durationSeconds = ((durationMs + 500) / 1000).toInt(),
            cover = cover,
        )
    } finally {
        retriever.release()
    }
}

/**
 * Soubor v cache aplikace. Každá příloha má vlastní podadresář, takže dva soubory stejného jména
 * se nepřepíšou a jméno zůstane přesně takové, jaké bylo. Cache smí systém vyčistit — TDLib si
 * soubor při odeslání načte a nahrává ze své kopie.
 */
private fun outgoingFile(context: Context, name: String): File {
    val directory = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}").apply { mkdirs() }
    // Jméno z cizího zdroje: bez lomítek, aby nemohlo vést mimo adresář.
    return File(directory, name.replace('/', '_').replace('\\', '_'))
}
