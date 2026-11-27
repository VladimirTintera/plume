import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.buildConfig)
    // Kontroly zdrojáků na `check` (build-logic/).
    id("plume.source-checks")
}

/**
 * Vrstva kolem TDLib: `core/telegram` a to, co potřebuje (rozhraní hovorů, sledování sítě
 * a popředí, úložiště klíče databáze, formátování textů do `UiText`).
 *
 * - **Jediný modul, který vidí TDLib wrapper** (`tdl-coroutines` jako `implementation`). Import
 *   `dev.g000sha256.tdl.*` jinde se nepřeloží — hranice z CLAUDE.md už nedrží jen dohoda.
 * - **Žádné Compose UI**, jen runtime a prostředky (`UiText` nese odkaz do `Res`).
 * - **Balíčky zůstaly** (`eu.tintera.plume.core.*`), takže `core.ui`, `core.calls` a další jsou
 *   rozdělené mezi tenhle modul a `:client:shared`. Kotlinu to nevadí; co odsud chce zbytek
 *   aplikace, musí být veřejné — `internal` platí jen uvnitř modulu.
 * - Co sem patří, je změřené grafem závislostí od `core/telegram` (roadmapa, „Rozdělení
 *   `:client:shared`"). Definice pro Koin nese `coreModule()`; vstupní bod ho jen zahrne.
 */
kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.coroutines.FlowPreview")
        // kotlinx-datetime 0.8 staví na kotlin.time.Instant.
        optIn.add("kotlin.time.ExperimentalTime")
    }

    android {
        namespace = "eu.tintera.plume.core"
        // Minor verze SDK: Compose 1.13 chce kompilovat proti 37.1+ (kontrola AAR metadat).
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Bez tohohle se commonTest na JVM vůbec nespustí.
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            // Texty a `Res` (UiText, chybové hlášky, servisní zprávy).
            api(projects.client.resources)
            implementation(libs.compose.runtime)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
            implementation(libs.okio)
            // `coreModule()`: definice vrstvy kolem TDLib pro Koin (client/core/…/di/CoreModule.kt).
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)

            // TDLib (Android, iOS, desktop). JEN tady — viz „Hranice kolem TDLib" v CLAUDE.md.
            implementation(libs.tdl.coroutines)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            // `androidContext()` v `corePlatformModule()`.
            implementation(libs.koin.android)
            // Hlásič pádů (core/logging) pro telefon i hodinky. Jediné místo v projektu, kde smí
            // být Crashlytics na classpath. Bez `google-services.json` se knihovna jen neinicializuje.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)
            // Zdroj pro ExoPlayer nad rozestavěným souborem TDLib (`core/video/PartialFileDataSource`):
            // telefon i televize. Jen datasource, přehrávač si přidá, kdo přehrává.
            api(libs.media3.datasource)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Náhražky typů z core (Fake…, Unimplemented…, testMessage).
            implementation(projects.client.coreTesting)
        }
    }
}

// Prostředky jsou v :client:resources; vlastní `Res` by se srazila s tou odtamtud.
compose.resources {
    generateResClass = never
}

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
