# Hlasovky a hudba

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

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
- **Dokud Android hrál přes `MediaPlayer`, `playbackParams` na PAUZNUTÉM přehrávači přehrávání
  ROZJEL** (zdokumentované chování). ExoPlayer (`setPlaybackSpeed`) to nedělá, rychlost se mu
  zapisuje rovnou. Na iOS `rate` na pauznutém přehrávači taky nic nespustí; zato se `enableRate`
  MUSÍ nastavit PŘED `prepareToPlay()` a `play()` rychlost resetuje na 1, takže se zapisuje znovu
  hned po něm.
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
- **Na Androidu se kvůli tomu mění atributy zvuku.** Do sluchátka se dostane jen zvuk
  s `USAGE_VOICE_COMMUNICATION`. Samotné `setCommunicationDevice` nestačí — to řídí komunikační
  zvuk, kdežto médium by hrálo dál z reproduktoru. S `MediaPlayer` se kvůli tomu přehrávač
  stavěl znovu (atributy jen před `prepare()`); ExoPlayer je mění za běhu
  (`setAudioAttributes`) a zvukovou stopu si postaví sám. **Po přechodu na ExoPlayer ověřené
  na Pixelu 7 Pro** (Android 17, 15. 10. 2026): uživatel slyšel u ucha sluchátko a po odtažení
  zase reproduktor a přehrávání šlo dál. V logu `Applying proximity: Positive` a hned nato
  `setCommunicationRouteForClient … type:earpiece`. V historii `dumpsys audio` je při každém
  přepnutí nová `AudioTrack` (`new player … started` → `device updated deviceIds:[2]/[3]`),
  takže stopu za běhu přestavuje ExoPlayer sám a ručně se nic přestavovat nemusí.
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
  odchozích protějškem chodí `UpdateMessageContentOpened` (`contentOpenedUpdates`). Ověřeno se
  dvěma účty (12. 10. 2026): po přehrání u příjemce zmizela tečka u odesílatele.
- **Android přehraje Ogg/Opus sám** (Media3 ExoPlayer: Ogg rozebere, Opus dekóduje systémový
  kodek; atributy řeč/média). Dřív `MediaPlayer` — viz „Přeskakování ticha".
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
- **`VoiceRecorder` není vláknově bezpečný — jeho rozsah musí běžet na vlákně volajícího.**
  V aplikaci je to `viewModelScope` (`Main`), takže tik úrovní a `finish` se nepotkají.
  `VoiceRecorderContinueTest` mu ale dával `Dispatchers.Default` a při plném `jvmTest` občas
  (2× ze 6) padal na `assertNotNull` druhého `finish`: tik, který už prošel `delay`, nejde
  zrušit, takže po `reset` zapsal stav zpátky. Další `start` ho vzal za běžící nahrávání, nic
  nespustil a `finish` vrátil `null` (`startedAt == null`). Okno je pár mikrosekund mezi
  `finish` a dalším `start`; spolehlivě ho otevřelo až 200 ms čekání mezi nimi, ne zpomalený
  tik. Ten jen posunul pozdní zápis za `start`. Test teď nahrávači dává rozsah `runBlocking`
  (`this`), a to i s oběma pokusy prošel. **Kdo nahrávač testuje, nedává mu cizí dispatcher.**
- **Nahrávač je `factory`, ne `single`** — patří obrazovce. Odchod z chatu nahrávku zahodí,
  spuštění nahrávání zastaví hrající hlasovku (nahrávala by se z reproduktoru).
- **Odesílá se jako samolepka** (`sendMedia`): bez letu, rozepsaný text zůstává, citace se použije.

## Hudba

`MessageAudio` je `MessageMedia.Audio` a hraje přímo v bublině (`AudioContent`): obal alba
jako tlačítko, název, interpret, lišta průběhu s posunem, čas a čip rychlosti.

- **Hraje TENTÝŽ přehrávač jako hlasovky** (`VoicePlayer`, klíč `VoiceKey`, akce
  `ToggleVoice`/`SeekVoice`). Zvuk je v aplikaci vždycky nejvýš jeden — skladba zastaví
  hlasovku i kolečko se zvukem a naopak — a druhý přehrávač by to musel hlídat zvlášť.
  `AudioEngine` hraje cokoli, co ne-Ogg: Android ExoPlayer, iOS `AVAudioPlayer` rovnou
  (dekódování do WAV je jen pro Ogg/Opus).
