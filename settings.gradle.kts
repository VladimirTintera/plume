rootProject.name = "plume"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Konvenční pluginy (kontroly zdrojáků, jazyky, hook TdlibJsonRepair), viz build-logic/.
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// AGP 9 nepovolí `com.android.application` + KMP plugin v jednom modulu, proto je klient rozdělený
// na jednu sdílenou KMP knihovnu a tenké vstupní body. Stejný princip jako v mlogu.
include(":client:resources")
include(":client:core")
include(":client:core-testing")
include(":client:shared")
include(":client:androidApp")
include(":client:wearApp")
include(":client:desktopApp")
// Součásti z Google Play s runtime modelu v zařízení (LiteRT-LM, llama.cpp); kód runtime je
// v knihovně ai-engine níž, tyhle moduly ho jen balí (viz docs/poznatky/ai-v-zarizeni.md).
include(":client:litertFeature")
include(":client:llamacppFeature")

// Model v zařízení: knihovna ai-engine (github.com/VladimirTintera/ai-engine) jako git submodul.
// Moduly mají souřadnice `eu.tintera.ai:<modul>` a Gradle je za ně dosadí sám. Změna v knihovně =
// PR v ai-engine, pak posun submodulu tady (docs/poznatky/ai-v-zarizeni.md, „Knihovna ai-engine").
val aiEngine = file("external/ai-engine")
require(aiEngine.resolve("settings.gradle.kts").exists()) {
    "Chybí submodul ai-engine, spusť: git submodule update --init --recursive"
}
// Android SDK pro included build: AGP i překlad llama.cpp pro desktop čtou `local.properties`
// z kořene ai-engine (nebo ANDROID_HOME). Android Studio ho zakládá jen pro Plume, tak se `sdk.dir`
// přenese odsud; soubor je v ai-engine gitignorovaný.
val aiEngineLocalProperties = aiEngine.resolve("local.properties")
if (!aiEngineLocalProperties.exists()) {
    file("local.properties").takeIf { it.exists() }?.readLines()
        ?.firstOrNull { it.trimStart().startsWith("sdk.dir=") }
        ?.let { aiEngineLocalProperties.writeText(it.trim() + "\n") }
}
includeBuild(aiEngine)
