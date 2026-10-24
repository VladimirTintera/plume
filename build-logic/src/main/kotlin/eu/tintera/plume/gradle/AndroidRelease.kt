package eu.tintera.plume.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.io.File
import java.util.Properties

/**
 * Verze a podpis release buildu pro každý vstupní bod, který jde do Play (telefon, hodinky).
 * Hodinky jsou ve stejném záznamu v obchodě, takže podepisují týmž upload klíčem a verzi berou
 * z téhož tagu; dvě kopie téhož bloku v build skriptech by se rozešly.
 *
 * - **Verze NENÍ v commitu, je v tagu.** Release workflow ji předá přes `-PandroidVersionCode`
 *   / `-PandroidVersionName`; výchozí hodnoty jsou jen záloha pro lokální build.
 * - **`versionCode` musí být v celém záznamu jedinečný.** Play nevezme dva bundly se stejným
 *   číslem, ani když jeden je pro telefon a druhý pro hodinky. Proto [AndroidReleaseExtension.versionCodeOffset].
 * - **Konfigurace mimo git se čte [configValue]: property → ENV → `local.properties`.** ENV přes
 *   `providers`, ne `System.getenv` — jinak by nevstoupila do otisku konfigurační keše.
 * - **Keystore na CI je absolutní cesta (`RUNNER_TEMP`), lokálně relativní od kořene projektu.**
 * - **`verifyReleaseConfiguration` visí na `bundleRelease`/`assembleRelease`**: nepodepsaný AAB
 *   Play odmítne a build s `TELEGRAM_API_ID = 0` se přeloží, jen se v něm nejde přihlásit.
 */
class AndroidReleasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("plumeRelease", AndroidReleaseExtension::class.java)
        extension.versionCodeOffset.convention(0)

        project.pluginManager.withPlugin("com.android.application") {
            val keystore = project.configValue("releaseStoreFile", "ANDROID_KEYSTORE_PATH")
                ?.let { path -> File(path).takeIf { it.isAbsolute } ?: project.rootProject.file(path) }
            val versionCode = (project.configValue("androidVersionCode", "ANDROID_VERSION_CODE") ?: "12").toInt()
            val versionName = project.configValue("androidVersionName", "ANDROID_VERSION_NAME") ?: "0.2.0"
            val passwords = listOf(
                "releaseStorePassword" to project.configValue("releaseStorePassword", "ANDROID_KEYSTORE_PASSWORD"),
                "releaseKeyAlias" to project.configValue("releaseKeyAlias", "ANDROID_KEY_ALIAS"),
                "releaseKeyPassword" to project.configValue("releaseKeyPassword", "ANDROID_KEY_PASSWORD"),
            )

            // finalizeDsl: až po build skriptu modulu, aby platil jeho `plumeRelease { … }`.
            project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java).finalizeDsl { android ->
                android.defaultConfig.versionCode = versionCode + extension.versionCodeOffset.get()
                android.defaultConfig.versionName = versionName

                // Bez klíče se release přeloží nepodepsaný; `verifyReleaseConfiguration` ho shodí.
                if (keystore?.exists() == true) {
                    val release = android.signingConfigs.create("release") {
                        storeFile = keystore
                        storePassword = passwords[0].second
                        keyAlias = passwords[1].second
                        keyPassword = passwords[2].second
                        // v3 umožní později rotovat klíč. Musí být zapnuté OD ZAČÁTKU — rotace se
                        // opírá o podpisovou linii v APK a dodatečně ji doplnit nejde.
                        enableV2Signing = true
                        enableV3Signing = true
                    }
                    android.buildTypes.getByName("release").signingConfig = release
                }
            }

            val apiId = project.configValue("telegramApiId", "TELEGRAM_API_ID")?.toIntOrNull() ?: 0
            val apiHash = project.configValue("telegramApiHash", "TELEGRAM_API_HASH").orEmpty()
            val verify = project.tasks.register("verifyReleaseConfiguration") {
                group = "verification"
                description = "Ověří, že release build má podpisový klíč a api_id/api_hash."
                doLast {
                    val store = keystore
                    check(store != null) {
                        "Chybí podpisový klíč pro release. Doplň do local.properties `releaseStoreFile` " +
                            "(nebo na CI ENV ANDROID_KEYSTORE_PATH)."
                    }
                    check(store.exists()) { "Keystore '${store.absolutePath}' neexistuje." }
                    val missing = passwords.filter { it.second == null }.map { it.first }
                    check(missing.isEmpty()) {
                        "Ke keystore chybí: ${missing.joinToString()}. Na CI jsou to secrets " +
                            "ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD."
                    }
                    check(apiId != 0 && apiHash.isNotBlank()) {
                        "Chybí telegramApiId/telegramApiHash z my.telegram.org. Bez nich se release build " +
                            "přeloží, ale přihlášení skončí chybou od serveru. Na CI jsou to secrets " +
                            "TELEGRAM_API_ID / TELEGRAM_API_HASH."
                    }
                }
            }
            project.tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
                dependsOn(verify)
            }
        }
    }
}

abstract class AndroidReleaseExtension {
    /**
     * Přičte se k `versionCode` z tagu. Telefon 0, hodinky 100 000 000: oba bundly vycházejí
     * z téhož tagu do téhož záznamu v Play a stejné číslo by druhý upload odmítlo. Hodinky
     * vyšším číslem nic nezastíní — telefon jejich bundl nedostane (`uses-feature` watch).
     */
    abstract val versionCodeOffset: Property<Int>
}

/**
 * Hodnota konfigurace mimo git. Priorita: gradle property (`-Pklic=…`) > ENV (CI) > `local.properties`.
 */
internal fun Project.configValue(gradleKey: String, envKey: String): String? =
    (providers.gradleProperty(gradleKey).orNull
        ?: providers.environmentVariable(envKey).orNull
        ?: localProperty(gradleKey))
        ?.takeIf { it.isNotBlank() }

private fun Project.localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    return Properties()
        .apply { file.inputStream().use { load(it) } }
        .getProperty(name)
        ?.takeIf { it.isNotBlank() }
}
