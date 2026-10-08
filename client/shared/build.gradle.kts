import java.util.Properties

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
                val process = ProcessBuilder("python3", script.absolutePath, "bundle", target.absolutePath)
                    .directory(repoDir)
                    .redirectErrorStream(true)
                    .start()
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

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Platformy bez Material You (dynamic color ze systému). Zatím jen iOS, ale až přibude
        // desktop (tdl-coroutines publikuje i JVM targety), spadne sem taky — proto vlastní
        // source set a ne prosté iosMain.
        val nonDynamicThemeMain by creating {
            dependsOn(commonMain.get())
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
            implementation(libs.androidx.core.ktx)
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

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
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
val verifyTranslations = tasks.register("verifyTranslations") {
    group = "verification"
    description = "Ověří, že values/ a values-cs/ mají stejné klíče."

    val defaults = layout.projectDirectory.file("src/commonMain/composeResources/values/strings.xml")
    val czech = layout.projectDirectory.file("src/commonMain/composeResources/values-cs/strings.xml")
    inputs.file(defaults)
    inputs.file(czech)

    doLast {
        val pattern = Regex("<(?:string|plurals) name=\"([^\"]+)\"")
        fun keysOf(file: File): Set<String> =
            pattern.findAll(file.readText()).map { it.groupValues[1] }.toSet()

        val inDefault = keysOf(defaults.asFile)
        val inCzech = keysOf(czech.asFile)
        val missingCzech = (inDefault - inCzech).sorted()
        val missingDefault = (inCzech - inDefault).sorted()

        check(missingCzech.isEmpty() && missingDefault.isEmpty()) {
            buildString {
                appendLine("Jazykové mutace se rozešly.")
                if (missingCzech.isNotEmpty()) appendLine("Chybí ve values-cs: " + missingCzech.joinToString())
                if (missingDefault.isNotEmpty()) appendLine("Chybí ve values (výchozí): " + missingDefault.joinToString())
            }
        }
    }
}

tasks.named("check") { dependsOn(verifyTranslations) }

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
