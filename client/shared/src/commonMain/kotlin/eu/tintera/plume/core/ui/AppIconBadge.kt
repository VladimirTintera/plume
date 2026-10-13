package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Předá barvy motivu odznaku nepřečtených na ikoně aplikace, který se kreslí mimo Compose.
 * Jen desktop: overlay na tlačítku okna ve Windows kreslíme sami (`DesktopTaskbar`), barvy
 * `error` / `onError` jako odznak v aplikaci. Na Androidu, iOS a v Docku kreslí odznak systém.
 */
@Composable
expect fun AppIconBadgeColors()
