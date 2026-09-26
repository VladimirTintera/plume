package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.InlineQueryResult
import dev.g000sha256.tdl.dto.InlineQueryResultAnimation
import dev.g000sha256.tdl.dto.InlineQueryResultArticle
import dev.g000sha256.tdl.dto.InlineQueryResultAudio
import dev.g000sha256.tdl.dto.InlineQueryResultContact
import dev.g000sha256.tdl.dto.InlineQueryResultDocument
import dev.g000sha256.tdl.dto.InlineQueryResultGame
import dev.g000sha256.tdl.dto.InlineQueryResultLocation
import dev.g000sha256.tdl.dto.InlineQueryResultPhoto
import dev.g000sha256.tdl.dto.InlineQueryResultSticker
import dev.g000sha256.tdl.dto.InlineQueryResultVenue
import dev.g000sha256.tdl.dto.InlineQueryResultVideo
import dev.g000sha256.tdl.dto.InlineQueryResultVoiceNote
import dev.g000sha256.tdl.dto.InlineQueryResults
import dev.g000sha256.tdl.dto.InlineQueryResultsButtonTypeStartBot
import dev.g000sha256.tdl.dto.InlineQueryResultsButtonTypeWebApp
import dev.g000sha256.tdl.dto.Photo

/** Stránka výsledků v doménové podobě. Výsledek, který neumíme ukázat, se vynechá. */
internal fun InlineQueryResults.toPage(): InlineResultsPage = InlineResultsPage(
    queryId = inlineQueryId,
    results = results.filterNotNull().mapNotNull { it.toDomain() },
    nextOffset = nextOffset,
    button = button?.let { button ->
        // Bez `else`: nový druh tlačítka v novější TDLib má rozbít překlad, ne tiše zmizet.
        when (val type = button.type) {
            is InlineQueryResultsButtonTypeStartBot -> InlineResultsButton.StartBot(button.text, type.parameter)
            is InlineQueryResultsButtonTypeWebApp -> InlineResultsButton.WebApp(button.text)
        }
    },
)

/**
 * Jeden výsledek. `null` jen u fotky bez jediné velikosti — nemá co ukázat.
 *
 * `when` bez `else` ze stejného důvodu jako u tlačítka.
 */
private fun InlineQueryResult.toDomain(): InlineResult? = when (this) {
    is InlineQueryResultAnimation -> InlineResult.Gif(id, animation.toMedia())
    is InlineQueryResultSticker -> InlineResult.Sticker(id, sticker.toMedia())
    is InlineQueryResultPhoto -> photo.gridFileId()?.let { fileId ->
        InlineResult.Picture(id, fileId, photo.minithumbnail?.toDomain(), isVideo = false)
    }
    // Obal videa je obrázek; bez něj zbyde řádek s titulkem, ať výsledek nezmizí.
    is InlineQueryResultVideo -> video.thumbnail.picture()?.file?.id?.let { fileId ->
        InlineResult.Picture(id, fileId, video.minithumbnail?.toDomain(), isVideo = true)
    } ?: InlineResult.Row(id, InlineRowKind.File, title.ifBlank { video.fileName }, description, null)

    is InlineQueryResultArticle ->
        InlineResult.Row(id, InlineRowKind.Article, title, description.ifBlank { url }, thumbnail.picture()?.file?.id)
    is InlineQueryResultDocument -> InlineResult.Row(
        id = id,
        kind = InlineRowKind.File,
        title = title.ifBlank { document.fileName },
        description = description,
        thumbnailFileId = document.thumbnail.picture()?.file?.id,
    )
    is InlineQueryResultAudio -> InlineResult.Row(
        id = id,
        kind = InlineRowKind.Audio,
        title = audio.title.ifBlank { audio.fileName },
        description = audio.performer,
        thumbnailFileId = audio.albumCoverThumbnail.picture()?.file?.id,
    )
    is InlineQueryResultVoiceNote -> InlineResult.Row(id, InlineRowKind.Voice, title, "", null)
    is InlineQueryResultContact -> InlineResult.Row(
        id = id,
        kind = InlineRowKind.Contact,
        title = listOf(contact.firstName, contact.lastName).filter { it.isNotBlank() }.joinToString(" "),
        description = contact.phoneNumber,
        thumbnailFileId = thumbnail.picture()?.file?.id,
    )
    is InlineQueryResultLocation -> InlineResult.Row(id, InlineRowKind.Location, title, "", thumbnail.picture()?.file?.id)
    is InlineQueryResultVenue -> InlineResult.Row(
        id = id,
        kind = InlineRowKind.Location,
        title = venue.title,
        description = venue.address,
        thumbnailFileId = thumbnail.picture()?.file?.id,
    )
    is InlineQueryResultGame -> InlineResult.Row(id, InlineRowKind.Game, game.title, game.description, null)
}

/**
 * Velikost fotky do buňky mřížky: nejmenší, která buňku ještě pokryje ostře
 * ([GRID_PHOTO_EDGE]), jinak největší, co je. Originál by kvůli buňce velké sto bodů stahoval
 * megabajty.
 */
private fun Photo.gridFileId(): Int? {
    val sizes = sizes.filterNotNull().ifEmpty { return null }
    return (
        sizes.filter { minOf(it.width, it.height) >= GRID_PHOTO_EDGE }.minByOrNull { it.width * it.height }
            ?: sizes.maxByOrNull { it.width * it.height }
        )?.photo?.id
}

/** Kratší hrana varianty, která na buňku mřížky stačí (buňka má kolem 100 dp, tedy ~300 px). */
private const val GRID_PHOTO_EDGE = 240
