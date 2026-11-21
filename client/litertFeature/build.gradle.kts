plugins {
    alias(libs.plugins.androidDynamicFeature)
    // Prázdné knihovny pro armeabi-v7a a x86, které LiteRT-LM nemá (build-logic, AbiPlaceholders.kt).
    id("plume.feature-abi-placeholders")
}

/**
 * Součást z Google Play stahovaná až na požádání (Play Feature Delivery): runtime LiteRT-LM pro
 * Gemmu a Qwen v zařízení, tedy :ai-litert z knihovny ai-engine s nativní knihovnou `liblitertlm_jni.so`
 * (23 MB na arm64). V základu aplikace by ji měl každý, přitom model v zařízení si zapne málokdo.
 *
 * Kód tu žádný není. Základ aplikace na třídy součásti nevidí ani při překladu, implementaci si
 * po instalaci najde podle jména (`PlayLiteRtRuntime` v ai-engine :ai-core); keep pravidlo pro R8 nese knihovna
 * (consumer rules :ai-core), R8 zpracovává základ i součást dohromady.
 *
 * **Jméno modulu je jméno součásti** (`litertFeature` v `PlayLiteRtRuntime.MODULE`) — přejmenování
 * by instalaci tiše rozbilo.
 */
android {
    namespace = "eu.tintera.plume.litert"
    // Minor verze SDK: Compose 1.13 chce kompilovat proti 37.1+ (kontrola AAR metadat).
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
        }
    }

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
    implementation("eu.tintera.ai:ai-litert")
}
