# CLAUDE.md

Material 3 Expressive klient pro Telegram. Kotlin Multiplatform, Android + iOS, sdílené Compose UI.

**Pracovní název je „Plume"**, balíček `eu.tintera.plume`. Podle [Telegram API ToS](https://core.telegram.org/api/terms)
nesmí název obsahovat „Telegram" bez předřazeného „Unofficial" a nesmí se použít oficiální logo.

## Stav

Fáze 6. Hotové: téma, přihlašovací automat, Navigation3 + adaptivní shell (rail/bar),
živý seznam chatů, textová konverzace (historie, živé zprávy, odesílání, označení přečteného),
avatary a fotky ve zprávách, reakce, odpovědi, notifikace na Androidu, sponzorované zprávy.

Není: přehrávání videa a zvuku, otevírání dokumentů, editace, skok na první nepřečtenou,
notifikace na iOS, perzistence nastavení.

## Build

```bash
./gradlew :client:androidApp:assembleDebug          # Android APK
./gradlew :client:shared:linkDebugFrameworkIosSimulatorArm64   # ověření iOS linku
open iosApp/iosApp.xcodeproj                         # iOS se staví z Xcode
```

Před prvním spuštěním doplň do `local.properties` (gitignorovaný):

```
telegramApiId=<z my.telegram.org>
telegramApiHash=<z my.telegram.org>
```

Bez nich se projekt přeloží, ale přihlášení skončí hláškou `AuthStage.Failed` s vysvětlením.

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

### Bump TDLib wrapperu

Verze Kotlinu v projektu **musí být ≥ verze, ze které je wrapper publikovaný** — jinak se jeho iOS klib
nenačte. Před bumpem zkontroluj:

```bash
curl -s https://repo1.maven.org/maven2/dev/g000sha256/tdl-coroutines-iosarm64/<ver>/tdl-coroutines-iosarm64-<ver>.module | grep -A2 kotlin-stdlib
```

## Navigace

Navigation3 (`rememberNavBackStack` + `NavDisplay` + `entryProvider`). Klíče jsou v
`core/navigation/Routes.kt` a jsou `@Serializable` — bez toho by se zásobník nepřežil zabití procesu.

Seznam chatů a konverzace tvoří **list-detail** dvojici přes `rememberListDetailSceneStrategy`:
`ChatsRoute` nese metadata `listPane(detailPlaceholder = …)`, `ConversationRoute` má `detailPane()`.
`directive` se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna — vlevo ukusuje
rail a scaffold by jinak rozhodl špatně.

**Známé zjednodušení fáze 1:** jeden zásobník pro všechny sekce; přepnutí sekce ho srovná na kořen,
takže sekce si nedrží vlastní historii. Pro Chaty/Nastavení to nevadí. Až přibude sekce s hlubší
navigací, patří sem multi-stack (vzor: `MultiStackNavigationState` v mlogu).

## Konverzace

Hranice je [ConversationRepository]; na rozdíl od [ChatRepository] je **bezstavová**. Stránkovací
stav drží `ConversationViewModel` — je tím testovatelný bez TDLib a přirozeně vázaný na životnost
obrazovky, ne na aplikaci.

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
- **Avatary místo počtu jen pod stropem.** U reakce s pár lidmi je zajímavé, KDO reagoval; nad
  stropem se čip sbalí na emoji + číslo. Dvě nezávislé podmínky:
  `MAX_AVATARS_PER_REACTION` (kolik lidí u jedné reakce) a `MAX_REACTIONS_WITH_AVATARS` (kolik
  různých emoji u zprávy) — jinak by pod krátkou zprávou vznikla stěna z profilovek.
  **`MAX_SENDERS_SHOWN` v repository musí sedět s tím prvním stropem**, protože nad ním se
  odesílatelé vůbec nenačítají a UI by chtělo avatary, které nikdo nezjistil.
- **`recentSenderIds` není úplný seznam.** TDLib posílá jen pár posledních; celkový počet je
  v `totalCount`. Podle délky seznamu se tedy počet odvodit nedá.

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

- **Notifikační API TDLib je ve výchozím stavu VYPNUTÉ.** `notification_group_count_max` je 0,
  což znamená, že `UpdateNotificationGroup` vůbec nevzniká. Musí se zapnout přes `setOption`
  (rozsah 1–25) — viz `enableNotifications()`. Bez toho je celá notifikační vrstva mrtvá
  a **nedá se to poznat**: nepřijde chyba, prostě se nic neděje.
- **TDLib vydává `UpdateNotificationGroup` i bez push služby**, kdykoli proces běží a je připojený.
  Push je jen doprava pro případ, kdy aplikace neběží — zobrazení je v obou případech totéž.
- **Co se zobrazí, rozhoduje TDLib**, ne klient: ztlumené chaty i `showPreview` řeší on. Proto se
  v `TdlNotificationRepository` nic nefiltruje.
