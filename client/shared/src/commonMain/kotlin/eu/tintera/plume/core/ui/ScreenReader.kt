package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Běží čtečka obrazovky, která čte to, čeho se prst dotkne (TalkBack s prozkoumáváním dotykem,
 * VoiceOver)? Mění se za běhu.
 *
 * Na co se to hodí: obsah, který **sám odjíždí** (příběh po šesti vteřinách), čtečka nestihne
 * přečíst — uživatel se k němu prstem teprve hledá. Kde čtečka běží, má se posouvat jen na pokyn.
 * Desktop čtečku nehlásí (`false`).
 */
@Composable
expect fun rememberScreenReaderEnabled(): Boolean
