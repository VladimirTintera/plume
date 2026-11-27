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
  — šipka ho přeskočí. **Zpráva vyšší než obrazovka** (dlouhý příspěvek kanálu) se ukáže jen
  částečně: fokus skáče po celých zprávách. Pro kanály se to bude řešit s feedem příspěvků.
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
- **`MediaSession` (media3-session)** dává přehrávač systému: tlačítka médií na ovladači
  a karta „Právě hraje" na Google TV. Ověřeno `dumpsys media_session` (stav PAUSED s pozicí).
- **Přehrávač leží NAD konverzací**, ta zůstává v kompozici — po zavření videa se historie
  nenačítá znovu. Zpět zavře nejdřív video: jeho `BackHandler` je složený později.
- **Po zavření videa se fokus vrací na zprávu s videem** (`focusTarget` v `MessageList`).
  Přehrávač fokus odnesl a bez vrácení skončil na jiné zprávě (ověřeno na emulátoru).
- **Zpráva s videem je `clickable` s `indication = null`**: na televizi bere i DPAD_CENTER,
  ale výchozí vlnka zabarvila celou šířku řádku vedle bubliny. Fokus nese rámeček bubliny.

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

## Ověření

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
