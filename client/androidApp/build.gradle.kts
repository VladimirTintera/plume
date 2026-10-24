import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Hook TdlibJsonRepair za JsonClient.receive uvnitř wrapperu (build-logic, Tdlib.kt).
    id("plume.tdlib-android")
}

// Kotlin/Android je v AGP 9 vestavěný — plugin org.jetbrains.kotlin.android se neaplikuje.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.client.shared)

    implementation(libs.androidx.activity.compose)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    // Hlídá uniklé activity, ViewModely a view roots; v release buildu není.
    debugImplementation(libs.leakcanary)
    // Composition tracing pro Perfetto (docs/poznatky/vykon.md, „Výkon a paměť"); zapíná se broadcastem.
    debugImplementation(libs.compose.runtime.tracing)
    debugImplementation(libs.tracing.perfetto.binary)

    // `implementation` závislosti ze :client:shared nejsou tranzitivní — co používá vstupní bod,
    // musí být deklarované i tady.
    implementation(libs.kermit)

    // Knihovna je tu vždycky; bez `google-services.json` se jen neinicializuje a push se nepoužije.
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.work.runtime)
}

// Cesta ke keystore. Na CI je to absolutní cesta do RUNNER_TEMP, lokálně relativní cesta
// od kořene projektu (`releaseStoreFile=plume-release.jks` v local.properties).
//
// Top-level `val` se v .kts inicializují v pořadí deklarace, takže tohle musí stát PŘED
// blokem `android { }`, který hodnotu čte — deklarace níž by byla `null`.
val releaseKeystoreFile: File? =
    configValue("releaseStoreFile", "ANDROID_KEYSTORE_PATH")
        ?.let { path -> File(path).takeIf { it.isAbsolute } ?: rootProject.file(path) }

