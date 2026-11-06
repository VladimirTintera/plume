package eu.tintera.plume.core.ai

import eu.tintera.plume.ai.engine.LITERT_ENGINE_FACTORY
import eu.tintera.plume.ai.engine.LlmEngineFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Desktop: runtime LiteRT-LM je v balíčku aplikace (:client:desktopApp závisí na :client:ai-litert),
 * nic se nedoinstalovává. Chybí jen v buildu, který modul nepřibalil — pak se model nenabídne
 * stejně jako bez nativní knihovny (`DesktopLiteRtPlatform.canRun`).
 */
class BundledLiteRtRuntime : LiteRtRuntime {

    private val engines: LlmEngineFactory? by lazy {
        runCatching { loadEngineFactory(BundledLiteRtRuntime::class.java.classLoader) }.getOrNull()
    }

    override fun installed(): Boolean = present

    override fun install(): Flow<ModelDownload> = flow {
        if (!present) throw RuntimeInstallException()
    }

    override fun engines(): LlmEngineFactory = engines ?: throw IllegalStateException("$LITERT_ENGINE_FACTORY is missing")

    private val present: Boolean by lazy {
        runCatching { Class.forName(LITERT_ENGINE_FACTORY, false, BundledLiteRtRuntime::class.java.classLoader) }.isSuccess
    }
}
