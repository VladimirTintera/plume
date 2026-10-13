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

**Co dál chybí**: vyzvánění (kanál `calls` hraje zvuk notifikace, ne vyzváněcí tón ve
smyčce), Bluetooth a sluchátka s kabelem, přechod Wi-Fi ↔ data za hovoru, integrace se
systémovým telefonem (`ConnectionService` / `core-telecom`: souběh s GSM hovorem, hovor
v autě), video, iOS a desktop.

## Neověřené

- **VoIP pushe na iOS** pro aplikaci s vlastním `api_id` (`deviceTokenApplePushVoIP`) — bez
  nich hovor na zamčeném iPhonu nezazvoní; Plume zatím čeká i na běžný APNs certifikát.
- **Spojení jen přes TURN bez reflectoru** — až s mediální vrstvou.
