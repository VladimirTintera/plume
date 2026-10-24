# Wear OS

Samostatná aplikace pro hodinky (`client/wearApp`). Plán a co zbývá: `docs/roadmap.md`, „Wear OS".

## Skladba

- **Stojí jen na `:client:core`** (a přes něj `:client:resources`). Vrstvu kolem TDLib dostane
  celou z `coreModule()` (`WearApplication`); UI telefonu (`shared`, `appModule()`) ani jeho
  knihovny do APK nejdou. TDLib se startuje s procesem, stejně jako na telefonu.
- **UI je Compose for Wear OS 1.7** (`androidx.wear.compose:compose-material3`), ne M3
  z `shared`: kulatý displej má vlastní komponenty (`AppScaffold`, `ScreenScaffold`,
  `TransformingLazyColumn`, `TitleCard`). Texty jsou v `:client:resources` s prefixem `wear_`.
- **Hook `TdlibJsonRepair`** dává plugin `plume.tdlib-android` jako u telefonu (CLAUDE.md:
  každý vstupní bod s TDLib). Ověřeno `dexdump -d` nad APK hodinek.
- **`applicationId` je stejné jako telefon** (`eu.tintera.plume`): Wear OS aplikace jde v Play do
  téhož záznamu jen se stejným id. Třídy mají vlastní balíček `eu.tintera.plume.wear`.
- **ABI jen hodinek** (`arm64-v8a`, `armeabi-v7a`): TDLib má na ABI přes 15 MB a x86 hodinky
  nepotřebují. Debug APK má 83 MB.
- **`minSdk` 30** (Wear OS 3), `com.google.android.wearable.standalone = true`.

## Přihlášení

- **Telefonní číslo se na hodinkách přeskočí:** na `WaitPhoneNumber` se rovnou volá
  `startQrLogin()`. Kód z SMS, e-mail a registrace obrazovka nepíše a nabídne návrat k QR kódu
  (`restartLogin()`).
- **QR kód má 0,62 kratší strany displeje a na začátku je svisle uprostřed.** Do kulatého
  displeje se vejde čtverec o straně 1/√2 průměru; první verze (150 dp pod nadpisem) měla spodní
  roh i s pozičním čtvercem uříznutý a čtečka ho nemusela najít. Bílý okraj kolem kódu, na
  černém pozadí hodinek by ho čtečka nenašla. Odkaz po vypršení vymění TDLib sám.
- **Heslo dvoufázového ověření systémovou klávesnicí** (`RemoteInput` přes `androidx.wear:wear-input`):
  Compose for Wear OS textové pole nemá. Klávesnice heslo nemaskuje — jiná cesta na hodinkách není.
- **Ověřeno na emulátoru 2026-10-24:** sken telefonem (Nastavení → Zařízení → Připojit zařízení),
  `AuthorizationState -> Ready`, pak seznam chatů. Heslo nevyzkoušené (účet ho nechtěl).

## Seznam chatů

- `ChatRepository.chats()` jako na telefonu; repository seznam po přihlášení načte sama.
  Karta: fotka chatu a název, náhled poslední zprávy (`UiText.resolve()`), v rohu počet
  nepřečtených, a když nic nečeká, čas poslední zprávy (dnes hodina, jinak datum).
- **Fotka chatu** (`ChatAvatar`): malá varianta (`avatarFileId`), stažení spustí odběr
  `FileRepository.file()` sám; dekóduje se mimo hlavní vlákno rovnou zmenšená (`inSampleSize`
  mocninou dvou) na velikost avataru — hodinky mají málo paměti. Bez fotky iniciála.
- **Položky seznamu BEZ klíče.** S `key = id` seznam po přihlášení začínal uprostřed: chaty
  přibývají postupně a lazy seznam drží první viditelnou položku, takže nové nahoře odjely nad
  displej i s nadpisem. Na hodinkách se má začínat nahoře.

## Konverzace

- **Jen pro čtení** (`ConversationScreen`): posledních 30 zpráv, nejnovější dole a seznam se na
  ni postaví při otevření i s každou novou. Nové, upravené a smazané zprávy se odebírají DŘÍV
  než historie, ať se mezi tím nic neztratí.
