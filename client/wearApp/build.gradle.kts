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

// Hodinky jdou do téhož záznamu v Play jako telefon a z téhož tagu: stejné versionCode by
// druhý upload odmítlo (viz AndroidReleaseExtension).
plumeRelease {
    versionCodeOffset = 100_000_000
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

        // Sběr hlášení pádů jen v release (níž), jako u telefonu: pády z vývoje do konzole nepatří.
        // Crashlytics příznak čte při inicializaci procesu, proto manifest, ne volání za běhu.
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"

        ndk {
            // Hodinky: arm64 a hodně z nich 32bitově (Wrapper TDLib balí oboje). x86 nepotřebují
            // a TDLib je na ABI přes 15 MB.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            // R8 jako u telefonu: keep pravidla JNI hranice TDLib si nese wrapper sám. Chybějící
            // pravidlo se projeví až za běhu — release se proto proklikává, ne jen překládá
            // (docs/poznatky/wear-os.md, „Vydání").
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

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    // Navigace s gestem tahu zpět (SwipeDismissableNavHost), standard na hodinkách.
    implementation(libs.wear.compose.navigation)
    // Zadání hesla (dvoufázové ověření) systémovou klávesnicí hodinek — Wear Compose pole nemá.
    implementation(libs.wear.input)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kermit)
    implementation(libs.androidx.core.ktx)

    // Vlastní push na hodinkách (FCM) a vypnutí zrcadlení notifikací telefonu, když jsou
    // hodinky přihlášené (BridgingManager).
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.wear.phone.interactions)
    // QR kód pro přihlášení (jako telefon).
    implementation(libs.zxing.core)

    // Dlaždice a komplikace s počtem nepřečtených: dlaždice se skládá z ProtoLayout (M3 pro
    // dlaždice), ne z Compose; TileService vrací ListenableFuture, korutinu do ní převede
    // concurrent-futures.
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.complications.data.source)
    implementation(libs.androidx.concurrent.futures)
}

// Push přes FCM: plugin `google-services` jen s `google-services.json`, jako u telefonu (bez něj
// se aplikace přeloží, jen bez pushe). Soubor je KOPIE z `androidApp`: plugin ho čte jen
// z adresáře modulu a hodinky mají stejné `applicationId`, takže platí tentýž. Změní-li se
// projekt Firebase, mění se oba.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    // Hlášení pádů na témže souboru. AŽ ZA `google-services` (čte jeho konfiguraci); k buildu
    // přidá build id a nahrání R8 mapping souboru — bez něj je stack trace z minifikovaného
    // releasu nečitelný. Kermit → Crashlytics přemosťuje `configureLogging()` z `core`.
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("wearApp: google-services.json chybí — hodinky bez pushe a bez hlášení pádů.")
}
