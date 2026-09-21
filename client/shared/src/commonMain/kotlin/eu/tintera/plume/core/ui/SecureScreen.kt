package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Zakáže snímky a nahrávání obrazovky, dokud je [enabled] a obrazovka složená — pro mizející
 * média v prohlížeči. Při odchodu se zákaz zruší.
 *
 * Android: `FLAG_SECURE` na okně. Snímek pak vyjde černý a obsah se neukáže ani v přehledu
 * úloh. iOS snímky obrazovky zakázat neumí (dá se je jen zpětně zjistit), tam se nedělá nic.
 */
@Composable
expect fun SecureScreen(enabled: Boolean)
