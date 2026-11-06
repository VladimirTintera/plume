package eu.tintera.plume.core.ai

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.FfmpegAudioDecoder
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopOs
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LiteRT-LM na desktopu (`litertlm-jvm`).
 *
 * - **Modely v místním, ne roamingovém adresáři.** Data aplikace na Windows leží v `%APPDATA%`
 *   (Roaming), který se v doménovém profilu synchronizuje mezi počítači — gigabajty modelu tam
 *   nepatří, proto `%LOCALAPPDATA%`. Na macOS a Linuxu ve složce dat; s `-Dplume.dataDir`
 *   (ladicí běh) pod ní, ať nesahá na modely nainstalované aplikace.
 * - **Stahování drží korutina procesu.** Aplikace na desktopu běží, dokud ji uživatel neukončí
 *   (zavřené okno ji schová do oznamovací oblasti); po ukončení zůstane `.part` a příští
 *   stahování naváže.
 * - **Model jde jen tam, kde je nativní knihovna** ([nativeLibraryBundled]): balíček nese jen
 *   knihovnu systému, na kterém se stavěl (plugin `plume.litertlm-desktop`), a Intel Mac ji
 *   nemá vůbec. **A jen na Javě 21+** ([javaSupported]): `litertlm-jvm` je pro ni přeložený
 *   a na starší by se model nabídl, ale při prvním použití selhal (desktopApp proto balí
 *   s JDK 21, `runtimeLauncher`).
 * - **EuroLLM (llama.cpp) taky jen s knihovnou v balíčku** ([llamaCppBundled], přeloží ji
 *   :client:ai-llamacpp-desktop) **a jen na procesoru, pro který je přeložená**
 *   ([llamaCppCpuSupported]) — jinak by první shrnutí shodilo celou aplikaci na SIGILL.
 * - **Obraz přes ImageIO** (JPEG a PNG z Telegramu; WebP neumí a popis se pak nenabídne),
 *   **zvuk přes FFmpeg** ([FfmpegAudioDecoder], hlasovka Ogg/Opus i kolečko MP4).
 * - **Emoji mimo základní rovinu Unicode do modelu nejdou** ([modelText]).
 */
class DesktopLiteRtPlatform : LiteRtPlatform {

    private val logger = Logger.withTag("OnDeviceAi")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloads = ConcurrentHashMap<LiteRtModel, Job>()

    override val modelsDir: File get() = File(localDirectory, MODELS_DIR)

    override val cacheDir: File get() = File(localDirectory, CACHE_DIR)

    override fun canRun(model: LiteRtModel): Boolean {
        val runtime = when (model.runtime) {
            ModelRuntime.LiteRt -> javaSupported && nativeLibraryBundled
            ModelRuntime.LlamaCpp -> llamaCppBundled && llamaCppCpuSupported
        }
        return runtime && totalMemoryBytes() >= model.minMemoryGb * GIB * MEMORY_TOLERANCE
    }

