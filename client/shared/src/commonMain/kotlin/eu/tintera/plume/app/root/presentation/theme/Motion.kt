package eu.tintera.plume.app.root.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme

/**
 * Vlastní [MotionScheme] – mírně hravý („expressive"), ale ne extrémní bounce.
 * Hodnoty jsou vyladěné na reálném provozu.
 *
 * - **Spatial** (pohyb/velikost): jemný bounce, dampingRatio ~0.6–0.78. Sweet spot příjemného
 *   expressive feelu; pod ~0.6 už je to divoké a rozbíjí to collapse chování M3 search baru.
 * - **Effects** (alpha/barva atd.): BEZ bounce (dampingRatio 1.0). Overshoot u průhlednosti/barvy se
 *   jen ořízne do [0,1] a působí jako škubnutí/bliknutí, ne jako bounce.
 *
 * Ladění: bounciness = dampingRatio (níž = víc bounce), rychlost = stiffness.
 */
private const val SpatialDampingDefault = 0.68f
private const val SpatialDampingFast = 0.78f
private const val SpatialDampingSlow = 0.6f

val AppMotion = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = SpatialDampingDefault,
        stiffness = Spring.StiffnessMediumLow,
    )

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = SpatialDampingFast,
        stiffness = Spring.StiffnessMedium,
    )

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = SpatialDampingSlow,
        stiffness = Spring.StiffnessLow,
    )

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
}
