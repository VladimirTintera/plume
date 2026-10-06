package eu.tintera.plume.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfilePhotoFile

/** Profilovka připravená k nahrání a obrázek, který ji ukazuje v náhledu (u videa jeho snímek). */
data class PreparedProfilePhoto(val file: ProfilePhotoFile, val preview: String)

/**
 * Nová profilovka z vybrané přílohy: napřed ořez ([PhotoCropOverlay]), pak čtvercový soubor.
 * Sdílí ji úprava vlastního profilu, úprava chatu a založení skupiny nebo kanálu — tři kopie
 * téhož postupu by se rozešly hned u první změny.
 *
 * **Video jde stejným ořezem jako fotka**: výřez se vybírá nad jeho prvním snímkem
 * ([ProfileVideo.frame]) a v podílech platí pro celé video.
 */
class ProfilePhotoCrop(
    private val squarePhoto: SquarePhoto,
    private val profileVideo: ProfileVideo,
) {
    /** Obrázek v ořezu (fotka, nebo snímek videa); `null` = ořez zavřený. */
    var image by mutableStateOf<String?>(null)
        private set

    /** Ořezává se video — ořez to řekne, protože se zkrátí a přijde o zvuk. */
    var isVideo by mutableStateOf(false)
        private set

    private var video: String? = null

    /**
     * Otevře ořez pro první přílohu. `false` = profilovkou být nemůže (soubor). U videa čeká na
     * vytažení snímku.
     */
    suspend fun open(attachments: List<OutgoingAttachment>): Boolean =
        when (val attachment = attachments.firstOrNull()) {
            is OutgoingAttachment.Photo -> {
                video = null
                isVideo = false
                image = attachment.path
                true
            }
            is OutgoingAttachment.Video -> {
                val frame = profileVideo.frame(attachment.path)
                video = attachment.path
                isVideo = true
                image = frame
                true
            }
            else -> false
        }

    fun cancel() {
        image = null
        video = null
    }

    /**
     * Ořízne podle vybraného výřezu. Ořez se zavře hned, ještě před první suspenzí, takže
     * druhé ťuknutí na „Hotovo" už nemá co ořezat.
     */
    suspend fun crop(region: CropRegion): PreparedProfilePhoto? {
        val shown = image ?: return null
        val source = video
        image = null
        video = null
        if (source == null) {
            val path = squarePhoto.crop(shown, region)
            return PreparedProfilePhoto(ProfilePhotoFile.Still(path), preview = path)
        }
        val animation = profileVideo.crop(source, region)
        // Náhled je týž výřez ze snímku: ukazuje přesně to, co bude statickou podobou fotky.
        val preview = squarePhoto.crop(shown, region)
        return PreparedProfilePhoto(ProfilePhotoFile.Animated(animation), preview = preview)
    }
}
