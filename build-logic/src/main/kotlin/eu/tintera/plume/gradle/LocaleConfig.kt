package eu.tintera.plume.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * `res/xml/locales_config.xml` pro každý vstupní bod na Androidu (telefon, hodinky): bez něj
 * systém jazyk aplikace (`LocaleManager`, `cmd locale set-app-locales`) nepoužije a aplikace jde
 * podle jazyka systému. Manifest na soubor odkazuje sám (`android:localeConfig`).
 *
 * Seznam jazyků vyrobí `:client:resources:generateAppLanguages` ze složek
 * `composeResources/values-*` — ručně psaný soubor se s nimi rozcházel: nový jazyk z Weblate by
 * v aplikaci byl, ale vybrat by nešel. Plugin je jeden pro oba moduly, aby se pravidla nerozešla
 * podruhé.
 */
class LocaleConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val generate = project.tasks.register("generateLocaleConfig", GenerateLocaleConfig::class.java) {
                dependsOn(":client:resources:generateAppLanguages")
                languages.set(project.rootProject.layout.projectDirectory.file("client/resources/build/generated/appLanguages/languages.txt"))
                outputDirectory.set(project.layout.buildDirectory.dir("generated/localeConfig/res"))
            }
            project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                variant.sources.res?.addGeneratedSourceDirectory(generate, GenerateLocaleConfig::outputDirectory)
            }
        }
    }
}

abstract class GenerateLocaleConfig : DefaultTask() {
    @get:InputFile
    abstract val languages: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        description = "locales_config.xml (systémový výběr jazyka aplikace) ze seznamu jazyků v :client:resources."
    }

    @TaskAction
    fun generate() {
        val locales = languages.get().asFile.readLines().filter { it.isNotBlank() }
        val file = outputDirectory.get().file("xml/locales_config.xml").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("<!-- Vygenerováno úlohou generateLocaleConfig ze složek composeResources/values-*. Neupravovat. -->")
                appendLine("""<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""")
                locales.forEach { appendLine("""    <locale android:name="$it" />""") }
                appendLine("</locale-config>")
            },
        )
    }
}