- **Id systémové notifikace = TDLib `notificationId`.** TDLib posílá zrušení podle něj
  (`removedNotificationIds`); s vlastním klíčem by notifikace po přečtení jinde zůstaly viset.
- **`google-services` plugin se aplikuje podmíněně**, jen když existuje `google-services.json` —
  jinak by se repozitář po klonu nepřeložil. Bez Firebase appka funguje, jen bez pushe.
- **Push payload je zašifrovaný.** Rozšifrovat ho umí jedině TDLib přes `processPushNotification`;
  služba nic nevykresluje, jen předává.
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
- **Ikona notifikace je zatím systémová** (`android.R.drawable.ic_dialog_email`). Cílově sem
  patří vlastní monochromatická ikona v `androidApp/res/drawable`.
- **`adb shell am force-stop` znemožní doručení FCM.** Aplikace se dostane do „stopped state"
  a zprávy jí systém nedoručí, dokud ji uživatel ručně nespustí. Push se tak testovat nedá —
  aplikaci je potřeba jen odswipovat z přehledu úloh.

## Odesílání a scrollování

- **Odeslaná zpráva se do seznamu přidává OKAMŽITĚ.** `sendMessage` vrací zprávu hned, s dočasným
  id a stavem `Sending`; čekat na server by znamenalo viditelnou prodlevu mezi ťuknutím a bublinou.
- **TDLib po potvrzení zprávě ZMĚNÍ id.** `UpdateMessageSendSucceeded` nese staré i nové —
  zprávu nestačí aktualizovat, původní se musí ze seznamu odebrat, jinak tam zůstane dvakrát.
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
- **`FileRepository` je eager** (`createdAtStart`) ze stejného důvodu jako seznam chatů — v
  konstruktoru se přihlašuje k `fileUpdates`.

## Material 3 / vzhled (UI)

Drž se **Material 3 Expressive guidelines a výchozích hodnot komponent**. Nečitelné změti barev
a splývajících ploch vznikají skoro vždy z ohýbání standardu, ne z jeho dodržování.

- **`material3` musí zůstat na ALPHA kanálu.** Ve stable artefaktu jsou API pod
  `@ExperimentalMaterial3ExpressiveApi` vyříznutá — `MaterialExpressiveTheme`, `MotionScheme`,
  `LoadingIndicator`, `WideNavigationRail`, `ButtonGroup`, `FloatingActionButtonMenu`. Bez alphy
  expressive vrstva neexistuje.
- **Preferuj defaulty.** Neoverriduj `containerColor`/elevation, dokud k tomu není konkrétní důvod.
- **Vrstvení = surface container role, ne `tonalElevation`.** `surface` < `surfaceContainerLow/…` <
  `surfaceContainerHigh` (overlay) < `surfaceContainerHighest` (menu nad overlayem). `tonalElevation`
  u `surfaceContainer*` barev nic nedělá — M3 `Surface` tónuje jen `color == colorScheme.surface`.
- Barvy vždy z `MaterialTheme.colorScheme`, nikdy natvrdo hex.
- **Animace nechávej na `MotionScheme`** (`AppMotion` v `app/root/presentation/theme/Motion.kt`).
  Spatial specy mají jemný bounce (dampingRatio 0.6–0.78), effects specy bounce nemají — overshoot
  u alphy/barvy se ořízne do [0,1] a působí jako bliknutí.
- **`SharedTransitionLayout` je v kořenovém tématu**, ne uvnitř obrazovek — sdílený prvek musí přežít
  výměnu obrazovky. Scope se bere z `LocalSharedTransitionScope`.
- **Ikony = Material Symbols** jako vector drawables v `composeResources`, s vyplněnou i nevyplněnou
  variantou (vybraná položka navigace má vyplněnou). Viz `TopLevelDestination`.
- **Obsah shellu jde přes `movableContentOf`.** Při přepnutí breakpointu rail↔bar se podstrom
  PŘESUNE, ne přerenderuje — jinak by se odmountoval a ztratil scroll i stav.

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
- **`ChatPosition.order == 0` znamená, že chat ve složce NENÍ.** Musí ze seznamu zmizet, jinak tam
  zůstane viset po archivaci.
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
  `height(...)` s `coerceAtMost`.
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
  zprávami, a neprojeví se to chybou, jen špatným obsahem.
- **Release link iOS frameworku trvá desítky minut.** Kotlin/Native pouští LTO nad celou statickou
  TDLib; naměřeno 15+ minut na M-series. Debug link je ~5 minut. Do CI to patří jako samostatný,
  cachovaný krok — ne do smyčky, kterou čeká vývojář.

## Co ještě není vyřešené

- `databaseEncryptionKey` je prázdný. Před ostrým nasazením tam patří klíč z Android Keystore /
  iOS Keychain.
- Sponsored messages v kanálech jsou podle ToS povinné a zatím se nezobrazují.
- Push notifikace (TDLib `registerDevice` + iOS notification service extension).
