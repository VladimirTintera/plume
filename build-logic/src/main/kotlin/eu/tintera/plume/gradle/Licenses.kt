package eu.tintera.plume.gradle

import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Seznam licencí knihoven pro „O aplikaci" (AboutLibraries). Jeden soubor na platformu, protože
 * každá přibaluje jiné knihovny — a sbírat je musí modul, který je do aplikace opravdu balí:
 *
 * - `:client:androidApp` → `android` (Firebase Messaging, Wear a Feature Delivery má jen vstupní bod),
 * - `:client:desktopApp` → `desktop` (LiteRT-LM pro JVM, Swing, Compose pro desktop),
 * - `:client:shared` → `ios` (iOS nemá vstupní bod v Gradlu; aplikace z toho bere jen knihovny
 *   ze společného kódu, viz `includeTargets`).
 *
 * Soubory leží v `:client:resources` (`files/licenses/<platforma>.json`), protože `Res` je jen
 * tam. **Generují se ručně a commitují** (`./gradlew exportLicenses`), po změně závislostí.
 *
 * - **Offline, s texty licencí v repu** (`docs/licenses/aboutlibraries/licenses`). Online plugin
 *   stahuje texty z SPDX a stahování selhávalo náhodně — pokaždé chyběl jiný text (LGPL, BSD,
 *   GPL), i při běhu po jednom. Nová licence se ohlásí varováním „does not contain the license
 *   text … OFFLINE MODE"; její text se přidá jako další soubor (`hash` = SPDX id).
 * - Co v grafu Gradlu není (TDLib uvnitř wrapperu, fonty, ikony, llama.cpp a LiteRT-LM ze součástí
 *   na požádání, ai-engine z included buildu), doplňuje `docs/licenses/aboutlibraries/libraries`.
 *   Ruční položky jdou do všech tří souborů; na kterou platformu patří, říká jejich `targets`
 *   a aplikace podle něj filtruje.
 *
 * Viz `docs/poznatky/nastaveni.md`, „Licence knihoven".
 */
class LicensesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.mikepenz.aboutlibraries.plugin")
        val settings = project.extensions.create("plumeLicenses", PlumeLicensesExtension::class.java)
        val root = project.rootProject.layout.projectDirectory
        project.extensions.configure(AboutLibrariesExtension::class.java) {
            // Texty licencí jsou v repu, síť by výsledek jen dělala náhodným (viz KDoc).
            offlineMode.set(true)
            collect {
                configPath.set(root.dir("docs/licenses/aboutlibraries"))
                // Jen pro iOS: z `shared` se tam berou knihovny společného kódu (`metadata`).
                includeTargets.set(true)
            }
            export {
                outputFile.set(settings.platform.map { root.file("client/resources/src/commonMain/composeResources/files/licenses/$it.json") })
                // Release, ne debug: LeakCanary a nástroje ladění do aplikace nejdou.
                variant.set(settings.variant)
                // Co obrazovka neukazuje, jen by nafukovalo soubor v každé sestavě.
                excludeFields.addAll("developers", "funding", "scm", "organization")
                // Čitelný diff v PR, když se po bumpu závislostí soubor přegeneruje.
                prettyPrint.set(true)
            }
        }
    }
}

abstract class PlumeLicensesExtension {
    /** Jméno souboru: `android`, `desktop`, nebo `ios` (čte ho `licensesFile` ve `shared`). */
    abstract val platform: Property<String>

    /** Varianta, ze které se sbírá; u Androidu `release`, jinde jediná. */
    abstract val variant: Property<String>
}
