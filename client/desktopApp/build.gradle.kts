import org.jetbrains.compose.desktop.application.dsl.AotMode
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.charset.Charset
import java.text.Normalizer

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Jar wrapperu jen s nativní knihovnou tohoto systému a s hookem TdlibJsonRepair (build-logic).
    id("plume.tdlib-desktop")
    // Jar LiteRT-LM (model v zařízení) jen s nativní knihovnou tohoto systému (build-logic).
    id("plume.litertlm-desktop")
    // Licence knihoven pro „O aplikaci" (build-logic, Licenses.kt).
    id("plume.licenses")
    // Textové pole v jaru foundation: zúžení okna s otevřenou konverzací jinak shodí scénu (build-logic).
    id("plume.compose-text-field-desktop")
}

plumeLicenses {
    platform = "desktop"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":client:shared"))
    // Gemma a Qwen v zařízení (LiteRT-LM, knihovna ai-engine v external/ai-engine). Na Androidu je to součást z Google Play, tady přímo
    // v balíčku; `shared` si implementaci najde za běhu (`BundledLiteRtRuntime`).
    runtimeOnly("eu.tintera.ai:ai-litert")
    // EuroLLM (llama.cpp), s nativní knihovnou přeloženou pro tenhle systém (ai-engine :ai-llamacpp-desktop).
    runtimeOnly("eu.tintera.ai:ai-llamacpp-desktop")
    // Skia (Skiko) pro systém, na kterém se staví. Balí se taky jen pro něj — jpackage cizí
    // systém neumí, takže macOS balíček vzniká na Macu, Windows na Windows.
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    // Log z `main` (doba startu, tréninkový běh AOT); `implementation` ze `shared` tranzitivní není.
    implementation(libs.kermit)
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

/**
 * JDK, se kterým se aplikace balí (runtime z jlinku) i spouští (`run`). Compose jinak bere JDK,
 * na kterém běží Gradle — release s `setup-java 17` by tak dostal Javu 17, a na ní se model
 * v zařízení nenačte: `litertlm-jvm` je přeložený pro Javu 21 (class file 65,
 * `UnsupportedClassVersionError`). Kód aplikace zůstává na `jvmToolchain(17)`.
 *
 * **25 kvůli AOT cache** (`AotMode.AotPrebuild` níž, JEP 514/515 chce JDK 25; s 21 by šlo jen
 * AppCDS, které nezrychlí linkování ani JIT). Toolchain si ho stáhne přes foojay.
 */
val runtimeLauncher: Provider<JavaLauncher> = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }

compose.desktop {
    application {
        mainClass = "eu.tintera.plume.desktop.MainKt"
        javaHome = runtimeLauncher.get().metadata.installationPath.asFile.absolutePath

        // JDK 24+ varuje na stderr při každém JNI/JNA (TDLib, Skiko, FFmpeg, ntgcalls) a při
        // `sun.misc.Unsafe` (DataStore, kotlinx). Nativní knihovny jsou podstata aplikace, ne
        // nehoda — povolují se výslovně. Platí pro balíček i pro `run`.
        jvmArgs += RUNTIME_JVM_ARGS

        /*
         * Release (`packageReleaseMsi` / `packageReleaseDmg`) = balíček s AOT cache (Compose 1.13).
         * Plugin aplikaci při balení jednou spustí (tréninkový běh, `-Dcompose.aot.training-run`,
         * v kódu `desktopTrainingRun` / `TrainingRun`), JVM zapíše načtené a slinkované třídy do
         * `app.aot` vedle aplikace a další starty ho berou. Nepasuje-li cache (jiný JDK), JVM
         * ji tiše přeskočí — `exitAppOnAotFailure` zůstává vypnuté.
         * ProGuard ne: pravidla pro wrapper TDLib, Koin a serializaci na desktopu nejsou; build
         * typ `release` je tu jen kvůli `aot`, které na výchozím build typu není.
         * Viz „Rychlejší start (AOT cache)" v docs/poznatky/desktop.md.
         */
        buildTypes.release {
            proguard { isEnabled.set(false) }
            // `-PdesktopAot=false`: release bez cache (a bez tréninkového běhu), kdyby se na
            // runneru aplikace spustit nedala — vydání pak nemusí čekat na opravu.
            val aotEnabled = providers.gradleProperty("desktopAot").orNull != "false"
            aot { mode = if (aotEnabled) AotMode.AotPrebuild else AotMode.None }
        }

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
            // `jdk.management` je velikost paměti počítače pro model v zařízení (`DesktopLiteRtPlatform`).
            modules("java.instrument", "jdk.unsupported", "jdk.management")

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
                // Balí se jen pro architekturu JDK, které staví (jako nativní TDLib, plugin `plume.tdlib-desktop`).
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
                        <string>Plume uses the microphone for calls and voice messages.</string>
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
        javaLauncher.set(runtimeLauncher)
        systemProperty("plume.debug", "true")
        systemProperty("plume.dataDir", rootProject.layout.projectDirectory.dir(".desktop-data").asFile.path)
        systemProperty("apple.awt.application.name", "Plume")
        systemProperty("apple.awt.application.appearance", "system")
    }
}

/** Viz `jvmArgs` v `compose.desktop.application`. */
val RUNTIME_JVM_ARGS: List<String>
    get() = listOf("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