android {
    namespace = "eu.tintera.plume"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "eu.tintera.plume"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Release z CI je nastavuje z git tagu (`-PandroidVersionCode` / `-PandroidVersionName`),
        // viz .github/workflows/android-release.yml. Hodnoty tady jsou jen záloha pro lokální build.
        //
        // `versionCode` je JEDNOSMĚRNÝ: Play si pamatuje každé nahrané číslo (i z internal tracku)
        // a stejné podruhé nevezme. Proto ho bump workflow zvedá při KAŽDÉM releasu, i když se
        // `versionName` nemění.
        versionCode = (configValue("androidVersionCode", "ANDROID_VERSION_CODE") ?: "12").toInt()
        versionName = configValue("androidVersionName", "ANDROID_VERSION_NAME") ?: "0.2.0"

        // Sběr hlášení pádů. Výchozí hodnota je pro VŠECHNY build typy vypnuto, zapíná ho jen
        // release níž — pády při vývoji (a pády z běžícího debuggeru) do konzole nepatří, jinak
        // by v ní skutečná hlášení od testerů zanikla.
        //
        // Přepíná se manifestem, ne za běhu: Crashlytics ten příznak čte při inicializaci
        // procesu, tedy dřív, než se v aplikaci dá cokoli spustit.
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Podpisový klíč se čte z local.properties, na CI z ENV; keystore je gitignorovaný.
    // Bez něj se release build přeloží, ale zůstane nepodepsaný — a to Android neinstaluje
    // a Play nevezme. `verifyReleaseConfiguration` níž takový build shodí dřív, než doběhne.
    val releaseStore = releaseKeystoreFile

    signingConfigs {
        if (releaseStore?.exists() == true) {
            create("release") {
                storeFile = releaseStore
                storePassword = configValue("releaseStorePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = configValue("releaseKeyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = configValue("releaseKeyPassword", "ANDROID_KEY_PASSWORD")

                // v3 umožní později rotovat klíč. Musí být zapnuté OD ZAČÁTKU — rotace se opírá
                // o podpisovou linii v APK a dodatečně ji doplnit nejde.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 — zkracování a obfuskace. Keep pravidla si knihovny nesou samy; `tdl-coroutines`
            // dodává v AAR `proguard.txt`, který drží JNI hranici (`org.drinkless.tdlib.JsonClient`).
            // Co ne, je v proguard-rules.pro.
            //
            // Pozor: chybějící keep pravidlo se neprojeví při buildu, ale AŽ ZA BĚHU. Po zásahu
            // do závislostí nebo reflexe je potřeba release build reálně proklikat.
            //
            // `isShrinkResources` vyhodí prostředky Androidu (`res/`), na které po R8 nic
            // neukazuje. Zapínal se zvlášť od minifikace, aby bylo poznat, co případný pád
            // způsobilo. **composeResources se ho netýkají** — leží v `assets/`, ne v `res/`.
            // Podle jména (`getIdentifier`) náš kód na prostředky nesahá; Firebase a Crashlytics
            // ano, ale shrinker v bezpečném režimu takové prostředky drží. Viz docs/poznatky/release.md, „R8".
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")

            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
        }
    }

    // Rozdělení podle ABI. Univerzální APK má kvůli TDLib přes 100 MB, protože nese nativní
    // knihovnu pro všechny čtyři architektury; samostatné arm64 APK je zhruba třetinové.
    //
    // **Jen pro APK, u bundlu vypnuté.** S `isShrinkResources` vyrobí AGP zmenšené prostředky
    // pro každou ABI variantu zvlášť a `bundleRelease` pak spadne na „Multiple shrunk-resources
    // files found" (issuetracker 402800800). Bundlu splity stejně nic nedávají — APK pro
    // konkrétní zařízení si z AAB skládá Play sám. Požadované úlohy jsou součástí klíče
    // konfigurační keše, takže se `assembleRelease` a `bundleRelease` nepletou.
    val buildsBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !buildsBundle
            reset()
            include("arm64-v8a", "armeabi-v7a")
            // Univerzální varianta zůstává jako záloha pro případ, že by ABI nesedělo.
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Hodnota konfigurace mimo git. Priorita: gradle property (`-Pklic=…`) > ENV (CI) > `local.properties`.
 *
 * ENV se čte přes [providers], ne přes `System.getenv` — jinak by jeho hodnota nevstoupila do
 * otisku konfigurační keše a build by po změně secretu použil tu starou.
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

// Push notifikace přes FCM.
//
// Plugin `google-services` se aplikuje POUZE když existuje `google-services.json`. Bez téhle
// podmínky by se repozitář po klonu vůbec nepřeložil — plugin chybějící soubor bere jako chybu.
// Aplikace tedy funguje i bez Firebase, jen bez pushe: notifikace chodí, dokud proces běží.
//
// Postup nastavení je v README, sekce „Push notifikace".
val googleServicesConfig = file("google-services.json")
if (googleServicesConfig.exists()) {
    apply(plugin = "com.google.gms.google-services")

    // Crashlytics jede na tomtéž `google-services.json` jako push — bere si z něj id aplikace,
    // pod kterým se hlášení v konzoli spárují. Plugin se aplikuje AŽ ZA `google-services`,
    // protože si jeho konfiguraci čte.
    //
    // Sám o sobě nic neodesílá; k buildu přidá dvě věci, které se bez něj nedají doplnit
    // zpětně: build id zapečené do APK a nahrání R8 mapping souboru. Bez mapping souboru je
    // stack trace z release buildu jen seznam jednopísmenných tříd.
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("google-services.json chybí — build bez push notifikací a bez hlášení pádů.")
}

/**
 * Pojistka pro release build. Chybějící podpis nebo prázdné api_id se jinak poznají až u testera —
 * nepodepsaný AAB Play odmítne a build bez `TELEGRAM_API_ID` se sice přeloží, ale přihlášení
 * v něm skončí chybou od serveru.
 */
val verifyReleaseConfiguration = tasks.register("verifyReleaseConfiguration") {
    group = "verification"
    description = "Ověří, že release build má podpisový klíč a api_id/api_hash."
    val keystore = releaseKeystoreFile
    val apiId = configValue("telegramApiId", "TELEGRAM_API_ID")?.toIntOrNull() ?: 0
    val apiHash = configValue("telegramApiHash", "TELEGRAM_API_HASH").orEmpty()
    val passwords = listOf(
        "releaseStorePassword" to configValue("releaseStorePassword", "ANDROID_KEYSTORE_PASSWORD"),
        "releaseKeyAlias" to configValue("releaseKeyAlias", "ANDROID_KEY_ALIAS"),
        "releaseKeyPassword" to configValue("releaseKeyPassword", "ANDROID_KEY_PASSWORD"),
    )
    doLast {
        val store = keystore
        check(store != null) {
            "Chybí podpisový klíč pro release. Doplň do local.properties `releaseStoreFile` " +
                "(nebo na CI ENV ANDROID_KEYSTORE_PATH)."
        }
        check(store.exists()) {
            "Keystore '${store.absolutePath}' neexistuje."
        }
        val missing = passwords.filter { it.second == null }.map { it.first }
        check(missing.isEmpty()) {
            "Ke keystore chybí: ${missing.joinToString()}. Na CI jsou to secrets " +
                "ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD."
        }
        check(apiId != 0 && apiHash.isNotBlank()) {
            "Chybí telegramApiId/telegramApiHash z my.telegram.org. Bez nich se release build " +
                "přeloží, ale přihlášení skončí chybou od serveru. Na CI jsou to secrets " +
                "TELEGRAM_API_ID / TELEGRAM_API_HASH."
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseConfiguration)
}

/**
 * `res/xml/locales_config.xml` (systémový výběr jazyka aplikace, `LocaleManager`) ze seznamu, který
 * vyrobí `:client:shared:generateAppLanguages` ze složek `composeResources/values-*`. Ručně psaný
 * soubor se s nimi rozcházel: nový jazyk z Weblate by v aplikaci byl, ale vybrat by nešel.
 */
abstract class GenerateLocaleConfig : DefaultTask() {
    @get:InputFile
    abstract val languages: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val locales = languages.get().asFile.readLines().filter { it.isNotBlank() }
        val file = outputDirectory.get().file("xml/locales_config.xml").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("<!-- Vygenerováno úlohou generateLocaleConfig ze složek composeResources/values-*. Neupravovat. -->")
                appendLine("""<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""")
                locales.forEach { appendLine("""    <locale android:name="$it" />""") }
                appendLine("</locale-config>")
            },
        )
    }
}

val generateLocaleConfig = tasks.register<GenerateLocaleConfig>("generateLocaleConfig") {
    dependsOn(":client:shared:generateAppLanguages")
    languages.set(rootProject.layout.projectDirectory.file("client/shared/build/generated/appLanguages/languages.txt"))
    outputDirectory.set(layout.buildDirectory.dir("generated/localeConfig/res"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateLocaleConfig, GenerateLocaleConfig::outputDirectory)
    }
}
