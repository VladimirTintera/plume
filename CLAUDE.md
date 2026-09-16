# CLAUDE.md

Material 3 Expressive klient pro Telegram. Kotlin Multiplatform, Android + iOS, sdílené Compose UI.

**Pracovní název je „Plume"**, balíček `eu.tintera.plume`. Podle [Telegram API ToS](https://core.telegram.org/api/terms)
nesmí název obsahovat „Telegram" bez předřazeného „Unofficial" a nesmí se použít oficiální logo.

## Stav

Fáze 6. Hotové: téma, přihlašovací automat, Navigation3 + adaptivní shell (rail/bar),
živý seznam chatů, textová konverzace (historie, živé zprávy, odesílání, označení přečteného),
avatary a fotky ve zprávách, reakce, odpovědi, notifikace na Androidu, sponzorované zprávy.

Není: přehrávání videa a zvuku, otevírání dokumentů, editace, notifikace na iOS,
perzistence nastavení. Prohlížeč médií umí zatím jen fotky. Rozpracovaný detail chatu má vlastní seznam:
[docs/roadmap-konverzace.md](docs/roadmap-konverzace.md).

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

## Pracovní postup

**Každá změna jde přes samostatnou větev a pull request, nic se necommituje rovnou do `main`.**

- Větev se zakládá z aktuálního `main` a pojmenovává podle obsahu (`fix/…`, `feat/…`,
  `docs/…`). Jedna větev = jedna ucelená změna.
- Do `main` se dostane jen sloučením PR. Slučuje uživatel, ne agent — ani automatické sloučení
  se nezapíná bez výslovného pokynu.
- Popis PR říká, CO se změnilo a PROČ, a jak to ověřit na zařízení. Když změna na zařízení
  vyzkoušená není, musí to v popisu stát.
- Poučení z opravy patří do tohohle souboru ve stejném PR jako oprava samotná.

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

## Přihlášení QR kódem

- **QR se generuje na platformě** (`encodeQrCode`): Android přes zxing, iOS přes CoreImage
  (`CIQRCodeGenerator`). Kodér v commonMain není. Platforma vrací jen mřížku modulů,
  kreslí se společně na `Canvas` — ostré v každé velikosti a nic se neškáluje z bitmapy.
- **Natvrdo černá na bílé, i v tmavém režimu.** Čtečky počítají s tmavými moduly na světlém
  podkladu; inverzní kód spousta z nich nepřečte. Stejná výjimka z rolí `colorScheme` jako
  bílé ovládání v prohlížeči médií.
- **Modul musí mít celé pixely.** Se zlomkovou velikostí nechá vyhlazování hran mezi sousedními
  moduly vlasové spáry; zbytek šířky se rozdělí do okrajů.
- **Odkaz se obnovuje.** TDLib posílá nový `AuthorizationStateWaitOtherDeviceConfirmation`
  zhruba každou půlminutu; kód se přepočítá přes `remember(content)`.

## Odhlášení

- **Po odhlášení je TDLib klient MRTVÝ.** `logOut` projde `LoggingOut` → `Closing` → `Closed`
  a wrapper pak ukončí všechny update flows klienta. Nový účet se na něm přihlásit nedá —
  musí vzniknout nová instance (`TelegramClientHolder.recreate()`), která začne od
  `WaitTdlibParameters` a sama dojde k zadání čísla. Dokud to tak nebylo, aplikace po odhlášení
  zůstala natrvalo stát na nápisu „Odhlášeno".
- **Klient se proto nikde nesmí zachytit do `val`.** Požadavky jdou přes `holder.client`
  (getter), odběry přes `holder.updates { … }`, které se na novou instanci přepnou samy
  (`flatMapLatest`). `client.fooUpdates.collect` na zachyceném klientovi po odhlášení tiše skončí.
- **Session se k novému klientovi přihlašuje `CoroutineStart.UNDISPATCHED`.** Odběr musí stát
  dřív než `getAuthorizationState` a nová instance nic nevydá, dokud nedostane první request —
  takže ztracený první stav by automat zastavil.
- **„Odhlášeno" není stav, jen přechod** (`AuthStage.LoggingOut` s indikátorem). Odhlášený
  uživatel je prostě na začátku přihlášení.
- **`fileId` platí jen v jedné databázi TDLib.** Nový účet čísluje soubory znovu, takže keše
  podle `fileId` (`TdlFileRepository`, `knownPaths` v `RemoteImage`) se po výměně klienta musí
  zahodit — jinak by se pod novými id ukázaly obrázky předchozího účtu. Totéž keš odesílatelů.
- **Úklid mimo aplikaci**: notifikace i zkratky konverzací (`NotificationUpdate.Cleared`). Zkratky
  jsou dlouhověké, takže `removeAllDynamicShortcuts` nestačí — je potřeba
  `removeLongLivedShortcuts`, jinak zůstanou chaty odhlášeného účtu v nabídce ikony aplikace.

## Navigace

Navigation3 (`rememberNavBackStack` + `NavDisplay` + `entryProvider`). Klíče jsou v
`core/navigation/Routes.kt` a jsou `@Serializable` — bez toho by se zásobník nepřežil zabití procesu.

Seznam chatů a konverzace tvoří **list-detail** dvojici přes `rememberListDetailSceneStrategy`:
`ChatsRoute` nese metadata `listPane(detailPlaceholder = …)`, `ConversationRoute` má `detailPane()`.
`directive` se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna — vlevo ukusuje
rail a scaffold by jinak rozhodl špatně.

**Spodní bar se v konverzaci skrývá — jen na compactu.** Na telefonu zabírá konverzace celou
obrazovku a sekční navigace tam nedává smysl; ven se chodí zpět, ne přepnutím sekce. Na širokém
rozvržení je konverzace jen panel vedle seznamu, takže rail zůstává. Past: `consumeWindowInsets`
spodního insetu musí být podmíněné stejnou podmínkou jako bar — když se konzumuje i se skrytým
barem, vstupní pole konverzace zaleze pod systémovou lištu.

**Skrytí baru musí animovat i jeho MÍSTO, ne jen posun.** `slideOutVertically` obsah jenom
posune — jeho slot v layoutu drží po celou animaci. Vstupní pole konverzace proto viselo nad
prázdným pruhem a doskočilo dolů až na konci. Řeší to `shrinkVertically` (a `expandVertically`
na druhou stranu) přidané k posunu: zmenšuje se i to místo, takže obsah roste současně
s odjezdem baru.

**Avatar a název letí ze seznamu do konverzace.** Klíče `chatAvatarKey` / `chatTitleKey`
obsahují id chatu, jinak by se při rychlém přepnutí spároval avatar jednoho chatu s druhým.
Na rozdíl od letu odeslané zprávy se tu viditelnost neřídí ručně — obě strany jsou
v `AnimatedContent` NavDisplaye, takže stačí `LocalNavAnimatedContentScope`.

**V dvoupanelu se nesdílí nic** (`LocalTwoPane`, poskytuje ho shell). Seznam vedle konverzace
zůstává vidět, takže by avatar odletěl z položky, která je pořád na obrazovce, a o jeden klíč
by se přetahovaly dva prvky naráz.

**Text se sdílí přes `sharedBounds` + `scaleToBounds`, ne přes `sharedElement`.** Název chatu má
v hlavičce jinou velikost písma; se `sharedElement` by se text na konci letu přeskládal skokem.

**Predictive back dělá `NavDisplay` sám** (přes `androidx.navigationevent`): při tažení zpět se
konverzace zmenší a odsune a pod ní se odkryje živý seznam. Nic se pro to nezapíná —
`android:enableOnBackInvokedCallback` systém při targetSdk 35+ ignoruje a je v manifestu jen
jako výslovné prohlášení.

**Past: obyčejný `BackHandler` predictive back zabije.** Kdo odchytí zpět bez
`PredictiveBackHandler`/`NavigationBackHandler`, dostane jen hotové gesto bez průběhu, takže
animace zmizí a nikde se neohlásí chybou. V aplikaci teď žádný `BackHandler` není a nemá
přibýt — ven z obrazovky se chodí přes `onBack` NavDisplaye.

**Známé zjednodušení fáze 1:** jeden zásobník pro všechny sekce; přepnutí sekce ho srovná na kořen,
takže sekce si nedrží vlastní historii. Pro Chaty/Nastavení to nevadí. Až přibude sekce s hlubší
navigací, patří sem multi-stack (vzor: `MultiStackNavigationState` v mlogu).

## Seznam chatů

Položky nejsou samostatné karty, ale **skupiny se společným zaoblením** — vzor, kterým M3
Expressive nahrazuje karty per položku. Skupiny jsou tři: připnuté, nepřečtené, ostatní.