- **Hudba u ucha nepřepíná do sluchátka** (`VoicePlayer.play(music = true)`): senzory se vůbec
  nezapnou (ověřeno `dumpsys sensorservice`), jinak by telefon v ruce při poslechu zhasínal displej.
- **Po dohrání hraje další novější skladba v chatu** — playlist, bez ohledu na „poslechnuto",
  které u hudby není. Hlasovky a skladby se mezi sebou neřetězí.
- **Poslech se hlásí TDLib** (`listenToAudio`, kontrakt jako metriky zobrazení): `MusicQueue`
  podává stav fronty `ListeningTracker`u, ten sčítá jen čas, kdy skladba opravdu zněla (pauza,
  stahování a hledání se nepočítají), a nahlásí ho, když skladba skončí, uživatel pustí jinou,
  nebo hudbu zastaví. Pod vteřinu se nehlásí (omylem ťuknutá skladba). Uživatel z toho nic
  nevidí; selhání se jen zaloguje. Hlasovky ne — ty mají „poslechnuto" přes otevření obsahu.
  - **Hodiny jsou monotónní** (`TimeSource.Monotonic`) a do třídy se podávají zvenku, testy
    (`ListeningTrackerTest`) jedou na virtuálních.
  - **Ověřeno na emulátoru** (log `Poslech skladby N: s`): skladba dohraná do 0:09, deset vteřin
    v pauze a zavřený dock → nahlášeno 9 s, TDLib bez chyby. Při ověřování přes adb se časy
    s odhadem rozcházely o pár vteřin — zdržení mezi příkazy `input`, ne chyba počítání.

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
- **Fronta umí i vlastní seznam skladeb** (`togglePlaylist`, hudba v profilu): sousedé jsou
  `QueueTrack` (id + skladba) a u seznamu jsou známí hned (`Current.neighborsKnown`), v chatu je
  dohledá `musicNeighbors`. Id ve vlastním seznamu nesmí potkat id zprávy v chatu klíče — hudba
  v profilu proto bere záporné id souboru (`docs/poznatky/profil-chatu.md`, „Hudba v profilu").
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

## Přeskakování ticha

Volba v Nastavení → Chaty → Zprávy („Přeskakovat ticho", `AppSettings.skipSilence`, výchozí
vypnuto): v hlasovkách i audiu se zkrátí pauzy, hlas se nezrychlí ani nezkreslí. Media3
`skipSilenceEnabled`, platí hned i pro to, co právě hraje (`VoicePlayer` sleduje nastavení).

- **Kvůli tomu Android přešel z `MediaPlayer` na ExoPlayer** (`AudioEngine.android.kt`).
  `MediaPlayer` ticho přeskakovat neumí a vlastní zpracování zvuku by znamenalo vlastní
  dekodér. Přehrávač zůstává JEDEN (`VoicePlayer`), vyměnil se jen engine pod ním — systémové
  ovládání hudby (`MusicSessionPlayer`) se nezměnilo.
  - **ExoPlayer se ovládá z vlákna, na kterém vznikl**, a hlídá to výjimkou. `VoicePlayer` běží
    na `Dispatchers.Main`, takže engine vzniká i žije na hlavním vlákně; soubor si ExoPlayer
    čte na svém.
  - **`load` čeká na `STATE_READY`** (`suspendCancellableCoroutine`), teprve pak je známá délka.
    Chyba přípravy (`onPlayerError`) vyletí jako výjimka a `VoicePlayer` ukáže „nepodařilo se
    přehrát", jako dřív.
  - **Audio focus dál řešíme sami** (`handleAudioFocus = false`): hlasovka chce jiný focus než
    hudba a přerušení se musí promítnout do stavu (`onInterrupted`).
  - **Přerušení se pozná podle `playWhenReady`, ne `isPlaying`** — ten je `false` i ve chvíli,
    kdy přehrávač jen čeká na data.
  - **Hudba má `WAKE_MODE_LOCAL`**: hraje se zhasnutým displejem ze služby a vlákno přehrávače
    nesmí usnout (oprávnění `WAKE_LOCK` aplikace už má kvůli uchu).
- **Volba je v Nastavení, ne u čipu rychlosti.** Volí se jednou a platí pro všechno, stejně jako
  rychlost; čip je jen u hrající hlasovky a druhý prvek vedle něj by bublinu přeplnil.
- **Jen Android** (`skipSilenceSupported`): `AVAudioPlayer` na iOS to neumí a desktop zvuk nemá;
  řádek v Nastavení tam chybí.
- **Posuvník při přeskakování poskakuje** — pozice je čas v nahrávce, ne čas poslechu, takže přes
  zkrácenou pauzu přeskočí. Je to správně: délka i pozice dál odpovídají souboru.
- **Ověřeno na emulátoru** (Android 17, 11minutová skladba od bota: 2 s tónu, 3 s ticha
  dokola): bez volby 21 s nahrávky za ~21 s, s volbou 40 s nahrávky za 20 s. Přepnutí za běhu
  zabralo bez restartu přehrávání. **Neověřeno**: skutečná řeč (zkrácení přirozených pauz),
  hlasovka v Ogg/Opus přes ExoPlayer na Androidu 9 a starším (M4A), u ucha.

## Paměť pozice v dlouhé nahrávce

Hlasovka nebo audio od 10 minut (`MIN_RESUME_AUDIO_DURATION_MS`) si pamatuje, kde uživatel
přestal, a příště pokračuje odtamtud. **Tentýž mechanismus jako u videa** (`VideoPositions`,
viz „Video" v `prohlizec-medii.md`): jeden seznam v `AppSettings`, klíč je trvalé id souboru
(`MessageMedia.Audio.fileKey` / `VoiceNote.fileKey` = `RemoteFile.uniqueId`), stejné okraje
(ne prvních 10 s, ne posledních 15 s, dohraná se zapomene), nejvýš 200 záznamů.

- **Práh je delší než u videa** (10 min proti 2): dvouminutová hlasovka se pustí znovu celá,
  k přednášce nebo podcastu se člověk vrací. Seznam je společný, proto se pamatovaná pozice při
  spuštění ověřuje znovu s prahem pro zvuk (`audioStart`, testy `AudioResumeTest`).
- **Zapisuje přehrávač, ne obrazovka** (`VoicePlayer`, parametr `resumeKey`): při pauze,
  zastavení (i přepnutí na jiný zvuk), posunu pozastavené nahrávky a každých 10 s za přehrávání
  (zabitý proces). Jen u načteného zvuku — zastavení během stahování by zapsalo nulu a pozici
  smazalo. Hudba hraje i bez konverzace, takže jinde to být nemůže.
- **Odkud začít**: výslovný čas („1:23" v textu) > pamatovaná pozice > začátek — pořadí jako
  u videa.
- **„Pokračuje od 12:34 · Od začátku"** je snackbar v konverzaci (`ConversationPlayback.resumes`,
  `VoicePlayback.resumedFromMs`), u videa je to pilulka v přehrávači. Bez něj by nahrávka hrála
  uprostřed bez vysvětlení. Hlásí se jen v prvních 6 s poslechu a jednou za ViewModel;
  **posun (i „Od začátku") `resumedFromMs` maže** — dokud ne, návrat do chatu ukázal hlášení
  znovu, protože pozice před bodem pokračování prošla podmínkou „ještě v okně".
- **Bublina ukazuje pamatované místo i bez přehrávání**: lišta (u hlasovky waveform) a čas
  „7:27 / 11:20" (`rememberedAudioPosition`). Obdoba proužku zhlédnutého u videa.
- **Tajný chat a mizející zprávy si pozici nepamatují** (`remembersPosition`, v profilu
  `isSecretChat`); u hudby to nese `MusicQueue` i na navazující skladby. Zvuk v článku taky ne
  (nemá klíč).
- **Ověřeno na emulátoru** (11minutová skladba): po posunu na půlku a pozastavení bublina
  ukázala 5:42 / 11:20 i po `force-stop`; ťuknutí pokračovalo s „Resuming from 5:42 · From the
  start" a akce vrátila nahrávku na 0:01; pozice zapsaná desetivteřinovým tikem přežila
  přeinstalaci uprostřed hraní; dohraná nahrávka se zapomněla (bublina zase jen „11:20").
  **Neověřeno**: dlouhá hlasovka (Ogg/Opus přes Bot API nevyrobíme bez kodéru), iOS (jen
  přeložené).
