import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.buildConfig)
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
            logger.lifecycle("generateChangelog: poznámky k vydání se nezapekly ($failure)")
            target.writeText(
                """
                // Fallback: build neměl python nebo git, poznámky se nezapekly.
                package eu.tintera.plume.core.changelog

                internal val GENERATED_CHANGELOG: List<ChangelogRelease> = emptyList()
                """.trimIndent() + "\n"
            )
        }
    }
}

/** Jazyky, které píšeme sami: musí mít všechny klíče ([verifyTranslations]) a do výběru jdou vždy. */
val completeLocales = setOf("cs")

/** Podíl přeložených klíčů, od kterého jde komunitní jazyk do výběru ([generateAppLanguages]). */
val minLanguageCoverage = 0.8

/**
 * Jazyky aplikace odvozené ze složek `composeResources/values-*`, aby nový jazyk z Weblate
 * nemusel nikdo zapisovat ručně na čtyři místa (viz „Jazyk aplikace" v docs/poznatky/nastaveni.md).
 *
 * - Výchozí `values` je angličtina, ta je v seznamu vždy první.
 * - [completeLocales] jdou do výběru vždy; komunitní jazyk až od [minLanguageCoverage]
 *   přeložených klíčů. Rozpracovaný jazyk by v aplikaci střídal věty s angličtinou a nabízet ho
 *   by bylo horší než ho nenabízet.
 * - Výstupem je Kotlin (`GENERATED_APP_LANGUAGES` pro `APP_LANGUAGES`) a `languages.txt`, ze
 *   kterého si `:client:androidApp` vyrobí `locales_config.xml`. Jedno místo pravidel pro oba.
 * - Složka `values-pt-rBR` (kvalifikátor regionu) je jazyk `pt-BR`.
 *
 * MUSÍ být deklarovaný před blokem `kotlin { }` (pořadí top-level `val`, viz [generateChangelog]).
 */
