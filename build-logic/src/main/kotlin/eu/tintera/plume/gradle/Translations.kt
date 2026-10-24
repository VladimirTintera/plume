package eu.tintera.plume.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Jazyky aplikace nad `src/commonMain/composeResources/values-*` modulu, který texty nese:
 * seznam jazyků ([GenerateAppLanguages]), hlídání mutací ([VerifyTranslations]) a složek iOS
 * ([VerifyIosLocalizations]). Kotlin z [GenerateAppLanguages] si modul přidá do `commonMain` sám
 * (`kotlinDirectory`), plugin na KMP nesahá.
 */
class TranslationsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(LifecycleBasePlugin::class.java)
        val extension = project.extensions.create("plumeTranslations", TranslationsExtension::class.java)
        val resources = project.layout.projectDirectory.dir("src/commonMain/composeResources")
        val strings = project.fileTree(resources) { include("values*/strings.xml") }

        val generate = project.tasks.register("generateAppLanguages", GenerateAppLanguages::class.java) {
            this.strings.from(strings)
            resourcesDirectory.set(resources)
            completeLocales.set(extension.completeLocales)
            minLanguageCoverage.set(extension.minLanguageCoverage)
            val output = project.layout.buildDirectory.dir("generated/appLanguages")
            kotlinDirectory.set(output.map { it.dir("kotlin") })
            languagesFile.set(output.map { it.file("languages.txt") })
        }
        val verify = project.tasks.register("verifyTranslations", VerifyTranslations::class.java) {
            this.strings.from(strings)
            resourcesDirectory.set(resources)
            completeLocales.set(extension.completeLocales)
        }
        val verifyIos = project.tasks.register("verifyIosLocalizations", VerifyIosLocalizations::class.java) {
            languages.set(generate.flatMap { it.languagesFile })
            targets.from(listOf("iosApp/iosApp", "iosApp/ShareExtension").map { project.rootProject.file(it) })
        }
        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(verify, verifyIos) }
    }
}

abstract class TranslationsExtension {
    /** Jazyky, které píšeme sami: musí mít všechny klíče ([VerifyTranslations]) a do výběru jdou vždy. */
    abstract val completeLocales: SetProperty<String>

    /** Podíl přeložených klíčů, od kterého jde komunitní jazyk do výběru ([GenerateAppLanguages]). */
    abstract val minLanguageCoverage: Property<Double>
}

/**
 * Jazyky aplikace odvozené ze složek `composeResources/values-*`, aby nový jazyk z Weblate
 * nemusel nikdo zapisovat ručně na čtyři místa (viz „Jazyk aplikace" v docs/poznatky/nastaveni.md).
 *
 * - Výchozí `values` je angličtina, ta je v seznamu vždy první.
 * - [completeLocales] jdou do výběru vždy; komunitní jazyk až od [minLanguageCoverage]
 *   přeložených klíčů. Rozpracovaný jazyk by v aplikaci střídal věty s angličtinou a nabízet ho
 *   by bylo horší než ho nenabízet.
 * - Výstupem je Kotlin (`GENERATED_APP_LANGUAGES` pro `APP_LANGUAGES`) a `languages.txt`, ze
 *   kterého si `:client:androidApp` vyrobí `locales_config.xml`. Jedno místo pravidel pro oba.
 * - Složka `values-pt-rBR` (kvalifikátor regionu) je jazyk `pt-BR`.
 * - Výstupy jmenovitě, NE celá složka `generated/appLanguages`: AGP hledá vedle každého
 *   zdrojového adresáře složku baselineProfiles (`generated/appLanguages/baselineProfiles` vedle
 *   `…/kotlin`) a kdyby ležela uvnitř výstupu téhle úlohy, release build padne na nedeklarovanou
 *   závislost `prepareAndroidMainArtProfile` → `generateAppLanguages`.
 */
abstract class GenerateAppLanguages : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val strings: ConfigurableFileCollection

    @get:Internal
    abstract val resourcesDirectory: DirectoryProperty

    @get:Input
    abstract val completeLocales: SetProperty<String>

    @get:Input
    abstract val minLanguageCoverage: Property<Double>

    @get:OutputDirectory
    abstract val kotlinDirectory: DirectoryProperty

    @get:OutputFile
    abstract val languagesFile: RegularFileProperty

    init {
        description = "Seznam jazyků aplikace ze složek values-* (APP_LANGUAGES, locales_config)."
    }

    @TaskAction
    fun generate() {
        val resourcesDir = resourcesDirectory.get().asFile
        val complete = completeLocales.get()
        val minCoverage = minLanguageCoverage.get()
        val defaults = keysOf(File(resourcesDir, "values/strings.xml"))
        val translated = resourcesDir.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .mapNotNull { dir ->
                val file = File(dir, "strings.xml").takeIf { it.isFile } ?: return@mapNotNull null
                val code = dir.name.removePrefix("values-").replace("-r", "-")
                val coverage = (keysOf(file) intersect defaults).size.toDouble() / defaults.size
                if (code !in complete && coverage < minCoverage) {
                    logger.lifecycle("generateAppLanguages: $code má ${(coverage * 100).toInt()} % — do výběru jazyků zatím nejde.")
                    return@mapNotNull null
                }
                code
            }
            .sorted()
        val languages = listOf("en") + translated

        languagesFile.get().asFile.apply { parentFile.mkdirs() }.writeText(languages.joinToString("\n") + "\n")
        File(kotlinDirectory.get().asFile, "eu/tintera/plume/core/locale/GeneratedAppLanguages.kt").apply { parentFile.mkdirs() }.writeText(
            """
            |// Vygenerováno úlohou generateAppLanguages ze složek composeResources/values-*. Neupravovat.
            |package eu.tintera.plume.core.locale
            |
            |internal val GENERATED_APP_LANGUAGES: List<String> = listOf(${languages.joinToString { "\"$it\"" }})
            |""".trimMargin(),
        )
    }

    private fun keysOf(file: File): Set<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()
}