- **První `history(…, fromMessageId = 0)` vrací TDLib často jen kus z lokální databáze**
  (klidně jednu zprávu) — dočítá se od nejstarší, dokud stránka není plná nebo historie neskončí.
- **Otevřený chat se TDLib ohlásí** (`openChat`, při odchodu `closeChat` v `APP_SCOPE`, scope
  obrazovky v `onDispose` už je zrušený) a **zobrazené zprávy se hlásí jako přečtené** (`markRead`),
  i ty, které přijdou, zatímco je konverzace otevřená. Kdo ji na hodinkách viděl, četl ji.
- Bublina: `text`, a když je prázdný, `description` (fotka bez popisku, anketa…) jako na telefonu;
  jméno odesílatele jen tam, kde se liší od názvu chatu (skupiny); servisní zprávy uprostřed.
- **Navigace** `SwipeDismissableNavHost` (`compose-navigation`): zpět tahem doprava jako všude na
  hodinkách. Konverzace dostane id chatu v cestě (`chat/{chatId}`).
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora (skutečný účet — chaty lidí se
  neotevírají, otevření je označí přečtené): historie, živá zpráva během otevřené konverzace,
  tah zpět, v seznamu pak místo počtu nepřečtených čas.

## Odpověď a reakce

- **Odpověď tlačítkem dole** (`EdgeButton` ve `ScreenScaffold`, vzor M3 Expressive pro hodinky):
  systémové zadávání (`RemoteInput` přes `RemoteInputIntentHelper`) nabízí emoji, diktování
  a klávesnici, pod nimi naše **rychlé odpovědi jako volby** (`setChoices`, texty
  `wear_quick_reply_*`). Ťuknutí na volbu pošle zprávu rovnou, bez potvrzení.
- **Odeslaná zpráva se ukáže hned** (dočasné id ze `send`), stav „Odesílání…" / „Neodesláno"
  místo času; po potvrzení ji `sentMessageUpdates` vymění za tu se skutečným id — nahradit, ne
  aktualizovat, TDLib id změní.
- **Samotné emoji (i z rychlé odpovědi „👍") nemá text ani popis:** Telegram ho pošle jako
  `MessageAnimatedEmoji`, `core` z něj udělá přílohu `MessageMedia.AnimatedEmoji` a text nechá
  prázdný, aby se pod animací nekreslil dvakrát. Hodinky animace nekreslí, takže znak berou
  z přílohy (i u `Dice`) a kreslí ho velkým písmem — bez toho byla odpověď prázdná bublina.
- **Reakce:** ťuknutí na zprávu otevře výběr (vlastní cesta v navigaci, zpět tahem) s emoji, která
  chat u zprávy dovoluje (`availableReactions`, nejvýš 12); daná je zvýrazněná a ťuknutí ji
  odebere (`setReaction(add = false)`). Stav si výběr načte sám (`message(chatId, messageId)`).
  Pod bublinou „👍 2  ❤", živě z `interactionUpdates`. **Vlastní emoji se vynechávají**
  (`customEmojiIdOf`) — hodinky je nevykreslí a klíč reakce by se ukázal jako kód.
- Reagovat jde jen na zprávu se skutečným id (ne odesílanou), servisní ne.
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora: rychlá odpověď „👍" (odešla,
  potvrzená, velké emoji), reakce ❤ přidaná a znovu odebraná. Diktování a klávesnice na emulátoru
  nevyzkoušené (vracejí text stejnou cestou jako volba).

## Hlasovky

- **Přehrávání vlastním tenkým přehrávačem** (`VoicePlayback`, systémový `MediaPlayer`): Ogg/Opus
  přehraje Android sám od verze 10, Media3 z telefonu sem nejde. Nejvýš jedna naráz, poslech se
  ohlásí TDLib (`openContent`) jako na telefonu. Stažení spustí odběr `FileRepository.file()`.
- **Past: `stop()` uvnitř `MediaPlayer().apply { … }` je `MediaPlayer.stop()`**, ne metoda
  obalu — příjemce `apply` ho zastíní. Po dohrání se zastavil jen přehrávač a tlačítko zůstalo
  na „hraje" (`dumpsys audio` ukázal přehrávač `stopped`). Proto `this@VoicePlayback.stop()`.
