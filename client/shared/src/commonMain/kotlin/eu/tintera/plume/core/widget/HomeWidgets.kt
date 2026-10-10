package eu.tintera.plume.core.widget

/**
 * Widget s chaty na ploše z aplikace: požádá systém, ať ho umístí (na Androidu dialog launcheru
 * „Přidat na plochu"). Hledat ho v nabídce widgetů launcheru nikdo nemusí.
 *
 * Jen Android; na iOS widget není (WidgetKit by potřeboval vlastní rozšíření) a položka se
 * v Nastavení nenabízí.
 */
interface HomeWidgets {
    /** Umí launcher widget přidat na žádost aplikace? */
    val canPin: Boolean

    /** Požádá o umístění widgetu s chaty; potvrzuje uživatel v dialogu launcheru. */
    fun pinChats()
}
