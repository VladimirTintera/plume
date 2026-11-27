# Android TV

Samostatná aplikace pro televize (`client/tvApp`). Plán a co zbývá: `docs/roadmap.md`, „Android TV".

## Skladba

- **Stojí jen na `:client:core`** (a přes něj `:client:resources`), stejně jako hodinky
  (`docs/poznatky/wear-os.md`). Vrstvu kolem TDLib dostane celou z `coreModule()`
  (`TvApplication`), UI telefonu (`shared`, `appModule()`) ani jeho knihovny do APK nejdou.
  TDLib se startuje s procesem a `ConnectionController` obnovuje spojení po návratu z pozadí.
- **TDLib na televizi běží bez úprav**: Android TV je obyčejný Android a wrapper balí nativní
  knihovnu pro všechna potřebná ABI.
- **ABI `arm64-v8a`, `armeabi-v7a` a `x86_64`.** Velká část televizí a přehrávačů (Chromecast
  s Google TV, spousta televizorů) jede 32bitový systém i na 64bitovém procesoru — mají málo
  paměti. Bez `armeabi-v7a` by se na ně aplikace vůbec nenainstalovala. `x86_64` je kvůli
  emulátoru Android TV na Intelu; TDLib má na ABI přes 15 MB.
- **Hook `TdlibJsonRepair`** dává plugin `plume.tdlib-android` jako u telefonu a hodinek
  (CLAUDE.md: každý vstupní bod s TDLib).
- **`applicationId` je stejné jako telefon** (`eu.tintera.plume`), aby šla televize v Play do
  téhož záznamu. Třídy mají vlastní balíček `eu.tintera.plume.tv`. **`versionCode` je posunutý
  o 200 000 000** (hodinky 100 000 000): Play nevezme dva bundly se stejným číslem.
- **Telefon televizní bundl nedostane**: manifest chce `android.software.leanback`
  (`required="true"`) a dotyk má `required="false"`. Vyšší `versionCode` tedy telefonní
  aplikaci nezastíní — stejná úvaha jako `uses-feature watch` u hodinek.
- **Banner v launcheru** (`drawable-xhdpi/tv_banner.png`, 320 × 180 dp) se generuje
  z `play/tools/icon.py` (`python3 play/tools/icon.py tv`) z téže geometrie pírka jako ikona.
  Google chce v banneru jméno aplikace; vektor text neumí, proto PNG se jménem v Bricolage.
- **Chat se intentem (`eu.tintera.plume.tv.CHAT_ID`) otevře jen v ladicím buildu**
  (`FLAG_DEBUGGABLE`). Aktivita je exportovaná a extra by mohla poslat kterákoli aplikace
  v televizi; otevřením se zprávy označí jako přečtené. Až chat bude otevírat notifikace nebo
  karta Google TV, půjde to přes `PendingIntent` a neexportovanou cestu.
- **Složka obnovená z uloženého stavu se ověří**: když se do pěti vteřin neobjeví mezi složkami
  účtu (smazaná v telefonu, než se proces obnovil), vrátí se hlavní seznam. Jinak záložky
  zvýraznily „Všechny chaty" a seznam zůstal prázdný.
- **Aktivita má `LEANBACK_LAUNCHER` i `LAUNCHER`.** První dává banner v launcheru televize,
  druhý ji ukáže v „Aplikacích" Google TV a dá se spustit `am start` i na emulátoru bez
  televizního launcheru.

## Skladba obrazovky (podle návodů Google)

