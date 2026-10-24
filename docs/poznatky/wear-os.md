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
- **Odhlášení je v Nastavení hodinek** (`SettingsScreen`, cesta `settings`): `CompactButton`
  „Nastavení" hned pod nadpisem seznamu chatů, na obrazovce „Odhlásit" a `AlertDialog` z Compose
  for Wear OS s křížkem a fajfkou. **Menu v liště Wear OS nemá** — vedlejší akce patří na začátek
  nebo konec seznamu. Napřed bylo odhlášení na konci seznamu chatů, a ten je dlouhý: uživatele by
  nenapadlo tam rolovat. Bez toho by hodinky šly odhlásit jen z telefonu. Odhlásí
  jen sezení hodinek (`TelegramSession.logOut` v `APP_SCOPE` — obrazovka po odhlášení zmizí);
  automat pak sám přejde k QR kódu, notifikace smaže `NotificationUpdate.Cleared`, dlaždice
  a komplikace dostanou stav odhlášeno a zrcadlení z telefonu se zase zapne.
  **Ověřené jen po dialog a zrušení** — emulátor je na skutečném účtu a znovu ho přihlásí jen
  uživatel QR kódem; samotné odhlášení stojí na `logOut` z `core` jako na telefonu.

## Seznam chatů

- `ChatRepository.chats()` jako na telefonu; repository seznam po přihlášení načte sama.
  Karta: fotka chatu a název, náhled poslední zprávy (`UiText.resolve()`), v rohu počet
  nepřečtených, a když nic nečeká, čas poslední zprávy (dnes hodina, jinak datum).
- **Fotka chatu** (`ChatAvatar`): malá varianta (`avatarFileId`), stažení spustí odběr
  `FileRepository.file()` sám; dekóduje se mimo hlavní vlákno rovnou zmenšená (`inSampleSize`
  mocninou dvou) na velikost avataru — hodinky mají málo paměti. Bez fotky iniciála.
- **Uložené zprávy svým jménem a se záložkou** (`savedMessagesChatId` z `ChatRepository`,
  `chatTitle` a `ChatAvatar(savedMessages = true)` v `SavedMessages.kt`): v TDLib nesou jméno
  a fotku vlastního účtu a hodinky je tak ukazovaly — jako chat se sebou samým (nahlásil
  uživatel z Pixel Watch). Platí v seznamu, v hlavičce konverzace i na dlaždici s chaty, kde je
  záložka prostředek Androidu (`ic_saved_messages`) v `onTileResourcesRequest` a fotka účtu se
  pro ně nestahuje. Ověřeno na emulátoru.
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

## Fotky

- **Fotka v bublině** (`MessagePhoto`): přes šířku bubliny v poměru stran fotky, popisek pod ní;
  slovo „Fotka" se pak nekreslí. Hned minináhled (`Minithumbnail`, malý JPEG přímo ve zprávě),
  po stažení varianta pro bublinu (`fileId`) dekódovaná zmenšená na šířku (`decodeScaled`,
  `inSampleSize` mocninou dvou). **Plná varianta (`fullFileId`) se na hodinkách nestahuje** —
  displej má pár set pixelů a šly by megabajty.