- **Zaoblení nese krajní položka skupiny**: velké nahoře u první, velké dole u poslední, malé
  (4 dp) mezi nimi. S rozestupem 2 dp uvnitř skupiny a 14 dp mezi skupinami z toho vznikne blok.
- **Zaoblení se animuje.** Skupiny se přeskládávají za běhu — přečtený chat přeskočí
  z nepřečtených mezi ostatní — a tvary jeho sousedů se tím mění. Skoková změna by při
  současném `animateItem()` působila jako chyba vykreslení.
- **Prázdné skupiny se zahazují**, jinak by po nich zbyla mezera. Bez připnutých a bez
  nepřečtených je z celého seznamu zase jeden souvislý blok.
- **Pořadí uvnitř skupiny zůstává od TDLib.** Je to pořadí, na které je uživatel zvyklý
  z ostatních klientů; přeskládávat ho by znamenalo rozhodovat za něj.
- **Důsledek, se kterým se počítá:** přečtení chatu ho přesune ze skupiny nepřečtených dolů mezi
  ostatní. Při návratu z konverzace tedy chat nezůstane tam, kde na něj uživatel klikl.
- **Mezera mezi skupinami se přidává u první položky skupiny**, ne ve `verticalArrangement` —
  `LazyColumn` o sousedech položky nic neví. Stejný postup jako u rozestupů zpráv v konverzaci.

### Odlehčení: blok je světlejší než pozadí

- **Vrstvení je otočené proti tomu, jak vzniklo.** Původně bylo pozadí `surface` (nejsvětlejší role)
  a řádky `surfaceContainerHighest` o tři stupně tmavší — z položek byly šedé plochy na světlém
  a seznam působil těžce. Teď mají řádky `surfaceContainerLowest`, takže je to vztah „světlý panel
  na tónované stránce". Tím dělá odstup i Google Messages: jeho řádky samy žádnou výplň nemají,
  nese ho rozdíl panelu a pozadí.
- **V tmavém režimu se to obrátí a je to správně.** `surfaceContainerLowest` je tam nejtmavší role,
  takže z panelu bude tmavší plocha na světlejším pozadí. Role drží HIERARCHII, ne konkrétní
  světlost — proto se nesahá po natvrdo zadané barvě.
- **Název jde běžným řezem, polotučně jen nepřečtený.** Když je polotučný každý chat, nevyčnívá
  žádný. Proto `bodyLarge` + `FontWeight.SemiBold` jen podle `hasUnread`, ne `titleMedium` pro vše.
- **Odznak nepřečtených není chybově červený.** M3 `Badge` je ve výchozím stavu `error`; nepřečtená
  zpráva ale není chyba a v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce.

### Co položka ukazuje

Čas poslední zprávy, ztlumení, dostupnost protějšku a jeho právě probíhající činnost.

- **Čas se s věkem zjednodušuje** (`asRelativeTime`): dnes hodiny, včera slovem, do týdne zkratka
  dne, pak datum. V seznamu jde o STÁŘÍ zprávy, ne o pořadí během dne — „14:32" u zprávy z loňska
  nic neřekne. V konverzaci se naopak vypisují jen hodiny, protože tam jdou zprávy chronologicky
  za sebou. Tentýž formát používá i „naposledy viděn".
- **Dostupnost se musí jednou přečíst, ne jen odebírat.** `updateUserStatus` chodí jen na ZMĚNU,
  takže bez počátečního `getUser` by zelená tečka naskočila až ve chvíli, kdy se protějšek odpojí
  a zase připojí. V soukromém chatu je `chatId` shodné s `userId`, takže není co dohledávat.
- **Dostupnost a činnost PŘEŽÍVAJÍ přepsání chatu** (`keepingLiveState`). `Chat` od TDLib je
  nenese — chodí samostatnými updaty. Bez toho by každý `UpdateNewChat` nebo seed z databáze
  zhasl zelenou tečku i „píše…", protože obojí by se vrátilo na `null`.
- **„Píše…" se musí umět zhasnout samo.** TDLib činnost ukončuje samostatným `ChatActionCancel`,
  jenže kdyby se ten ztratil, zůstalo by „píše…" viset napořád. Každá činnost si proto nese
  odpočet, který nová zpráva o psaní posouvá — při souvislém psaní nikdy nedoběhne.
- **Vlastní činnost se zahazuje.** Porovnává se s id přihlášeného účtu, zjištěným jednou při
  přihlášení; v seznamu by vypadalo divně, že chat hlásí psaní, když píšeme my.
- **Činnost má přednost před náhledem zprávy** a odlišuje se barvou — je to novější informace
  a obojí najednou se do řádku nevejde.
- **Telegram dostupnost záměrně rozmazává.** Kdo si to v soukromí vypne, je vidět jen jako
  „naposledy nedávno" a přesný čas nedostaneme. Proto je `Presence` několik pojmenovaných stupňů,
  ne čas — dopočítávat minutu, kterou neznáme, by bylo horší než neurčitost.

### Akce nad chatem

Dlouhý stisk otevře bottom sheet: připnout, ztlumit, označit jako nepřečtené, smazat/opustit.

- **Smazat NENÍ `deleteChat`.** Ten maže obsah VŠEM členům — z položky v seznamu to nikdo nečeká.
  U soukromého chatu se volá `deleteChatHistory(removeFromChatList = true, revoke = false)`, tedy
  smazání jen u nás, u skupiny a kanálu `leaveChat`. Jsou to dvě různé věci, takže má každá
  vlastní popisek i vlastní text potvrzení; jeden společný by lhal.
- **Nevratná akce má potvrzení a pojmenovává následek**, ne „opravdu?". `ChatActionsState` proto
  drží `target` (sheet) a `pendingDelete` (dialog) zvlášť — mezi zavřením nabídky a potvrzením
  nesmí zůstat viset obojí.
- **Ztlumení se nastavuje celým objektem `ChatNotificationSettings`**, ne jedním polem. Ostatní
  hodnoty se musí přenést z aktuálního stavu chatu, jinak by se uživateli tiše přepsal třeba
  vlastní zvuk. `useDefaultMuteFor = false` je nutné: dokud je `true`, jede chat podle globálního
  nastavení a `muteFor` se ignoruje. Ztlumení „navždy" je `Int.MAX_VALUE` sekund.
- **Stav se po akci nepřepisuje lokálně.** U reakcí v konverzaci to smysl dávalo (mezi ťuknutím
  a potvrzením je vidět prodleva), tady se položka jen přeskládá — a předběhnout server by
  znamenalo riskovat, že se seznam rozejde se skutečností. Potvrzení chodí updaty
  (`chatNotificationSettingsUpdates`, `chatIsMarkedAsUnreadUpdates`, `chatPositionUpdates`).
- **Limit připnutých chatů je serverový** (`pinned_chat_count_max`, Premium ho zvedá). Při
  překročení vrátí TDLib chybu, která MUSÍ jít vidět — jinak to vypadá, že se nic nestalo.
  Proto má seznam chatů `SnackbarHost`.
- **`Card(onClick = …)` umí jen krátké ťuknutí.** Dlouhý stisk potřebuje `combinedClickable`,
  takže se z karty stal `Surface` s vlastním klikáním.

## Konverzace

Hranice je [ConversationRepository]; na rozdíl od [ChatRepository] je **bezstavová**. Stránkovací
stav drží `ConversationViewModel` — je tím testovatelný bez TDLib a přirozeně vázaný na životnost
obrazovky, ne na aplikaci.

- **Rozestup je dvojí: 4 dp uvnitř skupiny, 12 dp mezi skupinami.** Navazující zprávy téhož
  odesílatele patří k sobě, takže se drží u sebe — tvar bublin to už rozlišuje staženým nárožím
  (`continuesGroup`) a mezera tomu jen dává prostor. Mezera mezi skupinami se přidává v bublině,
  ne v `verticalArrangement`: seznam o sousedech položky nic neví.
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

### Předěly dne

V bublině se vypisují jen hodiny, protože datum má nést předěl nad blokem zpráv. Bez něj se
v delší historii nedalo poznat, kde končí včerejšek.

- **Předěl se kreslí v položce zprávy, ne mezi položkami.** `LazyColumn` o sousedech nic neví,
  takže se stejně jako u mezer mezi skupinami rozhoduje z dat: předěl patří zprávě, jejíž
  starší soused (index +1, seznam jde od nejnovější) je z jiného dne.
- **`reverseLayout` obrací pořadí POLOŽEK, ne obsah jedné položky.** Uvnitř `Column` proto předěl
  stojí před bublinou a vykreslí se nad ní.
- **U nejstarší načtené zprávy se předěl ukáže taky.** Jakmile se dotáhne starší stránka, posune
  se nahoru. Lepší než nechat vršek historie bez data.
- **Za předělem začíná nový blok**, i když píše tentýž člověk — proto se do bubliny posílá
  `previous = null`. Jinak by první zpráva pod datem navazovala staženým nárožím na zprávu
  z minulého dne a přišla by o jméno odesílatele.
