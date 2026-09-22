# CLAUDE.md

Material 3 Expressive klient pro Telegram. Kotlin Multiplatform, Android + iOS, sdílené Compose UI.

**Pracovní název je „Plume"**, balíček `eu.tintera.plume`. Podle [Telegram API ToS](https://core.telegram.org/api/terms)
nesmí název obsahovat „Telegram" bez předřazeného „Unofficial" a nesmí se použít oficiální logo.

## Stav

Hotové: téma, přihlašovací automat, Navigation3 + adaptivní shell (rail/bar), živý seznam chatů a archiv,
konverzace (historie, živé zprávy, odesílání, přílohy a alba, hlasovky, samolepky a emoji, úprava,
přeposílání, připnuté zprávy), prohlížeč médií s fotkami i videem, reakce, odpovědi, notifikace
a hlášení pádů na Androidu, nabídka aktualizace z Play, sponzorované zprávy, stav spojení a jeho obnova po návratu z pozadí.

Není: notifikace na iOS ani hlášení pádů z iOS; sdílená média v profilu chatu.
Hledání umí chaty, veřejné chaty, zprávy napříč chaty i uvnitř jedné konverzace. Nastavení je list-detail se sekcemi Obecné, Vzhled a Zařízení; vědomě nastavitelné je zatím
téma aplikace (motiv a barvy z tapety) a vyčlenění nepřečtených chatů do vlastního bloku.
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
  jinde. Zatím tam kromě atributů pro čitelné stack trace není nic.
- **`isShrinkResources` zapnuté NENÍ.** Je to samostatný krok, který umí uklidit
  composeResources; zapínat ho spolu s minifikací by znamenalo nevědět, co z toho pád způsobilo.
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

### Výběr země a formát čísla

**Seznam zemí ani formáty čísel se nedrží u nás** — dává je TDLib, a všechno „can be called
before authorization":

- `getCountries()` → kód, název, **emoji vlajky**, `isHidden` a předvolby. Vlajka je emoji ze
  systémového písma, takže se pro dvě stě zemí nedrží žádná sada ikon.
- `getCountryCode()` → země podle IP. **Použije se, jen dokud si uživatel nevybral sám** —
  dorazí po síti a přepsat jím ruční volbu by vypadalo jako chyba.
