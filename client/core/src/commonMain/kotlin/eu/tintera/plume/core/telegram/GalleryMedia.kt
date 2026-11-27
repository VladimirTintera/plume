package eu.tintera.plume.core.telegram

/** Fotka nebo video v galerii přes všechny chaty: z kterého chatu a zpráva s médiem. */
data class GalleryItem(val chatId: Long, val message: ChatMessage)

/**
 * Stránka galerie ([ConversationRepository.galleryMedia]). [nextOffset] `null` = konec. Stránka
 * smí být prázdná i uprostřed (samá mizející média) — konec říká jen [nextOffset].
 */
data class GalleryPage(val items: List<GalleryItem>, val nextOffset: String?)
