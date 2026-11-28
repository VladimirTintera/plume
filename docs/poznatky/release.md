# Release, R8 a hlášení pádů

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

Desktop (Windows MSI a macOS DMG, `desktop-release.yml`) má vlastní oddíl v `desktop.md`, „Release".

## Release do Play

Vydává se pushnutím tagu `android-<versionName>-<versionCode>`; postup a seznam secrets je
v „Postup a nastavení" níž. Další body jsou to, co se z workflow nepozná.

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
- **Verze, podpis a `verifyReleaseConfiguration` dává plugin `plume.android-release`**
  (telefon i hodinky, viz „Hodinky (Wear OS)" níž). Dřív byly v build skriptu telefonu, kde
  cesta ke keystore musela stát před blokem `android { }` (top-level `val` v `.kts` se
  inicializují v pořadí deklarace); plugin nastavuje hodnoty ve `finalizeDsl` a tu past nemá.
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
- **Snímky hodinek jdou do `play/graphics/<locale>/wearScreenshots/`** (typ API
  `wearScreenshots`), pořadí podle jmen souborů jako u telefonu. Skript je kontroluje už lokálně
  (čtverec, strany 384–3840 px), `play_listing.py --check` to udělá bez klíče a bez knihoven
  Google — chyba by se jinak ukázala až v `edits:validate`. Snímek z emulátoru `Plume_Wear`
  (`adb exec-out screencap -p`) má 454 × 454 a projde. **Dělají se z testovacích účtů**
  (hodinky přihlášené QR kódem z testovacího telefonu), na hodinkách se skutečným účtem by
  seznam chatů ukázal jména lidí. Bez QR kódu přihlášení (nese platný token).
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
- **Barva ikony je z modré aplikace, ne vlastní** (`TOP` #1E88E5 → `BOTTOM` #0B3D86, z odstínu
  seedu `ThemeColor.BLUE` #2AABEE). Ikona a grafika v obchodě zůstaly po změně značkové barvy
  ve fialové z první palety a s aplikací neladily. Gradient jde napříč pozadím adaptivní ikony
  (telefon, hodinky, televize), PNG pro Play a iOS, desktop, banner televize i feature graphic
  (`feature_graphic.py` bere gradient z `icon.py`; světlejší řádky textu mají vlastní tón téže
  barvy). **Přímo seed ne:** světle modrý kruh s krémovým symbolem se blížil oficiálnímu logu
  Telegramu, které ToS zakazuje napodobit. Nahoře je proto sytější tón (L* 56) a dole hluboká
  modrá (L* 26), krémové pírko má kontrast aspoň 3,4.
- **Vektory po přegenerování vrátit, když se v nich nic nemělo změnit.** Jiná verze shapely
  zaokrouhlí `simplify` o setiny jinak a `ic_launcher_foreground.xml`, `ic_notification.xml`
  i `plume_logo_24dp.xml` se v diffu objeví beze změny tvaru.
- **Pírko je baculaté, ne dlouhé a úzké.** První verze byla štíhlá šikmá čárka: do kruhu se
  vepisuje podle NEJDELŠÍHO rozměru, takže úzký tvar zabral zlomek plochy a v notifikaci ve
  stavové liště nebyl skoro vidět. Širší vlajka a kratší stopka zabírají zhruba dvojnásobek.
- **Ikona notifikace se vepisuje podle obdélníku kolem tvaru, ne do kruhu** (`fitted(box =
  True)`, 22 dp z 24). Nic ji neořezává, takže kruh kolem těžiště jen plýtval rohy — šikmé
  pírko je vůči těžišti posunuté a vlevo nahoře zbývalo prázdno. Velikost se porovnává
  s ostatními ikonami ve stavové liště, ne od oka v náhledu.
- **Balíček SDK platformy se jmenuje `platforms;android-37.0`** — u minor verzí je nula součástí
  id. Musí odpovídat `android-compileSdk` v `libs.versions.toml`.
- **Generovaný zdrojový adresář nesmí ležet UVNITŘ výstupu, který úloha deklaruje celý.** AGP
  (`prepareAndroidMainArtProfile`) hledá vedle každého zdrojového adresáře složku
  `baselineProfiles` — u `generated/appLanguages/kotlin` tedy `generated/appLanguages/baselineProfiles`.
  Když `generateAppLanguages` deklarovala jako výstup celou `generated/appLanguages`, padl
  release (`bundleRelease`) na „uses this output of task … without declaring an explicit or
  implicit dependency" — tagy 0.3.64 a 0.3.65 se nevydaly. Debug build ani CI na PR to
  nechytí, úloha běží jen v release. Výstupy se proto jmenují zvlášť (`kotlin/`
  a `languages.txt`), jako má `generateChangelog` rovnou `generated/changelog/kotlin`.
  Ověření bez podpisového klíče: `./gradlew :client:androidApp:bundleRelease -x
  :client:androidApp:verifyReleaseConfiguration`.

### Postup a nastavení

Vydává se **pushnutím tagu** `android-<versionName>-<versionCode>` (např. `android-0.2.1-13`).
Workflow [`android-release.yml`](../../.github/workflows/android-release.yml) z něj vytáhne verzi,
postaví podepsaný AAB, nahraje ho na **internal track** i s textem „Co je nového" (česky
a anglicky, z poznámek v [`release-notes/`](../../release-notes), které přidává každý PR) a založí GitHub Release
s poznámkami a changelogem.
Žádný verzovací commit není potřeba — verze se do buildu dostane přes `-P`.

**Seznam licencí knihoven se nepřegenerovává sám** (`./gradlew exportLicenses`, výsledek
v `files/licenses/` se commituje). Patří do PR, který mění závislosti; zapomenutý znamená
v „Licence knihoven" staré verze, nic víc (`docs/poznatky/nastaveni.md`, „Licence knihoven").

Běžné vydání se spouští z GitHubu: **Actions → „Release — bump & tag" → Run workflow**, vybrat
typ bumpu (`build` / `patch` / `minor` / `major`). Workflow dopočítá verzi z posledního tagu,
tag pushne a release spustí. `versionCode` se zvyšuje vždy — Play si pamatuje každé nahrané
číslo a stejné podruhé nevezme.

