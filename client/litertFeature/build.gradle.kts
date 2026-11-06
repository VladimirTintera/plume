plugins {
    alias(libs.plugins.androidDynamicFeature)
    // Prázdné knihovny pro armeabi-v7a a x86, které LiteRT-LM nemá (build-logic, AbiPlaceholders.kt).
    id("plume.feature-abi-placeholders")
}

/**
 * Součást z Google Play stahovaná až na požádání (Play Feature Delivery): runtime LiteRT-LM pro
 * Gemmu a Qwen v zařízení, tedy :client:ai-litert s nativní knihovnou `liblitertlm_jni.so`
 * (23 MB na arm64). V základu aplikace by ji měl každý, přitom model v zařízení si zapne málokdo.
 *
 * Kód tu žádný není. Základ aplikace na třídy součásti nevidí ani při překladu, implementaci si
 * po instalaci najde podle jména (`core/ai/PlayLiteRtRuntime`); keep pravidlo pro R8 je proto
 * v androidApp, R8 zpracovává základ i součást dohromady.
 *
 * **Jméno modulu je jméno součásti** (`litertFeature` v `PlayLiteRtRuntime.MODULE`) — přejmenování
 * by instalaci tiše rozbilo.
 */
android {
    namespace = "eu.tintera.plume.litert"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

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
    implementation(projects.client.aiLitert)
}
