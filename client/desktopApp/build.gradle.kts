import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":client:shared"))
    // Skia (Skiko) pro systém, na kterém se staví. Balí se taky jen pro něj — jpackage cizí
    // systém neumí, takže macOS balíček vzniká na Macu, Windows na Windows.
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    // Vzhled titulku okna na macOS podle systému (`setMacOsAdaptiveTitleBar`).
    implementation(libs.platformtools.darkmodedetector)
    // `startKoin` vrací KoinApplication; `implementation` závislosti sdíleného modulu tranzitivní nejsou.
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
}

/** Verze balíčku: `-PdesktopVersion=X.Y.Z` (release z tagu), jinak 1.0.0 pro lokální build. */
val desktopVersion: String = providers.gradleProperty("desktopVersion").orNull ?: "1.0.0"

/**
 * Text pro metadata balíčku. **WiX (MSI) bere jen znaky z kódové stránky 1252** — „ě" v ní není
 * a `light.exe` končí kódem 311 (LGHT0311), jpackage pak hlásí jen „exit code 1". Na Windows se
 * proto text, který se do cp1252 nevejde, zbaví diakritiky celý („Vladimir Tintera", ne napůl
 * „Vladimír Tintera"). Jinde zůstává beze změny.
 */
fun msiSafe(text: String): String {
    val windows = System.getProperty("os.name").lowercase().contains("windows")
    if (!windows || Charset.forName("windows-1252").newEncoder().canEncode(text)) return text
    return Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

compose.desktop {
    application {
        mainClass = "eu.tintera.plume.desktop.MainKt"

        // `-PdesktopDebug`: zabalená aplikace (`runDistributable`) v ladicím režimu nad týmiž
        // daty jako `run`. Oznámení na macOS fungují jen v `.app` balíčku, holý `java` z `run`
        // je nemá (docs/poznatky/desktop.md, „Notifikace"). Do balíčku k vydání to nepatří —
        // vlastnosti by se zapekly do Plume.cfg.
        if (providers.gradleProperty("desktopDebug").isPresent) {
            jvmArgs += listOf(
                "-Dplume.debug=true",
                // Lomítka `/`, ne `\`: jpackage bere zpětné lomítko v Plume.cfg jako escape a z cesty
                // na Windows udělal `C:Develop<tab>elegram…` (z `\t`). Java `/` na Windows přijme.
                "-Dplume.dataDir=" + rootProject.layout.projectDirectory.dir(".desktop-data").asFile.invariantSeparatorsPath,
            )
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Plume"
            // jpackage chce MAJOR.MINOR.PATCH, MSI navíc MAJOR a MINOR do 255 a PATCH do 65535,
            // macOS MAJOR > 0. Podle verze MSI pozná aktualizaci: nainstalovat stejnou verzi
            // znovu nejde, nižší přes vyšší taky ne. Release ji bere z tagu (`desktop-release.yml`).
            packageVersion = desktopVersion
            // Podle ToS Telegram API smí slovo Telegram stát jen za „Unofficial".
            description = "Unofficial Telegram client"
            copyright = msiSafe("© 2026 Vladimír Tintěra. MIT License.")
            vendor = msiSafe("Vladimír Tintěra")
            // Moduly JDK pro jlink, podle `./gradlew :client:desktopApp:suggestRuntimeModules`.
            // `jdk.unsupported` je sun.misc.Unsafe (DataStore, kotlinx). Po nové závislosti
            // úlohu pustit znovu — chybějící modul se ukáže až za běhu zabalené aplikace.
            modules("java.instrument", "jdk.unsupported")

            // Ikony generuje `play/tools/icon.py desktop` z téže geometrie jako ostatní platformy.
            windows {
                iconFile.set(project.file("icons/plume.ico"))
                // Instalace jen pro uživatele (%LOCALAPPDATA%): bez oprávnění správce, což
                // u nepodepsaného instalátoru ušetří další varování.
                perUserInstall = true
                menu = true
                menuGroup = "Plume"
                shortcut = true
                // Podle něj MSI pozná, že jde o tutéž aplikaci, a starší verzi nahradí.
                // NESMÍ SE MĚNIT — nové UUID by nainstalovalo druhou kopii vedle staré.
                upgradeUuid = "d2ef8c8b-9027-4c2e-86a3-0e8d787909da"
            }
            macOS {
                bundleID = "eu.tintera.plume"
                iconFile.set(project.file("icons/plume.icns"))
                appCategory = "public.app-category.social-networking"
                // Podle nejvyššího `minos` přibalených nativních knihoven (`vtool -show-build`):
                // TDLib z wrapperu 12.0, ntgcalls (hovory, jen arm64) 14.0. Bez toho je
                // v Info.plist 10.13 a na starším systému by aplikace naběhla a spadla až při
                // nahrání knihovny; takhle ji Finder rovnou odmítne s vysvětlením.
                // Balí se jen pro architekturu JDK, které staví (jako `tdlibPlatform` níž).
                val arm64 = System.getProperty("os.arch").lowercase().let { "aarch64" in it || "arm64" in it }
                minimumSystemVersion = if (arm64) "14.0" else "12.0"
                // Hovory: podpis s hardened runtime potřebuje oprávnění ke kameře a mikrofonu,
                // jinak TCC dotaz neukáže a AVFoundation tiše nedává nic. Soubor nahrazuje
                // výchozí oprávnění Compose, proto je opakuje.
                entitlementsFile.set(project.file("entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("entitlements.plist"))
                // Bez popisu použití macOS aplikaci při prvním sáhnutí na kameru (AVFoundation
                // v ntgcalls) UKONČÍ; u mikrofonu by dotaz neměl čím vysvětlit.
                // Anglicky — Info.plist jinak než přes `.lproj` přeložit nejde.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>Plume uses the camera for video calls.</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Plume uses the microphone for calls.</string>
                    """.trimIndent()
                }
            }
            linux {
                packageName = "plume"
                iconFile.set(project.file("icons/plume.png"))
            }
        }
    }
}

/*
 * `./gradlew :client:desktopApp:run` je ladicí běh: vlastní adresář dat (nesahá na data
 * nainstalované aplikace) a `isDebugBuild`, stejně jako debug build na Androidu.
 */
afterEvaluate {
    tasks.named<JavaExec>("run") {
        systemProperty("plume.debug", "true")
        systemProperty("plume.dataDir", rootProject.layout.projectDirectory.dir(".desktop-data").asFile.path)
        systemProperty("apple.awt.application.name", "Plume")
        systemProperty("apple.awt.application.appearance", "system")
    }
}

// ---------------------------------------------------------------------------------------------
// Přepis jaru `tdl-coroutines-jvm`
//
// Wrapper publikuje desktopovou TDLib v jednom jaru pro všechny systémy (115 MB). Jar se při
// buildu přepíše artifact transformem:
//
// 1. **Jen nativní knihovna tohoto systému a architektury.** Balí se stejně jen pro systém,
//    na kterém se staví; cizí knihovny by v balíčku zabraly ~300 MB.
// 2. **Hook `TdlibJsonRepair`**: za každé `JsonClient.receive` uvnitř wrapperu se vloží
//    `TdlibReceiveHook.afterReceive`, stejně jako na Androidu instrumentací (viz
//    `TdlibReceiveHookFactory` v androidApp). JVM jar wrapperu obfuskovaný není.
//
// Do verze 15.0.0 se tu navíc přepisovaly cesty v macOS knihovně a přibalovalo OpenSSL
// (g000sha256/tdl-coroutines#163); od 16.0.0 je knihovna bez závislostí mimo systém.
// ---------------------------------------------------------------------------------------------

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
                    if (name.endsWith(".class") && name.startsWith("dev/g000sha256/tdl/")) bytes = hookReceive(bytes)
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
    }

    /** Totéž co `TdlibReceiveHookFactory` v androidApp, jen nad jednou třídou bez AGP. */
    private fun hookReceive(bytes: ByteArray): ByteArray {
        val reader = ClassReader(bytes)
        // Writer z readeru a bez COMPUTE_*: rámce i max zásobníku zůstávají, vložené volání
        // bere String? a vrací String?, tvar zásobníku se nemění.
        val writer = ClassWriter(reader, 0)
        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    if (owner == "org/drinkless/tdlib/JsonClient" && name == "receive" && descriptor == "(D)Ljava/lang/String;") {
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
        }, 0)
        return writer.toByteArray()
    }

    private companion object {
        val NATIVE_ROOTS = listOf("linux", "macos", "windows")
    }
}

/** Složka nativní knihovny pro JVM, na kterém běží Gradle — tentýž JDK pak aplikaci spouští i balí. */
val tdlibPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val system = when {
        "mac" in os -> "macos"
        "windows" in os -> "windows"
        else -> "linux"
    }
    "$system/" + if ("aarch64" in arch || "arm64" in arch) "arm64" else "x64"
}

val tdlibPatched = Attribute.of("eu.tintera.plume.tdlibPatched", Boolean::class.javaObjectType)

dependencies {
    attributesSchema { attribute(tdlibPatched) }
    artifactTypes.getByName("jar") { attributes.attribute(tdlibPatched, false) }
    registerTransform(TdlibJarTransform::class) {
        from.attribute(tdlibPatched, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(tdlibPatched, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        parameters {
            platform.set(tdlibPlatform)
        }
    }
}

configurations.named("runtimeClasspath") {
    attributes.attribute(tdlibPatched, true)
}
