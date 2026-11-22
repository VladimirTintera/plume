package eu.tintera.plume.core.calls

import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_quick_reply_later
import eu.tintera.plume.resources.call_quick_reply_right_back
import eu.tintera.plume.resources.call_quick_reply_text_me
import org.jetbrains.compose.resources.StringResource

/**
 * Hotové odpovědi pro odmítnutí hovoru zprávou — na zvonící obrazovce (`CallQuickReplyButton`)
 * i jako volby u akce v notifikaci hovoru. Jako systémový telefon: krátká věta místo
 * vyzvánění do ztracena. V `core`, protože je potřebuje i presenter notifikací.
 */
val CallQuickReplies: List<StringResource> = listOf(
    Res.string.call_quick_reply_text_me,
    Res.string.call_quick_reply_right_back,
    Res.string.call_quick_reply_later,
)
