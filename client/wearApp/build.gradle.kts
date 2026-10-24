plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Hook TdlibJsonRepair za JsonClient.receive uvnitř wrapperu (build-logic, Tdlib.kt). Povinný
    // pro každý vstupní bod, který přibalí TDLib — jinak ho dárek bez pozadí shodí.
    id("plume.tdlib-android")
}

/**
 * Plume pro Wear OS: samostatná aplikace na hodinkách s vlastním sezením TDLib (roadmapa,
 * „Wear OS"). Stojí jen na `:client:core` (a přes něj `:client:resources`), UI telefonu ani jeho
 * knihovny sem nejdou. UI je Compose for Wear OS, ne M3 z `shared` — komponenty pro kulatý
 * displej jsou jiné.
 */
kotlin {
    jvmToolchain(17)
}

android {
    namespace = "eu.tintera.plume.wear"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Stejné jako telefon: Wear OS aplikace jde v Play do téhož záznamu jen se stejným id.
        applicationId = "eu.tintera.plume"
        // Wear OS 3.
        minSdk = 30
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // Hodinky: arm64 a hodně z nich 32bitově (Wrapper TDLib balí oboje). x86 nepotřebují
            // a TDLib je na ABI přes 15 MB.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    // Zadání hesla (dvoufázové ověření) systémovou klávesnicí hodinek — Wear Compose pole nemá.
    implementation(libs.wear.input)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kermit)
    // QR kód pro přihlášení (jako telefon).
    implementation(libs.zxing.core)
}
