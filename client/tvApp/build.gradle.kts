plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Hook TdlibJsonRepair za JsonClient.receive uvnitř wrapperu (build-logic, Tdlib.kt). Povinný
    // pro každý vstupní bod, který přibalí TDLib — jinak ho dárek bez pozadí shodí.
    id("plume.tdlib-android")
    // res/xml/locales_config.xml ze seznamu jazyků (build-logic, LocaleConfig.kt), jako telefon.
    id("plume.locale-config")
    // Verze z tagu, podpis týmž upload klíčem jako telefon a verifyReleaseConfiguration
    // (build-logic, AndroidRelease.kt).
    id("plume.android-release")
}

// Televize jde do téhož záznamu v Play jako telefon a hodinky a z téhož tagu: stejné versionCode
// by druhý upload odmítlo (viz AndroidReleaseExtension). Hodinky mají 100 000 000.
plumeRelease {
    versionCodeOffset = 200_000_000
}

/**
 * Plume pro Android TV: samostatná aplikace s vlastním sezením TDLib (docs/poznatky/android-tv.md).
 * Stojí jen na `:client:core` (a přes něj `:client:resources`), jako hodinky; UI telefonu ani jeho
 * knihovny sem nejdou. UI je Compose for TV (`androidx.tv:tv-material`) — ovládá se fokusem
 * a šipkami dálkového ovladače, komponenty telefonu stojí na dotyku.
 */
kotlin {
    jvmToolchain(17)
}

android {
    namespace = "eu.tintera.plume.tv"
    // Minor verze SDK: Compose 1.13 chce kompilovat proti 37.1+ (kontrola AAR metadat).
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        // Stejné jako telefon: aplikace pro TV jde v Play do téhož záznamu jen se stejným id.
        applicationId = "eu.tintera.plume"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        // Sběr hlášení pádů jen v release (níž), jako u telefonu.
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"

        ndk {
            // Velká část televizí a přehrávačů jede 32bitový systém i na 64bitovém procesoru
            // (málo paměti), bez armeabi-v7a by se na ně aplikace nenainstalovala. x86_64 kvůli
            // emulátoru Android TV na Intelu; TDLib má na ABI přes 15 MB.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            // R8 jako u telefonu: keep pravidla JNI hranice TDLib si nese wrapper sám. Chybějící
            // pravidlo se projeví až za běhu — release se proto proklikává, ne jen překládá.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.client.core)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.tv.material)
    // Jen `LoadingIndicator` (neurčité čekání, M3 Expressive): tv-material indikátor nemá.
    implementation(libs.compose.material3)
    // Paleta z barvy Plume jako na telefonu (TvColors): materialkolor dopočítá schéma M3
    // a jeho role se přepíšou do schématu tv-material.
    implementation(libs.materialkolor)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kermit)
    implementation(libs.androidx.core.ktx)

    // Video přes celou obrazovku: přehrávač, jeho povrch (`PlayerView` bez vlastního ovládání)
    // a `MediaSession` pro tlačítka médií na ovladači. Zdroj nad souborem TDLib je v `core`.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // QR kód pro přihlášení (jako telefon a hodinky).
    implementation(libs.zxing.core)
}

// Hlášení pádů přes Crashlytics: plugin `google-services` jen s `google-services.json`, jako
// u telefonu a hodinek (bez něj se aplikace přeloží, jen bez hlášení pádů). Soubor je kopie
// z `androidApp`, platí tentýž (stejné `applicationId`).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("tvApp: google-services.json chybí — televize bez hlášení pádů.")
}
