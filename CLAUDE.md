# CLAUDE.md

Material 3 Expressive klient pro Telegram. Kotlin Multiplatform, Android + iOS, sdílené Compose UI.

**Pracovní název je „Plume"**, balíček `eu.tintera.plume`. Podle [Telegram API ToS](https://core.telegram.org/api/terms)
nesmí název obsahovat „Telegram" bez předřazeného „Unofficial" a nesmí se použít oficiální logo.

## Stav

Hotové: téma, přihlašovací automat, Navigation3 + adaptivní shell (bez railu i navigační lišty, Nastavení z menu účtu), živý seznam chatů a archiv,
konverzace (historie, živé zprávy, odesílání, přílohy a alba, hlasovky a video kolečka, samolepky a emoji, úprava,
přeposílání, připnuté zprávy, ankety a kvízy, témata ve skupinách), prohlížeč médií s fotkami i videem, reakce, odpovědi, notifikace
a hlášení pádů na Androidu, nabídka aktualizace z Play, sponzorované zprávy, stav spojení a jeho obnova po návratu z pozadí.

Není: hlášení pádů z iOS. Notifikace na iOS (APNs) jsou napojené, ale čekají na certifikát APNs u Telegramu a ověření skutečným pushem.
Hledání umí chaty, veřejné chaty, zprávy napříč chaty i uvnitř jedné konverzace. Nastavení je list-detail se sekcemi Obecné, Notifikace, Vzhled, Zařízení a Co je nového; vědomě
nastavitelné je téma aplikace (motiv a barvy z tapety), vyčlenění nepřečtených chatů do vlastního
bloku a upozornění po rozsazích (soukromé chaty / skupiny / kanály) s výjimkami.
Rozpracovaný detail chatu má vlastní seznam:
[docs/roadmap-konverzace.md](docs/roadmap-konverzace.md).

## Build

```bash
./gradlew :client:androidApp:assembleDebug          # Android APK
./gradlew :client:shared:linkDebugFrameworkIosSimulatorArm64   # ověření iOS linku
./gradlew :client:shared:testAndroidHostTest       # testy commonTest na JVM (sekundy, bez emulátoru)
open iosApp/iosApp.xcodeproj                         # iOS se staví z Xcode
```

Před prvním spuštěním doplň do `local.properties` (gitignorovaný):

```
telegramApiId=<z my.telegram.org>
telegramApiHash=<z my.telegram.org>
```

Bez nich se projekt přeloží, ale přihlášení skončí hláškou `AuthStage.Failed` s vysvětlením.

## Release do Play

Vydává se pushnutím tagu `android-<versionName>-<versionCode>`; postup a seznam secrets je
v [README](README.md#release-do-google-play). Sem patří jen to, co se z workflow nepozná.

- **Verze NENÍ v commitu, je v tagu.** `versionCode`/`versionName` se do buildu dostanou přes
  `-PandroidVersionCode` / `-PandroidVersionName`; hodnoty v `build.gradle.kts` jsou jen záloha
  pro lokální build. Verzovací commit by znamenal, že tag a kód mohou tvrdit každý něco jiného.
- **`versionCode` je jednosměrný.** Play si pamatuje každé nahrané číslo, i z internal tracku,
  a stejné podruhé nevezme. Bump workflow ho proto zvedá při KAŽDÉM releasu, i když se
  `versionName` nemění.
- **Konfigurace mimo git se čte `configValue`: property → ENV → `local.properties`.** Na CI
  žádné `local.properties` není a vyrábět ho tam by znamenalo psát secrets do souboru v pracovním
  adresáři. ENV se čte přes `providers`, ne `System.getenv` — jinak by hodnota nevstoupila do
  otisku konfigurační keše (`org.gradle.configuration-cache=true`) a build by po výměně secretu
  použil starý.
- **`releaseKeystoreFile` musí být deklarovaný PŘED blokem `android { }`.** Top-level `val`
  se v `.kts` inicializují v pořadí deklarace — stejná past jako u `NavSavedStateConfiguration`.
- **Keystore na CI je absolutní cesta, lokálně relativní.** `ANDROID_KEYSTORE_PATH` míří do
  `RUNNER_TEMP`, `releaseStoreFile` do kořene projektu; proto se `isAbsolute` rozlišuje.
- **`verifyReleaseConfiguration` visí na `bundleRelease`/`assembleRelease`.** Chybějící podpis
  nebo prázdné `api_id` se jinak poznají až u testera: nepodepsaný AAB Play odmítne a build
  s `TELEGRAM_API_ID = 0` se přeloží, jen se v něm nejde přihlásit.
- **AAB se nikam nepřikládá.** Ani jako artefakt běhu, ani jako příloha releasu. Bundl má přes
  50 MB a artefakty se počítají do kvóty Actions (free účet 500 MB = deset releasů); přílohy
  releasu se do ní sice nepočítají, ale kopie navíc stejně k ničemu není. Play si bundl drží
  a **build je z tagu reprodukovatelný**, protože verze je v tagu, ne v commitu.
- **První AAB musí do Play nahrát člověk.** Dokud aplikace nemá žádný release, androidpublisher
  API upload odmítne — krok `upload-google-play` je až pro další verze.
- **Ručně vydaná verze se značí tagem se suffixem `-manual`.** Release workflow ho ignoruje
  (`'!android-*-manual'`), ale bump logika z něj počítá další verzi.
- **Tag pushnutý přes `GITHUB_TOKEN` push trigger nespustí.** Ochrana proti rekurzivním workflow,
  proto bump workflow release volá ještě výslovně přes `gh workflow run --ref <tag>`.
- **Monitor paměti běží uvnitř build kroku, ne vedle něj.** Když runner umře na paměť, nespustí
  se ani krok s `if: always()`; poslední řádek `[mon]` v logu je pak jediná stopa.
- **Java na CI musí odpovídat `jvmToolchain(17)`.** S jinou verzí by ji foojay resolver stahoval
  při každém běhu znovu.
- **Záznam v obchodě je v `play/`, nahrává ho ruční workflow Play Listing**
  (`.github/scripts/play_listing.py`), ne release. Záznam je jeden pro všechny tracky a mění se
  jinak často než verze. Výchozí je `dry_run`: edit se jen ověří a zahodí.
- **Nahrání obrázků do editu projde i bez práva ke správě obchodu, 403 přijde až
  na `edits:validate`.** Service account umí vydávat buildy, a přesto záznam měnit nesmí —
  potřebuje navíc oprávnění ke správě prezentace v obchodě. Chyba se tedy neukáže v prvním
  kroku, ale až na konci.
- **Snímky obrazovky: nejvýš 2:1.** Emulátor dělá 1344 × 2992 (2,23:1) a Play ho odmítne;
  snímky se rozšiřují na 1496 px protažením krajního sloupce (pozadí je po stranách
  jednobarevné), ne ořezem, který by ukrojil hlavičku nebo pole.
- **Na snímcích nesmí být skuteční lidé.** Seznam chatů testovacího účtu ukazuje jména, fotky
  a zprávy kontaktů (i přihlašovací kód); snímky se proto dělají v chatu s testovacím botem,
  kterému zprávy posílá Bot API. Česky se z `adb input text` psát nedá — odchozí text jde do
  pole přes sdílení (`SEND` s `EXTRA_SHORTCUT_ID chat_<id bota>`). Pozor: `chat_id` v Bot API
  u soukromého chatu je id UŽIVATELE, takže `chat_<chat_id>` otevře Uložené zprávy, ne bota.
- **Ikona aplikace (pírko) se generuje, nekreslí se ručně** (`play/tools/icon.py`). Jedna
  geometrie v shapely → PNG pro Play, 1024px PNG pro iOS a vektor popředí adaptivní ikony
  Androidu (`ic_launcher_foreground.xml`, i jako `monochrome` pro tématické ikony). Tři ručně
  udržované kopie by se rozešly. Pozadí je gradient v `ic_launcher_background.xml` — jeho barvy
  musí sedět s `TOP`/`BOTTOM` ve skriptu. Pírko sahá do 31 dp od středu, bezpečná zóna
  adaptivní ikony je 33 dp; víc a launcher s kulatou maskou ho ořízne.
- **Pírko je baculaté, ne dlouhé a úzké.** První verze byla štíhlá šikmá čárka: do kruhu se
  vepisuje podle NEJDELŠÍHO rozměru, takže úzký tvar zabral zlomek plochy a v notifikaci ve
  stavové liště nebyl skoro vidět. Širší vlajka a kratší stopka zabírají zhruba dvojnásobek.
- **Ikona notifikace se vepisuje podle obdélníku kolem tvaru, ne do kruhu** (`fitted(box =
  True)`, 22 dp z 24). Nic ji neořezává, takže kruh kolem těžiště jen plýtval rohy — šikmé
  pírko je vůči těžišti posunuté a vlevo nahoře zbývalo prázdno. Velikost se porovnává
  s ostatními ikonami ve stavové liště, ne od oka v náhledu.
- **Balíček SDK platformy se jmenuje `platforms;android-37.0`** — u minor verzí je nula součástí
  id. Musí odpovídat `android-compileSdk` v `libs.versions.toml`.

## R8

Release build je minifikovaný. Dex spadl ze **43,8 MB na 7,2 MB** — TDLib generuje tisíce tříd
API, které aplikace z drtivé většiny nevolá.

- **Wrapper si keep pravidla nese sám.** `tdl-coroutines` má v AAR `proguard.txt`, který drží
  JNI hranici (`org.drinkless.tdlib.JsonClient`, jeho `native <methods>` a callback na logy);
  AGP ho aplikuje automaticky. **Nekopírovat je do `proguard-rules.pro`** — při bumpu wrapperu
  by se opsaná kopie tiše rozešla s tím, co knihovna opravdu potřebuje.
- **Do `proguard-rules.pro` patří jen to, co se ověřeně rozbilo na zařízení.** Preventivní
  `-keep` vypne zkracování tam, kde nemusí, a zakonzervuje chybu, která se mezitím opravila
  jinde. Zatím tam kromě atributů pro čitelné stack trace je jediné pravidlo: LiteRT-LM.
- **LiteRT-LM (Gemma) si keep pravidla NEPŘIBALUJE** — AAR `litertlm-android 0.11.0` nemá
  `proguard.txt`, na rozdíl od TDLib wrapperu. Nativní strana přitom sahá do Kotlinu podle
  jména: release build při prvním použití Gemmy spadl na `NoSuchMethodError …
  SamplerConfig.getTopK()` a **SIGABRT** — nativní pád, tedy bez výjimky, kterou by šlo
  chytit. Projevilo se to hned při OTEVŘENÍ CHATU: s příchozí poslední zprávou se sám rozběhne
  návrh odpovědi. Proto `-keep class com.google.ai.edge.litertlm.** { *; }` na celý balíček,
  ne výčet tříd — JNI hranici určuje knihovna a výčet by se při bumpu tiše rozešel. **Při
  bumpu knihovny zkontrolovat, jestli už `proguard.txt` nese** (`unzip -l` na AAR); pak
  pravidlo pryč. Ověřeno na emulátoru: bez pravidla pád, s ním shrnutí i návrhy.
- **`isShrinkResources` je zapnuté, ale až zvlášť po minifikaci** — zapínat obojí naráz by
  znamenalo nevědět, co z toho případný pád způsobilo.
  - **composeResources se ho NETÝKAJÍ.** Leží v `assets/` (v APK je jich před i po 180 souborů),
    shrinker uklízí jen `res/`. Starší poznámka, že „umí uklidit composeResources", neplatí.
  - **Ušetří málo: arm64 APK 54,6 → 53,5 MB** (z `res/` zmizelo 390 souborů nepoužitých
    prostředků knihoven). Většinu APK tvoří nativní knihovny (TDLib, LiteRT-LM).
  - **Prostředky hledané podle jména zůstávají.** Náš kód `getIdentifier` nevolá; Firebase
    a Crashlytics ano (`google_app_id`, `gcm_defaultSenderId`, id mapping souboru), ale ty drží
    keep XML z jejich pluginů — v `build/outputs/mapping/release/resources.txt` je u nich
    „reachable from keep xml file". Tam se kontroluje, proč prostředek zůstal nebo zmizel.
  - **S ABI splity rozbíjí `bundleRelease`** („Multiple shrunk-resources files found",
    issuetracker 402800800): AGP zmenší prostředky pro každou ABI variantu zvlášť a bundle
    čeká jeden soubor. Splity se proto u úloh `bundle*` vypínají (`buildsBundle`
    v `build.gradle.kts`). **Ověřovalo se přes APK, a tak to prošlo** — release staví AAB,
    takže po zásahu do release konfigurace se musí sestavit i `bundleRelease`.
  - **Soubory v `META-INF/services` mezi buildy mění jména** (R8 přejmenuje rozhraní služby),
    počet zůstává. Při porovnávání APK to vypadá jako ztráta, a není.
  - **Ověřeno na emulátoru** release APK podepsaným debug klíčem přes debug build: konverzace,
    výběr emoji, nabídka příloh, prohlížeč fotky, notifikace s ikonou a třemi akcemi a obnova
    do konverzace po zabití procesu. **`am kill` release proces nezabije, dokud není v keši**
    a `run-as` na release nejde — odsunout ho otevřením systémového Nastavení, pak `am kill`.
- **Chybějící keep pravidlo se při buildu nepozná, projeví se AŽ ZA BĚHU.** Release build se
  proto musí proklikat, ne jen přeložit.
- **`::class.simpleName` v release buildu nic neříká.** R8 třídy přejmenovává, takže z
  `AuthorizationStateReady` zbylo „du" — jednou v logu a podruhé rovnou v textu, který na
  přihlašovací obrazovce četl uživatel. Kde má název typu skončit v logu nebo před uživatelem,
  musí to být ručně psaný řetězec (`stateName()` v `TdlTelegramSession`); `when` nad sealed
  hierarchií bez `else` navíc zařídí, že nový stav v novější TDLib rozbije překlad, místo aby
  se tiše vypsalo „neznámý". `-keepnames` na balíček TDLib by fungoval taky, ale propašoval by
  jméno wrapper balíčku do `proguard-rules.pro` — přesně to, co se při bumpu tiše rozejde.
- **Název třídy stejně nepatří do textu pro uživatele**, ani bez R8. „Tahle fáze přihlášení není
  podporovaná: AuthorizationStateWaitEmailAddress" mu neřekne, co má dělat; pojmenovat fázi
  vlastní větou je práce navíc jen zdánlivě — je to jediná verze, která má pro čtenáře smysl.
- **Test se dá udělat bez ztráty přihlášení: release APK se přepodepíše DEBUG klíčem**
  (`apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --ks-key-alias
  androiddebugkey`) a nainstaluje přes `adb install -r`. Minifikace je vlastnost buildu,
  s podpisem nesouvisí; jinak by odinstalace smazala TDLib databázi a testovat by se muselo
  od přihlašování.
- **Nejcennější kontrola je obnova po zabití procesu** (`adb shell am kill` z pozadí, pak
  spustit znovu). Klíče navigace jsou `@Serializable` a polymorfní serializace přes
  kotlinx.serialization je to, co R8 láme nejčastěji — pokud se aplikace vrátí do konverzace
  a ne na seznam chatů, je tahle část v pořádku.
## Hlášení pádů (Crashlytics)

Android release build posílá pády, zachycené chyby a kontext do Firebase Crashlytics. Aplikace
ho ale **nikde nevolá přímo**: píše se do Kermitu (`Logger.withTag(…)`) a přemostění dělá
`CrashlyticsLogWriter` v `core/logging`. Dvě paralelní cesty k hlášení by se dřív nebo později
rozešly — část chyb by v konzoli chyběla a nebylo by poznat které.

- **`core/logging` je hranice**, stejně jako `core/telegram` kolem TDLib: `com.google.firebase.*`
  smí být importované jen tam, zbytek vidí rozhraní `CrashReporter`.
- **Hranice závažnosti jsou dvě, ne jedna.** Od `Info` výš se log přikládá k pádu jako kontext
  (`d`/`v` ne — Crashlytics si ke hlášení drží nejvýš 64 kB logu a starší řádky zahazuje). Samostatný problém
  v konzoli zakládá až `e` **s výjimkou**. Důvod: `logger.w` je v projektu hlášení OČEKÁVANÉHO
  selhání (nenačtená sada samolepek, neuložený koncept) a je jich přes dvacet — problémy z nich
  by seznam zaplevelily tak, že by v něm skutečné chyby zanikly.
- **Chybová hláška bez výjimky zůstane jen kontextem.** `recordException` staví problém kolem
  stack trace; bez výjimky by v konzoli byl záznam, u kterého není poznat, odkud je.
- **Řádek zalogovaný v téže milisekundě jako zachycená chyba se k ní ještě nemusí připojit.**
  Crashlytics zapisuje kontextové řádky na jiném vlákně, než sestavuje událost (v logu je pak
  „No log data to include with this event"). U pádu a u čehokoli staršího než pár milisekund je
  to bez významu — jen se na tom nedá stavět ověřovací test.
- **Writery se nastavují PŘED sestavením grafu** (`configureLogging()` na prvním řádku
  `startKoin`). Eager singletony — seznam chatů, `FileRepository`, nastavení, start TDLib —
  vznikají ještě uvnitř `startKoin` a právě jejich logy jsou u pádů při startu to nejzajímavější.
- **Systémový log zůstává i v release.** Release APK se ověřuje na zařízení (viz níž) a `adb
  logcat` je při tom jediné, co je vidět hned.
- **Sběr se zapíná manifestem, ne za běhu.** `firebase_crashlytics_collection_enabled` je
  placeholder podle build typu (release `true`, jinak `false`); Crashlytics si ho čte při
  inicializaci procesu, tedy dřív, než se dá spustit jakýkoli náš kód. Pády z vývoje do konzole
  nepatří — zanikla by v nich hlášení od testerů.
- **Bez `google-services.json` se nesmí nic rozbít.** Plugin se neaplikuje (stejná podmínka jako
  u pushe) a `createCrashReporter()` vrátí `NoopCrashReporter`. Ptá se přes
  `FirebaseApp.getInstance()`, protože `FirebaseCrashlytics.getInstance()` by bez inicializované
  Firebase vyhodilo `IllegalStateException` — a `getApps(context)` by kvůli `Context` znamenalo
  vyrábět hlásiče až z grafu závislostí, tedy pozdě.
- **Gradle plugin `crashlytics` musí být aplikovaný ZA `google-services`** — čte si jeho
  konfiguraci. Sám nic neodesílá; do buildu přidá build id a nahrání R8 mapping souboru.
  **S minifikovaným buildem je mapping soubor to jediné, co dělá stack trace čitelným**, a
  `-keepattributes SourceFile,LineNumberTable` v `proguard-rules.pro` je jeho druhá půlka:
  bez nich se čísla řádků do APK vůbec nedostanou a nemá je co doplnit zpátky.
- **Hlášení odchází až PŘI PŘÍŠTÍM STARTU aplikace**, ne v okamžiku pádu. Kdo po pádu aplikaci
  znovu nespustí, v konzoli nic nenajde a vypadá to jako nefunkční integrace.
- **Ověřit to jde jen skutečným pádem release buildu.** V debug buildu je sběr vypnutý, takže
  „projde to bez chyby" nic neznamená. Postup je stejný jako u R8: release APK se přepodepíše
  debug klíčem a nainstaluje přes `adb install -r`, takže se nepřijde o přihlášení.
- **iOS hlásič pádů zatím nemá** (`NoopCrashReporter`). Firebase v Xcode projektu není — stejně
  jako u notifikací. Až přibude, implementace musí přijít ze Swiftu: `FirebaseCrashlytics` je
  ObjC knihovna, kterou Kotlin/Native nevidí, a Kotlin výjimka nese jen adresy stack trace
  (`getStackTraceAddresses()`), ne symboly. Vzor je `IosCrashlyticsLogger` v projektu
  com.profilog.telemetry.android.

## Zařízení (aktivní relace)

Sekce Nastavení „Zařízení" (`features/devices`): kde je účet přihlášený, odhlášení jednoho nebo
všech ostatních, weby přihlášené přes Telegram, automatické odhlášení nečinných a přihlášení
jiného zařízení QR kódem. Hranice je `DeviceRepository` v `core/telegram`.

- **Seznam se po každé akci ČTE ZNOVU**, nic se nepřepisuje lokálně. Co odhlášení udělalo
  s ostatními relacemi (a jestli server vůbec poslechl), ví jen server.
- **„Byli jste to vy?" je karta nahoře v seznamu chatů** (`UnconfirmedLoginCard`), ne jen
  v Nastavení — tam by ji nikdo nehledal. Přichází `UpdateUnconfirmedSession` hned po startu,
  proto je repository eager (`createdAtStart`). Připojený bot (`SessionTypeConnectedBot`) se
  nezobrazuje — `confirmSession` bere jen id relace zařízení.
- **Čerstvě přihlášené zařízení (asi 24 h) nesmí odhlašovat ostatní** —
  `FRESH_RESET_AUTHORISATION_FORBIDDEN`, v `userMessage` má vlastní větu. Na emulátoru, který se
  přihlásil právě teď, to tlačítko skončí touhle hláškou; chyba to není.
- **Půl roku je 183 dní, ne 180** (`INACTIVE_TTL_OPTIONS`). Tak ho server posílá u účtu, který
  volbu nikdy neměnil; se 180 se ve výběru nic neoznačilo a řádek hlásil „183 dní".
- **Odhlášení cizího zařízení je hned pod hlavičkou detailu a sheet se otevírá celý**
  (`enabledValues = Hidden, Expanded`). Dokud bylo tlačítko pod údaji a přepínači, napůl
  rozbalený sheet ho schoval pod okraj a vypadalo to, že jednotlivé zařízení odhlásit nejde —
  jen všechna naráz.
- **Odhlášení TOHOTO zařízení je v Obecných, ne v detailu zařízení** — maže lokální data TDLib
  a je to jiný následek než odhlášení cizího zařízení.
- **Čtečka QR kódů je platformní** (`rememberQrScanner`). Android: **Google code scanner**
  (`play-services-code-scanner`) — skenovací obrazovku kreslí Play služby, aplikace nepotřebuje
  oprávnění ke kameře ani CameraX s ML Kitem; bez Play služeb se nespustí. iOS: AVFoundation
  (`AVCaptureMetadataOutput`) ve vlastním dialogu — VisionKit `DataScannerViewController` je jen
  pro Swift. `startRunning` blokuje, proto mimo hlavní vlákno; delegát se drží silnou referencí.
  Simulátor kameru nemá, skončí hláškou „čtečku se nepodařilo spustit".
- **Na server jde jen `tg://login?token=…`** (`isLoginLink`). Čtečka přečte jakýkoli QR kód
  a server by na cizí obsah odpověděl chybou, ze které uživatel nic nepozná.
- **Odkaz `tg://login` se z ťuknutí NEOTEVÍRÁ**, jen ze čtečky. Přihlašovací odkaz poslaný ve
  zprávě je typický podvod („naskenuj/otevři tohle"); oficiální klienti ho taky jen skenují.

## AI v zařízení (prototyp)

Experimentální „Shrnout" v hlavičce konverzace: shrnutí nepřečtených (jinak posledních 60 zpráv)
jazykovým modelem v telefonu. Zapíná se v Nastavení → Obecné → AI v zařízení (výchozí vypnuto),
tam se i stahuje model. Hranice je `core/ai` (`OnDeviceAi`).

### Dva poskytovatelé: Gemini Nano a Gemma

Uživatel volí, KDO model počítá (`ModelVariant.provider`, `AiProvider`): **Gemini Nano** v AICore
(ML Kit, `MlKitOnDeviceAi`), nebo **Gemma 4** přes LiteRT-LM (`LiteRtOnDeviceAi`), kterou si
stáhne aplikace. Volba platí pro celou aplikaci — rozhoduje `RoutingOnDeviceAi` (testy
`RoutingOnDeviceAiTest`) a každá funkce jen předá variantu z nastavení. Nová funkce s modelem
tedy o poskytovatelích nemusí vědět nic.

- **Poskytovatel je pole varianty, ne druhé rozhraní.** Funkce už variantu z nastavení braly
  (`onDeviceAiVariant`); kdyby byl poskytovatel samostatná volba, musela by každá funkce řešit
  dvě věci a jedna by na to zapomněla.
- **Router variantu NORMALIZUJE** (`forGeminiNano`, `forGemma`). ML Kit drží klienta na
  variantu v mapě, takže by jinak změna modelu Gemmy vyrobila druhého klienta téhož Nano.
- **Katalog Gemmy (`GemmaModel`) je převzatý z Google AI Edge Gallery**
  (`model_allowlists/1_0_19.json`): soubor, commit na Hugging Face, velikost a doporučená paměť.
  **Commit je součást adresy**, takže se stahuje pořád tentýž soubor; nová verze = vědomá změna
  katalogu. Verze knihovny (`litertlm-android 0.11.0`) je taky z Gallery — **tagy na GitHubu
  LiteRT-LM (v0.9.0) s verzemi na Google Maven nesedí**, a API se proto ověřovalo proti kódu
  Gallery, který se proti 0.11.0 překládá.
- **Gemma se nenabízí pod Androidem 12, bez arm64 a pod `minMemoryGb`** (8 GB pro E2B, 12 GB
  pro E4B; `totalMem` s 15% rezervou, výrobce udává víc, než jádro nechá). Pod tím by systém
  aplikaci zabil při načítání modelu. Kde model nejde, je `Unsupported` a volba se vůbec neukáže.
- **Uložená volba, kterou zařízení nesplní, se opraví** (`supportedVariant`, testy
  `SupportedVariantTest`): Nano na telefonu bez AICore → Gemma, E4B s málo pamětí → E2B.
  Chybějící uložený poskytovatel je Gemini Nano — tak se aplikace chovala dřív a kdo si ho
  zapnul, nemá po aktualizaci stahovat gigabajty.
- **Stahuje se do `.part` a přejmenovává až hotový**, takže přítomnost souboru = kompletní model.
  Přerušené stahování navazuje (`Range`). Před stahováním se kontroluje volné místo (velikost
  + 500 MB) a nedostatek má vlastní hlášku (`NotEnoughSpaceException`) — u gigabajtů je to
  nejčastější důvod selhání a jediný, se kterým uživatel může něco udělat.
- **Po výpadku sítě stahování naváže SAMO** (`LiteRtOnDeviceAi.fetch` čeká na `NetworkMonitor`).
  Stahuje se minuty a přechod z Wi-Fi na data je za tu dobu běžný; dokud výpadek končil
  „nepodařilo se stáhnout", musel se uživatel vracet do Nastavení kvůli něčemu, co se vlastně
  nepokazilo. Mezitím nastavení říká „Čekám na připojení" (`ModelDownload.waitingForNetwork`).
  Opakuje se jen chyba spojení: odpověď serveru (`HttpStatusException`) ani plný disk čekáním
  nespraví, a po pěti pokusech bez jediného nového bajtu se stahování vzdá. Po vypnutí
  letového režimu přišel na emulátoru ještě DRUHÝ výpadek, jak se síť usazovala — i ten se
  navázal, proto se čeká napřed na síť a teprve pak s prodlevou.
- **Nastavení u Gemmy říká, CO a KOLIK se stáhne** („Gemma 4 E2B je potřeba nejdřív stáhnout
  (2,4 GB)") a během stahování „164 MB z 2,4 GB". Samotné „zabere gigabajty" stačí u Nano, jehož
  velikost nikdo neřekne; tady ji víme. Velikost jde přes `fileSizeText` jako u souborů
  v chatu, takže je v GiB — katalog Gallery uvádí 2,6 GB v desítkových. Po selhání tlačítko
  slibuje „Pokračovat ve stahování": stažený kus se nezahazuje.
- **Stahovaný model jde smazat, systémový ne** (`ModelVariant.appManaged`, `OnDeviceAi.delete`).
- **Gemmu stahuje úloha WorkManageru, ne korutina v procesu** (`ModelDownloadWorker` v `core/ai`,
  vedle `LiteRtOnDeviceAi`). Dokud stahovala korutina, skončilo stahování se zabitým procesem
  a navázalo až dalším ťuknutím v Nastavení. Stahuje pořád `LiteRtOnDeviceAi` (i s čekáním na
  síť), úloha jen drží proces a ukazuje notifikaci průběhu se „Zastavit"; stav pro obrazovku je
  v téže instanci (proto je `LiteRtOnDeviceAi` v Koinu samostatný `single`). WorkManager je kvůli
  tomu i v `:client:shared` — v aplikaci byl už dřív, APK se nezměnilo.
  - **Služba v popředí `dataSync`, jen když ji systém pustí.** Po zabití procesu spustí
    WorkManager úlohu z pozadí a Android 12+ tam službu v popředí odmítne
    (`ForegroundServiceStartNotAllowedException`). Úloha pak stahuje jako běžná a notifikaci
    ukazuje sama; po limitu běžné úlohy ji systém zastaví, WorkManager spustí znovu a `.part`
    naváže. Ověřeno: po `kill -9` do 5 s nový proces bez služby v popředí, stahování pokračovalo
    od 751 MB. Když se aplikace mezitím otevře, rozběhne se úloha i se službou.
  - **`JobCancellationException` JE `IllegalStateException`.** `catch (IllegalStateException)`
    kolem `setForeground` chytal i zrušení úlohy a dokončené stahování vypadalo jako odepřená
    služba v popředí. Zrušení se proto chytá a posílá dál dřív.
  - **Podmínka sítě (`NetworkType.CONNECTED`), ne jen smyčka v `fetch`.** Bez sítě by úloha
    držela službu v popředí hodiny naprázdno. Při ztrátě sítě ji WorkManager zastaví
    (`STOP_REASON_CONSTRAINT_CONNECTIVITY` → obrazovka „Čekám na připojení") a s návratem
    spustí znovu. Krátký výpadek, o kterém systém ještě neví, přečká smyčka jako dřív.
  - **Důvod zastavení rozhoduje, co ukázat** (`getStopReason`): `CANCELLED_BY_APP` = uživatel
    zastavil („Zastavit" v notifikaci je `createCancelPendingIntent`), cokoli jiného = systém
    a úloha se vrátí, takže pro obrazovku se pořád stahuje.
  - **Spuštěná úloha zapíše stav NAČISTO**, i když už nějaký byl: jinak by po návratu sítě visel
    starý příznak čekání, než přijdou první 4 MB.
  - **Zastavit jde z notifikace i z Nastavení** a zčásti stažený model má vlastní stav
    (`OnDeviceAiStatus.Paused`: `.part` je, úloha není) s „Pokračovat ve stahování" a „Zahodit
    staženou část". Stahování, které přežije zabití aplikace, by jinak nešlo zastavit vůbec,
    a gigabajt rozstahovaného modelu by nešel smazat.
  - **Play chce typ `dataSync` vysvětlit** v deklaraci služeb v popředí (Play Console → Obsah
    aplikace), stejně jako `mediaPlayback` u hudby. Bez ní neprojde další release.
  - **Ověřeno na emulátoru sondou** (Pixel_9 se 4 GB, kontrola paměti v `deviceCanRun` dočasně
    vypnutá; necommituje se — stahování na paměti nezávisí): stažení celého E2B (velikost sedí
    na bajt) s notifikací „Model … je stažený", zabití procesu za běhu, „Zastavit" z notifikace
    i z Nastavení, letový režim (čekání, zastavení úlohy, navázání), zahození části a smazání.
    **Release build (R8) s úlohou ověřený není** — workery drží keep pravidla WorkManageru,
    na kterých stojí i notifikační úlohy.
- **Engine je v paměti nejvýš jeden a po dvou minutách nečinnosti se uvolní** — drží gigabajty
  RAM. Načtení trvá až deset sekund, proto ne hned po odpovědi (návrhy chodí po sobě).
  **Generování je jedno po druhém** (zámek): dvě konverzace nad jedním enginem LiteRT-LM neslibuje.
- **GPU, a když nejde, CPU.** GPU backend potřebuje `libOpenCL.so` — `uses-native-library`
  v manifestu aplikace (`required=false`). Na emulátoru GPU selže na OpenGL (`GL_INVALID_ENUM`
  v `delegate_opengl.cc`) a CPU naskočí; na zařízení ověřené není, jestli se GPU chytí.
- **LiteRT-LM v Kotlinu neumí počítat tokeny** (`countTokens` = `null`), takže se přepis jen
  odhaduje z limitu. Limit je velikost KV keše (8 192, stejně jako Gemini Nano) minus místo na
  odpověď — vstup i výstup se do keše musí vejít SPOLU. **Strop výstupu hlídá aplikace sama**
  (`MAX_OUTPUT_CHARS`, pak `Generated.truncated`); konfigurace konverzace žádný nemá.
- **Hugging Face model vydá bez přihlášení** (ověřeno `curl -IL`: 302 na CDN, 200, velikost
  sedí na bajt, `accept-ranges: bytes`). Licenční brána by se projevila 401/403 a tu prototyp
  hlásí jako selhání stahování.
- **Nativní knihovny přidají ~25 MB na ABI** (debug arm64 49,6 → 74,3 MB) a dostane je každý,
  i na telefonu, kde se Gemma nikdy nenabídne. Pro prototyp únosné; před vydáním Play Feature
  Delivery. Knihovna má minSdk 23 (projekt 26), `tools:overrideLibrary` potřeba není.
- **Na emulátoru se Gemma vyzkoušet DÁ — na rozdíl od Gemini Nano.** Stačí AVD s 8 GB RAM
  (`hw.ramSize=8192` v `config.ini`, jinak se E2B kvůli `minMemoryGb` nenabídne); hostitel
  paměť bere až podle skutečné spotřeby, na Macu s 18 GB to po zastavení Gradle daemonů šlo.
  Běží na CPU: shrnutí 32 zpráv zhruba za 40 s, proces drží ~3 GB a po dvou minutách
  nečinnosti spadne na ~300 MB. E4B (12 GB) se na takový Mac nevejde. **Čeština proti Nano
  a GPU se ověřují až na telefonu.**
- **Kvalita E2B je podobná Nano, ne lepší**: fakta drží (data, ceny, číslo účtu), ale
  propsala „v češtině" z instrukce, „Notifikator změnily" a v návrzích se objevil nesmysl
  („Ne, chyba, nebudu přijímat."). Kvůli kvalitě je tu E4B.

- **`core/ai` je hranice** jako `core/telegram`: `com.google.mlkit.genai.*`
  a `com.google.ai.edge.litertlm.*` smí být jen v `androidMain/core/ai`. Poskytovatelé jdou
  přidávat (Apple Foundation Models ze Swiftu) bez zásahu do obrazovek. iOS je zatím `NoopOnDeviceAi`.
- **AICore není API, je to systémová služba** s Gemini Nano; aplikace na ni sahá přes ML Kit
  GenAI Prompt API (`com.google.mlkit:genai-prompt`, beta). Verze se bere z oficiální ukázky
  `googlesamples/mlkit` (`android/genai`) — Google Maven se z cloudového sandboxu stáhnout nedá,
  ukázka na GitHubu ano, a je to jediný spolehlivý zdroj přesného API.
- **Hotová API ML Kitu (Summarization, Proofreading, Rewriting) češtinu NEUMÍ**, jazyk se u nich
  vybírá ze seznamu. Prompt API jazyk nevolí, proto prototyp stojí na něm.
- **Instrukce promptu jsou anglicky, jazyk odpovědi výslovně podle aplikace** (`summaryPrompt`,
  testy `SummaryPromptTest`). Přepis se ořezává ZEZAČÁTKU — shrnutí má říct, co se dělo
  naposledy.
- **Stream Prompt API vydává PŘÍRŮSTKY**, `OnDeviceAi.generate` celý dosavadní text.
- **Varianta modelu je `ModelConfig`** (`ModelReleaseStage` STABLE / PREVIEW, `ModelPreference`
  FULL / FAST; dokumentace „Select a model"). Pro AICore je to JINÝ model: vlastní stav, vlastní
  stažení, vlastní limit. Proto každé volání `OnDeviceAi` bere `ModelVariant` a klient je jeden
  na variantu. Volba je přání — co opravdu běží, říká `getBaseModelName()`.
- **Aplikace nevidí seznam modelů v telefonu ani je nespravuje** — to AICore nedovoluje. Kdo chce
  vybírat konkrétní soubor modelu, potřebuje vlastní runtime (LiteRT-LM).
- **Přepis se přizpůsobuje skutečnému limitu** (`getTokenLimit`, `countTokens`): napřed odhad
  podle limitu (`transcriptCharsFor`, tři znaky na token), pak nejvýš třikrát zkrátit úměrně
  přesahu (`shrunkTranscriptChars`). Pod shrnutím je model, tokeny a kolik zpráv se vešlo.
- **Inferenci AICore pustí jen aplikaci v popředí** — z notifikace nebo úlohy to nepůjde.
- **Sledování stahování běží v `appScope`**, odchod z Nastavení ho neruší; nová obrazovka se ve
  stavu `Downloading` k němu připojí voláním `download()`. Samotné stahování Nano řídí AICore,
  Gemmu úloha WorkManageru (viz výš).
- **Zprávy ke shrnutí dává obrazovka** (`summarySource` nad `state.messages`, testy
  `ChatSummaryTest`), ale historie se kvůli němu nejdřív DOTÁHNE
  (`ConversationViewModel.loadForSummary`). Hned po otevření chatu je načtená jediná stránka,
  takže „posledních 60 zpráv" bralo 30 — a kolik jich bude, záviselo na tom, kolik uživatel
  odscrolloval. Kdy přestat dotahovat, je čistá funkce `summaryHasEnough`.
- **Useknutý výstup se POZNÁ, neodhaduje se z délky** (`Candidate.FinishReason.MAX_TOKENS` →
  `Generated.truncated`). Model přestane psát uprostřed a text vypadá jako hotový; patička
  přitom hlásí VSTUP („583 z 8 192"), takže uživatele naopak uklidní, že se všechno vešlo.
  Ověřeno na Pixelu 11 Pro Foldu: shrnutí dvanácti zpráv skončilo u šesté a podruhé u sedmé —
  strop je v TOKENECH, takže kam text dosáhne, je pokaždé jinde. Kus streamu bez textu nese jen
  `finishReason`, takže se nesmí přeskočit — jinak se useknutí ztratí právě na té zprávě, která
  ho hlásí.
- **Čeština spotřebuje víc tokenů než angličtina**, na kterou jsou výchozí hodnoty laděné; proto
  `MAX_OUTPUT_TOKENS = 800`, ne 400. Není to „čím víc, tím líp" — platí se delším generováním
  (na Foldu 12–35 s).
- **Ověřeno na zařízení** (Pixel 11 Pro Fold, Android 17, AICore `thirdpartyexperimental`):
  `nano-v4-full`, limit vstupu 8 192 tokenů, rychlejší varianta je opravdu jiný model
  (`nano-v4-fast`) a je připravená hned. **Shrnutí funguje i v letovém režimu** — model počítá
  v telefonu, nic se neposílá.
- **Preview varianta se tiše chová jako stabilní.** `checkStatus` vrátí `AVAILABLE` (ne
  `DOWNLOADABLE`) a `getBaseModelName()` dá totéž jméno co stabilní verze, takže přepínač
  nedělá nic. Porovnáno na zařízení všemi čtyřmi kombinacemi: jméno určuje VÝHRADNĚ `fast`
  (`nano-v4-full` / `nano-v4-fast`), `preview` na ně nemá vliv a limit je pořád 8 192.
  Proto `previewSameAsStable` — jména se porovnají a pod přepínač se připíše, že se nepoužije.
- **Preview model v zařízení JE a přes ML Kit se k němu stejně nedostaneme — a je vidět
  proč.** V AICore Labs (`aicore.demo.labs.thirdparty.ThirdPartyLabsActivity`) jde vybrat
  „Gemini Nano 4 Full [Preview, TPU]" a odpoví na prompt; `getBaseModelName()` se zapnutým
  `preview` přesto vrací dál `nano-v4-full`, i po `force-stop`. **V logcatu je příčina:**
  ```
  GenAiProcessorKt: AICore failed with error type 3-PREPARATION_ERROR
    and error code 606-FEATURE_NOT_FOUND: Feature -1 is not available.
  GenAiProcessorKt: checkFeatureStatus failed
  ```
  ML Kit si konfiguraci překládá na číslo AICore „feature" a pro preview mu vyjde **-1**,
  tedy žádné. Chybu spolkne a tiše použije stabilní model. Není to tedy chybějícím modelem
  ani naším voláním — je to mezera v `genai-prompt` (1.0.0-beta4 je nejnovější verze, další
  alfa ani beta na Google Maven není). Hypotéza „stačí preview stáhnout" je vyvrácená
  pokusem. **Má to i kontrolní měření:** totéž shrnutí s VYPNUTÝM preview nechá v logu
  nula výskytů `FEATURE_NOT_FOUND`, model se načte normálně. Bez té kontroly by to bylo
  jen pozorování vedle sebe, ne příčina.
- **Labs nabízí čtyři modely, „Správa AI modelů" ukazuje jediný balík „Fast + Full [TPU]".**
  Proto je rychlá varianta připravená okamžitě a nic se nestahuje: full i fast jsou TENTÝŽ
  balík.
- **Preview Full je postavený na Gemma 4 E4B**, zatímco stabilní na E2B — tedy větší model
  a nejnadějnější směr pro kvalitu češtiny. **Cesta k němu přes ML Kit ale nevede** (viz výš)
  a seznam modelů API nemá: `GenerativeModel` zná jen `getBaseModelName()` pro právě
  nakonfigurovanou variantu. Kdo na E4B chce, potřebuje vlastní runtime (LiteRT-LM).
- **Na emulátoru se to vyzkoušet NEDÁ, ani v nejnovějším obrazu.** Android 17 (API 37,
  `google_apis_playstore_ps16k`) balíček `com.google.android.aicore` nemá; starší obrazy taky ne.
  **Řetězce „aicore" v `system.img` k ničemu neslouží jako důkaz** — `grep` jich najde desítky,
  ale jsou to klientská rozhraní `com.google.android.apps.aicore.aidl.*`, která si nese Dialer,
  autofill a detekce spamu. Rozhodne až `pm list packages` na běžícím systému.
- **Doinstalovat AICore z telefonu JDE a stejně to nepomůže.** Je v `/data/app` a není privileged
  (`flags=0x0`), takže `adb pull` tří split APK z Pixelu (128 MB) a `install-multiple` na emulátor
  projde, **služba nastartuje a ML Kit se k ní připojí** (v logu `Start proc … for bound-service
  AiCoreMultiUserService`). Pak ale přijde `606-FEATURE_NOT_FOUND: Feature 636 is not available`
  a v logu `Failed to fetch manifest config from source`: AICore v emulátoru nemá model ani
  hardware, na kterém by počítal. Sonda vrátila `status=Unsupported`, `countTokens=null`,
  `generate` selhalo — tedy **aplikace se zachová správně a nic nespadne**, což je to jediné, co
  se tím ověří.
- **`Feature 636` versus `Feature -1` je rozdíl, který něco říká.** Tady si ML Kit číslo featury
  odvodil správně a AICore ji nemá; u preview varianty na zařízení vyjde `-1`, tedy žádná. Je to
  nezávislé potvrzení, že `-1` je mezera v `genai-prompt`, ne chybějící model.
- **Detail k `info()`:** s doinstalovaným AICore vrátila `ModelInfo(name=null, tokenLimit=8192)`,
  tedy NENULOVÝ objekt bez jména. Na UI se to neprojeví (rozhoduje `status`), ale kdo by se ptal
  jen na `info() != null`, dostal by na nepodporovaném zařízení „model je".
- **Co na emulátoru ověřit JDE:** chování bez modelu (že se sekce v Nastavení nenabídne a nic
  nespadne — viz „Návrhy odpovědí"), vzhled podstrčeným stavem a čisté funkce testy. Na zařízení
  zbývá kvalita výstupu, rychlost, limit tokenů a useknutí.

### Návrhy odpovědí

Přijde zpráva, pole je prázdné → nad polem se ukážou až tři čipy s krátkou odpovědí. Ťuknutí ji
**VLOŽÍ do pole, neodešle**: text píše model a uživatel se pod něj podepisuje. Vlastní vypínač
v Nastavení (shrnutí ho nemá — otevírá se vědomě ťuknutím, kdežto návrhy vyskočí samy).

- **Čipy, ne bublina v konverzaci.** Průsvitná „duchová" bublina na místě, kde by odpověď byla,
  vypadá jako odeslaná zpráva a jednou za čas by si někdo myslel, že už odešla. Tenhle vzor znají
  lidé z Gmailu a Google Zpráv.
- **Krátká odpověď je RYCHLÁ.** Inference 0,2–0,4 s (měřeno v logu AICore mezi
  `CreateSessionWithSSV1` a `nativeUnload`), zatímco shrnutí 12–35 s. Rozdíl nedělá vstup, ale
  délka VÝSTUPU — našeptávání je tedy použitelné, i když shrnutí působí pomalu.
- **Poslední zpráva jde do promptu ZVLÁŠŤ a až za kontextem.** V jednom slepeném přepisu zanikne
  a model odpovídá na rozhovor jako celek: do chatu plného zpráv o výletu přišlo „Dáme v sobotu
  kafe?" a návrhy zněly „Jsem připraven." a „Dobré, pak se." Se zdůrazněnou poslední zprávou
  („Reply to THIS message") a kontextem zkráceným z 10 na 6 zpráv přišlo „Neděle mi vyhovuje."
- **Tykání/vykání se musí říct výslovně.** Bez instrukce model kamarádovi VYKAL („Co byste
  předpokládali?"). Instrukce jmenuje „tykání" i „vykání" česky — samotné „be informal" model do
  správného tvaru slovesa nepřeloží. Když z konverzace poznat nejde, tyká se: v Telegramu si lidé
  tykají mnohem častěji a tykání cizímu je menší prohřešek než vykání kamarádovi.
- **Jazyk se volí jinak než u shrnutí.** Shrnutí čte uživatel → jazyk aplikace. Návrh se ODEŠLE
  protějšku → jazyk konverzace; jazyk aplikace je jen nápověda, protože jinak model sklouzne
  (na nejednoznačný vstup odpověděl polsky).
- **Návrh se dělá JEDNOU na zprávu** (`ReplySuggestionsViewModel` si drží id). Bez toho by se
  inference pouštěla při každé změně stavu konverzace — přečtení, reakce, scroll.
- **Kdy nabídnout, je čistá funkce** `replySuggestionTarget` (testy `ReplySuggestionsTest`):
  poslední zpráva příchozí a neservisní, nic rozepsaného, neupravuje se, smí se psát. Vrací id
  zprávy, ne `Boolean` — obrazovka podle něj pozná, že jde o jinou zprávu, a nemusí si pamatovat nic.
- **Výstup modelu se parsuje tolerantně** (`parseReplySuggestions`, testy `ReplyPromptTest`):
  model odrážky nedrží, očísluje je, obalí uvozovkami nebo přidá úvodní větu s dvojtečkou.
  Nikdy nevrátí prázdno kvůli formátu — když řádky rozpoznat nejdou, je návrhem celý text.
- **Na zařízení bez AICore se nesmí nic stát a nestane.** Ověřeno na TŘECH systémech —
  Android 16, Android 9 (API 28, Play služby 21.45.15) a **Android 8 (API 26, tedy `minSdk`
  projektu)** —, všude bez balíčku `com.google.android.aicore`. Pokaždé stejně: `status` vrátí
  `Unsupported` (takže se ikona Shrnout ani čipy nenabídnou), `countTokens` `null` a `generate`
  skončí VÝJIMKOU `GenAiException [ErrorCode -101] AICore is either not installed or the
  installed version is too low`. Žádný pád, v logu aplikace čisto. Stáří systému ani verze Play
  služeb na tom nic nemění.
- **Na nepodporovaném zařízení se sekce v Nastavení VŮBEC NENABÍZÍ.** Dřív se vykreslila vždycky
  a uživatel zapnul přepínač, aby se teprve pak dozvěděl „tohle zařízení model nemá" — nabídka
  funkce, kterou nelze mít. Stejné pravidlo jako u archivace od neznámých (viz „Archiv“). Ptá se na VÝCHOZÍ
  variantu, ne na zvolenou: preview nebo rychlá může chybět zvlášť a kvůli tomu by sekce zmizela
  i tomu, kdo model má. Dokud stav nedorazí, taky se nekreslí — objevení ruší míň než zmizení.
- **Ověřuje se to sondou, ne proklikáním.** Do Nastavení se bez přihlášení nedostaneš, takže se
  do `startKoin` dočasně přidá volání `status`/`info`/`countTokens`/`generate` v `runCatching`
  a výsledek se zaloguje. Sonda se NECOMMITUJE — postaví se z ní APK a zdroják se hned vrátí.
- **Staré arm64 obrazy na Apple Silicon zamrzají.** Android 9 i 8 spadly na
  „detected a hanging thread QEMU2 main loop", dokud emulátor nedostal
  `-gpu swiftshader_indirect -cores 2`. Není to chyba aplikace a stojí to deset minut hledání.
- **Chyba ale nemusí přijít jako výjimka.** Kde chybí systémová část (staré Play služby, ořezaný
  ROM), vyletí `Error` — `NoClassDefFoundError`, `UnsatisfiedLinkError`, `VerifyError` —, a ten
  `catch (Exception)` NECHYTÍ. `MlKitOnDeviceAi` proto chytá i `LinkageError`: aplikace nesmí
  spadnout jen proto, že si někdo zapnul volitelnou funkci na starším telefonu.
- **Zjišťování dostupnosti má `catch`, i když „nemůže" selhat.** `available` je `flow { … }`
  v `stateIn(viewModelScope)`; cokoli, co by z něj vyletělo, shodí scope, tedy ViewModel celé
  obrazovky. Nepodařilo se zjistit = nenabízet.
- **AICore umí model rozbít a `checkStatus` pořád hlásí `AVAILABLE`.** Na Pixelu 11 Pro Foldu
  se po aktualizaci AICore (a restartu) Gemini Nano přestal nacházet ve vlastních souborech
  (`NOT_FOUND: …/aicore-models/symlinks/…/main/config.binarypb`) a každé generování skončilo
  `GenAiException` s kódem 5 (`COMPUTE_ERROR`, ML Kit ho mezi `ErrorCode` nemá). Featura byla
  v AICore `ACTIVE`, Plume model nabízela — a uživatel viděl jen „Shrnutí se nepovedlo", den
  a půl. **Spravilo to vymazání úložiště AICore** (`adb shell pm clear com.google.android.aicore`
  nebo Nastavení → Aplikace → AICore), pak si model stáhl znovu.
- **Kde to hledat:** `adb shell dumpsys activity service com.google.android.aicore` má historii
  posledních inferencí (`caller_package_names=eu.tintera.plume … status=N`; 1 = úspěch) a stav
  každé featury (`feature_647: ACTIVE` / `PENDING (DOWNLOADING)`). Plume volá featuru 647
  (`nano-v4-full`). Chyba samotná je v logu procesu AICore (`AiCoreInferenceHelper`).
- **Selhání generování nese důvod** (`OnDeviceAiException` s `AiFailureReason`, převod z kódu
  ML Kitu v `MlKitOnDeviceAi.toAiFailure`). Sheet shrnutí ho říká větou, u rozbitého Nano
  i s kódem a radou vymazat úložiště AICore nebo přepnout na Gemmu. Do Crashlytics jako
  problém (`e` s výjimkou) jde jen rozbitý / nedostupný model (`isReportable`), vytížený,
  dlouhý vstup a plný disk zůstávají `w`. Návrhy odpovědí se pouštějí samy u každé zprávy,
  takže hlásí jen první selhání za běh procesu.
- **Sloh je pořád slabina.** Tykání i věcnost sedí, ale mezi třemi návrhy bývá jeden nesmysl
  („Ne, zítra mi je zajímavé."). To je limit modelu, ne promptu.

- **Fakta model drží, sloh ne.** Na připraveném testu (ceny, číslo účtu, datum, jména) nevymyslel
  nic — ale na skutečné konverzaci komolí tvary („telefon nebudou mít vibrace“), plete si, kdo co
  řekl, občas propíše do textu instrukci („shrnutí konzultace **v češtině**“) a u pár zpráv nafoukne
  zdvořilosti na plnohodnotné body. Když to nebude stačit, další krok je Gemma 4 přes LiteRT-LM
  za stejným rozhraním.

## Aktualizace z obchodu

Android nabízí novou verzi z Play kartou nahoře v seznamu chatů (`UpdateCard`) přes Play
In-App Updates v režimu **flexible**: stahuje se na pozadí, pak „Restartovat" (`completeUpdate`).

- **`core/update` je hranice**, stejně jako `core/telegram` a `core/logging`:
  `com.google.android.play.*` smí být jen v `AppUpdates.android.kt`. Zbytek vidí `AppUpdateState`.
- **`AppUpdates` je `expect class`, ne rozhraní** — jako `AppForeground`. Spuštění aktualizace
  potřebuje activity (dialog obchodu), a singleton v grafu závislostí ji nemá; proto je start
  composable `rememberStartUpdate` a platformní člen `start(launcher)`, na který se ze společného
  kódu nedosáhne.
- **Dialog se spouští přes `ActivityResultLauncher`** (`startUpdateFlowForResult` s launcherem),
  ne přes activity. Výsledek se nečte: průběh hlásí `InstallStateUpdatedListener`, zrušený
  dialog nechá stav na „k dispozici".
- **Ptá se při každém resume seznamu chatů** (`LifecycleResumeEffect`). Dotaz jde do aplikace
  obchodu v telefonu, ne na server, a právě tak se po restartu připomene stažená, ale
  nenainstalovaná aktualizace (`installStatus == DOWNLOADED`) — jinak by visela napořád.
- **Immediate režim ne.** Celoobrazovková aktualizace by přerušila rozepsanou zprávu.
- **Zavřít jde jen nabídku, a pamatuje se podle `versionCode`** (`AppSettings.dismissedUpdateVersionCode`).
  Stahování a staženou aktualizaci si uživatel vyžádal sám, ty se ukazují vždycky. Pravidlo je
  čistá funkce `updateCard` (testy `UpdateCardTest`).
- **Mimo Play to nefunguje a není to chyba.** Debug build i `adb install` dostanou od obchodu
  chybu (na emulátoru `-6 ERROR_INSTALL_NOT_ALLOWED`) nebo „není k dispozici"; loguje se jako
  `i`, ne `w`, a karta se neukáže. Celá cesta se ověřuje přes internal app sharing.
- **Vzhled karty se ověřuje podstrčeným stavem**: v `addOnFailureListener` dočasně nastavit
  `AppUpdateState.Available(999)`, nainstalovat debug a vrátit. Jiná cesta, jak kartu v debugu
  vidět, není.
- **Artefakt je `app-update`, ne `app-update-ktx`.** Task API stačí a ktx by táhl
  `kotlinx-coroutines-play-services` kvůli dvěma voláním.
- **Karta je další blok seznamu**: zaoblení `GROUP_CORNER`, mezera pod ní jako mezi skupinami,
  `primaryContainer` (od světlých řádků ji odliší, sytou barvu nese jen tlačítko). Jedna karta
  pro všechny tři stavy s `animateContentSize` — výměna celé položky by vypadala jako nová.
- **Syté prvky karty jdou přes `accentOn`, ne `primary` natvrdo.** V paletě se zvýšeným
  kontrastem je `primaryContainer` v tmavém režimu světlý jako `primary`, takže „Teď ne" bylo
  bílé na světle modré a kolečko ikony s tlačítkem splývaly s kartou. Kde `primary` na kartě
  vidět není, převezme jeho roli `onPrimaryContainer` (viz „Material 3 / vzhled").
- **Ikona je `system_update` (telefon se šipkou dovnitř), ne `upgrade`.** Šipka nahoru k čáře se
  čte jako NAHRÁVÁNÍ — a to je přesně opačný směr, než co karta nabízí. Ikona má říct, co se
  stane, ne jakým gestem se to spustí.

## Pracovní postup

**Každá změna jde přes samostatnou větev a pull request, nic se necommituje rovnou do `main`.**

- Větev se zakládá z aktuálního `main` a pojmenovává podle obsahu (`fix/…`, `feat/…`,
  `docs/…`). Jedna větev = jedna ucelená změna.
- **Průběžné dolaďování jedné oblasti je JEDEN PR s více commity**, ne řada PR postavených
  na sobě. Když uživatel ladí tutéž věc v několika kolech (vzhled nabídky nad zprávou, jeden
  blok konverzace…), každé kolo je commit v téže větvi a popis PR se průběžně doplňuje.
  Řada navazujících PR (#80–#87 u nabídky) se musela slučovat v přesném pořadí a každý
  čekal na přepnutí cíle na `main` — práce navíc bez užitku, protože se stejně slučovaly
  všechny najednou. Nová větev a nový PR až pro jinou oblast.
- Do `main` se dostane jen sloučením PR. Slučuje uživatel, ne agent — ani automatické sloučení
  se nezapíná bez výslovného pokynu.
- Popis PR říká, CO se změnilo a PROČ, a jak to ověřit na zařízení. Když změna na zařízení
  vyzkoušená není, musí to v popisu stát.
- Poučení z opravy patří do tohohle souboru ve stejném PR jako oprava samotná.
- **Každý PR přidá poznámku k vydání** `release-notes/<větev>.md` (`/` → `-`), jinak neprojde
  check „Release notes". Viz „Poznámky k vydání" níž.

## Poznámky k vydání

Text „Co je nového" v Google Play se skládá z poznámek, které nese každý PR:

```
type: feat
cs: Hledání v seznamu chatů
en: Search in the chat list
```

`type` je `feat`, `fix`, nebo `internal` (dokumentace, CI, refaktoring — pak bez `cs`/`en`).
Formát, ořez i skládání jsou v `.github/scripts/release_notes.py`.

**Chybějící poznámku hlídá `pre-push` hook, ne až CI** (`.githooks/pre-push`, zapíná se jednou
na klon: `git config core.hooksPath "$(git rev-parse --show-toplevel)/.githooks"`; obejít jde
`git push --no-verify`).

- **Hlídá se až při pushi, ne při commitu.** U vícekolového PR přijde poznámka běžně až s druhým
  commitem, takže pre-commit by hlásil falešný poplach u každého předchozího. Push je okamžik,
  kdy z větve vzniká PR.
- **Tagy a `main` hook přeskakuje.** Release se vydává pushnutím tagu `android-…` a poznámky
  v něm už dávno jsou — blokovat ho by znamenalo rozbít vydávání. Do `main` se nic nedostane
  jinak než sloučením PR, který kontrolou prošel.
- **Formát se čte Z PUSHOVANÉHO COMMITU** (`git show`), ne z pracovního stromu: pushnout jde
  i starší commit, a hlídat se má to, co uvidí CI.
- **`core.hooksPath` musí být ABSOLUTNÍ cesta.** Relativní se počítá od pracovního stromu, do
  kterého se pushuje — a worktree (`.claude/worktrees/…`) stojí na vlastní větvi, kde `.githooks`
  být nemusí, protože je starší než hook. Hook tam pak neběžel a **nijak se to neohlásilo**: git
  na chybějící cestu k hooku nenadává. Config je mezi worktrees sdílený, takže absolutní cesta
  se nastavuje jednou a platí pro všechny.
- **Skript se hledá vedle hooku, teprve pak v pushovaném stromu.** Z worktree se pushuje větev,
  která `.github/scripts/` mít nemusí; hook naopak leží v hlavním stromu, kam absolutní cesta
  míří. Když není ani jeden, kontrola formátu se přeskočí a zbyde na CI — hook nemá blokovat
  push kvůli tomu, že si na něco nedosáhl.
- **Kontrolu dělá TENTÝŽ skript jako workflow.** Dvě kopie pravidel by se rozešly a lokální
  kontrola by začala pouštět dál, co CI zamítne. `release_notes.py` proto jen podle
  `GITHUB_ACTIONS` přepíná, jestli chybu vypsat jako anotaci GitHubu, nebo jako větu.

- **Píše se pro uživatele, ne pro vývojáře.** „Hledání v seznamu chatů", ne „ChatSearchBar přes
  AppBarWithSearch". Co uživatel nepozná, je `internal` — i tak se soubor přidává, jinak by
  nešlo odlišit „nic pro uživatele" od „zapomenuto".
- **Nejvýš 100 znaků na jazyk.** Play bere 500 znaků na jazyk za celý release, takže se vejde
  pět poznámek. Co se nevejde, nahradí řádek „…a další drobné úpravy"; novinky jdou před
  opravami. Upload kvůli délce nikdy neselže.
- **Soubor v repu, ne sekce v popisu PR.** Release čte poznámky přidané mezi předchozím
  a aktuálním tagem PŘÍMO Z TAGU (`git show <tag>:…`), takže jsou z tagu reprodukovatelné
  stejně jako verze a nepotřebují GitHub API. Oprava textu po sloučení = další commit, který
  soubor upraví; do vydání se dostane, pokud je před tagem.
- **Soubory se po vydání nemažou.** Rozsah určuje `git log --diff-filter=A PREV..TAG`, mazání
  by znamenalo commit z release workflow — a verze ani nic jiného se z releasu necommituje.
- **Více kol téhož PR = úprava téhož souboru**, ne další soubor.
- **Do Play jdou jazyky z `PLAY_LANGS`** (čeština a angličtina). Jazyk poznámky musí být
  v záznamu aplikace v obchodě (`play/listing/`, workflow Play Listing), jinak hrozí, že API
  odmítne celý upload. Úplně nový jazyk = řádek v `LOCALES`, klíč v každé poznámce, záznam
  v `play/listing/` — a do `PLAY_LANGS` až PO jeho nahrání.
- GitHub Release dostane celý seznam v obou jazycích (bez ořezu) nad technickým changelogem.

### „Co je nového" v aplikaci (`core/changelog`)

Pátá sekce Nastavení (`WhatsNewSettings`) ukazuje tytéž poznámky po vydáních, od nejnovějšího.
Po aktualizaci nic nevyskakuje — novinky jsou tam, kdo je hledá (převzato z mlogu, bez jeho
`WhatsNewGate`).

- **Poznámky se zapékají při buildu**: gradle task `:client:shared:generateChangelog` pustí
  `release_notes.py bundle` a vygeneruje `GeneratedChangelog.kt` do
  `build/generated/changelog/kotlin`, který je zdrojovým adresářem `commonMain`. **Tentýž skript
  jako pro Play** — dvě kopie pravidel (co je `internal`, rozsah mezi tagy) by se rozešly.
- **Kotlin, ne compose resource**: seznam je dostupný synchronně a chyba generátoru spadne při
  překladu. Vygenerovaný soubor leží v `build/`, takže ho `verifyNoHardcodedText` nečte.
- **Jen tagy `--merged HEAD`**: build ze starší větve nesmí ukazovat verze, které v něm nejsou.
  Release se staví z tagu, takže aktuální vydání v seznamu je; tagy se řadí podle verze a `-manual`
  projde. Vydání jen s `internal` poznámkami se vynechá.
- **Task je schválně nikdy up-to-date** (vstupem jsou i tagy) a trvá asi 2 s; překlad se přeskočí,
  když vyjde stejný obsah. **Bez pythonu nebo gitu build nespadne**: zapíše se prázdný seznam
  (sekce pak řekne, že sestava novinky nemá) a důvod se zaloguje. CI klonuje s `fetch-depth: 0`,
  bez tagů by seznam byl prázdný.
- **Jazyk poznámky se bere z prostředků** (`changelog_note_language` = `cs`/`en`), ne z locale.
  Poznámka tak vždycky sedí s jazykem, který Compose vybral pro zbytek obrazovky, i s jazykem
  nastaveným jen pro aplikaci — a funguje stejně na iOS, kde `NSLocale.currentLocale` jde za
  regionem systému (viz „Výběr země a formát čísla").
- **Vydání po deseti s „Zobrazit starší verze"**: sekce je sloupec, ne lazy seznam, a celá
  historie má stovky řádků.
- **Novinku od opravy odliší tvar ikony** (raketa, brouk), čtečce obrazovky slovo v popisu ikony.
- **Ověřeno na emulátoru** (anglicky i česky přes `set-app-locales`, stránkování, přežití změny
  jazyka). Na iOS jen přeložené a slinkované.

## Struktura modulů

```
client/
  shared/      :client:shared     – KMP knihovna klienta (JEDINÝ modul s KMP pluginem)
  androidApp/  :client:androidApp – com.android.application, jen vstupní bod
iosApp/        – Xcode projekt; framework `ComposeApp` produkuje :client:shared
```

**AGP 9 zakazuje `com.android.application` + KMP plugin v jednom modulu.** Proto je klient rozdělený
na tenké vstupní body nad jednou sdílenou KMP knihovnou. Platformní `expect`/`actual` zůstávají
v `:client:shared`, ne v app modulech.

### Source-set hierarchie v `:client:shared`

- `commonMain` – `App.kt`, `di/`, `app/`, `core/`, `features/`
- `nonDynamicThemeMain` (→ ios) – platformy bez Material You. Zatím jen iOS, ale až přibude desktop
  (tdl-coroutines publikuje i JVM targety), spadne sem taky.
- `androidMain`, `iosMain` – platformní `actual`

## Hranice kolem TDLib — nejdůležitější pravidlo projektu

Balíček `core/telegram` je **jediné místo, kde smí být `import dev.g000sha256.tdl.*`**. Zbytek
aplikace vidí jen doménová rozhraní: `TelegramSession` (přihlášení), `ChatRepository` (seznam
chatů), `ConversationRepository` (obsah konverzace), `FileRepository` (soubory a média). Klienta
sdílí přes `TelegramClientHolder` — TDLib klient je jeden na proces.

Proč to tak striktně:

1. **Upstream TDLib mění API i v patch verzích.** Wrapper to dokumentuje explicitně. Když se to stane,
   přepíše se jeden balíček a zbytek projektu se nezmění.
2. **`tdl-coroutines` je komunitní projekt jednoho autora.** Kdyby přestal vycházet, nahradí se vlastním
   `cinterop` nad C API `tdjson` (`td_create_client_id`, `td_send`, `td_receive`, `td_execute`) — zase
   pod stejným rozhraním.
3. ViewModely se testují proti `TelegramSession`, ne proti TDLib.

**Volej generované metody pojmenovanými argumenty.** Upstream do nich přidává a přejmenovává parametry;
poziční volání by se tiše rozsypalo. Platí hlavně pro `setTdlibParameters` (14 parametrů).

### Chybové hlášky pro uživatele

**Text z TDLib se uživateli nevypisuje, nikdy.** Překládá ho `userMessage(fallback)`
v `core/telegram`; surová hláška jde do logu (`logger.w` s výjimkou), a tím i do kontextu
hlášení pádů.

- **Chyby chodí ve dvou podobách a ani jedna není věta pro člověka.** Konstanta od serveru
  (`PHONE_CODE_INVALID`), nebo anglická věta od samotné TDLib („Have no write access to the
  chat", „Invalid chat identifier specified"). Které volání vyrobí kterou, se dopředu neuhádne —
  **znění se dá ověřit v knihovně**: `strings libtdjsonjava.so | grep …` najde ty, které si TDLib
  píše sama. Co tam není, přichází ze serveru.
- **`failure.message ?: "Česká věta"` je PAST, ne pojistka.** Takhle to bylo na dvaceti místech
  a ta česká věta byla mrtvý kód: výjimka z TDLib má `message` vždycky vyplněné, takže elvis
  nikdy nesepnul a uživatel viděl vždycky tu anglickou hlášku. Pořadí je proto obrácené —
  základ je věta od nás a hláška z TDLib se použije jen tam, kde z ní umíme vyrobit něco
  konkrétnějšího.
- **Tabulka překladů roste podle logu, ne podle fantazie.** Pár hlášek stojí za vlastní větu
  (limit připnutých chatů, vypršelý kód), zbytek spadne na text volajícího. Až se v logu objeví
  něco, co uživatele mate, přibude řádek.
- **Text od TDLib je i prosakování hranice.** Kdyby šel na obrazovku, měnila by novější verze
  TDLib text v UI — tedy přesně to, čemu má balíček `core/telegram` bránit.

### Odpovědi TDLib, které wrapper nerozebere

**`tdl-coroutines` si nullabilitu pole odvozuje JEN z textu dokumentace** (`td_api.tl`: „be null",
„pass null"; generátor `TdlGenerator.isNullable`). Povinné objektové pole čte `getValue`, takže
když ho TDLib vynechá, vyletí `NoSuchElementException: Key … is missing in the map` na vlákně
`TDL-Receiver-Thread` — a **shodí celý proces**. Přijímací smyčka (`TdlEngine`) je `launch` bez
ošetření chyb a engine je `lazy` na celý proces: i kdyby se výjimka spolkla, smyčka už skončila
a nepřijde jediná další odpověď ani update. Spolknout ji proto nejde, musí se jí předejít.

- **Známý případ: `gift.background`.** Dokumentace ho vede jako povinné, ale
  `StarGift::get_gift_object` (TDLib `StarGift.cpp`) nechá pozadí `nullptr`, když ho server
  k dárku nepošle. Týká se `getAvailableGifts` i zpráv `MessageGift` /
  `MessageRefundedUpgradedGift` — chat s takovým dárkem (i jako poslední zprávou v seznamu chatů)
  by aplikaci shazoval pokaždé znovu. Ve wrapperu 15.0.0 (nejnovější) opravené není; nahlášené
  jako [g000sha256/tdl-coroutines#162](https://github.com/g000sha256/tdl-coroutines/issues/162)
  (s kandidáty níž a návrhem, ať přijímací smyčka jednu vadnou odpověď přežije).
- **Obcházka: odpověď se opraví dřív, než ji wrapper dostane** (`TdlibJsonRepair`, testy
  `TdlibJsonRepairTest`). Tabulka „typ → chybějící pole → výchozí objekt" doplní pole, které
  TDLib vynechal; aplikace `Gift.background` nečte, takže na hodnotě nezáleží. Odpověď bez
  známého typu se neparsuje (hledá se podřetězec `"@type":"gift"`), oprava nikdy nevyhodí.
  **Do tabulky patří jen pole ověřená pádem nebo zdrojem TDLib**, ne preventivně.
- **Na Androidu ji zapojuje instrumentace bajtkódu** (`TdlibReceiveHookFactory`
  v `client/androidApp/build.gradle.kts`, `AsmClassVisitorFactory`, `InstrumentationScope.ALL`):
  za jediné volání `org.drinkless.tdlib.JsonClient.receive` uvnitř wrapperu vloží
  `TdlibReceiveHook.afterReceive`. Jiná cesta dovnitř není — smyčka i parser jsou interní
  a knihovna je obfuskovaná (`dev.g000sha256.tdl.a.mt0.D6` = `deserializeGift`); `JsonClient`
  si ale jméno nést musí kvůli JNI. Ověří se `dexdump -d` nad APK (`invoke-static …
  TdlibReceiveHook;.afterReceive` hned za `JsonClient;.receive`).
- **Že vložení zabralo, hlídá `TdlTelegramSession` po přihlášení**: do `Ready` musí odpovědi
  hookem projít, jinak `logger.e` s výjimkou (v Crashlytics jako problém). Bump wrapperu nebo
  přejmenování `TdlibReceiveHook` by obcházku tiše rozpojily.
- **iOS obcházku NEMÁ** (`tdlibJsonRepairWired = false`): Kotlin/Native klib přepsat nejde.
  Dárek bez pozadí tam aplikaci dál shodí, dokud to neopraví wrapper.
- **Kandidáti téhož druhu** (sken zdroje TDLib 1.8.67: `nullptr` do konstruktoru, dokumentace
  o null mlčí; za běhu neověřené, aplikace je nevolá): `product_info` u
  `starTransactionTypeBotInvoicePurchase/Sale` a `…BotSubscriptionPurchase/Sale`.
- **Při bumpu wrapperu**: napřed stav issue #162 výš, pak se podívat, jestli `deserializeGift`
  čte `background` přes `getObjectNullable` (zdrojový jar
  `tdl-coroutines-android-<ver>-sources.jar`, soubor `TdlDeserializer.kt`); když ano, řádek
  z tabulky pryč. Instrumentace i kontrola po přihlášení
  můžou zůstat — bez známých případů nestojí nic.

### Bump TDLib wrapperu

Verze Kotlinu v projektu **musí být ≥ verze, ze které je wrapper publikovaný** — jinak se jeho iOS klib
nenačte. Před bumpem zkontroluj:

```bash
curl -s https://repo1.maven.org/maven2/dev/g000sha256/tdl-coroutines-iosarm64/<ver>/tdl-coroutines-iosarm64-<ver>.module | grep -A2 kotlin-stdlib
```

## Šifrovaná databáze TDLib

TDLib drží historii zpráv v SQLite databázi v adresáři aplikace a šifruje ji klíčem, který dostane
v `setTdlibParameters`. Klíč je 32 náhodných bajtů z **`core/secrets`** — další hranice vedle
`core/telegram`, `core/logging` a `core/update`: `java.security.KeyStore` a `platform.Security.*`
smí být importované jen tam.

- **`DatabaseKeyStore` je rozhraní, ne `expect class`** (jako `ShareTargetPublisher`
  a `CrashReporter`). Na jeho hodnotách visí rozhodnutí, jestli se smažou lokální data, a to chce
  test s podstrčeným úložištěm — `expect class` se v `commonTest` nahradit nedá.
- **Klíč NESMÍ vyžadovat ověření uživatele.** Push budí mrtvý proces, když telefon leží zamčený
  v kapse, a TDLib potřebuje klíč dřív, než se složí jakékoli UI. Klíč za otiskem prstu znamená,
  že z pozadí nechodí notifikace vůbec. Z téhož důvodu je iOS `kSecAttrAccessibleAfterFirstUnlock…`,
  ne `WhenUnlocked`.
- **Tím je daná i mez.** Klíč dostane každý kód běžící pod naší aplikací: chrání to proti vytažení
  disku, záloze, `adb backup` a forenznímu čtení souborů, ne proti rootnutému zařízení s běžící
  aplikací. Stejný kompromis dělá i oficiální klient — jen to musí být napsané, aby se to za rok
  nepletlo za víc.
- **Android Keystore neumí uložit bajty, jen klíče.** To je past, na které se to nejčastěji láme:
  `AndroidKeyStore` není trezor na data. Klíč se proto obaluje AES-GCM klíčem z Keystore a na disku
  leží jen šifrotext (`filesDir/tdlib-key`, `[délka IV][IV][šifrotext]`). `androidx.security:security-crypto`
  dělá totéž, ale je to závislost navíc kvůli padesáti řádkům — stejná úvaha jako u `app-update`
  proti `app-update-ktx`.
- **`setInvalidatedByBiometricEnrollment` se NENASTAVUJE.** Přidaný otisk prstu by zneplatnil klíč
  a uživatel by přišel o přihlášení kvůli nastavení telefonu.
- **Soubor s klíčem se píše přes dočasný soubor a přejmenování.** Zabitý proces uprostřed zápisu
  by nechal na disku uříznutý klíč — a s ním databázi, která už nejde otevřít.
- **Na iOS klíč NEPATŘÍ do App Group.** Rozšíření sdílení TDLib nespouští a databázi nevidí,
  takže `kSecAttrAccessGroup` by jen rozšířil plochu bez užitku.
- **`ThisDeviceOnly`, a proto se adresáře TDLib vylučují ze zálohy** (`NSURLIsExcludedFromBackupKey`
  v `TelegramPaths.prepare()`). Klíč se na jiné zařízení nepřenese, takže obnovená databáze by se
  stejně zahodila — jen by do zálohy putovala celá historie zpráv. Na Androidu to řeší
  `allowBackup="false"` pro celou aplikaci. `prepare()` běží před KAŽDÝM `setTdlibParameters`,
  ne jednou při startu: adresáře vznikají znovu i po smazání nečitelné databáze a na jediném
  místě se na to nedá zapomenout.

### Pořadí, migrace a cesta ven

Pravidlo je čistá funkce `unlockDatabase` (`core/telegram/DatabaseUnlock.kt`, testy
`DatabaseUnlockTest`), ne tělo session: pořadí kroků je to jediné, co se na tom dá rozbít, a uvnitř
třídy s živým klientem by se to neotestovalo.

- **Klíč se ukládá DŘÍV, než se jím databáze zašifruje.** Obrácené pořadí („nejdřív zašifrovat,
  pak uložit") má okno, ve kterém zabitý proces nechá zašifrovanou databázi bez klíče — a to je
  nenávratné odhlášení. Takhle je nejhorší možný stav ten, že databáze za klíčem zaostává, a ten
  se pozná a dožene.
- **Kroky jsou tři a každý pojmenovává jeden stav světa:** otevřít uloženým klíčem (běžný start,
  jedno volání jako dřív) → nejde to, otevřít prázdným a zašifrovat (instalace z verze bez
  šifrování i přerušená migrace) → nejde ani to, databáze je zašifrovaná klíčem, který nemáme,
  takže se zahodí. První spuštění uspěje hned v prvním kroku: databáze ještě není a TDLib ji
  založí rovnou zašifrovanou, takže nikdy nevznikne ani na okamžik nešifrovaná.
- **Databázi s prázdným klíčem NELZE otevřít neprázdným** — TDLib vrátí 401 (hláška
  „Wrong database encryption key", ověřeno `strings libtdjsonjava.so | grep "encryption key"`).
  Přešifruje se až otevřená databáze, přes `setDatabaseEncryptionKey`. Není to tedy „změnit jeden
  řádek": bez migračního kroku by se každá existující instalace odhlásila.
- **Mazat se smí JEN na chybu o klíči.** Kdyby se mazalo při každém selhání `setTdlibParameters`,
  odhlásil by uživatele plný disk nebo překlep v `api_id`. Sedí na to test — a ověřilo se
  pokusem, ne úvahou: po odstranění téhle podmínky selžou přesně ty dva testy, které ji hlídají.
- **Soubory jdou pryč s databází, ne zvlášť.** `fileId` platí jen v jedné databázi TDLib, takže
  bez ní jsou stažená média neadresovatelné bajty.
- **Ztráta klíče se nevysvětluje vlastní hláškou.** Po zahození nečitelné databáze se otevře nová
  a uživatel skončí na přihlašovací obrazovce — po přenosu na nový telefon je to stejně to, co od
  Telegramu čeká. `AuthStage.Failed` by tu byl slepý konec: obrazovka chyby nemá tlačítko, kterým
  se dá pokračovat.
- **Selhání samotného zašifrování aplikaci nezastaví.** Databáze zůstane nešifrovaná, klíč uložený,
  a příští start to zkusí znovu druhým krokem. Loguje se `e` s výjimkou, takže je to v Crashlytics
  vidět jako problém.

### Prázdný klíč NEZNAMENÁ nešifrovanou databázi

**TDLib má SQLCipher zalinkovaný vždycky a `db.sqlite` je neprůhledná i s prázdným klíčem** —
ověřeno na emulátoru ještě před změnou: soubor nemá hlavičku „SQLite format 3", `strings` v něm
ani v `td.binlog` nenajde jediné jméno, adresu ani český text. Z prázdného klíče se tedy nestává
čitelná databáze, ale databáze šifrovaná **konstantou, kterou zná každý, kdo má TDLib**. To je
zamlžení, ne ochrana; smysl téhle změny je vyměnit ji za klíč, který má jen tohle zařízení.

Plyne z toho i to, **čím se to NEDÁ ověřit**. První verze tohohle odstavce radila porovnat
`strings files/tdlib/db.sqlite` před změnou a po ní — jenže ten test vyjde stejně v obou
případech a „nic nenašel" by se dalo přečíst jako úspěch i u kódu, který klíč vůbec nenastaví.
**Jediný důkaz, že klíč zabral, je, že databáze už PRÁZDNÝM klíčem otevřít nejde.**

### Jak to ověřit

Přesně to říká cesta ven, takže se obojí ověří jedním pokusem. Nová verze se nainstaluje přes
existující data (`adb install -r`, data zůstanou) a v logu musí být:

1. **při prvním spuštění** „Databáze TDLib zašifrovaná." a hned za ní `AuthorizationState -> Ready`
   — migrace proběhla a uživatel zůstal přihlášený,
2. **při druhém spuštění** už ta hláška NENÍ — databáze se otevřela rovnou uloženým klíčem,
   jedním voláním,
3. **po poškození klíče** (`run-as eu.tintera.plume sh -c 'head -c 61 /dev/urandom > files/tdlib-key'`)
   „Klíč k databázi nejde rozšifrovat" → „Databázi TDLib nejde odemknout" s `TdlThrowable: Wrong
   database encryption key` → `AuthorizationState -> WaitPhoneNumber`, bez pádu, a `db.sqlite`
   spadne z megabajtů na jednotky kilobajtů.

**Krok 3 je ten důkaz.** Aby se došlo až k mazání, musel selhat i pokus s PRÁZDNÝM klíčem —
tedy databáze opravdu není ta, kterou TDLib zakládá sám. Ověřeno na emulátoru: 15 MB → 12 kB
a přihlašovací obrazovka.

**Push do zmrazeného procesu je čtvrtý krok** a ověřuje to, co žádný z předchozích: že se klíč
načte bez jakéhokoli UI. Aplikaci nestačí odsunout na plochu (zůstane na adj 700) — musí se
vytlačit do keše spuštěním pár jiných aplikací, pak je asi do dvaceti vteřin `isFrozen=true`
(`dumpsys activity processes eu.tintera.plume`). Zpráva od testovacího bota pak musí skončit
notifikací se správným textem. Ověřeno na emulátoru.

### Release build (R8)

Šifrování klíč nikde nedohledává reflexí, takže nová keep pravidla nepotřebuje — ověřit se to
ale musí na zařízení, protože chybějící keep pravidlo se při buildu nepozná (viz „R8").
Ověřeno na emulátoru: databáze se uloženým klíčem otevřela (`WaitTdlibParameters` → `Ready`,
bez přešifrování), a **po `am kill` z pozadí se aplikace vrátila do otevřené konverzace** —
tedy i polymorfní serializace klíčů navigace drží.

- **Release APK se dá rovnou podepsat debug klíčem**, místo stavět a pak přepodepisovat:
  `-PreleaseStoreFile=$HOME/.android/debug.keystore -PreleaseStorePassword=android
  -PreleaseKeyAlias=androiddebugkey -PreleaseKeyPassword=android`. Minifikace je vlastnost
  buildu, s podpisem nesouvisí, a `adb install -r` přes debug build nechá data i přihlášení.
- **Cesta ven se na release buildu ověřit NEDÁ.** `run-as` na nepodepsatelné aplikaci hlásí
  „package not debuggable" a `adb root` na emulátoru s Play službami neprojde, takže se
  `tdlib-key` nemá jak poškodit. Testuje se tedy na debug buildu; z R8 je v té větvi citlivé
  jen `is TdlThrowable` a řetězcová konstanta, obojí přejmenování přežije.

### Jak to ověřit na iOS

Aplikace na simulátoru **do unified logu nic nepíše** — `log stream --predicate 'process ==
"Plume"'` ukáže jen systémové vrstvy (Security, FileURL, RunningBoard), ne Kermit, a
`simctl launch --console-pty` nevypíše nic. Ověřuje se proto chováním a stavem na disku, ne logem.

- **Keychain simulátoru se dá číst**: `~/Library/Developer/CoreSimulator/Devices/<id>/data/
  Library/Keychains/keychain-2-debug.db`, tabulka `genp`. `svce` i `acct` jsou zašifrované,
  ale **`agrp` a `pdmn` jsou v plaintextu**, takže záznam se pozná podle skupiny
  (`<TEAM>.eu.tintera.plume`) a atribut přístupnosti podle `pdmn`: **`cku` =
  `AfterFirstUnlockThisDeviceOnly`** (`ak` = WhenUnlocked, `aku` = WhenUnlockedThisDeviceOnly,
  `dku` = AlwaysThisDeviceOnly). Soubor je WAL, takže se před dotazem kopíruje i s `-wal`/`-shm`.
- **Že `SecItemCopyMatching` čte**, se pozná podle toho, že se `hex(data)` toho řádku mezi starty
  NEMĚNÍ — kdyby čtení selhalo, kód vyrobí a uloží klíč nový.
- **Vyloučení ze zálohy** je v systémovém logu (`kExcludedFromBackupXattrName set on path: …`)
  a v `xattr -l` na obou adresářích (`com.apple.metadata:com_apple_backup_excludeItem`).
- **Nepřihlášená aplikace se ověřit nedá.** Bez účtu nemá TDLib co uchovávat, `td.binlog` vůbec
  nevznikne a `db.sqlite` dostává při každém startu nový inode — vypadá to jako mazání, a není
  to ono. **Sledovat se musí `td.binlog`**: přežije-li restart se stejným inodem, databáze se
  uloženým klíčem otevřela.
- **Cesta ven se vyvolá `xcrun simctl keychain <id> reset`.** Odebrat jednotlivý záznam simctl
  neumí, takže to smaže keychain celého simulátoru (i cizím aplikacím na něm). Po restartu musí
  `db.sqlite` i `td.binlog` spadnout na jednotky kilobajtů, `td.binlog` dostat nový inode
  a aplikace skončit na přihlašovací obrazovce, bez pádu. Ověřeno: 14 MB → 12 kB.

## Přihlášení QR kódem

- **QR se generuje na platformě** (`encodeQrCode`): Android přes zxing, iOS přes CoreImage
  (`CIQRCodeGenerator`). Kodér v commonMain není. Platforma vrací jen mřížku modulů,
  kreslí se společně na `Canvas` — ostré v každé velikosti a nic se neškáluje z bitmapy.
- **Natvrdo černá na bílé, i v tmavém režimu.** Čtečky počítají s tmavými moduly na světlém
  podkladu; inverzní kód spousta z nich nepřečte. Stejná výjimka z rolí `colorScheme` jako
  bílé ovládání v prohlížeči médií.
- **Modul musí mít celé pixely.** Se zlomkovou velikostí nechá vyhlazování hran mezi sousedními
  moduly vlasové spáry; zbytek šířky se rozdělí do okrajů.
- **Odkaz se obnovuje.** TDLib posílá nový `AuthorizationStateWaitOtherDeviceConfirmation`
  zhruba každou půlminutu; kód se přepočítá přes `remember(content)`.
- **Z QR režimu se přes zadání čísla vrátit NEJDE** — je to smlouva TDLib, ne naše chyba.
  `setAuthenticationPhoneNumber` i `requestQrCodeAuthentication` vyjmenovávají stavy, ve kterých
  se smí volat, a `authorizationStateWaitOtherDeviceConfirmation` v žádném z těch seznamů není;
  berou ho jen `checkAuthenticationPasskey` a `checkAuthenticationWebToken` („for official
  Telegram apps only").
- **Jediné dveře ven jsou `destroy()`** (`TelegramSession.restartLogin`). Zavře klienta,
  `onClientClosed` vyrobí nového a ten dojde k zadání čísla. **Ne `logOut`**: ten chce funkční
  připojení a je to „řádné odhlášení" relace, která ještě neexistuje — a právě spojení nemusí
  mít ten, kdo se z QR vrací. `destroy` smí být volaný před autorizací a funguje offline.
  Bez tlačítka „Přihlásit se číslem" je QR slepá ulička: kdo nemá čím kód naskenovat, musel by
  aplikaci odinstalovat.
- **Zavírání klienta se v téhle situaci NEHLÁSÍ jako odhlašování** (`restartingLogin`,
  `closingStage()`). „Odhlašuji" by lhalo — uživatel přihlášený nikdy nebyl.

## Přihlašovací obrazovka

Jedna obrazovka pro všechny fáze (`AuthScreen`), fáze se střídají v `AnimatedContent`.

- **Uvítací tvar stojí NAD `AnimatedContent`, ne uvnitř.** Uvnitř by se jeho úvodní animace
  pouštěla při každém kroku přihlášení a z uvítání by byl tik. Takhle naběhne jednou a pak jen
  mění ikonu podle fáze.
- **Je to tentýž tvar jako u prázdného stavu** — `IntroShape` v `core/ui`, vytažený z
  `IntroEmptyState`. „Vítej v aplikaci" a „tady zatím nic není" jsou dvě podoby téhož okamžiku;
  dva různé vzhledy by jazyk tvarů rozmělnily (viz `ShapeMorphs`).
- **Fáze přijíždějí zdola a odjíždějí nahoru.** Přihlášení jde dopředu, tak jde i pohyb jedním
  směrem; prolnutí samo by nedalo najevo, že se postoupilo o krok.
- **Tvar se při změně kroku proanimuje znovu** (`replayKey`). Nad měnícím se obsahem by jinak
  stál jako obrázek, a přitom je to jediné, co drží souvislost mezi kroky.
- **Kroky pro tvar jsou hrubší než `AuthStage`** (`AuthPhase`). Startovní `Connecting` patří
  k zadání čísla, protože k němu automat míří — jinak by se tvar hned po náběhu proanimoval
  podruhé a vypadalo by to jako zadrhnutí. Kód a heslo jsou taky jeden krok: mezi nimi se
  nepřechází tam a zpět.

### Zadání kódu

Číselný kód se píše do políček — jedno na číslici, počet podle toho, co hlásí server
(`AuthCode`, `CodeField`). Hotový kód se odešle sám.

- **Kódy nejsou pořád stejně dlouhé.** Z Telegramu chodí pětimístný, SMS a Fragment posílají
  i šestimístné. Hádat se to ale nemusí: `AuthenticationCodeInfo.type` nese `length` u každého
  přihlášení zvlášť, takže se nakreslí přesně tolik políček, kolik jich bude potřeba.
- **A nemusí to být ani číslo.** `SmsWord` (jedno slovo), `SmsPhrase` (fráze) a `FlashCall`
  (kód se čte z čísla volajícího) délku nemají; tam zůstane obyčejné pole s písmenkovou
  klávesnicí. `when` nad `AuthenticationCodeType` je bez `else` — je sealed, takže nový způsob
  doručení v novější TDLib rozbije překlad, místo aby tiše spadl na obyčejné pole.
- **Délka od serveru se bere jen v rozumném rozsahu** (`CODE_DIGITS`). Nula by znamenala nula
  políček, tedy pole, do kterého se nedá psát; mimo rozsah se spadne na obyčejné pole, které
  funguje vždycky.
- **Uvnitř je JEDNO pole, políčka jsou dekorace** (`decorationBox`), ne N samostatných
  `TextField`ů. Řada polí znamená přehazovat fokus po každém úhozu, ručně řešit backspace přes
  hranici políčka a hlavně přijít o vložení ze schránky i o autofill — celý kód by spadl do
  prvního políčka.
- **`innerTextField()` se MUSÍ složit, i když není vidět.** Nese rozvržení textu, bez kterého
  pole nefunguje; je proto průhledné (barva textu i kurzoru) a leží pod políčky. Gesta a fokus
  má na sobě kořen dekorace, ne vnitřní pole, takže ťuknutí kamkoli na řadu políček zabere.
- **Číslice čte čtečka obrazovky z POLE, ne z políček** (`clearAndSetSemantics` na řadě).
  Semantika pole je merging, takže bez toho by se do jeho popisu slily všechny `Text`y.
- **Zaoblení je v procentech kratší strany, ne v dp.** Pevných 16 dp ukrojí z políčka širokého
  45 dp (šest číslic na telefonu širokém 360 dp) skoro celou hranu a zbude ovál — ověřeno na
  emulátoru zmenšeném na 720 × 1500. Z téhož důvodu klesá i výška (`codeCellHeight`, testy
  `CodeEntryTest`): se stálými 64 dp z políček byly vysoké úzké pilulky.
- **Hotový kód se odesílá sám, ale tlačítko zůstává.** Poslední číslice říká „jdeme na to" líp
  než další ťuknutí. Bez tlačítka by ale po chybě, která s kódem nesouvisí (spadlé spojení),
  nešlo poslat TENTÝŽ kód znovu: text se nezměnil, takže se odeslání samo už nespustí.
- **Věta nad polem říká, kudy kód opravdu přišel** (`CodeDelivery`). Jedna věta pro všechno
  („dorazil do jiného klienta nebo SMS") byla odhad — a přitom to TDLib pokaždé řekne.
- **Fokus si pole bere samo.** Zadat kód je jediná věc, kterou na téhle obrazovce jde udělat.
  Výjimka z pravidla „po ťuknutí na Upravit se pole samo nefokusuje": tam je psaní vsuvka,
  tady je to celá obrazovka.
- **Autocorrect je vypnutý u kódu i u hesla.** Slovní kód by klávesnice opravila na jiné slovo
  a uživatel by nepoznal proč.
- **Ověřuje se podstrčeným stavem, ne odhlášením.** Na přihlašovací obrazovku se jinak nedá
  bez ztráty relace; v `AuthViewModel` se dočasně nahradí `stage` za
  `MutableStateFlow(AuthStage.WaitCode(AuthCode(5, CodeDelivery.TelegramApp)))`, postaví se APK
  a zdroják se hned vrátí — stejně jako u karty aktualizace. Odeslání kódu na přihlášené relaci
  skončí chybou od serveru, takže se tou samou cestou ověří i chybový stav.
- **Ověřeno na emulátoru**: pět i šest políček, doplňování zleva, rámeček u aktivního políčka,
  vložení delšího textu se ořízne, plný kód se odešle sám a chyba obarví políčka i větu pod nimi;
  slovní kód dostane obyčejné pole s písmenkovou klávesnicí a neodesílá se sám. **Autofill kódu
  z SMS ověřený NENÍ** — je to jen deklarace `ContentType.SmsOtpCode`, doručit skutečnou SMS na
  emulátor nešlo.

### Heslo dvoufázového ověření

Tónovaný blok jako číslo, s okem na odkrytí (`PasswordField`).

- **Odkrytí okem je to hlavní.** Cloud heslo se píše jednou za dlouho a naslepo; překlep se
  pozná až z hlášky „Heslo nesedí" a uživatel ani potom neví, co vlastně napsal. Ikona
  pojmenovává AKCI, ne stav: se skrytým heslem oko („zobrazit"), s odkrytým přeškrtnuté.
- **Fokus a chybu nese rámeček kolem celého bloku, ne podtržení `TextField`u.** Indikátor je
  široký jen jako pole, takže vedle oka končil v půlce karty a vypadal jako chyba vykreslení.
  Barvu dává `authOutlineColor` — jedno místo pro políčka kódu i pro heslo, dvě kopie téhož
  pravidla by se rozešly.
- **Plovoucí label je pryč, zůstala nápověda v poli.** „Heslo" nad polem jen opakovalo nadpis
  kroku — tatáž úvaha jako u dvojího slova „Země" v bloku s číslem.
- **`ContentType.Password`**, protože cloud heslo bývá ve správci hesel; z celého přihlášení
  se autofill hodí právě tady nejvíc.
- **Autocorrect vypnutý**, jinak klávesnice udělá z hesla jiné slovo.
- **Ověřeno na emulátoru** podstrčeným `AuthStage.WaitPassword` (na obrazovku se jinak nedá bez
  odhlášení): skryté i odkryté heslo, přepnutí ikony, rámeček při fokusu, červený rámeček i věta
  při chybě. Sonda se NECOMMITUJE. **Autofill ze správce hesel ověřený není** — je to jen
  deklarace.
- **Jeden snímek není důkaz.** Po sjednocení bloku s číslem vyšel na jednom snímku horní řádek
  karty tmavší než spodní (změřeno #DFDFE7 proti #F3F3FA) a vypadalo to jako šev, který přidal
  rámeček. Tři opakovaná čerstvá spuštění ukázala kartu jednolitou — byl to přechodný stav fokusu
  na klikacím řádku země, ne následek změny. **Ověřovací pokus, který má vyloučit regresi, se
  musí zopakovat**, jinak se z jednoho vzorku vyrobí neexistující chyba (a stejně snadno se jí
  dá přehlédnout skutečná).

### Obnova hesla a cesta zpátky k číslu

„Zapomenuté heslo?" pod heslem pošle kód na obnovovací e-mail, kód se ověří a pak se nastaví
nové heslo s nepovinnou nápovědou (`PasswordStep` v `AuthViewModel`, testy `AuthViewModelTest`).
Z kódu i hesla vede „Jiné číslo" a systémové zpět zpátky k číslu.

- **Kroky obnovy drží ViewModel, ne automat.** TDLib zná jediný stav `WaitPassword`; kód
  z e-mailu (`checkAuthenticationPasswordRecoveryCode`) i nové heslo
  (`recoverAuthenticationPassword`) se volají nad ním. Při odchodu z `WaitPassword` se
  rozdělaná obnova zahazuje.
- **Maska e-mailu přijde NOVÝM STAVEM.** `recoveryEmailAddressPattern` je prázdný, dokud kód
  neodešel; po `requestAuthenticationPasswordRecovery` pošle TDLib `WaitPassword` znovu, už s ní.
  Obsah proto animuje podle klíče (`AuthView.key`: u hesla jen krok), jinak by přijel podruhé
  ve chvíli, kdy ho uživatel začíná číst.
- **Kód se ověřuje zvlášť, dřív než nové heslo.** Překlep v kódu by se jinak ukázal až po
  vymyšlení hesla a uživatel by ho psal znovu. Zpět z nového hesla vrací kód vyplněný.
- **Bez obnovovacího e-mailu se nic neposílá**, jen dialog řekne, co zbývá: vzpomenout si,
  nebo účet resetovat v oficiální aplikaci. Server by na žádost odpověděl `PASSWORD_RECOVERY_NA`
  a z obecné chyby by uživatel nepoznal, co dál. Tlačítko je u každého účtu — schované by
  nechalo hledat cestu, která není.
- **Reset účtu (`deleteAccount` s prázdným heslem) tu NENÍ.** Maže účet u všech kontaktů,
  provede se po týdnu a na testovacím účtu se ověřit nedá. Viz `docs/roadmap.md`.
- **Heslo se neořezává, kód ano.** Dřív se `trim()` dělal u všech kroků, takže heslo s mezerou
  na konci nešlo zadat — a nové heslo nastavené odsud by rozbilo přihlášení jinde.
- **Nápověda shodná s heslem se odmítne hned** (server by to udělal taky, ale obecnou chybou).
- **Zpět k číslu je `restartLogin` (`destroy`), ne `setAuthenticationPhoneNumber`.** To TDLib
  v kódu i heslu pustí, jenže automat by dál hlásil kód a obrazovka čísla by existovala jen
  u nás. Dokud heslo nesedí, server přihlášení nedokončil (`SESSION_PASSWORD_NEEDED`), takže
  po `destroy` nezůstane viset relace.
- **Zpět je `NavigationBackHandler`**, jen v kódu, hesle a QR. Na čísle zpět aplikaci zavře jako
  dřív — před číslem nic není. Uvnitř obnovy jde zpět o krok obnovy.
- **„Další" v poli nového hesla fokus přesouvá RUČNĚ** (`onNext` → `FocusRequester` nápovědy).
  Výchozí přesun skočil na tlačítko s okem a nápověda se psala naprázdno — ověřeno na emulátoru.
  Totéž čeká každé pole s ikonou, za kterým je další pole.
- **Kód z e-mailu je obyčejné pole, ne políčka** — délku TDLib neříká. `contentType` nemá:
  `SmsOtpCode` by autofill nabídl kód z SMS.
- **Ověřeno na emulátoru podstrčenou session** (testovací účet dvoufázové ověření nemá):
  delegující `TelegramSession` s `WaitPassword` a odpověďmi pro kód, obnovu a restart, zapojená
  jedním řádkem v `AuthViewModel`. **Nikdy ne přes skutečný `restartLogin`** — ten by přihlášený
  účet na emulátoru zavřel. Sonda se NECOMMITUJE; odstraňuje se tím jedním řádkem, ne
  `git checkout` souboru (ten vrátí i rozdělanou práci). **Skutečný kód z e-mailu a nastavení
  nového hesla ověřené NEJSOU** — chce to účet s heslem a obnovovacím e-mailem.
- **`adb input text` do pole nad `StateFlow` na pomalém emulátoru přeháže znaky** („abc" →
  „bca"); po znaku s pauzou vyjde správně. Pole přihlášení jedou přes `StateFlow` odjakživa
  (proti tomu varuje „Úprava profilu"); na zařízení se to zatím neprojevilo, ale při rychlém
  psaní na slabém telefonu by mohlo.

### Výběr země a formát čísla

**Seznam zemí ani formáty čísel se nedrží u nás** — dává je TDLib, a všechno „can be called
before authorization":

- `getCountries()` → kód, název, **emoji vlajky**, `isHidden` a předvolby. Vlajka je emoji ze
  systémového písma, takže se pro dvě stě zemí nedrží žádná sada ikon.
- `getCountryCode()` → země podle IP. **Použije se, jen dokud si uživatel nevybral sám** —
  dorazí po síti a přepsat jím ruční volbu by vypadalo jako chyba.
- `getPhoneNumberInfoSync(languageCode, prefix)` → z rozepsaného čísla pozná zemi a vrátí masku,
  ve které **`-` je očekávaná číslice**. Je to lokální dotaz, takže se smí volat při každém úhozu.

- **Fokus a chybu nese rámeček kolem celého bloku** (`authOutlineColor`, sdílený s políčky kódu
  a s heslem). Dřív to bylo podtržení `TextField`u: čára široká jen jako pole a přilepená přesně
  na spodní hranu karty, takže vypadala jako lem, který tam něco zapomnělo — a přihlašovací
  obrazovka měla pro tutéž věc tři různé projevy.
- **Číslo má nápovědu, ne plovoucí label.** Label po vyplnění odskočí nahoru a text s ním
  klesne — číslo pak nelícovalo s předvolbou vedle, a ta label nemá a mít nemůže (je to volba,
  ne psané pole). Platí obecně: **pole, které stojí v řádku vedle textu bez labelu, label mít
  nesmí.**
- **Než dorazí odhad země, stojí v řádku výzva „Vyber zemi", ne podruhé slovo „Země".** Popisek
  a hodnota se stejným textem nad sebou vypadaly jako chyba vykreslení.
- **Řadí se abecedně, ne jak to pošle TDLib.** Ten řadí podle KÓDU země (AD, AE, AF…), což pro
  čtenáře není pořadí. Vědomá výjimka z pravidla „pořadí necháváme od TDLib" — u chatů je to
  pořadí, na které je uživatel zvyklý z jiných klientů, tady je to náhodná řada.
- **Maska se ukazuje jen po poslední zadanou číslici** (`formatNationalNumber`). „777 --- ---"
  vypadá jako rozbité pole.
- **Hodnotou pole jsou syrové číslice, oddělovače dodává `VisualTransformation`.** S naformátovanou
  hodnotou by kurzor skákal pokaždé, když formátování vloží mezeru. Mapování pozic je pokryté
  testy (`PhoneEntryTest`) — chyba v něm se na zařízení pozná až skákajícím kurzorem, což se
  při klikání přehlédne.
- **Nesedící maska transformaci vypne.** Než po změně země dorazí přeformátování, je na jeden
  snímek maska od předchozí země; pole, které ukazuje jiné číslo, než v něm je, by bylo horší
  než pole bez mezer.
- **Ne každá země formát má.** Česko ano („777 123 456"), Německo ne — číslo pak zůstane
  bez mezer a je to v pořádku.
- **Názvy zemí překládá OPERAČNÍ SYSTÉM, ne TDLib** (`localizedCountryName`). `CountryInfo.name`
  má být podle dokumentace „native name", jenže TDLib před autorizací vrací anglické názvy
  (ověřeno na emulátoru se systémovou i aplikační češtinou). Kód země je ISO 3166-1 alpha-2
  a k němu mají obě platformy data z CLDR: Android `Locale.getDisplayCountry()`, iOS
  `NSLocale.displayNameForKey(NSLocaleCountryCode)`. Nestahuje se nic a nedrží se vlastní
  tabulka dvou set zemí, která by stárla.
- **Neznámý region se pozná podle toho, že se vrátí sám kód.** `getDisplayCountry` u regionu,
  který nezná, vrátí vstup — to je „nevím", ne překlad, a spadne se na název od TDLib.
- **Na Androidu stačí `Locale.getDefault()`**, ten sleduje i jazyk nastavený jen pro aplikaci
  (per-app locale), takže název země odpovídá tomu, v čem je zbytek obrazovky.
- **Na iOS se `NSLocale.currentLocale` použít NESMÍ.** Jde za regionálním nastavením systému,
  ne za jazykem aplikace: česká aplikace na anglickém simulátoru měla „Země: Czechia" k česky
  psanému zbytku obrazovky. Locale se proto staví z [platformLanguageCode]
  (`NSLocale(localeIdentifier = …)`) — tedy z téhož zdroje jazyka, jaký dostává i TDLib.
  Ověřeno na simulátoru: `simctl launch … -AppleLanguages "(cs)"` ukáže rozdíl bez přepínání
  celého systému.
- **Hledá se i podle anglického názvu** (`Country.englishName` od TDLib, nekreslí se). Kdo zná
  zemi jen anglicky, by ji v českém seznamu jinak nenašel — „Germany" vs. „Německo".

## Odhlášení

- **Po odhlášení je TDLib klient MRTVÝ.** `logOut` projde `LoggingOut` → `Closing` → `Closed`
  a wrapper pak ukončí všechny update flows klienta. Nový účet se na něm přihlásit nedá —
  musí vzniknout nová instance (`TelegramClientHolder.recreate()`), která začne od
  `WaitTdlibParameters` a sama dojde k zadání čísla. Dokud to tak nebylo, aplikace po odhlášení
  zůstala natrvalo stát na nápisu „Odhlášeno".
- **Klient se proto nikde nesmí zachytit do `val`.** Požadavky jdou přes `holder.client`
  (getter), odběry přes `holder.updates { … }`, které se na novou instanci přepnou samy
  (`flatMapLatest`). `client.fooUpdates.collect` na zachyceném klientovi po odhlášení tiše skončí.
- **Nový klient se zakládá, až když skončí odběr stavů, ne podle `AuthorizationStateClosed`.**
  Wrapper ukončuje update flows jedním příznakem `stopped` SPOLEČNÝM pro všechny odběry
  klienta (`TdlRepository.checkStopped`): kdo `Closed` zpracuje první, příznak nastaví a ostatním
  odběrům flow skončí DŘÍV, než k nim `Closed` dojde. Odběrů jsou desítky, takže session o `Closed`
  přicházela podle toho, kdo byl zrovna rychlejší — a po ukončení relace z jiného zařízení
  („Zařízení" → odhlásit) zůstala aplikace natrvalo na „Odhlašuji", dokud se nekillnula. Flow
  jinak než zavřením klienta neskončí, takže konec `collect` je spolehlivý signál
  (`onClientClosed`). **Obecně: na poslední update flow, který klient pošle, se nedá spolehnout —
  vidí ho jen nejrychlejší odběratel.**
- **Session se k novému klientovi přihlašuje `CoroutineStart.UNDISPATCHED`.** Odběr musí stát
  dřív než `getAuthorizationState` a nová instance nic nevydá, dokud nedostane první request —
  takže ztracený první stav by automat zastavil.
- **„Odhlášeno" není stav, jen přechod** (`AuthStage.LoggingOut` s indikátorem). Odhlášený
  uživatel je prostě na začátku přihlášení.
- **`fileId` platí jen v jedné databázi TDLib.** Nový účet čísluje soubory znovu, takže keše
  podle `fileId` (`TdlFileRepository`, `knownPaths` v `RemoteImage`) se po výměně klienta musí
  zahodit — jinak by se pod novými id ukázaly obrázky předchozího účtu. Totéž keš odesílatelů.
- **Úklid mimo aplikaci**: notifikace i zkratky konverzací (`NotificationUpdate.Cleared`). Zkratky
  jsou dlouhověké, takže `removeAllDynamicShortcuts` nestačí — je potřeba
  `removeLongLivedShortcuts`, jinak zůstanou chaty odhlášeného účtu v nabídce ikony aplikace.

## Stav spojení a obnova po pozadí

Spojení s Telegramem je **něco jiného než přihlášení**. `AuthStage` říká, jestli máme účet;
`ConnectionState` (`ConnectionRepository`, doslova TDLib `UpdateConnectionState`), jestli má kudy
chodit. Dokud se nesledovalo, vypadala nepřipojená aplikace úplně normálně — jen nic nechodilo.

- **Aplikace se po delší době v pozadí nepřipojila, dokud se nekillnula.** Systém v dozu (a při
  přepnutí sítě) zabije sokety, jenže TDLib je zevnitř dál považuje za živé; mrtvolu pozná až na
  vlastním odpočtu, který v dozu neběžel. Killnutí pomáhalo proto, že nový klient otevírá čerstvé
  sokety.
- **Lék je `setNetworkType`.** Dokumentace: *„Calling this method forces all network connections to
  reopen … so it must be called whenever the network is changed, even if the network type remains
  the same."* Volá se tedy **při každé změně sítě i při každém návratu do popředí** — právě to
  druhé je ten případ z dozu, kdy se síť nezměnila a spojení přesto nestojí.
- **Hlásí to `ConnectionController`, startovaný ze `startKoin`**, ne z UI: síť se mění i tehdy,
  když se žádná obrazovka neskládá.
- **Šum ze sítě se musí odfiltrovat, skutečná výměna ne.** Android posílá `onCapabilitiesChanged`
  i několikrát za minutu (kvalita signálu, ověření připojení) a vynucovat kvůli tomu znovuotevření
  všech spojení je horší než nedělat nic. Naopak přelezení mezi dvěma Wi-Fi je změna, kterou
  porovnání samotného typu nezachytí — proto `NetworkStatus` nese kromě typu i pořadové číslo,
  které se zvedá jen v `onAvailable` / `onLost`.
- **Bez sítě při startu to musí monitor říct sám.** `registerDefaultNetworkCallback` se bez
  sítě neozve vůbec, takže TDLib dostal výchozí `Other` z `ConnectionController` a aplikace
  spuštěná v letovém režimu hlásila napořád „Připojování…". Monitor se proto před registrací
  zeptá na `activeNetwork` a bez ní ohlásí `None`. iOS to nepotřebuje: `NWPathMonitor` se ozve
  hned po startu i s nesplněnou cestou. Ověřuje se STUDENÝM startem v letovém režimu — zapnutí
  letového režimu za běhu chodí přes `onLost` a chybu neukáže.
- **Otočení displeje NENÍ odchod do pozadí.** `onStop` staré instance a `onStart` nové by jinak
  vypadaly jako návrat z pozadí a zbytečně shodily všechna spojení; pozná se to podle
  `isChangingConfigurations`.
- **Popředí se nebere z kompozice.** `LocalLifecycleOwner` by fungoval, ale žil by jen po dobu,
  kdy se skládá UI — stejný důvod, proč se ze `startKoin` startuje i `NotificationPresenter`.
  Android to čte z `registerActivityLifecycleCallbacks`, ne z `ProcessLifecycleOwner`: ten je
  v androidx větvi lifecycle, zatímco projekt jede na multiplatformní
  `org.jetbrains.androidx.lifecycle`, a míchat dvě verze téže knihovny kvůli jednomu příznaku
  nestojí za to. iOS bere oznámení `UIApplication`; `applicationState` se čte přes
  `dispatch_async` na hlavní frontě, protože odběr běží na `Dispatchers.Default`.
- **Volba `online` se musí nastavit, jinak nás server nikdy nevidí online.** Není to jen zelená
  tečka pro ostatní — dokud je aplikace na obrazovce, chodí s ní updaty rychleji.
- **`ConnectionState` se smí použít jen jako popisek pro člověka**, říká to dokumentace u něj.
  Nic se z něj proto neodvozuje, jen se vypisuje.
- **Stav je v hlavičce, ne ve vlastním pruhu, a všude stejným znakem**: avatar se zvlní, naroste
  a točí (`ConnectionIndicator` v `core/ui`). V seznamu chatů je to fotka účtu a text je
  v nápovědě vyhledávací pilulky (viz „Hlavička: vyhledávací pilulka"); v konverzaci avatar
  chatu v hlavičce, text jen pro čtečku (`stateDescription` řádku hlavičky). Kdo se dívá na
  prázdný seznam, hledá vysvětlení nahoře.
- **`Ready` se nehlásí.** V tom stavu aplikace stráví skoro celou dobu; „připojeno" je šum, zatímco
  „nepřipojeno" je jediná informace, která uživateli vysvětlí, proč mu nic nechodí. Proto
  `ConnectionState.label()` vrací u `Ready` `null`.
- **Hlavička konverzace: spojení nese avatar, „online" tečka, podtitul jen „naposledy…".** Dřív
  byl stav spojení textem místo podtitulu; dvě podoby téhož stavu (text tady, zvlněná fotka
  v seznamu) byly jen nekonzistence. Tečka „online" je tatáž jako v seznamu chatů (`OnlineDot`),
  a proto **podtitul „online" nepíše** — tečka a slovo hned vedle sebe říkaly totéž dvakrát.
  Podtitul zbyl na „naposledy viděn"; čtečce obrazovky „online" řekne popis stavu hlavičky.
  **Bez spojení se dostupnost schová** (podtitul i tečka): dokud spojení nestojí, je to jen
  stará hodnota, kterou nemá co obnovit — a tečka by přeškrtla prstenec. „Píše…" v hlavičce
  není, viz níž.
  - **Avatar skupiny a kanálu se zvlní ze SVÉHO tvaru** (`ChatKind.avatarPolygon`, týž zdroj
    jako `avatarShape`), ne z kruhu — skokem do kruhu by přeskočil. Při výpadku dostane `Avatar`
    obdélníkový tvar (`isShaping`): ořezává se i vlastním tvarem, a vlnitý ořez by jinak zůstal
    uvnitř sušenky. Úhel otáčení roste s průběhem morfu, jinak by se čtverec kanálu při zvlnění
    skokem přetočil.
  - **Řádek hlavičky ořezává** (tvar vlnky stisku), takže má kolem sebe rezervu
    (`HEADER_RING_ROOM`) — bez ní byl zvětšený avatar vlevo useknutý. Avatar se tím posunul
    o ~4 dp doprava.
- **Ověřuje se to bez dozu** — chování je stejné: odejít z aplikace a vrátit se (v logu musí být
  „Návrat do popředí, obnovuji spojení"), zapnout a vypnout letový režim (`adb shell cmd
  connectivity airplane-mode enable`), otočit displej (v logu nesmí být NIC).

## Proxy

Nastavení → Obecné → Připojení → Proxy (`ProxySettingsScreen`, `features/proxy`) a odkazy
`t.me/proxy?…` / `t.me/socks?…` (`InternalLink.Proxy`, dialog `ProxyLinkDialog` přes
`ProxyLinkFlow`). Hranice je `ProxyRepository` (`TdlProxyRepository`), pravidla `problem()`
a `shareLink()` v `core/telegram/Proxy.kt` (testy `ProxyTest`).

- **Proxy si drží TDLib** (`addProxy`, `enableProxy`, `disableProxy`), ne nastavení aplikace.
  Přežije restart, platí i před přihlášením a zapnutá smí být jen jedna — přepnutí řeší TDLib.
- **O změně seznamu TDLib update nepošle**, takže se seznam po každé akci ČTE ZNOVU. Odezva
  (`pingProxy`) se měří souběžně při otevření a tlačítkem v liště; zapnutí ani vypnutí ji
  neměří znovu. Nedostupná proxy odpoví až po vypršení (asi 10 s), mezitím „Měřím…".
- **Odkaz nic nezapíná sám**: dialog ukáže server, port, druh a odezvu a teprve „Připojit"
  proxy přidá a zapne. Odkaz může poslat kdokoli a provozovatel proxy vidí, kdy a odkud se
  aplikace připojuje. Dialog je třída nad `CoroutineScope` jako `ReportFlow` — odkaz může přijít
  z kterékoli obrazovky s textem a druhý ViewModel kvůli jednomu dialogu nestojí za to.
- **`InternalLinkTypeProxy.proxy` smí být `null`** (druh, který TDLib nezná) — vlastní hláška
  (`UnsupportedProxyException`), do prohlížeče by to nevedlo nikam.
- **TDLib tajný klíč MTProto PŘEVEDE**: z odkazu s hex klíčem (`ee…`) vrací `getProxies` týž klíč
  v base64url. Editor i odkaz z „Kopírovat odkaz" ukazují tuhle podobu; Telegram ji bere taky.
- **„Zapnutá", ne „připojeno přes ni"**: zapnutá proxy nemusí fungovat a připojení visí na
  „Připojování…" (avatar ve vyhledávací pilulce se zvlní). Proto je odezva u každé proxy — kdo
  si zapne mrtvou, vrátí se ťuknutím na „Přímé připojení".
- **Nově přidaná proxy se rovnou zapne**, upravená si stav nechá. HTTP proxy odkaz Telegramu
  nemá (`shareLink() == null`), „Kopírovat odkaz" se u ní nenabízí.
- **Ověřeno na emulátoru** neexistující proxy (`203.0.113.7`, dokumentační rozsah, a
  `127.0.0.1:1080`): odkaz od testovacího bota, dialog s „Nedostupná", připojení (spojení spadlo
  na „Připojování…"), přepnutí na přímé, úprava, kopírování odkazu, ruční SOCKS5 a smazání.
  **Fungující proxy ověřená NENÍ** — žádná po ruce nebyla. iOS jen přeložené.
- **Řádek Proxy v Obecných říká stav** („Zapnutá · server:port" / „Vypnutá · přímé
  připojení"; `ProxyStatusViewModel`, `proxyStatus`, testy `ProxyStatusTest`). Zapnutá mrtvá
  proxy je nejčastější důvod věčného „Připojování…" a bez stavu v řádku se na ni přišlo až na
  obrazovce proxy. Stav se čte znovu při každém návratu (`LifecycleResumeEffect`) — TDLib změnu
  proxy nehlásí. **Odezvu řádek neměří**: `pingProxy` jde přes každou uloženou proxy a patří na
  obrazovku proxy, ne ke každému otevření Nastavení. Dokud stav nedorazí, podtitul není žádný.
  Ověřeno na emulátoru: vypnutá → přidaná proxy → návrat ukáže zapnutou → smazání → vypnutá.
- **Proxy jde nastavit i na přihlašovací obrazovce** („Připojit přes proxy" pod krokem,
  `AuthStage.offersProxy`, testy `AuthViewModelTest`). Kde je Telegram blokovaný, nedorazí kód
  — a Nastavení je až za přihlášením. Je to TÁŽ `ProxySettingsScreen` přes celou plochu
  přihlášení, ne routa: přihlášení stojí mimo `NavDisplay`. Nabízí se jen v krocích, kde se čeká
  na uživatele; start klienta, odhlašování a chybu proxy nespraví.
  - **Zpět mají DVĚ obsluhy a ta přihlašovací se s otevřenou proxy vypíná**
    (`isBackEnabled = … && !showingProxy`). V kódu, hesle a QR zpět volá `restartLogin`
    (`destroy`) — bez toho by gesto zpět nad proxy zahodilo rozdělané přihlášení.
  - **Ověřeno na emulátoru podstrčeným stavem** (`WaitPhoneNumber`, pak `WaitCode`; necommituje
    se): přidání, zapnutí a smazání proxy, šipka i gesto zpět vrací na přihlášení. **V kroku
    kódu sonda nahradila `back()` logem** — skutečný `restartLogin` by účet na emulátoru zavřel;
    první zpět zavřelo jen proxy, `back()` přišlo až druhým. Nepřihlášená aplikace ověřená
    není (proxy pak platí pro první spojení), iOS jen přeložené.

### Sponzorovaný chat (proxy a oznámení)

Cizí chat, který do hlavního seznamu dal někdo jiný (TDLib `ChatPosition.source` →
`ChatSummary.sponsor`, `ChatSponsor`): kanál od provozovatele MTProto proxy, nebo veřejné
oznámení od Telegramu. Stojí nahoře ve vlastním bloku (`chatGroups`, test
`sponzorovanyChatJeNahoreZvlastNadPripnutymi`), místo času má štítek „Sponzor proxy" /
„Oznámení" a dlouhý stisk otevře vlastní nabídku s vysvětlením (`SponsoredChatSheet`).

- **Zdroj patří POZICI, ne chatu** (`FolderPosition.sponsor`), a TDLib ho posílá jen v hlavním
  seznamu. Kdo by ho hledal na `Chat`, nenajde nic.
- **Od proxy smazat ani opustit nejde** — TDLib `delete_dialog_history` u sponzorovaného chatu
  odpoví „Can't delete the chat" (přečteno ve zdroji `MessagesManager.cpp`). Jediná cesta ven je
  proxy vypnout, a proto nabídka vede do Nastavení → Obecné → Proxy
  (`AppNavigation.openProxySettings`, zpět po krocích).
- **Oznámení jde skrýt** `deleteChatHistory(removeFromChatList = true)` — tak to TDLib u
  `chatSourcePublicServiceAnnouncement` chce (jinak „Can't delete chat history without removing
  the chat"); `deleteOrLeave` z kanálu neodchází, uživatel v něm není. Text oznámení posílá
  server a stojí v nabídce; prázdný nahradí obecná věta.
- **Připnout, archivovat (ani tahem), přidat do složky, zablokovat a smazat se nenabízí** — chat
  není uživatelův. Zbývá ztlumit (navždy) a označit jako (ne)přečtené.
- **Ověřeno na emulátoru jen sondou** (Notifikátor v `publish` dočasně vydávaný za chat od proxy,
  pak za oznámení; necommituje se): blok nahoře, štítky, obě nabídky, „Nastavení proxy" a zpět.
  **Skrytí oznámení se nezkoušelo** — se sondou by smazalo historii chatu s botem. Skutečný chat
  od proxy chce fungující proxy se sponzorovaným kanálem a oznámení server posílá jen některým
  účtům.

## Nastavení aplikace

`AppSettings` (`core/settings`) nad **DataStore Preferences** — multiplatformní, soubor
`app_settings.preferences_pb` v `filesDir` (Android) / Application Support (iOS). Jen to, co si
aplikace pamatuje o uživateli; data z Telegramu drží TDLib.

- **Hodnoty jsou `StateFlow` načtené eagerně při startu** (`createdAtStart` + `SharingStarted.Eagerly`
  v `appScope`). Obrazovka je má v prvním snímku — ikona ve vstupním poli jinak problikla
  z výchozí hodnoty na uloženou.
- **DataStore nad jedním souborem smí existovat jen jednou za proces** — proto `single` v Koinu.
  Přípona `.preferences_pb` je povinná.
- **Výčty se ukládají názvem, ne pořadím.** Nová hodnota uprostřed by jinak tiše změnila význam
  uložených dat; neznámý název (z novější verze aplikace) spadne na výchozí.
- **Zápis z obrazovky jde přes `appScope`**, ne `viewModelScope` — přepnutí těsně před odchodem
  z chatu se musí uložit.
- **Volby uživatele se od paměti aplikace neliší technikou, jen významem.** `ExpressionMode`,
  nedávná emoji a odstíny pleti si aplikace pamatuje sama; motiv a vyčlenění nepřečtených
  uživatel vědomě nastavuje. Obojí leží ve stejném DataStore — **Proto DataStore by tu byl
  krok zpátky**: znamenal by generování schématu (v KMP vlastní serializer nad kotlinx) kvůli
  hrstce skalárů, migraci při každém přidání pole a konec toho, že se neznámý klíč prostě
  ignoruje. Typovou bezpečnost nese rozhraní `AppSettings`, ne úložiště pod ním.
- **Motiv (`ThemeMode?`: podle systému / světlý / tmavý) a barvy z tapety (`dynamicColor:
  Boolean?`) jsou DVĚ volby, ne jeden výčet.** Material You byl dřív čtvrtou hodnotou
  `ThemeMode.DYNAMIC`, takže s ním nešel vynutit světlý ani tmavý režim. Teď je to přepínač pod
  koly a kombinuje se s kterýmkoli motivem; nabízí se jen tam, kde je (`supportsDynamicColor`).
  Uložené `DYNAMIC` ze starší verze se čte jako neznámá hodnota, tedy nevybraný motiv (podle
  systému) — a barvy z tapety zůstanou, protože nevybrané znamená výchozí.
- **`null` u obou znamená „uživatel si nevybral".** Výchozí barvy z tapety se liší podle
  platformy (Android 12+ zapnuto, jinde nejsou), takže zapsaná konkrétní hodnota by ten rozdíl
  zakonzervovala. Rozhoduje se až při kreslení a v Nastavení se jako vybraná ukáže ta možnost,
  podle které se aplikace opravdu kreslí — prázdný výběr by vypadal jako chyba.
- **`ThemeMode` bydlí v `core/settings`, ne u tématu.** Je to uložená hodnota a `core` nesmí
  importovat `app`; co z něj plyne pro vykreslení (`resolveIsDark`, `supportsDynamicColor`), zůstalo
  v `app/root/presentation` — závisí to na platformě a na systémovém tmavém režimu.
- **`ExpressionMode`** (Emoji / Samolepky): poslední otevřená část výběru. Podle ní se výběr
  otevře a ikona tlačítka ve vstupním poli ukazuje, co se otevře (smajlík / samolepka).
- **Nedávná emoji** (`recentEmojis`): nejnovější první, bez duplicit, nejvýš 40. Uložená jako
  jeden řetězec oddělený mezerou — `stringSetPreferencesKey` by ztratil pořadí; mezera v žádném
  emoji není. Přidání čte i zapisuje uvnitř jednoho `edit`, takže se dvě rychlá ťuknutí
  nepřepíšou, a běží v rozsahu nastavení (`recordRecentEmoji` neblokuje).
- **Odstíny pleti** (`emojiSkinTones`): základ → zvolená varianta, `základ:varianta` oddělené
  středníkem. Výchozí podoba se neukládá (záznam se smaže).
- Testy nad skutečným souborem v dočasném adresáři (`DataStoreAppSettingsTest`), každý test
  vlastní soubor. **Na skutečném čase (`runBlocking` + `Dispatchers.Default`), ne `runTest`**:
  DataStore pracuje na vlastních vláknech a s virtuálním plánovačem se to občas minulo — test
  visel do minutového limitu a selhával pokaždé jiný. Každé čekání má proto vlastní limit.
- **`first()` nad `StateFlow` NEČEKÁ na načtení — vrátí počáteční hodnotu.** Test „neznámá
  hodnota spadne na výchozí" tím neověřoval vůbec nic: výsledek vyšel správně i tehdy, když
  v úložišti ležela platná jiná hodnota (ověřeno pokusem, ne úvahou — test s „Stickers" v souboru
  dál tvrdil „Emoji" a prošel). Uložená hodnota se dá testovat jen jako **přechod z výchozí na
  jinou**: napřed platná hodnota a počkat na ni, teprve pak ta, která se má přeložit.
- **Limit čekání není měřítko rychlosti, ale pojistka proti zaseknutí** — patří mu tedy velkorysá
  hodnota (15 s). Přiškrcený limit z testu dělá generátor falešných poplachů.
- **Občasné selhání pod zátěží NEBYLA pomalost, ale ztracený zápis — chyba DataStore 1.2.x.**
  Nový odběr `data` čte úvodní stav bez zámku; když zrovna běží zápis, dostane STARÁ data
  s už zvýšenou verzí (zápis zvedá verzi PŘED zápisem souboru, aby pád mezi nimi neskryl změnu).
  Nová data pak odběr zahodí jako „už viděná" (`dropWhile { version <= startState.version }`)
  a hodnota zůstane stará až do dalšího zápisu. Selhávalo to jen pod zátěží (dva emulátory,
  link Kotlin/Native) a u testů, které zapisují „odpal a zapomeň" hned po vzniku nastavení —
  ne náhodně, ale pokaždé u toho, jehož zápis se trefil do okna. Opraveno v DataStore 1.3.0
  (čtení bez zámku bere verzi z keše), stabilní zatím není.
- **Proto `DataStoreAppSettings` odebírá `data` JEDNOU a každý zápis čeká na jeho načtení**
  (`preferences`, `edit`). Po prvním načtení odběr sleduje keš DataStore, kde souboj není; hodnoty
  se z jednoho odběru odvozují v paměti. Až bude DataStore 1.3 stabilní, obchvat jde pryč —
  ale jeden odběr místo pěti má smysl i tak.
- **Ověřilo se to měřením, ne úvahou.** Zápis doběhl za desítky milisekund a `StateFlow` ho
  neviděl ani za 20 s; teprve DALŠÍ zápis starou hodnotu přepsal. Reprodukce: všechna jádra
  vytížit (`yes > /dev/null &` dvakrát na jádro) a pustit scénář stokrát v jednom běhu — původní
  kód se zasekl v 25 kolech z 2 000, obchvat v 0 z 6 000. Hlídá to `zapisHnedPoVznikuSeProjevi`
  (300 kol, 0,2 s); bez zátěže chybu nepozná, pod ní s původním kódem selhal třikrát ze tří.
- **Čekání musí při vypršení říct, NA CO se čekalo a co bylo naposledy vidět.** Holé
  „Timed out waiting for 5000 ms" neumožnilo selhání ani dodatečně vysvětlit. Ze stejného důvodu
  se čeká na CELÝ očekávaný seznam nedávných emoji, ne jen na jeho první položku: jinak se
  neprojevený zápis pozná až o krok dál závěrečným `assertEquals`, kde už není poznat, který.

### Obrazovka nastavení

Nastavení je **list-detail jako chaty**: vlevo sekce (`SettingsScreen`), vpravo obsah té vybrané
(`SettingsSectionScreen`). Sekce jsou routy (`SettingsSectionRoute`), ne stav obrazovky — jinak by
se z telefonu nedalo chodit zpět bez `BackHandler`, který v téhle aplikaci zabíjí predictive back.

- **Vlastní `sceneKey` je povinný.** `ListDetailSceneStrategy` staví scénu s klíčem podle metadat
  a výchozí je `Unit` pro všechny. Chaty i nastavení jsou dvě list-detail dvojice v jednom
  `NavDisplay`, takže bez `SETTINGS_SCENE` by pro NavDisplay měly tutéž identitu.
- **V dvoupanelu je vždycky otevřená nějaká sekce.** Placeholder („vyber sekci") by vedle dvou
  řádků vypadal jako nedodělek, a bez otevřené sekce by directive dvoupanel nezapnul a seznam
  sekcí by se roztáhl přes celou šířku.
- **Výchozí sekce musí být V ZÁSOBNÍKU, ne jen dopočítaná** (`AppNavigation.ensureSettingsSection`).
  Zpět ze dvoupanelové scény odebírá seznam i sekci naráz a `NavDisplay` kvůli tomu volá
  `onBack` dvakrát (viz „Navigace"). S jen dopočítanou sekcí první volání odešlo do Chatů
  a druhé tam zavřelo otevřenou konverzaci. Dopočítává se jen na jeden snímek, než efekt
  sekci zapíše — jinak by seznam sekcí problikl přes celou šířku.
- **Efekt, který sekci zapisuje, stojí v shellu, ne v obrazovce seznamu sekcí.** Obrazovka po
  přepnutí sekce aplikace ještě dobíhá odchodovou animací a její efekty dál běží. Dokud byl
  efekt v ní, po rychlém Nastavení → Chaty přidal `SettingsSectionRoute` do zásobníku, který
  už patřil Chatům — vybrané byly Chaty, na ploše bylo „Obecné". Shell se ptá na vybranou
  sekci aplikace; klíčem je i velikost zásobníku Nastavení (návrat na kořen). **Obecně: efekt, který zapisuje do zásobníku, nepatří do
  obrazovky, která může být zrovna na odchodu.**
- **Šipka zpět je v sekci jen na compactu.** V dvoupanelu je seznam sekcí pořád vidět vedle
  a sekce se NAHRAZUJE, nevrší se zásobník — stejně jako konverzace.
- **Do Nastavení se chodí z menu účtu na každé šířce** (fotka ve vyhledávací pilulce seznamu
  chatů, `AppNavigation.openSettings`), a proto vždycky na kořen — menu slibuje „Nastavení",
  ne sekci, kterou tam uživatel kdysi nechal. Seznam sekcí má proto šipku zpět do Chatů vždycky
  (`SettingsScreen(onBack)`), i v dvoupanelu.
- **Šipka seznamu sekcí volá `closeSettings`, ne `goBack`.** `goBack` odebírá jednu položku,
  a v dvoupanelu je to otevřená sekce — efekt `ensureSettingsSection` ji hned vrátil a šipka
  nedělala nic. Dokud vedle stál rail, šipka v dvoupanelu nebyla a nic se nepoznalo.
  Systémové zpět to nepotřebuje: `NavDisplay` ho zavolá za obě položky scény naráz.
- **Seznam sekcí nemá ViewModel.** Obsah (účet, notifikace, motiv) patří sekcím; kdyby si účet
  načítal i seznam, ptali bychom se na něj v dvoupanelu dvakrát. Ze stejného důvodu se ViewModel
  sekce vyrábí až uvnitř `when` nad sekcí — složí se jen jedna větev.
- **Nadpis skupiny a její karta jsou jeden kus** (`SettingsGroup`). Jako dva sousedi ve sloupci
  sekce by nadpis visel stejně daleko od svojí karty jako od té předchozí.
- **Řádek nese klikání celý, ovládací prvek má `onClick = null`.** Do přepínače ani kolečka se
  nemá trefovat a čtečka obrazovky má číst jednu položku, ne dvě; semantiku dodá
  `selectable(role = RadioButton)` / `toggleable(role = Switch)`.
- **`ListItem` na tónované kartě NEDĚDÍ `contentColor` karty.** Text si barví sám
  (`onSurface`), takže `Surface(contentColor = onPrimaryContainer)` kolem něj nic neudělá —
  v tmavém tématu vyšel světlý název na světlé kartě Uložených zpráv. Barva popředí se musí
  předat i do `ListItemDefaults.colors`. Totéž platí pro vybraný řádek v `secondaryContainer`
  (výběr země) a pro `onSurfaceVariant`: ten je počítaný na surface role, na vybrané položce
  patří `on*Container` (čas a ikony u vybraného chatu).
- **Pozor na názvy barev `ListItemDefaults.colors`**: `contentColor` (ne `headlineColor`)
  a `supportingContentColor` (ne `supportingColor`). Hádaný název se pozná až překladem.
  Totéž ikona: `leadingContentColor`, ne `leadingIconColor` — ta patří STARÉMU přetížení
  s `headlineColor`, a v jednom volání se obě sady míchat nedají.
- **Velký nadpis se na jednom panelu sbaluje, v dvoupanelu ne** (`SettingsTopBar`,
  `settingsScrollBehavior`, podle `LocalTwoPane`). `LargeFlexibleTopAppBar` byl bez scroll
  behavior, takže se nesbaloval vůbec a na telefonu trvale bral sedminu výšky. Sbalený je 64dp
  lišta a **barvu nemění** (`scrolledContainerColor = surface`) — tónovaný pruh přes celou šířku
  je důvod, proč seznam chatů velký bar opustil. V dvoupanelu by se sekce vpravo sbalila, zatímco
  krátký seznam sekcí vlevo zůstal velký: dvě hlavičky různé výšky vedle sebe.
- **Spodní inset patří do scrollovaného obsahu, ne na kontejner** (`settingsContentPadding`,
  `settingsContentBottom`). S paddingem ze Scaffoldu na sloupci končila dlouhá sekce ostrou
  hranou nad navigační lištou — tentýž vzor jako seznam chatů a výběr chatu.
- **Sekce mají ikony** (`SettingsSection.icon`), v barvě popisu — na vybraném řádku tedy
  `onSecondaryContainer`, ne `onSurfaceVariant`. Ikona je dekorace (`contentDescription = null`),
  název stojí hned vedle. „Co je nového" má megafon, ne raketu: raketou se uvnitř sekce značí
  novinka u jedné poznámky.

### Soukromí účtu

Obecné → Soukromí → Soukromí účtu (`AccountPrivacyRoute`, `AccountPrivacyScreen`): seznam
nastavení se stavem („Moje kontakty · 2 výjimky") a detail jednoho z nich (`PrivacySettingRoute`,
`PrivacySettingScreen`): Všichni / Moje kontakty / Nikdo a výjimky „Vždy povolit" a „Nikdy
nepovolit" (lidé i skupiny, přidávají se přes `ChatPicker`). Hranice je `PrivacyRepository`
(`TdlPrivacyRepository`), pravidla jsou čisté funkce v `core/telegram/AccountPrivacy.kt` (testy
`AccountPrivacyTest`, `PrivacySettingTest`).

- **Je to nastavení ÚČTU**: TDLib ho drží na serveru a platí na všech zařízeních. Pravidla jsou
  živá (`UpdateUserPrivacySettingRules`, odběr PŘED prvním čtením), takže změnu z jiného klienta
  obrazovka ukáže sama. **Čas přečtení (`ReadDatePrivacySettings`) update NEMÁ** — čte se při
  otevření a přepínač se pohne hned, po selhání se vrátí.
- **Pravidla jsou seznam, kde vyhrává PRVNÍ shoda** a bez shody je zákaz. Výjimky proto jdou
  před základ (`AllowAll` / `AllowContacts` / `RestrictAll`), který je poslední. Tentýž člověk
  nemůže být „vždy" i „nikdy" (`withAdded` ho z druhé strany vyřadí) — TDLib by vzal pravidlo,
  které stojí dřív, a uživatel by nepoznal proč.
- **Zapisuje se CELÝ seznam** (`setUserPrivacySettingRules`, jiný zápis TDLib nemá), takže co
  obrazovka neumí (`AllowBots`, `AllowPremiumUsers`, `RestrictBots`, `RestrictContacts`), se
  přenese z naposledy přečtených pravidel (`privacyRulesOf`) — ale jen to, co stálo PŘED
  základem. Za základem se k ničemu nedostane a s novým základem by změnilo význam.
- **Výjimky, které se základem neplatí, se nezahazují** (`mergeHiddenExceptions`): u „Všichni"
  nemá „Vždy povolit" smysl a do zápisu nejde, ale přepnutí tam a zpátky ho musí vrátit. Server
  je po zápisu nevrátí, takže je drží stav obrazovky a čtení je s ním slučuje.
- **Past: zapsaná pravidla se berou za platná HNED, ne až s updatem** (`apply` ve
  `PrivacySettingViewModel`). První verze porovnávala nový zápis s naposledy PŘEČTENÝMI
  pravidly: rychlé A → B → A pak návrat porovnala se starým A, nic neposlala — a na serveru
  zůstalo B. Hlídá to test v `PrivacySettingTest` s podstrčeným repository.
- **Hledání podle čísla jen tam, kde číslo vidět není** (`privacyRowShown`): když telefon vidí
  všichni, najde ho podle čísla každý tak jako tak. TDLib tam bere jen Všichni / Moje kontakty
  bez výjimek (`PrivacySetting.FindByPhone`).
- **Hlasové zprávy omezit jde jen s Premium** — bez něj detail stav jen ukáže
  (`PrivacySettingState.editable`), server by zápis odmítl.
- **Kanál výjimkou být nemůže** (TDLib bere jen lidi a členy skupin), výběr ho nenabízí; tajný
  chat je člověk s id uživatele (`memberOf`).
- **Řádek „Soukromí účtu" je v bloku Soukromí první**, nad blokovanými.
- **Ověřeno na emulátoru JEN ČTENÍM** (anglicky): seznam se skutečnými stavy účtu a detail
  „Naposledy viděn" s výjimkami, nic se nepřepnulo. **Zápis na skutečný účet ověřený NENÍ**
  (mění soukromí všude) — zápis, rychlé přepínání a selhání pokrývají jen testy s podstrčeným
  repository. Zaškrtnutí vybrané volby `uiautomator` u řádků nehlásil (`checked="false"` u všech
  tří), vizuálně snímkem to ověřené není. iOS jen přeložené a slinkované.

### Úprava profilu

Karta účtu v Obecných otevře `EditProfileRoute` (`EditProfileScreen`, `AccountRepository`): fotka,
jméno, příjmení, uživatelské jméno a bio.

- **Routa se vrší nad sekci Obecné** — na telefonu jako obrazovka, v dvoupanelu nahradí sekci
  vpravo. **Zpět tam musí být `PopUntilContentChange`** (jen když je úprava nahoře): dvoupanel
  zůstává dvoupanelem, takže výchozí `PopUntilScaffoldValueChange` odebral úpravu i sekci pod
  ní a gesto zpět odešlo z Nastavení rovnou do Chatů. Klik na jinou sekci úpravu ze zásobníku
  odebere taky, jinak by visela pod novou sekcí.
- **Fotka se ukládá hned, texty až tlačítkem Uložit.** Fotka má vlastní nahrávání; kdyby čekala
  na Uložit, zmizela by s odchodem. Ořízne se na čtverec podle výřezu z ořezu (`SquarePhoto`,
  platformní; viz „Ořez profilové fotky") — kreslí se v kruhu a neořezanou by každý klient
  ořízl jinak.
- **Text polí je Compose stav přímo ve ViewModelu**, ne `StateFlow` — jinak poskakuje kurzor.
- **Uživatelské jméno se kontroluje dvakrát**: místní pravidla hned při psaní
  (`usernameFormatError`, testy `UsernameFormatTest`), pak dostupnost na serveru po pauze
  (`checkChatUsername`). Vlastní účet se ptá přes **soukromý chat se sebou** (id chatu = id
  uživatele) — TDLib jiný chat odmítne větou „Can't check username for private chat with other
  user". Nezjištěná dostupnost ukládání neblokuje, rozhodne server.
- **Jméno, uživatelské jméno a bio jsou tři volání.** Když jedno selže, další se neposílají
  a co prošlo, se zapíše jako výchozí stav — další pokus ho neposílá znovu.
- **Strop bia posílá server** (`bio_length_max`, bez Premium 70); počítadlo je vidět pořád.
- **Účet je živý** (`AccountRepository.account` nad `UpdateUser`): karta v Obecných i fotka ve
  vyhledávací pilulce se po úpravě změní samy, i když se profil upravil na jiném zařízení.
- **Ověřeno bez zásahu do účtu**: načtení, všechny tři stavy kontroly jména (krátké, obsazené
  „durov", volné), odchod bez uložení a zpět v dvoupanelu. Uložení a změna fotky mění skutečný
  profil, proto se zkouší jen se souhlasem.

### Ořez profilové fotky

Vybraná fotka (vlastní profil, úprava skupiny i založení skupiny nebo kanálu) se napřed otevře
v ořezu (`PhotoCropOverlay`): fotka pod kruhovým výřezem, posun a přiblížení prsty, „Hotovo"
vrátí výřez a teprve pak se fotka ořízne a nahraje. Dřív se ořezávala vždycky ze středu.

- **Výřez je v PODÍLECH rozměrů fotky** (`CropRegion`), ne v pixelech. Ořez ukazuje fotku
  zmenšenou na 1600 px (Coil), platforma ořezává originál; poměr stran mají oba stejný.
  Na pixely ho převádí `squarePixels` a srovná na čtverec uvnitř fotky — zaokrouhlení podílů
  by jinak z kruhu udělalo elipsu o pixel.
- **Geometrie je čistá funkce** (`PhotoCropGeometry`, testy `PhotoCropGeometryTest`): fotka
  musí kruh vždycky celý zakrývat, přiblížení 1–5× kolem prstů (bod pod prsty zůstane na
  místě, jako v prohlížeči médií).
- **Překryv přes celou obrazovku, ne `Dialog`.** Dialog je na Androidu vlastní okno a ve
  společném kódu (`DialogProperties` má jen tři volby) nejde nechat sahat pod stavovou lištu;
  nad černým ořezem by svítil pruh ztmavené obrazovky. Obrazovka ho proto dává za Scaffold do
  `Box`u, zpět zavírá `NavigationBackHandler`. Stejná cesta jako náhled přílohy nad polem.
- **Fotka čeká ve ViewModelu** (`cropping`), ne v obrazovce: výběr fotky je ve ViewModelu
  odjakživa a otočení displeje ořez nesmí zavřít.
- **Ověřeno na emulátoru** ve formuláři „Nová skupina" (náhled fotky ukazuje výsledek bez
  nahrání čehokoli): fotka na šířku vyplní kruh výškou, posun doleva se zastaví na okraji
  fotky a náhled ukáže přesně výřez z kruhu; zpět ořez zavře a fotka se nezmění. **Přiblížení
  dvěma prsty ověřené jen testy** (přes `adb` dvěma prsty neťukneš), iOS jen přeložené.
- **Výběr fotek na emulátoru může být prázdný**, než si picker načte knihovnu — po pár
  vteřinách se mřížka objeví sama. Testovací obrázek jde do galerie `adb push` do
  `/sdcard/Pictures` a vysílání `MEDIA_SCANNER_SCAN_FILE`; po ověření ho smazat i z MediaStore.
- **Ořez se nesmí kreslit mimo svou plochu** (`clipToBounds` na ploše fotky). Posunutá nebo
  přiblížená fotka (`graphicsLayer`) jinak přetekla přes nadpis i stavovou lištu.

#### Video jako profilovka

Z výběru jde vybrat i video: stejný ořez, nadpis „Upravit video" a pod ořezem pás snímků,
na kterém se volí úsek (nejvýš 10 s, bez zvuku) a snímek pro statickou fotku. Nahraje se jako animovaná fotka (`InputChatPhotoAnimation`,
`ProfilePhotoFile.Animated`) — u vlastního profilu, úpravy skupiny i založení chatu. Postup
sdílí všechny tři obrazovky (`ProfilePhotoCrop` v `core/ui`, testy `ProfilePhotoCropTest`),
převádí platforma (`ProfileVideo`).

- **Podmínky Telegramu jsou přísné**: čtvercové MP4, nejvýš 10 s, strana 160–1280 px, do 2 MB.
  Video z galerie nesplní skoro nikdy, takže se vždycky převádí: výřez, zmenšení na 800 px,
  10 s od zvoleného začátku, bez zvuku.
- **Výřez se vybírá nad SNÍMKEM** (`ProfileVideo.frame`, 1600 px) a v podílech platí pro
  celé video. Náhled z výběru příloh má jen 320 px — výřez by se vybíral z rozmazaného obrázku.
  Náhled u zakládaného chatu je týž výřez ze snímku, ne video.
- **Úsek a snímek pro statickou fotku** (`VideoTrim`, testy `VideoTrimTest`; pás `VideoTrimBar`):
  úsek má pevnou délku a posouvá se tahem jako celek, snímek je čárka v něm (tahem za ni nebo
  ťuknutím do pásu). Tah se rozhoduje podle toho, kde začal — u čárky snímek, jinde úsek; u videa
  do 10 s jde každý tah na snímek. Snímek jede s úsekem a leží vždycky UVNITŘ, nejpozději
  100 ms před koncem (poslední snímek animace začíná dřív, než skončí). Do TDLib jde jako
  `mainFrameTimestamp` = čas od začátku ÚSEKU, ne videa (`ProfilePhotoFile.Animated.mainFrameSeconds`).
- **Ořez ukazuje vybraný snímek**, takže se výřez vybírá nad tím, co bude fotkou, a náhled
  u zakládaného chatu je výřez z něj. Nový snímek se tahá až po 150 ms klidu. Výměna obrázku
  nesmí probliknout: rozměry se drží z posledního načtení (snímky téhož videa jsou stejně
  velké) a Coil dostane `placeholderMemoryCacheKey` z posledního úspěchu — bez toho by ořez při
  každé změně snímku spadl do načítání a zavřel se. Geometrie a posun výřezu zůstávají klíčované
  cestou PRVNÍHO snímku (sezení ořezu), ne aktuálního.
- **Snímky mají soubor na čas a velikost** (`profile-frame-<edge>-<ms>.jpg` vedle zdroje)
  a podruhé se jen vrátí. Snímek pro fotku je PŘESNÝ (`OPTION_CLOSEST`, na iOS nulové
  tolerance), dlaždice pásu nejbližší klíčový (rychlé). Zápis přes dočasný soubor
  a přejmenování: pás a ořez čtou souběžně.
- **Čtečka obrazovky tahat neumí**: pás má popis stavu („Úsek 0:07–0:17 · fotka 0:15")
  a akce po sekundě pro úsek i snímek.
- **Android: Media3 Transformer** (`Crop`, `Presentation`, ořez `MediaItem`, `setRemoveAudio`).
  `Crop` bere souřadnice −1…1, svisle zdola nahoru. Výřez platí pro OTOČENÝ obraz — dekodér
  snímky otočí dřív, než na ně jdou efekty; ověřeno videem z kamery s `orientation=270`. Export
  sdílí s kolečky `exportVideo` (`core/video`), datový tok 1,2 Mbit/s a když soubor přesto
  přeteče 2 MB, zkusí se polovina.
- **iOS: kompozice jen se stopou obrazu** (tím zmizí zvuk), zkrácená na 10 s,
  `AVMutableVideoComposition` s výřezem a export `MediumQuality` s `fileLengthLimit` na 2 MB
  (datový tok export nastavit neumí). `fileLengthLimit` se zapisuje setterem s vlastním importem
  (`setFileLengthLimit`) — jako vlastnost ho Kotlin nevidí. **Jen přeložené.**
- **Na emulátoru Transformer padá v dekodéru H.264** (`c2.goldfish.h264.decoder`,
  `queueInputBuffer error: 14`, v logu zamítnuté `memfd` SELinuxem) — tatáž vada jako u GIFů
  a videa. Převod se ověřil sondou, která v `exportVideo` vyřadí dekodéry goldfish
  (`DefaultAssetLoaderFactory` s `DefaultDecoderFactory.setMediaCodecSelector`); necommituje se.
- **Testovací video na emulátoru natočí systémová kamera** (`am start -a
  android.media.action.VIDEO_CAMERA`, ťuknutí na spoušť): virtuální scéna, žádní lidé, a video
  na výšku s otočením v metadatech. Dotaz na polohu odmítnout. Po ověření videa i fotky smazat
  z MediaStore.
- **Úsek a snímek ověřené na emulátoru** ve formuláři „Nová skupina" (nic se nenahrálo):
  19s video, úsek posunutý na 0:07–0:17 a snímek 0:15 (haptika `constant=26` v `dumpsys
  vibrator_manager`), export 718 × 718 a 9,2 s, jeho první snímek je výřez z 0:07 a snímek
  v 7,65 s odpovídá vybrané fotce; náhled ve formuláři je výřez ze snímku 0:15. Snímky exportu
  vytáhne na Macu `AVAssetImageGenerator` v krátkém Swift skriptu (ffmpeg v systému není);
  zdrojové video z emulátorové kamery macOS NEotevře, srovnává se se snímky, které vyrobila
  aplikace. **Že server vezme `mainFrameTimestamp` z vybraného snímku, ověřené NENÍ** —
  chtělo by to nahrát profilovku (skutečný účet nebo další testovací chat).
- **Ověřeno na emulátoru** v testovací skupině „Plume test zalozeni": 21s video na výšku →
  čtverec 718 × 718, 9,7 s, 604 kB, natočený správně a přesně podle výřezu; prohlížeč
  profilovek ukazuje u fotky „Přehrát" (server ji vzal jako animovanou). Fotka jde dál starou
  cestou. **Vlastní profil a založení chatu s videem ověřené nejsou** (mění skutečný účet /
  zakládají další chat), jdou ale stejnou cestou.

## Dvoufázové ověření

Nastavení → Obecné → Soukromí → Dvoufázové ověření (poslední řádek bloku, „Zapnuto" /
„Vypnuto"; dokud stav nedorazí, podtitul není). Obrazovka stavu (`TwoStepRoute`,
`TwoStepScreen`) a průvodci: zapnutí (heslo dvakrát, nápověda, nepovinný obnovovací e-mail
a kód z něj), změna hesla, změna e-mailu, vypnutí a zapomenuté heslo — e-mailem, nebo bez něj
reset po sedmi dnech čekání. Hranice je `PasswordRepository` (`TdlPasswordRepository`,
doména `TwoStepVerification.kt`), kroky průvodce čisté funkce v `TwoStepFlow.kt` (testy
`TwoStepFlowTest`, ViewModel `TwoStepViewModelTest`).

- **Heslo s novým e-mailem začne platit až po kódu z e-mailu.** `setPassword` s e-mailem vrátí
  stav s `pendingEmail` (maska a délka kódu) a heslo zapne teprve `checkRecoveryEmailAddressCode`.
  Průvodce proto odesílá na kroku E-mail (`commitsAt`), ne na nápovědě, a z kroku Kód se zpět
  NEvrací (`previous` = `null`): změna už je na serveru, a zopakovat ji by poslalo další kód.
  Rozdělané potvrzení e-mailu (zavřený průvodce, jiné zařízení) ukazuje obrazovka stavu jako
  kartu se „Zadat kód", s posláním kódu znovu a se zrušením
  (`cancelRecoveryEmailAddressVerification`).
- **Bez e-mailu se nejdřív zeptá** (dialog „Pokračovat bez e-mailu?"): zapomenuté heslo pak
  půjde zrušit jen po týdnu. Na server do potvrzení nejde nic.
- **Kroky jsou stav ViewModelu, ne routy.** V zásobníku navigace by hesla putovala do
  serializovaného stavu, který přežije i zabití procesu. Ze stejného důvodu **hesla nejsou
  `rememberSaveable`**, drží je Compose stav ve ViewModelu a po konci průvodce (i po zpět
  z prvního kroku) je `clearSecrets()` vynuluje. Zpět uvnitř průvodce jde o krok zpátky
  (`NavigationBackHandler`).
- **Hesla se nelogují ani v chybách.** Chyba z TDLib jde do logu (`logger.w` s výjimkou), vstup
  ne. Heslo se neořezává (mezera na konci je jeho součást), kód a e-mail ano. Nápověda shodná
  s heslem se odmítne hned, stejně jako při přihlášení.
- **Vypnutí napřed ověří současné heslo** (`getRecoveryEmailAddress` — čtení, které heslo
  vyžaduje, ale nic nemění) a teprve pak se ptá dialogem. Tlačítko kroku proto říká
  „Pokračovat", ne „Uložit" — uložení je až v dialogu.
- **Zapomenuté heslo bez e-mailu je reset** (`resetPassword`): `Pending` = heslo se zruší
  k datu (karta se „Zrušit reset" a `cancelPasswordReset`), `Declined` = zkusit znovu až
  k `retryAt`, `Done` = zrušené hned. Stav čekání se čte z `PasswordState.pendingResetDate`
  (`resetProgress`).
- **`EMAIL_INVALID` má vlastní větu** v `userMessage`; zbytek chyb (`PASSWORD_HASH_INVALID`,
  `CODE_INVALID`, `PASSWORD_RECOVERY_EXPIRED`…) jde tabulkou jako jinde.
- **Past: vlastní pomocník na log s českým textem neprojde `verifyNoHardcodedText`.** Kontrola
  pouští diakritiku jen na řádcích s `logger.`; `logFailure("Nepodařilo se…")` se proto
  vrátil zpátky na `onFailure { logger.w(it) { "…" } }`.
- **Ověřeno**: na skutečném účtu jen ČTENÍ stavu (řádek „Vypnuto", obrazovka „vypnuté"
  s „Nastavit heslo"). Průvodci na emulátoru PODSTRČENÝM repository (sonda v Koinu, necommituje
  se): zapnutí s e-mailem a kódem i bez e-mailu, chyby (neshoda hesel, nápověda = heslo,
  neplatný e-mail, špatný kód), obnova e-mailem (špatný i dobrý kód, nové heslo), reset bez
  e-mailu a jeho zrušení, vypnutí se špatným i dobrým heslem. **Skutečné nastavení hesla,
  skutečný kód z e-mailu a reset ověřené NEJSOU** — mění skutečný účet; ověří je uživatel
  zapnutím na zařízení. iOS jen přeložené a slinkované.

## Jazyky

**Výchozí jazyk je angličtina** (`composeResources/values/strings.xml`), čeština je mutace
(`values-cs/`). Texty se čtou přes `stringResource(Res.string.…)`; v kódu nesmí zůstat literál,
který uvidí uživatel.

- **Zpětné lomítko před apostrofem se v Compose Multiplatform NEROZBALÍ.** Android resources
  `\'` vyžadují, tady by se uživateli ukázalo „you\'ll". Ověřeno na zařízení — do XML patří
  apostrof tak, jak je; escapují se jen XML entity (`&`, `<`).
- **`stringResource` je `@Composable`, `StringResource` ale ne.** Proto ViewModel i doménový
  stav nosí `UiText` (odkaz do prostředků + argumenty) a překládá se až v UI. Text poskládaný
  ve ViewModelu by po změně jazyka zůstal v tom starém.
- **I převedené zprávy nesou `UiText`, ne hotovou větu.** Servisní oznámení (`serviceText`),
  popis obsahu (`describe`, „Anketa: otázka") i náhled poslední zprávy (`previewText`, „Fotka")
  vrací `UiText`; zpráva ho má v `ChatMessage.description` (vlastní text zůstává v `text`,
  u popisných zpráv prázdný), seznam chatů v `ChatSummary.lastMessagePreview`, témata
  v `TopicMessagePreview.text`, hledání ve `FoundMessage.text`. Dokud se tam skládal řetězec
  přes `getString`, zůstal po přepnutí jazyka ve starém: proces změnu jazyka přežije (Activity
  se jen složí znovu) a s ním i převedené zprávy v paměti repository a ViewModelů.
  - **`UiText` umí větu (`Resource`), plurál (`Plural`) a nepřekládaný text (`Raw`)**, a argument
    smí být sám `UiText` („Automatické mazání zpráv: 1 den", délka je vnořený plurál). `Raw` je
    tu proto, aby jedno pole uneslo „popisek, a když není, slovo Fotka" (`orText`).
  - **Kdo kreslí zprávu slovy, bere `text`, a když je prázdný, `description`**: bublina
    (`MessageText`), servisní oznámení, citace, pruh připnutých a naplánované zprávy
    (`summaryLine`). Kopírování, úprava, výběr textu a překlad berou jen vlastní `text` — náš
    popis z nich ven nepatří (u ankety to platilo odjakživa).
  - **Mimo kompozici se překládá `resolveNow()`** (`suspend`, jazyk platný TEĎ): notifikace,
    snackbar, přepis pro AI shrnutí a návrhy odpovědí. Výsledek se nikam neukládá.
  - **Vědomé výjimky, které zůstávají řetězcem:** náhradní název souboru a skladby bez jména
    („Soubor", „Audio" — je to jméno, pod kterým se soubor ukládá a které dostává systémový
    přehrávač) a štítky médií uvnitř článku („[Fotka]" — leží uprostřed textu s entitami
    a jeho offsety by se překladem posunuly). Po změně jazyka zůstanou ve starém, než se zpráva
    načte znovu.
  - **Najít, kdo text čte, pomohl překladač**: pole `ChatMessage.text` se dočasně přejmenovalo
    a chyby překladu vypsaly všech 88 míst. Grep podle `.text` by část minul (`it.text`,
    destrukturování) a ta by po změně tiše kreslila prázdno.
  - Ověřeno na emulátoru v jednom procesu (pid se nezměnil): seznam chatů, seznam témat
    a konverzace se servisními zprávami tam i zpět mezi češtinou a angličtinou.
- **Čeština má tři tvary množného čísla** (1 / 2–4 / 5+), angličtina dva. Kde se počítá, patří
  `plurals`, ne `when` nad číslem — viz „N nových zpráv".
- **Datum není jen překlad slov.** Pořadí dne a měsíce se liší, čeština chce měsíc ve 2. pádu
  („5. února"), angličtina v základním. Proto je i pořadí formátovací řetězec (`date_day_month`),
  ne slepenec v kódu.
- **`verifyTranslations` visí na `check`.** Chybějící klíč by se jinak poznal až na obrazovce:
  Compose spadne zpátky na výchozí jazyk, takže uprostřed české aplikace vyskočí anglická věta
  a nic to nenahlásí.
- **`verifyNoHardcodedText` hlídá, že se text nevrátí do kódu.** Má dvě pravidla, protože
  s jedním to dvakrát propadlo: slot v UI (`Text(`, `text =`, `contentDescription = …`) nesmí
  dostat literál vůbec a český text nesmí být nikde mimo komentáře, logy a vnitřní kontroly.
  Hledat jen podle diakritiky nestačí — „Odebrat soubor" ani „Video" tak nenajdeš.
- **Prompt pro model je výjimka z JEDNOHO z těch dvou pravidel, ne z celé kontroly.** Instrukce
  pro Gemini Nano musí jmenovat „tykání/vykání" česky (bez toho model kamarádovi vykal, viz
  „Návrhy odpovědí"), takže promptové soubory v `core.ai` mají vypnuté pravidlo o diakritice —
  pravidlo o slotu v UI v nich platí dál. Prompt nejde na obrazovku, ale do modelu; je to tedy
  táž kategorie jako logy a vnitřní kontroly, které kontrola vynechává odjakživa. **Escape
  sekvence jsou na DATA, ne na prózu:** tabulka diakritiky v `SearchHighlight.kt` se čte
  strojem, kdežto prompt se ladí ČTENÍM a „tyk\u00e1n\u00ed" uprostřed anglické věty přečíst
  nejde. Výjimka visí na JMÉNU souboru, ne na balíčku, a **ověřila se sondami, ne úvahou**:
  český literál v `core/ai/OnDeviceAi.kt` i `Text("…")` uvnitř `ReplyPrompt.kt` kontrolu dál
  rozsvítí, takže se dírou nestalo nic než to, co se jí stát mělo.
- **Že výjimka platí, říká hlavička KAŽDÉHO promptového souboru, stejnou větou.** Napřed to
  stálo jen v KDoc `formalityInstruction` — tedy u jedné funkce v jednom ze dvou souborů, které
  pravidlo řídí: `SummaryPrompt.kt` o vlastní výjimce mlčel a s přepsáním té funkce by z kódu
  zmizela poslední stopa. Nese to totiž JMÉNO souboru, takže kdo zakládá třetí prompt, musí se
  o konvenci dozvědět od souboru vedle; jinak mu kontrola spadne na hlášku o composeResources,
  ze které důvod nepozná. Úplné zdůvodnění zůstává na jednom místě (komentář u kontroly
  v `build.gradle.kts` a tenhle odstavec) — hlavička je ukazatel, ne druhá kopie pravidla.
- **Ověřuje se přepnutím jazyka aplikace**, ne celého systému:
  `adb shell cmd locale set-app-locales eu.tintera.plume --locales cs-CZ` (prázdná hodnota vrátí
  systémový). Restart aplikace stačí, emulátor rebootovat netřeba.

## Navigace

Navigation3 (`rememberNavBackStack` + `NavDisplay` + `entryProvider`). Klíče jsou v
`core/navigation/Routes.kt` a jsou `@Serializable` — bez toho by se zásobník nepřežil zabití procesu.

Seznam chatů a konverzace tvoří **list-detail** dvojici přes `rememberListDetailSceneStrategy`:
`ChatsRoute` nese metadata `listPane(detailPlaceholder = …)`, `ConversationRoute` má `detailPane()`.
`directive` se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna — scaffold
má rozhodovat podle místa, které opravdu dostane.

**Boční insety NEVĚDÍ, kde prvek leží.** Compose dá `safeDrawing`/`displayCutout` každému, kdo si
o ně řekne, i když stojí daleko od kamery. Telefon naležato proto měl v konverzaci (panel VPRAVO)
mezeru o šířku výřezu vlevo — v hlavičce a seznamu zpráv, ne ve vstupním poli, které si boky
nebralo vůbec. Pravidlo: kdo inset vyřešil, ten ho **zkonzumuje** (`consumeWindowInsets`).
Seznam v dvoupanelu konzumuje pravý inset (`listPaneInsets`) — ten patří panelu vpravo. Vstupní pole bere `safeDrawing` bokem i spodkem, jinak by odeslání zajelo pod
výřez nebo boční třítlačítkovou lištu. Ověřuje se v OBOU rotacích naležato
(`adb shell cmd window user-rotation lock 1` / `3`, pak `free`) — výřez je pokaždé na jiné straně.

**`LazyColumn` ořezává jen ve směru scrollu.** Vodorovně kreslí i mimo své hranice, takže sloupec
časů schovaný „za pravým okrajem" (odkrývání tahem) byl schovaný jen dokud seznam končil
u okraje obrazovky. S insetem vpravo prosvítal; proto `clipToBounds` na ploše seznamu.

**Sekční navigace není — ani spodní lišta, ani rail.** Sekce jsou dvě a navigační lišta i rail
mají podle M3 smysl až od tří. Lišta zabírala ~80 dp jen kvůli skoku do Nastavení a seznam chatů
pod ní působil přeplácaně; rail na širší ploše přežil o něco déle, ale jen zdvojoval menu účtu
a ukusoval šířku panelům. Do Nastavení vede menu účtu v pilulce na každé šířce, spodek obrazovky
patří FAB menu „Vytvořit" (viz „Složky místo filtrů a FAB"). Důsledek pro insety:
shell žádný nekonzumuje, každá obrazovka si je bere sama (Scaffold je má ve výchozích
`contentWindowInsets`). **Kdyby sekcí přibylo na tři**, přijde rail zpátky — past s místem:
skrytí musí animovat i SLOT (`shrinkHorizontally`), ne jen posun, jinak obsah doskočí až na konci.

**Avatar a název letí ze seznamu do konverzace.** Klíče `chatAvatarKey` / `chatTitleKey`
obsahují id chatu, jinak by se při rychlém přepnutí spároval avatar jednoho chatu s druhým.
Na rozdíl od letu odeslané zprávy se tu viditelnost neřídí ručně — obě strany jsou
v `AnimatedContent` NavDisplaye, takže stačí `LocalNavAnimatedContentScope`.

- **Tečka „online" letí VLASTNÍM sdíleným prvkem** (`chatOnlineDotKey`, `OnlineDot(chatId)`).
  Sdílený byl jen avatar, tečka vedle něj zůstala v obsahu obrazovky: avatar odletěl do hlavičky
  bez ní a tečka se k němu přilepila, až dosedl — problikla. Obalit sdíleným prvkem avatar
  i s tečkou nejde, stejný klíč `chatAvatarKey` nese i profil a tvář odesílatele, které tečku
  nemají, a obal by se do nich zmenšoval s avatarem pevné velikosti uvnitř. Obě strany mají
  tutéž pružinu, takže tečka po celou dráhu drží roh avataru.
- **Tečka musí mít v překryvu vyšší `zIndexInOverlay`** (`sharedNavElement(zIndexInOverlay)`).
  Sdílené prvky se během letu kreslí v překryvu mimo pořadí rodičů; se stejným indexem by ji
  letící avatar mohl přikrýt.
- **Ověřeno na emulátoru sondou** (Notifikátor podstrčený jako online v seznamu i hlavičce,
  necommituje se) a sérií surových snímků se zpomalenými animacemi, tam i zpět. Online
  kontakt se neotevírá — přečetly by se mu zprávy.

**Na široké ploše jsou dva panely vždycky** (kromě prázdného seznamu, viz „Archiv"), i bez otevřeného chatu — vpravo je pak
`ConversationPlaceholder`. Dřív dostal seznam bez konverzace celou šířku (`singlePane`), takže
placeholder, i když byl v metadatech `listPane`, se nikdy nekreslil, řádky chatů se natáhly přes
celý rozložený Fold a hlavička s pevnou maximální šířkou v tom visela.

- **Zpět v Chatech musí být `PopUntilContentChange`.** Výchozí `PopUntilScaffoldValueChange`
  přeskakuje položky, které nemění rozložení panelů; „seznam + placeholder" a „seznam + chat"
  jsou oba dvoupanel, takže zpět z konverzace odešel rovnou z aplikace. V Nastavení zůstává
  výchozí — sekce je v dvoupanelu otevřená vždycky (`ensureSettingsSection`), a kdyby zpět
  odebral jen ji, efekt by ji hned vrátil a z Nastavení by nešlo odejít.
- **Placeholder má úvodní animaci** (`IntroShape` nad `ShapeMorphs.Intro`): tvar naroste, protočí
  se a projde kruh → čtyřlístek → sedmiúhelník → zaoblená hvězda, pak naskočí ikona a text. Běží
  při každém zobrazení, i po zavření chatu, a pak stojí. Průběh tvaru jde po úsecích jednou dráhou
  (`tween` s emphasized decelerate), otočení a zvětšení jsou pružiny — hvězda dokmitne, i když
  tvar už stojí. Tvar se čte až v `drawBehind`, animace nic nerekomponuje. **Tentýž tvar má
  i přihlášení** — viz „Přihlašovací obrazovka".
- **Hvězda má cípy zaoblené víc než žlábky** (`rounding` 0,24, `innerRounding` 0,1). Se stejným
  zaoblením obou vyšla kytička.
- **Cípů je SEDM, ne pět.** Pěticípá hvězda má jeden cíp nahoře a dva dole, takže její optický
  střed nesedí se středem obrysu, do kterého se vepisuje ikona — tvar působil nevystředěně
  (`MorphShape` centruje podle obalového obdélníku, a ten u nesymetrické hvězdy leží jinde než
  těžiště). S vyšším lichým počtem je rozdíl neznatelný a hvězda zůstane hvězdou. Patří k tomu
  mělčí žlábky (vnitřní poloměr 0,68 místo 0,58); se sedmi hlubokými cípy je z toho ježek.
- **Mnohoúhelník před hvězdou má stejný počet vrcholů jako ona**, jinak se při morfu přeskupují.
  Proto se nebere `MaterialShapes.Pentagon` — ten sedl jen k pěti cípům.
- **Tvar se dá proanimovat znovu** (`IntroShape(replayKey = …)`): opakování je kratší než úvod,
  otáčí se opačně a NEodhaluje obsah — ikona uvnitř se mění sama a dvojí naskakování by se pralo.

**Scaffold list-detail si sám bere fokus — vypnuto** (`shouldAutoFocusCurrentDestination = false`
v `PaneDirective.kt`). `ThreePaneScaffold` při každé změně aktuálního panelu zavolá na panel
`requestFocus()`; fokus propadl na první fokusovatelný prvek konverzace, tedy vstupní pole,
a na rozloženém Foldu vyjela klávesnice při každém otevření chatu i po návratu do něj.
Na compactu (a na zaklapnutém Foldu) se to nedělo, protože jeden panel scaffold nekreslí —
proto to dlouho vypadalo jako chyba konverzace. Výchozí hodnota je `true` a nastavuje ji
i konstruktor `PaneScaffoldDirective` BEZ toho parametru, takže se musí předat výslovně.

**V dvoupanelu se nesdílí nic — kromě fotky do prohlížeče** (`LocalTwoPane`, poskytuje ho
shell). Seznam vedle konverzace zůstává vidět, takže by avatar odletěl z položky, která je pořád
na obrazovce, a o jeden klíč by se přetahovaly dva prvky naráz. Prohlížeč médií ale nahradí celý
dvoupanel, takže fotka z bubliny nemá s kým se přetahovat — `sharedNavElement(inTwoPane = true)`
proto dostávají všechny klíče `photoKey` a nic jiného. **Kdo přidá `inTwoPane` jinam, musí vědět,
že druhá strana letu dvoupanel nahrazuje**, jinak se vrátí přesně ten souboj o klíč.

**Text se sdílí přes `sharedBounds` + `scaleToBounds`, ne přes `sharedElement`.** Název chatu má
v hlavičce jinou velikost písma; se `sharedElement` by se text na konci letu přeskládal skokem.

**Predictive back dělá `NavDisplay` sám** (přes `androidx.navigationevent`): při tažení zpět se
konverzace zmenší a odsune a pod ní se odkryje živý seznam. Nic se pro to nezapíná —
`android:enableOnBackInvokedCallback` systém při targetSdk 35+ ignoruje a je v manifestu jen
jako výslovné prohlášení.

**Přechody mezi obrazovkami jsou vlastní, ne výchozí z Nav3** (`NavTransitions.kt`, předané
do `NavDisplay`). Výchozí přechod na Androidu je 700ms prolnutí obou obrazovek NARÁZ: obě jsou
neprůhledné, takže v půlce jsou obě napůl průhledné přes sebe (nadpis „Nastavení" přes pilulku
hledání) a vypadá to jako chyba vykreslení. Výchozí zpět gestem zmenšuje obrazovku na 70 %.

- **Tlačítkem je to sdílená osa X (fade through):** odcházející zmizí za 90 ms, přicházející se
  objeví až potom, obě se posunou o 30 dp ve směru navigace. Dvojí expozice nenastane nikdy.
- **Gestem je to fade through podle specifikace Googlu** (návrhové zásady Androidu, „Predictive
  back" → „Full screen surfaces"): odcházející 100 → 90 % a zmizí do 35 % tahu, přicházející
  110 → 100 % a objevuje se od 35 %, křivka (0,1; 0,1; 0; 1). **Specifikace platí pro návrat
  UVNITŘ aplikace**; systémový návrat mezi aktivitami (pod zmenšenou obrazovkou se odkrývá ta
  předchozí) je jiný vzor a Google k němu čísla nezveřejňuje. Tím jsme začali a po švihnutí
  obrazovka stála a pak pomalu vybledla — s fade through je odchod hotový v první třetině tahu.
- **Průběh gesta je lineární a Google říká, že se tak přímo použít nemá.** Křivka proto jde na
  zmenšení; s lineárním zmenšením nebylo v půlce tahu vidět nic, protože průběh k jedničce
  skoro nedojde.
- **Délka přechodu gestem neřídí tah, jen dojezd po puštění prstu.** Tah jde po zlomcích
  (`seekTo`), zbytek `NavDisplay` dojede sám v čase přechodu a rychlost prstu nebere v úvahu
  (Nav3 1.1.1 i 1.2.0-alpha04, nastavit to nejde). Rychlé švihnutí se pouští u malého zlomku,
  takže se přehraje skoro celý přechod — proto krátkých 240 ms.
- **Při dojezdu se volá jen přechod pro gesto, ne pro tlačítko** (ověřeno dočasným logem ve
  specifikacích). Hádat ze snímků, KTERÁ vrstva se prolíná, nejde: napůl průhledná horní vrstva
  se stejně barevným pozadím vypadá jako „šednoucí" spodní.
- **Zaoblené rohy nese dekorátor scény, ne přechod** (`PredictiveBackCorners`,
  `sceneDecoratorStrategies`). `ContentTransform` tvar neumí; dekorátor obalí obsah scény
  a poloměr animuje nad TÝMŽ přechodem (`transition.animateDp` podle `EnterExitState`), takže
  ho gesto posouvá se zmenšením. Obal musí nést v klíči i třídu obalené scény: `NavDisplay`
  rozlišuje scény podle třídy a klíče a třída obalu je u všech stejná. **U fade through jsou
  rohy jen jemný detail** — zaoblená obrazovka je vidět do 35 % tahu, pod ní plocha stejné barvy.
- **Seznam chatů se při návratu skládá celý znovu, data ale ne.** `NavDisplay` kreslí jen horní
  scénu, takže kompozice seznamu pod Nastavením neexistuje; `ChatsViewModel` s načteným
  seznamem i pozice scrollu (`SaveableStateHolder`) přitom zůstávají. Kompozici ve ViewModelu
  držet nejde — kdyby vadila, cesta je nechat scénu pod Nastavením složenou (Nastavení jako
  překryvná scéna), ne cachovat data.
- **Plynulost se na emulátoru neměří.** Změřeno `gfxinfo framestats` v RELEASE buildu: návrat
  z Nastavení měl snímky kolem 60 ms, jenže obyčejný scroll seznamu chatů 200–500 ms a GPU hlásilo
  95. percentil 4950 ms. Pomalost skládání se od pomalosti emulátoru oddělit nedá; posuzuje se
  na telefonu.
- **Ověřuje se zpomalenými animacemi a sérií surových snímků** (`animator_duration_scale 20`,
  `screencap /sdcard/N.raw` ve smyčce v jednom `adb shell`): PNG snímek trvá přes 400 ms a přechod
  mine. Gesto zastavené v půlce jde přes `input motionevent DOWN/MOVE`, snímek, pak `UP`.
  Emulátor gesto seekuje stejně jako telefon, plynulost se ale posuzuje na zařízení.

**Past: obyčejný `BackHandler` predictive back zabije.** Kdo odchytí zpět bez
`PredictiveBackHandler`/`NavigationBackHandler`, dostane jen hotové gesto bez průběhu, takže
animace zmizí a nikde se neohlásí chybou. V aplikaci teď žádný `BackHandler` není a nemá
přibýt — ven z obrazovky se chodí přes `onBack` NavDisplaye. Kde je potřeba zpět zachytit uvnitř
obrazovky (nabídka nad zprávou), je to `NavigationBackHandler` z `androidx.navigationevent`.

### Zásobník pro každou sekci

Každá sekce aplikace (Chaty, Nastavení) má **vlastní zásobník** a všechny drží jeden stav,
`AppNavigation`, vytvořený nahoře v `AppShell` a podávaný dolů. Přepnutí sekce nic nemaže, jen
vybere jiný zásobník — návrat do Chatů vrátí otevřenou konverzaci, do Nastavení otevřenou sekci.
Dřív byl zásobník jeden pro celou aplikaci a přepnutí ho srovnalo na kořen.

- **`NavDisplay` je jeden a kreslí zásobník Chatů a nad ním zásobník vybrané sekce**
  („concatenated back stack" z Nav3). Zpět z kořene Nastavení je tím obyčejný pop do Chatů
  i s predictive back, bez vlastního handleru. Ťuknutí na už vybranou sekci ji vrátí na kořen.
- **Položky se dekorují pro KAŽDOU sekci, i tu, která není vidět** (`rememberDecoratedNavEntries`
  pro oba zásobníky pokaždé). Zásobník, který by se zrovna nedekoroval, by pro Nav3 vypadal jako
  odebraný a jeho ViewModely by se zrušily.
- **Dekorátor ViewModelů je nutný** (`rememberViewModelStoreNavEntryDecorator`, v `AppShell` nad
  `NavDisplay`). Bez něj `NavDisplay` dává jen `SaveableStateHolder` a `koinViewModel()` v obrazovce
  bral úložiště Activity — **ViewModel každé otevřené konverzace žil až do konce Activity
  a `onCleared` se při odchodu z chatu nevolal**, přestože na něm visí dopsání konceptu,
  zastavení hlasovky a zahození nahrávky. Teď se ViewModel zruší s odebráním položky ze zásobníku,
  při přepnutí sekce ne (ověřeno logem v `onCleared`). Rodičem je owner Activity: Nav3 pod ním
  drží úložiště pro každou položku, rotaci přežije a s odchodem shellu (odhlášení) je vyčistí.
  Vlastní `ViewModelStoreOwner` jako v mlogu proto není potřeba.
- **Past: `NavDisplay` volá `onBack` jednou ZA KAŽDOU odebíranou položku**
  (`repeat(entries.size - scene.previousEntries.size) { onBack() }`). U dvoupanelové scény
  (seznam + detail) tedy dvakrát. Každé volání musí odpovídat právě jedné položce toho, co se
  kreslí — cokoli jen dopočítaného pro kreslení tu počty rozhodí.
- **Obrazovka píše do zásobníku SVÉ sekce** (`chatsStack`, `settingsStack` v `shellEntryProvider`),
  ne do toho vybraného.
- **Šipka zpět obrazovky odebírá SVOU položku, ne poslední** (`pop(route)` v `AppNavigation.kt`),
  a kořen nikdy. Dřív to byl `removeLastOrNull()` a aplikace padala na „NavDisplay entries cannot
  be empty": obrazovka po odebrání ještě dobíhá odchodovou animací a její tlačítka reagují dál,
  takže druhé ťuknutí na šipku odebralo seznam chatů pod ní. Spolehlivě se to vyvolá
  klávesnicí — Tab dá fokus šipce v hlavičce a mezera ji „ťukne" (`adb shell "input keyevent 61;
  input keyevent 62 62"`); stejně tak `adb shell input text` s mezerou, když pole nemá fokus.
  Pojistka patří tam, kde se odebírá: obrazovka, která v zásobníku už není, nesmí odebrat nic.
- Vybraná sekce přežije zabití procesu (`rememberSaveable` podle názvu `TopLevelDestination`),
  zásobníky taky (`rememberNavBackStack`).

## Seznam chatů

Položky nejsou samostatné karty, ale **skupiny se společným zaoblením** — vzor, kterým M3
Expressive nahrazuje karty per položku. Připnuté mají vlastní skupinu vždycky, nepřečtené jen
když si o to uživatel řekne v Nastavení (`separateUnreadChats`, **výchozí je vypnuto**).

- **Zaoblení nese krajní položka skupiny**: velké nahoře u první, velké dole u poslední, malé
  (4 dp) mezi nimi. S rozestupem 2 dp uvnitř skupiny a 14 dp mezi skupinami z toho vznikne blok.
  Tvary a rozestupy jsou v `core/ui/GroupedList.kt` — týmž jazykem mluví i profil chatu
  a výběr cíle přeposlání, a tři kopie konstant by se rozešly.
- **Vybraná položka je zaoblená celá**, jako skupina sama (dvoupanel, v seznamu chatů i sekcí
  nastavení). Vyčlení se tím z bloku — tvarem, nejen barvou. Sousedé si svá malá nároží nechávají.
- **Zaoblení se animuje.** Skupiny se přeskládávají za běhu — přečtený chat přeskočí
  z nepřečtených mezi ostatní — a tvary jeho sousedů se tím mění. Skoková změna by při
  současném `animateItem()` působila jako chyba vykreslení.
- **Prázdné skupiny se zahazují**, jinak by po nich zbyla mezera. Bez připnutých a bez
  nepřečtených je z celého seznamu zase jeden souvislý blok.
- **Pořadí uvnitř skupiny zůstává od TDLib.** Je to pořadí, na které je uživatel zvyklý
  z ostatních klientů; přeskládávat ho by znamenalo rozhodovat za něj.
- **Důsledek, se kterým se počítá:** přečtení chatu ho přesune ze skupiny nepřečtených dolů mezi
  ostatní. Při návratu z konverzace tedy chat nezůstane tam, kde na něj uživatel klikl. Právě
  tohle je důvod, proč je vyčlenění volitelné a **výchozí vypnuté**: platí se za něj tím, že
  seznam pod prstem uskočí, a vyplatí se to až u hodně chatů.
- **Pravidlo je čistá funkce `chatGroups`** (`ChatGroups.kt`, testy `ChatGroupsTest`), ne
  `private fun` v obrazovce. Hraniční kombinace (samé připnuté, žádné nepřečtené, prázdný
  seznam) se uvnitř `@Composable` neověří a v seznamu se projeví mezerou po prázdné skupině.
- **Mezera mezi skupinami se přidává u první položky skupiny**, ne ve `verticalArrangement` —
  `LazyColumn` o sousedech položky nic neví. Stejný postup jako u rozestupů zpráv v konverzaci.
- **Nad prvním chatem je neviditelná kotva** (`TOP_ANCHOR_KEY`, 1 dp). `LazyColumn` drží pozici
  podle klíče první viditelné položky: bez kotvy to byl první chat, a když nad něj vyskočil jiný
  (nová zpráva), seznam šel za tím původním a nový chat skončil nad horní hranou — ve vršku
  seznamu trčel jen useknutý kus karty. Kotva je první viditelná položka, takže seznam u vršku
  u vršku zůstane. **Nulovou výšku mít nesmí**, to by ji seznam za viditelnou nepovažoval; její
  výška i rozestup za ní se odečítají z horního odsazení, aby první chat neposkočil.

### Složky místo filtrů a FAB

Seznam chatů zužují JEN složky Telegramu v řádku pod pilulkou (viz „Složky chatů"). Dole je
FAB menu „Vytvořit" (`NewChatFabMenu`): přepínací FAB s tužkou a nad ním Nový kanál, Nová
skupina a Nová zpráva.

- **Dřív tu byl plovoucí toolbar s vlastními filtry** (Vše, Nepřečtené, Lidé, Skupiny, Kanály;
  `ChatFilter`), lokálními a kombinovatelnými se složkou. Dělaly ale totéž co doporučené složky
  Telegramu („Nepřečtené", „Osobní"…) — a s nimi přidanými stály v aplikaci dvě řady téhož nad
  sebou. Uživatel to vyhodnotil jako duplicitu a zrušily se. Co se tím ztratilo: kombinace
  „složka × filtr" (nepřečtené uvnitř Práce) a filtry pro toho, kdo složky nemá. Náhrada je
  „+" na konci řádku složek, který doporučené složky nabídne na jedno ťuknutí.
- **„+" otevře sheet** (`FolderSuggestionsSheet`, stav `ChatsViewModel.folderSuggestions`):
  doporučené složky (`getRecommendedChatFolders`, čtou se při každém otevření), pod nimi „Nová
  složka" a „Spravovat složky" do Nastavení → Obecné → Složky chatů
  (`AppNavigation.openChatFolders`, zpět po krocích jako u proxy). Přidaná doporučená složka
  se rovnou VYBERE — kdo si přidá „Nepřečtené", chce je vidět.
- **Sheet říká, že se složka synchronizuje** s ostatními klienty. U filtru, který dřív žil jen
  v Plume, by to nikdo nečekal.
- **Řádek složek se kreslí i bez složek** („Všechny chaty" a „+"), jinak by k doporučeným
  nevedla z hlavního seznamu žádná cesta. Samotné „Všechny chaty" má plný tvar, ne půlku
  spojené skupiny (`ToggleButtonDefaults.shapes()`).
- **„+" je mimo spojenou skupinu** (`OutlinedIconButton` s odstupem): je to akce, ne volba;
  ve skupině by se tvářil jako další složka.
- **Prázdná složka má `IntroEmptyState` s ikonou složky** („Nic tu není") — chaty jsou, jen
  ne tyhle (typicky „Nepřečtené" se vším přečteným). Prázdný účet má dál holý text.
- **FAB a snackbar nejsou ve slotech Scaffoldu** a stojí nad klávesnicí (`imePadding` za
  `consumeWindowInsets` spodního paddingu Scaffoldu): Scaffold dává do paddingu jen systémové
  lišty, takže by je vysunutá klávesnice zakryla. Seznam má dole `FAB_CLEARANCE`.
- **FAB menu M3 Expressive, ne rozšířený FAB** (`FloatingActionButtonMenu`
  + `ToggleFloatingActionButton`). Dřív byl FAB „Nová zpráva", který se při scrollu sbalil na
  ikonu; Nová skupina a Nový kanál byly jen řádky na obrazovce Nové zprávy (zůstaly tam). Popisky
  nesou položky, přepínač je jen ikona (tužka → ✕, zaoblený čtverec → kruh) — tak to M3 u FAB
  menu chce, takže sbalování při scrollu odpadlo. Nová zpráva je poslední, u palce.
- **Pod otevřeným menu je závoj** (`FabMenuScrim`, `surface` s průhledností): ťuknutí vedle
  menu zavře a nepropadne do seznamu (otevřelo by chat). Kreslí se DVAKRÁT — přes seznam a přes
  hlavičku, protože Scaffold kreslí `topBar` nad obsahem a ťuknutí na pilulku by jinak otevřelo
  hledání s menu otevřeným pod ním. Zpět menu zavře (`NavigationBackHandler`), teprve další zpět
  odchází z aplikace. Položka napřed menu zavře, pak naviguje — po návratu má být zavřené.
- **Popisek přepínače se mění se stavem** („Vytvořit" / „Zavřít") a čtečka ho čte PŘED
  položkami (`traversalIndex = -1`), jako v ukázce M3; z poslední položky menu zavře vlastní akce.
- **Vybraná složka se v řádku vždycky ukáže** (`bringIntoViewRequester` u vybraného tlačítka):
  přidaná doporučená složka stojí na konci řádku a vybrala by se za okrajem obrazovky.
- **Server doporučené složky vracet nemusí** — účtu, který už má „Unread" i „Personal", nevrátil
  žádnou. Sheet se pak ověřuje sondou (podstrčená doporučená složka v `openFolderSuggestions`;
  necommituje se), založení jde skutečným `createChatFolder`.
- **Ověřeno na emulátoru** (anglicky): FAB menu — otevření, zavření ťuknutím do seznamu i na
  hlavičku a zpět, všechny tři položky vedou na svou obrazovku a zpět se vrací se zavřeným menu.
- **Ověřeno na emulátoru** (česky, světlý i tmavý motiv): FAB se sbalením, řádek s „+", sheet
  bez doporučených i se sondou, přidání složky (vybraná a dojetá v řádku), prázdná složka
  s ikonou, „Nová složka" do editoru a „Spravovat složky" do seznamu složek, zpět po krocích.
  Testovací složka se pak smazala. **iOS jen přeložené.**

### Složky chatů

Složky Telegramu (`ChatListFolder`) jsou řádek přepínacích tlačítek pod pilulkou hledání:
„Všechny chaty · Práce 3 · Rodina…" (`ChatFolderRow`, pravidla `folderTabs` a `selectedFolderId`
v `ChatFolderTabs.kt`, testy `ChatFolderTabsTest`). Bez složek je v řádku jen „Všechny chaty"
a „+" (viz „Složky místo filtrů a FAB").

- **Složka je další `ChatFolder` (`Custom(id)`), ne nový druh seznamu.** Repository drží pozice
  chatu ve VŠECH seznamech, které TDLib hlásí (`positions`), takže seznam složky se skládá
  stejným `publish` jako hlavní a archiv. Seznamy se ale publikují jen pro složky, o které si
  někdo řekl (`lists` roste s `chats(folder)`) — skládat seznam každé složky při každém updatu
  by byla práce pro nic.
- **Složka se načítá až při výběru** (`loadMore(Custom)`), jako archiv. Do té doby je `Loading`;
  pozice chatů ve složce TDLib pošle teprve po `loadChats` nad ní.
- **Archivovaný chat může být ve složce** (`excludeArchived = false`), takže archivace se už
  nerozhoduje podle složky, ze které položka přišla, ale podle `ChatSummary.isArchived`
  (pozice v archivu). `folder` zůstává jen pro připínání — připnutí patří složce.
- **Uložené zprávy ve složce JSOU vidět i nepřipnuté** (`hiddenSavedMessagesChatId`). Do složky
  je uživatel dal sám; v hlavním seznamu zůstávají schované jako dřív.
- **Vybraná složka žije jako filtr**: `SavedStateHandle`, ne nastavení. Návrat z konverzace
  i zabití procesu ji zachovají, nové spuštění začne na všech chatech. Smazaná složka (třeba
  na jiném zařízení) spadne na celý seznam, ale výběr se NEPŘEPISUJE — po startu přichází
  `UpdateChatFolders` až po prvním složení a uložená volba by se jinak ztratila.
- **Řádek odjíždí spolu s pilulkou.** Scroll behavior nenese `AppBarWithSearch`, ale sloupec
  kolem pilulky i řádku (`Modifier.searchBarScrollBehavior()` je veřejný); bar sám ho nedostane,
  jinak by odjel jen on a řádek zůstal viset nahoře.
- **Jen ťuknutím, ne tahem do strany.** Vodorovný tah po seznamu patří archivaci.
- **„Všechny chaty" stojí, kam ho uživatel v Telegramu přesunul** (`mainChatListPosition`,
  bez Premium vždycky první); pozice za koncem (složka mezitím zmizela) jde na konec.
- **Číslo u složky je počet nepřečtených NEZTLUMENÝCH chatů** (`UpdateUnreadChatCount` pro
  `ChatListFolder`), jako odznak archivu. Složku plnou ztlumených kanálů by jinak číslo
  zdobilo napořád. Chodí jen se zapnutou databází zpráv.
- **Název složky může nést vlastní emoji** (`ChatFolderName.text` je `FormattedText`) — kreslí
  se jako v náhledu seznamu chatů (`appendWithCustomEmoji`).
- **Ověřeno na emulátoru** dvěma složkami založenými sondou (`createChatFolder`; jedna s vybranými
  chaty včetně Uložených zpráv, druhá „všichni boti"): řádek, přepnutí, číslo u složky, obnova
  po `am kill`, odjetí s pilulkou. **iOS jen přeložené.**

#### Správa složek

Nastavení → Obecné → Složky chatů (`ChatFoldersScreen`, `ChatFolderEditScreen`, hranice
`ChatFolderRepository` / `TdlChatFolderRepository`, pravidla konceptu `ChatFolderDraft`, testy
`ChatFolderDraftTest`) a „Přidat do složky" v nabídce nad chatem (`folderChoices`).

- **Seznam a pořadí složek jsou živé z `ChatRepository.chatFolders`**, správa jen zapisuje.
  Po založení, úpravě i smazání přijde `UpdateChatFolders` sám; nic se nepřepisuje lokálně.
- **Složka se zapisuje CELÁ** (`editChatFolder` bere celý `ChatFolder`), takže co aplikace
  nenabízí — ikona, barva, sdílení, připnuté chaty, vlastní emoji v názvu — se přenáší
  z načtené složky (`ChatFolderDraft`). Vlastní emoji patří textu, ve kterém přišly; změněný
  název je zahodí (`withTitle`), jinak by ukazovaly do jiných znaků.
- **Prázdnou složku server odmítne**: musí mít aspoň jeden vybraný chat nebo druh chatů,
  a název 1–12 znaků. Hlídá to `problem()` a Uložit ukáže důvod hned, ne chybu od serveru.
- **Vybraný chat nemůže být zároveň vynechaný** — `withChosen` / `withExcluded` ho z druhého
  seznamu vyřadí; server by jinak celou úpravu odmítl.
- **Odebrat chat ze složky nestačí z vybraných.** Složka „všichni boti" drží bota DRUHEM, takže
  `removeChat` ho navíc vynechá (`withoutChat(coveredByKind)`); jinak by zaškrtávátko nad chatem
  zhaslo a chat ve složce zůstal. Přidání (`addChatToList(ChatListFolder)`) vynechání zruší
  samo — ověřeno: Notifikátor ze „Boti" ven a zpátky.
- **„Přidat do složky" nabízí složky, ve kterých chat je, a ty, kam jde přidat**
  (`getChatListsToAddChat`), zaškrtávátkem a přímo v nabídce (rozbalení jako doby ztlumení).
  Členství se čte až při otevření nabídky, přepnutí se ukáže hned a při chybě vrátí.
- **Pořadí se mění tažením za úchyt** (`DragReorder`) a server dostane nové pořadí až po
  PUŠTĚNÍ (`snapshotFlow { draggedKey }` → `reorderChatFolders`), ne po každé výměně. „Všechny
  chaty" úchyt nemají — přesunout je jde jen s Premium.
- **Doporučené složky** (`getRecommendedChatFolders`) se čtou znovu po každé změně složek;
  přidaná doporučená z nabídky zmizí. Server je nevrací vždycky: při prvním otevření na emulátoru
  přišel prázdný seznam bez chyby, po smazání jiné složky „Nepřečtené" a „Osobní".
- **Výběr chatů do složky je `ChatPicker` přes celou obrazovku nad editorem**, ne routa: výběr
  patří rozdělanému konceptu, který by navigace musela přenášet. „Hotovo (0)" jde potvrdit
  i prázdné (`confirmWhenEmpty`) — tak se vybrané chaty odebírají všechny naráz.
- **Ikona složky je jméno od TDLib** (`ChatFolderIcon.name`: „Bots", „Cat", „Work"… — pevný seznam
  z dokumentace) a kreslí se Material Symbols (`CHAT_FOLDER_ICONS`, `folderIcon`), ne grafikou
  Telegramu. Neznámé jméno z novější verze je obecná složka a při úpravě se přenese dál.
- **Bez vybrané ikony ji volí Telegram podle obsahu** (`getChatFolderDefaultIconName`, lokální)
  a editor ukazuje tu, kterou by zvolil, přepočítanou s každou změnou konceptu: zapnutí „Boti" ji
  samo změní na robota. **S vybranými chaty je výchozí „Custom"**, tedy obecná složka — i u
  složky „všichni boti" s jedním chatem navíc. Není to chyba, tak to TDLib počítá.
- **`ChatFolderInfo.icon` v `UpdateChatFolders` je už ta výsledná** (zvolená, jinak výchozí),
  takže seznam složek si nic nedopočítává. Doporučené složky ikonu nemají a dopočítá se jim jen
  pro zobrazení (`RecommendedChatFolder.iconName`) — do konceptu nejde, přidaná složka si ji
  dál volí podle obsahu.
- **Výběr je sheet s „Automaticky" a mřížkou**, ikona stojí v poli s názvem jako v oficiálních
  klientech. Řádek složek nad seznamem chatů ikony nekreslí — je to text, jako u oficiálního
  klienta na Androidu, a ikona vedle názvu by z tlačítek udělala dvojnásobnou šířku.
- **Ověřeno na emulátoru** (anglicky, složený Fold): založení složky se dvěma chaty, přeřazení
  tažením (server pořadí převzal, řádek nad seznamem ho ukázal), úprava (druh Boti, odebraný
  chat) a smazání s potvrzením, doporučená „Unread" přidaná a zase smazaná, „Přidat do složky"
  oběma směry. Ikona: výběr tlapky, uložení (server ji převzal, seznam ji ukázal), návrat na
  „Automaticky", robot u nové složky s Boty a ikony doporučených. „Chaty ze složek" v nastavení
  archivu se ukazuje se skutečnou hodnotou účtu; **zápis ověřený není** (mění nastavení účtu).
  **iOS jen přeložené.**

#### Sdílené složky

Odkaz `t.me/addlist/…` ve zprávě otevře náhled složky (`FolderInviteSheet`, `InternalLink.FolderInvite`
z `checkChatFolderInviteLink`), v editoru vlastní složky je sekce Sdílení s odkazy
(`ChatFolderRepository.links` / `createLink` / `deleteLink`).

- **Nic se nepřidá bez potvrzení**, stejně jako u pozvánky do chatu: přidání složky znamená
  připojit se k jejím chatům. Chaty k připojení jsou zaškrtnuté všechny a jdou odškrtnout.
  Novou složku jde přidat i bez nich, když v některých chatech uživatel už je; u složky, kterou
  už má, se přidávají jen vybrané nové chaty, a když žádné nejsou, tlačítko není.
- **Sdílet jde jen složku z vybraných chatů** (`ChatFolderDraft.canBeShared`): TDLib odkaz na
  složku s druhy chatů nebo vynechanými odmítne. Editor to řekne pod tlačítkem, místo aby
  odkaz skončil chybou, a **sdílená složka druhy ani vynechané nenabízí** — uložení by server
  odmítl.
- **Odkaz nese chaty z `getChatsForChatFolderInviteLink`** — jen skupiny a kanály, kam smí
  uživatel zvát; soukromé chaty a boti v odkazu nejsou („1 chat" u složky se třemi). Bez nich
  odkaz vytvořit nejde (`NothingToShareException`, vlastní věta).
- **Odkaz se dělá z ULOŽENÉ složky**, takže s rozepsanými změnami tlačítko zašedne a řekne
  proč. Nový odkaz se rovnou zkopíruje, jako pozvánka do chatu.
- **Sdílenou se složka stane prvním odkazem a přestane být smazáním posledního** — a to
  potvrdí až `UpdateChatFolders`, ne odpověď na smazání. Proto nese `ChatFolderTab` příznak
  `isShareable`: bez něj `chatFolders` (StateFlow) update odfiltroval jako beze změny a editor
  dál schovával druhy chatů u složky, která sdílená už nebyla.
- **Po smazání posledního odkazu vrátí `getChatFolderInviteLinks` chvíli `FILTER_ID_INVALID`**,
  proto smazaný odkaz zmizí ze seznamu hned, ať čtení dopadne jakkoli.
- **Nové chaty v přidané složce** (autor do ní doplnil chaty) jsou karta nad seznamem chatů
  vybrané složky (`NewFolderChatsCard`, `ChatsViewModel.newFolderChats`), ne dialog: sheet se
  chaty k připojení se otevře až „Zobrazit". „Skrýt" pošle `processChatFolderNewChats`
  s prázdným seznamem — server tím nabídku zapomene. Ptá se jen u složky, která je sdílená
  a NENÍ moje (`isShareable && !hasMyInviteLinks`; `ChatFolderTab.hasMyInviteLinks` kvůli tomu).
- **`getChatFolderNewChats` se nesmí volat při každém výběru složky.** Dokumentace dovoluje
  nejvýš jednou za `chat_folder_new_chats_update_period` sekund (výchozí hodina), proto má
  `TdlChatFolderRepository` vlastní keš na složku; po vyřízení je prázdná.
- **Jeden sheet a jeden blok řádků pro „které z těchhle chatů"** (`FolderChatsSheet`,
  `CheckableChatRows` v `core/ui`): náhled z odkazu, nové chaty, chaty v odkazu i chaty
  k opuštění. Dřív byl sheet jen v `features/conversation` a seznam chatů by na konverzaci
  závisel.
- **Smazání přidané složky se ptá, které chaty opustit** (`getChatFolderChatsToLeave`, pak
  `deleteChatFolder(leaveChatIds)`), zaškrtnuté všechny, jako oficiální klienti. Dialog se
  otevře až s odpovědí — kdyby během čtení povyrostl o seznam, vypadalo by to jako chyba.
  U vlastní složky je seznam prázdný a dialog je stejný jako dřív.
- **Odkaz jde upravit: název a chaty** (`FolderLinkSheet`, `editChatFolderInviteLink`).
  Nabízí se chaty z `getChatsForChatFolderInviteLink` PLUS ty, které odkaz už nese — jinak by
  chat, který sdílet přestal jít, nešlo z odkazu odebrat. Bez chatů server odkaz odmítne, Uložit
  zašedne s větou. Ťuknutí na odkaz otevře sheet, sdílení zůstalo ikonou v řádku (nejčastější
  akce bez kroku navíc).
- **Potvrzení v sheetu hlásí sheet sám** (kopírování fajfkou, chyba uložení větou nad Uložit):
  snackbar obrazovky je pod modálním sheetem schovaný.
- **Načtení náhledu trvá pár vteřin** (dotaz na server). Kdo ťukne podruhé, trefí závoj sheetu
  a zavře ho — při ověřování přes `adb` se po ťuknutí čeká.
- **Ověřeno na emulátoru** (anglicky i česky): odkaz na složku „Plume test" (s testovací
  skupinou), sdílení schovalo druhy a vynechané, odkaz poslaný botem otevřel náhled „složku už
  máš"; smazání odkazu vrátilo složku na nesdílenou i v otevřeném editoru; u složky s druhem Boti
  vysvětlení. Náhled nové složky s chatem k připojení **jen sondou** (podstrčené `folderId = 0`
  a chaty), odškrtnutí chatu. **Skutečné přidání složky z cizího odkazu ověřené NENÍ** — chtělo
  by to druhý účet, nebo se připojit ke skutečným cizím chatům. Úprava odkazu ověřená naostro
  (přejmenování, odškrtnutí jediného chatu s větou, uložení, smazání ze sheetu). Dialog
  s chaty k opuštění a karta nových chatů **jen sondou** (podstrčené chaty u vlastní složky,
  nic se nepotvrdilo) — přidaná cizí složka na testovacím účtu není. **iOS jen přeložené.**

### Ověření a štítek podvodu

Za jménem chatu je fajfka ověřeného účtu, nebo štítek „PODVOD" / „FALEŠNÝ" (`VerificationBadge`
v `core/ui`, doména `Verification`, pravidlo `verificationOf`, testy `VerificationTest`) —
v seznamu chatů, v hlavičce konverzace a v profilu, kde u podvodu a falešného stojí i věta, co
to znamená.

- **Podvod a falešný mají přednost před ověřením.** Ověření by uklidnilo přesně tam, kde má
  uživatel zbystřit.
- **Podvod je slovo, ne ikona**, v chybové barvě a s obrysem místo plné plochy. Vykřičník by se
  četl jako „nepovedlo se"; plná chybová plocha by v řádku přebila všechno ostatní.
- **`Chat` ověření nenese** — je na `User` (soukromý a tajný chat) a `Supergroup` (skupina
  i kanál), základní skupina ho nemá. Dočítá se lokálním `getUser` / `getSupergroup`
  (`TdlClient.verificationOf`, jedno místo pro seznam i hlavičku) a živě ho mění `UpdateUser`
  a `UpdateSupergroup`. Supergroupa se na chat převádí konvencí TDLib (`supergroupChatId`).
- **Přežívá přepsání chatu** (`keepingLiveState`), stejně jako dostupnost — `UpdateNewChat` by
  ho jinak smazal.
- **Jméno v seznamu je ve vlastním řádku s váhou, uvnitř s váhou BEZ vyplnění.** První verze
  dala jménu `weight(1f, fill = false)` a za štítek mezeru s `weight(1f)` — obě váhy si místo
  rozdělily napůl, takže se každé delší jméno zkrátilo v půlce řádku a čas nesl skok. Totéž
  čeká každý prvek, který má stát HNED za textem proměnlivé délky a přitom nechat zbytek
  řádku jiným.
- **Značka je i u jména odesílatele ve skupině** (`ChatMessage.senderVerification`), hned za
  jménem, před bleskem boostů a štítkem člena. Podvodník ve skupině by jinak vypadal jako
  kdokoli jiný. Nese ji `SenderInfo.verification` z keše odesílatelů (`resolveSender`:
  `verificationStatus` uživatele, u kanálu jako odesílatele `verificationOf(chat)`), takže
  dotaz navíc nestojí. Fajfka má akcent ověřený proti ploše (`accentOn`), menší než v seznamu
  (14 dp, jméno je `labelLarge`). Ověřeno sondou (zprávy od bota s `#over1–3` dostaly jméno
  a značku; necommituje se) — skutečný podvodník ve skupině na testovacím účtu není.
- **Ověřit účet může i bot** (`VerificationStatus.botVerificationIconCustomEmojiId`): značka
  organizace jako vlastní emoji, v seznamu, hlavičce, u jména odesílatele i v profilu, VEDLE
  fajfky Telegramu (`Verification.Verified(official, botIconEmojiId)` — z výčtu je proto
  uzavřená hierarchie). U podvodu a falešného účtu se nekreslí, ze stejného důvodu jako fajfka.
- **V profilu jsou to DVA řádky**: fajfka s „Ověřeno Telegramem" a pod ní značka od bota
  s důvodem ověření (`UserFullInfo` / `SupergroupFullInfo.botVerification.customDescription`),
  bez důvodu „Ověřil @bot". Na jednom řádku stála značka od bota vedle „Ověřeno Telegramem",
  jako by ji udělil Telegram.
- **Ověřovací bot musí být schválený Telegramem** — testovací bot značku přidělit nemůže, takže
  se ověřuje sondou (`verificationOf(chat)` a profil pro chat s testovacím botem, skutečné id
  vlastního emoji z `getStickerSet` sady `StatusEmoji`). Necommituje se.
- **Ověřeno na emulátoru**: fajfka u chatu Telegram v seznamu; štítek podvodu v seznamu,
  hlavičce i profilu **jen podstrčeným stavem** (sonda v `verificationOf`, `UpdateUser`
  a profilu pro testovacího bota — chat s botem má id BOTA, číslo před dvojtečkou v tokenu,
  ne `chat_id` z Bot API). Skutečný účet označený jako podvod testovací účet nezná.

### Tvar avataru podle druhu chatu

Člověk kruh, skupina čtyřlaločná sušenka (`MaterialShapes.Cookie4Sided`), kanál zaoblený čtverec
(`MaterialShapes.Square`) — `ChatKind.avatarShape()` v `core/ui/Avatar.kt`. Druh se pozná dřív,
než se dočte název.

- **Statický tvar nese DRUH, morf nese STAV** (`ShapeMorphs`). Avatary se nehýbou a neberou
  tvary, které už něco znamenají (šesti- a dvanáctistranná sušenka, SoftBurst).
- **Tvar musí sedět všude, kam avatar letí sdíleným prvkem**: seznam → hlavička konverzace →
  profil. Jinak by uprostřed letu přeskočil. Hlavička ho odvozuje z `isChannel`/`isGroup`,
  profil z `ProfileKind`. Výchozí `Avatar(shape = CircleShape)` je pro odesílatele, reakce apod.
  — tam jde vždycky o člověka.

### Hlavička: vyhledávací pilulka

Seznam chatů nemá nadpis, ale `AppBarWithSearch` (`ChatSearchBar.kt`): pilulka „Hledat
v chatech" přes celou šířku, na jejím konci fotka účtu s menu (Uložené zprávy, Archiv,
Nastavení — vzor Gmailu a Google Messages; dřív tu bylo ⋮ vedle pilulky). Po ťuknutí se rozbalí
`ExpandedFullScreenSearchBar` s výsledky.

- **Fotka účtu je vidět hned, i než se účet načte** (ikona člověka). Tlačítko, které se objeví
  až později, by do té doby nešlo otevřít — a na telefonu je to jediná cesta do Nastavení.
  Fotka je `AccountInfo.photoFileId` (`getMe`), načítá ji `ChatsViewModel` jen pro hlavní seznam.

- **Nahradila `LargeFlexibleTopAppBar`, protože ten byl hezký jen rozbalený.** Sbalený přešel na
  `scrolledContainerColor` (`surfaceContainer`), tedy pruh přes celou šířku v roli, která jinde
  v seznamu není. Na rozloženém Foldu navíc stál vedle 64dp hlavičky konverzace bar jiné výšky
  i barvy. Pilulka je zaoblený blok jako skupiny chatů a bar je vysoký přesně 64 dp —
  a nemá mezistav, jen je, nebo není.
- **Při scrollu dolů bar odjede** (`enterAlwaysSearchBarScrollBehavior`), nahoru se vrátí.
  Odjetý bar odkryje stavovou lištu, pod kterou by chaty jezdily bez pozadí — proto seznam
  kreslí pod lištou závoj v barvě stránky (`windowInsetsTopHeight(statusBars)`), nad obsahem,
  ale pod `topBar`. **Závoj je přechod do průhledna, ne plná barva:** s plným pásem končily
  chaty o výšku lišty pod okrajem ostrou hranou, jako useknuté.
- **Výpadek spojení bar přivolá zpátky** (`scrollOffset` animovaný na 0). Jinak by odjetý bar
  schoval jediné vysvětlení, proč nic nechodí.
- **Sbalený stav barvu nemění** (`scrolledAppBarContainerColor = surface`). Tónovaný pruh přes
  celou šířku byl přesně to, co vadilo na velkém baru.
- **Plochu baru dává i `Modifier.background(surface)`.** `appBarContainerColor` bez scroll
  behavior v material3 1.5.0-alpha22 nenakreslil nic a chaty pod barem prosvítaly.
- **Stav spojení nese fotka účtu a nápověda pilulky** (`ConnectionIndicator` v `core/ui`,
  sdílený s hlavičkou konverzace), ne nadpis ani vlastní prvek. Skoro celou dobu není vidět nic; „Chaty"
  v nadpisu jen opakovalo navigaci. Dřív to byla tonální pilulka s kolečkem na konci hledání —
  ukusovala místo nápovědě a vedle fotky působila jako cizí prvek. V rozbaleném hledání se stav
  neukazuje.
  - **Fotka naroste (30 → 38 dp), zvlní se, po jejím okraji vyjede vlnitý prstenec a obojí se
    točí**; po obnovení spojení je fotka zase malý kruh. Prstenec je OBRYS fotky (tah leží napůl
    přes její okraj), ne rám kolem ní. Dvě slepé uličky: `CircularWavyProgressIndicator` kolem
    kulaté fotky (kruh v zubatém rámu nepůsobil jako celek) a zvlněná zmenšená fotka uvnitř
    prstence — mezera mezi nimi vypadala jako díra. Pilulku zvětšení nerozšíří: slot tlačítka
    má pevných 48 dp a 38 dp se vejde i pod 40dp ořez `IconButton`.
  - **Točí se obrys, fotka ne.** Obrys je ořez ve vrstvě (`graphicsLayer { shape; clip }`),
    obsah pod ním stojí; točit celou fotku by točilo i tvář. Zvlnění, zvětšení fotky i prstenec
    jdou z JEDNOHO průběhu morfu (`morphSpring`), úhel se čte až při
    kreslení — točení nic neskládá. `MorphShape` na to nestačí: otáčí jen spolu s průběhem.
  - **Prstenec a tečka archivu jsou mimo `IconButton`**, ve vrstvách nad ním: tlačítko ořezává
    obsah na 40dp kruh. Tečka je nad prstencem (jinak by ji přeškrtl) a zůstává v rohu
    NEZVĚTŠENÉ fotky, takže na prstenci sedí jako odznak; zvětšovat ji s fotkou by ji vystrčilo
    z ořezu. Zvětšuje se jen `Avatar`, ne obal s tečkou.
  - **Prstenec říká, ŽE je problém, nápověda CO.** Samotný tvar by nerozlišil „Čekání na síť"
    (uživatel může něco udělat) od „Připojování" (stačí počkat).
  - **Čekání na síť se točí POMALU (otáčka za 20 s) a prstenec je v `outline`**, připojování
    a aktualizace za 9 s v `primary`. Úplné zastavení (první návrh: „bez sítě se na nic nečeká,
    rotace by lhala") uživatel na emulátoru odmítl — stojící vlnka působila jako zamrzlá.
    Rychlost se animuje ve stupních za vteřinu a úhel přibývá po snímcích (`withFrameNanos`),
    takže přechod mezi stavy neškubne; smyčka běží jen při výpadku. Kontrast proti pilulce v tmavém motivu: `outline` 3,6 : 1,
    `primary` 9,7 : 1 (práh pro netextové prvky je 3 : 1).
  - **Nápověda se mění fade through, ne `Crossfade`.** Dva texty napůl průhledné přes sebe
    („Čekání na síť" přes „Připojování") se nedají přečíst — tatáž úvaha jako u přechodů
    obrazovek, i s jejich čísly (90 / 210 ms).
  - **Výpadek se ukáže až po 600 ms, návrat hned** (`rememberShownConnection`). Po každém návratu
    z pozadí TDLib na zlomek vteřiny hlásí `Updating` a fotka by se při každém otevření aplikace
    zavlnila. Na emulátoru se tím připojování po vypnutí letového režimu neukáže vůbec.
  - **Vzhled se ověřuje podstrčeným stavem, i bez přihlášení**: v `App.kt` dočasně
    `targetState = true` (shell se složí i nepřihlášený), v `ChatListScreen` pevný `AccountInfo`,
    `ArchiveUnread(2, 1)` (tečka) a `produceState`, který střídá čekání na síť → připojování → `null` po 4 s. Tak jde použít
    čerstvý emulátor, na kterém Plume přihlášená není. Necommituje se.
  - **Čtečce obrazovky stav říká popis tlačítka účtu** („Účet, Čekání na síť…").
- **Hledá se ve všech známých chatech**, ne v načteném seznamu — viz „Hledání chatů" níž.

### Hledání chatů

`ChatSearch` (`features/chats`) — sdílí ho hlavička seznamu chatů, přeposílání i sdílení.

- **`searchChats`, ne filtr nad seznamem.** Filtr nad `chats()` nenašel archiv ani chaty mimo
  složky (kontakty bez konverzace, opuštěné skupiny, které TDLib zná). Nejdřív lokálně
  (`searchChats`, hned), pak server (`searchChatsOnServer`), serverové výsledky se připojí ZA
  lokální (`mergeChatResults`) — co uživatel už vidí, se pod prstem nepřeskládá.
- **`transformLatest` s krátkou pauzou (150 ms).** Rozepsaný dotaz se dalším znakem zahodí,
  takže pomalá odpověď serveru na „ja" nepřepíše výsledky pro „jana".
- **Výsledky se ukazují jen k dotazu, na který TDLib odpovídal** (`ChatSearchState.query`).
  V rozbaleném hledání se mezitím točí indikátor; ve výběru pro přeposílání zůstávají předchozí
  výsledky, jinak by výběr při každém znaku problikl načítáním (`pickerChats`, testy
  `ChatSearchTest`).
- **Pod prázdným dotazem jsou návrhy**: nejčastější lidé (`getTopChats(TopChatCategoryUsers)`)
  a historie hledání. Historii vede TDLib — `searchChats("")` ji vrací, ťuknutí na výsledek ji
  zapisuje (`addRecentlyFoundChat`), „Vymazat" ji smaže. `searchRecentlyFoundChats` prázdný
  dotaz nebere. Návrhy se načítají, jen když je hledání rozbalené (`setSuggestionsWanted`).
- **`getTopChats` vrací chybu, když si to uživatel v Telegramu vypnul** — to je prázdný řádek,
  ne selhání.
- **Uložené zprávy TDLib najde pod jménem účtu**, takže se z výsledků vyřazují a hledají podle
  přeloženého názvu. V historii a mezi nejčastějšími je kreslí `chatTitle` / `isSavedMessages`.
- **Archivovaný výsledek má podtitul „V archivu".** V hlavním seznamu není vidět, a bez podtitulu
  by chat vypadal, že se odněkud vynořil. Složku doplní repository podle pozice
  (`summariesFor`); chat, který repository nezná, si dočte (`getChat`) a uloží.
- **Po výběru z hledání dojede seznam na vybraný chat** (`scrollToChat` v `ChatListContent`),
  jinak by na široké ploše vybraný chat vlevo nebyl vidět. Index se počítá ze skupin (kotva
  + hlavička + chaty) — `LazyListState` klíč položky mimo obrazovku na index nepřevede. Chat,
  který je celý vidět, se nehýbe. Požadavek je `rememberSaveable`: na telefonu konverzace
  seznam překryje a posun proběhne, až se na seznam uživatel vrátí.
- **V hlavním hledání jsou pod chaty ještě sekce Globálně a Zprávy** (`ChatSearch(everywhere =
  true)`; přeposílání a sdílení hledají jen chaty). Globálně je `searchPublicChats` bez chatů,
  které jsou už mezi výsledky (`globalOnly` — server vynechává jen chaty ze seznamu, ne všechny
  známé). Zprávy jsou `searchMessages(chatList = null)` přes hlavní seznam i archiv, stránkované
  `nextOffset` (prázdný = konec), duplicity na hranici stránek zahazuje `mergeFoundMessages`.
- **Filtry hledání zpráv jsou nad výsledky** (`MessageSearchFilters`, `MessageSearchFilter`):
  druh chatu (Soukromé / Skupiny / Kanály) a období (týden, měsíc, rok, vlastní rozsah). Filtruje
  **server** (`searchMessages` s `chatTypeFilter`, `minDate`/`maxDate`, převod `dateRange`, testy
  `MessageSearchFilterTest`) — filtr nad načtenou stránkou by z padesáti výsledků nechal pár
  a zbytek dotahoval naprázdno. Druh chatu zúží lokálně i sekce chatů; s obdobím se sekce chatů
  schovají, chaty datum nemají.
- **Změna jen filtru nehledá chaty znovu** — seznam by se při každém ťuknutí na čip přeskládal;
  zopakují se jen zprávy. Filtr se nuluje se sbalením hledání, jako dotaz.
- **Druh chatu je connected button group** (`ToggleButton` se spojenými tvary, vybraný plný
  s fajfkou, jako záložky profilu); druhé ťuknutí výběr zruší — filtr smí být prázdný.
  **Období je split button**: levá část období zapne a vypne a ukazuje které (poslední vybrané
  si pamatuje), šipka otevře Expressive menu se zaškrtnutou volbou a vlastním rozsahem. „Kdykoli"
  v nabídce není — to je vypnutí levé části. Nejdřív to byly `FilterChip`y; uživatel chtěl
  Expressive komponenty.
- **Na tlačítku krátký popisek** („Týden"), plný je v nabídce, a tlačítka mají užší odsazení
  (12 dp). S „Poslední týden" a výchozím odsazením se řádek v češtině nevešel a šipka nabídky
  skončila za okrajem — ověřuje se v češtině s vybraným druhem i obdobím, to je nejširší stav.
- **Výběr dat vrací půlnoc v UTC** — den se z ní vezme v UTC a začátek hledání je jeho půlnoc
  v MÍSTNÍM pásmu; konec rozsahu je konec dne. Budoucí dny se nenabízejí.
- **Ověřovat přes `adb` opatrně**: klávesa Esc (`keyevent 111`) hledání zavře a další ťuknutí
  trefí seznam chatů — otevřený chat se tím označí jako přečtený. Klávesnice se schovává gestem
  zpět, jen když je opravdu vidět (`dumpsys input_method | grep mInputShown`).
- **Chaty a Globálně jsou sbalené na čtyři řádky** se „Zobrazit vše" — u krátkého dotazu jich
  jsou desítky a zprávy by skončily daleko dole.
- **Nadpis sekce chatů je vždycky, když jsou chaty.** Dokud se ukazoval až s dalšími sekcemi,
  vložil se nad už vykreslené chaty a `LazyColumn`, který drží pozici podle první viditelné
  položky, ho odsunul nad horní hranu — nebyl vidět nikdy. Totéž čeká každou položku vkládanou
  nad obsah, na který se uživatel už dívá.
- **TDLib hledá bez ohledu na diakritiku, zvýraznění musí taky** (`highlightRanges`
  s `foldDiacritics`, znak za znak, takže pozice sedí na původní text). „obslehl" najde
  „Obšlehl"; se zvýrazněním jen přes `ignoreCase` by nalezená zpráva neměla zvýrazněné nic.
  Tabulka diakritiky je v escape sekvencích — `verifyNoHardcodedText` hlídá diakritiku
  v literálech a oslabovat ho kvůli datům nestojí za to.
- **U nalezené zprávy je, kdo ji napsal** („Jana: …", u vlastní „Vy: …";
  `FoundMessage.senderName`). Jméno jen ve SKUPINĚ — v soukromém chatu a kanálu ho říká název
  chatu; anonymní správce (odesílatel je skupina sama) jméno nemá. Dohledává se lokálně
  (`getUser` / `getChat`) při převodu stránky. Zvýraznění dotazu se posouvá o délku prefixu.
- **`adb input text` do pilulky hledání na zatíženém emulátoru ZTRÁCÍ první znaky**
  („kotlin" → „tlin"). Psát po znaku s pauzou, a před ťuknutím na výsledek ověřit dotaz dumpem.
- **Ukázka zprávy začíná kousek před nalezeným slovem** (`snippetAround`, „…" na začátku).
  Ve dvou řádcích by slovo z konce dlouhé zprávy bylo za výpustkou a řádek by nevysvětlil,
  proč se našel. Slovo jen v adrese skrytého odkazu vidět nebude — text ji nenese.
- **Vlastní emoji jsou v ukázce obrázkem**, jako v náhledu seznamu chatů (`FoundMessage.entities`
  z `previewEntities`, `snippetOf`, testy `ChatSearchTest`). Offsety se posouvají o uříznutý
  začátek a výpustku; spoiler přes začátek ukázky se zkrátí (emoji v něm zůstane skryté),
  useknuté vlastní emoji vypadne. Ověřeno sondou — bot vlastní emoji poslat nesmí (viz
  „Vlastní emoji v textu").
- **Ťuknutí na zprávu otevře chat se skokem na ni**: `ConversationRoute(chatId, messageId)`,
  ViewModel dostane `initialMessageId` a místo skoku na nepřečtené zavolá `jumpToMessage`
  (okno kolem zprávy a zvýraznění jako u citace, viz „Okno v historii"). Druhý parametr
  Koinu se čte INDEXEM (`parameters.values.getOrNull(1)`): bývá `null` a podle typu by se spletl
  s `chatId`. **Konverzace na vrcholu zásobníku se porovnává podle `chatId`**
  (`isOpenConversation`), ne rovností routy — s `messageId` by přeposlání otevřelo tentýž chat
  podruhé.
- **Globální výsledek otevře chat, ve kterém uživatel není**: `ChatInfo.canJoin` (supergroup
  se stavem `ChatMemberStatusLeft`, `getSupergroup` je lokální) nahradí vstupní pole tlačítkem
  „Přidat se" (`joinChat`). Psát do cizí skupiny nejde a nabízet pole by znamenalo chybu po
  odeslání.
- **Na široké ploše je hledání panel pod pilulkou, ne celá obrazovka** (`ExpandedDockedSearchBarWithGap`
  podle `LocalTwoPane`). Material doporučuje docked od střední šířky; celoobrazovkové hledání by
  na rozloženém Foldu přikrylo i otevřenou konverzaci vpravo. Panel je široký jako pilulka (tedy
  levý panel), zbytek obrazovky pod závojem; výšku i místo nad klávesnicí (`imePadding`) si řeší
  komponenta sama, takže výsledky v panelu spodní inset NEBEROU (`LocalDockedResults`) — jinak by
  pod posledním výsledkem zůstala mezera o výšku klávesnice. Obsah (`SearchResults`) je pro obě
  podoby jeden.
- **Zpět v panelu napřed schová klávesnici, teprve další zpět zavře hledání** (`PopupBackAfterKeyboard`
  v `core/ui/PopupBack.kt`), jako na telefonu. Panel je popup a dvě věci to rozbíjely:
  - **Popup si na Androidu 13+ registruje zpět s `PRIORITY_OVERLAY`**, takže předběhne
    klávesnici, a registruje ho JEN při připojení okna — změna `dismissOnBackPress` za běhu se už
    nepromítne (přečteno ve zdroji `AndroidPopup.android.kt`). Proto `dismissOnBackPress = false`
    (`popupDismissesOnBack`) a vlastní callback přímo v okně popupu (`PopupBackHandler`,
    `view.findOnBackInvokedDispatcher()`). Vnitřní `BackHandler` komponenty i `NavigationBackHandler`
    jdou přes dispatcher aktivity a zpět z okna popupu k nim nedojde — bez vlastního callbacku
    druhé zpět nedělalo nic.
  - **Callback je zapnutý pořád a rozhoduje až při vyvolání** (klávesnice vidět → schovat, jinak
    zavřít). Nezáleží pak na tom, do kterého okna si klávesnice registruje vlastní zpět, ani kdy
    během gesta zhasne `isImeVisible`. Klávesa zpět jde do klávesnice sama.
  - **Ověřuje se gestem v horní půlce obrazovky.** `input swipe` od levého okraje ve výšce těsně
    nad klávesnicí (y = 1100 na rozloženém Foldu) systém jako gesto zpět nevzal — v logu nebyl
    `startBackNavigation` — a tah dopadl do závoje popupu jako ťuknutí, takže zavřel hledání
    i s klávesnicí; vypadalo to, že oprava nefunguje. Proč zrovna tam, zjištěné není. Ve výšce
    700 gesto prošlo. Jestli zpět opravdu proběhlo, říká `adb logcat | grep
    startBackNavigation` (`CoreBackPreview`).
- **Spodní inset si berou seznamy výsledků, ne `ExpandedFullScreenSearchBar`.** Jeho výchozí
  `windowInsets` je `safeDrawing` a spodek dává obsahu jako padding, takže výsledky končily
  useknuté nad navigační lištou na holé ploše. Hledání dostane jen `Top + Horizontal`, seznamy
  spodek (lišta i klávesnice) v `contentPadding` (`resultsPadding`) — obsah jede pod lištu
  a poslední položka se nad ni doroluje. Tentýž vzor jako u seznamu chatů a konverzace.
- **Prázdný výsledek je `IntroEmptyState`** („Nic nenalezeno", ikona `search_off`), stejný
  jako placeholder detailu a prázdný archiv. Plochu nekreslí (`containerColor = Transparent`,
  hledání má vlastní barvu) a spodek má nad klávesnicí (`resultsPadding`), jinak by se
  centroval pod ni. **Jednou ukázaný vydrží i během hledání dalšího znaku** (`emptyShown`) —
  kdyby ho mezitím vystřídal indikátor, pouštěla by se úvodní animace při každém znaku.
- **Selhání hledání se nehlásí.** Prázdný výsledek je u hledání normální stav a lokální výsledky
  zůstanou, i když server neodpoví.
- **Dotaz se maže při KAŽDÉM sbalení** (efekt nad `expanded`), ne v obsluze šipky. Gesto zpět
  zavírá dialog hledání samo a obsluhu šipky obchází.
- **Ověřování snímky: `sips -c` ořezává ze STŘEDU**, `--cropOffset` ignoruje. Výřez „horní
  části" byl ve skutečnosti prostředek obrazovky a vypadalo to, že bar chybí.

### Odlehčení: blok je světlejší než pozadí

- **Vrstvení je otočené proti tomu, jak vzniklo.** Původně bylo pozadí `surface` (nejsvětlejší role)
  a řádky `surfaceContainerHighest` o tři stupně tmavší — z položek byly šedé plochy na světlém
  a seznam působil těžce. Teď mají řádky `surfaceContainerLowest`, takže je to vztah „světlý panel
  na tónované stránce". Tím dělá odstup i Google Messages: jeho řádky samy žádnou výplň nemají,
  nese ho rozdíl panelu a pozadí.
- **V tmavém režimu se to obrátí a je to správně.** `surfaceContainerLowest` je tam nejtmavší role,
  takže z panelu bude tmavší plocha na světlejším pozadí. Role drží HIERARCHII, ne konkrétní
  světlost — proto se nesahá po natvrdo zadané barvě.
- **Název jde běžným řezem, polotučně jen nepřečtený.** Když je polotučný každý chat, nevyčnívá
  žádný. Proto `bodyLarge` + `FontWeight.SemiBold` jen podle `hasUnread`, ne `titleMedium` pro vše.
- **Odznak nepřečtených není chybově červený.** M3 `Badge` je ve výchozím stavu `error`; nepřečtená
  zpráva ale není chyba a v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce.

### Uložené zprávy

Uložené zprávy jsou v Telegramu **obyčejný soukromý chat s vlastním účtem** — id chatu je id
uživatele z `getMe()`. Všichni klienti je ale kreslí jinak a my taky:

- **Odchozí je tu VŠECHNO.** TDLib hlásí u přeposlané zprávy `isOutgoing = false` (napsal ji
  někdo jiný), takže se kreslila jako příchozí — šedá bublina vlevo a bez odznaku doručení —,
  zatímco vlastní fotky v témž chatu stály vpravo. Chat sám se sebou ale druhou stranu nemá;
  `TdlConversationRepository.toChatMessage` proto bere `isOutgoing || chatId == myId()`
  a odkud zpráva je, říká značka „Přeposláno od …". **Musí to sedět i se `sendState`** — ten se
  plní „jen u odchozích", takže dokud se opravil jen `isOutgoing`, zpráva se přesunula doprava,
  ale odznak doručení pod ní chyběl. Id vlastního účtu se drží per klient, jako keš odesílatelů:
  po přihlášení jiného účtu je jiné.
- **Vlastní název a záložka místo jména a fotky účtu** (`chatTitle`, `Avatar(isSavedMessages =
  true)`). Rozpoznává se podle `ChatRepository.savedMessagesChatId`, do UI jde přes
  `LocalSavedMessagesChatId` (poskytuje shell). **`Avatar` si to NEzjišťuje sám** — kreslí
  i odesílatele (reakce, zhlédnutí, profilovky ve skupině) a tam má být vlastní tvář.
- **V seznamu chatů nejsou, pokud nejsou připnuté** (`chatGroups`, test
  `ulozeneZpravyJenPripnute`). Vstup je menu účtu v pilulce (vedle archivu). Připnutí
  je výslovné přání uživatele, takže připnuté zůstávají — schovat je by působilo jako chyba.
- **Ve výběrech (přeposlání, sdílení, iOS rozšíření, Direct Share) jsou vždy první**, i když
  v seznamu chatů nejsou. V `ChatPicker` jako samostatná karta v `primaryContainer` nad
  seznamem, ne jeho první řádek — v řadě chatů by nejčastější cíl splynul s ostatními. „Pošlu si to" je nejčastější cíl. Hledání je najde podle přeloženého
  názvu.
- **Chat se zakládá hned po přihlášení** (`createPrivateChat`, lokální). U účtu, který si nic
  neuložil, ho TDLib v databázi nemá a nešlo by ho otevřít ani do něj přeposlat.
- **Záložka v avataru je `primary`, na kartě ve výběru proto dostává `backdrop`.** V paletě
  se zvýšeným kontrastem je `primaryContainer` světlý jako `primary` a kolečko s kartou
  splynulo; `Avatar` pak přes `accentOn` sáhne po barvě popředí té plochy.
- **V hlavičce není „online"** — vlastní dostupnost v Uložených zprávách nic neříká.
- **Zkratka konverzace a notifikace** (připomínka v Uložených zprávách) mají název
  „Uložené zprávy" a záložku (`bookmarkIcon`), ne jméno a fotku účtu.
- **Štítky Uložených zpráv (`SavedMessagesTag`) zatím nejsou.** Témata ano, viz níž.

#### Témata Uložených zpráv

„Zobrazit jako chaty" v hlavičce Uložených zpráv přepne na seznam témat (`SavedTopicsRoute`,
`SavedTopicsScreen`): Moje poznámky, pak chaty, ze kterých se zprávy uložily, s poslední uloženou
zprávou. Téma se otevře jako konverzace (`ConversationRoute.savedTopicId`), „Zobrazit jako zprávy"
v ⋮ vrátí jednu konverzaci. Hranice je `SavedTopicsRepository` (`TdlSavedTopicsRepository`).

- **Témata chodí JEN updaty** (`UpdateSavedMessagesTopic`). Dotaz na seznam ani na jedno téma
  TDLib nemá; `loadSavedMessagesTopics` nic nevrací, jen řekne „pošli další". Repository je proto
  eager a drží mapu podle id, a `load()` se volá při každém otevření seznamu (po startu je TDLib
  sám nepošle). **Konec načítání je chyba 404**, ne prázdná odpověď. `order == 0` = téma v seznamu
  není, jako u chatů.
- **Do tématu jiného než Moje poznámky psát NEJDE.** TDLib odesílání s `MessageTopicSavedMessages`
  odmítne („Messages can't be explicitly sent to a Saved Messages topic", ověřeno sondou) —
  zpráva bez tématu skončí v Mých poznámkách (id tématu = id vlastního účtu). Konverzace tématu
  proto rozlišuje dvě pole: `readTopic` (historie, hledání, připnuté, nepřečtené) a `topic`
  (odeslání, koncept, „píše…"), které u Uložených zpráv zůstává `null`. Cizí téma je jen pro čtení
  (`WriteAccess.SavedTopic`) s tlačítkem „Otevřít chat" do zdrojového chatu.
- **Volba „jako chaty" se pamatuje** (`AppSettings.savedMessagesAsChats`, výchozí ne) a řídí jen
  ťuknutí ze seznamu chatů a z menu účtu (obojí jde přes `onChatClick`). Notifikace, hledání
  a výběry otevírají dál celou konverzaci — míří na konkrétní zprávu.
- **Smazání tématu je `deleteSavedMessagesTopicHistory`**: zmizí uložené zprávy, zdrojový chat ne.
  U Mých poznámek se nenabízí — smazalo by všechny vlastní poznámky jedním ťuknutím.
- **Ověřeno na emulátoru** (česky): seznam čtyř témat, Moje poznámky s polem (zpráva odešla
  a objevila se v tématu i v jeho náhledu), téma Notifikator jen pro čtení a „Otevřít chat",
  připnutí a odepnutí (seznam se přeskládá sám updatem), dialog smazání (zrušený), volba přežije
  restart. **Smazání tématu ověřené NENÍ** — mazalo by skutečné uložené zprávy. iOS jen přeložené.

### Nová konverzace a kontakty

`NewChatScreen` (`features/chats`), `NewChatRoute`, vstup FAB s tužkou v seznamu chatů.

- **Je to tentýž `ChatPicker` jako přeposílání a sdílení** (viz „Jeden výběr chatu"). Vlastní
  seznam tu byl a **při přestavbě výběru se na něj zapomnělo**: zůstal u `TextField`
  s podtržením, holých `ListItem`ů na pozadí, `shapes.large` u karty Uložených zpráv, holého
  textu místo prázdného stavu a bez spodního insetu, zatímco přeposílání už mluvilo jazykem
  seznamu chatů. Dva vzhledy pro „seznam chatů k výběru" znamenaly, že uživatel poznává výběr
  na každé obrazovce znovu.
- **ViewModely zůstávají dva.** `NewChatViewModel` načítá kontakty, `ForwardViewModel` řeší
  volby a odesílání — sdílí se vzhled, ne obsah.
- **Vstup je FAB menu** v seznamu chatů (viz „Složky místo filtrů a FAB"): Nová zpráva, Nová
  skupina a Nový kanál. Skupina a kanál jsou i řádky nahoře na téhle obrazovce.
- **Kontakty jdou z `getContacts`** a pro každý se lokálně zavolá `createPrivateChat(userId, force = false)`
  — TDLib tak pro kontakt založí chat objekt (lokálně v paměti/databázi, bez odesílání zpráv)
  a do seznamu se dostane kompletní `ChatSummary` s avatarem i dostupností (`withPresence()`).
- **Uložené zprávy jsou i tady nahoře**, jako samostatná karta v `primaryContainer`.
- **Vyhledávání kombinuje tři vrstvy:** lokální kontakty (filtrace podle jména), existující známé chaty
  z `searchChats` a veřejné Telegram uživatele / skupiny z `searchPublicChats` (podle `@username`).
- **Prázdno má dvě podoby.** Bez dotazu „Žádné kontakty" s radou hledat podle `@jména`,
  s dotazem „Nic nenalezeno" — dvě různé situace a rada dává smysl jen u té první.
- **Načítání se hlásí, jen dokud nejsou kontakty.** Jinak by hledání pod prstem vyměnilo seznam
  za kolečko při každém znaku.
- **List panel na dvoupanelovém rozvržení:** Na tabletu / TwoPane nahradí levý panel stejně jako
  Archiv, po výběru kontaktu se zavře a v pravém panelu se otevře konverzace.

### Založení skupiny a kanálu

„Nová skupina" a „Nový kanál" jsou dva řádky nad kontakty v „Nové zprávě" (schované při
hledání). Skupina: výběr členů (`NewGroupMembersRoute`, `NewGroupMembersScreen`) → název
a fotka (`CreateChatRoute`, `CreateChatScreen`); kanál rovnou formulář s popisem. Hranice je
`ChatCreation` v `core/telegram`, po založení se otevře nový chat.

- **Vždycky supergroupa** (`createNewSupergroupChat`), i pro malou skupinu. Základní skupina
  nemá témata, část práv ani veřejný odkaz a při prvním takovém kroku se převádí — na chat
  s JINÝM id, které by rozsypalo zásobník i koncepty. Skupina a kanál tak jdou jedním voláním.
- **`ChatCreation` je samostatné rozhraní, ne metoda `ChatRepository`** — ten má v testech
  desítky podvrhů a založení s jeho seznamem nesouvisí.
- **Výchozí dobu automatického mazání posílá klient** (`messageAutoDeleteTime`); TDLib ji
  u supergroupy sám nepoužije. Bere jen celé dny do roku a jinou hodnotu by odmítl i se
  založením (`newChatAutoDelete`, testy `CreateChatTest`).
- **Členové se přidávají až do STOJÍCÍ skupiny** (`addChatMembers`). Koho server nepustí
  (soukromí, `FailedToAddMembers`), ohlásí dialog jménem — skupina se kvůli tomu nezahazuje
  a otevře se po zavření dialogu. Totéž selhání fotky: chat už stojí, zakládat ho znovu by
  vyrobilo druhý.
- **Výběr dává chaty, do skupiny jdou uživatelé.** Id uživatele se bere z typu chatu
  (`ChatTypePrivate`), ne z id chatu — tajný chat má id jiné. Nabízejí se jen soukromé chaty
  bez Uložených zpráv (`groupMemberCandidates`).
- **Pokračovat jde i bez členů** (`ChatPicker(confirmWhenEmpty = true)`): skupina smí vzniknout
  prázdná a pozvat se dá odkazem.
- **Soukromý, nebo veřejný** (spojená přepínací tlačítka pod názvem, výchozí soukromý — veřejný
  chat najde kdokoli a to má být vědomá volba). Veřejný chce odkaz `t.me/…`, kontrolovaný jako
  vlastní uživatelské jméno: místní pravidla hned, dostupnost po pauze (`UsernameStatus`
  a `usernameMessage` se proto přestěhovaly z úpravy profilu do `core/ui/UsernameMessage.kt`).
  **Zakládaný chat se ptá `checkChatUsername(chatId = 0)`** — tak to TDLib pro chat, který ještě
  neexistuje, chce. Strop veřejných chatů (`PublicChatsTooMany`) má vlastní větu.
- **Veřejné jméno se nastavuje až stojící supergroupě** (`setSupergroupUsername`),
  `createNewSupergroupChat` ho nebere. Když to selže (jméno mezitím někdo vzal), chat zůstane
  soukromý a řekne to dialog po založení — zakládat znovu by vyrobilo druhý chat.
- **Komu server nedovolil přidání, tomu jde po založení poslat pozvánku** — tytéž věty a tatáž
  cesta (`ProfileRepository.sendInviteLink`) jako u přidávání členů. Pozvánku vlastníkovi vydá
  `profile(chatId).inviteLink`; když ji zjistit nejde, dialog jen vyjmenuje, koho přidat nešlo.
- **Fotka se ořízne hned po výběru** (`SquarePhoto`) a nahraje až s id chatu — náhled ukazuje
  přesně to, co odejde.
- **Po založení se z Chatů odebere celá větev** (nová zpráva, členové, formulář) a zpět z nového
  chatu vede do seznamu, ne do formuláře, ze kterého by šlo založit druhý.
- **Ověřeno na emulátoru se souhlasem** (česky): skupina s testovacím botem jako jediným
  členem (servisní zprávy „vytvořena" a „přibyli členové", tlačítko příkazů bota) a kanál
  s popisem (čtvercový avatar, popis v profilu); po založení se otevře chat a zpět vede do
  seznamu. Bot jde do výběru členů přes hledání — v kontaktech není. **Neověřeno**: fotka při
  založení, dialog s lidmi, které server nepustil (chce cizí účet se zakázaným přidáváním),
  a předání výchozí doby mazání. Testovací skupina a kanál na účtu zůstaly.
  Veřejný kanál ověřený na emulátoru: obsazené jméno („durov") i chybný formát hlásí pole,
  volné potvrdí, a založený kanál má `@plume_test_1005` v profilu i podle Bot API (`getChat`).
  **Dialog s pozvánkou jen sondou** (založení podstrčené výsledkem s testovacím botem mezi
  odmítnutými a selhaným odkazem, necommituje se): obě věty, „Poslat pozvánku" a bot dostal
  pozvánku jako odkaz s entitou `url`. Skutečný člověk, který přidávání zakazuje, ověřený není.
  Pozvánka je i u veřejného chatu odkaz `t.me/+…` (`profile.inviteLink`), ne `t.me/jméno` —
  funguje stejně.

### Jeden výběr chatu: `ChatPicker`

Přeposílání, sdílení ze systému i zahájení nové konverzace kreslí tentýž seznam
(`core/ui/ChatPicker.kt`): `SearchPill`, karta Uložených zpráv a bloky zaoblených řádků.

- **Bydlí v `core/ui`, ne v `features/forward`.** Tam vznikl a sdílení si ho odtud importovalo;
  nová konverzace (`features/chats`) by tím na balíčku přeposílání závisela. Stejná úvaha jako
  u `GroupedList.kt` — mluví jím víc obrazovek než jedna.
- **Obsah dávají SEKCE** (`PickerSection`): volitelný nadpis a chaty pod ním jako jeden blok.
  Přeposílání a sdílení dávají jednu sekci bez nadpisu, nová konverzace tři (Kontakty / Chaty /
  Globálně). Prázdné sekce se zahazují, jinak by po nich zbyl nadpis nad ničím — totéž pravidlo
  jako u skupin v seznamu chatů.
- **Klíč položky nese i klíč sekce.** Tentýž chat může být ve dvou sekcích a dva stejné klíče
  `LazyColumn` shodí.
- **Mezeru před blokem nese jeho první položka** (nadpis, nebo první řádek), ne
  `verticalArrangement` — seznam o sousedech položky nic neví. Nad prvním blokem se nepřidává,
  odstup od hlavičky dává `contentPadding`.
- **Dostupnost pod jménem je volba obrazovky** (`showPresence`), ne vlastnost seznamu. Patří
  tam, kde se vybírá ČLOVĚK; u přeposílání by „naposledy před hodinou" o cíli nic neříkalo
  a řádky by kvůli tomu byly vyšší.
- **Podtitul má vlastní barvu popředí** (`mutedContentColor`). `onSurfaceVariant` je počítaná na
  `surface` role, takže na vybraném `secondaryContainer` i na kartě Uložených zpráv patří jejich
  vlastní `on*` — tatáž past jako u času a ikon u vybraného chatu v seznamu. Zelená u „online"
  navíc projde přes `accentOn`: `primary` na tónované kartě je v paletě se zvýšeným kontrastem
  k nerozeznání.
- **Titulek se šipkou při scrollu odjede** (`enterAlwaysScrollBehavior`), jako hlavička seznamu
  chatů. Přečte se jednou a pak jen ukusuje ze seznamu; nahoru se vrátí na první pohyb.
- **Pilulka hledání s ním NEODJÍŽDÍ**, i když v seznamu chatů ano. Tam je pilulka sám ten bar
  a hlavní činnost je čtení seznamu; tady je hledání to hlavní — chatů jsou stovky a ten
  hledaný v první obrazovce většinou není. Zmizelé pole přesně ve chvíli, kdy ho uživatel
  potřebuje, je gesto navíc za nic.
- **Obsah hlavičky (`header`) je první POLOŽKA seznamu**, ne sourozenec nad ním. Volby
  přeposlání a karta „co se sdílí" jsou obsah úlohy, ne chrome, takže mají se scrollem odjet —
  a jako položka seznamu to dělají samy, bez další mechaniky. V načítání a v prázdném stavu
  žádný seznam není, takže se kreslí nad ním; vypadá to stejně.
- **Mezeru pod sebou nese hlavička, ne seznam.** Přeposlání bez voleb (server zakázal kopii)
  a sdílení bez textu nekreslí nic, a mezera po ničem by vypadala jako chyba.
- **Obsah se drží pod stavovou lištou** (`max(padding.top, statusBars.top)`). Odjíždějící bar
  smrskne horní padding od Scaffoldu až na nulu, takže by pilulka skončila pod hodinami.
  Seznam začíná až pod pilulkou, takže pod lištu nic nejede a **závoj jako v seznamu chatů tu
  potřeba není** — tam pod ni chaty jezdí, protože tam odjíždí i pilulka.
- **Sbalený bar NEMĚNÍ barvu** (`scrolledContainerColor = surface`). Výchozí `surfaceContainer`
  nechal pod stavovou lištou tónovaný pruh přes celou šířku — přesně to, kvůli čemu z hlavičky
  seznamu chatů zmizel velký app bar.

### Co položka ukazuje

Čas poslední zprávy, ztlumení, dostupnost protějšku a jeho právě probíhající činnost.

- **Čas se s věkem zjednodušuje** (`asRelativeTime`): dnes hodiny, včera slovem, do týdne zkratka
  dne, pak datum. V seznamu jde o STÁŘÍ zprávy, ne o pořadí během dne — „14:32" u zprávy z loňska
  nic neřekne. V konverzaci se naopak vypisují jen hodiny, protože tam jdou zprávy chronologicky
  za sebou.
- **„Naposledy viděn" má VLASTNÍ formát** (`asLastSeen`), ne tenhle. `asRelativeTime` je dělaný
  na SLOUPEC, kde čas stojí sám a zkratka dne se přečte jako zkratka; uvnitř věty z ní byla
  hláška **„naposledy ne"**, u které to vypadá, že se text usekl. `asLastSeen` proto skládá celou
  větu: „naposledy dnes, 7:41", „naposledy v neděli, 11:45", „naposledy 18. listopadu 2025".
- **Den nese předložku, protože v češtině není jedna** (`day_on_*`): „v pondělí", ale „ve středu".
  Lepit ji v kódu by znamenalo, že si ji jazyk neuhlídá — tentýž důvod, proč je pořadí dne
  a měsíce formátovací řetězec, ne slepenec.
- **Starší než týden dostane datum bez času.** Hodina u něčeho, co bylo před měsícem, nic neříká;
  rok se dopisuje, jen když nesedí s letoškem.
- **Dostupnost se musí jednou přečíst, ne jen odebírat.** `updateUserStatus` chodí jen na ZMĚNU,
  takže bez počátečního `getUser` by zelená tečka naskočila až ve chvíli, kdy se protějšek odpojí
  a zase připojí. V soukromém chatu je `chatId` shodné s `userId`, takže není co dohledávat.
- **Dostupnost a činnost PŘEŽÍVAJÍ přepsání chatu** (`keepingLiveState`). `Chat` od TDLib je
  nenese — chodí samostatnými updaty. Bez toho by každý `UpdateNewChat` nebo seed z databáze
  zhasl zelenou tečku i „píše…", protože obojí by se vrátilo na `null`.
- **„Píše…" se musí umět zhasnout samo.** TDLib činnost ukončuje samostatným `ChatActionCancel`,
  jenže kdyby se ten ztratil, zůstalo by „píše…" viset napořád. Každá činnost si proto nese
  odpočet, který nová zpráva o psaní posouvá — při souvislém psaní nikdy nedoběhne.
- **Vlastní činnost se zahazuje.** Porovnává se s id přihlášeného účtu, zjištěným jednou při
  přihlášení; v seznamu by vypadalo divně, že chat hlásí psaní, když píšeme my.
- **Činnost má přednost před náhledem zprávy** a odlišuje se barvou — je to novější informace
  a obojí najednou se do řádku nevejde.
- **Telegram dostupnost záměrně rozmazává.** Kdo si to v soukromí vypne, je vidět jen jako
  „naposledy nedávno" a přesný čas nedostaneme. Proto je `Presence` několik pojmenovaných stupňů,
  ne čas — dopočítávat minutu, kterou neznáme, by bylo horší než neurčitost.

### Archiv

Archivované chaty jsou vlastní seznam (`ArchiveRoute`, `ArchiveScreen`), vstup je menu účtu
v pilulce hledání. Do archivu a zpátky se chat dostane z nabídky po dlouhém stisku; místo potvrzení je
snackbar s „Vrátit".

- **Repository drží VŠECHNY chaty, které TDLib ohlásí, a složka je jen pozice**
  (`TdlChatRepository.positions`, `ChatFolder`). Dřív se chat mimo hlavní seznam vůbec neukládal,
  takže se zahazovaly i jeho updaty — archivovaný chat neukázal v konverzaci „píše…". Seznamy
  složek se skládají až při publikaci; `ChatSummary.order` / `isPinned` / `folder` platí pro tu
  složku, ze které položka přišla.
- **Počet pro `getChats` se bere ze složky, ne ze všech známých chatů.** Těch je víc (i chaty
  mimo složky, z notifikací a odkazů) a limit by se nafukoval.
- **Archivace je `addChatToList(ChatListArchive)`, vrácení `addChatToList(ChatListMain)`.**
  Složky se vylučují, odebrání z té druhé pošle TDLib samo updatem pozice s nulovým `order`.
  Uložené zprávy archivovat nejdou, nabídka je u nich nemá.
- **Připnutí patří pozici ve složce.** Archiv má vlastní připnuté (a vlastní limit
  `pinned_archived_chat_count_max`), proto `setPinned` bere složku.
- **Archiv se začne načítat až při otevření** (`ChatsViewModel` s parametrem `ChatFolder.Archive`).
  Do té doby je `Loading`, ne prázdný — složka je hotová, až se jednou přečte z databáze, nebo
  když v ní už něco je.
- **Jeden ViewModel i obsah pro oba seznamy.** `ChatsViewModel` dostane složku parametrem
  (Koin, klíč `chats-archive`), seznam je `ChatListContent`, akce `ChatActionsHost`. Archiv
  nemá hledání ani kartu aktualizace, jinak je to tentýž kód.
- **Na široké ploše je archiv samostatná list-detail scéna** (`ARCHIVE_SCENE` v `AppShell`).
  V dvoupanelu má vpravo vlastní detail panel `ArchiveConversationRoute` (nebo placeholder),
  takže neukazuje cizí konverzaci otevřenou z hlavního seznamu. Šipka zpět v hlavičce archivu
  odebere celou archivní větev a vrátí hlavní seznam v jeho původním stavu (včetně původně
  otevřené konverzace). Chat otevřený z archivu má vlastní routu `ArchiveConversationRoute`
  a při jeho obnovení z archivu („Vrátit mezi chaty“) se jeho konverzace automaticky zavře.
  Symetricky při archivaci chatu z hlavního seznamu se zavře jeho otevřená `ConversationRoute`.
  Šipku zpět má archiv i v dvoupanelu — na rozdíl od sekce nastavení je to seznam, ne detail.
- **Prázdný archiv má tentýž animovaný prázdný stav jako placeholder detailu**
  (`IntroEmptyState` v `core/ui`, vytažený z `ConversationPlaceholder`). Na široké ploše stojí
  oba vedle sebe a dva různé vzhledy pro „tady nic není" by se přely. Prázdný stav si
  `ChatListContent` bere jako slot (`empty`), hlavní seznam má dál holý text.
- **Prázdný seznam na široké ploše dostane celou šířku** (`nothingToPick` v `ShellContent`):
  bez otevřené konverzace a s prázdným zobrazeným seznamem (archiv i hlavní) přejde scéna na
  jeden panel. Placeholder „vyber chat vlevo" by vedle prázdného stavu radil něco, co udělat
  nejde. Rozhoduje se až nad `Ready` — `Loading` za prázdný nepočítat, jinak se rozvržení
  přepne tam a zpátky, než seznam dorazí. Výjimka z pravidla „dva panely vždycky" (viz „Navigace").
- **„Otevřená konverzace" se od rozdělení scén musí ptát NA VĚTEV, ne na zásobník jako celek**
  (`hasOpenConversation`). Dokud podmínka brala `ConversationRoute` i `ArchiveConversationRoute`
  najednou, držela otevřená konverzace hlavního seznamu dvoupanel i nad PRÁZDNÝM archivem —
  vlevo prázdný stav, vpravo placeholder archivní scény, tedy přesně ty dva prázdné stavy vedle
  sebe, kterým má `nothingToPick` bránit. Dřív to nevadilo, protože archiv sdílel scénu
  s hlavním seznamem a vpravo stála skutečná konverzace. **Obecně: co se ptá na zásobník Chatů,
  musí od rozdělení scén vědět, kterou větev se ptá.**
- **Pravidla nad zásobníkem Chatů jsou čisté funkce** (`ChatsStack.kt`, testy `ChatsStackTest`),
  ne lambdy v `entryProvider`. Zásobník nese dvě list-detail větve naráz, takže skoro každá
  operace musí vědět, do které patří — a uvnitř lambdy se to neotestuje. První verze testů to
  obešla tak, že si těla lambd přepsala do testu; takový test projde i nad rozbitým kódem,
  protože produkční cestu vůbec nezavolá.
- **Hlavička je obyčejný `TopAppBar` bez scroll behavior**, takže nemění barvu — tónovaný pruh
  přes celou šířku byl důvod, proč hlavní seznam opustil `LargeFlexibleTopAppBar`.
- **Počet nepřečtených v archivu počítá TDLib** (`UpdateUnreadChatCount` pro `ChatListArchive`),
  ne my nad načteným seznamem — archiv se načítá až při otevření, takže do té doby by nebylo
  z čeho počítat, a právě tehdy je odznak nejpotřebnější. Update chodí **jen se zapnutou databází
  zpráv** (`useMessageDatabase`); bez ní se nic nerozbije, jen se nikdy neobjeví.
- **`unreadCount` v tom updatu započítává i ručně označené chaty** (ověřeno na emulátoru:
  archivovaný chat bez nepřečtených zpráv, jen s `isMarkedAsUnread`, odznak vyrobil). Sedí to
  tedy s `ChatSummary.hasUnread` a dopočítávat `markedAsUnreadCount` netřeba.
- **Odznak je na dvou místech, protože menu účtu je zavřené**: číslo u položky Archiv a tečka na
  fotce účtu. **Tečka je jen od NEZTLUMENÝCH chatů** (`unreadUnmutedCount`) — do archivu se
  ztlumené chaty ukládají, takže tečka od nich by byla signál, který nikdy nezhasne. Číslo
  u položky počítá všechny, ale v neutrální barvě, dokud není co ztlumeného; sytý odznak nad
  samými ztlumenými chaty je falešné volání.
- **Ikona na podkladu patří k hraně, ze které se tah odkrývá** (doleva tedy vpravo od popisku,
  doprava vlevo). S pevným pořadím se při tahu doleva odkryl jako první konec věty, ze kterého
  není poznat, co se stane.
- **Archivace tahem jde na OBĚ strany a obě dělají totéž.** Druhá akce v seznamu není, takže by
  si uživatel musel pamatovat směr pro nic. Nepotvrzuje se — snackbar „Vrátit" je tentýž jako
  u archivace z nabídky. Uložené zprávy tah nemají, stejně jako nemají položku v nabídce.
- **`SwipeToDismissBox` se na tohle NEHODÍ a je pryč** (prah v `ArchiveSwipeGesture.kt`,
  testy `ArchiveSwipeGestureTest`). Byl „hodně moc citlivý": při scrollování seznamu odlétaly
  chaty do archivu. Dvě příčiny, ani jedna se zvenku vypnout nedá:
  - **Stojí na `draggable`, které si tah vezme po obyčejném touch slopu a je směrově
    agnostické.** Položka je potomek seznamu, takže gesto dostane dřív — a palec jde na začátku
    scrollu obloukem do strany, takže osm bodů do strany stačilo, aby seznam o scroll přišel.
  - **Archivuje ZKRATKOU PŘES RYCHLOST.** `AnchoredDraggableDefaults.flingBehavior` bere tah
    rychlejší než 125 dp/s jako dokončený BEZ OHLEDU NA TO, kam došel; `positionalThreshold`
    (výchozích 56 dp) se v tu chvíli vůbec nepočítá. Zvedat práh je proti tomuhle k ničemu —
    škubnutí prstem archivovalo, ať byl jakkoli vysoko.
- **Vlastní detektor rozhodne jednou za gesto** (`detectHorizontalSwipe`, sdílený s odkrýváním
  časů a odpovědí tahem v konverzaci, viz „Jeden detektor tahu do strany"): rozhoduje se až po DVOJNÁSOBKU touch slopu a jen když je
  vodorovná složka aspoň dvakrát větší než svislá; do té doby nic nespotřebovává, takže si
  šikmý tah vezme seznam. Archivuje se při PUŠTĚNÍ po `archiveSwipeThreshold` (40 % šířky
  řádku, nejvýš 160 dp), rychlost se nepočítá vůbec. Prah ohlásí haptika, jako u odpovědi
  tahem v konverzaci.
- **S vlastním detektorem odpadl hlídač dvojího odeslání.** `SwipeToDismissBox` hlásil dokončený
  tah z `LaunchedEffect`, jehož klíčem byla i lambda `onDismiss` — ta se při rekompozici položky
  mohla změnit, efekt se rozběhl ZNOVU a chat se archivoval podruhé (a po „Vrátit" zase zpátky).
  Tady akce odchází přímo z konce gesta, takže jednou. **Past platí pro každé použití téhle
  komponenty**, kdyby se někde vrátila.
- **Archivace odchází PŘED animací odjezdu.** Odscrollovaná položka se uklidí z kompozice
  i s rozsahem korutin, takže po odsunutí a rychlém scrollu by se akce na konci animace už
  neměla kde odeslat.
- **Tvar bloku se počítá mimo řádek** (`groupItemShape`): tentýž tvar musí mít i podklad
  odkrývaný tahem, jinak pod zaoblenou položkou vykukují jeho hranaté rohy. `animateItem` zůstává
  na kořeni položky, tedy na tahu, ne na řádku uvnitř.
- **Bez tahu se podklad nekreslí VŮBEC, ani jeho plocha.** Stejný tvar nestačí: vyhlazené hrany
  řádku jsou poloprůhledné a barevný podklad pod nimi prosvítal jako světlý lem kolem každého
  chatu, v tmavém tématu na skoro černém `surfaceContainerLowest` do očí bijící. Na emulátoru
  (hustota 3,0) leží rovné hrany na celých pixelech a lem je vidět jen v zaoblených rozích; na
  telefonu s necelou hustotou jde kolem celé karty. **Obecně: pod plochou, která má být krycí,
  nesmí trvale ležet jiná barva stejného tvaru.** Ukáže se na každé vyhlazené hraně.
- **Nastavení archivu je sheet z hlavičky archivu, ne pátá sekce Nastavení.** Jsou to dvě volby
  o tom, co se s archivem děje samo, a kdo je hledá, stojí zrovna v archivu.
- **TDLib na změnu `ArchiveChatListSettings` NEPOSÍLÁ update**, takže sheet je jediný zdroj pravdy:
  přepínač se pohne hned a při selhání se vrátí. Čekat na potvrzení by znamenalo přepínač, který
  se po ťuknutí nehýbe.
- **Zapisuje se celým objektem**, jako ztlumení chatu — všechny tři volby nese `ArchiveSettings`.
- **„Nechávat v archivu chaty ze složek" (`keepChatsFromFoldersArchived`) jen se složkami a jen
  bez „Nechávat chaty v archivu"** (`AnimatedVisibility` v sheetu). Týká se neztlumených chatů,
  které jsou ve složce vybrané nebo připnuté, a TDLib ho ignoruje, když platí volba nad ním —
  ta drží v archivu všechny neztlumené. Přepínač bez následku by sliboval něco, co se nestane.
- **„Archivovat chaty od neznámých" povoluje server** (`can_archive_and_mute_new_chats_from_unknown_users`);
  kde ho nepovolí, TDLib zapnutí odmítne. Přepínač se tam proto nenabízí vůbec — přepínač,
  který skončí chybou, je horší než žádný.

### Akce nad chatem

Dlouhý stisk otevře bottom sheet: připnout, ztlumit, označit jako nepřečtené, smazat/opustit.

- **Smazat NENÍ `deleteChat`.** Ten maže obsah VŠEM členům — z položky v seznamu to nikdo nečeká.
  U soukromého chatu se volá `deleteChatHistory(removeFromChatList = true, revoke = false)`, tedy
  smazání jen u nás, u skupiny a kanálu `leaveChat`. Jsou to dvě různé věci, takže má každá
  vlastní popisek i vlastní text potvrzení; jeden společný by lhal.
- **Nevratná akce má potvrzení a pojmenovává následek**, ne „opravdu?". `ChatActionsState` proto
  drží `target` (sheet) a `pendingDelete` (dialog) zvlášť — mezi zavřením nabídky a potvrzením
  nesmí zůstat viset obojí.
- **Ztlumení se nastavuje celým objektem `ChatNotificationSettings`**, ne jedním polem. Ostatní
  hodnoty se musí přenést z aktuálního stavu chatu, jinak by se uživateli tiše přepsal třeba
  vlastní zvuk. `useDefaultMuteFor = false` je nutné: dokud je `true`, jede chat podle globálního
  nastavení a `muteFor` se ignoruje. Ztlumení „navždy" je `Int.MAX_VALUE` sekund.
- **„Ztlumit" se v nabídce rozbalí na doby** (hodina, 8 hodin, 2 dny, navždy), přímo v sheetu,
  ne v dalším — jsou to varianty jedné položky. Stejné doby jako v profilu chatu. Ztlumený chat
  má jen „Zrušit ztlumení"; zapisuje výslovnou nulu, ne návrat k výchozímu (ten je v profilu),
  takže projde i ztlumeným rozsahem.
- **Stav se po akci nepřepisuje lokálně.** U reakcí v konverzaci to smysl dávalo (mezi ťuknutím
  a potvrzením je vidět prodleva), tady se položka jen přeskládá — a předběhnout server by
  znamenalo riskovat, že se seznam rozejde se skutečností. Potvrzení chodí updaty
  (`chatNotificationSettingsUpdates`, `chatIsMarkedAsUnreadUpdates`, `chatPositionUpdates`).
- **Limit připnutých chatů je serverový** (`pinned_chat_count_max`, Premium ho zvedá). Při
  překročení vrátí TDLib chybu, která MUSÍ jít vidět — jinak to vypadá, že se nic nestalo.
  Proto má seznam chatů `SnackbarHost`.
- **Pořadí připnutých chatů se mění režimem uspořádání** („Změnit pořadí připnutých" pod
  „Odepnout" v nabídce nad připnutým chatem, od dvou připnutých; `PinnedReorder`, pravidla
  v `PinnedReorder.kt`, testy `PinnedReorderTest`). Tentýž režim jako u témat: hlavička
  s ✕ a „Hotovo", jen připnuté chaty s úchytem, zpět ho zruší bez uložení. Platí pro složku,
  ve které se skládá — hlavní seznam, archiv i složku Telegramu (`setPinnedChats` s jejím
  `ChatList`).
  - **`setPinnedChats` bere seznam jako NOVÝ STAV připnutých, ne jako přeskládání** — chat,
    který v něm chybí, odepne, chat navíc připne. Odesílá se proto vždycky CELÝ seznam
    připnutých a srovnaný s tím, co je připnuté teď (`pinnedOrderToSave`): mezitím odepnutý
    chat (jiné zařízení) se znovu nepřipne a nově připnutý zůstane nahoře.
  - **Na rozdíl od témat nese pořadí `order`**: nové pořadí přijde zpátky updaty pozic a seznam
    se přeskládá sám, nic se nedrží lokálně. Ověřeno i po `force-stop`.
  - **Ověřeno na emulátoru** ve složce „Plume test" (hlavní seznam testovacího účtu připnuté
    nemá): připnutí bota a testovací skupiny, přetažení, „Hotovo", pořadí po restartu, zpět
    bez uložení, položka se u jediného připnutého nenabízí; obojí se pak odepnulo. Archiv
    a hlavní seznam jdou toutéž cestou, ověřené zvlášť nejsou. **iOS jen přeložené.**
- **`Card(onClick = …)` umí jen krátké ťuknutí.** Dlouhý stisk potřebuje `combinedClickable`,
  takže se z karty stal `Surface` s vlastním klikáním.

### Výběr víc chatů

„Vybrat" v nabídce nad chatem přepne seznam (hlavní, složku i archiv) do výběru: hlavička s ✕
a počtem místo pilulky hledání, ťuknutí i dlouhý stisk chat přepíná a místo FAB menu je plovoucí
lišta s archivací, ztlumením a přečtením (`ChatSelectionTopBar`, `ChatSelectionToolbar`).
Pravidla jsou čisté funkce v `ChatSelection.kt` (testy `ChatSelectionTest`).

- **Vstup je položka nabídky, ne dlouhý stisk** — ten patří nabídce nad chatem, stejně jako
  u výběru zpráv. Uvnitř výběru dlouhý stisk jen přepíná: nabídka nad jedním chatem by z výběru
  vytrhla.
- **Každá akce je přepínač podle VŠECH vybraných** (jako v Gmailu): archivovat, dokud nejsou
  všechny v archivu, ztlumit, dokud nejsou všechny ztlumené, přečíst, když je aspoň jeden
  nepřečtený. Posílá se jen chatům, kterým se něco změní (`archiveTargets`…) — jinak by „Vrátit"
  po archivaci vracel i to, co se nehnulo.
- **Po akci výběr končí** a archivace má tentýž snackbar s „Vrátit" jako u jednoho chatu
  (`ArchiveChange` nese seznam chatů, plurál „2 chaty jsou v archivu") a zavře jejich otevřené
  konverzace. Chaty jdou postupně a selhání jednoho ostatní nezastaví.
- **Uložené zprávy ve výběru archivaci zšednou, nezmizí** (archivovat je nejde); sponzorovaný
  chat vybrat nejde vůbec — není uživatelův a má vlastní nabídku.
- **Značka výběru je u KAŽDÉHO chatu** (prázdné kolečko / fajfka v rohu avataru, místo tečky
  „online"), ne jen barva vybraného řádku: u jediného vybraného by barva neřekla, že jdou
  vybírat i další. Ve výběru barvu řádku nese výběr, ne chat otevřený v detailu vedle.
- **Tah do archivu je ve výběru vypnutý**, zpět výběr ukončí (`NavigationBackHandler`),
  přepnutí složky taky. Chat, který ze seznamu odejde, z výběru vypadne (`within`).
- **„Označit jako přečtené" shazovalo jen ruční příznak** (`toggleChatIsMarkedAsUnread(false)`)
  a chat s nepřečtenými zprávami zůstal nepřečtený — i z nabídky nad jedním chatem. Teď jde přes
  `ChatRepository.readChat`: nejnovější zpráva `viewMessages` se zdrojem `MessageSourceChatList`
  a `forceRead` (přečte i všechno před ní), zmínky, reakce a příznak zvlášť.
- **Odstup lišty patří DOVNITŘ `AnimatedVisibility`**, ne na její modifikátor: skrytá lišta by
  jinak nechala nad FAB (a v archivu nad spodkem seznamu) 24 dp prázdna.
- **Ověřeno na emulátoru** ve složce „Plume test" a v archivu (česky): výběr dvou chatů, zšedlá
  archivace s Uloženými zprávami, ztlumení a zrušení ztlumení, archivace se snackbarem
  „2 chaty jsou v archivu", vrácení výběrem z hlavního seznamu i z archivu, označení jako
  nepřečtené a přečtení dvou skutečných zpráv od bota (odznak zmizel, notifikace taky), zpět
  ukončí výběr. **Nejisté je čtení skupiny s tématy** (nepřečtené se tam vedou po tématech
  a nejnovější zpráva patří jen jednomu) **— neověřené, stejně jako iOS.**

### Blokování a nahlášení spamu

Spam se řeší třemi věcmi naráz — nahlásit, zablokovat, smazat — a jde to ze tří míst:
**pruh nad konverzací** u chatu od neznámého (`SpamBarCard`), **podmenu „Smazat"** v nabídce
nad chatem (a totéž jako sheet v profilu, `DeleteChatOptions`) a **„Nahlásit"** samostatně
(nabídka nad chatem, ⋮ v profilu, nabídka nad zprávou). Nahlášení je vždycky tentýž průvodce
(`ReportFlow` + `ReportSheet`, testy `ReportFlowTest`).

- **Blokování má JEDNU cestu, a je jí `ChatRepository`** (`setBlocked(chatId, …)` →
  `setMessageSenderBlockList` s `BlockListMain`). Profil chatu ji volá taky; dřív měl vlastní
  `ProfileRepository.setBlocked(userId, …)` a dvě cesty k témuž volání by se rozešly. Volající
  má chatId, ne userId — rozbalení na `ChatTypePrivate`/`ChatTypeSecret` patří za hranici.
- **Blokování chat NEMAŽE, a proto ho musí ohlásit snackbar.** V seznamu se po zablokování nic
  viditelného nezmění; bez potvrzení to vypadá, že ťuknutí nic neudělalo. Snackbar má „Vrátit",
  takže potvrzovací dialog před ním nemá co dělat — stejné pravidlo jako u archivace a odepnuté
  zprávy. (V profilu chatu dialog zůstává: tam se do blokování chodí vědomě a snackbar s „Vrátit"
  tam není odkud ukázat.)
- **Stav blokování a nahlášení se čte až při otevření nabídky** (`spamActions`), jako práva
  u nabídky nad zprávou: `ChatSummary` ho nenese a `getUserFullInfo` na každý chat v seznamu by
  byl dotaz za položku. Dokud odpověď nedorazí, položka se nekreslí — objevení ruší míň než
  zmizení. **Nezjištěno = nenabízet nic** (`SpamActions()`).

#### Nahlášení je průvodce

- **`reportChat` NENÍ jedno volání.** Vrací `ReportChatResult`: `Ok`, nebo `OptionRequired`
  (volba důvodu), `TextRequired`, `MessagesRequired`. Jedním krokem (prázdné `optionId`
  i `messageIds`) projde jen chat se spamovým action barem (`ChatActionBarReportSpam`,
  `ChatActionBarReportAddBlock`); jinde je úvodní prázdný požadavek jen OTÁZKA — server vrátí
  důvody a nic se nenahlásí. Díky tomu jde průvodce ověřit na emulátoru bez skutečného nahlášení.
- **Dřív se proto „Nahlásit spam" nabízelo jen s action barem, a to byla ta chyba.** Action bar
  zmizí, jakmile uživatel protějšku cokoli odpoví, a bez průvodce pak nahlásit nešlo nic — v potvrzení
  mazání zbylo jen „Zablokovat taky". Teď se nabízí podle `Chat.canBeReported` NEBO action baru
  (`SpamActions.canReport`) a jen popisek se liší: s action barem „Nahlásit spam" (hotovo jedním
  ťuknutím), jinak „Nahlásit…" / „Nahlásit — vybereš důvod". Otázka po „Smazat" musí být
  ohlášená dopředu, jinak by vyskočila nečekaně.
- **Důvody i otázky píše server**, v jazyce účtu („What is wrong with this bot?", „Spam", „Scam
  or fraud"…). Vlastní seznam by se rozešel s tím, co server přijme; aplikace jen kreslí.
- **Nahlášení zprávy posílá `messageIds` v KAŽDÉM kroku**, ne jen v prvním (`ReportRequest`
  drží všechno, co už uživatel vybral). U alba celé album, jako mazání. Nabízí se podle
  `MessageProperties.canReportChat` — vlastní zprávy a Uložené zprávy ho nemají.
- **`MessagesRequired` z nabídky nad chatem dodat nejde** — průvodce řekne, kudy vede cesta
  (nabídka nad zprávou), místo slepé uličky.
- **Nahlásit jde i celý výběr víc zpráv** (vlajka v liště výběru, `MessageSelection.canReport`,
  `reportSelection`): tentýž průvodce s ids všech vybraných, jen když server dovolí nahlásit
  KAŽDOU z nich — stejné skládání práv přes `all` jako u přeposlání a mazání. Pozor, že
  `canReportChat` rozhoduje server: v chatu s botem ho má i VLASTNÍ zpráva (ověřeno — s `/help`
  ve výběru vlajka nezšedla), takže „vlastní zprávy nahlásit nejde" platí jen tam, kde to
  server řekne. Vlajka stojí před košem: spam se nejdřív nahlásí, pak maže.
- **Zavřený průvodce nesmí ožít** odpovědí na krok, který byl na cestě (`generation`).
- **`ReportFlow` je třída nad `CoroutineScope`, ne ViewModel.** Seznam chatů, profil
  i konverzace ji mají každý ve svém ViewModelu a kreslí `ReportHost` (sheet + snackbar).
  Server dostává jako funkci, takže se pořadí kroků testuje bez TDLib.

#### Úklid spamu a pořadí

- **Pořadí je nahlásit → zablokovat → smazat a na první chybě se ZASTAVÍ.** Nahlášení běží
  průvodcem a teprve jeho úspěch spustí zbytek (`ReportFlow.thenAfterReport`); zablokovat →
  smazat drží `cleanUpSpam` (testy `SpamCleanupTest`). Nahlásit jde jen chat, který ještě
  existuje, takže mazání musí být poslední; zrušený průvodce nesmaže nic; a kdyby se chat smazal
  i po selhaném blokování, bot by dál psal a chat, na kterém by šlo akci zopakovat, by byl pryč.
- **Smazání je podmenu v sheetu, ne dialog** (`CleanupOptions`). Dialog nad sheetem zavřel
  nabídku, ze které vyšel, a volby v něm stály mimo to, čeho se týkaly. Rozbalené „Smazat" nese
  větu s následkem, zaškrtávátka a plné chybové tlačítko — potvrzením je rozbalení a ťuknutí na
  tlačítko v něm, jako doby ztlumení pod „Ztlumit". Profil má z ⋮ totéž jako samostatný sheet
  (`ChatDeleteSheet`), protože do obyčejného menu se podmenu rozbalit nedá.
- **Sheet s podmenu je jen rozbalený (`Hidden, Expanded`) a scrolluje se, a podmenu se dotáhne
  do pohledu** (`snapshotFlow { scroll.maxValue }`). Rozbaluje se na KONCI nabídky, takže
  u polovičního sheetu by tlačítko smazání skončilo pod okrajem obrazovky.
- **Zaškrtávátka jsou výchozí vypnutá** a blokovanému se „Zablokovat taky" nenabízí. Zablokovat
  někoho omylem při mazání chatu je horší než ťuknutí navíc.

#### Pruh nad konverzací (`ChatActionBar`)

- **Tím Telegram spam řeší primárně**: nabídne se sám, jakmile cizí napíše. Kreslí se jen
  `ReportSpam` (Nahlásit spam), `ReportAddBlock` (Zablokovat, Nahlásit spam, Přidat kontakt)
  a `AddContact`; pruhy pro sdílení čísla, pozvání a žádost o vstup ne (`SpamBar`).
- **Živý přes `UpdateChatActionBar`**, odběr před prvním čtením; update nese pruh, ale čte se
  celý chat — kdo jde zablokovat, se pozná z typu chatu.
- **Zablokovat i Nahlásit otevřou sheet s volbami** (`SpamBarSheet`): hlavní akce je tlačítko,
  zbylé dvě zaškrtávátka (zablokovat / nahlásit a „Smazat i chat"). Tak to dělá i oficiální
  klient, jen dialogem. Smazaný chat konverzaci zavře (`chatGone`).
- **Přidat kontakt bere jméno z účtu protějšku** a svoje číslo NEsdílí (`sharePhoneNumber =
  false`) — to je samostatné rozhodnutí o soukromí. ✕ zavře pruh napořád (`removeChatActionBar`).
- **Nahlásit jde člověka JEN přes pruh.** `reportChat` pustí soukromý chat pouze s botem
  (`UserManager::can_report_user`: `is_bot`), skupinu nikdy, supergroup a kanál jen nevlastníkovi.
  Bez spamového pruhu u člověka nezbývá než zablokovat a smazat — a oficiální klient to má
  stejně. **Past:** když TDLib pruh zná a spam v něm není, prázdný `reportChat` vrátí
  `ReportChatResultOk` a NIC NEODEŠLE. Kdo by se ptal jen na výsledek, hlásil by úspěch.
- **Pruh se neztrácí časem, ale akcí na účtu.** TDLib si ho zjistí sám, jakmile chat poprvé
  vidí (`users.getFullUser` → `peerSettings`), a lokálně ho schová jen po nahlášení
  a zablokování. Jinak ho zhasne server: odpověď, přidání kontaktu nebo křížek z KTERÉHOKOLI
  klienta. Kdy ho server vůbec nastaví, dokumentované není.
- **Automaticky archivovaný chat má v pruhu „Vrátit mezi chaty" MÍSTO „Nahlásit spam"**
  (`canUnarchive`), jak to předepisuje dokumentace Telegramu. Nahlásit jde dál zaškrtávátkem
  v sheetu Zablokovat. Vrácení jsou DVĚ volání souběžně (`unarchiveFromSpamBar`):
  `addChatToList(ChatListMain)` a upozornění chatu na výchozí (`defaultChatNotificationSettings`,
  sdílené s obnovením výjimky v Nastavení) — server chat při archivaci i ztlumil. Pruh si pak
  TDLib upraví sám: nahlášení a blokování zmizí, přidání kontaktu zůstane.
- **Konverzace otevřená z archivu se po vrácení PŘESTĚHUJE do hlavního seznamu**
  (`moveConversationToMain`, testy `ChatsStackTest`), nezavře se. Zavřít by uživatele vyhodilo
  z chatu, o který právě řekl, že stojí; nechat by znamenalo konverzaci vedle archivu, ve kterém
  chat už není. Z hlavní větve se odebere i detail jiného chatu (profil, úprava) — jinak by zpět
  z vráceného chatu vedlo do profilu toho předchozího.
- **Údaje o protějšku jsou pod nadpisem pruhu** (`CounterpartAccount`, testy
  `CounterpartAccountTest`): od kdy je na Telegramu, země podle čísla s vlajkou a kdy změnil
  jméno a fotku. Chodí jen u `ReportAddBlock`, když cizí napíše poprvé. Neznámé hodnoty TDLib
  posílá jako nulu nebo prázdný řetězec, takže se vynechají — a bez jediné se neukáže nic.
  „Od března 2021" stojí na tvaru měsíce za dnem (`Month.name()`), protože po „od" je v češtině
  stejný 2. pád; nový sloupec nominativů by byl na jedno místo.
- **Vlajka je z kódu země, ne z tabulky** (`flagEmoji`): písmena se posunou na regionální
  indikátory a písmo je složí. Název země překládá systém (`localizedCountryName`), jako při
  přihlášení.
- **Pruh a připnuté zprávy se měří spolu** (jeden `Column` s `onSizeChanged`), seznam se odsazuje
  o výšku obou. Spam stojí nad připnutou zprávou.
- **Pruh nad konverzací je obecný `ChatBar`, ne jen spam** (`ChatRepository.chatBar`):
  `SpamBar` (spam, blokování, kontakt), `SharePhoneBar` (vzájemný kontakt nevidí vlastní číslo
  → `sharePhoneNumber`, po potvrzení dialogem — odchází osobní údaj) a `JoinRequestBar`
  (správce skupiny nebo kanálu, kam uživatel žádal o vstup — jinak by zpráva od něj vypadala
  jako spam). Nespamové pruhy kreslí `ChatNoticeBarCard` týmž tvarem; křížek je u všech
  `removeChatActionBar`. `InviteMembersBar` (čerstvě založená skupina) vede na výběr lidí,
  viz „Přidání členů".
- **Den z `asDayLabel` nepatří doprostřed věty** — je nadpisový („Včera"). U žádosti o vstup
  stojí proto na vlastním řádku za dvojtečkou („Žádost odeslána: Včera").
- **Text a tlačítka pruhu jsou v jednom sloupci, křížek vedle.** V řádku s `IconButton` (48 dp)
  zbyla mezi textem a tlačítky mezera a pruh vypadal rozpadle.

#### Seznam blokovaných v Nastavení

Obecné → Soukromí → Blokovaní (řádek s počtem, `BlockedSettings`) otevře obrazovku
`BlockedSendersScreen` (`BlockedSendersRoute`, vrší se nad sekci jako úprava profilu). Je to
JEDINÉ místo, kde jde zrušit blokování někoho, s kým uživatel chat smazal — nabídka nad chatem
i profil stojí na chatu, a ten po smazání není.

- **Je to tentýž `ChatPicker` jako kontakty, přeposílání a sdílení** — hlavička, `SearchPill`,
  bloky zaoblených řádků, prázdný stav. **První verze byla sheet s holými `ListItem`y a tlačítkem
  „Odblokovat" u každého řádku**: vedle kontaktů vypadala jako jiná aplikace a měla jiné
  ovládání. Blokovaný se do výběru dostane jako `ChatSummary` (jméno, fotka, druh — zbytek jsou
  výchozí hodnoty bez významu). **Obecně: seznam lidí nebo chatů patří do `ChatPicker`, i když
  z něj nic nevybíráš.**
- **Akce nad položkou jako všude jinde: ťuknutí otevře nabídku** (sheet s tváří, jménem
  a „Odblokovat", tvarem jako nabídka nad chatem), vratná akce má snackbar s „Vrátit".
  `ChatPicker` kvůli tomu bere vlastní `snackbarHostState` — výchozí umí jen chyby.
- **Blokovaný je ODESÍLATEL, ne uživatel** (`getBlockedMessageSenders` vrací `MessageSender`):
  blokovat jde i kanál nebo skupinu. `BlockedSender.id` drží konvenci odesílatele zprávy — kladné
  id uživatel, záporné chat — a `setSenderBlocked` z něj skládá `MessageSender` zpátky.
  `setBlocked(chatId)` z nabídky nad chatem jde přes tutéž funkci, takže cesta k
  `setMessageSenderBlockList` zůstala jedna.
- **Čte se celý seznam naráz** (`collectBlockedSenders`, stránky po 100, testy
  `BlockedSendersStateTest`) — hledání je lokální a počet v Obecných má sedět. Server vrací jen
  id, jména a fotky se dočítají lokálně; kdo se dočíst nedá, ze stránky vypadne, proto se
  **konec pozná z `totalCount` od serveru, ne z délky stránky**. Tentýž odesílatel na dvou
  stránkách zůstane jednou (dva stejné klíče by `LazyColumn` shodily) a selhaná stránka je
  selhání celku — půlka seznamu by tvrdila, že zbytek blokovaný není.
- **Odblokovaný zmizí ze seznamu hned a pozdější čtení ho tam nevrátí**, dokud uživatel nedá
  „Vrátit" (`unblockedIds`, testy `BlockedSendersViewModelTest`). Na emulátoru zůstal odblokovaný
  bot po novém přečtení v seznamu, zatímco počet v Obecných už seděl — příčina (odpověď serveru
  těsně po změně?) zjištěná není, test proto hlídá chování, ne domněnku.
- **Počet v Obecných se čte znovu při každém návratu** (`LifecycleResumeEffect`) — po odblokování
  na obrazovce seznamu by jinak zůstal starý. Podtitul je počet ÚČTŮ („14 účtů"), ne
  „14 zablokovaných": pod nadpisem „Blokovaní" by to slovo stálo dvakrát.
- **Smazaný účet má v avataru iniciály „SÚ"** — `ChatPicker` je bere z názvu. Nehezké, ale
  vlastní avatar kvůli jednomu druhu řádku by znamenal rozbít společný řádek výběru.
- **„Zablokovat uživatele" je blok nad seznamem** (`header` výběru), výběr je další obrazovka
  (`BlockSenderRoute`, `BlockSenderScreen`) nad seznamem blokovaných: kontakty a při hledání
  ostatní známé chaty (`blockCandidates`, testy `BlockCandidatesTest`). **Jen soukromé chaty**
  (lidé a boti), bez Uložených zpráv a bez už blokovaných — kanál nebo skupinu jde zablokovat
  jako odesílatele u její zprávy, jako položka seznamu by slibovala něco, co blokování neumí.
- **Zablokování z výběru se potvrzuje dialogem**, přestože nabídka nad chatem má jen snackbar
  s „Vrátit": obrazovka po zablokování odchází, takže snackbar není kde ukázat, a ťuknutí vedle
  v dlouhém seznamu kontaktů by zablokovalo někoho jiného. Stejně jako v profilu.
- **Seznam blokovaných se čte při KAŽDÉM složení** (`LaunchedEffect`), ne v `init` ViewModelu.
  Pod výběrem k zablokování složený není (NavDisplay kreslí jen horní scénu), takže návrat ho
  složí znovu a nově zablokovaný je v něm hned; řádek v Obecných čte počet sám při návratu.
- **Ověřeno na emulátoru** (česky): 14 blokovaných na skutečném účtu jen prohlížením, hledání
  „sop" bez diakritiky, nabídka po ťuknutí zavřená bez akce; na testovacím botovi zablokovat
  z nabídky nad chatem → v seznamu odblokovat (řádek zmizí, snackbar) → „Vrátit" (řádek zpátky)
  → odblokovat → 14 účtů. **Ověřuj ťuknutím podle TEXTU z `uiautomator dump`, ne podle
  souřadnic**: seznam chatů se na živém účtu přeskládá s každou zprávou a heads-up notifikace
  zakryje tlačítko účtu — pevná souřadnice pak otevřela cizí konverzaci a označila zprávy jako
  přečtené.

#### Ověření

- **Na emulátoru** (chat s testovacím botem, anglicky): „Report…" v nabídce nad chatem i v ⋮
  profilu, podmenu smazání se zaškrtávátky „Block them too" a „Report — you'll pick a reason",
  sheet smazání v profilu, „Report" v nabídce nad zprávou; průvodce ukáže důvody od serveru
  („What is wrong with this bot?" / „…this message?") a zavření nic neodešle.
- **Pruh nad konverzací se ověřoval PODSTRČENÝM stavem** (`spamBar` v `ConversationViewModel`
  pro chat s testovacím botem nahrazený pevným `SpamBar` i s `CounterpartAccount`, necommituje se)
  — chat od cizího na testovacím účtu není. Vzhled pruhu, údaje o protějšku (anglicky) a sheet
  „Report spam?" ověřené jsou, **skutečné `UpdateChatActionBar` ne.** Vrácení z archivu se na
  archivovaném botovi ťuknout dá, jenže podstrčený pruh zůstává i po něm.
- **Skutečné nahlášení, smazání s volbami a přidání kontaktu ověřené NEJSOU** — nahlášení by šlo
  moderátorům Telegramu na vlastního testovacího bota.

## Konverzace

### Bloky seznamu

Seznam zpráv se skládá z **bloků** (`ConversationItem`), které nesou svoje data a **samy se
vykreslí** — `LazyColumn` jen volá `Content`, žádný `when` nad druhy. O tom, JAKÝ blok vznikne,
rozhoduje jediná čistá funkce `conversationItems()` (testy `ConversationItemsTest`). Cílový stav
a postup přestavby: [docs/architektura-konverzace.md](docs/architektura-konverzace.md).

- **Všechno, co potřebuje znát sousedy, počítá builder**, ne položka v kompozici: předěly,
  seskupení bublin, profilovky, odznak přečtení, citace. Uvnitř `itemsIndexed` se to netestovalo.
- **Builder se volá v kompozici (`remember`), ne ve ViewModelu.** Ukotvení nové zprávy
  (`requestScrollToItem(0)`) musí vidět nový seznam v téže kompozici; tok z ViewModelu by šel
  o dispatch pozadu.
- **Klíče `remember` jsou jen pole stavu, ze kterých builder čte.** S celým `state` by se seznam
  přestavoval i při každém kroku průběhu stahování.
- **Lambdy v datech bloku nejsou** — rozbily by `equals` a tím přeskakování nezměněných položek.
  Obsluhu a rychle se měnící hodnoty (pozice hlasovky, průběh stahování, odkrývání časů) dává
  `ConversationItemScope`, a to jako funkce čtené až v UI, které je kreslí.
- **`Content` je nad `LazyItemScope`**, takže si blok sám volí `animateItem` — cíl letu odeslané
  zprávy se nesmí zjevovat prolnutím, sponzorovaná karta se neanimuje vůbec.
- **Zprávy sdílejí rám, obsah si kreslí samy.** `MessageItem` implementuje `Content` jednou
  přes `MessageFrame` (tvář, tah na odpověď, zvýraznění, reakce, doručení, kotva nabídky)
  a blok dodá jen `Body`. Druhy: `BubbleMessageItem`, `LargeEmojiMessageItem`,
  `StickerMessageItem`, `MediaMessageItem`, `LinkPreviewMessageItem` (`features/conversation/items/`).
- **Bublina není příznak rámu, ale sdílený kus** (`MessageBodyScope.Bubble`). Rám kreslí jen
  průhlednou plochu s ořezem a klikáním ve tvaru bubliny — ten potřebuje i nabídka pro díru
  v závoji —, pozadí přidá `Body`, když ho chce. Bez bubliny rám obsahu výslovně podá
  `onSurface`: barva popředí musí patřit ploše, ne bublině, která tam není.
- **Nový druh zprávy = nová třída + jedna větev v `messageItem()`.** Nic dalšího se nemění; kdyby
  bylo potřeba sáhnout do rámu nebo do jiného bloku, je to znamení, že to do rámu nepatří.
- **Akce jsou data** (`MessageAction`), a vede z bloků jediná cesta: `ConversationItemScope.onAction`
  → `ConversationViewModel.onAction`. Navigaci (`OpenMedia`) a sheet sady (`OpenStickerSet`)
  zachytí obrazovka dřív; ViewModel o navigaci neví. `messageId` jde vedle akce, ne v ní — u alba
  se ťuká na konkrétní fotku.
- **Nabídku skládají dvě strany a každá ví něco jiného.** Blok (builder, `menuActions()`) ví, co
  zpráva JE: holý text jde upravit, bez textu není co kopírovat, samolepka jde do oblíbených,
  připnutá se odepíná. Server ví, co se SMÍ — a to se načítá až při otevření. Položka
  (`MessageMenuAction.presentation`) sama řekne, jestli ji server dovolil a jak vypadá; nabídka
  jen projde seznam. Nová položka = nový objekt + místo v `menuActions()`.
- **Kandidáti se počítají ze SKUTEČNÉ zprávy, ne z `MessageRow.display`.** U alba má `display`
  přílohu vynulovanou (nese ji mřížka), takže by popisek alba vypadal jako upravitelný text.
- **Detektor gesta čte id zprávy přes `rememberUpdatedState`.** Vzniká jednou za život položky,
  ale odeslaná zpráva po potvrzení změní id (klíč položky ne); odpověď swipem by jinak mířila
  na dočasné id, které už neexistuje.
- **Servisní oznámení přerušuje blok z OBOU stran** (`continues`). Dřív se hlídala jen novější
  zpráva, takže bublina pod oznámením měla stažené nároží, jako by na něj navazovala. Našel to
  až test nad builderem — v kompozici to nikdo neověřoval.

### Obsah konverzace

Hranice je [ConversationRepository]; na rozdíl od [ChatRepository] je **bezstavová**. Stránkovací
stav drží `ConversationViewModel` — je tím testovatelný bez TDLib a přirozeně vázaný na životnost
obrazovky, ne na aplikaci.

- **Rozestup je dvojí: 4 dp uvnitř skupiny, 12 dp mezi skupinami.** Navazující zprávy téhož
  odesílatele patří k sobě, takže se drží u sebe — tvar bublin to už rozlišuje staženým nárožím
  (`continuesGroup`) a mezera tomu jen dává prostor. Mezera mezi skupinami se přidává v bublině,
  ne v `verticalArrangement`: seznam o sousedech položky nic neví.
- **Zprávy se drží od nejnovější po nejstarší**, protože je tak vykresluje
  `LazyColumn(reverseLayout = true)`. `reverseLayout` obrací i význam indexů: index 0 je nejnovější
  zpráva dole, stránkování do minulosti se tedy spouští na **konci** seznamu.
- **Řadí se podle id.** Id zpráv je v rámci chatu monotónně rostoucí, takže nahrazuje časové řazení
  a je stabilní i u zpráv se stejnou sekundou.
- **Konec historie se pozná jen podle prázdné odpovědi.** TDLib žádný příznak „jsi na začátku" nedává.
- **Odeslaná zpráva se do seznamu nepřidává ručně.** TDLib ji vrátí přes `newMessages` se skutečným
  id a časem; ruční přidání by znamenalo duplicitu.
- **`getChatHistory(onlyLocal = false)`** — s `true` by se po doscrollování na konec lokální cache
  vrátil prázdný seznam a vypadalo by to jako konec historie.
- **`viewMessages(forceRead = true)`** — bez toho TDLib označí za přečtené jen to, o čem si myslí,
  že to uživatel opravdu viděl.
- **`UpdateDeleteMessages.isPermanent == false`** znamená jen uvolnění z cache, ne smazání pro
  uživatele — takovou zprávu z obrazovky odstranit nesmíme.

### Médium bez bubliny

Fotka, video a album se kreslí přímo na plochu konverzace, bez bubliny (`showsBareMedia`
v `MessageRows.kt`, testy v `MessageRowsTest`). Bublina kolem snímku nemá co ohraničovat — je to
obrázek sám o sobě — a jen přidávala rám, ze kterého konverzace ztěžkla. Stejně to dělá Google
Messages.

- **Popisek na plochu NEPATŘÍ.** Text bez pozadí se na ploše konverzace ztratí, takže dostane
  vlastní kontejner v barvě bubliny pod snímkem. „Bez bubliny" platí pro obrázek, ne pro text u něj.
- **Snímek s popiskem je JEDNA KARTA**, jako v Google Zprávách a jako náhled odkazu: tvar bubliny
  nese celá karta (`clip(shape)`), uvnitř nejsou spáry ani vlastní zaoblení a **popisek má přesně
  šířku snímku** (`MediaWithCaption`). Dřív to byla skupina dvou kusů se spárou 2 dp a staženými
  nárožími a popisek byl široký jako jeho text — u krátkého čouhalo zaoblené nároží a vypadal jako
  další, useknutá zpráva, u dlouhého se roztáhl vedle zúžené fotky.
- **Šířku popisku dává vlastní `Layout`, ne `IntrinsicSize`.** Snímek je v `BoxWithConstraints`
  a ten intrinsické míry neumí (viz „Gotchas"); layout změří snímek a popisku dá jeho šířku napevno.
- **Karta se zarovnává ke straně odesílatele** (`horizontalAlignment` podle `isOutgoing`) a užší
  než řádek je VŽDYCKY: strop 82 % platí i tady (viz „Vzhled konverzace"). Dokud šla fotka na
  šířku přes celý řádek, bylo zarovnání k ničemu — okraj vyšel na obou stranách stejný.
- **Snímek bez popisku přebírá tvar bubliny** (`mediaShape`), aby navazoval na sousedy v bloku;
  s popiskem je rovný a ořezává karta.
  Náhled souboru v řádku si drží svých 12 dp — velké zaoblení bubliny by z 48dp čtverečku udělalo
  kolečko.
- **Jméno odesílatele nad holým snímkem si nese odsazení i do stran** (`MESSAGE_PADDING_H`,
  jako text v bublině). Celá zpráva je ořezaná tvarem bubliny (`MessageBox`, kvůli vlnce
  zvýraznění a díře v závoji nabídky), takže jméno přilepené k levé hraně ukouslo zaoblené
  nároží první písmeno („ilipHorky"). Totéž u karty náhledu odkazu. **Cokoli, co leží v rohu
  zprávy bez bubliny, se musí odsadit o zaoblení** — ořez nekončí u snímku.
- **Citace si bublinu vynutí.** Bez pozadí se ztratí, tentýž důvod, proč si ji drží i zpráva
  z pár emoji. Náhled odkazu má vlastní kartu (viz „Náhled odkazu jako karta").
- **Soubor, hlasovka ani samolepkový popis na plochu nejdou.** Jsou to řádky s textem a ikonou,
  ne obrázek — pozadí je to jediné, co je drží pohromadě.
- **Reakce a odznak doručení plochu snesou.** Čipy reakcí mají vlastní pozadí a odznak je pod
  bublinou už od dřívějška; pod holým snímkem tedy nebylo co řešit.
- **Barvy bubliny se počítají i tehdy, když bublina není** (`bubbleColor` / `onBubbleColor`).
  Kontejner popisku je potřebuje, a odkaz v popisku odchozí zprávy musí vzít barvu popředí —
  `primary` na `primary` by byla neviditelná, tatáž past jako u citace.

### Náhled odkazu jako karta

Náhled odkazu u zprávy bez přílohy a citace je **jedna karta ze dvou částí**: nahoře text zprávy
v barvě bubliny, dole popis webu (web, titulek, popis) v tónované ploše. S **velkým obrázkem**
stojí obrázek mezi nimi přes celou šířku, **malý** je čtvereček vpravo v popisu. Pravidlo
`showsLinkPreviewCard` (a `LinkPreview.showsLargePhoto`) v `MessageRows.kt`, blok i kreslení
v `items/LinkPreviewMessageItem.kt`.

- **Dvě plochy, ne proužek.** Malý náhled byl dřív uvnitř bubliny oddělený proužkem akcentu
  vlevo (převzatým z citace) — jazyk M2. Odlišná plocha dělá totéž bez čáry navíc a karta
  vypadá stejně s velkým obrázkem i bez něj.
- **Bez velkého obrázku je karta široká podle obsahu**, nejvýš jako každá jiná zpráva
  (`MESSAGE_MAX_WIDTH_FRACTION`), a obě části mají stejnou šířku (`IntrinsicSize.Max`). Smí to jen
  proto, že v kompaktní kartě nic se `SubcomposeLayout` není — velký obrázek (`BoxWithConstraints`)
  má vlastní větev se šířkou z `photoFrame`.
- **Zvýrazněná příchozí zpráva přejde do `tertiaryContainer`** — stejné barvy jako popis. Popis
  (i citace nad odpovědí) se proto posune o kus k barvě popředí té plochy (`surfaceApart`,
  `MessageBodyScope.insetColor`), jinak by obě části splynuly. **Posouvá se k `on*` roli, ne
  k `tertiary`:** `tertiary` je přesně ta barva, na kterou se spolehnout nedá — v paletě se
  zvýšeným kontrastem leží na téže světlosti jako `tertiaryContainer`, takže se karta slila
  v jednu plochu i u zvýrazněné ODCHOZÍ zprávy (bublina `tertiary`, popis `tertiaryContainer`).
  Podmínka proto není „příchozí a zvýrazněná", ale změřený kontrast vůči bublině.
- **Posun 0,18 vyšel z porovnání s tím dřívějším** (0,22 k `tertiary`): ten dával odstup ploch
  1,51 v tmavém a 1,35 ve světlém tématu, tenhle 1,49 a 1,35 — vzhled v běžné paletě zůstává
  a text na posunuté ploše je pořád nad AA. Větší posun by ho ukrajoval, protože míří přesně
  k barvě toho textu.
- **U zprávy s citací nebo přílohou zůstává náhled v bublině** (`LinkPreviewCard`), a to jako
  tónovaný kontejner stejný jako citace (`QUOTE_CONTAINER_ALPHA`, `QUOTE_CORNER`), ne proužek.

- **Bez spár.** Dřív to byly tři kusy se spárami a staženými nárožími, stejný jazyk jako fotka
  s popiskem — a vypadalo to jako tři zprávy. Sdílený odkaz je ale jedna věc: tvar bubliny nese
  celá karta (`clip(shape)` na sloupci), části uvnitř jsou rovné.
- **Text zprávy má barvu bubliny, popis webu `tertiaryContainer`.** Text je zpráva sama a odchozí
  zprávy se od příchozích poznají barvou (`primary`); v jiné roli by se strany lišily jen
  zarovnáním — past, kvůli které odchozí bublina přestala být `primaryContainer`. Popis je cizí
  vložený obsah, takže se od textu musí lišit; terciární role je v M3 na doplňkové prvky
  a nesplyne s žádnou z barev bublin. Název webu je `tertiary` na `tertiaryContainer`.
- **Karta jde k hraně řádku, bez vnitřního odsazení.** Dřív byla uvnitř průhledné „bubliny"
  s odsazením 14 dp a od ostatních zpráv ustupovala.
- **Šířku určuje obrázek.** Text i popis se na ni natáhnou (`Modifier.width(frame.width)` na
  sloupci), takže karta nemá roztřepený okraj.
- **Barvu z obrázku NEVYTAHUJEME.** Google Messages dává pod snímek pruh v jeho barvě, ale TDLib
  u náhledu žádnou barvu neposílá a kvantizace z bitmapy by znamenala keš na `fileId` a počítání
  mimo hlavní vlákno.
- **Citace a příloha si bublinu vynutí** — jako u holého média. Náhled u zprávy s fotkou je
  stejně jen doplněk k příloze, ne hlavní obsah.

### Rámeček fotky v bublině

Fotka a video se **zužují, neořezávají** (`photoFrame` v `MediaFrame.kt`, testy `MediaFrameTest`):
z poměru stran se spočítá výška, zastropuje se, a když se strop uplatní, dopočítá se šířka zpátky
jako `výška × poměr`.

- **Ořez byl ta chyba, ne strop.** Předtím se výška jen usekla stropem a šířka zůstala na
  `fillMaxWidth()`, takže `ContentScale.Crop` ze svislého snímku obrazovky (poměr kolem 1:2) nechal
  jen **prostřední pruh** — nešlo z něj přečíst ani co na něm je. Zvednutí stropu samo nepomůže:
  vysoká fotka přes celou šířku bubliny bude vždycky vyšší než rozumná bublina. Fotka proto zabírá
  méně šířky a je vidět celá, jako v Telegramu i Google Messages.
- **Podlaha šířky existuje** (`MIN_PHOTO_WIDTH`). U extrémně vysokého snímku by dopočítaná šířka
  vyšla na pár desítek dp a z fotky by byl nečitelný proužek; pod podlahou se poměr stran obětuje
  a `Crop` fotku ořízne do stran. Proto `ContentScale.Crop` zůstává, i když rámeček poměr stran
  jinak respektuje.
- **Pravidlo je čistá funkce mimo composable**, jako `readBadgeRow` nebo `formatFileSize`. Uvnitř
  `@Composable` by se netestovalo a přitom je to samý hraniční případ: neznámé rozměry (nula, NaN),
  panorama, úzká bublina.
- **Počítá se ručně v `BoxWithConstraints`**, ne přes `aspectRatio` — viz „Gotchas".
- **Popisek se zalomí na šířku fotky** (viz „Médium bez bubliny"), takže zúžená fotka s dlouhým
  popiskem („Screenshot (18. 9. 2026 5:23:34)") nenechá vedle sebe prázdné místo.
- **Fotka s popiskem využije celou nabízenou šířku** (parametr `fillWidth` u `photoFrame`).
  Popisek má šířku fotky, a vysoký snímek zúžený na pár set bodů lámal text po dvou třech
  slovech — delší popis byl nečitelný. S popiskem se proto fotka rozšíří na celou dostupnou
  šířku (kterou shora hlídá strop rámu 82 % řádku), výška zůstane na stropu a obrázek se
  **ořízne na střed** (`Crop`, poměr stran bez deformace), jako v Telegramu. Bez popisku se
  dál jen zužuje a je vidět celá. Totéž platí pro velký obrázek náhledu odkazu: text zprávy
  i popis webu mají jeho šířku.
- **Album ve čtvercích zůstává.** Mřížka je vědomé rozhodnutí (viz „Alba"), takže pravidlo platí
  jen pro samostatnou fotku a video; řádek alba s jednou fotkou se ořezává dál.

### Alba

Telegram **víc příloh v jedné zprávě nenese.** Album jsou samostatné zprávy (každá s vlastním id,
popiskem, reakcemi) spojené `mediaAlbumId`, odeslané naráz přes `sendMessageAlbum` (2–10; fotky
s videi dohromady, dokumenty a audio jen se stejným typem). Konverzace je kreslí jako jednu bublinu.

- **Položky seznamu jsou ŘÁDKY, ne zprávy** (`groupAlbums` → `MessageRow`). Sousední zprávy téhož
  alba tvoří jeden řádek. Na řádcích stojí sousedé (předěly dne, seskupení bublin, profilovky)
  i index v `LazyColumn` — skok na zprávu proto hledá řádek, který ji obsahuje.
- **Klíč alba je klíč jeho NEJSTARŠÍ zprávy.** Novější fotky dorážejí za ní a klíč se nezmění.
- **Popisek, reakce, odpověď a nabídka patří zprávě s popiskem** (`primary`), bez popisku
  nejstarší. Čas bubliny je čas nejnovější fotky a stav doručení „nejhorší" z alba — album je
  odeslané, až když jsou odeslané všechny jeho zprávy.
- **Jen „Odpovědět" z nabídky míří na PODRŽENOU fotku** (`MessageMenu.pressedMessageId`,
  `replyTarget`, test `odpovedMiriNaPodrzenouFotkuAlba`). Nabídka se otevírá pro zprávu
  s popiskem, takže odpověď na třetí fotku citovala popisek alba. Dlaždice proto hlásí, na
  které fotce stisk začal (`ShowMenu.pressed`), a odpověď vezme ji. Reakce, přeposlání,
  mazání a citace označeného textu dál platí pro celé album — text patří zprávě s popiskem;
  tah na odpověď cituje album (nemá z čeho poznat fotku). Ověřeno Bot API:
  `reply_to_message` míří na prostřední fotku bez popisku.
- **Přečtení a mazání se týkají celého alba.** Z viditelné položky přijde jen klíč nejstarší
  zprávy; bez rozšíření na celé album by zbylé fotky zůstaly napořád v počtu nepřečtených
  a „Smazat" by smazalo jednu fotku z bubliny.
- **Mřížka, ne carousel.** Album má být vidět celé a vodorovný tah carouselu by se pral se
  swipem na odpověď. Řádky po 1–3 podle pravidel (`albumRowSizes`), ne mozaika podle poměrů stran —
  vypadá vyrovnaně bez ohledu na obsah. Samotná fotka na řádku podle poměru stran se stropem,
  víc fotek čtverce. Zaoblení nese celá mřížka, dlaždice mají jen spáry.
- **Album dokumentů nebo audia je seznam pod sebou** — do mřížky patří jen fotky a videa s náhledem.
- **Ťuknutí na dlaždici otevře tu konkrétní fotku** (se sdíleným prvkem); prohlížeč pak listuje
  fotkami chatu, album v tom nijak zvlášť neřeší.
- **Popisek alba z pár emoji není velké emoji** — bublina alba si pozadí drží vždycky.
- **Album na hraně načtené historie může být neúplné**; dotažením starší stránky se k němu zbytek
  připojí.

### Vestavěná galerie (Android)

Sponka → „Fotka nebo video" otevře na Androidu 14 se SDK Extensions 15 **galerii v sheetu nad
vstupním polem** (`androidx.photopicker`, hranice `core/ui/EmbeddedMediaPicker.kt`), ne systémovou
obrazovku výběru. Je při vybírání vidět, do jakého chatu se posílá, a vybrané fotky naskakují
rovnou do pruhu nad polem. Kde vestavěný výběr není (starší Android, iOS), zůstává systémový —
rozhoduje `embeddedMediaPickerAvailable()`.

- **Soukromí zůstává systémové.** Galerii kreslí proces výběru fotek do `SurfaceView`
  (`SurfaceControlViewHost`), aplikace dostane jen vybraná URI a nepotřebuje oprávnění ke
  knihovně fotek — tedy totéž co u systémového výběru, jen bez odchodu z chatu.
- **Volat se smí až od API 34 s extension 15**, ale knihovna má `minSdk 23`, takže překlad nic
  nehlídá a pod tou hranicí by to spadlo za běhu. Podmínka je proto i uvnitř `actual`, ne jen
  u volajícího.
- **Příprava vybrané přílohy běží v rozsahu OBRAZOVKY, ne panelu** (`EmbeddedMediaSelection`,
  `rememberEmbeddedMediaSelection` drží `CoroutineScope`). Kopie velkého videa do cache trvá
  vteřiny a „Hotovo" panel zavře dřív; dokud běžela v `rememberCoroutineScope` panelu, zavření ji
  zrušilo a video se nad polem nikdy neobjevilo — bez hlášky, protože zrušení není chyba.
  U fotky to vidět nebylo, ta je hotová dřív, než člověk ťukne. Během přípravy je nad polem
  „Připravuji soubory…" a odeslání čeká (poslalo by dávku bez videa); odznačení položky, která
  se ještě kopíruje, hotovou kopii zahodí. **Ověřuje se velkým videem a rychlým „Hotovo"** —
  malé video (screenrecord emulátoru má desítky kB) chybu neukáže. Velké video se vyrobí bez
  kodéru: k malému MP4 se připojí box `free` o stovkách MB (délka + `free` + nuly), MediaStore
  i přehrávač ho berou.
- **Přílohy chodí PRŮBĚŽNĚ, odznačení taky** (`onPicked` / `onUnpicked`), ne až na konci výběru.
  Odznačená položka se hlásí jako URI od pickeru, ale konverzace zná přílohy podle CESTY do
  cache — proto si `actual` drží mapu URI → cesta a `ConversationViewModel.removeAttachments`
  bere cesty.
- **Sbalený picker NEJDE POSOUVAT, takže sheet v konverzaci ho má vždycky rozbalený**
  (`EmbeddedMediaPicker(expanded = true)` → `state.isExpanded`). Sbalený stav je náhled pro
  panel, který hostitel teprve táhne nahoru: bez záložek Photos / Collections a se zakázaným
  scrollováním — v logu pickeru `embeddedCollapsedModeScrollingEnabled=false`, a veřejné API ho
  zapnout neumí. Náš sheet je celý rozbalený, takže sbalený picker v něm byl mřížka, se kterou
  nešlo pohnout (hlášeno z Pixelu 11 Pro Foldu jako „zaseklý sheet"). Hodnota nastavená před
  startem session se promítne do `setPickerLaunchedInExpandedState`, pozdější změna jde přes
  `notifyPhotoPickerExpanded` — obojí funguje. Sbalený stav má smysl jen tam, kde panel jde
  vytáhnout (Direct Share, viz níž).
- **Obnovené zaškrtnutí picker při startu session ohlásí ZNOVU** (`onUriPermissionGranted`
  s položkami z `initialMediaSelection`). Ty přílohy už nad polem jsou, takže by se přidaly
  podruhé — po jediném výběru a znovuotevření galerie bylo v dávce „3 z 3". Co už je
  připravené (klíč v mapě URI → cesta), se proto přeskočí.
- **Dřívější tvrzení, že rozbalený picker přejde do VLASTNÍ AKTIVITY, se nepotvrdilo.** Na
  emulátoru s API 37.2 (profil Pixel 10 Pro Fold, rozložený i složený) zůstává `mCurrentFocus`
  na `MainActivity` a picker kreslí dál do našeho sheetu — i po výběru, odznačení a opětovném
  otevření. Kdyby se to na nějakém zařízení přesto dělo, pozná se to v logu pickeru podle
  `embeddedPickerLaunchedInExpandedState=true` a `mCurrentFocus` na
  `com.google.android.photopicker`; náhradní cesta je nastavit `isExpanded` až PO startu
  session (pak jde přes `notifyPhotoPickerExpanded`).
- **Co picker dostal za konfiguraci, je v logu** (`adb logcat | grep PhotopickerConfiguration`):
  celé `EmbeddedPhotoPickerFeatureInfo` i odvozená konfigurace. Tudy se příčina našla — vzhled
  (chybějící záložky) napovídal, ale rozhodl řádek `embeddedCollapsedModeScrollingEnabled`.
- **Picker se zapíná až po DOSEDNUTÍ sheetu.** `SurfaceView` má `setZOrderOnTop(true)` a s posunem
  vrstvy se nehýbe — tatáž vlastnost, kvůli které video v prohlížeči jede do `TextureView`
  a ne přes `PlayerView`. Během vjezdu sheetu by stál na místě a doskočil až na konci.
- **Výška je podíl okna se stropem**, ne pevné číslo: nad sheetem musí zůstat kus konverzace,
  jinak není poznat, kam se posílá — a to je celý smysl vestavěného výběru.
- **Co chat nepřijme, se nenabízí** (`setMimeTypes` podle `ContentAccess`), stejné pravidlo jako
  u sponky. Omezení se posílá, jen když se fotky a videa liší; jinak zůstává výchozí obojí.
- **Motiv se předává výslovně** (`setThemeNightMode`): picker jinak jede podle systému, ale
  uživatel si v Nastavení může vynutit světlý nebo tmavý a galerie v opačném by vedle konverzace
  svítila. Akcent se nastaví jen v mezích, které API bere (jas 0,05–0,9, plné krytí).
- **Co už je vybrané, si pamatuje OBRAZOVKA, ne panel** (`EmbeddedMediaSelection`,
  `rememberEmbeddedMediaSelection` v konverzaci). Panel se zavřením zahodí, takže bez toho začíná
  každé otevření s prázdným výběrem — a tatáž fotka se dá vybrat podruhé, v albu je pak dvakrát.
- **Zaškrtnutí se obnovuje PRŮNIKEM s přílohami nad polem**, ne jen z paměti. To je celá
  synchronizace: co uživatel mezitím odebral křížkem nebo odeslal, z výběru vypadne samo a panel
  to nemusí nijak sledovat. Průnik se počítá JEDNOU při otevření — během vybírání příloh přibývá
  a přepočítávat by znamenalo měnit klíč stavu, tedy restartovat session uprostřed výběru.
- **Past: `rememberEmbeddedPhotoPickerState` je saveable, takže `initialMediaSelection` samo
  nestačí.** Při dalším otevření se obnoví ULOŽENÝ stav i s výběrem, který už neplatí — odebraná
  fotka zůstala v mřížce zaškrtnutá (ověřeno na emulátoru). Množina obnovených položek se proto
  předává i jako `inputs`, na kterých stav resetuje; mezi otevřeními se mění jen tehdy, když se
  změnily přílohy, takže resetů to nepřidá.
- **Knihovna je alpha** (`1.0.0-alpha02`) a API má `@ExperimentalPhotoPickerComposeApi`. Proto
  je za `expect`/`actual`: bez ní i bez systémové podpory funguje výběr příloh dál.
- **Ověřeno na emulátoru** (API 36, extension 20): galerie v sheetu nad konverzací, výběr → fotka
  nad polem, odznačení → fotka zmizí, zavření gestem zpět, zavření a znovuotevření → fotka je
  pořád zaškrtnutá, po odeslání → zaškrtnutí zmizí.
- **Fallback ověřený na Androidu 14 (SDK Extensions 7) a 13**: sponka otevře systémový výběr
  (`PhotoPickerActivity`), vybrané fotky naskočí nad pole, a Direct Share se ukáže bez galerie.
  Na Androidu 16 (Extensions 17) je vestavěná galerie v sheetu i v Direct Share jako na 17.
  **Android 14 sám nestačí** — rozhoduje verze SDK Extensions, a obraz emulátoru API 34
  (`google_apis_playstore` r14) má jen 7; skutečný telefon s Androidem 14 ji mívá vyšší díky
  aktualizacím Play systému, takže tam galerie být může.

### Posílání příloh a alb

Sponka ve vstupním poli nabídne Fotka nebo video / Soubor (výběr víc najednou, nejvýš 10). Vybrané přílohy
čekají v kartě nad polem, text pole je jejich popisek; odeslání pošle jednu jako zprávu, víc jako
album. Bublina ukazuje průběh nahrávání.

- **Přílohy se neposílají hned po výběru**, ale čekají ve stavu (`ConversationState.attachments`).
  Jinak by k nim nešlo napsat popisek ani některou odebrat. Fotky jsou v M3
  `HorizontalMultiBrowseCarousel` s křížkem, soubory řádky s názvem (`StagedAttachmentsStrip`).
  Při úpravě zprávy se schovají a odeslání úpravy je nepošle.
- **Album = `sendMessageAlbum`** (2–10), jedna příloha jde přes `sendMessage`. **Popisek patří první
  položce** — tak ho Telegram u alba ukazuje. Entity popisku se dohledávají jako u textu.
- **Fotky s videi a soubory se nemíchají** (TDLib skládá dokumenty do alba jen s dokumenty). Výběr jiného
  druhu připravené přílohy **nahradí**, stejný druh se přidá; nad strop se ořízne a ohlásí
  (`stageAttachments`, testy v `StagedAttachmentsTest`).
- **Připravuje se souběžně, pořadí drží `awaitAll`.** Selhání jednoho souboru ostatní nezahodí.
  iOS PHPicker má `selection = Ordered`, aby album odešlo v pořadí ťuknutí.
- **TDLib u příloh koncept sám NEMAŽE** (`clearDraft` je jen u textu), takže se po odeslání maže
  výslovně — jinak by se popisek vrátil jako koncept. Při selhání se přílohy vrátí nad pole.
- **`rememberCarouselState { size }` počet přečte znovu při každé kompozici**, takže odebrání
  a přidání fotky carousel nerozbije.

- **TDLib posílá podle CESTY** (`InputFileLocal`). Systémové URI (`content://`, iOS URL z výběru)
  neotevře, takže platforma vybraný soubor nejdřív zkopíruje do cache aplikace
  (`rememberAttachmentPicker`, `expect`/`actual`). Každá příloha má vlastní podadresář — dva
  soubory stejného jména se nepřepíšou a jméno zůstane, protože ho TDLib pošle.
- **Výběr bez oprávnění**: Android Photo Picker + `OpenDocument`, iOS PHPicker + UIDocumentPicker
  (`asCopy`). Oba běží mimo aplikaci a vydají jen vybraný soubor.
- **Fotka se zmenšuje na 2560 px a ukládá jako JPEG.** TDLib přijme fotku nejvýš 10 MB a do
  10 000 px; z foťáku bývá větší. **Orientace se musí použít, ne zahodit**: přepsaný JPEG EXIF nemá,
  takže bez otočení pixelů by fotka odešla bokem. Android: `ImageDecoder` (9+) orientaci použije
  sám, pro starší `ExifInterface`. iOS: kreslení přes `UIGraphicsImageRenderer` ji použije taky
  a HEIC se tím zároveň převede na JPEG.
- **Soubor jde beze změny s `disableContentTypeDetection = true`** — uživatel posílal „soubor",
  TDLib by jinak z videa nebo hudby udělal přehrávatelnou zprávu.
- **iOS delegáty výběrů se drží silnou referencí** (`remember`). UIKit je drží jen slabě
  a bez toho by je uklidil garbage collector dřív, než uživatel něco vybere — výběr by tiše nic
  neudělal. Soubor z UIDocumentPickeru je v dočasném adresáři, který systém po delegátovi maže,
  proto se hned kopíruje.
- **Průběh nahrávání** chodí týmiž `fileUpdates` jako stahování (`remote.uploadedSize`),
  `FileRepository.uploadProgress`. Kreslí se jen u zprávy ve stavu `Sending`: vlnitý kruh
  (`CircularWavyProgressIndicator`) na tmavém kolečku přes fotku, u souboru místo náhledu. Dokud
  TDLib nahrávání neohlásí, točí se neurčitě. Průběh je i na každé dlaždici alba — dlaždice
  nahrávají každá sama za sebe.
- **Zrušení = smazání rozepsané zprávy** (`deleteMessages`, křížek uprostřed kolečka průběhu).
  TDLib tím zastaví i nahrávání a pošle `UpdateDeleteMessages`, takže zpráva zmizí jako každá
  smazaná. U alba se ruší jen ťuknutá položka, zbytek odejde.
- **Citace se spotřebuje** jako u textové zprávy; let ze vstupního pole se nekoná.
- **Ťuknutí na přílohu nad polem otevře náhled přes celou obrazovku** (`StagedAttachmentPreview`):
  karta mezi lištami (zpět a smazat nahoře, režim odeslání a Hotovo dole), ťuknutí lišty schová,
  štípnutí a dvojité ťuknutí přibližují, video se přehraje, tah dolů náhled zavře. **Stránky
  jsou tytéž jako v prohlížeči médií a na obrazovce „Komu: …"** (`ZoomableMediaPage`,
  `VideoPage`, krmené souborem na disku). Dřív tu byl třetí, vlastní zoom přes
  `detectTransformGestures` (bez přiblížení kolem prstů, bez dvojitého ťuknutí, posun bez mezí)
  a video se jen ukázalo náhledem; rozcházel se s prohlížečem při každém doladění. Spoiler je
  zakrytý jen s lištami, na celé obrazovce se dá zkontrolovat, co pod ním je.
- **Při úpravě zprávy sponka není.**
- **Video se nepřekódovává.** Server video nerozebírá, takže rozměry, délku a náhled (první
  snímek, JPEG do 320 px — víc TDLib nepřijme) čte platforma: Android `MediaMetadataRetriever`,
  iOS `AVURLAsset` + `AVAssetImageGenerator`. **Rozměry po otočení** — video na výšku je uložené
  naležato s příznakem; bez prohození by bublina měla poměr stran na šířku. Náhled je zároveň to,
  co se kreslí v carouselu nad polem (Coil video neotevře).
- **iOS video přebaluje do MP4** (`AVAssetExportPresetPassthrough`, bez překódování) s indexem na
  začátku (`shouldOptimizeForNetworkUse`) a posílá `supportsStreaming = true`. Android kopíruje
  soubor z foťáku, kde index bývá na konci, takže `supportsStreaming = false`. Soubor
  z `loadFileRepresentation` platí jen uvnitř callbacku — kopíruje se hned v něm.

### Otevřený chat (`openChat` / `closeChat`)

Otevřená konverzace to hlásí TDLib (`ConversationRepository.openChat`, protějšek při odchodu).
Dokud se to nedělalo, vypadal každý chat jako zavřený — a notifikace k chatu, na který se
uživatel právě díval, na liště problikla, než ji stihlo zrušit hlášení přečtení.

- **U otevřeného chatu se notifikace ODKLÁDÁ, neruší.** V `add_new_message_notification` je
  `min_delay_ms = 1000`, když je klient online a chat otevřený. To je celý ten rozdíl: během té
  vteřiny dorazí `viewMessages` z konverzace a notifikace se zahodí dřív, než se ukáže.
  **Otevřený chat notifikace NEPOTLAČUJE** — kdyby potlačoval, byl by chat nechaný „otevřený"
  na pozadí tichou ztrátou zpráv.
- **Proto se při odchodu do pozadí nezavírá.** Odklad se váže na `online`, a ten aplikace
  v pozadí vypíná sama (`ConnectionController`), takže na notifikace to nemá vliv. Zavírat
  a otevírat chat při každém přepnutí do pozadí by byla složitost bez užitku.
- **Pár musí sedět, takže obě volání jdou z `appScope`, ne z `viewModelScope`.** Ten se při
  odchodu z chatu ruší, takže otevření by se nemuselo odeslat, zatímco zavření (v `onCleared`)
  ano — a TDLib si otevření počítá. Zavření navíc čeká na doběhnutí otevření (`join`), jinak by
  se pořadí mohlo obrátit a chat by zůstal otevřený napořád.
- **Zavření je PRVNÍ věc v `onCleared`.** Kus pod ním končí předčasným `return` (nemá-li se co
  uložit z konceptu); pod ním by se na zavření při odchodu z chatu bez rozepsané zprávy nedošlo.
- **Zavřít neotevřený chat nevadí.** `close_dialog` s nulovým počtem se jen vrátí, takže na
  vyměněném klientovi (po odhlášení se počty resetují) to nic nerozbije.
- **Je to víc než notifikace.** Otevřený chat si TDLib nechává načtený v paměti, zvedá prioritu
  stahování jeho souborů, kratčeji odkládá ukládání konceptu a u supergroup a kanálů podle toho
  řídí doručování updatů.
- **Na blikání kvůli druhému zařízení to nemá vliv** — tam zprávu přečte ten druhý klient a ten
  o naší obrazovce nic neví (viz „Notifikace → Poučení", běžící emulátor).

### Předěly dne

V bublině se vypisují jen hodiny, protože datum má nést předěl nad blokem zpráv. Bez něj se
v delší historii nedalo poznat, kde končí včerejšek.

- **Předěl je samostatný blok seznamu** (`DayDividerItem`), vyrobený builderem, který sousedy
  zná (viz „Bloky seznamu"). Patří zprávě, jejíž starší soused je z jiného dne.
- **Předěl stojí NAD zprávou, a proto v obráceném seznamu ZA ní** (vyšší index).
- **Klíč předělu je datum**, ne zpráva. Když se dotáhne starší stránka, přestěhuje se předěl nad
  starší zprávu téhož dne jako tatáž položka. Opakovaný den (posunuté hodiny odesílatele)
  dostane do klíče i klíč zprávy — dva stejné klíče `LazyColumn` shodí.
- **Mezi předělem a zprávou pod ním leží i `verticalArrangement`**, takže předěl má dole o ten
  rozestup méně vzduchu (`DIVIDER_BOTTOM_SPACING`) — jinak by zpráva visela dál od svého data.
- **Skok na nepřečtenou posazuje nahoru i předěly nad ní**, ne samotnou zprávu. Dokud byl předěl
  součástí položky, šel nahoru s ní; jako samostatný blok by zůstal těsně nad horní hranou.
- **U nejstarší načtené zprávy se předěl ukáže taky.** Jakmile se dotáhne starší stránka, posune
  se nahoru. Lepší než nechat vršek historie bez data.
- **Za předělem začíná nový blok**, i když píše tentýž člověk — proto se do bubliny posílá
  `previous = null`. Jinak by první zpráva pod datem navazovala staženým nárožím na zprávu
  z minulého dne a přišla by o jméno odesílatele.
- **Dny se porovnávají přes místní datum, ne přes rozdíl sekund.** Den má 24 hodin jen někdy —
  na přechodu letního času je kratší nebo delší a půlnoc se posouvá podle časového pásma.
- **Podrobnost data roste s věkem**, obráceně než u `asRelativeTime`. Předěl je nadpis nad celým
  blokem, takže musí říct dost: Dnes / Včera / název dne / „23. května" / s rokem u starších let.
  Měsíc je ve druhém pádu, protože stojí za dnem.
- **Plovoucí datum při scrollu není `stickyHeader`.** Ten se v obráceném seznamu lepí na jeho
  začátek, tedy dolů — přesně naopak, než je potřeba. Je to overlay nad seznamem, který se ukáže
  jen při scrollu a bere datum z nejvyšší viditelné položky (opět podle klíče, ne indexu).
- **Předěl dne nese i čas první zprávy dne** („Včera · 21:00"), jako v Google Zprávách.
- **Časový předěl po pauze aspoň hodinu** (`TimeDividerItem`, `TIME_DIVIDER_GAP_SECONDS`, testy
  v `ConversationItemsTest`). V bublině čas není, takže bez něj se dal zjistit jen tahem doleva
  nebo v nabídce. Souvislý rozhovor čas nepřerušuje, nová dávka zpráv po pauze ho dostane. Na
  přelomu dne ho nese předěl dne — dva předěly nad sebou nevzniknou. Stojí nad předělem
  nepřečtených a **ukončuje blok bublin** stejně jako předěl dne (`continues` hlídá i pauzu,
  jinak by se nároží přes předěl stahovala). Klíč je odvozený od klíče zprávy pod ním.
- **Předěl a systémové oznámení vypadají stejně** (`CenteredNote`). Obojí je poznámka na okraji
  konverzace, ne zpráva od někoho; dvě různá pojetí téhož by seznam roztříštila.

### Skok na první nepřečtenou

Otevření chatu s nepřečtenými dotáhne historii až k hranici, skočí na první nepřečtenou zprávu
a nad ni nakreslí předěl „Nepřečtené zprávy".

- **Hranice se zachytí JEDNOU při otevření** (`lastReadInboxMessageId` z `getChat`) a už se nemění.
  Kdyby se posouvala podle toho, co se právě označilo za přečtené, předěl by uživateli utíkal pod
  rukama — a je to jediné místo, podle kterého pozná, kde přestal.
- **První nepřečtená je nejstarší PŘÍCHOZÍ zpráva za hranicí, ne nejstarší zpráva za ní**
  (`firstUnreadId`, testy `UnreadBoundaryTest`). Hranice je poslední přečtená PŘÍCHOZÍ, takže
  vlastní odpověď napsaná po ní má vyšší id. Předěl i skok to dřív braly jako „nad hranicí =
  nepřečtené": předěl chtěl staršího souseda s id ≤ hranici, a když jím byla vlastní odpověď,
  **neukázal se vůbec**; skok mířil na vlastní zprávu. Běžný případ — odepsal jsem, druhý
  odpověděl —, takže předěl chyběl právě tam, kde se čte nejčastěji.
- **V chatu s testovacím botem nepřečtený stav vyrobit NEJDE, když má účet otevřený Notifikátor
  jinde** (telefon): zprávu od bota přečte to druhé zařízení, v logu je notifikace „odložené=true"
  a pak zmizí bez „Zobrazuji", a chat v seznamu odznak nemá. Předěl i efekt nepřečtené se proto
  ověřují sondou, která v chatu s botem nastaví `unreadBoundaryId` na id zvolené zprávy (podle
  textu) a zavolá `loadUntilUnread()`. Necommituje se.
- **`unreadCount` je potřeba vedle ukazatele.** Z `lastReadInboxMessageId` samotného se nepřečtené
  odvodit nedají: v chatu, kde poslední zprávu napsal uživatel, je ukazatel starší než ta zpráva,
  a přesto není co číst.
- **Nejdřív pár stránek od nejnovější dozadu** (`MAX_UNREAD_PAGES`). Obvyklý případ tím pokryje
  a seznam zůstane souvislý až k přítomnosti. **Kdo má nepřečtených stovky, dostane okno kolem
  hranice** (`loadAround(requireTarget = false)`, viz „Okno v historii") — hranice je poslední
  přečtená zpráva a mohla být smazaná, takže okno se bere i bez ní.
- **Na zámek stránkování se čeká, nepřeskakuje se.** Dotažení kvůli skoku běží souběžně s prvním
  načtením při otevření chatu; kdyby se volání při probíhajícím načítání vzdalo, spotřebovalo by
  všechny pokusy naprázdno a skok by se nekonal.
- **`scrollToItem` v obráceném seznamu posadí položku ke SPODNÍ hraně**, protože tam je jeho
  začátek. U skoku na nepřečtené je to naopak, než je potřeba, takže se ještě dorovnává o výšku
  viewportu — a ta se **měří**, protože výška bubliny se zprávu od zprávy liší.
- **Viditelná plocha začíná NAD spodním odsazením seznamu** (`scrollToAnchor`). Dole je pod ním
  vstupní pole; dokud se horní hrana brala z celé výšky viewportu, zajela první nepřečtená
  o výšku pole pod horní okraj obrazovky.
- **Index v `LazyColumn` není index v `state.messages`.** Před zprávami stojí sponzorované
  bloky a mezi nimi předěly. Blok se proto hledá v seznamu bloků (`containsMessage`), ne
  v seznamu zpráv.
- **Předěl nepřečtených je akcentní, ne šedý jako datum.** Je to jediná věc, kterou uživatel po
  otevření chatu opravdu hledá.

### Připnuté zprávy

Pod hlavičkou visí **jeden řádek s jednou připnutou zprávou** (`PinnedMessagesBar`), ať je jich
kolik chce. Na začátku nejnovější; ťuknutí na ni skočí (stejně jako citace, i se zvýrazněním
a dotažením historie) a pruh posune o jednu starší, za nejstarší zase nejnovější. Všechny jsou
za ikonou seznamu v sheetu (`PinnedMessagesSheet`), s odepínáním.

- **Všechny připnuté přes `searchChatMessages` s `SearchMessagesFilterPinned`**, ne přes
  `getChatPinnedMessage` — ten vrací jen nejnovější. Stránkuje se po stovce, strop pět stránek.
  Vyhledávání potřebuje zapnutou databázi zpráv (`useMessageDatabase = true`).
- **Jedna, ne tři.** Kanály připnuté příspěvky neodepínají, jen přidávají další, takže tři
  řádky (dřívější podoba) visely nad konverzací jako trvalý blok, a s rozbalením „všech" to byla
  půl obrazovky. Že jich je víc a kde v řadě stojíme, říká titulek („Připnutá zpráva 3 z 5").
- **„Poslední připnutá" = nejnovější ZPRÁVA, ne poslední připnutí.** Čas připnutí Telegram
  neposílá vůbec; pořadí je podle id zprávy a `getChatPinnedMessage` vrací taky tu s nejvyšším
  id. U kanálu, který připíná čerstvé příspěvky, je to totéž; připne-li někdo starou zprávu,
  zařadí se podle svého stáří.
- **Která se ukáže, ROZHODUJE SCROLL** (`pinnedForScroll`, testy `PinnedMessagesBarTest`): ta,
  kterou uživatel právě míjí — nejnovější z připnutých nad viditelnou částí. Pruh tím slouží
  i jako orientace v historii, místo aby napořád visel na nejnovější. Nedrží se k tomu žádný
  stav; odvozuje se z klíče nejvyšší viditelné položky (ten už existoval kvůli plovoucímu datu),
  takže nová připnutá zpráva výběr nerozhodí.
- **„Nejvyšší viditelná" = nejvyšší POD pruhem, ne pod hlavičkou** (`topVisibleKey` bere jen
  položky pod horním odsazením seznamu). Seznam pod pruhem a hlavičkou pokračuje a pro
  `LazyColumn` jsou i tam položky viditelné. Skok z pruhu posadí připnutou těsně pod pruh
  a předchozí připnutá zůstala schovaná nad ní — pruh pak přeskočil o jednu (3 → 1 místo
  3 → 2). Ověřeno na složeném Foldu se čtyřmi připnutými.
- **Porovnává se STRIKTNĚ** (`it.id < top`). Připnutá, na kterou se právě skočilo, stojí nahoře
  viewportu; kdyby se počítala jako „nad", ukazoval by ji pruh dál a ťuknutí by vedlo pořád na
  ni — procházení by se zaseklo. Nad nejstarší připnutou se ukazuje ona sama, výš už žádná není.
- **Skok z pruhu kotví NAHORU, ne na střed** (`jumpToMessage(anchor = ScrollAnchor.Top)`). Se
  středem se nad cílem odkryje kus historie a pruh přeskočí rovnou k nejstarší připnuté — z 19
  → 16 → 14 se prostřední krok ztratí. Citace a hledání na středu zůstávají: tam je naopak
  potřeba vidět, na co zpráva navazuje.
- **Ukázaná zpráva se drží podle id, ne indexu** (`pinnedIndex`, testy `PinnedMessagesBarTest`).
  Nová připnutá by jinak posunula index a pruh by přeskočil na jinou. Odepnutá ukázaná spadne
  na nejnovější.
- **Křížek v pruhu je jen u jediné připnuté.** U víc zpráv je vpravo seznam a odepíná se v něm —
  křížek vedle jedné z mnoha svádí k domněnce, že zavře celý pruh.
- **Sheet se po odepnutí nezavírá** (odepíná se často víc zpráv po sobě), po ťuknutí na řádek
  ano — napřed se schová, pak se skáče.
- **Pruh je plovoucí pilulka z mléčného skla** (`CircleShape`, stín, `glassStyle`), ne karta se
  svislým proužkem vlevo. Proužek (dílek na zprávu) byl jazyk M2 a v aplikaci už jednou ustoupil
  ploše (citace, náhled odkazu); pilulka mluví stejně jako plovoucí lišty konverzace. Fotka,
  video a soubor s náhledem mají v řádku čtvereček (nejmenší varianta, ostrou kvůli 36 dp
  nestahovat).
- **Vlevo je špendlík v sedmistranné sušence** (`PinnedBadge`, `primaryContainer`), soustředný
  se zaoblením pilulky (odsazení = (výška − odznak) / 2). Při přechodu na jinou připnutou se
  sušenka pootočí o jednu vlnku pružinou s přeskokem, špendlík stojí — změna je vidět koutkem
  oka. Úhel je přímo `index × 360/7`, žádné počítadlo; po dosednutí má sušenka tentýž obrys.
  `Cookie7Sided` jinde nic neznamená (viz `ShapeMorphs`).
- **Krytí skla zůstává `GLASS_TEXT_TINT_ALPHA` (0,88)**, i když by pilulka s nižším vypadala víc
  „skleně". Pruh nese text a v tmavém režimu nad světlou fotkou by s nižším krytím spadl pod AA
  (viz „Vzhled konverzace", mléčné sklo). Rozostření je vidět nad fotkou nebo sytou bublinou.
- **Sheet všech připnutých je blok zaoblených řádků** (`groupItemShape`, `surfaceContainerLowest`,
  spára 2 dp), jako kontakty a odkazy v profilu; řádek je `ListItem` s náhledem, textem a dnem.
  Dřív holé řádky na ploše sheetu — jiný vzhled pro tentýž „seznam věcí".
- **Právo odepínat se zjišťuje jednou** (`canBePinned` první připnuté zprávy). Je v chatu jedno
  pro všechny zprávy; dotaz na každou by byl zbytečný. Bez práva křížek není.
- **Odepnutá zpráva z pruhu zmizí hned**, potvrzení dorazí updatem. Při selhání se seznam přečte
  znovu, takže se zpráva vrátí.
- **Místo potvrzovacího dialogu „Vrátit" ve snackbaru.** Odepnutí je vratné; dialog před vratnou
  akcí jen zdržuje. Vrácení připíná bez upozornění členům — je to oprava omylu, ne oznámení.
  Událost se ve stavu nuluje hned při zobrazení, jinak by se snackbar po otočení ukázal znovu.
- **Na změnu se seznam ČTE ZNOVU**, neodvozuje se z updatu. `UpdateMessageIsPinned` nese jednu
  zprávu a „odepnout vše" jich pošle dávku; `conflate` z ní udělá jedno čtení. Totéž po smazání
  připnuté zprávy. Úprava připnuté zprávy se do pruhu propisuje z `editedMessages`.
- **Odběr stojí PŘED prvním čtením** (`onStart { emit(Unit) }`), aby se změna mezi nimi neztratila.
- **Pruh plave nad seznamem, není v `topBar`.** Seznam se odsazuje o jeho změřenou výšku (dole
  o padding `bottomBar`) — jinak by při odjezdu pruhu obsah na konci animace poskočil.
  O stejnou výšku se posouvá i plovoucí datum.
- **Při odjezdu se kreslí poslední zpráva**, ne prázdný pruh (`AnimatedVisibility` dobíhá
  s obsahem, ale stav je už prázdný).
- **Připnout / Odepnout je i v nabídce nad zprávou**, podle `canBePinned` z `getMessageProperties`.
  Která z nich se nabídne, se bere ze seznamu připnutých ve stavu — `ChatMessage` příznak
  `isPinned` nenese a update o připnutí by ho stejně musel přemapovat v celé historii.
- **Připnutí má dialog s jednou volbou**, která mění, koho se dotkne: ve skupině a kanálu
  „upozornit členy/odběratele" (`disableNotification`), v soukromém chatu „uvidí i protějšek"
  (`onlyForSelf`). Upozornění už nikdo nevezme zpátky, proto se ptá dřív. V Uložených zprávách
  volba nemá smysl a připíná se rovnou. Soukromý chat připíná bez upozornění — protějšek uvidí
  servisní zprávu, oznámení navíc by byl šum.
- **Odepnutí z nabídky jde stejnou cestou jako křížek v pruhu**, včetně „Vrátit".
- **Totéž platí pro čtení textu hlášky.** Efekt u `notice` vynuloval událost a TEPRVE POTOM
  četl text (`getString` je `suspend`) — restart efektu po vynulování to čtení zrušil a hláška
  se ukázala, jen když byl text náhodou už načtený. Všechno, co se má po vynulování ještě
  udělat, patří do `snackbarScope.launch`.
- **Past: jednorázová událost ve stavu a `LaunchedEffect` klíčovaný tou událostí.** Když efekt
  událost hned vynuluje, změní tím vlastní klíč, restartuje se a zruší i to, co právě spustil.
  Snackbar „Vrátit" se tak neukázal nikdy. Snackbar proto běží v `rememberCoroutineScope`,
  mimo tělo efektu.

### Skok na citaci a tlačítko dolů

Ťuknutí na citaci v bublině odroluje na citovanou zprávu a krátce ji zvýrazní. Tlačítko „dolů"
se ukáže i bez nových zpráv, jakmile je uživatel o kus výš.

- **Načtená zpráva = okamžitý skok, jinak okno kolem ní** (`loadAround`, viz „Okno v historii").
  Skočit jde libovolně hluboko — citace na zprávu starou roky i výsledek hledání.
- **Na citaci a mezi výsledky hledání se DOJÍŽDÍ** (`animateToAnchor`, `slowSpatialSpec`),
  skok na první nepřečtenou a návrat dolů zůstávají okamžité. Okamžitý skok neukázal, kterým
  směrem a jak daleko zpráva je. Položka mimo obrazovku nemá změřenou polohu: dojede se na odhad
  z průměrné výšky viditelných položek a pak se doladí podle skutečného layoutu.
- **Ke vzdálené nebo nenačtené zprávě se PŘEJEDE** (`JumpTransition`): seznam odjede směrem
  k ní a zprůhlední, neviditelně se přesune (u nenačtené mezitím doběhne `loadAround`), nalezená
  zpráva se objeví rovnou na místě a zvýrazněná a sousedé k ní sjedou shora i zdola (vzdálenější
  z dál). Rolovat přes desítky zpráv (`MAX_ANIMATED_JUMP_ITEMS`, 40) by trvalo a skládalo by
  všechno mezi; do nenačtené historie dojet nejde vůbec.
- **Odjezd začíná HNED, ne až po načtení** (`ConversationState.pendingJump`). `jumpToMessage`
  ho ohlásí před `loadAround`, takže čekání na TDLib schová odjezd. Když se zpráva nenajde,
  `pendingJump` zmizí bez `scrollTarget` a seznam se vrátí (`restore`). Druhé volání `leave`
  nad rozjetým odjezdem jen pokračuje ze současné hodnoty — `Animatable` předchozí animaci přeruší.
- **Směr zná u nenačtené zprávy jen ViewModel** (`ScrollTarget.older`: menší id než nejstarší
  načtená). Po výměně okna je nový seznam dole a pozice by vždycky tvrdila „starší".
- **Sjíždění sousedů je modifikátor na kořeni KAŽDÉHO bloku** (`Modifier.gathered`, za
  `animateItem`). Obalit položku v `LazyColumn` dalším `Box`em nejde: `animateItem` musí být na
  kořeni položky, jinak jeho animace přestanou fungovat.
- **Dojíždí se jen ke zprávě, která v seznamu UŽ BYLA** (`ScrollTarget.animate`, rozhoduje
  `jumpToMessage`). Po výměně okna začíná nový seznam u spodní hrany okna, a tam se hned
  dotahují novější zprávy — ty se vkládají na začátek a posunou všechny indexy. Dojíždění
  z tohohle místa skončilo u úplně jiné zprávy a nalezená nebyla vidět.
- **Cíl se po dojetí hledá znovu podle id, ne podle indexu ze začátku jízdy** (`currentIndex`
  nad `rememberUpdatedState(items)`). Během jízdy se můžou dotáhnout stránky; `items`
  zachycené při spuštění efektu jsou v tu chvíli staré.
- **Zpráva, kterou okno neobsahuje, byla smazaná** — hláška „už není k dispozici" (u citace
  i u výsledku hledání vlastní věta). „Moc hluboko" už nenastane.

### Nepřečtené zmínky, reakce a hlasy v anketě

Nad tlačítkem dolů jsou tlačítka „@" (nepřečtené zmínky a odpovědi), „❤" (nepřečtené reakce na
vlastní zprávy) a anketa (nové hlasy ve vlastní anketě, TDLib 15) s počtem v odznaku
(`UnreadMarkButton`); v seznamu chatů je u chatu odznak s ikonou vedle počtu nepřečtených
(`UnreadBadges`). Počty drží TDLib (`Chat.unreadMentionCount`, `unreadReactionCount`,
`unreadPollVoteCount`), doména `UnreadMarks`, testy `UnreadMarksTest`.

- **Ťuknutí skočí na NEJSTARŠÍ** (`searchChatMessages` s `SearchMessagesFilterUnreadMention`
  / `UnreadReaction`, `oldestOf`), jako skok na nepřečtené. Výsledky jsou od nejnovější, bere se
  nejmenší id ze stránky po stovce.
- **Cíl se hlásí jako zhlédnutý hned po skoku** (`markRead`), nečeká se na obrazovku. Jinak by
  další ťuknutí během pár set milisekund našlo tutéž zprávu a tlačítko by nevedlo dál.
- **Reakce jsou na VLASTNÍCH zprávách, a ty se dřív jako zhlédnuté nehlásily vůbec** — hlášení
  přečtení filtruje odchozí zprávy. Reakce by tak zůstaly nepřečtené napořád a „❤" by
  nezhaslo, ani když na zprávu uživatel doskočil. Vlastní zpráva se proto hlásí, když nese
  nepřečtené reakce (`ChatMessage.hasUnreadReactions` z `Message.unreadReactions`, živě přes
  `UpdateMessageUnreadReactions`; pravidlo `messagesToView`). Zhlédnutí je přečte — ověřeno.
  **Totéž hlasy v anketě** (`ChatMessage.hasUnreadPollVotes` z `containsUnreadPollVotes`, živě
  přes `UpdateMessageContainsUnreadPollVotes`); obě změny nese jeden tok `unreadMarkChanges`.
  Že je zhlédnutí přečte, ověřené NENÍ — je to předpoklad podle reakcí.
- **Počty chodí ŠESTI updaty**: `UpdateChatUnreadMentionCount`, `UpdateMessageMentionRead`,
  `UpdateChatUnreadReactionCount`, `UpdateMessageUnreadReactions`,
  `UpdateChatUnreadPollVoteCount` a `UpdateMessageContainsUnreadPollVotes` (updaty zpráv nesou
  i počet).
  Konverzace si na každý z nich přečte chat znovu (`getChat` je lokální), seznam chatů bere
  počet přímo z updatu.
- **Podržení nabídne „Označit vše jako přečtené"** (`readAllChatMentions` /
  `readAllChatReactions` / `readAllChatPollVotes`), jako u oficiálních klientů.
- **Ve vlákně komentářů tlačítka nejsou** — počty patří celé diskusní skupině a vedly by do
  zpráv mimo vlákno.
- **Odznak v seznamu počet nenese** — jde o to, ŽE zmínka je; kolik, řekne konverzace.
- **Ověřuje se botem**: `setMessageReaction` na vlastní zprávu v chatu s botem vyrobí nepřečtenou
  reakci (změna emoji je nová nepřečtená). Ověřeno na emulátoru: odznak v seznamu, tlačítko
  s počtem, skok se zvýrazněním a zhasnutí, podržení a „Označit vše". **Skutečná zmínka ověřená
  v testovací skupině s botem** (`text_mention` účtu): tlačítko „@" s počtem, skok na zmínku
  a zhasnutí. Aby tlačítko vůbec naskočilo, musí zmínka přijít MIMO obrazovku (odrolovat nahoru,
  pak ji poslat) — viditelná se přečte hned.
- **Reakce na zprávu, která už JE vidět, se musí přečíst taky** (`unreadMarkedIds` ve
  `snapshotFlow` hlášení přečtení, test `zpravySNeprectenouReakciNeboHlasem`). Hlášení chodí jen
  na ZMĚNU viditelných položek a příchod reakce je nezmění — tlačítko „❤" pak viselo nad zprávou,
  na kterou se uživatel díval, dokud nezaroloval nebo na něj neťukl. Ověřeno pokusem: před
  opravou tlačítko po 10 s pořád, po ní se neukáže vůbec.
- **Hlas v anketě bot nevyrobí** (Bot API hlasovat neumí) a vlastní účet ve vlastní anketě
  nepočítá. Tlačítko s anketou i odznak v seznamu se proto ověřily podstrčeným počtem pro chat
  s testovacím botem; ťuknutí prošlo hledáním s `SearchMessagesFilterUnreadPollVote` bez chyby
  (prázdný výsledek). **Skutečný nový hlas, skok na anketu a zhasnutí po zhlédnutí ověřené
  NEJSOU** — chce to druhého člověka, který hlasuje v neanonymní anketě.
- **Ťuk podle souřadnic ze STARÉHO dumpu otevřel cizí chat** — seznam se mezi dumpem a ťuknutím
  přeskládal. Dump se dělá TĚSNĚ před ťuknutím a po něm se ověřuje název v hlavičce
  (`uiautomator dump`), než se na obrazovce cokoli dalšího udělá.

### Hledání v konverzaci

Lupa v hlavičce chatu přepne hlavičku na **vyhledávací pilulku** a místo vstupního pole ukáže
**plovoucí toolbar** „N z M" se šipkami (`InChatSearchTopBar`, `InChatSearchNavigator`). Stav je
`ConversationState.search` (`InChatSearch`, čistá data a přechody, testy `InChatSearchTest`);
`null` = zavřené.

- **Že se hledá, musí být poznat TVAREM, ne textem.** Hlavička i spodek byly dřív v rolích, které
  na ploše stránky nejsou vidět: pole bylo `TextField` s průhledným kontejnerem i indikátorem,
  tedy k nerozeznání od nadpisu chatu (jediná změna oproti klidovému stavu byl zmizelý avatar),
  a navigátor byl pruh v `surfaceContainer`, což je v tmavém tématu rozdíl na hranici
  rozlišitelnosti — z celého pruhu byly vidět jen dvě šipky zavěšené v prázdnu. S prázdným
  dotazem tak na obrazovce nebyl JEDINÝ signál, že je aplikace v jiném režimu.
- **Pilulka je tentýž prvek jako hledání v seznamu chatů.** Jeden jazyk pro hledání v celé
  aplikaci; na rozloženém Foldu vypadá hlavička konverzace při hledání stejně jako hlavička
  seznamu vedle ní.
- **Pilulka nejde postavit na `TopAppBar`.** Ten titulek MĚŘÍ na celou šířku, ale UMÍSŤUJE ho
  16 dp od kraje, takže by pilulka o ten kus přetekla za pravý okraj. Hlavička hledání je proto
  vlastní řádek s ručními insety (`TopAppBarDefaults.windowInsets`) a výškou 64 dp — ta musí
  lícovat s hlavičkou konverzace, se kterou se střídá.
- **Pole v pilulce je `BasicTextField` s `DecorationBox`**, ze stejného důvodu jako vstupní pole
  konverzace: `TextField` si bere minimální výšku 56 dp a odsazení 16 dp napevno, takže by se
  vedle ikony do pilulky nevešel.
- **Výběr člena stojí VEDLE pilulky, ne v ní.** Je to akce nad hledáním, ne součást dotazu —
  a tři ikony uvnitř by na úzkém telefonu nenechaly místo na text.
- **Toolbar se bez kritérií neukazuje.** Prázdný dotaz nemá co počítat a šipky by byly jen
  zašedlé. Plovoucí prvek smí naskočit, protože nemá v layoutu pevné místo: výšku drží `Box`
  kolem něj, takže se seznam zpráv pod ním nepřeskládá.
- **Obsah plovoucí lišty se MUSÍ vystředit sám** (`Modifier.align(Alignment.CenterVertically)`).
  `HorizontalFloatingToolbar` centruje na výšku jen Row, do kterého skládá svůj vlastní
  `leadingContent` / `trailingContent`; ten NÁŠ obsah dostane vnořený `Row` BEZ
  `verticalAlignment`, tedy s výchozím `Alignment.Top`. Výšku lišty určí 48dp `IconButton`y
  a cokoli nižšího se přilepí k horní hraně — změřeno `uiautomator dump`: text „1 z 52" měl
  střed o 14 dp výš než šipky vedle něj a celá lišta působila rozházeně. U výběru zpráv to
  nebylo vidět, protože tam jsou všechny položky stejně vysoké `IconButton`y.
- **`animateContentSize` patří na POPISEK, ne na celou lištu.** Na toolbaru ho vlastní
  `clipToBounds` ořízne i o stín a s výchozím `Alignment.TopStart` se obsah po dobu animace
  lepí doleva — tedy mimo střed, kvůli kterému je lišta v centrovaném `Box`u. Na popisku
  s `alignment = Alignment.Center` se šířka mění od středu a lišta kolem něj jen povyroste.
  Minimální šířka popisku (`STATUS_MIN_WIDTH`) pak zabrání poskočení mezi indikátorem
  načítání a první pozicí.
- **Lišta je jeden kus pro hledání i výběr zpráv** (`ConversationFloatingBar`). Dvě kopie
  téhož `Box` + `AnimatedVisibility` + toolbaru se rozešly hned při prvním ladění odsazení,
  a rozdílná výška slotu by znamenala, že se spodek obrazovky mezi režimy hýbe.
- **Plocha konverzace při hledání USTOUPÍ, neposune se k bublinám** (`surfaceContainerLowest`).
  Výraznější tónování by šlo jen směrem k `surfaceContainerHigh` příchozí bubliny, tedy proti
  čitelnosti zpráv; takhle se rozdíl naopak o kousek zvětší a nálezy vyskočí. Je to proto vědomě
  jemné — silnější posun v tomhle směru role nenabízejí.
- **`searchChatMessages`, stránkované `nextFromMessageId`** (po 50). Další stránka se dotahuje,
  až k ní uživatel došipkuje — u „google" má dlouhý chat přes 1 600 výsledků.
- **Každý výsledek je skok přes `jumpToMessage`**, tedy i okno v historii (viz „Okno
  v historii"). Výsledek starý roky se tím otevře stejně rychle jako včerejší.
- **Šipka nahoru = starší**, protože tak výsledky leží v konverzaci nad sebou; pozice 1 je
  nejnovější. Tak to dělá i oficiální klient.
- **Počet je odhad serveru, ale nikdy menší než načtené** (`count`). Server u hledání vrací
  `totalCount` přibližně; `-1` = neví.
- **Dotaz s pauzou 300 ms a rušením předchozího** (`searchJob`). Stránka pro starý dotaz se
  zahodí (`withNextPage` porovná `resultsQuery`).
- **„Nic nenalezeno" jen k dotazu, na který server odpověděl** (`nothingFound` porovnává
  `resultsQuery` s oříznutým `query`) — během psaní dalšího znaku by jinak problikávalo.
- **Text drží pole samo** (`rememberSaveable`), ne stav ViewModelu: hodnota vrácená přes
  `StateFlow` o dispatch později by poskakovala kurzorem.
- **Zpět zavírá hledání** (`NavigationBackHandler` povolený jen s otevřeným hledáním), teprve
  další zpět odchází z chatu. Konverzace zůstane na posledním výsledku.
- **Ukázaný výsledek zůstává zvýrazněný** (barva a obíhající vlnitý okraj, viz „Vzhled
  konverzace") — po jednorázovém pulzu by se v konverzaci ztratil.
- **Ve skupině jde hledat podle člena** (ikona `person_search` v hlavičce hledání →
  `MemberPickerSheet`, `searchChatMembers` podle jména; `searchChatMessages` se `senderId`).
  Vybraný člen je čip před polem, ťuknutím se filtr zruší. S členem stačí prázdný dotaz —
  výsledkem jsou všechny jeho zprávy (`InChatSearch.hasCriteria`), text k tomu hledání zúží.
  V soukromém chatu a v kanálu se nenabízí: odesílatelé jsou dva, respektive jeden.
- **Výsledky patří dvojici dotaz + odesílatel** (`resultsQuery`, `resultsSenderId`): stránka
  i „nic nenalezeno" pro jinou dvojici se zahodí. Výběr člena hledá hned, bez pauzy — je to
  hotové rozhodnutí, ne psaní.
- **Členové jen podle jména, ne seznamem.** Server u velké skupiny vrátí jen část; anonymní
  správce (odesílatel = chat) filtrovat nejde, `searchMembers` bere jen uživatele.
- **Hledané slovo je v textu zvýrazněné u KAŽDÉ zprávy**, ne jen u ukázaného výsledku
  (`MessageText` → `RichText(highlights)`). Je vidět, proč se zpráva našla. Zvýrazňuje se dotaz,
  ke kterému patří výsledky (`resultsQuery`), ne rozepsaný text.
- **Dotaz jde do bublin přes `derivedStateOf`** (`ConversationItemScope.searchHighlight`). Čte
  se v kompozici textu; s celým stavem by každá změna konverzace překreslila text všech bublin.
- **Zvýraznění je obrácená bublina**: podklad v barvě textu, písmo v barvě bubliny. Funguje na
  odchozí, příchozí i zvýrazněné bublině bez jediné pevné barvy.
- **Podklad je zaoblená pilulka kreslená za textem** (`drawHighlightPills` z `TextLayoutResult`,
  po řádcích), ne `SpanStyle.background` — ten zaoblit nejde a hranaté obdélníky v zaoblených
  a zvlněných bublinách působily jako cizí prvek.
- **Slovo v zakrytém spoileru se nezvýrazní** (`visibleHighlights`, testy
  `SearchHighlightTest`) — zvýraznění by ho prozradilo.
- **`highlightRanges` / `foldDiacritics` jsou v `core/ui`** (`SearchHighlight.kt`), sdílí je
  hledání napříč chaty i konverzace. Obojí hledá bez ohledu na diakritiku jako TDLib.

### Okno v historii

Skok na zprávu mimo načtené (hledání, citace, stovky nepřečtených) NENAČÍTÁ historii od
nejnovější až k ní. Načte **okno kolem ní** (`getChatHistory` se záporným `offset`: půl stránky
novějších, půl starších) a tím **nahradí** celý seznam. Ke starším se stránkuje jako vždy, k novějším
u spodní hrany (`loadNewer`, `offset = -PAGE_SIZE`), dokud stránka nepřinese nic nového.

- **`ConversationState.reachedEnd`** = okno sahá k nejnovější zprávě. Dokud ne, **cizí nové zprávy
  se zahazují** (`withMessage`) — za oknem by udělaly díru — a dorazí se stránkováním. Vlastní
  ODESÍLANÁ zpráva seznam srovná na ni (`reachedEnd = true`, `ScrollAnchor.Bottom`), starší
  se dotáhnou obvyklým stránkováním; poslat z historie tedy znamená skočit dolů.
- **Tlačítko „dolů" je v okně vidět vždy** a vede přes `jumpToLatest` (seznam znovu od nejnovější),
  ne rolováním — nejnovější zprávy v okně nejsou.
- **Stránka se použije, jen když okno mezitím nikdo nevyměnil** (`withNewerPage` / `withOlderPage`
  kontrolují krajní id). Pomalá stránka ze starého okna by se jinak vmíchala do nového.
- **Dotažená stránka k přítomnosti není „nová zpráva"** (`NewestItem.live`): posune nejnovější
  klíč, ale nesmí ukotvit seznam dole (`requestScrollToItem(0)` by přeskočil všechno mezi) ani
  připsat odznak nových zpráv.
- **Stav seznamu patří jednomu oknu** (`ConversationState.window`, `rememberSaveable(window)` nad
  `LazyListState`). Při výměně celého seznamu odešly všechny položky naráz a `animateItem` je
  nechal doznít na místě: předěl „Dnes" visel přes zprávy z roku 2021 a jezdil se scrollem.
  **Past: `remember { derivedStateOf { listState… } }` BEZ klíče** si pak pamatuje původní stav
  seznamu — plovoucí datum hlásilo „Dnes" nad rokem 2021. Všechno, co z `listState` odvozuje,
  má `listState` v klíči.
- **Reklama se v okně nekreslí.** Stojí pod nejnovější zprávou a ta v okně není.
- **TDLib smí vrátit míň, než se chtělo**, takže konec se pozná jen podle toho, že stránka
  nepřinesla nic nového — stejně jako konec historie.
- **Citace míří na STŘED obrazovky, nepřečtené k horní hraně** (`ScrollAnchor`). U citace je
  potřeba vidět, na co zpráva navazuje; u nepřečtených se čte od první dolů.
- **Zvýraznění je nutné.** Po skoku doprostřed historie by jinak nebylo poznat, na kterou
  z bublin citace mířila. Je to pruh přes celou šířku, ne obarvení bubliny — odchozí `primary`
  by změnu nesla špatně a u příchozí by to vypadalo jako změna stavu zprávy.
- **Citace si opakuje dlouhý stisk.** Má vlastní klikání, takže by stisk k bublině nepustila
  a nad citací by nešla otevřít nabídka — stejná past jako u fotky.
- **Tlačítko dolů je uprostřed** (`FabPosition.Center`). Vpravo viselo přímo nad tlačítkem
  odeslání a mikrofonu a ťuknutí o kousek vedle spustilo místo skoku dolů nahrávání.
- **Tlačítko dolů je kruh, s počtem pilulka.** Mezera před počtem je v obsahu, ne ve `spacedBy`:
  skrytý `AnimatedVisibility` je pořád potomek a rozestup by kruh nafoukl.
- **Ukáže se až o pár položek výš** (`SCROLL_DOWN_THRESHOLD_ITEMS`), ne při prvním pixelu —
  u spodní hrany by blikalo pod prstem.

### Práva psát

Kde se do chatu psát nesmí, není vstupní pole (`WriteAccess`, pravidlo `writeAccess`
v `core/telegram/WriteAccess.kt`, testy `WriteAccessTest`). Pole, ze kterého zpráva skončí
chybou až po odeslání, je horší než žádné. Kanál bez práva publikovat má místo něj tlačítko
Ztlumit / Zapnout zvuk, omezená skupina a chat, ve kterém uživatel není, větu proč.

- **Rozhoduje vlastní postavení ve skupině, ne typ chatu.** V kanálu publikuje vlastník
  a správce s `canPostMessages`; ve skupině výchozí práva (`Chat.permissions.canSendBasicMessages`),
  u omezeného člena jeho vlastní (`ChatMemberStatusRestricted.permissions`) — ta výchozí přebíjejí
  oběma směry. Správce a vlastník píšou do skupiny vždycky.
- **Postavení je lokální dotaz** (`getSupergroup` / `getBasicGroup`). Když ho zjistit nejde,
  psaní se NEBLOKUJE: pole navíc skončí nanejvýš chybou, chybějící pole by uživatele od chatu
  odřízlo.
- **Práva se mění za běhu** (`accessUpdates`: `UpdateChatPermissions`, `UpdateSupergroup`,
  `UpdateBasicGroup`). Update skupiny chodí i kvůli počtu členů, takže se přístup čte znovu
  a dál jde jen změna (`distinctUntilChanged`). Tudy se vrátí i pole po „Přidat se".
- **Převedená skupina** (`BasicGroup.isActive == false`) žije dál jako supergroup; do staré se
  nepíše, takže se bere jako „nejste v chatu".
- **Swipe na odpověď se v chatu jen pro čtení nekoná** (`ConversationState.canWrite`); nabídka
  nad zprávou se řídí `canBeReplied` od serveru jako dřív.
- **Video kolečka mají vlastní právo** (`ContentAccess.videoNotes` z `canSendVideoNotes`), bez
  výchozí hodnoty jako ankety. Kde smí jen hlasovky, tlačítko nepřepíná; kde nesmí ani jedno,
  je z něj neaktivní odeslání.
- **Druhy obsahu mají vlastní pravidlo** (`ContentAccess`, `contentAccess` vedle `writeAccess`,
  testy `WriteAccessTest`). Skupina smí povolit text a zakázat fotky; co se poslat nesmí, se
  nenabízí — sponka schová položku, mikrofon ustoupí odeslání, výběr zůstane na emoji.
  **Sponka ale nezmizí nikdy**, dokud jde psát: poloha je v TDLib „základní zpráva"
  (`canSendBasicMessages` jako text), takže v ní zůstane aspoň „Poloha". **Obě pravidla se musí shodovat v tom, koho omezení míjejí:** správce
  a vlastník omezení nemají, členovi platí výchozí práva skupiny, omezenému členovi jeho vlastní.
- **Neznámé postavení NEOMEZUJE nic**, stejně jako u psaní. Chybějící sponka odřízne uživatele
  od chatu víc než chyba po odeslání, která přijde jednou za čas.
- **Filtrovat se musí i to, co vydá systémový výběr** (`allowedByContent`, testy
  `StagedAttachmentsTest`). Výběr fotek vydá fotky i videa jedním tahem, takže ve skupině jen
  pro fotky se mezi vybranými objeví video — a bez filtru by skončilo chybou po odeslání.
- **Rozhoduje, JAKO CO příloha odejde, ne čím je.** Režim „Odeslat jako soubor" posílá fotku
  i video přes `InputMessageDocument` (`toInputContent`), takže se řídí právem na SOUBORY.
  Kdo to plete, nabídne přílohu, kterou server odmítne.
- **UI nestačí, pojistka patří i do ViewModelu.** Výběr samolepek se bez práva neotevře, jenže
  sada jde otevřít ťuknutím na samolepku v historii a poslat se dá z ní. Hláška je lepší než
  chyba od serveru.
- **Omezená skupina se bez druhého účtu nevyrobí.** Vlastník omezení nemá, takže vlastní
  testovací skupina pravidlo neprověří — ověřuje se podstrčeným `ContentAccess` v `chatAccess()`,
  jako vzhled karty aktualizace. Ověřeno na emulátoru: „jen fotky" nechá sponku s jedinou
  položkou, smajlík místo samolepky a odeslání místo mikrofonu; „nic" sponku schovávalo celou
  (dnes v ní zůstane „Poloha", ověřené to s podstrčeným stavem znovu není).

### „Píše…" v konverzaci

Činnost protějšku (ve skupině kohokoli) se kreslí **v seznamu zpráv tam, kam zpráva naskočí**:
tvář toho, kdo píše, a skákající trojtečka (`TypingItem`), jako v Google Zprávách. Protějšek
naopak vidí, že píšeme my.

- **Bez bubliny.** Je to informace o dění v chatu, ne zpráva — patří mezi předěly a servisní
  oznámení, které se kreslí holé na ploše. Bublina by tvrdila, že už něco přišlo.
- **V hlavičce není.** Tam zůstává dostupnost; „píše…" ji dřív vytlačovalo a od místa, kde
  zpráva přijde, bylo daleko.
- **Činnost se bere ze `ChatRepository`, ne z vlastního odběru.** Seznam chatů ji už sleduje
  i s odpočtem zhasnutí a odfiltrováním vlastní činnosti; druhá kopie téže logiky by se s ní
  rozešla. `ChatRepository.typing(chatId)` nese i KDO (`ChatTyping.senderId`) — `ChatSummary`
  drží jen druh, seznamu chatů stačí. Tvář dohledá `ConversationRepository.sender` z keše
  odesílatelů; v soukromém chatu je to tvář chatu.
- **Ve skupině lícuje s bublinami** (tvář 32 dp ve slotu 40 dp jako u zpráv), v soukromém chatu je
  tvář jen malá ikonka (24 dp) — zprávy tam tvář nemají. Nezjištěný odesílatel ve skupině nechá
  prázdné místo, ne tvář skupiny: ta by tvrdila, že píše skupina.
- **Stojí pod nejnovější zprávou a nad reklamou**, jen když okno sahá k přítomnosti (`reachedEnd`)
  a ne v kanálu. Klíč je jeden (`TYPING_KEY`), výměna píšícího je jen překreslení.
- **Při objevení se seznam ukotví dolů** (`requestScrollToItem(0)`), když je uživatel dole —
  seznam drží pozici podle první viditelné položky (nejnovější zprávy), takže by se indikátor
  vložil pod spodní hranu. Kdo čte historii, toho nevytrhne.
- **Trojtečka stačí jen u psaní.** Ostatní činnosti (nahrává hlasovku, vybírá samolepku) doplní
  slovy vedle teček. Tečky skáčou jen v části periody (`dotLift`, testy `ConversationItemsTest`),
  zbytek stojí — bez pauzy by vlnily neklidně. Fáze se čte až v `graphicsLayer`.
- **Vlastní „píšu" se opakuje nejvýš jednou za 5 s** (`TYPING_REPEAT`). Server činnost zhasíná
  asi po šesti vteřinách; posílat ji na každý úhoz by byl dotaz na znak.
- **Vymazání textu psaní výslovně ukončí** (`ChatActionCancel`), odeslání ne — to ukončí server
  sám. Po odeslání se jen zapomene čas posledního ohlášení, aby další zpráva ohlásila psaní hned.
- **Hlásí se jen ruční úpravy textu.** Vyprázdnění pole po odeslání jde mimo `onValueChange`,
  takže by se o něm `onDraftChanged` stejně nedozvěděl.
- **Chyba se jen loguje.** V kanálu bez práva psát `sendChatAction` selže; snackbar kvůli
  „píše…" by uživatele jen obtěžoval.

### Naplánované zprávy a odeslání bez zvuku

Podržení odesílacího tlačítka nabídne **Odeslat bez zvuku**, **Naplánovat zprávu…** a v soukromém
chatu **Odeslat, až bude online** (`SendOptions`, `MessageSchedule`; co se nabídne, je čistá
funkce `sendMenuOffer`, testy `SendMenuTest`). Naplánované zprávy má chat v seznamu za hodinami
u pole (`ScheduledMessagesSheet`): odeslat hned, změnit čas, smazat.

- **Naplánovaná zpráva do historie NEPATŘÍ — a tři cesty ji tam vmíchají**, když se nehlídají:
  `UpdateNewMessage` (naplánovaná přijde jako nová zpráva), `UpdateMessageSendSucceeded`
  (potvrzení odeslání na server — `withSentMessage` ji do seznamu vloží) a úprava zprávy
  (přeplánování je `editMessageSchedulingState`, tedy `UpdateMessageEdited` — `merge` ji vloží
  taky). Repository proto filtruje `schedulingState != null` na všech třech. Až zpráva v čase
  odejde, přijde znovu jako běžná, s novým id.
- **Že chat naplánované zprávy má, hlásí TDLib** (`Chat.hasScheduledMessages`,
  `UpdateChatHasScheduledMessages`) — i když zpráva odejde nebo se naplánuje z jiného zařízení.
  Podle toho jsou hodiny u pole; seznam se čte až při otevření (`getChatScheduledMessages`)
  a po každé akci znovu. Prázdný se zavře sám.
- **„Odeslat hned" je přeplánování na `null`**, ne nové odeslání — zpráva odejde tak, jak byla
  naplánovaná, i s odpovědí a přílohami.
- **Tlačítko odeslání `onLongClick` nemá** (material3 1.12 ho u `IconButton` nenabízí), takže
  dlouhý stisk hlídá `longPressInstead` v `Initial` a po něm **spotřebuje zbytek gesta** —
  jinak by puštění prstu zprávu zároveň odeslalo.
- **„Až bude online" jen se ZNÁMOU dostupností** (`Presence.Offline`). TDLib: „the user's
  online status must be known". Bot dostupnost nemá, skrytou („naposledy nedávno") server
  odmítne, a online protějšek ji nepotřebuje.
- **Ve vlákně komentářů se neplánuje** (`hasScheduledMessages` tam vrací `false`): naplánované
  patří celé skupině a v komentářích by se míchaly cizí.
- **Čas: napřed den, pak hodina** (`FutureDayDialog`, `TimeOfDayDialog` — vytažené z formuláře
  ankety, sdílí je oba). Dnešek je předvybraný a čas za hodinu od teď, protože se nejčastěji
  plánuje „na později dnes". **Nejdřív za minutu, nejpozději za rok** (`scheduleValid`); čas
  vybraný na minuty by jinak mohl minout dřív, než odejde. Neplatný čas nechá text v poli.
- **Naplánovaná zpráva neletí z pole** a do seznamu se nepřidává; místo toho snackbar
  „Zpráva je naplánovaná" — jinak by po odeslání nebylo vidět nic.
- **Past s časovým výběrem přes `adb`**: po ťuknutí na hodinu se ciferník přepíná na minuty
  animací a ťuknutí během ní se ztratí — mezi ťuknutími pauza aspoň vteřinu.
- **Ověřeno na emulátoru v chatu s botem**: nabídka nad tlačítkem (bez „až bude online" — bot),
  naplánování s ikonou hodin a snackbarem, seznam („zítra 18:55"), odeslat hned (bot zprávu
  dostal, v historii jednou), **automatické odeslání v čase** (17:55 — bot dostal, hodiny
  zmizely), změna času („pondělí 18:56"), smazání, odmítnutí času, který už minul, a odeslání
  bez zvuku (bot zprávu dostal; jestli bez zvuku, Bot API neprozradí). **Neověřeno**: „až bude
  online" (chce člověka se známou dostupností), naplánované přílohy, iOS (jen přeložené).
- **Hlasovka a anketa jdou naplánovat taky** — podržením odesílacího tlačítka během nahrávání
  a podržením „Odeslat" ve formuláři ankety (bez zvuku, naplánovat, u hlasovky i „až bude
  online"; efekt jen u textu, výběr efektu posílá obsah pole). Naplánovaná hlasovka se do
  konverzace nepřidává, jen ohlásí (`sendMedia(scheduled = true)`), jako text.
- **Nahrávání je po dobu nabídky a výběru času POZASTAVENÉ** (`VoiceRecorder.pause`), jinak by
  se do hlasovky nahrálo šustění při vybírání. Zavření bez volby nahrávání obnoví — ale **položka
  `ExpressiveMenu` volá NEJDŘÍV `onDismiss` a teprve pak svou akci**, takže obnovení v `onDismiss`
  proběhlo i při volbě „Naplánovat" (hlasovka vyšla o výběr času delší). O obnovení se proto
  rozhoduje až po zavření nabídky (`LaunchedEffect(sendMenuOpen)`), kdy už akce proběhla.
- **Anketa naplánovaná jako odpověď odpověď nad polem spotřebuje**, stejně jako odeslaná —
  ohlásí to formulář (viz „Ankety a kvízy", odpověď anketou). Ověřeno na emulátoru: odpověď
  zmizela a „Odeslat hned" z naplánovaných poslalo anketu s `reply_to_message` (Bot API).
- **Ověřeno na emulátoru v chatu s botem**: nabídka nad nahrávanou hlasovkou, zastavený čas
  během nabídky, naplánování a „Odeslat hned" (bot dostal hlasovku dlouhou jen o nahraný čas),
  zavření nabídky a nahrávání dál, bez zvuku; anketa naplánovaná z formuláře v seznamu
  naplánovaných. iOS jen přeložené.
- **Samolepka a GIF mají volby odeslání u PODRŽENÉ buňky**, ne na podržení (`StickerCell`,
  `GifCell`, společné `SendOptionsButton`, `SendMenuChoice`): podržení samolepku rozhýbe a druhé gesto nad ní by se s tím pralo. Tlačítko
  je v protějším rohu než hvězdička oblíbených a nabídne totéž co odesílací tlačítko bez efektu
  (bez zvuku, naplánovat, až bude online). Nabízí ho jen výběr v konverzaci — sada otevřená
  z historie a formulář ankety `sendMenu` nepředávají. Ověřeno na emulátoru v chatu s botem:
  naplánovaná samolepka v seznamu naplánovaných, „Odeslat hned" (bot ji dostal) a bez zvuku;
  naplánovaný GIF a „Odeslat hned" (bot dostal animaci).
- **Podržet jde každý GIF, ne jen uložený.** Dřív podržení znamenalo jen „odebrat z uložených",
  takže nalezený GIF podržet nešel. Teď podržený GIF (klíč položky, uložený bývá i mezi
  populárními) ukáže tlačítko voleb a jen uložený navíc křížek.
- **Pozor při ověřování přes `adb`: ťuknutí do mřížky GIFů GIF rovnou pošle.** Zpráva zavřela
  sheet a další ťuknutí skriptu šla do seznamu chatů. Pole hledání se proto hledá dumpem až
  po otevření sheetu, ne podle souřadnic z předchozího snímku.
- **`StickerPickerSheet` je `internal`** — s parametrem interního `SendMenuOffer` by veřejná
  funkce neprošla překladem (`EXPOSED_PARAMETER_TYPE`).

### Úprava zprávy

„Upravit" v nabídce nad zprávou přepne vstupní pole do úpravy: karta místo citace ukazuje původní
text, tlačítko je fajfka a potvrzení volá `editMessageText`.

- **Nenabízí se u zpráv, jejichž obsah pole neunese** (`composerEdit`, `composedFormatsOf`).
  Ruční formátování (tučné, kurzíva, podtržení, přeškrtnutí, spoiler, kód i blok kódu s jazykem,
  citace i sbalitelná) i odkaz s vlastním popiskem pole nese (viz „Formátování při psaní");
  odkazy psané v textu, `@jméno`, hashtagy, příkazy a čas v médiu se při uložení dohledají znovu.
  Zmínka jménem, vlastní emoji a datum by úprava tiše smazala — u nich „Upravit" není. Automatický odkaz se
  pozná tak, že jeho text v adrese opravdu stojí.
- **Popisek přílohy je JINÉ volání** (`editMessageCaption` vedle `editMessageText`), takže
  pravidlo neříká jen „jde to", ale i CO se upravuje (`ComposerEdit.Text` / `Caption`). Jedno
  volání za druhé zaměnit nejde: úprava textu by popisek nezměnila a naopak.
- **Popisek jde i přidat a odebrat.** „Upravit" se proto u přílohy nabízí i bez popisku (jinak
  by ho k fotce nešlo doplnit) a prázdné pole je platná úprava, která popisek smaže — odesílací
  tlačítko proto v téhle jediné situaci zůstává aktivní i s prázdným polem. U textu zprávy ne:
  prázdnou zprávu server odmítne.
- **Samolepka ani animované emoji popisek nést nemůžou** (`carriesCaption`, `when` bez `else`),
  takže se u nich „Upravit" nenabízí — server by úpravu odmítl.
- **Hlavička nad polem musí říct, že se upravuje POPISEK.** Příloha v konverzaci zůstane, ať se
  v poli píše cokoli; bez toho by nebylo poznat, že se nepíše nová zpráva. U přílohy bez popisku
  se původní text nekreslí vůbec — prázdný řádek vypadá jako chyba vykreslení.
- **Upravená příloha si kartu drží i s prázdným popiskem** (`hasCaption = text.isNotBlank() ||
  isEdited` v `MediaMessageItem`): v pruhu pod snímkem zůstane samotné „upraveno". Dokud popisek
  nešlo odebrat, byl ten stav nedosažitelný; teď je vědomý — jinak by se u fotky nikde nedozvěděl,
  že se zpráva změnila, a čas úpravy je až v „Informace".
- **Rozepsaný koncept se po dobu úpravy odloží a pak vrátí.** Úprava je vsuvka; kdo si uprostřed
  psaní všimne překlepu, nechce přijít o rozepsanou zprávu. Při přepnutí z jedné úpravy do druhé
  se odložený koncept nepřepisuje.
- **Během úpravy se neukládá koncept ani „píše…"** (`onDraftChanged` se vrátí hned). V poli je
  text staré zprávy; koncept by přepsal ten odložený.
- **Z úpravy se odchází hned, ne po odpovědi serveru.** Výsledek dorazí `editedMessages` jako
  každá jiná úprava. Když selže, úprava se vrátí do pole i s textem.
- **Beze změny se nic neposílá** — server by odpověděl chybou.
- **Text do pole jde přes `InputFill`**, společně s obnoveným konceptem. Koncept má
  `onlyIfEmpty` (nesmí přepsat, co uživatel mezitím napsal), úprava pole přepisuje vždycky.
- **Výběr jde otevřít i při úpravě, ale jen s emoji** (`stickersAvailable = false`): přepínač
  se schová, ikona je vždycky smajlík. Samolepka do textu upravované zprávy nepatří. Uložená
  volba části výběru se tím nemění. Emoji vložené při úpravě neukládá koncept ani „píše…" —
  `onDraftChanged` se při úpravě vrací hned.
- **Po „Upravit" se pole samo nefokusuje.** Že se něco stalo, ukazuje karta s původním textem
  a fajfka místo odeslání; klávesnici si uživatel vyvolá ťuknutím do pole. Dřív si pole fokus
  a klávesnici vyžádalo samo (uživatel si to nepřál).

### Formátování při psaní

Dokud je v poli označený text, vyjede pod ním lišta s ikonami: Tučné, Kurzíva, Podtržené,
Přeškrtnuté, Odkaz, Spoiler, Kód, Citace a Bez formátování (`FormattingBar`); použitý formát má
tlačítko zapnuté. Odejde
se zprávou, uloží se s konceptem a vrátí se do pole při úpravě zprávy. Model a pravidla jsou čisté
funkce v `core/telegram/TextFormatting.kt` (testy `TextFormattingTest`).

- **`TextFieldValue` formátování NEUNESE.** `BasicTextField(TextFieldValue)` sice bere
  `AnnotatedString`, ale `EditingBuffer` z Compose po každé úpravě vrací
  `AnnotatedString(toString())` — styly zmizí s prvním úhozem (ověřeno ve zdrojích ui-text 1.12).
  Rozsahy proto vede `ComposerDraft` zvlášť a pole je jen kreslí (`FormattingTransformation`,
  `OffsetMapping.Identity`).
- **Rozsahy se posouvají z ROZDÍLU textu** (`afterEdit`): společný začátek a konec, mezi nimi
  jeden nahrazený úsek. Nejednoznačný rozdíl („a" za „a") rozhodne kurzor po úpravě. Na konec
  rozsahu se píše dál ve formátu, na začátek ne; přepsaný formátovaný výběr formát drží.
- **Každá změna textu jde přes `ComposerDraft.update`** — obrazovka s ním pracuje jako s
  `TextFieldValue` přes delegát (`var draft by composer`), takže emoji z výběru, zmínka, návrh
  odpovědi i vyprázdnění po odeslání rozsahy posunou samy. Nové místo, které do pole píše, na
  formátování myslet nemusí; kdyby psalo mimo delegát, rozsahy by ujely.
- **Compose 1.12 má vlastní sledované styly** (`TextFieldBuffer.addStyle` → `TrackedRange`,
  `TextFieldState.textStyles`), ale jen nad `TextFieldState`, experimentálně a s významem
  schovaným ve `SpanStyle`. Přechod pole na `TextFieldState` by znamenal přepsat desítky míst
  kolem konceptu; vlastní model je menší a testovatelný bez Compose.
- **Bez výběru lištu otevře ikona „Aa" v kartě pole** (jen když je otevřená klávesnice; se
  schováním klávesnice se lišta zavře) a formát platí pro další psaní od kurzoru — „zapnout
  tučné a psát dál".
  Stav je `PendingFormats` v `ComposerDraft` (pravidla `toggledPending`, `typingAt`, `typed`,
  testy `TextFormattingTest`).
  - **Čekající formát je jednorázový** a použije se jen na text napsaný přesně na kurzor. Dál
    pokračuje sám: text psaný na konec rozsahu do něj patří (`afterEdit`), takže napsané tučné
    slovo pokračuje tučně a text napsaný s vypnutým tučným za ním zůstane bez něj. Nic se tedy
    nemusí hlídat při každém úhozu.
  - **Vypnutí uprostřed tučného slova ho rozdělí** — napsaný kus z rozsahu vypadne (`removed`)
    a další psaní už za ním pokračuje netučně.
  - **Přesun kurzoru čekající formát zahodí**; patří místu, kde se přepnul. Oprava slova
    klávesnicí nebo vložení jinam ho nepoužije (`insertedAtPending` bere jen čisté vložení
    na kurzor).
  - **Tlačítko ukazuje, jak se bude psát** (`typingAt`): zapnuté i u kurzoru na konci tučného
    slova, kde by se psalo tučně i bez ťuknutí. „Bez formátování" vypne všechno, co by pokračovalo.
  - **Citace bez výběru jde na řádek s kurzorem**, ne na další psaní — je to blok přes řádky.
    Jen na prázdném řádku čeká na psaní jako ostatní. **Odkaz bez výběru zašedne**: potřebuje
    popisek.
  - **„Aa" a výběr samolepek sdílejí JEDNO místo v kartě**: s otevřenou klávesnicí „Aa", se
    zavřenou samolepka (emoji / GIF), výměna zvětšením jako u odesílacího tlačítka. Při psaní
    emoji nabízí klávesnice sama a náš výběr by ji stejně zavřel; bez klávesnice zase není co
    formátovat. Dokud stály vedle sebe, ukusovaly textu 96 dp a „Aa" se s fokusem objevovalo
    DO ŠÍŘKY — rozepsaný víceřádkový text se přeskládal při každém ťuknutí do pole. Teď se šířka
    pole nemění nikdy (ověřeno po snímcích se zpomalenými animacemi: zalomení stejné).
    - **Rozhoduje viditelná klávesnice, ne fokus** (`typing` = fokus a nenulová výška
      `WindowInsets.ime`, přes `derivedStateOf`). Po schování klávesnice gestem zpět drží pole
      fokus dál a samolepka by se podle fokusu neukázala vůbec. Výška klávesnice funguje i na iOS
      (`isImeVisible` je jen na Androidu).
    - **S hardwarovou klávesnicí „Aa" není** (softwarová se neukáže). Formátovat jde dál
      označením textu, jen „zapnout tučné a psát dál" tam chybí.
    - **Svislé skládání ikon u víceřádkového textu (zvažováno) ne**: dvě ikony pod sebou chtějí
      ~100 dp výšky, tedy až od tří řádků, a při přepnutí se odstavec přeskládá do širší plochy
      — slova i kurzor uprostřed psaní poskočí o řádek. Rozvržení se má měnit ve chvíli, kdy
      není co přeskládat (otevření klávesnice), ne při zalomení.
- **Lišta, ne položky v kontextové nabídce výběru** (tak to bylo napřed). Ikony u vlastních
  položek nabídka výběru na telefonu NEZOBRAZÍ: na Androidu ji kreslí `ActionMode`
  (`AndroidTextContextMenuToolbarProvider`) a `leadingIcon` z `TextContextMenuItem` zahodí —
  ikony kreslí jen rozbalovací nabídka pro myš; na iOS ji kreslí systém (`UIEditMenu`), ať
  `item` bere composable ikonu, nebo ne. Položky navíc skončily v přetečení (⋮) a nešlo z nich
  poznat, co už na výběru je.
- **Lišta je POD textem, ne nad ním.** Systémová lišta výběru (Vyjmout, Kopírovat…) se kreslí
  nad výběrem a lištu nad polem přikryla — ověřeno na emulátoru, vidět zbyla jen ikona citace.
- **Zapnuté tlačítko je `primary`**, ne `secondaryContainer` jako u vybraného chatu v seznamu: karta
  pole je přitónovaná do `primaryContainer` a světlý kontejner na ní skoro nebyl vidět.
- **Na úzkém displeji se řada posouvá do strany.** Minimální dotyková plocha 48 dp zůstává
  i u menšího kontejneru (`minimumInteractiveComponentSize`), takže zmenšení tlačítek nepomůže;
  na složeném Foldu se osm tlačítek vedle odesílacího nevejde a „Bez formátování" ujíždí.
- **Citace jde po celých řádcích** (`toggled` roztáhne výběr), ostatní klienti ji kreslí jako blok.
  **Kód přes víc řádků je blok kódu** (`pre`), v řádku kód v řádku — jedna položka nabídky.
  „Kód" i „Citace" přepínají RODINU (`TextFormat.family`): blok kódu i sbalitelná citace se jimi
  vypnou, citace se nevnoří.
- **V kódu se nic nedohledává** (`detectEntities`): kód nesmí obsahovat jiné entity. Tučné
  a kurzívu uvnitř kódu TDLib při odeslání sám zahodí bez chyby (ověřeno: bot dostal jen `code`).
- **Doménový `MessageEntityType.Code` nese `block` a `language`** — kvůli úpravě: bez nich by se
  blok kódu vrátil jako kód v řádku a jazyk bloku by úprava smazala.
- **Jazyk bloku kódu nese `FormatSpan.language`** a odchází jako `preCode` (bez jazyka `pre`,
  prázdný jazyk je jako žádný). Tlačítko s jazykem („Jazyk kódu", popiskem je jazyk sám, třeba
  „kotlin") se v liště objeví hned za Kódem, jen když kurzor nebo výběr leží v bloku kódu
  (`codeBlockAt`); dialog drží obrazovka ze stejného důvodu jako dialog odkazu. Jazyk je jedno
  slovo bez mezer do 32 znaků (`codeLanguageOf`), prázdné pole ho sundá. Rozsahy se spojují jen
  se stejným jazykem (`merged`), takže sousední bloky v různých jazycích zůstanou dva. Uložený
  stav pole má páté pole pro jazyk; stav se čtyřmi poli (starší verze) se čte dál.
- **Sbalitelná citace je přepínač za Citací** (`unfold_less`, jen když kurzor nebo výběr leží
  v citaci, `quoteAt`). Mění jen druh citace (`toggledExpandable`), rozsah zůstává — obyčejnou
  a sbalitelnou TDLib stejně nedovolí vnořit.
- **Odkaz s popiskem je formát s adresou** (`TextFormat.Link`, `FormatSpan.url` →
  `TextEntityTypeTextUrl`). „Odkaz" v liště otevře dialog s adresou (`LinkDialog`,
  `linkTargetOf`: bez schématu `https://`, jinak jen `http(s)`, `tg://` a `mailto:`). Dialog
  drží obrazovka, ne lišta, a úsek se zachytí při ťuknutí: dialog vezme poli fokus a lišta
  s ním zmizí i se svým stavem. **Odkaz se psaním na konci neprodlužuje** (na rozdíl od tučného)
  a sousední odkazy na různé adresy se nespojí. V odkazu se nic nedohledává — adresa napsaná jako
  popisek by jinak byla druhým odkazem přes tentýž text. Tlačítko stojí hned za písmem: na
  složeném Foldu by jako osmé ujelo za okraj. Uložený stav pole odděluje pole znaky `\u001E`
  a `\u001F`, protože adresa obsahuje „:" i „;".
- **Koncept nese formátování** (`saveDraft(formats)`, obnova `draftFormatsOf`). Na rozdíl od
  úpravy se koncept nevzdává: z konceptu jiného klienta s vlastním emoji se vrátí aspoň tučné.
- **Změna jen formátování je změna**: uloží koncept (`onDraftChanged` bez změny textu neohlašuje
  „píše…" ani nezjišťuje náhled) a úprava zprávy, která mění jen formát, se odešle.
- **Spoiler se v poli neskrývá**, jen má podklad — pisatel musí vidět, co píše. Citace má v poli
  tónovaný podklad, ne pás přes celou šířku (ten se kreslí z layoutu zprávy).
- **Ověřeno na emulátoru** (anglicky, Fold složený): tučné v poli i v odeslané bublině, úprava
  tučné zprávy s přidanou kurzívou („edited", tučné zůstalo), koncept s tučným po zabití
  aplikace; bot přes Bot API dostal `code`, `bold` + `spoiler` a `blockquote` (roztažený na
  celý řádek) a `text_link` z dialogu; úprava zprávy s odkazem ho vrátí do pole a dialog nabídne
  změnu i odebrání. Formát bez výběru (česky, Pixel 9):
  „Aa" otevře lištu, zapnuté tučné platí pro napsané „XY", vypnuté pro „zz" za ním; odkaz je
  bez výběru zašedlý. Formátovaná zpráva psaná bez výběru se přes Bot API neověřovala — odeslání
  jde stejnou cestou rozsahů. Jazyk bloku kódu a sbalitelná citace (česky, Pixel 9): tlačítko
  jazyka se objeví za Kódem až nad blokem kódu, po uložení ukazuje „kotlin"; přepínač sbalitelné
  citace se objeví za Citací až v citaci; bot přes Bot API dostal `pre` s `language: kotlin`
  a `expandable_blockquote`. iOS jen přeložené.
- **Dva řádky na emulátoru jdou hardwarovým Enterem** (`adb shell input keyevent 66`) — vloží nový
  řádek, kdežto Enter softwarové klávesnice odesílá (`ImeAction.Send`). Rozepsaný koncept se dá
  při zkoušce odložit do schránky (`input keycombination 113 29`, pak `113 52`) a vrátit
  (`113 50`); Gboard ale při vložení do prázdného pole přidá na začátek mezeru, takže se po
  vrácení musí porovnat text (`uiautomator dump`) a mezera smazat.

### Náhled odkazu při psaní

Když rozepsaný text obsahuje odkaz, nad polem se po pauze v psaní ukáže náhled, jak ho připojí
server (`LinkPreviewHeader`: web, titulek, malý obrázek). Ťuknutí nebo ikona voleb otevře
`LinkPreviewOptionsSheet`: velký nebo malý obrázek, nad textem nebo pod ním, bez náhledu. ✕ náhled
vypne. Pravidla jsou čisté funkce v `ComposerLinkPreview.kt` (testy `ComposerLinkPreviewTest`).

- **`getLinkPreview` se volá, jen když text odkaz OPRAVDU má** (napřed lokální `getTextEntities`).
  TDLib u něj píše „do not call this function too often" a na text bez odkazu vrací 404. Pauza
  po posledním úhozu je 700 ms (`LINK_PREVIEW_DELAY_MS`). 404 je odpověď „náhled není", ne chyba.
- **Vynucená velikost bez adresy NEPLATÍ.** `forceSmallMedia` a `forceLargeMedia` TDLib bez
  výslovné `url` ignoruje. Adresa se proto doplní při odeslání z náhledu, který uživatel
  právě vidí (`forSending`). Ověřeno na emulátoru: YouTube, kde server volí velký obrázek,
  odešel s malým.
- **S víc odkazy v textu jde vybrat, ke kterému náhled patří** (sekce „Odkaz" v sheetu,
  `LinkPreviewOptions.url`, `selectedLink`, testy `ComposerLinkPreviewTest`). Odkazy vrací
  repository spolu s náhledem (`TextLinkPreview.links`) v podobě, jak stojí v textu — holé
  `example.com` zůstane holé a jde zpátky do `getLinkPreview` jako `url`. Označený řádek se
  porovnává s odkazem z textu, ne s `LinkPreview.url`: ta od serveru bývá upravená (`https://`,
  lomítko). **Výběr platí, jen dokud odkaz v textu stojí** (`forPreview(links)`): po jeho smazání
  by zpráva odešla s náhledem něčeho, co v ní už není. Výběr jiného odkazu se ptá hned, bez pauzy
  na psaní, a uloží se s konceptem (`toDomain` adresu přenáší). Ověřeno Bot API: druhý odkaz
  s malým obrázkem přišel jako `url` YouTube s `prefer_small_media`.
- **Text vložený sdílením je koncept jako napsaný** (`receiveShared` zapíše `draftText`). Pole
  se vyplní bez `onDraftChanged`, takže ho ViewModel neznal — a první uložení konceptu (tady
  změna volby náhledu) zapsalo prázdný koncept: sdílený text po odchodu z chatu zmizel.
- **Velikost se nabízí jen s `hasLargeMedia`** (`LinkPreview.canChangeSize`). Wikipedia ji nemá,
  YouTube ano. Volba, která nic nezmění, se nekreslí.
- **Jiný odkaz = volba znovu od začátku, kromě vypnutí** (`forPreview`). Vypnutí platí do
  vyprázdnění pole nebo odeslání. Kdo náhled zavřel, nechce, aby se vrátil s dalším písmenem.
- **Volba patří ke konceptu** (`DraftMessageContentText.linkPreviewOptions`) a po návratu do
  chatu se vrátí (`ChatInfo.draftLinkPreview`). Obnovený koncept si náhled zjistí sám —
  vyplnění pole z konceptu `onDraftChanged` nevolá. Totéž platí pro sdílený text.
- **S přílohami a při úpravě POPISKU se náhled neukazuje** (`composerLinkPreview`): text
  u příloh je popisek a `editMessageCaption` náhled nenese.
- **Úprava textu zprávy volbu náhledu PŘEBÍRÁ a jde ji měnit** (`ChatMessage.linkPreviewOptions`,
  `enterEdit`, `forEdit`, testy `ComposerLinkPreviewTest`). Nad polem je při úpravě tatáž
  hlavička náhledu a tentýž sheet jako při psaní. S výchozí volbou (`null`) by úprava zprávy
  poslané BEZ náhledu náhled vrátila — ověřeno Bot API: s `null` přišel náhled Wikipedie,
  s převzatou volbou zůstal `is_disabled`. Adresa se odešle jen tehdy, když odkaz v upraveném
  textu pořád je (i kdyby se odeslalo dřív, než dorazí náhled upraveného textu). Změna jen
  volby, bez změny textu, je taky úprava. Ze stejného důvodu úprava popisku přenáší
  `showCaptionAboveMedia` (dřív natvrdo `false`, takže popisek nad médiem nastavený jiným
  klientem úprava tiše vrátila pod médium).
- **Náhled konceptu se po dobu úpravy ODKLÁDÁ s ním** (`LinkPreviewStash`), stejně jako text
  konceptu. Stav náhledu je jeden pro pole, takže bez toho by upravená zpráva odešla s volbou
  rozepsaného konceptu a koncept se po úpravě vrátil s volbou upravené zprávy. Volba změněná
  při úpravě se do konceptu neukládá. Vymazaný text úpravy volbu nezahodí — není to nová zpráva.
- **Hlavička náhledu stála v bloku kresleném jen mimo úpravu** (spolu s citací a přílohami),
  takže při úpravě chyběla, i když ViewModel náhled měl. Teď je za ním.
- **Při ověřování úpravy přes `adb` se dlouhý stisk NESMÍ trefit do odkazu**: na přetíženém
  emulátoru dopadl jako ťuknutí a otevřel se Chrome — a další ťuknutí skriptu šla do něj.
  Stisk mířit na slovo před odkazem a po něm zkontrolovat `mCurrentFocus`.
- **Sheet je celý rozbalený** (`enabledValues = Hidden, Expanded`). Napůl rozbalený schoval
  „Poslat bez náhledu" pod okraj — tatáž past jako u ostatních sheetů s víc volbami.
- **`adb input text` s adresou se na emulátoru useká na tečce** („https://en.wikipedia"). Text pak
  odkaz neobsahuje a náhled se správně neukáže. Adresa se píše po kouscích („en", „.",
  „wikipedia"…). A s víc emulátory najednou `adb` bez `-s` odmítne všechno — v zsh se sériové
  číslo nastavuje přes `ANDROID_SERIAL`, proměnná `ADB="adb -s …"` se na slova nerozdělí.

### Koncept (rozepsaná zpráva)

Rozepsaný text se ukládá do TDLib (`setChatDraftMessage`), takže přežije odchod z chatu, je vidět
v seznamu chatů („Koncept: …") a na ostatních zařízeních.

- **Ukládá se s odstupem 1,5 s od poslední úpravy**, ne na každý úhoz — koncept se propisuje na
  server. Při odchodu z chatu se čekání přeskočí v `onCleared`.
- **Ukládání běží v `appScope`, ne ve `viewModelScope`.** Ten se při odchodu z chatu zruší dřív,
  než by poslední úprava stihla odejít. Hodnoty se proto berou při plánování na hlavním vlákně,
  úloha je jen odešle.
- **Odeslání čekající uložení ZAHODÍ.** TDLib koncept při odeslání maže sám (`clearDraft`);
  opožděné uložení by ho vrátilo se starým textem.
- **Koncept se do pole vrací jen, když je prázdné** (`InputFill.onlyIfEmpty`). Než ho TDLib
  vydá, mohl uživatel začít psát. Po vložení se `inputFill` vynuluje — jinak by se po otočení
  vrátil i smazaný.
- **Pole drží `TextFieldValue`, ne `String`.** U vráceného konceptu je potřeba postavit kurzor
  na konec; `onDraftChanged` se volá jen při změně TEXTU, ne při pohybu kurzoru.
- **Citace je součást konceptu** a obnoví se s ním (i když není v načtené historii).
- **Prázdný text koncept maže, i s citací.** Odpověď bez textu by byl koncept, který v seznamu
  nic neukáže.
- **Seznam chatů musí z `UpdateChatDraftMessage` brát i POZICE.** Chat s čerstvým konceptem se
  posune nahoru a jinudy se to nedozvíme. Čas u položky je proto novější z konceptu a poslední
  zprávy — jinak by u chatu nahoře stálo starší datum než pod ním.
- **Koncept v otevřeném chatu se z updatů NEČTE.** TDLib upozorňuje, že
  `UpdateChatDraftMessage` může pro právě otevřený chat přijít se starým obsahem; přepsat jím
  pole by uživateli smazalo, co právě napsal.
- **Ukazuje se jen textový koncept.** Rozpracovaná hlasovka je taky koncept, ale „Koncept:" bez
  obsahu by nic neřekl.

### Hlášení přečtení a indexy

- **Z viditelných položek se bere KLÍČ, ne index.** Index do `state.messages` nesedí: před
  zprávami stojí sponzorované položky, takže v kanálu s reklamou byl výběr posunutý o jejich
  počet a za přečtené se hlásily zprávy, které uživatel neviděl. Klíč zpráv je rovnou jejich id.
  Platí to i do budoucna — jakmile do seznamu přibude cokoli dalšího (datové předěly, předěl
  nepřečtených), indexy se rozejdou znovu, klíče ne.
- **Efekt s odpočtem nesmí záviset na `state.messages`.** `LaunchedEffect(listState, state.messages)`
  se restartoval při každé nové zprávě a 400ms odpočet začínal od nuly, takže se v živém chatu
  nemusel dopočítat vůbec.
- **Vlastní zprávy filtruje ViewModel, ne obrazovka.** Ta zná jen id viditelných položek;
  dohledávat si k nim zprávy by znamenalo držet je kvůli tomu v kompozici.
- **V `LazyColumn` se nehledá průchodem seznamu.** `indexOf(message)` uvnitř `items` a hledání
  citované zprávy přes `firstOrNull` znamenaly kvadratické chování při scrollu dlouhou historií.
  Index dává `itemsIndexed`, citace se hledá v mapě složené jednou na změnu seznamu.

### Tvar bubliny a seskupení

- **Stažené nároží (`BUBBLE_TIGHT`, 4 dp) drží skupinu pohromadě, takže se MUSÍ ptát na OBA
  sousedy.** Dřív bylo spodní nároží na straně odesílatele stažené VŽDY, bez ohledu na souseda:
  tím vznikla dvojice „spodek předchozí + vršek navazující" bez toho, aby bublina musela cokoli
  vědět o novějším sousedovi. Cena za tu zkratku byla, že zaoblení chybělo i tam, kde blok
  SKONČIL — poslední odchozí bublina měla ostré pravé dolní nároží (u příchozích zrcadlově levé)
  a blok světlých bublin končil bez rohového oblouku.
- **Novější soused je o index NÍŽ** (seznam jde od nejnovější), a ta informace už na místě volání
  byla — počítá se z ní profilovka („poslední zpráva bloku"). Bublina proto bere dva příznaky,
  `continuesAbove` a `continuesBelow`, a stahuje jen nároží na straně odesílatele; na druhé straně
  jde bublina k okraji obrazovky a stažené nároží by tam nemělo co navazovat.
- **Startovní strana letu musí mít tvar CÍLOVÉ bubliny.** Odesílaná zpráva je vždycky nejnovější,
  takže pod ní nic nenavazuje a všechna nároží jsou zaoblená — proto tam po téhle změně je
  `RoundedCornerShape(BUBBLE_CORNER)`. Dokud bylo spodní nároží stažené vždy, mělo ho i pole;
  s novým pravidlem by zpráva po cestě měnila tvar.

### Čas a stav doručení

Čas v bublině NENÍ a stav doručení je POD ní, ne v ní. V bublině zabíral řádek u každé jediné
zprávy, a přitom ani jedno z toho většinou nic neříkalo.

- **Datum nesou předěly dne a plovoucí datum při scrollu**, ne bublina; čas po pauze časové
  předěly (viz „Předěly dne"). Čas jednotlivé zprávy
  se odkrývá tahem doleva (viz „Odkrývání časů tahem") a je i v nabídce nad zprávou.
- **„Upraveno" zůstalo.** Že se zpráva změnila, se odjinud nedozvíme; čas ano.
- **Odznak je mimo bublinu, takže barva patří PLOŠE** (`onSurfaceVariant`), ne bublině. Dokud byl
  uvnitř, bral `contentColor`; pod bublinou by `onPrimary` na tónovaném pozadí nebyl vidět —
  tatáž past jako u `QuotedMessage.accent` a u zpráv bez bubliny.
- **`Box` kolem bubliny musí obepínat SAMOTNÝ `Surface`.** Nabídka nad zprávou si z něj bere
  `anchorBounds` a vykrajuje podle nich díru do závoje; kdyby v něm byl i odznak, byla by díra
  o ten kus vyšší. Odznak proto stojí ve sloupci ZA ním, a posun tahem se přesunul na ten sloupec,
  aby odznak jel s bublinou.
- **„Přečteno" hlásí automaticky nejvýš JEDNA zpráva v chatu** (`readBadgeRows`): nejnovější,
  a jen když je moje. Přečtením té poslední jsou přečtené i všechny před ní, takže dvojitá fajfka u celé
  historie byla řada stejných odznaků bez informace.
- **Odpověď protějšku přečtení dokazuje SAMA.** Když po mojí zprávě někdo napsal, musel ji
  přečíst a odznak nemá co dodat — proto se hledá nejnovější zpráva, ne nejnovější odchozí.
  Vyjde-li příchozí, nehlásí přečteno nikdo. (Nejdřív to bylo „nejnovější odchozí" a odznak
  zůstal svítit u zprávy, na kterou už protějšek odpověděl.)
- **Servisní oznámení se přeskakují.** „X se přidal do skupiny" nedokazuje, že si někdo něco
  přečetl, takže by odznak schovalo neprávem.
- **`Sending` a `Sent` zůstávají u každé zprávy** — ty ještě něco říkají.
- **`Failed` se ukazuje VŽDY.** Je to jediný stav, který uživatel musí zaregistrovat; schovat ho
  kvůli úspoře místa není úspora, ale skrytá chyba.
- **Ťuknutí na vlastní zprávu si odznak vyžádá**, druhé ho schová (`readReceiptFor`
  v `ConversationState`, `toggleReadReceipt`). Je to ubezpečení u jedné konkrétní zprávy —
  u starších se jinak nedá zjistit, jestli je protějšek přečetl.
- **Vyžádaný odznak automatický NERUŠÍ.** `readBadgeRows` vrací obojí: nejnovější zprávu podle
  pravidla výš a tu vyžádanou. Rozhoduje se to na jednom místě, ne druhou podmínkou v bublině —
  ty dvě by se časem rozešly.
- **Server se na nic neptá.** `lastReadOutboxMessageId` je posuvný ukazatel na chatu, takže stav
  libovolné odchozí zprávy je porovnání id; ťuknutí mění jen to, co se kreslí.
- **Jedno id, ne množina.** Ťuknutí na jinou zprávu odznak přepne tam. Odchod z chatu ho zahodí
  i se stavem — je to zobrazení na chvíli, ne nastavení.
- **Ťuknutí dostane jen bublina, ne její potomci.** Odkaz v textu, citace, fotka, náhled odkazu
  i samolepka mají vlastní klikání a k bublině ho nepustí, takže u zprávy složené z fotky není
  kam ťuknout. Přidat gesto i jim by znamenalo vzít ťuknutí těm akcím — u fotky otevření
  prohlížeče — a to je horší než chybějící zkratka.
- **V nabídce nad zprávou je SKUTEČNÝ stav, ne to, co ukazuje odznak.** Odznak „přečteno" je pod
  bublinou jen u vybraných zpráv; nabídka ho vypisuje vždycky (vedle času), protože ťuknutím se
  k němu čtečka obrazovky nedostane. Proto se počítá zvlášť `deliveryState` (pravda) a `delivery`
  (co kreslí odznak) — první verze to měla společné a v nabídce pak stav u starší zprávy chyběl.
- **Pravidlo je vytažené z composable do `readBadgeRows`** (`MessageRows.kt`) a pokryté testy
  (`MessageRowsTest`). Uvnitř `@Composable` by se netestovalo, a je to přesně ten druh podmínky,
  u které se hraniční případ (odpověď protějšku, servisní zpráva, album) pozná až na zařízení.
- **Hledá se jednou na změnu seznamu** (`readBadgeKey` v `remember`), ne v bublině. Průchod
  seznamem uvnitř `items` je to kvadratické chování, na které projekt narazil u citací.
- **Odznak se váže na NEJSTARŠÍ zprávu položky**, protože tím je klíč položky (u alba je to jeho
  nejstarší zpráva, viz `ConversationState.keyOf`).
- **Odznak mizí i se svým MÍSTEM** (`shrinkVertically`, ne jen `fadeOut`) a při odjezdu kreslí
  POSLEDNÍ známý stav — v okamžiku skrytí je hodnota už `null` a `AnimatedVisibility` by dobíhala
  s prázdným obsahem. Obě pasti jsou v projektu už zdokumentované (spodní bar, pruh připnutých).

### Vzhled konverzace

- **Žádná zpráva nezabere celý řádek: strop je 82 %** (`MESSAGE_MAX_WIDTH_FRACTION`, výchozí
  hodnota `MessageItem.maxWidthFraction`). Volný pruh na protější straně je pravidlo RÁMU, ne
  vlastnost jednoho bloku — nový druh zprávy ho dostane sám od sebe a celá šířka je vědomá výjimka.
  Dřív ho měla jen textová bublina a obrázky si šířku určovaly samy: **holá fotka, album i karta
  náhledu odkazu s velkým obrázkem šly přes celý řádek**, a protože se kreslí bez bubliny, neměly
  ani barvu, podle které by se odesílatel poznal — na obou stranách konverzace vypadaly stejně.
  Hlídá to test `zadnyBlokZpravyNezabereCelyRadek` (projde všechny druhy bloků), aby se šířka
  přes celý řádek nevrátila zadem u dalšího typu zprávy. Kopie zprávy v nabídce dostává tentýž
  podíl i totéž omezení šířky jako zpráva v seznamu.
- **Odesílatel ve skupině má stálou barvu** (`senderColor` v `core/ui`, testy `SenderColorTest`):
  jméno nad zprávou a jméno v citaci. Osm odstínů rovnoměrně po kruhu od odstínu `primary`,
  HCT tón 40 / 80 podle režimu — kontrast i soulad s Material You, žádná barva natvrdo.
- **Odpověď je karta ze dvou částí, jako náhled odkazu**: nahoře citace v tónované ploše
  (`MessageBodyScope.insetColor`, `tertiaryContainer`), pod ní odpověď v barvě bubliny
  (`QuotedBubble` v `BubbleMessageItem.kt`). Dřív byla citace subkartička uvnitř bubliny —
  zaoblená plocha v ploše, dvě vnořené karty. Jméno citovaného nese ve skupině jeho barvu,
  jinak `tertiary`.
- **Ikona citace (`format_quote`) stojí VŽDY před jménem** — na kartě zprávy i nad vstupním
  polem, na obou stranách konverzace. Stejně jako tužka u úpravy: říká, k čemu se obsah
  vztahuje. Na „opačné" straně by se u odchozí a příchozí zprávy stěhovala.
- **Karta odpovědi je vlastní `Layout`, ne `IntrinsicSize`**: pod citací může být fotka
  nebo album v `BoxWithConstraints`. Citace se změří v přirozené šířce, obsah dostane aspoň
  tu šířku jako minimum a plochy kreslí karta sama za obsahem (`drawBehind`).
- **Nároží bublin se animují** (spatial spec), jako skupiny v seznamu chatů. Pružina přestřelí,
  takže se ořezávají na nulu — záporné zaoblení `RoundedCornerShape` odmítne.
- **Nová zpráva pružně naskočí** z měřítka 0,85 od strany odesílatele (`arrival`). Jen jednou
  a jen když přišla po otevření konverzace (`claimArrival` s tolerancí pár sekund na hodiny
  serveru) — položka se při scrollu skládá znovu a pružit nemá co. Let ze vstupního pole se
  nepřebíjí. **Čip reakce při změně počtu povyskočí**, ne při prvním složení.
- **Odesílaná zpráva je průhlednější** (0,65) a po potvrzení se usadí. Klíč položky se potvrzením
  nemění, takže přechod dobíhá na téže bublině.
- **Zvýraznění po skoku je dvojí pulz měřítkem**, ne pruh přes celou šířku. Ověřovat se to dá
  jen měřením: běžný `screencap` pulz mine. Série `screencap /sdcard/x.raw` v jednom `adb shell`
  a porovnání okraje bubliny v řádku pixelů ho zachytí.
- **Zvýrazněná zpráva navíc změní barvu a její okraj se rozvlní, vlnky pomalu obíhají**
  (`MessageFrameData.emphasized`, `WavyShape` v `items/EmphasisShape.kt`). Stavy jsou dva
  a vypadají stejně, liší se délkou: jednorázově po skoku (`highlighted`, 2,5 s) a trvale jako
  ukázaný výsledek hledání (`searchFocused`, dokud se nepřejde jinam nebo hledání nezavře).
- **Barva je terciární role** (odchozí `tertiary`, příchozí `tertiaryContainer`). Bubliny jinak
  terciární roli nepoužívají, takže se nesplete s žádnou stranou, a rozdíl sytá / světlá mezi
  stranami zůstane. Prolíná se (`slowEffectsSpec`), akcent citace jde s ní.
- **Zvlňuje se sama zpráva, žádný obrys navíc.** Obrys kolem (první pokus) působil jako cizí
  rám a ležel přes fajfky doručení. Vlní se ořez celé zprávy v `MessageBox`, a to jen DOVNITŘ:
  vrcholy vlnek leží na původním okraji, takže pozadí bubliny, fotka i samolepka (každá ořezaná
  vlastním zaobleným tvarem) se do vlnek ořežou a nic nepřečnívá. Obsah má odsazení 8–14 dp,
  vlnky 3 dp do textu nesáhnou.
- **Vrstva ořezává tvarem bubliny, vlnky jsou jen ořez při kreslení** (`drawWithContent`
  + `clipPath` nad `WavyShape`). Fáze se mění každý snímek a `WavyShape` je pokaždé nový objekt;
  čte se až při kreslení, takže obíhání nerekomponuje (v `Modifier.clip` by rekomponovalo).
  Vlnek je po obvodu celý počet, jinak by na spoji byl zub.
- **Ořez VRSTVY rozhoduje i o dotyku — vlnitý obrys v něm nesmí být.** Dokud nesla vrstva
  `graphicsLayer { shape = WavyShape; clip = true }`, nešlo nad ukázaným výsledkem hledání otevřít
  nabídku dlouhým stiskem a nic uvnitř zprávy nereagovalo; po zavření hledání (zvýraznění zmizí)
  ano. Compose u ořezané vrstvy zkouší, jestli bod leží v obrysu, a u `Outline.Generic` to dělá
  průnikem obrysu se čtverečkem 0,01 px přes `Path.op` (`ShapeContainingUtil.isInPath`) — u vlnitého
  polygonu s tisíci body (zpráva vysoká přes 3 000 px) vyšel prázdný. Dotyk pak k rodiči kolem
  zprávy dorazil nespotřebovaný, ale k ničemu uvnitř ne. Zvlněná hrající hlasovka měla totéž.
  **Obecně: kde má obsah pod vlastním tvarem reagovat na dotyk, patří do ořezu vrstvy jen
  jednoduchý tvar (zaoblený obdélník), složitý obrys jen do kreslení.** Jestli selhával i krátký
  zvýrazněný text, ověřené není — na opravě to nic nemění.
- **Ověřilo se to sondou, ne úvahou**: log v `requestMenu` (při hledání se nezavolá vůbec), log
  událostí v `PointerEventPass.Final` na rámu (`Press`/`Release` nespotřebované) a pokus s
  `clip = false` u zvlnění (nabídka se otevře). Pozor na falešnou stopu: dokud se hledání načítá,
  zpráva ještě zvýrazněná není a nabídka se otevře — vypadalo to, že rozhoduje klávesnice.
- **Stažená nároží se při zvýraznění zakulatí** — zpráva se z bloku vyčlení i tvarem.

- **Odchozí bublina je `primary`, ne `primaryContainer`.** Se dvěma světlými plochami o podobné
  světlosti nebylo při rychlém průletu očima poznat, kdo co napsal — rozlišovala je jen strana.
  Sytá `primary` proti světlé `surfaceContainerHigh` dělá rozdíl barvou.
- **Co je uvnitř bubliny, nesmí mít natvrdo `primary`.** Citace měla proužek i jméno v `primary`
  a na nové odchozí bublině by byly NEVIDITELNÉ — stejná barva na stejné barvě. Proto
  `QuotedMessage` bere `accent` parametrem a bublina jí podá svoji `contentColor`. Ze stejného
  důvodu má vybraná reakce `primaryContainer` místo `primary`.
- **Startovní strana letu musí mít barvu cílové bubliny.** Jinak zpráva po cestě do konverzace
  změní barvu.
- **Vstupní pole JE `bottomBar`, ale spodní padding jde do `contentPadding` seznamu, ne na
  kontejner.** Obsah Scaffoldu je přes celou plochu a Scaffold padding jen spočítá. Dřív se dával
  modifikátorem na kontejner, seznam tím před polem natvrdo skončil a přes obrazovku vedl
  předěl, o který se zprávy při scrollu „zasekávaly" — proto pole na čas z `bottomBar` odešlo
  a měřilo se ručně. Jako `bottomBar` ho ale měří Scaffold (i s citací, úpravou, víc řádky
  a klávesnicí) a podle něj sám staví snackbar a tlačítko dolů (FAB slot). Ruční měření na
  třech místech tím odpadlo a snackbar přestal pole překrývat.
- **Tvar odesílacího tlačítka nese stav**: mikrofon je „sušenka" z `MaterialShapes`, odeslání
  a uložení úpravy kruh; změna se animuje morfem mezi nimi a ikona se vymění zvětšením. Stejně
  M3 Expressive používá tvar u přepínacích tlačítek. Tlačítko se mění samo od sebe (začne se psát,
  pole se vyprázdní) a skoková výměna ikony se dala přehlédnout; tvar se pozná i koutkem oka.
  Stisk mění jen kruh (na zaoblený čtverec) — mezi obecnými tvary `IconButton` neanimuje. Tvar (`MorphShape`) je nový objekt pro každou hodnotu průběhu. (Že by
  se tvar čtoucí průběh sám nepřekreslil, obecně NEPLATÍ — ořez vrstvy, `background` i kreslení
  čtení stavu v `createOutline` hlídají, viz nároží bubliny v „Recompozice za běhu". U tlačítka
  to vyzkoušené není.)
- **Morf má vlastní, pomalejší pružinu** (`morphSpring`, tuhost 70 místo ~380 u
  `defaultSpatialSpec`). S výchozí doběhl na skutečném telefonu za zlomek vteřiny a nebyl vidět —
  na emulátoru v debugu ano, ten je pomalejší, takže **rychlost animací se posuzuje na zařízení**.
  Toutéž pružinou jede výměna ikony a barva má `slowEffectsSpec`, aby nic nedoběhlo dřív než tvar.
- **Klidová sušenka je vlastní, ne `Cookie9Sided`**: šest vlnek s vnitřním poloměrem 0,7 místo
  devíti s 0,8. S mělkými vlnkami se tvar od kruhu skoro nelišil.
- **Tvary M3 Expressive mají v aplikaci pevné významy** (`ShapeMorphs` v `core/ui/MorphShape.kt`):
  šestistranná sušenka = klid / připraveno (mikrofon, pozastavená hlasovka i video, ikona
  pod taženým chatem či zprávou za prahem tahu), kruh = probíhající akce
  (odeslat, hraje), SoftBurst = živý signál (tlačítko při nahrávání dýchá, tečka v pruhu už
  nebliká — dva blikající prvky vedle sebe byly šum; na okamžik i za emoji čerstvě přidané reakce), dvanáctistranná sušenka = podbarvení
  podržené samolepky a vybraný vzorek barvy motivu — i vlnka stisku je ořezaná do téže sušenky (klikání má vlastní vrstvu,
  samolepka sama ořezaná není, přišla by o rohy), sled tvarů končící zaoblenou hvězdou =
  uvítání prázdného stavu (placeholder detailu, jednou a pak stojí). Kde se stav nemění (avatary, položky seznamu, reakce), se tvary
  nepoužívají: kdyby se hýbalo všechno, přestalo by to cokoli znamenat.
- **Barva odesílacího tlačítka říká, ČEHO se akce týká**: text `primary` (jako odchozí bublina),
  hlas `tertiary`. Hlasovka je doplňková cesta vedle psaní a terciární role je v M3 přesně na to;
  tlačítko je tak stejně výrazné jako odeslání textu (obojí plná role, ne tonální), jen se pozná,
  že udělá něco jiného. Prolíná se spolu s morfem. Tři různé barvy pro tři ikony by z tlačítka
  udělaly semafor, proto se drží dvojice.
  - **Dřív byl mikrofon tonální `secondaryContainer`** („klidový stav prázdného pole"). Tím ale
    bylo tlačítko v prázdném poli to nejméně nápadné na obrazovce, přestože je to jediná akce,
    která se z prázdného pole dá udělat.
  - **Mikrofon a odesílání hlasovky mají barvu STEJNOU.** Mezi „můžu nahrát" a „nahrávám"
    rozhoduje tvar (sušenka ↔ dýchající SoftBurst), vlnka karty a pruh s časem; přebarvit k tomu
    ještě tlačítko by byl čtvrtý signál pro jednu změnu. `SendButtonKind.SendVoice` je proto
    vlastní hodnota, i když kreslí tutéž ikonu jako `Send` — s jediným „Send" by tlačítko při
    spuštění nahrávání probliklo z terciární do `primary` a zpátky.
  - **Karta pole se při nahrávání barví na `tertiaryContainer`**, ne `primaryContainer`: hlas je
    v aplikaci terciární celý, včetně úrovní v pruhu nahrávání (`RecordingBar`).
- **Karta vstupního pole je `surfaceContainerHigh` PŘITÓNOVANÁ do `primaryContainer`**
  (`tintedSurface`, 30 %). V čistě neutrálních surface rolích to byla jediná velká šedá plocha
  na obrazovce a od pozadí konverzace ji dělila jen světlost — v Material You z tapety obzvlášť.
  Plný `primaryContainer` použít nejde: karta by se dostala na světlost odchozí bubliny a pole by
  vypadalo jako rozepsaná zpráva. Barva ikon v kartě (`inputIconColor`) se měří proti TÉHLE ploše,
  ne proti holé roli.
- **Odesílací tlačítko je velikost medium (56 dp).** Je to přesně minimální výška jednořádkového
  `TextField`, takže se spodním zarovnáním řádku sedí na střed pole bez dopočítávaného odsazení
  a u víceřádkového textu zůstává u posledního řádku. Výchozí small (40 dp) sedělo o 8 dp níž
  než střed pole, large (96 dp) by bylo vyšší než pole samo.
- **`consumeWindowInsets` v obsahu snackbar ani FAB neposune.** Ty nejsou potomky obsahu —
  rozmisťuje je Scaffold nad FAB / `bottomBar` / spodní inset `contentWindowInsets`. Co má stát
  nad nimi, musí být ve slotu, jinak o sobě nevědí.
- **Scaffold konverzace nedává spodní inset do `padding`** (`contentWindowInsets` jen Top +
  Horizontal). Spodek si řeší pole samo přes `navigationBarsPadding` + `imePadding`, takže
  spodní padding je přesně výška `bottomBar`. `padding` ze Scaffoldu se v obsahu jen aplikuje
  a inset NEKONZUMUJE — se spodním insetem v `contentWindowInsets` se výška systémové lišty
  připočítávala dvakrát a pole viselo zbytečně vysoko, se zavřenou i otevřenou klávesnicí.
- **Ikony přílohy a výběru emoji stojí VEDLE `TextField`, ne v jeho `leadingIcon` / `trailingIcon`.**
  Sloty `TextField` centrují na výšku pole, takže s víceřádkovou zprávou ikony ujížděly doprostřed.
  V řádku se spodním zarovnáním drží u posledního řádku jako odesílací tlačítko; odsazení 4 dp je
  (56 − 48) / 2, tedy střed jednořádkového pole. Barvu mají akcentní — výchozí
  `onSurfaceVariant` byla na `surfaceContainerHigh` karty zašedlá a nečitelná.
- **Akcent pro drobnou kresbu se VYBÍRÁ MĚŘENÍM, ne pevnou rolí** (`strongestAccentOn`
  v `core/ui/AccentColor.kt`, testy `AccentColorTest`). `primary` natvrdo prošla prahem
  čitelnosti a přesto vypadala rozmazaně: v Material You z tapety vyšla na kartě pole kontrast
  5,2–5,3 : 1, zatímco placeholder vedle ní 5,2–7,6 a ostatní ikony v aplikaci přes 10. Ikona
  je obrys s tahem 2 dp a její hrana proti kartě leží skoro celá v BARVĚ, ne ve světlosti
  (modrý kanál 23 proti 241) — a chromatickou hranu vidí oko měkčeji než luminanční, takže se
  tenká čára čte jako neostrá. `accentOn` na tohle nestačí: ten řeší, jestli akcent ZMIZÍ
  (práh AA 4,5), tady jde o to, jak je vidět. Vybírá se proto z dvojice `primary` /
  `onPrimaryContainer` ta, která má na té konkrétní ploše vyšší kontrast — pevná volba by
  v paletě se zvýšeným kontrastem spadla do téže pasti obráceně (`onPrimaryContainer` je tam
  v tmavém režimu TMAVÝ, viz `accentOn`). Naměřeno na emulátoru: 5,2 → 7,9 : 1.
- **„Rozmazané" u ikony NEMUSÍ být render.** Změřeno ze snímku: hrana má jeden pixel
  antialiasingu a podíl přechodových pixelů je stejný jako u šipky zpět, kterou nikdo za
  rozmazanou nepovažuje. Než se sáhne na drawable (velikost, `<group>`, viewport), vyplatí se
  změřit kontrast — dojem neostrosti dělá skoro vždycky barva.
- **Pole je `BasicTextField` s `TextFieldDefaults.DecorationBox`, ne `TextField`.** `TextField`
  má vnitřní odsazení 16 dp napevno a vedle sponky (glyf má v 48dp tlačítku svých 12 dp) začínal
  text skoro 30 dp od ikony. Dekorace bere `contentPadding`: 4 dp u ikony, 16 dp u holé hrany
  karty (úprava zprávy, kde sponka není).
- **Vlnka znamená „teď zní hlas".** Při nahrávání se karta pole rozvlní (`WavyShape`, stojí,
  neobíhá — živost nese dýchající tlačítko) a přebarví na `primaryContainer`; hlasovka
  v konverzaci se rozvlní a vlnky obíhají, jen když HRAJE (`rememberVoicePlaying`). **Stálá vlna
  na hlasovce (zkoušeno) vypadala jako rozbitý okraj** — nic neoznamovala, porušovala pravidlo
  „tvar jen tam, kde se mění stav". Hrající hlasovka si navíc zakulatí stažená nároží jako
  zvýraznění: vlnky vedené přes 4dp nároží v něm dělaly špičku. Hraje-li, čte se přes
  `derivedStateOf` — pozice se mění dvacetkrát za vteřinu a položka se má překreslit jen při
  spuštění a zastavení.
- **Nabídka příloh má vlastní umístění** (`AttachButton`, `AboveAnchorStartPositionProvider`).
  Výchozí umístění M3 menu drží od okraje okna povinný odstup 48 dp (`MenuVerticalMargin`,
  zahrnuto i do volby „spodkem ke kotvě"), a tlačítko u spodku obrazovky v tom pásu leží — menu
  se proto posadilo k oknu, zhruba 45 dp nad tlačítko. `offset` nepomůže, přičítá se ke
  kandidátům PŘED kontrolou. Expressive `DropdownMenuPopup` bere vlastní
  `DropdownMenuPopupPositionProvider` (s `transformOrigin` v rohu u tlačítka), takže vzhled je
  tentýž jako u ostatních nabídek. **Totéž čeká každou nabídku u spodku obrazovky.** Poznalo se
  to změřením (`uiautomator dump`): spodek menu byl přesně `výška okna − 48 dp`.
- **Nabídka sponky si nebere fokus** (`PopupProperties(focusable = false)`), jinak klávesnice
  zmizela bleskem a konverzace skočila dolů. Popup je na Androidu samostatné okno; fokusovatelné
  převzalo fokus od pole, systém klávesnici schoval — a animaci insetů dostává okno, které
  klávesnici řídí, tedy už okno nabídky. Hlavní okno dostalo rovnou konečné insety. Poznalo se
  to z `dumpsys window | grep mCurrentFocus` (`Pop-Up Window`) a snímků těsně po sobě. Klávesnice
  teď zůstane a nabídka stojí nad sponkou.
  - **Nefokusovatelné okno nedostane zpět**, proto `NavigationBackHandler` (první zpět zavře
    klávesnici, druhé nabídku). **A zavírá se už DOTYKEM mimo sebe — i na sponce**, takže klik
    po puštění ji otevřel znovu; klik do půl vteřiny po zavření se proto bere jako zavření.
  - **Sheet po volbě položky je zase další okno** a otevřený hned utrhl klávesnici stejně —
    konverzace nad sheetem skočila. `rememberAfterKeyboardHidden` (`core/ui`) klávesnici schová
    a akci pustí, až insety `ime` dojdou k nule (nejvýš 600 ms). Totéž tlačítko emoji. Na
    emulátoru dojede klávesnice za ~320 ms; zbytek prodlevy u emoji je skládání sheetu.
  - **Na iOS ověřené není** (jen přeložené).
- **Citace a psaní jsou JEDNA karta.** Dva samostatné kusy nad sebou dělaly z odpovědi cizí prvek
  vsunutý mezi konverzaci a vstup; ve společné kartě je vidět, že citace patří k tomu, co se
  právě píše. Citace (i hlavička úpravy) je **horní pruh karty v `tertiaryContainer`**
  (`ComposerHeader`), stejně jako citace na kartě zprávy — ne subkartička v kartě. Pole uvnitř je proto průhledné — kontejner nese karta, ne ono, a dvě zaoblené
  plochy v sobě by vypadaly jako chyba.
- **Pruh pod kartou, pruh připnutých a plovoucí datum jsou MLÉČNÉ SKLO** (`Glass.kt`, tentýž Haze
  jako závoj nabídky nad zprávou). Všechny tři plochy leží nad projíždějícím obsahem a dřív ho jen
  zakrývaly barvou; rozostření dělá totéž čitelněji — je vidět, že pod nimi obsah pokračuje.
  - **Lišty MÍSTO pole mají tentýž pás** (`barGlass` v `bottomBar`: Přidat se, Start, Ztlumit,
    věta „nejde psát"). Seznam pod spodní lištu zajíždí, takže bez pásu prosvítaly bubliny
    i sponzorovaná karta zpod tlačítka — dlouho bez povšimnutí, protože pole samo pás má.
    Každá nová náhrada pole dostane `barGlass` taky.
  - **Zdrojem je JEN seznam zpráv, ne celý Scaffold** (vlastní `HazeState`). Závoj nabídky
    rozostřuje Scaffold celý, protože je nad ním; tyhle plochy jsou ale jeho součástí, takže by
    se rozostřovaly samy sebou — pruh připnutých by nasál plovoucí datum a pás pod polem sám sebe.
  - **Barvu nese tint stylu, ne plocha.** Kdyby si ji plocha kreslila sama, překryla by rozostřený
    obraz pod sebou; průhledná plocha s tintem dělá sklo. Tvar se proto ořezává zvlášť
    (`clip(shape)` uvnitř `Surface`), jinak by sklo přeteklo přes zaoblení.
  - **Krytí je dvojí.** Pás pod polem vlastní text nenese (karta pole má svoji plochu), takže kryje
    0,72 a sklo je na něm znát. **Pruh připnutých a plovoucí datum text nesou a kryjí 0,88**: pod
    nimi projede i tmavá fotka a písmo by se ve ztmavlém skle ztratilo — změřeno, při 0,72 mělo
    „Včera" nad černým náhledem videa kontrast 2,85, tedy hluboko pod AA. Při 0,88 vyšel v tmavém
    režimu nad světlou fotkou 5,89.
  - **Poloměr 20 dp, víc než u závoje nabídky (12).** Tam jde o odsunutí celé konverzace do pozadí,
    takže silné rozostření z ní udělá kaši; tady prosvítá pruh vysoký pár desítek dp a při malém
    poloměru je pod ním text ještě čitelný — plocha pak vypadá jen zašpiněně.
  - **Původní krycí pás měl 0,93 a rozostření pod ním nebylo vidět vůbec**: text zprávy končil na
    jeho hraně, jako by ho něco ořízlo. Kdo sklo přidává, musí krytí snížit, jinak přidá jen cenu.
  - **Fallback je ověřený na Androidu 9** (API 28, emulátor): pás pod polem kryje stejně jako
    dřívější krycí plocha (prosvítá zhruba 7 %, tedy beze změny proti stavu před sklem), plovoucí
    datum je úplně krycí a text na něm má kontrast 11,3. Závoj nabídky nad zprávou tam taky jen
    ztmavuje, nerozostřuje.
  - **Na emulátoru se plynulost změřit NEDÁ.** `dumpsys gfxinfo` hlásil 98 % zadrhnutých snímků
    v konverzaci se sklem i v seznamu chatů BEZ něj — měří se tím hostitel pod tlakem paměti, ne
    naše změna. Patří to na zařízení, a hlavně na slabší s Androidem 12.

- **Offsety jsou v UTF-16 jednotkách**, tedy přesně v tom, čím indexuje Kotlin `String`. Žádný
  přepočet. Nesmí se ale míchat s počítáním shluků emoji (`emojiClusterCount`), které jde
  po grafémech.
- **Text a entity vrací jedna funkce** (`captionBody()`), ne dvě. Rozsahy platí jen pro ten
  řetězec, ze kterého vznikly; dvě nezávislá `when` by se rozešla první přidanou větví a entity
  by ukazovaly do jiného textu, než který se vykreslí. Stejný důvod jako u `serviceText()`.
- **Popisům, které si vyrábíme sami, se entity nepřipojují.** U typů bez vlastního textu vrací
  `captionBody()` jen náš popis („Anketa: …") a prázdné entity — rozsahy z původní zprávy by
  v něm ukazovaly jinam, v lepším případě obarvily nesmysl, v horším spadly na indexu.
- **Rozsahy se před použitím ořezávají do délky textu.** Server posílá platné hodnoty, ale
  `addStyle` mimo rozsah je pád aplikace kvůli jedné zprávě.
- **Vnoření se řeší samo.** Text se do `AnnotatedString` připojí jednou a každá entita jen přidá
  svůj styl na svůj úsek — tučné uvnitř odkazu jsou prostě dva styly přes sebe, žádný strom.
- **Barva odkazu se musí dát předat.** V odchozí bublině je pozadí samo `primary`, takže natvrdo
  zadaná `primary` by odkaz zneviditelnila — tatáž past jako u `QuotedMessage.accent`. Bublina
  podává svoji `contentColor`, podtržení nese odlišení i tam, kde barva splývá.
- **Kliknutí jde přes ViewModel, ne rovnou z bubliny do `UrlOpener`.** Odkazy na Telegram
  se tam rozlišují na jednom místě (viz „Odkazy na Telegram"). Vlastní
  `linkInteractionListener` je nutný — bez něj si `LinkAnnotation.Url` otevře odkaz sám přes
  `LocalUriHandler` a nemáme kam sáhnout.
- **Dlouhý stisk nad odkazem hlídá `RichText` sám** (`onLongClick`). Compose dává každému odkazu
  vlastní `combinedClickable` bez `onLongClick`: ten dlouhý stisk k bublině nepustí a po puštění
  ho vyhodnotí jako ťuknutí, takže se místo nabídky otevíral prohlížeč. `RichText` proto
  v `PointerEventPass.Initial` (bez konzumace) pozoruje stisk nad odkazem, po `longPressTimeout`
  zavolá nabídku a následné „kliknutí" na odkaz zahodí. Mimo odkazy se nehlídá nic — dlouhý
  stisk tam dostane bublina a nabídka by se otevřela dvakrát.
- **Odkaz bez schématu dostane `https://`.** TDLib označí za odkaz i holé `example.com`; Android
  by z toho udělal relativní URI a intent by nikam nevedl.
- **Cashtagy se schválně nemapují.** Obarvit je umíme, ale kliknutí by nemělo kam vést — modrý
  text, po kterém se nic nestane, je horší než text černý. Ze stejného důvodu je příkaz bota
  klikatelný jen tam, kam se smí psát (viz „Příkazy bota").
- **Zmínka a hashtag vedou dovnitř aplikace, ne na adresu** (`MessageEntityType.Mention`,
  `MentionUser`, `Hashtag`; v `RichText` jako `LinkAnnotation.Clickable` s vlastní obsluhou
  `onEntityClick`, mimo `onLinkClick`). `@jméno` najde chat přes `searchPublicChat`, zmínka
  uživatele bez jména (`TextEntityTypeMentionName`) nese jen `userId` a vede na
  `createPrivateChat`. Chat se otevře NAD konverzací (`onOpenChat`), takže zpět vede do ní;
  zmínka téhož chatu (kanál ve vlastním příspěvku) nikam nevede. Veřejný chat, kde uživatel
  není, dostane „Přidat se" jako z globálního hledání.
- **Hashtag otevře hledání v seznamu chatů s `#tag`** (`AppNavigation.openSearch`,
  `searchRequest`). Požadavek je STAV, ne událost — seznam chatů na telefonu pod konverzací
  složený není a vyzvedne si ho, až se složí. Na telefonu se zásobník Chatů srovná na kořen,
  v dvoupanelu konverzace zůstane. Efekt, který hledání rozbalí, požadavek nuluje AŽ NAKONEC —
  vynulování mění jeho klíč a na začátku by zrušilo rozbalování (tatáž past jako „Vrátit").
- **Hashtagový dotaz hledá jen zprávy** (`isHashtagQuery`). Hledání chatů i veřejných chatů
  mřížku zahodí, takže „#plume" vracelo kanály „Plumex" a „plume" — šum nad tím, co uživatel
  hledal.
- **U hashtagu jde přepnout na veřejné příspěvky kanálů** (`HashtagSourceToggle` nad výsledky,
  `MessageSearchFilter.publicPosts`, `searchPublicMessagesByTag`; pravidlo `searchesPublicPosts`,
  testy `ChatSearchTest`). **Přepínač, ne druhá sekce pod zprávami**: obě jsou stránkované
  a sekce pod nekonečnými zprávami by se k vlastní další stránce nedostala. Volba jede ve
  filtru, protože filtr už teče celou cestou (`onFilterChange`), nuluje se se sbalením hledání
  a stránka se k němu přiřazuje. **Druh chatu a období se s veřejnými příspěvky schovají
  a vynulují** — jsou to vždycky kanály a období API nebere; zůstat skryté ve filtru by nesměly,
  prázdný výsledek by radil „změň filtr", který není vidět. **Obsah je, co vrátí server**:
  u `#telegram` i `#kotlin` na emulátoru převážně spamové kanály — tak to vidí i oficiální
  klienti. Ověřeno: přepnutí oběma směry, dotahování stránek; otevření kanálu z výsledku
  nezkoušeno (jde toutéž cestou jako nalezená zpráva).
- **Zmínka a hashtag nejsou podtržené — kromě odchozí bubliny.** Podtržení patří adresám, které
  z aplikace odvedou. V odchozí bublině je ale barva odkazu barva textu (na `primary` jiná
  nejde), takže tam se podtrhávají, jinak by nebyly k poznání (`linkColor == overlayColor`).
- **Spoiler musí být opravdu nečitelný**: průhledný text na plné ploše, ne jen jiná barva.
  Odkrývá se ťuknutím přes `LinkAnnotation.Clickable` — `Modifier.clickable` by platilo pro celou
  zprávu, ne pro skrytý úsek. Odkrývá se celá zpráva naráz; dohledávat spoilery po kouskách
  je otrava.
- **Citace v textu je BLOK ZA TEXTEM, ne styl písma** (`MessageEntityType.Quote`,
  `drawQuoteBlocks` v `RichText.kt`, testy `QuoteRangesTest`). `SpanStyle.background` umí jen
  hranatý obdélník po řádcích — tatáž past jako u zvýraznění hledaného slova —, takže se plocha
  kreslí v `drawBehind` z `TextLayoutResult`, stejně jako pilulky zvýraznění. Blok jde napřed,
  pilulka na něj: hledané slovo musí být vidět i uvnitř citace.
- **Plocha, ne proužek vlevo.** Proužek je jazyk M2 a v aplikaci už jednou ustoupil ploše
  (náhled odkazu, citace nad odpovědí).
- **Pás od kraje ke kraji, ne zaoblená plocha uvnitř.** Zaoblený obdélník odsazený od okraje
  bubliny byl karta v kartě — přesně to, co už jednou musela opustit citace nad odpovědí
  („subkartička v bublině") i náhled odkazu („bez spár, části uvnitř jsou rovné"). Náprava je
  pokaždé stejná: **tvar nese vnější karta a díly uvnitř jsou rovné a přes celou šířku.** Pás
  proto přetéká za text přesně o vnitřní odsazení bubliny, nemá vlastní zaoblení a na nároží ho
  ořízne tvar bubliny.
- **Tónuje se INKOUSTEM, ne container rolí.** `tertiaryContainer` (`insetColor`) uvnitř bubliny
  byla druhá karta a na odchozí `primary` bublině navíc cizí barva. Podklad se proto odvozuje
  z barvy popředí (`onBubbleColor` s alfou), stejně jako podklad kódu — ten má tutéž poznámku
  o „cizím tělese" v `RichText` od začátku.
- **Na okraj bubliny pás dosedne, jen když nad textem (pod ním) opravdu nic nestojí**
  (`quoteBleedTop` / `quoteBleedBottom`, podává je bublina). `drawBehind` se na hranice textu
  neořezává, takže uvnitř `RichText` se nedá poznat, jestli výš není značka přeposlání nebo
  hlavička citované zprávy, přes jejichž šev pás přelézt nesmí. Bez toho zůstal nad citací přes
  celou zprávu proužek barvy bubliny a vypadalo to jako lem.
- **Odsazení, přesah i výška pásu berou TÁŽ čísla jako ostatní díly zprávy** (`MESSAGE_PADDING_H`
  14 dp, `MESSAGE_PADDING_V` 8 dp v `MessageDimens.kt`; `RichText` je dostává parametrem, aby
  `core/ui` neznalo rozměry bubliny). Bylo to opsané v pěti souborech — bublina, citace nad
  odpovědí, popis webu — a u pásu musí sedět PŘESNĚ: do stran přetéká o vnitřní odsazení karty,
  takže dosedne na její okraj, a svisle odsazuje text stejně jako díl nad ním.
- **Citace přes CELÝ text zprávy se odsazuje `padding`em, ne odstavcem** (`quoteIndentAsPadding`).
  Compose ellipsizovaný poslední řádek zalomí na plnou šířku a teprve pak ho posune o odsazení
  odstavce: konec i s výpustkou přeteče za okraj, `Text` ho ořízne a sbalená citace pak končí
  uprostřed slova a bez „…". S `padding`em se zalomí správně a výpustka je vidět. Uvozovky se
  kreslí do žlábku, který tím vznikne, tak jako tak — `drawBehind` měří vnější box i s ním.
- **Písmo citace je o kousek menší** (`QUOTE_FONT_SCALE`, 0,94 `em` — v `em`, aby platilo pro
  každý styl). **Kurzíva ne:** projekt nemá kurzivní řez (v `composeResources/font/` jsou jen
  přímé), takže by ji platforma dopočítala sklonem a u víceřádkové citace je to vidět.
- **Odsazení textu uvnitř zprávy dělá `ParagraphStyle(textIndent)`, a stojí to prázdný řádek.** Odstavcový
  styl nad rozsahem rozdělí text na odstavce na hranicích citace — a přilehlé `\n` tím přestanou
  být zalomením a stanou se prázdným řádkem (odstavec končící `\n` má řádek navíc, stejně jako
  odstavec začínající `\n`). U citace uvnitř delší zprávy je proto nad pásem i pod ním jedna
  volná řádka. **Jinak to nejde**: odstranit ta `\n` z textu by rozhodilo offsety všech entit,
  zvýraznění i spoilerů, a rozpad na víc `Text`ů zase jejich výpočet. Vypadá to jako vzduch kolem
  citace, takže se to nechává; kdyby vadilo, cena za tichý tvar je zrušit odsazení.
- **Odsazení se počítá z dp (`Dp.toSp()`), ne ze sp.** Lícuje s uvozovkami vedle sebe, ne
  s velikostí písma; `toSp()` počítá s `fontScale`, takže výsledek sedí na dp v každém nastavení.
- **Uvozovky se kreslí v `drawBehind` do žlábku, ne jako `InlineTextContent`.** Inline obsah se
  musí vejít do řádku a posouval by první slovo; ve žlábku, který si citace stejně dělá
  odsazením, nic neposouvá.
- **Sbalitelná citace** (`Quote.expandable`) se ořízne na tři řádky a pod pásem dostane
  rozbalovač na běžné ploše bubliny — citace je vrchní část zprávy, rozbalovač je ovládání k ní.
  **Sbalí se jen citace, která je CELÝ text zprávy** (`collapsesQuote`, testy `MessageRowsTest`):
  `maxLines` platí pro celý `Text`, takže by ořízl i větu pod citací, a rozdělit zprávu na víc
  `Text`ů nejde (zvýraznění, spoilery a dlouhý stisk nad odkazem se počítají nad jedním layoutem).
- **Rozbaluje a sbaluje i ťuknutí na samotnou citaci**, ne jen na rozbalovač — tak to dělá
  i oficiální klient a tři řádky jsou mnohem větší cíl než jeden řádek popisku. Bez vlnky:
  odezva je samo rozbalení, a vlnka by se kreslila v obdélníku kolem textu, který se s pásem
  (ten jde od kraje bubliny) nekryje. Dlouhý stisk se opakuje, jinak by nad citací nešla otevřít
  nabídka nad zprávou.
- **Popisek rozbalovače má zvýrazněný řez** (`labelLargeEmphasized`). V ODCHOZÍ bublině je akcent
  barva bubliny, takže `accentOn` ustoupí barvě textu a rozbalovač by se od zprávy lišil jen
  šipkou — tatáž úvaha jako u podtržení odkazů tam, kde barva splývá.
- **Rozbalovač má nad sebou DVOJNÁSOBNÉ odsazení** (2 × `MESSAGE_PADDING_V`). Pás si svoje
  spodní odsazení kreslí POD posledním řádkem citace, takže první polovina padne na něj a teprve
  druhá je mezera, kterou je vidět; s jedním odsazením seděl rozbalovač na okraji pásu (změřeno:
  5,7 dp nad popiskem, 15,2 dp pod ním). Dotykové odsazení se od toho neodečítá — leží i pod
  popiskem, a k němu ještě spodní odsazení bubliny.
- **Že se text nevešel, se pozná až z layoutu** (`hasVisualOverflow` přes `onOverflow`) — a po
  rozbalení se vejde vždycky. Kdyby se rozbalovač řídil jen aktuálním přetečením, po rozbalení by
  zmizel a nešlo by sbalit zpátky; ROZBALENÁ citace proto drží, co bylo.
- **Sbalená citace ale bere POSLEDNÍ měření, ne kterékoli.** Čerstvě odeslaná zpráva se poprvé
  měří v jiné šířce (přilétá z pole) a to měření přetečení nahlásí i u jednoho řádku. Dokud se
  příznak jen zapínal („stalo se to"), zůstalo u krátké sbalitelné citace „Zobrazit víc" viset,
  dokud se chat neotevřel znovu. **Obecně: příznak odvozený z layoutu, který se jen zapíná,
  zakonzervuje i měření, které platilo jeden snímek.** Ověřeno dočasným logem v `onTextLayout`:
  „Kratka citace" se při odeslání změřila v omezení 195 px místo 232 a ohlásila přetečení výšky,
  všechna další měření ne. Na emulátoru pak s opravou krátká citace po odeslání rozbalovač nemá,
  dlouhá (od bota) ho má a rozbalí se i sbalí.
- **Při odesílání se entity dohledávají** (`getTextEntities`), jinak odkaz odejde jako mrtvý text —
  server si ho sám neoznačí. Když dohledání selže, zpráva stejně odejde.

### Odkazy na Telegram

Odkaz ze zprávy (text, náhled odkazu) jde přes `ConversationViewModel.openLink` →
`ChatRepository.resolveLink`. Co odkaz znamená, rozhoduje TDLib (`getInternalLinkType`) — zná
všechny podoby (`t.me`, `telegram.me`, `tg://`) a my jen vybíráme, co umíme ukázat
(`InternalLink`). Ostatní jde do prohlížeče.

- **Rozpoznání je lokální volání**, takže běžný webový odkaz jde do prohlížeče bez prodlevy.
  Neinterní odkaz TDLib hlásí CHYBOU — to je `null` (prohlížeč), ne selhání.
- **Chat (`t.me/jméno`) se otevře nad konverzací** jako zmínka; zpět vede do konverzace.
- **Odkaz na zprávu** (`getMessageLinkInfo`) v tomhle chatu skočí na zprávu na místě
  (`jumpToMessage`, s dojetím / přejezdem), v jiném chatu otevře `ConversationRoute(chatId,
  messageId)`. Zprávu, kterou server nevydal (`message == null`), nahradí otevření chatu.
- **Pozvánka nikoho nepřidá sama** — ukáže náhled (`InviteSheet`: fotka, název, počet členů /
  odběratelů, popis) a přidává se až tlačítkem. `checkChatInviteLink` s nenulovým `chatId`
  znamená, že chat je dostupný už teď — otevře se rovnou.
- **`joinChatByInviteLink` vrací `ChatJoinResult`, ne chat** (novější TDLib): úspěch, odeslaná
  žádost (pozvánka se schválením → snackbar „Žádost odeslána"), odmítnutí, schválení botem.
  `when` je bez `else`, takže nový druh v novější TDLib rozbije překlad, ne tiše přidávání.
- **Neplatná pozvánka chodí jako `INVITE_HASH_EXPIRED`** (i u vymyšleného odkazu) — má vlastní
  větu v `userMessage`.
- **Sada samolepek** (`t.me/addstickers/…`, `searchStickerSet`) otevře tentýž sheet jako ťuknutí
  na samolepku.
- **Proxy** (`t.me/proxy`, `t.me/socks`) se jen nabídne dialogem — viz „Proxy".
- **Člověk podle čísla (`t.me/+420…`) a kontaktní odkaz (`t.me/contact/…`)** jdou přes
  `searchUserByPhoneNumber` / `searchUserByToken` a `createPrivateChat` (lokálně — bez něj by
  TDLib chat neznal). Neexistující číslo vrátí `404 Not Found` a má vlastní větu
  (`LinkUserNotFoundException`), ne obecné „odkaz nejde otevřít": uživatel z ní pozná, že
  odkaz je v pořádku a jen ten člověk na Telegramu není (nebo číslo skrývá).
- **`?text=…` u chatu se jen VLOŽÍ do pole, nic se neodesílá** — text napsal autor odkazu. Jde
  přes `ShareRequests` s vybraným chatem (`ShareTarget(confirm = false)`), tedy cestou
  sdíleného textu: připojí se za rozepsaný koncept, a to až po jeho obnovení. Vlastní „request"
  holder by zdvojil přesně tu logiku. Shell sdílení s vybraným chatem nikam nenaviguje, takže
  chat otevírá dál konverzace (`openTarget`); odkaz na TENTÝŽ chat vloží text rovnou.
- **`?profile` otevře profil nad konverzací** (`ConversationState.openProfile`), text z odkazu
  se pak zahodí — patří chatu, ne profilu.
- **`t.me/share/url?url=…&text=…` je sdílení bez vybraného chatu** (`shareRequests.start`): shell
  otevře výběr chatu sám, jako u sdílení ze systému. Adresu i text TDLib složí do jednoho textu.
- **Odkazy na obrazovky aplikace** (`InternalLink.Screen`, `AppScreen`, `AppNavigation.openScreen`):
  `tg://settings[/sekce]` do Nastavení po krocích (zařízení, soukromí → Obecné, složky, úprava
  profilu, notifikace, vzhled; sekce, které Plume nemá — jazyk, úložiště, Premium — na kořen),
  `tg://new` a `tg://contacts` do Nové zprávy, `tg://new/group` a `tg://new/channel` do
  založení, `tg://chats/search` do hledání. Nastavení se otevře jako z menu účtu, nová konverzace
  nad otevřeným chatem — zpět vede do něj. Odkaz na videochat otevře chat (pruh vysvětlí, že
  připojit se jde jen v oficiální aplikaci). **Podoby odkazů určuje TDLib** a hádat se nevyplatí:
  `tg://newgroup` ani `tg://settings/edit_profile` nepozná (správně `tg://new/group`,
  `tg://settings/edit`) — seznam je v `td/telegram/LinkManager.cpp` (`get_internal_link_impl`).
- **Přihlašovací odkazy se z ťuknutí neotevírají** (`InternalLink.LoginWarning`,
  `LoginLinkWarningDialog`): kód (`t.me/login/…`, `tg://login?code=…`) i přihlášení jiného
  zařízení (`tg://login?token=…`). Dřív šly do prohlížeče, a ten je mohl předat oficiálnímu
  klientovi — přesně to, co podvodník chce. Dialog řekne proč a u QR kódu, že se jen skenuje
  v Nastavení → Zařízení (TDLib takové upozornění předepisuje). Ani mini aplikace je neotevře.
- **Ověřování přes `adb`: poslední zpráva v chatu s botem leží pod panelem jeho klávesnice.**
  Ťuknutí na ni trefilo panel a výsledek vypadal jako chyba odkazu; panel se napřed schová
  („Schovat klávesnici bota").
- **Ověřeno na emulátoru** odkazy od testovacího bota: `?text=` v témže chatu, `?profile`,
  `t.me/share` do Uložených zpráv, neexistující číslo (hláška) a vlastní číslo (Uložené zprávy
  s textem). **Kontaktní odkaz ověřený NENÍ** (chce token jiného účtu), Uložené zprávy z odkazu
  taky ne (bot `tg://` odkaz v textu neoznačí).

### Čas v médiu

„1:23" v textu (`TextEntityTypeMediaTimestamp` → `MessageEntityType.MediaTimestamp`) pustí
médium od toho místa: video v prohlížeči, hlasovku, skladbu a kolečko přímo v chatu
(`MessageAction.PlayMediaAt`, `ConversationViewModel.playMediaAt`).

- **Kam čas míří, říká TDLib**: médium téže zprávy (popisek videa), nebo zprávy, na kterou
  odpovídá (`Message.hasTimestampedMedia` = `false`). Pravidlo je čistá funkce
  `timestampTarget` (testy `MediaTimestampTest`); citovaná zpráva mimo načtenou historii se
  bere z dotažených citací.
- **Entitu vyrábí TDLib i u příchozích zpráv sám** — bot poslal holý text „Tady 0:02" jako
  odpověď na video a entita v něm byla. Server ji neposílá, takže se Bot API ověřit dá.
- **Video se otevře rovnou na celé obrazovce a hraje** (`MediaRoute.startSeconds`,
  `NO_START` = běžné otevření). V galerii by ho uživatel musel pustit sám a čas by nic neudělal.
  Přeskočí se hned po načtení (`seekTo` v `VideoPage`), u streamovaného videa taky.
- **Hlasovka a skladba jdou přes `VoicePlayer.play(startMs = …)`**; čas za koncem (překlep
  u krátké hlasovky) se ignoruje, jinak by přehrávač hned skončil. Kolečko se zvukem si start
  bere z `ConversationState.soundingVideoNoteFromMs`.
- **Klikatelný je čas jen v konverzaci** (`RichText(timestampsClickable)`), jinde (bio, popis)
  zůstane textem — vedl by nikam.
- **Video v náhledu odkazu se otevře na webu od toho místa** (`videoLinkAt`, testy
  `MediaTimestampTest`): YouTube dostane `t=43s` v dotazu (stávající `t` se přepíše), Vimeo kotvu
  `#t=43s`, jiný web holou adresu — přehrávač na něm neznáme a hádat parametr nestojí za to.
  V aplikaci se cizí video nepřehrává. Video pozná náhled podle druhu od serveru
  (`LinkPreview.isVideo`: `LinkPreviewTypeVideo` a `EmbeddedVideoPlayer`), u článku nebo fotky
  čas nevede nikam. Ověřeno botem: odpověď „Refrén je od 0:43" na zprávu s odkazem na YouTube
  otevřela aplikaci YouTube s `…&t=43s` (vidět v `capturedLink` v logcatu — `START` adresu
  zkrátí na `https://youtube.com/...`).
- **Ověřeno na emulátoru botem**: popisek videa, odpověď na video (prohlížeč na celé obrazovce
  od 0:02), odpověď na skladbu, na hlasovku (m4a přes `sendVoice`) a na kolečko — vše hraje
  od 0:02. **Neověřeno**: iOS (jen přeložené).

### Datum a čas v textu

Datum a čas, které pisatel označil (`TextEntityTypeDateTime` → `MessageEntityType.DateTime`),
se ukazují v místním pásmu a v jazyce aplikace: „středa 30. září 2026 14:23", „30. 9. 2026",
„před 2 hodinami" (`dateTimeText` v `core/ui/DateTimeText.kt`, testy `RelativeTimeTest`).

- **Text se v řetězci NENAHRAZUJE, kreslí se místo něj inline obsah** (`dateTimeRanges`
  v `RichText`), stejně jako vlastní emoji. Oficiální klienti text entity nahrazují, a to by
  posunulo offsety všech entit za ní, zvýraznění hledání i spoilerů. Inline obsah se do řádku
  vejde jen ve své velikosti, takže se formátovaný text změří (`rememberTextMeasurer`)
  a placeholder dostane jeho šířku. Datum se tím nezalomí uprostřed — přejde na další řádek celé.
- **Formátuje se v kompozici, ne při převodu zprávy**: jazyk se může změnit a relativní čas
  stárne. Počítá se vůči okamžiku složení, po vteřinách se nepřepočítává.
- **Relativní čas se zaokrouhluje, ne ořezává** (`relativeTime`): s ořezem vyšly 1 h 59 min
  jako „před 1 hodinou" — a hodiny telefonu o pár vteřin pozadu z „před 2 hodinami" udělaly
  hodinu. Co se zaokrouhlí na celou vyšší jednotku, přejde do ní („1 hodina", ne „60 minut").
  Česky jsou „za" a „před" vlastní plurály (`relative_in_*`, `relative_ago_*`): po „za" je
  4. pád, po „před" 7. — tvary délky (`duration_*`) jsou v 1. pádu a nehodí se.
- **Den v týdnu uprostřed věty má vlastní řetězce** (`weekday_in_text_*`). `day_long_*` jsou
  nadpisy předělů s velkým písmenem; česky se ve větě píše malým, anglicky velkým, takže
  `lowercase()` nestačí.
- **Bez formátu** (`formattingType == null`) zůstává text pisatele — tak to chce TDLib.
- **Kopírování, citace a náhled v seznamu chatů berou text pisatele**, ne formátované datum.
- **Ověřuje se botem**: Bot API umí entitu `date_time` s `unix_time` a `date_time_format`
  (`r` relativní, `w` den v týdnu, `d`/`D` krátké/dlouhé datum, `t`/`T` krátký/dlouhý čas).
  Offsety jsou v UTF-16 — s ručně spočítaným offsetem nahradí entita i sousední znak. Ověřeno
  na emulátoru anglicky i česky. **iOS jen přeložené.**

### Ankety a kvízy

Anketa se kreslí jako vlastní blok (`PollMessageItem`) nad doménovým `ChatPoll` (`ChatMessage.poll`,
převod `MessagePoll.toChatPoll` v `MessageContent.kt`, pravidla a testy `ChatPollTest`): hlasování
jednou odpovědí ťuknutím, víc odpověďmi přes „Hlasovat", výsledky s pruhy a procenty, u kvízu
správná a vybraná špatná odpověď a vysvětlení. V nabídce „Zrušit hlas" a „Ukončit anketu",
v zápatí „Kdo hlasoval" (`PollVotersSheet`, `getPollVoters` po stránkách, testy `PollVotersTest`).

- **Popis „Anketa: otázka" nese `ChatMessage.description`** (jako `UiText`, viz „Jazyky")
  a blok ho jen nekreslí. Citace, připnutý pruh, náhled v seznamu chatů i shrnutí ho berou,
  když zpráva vlastní text nemá — anketa se tak nemusí doplňovat do každého z nich zvlášť.
  „Kopírovat" u ankety není (vlastní text nemá) a `composerEdit` ji vylučuje výslovně.
- **U ankety `canBeEdited` NEZNAMENÁ „jde upravit"**, ale „jde ukončit" (dokumentace
  `stopPoll`). Bez výjimky v `composerEdit` by se u vlastní ankety nabídlo „Upravit" a pole
  by upravovalo text „Anketa: …" voláním, které server odmítne.
- **Hlasuje se indexem v PŮVODNÍM pořadí**, ne v tom, jak se odpovědi ukazují. TDLib 15 posílá
  `optionOrder` (zamíchané pořadí); `PollAnswer.index` nese původní pozici a `setPollAnswer`
  i `getPollVoters` berou právě ji. Neplatné indexy v pořadí se zahodí a chybějící odpověď se
  připojí na konec — ztracená odpověď by byla horší než jiné pořadí.
- **Hlas se lokálně nepřepisuje.** Po `setPollAnswer` TDLib sám změní obsah zprávy
  (`UpdateMessageContent` → `editedMessages`): napřed s `isBeingChosen` (u odpovědi se točí
  indikátor), pak se skutečnými počty. **`UpdatePoll` je jen pro boty** — kdo na něj čeká,
  nedočká se.
- **Po hlasování jdou výsledky jen s `canSeeResults`.** Anketa si je může schovat do uzavření;
  pak zůstane vidět vlastní volba a věta „Výsledky uvidíš, až anketa skončí".
- **„Zrušit hlas" jen s `allowsRevoting`, u kvízu nikdy.** Rozhoduje o tom anketa sama
  (`canRetract`), ne příznaky od serveru — proto je to kandidát bloku, ne právo z nabídky.
- **Zakázaný řádek odpovědi NESMÍ mít `enabled = false`.** Vzal by s sebou i dlouhý stisk a nad
  anketou, kde se hlasovat nedá, by nešla otevřít nabídka. Řádek je `combinedClickable` vždycky,
  jen bez vlnky a s prázdným ťuknutím.
- **Pruhy výsledků vyjedou od nuly jen tehdy, když se výsledky odkryly před očima** (hlas,
  uzavření). Položka se při scrollu skládá znovu a pruhy by jinak jezdily při každém průletu.
- **Šířka ankety je pevná** (nejvýš 360 dp), ne podle obsahu: krátké odpovědi by udělaly úzkou
  bublinu, ve které rozdíl 30 a 40 % na pruhu nejde vidět.
- **`PollVotersViewModel` čte parametry Koinu INDEXEM** — `chatId` a `messageId` jsou dvě `Long`
  a podle typu by se zaměnily (tatáž past jako u `messageId` konverzace).
- **Ověřuje se testovacím botem**: `sendPoll` do soukromého chatu projde (anketa, kvíz
  s `correct_option_id` a `explanation`, `allows_multiple_answers`, `is_anonymous=false`),
  a `stopPoll` botem ukáže uzavřenou anketu živě. Ověřeno na emulátoru (česky, světlý i tmavý
  motiv): hlas jednou i víc odpověďmi, špatná odpověď na kvíz s vysvětlením, zrušení hlasu,
  „Kdo hlasoval", uzavření.
- **Přidat odpověď do cizí ankety jde, když to server dovolí** (`Poll.canAddOption`, tlačítko
  „Přidat odpověď“ pod odpověďmi → `AddPollOptionDialog` → `addPollOption`). Právo nese anketa
  sama (autor ho zapnul a účet smí), aplikace nic nedopočítává — jen u uzavřené ankety tlačítko
  schová, i kdyby příznak zůstal. Nová odpověď se lokálně nepřidává: přijde úpravou obsahu
  zprávy jako hlas, a server k ní pošle servisní zprávu. Strop délky je týž jako ve formuláři
  (`POLL_ANSWER_MAX`). Ověřuje se `sendPoll` botem s `allow_adding_options=true`
  a `is_anonymous=false`; `getUpdates` pak ukáže `added_by_user` a `poll_option_added`.
- **Přidaná odpověď smí nést přílohu** — tatáž ikona a nabídka jako u odpovědi ve formuláři
  (`MediaSlot`, výběr GIFu, samolepky, odkazu a místa je společný `PollMediaChooser`), TDLib
  u `addPollOption` bere tytéž druhy. Příloha žije jen v dialogu (přežít otočení nemusí).
  **Výběry se otevírají NAD dialogem**: sheet i systémový výběr fotek jsou vlastní okna
  přidaná později, takže leží nahoře a po výběru se vrací do dialogu. Ověřeno samolepkou:
  sheet nad dialogem, náhled v poli, odpověď s kachnou v anketě a Bot API ukázalo
  `options[].media.sticker`.
- **V zápatí jsou tváře posledních hlasujících** (`Poll.recentVoterIds` → `ChatPoll.recentVoters`,
  nejvýš `MAX_RECENT_VOTERS` = 3, přes sebe, nejnovější navrchu, s lemem v barvě bubliny;
  `FaceStack` v `core/ui`, společný s čipem komentářů).
  Server je posílá jen u neanonymní ankety. Jména a fotky dohledává repository z keše
  odesílatelů (`resolveSender`) při převodu zprávy — `toChatPoll` je čistá a nechává je prázdné.
  Čtečce obrazovky nic neříkají: kdo hlasoval, je v „Kdo hlasoval". Víc tváří než vlastní jeden
  účet nevyrobí — přes sebe se ověřovaly sondou, která seznam doplnila botem a iniciálami.
- **Zakládání je obrazovka `PollComposerRoute`** (sponka → Anketa), bez metadat scény jako
  přeposílání: jde přes celou plochu z hlavního seznamu i archivu, zpět umí sám `NavDisplay`.
  Pravidla formuláře jsou čisté funkce (`pollDraftOf`, `normalizedAnswers`,
  `correctAfterRemoval`, testy `PollComposerTest`).
- **Do soukromého chatu s ČLOVĚKEM anketa nejde** — TDLib: *„Polls can be sent to a private
  chat only if the chat is a chat with a bot or the Saved Messages chat."* Server by ji odmítl
  až po odeslání, proto o tom rozhoduje `ContentAccess.polls` (právo `canSendPolls` A druh
  chatu, `pollsAllowedIn`) a sponka ji jinde nenabízí. Chat s botem je proto i místo, kde se
  zakládání dá ověřit.
- **`ContentAccess.polls` nemá výchozí hodnotu, a schválně.** S `= true` se starý test skupiny
  „jen text" tiše změnil ve skupinu, kde se smí anketa, a sponka ožila. Každé místo, které práva
  skládá, musí o anketě rozhodnout výslovně.
- **Na konci odpovědí je vždycky jedno prázdné pole**, dokud se nedojde ke stropu — tlačítko
  „Přidat odpověď" by bylo ťuknutí navíc u každé odpovědi. Strop počtu posílá server
  (`poll_answer_count_max`, na emulátoru 12). Prázdné pole UPROSTŘED zůstává, odebírá se
  křížkem; správná odpověď kvízu se po odebrání drží TEXTU, ne pozice.
- **Kvíz v TDLib 15 smí mít víc správných odpovědí** (`correctOptionIds` je seznam) — nabízí se
  s „Víc odpovědí" (zaškrtávátka místo koleček).
- **Text se nad strop kousek pustí** (a Odeslat zašedne, počítadlo zčervená), aby šel vložit
  delší text a zkrátit ho; utnout ho hned by vložení tiše usekalo.
- **V kanálu je anketa vždycky anonymní** — přepínač se nekreslí a repository pošle
  `isAnonymous = true` i tak (neanonymní server do kanálu nepustí).
- **Odpověď anketou: formulář rozepsanou odpověď PŘEVEZME a po odeslání ji ohlásí jako
  spotřebovanou.** Routa nese `replyToMessageId` a citovaný úryvek, formulář ukáže citaci nad
  otázkou (křížek = anketa odejde bez odpovědi). Po úspěšném odeslání zavolá
  `ConversationReplyRequests.replySent` — STAV, jako požadavek odpovědi z prohlížeče — a
  konverzace odpověď zahodí, když pořád míří na tutéž zprávu (`replyConsumedBy`). **Dřív to
  konverzace poznávala z příchozí zprávy** (vlastní anketa ve stavu `Sending`), jenže
  NAPLÁNOVANÁ anketa do historie nepřijde (viz „Naplánované zprávy") a odpověď nad polem pak
  visela. Odpověď se nezahazuje při OTEVŘENÍ formuláře — kdo ho zavře, má ji mít dál.
- **Doba trvání je `openPeriod`, přesný čas `closeDate` — a jen jedno z nich.** Doba nezávisí
  na hodinách telefonu, proto je výchozí; nabídka (5 minut až týden) se ořízne stropem serveru
  `poll_open_period_max` — na emulátoru 2 628 000 s, tedy měsíc; bez stropu se volba nekreslí.
  Poslední položka nabídky („Vybrat datum a čas…", vlastní skupina) otevře výběr data a pak
  času; řádek se pak jmenuje „Konec ankety" — „Doba trvání: zítra 18:48" nesedělo.
  „Výsledky až po skončení" (`hideResultsUntilCloses`) se nabízí jen s koncem ankety.
- **Vlastní čas se hlídá PŘED odesláním** (`closeDateValid`, testy `PollComposerTest`):
  nejdřív za 5 minut (`POLL_MIN_CLOSE_AHEAD_SECONDS` — těsný čas by během vyplňování vypršel),
  nejpozději za strop serveru. Neplatný čas zůstane v řádku červeně s větou, dokdy smí anketa
  skončit, a Odeslat zašedne. **Čas stárne i bez úhozu**, takže obrazovka `now` ve ViewModelu
  obnovuje každých 15 s, dokud je vlastní čas nastavený.
- **Výběr data vrací půlnoc v UTC, hodina platí v MÍSTNÍM pásmu** (`closeDateOf`,
  `closeDaySelectable`). Tatáž past jako ve filtrech hledání. Strop 2 628 000 s je 30 dní
  a 10 hodin, takže nejzazší čas přeskočí přes změnu letního času — zkontrolováno: 8:51 v září
  → „25. října 17:51".
- **Popis ankety** (`description`) je pole pod otázkou v jejím bloku; strop je
  `message_caption_length_max` (1 024). Odkazy v něm se dohledávají `getTextEntities`, jako
  u popisku přílohy — bez toho by odešly jako mrtvý text.
- **Kdo smí hlasovat jen v kanálu** („for channel chats only"): `membersOnly` (v kanálu déle
  než den) a `countryCodes`, nejvýš `poll_country_count_max` zemí (na emulátoru 12). Mimo kanál
  se blok nekreslí a repository omezení neposílá, ani kdyby ve formuláři zůstala. Země vybírá
  tentýž `CountryPickerSheet` jako přihlášení, v režimu víc zemí (fajfka místo předvolby, nad
  stropem zašedlé) — proto se přestěhoval z `features/auth` do `core/ui`.
- **Konec ankety je v podtitulu bubliny** („končí zítra 14:30", `asUpcomingTime`), jen dokud
  nenastal; pak server anketu uzavře a podtitul řekne „Konečné výsledky". Nepřepočítává se
  každou vteřinu — uzavření přijde updatem obsahu.
- **Tlačítko bota „vytvořit anketu"** (`KeyboardButtonTypeRequestPoll`, třeba @QuizBot) otevře
  týž formulář; `forceQuiz` / `forceRegular` schová přepínač kvízu (`PollComposerMode`).
  Ověřuje se `sendMessage` s `reply_markup.keyboard[…].request_poll`.
- **Ověřeno na emulátoru** v chatu s botem: kvíz se třemi odpověďmi, správnou a vysvětlením
  odešel, odpověď na vlastní kvíz v odchozí bublině (barvy správně / špatně tam ustoupí bílé
  a rozdíl nesou ikony), „Ukončit anketu" z nabídky s potvrzením, tlačítko bota s vynuceným
  kvízem, anketa jako odpověď s dobou 1 hodina (citace v bublině, „končí 08:30", odpověď nad
  polem zmizela), popis s odkazem a vlastní čas konce („končí zítra 18:48"). **Blok „Kdo smí
  hlasovat" jen podstrčeným `isChannel = true`** (sonda, necommituje se): výběr zemí, „2 země",
  neplatný čas. **V testovacím kanálu a skupině ověřené**: anketa ve skupině (odeslání,
  hlas, 100 % / 0 %), přidání odpovědi do cizí ankety ve skupině (bot `sendPoll`
  s `allow_adding_options`, „Sushi" přibylo) a anketa v kanálu s „jen členové" (server ji přijal).
  Že omezení opravdu platí, ověřit nejde — hlasovat by musel nečlen.
- **Sheet se na snímku ukáže až po chvíli.** `screencap` hned po ťuknutí zachytí obrazovku bez
  něj a vypadá to, že tlačítko nefunguje; `uiautomator dump` už ho vidí.

#### Média v anketě a v odpovědích

Otázka i každá odpověď mohou nést přílohu (TDLib 15 `PollMedia`, doména `PollAttachment`).

- **Fotka, video, GIF, samolepka a soubor jsou `MessageMedia`**, stejně jako příloha zprávy
  (`PollMedia.toAttachment`). U otázky je proto kreslí tentýž `MessageAttachment` (rámeček podle
  poměru stran, GIF hraje sám, let do prohlížeče); odpověď má 44dp čtvereček (`AnswerMedia`).
  **Místo je `MessageMedia.Location`, tedy mapa jako u zprávy s polohou** (viz „Poloha
  a mapy"), u otázky s názvem a adresou pod ní, u odpovědi mapa ve čtverečku. Odkaz obdobu
  u zprávy nemá: řádek s ikonou, ťuknutí otevře web.
- **Náhled u odpovědi je samostatný cíl ťuknutí.** Řádek ťuknutím hlasuje; bez toho by nešla
  fotka odpovědi prohlédnout, aniž by se pro ni hlasovalo. Co otevřít nejde (soubor, zvuk),
  ťuknutí propustí řádku.
- **Příloha vysvětlení kvízu** (TDLib 15 `explanationMedia`, `PollQuiz.explanationMedia`) je
  pod vysvětlením, stejně velká jako příloha otázky (`PollQuestionMedia(slot = …)`), a stejně
  jako vysvětlení až po odpovědi. Do prohlížeče letí pod vlastním místem
  `POLL_EXPLANATION_MEDIA` (−3; −2 je `NO_POLL_MEDIA`), popiskem je text vysvětlení. Bot API
  ji pošle (`sendPoll` s `explanation_media`).
- **Formulář ji nabízí ikonou v poli vysvětlení**, se stejnou nabídkou jako u otázky: TDLib
  bere u vysvětlení tutéž sadu (animace, zvuk, soubor, poloha, fotka, místo, video), samolepku
  a odkaz ne — pravidlo je tedy `allowedForQuestion` a pojistka v `pollDraftOf`. Výběr jde přes
  `mediaTarget = POLL_EXPLANATION_MEDIA`. **Mimo kvíz se neposílá** (`explanationMedia.takeIf
  { quiz }`), ale ve formuláři zůstane: přepnutí na anketu a zpět ji nemá zahodit. Ověřeno na
  emulátoru kvízem s fotkou u vysvětlení v chatu s botem: Bot API ukázalo
  `explanation_media.photo` a po špatné odpovědi je fotka pod vysvětlením.
- **Pod jedním id zprávy je obrázků víc, takže klíč letu nese i přílohu**
  (`LocalPollMediaFlight` v `photoKey`, `MediaRoute.pollMedia`). Samotné id zprávy by spárovalo
  fotku odpovědi s fotkou otázky. Index odpovědi je PŮVODNÍ (`PollAnswer.index`), otázka
  `POLL_QUESTION_MEDIA`.
- **Prohlížeč ukazuje přílohu ankety samotnou a bez akcí** (`loadPollMedia`). Reakce, sdílení,
  přeposlání i smazání by šly na CELOU anketu — přeposlat fotku odpovědi by odeslalo anketu.
  Popiskem je otázka, nebo text odpovědi.
- **Formulář nabízí všechny druhy, které TDLib bere** — ikona v poli otevře nabídku: fotka nebo
  video (systémový výběr), GIF a samolepka (tentýž `StickerPickerSheet` jako v konverzaci, jen
  s jedinou částí — `modes`), odkaz (dialog, `pollLinkOf`: bez schématu `https://`, jen
  http(s), doména s tečkou) a místo. **Otázka smí jen fotku, video, GIF a místo**
  (`PollDraftMedia.allowedForQuestion`) — samolepku a odkaz TDLib u otázky nebere a server by
  odmítl celou anketu; nabídka u otázky je proto kratší. GIF a samolepka se posílají jako
  `InputFileId` souboru, který server už má. **Příloha odpovědi jen s textem**: prázdné pole se
  neposílá a příloha by zmizela potichu; po vymazání textu se proto zahodí i ona. Přílohy patří
  TEXTU, ne pozici (`afterRemoval`, testy `PollComposerTest`).
- **Místa hledá inline bot, kterého jmenuje server** (`venue_search_bot_username`, `PlaceRepository`)
  — pomocník `InlineBot` je společný s GIF botem. **Bez polohy uživatele bot nevrací nic
  použitelného**: na „Staroměstské náměstí Praha" i na samotné „Praha" přišel supermarket
  z amerického předměstí. Hledá se proto kolem polohy zařízení; prázdný dotaz vrací místa
  v okolí a „Moje poloha" (holá `InputPollMediaLocation`) je vždycky první řádek.
- **Poloha zařízení je nová hranice `core/location`** (`rememberDeviceLocation`): jen PŘIBLIŽNÁ
  (`ACCESS_COARSE_LOCATION`, iOS kilometr) a až ve chvíli, kdy uživatel přidává místo. Android
  `LocationManager` bez Play služeb (čerstvá poslední známá poloha, jinak jedno zjištění
  s limitem 10 s), iOS `CLLocationManager` s delegátem drženým silnou referencí,
  `NSLocationWhenInUseUsageDescription` v projektu. `uses-feature` polohy je `required=false`,
  jinak by oprávnění udělalo z polohy povinný hardware a aplikace by zmizela z Play pro zařízení
  bez ní. Bez polohy sheet řekne, proč místa najít nejde.
- **`adb emu geo fix` polohu emulátoru nezměnil** (`dumpsys location` hlásí dál výchozí Mountain
  View, `fused` i `gps`). Na ověření to stačí — výsledky jsou místa kolem té polohy —, jen se
  nečekej Praha.
- **Bot API to umí od verze 10.0** (`sendPoll` s `media`, `options[].media`; odkaz u odpovědi od
  10.1), a tudy se ověřují druhy, které formulář nenabízí: místo u otázky, odkaz, poloha a GIF
  u odpovědí, GIF u otázky a samolepka u odpovědi.
- **Ověřeno na emulátoru** v chatu s botem: anketa s fotkou u otázky i u odpovědi odešla, fotka
  otázky i náhled odpovědi se otevřou v prohlížeči (s popiskem, bez akcí), ťuknutí na náhled
  nehlasuje, náhled je i ve výsledcích; od bota místo, odkaz s náhledem webu, poloha, GIF
  (s ikonou přehrání u odpovědi, hrající u otázky) a samolepka. Z formuláře odešla anketa s místem
  z hledání, odkazem (server doplní obrázek webu) a samolepkou u odpovědí a s GIFem u otázky
  a „Moje poloha" u odpovědi; dotaz na přibližnou polohu se ukáže až při „Místo". Mapa u otázky
  i u odpovědi otevře Google Mapy a ťuknutí na ni nehlasuje. **Ťuknutí na odkaz (otevření webu)
  ověřené není** — jde toutéž cestou jako odkaz v textu.

### Tlačítka botů pod zprávou

Inline klávesnice bota (`ReplyMarkupInlineKeyboard`) se kreslí pod zprávou jako blok zaoblených
tlačítek (`BotKeyboardView`, `WithBelowMessage` v `items/BotKeyboard.kt`; doména `BotKeyboard`
v `ChatMessage.keyboard`). Dokud nebyla, nešlo projít kontrolou proti botům, kterou si velké
skupiny dávají před vstup — odpověď napsaná textem se nepočítá, bot čeká na ťuknutí.

- **Data zpětného volání do domény NEJDOU.** Jsou to bajty od bota a klávesnice se mezi
  vykreslením a ťuknutím může změnit. Tlačítko se určuje POZICÍ (`MessageAction.PressBotButton`)
  a repository si data dočte ze zprávy (`pressBotButton`), včetně kontroly, že na tom místě je
  pořád tlačítko s tímtéž textem.
- **Bot klávesnici přepisuje bez změny obsahu** (odpočet pokusů, další krok menu) — přijde jen
  `UpdateMessageEdited`, ne `UpdateMessageContent`. `editedMessages` proto odebírá obojí.
- **Na odpověď se čeká u jednoho tlačítka naráz** (`pressingBotButton`, na tlačítku se točí
  indikátor). Druhé ťuknutí by zpětné volání poslalo podruhé a u kontrolní otázky s jediným
  pokusem by spálilo odpověď.
- **Odpověď bota má tři podoby** (`BotAnswer`): krátký text jako snackbar, upozornění
  (`showAlert`) jako dialog, který se musí potvrdit, a odkaz. Prázdná odpověď je běžná — bot
  jen přepíše zprávu.
- **Odkaz mimo Telegram se napřed potvrzuje** (`OpenLinkDialog` s celou adresou). U odkazu
  v textu uživatel adresu vidí, na tlačítku ne. Odkaz na Telegram (`resolveLink`) se otevře
  rovnou, jako odkaz v textu.
- **Co Plume neumí, je vidět a ťuknutí to vysvětlí** (`BotButtonKind.Unsupported`: platba,
  hra, heslo, inline dotaz s cílem daným odkazem). Mini aplikace a přihlášení na web už umí
  (viz níž a „Mini aplikace botů"). Schované tlačítko by
  z klávesnice bota vykouslo řádek nebo jeho část a změnilo, co bot nabízí.
- **Inline dotaz tlačítkem** (`BotButtonKind.SwitchInline`, `SwitchInlineTarget`) dá do pole
  `@bot dotaz` — v tomhle chatu, nebo v chatu z výběru (`SwitchInlinePickerScreen`). Nic se
  neposílá, výsledek si uživatel vybere z napovídání nad polem (viz „Inline boti").
  - **Jméno bota tlačítko nenese**: je to bot, „přes" kterého zpráva přišla, jinak odesílatel.
    Dohledává se při převodu zprávy, a jen u klávesnice s takovým tlačítkem. **Past:**
    v `toChatMessage` je `senderId` už převedený na číslo (`plainId`), takže přetypování na
    `MessageSenderUser` tiše vrátilo `null` a tlačítko spadlo do „neumím" — kladné číslo je
    uživatel.
  - **Výběr nabízí jen druhy chatu, které bot dovolil, a jen chaty, kam se smí psát**
    (`inlineQueryTargets`, `SwitchInlineTarget.Chosen.allows`). Druh se bere z položky seznamu
    a TDLib se ptá jen tam, kde chat projít může: dotaz na každý chat v seznamu trval na
    emulátoru přes minutu. Bota od člověka rozliší `getUser`, jen když bot dovolil jedno bez
    druhého.
  - **Dotaz do vybraného chatu je STAV** (`InlineQueryRequests`), jako odpověď z prohlížeče:
    konverzace se teprve skládá, a i otevřená si ho vezme až po obnovení konceptu — koncept jde
    do prázdného pole a dotaz by jinak vytlačil. Rozepsanou zprávu dotaz nepřepíše (stejné
    pravidlo jako ťuknutí na „přes @bot").
  - **Ověřeno na emulátoru** zprávou od testovacího bota (`switch_inline_query_current_chat`,
    `switch_inline_query`, `switch_inline_query_chosen_chat` jen se skupinami): pole v témž
    chatu, výběr jen se skupinami, kam smí psát, výběr všeho s Uloženými zprávami. Testovací bot
    inline režim nemá, takže se pod polem výsledky neukážou — **napovídání po tlačítku ověřené
    není**, jde ale cestou napsaného `@bot dotaz`.
- **Přihlášení na web** (`BotButtonKind.LoginUrl`, `getLoginUrlInfo` → `LoginUrlPrompt`):
  server buď řekne „otevřít" (jako odkaz, s potvrzením adresy, pokud ho nevypne
  `skipConfirmation`), nebo „zeptat se" — pak `LoginUrlDialog` s doménou v nadpisu, větou,
  co web dostane (jméno, uživatelské jméno, fotku), a u `requestWriteAccess` zaškrtávátkem
  „dovolit botovi psát" (výchozí zapnuté, jako u oficiálních klientů). Potvrzení vezme adresu
  z `getLoginUrl` a otevře ji.
  - **Chyba = obyčejný odkaz**, tak to předepisuje TDLib. Chyba `getLoginUrl` až po potvrzení
    otevře web bez přihlášení a řekne to hláškou — uživatel web chtěl.
  - **Tlačítko s `login_url` bot pošle, jen když má v BotFatheru doménu** (`/setdomain`), jinak
    Bot API vrátí `BOT_DOMAIN_INVALID`. Testovací bot ji nemá, takže se ověřovalo sondou:
    obyčejné odkazové tlačítko vydávané za přihlašovací (TDLib pak vrátí „Button not found"
    a nabídne se odkaz) a podstrčené `LoginUrlPrompt.Confirm` pro vzhled dialogu.
- **Tlačítka jsou vysoká rovnou 48 dp.** S nižším je `Surface` dorovná na dotykovou velikost
  PRŮHLEDNÝM okrajem a svislé mezery mezi řádky vyšly o 8 dp větší než vodorovné.
- **Šířka klávesnice: aspoň jako zpráva, víc jen když potřebuje**, do stropu šířky zprávy —
  vlastní `Layout`, ne `IntrinsicSize` (zpráva bývá fotka v `BoxWithConstraints`).
- **Barvy od bota** (`ButtonStyle` v TDLib 15): výchozí tonální `secondaryContainer`, „primary"
  `primary`, „danger" `errorContainer`, „success" `tertiaryContainer` — vlastní roli pro úspěch
  M3 nemá.
- **Ověřuje se testovacím botem přes Bot API**: `sendMessage` s `reply_markup.inline_keyboard`
  (i `copy_text`, `style`), ťuknutí v aplikaci a `getUpdates` → `answerCallbackQuery` (s `text`,
  `show_alert`). **`getUpdates` si update potvrdí až dalším voláním s `offset`** — skript, který
  skončí hned po odpovědi, dostane příště TENTÝŽ callback znovu a `answerCallbackQuery` na něj
  vrátí 400. Přepsání klávesnice se zkouší `editMessageReplyMarkup`.
- **Ve skutečných skupinách na tlačítka neťukat** — antispamoví boti mají pod zprávami
  „Skrýt" / „Za co?" a ťuknutí jedná jménem účtu.

### Vlastní klávesnice bota

Klávesnice, kterou bot posílá místo systémové (`ReplyMarkupShowKeyboard`, třeba BotFather),
a vynucená odpověď (`ReplyMarkupForceReply`). Kreslí je `ReplyKeyboardPanel` NAD vstupním
polem; doména `ChatReplyMarkup`, zdroj `ConversationRepository.replyMarkup`.

- **Nad polem, ne místo systémové klávesnice** (rozhodnutí uživatele). Výměna systémové
  klávesnice za panel tak, aby pole neposkočilo, je nejkřehčí část Compose — tady se nic
  nepřepíná a jde psát i ťukat zároveň. Cena je výška: strop (`MAX_HEIGHT`) s rolováním
  a šipka, která panel sbalí. Sbalení se pamatuje pro jednu klávesnici (klíč je její zpráva).
- **Klávesnice patří CHATU, ne zprávě.** Ze které zprávy se bere, drží TDLib
  (`Chat.replyMarkupMessageId`) a mění ji `UpdateChatReplyMarkup` (nová zpráva bota, jiná
  klávesnice, vynucená odpověď). Odběr stojí před prvním čtením.
- **Jednorázová klávesnice se po použití SCHOVÁ, NEZMIZÍ.** Bot API: „the keyboard will still
  be available … the user can press a special button to see it again". `deleteChatReplyMarkup`,
  které TDLib po klientovi chce, update nepošle a klávesnice v chatu zůstává; panel se proto
  po použití jen sbalí. Kdo čeká, že klávesnice zmizí, hledá chybu, která tam není.
- **Že byla jednorázová klávesnice použitá, si pamatuje aplikace** (`AppSettings.usedOneTimeKeyboards`,
  chat → id zprávy s klávesnicí, nejvýš 200 chatů). TDLib to neví: klávesnice v chatu zůstává
  a po znovuotevření chatu se ukázala rozbalená, jako by ji nikdo nepoužil. Oficiální klient na
  Androidu to řeší stejně (`hidekeyboard_<dialog>` v preferencích). Pamatuje se ZPRÁVA, ne
  příznak: nová klávesnice od bota má jiné id a ukáže se rozbalená. Ověřeno na emulátoru
  i po `force-stop`.
- **Text z tlačítka nemaže rozepsaný koncept** (`sendKeyboardText` s `clearDraft = false`).
  Běžné odeslání (`send`) koncept maže, protože posílá obsah pole — tlačítko ne.
- **Ve skupině jde text tlačítka jako odpověď** na zprávu s klávesnicí, jako u oficiálního
  klienta; v soukromém chatu je citace šum.
- **Sdílení čísla je za potvrzením** (`SharePhoneDialog`) — údaj o uživateli nejde odeslat na
  jedno ťuknutí. Zakládání botů (`RequestManagedBot`) se jen vysvětlí, jako u tlačítek pod
  zprávou; anketa otevře formulář, mini aplikace se otevře (viz „Mini aplikace botů").
- **Poloha z tlačítka je PŘESNÁ a za potvrzením** (`ShareLocationDialog`,
  `rememberDeviceLocation(precise = true)`). Bot s ní volá taxi nebo kurýra; kilometrová
  přesnost, která stačí na místa v okolí, by tam nic neřekla. Android žádá přesnou spolu
  s přibližnou (uživatel smí zvolit přibližnou a pošle se ta), iOS `kCLLocationAccuracyBest`.
  Když čerstvá nepřijde (GPS v budově), pošle se poslední známá do deseti minut, jinak hláška.
  - **GPS emulátoru nové fixy samo nedává a `adb emu geo fix` ho nezmění.** Polohu podstrčí
    testovací poskytovatel: `adb shell appops set com.android.shell android:mock_location allow`,
    `cmd location providers add-test-provider fused`, `set-test-provider-enabled fused true`,
    `set-test-provider-location fused --location 50.0875,14.4208`. Po testu
    `remove-test-provider fused` a appop vrátit na `deny`.
- **Výběr lidí a chatu je obrazovka** (`BotShareRoute`, `BotShareScreen` nad `ChatPicker`,
  `shareUsersWithBot` / `shareChatWithBot`). Lidí se vybírá víc (nejvýš `max_quantity`) a sdílí
  tlačítkem, chat se po ťuknutí potvrzuje dialogem. Nabízí se jen to, co seznam ví
  (`botShareCandidates`, testy `BotShareCandidatesTest`): soukromé chaty, nebo skupiny / kanály
  s tématy či bez nich; zbytek omezení (bot, prémiový účet, vlastník, veřejné jméno) ověří
  server při výběru (`onlyCheck`) a nevyhovující řádek se ohlásí hned, ne po odeslání.
  - **Že se bot do chatu PŘIDÁ a dostane práva správce, říká karta i dialog výslovně**
    (`botIsMember`, `botAdministratorRights`) — z tlačítka „vyber skupinu" to nikdo nečeká.
  - **`request_name` / `request_title` / `request_photo` klient NEVIDÍ.** V TDLib jsou „bots
    only" a uživatelský klient je dostane vždycky `false` — ověřeno: bot o název žádal, TDLib
    hlásil `requestTitle=false` a bot název přesto dostal. Karta proto říká, co bot dostat MŮŽE
    (jméno / název, uživatelské jméno, fotku), ne co žádá.
  - **Bot API chce u `bot_administrator_rights` i `user_administrator_rights`** (podmnožinu),
    jinak `USER_RIGHTS_MISSING`.
  - **Ověřuje se tlačítkem, které chce BOTY** (`user_is_bot: true`): sdílení lidí by testovacímu
    botovi poslalo účty skutečných kontaktů. Ověřeno na emulátoru: odmítnutí bota u tlačítka
    pro lidi, sdílení dvou botů (`users_shared`), vlastní testovací skupiny (`chat_shared`)
    a dialog u skupiny s botem jako správcem (zrušený — bot se do skupiny nepřidal).
- **Vynucená odpověď přepne pole na odpověď sama, jednou na zprávu**, a jen když se zrovna
  neupravuje ani neodpovídá jinam. Zpráva bota do seznamu dorazí klidně až po updatu, proto se
  na ni chvíli čeká. Po odeslání odpovědi se odebere (`deleteChatReplyMarkup`).
- **Nápověda do pole od bota** (`inputFieldPlaceholder`, `ConversationState.inputPlaceholder`,
  testy `ReplyMarkupTest`) platí u vynucené odpovědi jen tehdy, když se odpovídá právě na ni.
- **Ověřeno testovacím botem** (`sendMessage` s `reply_markup.keyboard`, `one_time_keyboard`,
  `input_field_placeholder`, `request_contact`, `request_location`; pak `force_reply`).
  **Odeslání čísla ověřené není**, jen dialog.

### Příkazy bota

`/příkaz` v textu zprávy je klikatelný a ťuknutí ho pošle (`MessageEntityType.BotCommand`),
po napsání „/" nad polem napovídá seznam příkazů a v chatu s botem je u pole tlačítko s robotem,
které seznam otevře celý (`BotCommandsPanel`, doména `BotCommandItem`, pravidla
`commandSuggestions` a `botCommandText`, testy `BotCommandsTest`).

- **Příkazy se čtou z plných údajů** (`botCommands`): v soukromém chatu `UserFullInfo.botInfo
  .commands`, ve skupině `BotCommands` všech botů z `BasicGroupFullInfo` / `SupergroupFullInfo`.
  Kanál a chat s člověkem žádné — tlačítko se tam neukáže. Čtou se jednou při otevření chatu.
- **Ve skupině se posílá `/příkaz@jmeno_bota`.** Botů tam může být víc a holý příkaz by dostali
  všichni, nebo žádný (podle soukromí bota). Tak to dělají i oficiální klienti. Příkaz ťuknutý
  v textu jde tak, jak v textu stojí.
- **Příkaz v textu je klikatelný jen tam, kam se smí psát** (`ConversationItemScope
  .canSendCommands`, `RichText(commandsClickable)`). Jinde — kanál bez práva psát, profil,
  anketa — zůstane obyčejným textem: modrý text, po kterém se nic nestane, je horší.
- **Ťuknutí na příkaz v textu nemaže koncept** (`sendKeyboardText`, jako tlačítko vlastní
  klávesnice). Návrh z napovídání naopak pole vyprázdní i s konceptem — rozepsané „/st" byl ten
  příkaz.
- **Napovídání je jen pro text bez mezery** (`commandSuggestions`): po mezeře uživatel píše
  argument nebo větu, která náhodou začíná lomítkem. Část za `@` se ignoruje.
- **Nad polem, ne ve sheetu**, jako vlastní klávesnice bota: napovídání se mění s každým
  písmenem a sheet by se o klávesnici přetahoval. Seznam z tlačítka zavře psaní i gesto zpět.
- **Ikona je robot (`smart_toy`)** — oficiální klient má pilulku „Menu", jenže tři čárky se
  v Material čtou jako navigační zásuvka.
- **Ověřeno testovacím botem**: `setMyCommands` (tři příkazy), zpráva s příkazy v textu. Seznam
  z tlačítka, napovídání po „/n", ťuknutí na návrh (pole prázdné) i na příkaz v textu — všechny
  tři příkazy dorazily botovi (`getUpdates`). V Uložených zprávách tlačítko není. **Ve skupině
  ověřené** (testovací skupina s botem): seznam nabízí `/stav@NoTiBoBoBot` a odejde s příponou.
  **`getUpdates` testovacího bota si občas vybere jiný proces** (sdílený token), takže update
  zmizí dřív, než se na něj dojde — bublina v konverzaci je pak spolehlivější doklad.
- **Tlačítko nabídky bota (`BotMenuButton`) stojí na místě tlačítka příkazů** — viz „Mini
  aplikace botů". Příkazy pak napovídá jen „/", jako u oficiálních klientů.

### Spuštění bota

Prázdný chat s botem je jeho **profil** (`BotIntroPage`): velká tvář nahoře uprostřed, jméno,
„Bot" a pod tím popis (`BotInfo`), obrázek nebo GIF od bota až pod textem. Místo vstupního pole
je tlačítko **Start** (`sendBotStartMessage`). Startovací odkaz `t.me/bot?start=…` pošle botovi
parametr (`InternalLink.BotStart`).

- **Profil, ne karta uprostřed.** První verze byla karta „Co tento bot umí?" uprostřed prázdné
  konverzace; uživatel chtěl, aby to vypadalo jako profil — tvář a jméno představí bota dřív
  než text. Kreslí se i u bota bez popisu (`showsBotIntro`): tvář a jméno řeknou, s kým chat je.
- **Hlavička nad profilem nenabízí hledání ani shrnutí** — v prázdném chatu není co hledat
  a lupa nad profilem působila jako ovládání stránky.
- **Tvář je nad profilem jediná**: hlavička avatar schová (`AnimatedVisibility`, po Startu se
  vrátí) a velká tvář převezme, co nesl — tečku „online" a při výpadku zvlnění s prstencem
  (tentýž `rememberConnectionIndicator`, větší rozměry; popis stavu pro čtečku). Mezera mezi
  avatarem a jménem v hlavičce je proto odsazení AVATARU, ne `spacedBy` řádku — schovaný avatar
  by jinak nechal před jménem díru. Ověřeno letovým režimem.

- **Kdy Start, je čistá funkce** `showsBotStart` (testy `BotStartTest`): chat s botem
  (`botIntro != null`) a buď čekající parametr z odkazu, nebo PRÁZDNÁ historie. Prázdnou
  historii dokazuje až `reachedStart` — výchozí stav je taky prázdný a `loading` v něm ještě
  neplatí, takže by se u každého bota na okamžik ukázal Start.
- **Parametr z odkazu je STAV, ne událost** (`BotStartRequests`, jako `ChatOpenRequests`):
  odkaz se ťukne v jedné konverzaci a Start je v jiné, která ještě nemusí existovat. Přes routu
  se nepředává — změnila by se serializace zásobníku i parametry ViewModelu kvůli jedné hodnotě.
- **`autostart` rozhoduje TDLib**, ne my: u bota, se kterým chat už existuje, vrátí
  `InternalLinkTypeBotStart.autostart = true` a `/start` s parametrem odejde hned, bez
  tlačítka (tak to dělají i oficiální klienti). Tlačítko s čekajícím parametrem se tedy ukáže
  jen u bota, se kterým se ještě nepsalo.
- **V chatu je „/start" bez parametru**, parametr dostane jen bot — TDLib zprávu takhle vrací.
- **Odkazy, zmínky, hashtagy a příkazy v popisu jsou klikatelné** (`BotIntro.descriptionEntities`).
  Bot popis nastavuje jako holý text, entity dohledává TDLib lokálně (`getTextEntities`), jako
  u popisu skupiny v profilu. Ťuknutí jde TOUŽ cestou jako v textu zprávy (`MessageAction` přes
  scope obrazovky): `/help` z popisu odejde botovi, `@jméno` otevře chat nad konverzací. Ověřeno
  popisem nastaveným testovacímu botovi (`setMyDescription`) a sondou v `showsBotIntro`
  (necommituje se); popis se po ověření vrátil.
- **Ověřeno na emulátoru**: startovací odkaz na testovacího bota (autostart, bot dostal
  `/start hello`), prázdný chat s `@gif` (profil s popisem od serveru, bez lupy, a Start — **nestisknuté**,
  bot není náš) a tlačítko Start v chatu s testovacím botem přes sondu v `showsBotStart`
  (bot dostal `/start`; sonda se necommituje). **Neověřeno**: obrázek a GIF v profilu (testovací
  bot je nemá — Bot API je nastavit neumí, jen BotFather), čekající parametr u nespuštěného
  bota, iOS (jen přeložené).

#### Přidání bota do skupiny

Odkaz `t.me/bot?startgroup=…` (`InternalLink.BotStartInGroup`) otevře výběr skupiny
(`AddBotToGroupRoute`, `AddBotToGroupScreen` nad `ChatPicker`) a po potvrzení bota přidá
a pošle mu `/start` s parametrem (`ChatRepository.addBotToGroup`). Pak se otevře skupina.

- **Nabízí se jen skupina, kam bota přidat JDE** (`canAddBot`, testy `BotInGroupTest`):
  vlastník vždy, správce s právem zvát (a s `&admin=…` s právem jmenovat správce), člen jen
  když skupina členům zvát dovolí, a nikdy jako správce. Seznam, ve kterém by většina skupin
  skončila chybou od serveru, je horší než kratší seznam — na emulátoru z něj zmizely velké
  skupiny, kde účet jen čte, a s `&admin=` i skupina, kde účet správce jmenovat nesmí.
- **Skupiny se berou z načtených seznamů** (hlavní a archiv). Archiv se načítá až při otevření,
  takže archivovaná skupina ve výběru chybí, dokud ho uživatel neotevřel.
- **Práva správce jsou přesně ta z odkazu** (`administratorRights`); potvrzení to říká vlastní
  větou. V ZÁKLADNÍ skupině jde jmenovat jen člena, takže se bot napřed přidá
  (`addChatMember`) a pak jmenuje; supergroupa ho přidá rovnou `setChatMemberStatus`.
- **Přidání se potvrzuje dialogem** — bot tím dostane přístup ke zprávám skupiny.
- **Ověřeno na emulátoru jen po potvrzovací dialog** (zprávou od testovacího bota s oběma
  odkazy, anglicky i česky, zrušení i zpět). **Samotné přidání a `/start` ověřené v testovací
  skupině** (bot z ní napřed odešel přes Bot API `leaveChat`): „Do skupiny přibyli členové",
  `/start@bot` a Bot API `getChatMember` → `member`. Do skutečných skupin se nic nepřidává.

### Inline boti

`@bot dotaz` na začátku pole (`@gif kočka`, `@wiki Praha`, `@pic cat`) ukáže nad polem výsledky
bota; ťuknutí výsledek pošle a u zprávy pak stojí „přes @bot" (`InlineResultsPanel`, doména
`InlineQuery.kt`, převod `InlineResultsMapping.kt`, pravidla `inlineQueryOf` a `InlineQueryState`,
testy `InlineQueryTest`).

- **Dotaz se pozná podle TVARU textu, ne podle bota**: zavináč na začátku, jméno a MEZERA. Do
  mezery může jít o zmínku; jestli `@jméno` je bot, se zjistí až dotazem (`inlineBot`,
  `searchPublicChat` + `getUser`, pamatuje se pro instanci klienta).
- **Jestli bot inline dotazy umí, TDLib 15 neřekne** — `userTypeBot` nemá `is_inline`
  (v knihovně ten řetězec je, ve wrapperu 15.0.0 pole ne). Pozná se to až odpovědí: chyba
  „Bot doesn't support inline queries" (ověřeno `strings libtdjsonjava.so`) panel schová a bot
  se do konce konverzace už neptá (`notInlineBots`). Ověřeno na `@BotFather`.
- **Dotaz jde po pauze 400 ms** a další písmeno ho zruší. Předchozí výsledky mezitím zůstávají
  vidět — stránka patří dotazu, na který se čeká, a stránka pro starý dotaz se zahodí
  (`InlineQueryState.withPage`), tentýž vzor jako výsledky GIFů.
- **Pole se čte v obrazovce, ne v `onDraftChanged`** (`LaunchedEffect(inlineQueryOf(draft.text))`).
  Text se do pole dostává i mimo psaní — vrácený koncept, sdílení, ťuknutí na „přes @bot" —
  a dotaz musí zareagovat na všechno z toho.
- **Posílá se `sendInlineQueryResultMessage` (queryId + id výsledku), ne obsah.** Zprávu
  si připravil bot a může se od náhledu lišit (článek odejde jako odkaz s náhledem webu).
  Proto i GIF z `@gif` odchází s „přes @gif" — na rozdíl od výběru GIFů, který stejného bota
  jen používá k hledání a posílá soubor.
- **Nad polem, ne ve sheetu**, jako napovídání příkazů: výsledky se mění s každým písmenem
  a klávesnice musí zůstat. Mřížka pro GIFy, fotky, obaly videí a samolepky; řádky přes celou
  šířku pro článek, soubor, hudbu, kontakt, místo a hru. Rozhoduje se po výsledcích — jeden bot
  vrací obojí. GIF buňka je tatáž jako ve výběru GIFů (`GifCell`), samolepka `StickerPreview`.
- **Tlačítko nad výsledky** (`InlineQueryResultsButton`): „nastavit u bota" otevře chat s botem
  a spustí ho s parametrem (`openBotStart` s `autostart`), mini aplikace jen vysvětlí, že ji
  Plume neumí (`@pic` má „Create image with Alice AI").
- **Bot s `needLocation` dostane přibližnou polohu** (`rememberDeviceLocation`); o oprávnění se
  žádá až u takového bota.
- **Dotaz na takového bota se pošle až s polohou** (`inlineLocationResolved`), nebo když je jasné,
  že nebude (nedovolená, nezjištěná do limitu — `request` pak vrátí `null`). Dřív odešel první
  dotaz hned bez polohy a s její příchodem druhý: bot odpověděl dvakrát a výsledky se pod prstem
  vyměnily (ověřeno v logu bota: `location None`, pak souřadnice). Že je bot „polohový", se
  pozná až z `getUser` (`InlineBotInfo.needLocation`), proto se čeká až po něm.
- **„přes @bot" je údaj o původu, stejně jako přeposlání** (`OriginLabel`, `OriginHeader`,
  `hasOriginLabel` v `MessageFrame.kt`): holý obsah s ním je karta, GIF od `@gif` má značku
  v hlavičce téže karty. Jméno bota se dohledává `getUser` (lokální, pamatuje se). Ťuknutí na
  značku dá `@bot ` do pole (`MessageAction.UseInlineBot`) — ale **rozepsanou zprávu
  nepřepíše**, to jen ohlásí; přepíše prázdné pole nebo jiný dotaz na bota.
- **Nápověda bota** (`inlineQueryPlaceholder`, „Search GIFs") je první řádek panelu, dokud je
  dotaz prázdný — do pole ji dát nejde, nápověda pole se ukazuje jen v prázdném poli.
- **Ověřeno na emulátoru** (Uložené zprávy): `@gif kočka` (mřížka hrajících GIFů, odeslání,
  „via @gif"), ťuknutí na „via @gif" (`@gif ` v poli, nápověda a populární), `@wiki Prague`
  (řádky, odeslání jako karta odkazu s „via @wiki"), `@pic cat` (fotky a tlačítko mini
  aplikace), `@BotFather x` (bez panelu). **Neověřeno**: video a samolepky ve výsledcích a iOS
  (jen přeložené).
- **Tlačítko „nastavit u bota" a bot s polohou ověřené testovacím botem.** BotFather mu zapnul
  inline režim (`/setinline`) a polohu (`/setinlinegeo`), na dotazy odpovídal skript přes Bot API
  (`getUpdates` s `allowed_updates: ["inline_query"]`, `answerInlineQuery` s `button.start_parameter`).
  Ťuknutí na tlačítko poslalo botovi `/start nastaveni`; dotaz s polohou odešel jednou, rovnou se
  souřadnicemi; po odmítnutí oprávnění odešel bez nich. Poloha emulátoru z testovacího
  poskytovatele (viz „Vlastní klávesnice bota"). Inline režim zůstal botovi zapnutý. **Ve skupině ověřené** (`@gif cat` v testovací skupině, GIF odešel). Obraz GIFů
  ve výsledcích i ve zprávě zůstal na emulátoru prázdný — selhává dekodér
  (`queueInputBuffer() failed`), viz „GIFy".
- **`chat_<id>` v `EXTRA_SHORTCUT_ID` se sdíleným textem je nejrychlejší cesta, jak dostat dotaz
  do pole** (`am start -a android.intent.action.SEND … --es android.intent.extra.TEXT '@gif kočka'`),
  i s diakritikou. Pozor na id testovacího bota — viz „Na snímcích nesmí být skuteční lidé".

### Plovoucí napovídání nad polem

Emoji ke slovu, příkazy bota, zmínky a výsledky inline bota PLAVOU nad vstupním polem
(`FloatingSuggestion`, `floatAbove`) na skle v inverzní roli jako snackbar, a místo v rozvržení
nezabírají.

- **Pole je `bottomBar` a seznam zpráv se odsazuje o jeho výšku.** Dokud napovídání stála v jeho
  sloupci, každé objevení, zmizení i změna výšky (další písmeno zúží seznam příkazů) zvedly
  a spustily celou konverzaci — oči při psaní skákaly za ní.
- **`floatAbove` nahlásí nulovou výšku a obsah posadí nad své místo.** Obsah mimo hranice rodiče
  Compose kreslí i nechá ťukat, dokud nic neořezává (ověřeno ťuknutím na emoji i příkaz).
  Vrstva stojí v `bottomBar` těsně nad polem, u textu, ke kterému patří.
- **Vyrůstá zvětšením od pole, ne rozbalením do výšky** — to by u plovoucího prvku vypadalo,
  jako by se pod ním něco odsouvalo.
- **Sklo je v INVERZNÍ roli, jako snackbar** (`inverseSurface`, popředí `inverseOnSurface`,
  akcent `inversePrimary`; `FloatingSuggestionColors`): ve světlém motivu tmavé, v tmavém světlé.
  Je to dočasný prvek nad obsahem a má od něj vystoupit v OBOU motivech — M3 na to inverzní role
  má. Dvě slepé uličky: světlé sklo v barvě plochy (jako pruh připnutých) se ve světlém motivu
  slévalo s bublinami, natvrdo tmavé (`scrim` s bílým textem) by se slilo s tmavou konverzací.
  `ListItem` popředí nedědí, barvy se mu předávají (`FloatingSuggestionColors.listItem()`).
- **Krytí 0,8, víc než u ostatních skel** (`FLOATING_SUGGESTION_TINT_ALPHA`): inverzní plocha nese
  text opačné světlosti než obsah pod ní. `inverseSurface` je světlejší než černá, takže s 0,66
  (hodnota z tmavého `scrim`) by bílé písmo nad bílou fotkou spadlo pod 4 : 1; s 0,8 vyjde v obou
  motivech nejhůř kolem 6,5 : 1 (výpočet, ne měření).
- **Tlačítko dolů a zmínky (FAB slot) na tu chvíli ustoupí** (`suggestionsFloating`): Scaffold
  je staví nad `bottomBar`, tedy přesně přes plovoucí vrstvu.
- **V rozvržení zůstávají klávesnice bota a návrhy odpovědí.** Klávesnice bota je trvalá
  a zakryla by otázku bota, na kterou se odpovídá; návrhy odpovědí naskočí po příchozí zprávě
  a plovoucí by zakryly právě ji. Uvnitř karty pole (citace, náhled odkazu, lišta formátování)
  zůstává karta jedním celkem a roste.
- **Panely s posledním stavem pro odjezd ho drží ve STAVU** (`mutableStateOf`), ne v poli —
  u příkazů zůstával po „/n" → „/ne" celý seznam (tatáž past jako u emoji).
- **Ověřeno na emulátoru** (Fold složený, chat s botem): příkazy z tlačítka i po „/s" (seznam se
  zúžil, konverzace se nepohnula), ťuknutí poslalo `/stav`, `@gif cat` a „heart" s dosazením
  emoji; inverzní sklo ve světlém i tmavém motivu (`cmd uimode night yes`). Zmínky ověřené nejsou
  (jen ve skupině), iOS jen přeložené.

### Napovídání zmínek

Po `@` ve skupině napovídá nad polem členy (`MentionSuggestionsPanel`, doména `Mentions.kt`:
`mentionQueryAt`, `mentionInsertion`, `nameMentionEntities`, testy `MentionsTest`). Ťuknutí
vloží `@jmeno`, nebo u člena bez veřejného jména jeho křestní jméno se zmínkou (entita
`TextEntityTypeMentionName`).

- **Zmínka se hledá u KURZORU, ne na začátku textu** — píše se kdekoli ve větě. Zavináč musí
  stát na začátku nebo po mezeře („jana@firma" zmínka není) a mezi ním a kurzorem jen písmena
  (i s diakritikou, hledá se i podle jména), číslice a podtržítko. Samotné `@` nabídne členy
  bez filtru. Rozepsaný dotaz na inline bota (`@gif kočka`) má přednost.
- **Kdo jde zmínit, říká server** (`searchChatMembers` s `ChatMembersFilterMention`), ne seznam
  členů — u skupiny se skrytými členy by seznam vydal jen správce. Vlastní účet se vynechá.
- **Jen ve skupině a mimo úpravu zprávy.** V soukromém chatu je koho zmínit jen jeden, v kanálu
  nikdo nepíše a úprava posílá holý text, ze kterého by zmínka jménem zmizela.
- **Zmínka jménem žije ve ViewModelu, ne v poli** (`NameMention`, `nameMentions`). Pole je holý
  text, takže entitu s id uživatele doplní až odeslání (`detectEntities(text, mentions)`):
  jméno se v textu najde jako samostatné slovo mimo entity, které text už má, v pořadí vkládání.
  Smazané nebo přepsané jméno se prostě nenajde. Vyprázdněné pole i odeslání je zapomenou;
  koncept je nenese — po návratu do chatu zbyde jen text.
- **Funguje i v popisku příloh** (`sendAttachments` bere zmínky taky).
- **Ověřeno na emulátoru sondou**: účet na emulátoru v žádné skupině, kam smí psát, není (Kotlin
  Community i Android Developers tam ukazují „Join"), takže se napovídání dočasně povolilo
  v chatu s testovacím botem a vynutila se zmínka jménem. Panel po `@`, vložení jména s mezerou
  a bot přes `getUpdates` dostal `text_mention` se svým id. **Ve skupině ověřené** (testovací
  skupina s botem): po `@` panel s botem i jeho `@jménem`, ťuknutí vloží `@NoTiBoBoBot ` a zpráva
  odejde se zmínkou. **Bot v režimu soukromí zmínku ve skupině NEDOSTANE** — dostává jen příkazy
  `/x@bot`, odpovědi na sebe a servisní zprávy —, takže to přes `getUpdates` ověřit nejde; vidět je
  to na podtržené zmínce v bublině.
- **Id veřejné skupiny zjistí Bot API** (`getChat?chat_id=@jmeno`, bot nemusí být členem)
  a otevře ji `am start … --el eu.tintera.plume.extra.CHAT_ID <id>` — bez ťukání v seznamu
  chatů se skutečnými lidmi.

### Profilovky ve skupině

Ve skupině má cizí zpráva vlevo tvář odesílatele, aby se odesílatelé nerozlišovali jen jménem.

- **Tvář je u POSLEDNÍ zprávy bloku**, ostatní zprávy bloku mají na jejím místě mezeru
  (`SenderAvatar.Spacer`). Bez mezery by bubliny téhož člověka skákaly do stran.
- **Poslední = novější soused je o index NÍŽ** (seznam jde od nejnovější). Blok končí jiným
  odesílatelem, jinou stranou, jiným dnem nebo servisním oznámením.
- **Kanál tvář nemá** (odesílatel = chat sám): u každého příspěvku by byla tatáž a nese ji
  hlavička.
- **A nemá ani jméno nad příspěvkem.** Kanál je pro TDLib taky `ChatTypeSupergroup`, takže
  `ChatInfo.isGroup` je u něj `true` — a jméno kanálu stálo nad každým příspěvkem, zatímco tvář
  schovala jiná podmínka (odesílatel = chat). O jménu i tváři proto rozhoduje `isGroup`
  v repository jako „skupina a NE kanál". Kdo se ptá na „skupinu", musí se rozhodnout, jestli
  kanál myslí taky.
- **Jméno i tvář jdou z jednoho `resolveSender`** (s cache), ne ze dvou dotazů.
- **Tvář je mimo posouvaný `Box`** — při tažení na odpověď jede jen bublina.
- **Zarovnaná ke spodní hraně bubliny.** U dlouhé zprávy by uprostřed nebylo poznat, kam patří.

### Podpis autora a štítek člena

Příspěvek kanálu s podpisy má v řádku pod sebou, před počty, ikonu člověka a jméno autora
(`ChatMessage.authorSignature`, `PostStatsRow`). Ve skupině stojí za jménem odesílatele
ztlumeně jeho štítek (`ChatMessage.senderTag`, `SenderName`).

- **Podpis znamená dvě různé věci podle chatu.** V kanálu je to autor příspěvku a patří do
  řádku pod příspěvkem; ve skupině ho nese anonymní správce (odesílatel = chat) a je to jeho
  titul, takže patří k JMÉNU jako štítek. Rozhoduje převod zprávy podle `isGroup`, ne UI.
- **Štítek člena je `Message.senderTag`** (TDLib 15; „custom title" správce ve stavu člena už
  není, viz „Profil chatu"). Kreslí se jen tam, kde se kreslí jméno — tedy ve skupině, u cizí
  zprávy a na začátku bloku.
- **Jméno i štítek mají váhu BEZ vyplnění** a oba se zkracují: s vyplněním by si dlouhé jméno
  a štítek rozdělily řádek napůl i tam, kde by se vešly celé.
- **Boosty odesílatele** (`Message.senderBoostCount`, `ChatMessage.senderBoostCount`): mezi
  jménem a štítkem blesk v barvě jména, počet až od dvou — „1" by opakovalo, co říká samotný
  blesk (jako počet u čipu reakce). Blesk se nezkracuje; čtečka obrazovky dostane „Boosty
  skupině: N". Jen ve skupině a u cizí zprávy, jako jméno.
- **Řádek pod příspěvkem se kreslí i bez počtů, když je podpis** (prázdné `PostStats`).
- **Podpis u přeposlané zprávy** („Kanál (Autor)") dělá `withSignature` odjinud, z původu.
- **Ověřeno na emulátoru sondou** (necommituje se): zprávy od testovacího bota začínající
  `#podpis` / `#stitek` / `#boost` převod dočasně obohatí o podpis a počty, resp. o jméno se štítkem
  a jiného odesílatele (jinak by zpráva navázala na blok a jméno by se nekreslilo). Skutečný
  kanál s podpisy ani skupina se štítky na testovacím účtu nejsou.

### Kontrola faktů

Příspěvek, ke kterému server přidal poznámku nezávislých ověřovatelů (`Message.factCheck`), má
pod sebou kartu „Kontrola faktů": ikona s nadpisem, text poznámky s odkazy a věta, že ji
doplnili ověřovatelé (se zemí, pro kterou platí). `ChatMessage.factCheck`, `FactCheckCard`.

- **Karta visí pod zprávou, ne v bublině** — přes `WithBelowMessage` (dřív `WithBotKeyboard`),
  stejně jako tlačítka bota, a nad nimi. Pod holou fotkou žádná bublina není, a karta tak
  vypadá stejně u textu, fotky i alba; šířku má aspoň jako zpráva.
- **Terciární role**, jako popis webu u náhledu odkazu: je to cizí vložený obsah, ne slovo
  autora. Věta o ověřovatelích je nutná — bez ní by se poznámka dala číst jako dodatek kanálu.
- **Poznámku server přidává a mění až po zveřejnění** a přijde jen `UpdateMessageFactCheck`;
  `editedMessages` ho proto odebírá vedle úprav obsahu.
- **Přidávat kontroly faktů (`setMessageFactCheck`) smí jen ověřovatelé** — v aplikaci to není.
- **Ověřeno na emulátoru sondou** (necommituje se): zprávy od testovacího bota s `#fakta`
  převod dočasně obohatí o poznámku s odkazem a zemí; karta pod textem i pod fotkou s popiskem.
  Skutečný příspěvek s kontrolou faktů testovací účet nezná.

### Náhled odkazu

Pod textem se zprávou s odkazem se kreslí náhled: web, titulek, popis a obrázek.

- **Náhled skládá server**, ne klient (`MessageText.linkPreview`). Nic se nestahuje kromě
  obrázku, který k němu server přiložil — žádné připojení k cizí stránce z aplikace.
- **Obrázek jen z druhů, které nesou fotku**: článek, fotka, obal videa, náhled vloženého
  přehrávače. Ostatní druhy (samolepka, hlasovka, chat…) mají soubory, které neumíme vykreslit;
  náhled zůstane textový. **Úplně prázdný náhled se zahodí** — rámeček bez obsahu vypadá jako
  chyba načítání.
- **Velký nebo malý obrázek určuje odesílatel** (`showLargeMedia`), jen se to respektuje.
- **Vzhled: karta ze dvou částí** (viz „Náhled odkazu jako karta"), v bublině tónovaný
  kontejner jako citace. Akcent se podává zvenku ze stejného důvodu jako u citace — v odchozí
  `primary` bublině by `primary` zmizela.
- **Ťuknutí jde přes `onLinkClick`** jako odkaz v textu; **dlouhý stisk se opakuje**, jinak by
  náhled nabídku nad zprávou spolkl.
- **Náhled nad textem** (`showAboveText`) je přání odesílatele, jen se respektuje — jako velikost
  obrázku. S ním se otáčí i to, který díl karty je poslední: **spodní odsazení nese ten, pod kterým
  už nic není** (počítá se v něm s místem na čipy reakcí). **Značka přeposlání zůstává nahoře
  vždycky** — je to údaj o zprávě, ne o odkazu, a pod náhledem by vypadala jako jeho popisek.
- **Náhled jde z bubliny ven do vlastní karty** — viz „Náhled odkazu jako karta".

### Nabídka nad zprávou

Dlouhý stisk otevře **nabídku u zprávy**, ne bottom sheet: rychlé reakce v pruhu nad bublinou
a nabídka akcí pod ní, zbytek obrazovky pod závojem. Sheet dole utrhl pozornost od zprávy,
které se týkal, a unesl jen pár položek — akcí bude postupně deset.

- **Vrstva v okně obrazovky, ne `Popup`** (`MessageMenuLayer`, nad Scaffoldem konverzace).
  `Popup` je na Androidu vlastní okno: kopii zprávy do něj šlo dostat jen jako snímek (samolepka
  stála, zpátky se skákalo), závoj potřeboval díru přes `BlendMode.Clear`, počátek okna se musel
  měřit a plný výběr reakcí byl vlastní napodobenina sheetu. Ve stejném okně nic z toho není třeba.
- **Zpět obsluhuje `NavigationBackHandler`** (`androidx.navigationevent`), ne `BackHandler` —
  ten by zabil predictive back. Při rozbaleném výběru reakcí je vypnutý, zpět si vezme sheet.
- **Zpráva se v nabídce složí znovu, živá** (`MenuCopy`: tentýž `MessageBox` a `MessageBodyScope`
  jako v seznamu, stejná šířka, bez gest).
- **Kopie dostává stejné OMEZENÍ šířky jako zpráva v seznamu, ne šířku zprávy napevno**
  (`MenuAnchor.maxWidth`, zapisuje ho rám v `layout`, a stejný `maxWidthFraction`). S šířkou
  zprávy jako stropem vyšlo jinak všechno, co se řídí dostupnou šířkou: fotka s popiskem využívá
  plnou dostupnou šířku, v kopii by se při pevném oříznutí chovala jinak. V seznamu se zprůhlední (`isInMenu`) a drží místo —
  po ní zůstane prázdno, jako v Google Zprávách.
- **Let tam i zpátky je ruční posun kopie (`Animatable`), NE sdílený prvek.** Sdílený prvek se během
  přechodu kreslí v překryvu nad celou aplikací, mimo ořezy rodičů, takže zpráva začínající pod
  hlavičkou chatu přeletěla PŘES hlavičku. `clipInOverlayDuringTransition` se volal, ale na
  výsledku nebylo nic vidět, a `renderInOverlayDuringTransition = false` kreslil stejně. Kopie je
  ve stejném okně jako seznam, takže sdílený prvek by tu dělal jen tentýž posun — ručně ho jde
  oříznout pod hlavičkou (`clipRect` na ploše kopie).
- **Vrstva po zavření ještě chvíli žije** (`lingering`): kopie letí zpátky na místo zprávy a teprve
  po doběhnutí vrstva zmizí a zpráva v seznamu se znovu ukáže. Zmizet hned, neměla by kopie
  odkud letět.
- **Odkud zpráva vyjíždí, hlásí zpráva sama při dlouhém stisku** (`onMenuAnchor`, souřadnice v okně).
  Rám drží `LayoutCoordinates` v obyčejném poli, ne ve stavu — zápis stavu při každém layoutu
  by položku rekomponoval.
- **Zpráva se do pohledu VYSUNE, nabídka je VŽDY POD NÍ** (`placeMenuMessage`, testy
  `MenuMessagePlacementTest`). Zpráva zasahující pod hlavičku nebo pod pole vyjede tak, aby nad
  ní byl pruh reakcí a pod ní nabídka. **Vysoká zpráva se zmenší** (nejvýš na `MIN_MESSAGE_SCALE`
  0,6, menší text nejde číst), a **když se nevejde ani tak, ořízne se** a dole vybledne — vidět je
  začátek, podle kterého se pozná; celý obsah je v seznamu. Dokud se vysoká zpráva jen vysunula,
  nabídka se nevešla pod ni ani nad ni a skončila PŘES její začátek i přes pruh reakcí.
- **Let, zmenšení i ořez jdou z jednoho průběhu 0 → 1** (`progress`), takže se zpráva zmenšuje
  a ořezává plynule a zpátky zase dorůstá. Zmenšuje se k té straně, u které stojí, a nahoře
  zarovnaná; ořez je rámeček s výškou viditelné části, uvnitř zpráva v plné výšce.
- **Plný výběr reakcí je STANDARDNÍ `ModalBottomSheet`** (`ReactionPickerSheet`) bez vlastního
  závoje (`scrimColor = Transparent`) — ztmavení už dělá nabídka pod ním a pruh reakcí i zpráva
  nad sheetem musí zůstat vidět. Bez napůl rozbaleného stavu a s mřížkou pevné výšky, jako výběr
  samolepek. Ve starém sheetu nešlo ťuknout na emoji (podezřelý byl `FlowRow` bez scrollování
  a napůl rozbalený stav); tady ťuknutí funguje, ověřeno na zařízení.
- **Závoj rozostřuje, neztmavuje** (Haze, `hazeSource` na Scaffoldu konverzace, `hazeEffect`
  na závoji). Compose sám umí rozostřit jen VLASTNÍ obsah (`Modifier.blur`, i s přechodem od
  1.13); rozostřit to, co leží POD vrstvou, umí Android až od 17 QPR2
  (`RenderNode.setBackdropRenderEffect`). Haze obsah pod vrstvou zaznamená do vlastní grafické
  vrstvy a vykreslí rozostřený, na Androidu 12+ i na iOS. **Jde to jen proto, že je nabídka
  ve stejném okně** — z `Popup` (vlastní okno) by Haze na Scaffold nedosáhl.
- **Poloměr 12 dp, ne výchozích 20–24.** Při 24 dp je z konverzace kaše a nepozná se ani
  hlavička, a ta má zůstat rozpoznatelná (viz horní mez nabídky). Rozostření odsouvá do pozadí
  samo, takže ztmavení kleslo ze 45 % na 20 %. **Kde rozostření není (Android 11 a starší),
  kreslí Haze `fallbackTint`**, a ten je nastavený na dřívější závoj — o nic se nepřijde.
- **Závoj se objevuje průhledností, poloměr se neanimuje.** Změna poloměru znamená nový
  `RenderEffect` v každém snímku; prolnutí ostrého a rozostřeného obrazu vypadá skoro stejně
  a nic nestojí. **Průhlednost jde přes `graphicsLayer`, ne přes `alpha(…)` ve stylu:** styl je
  v Haze 2 neměnný, takže by se v každém snímku animace musel skládat znovu — a čtení animované
  hodnoty v kompozici by rekomponovalo celou vrstvu nabídky.
- **Haze 2 (`haze` + `haze-blur`, vždy stejná verze), vstup `HazeInput.Sources`.** Dokumentace
  doporučuje `Backdrop`, ale ten bez experimentálního příznaku stejně spadne na `Sources`
  a s ním by se na Androidu 17 QPR2 tiše přepnul na nativní cestu, podle autora zatím dražší.
  Dvojka nezaznamenává zdroj, dokud ho žádný efekt nečte — `hazeSource` na Scaffoldu tedy se
  zavřenou nabídkou nic nestojí (v 1.x se konverzace kreslila do vrstvy pořád).
  **Kotlin projektu musí být ≥ tomu, ze kterého je Haze publikovaná** (2.0 → 2.4.20) — stejná
  podmínka jako u TDLib wrapperu. Neimportuje se `haze-blur-materials`: styl je pár řádků
  (`scrimStyle`) a modul by táhl material3 v jiné verzi.
- **Nabídka akcí před sheetem NEMIZÍ.** Sheet ji jen překryje a klikat se na ni nedá, protože je
  modální; je to pořád táž otevřená nabídka. Dřív se schovávala (`if (!expanded)`), zmizela
  natvrdo a po zasunutí sheetu bleskově naskočila.
- **Spodní mez je horní hrana vstupního pole, ne navigační lišta** (`contentBottom`, obrazovka ho
  měří na `bottomBar`). Pole leží přes seznam, takže zpráva vysunutá jen nad navigační lištu
  skončila pod polem. Kopie zprávy se ořezává mezi hlavičkou a polem, aby přes ně nepřeletěla.
- **Otevření nabídky schová klávesnici** (`LocalSoftwareKeyboardController.hide()`, fokus pole zůstává).
  S klávesnicí se na menším telefonu mezi hlavičku a pole nevešla ani samotná nabídka — a zpráva
  vyšla se zápornou výškou: `Size(804 x -2) is out of range`, pád aplikace. Tak to dělají i Telegram
  a Google Zprávy. **Na velkém emulátoru se to nereprodukuje**, místa je dost; ověřuje se na
  zmenšeném displeji (`adb shell wm size 1080x1700`, pak `wm size reset`).
- **Poloha zprávy je FUNKCE čtená každý snímek** (`onMenuAnchor(() -> Rect?)`), ne hodnota
  z okamžiku stisku. Když zmizí klávesnice, seznam se posune — nabídka musí vyjet i dosednout
  tam, kde zpráva je teď.
- **V těsném okně zůstane zpráva vidět** aspoň `MIN_MESSAGE_VISIBLE` (72 dp) a nabídka dostane
  strop výšky a posouvá se. Nabídka smí sahat až k navigační liště (vstupní pole je pod
  závojem), zpráva jen nad pole. Výšky se ve vrstvě navíc ořezávají na nulu — pružina přestřelí.
- **Horní mez je spodek hlavičky chatu, ne stavová lišta** (`contentTop` ze
  `ConversationItemScope`, obrazovka ho měří na seznamu). Vysunutá zpráva ani pruh reakcí nesmí
  hlavičku překrýt — ztratilo by se povědomí, s kým je chat otevřený. Google Zprávy to dělají stejně.
- **Rozvržení nabídky se počítá z CÍLOVÉ polohy zprávy, ne z té, kde právě je.** Dokud se
  počítalo z aktuální polohy, pruh reakcí se v prvních snímcích (než je snímek zprávy hotový)
  nevešel nad zprávu, šel pod ni a pak přeskočil nahoru. Teď se o umístění rozhodne jednou
  a pruh i nabídka dojedou se zprávou (`travel`).
- **Past: `Modifier.clip(shape)` musí být PŘED `combinedClickable`.** `Surface` si tvarem ořezává
  jen svoje pozadí, a to až za modifikátory, které dostane zvenku — vlnka dlouhého stisku se tedy
  kreslila do obdélníku a přetékala přes zaoblená nároží bubliny.
- **Bez pruhu reakcí se pruh nepřesouvá** (`placeMenu`). Podmínka „pruh se nevejde nad zprávu“
  platila i s nulovou výškou pruhu, když zpráva stála u horní meze — tedy u každé vysoké zprávy
  zmenšené k horní mezi v chatu bez reakcí. Nabídka se tím posunula o mezeru níž, pod zprávu se
  nevešla, odskočila nad ni a ořízla se k horní mezi: přes zprávu. Test
  `bezPruhuReakciJeNabidkaPodZmensenouZpravou` prochází zprávy výšku po výšce.
- **Výšky obou kusů jsou konstanty, ne měření.** Kreslíme si je sami, takže je známe předem —
  ušetří se tím snímek, ve kterém by nabídka poskočila na správné místo.
- **Výška nabídky se počítá z UKÁZANÝCH položek, ne z kandidátů.** Blok nabízí kandidáty a server
  z nich část zakáže; s počtem kandidátů visela nabídka nad zprávou o 48 dp výš za každého
  zakázaného. Položky se proto sestaví jednou a z nich se počítá výška i kreslí nabídka.
- **Nabídka se zarovnává k hraně BUBLINY, pruh reakcí ke kraji obrazovky.** Nabídka má pevnou
  šířku, takže může vyrůstat od zprávy; pruh se šesti emoji je tak široký, že by se k bublině
  stejně nevešel.
- **Příznaky „co se smí" se načítají až při otevření nabídky.** TDLib je ve zprávě neposílá,
  jsou za samostatným `getMessageProperties` — načítat je dopředu by znamenalo dotaz na každou
  zprávu v historii kvůli nabídce, kterou uživatel skoro nikdy neotevře. Načítají se **současně**
  s nabídkou emoji, ne sériově.
- **Co server nedovolí, se nenabízí.** Položka, která skončí chybou, je horší než položka, která
  tam není. Když se příznaky nepodaří zjistit, nabídne se odpověď a kopírování (neškodné),
  mazání ne.
- **Kopírování se řídí `canBeSaved`.** Kanál si může obsah chránit; pak se z něj nesmí vynášet
  ani text do schránky.
- **Výběr části textu je SHEET z nabídky, ne výběr v bublině** (`SelectTextSheet`).
  `SelectionContainer` zahajuje výběr dlouhým stiskem, a ten v konverzaci už patří nabídce nad
  zprávou; kdyby se o gesto dělily, jedna by přestala fungovat — a nabídka je ta častější.
  Text v sheetu je holý (`copyableText`), ne `RichText`: odkazy mají vlastní klikání a se
  `SelectionContainer` by se o gesta praly. Kopírování obstará systémová nabídka nad výběrem,
  vlastní tlačítko by jen zdvojovalo, co platforma umí sama.
- **„Vybrat text" stojí na stejném právu jako „Kopírovat"** (`canCopy`). Chráněný obsah nesmí
  ven ani po kouskách a bez textu není co vybírat; obojí hlídá test.

#### Citace z nabídky

Text **kopie zprávy v nabídce jde označit** (dlouhým stiskem na slovo, pak úchyty). S označeným
úryvkem se „Odpovědět" přejmenuje na **„Citovat"** a odpověď pak míří jen na úryvek
(`InputMessageReplyToMessage.quote`); „Kopírovat" zkopíruje jen úryvek. Tak to dělá i Telegram
na iOS. Dlouhý stisk v SEZNAMU dál otevírá nabídku — o gesto se nedělí, výběr začíná až v kopii.

- **Sledovat výběr jde veřejným API**: Compose 1.12 má `SelectionContainer(state = SelectionState)`
  a `SelectionState.selectedTexts` je snapshot stav, takže nabídka se přejmenuje hned při tažení
  úchytu (`derivedStateOf`). Ověřeno `javap` nad `foundation-desktop-1.12.0`, bez opt-inu.
- **Položka se PŘEJMENUJE, nepřibývá** (`MessageMenuAction.presentation(menu, selection)`,
  `withSelection`). Výška nabídky se počítá z ukázaných položek, takže položka navíc by nabídku
  pod prstem natáhla; zašedlé položky jinde v nabídce nejsou.
- **Citovat jde jen text TÉHLE zprávy** (`quoteSelection`, testy `QuoteSelectionTest`). Kopie
  nese i jméno odesílatele, značku přeposlání a hlavičku citace nad odpovědí, a výběr přes ně
  vrací víc kusů; bere se nejdelší kus, který v textu zprávy je. Označený úryvek z hlavičky
  citace (text JINÉ zprávy) nechá „Odpovědět" být.
- **Kopie dostává scope, který akce zahazuje** (`InertItemScope`). S označitelným textem na ni
  jdou dotyky, a ťuknutí na fotku, odkaz nebo citaci by z nabídky otevřelo prohlížeč, web nebo skok.
- **Zavírání ťuknutím se NESMÍ ptát na spotřebování.** `SelectionContainer` si ťuknutí bere i bez
  výběru, takže s `waitForUpOrCancellation` nešla nabídka zavřít ťuknutím na zprávu (ověřeno na
  emulátoru). Rozhoduje jen: byl výběr při dotyku, jak dlouho prst ležel, jestli ujel.
- **Během označování pruh reakcí zmizí.** Systémová lišta výběru (Kopírovat, Vybrat vše, Přeložit)
  se kreslí nad označeným textem, tedy přesně přes pruh reakcí.
- **Označovat jde jen s `canCopy`** — chráněný obsah nesmí ven ani citací přes úryvek.
- **Barvy výběru podává plocha bubliny, ne `SelectionContainer`** (`BubbleSurface`,
  `MessageBodyScope.selectionColors`). Výchozí podklad výběru je `primary` s alfou 0,4 — na
  odchozí `primary` bublině tedy barva na barvě a označený úsek nebyl vidět vůbec. Podklad si
  `BasicText` čte z `LocalTextSelectionColors` SÁM, v místě textu, takže ho jde podat až na
  ploše, kde text leží. Pravidlo je tatáž `accentOn` jako u akcentu: `primary`, dokud je na
  bublině vidět, jinak barva popředí bubliny.
- **Úchyty výběru nastavuje nabídka, kolem `SelectionContainer`** — čte je kontejner, ne text.
  Visí přes spodní okraj bubliny, takže se musí odlišit od bubliny I od plochy konverzace
  (`distinctOnBoth`, testy `AccentColorTest`): `primary`, dokud má proti oběma aspoň 3 : 1
  (WCAG pro netextové prvky), jinak z `inversePrimary` / `onSurface` ta s lepším horším
  kontrastem. Pevná náhrada nevyjde v obou režimech: u odchozí zprávy vyhraje v tmavém
  `inversePrimary` (bublina světlá, plocha tmavá), ve světlém `onSurface`. Barvu bubliny
  sdílí nabídka s rámem přes `bubbleColorFor`. **Každá nová plocha v barvě bubliny jde přes
  `BubbleSurface`**, ne přes holý `Surface(color = bubbleColor)` — jinak na ní výběr zmizí znovu.
- **Formátování úryvku se MUSÍ přenést** (`quoteInput`, testy `ReplyQuoteTest`). TDLib: tučné,
  kurzíva, podtržení, přeškrtnutí, spoiler a vlastní emoji „must be kept in the quote". Repository
  si proto přečte původní zprávu (`getMessage`, lokální), entity ořízne na úryvek a posune.
  Odkazy, zmínky a kód do citace nepatří a odpadnou.
- **`position` je jen odhad** („approximate" v dokumentaci TDLib); repository si místo dohledá
  kolem něj, pak kdekoli. Úryvek, který v textu není, znamená odpověď na celou zprávu, ne chybu.
- **Strop 1024 znaků** (`MAX_QUOTE_LENGTH`, server `message_reply_quote_length_max`): delší výběr
  se zkrátí na začátek, i ten je platný úryvek.
- **Ověřeno na emulátoru (Fold, složený) i na serveru**: Bot API u odeslané zprávy vrátilo
  `quote: {text, position: 24, is_manual: true}`; odpověď bota s `reply_parameters.quote` se
  nakreslila s úryvkem; koncept s citací přežil odchod z chatu.
- **Schránka je vlastní `expect`/`actual`, ne Compose API.** `LocalClipboardManager` je zastaralý
  a jeho náhrada pracuje s `ClipEntry`, což je `expect class` bez společné továrny na text —
  v commonMain se prostě nedá vyrobit.
- **Prázdná nabídka emoji není chyba.** V Uložených zprávách Telegram místo reakcí používá tagy;
  dřív se kvůli tomu nabídka neotevřela vůbec a nešlo ani odpovědět. Teď se jen nevykreslí pruh.
- **Rozhoduje HIERARCHIE, ne to, kolik se toho má vejít.** Položka nabídky je jednorázová akce:
  nabídku ukončí a co si otevře, je samostatná věc — „Informace" je modální sheet a funguje
  správně, včetně zkopírování odkazu. Plný výběr reakcí je proti tomu **podnabídka tlačítka „+"**
  v pruhu rychlých reakcí, takže patří DOVNITŘ plovoucí nabídky. Sheet nad ní z podnabídky dělal
  něco, co ukončí nabídku, ke které pořád patří.
- **Nad sheetem zůstává pruh rychlých reakcí i vybraná zpráva** (nabídka akcí pod sheetem viz výš).
  Z pruhu je pořád vidět, čeho se výběr týká a co je už použité. Pruh zůstane
  u zprávy, dokud se nad sheet vejde, jinak se posadí těsně nad něj — a posun se animuje, aby to
  nevypadalo jako přeskládání. Rozvržení počítá `reactionSheetLayout` (testy `ReactionSheetTest`),
  čistá funkce mimo composable jako `photoFrame`: samý hraniční případ (zpráva dole, nahoře,
  nízké i vysoké okno). Počítá se i se sbaleným sheetem — při odjezdu `expanded` už neplatí
  a výška by mu pod rukama skočila na podlahu.
- **Smazaná zpráva se ze seznamu neodstraňuje ručně.** TDLib pošle `UpdateDeleteMessages` a ten se
  už zpracovává; předběhnout server by znamenalo riskovat, že zpráva zmizí z obrazovky a na
  serveru zůstane.

### Překlad zprávy

„Přeložit" v nabídce nad zprávou otevře sheet s překladem do jazyka aplikace
(`translateMessageText`, `TranslationSheet`, `MessageMenuAction.Translate`).

- **Sheet, ne náhrada textu v bublině**, jako u oficiálního Android klienta. Překlad je
  jednorázové nahlédnutí; v bublině by se musel pamatovat u každé zprávy a přežít úpravu.
- **Stojí na témže právu jako kopírování** (`canCopy`): bez textu není co překládat a chráněný
  obsah ven nesmí ani jinou řečí. Anketa ho nemá (její text je náš popis).
- **Výchozí cílový jazyk je `platformLanguageCode`** — tentýž zdroj jako jazyk pro TDLib, tedy
  i jazyk nastavený jen pro aplikaci. Název jazyka dává systém (`localizedLanguageName`, jako
  `localizedCountryName`).
- **Jiný jazyk jde vybrat ťuknutím na „Do jazyka: …"** (`TranslationLanguageSheet`, seznam
  a řazení `translationLanguageOptions`, testy `TranslationLanguagesTest`) a platí pro celou
  aplikaci (`AppSettings.translationLanguage`). **Jazyk aplikace se neukládá kódem, ale jako
  `null`**: po přepnutí jazyka aplikace se pak překládá do nového, ne do toho, který v ní byl
  kdysi. Nahoře seznamu je jazyk aplikace, pak ostatní abecedně; hledá se bez diakritiky.
- **Výběr je sheet NAD sheetem překladu**, jako detail sady nad výběrem samolepek: gesto zpět
  zavře jen výběr. Obsah vyměněný uvnitř jednoho sheetu by zpět zavřelo celé.
- **Seznam jazyků drží aplikace, TDLib ho lokálně nekontroluje** (v knihovně žádný není) —
  je z dokumentace `translateText`, bez aliasů (`iw`, `in`, `ji`); Java u hebrejštiny
  a indonéštiny vrací právě ty zastaralé kódy, proto `normalizedLanguageCode`.
- **Čínština podle regionu (`zh-CN`, `zh-TW`), ne podle písma.** `getDisplayName` pojmenuje
  `zh-Hant` „Čínština (han (tradiční))"; `zh-TW` je „Čínština (Tchaj-wan)" a server ho bere
  (ověřeno: přišla tradiční znaková sada). `localizedLanguageName` proto bere celý kód i s regionem
  (`getDisplayName`, na iOS `NSLocaleIdentifier`), ne jen jazyk.
- **`translateMessageText` má v TDLib 15 čtvrtý parametr `tone`**; prázdný = výchozí.
  Bez Premium přijde prostý text, s ním i formátování.
- **Odkazy, zmínky a hashtagy se v překladu nekreslí jako klikací** — sheet nemá konverzaci,
  kam by vedly. Zůstane jen formátování.
- **Kopírování sheet zavře a potvrdí snackbarem** — pod modálním sheetem by vidět nebyl.
- **Server překládá doslova i značky**: literální `**tučně**` v anglické zprávě vrátil jako
  `<b>tučně</b>`. Je to vlastnost překladu, ne chyba vykreslení.
- **Ověřeno na emulátoru** (aplikace přepnutá do češtiny, anglická zpráva od testovacího bota):
  položka v nabídce, načítání, překlad, kopírování se snackbarem; výběr jazyka s hledáním
  („cin"), překlad do čínštiny (Tchaj-wan), zapamatování po znovuotevření, zpět zavře jen výběr,
  a po volbě češtiny a přepnutí aplikace do angličtiny „To English". **iOS jen přeložené.**
- **Zpráva, která už JE v jazyce překladu, „Přeložit" nemá** (`MessageMenu.inTranslationLanguage`,
  pravidla `alreadyInLanguage` a `languageDetectionText`, testy `AlreadyInLanguageTest`). Jazyk
  rozpoznává zařízení (`LanguageDetector` v `core/language`: Android `TextClassifier.detectLanguage`
  od Androidu 10, iOS `NLLanguageRecognizer`); nic se neposílá a nic se nestahuje. Starší Android
  rozpoznávat neumí a položku nabízí vždycky.
  - **Rozpoznává se při otevření nabídky, souběžně s právy**, se stropem 300 ms — nabídka čeká na
    všechny úlohy a pomalý první start modelu ji nesmí zdržet. Nestihnuté = nabídnout.
  - **Chyba směrem „schovat" je horší než „nabídnout"**, proto: krátký text (pod deset písmen —
    „ok", „Zítra?") se nerozpoznává vůbec, a čínština se neschovává nikdy (cíl je písmo
    `zh-CN` / `zh-TW`, rozpoznání ho nerozliší).
  - **Práh jistoty je 0,7, ne 0,8** (`MIN_LANGUAGE_CONFIDENCE`). Změřeno na emulátoru: „Dneska je
    krásné počasí, pojďme se projít do parku." dostala češtinu jen 0,76 (zbytek slovenština),
    přitom slovenskou a polskou větu model pozná na 0,99 a češtinu u jiných vět na 0,998.
    Hypotézy se dají vypsat dočasným logem v `AndroidLanguageDetector` (`getLocale(i)`,
    `getConfidenceScore`); necommituje se.
  - **Ověřeno na emulátoru** (aplikace česky): dvě české věty bez „Přeložit", slovenská, polská,
    anglická a krátké „Zítra?" s ním. **iOS jen přeložené.**
  - **Pozor při ověřování přes `adb`**: zpět po zavřené nabídce odejde z konverzace a další dlouhé
    stisky trefí řádky seznamu chatů (se skutečnými lidmi). Před zpět ověřit dumpem, že je nabídka
    otevřená (text „Odpovědět").
- **Dlouhý stisk přes `adb` podle polohy z dumpu se může trefit do odkazu**, když se seznam mezi
  dumpem a stiskem posune — pak se místo nabídky otevře prohlížeč. Poloha se bere těsně před
  stiskem a mimo řádek s odkazem.

### Shrnutí od serveru

„Shrnout" v nabídce nad dlouhou zprávou otevře tentýž sheet jako překlad, jen s jinými texty
(`TranslationSheet.summary`, `MessageMenuAction.Summarize`, `summarizeMessage`). Je to jiná věc
než „Shrnout" v hlavičce konverzace (model v zařízení, viz „AI v zařízení") — tohle shrnuje
JEDNU zprávu a počítá to Telegram.

- **Kdy se nabídne, rozhoduje server** (`Message.summaryLanguageCode` → `ChatMessage.summaryLanguage`,
  prázdný = shrnout nejde). Aplikace délku zprávy neposuzuje; v kanálu s krátkými hlášeními ho
  měl jediný dlouhý příspěvek.
- **Nabízí se i u chráněného obsahu**, na rozdíl od překladu — dostupnost řídí server. Jen se
  tam výsledek nedá zkopírovat (`TranslationSheet.canCopy`): chráněný obsah ven nesmí ani
  převyprávěný. Testovací kanál GFN Catalog chráněný je, a „Shrnout" se tam napřed neukázalo,
  protože stálo na `canCopy` jako překlad.
- **Jazyk je tentýž jako u překladu** (`AppSettings.translationLanguage`, výběr ve sheetu ho
  mění pro obojí). Když se shoduje s jazykem zprávy, posílá se prázdný — server by jinak
  „překládal" sám do sebe.
- **Bez Premium je shrnutí omezené**: `SUMMARY_FLOOD_PREMIUM` má vlastní větu v `userMessage`.
  Sheet proto nese důvod (`TranslationSheet.error: UiText?`), ne jen příznak selhání — platí to
  i pro překlad. **Testovací účet Premium nemá a chybu dostal hned u prvního pokusu**, takže
  samotné shrnutí (text, změna jazyka, kopírování) ověřené NENÍ.
- **Pod shrnutím je věta, že ho psal Telegram a může něco vynechat** — text nepíše odesílatel.

### Automatické mazání zpráv

Chat může mít nastavené, že nové zprávy po době samy zmizí (`Chat.messageAutoDeleteTime`).
Hlavička konverzace to ukazuje odznakem s hodinami za názvem (`AutoDeleteBadge`, „⏱ 1 d"),
„Informace" u zprávy říkají, kdy se smaže (`ChatMessage.autoDeleteAt`), a v profilu chatu je
řádek s výběrem doby (`AutoDeleteSheet`, `setChatMessageAutoDeleteTime`).

- **Doba je živá** (`autoDeleteTime` nad `UpdateChatMessageAutoDeleteTime`, odběr před prvním
  čtením) — mění ji i protějšek nebo jiné zařízení.
- **`autoDeleteIn` je zbývající čas, převádí se na okamžik** při převodu zprávy. Zbývající
  sekundy by se počítaly od doby načtení a v „Informace" by lhaly.
- **Budoucí čas jde přes `asUpcomingTime`**, ne `asDayLabel` (ten je na minulost).
- **Kdo smí měnit**: soukromý chat (i s botem) ano, Uložené zprávy a tajný chat ne (tajný má
  vlastní časovač zpráv), skupina a kanál jen s právem měnit údaje chatu (`canChangeInfo`),
  jak to vyžaduje TDLib. Kdo měnit nesmí, vidí řádek jen při zapnutém mazání, bez klikání.
- **Nabízí se vypnuto, den, týden a měsíc** — jako oficiální klienti. **Měsíc je 31 dní**
  (2 678 400 s), tak ho Telegram posílá; jiná doba nastavená jinde se do výběru zařadí
  (`autoDeleteOptions`, testy `AutoDeleteOptionsTest`).
- **Délka se píše jedním formátovačem** (`durationText` / `durationString` / `durationShortText`
  v `core/ui/Durations.kt`, testy `DurationUnitTest`), sdíleným s dobou ankety. Servisní zpráva
  proto říká i dobu („Automatické mazání zpráv: 1 den"), dřív jen „Zprávy se budou samy mazat".
- **Věta s „po" by v češtině chtěla 6. pád** („po 1 dni"), plurály jsou ale v 1. pádu — texty
  jsou proto s dvojtečkou („…: 1 den").
- **Výchozí doba pro NOVÉ chaty je v Nastavení → Obecné → Soukromí** (`DefaultAutoDeleteSettings`
  nad `get`/`setDefaultMessageAutoDeleteTime`, `DefaultAutoDeleteViewModel`, testy
  `DefaultAutoDeleteViewModelTest`). Tentýž `AutoDeleteSheet` jako v profilu, jen s větou, že se
  týká chatů založených odteď — existující chaty nemění, a bez té věty by se to dalo čekat. Je
  to nastavení ÚČTU jako citlivý obsah: volba se pohne hned, po selhání se vrátí s větou pod
  řádkem, a dokud doba nedorazí, řádek se nekreslí. Stejná volba se nezapisuje. **Zápis mění
  skutečný účet, takže se ověřuje jen čtení a otevření výběru** (zavřít gestem zpět).
- **Ověřeno na emulátoru** v chatu s testovacím botem (česky): řádek v profilu, sheet, nastavení
  1 dne (odznak v hlavičce, servisní zpráva s dobou), „Smaže se: zítra 20:54" u nové zprávy
  a vypnutí (odznak zmizel, servisní zpráva „vypnuto"). **Ve skupině ověřené** (testovací
  skupina, vlastník): nastavení i vypnutí s odznakem a servisními zprávami. Kanál a správce bez
  vlastnictví ověřené nejsou, **iOS jen přeložené**.

### Výběr víc zpráv

Položka „Vybrat" v nabídce nad zprávou přepne konverzaci do režimu výběru: hlavička ukazuje ✕
a počet, místo vstupního pole je plovoucí toolbar s akcemi (kopírovat, přeposlat, smazat) a každá
zpráva má na volné straně kolečko. Stav je `ConversationState.selection` (`MessageSelection`,
čistá data a odvozená práva, testy `MessageSelectionTest`); `null` = režim neběží.

- **Vybírají se BLOKY seznamu, ne zprávy** (`MessageSelection.keys` jsou klíče položek). Album je
  v konverzaci jedna věc a přeposlat nebo smazat půlku alba nedává smysl; na id zpráv se klíče
  rozbalí až při akci (`selectedMessageIds`), stejně jako u nabídky nad jednou zprávou.
- **Klíč bloku je klíč jeho NEJSTARŠÍ zprávy** (`blockKeyOf`), takže ťuknutí na kteroukoli fotku
  alba vybere celé album. Ťuknutá fotka sama klíč bloku nenese.
- **Vstupem je položka nabídky, ne rovnou dlouhý stisk.** Dlouhý stisk už patří nabídce a ta je
  ta častější; zároveň by z něj nešlo poznat, že se vybírá. „Vybrat" se přitom nabízí VŽDY,
  jako „Informace" — na právu od serveru nestojí a co výběr dovolí, se ukáže až na jeho liště.
- **Práva se načítají pro každý vybraný blok zvlášť** (`getMessageProperties` při přidání do
  výběru) a skládají se přes `all`, ne `any`: akce platí pro CELÝ výběr, takže přeposlat půlku
  vybraného a o druhé mlčet by znamenalo, že uživatel neví, co odešlo. Dokud nedorazí všechna,
  jsou akce zašedlé.
- **Tlačítka v toolbaru NEMIZÍ, jen zšednou.** Práva chodí po jednom, takže by se toolbar při
  každém ťuknutí přeskládal pod prstem — a zmizelá akce neřekne, že ji zrovna tenhle výběr
  nedovoluje. Výjimka z pravidla „co server nedovolí, se nenabízí": tam jde o nabídku, která se
  otevře hotová, tady o lištu, která žije s měnícím se výběrem.
- **Ťuknutí bere PŘEKRYV nad celým řádkem**, ne `onTap` bubliny. Potomci s vlastním klikáním
  (fotka, GIF, odkaz, citace, náhled odkazu, samolepka) si ťuknutí berou sami a k rámu ho
  nepustí — bez překryvu by ťuknutí na fotku v režimu výběru otevřelo prohlížeč. Překryv bere
  i dlouhý stisk (taky přepíná výběr) a **tah na odpověď se v režimu výběru vypíná úplně**:
  překryvu by propadl a založil odpověď uprostřed vybírání.
- **Vybraná zpráva má pruh přes celou šířku, ne obarvenou bublinu.** Odchozí bublina je sytá
  `primary` a barvu by nenesla, a zpráva bez bubliny (fotka, samolepka) nemá co obarvit.
  `secondaryContainer` je tatáž role jako vybraná položka v seznamu chatů — výběr je v M3
  selekční stav, ne zvýraznění obsahu. Průsvitný (`SELECTION_TINT_ALPHA`), aby text zůstal
  čitelný, a zahrnuje i mezeru nad zprávou, takže z vybraných sousedů vznikne souvislý blok.
- **Kolečko stojí na VOLNÉ straně zprávy** — u odchozí vlevo, u příchozí vpravo. Volný pruh tam
  je vždycky (zpráva zabere nejvýš `MESSAGE_MAX_WIDTH_FRACTION` řádku), takže se kvůli němu nic
  neposouvá; Telegram místo toho obsah odsouvá a kolečko vsouvá zleva. Samotné podbarvení
  nestačí: u jediné vybrané zprávy by z něj nebylo poznat, že jdou vybírat i další.
- **`state.selection` MUSÍ být v klíčích `remember` nad builderem bloků**, jinak se seznam
  nepřestaví a zaškrtnutí se neobjeví. Chytlo se to až na zařízení: první zpráva se označila
  (vstup do výběru zároveň zavřel nabídku, a `messageMenu` v klíčích je), další už ne. V klíčích
  jsou jen KLÍČE výběru — práva k němu chodí po jednom a seznam bloků se jimi nemění.
- **Odebrání poslední vybrané režim ukončí.** Zůstat v něm s nulou vybraných je stav, ve kterém
  nejde nic dělat a není jasné proč.
- **Zpět ukončí výběr**, teprve další odejde z chatu — `NavigationBackHandler`, jako u hledání.
- **Strop je sto zpráv** (`MAX_SELECTED_MESSAGES`). `deleteMessages` i `forwardMessages` berou
  v TDLib nejvýš sto naráz, takže vyšší výběr by skončil chybou až u akce.
- **Kopíruje se chronologicky, s prázdným řádkem mezi zprávami.** Zprávy bez textu se vynechají;
  popisek alba nemusí být na ťuknuté fotce, takže se text hledá v celém bloku.
- **Tělo potvrzení mazání má vlastní věty pro víc zpráv** (`delete_messages_*`), ne plurál nad
  jednou větou: čeština i angličtina v nich mění víc než jedno slovo. Titulek plurál má
  (`conversation_delete_selected_title`), protože nese počet.
- **`PendingDelete.count` NENÍ `messageIds.size`.** Album je v bublině jedna věc, i když je to
  pět zpráv — titulek „Smazat 5 zpráv?" nad jedním albem by lhal.

### Informace o zprávě

Položka „Informace" v nabídce nad zprávou otevře bottom sheet: kdy zpráva odešla, kdy byla
upravená, kdy ji protějšek přečetl, kdo ji zhlédl a odkaz na ni (`MessageInfoSheet`,
`messageInfo` v `core/telegram`).

- **Sheet, ne popup ani řádky v nabídce.** Údajů je proměnlivý počet a zhlédnutí je celý seznam
  lidí, takže se v tom musí dát listovat. **Je to jednorázová akce, ne podnabídka** — proto
  nabídku ukončí a otevře samostatný sheet, na rozdíl od výběru reakcí, který zůstává uvnitř
  popupu (viz „Nabídka nad zprávou").
- **Jako jediná položka nabídky se nabízí VŽDY.** Nestojí na právu od serveru: čas odeslání má
  konverzace sama, takže sheet má co ukázat i u zprávy, u které server odkaz, zhlédnutí ani čas
  přečtení nevydá.
- **Tři doplňky, tři práva, jeden dotaz naráz.** `canGetReadDate`, `canGetViewers` a `canGetLink`
  se čtou z `getMessageProperties` a příslušná volání jdou **současně** se zprávou samotnou.
  Selhání jednoho ostatní nezahodí — server řekne ne u kdečeho a prázdný řádek je lepší než
  prázdný sheet.
- **Načítá se až při otevření.** Skoro žádná zpráva nemá všechna tři práva; ptát se dopředu by
  znamenalo tři dotazy na každou zprávu v historii kvůli sheetu, který se otevře jednou za čas.
- **Čas přečtení má PĚT podob, ne „čas nebo nic"** (`MessageReadState`): přečteno, nepřečteno,
  server už čas nedrží, protějšek má časy skryté, a „skrýváš je sám, takže je nevidíš ani ty".
  Každá je vlastní věta — „—" by uživateli neřeklo, jestli zprávu nikdo nepřečetl, nebo jestli je
  čas jen skrytý. Poslední dvě se od sebe liší tím, kdo to může změnit.
- **Ťuknutí na odkaz ho zkopíruje a sheet zavře.** Snackbar s potvrzením by pod modálním sheetem
  zůstal schovaný a nebylo by poznat, že se něco stalo; Android 13+ sice ukazuje vlastní náhled
  schránky, iOS a starší Androidy ne. Otevírat odkaz na zprávu, u které uživatel právě stojí,
  nedává smysl.
- **`getMessageLink` má sedm parametrů** (`mediaTimestamp`, `checklistTaskId`, `pollOptionId`,
  `forAlbum`, `inMessageThread`) — pojmenované argumenty, jako u všeho generovaného.
- **Typ odesílatele se jmenuje `SenderInfo`, ne `ReactionSender`.** Používají ho i zhlédnutí
  a název podle reakcí by u nich lhal.

### Soubory v chatu

Zpráva se souborem (`MessageMedia.Document`) má v bublině ikonu, název a velikost; ťuknutí soubor
stáhne a předá systému.

- **Stahuje se až na ťuknutí.** Soubory bývají velké a odběr `FileRepository.file()` sám spouští
  stahování — držet ho kvůli zobrazení by znamenalo stáhnout všechno, co kdo poslal.
- **Průběh je ve stavu podle `fileId`**, ne podle zprávy: tentýž soubor může být v chatu víckrát
  a bublina alba kreslí víc řádků. Druhé ťuknutí během stahování nic nespustí znovu.
- **Velikost se formátuje bez jednotky** (`formatFileSize`, testy v `FileSizeTest`); jednotku
  pojmenuje až UI z prostředků, protože pořadí čísla a zkratky se může lišit podle jazyka.
  Desetinné místo jen pod deset jednotek — „137,2 MB" je šum.
- **Desetinný oddělovač patří jazyku, ne číslu.** `FileSize` nese celou část a desetiny ZVLÁŠŤ
  a spojuje je až `fileSizeText` (`core/ui`) formátovacím řetězcem `decimal_number` — česky
  čárka, anglicky tečka, stejně jako „1,5×" u rychlosti hlasovek. Dokud `formatFileSize`
  vracel hotové „1.4", stála tečka i v české aplikaci — u souborů v chatu i v nastavení modelu
  („2.4 GB"). **Text velikosti skládá jen `fileSizeText`**; dřív si ho skládaly tři obrazovky
  a mapování jednotek bylo ve dvou kopiích.
- **Android: `ACTION_VIEW` přes `FileProvider`.** Od Androidu 7 je `file://` URI mimo aplikaci
  zakázané (`FileUriExposedException`). Provider `${applicationId}.documents` vydává adresář
  `tdlib-files` (a `cache/outgoing` u čerstvě odeslaných příloh), ale **oprávnění se uděluje jen
  na konkrétní URI** (`FLAG_GRANT_READ_URI_PERMISSION`). `FLAG_ACTIVITY_NEW_TASK` je nutný,
  protože se otevírá mimo naši úlohu.
- **Provider MUSÍ být vlastní třída** (`DocumentFileProvider : FileProvider()`), ne druhá
  deklarace `androidx.core.content.FileProvider` v manifestu. Providery se v procesu drží podle
  JMÉNA TŘÍDY, takže dvě deklarace téže třídy sdílejí jednu instanci — a ta zná jen tu autoritu,
  se kterou vznikla. Vedle provideru na obrázky do notifikací tedy požadavek na `…documents`
  spadl ještě uvnitř naší aplikace na `SecurityException: The authority … does not match the one
  of the contentProvider: ….notificationimages` a volající (Fotky) z toho ukázal jen „media not
  found". Každá další autorita proto potřebuje vlastní podtřídu.
- **`FILE_PROVIDER_PATHS` patří do manifestu i u vlastní třídy.** `getUriForFile` si strategii
  skládá z meta-dat, ne z konstruktoru — bez nich spadne na „Missing FILE_PROVIDER_PATHS
  meta-data".
- **Příznak v intentu některým příjemcům NESTAČÍ.** `FLAG_GRANT_READ_URI_PERMISSION` platí pro
  spuštěnou aktivitu, jenže aplikace jako Fotky soubor otevírají z jiné své části, která na grant
  z intentu nedosáhne — v logu je „Permission Denial: opening provider … that is not exported"
  a uživatel vidí „media not found". Proto se oprávnění ještě výslovně udělí každému, kdo intent
  zvládne (`queryIntentActivities` + `grantUriPermission`) — tatáž past jako u čteček notifikací
  u obrázku ve zprávě. Předchozí grant se při dalším otevření odebírá, aby nezůstal viset
  napořád — **ale jen když jde o jiné URI**. Odebrání u téhož souboru si vezme zpátky oprávnění,
  které jsme právě vydali, takže **první otevření prošlo a každé další hlásilo „media not found"**.
- **Ťuknutí musí umět stahování spustit ZNOVU** (`FileRepository.retryDownload`). `file()` si
  pamatuje, na co už request odešel, aby ho scroll nespouštěl při každém odběru; kdyby ale
  předchozí stahování skončilo v půli (spadlé spojení, TDLib soubor uvolnil), zůstal by ten stav
  viset a další ťuknutí by nedělalo nic. Výslovný pokyn uživatele tu paměť obchází.
- **Druhé ťuknutí stahování ZRUŠÍ i v TDLib** (`FileRepository.cancelDownload` →
  `cancelDownloadFile`), v konverzaci i v záložce Soubory profilu. Dřív se rušilo jen čekání
  v aplikaci: kolečko zmizelo, ale TDLib stahoval dál a soubor se potichu dostáhl. Zrušení
  zapomene i to, že se o soubor žádalo, takže další ťuknutí naváže na stažený kus. Ověřeno
  47MB souborem od bota: zrušeno ve 4. vteřině na 12,3 MB, po 20 s pořád 12,3 MB
  (`getFile` … `stahuje=false`); bez zrušení je soubor na emulátoru celý za 15 s.
- **iOS: `UIDocumentInteractionController`** (náhled, a z něj i sdílení). Kontroler i delegát se
  musí držet silnou referencí, dokud je náhled na obrazovce — stejná past jako u výběru příloh.
  Bez náhledu pro daný typ se ukáže nabídka „Otevřít v…".
- **Když soubor nemá čím otevřít, musí to jít vidět.** `FileOpener.open` vrací `false`
  a konverzace na to má snackbar; jinak by ťuknutí vypadalo, že nic neudělalo.
- **Obrázek a video poslané jako soubor s náhledem jsou VELKÉ, jako fotka** (`LargeDocumentPreview`,
  pravidlo `showsLargePreview` / `loadsOriginalPreview`, testy `MessageRowsTest`). Jdou na plochu
  bez bubliny (`showsBareMedia`), rámeček podle poměru stran náhledu (`photoFrame`; rozměry souboru
  TDLib u dokumentu nedává) a pod snímkem je vždycky název a velikost (`DocumentCaption`) — bez
  nich by soubor vypadal jako fotka. Ťuknutí otevře prohlížeč (s letem snímku), co ten neumí,
  systém s průběhem přes snímek; nabídka nad zprávou zůstává (Sdílet, Uložit jako…).
  - **Originál se stáhne sám jen u obrázku do 5 MB** (strop GIFů), který umí prohlížeč — jinak
    zůstane náhled od serveru (do 320 px), video vždycky. Ověřeno: 223 kB ostrý originál, 6,2 MB
    náhled, video se symbolem přehrání.
  - **Server náhled nevyrobí vždycky**: HEIC nikdy, a botem poslaný JPEG o 13 a 22 MB taky ne
    (6,2 MB ano). Bez náhledu zůstává řádek s ikonou — není z čeho vzít poměr stran.
  - **Citlivý soubor a album souborů zůstávají řádky.** Řádek umí rozostřit a odkrýt; album
    souborů je seznam, ne mřížka.
  - **S textem zprávy snímek využije celou šířku a ořízne se na střed**, jako fotka s popiskem
    (`fillWidth`); jen s řádkem názvu ne — ten má každý soubor a roztahoval by každý náhled.
- **Obrázek a video poslané jako soubor BEZ velkého náhledu mají v řádku náhled** místo ikony (`DocumentThumbnail`),
  video navíc symbol přehrání; bez náhledu aspoň ikonu podle druhu (`documentKind`, testy
  `DocumentKindTest`). Druh rozhoduje **přípona před `mimeType`** — ten posílá odesílatel
  a bývá `application/octet-stream` — a obojí se porovnává se seznamem formátů, ne s prefixem
  `image/`: TIFF, RAW ani PSD neotevřeme a ikona fotky by slibovala něco, co ťuknutí nesplní.
- **Obrázek a video poslané jako soubor se otevírají v prohlížeči médií**, jen když je platforma
  opravdu umí (`opensInViewer`, platformní `viewerUnsupportedFormats`): HEIC dekóduje až
  Android 9 a Skia v iOS ne, WebM a MKV přehraje ExoPlayer, AVPlayer ne. Co prohlížeč neumí, jde
  systému jako každý jiný soubor — ťuknutí, které skončí prázdnou stránkou, je horší.
- **V prohlížeči je soubor sám, bez listování a bez „N z M".** Listuje se přes
  `SearchMessagesFilterPhotoAndVideo`, kam dokument nepatří, takže načtení výřezu zprávu
  nenajde a prohlížeč spadne do stávající zálohy pro jednu zprávu. Pozice od TDLib by se
  počítala mezi fotkami, proto se u souboru zahazuje (`single`). Stránku staví `asViewerMedia`:
  soubor je ostrá verze, náhled okamžitý obrázek, rozměry z náhledu. Bez náhledu jsou
  nulové, karta je čtverec a obrázek se do ní vepíše (`Fit`), neořízne.
- **Náhled dokumentu vyrábí server, ale ne vždy** (ověřeno botem, `sendDocument`): JPEG i MP4
  ho dostanou, i s vypnutou detekcí typu, HEIC ne. Průběh stahování jde přes náhled bíle na
  závoji — akcent bubliny by na fotce zanikl.
- **MP4 bez zvuku poslané jako soubor se z Telegramu vrátí jako GIF** (`MessageAnimation`), ne
  jako dokument. Kdo chce testovat video-soubor, musí poslat soubor se zvukem, nebo
  `disable_content_type_detection=true` (`screenrecord` z emulátoru zvuk nemá).
- **Sdílet a Uložit jako… má soubor v nabídce nad zprávou** (`MessageMenuAction.ShareFile`
  a `SaveFileAs`, kandidáti jen u `MessageMedia.Document`, podle `canBeSaved`). Soubor, který
  obrázkem ani videem není, se do prohlížeče nedostane, takže ven jde odsud. Stahování je
  společné s otevřením (`withDocument`): stejný průběh v řádku, stejné hlášky. „Uložit jako…"
  potřebuje výběr umístění, takže ji z bloku zachytí obrazovka (`ScreenItemScope`) a ViewModelu
  předá `SaveAsLauncher`; na iOS se nenabízí (`saveAsSupported`).
- **Položka, jejíž práce trvá, musí nabídku zavřít SAMA** (`closeMenus()`). Nabídka se po
  ťuknutí sama nezavírá — to dělá každá akce ve ViewModelu. Bez toho zůstala otevřená přes
  celé stahování a hláška o výsledku zapadla pod závojem.
- **Odesílaný soubor se otevřít nedá** — ukazuje průběh nahrávání a ještě není kde.

### Přeposlaná zpráva

Nad obsahem přeposlané zprávy stojí „Přeposláno od …" s šipkou (`ForwardLabel` v `MessageFrame.kt`,
doména `ChatMessage.forwardOrigin` / `ForwardOrigin`). Ťuknutí vede ke zdroji: příspěvek kanálu
otevře kanál se skokem na původní zprávu (`ChatTarget(chatId, messageId)`), uživatel soukromý chat.

- **Původ jsou čtyři druhy a jen tři vedou někam** (`MessageOrigin*`). Skrytý uživatel
  (`MessageOriginHiddenUser`) nese jen jméno — zakázal si v soukromí odkaz na účet — takže se
  u něj značka nedá ťuknout. `when` je bez `else`: nový druh v novější TDLib rozbije překlad.
- **Značka je součást téže karty jako obsah, ne text nad ní.** Přeposlání je údaj O ZPRÁVĚ;
  volně nad holou fotkou (první verze) vypadala jako popisek něčeho jiného. V bublině je prvním
  řádkem, u holého snímku horní díl karty v barvě bubliny (`ForwardHeader`, protějšek popisku
  pod snímkem — `MediaWithCaption` má proto hlavičku, snímek a popisek se stejnou šířkou),
  a u karty odkazu v horním dílu s textem.
- **Přeposlaná samolepka a pár emoji si bublinu vynutí** (`needsCard` v `messageItem()`, testy
  `ConversationItemsTest`), stejně jako odpověď. Pravidlo je obecné: **holý obsah jde na plochu
  jen sám; jakmile nese údaj navíc (popisek, citace, přeposlání), je z něj karta.** Samostatný
  štítek nad samolepkou (zkoušeno) byl třetí, vymyšlený tvar vedle bubliny a karty.
- **Značka je u KAŽDÉ zprávy bloku**, ne jen u první jako jméno odesílatele. Přeposlané zprávy
  téhož člověka bývají každá odjinud.
- **Jméno zdroje jde z keše odesílatelů** (`resolveSender`, bere uživatele i chat). V kanálu,
  který přeposílá z pár zdrojů pořád dokola, je to dotaz na zdroj, ne na zprávu. Neznámé jméno
  (smazaný účet, chat, který TDLib nezná) = „Přeposlaná zpráva", ne značka pryč.
- **Barva je barva popředí plochy, ztlumená; jméno nese řez.** Barva zdroje by se musela hlídat
  proti každé ploše (bublina, zvýrazněná bublina, plocha konverzace pod holou fotkou), viz
  `accentOn`. Jméno se do šablony vkládá přes zástupný znak, aby šlo zvýraznit řezem a pořadí
  slov zůstalo na překladu.
- **Značka má vlastní klikání, takže opakuje dlouhý stisk** (`combinedClickable` s
  `requestMenu`) — jinak by nad ní nešla otevřít nabídka zprávy. Tatáž past jako u citace.
- **Ověřuje se botem**: `forwardMessage` z chatu s botem do téhož chatu vyrobí přeposlanou zprávu
  s původem „uživatel". Kanál jako zdroj bot bez přístupu ke kanálu nepřepošle.

### Přeposílání

„Přeposlat" v nabídce nad zprávou otevře výběr cílového chatu (`ForwardRoute`, vlastní obrazovka);
po odeslání se otevře cílový chat, takže je vidět, že zpráva dorazila.

- **Nabízí se podle `canBeForwarded`** z `getMessageProperties`, jako ostatní položky nabídky.
  Kanál s chráněným obsahem přeposílání zakazuje a nabídnout akci, která skončí chybou, je horší
  než ji nenabídnout.
- **Album se přeposílá celé** (`albumMemberIds`, stejně jako mazání) — v bublině je to jedna věc
  a v cíli má albem zůstat. **Id musí být vzestupně**, jinak TDLib volání odmítne.
- **Výběr je obrazovka, ne sheet.** Seznam chatů je dlouhý a listuje se v něm; sheet by navíc
  při hledání zápasil s klávesnicí. Kreslí ho `ChatPicker` z `core/ui`, společný se sdílením
  i novou konverzací (viz „Jeden výběr chatu").
- **Bez dotazu je ve výběru živý hlavní seznam, s dotazem výsledky `ChatSearch`** — ze všech
  chatů, i archivovaných (`pickerChats`, viz „Hledání chatů").
- **Výběr vypadá jako seznam chatů, ne jako systémový dialog**: hledá se v `SearchPill`
  a chaty jsou blok zaoblených řádků (`groupItemShape`, `surfaceContainerLowest`, spára 2 dp)
  s tváří podle druhu chatu. Vybraný cíl nese `secondaryContainer` a je zaoblený celý — výběr
  je v M3 selekční stav, tatáž role jako u vybraného chatu v dvoupanelu; samotné zaškrtávátko
  na konci řádku se v dlouhém seznamu hledá hůř než barva. Dřív to byl `TextField` s podtržením
  a holé `ListItem`y na pozadí, tedy dva vzhledy pro „seznam věcí" v jedné aplikaci.
- **Prázdný výsledek je `IntroEmptyState`**, jako u hledání v chatech a prázdného archivu.
- **Během odesílání se seznam nedá ťuknout podruhé** — dvě ťuknutí by poslala zprávu dvakrát.
- **Výchozí je přeposlání, kopie je volba** — přepínací tlačítka pod hledáním: „Skrýt
  odesílatele" (`sendCopy`, v cíli není „Přeposláno od …") a s ním „Skrýt popisky"
  (`removeCaption`). Pravidlo je čistá funkce `ForwardOptions.allowedBy` (testy
  `ForwardOptionsTest`).
- **Popisky jdou skrýt JEN u kopie.** TDLib `removeCaption` bez `sendCopy` ignoruje, takže se
  druhé tlačítko ukáže až se zapnutým prvním — a jen když přeposílané zprávy nějaký popisek
  nesou (médium s textem; u typů bez přílohy je text naše věta, ne popisek).
- **Kopii nabízí jen `canBeCopied`** u VŠECH zpráv (`forwardCapabilities`, zjišťuje se jednou
  při otevření, zpráva a příznaky souběžně). Volba se ukáže až po odpovědi — zmizelá by
  vypadala jako chyba; když se zjistit nepodaří, nenabídne se.
- **Volby se nepamatují.** Přeposlání bez odesílatele je rozhodnutí o konkrétní zprávě; zapnuté
  napořád by příště tiše vydávalo cizí zprávu za vlastní.
- **`animateContentSize` na FAB mu UŘÍZNE stín a spodní nároží.** Modifikátor ořezává na svoje
  hranice, takže plovoucí tlačítko vyšlo dole hranaté, jako by bylo useknuté. Počet cílů se
  proto animuje na POPISKU, ne na tlačítku — tatáž past jako u plovoucí lišty konverzace.
- **Seznam má dole místo na tlačítko** (`FAB_CLEARANCE`), jinak zůstane zaškrtávátko posledního
  chatu schované pod ním. Přidává se, jen když se vybírá (u sdílení tlačítko není).
- **Spodní inset patří do `contentPadding` seznamu, ne na kontejner.** S paddingem ze Scaffoldu
  na sloupci skončil seznam nad navigační lištou ostrou hranou a poslední chat byl useknutý —
  tentýž vzor jako seznam chatů a výsledky hledání: chaty jedou pod lištu a poslední se nad ni
  doroluje. Ze Scaffoldu se na sloupec bere jen horní a boční padding.
- **Scaffold výběru má `contentWindowInsets = safeDrawing`, tedy i klávesnici.** Podle spodního
  insetu staví Scaffold tlačítko a snackbar, takže s výchozími systémovými lištami odesílací
  tlačítko při hledání zmizelo pod klávesnicí. Seznam bere týž inset (`safeDrawing`), aby se
  poslední chat dal doscrollovat i nad ni.
- **Cílový chat se do zásobníku přidá jen, když na něm zásobník už nestojí** — přeposlání do
  téhož chatu by jinak otevřelo druhou kopii konverzace.
- **Přeposílá se do VÍC cílů naráz**, takže ťuknutí na chat ho VYBERE (zaškrtávátko) a odesílá
  se až tlačítkem s počtem. Je to o ťuknutí víc než dřív, ale poslat totéž do víc chatů je běžný
  případ a výběr se dá opravit; stejně to dělá i oficiální klient.
- **Sdílení ze systému zůstává na jednom cíli** (`ChatPicker(selected = null)`). Obsah tam čeká
  nad vstupním polem, takže „vyber a potvrď" by byl krok navíc před dalším krokem.
- **Po odeslání se otevře PRVNÍ vybraný cíl.** Otevřít poslední nebo neotevřít nic by bylo
  svévolné; první vybraný je ten, na který uživatel myslel nejdřív. Množina cílů si proto drží
  pořadí výběru (`Set.plus` vrací `LinkedHashSet`).
- **Do cílů se posílá POSTUPNĚ, ne souběžně.** Pořadí zpráv v cílech tím zůstane dané a server
  nedostane deset přeposlání naráz. Selhání jednoho cíle ostatní nezastaví — co odešlo, odešlo,
  a chyba se ohlásí na konci.

### Jeden prvek pro hledání: `SearchPill`

Hledání v seznamu chatů, v konverzaci, ve výběru člena i ve výběru chatu (přeposlání, sdílení)
kreslí tentýž prvek — pilulka `surfaceContainerHigh` s ikonou, polem a ✕ (`core/ui/SearchPill.kt`).

- **Tři různě vypadající pole = uživatel poznává hledání na každé obrazovce znovu.** Ve výběru
  cíle byl `TextField` s podtržením, ve výběru člena `TextField` se zaoblením 28 dp a v konverzaci
  ruční pilulka; rozešly se hned, jak se jedna z nich doladila.
- **Text drží pole samo** (`initialQuery` je jen výchozí hodnota). Hodnota vrácená přes `StateFlow`
  by přišla o dispatch později a kurzor by poskakoval — tentýž důvod jako u vstupního pole.
- **Uvnitř je `BasicTextField` s `DecorationBox`**, ne `TextField`: ten má minimální výšku 56 dp
  a odsazení 16 dp napevno, takže by vedle ikony v pilulce nezbylo místo na text.
- **Sloty `leading` a `prefix` si nesou odsazení samy** — tlačítko má kolem glyfu svoje, holá
  lupa ne. `leading` je v konverzaci zpět (zavírá hledání, ne obrazovku), jinde lupa; `prefix`
  nese čip vybraného člena.

### Odkrývání časů tahem

Tah doleva posune celou plochu zpráv a vpravo odkryje čas u každé z nich; po puštění se plocha
vrátí. V bublině čas není (viz „Čas a stav doručení"), takže tohle je vedle předělu dne a plovoucího
data jediné místo, kde se dá přečíst.

- **Je to gesto nad SEZNAMEM, ne nad položkou.** Čas u jedné zprávy neřekne, kdy přišly ty okolo,
  a u jediné bubliny by to vypadalo jako druhá polovina swipu na odpověď. Jede proto všechno
  najednou, včetně sponzorované karty — ta jen nemá co ukázat.
- **Bublina si proto nesmí vzít každý vodorovný tah.** `draggable` je po slopu směrově agnostický:
  vezme si i tah doleva, nikam ho nepromítne (posun je `coerceIn(0f, maxDrag)`) a rodiči už ho
  nepustí. Tah doleva se tím tiše zahazoval. Odpověď swipem proto bere jen tah DOPRAVA
  (`SwipeDirection.Right`), odkrývání jen doleva; opačný směr nespotřebují a propadne dál.
- **Svislý scroll má přednost a detektor se rozhoduje pomalu** (`detectHorizontalSwipe`, viz
  „Jeden detektor tahu do strany"). Seznam je potomek, dostane událost dřív
  a jeho spotřebování detektor rodiče zruší — jenže s `detectHorizontalDragGestures` stačilo, aby
  prst ujel doleva o jeden touch slop dřív než nahoru, a palec levé ruky jde na začátku tahu
  obloukem doleva: na telefonu se místo scrollu vysouvaly časy. Teď si tah odkrývání vezme až po
  **dvojnásobku touch slopu** (svislý scroll má práh kratší, takže šikmý tah si seznam vezme dřív)
  a jen když jde **aspoň 2× víc do strany než svisle** a doleva. Na emulátoru se to ověřuje
  `input swipe` se screencapem uprostřed tahu (`input swipe … 1500 & sleep 1; screencap`).
- **Posun se čte až při kreslení** (`graphicsLayer { translationX = -reveal() }`, lambda, ne
  hodnota). Jinak by každý snímek gesta přeskládal celou konverzaci.
- **Čas čeká za pravým okrajem** a přijíždí s posunem; seznam ořezává na svoje hranice, takže
  dokud se netáhne, není ho vidět. Sloupec má pevnou šířku a posun se o ni zastropuje — gumové
  tahání dál by jen mátlo.
- **Předěly dne se neposouvají.** Jsou to nadpisy nad blokem, ne zprávy, a čas u nich nedává smysl.
- **Čtečka obrazovky se tahem nikam nedostane**, takže čas je i v nabídce nad zprávou, prvním
  řádkem (den a hodina). Není to položka nabídky, nedá se na ni ťuknout. **Výška nabídky se
  počítá, ne měří**, takže se hlavička musí připočíst — jinak by nabídka seděla vedle.
- **`asTimeOfDay()` není `@Composable`** — hodiny a minuty žádné prostředky nepotřebují a ve
  24hodinovém zápisu se pořadí mezi jazyky nemění. Den vedle nich už prostředky potřebuje
  (`asDayLabel`), a proto je v nabídce, ne u zprávy.

### Odpověď swipem

Tažení zprávy doprava otevře odpověď. Je to nejčastější akce nad zprávou a přes nabídku by na ni
vedly dva kroky.

- **Při scrollu se zakládala odpověď** — bublina si tah brala přes
  `awaitHorizontalTouchSlopOrCancellation`, tedy po JEDNOM touch slopu a jen podle vodorovné
  složky. Bublina je potomek seznamu a dostane dotyk dřív než on, takže u šikmého tahu vyhrála
  ona. Teď jede na sdíleném `detectHorizontalSwipe` (viz „Jeden detektor tahu do strany").
- **Bublina jede s prstem od položení** (první `onDrag` nese celý posun z rozhodování), takže
  práh `REPLY_THRESHOLD` (64 dp) je celá dráha prstu. Dřív bublina začínala až za slopem
  a prst ušel slop + 56 dp — práh se zvedl, aby dráha zůstala stejná.
- **Táhne se doprava u OBOU stran konverzace.** Zrcadlit směr podle odesílatele by znamenalo,
  že uživatel musí před každým gestem přemýšlet, čí zpráva to je. Odchozí bublina se přitom kusem
  vysune za pravý okraj — tak to dělá Telegram i WhatsApp.
- **Odpověď odchází při PUŠTĚNÍ, ne při překročení prahu.** Kdo si to rozmyslí, vrátí prst zpátky
  a nic se nestane. Příznak `armed` hlídá, aby haptika i odpověď odešly jen jednou za tah.
- **Za prahem klade tah odpor** (30 %). Bez toho by šla bublina odtáhnout přes půl obrazovky
  a nebylo by poznat, že se dál už nic nestane.
- **Posun i průhlednost šipky se čtou až v layoutu a při kreslení** (`offset { }`,
  `graphicsLayer`), ne v kompozici. Jinak by každý snímek gesta znamenal přeskládání bubliny
  i s obsahem.
- **Šipka je uvnitř `Box`u kolem bubliny, posazená `offset`em mimo něj.** Do layoutu tím
  nezasahuje, takže nic neodsouvá a neovlivňuje šířku zprávy — a jede s bublinou, takže je
  jasné, čeho se gesto týká.
- **Vlevo od bubliny u obou stran**: tam vzniká mezera, když se táhne doprava.
- **U kraje obrazovky vyhrává systémové gesto zpět.** Bublina má odsazení 12 dp, takže se
  s ním u příchozích zpráv kousek překrývá. Nedá se s tím nic dělat a stejné to je i v Telegramu.

### Nápovědy gest napoprvé

Tři gesta, která jinak nikdo neobjeví, mají jednou za instalaci `RichTooltip` se šipkou
(`GestureHints.kt`, pravidlo `nextGestureHint`, testy `GestureHintTest`): odkrývání časů tahem
(bublina u pravého okraje seznamu), podržení odesílacího tlačítka pro volby (efekt, bez zvuku,
naplánovat) a podržení mikrofonu / kamery pro nahrávání. Co už uživatel viděl, si pamatuje
`AppSettings.seenGestureHints` (názvy výčtu `GestureHint`, neznámý se zahodí).

- **Jedna za návštěvu chatu a až po 1,5 s klidu** (`HINT_DELAY_MS`). Kandidát se počítá
  pořád znovu a každá změna podmínek čekání začne od nuly; vyskočit hned po otevření by
  přerušilo první pohled do chatu. Návštěva se pamatuje `rememberSaveable` podle chatu, takže
  otočení displeje neukáže hned další.
- **Viděná je od chvíle, kdy se UKÁZALA**, ne až po „Rozumím". Kdo ji zavře ťuknutím vedle,
  znovu ji vidět nechce. A **použité gesto ji zavře a označí taky** (`GestureHints.used`):
  tah odkrývání, podržení pro volby, začátek nahrávání.
- **Jen nad obrazovkou, na které nic nečeká** (`conversationHintContext`): načtený chat, žádná
  nabídka nad zprávou, výběr, hledání, úprava, sheet ani nahrávání. Když podmínka přestane
  platit (začne psát, otevře nabídku), bublina zmizí, i když ji uživatel ještě nedočetl —
  visela by nad něčím jiným. První verze podmínku na načtený chat neměla a nápověda vyskočila
  nad prázdnou hlavičkou „Konverzace".
- **Pořadí: volby odeslání, nahrávání, časy.** Volby jdou vidět jen při psaní, tedy vzácně;
  časy se týkají čtení a počkají.
- **Obrazovka podmínky NEČTE v kompozici.** `ConversationScreen` je na hraně (viz „Gotchas")
  a skládat ji kvůli nápovědě by bylo plýtvání: podmínky jdou přes `snapshotFlow` nad lambdami
  (`rememberGestureHints`) a bublinu čtou jen kotvy ve vlastních funkcích
  (`RevealTimesHintAnchor`, `ButtonGestureHint`). Co dělá tlačítko odeslání, ví jen vstupní pole,
  a tak ho do `GestureHints` zapisuje samo (`SideEffect`) a při odchodu pole vynuluje — jinak
  by hlášení viselo, když pole nahradí lišta výběru.
- **`TooltipBox` NEPŘEDÁ rodiči zarovnání.** `Modifier.align(Alignment.CenterEnd)` předaný jako
  `modifier` neudělal nic a kotva skončila v levém horním rohu seznamu (změřeno sondou:
  bounds 0,310). Bublina se pak „vešla" vpravo od ní a šipka ukazovala ven z obrazovky.
  Zarovnání proto nese vlastní `Box` kolem `TooltipBox`. Kotva je 16 dp od kraje, jinak leží
  šipka za okrajem.
- **Nápověda propouští dotyky** (nefokusovatelný popup): tah doleva přes ni rovnou odkryje
  časy a ťuknutí vedle ji zavře (`onDismissRequest`).
- **Ověřeno na emulátoru** (česky, chat s testovacím botem): nahrávání s kamerou, volby
  odeslání s efekty nad tlačítkem, časy u pravého okraje (sondou, která časy brala jako
  neviděné) a jejich zavření tahem. iOS jen přeložené.

### Jeden detektor tahu do strany

Odkrývání časů, odpověď tahem i archivace v seznamu chatů jsou tah do strany nad svislým
seznamem a mají JEDNO pravidlo (`detectHorizontalSwipe` a `horizontalSwipeDecision`
v `core/ui/HorizontalSwipe.kt`, testy `HorizontalSwipeTest`). Dřív to byly tři kopie a dvě
z nich se opravily, zatímco třetí (odpověď) zůstala citlivá — přesně to, čemu má jedno místo
bránit.

- **Rozhoduje se až po DVOJNÁSOBKU touch slopu a jen u tahu aspoň 2× víc do strany než svisle**
  (~27°), jen ve zvoleném směru (`SwipeDirection`). Svislý scroll má práh kratší, takže šikmý
  tah si seznam vezme dřív, než se tu o čemkoli rozhodne.
- **Spotřebování se hlídá i ve fázi `Final`.** Položka seznamu (bublina, řádek chatu) dostává
  události ve fázi `Main` DŘÍV než seznam, takže rozjetý scroll ve `Main` nevidí; ve `Final`
  už ano. Stejně to dělá `awaitTouchSlopOrCancellation` z Compose.
- **Práh ohlašuje i TVAR, ne jen haptika** (`SwipeThresholdBadge` v `core/ui`, archivace
  i odpověď tahem): ikona pod taženou položkou je do prahu kolečko, za prahem naroste,
  morfne do sušenky (`ShapeMorphs.Rest` — „připraveno, pusť a stane se") a vyplní se akcentem.
  Haptiku nemá každý zapnutou a v ruce se dá přehlédnout.
  - **Tvar sleduje AKTUÁLNÍ polohu prstu** (`derivedStateOf { |drag| >= práh }`), haptika jen
    první překročení v tahu (`armed`). Kdo prst vrátí před práh, vidí kolečko a ví, že po
    puštění se nic nestane. `derivedStateOf`, aby se nic nerekomponovalo každý snímek tahu.
  - **Pružina je rychlá (`StiffnessMediumLow`, tlumení 0,5)**, ne `morphSpring`: odezva patří
    k tahu a musí přijít s vibrací, ne o půl vteřiny později.
  - **Výplň se měří proti ploše pod ikonou** (`accentOn`). Na `primaryContainer` podkladu
    archivace `primary` neprojde AA a výplň je `onPrimaryContainer` s ikonou v barvě podkladu
    — ve světlém i tmavém motivu čitelné. U odpovědi (plocha konverzace) je `primary`.
  - **Archivace potřebuje šířku řádku i mimo `pointerInput`** (práh je podíl šířky), proto
    `onSizeChanged`. Ověřeno na emulátoru tahem přes `input motionevent` zastaveným před
    prahem a za ním a vráceným zpátky (nic se nearchivovalo ani nezaložila odpověď).
- **Plynulost a to, jestli to pod palcem sedí, se posuzuje na telefonu** — emulátor s `input
  swipe` ověří jen směr a pořadí rozhodnutí.

## Profil chatu

Ťuknutí na hlavičku konverzace otevře profil (`ChatProfileRoute`, `features/profile`): velká fotka
a jméno, podtitul (dostupnost / počet členů / „Bot"), dlaždice Ztlumit a Hledat, údaje
(uživatelské jméno, telefon, bio nebo popis) a u skupiny členové s rolí.

- **`ProfileRepository` v `core/telegram`** nad `getUserFullInfo` / `getBasicGroupFullInfo` /
  `getSupergroupFullInfo` a `searchChatMembers`. Data se čtou při otevření; živé je jen ztlumení
  (`ChatRepository.muted`, stejný vzor jako `activity`) a dostupnost (`presenceUpdates`).
- **Profil je detailový panel** (`detailPane()`): na telefonu překryje konverzaci, na širokém
  rozvržení ji v pravém panelu nahradí a zpět vede do ní. Spodní bar se skrývá jako u konverzace
  (`inDetail`). Routa je v `NavSavedStateConfiguration`.
- **Avatar a název přiletí z hlavičky konverzace** — tytéž sdílené klíče jako ze seznamu chatů.
- **Do profilu vede i tvář odesílatele ve skupině** (`MessageAction.OpenSenderProfile`). Profil
  stojí na chatu, takže se napřed najde soukromý chat (`privateChat`, lokálně). **Sdílený klíč
  nese jen ŤUKNUTÁ tvář** (`ConversationItemScope.isProfileFlight`, `rememberSaveable` podle id
  zprávy): tentýž člověk má tvář u každého bloku zpráv a o jeden klíč by se přetahovaly všechny.
  Klíč se nastaví při ťuknutí, dřív než se asynchronně najde chat — navigace ho pak už vidí.
  Záporné id odesílatele je chat (anonymní správce), ten profil neotevírá.
- **Hledat v profilu otevřeném ze skupiny otevře konverzaci S NÍM**, ne skupinu pod profilem:
  požadavek hledání je stav podle chatu a čekal by na chat, který není vidět.
- **Člen ze seznamu letí do hlavičky konverzace** (avatar i jméno, klíče jako ze seznamu chatů),
  kromě vlastního účtu — ten vede do Uložených zpráv se záložkou místo fotky.
- **Údaje i obsah záložek jsou bloky zaoblených řádků** (`groupItemShape`, `surfaceContainerLowest`,
  spára 2 dp), stejný jazyk jako seznam chatů. Dřív byly údaje karta `SettingsGroup`
  (`surfaceContainerHighest`) a záložky holé řádky — v jednom profilu dva vzhledy pro „seznam věcí".
  Mřížka médií nese velké zaoblení jen v rozích bloku (`mediaTileShape`, i schod neúplného
  posledního řádku). Role člena je tonální štítek, ne barevné slovo;
  vlastník terciární s hvězdou, správce sekundární se štítem — ikona proto, že barva sama
  rozdíl nenese (barvoslepost, paleta se zvýšeným kontrastem). Vlastní štítek člena nahrazuje
  slovo role, ikona a barva role zůstávají; běžný člen se štítkem má neutrální štítek. Bot je
  pod jménem, ne ve štítku — bývá i správce a dva štítky vedle sebe by se praly.
- **„Custom title" správce v TDLib 15 už NENÍ.** `ChatMemberStatusCreator/Administrator` ho
  nenesou; nahradil ho `ChatMember.tag`, který smí mít kterýkoli člen. Kdo hledá titul u stavu
  správce, nenajde nic. Akce jsou tónované dlaždice
  s ikonou nad popiskem; popisek říká, co se stane (Ztlumit / Zapnout zvuk), ne stav.
- **Záložky jsou connected button group** (`ToggleButton` s `ButtonGroupDefaults.connected*Shapes`),
  ne `PrimaryTabRow` s podtržením — to je vzhled M2 a mezi zaoblenými bloky působil cize.
  Vybraná je plná `primary` a zakulatí se celá. Semantika `Role.Tab`.
- **Vybraná záložka si přianimuje ikonu a o ni se rozšíří** (váha 1 → 1,4, jednou pružinou
  s ikonou). Se stejnou šířkou všech čtyř by ikona ukrojila popisek („Čle…"); ověřuje se
  v češtině, má nejdelší popisky.
- **Avatar se při sbalování zmenšuje do jména a mizí** (`graphicsLayer`, průběh = podíl výšky
  avataru, o který profil odjel). Zmenšuje se ke spodku a PŘESNĚ o tolik, o kolik odjel: vršek
  tím stojí těsně pod lištou. S poloviční mírou (první pokus) vršek dál utíkal pod lištu a byl
  useknutý. Scroll se čte až při kreslení, nic nerekomponuje.
- **Dlaždice akcí mění tvar**: stiskem zhranatí (20 → 12 dp) jako tlačítka M3 Expressive a ztlumený
  chat má dlaždici celou kulatou v `tertiaryContainer` — stejný jazyk jako vybraná záložka.
  Hledat stav nemá, takže tvarem nic nenese. Ikona s popiskem se mění přechodem: změna přijde až
  s potvrzením serveru a skok se dal přehlédnout.
- **Název se po odjetí velkého jména ukáže v liště** (`titleInBar`: scroll za spodkem jména,
  změřeným `onPlaced`). Bez toho nebylo u sbalené hlavičky poznat, čí profil je otevřený.
- **Hledat zavře profil a otevře hledání v konverzaci** přes `ConversationSearchRequests` — STAV,
  ne událost (jako `ChatOpenRequests`): konverzace pod profilem si požadavek vyzvedne, až ho
  odebírá. Hledání patří konverzaci, profil by ho jen zdvojil.
- **Popis skupiny chodí jako holý text** — odkazy a zmínky v něm dohledá `getTextEntities`
  (lokálně), aby byly klikatelné jako v bio (to je `FormattedText` rovnou). Bot bio nemá, bere se
  `botInfo.shortDescription`.
- **Členové jen tam, kde je server vydá** (`canListMembers`: malá skupina vždy, supergroup podle
  `canGetMembers`, kanál ne). Smazané účty (bez jména) a kdo odešel se vynechají. Ťuknutí na
  člena otevře soukromý chat.

### Členové velké skupiny

Záložka Členové se stránkuje po 50 (`MEMBERS_PAGE_SIZE`) a u skupiny, která se na stránku
nevejde, má nad seznamem pole hledání (`MemberList`, testy `MemberListTest`).

- **`searchChatMembers` stránkovat NEUMÍ** — nemá `offset`, jen `limit` do 200. Dřív se proto
  ukazovalo prvních 200 a dál nic. Supergroup se čte `getSupergroupMembers` s filtrem
  (`Recent`, s dotazem `Search`) a posunem; základní skupina má nejvýš pár set členů a vydá je
  `searchChatMembers` naráz.
- **Vlastník a správci napřed jen na první stránce bez dotazu** (samostatný dotaz s filtrem
  `Administrators`). Setřídit celý seznam podle role nejde, další stránky přicházejí až potom.
  Server správce vrací i mezi nedávnými, takže se **tentýž člověk na dvou stránkách drží jednou**
  (`distinctBy`) — dva stejné klíče by `LazyColumn` shodily. Totéž hrozí, když se mezitím někdo
  přidá a celá řada se posune o místo.
- **Posun další stránky se počítá ze SUROVÉ odpovědi**, ne z vyfiltrovaného seznamu, a konec
  z `totalCount`. Stránka, ze které po odfiltrování nezbyl nikdo nový, seznam neprodlouží —
  a seznam, který se neprodloužil, si o další stránku sám neřekne; ViewModel ji proto dotáhne hned.
- **Repository chytá do `Result` i zrušení korutiny** (`runCatching` chytí
  `CancellationException`). Zrušené hledání by tím prošlo jako selhání a přepsalo výsledky
  prázdnem — po návratu z repository proto `ensureActive()`.
- **Skupina se skrytými členy vydá běžnému členovi JEN SPRÁVCE** (`hasHiddenMembers`), i při
  hledání a i s `canGetMembers == true`. Velké veřejné skupiny to mívají zapnuté skoro vždycky
  (Android Developers: 16 z 11 168, Kotlin Community: 22 z 6 840). Záložka to říká větou nad
  seznamem (`ChatProfile.membersHidden`) a hledání se nenabízí; bez vysvětlení to vypadalo jako
  nedotažený seznam. **Nečlenovi veřejné skupiny server taky vydá jen správce.**
- **Ověřit to jde jen ve skupině, kde je účet ČLENEM a členy nevidí skryté** — a takovou
  testovací účet nemá. Stránkování se proto ověřilo sondou (stránka po 3) na Android Developers:
  šest stránek po sobě, konec podle `totalCount`, s i bez správců napřed. **Pozor na kontrolu
  proti botům:** skupiny pouštějí nové členy až po ťuknutí na tlačítko pod zprávou od svého bota,
  a Plume tlačítka botů zatím nekreslí — napsaná odpověď se nepočítá.
- **Uložené zprávy profil nemají** — hlavička v nich není klikatelná.
- **Počty se oddělují po tisících úzkou nezlomitelnou mezerou** (`Int.grouped()` v `core/ui`) —
  „10704668 odběratelů" se nedalo přečíst. Commonmain `NumberFormat` nemá; mezera je čitelná
  v češtině i angličtině. Plurály proto berou číslo jako `%1$s`.

- **Méně časté akce jsou za ⋮ v liště** (`ProfileMenu`): kopírovat odkaz (veřejné jméno, jinak
  pozvánka — tu server vydá jen správci s právem zvát), zablokovat / odblokovat uživatele či
  bota a smazat chat / opustit skupinu nebo kanál. Dlaždice nesou to, kvůli čemu se do profilu
  chodí; tohle se dělá jednou za čas a dvě z toho jsou nevratné, s potvrzením a stejnými texty
  jako v seznamu chatů. Odblokovat je bez potvrzení.
- **Po smazání nebo odchodu se zavře všechno, co chat ukazuje** (`closeChat` v `ChatsStack.kt`,
  test v `ChatsStackTest`): konverzace, profil a prohlížeč médií, v hlavním seznamu i archivu.
- **Blokuje se hlavní seznam** (`BlockListMain`) — ten brání psát i volat. Stav se čte
  z `UserFullInfo.blockList` a po akci přepíše lokálně: profil `UpdateUserFullInfo` neodebírá.
- **Opustit se nabízí jen tam, kde uživatel je** (`isMember` z vlastního postavení) — z chatu
  otevřeného z globálního hledání není odkud odejít.
- **Snackbar profilu dřív neukázal nikdy nic**, ani chybu: efekt klíčovaný `state.error` hlášku
  vynuloval a tím se sám zrušil (past „Vrátit"). Teď běží v `rememberCoroutineScope`.
- **⋮ → Sdílet kontakt** (`ShareContactRoute`, `ShareContactScreen`): tentýž `ChatPicker` jako
  přeposílání, víc cílů naráz, odesílá se tlačítkem a otevře se první vybraný chat. Nabízí se
  jen u člověka se ZNÁMÝM číslem (`ProfileKind.User`, `phone`) — bot číslo nemá a kontakt se
  bez čísla poslat nedá. **Potvrzovací dialog tu není**, na rozdíl od kontaktu ze sponky: tam
  odchází ťuknutím na řádek, tady až tlačítkem pod kartou, která jméno i číslo ukazuje.
  - **Tlačítko výběru říká „Poslat", ne „Přeposlat"** (`ChatPicker.confirmLabel`,
    `confirmIcon`). Dokud bylo napevno z přeposílání, lhalo o tom, co se stane.
  - Ověřeno na emulátoru sondou (položka v profilu testovacího bota, vlastní kontakt do
    Uložených zpráv — karta dorazila i s „Napsat zprávu"); bez sondy se u bota nenabízí.

### Úprava skupiny

„Upravit" v ⋮ profilu otevře `EditChatRoute` (`EditChatScreen`, `EditChatViewModel`): fotka,
název, popis a výchozí práva členů.

- **Dvě práva, ne jedno** (`ChatEditRights`, pravidlo `chatEditRights` ve `WriteAccess.kt`, testy
  `WriteAccessTest`). Název, popis a fotka stojí na `canChangeInfo`; výchozí práva členů na
  `canRestrictMembers`, protože to vyžaduje `setChatPermissions`. Správce může mít jen jedno
  z nich, takže se o každém bloku obrazovky rozhoduje zvlášť — a položka v menu se nabídne, když
  je aspoň jedno.
- **Rozhoduje vlastní postavení, ne typ chatu** — stejně jako u psaní (`writeAccess`). Vlastník
  smí všechno; správce to, co má v právech; běžný člen mění informace jen tam, kde to dovolují
  výchozí práva skupiny (`Chat.permissions.canChangeInfo`), a práva členů nikdy; omezený člen se
  řídí svými vlastními právy, která ta výchozí přebíjejí.
- **V kanálu se práva členů nenabízejí.** Nikdo do něj nepíše, takže není co omezovat — a TDLib
  by `setChatPermissions` stejně odmítl.
- **Fotka a práva se ukládají HNED, texty až tlačítkem Uložit.** Fotka má vlastní nahrávání
  a přepínač se musí pohnout na ťuknutí; posílat text po písmenech nemá smysl. Stejné dělení
  jako u úpravy vlastního profilu.
- **TDLib na změnu `ChatPermissions` update NEPOSÍLÁ**, takže je obrazovka jediný zdroj pravdy:
  přepínač se pohne hned a při selhání se vrátí — jako v nastavení archivu.
- **Práva se zapisují CELÝM objektem**, takže co aplikace nenabízí (hudba, video kolečka, ankety,
  náhledy odkazů, štítky, témata), se přenese z aktuálního stavu. Nastavit to mohl jiný klient
  a přepsat mu to tiše by bylo horší než to nenabízet.
- **Nabízí se jen ty druhy obsahu, které aplikace umí poslat** — tytéž jako `ContentAccess`.
  Přepínač u něčeho, co se z aplikace stejně poslat nedá, by sliboval vliv, který nemá.
- **Název a popis jsou dvě volání.** Když první selže, druhé se neposílá a co prošlo, se zapíše
  jako výchozí stav — další pokus to neposílá znovu. Přesně jako u vlastního profilu.
- **Fotka jde přes CESTU** (`InputChatPhotoStatic` + `InputFileLocal`), ne přes systémové URI —
  TDLib jiné nepřijme, stejně jako u příloh. Ořezává se na čtverec podle výřezu z ořezu
  (`SquarePhoto`, viz „Ořez profilové fotky"), protože se kreslí v kruhu. Odebrání je `setChatPhoto(photo = null)`; jiné volání na to není.
- **Úprava má vlastní routu v OBOU větvích** (`EditChatRoute`, `ArchiveEditChatRoute`). Scéna se
  určuje metadaty, takže jedna routa nemůže patřit hlavnímu seznamu i archivu — stejně jako
  u konverzace a profilu.
- **Zpět z úpravy vrací profil, ne chat.** Chaty mají `PopUntilContentChange` (viz „Navigace"),
  takže se dvoupanelová scéna nepřeskočí celá; kdyby tam bylo výchozí chování, zpět by odešlo
  rovnou z profilu i z konverzace.
- **Ověřeno bez zásahu do skupiny**: položka v menu u vlastníka, její nepřítomnost v soukromém
  chatu, načtení názvu, popisu, fotky a všech práv a neaktivní Uložit, dokud se nic nezmění.
- **Uložení ověřené v testovací skupině** („Plume test zakladni", Bot API `getChat`): popis
  i výchozí práva členů (fotky vypnout a zapnout). **Bot API práva skupiny vrací se zpožděním
  několika vteřin** — hned po přepnutí ukazuje starou hodnotu a vypadá to, že se nic neuložilo.
  Stejně tak `adb input text` s `%s` na pomalém emulátoru useklo konec popisu („Testovaci
  skupina p"): obsah pole se proto čte dumpem PŘED uložením, ne až ze serveru.

### Správa členů

Dlouhý stisk na členovi v záložce Členové otevře nabídku (`MemberActionsSheet`): napsat zprávu,
štítek, povýšit na správce / upravit jeho práva, sesadit, omezit / upravit omezení, zrušit
omezení, předat vlastnictví, vyhodit. Práva správce a omezení mají vlastní obrazovku
(`MemberRightsScreen`, routy `MemberRightsRoute` / `ArchiveMemberRightsRoute`). Pravidlo, co
uživatel s kterým členem smí, je čistá funkce `memberActions` (`core/telegram/MemberManagement.kt`,
testy `MemberManagementTest`).

- **Ťuknutí otevírá konverzaci dál, správa je dlouhý stisk** — jako akce nad chatem v seznamu.
  Kdyby ťuknutí otevíralo nabídku, zmizela by nejčastější cesta „napsat tomuhle člověku".
- **Nabídka se neotevře, když nemá co ukázat** (`MemberActions.any`). Běžnému členovi by se
  jinak otevřel prázdný sheet a vypadalo by to jako chyba.
- **Meze jsou ověřené v knihovně, ne odhadnuté** (`strings libtdjsonjava.so`): „Can't restrict
  users in basic group chats" (omezovat jde jen v supergroupě; v kanálu nikdo nepíše, takže tam
  není co omezovat), „Can't change owner in basic group chats", „Can't promote or demote self",
  „Can't restrict self" a **„Can't edit chat administrators"** — cizího správce, kterého jsem
  sám nepovýšil, měnit nesmím. To poslední se pozná z `ChatMemberStatusAdministrator.canBeEdited`;
  vlastníka ho neomezuje.
- **Sesazení není vlastní volání, jsou to prázdná práva** (`AdminRights.any == false` →
  `ChatMemberStatusMember`). Tak to bere TDLib, takže „Sesadit" v nabídce je zkratka k témuž
  uložení.
- **Vyhození jde přes `banChatMember`, ne `setChatMemberStatus`** — jen ono umí dobu a rozhodnutí
  o zprávách. `revokeMessages = false` vědomě: mazání celé historie je jiná věc a v nabídce hned
  vedle vyhození by se na něj dalo ťuknout omylem.
- **V základní skupině je vyhození jen odebrání**, vyhozeného může kdokoli přidat zpátky; ze
  supergroupy a kanálu se sám nevrátí. Text potvrzení je proto pro každý případ jiný — jeden
  společný by lhal.
- **Práva se ukládají TLAČÍTKEM, ne po jednom přepínači** — obráceně než výchozí práva skupiny
  v `EditChatScreen`. Každý přepínač by byl vlastní `setChatMemberStatus`, tedy série změn
  postavení na serveru, a u omezení se zároveň vybírá doba, která bez přepínačů nedává smysl.
- **U už omezeného člena je první volba doby „ponechat"** se zbytkem běžícího omezení. Bez ní by
  změna jediného přepínače u člověka omezeného na týden přepsala konec na „navždy" a nebylo by
  z čeho to poznat. Je to **vlastní příznak `keepsCurrentEnd`, ne porovnání `duration` s tím, co
  zbývá**: zbytek může vyjít přesně na některou z nabízených dob a obě volby by se pak tvářily
  jako vybrané.
- **„Ponechat" posílá PŘESNĚ původní konec** (`restrictMember(keepUntil)`, `keptUntilDate`,
  testy `MemberManagementTest`), ne „teď + zbytek". Přepočet posunul konec o čas strávený na
  obrazovce (ověřeno Bot API: +19 s) a omezení, kterému zbývalo pod 30 s, by udělal trvalým —
  tak krátkou dobu bere TDLib jako navždy. Takové se posune těsně za tu mez.
- **Doby se drží pod 366 dny.** Všechno delší (a kratší než 30 sekund) bere TDLib jako navždy,
  takže by delší volba slibovala konec, který nenastane. Navždy je `untilDate = 0`.
- **Práva správce jsou zúžená sada a zapisují se celým objektem** (jako `GroupPermissions`): co
  aplikace nenabízí (videohovory, stories, témata, přímé zprávy kanálu), se u stávajícího správce
  přenese z jeho současných práv — nastavit mu to mohl jiný klient. `canManageChat` drží podle
  toho, jestli správcem vůbec zůstává; v Telegramu je to implicitní právo každého správce.
- **Skupina a kanál ukazují každý jinou podmnožinu.** V kanálu se publikuje a upravují cizí
  příspěvky, ve skupině se připíná a omezuje; anonymita je jen v supergroupě.
- **Seznam členů se po změně ČTE ZNOVU, neodvozuje se z updatu** (`ProfileRepository.memberUpdates`
  nad `UpdateChatMember`, `conflate`). Jeden update nese jednoho člena a co změna doopravdy
  udělala, ví jen server — tentýž vzor jako připnuté zprávy a relace v Nastavení. **`UpdateChatMember`
  chodí jen správcům**, tedy právě těm, kdo členy mění; komu by k ničemu nebyl, ten ho nedostane.
- **Na změnu člena se čte znovu celý profil, ne jen seznam** — jinak hlavička po vyhození dál
  hlásila „2 členové". Samotné čtení ale nestačí: počet nese `SupergroupFullInfo`, který má
  TDLib v keši až minutu, takže hned po vyhození vrátí starý. Opraví ho až
  `UpdateSupergroupFullInfo` (u základní skupiny `UpdateBasicGroupFullInfo`), a proto ho
  `memberUpdates` odebírá taky. Ověřeno: „1 člen" do osmi vteřin po vyhození, bez odchodu
  z profilu.
- **Jestli jde předat vlastnictví, se ptáme PŘED otevřením dialogu** (`canTransferOwnership`).
  Bez zapnutého dvoufázového ověření (`PasswordNeeded`) nebo s čerstvým heslem či relací
  (`PasswordTooFresh` / `SessionTooFresh`) to Telegram nedovolí a uživatel by psal heslo zbytečně.
  Vlastníkem se navíc nestane bot.
- **Heslo NENÍ `rememberSaveable`.** Uložený stav se serializuje a přežil by i zabití procesu;
  heslo žije jen v dialogu a odchází rovnou do TDLib.
- **Po převodu se profil čte znovu** — vlastníkem je někdo jiný, takže se změnila i naše vlastní
  práva a s nimi celá nabídka.
- **Koin čte parametry INDEXEM** (`parameters.values[0]`), protože `chatId` i `userId` jsou `Long`
  a `get()` bere parametr podle typu — zaměnilo by je. Tatáž past jako u `messageId` konverzace.
- **`var duration` a `fun setDuration(…)` se v JVM srazí** („Platform declaration clash"): property
  si generuje setter téhož jména. Funkce se proto jmenuje `chooseDuration`.
- **Nabídka je CELÁ rozbalená a ještě scrollovatelná** (`enabledValues = Hidden, Expanded`).
  S výchozím sheetem skončilo „Vyhodit" pod spodní hranou obrazovky — u položky, která je
  poslední a nejzávažnější, to vypadá, že vyhodit člena vůbec nejde. Tatáž past jako u výběru
  samolepek a nastavení archivu; osm položek se do napůl rozbaleného sheetu nevejde.
- **Ověřit to jde botem ve vlastní testovací skupině.** Vlastník sám sebe spravovat nemůže
  („Can't promote or demote self"), takže se vlastní účet k ničemu nehodí; bot přidaný do skupiny
  je plnohodnotný druhý člen, na kterém jde povýšení, omezení i vyhození vyzkoušet bez druhého
  uživatelského účtu. **Omezit bota ale nejde** — omezení je jen v supergroupě, takže skupina se
  musí nejdřív převést. Převod vlastnictví se takhle ověřit nedá vůbec: bot vlastníkem být nemůže.
- **Ověřeno na emulátoru (bez zásahu do skutečné skupiny):** nabídka se na vlastním účtu
  neotevře (`isSelf`), na správci ukáže právě těch sedm položek, které vlastník v supergroupě
  má; práva správce se načtou se skutečnými hodnotami a „Uložit" je zašedlé, dokud se něco
  nezmění; zpět bez uložení nic neposílá; dialog vyhození má předvybrané „Navždy" a text podle
  druhu skupiny; „Předat vlastnictví" na účtu bez dvoufázového ověření skončí vysvětlením,
  ještě než se zeptá na heslo.
- **Zápisy ověřené v testovací supergroupě s testovacím botem** („Plume test zakladni",
  kontrola přes Bot API `getChatMember`): omezení na den (zakázané fotky, `until_date` +24 h),
  úprava omezení s „ponechat" (konec beze změny), zrušení omezení, štítek i jeho smazání
  (`tag`), povýšení s výchozími právy a sesazení, vyhození na hodinu i navždy a návrat bota
  přes „Přidat členy". **Dokončený převod vlastnictví ověřený NENÍ** — bot vlastníkem být nemůže
  a účet nemá dvoufázové ověření.
- **Dlouhý stisk přes `adb` chce 1,5 s.** Se `swipe … 900` se gesto vyhodnotilo jako ťuknutí
  a místo nabídky se otevřela konverzace s tím členem — vypadalo to jako chyba `combinedClickable`.
  A `uiautomator dump` při běžící animaci selže („null root node returned"), **jenže nechá na
  místě STARÝ soubor**, takže další `cat` vypíše předchozí obrazovku a nová verze aplikace se
  tváří, že se nezměnila. Před každým dumpem proto `rm -f /sdcard/ui.xml`.

### Historie profilových fotek

Ťuknutí na velkou fotku v profilu chatu otevře prohlížeč médií se všemi profilovkami chatu
(`MediaRoute(profilePhotos = true)`, `MediaViewerViewModel.loadProfilePhotos`,
`ProfileRepository.profilePhotos`). Otevírá se vpravo na aktuální fotce, doleva starší.

- **Je to zdroj prohlížeče, ne nová obrazovka.** Přiblížení, video, galerie / celá obrazovka,
  uložení a sdílení prohlížeč umí; stačilo mu podat položky. Stejně jako u přílohy ankety
  (`notMessages`): stránky nejsou zprávy, takže se nenačítají reakce ani práva, neodebírají
  se úpravy a mazání a z akcí zbývá uložit a sdílet (`PROFILE_PHOTO_ACTIONS`). Klíčem stránky
  je id FOTKY; `messageId` v routě je 0.
- **Člověk a bot: `getUserProfilePhotos`**, po stránkách do `totalCount` (strop 300). Osobní
  fotka, kterou si uživatel kontaktu nastavil sám, ani veřejná fotka v něm podle TDLib nejsou.
- **Skupina a kanál historii fotek v TDLib NEMAJÍ.** Skládá se z aktuální fotky (`photo`
  z plných údajů skupiny) a ze servisních zpráv o změně fotky (`SearchMessagesFilterChatPhoto`
  → `MessageChatChangePhoto`), bez duplicit podle id. Fotka, jejíž servisní zprávu někdo smazal,
  proto chybí. Smyčka hledání končí i na prázdné stránce a na neměnném pokračování — jinak by se
  při chybné odpovědi dotazy točily donekonečna.
- **Velikosti se vybírají stejně jako u fotky ve zprávě** (`photoMedia`, sdílené s
  `Photo.toMedia`): `ChatPhoto` nese tytéž `PhotoSize`, jen v jiném obalu. Animovaná profilovka
  je video (`AnimatedChatPhoto`, čtvercové podle `length`, délku přehrávač zjistí sám) s fotkou
  jako náhledem.
- **Datum v liště je, kdy si chat fotku nastavil** (`addedDate`), a podle něj se jmenuje i
  uložený soubor.
- **Fotky uživatele se NEŘADÍ podle data, drží pořadí od serveru** (první = aktuální). Starší
  fotka nastavená znovu jako hlavní si nechává původní `addedDate`: dokud se řadilo podle data,
  zůstala po nastavení uprostřed a jako aktuální se dál tvářila ta předchozí. Podle data se
  řadí jen skupiny, kde se historie skládá ze zpráv o změně fotky.
- **Let z avataru do prohlížeče není.** Avatar v profilu už nese sdílený klíč letu z hlavičky
  konverzace (`chatAvatarKey`); druhý klíč na tomtéž prvku by se s ním pral. Prohlížeč se
  otevře běžným přechodem.
- **Bez fotky (iniciály) se na avatar ťuknout nedá** a Uložené zprávy profil nemají.
- **Vlastní profilovky se otevírají z úpravy profilu** (Nastavení → Obecné → Upravit profil,
  ťuknutí na fotku). Id účtu je id chatu Uložených zpráv, takže `profilePhotos` nic navíc
  nepotřebuje; kladné id bez známého chatu vezme rovnou jako uživatele.
- **Prohlížeč pak leží v zásobníku NASTAVENÍ, ne Chatů** — a zavírá se z toho, ve kterém leží
  (`chatsStack.pop(route) || settingsStack.pop(route)`). Dokud šlo zavírání jen přes zásobník
  Chatů, ✕ by v Nastavení nedělalo nic; systémové zpět to nepotřebuje, bere vybranou sekci.
- **Vlastní profilovky jde v ⋮ nastavit jako hlavní a smazat** (`setProfilePhoto`
  s `InputChatPhotoPrevious`, `deleteProfilePhoto`; `MediaViewerState.ownProfilePhotos` podle
  `getMe`). Jen u vlastního účtu — starší fotku skupiny znovu použít ani jednotlivě smazat TDLib
  neumí. „Nastavit jako profilovou" se u aktuální fotky nenabízí, smazání se potvrzuje dialogem
  a po smazání poslední fotky se prohlížeč zavře. Nastavená fotka se přesune na konec, takže
  pager na ni dojede sám (`focusMessageId`) — jinak by zůstal na indexu, tedy na jiné fotce.
- **Znovu nastavená fotka dostane NOVÉ id** (ověřeno sondou: …131 → …133). Hledat ji po
  nastavení podle id, se kterým se nastavovala, nejde; pager se proto posílá na aktuální
  (poslední) fotku.
- **Ověřeno na emulátoru** (česky, se souhlasem, na skutečném účtu): starší fotka nastavená
  jako hlavní (snackbar, „3 z 3" a pager na ní) a hned vrácená původní, nabídka bez
  „Nastavit…" u aktuální fotky. **Smazání ověřené jen po potvrzovací dialog** — mazalo by
  skutečnou fotku. **Že u cizího profilu nabídka chybí, ověřené na zařízení NENÍ** (otevřel by
  se profil skutečného člověka).
- **Ověřeno na emulátoru** (česky): člověk se třemi fotkami („3 z 3", listování ke staršímu
  datu), kanál s jednou fotkou („1 z 1"), uložení do galerie (`Pictures/Plume`, jméno podle
  data přidání), vlastní profilovky z úpravy profilu („3 z 3", ✕ i zpět vrací do úpravy
  profilu). **Animovaná profilovka ani skupina s několika změnami fotky ověřené nejsou.**

### Přidání členů

Pruh „Pozvat členy" nad čerstvou skupinou (`InviteMembersBar` z `ChatActionBarInviteMembers`)
a ⋮ → „Přidat členy" / „Přidat odběratele" v profilu otevřou `AddMembersRoute`
(`AddMembersScreen`, `AddMembersViewModel`): tentýž `ChatPicker` jako kontakty, víc lidí naráz,
přidává se tlačítkem s počtem. Kdo smí, je čistá funkce `canInviteMembers` (testy
`InviteMembersTest`), kdo se nabídne, `addMemberCandidates` (testy `AddMemberCandidatesTest`).

- **Pravidlo „kdo smí" je totéž jako u bota bez práv správce** (`canAddBot`): vlastník, správce
  s právem zvát, člen podle výchozích práv skupiny, omezený podle svých. **V kanálu členové
  nezvou nikdy** — `chat.permissions.canInviteUsers` tam nic neznamená.
- **Supergroup a kanál berou všechny naráz (`addChatMembers`), základní skupina jen po jednom**
  (`addChatMember`) a novému členovi rovnou přepošle posledních 100 zpráv (`forwardLimit`,
  `BASIC_GROUP_HISTORY`) — jinak by vstoupil do skupiny, ve které nic nevidí.
- **Odmítnutí kvůli soukromí NENÍ chyba.** Obě volání vrací `FailedToAddMembers` a přidání
  ostatních proběhne; obrazovka ty odmítnuté vyjmenuje a nabídne poslat jim pozvánku
  (`sendInviteLink`, s entitou odkazu z `getTextEntities` — server ji v textu sám neoznačí).
  Bez pozvánky (vidí ji jen správce s právem zvát) dialog jen řekne, koho přidat nešlo.
- **Kdo už členem je, se nenabízí** — ale jen z první stránky členů (200). Číst kvůli výběru
  tisíce členů nestojí za to; server už přidaného podruhé nepřidá.
- **Bot do KANÁLU nejde přidat jako odběratel** — `addChatMembers` celé volání odmítne s
  `USER_BOT` (ne jako odmítnutého v `FailedToAddMembers`), takže dřív selhalo přidání úplně a
  ukázala se jen obecná hláška. V kanálu se proto boti z výběru oddělí (`botsAmong`, `getUser`
  → `UserTypeBot`), lidé se přidají jako dřív a na boty se obrazovka zeptá: „Přidat bota jako
  správce?" s právy zveřejňovat, upravovat a mazat příspěvky (`setAdminRights` na nečlena bota
  rovnou přidá). Ve skupině bot jako člen jde, tam se nic nemění. Ověřeno na emulátoru: bot
  v testovacím kanálu jako `administrator` s `can_post_messages` (Bot API `my_chat_member`).
- **Ověřeno na emulátoru** v testovací skupině: pruh podstrčenou sondou, přidání testovacího bota
  (Bot API `getChatMember` → `member`, v konverzaci „Members joined the group"), bot se pak
  podruhé nenabídne, položka v ⋮ profilu. Dialog odmítnutých sondou (repository vrátil všechny
  jako odmítnuté) anglicky i česky a „Poslat pozvánku" — bot dostal odkaz s entitou `url`.
  **Skutečné odmítnutí kvůli soukromí a základní skupina ověřené NEJSOU** — chtělo by to
  přidávat skutečné lidi. Bota z testovací skupiny jde zase odebrat `leaveChat` přes Bot API.

### Veřejný odkaz v úpravě chatu

Úprava skupiny nebo kanálu má tentýž blok Soukromý / Veřejný jako založení (`ChatTypeSection`
v `core/ui`, sdílený s `CreateChatScreen`). Odkaz se ukládá tlačítkem Uložit spolu s názvem
a popisem (`ProfileRepository.setUsername` → `setSupergroupUsername`, prázdné = soukromý).

- **Jen vlastník** (`EditableChat.canChangeUsername`). Základní skupině se blok nabízí taky,
  uložení s odkazem ji ale napřed převede na supergroupu — viz „Převod základní skupiny".
- **Bere se `usernames.editableUsername`**, ne první aktivní jméno: sběratelská jména z Fragmentu
  (`collectibleUsernames`) se tudy měnit nedají a do pole nepatří.
- **Přepnutí na soukromý řekne předem, že se odkaz uvolní** (`chat_type_link_released`) — vrátit
  ho nemusí jít, když si ho mezitím vezme někdo jiný.
- **Dostupnost se ptá `checkChatUsername(chatId)` existujícího chatu**; současné jméno se
  nekontroluje (bylo by „obsazené" sebou).
- **Profil chatu se po návratu z úpravy čte znovu** (`ChatProfileViewModel.refreshOnReturn`).
  Data čte jen při otevření a TDLib změnu názvu, popisu ani odkazu profilu nehlásí — po uložení
  ukazoval staré hodnoty, dokud se neotevřel znovu. Platilo to i pro název a popis, jen to dřív
  nikdo nezkoušel na zařízení.
- **Ověřeno na emulátoru** na testovacím kanálu: načtení veřejného odkazu, varování
  o uvolnění, přejmenování (Bot API `getChat` najde nové jméno, staré ne) a přepnutí na soukromý
  (Bot API hlásí `CHANNEL_PRIVATE`), profil po návratu pokaždé aktuální. **Neověřeno**:
  veřejná skupina (kód je společný), iOS.

### Převod základní skupiny

Veřejný odkaz ani témata základní skupina nemá. V úpravě skupiny se vlastníkovi nabízejí obojí
a uložení odkazu (nebo zapnutí témat) se napřed zeptá a skupinu převede na supergroupu
(`upgradeBasicGroupChatToSupergroupChat`, `ProfileRepository.upgradeToSupergroup`). Plume samo
základní skupiny nezakládá (viz „Založení skupiny a kanálu"), týká se to skupin odjinud.

- **Převod dá NOVÝ chat s jiným id** a je nevratný. Stará skupina zůstane jako neaktivní chat
  (`BasicGroup.isActive == false`, `upgradedToSupergroupId`) s dosavadními zprávami a ze seznamu
  chatů zmizí; nová začíná oznámením „Skupina byla převedena". Oficiální klienti obě historie
  slévají, Plume ne — proto **oznámení vede do té druhé skupiny** (`ChatMessage.upgradeLink`:
  „Zobrazit starší zprávy" / „Otevřít novou skupinu") a stará skupina má místo pole tlačítko do
  nové (`ChatInfo.upgradedToChatId`). Potvrzovací dialog to říká předem.
- **Pruh „Pozvat členy" drží TDLib i u převedené skupiny** — konverzace ho tam schová, zvát se
  dá jen do nové.
- **Úprava zůstává otevřená a její ViewModel se přepne na nové id** (`EditChatViewModel.chatId`
  je `var`); ostatní obrazovky staré skupiny přepíše navigace (`replaceUpgradedChat`, testy
  `ChatsStackTest`). Nahradit i úpravu by zahodilo rozepsané texty a hlášku o výsledku.
- **Dostupnost jména se u základní skupiny ptá s `chatId = 0`**, jako při zakládání —
  `checkChatUsername` základní skupinu nebere.
- **Id druhé strany se skládá konvencí TDLib** (základní skupina −id, supergroupa
  `supergroupChatId`) v čisté funkci `upgradeLink`, ne dotazem v `toChatMessage`.
- **Ověřeno na emulátoru** na čtyřech testovacích skupinách (sondou `createNewBasicGroupChat`
  s testovacím botem): odkaz (Bot API `getChat @…` → `supergroup`) i témata, oznámení oběma
  směry, tlačítko ve staré skupině, nová skupina v seznamu hned po převodu. iOS jen přeložené.
- **Nová skupina po převodu v seznamu chatů chyběla až do restartu** — souběh updatů, viz
  „Seznam chatů": `UpdateChatPosition` doběhl dřív než `UpdateNewChat` a `upsert` ho přepsal.

### Diskuse ke kanálu

V úpravě kanálu je řádek „Diskuse" (`DiscussionSheet`): „Bez diskuse" a skupiny, které jde
připojit (`ChatRepository.suitableDiscussionChats` nad `getSuitableDiscussionChats`).
Připojení i odpojení je `ProfileRepository.setDiscussionGroup` (`setChatDiscussionGroup`,
prázdné = `0`). Pod příspěvky se pak objeví „Komentovat" a vlákno vede do připojené skupiny
(viz „Komentáře").

- **Smí správce kanálu s právem měnit údaje** (`EditableChat.canSetDiscussion`). Skupina musí
  být supergroupa, kde je uživatel vlastník nebo správce — tu podmínku hlídá server výběrem
  vhodných skupin, aplikace ji nepočítá.
- **Připojení se potvrzuje, odpojení ne.** Připojená skupina dostává všechny příspěvky a její
  historie bude vidět i novým členům; odpojení jen vypne komentáře a jde vrátit.
- **Propojený chat čte `SupergroupFullInfo.linkedChatId`** (`EditableChat.linkedChat`); řádek se
  změní až po potvrzení serverem.
- **Ve výběru jsou i skutečné skupiny uživatele** (na testovacím účtu „Kuřáci 2.0", „Testing
  group") — při ověřování ťukat jen podle textu z čerstvého výpisu.
- **Bot API `getChat` hlásí `linked_chat_id` s pár vteřinovým zpožděním** — po propojení ještě
  `None`, o chvíli později správně. Ověřovat s odstupem, jinak to vypadá jako nepovedené volání.
- **Automatická kopie příspěvku v diskusní skupině dorazí jako zpráva skupiny** a vyvolá
  notifikaci skupiny, i když uživatel zrovna čte kanál. Jestli ji oficiální klienti potlačují,
  ověřené není.
- **Ověřeno na emulátoru**: testovací kanál ↔ testovací skupina (Bot API `linked_chat_id` na obou
  stranách), příspěvek od bota s „Komentovat", komentář ve vlákně a „1 komentář" pod
  příspěvkem, odpojení (`linked_chat_id` zmizel) a znovu připojení.
- **Profil ukazuje propojený chat v Informacích** (`ChatProfile.linkedChat`): u kanálu „Diskusní
  skupina", u skupiny „Propojený kanál"; ťuknutí ho otevře. Řádek stojí za popisem — je to údaj
  o chatu, ne nastavení. Chat, který TDLib lokálně nezná (soukromý kanál, kde uživatel není),
  se vynechá. Ověřeno na emulátoru oběma směry. Odpojit jde jen z úpravy KANÁLU
  (`setChatDiscussionGroup` bere id kanálu a chce jeho správce).

### Pozvánky a žádosti o vstup

V úpravě skupiny (⋮ v profilu → Upravit) je blok Pozvánky se dvěma řádky: **Odkazy k pozvání**
(`InviteLinksRoute`, `InviteLinksScreen`) a **Žádosti o vstup** (`JoinRequestsRoute`,
`JoinRequestsScreen`) s počtem čekajících. Hranice je `InviteLinkRepository` v `core/telegram`,
pravidla čisté funkce (`inviteLinkStatus`, `InviteLinkDraft.normalized`, `InviteLinkEditor`,
testy `InviteLinkEditorTest`).

- **Spravovat smí vlastník a správce s `canInviteUsers`** (`ChatEditRights.canManageInvites`).
  Členové smějí zvát podle výchozích práv, ale odkazy spravovat ne — `createChatInviteLink` chce
  správce. Správce, který smí JEN zvát, dostane úpravu skupiny jen s tímhle blokem.
- **Odkaz jde sdílet systémovou nabídkou** (`MediaSharer.shareText`): na kartě hlavního odkazu
  je „Sdílet" hlavní tlačítko a kopírování ikona vedle (tři textová tlačítka se česky na
  telefon nevešla), u dalších odkazů je „Sdílet" první položka nabídky. Odkaz se zakládá, aby
  šel někomu poslat — i mimo Telegram. Ověřeno na emulátoru: nabídka se otevře s adresou,
  zpět vrátí do Plume; skutečné odeslání se nezkoušelo.
- **Seznam odkazů je vlastní** (`getChatInviteLinks` bere tvůrce): hlavní odkaz, další
  odkazy a zrušené. **Odkazy ostatních správců vidí jen vlastník** — sekce pod vlastními
  (`getChatInviteLinkCounts`, ostatním správcům vrací chybu, a to je prázdná sekce, ne
  selhání) a ťuknutí na správce otevře sheet s jeho odkazy. Vlastník je smí zrušit a smazat,
  **upravit ne** — nastavení patří tomu, kdo odkaz založil, takže nabídka nad cizím odkazem
  „Upravit" nemá.
- **Odkazy bota smí zrušit a smazat jen bot sám.** Vlastníkovi server odpoví
  `EDIT_BOT_INVITE_FORBIDDEN`; sheet bota proto nabízí jen sdílet a kopírovat a řekne proč
  (`AdminInviteLinks.isBot`). Našlo se to až zkouškou — z dokumentace to poznat nejde.
- **Počty u správce se můžou chvíli rozcházet se seznamem.** Hned po založení dvou odkazů hlásil
  server u bota 1 platný, jeho seznam vrátil dva (hlavní a založený), po pár minutách 2. Pravdu
  má seznam, který se čte při otevření sheetu.
- **Chyba akce v sheetu se ukazuje v sheetu** (`AdminLinksView.error`), ne snackbarem —
  ten by zůstal pod modálním sheetem a selhání by vypadalo jako ťuknutí, které nic neudělalo.
  Přesně tak se schoval `EDIT_BOT_INVITE_FORBIDDEN` při první zkoušce.
- **Kdo se přidal** (`getChatInviteLinkMembers`, stránky po 100 přes `offsetMember`): sheet
  s tváří, jménem, dnem vstupu a „přes sdílenou složku". Na kartě hlavního odkazu je tlačítko
  „Kdo se přidal · N" (nabídku nad sebou karta nemá), u dalších odkazů položka nabídky —
  i u zrušeného, lidé ve skupině zůstávají. Jen s `memberCount > 0`.
- **Dialog zrušení odkazu má „Ponechat", ne „Zrušit"** — česky by byla obě tlačítka „Zrušit".
- **Po každé změně se seznam čte znovu**, nic se nepřepisuje lokálně — zrušení hlavního odkazu
  vyrobí na serveru nový a jeho adresu zná jen server. Hlavní odkaz se proto „vyměňuje", ne ruší.
- **Se schvalováním se limit nesmí poslat** — TDLib `member_limit` u odkazu se žádostmi odmítne.
  Koncept ho zahodí sám (`normalized`) a formulář limit se schvalováním schová.
- **Platnost a limit jsou volby, ne čísla** (hodina / den / týden / napořád, bez limitu / 1 / 10
  / 100). „Za den" se počítá až při uložení (`toDraft(now)`), ne při otevření formuláře. Úprava
  odkazu s koncem nabídne „Ponechat"; neobvyklý limit nastavený jinde se do voleb přidá.
- **Vypršelý a naplněný odkaz server sám nezruší**, jen přestane pouštět dovnitř; bez
  `inviteLinkStatus` by v seznamu vypadal jako platný.
- **Nový odkaz se rovnou zkopíruje** — vytváří se proto, aby se poslal dál. Sdílení textu
  aplikace zatím nemá (`MediaSharer` sdílí jen soubory).
- **Vyřizovaná žádost z řádku hned zmizí** (`processing`) a seznam se čte znovu až po vyřízení —
  jinak by na okamžik naskočila zpátky. Seznam se čte znovu i na `UpdateChatPendingJoinRequests`
  (žádost vyřídil jiný správce). Hromadné přijetí a odmítnutí je za ⋮ s potvrzením: týká se
  i žádostí, které na obrazovce nejsou (`processChatJoinRequests` s prázdným odkazem).
- **Ověřeno na emulátoru** v testovací skupině (založené sondou `createNewSupergroupChat`, Plume
  zakládat skupiny neumí): vytvoření odkazu s platností a limitem, úprava na schvalování
  (limit zmizel, platnost zůstala), zrušení s potvrzením, smazání zrušených, prázdné žádosti,
  anglicky i česky. **Skutečná žádost o vstup ověřená NENÍ** — bot se přes pozvánku nepřidá,
  chce to druhý účet; seznam žádostí jen podstrčenými daty a vyřízení vůbec.
- **Odkazy ostatních správců ověřené skutečnými daty**: testovací bot je v testovací skupině
  správce s právem zvát a odkazy si zakládá a ruší přes Bot API (`createChatInviteLink`,
  `revokeChatInviteLink`). „Kdo se přidal" s lidmi jen sondou (přidat se odkazem chce druhý
  účet), prázdný seznam skutečně.

#### Pruh s čekajícími žádostmi

Nad konverzací skupiny nebo kanálu, kde jsou žádosti o vstup, je pruh „5 žádostí o vstup"
s tvářemi posledních žadatelů a jmény (`JoinRequestsBar`, `InviteLinkRepository
.pendingJoinRequests` → `PendingJoinRequests`). Ťuknutí otevře Žádosti o vstup, ✕ ho schová.

- **Kdo pruh vidí, rozhoduje TDLib**: `Chat.pendingJoinRequests` dostane jen správce, který
  žádosti smí vyřizovat — aplikace práva neřeší.
- **✕ schová pruh, dokud nepřibude další žádost** (pamatuje se počet, `rememberSaveable` podle
  chatu). Napořád by správce o nové žádosti nevěděl, při každém otevření chatu by zase ukazoval
  totéž.
- **Tvarem a sklem jako videochat a připnuté**, hned pod pruhem spamu — obojí čeká na
  rozhodnutí. Ve vlákně komentářů není, v tématu skupiny ano (žádosti patří skupině).
- **Skupina s tématy ho má i nad seznamem témat** — otevírá se jím, takže by správce o žádostech
  věděl až po otevření tématu. Tam je to první blok seznamu nad hledáním (`floating = false`:
  bez stínu a vlastního odsazení, plocha `surfaceContainerHigh`), ne plovoucí pruh — seznam
  témat nad sebou nic nescrolluje a sklo by nemělo co rozostřit. ✕ platí pro každou obrazovku
  zvlášť.
- **Ověřeno sondou** (podstrčený počet a žadatelé pro testovací skupinu): pruh v tématu
  General, ťuknutí na žádosti, ✕. Skutečná žádost chce druhý účet.

#### Povýšení na správce s výchozími právy

Při povýšení jsou předvyplněná výchozí práva (`AdminRights.Default`) a „Uložit" bylo zašedlé:
porovnání s `MemberDetail.adminRights` vyšlo „beze změny", protože u nesprávce nese právě tu
výchozí nabídku. **Povýšit šlo jen s upraveným přepínačem.** Povýšení je proto změna i bez
úprav (`MemberRightsViewModel.changed`). Našlo se to až povýšením bota v testovací skupině —
předchozí ověření správy členů skutečné uložení nezkoušelo.

### Sdílená média v profilu

Pod hlavičkou profilu jsou záložky: **Členové** (kde je server vydá), **Média**, **Soubory**
a **Odkazy**. Obsah jde přes `searchChatMessages` s filtrem
(`SearchMessagesFilterPhotoAndVideo` / `Document` / `Url`), ne procházením historie — sdíleného
obsahu bývá zlomek zpráv.

- **Nabízejí se jen neprázdné záložky.** Počty se čtou naráz (`getChatMessageCount` na všechny
  druhy současně) a teprve podle nich se záložky vykreslí; prázdná záložka by slibovala obsah,
  který chat nemá. Dokud počty nedorazí, nejsou žádné — záložka, která by po chvíli zmizela,
  vypadá jako chyba.
- **Profil kvůli tomu NENÍ `LazyColumn`.** Obsah záložky je sám svislý seznam a dva svislé
  scrolly do sebe Compose nezměří, když vnitřní dostane nekonečnou výšku (`Vertically scrollable
  component was measured with an infinity maximum height constraints`). Profil je proto
  `verticalScroll` sloupec (hlavička, záložky, pager) a **pager má PEVNOU výšku** = plocha bez
  záložek. Vnější scroll tím sahá přesně o výšku hlavičky a **záložky se přilepí samy**; žádný
  `stickyHeader`.
- **Hlavička se musí dát táhnout prstem.** První verze kreslila hlavičku PŘES stránky a posouvala
  ji jen `NestedScrollConnection` ze seznamu pod ní: tah přes fotku nebo údaje nedělal nic
  a u krátké záložky (tři členové) nebylo čím hlavičku sbalit. Teď ji nese vnější scroll.
- **Scroll dolů si napřed vezme hlavička** (`collapseFirst` na pageru, `dispatchRawDelta` do
  vnějšího `ScrollState` v `onPreScroll`). Nahoru to platí samo: vnější scroll dostane jen to,
  co seznam nespotřeboval, tedy až po dojetí na začátek — i u flingu. Ověřuje se `input swipe`:
  malý tah dolů v odscrollované mřížce hýbe mřížkou, hlavička zůstává sbalená.
- **Dotažení další stránky NESMÍ stát na `layoutInfo`.** Porovnání indexu poslední viditelné
  položky s počtem se u mřížky minulo a další stránka nedorazila NIKDY — u seznamu s jedinou
  položkou naopak prošlo hned, takže vada byla vidět jen na mřížce. Spouští ho proto kompozice
  jedné z posledních položek (`LoadMoreAtEnd`): skládá se, teprve když je blízko viewportu.
- **Konec pozná jen prázdná odpověď od serveru**, ne počet po filtrování: stránka samých
  mizejících fotek vyjde prázdná i uprostřed chatu.
- **Mizející a tajná média se vyřazují** stejně jako z listování v prohlížeči — trvalý přehled
  není místo pro obsah, který má po zhlédnutí zmizet.
- **Dlaždice mřížky nese sdílený prvek, ale s JINÝM klíčem než bublina v konverzaci**
  (`LocalSharedMediaFlight`, který podle `MediaRoute.fromSharedMedia` poskytuje i prohlížeč).
  Prohlížeč z dlaždice vyroste a při zavření se do ní vrátí, i na široké ploše (`inTwoPane`
  jako u bubliny — prohlížeč nahradí celý dvoupanel). **Se společným klíčem se ty dvě strany spárovaly mezi sebou** —
  tady stávalo, že „konverzace pod profilem je nesložená, takže se o klíč nic nepře", jenže při
  VÝMĚNĚ obrazovek jsou složené obě, a otevření profilu (i návrat z něj) je výměna jako každá
  jiná. Fotka proto při každém otevření profilu přelétla přes celou obrazovku z bubliny na
  dlaždici a zpátky. **Obecně: „druhá strana zrovna není složená" o sdíleném prvku nic neříká,
  protože přechod je právě ten okamžik, kdy složené jsou obě.**
- **Obrázek a video poslané jako soubor se ze záložky Soubory otevřou v prohlížeči**, jako
  v konverzaci (`opensInViewer`; co prohlížeč neumí, dostane dál systém). Prohlížeč takovou
  zprávu ukáže samotnou, bez listování — dokument ve výřezu fotek a videí není. Ověřeno na
  emulátoru (JPEG od testovacího bota, zpět vrací do záložky).
- **Ťuknutí vede tam, kam z konverzace**: médium do prohlížeče (táž `MediaRoute`), soubor se
  stáhne a předá systému (včetně průběhu v řádku a druhého ťuknutí jako zrušení), odkaz do
  prohlížeče.
- **Titulek „null" posílá server doslova** (u `share.google/…` a podobných). `ifBlank` ho
  nechytil a stál v záložce Odkazy i v bublině; texty náhledu jdou proto přes `previewText`
  (`MessageContent.kt`, testy `LinkPreviewTextTest`), který ho zahodí jako prázdný.
- **Soubory, odkazy a hudba mají nad seznamem pole hledání** (`SharedTabFrame`, `searchShared`,
  pravidlo `offersSharedSearch`, přechody `SharedMediaTab`, testy `SharedMediaTabTest`). Hledá
  server: `searchChatMessages` s dotazem a TÍMŽ filtrem jako záložka — název souboru, titulek
  skladby, text zprávy s odkazem. **Média hledání nemají**: fotka se dá najít jen podle popisku,
  který většina nemá, a „Nic nenalezeno" nad mřížkou plnou fotek by vypadalo jako chyba.
  **Pole je až od devíti položek** (`SHARED_SEARCH_MIN_COUNT`) — nad třemi soubory by jen
  ubíralo místo; rozepsané hledání se neschová. Pauza 300 ms a zrušení předchozího dotazu jako
  u členů; **stránka patří dotazu i místu** (`withNextPage` porovná dotaz a `nextFromMessageId`),
  jinak by pomalá stránka ze starého hledání vmíchala cizí výsledky.
- **Další stránka se čte od poslední VRÁCENÉ zprávy, ne od poslední ukázané**
  (`SharedMediaPage.nextFromMessageId`). Stránka samých mizejících fotek je po odfiltrování
  prázdná; pokračovat od poslední ukázané by vrátilo tutéž stránku a dotahování by se zaseklo.
- **Ověřeno na emulátoru** v chatu s Notifikátorem: „klip" v Souborech našel `klip.mp4`,
  nesmysl ukázal „Nic nenalezeno", ✕ vrátil celý seznam, „meet" v Odkazech našel zprávu podle
  textu, Hudba s pár skladbami pole nemá.
- **`mailto:` se v adrese neukazuje** (`displayUrl`, testy `SharedMediaTest`). Filtr odkazů bere
  i e-maily a schéma u nich nic neříká; `https` od `tg` odlišit potřeba je, takže se zahazuje
  jen ono.

## Mini aplikace botů

Tlačítko pod zprávou (`web_app`), tlačítko vlastní klávesnice bota, tlačítko nad výsledky inline
bota, tlačítko nabídky bota, dlaždice „Otevřít" v profilu bota a odkazy `t.me/bot?startapp`
a `t.me/bot/app` otevřou mini aplikaci přes celou obrazovku (`WebAppRoute`, `WebAppScreen`, `WebAppViewModel`).
Adresu i podepsaná data uživatele skládá TDLib (`WebAppRepository`); aplikace stránku jen
zobrazí a zprostředkuje její události.

- **`core/webview` je hranice** jako `core/camera`: `android.webkit` a `platform.WebKit` jen tam.
  WebView stránce vystavuje JEDINOU věc, `window.TelegramWebviewProxy.postEvent(typ, data)` —
  Android `addJavascriptInterface`, iOS stejně pojmenovaný objekt vložený skriptem na začátku
  dokumentu, který posílá do `WKScriptMessageHandler`. Soubory zařízení vypnuté, jiná schémata
  než http(s) stránka sama neotevře (má na to `web_app_open_link`).
- **Události překládá čistý most** (`WebAppBridge.kt`, testy `WebAppBridgeTest`), WebView data
  jen přenáší. Zpět se doručují `Telegram.WebView.receiveEvent(typ, data)`; typ i data jdou do
  skriptu jako JSON literály, nic se neescapuje ručně.
- **Na co stránka čeká odpověď, na to se odpoví, i když to Plume neumí** (telefon, zápis, faktura,
  celá obrazovka, plocha): „zrušeno" / „nepodporováno". Jinak by mini aplikace visela.
  Schránku mini aplikacím nedáváme (`data: null`).
- **Motiv jsou barvy aplikace** (`webAppTheme`: stránka `surface`, sekce `surfaceContainerLowest`
  jako bloky seznamů, tlačítka `primary`) — do adresy přes TDLib i do `theme_changed` po změně
  motivu. Hlavička a hlavní tlačítko jsou naše, přebarvit je smí stránka; popředí se pak volí
  podle světlosti.
- **`applicationName` je `android` / `ios`**, TDLib ho dá stránce jako `tgWebAppPlatform`. Vlastní
  jméno by mini aplikace nepoznaly.
- **Poprvé u každého bota souhlas** (`WebAppConsentDialog`, `AppSettings.confirmedWebAppBots`) —
  TDLib u `openWebApp` předepisuje jednou ukázat, že mini aplikace posílá data botovi.
  **Ptá se obrazovka mini aplikace, ne místo, odkud se otevírá** (`WebAppViewModel.start`, nic
  se nenačte, dokud se nepotvrdí; „Zrušit" obrazovku zavře). Dokud se ptala konverzace, musel by
  si souhlas hlídat každý nový vstup zvlášť — profil bota i odkaz by na něj zapomněly. Dialog
  čeká na jméno bota (lokální `getUser`), jinak by na okamžik jmenoval „Mini aplikaci".
- **Spuštění přes `openWebApp` se musí zavřít** (`closeWebApp` v `onCleared` přes `appScope`).
  Klávesnice a inline tlačítko jdou přes `getWebAppUrl` a spuštění nemají; **data botovi**
  (`web_app_data_send`) smí poslat jen mini aplikace z vlastní klávesnice (`keyboardButtonText`).
- **Zpět jde napřed do mini aplikace**, když má vlastní „zpět" (šipka v hlavičce místo ✕),
  jinak zavírá — s potvrzením, když si ho stránka vyžádala (`need_confirmation`).
  `NavigationBackHandler`, ne `BackHandler`.
- **Odkaz na Telegram z mini aplikace** (`web_app_open_tg_link`) ji zavře a otevře chat nad
  konverzací pod ní (`WebAppRoute.archive` říká, do které větve); jiné druhy odkazů jdou do
  prohlížeče.
- **Tlačítko nabídky bota** (`BotInfo.menuButton`, `BotMenuApp`, `BotMenuAppButton`) stojí
  u pole MÍSTO tlačítka příkazů a otevírá mini aplikaci přes `openWebApp`, jako tlačítko pod
  zprávou — TDLib adresu z `botMenuButton` bere právě tam. Jen v soukromém chatu s botem;
  čte se jednou při otevření chatu, jako příkazy.
  - **V prázdném poli plná pilulka s textem od bota, při psaní (a s přílohami) jen ikona** —
    text bývá dlouhý a na úzkém telefonu by poli ukousl místo, zrovna když se píše. Sbalená má
    barvu ostatních ikon v kartě, ne plnou plochu: vedle plného odesílacího tlačítka by se
    dvě sytá kolečka prala. Text se zkracuje na 120 dp.
  - **Adresa typu `t.me/bot/app` se otevírá jako odkaz** (`BotMenuApp.isLink`), ne přes
    `openWebApp` — tak to TDLib u tlačítka předepisuje.
  - **Adresa tlačítka NENÍ adresa stránky**, ale `menu://<adresa>` (ověřeno sondou). Server ji
    pošle takhle a `openWebApp` si ji přeloží sám. **Odkaz `t.me/bot/app` nastavený přes Bot API
    server zahodí** — přijde holé `menu://` a `openWebApp` skončí `URL_INVALID`. Cestu
    s `isLink` proto Bot API ověřit nejde (asi ji vyrobí jen BotFather); stojí na dokumentaci.
  - **Ověřuje se `setChatMenuButton` botem** s `chat_id` vlastního účtu (jen pro náš chat,
    ne globálně) a po ověření vrátit `{"type":"default"}`. Ověřeno na emulátoru (Fold, světlý
    i tmavý motiv): pilulka, sbalení s konceptem, souhlas a otevření ukázky `cafe`.
- **Hlavní mini aplikace bota** (`getMainWebApp`, `WebAppRoute.main`): dlaždice „Otevřít" v profilu
  bota (`ChatProfile.hasMainWebApp` z `UserTypeBot.hasMainWebApp`), první v řadě — u bota
  s aplikací je to to, kvůli čemu do profilu přišel. A odkaz `t.me/bot?startapp=…`
  (`InternalLink.MainWebApp`, parametr jde do `getMainWebApp`).
  - **Spuštění nemá, takže se nezavírá** — `getMainWebApp` vrací jen adresu.
  - **Bot bez hlavní mini aplikace se z odkazu otevře jako chat**, ne chybou; tak to dělají
    i oficiální klienti. Režim z odkazu (kompaktní / celá obrazovka) se neřeší, Plume kreslí
    vždy přes celou obrazovku. Panel s podmínkami botů v nabídce příloh (`getAttachmentMenuBot`)
    taky ne — nabídku příloh botů Plume nemá.
  - **Odkaz na jinou mini aplikaci z mini aplikace otevře chat jejího bota**, ne druhou mini
    aplikaci nad první — vrstvené by zpět mátlo.
  - **Ověřuje se na @BotFatherovi** — má hlavní mini aplikaci (správa vlastních botů), testovací
    bot ji mít nemůže (nastavuje se jen v BotFatheru). V ní nic neměnit, jen otevřít a zavřít.
    Ověřeno na emulátoru: odkaz se souhlasem i bez něj, „Zrušit" vrátí do konverzace, dlaždice
    v profilu.
- **Pojmenovaná mini aplikace z odkazu `t.me/bot/app?startapp=…`** (`InternalLink.WebApp`,
  `WebAppRoute.shortName`): napřed `searchWebApp` (jestli ji bot má — jinak věta „neexistuje",
  ne obecná chyba), pak `getWebAppLinkUrl`. Spuštění nemá, nezavírá se.
  - **Ptá se i u potvrzeného bota, když to server chce** (`needsWebAppConsent`, testy
    `WebAppConsentTest`): `skipConfirmation = false` znamená, že odkaz může poslat kdokoli, a
    `requestWriteAccess` se rozhoduje pokaždé zaškrtávátkem „Dovolit … posílat mi zprávy"
    (výchozí zapnuté, jako u přihlášení na web).
  - **Ověřeno na emulátoru** s `t.me/DurgerKingBot/menu` (ukázka Telegramu, žádá o psaní —
    zaškrtávátko se při ověřování VYPÍNÁ, jinak smí bot účtu psát) a s neexistující
    `t.me/BotFather/neexistuje`.
- **Druhé tlačítko** (`web_app_setup_secondary_button`, `WebAppBottomBar`) stojí vůči hlavnímu,
  kde si stránka řekne: vlevo a vpravo sdílí řádek napůl, nahoře a dole jsou pod sebou;
  viditelné jen jedno jde přes celou šířku. Bez barvy od stránky je hlavní plné (`primary`)
  a druhé tonální (`secondaryContainer`) — dvě plná vedle sebe by neřekla, které je hlavní.
  Barvu lišty smí stránka změnit (`web_app_set_bottom_bar_color`: hex, nebo klíč z motivu
  v TÉMŽE poli — rozliší se podle „#").
  - **Žádná dostupná mini aplikace druhé tlačítko neukazuje** (Durger King i BotFather ho
    posílají s `is_visible: false`), takže se ověřovalo sondou: po `web_app_ready` ViewModel
    sám podstrčí události, jaké by poslala stránka, a projde všechny polohy. Necommituje se.
    Že ťuknutí dorazí do stránky, se pozná z logu skriptu Telegramu v konzoli WebView
    (`adb logcat | grep receiveEvent` → `secondary_button_pressed`).
  - **Server po prvním otevření pojmenované mini aplikace vrací `skipConfirmation`** a o psaní
    už nežádá — souhlas se zaškrtávátkem se tak ukáže jen napoprvé. Kdo ověřuje souhlas,
    potřebuje aplikaci, kterou účet ještě neotevřel.
- **Tmavá hlavička od stránky přepne ikony stavové lišty na světlé** (`DarkSystemBars`).
  BotFather si nastaví tmavou hlavičku i ve světlém motivu aplikace a tmavé ikony na ní nebyly
  vidět. Rozhoduje táž světlost, podle které se volí popředí hlavičky.
- **Ověřeno na emulátoru** s ukázkou Telegramu `webappcontent.telegram.org/cafe` (bot ji pošle
  jako `web_app` tlačítko): souhlas jen poprvé, motiv, hlavní tlačítko v barvě od stránky a změna
  jeho textu, vlastní „zpět" v hlavičce i systémovým gestem, potvrzení zavření s plným košíkem,
  otevření z vlastní klávesnice (ukázka tam hlásí „zavřeno" — objednávat dovolí jen s `query_id`
  z `openWebApp`, to je vlastnost ukázky). **Neověřeno**: popup, vlastní metody, `web_app_data_send`,
  odkaz na Telegram z mini aplikace a iOS (jen přeložené).

## Hlasovky

Přehrávají se přímo v bublině: tlačítko, waveform s průběhem (ťuknutí a tažení posouvá), čas,
tečka u neposlechnuté a u hrající čip rychlosti.

- **Hraje nejvýš jedna, pro celou aplikaci** (`VoicePlayer`, `single` v Koinu). Spuštění jiné
  tu předchozí zastaví. Zavření chatu hlasovku z něj zastaví (`onCleared`) — neměla by kde
  ukazovat průběh ani jak ji vypnout.
- **Engine si `VoicePlayer` NEVYRÁBÍ, dostane továrnu** (`newEngine`). Android potřebuje ke zvuku
  `Context` (audio focus) a `core/audio` ho nezná; dodá ho Koin jako `factory`, stejně jako
  u `AudioRecorder`. Proto je `AudioEngine` `expect class` BEZ konstruktoru.
- **Soubor se stahuje až při prvním přehrání.** Dopředu by to byla data za každou hlasovku
  v historii. Klíč je `chatId` + id zprávy — id zprávy je jedinečné jen v chatu.
- **Pozice je samostatný tok, ne součást `ConversationState`**, a do bubliny jde jako lambda,
  která se čte až ve `VoiceNoteContent`. Mění se dvacetkrát za vteřinu (`POSITION_TICK_MS`);
  v kompozici obrazovky by překreslovala celou konverzaci.
- **Waveform od Telegramu je po 5 bitech** (hodnoty 0–31, od nejnižšího bitu) — `decodeWaveform`,
  testy v `AudioFormatsTest`. Do bubliny se převzorkuje na 40 sloupců a vydělí stropem 31.
- **Waveform se NEŠKÁLUJE na špičku té konkrétní hlasovky.** Dřív ano — každá se roztáhla tak,
  aby její nejhlasitější místo sáhlo na strop —, jenže tím měla každá hlasovka v konverzaci jinak
  vysoké sloupce: hlasitá vyšla jako souvislý blok u stropu, tichá jako členitý tvar, a z waveformu
  se nedalo přečíst nic než „tahle je jiná než ta nad ní". Hodnoty od Telegramu jsou absolutní,
  takže stačí vydělit stropem. Hlídá to `tichaHlasovkaNeniRoztazenaNaStrop`: s normalizací vyjdou
  tichá i hlasitá ukázka SHODNĚ, takže test bez ní projde a s ní ne.
- **Na straně NAHRÁVÁNÍ se normalizuje dál** (`encodeWaveform`). Mikrofon vrací úroveň v měřítku,
  které si každá platforma určuje sama (Android `maxAmplitude`, iOS `averagePower` v dB), takže
  absolutní hodnota z něj nic neznamená a nenormovaná tichá nahrávka by odešla jako čára. Není to
  tedy protimluv: normuje se tam, kde je měřítko naše, a nenormuje tam, kde je od serveru.
- **Rychlost přehrávání je 1× / 1,5× / 2×** (`VoiceSpeed`), přepíná se čipem u času a platí pro
  CELOU aplikaci (uloženo v `AppSettings.voiceSpeed`). Tři hodnoty, ne posuvník: mezi 1,25 a 1,3
  nikdo nerozhoduje a ťuknutí musí vždycky někam dojít.
- **Čip rychlosti je jen u hrající hlasovky.** V každé bublině by z něj byl další ovládací prvek,
  a přitom se rychlost mění jednou za čas. Řádek s časem má šířku waveformu, takže se bublina
  objevením čipu nepřeskládá.
- **`MediaPlayer.playbackParams` na PAUZNUTÉM přehrávači přehrávání ROZJEDE.** Je to zdokumentované
  chování, ne chyba. Rychlost se proto drží v poli a zapisuje se buď v `start()`, nebo za běhu —
  jinak by přepnutí čipu u pozastavené hlasovky spustilo zvuk. Na iOS to neplatí, tam `rate` na
  pauznutém přehrávači nic nespustí; zato se `enableRate` MUSÍ nastavit PŘED `prepareToPlay()`
  a `play()` rychlost resetuje na 1, takže se zapisuje znovu hned po něm.
- **Po dohrání se pustí DALŠÍ neposlechnutá** (`VoicePlayer.completions` → `playNextUnlistened`).
  Bere se jen nejbližší novější hlasovka, a jen když je příchozí a neposlechnutá: řetězí se tím
  dávka hlasovek poslaná za sebou, což je ten případ, kvůli kterému to existuje. Přeskakovat
  k neposlechnuté někde dál v historii by překvapilo — uživatel si pustil jednu starou hlasovku
  a najednou mu hraje něco jiného.
- **Která je další, ví jedině konverzace**, ne přehrávač. `VoicePlayer` proto jen ohlásí, že
  hlasovka DOHRÁLA (ne že ji zastavil uživatel), a `ConversationViewModel` vybere následující.
- **Audio focus utne hudbu a hovor utne hlasovku.** Android `AUDIOFOCUS_GAIN_TRANSIENT` (ne
  `MAY_DUCK` — řeč pod ztlumenou hudbou se neposlouchá), iOS `AVAudioSessionInterruptionNotification`.
  **Po návratu focusu se NEPOKRAČUJE samo**: zvuk, který se rozezní bez ťuknutí, překvapí.
- **Přerušení se musí promítnout do stavu** (`AudioEngine.onInterrupted`). Přehrávač se zastaví
  sám, ale nikomu to neřekne — bez toho by hlasovka v UI dál „hrála" a posuvník by stál.
- **Tažení po waveformu tiká po SLOUPCÍCH, ne po pixelech** (`haptics.tick()` při změně indexu
  sloupce). Odezva na každý pohyb prstu by byla souvislé bzučení.

### Hlasovka u ucha

Přiložení telefonu k uchu přepne hlasovku z reproduktoru do sluchátka a zhasne displej; oddálení
ji vrátí zpátky. Sleduje se jen po dobu, kdy se HRAJE (`EarSensor`, startuje ho `VoicePlayer`).

- **Samotné přiblížení NESTAČÍ** (`raisedToEar`, testy `EarDetectionTest`). Senzor hlásí „zakryto"
  i telefonu položenému displejem dolů na stole — a tam by přepnutí znamenalo, že hlasovka ztichne
  a nikdo neví proč. Přidává se proto sklon z akcelerometru: u ucha je telefon zhruba nastojato
  (`|gravityZ| < 0,7 g`), na stole leží naplocho (přes 0,95 g).
- **Bez údaje o sklonu se rozhoduje podle přiblížení.** `gravityZ` je výchozí nula, tedy
  „nastojato"; zařízení bez akcelerometru tím spadne na samotné přiblížení, místo aby funkci
  ztratilo.
- **Se sluchátky se nepřepíná vůbec**, a čte se to při KAŽDÉ změně senzorů, ne jednou na začátku —
  drát i Bluetooth se dají připojit uprostřed hlasovky.
- **Na Androidu se kvůli tomu PŘESTAVUJE PŘEHRÁVAČ.** Do sluchátka se dostane jen zvuk
  s `USAGE_VOICE_COMMUNICATION` a `MediaPlayer.setAudioAttributes` se smí volat jen PŘED
  `prepare()`. Samotné `setCommunicationDevice` nestačí — to řídí komunikační zvuk, kdežto médium
  by hrálo dál z reproduktoru. Přenáší se pozice i to, jestli se hraje, takže je to slyšet jako
  zaváhání, ne jako skok na začátek.
- **iOS přehrávač nepřestavuje**, stačí kategorie `AVAudioSession` — `AVAudioPlayer` se za relací
  přesměruje sám. Změna kategorie ale RESETUJE `rate`, takže se rychlost zapisuje znovu.
- **Do sluchátka vede na iOS jedině `playAndRecord`**, a ta sahá na mikrofon. Přepne se proto jen
  tehdy, když je oprávnění k mikrofonu UŽ UDĚLENÉ (z nahrávání hlasovek); jinak se hraje dál
  z reproduktoru. Ptát se na mikrofon proto, že si někdo dal telefon k uchu při POSLECHU, je
  špatně — a je to horší než přijít o kus pohodlí.
- **Komunikační režim je nastavení CELÉHO SYSTÉMU**, ne našeho přehrávače. Hlasovka pozastavená
  u ucha ho proto nesmí nechat viset: pauza výstup vrací do reproduktoru (`returnToSpeaker`),
  kdežto zastavení ne — tam si to uklidí `release()` sám.
- **Dvě změny výstupu se nesmí potkat** (`routeMutex`). Ruka k uchu a zpátky umí poslat dvě hlášení
  těsně po sobě a bez zámku by se druhé pustilo do přehrávače, který ta první právě uvolnila.
- **Zhasínání displeje dělá PLATFORMA, ne naše pravidlo.** Android `PROXIMITY_SCREEN_OFF_WAKE_LOCK`
  (bez časového limitu — limit by displej rozsvítil uprostřed poslechu), iOS
  `isProximityMonitoringEnabled`. Systém zhasíná podle senzoru samotného, takže telefonu ležícímu
  displejem dolů zhasne taky; to nevadí, protože leží displejem dolů a zvuk se stejně nepřepne.
- **`isWakeLockLevelSupported` se musí zeptat předem**: `newWakeLock` s nepodporovanou úrovní
  vyhodí výjimku až při `acquire()`.
- **Senzor přiblížení je v manifestu `required="false"`.** Tablety ho nemají a aplikace by kvůli
  němu z Play pro ně zmizela. iPad se pozná podle toho, že `proximityMonitoringEnabled = true`
  zůstane `false`.
- **Vlastní přepínač v Nastavení není.** Je to chování, které lidé znají odjinud a děje se jen
  uprostřed přehrávání hlasovky; přidat ho, až o něj někdo řekne — stejná úvaha jako u haptiky.

#### Jak to ověřit

**Přiblížení se z počítače nasimulovat NEDÁ** — na skutečném zařízení `cmd sensorservice` umí jen
`set-uid-state`, injekce hodnot tam není. Emulátor by ji uměl (`adb emu sensor set proximity 0`),
jenže nemá sluchátko, takže by se ověřilo pravidlo, ne zvuk. Ověřuje se tedy rukou: pustit
hlasovku a dát si telefon k uchu. Co se dá číst zvenku:

- `dumpsys sensorservice | grep EarSensor` — při přehrávání musí přibýt `+` řádky pro akcelerometr
  i proximity, po dohrání `-`.
- `dumpsys power | grep voice-at-ear` — `ACQ` při spuštění, `REL` po dohrání.
- `dumpsys audio | grep -oE 'communication device.*'` — u ucha `type:earpiece`, jinak `null`.
  **`Mode owner` zůstane prázdný i při přepnutém výstupu**, takže se podle něj řídit nedá;
  rozhoduje `communication device`.
- Po dohrání musí být `Requested mode = MODE_NORMAL` a `communication device: null`.

**Ověřeno na Pixelu 11 Pro Foldu (Android 17):** u ucha se `communication device` přepne na
`earpiece` a po oddálení zpět na `null`, zámek `plume:voice-at-ear` drží přesně po dobu hlasovky
(ACQ 22:04:14 → REL 22:04:29 u jedenáctivteřinové), po dohrání se oba senzory odhlásí a režim
zvuku se vrátí na `MODE_NORMAL`. V logu nic z `AudioEngine`, `EarSensor` ani `VoicePlayer`.
Zvuk skutečně přeskočí ze spodního reproduktoru do sluchátka.

**Na Fold se debug build nedostane přes ten z Play** — liší se podpis i `versionCode`, takže
`adb install -r` skončí na `INSTALL_FAILED_VERSION_DOWNGRADE` a s `-d` na neshodě podpisů. Musí
se odinstalovat, což znamená nové přihlášení. Pro ověřování, které stačí na emulátoru, se to
nevyplatí.
- **Tažení po waveformu `pointerInput` spotřebuje**, takže se na něm nerozjede swipe na odpověď.
  Posouvat jde jen vybranou hlasovku; u ostatních waveform gesta nemá.
- **Poslechnuto = `openMessageContent`.** Ukáže se hned lokálně, potvrzení a poslechnutí našich
  odchozích protějškem chodí `UpdateMessageContentOpened` (`contentOpenedUpdates`).
- **Android přehraje Ogg/Opus sám** (`MediaPlayer`, atributy řeč/média).
- **iOS Ogg NEPŘEHRAJE** — AVFoundation zná Opus jen v CAF. Dekodér Opusu ale systém má: pakety
  se z Oggu vytáhnou v Kotlinu (`parseOggOpus`, commonMain), `AVAudioConverter` je dekóduje do PCM
  a uloží se WAV (dočasný adresář, jednou na soubor), který hraje `AVAudioPlayer`.
  - Opus se dekóduje vždy při 48 kHz; začátek se ořízne o `preSkip` z `OpusHead`.
  - Konvertor bere pakety po jednom v `AVAudioCompressedBuffer` **s popisem paketu** — velikosti
    se liší. Formát chce `mFramesPerPacket` předem; bere se z TOC bajtu (`opusPacketSamples`).
  - Dekodér je ověřený testem na simulátoru (`iosTest/OpusDecodeTest`): sinus zakódovaný
    systémovým Opusem se musí vrátit neztichlý a ve správné délce. Spouští se
    `./gradlew :client:shared:iosSimulatorArm64Test`.
  - Kategorie `AVAudioSession` je Playback — hlasovka hraje i s přepínačem ztlumení.
  - Delegát `AVAudioPlayer` se drží silnou referencí, jinak by dohrání nikdo nenahlásil.

### Nahrávání

Prázdné pole bez příloh má místo odeslání **mikrofon**, nebo **kameru** (video kolečko, viz
„Nahrávání koleček"). Nahrává se **podržením, jako v Telegramu**: puštění nahrávku odešle, tah
doleva ji zahodí, tah nahoru zamkne (karta pak ukáže koš, čas a živé úrovně a odesílá se
ťuknutím). Ťuknutí přepíná hlasovku ↔ kolečko a ukáže nápovědu. Gesto je `holdToRecord`
v `RecordControls.kt`, rozhodnutí o tahu čistá funkce `recordDragOutcome` (testy
`RecordGestureTest`).

- **Dřív to bylo ťuknutím, ne podržením**, s odůvodněním, že podržení s tažením je nad
  tlačítkem, které se během něj mění, křehké. Uživatel chtěl model Telegramu; křehkost řeší to,
  že gesto sedí na PRVNÍM modifikátoru tlačítka (měnící se druh tlačítka ho neresetuje), čte se
  v `Initial` a po začátku nahrávání spotřebovává všechno — puštění tak nevyhodnotí tlačítko
  jako ťuknutí. Kratší než vteřina se dál zahodí (`MIN_DURATION_MS`) s hláškou.
- **Nahrávat se začne po 250 ms držení** (`HOLD_TO_RECORD_MS`); kratší stisk je ťuknutí. Delší
  prodleva by u hlasovky useknula první slovo.
- **Oprávnění vyžádané až podržením se na TOMHLE podržení nepoužije.** Než uživatel dialog
  vyřídí, prst tlačítko pustil; nahrávat potom by bylo nahrávání, o kterém neví. Pozná se to
  podle toho, že odpověď `request` přišla až po jeho návratu (`immediate`), a podrží se znovu.
- **Zrušený dotyk nahrávku ZAMKNE, neodešle.** Otočení displeje, systémové gesto nebo stažená
  lišta pošlou puštění, které je už spotřebované (tak ho Compose odlišuje). Dokud se bralo jako
  puštění, odešla po otočení dvouvteřinová nahrávka, o které uživatel nerozhodl. **Po zrušení
  se nesmí čekat na „zbytek gesta"** — už nepřijde a čekání by spolklo příští ťuknutí.
- **Zámek nad tlačítkem kreslí obrazovka, ne pole** (`RecordLockHost`, stav gesta
  `RecordGestureState` drží obrazovka). Pás skla pod polem (Haze) kreslí lištu do vrstvy o její
  velikosti a všechno, co z ní přečnívá, ořízne — zámek v poli nebyl vidět vůbec. Vodorovně se
  srovná podle změřeného středu tlačítka.
- **Nápověda po ťuknutí je `TooltipBox` s `enableUserInput = false`** — výchozí tooltip se
  ukazuje na dlouhý stisk, a ten tu nahrává.
- **Čtečka obrazovky podržet neumí**: tlačítko má vlastní akci „Nahrát hlasovou zprávu" /
  „Nahrát video zprávu", která začne rovnou zamčené. **Ověřené to není** (TalkBack na emulátoru
  nikdo nepouštěl).
- **Formát chce Telegram Ogg/Opus mono**, případně M4A (TDLib ho u hlasovky přijme).
  - **Android 10+** nahrává Ogg/Opus rovnou (`MediaRecorder`, 48 kHz, 32 kbit/s). **Starší**
    Opus neumí → M4A/AAC. `stop()` po příliš krátkém nahrávání vyhodí výjimku.
  - **iOS** `AVAudioRecorder` Opus umí jen do CAF → nahrává se PCM (48 kHz mono), po skončení
    se zakóduje systémovým Opusem (`encodeOpus`, pakety po 20 ms) a zabalí do Oggu v commonMain
    (`oggOpusFile`: OpusHead, OpusTags, stránky po 50 paketech s CRC a granule). Celý řetězec
    kódování → Ogg → čtení → dekódování ověřuje `OpusDecodeTest` na simulátoru, zápis Oggu
    i CRC (referenční vektor) `AudioFormatsTest`.
  - iOS relace při nahrávání je PlayAndRecord s `DefaultToSpeaker` — jinak by potom všechno
    hrálo do sluchátka u ucha. Přehrávání si kategorii přepne zpátky samo.
- **Waveform se skládá z úrovní během nahrávání** (po 50 ms: Android `maxAmplitude`, iOS
  `averagePower`), převzorkuje na 100 hodnot 0–31 a zabalí po 5 bitech (`encodeWaveform`).
- **Oprávnění až při prvním ťuknutí na mikrofon** (`rememberRecordPermission`). Android
  `RECORD_AUDIO` za běhu, iOS `NSMicrophoneUsageDescription` v Info.plist — bez klíče iOS aplikaci
  při přístupu k mikrofonu ukončí.
- **`MediaRecorder` s výstupem Ogg/Opus pauzu NEDODRŽÍ.** `pause()` projde bez chyby a čas
  v aplikaci stojí, jenže kodér jede dál: hlasovka pozastavená na výběr času měla 1 590
  souvislých paketů po 20 ms, tedy 32 s místo tří (spočítáno rozebráním Oggu staženého přes
  Bot API; délku pak hlásil i server). Android proto pozastavuje UKONČENÍM úseku a obnovuje novým
  souborem; na konci se zvukové pakety úseků složí do jednoho Oggu (`joinOggOpus`, test
  `useckyNahravkySeSpojiZaSebe`). M4A (Android 9 a starší) spojovat neumí a zůstává u
  `MediaRecorder.pause` — ověřené není. iOS `AVAudioRecorder.pause` / `record` (PCM do CAF).
- **Zamčenou hlasovku jde zastavit a poslechnout před odesláním** (■ na konci pruhu,
  `stopRecordingForPreview`, `VoicePreviewBar`, `VoicePreview`). Pruh se změní na koš,
  přehrát / pozastavit, waveform a čas; odesílá se týmž tlačítkem (i s nabídkou podržení) a koš
  nebo odchod z chatu nahrávku zahodí i se souborem. Karta zůstává vlnitá a terciární jako při
  nahrávání (`voiceActive` v kartě), jen tlačítko nedýchá — nic neběží.
  - **Náhled hraje týž `VoicePlayer` pod klíčem zprávy −1** (`voicePreviewKey`). Po dohrání se od
    něj NESMÍ řetězit další neposlechnutá (`isVoicePreview`, testy `VoicePreviewTest`): „další po
    −1" je nejstarší hlasovka v chatu.
  - **Mezi ■ a hotovým souborem je mezistav** (`VoicePreview.voice == null`): iOS po zastavení
    teprve kóduje Opus a karta by mezitím ukázala textové pole, jako by nahrávka zmizela.
    Odeslat v něm nejde. Koš během dokončování náhled zruší a hotový soubor pak smaže — hlídá
    to porovnání instance mezistavu, jinak by se náhled po koši vrátil.
  - **Kolečko ■ má taky** — viz „Nahrávání koleček".
  - **Po poslechu jde nahrávat dál** (mikrofon na konci pruhu náhledu, `continueVoicePreview`,
    `VoiceRecorder.start(continuing)`, testy `VoiceRecorderContinueTest`). Nahrávání naváže
    rovnou zamčené (spouští ho ťuknutí, ne podržení; zamčení nastaví obrazovka), čas a úrovně
    běží od konce náhledu a po ■ nebo odeslání se oba úseky spojí `joinOggOpus` do souboru
    nového úseku; původní se smaže. **Spojuje nahrávač, ne `VoiceRecorder`**
    (`MediaCapture.canJoin` / `join`): výchozí je Ogg/Opus, kamera spojuje MP4 kodérem
    platformy (viz „Nahrávání koleček"). **Waveform se počítá znovu ze VŠECH úrovní**
    (`RecordedVoice.levels`) — normuje se na špičku celé nahrávky, takže dva hotové waveformy
    slepit nejde. Koš během navazování zahodí celou hlasovku, ne jen dovětek. Dovětek pod
    300 ms (`MIN_SEGMENT_MS`) se zahodí a zůstane původní nahrávka — `MediaRecorder` tak krátký
    úsek nedokončí. **M4A (Android 9 a starší) spojit nejde** (`VoicePreview.canContinue`),
    tlačítko tam není. Selhané spojení nechá původní nahrávku.
    Ověřeno na emulátoru: dva úseky, odeslaný soubor má podle granule v Oggu 35,1 s, tedy oba
    (délku v Bot API hlásíme sami, ta nic nedokazuje). iOS jen přeložené.
  - Ověřeno na emulátoru uživatelem (nahrát, zamknout, ■, přehrát, odeslat). iOS jen přeložené.
- **Nahrávač je `factory`, ne `single`** — patří obrazovce. Odchod z chatu nahrávku zahodí,
  spuštění nahrávání zastaví hrající hlasovku (nahrávala by se z reproduktoru).
- **Odesílá se jako samolepka** (`sendMedia`): bez letu, rozepsaný text zůstává, citace se použije.

## Hudba

`MessageAudio` je `MessageMedia.Audio` a hraje přímo v bublině (`AudioContent`): obal alba
jako tlačítko, název, interpret, lišta průběhu s posunem, čas a čip rychlosti.

- **Hraje TENTÝŽ přehrávač jako hlasovky** (`VoicePlayer`, klíč `VoiceKey`, akce
  `ToggleVoice`/`SeekVoice`). Zvuk je v aplikaci vždycky nejvýš jeden — skladba zastaví
  hlasovku i kolečko se zvukem a naopak — a druhý přehrávač by to musel hlídat zvlášť.
  `AudioEngine` hraje cokoli, co ne-Ogg: Android `MediaPlayer`, iOS `AVAudioPlayer` rovnou
  (dekódování do WAV je jen pro Ogg/Opus).
- **Hudba u ucha nepřepíná do sluchátka** (`VoicePlayer.play(music = true)`): senzory se vůbec
  nezapnou (ověřeno `dumpsys sensorservice`), jinak by telefon v ruce při poslechu zhasínal displej.
- **Po dohrání hraje další novější skladba v chatu** — playlist, bez ohledu na „poslechnuto",
  které u hudby není. Hlasovky a skladby se mezi sebou neřetězí.

### Hudba na pozadí

Skladba hraje dál i po odchodu z konverzace a z aplikace. Ovládá se z notifikace, zamykací
obrazovky, sluchátek a hodinek. Na Androidu přes Media3 MediaSession, na iOS přes
`MPNowPlayingInfoCenter` a `MPRemoteCommandCenter`.

- **Fronta skladeb je `MusicQueue` (`core/audio`), ne ViewModel konverzace.** Dokud byla
  v konverzaci, musela hudba s odchodem z ní skončit: bez obrazovky nebylo komu říct, co hraje
  dál. Sousední skladby se proto hledají v chatu (`ConversationRepository.musicNeighbors`,
  `SearchMessagesFilterAudio`), ne v načtené historii. Konverzace i záložka Hudba v profilu
  pouštějí skladby přes ni. `onCleared` konverzace zastavuje jen hlasovku
  (`stop(chatId, keepMusic = true)`).
- **`searchChatMessages` s `offset = -1` novější zprávu NEVRÁTÍ, jen tu samotnou.** Ověřeno
  logem od téže zprávy: `-1` vrátilo [skladba, starší, starší], `-2` vrátilo
  [novější, skladba, starší, starší]. Novější soused tak chyběl, v notifikaci nebylo „další"
  a po dohrání nic nenavázalo. Stejný vzor `-newer` má i `visualMedia` a vrací o jednu
  novější fotku míň. Tam to nevadí, protože se dotahuje po stránkách.
- **Zvuk hraje dál `VoicePlayer`, ne ExoPlayer.** Přehrávač je jeden pro hlasovky i hudbu
  a druhý by se s ním musel domlouvat, kdo zrovna hraje. Media3 dostane `SimpleBasePlayer`
  (`MusicSessionPlayer`), který nic nepřehrává. Jen popíše stav fronty a předá povely.
  **Playlist má nejvýš tři položky** (předchozí, hrající, další), takže Media3 sám rozhodne,
  jestli „předchozí" skočí na začátek skladby (po třech vteřinách), nebo na skladbu před ní.
  iOS to dělá ručně stejně.
- **Mezi dvěma skladbami nesmí stav hudby ani na okamžik zmizet** (`Current.advancing`).
  `VoicePlayer` po dohrání vynuluje stav, a než se najde a stáhne další skladba, je prázdný.
  Kdyby se to promítlo do relace, Media3 by stáhl notifikaci i službu z popředí. Z pozadí by
  ji systém znovu spustit nedovolil a hudba by skončila po první skladbě. Proto `onFinished`
  u `VoicePlayer.play`: volá se SYNCHRONNĚ ještě před vynulováním stavu. `completions` doběhnou
  až v korutině, takže by fronta prázdný stav viděla dřív, než se dozví proč.
- **Skladba, kterou zastavilo něco jiného** (hlasovka, video, nahrávání), z fronty odchází.
  Pozná se to podle stavu přehrávače bez příznaku `advancing`.
- **Služba (`MusicPlaybackService`) je v `:client:shared`, deklarace v manifestu vstupního bodu.**
  Spouští ji `MusicControls` obyčejným `startService`: hudba začíná ťuknutím, tedy s aplikací
  v popředí. Do popředí ji přepíná a zase z něj pouští Media3 podle stavu přehrávače. Relaci
  vytvořenou ve službě je potřeba přidat ručně (`addSession`), jinak notifikace nevznikne,
  dokud se nepřipojí cizí ovladač. Po konci hudby se služba ukončí sama.
- **Bez služby by hudba na pozadí přestala hrát** — aplikace v keši se do pár vteřin zmrazí
  (viz „Notifikace"), i s přehrávačem.
- **`FOREGROUND_SERVICE_MEDIA_PLAYBACK` chce Play vysvětlit** v deklaraci služeb v popředí
  (Play Console → Obsah aplikace). Bez ní neprojde další release, ne build.
- **Hudba má jiný audio focus než hlasovka** (`AudioEngine.load(music = true)`): trvalý
  `GAIN` a `CONTENT_TYPE_MUSIC`, takže jiná hudební aplikace se zastaví a sama nenaváže.
  Ztišení kvůli navigaci (`CAN_DUCK`) hudbu nezastaví, udělá ho systém. Vytažená sluchátka
  (`ACTION_AUDIO_BECOMING_NOISY`) hudbu pozastaví, jinak by se rozezněla z reproduktoru.
- **Ťuknutí na přehrávač v notifikaci otevře chat, ze kterého skladba hraje**
  (`setSessionActivity` s `NotificationIntents.chatIntent`).
- **iOS: `UIBackgroundModes: audio`** v Info.plist, kategorie relace je Playback
  (`AudioEngine`). Systém si pozici dopočítává z posledního hlášení, a proto
  `NowPlayingControls` hlásí pozici při každé změně a za přehrávání každých pět vteřin.
  Posun v bublině by se na zamykací obrazovce jinak neukázal.
- **Ověřeno na emulátoru** (Android 17, dvě skladby od bota v chatu s Notifikátorem):
  - skladba hraje dál na ploše (`isForeground=true`, `types=0x2`, šablona MediaStyle);
  - druhá skladba sama naváže a po poslední se relace i služba ukončí;
  - pauza a „další" z přehrávače v rychlém nastavení;
  - poslední skladba nemá „další";
  - obal, název a interpret jsou v přehrávači;
  - ťuknutí na přehrávač otevře chat.
  
  **Stav relace je v logu** (`adb logcat | grep "onSessionPlaybackStateChanged.*tintera"`).
  V `dumpsys media_session` se hledá podle `packages=`, ne `package=`.

  **Neověřeno**: zamykací obrazovka (emulátor nemá zámek), sluchátka a vytažení kabelu,
  přerušení hovorem a celý iOS (jen přeložené).
- **Rychlost platí i pro hudbu** (jedna volba pro celou aplikaci, jako u hlasovek); u podcastů
  se hodí, u písniček ji nikdo nezapne.
- **Délku server znát nemusí** — Bot API ji u souboru bez metadat pošle jako nulu. „0:00" by
  lhalo, takže se čas do spuštění neukazuje; skutečnou délku zjistí přehrávač.
- **V profilu je záložka Hudba** (`SharedMediaKind.Music`, `SearchMessagesFilterAudio`;
  `SharedMusicList`). Ťuknutí skladbu pustí přímo tam — TÝŽ `VoicePlayer` (profil ho dostane
  z Koinu, `ChatProfileViewModel.toggleMusic` přes `MusicQueue`), takže konverzace pod
  profilem ukazuje průběh a po dohrání naváže další skladba. U hrající
  skladby je místo délky „0:02 / 0:04". Hlasovky do Hudby nepatří (jiný filtr).
- **Metadata skladby nejspíš drží TDLib pro SOUBOR, ne pro zprávu** (domněnka, neověřená).
  V chatu s botem jsou nejspíš dvě zprávy s tímtéž souborem a jiným názvem; profil po
  instalaci ukázal u obou „Pírko nad vodou" s obalem, po restartu „Pírko s časem" bez obalu —
  a bubliny v konverzaci pak totéž co profil. Seznam tedy chybu nemá; kdo ověřuje hudbu, ať
  bere RŮZNÉ soubory.
- **Sdílet a Uložit jako… jdou cestou souboru** (`Audio.asDocument()`, `withDocument`), se
  jménem souboru, ne názvem skladby.
- **Náhled v seznamu chatů je „Interpret – Název"**, ne „Audio" — u každé skladby stejné slovo
  by nic neřeklo. Totéž v citaci.
- **Ověřuje se botem**: `sendAudio` s `title`, `performer` a `thumbnail` (obal). Zvuk se na Macu
  vyrobí `say -o x.aiff "…"` a `afconvert -f m4af -d aac`. Ověřeno na emulátoru: obal i bez
  obalu, přehrání s průběhem a čipem, navázání druhé skladby, sdílení, náhled v seznamu.
  **Neověřeno**: iOS (jen přeložené), přerušení hovorem u hudby.

## Prohlížeč médií

Ťuknutí na fotku nebo video v bublině je otevře přes celou obrazovku: u fotky štípnutí a dvojité
ťuknutí přibližují, video se přehrává; tah dolů zavírá.

- **Vlastní routa `MediaRoute`, ne overlay v konverzaci.** Ven se tím chodí zpět včetně predictive
  back, přežije to otočení i zabití procesu a nepotřebuje to `BackHandler`, který by predictive
  back zabil. **Nezapomenout ji zaregistrovat v `NavSavedStateConfiguration`** — bez toho to spadne
  až za běhu.
- **Routa nese jen id**, ne obsah. Zprávu si prohlížeč načte znovu (`ConversationRepository.message`);
  celá zpráva i s reakcemi a citací do serializovaného zásobníku nepatří a dotaz navíc je vedle
  stahování fotky zanedbatelný.
- **Do prohlížeče jde jiná varianta souboru než do bubliny.** `MessageMedia.Photo.fullFileId` je
  největší, co server má; `fileId` (do 1280 px) slouží jako okamžitý náhled, protože ho konverzace
  už stáhla. V bublině by originál znamenal desítky megabajtů kvůli obrázku širokému pár set bodů.
- **Prohlížeč nemá metadata list-detail scény**, takže ho scéna nepřevezme a `NavDisplay` ho
  vykreslí přes celou plochu i na širokém rozvržení.
- **Pager, sousedé, strop šířky, přisouvání karty a jezdící pozadí jsou `core/ui/MediaCarousel`**,
  vepsání karty mezi lišty je `core/ui/MediaPageLayout` — obojí sdílí prohlížeč s potvrzením
  sdílení (Direct Share). Prohlížeč si do stránky dodává zoom, video a režim celé obrazovky
  (`immersion`), nad carousel kreslí lišty a reakce. Všechno níž o sousedech, šířce a pozadí
  platí pro obě obrazovky; **nová podoba „snímků vedle sebe" patří do carouselu, ne do obrazovky.**
- **Ořez pozadí je nutný** (`clipToBounds` v pozadí `MediaCarousel`). Pozadí sousedů leží posunuté
  mimo plochu prohlížeče; bez ořezu prosvítalo vlevo (tehdy přes rail) nerozostřené — rozostření pokrývá
  jen plochu prohlížeče.
- **Gesta patří FOTCE, ne bublině.** Kdyby ťuknutí odchytávala bublina, sebralo by ho i odkazům
  v popisku. Dlouhý stisk se na fotce musí zopakovat: potomek s vlastním klikáním ho nepustí
  k rodiči, takže by nad fotkou nešla otevřít nabídka.
- **Listuje se `HorizontalPager`em, starší vlevo, novější vpravo.** Fotky se hledají
  `searchChatMessages` s `SearchMessagesFilterPhoto`, ne procházením historie — fotek je
  v chatu zlomek zpráv. Jedno volání vrátí obě strany: záporný `offset` přidá novější.
  Výchozí zpráva je ve výsledku taky, takže se při dotahování odfiltruje.
- **Pager drží stránku podle klíče** (id zprávy). Když se vlevo dotáhnou starší fotky, pod
  prstem se nic nepohne, jen se posune index — proto se stránkuje z `snapshotFlow` nad
  `currentPage`, které se po dotažení samo ozve znovu.
- **Počet stránek a klíč pageru čtou TÝŽ stav** (`rememberUpdatedState(items)`), ne seznam
  zachycený v lambdě. `rememberPagerState { … }` zapíše počet do stavu hned v rekompozici,
  kdežto pager v `BoxWithConstraints` dostane nový `key` až v subkompozici při layoutu. Mezi
  tím Compose přepočítá klíče stránek (`NearestRangeKeyIndexMap`) s novým počtem nad starým
  seznamem — po dotažení fotek to v release padalo na „Index 22 out of bounds for length 22".
  Platí pro každý pager / lazy seznam uvnitř `SubcomposeLayout`, jehož počet je mimo něj.
- **Otevřená fotka se ukáže HNED, výřez pro listování se dotáhne pod ní** (`loadInitial`).
  Dokud se čekalo na `visualMedia` (`searchChatMessages`, v chatu se čtyřiceti fotkami stovky
  ms), byl stav prázdný a kreslilo se načítání — fotka letící z bubliny neměla kam dosednout,
  zmizela, bliklo kolečko a pak se objevila. Zpráva přitom byla načtená už na začátku (kvůli
  mizejícím médiím). Pager drží stránku podle klíče, takže rozšíření výřezu kolem ní s ním
  nepohne; okraje jsou do té doby „dosažené", aby se listování nerozjelo od jediné fotky
  souběžně s výřezem. Ověřeno sérií surových snímků: fotka je vidět v každém snímku.
- **Dotažení sousedé PŘIJEDOU, nenaskočí** (`MediaCarousel(animateArrivals = true)`,
  `PageArrivals`). Otevřená fotka se zanimuje letem z bubliny a sousedé u okrajů by vedle ní
  naskočili bleskem. Stránka, která přibyla v datech, vyjede od okraje obrazovky (posun přesně
  o to, o co soused vykukuje) a prolne se. **Posun o kus šířky stránky nefungoval** — soused
  startoval hluboko za okrajem a vidět byl jen konec pohybu, takže to vypadalo jako pouhé
  prolnutí. **Pozor, pager skládá stránky i při listování** (soused dopředu): „skládá se
  poprvé" proto nestačí, jinak by přijížděla každá další fotka při švihnutí. Přijede jen stránka,
  jejíž klíč v předchozím stavu dat nebyl a která se složila hned se změnou; po dvou snímcích se
  nový stav bere jako známý. Směr se čte až při kreslení — po rozšíření dat pager přesune
  `currentPage` na otevřenou fotku až v layoutu. Sdílení (Direct Share) to nemá zapnuté, má
  vlastní animace přidávání a odebírání. Ověřeno surovými snímky: soused se při otevření vysune
  a prolne, při listování je hned celý.
- **Počítadlo „N z M" se ptá jednou**, na otevřenou fotku (`getChatMessagePosition` +
  `getChatMessageCount`; TDLib počítá od nejnovější, tak se to obrací). Ostatní stránky se
  odvozují posunem indexu, protože načtený výřez je souvislý.
- **`detectTransformGestures` s pagerem nejde.** Konzumuje pohyb v každém směru, takže by fotka
  spolkla i vodorovný tah a pager by se nepohnul. Místo něj vlastní detektor, který po slopu
  rozhodne jednou za gesto: dva prsty nebo přiblížená fotka = štípnutí/posun (konzumuje),
  svislý tah = zavírání (konzumuje), vodorovný tah nepřiblížené fotky = listování (nekonzumuje,
  pustí ho pageru). Listovat se tedy dá jen se základní velikostí; posun po přiblížené fotce
  by se s listováním pral. Konec gesta zná detektor sám, takže odpadl samostatný pozorovatel
  puštění, který byl nutný kvůli `detectTransformGestures`.
- **Odlistovaná fotka se vrací do základní velikosti.** Soused zůstává složený
  (`beyondViewportPageCount`), a s ponechaným přiblížením by se z něj po návratu nedalo listovat.
- **Přiblížení se počítá kolem prstů, ne kolem středu.** Bod pod prsty musí zůstat na místě,
  jinak fotka při štípnutí ujíždí a uživatel ji pak dohání posunem.
- **Meze posunu se počítají z poměru stran v metadatech**, ne z načteného souboru — jinak by se
  měnily ve chvíli, kdy dorazí ostrá verze.
- **Posun i přiblížení se čtou až v `graphicsLayer`**, takže gesto nic nerekomponuje; překresluje
  se jen vrstva s obrázkem. Totéž u zavíracího tahu.
- **Vzhled je po vzoru Google Zpráv.** Pozadí je **rozostřený aktuální snímek** pod tmavým
  nádechem (Haze nad malým náhledem), snímek je **karta se zaoblením**
  a sousední stránky u okrajů vykukují (`contentPadding` pageru, 24 dp soused, 8 dp mezera —
  se 14 dp souseda nebylo moc poznat, že vedle další fotky jsou). **Popisek sousední stránky
  mizí** podle posunu pageru (`captionAlpha`), jinak u okraje trčel useknutý kus textu.
- **`HorizontalPager` šířku stránky podle obsahu NEŘEŠÍ.** Stránky mají jednu šířku a karta se
  v ní centruje, takže fotka na výšku na širokém displeji (rozložený Fold) ležela uprostřed široké
  stránky a sousední karty byly celé za okrajem. Proto dvě věci: stránka má strop
  (`MAX_PAGE_WIDTH` 640 dp, zbytek jde do `contentPadding`, mezera mezi kartami je tam 24 dp místo 8) a **sousední karta se přisouvá k okraji
  bližšímu aktuální stránce** (`cardBias` = −`getOffsetDistanceInPages`, čteno až při umístění
  v `MediaPageLayout`). Plynule s tahem — karta, která dojede doprostřed, stojí na středu.
  Šířka se bere z `BoxWithConstraints`, ne z okna.
- **Pozadí jezdí s pagerem**, neprolíná se. Kreslí se pozadí aktuální stránky i sousedů vedle
  sebe, posunutá o `currentPageOffsetFraction` (čteno až v `graphicsLayer`). Dokud se pozadí
  jen prolnulo podle `currentPage`, měnilo se skokem až po přeskočení na další stránku, zatímco
  karta jela pod prstem. Šev mezi dvěma pozadími změkčí rozostření samo — Haze rozmazává
  složený obraz, ne každý snímek zvlášť. Nahoře kdo a kdy fotku poslal
  a akce, dole reakce. Dřív byl snímek přes celou obrazovku na holé černé
  a ovládání leželo přes fotku s přechodem do tmava.
- **Dva režimy: galerie a celá obrazovka.** Galerie je to výš (rozostřené pozadí, karty,
  sousedé u okrajů, lišty). Ťuknutí, dvojité ťuknutí nebo štípnutí na snímku přepne na celou
  obrazovku: černé pozadí, bez lišt a popisku, snímek bez zaoblení a stránka přes celou šířku
  (mezi fotkami při listování jen černá mezera). Zpátky vede jen ťuknutí — přiblížení vrácené
  na základní velikost zůstává na celé obrazovce a návrat do galerie přiblížení srovná.
  Zpět (gesto i tlačítko) prohlížeč zavírá z obou režimů, stejně jako tah dolů.
- **Režim je jeden průběh 0 → 1** (`immersion`), ze kterého jde všechno: odsazení a mezera
  pageru, místo pro lišty a popisek (`pageCardFrame`, testy `MediaPageLayoutTest`), zaoblení,
  průhlednost pozadí. Pružina přestřelí, takže se ořezává do 0..1 — záporné odsazení nebo
  zaoblení layout odmítne. Místo pro lišty se čte až při měření stránky, odsazení pageru
  v kompozici (bere jen hodnotu), takže přechod rekomponuje, ale jen po dobu animace.
- **Systémové lišty na celé obrazovce zůstávají** (čas, notifikace), jen se pod nimi
  ztmaví přechodem do průhledna. Přes vysoký nebo přiblížený snímek by jinak světlé ikony
  nebyly vidět.
- **Lišty leží NAD kartou, ne přes ni.** Stránka jde přes celou výšku (přiblížená fotka má kam
  růst), ale karta se vepisuje do místa mezi lištami. Rozvržení stránky je vlastní `Layout`
  (`MediaPageLayout`): nejdřív se změří popisek, snímek se vepíše do zbytku (`fitInto`, testy
  `MediaPageLayoutTest`). S popiskem jako samostatnou lištou by fotka poskočila při listování
  mezi stránkou s popiskem a bez.
- **Gesta sedí na stránce, přiblížení na kartě.** Štípnout jde i vedle úzké fotky; střed
  přiblížení je střed KARTY (zapamatovaný v `onPlaced` do obyčejného pole, ne do stavu), protože
  karta s popiskem pod sebou nestojí uprostřed stránky. Zaoblení nese `graphicsLayer` karty
  (`clip` + `shape`), takže se s přiblížením zvětšuje spolu s fotkou.
- **Podklad je tmavý v obou motivech**, i s rozostřením. Barva ovládání je proto natvrdo bílá —
  role z `colorScheme` by ve světlém tématu dala tmavý text na tmavé ploše — a **ikony
  systémových lišt musí být světlé** (`DarkSystemBars`, při odchodu vrátí předchozí stav).
  Kde rozostření není (Android 11 a starší), kreslí Haze skoro neprůhledný závoj
  (`AMBIENT_FALLBACK_ALPHA`) — ostrá fotka za kartou by rušila.
- **Uložení do galerie je v horní liště prohlížeče** (`MediaSaver`, `expect`/`actual`). Ukládá
  se KOPIE ostré verze (`fullFileId`, u souboru soubor sám) — soubor TDLib je v soukromém
  adresáři a s odhlášením zmizí. Android od verze 10 přes `MediaStore` do `Pictures/Plume`
  a `Movies/Plume` bez oprávnění, položka je během zápisu `IS_PENDING`; do verze 9 veřejný
  adresář s `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) a ohlášení skeneru médií. iOS
  `PHAssetChangeRequest` s oprávněním jen k přidávání (`NSPhotoLibraryAddUsageDescription`).
  Nabízí se podle `canBeSaved` (`MessageActions.canSave`), stejná ochrana obsahu jako
  u kopírování.
- **Lišta prohlížeče má venku jen Sdílet a Do galerie, zbytek je v nabídce ⋮** (Přeposlat,
  Uložit jako…, Smazat). Se čtyřmi ikonami by se na užším telefonu nevešel titulek; smazání
  do nabídky patří i tak — nevratná akce nemá stát vedle častých — a Přeposlat je sdílení do
  Telegramu, takže vedle Sdílet by byly dvě podobné ikony. Co právě běží, točí na místě svého
  tlačítka (`MediaViewerState.busy`), u „Uložit jako…" na místě ⋮.
- **„Uložit jako…" je jen na Androidu** (`ACTION_CREATE_DOCUMENT`, bez oprávnění, uživatel
  si vybere Stažené i cokoli jiného; `rememberSaveAs` vrací na iOS `null`). Na iOS nabízí
  dialog sdílení sám „Uložit obrázek" i „Uložit do Souborů", takže tam sdílení pokrývá všechno.
  Vestavěný `CreateDocument` má typ napevno z konstruktoru, proto vlastní kontrakt s typem
  podle souboru.
- **Sdílí se KOPIE pod čitelným jménem** (`cache/shared`, vydává ji provider `…documents`),
  ne soubor TDLib — příjemce jméno ukazuje. Mazat se může až při dalším sdílení, protože
  příjemce soubor čte až po odeslání. Oprávnění jde v `ClipData` (výběr aplikací ho předá dál)
  i výslovným `grantUriPermission` všem, kdo `ACTION_SEND` přijme — tatáž past jako
  u otevírání souborů. Na iPadu potřebuje `UIActivityViewController` kotvu popoveru, jinak
  aplikace spadne.
- **Fotka a video se ukládají pod jménem podle času zprávy** (`savedFileName`,
  `Plume_20260918_233025.jpg`). TDLib je drží jako `5868681087951770588_121.jpg`, což v galerii
  nic neřekne. Soubor si nechává jméno, které mu dal odesílatel.
- **Ověřuje se dotazem na `MediaStore`**, ne pohledem do galerie: `adb shell "content query
  --uri content://media/external/images/media --projection _display_name:_size --where
  \"relative_path LIKE 'Pictures/Plume%'\""`. Velikost prozradí, jestli se uložila ostrá verze.
- **Odpovědět jde přímo z prohlížeče** — šipka na začátku dolní lišty, jako v Google Zprávách.
  Prohlížeč se zavře a v konverzaci pod ním je nad polem citace. Požadavek jde přes
  `ConversationReplyRequests` — STAV, ne událost, jako hledání z profilu: konverzace se po
  zavření prohlížeče teprve znovu skládá. **Nabízí se jen nad konverzací téhož chatu**
  (`conversationBelow` v `AppShell`): z mřížky médií v profilu nebo z ankety by nebylo kam se
  vrátit. Podle práva `canReply` stránky; lišta se kvůli odpovědi ukáže i bez reakcí
  (Uložené zprávy je nemají).
- **Fotka z prohlížeče nemusí být v načtené historii** — prohlížeč listuje médii celého chatu.
  Odpověď si ji proto dočte (`repository.message`, lokálně); citace ji v seznamu mít nemusí.
  Ověřeno na emulátoru odpovědí na fotku 31 ze 43: bot ji dostal s `reply_to_message` na ni.
- **Reakce, přeposlání a smazání jsou přímo v prohlížeči.** Nabízené reakce a práva
  (`messageActions`) se načítají **pro stránku, na které uživatel stojí**, jednou za zprávu —
  stejný důvod jako u nabídky v konverzaci. Dokud nedorazí, akce se nekreslí. Smaže se jen
  zobrazená fotka, i z alba; ze seznamu ji odebere až `deletedMessages` a po poslední fotce se
  prohlížeč zavře.
- **Let z bubliny jede přes sdílený prvek** (`photoKey`), stejně jako avatar a název chatu.
  Na širokém rozvržení taky (`inTwoPane`, viz „Navigace"): prohlížeč jde přes celou plochu,
  takže fotka z pravého panelu vyletí na celou obrazovku a při zavření se do bubliny vrátí.
  Ověřeno na rozloženém Foldu sérií surových snímků se zpomalenými animacemi, tam i zpět.
- **Sdílený prvek se během letu kreslí v PŘEKRYVU nad celou aplikací, mimo ořezy rodičů** — tedy
  i přes hlavičku obrazovky. Fotka, která v konverzaci zpola mizí pod hlavičkou, tak po návratu
  z prohlížeče dosedla na místo, ale kreslila se PŘES hlavičku, a teprve na konci přechodu
  (kdy kreslení převezme seznam) hlavička blikla přes ni — vypadalo to jako chyba vykreslení
  a trvalo to víc než vteřinu, protože přechod běží dál i po dosednutí. Obrazovka proto řekne,
  kde ji ořezává vlastní seznam (`SharedElementClip`, `sharedElementClipArea` na TÉŽE ploše,
  která má `clipToBounds`), a `sharedNavElement` ten ořez použije i v překryvu
  (`clipInOverlayDuringTransition`).
- **Tvar fotky se musí předat i SDÍLENÉMU PRVKU, ne jen rodiči** (`sharedNavElement(shape = …)`,
  `flightShape`, testy `FlightShapeTest`). Ze stejného důvodu jako ořez výš: v překryvu se
  neuplatní nic, co leží nad prvkem. Bublina, náhled videa, dlaždice alba, odpověď ankety i mřížka
  v profilu měly `.clip(tvar)` PŘED `sharedNavElement`, takže se fotka z prohlížeče vracela
  s hranatými rohy a zaoblila se až po dosednutí; prohlížeč má zaoblení uvnitř, a proto bylo
  otevírání v pořádku. Rohy se během letu přelévají z karty prohlížeče (`flightFromCorner`) do
  tvaru cíle — jen při spárovaném přechodu, jinak platí cílový tvar. Průběh se čte ve vrstvě,
  let nic nerekomponuje.
  - **Dlaždice alba zaobluje mřížka, ne dlaždice** — tvar pro let jí proto počítá
    `albumTileShape` (testy `AlbumTileShapeTest`): rohy mřížky jen tam, kam dlaždice sahá.
  - Ověřeno sérií surových snímků se zpomalenými animacemi: samostatná fotka tam i zpátky
    a pravá dlaždice alba (zaoblená jen vpravo).
- **Ořezová plocha je VIDITELNÁ část seznamu, ne jeho hranice** (`SharedElementClip.obscuredTop`
  / `obscuredBottom`, `visibleArea`, testy `VisibleAreaTest`). Seznam zajíždí pod vstupní pole
  i pod pruh připnutých zpráv, takže jeho hranice sahají až ke spodku obrazovky: fotka ležící
  zpola pod polem se při návratu z prohlížeče kreslila PŘES pole a na konci přechodu skočila za
  něj — tatáž chyba jako u hlavičky, jen dole. Obrazovka proto po každé kompozici zapíše výšku
  obou pásů (výška pole se mění s klávesnicí). Pod polem je sklo, takže po dosednutí je dole
  vidět rozostřený kus fotky; to je nepatrný rozdíl proti skoku přes celé pole. Ověřeno pixelem
  uprostřed karty pole během zpomaleného návratu: před opravou ho fotka od půlky letu přebarvila
  až do konce přechodu, po opravě ani jednou.
- **Dole se ale neořezává rovnou čarou, fotka zajede POD kartu pole** (`SharedElementClip.band`,
  `occludesSharedElements`, `openBand`). Čára u horní hrany pásu useknula fotku v půlce letu nad
  kartou a vypadalo to, že do pole zalézá (hlášeno videem). Pás pod polem je průhledné sklo,
  takže se ořez rozšíří až na jeho spodek a vyřízne se z něj jen to, co fotku opravdu přikrývá:
  tvar karty, tvar tlačítka (i sušenka a vlnka při nahrávání — ořez si tvar čte při kreslení)
  a všechno pod nimi. Výřezy rostou s letem od spodku, jinak by na začátku, dokud fotka ještě
  stojí v prohlížeči, byla v ní vidět díra ve tvaru pole.
  - **Jen když pás leží hned pod seznamem.** Nad polem můžou být panely (návrhy odpovědí,
    klávesnice bota) a přes ty se letět nesmí; pak platí čára jako dřív. Totéž, když místo pole
    stojí jiná lišta (výběr, hledání, „Přidat se") — ta tvary nehlásí.
  - **Odscrollovat konverzaci, aby fotka dosedla celá, NENÍ řešení** (zkoušeno, uživatel
    odmítl): návrat z prohlížeče nemá měnit místo, kde uživatel čte.
  - V proužcích skla kolem karty letí fotka ostrá a po dosednutí ji převezme rozostřený seznam.
    Je to pár dp a pod kartou mizí tak jako tak, skok vidět není. Ověřeno sérií surových snímků
    se zpomalenými animacemi.
- **Ořez se ale nesmí zapnout skokem**, jinak hned v prvním snímku ukrojí kus celoobrazovkové
  fotky a odkryje pod ní hlavičku, která tam ještě nepatří. Otevřený ořez se proto nebere jako
  nějaké velké číslo, ale jako **obal plochy a samotného prvku** — ten prvek z definice obsahuje
  celý, takže na začátku neukrojí nic — a stahuje se na plochu toutéž pružinou jako let
  (`sharedElementClipRect`, testy `SharedElementClipTest`). Fotka tím pod hlavičku podjede.
- **Ořezává se jen strana, která PŘICHÁZÍ**, a to stačí: `sharedElement` kreslí v překryvu právě
  prvek, který se stává viditelným. Při otevírání prohlížeče je to fotka v prohlížeči, a ta
  žádnou plochu nedostává — skrytý proužek nad hlavičkou tam na okamžik probleskne, jenže fotka
  se v té chvíli tak jako tak zvětšuje přes celou obrazovku, takže to není vidět. Vadí to jen
  při návratu, kde prvek na svém místě ZŮSTANE stát.
- **Průběh ořezu jde z `AnimatedVisibilityScope.transition`** (`animateFloat` nad
  `EnterExitState`), ne z bounds prvku: `OverlayClip.getClipPath` dostane jen AKTUÁLNÍ hranice,
  ne cílové, takže z nich není jak poznat, jak daleko let došel.

### Poloha a mapy

Zpráva s polohou, místem (`MessageVenue`) a živou polohou je `MessageMedia.Location` a kreslí se
**statickou mapou** se špendlíkem uprostřed (`StaticMap` v `core/ui`, `LocationMap`); tentýž
kus kreslí místo v anketě i jeho náhled ve formuláři. Ťuknutí otevře mapy platformy.

- **Mapu kreslí Telegram** (`getMapThumbnailFile` → `FileRepository.mapThumbnail`): PNG soubor
  jako každý jiný, bez klíče k mapovému API a bez dotazu na cizí službu. Je to obrázek — neposouvá
  se ani nepřibližuje; interaktivní mapa by chtěla mapové SDK (a klíč), které aplikace nemá.
- **Mapa se žádá přesně na změřenou plochu** (`BoxWithConstraints`), v bodech zaokrouhlených na
  8 a v hustotě displeje (`scale` 1–3). Meze TDLib (zoom 13–20, strana 16–1024) se hlídají
  v `StaticMap` — mimo ně TDLib vrátí chybu, ne oříznutí. `fileId` výřezu si repository pamatuje
  (mapa se skládá při každém návratu položky do seznamu) a zahazuje s výměnou klienta.
- **Mapa jde na plochu bez bubliny jako fotka** (`showsBareMedia`), název a adresa místa pod ní
  jsou karta v barvě bubliny (`LocationDetails`), jako popisek fotky. Šířka je STÁLÁ (280 dp,
  poměr 0,6) — s názvem i bez něj, jinak by karta s popiskem vyšla širší než holá mapa vedle ní.
  Text zprávy je prázdný (`captionBody`): „Poloha" pod mapou by ji jen popisovalo podruhé;
  seznam chatů a citace dál píšou „Poloha" / název místa.
- **Mapa je vždycky světlá**, i v tmavém motivu (tmavé dlaždice Telegram nemá). Špendlík proto
  nebere `primary` natvrdo — v tmavém motivu je světlá — ale tmavší z `primary` / `inversePrimary`.
- **Živá poloha je taky jen obrázek.** Posun chodí jako úprava zprávy, takže se mapa přestaví
  sama; pod ní „Sdílí se do 14:21" / „Sdílení skončilo". Konec je `datum zprávy + livePeriod`
  (`liveLocationUntil`, testy `LiveLocationTest`), ne `expiresIn` — ten TDLib změnou neposílá,
  takže by zastaral. „Upraveno" se u živé polohy neukazuje, svítilo by pořád.
- **Známý směr pohybu (`LiveLocation.heading`, 1–360 po směru hodin od severu) je šipka místo
  špendlíku** (`StaticMap(heading)`, ikona `navigation`), na bílém kolečku — mapa je vždycky
  světlá, ale park nebo voda pod šipkou ne. Špendlík ukazuje místo hrotem, šipka středem, takže
  se neposouvá. Jen dokud se sdílí: u skončené polohy by směr tvrdil, že se někdo pořád hýbe.
- **Jestli se ještě sdílí, rozhoduje `rememberLiveSharing`, ne porovnání při složení.**
  Zastavené sdílení (`stopMessageLiveLocation`) přijde úpravou s `livePeriod` zkráceným na
  okamžik zastavení, takže konec padne do téže vteřiny jako složení a „už skončilo" vyšlo
  naprázdno — pod mapou zůstalo „Sdílí se do 14:08". Teď se jednou počká (`delay`) na konec
  a mapa i text se přepnou samy, i u polohy, která vyprší před očima. **Hodiny emulátoru jdou
  proti Macu klidně o 40 s pozadu** — na konec se čeká podle `adb shell date`.
- **Adresa map je platformní** (`mapsUrl` v `core/location`): Android Google Mapy přes https
  (app link do aplikace Map), iOS `maps.apple.com`. `geo:` iOS nezná a na Androidu by neprošel
  cestou odkazu z konverzace — ta umí jen adresy s `://`.
- **Mapa má vlastní klikání, takže opakuje dlouhý stisk** — jinak by nad ní nešla otevřít
  nabídka zprávy (tatáž past jako u fotky). Upravit polohu nejde (`carriesCaption` = false).
- **Ověřuje se botem**: `sendLocation`, `sendVenue`, `sendLocation` s `live_period`, a anketa
  s `media` typu `venue` / `location`. Ověřeno na emulátoru: všechny tři druhy zpráv, mapa
  u otázky i odpovědi ankety, náhled ve formuláři, ťuknutí do Google Map (i bez hlasu u
  odpovědi), dlouhý stisk s nabídkou. **Posun živé polohy ověřený není** (`editMessageLiveLocation`
  botem by šel) a **iOS jen přeložené** — Apple Mapy se na simulátoru neotevíraly. Odeslání ze
  sponky ověřené na emulátoru: místo i „Moje poloha" odešly a bot přes `getUpdates` dostal
  `venue` (s `foursquare_id`) a holou `location`.
- **Poslat polohu jde ze sponky** (`AttachButton` → „Poloha", `ConversationViewModel.sendLocation`).
  Je to tentýž sheet jako místo v anketě (`PlacePickerHost` nad `PlaceSearcher` — hledání
  kolem polohy zařízení, sdílí ho formulář ankety i konverzace): „Moje poloha" odejde jako
  `InputMessageLocation`, místo z hledání jako `InputMessageVenue` i s id a typem od
  poskytovatele (server ho pak vydá dál, Bot API ukázalo `foursquare_id`). Odchází hned, jako
  samolepka: citaci spotřebuje, rozepsaný text nechá. **V konverzaci si pole hledání fokus
  nebere** (`autoFocusSearch = false`) — hlavní akce je „Moje poloha" a klávesnice by schovala
  místa v okolí; v anketě se místo hledá podle názvu, tam ano.
- **Sdílení ŽIVÉ polohy** je „Sdílet živou polohu" pod „Moje poloha" ve výběru polohy
  (jen Android a ne v kanálu), pak doba (`LiveLocationPeriodDialog`: 15 min, 1 h, 8 h, „dokud ji
  nevypnu" = `LIVE_FOREVER`) a přesná poloha zařízení. Odchází `InputMessageLiveLocation`
  (`sendLiveLocation`), viz „Sdílení živé polohy" níž.

#### Sdílení živé polohy

Polohu do zpráv posílá služba v popředí `LiveLocationService` (`core/location`, typ `location`),
seznam zpráv drží `LiveLocationRepository` (`core/telegram/LiveLocation.kt`, testy
`LiveLocationSharingTest`). Zastavit jde z notifikace služby (všechno) a tlačítkem „Zastavit
sdílení" pod vlastní živou polohou (`MessageAction.StopLiveLocation`).

- **Které zprávy se mají posouvat, drží TDLib** (`UpdateActiveLiveLocationMessages`, s databází
  zpráv přežije restart). Dotaz na ten seznam v TDLib 15 NENÍ — jen update, proto je repository
  eager. Služba ho odebírá a s prázdným seznamem skončí sama (vypršelo, zastaveno v bublině nebo
  na jiném zařízení). Zastavení je `editMessageLiveLocation(location = null)`.
- **Při posunu se `livePeriod` posílá zpátky beze změny** — jinak by úprava dobu sdílení měnila.
- **Oprávnění k poloze na pozadí potřeba NENÍ.** Služba typu `location` spuštěná z popředí si
  nese přístup „při používání"; proto ji `LiveLocationControls` spouští, jen když je aplikace
  v popředí a je co sdílet (i po restartu aplikace — služba po `force-stop` naskočí s prvním
  otevřením). Z pozadí by ji Android 14 nepustil. Když spuštění selže (oprávnění vzaté zpátky),
  sdílení se zastaví — stojící „živá" poloha by klamala.
- **Posílá se jen to, co je znát** (`shouldSendLiveFix`): poloha se čte po 5 s, ale úprava zprávy
  odchází až při pohybu o 30 m (nejdřív po 10 s), bez pohybu jednou za 2 min, a hned po
  `UpdateMessageLiveLocationViewed` (TDLib to tak chce). Směr (`headingOf`) jen při pohybu nad
  0,5 m/s; sever je 360, nula je „neznámý".
- **Notifikace služby má kanál `live-location` s `IMPORTANCE_DEFAULT`** a je `setSilent`. S LOW
  skončila ve sbalené skupině „Tiché", a o sdílené poloze má uživatel vědět.
- **Úprava zprávy trvá i přes deset vteřin** (log: posláno 14:25:07, potvrzeno 14:25:21), takže
  se ověřuje se zpožděním — Bot API `getUpdates` hned po posunu nic neukáže.
- **iOS sdílení nenabízí** (`liveLocationSharingSupported = false`): bez
  `allowsBackgroundLocationUpdates` a režimu `location` v `UIBackgroundModes` by se poloha
  zastavila s odchodem z aplikace. Cizí živou polohu iOS kreslí dál.
- **Ověřeno na emulátoru** (Android 17) testovacím poskytovatelem polohy (viz „Vlastní klávesnice
  bota") v chatu s testovacím botem: 15 min i „dokud ji nevypnu", posuny s aplikací v popředí
  i na ploše (proces nezamrzl, bot dostal `edited_message` s novou polohou), obnova služby po
  `force-stop`, „Zastavit" z notifikace (konečná poloha bez `live_period`) a z bubliny.
  **Ťukat po spuštění aplikace podle
  souřadnic se nevyplatí:** notifikace služby otevřená z lišty vrátila seznam chatů a ťuknutí
  mířené na sponku otevřelo chat se skutečným člověkem. Nabídka sponky je popup, který
  `uiautomator dump` nevidí — ověřit snímkem, že je otevřený správný chat, a teprve pak ťukat.
- **Na přetíženém emulátoru se první dlouhý stisk ztratí** („spent 2442ms processing
  MotionEvent" v logu) a vypadá to jako chybějící nabídka. Zopakovat, než se hledá chyba.

#### Upozornění na blízkost

Pod vlastní živou polohou je vedle „Zastavit sdílení" „Upozornit na blízkost" (se zapnutým
„Upozorním do 500 m") a dialog se vzdálenostmi 100 m – 5 km a „Vypnout upozornění"
(`ProximityAlertDialog`, `MessageAction.SetProximityAlert`, `LiveLocationRepository
.setProximityAlert`). Servisní zpráva o spuštění říká vzdálenost („Upozornění na blízkost: 48 m",
`distanceUiText` v `core/ui/Distances.kt`, testy `DistanceTest`), jména dál ne.

- **Poloměr je součást živé polohy** (`LiveLocation.proximityAlertRadius`, 0 = vypnuto,
  nejvýš 100 km) a nastavuje se úpravou zprávy se STEJNOU polohou, dobou a směrem (bere se ze
  zprávy, ne ze zařízení). **Každý posun polohy ho musí poslat zpátky**, jinak ho vypne.
- **Server upozornění po spuštění SÁM VYPNE** (zpráva přijde s poloměrem 0)
  a `UpdateActiveLiveLocationMessages` kvůli tomu NEPŘIJDE. Dokud posun bral poloměr ze seznamu
  aktivních poloh, zapnul ho dalším posunem znovu — na emulátoru po prvním upozornění svítilo
  „Upozorním do 500 m" dál. Posun proto čte poloměr i dobu z aktuální zprávy (`getMessage`,
  lokální), seznam je jen záloha.
- **Kdy upozornění jde, ví zpráva** (`MessageMedia.Location.proximityAlertRadius`, `null` = nejde,
  `canSetProximityAlert`): jen vlastní poloha, ne v kanálu a ne v Uložených zprávách. „Direct
  messages chats" z dokumentace TDLib jsou přímé zprávy KANÁLU, ne soukromé chaty — chat
  s botem i s člověkem upozornění bere.
- **Spouští ho druhý účastník, který se PŘIBLÍŽÍ** (jeho posun do poloměru). Zapnutí poloměru
  ve chvíli, kdy je druhý už blízko, nespustí nic, a tatáž dvojice zpráv po vypnutí a novém
  zapnutí podruhé nespustila — zkouší se čerstvou dvojicí zpráv.
- **Ověřeno na emulátoru** testovacím poskytovatelem polohy a botem (`sendLocation`
  s `live_period` daleko, pak `editMessageLiveLocation` k nám): servisní zpráva „31 m",
  poloměr přežije posun vlastní polohy, po spuštění zůstane vypnutý, „Vypnout upozornění".
  Notifikace o spuštění jde cestou běžných zpráv (TDLib), zvlášť ověřená není. **iOS jen
  přeložené.** Bot v chatu s ním smaže i NAŠE zprávy (`deleteMessage` vrátil `true`),
  servisní zprávy o blízkosti ne — v chatu s testovacím botem zůstaly.
- **Ťuknutí na vzdálenost v dialogu podle souřadnic se může trefit do mapy pod ním**, když se
  dialog ještě neotevřel — otevřely se Google Mapy. Před ťuknutím ověřit dumpem text dialogu.

#### Dock živé polohy

Zpráva se živou polohou, která odjede z viditelné části konverzace, se plynule přemění v řádek
pod pruhy nad konverzací (pod připnutými): špendlík, kdo polohu sdílí a do kdy
(`LiveLocationDockBar`, pravidla v `LiveLocationDock.kt`, testy `LiveLocationDockTest`). Řádek
se rozbalí na mapu s „Přejít na zprávu" a u vlastní polohy „Zastavit sdílení" a „Upozornit na
blízkost". Když se zpráva scrollem vrátí do pohledu, řádek plynule zmizí.

- **Které polohy, drží ViewModel, ne načtená historie** (`ConversationState.liveLocations`).
  Zpráva s polohou bývá dávno odscrollovaná nebo mimo okno. Při otevření je dá
  `searchChatRecentLocationMessages` (`recentLiveLocations`, vrací i SKONČENÉ — filtruje
  `withLiveLocation` podle `liveUntil`), pak nové a upravené zprávy a vlastní sdílení
  z `LiveLocationRepository.active`. Zastavená poloha přijde úpravou se zkrácenou dobou a odejde
  sama, prošlou odebere odpočet na nejbližší konec (`nextLiveEnd`). Ve vlákně komentářů dock
  není, v tématu jen polohy tématu.
- **Kolik ze zprávy je schované, je průběh docku** (`dockProgress`): podíl zprávy za horní
  hranou (spodek pruhů) nebo za spodní (pole). Seznam je obrácený, offset roste odspodu.
- **Horní hrana se měří BEZ řádku docku** (`barsHeight` je vnitřní sloupec pruhů). Řádek se
  vysouvá s průběhem a s ním i odsazení seznamu; kdyby hrana šla s ním, zakryl by další kus
  zprávy, ta by se schovala víc… a z plynulého přechodu by byl skok.
- **Kompozice čte jen MNOŽINU id v docku** (`derivedStateOf` se `structuralEqualityPolicy`),
  průběh čte až layout (`reveal` jako lambda, `placeRelativeWithLayer`). Jinak by každý snímek
  scrollu rekomponoval sloupec pruhů. Velký skok průběhu (otevření chatu, skok na zprávu,
  `DOCK_JUMP`) se dojede pružinou, scroll jde s prstem.
- **Žádný závoj přes konverzaci.** První verze rozbalený dock podložila scrimem jako sheet;
  uživatel ho odmítl — kouzlo je v tom, že mapa je vidět a chatuje se dál. Rozbalený dock
  zavře šipka, zpět, „Přejít na zprávu" a návrat zprávy do pohledu (`dockedIds` prázdné).
- **Stín pod průsvitným sklem prosvítá DOVNITŘ plochy.** Rozbalená vysoká karta se 3dp stínem
  měla vpravo a dole ztmavlý pruh, posunutý vůči kartě — vypadalo to jako špatně umístěná
  vrstva (`placeRelativeWithLayer`), a nebyla to ona. Stín má proto jen sbalený řádek.
- **Ověřeno na emulátoru** (anglicky) živou polohou od testovacího bota (`sendLocation`
  s `live_period`) a šestnácti zprávami za ní: vysunutí při scrollu i po znovuotevření chatu,
  rozbalení s mapou, scroll pod rozbaleným dockem, sbalení a zmizení při návratu zprávy,
  „Přejít na zprávu" se zvýrazněním. **Vlastní poloha v docku (zastavit, upozornit) ověřená
  není** — tlačítka jsou tatáž cesta jako pod zprávou (`StopLiveLocation`,
  `SetProximityAlert`). iOS jen přeložené.

#### Mini přehrávač v docku

Hrající hudba nebo hlasovka, jejíž zpráva odjede z obrazovky, je ve sloupci pruhů pod dockem
polohy jako řádek se stejným sklem (`PlayerDockBar`): tlačítko s průběhem po obvodu (u skladby
přes obal), název a „interpret · 0:04 / 0:16", u skladby „další" a vždycky ✕. Ťuknutí na text
skočí na zprávu. Kdo do docku patří, je `VoicePlayback.docksIn` (testy `PlayerDockTest`).

- **Vysouvání a odsazení jsou společné s dockem polohy** (`Modifier.dockReveal`, rozměry
  `DOCK_*` v `DockRow.kt`). Průběh je `dockProgress` nad položkou s klíčem BLOKU
  (`blockKeyOf`) — skladba z alba je položka nejstarší zprávy alba.
- **Skladba z jiného chatu je v docku celá** (průběh 1), protože hudba hraje dál i po odchodu
  z konverzace a jinde než tady (a v notifikaci) by nešla zastavit. Ťuknutí na ni otevře její
  chat se skokem na zprávu. Hlasovka z jiného chatu ne — odchodem z chatu dohraje.
- **Náhled nahrávky (zpráva −1) do docku nepatří**, má vlastní pruh nad polem.
- **Kompozice čte jen KLÍČ hrajícího zvuku** (`derivedStateOf` se `structuralEqualityPolicy`),
  průběh a čas čte až řádek přes lambdu — pozice se mění dvacetkrát za vteřinu.
- **Název skladby z jiného chatu bere `MusicQueue.nowPlaying`**, ne načtenou historii: zpráva
  z jiného chatu v ní není.
- **✕ u hudby jde přes `MusicQueue.stop`**, u hlasovky přes `VoicePlayer.stop` — fronta jinak
  po zastavení navázala další skladbou.
- **Kolečko se zvukem hraje v docku dál, i obrazem** (`SoundingVideoNote`,
  `rememberSoundingVideoNote` v `items/SoundingVideoNote.kt`): v kolečku na místě tlačítka běží
  živé video s průběhem po obvodu, „Notifikator · Video zpráva · 0:23 / 0:39".
  - **Přehrávač kolečka se zvukem patří OBRAZOVCE, ne položce.** Dokud ho držela položka,
    kolečko s odscrollováním zmlklo (přehrávač odešel s kompozicí) a dock neměl co ukázat.
    Bublina si ho bere přes `ConversationItemScope.soundingVideoNote`, tichou smyčku dál hraje
    vlastním přehrávačem. Kopie v nabídce nad zprávou dostává `null` — hraje potichu.
  - **Obraz kreslí VŽDYCKY JEN JEDNA plocha** (`SoundingVideoNote.docked`): video jde na Androidu
    do `TextureView` a přehrávač má jediný výstup — plocha, která by o něj přišla, zůstala viset
    na posledním snímku. Předává se v polovině vysunutí docku (`VIDEO_NOTE_HANDOFF`), takže
    v bublině odjíždějící z obrazovky i v kolečku docku je obraz vždycky jen tam, kde je víc vidět.
  - **Zvuk vrací jen odchod z obrazovky, dohrání a ✕** (`VideoNoteSoundEnded`), ne odscrollování.
    Pozastavené kolečko se v bublině i v docku pustí dál ťuknutím, nezačíná znovu.
  - **Ověřeno na emulátoru** čtyřicetivteřinovým kolečkem od testovacího bota: dock s živým
    obrazem a časem, pauza a pokračování z docku, ✕ (bublina se vrátí do tiché smyčky), dohrání
    (dock zmizí), návrat scrollem (bublina hraje dál, čas běží), skok na zprávu ťuknutím. iOS jen
    přeložené.
- **Ověřeno na emulátoru** (anglicky) dvěma skladbami a hlasovkou od testovacího bota: vysunutí
  při scrollu, pauza, „další" (u poslední skladby není), skok na zprávu ťuknutím (dock zmizí,
  zpráva zvýrazněná), ✕, skladba z Notifikátora v docku Uložených zpráv. iOS jen přeložené.

#### Přehrávač nad seznamem chatů

Hudba, která hraje dál po odchodu z konverzace, má nad seznamem chatů tentýž řádek
(`PlayerDockBar`, `NowPlayingViewModel`): dole pod FAB menu, sklo nad seznamem.
Ťuknutí na text otevře chat se skokem na skladbu.

- **Jen hudba.** Hlasovka odchodem z chatu dohraje (`stop(chatId, keepMusic = true)`), takže
  nad seznamem by nebylo co ukázat. Zdrojem je `MusicQueue.nowPlaying`, ne `VoicePlayer`.
- **Dole, ne v hlavičce.** Pilulka hledání při scrollu odjíždí a řádek by s ní zmizel; dole je
  navíc pod palcem. FAB se nad něj posune (je ve stejném sloupci), seznam má dole místo navíc
  o změřenou výšku řádku, aby poslední chat nezůstal schovaný.
- **S otevřenou konverzací vedle (dvoupanel) se neukazuje** (`conversationOpen` z `AppShell`):
  konverzace má vlastní dock a dva přehrávače vedle sebe by říkaly totéž dvakrát.
- **Sklo rozostřuje jen seznam chatů** (vlastní `HazeState`), stejně jako v konverzaci.
- **Obal skladby se předává jako soubor** (`TrackInfo.coverFileId`). Fronta měla jen bitmapu pro
  systémové ovládání; řádek si obal stáhne sám přes `RemoteImage`.
- **Řádek mizí i se svým místem**, takže FAB po dohrání sjede dolů na jeho místo. Ťuknutí na ✕
  ve chvíli, kdy skladba právě dohrála, proto trefí FAB a otevře menu „Vytvořit" — neškodné,
  jen se na to při ověřování přes `adb` nesmí zapomenout.
- **Ověřeno na emulátoru** (anglicky) skladbami od testovacího bota: řádek po odchodu z chatu,
  pauza, „další" (u poslední není), ✕ za hraní, zmizení po dohrání, ťuknutí na text otevře
  chat se skladbou. Dvoupanel a iOS ověřené nejsou (iOS jen přeložené).

### GIFy

GIF je v Telegramu **MP4 bez zvuku** (`MessageAnimation`), ne skutečný GIF — přehrává se proto
týmž přehrávačem jako video (`MessageMedia.Animation`, `AnimationBubble`).

- **Hraje sám, dokud je vidět, ve smyčce a ztlumeně.** GIF, který se nehýbe, není GIF. Přehrávač
  žije s kompozicí bloku, takže odscrollovaný GIF dekodér vrátí. `LazyColumn` skládá i kousek za
  viewportem, takže pár GIFů hraje mimo obrazovku — proti sledování skutečné viditelnosti je to
  levnější a rozdíl je neznatelný.
- **Ztlumený obsah NEŽÁDÁ o audio focus** (Android `handleAudioFocus = !muted`) a nepřepíná
  kategorii `AVAudioSession` (iOS). Ztlumený GIF nemá co ztišit hudbu, která uživateli hraje —
  a ExoPlayer si o fokus řekne i při nulové hlasitosti.
- **Smyčku `AVPlayer` neumí sám.** Řeší ji pozorovatel `AVPlayerItemDidPlayToEndTimeNotification`,
  který se drží silnou referencí a odhlašuje při uvolnění přehrávače — `NSNotificationCenter` si
  pozorovatele nedrží. `AVPlayerLooper` by znamenal `AVQueuePlayer`.
- **Velký GIF se sám nestahuje** (`MAX_AUTOPLAY_ANIMATION_BYTES`, 5 MB; testy `MessageRowsTest`).
  Autoplay by bez ptaní snědl data, takže nad stropem — a u neznámé velikosti — zůstane náhled
  a čeká na ťuknutí. Ťuknutí na hrající GIF ho pozastaví, jinak by nešel zastavit vůbec.
- **GIF jde na plochu bez bubliny** jako fotka a video (`showsBareMedia`): je to obrázek sám
  o sobě. Od videa ho odliší značka „GIF" v rohu a to, že nemá délku.
- **Přehrávání se na emulátoru ověřit NEDÁ**, když je stroj zatížený: `c2.goldfish.h264.decoder`
  selže na `queueInputBuffer error: 14` a ExoPlayer uvízne ve `STATE_BUFFERING` (délka souboru
  je přitom známá). Pozná se to podle toho, že **stejně selže i video v prohlížeči médií** —
  tedy cesta, která s GIFy nesouvisí. Ověřit jde vzhled, značka a přepínání pauzy.

### Video kolečka

`MessageVideoNote` je `MessageMedia.VideoNote` a kreslí se bez bubliny jako kolečko 200 dp
(`VideoNoteBubble`, cesta přes `StickerMessageItem` — s citací nebo přeposláním v bublině).

- **Samo hraje ve smyčce a potichu, jako GIF** — stejný přehrávač, stejný strop velikosti
  (`autoPlays`), nad ním se nestahuje. Dole je délka s přeškrtnutým reproduktorem, jinak by
  nebylo poznat, že ťuknutí pustí zvuk.
- **Ťuknutí = zvuk od začátku, bez smyčky, s průběhem po obvodu**; další ťuknutí nebo dohrání
  vrátí tichou smyčku. Přepnutí je nové `load` nad týmž přehrávačem (Media3 i AVPlayer to
  snesou), takže video začne znovu od nuly.
- **Se zvukem hraje jedno kolečko na konverzaci a rozhoduje ViewModel**
  (`ConversationState.soundingVideoNote`), ne kolečko samo: ví i o hlasovkách. Kolečko se
  zvukem zastaví hlasovku, hlasovka a nahrávání umlčí kolečko.
- **Kolečko se zvukem hraje dál i odscrollované** — přehrávač má obrazovka a kolečko přejde do
  docku (viz „Mini přehrávač v docku"). Zvuk vrací (`VideoNoteSoundEnded`) až odchod
  z obrazovky, dohrání nebo ✕; jinak by ViewModel dál věřil, že hraje, a po návratu by se
  kolečko samo rozeznělo.
- **Audio focus drží jen kolečko se zvukem** — ztlumený přehrávač o něj nežádá (viz „GIFy"),
  takže tichá smyčka nezastaví hudbu. Ověřeno `dumpsys audio`: focus stack je prázdný při
  smyčce, `GAIN` od Plume jen se zvukem a po dohrání zase prázdný.
- **Zhlédnutí se hlásí jako poslech hlasovky** (`openMessageContent` při prvním puštění se
  zvukem, `isViewed`); potvrzení chodí týmž `contentOpenedUpdates`.
- **Ověřuje se botem**: `sendVideoNote` s MP4. Bez ffmpeg se dá vyrobit na Macu: `adb shell
  screenrecord --size 480x480` **při pohybu na obrazovce** (statickou obrazovku screenrecord
  nezapíše — vyšlo video o 0,4 s) a zvuk z `say -o x.aiff`, spojené přes `AVMutableComposition`
  + `AVAssetExportSession` v krátkém Swift skriptu. Nahrává se obrazovka emulátoru, takže jen
  plocha nebo chat s botem, nic s kontakty.
- **Ověřeno na emulátoru**: tichá smyčka, zvuk s průběhem a uplynulým časem, návrat po dohrání
  i druhým ťuknutím, nabídka dlouhým stiskem. **Neověřeno**: zhlédnutí u odesílatele (bot ho
  nevidí), souhra s hrající hlasovkou, přepis řeči (`speechRecognitionResult`) a iOS
  (jen přeložené).

### Nahrávání koleček

Ťuknutí na mikrofon v prázdném poli přepne na kameru (a zpátky), podržení nahraje kolečko:
nad konverzací je náhled z kamery v kruhu s průběhem po obvodu, ovládá se stejně jako hlasovka
(puštění, tah doleva, tah nahoru — viz „Nahrávání"). Volba se pamatuje
(`AppSettings.recordMode`). Hranice je `core/camera` (`VideoNoteCamera`): CameraX na Androidu,
AVFoundation na iOS.

- **Hlasovka i kolečko jdou tímtéž `VoiceRecorder`** nad rozhraním `MediaCapture` (čas, pauzy,
  zahození krátkého). Kamera ale žije s KOMPOZICÍ (potřebuje vlastníka životního cyklu a plochu
  pro náhled), nahrávání s ViewModelem — proto `CaptureSlot`: obrazovka kameru připojí, a když se
  kompozice rozloží, rozpracované kolečko se zahodí. Otočení Foldu do dvoupanelu tak kolečko
  zahodí (hlasovka naopak běží dál zamčená).
- **Kamera se otevírá až se `start`**, ne s náhledem nebo konverzací — indikátor soukromí by
  jinak svítil pořád. `ProcessCameraProvider.getInstance` při otevření chatu kameru neotevírá,
  jen inicializuje CameraX, takže je do podržení hotová.
- **Čtverec dělá `ViewPort` 1 : 1** nad náhledem i `VideoCapture` — kolečko obsahuje přesně to,
  co bylo v kruhu vidět (ověřeno: výstup 720 × 720). Přední kamera se zrcadlí i ve videu
  (`MIRROR_MODE_ON_FRONT_ONLY`), nahrávání je persistent a přežije přepnutí kamery.
- **Náhled je `PreviewView` v režimu `COMPATIBLE` (`TextureView`)**, jinak by ho ořez do kruhu
  v Compose neořízl — `SurfaceView` je samostatné okno.
- **Telegram kolečko větší než 640 px PŘEVEDE NA OBYČEJNÉ VIDEO.** TDLib stranu od 640 výš
  odmítne rovnou („Wrong video note length"); a když se `length` zastropoval, ale soubor zůstal
  720 × 720, server zprávu přijal — jako `video`, ne `video_note` (ověřeno Bot API), takže
  u všech byla obdélníkem s tlačítkem přehrání. Soubor proto musí být opravdu malý.
- **CameraX výstup zmenšit neumí, jen oříznout.** Kvalita je SD (480p → 480 × 480), s fallbackem
  radši nižším; kamera bez SD (emulátor, část telefonů) ale jinak než 720p nenatočí. Takové
  kolečko se po nahrání přepočítá na 480 × 480 přes Media3 Transformer (`shrinkIfNeeded`) —
  jen když je potřeba. Minuta na emulátoru: do deseti vteřin od konce nahrávání i s nahráním
  na server.
- **Po minutě kolečko odejde samo** (`VIDEO_NOTE_MAX_DURATION_MS`, víc Telegram nepřijme),
  pozastavený čas (nabídka, výběr času) se nepočítá. Ověřeno: 60 s, 480 px, 1,9 MB.
- **Délka, strana a náhled se čtou až z hotového souboru** (`prepareVideoNote`), ne z časovače.
- **iOS: `AVCaptureMovieFileOutput` do `.mov` a export do čtvercového MP4** se středovým ořezem
  (`AVMutableVideoComposition` po `preferredTransform`, strana nejvýš 480). **Přepnout kameru
  během nahrávání tam nejde** — výstup do souboru při výměně vstupu nahrávání ukončí; umělo by
  to jen `AVAssetWriter`. **K/N interop:** vlastnosti `AVMutable…` jsou pro Kotlin `val` ze
  základní třídy a zapisují se settery (`setRenderSize`, `setInstructions`…), `videoComposition`
  u exportu je z kategorie (`setVideoComposition` s vlastním importem) a `kCMTimeZero` je
  struktura bez `readValue` — nahrazuje ho `CMTimeMake(0, 1)`.
- **iOS přidává vstupy (`AVCaptureDeviceInput`) až při prvním nahrávání**, ne s konverzací:
  bez uděleného oprávnění vstup selže, nebo si o oprávnění systém řekne sám — a chat se otevírá
  mnohem častěji, než se nahrává.
- **Simulátor kameru nemá**, takže se tam kolečko nenabízí vůbec (`rememberVideoNoteCamera`
  vrátí `null`) a iOS je jen přeložené a slinkované, **na zařízení neověřené**.
- **Oprávnění ke kameře musí být v manifestu i s `uses-feature … camera required=false`**,
  jinak by z kamery udělalo povinný hardware a aplikace by zmizela z Play pro zařízení bez ní.
  Pozor při ověřování: APK sestavené PŘED úpravou manifestu oprávnění nemá a systém se na kameru
  vůbec nezeptá — výsledek pak vypadá jako odmítnuté oprávnění.
- **Zamčené kolečko jde zastavit a přehrát před odesláním** (■ v pruhu, `stopRecordingForPreview`
  → `VideoNotePreview`, `VideoNotePreviewBar`, kruh v `VideoNoteRecordingOverlay`). Místo kamery
  hraje v kruhu nahrané kolečko, a to **jako v bublině**: potichu ve smyčce, ťuknutí na kruh nebo
  na ▶ v kartě ho pustí od začátku se zvukem a s průběhem po obvodu, po dohrání zase potichu.
  Uživatel tak vidí přesně to, co protějšek dostane.
  - **Připravuje se hned po ■** (`prepareVideoNote`), ne až při odeslání: náhled přehrává hotový
    soubor. Mezistav (`note == null`) točí indikátor v kruhu i v kartě a odeslat v něm nejde —
    zmenšení na Androidu i ořez na iOS trvá vteřiny. Koš během dokončování soubory smaže, když
    doběhnou (porovnání instance mezistavu, jako u hlasovky).
  - **Stav přehrávání drží obrazovka** (`VideoNotePreviewPlayback`), ne ViewModel: čtou ho dvě
    místa (kruh s přehrávačem a karta s tlačítkem a časem) a přehrávač žije v kompozici.
  - **Po ■ je `recordingKind` pořád kolečko** (i bez běžícího nahrávání), jinak se odesílací
    tlačítko čtečce hlásilo jako „odeslat hlasovou zprávu".
  - **Odeslání i nabídka podržení jdou přes `sendRecording`**, takže naplánovat nebo poslat bez
    zvuku jde i zastavené kolečko. Koš a odchod z chatu smažou video i náhledový snímek.
  - **Ověřeno na emulátoru** (Pixel_10_Pro_XL, emulovaná kamera) v chatu s botem: zamknout, ■,
    tichá smyčka, zvuk s průběhem a časem (audio focus drží jen po dobu zvuku), návrat po dohrání,
    odeslání (bot dostal `video_note`, 480 px, 19 s) a koš (soubory pryč). iOS jen přeložené.
  - **Po přehrání jde nahrávat dál** (kamera na konci pruhu náhledu, `continueVideoNotePreview`)
    — totéž co u hlasovky, jen spojuje kamera (`VideoNoteCamera.join`) do nového souboru vedle
    nového úseku a vstupy maže až po úspěchu. Náhledový snímek starého kolečka jde pryč hned,
    nový se přečte z výsledku. Kolečko pod minutovým stropem (méně než vteřina místa) tlačítko
    nemá — dovětek by po zlomku vteřiny odešel sám.
    - **Android: Transformer s JEDNOU sekvencí dvou položek**, obě přes `Presentation` na 480 px.
      **Zvuk se musí vynutit do AAC** (`setAudioMimeType`): sekvenci Transformer zvukem vždycky
      překódovává a kodér volí podle vstupu — emulátor nahrává AMR-NB (8 kHz) a s kodérem AMR
      se export zasekl, hlídač ho po 25 s ukončil („Abort: no output sample written in the last
      25000 milliseconds", nahoře „Muxer error"). Zmenšení jednoho úseku (`shrinkIfNeeded`)
      zvuk jen přenáší, proto tam to vidět nebylo.
    - **iOS: `AVMutableComposition`** se stopou obrazu a zvuku a export do MP4. Obě kolečka už
      prošla čtvercovým exportem, takže `AVMutableVideoComposition` netřeba. Jen přeložené —
      simulátor kameru nemá.
    - **Ověřeno na emulátoru** v chatu s botem: 4 s + 4 s, náhled ukázal 0:08, výsledek AAC
      mono 7,9 s, bot dostal `video_note` (8 s, 480 px); koš během navazování smazal původní
      kolečko i rozpracovaný úsek. Po prvním (zaseknutém) pokusu zůstalo původní kolečko
      k odeslání — záchrana funguje.
- **Ověřeno na emulátoru** (Plume_Galerie_Fold, emulovaná přední kamera) v chatu s botem:
  přepnutí ťuknutím s nápovědou, dotazy na mikrofon a kameru, podržení s náhledem v kruhu,
  průběhem a zámkem, puštění (bot dostal `video_note`, 480 px), zrušení tahem doleva, zamčení
  a odeslání ťuknutím, minutový strop, hlasovka podržením, příliš krátký stisk, otočení během
  kolečka i hlasovky, pauza v nabídce odeslání (kolečko mělo 6 s, pět vteřin s otevřenou
  nabídkou se nezapočítalo). **Release build (R8)** nahrál hlasovku i kolečko se zmenšením bez
  pádu — CameraX i Media3 si keep pravidla nesou samy, do `proguard-rules.pro` nic nepřibylo.
  **Neověřeno**: přepnutí na zadní kameru během nahrávání, akce pro
  čtečku obrazovky, skutečný telefon a iOS.

### Přepis řeči

Hlasovka i kolečko mají vedle sebe malé tlačítko „Přepsat do textu"; přepis se ukáže pod
záznamem (u kolečka v kartě v barvě bubliny) a ťuknutím se schová. Doména `SpeechTranscript`
a `TranscribeOffer` (`core/telegram/SpeechRecognition.kt`, testy `TranscribeOfferTest`), UI
`items/Transcript.kt`.

- **Přepisuje server (`recognizeSpeech`) a výsledek NEVRACÍ** — přijde úpravou obsahu zprávy
  (`speechRecognitionResult` u `VoiceNote` i `VideoNote`): napřed `Pending` s částečným textem,
  pak text, nebo chyba. Jde tedy existující cestou `editedMessages` a ViewModel nic nedrží.
  Přepis zůstane ve zprávě i po zavření chatu.
- **I chyba přijde ve zprávě, ne z volání.** Účet bez Premium dostane od `recognizeSpeech` `Ok`
  a pak `SpeechRecognitionResultError` s `403 PREMIUM_ACCOUNT_REQUIRED` — ověřeno na emulátoru.
  Text chyby se uživateli neukazuje; `MSG_VOICE_TOO_LONG` a `PREMIUM_ACCOUNT_REQUIRED` mají
  vlastní větu a znovu se nezkoušejí, cokoli jiného jde zkusit znovu ťuknutím.
- **Kdy nabídnout, rozhoduje Premium a zkušební přepisy** (`UpdateSpeechRecognitionTrial`,
  chodí po startu jednou, proto ho drží eager `ChatRepository` jako emoji kostek). Bez
  zkušebních přepisů (`weeklyCount` 0) se tlačítko nenabízí; došlé přepisy a záznam delší než
  `maxMediaDuration` tlačítko nechají a ťuknutí místo přepisu vysvětlí proč, přímo pod
  záznamem. **Testovací účet má 0 z 0** — bez Premium tedy tlačítko správně nevidí; Telegram
  bezplatné přepisy tomuhle účtu nedává.
- **Ve skupině s dost boosty smí přepisovat každý** (`group_transcribe_level_min`), to zatím
  nepoznáme — tlačítko se tam bez Premium nenabídne.
- **Viditelnost přepisu je stav obrazovky** (`rememberSaveable` podle zprávy), výsledek stav
  zprávy. Přepis, který už ve zprávě je, je vidět hned.
- **Přepis je pod záznamem zalomený na jeho šířku** (`BelowWithSameWidth`, vlastní `Layout`):
  dlouhý text nemá bublinu rozšířit.
- **Hotový přepis jde kopírovat a vybrat** (`copyableText`), **přeložit ne** — server
  překládá text zprávy (`translateMessageText`) a přepis jím není.
- **Ověřeno na emulátoru sondou**: vynucené nabídnutí (server vrátil Premium-chybu a její věta
  se ukázala u hlasovky i kolečka) a podstrčený hotový a rozpracovaný přepis (vzhled, schování).
  **Skutečný přepis ověřený NENÍ** — chce účet s Premium. Hlasovku i kolečko posílá testovací
  bot (`sendVoice` s m4a z `say -v Zuzana`, `sendVideoNote` se záznamem obrazovky emulátoru).

### GIFy a samolepky z klávesnice

GIF vybraný v Gboardu **odchází hned** jako animace (`InputMessageAnimation`), jako samolepka
z výběru; statický obrázek a samolepka z klávesnice čekají nad polem jako fotka z výběru
(`KeyboardContentReceiver` v `core/ui`, `ConversationViewModel.sendAnimation`).

- **Klávesnice nabízí GIFy jen poli, které řekne, že je přijme** (`EditorInfo.contentMimeTypes`).
  Bez toho Gboard hlásí „animované GIFy nejsou podporované" — a nic z toho není chyba, jen
  chybějící deklarace.
- **`Modifier.contentReceiver` z Compose tu NEJDE.** Funguje jen s `BasicTextField` nad
  `TextFieldState` a naše pole stojí na `TextFieldValue` (viz „Koncept"). Android proto obaluje
  vstupní spojení přes `InterceptPlatformTextInput`: typy se do `EditorInfo` doplní až PO
  původním `createInputConnection` (ten ho vyplňuje a přepsal by je), `commitContent` převezme
  `InputConnectionCompat.createWrapper`.
- **Obal stojí kolem pole vždycky**, i když chat nic nepřijme (pak jen propouští). Větev
  „bez obalu" by pole při přepnutí do úpravy složila znovu a vzala mu fokus.
- **Nabízí se jen to, co chat přijme**: GIF podle práva na samolepky (`canSendOtherMessages`),
  obrázek podle práva na fotky; při úpravě zprávy nic, jako u sponky. Pojistka je i ve ViewModelu.
- **Obsah se kopíruje HNED a oprávnění se pak vrací** (`requestPermission` /
  `releasePermission`). Oprávnění k URI od klávesnice je dočasné, stejně jako u sdílení.
- **Přípona souboru se doplňuje podle typu** (`prepareAnimation`) — jméno od klávesnice bývá
  bez ní a TDLib podle ní pozná, co nahrává. GIF se nepřevádí, na MP4 ho převede server.
- **Průhledný obrázek se před JPEG skládá na bílou** (`opaque()` v `preparePhoto`). JPEG alfu
  nemá a průhledné pixely z něj vyšly černé — samolepka z Gboardu odcházela na černém čtverci.
  Týkalo se to i PNG z výběru příloh.
- **iOS klávesnice GIFy do pole nevkládají** (`commitContent` tam nemá obdobu) — GIF klávesnice
  i samolepky jdou přes SCHRÁNKU. Na iOS proto `KeyboardContentReceiver` dělá jen vložení ze
  schránky (viz níž).
- **Obrázek a GIF ze schránky vkládá vlastní položka kontextové nabídky pole** („Vložit GIF" /
  „Vložit obrázek", `appendTextContextMenuComponents`). Systémové „Vložit" v poli umí jen text
  a s obrázkem ve schránce se ani neukáže. Položka je jen tehdy, když schránka nese typ, který chat
  přijme; rozhoduje `primaryClipDescription`, který se čte při otevření nabídky — popis systém
  jako čtení schránky nehlásí, obsah (`primaryClip`) se bere až po ťuknutí. Dál stejná cesta
  jako obsah z klávesnice (`prepare`): GIF odchází hned, obrázek čeká nad polem.
- **Funguje to jen s NOVOU kontextovou nabídkou** (`ComposeFoundationFlags.isNewContextMenuEnabled`).
  Staré `TextToolbar` vlastní položky nebere. **Na Androidu je v 1.12 výchozí, na iOS NE**
  (`isNewContextMenuInitiallyEnabled = false` ve skiko) — zapíná se v `MainViewController`,
  dřív než se cokoli složí. Nabídku na iOS kreslí dál systém (`UIEditMenuInteraction`), vlastní
  položky jdou do ní jako nativní akce (`CMPEditMenuCustomAction`); systémové „Vložit" textu
  funguje dál (ověřeno na poli s číslem při přihlášení).
- **iOS: typ ve schránce se zjišťuje `containsPasteboardTypes`**, které systém nehlásí jako
  čtení. Data (`dataForPasteboardType`) až po ťuknutí — a protože to není systémové „Vložit",
  iOS se zeptá „Povolit vložení?". Obejít to jde jen `UIPasteControl` (tlačítko kreslí systém).
  Typy jsou UTI: `com.compuserve.gif`, `public.mpeg-4`, `public.png`/`jpeg`/`heic`,
  `org.webmproject.webp`. GIF má přednost — z téže položky by obrázek byl jen první snímek.
- **Na simulátoru se to ověří bez podstrkávání**: GIF do schránky Macu (`osascript -e 'set the
  clipboard to (read (POSIX file "…/x.gif") as «class GIFf»)'`), pak `xcrun simctl pbsync host
  <zařízení>`. Dotaz „Povolit vložení?" se ukáže s původem „CoreSimulatorBridge". Ověřeno:
  „Paste GIF" v nabídce, po povolení GIF odešel a hraje.
- **Bez dotazu „Povolit vložení?" vkládá jen `UIPasteControl`** — systémové tlačítko
  (`ClipboardPasteSuggestion`). Když má pole fokus a schránka nese GIF / obrázek, nad polem se
  ukáže pruh „GIF ve schránce" s tímto tlačítkem. Položka v kontextové nabídce zůstává, ale ptá se.
  - **Interop view musí být `placedAsOverlay = true`.** Výchozí interop leží POD plátnem Metalu
    a prosvítá průhledným otvorem; iOS pak tlačítko považuje za zakryté, ťuknutí neprojde
    ověřením a dotaz na povolení přijde stejně. Nic se nehlásí — pozná se to jen podle dotazu.
    Průhledné pozadí (`clearColor`) k tomu, jinak má kapsle bílé rohy.
  - **`intrinsicContentSize` u `UIPasteControl` rozměr NEHLÁSÍ** (−1), Compose mu dal nulovou
    plochu a tlačítko nebylo vidět. Měří se `systemLayoutSizeFittingSize`, s minimem.
  - **Cíl je `UIResponder` s `pasteConfiguration`** (přijímané UTI) a `pasteItemProviders`;
    data z `NSItemProvider.loadDataRepresentation` chodí na vlákně na pozadí.
  - **Vložený obsah se podruhé nenabízí**, pamatuje se `changeCount` schránky pro celý proces.
    Při vložení se čte ŽIVÝ `changeCount` — mění se i bez notifikace (jiná aplikace, na
    simulátoru synchronizace se schránkou Macu), a s číslem z doby zobrazení pruh zůstal viset.
  - Android návrh nekreslí — tam se na vložení nikdo neptá.
- **`preparePhotoData` na iOS skládá na bílou** (`opaque = true` + `UIRectFill`), ze stejného
  důvodu jako `opaque()` na Androidu.
- **Obrázek do schránky emulátoru z `adb` nedostaneš** — `cmd clipboard` neexistuje a Chrome
  i Fotky chtějí nejdřív projít úvodním souhlasem. Ověřuje se podstrčením: dočasně v debug buildu
  `setPrimaryClip(ClipData.newUri(…))` s GIFem v `cache/shared` (vydává ho provider `…documents`;
  soubor se tam dostane `adb push` do `/data/local/tmp` a `run-as … cp`). Pozor: po zápisu ukáže
  Android vlevo dole náhled schránky a dlouhý stisk v tom místě otevře systémové sdílení, ne pole.

### Video

Video je vlastní typ přílohy (`MessageMedia.Video`: soubor, náhled, rozměry, délka). V bublině
a v albu je náhled s tlačítkem přehrávání a délkou; přehrává se v prohlížeči, listuje se fotkami
i videi spolu (`SearchMessagesFilterPhotoAndVideo`).

- **Přehrává se až v prohlížeči na celé obrazovce, ne v bublině ani v galerii.** Video v seznamu
  by se pralo se scrollem a swipem na odpověď a každá viditelná bublina by držela dekodér.
  V galerii je náhled s tlačítkem přehrání; ťuknutí přepne na celou obrazovku a teprve tam se
  video stahuje a hraje. Návrat do galerie ho pozastaví.
- **Ťuknutí na celé obrazovce u videa napřed ukáže schované ovládání**, teprve ťuknutí
  s ovládáním na očích vede do galerie. Jinak by se u hrajícího videa k posuvníku nedalo dostat.
- **Video se streamuje z rozestavěného souboru** (Android; `StreamingSource`,
  `PartialFileDataSource`). ExoPlayer dostane vlastní `DataSource`, který čte soubor TDLib
  a místo konce vstupu POČKÁ, až data dorazí — `FileDataSource` by na konci rozestavěného
  souboru ohlásil konec a přehrávání by po pár vteřinách skončilo. Stahovat začne až vybraná
  stránka; soused složený dopředu ne.
- **Bez přesouvání stahování by to nefungovalo.** Přehrávač si hned na začátku řekne o `moov`
  atom, a ten u většiny videí leží na KONCI souboru; TDLib přitom stahuje od začátku, takže by
  se na něj čekalo marně. `StreamingSource.requestFrom` proto volá `downloadFile` s offsetem.
- **`downloadedPrefixSize` se počítá od `downloadOffset`, ne od začátku souboru.** Po skoku na
  konec je dostupný úsek `downloadOffset ..< downloadOffset + prefix` — prvních N bajtů
  dostupných NENÍ, i když se předtím stáhly (testy `FileStateTest`). Kdo to plete, čte díry.
- **`supportsStreaming` od serveru říká, co tvrdí odesílatel, ne kde index doopravdy je.**
  Ověřeno: video poslané botem s `supports_streaming=true` mělo `moov` na konci. Nedá se z něj
  tedy usuzovat, že skok nebude potřeba — jen se podle něj streamování vůbec zkusí.
- **Čekání na data blokuje vlákno ExoPlayeru, a tak to má být** — je to jeho vlastní vlákno pro
  načítání. Po 30 s bez nových dat se čtení vzdá: stahování mohlo spadnout a věčné čekání by
  vypadalo jako zamrzlý přehrávač.
- **Dostažený soubor TDLib PŘESUNE** z `files/tdlib-files/temp/<n>` na konečnou cestu
  (`videos/…`) a `FileState.Downloading` vystřídá `Ready` — bez `downloaded`. Zdroj streamu proto
  bere cestu jako FUNKCI (`StreamingSource.path`) a `VideoPage` mu po dokončení podá konečnou
  cestu a celý soubor jako stažený; přehrávač se nepřenačítá. Dokud si zdroj držel dočasnou
  cestu, šlo video přehrát jen jednou: přehrání od začátku (i skok) soubor OTEVÍRÁ ZNOVU
  a skončilo `ExoPlaybackException: Source error` / `FileNotFoundException … temp/70: ENOENT`.
  Hrající video přesun nepozná — už otevřený soubor přejmenování přežije. Ověřeno dočasným
  logem v `PartialFileDataSource.open`: první přehrání čte `temp/175`, opakované
  `videos/out2.mp4`.
- **Chyba přehrávače se čte jako stav** (`VideoPlayer.isFailed`: `playerError`, na iOS
  `AVPlayerItemStatusFailed`). Bez ní zůstalo optimistické `playing = true` z tlačítka a UI
  tvrdilo, že hraje. Po chybě je vidět tlačítko přehrát s hláškou a `play()` přehrávač
  připraví znovu (`prepare()` pokračuje z místa chyby). Ověřeno sondou s neexistující cestou.
- **Streamovací cestu ověří jen video, které TDLib ještě NEMÁ**: stažené jde přes `load(path)`
  a chyba se neukáže. Každý pokus proto chce nový soubor — k MP4 stačí připojit box `free`
  s náhodnými bajty (jinak TDLib soubory se stejným obsahem sloučí).
- **iOS zatím streamovat neumí** (`videoStreamingSupported = false`). `AVPlayer` by potřeboval
  `AVAssetResourceLoaderDelegate` — vlastní zdroj odpovídající na požadavky na rozsahy bajtů;
  do té doby se tam čeká na celý soubor.
- **Velký průběh uprostřed jen do ROZBĚHNUTÍ videa** (`hasPlayed` = přehrávač poprvé hlásí
  `isPlaying`), ne dokud se stahuje. U streamovaného videa je soubor „načtený" hned, ale
  přehrávač ještě čeká na data — procento staženého souboru by tam lhalo (hraje se dávno před
  100 %), proto se po načtení točí neurčitě. Tlačítko play/pauza se do té doby neukazuje.
- **Stahování pod hrajícím videem je kroužek v pravém horním rohu** (`VideoDownloadIndicator`);
  ťuknutí ho roztáhne do čáry přes horní okraj karty, další ho svine. Nahoře, protože kroužek tam
  už je a jen se roztáhne do šířky; dole by se čára přetahovala o místo s posuvníkem.
- **Jsou to běžné `CircularWavyProgressIndicator` / `LinearWavyProgressIndicator`** a přechod je
  obyčejné `AnimatedContent` se `SizeTransform`, rostoucí z rohu (`contentAlignment = TopEnd`).
  První verze kreslila na `Canvas` oblouk, který se rozvine do čáry — vymyšlený prvek, který
  nikde jinde není; uživatel chtěl standardní vzhled. Ukazuje se proto celkový `progress`, ne
  souvislý stažený úsek: obě komponenty kreslí průběh od nuly.
- **Podklad pilulky nese kontejner `AnimatedContent`, ne obsah.** Velikost se animuje na
  kontejneru; podklad na obsahu `SizeTransform` ořízne a pilulka roste s hranatým okrajem.
- **Na emulátoru se stahování zpomalit NEDÁ**: `adb emu network speed edge`/`gprs` odpoví OK,
  a 2,5MB video se přesto stáhne za vteřinu. Vzhled indikátoru se proto ověřuje podstrčeným
  stavem (`downloadVisible` bez podmínky na `Downloading`, pevný `progress`), jako karta aktualizace.
  **Přechod jde prohlédnout jen zpomaleně**: `adb shell settings put global
  animator_duration_scale 10` (Compose ho respektuje) a série `screencap` — v normální
  rychlosti ho snímky minou.
- **Obraz v obraze** (`rememberPictureInPicture` v `core/video`): tlačítko v liště videa
  a samočinný přechod při odchodu z aplikace, **jen dokud video hraje** — pozastavené okénko
  nepotřebuje. Jen v prohlížeči médií a jen pro stránku, která hraje na celé obrazovce;
  mizející video ne (okénko nad jinými aplikacemi by obešlo `FLAG_SECURE` i odpočet).
  - **Android posílá do okénka CELOU aktivitu** (`supportsPictureInPicture` na `MainActivity`),
    takže `VideoPage` během PiP schová ovládání a nechá jen obraz. Parametry
    (`setPictureInPictureParams`) platí pro aktivitu, ne pro obrazovku — **při odchodu
    z kompozice se automatika vypíná**, jinak by aplikace šla do okénka i ze seznamu chatů
    (ověřeno: po zavření prohlížeče Home okénko neotevře).
  - **`smallestScreenSize` musí být v `configChanges`.** Přechod do okénka mění velikost okna;
    bez něj by se aktivita vytvořila znovu a přehrávač s ní zanikl.
  - **Automatika:** od Androidu 12 `setAutoEnterEnabled(playing)`, starší přes
    `addOnUserLeaveHintListener`. Akce v okénku je `RemoteAction` (přehrát / pozastavit)
    s broadcastem jen pro vlastní balíček.
  - **Zavřené okénko i odchod na pozadí video pozastaví** (`LifecycleEventEffect(ON_STOP)`).
    Dřív video bez okénka po Home hrálo dál na pozadí, dokud proces žil.
  - **iOS** jde přes `AVPictureInPictureController` nad vrstvou přehrávače (`VideoPlayer.layer`,
    stav — povrch se skládá až po přehrávači) a `canStartPictureInPictureAutomaticallyFromInline`;
    na pozadí hraje díky `UIBackgroundModes: audio`. Jen přeložené.
  - **Při ověřování okénko odkryje aplikaci pod emulátorem** (v Androidu cokoli, co bylo v popředí
    předtím) — snímky obrazovky s PiP zachytí i cizí obsah. Stav se čte z `dumpsys activity
    activities` (`mode=pinned`), a na okénko se ťuká jen tam, kde okénko opravdu leží
    (`mBounds` úlohy), jinak ťuknutí dopadne do aplikace pod ním.
  - **Ověřeno na emulátoru:** tlačítko, automatický přechod při Home s hrajícím videem (bez hraní
    se nic neděje — správně), video v okénku dohraje, návrat z okénka bez nového vytvoření aktivity,
    zavření okénka tahem video pozastaví. **Akce přehrát / pozastavit v okénku ověřená NENÍ**
    (nabídka okénka se na emulátoru nedala spolehlivě trefit).
- **Přehrávač je v kompozici** (`rememberVideoPlayer`) a s ní se uvolní. Hraje jen vybraná
  stránka; stav (pozice, délka, dohráno) se čte po 200 ms, přehrávače ho samy nehlásí.
  Spuštění videa zastaví hrající hlasovku.
- **Android: Media3 ExoPlayer do `TextureView`**, ne `PlayerView`. Ten kreslí do `SurfaceView`,
  samostatného okna, které se s posunem vrstvy nehýbe — video by při tahu na zavření stálo.
  Jen `media3-exoplayer` + `media3-ui` (kvůli `AspectRatioFrameLayout`): `media3-ui-compose`
  táhne androidx Compose novější, než drží Compose Multiplatform. Audio focus řeší ExoPlayer.
- **iOS: `AVPlayer` + `AVPlayerLayer` v UIKit view** (`UIKitView`, `interactionMode = null`, aby
  dotyky šly do Compose). Vrstva velikost s view nemění sama — nastavuje se v `layoutSubviews`
  bez implicitní animace. „Dohráno" AVPlayer stavem nehlásí, pozná se podle pozice u konce.
- **Tah na zavření posouvá video `offset`em, ne `graphicsLayer`** — nativní view (UIKit) dostane
  jen pozici z layoutu, transformaci vrstvy ne.
- **Náhled MPEG-4 se zahazuje** (`ThumbnailFormatMpeg4`) — není to obrázek a Coil ho neotevře.
- **Dráha posuvníku se vlní, dokud video hraje** (`LinearWavyProgressIndicator` ve slotu
  `track` u `Slider`), po pozastavení, dohrání i během tahu je rovná. Tentýž význam jako vlnka
  hrající hlasovky („teď zní") a jako systémový přehrávač médií. Přechod amplitudy animuje
  indikátor sám, stačí mu podat 0 nebo 1. Vlnky se posouvají jen s viditelným ovládáním —
  schované ovládání odchází z kompozice i s animací. Ověřeno na emulátoru (hraje / pozastaveno),
  iOS jen přeložené.
- **Rychlost a ztlumení jsou v liště pod videem**, za časem: čip rychlosti (tentýž `SpeedChip`
  a stupně 1× / 1,5× / 2× jako u hlasovky) a reproduktor. Na přehrávači `setSpeed` a `setMuted`.
  - **Rychlost si aplikace pamatuje zvlášť od hlasovek** (`AppSettings.videoSpeed`): kdo poslouchá
    hlasovky na 2×, nemusí se tak dívat na video. Platí i v náhledu při sdílení a nad polem.
  - **Ztlumení drží prohlížeč, ne stránka, a neukládá se.** Na stránce by se po odlistování další
    video zase rozeznělo; uložené by příště pouštělo video potichu bez vysvětlení.
  - **Obojí se zapisuje až PO načtení** (efekty klíčované `loaded`): `load` hlasitost přenastaví.
  - **Ztlumení za běhu audio focus NEPUSTÍ** — video pořád běží a hudba se pod ním rozeznít nemá.
    Jinak než `muted` v `load` (GIF), který o focus vůbec nežádá.
  - **iOS `play()` vrací rychlost na 1×**, takže se drží v přehrávači a zapisuje po každém
    spuštění; do pozastaveného `rate` nepsat, rozjelo by ho.
  - **Na emulátoru ověřené jen ovládání**: čip přepíná a rychlost vydrží zavření prohlížeče,
    ztlumení ne. Přehrávání samo emulátor nepustil (`queueInputBuffer error: 14` u všech videí,
    viz „GIFy") — **že video opravdu hraje rychleji a potichu, ověřené NENÍ**.

## Spoilery a mizející média

Dva různé mechanismy TDLib. **Spoiler** (`hasSpoiler`) je jen rozmazání, fotka v historii zůstává
a ťuknutí ji odkryje. **Mizející médium** (`selfDestructType`: jednorázové zhlédnutí nebo časovač)
se otevírá rovnou na celou obrazovku bez listování a po zhlédnutí nebo odpočtu ho TDLib vymění
za `MessageExpiredPhoto` / `MessageExpiredVideo`.

- **Mizení se nabízí jen jedné příloze v soukromém chatu** (`selfDestructAllowed`
  v `StagedAttachments.kt`, testy `StagedAttachmentsTest`). Jinde TDLib odpoví „Messages can
  self-destruct only in private chats" (ověřeno `libtdjsonjava.so`) — a to až po odeslání.
  Dokud to hlídané nebylo, výběr režimu nabízel časovač i ve skupině a v kanálu.
- **Z alba mizení VIDITELNĚ zmizí** (`dropForbiddenSelfDestruct`, snackbar). Kdo k mizející
  fotce přidá druhou, dostane album; tiché přepnutí až při odeslání by poslalo trvale fotku,
  o které si odesílatel myslel, že zmizí.
- **Zbývající čas je od TDLib, ne délka časovače** (`ChatMessage.selfDestructIn`, čte se po
  `openMessageContent`). Odpočet běží od prvního otevření — i na jiném zařízení nebo před
  restartem — a `SelfDestructTimers` drží jen paměť procesu. Začít znovu od plné délky by obsah
  ukázalo déle, než odesílatel dovolil; proto `startTimer` odpočet umí zkrátit, prodloužit ne.
- **Vlastní odeslané mizející médium se u odesílatele nespotřebovává.** Odpočet patří příjemci;
  dokud se spouštěl i odesílateli, viděl u své zprávy „vypršelo", i když ji příjemce ještě
  neotevřel — a po restartu zase fotku, protože se to pamatovalo jen v paměti.
- **Prohlížeč s mizejícím médiem má `FLAG_SECURE`** (`SecureScreen`): snímek obrazovky vyjde
  černý a obsah se neukáže ani v přehledu úloh. iOS snímky zakázat neumí, tam je `SecureScreen`
  prázdné.
- **Mizející média nejsou v listování prohlížeče** (`visualMedia` je vyřazuje), nemají v něm
  reakce, sdílení ani uložení, a notifikace k nim nepřikládá obrázek — stejně jako ke spoileru.
- **Časovač u videa NENÍ to, co nastavil odesílatel.** TDLib ho při přijetí prodlouží aspoň na
  délku videa + 1 s (`ttl.ensure_at_least(get_message_content_duration(…) + 1)`
  v `MessagesManager.cpp`), a `MessageSelfDestructTypeTimer` už nese prodlouženou hodnotu.
  Video délky 6 s se 3s časovačem má v odznaku 7 s — to je správně, chyba to není. Délku
  videa zapisuje do zprávy odesílatel.
- **A délku TDLib zaokrouhluje NAHORU** (`std::ceil(video->duration_)` v
  `DocumentsManager.cpp`; server ji posílá s desetinami). Oficiální klient ji ukazuje
  zaokrouhlenou dolů, takže u videa, které má u odesílatele „▶ 3s" (ve skutečnosti 3,x s),
  vyjde u příjemce časovač 5 s: 4 + 1. Obě „sekundy navíc" jsou tedy od TDLib, ne od nás.
- **Odpočet začíná `openMessageContent`, a proto se volá až nad STAŽENÝM médiem**
  (`awaitSelfDestructMedia`). Dřív se volal hned při otevření prohlížeče: stahování ukrojilo
  z časovače (video 11 s, v prohlížeči se objevilo 7 s) a prohlížeč se zavřel dřív, než
  video dohrálo — časovač má na délku videa rezervu jedné vteřiny. Mizející video se ze
  stejného důvodu nestreamuje (`VideoPage(streaming = false)`): přehrávání a odpočet začnou
  naráz a nic se neukáže dřív, než odpočet běží.
- **Jednorázové zhlédnutí se hlásí až při ODCHODU z prohlížeče** (`onCleared`, `appScope`).
  U `MessageSelfDestructTypeImmediately` TDLib po `openMessageContent` obsah vyřídí okamžitě
  (`ttl_on_open` → `on_message_ttl_expired`): smaže soubory a pošle vypršelý obsah, na který
  se prohlížeč zavírá. Nahlášené při otevření by médium zmizelo dřív, než se stáhlo. Co se
  nestáhlo, uživatel neviděl — nespotřebuje se.

### Citlivý obsah

Médium, které server označil jako citlivé (`Message.restrictionInfo.hasSensitiveContent`), je
rozostřené se štítkem „Citlivý obsah" a odkryje se ťuknutím (`SensitiveContent`, testy
`SensitiveContentTest`).

- **Je to spoiler s jiným štítkem.** Převod nastaví `hasSpoiler` a navíc `isSensitive`, takže
  rozostření, odkrývání (`RevealedSpoilers`), prohlížeč i notifikace jdou toutéž cestou;
  `isSensitive` mění jen text štítku. Dvě samostatná skrývání by se rozešla při prvním doladění.
- **Volbu uživatele hlídá aplikace, TDLib ne.** Kdo si citlivý obsah v Telegramu povolil, má
  `ignore_sensitive_content_restrictions = true`, ale `hasSensitiveContent` přichází dál
  (`get_restriction_info_object` v `RestrictionReason.cpp` na volbu nesahá — přečteno ve zdroji
  TDLib). Na volbu se ptá jen zpráva, která příznak má (`TdlClient.hidesSensitive`), ne každá
  zpráva historie.
- **Do notifikace citlivá fotka nejde nikdy**, ani s povolenou volbou: zamykací obrazovku
  vidí i ostatní.
- **Přepínač „Zobrazovat citlivý obsah" je v Obecných → Soukromí** (`PrivacySettings`,
  `SensitiveContentSettings`, testy `SensitiveContentViewModelTest`). Je to nastavení ÚČTU —
  TDLib ho zapíše na server a platí na všech zařízeních, proto to podtitul říká. Nabízí se jen
  s `can_ignore_sensitive_content_restrictions` (jinak TDLib zápis odmítne), přepínač se pohne
  hned a po selhání se vrátí s větou pod sebou. Už převedené zprávy se nepřekreslí: příznak se
  čte při převodu zprávy, projeví se po návratu do chatu.
- **Přepínač se na testovacím účtu NEPŘEPÍNÁ bez souhlasu** — mění skutečný účet všude. Ověřeno
  jen čtení: na emulátoru se ukázal zapnutý, protože ho účet má povolený z jiného klienta.
- **GIF spoiler dřív NEUMĚL VŮBEC** (`MessageAnimation.hasSpoiler` se zahazoval) a hrál sám
  i schovaný. Skrytý GIF se teď nestahuje ani nepřehrává, dokud se neodkryje — pod rozostřením
  by se hýbal a prozradil, co skrývá.
- **Mřížka médií v profilu spoiler taky neznala** a ukazovala ho ostře. Skrytá dlaždice je
  rozostřená s přeškrtnutým okem a **první ťuknutí jen odkryje**, jako v konverzaci. Otevřít
  rovnou nejde: prohlížeč médium, na které se ťuklo, odkrývá sám (z konverzace je to druhé
  ťuknutí), takže by ho z mřížky ukázal bez varování.
- **Skrývá se VŠECHNO, co citlivá zpráva ukazuje jako obrázek**, ne jen fotka, video a GIF:
  samolepka, náhled souboru a obrázek náhledu odkazu. Jde to jednou cestou (`SensitiveBox`
  v `SpoilerOverlay.kt`): rozostření, štítek (u malých náhledů bez něj) a odkrytí prvním
  ťuknutím přes `RevealedSpoilers`, tedy pro celou zprávu naráz. Obrázek náhledu odkazu je
  `MessageMedia.Photo` se `hasSpoiler`/`isSensitive` (`linkPreview(hideSensitive)`),
  samolepka a soubor mají vlastní `isSensitive`.
- **Ťuknutí bere VRSTVA NAD obsahem, ne obal kolem něj.** Samolepka otevírá sadu, obrázek
  náhledu odkaz a potomek dostává dotyk dřív než rodič — skrytá samolepka by se místo odkrytí
  otevřela. Poslední potomek v `Box` je na řadě první, proto průhledná vrstva nad obsahem, jen
  dokud je skrytý. Dlouhý stisk se opakuje kvůli nabídce nad zprávou.
- **Rozostřená samolepka se ořezává do zaoblení** (`coveredShape`): je průhledná a závoj
  rozostření pokryl celý její obdélník — hranatý šedý čtverec. Odkrytá se neořezává.
- **Náhled v citaci i v pruhu připnutých respektuje skrytí taky** (`quoteThumbnail`,
  `PinnedMessagesBar.thumbnailFileId`). Pruh připnutých dřív ukazoval ostrý náhled i u fotky se
  spoilerem — visí nad konverzací pořád, takže to byl nejviditelnější únik.
- **Sonda „všechny zprávy od čísla N" v soukromém chatu s botem NEFUNGUJE podle čísel z Bot
  API.** Bot má v soukromém chatu vlastní číslování zpráv, jiné než náš účet, takže podmínka
  `id > (N shl 20)` chytila i staré zprávy. Spolehlivé je `date` (čas odeslání).
- **Citlivý může být i CELÝ chat** (`User` / `Supergroup.restrictionInfo.hasSensitiveContent`,
  `ChatInfo.sensitive`): konverzace napřed ukáže varování („Citlivý obsah" s tlačítkem „Přesto
  zobrazit") a obsah až po potvrzení, jako oficiální klient. Rozhoduje tatáž volba účtu jako
  u zpráv (`hidesSensitive(restriction)`) — kdo si citlivý obsah povolil, varování nedostane.
- **Potvrzení platí pro jedno otevření chatu** (`rememberSaveable` podle chatu, přežije otočení),
  ne napořád: „napořád" je volba účtu v Obecných → Soukromí, a druhá, místní verze téže volby by
  se s ní rozešla.
- **Varování a omezený chat se řídí JEDNOU podmínkou** (`chatHidden` v `ConversationScreen`):
  obsah, pole, lupa a shrnutí v hlavičce i pruh připnutých. Dokud to byly podmínky zvlášť,
  pruh připnutých u omezeného chatu zapomněl a obsah prozradil. Seznam se pod varováním
  neskládá, takže se nic nehlásí jako přečtené.
- **Testovací účet má citlivý obsah povolený**, takže skutečný citlivý kanál by varování
  neukázal — ověřuje se sondou, která `ChatInfo.sensitive` nastaví chatu s testovacím botem.
- **Ověřeno na emulátoru podstrčeným příznakem** (sonda: popisek `#citlive` od testovacího bota,
  necommituje se): fotka, video i GIF v konverzaci, odkrytí GIFu s přehráním, mřížka v profilu.
  Skutečná citlivá zpráva ověřená není. **Ruční GIF z Pythonu Telegram nepoznal** a poslal ho
  jako soubor; animaci vyrobí `sendAnimation` s němým MP4 (`screenrecord` z emulátoru). Ten ale
  nahrává obrazovku i se jmény kontaktů — testovací zprávy se proto po ověření mažou
  (`deleteMessage` botem).

### Omezený obsah

Zpráva, kterou server v téhle zemi nebo na téhle platformě nedovolí ukázat
(`Message.restrictionInfo.restrictionReason`), má v bublině místo obsahu důvod od serveru
s ikonou zákazu (`restrictionText`, `ChatMessage.restricted`, `RestrictedNote`, testy
`RestrictedContentTest`).

- **Je to jiná věc než citlivý obsah.** TDLib (`get_restriction_info_object`) plní
  `restrictionReason` JEN důvodem, který citlivý není; citlivost hlásí zvlášť
  `hasSensitiveContent` a ten se odkrývá ťuknutím. Omezený obsah odkrýt nejde — ani v prohlížeči,
  ani v náhledu, ani v citaci.
- **Důvod je věta od serveru a vypisuje se.** Pravidlo „text z TDLib se uživateli nevypisuje"
  platí pro chybové hlášky; tohle je obsah, který dokumentace popisuje jako „human-readable
  description", a oficiální klienti ho ukazují taky.
- **Zahazuje se všechno, co by obsah prozradilo nebo nabízelo akci nad ním**: příloha, entity,
  náhled odkazu, anketa, tlačítka bota, kontrola faktů. Odesílatel, čas a reakce zůstávají.
- **Náhled v seznamu chatů a notifikace nesou důvod taky** (`previewTextOrNull`,
  `Message.previewEntities`), jinak by obsah prosákl tam; fotka se do notifikace nepřikládá.
- **Ikona a ztlumená barva jsou nutné**: holý text v bublině by se četl jako zpráva odesílatele.
- **Omezený může být i CELÝ chat** (`ChatInfo.restriction`, `restrictionOf`): kanál nebo účet
  zablokovaný v zemi. `Chat` omezení nenese — je na `User` a `Supergroup`, stejně jako ověření;
  základní skupina ho nemá. Konverzace pak místo zpráv ukáže prázdný stav s ikonou zákazu
  a důvodem, bez vstupního pole, bez hledání a shrnutí v hlavičce.
- **Pruh připnutých zpráv se u omezeného chatu schovává taky** — první verze ho nechala a nad
  prázdným stavem visela připnutá zpráva i s obsahem, tedy přesně to, co omezení skrývá.
  Pruh proti spamu zůstává: zablokovat a nahlásit dává smysl i u omezeného účtu.
- **Prázdný stav stojí PŘED načítáním.** Historie se načte tak jako tak, jen se neukáže;
  točící se indikátor by sliboval, že zprávy přijdou.
- **Ověřeno na emulátoru sondou** (text `#omezeno` od testovacího bota převod dočasně vydával
  za omezený; necommituje se): fotka s popiskem i text s odkazem ukázaly jen důvod. Omezený
  chat sondou v `chatInfo` (chat s testovacím botem): prázdný stav s důvodem, bez pole
  a připnutých. Skutečně
  omezenou zprávu testovací účet nezná, náhled v seznamu a notifikace ověřené nejsou.

### Jak to ověřit

**Přes testovacího bota jde ověřit jen ODESÍLACÍ strana.** Bot API mizející média posílat neumí
— `sendPhoto` nemá `ttl_seconds` a `ephemeral_message_parameters` (Bot API 10.3) je něco jiného:
viditelnost zprávy ve skupině, ne mizení. Kdo čeká, že si mizející fotku pošle botem, hledá
parametr, který neexistuje.

- **Chat s botem je platný cíl a server ho přijme.** `selfDestructAllowed` se ptá na soukromý
  chat, a chat s botem jím je — ověřeno na emulátoru odesláním obou režimů. Testuje se tedy
  v něm, ne ve skupině.
- **Příjmová strana potřebuje druhý uživatelský účet.** Tikající odpočet ze `selfDestructIn`,
  spotřebování po zhlédnutí, výměna za `MessageExpiredPhoto` a vyřazení z listování prohlížeče
  se u vlastní odeslané zprávy z principu neprojeví — u odesílatele se nespotřebovává.
- **Že se vlastní zpráva nespotřebovává, se pozná podle STOJÍCÍHO odpočtu**: v prohlížeči zůstane
  „10s" i po patnácti vteřinách. U jednorázového zhlédnutí to samé přežije otevření i zavření.
- **`FLAG_SECURE` se ověřuje dvakrát, ne snímkem.** Černý `screencap` sám o sobě nic nedokazuje —
  stejně vyjde i obrazovka, která se nestihla vykreslit. Druhá půlka důkazu je
  `adb shell dumpsys window windows | grep -A3 plume`, kde musí být ve `fl=` příznak `SECURE`.
- **Se zapnutým `FLAG_SECURE` se prohlížeč nedá ověřovat snímky** a musí se číst strom
  zpřístupnění (`adb shell uiautomator dump`). Ten čte hierarchii, ne pixely, takže ho ochrana
  proti snímkům neomezuje.

## Typy zpráv

TDLib má přes sto podtypů `MessageContent`. Vykreslit umíme hrstku; zbytek musí mít aspoň
pravdivý popis, protože jinak se uživateli tváří jako chyba načítání.

- **Nikdy nepoužívej jedno generické „Zpráva".** Tak to bylo a splývaly pod tím anketa, poloha,
  systémové oznámení i samotné emoji — a hlavně to vypadalo, že se zpráva nenačetla. Poslední
  záchrana je „Tenhle typ zprávy zatím neumíme zobrazit", což říká pravdu.
- **Servisní zprávy poznává `serviceText()`, která zároveň dává jejich text.** Jedno místo
  rozhoduje o textu i o způsobu vykreslení, takže se ty dva nemůžou rozejít. `null` znamená
  „tohle je běžný obsah". Vrací `UiText` a není `suspend`, takže se testuje bez prostředků
  (`ServiceTextTest`).
- **Servisní zpráva se kreslí na střed, ne do bubliny.** Bublina patří odesílateli a tyhle
  odesílatele nemají — jsou to oznámení o dění v chatu. Čas se u nich nevypisuje, reakce ani stav
  doručení k nim nepatří.
- **V servisních zprávách se nevypisují jména.** TDLib dává jen id uživatelů a jméno by znamenalo
  dotaz navíc na každou takovou zprávu, často na někoho, kdo v chatu už dávno není. Bezejmenná
  formulace („Do skupiny přibyli členové") je vždycky pravdivá a nic nestojí.
- **`MessageSupergroupChatCreate` chodí pro skupinu I kanál** („a newly created supergroup or
  channel") a obsah druh nenese — dokud tam natvrdo stál kanál, hlásila každá nová skupina
  „Kanál „X“ vytvořen". Rozlišuje `Message.isChannelPost` (`supergroupCreatedText`, testy
  `SupergroupCreatedTest`), a proto ho `serviceText`, `describe` i `previewTextOrNull` berou
  parametrem BEZ výchozí hodnoty. Citace z jiného chatu zprávu nemá, bere ho z `origin`
  (`MessageOriginChannel`). Ověřeno sondou nad první zprávou (id `1 shl 20`) třinácti supergroup
  na emulátoru: `isChannelPost` sedělo s `ChatTypeSupergroup.isChannel` pokaždé.
- **`MessageUnsupported` znamená, že klient je starší než server.** Doslovný název typu by
  uživateli neřekl nic, takže dostane stejnou větu jako ostatní neznámé typy.
- **Notifikace „neumíme zobrazit" neříká nikdy** (`notificationText`): typ, který neumíme
  popsat, je na liště „Nová zpráva". V aplikaci ta věta stojí na místě zprávy, na liště vypadala
  jako chybová hláška místo zprávy.
- **Nové typy TDLib se objevují tiše**: TDLib 15 přidal dárky, soutěže, boosty, skupinové
  hovory i články (`MessageRichMessage`, text se bere z bloků) a všechno padalo do „neumíme
  zobrazit". Při bumpu TDLib projít seznam podtypů `MessageContent` a `PushMessageContent`
  proti `describe()` a `serviceText()`.
- **Podtypy se vyjmenují podle PŘEDKA, ne podle názvu.** `grep dto/Message` v seznamu tříd jich
  najde 180 a většina `MessageContent` vůbec není (`MessageSender`, `MessageReplyTo`,
  `MessageOrigin`…), takže se v tom skutečné mezery ztratí. Třídy se rozbalí z
  `tdl-coroutines-api.jar` a proženou `javap`; `class … extends …MessageContent` jich nechá
  105 a rozdíl proti tomu, co je vyjmenované v kódu, je pak přesný seznam nepokrytých typů:

  ```bash
  javap -cp . $(ls dev/g000sha256/tdl/dto/Message*.class | sed 's#.*/##;s#\.class##;s#^#dev.g000sha256.tdl.dto.#') \
    | grep -E "class .* extends dev\.g000sha256\.tdl\.dto\.MessageContent"
  ```
- **Neznámé SERVISNÍ oznámení vypadá hůř než neznámý obsah.** `serviceText()` o něm neví, takže
  se nekreslí jako poznámka na střed, ale jako bublina se jménem odesílatele a větou „neumíme
  zobrazit" — v kanálu to mezi dvěma videi vypadá jako rozbitý příspěvek. Přesně takhle se
  projevilo `MessageDirectMessagePriceChanged` (cena přímých zpráv kanálu, TDLib 15).
- **Co je „for bots only", se schválně nepopisuje** (`MessagePassportDataReceived`,
  `MessageWebAppDataReceived`, `MessagePaymentSuccessfulBot`). K uživatelskému účtu se to
  nedostane, takže by to byla věta psaná naslepo; kdyby přece jen dorazilo, zapíše se to do logu
  a podle logu tabulka roste.
- **Neznámý typ se zapíše do logu** (`reportUnknownContent`, `w`, jednou za typ a proces), a tím
  do kontextu hlášení v Crashlytics. Bez toho se typ ze snímku „neumíme zobrazit" nedal zjistit.
  Název se bere z `toString()` (TDLib ho má jako řetězcový literál, R8 ho nepřejmenuje), obsah
  zprávy do logu nejde. `MessageUnsupported` jde toutéž cestou — v notifikaci „Nová zpráva".
- **Když typ dostane přílohu s popiskem, `captionText()` pro něj musí vracet prázdno** — jinak
  se popisek v bublině objeví dvakrát. Platí pro video kolečko, hru a platbu.

### Článek

`MessageRichMessage` (TDLib 15) je článek ze stromu bloků — nadpisy, odstavce, seznamy,
citace, kód, tabulky, média. V bublině je to **jeden text s entitami**, které bublina už umí
(`toRichBody` v `core/telegram/RichMessageBody.kt`, testy `RichMessageBodyTest`): nadpis tučně
s volným řádkem nad sebou, odrážky, čísla a zaškrtávátka (☑/☐), citace jako pás, kód, odkazy,
zmínky, hashtagy, příkazy, spoiler, vlastní emoji, datum.

- **Text, ne vlastní sazba bloků.** Druhá sazba vedle bubliny by znamenala zdvojit, co text
  umí: hledání se zvýrazněním, kopírování, výběr textu a citace z nabídky, spoilery. Rozložený
  do textu to všechno funguje beze změny.
- **Offsety se počítají z délky `StringBuilder`u** (UTF-16, jako všude) a entita se zapisuje
  až po napsání obsahu. Proto se nic nesmí z textu MAZAT zpětně: blok za odrážkou seznamu první
  verze přitahovala smazáním `\n` a posunula tím všechny entity za ním. Teď `continueLine`
  zakáže nový řádek předem.
- **Média stojí v textu jako slovo, a bublina místo něj nakreslí médium.** Text zůstává
  „[Fotka] popisek" (kopírování, náhled, hledání, přepis pro AI), a TENTÝŽ průchod stromem zapíše
  kotvu (`ArticleMedia`: úsek slova, pořadí, `MessageMedia`). Bublina článek s médii kreslí po
  úsecích (`articleSegments`, testy `ArticleSegmentsTest`): text, fotka / video / GIF, text…
  Entity se do úseků ořežou a posunou, zvýraznění hledání se počítá nad úsekem. Popisek média je
  kurzívou — pod fotkou by jinak splynul s textem.
- **Koláž a prezentace jsou mřížka alba** (`ArticleMedia.group`, `AlbumGrid` s `AlbumCell`).
  Mřížka alba dřív brala jen zprávy (`ChatMessage` na dlaždici); teď bere dlaždice s id zprávy,
  médiem a pořadím v článku, které jde do klíče letu (`LocalArticleMediaFlight` u každé
  dlaždice). Média koláže jdou do jedné mřížky, jen když mezi nimi v textu není nic než konec
  řádku — médium koláže s vlastním popiskem mřížku přeruší, popisek by v ní neměl kde být.
  Prezentace (listování) se kreslí stejně, jako mřížka.
- **Fotky a videa článku jsou i v záložce Média v profilu** (`sharedMediaTiles`, testy
  `SharedMediaTilesTest`): článek dá dlaždici za každé médium. TDLib článek v hledání
  s `SearchMessagesFilterPhotoAndVideo` vrací (ověřeno sondou), Plume ho dřív zahodil, protože
  článek nemá `media`. Klíč dlaždice nese id i pořadí média, dlaždice otevře prohlížeč
  s `MediaRoute(fromSharedMedia = true, articleMedia = …)`.
- **Zvuk, hlasovka a soubor v článku jsou řádek jako ve zprávě** (přehrát, posunout,
  otevřít). Přehrávač i stahování souborů dřív počítaly s jedním médiem na zprávu: klíč
  přehrávače proto nese pořadí zvuku ve zprávě (`VoiceKey.part`), akce článku nesou pořadí média
  (`ToggleArticleAudio`, `SeekArticleAudio`, `OpenArticleDocument`) a stahování souboru jede
  přes `withDocument(document)` — průběh je podle `fileId`, takže na zprávě nezáleží.
  - **Skladba z článku hraje jako hlasovka, ne jako hudba na pozadí**: fronta hudby
    (`MusicQueue`) hledá sousední skladby mezi zprávami a médium článku zprávou není. Po dohrání
    zvuku z článku se nic neřetězí (`part != NO_PART`).
  - **Název souboru není popisek.** Řádek souboru ho ukazuje sám; dřív, když byl soubor jen
    slovem, se název psal jako popisek.
- **Médium článku se otevírá v prohlížeči jako příloha ankety** (`MediaRoute.articleMedia`,
  `LocalArticleMediaFlight` v klíči letu): jediná stránka bez akcí. Pod jedním id zprávy je médií
  víc, takže bez indexu v klíči by se spárovala špatná fotka.
- **Dlouhý článek posílá TDLib USEKNUTÝ** (`RichMessage.isFull = false`) a zbytek vydá až
  `getFullRichMessage`. Dokud se to neřešilo, článek o 12 700 znacích končil po 8 200 a nebylo
  poznat proč. Pod useknutým článkem je „Číst celý článek" (`MessageAction.ReadFullArticle`,
  `ConversationRepository.fullArticle`); dočtený se v repository pamatuje podle zprávy
  a `editDate`, takže další převody téže zprávy (přečtení, reakce, návrat do chatu) nevrátí
  začátek. Prohlížeč bere médium z uložené zprávy a celý článek čte, jen když v ní není.
- **Stav zaškrtávátka nese `isChecked`** — Kotlin getter booleanu, takže `javap | grep get` ho
  mine a vypadá to, že wrapper stav nemá.
- **Náhled v seznamu chatů zůstává holý text** (`plainText`, spoiler vynechá).
- **„Upravit" u vlastního článku se nabídnout může** (`composerEdit` článek od textu nerozezná)
  a server `editMessageText` odmítne; poslat článek jde jen s Premium, takže se to zatím netýká.
- **Ověřuje se botem**: `sendRichMessage` s `rich_message.markdown` (Bot API 10). Ověřeno na
  emulátoru anglicky: nadpisy, formátování, spoiler, seznamy i zaškrtávátka, citace s podpisem,
  kód, oddělovač, tabulka a klikatelné `/help`, `@BotFather`, `#vylet`. **Neověřeno**: sbalitelná
  citace, iOS (jen přeložené).
  - **Média v markdownu jsou odkaz `tg://photo?id=p1`** a soubor jde v poli `rich_message.media`
    (`{"id":"p1","media":{"type":"photo","media":"attach://f1"}}`, multipart s `f1=@…`). Obrázek
    adresou (`![](https://…)`) server odmítne (`RICH_MESSAGE_PHOTO_NO_MEDIA_FOUND`) a stažení
    z Wikimedie selhalo (`failed to get HTTP URL content`); obrázky se proto vyrábějí lokálně.
  - **Zvuk a soubory článků v záložkách Soubory a Hudba profilu nejsou — TDLib je tam nevrací**
    (ověřeno sondou: hledání s `SearchMessagesFilterDocument` / `Audio` vrátilo jen starší
    zprávy, žádný článek). U Médií je vrací, proto tam dlaždice z článků jsou.
  - **Useknutý článek vyrobí dlouhý text**: 39 odstavců po ~320 znacích (~12 700) TDLib poslal
    s `isFull = false` a 8 224 znaky.
  - **Hlasovka má v `rich_message.media` typ `voice_note`** (jako `InputMediaVoiceNote`);
    s `voice` server odpoví „type "voice" is unsupported". Hlasovka i skladba se odkazují
    `tg://audio?id=…`. M4A od `say` / `afconvert` jako hlasovka nemá délku ani waveform (0:00).
  - Ověřeno na emulátoru česky: fotka s popiskem a koláž dvou fotek na svém místě, otevření
    fotky z článku v prohlížeči (svislá fotka celá), „Číst celý článek" s kolečkem (pak 12 665
    znaků a konec článku) a dočtený článek i po odchodu z chatu, zvýraznění hledaného slova
    v úseku za fotkami. **Video a GIF v článku ověřené nejsou** (jdou toutéž cestou jako fotka).
- **`getUpdates` testovacího bota nic nevrací, když `allowed_updates` zúžilo dřívější volání**
  (`getWebhookInfo` pak ukáže `"allowed_updates":["my_chat_member"]`). Volba platí, dokud ji
  další `getUpdates` nezmění, a zprávy mezitím server zahodí — volat s
  `allowed_updates=["message","my_chat_member","callback_query"]`.

### Počty pod příspěvkem

Pod příspěvkem kanálu je řádek se zhlédnutími a přeposláními (`PostStatsRow`) a u příspěvku
s diskusí před nimi čip komentářů (`CommentsChip`, obojí v `PostFooter`; doména `PostStats`
v `ChatMessage.postStats`). Čísla jsou zkrácená („12 tis.", „1.2K";
`compactCount`, testy `CompactCountTest`), čtečka obrazovky dostane přesná.

- **Reakce a počty chodí JEDNÍM updatem** (`UpdateMessageInteractionInfo`), proto je z dřívějšího
  `reactionUpdates` `interactionUpdates` a nese obojí (`MessageInteractions`). Dva odběry téhož
  updatu by jen čekaly, až se rozejdou.
- **Pod zprávou, ne v ní, a na protější straně než čipy reakcí.** Ty visí přes dolní nároží na
  straně odesílatele, takže u příchozího příspěvku jsou počty vpravo. Barva patří ploše
  konverzace, jako u odznaku doručení. Když řádek reakcí přesahuje bublinu, zarovnají se počty
  k jeho konci — sloupec zprávy je široký podle něj.
- **Komentáře jen v kanálu** (`PostStats.shownIn`, test `komentareJenVKanalu`). `replyInfo` má
  i zpráva v diskusní skupině (odpovědi ve vláknu) a pod každou by stálo „0".
- **Čip a počty jsou JEDEN řádek.** Dva řádky pod čipy reakcí (počty, pod nimi čip) příspěvek
  natahovaly a mezi příspěvky vznikala díra. S čipem stojí řádek na straně zprávy, bez něj na
  protější.
- **Zaokrouhluje se DOLŮ.** 999 999 zhlédnutí není „1 mil."; příspěvek nemá ukazovat víc, než
  kolik jich má.
- **Mezera v „12 tis." je nezlomitelná** (`&#160;` v `strings.xml`), jinak by se jednotka mohla
  odtrhnout na další řádek.

### Komentáře (vlákno diskuse)

Ťuknutí na čip komentářů otevře vlákno: `getMessageThread` řekne, kde je (diskusní skupina
a kořenová zpráva), a otevře se `ConversationRoute(chatId = skupina, threadId = kořen)`.

- **Komentáře NEJSOU v kanálu.** Jsou to zprávy v připojené diskusní skupině, které odpovídají
  na kopii příspěvku (server ji tam při zveřejnění sám přepošle), nebo na jiný komentář.
- **Vlákno je obyčejná konverzace skupiny za obalem** (`ThreadConversationRepository`,
  delegace `by`). ViewModel konverzace o vláknu skoro neví: historie jde přes
  `getMessageThreadHistory`, nové zprávy se filtrují podle `ChatMessage.threadId`
  (`MessageTopicThread`), ostatní jde rovnou do skutečného repository. Druhá obrazovka pro
  komentáře by znamenala zdvojit celou konverzaci.
- **Odeslání bez citace odpovídá KOŘENI.** Zpráva bez odpovědi by skončila v hlavním chatu
  skupiny, ne pod příspěvkem. Obal to doplní u všech `send*`.
- **Kořen je nejstarší zpráva vlákna** a `getMessageThreadHistory` ho nevrací; obal ho přidá,
  když přijde prázdná stránka (začátek vlákna), a stránka „od kořene" je vždycky prázdná.
- **Citace kořene se nekreslí** (`state.threadRootId` v builderu, test
  `veVlaknuSeKorenNecituje`) — odpovídá mu skoro každý komentář.
- **Ve vlákně se vypíná, co patří chatu skupiny:** „píše…" (viděla by ho celá skupina),
  připnuté zprávy, reklamy, počty zmínek a reakcí (jsou za skupinu), shrnutí, profil
  z hlavičky a ankety (zakládá je obrazovka, která o vláknu neví).
- **Hledání a koncept patří VLÁKNU** (`searchInThread`, `saveThreadDraft` s `MessageTopicThread`;
  koncept se čte z `MessageThreadInfo.draftMessage`, `CommentThread.draft`). Bez tématu by
  hledání prošlo celou diskusní skupinu a koncept skončil v jejím hlavním chatu i v seznamu
  chatů. Koncept skupiny zůstává nedotčený; odpověď kořeni se z obnoveného konceptu zahazuje —
  je to výchozí cíl komentáře, ne citace.
- **Ověřeno na emulátoru** (FilipHorky – zprávy, bez členství, diskusní skupina psaní bez
  vstupu dovoluje): „binance" našlo ve vlákně 6 výsledků a všechny se otevřou ve vlákně; pole
  místo „Přidat se"; rozepsaný komentář se po odchodu a návratu vrátil do pole a po smazání
  zmizel. Koncept skupiny zůstal prázdný (ověřeno sondou). **Nic se neodeslalo.**
- **Nepřečtené komentáře jsou vlákna, ne skupiny**: otevření skočí na první nepřečtený
  s předělem nad ním, jako v chatu. Počet dá `getMessageThread` (`unreadMessageCount`),
  vlákno ho podstrčí do `chatInfo` místo počtu skupiny (`ThreadConversationRepository`).
- **Hranici přečtení vlákna server NEPOSÍLÁ, i když pole na ni má.**
  `MessageReplyInfo.lastReadInboxMessageId` vrátil po přečtení šesti ze třinácti komentářů
  NEJNOVĚJŠÍ komentář, zatímco `unreadMessageCount` správně klesl na sedm (ověřeno na
  emulátoru). Hranice se proto dopočítá z počtu: N-tý nejnovější cizí komentář je první
  nepřečtený (`threadReadBoundary`, testy `ThreadReadBoundaryTest`). Nula znamená nikdy
  neotevřené vlákno — nepřečtené je všechno pod kořenem a historie se kvůli tomu nečte.
- **Přečtení ve vlákně jde se zdrojem `MessageSourceMessageThreadHistory`**
  (`markThreadRead`). S `MessageSourceChatHistory` se čte chat skupiny a přečtení vlákna na
  serveru stojí. **Server ho vede jako nejvyšší přečtené id**: nahlášení nejnovějšího komentáře
  přečte celé vlákno.
- **Přečtení se hlásí až po skoku na nepřečtené** (`heldReads` v `ConversationViewModel`,
  platí pro každý chat). Konverzace se otevírá u nejnovějších zpráv a ty byly vidět déle než
  odpočet hlášení (400 ms), než se historie dotáhla — ve vlákně, kde se tahá ze serveru,
  pokaždé, takže se vlákno přečetlo celé dřív, než na něj uživatel pohlédl. Podržené hlášení
  po skoku propadne; když se neskáče, odejde.
- **Hlavička: „Komentáře" a pod tím název skupiny**, bez sdílených prvků — vlákno je jiná
  obrazovka než chat skupiny a klíč by se přetahoval s položkou v seznamu.
- **Komentovat jde i bez vstupu do skupiny, když to skupina dovolí**
  (`Supergroup.joinToSendMessages == false` → `writeAccess(nonMembersCanSend)`, testy
  `WriteAccessTest`). Nečlen pak má pole, ne „Přidat se" (`canJoin` je `false`), a platí pro něj
  výchozí práva skupiny; vyhozenému to nepomůže. Jinak vidí „Přidat se". Bot API pole
  `join_to_send_messages` u skupiny, která psaní bez vstupu dovoluje, vůbec neposílá — tak se
  pozná diskusní skupina k ověření (`getChat` na `linked_chat_id` kanálu).
- **Čip nese tváře posledních komentujících místo ikony** (`MessageReplyInfo.recentReplierIds` →
  `PostStats.commenters`, nejvýš `MAX_RECENT_COMMENTERS` = 3, jména z keše odesílatelů). Kreslí je
  tentýž `FaceStack` (`core/ui`) jako tváře hlasujících v anketě — dvě kopie hromádky by se
  rozešly hned při prvním doladění. Lem má barvu plochy konverzace (`surface`): čip je bez
  výplně, leží přímo na ní. Bez komentujících (nový příspěvek) zůstává ikona. Ověřeno na
  kanálu FilipHorky – zprávy jen prohlížením.
- **Ověřeno na emulátoru jen čtení** (kanál FilipHorky – zprávy, bez přidání se): čip, vlákno
  s kořenem nahoře, zpět do kanálu. **Odeslání komentáře ověřené NENÍ** — veřejně by ho viděli
  všichni čtenáři kanálu.

### Témata ve skupinách

Skupina s tématy (fórum, `Supergroup.isForum`) se z seznamu chatů, archivu i výsledků hledání
otevírá **seznamem témat** (`ForumTopicsRoute` / `ArchiveForumTopicsRoute`, `ForumTopicsScreen`),
téma pak jako konverzace (`ConversationRoute(topicId)`). Hranice: `ForumRepository`
(`TdlForumRepository`) a `TdlConversationRepository(forumTopicId)`; pravidla seznamu jsou čisté
funkce v `ForumTopics.kt` (testy `ForumTopicsTest`).

- **Zúžení na téma je PARAMETR repository, ne obal** jako u vlákna komentářů
  (`ConversationRepository.inForumTopic`). Téma se TDLib předává v každém odeslání, konceptu,
  „píše…", hledání i připnutých (`topicId = MessageTopicForum`); obal by musel přepsat přes dvacet
  metod kvůli jednomu argumentu. Historie jde přes `getForumTopicHistory`, nové zprávy se filtrují
  podle `Message.topicId`, přečtení se hlásí se zdrojem `MessageSourceForumTopicHistory` (jinak se
  přečtení tématu nepohne — tatáž past jako u vlákna), počty nepřečtených, hranice přečtení,
  koncept a „Označit vše" (`readAllForumTopic*`) jsou tématu (`getForumTopic`).
- **O tom, že je chat fórum, ví položka seznamu** (`ChatSummary.isForum`, dočítá se lokálním
  `getSupergroup` spolu s ověřením a mění ho `UpdateSupergroup`). Kdo položku seznamu nemá
  (zmínka, odkaz, globální výsledek hledání), ptá se `ChatRepository.isForum` — lokálně, TDLib
  supergroupu zná, když zná chat.
- **Seznam témat je detailový panel, ne druhý list panel.** Stojí vpravo na místě konverzace
  a téma se vrší nad něj; list panel by chtěl vlastní scénu i vlastní profil a úpravy (jako archiv).
  `openConversation` / `openForum` nahrazují konverzaci i seznam témat — výběr jiného chatu je
  výměna obojího.
- **Změna tématu nese jen id** (`topicChanges` z `UpdateForumTopic`, `UpdateForumTopicInfo`
  a nových zpráv s `MessageTopicForum`) a téma se čte znovu — stejný vzor jako připnuté zprávy.
  Téma, které ještě není načtené, se tím PŘIDÁ (nové téma, zpráva hluboko v seznamu), a dočtená
  stránka pak NEPŘEPÍŠE novější verzi z updatu (`withPage`). Téma, které se dočíst nedá, bylo
  smazané; skryté obecné téma se nekreslí (`isHidden`).
- **Čtení tématu vyrobí update, na který se téma čte — past nekonečné smyčky.** `getForumTopic`
  se ptá SERVERU a TDLib po odpovědi pošle `UpdateForumTopic`, i když se nic nezměnilo. Seznam
  na ten update téma čte znovu, takže se čtení a update honily dokola: po každém otevření
  skupiny s tématy asi 40 s, 638 dotazů na server u dvou témat (změřeno dočasným logem
  v `refresh`), pak to samo ustalo. Nebylo to vidět nijak jinak než v logu. Proto
  `changedPerTopic` (testy `ChangedPerTopicTest`): update téhož tématu jde dál, jen když se
  od posledního LIŠÍ (DTO z TDLib mají `equals`). S ním proběhnou po otevření čtyři čtení.
  **Obecně: kdo na update odpovídá dotazem, musí vědět, jestli ten dotaz sám update nevyrobí.**
- **Pořadí je `ForumTopic.order` sestupně**, připnutá témata ho mají vyšší — kromě pořadí
  připnutých mezi sebou, to `order` NENESE (viz „Správa témat").
- **Uzavřené téma = `WriteAccess.TopicClosed`** (`topicWriteAccess`, testy `TopicWriteAccessTest`):
  psát smí vlastník a správce s `canManageTopics`; důvod ze skupiny (není členem, omezený) má
  přednost. Mění se živě (`accessUpdates` odebírá i `UpdateForumTopicInfo`).
- **V tématu se neplánuje a nezakládá anketa** — naplánované zprávy patří skupině a formulář
  ankety o tématu neví (skončila by v obecném tématu). Reklamy se v tématu nekreslí.
- **Ikona tématu** (`ForumTopicIcon` v `core/ui`): vlastní emoji, jinak bublina v barvě od serveru
  s písmenem (barva písma podle jasu — na žluté by bílé zmizelo), obecné téma `#`. Tvar je
  bublina, ne kruh ani sušenka: tvary avatarů nesou druh chatu a téma není ani jeden.
- **V hlavičce tématu nic neletí** (`ConversationState.narrowed`): ikona tématu v řádku seznamu
  chatů není. Letí fotka a název skupiny ze seznamu chatů do hlavičky seznamu témat.
- **Past s vahami v řádku podruhé**: název tématu s `weight(1f, fill = false)` a `Spacer`
  s `weight(1f)` si řádek rozdělily napůl a čas visel uprostřed (viz „Ověření a štítek podvodu").
- **Ověřeno na emulátoru jen čtením** na veřejné Kotlin Community (@kotlin_lang, účet v ní není —
  místo pole „Join", na který se neťuká): seznam témat s ikonami z vlastních emoji, odesílatel
  v náhledu, stránkování, konverzace tématu (hlavička, připnuté zprávy tématu 6 proti 86 ve
  skupině), „Zobrazit jako zprávy" a zpět. **Psaní do tématu, uzavřené téma, přečtení
  a dvoupanel ověřené** v testovací skupině „Plume test pozvánek" (vlastník píše i do uzavřeného
  General). Dvoupanel na emulátoru zvětšeném jako rozložený Fold (`adb shell wm size 2208x1840`,
  pak `wm size reset`): seznam témat vpravo, téma ho nahradí, zpět vrátí seznam témat a další
  zpět placeholder.
- **Vlastní zprávy nad hranicí nepřečtených se hlásí jako zhlédnuté** (`messagesToView`, testy
  `UnreadMarksTest`). Server v tématu počítal jako nepřečtené i VLASTNÍ zprávy nad hranicí
  (servisní „přibyli členové", zpráva z jiného zařízení); cizí nad ní nebyla, nehlásilo se nic
  a odznak „2" u tématu zůstal, i když v něm uživatel četl a psal. Ověřeno dočasným logem:
  před opravou `getForumTopic` `unreadCount = 2` s hranicí pod oběma vlastními zprávami, po ní
  `unreadCount = 0` a hranice na nejnovější zprávě. Vlastní zprávu bez hranice (nic
  nepřečteného) se hlásit nemusí.
- **Domněnka, která se nepotvrdila:** že krátká konverzace po skoku na první nepřečtenou
  přečtení nenahlásí, protože se viditelné položky nezmění. Skok vloží předěl nepřečtených,
  viditelné položky se tím změní a hlášení odejde — ověřeno pokusem v krátké skupině s botem.

#### Správa témat

Seznam témat má nahoře hledání podle názvu, dole „Nové téma" a podržení řádku otevře nabídku
(připnout, ztlumit, přejmenovat, uzavřít / znovu otevřít, skrýt obecné téma, smazat). Témata ve
skupině zapíná přepínač v úpravě skupiny. Kdo co smí, rozhodují čisté funkce `topicRights`
a `topicActions` (`ForumTopics.kt`, testy `TopicManagementTest`).

- **Vlastní téma smí autor upravit, uzavřít i smazat bez práv správce** (`ForumTopicInfo.isOutgoing`
  → `ForumTopic.isOwn`); cizí jen správce s `canManageTopics`, smazat s `canDeleteMessages`.
  Připnout smí jen správce — pořadí témat patří celé skupině. Zakládá člen, když to skupina
  dovolí (`ChatPermissions.canCreateTopics`), správce s `canManageTopics` vždy.
- **Obecné téma smazat nejde, jen skrýt** — a skryté se v seznamu nekreslí (`sortedTopics`).
  Proto stránka nese obecné téma zvlášť (`ForumTopicsPage.general`, i skryté) a vrací se z ⋮
  („Zobrazit obecné téma"). **Server skryté obecné téma zároveň UZAVŘE a při vrácení ho neotevře**
  — v seznamu pak má zámek; znovu otevřít jde z nabídky.
- **Barvy nového tématu jsou jen ty, které TDLib přijme** (`TOPIC_COLORS`, šest). Přejmenování
  barvu nemění — `editForumTopic` ji měnit neumí (jen vlastní emoji, a to až s Premium).
- **Nové téma se po vytvoření rovnou otevře** (`createdTopicId`), jako u oficiálních klientů.
- **Ztlumení tématu se zapisuje celým objektem** jako ztlumení chatu (`withMuteFor`,
  `useDefaultMuteFor = false`), jinak by se přepsal zvuk a téma by jelo podle skupiny.
- **Upozornění tématu jsou tentýž sheet jako upozornění chatu** (`ChatNotificationSheet`,
  „Upozornění" v nabídce nad tématem vedle rychlého Ztlumit): doby ztlumení, náhled, připnuté,
  zmínky a „Vrátit nastavení skupiny". Zápis jde přes `NotificationSettingsRepository`
  (`setTopicMute`, `setTopicOption`, `resetTopic`), ne přes `ForumRepository` — cesta
  k `setForumTopicNotificationSettings` je jedna, i pro rychlé Ztlumit.
- **Téma má TŘI vrstvy, ne dvě**: vlastní nastavení, pak skupina, pak výchozí nastavení skupin.
  `useDefault…` u tématu znamená „podle skupiny", ne „podle rozsahu"; téma ve ztlumené skupině je
  tedy ztlumené, i když samo nic nemá. Každá volba hledá svou vrstvu ZVLÁŠŤ (`notificationPrefs`,
  testy `NotificationPrefsTest`) — sdílí ji i čtení upozornění chatu, jen bez prostřední vrstvy.
  Odběr hlídá všechny tři (`UpdateForumTopic`, `UpdateChatNotificationSettings`, rozsah).
- **Zvuk tématu v sheetu není.** Kanál konverzace je jeden na CHAT a notifikace všech témat
  skupiny jdou jím (skupina notifikací TDLib je taky jedna na chat).
- **Seznam témat ukazuje ztlumení jen VLASTNÍ** (`isTopicMuted`), ne zděděné ze skupiny — to
  seznam ukazovat nemá, ztlumená je celá skupina. Sheet naopak ukazuje ÚČINNÝ stav („Ztlumeno ·
  jako ve skupině").
- **Pořadí připnutých se mění režimem uspořádání** („Změnit pořadí připnutých" v nabídce nad
  připnutým tématem; jen s právem připínat a od dvou připnutých, `canReorderPinned`). Připnutá
  témata dostanou úchyt, tah je jen koncept a „Hotovo" ho pošle jedním voláním
  (`setPinnedForumTopics`); ✕ i zpět ho zahodí. Čtečka obrazovky má u řádku akce „Posunout
  nahoru / dolů".
  - **`order` od TDLib pořadí připnutých NENESE.** Po `setPinnedForumTopics` zůstalo u obou
    témat totéž `order` (ověřeno sondou i po čtyřech vteřinách), zatímco `getForumTopics` vracel
    témata už v novém pořadí. Seznam proto řadí připnutá podle pořadí z PRVNÍ stránky od serveru
    (`ForumTopicsPage.pinnedOrder`, `sortedTopics(pinnedOrder)`, testy `PinnedTopicsOrderTest`)
    a po uložení převezme nové pořadí sám. Nově připnuté téma (v pořadí ještě není) jde nahoru,
    kam ho dává i server. **Změnu pořadí z jiného zařízení** seznam uvidí až při dalším otevření.
  - **Tažení v `LazyColumn` je obecné** (`core/ui/DragReorder.kt`: `rememberDragReorderState`,
    `Modifier.dragHandle`, `Modifier.draggedItem`), používají ho i připnuté chaty. Tažená
    položka jede jen kreslením (`graphicsLayer`) a NEMÁ `animateItem`: animace umístění by se
    s posunem pod prstem prala. Výměnu ohlásí `tick`. Úchyt táhne hned, bez podržení — podržení
    řádku patří nabídce. Hlavička režimu a řádek s úchytem jsou taky sdílené
    (`core/ui/ReorderRow.kt`: `ReorderTopBar`, `ReorderRow`), aby témata a chaty vypadaly
    i ovládaly se stejně.
- **Hledání hledá server** (`getForumTopics` s dotazem, první stránka), takže najde i téma, které
  ještě není načtené. Rozepsaný dotaz další znak zahodí (`transformLatest`), jako hledání chatů.
- **Zapnout témata smí jen vlastník** (`EditableChat.canToggleForum`) a diskusní skupina kanálu
  je mít nemůže. Základní skupině se přepínač nabízí, zapnutí ji ale napřed převede na
  supergroupu (viz „Převod základní skupiny"). `hasForumTabs` (záložky místo seznamu) posíláme
  `false`.
- **Ověřeno na emulátoru** v testovací skupině „Plume test pozvánek" (zapnutím témat): zapnutí,
  založení tématu se zelenou ikonou a jeho otevření, připnutí, uzavření, ztlumení, přejmenování,
  hledání, skrytí a vrácení obecného tématu, smazání s potvrzením, anglicky i česky.
  **Neověřeno**: vypnutí témat, práva správce bez vlastnictví a člena (chce druhý účet), iOS.

#### Cesty do tématu

Notifikace, odkaz na zprávu, výsledek hledání i zmínka vedou do tématu, ne do celé skupiny.
Kam cíl míří, nese `ChatTarget` (`topicId`, `forum`) a kam ho zásobník uloží, rozhoduje čistá
funkce `openTarget` (`ChatsStack.kt`, testy `ChatsStackTest`).

- **Téma zprávy nese `Message.topicId`** (`MessageTopicForum`), odkaz `MessageLinkInfo.topicId`.
  **Holý odkaz `t.me/skupina/123` téma NENESE** — takový vydá „Informace" u zprávy
  (`getMessageLink` bez `inMessageThread`). Proto se bere i `topicId` zprávy, kterou odkaz vydá.
- **Odkaz a zmínka otevřou cíl NAD konverzací, bez seznamu témat pod ním**: zpět vede tam, kde
  uživatel na odkaz ťukl, ne do cizí skupiny. Skupina s tématy bez tématu (zmínka, `t.me/skupina`)
  otevře seznam témat — celá skupina jako zprávy je jen volba v něm.
- **Notifikace a výsledek hledání naopak seznam témat POD téma vloží** (`AppNavigation.openChat`,
  `openFoundMessage`): zásobník se tu skládá od seznamu chatů a zpět má vést tam, kam by vedl
  z tématu otevřeného ručně.
- **Téma téže skupiny není „otevřený chat"** — dřív stačila shoda `chatId` a odkaz na zprávu
  mimo téma z tématu téže skupiny nedělal nic. Otevřený je jen celý chat (bez tématu a vlákna);
  skok na místě jen tam, kde zpráva patří do právě otevřeného tématu.
- **Notifikace nese téma NEJNOVĚJŠÍ zprávy** (`ChatNotification.topicId` → `EXTRA_TOPIC_ID`).
  Skupina notifikací TDLib je jedna na chat, ne na téma, takže víc témat v jedné notifikaci otevře
  to poslední. **Z pushe téma nepřijde** (`NotificationTypeNewPushMessage` ho nemá) — dočasná
  notifikace otevře celou skupinu, dokud ji nenahradí skutečná. Staré téma se nedrží: zpráva
  z jiného tématu by jinak otevřela to předchozí.
- **Odpověď z notifikace jde do tématu** (`NotificationActions.send(topicId)` přes
  `inForumTopic`) — bez něj by skončila v obecném tématu.
- **`ChatOpenRequests` nese dvojici chat + téma** (`ChatOpenRequest`), stále jako stav.
- **„Píše…" v tématu jen z tématu** (`ChatTyping.topicId` z `UpdateChatAction.topicId`).
  Ověřené NENÍ — v tématu, kde by někdo psal, účet není.
- **iOS téma z notifikace nemá**: APNs push od Telegramu téma v `custom` nepopisuje a skutečný
  push ověřený není; ťuknutí otevře skupinu.
- **Ověřeno na emulátoru** (Kotlin Community jen čtením): holý odkaz na zprávu v tématu poslaný
  botem otevřel „Kotlin Releases" se skokem na zprávu a zpět vedl do chatu s botem; zmínka
  `@kotlin_lang` otevřela seznam témat; intent notifikace s `EXTRA_TOPIC_ID` otevřel téma,
  zpět seznam témat, zpět seznam chatů. **Skutečná notifikace z tématu a odpověď z ní ověřené
  NEJSOU** — chce to zprávu v tématu skupiny, kde účet je.

### Placená média

`MessagePaidMedia` (fotka nebo video za hvězdy, častá v kanálech) je `MessageMedia.PaidMedia`:
holé médium s rozmazaným náhledem, cenou s hvězdou uprostřed a u videa délkou v rohu
(`PaidMediaPreview` v `Attachments.kt`, převod `toPaidMedia`, testy `PaidMediaTest`).

- **Server před zaplacením vydá jen náhled** (`PaidMediaPreview`: rozměry, délka, minithumbnail),
  ne soubor. Rozmazání je tedy všechno, co je; nestahuje se nic a nic se neodkrývá.
- **Zaplacené médium je obyčejná fotka nebo video** (`PaidMediaPhoto` / `PaidMediaVideo` →
  `Photo` / `Video`), i s prohlížečem a reakcemi — týká se i prodejce, který vidí vlastní
  příspěvek odemčený. Ze ZAPLACENÝCH víc položek se ukazuje PRVNÍ: prohlížeč i reakce stojí
  na tom, že zpráva je jedna fotka (album je jinak víc zpráv), a ověřit by to nešlo.
- **Nezaplacené víc položek je mřížka** (`PaidPreviewGrid`) se stejnými řádky a výškami jako
  album (`albumRowSizes`, `ALBUM_GAP`, `ALBUM_TILE_MAX_HEIGHT`) a s JEDNOU cenou přes celou
  mřížku — kupuje se všechno naráz, cena u každé dlaždice by tvrdila, že jde koupit kus. Model
  proto nese náhledy všech položek (`PaidMedia.previews`), ne jen první s počtem; „N položek"
  zbyl jen v popisu pro čtečku. Položka, kterou TDLib nezná, je prázdná dlaždice.
- **Odemknout (koupit za hvězdy) Plume neumí**, ťuknutí to řekne snackbarem
  (`MessageAction.ExplainPaidMedia`) místo prohlížeče, který by neměl co ukázat.
- **Bot placené médium do soukromého chatu pošle** (`sendPaidMedia` se `star_count`), takže se
  to ověřuje bez kanálu. Ověřeno na emulátoru česky: fotka s popiskem (25 hvězd), mřížka tří
  fotek a mřížka 2 × 2 s videem (délka na dlaždici) a popiskem, ťuknutí s vysvětlením, popis
  pro čtečku („4 položky, Placený obsah za 40 hvězd"). **Neověřeno**: zaplacené médium
  (zaplatit nejde), iOS (jen přeložené).

### Faktura a hra

`MessageInvoice` a `MessageGame` jsou příloha `MessageMedia.Product` a v bublině karta
(`ProductCard`): obrázek přes celou šířku bubliny, název, popis a u faktury cena — hvězdy ikonou,
jiné měny částkou s kódem („12,50 EUR"), u zaplacené „Zaplaceno", u testovací „Testovací platba".
Dřív to byl řádek „Platba: …" s malým náhledem, ze kterého nebylo poznat, co a za kolik se prodává.
Seznam chatů, citace a notifikace dál píšou „Platba: …" / „Hra: …".

- **Karta na ťuknutí nereaguje.** Zaplatit a hrát jde jen tlačítkem bota pod zprávou („Pay",
  „Play"), které Telegram k faktuře i hře přikládá vždycky — a to už říká, že to Plume neumí
  (`bot_unsupported_buy` / `_game`). Obrázek do prohlížeče nevede: ten otevírá fotku ZPRÁVY.
- **Kolik desetinných míst měna má, TDLib neříká** — částka chodí v nejmenších jednotkách
  (`totalAmount`). Rozkládá ji `Price.parts` s tabulkou výjimek (`currencyExponent`: hvězdy
  `XTR` a JPY bez desetin, KWD tři; testy `PriceTest`), neznámá měna dostane dvě. Oddělovač
  patří jazyku (`decimal_number`), stejně jako u velikosti souboru.
- **Hra ukazuje fotku, ne animaci** (`Game.animation` se zatím zahazuje), a popis `description`;
  `text` hry (výsledková tabulka) ne.
- **Ověřuje se botem** fakturou v hvězdách: `sendInvoice` s `currency=XTR` nepotřebuje
  `provider_token` (jiné měny ano, takže je testovací bot poslat nemůže). Ověřeno na emulátoru
  česky: karta s obrázkem z `photo_url` i bez něj, cena „★ 1 500", tlačítko Pay vysvětlí, že
  platby Plume neumí. **Neověřeno**: jiná měna, „Zaplaceno", testovací faktura, hra (chce hru
  založenou v BotFatheru) a iOS (jen přeložené). Obrázek z `photo_url` vyšel měkký — jakou
  velikost server vydá, zjištěné není.

### Soutěž

Vyhlášení soutěže (`MessageGiveaway`) a její výsledky (`MessageGiveawayWinners`) jsou příloha
`MessageMedia.Giveaway` a v bublině karta (`GiveawayCard`): samolepka dárku (u výsledků ikona
oslavy), výhra („3 × Telegram Premium na 6 měsíců", „1 500 hvězd rozdělí se mezi 2 výherce"),
popis navíc od pořadatele, čipy kanálů, podmínky (jen noví odběratelé, země s vlajkou, kdy se
losuje) a u výsledků čipy výherců a nevyzvednuté výhry. „Podrobnosti" otevře sheet se stavem
účasti tohoto účtu (`GiveawayStatusSheet`), u výsledků „Zobrazit soutěž" skočí na vyhlášení.
Doména a převod v `core/telegram/Giveaway.kt`, testy `GiveawayTest`.

- **Stav účasti NENÍ ve zprávě** — je to dotaz na server (`getGiveawayInfo`), a proto se ptá až
  sheet, ne každá karta. Hotová soutěž má přednost před stavem účastníka: zrušená (`wasRefunded`)
  před výhrou, výhra = neprázdný `giftCode` nebo `wonStarCount > 0`.
- **Výhru Premium nese dárkový kód** a ten jde zkopírovat (potvrzení fajfkou v tlačítku — snackbar
  by zůstal pod modálním sheetem). Uplatnit ho Plume neumí, věta posílá do oficiální aplikace.
- **Jména kanálů a výherců dohledává `resolveSender`** při převodu zprávy (záporné id = chat).
  Kanál, který TDLib nezná, z karty vypadne — čip bez jména by nikam nevedl; výherce bez jména
  zůstane v datech, ale nekreslí se. Výsledky nesou jen pořádající kanál, ostatní počtem
  („a 2 další kanály").
- **Čip vede do chatu nad konverzací** (`MessageAction.OpenChat`, cesta zmínky). U výsledků
  `giveawayMessageId` míří do pořádajícího chatu; skok funguje, jen když výsledky v tomtéž chatu
  jsou — jinak by vedl na id z cizího chatu.
- **Tlačítka jsou pod sebou přes celou šířku karty.** Vedle sebe se „Zobrazit soutěž" do půlky
  karty nevešlo a zkrátilo se.
- **Bot soutěž nevyrobí** (pořádá ji jen kanál s boosty a Premium), takže se ověřuje sondou:
  v `toChatMessage` text `#soutez` / `#vysledky` od testovacího bota převést na podstrčené
  `MessageGiveaway` / `MessageGiveawayWinners` a v `giveawayStatus` vrátit stav podle textu.
  Necommituje se. Ověřeno na emulátoru česky i anglicky, světle i tmavě: obě karty, sheet
  „Soutěžíš" i výhra s kódem a kopírováním, skok se zvýrazněním, čip otevře chat. **Skutečná
  soutěž a skutečné `getGiveawayInfo` ověřené NEJSOU**, iOS jen přeložené.

### Dárek

Dárky (`MessageGift`, `MessageUpgradedGift`, `MessageRefundedUpgradedGift`, `MessageGiftedPremium`,
`MessagePremiumGiftCode`, `MessageGiftedStars`, `MessageGiveawayPrizeStars`, `MessageGiftedGrams`)
jsou příloha `MessageMedia.Gift` (doména `GiftKind` v `core/telegram/Gift.kt`, testy `GiftTest`)
a kreslí se jako karta uprostřed konverzace (`GiftCard`): samolepka dárku, co se darovalo, od koho
a komu, vzkaz a stav. Dřív z nich zbylo jen „Byl poslán dárek".

- **Zůstávají servisním oznámením**, jako u oficiálních klientů — karta je uvnitř
  `ServiceMessageItem`, ne bublina. Nemají stranu ani odesílatele, a proto nemají ani nabídku.
- **Věta oznámení říká, CO se darovalo** („Dárek: 1 500 hvězd", „Dárek: Plush Pepe #42",
  „Dárek: Telegram Premium na 3 měsíce"). Jde do seznamu chatů, citace a notifikace, kde karta
  není. Běžný dárek zůstává „Byl poslán dárek" — hvězdy jsou jeho cena, ne obsah.
- **„Od: / Pro:" s dvojtečkou**, ne „od Jany": jméno je pak v 1. pádu a nemusí se skloňovat.
  Jména dohledává tentýž `senderName` jako u soutěže; neznámé jméno řádek vynechá.
- **Sběratelský dárek má pod samolepkou kruhový přechod** z barev pozadí (`centerColor` →
  `edgeColor`, RGB bez alfy, takže `OPAQUE or`). Samolepka je MODEL (jak dárek vypadá), ne
  symbol — ten je jen vzor na pozadí. Pořadové číslo bez oddělovače tisíců („#1042").
- **Proměnit, vylepšit ani předat dárek Plume neumí** (hvězdy nemáme), karta to nenabízí. Jediná
  akce je zkopírovat dárkový kód Premium; potvrzuje se fajfkou v tlačítku a věta pod ním říká,
  že se uplatňuje v oficiální aplikaci.
- **TON chodí v nanoTON** (10⁹ na TON); `tonParts` dá celou část a nejvýš čtyři desetinná místa
  bez koncových nul, oddělovač dodá jazyk (`decimal_number`).
- **Karty mají stejnou šířku** (do 280 dp přes celou plochu), ne podle obsahu: karta „Dárek" vedle
  „Telegram Premium na 3 měsíce" vypadala jako jiný prvek.
- **`tdl-coroutines` 15.0.0 padá na dárku bez `background` — na Androidu to obchází
  instrumentace, na iOS NE.** Viz „Odpovědi TDLib, které wrapper nerozebere" (Hranice kolem
  TDLib). Ověřeno sondou přes `getAvailableGifts`: bez obcházky pád, s ní 151 dárků.
- **Bot dárek poslat neumí**, takže se ověřuje sondou: text `#darek1–8` od testovacího bota
  `toChatMessage` dočasně nahradí obsahem dárku (objekty skládané ručně, samolepky
  z `getAnimatedEmoji` / `getStickers`). Necommituje se. Animované samolepky drží Compose
  v pohybu a `uiautomator dump` pak selhává („null root node") — ověřuje se snímky.
- **Ověřeno na emulátoru sondou** (česky, světlý i tmavý motiv): všech osm druhů, kopírování kódu
  (fajfka a náhled schránky). **Skutečný dárek ověřený NENÍ**; iOS jen přeložené.

### Sdílený příběh

Příběh poslaný do chatu (`MessageStory`) je snímek se značkou „Příběh" v rohu a pod ním karta
„Příběh od …", případně „V příběhu tě zmínili" a popisek příběhu (`MessageMedia.Story`,
`StoryPreview` a `StoryDetails` v `items/StoryCard.kt`, převod `core/telegram/Story.kt`, testy
`StoryTest`). Ťuknutí otevře fotku nebo video v prohlížeči médií. Příběhy jako takové (kroužky,
přehrávání po sobě, odpovědi) aplikace nemá.

- **Zpráva nese jen odkaz** (autor a id), obsah se dočítá `getStory` při převodu zprávy:
  napřed lokálně, pak ze serveru (`TdlConversationRepository.sharedStory`). Pamatuje se pro
  instanci klienta jako efekt zprávy, i „není" — ale jen když to řekl server chybou 4xx:
  vypršelý příběh je 404, autor, který příběhy nikdy neměl, **400 `STORIES_NEVER_CREATED`**
  (ověřeno na kanálu @telegram). Výpadek spojení se nepamatuje.
- **Dotaz na server má strop 3 s.** Převod zprávy je součást načítání historie a TDLib bez
  spojení na síť čeká; jeden sdílený příběh by jinak zdržel celou konverzaci.
- **Obsah kreslí táž cesta jako příloha zprávy** (`MessageAttachment` nad vnitřní fotkou nebo
  videem): rámeček, stahování, let do prohlížeče pod klíčem zprávy. Šířka je stálá
  (`fillWidth = false`) — příběh je na výšku a přes celou šířku by z něj zbyl ořezaný pruh.
- **Prohlížeč příběh rozbalí** (`toViewerItem`): jediná stránka bez „N z M" (mezi fotkami chatu
  příběh není), popiskem je text příběhu, akce jsou akce zprávy.
- **Živé vysílání a příběh, který už není, jsou plocha na výšku s ikonou a větou**; ťuknutí na
  živé vysílání vysvětlí, že ho jde sledovat jen v oficiální aplikaci.
- **Bot příběh poslat neumí** (Bot API příběhy jen pro firemní účty), takže se ověřuje sondou:
  zprávy od testovacího bota s `#pribeh…` převod dočasně vydává za `MessageStory` — fotka
  z téže zprávy jako obsah, skutečný dotaz na kanál @telegram (bez příběhů, tedy „není")
  a podstrčené živé vysílání. Necommituje se. **Skutečný sdílený příběh ověřený NENÍ**, iOS
  jen přeložené.

#### Odpověď na příběh a odkaz na příběh

Zpráva, která odpovídá na příběh (`MessageReplyToStory`), má nad sebou citaci jako odpověď
z jiného chatu: náhled příběhu, jméno autora a „Příběh" (`ExternalReply` s `quote.media =
MessageMedia.Story`, `toExternalReply` v `TdlConversationRepository`). Ťuknutí na ni a odkaz
`t.me/jméno/s/N` (`InternalLink.Story`) otevřou příběh v prohlížeči médií.

- **Kreslí ji TÁŽ citace jako odpověď z jiného chatu** — `QuotedMessage` příběh uměl už kvůli
  sdílenému příběhu (náhled, „Příběh"). Nový je jen převod a cíl ťuknutí.
- **Prohlížeč otevírá příběh podle autora a id, ne podle zprávy** (`MediaRoute.storyId`,
  `MediaViewerViewModel.loadStory`, `ConversationRepository.story`): odpověď na příběh ani odkaz
  zprávu s příběhem nemají. Jediná stránka bez akcí, jako příloha ankety; popiskem je text
  příběhu, v liště autor a čas zveřejnění (`MessageMedia.Story.postedAt`).
- **Příběh, který už není, a živé vysílání prohlížeč neotevírají** — hláška
  „Příběh už není k dispozici" / vysvětlení živého vysílání. Rozhoduje se ve ViewModelu
  konverzace dřív, než se naviguje (`openStory`), prázdný prohlížeč by nic neřekl.
- **Odkaz na příběh dřív šel do prohlížeče** (TDLib ho rozpozná jako `InternalLinkTypeStory`,
  Plume ho neuměl). Autor se z odkazu najde `searchPublicChat`.
- **Past: další `when` se suspend voláním přímo v `toChatMessage` shodil aplikaci při startu.**
  Ta funkce je obří suspend funkce a ART ji po přidání větve odmítl při ověření třídy
  (`VerifyError … copy-reference … type=Conflict`) — pád hned po spuštění, ne při té zprávě.
  Výběr citace je proto samostatná funkce (`externalReply`). **Kdo do `toChatMessage` přidává
  suspend volání, ať ho vytáhne do vlastní funkce** a aplikaci po změně spustí.
- **Ověřeno sondou** (zpráva od testovacího bota s `#pribeh` převod vydával za odpověď na
  příběh kanálu nalezený `searchPublicStoriesByTag`, `#pribehpryc` za odpověď na neexistující
  příběh; necommituje se): citace s náhledem a jménem kanálu, ťuknutí otevře prohlížeč
  s fotkou příběhu, u neexistujícího hláška. **`searchPublicStoriesByTag` vrací hlavně příběhy
  LIDÍ** — sonda proto brala jen kanály (`posterChatId < 0`). Odkaz `t.me/telegram/s/1` od bota
  zůstal v aplikaci a ohlásil nedostupný příběh. **Skutečná odpověď na příběh ověřená NENÍ**
  (příběhy aplikace neumí, takže na ně z Plume odpovědět nejde), iOS jen přeložené.

### Kontakt

`MessageContact` je příloha `MessageMedia.Contact` a v bublině karta jako řádek souboru
a hovoru: tvář, jméno, číslo a u kontaktu na Telegramu „Napsat zprávu" (soukromý chat přes
`MessageAction.OpenUser`, `ContactCard`). Seznam chatů, citace i notifikace dál píšou
„Kontakt: Jana".

- **„Napsat zprávu" jen u kontaktu, který vede JINAM** (`MessageMedia.Contact.isThisChat`).
  Vlastní kontakt v Uložených zprávách i kontakt protějšku v chatu s ním vedou do chatu, ve
  kterém karta leží, a konverzace sama sebe znovu neotevře — tlačítko svítilo a nedělalo nic.
  Pozná se to při převodu zprávy: soukromý chat má id uživatele (Uložené zprávy id vlastního účtu).
- **Tvář se dočítá lokálním `getUser`** při převodu zprávy (`contactPhotoFileId`) — TDLib
  uživatele zná, když zná zprávu, která ho sdílí. Kontakt bez Telegramu (`userId` 0) má
  iniciály ze jména.
- **Číslo dostane „+", jen když je ze samých číslic** (`contactPhone`, testy
  `ContactPhoneTest`). Místní číslo s mezerami zůstane, jak přišlo — plus by z něj udělal cizí.
- **„Kopírovat" v nabídce kopíruje jméno a číslo** (`copyableText`); text zprávy je prázdný.
- **„Přidat do kontaktů" je na kartě i v ⋮ profilu** (`MessageMedia.Contact.canAddToContacts`,
  `addSharedContact`; profil `ChatProfile.isContact`, `ChatProfileViewModel.setContact`).
  Karta ho nabízí jen u člověka na Telegramu, který v adresáři ještě není — `getUser` při převodu
  zprávy (`sharedContactUser`, vedle tváře): `isContact`, `UserTypeRegular`, ne vlastní účet.
  - **Z karty se ukládá JMÉNO A ČÍSLO Z KARTY**, ne z účtu: odesílatel ho tak pojmenoval a číslo
    může být jediné, co uživatel o tom člověku ví. Profil (stejně jako pruh proti spamu) bere
    jméno z účtu (`addToContacts`). Vlastní číslo se nesdílí (`sharePhoneNumber = false`).
  - **Přidání z karty má snackbar s „Vrátit"**, ne potvrzení — je vratné (`removeContacts`).
    Po přidání zhasnou VŠECHNY karty téhož člověka v načtené historii (`withContactAddable`,
    testy `AddContactTest`), po „Vrátit" se rozsvítí.
  - **Odebrání z kontaktů je jen v profilu a s potvrzením**: smaže jméno uložené v adresáři na
    všech zařízeních a vrátit ho jde jen ručně. Ikona `person_remove` je odvozená z
    `person_add` (plus nahrazený minusem).
  - **Server do kontaktů vezme i BOTA** (`addContact` na testovacího bota prošlo). Karta
    a profil ho přesto nenabízejí — oficiální klienti taky ne. Tudy se ale dá ověřovat bez
    zásahu do adresáře se skutečnými lidmi: sonda vydá kartu od testovacího bota za kontakt
    s id bota a profil bota za člověka; necommituje se, bota pak „Vrátit" / „Odebrat" smaže.
  - **Ověřeno na emulátoru sondou** (česky): tlačítko na kartě, chyba u kontaktu bez Telegramu,
    přidání se snackbarem a „Vrátit" (tlačítko se vrátí), v profilu přidání a odebrání
    s potvrzovacím dialogem. **S člověkem ověřené NENÍ** — mění skutečný adresář. iOS jen
    přeložené. **Snackbar je krátký** a snímek po 1,5 s ho minul; „Vrátit" se hledá dumpem.
- **Poslat kontakt jde ze sponky** („Kontakt" → `ContactPickerSheet` → `ContactConfirmDialog`
  → `sendContact`). Seznam je `ChatRepository.contacts()`, filtruje se lokálně podle jména
  (`ContactPicker.shown`, testy `ContactPickerTest`). **Odeslání se potvrzuje dialogem se jménem
  a ČÍSLEM** (`contactCard`, lokální `getUser`): odchází cizí telefonní číslo a ťuknutí vedle
  v seznamu by poslalo jiné. Kontakt, který číslo skrývá (prázdné `phoneNumber`), poslat nejde
  a řekne to hláška. Posílá se i `userId`, takže u příjemce funguje „Napsat zprávu".
  Nabízí se tam, kde poloha — TDLib kontakt řadí k „základním zprávám" jako text.
- **Nad kontakty je karta „Můj kontakt"** (`ContactPicker.me`, `primaryContainer` jako Uložené
  zprávy ve výběru chatu): vlastní číslo je to, co se posílá nejčastěji. Vlastní účet se bere
  z `savedMessagesChatId` (id Uložených zpráv je id uživatele) a jde toutéž cestou, i s dialogem
  s číslem. Při hledání se karta schová (`showsMe`) — hledá se v kontaktech, ne v sobě.
- **Sheet je jen rozbalený** (`enabledValues = Hidden, Expanded`); napůl rozbalený nechal seznam
  kontaktů pod spodním okrajem. Ověřeno odesláním do Uložených zpráv — ne botovi, cizí číslo
  nemá odcházet nikam mimo vlastní účet.
- **Ověřuje se botem** (`sendContact`): fiktivní číslo dá kartu bez tlačítka; s `user_id`
  vlastního účtu karta s fotkou a „Napsat zprávu" otevře Uložené zprávy. Zprávu s vlastním
  číslem po ověření smazat (`deleteMessage`).

### Zpráva o hovoru

`MessageCall` je příloha `MessageMedia.Call` a v bublině řádek jako u souboru: kolečko se
šipkou směru, název („Zmeškaný hovor", „Odchozí videohovor") a délka (`CallRow`). Tentýž název
nese náhled v seznamu chatů, citace i notifikace. Pravidlo je čistá funkce `callOutcome`
(`CallRecord.kt`, testy `CallOutcomeTest`).

- **Odchozí nevzatý i odmítnutý hovor je „zrušený"**, jako u oficiálních klientů; u příchozího
  se zmeškaný a odmítnutý rozlišují. Spadlé spojení je normální hovor — mluvilo se.
- **Směr zná jen zpráva, ne obsah** (`Message.isOutgoing`), proto je název v
  `previewTextOrNull`, ne v `describe()` nad `MessageContent`.
- **Chybová barva jen u zmeškaného a odmítnutého** — to jediné po uživateli něco chce. Obojí je
  příchozí, tedy na světlé bublině; kontrast se přesto hlídá `accentOn`.
- **Ťuknutí nikam nevede.** Hovory Plume neumí (viz `docs/roadmap.md`, „Hovory") a „zavolat
  zpátky" by slibovalo něco, co se nestane; ťuknutí i dlouhý stisk dostane bublina.
- **Ověřuje se sondou**: skutečné hovory jsou jen v chatech se skutečnými lidmi (otevření by
  jim označilo zprávy jako přečtené) a bot volat neumí. Dočasně v `messageMedia` převést text
  `#call:<CallOutcome>:video:<sekundy>` na `MessageMedia.Call` (a v `captionBody` na prázdný
  text), zprávy poslat botem do Notifikátoru. Necommituje se.

## Emoji a samolepky

- **Zpráva složená jen z emoji se kreslí velká a bez bubliny**, stejně jako samolepka. Bublina
  kolem nich nemá co ohraničovat: samolepka má vlastní tvar s průhledností a velká emoji jsou
  obrázek samy o sobě. Barva popředí pak musí patřit PLOŠE, ne bublině, která tam není —
  `onPrimaryContainer` na průhledném pozadí je nečitelný.
- **Samotné emoji NENÍ textová zpráva.** Telegram ho posílá jako `MessageAnimatedEmoji`
  s animovanou podobou navíc, takže padalo do větve `else` a v bublině z něj bylo slovo „Zpráva".
- **Animované emoji s `.tgs` je příloha** (`MessageMedia.AnimatedEmoji`), kreslí se stejnou
  komponentou jako samolepka (`AnimatedSticker`) a bez bubliny. Text je u něj prázdný, jinak by pod
  animací stál ještě jednou jako znak. O textu i příloze rozhoduje jedna funkce
  (`MessageAnimatedEmoji.animationFileId`) — dvě nezávislá rozhodnutí by mohla nechat zprávu bez
  obojího, nebo s obojím.
- **Bez animace, nebo v jiném formátu, zůstává emoji textem** (`emoji`, obyčejný unicode znak):
  vykreslí se sám a chytí se na něj vykreslení velkých emoji. Prémiová emoji můžou mít animaci
  v libovolném formátu.
- **Než se animace načte, drží místo tentýž znak velkým písmem** v pevném čtverci 112 dp. Přechod
  je výměna obrázku za obrázek a bublina se při ní nepřeskládá.
- **Kopírování animovaného emoji kopíruje jeho znak** (`copyableText`) — text je prázdný, ale
  dřív, jako textová zpráva, to šlo.
- **Kostka (`MessageDice`) padala do téhož `else`.** Dnes je to animace (viz „Hod kostkou");
  text „🎲 6" zůstává v náhledu, citaci, schránce a tam, kde animace není `.tgs`.

### Hod kostkou

Kostka, šipky, míč, koš, kuželky i automat (`MessageDice`) jsou příloha `MessageMedia.Dice`
a kreslí se bez bubliny jako samolepka (`DiceImage` v `Attachments.kt`, převod `toDiceMedia`,
testy `DiceMediaTest`).

- **Animaci posílá Telegram, výsledek je v ní NAKRESLENÝ.** `finalState` končí snímkem s tím,
  co padlo; `value` je jen číslo pro text. Proto se nekreslí žádné vlastní číslo navrch.
- **Hraje jednou, ne ve smyčce** (`StickerPlayback.Once`). Ve smyčce by se házelo pořád dokola
  a výsledek by byl vidět jen na okamžik. Hod z historie se ukáže rovnou posledním snímkem
  (`LastFrame`); přehraje se jen ten, který přišel po otevření konverzace
  (`claimDiceThrow`, stejná tolerance jako pružný příchod zprávy), a jen jednou — po
  odscrollování a návratu je vidět výsledek. Kopie v nabídce nad zprávou nehází.
- **Konec hodu se hlídá mimo kompozici** (`snapshotFlow { state.isAtEnd }`): průběh se mění
  každý snímek a čtený v kompozici by ji skládal znovu.
- **Automat je pět vrstev přes sebe** (pozadí, páka, tři válce) a hod skončil, až doběhnou
  VŠECHNY — válce nekončí naráz. Když kterákoli vrstva není `.tgs`, zůstane text: chybějící
  válec by ukázal jiný výsledek, než jaký padl.
- **Bez výsledku (`value == 0`) se točí úvodní animace** (`initialState`) ve smyčce; na druhý
  stav se nepadá. Nastane u vlastního hodu, dokud server výsledek nepošle (ověřeno: odeslaná
  kostka se chvíli točí, pak dopadne).
- **Ověřuje se botem**: `sendDice` s `emoji=🎲` a `🎰` do otevřeného chatu (hod proběhne), pak
  odejít a vrátit se (výsledek hned). Ověřeno na emulátoru.

- **Výhru oslaví konfety** (`DiceConfetti`, `MessageMedia.Dice.successFrame` ze
  `successAnimationFrameNumber`). Vyletí na snímku, který poslal server — když míč propadne
  sítí, šipka zasáhne střed — ne až po dohrání: `AnimatedSticker` umí ohlásit snímek
  (`markerFrame`, snímek = `lerp(startFrame, endFrame, progress)`), u automatu poslední
  vrstva (pravý válec dotočí poslední). **Jen u VLASTNÍHO hodu, který proběhl před očima** —
  server snímek výhry posílá kvůli odeslanému hodu (TDLib: „on updateMessageSendSucceeded"),
  oficiální klienti cizí výhru neslaví. Konfety se kreslí v Compose (barvy z tématu, náhoda
  se semínkem id zprávy), bez velikosti v layoutu; položka se po dobu oslavy zvedne
  (`LocalRaiseItem`, `zIndex` jako u efektu zprávy).
- **Ověřuje se hodem ⚽ v chatu s botem** (gól je hodnota 3–5, tedy asi tři hody z pěti; text
  do pole přes sdílení, `SEND` s `EXTRA_SHORTCUT_ID chat_<id bota>`) a sérií surových snímků
  (`screencap` do `/data/local/tmp` ve smyčce v jednom `adb shell`, aspoň 70 — hod trvá přes
  tři vteřiny). Hodnotu hodu řekne `getUpdates`.

#### Poslat hod

- **Samotné emoji ze seznamu serveru odejde jako hod**, ne jako text (`diceEmojiFor`, testy
  `DiceEmojiTest`; `InputMessageDice`). Tak to dělají oficiální klienti — žádné tlačítko navíc,
  kdo pošle 🎲, hází. Seznam (🎲 🎯 🏀 ⚽ 🎳 🎰) posílá server v `UpdateDiceEmojis`.
- **`UpdateDiceEmojis` chodí JEDNOU po startu klienta**, takže ho drží eager `ChatRepository`
  (`diceEmojis`), ne konverzace — ta vzniká až s obrazovkou a update by minula. Prázdný seznam
  (nic nedorazilo) = emoji odejde jako text, nic se nerozbije.
- **Porovnává se bez U+FE0F a posílá se podoba od serveru.** Klávesnice dává „🎰️" i „🎰"
  podle toho, odkud emoji vzala; ověřeno, že „🎰️" z pole dorazilo botovi jako hod.
- **Hod je v právech „ostatní zprávy"** (`ContentAccess.stickers`). Kde se smí jen text, emoji
  odejde jako text — hláška by tu byla horší, uživatel chtěl poslat zprávu.
- **Neletí z pole** (`sendMedia`, jako samolepka): bublina by přiletěla jako text a na konci
  se proměnila v kostku. Pole i koncept se vyprázdní jako u textu (`clearDraft = true`),
  citace se spotřebuje.
- **Ověřeno na emulátoru** v chatu s botem: 🎲 z pole (točí se, pak výsledek; bot dostal
  `dice.value`), 🎰️ s výběrem varianty jako odpověď na hod (bot dostal `reply_to_message`).
  Text do pole jde přes sdílení (`SEND` s `EXTRA_SHORTCUT_ID chat_<id bota>`) — emoji
  `adb input text` nenapíše.
- **Strop jsou 3 emoji** (`MAX_LARGE_EMOJI`). Nad ním už nejde o gesto, ale o odstavec, a ve
  velké sazbě by zabral půl obrazovky. Míň emoji = větší: `displayLarge` / `Medium` / `Small`.
- **Odpověď si bublinu drží**, i když je jen z emoji — citace bez pozadí se ztratí.
- **Detekce emoji je pragmatický výběr rozsahů, ne unicode tabulka.** Kotlin v commonMain nemá
  ani `codePointAt`, ani `\p{...}` v regulárních výrazech, takže úplná data by znamenala vlastní
  tabulku a její údržbu při každé revizi Unicode. Chyba je na obě strany levná: nepoznané emoji
  se vykreslí normálně velké, symbol omylem považovaný za emoji se ukáže velký.
- **Počítají se shluky, ne kódové body.** Rodina (👨‍👩‍👧) je pět kódových bodů spojených ZWJ
  a vlajka dva regionální indikátory — pro uživatele je to jedno emoji. ASCII se za emoji nepovažuje,
  takže „:-P" zůstává normální zpráva (a klávesnicová emoji `1️⃣` se tím minou).
- **Samolepky: Coil umí jen `.webp`.** `.tgs` je gzipovaná Lottie animace, `.webm` video — ani
  jedno není obrázek. U nich se bere statický náhled, který TDLib k samolepce dává, a teprve když
  není ani ten, emoji. Náhled jen v rastrovém formátu: `ThumbnailFormatTgs` a `Mpeg4` mají tentýž
  problém, jen o úroveň níž.
- **`.tgs` přehrává Compottie** (`AnimatedSticker`). Gzip Compottie neumí, takže se soubor
  rozbalí přes Okio (`gzip()` funguje v commonMain, protože Okio má zlib pro JVM i native) a do
  Compottie jde až JSON. Do té doby i při chybě se kreslí statický náhled — odpovídá prvnímu
  snímku, takže přechod na animaci není skok z prázdna.
- **Compottie je `lite`.** Lite nemá JavaScriptový engine pro výrazy; samolepky Telegramu výrazy
  nepoužívají (oficiální rlottie je ani neumí). Compottie táhne Compose `foundation` tranzitivně,
  takže novější verze než náš Compose by ho tiše povýšila — před bumpem zkontrolovat její metadata.
- **Rozparsované animace mají vlastní cache (50), ne `LocalLottieCache` (10 na celou aplikaci).**
  V konverzaci se jedna samolepka opakuje a při scrollu by se jinak rozbalovala a parsovala
  znovu. Klíčem je cesta, ne MD5 obsahu jako u `JsonString` — hash rozbaleného JSONu by stál víc,
  než cache ušetří. Kvůli cestě jako klíči se cache po odhlášení zahazuje (`forgetStickerAnimations`).
- **Mimo obrazovku se animace zastavovat nemusí.** Položka `LazyColumn` mimo viewport vypadne
  z kompozice i s hodinami animace.
- **`.webm` samolepky zůstávají statické.** Je to VP9 s průhledností: hardwarový dekodér na
  Androidu alfu zahodí a iOS WebM neumí vůbec, takže by to znamenalo přibalit libvpx.
- **`RemoteImage` má parametr `background`.** Výchozí podložka `surfaceContainerHighest` je správně
  u fotky, ale u samolepky by z průhledného tvaru udělala obrázek v rámečku.
- **`captionText()` vrací u samolepky prázdno.** Její emoji se dřív vypisovalo jako text zprávy;
  vedle vykreslené samolepky je to zdvojení. V náhledu seznamu chatů a v citaci ale zůstává —
  tam není kam kreslit obrázek.
- **Emoji v čipu reakce je záměrně mimo typografickou škálu** (18 sp natvrdo). Role jako
  `labelLarge` míří na výšku písmen, ale u emoji je nosná celá kresba, takže ve stejné velikosti
  působí menší než číslo vedle ní.

### Efekty zprávy

Zpráva poslaná s efektem (`Message.effectId`: oheň, konfety, srdce…) má na volné straně vedle
spodku bubliny kolečko s emoji efektu (`EffectMark` v `items/MessageEffect.kt`) a animace se nad
ní přehraje: sama jednou u zprávy, která přišla po otevření chatu (`claimEffect`, stejná
tolerance jako hod kostkou) nebo je NEPŘEČTENÁ, a znovu po ťuknutí na značku. Převod
`toMessageEffect`, doména
`MessageEffect` v `ChatMessage.effect`.

- **Nepřečtená příchozí zpráva efekt přehraje taky** (`effectAutoplays`, testy
  `EffectAutoplayTest`): kdo ji ještě neviděl, má vidět i efekt, se kterým přišla — tak to dělají
  oficiální klienti. Rozhoduje hranice nepřečtených zachycená při otevření (`unreadBoundaryId`),
  vlastní zpráva se nepočítá. **Hranice dorazí až po prvních zprávách**, proto na ni efekt
  položky čeká (`snapshotFlow { scope.unreadBoundary() }`). **V kompozici se nečte** — viz
  „Recompozice za běhu" (Výkon a paměť): i přes `derivedStateOf` tím obal každé viditelné
  zprávy přeskládala každá změna stavu konverzace. Ověřeno sondou hranice
  (viz „Skok na první nepřečtenou"): obě nepřečtené zprávy s 🔥 zahořely po otevření chatu.
  Ověření bez sondy v #325 nic nedokazovalo — chat otevřený do pěti vteřin po zprávě spadl pod
  toleranci příchodu, ne pod nepřečtené. Efekt u nejnovější zprávy
  zčásti zajede pod vstupní pole — to leží nad seznamem.
- **Efekt se dočítá při převodu zprávy** (`getMessageEffect`, pamatuje se pro instanci klienta),
  jako jméno inline bota. Efektů je pár desítek a opakují se; `fileId` animace platí jen v jedné
  databázi.
- **Druhy jsou dva a animaci nese každý jinde**: efekt reakce `effectAnimation`, prémiová
  samolepka `premiumAnimation` své samolepky. `when` bez `else`.
- **Značka vedle zprávy, ne pod ní**: zpráva s efektem není vyšší a animace vybuchne z volného
  pruhu. Animace je dítě značky, které hlásí nulovou velikost, takže nic nepřeskládá.
- **Plátno animace je navržené pro ODCHOZÍ zprávu**: zdroj výbuchu leží asi ve ¾ šířky
  (`EFFECT_SOURCE_X`) a výbuch letí doleva. Na značku se kotví ten bod, ne střed plátna —
  vystředěný oheň stál o ~50 dp vedle značky. U příchozí zprávy se animace zrcadlí; bez zrcadlení
  letěla srdce doleva přes bubliny (ověřeno obojí na emulátoru).
- **Dokud efekt hraje, má položka `zIndex(1f)`.** Animace přesahuje zprávu a starší zprávy se
  v `LazyColumn` kreslí po ní — přikryly by ji. `zIndex` na kořeni položky pořadí kreslení mezi
  položkami mění.
- **Animace je krátká (kolem vteřiny)**: snímek o půl vteřiny po ťuknutí už nic nezachytí
  a vypadá to, že ťuknutí nefunguje. Ověřuje se sérií `screencap` v jednom `adb shell`.
- **Poslat s efektem jde z podržení odesílacího tlačítka** („Odeslat s efektem…" →
  `EffectPickerSheet`, `SendOptions.effectId` → `MessageSendOptions.effectId`). Ťuknutí na efekt
  zprávu rovnou pošle; v mřížce je jen emoji efektu, animace se ukáže nad odeslanou zprávou.
- **Podržení efektu ve výběru ho přehraje** (náhled), ťuknutí dál rovnou posílá. Animace je
  tatáž `EffectAnimation` jako u značky zprávy (kotvená zdrojem výbuchu, jako u ODCHOZÍ zprávy)
  a leží nad CELÝM sheetem, ne v políčku: mřížka ořezává ve směru scrollu a výbuch nad políčkem
  by uřízla. Kotví se ve středu podrženého políčka (`localPositionOf` vůči sheetu). Sheet sám
  ořezává svou plochu, takže co vyletí nad jeho horní hranu, vidět není — konfety u krátkého
  sheetu dosáhnou jen k nadpisu, a to na náhled stačí.
- **Jen v soukromém chatu, i s botem** (`SendMenuOffer.effects`, testy `SendMenuTest`) — jinde
  TDLib efekt nepřijme; v Uložených zprávách a ve vlákně komentářů se nenabízí.
- **Seznam posílá server JEDNOU po startu** (`UpdateAvailableMessageEffects`: efekty reakcí,
  pak prémiových samolepek), takže ho drží eager `ChatRepository.availableEffectIds`, jako
  emoji kostek. Efekty se dočítají až při otevření výběru (`sendableEffects`, tentýž keš jako
  u zpráv) — většina odeslání efekt nechce.
- **Prémiové efekty se bez Premium nenabízejí** (`MessageEffect.needsPremium`), stejně jako
  prémiové samolepky. Testovací účet jich dostane šest (👍 👎 ❤️ 🔥 🎉 💩).
- **Ověřuje se botem**: `sendMessage` s `message_effect_id` (🔥 5104841245755180586,
  🎉 5046509860389126442, ❤️ 5159385139981059251) do chatu s testovacím botem. Ověřeno na
  emulátoru: značky, samočinné přehrání po příchodu, přehrání ťuknutím, kresba nad staršími
  zprávami. Odeslání s efektem: bot přes `getUpdates` dostal `effect_id`, u odchozí zprávy
  vylétají konfety doleva do volného pruhu (zrcadlení sedí). **Neověřeno**: efekt prémiové
  samolepky (testovací účet Premium nemá) a iOS (jen přeložené).

### Vlastní emoji v textu

Vlastní emoji (`TextEntityTypeCustomEmoji`) je samolepka, která v textu stojí místo náhradního
emoji. Kreslí se jako inline obsah `RichText` (`CustomEmojiImage`, `customEmojiRanges`, testy
`CustomEmojiRangesTest`), data dává `CustomEmojiRepository` nad `getCustomEmojiStickers`.

- **Náhradní znak v řetězci ZŮSTÁVÁ**, jen se místo něj vykreslí obrázek. Inline obsah se
  připojuje anotací nad UŽ PŘIPOJENÝM úsekem (`addStringAnnotation` se značkou
  `INLINE_CONTENT_TAG`), ne `appendInlineContent` — ten by text přestavěl a posunul offsety
  všech entit, zvýraznění hledání i spoilerů. Značka je v Compose `internal`, a proto opsaná;
  `appendInlineContent` ji přidává přesně takhle.
- **Placeholder je čtverec 1,2 em** na střed řádku; než obrázek dorazí (a když ho server
  nevydá), kreslí se v něm náhradní emoji, takže se výměnou nic nepřeskládá.
- **V zakrytém spoileru se nenahrazuje** — obrázek by prozradil, co je skryté.
- **Dotazy se slučují do dávky** (16 ms, nejvýš 200 id na volání) a výsledek se pamatuje po
  dobu života klienta; `fileId` platí v jedné databázi, takže se po odhlášení zahazuje.
  Nevydané emoji se zapomene, ať se o něj příště řekne znovu.
- **`needsRepainting` = obarvit barvou textu** (`tinted`, `SrcIn` nad vlastní vrstvou) — na
  odchozí bublině by jednobarevné emoji jinak splynulo.
- **Formáty jako u samolepky**: `.tgs` hraje (Compottie), `.webp` a rastrový náhled jsou obrázek,
  `.webm` zůstane náhledem, bez náhledu náhradním znakem.
- **Zpráva s vlastním emoji nejde upravit** — vlastní emoji pole nenese (`composedFormatsOf`),
  takže by ho úprava smazala.
- **Bot vlastní emoji poslat nesmí** (Bot API je bez placeného uživatelského jména na Fragmentu
  potichu zahodí, v odpovědi pak `entities` chybí). Ověřuje se proto sondou: skutečná id vydá
  Bot API `getStickerSet` (sady `RestrictedEmoji`, `StatusEmoji`) a dočasně se přidají jako
  entity textu od testovacího bota v `FormattedText.toBody`. Necommituje se.
- **Ověřeno na emulátoru sondou**: animovaný `.tgs` uprostřed textu, emoji na konci řádku,
  zpráva jen z vlastních emoji ve velké sazbě, obarvení v tmavém motivu (podstrčené `tinted`).
  **Skutečná zpráva od člověka ověřená není.**

### Vlastní emoji mimo text

Reakce, emoji status za jménem, náhled v seznamu chatů, citace a ikona tlačítka bota. Všechno
jde přes tentýž `CustomEmojiImage`; samostatně stojící emoji je `CustomEmojiIcon`, emoji
v obyčejném `Text` (náhled, citace) `appendWithCustomEmoji` + `customEmojiInlineContent`.

- **Reakce vlastním emoji jsou řetězcový KLÍČ, ne nový typ** (`customEmojiReaction`,
  `customEmojiIdOf`, testy `ReactionKeyTest`). Reakce jdou jako `String` přes celou cestu —
  nabídka, optimistické přepnutí, prohlížeč médií, klíče v mřížce výběru — a všude jsou jen
  porovnávaným klíčem. Vědět o nich musí jen hranice TDLib (`toReactionKey` / `toReactionType`,
  `when` bez `else`) a kreslení; typ by měnil celou cestu kvůli dvěma koncům.
- **Nabídka reakcí vynechá `needsPremium`**, když účet Premium nemá — ťuknutí by skončilo chybou.
  `getMe` se volá, jen když taková reakce v nabídce je.
- **Náhradní znak reakce ani ikona tlačítka nenesou** (jen id), takže než obrázek dorazí, je
  místo prázdné. V textu drží místo náhradní znak.
- **Emoji status nese `Chat.emojiStatus`** a mění ho `UpdateChatEmojiStatus` — na rozdíl od
  ověření se na uživatele ani supergroupu ptát nemusí (`statusEmojiId`, testy
  `EmojiStatusTest`). Prošlý status se nekreslí i bez updatu: chat z databáze ho nese dávno
  prošlý. Vylepšený dárek se kreslí svým modelem (`modelCustomEmojiId`). Jednobarevný status má
  barvu akcentu, ne textu (`EmojiStatusBadge`), na vybraném chatu přes `accentOn`.
- **Kdo kreslí emoji statusy, musí v pruhu spamu upozornit, že status nic neověřuje**
  (`SpamBar.warnsEmojiStatus`). Dokumentace `chatActionBarReportSpam` / `ReportAddBlock`:
  „a notice about emoji status usage must be shown" — status jde koupit s Premium a u jména
  vypadá jako odznak. Proto přišlo upozornění v TÉMŽE PR jako statusy, ne později.
- **Jméno v profilu je v řádku se statusem, a kde hlavička končí, hlásí ŘÁDEK.** `onPlaced`
  na textu uvnitř řádku by dal pozici vůči řádku, ne vůči hlavičce — a lišta by ukázala název
  v nesprávnou chvíli.
- **Náhled v seznamu nese jen vlastní emoji a spoilery** (`previewEntities`,
  `ChatSummary.lastMessagePreviewEntities`, `ChatDraft.entities`). Rozsahy sedí, protože náhled
  bere text zprávy nebo popisek doslova; prázdný popisek („Fotka") entity nemá. Offsety se za
  prefixem „Koncept: " posunou samy (`appendWithCustomEmoji`, testy `AppendWithCustomEmojiTest`).
- **Citace kreslí vlastní emoji jen u celé zprávy.** Ruční úryvek entity nenese a offsety
  zprávy by do něj ukazovaly jinam.
- **V notifikaci zůstává náhradní znak** — notifikaci kreslí systém a vlastní obrázek do textu
  nevloží. Není to chyba, náhradní emoji je přesně to, co odesílatel k obrázku zvolil.
- **Bot nic z toho nevyrobí**: vlastní emoji v textu zahodí (viz výš), reakci vlastním emoji
  odmítne (`REACTION_INVALID`) a `icon_custom_emoji_id` Bot API sice vrátí v odpovědi, ale do
  zprávy se nedostane (TDLib hlásí `iconCustomEmojiId = 0`) — smí ho jen bot s Fragment jménem
  nebo majitelem s Premium. Ověřuje se proto sondou: vlastní emoji v `FormattedText.toBody`,
  reakce v `toReactions` a `availableReactions`, status v `toSummary` / `chatInfo` / `profile`
  a ikona v `toBotKeyboard`, vše pro chat s testovacím botem. Necommituje se.
- **Ověřeno na emulátoru sondou**: status v seznamu, hlavičce a profilu; vlastní emoji
  v náhledu, v citaci nad polem, v čipu reakce (i vybrané s počtem), v pruhu rychlých reakcí
  a ve výběru; ikona na tlačítku bota; upozornění na status v pruhu spamu (česky, podstrčeným
  pruhem). **Skutečná data od člověka ověřená nejsou**, stejně jako
  přidání reakce vlastním emoji (bez Premium ji server nabídne jen tam, kde ji chat povolil)
  a detail reakcí s vlastním emoji (kreslí ho tentýž `CustomEmojiIcon` jako čip).

### Emoji podle slov

Napsané slovo, ke kterému má TDLib klíčové slovo („srdce", „heart"), nabídne nad polem pilulku
s emoji (`EmojiSuggestionsPanel`, `EmojiSuggestionsViewModel`, pravidla `emojiWordAt`
a `emojiReplacement`, testy `EmojiSuggestionsTest`). Ťuknutí dosadí emoji MÍSTO slova a zařadí
ho mezi nedávná.

- **Přesná shoda, ne začátek** (`getKeywordEmojis`, `EmojiRepository.keywordEmojis`).
  `searchEmojis` hledá podle začátků a panel by vyskakoval u každého druhého slova.
- **Slovo u KURZORU, jen písmena, aspoň dva znaky, a celé** — kurzor uprostřed slova nic
  nenabídne. Za `@`, `#`, `/` a v adrese (`.`, `:`) to slovo není. S dotazem na inline bota,
  zmínkou nebo příkazem se nenabízí: to napovídání má nad polem přednost.
- **Jazyk zařízení a angličtina**, jako hledání ve výběru emoji; výsledky se zúží na katalog
  a dostanou zvolený odstín pleti.
- **Panel nad polem drží poslední sadu ve STAVU, ne v obyčejném poli.** Napovídání zmínek mělo
  `remember { arrayOf(…) }` a zápis do pole kompozici nevyvolá: obsah uvnitř
  `AnimatedVisibility` zůstal u první sady — po „hear" → „heart" dál 👂 🙉, přestože
  ViewModel už vracel srdce (ověřeno sondou v logu). Opraveno v obou panelech.
- **Pilulka PLAVE nad polem a místo v rozvržení nezabírá** — viz „Plovoucí napovídání nad
  polem" (Konverzace).
- **Ověřeno na emulátoru** v Uložených zprávách jen psaním: „heart" anglicky i „srdce" česky,
  přechod „hear" → „heart", ťuknutí dosadí emoji. Nic se neodeslalo.

## Výběr samolepek

Tlačítko se samolepkou ve vstupním poli otevře bottom sheet: záložky Nedávné, Oblíbené
a nainstalované sady, pod nimi mřížka. Ťuknutí samolepku pošle (i jako odpověď).

- **Bottom sheet, ne panel místo klávesnice.** Výměna klávesnice za panel tak, aby vstupní pole
  neposkočilo, je v Compose nejkřehčí část — sheet ji nepotřebuje. Klávesnice se před otevřením
  zavře, jinak by se se sheetem přetahovaly o spodek obrazovky.
- **Hranice je `StickerRepository`** (vlastní, ne kus konverzace): seznamy jsou živé toky, které
  se na update **čtou znovu** — updaty nesou jen id sad a souborů. Odběr stojí před prvním čtením.
- **Záložky jsou stránky `HorizontalPager`u** — mezi sadami se swipuje do stran. Řádek záložek
  a pager sdílejí `PagerState`: swipe přepne záložku (`currentPage` už v půlce tahu), ťuknutí
  na záložku posune pager. Počet stránek je lambda, protože sady dorazí až po nedávných
  a oblíbených; klíč stránky je záložka, ne index, aby se stránkám nerozsypal scroll.
- **ViewModel drží obsah VŠECH záložek**, ne jen vybrané — při swipu je vidět i sousední stránka.
- **Obsah sady se načítá až když se její stránka poprvé ukáže** (`load` v `LaunchedEffect` stránky;
  pager skládá jen viditelné). Sad bývá desítky.
- **V mřížce samolepky NEHRAJÍ** (`AnimatedSticker(isPlaying = false)`). Desítky animací v jedné
  mřížce by stály víc, než přinesou. Bere se nejlevnější podoba: rastrový náhled, statická `.webp`,
  a teprve bez nich první snímek `.tgs`.
- **Podržením se samolepka rozhýbe — vždy nejvýš jedna** (`activeStickerId`). Hraje, dokud se
  nepodrží jiná, a přežije přepnutí záložky. Do načtení animace drží místo statický náhled, takže
  se rozběhne bez probliknutí. Podržení nic neposílá; `combinedClickable` po dlouhém stisku klik
  nevyvolá. Aktivní buňka je podbarvená — statická `.webp` se rozhýbat nemá jak a bez podbarvení
  by nebylo poznat, že podržení zabralo.
- **Sheety výběru a sady nemají napůl rozbalený stav** (`enabledValues = Hidden, Expanded`).
  Jejich obsah je vyšší než polovina obrazovky a napůl rozbalený sheet schoval spodek pod okraj —
  tlačítko „Odebrat sadu" skončilo pod navigační lištou. V sheetu SADY řeší spodní inset
  výchozí `BottomSheetDefaults.modalWindowInsets` — je tam tlačítko a to nad lištou stát musí.
- **Sheet výběru (emoji, samolepky, GIFy) si z insetů bere jen stavovou lištu a klávesnici**
  (`expressionSheetInsets`), navigační lištu dostanou seznamy do `contentPadding`
  (`sheetListPadding`). S výchozími insety byla lišta odsazením celého obsahu: mřížka končila
  ostrou hranou nad ní a pod ní zůstal prázdný pruh sheetu. Plochy s pevnou výškou jsou o lištu
  vyšší (`sheetBottomInset`), aby viditelná část zůstala stejná. **Klávesnici seznamům nechat
  nejde** — mřížka má pevnou výšku a skoro celá by zmizela pod ní; proto `navigationBars
  .exclude(ime)`: s otevřenou klávesnicí lištu kryje ona a její místo drží sheet.
- **Mřížka má pevnou výšku**, jinak by se sheet při přepnutí záložky natahoval a smršťoval.
- **Hledání má dva zdroje.** `getStickers` prochází nainstalované sady podle emoji i podle
  klíčových slov samolepek; `searchStickers` veřejné sady, ale jen podle emoji. Slovo se proto
  převádí na emoji přes `getKeywordEmojis` (potřebuje `useFileDatabase = true`) v jazyce zařízení
  a angličtině — klíčová slova samolepek bývají anglicky. Bez převedených emoji se veřejné hledání
  vynechá, vrátilo by nahodilé samolepky. Jestli je dotaz emoji, nebo slovo, rozhoduje ViewModel
  (`emojiClusterCount`), repository to dostane jako `StickerQuery`.
- **Hledá se po 300 ms pauze v psaní, přes `transformLatest`**: rozepsané hledání se při dalším
  znaku zahodí, takže pomalá odpověď na „ko" nepřepíše výsledky pro „kočka".
- **Zdroj, který selže, výsledek nezahodí** — bez sítě se aspoň najde, co je ve vlastních sadách.
- **Výsledky hledání nahradí záložky ve stejně vysoké ploše** (řádek záložek má proto pevnou
  výšku), jinak by sheet při prvním písmenu poskočil. Co se ukazuje, rozhoduje text v poli, ne
  stav hledání — po smazání textu se záložky vrátí hned, bez čekání na debounce.
- **Oblíbené se přepínají hvězdičkou u PODRŽENÉ samolepky**, ne u každé buňky. Hvězdička všude
  by zaplevelila mřížku a ťuknutí vedle ní by samolepku omylem poslalo; podržení je zároveň
  „prohlížím si tuhle". Stav se lokálně nepřepisuje, potvrzení přijde `favoriteStickersUpdates`.
- **Buňky mřížky mají `animateItem`** — odebraná samolepka plynule zmizí a ostatní se dosunou.
  Průhlednost přes effects spec, posun přes spatial. **Mřížka zůstává i prázdná** a prázdný text
  se nad ní jen objeví: kdyby ji prázdný stav nahradil, zmizela by i s poslední samolepkou dřív,
  než ta stihne odejít animací.
- **V konverzaci jsou oblíbené v nabídce nad zprávou se samolepkou.** Jestli je samolepka
  oblíbená, se zjistí při otevření nabídky (souběžně s ostatními příznaky). Výsledek hlásí
  snackbar (`notice`) — v konverzaci se jinak nic viditelného nezmění.
- **Samolepka se do oblíbených dává podle souboru** (`stickerFileId`), ne podle id samolepky —
  tak to TDLib chce (`addFavoriteSticker(InputFileId)`). `MessageMedia.Sticker` ho proto nese
  vždycky, bez ohledu na formát. Oblíbených je omezený počet; přidání nad limit vytlačí nejstarší.
- **Ťuknutí na samolepku v konverzaci otevře její sadu** (`StickerSetSheet`): celá sada, přidat /
  odebrat (`changeStickerSet`), mřížka se chová jako ve výběru. Klikání na samolepce je **bez vlnky**
  (průhledný tvar bez bubliny) a opakuje dlouhý stisk kvůli nabídce. Sada se nainstalovanost
  dozví z `installedStickerSetsUpdates` — jiný update o sadě TDLib nepošle; tlačítko je mezitím
  neaktivní, aby nešlo zmáčknout dvakrát. Oficiální sady odebrat nejdou. ViewModel sady má klíč
  s id sady, stejně jako konverzace.
- **Trendy jsou záložka hned za Oblíbenými** — na konci za desítkami sad by je nikdo nenašel.
  Místo mřížky mají seznam sad s ukázkou a tlačítkem Přidat; ťuknutí na sadu otevře její detail
  jako **sheet nad výběrem** (samolepka poslaná z detailu zavře oba). Trendy jdou na server, proto
  mají vlastní tok s `WhileSubscribed` mimo stav výběru a načtou se až při zobrazení stránky.
  Přepočítávají se i na `installedStickerSetsUpdates` — přidání sady trendy samy nehlásí. Po
  zobrazení se označí za zhlédnuté (`viewTrendingStickerSets`).
- **Prémiové samolepky se bez Premium nenabízejí** (`StickerFullTypeRegular.premiumAnimation`).
  Odeslat je nejde a samolepka, po které přijde chyba, je horší než žádná. Na Premium se ptá
  `getMe` při každém čtení — předplatné se mění.
- **Posílá se soubor samolepky** (`InputFileId`), ne náhled; náhled se nenahrává, samolepka už
  na serveru je. Po odeslání se výslovně přidá mezi nedávné, aby byla hned nahoře.
- **Sheet se nejdřív schová, teprve pak se pošle.** Zmizení bez animace vypadá jako pád.
- **Samolepka nezakládá let ze vstupního pole** a nemaže rozepsaný text — z pole nevyletěla.
- **Ikony Material Symbols ze SVG mají souřadnice y v -960..0.** Ve vector drawable se to řeší
  `group` s `translateY="960"`, cesta zůstává, jak je.

## Výběr GIFů

Třetí část sheetu výběru (Emoji / Samolepky / GIFy, `ExpressionMode.Gifs`): pole hledání, pod
ním mřížka. Bez dotazu nahoře uložené GIFy, pod nimi populární. Ťuknutí GIF hned pošle
(`GifPanel`, `GifPickerViewModel`, hranice `GifRepository` v `core/telegram`).

- **Hledání GIFů API nemá — hledá inline bot.** Jméno posílá server (`animation_search_bot_username`,
  dnes `gif`), `searchPublicChat` z něj udělá id a `getInlineQueryResults` vrací
  `InlineQueryResultAnimation`. Prázdný dotaz = populární. Stránkuje `nextOffset` (prázdný =
  konec). Id bota se pamatuje pro INSTANCI klienta — po odhlášení je jiný.
- **Posílá se `InputFileId`, ne `sendInlineQueryResultMessage`.** GIF z výsledku je soubor, který
  na serveru už je; přes bota by u zprávy stálo „přes @gif" a byly by to dvě různé cesty pro
  uložený a nalezený GIF. Po odeslání `addSavedAnimation`, ať je nahoře (jako `addRecentSticker`).
- **Uložené jsou živý tok** (`UpdateSavedAnimations` → znovu `getSavedAnimations`). GIF poslaný
  z klávesnice se mezi ně dostane sám. Podržení uloženého nabídne křížek na odebrání.
- **Stránka patří dotazu** (`GifResults.withPage`, testy `GifResultsTest`) — pomalá odpověď na
  „ko" nesmí přepsat „kočka". Bot vrací tentýž GIF na dvou stránkách; duplicitní klíč by mřížku
  shodil, proto `distinctBy`.
- **GIFy v mřížce hrají**, jako v konverzaci: GIF se vybírá podle toho, co dělá. Hrají jen
  složené buňky a velký se sám nestahuje (`autoPlays`).
- **Obraz přehrávače se do buňky ROZTAHUJE, ne vepisuje** (`coverAspect`). `VideoSurface` obraz
  vepisuje, takže v buňkách pevné výšky zůstávaly pruhy a každý GIF měl jinou velikost.
- **Právo je totéž jako u samolepek** (`canSendOtherMessages`): bez něj ani při úpravě se část
  GIFy nenabízí. `ViewModel` klíčovaný chatem (`gifs-$chatId`) — inline bot chce vědět, kam se hledá.
- **Zdrojový soubor nesmí obsahovat znak NUL.** Konstanta `"\u0000"` zapsaná nástrojem jako
  skutečný bajt udělala z `.kt` pro `grep` binární soubor a hledání v něm tiše nic nenašlo.

## Výběr emoji

Týž sheet jako samolepky, přepínaný segmentovými tlačítky Emoji / Samolepky (a tlačítko ve vstupním
poli má proto ikonu smajlíka). Emoji se **vkládá do rozepsaného textu** na pozici kurzoru.

- **Katalog je vygenerovaný z Unicode `emoji-test.txt`** (`EmojiCatalog.kt`, neupravovat ručně).
  TDLib žádný katalog nemá, jen slovník klíčových slov. Jen fully-qualified emoji **do verze 15.0**
  — novější by na starších systémech, jejichž písmo je nezná, byly prázdné čtverečky — a bez
  variant odstínu pleti. Emoji skupiny jsou jeden řetězec oddělený mezerou, ne `listOf` s tisíci
  literály: inicializace by na JVM narážela na limit velikosti metody.
- **Rozepsaný text drží obrazovka, ne vstupní pole** (`draft: TextFieldValue`). Pole je pod sheetem
  a výběr do textu vkládá i z něj maže. Úprava z výběru volá `onDraftChanged` jako psaní —
  ukládá koncept i hlásí „píše…".
- **Sheet se po vložení nezavírá** — emoji se píšou po několika. Nahoře je proto náhled textu
  (`StartEllipsis`, protože vkládá se na konec) s vlastním mazáním.
- **Mazání bere celý znak, jak ho vidí uživatel** (`previousClusterStart`): surrogate páry,
  modifikátory (odstín, výběr varianty, rámeček klávesy, tagy vlajek regionů), ZWJ složeniny
  a vlajky jako dvojice indikátorů. Smazat jednu jednotku UTF-16 by nechalo v textu půlku emoji.
  Pokryté testy v `commonTest`.
- **Hledání přes `searchEmojis`** v jazyce zařízení a angličtině, výsledky zúžené na katalog.
  TDLib vrací emoji občas bez U+FE0F; porovnává se bez něj a vkládá se podoba z katalogu, jinak
  by se na části systémů nakreslila jako černobílý textový symbol.
- **Nedávná emoji jsou první záložka** (ukládá se ťuknutí ve výběru, i ve výsledcích hledání;
  emoji ze systémové klávesnice zachytit nejde). **Ve sheetu jsou zmražená** na stav při otevření —
  živý seznam by se po každém ťuknutí přeskládal pod prstem. Bez nedávných se výběr otevře
  rovnou na první skupině. Při čtení se zúží na katalog.
- **Odstíny pleti se volí podržením** emoji, které je má (trojúhelník v rohu buňky): nabídka nad
  buňkou s výchozí podobou a pěti odstíny. Volba se **pamatuje po emoji** a mřížka pak kreslí
  i obyčejným ťuknutím vkládá zvolenou variantu — v kategoriích, hledání i nedávných. Varianty
  jsou vygenerované z téhož `emoji-test.txt` (`EmojiSkinTones.kt`), jen se stejným odstínem pro
  všechny osoby: kombinace dvou odstínů by znamenaly 25 variant. Nabídka je `Popup` s vlastním
  `PopupPositionProvider` (nad buňku, vodorovně uvnitř okna), zpět obsluhuje sám.
- **Emoji se dvěma osobami (👫, 🤝, 💑, 💏 a jejich varianty, 13 emoji) mají místo řady mřížku
  5 × 5** (`mixedSkinToneGrid`, `EMOJI_MIXED_SKIN_TONES_PACKED`): řádek je odstín první osoby,
  sloupec druhé, na úhlopříčce stejný. Zvolený pár se pamatuje jako každá varianta (podle
  základu), takže se pak kreslí i vkládá obyčejným ťuknutím. Smíšené varianty jsou v Unicode do
  15.0 (handshake 14.0, páry 12.0–13.1), takže mez katalogu platí i tu.
- **Odstíny generuje `client/tools/emoji_skin_tones.py`** z `emoji-test.txt` (návod v hlavičce
  skriptu). Původní generátor v repu nebyl; nový vyrobil stejnorodé varianty znak po znaku
  shodně se starým souborem, než se přidaly smíšené. **Základ se hledá podle JMÉNA, ne kódových
  bodů**: smíšené varianty páru se píšou rozloženě (👩🏻‍🤝‍👨🏼), kdežto základ je jeden znak (👫);
  „kiss: person, person" jako emoji neexistuje, jeho základ je 💏 (jméno před dvojtečkou).
- **`catalogEmoji` pozná i varianty** — nedávná je ukládají, a bez toho by při čtení vypadla.
- **Výšky obou částí sedí**: náhled textu si ukousne z výšky mřížky, takže přepnutí Emoji /
  Samolepky sheet nenatáhne.

## Reakce

- **TDLib nezná „přepnutí".** Má `addMessageReaction` a `removeMessageReaction`; jestli reakce
  už je, si musí pamatovat klient (`MessageReaction.isChosen`).
- **Stav se mění lokálně hned**, ještě před odpovědí serveru — jinak by mezi ťuknutím a potvrzením
  byla viditelná prodleva. Při selhání se změna vrátí; autoritativní stav stejně dorazí přes
  `reactionUpdates` (`UpdateMessageInteractionInfo`).
- **Reakce, která klesne na nulu, ze seznamu zmizí.** Čip s nulou vypadá jako chyba.
- **Obyčejná i vlastní emoji, placená (hvězdičky) ne.** Vlastní emoji se kreslí jako obrázek
  (`CustomEmojiIcon`), viz „Vlastní emoji mimo text". Placená reakce se přeskočí, aby u zprávy
  nezmizely ty ostatní.
- **Prázdná nabídka není chyba.** V Uložených zprávách Telegram místo reakcí používá tagy a
  `getMessageAvailableReactions` vrátí prázdný seznam. Testovat reakce tam tedy nejde.
- **Pořadí rychlých reakcí podle zvyku uživatele dodává Telegram, vlastní počítadlo NEPSAT.**
  `getMessageAvailableReactions` vrací `topReactions` (nejčastěji používané, řadí server a drží
  je napříč zařízeními), `recentReactions` a `popularReactions`; skládají se v tomhle pořadí bez
  duplicit a pruh bere prvních šest (`MAX_QUICK_REACTIONS`). Přidání reakce posílá
  `updateRecentReactions = true`, takže se i reakce z Plume propíše do „nedávných" všude.
  Počítadlo v aplikaci by vidělo jen reakce z tohohle zařízení a s pořadím serveru by se přelo.
- **Pruh reakcí bere jen ÚMYSLNÉ ťuknutí** (`strayTapGuard` v `core/ui`, testy
  `StrayTapGuardTest`) — v prohlížeči médií i nad zprávou. Srdíčko se dvakrát samo přidalo
  fotce, aniž by uživatel o nabídce věděl. **`clickable` tah nezruší, dokud prst neopustí
  tlačítko** (`waitForUpOrCancellation` ruší jen spotřebovanou událost nebo pohyb MIMO hranice,
  rozšířené na 48 dp); nad seznamem pohyb spotřebuje scroll, jenže pruh reakcí nad sebou nic
  scrollovatelného nemá — nedbalý švih začínající na srdíčku byl ťuknutí. Pojistka v
  `PointerEventPass.Initial` spotřebuje dotyk, který ujel přes touch slop, šel víc prsty, nebo
  začal do 500 ms od objevení pruhu (pruh vyskočil pod prstem: druhé ťuknutí na fotku, scroll po
  omylem otevřené nabídce). **Který z případů to u uživatele byl, ověřené není** — pojistka
  kryje všechny tři; ověřuje se na telefonu švihem přes pruh v prohlížeči.
- **Blok `pointerInput` se NESPOUŠTÍ při složení, ale až s PRVNÍM dotykem**
  (`SuspendingPointerInputModifierNodeImpl`: „Coroutine lazily launches when first event comes
  in"). První verze pojistky si v něm zapisovala čas objevení pruhu — ve skutečnosti tedy čas
  prvního dotyku, takže lhůta 500 ms spolkla KAŽDÉ první ťuknutí, i to po deseti vteřinách:
  reakce z nabídky nad zprávou zabírala až na druhé ťuknutí. Test čisté funkce `isStrayTap` to
  nechytil, modifier sám nezkoušel. Čas proto zapisuje `Modifier.Node` v `onAttach` a při změně
  klíče (`update`). **Obecně: co se má měřit od objevení prvku, nepatří do `pointerInput`.**
- **V prohlížeči pruh reakcí vyjede zdola, až prohlížeč dosedne** (přechod navigace i let fotky
  hotové, `LocalNavAnimatedContentScope.transition` ve stavu `Visible`) a reakce jsou načtené.
  Během letu by vyskočil tam, kam fotka teprve míří — a tam dopadá druhé ťuknutí dvojitého
  ťuknutí. Vyjíždí jen poprvé; po celé obrazovce a mezi stránkami se jen prolne jako horní lišta.
  Změřeno na emulátoru: fotka dosedne ~0,86 s po ťuknutí, přechod se ohlásí hotový až ~1,24 s
  (dozní pružina), pruh vyjede s tím odstupem.
- **`isBig = false`** — `true` je ta celoobrazovková animace; patří k vědomému gestu, ne ke
  každému ťuknutí na čip.
- **Avatary v čipech NEJSOU.** Byly, a byla to slepá ulička: čip s profilovkami je široký, takže
  se jich vedle sebe vešlo pár, a stejně ukazoval jen několik posledních (`recentSenderIds` není
  úplný seznam — celkový počet je v `totalCount`). Čip proto nese jen emoji a počet, a KDO
  reagoval, říká detail. Odpadly s tím i stropy `MAX_AVATARS_PER_REACTION`,
  `MAX_REACTIONS_WITH_AVATARS` a `MAX_SENDERS_SHOWN` a dotazy na odesílatele u každé zprávy
  s reakcemi.
- **Čip je kolečko, počet je odznak přes jeho roh** — ne kapsle s číslem vedle emoji. Čip leží
  PŘES nároží bubliny, takže musí být co nejmenší; kulatý tvar na to sedí líp a číslo vedle emoji
  by ho roztáhlo. **Počet se vypisuje až od dvou**: „1" je šum, že tam někdo je, říká samotný čip.
- **Místo pro odznak si vyhradí obálka čipu, odznak z kolečka NEČOUHÁ.** Řádek reakcí se posouvá
  do strany a posouvatelný kontejner si obsah ořezává na svoje hranice — zavěšený odznak by se
  uřízl.
- **Bublina má s reakcí i bez ní STEJNOU velikost.** Čip je její odznak: leží přes spodní nároží
  a na bublinu zasahuje kolečkem o 8 dp (`REACTION_CHIP_ON_BUBBLE`) — přesně o spodní odsazení
  obsahu, takže nezakryje poslední řádek. Středem na okraji (zkoušeno) zakrýval začátek
  posledního řádku; úplně pod zprávou (zkoušeno) přestal působit jako její odznak. Obsah si pro
  něj místo NEDĚLÁ — dřív si ho dělal uvnitř (`REACTION_STRIP_SPACE`) a zpráva se při první
  reakci skokem zvětšila.
- **Kolečko lícuje s hranou bubliny**, jako v Google Zprávách — ne 10 dp uvnitř. Řádek se vysune
  o místo na odznak počtu (vpravo od kolečka) a o místo na stín, takže samotné kolečko končí
  přesně na okraji.
- **Čip má lem v barvě pozadí A drobný stín** (`REACTION_RING` v `surface`, `REACTION_ELEVATION`
  1 dp). Lem odděluje čip od bubliny, jako by z ní byl vykrojený (Google Zprávy). Jenže pozadí
  konverzace je `background`, v M3 totéž co `surface`, takže mimo bublinu lem zmizí a bílý čip
  na skoro bílé ploše splýval — hlavně ve sloupci pod dlouhými příspěvky v kanálu. Stín s
  elevací 1 dp se vejde do rezervy řádku (`REACTION_SHADOW_ROOM`, 3 dp), kterou jinak ořezává
  `horizontalScroll`; vyšší elevace by se tam uřízla, a právě proto dřív stín nebyl vůbec.
- **Emoji v čipu má výšku řádku rovnou písmu, na střed a oříznutou** (`LineHeightStyle` s `Trim.Both`).
  Holý `Text` nese nad kresbou rezervu řádku a emoji sedělo v kolečku viditelně níž.
- **Výplň čipu je neutrální i u vlastní reakce** (`surfaceContainerLowest`). Dřív měla vlastní
  `primaryContainer` a emoji od Telegramu — kreslená se stíny a přechody, žádné ploché symboly —
  na tónované ploše splývala: žlutý palec na světle modré nebyl skoro k poznání. Vlastní reakci
  nese **kroužek v `primary` UVNITŘ lemu v barvě pozadí** (na odchozí `primary` bublině ho od
  ní lem odděluje) a plný `primary` odznak počtu.
- **Místo pod zprávou se otevře animovaně** (`REACTION_OVERHANG` přes spatial spec), takže zpráva
  plynule popojede. Místo je uvnitř `MessageBox`, který kreslí i nabídka nad zprávou — čip patří
  ke zprávě a do nabídky jede s ní.
- **Čipy naběhnou z nuly s pružným přeskokem** (tlumení 0,45 — víc než běžný spatial spec; náběh
  reakce je drobná radostná událost, ne přesun obsahu). Při odebrání poslední reakce se dál
  kreslí ta poslední, jinak by odchod dobíhal s prázdným řádkem. Čipy, které tu byly při prvním
  složení, se nezvětšují — jinak by pulzoval scroll.
- **Dokud zprávu kreslí nabídka, drží se její reakce z doby před otevřením** (`heldReactions`,
  v seznamu i v kopii). Reakce se přidává v nabídce a ta se tím zavře; bez podržení by náběh čipu
  doběhl na skryté zprávě a uživatel by viděl čip už hotový. Takhle naskočí, až zpráva dosedne
  zpátky na místo — jako v Google Zprávách. Ověřeno sérií surových snímků: prvních sedm snímků
  (návrat zprávy) bez čipu, pak přeskok a ustálení.
- **Jedou s bublinou i při tahu na odpověď**, protože posun sedí na sloupci kolem toho `Box`u.
- **Jeden řádek, který se posouvá do strany**, ne lámání do víc řádků: čipy leží na bublině
  a druhý řádek by ji přerostl. U příspěvku s deseti různými emoji je to jediné, co se vejde.
- **Přidaná reakce se ohlásí výbuchem**: za emoji se na 600 ms otevře `SoftBurst` v `primary`
  (naroste, pootočí se a vybledne) a čip pružně povyskočí (`ReactionChip`, `burst`). Ne konfety,
  ty patří výhře v kostce. **Jen při přechodu na vybranou reakci před očima**: čip, který byl
  vybraný už při složení, nebo cizí reakce nic nedělají — jinak by bouchal každý scroll.
  - **Co tu bylo při složení, si pamatuje `ReactionRow`, ne `ReactionChips`.** Čipy se skládají
    až s první reakcí (`AnimatedVisibility`), takže jejich „počáteční" množina už novou reakci
    obsahovala a výbuch se u první reakce zprávy nekonal (ověřeno na emulátoru, první pokus).
  - **Výbuch se kreslí UVNITŘ kolečka čipu** (`drawBehind` pod emoji), ne přes jeho okraj:
    řádek reakcí je `horizontalScroll` a ořezává na svoje hranice, přetékající tvar by se uřízl.
  - Průběh se čte až při kreslení, animace nic nerekomponuje. Ověřuje se zpomalenými animacemi
    a sérií surových snímků — v normální rychlosti ho snímek mine.
- **Ťuknutí na čip otevírá DETAIL, nepřepíná reakci.** Přidat i odebrat jde z nabídky nad zprávou,
  kde je vybraná reakce zvýrazněná a ťuknutí na ni ji sundá. Dvě věci na jedno ťuknutí nejdou
  a detail je to, co se jinde nedozvíme.
- **V kanálu detail není** — `getMessageAddedReactions` tam vrátí `BROADCAST_FORBIDDEN`. Čip se
  proto v kanálu nedá ťuknout a nemá ani vlnku: čip, po kterém přijde chyba, je horší než čip,
  který nic nedělá. Rozhoduje o tom `ChatInfo.isChannel`, ne odpověď serveru — ta by přišla až po
  ťuknutí.
- **Detail jde filtrovat podle reakce** — nad seznamem spojená skupina přepínacích tlačítek
  „Vše N" a každá reakce s počtem (`ReactionFilter`, tentýž prvek jako druh chatu ve filtrech
  hledání). Jen s víc druhy reakcí; vybrané je vždycky právě jedno. **Filtruje server**
  (`reactionType` v `getMessageAddedReactions`): filtr nad jedinou stránkou by u příspěvku
  s tisíci reakcemi ukázal zlomek. Počty jsou ze zprávy v okamžiku otevření. Při přepnutí
  zůstává starý seznam, dokud nedorazí nový — indikátor by sheet smrštil a zase natáhl.
- **Detail je jedna stránka** (`REACTION_DETAIL_LIMIT`, 50 řádků). U příspěvku s tisíci reakcemi
  ho nikdo neprojde a stránkování by znamenalo držet offset ve stavu sheetu.
- **Nepřečtená reakce na vlastní zprávu se zvýrazní, jakmile je zpráva vidět** (`NewReaction`,
  `ChatMessage.newReactions` z `Message.unreadReactions`, živě přes `unreadMarkChanges`): čip
  dvakrát povyskočí (`NEW_REACTION_PULSE_SCALE`) a ve skupině vedle čipů na pár vteřin vyjede
  pilulka s tvářemi a jmény těch, kdo reagovali (`ReactorsLabel`, „Jana, Petr a 2 další").
  V soukromém chatu jméno nic nepřidá — reagovat mohl jen protějšek.
- **Zvýraznění se musí PODRŽET** (`rememberReactionHighlight`, 4 s). Zobrazená zpráva se hlásí
  jako zhlédnutá po 400 ms, TDLib reakce přečte a `newReactions` zmizí — bez podržení by
  zvýraznění zhaslo dřív, než si ho kdo všimne. Stav patří složené položce, takže po
  odscrollování a návratu je reakce přečtená a nic nepulzuje.
- **Pilulka místo v rozvržení nezabírá** (`layout` s nulovou velikostí): sloupec zprávy se
  roztahuje podle řádku reakcí, a popisek v něm by bublinu na pár vteřin rozšířil a posunul.
  Leží přes spodní okraj zprávy vedle čipů, stejně jako čip.
- **Ověřuje se botem**: vlastní zpráva do chatu s botem, `setMessageReaction` botem (změna emoji
  je nová nepřečtená), pak chat otevřít. Pulz jde vidět jen zpomaleně
  (`animator_duration_scale 5`) a změřením plochy emoji ve snímcích. Jména se kreslí jen ve
  skupině, takže se v chatu s botem ukážou jen sondou (`namesReactors = true`, necommituje se).
  **Ve skupině ověřená** (testovací skupina s botem, `setMessageReaction` na vlastní zprávu,
  id zprávy v supergroupě = id u serveru): pilulka „Notifikator" vedle čipu.

## Odpovědi (reply)

- **`ChatMessage` nese jen `replyToMessageId`, ne celou citovanou zprávu.** V konverzaci se
  odpovídá skoro vždy na něco, co už je načtené, takže náhled si poskládá `ConversationViewModel`
  z vlastního seznamu a ušetří jedno volání na každou odpověď. Zbytek (odpověď na starou zprávu
  hluboko v historii) dotahuje `resolveMissingQuotes()` do `ConversationState.quotes`.
- **Odpověď na zprávu z JINÉHO chatu nese citaci sama** (`ChatMessage.externalReply`,
  `toExternalReply`). Náhled se jinak skládá z načtené historie, která je vždy jen z jednoho
  chatu, takže dřív se taková odpověď zahazovala a zpráva vypadala jako bez citace. TDLib ale
  posílá v `MessageReplyToMessage` i `origin` a `content` citované zprávy — zástupná
  `ChatMessage` se z nich poskládá bez dotazu navíc. Text je automatický úryvek serveru
  (`quote` s `isManual == false`), bez něj popis obsahu (`MessageContent.previewTextOrNull`);
  jméno z původu, bez něj název chatu.
- **Ťuknutí na ni otevře ten druhý chat na citované zprávě** (`MessageAction.OpenForwardOrigin`,
  tatáž cesta jako značka „Přeposláno od"), ne skok v téhle konverzaci. Nulové `chatId` =
  chat, který server neprozradil; citace se pak kreslí, jen nikam nevede.
- **Bot odpověď z jiného chatu nevyrobí** (musel by mít přístup k oběma chatům), takže se
  ověřuje sondou: v `toChatMessage` dočasně sestavit `MessageReplyToMessage` (chat Uložené
  zprávy, s `origin` a `content`, nebo jen s `quote`) pro text `#ext…` od testovacího bota.
  Prochází tím převod i kreslení i navigace; necommituje se.
- **`InputMessageReplyToMessage.pollOptionId` je nenullovatelný** — prázdný řetězec, ne `null`.
- **Rozepsaná odpověď se ruší hned při odeslání**, ne až po potvrzení; jinak by při pomalé síti
  šlo odeslat druhou zprávu se stejnou citací.
- **V soukromém chatu nemá citace koho uvést.** `senderName` se vyplňuje jen ve skupinách, takže
  se jako záloha bere název chatu (`ChatInfo.title`).
- **Citace části zprávy** (`ChatMessage.replyQuote`, `ConversationState.replyQuote`) se kreslí
  místo začátku citované zprávy — nad odpovědí i v hlavičce nad polem. Bere se jen ruční citace
  (`TextQuote.isManual`); automatickou server doplňuje u odpovědí z jiného chatu, ty se zahazují.
  Citace je součást odpovědi i konceptu (`saveDraft(quote)`, `ChatInfo.draftReplyQuote`) a maže
  se s odpovědí (`clearReply`). Jak vzniká, viz „Citace z nabídky".
- **Citace nese náhled přílohy** (`quoteThumbnail` v `QuotedMessage.kt`, testy
  `QuoteThumbnailTest`): fotka, video, GIF, kolečko (kulaté), obal skladby, samolepka a soubor
  s náhledem — v bublině odpovědi i nad vstupním polem, vlevo od jména. Bere se nejmenší
  varianta; do čtverečku 36 dp se ostrá verze stahovat nemá.
- **Spoiler a citlivý obsah mají v citaci JEN rozmazaný náhled** (`BlurredThumbnail`, nic se
  nestahuje). `RemoteImage` by si ostrou verzi stáhl a po chvíli prolnul — citace by pak
  prozradila, co bublina nad ní schovává. **Mizející a tajné médium náhled nemá vůbec**: citace
  žije v konverzaci déle než médium samo.
- **Odpověď z jiného chatu náhled má taky** (`MessageContent.replyMedia`): fotka, video, GIF
  a samolepka z `MessageReplyToMessage.content`. Celý převod `messageMedia` stojí na `Message`,
  kterou tam nemáme; citlivost cizího chatu neznáme, spoiler ano.
- **Ověřeno na emulátoru** botem (`sendPhoto`, pak `sendMessage` s `reply_parameters`): náhled
  u odpovědi na fotku, rozmazaný u odpovědi na spoiler, náhled nad polem po tahu na odpověď.
  Kolečko, samolepka, obal skladby a odpověď z jiného chatu jsou pokryté testem, **na zařízení
  ověřené nejsou**; iOS jen přeložené.
- **Vynucená odpověď bota může přebít koncept.** `restoreDraftReply` i vynucená odpověď nastavují
  `replyingTo`, jen když je prázdné, a kdo doběhne dřív, vyhraje — koncept se čte asynchronně.
  Při zkoušení citací v chatu s testovacím botem pozor na starou `force_reply`.

## Notifikace

### Jak s notifikacemi zachází TDLib

Notifikace **nespravuje server, ale TDLib v telefonu**. Server posílá zprávy a pushe; co se
ukáže, kdy a kdy zase zmizí, rozhoduje knihovna lokálně a aplikace jen kreslí `UpdateNotificationGroup`.
Notifikace je proto živý objekt, který se může vyměnit, odložit nebo stáhnout — kdo ho vykreslí
doslova, dostane problikávání, dvojí upozornění i notifikace, které zmizí.

- **Tatáž zpráva přijde dvakrát, dvěma cestami.** Nejdřív push (FCM probudí proces, obsah je
  zašifrovaný výtah zprávy), pak — jakmile se TDLib připojí — plnohodnotná zpráva spojením.
  Rozestup bývá desítky až stovky ms. Když spojení ještě žije, přijde jen ta druhá.
- **Z pushe vznikne DOČASNÁ notifikace** (`NotificationTypeNewPushMessage`), protože zprávu
  samotnou TDLib ještě nemá. Jakmile ji má, dočasnou **odebere a přidá novou** (jiné id, typ
  `NewMessage`), navázanou na zprávu, u které jde sledovat přečtení a smazání. Dva updaty ~5 ms
  po sobě; pro systém by to byla nová zpráva.
- **Notifikace se ODKLÁDÁ, místo aby se ukázala a pak stahovala.** Obvykle o 1,5 s
  (`notification_default_delay_ms`): kdo zrovna píše na počítači, přečte si zprávu tam a telefon
  ani nepípne. Když byl účet do 5 min (`online_cloud_timeout_ms`) online na jiném zařízení,
  o 30 s (`notification_cloud_delay_ms`). Hodnoty posílá server. Stav čekání hlásí
  `UpdateHavePendingNotifications`.
- **Stáhnutí existuje taky** — přečtení na jiném zařízení pošle `removedNotificationIds`.
- **Server má vlastní odklad.** Když byl účet nedávno aktivní, push odešle až po ~30 s.
- **Oficiální Android klient TDLib nepoužívá** (vlastní síťová vrstva), notifikaci ukáže rovnou
  z pushe. Srovnávat s ním časování jde, mechanismus ne.

Z toho plyne všechno níž: celý JSON do `processPushNotification`, zobrazování
`NewPushMessage`, čekání na klid ve službě pushe, worker pro 30s odklad, tichá náhrada
a odložené rušení prázdné notifikace.

### Poučení

- **Notifikační API TDLib je ve výchozím stavu VYPNUTÉ.** `notification_group_count_max` je 0,
  což znamená, že `UpdateNotificationGroup` vůbec nevzniká. Musí se zapnout přes `setOption`
  (rozsah 1–25) — viz `enableNotifications()`. Bez toho je celá notifikační vrstva mrtvá
  a **nedá se to poznat**: nepřijde chyba, prostě se nic neděje.
- **TDLib vydává `UpdateNotificationGroup` i bez push služby**, kdykoli proces běží a je připojený.
  Push je jen doprava pro případ, kdy aplikace neběží — zobrazení je v obou případech totéž.
- **Co se zobrazí, rozhoduje TDLib**, ne klient: ztlumené chaty i `showPreview` řeší on. Proto se
  v `TdlNotificationRepository` nic nefiltruje.
- **`google-services.json` je v repozitáři.** Nejsou v něm tajemství: API klíč Firebase je
  identifikátor projektu, ne heslo, a push bez klíče na my.telegram.org stejně nikdo nepošle.
  Soubor v gitu znamená, že push funguje hned po klonu i v debug buildu a CI ho nemusí skládat
  ze secretu. `google-services` plugin se přesto aplikuje podmíněně — bez souboru se projekt
  přeloží, jen bez pushe a hlášení pádů.
- **Push payload je zašifrovaný.** Rozšifrovat ho umí jedině TDLib přes `processPushNotification`;
  služba nic nevykresluje, jen předává.
- **`processPushNotification` chce CELOU datovou část pushe jako JSON**, ne jen pole `p`, a k ní
  `google.sent_time` (a `google.notification.sound`, je-li). S holým `p` TDLib každý push odmítl
  chybou „Failed to parse payload as JSON object" — push tím nefungoval NIKDY, ani u zabité
  aplikace, a v logu to byla jediná stopa.
- **Push se testuje botem, ne druhým účtem.** @BotFather → `/newbot`, botovi poslat `/start`,
  pak `curl https://api.telegram.org/bot<token>/sendMessage -d chat_id=… -d text=…`
  (`chat_id` z `getUpdates`). Zpráva od bota přijde pushem jako od kohokoli jiného. Token patří
  do `local.properties` (`testBotToken`), ne do repozitáře.
- **Doručení pushe trvá různě, klidně 30 s.** Server Telegramu push odkládá, když je účet
  nedávno online — to není chyba aplikace.
- **Proces v pozadí systém ZMRAZÍ, i když ho nikdo nekillnul.** Aplikace odsunutá za jinou
  (oom adj 900, cache) je do ~10 s `isFrozen=true` (`adb shell dumpsys activity processes
  eu.tintera.plume | grep isFrozen`). Zmrazený proces nemá spojení, takže z pozadí chodí
  notifikace JEN pushem — cesta „TDLib je vydá sám, když běží" tam neplatí.
- **`onMessageReceived` proto blokuje, dokud notifikace není na liště** (`awaitDelivered`, strop
  8 s). Služba drží proces rozmrazený jen po dobu té metody; dokud se zpracování jen spustilo
  v korutině, proces zamrzl uprostřed skládání notifikace. „Hotovo" = TDLib nehlásí nevydané
  notifikace (`UpdateHavePendingNotifications`) a presenter zpracoval všechno, co repository
  vydala — počítá se to přes `transform { emit(); … }` za `merge`, kde se `emit` vrací až po
  zpracování odběratelem.
- **Klid musí vydržet souvisle 2 s, první okamžik bez práce nestačí.** Z pushe TDLib vydá
  dočasnou notifikaci hned, o pár set ms později se připojí, dočasnou zruší a skutečnou si
  odloží. Služba skončila přesně v té mezeře a na liště nezůstalo nic.
- **TDLib notifikace ODKLÁDÁ, i o 30 s.** `notification_default_delay_ms` = 1,5 s, a když byl
  účet do `online_cloud_timeout_ms` (5 min) online na jakémkoli zařízení (i v Plume),
  `notification_cloud_delay_ms` = 30 s — přečtení jinde notifikaci zruší. Hodnoty posílá server
  (přečteno `getOption`). Do okna služby pushe se to nevejde, proto se na odloženou notifikaci
  čeká v expedited úloze (`NotificationDeliveryWorker`, WorkManager, strop 60 s); dokud běží,
  proces zmrazený není. Expedited jen od Androidu 12 — starší WorkManager by pro ni chtěl
  foreground službu s vlastní notifikací. **Vyvolat se to dá** otevřením oficiálního Telegramu
  na chvíli (účet je online jinde), pak zmrazit Plume a poslat zprávu botem; když je naopak
  nedávno online Plume, odkládá už server a push dorazí až po ~30 s.
- **Z pushe TDLib vydává `NotificationTypeNewPushMessage`, ne `NewMessage`.** Obsah je
  rozšifrovaný přímo z pushe, protože zprávu samotnou TDLib bez spojení nemá. Dokud se
  zobrazoval jen `NewMessage`, push se zpracoval a notifikace se tiše zahodila — to byl hlavní
  důvod, proč z pozadí „prakticky nechodily". Text skládá `PushMessageContent.previewText()`.
- **Náhrada dočasné notifikace skutečnou se zobrazuje POTICHU.** TDLib dočasnou odebere a přidá
  novou k téže zprávě; pro systém je to nová zpráva a telefon upozornil dvakrát. Presenter
  si proto pamatuje nedávno odebrané řádky (minutu) a přidání téže zprávy je tiché. **Porovnává
  se id zprávy, ne text** — push u fotky předřazuje popisku `🖼`, takže texty dočasné
  a skutečné notifikace nesedí a náhrada se nepoznala. **Prázdná notifikace se proto ruší až po 1 s**: odebrání a přidání náhrady chodí
  ~5 ms po sobě a okamžité zrušení smazalo notifikaci dřív, než systém stihl upozornit — s tichou
  náhradou pak telefon neupozornil vůbec. Takhle náhrada stávající notifikaci jen přepíše.
  Upozornění se počítají v event logu, jde to i s vypnutým zvukem:
  `adb logcat -b events | grep notification_alert` (druhé číslo = vibrace, třetí = zvuk).
- **Fotka ze zprávy se v notifikaci ukazuje** (`MessagingStyle.Message.setData`), varianta do
  800 px, stahuje se se stropem 3 s. Ne při vypnutém náhledu, u spoileru, tajné ani mizející
  fotky. Z pushe se většinou stáhnout nestihne (bez spojení) — obrázek pak přinese skutečná
  notifikace, která dočasnou potichu nahradí.
- **Notifikaci kreslí jiný proces, cesta k souboru mu nestačí.** Obrázek jde přes `FileProvider`
  (`${applicationId}.notificationimages`, `res/xml/notification_images.xml`) jako zmenšená kopie
  v `cache/notification_images`, ne soubor TDLib — provider tak nevydává adresář TDLib a kopie
  nezmizí s úklidem TDLib. System UI dostane čtení automaticky od `NotificationManager`;
  **čtečky notifikací ne** — Wear companion hlásil `Permission Denial` a hodinky obrázek
  vynechaly, proto `grantUriPermission` všem `getEnabledListenerPackages`.
- **Popisek fotky je samostatný řádek pod obrázkem.** U řádku s obrázkem Android text nezobrazí,
  takže bez toho se popisek z notifikace ztratil. Sbalená notifikace pak ukazuje popisek
  a obrázek jako náhled vpravo.
- **Profilovka v notifikaci má strop 2 s.** Stahování z pozadí čeká na spojení; notifikace bez
  fotky je lepší než notifikace, která nestihne okno pushe.
- **Snížená priorita pushe se loguje** (`priority != originalPriority`). FCM ji snižuje aplikacím,
  které po vysoké prioritě neukazují notifikaci; v dozu pak push čeká na údržbové okno.
- **Obecná záchranná notifikace („Nová zpráva") při neúspěchu NENÍ dobrý nápad.** Telegram
  pushem posílá i to, co se zobrazit nemá (přečtení jinde, smazání zprávy) a obsah je zašifrovaný,
  takže klient bez TDLib nepozná, který push je který.
- **Presenter se startuje v `startKoin`, ne definicí v modulu.** `single { get<NotificationPresenter>()… }`
  si odvodí vlastní typ a resolvuje sám sebe → `StackOverflowError`.
- **Od Androidu 13 je `POST_NOTIFICATIONS` běhové oprávnění.** Bez něj `notify` tiše nic neudělá,
  takže se kontroluje před každým zobrazením.
- **Nejdřív vlastní vysvětlení, teprve pak systémový dialog.** Android dá na systémový dialog jen
  dva pokusy a pak ho zablokuje natrvalo; vlastní obrazovka (`NotificationPermissionPrompt`) žádný
  pokus nespotřebuje. Ptá se až v přihlášené části — nad přihlášením uživatel netuší, co appka dělá.
- **Musí existovat cesta zpět.** Po zablokování dialogu jde oprávnění zapnout jen v systému, takže
  Nastavení mají řádek, který ho otevře (`openSystemSettings`). Stav se čte znovu přes
  `LifecycleResumeEffect`, jinak by po návratu zůstala viset stará hodnota.
- **Na vynulování příznaku „už jsme se ptali" nepoužívej `pm clear`** — smaže i TDLib databázi
  a odhlásí účet. Stačí:
  `adb shell run-as eu.tintera.plume rm /data/data/eu.tintera.plume/shared_prefs/notification_permission.xml`
- **TDLib se startuje v `startKoin`, ne v `AuthViewModel`.** Když push probudí mrtvý proces, žádné
  UI se neskládá — poběží jen `Application.onCreate` a `FirebaseMessagingService`. Kdyby
  `setTdlibParameters` viselo na vzniku ViewModelu, zůstal by TDLib ve `WaitTdlibParameters`
  a `processPushNotification` by nemělo co rozšifrovat: push by dorazil a tiše nic neudělal.
- **Telegram chce service account JSON (FCM v1)**, ne legacy server key — ověřeno nahráním na
  my.telegram.org. Dokumentace na core.telegram.org pořád mluví o „application key (GCM)", což je
  zastaralé; legacy endpoint Google vypnul v červnu 2024.
- **Jeden chat = JEDNA notifikace, se zprávami jako řádky v `MessagingStyle`.** `setGroup` udělá
  z notifikací hromádku, kterou musí uživatel rozbalit — pořád jsou to dvě oznámení, ne jedno.
  Tudy vede i Android: u chatů je `MessagingStyle` očekávaný vzor.
- **Id systémové notifikace je `notificationGroupId`, ne `notificationId`.** Ten je od TDLib jeden
  na chat, takže nová zpráva stávající notifikaci PŘEPÍŠE. `notificationId` se drží u jednotlivých
  řádků, protože podle nich TDLib ruší (`removedNotificationIds`); bez nich by se po přečtení jedné
  zprávy musela zahodit celá notifikace.
- **Po restartu procesu se řádky přebírají z lišty** (`extractMessagingStyleFromNotification`).
  Není to okrajový případ: push probouzí MRTVÝ proces, takže bez toho by třetí zpráva nahradila
  notifikaci se dvěma předchozími. Převzaté řádky nemají `notificationId` — TDLib ho do notifikace
  nepředává — takže se jednotlivě zrušit nedají a odejdou až s celou notifikací.
- **Profilovka se do notifikace předává jako CESTA, ne `fileId`.** Notifikace se skládá naráz
  a ikonu na ni nejde doplnit dodatečně, takže se soubor musí vyřídit dřív, než se hodnota dostane
  do `ChatNotification`. V repository se proto stahuje `synchronous = true` — vědomě naopak než
  u obrázků v UI, kde je asynchronní stahování žádoucí, aby bylo co ukázat mezitím. Profilovka má
  pár kilobajtů a většinou už na disku je, protože ji kreslí seznam chatů.
- **Ve skupině patří k řádku profilovka ODESÍLATELE**, ne fotka skupiny; fotka chatu je záloha pro
  kanály a boty, kde odesílatel vlastní profilovku nemá. Jméno i fotka se berou z jednoho dotazu
  na uživatele.
- **Velký kruh v notifikaci bere Android ze ZKRATKY, ne ze `setLargeIcon`.** U `MessagingStyle`
  se `setLargeIcon` ignoruje: bez `ShortcutInfo` + `setShortcutId` notifikaci nepovažuje za
  konverzaci a do kruhu dosadí ikonu aplikace. Takhle vznikly dvě ikony naráz — panáček nahoře
  a profilovka u řádku. Zkratka MUSÍ být dlouhověká (`setLongLived`), jinak ji systém po čase
  zahodí a kruh se vrátí k ikoně aplikace; její intent musí mít nastavenou akci, jinak
  `pushDynamicShortcut` vyhodí výjimku. Vedlejší efekt je nabídka po podržení ikony aplikace —
  to je záměr, přesně tak to dělá Telegram i Messages.
- **Profilovka u řádku patří jen do skupiny.** V soukromém chatu je to tatáž fotka, kterou nese
  ikona konverzace, takže by v notifikaci byla dvakrát nad sebou.
- **Velká ikona notifikace je fotka CHATU, ne odesílatele.** Je to ten kruh vlevo; bez něj tam
  Android dá ikonu aplikace, takže u všech chatů svítí totéž a nepoznají se od sebe. U řádku
  naopak patří profilovka toho, kdo psal — ve skupině to jsou dvě různé fotky.
- **`IconCompat.createWithAdaptiveBitmap`** kvůli kulatému ořezu — Android profilovky v notifikacích
  kreslí v kruhu a čtvercová fotka by z řady vyčnívala. Dekódované ikony se cachují podle cesty,
  protože se notifikace překresluje při každé nové zprávě.
- **Skupina se pozná podle jména odesílatele.** TDLib ho vyplňuje jen tam, kde není totožné
  s chatem. V soukromém chatu se proto název chatu nedává do hlavičky konverzace — byl by to
  tentýž řetězec jako jméno u každého řádku.
- **Neznámá skupina při rušení = notifikace z minulého běhu procesu.** Řádky nemáme s čím spárovat,
  takže jde pryč celá; TDLib stejně ruší po přečtení celý chat naráz.
- **Proklik do chatu jde přes `ChatOpenRequests`, a je to STAV, ne událost.** V okamžiku ťuknutí
  nemusí být komu doručit: proces mohl být mrtvý a UI se teprve skládá, nebo aplikace visí na
  přihlášení a `AppShell` vůbec neexistuje. Událost by se v obou případech zahodila a notifikace
  by otevřela jen seznam. Požadavek si proto počká, než ho má kdo vyřídit.
- **Z launcher intentu se bere JEN komponenta.** Hotový `getLaunchIntentForPackage` nese
  `ACTION_MAIN` + `CATEGORY_LAUNCHER` a systém ho vyhodnotí jako „spuštění z plochy": běžící úlohu
  vytáhne dopředu s PŮVODNÍM intentem a `onNewIntent` s chatId nikdy nedorazí.
- **Request code PendingIntentu musí být jedinečný, a bere se `notificationId`.** PendingIntenty
  se porovnávají podle request code a `filterEquals` — extras do rovnosti NEVSTUPUJÍ, takže se
  společným kódem by všechny notifikace otevřely ten chat, který se zaregistroval první.
  `chatId.hashCode()` by nestačil: Long se do Intu nevejde bez ztráty a kolize dvou chatů by
  tiše otevřela ten cizí.
- **`MainActivity` je `singleTask` a v `onNewIntent` volá `setIntent`.** Bez toho by se
  activity skládala znovu a ztratila stav obrazovek; bez `setIntent` by `getIntent()` dál vracelo
  ten intent, kterým se activity zakládala. (Dřív `singleTop` — ten ale nestačí na sdílení, viz
  „Sdílení do aplikace".)
- **Extra se po vyřízení z intentu maže.** Po otočení displeje se `onCreate` spustí znovu nad týmž
  intentem a chat by se otevřel podruhé, i kdyby se uživatel mezitím proklikal jinam.
- **`MessagingStyle` sám nestačí, notifikace potřebuje i kategorii a ticker.** Bez
  `CATEGORY_MESSAGE` na ni nedosáhne výjimka „zprávy" v režimu Nerušit a aplikace, které
  notifikace třídí (Light Flow, hodinky), ji nemusí poznat jako zprávu. Titulek a text si systém
  z `MessagingStyle` doplní sám, ticker ne — a starší čtenáři notifikací berou text odsud.
  Porovnání s oficiálním klientem: `adb shell dumpsys notification --noredact`.
- **Ikona notifikace je pírko** (`ic_notification`), vygenerované `play/tools/icon.py` jako
  ikona aplikace. Leží v `:client:shared` (`androidMain/res`, třída `eu.tintera.plume.client.R`),
  ne v `androidApp` — kód notifikací je v knihovně a na prostředky aplikace nedosáhne. Osten
  je silnější než u ikony aplikace: při 24 dp by původní šířka s vlajkou splynula.
- **Odznak v rohu avataru konverzační notifikace je IKONA APLIKACE, ne malá ikona**, a System
  UI si ji drží v paměti. Po změně ikony tam zůstala ta stará i po přeinstalování a se změněným
  `versionCode`; `am force-stop com.android.systemui` System UI nerestartuje (ověřit `ps`).
  Pomohl až restart emulátoru.
- **`adb shell am force-stop` znemožní doručení FCM.** Aplikace se dostane do „stopped state"
  a zprávy jí systém nedoručí, dokud ji uživatel ručně nespustí. Push se tak testovat nedá —
  aplikaci je potřeba jen odswipovat z přehledu úloh.
- **Běžící emulátor stahuje notifikace z telefonu.** Je to plnohodnotné druhé přihlášené
  zařízení: s otevřenou konverzací hlásí zprávy jako přečtené (`viewMessages(forceRead = true)`
  podle viditelných položek) a TDLib v telefonu notifikaci vzápětí stáhne
  (`removedNotificationIds`) — na liště blikne a zmizí. Vypadá to jako chyba notifikací, a není.
- **Emulátor NEVÍ, že se na něj nedíváš.** Ztrátu fokusu hostitelského okna nepředává: změřeno
  na emulátoru vloženém v Android Studiu, s frontmost aplikací na Macu mimo něj (Android Studio,
  Finder i jiná) hlásí `dumpsys window` pokaždé `mCurrentFocus=eu.tintera.plume/MainActivity`
  a Plume zůstává `ResumedActivity`. Podmínka na `isWindowFocused` u hlášení přečtení by tenhle
  případ tedy NEVYŘEŠILA. Zabere až zhasnutý displej (`adb shell input keyevent 26` →
  `mCurrentFocus=null`, `visibleRequested=false`); na skutečném telefonu to zhasne samo, proto
  je to past jen emulátorová. Před testováním notifikací na telefonu tedy emulátor zhasnout,
  odejít v něm z konverzace, nebo ho odhlásit.

### Ohlášení příchozího hovoru

Hovory Plume přijmout neumí (viz `docs/roadmap.md`, „Hovory"), ale příchozí hovor ohlásí
notifikací: kdo volá, s videem nebo bez, „Přijmout jde zatím jen v oficiální aplikaci Telegramu"
a akce „Odmítnout" (`NotificationUpdate.Call`, `IncomingCall`, `showCall`). Dřív hovor propadl
bez stopy a kdo měl jen Plume, dozvěděl se o něm až ze zmeškaného hovoru.

- **Jde přes notifikace TDLib, ne přes vlastní odběr `updateCall`.** TDLib k příchozímu hovoru
  vydá `NotificationTypeNewCall` ve skupině `NotificationGroupTypeCalls` a sám ji zruší, když
  hovor skončí (přijatý jinde, odmítnutý, zmeškaný) — `Removed` téže skupiny, kterou presenter
  zruší celou, protože ji v paměti nemá. Z pushe by to mělo jít toutéž cestou jako zprávy
  (ověřené NENÍ). `updateCall` se odebírá jen kvůli videu (notifikace nese jen id hovoru).
- **Vlastní notifikace v kanálu `calls`, ne řádek konverzace** — „Odpovědět" ani „Přečteno"
  k hovoru nepatří. Kanál zvlášť, aby si ho šlo vypnout samotný.
- **`CallStyle` ne.** Od Androidu 12 chce službu v popředí, nebo celoobrazovkový intent, a
  přijmout hovor stejně nejde. Kategorie je `CALL`, notifikace `ongoing` (dokud zvoní, nemá jít
  smáznout omylem) a po 90 s zmizí i sama (`setTimeoutAfter`), kdyby se zrušení ztratilo.
- **Odmítnutí je `discardCall`** (`NotificationActions.declineCall`), notifikaci pak stáhne
  TDLib. Volající pozná hned, že hovor nikdo nepřijme.
- **Ověřit to jde jen sondou**: bot volat neumí a na vlastní účet zavolat nejde. Sonda v
  `groupUpdate` vyrobí z notifikace zprávy `#hovor` od testovacího bota `NotificationUpdate.Call`
  se stejnou skupinou — přečtení zprávy pak ověří i rušení. Necommituje se. **Pozor:** aplikace
  na pozadí na emulátoru notifikaci nedostala (zmrazený proces, push nepřišel); ověřovat
  s aplikací v popředí v jiném chatu (Uložené zprávy). Ověřeno: vzhled, „Odmítnout" dojde
  k `discardCall`, přečtení notifikaci stáhne. **Skutečný hovor (a hovor z pushe) ověřený NENÍ.**

### Pruh videochatu

Probíhající nebo naplánovaný videochat skupiny (v kanálu živé vysílání) má nad konverzací pruh
(`VideoChatBar`, `ChatRepository.videoChat`, `VideoChatInfo`): „Probíhá videochat · název ·
12 účastníků", „Videochat začne zítra 18:00". Připojit se Plume neumí; ťuknutí řekne, že to
jde zatím jen v oficiální aplikaci. Bez pruhu by kdo má jen Plume o videochatu nevěděl.

- **Chat říká ŽE, hovor KOLIK.** `Chat.videoChat.groupCallId` (živě `UpdateChatVideoChat`,
  nula = žádný) přepíná odběr hovoru (`flatMapLatest`); počet účastníků, název a naplánovaný
  začátek nese `GroupCall` (`getGroupCall`, živě `UpdateGroupCall`).
- **Pruh naskočí hned, i bez načteného hovoru** (počet pak chybí) — `getGroupCall` jde na server
  a pruh, který by se objevil až po něm, by pod hlavičkou poskočil. Skončený hovor
  (`isActive = false` a nic naplánovaného) pruh schová, i kdyby update chatu ještě nedorazil.
- **Tvar a sklo jako pruh připnutých**, mezi pruhem proti spamu a připnutými; měří se s nimi,
  seznam se odsazuje o výšku všech. Odznak je kamera v TERCIÁRNÍM kruhu (terciární role nese
  v aplikaci „živé" věci), takže se od špendlíku připnutých liší i barvou. Ve vlákně komentářů
  pruh není — videochat patří diskusní skupině.
- **Ověřuje se ve vlastní testovací skupině sondou**: bot videochat založit neumí. Sonda v
  `videoChat` (necommituje se) zavolala `createVideoChat` se začátkem za den, po 20 s
  `startScheduledVideoChat` a po dalších 25 s `endGroupCall`. Ověřeno na emulátoru: naplánovaný
  pruh, přechod na probíhající živě (s počtem), zmizení po ukončení, hláška po ťuknutí. Ve
  skupině po tom zůstanou tři servisní zprávy. **Kanál s živým vysíláním a hovor s účastníky
  ověřené NEJSOU.**

### Kanály a nastavení notifikací

Kanály jsou čtyři (`NotificationChannelKind`, čistá funkce `notificationChannelFor`, testy
`NotificationChannelTest`): Soukromé chaty, Skupiny, Kanály — jedna k jedné s rozsahy nastavení
TDLib — a Zmínky a odpovědi. Sekce Notifikace v Nastavení (`NotificationSettings`,
`NotificationSettingsRepository`) drží u každého rozsahu volby TDLib a odkaz „Zvuk a vibrace" do
JEHO kanálu (`ACTION_CHANNEL_NOTIFICATION_SETTINGS`).

- **TDLib o kanálech neví nic**, rozhoduje jen, jestli se notifikace ukáže. Kanál vybírá aplikace
  podle druhu chatu z `notificationSettingsChatId` — tedy chatu, podle jehož nastavení se TDLib
  řídila —, zmínky podle `NotificationGroupTypeMentions`. Zmínky mají přednost: ve ztišené skupině
  jsou to jediné, co má zvonit.
- **Zmínky a běžné zprávy téhož chatu jsou v TDLib dvě skupiny notifikací**, tedy dvě systémové
  notifikace s různým `groupId`. Kanál proto patří konverzaci presenteru, ne chatu.
- **Kanál je po založení uživatelův.** `createNotificationChannel` u existujícího id přepíše jen
  název a popis — proto se volá při každém startu (překlad po změně jazyka) a nic tím nerozbije.
  **Id kanálu se nesmí měnit** (`channelId`): nové id je pro systém nový kanál a nastavení
  starého zůstane u něj. Zvuk z Telegramu (`notificationSoundId`) se proto nesynchronizuje.
- **Kanály telegramových kanálů mají `IMPORTANCE_DEFAULT`**, ostatní `HIGH` — příspěvků chodí
  hodně a málokdy se na ně reaguje hned. Rozhoduje se jen při založení.
- **Starý kanál `messages` se maže** (`deleteNotificationChannel`), jinak by v systému visel
  prázdný. Co si u něj uživatel nastavil, se nepřenese — kanál přenést nejde.
- **Rozsah se zapisuje celým objektem** (`setScopeNotificationSettings`), stejně jako ztlumení
  chatu: zvuk a stories se přenesou z aktuálního stavu, jinak by se přepsalo nastavení z jiného
  klienta. Hodnoty jsou živé přes `UpdateScopeNotificationSettings` (odběr před prvním čtením),
  takže přepínač čeká na potvrzení z TDLib — přijde lokálně hned.
- **„Upozornění vypnutá" = `muteFor` > 0**, zapnutá = 0. Dočasně ztlumený rozsah se tedy ukáže
  jako vypnutý; vypnutí zapisuje `Int.MAX_VALUE` jako ztlumení chatu navždy.
- **Náhled se bez upozornění nedá přepnout, zmínky a připnuté ano.** Zmínky projdou i ztlumeným
  chatem — přesně proto existují. Přepínač zmínek je jen u skupin: v soukromém chatu je každá
  zpráva „pro mě", v kanálu píše jen kanál.
- **Výjimky** (`getChatNotificationSettingsExceptions`, `compareSound = false`) jsou jediné místo,
  kde je vidět, co kde je ztlumené. „Obnovit" zapne všechna `useDefault*` a seznam se čte znovu —
  co zbylo, ví jen server. Chat s vlastní volbou jinou než ztlumení je „Vlastní nastavení".
- **„Kontakt se připojil" je volba s ZÁPORNÝM názvem** (`disable_contact_registered_notifications`);
  nenastavená přijde jako `OptionValueEmpty`, tedy „upozorňovat".
- **Oprávnění k notifikacím je v sekci Notifikace, ne v Obecných** — bez něj ostatní volby nic
  nezmění, proto nahoře.
- **Upozornění jednoho chatu jsou v profilu**: řádek „Upozornění" v bloku Informace se stavem
  („Ztlumeno do 14:30 · výchozí nastavení") otevře sheet s dobou ztlumení, náhledem, připnutými,
  zmínkami (jen skupina), zvukem konverzace a obnovením (`ChatNotificationSheet`). Dlaždice
  Ztlumit nad ním zůstává rychlým přepínačem. Hodnoty jsou ÚČINNÉ (`chatSettings`): kde chat jede
  podle rozsahu, ukazuje se hodnota rozsahu — a proto se čte znovu i na změnu rozsahu. Změna
  jedné volby zapíše `useDefault… = false` jen u ní, zbytek se přenese.
- **`muteFor` je ZBÝVAJÍCÍ čas v sekundách**, ne okamžik konce; konec ztlumení se dopočítá při
  čtení (`muteState`, testy `MuteStateTest`). Nad rok = navždy — my zapisujeme `Int.MAX_VALUE`,
  jiní klienti roky. `asDayLabel` je dělaný na minulost a zítřek vydával za „Dnes"; budoucí čas
  má `asUpcomingTime`.
- **Zvuk jednoho chatu = kanál konverzace** (`ensureConversationChannel`, Android 11+). Systém si
  ho zakládá sám, až když uživatel u notifikace zvolí „Prioritní"; dřív `EXTRA_CONVERSATION_ID`
  otevře jen nadřazený kanál. Aplikace ho proto založí sama (`setConversationId(parent,
  shortcutId)`, nastavení zkopírované z nadřazeného) a notifikace se zkratkou konverzace do něj
  systém směruje sám — v `dumpsys notification` je vidět jako `effectiveNotificationChannel`,
  zatímco `channel=` dál ukazuje nadřazený. **Past: `getNotificationChannel(parent,
  conversationId)` bez kanálu konverzace vrátí NADŘAZENÝ kanál, ne `null`** — rozhoduje až
  `conversationId` výsledku. Kanály konverzací nesou názvy chatů, takže se po odhlášení mažou.
- **Systémové nastavení se otevírá ve vlastní úloze** (`FLAG_ACTIVITY_NEW_TASK`): zpět z něj
  nevede do Plume, ale dál po nastavení systému. Při ověřování se do aplikace vrací `am start`.
- **Ověření:** `adb shell dumpsys notification --noredact | grep "pkg=eu.tintera.plume"` ukáže
  `channel=` u notifikace; kanály aplikace jsou v `dumpsys notification` pod
  `AppSettings: eu.tintera.plume` (smazaný má `mDeleted=true`). Soukromá zpráva od bota přišla
  kanálem `private`, odkaz „Zvuk a vibrace" otevřel systémové nastavení správného kanálu, zápis
  rozsahu i obnovení výjimky se projevily hned (emulátor). **Skupiny, zmínky i kanály ověřené
  na emulátoru testovacím botem** v testovací skupině a kanálu (bot jako člen skupiny a správce
  kanálu): zpráva ve skupině kanálem `groups`, `text_mention` účtu kanálem `mentions` jako
  samostatná notifikace téhož chatu, příspěvek kanálu kanálem `channels`. Do skutečných skupin
  se testovací zprávy neposílají — test jde jen tam, kde je bot.

### Akce z notifikace

Notifikace chatu má akce „Odpovědět" s polem na text (`RemoteInput`), „Označit jako přečtené"
a „Ztlumit na hodinu".
Odpověď odejde, aniž by se aplikace otevřela; po potvrzení se chat přečte a TDLib notifikaci
stáhne. Všechny akce jdou přes jednoho příjemce a jednu třídu (`NotificationActionReceiver`,
`NotificationActions`). **Víc než tři akce být nesmí** — Android víc neukáže.

- **„Označit jako přečtené" čte PO nejnovější zprávu NOTIFIKACE, ne chatu.** Co mezitím přišlo,
  uživatel neviděl. Id jde v extras akce; u notifikace z minulého běhu procesu (řádky bez id)
  se čte po nejnovější zprávu v chatu. Nabízí se i v kanálu — číst jde všude.
- **Po přečtení se notifikace neruší ručně**, stáhne ji TDLib (`removedNotificationIds`), i po
  probuzení mrtvého procesu. Na potvrzení se nečeká: přečtení drží TDLib v databázi a pošle ho,
  až bude spojení. Proto přečtení nemá obdobu `ReplyDeliveryWorker`.
- **Ztlumení je na HODINU, ne natrvalo** (`NotificationActions.MUTE_DURATION`, `muteFor`).
  Z notifikace se ztlumuje skupina, která se zrovna rozjela; natrvalo jde ztlumit v aplikaci
  a z notifikace by se na to snadno zapomnělo. Délku musí nést popisek akce.
- **„Ztlumit" jen u běžných zpráv** (`ChatNotification.canMute` =
  `NotificationGroupTypeMessages`). Zmínky a odpovědi chodí i ze ztlumeného chatu, a ztlumení
  na hodinu by mu po hodině zvuk VRÁTILO — i když ho měl uživatel ztlumený natrvalo.
- **Ztlumený chat TDLib z notifikací stáhne sám**, ve stejné milisekundě jako potvrzení
  `setChatNotificationSettings` (ověřeno logem, i po probuzení mrtvého procesu). Ruční zavírání
  notifikace po ztlumení (první verze) bylo zbytečné.
- **iOS: akce nese KATEGORIE, ne notifikace** (`IosNotificationCenter`, smlouva
  `NotificationCategories` v commonMain) — kombinace „smí odpovědět / smí ztlumit" jsou čtyři
  předem zaregistrované kategorie. Kategorii a klíče `plume_*` vyplní rozšíření notifikací, viz
  „Notifikace na iOS (APNs)".
- **iOS: delegát `UNUserNotificationCenter` se nastavuje v `initKoin`** (z `App.init`). Akce umí
  spustit ukončenou aplikaci a odpověď dostane jen delegát, který existuje dřív, než aplikace
  doběhne start. Drží se silnou referencí — centrum ho má jen slabě.
- **iOS: `suspend` v argumentech ObjC volání shodí aplikaci.** Kategorie měly popisky
  `title = getString(…)` přímo v `actionWithIdentifier(…)`: Kotlin/Native převedl už vyhodnocené
  argumenty na NSString, korutina se uspala, pokračovala jinde a autorelease pool je uvolnil —
  pád na `objc_retain` hned při startu. Test bez suspendu v argumentech prošel, takže se to
  pozná jen v aplikaci. **Hodnoty ze `suspend` funkcí se načtou do proměnných PŘED voláním ObjC.**
- **iOS: přečtení a ztlumení potřebují chvíli PO potvrzení** (`SYNC_GRACE`, 3 s). TDLib je
  potvrdí lokálně hned a na server pošle až potom; po completion handleru iOS proces uspí,
  a když ho pak ukončí, chat zůstane nepřečtený. Ověřeno: studený start, akce, iOS proces sám
  ukončil — bez pauzy nepřečteno, s ní přečteno. Stav spojení se na to použít nesmí (jen popisek).
- **iOS: `plume_message_id` 0 = id chybí.** Nula prošla jako id zprávy a TDLib odmítl
  `viewMessages` („Invalid message identifier").
- **Ověření na simulátoru bez APNs:** `xcrun simctl push <zařízení> eu.tintera.plume x.apns`
  s `aps.category` a klíči výš (aplikace potřebuje oprávnění k notifikacím — to se zatím
  nežádá, pro test ho dočasně vyžádat v `install`). Akce: centrum notifikací (tah od horní hrany),
  notifikace doleva → View. Podržení na simulátoru náhled NEotevře. Aplikace do unified logu
  nepíše; průběh akce se dá sledovat jen dočasným zápisem do souboru v `Documents`
  (`simctl get_app_container … data`). Ověřeno: všechny tři akce, přečtení i ze studeného startu.
- **Tři akce, jeden request code** (`groupId`) nevadí: akce intentu vstupuje do `filterEquals`,
  takže se `PendingIntent`y nepřepíšou.

- **Cesta:** akce → broadcast `NotificationActionReceiver` (vstupní bod, `goAsync`) →
  `NotificationActions` v `core/notifications` (odeslat, dočkat se, přečíst). Co se nevejde do
  okna příjemce (8 s z 10), dočká `ReplyDeliveryWorker`. Příjemce je ve vstupním bodu kvůli
  WorkManageru; `:client:shared` na něj míří akcí a balíčkem (`NotificationIntents.reply`), ne
  třídou — stejně jako na `MainActivity`.
- **`PendingIntent` akce musí být MUTABLE.** Systém do něj vkládá napsaný text; s IMMUTABLE by
  `RemoteInput.getResultsFromIntent` vrátil `null`.
- **Notifikace se po odpovědi MUSÍ překreslit**, jinak se v ní točí kolečko napořád. Překreslí se
  hned vlastním řádkem s odpovědí (`showReply`, osoba `null` = uživatel), ještě před odesláním.
- **Přečte se až PO potvrzení od serveru.** Přečtení notifikaci stáhne; kdyby přišlo hned
  a odeslání selhalo, nebylo by kde ukázat „Neodesláno: …" (`showReplyFailed`, i s textem, a se
  zvukem — selhání uživatel zaregistrovat musí). Přečte se zpráva nejnovější PŘED odesláním:
  po něm je nejnovější naše odpověď a její přečtení nic neoznačí.
- **Vlastní řádek notifikaci naživu nedrží.** TDLib ho nezná, takže ho neodebere; notifikace se
  ruší, když v ní zbydou jen vlastní řádky.
- **Potvrzení se zjišťuje dotazem na zprávu, ne updatem** (`NotificationActions.await`). Úloha
  začíná pozdě a `UpdateMessageSendSucceeded` mohl dávno proletět. Stav jde přečíst kdykoli:
  TDLib po potvrzení zprávu přečísluje, takže nenalezené dočasné id = odesláno.
- **Mrtvý proces:** akce ho probudí jako push, `startKoin` rozběhne TDLib a odpověď čeká na
  `AuthStage.Ready`. Notifikace z minulého běhu v paměti presenteru není — řádek se připíše
  přestavěním té z lišty (`NotificationCompat.Builder(context, notification)`).
- **Ve skupině odpověď cituje nejnovější zprávu z notifikace**, v soukromém chatu ne. Tak to
  dělá i oficiální klient (`WearReplyReceiver` posílá `max_id` jako `replyToMsg`): mezi více
  lidmi by jinak nebylo poznat, komu se odpovídá; ve dvou je citace šum. Id jde v extras akce
  a `FLAG_UPDATE_CURRENT` ho přepíše při každém překreslení.
- **Oficiální klient chat čte HNED, ne po potvrzení**, a ve vláknech (topics) vůbec — tam mu
  notifikace po odpovědi zůstane.
- **Kam se psát nesmí, akce není** (`ChatNotification.canReply`, totéž pravidlo `writeAccess` jako
  vstupní pole; `ownMemberStatus` je sdílené). Neznámý chat psaní neblokuje.
- **Bez sítě odeslání NESELŽE, TDLib čeká.** „Neodesláno" tedy znamená odmítnutí serverem,
  ne výpadek spojení; offline odpověď zůstane v notifikaci a úloha ji potvrdí, jakmile se síť
  vrátí (ověřeno letovým režimem: potvrzení v úloze za 16 s po zapnutí sítě).
- **Ověřit to jde jen s notifikací, kterou nepřečte jiné zařízení.** Telefon s otevřeným chatem
  s botem zprávu přečte hned a TDLib notifikaci stáhne dřív, než se ukáže. Tlačítko v liště
  najde `uiautomator dump` (text „Reply"), text se napíše `input text` a odešle Enterem.
- Ověřeno na emulátoru: živý proces (odesláno za 2 s), proces zabitý `am kill` (0,5 s),
  letový režim (úloha). Přečtení: živý proces i zabitý (proces spustil broadcast, TDLib `Ready`
  za 1 s, notifikace pryč).
- **`am kill` zabije jen proces v keši** — s rozbalenou lištou ho nezabil. Spolehlivě to jde
  u debug buildu přes `adb shell run-as eu.tintera.plume kill -9 <pid>`.
- **Druhé přihlášené zařízení dává na tlačítko jen pár vteřin**, pak notifikaci stáhne. Lištu
  proto rozbalit PŘEDEM, čekat na „Zobrazuji" v logu a tlačítko hledat `uiautomator dump`
  až potom — poloha tlačítek se mění s počtem řádků notifikace. Cesta „Neodesláno" ověřená není — server odpověď bota neodmítne.

### Notifikace na iOS (APNs)

Na iOS notifikace **zobrazuje systém přímo z APNs pushe od Telegramu**, ne aplikace. Zařízení se
registruje `registerDevice` s `deviceTokenApplePush` (`IosPush`); token vydá iOS do `AppDelegate`
(`iOSApp.swift`) a ten ho předá do Kotlinu.

- **Plain APNs od Telegramu NENÍ šifrovaný** — `deviceTokenApplePush` šifrování nemá (má ho jen
  FCM a APNs VoIP). Push nese `aps.alert.loc-key` + `loc-args` (typ zprávy, jména, text)
  a `custom` (`from_id` / `chat_id` / `channel_id`, `msg_id`, `mention`…). TDLib se v tom
  neúčastní: `processPushNotification` by potřebovalo TDLib v rozšíření, a to na databázi
  aplikace nedosáhne a limit paměti rozšíření by ji stejně nesnesl.
- **Větu skládá iOS z `Localizable.strings` aplikace.** Chybějící klíč ukáže doslova
  („MESSAGE_PHOTO"), takže tam musí být všechny. Soubory se generují
  (`python3 iosApp/tools/push_strings.py`): angličtina z `core.telegram.org/api/config.json`
  (`push`), čeština je ve skriptu; skript skončí chybou, když Telegram přidá klíč. Česky
  přítomným časem („Jana posílá fotku") — rod odesílatele push neprozradí.
- **Rozšíření notifikací (`iosApp/NotificationService`) text neskládá**, jen doplní kategorii
  s akcemi, vlákno podle chatu a klíče `plume_*`. Totéž pravidlo má Kotlin (`TelegramPush`),
  protože **jestli Telegram posílá `mutable-content`, nikde popsané není** — bez něj rozšíření
  neběží a aplikace čte holé `custom` (`pushInfo`). Ťuknutí otevře chat v obou případech; akce
  jsou jen s rozšířením.
- **Číslování:** chat uživatele = `from_id`, základní skupina = −`chat_id`, supergroup i kanál
  = −10¹² − `channel_id`; zpráva = `msg_id` posunuté o 20 bitů. Skupina se pozná podle
  `loc-key` s prefixem `CHAT_` (i supergroup — ta nese `channel_id`), kanál podle `CHANNEL_`.
- **`sandbox` musí sedět s prostředím tokenu** (`#if DEBUG` v `AppDelegate`): build z Xcode má
  token vývojového APNs, TestFlight a App Store produkčního. Při nesouladu APNs push tiše
  zahodí. Entitlement je `aps-environment = development`; při exportu pro App Store ho Xcode
  přepne sám.
- **Registrace u APNs se opakuje při každém startu** (když je oprávnění), token jde do TDLib až
  při `AuthStage.Ready` — jako `PushRegistrar` na Androidu.
- **Metody z kategorií ObjC jsou v Kotlinu rozšíření a potřebují vlastní import**:
  `UIApplication.registerForRemoteNotifications()` bez `import platform.UIKit.registerForRemoteNotifications`
  hlásí „Unresolved reference", přestože třída importovaná je.
- **Přečtení jinde push nezpracuje** (`READ_HISTORY` je servisní push, bez TDLib v rozšíření
  se nevyhodnotí). Proto `NotificationPresenter.ios` při běhu aplikace uklidí notifikace chatů,
  které TDLib hlásí jako přečtené, a srovná odznak (`setBadgeCount`, nepřečtené zprávy
  neztlumených chatů hlavního seznamu) — server ho nastavuje v pushi a po přečtení v aplikaci by
  zůstal starý. Servisní klíče se v popředí nezobrazují.
- **Vysvětlení před systémovým dotazem je společné** (`NotificationRationaleDialog`). iOS dá
  systémový dotaz jen jednou napořád; „Teď ne" se pamatuje v `NSUserDefaults`.
- **`simctl push` rozšíření NESPUSTÍ** — vloží notifikaci přímo (v logu `mutableContent: YES`
  a žádná služba). Na simulátoru se tím ověří šablony, ťuknutí (otevře chat jen z `custom`)
  a úklid přečtených chatů; rozšíření jen skutečný push přes APNs.
- **Co poslal Telegram, se dá přečíst**: rozšíření v DEBUG buildu ukládá poslední push do
  kontejneru App Group (`push/last.json`). U prvního skutečného pushe je to jediný způsob, jak
  ověřit tvar pushe (`mutable-content`, `custom`), o kterém dokumentace mlčí.
- **Co je potřeba mimo repo:** v Apple Developer účtu zapnout Push Notifications pro App ID
  aplikace (automatické podepisování s vyplněným `TEAM_ID` založí i App ID rozšíření
  `….notifications`), vyrobit certifikát / klíč APNs a nahrát ho Telegramu v nastavení
  aplikace na my.telegram.org (tam, kde je service account pro FCM).

## Sdílení do aplikace

Plume je v systémové nabídce „Sdílet" (Android): text, odkaz, jeden i víc souborů. Otevře se
výběr chatu (`ShareRoute`), po výběru chat a obsah čeká nad vstupním polem — nic se neposílá
samo, k souborům se píše popisek a uživatel vidí, co odejde.

- **`MainActivity` je `singleTask`, ne `singleTop`.** Výběr z nabídky „Sdílet" spouští activity
  v úloze VOLAJÍCÍ aplikace; se `singleTop` tak vznikla druhá instance Plume v úloze Fotek
  — dvě okna Plume v přehledu, každé s vlastní navigací. `singleTask` doručí sdílení do
  existující úlohy přes `onNewIntent` a zpět z kořene vrátí uživatele do aplikace, ze které
  sdílel. Ověřeno `dumpsys activity activities`: jedna úloha Plume před sdílením i po něm.
- **Soubory se kopírují HNED** (`ShareIntake`, stejná příprava jako výběr příloh — fotka se
  zmenší, z videa se přečte náhled). Oprávnění k `content://` URI je dočasné; až uživatel vybere
  chat, nemuselo by platit. Kopíruje se v `appScope`, ne v rozsahu aktivity.
- **Požadavek je STAV** (`ShareRequests`), jako `ChatOpenRequests`: sdílet jde i do aplikace,
  která neběží nebo visí na přihlášení. Výběr chatu se ukáže hned, soubory dobíhají
  (`attachments == null` = „Připravuji soubory…") a konverzace si obsah vyzvedne, až je hotový.
- **Fotky a videa jdou jako média, jen když jsou médii všechny.** Telegram fotky se soubory do
  alba nesloží; smíšené sdílení (fotka + PDF) odejde celé jako soubory, ať se nic neztratí.
- **Sdílený text rozepsaný koncept DOPLNÍ, nepřepíše**, a vkládá se až po obnovení konceptu
  z TDLib (`draftRestored`) — koncept jde jen do prázdného pole, takže sdílení před ním by ho
  vytlačilo.
- **Koncept a sdílený text přijdou do pole HNED PO SOBĚ, takže obrazovka nuluje jen to vložení,
  které provedla** (`consumeInputFill(fill)`, porovnává instanci). Bezpodmínečné vynulování
  smazalo sdílený text dřív, než ho obrazovka uviděla: pole zůstalo prázdné, ViewModel ho přitom
  vedl jako koncept a uložil do TDLib — a další sdílení ho připojilo podruhé. Vyvolat se to na
  emulátoru nepodařilo, oprava stojí na čtení kódu; ověřené je, že koncept + sdílený odkaz vyjdou
  jako dva řádky.
- **U sdílení TEXTU se `ClipData` nečte** (`ShareIntake.streams`). Chrome ke sdílenému odkazu
  přikládá do `ClipData` obrázek náhledu stránky (`text/uri-list`, favicon nebo `og:image`) jen
  kvůli náhledu v systémové nabídce. Dokud se `ClipData` bral jako záloha za `EXTRA_STREAM`, byl
  z odkazu „1 soubor": výběr chatu ho tak hlásil, Direct Share otevřel „Komu: …" s logem webu
  a odešla fotka s odkazem jako popiskem, bez náhledu odkazu. Skutečný soubor nese `EXTRA_STREAM`
  (systém ho do `ClipData` jen zrcadlí) a typ intentu pak není `text/*`. **`adb shell am start`
  `ClipData` nastavit neumí**, takže sdílení ze skutečného Chrome se ověřuje v Chrome (menu →
  Share…); v logu je vidět `clip={text/uri-list … {U(content)}}` u `START … act=SEND`.
- **Odchod výběru bez volby chatu sdílení zahodí**, a to v `onDispose` obrazovky, ne na šipce.
  Systémové gesto zpět jde přes `NavDisplay`, ne přes obrazovku; se zahazováním jen na šipce
  zůstalo po gestu sdílení viset a výběr by se po obnově aktivity otevřel znovu. `abandon()`
  nezahazuje obsah s vybraným chatem — i po výběru výběr odchází (nahradí ho konverzace).
- **Opakované sdílení** (nové sdílení, zatímco výběr chatu nebo obrazovka „Komu: …" ještě
  visí) dřív obrazovku zavřelo a nové sdílení se neukázalo vůbec. Příčiny byly tři a každá
  stačila sama:
  - **Mezistav bez chatu.** Obsah vznikal `start` a chat dostával až dalším voláním
    (`confirm` / `choose`). Otevřená obrazovka „Komu: …" mezitím viděla obsah bez chatu, vzala
    ho jako „obsah zmizel" a odešla přes `moveTaskToBack`. **Cíl se proto zakládá spolu
    s obsahem** (`ShareRequests.start(target = ShareTarget(…))`) — na Androidu i v iOS rozšíření.
  - **Odchod zahazoval cizí sdílení.** Kvůli novému sdílení shell starou obrazovku nahrazuje
    (`popToRoot`), a její `onDispose` volal `abandon()` nad AKTUÁLNÍM obsahem — tedy nad tím
    novým. **`abandon(id)` zahazuje jen sdílení, které obrazovka ukazovala**; výběr chatu si
    pamatuje poslední sdílení bez chatu, „Komu: …" to svoje (`ShareToChatState.contentId`).
  - **Nové sdílení do TÉHOŽ chatu se nepropsalo.** Shell obrazovku nechává (tatáž routa)
    a ViewModel přebíral přílohy jen jednou (`if (attachments == null)`). Teď se s novým `id`
    přenastaví celý stav a obsah obrazovky se skládá znovu (`key(state.contentId)`), jinak by
    staré snímky z carouselu „odcházely" animací a galerie držela neplatné zaškrtnutí.
  - Obrazovka „Komu: …" proto odchází sama (`gone`) už **jen když obsah zmizel úplně**; sdílení
    jinam ji nechá být, nahradí ji shell.
  - **Ověřeno na emulátoru** dvojicí `am start SEND` po sobě: tentýž chat (obrazovka zůstane,
    ukáže nový snímek), jiný chat (Uložené zprávy), sdílení bez chatu nad „Komu: …" (výběr
    chatu s novým obsahem), Direct Share nad výběrem chatu; pak odeslání a ✕ s návratem.
  - **Na iOS simulátoru** sdílení po sobě do Notifikátora, Uložených zpráv a znovu Notifikátora
    (pokaždé rovnou správný chat s novou fotkou a popiskem), ze studeného startu a odeslání.
    **Ověřuje se zápisem do inboxu App Group, ne ťukáním v rozšíření**: složka
    `share/inbox/<čas>-<uuid>/` s obrázkem a `manifest.json` (`{"chatId", "text", "items":
    [{"file", "kind": "image"}]}`) zapsaným POSLEDNÍ, pak přepnout do jiné aplikace a Plume
    spustit (`simctl launch`). Inbox se čte při návratu do popředí — `simctl openurl` nad běžící
    aplikací ukáže jen systémový dialog a do pozadí ji nepošle, takže se nic nestane.
  - **Seznam chatů v rozšíření se po otevření přeskládá** (dočte naposledy použité). Ťuknutí
    podle souřadnic ze snímku tak trefilo jiný chat — skutečného člověka; fotka naštěstí jen
    čekala nad polem. Proto zápis do inboxu, ne ťukání.
- **Uložené zprávy v hlavičce „Komu: …" mají vlastní název**, ne jméno účtu, pod kterým je
  vede TDLib — tatáž past jako u `chatTitle` jinde.
- **Výběr má ✕, ne šipku zpět, a ✕ i gesto zpět vrací do aplikace, ze které se sdílelo**
  (`rememberReturnToCaller` → `moveTaskToBack`, `closeShare` v `AppShell`). Šipka slibovala
  návrat do Plume, kde by sdílený obsah zmizel bez stopy — bez volby chatu nemá kam jít.
  `singleTask` drží Plume ve vlastní úloze, takže pod ní je právě aplikace, ze které se sdílelo.
  Výběr se napřed odebere ze zásobníku, aby příští otevření Plume nezačínalo ve výběru.
- **Otevřený výběr se při dalším sdílení NEvyměňuje** (`openShare`). Odebrat a vrátit by ho
  zlikvidovalo a jeho `onDispose` by zahodil právě to nové sdílení.
- **Direct Share (chaty přímo v nabídce „Sdílet") stojí na zkratkách konverzací**, týchž, které
  zakládají notifikace (`conversationShortcut`). Podmínky jsou tři a chybějící se neprojeví
  chybou, jen se nic neukáže: kategorie `SHARE_CATEGORY` u zkratky, `share-target` se stejnou
  kategorií v `res/xml/shortcuts.xml` a `meta-data android.app.shortcuts` na `MainActivity`.
- **Pořadí chatů si skládá systém** z toho, jak často a nedávno aplikace zkratku publikuje
  (`pushDynamicShortcut` se počítá jako použití). Proto se hlásí KAŽDÉ odeslání i přeposlání
  (`ShareTargets.reportUsed`, v rozsahu aplikace, název a profilovku si dohledá samo).
  Zkratky z dřívějška kategorii nemají a do nabídky se dostanou až při příští notifikaci nebo
  odeslání — ověřuje se `dumpsys shortcut` (`categories={…SHARE_TARGET}`).
- **Kontakt v náhledu screenshotu NENÍ systémový výběr, ale Android System Intelligence**
  (`com.google.android.as`, chytré akce screenshotu). Intent spouští on, s typem `*`/`*`,
  `image/png` jen v popisu `ClipData` a **bez `FLAG_GRANT_READ_URI_PERMISSION`** — ke snímku
  sám přístup nemá, takže nemá co předat. Plume pak dostane `SecurityException: … has no access
  to content://media/…` a obejít to nejde (oprávnění ke knihovně fotek kvůli tomu nechceme).
  Proto: typ se bere i z typu intentu a popisu `ClipData`, když ho `MediaProvider` bez přístupu
  nevydá (`declaredTypes`) — jinak by screenshot otevřel konverzaci místo „Komu: …" —, selhání
  na oprávnění se počítá zvlášť (`SharedContent.denied`) a obrazovka „Komu: …" místo obecné
  chyby radí vybrat snímek v galerii pod polem, kde je nejnovější (`AccessDenied`). Zjištěno
  z logu Foldu (`START … from uid … (com.google.android.as)`, `flg=0x10000000`). Na emulátoru
  se to navodí `am start -a SEND -t image/png --eu android.intent.extra.STREAM
  content://media/external/images/media/<id>` se zkratkou chatu — `adb` přístup taky nepředá.
- **`EXTRA_SUBJECT` je náhrada textu jen bez souborů.** Screenshot ho nese jako „Screenshot
  (datum)" a končil v popisku každého sdíleného snímku.
- **Sdílení přes zkratku nese `EXTRA_SHORTCUT_ID`** a výběr chatu přeskočí: fotka a video jdou
  na potvrzovací obrazovku (viz „Direct Share" níž), zbytek rovnou do konverzace
  (`ShareIntake`: `choose(chatId)` + `ChatOpenRequests`).
- **Chat bez fotky má ve zkratce iniciálu** (`initialIcon`), jinak ukazoval systém prázdné
  kolečko — ve sdílení i ve velkém kruhu notifikace. Barvy ze systémové palety Material You
  (`system_accent1_100/900`): zkratka se kreslí mimo aplikaci, téma aplikace tam není. Paleta je
  od Androidu 12, na starším zůstane ikona prázdná.
- **Seznam s hledáním je společný s přeposíláním i novou konverzací** (`ChatPicker` v `core/ui`,
  viz „Jeden výběr chatu"). Co se sdílí, jde do jeho slotu `header` — tentýž, ve kterém má
  přeposílání volby; `ChatPicker` sám žádný podtitul nezná.
- **Co se sdílí, je KARTA, ne řádek textu na pozadí** (`SharedContentCard`). Jako holý text to byl
  jediný prvek obrazovky, který nestál na ploše — a přitom je to to, podle čeho uživatel pozná,
  že posílá správnou věc. Role je `tertiaryContainer`: je to cizí vložený obsah, tedy totéž co
  citace nad vstupním polem nebo popis webu u náhledu odkazu. **Ikona říká druh** (sponka
  u souborů, řetěz u odkazu, stránka u textu), a dokud se soubory připravují, je místo ní
  indikátor — „Připravuji soubory…" je průběh, ne popis.

### Direct Share: obrazovka „Komu: …"

Sdílení fotky nebo videa na konkrétní chat ze systémové nabídky (Direct Share) neotevře rovnou
konverzaci, ale **potvrzovací obrazovku** (`ShareToChatRoute`, `ShareToChatScreen`): hlavička
„Komu: …" s tváří chatu, snímek velký uprostřed, popisek a odeslání. Vzor je Google Zprávy
sdílející z Fotek.

- **Jen fotky a videa.** Soubory, text a odkaz dál otevřou konverzaci a čekají nad polem
  (`ShareIntake`): u odkazu není co ukazovat a pole na popisek by k němu byl krok navíc.
- **Rozhoduje se z MIME typů, ne z připravených příloh** — příprava běží na pozadí a obrazovka
  se má ukázat hned. Dokud soubory nejsou hotové, je místo snímku „Připravuji soubory…".
- **Odesílá obrazovka, ne konverzace.** Sdílení je proto ve stavu `awaitingConfirmation`
  (`ShareRequests.confirm`) a `deliveries` ho vynechává — jinak by si ho konverzace vyzvedla
  nad vstupní pole a odešlo by dvakrát.
- **Po odeslání se otevře konverzace**, jako u přeposílání: je vidět, že zpráva dorazila.
  Odchod bez odeslání sdílení zahodí a vrátí do aplikace, ze které se sdílelo (tentýž
  `closeShare` jako u výběru chatu).
- **Past: `consume` zhasne `incoming`, takže vlastní úspěch vypadá jako „obsah zmizel".** Odběr,
  který z prázdného `incoming` dělá „zavřít", proto musí vynechat vlastní odesílání — jinak
  aplikace po odeslání skočí na plochu místo do chatu. Ověřeno na emulátoru: bez toho se to
  stalo pokaždé. Stav se navíc nastavuje PŘED `consume`.
- **Spoiler, mizení i „jako soubor" nabízí tentýž `AttachmentModeSheet`** jako nad vstupním
  polem — jedno místo, kde se o způsobu odeslání rozhoduje, a platí i tytéž meze
  (`selfDestructAllowed`: mizení jen u jediné fotky v soukromém chatu, v Uložených zprávách ne).
- **Snímky kreslí TENTÝŽ `MediaCarousel` jako prohlížeč médií**: sousedé vykukují u okrajů,
  rozostřené pozadí jede s tahem, karta se vepisuje přes `MediaPageLayout` mezi hlavičku a pole
  na popisek (carousel jde přes celou obrazovku, `reservedBottom` je vzdálenost horní hrany
  řádku s polem od spodku, takže s klávesnicí se karta zmenší). Obrazovka dodává jen obsah
  karty (snímek, ✕, volba režimu) a pozadí stránky. **Dřív tu byl vlastní pager přes celou
  šířku, vlastní rámeček karty a vlastní prolínané pozadí** — sdílel se jen styl rozostření,
  a jakmile se doladil prohlížeč (sousedé, strop šířky), vypadalo sdílení jako jiná aplikace.
- **Karta má rozměry SNÍMKU, ne plochy** — zaoblení i ✕ patří fotce. To teď zařizuje
  `MediaPageLayout` z poměru stran přílohy.
- **Past: `Avatar(backdrop = …)` s barvou mimo schéma.** `contentColorFor` zná jen role, u vlastní
  barvy vrátí `Unspecified` — a záložka Uložených zpráv se nakreslila černá na černém pozadí.
  Kde roli nezná, rozhoduje `LocalContentColor`.
- **Ťuknutí na snímek přepne na celou obrazovku, jako v prohlížeči médií**: hlavička, pole
  i galerie zmizí, fotka jde přiblížit (štípnutí, dvojité ťuknutí), video přehrát; zpátky vede
  ťuknutí, tah dolů nebo gesto zpět. **Stránky jsou tytéž jako v prohlížeči** —
  `ZoomableMediaPage` a obecná `VideoPage` (zdroj souboru, náhled a ovládání navíc jsou
  parametry) — jen nakrmené souborem na disku místo zprávy. Dokud byla gesta i přehrávání
  napevno spojené se zprávou, karta ve sdílení byla jen obrázek a zkontrolovat, co odchází,
  nešlo. Na celé obrazovce je spoiler odkrytý (je to volba odesílatele, ne cizí obsah). Tytéž
  stránky nese i náhled přílohy nad vstupním polem (`StagedAttachmentPreview`).
- **Galerie na celé obrazovce není složená** — `SurfaceView` leží nad vším a snímek by zakryla.
  Po návratu se session pickeru otevře znovu a zaškrtnutí obnoví z příloh jako po každém zavření.
- **Chrome mizí jen průhledností (`AnimatedVisibility` s `fadeOut`) a jeho rozměry se po dobu
  celé obrazovky NEměří znovu** (`headerHeight`, `captionTop`). Karta se při návratu vepíše
  tam, odkud odjela; s přeměřením by v půlce přechodu poskočila.
- **Víc snímků je `HorizontalPager` s „N z M" v hlavičce**, ne mřížka: album se stejně posílá celé
  a pruh miniatur by zdvojoval to, co umí swipe. ✕ ubírá snímek; odebrání posledního obrazovku
  zavře, protože není co poslat.
- **Pod popiskem je galerie, přes kterou jde dávku doplnit** (`GalleryPanel` + tentýž
  `EmbeddedMediaPicker` jako sheet v konverzaci), podle vzoru Google Zpráv. **Sbalená jen
  vykukuje** a rozvržení s ní počítá (snímek nad ní je celý vidět); vybírat v ní jde, posouvat
  ne. **Rozbalená vyjede PŘES snímek i pole až pod hlavičku**, nic nezmenšuje — první verze
  panel natahovala do sloupce a snímek se smrskl na známku. V hlavičce je pak místo ✕ šipka
  dolů, která panel sbalí; gesto zpět taky nejdřív sbalí (`NavigationBackHandler`).
- **Galerie má DVĚ ukotvené polohy a zavřít nejde** — dole a nahoře pod hlavičkou. „Hotovo"
  v pickeru ji jen vrátí dolů. Jediné, čemu ustoupí, je klávesnice při psaní popisku (zabírá
  totéž místo); po jejím schování je galerie zpátky sama, takže ikona „přidat fotky" v poli
  tam nemá co dělat.
- **Kde vestavěná galerie není** (Android pod 14, nebo bez SDK Extensions 15), je v poli na
  popisek ikona, která otevře **systémový výběr** (`rememberAttachmentPicker`) — jinak by šlo
  poslat jen to, co přišlo sdílením. Vybrané jdou za sdílené stejně jako z galerie. Systémový
  výběr fotky vydá pod vlastními URI, takže tu nejde poznat, že uživatel vybral sdílenou fotku
  podruhé. Ověřeno na emulátoru s Androidem 14 (Extensions 7): ikona otevře
  `PhotoPickerActivity`, vybraná fotka přibude a carousel na ni přejede; na Foldu s galerií
  ikona není.
- **Úchyt je úchyt, ne tlačítko.** Panel jede s prstem (výška sleduje tah, sevřená mezi obě
  polohy) a po puštění dosedne podle švihnutí, jinak podle toho, kam dojel (přes půl cesty
  nahoru). Ťuknutí prstem nedělá nic — první verze úchytem jen přepínala a uživatel správně
  namítl, že úchyt, který nejde táhnout, je tlačítko převlečené za něco jiného. Čtečka obrazovky
  tahat neumí, proto má úchyt rozbalení a sbalení jako akce (`semantics { expand / collapse }`).
- **Tah se chytá jen na úchytu**, ne na mřížce — tu kreslí cizí okno (`SurfaceView`), které si
  dotyky bere samo. Obsah pickeru za panelem při tahu a animaci o kus zaostává (picker se
  překresluje po `notifyResized`), po dosednutí sedí.
- **Tah se na emulátoru ověřuje `input motionevent DOWN/MOVE`, snímek, pak `UP`** — `input swipe`
  je hotové gesto a mezistav s panelem pod prstem nezachytí.
- **Dobrané snímky jdou ZA sdílené** a pager na ně přejede. Odznačení v galerii obrazovku
  nezavírá ani u posledního snímku (je to oprava výběru), ✕ na posledním snímku ano. Po ✕ se
  galerie otevře znovu (`removals` jako klíč): zaškrtnutí si obnovuje jen při otevření.
- **Sdílená fotka v galerii zaškrtnutá NENÍ a být nemůže.** MediaProvider předvýběr odmítne
  („MediaQuery:PreSelection: … package does not have permission for the uri"): předvybrat jde
  jen fotku, ke které aplikace dostala oprávnění PŘES PICKER, a sdílená přišla s oprávněním
  k URI jiné aplikace. Obejít by to šlo jen oprávněním ke čtení celé knihovny fotek, a to kvůli
  jednomu zaškrtnutí nestojí za to (a Play ho hlídá). Galerie ji ale pozná (`SharedContent.sources`,
  `pickerUriFor`): picker drží místní fotky jako `content://media/picker/<uživatel>/
  com.android.providers.media.photopicker/media/<id MediaStore>`, takže MediaStore URI i URI
  zabalené v cestě Fotek Google jde převést. Ťuknutí na sdílenou fotku ji proto nepřidá podruhé
  a odznačení ji z dávky odebere.
- **Odebraný snímek odchází animací, ne skokem** (`ShownAttachments`, `SharedPhoto(leaving)`).
  Pager odebrání stránky animovat neumí — chybějící stránka zmizí a soused naskočí na její
  místo. Odebraný snímek (odznačený v galerii i ✕) proto v carouselu chvíli zůstane: karta se
  stáhne a zprůhlední, a byl-li zobrazený, carousel dojede na souseda (další, u posledního
  předchozí); teprve pak stránka zmizí. Pager drží pozici podle klíče, takže odebrání stránky,
  ze které už odjel, s ním nepohne. Stránky tím nejsou 1:1 s přílohami — index do ViewModelu
  (✕, režim) se hledá podle cesty. Znovu zaškrtnutý snímek se během odchodu vrátí.
- **Zpomalené animace pro snímky ukládej mimo `/sdcard`** (`/data/local/tmp`): PNG v `/sdcard`
  MediaStore zaindexuje a objeví se v testované galerii.
- **Hláška (snackbar) stojí nad polem**, ne u spodku — galerie je `SurfaceView` nad vším.
- **Ověřeno na emulátoru** (jedna fotka i dvě z Fotek): obrazovka, spoiler, mizení v chatu s botem
  a jeho nepřítomnost v Uložených zprávách, popisek, odeslání s otevřením konverzace, ✕ s návratem
  do zdrojové aplikace. Galerie (profil Pixel 10 Pro Fold, API 37.2, rozložený i složený): výběr
  ve sbalené, tah s panelem pod prstem a dosednutí do obou poloh, tah dolů ze spodní polohy
  galerii nezavře, ťuknutí na úchyt nic nedělá, scroll v rozbalené, šipka i gesto zpět,
  znovuotevření bez zdvojení a odeslání alba dvou snímků s popiskem. **Přes `adb` jde poslat jen JEDNA příloha** — `EXTRA_STREAM` jako seznam
  `am start` neumí (`--eu` je jedna hodnota) a `--grant-read-uri-permission` platí pro `data`
  URI, ne pro extras: bez `-d <totéž URI>` aplikace soubor nepřečte, `getType` vrátí `null`
  a sdílení se zachová jako u dokumentu. Víc fotek se musí proklikat z Fotek.

### iOS: rozšíření sdílení

Na iOS je sdílení **rozšíření** (`iosApp/ShareExtension`, SwiftUI): list „Sdílet do" s chaty
přímo v nabídce sdílení, jako Telegram nebo WhatsApp. Po výběru chatu rozšíření otevře Plume
a obsah čeká nad vstupním polem, stejně jako na Androidu.

- **Rozšíření je samostatný proces s vlastním limitem paměti; TDLib v něm neběží** a databázi
  aplikace nevidí. Obě strany si předávají soubory přes kontejner App Group
  (`group.eu.tintera.plume$(TEAM_ID)`, klíč `PlumeAppGroup` v Info.plist obou): aplikace píše
  snímek chatů (`share/targets.json` + profilovky, `IosShareSnapshot`), rozšíření píše sdílení
  (`share/inbox/<čas>-<uuid>/`, `manifest.json` POSLEDNÍ). Formát drží `ShareTargets.ios.kt`
  a `ShareModels.swift` — **změna jedné strany musí jít i do druhé**.
- **Snímek chatů se po odhlášení maže** (`holder.replaced`), i s nevyzvednutým inboxem —
  rozšíření by jinak nabízelo chaty předchozího účtu. Nahoře jsou naposledy použité
  (`ShareTargets.reportUsed`, obdoba Direct Share).
- **Aplikace si inbox projde při každé aktivaci** (`IosShareInbox` nad `AppForeground`), ne
  jen při otevření přes URL: kdyby otevření z rozšíření selhalo, sdílení počká na nejbližší
  spuštění. Soubory se přesunou do `outgoing` a připraví stejně jako výběr příloh
  (`preparePhotoData`, `prepareVideoAt`, `prepareDocument`); složka inboxu se hned maže,
  kontejner App Group systém neuklízí.
- **Otevřít aplikaci z rozšíření sdílení oficiálně nejde.** `UIApplication.shared` ani `open`
  v rozšíření nejsou a **Xcode 27 u rozšíření vynucuje `APPLICATION_EXTENSION_API_ONLY = YES`**
  (s `NO` build selže). `openURL:options:completionHandler:` se proto volá přes Objective-C
  runtime na instanci `UIApplication` z řetězce responderů (`method(for:)` +
  `@convention(c)`). Na iOS 26 v simulátoru funguje; URL schéma aplikace je `eu.tintera.plume`.
- **Rozšíření fotky nezmenšuje** — limit paměti rozšíření je malý a zmenšení umí aplikace.
- **Simulátor bere entitlementy ze sekce `__TEXT,__entitlements` binárky**, ne z podpisu
  (ad-hoc, `codesign -d --entitlements` ukáže prázdný slovník). Ověření:
  `otool -s __TEXT __entitlements <binárka>`. S `CODE_SIGNING_ALLOWED=NO` tam nejsou a kontejner
  App Group neexistuje — pro zkoušení sdílení se staví s výchozím podepisováním. Kontejner:
  `xcrun simctl get_app_container <zařízení> eu.tintera.plume groups`.
- **Na zařízení musí App Group existovat v Apple Developer účtu.** Automatické podepisování
  ho s vyplněným `TEAM_ID` založí samo; bez týmu se build na zařízení nepodepíše.
- **Target rozšíření se do projektu přidával gemem `xcodeproj`** (`LANG=en_US.UTF-8`, jinak
  spadne na diakritice v pbxproj). Projekt je ve formátu Xcode 16 se synchronizovanými
  složkami; gem ho načte i uloží bez ztrát. Entitlementy aplikace jsou v `Configuration/`,
  ne v synchronizované složce `iosApp/` — odtud by je Xcode kopíroval jako resource. Obojí je „vyber, kam s tím".
- **Ověřování přes `am start` pozor na uvozovky**: `adb shell am start … --es … "dvě slova"`
  rozdělí text na dva argumenty a z druhého slova udělá balíček. Celý příkaz patří do jedněch
  uvozovek: `adb shell "am start … --es android.intent.extra.TEXT 'dvě slova' …"`. A hned po
  `adb install` sdílení někdy spolkne systémové znovuspuštění aplikace (`PackageUpdateActivity`)
  — zkoušet až s běžící aplikací.

#### Návrhy chatů v systémové nabídce (obdoba Direct Share)

Každé odeslání a přeposlání (`ShareTargets.reportUsed` → `IosShareTargetPublisher`) daruje
systému `INInteraction` s `INSendMessageIntent`; z toho iOS skládá řádek návrhů nahoře
v nabídce sdílení. Ťuknutí na návrh otevře rozšíření s `extensionContext.intent` a rozšíření
výběr chatu přeskočí — rovnou zapíše inbox a otevře aplikaci.

- **Podmínky jsou tři a chybějící se neprojeví chybou**, jen se nic neukáže (jako u Androidu):
  `NSUserActivityTypes` s `INSendMessageIntent` v Info.plist APLIKACE, `IntentsSupported`
  s týmž v `NSExtensionAttributes` rozšíření a `conversationIdentifier` u darovaného intentu
  (= id chatu, rozšíření ho čte zpátky).
- **Konverzace se daruje jako „skupina" (`speakableGroupName` + obrázek pro ten parametr)
  i u soukromého chatu.** Recipient `INPerson` chce handle nebo kontakt, které o protějšku
  nemáme. Obrázek jde jako data (`imageWithImageData`), ne URL souboru TDLib — ten leží
  v kontejneru aplikace, kam systém nedosáhne. Uložené zprávy obrázek nemají.
- **Po odhlášení se darované konverzace mažou** (`deleteAllInteractionsWithCompletion`, vedle
  smazání snímku chatů) — nesou jména a fotky chatů předchozího účtu.
- **Chat z návrhu, který snímek nezná, se pošle podle id** — aplikace ho zná, i když vypadl
  z padesáti chatů snímku.
- **Řádek návrhů se na simulátoru NEUKÁŽE.** Darování projde bez chyby, ale `sharingd`
  hlásí „People suggestions are not expected" a coreduetd v simulátoru neodpovídá. Ověřuje se
  proto po částech: darování dočasným `NSLog` v completion (`error=null`, v
  `log show --predicate 'eventMessage CONTAINS "…"'`), cesta rozšíření dočasně podstrčeným
  intentem (`context?.intent ?? INSendMessageIntent(…)`) a hlídáním inboxu App Group smyčkou
  v shellu (aplikace manifest při návratu do popředí hned vyzvedne). Sondy se necommitují.
- **Na simulátoru se po spuštění nejdřív ukáže zadání čísla** — to je krok `Connecting`
  (patří k zadání čísla, viz „Přihlašovací obrazovka"), ne odhlášení. Rozhoduje, co je vidět
  o pár vteřin později.

## Odesílání a scrollování

- **Odeslaná zpráva se do seznamu přidává OKAMŽITĚ.** `sendMessage` vrací zprávu hned, s dočasným
  id a stavem `Sending`; čekat na server by znamenalo viditelnou prodlevu mezi ťuknutím a bublinou.
- **TDLib po potvrzení zprávě ZMĚNÍ id.** `UpdateMessageSendSucceeded` nese staré i nové —
  zprávu nestačí aktualizovat, původní se musí ze seznamu odebrat, jinak tam zůstane dvakrát.
- **Klíč položky v `LazyColumn` proto NENÍ id zprávy**, ale `ConversationState.keyOf()`: u odeslané
  zprávy zůstává dočasné id (`itemKeys`: nové id → dočasné). S id jako klíčem bylo potvrzení
  doručení pro seznam odebrání jedné položky a přidání jiné — `animateItem()` jednu nechal
  odprchnout, druhou naskočit a bublina problikla. Let ze vstupního pole navíc přišel o cíl,
  protože potvrzení dorazí většinou dřív, než let doběhne. Kdo z viditelných položek čte klíč
  (hlášení přečtení, plovoucí datum), musí počítat s tím, že to není id.
- **Pořadí mezi `newMessages` a `sentMessageUpdates` NENÍ ZARUČENÉ.** Jsou to dva samostatné
  odběry ve dvou korutinách, takže potvrzení odeslání může předběhnout doručení té rozepsané
  zprávy, ke které patří. Vrátit ji pak do seznamu znamená mít tam zprávu dvakrát — a protože
  si podle `itemKeys` obě drží TENTÝŽ klíč, `LazyColumn` na to spadne
  („Key … was already used"). Proto obě změny stavu dělá `ConversationState`
  (`withMessage` / `withSentMessage`) a zprávu, kterou už potvrzení nahradilo, `withMessage`
  zahodí. Kontroluje se to proti HODNOTÁM `itemKeys`, tedy dočasným id.
- **Pád z toho byl vzácný a nereprodukovatelný** — poprvé se objevil po stovkách odeslaných zpráv
  a při dvou dalších pokusech o totéž nepřišel. Sedí na něj proto **test nad stavem**, ne klikání:
  `ConversationStateTest` prožene obě pořadí a hlídá, že klíče položek zůstanou jedinečné.
  Bez opravy test selže přesně tím, čím spadla aplikace: `[382754357249, 382754357249]`.
- **Rozepsaná zpráva dorazí přes `newMessages` DŘÍV, než `sendMessage` vrátí výsledek.** Let se
  proto na ni váže už tam (`landingOn`, podle textu a stavu `Sending`). Když se vázal až
  v `send`, bublina se na snímek vykreslila na místě, pak zmizela do letu a přiletěla znovu.
- **Nová zpráva dole se ukotví přes `requestScrollToItem(0)` v kompozici**, ne efektem
  s `animateScrollToItem`. `LazyColumn` drží pozici podle klíče první viditelné položky, takže
  vložená zpráva skončila pod spodní hranou a seznam k ní dojel až o snímek později.
- **Cíl letu nemá fade-in `animateItem`.** Během letu se kreslí v překryvu, kam průhlednost
  položky nedosáhne; po přistání by skočil z plné viditelnosti do rozjetého prolnutí.
- **Ikona doručení NENÍ `Crossfade`.** Ten prolíná obě ikony naráz, takže v půlce přechodu jsou
  napůl průhledné přes sebe; fajfka a dvojitá fajfka se kryjí a jejich společná část na okamžik
  zeslábla. Teď `AnimatedContent`: nová ikona naskočí zvětšením, stará jen rychle zmizí, obojí
  přes `MotionScheme`. Barva a popis se počítají ze stavu UVNITŘ obsahu — zvenku by odcházející
  hodiny zčervenaly dřív, než zmizí.
- **Čipy reakcí jsou v `key(emoji)`.** Server pořadí reakcí přeskládává a bez klíče se stav
  animací (barva, tvar) přenesl na sousední čip.
- **„Přečteno" není ve zprávě, ale na chatu** (`lastReadOutboxMessageId`): posuvný ukazatel,
  pod který spadají všechny odchozí zprávy s menším id. Kdyby se ukládalo do zpráv, muselo by se
  při každém přečtení přemapovat celá historie.
- **Scroll po odeslání je bezpodmínečný**, u příchozí zprávy jen když je uživatel dole. Jinak by
  ho nová zpráva vytrhla ze čtení historie — místo toho se dole objeví odznak s počtem.
- **Sdílený prvek při odeslání jde přes `sharedElementWithCallerManagedVisibility`.** Běžné
  `sharedElement` chce `AnimatedVisibilityScope`, který položka `LazyColumn` ani obsah vstupního
  pole nemají; tahle varianta bere jen `visible: Boolean`. `SharedTransitionLayout` už obaluje
  celou aplikaci v `AppTheme` — **další obalení by vyrobilo vnořený scope a klíče by se
  nespárovaly**.
- **Sdílí se CELÁ bublina, ne jen text.** Se sdíleným textem doletí text sám a pozadí pod ním
  na konci skokem naskočí.
- **Startovní strana letu musí přežít vyprázdnění pole.** Text se zapamatuje do `MessageFlight`
  hned při odeslání; bez toho by mezi vyprázdněním pole a příletem zprávy nebylo co animovat.
  Let se ruší až po doběhnutí animace — dřívější zrušení sebere klíč uprostřed přechodu.
- **Pořadové číslo v klíči letu** rozliší dvě rychle po sobě odeslané zprávy; s jedním klíčem
  by se dvě animace popraly o tentýž prvek.
- **„Jsem dole" kontroluje i offset**, ne jen index 0. Bez toho se za dole považuje i stav,
  kdy je poslední bublina z půlky za okrajem.

## Sponzorované zprávy

Zobrazení je podle [Telegram API ToS](https://core.telegram.org/api/terms) **povinné** a klient
do reklam nesmí zasahovat — nesmí je filtrovat, skrývat, ani měnit popisek tlačítka.

- **Zhlédnutí se hlásí přes `viewMessages`**, ne zvláštní funkcí. `viewSponsoredMessage` v TDLib
  1.8.67 už neexistuje; id sponzorované zprávy se předává do `viewMessages` stejně jako u běžné.
- **Hlásí se, teprve až je vidět celý text** (bez tlačítka) — tak to TDLib vyžaduje. Bereme
  přísnější podmínku: celá karta ve viewportu, protože rozměr samotného textu odsud nezjistíme.
- **Každé zhlédnutí jen jednou za relaci.** Opakované hlášení při každém scrollu by nafouklo
  statistiku inzerenta.
- **Kliknutí se hlásí PŘED otevřením odkazu** — jakmile se otevře prohlížeč, aplikace jde do
  pozadí a doručení už není jisté.
- **Prázdný seznam je normální stav**, ne chyba: reklamy se servírují jen v dost velkých veřejných
  kanálech, nikdy uživatelům s Premium, a závisí na dostupné inzerci.

## Soubory a média

TDLib **nedává URL**. Soubory drží ve vlastním adresáři a identifikuje je celočíselným `fileId`;
cesta je známá až po stažení a průběh chodí updaty. Překlad do jednoho reaktivního stavu je
[FileRepository] / `TdlFileRepository`.

- **Odběr `file(fileId)` sám spustí stahování.** UI nic neinicializuje — `RemoteImage` jen vykreslí,
  co dostane. Díky tomu nemůže vzniknout obrázek, který se nikdy nezačne stahovat.
- **Jeden stav na `fileId`.** Když tentýž obrázek visí v seznamu i v konverzaci, sdílí jedno
  stahování.
- **`downloadFile(synchronous = false)`** — s `true` by volání čekalo na dokončení a UI by mezitím
  nemělo co ukázat.
- **`local.path` je vyplněná i u rozestavěného souboru**, takže hotovost se pozná jen podle
  `isDownloadingCompleted`.
- **Coil potřebuje schéma** — `model = "file://$path"`; holá cesta se vyhodnotí jako relativní URI.
- **Progresivní načtení:** TDLib drží fotku v několika velikostech. Do bubliny jde největší varianta
  do 1280 px, nejmenší slouží jako okamžitý náhled (`thumbnailFileId`).
- **`RemoteImage` si pamatuje poslední známou cestu** (`knownPaths`). Odběr souboru startuje vždycky
  na `Pending` a odpovídá až z korutiny, takže každý nově složený `RemoteImage` proletí aspoň jedním
  snímkem bez obrázku. U sdíleného prvku ze seznamu do konverzace to bylo vidět: avatar odletěl jako
  prázdný kruh a obrázek naskočil až na místě. Mapa se čte i zapisuje jen z kompozice, takže
  nepotřebuje zámek.
- **Stahování obrázku ukazuje lesk přes náhled, ne indikátor** (`loadingShimmer` v `RemoteImage`):
  šikmý světlý pruh přejíždí přes rozmazaný náhled (nad fotkou bílý, na holé podložce v barvě
  popředí), jen dokud se stahuje — stojící rozmazání tedy znamená, že se nic neděje. Dřív to
  byl `ContainedLoadingIndicator` uprostřed: nad rozmazanou fotkou barevný disk, který k obrázku
  nepatřil a vypadal jako nálepka. Na průhledné podložce bez náhledu (samolepka) se nekreslí
  nic, pruh by přejížděl přes prázdno; pod 24 dp (malé avatary) taky ne.
- **Lesk běží i přes ZMENŠENINU, dokud se stahuje ostrá verze** (`sharpening` v `RemoteImage`).
  V prohlížeči médií stojí za ostrou fotkou zmenšenina z bubliny (`thumbnailFileId`) a dřív
  vypadala jako hotový obrázek — u velké fotky na pomalé síti nebylo poznat, že se něco děje.
  Náhled (`thumbnailFileId`) předávají jen fotky, takže pruh je vždycky ten nad fotkou.
  **Ověřuje se letovým režimem**: zmenšenina v konverzaci se načte, pak `adb shell cmd
  connectivity airplane-mode enable` a otevřít prohlížeč — ostrá verze zůstane ve stahování
  a lesk běží; po vypnutí letového režimu se dotáhne a obraz stojí. Zpomalení sítě na
  emulátoru nefunguje (viz „Video").
- **Lesk se ověřuje sondou a zpomalenými animacemi**: v `RemoteImage` dočasně `if (true)` místo
  `current == null` a `shimmer` bez podmínky na `Downloading`, pak `animator_duration_scale 5`
  a série `screencap`. V normální rychlosti snímek trefí pauzu mezi průjezdy a pruh „chybí".
  Při krytí 0,28 byl na snímku sotva znát — slabý lesk na telefonu splyne s rozmazáním.
- **Než dorazí jakýkoli soubor, kreslí se rozmazaný náhled ze zprávy** (`Minithumbnail`,
  parametr `placeholder` u `RemoteImage`). TDLib ho posílá přímo v datech fotky, videa, GIFu
  a dokumentu (JPEG kolem 40 px), takže je vidět v prvním snímku, bez stahování.
- **`Minithumbnail` není data class.** `ByteArray` se v data class porovnává podle reference;
  každé nové namapování téže zprávy by vypadalo jako změna a seznam by přestal přeskakovat
  nezměněné položky. `equals` proto porovnává obsah.
- **Rozmazává `Modifier.blur`, ne Haze.** Obojí umí vlastní obsah na Androidu 12+ i na iOS, ale
  na starším Androidu Haze kreslí místo rozostření tónovanou plochu, kdežto `blur` se jen vypne
  a zbude bilineárně zvětšený 40px náhled, který rozmazaně vypadá sám.
- **Poslední `updateFile` stahovaného souboru může lhát.** TDLib po stažení pozná, že tentýž
  soubor má už pod jinými id (bot poslal skladbu, která v chatu byla), a soubory SLOUČÍ: „hotovo"
  pošle jen pro ta starší id a stahované id dostane jako poslední update „stahuje, 73531 ze
  73531" — a dál nic. `getFile` o chvíli později hlásí hotovo. Přehrávač skladby na to čekal
  navždy (`loading`, MediaSession `BUFFERING`). Proto `confirmFinished`: soubor stažený celý,
  a přesto „stahující" (`looksFinished`, testy `LooksFinishedTest`), se ověří dotazem po 250 ms,
  1, 3 a 10 s. Ověřeno na emulátoru: `sendAudio` botem se souborem, který v chatu už byl,
  a ťuknutí do pár vteřin; v logu `Files` pak „Soubor N po stažení: hotovo=true" a skladba hraje.
  **Obecně: na stav souboru z updatů se nespoléhat, když na něm něco čeká bez časového limitu.**
- **`FileRepository` je eager** (`createdAtStart`) ze stejného důvodu jako seznam chatů — v
  konstruktoru se přihlašuje k `fileUpdates`.

## Material 3 / vzhled (UI)

Drž se **Material 3 Expressive guidelines a výchozích hodnot komponent**. Nečitelné změti barev
a splývajících ploch vznikají skoro vždy z ohýbání standardu, ne z jeho dodržování.

- **Verze Compose se berou z mlogu** (stejný Kotlin i AGP, ověřená kombinace): Compose, material3
  alpha, adaptive a lifecycle se zvedají spolu. Od Compose 1.12 se okno čte přes
  `currentWindowAdaptiveInfoV2` (zná navíc třídy Large a XL — na `widthClass` to nic nemění, obě
  leží nad Expanded) a stav modálního sheetu přes `rememberBottomSheetState(SheetValue.Hidden)`.
- **`material3` musí zůstat na ALPHA kanálu.** Ve stable artefaktu jsou API pod
  `@ExperimentalMaterial3ExpressiveApi` vyříznutá — `MaterialExpressiveTheme`, `MotionScheme`,
  `LoadingIndicator`, `WideNavigationRail`, `ButtonGroup`, `FloatingActionButtonMenu`. Bez alphy
  expressive vrstva neexistuje.
- **Preferuj defaulty.** Neoverriduj `containerColor`/elevation, dokud k tomu není konkrétní důvod.
- **Vrstvení = surface container role, ne `tonalElevation`.** `surface` < `surfaceContainerLow/…` <
  `surfaceContainerHigh` (overlay) < `surfaceContainerHighest` (menu nad overlayem). `tonalElevation`
  u `surfaceContainer*` barev nic nedělá — M3 `Surface` tónuje jen `color == colorScheme.surface`.
- Barvy vždy z `MaterialTheme.colorScheme`, nikdy natvrdo hex.
- **Přepnutí motivu se PROLÍNÁ, neprobliká** (`animateColorScheme` z materialkoloru v `AppTheme`,
  spec `AppMotion.defaultEffectsSpec`). Skoková výměna schématu přebarvila celou obrazovku v jednom
  snímku a vypadala jako chyba vykreslení. Animuje se KAŽDÁ role zvlášť, ne crossfade dvou kopií
  aplikace přes sebe: v aplikaci je plno průsvitných vrstev (závoj nabídky nad zprávou, závoj pod
  stavovou lištou, karta vstupního pole) a přes sebe položené kopie by prosvítaly.
- **49 vlastních `animateColorAsState` se psát nemusí.** Materialkolor je v projektu už kvůli
  generování palety a `animateColorScheme` pokrývá všechny role. Je ale stavěný proti STARŠÍ
  material3 (4.1.1 → 1.10.0-alpha05, projekt jede na 1.12) a volá `ColorScheme.copy` — tedy
  syntetické `copy$default` s pevným počtem parametrů. Nová barevná role v material3 by ho binárně
  rozešla a **chyba by přišla až za běhu**; před bumpem materialkoloru nebo material3 se to ověří
  `javap` nad `material3-desktop` (obě verze mají `copy-_VG5OTI$default` se 48 barvami).
- **`ColorScheme` NEMÁ `equals`** (ověřeno `javap`), takže se porovnává referencí. Cíl animace proto
  musí být STABILNÍ INSTANCE: `dynamicLightColorScheme`/`dynamicDarkColorScheme` se na Androidu
  `remember`ují, jinak by `updateTransition` uvnitř `animateColorScheme` zakládalo transition
  v každé rekompozici znovu. `rememberDynamicColorScheme` z materialkoloru stabilní je.
- **Ikony systémových lišt se překlápějí skokem** (`SystemBarsAppearance`), i když barvy jedou
  animací. Čekat na dojetí by znamenalo světlé ikony na světlém pozadí po celou dobu přechodu.
- **Během přechodu rekomponuje všechno, co čte schéma** — je to nová instance v každém snímku.
  Proto `defaultEffectsSpec` (kriticky tlumená pružina, ~čtvrt vteřiny), ne `slowEffectsSpec`:
  delší animace by ten náklad jen natáhla. Co si z barev něco dopočítává (`senderColor` staví
  osm HCT odstínů, `accentOn` měří kontrast), to se po dobu přechodu počítá každý snímek.
- **Akcent na tónované ploše se musí ověřit PROTI TÉ PLOŠE** (`accentOn` v `core/ui/AccentColor.kt`,
  testy `AccentColorTest`). M3 garantuje kontrast jen mezi plochou a její `on*` rolí; že je
  `tertiary` čitelná na `tertiaryContainer` (jméno v citaci, název webu u náhledu odkazu, odkaz ve
  zvýrazněné bublině, hlavička nad vstupním polem), je vizuální zkratka — a **v paletě se zvýšeným
  kontrastem** (Android: Usnadnění → Kontrast) **neplatí**: tam jsou v tmavém režimu container role
  SVĚTLÉ a jejich `on*` tmavé, takže se `tertiary` s `tertiaryContainer` potkají na téže světlosti.
  Změřeno ze snímku od uživatele: text odkazu #8AE9B3–#B3FFD6 na ploše #96F5BF, tedy kontrast 1,05
  — odkaz nebyl vidět vůbec. Akcent proto při nedostatku kontrastu ustoupí barvě popředí té plochy
  a odlišení nese řez (u odkazu podtržení, `RichText` ho přidá samo, když `linkColor == overlayColor`).
  **Platí i pro `senderColor`** — osm odstínů je laděných na plochu konverzace, ne na bublinu.
  **A pro `primary` na `primaryContainer`** (vybraná volba režimu přílohy, úrovně v pruhu
  nahrávání, „Teď ne" na kartě aktualizace): tatáž past o roli vedle.
- **Rozhoduje se z CÍLOVÝCH barev, ne z právě animovaných.** Barva bubliny se při zvýraznění
  prolíná; kdyby se kontrast počítal z okamžité hodnoty, přepnul by akcent uprostřed přechodu
  skokem. Výsledek se pak prolíná spolu s ní (`bubble-accent` v `MessageFrame`).
- **Animace nechávej na `MotionScheme`** (`AppMotion` v `app/root/presentation/theme/Motion.kt`).
  Spatial specy mají jemný bounce (dampingRatio 0.6–0.78), effects specy bounce nemají — overshoot
  u alphy/barvy se ořízne do [0,1] a působí jako bliknutí.
- **`SharedTransitionLayout` je v kořenovém tématu**, ne uvnitř obrazovek — sdílený prvek musí přežít
  výměnu obrazovky. Scope se bere z `LocalSharedTransitionScope`.
- **Ikony = Material Symbols** jako vector drawables v `composeResources`, s vyplněnou i nevyplněnou
  variantou (vybraná položka má vyplněnou).

- **Icon buttony mění tvar při stisku** (`shapes = IconButtonDefaults.shapes()`: kruh → zaoblený
  čtverec, u přepínacích `toggleableShapes()`), všechny — i `Filled…`, `FilledTonal…`
  a `Outlined…`. Kdo přidává nový, předá `shapes` taky; tlačítko bez nich se chová postaru
  a vedle ostatních to vypadá jako nedodělek. U standardního tlačítka bez plochy se mění tvar
  vlnky (ořez), u plného i plocha. Výjimky: tlačítka s vlastním tvarem, který nese stav
  (odesílací tlačítko, přehrát u videa a hlasovky, náhled přílohy), a fotka účtu v pilulce —
  obsahem je kulatá fotka a hranatá vlnka by kolem ní trčela. Doplnilo se skriptem do všech
  ~100 volání naráz; ověřeno na emulátoru (šipka zpět podržená přes `input motionevent DOWN`).
- **Přepínače mají v jezdci ikonu** (`IconSwitch` v `core/ui`): fajfka zapnutý, křížek
  vypnutý. Stav tak nese i tvar, ne jen barva — pozná ho barvoslepý i v paletě se zvýšeným
  kontrastem. **Holý `Switch` se nepoužívá**; kdo přidává přepínač, sáhne po `IconSwitch`,
  jinak bude vedle ostatních vypadat jako nedodělek. Barvu ikony podává přepínač sám
  (`checkedIconColor` / `uncheckedIconColor`), ikona je dekorace bez popisu — stav čte čtečka
  z řádku (`toggleable(role = Switch)`). Vypnutý jezdec s ikonou je větší než prázdný; M3 to
  tak má. Ověřeno na emulátoru ve světlém i tmavém motivu (Vzhled, Notifikace).
- **Počty se přetáčejí** (`RollingNumber` v `core/ui`): odznak nepřečtených v seznamu chatů,
  počet u složky a u archivu v menu účtu, odznaky zmínek a reakcí v konverzaci i „N nových
  zpráv" na tlačítku dolů. Přetočí se jen změněné číslice, párované ODZADU (9 → 10 přetočí
  jednotky, desítky naskočí); roste-li číslo, přijíždějí zdola. Směr se bere z celého čísla,
  ne z číslice. Čtečce obrazovky jde celé číslo (`clearAndSetSemantics`).
  - **Číslo uvnitř věty z `plurals`** se nesestavuje v kódu: do plurálu se místo čísla dosadí
    značka (`NUMBER_MARK`, znak ze soukromé oblasti Unicode) a věta se kolem ní rozdělí. Jde to
    proto, že Compose Resources dosazuje argumenty jako text (`replaceWithArgs` nahradí
    `%1$d` čímkoli). Kdyby se to změnilo, ukáže se značka místo čísla.
  - Ověřeno na emulátoru zpomalenými animacemi (`animator_duration_scale 10`) a sérií surových
    snímků odznaku: 1 odjede nahoru, 2 přijede zdola.
- **Neurčité čekání je `LoadingIndicator` (měnící se tvar), kolečko jen jako PRŮBĚH.**
  Kolečko (`CircularProgressIndicator`) zůstává tam, kde ukazuje, kolik je hotovo (přehrávání
  kolečka, průběh v docku přehrávače), nebo kde se z čekání v průběh PŘEMĚNÍ: kruh kolem tlačítka
  v docku čeká na stažení skladby a pak plynule nese průběh — tvar by tam po načtení přeskočil
  na kruh. Totéž vlnitý kruh nahrávání, který se točí neurčitě, dokud TDLib průběh neohlásí.
  V tlačítkách se `LoadingIndicator` vejde: 24 dp v textovém tlačítku mini aplikace, 32 dp místo
  přehrát v pruhu náhledu nahrávky, 36 dp v odesílacím tlačítku sdílení. „Číst celý článek" ho
  má jen 18 dp (pod minimem M3) — větší by o pár bodů zvedl řádek s popiskem a ten by poskočil.
  Ověřeno na emulátoru v náhledu zastaveného kolečka (v kruhu i v pruhu karty, sérií snímků).
- **Nabídky jsou Expressive** (`ExpressiveMenu` v `core/ui`: `DropdownMenuPopup`
  + `DropdownMenuGroup`), ne holé `DropdownMenu`. Položky jsou ve skupinách se společným
  zaoblením krajních položek; **nevratná akce má skupinu vlastní a chybovou barvu**
  (`MenuAction.destructive`), co k sobě patří, je v jedné. Nabídka s výběrem (období v hledání)
  bere položky s `checked` — vybraná je zaškrtnutá.

### Omezené rozostření

Mléčné sklo v konverzaci, závoj pod nabídkou nad zprávou a pozadí prohlížeče se nerozostřují,
když to systém nechce (`LocalBackgroundBlurAllowed`, `rememberBackgroundBlurAllowed` v `core/ui`);
Haze pak kreslí náhradní tónování, jako na Androidu 11.

- **Android: volba Přístupnost → Barvy a pohyb → „Omezit efekty rozmazání"** (Android 16 QPR2)
  zapisuje `Settings.Global.disable_window_blurs = 1` a aplikace se to dozví veřejným API
  `WindowManager.isCrossWindowBlurEnabled` / `addCrossWindowBlurEnabledListener` (od Androidu 12).
  Totéž hlásí úspora baterie a vývojářská volba rozostření oken. Zjištěno porovnáním
  `settings list` před přepnutím a po něm; `dumpsys window | grep mBlurEnabled` stav ukáže.
- **iOS: „Omezit průhlednost"** (`UIAccessibilityIsReduceTransparencyEnabled` a oznámení o změně).
  Jen přeložené.
- **Styl rozostření jen VYPÍNÁ** (`if (!blur) blurEnabled(false)`). `blurEnabled(true)` by přebil
  výchozí hodnotu Haze, která rozostření pod Androidem 12 sama vypíná.
- **Spoiler a citlivý obsah se rozostřují dál** — rozostření tam obsah skrývá, není to ozdoba
  pozadí, na kterou volba míří.
- **Ověřeno na emulátoru (Android 17)**: s volbou závoj nabídky jen ztmavuje (hrany za ním
  ostré), po `settings put global disable_window_blurs 0` za běhu se rozostření vrátí bez
  restartu. Původní stav emulátoru je klíč BEZ hodnoty (`settings delete`), ne nula.

### Typografie

Dvě rodiny (`theme/Typography.kt`): **Figtree** na všechno čtené, **Bricolage Grotesque** jen na
role `display*` a `headline*` — velké nadpisy obrazovek, přihlášení, iniciály v avataru. Obě
OFL z Google Fonts, licence v `docs/licenses/`.

- **Písmo se volí rolí škály, ne v obrazovce.** Žádné `fontFamily` v `features/`; kdo chce
  výrazné písmo, sáhne po `headline*`. Výměna dvojice je tím jen `Typography.kt` a `font/`.
- **`titleLarge` zůstává v primární rodině**, přestože zní jako nadpis. Nese název chatu
  v hlavičce konverzace, kam letí ze seznamu (`sharedBounds`) — změna písma by uprostřed letu
  skočila. Z téhož důvodu je sbalený nadpis `LargeFlexibleTopAppBar` (`titleLarge`) ve Figtree
  a rozbalený (`displaySmall`) v Bricolage; bar mezi nimi prolíná, takže to nevadí.
- **Statické řezy vyrobené z variabilních fontů**, ne variabilní soubory v aplikaci:
  `fonttools varLib.instancer <VF>.ttf wght=600 wdth=100 opsz=96 --static --update-name-table`.
  Jednodušší a na iOS jistější; osy se volí jednou, při výrobě.
- **Bricolage musí mít velkou optickou velikost a aspoň SemiBold.** S `opsz=48` a řezem Medium
  z ní byl obyčejný grotesk a nadpis nebyl od zbytku k rozeznání. Charakter (zúžení, zářezy
  v ohybech) je vidět až ve velkém a tučném.
- **Proložení výchozí M3 škály je laděné na Roboto** (`bodyLarge` +0,5 sp) a s jiným písmem je
  řídké. Po výměně rodiny se musí přeladit: text skoro nula, velké nadpisy lehce do minusu (Bricolage s `opsz=96` je těsná sama).
- **Text zpráv je 16/22**, ne 16/24 — bublin je hodně a hustší sazba je znát.
- **Tabulkové číslice (`tabularNumbers()`, `core/ui`)** všude, kde se číslo mění na místě nebo
  stojí ve sloupci: časy v seznamu chatů, počty nepřečtených a reakcí, čas hlasovky a videa,
  sloupec časů odkrývaný tahem, „N z M" v prohlížeči. Obě písma `tnum` mají.
- **Nepřečtený náhled v seznamu se odlišuje barvou (`onSurface`), ne řezem.** Pod polotučným
  názvem by byly na jednom řádku dvě tučnosti.
- **Zvýrazněné varianty (`*Emphasized`) jsou tatáž rodina o stupeň tučnější**, ne druhé písmo —
  zvýraznění uvnitř běžného obsahu nesmí vypadat jako cizí prvek.
- **Emoji ve vlastním písmu nejsou**, berou se ze systémového fontu (na Androidu i iOS funguje
  fallback sám). Kostka „🎲 6" má proto číslo v Bricolage — velká emoji jsou `display*`.
- **Iniciály v avataru mají `lineHeight = Unspecified`.** Výška řádku `headlineSmall` (32 sp) by
  byla vyšší než malý avatar; přirozená výška se škáluje s `fontSize`.

### Material You a jeho náhrada

`colorScheme(isDark, dynamic, seed)` je `expect`. Android 12+ s barvami z tapety vrací systémové
dynamic color; jinak (iOS, Android do 11, vypnutá tapeta) se paleta generuje ze seed barvy přes
materialkolor (`expressiveColorScheme` v `theme/ColorScheme.kt`). iOS build tedy není „Android bez
barev", ale má vlastní konzistentní paletu.

**Seed si volí uživatel** — Nastavení → Vzhled → Barva (`ThemeColor`, `AppSettings.themeColor`,
`ThemeColorPicker`), osm vzorků jako „Tapeta a styl" na Pixelu.

- **Styl palety je `Vibrant`, ne `Expressive`.** Expressive (výchozí v materialkoloru, spec 2021)
  posouvá odstín primární barvy o 240° od seedu (`ColorSpec2021`) — vzorek „Červená" by nebyl
  červený a z modrého seedu vyšla jiná barva, než jaká se dala čekat. Vibrant drží odstín seedu
  a sytost dává nejvyšší, jakou odstín unese.
- **Vzorek se kreslí z TÉHOŽ schématu, jaké by aplikace dostala** (horní půlka `primary`, dole
  `secondaryContainer` a `tertiaryContainer`), v aktuálním motivu. Seed sám nic neříká — v tmavém
  motivu je primární barva světlý tón, a právě ten uživatel uvidí.
- **S barvami z tapety žádný vzorek vybraný není**, i když nějaký uložený je: vybraný vzorek by
  tvrdil, že se aplikace kreslí z něj. Řada je vidět i tak a ťuknutí tapetu vypne
  (`chooseThemeColor`), věta pod řadou to říká předem. Vypnutí přepínače tapety vrátí uloženou
  barvu.
- **`null` = modrá a výchozí se neukládá** (stejné pravidlo jako u motivu): výchozí barvu tak jde
  změnit bez migrace. Neznámý název z novější verze spadne na výchozí.
- **Vybraný vzorek se morfne do dvanáctistranné sušenky** (`ShapeMorphs.Highlight`, „to vybrané /
  podržené"); fajfka na ploše `surface` téže palety nese výběr i bez tvaru. Sušenka jde jen do
  ořezu při KRESLENÍ, vrstva a dotyk zůstávají v kruhu (viz „Vzhled konverzace").
- **Vybraný vzorek se ve výpisu `uiautomator` rozpadne do tří uzlů** (zaškrtnutelný bez popisu,
  popis, `RadioButton`), nevybraný je jeden uzel s popisem. Dělá to Compose u `selectable`
  s rolí RadioButton (vybraný přestane být „klikatelný") a stejně vypadají vybrané řádky motivu;
  přeřazení `semantics` před `selectable` na tom nic nemění. Čtečka uzly slučuje.
- **Ověřeno na emulátoru** (česky): vzorky ve světlém i tmavém motivu, výběr zelené vypne tapetu
  a přebarví aplikaci, červená v tmavém motivu, modrá, opětovné zapnutí tapety výběr zruší.
  **iOS jen přeložené.**

## Haptika

Hmatová odezva má **jeden slovník v `core/ui/Haptics.kt`** — pět pojmenovaných rolí místo
`HapticFeedbackType` rozsetého po obrazovkách: `threshold()` (gesto překročilo práh),
`confirm()` (akce se povedla), `reject()` (nepovedla), `toggle(on)` (přepnutý stav) a `tick()`
(procházení po dílcích). Stejná úvaha jako u `ShapeMorphs` a `AppMotion`: význam má nést typ, ne
vkus toho, kdo zrovna píše obrazovku.

- **Dlouhý stisk si `combinedClickable` dělá SÁM.** Ruční volání navíc odezvu ZDVOJÍ — změřeno
  na emulátoru: dvě vibrace 9 ms po sobě, obě `constant=0`. Takhle to bylo na čtyřech místech
  (podržení samolepky, GIFu, emoji a samolepky v sadě). `longPress()` patří jen tam, kde si
  gesto detekujeme sami — dnes jediné místo je odkaz v textu (`RichText`).
- **Odezva patří k akci UŽIVATELE, ne k události od serveru.** Příchozí zpráva, dotažená stránka
  historie ani změna stavu spojení nevibrují; od upozornění na cizí akci jsou notifikace. Výjimka
  potvrzuje pravidlo: chyba se hlásí `reject()`, protože přišla po akci, kterou uživatel udělal.
- **Jakmile vibruje všechno, přestane cokoli znamenat** — totéž pravidlo jako u tvarů. Proto
  **odkrývání časů tahem haptiku NEMÁ**: na rozdíl od archivace a odpovědi tahem tam žádný práh
  „při puštění se něco stane" není, odkrytí je vidět hned a buzení při každém nahlédnutí by byl
  jen šum.
- **Potvrzení se hlásí při ŤUKNUTÍ, ne po odpovědi serveru.** Odezva patří ke gestu; kdyby čekala
  na potvrzení odeslání, přišla by po stovkách milisekund a necítila by se jako reakce na stisk.
- **iOS je hrubší, než se ze seznamu typů zdá.** `CupertinoHapticFeedback` mapuje celou sadu na
  čtyři generátory: `confirm` a `reject` → notification, `longPress` → medium impact, `threshold`
  i `toggle` → light impact, `tick` → selection. Zapnutí a vypnutí přepínače na iPhonu nejde
  rozeznat — spolehnout se, že iOS odliší to, co Android, nelze.
- **Na Androidu část typů na starších verzích není.** `Confirm` a `Reject` jsou od API 30,
  `ToggleOn`, `ToggleOff`, `GestureThresholdActivate` a `SegmentTick` od API 34, zatímco `minSdk`
  je 26; Compose je posílá přes `HapticFeedbackConstantsCompat`, který je nahradí bližším typem,
  nebo neudělá nic. Ověřovat tedy i na starším emulátoru.
- **Oprávnění `VIBRATE` potřeba není** (jde to přes view, ne přes `Vibrator`) a vlastní přepínač
  v Nastavení taky ne — vypnout odezvu jde v systému a `performHapticFeedback` to respektuje.
- **Emulátor s Androidem 9 nemá vibrátor VŮBEC.** Obraz `google_apis_playstore;arm64-v8a` pro
  API 28 nezaznamená ani dlouhý stisk launcheru (`dumpsys vibrator` má „Previous vibrations"
  prázdné) — náhradu typů, které na starých verzích neexistují, tedy na něm ověřit nejde. Zbývá
  na to skutečné staré zařízení; co se ověřit dá, je, že nic nespadne.
- **Posuvník videa tiká po dvacetinách délky, prohlížeč při dosednutí na jinou fotku** — jen
  po tahu prstem (`collectIsDraggedAsState` nad `interactionSource` pageru); programový posun
  (smazaná fotka, skok na fotku) netiká. První dílek tahu po posuvníku netiká, tik znamená
  „přešel jsi hranici dílku".
- **Ovládání videa na celé obrazovce leželo v povinné zóně systémových gest** (emulátor Pixel 9:
  `mandatorySystemGestures` posledních 84 px, střed posuvníku na y 2350 z 2424). Vodorovný tah
  po posuvníku tam vzal systém a prohlížeč se zavřel; na telefonu s navigací gesty by přepnul
  aplikace. Lišta proto bere spodní inset `navigationBars ∪ mandatorySystemGestures`. Zónu
  ukáže `adb shell dumpsys window | grep -A2 SystemGestures`.
- **Emulátor nevibruje, ale volání zaznamenává**: `adb shell dumpsys vibrator_manager` má sekci
  „Recent vibrations" a v ní `reason: performHapticFeedback(constant=N)`. Čísla jsou
  `HapticFeedbackConstants`: **0 = LONG_PRESS, 16 = CONFIRM, 17 = REJECT, 21 = TOGGLE_ON,
  22 = TOGGLE_OFF, 23 = GESTURE_THRESHOLD_ACTIVATE, 26 = SEGMENT_TICK.** Tím se dá ověřit, že
  odezva odešla a která — **jak to sedí pod prstem (hlavně jestli dvě nejdou hned po sobě) se
  pozná jedině rukou na telefonu.**

## Výkon a paměť

Nástroje pro analýzu výkonu a paměti: reporty Compose kompilátoru, LeakCanary v debug buildu
a skripty nad heap dumpy (`client/tools/heap/`). Co z první analýzy zbylo k řešení, je
v `docs/roadmap.md` („Výkon a paměť").

### Reporty Compose kompilátoru

`./gradlew :client:shared:compileAndroidMain -PcomposeReports` zapíše do
`client/shared/build/compose-reports` stabilitu tříd (`*-classes.txt`), skippability
composables (`*-composables.txt`, `.csv`) a souhrn (`android/main/*-module.json`). Bez
přepínače se reporty nevyrábějí, běžný build je nepotřebuje.

- **Strong skipping je zapnutý (Kotlin 2.x), takže „neskippable" composable v projektu
  není.** Stabilita rozhoduje o tom, JAK se parametry porovnávají: stabilní přes `equals`,
  nestabilní přes identitu (`===`). Nestabilní parametr tedy recompozici nevynutí, jen při
  každé nové instanci se stejným obsahem nepřeskočí.
- **Bloky konverzace jsou `@Immutable`** (`ConversationItem`, `MessageFrameData`…), takže se
  porovnávají přes `equals` i s nestabilními poli uvnitř. Na tom stojí to, že builder
  bloků vyrábí nové instance a nezměněné položky se přesto přeskočí. Report je u nich vede
  jako `stable`, zatímco jejich pole jako `runtime`. To je v pořádku.
- **Neměnné třídy, které kompilátor sám za stabilní nepozná, vyjmenovává
  `client/shared/compose-stability.conf`**, ne `@Immutable`: doménové typy v `core/telegram`
  nemají importovat Compose. Patří tam jen třídy, které se po vzniku opravdu nemění. Kdo do
  nich přidá měnitelné pole, musí je ze souboru vyjmout.
  - **`Minithumbnail` (`ByteArray`) dělal nestabilní celý řetěz**: `MessageMedia.Photo`,
    `Video`, `Animation`, `VideoNote`, `Audio`, `PaidPreview`, `LinkPreview` a tím
    i `ChatMessage`. Composables, které je brala přímo, je porovnávala identitou.
  - **Sealed interface vychází jako „Uncertain"**, i když jsou všechny implementace stabilní
    (`SelfDestructType`, `SpeechTranscript`). Kompilátor rozhoduje jen podle typu, ne podle
    implementací.
  - **Rekurzivní typ nejde odvodit**: `ChatMessage` → `ExternalReply` → `ChatMessage`
    vycházel nestabilní, dokud nebyl `ExternalReply` v konfiguraci.
  - Po úpravě je v `core/telegram` stabilní celý řetěz médií a `ChatMessage` vychází
    `runtime` (rozhodne `ChatPoll`, který je stabilní). Další sealed interface (`UiText`,
    `MessageMedia`, `Verification`) zůstávají „Uncertain": nejsou všechny implementace
    stabilní, a dát je do konfigurace naslepo by znamenalo slibovat neměnnost i za ně.
- **Změnu `compose-stability.conf` inkrementální překlad NEZAREGISTRUJE.** Přeloží se jen
  změněné zdrojové soubory, takže report ukáže novou stabilitu jen u nich a zbytek zůstane
  podle staré konfigurace. Po změně konfigurace se překládá s `--rerun`
  (`./gradlew :client:shared:compileAndroidMain --rerun -PcomposeReports`). Na CI to nevadí,
  tam se překládá načisto.
- **`List` a `Map` v datech jsou `runtime`**, ne `stable`. Do konfigurace stability je
  nepřidávat naslepo: s nimi by se porovnávaly přes `equals`, tedy u seznamu zpráv
  prvek po prvku při každé recompozici.

### LeakCanary

Je jen v debug buildu (`debugImplementation`); v launcheru přidá ikonu „Leaks". Nález
hlásí v logcatu (`adb logcat -s LeakCanary`) a v notifikaci.

- **Sám toho v Plume moc neuvidí.** Aktivita je jedna a `configChanges` pokrývá otočení,
  rozložení i změnu motivu, takže se nikdy nevytváří znovu. Hlídá proto jen okna, která
  zmizí (popupy, dialogy, sheety: `PopupLayout`), a služby (`PlumeMessagingService`).
- **ViewModely Navigation3 nehlídá.** Napojuje se jen na `ViewModelStore` aktivity, kdežto
  dekorátor ViewModelů Nav3 drží úložiště pro každou položku zásobníku. Únik ViewModelu se
  proto hledá počítáním instancí v heap dumpu (níž).
- **Analýzu pouští sám, až když jsou zadržené objekty**: v popředí od pěti, na pozadí od
  jednoho. Ticho po odeslání aplikace na pozadí (`input keyevent 3`, pak čtvrt minuty) znamená
  „nic nezadrženo".

### Heap dumpy a počítání instancí

`client/tools/heap/heap_dump.sh <adresář> <jméno>` vynutí GC, uloží `meminfo` a dump
a spočítá instance tříd. Postup: dump, N cyklů scénáře (otevřít a zavřít obrazovku), dump,
`heap_diff.py před.counts po.counts`. Co v paměti zůstalo, ukáže `heap_path.py dump.hprof
<třída>`: nejkratší SILNOU cestu od GC kořene (slabé reference se nepočítají).

- **GC se vynucuje `run-as eu.tintera.plume kill -10 <pid>`** (SIGUSR1 = explicitní GC
  v ART). Bez `run-as` to shell neprovede („Operation not permitted"), a jde to jen
  u debuggable buildu.
- **Únik se pozná podle RŮSTU s počtem cyklů, ne podle nenulového počtu.** Proto dvě kola
  (2 cykly na zahřátí, pak 8, pak dalších 8). `StateFlowSlot` po prvních cyklech přibyly
  o 45 a pak už ne: pool slotů se jednou zvětší, únik odběrů to není.
- **`uiautomator dump` drží v paměti poslední obrazovku.** Zapne v procesu přístupnost
  a Compose si v `AndroidComposeViewAccessibilityDelegateCompat.currentSemanticsNodes`
  nechá sémantiku posledního stavu, i s lambdami a ViewModelem v nich. Po zavření profilu
  tak v dumpu zůstal `ChatProfileViewModel`, po mini aplikaci `WebAppViewModel` i `WebView`
  (zničená, `destroy()` proběhl). Cache přetrvala i navigaci jinam. Jde o artefakt
  ověřování, ne únik; `heap_path.py` ho prozradí cestou přes `AccessibilityManager`.
- **Jedna instance ViewModelu posledního zavřeného chatu může viset v interní cache
  Compose** (`NodeChain$Differ.before` živého `LayoutNode`). Je jedna a s cykly neroste;
  kód aplikace s tím nic nezmůže.
- **Jednorázové keše nejsou únik**: `MediaCodecInfo` (seznam kodeků po prvním videu nebo
  kolečku) a stovky `BaseLocale` (ExoPlayer si poprvé staví mapu jazyků ISO).

### Delší běh (native heap)

Heap dump ukáže jen Java haldu; nativní (TDLib, kodeky, Chromium) se sleduje číslem.
`client/tools/heap/mem_sample.sh <štítek>` vynutí GC a vypíše jeden řádek: `alloc` (Heap
Alloc nativní haldy), `rss`, Java heap a PSS procesu. Scénář se pouští dokola a vzorek se bere
po každých pěti průchodech; rozhoduje trend `alloc`, ne jedno číslo.

- **Dívá se na `alloc`, ne na PSS ani RSS.** Ty kolísají podle toho, co systém zrovna odložil
  do zramu: proces s týmž `alloc` měl jednou PSS 27 MB a 26 MB `SwapPss`, jindy naopak.
- **Náběh trvá pár průchodů**, pak čísla kolísají o ±5 MB bez trendu. Porovnává se proto až
  stav po zahřátí, jako u počítání instancí.
- **`input swipe` přes otevřenou klávesnici PÍŠE.** Gboard bere tah po klávesnici jako psaní
  tahem, takže se v chatu s botem za čtyřicet průchodů napsalo „Balbau j h qk h heiqj h…"
  a uložilo jako koncept. Před tahy se kontroluje `dumpsys input_method | grep mInputShown`
  a klávesnice se schová; tahy se vedou nad ní.
- **Mini aplikace se ve smyčce zavírá gestem zpět, ne ťuknutím na ✕.** Za půl hodiny přijde
  heads-up notifikace od skutečného kontaktu a leží přesně přes hlavičku; ťuknutí podle
  souřadnic (i z dumpu těsně před ním) by otevřelo cizí chat. **Stalo se to** při ťuknutí na
  název skupiny v hlavičce konverzace: `uiautomator dump` heads-up nevidí (dumpuje okno
  aplikace), takže prvek „našel" a ťuknutí trefilo notifikaci. Před ťuknutím do horní části
  obrazovky se proto čte `dumpsys activity service com.android.systemui` a při
  `headsUpPinned=true` se čeká. Ze stejného důvodu tahy
  nezačínají v horní třetině obrazovky — tah dolů po heads-upu rozbalí lištu notifikací.
- **ANR během dlouhého scrollu na emulátoru nebyl náš.** Po ~25 minutách souvislého scrollu
  přišel jeden „Input dispatching timed out" a dialog se pak vracel i po „Wait", zatímco hlavní
  vlákno Plume spalo. Zadrhával i `system_server` a SystemUI a `IComposerClient.executeCommands`
  trval 2 s, tedy grafika emulátoru. Stack hlavního vlákna mířil do
  `GlobalSnapshot … globalWriteObservers`, a únik observerů to NENÍ: heap dump jich ukázal
  jeden (`GlobalSnapshotManager`) a 24 apply observerů (20 `snapshotFlow`), jak má být.
  Kontrola se dělá rozborem statických polí `SnapshotKt` v dumpu, ne odhadem ze stacku.

### Recompozice za běhu (composition tracing)

Debug build má `runtime-tracing` a přibalenou knihovnu Perfetto SDK (`tracing-perfetto-binary`),
takže každá composable je v systémovém trace vlastní řez se jménem a řádkem.
`client/tools/perf/trace_record.sh <adresář> <jméno> <ms> '<scénář>'` tracing v aplikaci zapne
broadcastem, nahraje trace a mezitím pustí scénář. `trace_analyze.py` pak vypíše snímky
aplikace a nejčastěji skládané composables (Python balíček `perfetto`, nejlépe ve venv).

- **Broadcast se posílá do BĚŽÍCÍHO procesu** a platí do jeho konce. Po přeinstalaci nebo
  zabití se musí poslat znovu, jinak trace nese jen systémové řezy a vypadá to, že se nic
  neskládá. `exitCode 1` = zapnuto, `2` = už bylo.
- **Nula řezů aplikace v nečinnosti je cíl, ne chyba záznamu.** Že záznam funguje, se ověří
  scénářem, ve kterém aplikace pracovat musí (scroll).
- **`Recomposer:animation` bez skládání = někdo si říká o snímky** (`withFrameNanos`,
  nekonečná animace) a jeho hodnota se čte až při kreslení. Ze jména řezu se nepozná kdo;
  hledá se v kódu (`withFrameNanos`, `rememberInfiniteTransition`, `while (true)`) a měří
  znovu.
- **Kdo se skládá v každém snímku, se pozná rozdělením řezů do časových oken**
  (`cast((ts - min) / 1e8 as int)`): šest složení za 100 ms = každý snímek, tedy animace
  čtená v kompozici; jedno za úhoz nebo za zprávu = v pořádku.

Nálezy a opravy (emulátor Plume_Galerie_Fold, debug build, říjen 2026):

- **Otevřený chat si říkal o snímek 60× za vteřinu, i když se nic nehýbalo** (průchody
  `Choreographer#doFrame` bez kreslení, viz „Start a plynulost"). Smyčka polohy
  zprávy pro nabídku (`MessageMenuLayer`, `withFrameNanos` dokola) běžela i se zavřenou
  nabídkou. Nečinné Uložené zprávy: 478 snímků za 8 s → 0. Seznam chatů měl nulu už předtím.
- **Scroll konverzace přeskládával celou obrazovku**, protože `visibleDay` (plovoucí datum)
  četl `topVisibleKey` přímo v těle `ConversationScreen`. Teď se odvozuje jen začátek dne
  (`startOfLocalDay`). Stejný scroll: našich složení 4 915 → 2 166, hlavička a pole 48× → 1×.
- **Při nahrávání se celá karta vstupního pole skládala v každém snímku** kvůli „dýchání"
  tlačítka; při psaní totéž po dobu morfu mikrofon → odeslat. Morf, dýchání a barvy tlačítka se
  čtou až v obsahu `TooltipBox` (vlastní rozsah). Šest vteřin nahrávání: karta 408× (553 ms) →
  61× (94 ms). Tlačítko se v každém snímku skládá dál (~0,8 ms), nový `MorphShape` za snímek
  jinak nejde.
- **Každá aktualizace seznamu chatů přeskládala všechny viditelné řádky.** `ChatSummary` měl
  „runtime" stabilitu, která za běhu vyšla nestabilní, takže se řádky porovnávaly identitou.
  Je v `compose-stability.conf`. Dvě příchozí zprávy: `ChatRow` 40× → 4×.
- **Příchozí zpráva přeskládala starší bublinu v každém snímku animace nároží** (`animateDpAsState`
  se četl v kompozici a z hodnoty se stavěl nový `RoundedCornerShape`). Teď je tvar jedna stálá
  instance s vlastním `CornerSize` (`AnimatedCorner` v `MessageFrame.kt`), který stav čte až
  v `toPx`, tedy při výpočtu obrysu. Tři zprávy od bota: `MessageFrame` 14× (490 ms) → 6× (72 ms).
  - **Překreslí se to bez rekompozice, protože Compose čtení stavu při počítání obrysu hlídá**
    (přečteno ve zdrojích ui 1.12): ořez a `graphicsLayer(shape)` počítají obrys uvnitř
    pozorovaného `updateLayerParameters`, `background(color, shape)` (a tím `Surface`) ho
    pozoruje v `getOutline` a překreslí se, a `drawWithContent` čte v kreslení tak jako tak.
    Vrstva obrysy porovnává hodnotou, takže stálá instance tvaru nevadí.
  - **Kdo z tvaru bere rohy, musí brát OBJEKT `CornerSize`, ne jeho hodnotu v kompozici**:
    `albumTileShape` rohy kopíruje, `flightShape` volá `toPx` ve `graphicsLayer` — obojí
    zůstává líné. Rohy z bubliny přečtené v kompozici by animaci zmrazily.
  - **Ověřeno 40× zpomalenými animacemi a sérií surových snímků**: profil spodního nároží
    předchozí bubliny prochází mezi kulatým a staženým plynule (měřením první barevné
    souřadnice v řádcích nad spodní hranou), ne skokem.
- **Každá nová zpráva přeskládala obal VŠECH viditelných zpráv konverzace** (`MessageItem.Content`,
  `RevealingTime`, `arrival`). Dvě příčiny a každá stačila sama — bez kterékoli z oprav zůstalo
  56 složení na tři zprávy:
  - **`Content` je člen rozhraní (`open`), a otevřené composables kompilátor přeskakovat
    NEUMÍ** (v reportu bez `restartable skippable`, jen `open`). Nová zpráva posune indexy všech
    položek a `LazyColumn` znovu spustí obsah každé viditelné — a s ním `Content`. Seznam proto
    volá top-level `ConversationItemContent(item, scope)`, který přeskočitelný je: blok se
    porovná přes `equals` a nezměněná položka se nesloží. Data class, která `Content` sama
    přepisuje (předěl dne), přeskočitelná je; bloky zpráv ho ale dědí z `MessageItem`.
  - **Obal četl v kompozici hranici nepřečtených** (`scope.unreadBoundary()`, kvůli efektu
    zprávy), a ta ho invalidovala s každou změnou stavu konverzace, přestože se hranice nemění
    a čte se přes `derivedStateOf` — i s `structuralEqualityPolicy()` (ověřeno pokusem, proč,
    zjištěné není). Teď ji čte až efekt (`snapshotFlow`). **Obecně: odvozený stav čtený
    v položce seznamu měřit, ne věřit, že `derivedStateOf` invalidaci zastaví.**
  - Tři zprávy od bota: obal zprávy 53× → 6× (nová zpráva a soused, kterému se změnilo nároží),
    našich složení 638 → 384. Příčina se našla sondou, která v `ConversationItemContent`
    logovala, jestli je blok `equals` s předchozím: byl, a obal se přesto skládal — tedy
    invalidace zevnitř, ne nové parametry.
- **Nestabilní argument nevadí, když nezměněné položky drží TUTÉŽ instanci.** `ForumTopic`
  je pro kompilátor „runtime" (`TopicMessagePreview` nese `UiText`, `ChatDraft`) a `TopicRow` ho
  porovnává identitou — a přesto se při změně jednoho tématu skládá jen jeho řádek: seznam témat
  mění jedno téma z updatu a ostatní instance nechává. Změřeno v testovací skupině s pěti tématy:
  tři zprávy od bota do jednoho tématu, `TopicRow` 4×. Do `compose-stability.conf` proto
  nepatří. Seznam chatů byl jiný případ: `publish` skládá při každém updatu nové `ChatSummary`
  pro všechny chaty. `InviteLink` a členové se čtou znovu jen po akci uživatele (jednou, ne
  s každým updatem) a `MemberDetail` je jediný sheet, ne seznam — měřit je nemá smysl.
- **Obecně: argument composable v seznamu, jehož třída nese pole typu sealed interface, se
  porovnává identitou**, i když je data class z `val`. Report kompilátoru ho ukáže bez
  `stable` u parametru (`chat: ChatSummary` místo `stable chat: ChatSummary`).

### Start a plynulost

Měřeno v říjnu 2026: emulátor Plume_Galerie_Fold s release buildem (R8) podepsaným debug klíčem
a Pixel 11 Pro Fold (složený) s verzí 0.3.51 z Play. Postupy:

- **Studený start**: `am force-stop`, pak `am start -W -n eu.tintera.plume/.MainActivity`.
  Pět opakování a medián; první běh po kompilaci se zahazuje.
- **Kdy je start hotový**, hlásí seznam chatů přes `ReportDrawnWhen` (`core/ui/ReportDrawn.kt`)
  ve chvíli, kdy opustí načítání. V logu je to `Fully drawn eu.tintera.plume/.MainActivity:
  +N ms`; z téhož hlášení počítají Play vitals i Macrobenchmark. Samotné `TotalTime` z `am start
  -W` je první snímek, ten ještě chaty nemá.
- **Plynulost**: `dumpsys gfxinfo eu.tintera.plume reset`, tahy přes `input swipe`, pak
  `dumpsys gfxinfo eu.tintera.plume`. Rozhoduje „Janky frames" a percentily; „legacy"
  a „High input latency" jsou u vstupů z adb nesmyslné. Na emulátoru se plynulost neměří.

Výsledky:

- **Studený start je rychlý.** Emulátor: první snímek ~130 ms, seznam chatů ~170 ms. Fold:
  první snímek 151–175 ms, TDLib připravená 110–140 ms po startu procesu.
- **Vlastní baseline profile se zatím nevyplatí.** APK nese profil knihoven (`baseline.prof`,
  15 kB) a Play instaluje s profilem z cloudu (`speed-profile`, `install-dm`). Na emulátoru
  vyšel seznam chatů v režimech `verify` / `speed-profile` / `speed` (strop toho, co dokáže
  dokonalý profil) za 207 / 171 / 157 ms. Zbývá ~15 ms, a to modul Macrobenchmark s AGP 9
  a KMP nestojí. Režim se přepíná `adb shell cmd package compile -f -m <režim> eu.tintera.plume`.
- **Scroll na Foldu (120 Hz)**: seznam chatů 0,07 % zadrhnutých snímků, 99. percentil 9 ms;
  Uložené zprávy 0,31 %, 15 ms; chat s Notifikátorem (kolečka, samolepky) 0,34 %, 15 ms.
- **`gfxinfo` počítá jen VYKRESLENÉ snímky.** Nečinný chat ve verzi 0.3.51 měl v `gfxinfo`
  nulu, a přitom Perfetto na Foldu ukázalo 361 průchodů `Choreographer#doFrame` za 6 s. Smyčka
  s `withFrameNanos` budí hlavní vlákno každý snímek, i když nic nekreslí. Takové plýtvání se
  proto hledá Perfettem (`trace.cfg` funguje i s release buildem, `Choreographer#doFrame`
  zaznamená atrace kategorie `view`), ne přes `gfxinfo`.

### Výsledky první analýzy

Říjen 2026, emulátor Plume_Galerie_Fold (Android 17), debug build, cykly v testovacích
chatech: Uložené zprávy (prohlížeč, sheet výběru, nabídka sponky, nabídka nad zprávou),
Notifikátor (kolečka, profil se všemi záložkami), Nastavení (všech pět sekcí), mini aplikace
(ukázka `cafe`). **Únik nenalezen** v žádném scénáři. Java heap se drží na 23–30 MB a mezi
koly se nemění, přehrávače (`ExoPlayerImpl`) po odchodu z chatu mizí a LeakCanary nic
nezadržel.

**Native heap se ustálí, neroste** (delší běh, `mem_sample.sh` níž). Čerstvý proces má
alokováno ~45 MB; po prvních pěti průchodech chatem s botem (kolečka, GIFy, samolepky, scroll
do historie a zpět) ~80 MB a pak 79–90 MB až do čtyřicátého průchodu, bez trendu. Třicet
otevření a zavření mini aplikace (`cafe`) přidalo 0,3 MB (69,6 → 69,9) a po zavření je
`WebViews: 0`. Dřívější „80 → 130 MB" byl tedy náběh kešů (TDLib, kodeky, Chromium), ne únik.

**Při rozložení a složení displeje aplikace padala.** Příčina byla `consumeWindowInsets(PaddingValues)`
s hodnotou, která se mění se změnou scény, viz „Gotchas". Na Foldu to byl pád
`NPE … Object.getClass()` ve verzích 0.3.47 i 0.3.50.

## Gotchas

- **Modifikátor insetů s hodnotou, která se mění se změnou scény, shodí aplikaci při
  rozložení nebo složení Foldu.** `consumeWindowInsets(PaddingValues)` i `windowInsetsPadding`
  při změně hodnoty volají `insetsInvalidated()` → `traverseDescendants` a NEKONTROLUJÍ, jestli
  je uzel připojený (Compose 1.12, `WindowInsetsPadding.kt`). Když NavDisplay přesouvá obsah
  položky do jiné scény (jeden panel ↔ list-detail), aplikuje se změněný modifikátor uzlu,
  který je zrovna odpojený: `visitSubtreeIf called on an unattached node`. Compose chybu
  zachytí („Error was captured in composition") a pád pak přijde až při měření jako
  `NullPointerException` v `LookaheadPassDelegate.forEachChildAlignmentLinesOwner`, v release
  buildu `NPE … Object.getClass()`. **Hledá se tedy podle PRVNÍ chyby v logu, ne podle té,
  která aplikaci shodila.** Tak padal seznam chatů: `consumeWindowInsets(PaddingValues(bottom =
  scaffoldPadding…))`, jehož hodnota se při rozložení mění. Pravidlo: do modifikátorů insetů
  dávat POPIS insetů (`WindowInsets.navigationBars.union(WindowInsets.ime)`,
  `ScaffoldDefaults.contentWindowInsets.only(…)`), který je mezi rekompozicemi pořád stejný,
  ne čísla odvozená z paddingu. Reprodukce na emulátoru Fold: seznam chatů na složeném
  displeji, `adb emu unfold` / `fold` v cyklu (bez opravy pád do šesti cyklů).

- **`ConversationScreen` je v dexu na hraně a nová logika patří do VLASTNÍ funkce.** Po přidání
  docku přehrávače přímo do ní měla funkce 275 registrů a otevření každého chatu padalo na
  `NullPointerException … Composer.sourceInformationMarkerStart` na `null` v
  `rememberComposableLambda` u `bottomBar = {`. Dřívější verze měla 253 registrů a fungovala, po
  vytažení do `rememberPlayerDock` / `ConversationPlayerDock` má 268 a funguje. **Mechanismus
  (D8 nebo ART) ověřený NENÍ** — jisté je jen, že vytažení pomohlo. Kdo přidává do
  `ConversationScreen`, ať aplikaci po změně otevře v chatu, ne jen přeloží; počet registrů
  ukáže `dexdump -d` nad APK (`registers` u `ConversationScreen(`).

- **`adb shell input text` do pole BEZ fokusu jde jako klávesnice do obrazovky a mezera
  (`keyevent 62`) „ťukne" na zaostřené tlačítko.** Formulář ankety se tak zavřel přes Zavřít,
  pak šipkou zpět odešla i konverzace a zbytek textu skončil v hledání seznamu chatů — bez
  jediného cizího příkazu (ověřeno v logu `adbd service requested`). Před psaním ověřit
  `focused="true"` ve výpisu a mezery psát jako `input text "%s"`, ne `keyevent 62`.

- **`ExtendedFloatingActionButton(text = …, icon = …)` popisek skrývá před čtečkou obrazovky**
  (`clearAndSetSemantics` uvnitř animace rozbalení) — tlačítko pak ve stromu přístupnosti nemá
  žádný název a `uiautomator` ho podle textu nenajde. Obsahová varianta
  (`ExtendedFloatingActionButton(onClick) { Icon; Spacer; Text }`) popisek nese.

- **„JDK home directory does not exist" s cestou do `actions-runner` = zastaralá konfigurační
  keš**, ne chyba v projektu. Keš si pamatuje cestu k toolchainu z doby, kdy na Macu běžel
  GitHub runner; po jeho smazání build padá dřív, než se dostane ke kódu. Pomůže
  `./gradlew --stop` a `rm -rf .gradle/configuration-cache`.
- **Neexistující úloha Gradle shodí CELÉ volání, i úlohy před ní.** `assembleDebug
  :client:shared:neexistujici -q | grep "^e:"` nevypíše nic (chyba je „Task … not found", ne
  `e:`) a APK zůstane STARÉ — na emulátoru pak chybí právě přidaná změna a vypadá to jako chyba
  v kódu. Po sestavení k ověření kontrolovat čas APK nebo `BUILD SUCCESSFUL`.
- **CI na pull requestech NIC NEPŘEKLÁDÁ** — kontroluje jen poznámky k vydání. Po #284 se `main`
  nedal přeložit (`vararg` hodnotové třídy `Color`: „Prohibited vararg parameter type") a nikdo
  si toho nevšiml, dokud další větev nestavěla. Před pushem proto aspoň
  `./gradlew :client:androidApp:assembleDebug`; u parametru s hodnotovou třídou (`Color`, `Dp`)
  seznam, ne `vararg`.
- **Test ViewModelu MUSÍ nastavit `Dispatchers.setMain`** (a v `@AfterTest` `resetMain`).
  `viewModelScope` běží na `Dispatchers.Main`: na JVM žádný není a lifecycle vezme prázdný
  kontext, takže test projde, kdežto na iOS je `Main` skutečná hlavní fronta, kterou `runTest`
  na hlavním vlákně nevyprázdní — ViewModel se nerozběhne a test visí do minutového limitu
  (`UncompletedCoroutinesError`). Zelené `testAndroidHostTest` proto o iOS nic neříká.
  S `StandardTestDispatcher` jako `Main` si `runTest` převezme jeho plánovač a ViewModel jede
  na tomtéž virtuálním čase (viz `NewChatViewModelTest`).
- **Blokové komentáře v Kotlinu se VNOŘUJÍ, takže `/*` uvnitř KDoc ho nechá neuzavřený.**
  Cesta s hvězdičkou (`core/ai/` + `*Prompt.kt`) napsaná do KDoc otevře vnořený komentář,
  závěrečné `*/` zavře jen ten a zbytek souboru zmizí v komentáři. Hlásí se to zavádějícím
  „Unresolved reference" na symbol o desítky řádků níž a teprve pak „Unclosed comment" na konci
  souboru — hledat se přitom musí v komentáři, kterého si nikdo nevšimne. Glob se do komentáře
  proto nepíše.
- **`androidResources { enable = true }`** v KMP modulu je povinné, jinak se composeResources nezabalí
  a čtení stringu spadne až za běhu na „No instrumentation registered!".
- **iOS framework musí být `isStatic = true`** — TDLib je statická knihovna, jinak link padne na
  undefined symbols `_td_*`.
- **Odběr update flow musí běžet dřív než první request.** TDLib odpovídá updatem, ne návratovou
  hodnotou; opačné pořadí zahodí první `AuthorizationState` a přihlášení zůstane viset.
- **`loadChats` vrací `Ok`, ne data.** Chyba 404 znamená „konec seznamu", ne selhání. Samotné
  chaty přicházejí **updaty**, ne návratovou hodnotou — viz `TdlChatRepository`.
- **`ChatPosition.order == 0` znamená, že chat ve složce NENÍ.** Musí ze seznamu složky zmizet,
  jinak tam zůstane viset po archivaci. Chat sám ale známý zůstává — viz „Archiv".
- **Material Symbols z Google Fonts potřebují úpravu**, než je uložíš do `composeResources`:
  vyhoď `android:tint="?attr/…"` a nahraď `@android:color/white` literální barvou. Resource
  reference commonMain parser nezná. `material-icons-core` **nepoužívej** — Google ho neudržuje,
  poslední verze je 1.7.3.
- **Repository seznamu chatů MUSÍ vznikat eagerně** (`createdAtStart = true` v Koinu). Odběry
  chat updatů se registrují v konstruktoru; při líné konstrukci vznikne až při první kompozici
  seznamu, což je (naměřeno) ~480 ms po `AuthorizationStateReady` — a první dávku `UpdateNewChat`
  TDLib do té doby rozešle do prázdna. Seznam pak zůstane navždy prázdný.
- **Updaty samy nestačí — po `loadChats` se musí přečíst stav z lokální DB** (`getChats` +
  `getChat`). Updaty chodí jen na ZMĚNY; bez tohohle seedu je seznam závislý na načasování odběru.
- **Updaty k jednomu chatu chodí v RŮZNÝCH korutinách a pořadí mezi nimi neplatí.** Každý
  odběr (`newChatUpdates`, `chatPositionUpdates`, `chatLastMessageUpdates`…) je vlastní `launch`,
  a `upsert` si před zápisem ještě dočítá ověření a fórum. `UpdateChatPosition` poslaný TDLib až
  po `UpdateNewChat` tak doběhl dřív, `upsert` ho přepsal prázdnými pozicemi z objektu chatu
  a skupina čerstvě převedená na supergroupu v seznamu chyběla až do restartu; poslední zpráva
  k ještě neznámému chatu se zahodila (`edit`). Proto u NOVÉHO chatu `upsert` pozice z updatů
  slévá a nová poslední zpráva si neznámý chat dočte (`edit(fetchUnknown = true)`).
- **Každá nová routa se musí zaregistrovat v `NavSavedStateConfiguration`.** `NavKey` je otevřená
  polymorfní hierarchie, kotlinx.serialization si podtypy neodvodí. Chybějící registrace projde
  kompilací a **spadne až za běhu** při první kompozici shellu.
- **Top-level `val` se inicializují v pořadí deklarace.** `NavSavedStateConfiguration` má proto
  `SerializersModule` vložený inline — odkaz na `private val` deklarovanou níž by byl `null`.
- **`mapNotNull` neexistuje pro primitivní pole.** `Chats.chatIds` je `LongArray`, je potřeba
  `.toList()`. Totéž u `Messages.messages`, kde jsou navíc prvky v Kotlin metadatech nullable
  (Java signatura je `Message[]`) → `.filterNotNull()`.
- **Výšku obrázku nepočítej `aspectRatio`, když ji chceš zastropovat.** `aspectRatio` si výšku
  dopočítá ze šířky a předřazené `heightIn(max = …)` neuplatní — svislý snímek obrazovky pak vyroste
  přes celou bublinu a popisek pod ním se překryje. Správně: `BoxWithConstraints` + explicitní
  velikost s `coerceAtMost` (`photoFrame`, viz „Rámeček fotky v bublině"). A **se stropem výšky
  musí jít dolů i šířka** — jinak strop výšku jen usekne a `Crop` z fotky ukrojí kraje.
- **`height(IntrinsicSize.Min)` nesmí obalit nic se `SubcomposeLayout`** (`BoxWithConstraints`,
  lazy seznamy). Intrinsické míry neumí a aplikace spadne až při vykreslení — u náhledu odkazu
  s velkým obrázkem to shodilo každý chat, kde takový náhled byl vidět. Proužek „přes celou výšku"
  se proto kreslí `drawBehind` za obsahem, ne jako `Box` s `fillMaxHeight`.
- **`implementation` závislosti z `:client:shared` nejsou tranzitivní.** Co používá vstupní bod
  (`:client:androidApp`), musí být deklarované i tam — narazilo se na tom u Kermitu.
- **Chyby ze stavu musí mít kam vyjít.** `ConversationScreen` má `SnackbarHost`; dřív se
  `state.error` vykreslovalo jen u prázdné konverzace, takže neúspěšná reakce nebo odeslání
  zmizely bez jediné stopy.
- **Barvy avatarů drž jako dvojice pozadí + popředí.** `onPrimaryContainer` na `tertiaryContainer`
  je kontrastní nesmysl, který M3 negarantuje.
- **ViewModel na detailu MUSÍ mít klíč obsahující id** (`koinViewModel(key = "conversation-$chatId")`).
  ViewModel se cachuje podle klíče a Koin u cachované instance **ignoruje nové `parameters`** —
  konstantní klíč tedy znamená, že druhá otevřená konverzace dostane ViewModel té první i se
  zprávami, a neprojeví se to chybou, jen špatným obsahem. Od dekorátoru ViewModelů (viz
  „Zásobník pro každou sekci") má každá položka vlastní úložiště a klíč je jen pojistka — ale
  obrazovka mimo `NavDisplay` by ji zase potřebovala.
- **`Info.plist` musí mít `CADisableMinimumFrameDurationOnPhone = true`.** Compose Multiplatform
  to kontroluje při startu a bez klíče aplikace na iOS hned spadne na `IllegalStateException`
  (jinak by na 120Hz displejích běžela na 60 snímků). Chyba je vidět jen v konzoli, na simulátoru
  se aplikace prostě zavře.
- **Systémové frameworky iOS se do Xcode projektu přidávat nemusí.** Kotlin/Native zapíše použité
  frameworky (PhotosUI, UniformTypeIdentifiers, CoreImage…) do objektových souborů a linker je
  přidá sám, i u statického frameworku. Ověření: `otool -L` na `Plume.app/Plume.debug.dylib`
  (v Debug je kód v `.debug.dylib`, ne v hlavní binárce). Sestavení aplikace bez podpisu:
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.
- **Release link iOS frameworku trvá desítky minut.** Kotlin/Native pouští LTO nad celou statickou
  TDLib; naměřeno 15+ minut na M-series. Debug link je ~5 minut. Do CI to patří jako samostatný,
  cachovaný krok — ne do smyčky, kterou čeká vývojář.

## Co ještě není vyřešené

**Seznam se vede ve dvou živých dokumentech, ne tady.** Tenhle soubor se čte při každé session,
takže by v něm rostoucí seznam úkolů jen ubíral místo poučením.

- **[docs/roadmap.md](docs/roadmap.md)** — přes celou aplikaci: co chybí před vydáním, čím je
  iOS pozadu, které obrazovky a sekce nejsou (archiv, hledání, profil, nová konverzace), a taky
  co právě leží rozpracované v pracovním adresáři.
- **[docs/roadmap-konverzace.md](docs/roadmap-konverzace.md)** — podrobný seznam pro detail chatu
  i zásobník menších nálezů z čtení kódu.

**A ani ve třetím.** README mělo vlastní odstavec „Není hotové" a přestal se aktualizovat: tvrdil,
že chybí přehrávání videa a zvuku, otevírání dokumentů, úprava zprávy, skok na první nepřečtenou
a perzistence nastavení — všechno dávno hotové. Seznam úkolů vedený na dvou místech se udržuje
na jednom a to druhé tiše lže. README proto říká, co aplikace umí, a na roadmapy odkazuje;
číslování fází z něj zmizelo ze stejného důvodu (README hlásilo fázi 7, CLAUDE.md fázi 6).

Obojí je živý dokument: co se udělá, odtud zmizí a poučení z toho patří sem.