val generateAppLanguages = tasks.register("generateAppLanguages") {
    description = "Seznam jazyků aplikace ze složek values-* (APP_LANGUAGES, locales_config)."
    val resources = layout.projectDirectory.dir("src/commonMain/composeResources")
    inputs.files(fileTree(resources) { include("values*/strings.xml") })
    val resourcesDir = resources.asFile
    val complete = completeLocales
    val minCoverage = minLanguageCoverage
    val outputDir = layout.buildDirectory.dir("generated/appLanguages")
    // Výstupy jmenovitě, NE celá složka: AGP hledá vedle každého zdrojového adresáře složku
    // baselineProfiles (`generated/appLanguages/baselineProfiles` vedle `…/kotlin`) a kdyby
    // ležela uvnitř výstupu téhle úlohy, release build padne na nedeklarovanou závislost
    // `prepareAndroidMainArtProfile` → `generateAppLanguages`.
    outputs.dir(outputDir.map { it.dir("kotlin") })
    outputs.file(outputDir.map { it.file("languages.txt") })

    doLast {
        val keyPattern = Regex("""<(?:string|plurals) name="([^"]+)"""")
        fun keysOf(file: File) = keyPattern.findAll(file.readText()).map { it.groupValues[1] }.toSet()

        val defaults = keysOf(File(resourcesDir, "values/strings.xml"))
        val translated = resourcesDir.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .mapNotNull { dir ->
                val file = File(dir, "strings.xml").takeIf { it.isFile } ?: return@mapNotNull null
                val code = dir.name.removePrefix("values-").replace("-r", "-")
                val coverage = (keysOf(file) intersect defaults).size.toDouble() / defaults.size
                if (code !in complete && coverage < minCoverage) {
                    logger.lifecycle("generateAppLanguages: $code má ${(coverage * 100).toInt()} % — do výběru jazyků zatím nejde.")
                    return@mapNotNull null
                }
                code
            }
            .sorted()
        val languages = listOf("en") + translated

        val root = outputDir.get().asFile
        File(root, "languages.txt").apply { parentFile.mkdirs() }.writeText(languages.joinToString("\n") + "\n")
        File(root, "kotlin/eu/tintera/plume/core/locale/GeneratedAppLanguages.kt").apply { parentFile.mkdirs() }.writeText(
            """
            |// Vygenerováno úlohou generateAppLanguages ze složek composeResources/values-*. Neupravovat.
            |package eu.tintera.plume.core.locale
            |
            |internal val GENERATED_APP_LANGUAGES: List<String> = listOf(${languages.joinToString { "\"$it\"" }})
            |""".trimMargin(),
        )
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
    description = "Přeloží nativní most macOS (notifikace, titulek okna) do jvmMain resources."
    // Jedna knihovna pro všechno nativní na Macu, jako `plumenotifications.dll` na Windows.
    val sources = listOf("PlumeNotifications.m", "PlumeWindow.m")
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
    // knihovna — stejné rozdělení jako u Androidu. TDLib dodává `tdl-coroutines-jvm` i s nativní
    // knihovnou pro všechny tři systémy; macOS ji ale potřebuje opravit, viz desktopApp.
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Platformy bez Material You (dynamic color ze systému): iOS a desktop.
        val nonDynamicThemeMain by creating {
            dependsOn(commonMain.get())
        }

        commonMain {
            // „Co je nového" v Nastavení — poznámky z release-notes/ zapečené při buildu.
            kotlin.srcDir(generateChangelog)
            // Jazyky aplikace ze složek values-* (APP_LANGUAGES).
            kotlin.srcDir(files(layout.buildDirectory.dir("generated/appLanguages/kotlin")).builtBy(generateAppLanguages))
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.animation)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
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

            // Jediné místo v projektu, kde smí být TDLib na classpath — viz core/telegram.
            implementation(libs.tdl.coroutines)
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
            // Hlásič pádů. Jediné místo v projektu, kde smí být Crashlytics na classpath —
            // viz core/logging. Bez `google-services.json` se knihovna jen neinicializuje.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)
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
            // Překlad v telefonu — viz core/translation.
            implementation(libs.mlkit.translate)
            // Gemini Nano v zařízení. Jediné místo v projektu, kde smí být ML Kit GenAI — viz core/ai.
            implementation(libs.mlkit.genai.prompt)
            // Gemma v zařízení (LiteRT-LM). Taky jen v core/ai.
            implementation(libs.litertlm.android)
            // androidContext() v platformModule — Koin na Androidu potřebuje Context pro cestu k DB.
            implementation(libs.koin.android)
        }

        iosMain {
            dependsOn(nonDynamicThemeMain)
        }

        jvmMain {
            dependsOn(nonDynamicThemeMain)
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
            // Tmavý titulek okna na Windows (`WindowTitleBarAppearance`).
            implementation(libs.platformtools.darkmodedetector)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
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

compose.resources {
    packageOfResClass = "eu.tintera.plume.resources"
    generateResClass = always
}

/**
 * Hlídá, že se jazykové mutace nerozejdou.
 *
 * Výchozí je angličtina (`values/`), čeština je mutace (`values-cs/`). Chybějící klíč se jinak
 * pozná až na obrazovce — Compose spadne zpátky na výchozí jazyk, takže uprostřed české
 * aplikace vyskočí anglická věta a nic to nenahlásí.
 */
/**
 * Hlídá jazykové mutace `composeResources/values-*`.
 *
 * - **Jazyky, které píšeme sami ([completeLocales]), musí mít všechny klíče** — chybějící klíč by
 *   se poznal až na obrazovce: Compose spadne na výchozí jazyk a uprostřed české aplikace
 *   vyskočí anglická věta.
 * - **Komunitní jazyky (Weblate) chybějící klíč jen ohlásí.** Rozpracovaný jazyk padá na
 *   angličtinu po jednotlivých větách, a build kvůli tomu padat nesmí — jinak by každý nový
 *   klíč v angličtině rozbil překlad, dokud ho někdo nedoplní.
 * - **Formátovací argumenty jsou chyba u všech jazyků.** Překlad s `%2${'$'}s`, který angličtina nemá,
 *   nebo s `%1${'$'}d` místo `%1${'$'}s`, by se za běhu poskládal špatně. Řetězec musí mít tytéž argumenty
 *   jako výchozí; plurál smí v některém tvaru argument vynechat („Před minutou"), ale nesmí
 *   přidat cizí ani změnit typ.
 */
val verifyTranslations = tasks.register("verifyTranslations") {
    group = "verification"
    description = "Ověří jazykové mutace: klíče (cs úplně, komunitní jazyky varováním) a formátovací argumenty."

    val resources = layout.projectDirectory.dir("src/commonMain/composeResources")
    inputs.files(fileTree(resources) { include("values*/strings.xml") })
    // Konfigurační keš: do `doLast` jen obyčejné hodnoty, ne skriptové objekty.
    val resourcesDir = resources.asFile
    val complete = completeLocales

    doLast {
        val stringPattern = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val pluralsPattern = Regex("""<plurals name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        val itemPattern = Regex("""<item quantity="[^"]+"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
        val argPattern = Regex("""%(\d+)\$([a-z])""")

        // Klíč → (argumenty, je to plurál); u plurálu sjednocení argumentů přes všechny tvary.
        // Dvojice, ne lokální třída: tu kompilátor build skriptu uvnitř `doLast` neunese.
        fun argsOf(text: String): Set<String> = argPattern.findAll(text).map { it.groupValues[1] + "$" + it.groupValues[2] }.toSet()

        fun entriesOf(file: File): Map<String, Pair<Set<String>, Boolean>> {
            val text = file.readText()
            return stringPattern.findAll(text).associate { it.groupValues[1] to (argsOf(it.groupValues[2]) to false) } +
                pluralsPattern.findAll(text).associate { match ->
                    match.groupValues[1] to (itemPattern.findAll(match.groupValues[2]).flatMap { argsOf(it.groupValues[1]) }.toSet() to true)
                }
        }

        val defaults = entriesOf(File(resourcesDir, "values/strings.xml"))
        val locales = resourcesDir.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .mapNotNull { dir -> File(dir, "strings.xml").takeIf { it.isFile }?.let { dir.name.removePrefix("values-") to it } }
            .sortedBy { it.first }

        val errors = mutableListOf<String>()
        locales.forEach { (locale, file) ->
            val translated = entriesOf(file)
            val missing = (defaults.keys - translated.keys).sorted()
            val extra = (translated.keys - defaults.keys).sorted()
            if (locale in complete) {
                if (missing.isNotEmpty()) errors += "Chybí ve values-$locale: " + missing.joinToString()
                if (extra.isNotEmpty()) errors += "Chybí ve values (výchozí), ale jsou ve values-$locale: " + extra.joinToString()
            } else {
                if (missing.isNotEmpty()) logger.warn("values-$locale: nepřeloženo ${missing.size} z ${defaults.size} (spadne na angličtinu).")
                if (extra.isNotEmpty()) logger.warn("values-$locale: klíče, které výchozí jazyk už nemá: " + extra.joinToString())
            }
            translated.forEach { (key, entry) ->
                val (args, plural) = entry
                val baseArgs = defaults[key]?.first ?: return@forEach
                val foreign = args - baseArgs
                val lost = if (plural) emptySet() else baseArgs - args
                if (foreign.isNotEmpty() || lost.isNotEmpty()) {
                    errors += "values-$locale/$key: argumenty ${args.sorted()} místo ${baseArgs.sorted()}"
                }
            }
        }

        check(errors.isEmpty()) {
            buildString {
                appendLine("Jazykové mutace se rozešly.")
                errors.forEach { appendLine(it) }
            }
        }
    }
}

tasks.named("check") { dependsOn(verifyTranslations) }

/**
 * iOS nabídne jazyk v Nastavení jen podle složek `<kód>.lproj` v balíčku — a ty Gradle vyrobit
 * neumí, nesou vlastní texty iOS (`Localizable.strings`, překládají se zvlášť). Jazyk, který
 * [generateAppLanguages] pustil do výběru a iOS složku nemá, se proto jen OHLÁSÍ: na Androidu
 * jazyk funguje hned a build kvůli iOS padat nemá.
 */
val verifyIosLocalizations = tasks.register("verifyIosLocalizations") {
    group = "verification"
    description = "Ohlásí jazyk aplikace bez <kód>.lproj v iosApp a ShareExtension."
    dependsOn(generateAppLanguages)
    val languages = layout.buildDirectory.file("generated/appLanguages/languages.txt")
    inputs.file(languages)
    val targets = listOf("iosApp/iosApp", "iosApp/ShareExtension").map { rootProject.file(it) }

    doLast {
        languages.get().asFile.readLines().filter { it.isNotBlank() }.forEach { code ->
            targets.filter { !File(it, "$code.lproj").isDirectory }.forEach { target ->
                logger.warn("verifyIosLocalizations: $code chybí ${target.name}/$code.lproj — iOS ho v Nastavení nenabídne.")
            }
        }
    }
}

tasks.named("check") { dependsOn(verifyIosLocalizations) }

/**
 * Hlídá, že se do UI nevrátí natvrdo psaný text.
 *
 * Propadlo to dvakrát: první kontrola hledala jen texty s diakritikou, takže „Odebrat soubor"
 * ani „Video" nenašla. Proto pravidla dvě — slot v UI nesmí dostat literál vůbec, a český text
 * nesmí být nikde v kódu mimo komentáře, logy a vnitřní kontroly (`require`/`check`), protože
 * jazykem zdrojáku je od téhle chvíle angličtina v prostředcích.
 */
val verifyNoHardcodedText = tasks.register("verifyNoHardcodedText") {
    group = "verification"
    description = "Ověří, že texty pro uživatele jsou v prostředcích, ne v kódu."

    val sources = listOf(
        layout.projectDirectory.dir("src/commonMain/kotlin"),
        layout.projectDirectory.dir("src/androidMain/kotlin"),
        layout.projectDirectory.dir("src/iosMain/kotlin"),
        layout.projectDirectory.dir("src/jvmMain/kotlin"),
    )
    sources.forEach { inputs.dir(it) }

    // Konfigurační keš neunese odkaz na skriptový objekt uvnitř `doLast` — proto se cesty
    // vytáhnou jako obyčejné `File` už tady. Stejná past jako u `releaseKeystoreFile`.
    val roots = sources.map { it.asFile }
    val projectDir = layout.projectDirectory.asFile

    doLast {
        // Sloty, do kterých se kreslí text pro uživatele. `label =` tady schválně není:
        // stejný název nese i popisek animace (`AnimatedContent(label = "delivery")`).
        val uiSlot = Regex("""(?:\bText\(|\btext = |contentDescription = |placeholder = |onClickLabel = |headlineContent|supportingContent)\s*\{?\s*(?:Text\()?"""")
        val czech = Regex("""\"[^\"]*[áčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][^\"]*\"""")
        // Řádky, kde text pro uživatele není: logy a vnitřní kontroly.
        val internal = Regex("""logger\.[dewiv]\b|Logger\.withTag|\brequire|\bcheck|\berror\(|Exception\(""")
        // Prompt pro jazykový model taky text pro uživatele není — nejde na obrazovku, ale do
        // modelu v telefonu. Česká slova v něm bývají POTŘEBA: bez výslovného „tykání/vykání"
        // model kamarádovi vykal (viz „Návrhy odpovědí" v docs/poznatky/ai-v-zarizeni.md). Je to tedy stejná kategorie
        // jako logy a vnitřní kontroly výš, a řeší se stejně — výjimkou, ne escape sekvencemi:
        // ty jsou správně u DAT (tabulka diakritiky v `SearchHighlight.kt`), ale prompt je próza,
        // která se ladí čtením, a „tyk\u00e1n\u00ed" uprostřed anglické věty se číst nedá.
        // Výjimka je proto UŽŠÍ než ty stávající: vypíná JEN pravidlo o diakritice, a jen
        // v souborech, které nenesou nic jiného než prompty. Slot v UI se hlídá i v nich
        // (dvě pravidla jsou tu schválně) a prompt přesunutý jinam kontrolu rozsvítí.
        val promptFile = Regex("""core/ai/[A-Za-z]+Prompt\.kt$""")

        val offenders = mutableListOf<String>()
        roots.flatMap { it.walkTopDown().filter { f -> f.extension == "kt" } }.forEach { file ->
            val isPrompt = promptFile.containsMatchIn(file.invariantSeparatorsPath)
            val lines = file.readLines()
            var inBlockComment = false
            lines.forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("/*")) inBlockComment = true
                if (inBlockComment) {
                    if (line.contains("*/")) inBlockComment = false
                    return@forEachIndexed
                }
                if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed

                val context = lines.subList(maxOf(0, index - 3), index + 1).joinToString("\n")
                if (internal.containsMatchIn(context)) return@forEachIndexed

                // Prázdný literál žádný text nenese (`MessageBody(text = "")`).
                val meaningful = line.replace("\"\"", "")
                if (uiSlot.containsMatchIn(meaningful) || (!isPrompt && czech.containsMatchIn(meaningful))) {
                    offenders += "${file.relativeTo(projectDir)}:${index + 1}: $line"
                }
            }
        }

        check(offenders.isEmpty()) {
            "Text pro uživatele patří do composeResources, ne do kódu:\n" + offenders.joinToString("\n")
        }
    }
}

tasks.named("check") { dependsOn(verifyNoHardcodedText) }

/**
 * Hlídá, že suspend čtení textu z prostředků jde jen přes `readString` / `readPluralString`
 * (`core/ui/ResourceStrings.kt`), které čtou mimo hlavní vlákno.
 *
 * Přímé `getString` z korutiny na `Dispatchers.Main` umí s `stringResource` v kompozici
 * zamknout hlavní vlákno natrvalo — zdůvodnění je v KDoc `readString`. Volání bylo přes
 * šedesát a každé nové by past otevřelo znovu, aniž by si toho kdo všiml: deadlock je souběh,
 * v testech ani při zkoušení se neukáže.
 */
val verifyResourceReadsOffMain = tasks.register("verifyResourceReadsOffMain") {
    group = "verification"
    description = "Ověří, že se text z prostředků mimo kompozici čte přes readString."

    val sources = listOf(
        layout.projectDirectory.dir("src/commonMain/kotlin"),
        layout.projectDirectory.dir("src/androidMain/kotlin"),
        layout.projectDirectory.dir("src/iosMain/kotlin"),
        layout.projectDirectory.dir("src/jvmMain/kotlin"),
    )
    sources.forEach { inputs.dir(it) }
    val roots = sources.map { it.asFile }
    val projectDir = layout.projectDirectory.asFile

    doLast {
        val directRead = Regex("""^import org\.jetbrains\.compose\.resources\.(?:getString|getPluralString|getStringArray|\*)\b""")
        val offenders = roots
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" && f.name != "ResourceStrings.kt" } }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.relativeTo(projectDir)}:${index + 1}: $line".takeIf { directRead.containsMatchIn(line.trim()) }
                }
            }
        check(offenders.isEmpty()) {
            "Text z prostředků se mimo kompozici čte přes readString / readPluralString (core/ui/ResourceStrings.kt):\n" +
                offenders.joinToString("\n")
        }
    }
}

