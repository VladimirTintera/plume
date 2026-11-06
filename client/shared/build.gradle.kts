import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    // Kontroly zdrojáků na `check` (build-logic/); jazyky hlídá :client:resources.
    id("plume.source-checks")
}

/**
 * Zapeče poznámky k vydání z `release-notes/` do aplikace — „Co je nového" v Nastavení
 * (`core/changelog`). Generuje je `.github/scripts/release_notes.py bundle`, tedy **tentýž skript**,
 * který skládá text pro Google Play; dvě kopie pravidel by se rozešly.
 *
 * - **Kotlin, ne compose resource**: seznam je dostupný synchronně na obou platformách a chyba
 *   v generátoru spadne při překladu, ne za běhu.
 * - **Nikdy up-to-date**: vstupem jsou i git tagy, ne jen soubory, takže kontrola vstupů by lhala.
 *   Generování trvá pár vteřin a překlad se přeskočí, když vyjde stejný obsah.
 * - **Bez pythonu nebo gitu se build nesmí rozbít** (cizí stroj, mělký klon): zapíše se prázdný
 *   seznam a sekce řekne, že novinky v téhle sestavě nejsou. Důvod se loguje — tichý fallback by
 *   vypadal jako „žádná vydání".
 * - **Na CI (`CI` v prostředí) ale build spadne.** Release se staví jen tam a prázdný seznam by
 *   odešel uživatelům: desktop 1.0.0 vyšel bez „Co je nového", protože git na self-hosted
 *   Windows runneru skončil chybou a jediná stopa byla řádka v logu, kterou nikdo nečetl.
 *
 * MUSÍ být deklarovaný PŘED blokem `kotlin { }`, který ho přidává do `commonMain`: top-level `val`
 * se v `.kts` inicializují v pořadí deklarace (stejná past jako `releaseKeystoreFile`).
 */
val generateChangelog = tasks.register("generateChangelog") {
    description = "Zapeče poznámky z release-notes/ do aplikace (core/changelog)."
    // Konfigurační keš neunese odkaz na skript uvnitř `doLast` — cesty se vytáhnou už tady.
    val script = rootProject.file(".github/scripts/release_notes.py")
    val repoDir = rootProject.projectDir
    val outputDir = layout.buildDirectory.dir("generated/changelog/kotlin")
    val requireChangelog = System.getenv("CI") != null
    outputs.dir(outputDir)
    outputs.upToDateWhen { false }

    doLast {
        val target = outputDir.get().file("eu/tintera/plume/core/changelog/GeneratedChangelog.kt").asFile
        target.parentFile.mkdirs()
        val failure = if (!script.exists()) {
            "chybí ${script.name}"
        } else {
            runCatching {
                // Na Windows bývá jen `python` (python.org i runner GitHubu), `python3` ne.
                val process = listOf("python3", "python").firstNotNullOfOrNull { python ->
                    runCatching {
                        ProcessBuilder(python, script.absolutePath, "bundle", target.absolutePath)
                            .directory(repoDir)
                            .redirectErrorStream(true)
                            // Konzole Windows je v cp1252 a skript vypisuje češtinu: bez UTF-8 padal
                            // na UnicodeEncodeError a poznámky se nezapekly.
                            .apply { environment()["PYTHONIOENCODING"] = "utf-8" }
                            .start()
                    }.getOrNull()
                } ?: error("python3 ani python není na PATH")
                val output = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0) {
                    logger.info(output)
                    null
                } else {
                    "exit ${process.exitValue()}: $output"
                }
            }.getOrElse { it.message ?: it.toString() }
        }
        if (failure != null) {
            if (requireChangelog) throw GradleException("generateChangelog: poznámky k vydání se nezapekly ($failure)")
            logger.lifecycle("generateChangelog: poznámky k vydání se nezapekly ($failure)")
            target.writeText(
                """
                // Fallback: build neměl python nebo git, poznámky se nezapekly.
                package eu.tintera.plume.core.changelog

                internal val GENERATED_CHANGELOG: Map<String, List<ChangelogRelease>> = emptyMap()
                """.trimIndent() + "\n"
            )
        }
    }
}

