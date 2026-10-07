package eu.tintera.plume.core.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfilePhotoFile

/** Profilovka připravená k nahrání a obrázek, který ji ukazuje v náhledu (u videa jeho snímek). */
data class PreparedProfilePhoto(val file: ProfilePhotoFile, val preview: String)

/**
 * Volba úseku videa a snímku pro statickou podobu profilovky. Drží ji [ProfilePhotoCrop],
 * kreslí [PhotoCropOverlay] (pás snímků pod ořezem).
 */
@Stable
interface VideoCropControl {
    val trim: VideoTrim

    /** Posune úsek; snímek jede s ním ([VideoTrim.movedTo]). */
    fun moveTo(startMs: Long)

    /** Vybere snímek pro statickou fotku; sevře se do úseku. */
    fun chooseCover(atMs: Long)

    /** Snímek videa — pro ořez i pro dlaždice pásu ([ProfileVideo.frame]). */
    suspend fun frame(atMs: Long, edge: Int): String
}

/**
 * Nová profilovka z vybrané přílohy: napřed ořez ([PhotoCropOverlay]), pak čtvercový soubor.
 * Sdílí ji úprava vlastního profilu, úprava chatu a založení skupiny nebo kanálu — tři kopie
 * téhož postupu by se rozešly hned u první změny.
 *
 * **Video jde stejným ořezem jako fotka**: výřez se vybírá nad snímkem ([ProfileVideo.frame])
 * a v podílech platí pro celé video. Navíc se volí úsek a snímek pro statickou podobu
 * ([video]); ten se ukazuje v ořezu, takže výřez se vybírá právě nad ním.
 */
class ProfilePhotoCrop(
    private val squarePhoto: SquarePhoto,
    private val profileVideo: ProfileVideo,
) {
    /** Obrázek v ořezu (fotka, nebo první snímek videa); `null` = ořez zavřený. */
    var image by mutableStateOf<String?>(null)
        private set

    /** Úsek a snímek, když se ořezává video; `null` u fotky. */
    var video by mutableStateOf<VideoCropControl?>(null)
        private set

    /**
     * Otevře ořez pro první přílohu. `false` = profilovkou být nemůže (soubor). U videa čeká na
     * vytažení snímku a délky.
     */
    suspend fun open(attachments: List<OutgoingAttachment>): Boolean =
        when (val attachment = attachments.firstOrNull()) {
            is OutgoingAttachment.Photo -> {
                video = null
                image = attachment.path
                true
            }
            is OutgoingAttachment.Video -> {
                val frame = profileVideo.frame(attachment.path)
                // Délku z výběru příloh má video jen v celých sekundách; přesnou zná soubor.
                val duration = profileVideo.durationMs(attachment.path)
                    .takeIf { it > 0 } ?: (attachment.durationSeconds * 1000L)
                video = Control(attachment.path, VideoTrim(duration))
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
        val control = video as Control?
        image = null
        video = null
        if (control == null) {
            val path = squarePhoto.crop(shown, region)
            return PreparedProfilePhoto(ProfilePhotoFile.Still(path), preview = path)
        }
        val trim = control.trim
        val animation = profileVideo.crop(control.path, region, trim.startMs)
        // Náhled je týž výřez z vybraného snímku: ukazuje přesně to, co bude statickou
        // podobou fotky (server ji vyrobí z téhož času).
        val preview = squarePhoto.crop(profileVideo.frame(control.path, trim.coverMs), region)
        return PreparedProfilePhoto(
            ProfilePhotoFile.Animated(animation, mainFrameSeconds = trim.mainFrameSeconds),
            preview = preview,
        )
    }

    private inner class Control(val path: String, initial: VideoTrim) : VideoCropControl {
        override var trim by mutableStateOf(initial)
            private set

        override fun moveTo(startMs: Long) {
            trim = trim.movedTo(startMs)
        }

        override fun chooseCover(atMs: Long) {
            trim = trim.withCover(atMs)
        }

        override suspend fun frame(atMs: Long, edge: Int): String = profileVideo.frame(path, atMs, edge)
    }
}
