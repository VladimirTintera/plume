plugins {
    `kotlin-dsl`
}

dependencies {
    // Obojí dodá za běhu AGP z kořenového buildu (`apply false` v build.gradle.kts). Vlastní kopie
    // ASM na classpath pluginu by se s tou z AGP mohla potkat jako dvě různé třídy ClassVisitor.
    compileOnly(libs.android.gradleApi)
    compileOnly(libs.asm)
}

gradlePlugin {
    plugins {
        register("sourceChecks") {
            id = "plume.source-checks"
            implementationClass = "eu.tintera.plume.gradle.SourceChecksPlugin"
        }
        register("translations") {
            id = "plume.translations"
            implementationClass = "eu.tintera.plume.gradle.TranslationsPlugin"
        }
        register("tdlibAndroid") {
            id = "plume.tdlib-android"
            implementationClass = "eu.tintera.plume.gradle.TdlibAndroidPlugin"
        }
        register("localeConfig") {
            id = "plume.locale-config"
            implementationClass = "eu.tintera.plume.gradle.LocaleConfigPlugin"
        }
        register("tdlibDesktop") {
            id = "plume.tdlib-desktop"
            implementationClass = "eu.tintera.plume.gradle.TdlibDesktopPlugin"
        }
    }
}