/**
 * Notifikace na macOS (`jvmMain/objc/PlumeNotifications.m`, JNI nad `UNUserNotificationCenter`).
 *
 * - **Jen na Macu.** Jinde se úloha přeskočí a knihovna v jaru nebude — `MacNotificationCenter`
 *   ji pak nenajde a notifikace jdou přes AWT. Balíček pro macOS stejně vzniká jen na Macu.
 * - **Univerzální binárka** (arm64 + x86_64), ať nezáleží na tom, jaké JDK aplikaci pouští.
 * - **JNI hlavičky z JDK, které pouští Gradle.** `jni.h` se mezi verzemi nemění tak, aby na tom
 *   záleželo, a toolchain tu hledat není nutné.
 * - **Ad-hoc podpis**: na arm64 Macu se nepodepsaná knihovna nenahraje.
 *
 * MUSÍ být deklarovaný před blokem `kotlin { }` (pořadí top-level `val`, viz [generateChangelog]).
 */
/** JDK s hlavičkami JNI pro nativní mosty notifikací — tentýž, jakým se překládá Kotlin. */
val jniToolchainHome: Provider<String> = javaToolchains
    .launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
    .map { it.metadata.installationPath.asFile.absolutePath }

/**
 * Nativní knihovna ntgcalls pro hovory na desktopu (`core/calls/DesktopCallMedia.kt`) — jen pro
 * systém, na kterém se staví (jako TDLib), z vydání na GitHubu, ověřená SHA-256 (`digest`
 * v API vydání). Vybalí se do resources `jvmMain` pod prefix JNA (`darwin-aarch64/…`), kde ji
 * `Native.load` sama najde. Zip se drží v mezipaměti Gradlu, ne v `build/`, ať ho `clean`
 * nestahuje znovu (12–40 MB).
 *
 * **Bez sítě nebo na nepodporovaném systému (Intel Mac) build nespadne** — knihovna chybí
 * a hovory se na desktopu nenabídnou (`DesktopCallMedia.available`).
 */
/** Klasifikátor nativních jarů JavaCPP (FFmpeg) pro systém, na kterém se staví; `null` = žádný. */
val javacppPlatform: String? = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val arm = arch == "aarch64" || arch == "arm64"
    when {
        os.contains("mac") -> if (arm) "macosx-arm64" else "macosx-x86_64"
        os.contains("win") && !arm -> "windows-x86_64"
        os.contains("linux") -> if (arm) "linux-arm64" else "linux-x86_64"
        else -> null
    }
}

