package eu.tintera.plume.core.share

/**
 * iOS zatím nic. Obdobou Direct Share je darování `INSendMessageIntent` (Intents framework)
 * a chaty se pak nabízejí v řádku kontaktů nahoře v nabídce sdílení — to ale potřebuje
 * Share Extension, která sdílení přijme (viz roadmapa).
 */
class IosShareTargetPublisher : ShareTargetPublisher {
    override fun publish(chatId: Long, title: String, avatarPath: String?) = Unit
}
