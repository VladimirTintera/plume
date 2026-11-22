package eu.tintera.plume.gradle

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Desktop: textové pole přežije přesun do jiné scény. Přepis jaru `foundation-desktop`, viz
 * „Textové pole při změně scény" v docs/poznatky/desktop.md.
 *
 * `TextFieldCoreModifierNode.startCursorJob()` dostane na začátek `if (!isAttached) return`.
 * Bez toho zúžení okna přes práh dvou panelů s otevřenou konverzací (pole zprávy má fokus)
 * shodí okno na „Cannot read CompositionLocal because the Modifier node is not currently
 * attached": NavDisplay přesouvá obsah do jiné scény (`CopyNodesToNewAnchorLocation`),
 * `LayoutNode.attach` → `applyModifier` zavolá `updateNode` nad uzlem, který ještě připojený
 * není, a ten rovnou spouští blikání kurzoru (`currentValueOf`, `coroutineScope`).
 *
 * Připojený uzel si kurzor spustí sám v `onAttach` (`if (isFocused && showCursor)`), takže
 * přeskočené volání nic neztratí. Upstream to v Compose 1.13.0-rc01 ani v androidx-main
 * (listopad 2026) opravené nemá. Build spadne, když třídu nebo metodu nenajde — přejmenování
 * v bumpu by opravu jinak tiše rozpojilo.
 */
class ComposeTextFieldDesktopPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val patched = Attribute.of("eu.tintera.plume.composeTextFieldPatched", Boolean::class.javaObjectType)
        project.dependencies.apply {
            attributesSchema { attribute(patched) }
            artifactTypes.getByName("jar") { attributes.attribute(patched, false) }
            registerTransform(ComposeTextFieldJarTransform::class.java) {
                from.attribute(patched, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                to.attribute(patched, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            }
        }
        project.pluginManager.withPlugin("java") {
            project.configurations.named("runtimeClasspath") { attributes.attribute(patched, true) }
        }
    }
}

private const val PATCHED_CLASS =
    "androidx/compose/foundation/text/input/internal/TextFieldCoreModifierNode.class"

abstract class ComposeTextFieldJarTransform : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        // `foundation-desktop`, ne `foundation-layout-desktop` ani jiný jar.
        if (!input.name.matches(Regex("foundation-desktop-\\d.*\\.jar"))) {
            outputs.file(input)
            return
        }
        val output = outputs.file(input.nameWithoutExtension + "-plume.jar")
        var done = false
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    var bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name == PATCHED_CLASS) {
                        bytes = guardStartCursorJob(bytes) ?: throw GradleException(
                            "$PATCHED_CLASS: v ${input.name} chybí startCursorJob()V — zkontroluj, jestli " +
                                "Compose nezměnil textové pole nebo chybu neopravil (ComposeTextField.kt).",
                        )
                        done = true
                    }
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
        if (!done) {
            throw GradleException(
                "${input.name} nemá $PATCHED_CLASS — zkontroluj, jestli Compose nezměnil textové pole " +
                    "nebo chybu neopravil (ComposeTextField.kt).",
            )
        }
    }

    /** Vloží stráž na začátek `startCursorJob()`; `null`, když metoda ve třídě není. */
    private fun guardStartCursorJob(bytes: ByteArray): ByteArray? {
        val reader = ClassReader(bytes)
        // Writer z readeru a bez COMPUTE_*: COMPUTE_FRAMES by pro ostatní metody potřeboval
        // načítat cizí třídy. Vložený rámec je F_SAME vůči počátečnímu (jen `this`, prázdný
        // zásobník) a další rámce metody se počítají vůči němu, tedy beze změny; zásobník
        // stráže (hloubka 1) se vejde do původního maxima.
        val writer = ClassWriter(reader, 0)
        var found = false
        reader.accept(
            object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val next = super.visitMethod(access, name, descriptor, signature, exceptions)
                    if (name != "startCursorJob" || descriptor != "()V") return next
                    found = true
                    return object : MethodVisitor(Opcodes.ASM9, next) {
                        override fun visitCode() {
                            super.visitCode()
                            val attached = Label()
                            visitVarInsn(Opcodes.ALOAD, 0)
                            visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL,
                                "androidx/compose/ui/Modifier\$Node",
                                "isAttached",
                                "()Z",
                                false,
                            )
                            visitJumpInsn(Opcodes.IFNE, attached)
                            visitInsn(Opcodes.RETURN)
                            visitLabel(attached)
                            visitFrame(Opcodes.F_SAME, 0, null, 0, null)
                        }
                    }
                }
            },
            0,
        )
        return if (found) writer.toByteArray() else null
    }
}