- `getPhoneNumberInfoSync(languageCode, prefix)` → z rozepsaného čísla pozná zemi a vrátí masku,
  ve které **`-` je očekávaná číslice**. Je to lokální dotaz, takže se smí volat při každém úhozu.

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
- **Stav je v hlavičce, ne ve vlastním pruhu**: v seznamu chatů pilulka uvnitř vyhledávací
  pilulky (viz „Hlavička: vyhledávací pilulka"), v konverzaci nahrazuje podtitul. Kdo se dívá
  na prázdný seznam, hledá vysvětlení nahoře. Stejně to řeší oficiální klient.
- **`Ready` se nehlásí.** V tom stavu aplikace stráví skoro celou dobu; „připojeno" je šum, zatímco
  „nepřipojeno" je jediná informace, která uživateli vysvětlí, proč mu nic nechodí. Proto
  `ConnectionState.label()` vrací u `Ready` `null`.
- **V hlavičce konverzace má stav spojení přednost před „píše…" i dostupností.** Dokud spojení
  nestojí, jsou obě jen staré hodnoty, které nemá co obnovit.
- **Ověřuje se to bez dozu** — chování je stejné: odejít z aplikace a vrátit se (v logu musí být
  „Návrat do popředí, obnovuji spojení"), zapnout a vypnout letový režim (`adb shell cmd
  connectivity airplane-mode enable`), otočit displej (v logu nesmí být NIC).

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
  už patřil Chatům — rail hlásil Chaty, na ploše bylo „Obecné". Shell se ptá na vybranou
  sekci aplikace; klíčem je i velikost zásobníku Nastavení kvůli ťuknutí na už vybrané
  Nastavení (návrat na kořen). **Obecně: efekt, který zapisuje do zásobníku, nepatří do
  obrazovky, která může být zrovna na odchodu.**
- **Šipka zpět je v sekci jen na compactu.** V dvoupanelu je seznam sekcí pořád vidět vedle
  a sekce se NAHRAZUJE, nevrší se zásobník — stejně jako konverzace.
- **Spodní bar se v sekci nastavení NESKRÝVÁ**, na rozdíl od konverzace. Sekce je mělká obrazovka
  v téže sekci aplikace; ťuknutí na „Nastavení" v baru z ní vede zpátky na seznam sekcí.
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
  předat i do `ListItemDefaults.colors`.
- **Pozor na názvy barev `ListItemDefaults.colors`**: `contentColor` (ne `headlineColor`)
  a `supportingContentColor` (ne `supportingColor`). Hádaný název se pozná až překladem.

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
- **Mimo kompozici se čte `getString(…)`, což je `suspend`.** Tudy jdou popisy typů zpráv
  (`previewText`, `serviceText`), notifikace a schránka — kvůli tomu je řada mapovacích funkcí
  v `core/telegram` `suspend`. Náhled poslední zprávy se proto skládá **před** `edit` nad stavem
  seznamu chatů, ne uvnitř.
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
- **Ověřuje se přepnutím jazyka aplikace**, ne celého systému:
  `adb shell cmd locale set-app-locales eu.tintera.plume --locales cs-CZ` (prázdná hodnota vrátí
  systémový). Restart aplikace stačí, emulátor rebootovat netřeba.

## Navigace

Navigation3 (`rememberNavBackStack` + `NavDisplay` + `entryProvider`). Klíče jsou v
`core/navigation/Routes.kt` a jsou `@Serializable` — bez toho by se zásobník nepřežil zabití procesu.

Seznam chatů a konverzace tvoří **list-detail** dvojici přes `rememberListDetailSceneStrategy`:
`ChatsRoute` nese metadata `listPane(detailPlaceholder = …)`, `ConversationRoute` má `detailPane()`.
`directive` se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna — vlevo ukusuje
rail a scaffold by jinak rozhodl špatně.

**Rail má výchozí insety a položky na středu.** Nulové insety (`WindowInsets()`) ho lepily pod
stavovou lištu. Střed doporučuje M3 pro tablety (líp se na něj dosáhne); nahoru patří jen menu
a FAB, které rail nemá. Až přibudou, půjdou do `header`, který je nahoře vždy.

**Na nízkém okně (telefon naležato) má rail jen ikony** — výška pod `HEIGHT_DP_MEDIUM_LOWER_BOUND`.
Bez popisku nese název sekce `contentDescription` ikony. **Je to klasický `NavigationRail`
(80 dp), ne `WideNavigationRail`:** zúžený wide rail má pevných 96 dp bez ohledu na popisky
a položku bez popisku měří na šířku indikátoru a klade na x = 0 — ikony visely u levého okraje
a vpravo zbyl prázdný pruh. Popisky tedy šířku neovlivňovaly, jen výšku.
Seznam chatů vedle něj má naležato levý okraj 8 dp místo 16 (`isShortWindow`): rail má kolem
ikon vlastní vzduch a 8 dp je vnitřní okraj vyhledávací pilulky, takže karty s ní lícují.

**Boční insety NEVĚDÍ, kde prvek leží.** Compose dá `safeDrawing`/`displayCutout` každému, kdo si
o ně řekne, i když stojí daleko od kamery. Telefon naležato proto měl v konverzaci (panel VPRAVO)
mezeru o šířku výřezu vlevo — v hlavičce a seznamu zpráv, ne ve vstupním poli, které si boky
nebralo vůbec. Pravidlo: kdo inset vyřešil, ten ho **zkonzumuje** (`consumeWindowInsets`).
Shell konzumuje levý inset za rail, seznam v dvoupanelu pravý (`listPaneInsets`) — ten patří
panelu vpravo. Vstupní pole bere `safeDrawing` bokem i spodkem, jinak by odeslání zajelo pod
výřez nebo boční třítlačítkovou lištu. Ověřuje se v OBOU rotacích naležato
(`adb shell cmd window user-rotation lock 1` / `3`, pak `free`) — výřez je pokaždé na jiné straně.

**`LazyColumn` ořezává jen ve směru scrollu.** Vodorovně kreslí i mimo své hranice, takže sloupec
časů schovaný „za pravým okrajem" (odkrývání tahem) byl schovaný jen dokud seznam končil
u okraje obrazovky. S insetem vpravo prosvítal; proto `clipToBounds` na ploše seznamu.

**Spodní bar se v konverzaci skrývá — jen na compactu.** Na telefonu zabírá konverzace celou
obrazovku a sekční navigace tam nedává smysl; ven se chodí zpět, ne přepnutím sekce. Na širokém
rozvržení je konverzace jen panel vedle seznamu, takže rail zůstává. Past: `consumeWindowInsets`
spodního insetu musí být podmíněné stejnou podmínkou jako bar — když se konzumuje i se skrytým
barem, vstupní pole konverzace zaleze pod systémovou lištu.

**Skrytí baru musí animovat i jeho MÍSTO, ne jen posun.** `slideOutVertically` obsah jenom
posune — jeho slot v layoutu drží po celou animaci. Vstupní pole konverzace proto viselo nad
prázdným pruhem a doskočilo dolů až na konci. Řeší to `shrinkVertically` (a `expandVertically`
na druhou stranu) přidané k posunu: zmenšuje se i to místo, takže obsah roste současně
s odjezdem baru.

**Avatar a název letí ze seznamu do konverzace.** Klíče `chatAvatarKey` / `chatTitleKey`
obsahují id chatu, jinak by se při rychlém přepnutí spároval avatar jednoho chatu s druhým.
Na rozdíl od letu odeslané zprávy se tu viditelnost neřídí ručně — obě strany jsou
v `AnimatedContent` NavDisplaye, takže stačí `LocalNavAnimatedContentScope`.

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

**V dvoupanelu se nesdílí nic** (`LocalTwoPane`, poskytuje ho shell). Seznam vedle konverzace
zůstává vidět, takže by avatar odletěl z položky, která je pořád na obrazovce, a o jeden klíč
by se přetahovaly dva prvky naráz.

**Text se sdílí přes `sharedBounds` + `scaleToBounds`, ne přes `sharedElement`.** Název chatu má
v hlavičce jinou velikost písma; se `sharedElement` by se text na konci letu přeskládal skokem.

**Predictive back dělá `NavDisplay` sám** (přes `androidx.navigationevent`): při tažení zpět se
konverzace zmenší a odsune a pod ní se odkryje živý seznam. Nic se pro to nezapíná —
`android:enableOnBackInvokedCallback` systém při targetSdk 35+ ignoruje a je v manifestu jen
jako výslovné prohlášení.

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

### Hlavička: vyhledávací pilulka

Seznam chatů nemá nadpis, ale `AppBarWithSearch` (`ChatSearchBar.kt`): pilulka „Hledat
v chatech", vedle ní menu ⋮. Po ťuknutí se rozbalí `ExpandedFullScreenSearchBar` s výsledky.

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
- **Stav spojení je tonální pilulka na konci hledání** (`ConnectionPill`), ne nadpis. Skoro
  celou dobu není vidět nic; „Chaty" v nadpisu jen opakovalo navigaci. V rozbaleném hledání se
  neukazuje.
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
- **Ukázka zprávy začíná kousek před nalezeným slovem** (`snippetAround`, „…" na začátku).
  Ve dvou řádcích by slovo z konce dlouhé zprávy bylo za výpustkou a řádek by nevysvětlil,
  proč se našel. Slovo jen v adrese skrytého odkazu vidět nebude — text ji nenese.
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

- **Vlastní název a záložka místo jména a fotky účtu** (`chatTitle`, `Avatar(isSavedMessages =
  true)`). Rozpoznává se podle `ChatRepository.savedMessagesChatId`, do UI jde přes
  `LocalSavedMessagesChatId` (poskytuje shell). **`Avatar` si to NEzjišťuje sám** — kreslí
  i odesílatele (reakce, zhlédnutí, profilovky ve skupině) a tam má být vlastní tvář.
- **V seznamu chatů nejsou, pokud nejsou připnuté** (`chatGroups`, test
  `ulozeneZpravyJenPripnute`). Vstup je menu ⋮ v horní liště (vedle archivu). Připnutí
  je výslovné přání uživatele, takže připnuté zůstávají — schovat je by působilo jako chyba.
- **Ve výběrech (přeposlání, sdílení, iOS rozšíření, Direct Share) jsou vždy první**, i když
  v seznamu chatů nejsou. V `ChatPicker` jako samostatná karta v `primaryContainer` nad
  seznamem, ne jeho první řádek — v řadě chatů by nejčastější cíl splynul s ostatními. „Pošlu si to" je nejčastější cíl. Hledání je najde podle přeloženého
  názvu.
- **Chat se zakládá hned po přihlášení** (`createPrivateChat`, lokální). U účtu, který si nic
  neuložil, ho TDLib v databázi nemá a nešlo by ho otevřít ani do něj přeposlat.
- **V hlavičce není „online"** — vlastní dostupnost v Uložených zprávách nic neříká.
- **Zkratka konverzace a notifikace** (připomínka v Uložených zprávách) mají název
  „Uložené zprávy" a záložku (`bookmarkIcon`), ne jméno a fotku účtu.
- **Témata Uložených zpráv (`SavedMessagesTopic`, dělení podle původního chatu) a štítky zatím
  nejsou.** Konverzace ukazuje všechny uložené zprávy dohromady, jako dřív.

### Nová konverzace a kontakty

`NewChatScreen` (`features/chats`), `NewChatRoute`, vstup FAB s tužkou v seznamu chatů.

- **M3 Expressive Extended FAB napojený na scroll.** Na začátku seznamu nebo při scrollu nahoru
  je rozbalený s textem „Nová zpráva", při scrollu dolů se plynule sbalí do kompaktní ikony,
  aby nezakrýval řádky chatů, a po zastavení scrollu zůstává sbalený až do dalšího scrollu nahoru (dle M3 guidelines).
- **Kontakty jdou z `getContacts`** a pro každý se lokálně zavolá `createPrivateChat(userId, force = false)`
  — TDLib tak pro kontakt založí chat objekt (lokálně v paměti/databázi, bez odesílání zpráv)
  a do seznamu se dostane kompletní `ChatSummary` s avatarem i dostupností (`withPresence()`).
- **Uložené zprávy jsou i tady nahoře**, jako samostatná karta v `primaryContainer`.
- **Vyhledávání kombinuje tři vrstvy:** lokální kontakty (filtrace podle jména), existující známé chaty
  z `searchChats` a veřejné Telegram uživatele / skupiny z `searchPublicChats` (podle `@username`).
- **List panel na dvoupanelovém rozvržení:** Na tabletu / TwoPane nahradí levý panel stejně jako
  Archiv, po výběru kontaktu se zavře a v pravém panelu se otevře konverzace.

### Co položka ukazuje

Čas poslední zprávy, ztlumení, dostupnost protějšku a jeho právě probíhající činnost.

- **Čas se s věkem zjednodušuje** (`asRelativeTime`): dnes hodiny, včera slovem, do týdne zkratka
  dne, pak datum. V seznamu jde o STÁŘÍ zprávy, ne o pořadí během dne — „14:32" u zprávy z loňska
  nic neřekne. V konverzaci se naopak vypisují jen hodiny, protože tam jdou zprávy chronologicky
  za sebou. Tentýž formát používá i „naposledy viděn".
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

Archivované chaty jsou vlastní seznam (`ArchiveRoute`, `ArchiveScreen`), vstup je menu ⋮ vedle
hledání. Do archivu a zpátky se chat dostane z nabídky po dlouhém stisku; místo potvrzení je
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
- **Stav se po akci nepřepisuje lokálně.** U reakcí v konverzaci to smysl dávalo (mezi ťuknutím
  a potvrzením je vidět prodleva), tady se položka jen přeskládá — a předběhnout server by
  znamenalo riskovat, že se seznam rozejde se skutečností. Potvrzení chodí updaty
  (`chatNotificationSettingsUpdates`, `chatIsMarkedAsUnreadUpdates`, `chatPositionUpdates`).
- **Limit připnutých chatů je serverový** (`pinned_chat_count_max`, Premium ho zvedá). Při
  překročení vrátí TDLib chybu, která MUSÍ jít vidět — jinak to vypadá, že se nic nestalo.
  Proto má seznam chatů `SnackbarHost`.
- **`Card(onClick = …)` umí jen krátké ťuknutí.** Dlouhý stisk potřebuje `combinedClickable`,
  takže se z karty stal `Surface` s vlastním klikáním.

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
  půl obrazovky. Že jich je víc, říká svislý ukazatel vlevo (dílek na zprávu, u víc než čtyř
  okno kolem aktuální) a titulek „Připnutá zpráva č. N" — tak to dělá i oficiální klient.
- **„Poslední připnutá" = nejnovější ZPRÁVA, ne poslední připnutí.** Čas připnutí Telegram
  neposílá vůbec; pořadí je podle id zprávy a `getChatPinnedMessage` vrací taky tu s nejvyšším
  id. U kanálu, který připíná čerstvé příspěvky, je to totéž; připne-li někdo starou zprávu,
  zařadí se podle svého stáří.
- **Ukázaná zpráva se drží podle id, ne indexu** (`pinnedIndex`, `nextPinnedId`, testy
  `PinnedMessagesBarTest`). Nová připnutá by jinak posunula index a pruh by přeskočil na jinou.
  Odepnutá ukázaná spadne na nejnovější.
- **Křížek v pruhu je jen u jediné připnuté.** U víc zpráv je vpravo seznam a odepíná se v něm —
  křížek vedle jedné z mnoha svádí k domněnce, že zavře celý pruh.
- **Sheet se po odepnutí nezavírá** (odepíná se často víc zpráv po sobě), po ťuknutí na řádek
  ano — napřed se schová, pak se skáče.
- **Pruh je plovoucí karta** s okrajem, zaoblením a drobným stínem, ne plocha přes celou šířku:
  zprávy pod ní zajíždějí a karta se od nich odlepí sama. Fotka, video a soubor s náhledem mají
  v řádku čtvereček (nejmenší varianta, ostrou kvůli 36 dp nestahovat).
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

### Hledání v konverzaci

Lupa v hlavičce chatu přepne hlavičku na pole a místo vstupního pole ukáže řádek „N z M" se
šipkami (`InChatSearchTopBar`, `InChatSearchNavigator`). Stav je `ConversationState.search`
(`InChatSearch`, čistá data a přechody, testy `InChatSearchTest`); `null` = zavřené.

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
- **Jednotlivé druhy obsahu zatím nerozlišujeme.** Skupina, kde smí jen text (bez fotek,
  samolepek, hlasovek), má pole celé; zakázaný druh skončí chybou po odeslání.

### „Píše…" v konverzaci

Hlavička ukazuje činnost protějšku (ve skupině kohokoli) místo dostupnosti a protějšek naopak
vidí, že píšeme my.

- **Činnost se bere ze `ChatRepository`, ne z vlastního odběru.** Seznam chatů ji už sleduje
  i s odpočtem zhasnutí a odfiltrováním vlastní činnosti; druhá kopie téže logiky by se s ní
  rozešla. Čte se přes `ChatRepository.activity(chatId)`, které nezáleží na složce — viz „Archiv".
- **Činnost má v podtitulu přednost před dostupností** a výšku hlavičky animuje
  `AnimatedContent` — skupina podtitul jinak nemá a název by při „píše…" poskočil.
- **Vlastní „píšu" se opakuje nejvýš jednou za 5 s** (`TYPING_REPEAT`). Server činnost zhasíná
  asi po šesti vteřinách; posílat ji na každý úhoz by byl dotaz na znak.
- **Vymazání textu psaní výslovně ukončí** (`ChatActionCancel`), odeslání ne — to ukončí server
  sám. Po odeslání se jen zapomene čas posledního ohlášení, aby další zpráva ohlásila psaní hned.
- **Hlásí se jen ruční úpravy textu.** Vyprázdnění pole po odeslání jde mimo `onValueChange`,
  takže by se o něm `onDraftChanged` stejně nedozvěděl.
- **Chyba se jen loguje.** V kanálu bez práva psát `sendChatAction` selže; snackbar kvůli
  „píše…" by uživatele jen obtěžoval.

### Úprava zprávy

„Upravit" v nabídce nad zprávou přepne vstupní pole do úpravy: karta místo citace ukazuje původní
text, tlačítko je fajfka a potvrzení volá `editMessageText`.

- **Nenabízí se u zpráv s ručním formátováním** (`isEditableAsPlainText`). Pole umí jen holý
  text — tučné, kurzíva, spoiler nebo odkaz s vlastním popiskem by úprava tiše smazala.
  Automatické odkazy se poznají tak, že jejich text v adrese opravdu stojí; ty se při uložení
  dohledají znovu (`getTextEntities`, stejně jako při odeslání).
- **Nenabízí se ani u médií** — popisek fotky je jiné volání (`editMessageCaption`).
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
- **Tvar jde přes `graphicsLayer { shape; clip }`, ne `Modifier.clip`.** Fáze se mění každý
  snímek a `WavyShape` je pokaždé nový objekt; ve vrstvě to přepočítá jen obrys, v `clip` by to
  rekomponovalo zprávu. Vlnek je po obvodu celý počet, jinak by na spoji byl zub.
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
  Stisk mění jen kruh (na zaoblený čtverec) — mezi obecnými tvary `IconButton` neanimuje. Tvar (`MorphShape`) je nový objekt pro každou hodnotu průběhu — Compose
  si obrys drží podle instance, takže tvar čtoucí průběh sám by se nepřekreslil.
- **Morf má vlastní, pomalejší pružinu** (`morphSpring`, tuhost 70 místo ~380 u
  `defaultSpatialSpec`). S výchozí doběhl na skutečném telefonu za zlomek vteřiny a nebyl vidět —
  na emulátoru v debugu ano, ten je pomalejší, takže **rychlost animací se posuzuje na zařízení**.
  Toutéž pružinou jede výměna ikony a barva má `slowEffectsSpec`, aby nic nedoběhlo dřív než tvar.
- **Klidová sušenka je vlastní, ne `Cookie9Sided`**: šest vlnek s vnitřním poloměrem 0,7 místo
  devíti s 0,8. S mělkými vlnkami se tvar od kruhu skoro nelišil.
- **Tvary M3 Expressive mají v aplikaci pevné významy** (`ShapeMorphs` v `core/ui/MorphShape.kt`):
  šestistranná sušenka = klid / připraveno (mikrofon, pozastavená hlasovka i video), kruh = probíhající akce
  (odeslat, hraje), SoftBurst = živý signál (tlačítko při nahrávání dýchá, tečka v pruhu už
  nebliká — dva blikající prvky vedle sebe byly šum), dvanáctistranná sušenka = podbarvení
  podržené samolepky — i vlnka stisku je ořezaná do téže sušenky (klikání má vlastní vrstvu,
  samolepka sama ořezaná není, přišla by o rohy), sled tvarů končící zaoblenou hvězdou =
  uvítání prázdného stavu (placeholder detailu, jednou a pak stojí). Kde se stav nemění (avatary, položky seznamu, reakce), se tvary
  nepoužívají: kdyby se hýbalo všechno, přestalo by to cokoli znamenat.
- **Barva odesílacího tlačítka říká, jak silná akce to je**: mikrofon tonální
  (`secondaryContainer`), odeslání i uložení úpravy plná `primary`. Prolíná se spolu s morfem.
  Tři různé barvy pro tři ikony by z tlačítka udělaly semafor.
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
  (56 − 48) / 2, tedy střed jednořádkového pole. Barvu mají `primary` — výchozí
  `onSurfaceVariant` byla na `surfaceContainerHigh` karty zašedlá a nečitelná.
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
- **Nabídka příloh není `DropdownMenu`** (`AttachButton`). M3 menu drží od okraje okna povinný
  odstup 48 dp (`MenuVerticalMargin`, zahrnuto i do volby „spodkem ke kotvě"), a tlačítko
  u spodku obrazovky v tom pásu leží — menu se proto posadilo k oknu, zhruba 45 dp nad tlačítko.
  `offset` nepomůže, přičítá se ke kandidátům PŘED kontrolou. Vlastní `Popup` s pozicí nad
  kotvou; vzhled z `MenuDefaults`, položky standardní `DropdownMenuItem`. **Totéž čeká každé
  `DropdownMenu` u spodku obrazovky.** Poznalo se to změřením (`uiautomator dump`): spodek menu
  byl přesně `výška okna − 48 dp`, ne horní hrana kotvy.
- **Citace a psaní jsou JEDNA karta.** Dva samostatné kusy nad sebou dělaly z odpovědi cizí prvek
  vsunutý mezi konverzaci a vstup; ve společné kartě je vidět, že citace patří k tomu, co se
  právě píše. Citace (i hlavička úpravy) je **horní pruh karty v `tertiaryContainer`**
  (`ComposerHeader`), stejně jako citace na kartě zprávy — ne subkartička v kartě. Pole uvnitř je proto průhledné — kontejner nese karta, ne ono, a dvě zaoblené
  plochy v sobě by vypadaly jako chyba.
- **Pruh pod kartou má barvu POZADÍ, jen průsvitnou.** Zprávy pod něj zajíždějí, takže by se bez
  něj pod kartou míchaly s textem; plná barva by zase vrátila ostrý předěl přes celou obrazovku,
  kvůli kterému se `bottomBar` rušil. Průsvit dělá závoj: je vidět, že tam obsah je, ale nepřebíjí
  psaní.

### Formátování textu (entity)

Telegram **markup neposílá**. Text zprávy je holý řetězec a formát je seznam rozsahů nad ním
(`FormattedText.entities`). Kdo je zahodí, dostane neklikatelný odkaz a plochý text — přesně tak
to tu bylo, než vznikly `MessageEntity` a `RichText`.

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
- **Příkazy botů a cashtagy se schválně nemapují.** Obarvit je umíme, ale kliknutí by nemělo
  kam vést — modrý text, po kterém se nic nestane, je horší než text černý.
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
- **Zmínka a hashtag nejsou podtržené — kromě odchozí bubliny.** Podtržení patří adresám, které
  z aplikace odvedou. V odchozí bublině je ale barva odkazu barva textu (na `primary` jiná
  nejde), takže tam se podtrhávají, jinak by nebyly k poznání (`linkColor == overlayColor`).
- **Spoiler musí být opravdu nečitelný**: průhledný text na plné ploše, ne jen jiná barva.
  Odkrývá se ťuknutím přes `LinkAnnotation.Clickable` — `Modifier.clickable` by platilo pro celou
  zprávu, ne pro skrytý úsek. Odkrývá se celá zpráva naráz; dohledávat spoilery po kouskách
  je otrava.
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

### Profilovky ve skupině

Ve skupině má cizí zpráva vlevo tvář odesílatele, aby se odesílatelé nerozlišovali jen jménem.

- **Tvář je u POSLEDNÍ zprávy bloku**, ostatní zprávy bloku mají na jejím místě mezeru
  (`SenderAvatar.Spacer`). Bez mezery by bubliny téhož člověka skákaly do stran.
- **Poslední = novější soused je o index NÍŽ** (seznam jde od nejnovější). Blok končí jiným
  odesílatelem, jinou stranou, jiným dnem nebo servisním oznámením.
- **Kanál tvář nemá** (odesílatel = chat sám): u každého příspěvku by byla tatáž a nese ji
  hlavička.
- **Jméno i tvář jdou z jednoho `resolveSender`** (s cache), ne ze dvou dotazů.
- **Tvář je mimo posouvaný `Box`** — při tažení na odpověď jede jen bublina.
- **Zarovnaná ke spodní hraně bubliny.** U dlouhé zprávy by uprostřed nebylo poznat, kam patří.

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
- **Náhled nad textem (`showAboveText`) se zatím ignoruje** — kreslí se vždycky pod ním.
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
- **iOS: `UIDocumentInteractionController`** (náhled, a z něj i sdílení). Kontroler i delegát se
  musí držet silnou referencí, dokud je náhled na obrazovce — stejná past jako u výběru příloh.
  Bez náhledu pro daný typ se ukáže nabídka „Otevřít v…".
- **Když soubor nemá čím otevřít, musí to jít vidět.** `FileOpener.open` vrací `false`
  a konverzace na to má snackbar; jinak by ťuknutí vypadalo, že nic neudělalo.
- **Obrázek a video poslané jako soubor mají v řádku náhled** místo ikony (`DocumentThumbnail`),
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

### Přeposílání

„Přeposlat" v nabídce nad zprávou otevře výběr cílového chatu (`ForwardRoute`, vlastní obrazovka);
po odeslání se otevře cílový chat, takže je vidět, že zpráva dorazila.

- **Nabízí se podle `canBeForwarded`** z `getMessageProperties`, jako ostatní položky nabídky.
  Kanál s chráněným obsahem přeposílání zakazuje a nabídnout akci, která skončí chybou, je horší
  než ji nenabídnout.
- **Album se přeposílá celé** (`albumMemberIds`, stejně jako mazání) — v bublině je to jedna věc
  a v cíli má albem zůstat. **Id musí být vzestupně**, jinak TDLib volání odmítne.
- **Výběr je obrazovka, ne sheet.** Seznam chatů je dlouhý a listuje se v něm; sheet by navíc
  při hledání zápasil s klávesnicí.
- **Bez dotazu je ve výběru živý hlavní seznam, s dotazem výsledky `ChatSearch`** — ze všech
  chatů, i archivovaných (`pickerChats`, viz „Hledání chatů").
- **Během odesílání se seznam nedá ťuknout podruhé** — dvě ťuknutí by poslala zprávu dvakrát.
- **Posílá se jako přeposlání, ne kopie** (`sendCopy = false`), takže v cíli zůstane „Přeposláno
  od …". Kopie bez odesílatele je v Telegramu samostatná volba a zatím ji nenabízíme.
- **Cílový chat se do zásobníku přidá jen, když na něm zásobník už nestojí** — přeposlání do
  téhož chatu by jinak otevřelo druhou kopii konverzace.

### Odkrývání časů tahem

Tah doleva posune celou plochu zpráv a vpravo odkryje čas u každé z nich; po puštění se plocha
vrátí. V bublině čas není (viz „Čas a stav doručení"), takže tohle je vedle předělu dne a plovoucího
data jediné místo, kde se dá přečíst.

- **Je to gesto nad SEZNAMEM, ne nad položkou.** Čas u jedné zprávy neřekne, kdy přišly ty okolo,
  a u jediné bubliny by to vypadalo jako druhá polovina swipu na odpověď. Jede proto všechno
  najednou, včetně sponzorované karty — ta jen nemá co ukázat.
- **Bublina si proto nesmí vzít každý vodorovný tah.** `draggable` je po slopu směrově agnostický:
  vezme si i tah doleva, nikam ho nepromítne (posun je `coerceIn(0f, maxDrag)`) a rodiči už ho
  nepustí. Tah doleva se tím tiše zahazoval. Odpověď swipem proto jede na **vlastním detektoru**,
  který rozhodne jednou za gesto: doprava spotřebuje, doleva nespotřebuje nic.
- **Nespotřebovaný pohyb se neztratí.** `awaitHorizontalTouchSlopOrCancellation` po nespotřebování
  počítá slop dál, takže gesto propadne k detektoru nad seznamem. Jakmile si ho vezme on, další
  událost přijde jako spotřebovaná a detektor bubliny sám skončí.
- **Svislý scroll má přednost a detektor se rozhoduje pomalu** (`detectTimeReveal`, pravidlo
  `timeRevealDecision`, testy `TimeRevealGestureTest`). Seznam je potomek, dostane událost dřív
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

- **`draggable` s vodorovnou orientací, ne `detectHorizontalDragGestures`.** Ten konzumuje od
  prvního pixelu a rozbil by svislý scroll seznamu. S `draggable` rozhodne o směru až první pohyb
  za slop, takže si `LazyColumn` svislé tahy nechá.
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
- **Údaje a členové jsou `SettingsGroup`** z Nastavení: pro „seznam vlastností" má aplikace jeden
  vzhled. Akce jsou tónované dlaždice s ikonou nad popiskem; popisek říká, co se stane
  (Ztlumit / Zapnout zvuk), ne stav.
- **Hledat zavře profil a otevře hledání v konverzaci** přes `ConversationSearchRequests` — STAV,
  ne událost (jako `ChatOpenRequests`): konverzace pod profilem si požadavek vyzvedne, až ho
  odebírá. Hledání patří konverzaci, profil by ho jen zdvojil.
- **Popis skupiny chodí jako holý text** — odkazy a zmínky v něm dohledá `getTextEntities`
  (lokálně), aby byly klikatelné jako v bio (to je `FormattedText` rovnou). Bot bio nemá, bere se
  `botInfo.shortDescription`.
- **Členové jen tam, kde je server vydá** (`canListMembers`: malá skupina vždy, supergroup podle
  `canGetMembers`, kanál ne). Vlastník a správci napřed, nejvýš `MAX_PROFILE_MEMBERS`; smazané
  účty (bez jména) a kdo odešel se vynechají. Ťuknutí na člena otevře soukromý chat.
- **Uložené zprávy profil nemají** — hlavička v nich není klikatelná.
- **Počty se oddělují po tisících úzkou nezlomitelnou mezerou** (`Int.grouped()` v `core/ui`) —
  „10704668 odběratelů" se nedalo přečíst. Commonmain `NumberFormat` nemá; mezera je čitelná
  v češtině i angličtině. Plurály proto berou číslo jako `%1$s`.
- **Sdílená média (Média / Soubory / Odkazy) zatím nejsou** — další krok, viz roadmapa.

## Hlasovky

Přehrávají se přímo v bublině: tlačítko, waveform s průběhem (ťuknutí a tažení posouvá), čas
a tečka u neposlechnuté.

- **Hraje nejvýš jedna, pro celou aplikaci** (`VoicePlayer`, `single` v Koinu). Spuštění jiné
  tu předchozí zastaví. Zavření chatu hlasovku z něj zastaví (`onCleared`) — neměla by kde
  ukazovat průběh ani jak ji vypnout.
- **Soubor se stahuje až při prvním přehrání.** Dopředu by to byla data za každou hlasovku
  v historii. Klíč je `chatId` + id zprávy — id zprávy je jedinečné jen v chatu.
- **Pozice je samostatný tok, ne součást `ConversationState`**, a do bubliny jde jako lambda,
  která se čte až ve `VoiceNoteContent`. Mění se dvacetkrát za vteřinu (`POSITION_TICK_MS`);
  v kompozici obrazovky by překreslovala celou konverzaci.
- **Waveform od Telegramu je po 5 bitech** (hodnoty 0–31, od nejnižšího bitu) — `decodeWaveform`,
  testy v `AudioFormatsTest`. Do bubliny se převzorkuje na 40 sloupců a znormuje podle špičky.
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

Prázdné pole bez příloh má místo odeslání **mikrofon**. Ťuknutí spustí nahrávání (karta ukáže
pruh s košem, blikající tečkou, časem a živými úrovněmi), odesílací tlačítko ho ukončí a pošle.

- **Ťuknutím, ne podržením.** Podržení s tažením na zrušení/zamčení je v Compose křehké gesto nad
  tlačítkem, které se během něj mění; ťuknutí funguje i s čtečkou obrazovky. Kratší než vteřina
  se zahodí (`MIN_DURATION_MS`) s hláškou.
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
- **Nahrávač je `factory`, ne `single`** — patří obrazovce. Odchod z chatu nahrávku zahodí,
  spuštění nahrávání zastaví hrající hlasovku (nahrávala by se z reproduktoru).
- **Odesílá se jako samolepka** (`sendMedia`): bez letu, rozepsaný text zůstává, citace se použije.

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
  vykreslí přes celou plochu i na širokém rozvržení. **Rail se v něm schová** (`showRail`
  v `AppShell`, i s místem přes `shrinkHorizontally`): není to panel vedle seznamu, ze kterého
  se přepíná sekce, a rail by jen ukusoval šířku fotce a sousedním stránkám.
- **Ořez pozadí je nutný** (`clipToBounds` v `AmbientBackground`). Pozadí sousedů leží posunuté
  mimo plochu prohlížeče; bez ořezu prosvítalo vlevo přes rail nerozostřené — rozostření pokrývá
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
  Šířka se bere z `BoxWithConstraints`, ne z okna: vedle prohlížeče může ležet rail.
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
- **Reakce, přeposlání a smazání jsou přímo v prohlížeči.** Nabízené reakce a práva
  (`messageActions`) se načítají **pro stránku, na které uživatel stojí**, jednou za zprávu —
  stejný důvod jako u nabídky v konverzaci. Dokud nedorazí, akce se nekreslí. Smaže se jen
  zobrazená fotka, i z alba; ze seznamu ji odebere až `deletedMessages` a po poslední fotce se
  prohlížeč zavře.
- **Let z bubliny jede přes sdílený prvek** (`photoKey`), stejně jako avatar a název chatu.
  Na širokém rozvržení se nekoná: `LocalTwoPane` tam sdílení vypíná.

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
- **Video se stáhne celé, teprve pak hraje** (průběh přes náhled). Rozestavěný soubor z TDLib
  přehrávač nepřečte, index bývá na konci. Streamování (čtení částí přes `readFilePart` / vlastní
  zdroj dat přehrávače) je v roadmapě. Stahovat začne až vybraná stránka; soused složený dopředu ne.
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

## Typy zpráv

TDLib má přes sto podtypů `MessageContent`. Vykreslit umíme hrstku; zbytek musí mít aspoň
pravdivý popis, protože jinak se uživateli tváří jako chyba načítání.

- **Nikdy nepoužívej jedno generické „Zpráva".** Tak to bylo a splývaly pod tím anketa, poloha,
  systémové oznámení i samotné emoji — a hlavně to vypadalo, že se zpráva nenačetla. Poslední
  záchrana je „Tenhle typ zprávy zatím neumíme zobrazit", což říká pravdu.
- **Servisní zprávy poznává `serviceText()`, která zároveň dává jejich text.** Jedno místo
  rozhoduje o textu i o způsobu vykreslení, takže se ty dva nemůžou rozejít. `null` znamená
  „tohle je běžný obsah".
- **Servisní zpráva se kreslí na střed, ne do bubliny.** Bublina patří odesílateli a tyhle
  odesílatele nemají — jsou to oznámení o dění v chatu. Čas se u nich nevypisuje, reakce ani stav
  doručení k nim nepatří.
- **V servisních zprávách se nevypisují jména.** TDLib dává jen id uživatelů a jméno by znamenalo
  dotaz navíc na každou takovou zprávu, často na někoho, kdo v chatu už dávno není. Bezejmenná
  formulace („Do skupiny přibyli členové") je vždycky pravdivá a nic nestojí.
- **`MessageUnsupported` znamená, že klient je starší než server.** Doslovný název typu by
  uživateli neřekl nic, takže dostane stejnou větu jako ostatní neznámé typy.
- **Když typ dostane přílohu s popiskem, `captionText()` pro něj musí vracet prázdno** — jinak
  se popisek v bublině objeví dvakrát. Platí pro video kolečko, hru a platbu.

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
- **Kostka (`MessageDice`) padala do téhož `else`.** Hozená hodnota je součástí animace, takže se
  dopisuje číslem (`🎲 6`) — jinak by ze zprávy zbyl hod bez výsledku. Nula znamená, že server
  výsledek ještě neposlal.
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
  tlačítko „Odebrat sadu" skončilo pod navigační lištou. Spodní inset lišty řeší výchozí
  `BottomSheetDefaults.modalWindowInsets`, na to se sahat nemusí.
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
- **Jen standardní emoji.** Vlastní (placená) emoji jsou samolepky — stahují se jako soubor a mají
  vlastní animaci, takže patří k médiím. Neznámé typy se přeskočí, aby u zprávy nezmizely
  i ty ostatní.
- **Prázdná nabídka není chyba.** V Uložených zprávách Telegram místo reakcí používá tagy a
  `getMessageAvailableReactions` vrátí prázdný seznam. Testovat reakce tam tedy nejde.
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
- **Ťuknutí na čip otevírá DETAIL, nepřepíná reakci.** Přidat i odebrat jde z nabídky nad zprávou,
  kde je vybraná reakce zvýrazněná a ťuknutí na ni ji sundá. Dvě věci na jedno ťuknutí nejdou
  a detail je to, co se jinde nedozvíme.
- **V kanálu detail není** — `getMessageAddedReactions` tam vrátí `BROADCAST_FORBIDDEN`. Čip se
  proto v kanálu nedá ťuknout a nemá ani vlnku: čip, po kterém přijde chyba, je horší než čip,
  který nic nedělá. Rozhoduje o tom `ChatInfo.isChannel`, ne odpověď serveru — ta by přišla až po
  ťuknutí.
- **Detail je jedna stránka** (`REACTION_DETAIL_LIMIT`, 50 řádků). U příspěvku s tisíci reakcemi
  ho nikdo neprojde a stránkování by znamenalo držet offset ve stavu sheetu.

## Odpovědi (reply)

- **`ChatMessage` nese jen `replyToMessageId`, ne celou citovanou zprávu.** V konverzaci se
  odpovídá skoro vždy na něco, co už je načtené, takže náhled si poskládá `ConversationViewModel`
  z vlastního seznamu a ušetří jedno volání na každou odpověď. Zbytek (odpověď na starou zprávu
  hluboko v historii) dotahuje `resolveMissingQuotes()` do `ConversationState.quotes`.
- **Odpovědi mimo tenhle chat se zahazují** (`MessageReplyToMessage.chatId != chatId`) — náhled
  se skládá z načtené historie, a ta je vždy jen z jednoho chatu.
- **`InputMessageReplyToMessage.pollOptionId` je nenullovatelný** — prázdný řetězec, ne `null`.
- **Rozepsaná odpověď se ruší hned při odeslání**, ne až po potvrzení; jinak by při pomalé síti
  šlo odeslat druhou zprávu se stejnou citací.
- **V soukromém chatu nemá citace koho uvést.** `senderName` se vyplňuje jen ve skupinách, takže
  se jako záloha bere název chatu (`ChatInfo.title`).

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
- **Odchod výběru bez volby chatu sdílení zahodí**, a to v `onDispose` obrazovky, ne na šipce.
  Systémové gesto zpět jde přes `NavDisplay`, ne přes obrazovku; se zahazováním jen na šipce
  zůstalo po gestu sdílení viset a výběr by se po obnově aktivity otevřel znovu. `abandon()`
  nezahazuje obsah s vybraným chatem — i po výběru výběr odchází (nahradí ho konverzace).
- **Výběr má ✕, ne šipku zpět, a ✕ i gesto zpět vrací do aplikace, ze které se sdílelo**
  (`rememberReturnToCaller` → `moveTaskToBack`, `closeShare` v `AppShell`). Šipka slibovala
  návrat do Plume, kde by sdílený obsah zmizel bez stopy — bez volby chatu nemá kam jít.
  `singleTask` drží Plume ve vlastní úloze, takže pod ní je právě aplikace, ze které se sdílelo.
  Výběr se napřed odebere ze zásobníku, aby příští otevření Plume nezačínalo ve výběru.
  Bar ani rail k výběru nepatří (`inDetail`, `showRail`) — je to vsuvka odjinud, ne sekce.
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
- **Sdílení přes zkratku nese `EXTRA_SHORTCUT_ID`** a výběr přeskočí: `ShareIntake` rovnou
  `choose(chatId)` a otevře chat přes `ChatOpenRequests`.
- **Chat bez fotky má ve zkratce iniciálu** (`initialIcon`), jinak ukazoval systém prázdné
  kolečko — ve sdílení i ve velkém kruhu notifikace. Barvy ze systémové palety Material You
  (`system_accent1_100/900`): zkratka se kreslí mimo aplikaci, téma aplikace tam není. Paleta je
  od Androidu 12, na starším zůstane ikona prázdná.
- **Seznam s hledáním je společný s přeposíláním** (`ChatPicker`).

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
- **Rozhoduje se z CÍLOVÝCH barev, ne z právě animovaných.** Barva bubliny se při zvýraznění
  prolíná; kdyby se kontrast počítal z okamžité hodnoty, přepnul by akcent uprostřed přechodu
  skokem. Výsledek se pak prolíná spolu s ní (`bubble-accent` v `MessageFrame`).
- **Animace nechávej na `MotionScheme`** (`AppMotion` v `app/root/presentation/theme/Motion.kt`).
  Spatial specy mají jemný bounce (dampingRatio 0.6–0.78), effects specy bounce nemají — overshoot
  u alphy/barvy se ořízne do [0,1] a působí jako bliknutí.
- **`SharedTransitionLayout` je v kořenovém tématu**, ne uvnitř obrazovek — sdílený prvek musí přežít
  výměnu obrazovky. Scope se bere z `LocalSharedTransitionScope`.
- **Ikony = Material Symbols** jako vector drawables v `composeResources`, s vyplněnou i nevyplněnou
  variantou (vybraná položka navigace má vyplněnou). Viz `TopLevelDestination`.
- **Obsah shellu jde přes `movableContentOf`.** Při přepnutí breakpointu rail↔bar se podstrom
  PŘESUNE, ne přerenderuje — jinak by se odmountoval a ztratil scroll i stav.

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

`colorScheme(isDark, dynamic)` je `expect`. Android 12+ vrací systémové dynamic color; `nonDynamicThemeMain`
generuje expressive paletu ze seed barvy přes materialkolor (`theme/ColorScheme.kt`). iOS build tedy
není „Android bez barev", ale má vlastní konzistentní paletu.

## Gotchas

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
