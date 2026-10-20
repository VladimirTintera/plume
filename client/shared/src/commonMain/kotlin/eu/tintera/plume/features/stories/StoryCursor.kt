package eu.tintera.plume.features.stories

import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.StoryListKind

/** Autoři jednoho seznamu příběhů v pořadí TDLib: dvojice (order, id chatu) sestupně. */
fun storyListOrder(all: Collection<ActiveStories>, list: StoryListKind): List<ActiveStories> =
    all.filter { it.list == list }
        .sortedWith(compareByDescending<ActiveStories> { it.order }.thenByDescending { it.chatId })

/**
 * Koho prohlížeč otevřený u [startChatId] projde tahem do strany: celý seznam, ve kterém autor je
 * (z řádku chatu tak jde přejít na další lidi jako z hromádky), a když v žádném není, jen jeho.
 */
fun viewerPosters(all: Map<Long, ActiveStories>, startChatId: Long): List<ActiveStories> {
    val start = all[startChatId] ?: return emptyList()
    val list = start.list ?: return listOf(start)
    return storyListOrder(all.values, list)
}

data class StoryKey(val posterChatId: Long, val storyId: Int)

/**
 * Stav prohlížeče. **Autoři i jejich příběhy jsou zmražení při otevření**: zhlédnutím se mění
 * pořadí seznamu (TDLib posouvá zhlédnuté dozadu) a živý seznam by uživateli ujížděl pod rukama.
 */
data class StoriesState(
    val posters: List<ActiveStories> = emptyList(),
    /** Index autora, který je na obrazovce. */
    val poster: Int = 0,
    /** Index příběhu u každého autora (podle id chatu); kdo tu není, začíná [ActiveStories.firstToShow]. */
    val positions: Map<Long, Int> = emptyMap(),
    /** Načtený obsah příběhů. */
    val pages: Map<StoryKey, MessageMedia.Story> = emptyMap(),
    /** Roste s každým „pusť znovu od začátku" (zpět u prvního příběhu prvního autora). */
    val restart: Int = 0,
    val closed: Boolean = false,
) {
    fun storyIndex(posterIndex: Int): Int {
        val poster = posters.getOrNull(posterIndex) ?: return 0
        return (positions[poster.chatId] ?: poster.firstToShow).coerceIn(0, (poster.stories.size - 1).coerceAtLeast(0))
    }

    fun keyAt(posterIndex: Int): StoryKey? {
        val poster = posters.getOrNull(posterIndex) ?: return null
        val story = poster.stories.getOrNull(storyIndex(posterIndex)) ?: return null
        return StoryKey(poster.chatId, story.id)
    }

    val current: StoryKey? get() = keyAt(poster)
}

/** Další příběh: u téhož autora, pak další autor, a za posledním se prohlížeč zavře. */
fun StoriesState.next(): StoriesState {
    val author = posters.getOrNull(poster) ?: return copy(closed = true)
    val index = storyIndex(poster)
    return when {
        index + 1 < author.stories.size -> copy(positions = positions + (author.chatId to index + 1))
        poster + 1 < posters.size -> copy(poster = poster + 1)
        else -> copy(closed = true)
    }
}

/**
 * Předchozí příběh: u téhož autora, pak předchozí autor (tam, kde se u něj skončilo), a u úplně
 * prvního se příběh pustí znovu.
 */
fun StoriesState.previous(): StoriesState {
    val author = posters.getOrNull(poster) ?: return this
    val index = storyIndex(poster)
    return when {
        index > 0 -> copy(positions = positions + (author.chatId to index - 1))
        poster > 0 -> copy(poster = poster - 1)
        else -> copy(restart = restart + 1)
    }
}

/** Tahem do strany na jiného autora. */
fun StoriesState.showPoster(index: Int): StoriesState =
    if (index == poster || index !in posters.indices) this else copy(poster = index)
