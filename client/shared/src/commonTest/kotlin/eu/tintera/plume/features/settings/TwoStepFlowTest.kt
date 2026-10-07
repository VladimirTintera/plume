package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.ResetProgress
import eu.tintera.plume.core.telegram.resetProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kroky průvodce dvoufázového ověření a pravidla, která hlídají formulář dřív než server. */
class TwoStepFlowTest {

    @Test
    fun zapnutiKonciKodemZEmailu() {
        val steps = TwoStepAction.Enable.steps()
        assertEquals(TwoStepStep.NewPassword, steps.first())
        assertEquals(TwoStepStep.EmailCode, steps.last())
        // Odesílá se až e-mail, ne nápověda — nové heslo s e-mailem platí teprve po kódu.
        assertTrue(TwoStepAction.Enable.commitsAt(TwoStepStep.Email))
        assertFalse(TwoStepAction.Enable.commitsAt(TwoStepStep.Hint))
    }

    @Test
    fun vypnutiJeJedinyKrokSoucasnehoHesla() {
        assertEquals(listOf(TwoStepStep.CurrentPassword), TwoStepAction.Disable.steps())
        assertTrue(TwoStepAction.Disable.commitsAt(TwoStepStep.CurrentPassword))
        assertNull(TwoStepAction.Disable.next(TwoStepStep.CurrentPassword))
    }

    @Test
    fun zKoduSeZpetNevraci() {
        // Změna už je na serveru; zpět by vedlo do kroku, který se znovu odeslat nesmí.
        assertNull(TwoStepAction.Enable.previous(TwoStepStep.EmailCode))
        assertNull(TwoStepAction.ChangeEmail.previous(TwoStepStep.EmailCode))
        assertEquals(TwoStepStep.NewPassword, TwoStepAction.Enable.previous(TwoStepStep.Hint))
        assertNull(TwoStepAction.ChangePassword.previous(TwoStepStep.CurrentPassword))
    }

    @Test
    fun obnovaOdesilaNapovedou() {
        assertEquals(TwoStepStep.RecoveryCode, TwoStepAction.Recover.steps().first())
        assertTrue(TwoStepAction.Recover.commitsAt(TwoStepStep.Hint))
        assertTrue(TwoStepAction.ChangePassword.commitsAt(TwoStepStep.Hint))
        assertFalse(TwoStepAction.ConfirmEmail.commitsAt(TwoStepStep.EmailCode))
    }

    @Test
    fun noveHeslo() {
        assertEquals(NewPasswordProblem.Empty, newPasswordProblem("", ""))
        assertEquals(NewPasswordProblem.Mismatch, newPasswordProblem("tajne", "tajny"))
        assertEquals(NewPasswordProblem.TooLong, newPasswordProblem("a".repeat(257), "a".repeat(257)))
        assertNull(newPasswordProblem("tajne heslo ", "tajne heslo "))
        // Mezera na konci je součást hesla, ne překlep.
        assertEquals(NewPasswordProblem.Mismatch, newPasswordProblem("tajne ", "tajne"))
    }

    @Test
    fun napovedaNesmiBytHeslo() {
        assertTrue(hintRevealsPassword("kocka", "kocka"))
        assertTrue(hintRevealsPassword(" kocka ", "kocka"))
        assertFalse(hintRevealsPassword("", "kocka"))
        assertFalse(hintRevealsPassword("jmeno kocky", "kocka"))
    }

    @Test
    fun email() {
        assertTrue(isPlausibleEmail("jana@example.cz"))
        assertTrue(isPlausibleEmail(" jana@example.cz "))
        assertFalse(isPlausibleEmail("jana"))
        assertFalse(isPlausibleEmail("@example.cz"))
        assertFalse(isPlausibleEmail("jana@example"))
        assertFalse(isPlausibleEmail("jana@example."))
        assertFalse(isPlausibleEmail("ja na@example.cz"))
        assertFalse(isPlausibleEmail("jana@@example.cz"))
    }

    @Test
    fun cekaniNaReset() {
        assertEquals(ResetProgress.None, resetProgress(resetDate = 0, nowSeconds = 100))
        assertEquals(ResetProgress.Waiting(200), resetProgress(resetDate = 200, nowSeconds = 100))
        assertEquals(ResetProgress.Ready, resetProgress(resetDate = 100, nowSeconds = 100))
    }
}
