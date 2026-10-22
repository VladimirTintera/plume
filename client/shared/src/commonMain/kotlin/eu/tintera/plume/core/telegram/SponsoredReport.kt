package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ReportSponsoredResult
import dev.g000sha256.tdl.dto.ReportSponsoredResultAdsHidden
import dev.g000sha256.tdl.dto.ReportSponsoredResultFailed
import dev.g000sha256.tdl.dto.ReportSponsoredResultOk
import dev.g000sha256.tdl.dto.ReportSponsoredResultOptionRequired
import dev.g000sha256.tdl.dto.ReportSponsoredResultPremiumRequired
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sponsored_ads_hidden
import eu.tintera.plume.resources.sponsored_hide_needs_premium
import eu.tintera.plume.resources.sponsored_report_gone

/**
 * Krok průvodce nahlášením reklamy — společný pro reklamu v kanálu (`reportChatSponsoredMessage`)
 * i sponzorovaný chat v hledání (`reportSponsoredChat`); obě vrací týž výsledek.
 */
internal fun ReportSponsoredResult.toReportStep(): ReportStep =
    // Bez `else`: nový výsledek v novější TDLib musí rozbít překlad, ne tiše skončit.
    when (this) {
        is ReportSponsoredResultOk -> ReportStep.Done
        is ReportSponsoredResultOptionRequired -> ReportStep.ChooseOption(
            title = title,
            options = options.map { ReportOption(ReportOptionId(it.id), it.text) },
        )
        is ReportSponsoredResultAdsHidden -> ReportStep.Ended(UiText(Res.string.sponsored_ads_hidden), done = true)
        is ReportSponsoredResultPremiumRequired ->
            ReportStep.Ended(UiText(Res.string.sponsored_hide_needs_premium), done = false)
        // Reklama je stará nebo zmizela; nahlásit už nejde, ale ani ji ukazovat.
        is ReportSponsoredResultFailed -> ReportStep.Ended(UiText(Res.string.sponsored_report_gone), done = true)
    }
