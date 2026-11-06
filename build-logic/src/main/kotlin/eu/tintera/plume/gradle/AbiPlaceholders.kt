package eu.tintera.plume.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Prázdné „knihovny" v součásti z Google Play pro ABI, pro která runtime nic nemá
 * (`plume.feature-abi-placeholders`, seznam ABI v [AbiPlaceholdersExtension.abis]).
 *
 * bundletool chce, aby VŠECHNY moduly s nativními knihovnami podporovaly stejnou sadu ABI:
 * „All modules with native libraries must support the same set of ABIs" — základ má kvůli
 * TDLib čtyři, LiteRT-LM a llama.cpp jen arm64-v8a a x86_64. Vypustit 32bitové telefony z Play
 * kvůli volitelné funkci nedává smysl. Model se tam nenabízí (`AndroidLiteRtPlatform.canRun`
 * chce arm64), takže se součást na takový telefon nikdy nestáhne a soubor nikdo nenačte.
 * AGP při buildu hlásí, že ho nejde ořezat („Unable to strip") — to je v pořádku.
 */
class AbiPlaceholdersPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("abiPlaceholders", AbiPlaceholdersExtension::class.java)
        extension.abis.convention(listOf("armeabi-v7a", "x86"))
        val task = project.tasks.register("abiPlaceholders", AbiPlaceholdersTask::class.java) {
            abis.set(extension.abis)
            fileName.set("libplume_no_${project.name.lowercase()}.so")
            outputDir.set(project.layout.buildDirectory.dir("generated/abiPlaceholders"))
        }
        project.plugins.withId("com.android.dynamic-feature") {
            project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                variant.sources.jniLibs?.addGeneratedSourceDirectory(task, AbiPlaceholdersTask::outputDir)
            }
        }
    }
}

abstract class AbiPlaceholdersExtension {
    abstract val abis: ListProperty<String>
}

abstract class AbiPlaceholdersTask : DefaultTask() {
    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val fileName: org.gradle.api.provider.Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        // Výstup se neuklízí sám: soubor pod dřívějším jménem by v součásti zůstal vedle nového.
        outputDir.get().asFile.deleteRecursively()
        abis.get().forEach { abi ->
            outputDir.file("$abi/${fileName.get()}").get().asFile.apply {
                parentFile.mkdirs()
                writeBytes(ByteArray(0))
            }
        }
    }
}