- **Dny se porovnávají přes místní datum, ne přes rozdíl sekund.** Den má 24 hodin jen někdy —
  na přechodu letního času je kratší nebo delší a půlnoc se posouvá podle časového pásma.
- **Podrobnost data roste s věkem**, obráceně než u `asRelativeTime`. Předěl je nadpis nad celým
  blokem, takže musí říct dost: Dnes / Včera / název dne / „23. května" / s rokem u starších let.
  Měsíc je ve druhém pádu, protože stojí za dnem.
- **Plovoucí datum při scrollu není `stickyHeader`.** Ten se v obráceném seznamu lepí na jeho
  začátek, tedy dolů — přesně naopak, než je potřeba. Je to overlay nad seznamem, který se ukáže
  jen při scrollu a bere datum z nejvyšší viditelné položky (opět podle klíče, ne indexu).
- **Předěl a systémové oznámení vypadají stejně** (`CenteredNote`). Obojí je poznámka na okraji
  konverzace, ne zpráva od někoho; dvě různá pojetí téhož by seznam roztříštila.

### Skok na první nepřečtenou

Otevření chatu s nepřečtenými dotáhne historii až k hranici, skočí na první nepřečtenou zprávu
a nad ni nakreslí předěl „Nepřečtené zprávy".

- **Hranice se zachytí JEDNOU při otevření** (`lastReadInboxMessageId` z `getChat`) a už se nemění.
  Kdyby se posouvala podle toho, co se právě označilo za přečtené, předěl by uživateli utíkal pod
  rukama — a je to jediné místo, podle kterého pozná, kde přestal.
- **`unreadCount` je potřeba vedle ukazatele.** Z `lastReadInboxMessageId` samotného se nepřečtené
  odvodit nedají: v chatu, kde poslední zprávu napsal uživatel, je ukazatel starší než ta zpráva,
  a přesto není co číst.
- **Načítá se od nejnovější dozadu, ne od hranice dopředu.** TDLib by uměl i to (záporný `offset`
  v `getChatHistory`), ale kotva uprostřed znamená, že stránkování musí umět **oba směry** — dá se
  dojít na konec načteného i směrem k novějším. Za tu složitost to nestojí; pár stránek zpět je
  z lokální databáze rychlých.
- **Proto je tam strop na počet stránek.** Kdo má stovky nepřečtených, skok nedostane a chová se
  to jako dřív. Načítat kvůli tomu půlku historie by bylo horší než nepomoct.
- **Na zámek stránkování se čeká, nepřeskakuje se.** Dotažení kvůli skoku běží souběžně s prvním
  načtením při otevření chatu; kdyby se volání při probíhajícím načítání vzdalo, spotřebovalo by
  všechny pokusy naprázdno a skok by se nekonal.
- **`scrollToItem` v obráceném seznamu posadí položku ke SPODNÍ hraně**, protože tam je jeho
  začátek. U skoku na nepřečtené je to naopak, než je potřeba, takže se ještě dorovnává o výšku
  viewportu — a ta se **měří**, protože výška bubliny se zprávu od zprávy liší.
- **Viditelná plocha začíná NAD spodním odsazením seznamu** (`scrollToAnchor`). Dole je pod ním
  vstupní pole; dokud se horní hrana brala z celé výšky viewportu, zajela první nepřečtená
  o výšku pole pod horní okraj obrazovky.
- **Index v `LazyColumn` není index v `state.messages`.** Před zprávami stojí sponzorované
  položky, takže se k němu musí přičíst jejich počet. Stejná past jako u hlášení přečtení.
- **Předěl nepřečtených je akcentní, ne šedý jako datum.** Je to jediná věc, kterou uživatel po
  otevření chatu opravdu hledá.

### Připnuté zprávy

Pod hlavičkou visí připnuté zprávy: nejvýš tři nejnovější, zbytek za „Zobrazit všechny (N)".
Ťuknutí na řádek skočí na zprávu (stejně jako citace, i se zvýrazněním a dotažením historie),
křížek ji odepne.

- **Všechny připnuté přes `searchChatMessages` s `SearchMessagesFilterPinned`**, ne přes
  `getChatPinnedMessage` — ten vrací jen nejnovější. Stránkuje se po stovce, strop pět stránek.
  Vyhledávání potřebuje zapnutou databázi zpráv (`useMessageDatabase = true`).
- **Tři sbalené, ne jedna ani všechny.** Jedna schová, že připnutých je víc; všechny by z hlavičky
  udělaly půl obrazovky. Rozbalený seznam se listuje a má strop výšky.
- **Rozbalení je jen na chvíli**: sbalí se po skoku na zprávu a při pohybu konverzací. Leží přes
  zprávy a zavírat ho pokaždé ručně by otravovalo.
- **Právo odepínat se zjišťuje jednou** (`canBePinned` první připnuté zprávy). Je v chatu jedno
  pro všechny zprávy; dotaz na každou by byl zbytečný. Bez práva křížek není.
- **Odepnutá zpráva z pruhu zmizí hned**, potvrzení dorazí updatem. Při selhání se seznam přečte
  znovu, takže se zpráva vrátí.
- **Místo potvrzovacího dialogu „Vrátit" ve snackbaru.** Odepnutí je vratné; dialog před vratnou
  akcí jen zdržuje. Vrácení připíná bez upozornění členům — je to oprava omylu, ne oznámení.
  Událost se ve stavu nuluje hned při zobrazení, jinak by se snackbar po otočení ukázal znovu.
- **Na změnu se seznam ČTE ZNOVU**, neodvozuje se z updatu. `UpdateMessageIsPinned` nese jednu
  zprávu a „odepnout vše" jich pošle dávku; `conflate` z ní udělá jedno čtení. Totéž po smazání
  připnuté zprávy. Úprava připnuté zprávy se do pruhu propisuje z `editedMessages`.
- **Odběr stojí PŘED prvním čtením** (`onStart { emit(Unit) }`), aby se změna mezi nimi neztratila.
- **Pruh plave nad seznamem, není v `topBar`.** Seznam se odsazuje o jeho změřenou výšku (dole
  o padding `bottomBar`) — jinak by při odjezdu pruhu obsah na konci animace poskočil.
  O stejnou výšku se posouvá i plovoucí datum.
- **Při odjezdu se kreslí poslední seznam**, ne prázdný pruh (`AnimatedVisibility` dobíhá
  s obsahem, ale stav je už prázdný).
- **Připnout / Odepnout je i v nabídce nad zprávou**, podle `canBePinned` z `getMessageProperties`.
  Která z nich se nabídne, se bere ze seznamu připnutých ve stavu — `ChatMessage` příznak
  `isPinned` nenese a update o připnutí by ho stejně musel přemapovat v celé historii.
- **Připnutí má dialog s jednou volbou**, která mění, koho se dotkne: ve skupině a kanálu
  „upozornit členy/odběratele" (`disableNotification`), v soukromém chatu „uvidí i protějšek"
  (`onlyForSelf`). Upozornění už nikdo nevezme zpátky, proto se ptá dřív. V Uložených zprávách
  volba nemá smysl a připíná se rovnou. Soukromý chat připíná bez upozornění — protějšek uvidí
  servisní zprávu, oznámení navíc by byl šum.
- **Odepnutí z nabídky jde stejnou cestou jako křížek v pruhu**, včetně „Vrátit".
- **Past: jednorázová událost ve stavu a `LaunchedEffect` klíčovaný tou událostí.** Když efekt
  událost hned vynuluje, změní tím vlastní klíč, restartuje se a zruší i to, co právě spustil.
  Snackbar „Vrátit" se tak neukázal nikdy. Snackbar proto běží v `rememberCoroutineScope`,
  mimo tělo efektu.

### Skok na citaci a tlačítko dolů

Ťuknutí na citaci v bublině odroluje na citovanou zprávu a krátce ji zvýrazní. Tlačítko „dolů"
se ukáže i bez nových zpráv, jakmile je uživatel o kus výš.

- **Sdílí dotahování historie se skokem na nepřečtené** (`loadUntil`), jen s vyšším stropem.
  Tady jde o výslovné ťuknutí, na jehož výsledek se čeká, ne o pohodlí při otevření chatu.
- **Nenalezená zpráva má dvě různé hlášky.** Je-li načtená celá historie, zpráva byla smazaná;
  jinak je jen moc hluboko. Jedna společná by v jednom z případů lhala.
- **Citace míří na STŘED obrazovky, nepřečtené k horní hraně** (`ScrollAnchor`). U citace je
  potřeba vidět, na co zpráva navazuje; u nepřečtených se čte od první dolů.
