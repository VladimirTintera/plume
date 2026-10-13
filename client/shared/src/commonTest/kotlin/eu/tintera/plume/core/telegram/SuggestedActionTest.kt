package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.SuggestedActionCheckPassword
import dev.g000sha256.tdl.dto.SuggestedActionCustom
import dev.g000sha256.tdl.dto.SuggestedActionExtendPremium
import dev.g000sha256.tdl.dto.SuggestedActionAddLoginPasskey
import dev.g000sha256.tdl.dto.SuggestedActionSetBirthdate
import dev.g000sha256.tdl.dto.SuggestedActionSetLoginEmailAddress
import dev.g000sha256.tdl.dto.SuggestedActionSetPassword
import dev.g000sha256.tdl.dto.SuggestedActionUpgradePremium
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Které doporučené akce od TDLib Plume nabízí a jak je převádí. */
class SuggestedActionTest {

    @Test
    fun podporovaneAkceSePrevedou() {
        assertEquals(SuggestedAction.CheckPassword, SuggestedActionCheckPassword().toDomain())
        assertEquals(SuggestedAction.SetPassword(authorizationDelaySeconds = 604_800), SuggestedActionSetPassword(authorizationDelay = 604_800).toDomain())
        // Vyžadovaný přihlašovací e-mail nejde schovat.
        assertEquals(SuggestedAction.SetLoginEmail(canBeHidden = false), SuggestedActionSetLoginEmailAddress(canBeHidden = false).toDomain())
        assertEquals(
            SuggestedAction.Custom(name = "verify", title = "Ověř účet", description = "Popis", url = "https://t.me/x"),
            SuggestedActionCustom(
                name = "verify",
                title = FormattedText(text = "Ověř účet", entities = emptyArray()),
                description = FormattedText(text = "Popis", entities = emptyArray()),
                url = "https://t.me/x",
            ).toDomain(),
        )
    }

    @Test
    fun placeneSluzbyANeumeneSeVynechaji() {
        assertNull(SuggestedActionUpgradePremium().toDomain())
        assertNull(SuggestedActionExtendPremium(managePremiumSubscriptionUrl = "https://t.me/premium").toDomain())
        // Přístupový klíč cizí klient přidat nemůže (viz docs/poznatky/nastaveni.md).
        assertNull(SuggestedActionAddLoginPasskey().toDomain())
    }

    @Test
    fun datumNarozeniSeNabizi() {
        assertEquals(SuggestedAction.SetBirthdate, SuggestedActionSetBirthdate().toDomain())
    }
}