tasks.named("check") { dependsOn(verifyResourceReadsOffMain) }

/**
 * Hlídá, že bubliny jdou přes `WithTooltip` / `RootTooltipBox` (`core/ui/WithTooltip.kt`), které je
 * kreslí z kořene kompozice, a nikde jinde se nepoužije holý M3 `TooltipBox`.
 *
 * Holý `TooltipBox` na desktopu shodí vykreslování (`RootNodeOwner is already disposed`), když jeho
 * kotva s viditelnou bublinou zmizí z kompozice během měření — větev `BoxWithConstraints` podle
 * šířky, slot `Scaffold`u, položka lazy seznamu. Při zkoušení se to neukáže: musí se sejít
 * viditelná bublina se změnou rozložení, třeba tahem za okraj okna.
 */
val verifyTooltipsThroughRoot = tasks.register("verifyTooltipsThroughRoot") {
    group = "verification"
    description = "Ověří, že se bubliny kreslí přes WithTooltip / RootTooltipBox, ne holým TooltipBox."

    val sources = listOf(
        layout.projectDirectory.dir("src/commonMain/kotlin"),
        layout.projectDirectory.dir("src/androidMain/kotlin"),
        layout.projectDirectory.dir("src/iosMain/kotlin"),
        layout.projectDirectory.dir("src/jvmMain/kotlin"),
    )
    sources.forEach { inputs.dir(it) }
    val roots = sources.map { it.asFile }
    val projectDir = layout.projectDirectory.asFile

    doLast {
        val rawTooltip = Regex("""^import androidx\.compose\.material3\.(?:TooltipBox|\*)\b|\bandroidx\.compose\.material3\.TooltipBox\(""")
        val offenders = roots
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" && f.name != "WithTooltip.kt" } }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.relativeTo(projectDir)}:${index + 1}: $line".takeIf { rawTooltip.containsMatchIn(line.trim()) }
                }
            }
        check(offenders.isEmpty()) {
            "Bublina se kreslí přes WithTooltip nebo RootTooltipBox (core/ui/WithTooltip.kt), ne holým TooltipBox:\n" +
                offenders.joinToString("\n")
        }
    }
}

