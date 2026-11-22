plugins {
    // Deklarované bez `apply`, aby se plugin classloader nenačítal v každém subprojektu znovu.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidDynamicFeature) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.buildConfig) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.crashlytics) apply false
}

/**
 * Přegeneruje seznamy licencí knihoven pro „O aplikaci" (`files/licenses/` v :client:resources,
 * konvenční plugin `plume.licenses`). Spouští se ručně po změně závislostí a výsledek se commituje —
 * texty licencí se stahují z SPDX, release ze sítě číst nemá.
 */
tasks.register("exportLicenses") {
    group = "plume"
    description = "Přegeneruje licence knihoven pro O aplikaci (Android, desktop, iOS)."
    dependsOn(
        ":client:androidApp:exportLibraryDefinitions",
        ":client:desktopApp:exportLibraryDefinitions",
        ":client:shared:exportLibraryDefinitions",
    )
}
