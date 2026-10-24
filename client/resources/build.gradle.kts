import eu.tintera.plume.gradle.GenerateAppLanguages

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Seznam jazyků a kontrola mutací nad composeResources tohohle modulu (build-logic/).
    id("plume.translations")
}

/**
 * Prostředky aplikace — texty s překlady, ikony, fonty — a třída `Res`, ve vlastním modulu.
 *
 * - **Všechny prostředky v jednom modulu**, ne texty v `core` a ikony ve `shared`: `Res` se
 *   generuje pro každý modul zvlášť a přes jednu `Res` bere texty i ikony přes dvě stě souborů.
 *   Dvě třídy `Res` by ve stejném balíčku nešly a v různých by chtěly aliasy importů.
 * - **Balíček `Res` zůstal `eu.tintera.plume.resources`**, takže se přesunem nezměnil jediný
 *   import. Ostatní moduly `Res` negenerují (`generateResClass = never`), jinak by vznikla druhá
 *   třída téhož jména.
 * - Kód tu není, jen vygenerovaný seznam jazyků (`GENERATED_APP_LANGUAGES`).
 */
kotlin {
    jvmToolchain(17)

    android {
        namespace = "eu.tintera.plume.resources"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            // KMP moduly mají Android resources defaultně vypnuté; bez tohohle se composeResources
            // nezabalí a každé čtení stringu spadne až za běhu na „No instrumentation registered!".
            enable = true
        }
    }

    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain {
            // Jazyky aplikace ze složek values-* (APP_LANGUAGES).
            kotlin.srcDir(tasks.named<GenerateAppLanguages>("generateAppLanguages").flatMap { it.kotlinDirectory })
        }
        commonMain.dependencies {
            // `api`: typy `StringResource`, `DrawableResource`… jsou v API vygenerované `Res`.
            api(libs.compose.resources)
            // Kompilátor Compose bez runtime na classpath odmítne překládat; knihovna prostředků
            // ho do API nevystavuje.
            implementation(libs.compose.runtime)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "eu.tintera.plume.resources"
    generateResClass = always
}

/**
 * Jazyky aplikace (`plume.translations`, build-logic/Translations.kt). `completeLocales` zrcadlí
 * `OWN_LOCALES` v `.github/scripts/release_notes.py`.
 */
plumeTranslations {
    // Jazyky, které píšeme sami: musí mít všechny klíče a do výběru jdou vždy.
    completeLocales = setOf("cs")
    // Podíl přeložených klíčů, od kterého jde komunitní jazyk do výběru.
    minLanguageCoverage = 0.8
}
