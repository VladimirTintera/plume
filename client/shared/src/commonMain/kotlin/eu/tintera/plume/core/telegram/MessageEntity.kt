package eu.tintera.plume.core.telegram

/**
 * Formátovaný úsek textu zprávy.
 *
 * Telegram neposílá text s markupem, ale holý řetězec a k němu seznam rozsahů — odkaz, tučné,
 * kód. Bez nich je z odkazu jen text, na který se nedá kliknout.
 *
 * **[offset] i [length] jsou v UTF-16 jednotkách**, tedy přesně v tom, čím indexuje Kotlin
 * `String`. Žádný přepočet se nedělá; zároveň se s nimi nesmí míchat počítání shluků emoji
 * (`emojiClusterCount`), které jde po grafémech.
 *
 * Rozsahy se **můžou vnořovat** (tučný text uvnitř odkazu), ale nesmí se křížit. Vykreslení s tím
 * počítá tak, že každý rozsah jen přidá svůj styl na daný úsek — vnoření tím vznikne samo.
 */
data class MessageEntity(
    val offset: Int,
    val length: Int,
    val type: MessageEntityType,
)

/**
 * Co s úsekem textu udělat.
 *
 * Záměrně **není 1:1 s TDLib**. Ten rozlišuje `TextEntityTypeUrl` (odkaz je rovnou v textu)
 * a `TextEntityTypeTextUrl` (text je popisek, adresa je zvlášť); pro vykreslení je to totéž,
 * takže obojí skončí jako [Link]. Stejně tak e-mail a telefon — i z nich je otevíratelná adresa,
 * jen s jiným schématem.
 *
 * Typy, které neumíme dovést do konce, se sem **nemapují vůbec** a text po nich zůstane obyčejný.
 * Týká se to cashtagů: obarvit je umíme, ale kliknutí by nemělo kam vést. Modrý text, po kterém
 * se nic nestane, je horší než text černý.
 */
sealed interface MessageEntityType {

    /**
     * Otevíratelná adresa.
     *
     * [url] je vždycky úplná i se schématem, takže se předá do `UrlOpener` bez dalšího zkoumání.
     * U odkazu psaného bez schématu (`example.com`) ho doplní mapování z TDLib.
     */
    data class Link(val url: String) : MessageEntityType

    /**
     * `@jméno` přímo v textu. Vede na chat uvnitř aplikace (`searchPublicChat`), ne do
     * prohlížeče. [username] je bez zavináče.
     */
    data class Mention(val username: String) : MessageEntityType

    /**
     * Zmínka uživatele bez veřejného jména — text je jméno, uživatel je jen [userId]. Vede na
     * soukromý chat s ním.
     */
    data class MentionUser(val userId: Long) : MessageEntityType

    /** `#tag` — vede na hledání zpráv s tím tagem. [tag] je i s mřížkou, tak se i hledá. */
    data class Hashtag(val tag: String) : MessageEntityType

    /**
     * Příkaz bota (`/start`, ve skupině i `/start@bot`) — ťuknutí ho pošle do chatu, jako
     * u oficiálních klientů. [command] je celý text úseku i s lomítkem, tak se i posílá.
     */
    data class BotCommand(val command: String) : MessageEntityType

    /**
     * Čas v médiu („1:23"): ťuknutí pustí video, hlasovku, skladbu nebo kolečko od [seconds].
     * Médium je v téže zprávě, nebo ve zprávě, na kterou odpovídá
     * ([ChatMessage.hasTimestampedMedia]).
     */
    data class MediaTimestamp(val seconds: Int) : MessageEntityType

    data object Bold : MessageEntityType

    data object Italic : MessageEntityType

    data object Underline : MessageEntityType

    data object Strikethrough : MessageEntityType

    /** Kód — inline i blok. Rozdíl je jen v tom, že blok bývá na vlastních řádcích. */
    data object Code : MessageEntityType

    /**
     * Skrytý text. Odkrývá se ťuknutím, do té doby musí být **opravdu nečitelný** — obarvit ho
     * jinak by obsah vyzradilo, což je přesně to, čemu se spoiler vyhýbá.
     */
    data object Spoiler : MessageEntityType

    /**
     * Vlastní emoji: úsek textu (náhradní emoji) se nahradí obrázkem [id]. Offsety zůstávají
     * nad původním textem, takže zvýraznění, spoilery i kopírování fungují beze změny.
     */
    data class CustomEmoji(val id: Long) : MessageEntityType

    /**
     * Citace uvnitř textu — úsek, který pisatel odněkud převzal.
     *
     * Kreslí se jako tónovaný blok za textem, ne proužkem vlevo: stejnou řečí mluví citace nad
     * odpovědí i popis webu u náhledu odkazu (viz `MessageBodyScope.insetColor`). Bez toho byl
     * převzatý text k nerozeznání od vlastních slov pisatele.
     *
     * [expandable] je citace, kterou Telegram v ostatních klientech sbaluje na pár řádků
     * („Zobrazit víc"). Zatím se kreslí celá — příznak se drží, aby se sbalení dalo doplnit.
     */
    data class Quote(val expandable: Boolean) : MessageEntityType
}
