package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Nahraje OpenSSL, které potřebuje nativní TDLib z `tdl-coroutines-jvm` na macOS. Volá se
 * jednou při startu desktopové aplikace, **před prvním použitím TDLib**.
 *
 * Wrapper 15.0.0 má macOS knihovnu slinkovanou proti Homebrew autora absolutní cestou
 * (g000sha256/tdl-coroutines#163) a bez Homebrew se nenačte. Přepis jaru při buildu
 * (`client/desktopApp/build.gradle.kts`) ji přepíše na `@rpath/libssl.3.dylib`
 * a `@rpath/libcrypto.3.dylib` a obě knihovny k ní přibalí. dyld pak `@rpath` závislost
 * najde mezi už nahranými knihovnami podle jejich install name — stačí je nahrát dřív, a to
 * tady. Wrapper sám rozbaluje jen svoji knihovnu, o přibalených neví.
 *
 * Mimo macOS (nebo bez přepsaného jaru) tu nic není a funkce nic neudělá: Windows build si
 * OpenSSL veze sám a Linux bere systémové `libssl.so.3`.
 */
fun preloadTdlibNativeDependencies() {
    val logger = Logger.withTag("TdlibNatives")
    val platform = macPlatform() ?: return
    val libraries = BUNDLED_LIBRARIES.mapNotNull { name ->
        TdlibReceiveHook::class.java.getResource("/$platform/$name")?.let { name to it }
    }
    if (libraries.isEmpty()) {
        logger.w { "Jar TDLib nemá přibalené OpenSSL; nativní knihovna se bez Homebrew nenačte." }
        return
    }
    val directory = createTempDirectory("plume-tdlib").toFile().apply { deleteOnExit() }
    libraries.forEach { (name, resource) ->
        val file = File(directory, name).apply { deleteOnExit() }
        resource.openStream().use { input -> file.outputStream().use(input::copyTo) }
        System.load(file.absolutePath)
    }
}

/** Složka ve jaru wrapperu (`macos/arm64`, `macos/x64`), stejně jako ji pojmenovává on sám. */
private fun macPlatform(): String? {
    if ("mac" !in System.getProperty("os.name").orEmpty().lowercase()) return null
    val arch = System.getProperty("os.arch").orEmpty().lowercase()
    return when {
        "aarch64" in arch -> "macos/arm64"
        "x86_64" in arch || "amd64" in arch -> "macos/x64"
        else -> null
    }
}

/** V pořadí závislostí: libssl potřebuje libcrypto. */
private val BUNDLED_LIBRARIES = listOf("libcrypto.3.dylib", "libssl.3.dylib")
