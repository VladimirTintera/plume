import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

    // `implementation` závislosti ze :client:shared nejsou tranzitivní — co používá vstupní bod,
    // musí být deklarované i tady.
    implementation(libs.kermit)

    // Knihovna je tu vždycky; bez `google-services.json` se jen neinicializuje a push se nepoužije.
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}

android {
    namespace = "eu.tintera.plume"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "eu.tintera.plume"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 12
        versionName = "0.2.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Podpisový klíč se čte z local.properties; keystore je gitignorovaný.
    // Bez něj se release build přeloží, ale zůstane nepodepsaný — a to Android neinstaluje.
    val releaseStore = localProperty("releaseStoreFile")?.let { rootProject.file(it) }

    signingConfigs {
        if (releaseStore?.exists() == true) {
            create("release") {
                storeFile = releaseStore
                storePassword = localProperty("releaseStorePassword")
                keyAlias = localProperty("releaseKeyAlias")
                keyPassword = localProperty("releaseKeyPassword")

                // v3 umožní později rotovat klíč. Musí být zapnuté OD ZAČÁTKU — rotace se opírá
                // o podpisovou linii v APK a dodatečně ji doplnit nejde.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 zatím vypnutý: TDLib jde přes JNI a bez ověřených keep rules hrozí, že se
            // ořízne něco, co se volá jen z nativní strany. Zapnout až s otestovanými pravidly.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Rozdělení podle ABI. Univerzální APK má kvůli TDLib přes 100 MB, protože nese nativní
    // knihovnu pro všechny čtyři architektury; samostatné arm64 APK je zhruba třetinové.
    splits {
        abi {
            isEnable = true
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

// Push notifikace přes FCM.
//
// Plugin `google-services` se aplikuje POUZE když existuje `google-services.json`. Bez téhle
// podmínky by se repozitář po klonu vůbec nepřeložil — plugin chybějící soubor bere jako chybu.
// Aplikace tedy funguje i bez Firebase, jen bez pushe: notifikace chodí, dokud proces běží.
//
// Postup nastavení je v README, sekce „Push notifikace".
fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    return Properties()
        .apply { file.inputStream().use { load(it) } }
        .getProperty(name)
        ?.takeIf { it.isNotBlank() }
}

val googleServicesConfig = file("google-services.json")
if (googleServicesConfig.exists()) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("google-services.json chybí — build bez push notifikací.")
}
