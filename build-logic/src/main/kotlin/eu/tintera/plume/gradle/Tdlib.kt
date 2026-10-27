package eu.tintera.plume.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
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
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

// Obcházka pádu `tdl-coroutines` na odpovědi TDLib, ve které chybí pole dokumentované jako povinné
// (15.0.0 a 16.0.0 dárek bez `background`, opravené v 17.0.0). Viz „Dárek" v docs/poznatky/typy-zprav.md.
//
// Za každé `JsonClient.receive` uvnitř wrapperu se vloží `TdlibReceiveHook.afterReceive`, takže
// odpověď projde `TdlibJsonRepair` dřív, než ji wrapper rozebere. Jinou cestu dovnitř wrapper
// nemá: přijímací smyčka i parser jsou interní a knihovna je obfuskovaná. `JsonClient` naopak
// jméno mění nesmí (JNI), takže se na něj dá spolehnout i po bumpu. Že vložení zabralo, hlídá
// `TdlTelegramSession` po přihlášení.
//
// Vkládá se dvěma cestami nad týmž visitorem: na Androidu instrumentací AGP ([TdlibAndroidPlugin]),
// na desktopu přepisem jaru wrapperu ([TdlibDesktopPlugin]). Každý vstupní bod, který přibalí
// TDLib (i budoucí Wear OS), musí jeden z pluginů použít — jinak ho dárek bez pozadí shodí.

/** Třídy wrapperu, ve kterých se volání hledá. */
private const val WRAPPER_PACKAGE = "dev/g000sha256/tdl/"

/** Za `JsonClient.receive` vloží `TdlibReceiveHook.afterReceive`. */
internal class TdlibReceiveHookVisitor(next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor = object : MethodVisitor(
        Opcodes.ASM9,
        super.visitMethod(access, name, descriptor, signature, exceptions),
    ) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String?,
            name: String?,
            descriptor: String?,
            isInterface: Boolean,
        ) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            if (owner == "org/drinkless/tdlib/JsonClient" && name == "receive" &&
                descriptor == "(D)Ljava/lang/String;"
            ) {
                // Na zásobníku je String? z receive a po volání zase String? — rámce
                // se nemění, stačí COPY_FRAMES (na JVM writer bez COMPUTE_*).
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "eu/tintera/plume/core/telegram/TdlibReceiveHook",
                    "afterReceive",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false,
                )
            }
        }
    }
}

/**
 * Android: instrumentace bajtkódu AGP (`InstrumentationScope.ALL`, tedy i závislosti). Ověří se
 * `dexdump -d` nad APK: `invoke-static … TdlibReceiveHook;.afterReceive` hned za `JsonClient;.receive`.
 */
class TdlibAndroidPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                variant.instrumentation.transformClassesWith(
                    TdlibReceiveHookFactory::class.java,
                    InstrumentationScope.ALL,
                ) {}
                variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
            }
        }
    }
}

abstract class TdlibReceiveHookFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className.startsWith(WRAPPER_PACKAGE.replace('/', '.'))

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        TdlibReceiveHookVisitor(nextClassVisitor)
}

/**
 * Desktop: přepis jaru `tdl-coroutines-jvm` artifact transformem na `runtimeClasspath`.
 *
 * Wrapper publikuje desktopovou TDLib v jednom jaru pro všechny systémy (115 MB). Jar se při
 * buildu přepíše:
 *
 * 1. **Jen nativní knihovna tohoto systému a architektury.** Balí se stejně jen pro systém,
 *    na kterém se staví; cizí knihovny by v balíčku zabraly ~300 MB.
 * 2. **Hook `TdlibJsonRepair`** ([TdlibReceiveHookVisitor]). JVM jar wrapperu obfuskovaný není.
 *
 * Do verze 15.0.0 se tu navíc přepisovaly cesty v macOS knihovně a přibalovalo OpenSSL
 * (g000sha256/tdl-coroutines#163); od 16.0.0 je knihovna bez závislostí mimo systém.
 */
class TdlibDesktopPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val patched = Attribute.of("eu.tintera.plume.tdlibPatched", Boolean::class.javaObjectType)
        project.dependencies.apply {
            attributesSchema { attribute(patched) }
            artifactTypes.getByName("jar") { attributes.attribute(patched, false) }
            registerTransform(TdlibJarTransform::class.java) {
                from.attribute(patched, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                to.attribute(patched, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                parameters { platform.set(tdlibPlatform()) }
            }
        }
        project.pluginManager.withPlugin("java") {
            project.configurations.named("runtimeClasspath") { attributes.attribute(patched, true) }
        }
    }

    /** Složka nativní knihovny pro JVM, na kterém běží Gradle — tentýž JDK pak aplikaci spouští i balí. */
    private fun tdlibPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val system = when {
            "mac" in os -> "macos"
            "windows" in os -> "windows"
            else -> "linux"
        }
        return "$system/" + if ("aarch64" in arch || "arm64" in arch) "arm64" else "x64"
    }
}

abstract class TdlibJarTransform : TransformAction<TdlibJarTransform.Parameters> {

    interface Parameters : TransformParameters {
        /** Složka ve jaru wrapperu pro tenhle stroj: `macos/arm64`, `linux/x64`, `windows/x64`… */
        @get:Input
        val platform: Property<String>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        if (!input.name.startsWith("tdl-coroutines-jvm")) {
            outputs.file(input)
            return
        }
        val platform = parameters.platform.get()
        val output = outputs.file(input.nameWithoutExtension + "-plume.jar")
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val native = NATIVE_ROOTS.any { name.startsWith("$it/") }
                    if (native && !name.startsWith("$platform/")) continue
                    var bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (name.endsWith(".class") && name.startsWith(WRAPPER_PACKAGE)) bytes = hookReceive(bytes)
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
    }

    private fun hookReceive(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        // Writer z readeru a bez COMPUTE_*: rámce i max zásobníku zůstávají, vložené volání
        // bere String? a vrací String?, tvar zásobníku se nemění.
        val writer = ClassWriter(reader, 0)
        reader.accept(TdlibReceiveHookVisitor(writer), 0)
        return writer.toByteArray()
    }

    private companion object {
        val NATIVE_ROOTS = listOf("linux", "macos", "windows")
    }
}