#### Co je potřeba nastavit jednou

**1. Service account pro Play Console.** Patří do **vlastního GCP projektu** (např. `mobile-ci`),
ne do Firebase projektu aplikace — do toho potřebuje přístup každý, kdo ladí push notifikace,
a takový člověk by tím zároveň mohl vystavit klíč s právem publikovat do Play. Vlastní projekt
se navíc dá použít i pro další aplikace a přežije případné smazání Firebase projektu.

Postup: v [Google Cloud](https://console.cloud.google.com/) založ projekt (*název* `mobile-ci`,
ID musí být globálně unikátní, takže klidně vygenerované), povol v něm **Google Play Android
Developer API**, vytvoř service account a stáhni jeho **JSON klíč**. Pak v Play Console →
*Users and permissions* → *Invite new users* pozvi e‑mail toho service accountu a dej mu
u aplikace Plume práva **Release → Release apps to testing tracks** (plus *View app information*).

Oprávnění k publikování tedy **nejde přes GCP IAM**, ale přes Play Console; GCP projekt jen
hostuje identitu a má povolené API. Propojení se nastavuje v Play Console → *Setup* → *API access*
— pozor, **linkuje se jeden Cloud projekt na celý developer účet**. Když už tam nějaký připojený
je, je čistší service account založit v něm než link přepisovat.

**2. Secrets v repozitáři** (Settings → Secrets and variables → Actions):

| Secret | Odkud |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -i plume-release.jks \| tr -d '\n' \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | `releaseStorePassword` z `local.properties` |
| `ANDROID_KEY_ALIAS` | `releaseKeyAlias` (`plume`) |
| `ANDROID_KEY_PASSWORD` | `releaseKeyPassword` |
| `TELEGRAM_API_ID` | `telegramApiId` z my.telegram.org |
| `TELEGRAM_API_HASH` | `telegramApiHash` z my.telegram.org |
| `PLAY_SERVICE_ACCOUNT_JSON` | celý obsah JSON klíče service accountu |
| `GOOGLE_SERVICES_JSON` | `gh secret set GOOGLE_SERVICES_JSON < client/androidApp/google-services.json` |

