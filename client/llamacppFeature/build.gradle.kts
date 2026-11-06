plugins {
    alias(libs.plugins.androidDynamicFeature)
    // Prázdné knihovny pro armeabi-v7a a x86, pro které llama.cpp nestavíme (build-logic, AbiPlaceholders.kt).
    id("plume.feature-abi-placeholders")
}

/**
 * Součást z Google Play stahovaná až na požádání: runtime llama.cpp pro modely GGUF (EuroLLM),
 * tedy :client:ai-llamacpp s nativní knihovnou `libplume_llama.so`. Stejný princip jako
 * :client:litertFeature — viz tamní komentář a docs/poznatky/ai-v-zarizeni.md.
 *
 * **Jméno modulu je jméno součásti** (`llamacppFeature` v Koinu u `PlayLiteRtRuntime`) —
 * přejmenování by instalaci tiše rozbilo.
 */
android {
    namespace = "eu.tintera.plume.llamacpp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // Knihovny z :client:ai-llamacpp ořezává (strip) až modul, který je balí — a tím NDK. Bez
    // téhle verze AGP ořez přeskočí („Unable to strip") a do součásti jde 55 MB místo 7 MB.
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.client.androidApp)
    implementation(projects.client.aiLlamacpp)
}
