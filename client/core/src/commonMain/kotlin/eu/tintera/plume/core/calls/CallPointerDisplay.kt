package eu.tintera.plume.core.calls

/**
 * Kde se ukáže ukazovátko protistrany, když sdílíme obrazovku: značka přes všechny aplikace
 * (Android: okno `TYPE_APPLICATION_OVERLAY`). Sdílí se totiž skoro vždycky jiná aplikace než
 * Plume, takže značka v Plume by nebyla vidět. Do sdíleného obrazu se dostane taky, takže ji
 * vidí i ten, kdo ukazuje.
 */
interface CallPointerDisplay {
    /** Smí se kreslit přes jiné aplikace (Android: „Zobrazení přes jiné aplikace"). */
    val available: Boolean

    /** Značka na místě [x], [y] (0–1 ze šířky a výšky obrazovky); [down] = prst je dole. */
    fun show(x: Float, y: Float, down: Boolean)

    /** Pryč se značkou (konec sdílení, konec hovoru). */
    fun hide()
}
