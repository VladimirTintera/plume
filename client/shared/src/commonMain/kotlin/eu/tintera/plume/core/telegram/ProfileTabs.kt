package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ProfileTab
import dev.g000sha256.tdl.dto.ProfileTabFiles
import dev.g000sha256.tdl.dto.ProfileTabGifs
import dev.g000sha256.tdl.dto.ProfileTabGifts
import dev.g000sha256.tdl.dto.ProfileTabLinks
import dev.g000sha256.tdl.dto.ProfileTabMedia
import dev.g000sha256.tdl.dto.ProfileTabMusic
import dev.g000sha256.tdl.dto.ProfileTabPosts
import dev.g000sha256.tdl.dto.ProfileTabVoice

internal fun ProfileTab.toDomain(): ProfileMainTab? = when (this) {
    is ProfileTabPosts -> ProfileMainTab.Posts
    is ProfileTabGifts -> ProfileMainTab.Gifts
    is ProfileTabMedia -> ProfileMainTab.Media
    is ProfileTabFiles -> ProfileMainTab.Files
    is ProfileTabLinks -> ProfileMainTab.Links
    is ProfileTabMusic -> ProfileMainTab.Music
    is ProfileTabVoice -> ProfileMainTab.Voice
    is ProfileTabGifs -> ProfileMainTab.Gifs
    // Druh z novější TDLib: profil se otevře, jak by se otevřel bez volby.
    else -> null
}

internal fun ProfileMainTab.toTdl(): ProfileTab = when (this) {
    ProfileMainTab.Posts -> ProfileTabPosts()
    ProfileMainTab.Gifts -> ProfileTabGifts()
    ProfileMainTab.Media -> ProfileTabMedia()
    ProfileMainTab.Files -> ProfileTabFiles()
    ProfileMainTab.Links -> ProfileTabLinks()
    ProfileMainTab.Music -> ProfileTabMusic()
    ProfileMainTab.Voice -> ProfileTabVoice()
    ProfileMainTab.Gifs -> ProfileTabGifs()
}
