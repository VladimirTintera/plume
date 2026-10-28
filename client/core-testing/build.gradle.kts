plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

/**
 * Testovací náhražky typů z :client:core (`Fake…`, `Unimplemented…`, `testMessage`).
 *
 * Testy jednoho KMP modulu druhý nevidí, a náhražky potřebují testy obou: `core` (pár testů)
 * i `shared` (desítky testů ViewModelů). Proto vlastní modul s náhražkami v `commonMain`, na
 * kterém visí `commonTest` obou. Do aplikace se nedostane — závisí na něm jen testy.
 */
kotlin {
    jvmToolchain(17)

    android {
        namespace = "eu.tintera.plume.core.testing"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(projects.client.core)
            implementation(libs.kotlinx.coroutines.core)
            // ConversationRepository bere měsíc a pásmo (kalendář zpráv).
            implementation(libs.kotlinx.datetime)
        }
    }
}
