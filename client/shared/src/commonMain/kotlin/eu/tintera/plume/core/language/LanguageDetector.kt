package eu.tintera.plume.core.language

/**
 * Rozpoznání jazyka textu v zařízení — Android `TextClassifier` (od Androidu 10), iOS
 * `NLLanguageRecognizer`. Nic se neposílá a nic se nestahuje: obojí má model v systému.
 *
 * `core/language` je hranice jako `core/camera`: `android.view.textclassifier`
 * a `platform.NaturalLanguage` smí být jen tady.
 */
fun interface LanguageDetector {
    /**
     * Jazyk [text]u jako kód BCP 47 („cs", „en"), když si je platforma jistá aspoň na
     * [MIN_LANGUAGE_CONFIDENCE]; jinak `null` — i když rozpoznání na zařízení není.
     */
    suspend fun detect(text: String): String?
}

/**
 * Pod tím se jazyk nebere jako rozpoznaný. Chybné „už je to v tvém jazyce" schová překlad,
 * který uživatel chtěl, kdežto chybné „cizí" jen nechá položku navíc — proto vysoko, ale ne
 * nad 0,8: model Androidu dal jasně české větě „Dneska je krásné počasí, pojďme se projít do
 * parku." češtinu jen na 0,76 (zbytek slovenština). Slovenskou a polskou větu přitom pozná
 * na 0,99 — práh 0,7 tedy schová překlad jen u textu, který je opravdu v cílovém jazyce.
 */
const val MIN_LANGUAGE_CONFIDENCE = 0.7f
