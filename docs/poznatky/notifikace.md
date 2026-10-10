# Notifikace

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

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

### Tiché hodiny

Nastavení → Notifikace → Tiché hodiny (`QuietHours`, `AppSettings.quietHours`, testy
`QuietHoursTest`): v nastaveném úseku zazvoní jen kanál **Zmínky a odpovědi**, ostatní notifikace
přijdou potichu (`setSilent`). Výchozí úsek je 22:00–7:00, vypnuto.

- **Potichu, ne vůbec.** Notifikace na liště zůstane, ráno je vidět, co přišlo. Zahodit ji by
  znamenalo ztratit zprávu, kterou TDLib už za doručenou považuje — znovu ji nevydá.
- **Rozhoduje KANÁL notifikace, ne druh chatu.** Zmínka ve skupině jde kanálem `mentions`
  (`NotificationGroupTypeMentions`: zmínky, odpovědi na mě, připnutá zpráva) a ta má zvonit;
  zpráva v soukromém chatu jde kanálem `private` a v tichých hodinách je tichá. Jestli TDLib
  dává do skupiny zmínek i odpověď v soukromém chatu, ověřené NENÍ — podle dokumentace skupiny
  spíš ne, takže v soukromém chatu je tichá i odpověď.
- **Hovory se neztišují** — ohlášení hovoru (`showCall`) jde mimo `add` a vlastním kanálem.
  Kdo volá v noci, nejspíš to myslí vážně, a přijmout hovor Plume stejně neumí.
- **Čas je místní čas zařízení** v okamžiku zobrazení, minuty od půlnoci. Úsek přes půlnoc je
  běžný; začátek == konec je prázdný úsek, ne celý den.
- **Jen Android** (`NotificationPermission.supportsQuietHours`). Na iOS notifikace z APNs kreslí
  systém; obdobou je režim Soustředění. Skupina se tam v Nastavení neukazuje.
- **Jen v zařízení.** Ostatní zařízení o tichých hodinách nevědí — notifikace kreslí aplikace
  sama. Kandidát na synchronizaci přes CloudStorage (viz `docs/poznatky/nastaveni.md`).
- **Totéž ztišení platí pro odložený chat**, dokud spí (viz „Odložení chatu" v `seznam-chatu.md`).
- **Ověření:** `adb logcat -b events | grep notification_alert` — u tiché notifikace jsou vibrace
  i zvuk 0 a `dumpsys notification` ukáže `flags=…|SILENT`. Ověřeno na emulátoru (Fold, česky):
  úsek 14:00–7:00 ve 14:20, soukromá zpráva od testovacího bota přišla potichu, po vypnutí
  zazvonila. **Zmínka v tichých hodinách ověřená NENÍ**: v testovací skupině je téma General
  zavřené (`TOPIC_CLOSED`) a bot nemá právo spravovat témata (`CHAT_ADMIN_REQUIRED`).
  Rozhodování o kanálu zmínek je ale staré a ověřené (viz „Kanály a nastavení notifikací").

### Bubliny konverzací (Android 11+)

Notifikace konverzace nese `BubbleMetadata`, takže ji jde poslat do bubliny (tlačítko v rozbalené
notifikaci, nebo volba u aplikace / konverzace v systému). V bublině běží `BubbleActivity`
(`:client:androidApp`) s `BubbleShell`: jedna konverzace a nad ní prohlížeč médií.

- **Vlastní aktivita, ne `MainActivity`.** Ta je `singleTask` a otevřela by celou aplikaci.
  Bublina chce vložitelnou aktivitu (`allowEmbedded`, `resizeableActivity`) a dokument pro
  každý chat (`documentLaunchMode="always"`). Míří se na ni AKCÍ a balíčkem
  (`NotificationIntents.bubble`), stejně jako na příjemce odpovědi: `:client:shared` třídy
  vstupního bodu nezná.
- **`PendingIntent` bubliny musí být `FLAG_MUTABLE`** (Android 12+) a data nesou chatId
  (`plume-bubble://chat/<id>`): dva chaty musí mít různý intent podle `filterEquals`.
- **Vlastní malý zásobník, ne `AppShell`.** Konverzace má přes dvacet napojení na shell
  a navigační singletony (`ChatOpenRequests`, `KeyboardShortcuts`) jsou na proces — druhý shell
  by si s hlavní aktivitou přebíral požadavky. V bublině jsou zprávy, psaní, přílohy a média;
  profil, přeposlání, jiný chat, mini aplikace a další otevřou plnou aplikaci (`openInApp`).
  `NavDisplay` tam je kvůli sdíleným přechodům (`LocalNavAnimatedContentScope` mimo něj není).
- **Kořen aplikace je společný** (`AppRoot` v `App.kt`): motiv, přihlášení a **zámek aplikace
  platí i v bublině** — jinak by bublina PIN obešla.
- **Notifikaci, ze které je bublina, se NESMÍ zrušit** — se zrušenou notifikací systém zruší
  i bublinu (`BubbleData.doRemove … reason=NOTIF_CANCEL`). A rušila se hned: otevřená bublina
  zprávy přečte, TDLib notifikaci stáhne a prázdná skupina se ruší. Presenter proto u notifikace
  s `FLAG_BUBBLE` místo zrušení přepíše notifikaci s `setSuppressNotification(true)`
  (`keepBubble`, z aktivní notifikace přes `recoverBuilder`, takže i po restartu procesu):
  z lišty zmizí, bublina zůstane.
- **Šipka zpět bublinu SBALÍ** (`moveTaskToBack`), ne `finish()` — to ukončí úlohu a systém
  bublinu odebere (`reason=TASK_FINISHED`).
- **Výchozí stav aplikace je „bubliny nepovolené"** (`mAllowBubble=false` v `dumpsys
  notification`); povolí je uživatel. Aplikace nic nevynucuje a nerozbaluje se sama.
- **Ověřeno na emulátoru (Fold, Android 17)**: tlačítko bubliny v notifikaci od testovacího
  bota, rozbalení s konverzací, přečtení bez zmizení bubliny, sbalení šipkou a nové rozbalení,
  profil z hlavičky otevřel plnou aplikaci na téže konverzaci. Logy: `adb logcat | grep
  'Bubbles '`. Dál ověřeno: fotka od bota se v bublině otevřela v prohlížeči, „Odpovědět"
  z prohlížeče vrátil konverzaci s citací a odpověď odešla (bot ji dostal jako odpověď na
  fotku); se zapnutým zámkem aplikace (po minutě nečinnosti) se bublina otevřela na zadání PINu
  a obsah konverzace pod ním nebyl ani ve výpisu přístupnosti, po PINu se ukázal.
- **Se zapnutým zámkem jsou snímky obrazovky ČERNÉ** — `SecureScreen` zapne `FLAG_SECURE`
  i v bublině. Co je na obrazovce, se pak ověřuje výpisem `uiautomator dump`, ne snímkem.
  Testovací zámek po ověření vypnout (Nastavení → Obecné → Zámek aplikace → Vypnout zámek),
  jinak zůstane emulátor zamčený na PIN, který nikdo nezná.

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
