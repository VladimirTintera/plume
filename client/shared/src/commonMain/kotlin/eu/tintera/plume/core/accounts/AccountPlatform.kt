package eu.tintera.plume.core.accounts

/**
 * Jde na téhle platformě přidat další účet? Na iOS zatím ne: rozšíření pro notifikace a sdílení
 * účet neznají a push by chodil jen jednomu (návrh `docs/navrh-vice-uctu.md`, fáze 2).
 */
expect val canAddAccounts: Boolean
