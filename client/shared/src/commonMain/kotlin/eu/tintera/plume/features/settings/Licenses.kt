package eu.tintera.plume.features.settings

import androidx.compose.runtime.Immutable
import com.mikepenz.aboutlibraries.Libs
import eu.tintera.plume.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Knihovny a jejich licence pro obrazovku Licence knihoven. Data jsou `files/licenses/<platforma>.json`
 * v `:client:resources`, generovaná konvenčním pluginem `plume.licenses` (AboutLibraries) —
 * proč po platformách a proč ručně, říká KDoc pluginu v `build-logic/…/Licenses.kt`.
 *
 * Vlastní typy místo `Library` a `License` z AboutLibraries: ty jsou datové třídy se `Set`
 * a Compose by je vedl jako nestabilní. A hranice stejně jako u TDLib — obrazovka nevidí, odkud
 * seznam je.
 */
@Immutable
internal data class LibraryEntry(
    val id: String,
    val name: String,
    val version: String?,
    val description: String?,
    val website: String?,
    val licenses: List<LicenseEntry>,
)

@Immutable
internal data class LicenseEntry(
    val name: String,
    /** Text licence; `null` u proprietárních podmínek (ML Kit, Play Core), ty mají jen [url]. */
    val text: String?,
    val url: String?,
)

/** Jméno souboru se seznamem: `android`, `desktop`, `ios` (jak je zapisuje `plume.licenses`). */
internal expect val licensesFile: String

/**
 * Cíl Kotlinu, do kterého knihovna musí patřit (`targets` v souboru). Sebrané knihovny Androidu
 * a desktopu ho nemají (jeden cíl, prázdné pole), ruční položky ano — LiteRT-LM pro Android tak
 * na desktopu není. Na iOS jen knihovny společného kódu (`metadata`).
 */
internal expect val licensesTarget: String

/** Načte a rozebere seznam mimo hlavní vlákno (stovky kB JSONu); řazeno podle jména. */
internal suspend fun loadLicenses(): List<LibraryEntry> = withContext(Dispatchers.Default) {
    val json = Res.readBytes("files/licenses/$licensesFile.json").decodeToString()
    Libs.Builder().withJson(json).build().libraries
        .filter { it.targets.isEmpty() || licensesTarget in it.targets }
        .map { library ->
            LibraryEntry(
                id = library.uniqueId,
                name = library.name.ifBlank { library.uniqueId },
                version = library.artifactVersion?.takeIf { it.isNotBlank() },
                description = library.description?.takeIf { it.isNotBlank() },
                website = library.website?.takeIf { it.isNotBlank() },
                licenses = library.licenses.map { license ->
                    LicenseEntry(
                        name = license.name,
                        text = license.licenseContent?.takeIf { it.isNotBlank() },
                        url = license.url?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
        .sortedBy { it.name.lowercase() }
}
