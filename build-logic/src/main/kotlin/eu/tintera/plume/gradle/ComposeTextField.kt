package eu.tintera.plume.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
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

// Textové pole přežije přesun do jiné scény. Viz „Textové pole při změně scény"
// v docs/poznatky/desktop.md (mechanismus, desktop) a „Textové pole při rozložení Foldu"
// v docs/poznatky/navigace.md (Android).
//
// `TextFieldCoreModifierNode.startCursorJob()` dostane na začátek `if (!isAttached) return`.
// Bez toho přepnutí scény s fokusem v textovém poli (zúžení okna na desktopu, složení
// a rozložení Foldu) shodí aplikaci na „Cannot read CompositionLocal because the Modifier node
// is not currently attached": NavDisplay přesouvá obsah do jiné scény
// (`CopyNodesToNewAnchorLocation`), `LayoutNode.attach` → `applyModifier` zavolá `updateNode`
// nad uzlem, který ještě připojený není, a ten rovnou spouští blikání kurzoru (`currentValueOf`,
// `coroutineScope`).
//
// Připojený uzel si kurzor spustí sám v `onAttach` (`if (isFocused && showCursor)`), takže
// přeskočené volání nic neztratí. Upstream to nemá opravené v Compose 1.13.0 ani
// 1.14.0-alpha01 (listopad 2026).
//
// Vkládá se dvěma cestami nad týmž visitorem, jako hook TDLib (Tdlib.kt): na Androidu
// instrumentací AGP ([ComposeTextFieldAndroidPlugin]), na desktopu přepisem jaru
// `foundation-desktop` ([ComposeTextFieldDesktopPlugin]). iOS ji nemá — klib Kotlin/Native
// přepsat nejde.

/** Třída s blikáním kurzoru, ve tvaru pro ASM. */
private const val PATCHED_INTERNAL_NAME =
    "androidx/compose/foundation/text/input/internal/TextFieldCoreModifierNode"

/**
 * Vloží na začátek `startCursorJob()V` stráž `if (!isAttached) return`.
 *
 * Rámec za stráží je tentýž jako počáteční (jen `this`, prázdný zásobník), takže ostatní rámce
 * metody zůstávají a zásobník stráže (hloubka 1) se vejde do původního maxima. Zapisuje se ale
 * ve tvaru, v jakém rámce čte reader: s `ClassReader.EXPAND_FRAMES` (AGP v režimu
 * `COPY_FRAMES`) jako `F_NEW`, jinak `F_SAME` — míchat rozbalené a komprimované rámce ASM
 * neumí. Writer s `COMPUTE_FRAMES` (ostatní režimy AGP) vložený rámec ignoruje.
 */
internal class StartCursorJobGuardVisitor(
    next: ClassVisitor,
    private val expandedFrames: Boolean,
) : ClassVisitor(Opcodes.ASM9, next) {

    private var found = false

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
                if (expandedFrames) {
                    visitFrame(Opcodes.F_NEW, 1, arrayOf<Any>(PATCHED_INTERNAL_NAME), 0, arrayOf<Any>())
                } else {
                    visitFrame(Opcodes.F_SAME, 0, null, 0, null)
                }
            }
        }
    }

    override fun visitEnd() {
        // Třídu jsme našli, metodu ne: Compose textové pole změnil (nebo chybu opravil). Bez
        // pádu buildu by se oprava tiše rozpojila.
        if (!found) {
            throw GradleException(
                "$PATCHED_INTERNAL_NAME nemá startCursorJob()V — zkontroluj, jestli Compose nezměnil " +
                    "textové pole nebo chybu neopravil (ComposeTextField.kt).",
            )
        }
        super.visitEnd()
    }
}

/**
 * Android: instrumentace bajtkódu AGP (`InstrumentationScope.ALL`, tedy i závislosti), jen třída
 * [PATCHED_INTERNAL_NAME] z `foundation-android`. Režim rámců nenastavuje: výchozí `COPY_FRAMES`
 * (tentýž, který chce `plume.tdlib-android`) čte s `EXPAND_FRAMES` a zapisuje bez přepočtu.
 *
 * Přejmenovanou metodu ohlásí build ([StartCursorJobGuardVisitor.visitEnd]), přejmenovanou nebo
 * přesunutou třídu ne — instrumentace se na ni prostě nezavolá. Ověří se `dexdump -d` nad APK:
 * `startCursorJob` začíná `invoke-virtual … Modifier$Node;.isAttached` a `if-nez`.
 */
class ComposeTextFieldAndroidPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                variant.instrumentation.transformClassesWith(
                    ComposeTextFieldGuardFactory::class.java,
                    InstrumentationScope.ALL,
                ) {}
            }
        }
    }
}

abstract class ComposeTextFieldGuardFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className == PATCHED_INTERNAL_NAME.replace('/', '.')

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        StartCursorJobGuardVisitor(nextClassVisitor, expandedFrames = true)
}

/**
 * Desktop: přepis jaru `foundation-desktop` artifact transformem na `runtimeClasspath`. Build
 * spadne, když třídu nebo metodu nenajde — přejmenování v bumpu by opravu jinak tiše rozpojilo.
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

private const val PATCHED_CLASS = "$PATCHED_INTERNAL_NAME.class"

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
                        bytes = guardStartCursorJob(bytes)
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

    /** Vloží stráž na začátek `startCursorJob()`; bez metody build spadne. */
    private fun guardStartCursorJob(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        // Writer z readeru a bez COMPUTE_*: COMPUTE_FRAMES by pro ostatní metody potřeboval
        // načítat cizí třídy. Reader bez EXPAND_FRAMES, takže stráž vkládá komprimovaný rámec.
        val writer = ClassWriter(reader, 0)
        reader.accept(StartCursorJobGuardVisitor(writer, expandedFrames = false), 0)
        return writer.toByteArray()
    }
}
