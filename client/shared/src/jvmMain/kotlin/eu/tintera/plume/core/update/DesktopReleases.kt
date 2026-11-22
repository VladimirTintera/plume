package eu.tintera.plume.core.update

import eu.tintera.plume.core.platform.DesktopOs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

/**
 * Verze desktopu `X.Y.Z` — tatáž, jakou nese tag `desktop-X.Y.Z` a balíček (`jpackage.app-version`,
 * `packageVersion` v `client/desktopApp/build.gradle.kts`).
 */
internal data class DesktopVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<DesktopVersion> {

    override fun compareTo(other: DesktopVersion): Int =
        compareValuesBy(this, other, DesktopVersion::major, DesktopVersion::minor, DesktopVersion::patch)

    /**
     * Číslo pro [AppUpdateState] (a zavřenou nabídku v `dismissedUpdateVersionCode`). Bity podle
     * mezí verze, které vynucuje release (X a Y do 255, Z do 65535), takže každá verze má jiné.
     * Pořadí se z něj nebere — porovnává se [DesktopVersion].
     */
    val code: Int get() = (major shl 24) or (minor shl 16) or patch

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,5})""")

        fun parse(text: String?): DesktopVersion? {
            val match = text?.trim()?.let(PATTERN::matchEntire) ?: return null
            val (major, minor, patch) = match.destructured
            return DesktopVersion(major.toInt(), minor.toInt(), patch.toInt())
                .takeIf { it.major <= 255 && it.minor <= 255 && it.patch <= 65535 }
        }

        /** `desktop-1.2.3` → 1.2.3; tagy Androidu a cokoli jiného `null`. */
        fun fromTag(tag: String?): DesktopVersion? = tag?.removePrefix(TAG_PREFIX)?.takeIf { tag.startsWith(TAG_PREFIX) }?.let(::parse)

        private const val TAG_PREFIX = "desktop-"
    }
}

/** Instalační balíček z přílohy vydání. */
internal data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
    /**
     * SHA-256 v šestnáctkové soustavě (`digest` u přílohy). Povinný: příloha bez něj se
     * nenabízí, protože by se stažený soubor neměl proti čemu ověřit (viz [latestDesktopRelease]).
     */
    val sha256: String,
)

internal data class DesktopRelease(val version: DesktopVersion, val asset: ReleaseAsset) {

    /**
     * Jméno staženého balíčku v `updates/` — z verze a přípony, ne ze jména přílohy z API: to
     * jde do cesty k souboru a pak do skriptu instalátoru, a nic z odpovědi serveru tam nepatří.
     */
    val fileName: String get() = "Plume-$version" + if (asset.name.endsWith(".msi", ignoreCase = true)) ".msi" else ".dmg"
}

/**
 * Balíček pro tenhle systém, podle jmen, která dává `desktop-release.yml`: MSI pro Windows
 * (`Plume-X.Y.Z.msi`), DMG jen pro Apple Silicon (`Plume-X.Y.Z-macos-arm64.dmg`). Linux se
 * nevydává a Mac s Intelem balíček nemá — tam nic, a aktualizace se tedy nenabídne.
 */
internal fun assetMatches(name: String, os: DesktopOs, arm64: Boolean): Boolean = when (os) {
    DesktopOs.Windows -> name.endsWith(".msi", ignoreCase = true)
    DesktopOs.MacOs -> arm64 && name.endsWith("-macos-arm64.dmg", ignoreCase = true)
    else -> false
}

/**
 * Nejnovější vydání desktopu s balíčkem pro tenhle systém z odpovědi `GET /repos/…/releases`,
 * nebo `null`. V repu jsou i vydání Androidu (`android-…`), proto se nebere `releases/latest`
 * — to by vrátilo, co vyšlo naposledy, klidně Android. Rozpracovaná (draft) a předběžná
 * (pre-release, `0.x`) vydání se nenabízejí, stejně jako balíček bez platného `digest`
 * (bez kontrolního součtu by se instaloval cokoli, co přijde).
 */
internal fun latestDesktopRelease(json: String, os: DesktopOs, arm64: Boolean): DesktopRelease? {
    val releases = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull() ?: return null
    return releases.mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        if (release.flag("draft") || release.flag("prerelease")) return@mapNotNull null
        val version = DesktopVersion.fromTag(release.text("tag_name")) ?: return@mapNotNull null
        val asset = (release["assets"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { assetMatches(it.text("name").orEmpty(), os, arm64) }
            ?.toAsset() ?: return@mapNotNull null
        DesktopRelease(version, asset)
    }.maxByOrNull { it.version }
}

private fun JsonObject.toAsset(): ReleaseAsset? {
    val name = text("name") ?: return null
    val url = text("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return null
    val sha256 = text("digest")?.takeIf { it.startsWith(SHA256_PREFIX) }?.removePrefix(SHA256_PREFIX)?.lowercase()
        ?.takeIf(SHA256_HEX::matches) ?: return null
    return ReleaseAsset(name = name, url = url, size = (this["size"] as? JsonPrimitive)?.longOrNull ?: 0L, sha256 = sha256)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

private const val SHA256_PREFIX = "sha256:"

private val SHA256_HEX = Regex("[0-9a-f]{64}")
