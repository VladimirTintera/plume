# Konverzace — architektura seznamu zpráv

Cílový stav a postup přestavby. Rozpracované kroky jsou v [roadmap-konverzace.md](roadmap-konverzace.md);
hotová poučení patří do `CLAUDE.md`.

## Proč

Dřív se o tom, co se v konverzaci nakreslí, rozhodovalo na třech místech zároveň:

- v `itemsIndexed` obrazovky (sousedé, předěly, avatar, odznak přečtení, citace, let) — uvnitř
  kompozice, takže netestovatelně,
- v čistých funkcích `MessageRows.kt` (`showsBareMedia`, `showsLinkPreviewCard`),
- v `MessageBubble` s 27 parametry, která sama řešila velká emoji, samolepky bez bubliny, tvar,
  barvy, popisek pod médiem, tři větve textu a náhledu, gesto odpovědi, reakce, doručení
  i ukotvení nabídky.

Každé nové rozvržení (třeba náhled odkazu) tak znamenalo další příznak `bareX` propsaný do tvaru,
odsazení, zarovnání, popisku i reakcí v jedné funkci.

## Cílový stav

```
ChatMessage (repozitář, core/telegram)
   │  čistá funkce conversationItems(...) – testovaná bez Compose
   ▼
List<ConversationItem>        ← klíč, contentType, vlastní data, vlastní akce
   │
   ▼
LazyColumn: with(item) { Content(scope) }     ← žádný `when`
```

### Blok nese odpovědnost sám

`ConversationItem` je UI model (ne doménová data): nese svoje data a **sám se vykreslí**.
Každý druh bloku má vlastní soubor s daty i UI pohromadě.

```kotlin
@Immutable
interface ConversationItem {
    val key: Any
    val contentType: Any get() = this::class
    fun containsMessage(messageId: Long): Boolean = false
    // Nad LazyItemScope, aby si blok sám zvolil `animateItem`.
    @Composable fun LazyItemScope.Content(scope: ConversationItemScope)
}
```

- **Jediný `when` je v builderu** — rozhodnutí, JAKÝ blok ze zprávy vznikne. To je rozhodnutí
  nad daty, patří na jedno místo a testuje se ověřením typu a dat bloku.
- **Předěly, sponzorovaná karta a načítání jsou samostatné bloky.** Builder sousedy zná, takže
  předěl už nemusí být kreslený uvnitř zprávy.
- **Zprávy sdílejí rám, obsah si kreslí samy.** `MessageItem : ConversationItem` implementuje
  `Content` jednou přes `MessageFrame` (avatar, tah na odpověď, zvýraznění, reakce přes nároží,
  odznak doručení, kotva nabídky) a nechá blok nakreslit `Body`. Jestli má zpráva bublinu, není
  příznak: `Body` buď použije sdílený kus `Bubble { }`, nebo ne.
- **Lambdy v datech bloku nejsou.** Rozbily by `equals` a s ním přeskakování položek. Obsluhu
  a rychle se měnící hodnoty (pozice hlasovky, průběh stahování, odkrývání časů) dostane blok
  přes `ConversationItemScope`.
- **Akce jsou data.** Blok nese `actions` (`onTap` + kandidáty do nabídky jako `MessageAction`);
  serverová práva (`getMessageProperties`) se dál načítají až při otevření nabídky a nabídka
  ukáže průnik. Do ViewModelu vede jediný vstup `onAction(key, action)`.

### Proč se builder volá v kompozici, ne ve ViewModelu

Nová zpráva dole se ukotvuje přes `requestScrollToItem(0)` **v téže kompozici**, ve které se
objeví (viz `CLAUDE.md`, „Odesílání a scrollování"). Tok položek odvozený ve ViewModelu by šel
o dispatch za `state` a ukotvení by mířilo na starý seznam. Builder je proto čistá funkce volaná
přes `remember(vstupy)` — testovatelná stejně, jen synchronní se stavem.

## Postup

1. **Builder a bloky seznamu.** — HOTOVO. `conversationItems()` + `ConversationItemsTest`;
   předěly dne a nepřečtených, servisní oznámení, sponzorovaná karta a načítání jsou vlastní
   bloky. Zprávy jsou zatím jeden `BubbleMessageItem`, který obaluje dosavadní `MessageBubble`
   s hotovými daty (sousedé, avatar, odznak, citace spočítané builderem).
2. **Rozpad `MessageBubble`.** — HOTOVO. `MessageFrame` + bloky `BubbleMessageItem`,
   `LargeEmojiMessageItem`, `StickerMessageItem`, `MediaMessageItem`, `LinkPreviewMessageItem`;
   o druhu rozhoduje `messageItem()` v builderu. Sdílené kusy (přílohy, album, náhledy, citace,
   předěly, doručení) se přestěhovaly z obrazovky do `features/conversation/items/`.
   Obrazovka spadla z 3 671 na ~1 540 řádků. `showsBareMedia` / `showsLinkPreviewCard` zůstaly
   v `MessageRows.kt` kvůli svým testům; volá je jen builder.
3. **`MessageAction` a `onAction`.** — HOTOVO. Akce jsou data (`MessageAction`), položky nabídky
   podmnožina `MessageMenuAction`. Blok nese kandidáty do nabídky (`menuActions()` v builderu,
   podle toho, co zpráva JE) a akci na ťuknutí; každá položka nabídky sama řekne, jestli ji
   server dovolil a jak vypadá (`presentation`). Do ViewModelu vede jediný `onAction`;
   navigaci a sheet sady samolepek vyřizuje obrazovka. `MessageMenuHandlers` a patnáct metod
   scope zmizely, metody akcí ve ViewModelu jsou `private`.
4. **Náhled odkazu pořádně.** — HOTOVO. Jedna souvislá karta bez spár, text v barvě bubliny,
   popis webu `tertiaryContainer`, bez vnitřního odsazení. Změna se týkala jen souboru bloku
   (a úklidu `BareLinkPreview`, který nikdo jiný nepoužíval) — přesně to, kvůli čemu přestavba
   vznikla.

Později, mimo tuhle přestavbu: rozdělit `ConversationState` na data a přechodný stav UI
a ViewModel na menší části (připnuté, koncept, hlasovky, přílohy).
