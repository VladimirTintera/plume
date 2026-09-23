package eu.tintera.plume.core.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Slovník hmatové odezvy pro celou aplikaci.
 *
 * Význam má nést typ, ne vkus toho, kdo zrovna píše obrazovku — stejná úvaha jako u [ShapeMorphs]
 * a `AppMotion`. `HapticFeedbackType` rozseté po obrazovkách by znamenalo, že se tatáž událost
 * (přepnutí, potvrzení) v každé části aplikace ozve jinak.
 *
 * **Jakmile vibruje všechno, přestane cokoli znamenat.** Platí tu totéž pravidlo jako u tvarů:
 * odezva patří k akci, kterou udělal UŽIVATEL, a jen tam, kde se ťuknutím něco STALO. Příchozí
 * zpráva, dotažená stránka historie ani změna stavu spojení nevibrují — od upozornění na cizí
 * akci jsou notifikace.
 *
 * **Většinu dlouhých stisků dělá platforma sama.** `combinedClickable` z Foundation volá
 * [HapticFeedbackType.LongPress] uvnitř, takže podržení zprávy, chatu v seznamu i dlaždice
 * vibruje bez našeho přičinění a ruční volání navíc by odezvu ZDVOJILO. [longPress] patří jen
 * tam, kde si gesto detekujeme sami (odkaz v textu, prahy tahů).
 *
 * **iOS je hrubší, než se ze seznamu typů zdá.** `CupertinoHapticFeedback` mapuje celou sadu na
 * čtyři generátory: [confirm] a [reject] na notification, [longPress] na medium impact,
 * [threshold] i [toggle] na light impact a [tick] na selection. Zapnutí a vypnutí přepínače
 * tedy na iPhonu nejde rozeznat — spolehnout se, že iOS odliší to, co Android, nelze.
 *
 * **Na Androidu část typů na starších verzích není.** `Confirm` a `Reject` jsou od API 30,
 * `ToggleOn`, `ToggleOff`, `GestureThresholdActivate` a `SegmentTick` od API 34, zatímco `minSdk`
 * je 26. Compose je posílá přes `HapticFeedbackConstantsCompat`, který je nahradí bližším typem,
 * nebo neudělá nic — ověřovat se tedy musí i na starším emulátoru.
 *
 * Vlastní přepínač v Nastavení není: vypnout hmatovou odezvu jde v systému a `performHapticFeedback`
 * to respektuje. Oprávnění `VIBRATE` potřeba není, odezva jde přes view, ne přes `Vibrator`.
 */
object Haptics

/** Gesto právě překročilo práh, po kterém se při puštění něco stane (archivace tahem, odpověď). */
fun HapticFeedback.threshold() = performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

/** Akce uživatele se povedla: odeslaná zpráva, hotová nahrávka, přidaná samolepka do oblíbených. */
fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.Confirm)

/** Akce se nepovedla, nebo ji nešlo udělat: odmítnuté odeslání, zahozená nahrávka. */
fun HapticFeedback.reject() = performHapticFeedback(HapticFeedbackType.Reject)

/** Přepnutí stavu — přepínač, reakce, položka výběru. Zapnutí a vypnutí se liší (na Androidu). */
fun HapticFeedback.toggle(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

/** Tik při procházení po dílcích: další výsledek hledání, další stránka, další díl posuvníku. */
fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)

/**
 * Dlouhý stisk, který si obrazovka detekuje SAMA.
 *
 * U `combinedClickable` se nevolá — ten ho dělá sám a odezva by byla dvakrát.
 */
fun HapticFeedback.longPress() = performHapticFeedback(HapticFeedbackType.LongPress)