/**
 * Hlídá jazykové mutace `composeResources/values-*`.
 *
 * - **Jazyky, které píšeme sami ([completeLocales]), musí mít všechny klíče** — chybějící klíč by
 *   se poznal až na obrazovce: Compose spadne na výchozí jazyk a uprostřed české aplikace
 *   vyskočí anglická věta.
 * - **Komunitní jazyky (Weblate) chybějící klíč jen ohlásí.** Rozpracovaný jazyk padá na
 *   angličtinu po jednotlivých větách, a build kvůli tomu padat nesmí — jinak by každý nový
 *   klíč v angličtině rozbil překlad, dokud ho někdo nedoplní.
 * - **Formátovací argumenty jsou chyba u všech jazyků.** Překlad s `%2$s`, který angličtina nemá,
 *   nebo s `%1$d` místo `%1$s`, by se za běhu poskládal špatně. Řetězec musí mít tytéž argumenty
 *   jako výchozí; plurál smí v některém tvaru argument vynechat („Před minutou"), ale nesmí
 *   přidat cizí ani změnit typ.
 */
abstract class VerifyTranslations : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val strings: ConfigurableFileCollection

    @get:Internal
    abstract val resourcesDirectory: DirectoryProperty

    @get:Input
    abstract val completeLocales: SetProperty<String>

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Ověří jazykové mutace: klíče (cs úplně, komunitní jazyky varováním) a formátovací argumenty."
    }

    @TaskAction
    fun verify() {
        val resourcesDir = resourcesDirectory.get().asFile
        val complete = completeLocales.get()
        val stringPattern = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val pluralsPattern = Regex("""<plurals name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        val itemPattern = Regex("""<item quantity="[^"]+"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
        val argPattern = Regex("""%(\d+)\$([a-z])""")

        // Klíč → (argumenty, je to plurál); u plurálu sjednocení argumentů přes všechny tvary.
        fun argsOf(text: String): Set<String> = argPattern.findAll(text).map { it.groupValues[1] + "$" + it.groupValues[2] }.toSet()

        fun entriesOf(file: File): Map<String, Pair<Set<String>, Boolean>> {
            val text = file.readText()
            return stringPattern.findAll(text).associate { it.groupValues[1] to (argsOf(it.groupValues[2]) to false) } +
                pluralsPattern.findAll(text).associate { match ->
                    match.groupValues[1] to (itemPattern.findAll(match.groupValues[2]).flatMap { argsOf(it.groupValues[1]) }.toSet() to true)
                }
        }

        val defaults = entriesOf(File(resourcesDir, "values/strings.xml"))
        val locales = resourcesDir.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .mapNotNull { dir -> File(dir, "strings.xml").takeIf { it.isFile }?.let { dir.name.removePrefix("values-") to it } }
            .sortedBy { it.first }

        val errors = mutableListOf<String>()
        locales.forEach { (locale, file) ->
            val translated = entriesOf(file)
            val missing = (defaults.keys - translated.keys).sorted()
            val extra = (translated.keys - defaults.keys).sorted()
            if (locale in complete) {
                if (missing.isNotEmpty()) errors += "Chybí ve values-$locale: " + missing.joinToString()
                if (extra.isNotEmpty()) errors += "Chybí ve values (výchozí), ale jsou ve values-$locale: " + extra.joinToString()
            } else {
                if (missing.isNotEmpty()) logger.warn("values-$locale: nepřeloženo ${missing.size} z ${defaults.size} (spadne na angličtinu).")
                if (extra.isNotEmpty()) logger.warn("values-$locale: klíče, které výchozí jazyk už nemá: " + extra.joinToString())
            }
            translated.forEach { (key, entry) ->
                val (args, plural) = entry
                val baseArgs = defaults[key]?.first ?: return@forEach
                val foreign = args - baseArgs
                val lost = if (plural) emptySet() else baseArgs - args
                if (foreign.isNotEmpty() || lost.isNotEmpty()) {
                    errors += "values-$locale/$key: argumenty ${args.sorted()} místo ${baseArgs.sorted()}"
                }
            }
        }

        check(errors.isEmpty()) {
            buildString {
                appendLine("Jazykové mutace se rozešly.")
                errors.forEach { appendLine(it) }
            }
        }
    }
}

/**
 * iOS nabídne jazyk v Nastavení jen podle složek `<kód>.lproj` v balíčku — a ty Gradle vyrobit
 * neumí, nesou vlastní texty iOS (`Localizable.strings`, překládají se zvlášť). Jazyk, který
 * [GenerateAppLanguages] pustil do výběru a iOS složku nemá, se proto jen OHLÁSÍ: na Androidu
 * jazyk funguje hned a build kvůli iOS padat nemá.
 */
abstract class VerifyIosLocalizations : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val languages: RegularFileProperty

    /** Složky, které musí mít `<kód>.lproj`: aplikace a rozšíření pro sdílení. */
    @get:Internal
    abstract val targets: ConfigurableFileCollection

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Ohlásí jazyk aplikace bez <kód>.lproj v iosApp a ShareExtension."
    }

    @TaskAction
    fun verify() {
        languages.get().asFile.readLines().filter { it.isNotBlank() }.forEach { code ->
            targets.files.filter { !File(it, "$code.lproj").isDirectory }.forEach { target ->
                logger.warn("verifyIosLocalizations: $code chybí ${target.name}/$code.lproj — iOS ho v Nastavení nenabídne.")
            }
        }
    }
}