tasks.named("check") { dependsOn(verifyTooltipsThroughRoot) }

// api_id/api_hash z my.telegram.org. Čtou se z local.properties (gitignorovaný), aby se pár
// nikdy nedostal do repozitáře. Bez nich se projekt přeloží, ale přihlášení skončí chybou
// od serveru — hlásí se to srozumitelně místo pádu (viz sendTdlibParameters v TdlTelegramSession).
//
// Na CI žádné local.properties není, takže se pár bere z ENV (`TELEGRAM_API_ID` / `TELEGRAM_API_HASH`).
buildConfig {
    packageName = "eu.tintera.plume"
    className = "BuildConfiguration"
    buildConfigField("Int", "TELEGRAM_API_ID", configValue("telegramApiId", "TELEGRAM_API_ID")?.toIntOrNull() ?: 0)
    buildConfigField("String", "TELEGRAM_API_HASH", "\"${configValue("telegramApiHash", "TELEGRAM_API_HASH") ?: ""}\"")
}

/**
 * Hodnota konfigurace mimo git. Priorita: gradle property (`-Pklic=…`) > ENV (CI) > `local.properties`.
 *
 * ENV se čte přes [providers], ne přes `System.getenv` — jinak by se jeho hodnota nedostala do
 * otisku konfigurační keše a build s configuration-cache by po změně secretu použil starou.
 */
fun configValue(gradleKey: String, envKey: String): String? =
    (providers.gradleProperty(gradleKey).orNull
        ?: providers.environmentVariable(envKey).orNull
        ?: localProperty(gradleKey))
        ?.takeIf { it.isNotBlank() }

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    return Properties()
        .apply { file.inputStream().use { load(it) } }
        .getProperty(name)
        ?.takeIf { it.isNotBlank() }
}
