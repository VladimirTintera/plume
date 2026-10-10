package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.NetworkType
import dev.g000sha256.tdl.dto.NetworkTypeMobile
import dev.g000sha256.tdl.dto.NetworkTypeMobileRoaming
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.settings.AutoDownloadLevel
import eu.tintera.plume.core.settings.AutoDownloadSettings
import eu.tintera.plume.core.settings.HIGH_BYTES
import eu.tintera.plume.core.settings.LOW_VIDEO_BYTES
import eu.tintera.plume.core.settings.MEDIUM_BYTES
import eu.tintera.plume.core.settings.limits
import kotlin.math.sqrt
import dev.g000sha256.tdl.dto.AutoDownloadSettings as TdAutoDownloadSettings

/**
 * Předvolby automatického stahování ÚČTU (`getAutoDownloadSettingsPresets` /
 * `setAutoDownloadSettings`), převedené na úrovně Plume.
 *
 * Server drží tři předvolby, „nízká, střední a vysoká spotřeba dat", a TDLib je při zápisu
 * mapuje podle sítě: roaming → nízká, Wi-Fi → vysoká, ostatní → střední
 * (`SaveAutoDownloadSettingsQuery` v `AutoDownloadSettings.cpp`). Totéž čtou oficiální klienti.
 */
interface AutoDownloadPresets {

    suspend fun load(): Result<AutoDownloadSettings>

    /** Zapíše jen sítě, jejichž úroveň se od serveru liší; ostatní pole předvolby nechá být. */
    suspend fun save(settings: AutoDownloadSettings): Result<Unit>
}

class TdlAutoDownloadPresets(private val holder: TelegramClientHolder) : AutoDownloadPresets {

    private val logger = Logger.withTag("Telegram")

    override suspend fun load(): Result<AutoDownloadSettings> =
        holder.client.getAutoDownloadSettingsPresets().toResult().map { presets ->
            logger.d { "Předvolby ze serveru: low=${presets.low.describe()} medium=${presets.medium.describe()} high=${presets.high.describe()}" }
            AutoDownloadSettings(
                wifi = presets.high.toLevel(),
                mobile = presets.medium.toLevel(),
                roaming = presets.low.toLevel(),
            )
        }

    override suspend fun save(settings: AutoDownloadSettings): Result<Unit> {
        // Čte se těsně před zápisem: zbylá pole (datový tarif hovorů, přednačítání…) nastavují
        // oficiální klienti a Plume je nesmí přepsat svými výchozími.
        val presets = holder.client.getAutoDownloadSettingsPresets().toResult().getOrElse { return Result.failure(it) }
        logger.d { "Předvolby před zápisem: low=${presets.low.describe()} medium=${presets.medium.describe()} high=${presets.high.describe()}" }
        val networks = listOf<Triple<AutoDownloadLevel, TdAutoDownloadSettings, NetworkType>>(
            Triple(settings.wifi, presets.high, NetworkTypeWiFi()),
            Triple(settings.mobile, presets.medium, NetworkTypeMobile()),
            Triple(settings.roaming, presets.low, NetworkTypeMobileRoaming()),
        )
        for ((level, server, type) in networks) {
            if (server.toLevel() == level) continue
            holder.client.setAutoDownloadSettings(settings = level.toTd(server), type = type)
                .toResult()
                .onFailure { return Result.failure(it) }
        }
        return Result.success(Unit)
    }
}

private fun TdAutoDownloadSettings.toLevel(): AutoDownloadLevel = autoDownloadLevelOf(
    enabled = isAutoDownloadEnabled,
    maxPhotoBytes = maxPhotoFileSize.toLong(),
    maxVideoBytes = maxVideoFileSize,
)

private fun AutoDownloadLevel.toTd(server: TdAutoDownloadSettings): TdAutoDownloadSettings {
    val limits = serverLimits(this, server.maxPhotoFileSize.toLong())
    return TdAutoDownloadSettings(
        isAutoDownloadEnabled = this != AutoDownloadLevel.Off,
        // Vypnuté stahování nechá stropy, jak byly: kdo ho v oficiálním klientu zapne, má je zpátky.
        maxPhotoFileSize = limits?.photoBytes?.toInt() ?: server.maxPhotoFileSize,
        maxVideoFileSize = limits?.videoBytes ?: server.maxVideoFileSize,
        maxOtherFileSize = limits?.fileBytes ?: server.maxOtherFileSize,
        videoUploadBitrate = server.videoUploadBitrate,
        preloadLargeVideos = server.preloadLargeVideos,
        preloadNextAudio = server.preloadNextAudio,
        preloadStories = server.preloadStories,
        useLessDataForCalls = server.useLessDataForCalls,
    )
}

private fun TdAutoDownloadSettings.describe() =
    "on=$isAutoDownloadEnabled photo=$maxPhotoFileSize video=$maxVideoFileSize file=$maxOtherFileSize"

/**
 * Úroveň Plume, která předvolbě serveru leží nejblíž.
 *
 * Plume rozlišuje úrovně stropem videa (GIFy a kolečka), takže rozhoduje on. Hranice leží
 * v geometrickém středu mezi sousedními stropy (1 MB · 5 MB · 20 MB): předvolby oficiálních
 * klientů na ně nesedí a velikosti se porovnávají v poměru, ne v rozdílu. Převod Plume → server
 * → Plume tím vrací tutéž úroveň.
 *
 * Horní hranice je OSTRÁ: výchozí střední předvolba Telegramu má video přesně 10 MB (naměřeno
 * na emulátoru: nízká 0, střední 10 MB, vysoká 15 MB) a to je mobilní síť, kde má i Plume
 * výchozí střední úroveň. S `>=` by na mobilních datech stahovalo až 20 MB.
 */
internal fun autoDownloadLevelOf(enabled: Boolean, maxPhotoBytes: Long, maxVideoBytes: Long): AutoDownloadLevel = when {
    !enabled -> AutoDownloadLevel.Off
    maxPhotoBytes <= 0 && maxVideoBytes <= 0 -> AutoDownloadLevel.Off
    maxVideoBytes > HIGH_THRESHOLD -> AutoDownloadLevel.High
    maxVideoBytes >= MEDIUM_THRESHOLD -> AutoDownloadLevel.Medium
    else -> AutoDownloadLevel.Low
}

/** Stropy, které úroveň zapíše na server; `null` u [AutoDownloadLevel.Off] (stropy zůstanou). */
internal data class ServerLimits(val photoBytes: Long, val videoBytes: Long, val fileBytes: Long)

internal fun serverLimits(level: AutoDownloadLevel, serverPhotoBytes: Long): ServerLimits? {
    if (level == AutoDownloadLevel.Off) return null
    val limits = level.limits
    // Plume stahuje fotky celé bez stropu; nulu ze serveru (fotky vypnuté) nahradí strop TDLib.
    val photo = serverPhotoBytes.takeIf { it > 0 } ?: MAX_PHOTO_BYTES
    return ServerLimits(photoBytes = photo, videoBytes = limits.maxVideoBytes, fileBytes = limits.maxFileBytes)
}

/** Nejvyšší strop fotky, který TDLib přijme (`MAX_PHOTO_SIZE` v `AutoDownloadSettings.cpp`). */
internal const val MAX_PHOTO_BYTES: Long = 10L * 1024 * 1024

private val MEDIUM_THRESHOLD: Long = sqrt(LOW_VIDEO_BYTES.toDouble() * MEDIUM_BYTES).toLong()
private val HIGH_THRESHOLD: Long = sqrt(MEDIUM_BYTES.toDouble() * HIGH_BYTES).toLong()
