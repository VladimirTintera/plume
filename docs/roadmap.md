# Plume — co dodělat

Živý dokument přes celou aplikaci. Nové nápady sem, hotové věci ven (a poučení z nich do
`docs/poznatky/`, obecná do `CLAUDE.md`). Pořadí uvnitř sekcí je doporučení, ne závazek.

Detail chatu má vlastní, podrobnější seznam: [roadmap-konverzace.md](roadmap-konverzace.md).
Sem patří jen to, co je mimo něj.

---

## Rozpracované

Sem patří to, co leží v pracovním adresáři a ztratilo by se při přerušení práce: nedodělaná
větev, ladicí kód k vyhození, nedopsané poučení. Právě tohle se ztrácí nejsnadněji, protože to
není ani v kódu, ani v commitu.

Teď je prázdné.

---

## Než se dá vydat na veřejnost

- **Záznam v Play dokončit mimo API.** Texty a grafika (cs, en) jsou nahrané, workflow Play
  Listing je nahrává z `play/`. Ručně v Play Console zbývá kategorie, zásady ochrany soukromí (URL),
  hodnocení obsahu (IARC), cílová skupina, bezpečnost dat a prohlášení o reklamách.
- **Po publikaci (stačí uzavřený test) ověřit modely v zařízení z Play.** Dokud je Plume
  v Play jen koncept s interním testováním, Play odmítá doinstalovat součásti s runtime
  (`litertFeature`, `llamacppFeature`) chybou -15 a model se z Play stáhnout nedá. Po publikaci
  na Foldu z Play „Stáhnout model" a zkusit i Internal app sharing, který koncept taky nepustí
  (`docs/poznatky/ai-v-zarizeni.md`, „Runtime jako součást z Google Play"). **Do té doby jsou
  součásti dočasně „s instalací"** (+13,7 MB ke stažení na arm64): před produkcí vrátit
  v manifestech obou součástí `<dist:on-demand />` a teprve pak ověřit doinstalaci z Play.
- **Mini aplikace: dialogy a odkazy jen po gestu uživatele.** `open_link`, `open_tg_link`,
  popup, žádost o zápis, polohu a stažení přijdou kdykoli a v libovolném počtu: stránka může
  ve smyčce otevírat odkazy, a dialog polohy může naskočit přesně pod opakované ťuknutí na
  stránce (souhlas, který uživatel neviděl). Brát je jen pár sekund po dotyku ve WebView
  a krátce po zobrazení dialogu ignorovat ťuknutí, jako oficiální klienti.
- **Mini aplikace: souhlas s polohou nejde odvolat.** Uloží se navždy
  (`MiniAppStorage.setLocationAllowed`), `web_app_open_location_settings` nic nedělá a v profilu
  bota ani v Nastavení k tomu nic není. Chce řádek v profilu bota (a v Nastavení → Soukromí
  seznam botů s polohou).
- **Podpis balíčků desktopu** (Authenticode, Developer ID, nebo vlastní klíč mimo GitHub),
  který self-update ověří před instalací — SHA-256 i attestace vznikají uvnitř GitHubu
  (`docs/poznatky/release.md`, „Oprávnění workflow").
- **Seznam chatů na snímcích obrazovky chybí.** Na účtu jsou skutečné osoby; snímek seznamu
  chce demo účet se smyšlenými chaty.
- **Název aplikace u `api_id` je „MGram"** — tak se Plume ukazuje v seznamu zařízení
  a v oznámeních Telegramu („Device: MGram, 0.3.75"). Přejmenovat na my.telegram.org (API
  development tools); v kódu se nenastavuje (`docs/poznatky/nastaveni.md`, „Zařízení").
- ~~**`isShrinkResources` není zapnuté.**~~ — hotovo (release proklikaný na emulátoru;
  `docs/poznatky/release.md`, „R8").
- **Play Console: deklarace služeb v popředí a celoobrazovkových notifikací** (Obsah
  aplikace). Play hlásí sedm nedeklarovaných typů služby v popředí. **U každého chce kromě
  zaškrtnuté úlohy i popis a odkaz na video**, které funkci ukazuje: záznam obrazovky,
  na YouTube jako neveřejné video. Bez deklarace Play vydání s těmi oprávněními nepustí.
  Jedno video z videohovoru stačí na všechny čtyři typy hovoru, pokud je v něm kamera,
  sdílení obrazovky a odchod z aplikace. Kdo co v kódu spouští: `CallService`,
  `MusicPlaybackService`, `LiveLocationService` a `ModelDownloadWorker`, typy jsou
  v `AndroidManifest.xml` vstupního bodu. Popisy níž jsou návrhy, anglicky, protože je čte
  recenzent Googlu. Názvy úloh kromě kamery jsou odhad: zaškrtnout tu, která odpovídá
  smyslu; „Jiné" jen tehdy, když nesedí nic, protože vede k delší ruční kontrole.
  - [ ] **Celoobrazovkové notifikace** (`USE_FULL_SCREEN_INTENT`, samostatná deklarace):
    hlavní funkce „Volání a přijímání hovorů", udělit předem „Ano". Video není potřeba.
    Bez ní Plume z Play na Androidu 14+ zamčený telefon při hovoru nerozsvítí. Aplikace stav
    ukazuje a jednou se zeptá (`docs/poznatky/notifikace.md`, „Celoobrazovková výzva hovoru").
  - [ ] **`FOREGROUND_SERVICE_CAMERA`**: úloha „Streamování z kamery na pozadí". Video:
    videohovor se zapnutou kamerou, pak Domů nebo okénko (PiP) a protistrana dál vidí obraz.
    > Plume is a Telegram client with voice and video calls. During a video call the user can
    > leave the app (switch to another app, use picture-in-picture, or lock the screen) and
    > the call continues; the camera keeps streaming to the other party while the foreground
    > service runs. The camera type is added only when the user turns the camera on in a
    > call, and the service stops when the call ends.
  - [ ] **`FOREGROUND_SERVICE_PHONE_CALL`**: úloha pro probíhající hovor (VoIP). Video:
    přijatý hovor, odchod z aplikace, hovor běží dál s notifikací a jde zavěsit z ní.
    Tentýž typ (a `MICROPHONE` s mluvením) má i videochat ve skupině (`VideoChatService`),
    do videa tedy patří i odchod z videochatu na plochu a „Zavěsit" v jeho notifikaci.
    > Plume makes and receives Telegram voice and video calls (VoIP, registered with the system
    > Telecom framework through androidx.core.telecom). The foreground service runs for the whole call
    > so the call continues when the user leaves the app or locks the screen, and shows an
    > ongoing call notification with Hang up. It stops when the call ends.
  - [ ] **`FOREGROUND_SERVICE_MICROPHONE`**: úloha pro hovor / přístup k mikrofonu na pozadí.
    Video: tentýž hovor, protistrana slyší i po odchodu z aplikace.
    > During a Telegram voice or video call the microphone must keep sending the user's voice
    > to the other party after the user leaves the app or locks the screen. The microphone
    > is used only while a call is in progress and the service stops when the call ends.
  - [ ] **`FOREGROUND_SERVICE_MEDIA_PROJECTION`**: úloha pro sdílení obrazovky. Video:
    v hovoru sdílet obrazovku, přepnout do jiné aplikace, protistrana ji vidí.
    > In a call the user can share their screen with the other party. The user starts it
    > explicitly and confirms the system screen capture dialog; sharing continues while the
    > user switches to other apps, which is the point of sharing the screen. It stops when
    > the user stops sharing or the call ends.
  - [ ] **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`**: úloha pro přehrávání zvuku na pozadí. Video:
    pustit skladbu (hudební soubor) v chatu, odejít z aplikace, hraje dál a ovládá se
    z notifikace.
    > Users can play music files and audio sent in Telegram chats. Playback continues in the
    > background with a media notification and media session controls (Media3
    > MediaSessionService), and stops when the playlist ends or the user stops it.
  - [ ] **`FOREGROUND_SERVICE_LOCATION`**: úloha pro sdílení polohy. Video: v chatu poslat
    živou polohu (sponka → Poloha), odejít z aplikace, poloha se dál aktualizuje
    a notifikace sdílení jde zastavit.
    > Users can share their live location in a Telegram chat for a period they choose
    > (15 minutes to 8 hours, or until they stop it). The service keeps updating the location in the shared message
    > while the app is in the background and shows a notification with Stop. It is started
    > only from the app in the foreground after the user's explicit action; background
    > location permission is not requested.
  - [ ] **`FOREGROUND_SERVICE_DATA_SYNC`**: úloha pro stahování / přenos dat po síti. Video:
    Nastavení → AI v zařízení → stáhnout model, odejít z aplikace, průběh v notifikaci.
    Ověřit ještě stahování v release buildu a na telefonu (sekce „AI v zařízení" níž).
    > The user can download an on-device AI model (Gemma, several GB) for summaries and reply
    > suggestions. The download is started explicitly by the user, runs as a WorkManager task
    > in a foreground service with a progress notification and Stop, and continues if the
    > user leaves the app. It stops when the download finishes or the user cancels it.

## Zveřejnění zdrojáků

Rozhodnuto: **jedno veřejné repo i s `CLAUDE.md` a `docs/poznatky`**, žádné soukromé dev repo
se zrcadlem. Zrcadlo se stejnou historií nejde zbavit souborů (vyřazení přepíše commity) a se
snímky by se cizí PR i změny z Weblate musely přenášet ručně. Poznatky jsou vývojářská
dokumentace, ne tajemství. Osobní paměť agenta (`~/.claude/…/memory`) v repu není a nebude.

Proč: **důvěra** (kdokoli si ověří, že v aplikaci nic schovaného není) a **runnery zdarma**
(standardní hostované runnery jsou ve veřejném repu bez limitu minut, i macOS a Windows).

**Ověřeno sken 2026-11-22** (2 976 commitů, všechny větve): `telegramApiHash` ani
`telegramApiId` v historii nikdy nebyly (`git log --all -S` se skutečnými hodnotami), stejně
jako podpisový klíč a `local.properties`; žádný soubor nad 2 MB; submodul ai-engine je veřejný
(Apache 2.0); licence MIT a cizí licence (TDLib, fonty, Material Symbols, ntgcalls LGPL) už
jsou v README. Gitleaks se nad historií ještě nepouštěl.

Postup, až nastane čas (ideálně bez rozpracovaných větví — všechny dostanou nová SHA):

### 0. Příprava ve stávajícím repu

- ~~**Souhlas MagicWare**~~ — rozhodnuto, není potřeba. Z mlogu jsou převzaté hlavně nápady
  a nastavení (hodnoty pružin v `Motion.kt`, ověřená kombinace verzí Compose, rozdělení na
  knihovnu a tenké vstupní body), které autorské právo nechrání. Konkrétní kód jsou jen
  malé obecné obcházky (`RootOverlay.kt`, iOS `CrashlyticsBridge.kt`, dva kroky
  v `desktop-release.yml`) a mlog vznikl jako autorův večerní projekt. E-mail `@magicware.cz`
  u commitů na vlastnictví nic nemění (zaměstnanecké dílo vzniká z pracovního úkolu, ne podle
  `git config`); přepis ho stejně sjednotí na noreply.
- ~~**Odkazy na mlog a Profilog**~~ — hotovo: z komentářů v kódu, workflowů, `CLAUDE.md`
  i poznatků zmizely („převzato z mlogu", KMPMlog#992, runnery kmpmlog a telemetry).
  Ve staré historii zůstávají; tajemství to není, takže je přepis nechá být.
- ~~**Žádný self-hosted runner na `pull_request`**~~ — hotovo: `release-notes-check.yml` běží
  natvrdo na `ubuntu-latest`, bez repo proměnné (`release.md`, „Runnery").
  Workflow nesmí používat `pull_request_target` (secrets pro cizí PR); dnes ho nepoužívá žádný.
- ~~**Komentáře o vnitřní infrastruktuře**~~ — hotovo: jméno stroje HQDEV50 nahradil
  „vývojářský Windows" a jméno runneru `<stroj>-plume`.
- ~~**`SECURITY.md` a `CONTRIBUTING.md`**~~ — hotovo, anglicky (čtou je cizí přispěvatelé).
  Odkaz na soukromé hlášení v `SECURITY.md` začne fungovat, až se ve fázi 2 zapne.
- ~~**README**~~ — hotovo: přepsané anglicky podle stavu v `CLAUDE.md` (proč je kód veřejný,
  co aplikace umí, platformy, s kým aplikace mluví kromě Telegramu, build, podmínky API,
  licence). Postup vydání do Play a velikosti aplikace se přesunuly do `release.md`.
- **Název repa `plume`**, ne `telegram`: podle ToS nesmí název působit jako oficiální klient.
  Nastaví se při zakládání nového repa (fáze 2).

### 1. Přepis historie (`git filter-repo` nad kopií)

**Zkušební přepis hotový 2026-11-22** nad klonem `main` (`~/Develop/plume-prepis`, pravidla
v `~/Develop/plume-prepis-pravidla` na Macu vývojáře — v repu nejsou, obsahují ta čísla).
**Před založením nového repa se ZOPAKUJE nad čerstvým klonem**: `main` mezitím ujede (i tímhle
zápisem) a přepsaná kopie by ho neměla. Postup je mechanický:

```bash
git clone --no-local https://github.com/VladimirTintera/telegram.git plume-prepis && cd plume-prepis
git filter-repo --invert-paths --paths-from-file ../plume-prepis-pravidla/odstranit-cesty.txt \
  --replace-text ../plume-prepis-pravidla/nahradit-text.txt --mailmap ../plume-prepis-pravidla/mailmap
```

Do nového repa jde jen `main` a 201 tagů vydání (tagy potřebuje skládání poznámek k vydání,
`git log PREV..TAG`). Větve `docs/citace-casti-zpravy` (celá sloučená) a `docs/pruzkum-hovoru`
(jeden nesloučený commit) zůstávají jen v archivu; v čerstvém klonu je před přepisem smazat
(`git update-ref -d refs/remotes/origin/docs/…`). Nové repo je ze starého GitHubu oddělené,
`refs/pull/N/head` do něj nejdou, proto se nedělá force-push do stávajícího. To zůstane
soukromé jako archiv.

- **Gitleaks nad celou historií** (8.30.1, `gitleaks git --log-opts=--all`): dva klíče Firebase
  (`gcp-api-key` v obou `google-services.json`) a dva falešné poplachy `generic-api-key`
  v `AuthScreen.kt` (`AuthStage.WaitCode -> Res.drawable…`). Hesla, tokeny ani klíče Telegramu
  nenašel. Plist pro iOS gitleaks nehlásí, jde pryč i tak.
- **Vlastní sken všech verzí všech souborů** (telefonní čísla, e-maily, cesty `/Users/…`,
  sériová čísla zařízení, id účtu): čísla vývojáře a testovacího účtu a id testovacího účtu
  (Uložené zprávy, v `WatchTranscriptionTest` a `TelegramPushTest`). E-maily ani cesty v obsahu
  souborů nejsou. Ostatní čísla v testech jsou smyšlená (+420 602 123 456, 777 123 456…).
  Adresa „Vinohradská 1511/230" v testech hledání adres zůstává (rozhodnuto).
- **Přepis**:
  - `--invert-paths` na **všechny tři** konfigurace Firebase: `client/androidApp/google-services.json`,
    `client/wearApp/google-services.json` a `iosApp/iosApp/GoogleService-Info.plist`. Build bez
    nich funguje (Gradle plugin se aplikuje jen se souborem, iOS `FirebaseCrashlyticsBridge`
    bez plistu Firebase nespustí). Tajemství to není — klíč je v každém APK, chrání ho omezení
    na balíček a SHA-1 v Google Cloud konzoli —, jen do veřejného repa nepatří. Od fáze 2
    nejsou ani v `main` (`.gitignore`) a release workflow píše `google-services.json` ze secretu
    `GOOGLE_SERVICES_JSON`; bez něj release zastaví (`notifikace.md`, „Konfigurace Firebase").
  - `--replace-text`: obě telefonní čísla (s mezerami i bez) na smyšlená 601 234 567
    a 601 987 654, id testovacího účtu na 444555666. Náhrady v historii předtím nebyly, takže
    se s ničím nesrazí, a v testech se mění všude stejně.
  - `--mailmap`: všechny commity pod `Vladimír Tintěra <…+VladimirTintera@users.noreply.github.com>`
    (dřív i gmail a `@magicware.cz`); commity Clauda a merge od GitHubu zůstávají.
- **Ověřeno**: gitleaks po přepisu hlásí jen dva falešné poplachy, vlastní sken nic, Firebase
  v historii není ani jednou. Rozdíl přepsaného a původního `main` jsou přesně tři odstraněné
  soubory a náhrady v sedmi souborech. `main` má 2 984 commitů místo 2 985 (commit, který
  jen přidal `google-services.json`, zbyl prázdný a zmizel), `.git` 37 MB.
  `assembleDebug` a testy `:client:shared` a `:client:core` na JVM v přepsané kopii prošly
  (2 910 testů, žádné selhání; build bez Firebase ohlásí chybějící `google-services.json`
  u telefonu i hodinek a pokračuje). iOS a desktop se v kopii nestavěly.

### 2. Nové repo, nejdřív soukromé

- ~~**Konfigurace Firebase ze secretu**~~ — hotovo: všechny tři soubory jsou z gitu venku
  (`.gitignore`) a `android-release.yml` píše `google-services.json` do telefonu i hodinek ze
  secretu `GOOGLE_SERVICES_JSON`, bez něj release zastaví. **Secret musí být nastavený už ve
  STÁVAJÍCÍM repu**, dokud se z něj vydává. Kopie souborů je u pravidel přepisu
  (`~/Develop/plume-prepis-pravidla/firebase`).
- ~~**Push přepsané historie**~~ — hotovo 2026-11-22: repo
  [VladimirTintera/plume](https://github.com/VladimirTintera/plume) má přepsaný `main`
  (2 988 commitů, stav po sloučení #1232 ve starém repu) a 201 tagů vydání. Přepis zopakovaný
  nad čerstvým klonem a ověřený stejně jako zkušební (gitleaks jen dva falešné poplachy,
  vlastní sken čistý, Firebase v historii ani jednou, rozdíl proti originálu jen náhrady
  v šesti souborech). Build se znovu nespouštěl: kód je tentýž jako ve zkušebním přepisu,
  přibyla jen dokumentace, `.gitignore` a krok workflow.
  - **Push tagů nespustil žádný release**: tagy šly jedním pushem a GitHub při víc než třech
    tazích naráz události nevytváří. Push jednotlivého tagu `android-*` / `desktop-*` by release
    spustil (a v repu bez secretů a runnerů by visel nebo spadl).
  - **První PR minuty po pushi workflow nespustil**: Actions zapnuté, workflowy registrované
    a aktivní, `PullRequestEvent` v událostech repa, a přesto žádný běh ani check suite. GitHub
    workflowy čerstvě nahraného repa zřejmě zpracovává se zpožděním a událost, která přišla
    dřív, propadne. Pomohlo PR zavřít a znovu otevřít (`gh pr close` / `reopen`); kontrola
    poznámek pak proběhla na hostovaném runneru. Fakturace to nebyla: ve starém soukromém repu
    hostované kontroly týž den běžely.
  - **Repo se zakládalo DVAKRÁT.** Dva commity v prvním PR vznikly v klonu bez vlastního
    `user.email`, takže dostaly globální gmail vývojáře — zrovna adresu, kterou přepis
    odstranil. `main` šel opravit mailmapem a force pushem, ale GitHub drží `refs/pull/1/head`
    s původními commity a smazat ho nejde. Repo se proto smazalo a založilo znovu prázdné
    a dostalo opravený `main` a tagy. **Každý klon `plume` má mít `user.email` na GitHub
    noreply** (`git config user.email 69807370+VladimirTintera@users.noreply.github.com`; hlavní
    strom ho má, worktree ho sdílejí).
- **Vývoj se přesouvá do `plume`, staré repo `telegram` se zmrazí.** Historie spolu nesouvisí,
  takže každý commit sloučený do starého repa by se musel přenášet ručně. Na každém stroji
  `git remote set-url origin https://github.com/VladimirTintera/plume.git`, `git fetch`
  a `main` přenastavit na `origin/main`; rozpracované větve ze starého repa přenést
  `cherry-pick`em. `core.hooksPath` (absolutní) zůstává.
- **Čísla PR začínají v `plume` znovu od 1.** Odkazy „#650" v poznatcích a commitech míří do
  starého repa (viz otevřená rozhodnutí).
- ~~**Secrets a proměnné**~~ — hotovo: všech osm secrets, které workflowy čtou (Telegram API
  a podpis z `local.properties`, keystore `plume-release.jks`, `GOOGLE_SERVICES_JSON` ze zálohy,
  klíč service accountu `play-publishing` z projektu `mobile-ci`), a proměnné `WEAR_RELEASE=true`
  a `*_RUNS_ON` na hostované runnery (`ubuntu-latest`, `macos-latest`, `windows-latest`).
- **První release z `plume`** je zároveň první zkouška hostovaných runnerů pro release a kroku
  *Write Firebase config*. **macOS runner má 7 GB RAM** — release link iOS s LTO nad celou
  TDLib na tom může narazit; Android a desktop vystačí. GitHub Releases jsou v `plume` prázdné,
  dokud nevyjde první desktop; staré DMG a MSI zůstávají ve starém repu.
- ~~**Nastavení repa**~~ — hotovo po zveřejnění:
  - ochrana `main`: jen přes PR (bez povinného schválení — vlastník by svůj PR schválit
    nemohl), povinná kontrola `check` od aplikace `github-actions` (id 15368, ať ji nejde
    podvrhnout stavem z jiné aplikace), bez force pushe a mazání; správce ji obejít může;
  - workflowy u PR schvaluje vlastník u všech externích přispěvatelů, ne jen u prvního PR;
  - secret scanning s push protection (u veřejného repa zapnuté samo), upozornění Dependabotu,
    soukromé hlášení zranitelností (`SECURITY.md`);
  - **automatické bezpečnostní PR Dependabotu vypnuté**: neuměly by poznámku k vydání a check
    by je zamítl.
- ~~**Ověřitelnost buildu**~~ — hotovo: release jen z hostovaných runnerů a k MSI, DMG
  i AAB attestace `actions/attest-build-provenance` (`release.md`, „Původ buildu"). APK
  z Play se tím ověřit nedá (Play ho skládá a přepodepisuje); ověřitelný Android build chce
  vlastní APK mimo Play, reprodukovatelný build nebo F-Droid — to až později, chce vydání bez
  Firebase a Crashlytics.

### 3. Zveřejnění

- ~~**Přepnout na public**~~ — hotovo 2026-11-22. Release workflowy jedou repo proměnnými na
  hostovaných runnerech (`release.md`, „Runnery").
- ~~**První release z `plume`**~~ — hotovo 2026-11-22: Android 0.4.0 (171) i s hodinkami do
  Play a desktop 1.1.1 (MSI a DMG) na GitHub Releases, vše na hostovaných runnerech. Desktop 1.1.0
  spadl na NDK bez licence na `macos-latest` (oprava v `release.md`, „Runnery").
- ~~**Úklid po přechodu**~~ — hotovo: self-hosted runnery `plume-mac` a `…-plume` (Windows)
  odregistrované z GitHubu, repo `telegram` archivované (popis odkazuje na `plume`), výchozí
  `runs-on` ve workflowech hostovaný a oddíl o runnerech v `release.md` přepsaný. **Služby
  runnerů na obou strojích je potřeba odinstalovat ručně** (Mac `./svc.sh uninstall`
  v adresáři runneru, Windows služba `actions.runner.VladimirTintera-telegram.…`).
- Weblate (sekce níž) a odkaz na zdrojáky do záznamu v Play.

### Otevřená rozhodnutí

- **Odkazy na čísla PR** v commitech a poznatcích (#650, #80–#87…) budou v novém repu mířit
  na jiné PR. Přepsat (třeba „PR 650 (archiv)"), nebo nechat jako historické.
- **Název „MGram"** (jméno aplikace u `api_id` na my.telegram.org, viz výš) —
  přejmenovat na Plume, nebo nechat.

## Komunitní překlad

Až bude repo veřejné. Volba: **Hosted Weblate, plán Libre** (zdarma pro veřejné projekty
s licencí schválenou OSI — MIT stačí; limit 160k řetězců, při ~2 200 klíčích tedy desítky
jazyků). Vlastní backend ani GitHub Pages potřeba nejsou. Záloha: Crowdin for Open Source.

- **Formát „Compose Multiplatform resources"**, ne „Android string resources" (Weblate ≥ 5.12).
  Liší se escapováním: Android formát by psal `\'` a to Compose Multiplatform nerozbalí
  (viz „Jazyky" v CLAUDE.md). Maska `client/resources/src/commonMain/composeResources/values-*/strings.xml`,
  základ `values/strings.xml`.
- **Schvalování**: hlasování o návrzích s prahem pro automatické přijetí, nebo vyhrazení
  schvalování recenzentům (u nových jazyků si vybrat rodilého mluvčího). Čeština zůstává
  v repu a na Weblate jen pro čtení, dokud ji píšeme sami.
- **Weblate posílá změny jako PR** (doplněk „Squash Git commits" + push do větve `weblate`).
  ~~Check „Release notes" by ho zamítl~~ — hotovo: PR jen s komunitními překlady poznámku
  nepotřebuje (`release_notes.py exempt`, CLAUDE.md „Poznámky k vydání").
- ~~**`verifyTranslations` umí jen `values-cs`.**~~ — hotovo: všechny `values-*`, komunitní
  jazyky chybějící klíč jen varováním, formátovací argumenty chybou u všech (CLAUDE.md, „Jazyky").
- ~~**Nový jazyk se dnes zapisuje ručně na čtyři místa**~~ — hotovo: seznam ze složek `values-*`
  s prahem 80 %, `locales_config.xml` i `APP_LANGUAGES` se generují, názvy jazyků dává systém
  (`docs/poznatky/nastaveni.md`, „Jazyk aplikace"). Zbývá iOS: `<kód>.lproj` se přidává ručně
  (build jen varuje) a `Localizable.strings` z obou `.lproj` jde na Weblate jako další
  komponenta (formát iOS strings).
- **Texty v Play** (`play/listing/`, poznámky k vydání) se komunitně nepřekládají — tam rozhoduje
  `PLAY_LANGS` a jazyk musí mít záznam v obchodě.

## iOS

Android je napřed; tohle je, čím se to dorovnává.

- ~~**AI v zařízení není vůbec**~~ — zapojená Apple Intelligence (Foundation Models, iOS 26+)
  přes Swift most (`docs/poznatky/ai-v-zarizeni.md`, „Apple Intelligence na iOS"). Zbývá:
  **ověřit generování** na Macu se zapnutou Apple Intelligence nebo na iPhonu (a jestli zvládne
  češtinu), stav „zapni si Apple Intelligence" v Nastavení, a strukturovaný výstup / přepis
  řeči / popis obrázků přes `@Generable` a obrazový vstup.
- **Notifikace** — napojené (APNs, rozšíření notifikací, texty, oprávnění, úklid přečtených
  a odznak; `docs/poznatky/notifikace.md`, „Notifikace na iOS (APNs)"). Zbývá:
  - **Certifikát APNs u Telegramu** (my.telegram.org) a Push Notifications v Apple Developer účtu.
  - **Ověřit skutečným pushem na telefonu**: jestli Telegram posílá `mutable-content` (běží
    rozšíření → akce a seskupení), tvar `custom` (`push/last.json` v App Group) a češtinu šablon.
  - **Titulek zvlášť** (jméno chatu jako titulek, text pod ním) — dnes je celá věta v těle,
    jako „Jana: ahoj". Šlo by to v rozšíření, až bude jisté, že běží.
  - **Přečtení a smazání jinde v reálném čase** (`READ_HISTORY`, `MESSAGE_DELETED`) — dnes se
    uklidí až s otevřením aplikace.
  - **Vysvětlení před dotazem na oprávnění ověřit na čistém simulátoru** (na testovacím bylo
    oprávnění vyřízené dřív).
- **Hlášení pádů** — napojené (Swift most do Crashlytics, `docs/poznatky/release.md`, „Hlášení
  pádů na iOS"), hlášení z Debug buildu se zapnutým sběrem v simulátoru odchází. Zbývá ověřit
  pád z Release buildu na zařízení a nahrávání dSYMů v CI (až bude iOS release workflow).
- **Vydání: nejspíš App Store** (zatím nerozhodnuté, testování přes TestFlight). Chce placený
  Apple Developer Program (99 USD ročně, odpuštění jen pro neziskovky a školy). Tentýž účet dá
  i Developer ID a notarizaci pro DMG na macOS. Zvážené a odložené: alternativní obchody v EU
  (AltStore PAL) chtějí stejný účet i notarizaci, takže nic neušetří; stažení z webu je jen pro
  vývojáře s milionem instalací. Bez poplatku by šla nepodepsaná IPA instalovaná přes
  SideStore / AltStore (podpis uživatelovým Apple ID na 7 dní), ovšem bez push notifikací.
- **Rozmazané přiblížení a nejspíš zubaté zmenšování fotek** (prohlížeč médií). iOS jede přes
  týž kód Coilu jako desktop (`nonAndroidMain`, výchozí `SkiaImageDecoder` zmenšuje nejbližším
  sousedem), ale opravu desktopu nemá: `zoomLoadsFullResolution` je v `ZoomResolution.ios.kt`
  `false`, takže přiblížení až 5× roztahuje obrázek velký jako karta, a vlastní dekodér
  (`SmoothSkiaImageDecoder`, `SmoothImage` v `jvmMain/…/core/ui/DesktopImageLoader.kt`) se
  zapojuje jen z `desktopApp` `Main.kt`. Skia je na iOS taky (Skiko), takže by dekodér nejspíš
  šel přesunout do `nonDynamicThemeMain` (iOS + JVM) a zapojit i ve vstupním bodě iOS; testy
  `SmoothSkiaImageDecoderTest` a `SmoothImageTest` jsou dnes v `jvmTest`. Mechanismus
  a pasti (proč `filterQuality` nepomůže, proč `SmoothImage` místo `BitmapImage`):
  `docs/poznatky/desktop.md`, „Zubaté a rozmazané obrázky (Coil na Skie)". Napřed **ověřit na
  simulátoru nebo iPhonu**, že problém na iOS opravdu je (písmo na letáku, hrany při zmenšení,
  detail při přiblížení), pak opravit a ověřit tamtéž. Zjištěno čtením kódu při práci na
  prohlížeči médií (říjen 2026), na iOS nevyzkoušené.
- **Release link do CI jako samostatný cachovaný krok.** Kotlin/Native pouští LTO nad celou
  statickou TDLib a naměřeno je 15+ minut na M-series; do smyčky, kterou čeká vývojář, to nepatří.

## Desktop

Běží na macOS (ověřeno: přihlášení QR kódem, seznam chatů, konverzace, odeslání zprávy).
Poznatky v `docs/poznatky/desktop.md`. Co chybí, zhruba podle důležitosti:

- **Ověřit Windows a Linux.** Přeložené a zabalitelné, ale nespuštěné. Linux chce glibc 2.34+.
- **Hlášení pádů se samo neodesílá.** Záznamy jsou v souborech (O aplikaci → Záznamy aplikace,
  `docs/poznatky/desktop.md`, „Záznamy o pádech"), uživatel je posílá ručně. Automatické
  odesílání by chtělo službu s JVM SDK (Sentry) a souhlas uživatele; zatím nerozhodnuté.
- **Ověřit macOS pod 26.** Od wrapperu 16.0.0 má balíček `minimumSystemVersion` 14.0 (Apple
  Silicon, kvůli ntgcalls), spuštěné je ale jen na macOS 26.
- **Notifikace: ověřit Linux** (AWT `TrayIcon`, podle prostředí — lepší by byl
  `org.freedesktop.Notifications` přes D-Bus), odznak v Docku a zavření okna s návratem
  z Docku. ~~Akce v oznámení (Odpovědět, Přečteno, Ztlumit)~~ — hotovo na Windows i macOS
  (`docs/poznatky/desktop.md`, „Tlačítka toastu", „Tlačítka oznámení na macOS"); na macOS
  zbývá ověřit Přijmout / Odmítnout hovor po přestavbě mostu. Na Linuxu
  tlačítka nejsou a zavření okna aplikaci ukončí — chce ikonu v oznamovací oblasti.
- **Hovory**: na macOS hlasové i videohovory se zvoněním, okénkem příchozího hovoru
  a náhledem kamery (ntgcalls přes JNA, `docs/poznatky/hovory.md`, „Desktop"); Windows
  a Linux neověřené. Co zbývá, je v sekci Hovory.
- ~~**AI v zařízení**~~ — Gemma a Qwen přes `litertlm-jvm` (`docs/poznatky/desktop.md`,
  „Model v zařízení (LiteRT-LM)"). Na Windows ověřené i v přihlášené aplikaci (stažení,
  shrnutí, rada u hlasovky). Na macOS jede GPU (WebGPU nad Metalem, ověřené sondou, ne
  v přihlášené aplikaci). Zbývá Linux; GPU na Windows s Intel UHD nejde, jede procesor.
- **Zvuk**: ~~přehrávání hlasovek (Opus) a hudby~~ — hotovo přes FFmpeg a `javax.sound`
  (`docs/poznatky/desktop.md`, „Zvuk"), ~~nahrávání hlasovek~~ — hotovo (`javax.sound`
  a `libopus` z FFmpegu). Zbývá přeskakování ticha, ovládání hudby ze systému (Now Playing na
  macOS, MPRIS na Linuxu) a nahrávání do Telegramu už během nahrávání (kóduje se až na konci).
- **Video**: ~~ztlumené (GIFy, tichá smyčka kolečka)~~, ~~se zvukem (prohlížeč, kolečko,
  příběhy)~~, ~~streamování rozestavěného souboru~~, ~~celá obrazovka a klávesy~~ a ~~obraz
  v obraze~~ (okénko navrchu, změna velikosti, návrat do prohlížeče) — hotovo přes FFmpeg
  z JavaCPP a `javax.sound` (`docs/poznatky/desktop.md`, „Video (FFmpeg)"). Zbývá:
  - **Hlasitost**: posuvník v liště a šipky ↑/↓ (jako YouTube). Dnes je jen ztlumení.
  - **Další klávesy**: J/L ±10 s, `,`/`.` po snímku u pozastaveného videa, 0–9 skok na
    procenta, `<`/`>` rychlost.
  - **Klávesy médií** (přehrát / pozastavit na klávesnici) pro video i hudbu — spolu
    s ovládáním hudby ze systému (Now Playing, MPRIS) výš u Zvuku.
  - **Pozice z okénka obrazu v obraze se neukládá**: pamatuje se místo, kde se odešlo
    z prohlížeče, ne kde video v okénku skončilo.
  - **Windows a Linux ověřit** — největší riziko: streamování (přesun dostaženého souboru
    s otevřeným čtením na Windows), celá obrazovka bez vlastní plochy, okénko obrazu v obraze;
    k tomu streamování na pomalé síti a přepočítané kvality.
  - AV1 kvality (`libdav1d`).
  - Příběh s videem ověřit v aplikaci (příběh z testovacího účtu, až budou telefony
    k dispozici).
- **Trezor tajemství na Windows a Linuxu** (DPAPI, Secret Service / libsecret). Dnes soubor
  s právy jen pro vlastníka vedle databáze.
- ~~**Vkládání obrázků ze schránky a přetažení souboru do okna**~~ — hotovo
  (`docs/poznatky/desktop.md`, „Vložení ze schránky a přetažení souborů"). Zbývá obrázek
  tažený z prohlížeče (přichází bez souboru).
- ~~**Dostat přílohu z aplikace ven**~~ — hotovo kopírováním do schránky a „Uložit jako…"
  (`docs/poznatky/desktop.md`, „Kopírování příloh do schránky a cesta ven"). ~~Přetažení přílohy
  z okna ven~~ — hotovo (tamtéž, „Přetažení přílohy z okna ven"). ~~„Zobrazit ve složce" po
  uložení do Stažených~~ a ~~tah fotky z prohlížeče médií~~ — hotovo (tamtéž). ~~Tah videa
  z prohlížeče~~ — hotovo (tamtéž).
- **Release pro Linux a Intel Mac** (Windows MSI a DMG pro Apple Silicon už jsou,
  `desktop-release.yml`): job na Ubuntu (DEB, glibc 2.38+) a na Intel runneru (`macos-15-intel`,
  DMG pro macOS 15+). Ani jedno zatím nikdo nezkoušel spustit.
- **Podpis instalátorů**: Windows (certifikát pro podepisování kódu, jinak SmartScreen),
  macOS podpis Developer ID a notarizace. Bez nich systém instalátor při prvním spuštění zastaví.
- ~~**Aktualizace desktopu**~~ — hotovo z GitHub Release (`docs/poznatky/desktop.md`,
  „Aktualizace z GitHub Release"); zbývá ověřit MSI přes PowerShell na Windows.
- ~~**Jazyk aplikace jen pro aplikaci**~~ — hotovo (`DesktopAppLanguage`; `docs/poznatky/desktop.md`,
  „Jazyk aplikace").
- **Úprava fotky, profilové video, mini aplikace (WebView), poloha, QR čtečka, kalendář,
  kontakty** — vše `actual`, které dnes říká „neumí".
- ~~**Desktopové chování**~~ — hotovo: nabídka „Chaty" se zkratkami v liště macOS
  (`docs/poznatky/navigace.md`, „Klávesové zkratky"), pamatovaná velikost okna, ikona
  v oznamovací oblasti a jedna instance (`docs/poznatky/desktop.md`).

## Rozdělení `:client:shared`: vrstva kolem TDLib do `:client:core`

**Hotovo** (24. 10. 2026, kroky 1–3c níž, každý vlastní PR). Zbývá jen to, co potřebuje Wear OS.

**Proč i bez Wear OS:** hranice kolem TDLib a wrapperu (nejdůležitější pravidlo projektu) dnes
drží jen dohoda. Po rozdělení je wrapper závislost jen modulu `core` a `import dev.g000sha256.tdl.*`
ve `features` se nepřeloží — výměna wrapperu za vlastní `cinterop` pak opravdu znamená přepsat
jeden modul. Wear OS (sekce níž) je druhý užitek, ne důvod: hodinky by stály jen na `core`
a netahaly UI telefonu a jeho knihovny.

Dělit po vrstvách, ne po funkcích: funkce mají mezi sebou cykly (chats ↔ conversation ↔ profile
↔ settings ↔ stories) a rozplétat je kvůli tomu netřeba.

```
client/
  resources/   :client:resources – composeResources (texty, ikony, fonty), Res, plume.translations
  core/        :client:core      – core/telegram a co táhne: rozhraní hovorů, connectivity, secrets,
                                   formátování textů (UiText), BuildConfiguration; jediný s TDLib
  core-testing/ :client:core-testing – náhražky typů z core (Fake…, Unimplemented…, testMessage)
  shared/      :client:shared    – features, téma, navigace, komponenty, AppSettings, notifikace;
                                   Haze, Coil, Media3, CameraX, ML Kit, LiteRT, Glance, Play, ntgcalls
  androidApp, desktopApp, iosApp – jako dnes, nad :client:shared
  wearApp      :client:wearApp   – jen :client:core (+ resources) a Compose for Wear OS
```

**Rozhodnuto (24. 10. 2026):** prostředky ve vlastním modulu, ne v `core` (hranice kolem TDLib by
jinak nesla ikony a fonty aplikace); náhražky v `:client:core-testing`, ne kopie; **balíčky beze
změny** — `core.ui`, `core.calls` a další budou rozdělené mezi dva moduly, Kotlin to unese
a přejmenování by byly stovky importů bez užitku; `AppSettings` zůstává ve `shared`
(repository ho nepotřebují).

**Co do `core` patří, změřeno** (graf závislostí mezi soubory od `core/telegram`, s viditelností
source setů a `expect`/`actual`): po kroku 3a ~200 souborů a ~39 tisíc řádků, z toho 36 tisíc
`core/telegram`; žádné Compose UI, ML Kit, LiteRT, CameraX, Glance, Media3, Coil, Haze ani ntgcalls.
Z `core/ui` jen formátování textů (`UiText`, `ResourceStrings`, `TimeFormat`, `Durations`,
`Distances`, `NumberFormat`, `DateTimeText`, `FileSizeText`, `SearchHighlight`, štítky stavu
a aktivity). `TimeFormat` → `KeyboardShortcuts` v grafu je falešná shoda jména `label`.

**Výchozí stav (říjen 2026):** shared má ~190 tisíc řádků (`features` 127 tisíc, `core` 61 tisíc,
z toho `core/telegram` 36 tisíc). `core/telegram` z `features` neimportuje nic; z ostatního `core`
bere `UiText` / `getString`, rozhraní hovorů, connectivity, settings, secrets, location, contacts
a audio, z Compose jen `StringResource`. Z `core` do `features` mířila tři místa (`AppSettings`,
`WidgetChats`, `PhotoDescriptionWorker`) a pět testů; rozpojené v kroku 2.

**Další přínos:** změna ve `features` nepřekládá vrstvu kolem TDLib a testy `core` jedou
bez Compose UI.

**Kde to drhne:**

- ~~**Texty a `Res`.**~~ `Res` se generuje pro každý modul zvlášť a 219 souborů bere přes jeden
  `Res` texty i ikony — texty v `core` a ikony ve `shared` by znamenaly dvě třídy `Res` (ve
  stejném balíčku to nejde) a aliasy importů. Proto všechny prostředky do `:client:resources`
  se stejným balíčkem `Res` (`eu.tintera.plume.resources`): ve `features` se nezmění import.
  Hotovo v kroku 3b; ověřeno na Androidu (česky i anglicky), desktopu (jar) a iOS.
- **Hovory.** `TdlCallRepository` mluví s `CallMedia` — rozhraní v `core/calls`, implementace
  s ntgcalls zůstává ve `shared` a zapojí se přes Koin (dnes už je to tak); Wear hovory nemá.
- ~~**Notifikace**~~ — hotovo v kroku 3a: `NotificationPresenter` je v `core/notifications`
  ve `shared`. Rozhraní netřeba, `core` ho nevolá — je to konzument `NotificationRepository`.
- ~~**`internal`**~~ — v kroku 3c se zveřejnilo 19 deklarací podle chyb překladu (formátování
  dat, diakritika, platforma desktopu, `TdlCallRepository`, `TdlDownloadsRepository`); 16 míst
  ve `shared` dostalo lokální proměnnou místo smart castu na vlastnost z cizího modulu.
- ~~**Gradle a hook `TdlibJsonRepair`**~~ — hotovo: kontroly, jazyky a hook jsou konvenční
  pluginy v `build-logic/` (CLAUDE.md, „Struktura modulů"); `wearApp` si vezme
  `plume.tdlib-android`. Konvence pro cíle KMP zatím není — s jedním KMP modulem by neměla
  komu sloužit; přibude při vyčlenění `core`, až bude vidět, které cíle a binárky mu patří.
- **iOS dál jeden framework** z UI modulu, `core` se do něj zabalí (dva frameworky = dva
  runtime). Pravidlo „jediný modul s KMP pluginem" v `CLAUDE.md` se přepíše.
- ~~**Koin**~~ — `coreModule()` v `core` (48 definic: klient, přihlášení, repository a pomocníci i s eager definicemi, `APP_SCOPE`, platformní cesty, síť, popředí a klíč databáze i s úložišti tajemství); `appModule()` ho zahrne. Hovory (`CallMedia`…) dál registruje aplikace.
- ~~**Testovací náhražky**~~ — v `:client:core-testing` (9 souborů), na kterém visí testy obou
  modulů. 115 testů přešlo do `core` (dva rozdělené testy z `features`), součet testů obou
  modulů sedí na kus s tím před rozdělením.
- **iOS:** všechno, co volá Swift (`IosPush`, `IosLinks`, AI od Applu, `MainViewController`),
  zůstává ve `shared` — framework `export` nepotřebuje.
- **Mimo `core`/`features` zbývá:** `TdlTelegramSession` čte `BuildConfiguration` (buildConfig
  s api_id půjde s ním do `core`) a `core/ui/HideSystemBars.ios.kt` bere `StatusBarHost`
  z kořenového balíčku (`core/ui` zůstává v UI modulu, takže nevadí).

**Pořadí, každý krok vlastní PR:**

1. ~~`build-logic` s konvenčními pluginy~~ — hotovo (kontroly, jazyky, hook).
2. ~~Rozpojit místa, kde `core` sahá do `features`~~ — hotovo, hlídá `verifyCoreLayering`.
3. Vyčlenit `:client:core`, ve třech PR:
   - **3a** ~~`NotificationPresenter` ven z `core/telegram`, `GeoPoint` a `PhoneContact` do
     vlastních souborů~~ — hotovo; uzávěr `core/telegram` spadl z 325 souborů na ~200.
   - **3b** ~~`:client:resources`: všechny `composeResources`, `plume.translations`, seznam
     jazyků, `generateLocaleConfig`, `release_notes.py exempt`~~ — hotovo. Weblate se zakládá až
     s veřejným repem (sekce „Komunitní překlad"), maska tam už míří na nové místo.
   - **3c** ~~`:client:core`: `git mv` uzávěru (202 souborů), `tdl-coroutines` jen tady,
     `BuildConfiguration`, `internal` → veřejné, testy a `:client:core-testing`, CLAUDE.md~~ —
     hotovo. `coreModule()` v Koinu přibyl hned potom. **Vědomě odložené:** konvence pro cíle
     KMP v `build-logic` (tři moduly ji mají ručně, jsou to čtyři řádky).
4. Pak případně `:client:wearApp` (MVP v sekci Wear OS) — vezme si `coreModule()`.

## Wear OS

**Stav (25. 10. 2026):** samostatná aplikace `:client:wearApp` nad `:client:core` je v interním
testu v Play a běží na Pixel Watch. Umí přihlášení QR kódem (i heslo, nebo potvrzením z telefonu), seznam chatů, konverzaci
(fotky, samolepky, videa a kolečka náhledem, hlasovky s vlnou, ankety, tlačítka botů, odkazy do
telefonu, citace, předěly dní, stav doručení), odpověď, reakce, nahrávání hlasovek, vlastní push
a notifikace s akcemi, dlaždice a komplikaci, Nastavení (vyčištění mezipaměti, odhlášení),
hlášení pádů a M3 Expressive vzhled. Jak je to poskládané a co se při tom zjistilo:
`docs/poznatky/wear-os.md`.

Hodinky navíc dostávají notifikace zrcadlené z telefonu (`MessagingStyle`, odpověď, Přečteno,
Ztlumit — `docs/poznatky/notifikace.md`); po přihlášení na hodinkách se zrcadlení vypne.

**Zbývá:**

- **Ověřit na skutečných hodinkách se spárovaným telefonem:** přihlášení telefonem (Data Layer na emulátoru neověřený), push s ukončenou aplikací (na
  emulátoru FCM nedoručil), že zrcadlené notifikace po přihlášení zmizí, samotné odhlášení.
- **Play:** snímky hodinek z testovacích účtů do `play/graphics/<locale>/wearScreenshots/`
  (workflow Play Listing je umí), pak přihlášení k distribuci pro Wear OS s kontrolou kvality.
- **Baterie a úložiště** sledovat v provozu: TDLib drží spojení a databázi; mezipaměť se uklízí
  sama (3 dny, strop 100 MB), média se stahují jen zobrazená.

**Další funkce na hodinkách (2026-10-25, podle užitku):**

1. ~~**Nabídka nad zprávou**~~ — hotovo (reakce v mřížce, odpověď s citací, smazání vlastní).
2. ~~**Přepis hlasovky na požádání**~~ — hotovo (server → telefon → hodinky); úspěšné cesty
   čekají na ověření na Pixel Watch se spárovaným telefonem.
3. ~~**Poslání samolepky**~~ — hotovo (oblíbené a nedávné).
4. ~~**Akce nad chatem**~~ — hotovo (podržením karty: přečteno / nepřečteno, ztlumit, archivovat).
5. ~~**Hledání chatu a nová konverzace**~~ — hotovo (diktováním, chaty i kontakty).
6. ~~**Archiv a složky**~~ — hotovo (výběr složky s počty nepřečtených, archiv, vrácení z něj).
7. ~~**Rychlá poznámka do Uložených zpráv**~~ — hotovo (dlaždice a komplikace, diktování rovnou).
8. ~~**Poslání polohy**~~ — hotovo (jednorázově, po potvrzení; poloha v bublině s mapou v telefonu).
9. ~~**Kontakt a záznam hovoru jako prázdná bublina**~~ — opraveno (řádek se jménem a číslem,
   u hovoru směr, popisek a délka).

Vědomě ne: hovory, správa skupin a nastavení účtu (zůstávají na telefonu), GIFy a animace,
přehrávání videa a hudby, mapy u polohy, Instant View, formátování textu — na malém displeji
drahé na baterii a výkon, nebo skoro nic nepřidají. Gemini Nano na hodinkách není, návrhy
odpovědí jsou jen ze systému.

## Obrazovky a sekce, které nejsou

Sekce jsou dnes dvě: Chaty a Nastavení (z menu účtu, rail ani lišta nejsou). Routy: `ChatsRoute`, `SettingsRoute`,
`SettingsSectionRoute`, `ArchiveRoute`, `ArchiveConversationRoute`, `ArchiveChatProfileRoute`,
`ConversationRoute`, `ChatProfileRoute`, `MediaRoute`, `ForwardRoute`, `ShareRoute`, `NewChatRoute`
(`core/navigation/Routes.kt`).

- ~~**Přihlášení: zapomenuté heslo je slepá ulička.**~~ — hotovo (kód na obnovovací e-mail,
  nové heslo s nápovědou, dialog u účtu bez e-mailu; `docs/poznatky/prihlaseni.md`, „Obnova hesla a cesta zpátky
  k číslu"). Zbývá:
  - **Ověřit se skutečným účtem** s heslem a obnovovacím e-mailem — zatím jen podstrčenou session.
  - **Reset účtu bez obnovovacího e-mailu** (`deleteAccount` s prázdným heslem, provede se po
    týdnu). Dnes dialog posílá do oficiální aplikace. Než se postaví: jak TDLib hlásí čekání
    (server má `2FA_CONFIRM_WAIT_…`), v jakém stavu automat zůstane a co ukázat, když se uživatel
    během týdne vrátí. Ověřit se to na testovacím účtu nedá.
  - **Telegram Passport** (`hasPassportData`): jestli obnova hesla uložené údaje smaže a má se
    to říct předem, ověřené není.

- ~~**Archiv**~~ — hotovo (seznam v menu účtu, archivace a vrácení z nabídky chatu i tahem do
  strany s „Vrátit", počet nepřečtených u vstupu, nastavení archivu). Poučení v `docs/poznatky/seznam-chatu.md`,
  „Archiv". Zbývá:
  - ~~**Ověřit nastavení archivu proti druhému účtu.**~~ — „Nechávat chaty v archivu" ověřené se
    dvěma účty (zapnuté: archivovaná skupina po nové zprávě zůstala v archivu; vypnuté: vrátila
    se). „Archivovat od neznámých" testovacímu účtu server nepovolil
    (`can_archive_and_mute_new_chats_from_unknown_users`), Plume ji proto nenabízí — ověřit
    s účtem, kterému ji server dá (Premium), i „Vrátit mezi chaty" z pruhu u automaticky
    archivovaného chatu.
- ~~**Detail chatu / profil.**~~ — hotovo (ťuknutí na hlavičku: fotka, jméno, ztlumení, hledání,
  údaje, členové skupiny; `docs/poznatky/profil-chatu.md`, „Profil chatu"). Zbývá:
  - ~~**Sdílená média**~~ — hotovo (záložky Členové / Média / Soubory / Odkazy s dotahováním
    stránek; `docs/poznatky/profil-chatu.md`, „Sdílená média v profilu"). ~~Hledání uvnitř záložky~~ — hotovo
    (soubory, odkazy, hudba). ~~Velký náhled u souborů~~ — hotovo (obrázek a video
    poslané jako soubor se ze záložky Soubory otevřou v prohlížeči).
  - ~~**Akce navíc**~~ — hotovo: kopírovat odkaz (i pozvánku pro správce), zablokovat, smazat
    chat / opustit skupinu či kanál (⋮ v liště) a ~~**upravit skupinu**~~ (název, popis, fotka,
    práva členů; `docs/poznatky/profil-chatu.md`, „Úprava skupiny") a ~~**správa jednotlivých členů**~~ (dlouhý
    stisk na členovi: povýšit na správce a upravit jeho práva, sesadit, omezit s dobou, zrušit
    omezení, štítek, vyhodit s dobou, předat vlastnictví; `docs/poznatky/profil-chatu.md`, „Správa členů").
    ~~Sdílet kontakt~~ — hotovo (⋮ → Sdílet kontakt). ~~Správa pozvánek~~ — hotovo (odkazy
    s limitem, platností a schvalováním, zrušení a mazání, žádosti o vstup; `docs/poznatky/profil-chatu.md`,
    „Pozvánky a žádosti o vstup"; sdílení odkazu, odkazy ostatních správců, kdo se přidal
    a pruh s čekajícími žádostmi v konverzaci jsou hotové; skutečná žádost, přijetí, odmítnutí
    a „kdo se přidal" ověřené dvěma účty).
    ~~Uložení úpravy skupiny, práv člena, omezení, vyhození a štítku~~ — ověřené v testovací
    skupině s botem. Zbývá **dokončený převod vlastnictví**: testovací účet …029 má dvoufázové
    ověření od 12. 10. 2026 a Telegram převod pustí po týdnu — **od 19. 10. 2026** ve skupině
    „Plume test predani" (Pixel 7 → Pixel 5). Heslo zadává uživatel.
  - ~~**Seznam členů velké skupiny**~~ — hotovo (stránky po 50, hledání, věta u skupiny se
    skrytými členy; `docs/poznatky/profil-chatu.md`, „Členové velké skupiny"). **Zbývá ověřit ve skupině, kde členy
    vidět jsou** — testovací účet v takové velké není; ověřené je to sondou s malou stránkou.
- ~~**Pořadí připnutých chatů**~~ — hotovo: „Změnit pořadí připnutých" v nabídce nad připnutým
  chatem, tažení za úchyt, v hlavním seznamu, archivu i složce (`docs/poznatky/seznam-chatu.md`, „Akce nad chatem").
- ~~**Složky chatů**~~ — zobrazení i správa hotové (řádek pod pilulkou, přepínání, nepřečtené;
  Nastavení → Obecné → Složky chatů: založit, upravit, smazat, přeřadit, doporučené; „Přidat do
  složky" nad chatem; `docs/poznatky/seznam-chatu.md`, „Složky chatů"). Zbývá:
  - **„Všechny chaty" jinde než první** (`mainChatListPosition`, jen Premium) — správa ho drží
    na místě, kde je.
  - ~~**Sdílené složky**~~ — hotovo: náhled a přidání z odkazu, odkazy na vlastní složku i jejich
    úprava, nové chaty v přidané složce a chaty k opuštění při jejím smazání; přidání cizí
    složky, nové chaty od autora i smazání s opuštěním chatů ověřené dvěma účty.
  - **Štítky složek u chatů** (`toggleChatFolderTags`, jen Premium) a barva složky.
  - ~~**Ikona složky**~~ a ~~**„Nechávat chaty ze složek v archivu"**~~ — hotovo. Zápis volby
    archivu ověřený není (mění nastavení účtu).
- **Hledání.** ~~Chaty ve všech složkách (lokálně i na serveru), historie hledání a nejčastější
  lidé, totéž ve výběru pro přeposílání a sdílení~~ — hotovo (`ChatSearch`, poučení
  v `docs/poznatky/hledani-a-zakladani-chatu.md`, „Hledání chatů"). Zbývá, po krocích:
  1. ~~**Globální hledání a zprávy**~~ — hotovo (sekce Globálně a Zprávy, skok na nalezenou
     zprávu, „Přidat se" u veřejného chatu). Zbývá:
     - ~~**Filtry hledání zpráv**~~ — hotovo (čipy druhu chatu a období nad výsledky).
     - ~~**Jméno odesílatele u nalezené zprávy ve skupině**~~ — hotovo („Jana: …", „Vy: …").
     - ~~**Kanál po „Přidat se" pořád nabízí vstupní pole**~~ — hotovo: kanál bez práva
       publikovat má místo pole Ztlumit (`docs/poznatky/konverzace-psani.md`, „Práva psát").
  2. ~~**Klikatelné zmínky a hashtagy v textu**~~ — hotovo (`@jméno` otevře chat, `#tag`
     hledání zpráv). Odkazy na Telegram (chat, zpráva, pozvánka, sada samolepek) se otevírají
     v aplikaci (`docs/poznatky/konverzace-zobrazeni.md`, „Odkazy na Telegram"); ~~boti se `start`, mini aplikace, sdílené
     složky, člověk podle čísla a kontaktního odkazu, `?text=` a `?profile`, `t.me/share`,
     Uložené zprávy, proxy (`t.me/proxy`, `t.me/socks`)~~ hotové. ~~Odkazy do Nastavení
     (`tg://settings/…`), nová konverzace, skupina a kanál, kontakty, hledání, videochat
     a přihlašovací kódy~~ — hotovo (přihlašovací odkazy se jen vysvětlí, neotevřou). Do
     prohlížeče dál jdou platby, stories, dárky, témata vzhledu a jazykové balíčky. ~~**Veřejné příspěvky
     s tagem**~~ — hotovo (přepínač „Vaše chaty / Veřejné příspěvky" u hashtagu).
  3. ~~**Hledání v konverzaci**~~ — hotovo (lupa v hlavičce, šipky mezi výsledky, viz
     `docs/poznatky/konverzace-pohyb.md`, „Hledání v konverzaci"). Hledané slovo je v bublinách zvýrazněné.
     Ve skupině jde hledat i podle člena (čip v hlavičce hledání).
  4. ~~**Filtry obsahu** (média, soubory, odkazy), hlavně pro profil chatu~~ — hotovo (záložky
     profilu s hledáním). Zbývá hledání médií podle popisku, kdyby o něj byl zájem.

  ~~Na rozloženém Foldu by místo celoobrazovkového hledání sedělo `ExpandedDockedSearchBar`~~ —
  hotovo (panel pod pilulkou, `docs/poznatky/hledani-a-zakladani-chatu.md`, „Hledání chatů“); ~~zpět s otevřenou klávesnicí
  zavřel celé hledání~~ — opraveno (napřed schová klávesnici).
- ~~**Nová konverzace a kontakty.**~~ — hotovo (FAB „Nová zpráva“ v seznamu chatů,
  `NewChatRoute`, Uložené zprávy, kontakty z TDLib s dostupností, globální vyhledávání lidí podle
  @username; poučení v `docs/poznatky/hledani-a-zakladani-chatu.md`, „Nová konverzace").
  ~~**Založení skupiny a kanálu**~~ — hotovo („Nová skupina" / „Nový kanál" nad kontakty, výběr
  členů, název a fotka, u kanálu popis, ~~soukromý / veřejný s odkazem~~ a ~~pozvánka lidem,
  které server nepustil~~ a ~~veřejný odkaz v úpravě chatu~~; `docs/poznatky/hledani-a-zakladani-chatu.md`, „Založení skupiny
  a kanálu" a „Veřejný odkaz v úpravě chatu"). ~~Ověřit pozvánku se skutečným člověkem,
  který přidávání zakazuje~~ — ověřeno se dvěma účty. ~~Veřejná základní skupina~~ — hotovo
  (převod na supergroupu, `docs/poznatky/profil-chatu.md`, „Převod základní skupiny").
  ~~**Přidat kontakt podle telefonního čísla**~~ — hotovo („Nový kontakt" v Nové zprávě,
  `docs/poznatky/hledani-a-zakladani-chatu.md`, „Nový kontakt podle čísla"). ~~Synchronizace
  kontaktů z telefonu~~ — hotovo jako volba (Nastavení → Soukromí, výchozí vypnuto; tamtéž,
  „Synchronizace kontaktů z telefonu"). Zbývá iOS a deklarace kontaktů v Play Console.
  ~~**Můj QR kód a odkazy zvenčí**~~ — hotovo, NEOVĚŘENO na zařízení (menu účtu, Nastavení →
  Obecné, „Naskenovat QR kód" v Nové zprávě, `t.me` / `tg://` z jiných aplikací;
  `docs/poznatky/hledani-a-zakladani-chatu.md`, „Můj QR kód a odkazy zvenčí"). Zbývá:
  - **Výměna dotykem (Quick Share „Tap to Share", od 8/2026 na Pixelu 6+).** Na ploše si
    telefony vymění SYSTÉMOVÉ kontakty, obsah jde přes nabídku Sdílet a přiložení telefonů.
    Veřejné API pro aplikace dohledané není, takže cesta je nabídka Sdílet: „Sdílet" v sheetu
    QR kódu posílá odkaz textem — ověřit, že dorazí dotykem, a zvážit vizitku (`.vcf` se
    jménem, číslem jen se souhlasem a odkazem `t.me`), kterou si druhý uloží i bez Plume.
  - ~~**Kontaktní odkaz otevřít profilem s viditelným „Přidat do kontaktů"**~~ — hotovo
    (`docs/poznatky/hledani-a-zakladani-chatu.md`, „Kontaktní odkaz do profilu"). Skutečný
    `t.me/contact/…` od jiného účtu zbývá ověřit.
  - ~~**iOS: odkazy zvenčí** (`CFBundleURLTypes` pro `tg`, předání z `onOpenURL` do
    `LinkOpenRequests`)~~ — hotovo pro `tg://`; `t.me` jako Universal Link nejde (doménu vydává
    Telegram), viz `docs/poznatky/hledani-a-zakladani-chatu.md`. Ověřit ze Safari přes stránku
    `t.me` na zařízení.
- **Ochrana před spamem.** ~~Zablokovat z nabídky nad chatem a při mazání rovnou zablokovat
  i nahlásit~~, ~~průvodce nahlášením s volbou důvodu~~ (i zprávy z nabídky nad zprávou),
  ~~pruh nad konverzací u chatu od neznámého~~ — hotovo (`docs/poznatky/spam-a-blokovani.md`, „Blokování a nahlášení
  spamu"). Zbývá:
  - ~~**„Vrátit mezi chaty" v pruhu**~~ — hotovo (`docs/poznatky/spam-a-blokovani.md`, „Pruh nad konverzací").
    **Zbývá ověřit na zařízení**, že se konverzace otevřená z archivu přestěhuje do hlavního
    seznamu a chat se odtlumí; emulátor při zkoušce spadl a odhlásil se.
  - **Zbylé druhy pruhu nad chatem.** `ChatActionBar` má v TDLib 15 šest druhů a „akce, které
    musí jít z pruhu udělat" (dokumentace TDLib); Plume kreslí jen `ReportSpam`, `ReportAddBlock`
    a `AddContact`. Chybí:
    - ~~**Údaje o protějšku u `ReportAddBlock`**~~ — hotovo (`CounterpartAccount`). Česká
      podoba řádků ověřená skutečným pruhem se dvěma účty.
    - ~~**Upozornění na emoji status** u `ReportSpam` / `ReportAddBlock`~~ — hotovo spolu
      s emoji statusy (`SpamBar.warnsEmojiStatus`), ověřené podstrčeným pruhem.
    - ~~**`SharePhoneNumber`**~~ a ~~**`JoinRequest`**~~ — hotovo (`ChatNoticeBarCard`, ověřené
      podstrčeným pruhem; skutečné nasdílení čísla ověřené není).
    - ~~**`InviteMembers`**~~ — hotovo (`InviteMembersBar`, výběr `AddMembersScreen`, i z ⋮
      v profilu). Pruh ověřený podstrčeným stavem, přidání testovacím botem; odmítnutí kvůli
      soukromí ověřené se dvěma účty při zakládání skupiny (z ⋮ v profilu ne).
    - ~~**Pruh firemního bota** (`UpdateChatBusinessBotManageBar`, jen účty s Telegram Business)~~
      — hotovo (`docs/poznatky/spam-a-blokovani.md`, „Pruh firemního bota"); skutečný pruh
      neověřený (účet Business nemá).
  - ~~**Nahlásit z režimu výběru víc zpráv**~~ — hotovo (vlajka v liště výběru).
  - ~~**Ověřit skutečný pruh (`UpdateChatActionBar`)** na chatu od cizího~~ — ověřeno se dvěma účty
    (`docs/poznatky/spam-a-blokovani.md`, „Ověření"); nahlášení se vědomě nezkoušelo.
  - ~~**Seznam blokovaných v Nastavení**~~ — hotovo (Obecné → Soukromí → Blokovaní), ~~i se
    „Zablokovat uživatele"~~ (kontakty a známé soukromé chaty, s potvrzením).
- **Přihlašovací obrazovka** — hotová (výběr země, formátování čísla, návrat z QR na číslo,
  expressive přestavba, ~~zpět z kódu a hesla k číslu~~, ~~text polí v Compose stavu místo
  `StateFlow`~~; `docs/poznatky/prihlaseni.md`, „Přihlašovací obrazovka").
- **Sdílení ze systému do aplikace.** ~~Android~~ — hotovo (text, odkaz, jeden i víc
  souborů; čip „Sdílíte: …", obsah čeká nad vstupním polem). Poučení v `docs/poznatky/sdileni.md`, „Sdílení do
  aplikace". Zbývá:
  - ~~**Direct Share**~~ — hotovo, i na iOS (darování `INSendMessageIntent`, řádek návrhů
    v systémové nabídce). **Na skutečném iPhonu řádek návrhů ověřený není** — simulátor ho
    neukazuje.
  - ~~**Opakované sdílení do otevřené obrazovky „Komu: …" ji zavře**~~ — opraveno (obsah
    vzniká rovnou s chatem, obrazovka zahazuje jen své sdílení; `docs/poznatky/sdileni.md`, „Opakované sdílení").
  - ~~**iOS: Share Extension**~~ — hotovo (výběr chatu v rozšíření, App Group). Zbývá
    ověřit na zařízení (App Group v Developer účtu).
  - ~~**Sdílení jako kontext místo samostatného výběru**~~ — hotovo na Androidu: místo výběru
    chatu seznam chatů s čipem „Sdílíte: …", vložení ťuknutím v otevřené konverzaci, ✕ zahodí,
    přežije zabití procesu (nejdéle 24 h); `docs/poznatky/sdileni.md`, „Čip místo výběru chatu".
    Zbývá:
    - **iOS rozšíření** dál vybírá chat samo. Mohlo by jen předat obsah (inbox bez `chatId`)
      a výběr nechat aplikaci — pak by na obou platformách fungovalo sdílení stejně. Na iOS by
      k tomu chyběla i perzistence čipu (`ShareStore` je jen na Androidu).
    - **Ověřit na zařízení** vypršení po 24 h, sdílení víc souborů z Fotek, tmavý motiv
      a paletu se zvýšeným kontrastem (tlačítko „Vložit sem" je `tertiary` na
      `tertiaryContainer`).

## Mezery proti API TDLib

Porovnání API ve wrapperu `tdl-coroutines` 15.0.0 (rozdíl k 16.0.0 níž) s tím, co volá `core/telegram` (říjen 2026,
zopakované 18. a 19. 10.): **z 1022 funkcí se volá 400, ze 190 updatů se odebírá 89** (při
prvním průchodu 294 a 70, 18. 10. 395 a 89). Většinu zbytku tvoří Bot API,
Telegram Business, Passport a testovací volání, které klient nepotřebuje. Sem patří jen to, co
v jiné sekci roadmapy (nebo v [roadmap-konverzace.md](roadmap-konverzace.md)) zapsané nebylo;
hovory mají své místo jinde.

**Pokrytí po oblastech** (19. 10. 2026; funkce i updaty, volané / všechny; oblast podle jména
funkce, takže hranice jsou hrubé):

| Oblast | Pokrytí | Stav |
| --- | --- | --- |
| Přihlášení, relace, účet, uživatelská jména | 50 / 66 | hotové; zbývá OAuth na webu, přístupové klíče (cizí klient nesmí) |
| Témata, Uložené zprávy, přímé zprávy kanálu | 28 / 40 | hotové; zbývají štítky Uložených zpráv a drobnosti |
| Samolepky a emoji | 32 / 62 | prohlížení a posílání hotové; tvorba vlastních sad ne |
| Reakce | 13 / 18 | hotové; placené reakce ne |
| Notifikace | 16 / 23 | hotové; reakce TDLib neumí (viz níž) |
| Mini aplikace a boti (uživatelská strana) | 20 / 50 | hotové i zápis, sdílení, výběr chatu a stažení z mini aplikace; zbývá úložiště zařízení, poloha, nabídka příloh; zbytek jsou volání jen pro boty |
| Soubory a stahování | 6 / 31 | stahování po jednom; správce stahování a předběžné nahrávání ne |
| Hovory ve dvou | 6 / 12 | hotové na Androidu a macOS; seznam hovorů a hodnocení ne |
| AI od serveru | 3 / 13 | úpravy textu hotové; vlastní styly a články ne |
| Jazyk a překlad | 1 / 17 | překlad zprávy a jazykový balíček hotové; zbytek nepotřebujeme |
| **Příběhy (stories) a živé příběhy** | **26 / 59** | **prohlížení, reakce, odpovědi, zveřejnění (fotka i video, blízcí přátelé), diváci, skrytí do archivu, profil s alby a jejich správou, nahlášení a upozornění, správa zveřejněného (popisek, viditelnost, profil, připnutí) a archiv vlastních hotové; sledování živých příběhů na Androidu**; zprávy ve vysílání a vlastní vysílání ne |
| Skupinové hovory a videochaty | 6 / 46 | pruh nad konverzací (sekce Hovory); příjem obrazu a zvuku pro živé příběhy a poslech videochatu |
| Premium a boosty | 1 / 18 | ne |
| Vzhled od Telegramu (pozadí, motivy, barvy, emoji status) | 1 / 33 | vědomě ne (`roadmap-konverzace.md`, „Vědomě NE") |
| Hvězdy, platby, TON, affiliate | 0 / 59 | ne — rozhodnutí, ne mezera (viz níž) |
| Dárky | 0 / 43 | jen karta dárku ve zprávě |
| Telegram Business a rychlé odpovědi | 0 / 40 | ne (chce předplatné Business) |
| Komunity | 0 / 6 | ne (novinka TDLib; 16.0.0 přidal fotku, práva členů a smazání) |
| Passport | 1 / 12 | ne |
| Jen pro boty, podporu Telegramu, oficiální aplikace, testy, logy | 7 / 129 | nepatří do klienta |

Porovnání se dá zopakovat: jména funkcí z `TdlClient.kt` ve zdrojovém jaru wrapperu
(`grep -oE "suspend fun [a-zA-Z]+"`, updaty `val [a-zA-Z]+Updates`) proti
`grep -rl "dev.g000sha256.tdl" client` a hledání `\bjméno\(`. **Grep po jednom jménu přes celý
kód trvá přes dvě minuty**; rychlé je načíst jména i spojený zdroj do Pythonu a porovnat množiny. Výsledek je hrubé síto:
funkce, která se nevolá, ještě neznamená chybějící funkci (stejnou věc může dělat jiné volání).

**Co přinesl wrapper 16.0.0 (TDLib 1.8.68)** — rozdíl zdrojových jarů 15.0.0 a 16.0.0
(25. 10. 2026): 1022 → 1065 funkcí, 76 nových typů, jeden ubyl (`ServiceUpdate`). Skoro celý
přírůstek je **vestavěná peněženka TON** (38 funkcí: stav a založení peněženky, převody,
transakce, NFT, záloha a tajná fráze, TON Connect pro dApps, nákup kryptoměny přes on-ramp,
kurzy měn; vše podmíněné volbou `can_use_ton_wallet`). Spadá pod „Hvězdy, platby, dárky, TON"
ve „Vědomě mimo" níž — peněženka v aplikaci z Play je navíc rozhodnutí o zásadách Play
pro kryptoměny, ne jen o fakturaci. Co z toho stojí za to i bez peněženky:

- ~~**Adresa TON v článku zmizí.**~~ — opraveno (`docs/poznatky/typy-zprav.md`, „Článek").
- ~~**Převod z peněženky jako zpráva**~~ — hotovo, popis ve zprávě, seznamu i notifikaci
  (`docs/poznatky/typy-zprav.md`, „Převod TON"); neověřeno na skutečném převodu.
- **Odkazy na převod a TON Connect** (`InternalLinkTypeTonWalletTransfer`,
  `InternalLinkTypeTonConnect`) — ověřit, že skončí srozumitelně (neznámý odkaz), ne tichem.
- **Drobnosti:** `MessageProperties.customEmojiIds` (vlastní emoji zprávy předem, bez procházení
  entit — případně pro přednačtení), `AnimatedEmoji.isRegularEmoji`, druhé uživatelské jméno
  bota pro vlastníky s Premium (`addBotSecondaryUsername`, `Usernames.secondaryUsername`;
  Plume správu botů nemá). Komunity: `getCommunityFullInfo` nahradil `loadCommunityFullInfo`
  (vrací data místo updatu), přibyly `setCommunityPhoto`, `setCommunityPermissions`
  a `deleteCommunity` — API dozrává, oblast zůstává otevřená (viz „Novinky TDLib" níž).
- Jen pro boty / oficiální aplikace, do klienta nepatří: `replyToCallbackQueryWithEphemeralMessage`
  (náhrada `editCallbackQueryMessage`), `dismissWebToken`, `star_top_up_disabled`
  u nákupu hvězd, `ThemeSettings.hasOutgoingMessageAccentColor` (motivy vědomě ne).

**Co přinesl wrapper 17.0.0 (TDLib 1.8.69)** — rozdíl zdrojových jarů 16.0.0 a 17.0.0
(27. 10. 2026): 1065 → 1076 funkcí, 35 nových typů, jeden ubyl (`StoryAreaPosition` →
`AreaPosition`). Opravený pád na dárku bez pozadí (g000sha256/tdl-coroutines#162, viz CLAUDE.md
„Odpovědi TDLib, které wrapper nerozebere"). Při bumpu opravené: `getUserLink` má parametry pro
odkaz „psaní zdarma" (volá se se všemi `false`, chování jako dřív), `InputTextQuote.richTexts`
(citace z článku; posílá se prázdné), webová proxy se ohlásí jako nepodporovaná a servisní
zpráva `MessageUnpaidMessagesAllowedByLink` má větu. Co zbývá:

- **Webová proxy** (`ProxyTypeWeb`) — MTProto přes HTTPS přenosovou stránku ve skrytém WebView,
  kterou hostí aplikace (`updateWebProxyCarrierOpened/Data/Closed`,
  `processWebProxyCarrierMessage`, `reportWebProxyCarrierError`). Obchází blokace, kde běžná
  proxy neprojde; dnes odkaz skončí hláškou „nepodporovaná proxy" a z `getProxies` se
  vynechává.
- **Odkaz na psaní zdarma** (`getUserLink(removeMessagesFee = true, …)`,
  `UserLink.canGetPermanentLink`) — pro účet s placenými zprávami; patří k „Můj QR kód".
- **Citace z článku** (`InputTextQuote.richTexts`) — citace v nabídce nad zprávou u článků
  (`MessageRichMessage`) zatím není.
- **Drobnosti:** smazání dárku (`deleteGift`, `MessageGift.canBeDeleted`), motiv sdílení
  profilu (`setProfileShareTheme`), stažení a vklad sběratelského dárku do TON
  (`withdrawUpgradedGift`, `getUpgradedGiftDepositInfo`; spadá pod „Vědomě mimo").

**Co uživatele zablokuje nebo vypadá jako chyba** — přednostně:

- ~~**Přihlášení s e-mailovým kódem.**~~ — hotovo (zadání adresy, kód, oprava adresy, reset
  přihlašovacího e-mailu; `docs/poznatky/prihlaseni.md`, „Přihlašovací e-mail"). Zbývá ověřit
  se skutečným serverem — testovací účet e-mail nechce. Google a Apple ID vědomě ne.
- ~~**Ověření aplikace při přihlášení.**~~ — hotovo: Plume ověření (Play Integrity, APNs,
  reCAPTCHA) hned odmítá a dotaz skončí srozumitelnou větou místo věčného čekání; hlásí se do
  Crashlytics (`docs/poznatky/prihlaseni.md`, „Ověření aplikace"). Zbývá podle Crashlytics
  posoudit, jestli to server po Plume vůbec chce.
- ~~**Neodeslanou zprávu nejde poslat znovu.**~~ — hotovo („Neodesláno. Ťukni a zkus to znovu."
  pod bublinou a „Poslat znovu" v nabídce; `docs/poznatky/konverzace-psani.md`, „Neodeslaná
  zpráva a opakované odeslání"). Zbývá ověřit album a zprávu, kterou znovu poslat nejde.
- ~~**Fotka chatu se nejspíš neobnovuje živě.**~~ — potvrzeno a opraveno (`chatPhotoUpdates`
  v seznamu chatů, hlavička konverzace živě i s názvem; `docs/poznatky/seznam-chatu.md`,
  „Co položka ukazuje").
- ~~**Profil se živě neobnovuje**~~ — hotovo (`ProfileRepository.profileUpdates`;
  `docs/poznatky/profil-chatu.md`, „Profil chatu").
- ~~**Systémové updaty bez odběru:** podmínky služby, oznámení od Telegramu, zmrazený účet~~ —
  hotovo (`docs/poznatky/systemova-oznameni.md`); skutečný update od serveru neověřený.
- ~~**Doporučené akce**~~ — hotovo (karta v seznamu chatů; `docs/poznatky/systemova-oznameni.md`,
  „Doporučené akce"). ~~Nastavení přihlašovacího e-mailu~~ — hotovo (`docs/poznatky/nastaveni.md`,
  „Přihlašovací e-mail"). ~~Změna čísla~~ — hotovo (`docs/poznatky/nastaveni.md`, „Změna telefonního čísla").

**Správa skupin a kanálů** (nad rámec názvu, popisu, fotky, práv a pozvánek):

- ~~Pomalý režim, povolené reakce, ochrana obsahu a přepínače supergroupy~~ — hotovo (úprava
  skupiny → Nastavení skupiny / kanálu; `docs/poznatky/profil-chatu.md`, „Nastavení skupiny
  a kanálu"). Zbývá automatický překlad kanálu (`toggleSupergroupHasAutomaticTranslation`, chce
  úroveň boostů), strážní bot u schvalování (`guardBotUserId`) a ověřit kanál, veřejnou skupinu,
  diskusi kanálu, skrytí členů a antispam.
- ~~Seznamy vyhozených a omezených členů~~ — hotovo (úprava skupiny → Členové;
  `docs/poznatky/profil-chatu.md`, „Omezení a vyhození"). Zbývá ověřit kanál.
- ~~Při mazání zprávy člena „smazat vše od něj" a „nahlásit spam"~~ — hotovo i s vyhozením
  (`docs/poznatky/konverzace-akce.md`, „Smazání cizích zpráv správcem"). ~~Smazat reakce
  člena (`deleteAllRecentMessageReactionsFromSender`)~~ — hotovo (tamtéž). Zbývá ověřit
  nahlášení spamu.
- ~~Odepnout všechny zprávy (`unpinAllChatMessages`, `unpinAllForumTopicMessages`)~~ — hotovo
  (sheet připnutých → „Odepnout vše"). Zbývá ověřit v tématu skupiny.
- ~~Smazat skupinu nebo kanál pro všechny (`deleteChat`)~~ — hotovo (konec úpravy skupiny).
- ~~Záznam posledních akcí (`getChatEventLog`)~~ — hotovo (úprava skupiny → Poslední akce;
  `docs/poznatky/profil-chatu.md`). ~~Filtry a hledání v záznamu~~ — hotovo (tamtéž, „Hledání
  a filtry záznamu"; filtr podle autora ověřit ve skupině s víc správci). ~~Statistiky
  kanálu, skupiny a příspěvku (`getChatStatistics`, `getStatisticalGraph`, `getMessageStatistics`,
  `getMessagePublicForwards`)~~ — hotovo (úprava chatu → Statistiky, „Informace o zprávě" →
  Statistika příspěvku; `docs/poznatky/profil-chatu.md`, „Statistiky"). Ověřené jen sondou
  s podstrčenými daty — testovací účet nemá kanál ani skupinu, které by server statistiky
  vydal. Zbytky v „Prověrka 26. 10. 2026" níž.
- ~~Psaní jménem kanálu ve skupině (`getChatAvailableMessageSenders`, `setChatMessageSender`)~~ —
  hotovo (`docs/poznatky/konverzace-psani.md`, „Psaní jménem kanálu nebo skupiny"). Zbývá ověřit
  na zařízení s kanálem nebo anonymním správcem a prémiovou volbu.
- ~~Přímé zprávy kanálu (monoforum: `loadDirectMessagesChatTopics` a spol.)~~ — hotovo (zapnutí
  vlastníkem, „Napsat" u odběratele, vlákna u správce; `docs/poznatky/prime-zpravy-kanalu.md`).
  ~~Archivní větev a „Napsat" v profilu kanálu~~ a ~~navrhované příspěvky (`approveSuggestedPost`)~~
  — hotovo. Zbývají placené zprávy a placené návrhy (`addOffer`).
- ~~Zobrazení fóra jako jednoho seznamu zpráv (`toggleChatViewAsTopics`)~~ — hotovo, volba se
  ukládá na serveru (`docs/poznatky/typy-zprav.md`, „Témata ve skupinách"). Zbývá ověřit
  synchronizaci s druhým zařízením.

**Účet a nastavení:**

- ~~Úprava kontaktu: jméno, poznámka (`ImportedContact.note`, `UserFullInfo.note`) a vlastní fotka,
  kterou vidí jen uživatel (`setUserPersonalProfilePhoto`)~~ — hotovo (⋮ v profilu → Upravit
  kontakt; `docs/poznatky/profil-chatu.md`, „Úprava kontaktu"). Zbývá ověřit iOS a poznámku
  s formátováním od jiného klienta.
- ~~Vymazání historie chatu, celé i za vybrané dny (`deleteChatHistory` bez odebrání ze
  seznamu, `deleteChatMessagesByDate`)~~ — hotovo (⋮ v konverzaci → Vymazat historii…;
  `docs/poznatky/konverzace-akce.md`, „Vymazání historie"). Plume dřív uměl jen smazat celý chat.
- ~~Tiché odesílání jako výchozí pro chat (`toggleChatDefaultDisableNotification`)~~ — hotovo
  jako zvoneček v kanálu (`docs/poznatky/konverzace-psani.md`, „Výchozí odesílání bez zvuku").
- ~~Smazání všech konceptů najednou (`clearAllDraftMessages`)~~ — hotovo („Smazat vše" v přehledu
  konceptů; `docs/poznatky/seznam-chatu.md`, „Přehled konceptů").
- ~~Doporučené kanály (`getRecommendedChats`)~~ — hotovo (návrhy v hledání;
  `docs/poznatky/hledani-a-zakladani-chatu.md`, „Hledání chatů").
- ~~Nahlášení profilové fotky (`reportChatPhoto`)~~ — hotovo (⋮ v prohlížeči profilovek;
  `docs/poznatky/profil-chatu.md`, „Historie profilových fotek"). Samotné odeslání neověřené.

- ~~Změna telefonního čísla~~ — hotovo (úprava profilu → Telefonní číslo).
- ~~Smazání účtu a samosmazání po neaktivitě~~ — hotovo (Soukromí účtu → Smazání účtu;
  `docs/poznatky/nastaveni.md`, „Smazání účtu a samosmazání").
- ~~Přihlašovací e-mail~~ (hotovo) a ~~přístupové klíče~~ — seznam a odebrání hotové (Obecné →
  Soukromí); přidat klíč ani se jím přihlásit cizí klient NEMŮŽE (`docs/poznatky/nastaveni.md`,
  „Přístupové klíče").
- ~~Narozeniny~~ — hotovo: vlastní datum v úpravě profilu, datum v profilu kontaktu, karta
  s narozeninami kontaktů (`docs/poznatky/nastaveni.md`, „Narozeniny") ~~a návrh data narození
  od kontaktu~~ (odeslání z profilu, přijetí s „Nastavit"; tamtéž).
- ~~Osobní kanál v profilu~~ — hotovo (`docs/poznatky/nastaveni.md`, „Osobní kanál"). ~~Víc
  uživatelských jmen~~ — hotovo pro vlastní účet (`docs/poznatky/nastaveni.md`, „Víc uživatelských
  jmen"); kanály, skupiny a boti zbývají.
- ~~Statistika přenesených dat~~ — hotovo (Úložiště → Přenesená data; `docs/poznatky/nastaveni.md`,
  „Přenesená data"). ~~Automatické ukládání do galerie~~ — hotovo (Úložiště → Ukládání do
  galerie; `docs/poznatky/prohlizec-medii.md`, „Automatické ukládání do galerie"), ~~včetně
  zakládání výjimek z profilu chatu~~ — hotovo (řádek „Ukládání do galerie" v profilu).
- ~~Zvuky notifikací z Telegramu~~ — hotovo pro rozsahy i ~~jeden chat v profilu~~
  (`docs/poznatky/notifikace.md`, „Zvuky z Telegramu"); zbývá téma ve skupině. **Notifikace reakcí NEJDOU**: TDLib je
  neumí (push `REACT_*` odmítne, kód je zakomentovaný) a zašifrovaný push si Plume nepřečte.
- ~~Podpora — „Zeptat se"~~ — hotovo (Nastavení → Pomoc, i časté dotazy; `docs/poznatky/nastaveni.md`).

**Konverzace a hledání:**

- ~~Skok na datum~~ — hotovo (kalendář v hledání v konverzaci; `docs/poznatky/konverzace-pohyb.md`,
  „Skok na datum"). ~~Kalendář médií ve sdílených médiích (`getChatMessageCalendar`)~~ — hotovo
  (`docs/poznatky/profil-chatu.md`, „Kalendář médií"). ~~Rychlé rolování po pozicích
  (`getChatSparseMessagePositions`)~~ — hotovo v Médiích (`docs/poznatky/profil-chatu.md`,
  „Rychlé rolování v Médiích"). Zbývá ověřit dotahování starších měsíců kalendáře a tik
  rychlého rolování v chatu s delší historií; v konverzaci API nejde (prázdný filtr nebere).
- ~~Instant View článků~~ — hotovo (čtečka v aplikaci; `docs/poznatky/typy-zprav.md`, „Instant View").
  Zbývá přehrávání videa ze stránky a vložený obsah (mapy, embedy, propojené stránky).
- ~~Checklisty: zobrazit, vytvořit, doplnit, odškrtnout a upravit, servisní zprávy se jménem~~ —
  hotovo (`docs/poznatky/ankety.md`, „Seznamy úkolů"). Zbývá **ověřit akce s účtem s Premium** —
  testovací účet ho nemá, ověřené je to sondou — a ťuknutím na servisní zprávu skočit na seznam.
- ~~Výměna média při úpravě zprávy (`editMessageMedia`)~~ — hotovo (`docs/poznatky/konverzace-psani.md`,
  „Úprava zprávy"). ~~Výměna z vestavěné galerie~~ — hotovo (sheet s jednou položkou).
  ~~Výměna GIFu~~ — hotovo (GIFem z výběru).
- ~~Hudba se posílá jako soubor~~ — hotovo (`docs/poznatky/konverzace-psani.md`, „Posílání příloh
  a alb"). Zbývá desktop (JVM značky MP3 nepřečte) a ověřit iOS na zařízení.
- ~~Ťuknutí na velké animované emoji s efektem pro oba (`clickAnimatedEmojiMessage`,
  `animatedEmojiMessageClickedUpdates`)~~ — hotovo i s hmatovým tepem
  (`docs/poznatky/emoji-samolepky-reakce.md`, „Efekt animovaného emoji pro oba"). Zbývá ověřit
  efekt od protějšku a „sleduje animaci…" druhým účtem a zvuk emoji (`AnimatedEmoji.sound`).
- ~~Napovídání hashtagů a nedávných inline botů (`searchHashtags`, `getRecentInlineBots`)~~ —
  hotovo (`docs/poznatky/konverzace-psani.md`, „Napovídání hashtagů a nedávných inline botů").
  ~~Odebrat hashtag z historie (`removeRecentHashtag`, podržením v panelu)~~ — hotovo (tamtéž).
  Zbývá ověřit boty ve skupině (před členy).
- ~~Ankety: smazat možnost (`deletePollOption`)~~ — hotovo i s přidáváním odpovědí ve formuláři
  (`docs/poznatky/ankety.md`). Statistika hlasů (`getPollVoteStatistics`) zbývá — vydá ji jen
  server u větších anket (`MessageProperties.canGetPollVoteStatistics`). Graf už je
  (`StatGraphCard` ze statistik kanálu), zbývá dotaz a místo v anketě.
- ~~Označit celou složku jako přečtenou (`readChatList`)~~ — hotovo (podržení složky v liště,
  archiv ikonou v jeho liště).
- ~~Odebrat jednu položku z nedávného hledání a z nejčastějších lidí (`removeRecentlyFoundChat`,
  `removeTopChat`)~~ — hotovo (křížek v historii, podržení člověka v Lidech).
- ~~Záložky profilu, které chybí: společné skupiny (`getGroupsInCommon`), podobné kanály
  (`getChatSimilarChats`), GIFy a kolečka~~ — hotovo (`docs/poznatky/profil-chatu.md`,
  „Sdílená média v profilu"). Zbývá ověřit podobné kanály. Hlasovky jsou taky
  (vlastní záložka s přehráváním).

**Novinky TDLib, na které se zatím nesáhlo:** komunity (`createCommunity`), ~~mizející
zprávy botů (`sendEphemeralMessage`, `ephemeralContent`)~~ (hotovo, `docs/poznatky/boti.md`,
„Mizející zprávy botů"; zbývá ověřit média, téma skupiny a iOS), živé příběhy. ~~AI úpravy textu od serveru
(`composeTextWithAi`, `fixTextWithAi`, styly psaní)~~ — hotovo v sheetu „Zkontrolovat
a přepsat…" (`docs/poznatky/ai-v-zarizeni.md`, „Úpravy textu od Telegramu"); ~~překlad
a emoji z `composeTextWithAi`~~ (hotovo, čipy „Přeložit…" a „Přidat emoji"); zbývají vlastní styly (`isCustom`, odkazy na styl), oprava formátované
zprávy (`fixRichMessageWithAi`) a ~~jazykový balíček~~ (hotovo, `syncLanguagePack`; názvy stylů ale přišly anglicky i s ním —
ověřit s čerstvou instalací, jestli je server česky vůbec má). ~~Hudba v profilu (`getUserProfileAudios`)~~ —
hotovo (profil, úprava profilu, „Přidat do profilu" nad zprávou, soukromí;
`docs/poznatky/profil-chatu.md`, „Hudba v profilu"); ~~pořadí skladeb
(`setProfileAudioPosition`), přidání skladby ze souboru v telefonu~~ (hotovo, úprava profilu;
`docs/poznatky/nastaveni.md`, „Hudba ve vlastním profilu"); zbývá ověřit iOS a navazování skladeb
v cizím profilu.

### Audit 19. 10. 2026: co ještě chybí

Prošlo se všech 622 nevolaných funkcí a 101 updatů bez odběru. Níž je jen to, co v roadmapách
zapsané nebylo; seřazené podle toho, jak moc to uživatel pozná.

**Co dokumentace TDLib přikazuje a Plume nedělá** — malé, ale jde o kontrakt s knihovnou
a se serverem, takže přednostně:

- ~~**Metriky zobrazení zpráv (`sendMessageViewMetrics`)**~~ — hotovo
  (`docs/poznatky/konverzace-zobrazeni.md`, „Metriky zobrazení"). Zbývá ověřit odchod do
  pozadí a pětiminutové zobrazení na zařízení (zatím jen testy), iOS a desktop.
- ~~**Streamované odpovědi botů** (`pendingMessageUpdates`, `stopMessageDraftUpdates`,
  `stopPendingMessage`)~~ — hotovo i s tlačítkem Zastavit (`docs/poznatky/boti.md`,
  „Streamované odpovědi botů"; ověřené testovacím botem). Zbývá ověřit iOS a desktop, odpověď
  v tématu, článek (`messageRichMessage`) a zastavení z jiného zařízení.
- ~~**Vstup do chatu přes strážního bota.**~~ — hotovo pro pozvánku i „Přidat se"
  (`docs/poznatky/boti.md`, „Strážní bot u vstupu do chatu"). Zbývá ověřit se skutečným
  strážním botem — testovací účet žádnou takovou skupinu nezná. Strana správce
  (`guardBotUserId`) je zapsaná výš u nastavení skupiny.
- ~~**Mini aplikace se po odeslání zprávy nezavře** (`webAppMessageSentUpdates`)~~ — hotovo
  a ověřené testovacím botem (`docs/poznatky/boti.md`, „Mini aplikace botů").
- **Reklamy: další druhy.** ~~Nahlášení sponzorované zprávy (`reportChatSponsoredMessage`)~~ —
  hotovo, ⋮ na kartě reklamy (`docs/poznatky/konverzace-zobrazeni.md`, „Sponzorované zprávy").
  ~~Sponzorované chaty ve výsledcích hledání (`getSearchSponsoredChats`,
  `viewSponsoredChat`, `openSponsoredChat`, `reportSponsoredChat`)~~ — hotovo
  (`docs/poznatky/hledani-a-zakladani-chatu.md`, „Sponzorované chaty ve výsledcích").
  Podmínky API (§3.3) chtějí výslovně jen reklamy v kanálech, [specifikace
  reklam](https://core.telegram.org/api/sponsored-messages) ale „must" říká i u dalších druhů:
  ~~**reklama v chatu s botem** jako pruh nad konverzací~~ — hotovo, `SponsoredBar` se
  štítkem „Reklama" / „Doporučeno", barvou z `accent_color_id`, fotkou vpravo a odkazem
  s potvrzením mimo domény Telegramu (`docs/poznatky/konverzace-zobrazeni.md`, „Reklama
  u bota"). ~~**Reklamy ve videu** na celé obrazovce (`getVideoMessageAdvertisements`,
  `viewVideoMessageAdvertisement`, `clickVideoMessageAdvertisement`,
  `reportVideoMessageAdvertisement`)~~ — hotovo (`docs/poznatky/prohlizec-medii.md`, „Reklamy
  ve videu"); skutečnou reklamu od serveru zatím nevrátil žádný kanál, ověřeno s podstrčenou.
  ~~**„Informace o sponzorovi"** u reklamy v kanálu a u bota (`sponsor.info`,
  `additional_info`)~~ — hotovo, v ⋮ (`docs/poznatky/konverzace-zobrazeni.md`,
  „Sponzorované zprávy"); se skutečnou reklamou, která informace nese, ověřené není.

**Živé updaty bez odběru** (stav se přečte jednou a pak stárne):

- ~~**Ochrana obsahu** (`chatHasProtectedContentUpdates`)~~ — hotovo, výběr textu a akce
  u zprávy se přepnou za běhu (`docs/poznatky/konverzace-akce.md`, „Výběr textu myší v bublině").
  ~~Žádost o vypnutí ochrany v soukromém chatu
  (`processChatHasProtectedContentDisableRequest`)~~ — hotovo, Odmítnout / Povolit pod žádostí
  (`docs/poznatky/konverzace-akce.md`, „Žádost o vypnutí ochrany obsahu"); zbývá ověřit se
  skutečnou žádostí.
- ~~**Počet členů online** (`chatOnlineMemberCountUpdates`)~~ — hotovo, „128 členů, 12 online"
  v hlavičce skupiny a počet odběratelů u kanálu (`docs/poznatky/konverzace-zobrazeni.md`,
  „Počet členů v hlavičce").
- ~~**Zablokování z jiného zařízení** (`chatBlockListUpdates`)~~ — hotovo: profil, sheet
  smazání v konverzaci a seznam blokovaných (`docs/poznatky/spam-a-blokovani.md`, „Blokování
  a nahlášení spamu"), i „Odblokovat" místo vstupního pole v chatu se zablokovaným.
- Drobné: ~~změna nainstalované sady samolepek (`stickerSetUpdates`)~~ (hotovo, výběr i detail
  sady se přečtou znovu; `docs/poznatky/emoji-samolepky-reakce.md`, „Výběr samolepek"; zbývá
  ověřit se sadou, do které autor přidá samolepku), ~~nepřečtené zprávy
  (`unreadMessageCountUpdates`)~~ (hotovo, odznak na iOS a desktopu počítá TDLib místo součtu
  nad načtenými chaty; `docs/poznatky/notifikace.md`; iOS a desktop jen přeložené).
  Počet zpráv v tématu Uložených zpráv (`topicMessageCountUpdates`) vědomě NE: Plume ho nikde
  neukazuje, update by neměl co obnovit — přijde na řadu, až bude počet někde vidět.

**Funkce pro běžného uživatele** (bez Premium a bez plateb):

1. **Příběhy (stories).** ~~Prohlížení~~ — hotovo: lem po tvaru avataru v řádku chatu, hromádka
   v pilulce hledání a prohlížeč s přechodem mezi lidmi (`docs/poznatky/pribehy.md`).
   ~~Reakce a odpověď~~ — hotovo (srdce, výběr reakcí, odpověď do chatu s autorem).
   ~~Vlastní příběh z fotky~~ — hotovo (limit předem, plátno 1080 × 1920, viditelnost,
   počet zhlédnutí, smazání). ~~Video příběh~~ — hotovo (převod na H.265 720 × 1280, prvních
   60 s). ~~Seznam diváků~~ — hotovo (s reakcemi, po stránkách). ~~Blízcí přátelé~~ — hotovo (seznam z obrazovky nového příběhu, lem v `tertiary`).
   ~~Skrytí do archivu~~ — hotovo (⋮ v prohlížeči, hromádka v archivu). ~~Příběhy v profilu~~ — hotovo (lem kolem avataru,
   záložka „Příběhy"). ~~Nahlášení~~ — hotovo (průvodce jako u chatu). ~~Notifikace~~ — hotovo (rozsah i chat; obcházka chyby TDLib u rozsahu). ~~Alba příběhů~~ — hotovo prohlížení (čipy v záložce
   „Příběhy" v profilu) i správa u vlastního příběhu (založit, přidat, vyndat, přejmenovat,
   smazat, seřadit alba i příběhy v albu); skutečné album na zařízení ověřené není. ~~Výběr úseku a obálky videa~~ — hotovo (pás jako u profilového videa; zveřejnění
   s úsekem ověřené není). ~~Sledování živých příběhů~~ — hotovo na Androidu (ntgcalls,
   WebRTC i stream po segmentech; ověřené RTMP vysílání). ~~Zprávy ve vysílání~~ — hotovo
   (čtení, komentáře zdarma, smazání, cizí placené reakce; `docs/poznatky/pribehy.md`, „Chat
   vysílání"); ověřený jen vzhled sondou, se skutečným vysíláním zbývá ověřit (limit příběhů
   na testovacích účtech). Zbývá: vlastní vysílání (z kamery i RTMP), sledování na desktopu,
   ověření vysílání z kamery; placené zprávy a reakce patří pod hvězdy (vědomě mimo).
2. **Přihlášení na webu přes Telegram (OAuth)** — potvrzení žádosti webu dnes NEJDE kvůli TDLib
   (push `OAUTH_REQUEST` bez `data_url`, odkaz `t.me/oauth` jako mini aplikace; `docs/poznatky/prihlaseni.md`,
   „Proč tu není potvrzení žádosti webu"). Nahlásit upstream a vrátit se k tomu po bumpu wrapperu.
   ~~Automatické přihlášení po ťuknutí na odkaz (`getExternalLinkInfo`, `getExternalLink`)~~ —
   hotovo (tamtéž); zbývá ověřit na webu, který ho nabízí.
3. ~~**Seznam hovorů a hodnocení** (`searchCallMessages`, `deleteAllCallMessages`,
   `sendCallRating`)~~ — hotovo: menu účtu → Hovory a hodnocení po hovoru, o které server
   požádá, i ladicí data a log, když o ně server požádá (`sendCallDebugInformation`,
   `sendCallLog`; `docs/poznatky/hovory.md`, „Historie hovorů" a „Hodnocení hovoru"). Zbývá
   ověřit na zařízení skutečnou žádost serveru o hodnocení a o ladicí data.
4. ~~**Import chatu z jiné aplikace** (`getMessageFileType`, `getMessageImportConfirmationText`,
   `importMessages`)~~ — hotovo: export z WhatsAppu sdílený do Plume se v chatu nabídne
   k importu (`docs/poznatky/sdileni.md`, „Import chatu z jiné aplikace"). Zbývá export jako
   `.zip` (WhatsApp na iOS) a ověření se skutečným exportem z WhatsAppu.
5. ~~**Limit kanálů a skupin** (`CHANNELS_TOO_MUCH`, `getInactiveSupergroupChats`) a veřejných
   odkazů (`checkCreatedPublicChatsLimit`, `getCreatedPublicChats`)~~ — hotovo, sheet
   s neaktivními k opuštění a sheet s vlastními veřejnými chaty k odebrání odkazu
   (`docs/poznatky/hledani-a-zakladani-chatu.md`, „Limit skupin a kanálů" a „Limit veřejných
   odkazů").
6. ~~**Předem poznat, že protějšek nepřijímá zprávy** (`canSendMessageToUser`)~~ — hotovo,
   věta místo vstupního pole (`docs/poznatky/konverzace-psani.md`, „Práva psát"). Zbývá ověřit
   na zařízení s protějškem, který nové chaty omezuje nebo zprávy zpoplatnil.
7. ~~**Správce stahování** (`addFileToDownloads`, `searchFileDownloads`, `toggleDownloadIsPaused`)~~
   — hotovo, menu účtu → Stahování (`docs/poznatky/prohlizec-medii.md`, „Stahování"). Zbývá
   ověřit pozastavení na velkém souboru a hledání v seznamu (`searchFileDownloads(query)`).
8. ~~**Nahrávání hlasovky a kolečka už během nahrávání** (`preliminaryUploadFile`)~~ — hotovo
   pro hlasovku nahrávanou vcelku na Androidu 10+ a pro kolečko na Androidu (fragmentované MP4,
   `docs/poznatky/zvuk.md`, „Nahrávání do Telegramu už během nahrávání"). Zbývá iOS (Opus se
   kóduje až po skončení, kolečko se ořezává exportem), ověření na pomalé síti a přehrání
   fragmentovaného kolečka v oficiálních klientech (ověřeno jen AVFoundation a Plume).
9. ~~**Profil firmy** (`UserFullInfo.businessInfo`: otevírací doba, poloha)~~ — hotovo
   (`docs/poznatky/profil-chatu.md`, „Profil firmy"). ~~Úvodní stránka (`startPage`)
   v prázdné konverzaci~~ — hotovo („Úvodní stránka firmy v prázdném chatu"). Zbývá ověření
   na skutečném firemním účtu.
10. ~~**Dárky v profilu** (`getReceivedGifts`)~~ — hotovo, záložka Dárky
    (`docs/poznatky/profil-chatu.md`, „Dárky v profilu"). ~~Sbírky (`getGiftCollections`)~~ — hotovo, čipy nad mřížkou (skutečná sbírka ověřená není). Správa
    vlastních dárků zbývají; posílání stojí hvězdy (viz níž).
11. Drobnosti: ~~hledání v tajných chatech (`searchSecretMessages` — hledání napříč chaty je
    vynechává)~~ (hotovo v konverzaci i napříč chaty, `docs/poznatky/tajne-chaty.md`, „Hledání";
    zbývá ověřit na zařízení s tajným chatem), ~~historie hledaných hashtagů (`getSearchedForTags`, `removeSearchedForTag`)~~ (hotovo,
    `docs/poznatky/hledani-a-zakladani-chatu.md`, „Hledání chatů"),
    ~~pořadí připnutých témat Uložených zpráv (`setPinnedSavedMessagesTopics`)~~ (hotovo,
    `docs/poznatky/seznam-chatu.md`, „Témata Uložených zpráv"), ~~autor příspěvku
    kanálu pro správce (`getMessageAuthor`)~~ (hotovo — TDLib ho vydá jen v přímých zprávách
    kanálu: kdo ze správců odpověděl; `docs/poznatky/prime-zpravy-kanalu.md`; zbývá ověřit
    na zařízení), ~~blokování z chatu Odpovědi
    (`blockMessageSenderFromReplies`)~~ (hotovo, nabídka nad zprávou → Další;
    `docs/poznatky/spam-a-blokovani.md`, „Blokování z chatu Odpovědi"; zbývá ověřit se skutečným
    chatem Odpovědi), ~~nahlášení reakcí a smazání reakcí člena u jedné zprávy
    (`reportMessageReactions`, `deleteMessageReactionsFromSender`)~~ (hotovo, detail reakcí;
    `docs/poznatky/emoji-samolepky-reakce.md`, „Reakce"; zbývá ověřit na cizí reakci), ~~smazání nedávných reakcí
    (`clearRecentReactions`)~~ (hotovo, plný výběr reakcí nad zprávou; má smysl jen s Premium —
    bez něj TDLib nedávné neřadí; `docs/poznatky/emoji-samolepky-reakce.md`, „Reakce"; zbývá
    ověřit s Premium), ~~obrysy samolepek při načítání (`getStickerOutlineSvgPath`)~~
    (hotovo, `docs/poznatky/emoji-samolepky-reakce.md`, „Výběr samolepek"), ~~sady samolepek
    použité na fotce (`getAttachedStickerSets`)~~ (hotovo, ⋮ prohlížeče;
    `docs/poznatky/prohlizec-medii.md`, „Samolepky na fotce"; zbývá ověřit na fotce se
    skutečnými samolepkami), ~~oznámení o poslechu hudby
    (`listenToAudio`)~~ (hotovo, `docs/poznatky/zvuk.md`, „Hudba").

**Správa skupin a kanálů — zbytky:** ~~zrušit hlavní pozvánku (`replacePrimaryChatInviteLink`)~~
— už bylo: „Nahradit" u hlavní pozvánky volá `revokeChatInviteLink` a TDLib po zrušení hlavní
pozvánky rovnou vydá novou. ~~Upozornění vlastníka, kdo chat zdědí po odchodu
(`getChatOwnerAfterLeaving`)~~ — hotovo (`docs/poznatky/spam-a-blokovani.md`, „Odchod
vlastníka"). ~~Gigaskupina (`toggleSupergroupIsBroadcastGroup`)~~ — hotovo, nabízí se jen s doporučením od Telegramu; na zařízení NEOVĚŘENÉ, testovací účet tak velkou skupinu nemá (`docs/poznatky/profil-chatu.md`, „Gigaskupina"). ~~Oprava omylu antispamu
(`reportSupergroupAntiSpamFalsePositive`)~~ — hotovo, tlačítko „Není to spam" v Posledních akcích; na zařízení NEOVĚŘENÉ, zbývá s ověřením antispamu výš (`docs/poznatky/profil-chatu.md`, „Omyl antispamu"), ~~stav boostů pro správce
(`getChatBoostStatus`)~~ — hotovo i se seznamem jednotlivých boostů (`getChatBoosts`;
`docs/poznatky/profil-chatu.md`, „Boosty"; zbývá ověřit na chatu se skutečnými boosty), ~~uvítací zprávy
(`addChatWelcomeMessage` a spol., novinka TDLib)~~ — hotovo pro textové zprávy
(`docs/poznatky/profil-chatu.md`, „Uvítací zprávy"); média a úprava existující zbývají.

### Audit 22. 10. 2026: druhé síto

Znovu všech 1022 funkcí proti kódu: **volá se 486** (po auditu 19. 10. a pracích po něm),
nevolaných zůstává 536. Většina spadá pod „Vědomě mimo" níž, pod Hovory (videochaty, skupinové
hovory) nebo je zapsaná výš. Tohle v roadmapách nebylo:

- ~~**Odhrnutá notifikace se vracela**~~ — hotovo: TDLib se o odhrnutí dozví
  (`removeNotificationGroup`, dokumentace ho pro zavření uživatelem předepisuje) a presenter
  zapomene řádky; dřív je další zpráva téhož chatu vrátila do notifikace
  (`docs/poznatky/notifikace.md`, „Odhrnutí notifikace"). Zbývá iOS a desktop.
- **Most mini aplikací.** ~~`requestWriteAccess`, `shareMessage`, `requestChat`,
  `downloadFile`~~ — hotovo (`docs/poznatky/boti.md`, „Požadavky mini aplikace"); skutečnou
  připravenou zprávu a tlačítko ověřit s botem, který je vyrobí přes Bot API. ~~`DeviceStorage`,
  poloha, celá obrazovka a zástupce na ploše (Android)~~ — hotovo (tamtéž, „Úložiště, poloha,
  celá plocha a zástupce na ploše"). ~~`SecureStorage`~~ — hotovo (šifrovaně v trezoru
  platformy, bez obnovy ze zálohy; tamtéž). Zbývá zástupce na iOS a desktopu; emoji status
  z mini aplikace chce Premium.
- ~~**Podobné kanály: oznámit otevření** (`openChatSimilarChat`) a podobní boti v profilu bota
  (`getBotSimilarBots`, `openBotSimilarBot`)~~ — hotovo (`docs/poznatky/profil-chatu.md`,
  „Záložka Podobné"). ~~Náhledy mini aplikace v profilu bota (`getBotMediaPreviews`)~~ — hotovo (tamtéž,
  „Ukázky mini aplikace").
- ~~**Témata Uložených zpráv a vlákna přímých zpráv kanálu:** skok na datum, vymazání (i za
  vybrané dny) a odepnout vše ve vlákně~~ — hotovo (`docs/poznatky/konverzace-pohyb.md`, „Skok
  na datum"; `konverzace-akce.md`, „Vymazání historie"). Ověřené v tématu Uložených zpráv,
  vlákno přímých zpráv zbývá ověřit s druhým účtem.
- ~~**Příběhy — správa zveřejněného:** úprava popisku, změna viditelnosti, ponechat v profilu
  a připnout, archiv vlastních prošlých příběhů~~ — hotovo (⋮ v prohlížeči, menu účtu → Moje
  příběhy; `docs/poznatky/pribehy.md`, „Správa vlastního příběhu"). ~~Výměna média
  (`editStory` s obsahem), výběr lidí ve viditelnosti, příběh za kanál (`getChatsToPostStories`)~~
  — hotovo (tamtéž; „Příběh za kanál nebo skupinu"); výměna média i výběr lidí ověřené na emulátoru. ~~Obálka videa (`editStoryCover`), reakce podle
  `getStoryAvailableReactions`~~ — hotovo (tamtéž, „Změnit obálku", „Reakce a odpověď").
  Zbývá ťuknutí na místo v příběhu (`searchPublicStoriesByLocation`, `…ByVenue`).
- **Drobnosti v nastavení a hledání:** ~~obnovit všechna nastavení upozornění
  (`resetAllNotificationSettings`), smazat všechny výjimky ukládání do galerie
  (`clearAutosaveSettingsExceptions`), vymazat celou historii hashtagů (`clearSearchedForTags`),
  sběratelské jméno nebo číslo z Fragmentu v profilu (`getCollectibleItemInfo`), neznámý odkaz
  `tg://` s vysvětlením od serveru (`getDeepLinkInfo`)~~ — hotovo (`notifikace.md`,
  `prohlizec-medii.md`, `hledani-a-zakladani-chatu.md`, `profil-chatu.md`). Obnovení upozornění
  ověřené jen po potvrzovací dialog, číslo +888 neověřené. ~~Profilová fotka z emoji
  (`getDefaultProfilePhotoCustomEmojiStickers`)~~ — hotovo („Z emoji" v úpravě profilu
  i chatu; `profil-chatu.md`, „Fotka z emoji"). ~~Fotka z vlastního emoji nebo
  samolepky z nainstalovaných sad a fotka z emoji už při zakládání skupiny~~ — hotovo (tamtéž).
- **Prohlížeč odkazů podle Telegramu** (`getLinkWebBrowserType`, `changeWebBrowserSettings`,
  výjimky po adresách) — novinka TDLib, nastavení synchronizované přes účet. Plume má vlastní
  „Odkazy v aplikaci"; až se bude sahat na otevírání odkazů, zvážit převzetí.
- ~~**Správa kanálu za boosty:** počet boostů, se kterými člen obejde pomalý režim a omezení
  (`setSupergroupUnrestrictBoostCount`), vypnutí reklam ve vlastním kanálu
  (`toggleSupergroupCanHaveSponsoredMessages`), hlavní záložka profilu kanálu i vlastního
  (`setSupergroupMainProfileTab`, `setMainProfileTab`)~~ — hotovo (karta nastavení v úpravě
  chatu, úprava profilu; profil se otevře na hlavní záložce; `profil-chatu.md`, „Správa za
  boosty a hlavní záložka"). Skutečné vypnutí reklam neověřené (chce úroveň 50). Barvy, emoji
  status a sada emoji skupiny patří pod „Vzhled od Telegramu" (vědomě ne).

~~**Tvorba samolepek z fotky**~~ — hotovo na Androidu, iOS i desktopu: z prohlížeče i z Nastavení,
odstranění pozadí přes ML Kit / Vision (desktop bez něj), nová i vlastní sada (`docs/poznatky/emoji-samolepky-reakce.md`, „Tvorba
samolepky z fotky") a úprava vlastní sady (název, pořadí, emoji, odebrání samolepky, smazání
sady, obálka; „Úprava vlastní sady samolepek") a bílý obrys kolem vyříznutého objektu.
Zbývá: video a animované samolepky, odstranění pozadí na desktopu (chtělo by přibalit model),
a ověření iOS na skutečném iPhonu.

### Prověrka 26. 10. 2026: co z nevolaných stojí za to

Wrapper 16.0.0 má 1 065 funkcí (bez updatů), aplikace volá asi 540. Ze zbylých ~520 spadá
většina pod „Vědomě mimo" níž (hvězdy, platby, dárky a TON přes 150 funkcí, Business,
Passport, volání jen pro boty, testy a logy) nebo pod Hovory (videochaty). Nevolaná funkce
ještě neznamená chybějící funkci: hlavní pozvánku ruší `revokeChatInviteLink`, takže
`replacePrimaryChatInviteLink` netřeba; `setUserNote` je vynechaná vědomě. Co zbylo a stojí
za to, od nejužitečnějšího:

- ~~**Statistiky kanálu, skupiny a příspěvku**~~ — hotovo (viz Správa skupin a kanálů výš).
  Zbývá: přiblížení grafu po hodinách (`zoomToken` → `getStatisticalGraph` s `x`), statistika
  příběhu (`getStoryStatistics`, `getStoryPublicForwards`; příběh v přehledu kanálu se dnes
  neotevře), ťuknutí na člena v žebříčcích skupiny → profil, a hlavně **ověření se skutečným
  kanálem** (statistiky vydá server kanálu zhruba od 500 odběratelů, `canGetStatistics`).
  Názvy řad grafu („Views", „Joined") posílá server anglicky — ověřit, jestli je posílá
  v jazyce účtu; když ne, zvážit vlastní názvy podle druhu grafu.
- ~~**Odkaz na téma** (`getForumTopicLink`)~~ a ~~**ikona tématu z emoji**
  (`getForumTopicDefaultIcons`)~~ — hotovo (nabídka nad tématem → Kopírovat odkaz, formulář
  tématu → Ikona; `docs/poznatky/typy-zprav.md`, „Správa témat"). Jakékoli vlastní emoji
  (Premium) ne — ověřit by nebylo na čem.
- ~~**Tvorba samolepek — doplňky:** vlastní odkaz sady s kontrolou (`checkStickerSetName`;
  návrh `getSuggestedStickerSetName` se volal už dřív) a klíčová slova samolepky
  (`NewSticker.keywords`, `setStickerKeywords`)~~ — hotovo (`docs/poznatky/emoji-samolepky-reakce.md`,
  „Tvorba samolepky z fotky" a „Úprava vlastní sady"). `getStickerSetName` vrací jen jméno
  sady podle id a Plume ho nepotřebuje.
- ~~**„Poslední soubory" ve výběru příloh** (`searchOutgoingDocumentMessages`)~~ — hotovo
  (sponka → Poslední soubory; `docs/poznatky/konverzace-psani.md`, „Poslední soubory").
- ~~**Samolepka na pozdrav v prázdném soukromém chatu** (`getGreetingStickers`)~~ — hotovo;
  oficiální Android bere tutéž sadu (`docs/poznatky/emoji-samolepky-reakce.md`, „Samolepka na
  pozdrav v prázdném chatu"). Zbývá ověřit ve skutečném prázdném chatu s kontaktem.
- **Funkce, které Premium jen odemyká** (štítky Uložených zpráv `getSavedMessagesTags` /
  `setSavedMessagesTagLabel`, štítky složek `toggleChatFolderTags`, nastavení emoji statusu,
  tajný režim příběhů `activateStoryStealthMode`) — Premium cizí klient neprodá, ale kdo ho
  má z oficiální aplikace, tyhle funkce používá a v Plume mu chybí. Až bude účet s Premium,
  na kterém se dají ověřit.
- **Videochat s mluvením a kamerou, iOS** — viz Hovory; hlavní zbytek ze 40 nevolaných
  funkcí skupinových hovorů.

**Vědomě mimo, dokud se nerozhodne jinak:**

- **Hvězdy, platby, dárky, TON** (přes 100 funkcí). Nejdřív rozhodnutí, jestli a jak placení
  v aplikaci z Play jde se zásadami Play o fakturaci; dnes odkazy na platby jdou do prohlížeče
  a placená média, hry a dárky jsou jen ke čtení (`roadmap-konverzace.md`).
- **Premium** — koupit ho cizí klient nemůže (`canPurchaseFromStore` jen pro oficiální
  aplikace). Funkce, které Premium jen odemyká (emoji status `setEmojiStatus`, barvy profilu,
  štítky složek, tajný režim příběhů), mají smysl až s účtem, na kterém se dají ověřit.
- **Telegram Business a rychlé odpovědi** (40 funkcí) — chce předplatné; pruh firemního bota
  je zapsaný výš.
- **Telegram Passport** — sdílení dokladů se službou; citlivé, nízká poptávka.
- **Bot API, podpora Telegramu, testovací a logovací volání** — do klienta nepatří.

## Tajné chaty

Základ hotový (`docs/poznatky/tajne-chaty.md`): přijetí, založení z profilu, psaní, časovač,
ukončení, notifikace, zákaz snímků. Zbývá:

- ~~**Šifrovací klíč**~~ — hotovo (obrázek a čísla shodné s oficiálním klientem,
  `docs/poznatky/tajne-chaty.md`, „Šifrovací klíč").
- ~~**Dostupnost a ověření v seznamu chatů**~~, ~~**média, hlasovky a kolečka**~~ a ~~**založení
  z „Nové zprávy"**~~ — hotovo (`docs/poznatky/tajne-chaty.md`, „Ověření").
- iOS a desktop ověřit.

## Hovory

Hovory ve dvou fungují na Androidu (zvuk i video) a na desktopu (jen zvuk), viz krok 2 níž;
iOS a skupinové hovory chybí.

**TDLib umí jen signalizaci, ne přenos zvuku a obrazu.** Založení, přijetí, zavěšení
(`createCall`, `acceptCall`, `discardCall`), stav přes `updateCall`, výměnu klíčů s emoji
k ověření, seznam serverů a předávání signalizačních dat (`sendCallSignalingData`,
`updateNewCallSignalingData`); totéž pro skupinové hovory a videochaty (`joinGroupCall`…).
Média obstarává samostatná knihovna **tgcalls** (C++, nad WebRTC), kterou si přibalují všichni
klienti včetně Telegram X na TDLib.

Dá se to rozdělit na dva kroky, které na sobě nezávisí:

1. **Hovory v historii a příchozí hovor bez přenosu** (malé, jen nad TDLib):
   - ~~**Zpráva o hovoru jako řádek, ne bublina s jedním slovem**~~ — hotovo (směr, zmeškaný /
     odmítnutý / zrušený, délka, video; `docs/poznatky/typy-zprav.md`, „Zpráva o hovoru"). Ťuknutí nevede nikam.
     Skutečný hovor v historii ověřený se dvěma účty (`docs/poznatky/typy-zprav.md`, „Zpráva o hovoru").
   - ~~**Příchozí hovor ohlásit**~~ — hotovo na Androidu: notifikace v kanálu Hovory
     s „Odmítnout" (`docs/poznatky/notifikace.md`, „Ohlášení příchozího hovoru"). Ověřené skutečným hovorem
     mezi dvěma telefony, i z pushe se zabitým procesem. Na iOS ohlásí hovor push od Telegramu sám (`PHONE_CALL_REQUEST`
     v textech pushe); v popředí aplikace se zatím neohlašuje nic.
   - ~~**Hovory ve skupině**~~ — hotovo: pruh „Probíhá videochat" / „Živé vysílání" / naplánovaný
     nad konverzací s počtem účastníků (`docs/poznatky/notifikace.md`, „Pruh videochatu"). Na Androidu
     z něj jde videochat poslouchat (viz krok 2, „Videochat na poslech"); jinde se připojit nejde;
     ťuknutí řekne, kde to jde.
2. **Skutečné hovory** (největší samostatný kus práce v projektu). Průzkum 13. 10. 2026
   (`docs/poznatky/hovory.md`): sonda na dvou účtech ukázala, že oficiální Android bere
   verze 8, 10, 11 i 12 a z celé řady vybere 8.0.0; servery jsou vždy TURN Telegramu se
   jménem a heslem plus reflector. Cesty k přenosu:
   - **A) tgcalls se svým forkem WebRTC na všech platformách** — totéž, co oficiální klienti
     (všechny verze, reflectory), ale stavět ho sám pro Android, iOS i desktop. Řádově
     2–3 měsíce, údržba (tgcalls se mění každý týden).
   - **B) Holé WebRTC a vlastní verze 10/11 v Kotlinu** — čistě MIT, ale vysoké riziko:
     Telegram X 11.0.0 vyřadil jako nefunkční, tgcalls ji právě předělává, aktuální
     `InstanceV2ReferenceImpl` potřebuje háčky z forku WebRTC a reflectory by se psaly znovu
     podle nedokumentovaného kódu. Jen jako experiment.
   - **C) ntgcalls (LGPL-3.0) na Androidu a desktopu, tgcalls xcframework na iOS** — hotové
     AAR v Maven Central (~9 MB na ABI), verze 8/9/12/13, které oficiální klienti nabízejí
     (8.0.0 si Android sám vybral), klíč bere z TDLib. Tak to dělá Mithka (Flutter + TDLib).
     Řádově 3–6 týdnů na hovory ve dvou na Androidu. Rizika: malý tým zaměřený na boty,
     API mezi hlavními verzemi nekompatibilní, JNI chce keep pravidla pro R8, na iOS jiná
     knihovna než na Androidu.

   **Sonda C prošla (13. 10. 2026, `docs/poznatky/hovory.md`, „Sonda s ntgcalls")**: hovor
   se zvukem Plume ↔ oficiální Android oběma směry i Plume ↔ Plume, verze 8.0.0, emoji
   shodná. **Obálka na Androidu hotová** (`docs/poznatky/hovory.md`, „Obálka na Androidu":
   služba, přijetí z notifikace i nad zámkem, obrazovka hovoru, reproduktor, senzor
   přiblížení). Vyzvánění a Bluetooth taky (`docs/poznatky/hovory.md`, „Vyzvánění a výstup
   zvuku"). Změna sítě a systémový telefon taky (`docs/poznatky/hovory.md`, „Změna sítě
   za hovoru", „Systémový telefon"). Videohovory taky (`docs/poznatky/hovory.md`,
   „Videohovory"). **Desktop: hlasové hovory na macOS** (`docs/poznatky/hovory.md`,
   „Desktop"). Zbývá:
   - **Desktop: Windows a Linux ověřit** (knihovna se stáhne a přibalí, nespuštěné; toast
     hovoru s Přijmout / Odmítnout, okénko příchozího hovoru a kamera přes modul WebRTC tam
     nikdo neviděl). Zvonění, okénko příchozího
     hovoru a video na macOS hotové (`docs/poznatky/hovory.md`, „Zvonění a příchozí hovor na
     desktopu", „Video na desktopu").
   - **Desktop: video dál** — náhled kamery při vytáčení na Windows a Linuxu (macOS hotový,
     `docs/poznatky/hovory.md`, „Video na desktopu"), výběr kamery (bere se první) a mikrofonu,
     výběr výstupu na Windows a Linuxu,
     sestavení podepsané stálou identitou i pro ladění (ad hoc podpis nutí povolovat kameru
     a mikrofon po každém sestavení znovu).
   - **Desktop: zabalená aplikace** — v `.app` z `createDistributable` JNA `libntgcalls`
     najde a popisy použití i entitlementy kamery a mikrofonu jsou hotové
     (`docs/poznatky/desktop.md`, „Kamera a mikrofon v podepsané aplikaci"); ověřit ještě
     MSI a DMG. Popisy použití v Info.plist jsou jen anglicky. ~~Vybalení knihovny jednou do
     dat aplikace~~ a ~~stav spojení v logu jménem~~ — hotovo (`docs/poznatky/hovory.md`,
     „Desktop"); vybalení ze zabalené aplikace ověřit.
   - **Android: hodinový videohovor** (teplota a baterie; šest minut ověřených,
     `docs/poznatky/hovory.md`, „Ověřeno 14. 10. 2026"). Ostatní dřív neověřené kousky
     (Bluetooth, okénko, Android 11, hovor z oficiálního klienta) jsou ověřené.
   - **Play Console: deklarace služeb v popředí hovoru a celoobrazovkových notifikací** —
     checklist v „Než se dá vydat na veřejnost".
   - **Zpoždění hovoru změřit** — uživatel s Bluetooth sluchátky popsal zpoždění „jako
     vysílačka" po celý hovor, přitom RTT sítě 40–100 ms a buffer 50 ms. Sondou změřit dobu
     tam a zpět pípnutím (bez mikrofonu a reproduktoru) a zvlášť s sluchátky a bez nich;
     LE Audio přidává v režimu hovoru desítky až stovky ms v každém směru.
   - **ntgcalls bere i rozhraní bez internetu** (IMS pro volání přes LTE, `rmnet`): pingy na
     reflectory přes ně nikdo nezodpoví a WebRTC je každých 16 s zahodí a sbírá znovu (572× za
     2,5 h hovoru). Výpadky to nezpůsobuje, jen zbytečné probouzení rádia u každého uživatele
     s SIM. Zjistit, jestli jde ntgcalls předat seznam sítí z Androidu (`NET_CAPABILITY_INTERNET`),
     nebo síť vyloučit (`docs/poznatky/hovory.md`, „Zátěžový test").
   - **Sdílení obrazovky dál** — v hovoru ve dvou hotové na Androidu (posílá i přijímá)
     a příjem na desktopu (`docs/poznatky/hovory.md`, „Sdílení obrazovky"). Zbývá: posílání
     z desktopu, Android pod 14, otočení telefonu za sdílení. Ukazovátko na sdílené obrazovce hotové
     (`docs/poznatky/hovory.md`, „Ukazovátko na sdílené obrazovce"); zbývá ověřit desktop
     jako diváka a opakovat `Hello`, když se oprávnění povolí až za sdílení. Plné vzdálené
     ovládání zvažované a odložené (důvody tamtéž).
   - **Videochat na poslech** — hotovo na Androidu: připojení ztlumeně z pruhu, účastníci,
     zvuk, jeden obraz (sdílená obrazovka přednostně), Opustit (`docs/poznatky/hovory.md`,
     „Videochat na poslech"). ~~Mluvení (odtlumení, zvednutí ruky)~~ — hotovo (tamtéž, „Mluvení
     a zvednutá ruka"). Zbývá: ověřit, že hlas na druhé straně zní (ušima), kamerou a sdílením
     obrazovky účastníka a velký videochat po segmentech; ~~poslech na pozadí s pruhem nad
     aplikací~~ — hotovo (`docs/poznatky/hovory.md`, „Videochat na pozadí"); vlastní kamera,
     mřížka víc kamer, „kdo mluví", správa pro správce (ztlumit ostatní, pustit ke slovu),
     desktop. ~~Pruh hovoru ve dvou překrýval obsah~~ — hotovo, odsouvá ho jako pruh videochatu.
   - **iOS** (tgcalls xcframework, CallKit, VoIP pushe) a **skupinové hovory s mluvením**.
   Původní plán sondy:

   **Doporučení: C, začít dvoudenní sondou na Androidu.** ntgcalls v `debug` větvi, jen zvuk:
   `CallStateReady` → `skip_exchange` + `connect_p2p`, signalizace oběma směry, hovor
   z Plume do oficiálního klienta a zpět. Před sondou zkontrolovat 16KB zarovnání `.so`
   v AAR (Play) a velikost. Když spojí, pokračovat obálkou (níž); když ne, rozhoduje se mezi
   A a odložením. LGPL dynamicky linkovaná MIT nemění; z Telegram X ani z Telegramu pro
   Android se nekopíruje nic (GPL).
   - **Platformní obálka:** Android `ConnectionService` (a foreground služba typu
     `phoneCall`/`microphone`/`camera`), iOS **CallKit** a **VoIP pushe přes PushKit** — bez
     nich příchozí hovor nezazvoní na zamčeném telefonu, a Apple je aplikaci s hovory předepisuje
     (VoIP push, který neohlásí hovor CallKitu, systém trestá ukončením aplikace).
   - **Obrazovka hovoru:** stav, emoji k ověření, ztlumit / reproduktor / kamera / zavěsit,
     obraz v obraze pro video. Oprávnění mikrofonu už máme (hlasovky), kamery ne.
   - **Skupinové hovory** až potom — jiná signalizace a víc účastníků. Tady je cesta přes holé
     WebRTC realističtější než u hovoru ve dvou: prostředníkem je server Telegramu (SFU) a data
     pro `joinGroupCall` jsou blízko běžnému WebRTC (ufrag, pwd, fingerprinty, SSRC v JSONu);
     komunitní knihovny (pytgcalls / ntgcalls) to tak dělají.
   - Ověřování chce druhý účet nebo druhé zařízení; bot volat neumí.

## AI v zařízení

Shrnutí chatu a návrhy odpovědí běží na Gemini Nano přes ML Kit Prompt API (Android). Poučení
z prototypu je v `docs/poznatky/ai-v-zarizeni.md`, sekce „AI v zařízení"; sem patří, co zbývá.

**Model se NEUČÍ a učit ho nejde.** Žádný fine-tuning ani doladění na uživatelových datech:
balík spravuje systém a aplikace ho jen volá. Co API nabízí místo toho, je kešovaný kontext
(`Caches`, níž) a system prompt — a hlavně personalizace na NAŠÍ straně: co si aplikace
o uživateli pamatuje, vloží do promptu. Kdo čeká, že se model časem „naučí, jak uživatel píše",
hledá něco, co tam není.

**Pravidlo pro výběr featur: model drží fakta, ale drhne mu sloh.** Čísla, jména a termíny
drží spolehlivě — na ověřovacím testu (ceny, číslo účtu, datum, jména) si nevymyslel nic —,
zatímco věty komolí („telefon nebudou mít vibrace", „Pěče pod Sněžkou") a mezi třemi návrhy
odpovědi bývá jeden nesmysl. **Prompt už vyladěný je; tohle je strop modelu, ne zadání.**
Z toho plyne pořadí, ve kterém se vyplatí featury stavět:

- **Klasifikace, extrakce a rozhodování jsou silná stránka** — výstupem je štítek, datum nebo
  ano/ne, takže na slohu nezáleží a chyba se pozná.
- **Úprava textu, který uživatel schválí, je únosná** — chybu vidí a opraví, než odešle.
- **Generování textu k odeslání je ta nejslabší kategorie.** Shrnutí i našeptávání odpovědí
  z ní obě jsou; zajímavé věci leží v první a zatím z ní nemáme nic.

### Sdílená knihovna ai-engine

Poskytovatelé, runtime a jejich nativní knihovny jsou v knihovně ai-engine
(github.com/VladimirTintera/ai-engine), kterou Plume bere jako submodul `external/ai-engine`
(`docs/poznatky/ai-v-zarizeni.md`, „Knihovna ai-engine"). Úkoly ke kódu knihovny patří do
repozitáře ai-engine; tady zůstávají ty, které sahají i do Plume:

- **Testy ai-core i na Android hostu** (v ai-engine) — dnes `jvmTest`; `LiteRtOnDeviceAi` je
  sdílený s Androidem, ale `MlKitOnDeviceAi` a úloha stahování testy nemají ani předtím neměly.

### Co zbývá u toho, co běží

- **Větší model: Gemma přes LiteRT-LM běží, ověřená na emulátoru (E2B na CPU).** Preview
  varianta Gemini Nano (Gemma 4 **E4B**) přes ML Kit tehdy vypadala nedostupná (mylně, viz níž),
  takže přibyl druhý poskytovatel — Gemma 4 E2B / E4B, kterou si stáhne aplikace
  (`LiteRtOnDeviceAi`). Stažení, navázání po výpadku sítě, smazání, shrnutí, návrhy i release
  build (s keep pravidlem) prošly. Zbývá:
  - **vyzkoušet na Foldu E4B a GPU** a hlavně **porovnat češtinu E4B s Gemini Nano** — kvůli
    tomu to celé je; E2B na emulátoru vyšla kvalitou zhruba jako Nano,
  - **velikost APK**: nativní knihovny přidají ~25 MB na ABI každému, i kde se Gemma nenabídne
    → Play Feature Delivery,
  - ~~**stahování na pozadí**~~ — hotovo (úloha WorkManageru s notifikací průběhu a „Zastavit";
    `docs/poznatky/ai-v-zarizeni.md`, „AI v zařízení"). Zbývá ověřit v release buildu a na telefonu a **vyplnit
    v Play Console deklaraci služby v popředí `dataSync`** (checklist v „Než se dá vydat
    na veřejnost").
- **Porovnat češtinu preview Gemini Nano (E4B) se stabilní (E2B).** Preview přes ML Kit
  JEDE (featura 647, ověřeno na Foldu 6. 11. 2026 v logu `InferenceTaskScheduler`;
  `docs/poznatky/ai-v-zarizeni.md`, „Dva poskytovatelé: Gemini Nano a Gemma"), takže na E4B
  vlastní runtime potřeba není. Zbývá shrnutí a návrhy odpovědí v Plume s přepínačem preview
  a bez něj; podle toho rozhodnout, jestli preview nabízet jako výchozí.
- **Kešování kontextu a `warmup()` nejsou využité.** Prompt API je nabízí (`Caches`,
  `CachedContext`, `isCachingFeatureAvailable`) a u opakovaného shrnování téhož chatu by mohly
  ušetřit čas. Zatím netestované.
- ~~**Shrnutí bere jen to, co se vejde do jednoho promptu.**~~ — hotovo: dlouhá konverzace se
  shrnuje po nejvýš třech částech a z poznámek se složí jedno shrnutí
  (`docs/poznatky/ai-v-zarizeni.md`, bod „Shrnutí po částech"). Zbývá **ověřit s Gemini Nano na
  telefonu** (limit 8 192 tokenů, části tam nastanou dřív než u Gemmy) a kvalitu složeného
  shrnutí na skutečné konverzaci; co se nevejde ani do tří částí, dál odpadne.
- ~~**Strop výstupu je kompromis.**~~ — zmírněno: odrážky mají instrukcí nejvýš 20 slov
  a useknutý text končí poslední celou odrážkou (`completeLines`). Jestli to na Nano stačí,
  aby shrnutí dvanácti zpráv nekončilo u šesté, se ukáže až na telefonu.

### Co by šlo postavit

Seřazené podle toho, jak sedí na pravidlo výš, ne podle nápaditosti. **Každá z nich má cenu,
se kterou se musí počítat předem: inferenci pustí AICore jen aplikaci v POPŘEDÍ.** Z notifikace
ani z úlohy na pozadí tedy nic z toho běžet nemůže — featura, která má vyhodnotit zprávu
v okamžiku, kdy přijde, narazí na tohle, ne na model.

- **Konverzace s modelem (hřiště na zkoušení modelů).** MVP hotové: Nastavení → AI v zařízení →
  Vyzkoušet model, patička s měřením, předěl, kde model zapomíná, Zastavit, Odpovědět znovu
  (`docs/poznatky/ai-v-zarizeni.md`, „Vyzkoušet model (hřiště)"). Zbývá:
  - **ověřit skutečné generování na zařízení** (emulátor model nedotáhne, viz poznatky),
  - **Nahlásit odpověď** — kvůli pravidlům Google Play pro generativní AI, dořešit před vydáním
    (co se s nahlášením stane),
  - fáze 2–4 z [navrh-konverzace-s-modelem.md](navrh-konverzace-s-modelem.md): porovnání
    jiným modelem, vlastní pokyny, stavová konverzace s KV keší.
- **Vlastní model vedle katalogu.** Odkaz na soubor GGUF nebo `.litertlm` na Hugging Face,
  aplikace ho stáhne a počítá jako modely z katalogu (jen text, ruční přiřazení úloh a hřiště).
  Návrh, změny v ai-engine, přemýšlení u llama.cpp a fáze:
  [navrh-vlastni-model.md](navrh-vlastni-model.md).
- ~~**Strukturovaný výstup jako první krok.**~~ — hotovo pro návrhy odpovědí na Gemini Nano
  (`OnDeviceAi.generateStructured`, ručně psaný `GenerableProvider` bez KSP, náhrada textem;
  `docs/poznatky/ai-v-zarizeni.md`, „Strukturovaný výstup"). Gemma ho nemá, jde dál textem. Zbývá:
  - ~~**ověřit na telefonu s Gemini Nano**~~ — ověřeno na Foldu (jede na featuře 648 „Fast Mode
    (Structured Output)", návrhy nevyšly horší než text; tamtéž). Zbývá porovnat na větším
    vzorku zpráv,
  - ~~**další typy**~~ — hotovo: podvod, tón, připomínka, termín do kalendáře a hledání
    přirozeným jazykem (`generateAnswer`; `docs/poznatky/ai-v-zarizeni.md`, „Strukturovaný
    výstup"), ověřené na Foldu. ~~Model spočítal špatně „den před Silvestrem"~~ — svátky
    s pevným datem teď poznají pravidla; jiná relativní data („třetí pátek v měsíci") zůstávají
    na modelu.
- ~~**Detekce podvodných zpráv.**~~ — hotovo: karta „Možný podvod" pod zprávou, předfiltr
  pravidly a posouzení modelem (`docs/poznatky/ai-v-zarizeni.md`, „Varování před podvodem").
  Původní zadání: „Naskenuj tenhle QR a ověř si účet", falešná výhra, odkaz na
  přihlášení. Nenápadné varování u zprávy. Je to klasifikace, tedy silná stránka, a **děje se to
  celé v telefonu** — obsah zprávy nikam neodchází, což je u téhle featury podstatné. Navazuje
  na pravidlo, které projekt už má (odkaz `tg://login` se z ťuknutí neotevírá, viz `docs/poznatky/prihlaseni.md`,
  „Přihlášení QR kódem"). Pozor na to, že varování u legitimní zprávy je horší než žádné:
  práh se musí nastavit tak, aby se raději mlčelo.
- **AI ikonka u zprávy s rozpoznaným kontextem** — první kolo hotovo: termín → kalendář nebo
  připomínka, číslo účtu → QR platba a kopírování, adresa → mapy, telefon a e-mail → uložit do
  kontaktů, číslo letu → stav letu a let do kalendáře, Wi-Fi s heslem → připojit a QR kód sítě, kód → zkopírovat,
  cizí měna → převod kurzem ČNB, jednotky druhé soustavy → převod, kód rezervace → zkopírovat,
  číslo zásilky (Česká pošta / S10, Zásilkovna, PPL a DPD se jménem dopravce) → sledování, z ikonky i z nabídky nad zprávou; model termín potvrzuje a doplní název a místo (`docs/poznatky/ai-v-zarizeni.md`,
  „Akce ze zpráv"). Zbývá:
  - **ověřit na telefonu**: editor události v kalendáři (emulátor bez účtu Google chce
    přihlášení), načtení QR platby bankovní aplikací, Gemini Nano místo Gemmy, iOS (jen přeložené),
  - **adresa bez jasné stavby** („sraz na Vinohradské 12") a místo bez termínu („u Kotvy") —
    pravidla je vědomě nechávají, chtělo by to předfiltr a model, jako u termínu. ~~Víceslovné
    ulice~~ s PSČ hotové („Karla Engliše 3, 150 00 Praha 5"); bez PSČ a tři slova dál ne,
  - **jméno u kontaktu** jen z jasné stavby („Jan Novák: 602…"); jinak by ho uměl model,
  - **letiště mimo tabulku** (~110 nejběžnějších) a měst mimo seznam českých a anglických
    názvů; u města s víc letišti se bere hlavní (Londýn → Heathrow), pokud zpráva nemá kód;
    správa rezervace u dopravce chce adresu pro každou společnost zvlášť,
  - **Wi-Fi na iOS** jen s kopírováním hesla a QR kódem — připojení chce entitlement Hotspot
    Configuration v profilu aplikace; víceslovný název sítě jen v uvozovkách,
  - ~~**snackbar „Zkopírováno" leží pod sheetem**~~ — hotovo (fajfka v řádku, `fix/akce-ze-zprav-drobnosti`),
  - ~~**čísla PPL a DPD**~~ — hotovo, jen se jménem dopravce poblíž (`fix/akce-ze-zprav-drobnosti`),
  - ~~**místo od modelu bývá vágní** („letišti", „Kotvy") a mapy s ním neporadí~~ — hotovo:
    mapy jen u místa s číslem nebo vlastním jménem (`mapsQuery`),
  - ~~**QR platbu sdílet jako obrázek**~~ — hotovo („Sdílet QR kód"; načtení bankovní aplikací
    zbývá ověřit na telefonu),
  - **`TextClassifier` / ML Kit Entity Extraction / `NSDataDetector`** místo vlastních pravidel —
    ověřit češtinu (ML Kit Entity Extraction ji v seznamu jazyků podle všeho nemá).
- ~~**Triáž chatů.**~~ — hotovo pro soukromé chaty ~~i skupiny se zmínkou~~, ~~posudky přes
  restart~~ (`docs/poznatky/ai-v-zarizeni.md`, „Triáž chatů"). Zbývá **Gemini Nano** ověřit na
  telefonu a ve skupině otázka, která uživatele jmenuje jen jménem v textu (bez zmínky ji
  server za zmínku nepovažuje, takže skupina kandidátem není).
- **Štítky Uložených zpráv.** Témata Uložených zpráv (dělení podle chatu) už jsou, štítky od
  Telegramu (`SavedMessagesTag`, jen s Premium) ne; automatické zatřídění by je nahradilo bez
  serverové podpory a je to zase klasifikace.
- ~~**Přepsat před odesláním**~~ — hotovo (podržení odeslání → „Přepsat…";
  `docs/poznatky/ai-v-zarizeni.md`, „Přepis před odesláním"). Původní zadání:
  („kratší", „zdvořileji", „bez překlepů"). Sloh drhne, ale uživatel
  výsledek VIDÍ a rozhodne — chyba je levná, na rozdíl od našeptávání, kde se text podepisuje
  naslepo. Patří do nabídky nad vstupním polem, ne do automatiky.
- ~~**Vysvětlit dlouhou cizí zprávu**~~ — hotovo („Vysvětlit" v nabídce nad zprávou;
  `docs/poznatky/ai-v-zarizeni.md`, „Vysvětlení dlouhé zprávy"). Původní zadání:
  („o co jde") — totéž co shrnutí, jen nad jednou zprávou,
  takže se vejde do vstupu a sloh vadí míň, protože to čte jen uživatel.

- **Úpravy textu od Telegramu i v telefonu** (říjen 2026; server je umí v sheetu „Zkontrolovat
  a přepsat…", tady jde o totéž bez odeslání textu). Styly (Viking, Biblical…) vědomě ne — je to
  volné generování, nejslabší kategorie; „Kratší" a „Zdvořileji" už lokálně jsou.
  - ~~**Překlad konceptu**~~ — hotovo ML Kitem, ne jazykovým modelem (`docs/poznatky/ai-v-zarizeni.md`,
    „Překlad konceptu v telefonu"). Zbývá iOS (s překladem zpráv).
  - ~~**Zvýraznit změny u „Opravit překlepy"**~~ — hotovo (`draftDiff`; `docs/poznatky/ai-v-zarizeni.md`,
    „Přepis před odesláním").
  - ~~**Přidat emoji modelem s pojistkou**~~ — hotovo (`onlyEmojiAdded`; `docs/poznatky/ai-v-zarizeni.md`,
    „Přepis před odesláním"). Zbývá ověřit Gemini Nano.

**Na překlad se Nano nehodí a nemá se používat.** ML Kit má vlastní offline Translation API,
stavěné přímo na to; pouštět na překlad jazykový model by bylo horší v kvalitě i v čase.

**Další nápady (říjen 2026)**, vybrané podle téhož pravidla:

- ~~**Hledání přirozeným jazykem.**~~ — hotovo („Hledat s AI" v hledání; `docs/poznatky/hledani-a-zakladani-chatu.md`,
  „Hledání přirozeným jazykem"). Odesílatel („od Jany") zatím ne — globální hledání zpráv ho
  neumí filtrovat; strukturovaný výstup Prompt API taky ne (řádky + pravidla). Původní zadání:
  „Fotky od Jany z loňského léta" → filtry, které hledání už
  umí (chat, druh obsahu, období, odesílatel — `MessageSearchFilter`, hledání podle člena).
  Model nic negeneruje, jen vyplní typ přes `GenerateTypedContentRequest`, takže je to přirozený
  první krok ke **strukturovanému výstupu** výš. Chybu uživatel vidí hned (čipy filtrů nad
  výsledky) a opraví ji ťuknutím. Dotaz bez rozpoznaných filtrů jde dál jako text.
- **„Připomenout mi" nad zprávou.** ~~Bez AI~~ hotovo (nabídka nad zprávou → „Připomenout…",
  naplánované přeposlání do Uložených zpráv; `docs/poznatky/konverzace-akce.md`, „Připomenutí
  zprávy"). ~~Termín z textu jako první volba~~ hotovo pravidly bez modelu (`reminderTimeFromText`,
  česky a anglicky), ~~a když nenajdou nic, model v zařízení~~ (Gemma i Gemini Nano, ověřeno
  s Gemmou na emulátoru). A notifikace připomínky říká „Uložené zprávy: <já>",
  ne „Připomínka" — TDLib ji od jiné zprávy v Uložených neodliší (přeposlaná vlastní zpráva).
- ~~**Kontrola tónu před odesláním**~~ — hotovo v sheetu „Zkontrolovat a přepsat…"
  (`docs/poznatky/ai-v-zarizeni.md`, „Kontrola tónu"); pasivní agrese vědomě ne. Původní zadání:
  („nezní to podrážděně?"). Klasifikace vlastního konceptu,
  nic se nepřepisuje a varování jde přejít. Stejná opatrnost jako u podvodných zpráv: radši
  mlčet než varovat zbytečně. Spouštět jen na vyžádání nebo u delších zpráv, ne na každý úhoz.
- ~~**„Co jsem zmeškal".**~~ — hotovo jako karta „Čeká na tvou odpověď" nahoře v hlavním
  seznamu (`docs/poznatky/seznam-chatu.md`, „Čeká na odpověď"). Zbývá totéž co u triáže:
  skupiny a výsledky přes restart; případně i „jen informace" (shrnutí ostatních nepřečtených
  jednou větou) — to by ale chtělo model nad každým chatem, ne jen ano/ne.
- **Popis obrázků** — ~~ověřit, jestli Gemma přes LiteRT-LM 0.11.0 přijímá obrázky~~ přijímá;
  ~~„Popsat fotku" v prohlížeči~~ hotovo (`docs/poznatky/prohlizec-medii.md`, „Popis fotky").
  ~~**Popis jako `contentDescription` fotky v bublině**~~ — hotovo pro fotky, které uživatel
  popsat nechal (uložené popisy; `docs/poznatky/prohlizec-medii.md`, „Popis fotky"). ~~Hledání fotek podle
  obsahu~~ — hotovo na Androidu (popisy na pozadí na nabíječce, blok v hledání;
  `docs/poznatky/prohlizec-medii.md`, „Popisy na pozadí a hledání fotek podle obsahu"). Zbývá iOS
  (`BGProcessingTask`) a ověření na skutečném telefonu. Gemini Nano přes Prompt API zatím jen text.
- ~~**Offline překlad přes ML Kit Translation**~~ — hotovo na Androidu (přepínač „Překládat
  v telefonu" v sheetu překladu; `docs/poznatky/konverzace-akce.md`, „Překlad v telefonu").
  ~~Správa stažených balíčků~~ hotovo (Nastavení → Úložiště → Balíčky pro překlad).
  Zbývá **iOS** (Translation framework je jen pro SwiftUI — šlo by přes most ze Swiftu)
  a rozhodnout, jestli **+6–7 MB nativní knihovny**
  na stažení z Play za to stojí (jinak dynamický modul Play Feature Delivery).
- ~~**Lokální přepis řeči v zařízení (Speech-to-Text bez Premium).**~~ — hotovo přes Gemmu 4
  se zvukovým vstupem (`OnDeviceAi.transcribe`; `docs/poznatky/prohlizec-medii.md`, „Přepis
  v telefonu"). ~~Telefony bez Gemmy~~ napsané (systémový `SpeechRecognizer` v zařízení,
  Android 13+), ale **neověřené na skutečném telefonu** — emulátor ho nemá. Zbývá iOS
  (`SFSpeechRecognizer` s `requiresOnDeviceRecognition`)
  a ověření kolečka. ~~Přepis přes restart aplikace~~ hotovo (uložený v nastavení po účtech).
  ~~Bez přepisu tlačítko mizelo~~ — vysvětluje, co zapnout, a offline jazyk pro systémový
  rozpoznávač stáhne tlačítkem v bublině (**neověřené na skutečném telefonu**). **Přepis i s Gemini Nano**:
  ML Kit GenAI má vlastní Speech Recognition API (alpha; ověřit, na kterých telefonech běží
  a jestli umí češtinu) — uživatel s Gemini Nano by nemusel stahovat gigabajty Gemmy jen kvůli
  přepisu.
- ~~**Extrakce textu z fotek (OCR / Live Text).**~~ — hotovo (⋮ → „Text z fotky" v prohlížeči:
  zvýraznění řádků na snímku, výběr řádků ťuknutím, panel s výběrem a „Kopírovat vše / výběr";
  `docs/poznatky/prohlizec-medii.md`, „Text z fotky"). Zbývá: iOS (Vision) ověřit na simulátoru
  a release build s R8.

### Navazuje na víc modelů naráz (říjen 2026)

Gemini Nano a Gemma jdou zapnout zároveň a úlohy se jim přiřazují (`AiTask`, `variantFor`;
`docs/poznatky/ai-v-zarizeni.md`, „Víc modelů naráz a co čím počítat"). **Na Foldu zbývá
ověřit, že to vedle sebe opravdu běží**: shrnutí na Nano, přepis hlasovky na Gemmě a Gemma se
po přepisu uvolní z paměti. Co z toho dál plyne:

- ~~**Porovnání modelů vedle sebe**~~ — hotovo: v sheetu shrnutí a vysvětlení „Porovnat
  s dalšími modely" spočítá totéž ostatními zapnutými modely, po sobě, s časem v patičce
  (`docs/poznatky/ai-v-zarizeni.md`, „Porovnání modelů"). Zbývá **na Foldu porovnat češtinu
  Nano, E2B a E4B** na několika skutečných chatech a podle toho rozhodnout, co je výchozí.
- ~~**Shrnutí dlouhé hlasovky.**~~ — hotovo: „Shrnout přepis" v nabídce nad příchozí hlasovkou
  nebo kolečkem od minuty s hotovým přepisem (`docs/poznatky/ai-v-zarizeni.md`, „Shrnutí
  hlasovky"). Zbývá ověřit na telefonu a zvážit tlačítko přímo pod dlouhým přepisem v bublině
  — nabídka je o ťuknutí dál; a shrnutí bez přepisu napřed (přepsat a hned shrnout jedním
  ťuknutím).
- ~~**Ohled na baterii a teplotu.**~~ — hotovo jinak, než se plánovalo: pod úsporným režimem
  a přehřátím Gemma sama od sebe nic nepočítá (návrhy, podvod, triáž, termíny, popisy na
  pozadí), úlohy na ťuknutí jedou dál; platí i pro ručně přiřazenou Gemmu
  (`docs/poznatky/ai-v-zarizeni.md`, „Úsporný režim a přehřátí"). Zbývá ověřit na telefonu,
  jestli `MODERATE` není příliš brzy (Fold při nabíjení).
- ~~**Kratší držení Gemmy v paměti.**~~ — hotovo: s Nano na textu se Gemma uvolní po 30 s,
  jinak po dvou minutách (`gemmaIdleUnloadMs`; `docs/poznatky/ai-v-zarizeni.md`, „Dva
  poskytovatelé"). Zbývá **změřit na Foldu**, kolik stojí opětovné načtení na GPU a jestli
  30 s nestačí málo na hlasovky přepisované za sebou.
- ~~**Návrh složek.**~~ — hotovo: „Navrhnout složky" v Nastavení → Složky chatů rozřadí 40
  nejnovějších chatů do pevných kategorií a nabídne složky s „Přidat"
  (`docs/poznatky/seznam-chatu.md`, „Návrh složek modelem"). Zbývá ověřit kvalitu na
  skutečných chatech v telefonu a zvážit úpravu návrhu před přidáním (odškrtnout chat).
- ~~**Popisek fotky před odesláním.**~~ — hotovo: „Navrhnout popisek" pod fotkou nad polem vloží
  krátkou větu od Gemmy do prázdného pole (`docs/poznatky/konverzace-psani.md`, „Návrh popisku
  fotky"). Zbývá ověřit češtinu na telefonu; alternativní text pro čtečku Telegram jako zvláštní
  pole nemá, takže zůstává jen popisek.

---

## Výkon a paměť

Nástroje a postup jsou v `docs/poznatky/vykon.md` („Výkon a paměť"). První analýza (reporty Compose
kompilátoru, LeakCanary, heap dumpy) únik nenašla a delší
běh ukázal, že se ustálí i native heap. Zbylo:

- **Fully drawn na Foldu** ověřit s první verzí, která `ReportDrawnWhen` nese: měřené
  hodnoty na telefonu jsou zatím jen první snímek.
- **Samolepky na víc vláknech.** Kreslení `.tgs` běží mimo hlavní vlákno, ale jen na jednom,
  protože Compottie má globální pracovní objekty a sdílené vrstvy kompozice
  (`docs/poznatky/emoji-samolepky-reakce.md`, „Samolepky `.tgs` se kreslí MIMO hlavní vlákno").
  Na emulátoru stíhá mřížku na ~80 %, na slabším telefonu samolepky ubírají snímky. Šlo by
  rozložit kompozice bez ořezu cesty (`"ty":"tm"`) na víc pevných vláken, nebo poslat opravu
  globálů do Compottie. Napřed změřit na skutečném starším telefonu (Pixel 5).
- **Kreslení samolepek na iOS a desktopu ověřit naživo** — přeloženo, ale spuštěné jen na Androidu.

---

## Haptika

Slovník je hotový (`core/ui/Haptics.kt`: `threshold`, `confirm`, `reject`, `toggle`, `tick`)
a poučení z něj v `CLAUDE.md`, „Haptika". Odezvu dnes mají: prahy tahů (archivace, odpověď,
zavírací tah v prohlížeči médií), dlouhý stisk odkazu, odeslání zprávy, samolepky i GIFu,
start a konec nahrávání hlasovky i její zahození, reakce (pruh i plný výběr), přidání
a odebrání z výběru zpráv, oblíbení samolepky, přepínače v Nastavení, v profilu (ztlumení),
v nastavení archivu, v úpravě skupiny a v Zařízeních, skok mezi výsledky hledání v konverzaci,
tažení po waveformu hlasovky, přepnutí rychlosti přehrávání a chyba po akci uživatele.

Vede se to tady, ne v [roadmap-konverzace.md](roadmap-konverzace.md): je to jeden slovník přes
celou aplikaci a rozdělený na dvě místa by se rozešel. Zbývá:

- **Procházení po dílcích** (`tick`): ~~tažení po waveformu hlasovky~~ — hotovo (tiká po
  sloupcích, ne po pixelech) —, ~~posuvník videa~~ (po dvacetinách délky) a ~~přeskočení
  stránky v prohlížeči médií~~ (jen po tahu prstem) — hotovo; zbývá pruh rychlých reakcí. Je to jediná skupina, kde hrozí, že odezva bude otravná — chce to
  ladit rukou na telefonu, ne od stolu. **Tažení po waveformu ověřené rukou NENÍ**, jen
  z `dumpsys vibrator_manager`.
- **Ověřit rukou na telefonu.** Emulátor nevibruje; z `dumpsys vibrator_manager` se pozná jen,
  že volání odešlo a které (ověřeno: odeslání 16, dlouhý stisk jediná 0, reakce 21,
  přepínač 21/22). Jestli dvě haptiky nejdou nepříjemně po sobě — třeba práh tahu a hned
  potvrzení akce — se od stolu zjistit nedá.
- **Ověřit na starém zařízení, ne na emulátoru** (API 26–33), kde `Confirm`, `Reject`,
  `ToggleOn`/`ToggleOff`, `GestureThresholdActivate` ani `SegmentTick` neexistují
  a `HapticFeedbackConstantsCompat` je nahrazuje bližším typem, nebo neudělá nic. **Emulátor
  s Androidem 9 na to nestačí: nemá vibrátor vůbec** — nezaznamená ani dlouhý stisk launcheru.
  Ověřené je jen to, že tam nic nespadne.
- **iOS ověřit na zařízení.** Sada se tam mapuje na čtyři generátory, takže zapnutí a vypnutí
  přepínače od sebe uživatel nerozezná; jestli to vadí, se pozná až rukou.
- **Vlastní přepínač v Nastavení** zatím ne — vypnout odezvu jde v systému. Přidat, až o něj
  někdo řekne.

## Velká plocha (adaptivní rozvržení)

Rozbor z října 2026. Hotové: šířkové třídy V2 a odvozené prahy, profil jako třetí panel
(`feat/tri-panely`), seznam na ploše okna a konverzace s profilem jako jedna karta, táhlo s kotvami, sbalený seznam a šipka
zpět, která vedle seznamu konverzaci zavře (`feat/kotvy-panelu`), profil, který zůstane
otevřený při přepnutí chatu (`feat/profil-zustava`), Nastavení na tři panely (`feat/nastaveni-tri-panely`), táhlo profilu (`feat/tahlo-profilu`). Co platí a jak se chová knihovna, je
v `docs/poznatky/navigace.md`, „Šířkové třídy, tři panely a kotvy" a „Táhlo, kotvy a sbalený
seznam". Zbývá:

1. **Drobnosti.**
   - ~~Sbalený seznam nemá FAB ani výběr víc chatů~~ — hotovo (`feat/sbaleny-seznam-fab-vyber`;
     karty nad seznamem hlásí tlačítko s tečkou).
   - ~~Zkratka hledání se sbaleným seznamem nic neukáže~~ — hotovo s hledáním jako destinací
     (panel se při otevření hledání rozbalí).
2. **Hlavička nad kartou: zbývá ověřit.** Konverzace, profil, jeho podstránky a kalendář mají
   hlavičku na ploše okna a kartu od výšky prvního řádku seznamu až k dolnímu kraji
   (`feat/conversation-header-outside-card`, `docs/poznatky/navigace.md`, „Hlavička konverzace
   leží NAD kartou"). Neověřené: desktop (horní řada vůči titulku okna), tablet, naležato,
   predictive back z konverzace, podstránky profilu naživo (skupina nebo kanál se statistikami
   a pozvánkami), výběr zpráv a pruhy nad zprávami (připnuté, spam) u horní hrany karty.
3. **Hledání jako destinace v panelu seznamu: zbývá režim Všechny účty.** Hotovo i s přechodem
   pilulka → pole (VladimirTintera/plume#35, [navrh-hledani.md](navrh-hledani.md)), ověřené na
   desktopu a na emulátoru Fold (telefon i rozložený, skládání s otevřeným hledáním). Naživo
   neověřený zbývá režim Všechny účty (hledání přes víc účtů naráz).
4. **Naležato neověřené naživo.** Telefon naležato prošel (`feat/obrazovky-nalezato`,
   `docs/poznatky/navigace.md`, „Nízké okno"). Naživo zbývá prohlížeč příběhů (účet, který
   příběhy vidí), tvorba příběhu (Premium), videochat (běžící hovor), hřiště AI (zařízení
   s modelem) a hovor. Zadávání PINu v úzkém panelu Nastavení naležato roluje, vedle sebe se tam
   nevejde.

**Jak ověřovat:** desktop změnou velikosti okna (nejrychlejší cesta k Large a XL), emulátor
tabletu, Fold (`adb emu fold` / `unfold` v cyklu — viz Gotchas o insetech v `CLAUDE.md`, změna
scény při rozložení už jednou shazovala aplikaci) a obě rotace naležato kvůli výřezu.

---

## Po přechodu na Compose 1.13

Compose Multiplatform 1.13.0-rc01 opravil několik věcí, kvůli kterým projekt nesl obcházky
(přepis jaru insetů na desktopu, `RootTooltipBox`, `readString`); všechny tři jsou pryč. Až vyjde
stabilní 1.13.0, zvednout z RC.

- **ai-engine kompiluje proti `compileSdk` 37.0**, Plume proti 37.2 (Compose 1.13 chce 37.1+).
  Až ai-engine převezme Compose 1.13, sjednotit a z CI vyhodit `platforms;android-37.0`.

### Novinky z 1.13, které by šly využít

Z release notes CMP 1.13.0-alpha01 až rc01 a z porovnání zdrojů material3 1.12.0-alpha03
a 1.13.0-rc01 (listopad 2026). Seřazené podle přínosu pro Plume.

- **`mediaQuery` (`UiMediaScope`, `@ExperimentalMediaQueryApi`)**: dotaz na vlastnosti zařízení
  přímo v composable — přesnost ukazatele, druh klávesnice, kamera, mikrofon, poloha skládačky
  (`FoldState`, `WindowPosture`), rozměry okna. **Skutečné hodnoty dává jen Android.** Na desktopu
  jsou pevné (`DesktopMediaScope` v ui-desktop 1.13: mikrofon vždy `true`, kamera vždy `false`
  „no reliable way to get it", ukazatel `Fine`, klávesnice `Physical`, poloha prázdná); obecný
  skiko základ vrací `false` / `None` všude. Co z toho plyne:
  - ~~**Kamera a mikrofon na desktopu**~~ — přes `mediaQuery` nejde (viz výš). Kolečka desktop
    stejně nenabízí (`rememberVideoNoteCamera() = null`); hlasovka bez mikrofonu by se musela
    poznat z `javax.sound` (`AudioSystem.getTargetLineInfo`), ne odsud.
  - ~~**Skládačka napůl otevřená (tabletop), jen Android**~~ — hotovo: prohlížeč videa i hovor
    (obraz nebo fotka nad pantem, ovládání pod ním; `rememberTabletopHinge` z `windowPosture`
    material3 adaptive, ne z `mediaQuery`, ten polohu pantu nedává). Viz
    `docs/poznatky/prohlizec-medii.md`, „Video", a `docs/poznatky/hovory.md`. Na zařízení
    neověřený zůstává videohovor v té poloze.
  - **Přesnost ukazatele:** na desktopu pevně `Fine`, takže přínos jen na Androidu (tablet s myší,
    Chromebook) a nejasně na iOS. Dílčí kontroly (`HorizontalSwipe`, `MessageFrame`, prohlížeč,
    `ViewerControls`) se čtou z typu ukazatele u události, a to funguje všude — výměnu nedělat,
    dokud API zůstává experimentální.
- **Visící odsazení položek seznamu v článcích — zkoušeno, zatím nejde bez kompromisu.**
  Víceřádková položka se zalomí pod odrážku („•", „1.", „☑" jsou znaky v textu z
  `RichMessageBody.kt`). `Bullet` i vlastní `ParagraphStyle(textIndent)` na položku narazí na
  prázdný řádek na každé hranici odstavce; jediné řešení bez něj (`\n` → znak nulové šířky
  ve vykresleném textu) by rozbilo kopírování výběru a citaci z výběru. Podrobnosti a pokusy:
  `docs/poznatky/konverzace-zobrazeni.md`, „Odsazení textu uvnitř zprávy".
- ~~**Desktop: rychlejší start (AppCDS / AOT)**~~ — hotovo (`AotPrebuild`, JDK 25;
  `docs/poznatky/desktop.md`, „Rychlejší start (AOT cache)"; release 1.2.0 s cache prošel na obou runnerech);
  zbývá změřit start na Windows.
- **iOS zadarmo, jen ověřit na simulátoru:**
  - **Dynamic Type** — písmo se řídí systémovou velikostí; vyzkoušet spolu s vlastní velikostí
    písma zpráv a s řádky pevné výšky.
  - **Omezit pohyb** — Compose ho nově respektuje; podívat se, jestli to sedí s `AppMotion`.
  - **Writing Tools v textových polích** opravené — souvisí s Apple Intelligence (`ai-v-zarizeni.md`).
  - **Gesta trackpadu** (posun a štípnutí) na iPadu — prohlížeč fotek, přiblížení písma štípnutím.
  - **Automatické vyplnění hesla z Klíčenky** — pole už nesou `ContentType.Password`
    / `NewPassword` (`BlockField.kt`, `TwoStepScreen.kt`), na iOS by to teď mělo fungovat.
- **Výběr textu tahem sám posouvá obsah** za okraj `SelectionContainer` (bez vrtění myší) —
  vyzkoušet ve výběru textu zprávy a v článku.
- **Paralaxa pro karusel** (`CarouselParallaxScrollEffectState`, `carouselParallaxScrollEffect`
  na `LazyRow`) — zatím bez vhodného místa; kandidát jen tam, kde by vznikl pás fotek.

## Vzhled (Material 3 Expressive)

Nápady na doladění vzhledu; nic z toho zatím nikdo nezačal. Navazují na slovník, který
aplikace už má (`CLAUDE.md`, „Material 3 / vzhled" a `ShapeMorphs`): tvar nese stav, vlnka
znamená „teď zní", terciární role „živé". Seřazené od nejlepšího poměru přínosu a práce.
Hotové jsou FAB menu, vlnitý posuvník videa, tvary tlačítek při stisku, přetáčení počtů a přepínače s ikonou (poučení v `CLAUDE.md`).

**Malé**

- ~~**Tlačítka v hlavičkách měknou při stisku**~~ — hotovo pro všechna tlačítka s ikonou
  (`IconButtonDefaults.shapes()`, VladimirTintera/telegram#498).
- ~~**Počet nepřečtených se přetočí**~~ — hotovo (`RollingNumber`, VladimirTintera/telegram#500):
  odznaky, počty u složek a archivu, „N nových zpráv".
- ~~**Přepínače s ikonou**~~ — hotovo (`IconSwitch`: fajfka / křížek v jezdci, všechny
  přepínače v aplikaci).

**Střední**

- ~~**FAB „Nová zpráva" jako `FloatingActionButtonMenu`**~~ — hotovo: Nová zpráva, Nová
  skupina a Nový kanál (Kontakty ne — Nová zpráva je seznam kontaktů), VladimirTintera/telegram#496.
- ~~**Vlnitý posuvník videa, dokud hraje**~~ — hotovo (VladimirTintera/telegram#497).
- ~~**Tvarová odezva na prahu tahu**~~ — hotovo: archivace i odpověď tahem, kolečko za prahem
  naroste do sušenky a vyplní se (`SwipeThresholdBadge`).
- ~~**Reakce s výbuchem**~~ — hotovo: za emoji přidané reakce se otevře `SoftBurst` a čip
  povyskočí (`ReactionChip`).
- ~~**Expressive `LoadingIndicator` místo kolečka**~~ — hotovo: dotahování historie a první
  načtení seznamu ho už měly, zbylá neurčitá kolečka (tlačítka, náhled nahrávky) taky.

**Větší (spíš funkce než detail)**

- ~~**Výběr víc chatů v seznamu**~~ — hotovo: „Vybrat" v nabídce nad chatem, plovoucí lišta
  s archivací, ztlumením a přečtením (`ChatSelection`), v hlavním seznamu i archivu.
- ~~**Vlastní barva motivu**~~ — hotovo: Nastavení → Vzhled → Barva, osm vzorků kreslených
  z palety, kterou aplikace dostane (`ThemeColorPicker`, `AppSettings.themeColor`).
- ~~**Nápovědy u gest napoprvé**~~ — hotovo: `RichTooltip` se šipkou u odkrývání časů,
  podržení pro volby odeslání a podržení pro nahrávání, každá jednou (`GestureHints`).
- ~~**Barva konverzace**~~ — hotovo: profil chatu → Barva konverzace, tytéž vzorky jako barva
  aplikace a „Podle aplikace". Celá konverzace (i její profil a seznam témat) dostane paletu
  z té barvy, v seznamu chatů ji nese jen odznak nepřečtených (`ChatColorTheme`, `ChatColors`;
  `docs/poznatky/konverzace-zobrazeni.md`, „Barva konverzace"). Místo automatického podbarvení
  z profilové fotky: barvu dává uživatel, takže něco znamená. Zbývá: synchronizace mezi
  zařízeními (spolu s ostatním nastavením) a ověřit iOS.

### Sklouzávání k M2 (audit říjen 2026)

Průchod kódem a obrazovkami na emulátoru. **Hlavní body jsou hotové** (#825–#850); co zbývá, je níž nepřeškrtnuté a v roadmapě konverzace. Hlavní plochy (seznam chatů, vstupní pole, nabídky,
plovoucí lišty) jsou expressive; do „holého M3" se padá na okrajích — v podstránkách, sheetech,
dialozích, prázdných stavech a u textových tlačítek. Konverzace má vlastní seznam
v [roadmap-konverzace.md](roadmap-konverzace.md), „Vzhled podle M3 Expressive". Seřazené podle
poměru přínosu a práce; každý bod je samostatný PR.

- ~~**Nastavení: segmentované bloky, motiv jako skupina tlačítek, jednotné hlavičky**~~ — hotovo
  (`GroupedColumn` v `core/ui/GroupedList.kt`, VladimirTintera/telegram#825; `docs/poznatky/nastaveni.md`, „Skupiny jsou
  bloky zaoblených řádků").
- ~~**Tvary textových tlačítek při stisku a velká hlavní tlačítka**~~ — hotovo (všech ~350
  volání, `verifyButtonShapes`; hlavní akce přes `MediumButton`, `CLAUDE.md` u ikonových tlačítek;
  VladimirTintera/telegram#827).
- ~~**Prázdné a chybové stavy jako `IntroEmptyState`**~~ — hotovo pro prázdný účet a chybu seznamu
  chatů, prázdnou konverzaci, chybu Instant View; podseznamy (pozvánky, moje samolepky, hledání
  témat) mají `GroupEmptyRow` (VladimirTintera/telegram#829). Chyba načtení passkeys a odepřený přístup ve sdílení
  doběhly v druhém kole (bod níž).
- ~~**Emphasized typografie**~~ — hotovo pro jméno v profilu a hovoru, nadpis přihlášení
  a prázdného stavu, kartu účtu, velká čísla v Úložišti a Využití sítě, počet vybraných, vybranou
  složku a otázku ankety; ruční SemiBold k rolím `title*`/`label*` převedený (`CLAUDE.md`,
  Typografie; VladimirTintera/telegram#830). Nadpisy sheetů dostanou zvýraznění se společným `SheetHeader` (bod níž).
- ~~**Společná hlavička sheetů**~~ — hotovo (`SheetTitle`, 53 sheetů, VladimirTintera/telegram#831). Výběr
  jedné hodnoty v sheetu doběhl v druhém kole (`ChoiceListItem`, bod níž).
- ~~**Společný `ConfirmDialog`**~~ — hotovo pro 25 potvrzení (VladimirTintera/telegram#832)
  a dalších 9 s volbou nebo polem přes slot `content` (smazání zprávy s „i pro ostatní" a moderací,
  vyhození s délkou, předání vlastnictví s heslem, smazání složky s opuštěním chatů, ukončení ankety,
  odmítnutí příspěvku, žádosti o vstup, dvoufázové ověření; VladimirTintera/telegram#854). `AlertDialog` zůstal
  u podmínek služby, popupu mini aplikace, informací o proxy, dotazu na podporu, výběru osobního
  kanálu a editoru ukládání do galerie — nejsou to potvrzení akce. Druhé kolo auditu našlo
  dalších 20 potvrzení (bod níž, hotovo).
- ~~**Výběr hodnoty v Nastavení jako nabídka u řádku**~~ — hotovo (`ChoiceRow`, šest výběrů, VladimirTintera/telegram#834;
  `docs/poznatky/nastaveni.md`). Ukládání do galerie zůstalo dialogem (víc hodnot najednou).
- ~~**Holé `ListItem`y bez bloku**~~ — hotovo: výsledky hledání a nedávná hledání, koncepty,
  nastavení archivu, výběr země (s hledací pilulkou místo obrysového pole a bez dělicí čáry);
  řádek v lazy seznamu přes `Modifier.groupedItem` (`core/ui/GroupedList.kt`, VladimirTintera/telegram#836). **`PhoneField`
  zůstal jedním blokem s čárou** — obrys při psaní zvýrazní celé pole (země i číslo); dva bloky by
  ho rozdělily a přihlášení nešlo na přihlášených telefonech ověřit.
- ~~**Dvojí vstupní pole**~~ — hotovo (`FormTextField`, VladimirTintera/telegram#837, 21 polí: úprava profilu a skupiny, proxy,
  složka, pozvánka, téma, nový kontakt, nová skupina/kanál, slovní kód v přihlášení). Pole
  v `AlertDialog`u zůstala obrysová (odkaz ve formátování, možnost ankety, štítek člena…).
- ~~**Ruční `Card` bloky v profilu**~~ — hotovo (úprava skupiny, `GroupSettingsCard`, práva
  a omezení člena přes `GroupedColumn`, VladimirTintera/telegram#839).
- ~~**Hlavičky podstránek profilu**~~ — hotovo (`ProfileSubpageTopBar` s názvem chatu v podtitulku;
  „Uložit", „Přidat", „Vytvořit", „Hotovo" jako `SaveActionButton` i v novém kontaktu, nové skupině
  a řazení; VladimirTintera/telegram#840).
- ~~**Obrazovka hovoru**~~ — hotovo: ikony `mic_off` / `videocam_off` podle stavu, široké zavěšení
  probíhajícího hovoru, fotka dýchající v tvaru `ShapeMorphs.Live`, dokud hovor čeká
  (`docs/poznatky/hovory.md`, „Vzhled obrazovky hovoru"; VladimirTintera/telegram#843).
- ~~**Tokeny tvarů místo dp**~~ — hotovo: 69 zaoblení ze škály na `ShapeDefaults`, kopie
  `GROUP_CORNER`/`INNER_CORNER` sloučené, `verifyShapeTokens` na `check` (VladimirTintera/telegram#845). Hodnoty mimo škálu
  (24, 18, 10 dp u bublin a médií, 16 dp soustředně uvnitř 20dp karty) zůstaly záměrně.
- ~~**Výchozí animace mimo `AppMotion`**~~ — hotovo: 94 přechodů a 5 `animate*AsState` se spec
  z `motionScheme`, `verifyMotionSpecs` na `check`, VladimirTintera/telegram#847. (Audit odhadoval ~20 `animate*AsState` bez specu,
  ve skutečnosti jich bylo pět — zbytek měl spec na dalším řádku.)
- ~~**Tvar řádku při stisku**~~ — hotovo bez `SegmentedListItem`: stisknutý řádek bloku se
  zaoblí na 16 dp v seznamu chatů, kořeni i sekcích Nastavení (`GroupedColumn`) a lazy blocích
  (`groupedItem`); `MorphShape.kt` to bere jako odezvu na prst, ne jako stav (VladimirTintera/telegram#855).
  `SegmentedListItem` by přinesl ještě tvar při fokusu klávesnicí — zatím nic.
- ~~Drobnosti~~ — hotovo (VladimirTintera/telegram#856): barva nového tématu fóra přes `ColorSwatch`
  (sušenka s fajfkou jako barva motivu), titulek přímých zpráv s ořezem vlnky, odznak
  zabezpečení `SecurityBadge` (`Gem`) místo kruhů, `ActionButtonGroup` (M3E `ButtonGroup`)
  v Mém QR kódu, pozvánkách a kartách aktualizace, nepotvrzeného přihlášení a nových chatů
  složky, jas ve videu tímtéž (rovným) indikátorem jako posuvník.

#### Druhé kolo (18. října 2026)

Po #825–#856 znovu kódem (výskyty komponent) a na Pixelu 7. Hlavní plochy jsou čisté; zbytky
jsou zase na okrajích — sheety a dialogy, které předchozí kola minula. Seřazené podle přínosu.

- ~~**Potvrzení jako holý `AlertDialog` místo `ConfirmDialog`**~~ — hotovo (VladimirTintera/telegram#858,
  20 dialogů včetně připnutí zprávy; zaškrtávací volby přes `DialogCheckRow`, fotka kontaktu
  přes `iconContent`). Holý `AlertDialog` zůstal u čistě informačních (jen „OK": upozornění bota,
  odmítnuté pozvánky, nedostupná obnova) a u žádostí o oprávnění. Byly to: smazání jazykového balíčku
  a modelu a vymazání mezipaměti s výběrem kategorií (`StorageSettings`), přidání bota do skupiny
  (`AddBotToGroup`) a bot jako správce (`AddMembersScreen`), sdílení přes bota (`BotShareScreen`),
  poslání kontaktu (`ContactPicker`), sdílení čísla (`ChatNoticeBar`, `BotDialogs`) a polohy,
  otevření odkazu a přihlášení přes bota (`BotDialogs`), přijetí návrhu fotky a narozenin
  (`items/Notes.kt`), převod na supergroup (`EditChatScreen`), propojení diskuse
  (`DiscussionSheet`), zavření mini aplikace a souhlas se zápisem (`WebAppScreen`), obnova
  e-mailu v přihlášení (`AuthScreen`). Bez ikony, bez věty o následku v jednotném tvaru
  a nevratné akce bez chybové barvy.
- ~~**Výběr jedné hodnoty v sheetu jako holé řádky s kolečky**~~ — hotovo (`ChoiceListItem`
  v `core/ui`, VladimirTintera/telegram#859): automatické mazání, zvuk upozornění (vestavěné volby, vlastní tóny
  a nahrání jako tři bloky), sada samolepek skupiny a diskuse kanálu (obálka a fotka skupiny
  vlevo, „žádná" se znakem na jejich místě). Vymazání historie bloky s kolečky mělo už dřív
  a doby u člena jsou v dialogu — zůstaly.
- ~~**Sheet upozornění chatu**~~ — hotovo (VladimirTintera/telegram#860): tři bloky přes `GroupedColumn` —
  ztlumení s ikonami, volby, návrat k výchozímu; hlavička jako u ostatních sheetů
  (`docs/poznatky/notifikace.md`).
- ~~**Výběr hodnoty v `AlertDialog`u s kolečky**~~ — hotovo (VladimirTintera/telegram#861): samosmazání účtu
  nabídkou u řádku, jazyk aplikace a rychlá reakce jako sheet, doba živé polohy jako sheet s blokem
  akcí a vzdálenost upozornění jako sheet s výběrem a vlastním blokem „Vypnout"
  (`LiveLocationSheets.kt`). Hledání v jazycích zatím netřeba — jsou tři.
- ~~**Obrysová pole mimo dialog**~~ — hotovo (VladimirTintera/telegram#862): úprava kontaktu, „Jiný důvod"
  v nahlášení fotky a pole v nahlášení (dřív napodobenina bloku bez rámečku při psaní) přes
  `FormTextField`. `OutlinedTextField` zůstal jen v `AlertDialog`ech, blok možností ankety
  (`GroupTextField` v `core/ui/BlockField.kt`, dřív `BlockField` v `PollComposer`) je záměrně vlastní (skupinové tvary).
- ~~**Zbývá z prvního kola**~~ — hotovo (VladimirTintera/telegram#863): chyba načtení přístupových klíčů je řádek
  v bloku sekce s ikonou a tónovým „Zkusit znovu"; odepřený přístup ve sdílení má zmenšený
  úvodní tvar (`IntroShape`, 96 dp) a zvýrazněný nadpis — celoobrazovkový stav se mezi
  hlavičku a galerii nevejde.
- **Ke zvážení, ne chyba:** formuláře na celou obrazovku (nový kontakt, nová skupina, tvorba
  ankety a seznamu úkolů) mají malý `TopAppBar`, kdežto podstránky profilu `MediumFlexibleTopAppBar`.
  M3 malý bar u formuláře na celou obrazovku připouští; sjednotit, jen pokud to bude vadit.
- **Prověřeno a v pořádku:** stíny plovoucích prvků v konverzaci (záměrně, `FloatingButtonSurface`),
  ikony v tónovém kruhu u kategorií úložiště a zařízení (vedoucí prvek řádku podle M3),
  `LinearProgressIndicator` jako podíl v Úložišti a Využití sítě (poměr, ne průběh), návrhy pod
  polem (mention, hashtag, příkazy, inline) jako prostý seznam v plovoucím panelu, karta účtu
  v Nastavení (`Card` s barvou bloku).

#### Třetí kolo (18. října 2026)

Po #858–#863 znovu kódem, tentokrát po jednotlivých funkcích sheetů (předchozí sken hledal
po souborech, a soubor, který měl bloky aspoň někde, propadl). Hlavní plochy i dialogy jsou čisté;
zbývají sheety, které se otevírají z podstránek.

- ~~**Sheety s holými řádky s kolečky**~~ — hotovo (VladimirTintera/telegram#865): pomalý režim a reakce skupiny
  přes `ChoiceListItem`; „Uložit" u reakcí je `MediumButton`.
- ~~**Sheety s holými seznamy**~~ — hotovo (VladimirTintera/telegram#866): detail zařízení (údaje a přepínače
  jako dva bloky, hlavička ikonou a nadpisem), výjimky upozornění (chaty v bloku, „Obnovit
  výchozí" jako ikonové tlačítko s bublinou), záložky a kdo reagoval (`groupedItem`).
- ~~**Tlačítka vedle sebe v `Row` místo `ActionButtonGroup`**~~ — hotovo (VladimirTintera/telegram#868): žádosti
  o vstup, překlad, přepis zprávy, úprava fotky. Přitom oprava všech skupin z #856: smrštěný
  soused zalamoval popisek na dva řádky (`GroupActionText`, limit smrštění u `TextButton`).
- **Ke zvážení, ne chyba:** kolečka v blocích na obrazovkách (Všichni / Kontakty / Nikdo
  v soukromí, nastavení skupiny, práva člena, poskytovatel AI, rozsah vymazání historie) jsou
  platný M3 seznam; v sheetech je výběr fajfkou (`ChoiceListItem`), na obrazovkách kolečkem.
  Sjednotit, jen pokud to bude vadit.
- **Prověřeno a v pořádku:** zbylé `AlertDialog`y jsou informační (jen „OK"), žádosti
  o oprávnění nebo dialogy s polem; ruční `SemiBold` je jen u rolí `body*` (pravidlo
  typografie); řádky bez bloku v `BirthdatePicker`, `LoginEmailScreen`, `UsernamesSection`
  a v rychlé reakci leží v bloku obrazovky, která je skládá.

## Vychytávky

Nápady z října 2026, nic z toho zatím nikdo nezačal.

- ~~**Bubliny konverzací (Android 11+)**~~ — hotovo (`BubbleActivity`, `BubbleShell`;
  `docs/poznatky/notifikace.md`, „Bubliny konverzací"). Profil, přeposlání a jiné chaty
  bublina zatím posílá do plné aplikace; ověřit na skutečném telefonu.
- ~~**Widget na plochu**~~ s připnutými a nepřečtenými chaty — hotovo na Androidu (Glance;
  `docs/poznatky/seznam-chatu.md`, „Widget na ploše"). ~~Fotky chatů~~ a ~~náhled ve výběru
  widgetů~~ — hotovo. Zbývá iOS (WidgetKit, vlastní rozšíření).
- **Fold a klávesnice.** ~~Klávesové zkratky (Ctrl+K hledání, Alt+↑/↓ mezi chaty, Esc zavře
  konverzaci nebo hledání)~~ a ~~Enter odesílá~~ — hotovo (`docs/poznatky/navigace.md`, „Klávesové
  zkratky"; na iOS jen Enter). ~~Přetažení obrázku či souboru z jiné aplikace na vstupní pole~~
  — hotovo na Androidu (`docs/poznatky/konverzace-psani.md`, „Přetažení z jiné aplikace")
  i na desktopu (`docs/poznatky/desktop.md`, „Vložení ze schránky a přetažení souborů");
  zbývá iOS (`UIDropInteraction`). ~~Seznam zkratek v systémovém přehledu (Meta+/)~~ — hotovo.
- ~~**Přehled rozepsaných konceptů**~~ — hotovo (menu účtu → Koncepty;
  `docs/poznatky/seznam-chatu.md`, „Přehled konceptů"). ~~Smazání konceptu přímo z přehledu~~ —
  hotovo (koš a „Vrátit"). ~~Koncepty v tématech fóra~~ — hotovo. ~~Koncepty ve vláknech
  komentářů~~ — hotovo z evidence vláken, kde koncept vznikl (`docs/poznatky/seznam-chatu.md`,
  „Přehled konceptů"); koncept psaný jen na jiném zařízení v neotevřeném vlákně chybí.
- ~~**Lokální odložení chatu (Snooze)**~~ — hotovo z nabídky nad chatem (`docs/poznatky/seznam-chatu.md`,
  „Odložení chatu"). ~~Odložení tahem~~ a ~~upozornění v čase vynoření~~ — hotovo. Zbývá iOS
  ověřit (lokální notifikace i tah).
- ~~**Čistič odkazů od sledování (Privacy URL Stripper).**~~ — hotovo (automatické odstraňování
  sledovacích a analytických parametrů `utm_*`, `fbclid`, `gclid`, YouTube/Spotify `si`, TikTok, Twitter/X
  při odesílání, úpravě zprávy i otevírání odkazů, volitelný přepínač v Nastavení → Soukromí a bezpečnost;
  `docs/poznatky/nastaveni.md`, „Čistič odkazů od sledování").
- ~~**Rychlý přepínač mezi aktivními chaty (Quick Switcher v konverzaci).**~~ — hotovo jako
  podržení šipky zpět: nabídka naposledy otevřených chatů a vršku seznamu, ťuknutí chat nahradí
  (`docs/poznatky/konverzace-pohyb.md`, „Rychlý přepínač chatů"). Tah po vstupním poli vědomě ne
  (pere se s posunem kurzoru a výběrem textu). Aby se dal najít, je i v ⋮ s nápovědou podržení
  a šipka nese odznak nepřečtených chatů jinde. Zbývá ověřit na iOS.

---

## Nápady z průchodu aplikací (27. 10. 2026)

Průchod seznamem chatů, Nastavením, profilem, médii, příběhy, hovory a notifikacemi proti
oficiálnímu Telegramu a Google Messages; každý bod ověřený v kódu a proti tomuhle seznamu
a poznatkům. Konverzace má vlastní část v [roadmap-konverzace.md](roadmap-konverzace.md)
(„Nápady z průchodu aplikací"). Velikost: S = malé, M = střední, L = velké. Chyba z téhož
průchodu (opakované přeposlání po částečném selhání) je opravená zvlášť.

**Seznam chatů a složky**

- ~~**Výběr víc chatů: smazat / opustit, připnout, přidat do složky, vybrat vše**~~ — hotovo,
  i s bublinami s popisem u všech akcí (`docs/poznatky/seznam-chatu.md`, „Výběr víc chatů").
- ~~**Nabídka nad záložkou složky a prázdná složka**~~ — hotovo: upravit, spravovat a smazat
  složku z podržení, „Přidat chaty" v prázdné složce (`docs/poznatky/seznam-chatu.md`, „Složky
  chatů"). „Ztlumit vše" vědomě ne (TDLib ztlumení složky nemá).
- ~~**„Vrátit" u smazání / opuštění**~~ — hotovo (odložené smazání, `docs/poznatky/seznam-chatu.md`,
  „Akce nad chatem").
- ~~**„Vymazat historii" z nabídky nad chatem**~~ — hotovo (tentýž `ClearHistorySheet` jako
  v konverzaci, `docs/poznatky/seznam-chatu.md`, „Akce nad chatem").
- ~~**Zástupce chatu na ploše**~~ — hotovo: „Přidat na plochu" v nabídce nad chatem i v ⋮
  profilu, jen Android (`docs/poznatky/seznam-chatu.md`, „Akce nad chatem").

**Nastavení a profil**

- ~~**Hledání v Nastavení**~~ — hotovo: pilulka nad sekcemi, rejstřík voleb se synonymy, výsledek
  otevře sekci i podstránku, doscrolluje na volbu a zvýrazní ji (`docs/poznatky/nastaveni.md`,
  „Hledání v Nastavení").
- ~~**QR kód a systémové sdílení** veřejného chatu, uživatele a pozvánky~~ — hotovo
  (`LinkQrSheet`; `docs/poznatky/hledani-a-zakladani-chatu.md`, „QR kód odkazu na chat a pozvánku").
- **Víc účtů** (L) — z větší části hotovo: přidání, odebrání a přepnutí účtu, společný seznam
  chatů s filtrem účtů, účet v konverzaci a v notifikacích, jeden hovor přes všechny účty. Co
  zbývá, vede stav a „Postup" v [navrh-vice-uctu.md](navrh-vice-uctu.md).

**Přeposílání**

- ~~**Komentář, tiché odeslání a časté cíle ve výběru**~~ — hotovo
  (`docs/poznatky/konverzace-akce.md`, „Přeposílání").

**Příběhy**

- ~~**Poslat dál, přeposlat a zkopírovat odkaz**~~ — hotovo: „Poslat dál" přes výběr
  přeposílání, kopírovat a sdílet odkaz u autora s veřejným jménem
  (`docs/poznatky/pribehy.md`, „Poslat dál a odkaz"). TDLib `getStoryLink` nemá, odkaz skládá
  `getInternalLink`.
- ~~**Ztlumení videa, rozbalení dlouhého popisku a čtečka obrazovky**~~ — hotovo
  (`docs/poznatky/pribehy.md`, „Zvuk, dlouhý popisek a čtečka obrazovky"). Zbývá popis fotky
  modelem v zařízení u příběhů a ověření se skutečným TalkBackem.

**Hovory a videochat**

- ~~**„Odmítnout se zprávou" a „Zavolat zpět"**~~ — hotovo: „Zpráva" s hotovými odpověďmi na
  zvonící obrazovce i v notifikaci hovoru, „Zavolat zpět" u notifikace zmeškaného hovoru
  (`docs/poznatky/hovory.md`, „Zavolat zpět a odmítnutí se zprávou"). Zbývá ověřit na telefonu,
  hodinkách a v Android Auto.
- **Výběr výstupu zvuku ve videochatu** (S). Hovor ve dvou ho má (`AudioOutputButton`),
  videochat ne (`VideoChatScreen.kt`) — sluchátko / reproduktor / Bluetooth.
- ~~**Historie hovorů: smazat jeden hovor, sloučit opakované**~~ — hotovo: opakované po sobě
  jsou jeden řádek s počtem, podržení nabídne smazání řádku (`docs/poznatky/hovory.md`,
  „Historie hovorů").

**Stahování**

- ~~**Otevřít soubor, ikona podle typu, datum, „Vrátit"**~~ — hotovo: stažený soubor se
  otevře (video a obrázek v prohlížeči), ikona podle druhu, čas dokončení, odebrání i smazání
  s „Vrátit" (`docs/poznatky/prohlizec-medii.md`, „Stahování"). Zbývá ověřit na iOS a desktopu.
  (Hledání ve Stažených je na seznamu už dřív.)

**Přístupnost přes celou aplikaci**

- ~~**Nadpisy pro čtečku obrazovky**~~ — hotovo: `GroupTitle`, `SheetTitle`, 24 ručně
  kreslených nadpisů bloků a předěly dnů a nepřečtených v konverzaci nesou `heading()`.
  Nadpisy horních lišt (`TopAppBar`) zatím ne.
- ~~**Akce tahu v seznamu chatů pro čtečku**~~ — hotovo (`customActions` na řádku,
  `docs/poznatky/seznam-chatu.md`, „Akce tahem"). Se skutečným TalkBackem na telefonu ověřené
  není nic z toho, jen headless testy.

---

## Nápady z listopadu 2026 (17. 11. 2026)

Co v aplikaci ani v roadmapách není (ověřeno grepem v `client/`, `docs/` a `iosApp/`).
Nic z toho zatím nikdo nezačal. Velikost: S = malé, M = střední, L = velké.

- ~~**Android Auto**~~ — deklarace hotová (`automotive_app_desc.xml`, meta-data v manifestu;
  `docs/poznatky/notifikace.md`, „Android Auto"); zobrazení a přečtení nahlas s označením za
  přečtené ověřené v Desktop Head Unit. Zbývá **odpověď hlasem** (na testovacím telefonu padá
  asistent Gemini, ne Plume) a před vydáním **přihlásit Android Auto v Play Console** (Formáty
  zařízení) a projít kontrolou pro auta.
- **Ovládání asistentem** (M). Android 16 **AppFunctions**: Gemini by uměl „pošli Janě
  v Plume…“ nebo „co mi psal Petr?“. Na iOS **App Intents** pro Siri a Zkratky; darování
  `INSendMessageIntent` kvůli Direct Share už je, takže polovina cesty je hotová. Než se
  začne: co smí asistent číst bez otevřené aplikace a jak to sedí na zámek aplikace.
- **Systémové hledání na iOS** (S–M). Chaty a kontakty do **CoreSpotlight**, ťuknutí na
  výsledek otevře chat (`LinkOpenRequests`). Na Androidu obdobu dělají dynamické zkratky,
  které už jsou.
- **Překlad celého chatu v zařízení** (M). Od Telegramu jen s Premium
  (`UpdateChatIsTranslatable`, zapsané v `roadmap-konverzace.md`). Lokálně by šel na Androidu
  přes ML Kit Translation (jazyk zprávy už rozpoznáváme, model jazyka ~30 MB na stažení),
  na iOS přes Translation framework, případně EuroLLM / Aya. Text nikam neodchází a nechce
  Premium. Překládat jen zprávy v okně, ne celou historii; ukázat, že jde o strojový překlad.
- **Export chatu** (M). Import z WhatsAppu je, opačná cesta ne. Formát jako Telegram Desktop
  (HTML / JSON s médii ve složce), nebo Markdown (kopírování výběru do Markdownu už je).
  **Chat s ochranou obsahu (`hasProtectedContent`) se exportovat nesmí**, stejně jako se
  z něj nesmí kopírovat.
- **Zamčené a skryté chaty** (M). Zámek aplikace je celý nebo nic. Zámek jednoho chatu jen
  v tomhle zařízení: chat zmizí ze seznamu (najde se hledáním s PINem / biometrií),
  notifikace řekne jen „Nová zpráva“, widget, Direct Share a bubliny ho vynechají. Oficiální
  Telegram to nemá (WhatsApp ano). Navazuje na `docs/poznatky/zamek-aplikace.md`; co zámek
  nechrání, musí platit i tady.
- **Live Updates na Androidu 16** (S–M). Notifikace s `ProgressStyle` (povýšená průběžná)
  pro odesílání velkých souborů, stahování, sdílení živé polohy a probíhající hovor — místo
  dnešních obyčejných notifikací služby v popředí. V kódu zatím `ProgressStyle` není.
- **Desktop: chat v samostatném okně** (M). Pro víc monitorů. Druhé okno už v `application {}`
  stojí (`IncomingCallWindow`); otevřená konverzace by potřebovala vlastní zásobník navigace
  a ViewModel s klíčem chatu. Pozor na insety a bubliny z kořene kompozice
  (`docs/poznatky/desktop.md`).

---

## Nastavení

Nastavení je list-detail: vlevo karta účtu, sekce, řádek Jazyk, Pomoc a odhlášení, vpravo obsah vybrané sekce (nebo úprava profilu).

### Přeskupení sekcí (říjen 2026)

Hotové (`feat/nastaveni-sekce`); poučení v `docs/poznatky/nastaveni.md`, „Obrazovka nastavení". Odchylka od rozvrhu: správa stažených modelů AI zůstala v Úložišti (přehled místa), sekce AI maže aktuální model sama. Navazuje na to Nastavení na tři panely
(„Velká plocha"), samostatný PR až potom.

**Co nesedělo.** Obecné byla sběrná sekce: karta účtu, Jazyk, Chaty (složky, samolepky, tahy,
reakce, odkazy), celé Soukromí a zabezpečení (10 řádků), Proxy, AI v zařízení a Odhlášení —
asi 25 řádků v šesti skupinách, podtitulek přitom slibuje „účet, profil, blokovaní, odhlášení".
Související věci jsou rozdělené: „Nepřečtené ve vlastním bloku" je ve Vzhledu, zbytek chování
seznamu v Obecných; modely AI se zapínají v Obecných, mažou v Úložišti; Proxy je v Obecných,
přenesená data v Úložišti. Karta účtu se najde až po otevření Obecných.

**Nový seznam sekcí:**

```
[Karta účtu: fotka, jméno, číslo, @jméno, QR]  → Upravit profil
Soukromí
Notifikace
Chaty
Vzhled
Úložiště
Zařízení
AI v zařízení        (jen kde je dostupný model)
Jazyk · Čeština      (řádek, otevře rovnou výběr; bez vlastní sekce)
Pomoc: Zeptat se · Časté dotazy · Co je nového
Odhlásit se          (vlastní skupina, chybová barva)
```

- **Soukromí** — *Soukromí:* Soukromí účtu (kdo vidí / kdo smí, smazání účtu), Blokovaní,
  Automatické mazání v nových chatech, Citlivý obsah. *Stopy:* Diskrétní čtení, Odstraňovat
  sledovací parametry. *Zabezpečení:* Dvoufázové ověření, Přihlašovací e-mail, Přístupové klíče,
  Zámek aplikace.
- **Notifikace** — beze změny.
- **Chaty** — *Seznam:* Složky chatů, Nepřečtené ve vlastním bloku (z Vzhledu), Tah doprava
  a doleva, Widget na plochu (z Vzhledu). *Zprávy:* Rychlá reakce a emoji, Akce ze zpráv, Sady
  samolepek, Sady emoji. *Odkazy:* Otevírat v aplikaci, Odkazy Telegramu v Plume.
- **Vzhled** — Motiv, Barva, Velikost písma a samolepek, Řádky náhledu zprávy.
- **Úložiště** — dnešní obsah a navíc skupina *Připojení* s Proxy (jako oficiální Telegram).
  Správa stažených modelů AI odtud odchází do AI v zařízení; balíčky pro překlad zůstávají.
- **Zařízení** — beze změny.
- **AI v zařízení** — zapnutí funkcí, volba modelu, stažení i smazání na jednom místě.
- **Co je nového** se stěhuje do Pomoci: je to čtení, ne nastavení.

**Rozhodnutí:** karta účtu nahoře v seznamu sekcí (jako Telegram i systém Android), ne uvnitř
sekce; Jazyk jako samostatný řádek, ne skupina ve Vzhledu (hledá ho i ten, kdo vzhled neřeší);
Odhlásit se dole v seznamu sekcí, ne v úpravě profilu; Proxy do Úložiště; krátké názvy sekcí
(„Soukromí", „Úložiště"), ne „Soukromí a zabezpečení" / „Data a úložiště".

**Na co pamatovat při implementaci:**
- `SettingsSection` (`core/navigation/Routes.kt`) se ukládá v zásobníku JMÉNEM — zrušená nebo
  přejmenovaná hodnota (`General`) by obnovu uloženého stavu shodila. Napřed ověřit, co
  kotlinx.serialization s neznámou hodnotou výčtu udělá, a podle toho nechat starou hodnotu jako
  alias, nebo obnovu ošetřit.
- Podstránky se dnes „vrší nad sekci Obecné" — komentáře v `Routes.kt`, `openSettingsAt(…)`
  v `AppNavigation.kt`, `openProxySettings`, `openChatFolders` a mapování `AppScreen`
  (`General` i pro `tg://settings/privacy`, `ChangePhone` přes `EditProfile`).
- Úprava profilu je s kartou nahoře podstránka SEZNAMU sekcí, ne sekce; ve dvoupanelu vedle
  seznamu stojí místo sekce.
- Podtitulky sekcí podle nového obsahu, texty v obou jazycích, poznatky v `docs/poznatky/nastaveni.md`.

- ~~**Úprava vlastního profilu**~~ — hotovo (fotka, jméno, uživatelské jméno s kontrolou
  dostupnosti, bio; `docs/poznatky/nastaveni.md`, „Úprava profilu"). ~~Historie profilových fotek~~ — hotovo v profilu chatu i u vlastního účtu (`docs/poznatky/profil-chatu.md`, „Historie profilových fotek"), u vlastního i s nastavením starší fotky jako hlavní a smazáním jednotlivé fotky. ~~Ruční ořez
  fotky~~ — hotovo (`docs/poznatky/nastaveni.md`, „Ořez profilové fotky"). ~~Video jako profilovka~~ — hotovo
  (prvních 10 s bez zvuku, výřez nad prvním snímkem; `docs/poznatky/nastaveni.md`, „Video jako profilovka"). ~~Volba
  úseku videa a snímku pro statickou podobu~~ — hotovo (pás snímků pod ořezem). Zbývá ověření na iOS.
- ~~**Sekce Notifikace**~~ — hotovo, viz [Notifikace](#notifikace) níž.
- ~~**Sekce Zařízení**~~ — hotovo (seznam, odhlášení, weby, automatické odhlášení, „Byli jste
  to vy?" v seznamu chatů, přihlášení jiného zařízení QR kódem). Poučení v `docs/poznatky/nastaveni.md`,
  „Zařízení". Zbývá:
  - **Ověřit přihlášení QR kódem na zařízení** — na emulátoru se spustí jen čtečka; potvrzení
    (`confirmQrCodeAuthentication`) proti skutečnému kódu z Telegram Desktop / webu zatím nikdo
    neviděl. Na iOS se čtečka dá vyzkoušet jen na telefonu, simulátor kameru nemá.
  - **Připojení boti (Telegram Business)** v seznamu relací ani v kartě „Byli jste to vy?" nejsou.
- ~~**Proxy**~~ — hotovo (Obecné → Připojení → Proxy, odkazy `t.me/proxy`, přihlašovací
  obrazovka, stav v řádku a sponzorovaný chat; `docs/poznatky/databaze-spojeni-proxy.md`, „Proxy").
- ~~**Řádky sekcí nemají ikony.**~~ — hotovo (`SettingsSection.icon`).

**Nastavení, která chybí** (říjen 2026; v kódu ověřeno, že nic z toho není):

- ~~**Soukromí účtu.**~~ — hotovo (Obecné → Soukromí → Soukromí účtu: naposledy viděn, telefon
  a hledání podle čísla, profilovka, bio, narozeniny, odkaz v přeposlaných, hovory, P2P,
  pozvánky do skupin, hlasové zprávy a čas přečtení, s výjimkami; `docs/poznatky/nastaveni.md`, „Soukromí účtu").
  Zbývá **kdo smí psát** (`NewChatPrivacySettings`: jen kontakty / za hvězdy) — jiné API
  a placené zprávy Plume neumí.
- ~~**Dvoufázové ověření**~~ — hotovo (Obecné → Soukromí; `docs/poznatky/nastaveni.md`, „Dvoufázové ověření").
  **Skutečné nastavení hesla a kód z e-mailu ověřené NEJSOU** (průvodci jen podstrčeným
  repository) — ověří je uživatel zapnutím na zařízení, a tím se zároveň dá ověřit převod
  vlastnictví a obnova hesla při přihlášení.
- ~~**Zámek aplikace**~~ — hotovo (PIN, biometrie na Androidu i iOS, zamykání po době neaktivity,
  brzda pokusů, celoplošný `LockScreen`, skrytí v přehledu úloh přes `FLAG_SECURE` při aktivním zámku,
  nastavení v Nastavení → Soukromí; `docs/poznatky/zamek-aplikace.md`).
- ~~**Zámek aplikace: biometrie svázaná se seznamem otisků.**~~ — hotovo (klíč v Keystore se
  `setInvalidatedByBiometricEnrollment`, na iOS `evaluatedPolicyDomainState`;
  `docs/poznatky/zamek-aplikace.md`, „Biometrie"). Zbývá ověřit iOS na zařízení a volitelný
  šestimístný PIN.
- ~~**Automatické stahování médií**~~ — hotovo (Úložiště: úroveň pro Wi-Fi, mobilní data
  a roaming, spořič dat; `docs/poznatky/prohlizec-medii.md`, „Automatické stahování"). Zbývá
  ověřit spořič dat a roaming na telefonu a případně přednačítání videí (dnes se nepřednačítá nic).
- ~~**Úložiště.**~~ — hotovo (Nastavení → Úložiště: zabrané místo po druzích a chatech, mazání
  všude i v chatu, „Uchovávat média", model Gemmy; `docs/poznatky/nastaveni.md`, „Úložiště").
  ~~**Strop velikosti mezipaměti**~~ — hotovo (1 / 2 / 5 / 10 GB; `docs/poznatky/nastaveni.md`,
  „Úložiště"). Zbývá ověřit automatický úklid po dni (a se stropem po šesti hodinách) na zařízení.
- ~~**Akce tahem v seznamu chatů pro každou stranu zvlášť**~~ — hotovo (Obecné → Chaty:
  archivovat, přečteno, ztlumit, připnout, nic; `docs/poznatky/seznam-chatu.md`, „Akce tahem").
- ~~**Menší přepínače**~~ — hotovo: rychlá reakce dvojitým ťuknutím, Enter odesílá (fyzická
  klávesnice), velikost písma zpráv, odkazy v aplikaci (Custom Tabs / Safari) a jazyk aplikace
  (`docs/poznatky/nastaveni.md`, „Menší přepínače"). Zbývá:
  - ~~**Jazyk aplikace na Androidu do 12**~~ — hotovo bez AppCompatu (`LegacyAppLanguage`;
    `docs/poznatky/nastaveni.md`, „Jazyk aplikace").
  - **iOS ověřit na simulátoru** — Safari v aplikaci, otevření systémového Nastavení z řádku
    jazyka, dvojité ťuknutí.
- **Synchronizace nastavení mezi zařízeními** (barva motivu, velikost písma zpráv, rychlost
  hlasovek…) přes CloudStorage mini aplikací (`sendWebAppCustomRequest`). Sonda ověřila, že to
  jde bez otevřené mini aplikace i u bota bez ní. Zbývá založit bota v BotFatheru (uživatel),
  podívat se do oficiálního klienta, jestli se bot nikde neukáže, a postavit to.
  Podrobnosti v `docs/poznatky/nastaveni.md`, „Synchronizace nastavení mezi zařízeními".
- ~~**Diskrétní čtení a psaní (Ghost Mode).**~~ — hotovo (Nastavení → Obecné → Soukromí;
  `docs/poznatky/nastaveni.md`, „Diskrétní čtení"), ~~i poslech hlasovek~~. Mizející média
  vědomě ne. Původní zadání: V Nastavení → Soukromí volba pro potlačení
  automatického odesílání potvrzení o přečtení (`viewMessages`) a indikátoru „Píše..."
  (`sendChatAction`). Zprávy se označí jako přečtené až při odeslání vlastní odpovědi v chatu
  nebo po ručním ťuknutí na plovoucí čip „Označit jako přečtené".

## Aktualizace z obchodu

Karta v seznamu chatů a Play In-App Updates na Androidu jsou hotové (poučení v `docs/poznatky/release.md`,
„Aktualizace z obchodu"). Zbývá:

- **Ověřit celou cestu přes Play.** Debug build aktualizaci nenajde, takže stahování, průběh
  v kartě a restart zatím nikdo neviděl. Postup: internal app sharing, nainstalovat verzi
  s nižším `versionCode`, pak nahrát vyšší a otevřít seznam chatů. Vzhled karty ověřený je
  (podstrčeným stavem na emulátoru).
- **iOS.** API na aktualizace nemá. Až bude aplikace v App Store, dá se verze zjistit dotazem na
  `itunes.apple.com/lookup?bundleId=…` a karta otevře stránku aplikace v App Store. Dnes
  `AppUpdates` na iOS vrací napořád `None`.

## Notifikace

- ~~**Kanály podle druhu chatu a sekce Notifikace v Nastavení**~~ — hotovo (kanály Soukromé
  chaty / Skupiny / Kanály / Zmínky, u každého rozsahu zapnutí, náhled, připnuté, zmínky, výjimky
  a odkaz do systémového kanálu; poučení v `docs/poznatky/notifikace.md`, „Kanály a nastavení notifikací"). Zbývá:
  - ~~**Ověřit kanály Skupiny, Kanály a Zmínky skutečnou zprávou**~~ — ověřeno testovacím botem
    v testovací skupině a kanálu (při tom opravené přidání bota do kanálu jako správce).
  - ~~**Upozornění jednoho chatu v profilu**~~ — hotovo (doba ztlumení, náhled, připnuté,
    zmínky, obnovení, zvuk přes kanál konverzace) a doby ztlumení i v nabídce chatu v seznamu.
  - ~~**Stories** (`muteStories`, `showStoryPoster`)~~ — hotovo (rozsah Soukromé chaty i chat;
    `docs/poznatky/notifikace.md`, „Upozornění na příběhy"). Zbývá ověřit, že upozornění na nový
    příběh opravdu chodí, a nahlásit TDLib, že `setScopeNotificationSettings` změnu jen příběhů zahodí.
  - ~~**Zvuk z Telegramu**~~ — hotovo: kanál podle zvuku (výchozí kanál zůstává), výběr pro
    rozsahy a tóny účtu, ~~výběr pro jeden chat v profilu~~ (sheet Upozornění → Zvuk), ~~výběr pro téma ve skupině~~
    (sheet upozornění tématu). Zbývá ověřit notifikaci z tématu skutečnou zprávou.
  - **iOS** kanály nemá; obdobou je `threadIdentifier` a `interruptionLevel` (zmínky jako
    `timeSensitive`). Přijde na řadu s notifikacemi na iOS vůbec.
- **Rychlé akce z notifikace.** Odpověď, označení za přečtené a ztlumení na hodinu jsou na
  Androidu hotové (viz docs/poznatky/notifikace.md, „Akce z notifikace"). Chybí:
  - **iOS** — akce vyplní rozšíření notifikací; ověřit skutečným pushem (viz iOS výš).
