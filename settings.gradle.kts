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
// Model v zařízení: rozhraní runtime, implementace nad LiteRT-LM a na Androidu součást z Google
// Play, která ji přinese až na požádání (viz docs/poznatky/ai-v-zarizeni.md).
include(":client:ai-engine")
include(":client:ai-litert")
include(":client:litertFeature")
include(":client:ai-llamacpp")
include(":client:ai-llamacpp-android")
include(":client:ai-llamacpp-desktop")
include(":client:llamacppFeature")