Podle [TV components](https://developer.android.com/design/ui/tv/guides/components) a
[Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), procházeno
27. 11. 2026. Co z nich plyne pro Plume:

- **Seznam chatů je svislý dvouřádkový seznam s kulatými avatary** (`ListItem`). Přesně to
  Google doporučuje na kolekci, ze které se vybírá jedna položka (lidé a entity kulatě).
  **Immersive list** (řada karet a velký náhled na pozadí, který sleduje fokus) je podle návodu
  pro pár vybraných položek (novinky, populární), ne pro dlouhý seznam — na chaty se nehodí.
  Karty v řadách jsou pro vizuální katalog (objevování kanálů, feed příspěvků).
- **Z immersive listu si seznam bere náhled**: vpravo zaostřený chat (`ChatPreview`), jeho
  poslední fotka nebo video přes pozadí celé obrazovky (prolíná se i pod záložky) a poslední
  zprávy. Obraz je zarovnaný doprava nahoru (Google: hlavní motiv vpravo nahoře) a zleva
  a zespodu ho kryje přechod do plochy, takže text zůstává čitelný. Načítá se se zpožděním
  300 ms (`getChatHistory`, 20 zpráv) — rychlé projíždění šipkou nemá číst historii každého
  řádku. `getChatHistory` chat neotevírá ani nehlásí přečtení.
- **Složky jsou záložky nahoře** (`TabRow` s pilulkou), ne lišta: záložky jsou podle návodu
  pro rovnocenné sekce jedné obrazovky a smí jich být kolik je potřeba. `NavigationDrawer` je
  „páteř" aplikace jen pro hlavní cíle (pět až šest, s ikonami) — první verze ho měla na složky
  a s víc složkami by přetekl. Lišta se vrátí, až budou hlavní cíle (Chaty, Hledání,
  Nastavení). Záložka se vybírá už zaostřením (`onFocus`), jako všude na TV.
- **Bezpečné okraje 58 dp po stranách a 28 dp nahoře a dole** (`SAFE_HORIZONTAL`,
  `SAFE_VERTICAL`; 5 % z 960 × 540 dp). Návod má na téže stránce i 48 / 27 dp; drží se
  přísnější varianta. Pozadí okraje nerespektuje (Google: „fill the full screen").
- **Návrh v 960 × 540 dp** (televize 1080p má hustotu 320), mřížka 12 sloupců po 52 dp
  s mezerou 20 dp; šířky karet pro N vedle sebe: 1 = 844, 2 = 412, 3 = 268, 4 = 196 dp.
- **Přehrávač návod nepopisuje** (stránka o médiích mezi komponentami není); ovládání je
  podle systémových přehrávačů, viz „Video".

## UI

- **Compose for TV (`androidx.tv:tv-material` 1.1)**, ne M3 z telefonu: televize se ovládá
  fokusem a šipkami ovladače, komponenty telefonu stojí na dotyku (tvar měnící se při stisku,
  tahy). Zaostřený prvek se v tv-material zvětší a změní plochu — fokus je na televizi jediný
  „kurzor", který uživatel vidí, takže každý ovládací prvek musí jít zaostřit a být vidět, kde
  fokus je.
- **Seznamy jsou z běžného `foundation`** (`LazyColumn`), ne `androidx.tv.foundation`: lazy
  seznamy pro TV jsou zastaralé, běžné od Compose 1.7 fokus a posun za ním umí samy.
- **Jediná závislost na M3 telefonu je `LoadingIndicator`** (`libs.compose.material3`):
  tv-material indikátor čekání nemá. Barvu dostává výslovně z tv schématu — výchozí by si vzal
  z `MaterialTheme` telefonu, který tu není nastavený.
- **Téma je vždy tmavé** (`darkColorScheme()` z tv-material): světlá plocha v tmavém pokoji
  oslňuje a Google ji na TV nedoporučuje. Barvy z tapety televize nemá.
- **Navigace jsou dvě úrovně ve stavu** (`TvNavigation`): seznam a otevřená konverzace, zpět
  tlačítkem ovladače (`BackHandler`). Knihovna navigace by na dvě obrazovky jen přibyla.
- **Seznam po návratu z konverzace stojí, kde stál, a fokus je na chatu, ze kterého uživatel
  odešel.** `LazyListState` (zvlášť pro každou složku) a poslední otevřený chat proto drží
  navigace, ne obrazovka — seznam s konverzací z kompozice odchází. Bez vráceného fokusu nebyl
  fokus po návratu nikde a první šipka skočila do lišty složek.
- **Fokus se přenastavuje, dokud uživatel sám nepohne šipkou.** Složka se dočítá po dávkách
  a chaty s vyšším pořadím přibývají NAD zaostřeným řádkem; `LazyColumn` drží první viditelný
  řádek podle klíče, takže zaostřený odjel nad horní okraj a fokus nebyl vidět — první šipka
  dolů pak „naskočila" kdesi uprostřed seznamu (ověřeno na emulátoru při prvním otevření složky
  Kanály). Řešení: dokud fokus nedostane jiný řádek, každá změna seznamu znovu posune
  zaostřený řádek do obrazu a vyžádá fokus (`navigated` v `ChatList`).
- **Při procházení záložek si seznam fokus nebere** (`takeFocus`): přenastavování fokusu výš
  by ho při každém přepnutí složky (záložka se vybírá už zaostřením) vytáhlo ze záložek dolů.
- **Konverzace je stav v kompozici, ne ViewModel** (`ConversationState`): ViewModel by žil
  v úložišti aktivity i po zavření chatu a `closeChat` by TDLib nikdy nedostala. Zprávy jsou
  zaostřitelné jednotlivě (`focusable` s rámečkem), seznam je obrácený (nejnovější dole)
  a starší historie se dočítá, když se fokus blíží horní hraně. Předěl dne je nezaostřitelný
  — šipka ho přeskočí. **Zpráva vyšší než obrazovka** se šipkou napřed odroluje (viz „Dlouhé
  zprávy").
- **Písma jsou tatáž jako na telefonu** (`tvTypography`: Figtree a Bricolage z
  `:client:resources`, proložení podle `theme/Typography.kt`). Výchozí škála tv-material je
  Roboto a vedle zbytku Plume vypadala cize.
- **Heslo dvoufázového ověření je `BasicTextField`** v plošce s rámečkem fokusu: tv-material
  textové pole nemá. Píše se systémovou klávesnicí televize (na Google TV i hlasem z ovladače).
  Telefonní číslo a kód se na televizi nezadávají — přihlášení je jen QR kódem, jako na hodinkách.

## Video

- **Přehrávání za běhu stahování je v `:client:core`** (`core/video`: `StreamingSource`,
  `streamOriginalVideo`, `downloadHeadsTo` v `commonMain`, `PartialFileDataSource` v
  `androidMain`). Dřív bylo ve `shared` a televize by ho musela kopírovat. Balíček zůstal
  stejný, takže telefon se změnou importů nemusel měnit. `core` kvůli tomu táhne jen
  `media3-datasource` (`api`, typ je ve veřejném API zdroje), přehrávač si přidává, kdo přehrává.
- **`VideoPlayerScreen`**: ExoPlayer, obraz v `PlayerView` bez vlastního ovládání (jen povrch
  a poměr stran), ovládání v Compose. OK / Přehrát-Pozastavit přepíná, šipky vlevo a vpravo
  (i přetáčení na ovladači) skáčou o 10 s, Zpět zavře. Ovládání se po 4 s přehrávání schová,
  vrátí se s každým stiskem; první OK při schovaném ovládání ho jen ukáže, jako u systémových
  přehrávačů. Fokus zvuku a „sluchátka vytažena" řeší ExoPlayer (`handleAudioFocus`).
- **Tlačítka médií na ovladači přepínají vždycky**, i se schovaným ovládáním: klávesa se
  v `onPreviewKeyEvent` spotřebuje a `MediaSession` ji už nedostane, takže pravidlo „první stisk
  jen ukáže ovládání" (správné pro OK) by pauzu z ovladače napoprvé spolklo.
- **Zavřené video se přestane stahovat** (`cancelDownload` při odchodu, když se ještě stahuje):
  odběr `files.file` stahování spustil a sám ho nezruší. Telefon to má stejně, ale televize
  mívají kolem 8 GB a náhled pár vteřin videa z kanálu by stáhl celé.
- **`MediaSession` (media3-session)** dává přehrávač systému: tlačítka médií na ovladači
  a karta „Právě hraje" na Google TV. Ověřeno `dumpsys media_session` (stav PAUSED s pozicí).
- **Přehrávač leží NAD konverzací**, ta zůstává v kompozici — po zavření videa se historie
  nenačítá znovu. Zpět zavře nejdřív video: jeho `BackHandler` je složený později.
- **Po zavření videa se fokus vrací na zprávu s videem** (`focusTarget` v `MessageList`).
  Přehrávač fokus odnesl a bez vrácení skončil na jiné zprávě (ověřeno na emulátoru).
- **Zpráva s videem je `clickable` s `indication = null`**: na televizi bere i DPAD_CENTER,
  ale výchozí vlnka zabarvila celou šířku řádku vedle bubliny. Fokus nese rámeček bubliny.

## Dlouhé zprávy

- **Zpráva vyšší než seznam se šipkou napřed odroluje** (`TallItemScroll`, v konverzaci
  i v komentářích): dokud zaostřená zpráva přečnívá přes kraj seznamu ve směru šipky, šipka
  posune seznam o 60 % výšky (nejvýš o to, co zbývá do kraje zprávy), teprve pak pustí fokus
  dál. Dřív fokus skákal po celých zprávách a seznam ukázal jen kraj zaostřené — prostředek
  dlouhé zprávy nebo komentáře se nedal přečíst. Detail příspěvku kanálu to řeší vlastním
  rolováním textu (`PostBody`).
- **Při vstupu do vysoké zprávy** ukáže seznam (výchozí `bringIntoView`) její bližší kraj:
  zdola spodek, shora začátek. Šipka pak čte dál týmž směrem, takže se nic nepřeskočí.
- **Kraj se měří přes polohu v okně a výšku, ne `boundsInWindow`** — ta je oříznutá rodiči,
  takže přečnívající zpráva by vypadala, že se vejde. Seznam se měří bez odsazení obsahu.
- **Instance pomocníka se nesmí měnit se změnou odsazení.** Tlačítko psaní se ukáže až po
  načtení práv chatu, spodní odsazení se změní — a s `remember(…, odsazení)` vznikla nová
  instance, která nevěděla, která zpráva je zaostřená (změna fokusu, která to hlásí, už
  proběhla), takže šipka zprávu přeskočila (ověřeno logem na emulátoru). Odsazení se proto jen
  přepisuje do stávající instance.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem na zprávě o 47 řádcích od testovacího bota:
  šipka nahoru roluje zprávou až k jejímu začátku, další nahoru jde na předchozí video, dolů
  zpátky na začátek dlouhé zprávy a rolováním až na její konec. **Komentáře** (týž pomocník)
  na zařízení ověřené nejsou.

## Úklid mezipaměti

- **Automatický úklid stažených médií** (`AutoStorageCleaner` v `core/storage`, androidMain):
  média, na která se nesáhlo 3 dny, a pak nejdéle nepoužitá nad **1 GB**. Levné televize mají
  kolem 8 GB a videa z kanálů (s přehráváním po sobě) a příběhy by je zaplnila. Strop je výš
  než na hodinkách (100 MB): video má desítky MB a mezipaměť se pak obnovuje pořád dokola.
  Rozkoukané video smazáním souboru nepřijde o pozici (ta je zvlášť) a právě přehrávané video
  úklid nesmaže (TDLib nechá soubory mladší než hodina).
- **Společný s hodinkami**: dřív `WearStorageCleaner` ve `wearApp`, teď tatáž třída v `core`
  s mezemi od aplikace (hodinky dál `wear_storage`, 3 dny a 100 MB; televize `tv_storage`).
  Dvě kopie téhož úklidu by se rozešly.
- **Kontroluje se po přihlášení a pak každou hodinu, dokud proces běží** (úklid sám nejvýš
  jednou za 24 h). Televize nechává aplikaci v paměti celé dny — jen po startu procesu by
  úklid neproběhl nikdy. Kontrola je jen čtení času z `SharedPreferences`.
- **Ruční vyčištění televize nemá** (nemá nastavení); hodinky ho mají v Nastavení.
- **Ověřeno 27. 11. 2026**: po startu „Úklid úložiště (tv_storage) uvolnil 0 B" (vše mladší než
  3 dny a pod stropem, `files` 91 MB) a zapsaný čas v `tv_storage.xml`. Že úklid nad stropem
  opravdu maže, na televizi ověřené není — je to týž `cleanUp` z `core`, ověřený na hodinkách.

## Skok na zprávu

- **Okno historie kolem zprávy** (`ConversationState.jumpTo`, `loadAround`): `getChatHistory`
  od zprávy se záporným posunem (polovina starších, polovina novějších). Je-li zpráva už
  načtená, jen se na ni přesune fokus. Obrazovka žádost spotřebuje (`JumpRequest` s pořadím —
  týž cíl dvakrát po sobě musí skočit znovu), posune se na bublinu, počká snímek a vezme fokus.
- **V okně uprostřed historie** (`hasNewer`) se novější zprávy dočítají, jak se fokus blíží
  spodku (`loadNewer`, prázdná stránka = konec), a **živé nové zprávy se do okna nepřidávají** —
  stály by za mezerou v historii; přijdou s dočtením. Odeslání zprávy z okna načte konec historie
  (`loadLatest`), jinak by nová zpráva visela za mezerou.
- **Odkud se skáče**: zpráva ve výsledcích hledání (chat se otevře rovnou na ní,
  `TvNavigation.openedAt`), „Zobrazit v chatu" u rozkoukaného videa (dřív jen „Otevřít chat"),
  OK na zprávě bez média, která na něco odpovídá, a „Zobrazit citovanou zprávu" v nabídce nad
  zprávou (podržení OK) — u zprávy s médiem OK dál otevírá médium.
