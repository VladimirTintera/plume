package eu.tintera.plume.core.ai

import eu.tintera.ai.core.Generated
import eu.tintera.ai.core.ModelDownload
import eu.tintera.ai.core.ModelInfo
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * OnDeviceAi pro testy: každé volání, které test nepřepsal, vyhodí chybu se jménem metody — test tak
 * hned řekne, na co ještě sahá. Vygenerované z rozhraní; po změně rozhraní stačí doplnit chybějící
 * člen (překladač ho vyjmenuje).
 */
open class UnimplementedOnDeviceAi : OnDeviceAi {
    override suspend fun status(variant: ModelVariant) : OnDeviceAiStatus = error("status")
    override suspend fun info(variant: ModelVariant) : ModelInfo? = error("info")
    override fun download(variant: ModelVariant, mobileData: Boolean): Flow<ModelDownload> = error("download")
    override suspend fun countTokens(variant: ModelVariant, prompt: String) : Int? = error("countTokens")
    override fun generate(variant: ModelVariant, prompt: String) : Flow<Generated> = error("generate")
}
