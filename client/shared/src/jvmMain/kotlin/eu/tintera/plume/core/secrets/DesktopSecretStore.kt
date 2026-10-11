package eu.tintera.plume.core.secrets

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopOs
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Trezor tajemství na desktopu: na macOS Klíčenka, jinde soubor v datech aplikace.
 *
 * - **macOS: Klíčenka přes `/usr/bin/security`.** Nativní API (`SecItem*`) by chtělo JNA nebo
 *   JNI most; nástroj `security` je součást systému a podepsaný Applem. Tajemství jde do něj
 *   STANDARDNÍM VSTUPEM (`security -i`), ne argumentem — argumenty procesu vidí každý přes `ps`.
 *   Položka je obecné heslo (`generic-password`) se službou [service] a účtem [account], stejně
 *   jako `KeychainSecretStore` na iOS.
 * - **Windows a Linux: soubor s právy jen pro vlastníka.** Je to slabší než systémový trezor
 *   (DPAPI, Secret Service) — klíč leží na tomtéž disku co databáze, takže šifrování chrání jen
 *   proti kopii databáze bez souboru s klíčem. Trezory těchto systémů jsou v docs/roadmap.md.
 */
fun desktopSecretStore(service: String, account: String, logTag: String): SecretStore =
    if (desktopOs == DesktopOs.MacOs) KeychainCliSecretStore(service, account, logTag) else FileSecretStore(service, account, logTag)

private abstract class DesktopSecretStore(logTag: String) : SecretStore {
    protected val logger = Logger.withTag(logTag)

    override fun generate(): ByteArray = ByteArray(SECRET_SIZE).also(SecureRandom()::nextBytes)
}

private class KeychainCliSecretStore(
    private val service: String,
    private val account: String,
    logTag: String,
) : DesktopSecretStore(logTag) {

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        val result = security("find-generic-password", "-s", service, "-a", account, "-w")
        when (result.exitCode) {
            0 -> result.output.trim().hexToByteArrayOrNull().also { if (it == null) logger.w { "Položka v Klíčence není hex." } }
            NOT_FOUND -> null
            else -> {
                logger.w { "Klíčenka nejde přečíst (${result.exitCode}): ${result.output.trim()}" }
                null
            }
        }
    }

    override suspend fun store(key: ByteArray) = withContext(Dispatchers.IO) {
        // `-U` přepíše existující položku. Hodnoty jsou naše konstanty a hex, uvozovky stačí.
        val command = "add-generic-password -U -s \"$service\" -a \"$account\" -w ${key.toHex()}\n"
        val result = security("-i", input = command)
        check(result.exitCode == 0) { "Klíčenka nejde zapsat (${result.exitCode}): ${result.output.trim()}" }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            val result = security("delete-generic-password", "-s", service, "-a", account)
            if (result.exitCode != 0 && result.exitCode != NOT_FOUND) {
                logger.w { "Položku v Klíčence nejde smazat (${result.exitCode}): ${result.output.trim()}" }
            }
        }
    }

    private class Result(val exitCode: Int, val output: String)

    private fun security(vararg arguments: String, input: String? = null): Result {
        val process = ProcessBuilder(listOf("/usr/bin/security") + arguments).redirectErrorStream(true).start()
        process.outputStream.use { stream -> input?.let { stream.write(it.toByteArray()) } }
        val output = process.inputStream.bufferedReader().readText()
        return Result(process.waitFor(), output)
    }

    private companion object {
        /** `errSecItemNotFound` tak, jak ho `security` vrací v návratovém kódu. */
        const val NOT_FOUND = 44
    }
}

private class FileSecretStore(service: String, account: String, logTag: String) : DesktopSecretStore(logTag) {

    private val file = File(File(desktopDataDirectory, "secrets"), "$service.$account")

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { file.readText().trim().hexToByteArrayOrNull() }
            .onFailure { logger.w(it) { "Soubor s tajemstvím nejde přečíst." } }
            .getOrNull()
    }

    override suspend fun store(key: ByteArray) = withContext(Dispatchers.IO) {
        file.parentFile.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(key.toHex())
        runCatching { Files.setPosixFilePermissions(temporary.toPath(), PosixFilePermissions.fromString("rw-------")) }
        check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) { "Soubor s tajemstvím nejde zapsat." }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { file.delete() }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun String.hexToByteArrayOrNull(): ByteArray? {
    if (length % 2 != 0 || any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