- **Kanál skočí rovnou do detailu příspěvku**, ve kterém zpráva je; po Zpět je fokus ve feedu
  na tomtéž příspěvku. Feed dočítá novější příspěvky u horního okraje stejně jako konverzace.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem: hledání „testovaci" → nejstarší výsledek
  (12:28) → konverzace otevřená na něm s fokusem, šipkou dolů dočtení až k nejnovější zprávě
  (17:36) a na „Napsat zprávu"; OK na odpovědi „Test odpovedi z TV" → fokus na citované zprávě.
  **Neověřené na zařízení**: „Zobrazit v chatu" z Rozkoukaných (táž cesta jako z hledání),
  skok v kanálu do detailu příspěvku a odeslání zprávy z okna.

## Kanál jako pořad (další video samo)

- **Žádost o přehrání nese frontu** (`VideoRequest.queue`, `next()`): po dohrání naskočí vpravo
  dole karta dalšího videa s náhledem, popiskem a šestivteřinovým odpočtem, pak se pustí samo.
  OK přehraje hned, Zpět odpočet zruší (video zůstane na konci, druhé Zpět zavře), jiná klávesa
  ho zruší a udělá, co dělá vždycky (šipka vlevo skočí zpět). Tlačítko „další" na ovladači pustí
  další video kdykoli. Ovládání během odpočtu ustoupí kartě — jinak ji překrývalo.
- **Směr podle toho, jak se obrazovka čte**: v konverzaci novější zprávy (`mediaOpenAction`
  z `allMessages`), v kanálu STARŠÍ příspěvky, protože feed jde od nejnovějšího dolů
  (`ChannelFeedScreen` předá frontu sám přes `videosAfter`). Ve frontě jsou jen videa, která
  přehrávač pustí (ne mizející a tajná), nejvýš 50, a jen z načtené části historie — feed se při
  přehrávání nedočítá.
- **Zpět z přehrávače vede tam, odkud se pouštělo první video** (detail příspěvku, zpráva
  v konverzaci), ne na příspěvek posledního přehraného. Přehrávač je jedna vrstva nad obrazovkou
  a každé video z fronty je nová instance (`key(fileId)`) téže vrstvy.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem na dvou patnáctivteřinových videích od
  testovacího bota: po dohrání karta „Další za 5 s" s náhledem a popiskem videa B, samo pustí B;
  Zpět během odpočtu zůstane na konci A; tlačítko „další" (`KEYCODE_MEDIA_NEXT`) pustí B hned;
  Zpět z B vrátí fokus na zprávu s A. **Fronta v kanálu (starší příspěvky) na zařízení ověřená
  NENÍ** — odebírané kanály s videy po sobě v testu nebyly.

## Rozkoukaná videa