- **Zvýraznění je nutné.** Po skoku doprostřed historie by jinak nebylo poznat, na kterou
  z bublin citace mířila. Je to pruh přes celou šířku, ne obarvení bubliny — odchozí `primary`
  by změnu nesla špatně a u příchozí by to vypadalo jako změna stavu zprávy.
- **Citace si opakuje dlouhý stisk.** Má vlastní klikání, takže by stisk k bublině nepustila
  a nad citací by nešla otevřít nabídka — stejná past jako u fotky.
- **Tlačítko dolů je kruh, s počtem pilulka.** Mezera před počtem je v obsahu, ne ve `spacedBy`:
  skrytý `AnimatedVisibility` je pořád potomek a rozestup by kruh nafoukl.
- **Ukáže se až o pár položek výš** (`SCROLL_DOWN_THRESHOLD_ITEMS`), ne při prvním pixelu —
  u spodní hrany by blikalo pod prstem.

### „Píše…" v konverzaci

Hlavička ukazuje činnost protějšku (ve skupině kohokoli) místo dostupnosti a protějšek naopak
vidí, že píšeme my.

- **Činnost se bere ze `ChatRepository`, ne z vlastního odběru.** Seznam chatů ji už sleduje
  i s odpočtem zhasnutí a odfiltrováním vlastní činnosti; druhá kopie téže logiky by se s ní
  rozešla. Důsledek: chat, který v hlavním seznamu není (archiv), „píše…" v hlavičce neukáže.
- **Činnost má v podtitulu přednost před dostupností** a výšku hlavičky animuje
  `AnimatedContent` — skupina podtitul jinak nemá a název by při „píše…" poskočil.
- **Vlastní „píšu" se opakuje nejvýš jednou za 5 s** (`TYPING_REPEAT`). Server činnost zhasíná
  asi po šesti vteřinách; posílat ji na každý úhoz by byl dotaz na znak.
- **Vymazání textu psaní výslovně ukončí** (`ChatActionCancel`), odeslání ne — to ukončí server
  sám. Po odeslání se jen zapomene čas posledního ohlášení, aby další zpráva ohlásila psaní hned.
- **Hlásí se jen ruční úpravy textu.** Vyprázdnění pole po odeslání jde mimo `onValueChange`,
  takže by se o něm `onDraftChanged` stejně nedozvěděl.
- **Chyba se jen loguje.** V kanálu bez práva psát `sendChatAction` selže; snackbar kvůli
  „píše…" by uživatele jen obtěžoval.

### Úprava zprávy

„Upravit" v nabídce nad zprávou přepne vstupní pole do úpravy: karta místo citace ukazuje původní
text, tlačítko je fajfka a potvrzení volá `editMessageText`.

- **Nenabízí se u zpráv s ručním formátováním** (`isEditableAsPlainText`). Pole umí jen holý
  text — tučné, kurzíva, spoiler nebo odkaz s vlastním popiskem by úprava tiše smazala.
  Automatické odkazy se poznají tak, že jejich text v adrese opravdu stojí; ty se při uložení
  dohledají znovu (`getTextEntities`, stejně jako při odeslání).
- **Nenabízí se ani u médií** — popisek fotky je jiné volání (`editMessageCaption`).
- **Rozepsaný koncept se po dobu úpravy odloží a pak vrátí.** Úprava je vsuvka; kdo si uprostřed
  psaní všimne překlepu, nechce přijít o rozepsanou zprávu. Při přepnutí z jedné úpravy do druhé
  se odložený koncept nepřepisuje.
- **Během úpravy se neukládá koncept ani „píše…"** (`onDraftChanged` se vrátí hned). V poli je
  text staré zprávy; koncept by přepsal ten odložený.
- **Z úpravy se odchází hned, ne po odpovědi serveru.** Výsledek dorazí `editedMessages` jako
  každá jiná úprava. Když selže, úprava se vrátí do pole i s textem.
- **Beze změny se nic neposílá** — server by odpověděl chybou.
- **Text do pole jde přes `InputFill`**, společně s obnoveným konceptem. Koncept má
  `onlyIfEmpty` (nesmí přepsat, co uživatel mezitím napsal), úprava pole přepisuje vždycky.
- **Po „Upravit" dostane pole fokus a klávesnici.** Nabídka se otevírá dlouhým stiskem na zprávě,
  takže pole fokus nemá a bez toho by nebylo poznat, že se něco stalo.

### Koncept (rozepsaná zpráva)