- **Vysoká fotka se zastropuje na 1,25 × šířky a zúží se s ní**, neořízne se (CLAUDE.md, „Výšku
  obrázku nepočítej `aspectRatio`").
- **Ťuknutí na fotku ji otevře přes celý displej** (`PhotoScreen`, cesta `photo/{fileId}`, zpět
  tahem), zbytek bubliny dál otevírá výběr reakce. Tatáž varianta jako v bublině, jen dekódovaná
  na velikost displeje — po bublině bývá stažená.
- **Spoiler, citlivý obsah, tajný chat a mizející fotka zůstávají slovem** (`shownPhoto()`):
  odkrývání ani časovač hodinky nemají a ukázat je rovnou by obešlo, co chtěl odesílatel nebo
  server.
- **Ověřeno na emulátoru 2026-10-24** v chatu Notifikátora (fotky poslané botem přes `sendPhoto`,
  vygenerované přechody bez lidí): široká s popiskem, vysoká zastropovaná a zúžená, náhled přes
  celý displej a zpět. Video, samolepky a GIFy zůstávají slovem.

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
  (na hodinkách „Open on watch") otevře rovnou konverzaci: `MainActivity.EXTRA_CHAT_ID` jako
  u dlaždice s chaty, request code podle skupiny, aby se intenty chatů neslily. Ověřeno na
  emulátoru s aplikací na pozadí i po `am kill` (notifikace zůstala, aktivita se obnovila
  a konverzaci otevřela). Název konverzace jen ve skupině — v soukromém chatu je odesílatel totéž co
  název a titulek zněl „Notifikator: Notifikator".
- **Akce Odpovědět a Přečteno** (`WearNotificationReceiver`, broadcast, `goAsync`) podle poučení
  telefonu (sekce „Akce z notifikace" v `notifikace.md`): `PendingIntent` odpovědi MUTABLE
  (systém do něj vkládá text), vlastní řádek do notifikace hned, **přečtení až po potvrzení od
  serveru** a po nejnovější zprávu NOTIFIKACE (ne chatu), potvrzení dotazem na zprávu
  (přečíslovaná dočasná = odesláno), notifikaci stáhne TDLib, vlastní řádky ji naživu nedrží.
  Mrtvý proces probudí akce jako push a čeká se na `AuthStage.Ready`. Presenter je v Koinu jeden
  na proces, aby do něj přijímač mohl připsat odpověď. Telefon má na pomalé potvrzení úlohu
  WorkManageru, hodinky ne: co se nestihne do ~9 s (`goAsync`), zůstane odeslané, ale nepřečtené.
- **Wear OS akci odpovědi vykreslí přímo v notifikaci** jako emoji / hlas / klávesnici;
  **rychlé odpovědi (`setChoices`) se tam na emulátoru neukázaly** — v konverzaci (vlastní
  `RemoteInput` aktivita) ano. Zadání emoji vloží znak do pole a odesílá se šipkou.
- **O notifikace se Android 13+ ptá za běhu** — hodinky jednou, hned po přihlášení.
- **Zrcadlení z telefonu** (`PhoneBridging`, `BridgingManager`): vypnuté, dokud jsou hodinky
  přihlášené (jinak by každá zpráva přišla dvakrát), po odhlášení zase zapnuté.
- **Ověřeno na emulátoru 2026-10-24:** FCM token a registrace u TDLib, přepnutí zrcadlení po
  přihlášení, notifikace z TDLib s aplikací na pozadí (do 2 s); odpověď „👍" z notifikace
  (v chatu se objevila, notifikace po potvrzení zmizela) a Přečteno (v seznamu chatů místo
  počtu nepřečtených čas).
- **Neověřeno: doručení pushu s ukončeným procesem.** Po `am kill` (ne `force-stop`, ten FCM
  zakáže) a zprávě od Notifikátora se FCM na emulátoru o doručení dvakrát ani nepokusil (150 s,
  v logu žádné `c2dm`). Může to být emulátor (spojení Google Play služeb bez účtu a bez
  spárovaného telefonu) i server (odklad pushe, když je účet aktivní jinde) — ověří až skutečné
  hodinky. Zrcadlení z telefonu na emulátoru bez spárovaného telefonu ověřit nejde.

## Dlaždice a komplikace

- **Dlaždice** (`UnreadTileService`): název aplikace, uprostřed velký počet nepřečtených chatů
  s popiskem z plurálu, dole `textEdgeButton` „Otevřít". Není to Compose: systém ji kreslí sám
  z popisu rozvržení (ProtoLayout, `protolayout-material3`), `TileService` vrací
  `ListenableFuture`, korutinu do ní převede `SuspendToFutureAdapter` (concurrent-futures).
- **Komplikace** (`UnreadComplicationService`, `SuspendingComplicationDataSourceService`): jen `SHORT_TEXT` — ikona
  „mark_chat_unread" (prostředek Androidu, ne composeResources: `Icon.createWithResource`) a číslo,
  ťuknutí otevře aplikaci. Bez přihlášení pomlčka, ikona zůstává.
- **Počet se drží v souboru** (`UnreadState`, SharedPreferences `wear_unread`): systém se na
  dlaždici i komplikaci ptá kdykoli, i když proces neběží, a TDLib se za tu chvíli zeptat nedá.
  Zapisuje ho `UnreadCountPublisher` (od startu procesu) a pak požádá o obnovu
  (`TileService.getUpdater`, `ComplicationDataSourceUpdateRequester`) — **pravidelná obnova
  žádná** (`UPDATE_PERIOD_SECONDS` 0, bez `freshnessInterval`).
- **Počítá TDLib** (`mainUnread.unmutedChats`): nepřečtené chaty hlavního seznamu bez
  ztlumených, jako záhlaví widgetu na telefonu. Součet nad seznamem chatů by vynechal chaty,
  ke kterým se stránkování ještě nedostalo.
- **Zapisuje se jen v jasných stavech** (přihlášeno a seznam načtený, nebo odhlášeno) a po vteřině
  klidu: při studeném startu by jinak dlaždice problikla nulou, než TDLib počet ohlásí.
- **Otevřená konverzace zprávy rovnou čte** (viz Konverzace), takže zpráva do chatu, který zůstal
  otevřený pod ciferníkem, počet nezvedne. Při zkoušení se proto vrací do seznamu chatů.
- **Na emulátoru se dlaždice přidá ladicím příkazem**: `am broadcast -a
  com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component
  eu.tintera.plume/eu.tintera.plume.wear.UnreadTileService` (`remove-tile` ji odebere), pak tah
  doleva z ciferníku. Komplikace `--es operation set-complication … --ecn watchface <ciferník>
  --ei slot 0 --ei type 3`; výchozí ciferník Orbita má políčka vypnutá („Complication slot is not
  enabled"), jde to na AnalogClassic (`set-watchface`).
- **Jazyk aplikace potřebuje `localeConfig`** (`android:localeConfig` v manifestu, soubor
  vyrábí plugin `plume.locale-config` stejně jako u telefonu). Bez něj `set-app-locales` nezabral
  ani v aplikaci samotné, přestože ho systém uložil. **Projeví se až v novém procesu**: běžící
  aplikace zůstala ve starém jazyce i po novém vytvoření aktivity, po `am kill` mluvila česky.
  Dlaždice si texty čte při každém kreslení, po změně jazyka se ale překreslí až s další změnou
  počtu.
- Náhled dlaždice ve výběru (`androidx.wear.tiles.PREVIEW`) zatím není — obrázek by musel mít
  i českou variantu.
- **Čeština ověřená na emulátoru 2026-10-24** přes jazyk aplikace: seznam „Chaty", dlaždice
  „0 nepřečtených chatů" / „1 nepřečtený chat", „Otevřít".
- **Ověřeno na emulátoru 2026-10-24** (anglicky): dlaždice s počtem, zpráva od Notifikátora ho
  zvedla během pár vteřin (dlaždice i komplikace), přečtení v aplikaci snížilo, „Otevřít" i ťuknutí
  na komplikaci otevřely aplikaci, po `am kill` se dlaždice nakreslila z nově spuštěného procesu.
  Neověřené: stav bez přihlášení (vyžaduje odhlášení), skutečné hodinky.

## Dlaždice s chaty

- **`RecentChatsTileService`**: nadpis „Chaty", pod ním chaty ze začátku hlavního seznamu jako
  `avatarButton` (fotka, název, pod ním „N nových zpráv", a když nic nečeká, náhled poslední
  zprávy), dole `textEdgeButton` „Všechny chaty". Začátek seznamu = připnuté a pak podle poslední
  zprávy, tedy totéž co nahoře v aplikaci.
- **Dva řádky, tři až od výšky 260 dp.** Na emulátoru (227 dp) se třetí s tlačítkem dole nevešel.
- **Podklady v souboru** (`RecentChatsState`, SharedPreferences `wear_recent_chats`, JSON), zapisuje
  `RecentChatsPublisher` jako u počtu nepřečtených: jen v jasných stavech, po vteřině klidu,
  a jen když se změní, co dlaždice ukazuje (id, název, počet, náhled, fotka) — seznam se mění
  s každou zprávou kdekoli. Náhled je přeložený při zápisu a bez zalomení řádků (jeden řádek).
- **Fotky jsou miniatury 96 px připravené při zápisu** (`filesDir/tile/avatars/<fileId>.png`,
  čtverec ze středu, ořez do kruhu dělá `avatarImage`); na stažení se čeká nejvýš 5 s, pak
  iniciála na `primaryContainer`. Nepoužívané se po zápisu smažou.
- **Fotky dodává `onTileResourcesRequest`, ne automatická registrace** (`Material3TileService`,
  `avatarImage(ImageResource)` s `ProtoLayoutScope`). S rendererem **1.300** (emulátor Wear OS 5)
  prostředky ze scope do balíčku nedorazily: „Resource avatar-… is not defined in resources
  bundle" v logu systému, místo fotky prázdno a knihovna sama nezalogovala nic. Klasická cesta:
  verze prostředků = otisk jmen miniatur, obrázky jako `InlineImageResource` s komprimovaným PNG
  (formát neurčený). Verzi rendereru ukáže `requestParams.deviceConfiguration.rendererSchemaVersion`.
- **Sloupec tlačítek musí mít šířku `expand`.** `avatarButton` je `expand` a sloupec s výchozím
  `wrap` renderer zahodil celý („Column set to wrap but contents are unmeasurable") — dlaždice
  měla jen nadpis a tlačítko.
- **Ťuknutí na chat otevře konverzaci** (`LaunchAction` s `AndroidLongExtra`
  `MainActivity.EXTRA_CHAT_ID`), nad seznamem chatů, takže tah zpět vede do seznamu. **Aktivita je
  `singleTop`**: když aplikace běží, systém ji jen přivede dopředu a bez `onNewIntent` by extra
  zůstalo nepřečtené. Po obnově procesu (`savedInstanceState`) se nenaviguje podruhé.
- **Ověřeno na emulátoru 2026-10-24** (anglicky): dlaždice s Notifikátorem (iniciála, „1 new
  message") a kanálem s fotkou, obnova po zprávě od bota, ťuknutí otevřelo konverzaci Notifikátora
  ze studeného startu i s aplikací na pozadí (jedna instance aktivity), tah zpět do seznamu.
  Neověřené: stav bez přihlášení, chat s nestaženou fotkou, skutečné hodinky.

## Úložiště

- **Automatický úklid** (`WearStorageCleaner`): po přihlášení, nejvýš jednou za 24 h, smaže
  média, na která se nesáhlo 3 dny, a pak nejdéle nepoužitá nad 100 MB —
  `StorageRepository.cleanUp` z `core` (`optimizeStorage` s mezemi vypsanými výslovně, ne
  `use_storage_optimizer`; proč, viz `StorageCleaner` telefonu). Samolepky, avatary a náhledy
  nechá být. **Pevné meze, ne volby**: hodinky nastavení úložiště nemají.
- **Ruční vyčištění v Nastavení** („Vyčistit mezipaměť", pod ním „Uvolněno N") se stejným
  výběrem jako výchozí dialog telefonu (`DEFAULT_CLEAR_CATEGORIES`: média ano, samolepky
  a avatary ne). Bez potvrzení — soubory zůstávají na serveru a stáhnou se znovu. Úklid a ruční
  vyčištění hlídá jeden `Mutex` (souběžné `optimizeStorage` TDLib ruší), proto je čistič v Koinu
  jeden na proces.
- **Ověřeno na emulátoru 2026-10-24**: po startu „Úklid úložiště hodinek uvolnil 0 B" (vše
  mladší než 3 dny, pod stropem), ruční vyčištění „Freed 204 kB" (`files` 23 → 22 MB), fotka
  v konverzaci Notifikátora se pak stáhla znovu.

## Hlášení pádů

- **Crashlytics jako telefon** (`release.md`, „Hlášení pádů"): `configureLogging()` z `core`
  na začátku `WearApplication.onCreate`, před `startKoin`; plugin `crashlytics` za
  `google-services`, sběr jen v release (placeholder `crashlyticsCollectionEnabled`
  v manifestu). Release build nahrává R8 mapping soubor (`uploadCrashlyticsMappingFileRelease`).
- **Ověřeno na emulátoru 2026-10-24** release buildem přepodepsaným debug klíčem: `adb shell am
  crash eu.tintera.plume` → „Handling uncaught exception … shell-induced crash" a „Crashlytics
  report successfully enqueued to DataTransport". Podrobný log Crashlytics zapne `adb shell
  setprop log.tag.FirebaseCrashlytics DEBUG`. Testovací pád má `versionCode` 100000999 (9.9.9).

## Vydání

Postup a pasti jsou v `release.md`, „Hodinky (Wear OS)": track `wear:internal` z tagu Androidu,
`versionCode` + 100 000 000, poznámky jen s `units: wear`, minifikovaný release.

## Emulátor

- AVD **`Plume_Wear`** (`wearos_large_round`, obraz `system-images;android-34;android-wear;arm64-v8a`,
  Wear OS 5), port 5600, spouštět s oknem — QR kód se skenuje z obrazovky.
- První start ukazuje systémové „Starting…" ještě po `sys.boot_completed`; aplikace je pod ním.
- Varování `CompositionLocals: SecurityException … reduce_motion … targetSdkVersion lower than
  or equal to: 34` hlásí Compose for Wear OS na Wear OS 5 s vyšším `targetSdk`; knihovna ho ošetří
  a jede dál.
