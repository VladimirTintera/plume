package eu.tintera.plume.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
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
import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android: Photo Picker (`PickMultipleVisualMedia`) na fotky, `OpenMultipleDocuments` na soubory.
 *
 * Photo Picker nepotřebuje žádné oprávnění ke čtení médií a na starších Androidech ho dodává
 * aktualizace Google Play služeb, jinak spadne na systémový výběr dokumentů.
 */
@Composable
actual fun rememberAttachmentPicker(
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onError: (String) -> Unit,
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
            if (prepared.size < results.size) failed("Některý soubor se nepodařilo načíst.")
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = PICK_LIMIT),
    ) { uris -> prepare(uris, ::preparePhoto) }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        prepare(uris.take(PICK_LIMIT), ::prepareDocument)
    }

    return remember(photoLauncher, documentLauncher) {
        object : AttachmentPicker {
            override fun pickPhoto() =
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

            override fun pickDocument() = documentLauncher.launch(arrayOf("*/*"))
        }
    }
}

/**
 * Zmenší fotku na [PHOTO_MAX_EDGE] a uloží jako JPEG.
 *
 * **Orientace se musí použít, ne zahodit.** Fotka z foťáku je uložená naležato a otočení nese EXIF;
 * přepsaný JPEG EXIF nemá, takže bez otočení pixelů by odešla bokem. `ImageDecoder` (Android 9+)
 * orientaci použije sám, pro starší se otáčí podle `ExifInterface`.
 */
private fun preparePhoto(context: Context, uri: Uri): OutgoingAttachment.Photo {
    val bitmap = decodeScaled(context, uri)
    val target = outgoingFile(context, "photo.jpg")
    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
    return OutgoingAttachment.Photo(path = target.absolutePath, width = bitmap.width, height = bitmap.height)
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
private fun prepareDocument(context: Context, uri: Uri): OutgoingAttachment.Document {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?.takeIf { it.isNotBlank() }
        ?: "soubor"
    val target = outgoingFile(context, name)
    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        target.outputStream().use { input.copyTo(it) }
    }
    return OutgoingAttachment.Document(path = target.absolutePath)
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
