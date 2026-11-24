# Návrh: hledání jako destinace v panelu seznamu

> **Stav:** hotové (VladimirTintera/plume#35), kroky 1–4. Přechod pilulka → pole je ruční, ne
> container transform se sdíleným prvkem, jak počítal plán — proč, viz „Pole vyrůstá z pilulky"
> v `docs/poznatky/hledani-a-zakladani-chatu.md`. Naživo neověřené: Android a režim Všechny účty.

Hledání v seznamu chatů se dnes rozbaluje z pilulky v hlavičce. Na telefonu je to popup přes
celou obrazovku (`ExpandedFullScreenSearchBar`), na široké ploše malý panel pod pilulkou
(`ExpandedDockedSearchBarWithGap`) a zbytek okna pod závojem. Material ho na střední a velké
šířce doporučuje, ale v panelu seznamu je to špatně:

- **Výsledky se tlačí do plovoucího okénka**, zatímco seznam chatů pod ním pořád leží a ruší.
  Zprávy napříč chaty, filtry, veřejné chaty a doporučení se do něj nevejdou.
- **Je to popup, tedy cizí okno.** Kolem toho narostla řada obcházek: zpět z popupu před
  klávesnicí (`PopupBackAfterKeyboard`), vlastní snackbar hledání (snackbar seznamu by zůstal pod
  popupem), výsledky bez spodního insetu (`LocalDockedResults`), na desktopu popup nad titulkem
  okna (`overTitleBar()`).

**Cíl:** hledání je plnohodnotná navigační destinace v panelu seznamu. Na široké ploše nahradí
seznam chatů na celou výšku a konverzace vpravo zůstane, takže výsledek se otevře vedle
a uživatel může procházet další výsledky, aniž by hledání zavřel (tak to mají Telegram
Desktop, Gmail a Slack). Na telefonu je to obyčejná obrazovka. Pilulka v hlavičce seznamu zůstává
jako vstup a do pole hledání se promění.

**Zvážené a zamítnuté varianty:**
- **Stránka přes celé okno.** Konverzace by při každém výsledku překryla výsledky a pro další
  výsledek by se uživatel musel vracet. Výsledky jsou seznam, takže patří do panelu seznamu.
- **Holá ikona hledání místo pilulky.** Pilulka nese stav spojení (místo lupy znak „bez
  spojení" a věta v nápovědě) a hromádku příběhů, které by musely jinam. Je to navíc standardní
  vstup M3 a dá se z ní udělat pěkný přechod do pole.

## Co dnes je

**Stav hledání žije v `ChatsViewModel`** (`val search = ChatSearch(repository, viewModelScope,
everywhere = true, sponsored = true)`). `ChatSearch` už je samostatná třída nad repository
a scope; používá ji i výběr chatu, přeposílání, nová konverzace a Nastavení. K hledání patří
v `ChatsViewModel` ještě:
- sponzorované chaty: `sponsoredChatSeen`, `sponsoredChatOpened`, `sponsoredChatReportFlow`,
  `reportSponsoredChat`,
- hledání přirozeným jazykem: `naturalSearchAvailable`, `parseSearch`.

**UI je `ChatSearchBar`** (`features/chats/ChatSearchBar.kt`, ~1450 řádků):
- sbalená pilulka (`AppBarWithSearch`) se stavem spojení a hromádkou příběhů,
- rozbalené hledání: docked na široké ploše, celoobrazovkové na telefonu,
- obsah `SearchResults`: návrhy pod prázdným dotazem, sekce chatů, veřejných chatů,
  sponzorovaných chatů a zpráv, filtry zpráv a hashtagů, stránkování zpráv,
- tlačítko hledání přirozeným jazykem (`AiSearchButton`).

**Vstupy do hledání** vedou přes `AppNavigation.openSearch(query, keepConversation)`, který
srovná zásobník Chatů a nastaví `searchRequest`. Seznam chatů si požadavek vyzvedne, až se
složí, a pak ho nuluje (`consumeSearchRequest`). Vstupů je pět:
- pilulka v hlavičce seznamu,
- klávesová zkratka ⌘K / Ctrl+K (`ShellShortcuts`),
- `AppScreen.Search` (vstup zvenku),
- hashtag ťuknutý v konverzaci,
- tlačítko hledání ve sbaleném sloupci, které nejdřív rozbalí panel (`LocalExpandListPane`).

**Víc účtů:** v režimu Všechny účty hledá obrazovka přes `ChatSearch` každého účtu
a `mergedSearchState` výsledky slévá. Návrhy pod prázdným dotazem jsou z účtu obrazovky.

**Po výběru výsledku** se chat zapíše do historie (`recordFound`) a seznam chatů dojede na vybraný
chat (`scrollToChat`), aby byl na široké ploše vlevo vidět.

## Návrh

### Routa a místo v zásobníku

- **`ChatSearchRoute(slot, query = "")`** v roli List, registrovaná v
  `NavSavedStateConfiguration` (jinak spadne až za běhu). Dotaz zvenku (hashtag, budoucí odkazy)
  jde v routě, takže odpadne `searchRequest` / `consumeSearchRequest` a parametry
  `request` / `onRequestHandled`.
- **Metadata jako u seznamu chatů**: šířka panelu z táhla (`Panes.width(listPaneWidth)`,
  `Panes.instantBounds`) a placeholder vpravo. Jinak by panel při otevření hledání skočil na
  výchozí šířku.
- **Hledání leží v zásobníku POD konverzací**: `[Chats, Search, Conversation]`. List-detail scéna
  bere za seznam poslední položku v roli List a za detail to, co leží nad ní; konverzace pod
  hledáním by se vpravo neukázala. `openSearch` proto vloží hledání pod otevřenou konverzaci
  a výsledek se otevře nad hledáním. Otevřený chat tak při hledání zůstane vidět.
- **Zavření hledání odebere jen jeho položku** (šipka zpět v poli, Esc v poli): `[Chats,
  Conversation]`, konverzace zůstane. Systémové zpět jde dál po jedné položce shora, takže
  s otevřenou konverzací napřed zavře ji. Na telefonu se z konverzace otevřené z hledání vrací
  zpět do výsledků (`[Chats, Search, Conversation]` → `[Chats, Search]`).

### ViewModel

- **`ChatSearchViewModel`** v kontejneru účtu (`accountModule`, drží stav účtu — viz
  `docs/navrh-vice-uctu.md`). Vlastní `ChatSearch(everywhere = true, sponsored = true)`
  a převezme z `ChatsViewModel` sponzorované chaty a hledání přirozeným jazykem. Počáteční dotaz
  dostane z routy.
- **`ChatsViewModel` hledání ztratí**, ostatní uživatelé `ChatSearch` se nemění.
- **Klíč ViewModelu obsahuje účet** (`koinViewModel(key = "chat-search-${slot.id}")`), stejně
  jako `NewChatRoute`.
- **Režim Všechny účty:** obrazovka hledání si vezme `ChatSearchViewModel` každého účtu
  společného seznamu (ve scope jeho kontejneru, jako dnes `ChatSource`) a výsledky slije
  stávajícím `mergedSearchState`. Výsledky si nesou účet (`ChatSummary.slot`), otevření jde do
  účtu chatu jako dnes.
- **Dojetí seznamu na vybraný chat** se nesmí ztratit. Dnes ho nastavuje seznam sám, nově ho
  předá navigace: požadavek „dojeď na chat X" ve stavu `AppNavigation` (stav, ne událost, jako
  dnešní `searchRequest`), seznam ho vyzvedne, až se složí.

### Obrazovka hledání

- **`ChatSearchScreen`**: nahoře pole hledání na místě pilulky (šipka zpět, pole, AI, ✕),
  pod ním filtry a `SearchResults` beze změny obsahu. Výsledky jdou na celou výšku panelu.
- **Snackbar má obrazovka vlastní a obyčejný** (nahlášení sponzorovaného chatu). Hledání už není
  popup, takže odpadá důvod pro zvláštní snackbar.
- **Hlavička musí respektovat tlačítka okna na desktopu.** Pole stojí v řádku se semafory, takže
  potřebuje `WindowControlsSpace` / `WindowControlsEndSpace` jako ostatní hlavičky. Je to vlastní
  řádek, ne `TopAppBar`, takže ho `verifyWindowControls` nehlídá a místo se musí dát ručně.
- **Zpět z pole na telefonu:** první zpět schová klávesnici, druhé hledání zavře. To umí
  obrazovka sama (systémová klávesnice si zpět bere jako první), `PopupBackAfterKeyboard` tu
  potřeba není.

### Pilulka v hlavičce seznamu

- **Pilulka se nerozbaluje, jen naviguje.** `AppBarWithSearch` s vlastním stavem
  `SearchBarState` nahradí pilulka-tlačítko, která vypadá stejně: lupa nebo znak spojení, nápověda
  se stavem spojení, hromádka příběhů na konci, avatar účtu vedle. Ťuknutí otevře
  `ChatSearchRoute`. Odjíždění při scrollu a přivolání při výpadku spojení zůstává.
- **Odpadne:** `ExpandedDockedSearchBarWithGap`, `ExpandedFullScreenSearchBar`, `LocalDockedResults`,
  `PopupBackAfterKeyboard` v hledání, `searchSnackbarHostState` v seznamu chatů a `overTitleBar()`
  (používá ho jen docked hledání).

### Přechod

- **Pilulka → pole je container transform** (`sharedBounds`, stejná technika jako otevření
  chatu z řádku, `ChatContainer`): pilulka se roztáhne na pole na témže místě a seznam chatů
  se prolne do výsledků (fade through). Zpět opačně.
- **Šířka panelu se při přechodu nemění**, takže tu nehrozí potíž ze sbaleného seznamu: sdílený
  prvek tam letěl na cíl z rozložení „napřed" a v něm měl panel jinou šířku (viz
  `docs/poznatky/navigace.md`, „Mezi plným seznamem a sbaleným sloupcem se přelétá"). Kdyby se
  i tady ukázal skok na konci, je po ruce ruční let (`collapseFlight`).
- **Vedle panelů je dnes sdílení vypnuté** (`LocalPanesSideBySide`), protože obě strany páru jsou
  vidět naráz. Tady pilulka a pole naráz vidět nejsou (hledání seznam v panelu nahradí), takže
  pár smí letět i vedle panelů, jako fotka do prohlížeče (`besidePanes`).
- **Ze sbaleného sloupce** se panel napřed rozbalí a hledání se otevře, až je pilulka vidět
  (dnešní pořadí). Přechod pak jde z pilulky jako jindy.

## Postup

Každý krok je samostatný PR, aplikace mezi nimi funguje.

1. **Stav do `ChatSearchViewModel`** bez změny UI: seznam chatů ho bere z nového ViewModelu
   místo `ChatsViewModel.search`. Testy hledání (`ChatSearchTest`, `ChatSearchSuggestionsTest`)
   zůstávají, přibude test ViewModelu (počáteční dotaz, sponzorované chaty).
2. **Routa a obrazovka hledání**, zatím vedle starého hledání: `ChatSearchRoute`,
   `ChatSearchScreen` (pole, filtry, výsledky, snackbar), `openSearch` vloží routu pod
   konverzaci, hashtag z konverzace jde v routě, dojetí seznamu přes navigaci. Pilulka už
   naviguje; docked a celoobrazovkové hledání se vypnou.
3. **Úklid**: pryč docked a celoobrazovkové hledání, `LocalDockedResults`,
   `PopupBackAfterKeyboard` v hledání, `searchRequest`, `overTitleBar()` a poznatky o nich
   v `docs/poznatky/hledani-a-zakladani-chatu.md`.
4. **Přechod pilulka → pole** (container transform, fade through) a ověření na desktopu
   (dva panely, sbalený sloupec, tlačítka okna na Macu i v náhledu pro Windows), na telefonu
   a na Foldu (rozložení a složení s otevřeným hledáním).
5. **Režim Všechny účty** v nové obrazovce, pokud se nevejde už do kroku 2.

## Otevřené otázky

- **Esc s otevřenou konverzací vedle hledání.** Návrh: Esc v poli hledání (fokus v poli) zavře
  hledání, Esc jinde jde po zásobníku a napřed zavře konverzaci. Jestli to nebude matoucí, se
  pozná až v ruce.
- **Rozšířit panel při hledání?** Výsledky zpráv mají delší ukázky a 360 dp je na ně těsno.
  Dočasné rozšíření panelu (a návrat po zavření) by pomohlo, ale přidá pohyb navíc. Až po
  kroku 4, podle toho, jak to bude vypadat.
- **Má hledání přežít zavření?** Dnes se dotaz a filtr se sbalením mažou. S destinací zmizí stav
  s položkou zásobníku, takže se chování nezmění. Kdyby se hodilo „vrátit se k poslednímu
  hledání", šlo by to přes historii hledání v TDLib, ne přes držení stavu.
