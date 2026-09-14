import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.buildConfig)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // Expressive API je celé experimentální; opt-in globálně, ať to nemusí být na každé funkci.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.coroutines.FlowPreview")
        // kotlinx-datetime 0.8 staví na kotlin.time.Instant.
        optIn.add("kotlin.time.ExperimentalTime")
    }

    // AGP 9: Android target jde přes com.android.kotlin.multiplatform.library, ne com.android.library.
    android {
        namespace = "eu.tintera.plume.client"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            // KMP moduly mají Android resources defaultně vypnuté; bez tohohle se composeResources
            // nezabalí a každé čtení stringu spadne až za běhu na „No instrumentation registered!".
            enable = true
        }
        // Bez tohohle se commonTest na JVM vůbec nespustí.
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            // TDLib je statická knihovna; framework musí být statický, jinak se její symboly
            // nedostanou do výsledné binárky a link padne na undefined symbols _td_*.
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Platformy bez Material You (dynamic color ze systému). Zatím jen iOS, ale až přibude
        // desktop (tdl-coroutines publikuje i JVM targety), spadne sem taky — proto vlastní
        // source set a ne prosté iosMain.
        val nonDynamicThemeMain by creating {
            dependsOn(commonMain.get())
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.animation)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material3)

            implementation(libs.androidx.adaptive)
            implementation(libs.androidx.adaptive.layout)
            implementation(libs.androidx.adaptive.navigation3)
            implementation(libs.navigation)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)

            // Generuje expressive ColorScheme ze seed barvy — náhrada Material You tam,
            // kde systém tapetovou paletu nedává (viz theme/ColorScheme.kt).
            implementation(libs.materialkolor)

            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.navigation3)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
            implementation(libs.coil.compose)

            // Jediné místo v projektu, kde smí být TDLib na classpath — viz core/telegram.
            implementation(libs.tdl.coroutines)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            // androidContext() v platformModule — Koin na Androidu potřebuje Context pro cestu k DB.
            implementation(libs.koin.android)
        }

        iosMain {
            dependsOn(nonDynamicThemeMain)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "eu.tintera.plume.resources"
    generateResClass = always
}

// api_id/api_hash z my.telegram.org. Čtou se z local.properties (gitignorovaný), aby se pár
// nikdy nedostal do repozitáře. Bez nich se projekt přeloží, ale přihlášení skončí chybou
// od serveru — hlásí se to srozumitelně místo pádu (viz sendTdlibParameters v TdlTelegramSession).
buildConfig {
    packageName = "eu.tintera.plume"
    className = "BuildConfiguration"
    buildConfigField("Int", "TELEGRAM_API_ID", localProperty("telegramApiId")?.toIntOrNull() ?: 0)
    buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProperty("telegramApiHash") ?: ""}\"")
}

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    return Properties()
        .apply { file.inputStream().use { load(it) } }
        .getProperty(name)
        ?.takeIf { it.isNotBlank() }
}
