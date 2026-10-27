package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.features.conversation.items.DiceStakeLabel
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Pilulka pod kostkou se sázkou: vklad, po dopadu výhra nebo „bez výhry". Částky jsou
 * v nanotonech; jazyk testu je angličtina (desetinná tečka).
 *
 * Angličtina se nastavuje výslovně: Compose Resources na JVM berou jazyk z `Locale.getDefault()`
 * (tak ho přepíná i desktop, viz `AppLanguage.jvm.kt`), takže na stroji v češtině by se
 * vykreslilo „Vsazeno 1,5 TON" a test by padal.
 */
@OptIn(ExperimentalTestApi::class)
class DiceStakeLabelTest {

    private val previousLocale = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(previousLocale)

    @Test
    fun vkladVyhraAPropadlaSazka() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    DiceStakeLabel(MessageMedia.DiceStake(stake = 1_500_000_000, prize = null))
                    DiceStakeLabel(MessageMedia.DiceStake(stake = 1_500_000_000, prize = 3_000_000_000))
                    DiceStakeLabel(MessageMedia.DiceStake(stake = 2_000_000_000, prize = 0))
                }
            }
        }
        onNodeWithText("Stake 1.5 TON").assertExists()
        onNodeWithText("Stake 1.5 TON · won 3 TON").assertExists()
        onNodeWithText("Stake 2 TON · no win").assertExists()
    }
}
