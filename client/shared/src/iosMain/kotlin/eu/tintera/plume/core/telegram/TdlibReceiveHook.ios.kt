package eu.tintera.plume.core.telegram

// Kotlin/Native přijímací smyčku wrapperu přepsat nedovolí, takže na iOS odpovědi TDLib
// opravou neprocházejí a dárek bez pozadí aplikaci dál shodí. Viz „Dárek" v CLAUDE.md.
internal actual val tdlibJsonRepairWired: Boolean = false
