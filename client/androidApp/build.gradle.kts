plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Hook TdlibJsonRepair za JsonClient.receive uvnitř wrapperu (build-logic, Tdlib.kt).
    id("plume.tdlib-android")
    // res/xml/locales_config.xml ze seznamu jazyků (build-logic, LocaleConfig.kt).
    id("plume.locale-config")
    // Verze z tagu, podpis a verifyReleaseConfiguration (build-logic, AndroidRelease.kt).
    id("plume.android-release")
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
    // Přihlášení hodinek z telefonu: hodinky pošlou odkaz z QR kódu (WatchLoginService).
    implementation(libs.play.services.wearable)
    // SplitCompat: součást z Google Play doinstalovaná za běhu (:client:litertFeature).
    implementation(libs.play.feature.delivery)
}

android {
    namespace = "eu.tintera.plume"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "eu.tintera.plume"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // versionCode / versionName z tagu a podpis release buildu dává plugin
        // `plume.android-release` (build-logic, AndroidRelease.kt), stejně jako hodinkám.

        // Sběr hlášení pádů. Výchozí hodnota je pro VŠECHNY build typy vypnuto, zapíná ho jen
        // release níž — pády při vývoji (a pády z běžícího debuggeru) do konzole nepatří, jinak
        // by v ní skutečná hlášení od testerů zanikla.
        //
        // Přepíná se manifestem, ne za běhu: Crashlytics ten příznak čte při inicializaci
        // procesu, tedy dřív, než se v aplikaci dá cokoli spustit.
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
    }

    // Součásti z Google Play stahované až na požádání (Play Feature Delivery). Runtime LiteRT-LM
    // pro model v zařízení — viz client/litertFeature a docs/poznatky/ai-v-zarizeni.md.
    dynamicFeatures += setOf(":client:litertFeature")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
        }
    }

    // **Bez rozdělení APK podle ABI** (`splits.abi`). Dřív bylo, protože univerzální APK má kvůli
    // TDLib přes 100 MB; jenže se součástí z Google Play (`dynamicFeatures`) AGP součást
    // nesestaví („Cannot find PROCESSED_RES output" — hledá výstup základu bez filtru ABI).
    // Nic se tím neztratilo: do Play jde AAB, ze kterého si APK pro zařízení skládá Play sám,
    // a na emulátor se instaluje univerzální APK.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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
