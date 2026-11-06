plugins {
    alias(libs.plugins.androidDynamicFeature)
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

/**
 * Prázdné „knihovny" pro ABI, pro která LiteRT-LM nic nemá (armeabi-v7a, x86).
 *
 * bundletool chce, aby VŠECHNY moduly s nativními knihovnami podporovaly stejnou sadu ABI:
 * „All modules with native libraries must support the same set of ABIs" — základ má kvůli
 * TDLib čtyři, `litertlm-android` jen arm64-v8a a x86_64. Vypustit 32bitové telefony z Play kvůli
 * volitelné funkci nedává smysl. Tam se model stejně nenabízí (`AndroidLiteRtPlatform.canRun`
 * chce arm64), takže se tahle součást na takový telefon nikdy nestáhne a soubor nikdo nenačte.
 */
abstract class AbiPlaceholders : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        listOf("armeabi-v7a", "x86").forEach { abi ->
            outputDir.file("$abi/libplume_no_litertlm.so").get().asFile.apply {
                parentFile.mkdirs()
                writeBytes(ByteArray(0))
            }
        }
    }
}

val abiPlaceholders = tasks.register<AbiPlaceholders>("abiPlaceholders") {
    outputDir.set(layout.buildDirectory.dir("generated/abiPlaceholders"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(abiPlaceholders, AbiPlaceholders::outputDir)
    }
}
