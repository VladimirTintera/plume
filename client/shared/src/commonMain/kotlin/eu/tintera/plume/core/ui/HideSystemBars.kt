package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Celá obrazovka bez systémových lišt, dokud je [hidden] (a dokud je v kompozici). Lišty jde
 * dočasně vytáhnout tahem od okraje; s odchodem z kompozice se vrátí.
 */
@Composable
expect fun HideSystemBars(hidden: Boolean)