- **Tatáž pravidla a tentýž seznam jako na telefonu** (`docs/poznatky/prohlizec-medii.md`,
  „Pozice přehrávání se pamatuje"): klíč je soubor na serveru (`MessageMedia.Video.fileKey`),
  jen videa od 2 min, ne prvních 10 s ani posledních 15 s, dohrané se zapomene. Data a čisté
  funkce (`VideoPosition`, `withPosition`) se kvůli tomu přesunuly do `:client:core`
  (`core/video/VideoPosition.kt`); `VideoPositions` nad `AppSettings` zůstal ve `shared`.
  Balíček je stejný, telefon importy neměnil.
- **Uložené v `SharedPreferences`** (`TvVideoPositions`), ne v DataStore — televize stojí jen
  na `core` a jeden seznam do sto kB DataStore nepotřebuje.
- **Synchronizace s telefonem a desktopem** (`TvPositionSync`): tatáž skupina
  `plume_positions` v úložišti bota `@PlumeSyncBot`, týž formát a totéž trojcestné sloučení
  jako `CloudSync` (`docs/poznatky/nastaveni.md`, „Synchronizace nastavení"). Formát, sloučení
  a čtení i zápis skupin jsou proto v `core` (`core/settings/sync/CloudGroups.kt`, `CloudStore.kt`);
  ve `shared` zůstaly jen skupiny, které televize nemá (záložky, barvy chatů, štítky, zkratky,
  volby). Televize čte a píše JEN svou skupinu — každá skupina má v úložišti vlastní klíče,
  takže ostatní nepřepíše. Synchronizuje se po `Ready`, **po návratu do popředí** (televize
  nechává aplikaci v pozadí celé dny a telefon mezitím sledoval dál), po místní změně
  s prodlevou 15 s a každých 15 minut. Základ sloučení drží `TvVideoPositions.syncBase`.
- **Video se pustí od pamatované pozice** a nahoře na 6 s od rozběhnutí svítí „Pokračuje od
  12:34" s tlačítkem Od začátku. Tlačítko nebere fokus (ten drží přehrávač kvůli šipkám), OK
  dokud je nápis vidět vrátí na začátek — jako „Přeskočit úvod" u streamovacích služeb. Pauza
  jde i tehdy tlačítkem médií. Bez pamatované pozice se začíná od `startSeconds` (přeposlání
  „od 1:23").
- **Zápis**: každých 10 s hraní, při dohrání, zavření přehrávače a `ON_STOP` (tlačítko Domů).
  Jen u videa, které opravdu hrálo — otevření a zavření bez puštění by zapsalo začátek, a tím
  smazalo pozici z telefonu. Pozice z posledního tiknutí, přehrávač už může být uvolněný.
- **Proužek zhlédnutého a „0:23 / 1:02:08"** v bublině videa i na kartě příspěvku kanálu
  (`rememberWatched`, `WatchedBar`), jako na telefonu.
- **Záložka Rozkoukaná videa** hned za Všemi chaty, jen když nějaká jsou (vybraná zůstane
  i s prázdným seznamem, ať odebrání posledního videa neschová záložku pod fokusem). Mřížka karet
  po třech (`ContinueWatchingPane`): OK pustí video rovnou, podržení OK nabídne Otevřít chat
  a Odebrat ze seznamu. Zpráva se dočte jednou a pamatuje se; napřed `summaries` nad chaty —
  video rozkoukané na telefonu bývá z chatu, který televize ještě nenačetla, a `getMessage` by
  bez něj selhal. Smazaná zpráva nebo zpráva bez videa se tiše nevykreslí. „Zobrazit v chatu"
  jako na telefonu to není: skok na zprávu konverzace na televizi zatím neumí.
- **Zamčené chaty televize nemá** (`LockedChats` dodává jen aplikace telefonu), takže v seznamu
  jsou i videa ze zamčených chatů — stejně jako ty chaty v seznamu chatů.
- **Šipka nahoru ze seznamu jde na VYBRANOU záložku** (`focusRestorer` na `TabRow`). Dřív šla
  na záložku nejblíž nad zaostřeným řádkem, a protože se záložka vybírá už zaostřením, šipka
  nahoru tiše přepnula složku (s novou záložkou na druhém místě pokaždé na Rozkoukaná videa).
- **Na plochu Google TV (řada „Pokračovat ve sledování") to nejde**, prozkoumáno 27. 11. 2026:
  - **Engage SDK** (`com.google.android.engage:engage-tv`, cesta, kterou Google do té řady
    dokumentuje) chce přihlášku a schválení Googlem, aplikace na Play v podporované kategorii
    a velikost (Media Experience Program nebo 100 tisíc denních uživatelů). **Zasílání zpráv
    mezi podporovanými kategoriemi výslovně není.**
  - **Starší Watch Next** (`androidx.tvprovider`, řada „Play Next") schválení nechce, ale pravidla
    říkají přidávat jen klasické filmy a seriály, ne klipy a krátká videa — přesně to, co je
    v chatech. Jestli se na Google TV ještě ukazuje v řadě „Pokračovat ve sledování", dokumentace
    neříká. Riziko, že kontrola kvality pro Android TV v Play aplikaci kvůli tomu vrátí, je
    větší než užitek.

## Příběhy

- **Řada příběhů nad Všemi chaty** (`StoryRowContent`), ne hromádka v pilulce jako na telefonu:
  avatar v řádku chatu nejde ovladačem zaostřit zvlášť (OK otevře chat) a kontakt bez chatu by
  se jinak k příběhům nedostal. Je to PRVNÍ POLOŽKA seznamu, takže s pohybem dolů odjede
  a chatům místo nebere; jen v hlavním seznamu a jen když nějaké příběhy jsou. Pořadí TDLib
  (`storyListOrder`), lem z dílků po kruhu (barevné nezhlédnuté, tenčí šedé zhlédnuté), vlastní
  příběh pod nadpisem „Moje příběhy" s fotkou účtu.
- **Řada přibude až s updaty od TDLib, kdy už seznam stojí.** Seznam drží první viditelnou
  položku podle klíče, takže řada zůstala nad horním okrajem a šipka nahoru ukázala jen avatar
  bez nadpisu (ověřeno na emulátoru). Dokud se uživatel nepohnul, seznam se vrátí na začátek.
  Indexy chatů jsou o řadu posunuté (`header` v `ChatList`).
- **Fokus v řadě znamená „uživatel se pohnul"** (`navigated`): bez toho by mu ho seznam při
  každé změně chatů (nová zpráva) vzal zpátky na chat. A **když nad seznamem leží prohlížeč nebo
  přehrávač, seznam si fokus nebere vůbec** (`overlayOpen`): změna chatů by ho prohlížeči
  ukradla a šipky by šly do seznamu pod ním. Po zavření jde fokus na avatar, ze kterého se
  příběhy otevřely.
- **Prohlížeč** (`StoriesViewerScreen`): příběh uprostřed na výšku (9 : 16, výška obrazovky bez
  bezpečných okrajů), nahoře dílky průběhu, autor a čas, dole popisek. Autoři i příběhy se při
  otevření zmrazí — `StoriesState` a pohyb (`next`, `previous`, `showPoster`) jsou tytéž čisté
  funkce jako na telefonu, kvůli tomu přesunuté do `:client:core` (`core/stories/StoryCursor.kt`).
  Šipky vlevo a vpravo předchozí a další příběh (za posledním autora na dalšího, za posledním
  autorem se zavře), nahoru a dolů rovnou jiný autor, OK pauza, Zpět zavře.
- **Otevírá se rovnou na vybraném autorovi** (index v počátečním stavu). Přes nastavení až po
  složení by se na okamžik otevřel první autor seznamu a TDLib by jeho příběh započítal jako
  zhlédnutý — autor by to viděl.
- **`openStory` / `closeStory`** při každém přechodu (`DisposableEffect` nad aktuálním příběhem,
  zavření přes aplikační scope). Načítá se obsah aktuálního a dalšího příběhu.
- **Fotka stojí 6 s od načtení, video hraje celé** — stáhne se napřed celé (příběhy mají do
  minuty a jednotky MB), pak ExoPlayer s `RESIZE_MODE_ZOOM` jako `Crop` u fotky. Fokus zvuku
  vezme přehrávači hudby (pozastaví se). Živé vysílání televize neumí: věta a po 6 s dál, stejně
  jako příběh, který už není.
- **Zatím ne**: reakce a odpověď na příběh, ztlumení (hlasitost má ovladač), skrytí autora,
  vlastní příběh (diváci, správa), lem příběhu u avataru v řádku chatu a v konverzaci.

## Hledání

- **První záložka, jen s lupou** — jako horní navigace samotné Google TV (hledání vlevo ikonou,
  pak sekce). Lišta s hlavními cíli (`NavigationDrawer`), se kterou počítala roadmapa, by kvůli
  dvěma cílům vzala šířku seznamu. Co ukazuje obrazovka pod záložkami, nese `ListSection`
  (Chaty, Rozkoukaná videa, Hledání); složka zůstává zvlášť.
- **Tytéž dotazy jako na telefonu** z `core`: `searchChats` napřed lokálně a pak i na serveru,
  `searchPublicChats` (globální výsledky) a `searchMessages` napříč chaty. Bez dotazu nedávno
  nalezené chaty (`recentlyFoundChats`). Vpravo náhled zaostřeného výsledku jako v seznamu chatů.
- **Části výsledků chodí, jak doběhnou dotazy, a chaty bývají až po globálních.** Seznam drží
  první viditelnou položku podle klíče, takže chaty i s nadpisem zůstaly nad horním okrajem a
  vypadalo to, že vlastní chat nenašel (ověřeno na emulátoru). Dokud v seznamu není fokus, drží
  se na začátku. **A výsledek se zapíše, jen když dotaz pořád platí**: volání TDLib nemusí na
  zrušení korutiny reagovat a výsledek staršího dotazu („Notifik" při psaní po částech) by
  přepsal novější.
- **Stav hledání drží navigace** (`TvSearchState`): z výsledku se otevírá chat, seznam i s
  hledáním odchází z kompozice a po návratu musí stát dotaz, výsledky i fokus na položce, ze
  které se odcházelo — bez nového dotazu.
- **Šipku vlevo si pole bere na posun kurzoru**, takže se z něj na mikrofon nedalo dostat. Na
  začátku textu šipka vlevo z pole odejde (`TextFieldValue` kvůli kurzoru); šipka dolů se posílá
  výslovně jako v okně odpovědi.
- **Hlasové zadání je systémové** (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`): na Google TV ho
  obslouží aplikace Google vlastním okénkem a vrátí text. Od Androidu 11 je k tomu potřeba
  `<queries>` v manifestu, jinak `resolveActivity` vrátí `null` a tlačítko se nikdy neukáže.
  **Jazyk se předává z aplikace** (`EXTRA_LANGUAGE`): na emulátoru s anglickým systémem
  a českou aplikací poslouchalo rozpoznávání anglicky.
- **Tlačítko hledání na ovladači** (`KEYCODE_SEARCH`, má ho část ovladačů) odkudkoli zavře, co je
  otevřené, přepne na hledání a spustí hlasové zadání; fokus jde na mikrofon (jinak zůstal na
  záložce a šipka vpravo přepnula složku). **Mikrofon na ovladači Google TV si ale bere Asistent
  systému**, aplikace ho nedostane — globální hledání přes Asistenta („najdi … v Plume") by
  chtělo `searchable` a obsahového poskytovatele, zatím není.
- **Zpráva z výsledků otevře chat rovnou na sobě** (viz „Skok na zprávu").

## Spořič obrazovky s fotkami

- **Systémový spořič** (`PhotoDreamService`, `DreamService`): fotky z Uložených zpráv, nebo
  z chatu vybraného v nastavení spořiče (`DreamSettingsActivity`, otevírá ho systém přes
  `android:settingsActivity` v `res/xml/photo_dream.xml`; volba v `SharedPreferences`). Fotka
  po fotce po 10 s, prolnutí a pomalé přiblížení (efekt Kena Burnse), vpravo dole hodiny a datum
  na ztmavení zespodu. Bez přihlášení jen hodiny. Neinteraktivní: první stisk spořič ukončí.
- **Fotky přes `sharedMedia(Media)`** z `core`, nejvýš 200 (10 stránek), v náhodném pořadí.
  **Bez spoileru, citlivého obsahu a mizejících fotek** — spořič běží v obýváku pro každého, kdo
  se dívá. Ze všech chatů naráz (`galleryMedia`) se vědomě nebere: na televizi by se ukazovalo
  cokoli, co kdo do kterého chatu poslal.
- **`DreamService` není `LifecycleOwner` ani `SavedStateRegistryOwner`**, které `ComposeView`
  hledá ve stromu oken (bez nich spadne při připojení). Spořič je proto dává sám a vede jejich
  stav podle snění (`onDreamingStarted` / `Stopped`, `onDetachedFromWindow`).
- **Proti vypálení**: obraz se pořád pomalu hýbe a hodiny se každou minutu posunou o pár bodů.
  Ztmavení pod hodinami jde přes celou šířku zespodu — s přechodem jen do strany mělo
  viditelnou vodorovnou hranu.
- **Kde jde spořič vybrat**: na Android TV v nastavení systému (Spořič obrazovky). **Google TV
  v nastavení nabízí jen svůj Ambientní režim** a spořič třetí strany se tam podle všeho vybrat
  nedá (na skutečném zařízení neověřeno); jde nastavit přes `adb`
  (`settings put secure screensaver_components eu.tintera.plume/eu.tintera.plume.tv.PhotoDreamService`).
- **Ověření na emulátoru**: `cmd dreams start-dreaming` chce root (obraz s Google Play ho nemá)
  a `Somnambulator` spořič nespustí. Funguje to přirozeně: `screensaver_enabled`,
  `screensaver_components`, `screensaver_activate_on_sleep` = 1, `dumpsys battery set ac 1`,
  `stay_on_while_plugged_in` = 0 a krátký `screen_off_timeout`. **Emulátor `Plume_TV` má po startu
  `screen_off_timeout` nastavený na „nikdy"** (`2147483647`), takže to po restartu znovu. Po testu
  vrátit (`settings delete`, `dumpsys battery reset`, timeout a `stay_on` zpátky).
- **Ověřeno 27. 11. 2026**: nastavení spořiče (Uložené zprávy zaškrtnuté, výběr Notifikátoru
  uložen), spořič s fotkami Notifikátoru (9 fotek, střídání po 10 s podle dočasného logu
  a snímků), přiblížení, hodiny s datem a jejich posun v nové minutě, ukončení stiskem OK.

## Fotky a alba

- **Prohlížeč přes celou obrazovku** (`MediaViewerScreen`): šipky vlevo a vpravo jdou po VŠECH
  fotkách a videích načtené konverzace (jako prohlížeč na telefonu), ne jen po albu; OK na
  videu ho pustí (přehrávač leží nad prohlížečem), Zpět zavře. Popisek a „3 / 12" se po 4 s
  schovají, u krajů šipky naznačují, že je kam jít. Fotka se ukáže hned ve variantě z bubliny
  (bývá stažená) a vymění se za plnou (`fullFileId`, dekódovaná na 1080 px), až dorazí.
- **Vrstvy v `TvNavigation`: chat → prohlížeč → přehrávač.** Zpět zavírá od horní (pozdější
  `BackHandler` má přednost). Fokus si po zavření bere vrstva, která zbyla: chat jen když je
  zavřené obojí (`overlayOpen`), jinak by po zavření přehrávače ukradl fokus prohlížeči.
- **Co udělá OK na zprávě, rozhoduje jedno místo** (`mediaOpenAction`): samotné video se rovnou
  přehraje (bez mezikroku přes prohlížeč), fotka nebo album otevře prohlížeč, zpráva bez média
  jen drží fokus. Platí v konverzaci, v komentářích i v detailu příspěvku.
- **Album je v konverzaci i v komentářích jedna bublina** (`albums`: po sobě jdoucí zprávy se
  stejným `mediaAlbumId`) s mřížkou 2 × 2 a „+N" na posledním náhledu. Popisek alba nese jen
  jedna zpráva, odesílatel a čas jsou z první. V detailu příspěvku je pod hlavním médiem pás
  dalších náhledů alba (jen na ukázku, prochází se v prohlížeči).
- **`MediaActions`** (přehrát video, otevřít prohlížeč) jde cestou od navigace ke zprávě jako
  jedna skupina, ne dva callbacky v každé funkci.
- **Přiblížení fotky v prohlížeči**: OK na fotce ji přiblíží na 2×, další OK na 3×, třetí zpátky.
  V přiblížení šipky fotku posouvají (místo přechodu na jinou), Zpět napřed přiblížení zruší
  (`BackHandler` složený po zavíracím má přednost). Vpravo nahoře „2×", šipky u krajů zmizí.
  Posun je podíl největšího možného posunu (-1..1) a **omezuje se na to, o kolik přiblížená
  fotka skutečně přesahuje plochu** (`ContentScale.Fit`: fotka 4 : 3 je užší než obrazovka) —
  s posunem počítaným z velikosti plochy fotka vyjela a odkryla černý pruh (ověřeno na emulátoru).
  Přiblížená fotka se dekóduje ve 2160 px, v 1080 px by při 3× byla rozmazaná.
- **Ověřeno 27. 11. 2026** na fotce 4 : 3 od testovacího bota: 2×, posun vpravo a dolů až na kraj
  fotky (bez černého pruhu), 3×, Zpět zpátky na celou fotku a druhé Zpět zavře prohlížeč.
- **Fotka, video a album bez bubliny, jako na telefonu** („Médium bez bubliny"
  v `konverzace-zobrazeni.md`). Bez popisku a citace leží snímek holý na ploše (`BareMedia`):
  tvar bubliny, rámeček fokusu po něm, čas na tmavé pilulce v pravém dolním rohu (délka videa
  proto jde nahoru vlevo), reakce pod snímkem na ploše, jméno odesílatele nad ním odsazené jako
  text v bublině. Dřív ležel snímek v bublině s odsazením 16 dp a vlastním zaoblením — rám, který
  nic neohraničoval.
- **S popiskem nebo citací je to JEDNA karta** (`MediaCard`): citace nahoře, snímek od kraje ke
  kraji (bez vlastního zaoblení, ořezává karta), pod ním popisek, reakce a čas. **Popisek má
  šířku snímku**: karta je `IntrinsicSize.Min` — snímek má pevnou šířku, text jen nejdelší slovo —
  jinak by se dlouhý popisek roztáhl vedle úzké fotky. Uvnitř karty není nic se
  `SubcomposeLayout`, intrinsické míry tedy jdou.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem (fotka a video bez popisku poslané testovacím
  botem): holá fotka s časem v rohu, holé video s délkou nahoře a časem dole, rámeček fokusu po
  tvaru snímku; video a album s popiskem jako karta, popisek na šířku snímku. Citace nad snímkem
  a jméno odesílatele ve skupině na zařízení ověřené nejsou.

## Reakce a odpovědi

- **Reakce jsou pod zprávou jako čipy** (emoji a počet, moje zvýrazněná). Citovaná zpráva se
  bere z načtené historie a co v ní není, dočte se jednou z TDLib (`ConversationState.quote`,
  jako na hodinkách). Vlastní emoji se v čipech i v nabídce vynechávají, televize je kreslit neumí.
- **Odpověď je karta ze dvou částí, jako na telefonu**: nahoře citace v tónované ploše
  (`tertiaryContainer`; ikona uvozovek, jméno, první řádek), pod ní odpověď v barvě bubliny.
  První verze měla svislý proužek v barvě motivu — jazyk M2, který telefon dávno nahradil
  (`konverzace-zobrazeni.md`, „Odpověď je karta ze dvou částí"). Jméno odesílatele ve skupině
  je v horním dílu, ne nad kartou. Obě části mají šířku té širší (`IntrinsicSize.Max` — uvnitř
  bubliny nic se `SubcomposeLayout` není). Text citace je `onTertiaryContainer`, ne `tertiary`:
  to je jediná dvojice, kterou M3 na té ploše garantuje. V nabídce a v dialogu odpovědi je
  citace samostatná tónovaná kartička (`QuoteCard`).
- **Nabídka nad zprávou je PODRŽENÍ OK** (`combinedClickable` s `onLongClick` — dlouhý stisk
  klávesy pozná sám; v adb `input keyevent --longpress KEYCODE_DPAD_CENTER`). Krátké OK zůstává
  médiu (video, prohlížeč, zvuk). V nabídce rychlé reakce ze sady chatu (`availableReactions`,
  nejvýš 7 — víc se do řady nevejde) a „Odpovědět", jen když se smí psát (`writeAccess`).
- **Nabídka i odpověď jsou `Dialog`** (vlastní okno): fokus z nich šipkou neuteče na zprávy pod
  nimi, Zpět zavře dialog, ne konverzaci, a po zavření se fokus vrátí na zprávu v hlavním okně.
  **`usePlatformDefaultWidth = false`**: výchozí šířka dialogu je kolem 440 dp, takže strop
  panelu 640 dp se neuplatnil a sedmé emoji v řadě rychlých reakcí bylo uříznuté.
- **Reakce se přepne hned** (`toggleReaction`, optimisticky), potvrdí ji update interakcí;
  nepovedená se vrátí — jen u té zprávy (první verze vracela celý seznam z doby před reakcí
  a zahodila by, co mezitím přišlo). **Běží ve scope obrazovky, ne nabídky**: nabídka se po
  výběru hned zavře a s jejím `rememberCoroutineScope` by se zrušil i rozběhnutý požadavek.
- **Pole odpovědi je jednořádkové a šipka dolů ho výslovně posílá na Odeslat.** Víceřádkové
  pole si šipku dolů bralo na pohyb kurzoru a z pole se ovladačem nedalo odejít (ověřeno na
  emulátoru). Pole dostane fokus hned, takže naskočí systémová klávesnice; její „odeslat"
  zprávu rovnou pošle.
- **Zprávu jde nadiktovat**: vlevo od pole je mikrofon (`MicButton`, tentýž systémový
  `RecognizerIntent` jako v hledání, sdílený přes `rememberVoiceInput` ve `VoiceInput.kt`).
  Šipka vlevo na začátku textu z pole odejde na mikrofon. Nadiktovaný text se **připojí** za
  napsaný (u úpravy za text zprávy), ne že ho přepíše — diktuje se po větách a oprava jedné
  věty by smazala předchozí. Kurzor jde na konec a fokus zpátky do pole, takže systémová
  klávesnice rovnou odešle. Kde rozpoznávání nic neobslouží, mikrofon chybí. **Na emulátoru se
  otevřelo okénko Google v češtině s výzvou „Řekněte zprávu"; skutečné nadiktování (zvuk) se
  ověřit nedalo**, vrácení výsledku je týž kód jako v hledání.
- **Po odeslání jde fokus na novou vlastní zprávu** a seznam k ní odroluje; jinak zůstal na
  zprávě, na kterou se odpovídalo, a nová visela pod spodním okrajem. Jen po odeslání z televize
  (`ConversationState.sentCount`): zpráva poslaná z telefonu téhož účtu by jinak odrolovala
  čtení starší historie.
- **Nová zpráva je tlačítko „Napsat zprávu" POD seznamem** (`ComposeButton`), ne pole: šipka
  dolů z nejnovější zprávy na něj skočí a OK otevře tentýž dialog jako odpověď, jen bez citace.
  Pole přímo pod konverzací by na televizi otevíralo klávesnici při každém průchodu fokusu.
  Tlačítko je jen tam, kde se smí psát (kanál ho nemá); pod komentáři „Napsat komentář".
  Seznam má, když je tlačítko vidět, dole jen malou mezeru — bezpečný okraj a místo pro dock
  nese tlačítko.
- **V komentářích pod příspěvkem totéž** (vlastní `MessageInteractions`); odpověď bez citace
  jde kořeni vlákna (`ThreadConversationRepository.send`), citace kořene se v bublině nekreslí.

## Úprava a smazání vlastní zprávy

- **V nabídce nad zprávou (podržení OK) přibude „Upravit" a „Smazat"** podle toho, co dovolí
  server (`messageActions` → `canEdit`, `canDeleteForSelf` / `canDeleteForAll`). Práva se
  dočítají až při otevření nabídky (`produceState`), tlačítka proto naskočí o chvilku později
  než reakce — u cizí zprávy nenaskočí vůbec.
- **Úprava je tentýž dialog jako odpověď** (`ReplyDialog(editing = …)`), předvyplněný textem
  zprávy; u média upravuje popisek (`editCaption`). Nezměněný text se neposílá. **Formátování
  se nezachová** — pole na televizi je holý text a entity by po úpravě neseděly na offsetech;
  kdo potřebuje tučné písmo, upraví zprávu na telefonu. Výsledek přijde updatem úprav, takže
  seznam se překreslí sám.
- **Kurzor stojí na KONCI předvyplněného textu** (`TextFieldValue` s `TextRange(délka)`).
  S holým řetězcem byl na začátku a dopsat slovo znamenalo napřed dojet šipkami na konec —
  ovladačem po znaku (ověřeno na emulátoru).
- **Upravená zpráva má před časem „upraveno"**, jako na telefonu.
- **Smazání se potvrzuje dialogem** se Zrušit a tlačítky podle práv: „Smazat pro všechny"
  a „Smazat jen pro mě" (ve skupině a kanálu bývá jen jedno). **Výchozí fokus je na Zrušit**:
  dialog naskočí pod prstem hned po výběru „Smazat" v nabídce a s fokusem na smazání stačilo
  OK zmáčknuté o jednou víc — při ověřování na emulátoru tak zmizela pro všechny cizí zpráva. Album se maže celé (všechny zprávy se stejným
  `mediaAlbumId`), jinak by v bublině zůstal zbytek mřížky. Zpráva zmizí ze seznamu hned po
  úspěchu, update o smazání ji pak už nenajde.
- **Po smazání fokus nezmizí**: přejde na sousední prvek (tlačítko „Napsat zprávu" nebo
  předchozí zprávu), šipka nahoru dál vede do historie (ověřeno na emulátoru).

## Hlasovky a hudba

- **Jeden přehrávač zvuku pro celou aplikaci** (`TvAudioPlayer`, žije v `TvNavigation` a dává se
  dál přes `LocalAudioPlayer`): hraje dál při přechodu mezi chaty a seznamem, dva zvuky se
  nepřou. OK na bublině hlasovky nebo skladby ji pustí, na právě hrající ji pozastaví. Po
  dohrání hraje další stejného druhu z konverzace (hlasovky a hudba se nemíchají).
- **Soubor se napřed stáhne celý**, pak hraje: hlasovky mají desítky kB, skladby jednotky MB,
  streamování jako u videa by tu přidalo složitost bez znatelného užitku. Dokud se stahuje,
  ukazuje tlačítko `LoadingIndicator`.
- **`MediaSession` dostává `ForwardingPlayer`, ne ExoPlayer přímo.** Fronta je v aplikaci
  a ExoPlayer má vždy jen jednu položku, takže session „další" a „předchozí" nenabídla —
  pauza z ovladače šla, další ne (ověřeno na emulátoru). Obal příkazy nabízí a posílá do fronty.
  „Předchozí" po třech vteřinách přehrávání vrací na začátek položky, jako všude.
- **Položka, která nejde stáhnout ani přehrát, se přeskočí** (`skipUnplayable`), na konci fronty
  přehrávání skončí; jinak zůstala v docku s tlačítkem, které nic nedělalo.
- **Příchozí hlasovka se po spuštění nahlásí jako poslechnutá** (`openContent`), tečka
  „nepřehráno" v bublině zmizí.
- **Dock dole uprostřed** (`AudioDock`): co hraje, průběh, čas a že je ve frontě další. Není
  zaostřitelný — ovládá se tlačítky médií a OK na bublině; další cíl fokusu by na televizi
  jen překážel. Seznamy mají, dokud je dock vidět, spodní odsazení navíc (`audioDockInset`),
  jinak by zakryl poslední řádky. Při videu se dock schová a hudbu pozastaví fokus zvuku
  (`handleAudioFocus` u obou přehrávačů).
- **Délku skladby nemusí odesílatel uvést** (bot bez `duration` → 0): bublina pak délku ukáže
  až z přehrávače a do té doby žádnou, ne „0:00".

## Kanály: feed a komentáře

- **Kanál se otevírá jako feed příspěvků** (`ChannelFeedScreen`), ostatní chaty jako konverzace.
  O druhu rozhoduje `ChatInfo.isChannel` (`ChatScreen` v `TvNavigation.kt`), než se cokoli
  nakreslí. Karty jsou podle návodů Google obsah k vizuálnímu procházení — u kanálu, kde se
  čtou příspěvky, sedí líp než bubliny. Nejnovější nahoře, starší se dočítají ke konci.
- **Karta příspěvku** (200 dp vysoká, přes celou šířku): médium 16 : 9 vlevo (fotka, náhled
  videa s délkou, u alba „+N"), vpravo první řádek textu jako nadpis, zbytek jako text (prázdné
  řádky sbalené, jinak by snědly místo), dole datum, zhlédnutí („2,1 tis." — `compactCountText`,
  přesunuté kvůli tomu ze `shared` do `core`), komentáře a tři nejčastější reakce. Vlastní
  emoji v reakcích se vynechávají, televize je kreslit neumí. Zaostřená karta se zvětší jen
  o 3 %: výchozích 10 % by kartu přes celou šířku vytlačilo přes bezpečný okraj.
- **Album je jeden příspěvek** (`channelPosts`: po sobě jdoucí zprávy se stejným
  `mediaAlbumId`). Popisek nese jen jedna ze zpráv alba a počty (zhlédnutí, komentáře) jen ta,
  ke které server přivázal diskusi — hledají se proto napříč albem (`statsMessage`).
- **Detail příspěvku** (`PostDetail`): vlevo médium a celý text, vpravo komentáře. Text se roluje
  šipkami nahoru a dolů, dokud je kam (vlastní `onPreviewKeyEvent` nad `verticalScroll` —
  zaostřitelná je celá plocha, ne jednotlivé odstavce). U videa OK přehraje. Detail kartu
  NAHRADÍ, neleží nad ní: fokus by šipkou utekl na karty pod detailem. Pozice feedu a karta,
  na kterou se vrátí fokus, proto žijí nad oběma.
- **Komentáře jsou vlákno v diskusní skupině**: `commentThread` (`getMessageThread`) řekne
  skupinu a kořen, historii a živé zprávy dá `ThreadConversationRepository` z `core` (tentýž
  obal jako na telefonu; `rememberConversationState(threadRootId = …)`). Kořen vlákna (kopie
  příspěvku ve skupině) se mezi komentáři vynechává, je vlevo. Počet komentářů se živě
  aktualizuje (ověřeno: 64 → 67 během testu). Kanál bez diskuse (`postStats.comments == null`)
  ukáže větu, že komentáře nemá.
- **Vstup do komentářů padá na nejnovější** (`focusRestorer` s nejnovějším komentářem jako
  zálohou, příště naposledy zaostřený). Bez toho šipka z textu vybrala komentář podle polohy
  — kterýkoli, který zrovna ležel naproti.

## Past: `produceState` hodnotu při změně klíče nevynuluje

Stav uvnitř `produceState` je `remember` BEZ klíčů; klíče jen znovu spustí producenta. Kdo
čte obrázek podle `fileId` a hlídá `if (value == null)`, ukáže po změně klíče pořád ten první:
náhled chatu i pozadí tak měly fotku prvního zaostřeného chatu u všech dalších. Producent musí
hodnotu vynulovat sám (`rememberImage` v `ChatAvatar.kt`). **Hodinky to mají taky**
(`ChatAvatar.kt` ve `wearApp`: při změně na `null` zůstane stará fotka) — v seznamu bez klíčů
položek se to může projevit cizím avatarem u recyklované karty; neověřené.

## Vydání

Do Play z téhož tagu jako telefon a hodinky, track `tv:internal`, za repo proměnnou
`TV_RELEASE` — postup, banner do obchodu, snímky a ověření release buildu jsou v
`docs/poznatky/release.md`, „Televize (Android TV)".

## Ověření

- **Hledání ověřeno 27. 11. 2026**: záložka s lupou, pole se systémovou klávesnicí, dotaz
  „Notifikator" psaný po částech → Chaty (vlastní Notifikátor) a Globální výsledky, šipka dolů
  na výsledek a náhled vpravo, otevření chatu a Zpět s fokusem na výsledku; „TV" → Zprávy
  s časy; šipka vlevo z pole na mikrofon; mikrofon i `input keyevent KEYCODE_SEARCH` otevřou
  okénko Googlu s výzvou „Co hledáte?" česky. Skutečný hlas a text vrácený rozpoznáváním ověřené
  nejsou (emulátor mikrofon nemá připojený).
- **Příběhy ověřeny 27. 11. 2026 jen částečně**: účet v tu chvíli neměl žádné aktivní příběhy
  (řada se správně neukázala) a příběhy lidí se na skutečném účtu otevírat nesmí (autor vidí,
  kdo je zhlédl). Dočasným ladicím háčkem (odstraněným): řada s kanálem Pavla Durova a dvěma
  vymyšlenými autory — nadpis, lemy (částečně zhlédnutý, zhlédnutý, nezhlédnutý), fokus,
  návrat fokusu na avatar po zavření; prohlížeč nad pěti příběhy z profilu téhož kanálu —
  video i fotka, dílky průběhu, autor a datum, popisek, pauza, šipkou dál, Zpět. Přechod mezi
  autory (nahoru a dolů, dohrání posledního příběhu autora) na zařízení ověřený NENÍ.
- **Rozkoukaná videa ověřena 27. 11. 2026** s tříminutovým videem od Notifikátora: po startu
  synchronizace stáhla pozice z telefonu (záložka s videem rozkoukaným na Foldu, „0:26 /
  1:02:08"), přehrání 28 s → proužek a „0:27 / 3:00" v bublině, znovu otevřené „Pokračuje od
  0:27", OK → od začátku, posun a zavření → nahráno do úložiště (log `TvPositionSync`), karta
  v záložce, přehrání z karty od 1:23 a fokus zpátky na kartě, podržení → Odebrat (zmizí
  i z úložiště, videa z telefonu zůstala). Že se pozice z televize ukáže na telefonu, ověřené
  není (telefon v tu chvíli neběžel); formát i sloučení jsou tytéž funkce z `core`.
- **Nová zpráva ověřena 27. 11. 2026** v chatu s Notifikátorem: šipka dolů na tlačítko,
  dialog „Nová zpráva", odeslání, fokus na nové zprávě. Pod komentáři jen přeloženo.
- **Reakce a odpovědi ověřeny 27. 11. 2026** v chatu s Notifikátorem: podržení OK, reakce 👍
  (čip hned), odpověď přes systémovou klávesnici, citace v odeslané bublině, fokus na nové
  zprávě. V komentářích pod veřejným příspěvkem jen přeloženo (reakce i komentář by byly vidět
  všem odběratelům).
- **Hlasovky a hudba ověřeny 27. 11. 2026** (hlasovka a dvě skladby od Notifikátora): vlna
  s průběhem, dock, hraní dál po odchodu do seznamu, pauza a „další" z ovladače
  (`input keyevent KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_MEDIA_NEXT`), stav v `dumpsys media_session`.
- **Fotky a alba ověřeny 27. 11. 2026**: album (tři fotky a video, poslané Notifikátorem do jeho
  chatu) jako bublina s mřížkou, prohlížeč od první fotky alba, šipkami na video, přehrání, Zpět
  do prohlížeče a do konverzace s fokusem na albu. Pás alba v detailu příspěvku je jen přeložený
  (odebírané kanály s alby v testu nebyly).
- **Feed kanálu ověřen 27. 11. 2026** na veřejných kanálech, které účet odebírá a měl
  přečtené (Pavel Durov bez komentářů, Short Drama s komentáři a reakcemi): karty, detail,
  rolování textu, vstup do komentářů, zpět s fokusem na kartě. Kanál se otevře i intentem
  (`--el eu.tintera.plume.tv.CHAT_ID -1001006503122` je kanál Pavla Durova).

- **Emulátor `Plume_TV`**: obraz telefonu (`android-34`, `google_apis_playstore`) s displejem
  televize — 1920 × 1080, hustota 320 (tedy 960 × 540 dp jako televize 1080p), `hw.dPad=yes`,
  `hw.screen=no-touch`, naležato. Televizní obraz systému v SDK není (a `sdkmanager` taky ne);
  aplikaci to nevadí, `uses-feature` instalace přes `adb` nehlídá, filtruje jen Play. Televizní
  launcher a banner se na něm ověřit nedají. Šipky a Enter z klávesnice Macu jdou jako D-pad,
  z příkazové řádky `adb shell input keyevent KEYCODE_DPAD_DOWN` / `KEYCODE_DPAD_CENTER` / `KEYCODE_BACK`.
- **Ověřeno 27. 11. 2026** (česky): QR přihlášení, seznam chatů s avatary a časy, rozbalení
  lišty šipkou vlevo, přepnutí složky (fokus na první chat), ztlumený odznak, konverzace kanálu
  s předělem „Dnes", posun po zprávách šipkou, zpět s fokusem na otevřeném chatu. Heslo
  dvoufázového ověření a fotky v bublině na emulátoru vyvolané nejsou (účet heslo nemá, otevřený
  kanál fotky neměl); skutečná televize ověřená není.
- **Ověřeno 27. 11. 2026 odpoledne** (česky): záložky složek s počty, přepnutí složky
  zaostřením záložky (fokus zůstane v záložkách, náhled se vynuluje), náhled zaostřeného chatu
  s pozadím z posledního média a živým „píše…", přehrání 40s videa (poslané Notifikátorem do
  jeho chatu) za běhu stahování, skok o 10 s, pauza, schování ovládání, `MediaSession`, zpět
  s fokusem na zprávě s videem. Otevření chatu intentem:
  `am start -n eu.tintera.plume/eu.tintera.plume.tv.MainActivity --el eu.tintera.plume.tv.CHAT_ID 8875105705`
  (Notifikátor) — do testovacího chatu se neťuká přes seznam, ten se mezi snímkem a stiskem
  přeskládá.
