# CLAUDE.md

Material 3 Expressive klient pro Telegram. Kotlin Multiplatform, Android + iOS, sdílené Compose UI.

**Pracovní název je „Plume"**, balíček `eu.tintera.plume`. Podle [Telegram API ToS](https://core.telegram.org/api/terms)
nesmí název obsahovat „Telegram" bez předřazeného „Unofficial" a nesmí se použít oficiální logo.

## Stav

Fáze 2. Hotové: téma, přihlašovací automat, Navigation3 + adaptivní shell (rail/bar),
živý seznam chatů, textová konverzace (historie, živé zprávy, odesílání, označení přečteného).

Není: vykreslování médií (zatím jen popisek), reply, reakce, editace, skok na první nepřečtenou,
push notifikace, sponsored messages, perzistence nastavení.

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

`core/telegram/TelegramSession.kt` je **jediné** rozhraní, přes které zbytek aplikace mluví
s Telegramem. `core/telegram/TdlTelegramSession.kt` je **jediný soubor, který smí importovat
`dev.g000sha256.tdl.*`**.

Proč to tak striktně:

1. **Upstream TDLib mění API i v patch verzích.** Wrapper to dokumentuje explicitně. Když se to stane,
   přepíše se jeden soubor.
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