    override fun png(path: String, maxSide: Int): ByteArray? {
        val source = runCatching { ImageIO.read(File(path)) }.getOrNull() ?: return null
        val longer = max(source.width, source.height)
        val image = if (longer > maxSide) {
            val scale = maxSide.toDouble() / longer
            val width = (source.width * scale).roundToInt().coerceAtLeast(1)
            val height = (source.height * scale).roundToInt().coerceAtLeast(1)
            BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { scaled ->
                val graphics = scaled.createGraphics()
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(source, 0, 0, width, height, null)
                graphics.dispose()
            }
        } else {
            source
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    /**
     * Dekodér vydává 48 kHz stereo 16 bitů (pro reproduktor); na 16 kHz mono se to dostane
     * průměrem obou kanálů a tří po sobě jdoucích vzorků — 48 000 je přesně trojnásobek.
     */
    override fun wavClips(path: String): List<ByteArray> {
        val mono = ShortArrayBuilder()
        FfmpegAudioDecoder.open(path).use { decoder ->
            var sum = 0
            var count = 0
            while (true) {
                val chunk = decoder.read() ?: break
                val samples = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (samples.remaining() >= 2) {
                    sum += samples.get() + samples.get()
                    count += 2
                    if (count == DOWNSAMPLE * 2) {
                        mono.add((sum / count).toShort())
                        sum = 0
                        count = 0
                    }
                }
            }
        }
        val samples = mono.toArray()
        val clip = SAMPLE_RATE * MAX_CLIP_SECONDS
        return (samples.indices step clip).map { from -> wav(samples.copyOfRange(from, minOf(from + clip, samples.size))) }
    }

    /**
     * Bez znaků mimo základní rovinu Unicode (😊, 👍, 🌧 — skoro všechna emoji).
     *
     * HotSpot je přes JNI předává v „modifikovaném UTF-8" jako dvojici náhradních znaků (CESU-8)
     * a nativní strana LiteRT-LM na takovém textu vyhodí výjimku C++, kterou nikdo nechytí: spadne
     * celá JVM (`EXCEPTION_UNCAUGHT_CXX_EXCEPTION` v `nativeSendMessageAsync`). Stačí jediný
     * smajlík — „Ahoj 😊" shodí proces, „Ahoj ☔" (základní rovina) projde. Shrnutí chatu s emoji
     * tak aplikaci zavřelo. ART na Androidu posílá standardní UTF-8, tam se nic nemění. Odpověď
     * modelu emoji obsahovat smí, ta jde opačným směrem a dorazí celá.
     */
    override fun modelText(text: String): String {
        if (text.none { it.isSurrogate() }) return text
        return buildString(text.length) {
            text.codePoints().forEach { if (it < Character.MIN_SUPPLEMENTARY_CODE_POINT) appendCodePoint(it) }
        }
    }

    override fun startDownload(model: LiteRtModel, run: suspend () -> Unit) {
        downloads.compute(model) { _, running ->
            running?.takeIf { it.isActive } ?: scope.launch {
                try {
                    run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Stav pro obrazovku už zapsal runtime; hlášku ukáže Nastavení.
                    logger.i(e) { "Stahování ${model.displayName} skončilo chybou" }
                }
            }
        }
    }

    override fun cancelDownload(model: LiteRtModel) {
        downloads.remove(model)?.cancel()
    }

    override suspend fun downloadPending(model: LiteRtModel): Boolean = downloads[model]?.isActive == true

    private fun totalMemoryBytes(): Long =
        (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)?.totalMemorySize ?: 0L

    private fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        return ByteBuffer.allocate(WAV_HEADER + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(WAV_HEADER - 8 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
            samples.forEach { putShort(it) }
        }.array()
    }

    /** Rostoucí pole vzorků bez krabičkování `Short`. */
    private class ShortArrayBuilder {
        private var data = ShortArray(SAMPLE_RATE * 10)
        private var size = 0
        fun add(value: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }
        fun toArray(): ShortArray = data.copyOf(size)
    }

    private companion object {
        const val MODELS_DIR = "ai-models"
        const val CACHE_DIR = "ai-cache"
        const val SAMPLE_RATE = 16_000
        const val MAX_CLIP_SECONDS = 30
        const val DOWNSAMPLE = FfmpegAudioDecoder.OUTPUT_RATE / SAMPLE_RATE
        const val WAV_HEADER = 44
        const val GIB = 1024.0 * 1024 * 1024
        const val MEMORY_TOLERANCE = 0.85

        /** Java, pro kterou je `litertlm-jvm` přeložený (class file 65). */
        val javaSupported: Boolean = Runtime.version().feature() >= MIN_JAVA

        const val MIN_JAVA = 21

        /** Místní (neroamingový) adresář pro modely a jejich keš. */
        val localDirectory: File by lazy {
            val override = System.getProperty("plume.dataDir")?.takeIf { it.isNotBlank() }
            when {
                override != null -> desktopDataDirectory
                desktopOs == DesktopOs.Windows ->
                    File(System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local", "Plume")
                else -> desktopDataDirectory
            }
        }

        /** `aarch64` nebo `x86_64`, jak ji nesou složky nativních knihoven; `null` u jiné. */
        val arch: String? = when (System.getProperty("os.arch").lowercase()) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> null
        }

        /** `darwin`, `windows` nebo `linux` — první půlka jména složky s nativní knihovnou. */
        val system: String get() = when (desktopOs) {
            DesktopOs.Windows -> "windows"
            DesktopOs.MacOs -> "darwin"
            else -> "linux"
        }

        /**
         * Nese balíček nativní knihovnu LiteRT-LM pro tenhle systém? Cesty jsou ty, odkud ji
         * knihovna sama vybaluje (`NativeLibraryLoader`): `jni/<systém>-<architektura>/`.
         */
        val nativeLibraryBundled: Boolean by lazy {
            val cpu = arch ?: return@lazy false
            val library = if (desktopOs == DesktopOs.Windows) "litertlm_jni.dll" else "liblitertlm_jni.so"
            DesktopLiteRtPlatform::class.java.getResource("/com/google/ai/edge/litertlm/jni/$system-$cpu/$library") != null
        }

        /**
         * Nese balíček knihovnu llama.cpp pro tenhle systém? Cesta je ta, odkud ji vybaluje
         * `LlamaNative` (:client:ai-llamacpp), a kam ji dává :client:ai-llamacpp-desktop.
         */
        val llamaCppBundled: Boolean by lazy {
            val cpu = arch ?: return@lazy false
            val library = System.mapLibraryName("plume_llama")
            DesktopLiteRtPlatform::class.java.getResource("/eu/tintera/plume/ai/llamacpp/native/$system-$cpu/$library") != null
        }

        /**
         * Umí procesor instrukce, pro které je llama.cpp přeložená (client/ai-llamacpp/src/main/cpp)?
         *
         * - **x86_64: AVX2** (s ním FMA, F16C a BMI2 — všechny procesory s AVX2 je mají). Ptá se
         *   HotSpotu: `UseAVX` si JVM při startu nastaví podle CPUID i podle toho, jestli registry
         *   AVX ukládá systém, tedy totéž, co by chtěla vědět vlastní kontrola. Starší procesory
         *   a Pentium či Celeron bez AVX tu vyjdou nulou. Bez `jdk.management` (v balíčku je,
         *   `modules` v desktopApp) se nedá zjistit a model se nenabídne.
         * - **arm64: dot product.** Mají ho všechna Apple Silicon (M1 a novější); na Linuxu se
         *   čte z `/proc/cpuinfo` (`asimddp`). Pro Windows na ARM se knihovna nestaví.
         */
        val llamaCppCpuSupported: Boolean by lazy {
            when (arch) {
                "x86_64" -> runCatching {
                    ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
                        .getVMOption("UseAVX").value.toInt() >= 2
                }.getOrDefault(false)
                "aarch64" -> when (desktopOs) {
                    DesktopOs.MacOs -> true
                    DesktopOs.Windows -> false
                    else -> runCatching { File("/proc/cpuinfo").readText().contains(" asimddp") }.getOrDefault(false)
                }
                else -> false
            }
        }
    }
}
