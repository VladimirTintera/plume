package eu.tintera.plume.gradle

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Desktop: jar `litertlm-jvm` (model v zařízení) jen s nativní knihovnou tohoto systému.
 *
 * Knihovna publikuje JNI pro Windows, macOS a Linux v jednom jaru (134 MB zabalených, 385 MB
 * vybalených); balí se ale jen pro systém, na kterém se staví (jako TDLib, [TdlibDesktopPlugin]).
 * Knihovnu si za běhu vybalí `NativeLibraryLoader` z `com/google/ai/edge/litertlm/jni/<systém>-<arch>/`;
 * co tu odpadne, aplikace pozná jako „model tu nejde" (`DesktopLiteRtPlatform.nativeLibraryBundled`).
 *
 * Vlastní atribut, ne další krok v přepisu TDLib: Gradle oba přepisy zřetězí a každý zůstává
 * u svého jaru.
 */
class LiteRtDesktopPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val stripped = Attribute.of("eu.tintera.plume.liteRtStripped", Boolean::class.javaObjectType)
        project.dependencies.apply {
            attributesSchema { attribute(stripped) }
            artifactTypes.getByName("jar") { attributes.attribute(stripped, false) }
            registerTransform(LiteRtJarTransform::class.java) {
                from.attribute(stripped, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                to.attribute(stripped, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                parameters { platform.set(liteRtPlatform()) }
            }
        }
        project.pluginManager.withPlugin("java") {
            project.configurations.named("runtimeClasspath") { attributes.attribute(stripped, true) }
        }
    }

    /** Složka JNI pro JVM, na kterém běží Gradle: `windows-x86_64`, `darwin-aarch64`, `linux-x86_64`… */
    private fun liteRtPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val system = when {
            "mac" in os -> "darwin"
            "windows" in os -> "windows"
            else -> "linux"
        }
        return "$system-" + if ("aarch64" in arch || "arm64" in arch) "aarch64" else "x86_64"
    }
}

abstract class LiteRtJarTransform : TransformAction<LiteRtJarTransform.Parameters> {

    interface Parameters : TransformParameters {
        /** Složka JNI ve jaru pro tenhle stroj. */
        @get:Input
        val platform: Property<String>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        if (!input.name.startsWith("litertlm-jvm")) {
            outputs.file(input)
            return
        }
        val keep = "$JNI_ROOT/${parameters.platform.get()}/"
        val output = outputs.file(input.nameWithoutExtension + "-plume.jar")
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name.startsWith("$JNI_ROOT/") && !name.startsWith(keep)) continue
                    out.putNextEntry(ZipEntry(name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }

    private companion object {
        const val JNI_ROOT = "com/google/ai/edge/litertlm/jni"
    }
}