Rozepsaný text se ukládá do TDLib (`setChatDraftMessage`), takže přežije odchod z chatu, je vidět
v seznamu chatů („Koncept: …") a na ostatních zařízeních.

- **Ukládá se s odstupem 1,5 s od poslední úpravy**, ne na každý úhoz — koncept se propisuje na
  server. Při odchodu z chatu se čekání přeskočí v `onCleared`.
- **Ukládání běží v `appScope`, ne ve `viewModelScope`.** Ten se při odchodu z chatu zruší dřív,
  než by poslední úprava stihla odejít. Hodnoty se proto berou při plánování na hlavním vlákně,
  úloha je jen odešle.
- **Odeslání čekající uložení ZAHODÍ.** TDLib koncept při odeslání maže sám (`clearDraft`);
  opožděné uložení by ho vrátilo se starým textem.
- **Koncept se do pole vrací jen, když je prázdné** (`InputFill.onlyIfEmpty`). Než ho TDLib
  vydá, mohl uživatel začít psát. Po vložení se `inputFill` vynuluje — jinak by se po otočení
  vrátil i smazaný.
- **Pole drží `TextFieldValue`, ne `String`.** U vráceného konceptu je potřeba postavit kurzor
  na konec; `onDraftChanged` se volá jen při změně TEXTU, ne při pohybu kurzoru.
- **Citace je součást konceptu** a obnoví se s ním (i když není v načtené historii).
- **Prázdný text koncept maže, i s citací.** Odpověď bez textu by byl koncept, který v seznamu
  nic neukáže.
- **Seznam chatů musí z `UpdateChatDraftMessage` brát i POZICE.** Chat s čerstvým konceptem se
  posune nahoru a jinudy se to nedozvíme. Čas u položky je proto novější z konceptu a poslední
  zprávy — jinak by u chatu nahoře stálo starší datum než pod ním.
- **Koncept v otevřeném chatu se z updatů NEČTE.** TDLib upozorňuje, že
  `UpdateChatDraftMessage` může pro právě otevřený chat přijít se starým obsahem; přepsat jím
  pole by uživateli smazalo, co právě napsal.
- **Ukazuje se jen textový koncept.** Rozpracovaná hlasovka je taky koncept, ale „Koncept:" bez
  obsahu by nic neřekl.

### Hlášení přečtení a indexy

- **Z viditelných položek se bere KLÍČ, ne index.** Index do `state.messages` nesedí: před
  zprávami stojí sponzorované položky, takže v kanálu s reklamou byl výběr posunutý o jejich
  počet a za přečtené se hlásily zprávy, které uživatel neviděl. Klíč zpráv je rovnou jejich id.
  Platí to i do budoucna — jakmile do seznamu přibude cokoli dalšího (datové předěly, předěl
  nepřečtených), indexy se rozejdou znovu, klíče ne.
- **Efekt s odpočtem nesmí záviset na `state.messages`.** `LaunchedEffect(listState, state.messages)`
  se restartoval při každé nové zprávě a 400ms odpočet začínal od nuly, takže se v živém chatu
  nemusel dopočítat vůbec.
- **Vlastní zprávy filtruje ViewModel, ne obrazovka.** Ta zná jen id viditelných položek;
  dohledávat si k nim zprávy by znamenalo držet je kvůli tomu v kompozici.
- **V `LazyColumn` se nehledá průchodem seznamu.** `indexOf(message)` uvnitř `items` a hledání
  citované zprávy přes `firstOrNull` znamenaly kvadratické chování při scrollu dlouhou historií.
  Index dává `itemsIndexed`, citace se hledá v mapě složené jednou na změnu seznamu.

### Vzhled konverzace

- **Odchozí bublina je `primary`, ne `primaryContainer`.** Se dvěma světlými plochami o podobné
  světlosti nebylo při rychlém průletu očima poznat, kdo co napsal — rozlišovala je jen strana.
  Sytá `primary` proti světlé `surfaceContainerHigh` dělá rozdíl barvou.
- **Co je uvnitř bubliny, nesmí mít natvrdo `primary`.** Citace měla proužek i jméno v `primary`
  a na nové odchozí bublině by byly NEVIDITELNÉ — stejná barva na stejné barvě. Proto
  `QuotedMessage` bere `accent` parametrem a bublina jí podá svoji `contentColor`. Ze stejného
  důvodu má vybraná reakce `primaryContainer` místo `primary`.
- **Startovní strana letu musí mít barvu cílové bubliny.** Jinak zpráva po cestě do konverzace
  změní barvu.
- **Vstupní pole JE `bottomBar`, ale spodní padding jde do `contentPadding` seznamu, ne na
  kontejner.** Obsah Scaffoldu je přes celou plochu a Scaffold padding jen spočítá. Dřív se dával
  modifikátorem na kontejner, seznam tím před polem natvrdo skončil a přes obrazovku vedl
  předěl, o který se zprávy při scrollu „zasekávaly" — proto pole na čas z `bottomBar` odešlo
  a měřilo se ručně. Jako `bottomBar` ho ale měří Scaffold (i s citací, úpravou, víc řádky
  a klávesnicí) a podle něj sám staví snackbar a tlačítko dolů (FAB slot). Ruční měření na
  třech místech tím odpadlo a snackbar přestal pole překrývat.
- **Odesílací tlačítko je velikost medium (56 dp).** Je to přesně minimální výška jednořádkového
  `TextField`, takže se spodním zarovnáním řádku sedí na střed pole bez dopočítávaného odsazení
  a u víceřádkového textu zůstává u posledního řádku. Výchozí small (40 dp) sedělo o 8 dp níž
  než střed pole, large (96 dp) by bylo vyšší než pole samo.
- **`consumeWindowInsets` v obsahu snackbar ani FAB neposune.** Ty nejsou potomky obsahu —
  rozmisťuje je Scaffold nad FAB / `bottomBar` / spodní inset `contentWindowInsets`. Co má stát
  nad nimi, musí být ve slotu, jinak o sobě nevědí.
- **Scaffold konverzace nedává spodní inset do `padding`** (`contentWindowInsets` jen Top +
  Horizontal). Spodek si řeší pole samo přes `navigationBarsPadding` + `imePadding`, takže
  spodní padding je přesně výška `bottomBar`. `padding` ze Scaffoldu se v obsahu jen aplikuje
  a inset NEKONZUMUJE — se spodním insetem v `contentWindowInsets` se výška systémové lišty
  připočítávala dvakrát a pole viselo zbytečně vysoko, se zavřenou i otevřenou klávesnicí.
- **Citace a psaní jsou JEDNA karta.** Dva samostatné kusy nad sebou dělaly z odpovědi cizí prvek
  vsunutý mezi konverzaci a vstup; ve společné kartě je vidět, že citace patří k tomu, co se
  právě píše. Pole uvnitř je proto průhledné — kontejner nese karta, ne ono, a dvě zaoblené
  plochy v sobě by vypadaly jako chyba.
- **Pruh pod kartou má barvu POZADÍ, jen průsvitnou.** Zprávy pod něj zajíždějí, takže by se bez
  něj pod kartou míchaly s textem; plná barva by zase vrátila ostrý předěl přes celou obrazovku,
  kvůli kterému se `bottomBar` rušil. Průsvit dělá závoj: je vidět, že tam obsah je, ale nepřebíjí
  psaní.

### Formátování textu (entity)

Telegram **markup neposílá**. Text zprávy je holý řetězec a formát je seznam rozsahů nad ním
(`FormattedText.entities`). Kdo je zahodí, dostane neklikatelný odkaz a plochý text — přesně tak
to tu bylo, než vznikly `MessageEntity` a `RichText`.

- **Offsety jsou v UTF-16 jednotkách**, tedy přesně v tom, čím indexuje Kotlin `String`. Žádný
  přepočet. Nesmí se ale míchat s počítáním shluků emoji (`emojiClusterCount`), které jde
  po grafémech.
- **Text a entity vrací jedna funkce** (`captionBody()`), ne dvě. Rozsahy platí jen pro ten
  řetězec, ze kterého vznikly; dvě nezávislá `when` by se rozešla první přidanou větví a entity
  by ukazovaly do jiného textu, než který se vykreslí. Stejný důvod jako u `serviceText()`.
- **Popisům, které si vyrábíme sami, se entity nepřipojují.** U typů bez vlastního textu vrací
  `captionBody()` jen náš popis („Anketa: …") a prázdné entity — rozsahy z původní zprávy by
  v něm ukazovaly jinam, v lepším případě obarvily nesmysl, v horším spadly na indexu.
- **Rozsahy se před použitím ořezávají do délky textu.** Server posílá platné hodnoty, ale
  `addStyle` mimo rozsah je pád aplikace kvůli jedné zprávě.
- **Vnoření se řeší samo.** Text se do `AnnotatedString` připojí jednou a každá entita jen přidá
  svůj styl na svůj úsek — tučné uvnitř odkazu jsou prostě dva styly přes sebe, žádný strom.
- **Barva odkazu se musí dát předat.** V odchozí bublině je pozadí samo `primary`, takže natvrdo
  zadaná `primary` by odkaz zneviditelnila — tatáž past jako u `QuotedMessage.accent`. Bublina
  podává svoji `contentColor`, podtržení nese odlišení i tam, kde barva splývá.
- **Kliknutí jde přes ViewModel, ne rovnou z bubliny do `UrlOpener`.** Až budeme rozlišovat
  interní `t.me` odkazy, rozhodne se to na jednom místě a obrazovka se měnit nebude. Vlastní
  `linkInteractionListener` je nutný — bez něj si `LinkAnnotation.Url` otevře odkaz sám přes
  `LocalUriHandler` a nemáme kam sáhnout.
- **Dlouhý stisk nad odkazem hlídá `RichText` sám** (`onLongClick`). Compose dává každému odkazu
  vlastní `combinedClickable` bez `onLongClick`: ten dlouhý stisk k bublině nepustí a po puštění
  ho vyhodnotí jako ťuknutí, takže se místo nabídky otevíral prohlížeč. `RichText` proto
  v `PointerEventPass.Initial` (bez konzumace) pozoruje stisk nad odkazem, po `longPressTimeout`
  zavolá nabídku a následné „kliknutí" na odkaz zahodí. Mimo odkazy se nehlídá nic — dlouhý
  stisk tam dostane bublina a nabídka by se otevřela dvakrát.
- **Odkaz bez schématu dostane `https://`.** TDLib označí za odkaz i holé `example.com`; Android
  by z toho udělal relativní URI a intent by nikam nevedl.
- **Zmínky, hashtagy a příkazy botů se schválně nemapují.** Obarvit je umíme, ale kliknutí by
  nemělo kam vést — zmínka se hledá uvnitř aplikace (`searchPublicChat`), ne v prohlížeči.
  Modrý text, po kterém se nic nestane, je horší než text černý.
- **Spoiler musí být opravdu nečitelný**: průhledný text na plné ploše, ne jen jiná barva.
  Odkrývá se ťuknutím přes `LinkAnnotation.Clickable` — `Modifier.clickable` by platilo pro celou
  zprávu, ne pro skrytý úsek. Odkrývá se celá zpráva naráz; dohledávat spoilery po kouskách
  je otrava.
- **Při odesílání se entity dohledávají** (`getTextEntities`), jinak odkaz odejde jako mrtvý text —
  server si ho sám neoznačí. Když dohledání selže, zpráva stejně odejde.

### Profilovky ve skupině

Ve skupině má cizí zpráva vlevo tvář odesílatele, aby se odesílatelé nerozlišovali jen jménem.

- **Tvář je u POSLEDNÍ zprávy bloku**, ostatní zprávy bloku mají na jejím místě mezeru
  (`SenderAvatar.Spacer`). Bez mezery by bubliny téhož člověka skákaly do stran.
- **Poslední = novější soused je o index NÍŽ** (seznam jde od nejnovější). Blok končí jiným
  odesílatelem, jinou stranou, jiným dnem nebo servisním oznámením.
- **Kanál tvář nemá** (odesílatel = chat sám): u každého příspěvku by byla tatáž a nese ji
  hlavička.
- **Jméno i tvář jdou z jednoho `resolveSender`** (s cache), ne ze dvou dotazů.
- **Tvář je mimo posouvaný `Box`** — při tažení na odpověď jede jen bublina.
- **Zarovnaná ke spodní hraně bubliny.** U dlouhé zprávy by uprostřed nebylo poznat, kam patří.

### Náhled odkazu

Pod textem se zprávou s odkazem se kreslí náhled: web, titulek, popis a obrázek.

- **Náhled skládá server**, ne klient (`MessageText.linkPreview`). Nic se nestahuje kromě
  obrázku, který k němu server přiložil — žádné připojení k cizí stránce z aplikace.
- **Obrázek jen z druhů, které nesou fotku**: článek, fotka, obal videa, náhled vloženého
  přehrávače. Ostatní druhy (samolepka, hlasovka, chat…) mají soubory, které neumíme vykreslit;
  náhled zůstane textový. **Úplně prázdný náhled se zahodí** — rámeček bez obsahu vypadá jako
  chyba načítání.
- **Velký nebo malý obrázek určuje odesílatel** (`showLargeMedia`), jen se to respektuje.
- **Tvar vychází z citace** (proužek v barvě akcentu, bez vlastního pozadí). Akcent se podává
  zvenku ze stejného důvodu jako u citace — v odchozí `primary` bublině by `primary` zmizela.
- **Ťuknutí jde přes `onLinkClick`** jako odkaz v textu; **dlouhý stisk se opakuje**, jinak by
  náhled nabídku nad zprávou spolkl.
- **Náhled nad textem (`showAboveText`) se zatím ignoruje** — kreslí se vždycky pod ním.

### Nabídka nad zprávou

Dlouhý stisk otevře **popup u zprávy**, ne bottom sheet: rychlé reakce v pruhu nad bublinou
a nabídka akcí pod ní, zbytek obrazovky pod závojem. Sheet dole utrhl pozornost od zprávy,
které se týkal, a unesl jen pár položek — akcí bude postupně deset.

- **Popup, ne vlastní overlay v obsahu obrazovky.** `Popup` s `focusable = true` si tlačítko zpět
  obslouží sám přes `onDismissRequest`. Vlastní overlay by potřeboval `BackHandler`, a ten
  v téhle aplikaci zabíjí predictive back.
- **Deklaruje se UVNITŘ bubliny.** `calculatePosition` dostane `anchorBounds` toho prvku, pod
  kterým popup v kompozici visí, takže se nikam nemusí předávat souřadnice a nemají se jak
  rozejít. Před tím se sem plumboval `positionOnScreen()` z bubliny přes celou obrazovku.
- **Past: nesmí viset v OBSAHU bubliny.** Uvnitř `Column` s odsazením by ukotvením byla plocha
  zmenšená o to odsazení a díra v závoji by byla o kus menší než bublina. Proto je kolem
  `Surface` ještě `Box` a popup je v něm.
- **Závoj musí přes celou obrazovku, tedy `clippingEnabled = false`.** Systém jinak okno popupu
  vsadí dovnitř obsahu okna a u stavové i navigační lišty zbude světlý pruh — vypadá to jako
  nedotažené vsazení, protože to jím doslova je. S vypnutým ořezem ale popup o vsazeních nic neví,
  takže se **čtou v hlavním okně** (tedy v bublině, kde je nabídka deklarovaná) a předají se dovnitř
  jako okraje, do kterých se nabídka nesmí umístit. Závoj jde pod lišty, obsah ne.
- **Počátek popupu se MĚŘÍ, nepočítá.** Popup je na Androidu vlastní okno a systém ho posadí pod
  stavový řádek bez ohledu na požadovanou pozici; o kolik, ví jenom platforma. Bez toho seděla
  díra o výšku stavového řádku vedle. Měří se `positionOnScreen()` kořene popupu a anchor se o to
  posune.
- **Závoj má díru přesně na bublině** (`BlendMode.Clear`). Vybraná zpráva je kontext, ke kterému se
  nabídka vztahuje — ztmavit i ji by znamenalo ptát se na něco, co není vidět. Vykrojení nutně
  potřebuje `CompositingStrategy.Offscreen`: `Clear` maže z vrstvy a bez vlastní vrstvy by
  vymazalo i všechno pod popupem.
- **Tvar díry se bere ze samotné bubliny** (`Shape.createOutline` + `drawOutline`), nekreslí se
  podle konstanty. Navazující zpráva má jedno nároží stažené na 4 dp a jednotné zaoblení by u ní
  z díry vyčnívalo; konstanta by se navíc musela ručně držet v souladu s `BUBBLE_CORNER`.
- **Past: `Modifier.clip(shape)` musí být PŘED `combinedClickable`.** `Surface` si tvarem ořezává
  jen svoje pozadí, a to až za modifikátory, které dostane zvenku — vlnka dlouhého stisku se tedy
  kreslila do obdélníku a přetékala přes zaoblená nároží bubliny.
- **Výšky obou kusů jsou konstanty, ne měření.** Kreslíme si je sami, takže je známe předem —
  ušetří se tím snímek, ve kterém by nabídka poskočila na správné místo.
- **Nabídka se zarovnává k hraně BUBLINY, pruh reakcí ke kraji obrazovky.** Nabídka má pevnou
  šířku, takže může vyrůstat od zprávy; pruh se šesti emoji je tak široký, že by se k bublině
  stejně nevešel.
- **Příznaky „co se smí" se načítají až při otevření nabídky.** TDLib je ve zprávě neposílá,
  jsou za samostatným `getMessageProperties` — načítat je dopředu by znamenalo dotaz na každou
  zprávu v historii kvůli nabídce, kterou uživatel skoro nikdy neotevře. Načítají se **současně**
  s nabídkou emoji, ne sériově.
- **Co server nedovolí, se nenabízí.** Položka, která skončí chybou, je horší než položka, která
  tam není. Když se příznaky nepodaří zjistit, nabídne se odpověď a kopírování (neškodné),
  mazání ne.
- **Kopírování se řídí `canBeSaved`.** Kanál si může obsah chránit; pak se z něj nesmí vynášet
  ani text do schránky.
- **Schránka je vlastní `expect`/`actual`, ne Compose API.** `LocalClipboardManager` je zastaralý
  a jeho náhrada pracuje s `ClipEntry`, což je `expect class` bez společné továrny na text —
  v commonMain se prostě nedá vyrobit.
- **Prázdná nabídka emoji není chyba.** V Uložených zprávách Telegram místo reakcí používá tagy;
  dřív se kvůli tomu nabídka neotevřela vůbec a nešlo ani odpovědět. Teď se jen nevykreslí pruh.
- **Plný seznam emoji zůstal bottom sheetem.** Za „+" jich bývá přes třicet, takže se v tom
  listuje — a to popup neumí.
- **Smazaná zpráva se ze seznamu neodstraňuje ručně.** TDLib pošle `UpdateDeleteMessages` a ten se
  už zpracovává; předběhnout server by znamenalo riskovat, že zpráva zmizí z obrazovky a na
  serveru zůstane.

### Odpověď swipem

Tažení zprávy doprava otevře odpověď. Je to nejčastější akce nad zprávou a přes nabídku by na ni
vedly dva kroky.

- **`draggable` s vodorovnou orientací, ne `detectHorizontalDragGestures`.** Ten konzumuje od
  prvního pixelu a rozbil by svislý scroll seznamu. S `draggable` rozhodne o směru až první pohyb
  za slop, takže si `LazyColumn` svislé tahy nechá.
- **Táhne se doprava u OBOU stran konverzace.** Zrcadlit směr podle odesílatele by znamenalo,
  že uživatel musí před každým gestem přemýšlet, čí zpráva to je. Odchozí bublina se přitom kusem
  vysune za pravý okraj — tak to dělá Telegram i WhatsApp.
- **Odpověď odchází při PUŠTĚNÍ, ne při překročení prahu.** Kdo si to rozmyslí, vrátí prst zpátky
  a nic se nestane. Příznak `armed` hlídá, aby haptika i odpověď odešly jen jednou za tah.
- **Za prahem klade tah odpor** (30 %). Bez toho by šla bublina odtáhnout přes půl obrazovky
  a nebylo by poznat, že se dál už nic nestane.
- **Posun i průhlednost šipky se čtou až v layoutu a při kreslení** (`offset { }`,
  `graphicsLayer`), ne v kompozici. Jinak by každý snímek gesta znamenal přeskládání bubliny
  i s obsahem.
- **Šipka je uvnitř `Box`u kolem bubliny, posazená `offset`em mimo něj.** Do layoutu tím
  nezasahuje, takže nic neodsouvá a neovlivňuje šířku zprávy — a jede s bublinou, takže je
  jasné, čeho se gesto týká.
- **Vlevo od bubliny u obou stran**: tam vzniká mezera, když se táhne doprava.
- **U kraje obrazovky vyhrává systémové gesto zpět.** Bublina má odsazení 12 dp, takže se
  s ním u příchozích zpráv kousek překrývá. Nedá se s tím nic dělat a stejné to je i v Telegramu.

## Prohlížeč médií

Ťuknutí na fotku v bublině ji otevře přes celou obrazovku: štípnutí a dvojité ťuknutí přibližují,
tah dolů zavírá.

- **Vlastní routa `MediaRoute`, ne overlay v konverzaci.** Ven se tím chodí zpět včetně predictive
  back, přežije to otočení i zabití procesu a nepotřebuje to `BackHandler`, který by predictive
  back zabil. **Nezapomenout ji zaregistrovat v `NavSavedStateConfiguration`** — bez toho to spadne
  až za běhu.
- **Routa nese jen id**, ne obsah. Zprávu si prohlížeč načte znovu (`ConversationRepository.message`);
  celá zpráva i s reakcemi a citací do serializovaného zásobníku nepatří a dotaz navíc je vedle
  stahování fotky zanedbatelný.
- **Do prohlížeče jde jiná varianta souboru než do bubliny.** `MessageMedia.Photo.fullFileId` je
  největší, co server má; `fileId` (do 1280 px) slouží jako okamžitý náhled, protože ho konverzace
  už stáhla. V bublině by originál znamenal desítky megabajtů kvůli obrázku širokému pár set bodů.
- **Prohlížeč nemá metadata list-detail scény**, takže ho scéna nepřevezme a `NavDisplay` ho
  vykreslí přes celou plochu i na širokém rozvržení.
- **Gesta patří FOTCE, ne bublině.** Kdyby ťuknutí odchytávala bublina, sebralo by ho i odkazům
  v popisku. Dlouhý stisk se na fotce musí zopakovat: potomek s vlastním klikáním ho nepustí
  k rodiči, takže by nad fotkou nešla otevřít nabídka.
- **Listuje se `HorizontalPager`em, starší vlevo, novější vpravo.** Fotky se hledají
  `searchChatMessages` s `SearchMessagesFilterPhoto`, ne procházením historie — fotek je
  v chatu zlomek zpráv. Jedno volání vrátí obě strany: záporný `offset` přidá novější.
  Výchozí zpráva je ve výsledku taky, takže se při dotahování odfiltruje.
- **Pager drží stránku podle klíče** (id zprávy). Když se vlevo dotáhnou starší fotky, pod
  prstem se nic nepohne, jen se posune index — proto se stránkuje z `snapshotFlow` nad
  `currentPage`, které se po dotažení samo ozve znovu.
- **Počítadlo „N z M" se ptá jednou**, na otevřenou fotku (`getChatMessagePosition` +
  `getChatMessageCount`; TDLib počítá od nejnovější, tak se to obrací). Ostatní stránky se
  odvozují posunem indexu, protože načtený výřez je souvislý.
- **`detectTransformGestures` s pagerem nejde.** Konzumuje pohyb v každém směru, takže by fotka
  spolkla i vodorovný tah a pager by se nepohnul. Místo něj vlastní detektor, který po slopu
  rozhodne jednou za gesto: dva prsty nebo přiblížená fotka = štípnutí/posun (konzumuje),
  svislý tah = zavírání (konzumuje), vodorovný tah nepřiblížené fotky = listování (nekonzumuje,
  pustí ho pageru). Listovat se tedy dá jen se základní velikostí; posun po přiblížené fotce
  by se s listováním pral. Konec gesta zná detektor sám, takže odpadl samostatný pozorovatel
  puštění, který byl nutný kvůli `detectTransformGestures`.
- **Odlistovaná fotka se vrací do základní velikosti.** Soused zůstává složený
  (`beyondViewportPageCount`), a s ponechaným přiblížením by se z něj po návratu nedalo listovat.
- **Nahoře je přechod do tmava, ne pruh.** Bílé ovládání se na světlé fotce (screenshot) ztrácelo;
  s počítadlem to bylo vidět ještě víc. Plný pruh by fotku nahoře ořízl.
- **Přiblížení se počítá kolem prstů, ne kolem středu.** Bod pod prsty musí zůstat na místě,
  jinak fotka při štípnutí ujíždí a uživatel ji pak dohání posunem.
- **Meze posunu se počítají z poměru stran v metadatech**, ne z načteného souboru — jinak by se
  měnily ve chvíli, kdy dorazí ostrá verze.
- **Posun i přiblížení se čtou až v `graphicsLayer`**, takže gesto nic nerekomponuje; překresluje
  se jen vrstva s obrázkem. Totéž u zavíracího tahu (`drawBehind`).
- **Pozadí je tmavé v obou režimech** (role `scrim`). Světlá plocha kolem snímku mění jeho
  vnímanou expozici. Barva ovládání nad ním je proto natvrdo bílá — role z `colorScheme` by ve
  světlém tématu dala tmavý text na tmavé ploše.
- **Let z bubliny jede přes sdílený prvek** (`photoKey`), stejně jako avatar a název chatu.
  Na širokém rozvržení se nekoná: `LocalTwoPane` tam sdílení vypíná.

## Typy zpráv

TDLib má přes sto podtypů `MessageContent`. Vykreslit umíme hrstku; zbytek musí mít aspoň
pravdivý popis, protože jinak se uživateli tváří jako chyba načítání.

- **Nikdy nepoužívej jedno generické „Zpráva".** Tak to bylo a splývaly pod tím anketa, poloha,
  systémové oznámení i samotné emoji — a hlavně to vypadalo, že se zpráva nenačetla. Poslední
  záchrana je „Tenhle typ zprávy zatím neumíme zobrazit", což říká pravdu.
- **Servisní zprávy poznává `serviceText()`, která zároveň dává jejich text.** Jedno místo
  rozhoduje o textu i o způsobu vykreslení, takže se ty dva nemůžou rozejít. `null` znamená
  „tohle je běžný obsah".
- **Servisní zpráva se kreslí na střed, ne do bubliny.** Bublina patří odesílateli a tyhle
  odesílatele nemají — jsou to oznámení o dění v chatu. Čas se u nich nevypisuje, reakce ani stav
  doručení k nim nepatří.
- **V servisních zprávách se nevypisují jména.** TDLib dává jen id uživatelů a jméno by znamenalo
  dotaz navíc na každou takovou zprávu, často na někoho, kdo v chatu už dávno není. Bezejmenná
  formulace („Do skupiny přibyli členové") je vždycky pravdivá a nic nestojí.
- **`MessageUnsupported` znamená, že klient je starší než server.** Doslovný název typu by
  uživateli neřekl nic, takže dostane stejnou větu jako ostatní neznámé typy.
- **Když typ dostane přílohu s popiskem, `captionText()` pro něj musí vracet prázdno** — jinak
  se popisek v bublině objeví dvakrát. Platí pro video kolečko, hru a platbu.

## Emoji a samolepky

- **Zpráva složená jen z emoji se kreslí velká a bez bubliny**, stejně jako samolepka. Bublina
  kolem nich nemá co ohraničovat: samolepka má vlastní tvar s průhledností a velká emoji jsou
  obrázek samy o sobě. Barva popředí pak musí patřit PLOŠE, ne bublině, která tam není —
  `onPrimaryContainer` na průhledném pozadí je nečitelný.
- **Samotné emoji NENÍ textová zpráva.** Telegram ho posílá jako `MessageAnimatedEmoji`
  s animovanou podobou navíc, takže padalo do větve `else` a v bublině z něj bylo slovo „Zpráva".
- **Animované emoji s `.tgs` je příloha** (`MessageMedia.AnimatedEmoji`), kreslí se stejnou
  komponentou jako samolepka (`AnimatedSticker`) a bez bubliny. Text je u něj prázdný, jinak by pod
  animací stál ještě jednou jako znak. O textu i příloze rozhoduje jedna funkce
  (`MessageAnimatedEmoji.animationFileId`) — dvě nezávislá rozhodnutí by mohla nechat zprávu bez
  obojího, nebo s obojím.
- **Bez animace, nebo v jiném formátu, zůstává emoji textem** (`emoji`, obyčejný unicode znak):
  vykreslí se sám a chytí se na něj vykreslení velkých emoji. Prémiová emoji můžou mít animaci
  v libovolném formátu.
- **Než se animace načte, drží místo tentýž znak velkým písmem** v pevném čtverci 112 dp. Přechod
  je výměna obrázku za obrázek a bublina se při ní nepřeskládá.
- **Kopírování animovaného emoji kopíruje jeho znak** (`copyableText`) — text je prázdný, ale
  dřív, jako textová zpráva, to šlo.
- **Kostka (`MessageDice`) padala do téhož `else`.** Hozená hodnota je součástí animace, takže se
  dopisuje číslem (`🎲 6`) — jinak by ze zprávy zbyl hod bez výsledku. Nula znamená, že server
  výsledek ještě neposlal.
- **Strop jsou 3 emoji** (`MAX_LARGE_EMOJI`). Nad ním už nejde o gesto, ale o odstavec, a ve
  velké sazbě by zabral půl obrazovky. Míň emoji = větší: `displayLarge` / `Medium` / `Small`.
- **Odpověď si bublinu drží**, i když je jen z emoji — citace bez pozadí se ztratí.
- **Detekce emoji je pragmatický výběr rozsahů, ne unicode tabulka.** Kotlin v commonMain nemá
  ani `codePointAt`, ani `\p{...}` v regulárních výrazech, takže úplná data by znamenala vlastní
  tabulku a její údržbu při každé revizi Unicode. Chyba je na obě strany levná: nepoznané emoji
  se vykreslí normálně velké, symbol omylem považovaný za emoji se ukáže velký.
- **Počítají se shluky, ne kódové body.** Rodina (👨‍👩‍👧) je pět kódových bodů spojených ZWJ
  a vlajka dva regionální indikátory — pro uživatele je to jedno emoji. ASCII se za emoji nepovažuje,
  takže „:-P" zůstává normální zpráva (a klávesnicová emoji `1️⃣` se tím minou).
- **Samolepky: Coil umí jen `.webp`.** `.tgs` je gzipovaná Lottie animace, `.webm` video — ani
  jedno není obrázek. U nich se bere statický náhled, který TDLib k samolepce dává, a teprve když
  není ani ten, emoji. Náhled jen v rastrovém formátu: `ThumbnailFormatTgs` a `Mpeg4` mají tentýž
  problém, jen o úroveň níž.
- **`.tgs` přehrává Compottie** (`AnimatedSticker`). Gzip Compottie neumí, takže se soubor
  rozbalí přes Okio (`gzip()` funguje v commonMain, protože Okio má zlib pro JVM i native) a do
  Compottie jde až JSON. Do té doby i při chybě se kreslí statický náhled — odpovídá prvnímu
  snímku, takže přechod na animaci není skok z prázdna.
- **Compottie je `lite`.** Lite nemá JavaScriptový engine pro výrazy; samolepky Telegramu výrazy
  nepoužívají (oficiální rlottie je ani neumí). Compottie táhne Compose `foundation` tranzitivně,
  takže novější verze než náš Compose by ho tiše povýšila — před bumpem zkontrolovat její metadata.
- **Rozparsované animace mají vlastní cache (50), ne `LocalLottieCache` (10 na celou aplikaci).**
  V konverzaci se jedna samolepka opakuje a při scrollu by se jinak rozbalovala a parsovala
  znovu. Klíčem je cesta, ne MD5 obsahu jako u `JsonString` — hash rozbaleného JSONu by stál víc,
  než cache ušetří. Kvůli cestě jako klíči se cache po odhlášení zahazuje (`forgetStickerAnimations`).
- **Mimo obrazovku se animace zastavovat nemusí.** Položka `LazyColumn` mimo viewport vypadne
  z kompozice i s hodinami animace.
- **`.webm` samolepky zůstávají statické.** Je to VP9 s průhledností: hardwarový dekodér na
  Androidu alfu zahodí a iOS WebM neumí vůbec, takže by to znamenalo přibalit libvpx.
- **`RemoteImage` má parametr `background`.** Výchozí podložka `surfaceContainerHighest` je správně
  u fotky, ale u samolepky by z průhledného tvaru udělala obrázek v rámečku.
- **`captionText()` vrací u samolepky prázdno.** Její emoji se dřív vypisovalo jako text zprávy;
  vedle vykreslené samolepky je to zdvojení. V náhledu seznamu chatů a v citaci ale zůstává —
  tam není kam kreslit obrázek.
- **Emoji v čipu reakce je záměrně mimo typografickou škálu** (18 sp natvrdo). Role jako
  `labelLarge` míří na výšku písmen, ale u emoji je nosná celá kresba, takže ve stejné velikosti
  působí menší než číslo vedle ní.

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
- **`MainActivity` je `singleTop` a v `onNewIntent` volá `setIntent`.** Bez `singleTop` by se
  activity skládala znovu a ztratila stav obrazovek; bez `setIntent` by `getIntent()` dál vracelo
  ten intent, kterým se activity zakládala.
- **Extra se po vyřízení z intentu maže.** Po otočení displeje se `onCreate` spustí znovu nad týmž
  intentem a chat by se otevřel podruhé, i kdyby se uživatel mezitím proklikal jinam.
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
- **Klíč položky v `LazyColumn` proto NENÍ id zprávy**, ale `ConversationState.keyOf()`: u odeslané
  zprávy zůstává dočasné id (`itemKeys`: nové id → dočasné). S id jako klíčem bylo potvrzení
  doručení pro seznam odebrání jedné položky a přidání jiné — `animateItem()` jednu nechal
  odprchnout, druhou naskočit a bublina problikla. Let ze vstupního pole navíc přišel o cíl,
  protože potvrzení dorazí většinou dřív, než let doběhne. Kdo z viditelných položek čte klíč
  (hlášení přečtení, plovoucí datum), musí počítat s tím, že to není id.
- **Rozepsaná zpráva dorazí přes `newMessages` DŘÍV, než `sendMessage` vrátí výsledek.** Let se
  proto na ni váže už tam (`landingOn`, podle textu a stavu `Sending`). Když se vázal až
  v `send`, bublina se na snímek vykreslila na místě, pak zmizela do letu a přiletěla znovu.
- **Nová zpráva dole se ukotví přes `requestScrollToItem(0)` v kompozici**, ne efektem
  s `animateScrollToItem`. `LazyColumn` drží pozici podle klíče první viditelné položky, takže
  vložená zpráva skončila pod spodní hranou a seznam k ní dojel až o snímek později.
- **Cíl letu nemá fade-in `animateItem`.** Během letu se kreslí v překryvu, kam průhlednost
  položky nedosáhne; po přistání by skočil z plné viditelnosti do rozjetého prolnutí.
- **Ikona doručení NENÍ `Crossfade`.** Ten prolíná obě ikony naráz, takže v půlce přechodu jsou
  napůl průhledné přes sebe; fajfka a dvojitá fajfka se kryjí a jejich společná část na okamžik
  zeslábla. Teď `AnimatedContent`: nová ikona naskočí zvětšením, stará jen rychle zmizí, obojí
  přes `MotionScheme`. Barva a popis se počítají ze stavu UVNITŘ obsahu — zvenku by odcházející
  hodiny zčervenaly dřív, než zmizí.
- **Čipy reakcí jsou v `key(emoji)`.** Server pořadí reakcí přeskládává a bez klíče se stav
  animací (barva, tvar) přenesl na sousední čip.
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
- **`RemoteImage` si pamatuje poslední známou cestu** (`knownPaths`). Odběr souboru startuje vždycky
  na `Pending` a odpovídá až z korutiny, takže každý nově složený `RemoteImage` proletí aspoň jedním
  snímkem bez obrázku. U sdíleného prvku ze seznamu do konverzace to bylo vidět: avatar odletěl jako
  prázdný kruh a obrázek naskočil až na místě. Mapa se čte i zapisuje jen z kompozice, takže
  nepotřebuje zámek.
- **`FileRepository` je eager** (`createdAtStart`) ze stejného důvodu jako seznam chatů — v
  konstruktoru se přihlašuje k `fileUpdates`.

## Material 3 / vzhled (UI)

Drž se **Material 3 Expressive guidelines a výchozích hodnot komponent**. Nečitelné změti barev
a splývajících ploch vznikají skoro vždy z ohýbání standardu, ne z jeho dodržování.

- **Verze Compose se berou z mlogu** (stejný Kotlin i AGP, ověřená kombinace): Compose, material3
  alpha, adaptive a lifecycle se zvedají spolu. Od Compose 1.12 se okno čte přes
  `currentWindowAdaptiveInfoV2` (zná navíc třídy Large a XL — na `widthClass` to nic nemění, obě
  leží nad Expanded) a stav modálního sheetu přes `rememberBottomSheetState(SheetValue.Hidden)`.
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
- **`height(IntrinsicSize.Min)` nesmí obalit nic se `SubcomposeLayout`** (`BoxWithConstraints`,
  lazy seznamy). Intrinsické míry neumí a aplikace spadne až při vykreslení — u náhledu odkazu
  s velkým obrázkem to shodilo každý chat, kde takový náhled byl vidět. Proužek „přes celou výšku"
  se proto kreslí `drawBehind` za obsahem, ne jako `Box` s `fillMaxHeight`.
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
- **`Info.plist` musí mít `CADisableMinimumFrameDurationOnPhone = true`.** Compose Multiplatform
  to kontroluje při startu a bez klíče aplikace na iOS hned spadne na `IllegalStateException`
  (jinak by na 120Hz displejích běžela na 60 snímků). Chyba je vidět jen v konzoli, na simulátoru
  se aplikace prostě zavře.
- **Release link iOS frameworku trvá desítky minut.** Kotlin/Native pouští LTO nad celou statickou
  TDLib; naměřeno 15+ minut na M-series. Debug link je ~5 minut. Do CI to patří jako samostatný,
  cachovaný krok — ne do smyčky, kterou čeká vývojář.

## Co ještě není vyřešené

- `databaseEncryptionKey` je prázdný. Před ostrým nasazením tam patří klíč z Android Keystore /
  iOS Keychain.
- Push notifikace (TDLib `registerDevice` + iOS notification service extension).
- **Detail chatu má vlastní rozpracovaný seznam: [docs/roadmap-konverzace.md](docs/roadmap-konverzace.md).**
  Jsou tam čtyři velké kusy (entity a odkazy v textu, popup nad zprávou místo bottom sheetu,
  odpověď swipem, prohlížeč médií) i zásobník menších nálezů. Je to živý dokument — co se udělá,
  odtud zmizí a poučení z toho patří sem.