val ntgcallsVersion = libs.versions.ntgcalls.get()
val downloadNtgcalls = tasks.register("downloadNtgcalls") {
    description = "Stáhne nativní ntgcalls pro hovory na desktopu do jvmMain resources."
    val outputDir = layout.buildDirectory.dir("generated/ntgcalls")
    // Lokální kopie: akce úlohy nesmí sahat na proměnné skriptu (konfigurační cache).
    val version = ntgcallsVersion
    val cacheDir = gradle.gradleUserHomeDir.resolve("caches/plume-ntgcalls/$version")
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val arm = arch == "aarch64" || arch == "arm64"
    // Soubor vydání, jeho SHA-256 (z API vydání) a prefix JNA pro tenhle systém.
    val asset: Triple<String, String, String>? = when {
        os.contains("mac") && arm -> Triple("macos-arm64", "da3a98767978facf093d363c44651204259f5e896f84fe3a7eb170d53ed07578", "darwin-aarch64")
        os.contains("win") && !arm -> Triple("windows-x86_64", "925efdfee4042f243e5f9ce5dad5b8f13236c54e8dd31bc2108abf071af6420d", "win32-x86-64")
        os.contains("linux") && !arm -> Triple("linux-x86_64", "0e579e0cae2ff7133222a67a74e15af53a6c7d6ae2655fddd75d412876ad33c4", "linux-x86-64")
        os.contains("linux") && arm -> Triple("linux-arm64", "2958e1bc0707955b1606d0fe69ea6082e7a47358b90e5cb307274d9388db0bb8", "linux-aarch64")
        else -> null
    }
    inputs.property("asset", asset?.first ?: "none")
    inputs.property("version", version)
    outputs.dir(outputDir)

    doLast {
        val target = outputDir.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        if (asset == null) {
            logger.warn("ntgcalls pro $os/$arch není, hovory na desktopu nebudou.")
            return@doLast
        }
        val (name, sha256, prefix) = asset
        val zip = cacheDir.resolve("ntgcalls.$name-shared_libs.zip")
        fun digest(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        runCatching {
            if (!zip.exists() || digest(zip) != sha256) {
                cacheDir.mkdirs()
                val url = "https://github.com/pytgcalls/ntgcalls/releases/download/v$version/${zip.name}"
                URI(url).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
                check(digest(zip) == sha256) { "SHA-256 ${zip.name} nesedí, soubor smazán." }
            }
            ZipFile(zip).use { archive ->
                val library = archive.entries().asSequence().firstOrNull { entry ->
                    !entry.isDirectory && entry.name.substringAfterLast('/').let {
                        it == "libntgcalls.dylib" || it == "ntgcalls.dll" || it == "libntgcalls.so"
                    }
                } ?: error("V ${zip.name} není knihovna ntgcalls.")
                val out = target.resolve("$prefix/${library.name.substringAfterLast('/')}")
                out.parentFile.mkdirs()
                archive.getInputStream(library).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }.onFailure { failure ->
            zip.takeIf { it.exists() && runCatching { digest(it) != sha256 }.getOrDefault(true) }?.delete()
            logger.warn("ntgcalls se nepodařilo připravit (${failure.message}), hovory na desktopu nebudou.")
        }
    }
}

val compileMacNotifications = tasks.register("compileMacNotifications") {
    description = "Přeloží nativní most macOS (notifikace, titulek okna, kamera, zvuková zařízení) do jvmMain resources."
    // Jedna knihovna pro všechno nativní na Macu, jako `plumenotifications.dll` na Windows.
    val sources = listOf("PlumeNotifications.m", "PlumeWindow.m", "PlumeCamera.m", "PlumeAudio.m")
        .map { layout.projectDirectory.file("src/jvmMain/objc/$it") }
    val outputDir = layout.buildDirectory.dir("generated/macNotifications")
    // Hlavičky JNI z toolchainu projektu, ne z JVM démona Gradle: démona může spustit IDE se
    // svým runtime (JBR z Android Studia), který `include/` nemá — překlad pak padal na `jni.h`.
    val jdkHome = jniToolchainHome
    val onMac = System.getProperty("os.name").lowercase().contains("mac")
    inputs.files(sources)
    outputs.dir(outputDir)
    onlyIf { onMac }

    doLast {
        val library = outputDir.get().file("macos/libplumenotifications.dylib").asFile
        library.parentFile.mkdirs()
        fun run(vararg command: String) {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "${command.first()} selhal:\n$output" }
        }
        run(
            "clang", "-dynamiclib", "-fobjc-arc", "-O2",
            "-arch", "arm64", "-arch", "x86_64", "-mmacosx-version-min=11.0",
            "-I${jdkHome.get()}/include", "-I${jdkHome.get()}/include/darwin",
            "-framework", "Foundation", "-framework", "UserNotifications", "-framework", "AppKit",
            "-framework", "AVFoundation", "-framework", "CoreMedia", "-framework", "CoreVideo", "-framework", "CoreAudio",
            "-o", library.absolutePath, *sources.map { it.asFile.absolutePath }.toTypedArray(),
        )
        run("codesign", "--force", "--sign", "-", library.absolutePath)
    }
}

/**
 * Most k toastům Windows (`src/jvmMain/cpp/PlumeToasts.cpp`, C++/WinRT) do jvmMain resources.
 * Potřebuje MSVC s Windows SDK (Visual Studio nebo Build Tools, úloha „Vývoj desktopových
 * aplikací pomocí C++"). Bez nich se lokálně přeskočí a oznámení půjdou přes AWT jako dřív;
 * na CI (`CI`) je to chyba, ať release nevyjde potichu bez toastů.
 */
val compileWindowsNotifications = tasks.register("compileWindowsNotifications") {
    description = "Přeloží most k toastům Windows (WinRT) do jvmMain resources."
    val source = layout.projectDirectory.file("src/jvmMain/cpp/PlumeToasts.cpp")
    val outputDir = layout.buildDirectory.dir("generated/windowsNotifications")
    val workDir = layout.buildDirectory.dir("tmp/windowsNotifications")
    // Hlavičky JNI z toolchainu projektu, ne z JVM démona Gradle: démona může spustit IDE se
    // svým runtime (JBR z Android Studia), který `include/` nemá — překlad pak padal na `jni.h`.
    val jdkHome = jniToolchainHome
    val onWindows = System.getProperty("os.name").lowercase().contains("windows")
    // Knihovna pro architekturu JDK, které aplikaci spouští i balí (jako TDLib v desktopApp).
    val arch = if (System.getProperty("os.arch").lowercase().let { "aarch64" in it || "arm64" in it }) "arm64" else "x64"
    val requireToolchain = System.getenv("CI") != null
    inputs.file(source)
    outputs.dir(outputDir)
    onlyIf { onWindows }

    doLast {
        val vswhere = File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Microsoft Visual Studio\\Installer\\vswhere.exe")
        val installation = vswhere.takeIf { it.isFile }?.let {
            val process = ProcessBuilder(
                it.path, "-latest", "-products", "*",
                "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                "-property", "installationPath",
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
        }?.takeIf { it.isNotEmpty() }
        val vcvars = installation?.let { File(it, "VC\\Auxiliary\\Build\\vcvarsall.bat") }?.takeIf { it.isFile }
        if (vcvars == null) {
            val message = "Chybí MSVC (Visual Studio s C++), toasty Windows se nepřeloží a oznámení půjdou přes AWT."
            if (requireToolchain) throw GradleException(message)
            logger.warn(message)
            return@doLast
        }
        val library = outputDir.get().file("windows/plumenotifications.dll").asFile
        library.parentFile.mkdirs()
        val work = workDir.get().asFile.apply { mkdirs() }
        // vcvarsall nastaví prostředí jen pro cmd, ve kterém běží — překlad proto jde jedním skriptem.
        // C++20, ne 17: C++/WinRT pod C++17 sahá po <experimental/coroutine> a MSVC z Visual
        // Studia 2026 (windows-latest) ho odmítá chybou STL1011; C++20 bere standardní <coroutine>.
        // /we4129 = neznámá escape sekvence je chyba, ne varování: cesta k registru napsaná
        // s jednoduchými lomítky („Software\Microsoft…") se přeložila jako „SoftwareMicrosoft…"
        // a spouštění po přihlášení se zapsalo do nesmyslného klíče — jen s varováním C4129.
        val script = File(work, "build.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "call \"${vcvars.path}\" $arch >nul || exit /b 1",
                "cl /nologo /LD /EHsc /std:c++20 /O2 /MT /permissive- /W3 /we4129 " +
                    "/I \"${jdkHome.get()}\\include\" /I \"${jdkHome.get()}\\include\\win32\" " +
                    "/Fo\"${work.path}\\\\\" \"${source.asFile.path}\" /Fe\"${library.path}\" " +
                    "/link runtimeobject.lib ole32.lib oleaut32.lib advapi32.lib shell32.lib propsys.lib shlwapi.lib",
            ).joinToString("\r\n"),
        )
        val process = ProcessBuilder("cmd", "/c", script.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "Překlad toastů Windows selhal:\n$output" }
        // Linker nechává importní knihovnu vedle DLL; do resources nepatří.
        listOf("lib", "exp").forEach { File(library.parentFile, "plumenotifications.$it").delete() }
    }
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // Expressive API je celé experimentální; opt-in globálně, ať to nemusí být na každé funkci.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.coroutines.FlowPreview")
        // kotlinx-datetime 0.8 staví na kotlin.time.Instant.
        optIn.add("kotlin.time.ExperimentalTime")
    }

    // AGP 9: Android target jde přes com.android.kotlin.multiplatform.library, ne com.android.library.
    android {
        namespace = "eu.tintera.plume.client"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            // KMP moduly mají Android resources defaultně vypnuté; bez tohohle se composeResources
            // nezabalí a každé čtení stringu spadne až za běhu na „No instrumentation registered!".
            enable = true
        }
        // Bez tohohle se commonTest na JVM vůbec nespustí.
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            // TDLib je statická knihovna; framework musí být statický, jinak se její symboly
            // nedostanou do výsledné binárky a link padne na undefined symbols _td_*.
            isStatic = true
        }
    }

    // Desktop (macOS, Linux, Windows). Vstupní bod a balení je v :client:desktopApp, tady jen
    // knihovna — stejné rozdělení jako u Androidu. TDLib (`tdl-coroutines-jvm` i s nativní
    // knihovnou pro všechny tři systémy) přichází přes :client:core.
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Platformy bez Material You (dynamic color ze systému): iOS a desktop.
        val nonDynamicThemeMain by creating {
            dependsOn(commonMain.get())
        }
        // Platformy, kde obrázky kreslí Skia přímo (Compose ji tam přibaluje): iOS a desktop.
        // Výroba samolepek (`SkiaStickers`) — WebP tam umí zakódovat jen Skia.
        val skiaMain by creating {
            dependsOn(commonMain.get())
        }
        // Model v zařízení přes LiteRT-LM (Gemma, Qwen): Android a desktop. Stahování modelu
        // a stav pro obrazovku (`core/ai/LiteRtOnDeviceAi`) jsou společné; samotnou knihovnu
        // LiteRT-LM tu nevidíme, jen rozhraní enginu. Implementace (:client:ai-litert) přichází
        // na Androidu jako součást z Google Play až na požádání, na desktopu z desktopApp.
        val liteRtMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(projects.client.aiEngine)
            }
        }

        commonMain {
            // „Co je nového" v Nastavení — poznámky z release-notes/ zapečené při buildu.
            kotlin.srcDir(generateChangelog)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.animation)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            // Texty, ikony, fonty a `Res` (eu.tintera.plume.resources) — viz client/resources.
            implementation(projects.client.resources)
            // Vrstva kolem TDLib (client/core). `api`: její typy (repository, ChatMessage…) jsou
            // v API shared a vstupní body je potřebují taky.
            api(projects.client.core)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material3)

            implementation(libs.androidx.adaptive)
            implementation(libs.androidx.adaptive.layout)
            implementation(libs.androidx.adaptive.navigation3)
            implementation(libs.navigation)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)

            // Generuje expressive ColorScheme ze seed barvy — náhrada Material You tam,
            // kde systém tapetovou paletu nedává (viz theme/ColorScheme.kt).
            implementation(libs.materialkolor)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.navigation3)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
            implementation(libs.coil.compose)
            // Lite = bez JavaScriptového enginu pro výrazy. Samolepky Telegramu výrazy nepoužívají
            // (oficiální rlottie je ani neumí), takže by to byla jen váha navíc.
            implementation(libs.compottie.lite)
            implementation(libs.haze)
            implementation(libs.haze.blur)
            implementation(libs.okio)
            implementation(libs.datastore.preferences.core)
            // TDLib tu na classpath NENÍ — jen v :client:core (viz „Hranice kolem TDLib" v CLAUDE.md).
        }

        androidMain {
            dependsOn(liteRtMain)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // Widget s chaty na ploše (core/widget).
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.core.ktx)
            // Odkazy v aplikaci (Custom Tabs, viz core/ui/UrlOpener).
            implementation(libs.androidx.browser)
            // Hovory (sonda): ntgcalls, viz docs/poznatky/hovory.md.
            implementation(libs.ntgcalls)
            implementation(libs.androidx.core.telecom)
            // QR kód přihlášení. Na iOS ho generuje CoreImage, tam závislost netřeba.
            implementation(libs.zxing.core)
            // Vestavěný výběr fotek (viz core/ui/EmbeddedMediaPicker). Kde ho systém nemá,
            // zůstává klasický Photo Picker — knihovna se jen nezavolá.
            implementation(libs.androidx.photopicker.compose)
            implementation(libs.media3.exoplayer)
            // Video kolečka — jediné místo, kde smí být CameraX, je core/camera. `camera-view`
            // kvůli PreviewView: `camera-compose` táhne androidx Compose novější, než drží
            // Compose Multiplatform (stejná úvaha jako `media3-ui` místo `media3-ui-compose`).
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.video)
            implementation(libs.camerax.view)
            // Kolečko z kamery bez SD (480p) se po nahrání zmenší na 480 × 480 — Telegram větší
            // kolečko převede na obyčejné video.
            implementation(libs.media3.transformer)
            implementation(libs.media3.effect)
            implementation(libs.media3.ui)
            // Hudba na pozadí: MediaSession, notifikace s ovládáním a služba v popředí.
            implementation(libs.media3.session)
            // Stahování modelu Gemma na pozadí (core/ai/ModelDownloadWorker). V aplikaci už je
            // kvůli notifikacím, APK se tím nezvětší.
            implementation(libs.androidx.work.runtime)
            implementation(libs.play.app.update)
            // Čtečka QR kódů pro přihlášení jiného zařízení — viz core/ui/QrScanner.
            implementation(libs.play.code.scanner)
            // Text z fotky v prohlížeči médií — viz core/ocr.
            implementation(libs.play.text.recognition)
            implementation(libs.play.subject.segmentation)
            // Překlad v telefonu — viz core/translation.
            implementation(libs.mlkit.translate)
            // Gemini Nano v zařízení. Jediné místo v projektu, kde smí být ML Kit GenAI — viz core/ai.
            implementation(libs.mlkit.genai.prompt)
            // Strukturovaný výstup Gemini Nano (`GenerableProvider` pro typy odpovědí). Jen popis
            // tvaru, KSP procesor ne — providery jsou ručně v core/ai.
            implementation(libs.mlkit.genai.schema)
            // Gemma a Qwen v zařízení: runtime LiteRT-LM se doinstaluje z Google Play až na
            // požádání (core/ai/PlayLiteRtRuntime, součást :client:litertFeature). Knihovna sama
            // tu NENÍ — jen v :client:ai-litert, na kterém visí ta součást.
            implementation(libs.play.feature.delivery)
            // androidContext() v platformModule — Koin na Androidu potřebuje Context pro cestu k DB.
            implementation(libs.koin.android)
        }

        iosMain {
            dependsOn(nonDynamicThemeMain)
            dependsOn(skiaMain)
        }

        jvmMain {
            dependsOn(nonDynamicThemeMain)
            dependsOn(skiaMain)
            dependsOn(liteRtMain)
        }

        jvmMain {
            // libplumenotifications.dylib z [compileMacNotifications]; mimo Mac je složka prázdná.
            resources.srcDir(files(layout.buildDirectory.dir("generated/macNotifications")).builtBy(compileMacNotifications))
            // plumenotifications.dll z [compileWindowsNotifications]; mimo Windows je složka prázdná.
            resources.srcDir(files(layout.buildDirectory.dir("generated/windowsNotifications")).builtBy(compileWindowsNotifications))
            // Nativní ntgcalls pro hovory z [downloadNtgcalls] (jen pro systém, na kterém se staví).
            resources.srcDir(files(layout.buildDirectory.dir("generated/ntgcalls")).builtBy(downloadNtgcalls))
        }

        jvmMain.dependencies {
            // Dispatchers.Main na desktopu je fronta událostí Swingu (AWT), ve které běží Compose.
            implementation(libs.kotlinx.coroutines.swing)
            // QR kód přihlášení, stejně jako na Androidu.
            implementation(libs.zxing.core)
            // Hovory: C API ntgcalls (`core/calls/NtgNative.kt`).
            implementation(libs.jna)
            // Gemma a Qwen v zařízení: runtime LiteRT-LM (:client:ai-litert) přidává až desktopApp,
            // tady je jen rozhraní (liteRtMain). Najde se za běhu (`BundledLiteRtRuntime`).
            // Tmavý titulek okna na Windows (`WindowTitleBarAppearance`).
            implementation(libs.platformtools.darkmodedetector)
            // Dekodér videa (GIFy, `core/video/Video.jvm.kt`): FFmpeg přes JavaCPP. Nativní
            // knihovny jen pro systém, na kterém se staví — jako Skia a TDLib; jinde by v balíčku
            // ležely navíc desítky MB. Na neznámém systému chybí a GIF zůstane náhledem.
            implementation(libs.javacpp)
            implementation(libs.ffmpeg)
            javacppPlatform?.let { platform ->
                implementation("org.bytedeco:javacpp:${libs.versions.javacpp.get()}:$platform")
                implementation("org.bytedeco:ffmpeg:${libs.versions.ffmpeg.get()}:$platform")
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Náhražky typů z :client:core (Fake…, Unimplemented…, testMessage).
            implementation(projects.client.coreTesting)
        }

        jvmTest.dependencies {
            // Headless Compose UI testy (`runComposeUiTest` nad skiko scénou, bez okna a displeje).
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}

/**
 * Reporty Compose kompilátoru: které composables nejdou přeskočit a které třídy jsou pro Compose
 * nestabilní. Jen na vyžádání (`-PcomposeReports`), běžný build je nepotřebuje:
 * `./gradlew :client:shared:compileAndroidMain -PcomposeReports`, výstup v `build/compose-reports`.
 *
 * `compose-stability.conf` vyjmenovává neměnné třídy, které kompilátor sám za stabilní nepozná.
 */
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
    if (providers.gradleProperty("composeReports").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

// Prostředky a `Res` jsou v :client:resources. Vlastní `Res` by vznikla i bez prostředků (modul
// závisí na knihovně prostředků) a srazila by se s tou z :client:resources.
compose.resources {
    generateResClass = never
}
