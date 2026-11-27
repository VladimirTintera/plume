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
- **Lišta složek je `NavigationDrawer`** (ne modální): sbalená ukazuje ikony, rozbalí se
  s popisky a počty nepřečtených, jakmile do ní uživatel šipkou vlevo vjede.
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

## Ověření

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
