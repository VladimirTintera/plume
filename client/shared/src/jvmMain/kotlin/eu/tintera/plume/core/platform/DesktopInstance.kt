package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Jedna běžící aplikace na jeden adresář dat. Druhá by narazila na zamčenou databázi TDLib —
 * a s aplikací schovanou v oznamovací oblasti se ze Startu spouští znovu běžně: kdo ikonu
 * nevidí, klikne na zástupce. Druhý proces proto jen požádá první o vytažení okna a skončí.
 *
 * - **Zámek souboru, ne existence souboru.** `FileLock` drží operační systém a po pádu procesu
 *   ho uvolní sám; soubor, který po pádu zůstal, by aplikaci zablokoval navždy.
 * - **Žádost jde po loopbacku** na port, který první instance zapíše vedle zámku, i s náhodným
 *   tokenem. Port na loopbacku vidí každý místní proces; token z dat aplikace (čte ho jen
 *   uživatel) zajistí, že okno vytáhne jen další Plume téhož uživatele. Víc žádost neumí.
 * - Ladicí běh má vlastní adresář dat (`plume.dataDir`), takže vedle nainstalované aplikace
 *   běžet může.
 */
object DesktopInstance {

    private val logger = Logger.withTag("DesktopInstance")

    /** Drží zámek po celý běh procesu; uvolní ho až jeho konec. */
    @Suppress("unused")
    private var lock: FileLock? = null

    /**
     * `true` = tahle instance je první a má běžet. `false` = už jedna běží, byla požádána
     * o okno a tahle má skončit. Volat před čímkoli, co sahá na data (TDLib).
     */
    fun acquire(): Boolean {
        val directory = desktopDataDirectory
        val lockFile = File(directory, "instance.lock")
        val portFile = File(directory, "instance.port")
        // `null` = zámek drží jiný proces. Výjimka = zámek nejde založit vůbec (adresář dat nejde
        // vytvořit…) — to NENÍ „už běžím": aplikace dřív v takovém případě tiše skončila s kódem 0.
        val acquired = try {
            RandomAccessFile(lockFile, "rw").channel.tryLock()
        } catch (e: Exception) {
            logger.w(e) { "Zámek jediné instance nejde založit, běžím bez něj." }
            return true
        }
        if (acquired == null) {
            logger.i { "Plume už běží, žádám ji o okno." }
            signal(portFile)
            return false
        }
        lock = acquired
        runCatching { listen(portFile) }.onFailure { logger.w(it) { "Žádosti druhé instance nepůjde přijímat." } }
        return true
    }

    private fun listen(portFile: File) {
        val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val server = ServerSocket()
        server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        portFile.writeText("${server.localPort}\n$token")
        thread(name = "plume-instance", isDaemon = true) {
            while (true) {
                runCatching {
                    server.accept().use { socket ->
                        socket.soTimeout = TIMEOUT_MS
                        val request = socket.getInputStream().bufferedReader().readLine()
                        if (request == "$ACTIVATE $token") {
                            logger.i { "Druhé spuštění, vytahuji okno." }
                            DesktopWindow.activate()
                        }
                    }
                }
            }
        }
    }

    private fun signal(portFile: File) {
        runCatching {
            val (port, token) = portFile.readLines().let { it[0].trim().toInt() to it[1].trim() }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MS)
                socket.getOutputStream().apply {
                    write("$ACTIVATE $token\n".toByteArray())
                    flush()
                }
            }
        }.onFailure { logger.w(it) { "Běžící Plume nejde požádat o okno." } }
    }

    private const val ACTIVATE = "activate"
    private const val TOKEN_BYTES = 16
    private const val TIMEOUT_MS = 2_000
}