- **Nahrávání** (`VoiceRecorder`, `MediaRecorder`): Ogg/Opus mono 48 kHz, 32 kbit/s jako telefon;
  úrovně pro waveform každých 50 ms ze `maxAmplitude` (odmocnina), zabalené `encodeWaveform`
  z `core`. Kratší než vteřina se zahodí (`stop()` po krátkém nahrávání navíc vyhodí výjimku).
  Ověřeno `file` nad nahrávkou z hodinek: „Ogg data, Opus audio, mono, 48000 Hz".
- **O mikrofon se žádá až na obrazovce nahrávání**, ne při startu. Ťuknutí spustí, druhé zastaví
  a pošle (`sendVoiceNote`); zahodit jde tahem zpět, nahrávka se při odchodu smaže.
- Nahrané soubory zůstávají v `cacheDir` (`voice-*.ogg`): TDLib je nahrává z cesty na pozadí,
  smazat je hned po odeslání by nahrávání utnulo. Úklid nechává na systému.
- Tlačítko přehrát v příchozí bublině má plnou barvu: tónová výplň je stejná jako příchozí
  bublina a tlačítko v ní zmizelo.
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora: žádost o mikrofon („Only this time"),
  nahrávka 0:03 odešla a potvrdila se, přehrání vlastní (z disku) i příchozí (soubor poslaný
  botem přes `sendVoice`, stažení) — „Pause" během přehrávání, po dohrání zpět „Play". Mikrofon
  emulátoru bez povolení hostitele nahrává ticho; skutečný zvuk a reproduktor hodinek neověřené.

## Push a notifikace

- **Vlastní push:** hodinky mají vlastní sezení, takže i vlastní FCM token (`WearPushRegistrar`
  ho po přihlášení zaregistruje u TDLib) a vlastní službu (`WearMessagingService`: celý JSON do
  `processPush`, počká na vydání). Hovory hodinky nemají.
- **`google-services.json` je kopie z `androidApp`:** plugin ho čte jen z adresáře modulu a
  hodinky mají stejné `applicationId`, takže platí tentýž soubor. Změní-li se projekt Firebase,
  mění se oba. Bez souboru se hodinky přeloží, jen bez pushe.
- **Notifikace kreslí `WearNotifications`** (telefonní presenter je ve `shared`): jedna na chat
  (`MessagingStyle`, id = `notificationGroupId`), stažená skupina (přečteno jinde) zmizí, ťuknutí
  otevře aplikaci. Název konverzace jen ve skupině — v soukromém chatu je odesílatel totéž co
  název a titulek zněl „Notifikator: Notifikator". Odpověď z notifikace zatím není.
- **O notifikace se Android 13+ ptá za běhu** — hodinky jednou, hned po přihlášení.
- **Zrcadlení z telefonu** (`PhoneBridging`, `BridgingManager`): vypnuté, dokud jsou hodinky
  přihlášené (jinak by každá zpráva přišla dvakrát), po odhlášení zase zapnuté.
- **Ověřeno na emulátoru 2026-10-24:** FCM token a registrace u TDLib, přepnutí zrcadlení po
  přihlášení, notifikace z TDLib s aplikací na pozadí (do 2 s).
- **Neověřeno: doručení pushu s ukončeným procesem.** Po `am kill` (ne `force-stop`, ten FCM
  zakáže) a zprávě od Notifikátora se FCM na emulátoru o doručení dvakrát ani nepokusil (150 s,
  v logu žádné `c2dm`). Může to být emulátor (spojení Google Play služeb bez účtu a bez
  spárovaného telefonu) i server (odklad pushe, když je účet aktivní jinde) — ověří až skutečné
  hodinky. Zrcadlení z telefonu na emulátoru bez spárovaného telefonu ověřit nejde.

## Emulátor

- AVD **`Plume_Wear`** (`wearos_large_round`, obraz `system-images;android-34;android-wear;arm64-v8a`,
  Wear OS 5), port 5600, spouštět s oknem — QR kód se skenuje z obrazovky.
- První start ukazuje systémové „Starting…" ještě po `sys.boot_completed`; aplikace je pod ním.
- Varování `CompositionLocals: SecurityException … reduce_motion … targetSdkVersion lower than
  or equal to: 34` hlásí Compose for Wear OS na Wear OS 5 s vyšším `targetSdk`; knihovna ho ošetří
  a jede dál.
