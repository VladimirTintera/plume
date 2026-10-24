package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

/** Kolik z cesty k další úrovni je hotovo a kolik boostů chybí. */
class BoostStatusTest {

    private fun status(boosts: Int, current: Int, next: Int) = BoostStatus(
        level = 1,
        boostCount = boosts,
        currentLevelBoosts = current,
        nextLevelBoosts = next,
        premiumMembers = 0,
        premiumPercentage = 0.0,
        boostUrl = "",
    )

    @Test
    fun prubehSePocitaOdSoucasneUrovne() {
        val status = status(boosts = 7, current = 5, next = 9)
        assertEquals(0.5f, status.progress)
        assertEquals(2, status.missingBoosts)
    }

    @Test
    fun nejvyssiUrovenJePlnaANicNechybi() {
        val status = status(boosts = 120, current = 100, next = 0)
        assertEquals(1f, status.progress)
        assertEquals(0, status.missingBoosts)
    }

    @Test
    fun bezBoostuJeNulaAZbytekChybi() {
        val status = status(boosts = 0, current = 0, next = 1)
        assertEquals(0f, status.progress)
        assertEquals(1, status.missingBoosts)
    }
}
