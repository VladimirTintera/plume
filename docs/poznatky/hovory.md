# Hovory

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

Plume hovory zatím neumí — jen je ukazuje v historii a ohlásí příchozí (viz
`docs/poznatky/typy-zprav.md`, „Zpráva o hovoru", a `docs/poznatky/notifikace.md`, „Ohlášení
příchozího hovoru"). Tenhle soubor drží, co se o hovorech zjistilo průzkumem (13. 10. 2026);
plán je v `docs/roadmap.md`, „Hovory".

## Co dělá TDLib a co ne

TDLib obstará **signalizaci a klíč**: `createCall` / `acceptCall` s `CallProtocol`, stavy přes
`updateCall` (`Pending` → `ExchangingKeys` → `Ready`), v `CallStateReady` dohodnutou verzi,
servery, klíč, emoji k ověření a JSON `config`; signalizační pakety protějšku chodí
`updateNewCallSignalingData` a naše se posílají `sendCallSignalingData`. **Zvuk a obraz ne** —
to je práce knihovny nad WebRTC (tgcalls, ntgcalls).

## Ověřeno sondou na dvou účtech

Dočasná sonda v debug buildu (necommitnutá) na Pixelu 5 přijímala hovory z oficiálního
Telegramu pro Android na Pixelu 7 s různým `libraryVersions` a zapisovala `CallStateReady`:

- **Server vrátí JEDNU verzi**, ne seznam protějšku. `CallStateReady.protocol.libraryVersions`
  má jediný prvek — tu, na které se strany shodly.
- **Oficiální Android bere 10.0.0, 11.0.0 i 12.0.0**; z celé řady (13 … 7, 5.0.0, 2.7.7)
  vybral **8.0.0**. 10.0.0 a 11.0.0 jsou tedy pořád ve hře, ale preferované nejsou.
- **Bez společné verze skončí hovor hned v `CallStateError`** (nabídka „99.0.0"), oficiální
  klient ukáže „Canceled Call". Plume tedy nesmí nabízet verzi, kterou neumí — druhá strana
  by na ni přistoupila.
- **Servery jsou dva druhy pokaždé**: jeden `CallServerTypeWebrtc` (TURN i STUN Telegramu,
  se jménem a heslem, port 1400) a jeden `CallServerTypeTelegramReflector` (UDP, `peerTag`
  16 bajtů). Jestli hovor spojí jen TURN bez reflectoru, se bez mediální vrstvy ověřit nedá.
- **Oficiální klient pošle signalizaci hned po `Ready`** (5 paketů 1,5–2,3 kB v první
  desetině vteřiny, pak další) — zašifrované klíčem hovoru, čitelné až v knihovně.
- **Délku hovoru oficiální klient počítá od `Ready`**, i když žádná média neprotekla
  („Outgoing Call (8 seconds)"). Plume, který hovor přijme a nespojí, tak protějšku zapíše
  „uskutečněný" hovor — přijímat se smí, až bude čím ho spojit.

## Knihovny (stav 13. 10. 2026)

| | tgcalls | ntgcalls |
|---|---|---|
| Kdo | Telegram, používají ho všichni oficiální klienti i Telegram X a Unigram | pytgcalls, hlavně pro boty; na mobilu ho používá Mithka (Flutter + TDLib) |
| Licence | LGPL-3.0 | LGPL-3.0 |
| Verze | 2.7.7, 5.0.0, 7–13 (+14, 18, 19 nové) | **jen 8.0.0, 9.0.0, 12.0.0, 13.0.0** |
| WebRTC | fork Telegramu (`ali-fareed/webrtc`, ~M123 s patchi) | vlastní build m152 |
| Android | stavět sám (CMake nad tgcalls + forkem WebRTC) | **hotové AAR v Maven Central** (`io.github.pytgcalls:ntgcalls`, ~37 MB za 4 ABI) |
| iOS | xcframework z Telegram-iOS (Bazel), tak to dělá Mithka | není |
| Desktop | tgcalls nad `tg_owt` | C API, hotové knihovny pro Linux, Windows a macOS arm64 (ne Intel) |
| Klíč z TDLib | ano | ano (`skip_exchange(encryption_key, is_outgoing)`) |

- **Z Telegram X ani z Telegramu pro Android se nic nekopíruje** — obojí je GPL a Plume by
  přestal být MIT. LGPL knihovna dynamicky linkovaná (`.so` na Androidu, sdílená knihovna na
  desktopu) MIT nemění; statický xcframework na iOS je s LGPLv3 sporný (Mithka ho tak vydává).
- **Telegram X verzi 11.0.0 vyřadil** (2024: „unused and does not work"); tgcalls na ní teď
  intenzivně pracuje (základ verzí 18 a 19). Vlastní implementace 10/11 nad holým WebRTC
  (cesta B v roadmapě) by tak stála na nejméně stabilní části protokolu.
- **Aktuální `InstanceV2ReferenceImpl` se proti neupravenému WebRTC nepřeloží** — používá
  háčky z forku Telegramu (`dtls_transport_factory`, `external_transport_security`), za běhu
  volitelné (`network_use_mtproto`).
- **Protokol reflectorů není nikde popsaný** (core.telegram.org popisuje jen verze pod 7.0),
  jediná specifikace je `tgcalls/v2/ReflectorPort.cpp` a jeho port v ntgcalls.
- **Mithka s ntgcalls vypnula R8** — JNI hledá třídy podle jména. Plume R8 má, takže to chce
  keep pravidla, ne vypnutí (`docs/poznatky/release.md`, „R8").

## Sonda s ntgcalls (Android, 13. 10. 2026)

Hovor ve dvou jen se zvukem: `CallMedia` (společné rozhraní bez TDLib) → `NtgCallMedia`
(Android), signalizace a stavy v `TdlCallRepository`, pruh hovoru nad aplikací (`CallBar`)
a „Zavolat" v nabídce profilu. Ověřeno na Pixelu 5 a Pixelu 7 Pro: **Plume → oficiální
Android, oficiální → Plume i Plume ↔ Plume**, pokaždé verze 8.0.0, emoji shodná na obou
stranách, zavěšení z obou stran (délka hovoru se zapíše), zvuk se po hovoru vrátí do
normálního režimu. Mikrofon i přehrávání hovoru jedou s potlačením ozvěny a šumu
(`dumpsys audio`). **Jestli je zvuk slyšet, je potřeba ověřit ušima.**

- **AAR `io.github.pytgcalls:ntgcalls:3.0.3`**: `.so` zarovnané na 16 kB (`LOAD align
  2**14`, Play OK), libc++ staticky (s TDLib se nepere), závislosti jen systémové
  (`libOpenSLES`, `libEGL`…). Keep pravidla si nese sám (`proguard.txt`); po R8 zůstávají
  třídy `io.github.pytgcalls` nepřejmenované, tedy past z Mithky se nás netýká (ověřeno
  dexdumpem, release za běhu ne). Do manifestu přidává `CAMERA` a `RECORD_AUDIO` — obojí
  už máme.
- **Velikost**: `libntgcalls.so` je v APK nezabalená (21,5 MB na arm64, 14,3 MB na armv7);
  release arm64 APK má s ní 93,5 MB. Stažení z Play je komprimované, bude to méně.
- **Pořadí volání drží knihovna**: `createP2pCall` → `setStreamSources` (CAPTURE mikrofon,
  PLAYBACK reproduktor; zařízení je JSON `{"is_microphone":…}`) → `skipExchange(klíč,
  odchozí)` (výměnu udělal TDLib) → `connectP2p(servery, verze, p2p, customParameters)`.
  Před prvním dotykem `ContextUtils.initialize` (WebRTC čte kontext odtud).
- **`customParameters` z TDLib bývá prázdný řetězec a ntgcalls ho parsuje jako JSON** —
  `boost::json` vyhodí výjimku a ta shodí CELÝ proces (SIGSEGV při rozbalování výjimky
  v `P2PCall::connect`). Posílá se `{}`.
- **Server si vybere 8.0.0** i při nabídce 8/9/12/13 z ntgcalls; oficiální klient taky.
- **Oficiální Telegram na témže telefonu se cpe dopředu**: příchozí hovor dostanou obě
  aplikace a oficiální má celoobrazovkovou výzvu, která překryje Plume. Při ověřování se
  oficiální aplikace zastavila (`am force-stop org.telegram.messenger`). Skutečný uživatel
  s jednou aplikací to neuvidí; přijetí z pozadí ale chce full-screen intent a službu
  (níž).
- **Co sonda NEŘEŠÍ**: hovor na pozadí a se zamčenou obrazovkou (bez foreground služby
  `phoneCall` a `ConnectionService` ho systém může zastavit), přijetí z notifikace,
  sluchátko/reproduktor/Bluetooth, přechod Wi-Fi ↔ data, video, iOS a desktop.

## Obálka na Androidu

Hovor, který přežije zavřenou aplikaci a zamčený telefon:

- **Služba v popředí po dobu hovoru** (`CallService`, typ `phoneCall|microphone`,
  oprávnění `FOREGROUND_SERVICE_PHONE_CALL`, `FOREGROUND_SERVICE_MICROPHONE`
  a `MANAGE_OWN_CALLS`, bez kterého typ `phoneCall` Android 14 nepustí). Spouští ji
  `CallServiceControls`, jakmile hovor vytáčí / spojuje / běží — vždycky po akci uživatele
  v aplikaci, tedy z popředí; mikrofon na pozadí Android dá jen službě, která tak vznikla.
  Ověřeno: na ploše mikrofon dál nahrává (`dumpsys audio`, `silenced:false`).
- **Notifikace probíhajícího hovoru je `CallStyle.forOngoingCall`**: jméno, čas (chronometr
  od spojení) a „Zavěsit" od systému → `ACTION_HANG_UP` do služby. Kanál `calls`, potichu.
- **Příchozí hovor je `CallStyle.forIncomingCall`** s „Přijmout" a „Odmítnout", když Plume
  hovory umí (`NotificationPresenter(callsSupported)`), jinak postaru s větou o oficiální
  aplikaci. **`CallStyle` bez služby chce celoobrazovkový intent** — služba při zvonění
  neběží, takže `setFullScreenIntent` je povinný (`USE_FULL_SCREEN_INTENT`).
- **„Přijmout" otevírá aktivitu, ne broadcast**: trampolína přes přijímač je od Androidu 12
  zakázaná a přijetí chce aplikaci v popředí (mikrofon pro službu). `MainActivity` předá
  požadavek do `CallRequests`, shell se zeptá na mikrofon a zavolá `accept`.
- **Nad zámkem**: dokud hovor trvá, má `MainActivity` `setShowWhenLocked` a
  `setTurnScreenOn` — celoobrazovková výzva tak rozsvítí displej a ukáže obrazovku hovoru
  nad zámkem; přijmout jde bez odemčení. Po hovoru se obojí vrací.
- **U ucha zhasíná senzor přiblížení** (`PROXIMITY_SCREEN_OFF_WAKE_LOCK`), jen ve sluchátku
  a při běžícím hovoru.
- **Reproduktor** od Androidu 12 přes `setCommunicationDevice` (vestavěný reproduktor /
  sluchátko), starší `isSpeakerphoneOn`.
- **Obrazovka hovoru** (`CallOverlay`): nový hovor přes celou obrazovku, šipka nebo Zpět ji
  zmenší do pruhu nahoře (Zpět hovor neukončí), ťuknutí na pruh ji zase rozbalí, stejně
  tak ťuknutí na notifikaci (`CallRequests.show`). Pozadí `surface`, ne
  `surfaceContainer` — nevybraný `FilledIconToggleButton` má plochu `surfaceContainer`
  a na stejné ploše splynul.

Ověřeno na Pixelu 5 a Pixelu 7 Pro (Plume ↔ Plume): příchozí notifikace s Přijmout /
Odmítnout na ploše, přijetí z ní, hovor na ploše, reproduktor tam a zpět, zámek senzoru,
zavěšení ze systémové notifikace (služba skončí na obou stranách, zvuk zpět v normálu),
zhasnutý a zamčený telefon (rozsvítí se obrazovka hovoru nad zámkem, přijetí bez odemčení),
zmenšení, rozbalení a Zpět, a **Plume zabitý na pozadí** (`am kill`): push ho probudil,
notifikace i obrazovka hovoru přišly a přijetí spojilo.

**Co dál chybí**: iOS a desktop (video viz „Videohovory").

## Videohovory

Videohovor z nabídky profilu, kamera zapnutelná v každém hovoru (i v hlasovém, i u
protistrany), přední / zadní kamera, obraz protistrany přes celou obrazovku a náš náhled
v rohu. Ověřeno na Pixelu 5 a Pixelu 7 Pro: Plume ↔ Plume oběma směry, kamera zapnutá
uprostřed hlasového hovoru, vypnutá a znovu zapnutá, přepnutí kamery, kamera na pozadí
(plocha), a **Plume → oficiální Telegram pro Android** (obraz oběma směry).

- **Kameru bere ntgcalls 3.0.3 sám** (zdroj DEVICE, `VideoDescription(DEVICE, 1280, 720, 30,
  metadata kamery)`; metadata `{"id": …, "is_front": …}` z `NTgCalls.getMediaDevices()`).
  Snímky z ní vrací i nám (`onFrames`, CAPTURE + CAMERA) — to Mithka nad 2.2.5 neměla
  a kameru si brala sama přes EXTERNAL, s otáčením snímků a pády enkodéru. Pořadí argumentů
  `VideoDescription` se mezi 2.x a 3.x změnilo (`width, height, fps, input, keepOpen`).
- **Rozměr zdroje musí mít poměr stran kamery (na šířku)**: knihovna snímek škáluje přesně
  na něj a otočení nese zvlášť (`frame.rotation`). Rozměr na výšku by obraz zdeformoval.
- **Obraz protistrany se přijímá VŽDY**, i v hlasovém hovoru: PLAYBACK s kamerou EXTERNAL
  (rozměr 0 = podle snímku). Příjem videa ntgcalls zapne jen s ní (`enable_video_incoming`)
  a protistrana může kameru zapnout kdykoli.
- **Stav kamery protistrany** chodí signalizační zprávou `MediaState` a knihovna ho pošle do
  `onRemoteSourceChange` (CAMERA: ACTIVE / PAUSED / IDLING). Jen ACTIVE = je co ukázat.
- **Přepnutí kamery výměnou zdroje kameru ZHASNE.** Jiné `input` ve `setStreamSources`
  vyrobí nový zdroj dřív, než zruší starý: nový převezme sdílenou kameru (`SmartVideoSource`,
  „Reusing video source"), starý pak naplánuje její uvolnění za 200 ms („Released video
  source") a obraz stojí. Přepíná se proto přímo sdílená kamera — `SmartVideoSource.getInstance`
  se stejným rozměrem a jiným id zavolá `switchCamera` (třída je neveřejná, reflexí; keep
  pravidla ntgcalls jména drží). Když to po aktualizaci knihovny nepůjde, kamera se vypne
  a hned zapne (protistrana na okamžik uvidí vypnutou kameru).
- **Vykresluje `TextureViewRenderer` z AAR** (WebRTC nad EGL, sdílený kontext
  `JavaVideoCapturerModule.getSharedEGLContext()`) v `AndroidView`; `TextureView`, ne
  `SurfaceView`, aby šel oříznout do zaobleného rohu a překrýt ovládáním. Snímek z `onFrames`
  (bajty I420) se kopíruje do přímého bufferu z malé zásoby — nový buffer pro 1,4MB snímek
  30× za vteřinu by uvolnil až GC. Obraz stejné orientace jako plocha ji vyplní, jiné se vejde.
- **Kamera na pozadí chce službu typu `camera`** (`FOREGROUND_SERVICE_CAMERA`): typ se přidá
  se zapnutím kamery (vždycky akce v aplikaci, tedy z popředí) a ubere s vypnutím. Ověřeno:
  na ploše kamera dál dává 30 snímků/s a protistrana je kreslí (`types=0xC4` ve `dumpsys
  activity services`). **Typ `camera` je v Play potřeba deklarovat** u služeb v popředí.
- **Videohovor jde do reproduktoru** (a zapnutí kamery v hlasovém hovoru přepne sluchátko na
  reproduktor); senzor přiblížení s obrazem nezhasíná. Reproduktor vybraný při vytáčení,
  kdy hovor v Telecomu ještě není, platí hned a Telecomu se oznámí po přihlášení (viz
  „Kdo říká systému, kam jde zvuk").
- **Náhled kamery už při vytáčení** (a mezi přijetím a spojením): zdroj kamery ntgcalls
  vzniká až po `Ready`, takže by do té doby bylo černo. Kamera se proto otevře hned na
  sdílené kameře knihovny (`SharedCamera`: `SmartVideoSource.getInstance` + vlastní
  `CapturerObserver` + `open`) a snímky (`VideoFrame` s texturou ve sdíleném kontextu EGL) jdou
  rovnou do rendereru. **Před spuštěním přenosu se napevno uvolní** a zdroj knihovny kameru
  otevře znovu sám — převzít běžící kameru nesmí (viz „Zablokování při převzetí běžící
  kamery"); mezi `Ready` a spojením náhled chvíli stojí. Zrušený hovor kameru uvolní.
  Ověřeno na Pixelu 5 i Pixelu 7 Pro: náhled při „Volám…", přepnutí přední ↔ zadní při
  vytáčení, uvolnění kamery po zrušení.
- **Telecom dostane typ hovoru jen při přihlášení** (`CALL_TYPE_VIDEO_CALL`); kamera zapnutá
  později ho nezmění — na chování to vliv nemá.
- **Přijetí videohovoru** se ptá na kameru i mikrofon (`rememberVideoNotePermission`);
  kdo kameru nepovolí, přijme bez ní — protistranu vidí, ona jeho ne.
- **Obraz v obraze** (`rememberCallPictureInPicture` v `core/calls`): po odchodu z aplikace
  jde videohovor do okénka sám, **jen dokud je v hovoru obraz** (naše kamera, nebo
  protistrany) — hlasovému hovoru stačí notifikace. V okénku je jen obraz protistrany (bez ní
  náš), poměr stran podle jejích snímků (`CallVideo.onAspectRatio`, do prvního snímku 9 : 16).
  Akce pod okénkem: ztlumit / zapnout mikrofon a zavěsit (`RemoteAction`, ikony
  `ic_pip_mic*`, `ic_pip_hang_up` v prostředcích Androidu).
  - **Stejný mechanismus jako u videa v prohlížeči** (`docs/poznatky/prohlizec-medii.md`,
    „Obraz v obraze"): do okénka jde celá aktivita, `CallOverlay` v něm místo všeho kreslí jen
    obraz.
  - **Okénko si s prohlížečem dělí aktivita a vede ho jeden rozhodčí**
    (`PictureInPictureArbiter` v `core/video`). Parametry jsou jedny na aktivitu: dokud si je
    každý nastavoval sám, vypnul ten, kdo skončil, automatiku i druhému — zavřený prohlížeč
    během videohovoru poslal hovor po odchodu z aplikace místo do okénka na pozadí. Platí
    poslední přihlášený, který okénko právě chce (hrající video, hovor s obrazem); odhlášení
    jen znovu rozhodne. **„Jsem v okénku" dostane jen ten, kvůli komu se do okénka šlo**,
    ostatní „okénko je cizí" a nekreslí se: pruh hovoru ležel přes video v okénku.
  - Ověřeno na Pixelu 5 během videohovoru: otevřené a zavřené video v prohlížeči, pak Domů →
    okénko s hovorem (9 : 16); hrající video v prohlížeči a Domů → okénko s videem bez pruhu
    hovoru.
  - **Konec hovoru okénko zavře** (`moveTaskToBack`): v okénku by zbylo „Hovor skončil".
    Totéž, když obraz zmizí (obě kamery vypnuté).
  - **Ověřeno na Pixelu 5** (Plume ↔ Plume): Domů během videohovoru otevře okénko
    (`mode=pinned` v `dumpsys activity activities`) s obrazem protistrany, ztlumení z okénka
    tam i zpět (ikona se překreslí), zavěšení z okénka okénko zavře a uvolní kamery na obou
    stranách, návrat z okénka do aplikace. **Nabídka okénka se ukáže ťuknutím a za pár vteřin
    zmizí** — při ověřování se ťuká na okénko a hned na akci jedním `adb shell "…; sleep 0.7; …"`,
    akce leží dole v okénku (`mBounds` úlohy).
- **Při ladění logu: `grep` do souboru jen s `--line-buffered`.** Bez něj se poslední řádky
  do souboru nedostanou a vypadá to, že se nic nestalo (tak to vypadalo s přepnutím kamery).

## Ticho při spojeném hovoru

Všechny hovory až do přechodu na Telecom byly NĚMÉ — spojily se, čas běžel, Telecom, výstupy i změna sítě
fungovaly, ale protistranu nebylo slyšet na žádném telefonu. Nikdo si toho nevšiml, protože
se ověřovalo podle logu a obrazovky, ne poslechem.

- **V režimu `PLAYBACK` je slot `microphone` v `MediaDescription` mikrofon PROTISTRANY.**
  Příjem zvuku ntgcalls zapíná jen pro něj (`StreamManager::optimize_sources`:
  `enable_audio_incoming(writers_.contains(Microphone))`). Reproduktor ve slotu `speaker`
  (`MediaDescription(null, speaker, …)`, jak to dělá Mithka nad ntgcalls 2.2.5) ve 3.0.3 dá
  ticho: výstup Oboe se otevře, ale příchozí kanál nevznikne. Správně
  `MediaDescription(device(SPEAKER), null, null, null)` — metadata dál `is_microphone: false`,
  zařízení je výstup (kontroluje to `BaseDeviceModule`: `is_microphone` musí sedět s režimem).
- **Poznat to jde z logu**: s příjmem se objeví `Adding incoming audio channel with ssrc …`
  a `AddRecvStream`; bez něj jen `AddSendStream`. `Unable to set playout device`
  (`adm_helpers.cc`) je šum — vlastní ADM ntgcalls výběr zařízení neumí a nevadí to.
- **Hovor se ověřuje POSLECHEM na obou stranách**, ne stavem spojení. Spojené ICE/DTLS
  a běžící čas nic neříkají o tom, jestli jde zvuk oběma směry.
- **Signalizace před spuštěním přenosu se drží ve frontě** (`TdlCallRepository.pendingSignaling`)
  a po `start` se přehraje. Ticho nezpůsobovala (byla to první, mylná stopa), ale zahodit
  `InitialSetup` protějšku, který přijde před naším `Ready`, by dohodu kanálů rozbilo.

Ověřeno na Pixelu 5 a Pixelu 7 Pro poslechem: protistranu je slyšet na obou telefonech.

## Systémový telefon (Telecom, Android)

Hovory jsou přihlášené do Telecomu přes Jetpack `androidx.core:core-telecom` 1.0.1
(`TelecomCallBridge`): `CallsManager.registerAppWithTelecom(CAPABILITY_BASELINE)` při startu
a `addCall` pro každý hovor, jakmile vytáčí nebo zvoní. Pod Androidem 14 knihovna jede přes
`ConnectionService`, od 14 přes transakční API; obojí `self_mng` účet (`MANAGE_OWN_CALLS`).

- **Stav se zrcadlí z `CallRepository`**: spojení → `setActive` (odchozí) / `answer`
  (příchozí přijatý v aplikaci), konec → `disconnect` (`LOCAL`, u selhání `ERROR`).
  Opačně: `onAnswer` (auto, hodinky) → `accept`, `onDisconnect` → `hangUp`,
  `onSetInactive` / `onSetActive` → `setHeld`.
- **GSM hovor hovor Plume podrží** (`ACTIVE → ON_HOLD`, `onSetInactive` → mikrofon i přehrávání
  ntgcalls stojí, „Podrženo"). **Po GSM hovoru ho systém sám NEVRÁTÍ** — zůstal by viset
  podržený. Vrací ho uživatel tlačítkem „Pokračovat v hovoru" → `CallControlScope.setActive`
  (`CallRepository.resume` přes `setResumeHandler`), jako u systémového telefonu.
- **Výstupy a volbu vede Telecom** (`TelecomAudioRoutes`: `availableEndpoints`,
  `currentCallEndpoint`, `requestEndpointChange`), **systému ji ale říká Plume** — viz
  „Kdo říká systému, kam jde zvuk". `CallAudioRouter` zůstává jako záloha, když Telecom
  aplikaci nebo hovor nepřijme.
- **Na Androidu 14 (Pixel 5) přepíná Telecom reproduktor postaru**: `ActiveSpeakerRoute`,
  ale `Active communication device` v `dumpsys audio` dál hlásí sluchátko. Kam zvuk opravdu
  jde, říká `deviceId` přehrávače (2 = reproduktor) a řádky `Devices:` streamů.
- **Ověřeno na Pixelu 5 a Pixelu 7 Pro**: hovor v Telecomu na obou stranách (`self_mng`,
  `CONNECTING` / `RINGING` → `ACTIVE`), reproduktor tam a zpět přes Telecom, GSM hovor
  z jiného telefonu na Pixel 5 hovor Plume podržel, po něm „Resume call" vrátil `ACTIVE`
  a mikrofon zase nahrával; zavěšení (i podrženého hovoru) Telecom uklidí.
  Bluetooth přes Telecom (Pixel 7 Pro, Pixel Buds Pro 2): připojení sluchátek za hovoru
  přepnulo hovor na ně (`bt_sco_hs`), výběr přepnul na reproduktor a zpět.
  **Neověřeno**: Android Auto, hodinky, přijetí odjinud (`onAnswer`).
- **Pozor na staré řádky v `dumpsys audio`**: historie (`setCommunicationRouteForClient …
  bt_sco`) obsahuje zařízení i dávno odpojená. Jestli jsou sluchátka opravdu připojená,
  říká `dumpsys bluetooth_manager` (`ACL BR/EDR:Y`), ne výskyt jména v `dumpsys audio`.

## Zablokování při převzetí běžící kamery

**Zdroj kamery ntgcalls (`JavaVideoCapturerModule`) nesmí vzniknout nad kamerou, která už
běží** — celý přenos se zablokuje a s ním i vlákno `ntgcalls`, takže až do restartu aplikace
nespojí ani jeden další hovor (na druhé straně `TIMEOUT` po přijetí, u právě běžícího hovoru
po 20 s `FAILED`).

- **Mechanismus (ze zdroje ntgcalls 3.0.3 a zásobníků přes `jdb`)**: `StreamManager::start`
  (při spojení) i `handle_capture_config` (zapnutí kamery za hovoru) drží zámek `mutex_`
  a volají Java `JavaVideoCapturerModule.open`, která chce Java zámek `lock`. Vlákno kamery
  (`VideoCapturerThread`) ten `lock` drží v `onFrameCaptured` a v `nativeOnFrame` čeká na
  `mutex_`. Dokud kamera do zdroje snímky neposílá (běžný případ: `open` ji teprve spustí),
  souběh nenastane; převezme-li ale zdroj BĚŽÍCÍ kameru (`getInstance` → „Reusing video
  source"), snímky do něj tečou hned od založení. `NTgCalls.stop` pak čeká na `mutex_` navždy.
- **Spustil to náhled při vytáčení**, který kameru zdroji předával běžící (a stalo se jen
  občas: snímek musel přijít přesně, když `start` držel zámek). Stejně by to udělalo rychlé
  vypnutí a zapnutí kamery za hovoru — knihovna kameru uvolňuje až za 200 ms (`softRelease`).
- **Oprava (`SharedCamera.releaseNow` / `releaseCurrent`)**: náhled se před spuštěním přenosu
  napevno uvolní (`SmartVideoSource.release`) a vypnutá kamera za hovoru taky; zruší se přitom
  i odložené uvolnění knihovny, které by jinak po 200 ms vynulovalo odkaz na novou kameru.
  Přepnutí přední ↔ zadní nový zdroj nezakládá, to dál jde na běžící kameře.
- **Jak se to hledalo**: vlákno, které „jen čeká", se z `/proc/<pid>/task/*/wchan` nepozná
  (nečinný executor je taky ve `futex_wait_queue`). Rozhodl Java zásobník přes JDWP — aplikace
  je debug: `adb forward tcp:8701 jdwp:<pid>`, `jdb -attach localhost:8701`, v JEDNOM sezení
  `threads`, `thread <id>`, `suspend`, `where` (id se s každým připojením mění). `ntgcalls`
  stál v `NTgCalls.stop`, `VideoCapturerThread` v `nativeOnFrame`, nativní vlákno v `open`.
- **Ověřeno**: pět videohovorů Pixel 5 → Pixel 7 Pro za sebou (Pixel 7 Pro přijímal s kamerou,
  tedy s náhledem mezi přijetím a spojením; ve třetím kamera vypnutá a za 0,1 s zapnutá),
  všechny se spojily na obou stranách a kamery se po zavěšení uvolnily.

## Hovor z konverzace

Sluchátko v hlavičce soukromého i tajného chatu (`ConversationCallButton`): ťuknutí nabídne
hlasový hovor a videohovor, protějšku bez videohovorů volá rovnou. Ťuknutí na zprávu o hovoru
volá zpátky, stejně jako tehdy.

- **Komu jde zavolat, říká `ChatInfo.callUserId`**: protějšek soukromého nebo tajného chatu
  (v tajném se id chatu s id uživatele neshoduje), který je člověk (`UserTypeRegular`, ne bot,
  ne vlastní účet) a hovory od nás přijímá (`userFullInfo.canBeCalled` — soukromí hovorů).
  Videohovor nabízí jen s `supportsVideoCalls`. Nedostupné podrobnosti (bez sítě) hovor
  nezakazují; rozhodne server.
- **Zavolání zpátky jde přes stav, ne přímo z bubliny**: ViewModel zprávu převede na
  `ConversationState.callRequest` a vytočí tlačítko v hlavičce — dotaz na mikrofon a kameru umí
  jen kompozice. Když volat nejde, ViewModel ukáže hlášku „Tomuto člověku nejde zavolat".
- **Tlačítko je ve vlastní funkci**, `ConversationScreen` ho jen volá jedním řádkem — ten je
  v dexu na hraně (CLAUDE.md, Gotchas). Po změně ověřeno otevřením chatu na zařízení.
- **Během hovoru tlačítko zmizí** — druhý hovor naráz Plume nevede.
- **Ověřeno na Pixelu 5 a Pixelu 7 Pro**: sluchátko v soukromém chatu (ve skupině ne), nabídka
  Zavolat / Videohovor, ťuknutí na „Odchozí videohovor" vytočilo videohovor a druhý telefon
  zazvonil, podržení zprávy o hovoru otevřelo nabídku nad zprávou.

## Když hovor nejde zahájit

Důvod se ukáže na obrazovce hovoru místo stavu (`ActiveCall.failure`, `UiText`), u neúspěšného
hovoru 5 s místo 2 — je to věta, ne slovo.

- **Chyby hovoru chodí dvěma cestami.** Selhání samotného `createCall` / `acceptCall`
  (`Result`), nebo hovor vznikne a hned skončí `CallStateError` s hláškou od serveru. Omezení
  počtu hovorů přišlo tou druhou: `429 Too Many Requests: retry after 1579` — a **dřív než
  `Pending`**, takže hovor ještě nebyl v `active` a obrazovka se vůbec neotevřela; vytočení
  beze slova nic neudělalo. Chyba odchozího hovoru bez stavu proto stav založí.
- **Překlad je ve společné tabulce** (`errorText` v `TelegramErrors.kt`, testy
  `TelegramErrorsTest`): `USER_PRIVACY_RESTRICTED` i `USER_IS_BLOCKED` → „nepřijímá od tebe
  hovory" (zablokování se neprozrazuje), `PARTICIPANT_VERSION_OUTDATED` → aplikace protějšku
  hovory neumí, omezení počtu → „zkus to za N min" jako jinde. Ostatní spadne na „Hovor se
  nepodařilo spojit".
- **Hovor, který nikdy nezačal** (`createCall` selhal, chybí oprávnění), má id 0 (TDLib čísluje
  od 1) a probíhající hovor nepřepíše.
- **Zamítnuté oprávnění** (mikrofon, u videohovoru i kamera) se hlásí stejně
  (`CallRepository.reportFailure`): trvale odepřené by se systém už nezeptal a ťuknutí na
  Zavolat by neudělalo nic. U zvonícího hovoru důvod nahradí stav a zvonění pokračuje.
- **Ověřeno na Pixelu 5**: odebraný mikrofon (`pm revoke`), Zavolat z hlavičky, v dialogu
  „Nepovolovat" → obrazovka hovoru s větou o mikrofonu, po 5 s zmizí. Cesty se serverovými
  chybami ověřené jen testy překladu — soukromí by chtělo měnit nastavení testovacího účtu
  a omezení počtu desítky hovorů.

## Kdo říká systému, kam jde zvuk

**Vlastníkem režimu hovoru je Plume, ne Telecom** — a systém bere volbu výstupu JEN od
vlastníka režimu. U hovoru přes `CallsManager` (Jetpack core-telecom) Telecom
`MODE_IN_COMMUNICATION` nenastavuje; v historii `dumpsys audio` je jen `setMode` od Plume
(nastavuje ho od vytáčení kvůli tónu protistrany) a od Telecomu až `MODE_NORMAL` po konci.

- **Příznak: videohovor zhruba v každém pátém pokusu do sluchátka u ucha.** Telecom
  (i sám core-telecom u videohovoru) přepne na reproduktor (`ActiveSpeakerRoute`,
  `setCommunicationDevice` z pid system_serveru), systém jeho volbu nepoužije („Computed
  Preferred communication device: null"), Telecomu pak přijde hlášení „reproduktor vypnut"
  (`SPEAKER_OFF` z přijímače změn reproduktoru, ~10 ms po zapnutí) a vrátí se na sluchátko
  (`ActiveEarpieceRoute`). Zda to vyjde, rozhodovalo načasování.
- **Testovací smyčka s `am force-stop` to maskovala**: mrtvý proces přestane být vlastníkem
  režimu a systém pak volbu Telecomu vezme. Měřit se musí v jednom živém procesu.
- **Oprava (`TelecomAudioRoutes`)**: naši volbu (i odloženou z vytáčení) nastaví Plume hned
  sám (`setCommunicationDevice`) a Telecomu ji oznámí; 3 s po ní platí přednostně a Telecomu
  se oznamuje znovu, kdykoli se odchýlí (umí se vrátit i po potvrzení). Mimo tu lhůtu Plume
  zrcadlí, co hlásí Telecom (připojená souprava, volba v systému). `setCommunicationDevice`
  trvá stovky milisekund — jde vlastním vláknem (`call-audio`), stejným jako `setMode`.
- **Ověřeno**: 9 z 9 videohovorů z Pixelu 5 a videohovor z Pixelu 7 Pro (Android 17) do
  reproduktoru na obou stranách, hlasový hovor do sluchátka na obou, reproduktor tam a zpět,
  zapnutí kamery přepne na reproduktor. Skutečné zařízení potvrdil i `dumpsys
  media.audio_flinger` (`Output devices: 0x2 (AUDIO_DEVICE_OUT_SPEAKER)`).
  **Bluetooth s touto logikou ověřený NENÍ** (souprava při testu nebyla připojená).
- **Měřit jde jen pár desítek hovorů**: po zhruba čtyřiceti zkušebních hovorech za hodinu
  Telegram další odmítl (`429 Too Many Requests: retry after 1579`, hovor skončí
  `CallStateError`). Přijímat hovory omezení nebrání — zkoušet dál jde opačným směrem.

## Změna sítě za hovoru

- **Přechod Wi-Fi ↔ data řeší WebRTC v ntgcalls samo**: ICE s průběžným sbíráním kandidátů
  (`GATHER_CONTINUALLY`) a znovusbíráním po výpadku sítě (`regather_on_failed_networks`,
  `wrtc/src/interfaces/native_network_interface.cpp`). Nové kandidáty posílá signalizací
  přes TDLib; ten o změně sítě ví od `ConnectionController` (`setNetworkType`), takže
  signalizace po přepnutí neuvízne na mrtvém spojení.
- **ntgcalls výpadek NEHLÁSÍ**: odpojení vede jen na interní „Reconnecting" (zpět do
  `Connecting`, callback se nevolá), obnova taky ne. Až když spojení **20 s** stojí
  (`kMaxTimeout` v `native_connection.cpp`), přijde `FAILED` a Plume zavěsí — s
  `isDisconnected = true`, ať to protějšek dostane jako přerušený hovor, ne zavěšení.
- **„Čekám na připojení…"** místo času (obrazovka hovoru, pruh i notifikace) bere Plume
  z vlastního monitoru sítě (`NetworkKind.None`), protože od ntgcalls se to nedozví.
- **Ověřeno na Pixelu 7 Pro** (13. 10. 2026, Plume ↔ Plume s Pixelem 5): Wi-Fi vypnutá
  a znovu zapnutá za hovoru, mezitím zapnutá mobilní data — hovor pokračoval, WebRTC
  vytvořilo nová spojení přes mobilní síť (`connection.cc … Cellular`) a k `FAILED`
  nedošlo. **Úplná ztráta sítě** (Wi-Fi vypnutá, mobilní data vypnutá): na Pixelu 7
  „Čekám na připojení…", na Pixelu 5 hovor dál; po zapnutí Wi-Fi zase běží čas a hovor
  pokračoval (ověřil uživatel na telefonu).
- **Podrobný výpis WebRTC (`connection.cc`) vytlačí z logcatu vlastní řádky aplikace během
  desítek vteřin** — při ověřování hovorů číst log průběžně (`logcat | grep` na pozadí),
  ne až po testu. Na Pixelu 7 přes bezdrátové adb se navíc s Wi-Fi ztratí i adb.
- **Připojení a odpojení sluchátek za hovoru** (Pixel Buds Pro 2): zvuk přešel na
  soupravu a po odpojení zpět do sluchátka telefonu (`setCommunicationRouteForClient …
  earpiece` v systémovém logu); ověřil uživatel.
- **Ukončení po 20 s bez sítě ověřené z obou stran**: (a) Pixel 7 bez sítě — Pixel 5 (se
  sítí) nahlásil `FAILED` 32 s po spojení a hovor sám ukončil; (b) Pixel 5 bez sítě —
  „Waiting for network…", po ~22 s `FAILED`, ukončení, úklid (režim zvuku, služba).
  Spadlý hovor, který už běžel, hlásí „Hovor se přerušil", ne „nepodařilo se spojit"
  (`connectedAt` je vyplněné). Záznam logu Pixelu 7 přežije výpadek bezdrátového adb, když
  běží přímo v telefonu (`nohup sh -c "logcat | grep … > /data/local/tmp/…"`).

## Vyzvánění a výstup zvuku (Android)

- **Příchozí hovor zvoní `CallRinger`**: vyzváněcí tón telefonu (`RingtoneManager`,
  `USAGE_NOTIFICATION_RINGTONE`) ve smyčce a vibrace jako při vyzvánění, dokud se hovor
  nepřijme, neodmítne nebo nezruší. Řídí se režimem zvonění (tichý = nic, vibrace = jen
  vibrace, normální = tón a vibrace, když je zapnutá systémová „Vibrace při vyzvánění",
  klíč `vibrate_when_ringing`); **Nerušit** (filtr jiný než „vše") zvonění vypne. Vibrace
  chtějí oprávnění `VIBRATE` — haptika jinde jde přes view a nepotřebuje ho.
- **Notifikace příchozího hovoru je v tichém kanálu `incoming-calls`**, ne `setSilent`:
  tichou notifikaci Android nebere jako vyrušující a **celoobrazovkovou výzvu nespustí**
  (ověřeno: s `setSilent` zůstal zhasnutý telefon zhasnutý a jen zvonil). Zvuk kanálu po
  založení změnit nejde, proto nový kanál bez zvuku a vibrací.
- **Odchozí hovor má tón protistrany** (`ToneGenerator.TONE_SUP_RINGTONE` na
  `STREAM_VOICE_CALL`) do zvednutí. **Jen v režimu hovoru**: mimo něj ho systém pustí jako
  DTMF a telefon na vibracích ho ztlumí úplně (stopa v `dumpsys media.audio_flinger` měla
  `-inf` dB, v režimu hovoru −29 dB).
- **Zvuková relace (režim hovoru + výstup) začíná už vytáčením / přijetím**
  (`CallMedia.startAudio`), ne až spojením: tón protistrany potřebuje režim hovoru a přepnout
  reproduktor má jít hned. Končí s `stop` — i u hovoru, který se nespojil. Po hovoru se
  vrací vždycky `MODE_NORMAL`, ne „předchozí" režim.
- **Výstup vybírá `CallAudioRouter`**: sám od sebe Bluetooth > sluchátka (kabel, USB) >
  sluchátko telefonu. Dřív Plume na začátku hovoru natvrdo nastavil sluchátko telefonu
  a připojenou soupravu tím přebil. Připojení soupravy za hovoru na ni přepne i z
  reproduktoru; zmizí-li vybrané zařízení, vrátí se automatická volba. Od Androidu 12
  `setCommunicationDevice` nad `availableCommunicationDevices`, starší `startBluetoothSco`
  a `isSpeakerphoneOn`.
- **Jen sluchátko a reproduktor = přepínač „Reproduktor"**, se soupravou nebo sluchátky
  výběr z nabídky (Telefon / Reproduktor / Sluchátka / název soupravy) s ikonou a popiskem
  podle toho, kam zvuk jde.
- **Ověřeno na Pixelu 5 a Pixelu 7 Pro**: zhasnutý Pixel 7 se rozsvítil a zvonil tónem
  i vibrací (`dumpsys audio` / `vibrator_manager`, usage RINGTONE), přijetím zvonění
  i tón protistrany utichly, zrušení volajícím zvonění zastavilo, tón protistrany v režimu
  hovoru (Pixel 5 na vibracích). **Pixel Buds Pro 2**: hovor šel sám do soupravy
  (`bt_sco_hs`), výběr přepnul na reproduktor, telefon (se senzorem přiblížení) a zpět,
  po hovoru souprava zpět na A2DP; připojení a odpojení soupravy za hovoru (viz „Změna
  sítě za hovoru"). **Neověřeno**: kabelová sluchátka, Android pod 12 (cesta přes
  `startBluetoothSco`).

## Desktop

Hlasové hovory na desktopu přes **C API ntgcalls 3.0.3** (`NtgNative` — vazba JNA,
`DesktopCallMedia`), stejné pořadí volání jako na Androidu. Jen zvuk; video (`supportsVideo`
= `false`) se nenabízí, videohovor se vytočí i přijme jako hlasový.

- **Vazba přes JNA, ne JNI ani Java FFM.** Desktop jede na JDK 17 (FFM je finální až v 22)
  a ntgcalls Java vazby pro desktop nemá (jen Android, C, Node, Python, Rust). C API je
  synchronní, struktury se předávají hodnotou (`Structure.ByValue`), zpětná volání chodí
  z vláken knihovny a **musí zůstat silně odkazovaná**, jinak je GC uklidí.
- **C `bool` jako `Byte` 1/0, ne Kotlin `Boolean`.** JNA převádí `Boolean` na `int`
  a knihovna `is_outgoing = true` četla jako nepravdu: desktop jako volající se choval jako
  volaný, `InitialSetup` (ICE ufrag/pwd a otisk DTLS) neposlal nikdo — signalizace tekla, ACK
  chodily, ale spojení skončilo po 10 s TIMEOUT. Jako volaný (`false`) desktop fungoval, proto
  se to projevilo jen jedním směrem. Ve strukturách je `bool` 1 bajt tak jako tak.
- **Zvuk bere knihovna sama**: macOS AudioQueue (CoreAudio), Windows Core Audio, Linux
  Pulse/ALSA — nezávisle na tom, že desktop jinak zvuk přehrávat neumí. Metadata zařízení si
  každý modul čte po svém: macOS prázdné `uid` = výchozí zařízení systému, Windows `index` 0 =
  výchozí, Pulse a ALSA chtějí `id` (bere se první zařízení, které knihovna vypíše). Výstup se
  nepřepíná (jediný „Reproduktor", tlačítko výstupu se s jedním výstupem nekreslí).
- **Knihovnu přibalí Gradle** (`downloadNtgcalls` v `client/shared/build.gradle.kts`): jen pro
  systém, na kterém se staví, z vydání na GitHubu, ověřenou SHA-256 (`digest` z API vydání),
  do resources `jvmMain` pod prefix JNA (`darwin-aarch64/libntgcalls.dylib`, 35 MB), odkud ji
  `Native.load` sám vybalí. Zip se drží v `~/.gradle/caches/plume-ntgcalls/`. **Bez sítě nebo
  na Intel Macu build nespadne** — `CallMedia` se nezaregistruje a hovory se nenabídnou.
  Knihovna závisí jen na systémových frameworcích (žádné cesty do Homebrew jako TDLib).
- **Při ladění pomůže log knihovny**: `ntg_set_log_callback`, zdroj 2 = ntgcalls (zprávy
  signalizace „Sending initial setup", „processSignalingData"), zdroj 1 = WebRTC. Běžně jen
  chyby; WebRTC na úrovni informací píše desítky řádků za vteřinu.
- **Ověřeno na macOS (arm64) s Pixelem 5**: příchozí hovor na desktop i odchozí z desktopu,
  zvuk oběma směry (poslechem), emoji shodná, zavěšení z desktopu i z telefonu. **Windows
  a Linux jen přeložené.** Chybí zvonění a oznámení příchozího hovoru (zazvoní jen obrazovka
  hovoru v otevřené aplikaci) a video.

## Neověřené

- **VoIP pushe na iOS** pro aplikaci s vlastním `api_id` (`deviceTokenApplePushVoIP`) — bez
  nich hovor na zamčeném iPhonu nezazvoní; Plume zatím čeká i na běžný APNs certifikát.
- **Spojení jen přes TURN bez reflectoru** — až s mediální vrstvou.
