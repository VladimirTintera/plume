package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Smí aplikace rozostřovat pozadí (mléčné sklo, závoj pod nabídkou, pozadí prohlížeče)?
 *
 * Řídí to systém: na Androidu volba Přístupnost → Barvy a pohyb → „Omezit efekty rozmazání"
 * (Android 16 QPR2), úspora baterie a vývojářská volba rozostření oken — všechno se hlásí jako
 * `WindowManager.isCrossWindowBlurEnabled`. Na iOS „Omezit průhlednost". Kde rozostřovat nesmí,
 * kreslí Haze náhradní tónování, stejně jako na Androidu 11 a starším.
 *
 * **Spoiler a citlivý obsah se rozostřují dál.** Tam rozostření obsah SKRÝVÁ, není to ozdoba
 * pozadí, na kterou volba míří — a jejich náhradní tónování by bylo jen neprůhledný obdélník.
 *
 * Poskytuje ho kořenové téma ([rememberBackgroundBlurAllowed]); styly si ho čtou samy.
 */
val LocalBackgroundBlurAllowed = compositionLocalOf { true }

/** Současná volba systému; mění se za běhu (posluchač na Androidu, oznámení na iOS). */
@Composable
expect fun rememberBackgroundBlurAllowed(): Boolean