`GOOGLE_SERVICES_JSON` je obsah `client/androidApp/google-services.json` (v repu není, bez secretu
release skončí chybou; viz „Konfigurace Firebase" v `notifikace.md`). Bez `TELEGRAM_API_ID` build **spadne**: zapekla by se nula a appka by se nepřihlásila.

**3. První AAB nahrát ručně.** Dokud aplikace nemá v Play žádný release, androidpublisher API
upload odmítne — první bundle tedy musí do Play Console projít přes web. Postav ho lokálně:

```bash
./gradlew :client:androidApp:bundleRelease -PandroidVersionName=0.2.0 -PandroidVersionCode=12
```

Výsledek je v `client/androidApp/build/outputs/bundle/release/androidApp-release.aab`.
Při nahrání nech zapnuté **Play App Signing** — `plume-release.jks` se tím stane *upload* klíčem
a Play si drží vlastní podpisový. Ten keystore proto **nesmí být ztracen**: bez něj už nejde
nahrát další verze (dá se resetovat přes podporu, ale je to otrava).

**4. Zaznamenat výchozí tag**, aby bump workflow věděl, odkud počítat. Ručně nahraný release
se značí suffixem `-manual`, který release workflow *nespustí*:

```bash
git tag android-0.2.0-12-manual && git push origin android-0.2.0-12-manual
```

Od téhle chvíle už stačí spouštět „Release — bump & tag".

**5. Hodinky (Wear OS)** jdou z téhož tagu na track `wear:internal`, ale jen s repo proměnnou
`WEAR_RELEASE = true` (Settings → Secrets and variables → Actions → Variables). Napřed v Play
Console povol Wear OS (Nastavení → Rozšířená nastavení → Formáty) — bez toho track neexistuje
a upload by shodil i vydání telefonu. Secrets jsou tytéž, hodinky podepisuje tentýž upload klíč.

#### Lokální release build

Podpisové hodnoty a api_id se berou v pořadí **gradle property → ENV → `local.properties`**,
takže lokálně stačí mít v `local.properties`:

```
releaseStoreFile=plume-release.jks
releaseStorePassword=...
releaseKeyAlias=plume
releaseKeyPassword=...
```

`verifyReleaseConfiguration` běží před každým `assembleRelease`/`bundleRelease` a shodí build,
když něco z toho chybí — nepodepsaný AAB by Play stejně nevzal.

### Televize (Android TV)

- **Z téhož tagu do téhož záznamu jako telefon a hodinky**, na track `tv:internal`
  (`android-release.yml`, kroky „TV"). Zapíná je repo proměnná **`TV_RELEASE = true`** — dokud
  formát Android TV není v Play Console povolený (Nastavení → Rozšířená nastavení → Formáty),
  track `tv:internal` neexistuje a upload by shodil i vydání telefonu a hodinek v témže běhu.
- **`versionCode` televize = číslo z tagu + 200 000 000** (hodinky + 100 000 000). Telefon
  bundl televize nedostane (`uses-feature android.software.leanback` povinné), takže vyšší číslo
  nic nezastíní. Posun se po prvním vydání už nedá zmenšit (jako u hodinek).
- **Poznámky jen s `units: tv`** (`release_notes.py play … tv`, adresář `whatsnew-tv`), jinak
  „Drobné opravy a vylepšení". V aplikaci televize „Co je nového" zatím nemá (Nastavení není).
- **`google-services.json` se kopíruje i do `client/tvApp`** — televize ho potřebuje kvůli
  hlášení pádů (Crashlytics), push zatím nemá.
- **Banner do obchodu** (`tvBanner`, přesně 1280 × 720) je v `play/graphics/tvBanner/`, společný
  pro jazyky; generuje ho `play/tools/icon.py tv` spolu s bannerem v launcheru. **Snímky TV**
  (`tvScreenshots`, 16 : 9, aspoň 1280 × 720 — emulátor `Plume_TV` dělá 1920 × 1080) patří do
  `play/graphics/<locale>/tvScreenshots/`; `play_listing.py --check` hlídá rozměry obou předem.
  **Snímky se dělají z testovacího účtu**: seznam chatů a náhled by na skutečném účtu ukázaly
  jména a fotky lidí. Pro interní test snímky potřeba nejsou, pro přihlášení k distribuci na
  Android TV ano (jako u hodinek).
- **Jak vznikly snímky TV (28. 11. 2026):** emulátor s obrazem telefonu (televizní obraz v SDK
  není) má nahoře stavový řádek, který televize nemá. Ořez + zvětšení by rozmazalo text, proto
  `adb shell wm size 1920x1128`: aplikace dostane pod 48px řádkem přesně 1920 × 1080 a snímek se
  jen ořízne o horních 48 px, bez škálování (po focení `wm size reset`). Konverzace je
  s Notifikátorem přes Bot API (album ilustrací krajiny vygenerovaných v Pillow, žádní lidé ani
  značky), odpovědi z TV přes `adb shell input text` — ten neumí diakritiku, české odpovědi jsou
  proto bez ní. Seznam chatů se „uklidil" zprávami do testovacích skupin, Uložených a Pixelu 7
  Pro, aby pod okraj spadl chat Telegram s přihlašovacím kódem a pozvánka v Uložených; kvůli
  chybě se seznamem (roadmapa, Android TV) se fotil po restartu aplikace.
- **Release je minifikovaný (R8)**: APK se třemi ABI 60 MB. Ověřeno 27. 11. 2026 na emulátoru
  (release přepodepsaný ladicím klíčem přes `apksigner`, nainstalovaný `adb install -r -d` přes
  ladicí build — přihlášení zůstalo): seznam chatů, konverzace, hudba s `MediaSession`, video
  za běhu stahování; intent s `CHAT_ID` se v release (ne ladicím) buildu ignoruje.
- **Postup v Play Console**: povolit formát Android TV, nastavit `TV_RELEASE = true`, vydat
  (bundl jde na `tv:internal`), u kanálu interního testování televize zaškrtnout testery (vlastní
  seznam, jako hodinky); pro veřejné vydání pak snímky TV a přihlášení k distribuci na Android TV
  s kontrolou kvality (ovládání D-padem všude, banner, žádná funkce jen na dotyk).

### Hodinky (Wear OS)

- **Z téhož tagu do téhož záznamu**, na track `wear:internal` (`android-release.yml`, kroky
  „Wear"). Zapíná je repo proměnná `WEAR_RELEASE = true` — dokud Wear OS není v Play Console
  povolený (Nastavení → Rozšířená nastavení → Formáty), track `wear:internal` neexistuje
  a upload by shodil i vydání telefonu, který jde v témže běhu.
- **`versionCode` hodinek = číslo z tagu + 100 000 000** (`plumeRelease { versionCodeOffset }`).
  Play nevezme dva bundly se stejným číslem v jednom záznamu, ani pro jiný formát zařízení.
  Vyšší číslo hodinek telefonu nic nezastíní: bundl hodinek má `uses-feature` watch a telefon
  ho nedostane. **Posun už nejde zmenšit**: hodinky jsou v Play od verze 100000138 a každá další
  musí mít vyšší číslo, jinak by se na hodinkách neaktualizovala.
- **Kanál „Interní testování (Wear OS)" má vlastní seznam testerů** — ten z telefonu nepřebírá
  a interní testery u něj je potřeba zaškrtnout zvlášť, jinak si aplikaci na hodinkách nikdo
  nestáhne.
- **Postup v Play Console** (Rozšířená nastavení → Typ zařízení → Wear OS) má tři kroky: snímky
  obrazovky z Wear OS pro všechny záznamy v obchodě, vydání bundlu pro Wear OS v testovacím kanálu
  a nakonec přihlášení k distribuci pro Wear OS se souhlasem se zásadami kontroly. Třetí krok se
  odemkne až po prvních dvou a je potřeba až pro veřejné vydání; interní test na snímcích
  nezávisí. **Snímky se dělají z testovacích účtů** (na hodinkách přihlášených QR kódem
  z testovacího telefonu), ne ze skutečného — seznam chatů by ukázal jména kontaktů.
- **Verze, podpis a `verifyReleaseConfiguration` jsou v pluginu `plume.android-release`**
  (`build-logic/…/AndroidRelease.kt`) pro telefon i hodinky. Hodnoty se nastavují ve
  `finalizeDsl`, tedy až po build skriptu modulu, aby platil jeho `plumeRelease { … }`.
- **Poznámky jen s `units: wear`** (`release_notes.py play … wear`), jinak „Drobné opravy
  a vylepšení". Poznámka bez `units:` platí jen pro plnohodnotné klienty.
- **Release hodinek je minifikovaný** (R8, `isShrinkResources`): APK 93 → 40 MB, AAB 24 MB.
  Hook `TdlibJsonRepair` v minifikovaném dexu R8 vloží přímo za `JsonClient.receive` (místo
  volání `TdlibReceiveHook.afterReceive` je tam jeho tělo), `dexdump` proto jméno hooku
  nenajde — zapojení hlídá kontrola po přihlášení.
- **Zkouška release na emulátoru s `versionCode` z tagu vrací debug build jen downgradem**:
  release `100000999` nad debugem `100000012`. `adb install -r -d` projde, protože emulátor
  má `ro.debuggable=1`; přihlášení zůstane.
- **Ověřeno na emulátoru 2026-10-24** (release přepodepsaný debug klíčem): seznam chatů,
  konverzace, notifikace se dvěma akcemi a Přečteno, dlaždice, obnova po `am kill`. Podpis
  obou AAB týmž upload klíčem (`keytool -printcert -jarfile`).
- **První vydání 2026-10-24** (`android-0.3.100-138`): workflow nahrál bundl hodinek na
  `wear:internal` jako 100000138, „Dostupné interním testerům" na 82 zařízeních. Instalace z Play na
  Pixel Watch pak prošla a aplikace na hodinkách funguje; zpřístupnění trvalo déle než u telefonu
  a hodinky se v Play („instalovat na další zařízení") objevily až po delší době.

## Velikost aplikace

Čísla se přenesla z dřívějšího README a dnes už mohou být jiná.

TDLib je velká nativní knihovna a je to vidět:

| | Velikost |
|---|---|
| Debug APK (všechny 4 ABI) | ~100 MB |
| Release APK arm64 (R8) | **26 MB** |
| `libtdjsonjava.so` na jedno ABI | 15–25 MB |
| iOS debug framework | ~383 MB (nestripovaný) |
| iOS release framework (arm64) | 242 MB → **199 MB** po stripnutí symbolů → **~58 MB** zabalený |

Čísla vypadají hrozivě, ale to, co reálně stahuje uživatel, je poslední sloupec:

- **Android:** AAB s ABI splity — uživatel stáhne jen svoje ABI, tedy ~21 MB nativního kódu místo 84 MB.
  Nic se pro to nemusí konfigurovat, Play to dělá sám z App Bundle. K tomu R8, který dex zmenšil
  z 43,8 MB na 7,2 MB — TDLib generuje tisíce tříd API, které klient nevolá.
- **iOS:** `.ipa` je zip a Xcode při archivaci symboly stripuje. Naměřeno ~58 MB zabaleno; App Store
  aplikuje ještě vlastní kompresi a thinning, takže reálná velikost ke stažení bude kolem toho nebo níž.

Není to malá appka, ale je to v normálu pro klienta s vlastním MTProto stackem — oficiální Telegram
pro iOS se pohybuje v podobných řádech.

## Runnery

Všechny workflowy běží na **hostovaných runnerech GitHubu**, ve veřejném repu zdarma. Výchozí
`runs-on` je hostovaný (`ubuntu-latest`, desktop `macos-latest` a `windows-latest`); repo
proměnné `ANDROID_RUNS_ON`, `RELEASE_TAG_RUNS_ON`, `DESKTOP_MAC_RUNS_ON`
a `DESKTOP_WINDOWS_RUNS_ON` je umí přesměrovat jinam (JSON pole labelů). V `plume` jsou dnes
nastavené na tytéž hostované runnery, takže na výsledku nic nemění.

- **Job `release` v Desktop Release běží na `ubuntu-latest` natvrdo**, ne přes
  `DESKTOP_MAC_RUNS_ON`. Jen stáhne hotové MSI a DMG, podepíše původ, složí changelog a nahraje
  je přes `gh` — nic z toho macOS nepotřebuje. Na macOS zůstal z doby vlastního Macu a na
  hostovaný macOS runner pak čekal přes čtvrt hodiny (desktop-1.2.6), kdežto Ubuntu se chytne hned.

- **Kontrola poznámek na PR (`release-notes-check.yml`) je na hostovaném NATVRDO**, bez repo
  proměnné. Běží na `pull_request`, tedy i na PR z cizího forku, a na self-hosted stroji by tam
  kdokoli pustil vlastní kód. GitHub self-hosted runnery ve veřejném repu nedoporučuje právě
  kvůli tomu. Totéž platí pro každý další workflow na `pull_request` (dnes i Build, viz
  „Kontrola PR").
- **Hostovaný obraz se mění a na jeho obsah se nespoléhá.** Co build potřebuje, si job
  nainstaluje sám (SDK, NDK, CMake přes `setup-android`, JDK přes `setup-java`).
- **macOS runner má 7 GB RAM.** Desktop (jen JVM) se vejde; release link iOS s LTO nad celou
  TDLib by tam narazit mohl (iOS se z CI zatím nevydává).
- **Android SDK si job stáhne sám** (`setup-android` → `~/.android/sdk`; MSI i DMG ho potřebují
  kvůli Android targetu `:client:shared`). Runner nemá `ANDROID_HOME`, takže SDK ze Studia
  (`~/Library/Android/sdk`) zůstává nedotčené.
- **Balíčky SDK, které build potřebuje, vyjmenuje krok `setup-android`** — i ty, které si AGP
  stáhne samo. NDK pro `:ai-llamacpp-android` (ai-engine) AGP stáhlo, CMake ne: první release s llama.cpp
  (android-0.3.124-162) spadl na „[CXX1300] CMake '3.31.0' was not found in SDK". Verze
  `ndk` a `cmake` v `libs.versions.toml` se proto mění spolu s workflow `android-release.yml`
  **i `desktop-release.yml`**: desktop CMake z SDK překládá llama.cpp a NDK chce AGP už při
  konfiguraci Android modulu, i když se z něj nic nestaví.
- **Krok `setup-android` běží i na hostovaném runneru, bez podmínky.** V desktopovém workflow
  měl dřív `if: runner.environment == 'self-hosted'` s tím, že hostovaný obraz SDK má a NDK si
  AGP stáhne. První desktop release z veřejného repa (desktop-1.1.0, `macos-latest` = obraz
  `macos-26-arm64`) spadl při konfiguraci `:ai-engine:ai-llamacpp-android` na
  `LicenceNotAcceptedException … NDK (Side by side) 29.0.13846066`: NDK na obrazu není a stáhnout
  ho AGP bez odsouhlasené licence nesmí. Windows obraz tentýž build prošel. Na předinstalovaný
  obsah hostovaného obrazu se nespoléhat — mění se s každou verzí obrazu.

### Dřív: vlastní runnery (do zveřejnění, listopad 2026)

Dokud bylo repo soukromé, jely releasy na vývojářských strojích (Mac M3 Pro jako `plume-mac`,
Windows jako `…-plume`, registrované ke starému repu `VladimirTintera/telegram`): hostovaný
runner v soukromém repu účtuje každý job jako celou minutu nahoru a macOS desetinásobně —
kontrola poznámek sama v říjnu 2026 spálila 475 účtovaných minut za 70 skutečných. Po
zveřejnění se runnery odregistrovaly a staré repo archivovalo. Návod na registraci a pasti
(LaunchAgent jen v přihlášené session, účet služby na Windows a „dubious ownership" checkoutu,
`gpg` v PATH služby) jsou v historii tohohle souboru.

Ve workflowech z té doby zůstaly kroky podmíněné `runner.environment == 'self-hosted'` (bez
cache `setup-gradle`, bez zápisu do `~/.gradle/gradle.properties`, *Trust workspace for git*
na Windows) a vlastní registr daemonů. Na hostovaném nic nedělají a nechávají otevřenou cestu
zpátky na vlastní stroj proměnnou `*_RUNS_ON` — jen ne pro nic, co běží na `pull_request`.

- **Gradle v jobech běží pod vlastním registrem daemonů** (krok „Isolate Gradle daemons":
  `GRADLE_OPTS` s `-Dorg.gradle.daemon.registry.base=~/.gradle/plume-ci-daemon` do
  `$GITHUB_ENV`); smysl má jen na sdíleném stroji. `gradlew --stop` z lokální práce nebo ze
  sezení agenta (pouští ho po každém buildu) by zastavil i daemon běžícího releasu a ten by
  spadl na „Gradle build daemon has been stopped: stop command received". Jméno registru nese projekt,
  ať se nepotká s CI jiných projektů na týchž strojích. **Nový job s Gradlem na
  self-hosted dostane týž krok.** Na Windows jde cesta přes `cygpath -m`: s
  `MSYS_NO_PATHCONV=1` by Java z `/c/Users/…` udělala `C:\c\Users\…` (ověřeno sondou).
  Kdo daemon zastavil, poznáš z logů v `~/.gradle/daemon/<verze>/`: `Stop` dostanou všechny
  daemony naráz a ten, kterému vteřinu předtím skončil build, nese v `currentDir` cestu toho,
  kdo `--stop` pouští.

## Kontrola PR

Workflow **Build** (`.github/workflows/build.yml`) běží na každém PR do `main` a na pushi do
`main`: `check -x lint` (kontroly zdrojáků `verify*`, překlady, testy commonTest na JVM přes
`androidHostTest` i `jvmTest`, unit testy vstupních bodů), debug APK telefonu i hodinek a jar
desktopu. `pull_request` staví sloučení s aktuálním `main`, takže chytí i dva PR, které
zvlášť projdou a dohromady se nepřeloží.

- **Co NEDĚLÁ:** lint (`-x lint`, nikdy se nepouštěl a stav není známý), release build s R8
  (`bundleRelease`, na něm padly tagy 0.3.64 a 0.3.65, viz „Generovaný zdrojový adresář…"), iOS
  (link na macOS runneru trvá minuty a release s LTO desítky minut). Kandidáti na noční běh.
- **PR jen s dokumentací se nestaví, ale projde.** Rozhoduje malý job `Detect changes` ze
  seznamu souborů PR od GitHub API, bez checkoutu (pár sekund; checkout se submoduly a celou
  historií by sám stál minutu). Když PR mění jen `docs/` (kromě `docs/licenses/`, konfigurace
  AboutLibraries), `release-notes/`, `play/listing/` a `play/graphics/`, `.claude/`, `*.md`
  nebo `LICENSE`, job `Build and test` se přes `if` PŘESKOČÍ. Přeskočený job GitHub počítá
  u povinného checku jako úspěch, kdežto filtr `paths` / `paths-ignore` na úrovni workflow by
  workflow vůbec nespustil a povinný check by navždy visel ve stavu „čeká" — PR by nešel
  sloučit. Překlady (`strings.xml`) se staví, hlídá je `verifyTranslations`. Poznámky
  k vydání čte i `generateChangelog`, ale s fallbackem, takže build nerozbijí. Nová složka,
  která do buildu nevstupuje, patří do vzoru `IGNORED` ve workflow.
- **Keš Gradlu zapisuje jen `main`** (výchozí chování `setup-gradle`), PR ji jen čtou. Proto
  workflow běží i na pushi do `main`; release workflow keš neplní, běží na tagu.
- **Na hostovaném natvrdo**, jako kontrola poznámek: běží i na PR z forku. Secrets nepotřebuje,
  bez `telegramApiId` se projekt přeloží (jen by se nepřihlásil).
- **`Build and test` je povinný check** v klasické ochraně větve `main` (Settings → Branches,
  od 23. 11. 2026), vedle `check` z kontroly poznámek. Kontext je JMÉNO JOBU, ne workflow:
  přejmenování jobu by povinný check nechalo čekat na kontext, který už nikdy nepřijde, a žádný
  PR by nešel sloučit — nejdřív upravit ochranu, pak jméno. Z příkazové řádky:
  `gh api repos/VladimirTintera/plume/branches/main/protection/required_status_checks`.

## Původ buildu

Release workflowy ke každému balíčku vystaví **attestaci původu** (`actions/attest-build-provenance`,
SLSA build provenance): záznam podepsaný přes Sigstore krátkodobým certifikátem z OIDC tokenu
GitHub Actions, že soubor s daným SHA-256 postavil konkrétní workflow z konkrétního commitu
a tagu. Ukládá se k repu (záložka Attestations), ne jako artefakt běhu. Je to odpověď na
otázku, kterou otevřený kód sám nezodpoví: jestli stažený balíček opravdu vznikl z něj.

- **Desktop: MSI a DMG** se attestují v jobu `release` po stažení artefaktů, tedy přesně ty
  bajty, které jdou do GitHub Release. Ověření:
  `gh attestation verify Plume-1.2.3.msi --repo VladimirTintera/plume`.
- **Android a hodinky: AAB** (`plume-*.aab`, oba bundly jedním globem). Bundl se ale nikam
  nepřikládá a **APK z Google Play se ověřit nedá**: Play ho z bundlu skládá po kouscích
  (splity podle zařízení) a přepodepisuje vlastním klíčem (Play App Signing). Attestace AAB
  tak dokládá jen to, co šlo do Play; pro uživatele by musel bundl ležet u releasu, a i pak by
  nešlo porovnat s tím, co má v telefonu. Skutečně ověřitelný Android build chce vlastní APK
  mimo Play (GitHub Releases, F-Droid) — a to Firebase a Crashlytics zatím komplikují.
- **Práva**: workflow potřebuje `id-token: write` (OIDC token pro Sigstore) a
  `attestations: write`. Ve veřejném repu jde podpis do veřejného logu transparentnosti
  Sigstore (Rekor); v soukromém by šel do privátní instance GitHubu.
- **Attestace nedokazuje, že build je reprodukovatelný** — jen kdo, kdy a z čeho ho postavil.
  Že workflow nedělá nic jiného, než tvrdí, se ověřuje čtením workflow u commitu z attestace.
- **Desktop attestaci sám neověřuje** (`AppUpdates.jvm.kt` kontroluje jen SHA-256 z téže
  odpovědi API, která dává i odkaz). Viz „Oprávnění workflow" níž, proč na tom záleží.

## Oprávnění workflow

**Desktop si přílohy GitHub Release sám stahuje a instaluje** (`docs/poznatky/desktop.md`,
„Aktualizace z GitHub Release"), a balíčky nejsou podepsané pro systém, takže je aktualizace
pustí bez SmartScreen i Gatekeeperu. Kdo umí přepsat přílohu vydání, spustil by svůj kód
u všech uživatelů desktopu, jakmile ťuknou na Restartovat. Aplikace proto chce podpis
aktualizace klíčem ze secretu (`docs/poznatky/desktop.md`, „Podpis aktualizací"), a dál:

- **V `desktop-release.yml` má zápis jen job `release`** (`contents`, `id-token`,
  `attestations`); workflow jako celek má `contents: read`. Joby `windows` a `macos` pouštějí
  Gradle s pluginy a závislostmi třetích stran — dřív s tokenem, který uměl přepsat přílohy
  i kód v repu. Job `release` nic nestaví, jen attestuje a nahrává hotové artefakty.
- **Akce třetích stran jsou připnuté na SHA commitu** (s verzí v komentáři), v obou release
  workflow: `gradle/actions/setup-gradle`, `android-actions/setup-android`
  a `r0adkll/upload-google-play` (ta dostává klíč servisního účtu Play). Tag jde u cizího repa
  přesunout na jiný commit; akce `actions/*` od GitHubu zůstávají na tagu. Při bumpu se SHA
  zjistí `gh api repos/<repo>/commits/<tag> --jq .sha` a celé číslo verze
  `gh api "repos/<repo>/tags?per_page=100" --jq '.[] | select(.commit.sha=="<sha>") | .name'`.
- **`android-release.yml` je jeden job** (build, podpis, Play, release), takže zápis nese i build.
  Rozdělit ho by tolik nepomohlo: build tak jako tak drží podpisový klíč a přístup do Play.
- **Nastavení repa (ne v kódu, `gh api`):**
  - **Ruleset „Release tags are immutable"**: tagy `desktop-*` a `android-*` nejde smazat ani
    přesunout, bez výjimek. Tag ukazuje na kód, ze kterého release vznikl (a odkazuje na něj
    attestace); přesunout ho by znamenalo tiše vydat jiný kód pod starou verzí. Opravit tag
    jde jen dočasným vypnutím rulesetu (Settings → Rules).
  - **Omezit, kdo tag ZALOŽÍ, nejde**: výjimku pro GitHub Actions (`create-release-tag.yml`
    pushuje tag `GITHUB_TOKEN`em) GitHub v repu osobního účtu odmítne („Actor GitHub Actions
    integration must be part of the ruleset source or owner organization"), a bez ní by
    pravidlo zablokovalo vlastní workflow. Pojistkou je proto schválení níž.
  - **Environment `release`**: job `release` v obou workflow čeká na schválení vlastníkem
    (required reviewers) a nasadit do něj jde jen z tagů `desktop-*` / `android-*` (deployment
    tag policy). Release se tedy po buildu (desktop) nebo před ním (Android, jeden job) zastaví
    na „Review deployments" v běhu workflow. Proti ukradenému tokenu s právem `repo` to nechrání
    (schválit jde i API), proti cizímu kódu v CI a omylem pushnutému tagu ano.
  - **Secrets releasu jsou v environmentu `release`, ne v repu**: `ANDROID_KEYSTORE_BASE64`,
    `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
    `GOOGLE_SERVICES_JSON`, `PLAY_SERVICE_ACCOUNT_JSON` a `DESKTOP_UPDATE_SIGNING_KEY`
    (PEM klíče Ed25519 pro podpis aktualizací desktopu). Secret repa dostane jakýkoli běh na
    jakékoli větvi (stačí pushnout větev s workflow, které ho vypíše); secret environmentu jen
    job přihlášený do `release`, po schválení a z povoleného ref. Proto je v environmentu
    i `play-listing.yml` a environment kromě tagů pouští větev `main`, odkud se Play Listing
    spouští ručně.
  - **`TELEGRAM_API_ID` a `TELEGRAM_API_HASH` zůstávají v repu**: potřebují je buildy desktopu
    (joby `windows` a `macos`, mimo environment) a hodnoty jsou stejně zapečené v každém
    APK i balíčku desktopu.
  - **GitHub hodnoty secrets nevydá**, přesun znamená zadat je znovu (Settings → Environments →
    `release`, nebo `gh secret set <JMÉNO> --env release < soubor`) a teprve pak smazat ty
    z repa. Obráceně by release Androidu selhal.
- **Skutečnou ochranu proti ukradenému účtu GitHubu dá až podpis balíčku**, který desktop
  ověří před instalací (Authenticode / Developer ID, nebo vlastní klíč mimo GitHub). SHA-256
  i attestace vznikají uvnitř GitHubu, takže před tím, kdo ho ovládá, nechrání.

## R8

Release build je minifikovaný. Dex spadl ze **43,8 MB na 7,2 MB** — TDLib generuje tisíce tříd
API, které aplikace z drtivé většiny nevolá.

- **Wrapper si keep pravidla nese sám.** `tdl-coroutines` má v AAR `proguard.txt`, který drží
  JNI hranici (`org.drinkless.tdlib.JsonClient`, jeho `native <methods>` a callback na logy);
  AGP ho aplikuje automaticky. **Nekopírovat je do `proguard-rules.pro`** — při bumpu wrapperu
  by se opsaná kopie tiše rozešla s tím, co knihovna opravdu potřebuje.
- **Do `proguard-rules.pro` patří jen to, co se ověřeně rozbilo na zařízení.** Preventivní
  `-keep` vypne zkracování tam, kde nemusí, a zakonzervuje chybu, která se mezitím opravila
  jinde. Pravidla, která nese knihovna z repozitáře ai-engine (LiteRT-LM, továrny runtime), jsou
  v ní jako consumer rules a do `proguard-rules.pro` Plume nepatří.
- **LiteRT-LM (Gemma) si keep pravidla NEPŘIBALUJE** — AAR `litertlm-android 0.11.0` nemá
  `proguard.txt`, na rozdíl od TDLib wrapperu. Nativní strana přitom sahá do Kotlinu podle
  jména: release build při prvním použití Gemmy spadl na `NoSuchMethodError …
  SamplerConfig.getTopK()` a **SIGABRT** — nativní pád, tedy bez výjimky, kterou by šlo
  chytit. Projevilo se to hned při OTEVŘENÍ CHATU: s příchozí poslední zprávou se sám rozběhne
  návrh odpovědi. Proto `-keep class com.google.ai.edge.litertlm.** { *; }` na celý balíček,
  ne výčet tříd — JNI hranici určuje knihovna a výčet by se při bumpu tiše rozešel. **Při
  bumpu knihovny zkontrolovat, jestli už `proguard.txt` nese** (`unzip -l` na AAR); pak
  pravidlo pryč. Ověřeno na emulátoru: bez pravidla pád, s ním shrnutí i návrhy. Pravidlo je
  dnes v consumer rules modulu `:ai-litert` knihovny ai-engine (`external/ai-engine`) a platí
  i z dynamic feature `litertFeature` — R8 bere pravidla závislostí součástí (`seeds.txt`).
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
  - **Vlastní `tools:keep` jen v souboru v `res/raw`** (`client/androidApp/src/main/res/raw/plume_keep.xml`).
    Na kořeni souboru ve `values/` ho shrinker nečte a prostředek tiše vyhodí — tak zmizela
    schopnost hodinek `android_wear_capabilities` (viz `docs/poznatky/wear-os.md`, „Schopnost
    telefonu v release").
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
- **Balíček leží v modulu `:client:core`** (od hlášení pádů na hodinkách), Crashlytics je
  `androidMain` závislost `core`. Hodinky stojí jen na `core`, a ve `shared` by se k němu
  nedostaly — druhá kopie mostu by se s první rozešla. Vstupní bod volá `configureLogging()`
  (telefon v `startKoin` ve `shared`, hodinky v `WearApplication.onCreate`) a aplikuje plugin
  `crashlytics` za `google-services`.
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

Desktop Crashlytics nemá; píše záznamy o pádech do souborů v datech aplikace, viz „Záznamy
o pádech (logy)" v `docs/poznatky/desktop.md`.

### Hlášení pádů na iOS

Do téhož Firebase projektu jako Android (`telegram-b9fff`), iOS aplikace s bundlem
**`eu.tintera.plume`**.

- **Kotlin/Native knihovnu Firebase nevidí** (ObjC ze SPM, ne cinterop), proto most ze Swiftu:
  `FirebaseCrashlyticsBridge.swift` v `iosApp` implementuje Kotlin rozhraní `CrashlyticsBridge`
  (`:client:shared` iosMain, `core/logging`) a `iOSApp.swift` ho podá do `initKoin`. Kotlin ho
  obalí do `IosCrashReporter` a vloží do `IosCrashReporting.reporter` v `:client:core` — **dřív
  než `configureLogging`**, ze kterého si ho `createCrashReporter` vezme. Rozhraní je ve
  `shared`, ne v `core`: framework `ComposeApp` nic neexportuje, takže Swift vidí jen typy ze
  `shared`.
- **Kotlin výjimka nese jen adresy stacku** (`getStackTraceAddresses()`); Swift z nich udělá rámce
  `ExceptionModel` a Crashlytics je symbolizuje podle dSYM. `ComposeApp` je statický framework,
  Kotlin kód je tedy v binárce aplikace a v jejím dSYM. Model nese jeden stack, řetěz `Caused by`
  jde do `reason` (nejvýš 5 příčin, cyklus nezacyklí).
- **Zásobník začíná místem vzniku výjimky, ne konstruktorem** (`stackFromThrowSite`). Kotlin
  výjimka nese adresy od `kotlin.Throwable#<init>` přes `Exception#<init>`… a Crashlytics podle
  horního rámce pojmenuje a SESKUPÍ problém — první zachycená chyba v konzoli se jmenovala
  `kfun:kotlin.Throwable#<init>(kotlin.String?){}` a pod tím by skončily všechny. Úvodní rámce
  `#<init>(` se proto zahodí podle jmen z `getStackTrace()`; bez jmen (ořezaná binárka) zůstane
  zásobník celý.
- **Nezachycená Kotlin výjimka** končí `abort`em z runtime a Crashlytics by viděl jen ten — všechny
  pády z Kotlinu by spadly do jednoho problému. `installUnhandledExceptionHook` ji proto před
  ukončením pošle jako non-fatal a celý `stackTraceToString()` jako řádek logu. U pádu z Kotlinu
  je tedy v konzoli **dvojice záznamů** (fatální abort + non-fatal s příčinou) — záměr.
  - **Bez chvilky času se záznam NEZAPÍŠE**: Crashlytics zápis dokončuje
    přes hlavní frontu a `terminateWithUnhandledException` proces ukončí dřív. Ověřeno na disku
    (`reports/active/<id>/custom_exception_a.clsrecord` chyběl, hook prokazatelně proběhl).
    **`sleep` na hlavním vlákně nepomůže ani 3 s** — frontu blokuje; hook proto na hlavním
    vlákně protočí run loop (`runUntilDate`, 0,5 s), na jiném vlákně spí. Pak záznam je
    (pád z korutiny na Main i na Default).
  - **Výjimka propadlá z Kotlin lambdy předané jako ObjC blok** (`dispatch_after`, completion
    handler iOS API) jde přes `std::terminate`, kde ji první chytí terminate handler Crashlytics
    (`FIRCLSTerminateHandler`), zapíše jako fatální C++ výjimku `ExceptionObjHolderImpl` a proces
    označí za padající; náš hook se volá až po něm a další záznam Crashlytics nevezme. U takového
    pádu je v konzoli jen `ExceptionObjHolderImpl` bez příčiny — z kódu se to opravit nedá.
  - **Zkoumat záznamy jde bez konzole**: build se zapnutým sběrem, pád, a PŘED dalším spuštěním
    číst `reports/active/<id>/` (jména a důvody jsou v JSON hexem); odinstalace aplikace
    neodeslaná hlášení zahodí. **S vypnutým sběrem Crashlytics zachycené chyby ani logy
    nezapisuje vůbec**, jen pády — zkouška s `NO` nic neříká.
- **Firebase přes SPM** (`firebase-ios-sdk`, produkt `FirebaseCrashlytics`, do příští major) jen
  v cíli `iosApp`; rozšíření (notifikace, sdílení) hlásič nemají.
- **Sběr jen z Release**: `FirebaseCrashlyticsCollectionEnabled` v `Info.plist` je build setting
  `PLUME_CRASHLYTICS_COLLECTION_ENABLED` (Debug `NO`, Release `YES`). Crashlytics bere i textové
  `YES`/`NO`. Archiv do TestFlightu je Release, build z Xcode na zařízení Debug.
- **`GoogleService-Info.plist` patří do `iosApp/iosApp/`**, ale v gitu není (`.gitignore`, stejně
  jako `google-services.json`; viz „Konfigurace Firebase" v `notifikace.md`). Složka je synchronizovaná skupina Xcode, do
  bundlu se dostane sama. iOS aplikace ve Firebase je `1:737473989471:ios:f0c3…`, bundle
  `eu.tintera.plume`. **Bez plistu aplikace běží dál, jen bez hlášení**:
  `FirebaseCrashlyticsBridge.start()` vrátí `nil` (`FirebaseApp.configure()` by bez plistu shodil
  proces).
- **dSYMy** se zatím nenahrávají samy — iOS se nevydává a release workflow nemá. Ruční nahrání
  po archivu: `"<DerivedData>/iosApp-…/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/upload-symbols" -gsp iosApp/iosApp/GoogleService-Info.plist -p ios <cesta k dSYMs>`.
  Bez dSYMu je u pádu z Kotlinu v konzoli jen seznam adres.
- **Debug build má kód v `Plume.debug.dylib`** (Xcode „debug dylib", `ENABLE_DEBUG_DYLIB`),
  `Plume` je jen spouštěč. Crashlytics pak chtěl dSYM k UUID té dylib a hlásil ho jako „Missing
  (required)", přestože ho `upload-symbols` nahrál s „Successfully submitted" — u dylib ho
  nezaevidoval. Release build dylib nemá; pro zkoušku v Debug se proto staví s
  `ENABLE_DEBUG_DYLIB=NO` (kód i dSYM v jediné binárce, ~180 MB).
- **Zkouška bez Release buildu** (ten kvůli LTO nad TDLib trvá 15+ minut): Debug přes
  `xcodebuild … PLUME_CRASHLYTICS_COLLECTION_ENABLED=YES DEBUG_INFORMATION_FORMAT=dwarf-with-dsym ENABLE_DEBUG_DYLIB=NO`,
  nahrát dSYM, dočasný pád za proměnnou prostředí (`SIMCTL_CHILD_… xcrun simctl launch … -FIRDebugEnabled`),
  znovu spustit. Hlášení odejde při dalším startu přes `firebaselogging-pa.googleapis.com`;
  odeslané hlášení zmizí z `Library/Caches/com.crashlytics.data/…/v5/reports/prepared`.
  Ověřeno tak 25. 10. 2026 v simulátoru: zachycená chyba přes `Logger.e` v konzoli se
  symbolizovaným zásobníkem, nezachycená výjimka z korutiny jako dvojice; dSYM obsahuje Kotlin
  symboly (`kfun:eu.tintera.plume…`).

## Aktualizace z obchodu

Android nabízí novou verzi z Play kartou nahoře v seznamu chatů (`UpdateCard`) přes Play
In-App Updates v režimu **flexible**: stahuje se na pozadí, pak „Restartovat" (`completeUpdate`).
Desktop má tutéž kartu nad přílohami GitHub Release (`docs/poznatky/desktop.md`, „Aktualizace
z GitHub Release").

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
- **Proužek průběhu dojíždí animací** (`animateFloatAsState` se specem
  `WavyProgressIndicatorDefaults.ProgressAnimationSpec`, který M3 dává přesně na tohle). Obchod
  hlásí průběh po skocích a holá hodnota proužkem cukala.
  **Ověřuje se podstrčeným stavem** jako vzhled karty: dočasná série `Downloading` po `Handler`
  a měření konce proužku ze `screenrecord`. Karta se v seznamu během toho posouvá, jak přibývají
  bloky nad ní (Čeká na odpověď), takže je třeba měřit na její aktuální výšce.
- **Ruční kontrola v „O aplikaci" je `checkNow()`, ne `check()`** (`docs/poznatky/nastaveni.md`,
  „O aplikaci"). `check()` výsledek nevrací a na desktopu se ptá nejvýš jednou za šest hodin —
  kdo ťukne na „Zkontrolovat aktualizace", chce slyšet i „máš nejnovější verzi" a hned.
  `checkNow()` mění `state` stejně, takže nabídku pak ukáže i karta v seznamu chatů.
- **Instalace mimo Play se pozná podle instalátoru** (`getInstallSourceInfo` → `com.android.vending`),
  ne podle chyby obchodu: ten na cizí instalaci odpoví chybou (`-6`, `-10`…), ale může i „aktualizace
  není", a „máš nejnovější verzi" by u ladicího buildu lhalo. Chyby `ERROR_APP_NOT_OWNED`,
  `ERROR_API_NOT_AVAILABLE` a `ERROR_PLAY_STORE_NOT_FOUND` jsou pojistka pro totéž.
- **Ikona je `system_update` (telefon se šipkou dovnitř), ne `upgrade`.** Šipka nahoru k čáře se
  čte jako NAHRÁVÁNÍ — a to je přesně opačný směr, než co karta nabízí. Ikona má říct, co se
  stane, ne jakým gestem se to spustí.
