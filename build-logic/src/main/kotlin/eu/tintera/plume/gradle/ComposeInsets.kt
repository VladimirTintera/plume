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
 * Desktop: oprava insetů z Compose Multiplatform 1.13 přenesená do 1.12 přepisem jaru
 * `foundation-layout-desktop`. Viz „Insety při změně scény" v docs/poznatky/desktop.md.
 *
 * `insetsInvalidated()` u [PATCHED_CLASSES] dostane na začátek `if (!isAttached) return` — totéž,
 * co upstream přidal v 1.13.0-alpha01. Bez toho zúžení okna přes práh dvou panelů shodí okno na
 * „visitSubtreeIf called on an unattached node": na skiko je `WindowInsets.safeDrawing` a spol.
 * při každém čtení nový objekt bez `equals`, takže každá rekompozice `windowInsetsPadding`
 * volá `update`, a to i nad uzlem, který NavDisplay zrovna přesouvá do jiné scény.
 *
 * Připojený uzel si insety přepočítá sám v `onAttach`, takže přeskočené volání nic neztratí.
 * Až projekt přejde na 1.13, plugin může pryč; do té doby build spadne, když třídu nebo metodu
 * nenajde (přejmenování v bumpu by opravu jinak tiše rozpojilo).
 */
class ComposeInsetsDesktopPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val patched = Attribute.of("eu.tintera.plume.composeInsetsPatched", Boolean::class.javaObjectType)
        project.dependencies.apply {
            attributesSchema { attribute(patched) }
            artifactTypes.getByName("jar") { attributes.attribute(patched, false) }
            registerTransform(ComposeInsetsJarTransform::class.java) {
                from.attribute(patched, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                to.attribute(patched, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            }
        }
        project.pluginManager.withPlugin("java") {
            project.configurations.named("runtimeClasspath") { attributes.attribute(patched, true) }
        }
    }
}

/** Třídy, jejichž `insetsInvalidated()` se nesmí rozběhnout nad odpojeným uzlem. */
private val PATCHED_CLASSES = setOf(
    // Základ: přepočet a průchod potomky (`traverseDescendants` tu padá).
    "androidx/compose/foundation/layout/InsetsConsumingModifierNode.class",
    // `windowInsetsPadding`: po základu ještě `invalidateMeasurement`.
    "androidx/compose/foundation/layout/InsetsPaddingModifierNode.class",
)

abstract class ComposeInsetsJarTransform : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        if (!input.name.startsWith("foundation-layout-desktop")) {
            outputs.file(input)
            return
        }
        val output = outputs.file(input.nameWithoutExtension + "-plume.jar")
        val done = mutableSetOf<String>()
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    var bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name in PATCHED_CLASSES) {
                        bytes = guardInsetsInvalidated(bytes) ?: throw GradleException(
                            "${entry.name}: v ${input.name} chybí insetsInvalidated()V — zkontroluj, jestli " +
                                "oprava z Compose 1.13 není už v knihovně (ComposeInsets.kt).",
                        )
                        done += entry.name
                    }
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
        val missing = PATCHED_CLASSES - done
        if (missing.isNotEmpty()) {
            throw GradleException(
                "${input.name} nemá $missing — zkontroluj, jestli oprava z Compose 1.13 není už " +
                    "v knihovně (ComposeInsets.kt).",
            )
        }
    }

    /** Vloží stráž na začátek `insetsInvalidated()`; `null`, když metoda ve třídě není. */
    private fun guardInsetsInvalidated(bytes: ByteArray): ByteArray? {
        val reader = ClassReader(bytes)
        // Writer z readeru a bez COMPUTE_*: COMPUTE_FRAMES by pro ostatní metody potřeboval
        // načítat cizí třídy. Metoda nemá větvení, takže jediný rámec je ten vložený (F_SAME
        // vůči vstupu) a zásobník stráže (hloubka 1) se vejde do původního maxima.
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
                    if (name != "insetsInvalidated" || descriptor != "()V") return next
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
