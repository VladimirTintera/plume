# Konverzace: zobrazení zpráv

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Skladba obrazovky

`ConversationScreen` jen drží stav sdílený mezi částmi a skládá je do Scaffoldu. Každá část má
vlastní soubor v `features/conversation/`:

| Část | Soubor | Co v ní je |
|---|---|---|
| Hlavička | `ConversationTopBar.kt` | výběr, hledání, nebo název s avatarem, podtitulem a akcemi |
| Pruhy nad seznamem | `ConversationBars.kt` | spam a oznámení o chatu, reklama u bota, žádosti o vstup, videochat, připnuté, dock polohy, přehrávač |
| Obsah | `ConversationContent.kt` | seznam zpráv, nebo stav místo něj; plovoucí datum |
| Spodní lišta | `ConversationBottomBar.kt` | vstupní pole s napovídáním, nebo lišta místo něj (Přidat se, Start, ztlumení…) |
| Vstupní pole | `MessageInput.kt` | karta pole, tlačítko odeslání a nahrávání, hlavičky citace a úpravy |
| FAB slot | `ConversationFloatingButtons.kt` | odznaky zmínek a reakcí, „přečíst", tlačítko dolů |
| Dialogy a sheety | `ConversationDialogs.kt` | výběry, plánování, sheety otevírané stavem ViewModelu; `ConversationSheets` |
| Efekty seznamu | `ConversationListEffects.kt` | snackbary, stránkování, skoky na zprávu, nové zprávy dole, hlášení přečtení, reklam a metrik zobrazení |
| Navigace | `ConversationNavigation.kt` | `ConversationNavigation` (callbacky do shellu) a `ConversationEvents` (jednorázové události) |
| Nahrávání | `ConversationRecording.kt` | stav hlasovky a kolečka, gesto, oprávnění, kamera |

- **Nová logika patří do své části, ne do `ConversationScreen`.** Funkce byla v dexu na hraně
  registrů (viz „Obrazovka konverzace je rozdělená do částí" v CLAUDE.md).
- **Co patří jedné části, drží si ta část sama**: stav pruhů a sheet z pruhu proti spamu, výběr
  příloh, emoji ke slovu, stav spojení. V obrazovce zůstává jen to, co čte víc částí:
  `ConversationSheets` (co je otevřené nad konverzací), `ConversationRecording`, `ComposerDraft`,
  stav seznamu a změřené výšky pruhů a pole.
- **`state` předaný jako hodnota je snímek z doby skládání.** Efekt, který žije přes víc změn
  stavu (`snapshotFlow` u hlášení přečtení a reklam), musí číst `stateHolder`. V obrazovce to
  nebylo vidět, protože tam je `state` delegát (`by stateHolder`) a čte se vždy aktuální.
- **Rychle se měnící hodnoty se předávají jako lambda nebo `State`, ne jako hodnota**: klíč
  nejvyšší viditelné zprávy, odkrytí časů, vysunutí docku. Přečtené při volání by skládaly
  volajícího (viz `topVisibleKey` v `docs/poznatky/vykon.md`).
- **Funkce, která vrací hodnotu, se nepřeskakuje** a běží na místě volání. Na tom stojí
  `rememberNewMessages`: `requestScrollToItem` v kompozici musí proběhnout v tomtéž průchodu
  jako dřív v těle obrazovky.
- **`ConversationNavigation` je `data class`.** Volající ji skládá při každé rekompozici znovu;
  bez `equals` by se lišila referencí a konverzace by se skládala s ním. Lambdy volajícího si
  pamatuje strong skipping, takže dvě po sobě složené instance jsou si rovné.

## Skladba ViewModelu

`ConversationViewModel` sám drží jen jádro: stav a jeho plnění z TDLib (`observeUpdates`),
stránkování, skoky na zprávu, hlášení přečtení, připnuté zprávy, reklamy a rozcestník akcí
z bubliny (`onAction`). Ostatní dělají části v `features/conversation/`:

| Část | Co dělá |
|---|---|
| `ConversationComposer` | koncept, „píše…", odpověď a citace, úprava, náhled odkazu, efekty, odeslání (`sendMedia`) |
| `ConversationAttachments` | samolepky, GIFy, poloha, kontakt, přílohy nad polem a jejich odeslání, sdílený obsah |
| `ConversationSuggestions` | zmínky a inline boti nad polem |
| `ConversationSendAs` | kým se do skupiny píše (vlastní účet, kanál, anonymní správce) |
| `ConversationLinks` | kam vede odkaz nebo ťuknutí: chat, zpráva, příběh, pozvánka, start bota, mini aplikace |
| `ConversationBots` | tlačítka a klávesnice bota, příkazy, ankety, přihlášení, soutěž |
| `ConversationMessageMenu` | nabídka nad zprávou, kopírování, informace, záložky |
| `ConversationMessageActions` | připnutí, smazání, přeposlání, připomínka, nahlášení, kontakt z karty |
| `ConversationReactions` | reakce a kdo jak reagoval |
| `ConversationSelection` | výběr víc zpráv a hromadné akce |
| `ConversationSearch` | hledání v chatu, filtr podle člena, skok na datum |
| `ConversationTranslation` | překlad (server i telefon) a shrnutí |
| `ConversationMessageContext` | akce u zprávy a posudky modelu (termíny, podvod) |
| `ConversationPlayback` | hlasovky, kolečko se zvukem, čas v médiu, hudba, hlášení poslechu, přepis řeči |
| `ConversationRecorder` | nahrávání hlasovky a kolečka s náhledem |
| `ConversationDocuments` | stažení, otevření, sdílení a uložení souboru |
| `ConversationChatActions` | chat jako celek: pruhy nad konverzací, vstup, ztlumení, smazání |
| `ConversationScheduled` | naplánované zprávy |
| `ConversationPendingReplies` | rozepsané odpovědi botů a jejich zastavení (viz „Streamované odpovědi botů" v `boti.md`) |

`ConversationState.kt` nese stav a jeho typy, `ConversationParts.kt` to, co si části volají
navzájem (`MediaSender`, `MessageJumper`, `Throwable.reported`).

- **Veřejné API pro obrazovku je pořád ViewModel.** Části jsou `internal` a ViewModel na ně
  deleguje jednořádkovými funkcemi; obrazovka o nich neví. Nová logika patří do části, ne do
  ViewModelu; nová část = nový soubor, pole ve ViewModelu a delegace.
- **Závislosti jsou v konstruktoru části, a jen ty, které potřebuje.** Volání do jiné části je
  funkce (`closeMenus: () -> Unit`), nebo rovnou instance, když by nevznikl kruh (boti dostávají
  `ConversationLinks`). Kruh se řeší funkcí, která sáhne na druhou část až při volání
  (`sendAttachments = { … -> attachments.sendAttachments(…) }` v psaní).
- **Kde volající pojmenovává argumenty nebo spoléhá na výchozí hodnoty, je to rozhraní**, ne
  funkční typ — ten pojmenované argumenty neunese a `fun interface` zase nedovolí výchozí
  hodnoty (`MediaSender`, `MessageJumper`).
- **Pořadí inicializace:** části vznikají až po `_state` a `logger` a části, které dostávají
  jinou část jako instanci, až po ní. Pole částí mají **explicitní typ** — vzájemné odkazy přes
  lambdy jinak zacyklí odvození typu („Type checking has run into a recursive problem").
- **Pole se stavem se v části jmenuje `state`, takže lokální `val state` ani parametr lambdy
  `{ state -> }` mít nesmí** — zakryly by ho. Ve ViewModelu bylo `_state.update { state -> … }`
  a `val state = _state.value` běžné; v částech je `current` / `snapshot`. Překladač zakrytí
  pozná jen tam, kde se typy liší (`.update` na `ConversationState`), proto se to drží jako
  pravidlo, ne jako spoléhání na chybu.
- **Části se testují samy, bez ViewModelu** (`ConversationSearchTest`, `ConversationSelectionTest`,
  `ConversationReactionsTest`, `ConversationScheduledTest`, `ConversationComposerTest`,
  `ConversationMessageActionsTest`, `ConversationAttachmentsTest`, `ConversationLinksTest`,
  `ConversationBotsTest`, `ConversationSuggestionsTest`, `ConversationPlaybackTest`,
  `ConversationRecorderTest`, `ConversationDocumentsTest`, `ConversationTranslationTest`,
  `ConversationMessageContextTest`, `ConversationMessageMenuTest`, `ConversationChatActionsTest`,
  `ConversationPendingRepliesTest` — tedy všechny; držáky obrazovky `ConversationSheets` a `ConversationRecording` testuje
  `ConversationScreenHoldersTest`):
  stav je obyčejný `MutableStateFlow`, repository
  `UnimplementedConversationRepository`, `UnimplementedChatRepository`,
  `UnimplementedStickerRepository`, `UnimplementedInviteLinkRepository`, model
  `UnimplementedOnDeviceAi` a nastavení
  `UnimplementedAppSettings` (v testech; každé nepřepsané volání vyhodí
  chybu se jménem metody, takže test řekne, na co ještě sahá) a volání do jiných částí jsou
  lambdy. Vygenerované je z rozhraní — po změně `ConversationRepository` překladač vyjmenuje
  člen, který chybí. **Metoda bez uvedeného návratového typu má ve stubu `: Unit`**: holé
  `= error(…)` by odvodilo `Nothing` a test by ji pak nemohl přepsat (narazilo se na tom
  u `markOneTimeKeyboardUsed`).
- **Rozsah části v testu je `this` (`TestScope`), ne `backgroundScope`.** `advanceUntilIdle`
  na práci v `backgroundScope` nečeká, takže spuštěné dotazy na repository vůbec neproběhly
  a testy padaly na prázdné výsledky. Části nic dlouhodobého nespouštějí, `runTest` na ně
  počká i na konci.
- **Závislost, kterou nejde v testu nahradit, se předává jako funkce.** `TextClipboard` je
  `expect class`; výběr proto dostává `copyToClipboard: suspend (String) -> Unit`. Stejně psaní
  i přílohy dostávají `reportUsed` místo `ShareTargets` (třída se závislostmi na systému
  a souborech) a odkazy `openUrl` a `openProxy` místo `UrlOpener` (`expect class`)
  a `ProxyLinkFlow`. Boti dostávají `openUrl`, `copyToClipboard` a `clipboardConfirmsCopy`
  (hodnota `TextClipboard.confirmsCopy`, na platformě neměnná), stejně jako překlad a akce
  u zprávy a nabídka nad zprávou. Nabídka bere skutečné části překladu a akcí u zprávy (ptá se
  jich na cílový jazyk a nálezy), test je proto staví taky — stačí jim tytéž náhrady.
  Dokumenty dostávají `openFile` a `share` místo `FileOpener` a `MediaSharer`, akce
  u zprávy `addCalendarEvent`, `saveNewContact`, `joinWifiNetwork` a `loadRates` místo
  `CalendarEvents`, `ContactSaver`, `WifiJoiner` a `ExchangeRates` (jejich `null` znamená totéž
  co dřív chybějící třída) a místo `HomeRegion` rovnou měnu a jednotky.
- **`getString` v testech na JVM nejde** — `Resources.getSystem()` tam Android nemá („not
  mocked"). Co text z prostředků teprve skládá, se z části vytáhne do čisté funkce a testuje se
  ta: událost letu v kalendáři (`Flight.calendarEvent`) dostane název hotový. Výjimka spadne
  v korutině části a test pak jen nevidí výsledek, takže se to hledá špatně.
- **Přehrávač a fronta hudby jsou pro části rozhraní** (`VoicePlayerControl`,
  `MusicQueueControl` v `core/audio`), mikrofon `MediaCapture`. `VoicePlayer` stojí na
  platformních `AudioEngine` a `EarSensor` a v testu sestavit nejde; části z něj stejně používají
  jen pár metod. Výchozí hodnoty parametrů jsou v rozhraní — třída je při přepsání opakovat nesmí.
- **Část s nekonečným odběrem dostane v testu `detachedScope()`** (fixtures): přehrávání drží
  `transcribeContext` přes `stateIn(…, Eagerly)` a na dítě rozsahu testu by `runTest` čekal
  donekonečna. Rozsah je na plánovači testu, ale mimo jeho strom, a zruší se s jeho koncem.
- **Nahrávání se testuje v reálném čase** (`runBlocking`, `ConversationRecorderTest`):
  `VoiceRecorder` měří délku monotónními hodinami, takže virtuální čas testu na „příliš krátkou
  nahrávku" nedosáhne. Mikrofon je podvržený a „nahraje" platný Ogg.
- **Čas v médiu („1:23" v textu) je přehrávání, ne odkaz** (`ConversationPlayback.playMediaAt`).
  Dokud ležel v odkazech, táhly kvůli němu odkazy za sebou přehrávač, frontu hudby i celou
  část přehrávání — a v testu nešly sestavit. Odkazům z něj zbylo jen video z náhledu odkazu,
  které přehrávání otevře přes `openLink`.

### Jeden dialog ve stavu (`ConversationDialog`)

Potvrzovací a rozhodovací dialogy (smazat, připnout, odkaz z tlačítka bota, upozornění od bota,
ukončit anketu, sdílet číslo a polohu…) nesou jedno pole `ConversationState.dialog`, sealed typ
`ConversationDialog`. Dřív mělo každý z osmnácti vlastní pole a `ConversationDialogs` je kreslil
všechny nad sebou; dva modální dialogy naráz nedávají smysl a typ to teď říká sám.

- **Otevírá se `copy(dialog = …)`, čte `dialog<Druh>()`, zavírá `closing<Druh>()`**, ne
  `copy(dialog = null)`. Zavírá se jen dialog TOHO druhu: odpověď, která přijde pozdě
  (upozornění od bota, přihlášení z tlačítka), mohla mezitím otevřít jiný a zavření starého
  ho smazat nesmí (test `ConversationDialogTest`).
- **Nový dialog starý nahradí.** Jediná viditelná změna proti dřívějšku: dvě tlačítka bota
  (číslo a poloha) ťuknutá za sebou dřív čekala na potvrzení obě, teď jen to druhé.
- **Nápovědy gest mlčí nad jakýmkoli dialogem** (`dialog == null`), dřív jen nad smazáním
  a připnutím.
- **Mimo zůstala nabídka nad zprávou** (`messageMenu`). Sheety mají vlastní pole (viz „Jeden sheet
  ve stavu"), navigace mimo obrazovku jde událostmi (viz níž).

### Jeden sheet ve stavu (`ConversationSheet`)

Sheety nad konverzací (překlad, informace o zprávě, kdo reagoval, pozvánka a pozvánka do
složky, soutěž, hlasující, kontakty a poslední soubory ze sponky, naplánované zprávy,
připomenutí, import chatu, výběr textu) nesou jedno pole `ConversationState.sheet`, sealed typ
`ConversationSheet`. Dřív jich bylo třináct polí a mohlo jich být otevřených víc naráz.

- **Datové třídy sheetů typ implementují samy** (`TranslationSheet`, `ReactionDetails`,
  `InviteSheetState`…), takže se do nich sahá napřímo; obálka je jen tam, kde byla hodnota holá
  (`PollVotersSheet`, `ScheduledSheet`, `SelectTextSheet`).
- **Na rozdíl od dialogu se do sheetu dotahují data** (lidé, kteří reagovali; výsledek
  překladu; stav soutěže). Mění se proto `updatingSheet<Druh> { … }` (vrací nový, `null` =
  zavřít), který se týká jen sheetu TOHO druhu: data, která dorazí pozdě, nesmí přepsat sheet,
  který mezitím uživatel otevřel jinde. Platí i pro změnu hned po přečtení `state.value`
  (`joining`, `importing`, filtr reakcí) — mezi čtením a zápisem se sheet mohl vyměnit.
  Testy `ConversationSheetTest`.
- **Dialog a sheet se nevylučují**: potvrzení může vyskočit nad otevřeným sheetem.
- **Nápovědy gest mlčí nad jakýmkoli sheetem** (`sheet == null`), dřív nad výčtem pěti.
- **Mimo zůstalo, co není sheet se stavem:** `callRequest` a `openStickerSet` jsou požadavky,
  které obrazovka vyzvedne a vynuluje (blíž událostem), `memberPicker` patří k hledání.

### Navigace jako události (`ConversationNavEvent`)

Kam konverzace odchází (otevřít chat, obrazovku, příběh, profil, výběr pro přeposlání, mini
aplikaci, formulář ankety…), posílají části ViewModelu jako UDÁLOST
(`ConversationViewModel.navigation`, `ConversationNavEvent`), ne jako pole stavu. Dřív to bylo
třináct polí `ConversationState`, každé s funkcí `consume…`, kterou obrazovka po vyzvednutí
volala; stav ale říká, co JE, a zapomenuté vynulování by po otočení otevřelo chat znovu.

- **Kanál (`Channel.BUFFERED`), ne `SharedFlow`**: událost, která přijde, když obrazovka
  zrovna neodebírá (přechod, otočení), počká ve frontě a doručí se právě jednou.
- **Části ViewModelu dostávají `navigate` jako POVINNÝ parametr** konstruktoru. Výchozí „nic"
  by navigaci v produkci tiše zahodilo; testy si místo něj předají seznam a čtou z něj.
- **Odběr je jeden `LaunchedEffect` v `ConversationEvents`** a čte navigaci i stav přes
  `rememberUpdatedState` — anketa od tlačítka bota bere odpověď, která je v poli TEĎ.
- **Sheety uvnitř obrazovky zůstávají stavem** (`remindRequest`, `callRequest`,
  `openStickerSet`): nejsou navigace, otevírá je obrazovka sama a stav nese i jejich data.

## Bloky seznamu

Seznam zpráv se skládá z **bloků** (`ConversationItem`), které nesou svoje data a **samy se
vykreslí** — `LazyColumn` jen volá `Content`, žádný `when` nad druhy. O tom, JAKÝ blok vznikne,
rozhoduje jediná čistá funkce `conversationItems()` (testy `ConversationItemsTest`). Cílový stav
a postup přestavby: [docs/architektura-konverzace.md](../architektura-konverzace.md).

- **Všechno, co potřebuje znát sousedy, počítá builder**, ne položka v kompozici: předěly,
  seskupení bublin, profilovky, odznak přečtení, citace. Uvnitř `itemsIndexed` se to netestovalo.
- **Builder se volá v kompozici (`remember`), ne ve ViewModelu.** Ukotvení nové zprávy
  (`requestScrollToItem(0)`) musí vidět nový seznam v téže kompozici; tok z ViewModelu by šel
  o dispatch pozadu.
- **Klíče `remember` jsou jen pole stavu, ze kterých builder čte.** S celým `state` by se seznam
  přestavoval i při každém kroku průběhu stahování.
- **Lambdy v datech bloku nejsou** — rozbily by `equals` a tím přeskakování nezměněných položek.
  Obsluhu a rychle se měnící hodnoty (pozice hlasovky, průběh stahování, odkrývání časů) dává
  `ConversationItemScope`, a to jako funkce čtené až v UI, které je kreslí.
- **`Content` je nad `LazyItemScope`**, takže si blok sám volí `animateItem` — cíl letu odeslané
  zprávy se nesmí zjevovat prolnutím, sponzorovaná karta se neanimuje vůbec.
- **Zprávy sdílejí rám, obsah si kreslí samy.** `MessageItem` implementuje `Content` jednou
  přes `MessageFrame` (tvář, tah na odpověď, zvýraznění, reakce, doručení, kotva nabídky)
  a blok dodá jen `Body`. Druhy: `BubbleMessageItem`, `LargeEmojiMessageItem`,
  `StickerMessageItem`, `MediaMessageItem`, `LinkPreviewMessageItem` (`features/conversation/items/`).
- **Bublina není příznak rámu, ale sdílený kus** (`MessageBodyScope.Bubble`). Rám kreslí jen
  průhlednou plochu s ořezem a klikáním ve tvaru bubliny — ten potřebuje i nabídka pro díru
  v závoji —, pozadí přidá `Body`, když ho chce. Bez bubliny rám obsahu výslovně podá
  `onSurface`: barva popředí musí patřit ploše, ne bublině, která tam není.
- **Nový druh zprávy = nová třída + jedna větev v `messageItem()`.** Nic dalšího se nemění; kdyby
  bylo potřeba sáhnout do rámu nebo do jiného bloku, je to znamení, že to do rámu nepatří.
- **Akce jsou data** (`MessageAction`), a vede z bloků jediná cesta: `ConversationItemScope.onAction`
  → `ConversationViewModel.onAction`. Navigaci (`OpenMedia`) a sheet sady (`OpenStickerSet`)
  zachytí obrazovka dřív; ViewModel o navigaci neví. `messageId` jde vedle akce, ne v ní — u alba
  se ťuká na konkrétní fotku.
- **Nabídku skládají dvě strany a každá ví něco jiného.** Blok (builder, `menuActions()`) ví, co
  zpráva JE: holý text jde upravit, bez textu není co kopírovat, samolepka jde do oblíbených,
  připnutá se odepíná. Server ví, co se SMÍ — a to se načítá až při otevření. Položka
  (`MessageMenuAction.presentation`) sama řekne, jestli ji server dovolil a jak vypadá; nabídka
  jen projde seznam. Nová položka = nový objekt + místo v `menuActions()`.
- **Kandidáti se počítají ze SKUTEČNÉ zprávy, ne z `MessageRow.display`.** U alba má `display`
  přílohu vynulovanou (nese ji mřížka), takže by popisek alba vypadal jako upravitelný text.
- **Detektor gesta čte id zprávy přes `rememberUpdatedState`.** Vzniká jednou za život položky,
  ale odeslaná zpráva po potvrzení změní id (klíč položky ne); odpověď swipem by jinak mířila
  na dočasné id, které už neexistuje.
- **Servisní oznámení přerušuje blok z OBOU stran** (`continues`). Dřív se hlídala jen novější
  zpráva, takže bublina pod oznámením měla stažené nároží, jako by na něj navazovala. Našel to
  až test nad builderem — v kompozici to nikdo neověřoval.

## Obsah konverzace

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

## Médium bez bubliny

Fotka, video a album se kreslí přímo na plochu konverzace, bez bubliny (`showsBareMedia`
v `MessageRows.kt`, testy v `MessageRowsTest`). Bublina kolem snímku nemá co ohraničovat — je to
obrázek sám o sobě — a jen přidávala rám, ze kterého konverzace ztěžkla. Stejně to dělá Google
Messages.

- **Popisek na plochu NEPATŘÍ.** Text bez pozadí se na ploše konverzace ztratí, takže dostane
  vlastní kontejner v barvě bubliny pod snímkem. „Bez bubliny" platí pro obrázek, ne pro text u něj.
- **Snímek s popiskem je JEDNA KARTA**, jako v Google Zprávách a jako náhled odkazu: tvar bubliny
  nese celá karta (`clip(shape)`), uvnitř nejsou spáry ani vlastní zaoblení a **popisek má přesně
  šířku snímku** (`MediaWithCaption`). Dřív to byla skupina dvou kusů se spárou 2 dp a staženými
  nárožími a popisek byl široký jako jeho text — u krátkého čouhalo zaoblené nároží a vypadal jako
  další, useknutá zpráva, u dlouhého se roztáhl vedle zúžené fotky.
- **Šířku popisku dává vlastní `Layout`, ne `IntrinsicSize`.** Snímek je v `BoxWithConstraints`
  a ten intrinsické míry neumí (viz „Gotchas"); layout změří snímek a popisku dá jeho šířku napevno.
- **Karta se zarovnává ke straně odesílatele** (`horizontalAlignment` podle `isOutgoing`) a užší
  než řádek je VŽDYCKY: strop 82 % platí i tady (viz „Vzhled konverzace"). Dokud šla fotka na
  šířku přes celý řádek, bylo zarovnání k ničemu — okraj vyšel na obou stranách stejný.
- **Snímek bez popisku přebírá tvar bubliny** (`mediaShape`), aby navazoval na sousedy v bloku;
  s popiskem je rovný a ořezává karta.
  Náhled souboru v řádku si drží svých 12 dp — velké zaoblení bubliny by z 48dp čtverečku udělalo
  kolečko.
- **Jméno odesílatele nad holým snímkem si nese odsazení i do stran** (`MESSAGE_PADDING_H`,
  jako text v bublině). Celá zpráva je ořezaná tvarem bubliny (`MessageBox`, kvůli vlnce
  zvýraznění a díře v závoji nabídky), takže jméno přilepené k levé hraně ukouslo zaoblené
  nároží první písmeno („ilipHorky"). **Cokoli, co leží v rohu
  zprávy bez bubliny, se musí odsadit o zaoblení** — ořez nekončí u snímku.
- **Má-li karta hlavičku (citace, přeposlání), je jméno v ní, ne nad kartou** (`CardHeader`,
  `showsSenderName`). Karta odkazu ho má v horním dílu vždycky. Dřív stálo jméno na ploše nad
  kartou, zatímco u textu je prvním řádkem bubliny — blok začínající kartou vypadal jinak než
  blok začínající textem. Holý snímek bez hlavičky má jméno nad sebou dál: hlavička jen kvůli
  jménu by z fotky udělala kartu.
- **„Upraveno" samo popisek nedělá** (`hasCaption` v `MediaMessageItem`). Upravená fotka bez
  popisku dostávala pruh v barvě bubliny jen s tímhle slovem; teď stojí pod snímkem na ploše jako
  u samolepky, s místem na čipy reakcí.
- **Akcent obsahu na ploše je `primary` na obou stranách** (`surfaceAccent`). Odchozí strana měla
  barvu popředí plochy — zbytek z doby, kdy snímky ležely v bublině, kde v odchozí `primary`
  bublině akcent `primary` být nemohl (zděděno přestavbou na bloky, `5018e7f4d`).
- **Citace je horní díl karty snímku, bublinu si nevynutí** (`QuotePart`, nad značkou
  přeposlání; `MediaMessageItem.quoted`). Bez pozadí by se ztratila — ale pozadí jí dá karta,
  stejně jako popisku a značce přeposlání. Dřív si bublinu vynutila a fotka, video, album i mapa
  v ní ležely odsazené o `MESSAGE_PADDING_H` s vlastním zaoblením 12 dp, jako fotka v rámečku,
  zatímco přeposlaný snímek šel od kraje ke kraji. Samolepka, kolečko a velká emoji si bublinu
  s citací drží dál (viz „Přeposlaná zpráva"). Náhled odkazu má vlastní kartu (viz „Náhled
  odkazu jako karta").
- **Kolečko v bublině má přepis taky** (`VideoNoteWithTranscript` v `BubbleMessageItem`). S citací
  nebo přeposláním se kreslilo holou přílohou a tlačítko přepisu i přepis zmizely; bez nich má
  vlastní blok `VideoNoteMessageItem`.
- **Rozhoduje citace, která se KRESLÍ, ne `replyToMessageId`** (`showsQuote` v `MessageRows.kt`).
  Ve vlákně komentářů TDLib vyplní odpověď na kořen (příspěvek kanálu) skoro každému komentáři,
  i bez ťuknutí na „Odpovědět"; citace kořene se tam nekreslí (`quoted` ho přeskočí). Pravidla
  bubliny ale hleděla jen na `replyToMessageId != null`, takže fotky, videa i samolepky
  v komentářích ležely v prázdné bublině a náhled odkazu nebyl karta — nahlášené u videa,
  protože tam zrovna bylo. Naopak odpověď na zprávu z JINÉHO chatu (`externalReply`, i odpověď
  na příběh) `replyToMessageId` nemá, citaci kreslí, a fotka s ní šla na plochu bez citace.
  Ověřeno na emulátoru ve vlákně komentářů kanálu.
- **Smazaná citovaná zpráva má zástupnou citaci „Smazaná zpráva"** (`deletedQuote`,
  `ConversationState.deletedQuotes`, test `smazanaCitovanaZpravaMaZastupnouCitaci`). Citaci tahle
  odpověď kreslí (`showsQuote`), takže si vynutila bublinu nebo hlavičku — a ta zůstala prázdná,
  bez jediného slova proč. Kdo psal, už nevíme, takže citace má jen ten řádek a nikam neskáče.
  - **Selhání dotazu = smazaná zpráva.** Chybu vrací wrapper a 404 od výpadku nerozliší, ale
    TDLib na spojení čeká, takže dotaz offline nespadne, jen počká.
  - **Na nedohledané citace se znovu neptá.** `resolveMissingQuotes` běží s každou novou zprávou
    a dřív posílal `getMessage` na tutéž smazanou zprávu pořád dokola; teď si pamatuje selhání
    i dotazy, které ještě běží (`resolvingQuotes`).
  - Ověřeno na emulátoru: Notifikátor odpověděl fotkou a původní zprávu smazal (`deleteMessage`).
- **Soubor, hlasovka ani samolepkový popis na plochu nejdou.** Jsou to řádky s textem a ikonou,
  ne obrázek — pozadí je to jediné, co je drží pohromadě.
- **Reakce a odznak doručení plochu snesou.** Čipy reakcí mají vlastní pozadí a odznak je pod
  bublinou už od dřívějška; pod holým snímkem tedy nebylo co řešit.
- **Barvy bubliny se počítají i tehdy, když bublina není** (`bubbleColor` / `onBubbleColor`).
  Kontejner popisku je potřebuje, a odkaz v popisku odchozí zprávy musí vzít barvu popředí —
  `primary` na `primary` by byla neviditelná, tatáž past jako u citace.

## Náhled odkazu jako karta

Náhled odkazu u zprávy bez přílohy a citace je **jedna karta ze dvou částí**: nahoře text zprávy
v barvě bubliny, dole popis webu (web, titulek, popis) v tónované ploše. S **velkým obrázkem**
stojí obrázek mezi nimi přes celou šířku, **malý** je čtvereček vpravo v popisu. Pravidlo
`showsLinkPreviewCard` (a `LinkPreview.showsLargePhoto`) v `MessageRows.kt`, blok i kreslení
v `items/LinkPreviewMessageItem.kt`.

- **Dvě plochy, ne proužek.** Malý náhled byl dřív uvnitř bubliny oddělený proužkem akcentu
  vlevo (převzatým z citace) — jazyk M2. Odlišná plocha dělá totéž bez čáry navíc a karta
  vypadá stejně s velkým obrázkem i bez něj.
- **Bez velkého obrázku je karta široká podle obsahu**, nejvýš jako každá jiná zpráva
  (`MESSAGE_MAX_WIDTH_FRACTION`), a obě části mají stejnou šířku (`IntrinsicSize.Max`). Smí to jen
  proto, že v kompaktní kartě nic se `SubcomposeLayout` není — velký obrázek (`BoxWithConstraints`)
  má vlastní větev se šířkou z `photoFrame`.
- **Zvýrazněná příchozí zpráva přejde do `tertiaryContainer`** — stejné barvy jako popis. Popis
  (i citace nad odpovědí) se proto posune o kus k barvě popředí té plochy (`surfaceApart`,
  `MessageBodyScope.insetColor`), jinak by obě části splynuly. **Posouvá se k `on*` roli, ne
  k `tertiary`:** `tertiary` je přesně ta barva, na kterou se spolehnout nedá — v paletě se
  zvýšeným kontrastem leží na téže světlosti jako `tertiaryContainer`, takže se karta slila
  v jednu plochu i u zvýrazněné ODCHOZÍ zprávy (bublina `tertiary`, popis `tertiaryContainer`).
  Podmínka proto není „příchozí a zvýrazněná", ale změřený kontrast vůči bublině.
- **Posun 0,18 vyšel z porovnání s tím dřívějším** (0,22 k `tertiary`): ten dával odstup ploch
  1,51 v tmavém a 1,35 ve světlém tématu, tenhle 1,49 a 1,35 — vzhled v běžné paletě zůstává
  a text na posunuté ploše je pořád nad AA. Větší posun by ho ukrajoval, protože míří přesně
  k barvě toho textu.
- **U zprávy s citací nebo přílohou zůstává náhled v bublině** (`LinkPreviewCard`), a to jako
  tónovaný kontejner stejný jako citace (`QUOTE_CONTAINER_ALPHA`, `QUOTE_CORNER`), ne proužek.

- **Bez spár.** Dřív to byly tři kusy se spárami a staženými nárožími, stejný jazyk jako fotka
  s popiskem — a vypadalo to jako tři zprávy. Sdílený odkaz je ale jedna věc: tvar bubliny nese
  celá karta (`clip(shape)` na sloupci), části uvnitř jsou rovné.
- **Text zprávy má barvu bubliny, popis webu `tertiaryContainer`.** Text je zpráva sama a odchozí
  zprávy se od příchozích poznají barvou (`primary`); v jiné roli by se strany lišily jen
  zarovnáním — past, kvůli které odchozí bublina přestala být `primaryContainer`. Popis je cizí
  vložený obsah, takže se od textu musí lišit; terciární role je v M3 na doplňkové prvky
  a nesplyne s žádnou z barev bublin. Název webu je `tertiary` na `tertiaryContainer`.
- **Karta jde k hraně řádku, bez vnitřního odsazení.** Dřív byla uvnitř průhledné „bubliny"
  s odsazením 14 dp a od ostatních zpráv ustupovala.
- **Šířku určuje obrázek.** Text i popis se na ni natáhnou (`Modifier.width(frame.width)` na
  sloupci), takže karta nemá roztřepený okraj.
- **Barvu z obrázku NEVYTAHUJEME.** Google Messages dává pod snímek pruh v jeho barvě, ale TDLib
  u náhledu žádnou barvu neposílá a kvantizace z bitmapy by znamenala keš na `fileId` a počítání
  mimo hlavní vlákno.
- **Citace a příloha si bublinu vynutí** — jako u holého média. Náhled u zprávy s fotkou je
  stejně jen doplněk k příloze, ne hlavní obsah.

## Rámeček fotky v bublině

Fotka a video se **zužují, neořezávají** (`photoFrame` v `MediaFrame.kt`, testy `MediaFrameTest`):
z poměru stran se spočítá výška, zastropuje se, a když se strop uplatní, dopočítá se šířka zpátky
jako `výška × poměr`.

- **Ořez byl ta chyba, ne strop.** Předtím se výška jen usekla stropem a šířka zůstala na
  `fillMaxWidth()`, takže `ContentScale.Crop` ze svislého snímku obrazovky (poměr kolem 1:2) nechal
  jen **prostřední pruh** — nešlo z něj přečíst ani co na něm je. Zvednutí stropu samo nepomůže:
  vysoká fotka přes celou šířku bubliny bude vždycky vyšší než rozumná bublina. Fotka proto zabírá
  méně šířky a je vidět celá, jako v Telegramu i Google Messages.
- **Podlaha šířky existuje** (`MIN_PHOTO_WIDTH`). U extrémně vysokého snímku by dopočítaná šířka
  vyšla na pár desítek dp a z fotky by byl nečitelný proužek; pod podlahou se poměr stran obětuje
  a `Crop` fotku ořízne do stran. Proto `ContentScale.Crop` zůstává, i když rámeček poměr stran
  jinak respektuje.
- **Pravidlo je čistá funkce mimo composable**, jako `readBadgeRow` nebo `formatFileSize`. Uvnitř
  `@Composable` by se netestovalo a přitom je to samý hraniční případ: neznámé rozměry (nula, NaN),
  panorama, úzká bublina.
- **Počítá se ručně v `BoxWithConstraints`**, ne přes `aspectRatio` — viz „Gotchas".
- **Popisek se zalomí na šířku fotky** (viz „Médium bez bubliny"), takže zúžená fotka s dlouhým
  popiskem („Screenshot (18. 9. 2026 5:23:34)") nenechá vedle sebe prázdné místo.
- **Fotka s popiskem využije celou nabízenou šířku** (parametr `fillWidth` u `photoFrame`).
  Popisek má šířku fotky, a vysoký snímek zúžený na pár set bodů lámal text po dvou třech
  slovech — delší popis byl nečitelný. S popiskem se proto fotka rozšíří na celou dostupnou
  šířku (kterou shora hlídá strop rámu 82 % řádku), výška zůstane na stropu a obrázek se
  **ořízne na střed** (`Crop`, poměr stran bez deformace), jako v Telegramu. Bez popisku se
  dál jen zužuje a je vidět celá. Totéž platí pro velký obrázek náhledu odkazu: text zprávy
  i popis webu mají jeho šířku.
- **Album ve čtvercích zůstává.** Mřížka je vědomé rozhodnutí (viz „Alba"), takže pravidlo platí
  jen pro samostatnou fotku a video; řádek alba s jednou fotkou se ořezává dál.

## Alba

Telegram **víc příloh v jedné zprávě nenese.** Album jsou samostatné zprávy (každá s vlastním id,
popiskem, reakcemi) spojené `mediaAlbumId`, odeslané naráz přes `sendMessageAlbum` (2–10; fotky
s videi dohromady, dokumenty a audio jen se stejným typem). Konverzace je kreslí jako jednu bublinu.

- **Položky seznamu jsou ŘÁDKY, ne zprávy** (`groupAlbums` → `MessageRow`). Sousední zprávy téhož
  alba tvoří jeden řádek. Na řádcích stojí sousedé (předěly dne, seskupení bublin, profilovky)
  i index v `LazyColumn` — skok na zprávu proto hledá řádek, který ji obsahuje.
- **Klíč alba je klíč jeho NEJSTARŠÍ zprávy.** Novější fotky dorážejí za ní a klíč se nezmění.
- **Popisek, reakce, odpověď a nabídka patří zprávě s popiskem** (`primary`), bez popisku
  nejstarší. Čas bubliny je čas nejnovější fotky a stav doručení „nejhorší" z alba — album je
  odeslané, až když jsou odeslané všechny jeho zprávy.
- **Jen „Odpovědět" z nabídky míří na PODRŽENOU fotku** (`MessageMenu.pressedMessageId`,
  `replyTarget`, test `odpovedMiriNaPodrzenouFotkuAlba`). Nabídka se otevírá pro zprávu
  s popiskem, takže odpověď na třetí fotku citovala popisek alba. Dlaždice proto hlásí, na
  které fotce stisk začal (`ShowMenu.pressed`), a odpověď vezme ji. Reakce, přeposlání,
  mazání a citace označeného textu dál platí pro celé album — text patří zprávě s popiskem;
  tah na odpověď cituje album (nemá z čeho poznat fotku). Ověřeno Bot API:
  `reply_to_message` míří na prostřední fotku bez popisku.
- **Přečtení a mazání se týkají celého alba.** Z viditelné položky přijde jen klíč nejstarší
  zprávy; bez rozšíření na celé album by zbylé fotky zůstaly napořád v počtu nepřečtených
  a „Smazat" by smazalo jednu fotku z bubliny.
- **Mřížka, ne carousel.** Album má být vidět celé a vodorovný tah carouselu by se pral se
  swipem na odpověď. Řádky po 1–3 podle pravidel (`albumRowSizes`), ne mozaika podle poměrů stran —
  vypadá vyrovnaně bez ohledu na obsah. Samotná fotka na řádku podle poměru stran se stropem,
  víc fotek čtverce. Zaoblení nese celá mřížka, dlaždice mají jen spáry.
- **Album dokumentů nebo audia je seznam pod sebou** — do mřížky patří jen fotky a videa s náhledem.
- **Ťuknutí na dlaždici otevře tu konkrétní fotku** (se sdíleným prvkem); prohlížeč pak listuje
  fotkami chatu, album v tom nijak zvlášť neřeší.
- **Popisek alba z pár emoji není velké emoji** — bublina alba si pozadí drží vždycky.
- **Album na hraně načtené historie může být neúplné**; dotažením starší stránky se k němu zbytek
  připojí.

## Předěly dne

V bublině se vypisují jen hodiny, protože datum má nést předěl nad blokem zpráv. Bez něj se
v delší historii nedalo poznat, kde končí včerejšek.

- **Předěl je samostatný blok seznamu** (`DayDividerItem`), vyrobený builderem, který sousedy
  zná (viz „Bloky seznamu"). Patří zprávě, jejíž starší soused je z jiného dne.
- **Předěl stojí NAD zprávou, a proto v obráceném seznamu ZA ní** (vyšší index).
- **Klíč předělu je datum**, ne zpráva. Když se dotáhne starší stránka, přestěhuje se předěl nad
  starší zprávu téhož dne jako tatáž položka. Opakovaný den (posunuté hodiny odesílatele)
  dostane do klíče i klíč zprávy — dva stejné klíče `LazyColumn` shodí.
- **Mezi předělem a zprávou pod ním leží i `verticalArrangement`**, takže předěl má dole o ten
  rozestup méně vzduchu (`DIVIDER_BOTTOM_SPACING`) — jinak by zpráva visela dál od svého data.
- **Skok na nepřečtenou posazuje nahoru i předěly nad ní**, ne samotnou zprávu. Dokud byl předěl
  součástí položky, šel nahoru s ní; jako samostatný blok by zůstal těsně nad horní hranou.
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
- **Předěl dne nese i čas první zprávy dne** („Včera · 21:00"), jako v Google Zprávách.
- **Časový předěl po pauze aspoň hodinu** (`TimeDividerItem`, `TIME_DIVIDER_GAP_SECONDS`, testy
  v `ConversationItemsTest`). V bublině čas není, takže bez něj se dal zjistit jen tahem doleva
  nebo v nabídce. Souvislý rozhovor čas nepřerušuje, nová dávka zpráv po pauze ho dostane. Na
  přelomu dne ho nese předěl dne — dva předěly nad sebou nevzniknou. Stojí nad předělem
  nepřečtených a **ukončuje blok bublin** stejně jako předěl dne (`continues` hlídá i pauzu,
  jinak by se nároží přes předěl stahovala). Klíč je odvozený od klíče zprávy pod ním.
- **Předěl a systémové oznámení vypadají stejně** (`CenteredNote`). Obojí je poznámka na okraji
  konverzace, ne zpráva od někoho; dvě různá pojetí téhož by seznam roztříštila.
- **Akce u oznámení je textové tlačítko pod větou** (`NoteWithAction` v `Notes.kt`: odkaz na
  převedenou skupinu, návrh profilové fotky a data narození; žádost o vypnutí ochrany má dvě
  tlačítka). Dřív byla část akcí klikatelný sloupec s textem v `primary` a část tlačítka —
  v jedné konverzaci dva jazyky pro tutéž věc. Skok na zprávu z oznámení (připnutí, anketa)
  tlačítko nemá: věta sama říká, o co jde, a klikatelná je celá.

## Tvar bubliny a seskupení

- **Stažené nároží (`BUBBLE_TIGHT`, 4 dp) drží skupinu pohromadě, takže se MUSÍ ptát na OBA
  sousedy.** Dřív bylo spodní nároží na straně odesílatele stažené VŽDY, bez ohledu na souseda:
  tím vznikla dvojice „spodek předchozí + vršek navazující" bez toho, aby bublina musela cokoli
  vědět o novějším sousedovi. Cena za tu zkratku byla, že zaoblení chybělo i tam, kde blok
  SKONČIL — poslední odchozí bublina měla ostré pravé dolní nároží (u příchozích zrcadlově levé)
  a blok světlých bublin končil bez rohového oblouku.
- **Novější soused je o index NÍŽ** (seznam jde od nejnovější), a ta informace už na místě volání
  byla — počítá se z ní profilovka („poslední zpráva bloku"). Bublina proto bere dva příznaky,
  `continuesAbove` a `continuesBelow`, a stahuje jen nároží na straně odesílatele; na druhé straně
  jde bublina k okraji obrazovky a stažené nároží by tam nemělo co navazovat.
- **Startovní strana letu musí mít tvar CÍLOVÉ bubliny.** Odesílaná zpráva je vždycky nejnovější,
  takže pod ní nic nenavazuje a všechna nároží jsou zaoblená — proto tam po téhle změně je
  `RoundedCornerShape(BUBBLE_CORNER)`. Dokud bylo spodní nároží stažené vždy, mělo ho i pole;
  s novým pravidlem by zpráva po cestě měnila tvar.

## Čas a stav doručení

Čas v bublině NENÍ a stav doručení je POD ní, ne v ní. V bublině zabíral řádek u každé jediné
zprávy, a přitom ani jedno z toho většinou nic neříkalo.

- **Datum nesou předěly dne a plovoucí datum při scrollu**, ne bublina; čas po pauze časové
  předěly (viz „Předěly dne"). Čas jednotlivé zprávy
  se odkrývá tahem doleva (viz „Odkrývání časů tahem") a je i v nabídce nad zprávou.
- **„Upraveno" zůstalo.** Že se zpráva změnila, se odjinud nedozvíme; čas ano.
- **Odznak je mimo bublinu, takže barva patří PLOŠE** (`onSurfaceVariant`), ne bublině. Dokud byl
  uvnitř, bral `contentColor`; pod bublinou by `onPrimary` na tónovaném pozadí nebyl vidět —
  tatáž past jako u `QuotedMessage.accent` a u zpráv bez bubliny.
- **`Box` kolem bubliny musí obepínat SAMOTNÝ `Surface`.** Nabídka nad zprávou si z něj bere
  `anchorBounds` a vykrajuje podle nich díru do závoje; kdyby v něm byl i odznak, byla by díra
  o ten kus vyšší. Odznak proto stojí ve sloupci ZA ním, a posun tahem se přesunul na ten sloupec,
  aby odznak jel s bublinou.
- **„Přečteno" hlásí automaticky nejvýš JEDNA zpráva v chatu** (`readBadgeRows`): nejnovější,
  a jen když je moje. Přečtením té poslední jsou přečtené i všechny před ní, takže dvojitá fajfka u celé
  historie byla řada stejných odznaků bez informace.
- **Odpověď protějšku přečtení dokazuje SAMA.** Když po mojí zprávě někdo napsal, musel ji
  přečíst a odznak nemá co dodat — proto se hledá nejnovější zpráva, ne nejnovější odchozí.
  Vyjde-li příchozí, nehlásí přečteno nikdo. (Nejdřív to bylo „nejnovější odchozí" a odznak
  zůstal svítit u zprávy, na kterou už protějšek odpověděl.)
- **Servisní oznámení se přeskakují.** „X se přidal do skupiny" nedokazuje, že si někdo něco
  přečetl, takže by odznak schovalo neprávem.
- **`Sending` a `Sent` zůstávají u každé zprávy** — ty ještě něco říkají.
- **`Failed` se ukazuje VŽDY.** Je to jediný stav, který uživatel musí zaregistrovat; schovat ho
  kvůli úspoře místa není úspora, ale skrytá chyba. A nese větu, co dál („Neodesláno. Ťukni a zkus
  to znovu."), viz `docs/poznatky/konverzace-psani.md`, „Neodeslaná zpráva a opakované odeslání".
- **Ťuknutí na vlastní zprávu si odznak vyžádá**, druhé ho schová (`readReceiptFor`
  v `ConversationState`, `toggleReadReceipt`). Je to ubezpečení u jedné konkrétní zprávy —
  u starších se jinak nedá zjistit, jestli je protějšek přečetl.
- **Vyžádaný odznak automatický NERUŠÍ.** `readBadgeRows` vrací obojí: nejnovější zprávu podle
  pravidla výš a tu vyžádanou. Rozhoduje se to na jednom místě, ne druhou podmínkou v bublině —
  ty dvě by se časem rozešly.
- **Server se na nic neptá.** `lastReadOutboxMessageId` je posuvný ukazatel na chatu, takže stav
  libovolné odchozí zprávy je porovnání id; ťuknutí mění jen to, co se kreslí.
- **Jedno id, ne množina.** Ťuknutí na jinou zprávu odznak přepne tam. Odchod z chatu ho zahodí
  i se stavem — je to zobrazení na chvíli, ne nastavení.
- **Ťuknutí dostane jen bublina, ne její potomci.** Odkaz v textu, citace, fotka, náhled odkazu
  i samolepka mají vlastní klikání a k bublině ho nepustí, takže u zprávy složené z fotky není
  kam ťuknout. Přidat gesto i jim by znamenalo vzít ťuknutí těm akcím — u fotky otevření
  prohlížeče — a to je horší než chybějící zkratka.
- **V nabídce nad zprávou je SKUTEČNÝ stav, ne to, co ukazuje odznak.** Odznak „přečteno" je pod
  bublinou jen u vybraných zpráv; nabídka ho vypisuje vždycky (vedle času), protože ťuknutím se
  k němu čtečka obrazovky nedostane. Proto se počítá zvlášť `deliveryState` (pravda) a `delivery`
  (co kreslí odznak) — první verze to měla společné a v nabídce pak stav u starší zprávy chyběl.
- **Pravidlo je vytažené z composable do `readBadgeRows`** (`MessageRows.kt`) a pokryté testy
  (`MessageRowsTest`). Uvnitř `@Composable` by se netestovalo, a je to přesně ten druh podmínky,
  u které se hraniční případ (odpověď protějšku, servisní zpráva, album) pozná až na zařízení.
- **Hledá se jednou na změnu seznamu** (`readBadgeKey` v `remember`), ne v bublině. Průchod
  seznamem uvnitř `items` je to kvadratické chování, na které projekt narazil u citací.
- **Odznak se váže na NEJSTARŠÍ zprávu položky**, protože tím je klíč položky (u alba je to jeho
  nejstarší zpráva, viz `ConversationState.keyOf`).
- **Dokud ukazatel přečtení neznáme, „odesláno" se nekreslí** (`lastReadOutboxMessageId: Long?`,
  `null` = neznámý; `delivery` pak vrátí `null`). Ukazatel chodil jen s `chatInfo`, které skládá
  řadu dotazů za sebou (téma, práva, supergroup, hovory), a historie z lokální databáze ho
  předběhla. S nulou místo neznámé hodnoty dostala každá odchozí zpráva fajfku, po příchodu
  `chatInfo` se u všech kromě poslední animovaně sbalila (viz bod níž) a seznam se zkrátil —
  po otevření chatu dvojí probliknutí a nejvíc poskočila zpráva nahoře v okně. Ukazatel se proto
  ptá napřed a zvlášť (`lastReadOutboxMessageId(chatId)`, jen `getChat`), před první stránkou
  historie; `chatInfo` i update ho jen posouvají dopředu (`maxOf`). Hodiny a vykřičník na
  ukazateli nezávisí a kreslí se hned.
- **Odznak mizí i se svým MÍSTEM** (`shrinkVertically`, ne jen `fadeOut`) a při odjezdu kreslí
  POSLEDNÍ známý stav — v okamžiku skrytí je hodnota už `null` a `AnimatedVisibility` by dobíhala
  s prázdným obsahem. Obě pasti jsou v projektu už zdokumentované (spodní bar, pruh připnutých).

## Vzhled konverzace

- **Žádná zpráva nezabere celý řádek: strop je 82 %** (`MESSAGE_MAX_WIDTH_FRACTION`, výchozí
  hodnota `MessageItem.maxWidthFraction`). Volný pruh na protější straně je pravidlo RÁMU, ne
  vlastnost jednoho bloku — nový druh zprávy ho dostane sám od sebe a celá šířka je vědomá výjimka.
  Dřív ho měla jen textová bublina a obrázky si šířku určovaly samy: **holá fotka, album i karta
  náhledu odkazu s velkým obrázkem šly přes celý řádek**, a protože se kreslí bez bubliny, neměly
  ani barvu, podle které by se odesílatel poznal — na obou stranách konverzace vypadaly stejně.
  Hlídá to test `zadnyBlokZpravyNezabereCelyRadek` (projde všechny druhy bloků), aby se šířka
  přes celý řádek nevrátila zadem u dalšího typu zprávy. Kopie zprávy v nabídce dostává tentýž
  podíl i totéž omezení šířky jako zpráva v seznamu.
- **Odesílatel ve skupině má stálou barvu** (`senderColor` v `core/ui`, testy `SenderColorTest`):
  jméno nad zprávou a jméno v citaci. Osm odstínů rovnoměrně po kruhu od odstínu `primary`,
  HCT tón 40 / 80 podle režimu — kontrast i soulad s Material You, žádná barva natvrdo.
- **Odpověď je karta ze dvou částí, jako náhled odkazu**: nahoře citace v tónované ploše
  (`MessageBodyScope.insetColor`, `tertiaryContainer`), pod ní odpověď v barvě bubliny
  (`QuotedBubble` v `BubbleMessageItem.kt`). Dřív byla citace subkartička uvnitř bubliny —
  zaoblená plocha v ploše, dvě vnořené karty. Jméno citovaného nese ve skupině jeho barvu,
  jinak `tertiary`.
- **Ikona citace (`format_quote`) stojí VŽDY před jménem** — na kartě zprávy i nad vstupním
  polem, na obou stranách konverzace. Stejně jako tužka u úpravy: říká, k čemu se obsah
  vztahuje. Na „opačné" straně by se u odchozí a příchozí zprávy stěhovala.
- **Karta odpovědi je vlastní `Layout`, ne `IntrinsicSize`**: pod citací může být fotka
  nebo album v `BoxWithConstraints`. Citace se změří v přirozené šířce, obsah dostane aspoň
  tu šířku jako minimum a plochy kreslí karta sama za obsahem (`drawBehind`).
- **Nároží bublin se animují** (spatial spec), jako skupiny v seznamu chatů. Pružina přestřelí,
  takže se ořezávají na nulu — záporné zaoblení `RoundedCornerShape` odmítne.
- **Nová zpráva pružně naskočí** z měřítka 0,85 od strany odesílatele (`arrival`). Jen jednou
  a jen když přišla po otevření konverzace (`claimArrival` s tolerancí pár sekund na hodiny
  serveru) — položka se při scrollu skládá znovu a pružit nemá co. Let ze vstupního pole se
  nepřebíjí. **Čip reakce při změně počtu povyskočí**, ne při prvním složení.
- **Odesílaná zpráva je průhlednější** (0,65) a po potvrzení se usadí. Klíč položky se potvrzením
  nemění, takže přechod dobíhá na téže bublině.
  - **Ale až když odesílání trvá přes vteřinu, a nikdy během letu z pole** (`sendingAlpha`
    v `MessageFrame`). Letící bublina se kreslí v překryvu nad aplikací, kam průhlednost z vrstvy
    položky nedosáhne (stejně jako fade-in `animateItem`, viz „Cíl letu nemá fade-in"
    v `konverzace-psani.md`): doletěla sytá, po přistání skočila na 0,65 a s potvrzením od
    serveru zase ztmavla. Po dosednutí tak dvakrát problikla. Při běžném potvrzení se teď
    neztlumí vůbec, offline zpráva zesvětlá po vteřině plynule a zpráva odesílaná déle než
    pár vteřin (návrat do chatu bez sítě) je ztlumená rovnou. Ověřeno nahrávkou na emulátoru
    s vypnutou sítí.
- **Zvýraznění po skoku je dvojí pulz měřítkem**, ne pruh přes celou šířku. Ověřovat se to dá
  jen měřením: běžný `screencap` pulz mine. Série `screencap /sdcard/x.raw` v jednom `adb shell`
  a porovnání okraje bubliny v řádku pixelů ho zachytí.
- **Zvýrazněná zpráva navíc změní barvu a její okraj se rozvlní, vlnky pomalu obíhají**
  (`MessageFrameData.emphasized`, `WavyShape` v `items/EmphasisShape.kt`). Stavy jsou dva
  a vypadají stejně, liší se délkou: jednorázově po skoku (`highlighted`, 2,5 s) a trvale jako
  ukázaný výsledek hledání (`searchFocused`, dokud se nepřejde jinam nebo hledání nezavře).
- **Barva je terciární role** (odchozí `tertiary`, příchozí `tertiaryContainer`). Bubliny jinak
  terciární roli nepoužívají, takže se nesplete s žádnou stranou, a rozdíl sytá / světlá mezi
  stranami zůstane. Prolíná se (`slowEffectsSpec`), akcent citace jde s ní.
- **Zvlňuje se sama zpráva, žádný obrys navíc.** Obrys kolem (první pokus) působil jako cizí
  rám a ležel přes fajfky doručení. Vlní se ořez celé zprávy v `MessageBox`, a to jen DOVNITŘ:
  vrcholy vlnek leží na původním okraji, takže pozadí bubliny, fotka i samolepka (každá ořezaná
  vlastním zaobleným tvarem) se do vlnek ořežou a nic nepřečnívá. Obsah má odsazení 8–14 dp,
  vlnky 3 dp do textu nesáhnou.
- **Vrstva ořezává tvarem bubliny, vlnky jsou jen ořez při kreslení** (`drawWithContent`
  + `clipPath` nad `WavyShape`). Fáze se mění každý snímek a `WavyShape` je pokaždé nový objekt;
  čte se až při kreslení, takže obíhání nerekomponuje (v `Modifier.clip` by rekomponovalo).
  Vlnek je po obvodu celý počet, jinak by na spoji byl zub.
- **Ořez VRSTVY rozhoduje i o dotyku — vlnitý obrys v něm nesmí být.** Dokud nesla vrstva
  `graphicsLayer { shape = WavyShape; clip = true }`, nešlo nad ukázaným výsledkem hledání otevřít
  nabídku dlouhým stiskem a nic uvnitř zprávy nereagovalo; po zavření hledání (zvýraznění zmizí)
  ano. Compose u ořezané vrstvy zkouší, jestli bod leží v obrysu, a u `Outline.Generic` to dělá
  průnikem obrysu se čtverečkem 0,01 px přes `Path.op` (`ShapeContainingUtil.isInPath`) — u vlnitého
  polygonu s tisíci body (zpráva vysoká přes 3 000 px) vyšel prázdný. Dotyk pak k rodiči kolem
  zprávy dorazil nespotřebovaný, ale k ničemu uvnitř ne. Zvlněná hrající hlasovka měla totéž.
  **Obecně: kde má obsah pod vlastním tvarem reagovat na dotyk, patří do ořezu vrstvy jen
  jednoduchý tvar (zaoblený obdélník), složitý obrys jen do kreslení.** Jestli selhával i krátký
  zvýrazněný text, ověřené není — na opravě to nic nemění.
- **Ověřilo se to sondou, ne úvahou**: log v `requestMenu` (při hledání se nezavolá vůbec), log
  událostí v `PointerEventPass.Final` na rámu (`Press`/`Release` nespotřebované) a pokus s
  `clip = false` u zvlnění (nabídka se otevře). Pozor na falešnou stopu: dokud se hledání načítá,
  zpráva ještě zvýrazněná není a nabídka se otevře — vypadalo to, že rozhoduje klávesnice.
- **Stažená nároží se při zvýraznění zakulatí** — zpráva se z bloku vyčlení i tvarem.

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
- **Tvar odesílacího tlačítka nese stav**: mikrofon je „sušenka" z `MaterialShapes`, odeslání
  a uložení úpravy kruh; změna se animuje morfem mezi nimi a ikona se vymění zvětšením. Stejně
  M3 Expressive používá tvar u přepínacích tlačítek. Tlačítko se mění samo od sebe (začne se psát,
  pole se vyprázdní) a skoková výměna ikony se dala přehlédnout; tvar se pozná i koutkem oka.
  Stisk mění jen kruh (na zaoblený čtverec) — mezi obecnými tvary `IconButton` neanimuje. Tvar (`MorphShape`) je nový objekt pro každou hodnotu průběhu. (Že by
  se tvar čtoucí průběh sám nepřekreslil, obecně NEPLATÍ — ořez vrstvy, `background` i kreslení
  čtení stavu v `createOutline` hlídají, viz nároží bubliny v „Recompozice za běhu". U tlačítka
  to vyzkoušené není.)
- **Morf má vlastní, pomalejší pružinu** (`morphSpring`, tuhost 70 místo ~380 u
  `defaultSpatialSpec`). S výchozí doběhl na skutečném telefonu za zlomek vteřiny a nebyl vidět —
  na emulátoru v debugu ano, ten je pomalejší, takže **rychlost animací se posuzuje na zařízení**.
  Toutéž pružinou jede výměna ikony a barva má `slowEffectsSpec`, aby nic nedoběhlo dřív než tvar.
- **Klidová sušenka je vlastní, ne `Cookie9Sided`**: šest vlnek s vnitřním poloměrem 0,7 místo
  devíti s 0,8. S mělkými vlnkami se tvar od kruhu skoro nelišil.
- **Tvary M3 Expressive mají v aplikaci pevné významy** (`ShapeMorphs` v `core/ui/MorphShape.kt`):
  šestistranná sušenka = klid / připraveno (mikrofon, pozastavená hlasovka i video, ikona
  pod taženým chatem či zprávou za prahem tahu), kruh = probíhající akce
  (odeslat, hraje), SoftBurst = živý signál (tlačítko při nahrávání dýchá, tečka v pruhu už
  nebliká — dva blikající prvky vedle sebe byly šum; na okamžik i za emoji čerstvě přidané reakce), dvanáctistranná sušenka = podbarvení
  podržené samolepky a vybraný vzorek barvy motivu — i vlnka stisku je ořezaná do téže sušenky (klikání má vlastní vrstvu,
  samolepka sama ořezaná není, přišla by o rohy), sled tvarů končící zaoblenou hvězdou =
  uvítání prázdného stavu (placeholder detailu, jednou a pak stojí). Kde se stav nemění (avatary, položky seznamu, reakce), se tvary
  nepoužívají: kdyby se hýbalo všechno, přestalo by to cokoli znamenat.
- **Barva odesílacího tlačítka říká, ČEHO se akce týká**: text `primary` (jako odchozí bublina),
  hlas `tertiary`. Hlasovka je doplňková cesta vedle psaní a terciární role je v M3 přesně na to;
  tlačítko je tak stejně výrazné jako odeslání textu (obojí plná role, ne tonální), jen se pozná,
  že udělá něco jiného. Prolíná se spolu s morfem. Tři různé barvy pro tři ikony by z tlačítka
  udělaly semafor, proto se drží dvojice.
  - **Dřív byl mikrofon tonální `secondaryContainer`** („klidový stav prázdného pole"). Tím ale
    bylo tlačítko v prázdném poli to nejméně nápadné na obrazovce, přestože je to jediná akce,
    která se z prázdného pole dá udělat.
  - **Mikrofon a odesílání hlasovky mají barvu STEJNOU.** Mezi „můžu nahrát" a „nahrávám"
    rozhoduje tvar (sušenka ↔ dýchající SoftBurst), vlnka karty a pruh s časem; přebarvit k tomu
    ještě tlačítko by byl čtvrtý signál pro jednu změnu. `SendButtonKind.SendVoice` je proto
    vlastní hodnota, i když kreslí tutéž ikonu jako `Send` — s jediným „Send" by tlačítko při
    spuštění nahrávání probliklo z terciární do `primary` a zpátky.
  - **Karta pole se při nahrávání barví na `tertiaryContainer`**, ne `primaryContainer`: hlas je
    v aplikaci terciární celý, včetně úrovní v pruhu nahrávání (`RecordingBar`).
- **Karta vstupního pole je `surfaceContainerHigh` PŘITÓNOVANÁ do `primaryContainer`**
  (`tintedSurface`, 30 %). V čistě neutrálních surface rolích to byla jediná velká šedá plocha
  na obrazovce a od pozadí konverzace ji dělila jen světlost — v Material You z tapety obzvlášť.
  Plný `primaryContainer` použít nejde: karta by se dostala na světlost odchozí bubliny a pole by
  vypadalo jako rozepsaná zpráva. Barva ikon v kartě (`inputIconColor`) se měří proti TÉHLE ploše,
  ne proti holé roli.
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
- **Ikony přílohy a výběru emoji stojí VEDLE `TextField`, ne v jeho `leadingIcon` / `trailingIcon`.**
  Sloty `TextField` centrují na výšku pole, takže s víceřádkovou zprávou ikony ujížděly doprostřed.
  V řádku se spodním zarovnáním drží u posledního řádku jako odesílací tlačítko; odsazení 4 dp je
  (56 − 48) / 2, tedy střed jednořádkového pole. Barvu mají akcentní — výchozí
  `onSurfaceVariant` byla na `surfaceContainerHigh` karty zašedlá a nečitelná.
- **Akcent pro drobnou kresbu se VYBÍRÁ MĚŘENÍM, ne pevnou rolí** (`strongestAccentOn`
  v `core/ui/AccentColor.kt`, testy `AccentColorTest`). `primary` natvrdo prošla prahem
  čitelnosti a přesto vypadala rozmazaně: v Material You z tapety vyšla na kartě pole kontrast
  5,2–5,3 : 1, zatímco placeholder vedle ní 5,2–7,6 a ostatní ikony v aplikaci přes 10. Ikona
  je obrys s tahem 2 dp a její hrana proti kartě leží skoro celá v BARVĚ, ne ve světlosti
  (modrý kanál 23 proti 241) — a chromatickou hranu vidí oko měkčeji než luminanční, takže se
  tenká čára čte jako neostrá. `accentOn` na tohle nestačí: ten řeší, jestli akcent ZMIZÍ
  (práh AA 4,5), tady jde o to, jak je vidět. Vybírá se proto z dvojice `primary` /
  `onPrimaryContainer` ta, která má na té konkrétní ploše vyšší kontrast — pevná volba by
  v paletě se zvýšeným kontrastem spadla do téže pasti obráceně (`onPrimaryContainer` je tam
  v tmavém režimu TMAVÝ, viz `accentOn`). Naměřeno na emulátoru: 5,2 → 7,9 : 1.
- **„Rozmazané" u ikony NEMUSÍ být render.** Změřeno ze snímku: hrana má jeden pixel
  antialiasingu a podíl přechodových pixelů je stejný jako u šipky zpět, kterou nikdo za
  rozmazanou nepovažuje. Než se sáhne na drawable (velikost, `<group>`, viewport), vyplatí se
  změřit kontrast — dojem neostrosti dělá skoro vždycky barva.
- **Pole je `BasicTextField` s `TextFieldDefaults.DecorationBox`, ne `TextField`.** `TextField`
  má vnitřní odsazení 16 dp napevno a vedle sponky (glyf má v 48dp tlačítku svých 12 dp) začínal
  text skoro 30 dp od ikony. Dekorace bere `contentPadding`: 4 dp u ikony, 16 dp u holé hrany
  karty (úprava zprávy, kde sponka není).
- **Vlnka znamená „teď zní hlas".** Při nahrávání se karta pole rozvlní (`WavyShape`, stojí,
  neobíhá — živost nese dýchající tlačítko) a přebarví na `primaryContainer`; hlasovka
  v konverzaci se rozvlní a vlnky obíhají, jen když HRAJE (`rememberVoicePlaying`). **Stálá vlna
  na hlasovce (zkoušeno) vypadala jako rozbitý okraj** — nic neoznamovala, porušovala pravidlo
  „tvar jen tam, kde se mění stav". Hrající hlasovka si navíc zakulatí stažená nároží jako
  zvýraznění: vlnky vedené přes 4dp nároží v něm dělaly špičku. Hraje-li, čte se přes
  `derivedStateOf` — pozice se mění dvacetkrát za vteřinu a položka se má překreslit jen při
  spuštění a zastavení.
- **Nabídka příloh má vlastní umístění** (`AttachButton`, `AboveAnchorStartPositionProvider`).
  Výchozí umístění M3 menu drží od okraje okna povinný odstup 48 dp (`MenuVerticalMargin`,
  zahrnuto i do volby „spodkem ke kotvě"), a tlačítko u spodku obrazovky v tom pásu leží — menu
  se proto posadilo k oknu, zhruba 45 dp nad tlačítko. `offset` nepomůže, přičítá se ke
  kandidátům PŘED kontrolou. Expressive `DropdownMenuPopup` bere vlastní
  `DropdownMenuPopupPositionProvider` (s `transformOrigin` v rohu u tlačítka), takže vzhled je
  tentýž jako u ostatních nabídek. **Totéž čeká každou nabídku u spodku obrazovky.** Poznalo se
  to změřením (`uiautomator dump`): spodek menu byl přesně `výška okna − 48 dp`.
- **Nabídky si neberou klávesnici** (`MenuPopupProperties` v `core/ui`, výchozí pro
  `ExpressiveMenu`). Výchozí M3 menu je fokusovatelný popup — na Androidu samostatné okno, které
  převzalo fokus od pole; systém klávesnici schoval a animaci insetů dostalo okno nabídky, takže
  hlavní okno dostalo rovnou konečné insety a konverzace skokem spadla dolů. Poznalo se to
  z `dumpsys window | grep mCurrentFocus` (`Pop-Up Window`) a snímků těsně po sobě. Týkalo se
  sponky, podržení odesílacího tlačítka (to klávesnici navíc schovávalo samo), tlačítek dolů
  a ke zmínkám i přepínače chatů na šipce zpět.
  - **Řešení je `FLAG_ALT_FOCUSABLE_IM`, ne `focusable = false`.** Okno fokus dostane, ale
    klávesnici nepotřebuje (jako `PopupWindow.INPUT_METHOD_NOT_NEEDED` u systémových nabídek),
    takže `imeInputTarget` i `imeControlTarget` zůstanou na hlavním okně. Ověří se
    `dumpsys window | grep -E "mCurrentFocus|imeInputTarget"`: fokus `Pop-Up Window`, cíl
    klávesnice `MainActivity`, `dumpsys input_method` drží `mInputShown=true`.
  - **Nefokusovatelné okno (dřívější řešení u sponky) mělo dvě díry**, které se musely lepit
    ručně: nedostane zpět (`NavigationBackHandler`) a dotyk mimo něj projde do okna pod ním —
    ťuknutí na sponku nabídku zavřelo a klik po puštění ji otevřel znovu (pojistka půl
    vteřiny). Fokusovatelné okno obojí řeší samo: zpět zavře jen nabídku (klávesnice zůstane,
    další zpět schová ji), dotyk mimo nabídku ji zavře a dál nejde.
  - Popup bez `ExpressiveMenu` (`DropdownMenuPopup` přepínače chatů a filtrů hledání, odstíny
    emoji nad hledáním) dostává `MenuPopupProperties` ručně.
  - **Sheet po volbě položky je zase další okno** a otevřený hned utrhl klávesnici stejně —
    konverzace nad sheetem skočila. `rememberAfterKeyboardHidden` (`core/ui`) klávesnici schová
    a akci pustí, až insety `ime` dojdou k nule (nejvýš 600 ms). Totéž tlačítko emoji a položky
    nabídky odeslání, které otevírají sheet či dialog (naplánovat, efekt, přepsat); „bez zvuku"
    a „až bude online" pošlou rovnou a klávesnice zůstane jako po obyčejném odeslání. Na
    emulátoru dojede klávesnice za ~320 ms; zbytek prodlevy u emoji je skládání sheetu.
  - **Na iOS ověřené není** (jen přeložené). `MenuPopupProperties` je tam výchozí M3
    (fokusovatelné), sponka tím přišla o dřívější `focusable = false`.
- **Citace a psaní jsou JEDNA karta.** Dva samostatné kusy nad sebou dělaly z odpovědi cizí prvek
  vsunutý mezi konverzaci a vstup; ve společné kartě je vidět, že citace patří k tomu, co se
  právě píše. Citace (i hlavička úpravy) je **horní pruh karty v `tertiaryContainer`**
  (`ComposerHeader`), stejně jako citace na kartě zprávy — ne subkartička v kartě. Pole uvnitř je proto průhledné — kontejner nese karta, ne ono, a dvě zaoblené
  plochy v sobě by vypadaly jako chyba.
- **Pruh pod kartou, pruhy nad konverzací, plovoucí datum, docky a napovídání nad polem jsou
  MLÉČNÉ SKLO** (`Glass.kt`, tentýž Haze jako závoj nabídky nad zprávou). Plochy leží nad
  projíždějícím obsahem a dřív ho jen zakrývaly barvou; rozostření dělá totéž čitelněji — je
  vidět, že pod nimi obsah pokračuje.
  - **Je to JEDEN materiál ve dvou podobách**: pilulka s textem (`pillGlass`; v tmavém motivu
    `surfaceContainerHigh`, ve světlém bílá `surfaceContainerLowest`; hrana `glassRim`, stín 3 dp) a pás pod polem (`bandGlassStyle`, `surface`). Rozostření, matici,
    zesílení barev, zrnitost a krycí podklad mají společné, liší se barvou a únikem jasu. Dřív si
    každá plocha skládala styl sama (deset kopií v `ConversationBars`, dvě u pole) a napovídání
    mělo vlastní inverzní sklo — nad polem visela jiná plocha než nahoře. Nová plovoucí plocha nad
    seznamem bere `pillGlass(listHaze)`, ne vlastní styl.
  - **Lišty MÍSTO pole mají tentýž pás** (`barGlass` v `bottomBar`: Přidat se, Start, Ztlumit,
    věta „nejde psát"). Seznam pod spodní lištu zajíždí, takže bez pásu prosvítaly bubliny
    i sponzorovaná karta zpod tlačítka — dlouho bez povšimnutí, protože pole samo pás má.
    Každá nová náhrada pole dostane `barGlass` taky.
  - **Pás leží pod CELÝM blokem nad konverzací, ne jen pod polem** (sloupec v `bottomBar`:
    návrhy odpovědi, klávesnice bota, `MessageInput`). Návrhy a klávesnice stojí v rozvržení,
    takže pod ně zprávy při odrolování zajedou stejně jako pod pole — dokud mělo sklo jen pole,
    prosvítala pod čipy ostrá příchozí bublina a s čipem (plocha karty pole) vypadala jako dva
    čipy přes sebe. `MessageInput` si dál nese jen pás pro letící fotku
    (`sharedElementOcclusionBand`): přes návrhy a klávesnici se letět nesmí (`openBand`).
    Plovoucí napovídání (příkazy, zmínky, emoji) místo v rozvržení nezabírá a má sklo pilulky.
  - **Hlavička je sklo taky, ale jen v kartě** (`PaneHeader(glass = headerGlass(listHaze))`):
    na telefonu a na jednom panelu, kde je konverzace celá obrazovka, zajíždí seznam pod hlavičku
    stejně jako pod pole a konverzace je nahoře i dole souměrná. Vedle seznamu chatů leží hlavička
    nad kartou na ploše okna, pod ní nic nejezdí a sklo by nemělo co ukázat — `PaneHeader` ho tam
    vynechá sám. M3 sklo nemá; nejblíž je „scroll-under" horní lišty (pod lištou obsah, lišta
    změní plochu), tady místo jiné barvy prosvítá obsah.
    - **Barva je plocha stránky, únik pilulky** (`headerGlassStyle`): v klidu vypadá hlavička jako
      dřív, ale nese jméno a podtitul, takže propouští jen 0,18 / 0,25 jako pilulka, ne 0,28 jako
      pás. Kontrast `onSurface`, `onSurfaceVariant` (AA) i `primary` (≥ 3) hlídá `GlassTest`.
      Bez rozostření (Android 11 a starší) kryje úplně. Hrana ani stín: lišta přes celou šířku je
      pás, ne plovoucí plocha.
    - **Lišta je průhledná přes `paneHeaderContainerColor()`**, tedy i hledání a výběr zpráv —
      `PaneHeader` s předaným sklem hlásí totéž co hlavička nad kartou.
    - **Výšku hlavičky přičítá všechno, co měří od horní hrany seznamu** (`headerInset`
      v `ConversationScreen`): odsazení seznamu nahoře, pruhy a překryvy (vnitřní `Box` s odsazením,
      jeho hrana je `contentTop` pro nabídku nad zprávou), ořez letící fotky (`obscuredTop`), dock
      živé polohy a přehrávače (`barsHeight`), prázdné stavy a načítání. Co bere `contentPadding`
      seznamu (`beforeContentPadding` / `afterContentPadding`), sedí samo; zhlédnutí sponzorované
      karty se proto počítá z odsazení, ne z celého výřezu — pod hlavičkou ani pruhy karta vidět
      není.
  - **Zdrojem je JEN seznam zpráv, ne celý Scaffold** (vlastní `HazeState`). Závoj nabídky
    rozostřuje Scaffold celý, protože je nad ním; tyhle plochy jsou ale jeho součástí, takže by
    se rozostřovaly samy sebou — pruh připnutých by nasál plovoucí datum a pás pod polem sám sebe.
  - **Barvu nese tint stylu, ne plocha.** Kdyby si ji plocha kreslila sama, překryla by rozostřený
    obraz pod sebou; průhledná plocha s tintem dělá sklo. Tvar se proto ořezává zvlášť
    (`clip(shape)` uvnitř `Surface`), jinak by sklo přeteklo přes zaoblení.
  - **Únik jasu je dvojí.** Pás pod polem vlastní text nenese (karta pole má svoji plochu), takže
    propouští 0,28 (dřív krytí 0,72) a sklo je na něm znát. **Pilulky text nesou** (0,18 v tmavém,
    0,25 ve světlém motivu) a pod nimi projede
    i tmavá fotka, ve které by se písmo ztratilo — změřeno, při krytí 0,72 mělo „Včera" nad černým
    náhledem videa kontrast 2,85, tedy hluboko pod AA. Při krytí 0,88 vyšel v tmavém režimu nad
    světlou fotkou 5,89.
  - **Rozostřená vrstva musí být KRYCÍ** (`backgroundColor` ve stylu, `glassStyle`). Bez podkladu
    byla u okrajů plochy průhledná — na šíři poloměru rozostření, a pilulka je vysoká 48 dp proti
    20 dp poloměru — a pod sklem prosvítal OSTRÝ seznam, jen ztlumený tintem. Rozostření tak nebylo
    poznat vůbec, i se sklem „zapnutým". Poznalo se to až pokusem s tintem 0 na desktopu: uprostřed
    rozmazaná bublina, u horní a dolní hrany ostrý text. Podklad v barvě stránky přímo ve zdroji
    (`hazeSource(listHaze).drawBehind { … }`) NEPOMOHL — chybí-li obraz u okraje, musí ho doplnit
    sám efekt.
  - **Kontrast hlídá matice, ne krytí** (`glassStyle`, `backdropMatrix`). Rozostřený obraz se
    napřed stáhne k barvě plochy na 0,3 a zesytí 2,4×, teprve pak přijde tint s krytím 0,6. Jas
    pozadí propouští únik `(1 − 0,6) × stažení`; šedý obsah tak vypadá přesně jako pod holým
    tintem se stejným únikem, barva pod sklem je vidět víc než dvakrát (hlídá `GlassTest`).
  - **Pilulka nesmí mít roli příchozí bubliny** (`surfaceContainerHigh`). V téže roli se přes ni
    slévala — ve světlém motivu ji oddělil jen stín a hrana (ověřeno na desktopu). Ve světlém
    motivu je proto bílá, matné bílé sklo vystoupí z šedých bublin i z namodralé stránky a tmavému
    textu přidá kontrast. V tmavém zůstává
    `surfaceContainerHigh`: světlejší `surfaceContainerHighest` by ubrala kontrast světlému textu,
    který tam v nejhorším případě drží těsně; od tmavé bubliny ji tam odliší hrana.
  - **Únik pilulky je 0,18 v tmavém a 0,25 ve světlém motivu — kontrast se POČÍTÁ, ne odhaduje.**
    `GlassTest` bere skutečné palety (`dynamicColorScheme`, všech osm `ThemeColor`) a nejhorší
    případ: pod celým textem úplně bílá plocha (tmavý motiv), úplně černá (světlý). `onSurface`
    i `onSurfaceVariant` drží AA, primární popisek („Připnutá zpráva") vědomě klesne na 3,7 / 3,5
    (test hlídá ≥ 3). Nad skutečným obsahem (rozostřená bublina, fotka) drží kolem 4,8. Přísné AA
    i u popisku by dovolilo jen 0,18 / 0,15 — a při dřívějších 0,12 prosvítal obsah, zvlášť pod
    bílou pilulkou, jen velmi decentně (srovnáno na desktopu, 11/2026). Ruční odhad z typických
    tónů M3 se od palety lišil až o 1,4 (tmavý motiv 4,4 odhad, 5,7 skutečnost), proto test.
  - **Sklo poznat dávají i zrnitost (0,08) a světlá hrana** (`glassRim`, 1 dp, dolů mizí, za
    `clip(tvar).then(glass)` uvnitř plochy). V tmavém motivu projíždí pod tmavou pilulkou tmavý
    obsah a rozostření samo nemá co ukázat; hrana řekne „sklo" i tehdy.
  - **Poloměr 20 dp, víc než u závoje nabídky (12).** Tam jde o odsunutí celé konverzace do pozadí,
    takže silné rozostření z ní udělá kaši; tady prosvítá pruh vysoký pár desítek dp a při malém
    poloměru je pod ním text ještě čitelný — plocha pak vypadá jen zašpiněně.
  - **Původní krycí pás měl 0,93 a rozostření pod ním nebylo vidět vůbec**: text zprávy končil na
    jeho hraně, jako by ho něco ořízlo. Kdo sklo přidává, musí krytí snížit, jinak přidá jen cenu.
  - **Fallback je ověřený na Androidu 9** (API 28, emulátor): pás pod polem kryje stejně jako
    dřívější krycí plocha (prosvítá zhruba 7 %, tedy beze změny proti stavu před sklem), plovoucí
    datum je úplně krycí a text na něm má kontrast 11,3. Závoj nabídky nad zprávou tam taky jen
    ztmavuje, nerozostřuje.
  - **Na emulátoru se plynulost změřit NEDÁ.** `dumpsys gfxinfo` hlásil 98 % zadrhnutých snímků
    v konverzaci se sklem i v seznamu chatů BEZ něj — měří se tím hostitel pod tlakem paměti, ne
    naše změna. Patří to na zařízení, a hlavně na slabší s Androidem 12.

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
- **Kliknutí jde přes ViewModel, ne rovnou z bubliny do `UrlOpener`.** Odkazy na Telegram
  se tam rozlišují na jednom místě (viz „Odkazy na Telegram"). Vlastní
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
- **Cashtagy se schválně nemapují.** Obarvit je umíme, ale kliknutí by nemělo kam vést — modrý
  text, po kterém se nic nestane, je horší než text černý. Ze stejného důvodu je příkaz bota
  klikatelný jen tam, kam se smí psát (viz „Příkazy bota").
- **Zmínka a hashtag vedou dovnitř aplikace, ne na adresu** (`MessageEntityType.Mention`,
  `MentionUser`, `Hashtag`; v `RichText` jako `LinkAnnotation.Clickable` s vlastní obsluhou
  `onEntityClick`, mimo `onLinkClick`). `@jméno` najde chat přes `searchPublicChat`, zmínka
  uživatele bez jména (`TextEntityTypeMentionName`) nese jen `userId` a vede na
  `createPrivateChat`. Chat se otevře NAD konverzací (`onOpenChat`), takže zpět vede do ní;
  zmínka téhož chatu (kanál ve vlastním příspěvku) nikam nevede. Veřejný chat, kde uživatel
  není, dostane „Přidat se" jako z globálního hledání.
- **Hashtag otevře hledání v seznamu chatů s `#tag`** (`AppNavigation.openSearch`,
  `searchRequest`). Požadavek je STAV, ne událost — seznam chatů na telefonu pod konverzací
  složený není a vyzvedne si ho, až se složí. Na telefonu se zásobník Chatů srovná na kořen,
  v dvoupanelu konverzace zůstane. Efekt, který hledání rozbalí, požadavek nuluje AŽ NAKONEC —
  vynulování mění jeho klíč a na začátku by zrušilo rozbalování (tatáž past jako „Vrátit").
- **Hashtagový dotaz hledá jen zprávy** (`isHashtagQuery`). Hledání chatů i veřejných chatů
  mřížku zahodí, takže „#plume" vracelo kanály „Plumex" a „plume" — šum nad tím, co uživatel
  hledal.
- **U hashtagu jde přepnout na veřejné příspěvky kanálů** (`HashtagSourceToggle` nad výsledky,
  `MessageSearchFilter.publicPosts`, `searchPublicMessagesByTag`; pravidlo `searchesPublicPosts`,
  testy `ChatSearchTest`). **Přepínač, ne druhá sekce pod zprávami**: obě jsou stránkované
  a sekce pod nekonečnými zprávami by se k vlastní další stránce nedostala. Volba jede ve
  filtru, protože filtr už teče celou cestou (`onFilterChange`), nuluje se se sbalením hledání
  a stránka se k němu přiřazuje. **Druh chatu a období se s veřejnými příspěvky schovají
  a vynulují** — jsou to vždycky kanály a období API nebere; zůstat skryté ve filtru by nesměly,
  prázdný výsledek by radil „změň filtr", který není vidět. **Obsah je, co vrátí server**:
  u `#telegram` i `#kotlin` na emulátoru převážně spamové kanály — tak to vidí i oficiální
  klienti. Ověřeno: přepnutí oběma směry, dotahování stránek; otevření kanálu z výsledku
  nezkoušeno (jde toutéž cestou jako nalezená zpráva).
- **Zmínka a hashtag nejsou podtržené — kromě odchozí bubliny.** Podtržení patří adresám, které
  z aplikace odvedou. V odchozí bublině je ale barva odkazu barva textu (na `primary` jiná
  nejde), takže tam se podtrhávají, jinak by nebyly k poznání (`linkColor == overlayColor`).
- **Spoiler musí být opravdu nečitelný**: průhledný text na plné ploše, ne jen jiná barva.
  Odkrývá se ťuknutím přes `LinkAnnotation.Clickable` — `Modifier.clickable` by platilo pro celou
  zprávu, ne pro skrytý úsek. Odkrývá se celá zpráva naráz; dohledávat spoilery po kouskách
  je otrava.
- **Citace v textu je BLOK ZA TEXTEM, ne styl písma** (`MessageEntityType.Quote`,
  `drawQuoteBlocks` v `RichText.kt`, testy `QuoteRangesTest`). `SpanStyle.background` umí jen
  hranatý obdélník po řádcích — tatáž past jako u zvýraznění hledaného slova —, takže se plocha
  kreslí v `drawBehind` z `TextLayoutResult`, stejně jako pilulky zvýraznění. Blok jde napřed,
  pilulka na něj: hledané slovo musí být vidět i uvnitř citace.
- **Plocha, ne proužek vlevo.** Proužek je jazyk M2 a v aplikaci už jednou ustoupil ploše
  (náhled odkazu, citace nad odpovědí).
- **Pás od kraje ke kraji, ne zaoblená plocha uvnitř.** Zaoblený obdélník odsazený od okraje
  bubliny byl karta v kartě — přesně to, co už jednou musela opustit citace nad odpovědí
  („subkartička v bublině") i náhled odkazu („bez spár, části uvnitř jsou rovné"). Náprava je
  pokaždé stejná: **tvar nese vnější karta a díly uvnitř jsou rovné a přes celou šířku.** Pás
  proto přetéká za text přesně o vnitřní odsazení bubliny, nemá vlastní zaoblení a na nároží ho
  ořízne tvar bubliny.
- **Tónuje se INKOUSTEM, ne container rolí.** `tertiaryContainer` (`insetColor`) uvnitř bubliny
  byla druhá karta a na odchozí `primary` bublině navíc cizí barva. Podklad se proto odvozuje
  z barvy popředí (`onBubbleColor` s alfou), stejně jako podklad kódu — ten má tutéž poznámku
  o „cizím tělese" v `RichText` od začátku.
- **Na okraj bubliny pás dosedne, jen když nad textem (pod ním) opravdu nic nestojí**
  (`quoteBleedTop` / `quoteBleedBottom`, podává je bublina). `drawBehind` se na hranice textu
  neořezává, takže uvnitř `RichText` se nedá poznat, jestli výš není značka přeposlání nebo
  hlavička citované zprávy, přes jejichž šev pás přelézt nesmí. Bez toho zůstal nad citací přes
  celou zprávu proužek barvy bubliny a vypadalo to jako lem.
- **Odsazení, přesah i výška pásu berou TÁŽ čísla jako ostatní díly zprávy** (`MESSAGE_PADDING_H`
  14 dp, `MESSAGE_PADDING_V` 8 dp v `MessageDimens.kt`; `RichText` je dostává parametrem, aby
  `core/ui` neznalo rozměry bubliny). Bylo to opsané v pěti souborech — bublina, citace nad
  odpovědí, popis webu — a u pásu musí sedět PŘESNĚ: do stran přetéká o vnitřní odsazení karty,
  takže dosedne na její okraj, a svisle odsazuje text stejně jako díl nad ním.
- **Citace přes CELÝ text zprávy se odsazuje `padding`em, ne odstavcem** (`quoteIndentAsPadding`).
  Compose ellipsizovaný poslední řádek zalomí na plnou šířku a teprve pak ho posune o odsazení
  odstavce: konec i s výpustkou přeteče za okraj, `Text` ho ořízne a sbalená citace pak končí
  uprostřed slova a bez „…". S `padding`em se zalomí správně a výpustka je vidět. Uvozovky se
  kreslí do žlábku, který tím vznikne, tak jako tak — `drawBehind` měří vnější box i s ním.
- **Písmo citace je o kousek menší** (`QUOTE_FONT_SCALE`, 0,94 `em` — v `em`, aby platilo pro
  každý styl). **Kurzíva ne:** projekt nemá kurzivní řez (v `composeResources/font/` jsou jen
  přímé), takže by ji platforma dopočítala sklonem a u víceřádkové citace je to vidět.
- **Odsazení textu uvnitř zprávy dělá `ParagraphStyle(textIndent)`, a stojí to prázdný řádek.** Odstavcový
  styl nad rozsahem rozdělí text na odstavce na hranicích citace — a přilehlé `\n` tím přestanou
  být zalomením a stanou se prázdným řádkem (odstavec končící `\n` má řádek navíc, stejně jako
  odstavec začínající `\n`). U citace uvnitř delší zprávy je proto nad pásem i pod ním jedna
  volná řádka. **Jinak to nejde**: odstranit ta `\n` z textu by rozhodilo offsety všech entit,
  zvýraznění i spoilerů, a rozpad na víc `Text`ů zase jejich výpočet. Vypadá to jako vzduch kolem
  citace, takže se to nechává; kdyby vadilo, cena za tichý tvar je zrušit odsazení.
- **Visící odsazení položek seznamu v článku stejnou cestou NEJDE** (zkoušeno při přechodu na
  Compose 1.13, desktopovým snímkem z `runComposeUiTest` + `captureToImage`). Pokusy a výsledky:
  - `ParagraphStyle(textIndent = TextIndent(0, šířka odrážky))` na každou položku zvlášť: odsazení
    sedí (šířka předpony změřená `TextMeasurer`em), ale **mezi každými dvěma položkami je prázdný
    řádek** — totéž co u citace, jen tady na každém řádku seznamu.
  - Rozsah položky i s koncovým `\n` (nebo s úvodním): prázdný řádek zůstává, odstavec končící
    i začínající `\n` má řádek navíc.
  - Jeden `ParagraphStyle` přes celý seznam: na Skia se `firstLine` uplatní jen na první řádek
    celého rozsahu, další položky dostanou `restLine` i na prvním řádku (odrážka odsazená).
  - `Bullet` / `withBulletListItem` (ui-text 1.13) stojí na témže `ParagraphStyle` — vzorové
    použití skládá položky BEZ `\n` a zalomení dělá hranice odstavce.
  - **Fungovalo by** nahradit ve VYKRESLENÉM textu `\n` na hranici položky znakem nulové šířky
    (U+200B, stejná délka, offsety entit i zvýraznění se nehnou) — hranice odstavce zalomí sama.
    Jenže `SelectionContainer` kopíruje vykreslený text a citace z výběru (`quoteSelection`)
    ho porovnává s textem zprávy: výběr přes víc položek by ztratil zalomení a citace by se
    nenašla. Kdyby se to mělo udělat, musí se znak vracet na `\n` v obou cestách.
- **Odsazení se počítá z dp (`Dp.toSp()`), ne ze sp.** Lícuje s uvozovkami vedle sebe, ne
  s velikostí písma; `toSp()` počítá s `fontScale`, takže výsledek sedí na dp v každém nastavení.
- **Uvozovky se kreslí v `drawBehind` do žlábku, ne jako `InlineTextContent`.** Inline obsah se
  musí vejít do řádku a posouval by první slovo; ve žlábku, který si citace stejně dělá
  odsazením, nic neposouvá.
- **Sbalitelná citace** (`Quote.expandable`) se ořízne na tři řádky a pod pásem dostane
  rozbalovač na běžné ploše bubliny — citace je vrchní část zprávy, rozbalovač je ovládání k ní.
  **Sbalí se jen citace, která je CELÝ text zprávy** (`collapsesQuote`, testy `MessageRowsTest`):
  `maxLines` platí pro celý `Text`, takže by ořízl i větu pod citací, a rozdělit zprávu na víc
  `Text`ů nejde (zvýraznění, spoilery a dlouhý stisk nad odkazem se počítají nad jedním layoutem).
- **Rozbaluje a sbaluje i ťuknutí na samotnou citaci**, ne jen na rozbalovač — tak to dělá
  i oficiální klient a tři řádky jsou mnohem větší cíl než jeden řádek popisku. Bez vlnky:
  odezva je samo rozbalení, a vlnka by se kreslila v obdélníku kolem textu, který se s pásem
  (ten jde od kraje bubliny) nekryje. Dlouhý stisk se opakuje, jinak by nad citací nešla otevřít
  nabídka nad zprávou.
- **Popisek rozbalovače má zvýrazněný řez** (`labelLargeEmphasized`). V ODCHOZÍ bublině je akcent
  barva bubliny, takže `accentOn` ustoupí barvě textu a rozbalovač by se od zprávy lišil jen
  šipkou — tatáž úvaha jako u podtržení odkazů tam, kde barva splývá.
- **Rozbalovač má nad sebou DVOJNÁSOBNÉ odsazení** (2 × `MESSAGE_PADDING_V`). Pás si svoje
  spodní odsazení kreslí POD posledním řádkem citace, takže první polovina padne na něj a teprve
  druhá je mezera, kterou je vidět; s jedním odsazením seděl rozbalovač na okraji pásu (změřeno:
  5,7 dp nad popiskem, 15,2 dp pod ním). Dotykové odsazení se od toho neodečítá — leží i pod
  popiskem, a k němu ještě spodní odsazení bubliny.
- **Že se text nevešel, se pozná až z layoutu** (`hasVisualOverflow` přes `onOverflow`) — a po
  rozbalení se vejde vždycky. Kdyby se rozbalovač řídil jen aktuálním přetečením, po rozbalení by
  zmizel a nešlo by sbalit zpátky; ROZBALENÁ citace proto drží, co bylo.
- **Sbalená citace ale bere POSLEDNÍ měření, ne kterékoli.** Čerstvě odeslaná zpráva se poprvé
  měří v jiné šířce (přilétá z pole) a to měření přetečení nahlásí i u jednoho řádku. Dokud se
  příznak jen zapínal („stalo se to"), zůstalo u krátké sbalitelné citace „Zobrazit víc" viset,
  dokud se chat neotevřel znovu. **Obecně: příznak odvozený z layoutu, který se jen zapíná,
  zakonzervuje i měření, které platilo jeden snímek.** Ověřeno dočasným logem v `onTextLayout`:
  „Kratka citace" se při odeslání změřila v omezení 195 px místo 232 a ohlásila přetečení výšky,
  všechna další měření ne. Na emulátoru pak s opravou krátká citace po odeslání rozbalovač nemá,
  dlouhá (od bota) ho má a rozbalí se i sbalí.
- **Při odesílání se entity dohledávají** (`getTextEntities`), jinak odkaz odejde jako mrtvý text —
  server si ho sám neoznačí. Když dohledání selže, zpráva stejně odejde.

## Pruhy a přepínače M3 Expressive

- **Pruh proti spamu a oznámení o chatu jsou sklo** (`ChatBarSurface` v `SpamBarCard.kt`):
  mléčné sklo se stínem 3 dp a okrajem 12/6 dp jako pruh připnutých a žádostí. Neprůhledná karta
  se stínem mezi skleněnými pruhy vypadala jako cizí prvek. Zaoblení `LargeIncreased`, ne
  pilulka — pruh má víc řádků. Akce jsou malá tónová tlačítka (`ChatBarButton`), akce PROTI
  protějšku (Zablokovat, Nahlásit) v `errorContainer`.
- **Emoji / Samolepky / GIFy jsou spojená skupina přepínacích tlačítek**, ne segmentová tlačítka
  (M3 Expressive je nahrazuje právě touhle skupinou). Vybraný režim má fajfku a rozšíří se
  (váha 1,4) — se stejnou šířkou česky uřízlo „Samolepky". Pod záložkami emoji a sad samolepek
  není dělicí čára (`divider = {}`).
- **Odeslat ankety a seznamu úkolů je split button** (`SendSplitButton`): nabídka (bez zvuku,
  naplánovat) za šipkou, ne za dlouhým stiskem, o kterém nic nenapovídalo. U uložení upraveného
  seznamu nabídka není, je to obyčejné tlačítko.
- **Ztlumení kanálu je `FilledTonalToggleButton`** (do material3 1.12 `TonalToggleButton`) ve velikosti medium: ztlumený kanál ho má vyplněné
  a zakulacené, ikona ukazuje stav (přeškrtnutý zvonek = ztlumeno). Ověřeno na Pixelu 5.
- **Předěl „Nepřečtené zprávy" je tónová pilulka** (`secondaryContainer`, `labelMediumEmphasized`),
  ne šedý text jako předěl dne — je to jediná značka „odsud čti".
- **Sheety v konverzaci jsou bloky zaoblených řádků** (informace o zprávě, Poslat jako, jazyk
  překladu, výběr člena při hledání). Volba režimu přílohy a náhledu odkazu berou
  `groupItemShape(index, count, selected)` přímo — ruční kopie neuměly vyčlenit vybraný řádek.

- **Plovoucí tlačítka nad polem mění tvar při stisku** (`FloatingButtonSurface`: dolů, @ / ❤,
  „Označit jako přečtené"). Kruh (pilulka) → zaoblení 28 % výšky, jako ikonová tlačítka. Byla to
  jediná tlačítka v aplikaci bez odezvy tvarem. Zaoblení v procentech, takže kruh i pilulka
  s počtem reagují stejně; pružina přestřelí jen nahoru a strop je 50 %. U tlačítka @ / ❤ nese
  klikání obsah (`contextClickable` kvůli podržení), takže dostává tentýž `interactionSource`.
  Ověřeno na Pixelu 7 snímkem během `input motionevent DOWN`.

- **Kategorie pod hledáním samolepek jsou tónová přepínací tlačítka** (`FilledTonalIconToggleButton`,
  `toggleableShapes`, malé široké), ne obrysové `FilterChip`y: výběr jedné ikony z řady je v M3
  Expressive přepínač, který se vybraný vyplní a zaoblí — jako formátování a hovor.

- **Hledání emoji, samolepek a GIFů je `ControlledSearchPill`** (`core/ui/SearchPill.kt`),
  tatáž pilulka jako hledání v celé aplikaci, jen řízená zvenku: výběr kategorie samolepek pole
  vymaže. Text drží Compose stav obrazovky (`rememberSaveable`), ne `StateFlow` z ViewModelu —
  s ním by kurzor poskakoval (proto má běžná `SearchPill` text u sebe). Vlastní
  `StickerSearchField` (TextField v Surface) zmizel.

## Barva konverzace

Profil chatu → Barva konverzace: „Podle aplikace" a tytéž vzorky jako barva aplikace
(`ChatColorSheet` nad `ThemeColorPicker`). Uložená po účtech (`ChatColors`,
`AppSettings.chatColors`, jako odložené chaty) a **mezi zařízeními ji nese synchronizace přes
úložiště bota** (`CloudSync`, skupina `ChatColors`; `docs/poznatky/nastaveni.md`,
„Synchronizace nastavení mezi zařízeními"). Barva APLIKACE se naopak nesynchronizuje: kamaráda
modře chce uživatel vidět všude, tmavý desktop a telefon podle tapety jsou volba zařízení.

- **Mění se CELÉ schéma, ne vybrané plochy** (`ChatColorTheme` kolem konverzace, seznamu témat,
  profilu a bubliny konverzace). Z barvy se dopočítá celá M3 paleta (`chatColorScheme`),
  takže bubliny, pole, hlavička, sklo i plocha spolu ladí
  a kontrast `on*` rolí hlídá M3 — nic se neladí ručně, `accentOn` i `senderColor` se přizpůsobí
  samy. Neutrální plochy palety nesou nádech odstínu, takže chat pozná i podle pozadí.
- **Proč ne tapeta Telegramu ani automatika z profilovky.** Obrázková pozadí (`Chat.background`)
  aplikace odmítá vědomě (roadmapa konverzace, „Vědomě NE"). Barva odvozená automaticky by dostal
  KAŽDÝ chat a přestala by cokoli znamenat — stejná úvaha jako u haptiky. Barvu, kterou dal
  uživatel pár chatům, si spojí s významem („práce", „rodina").
- **Paleta chatu je TonalSpot, ne Vibrant jako aplikace.** Vibrant v tmavém motivu dával zelené
  a tyrkysové primární tón skoro neonový: odchozí bublina svítila víc než cokoli jiného
  v aplikaci (ověřeno na emulátoru, snímky v PR). TonalSpot (výchozí styl Material You) drží
  odstín s umírněnou sytostí — chat vypadá jako aplikace s jinou tapetou. Vzorky ve výběru se
  kreslí z téhož stylu (`ThemeColorPicker(schemeOf = …)`), takže se liší od vzorků barvy
  aplikace v Nastavení, a tak to má být: vzorek ukazuje, co výběr opravdu dá.
- **Výběr je jen z osmi vzorků**, žádné kolo barev: ošklivá kombinace nejde vyrobit.
  „Podle aplikace" je neutrální vzorek s přeškrtnutou kapkou — kreslit ho barvami aplikace nejde,
  protože kolem je už paleta chatu a vzorek by se od modré nelišil.
- **V seznamu chatů nese barvu JEN odznak nepřečtených** (`rememberChatAccents`: paleta jedna na
  barvu, ne na řádek — řádky se při scrollu skládají pořád znovu). Celý řádek v barvě by udělal
  ze seznamu mozaiku; nepřečtený chat je navíc ten, u kterého na rozlišení záleží.
- **Konverzace se skládá rovnou v barvě chatu** (`ChatColors.current()` jako počáteční hodnota
  odběru). S `initial = null` by první snímek měl barvy aplikace a prolnutí k barvě chatu by při
  každém otevření vypadalo jako bliknutí.
- **Návrat k barvám aplikace míří na CÍLOVÉ schéma** (`LocalAppColorScheme` z `AppTheme`), ne na
  `MaterialTheme.colorScheme`: to se během přepnutí motivu prolíná a je v každém snímku nová
  instance, takže by se cíl vnořené animace měnil pořád. Režim (světlý/tmavý) dává
  `LocalIsDarkTheme` — ze světlosti plochy se během prolínání poznat spolehlivě nedá.
- **Výběr sheet nezavírá**: profil i sheet leží v barvě chatu a přebarví se hned za prstem — to je
  náhled.
- **Ověřeno na emulátoru** (Pixel 7, Android 16): výběr, okamžité přebarvení profilu i sheetu,
  konverzace ve světlém i tmavém motivu, návrat na „Podle aplikace". Odznak v seznamu chatů
  ověřený NENÍ — testovací chat s botem měla otevřený jiná session a zprávy se hned četly.
- **Kontrast se zvýšeným kontrastem systému NEsleduje**, stejně jako barva aplikace ze seedu —
  materialkolor dostává výchozí úroveň kontrastu. Barvy z tapety ho mají; když se to bude řešit,
  pak pro obě palety naráz.

## Odkazy na Telegram

Odkaz ze zprávy (text, náhled odkazu) jde přes `ConversationViewModel.openLink` →
`ChatRepository.resolveLink`. Co odkaz znamená, rozhoduje TDLib (`getInternalLinkType`) — zná
všechny podoby (`t.me`, `telegram.me`, `tg://`) a my jen vybíráme, co umíme ukázat
(`InternalLink`). Ostatní jde do prohlížeče.

- **Rozpoznání je lokální volání**, takže běžný webový odkaz jde do prohlížeče bez prodlevy.
  Neinterní odkaz TDLib hlásí CHYBOU — to je `null` (prohlížeč), ne selhání.
- **Chat (`t.me/jméno`) se otevře nad konverzací** jako zmínka; zpět vede do konverzace.
- **Odkaz na zprávu** (`getMessageLinkInfo`) v tomhle chatu skočí na zprávu na místě
  (`jumpToMessage`, s dojetím / přejezdem), v jiném chatu otevře `ConversationRoute(chatId,
  messageId)`. Zprávu, kterou server nevydal (`message == null`), nahradí otevření chatu.
- **Pozvánka nikoho nepřidá sama** — ukáže náhled (`InviteSheet`: fotka, název, počet členů /
  odběratelů, popis) a přidává se až tlačítkem. `checkChatInviteLink` s nenulovým `chatId`
  znamená, že chat je dostupný už teď — otevře se rovnou.
- **`joinChatByInviteLink` vrací `ChatJoinResult`, ne chat** (novější TDLib): úspěch, odeslaná
  žádost (pozvánka se schválením → snackbar „Žádost odeslána"), odmítnutí, schválení botem.
  `when` je bez `else`, takže nový druh v novější TDLib rozbije překlad, ne tiše přidávání.
- **Neplatná pozvánka chodí jako `INVITE_HASH_EXPIRED`** (i u vymyšleného odkazu) — má vlastní
  větu v `userMessage`.
- **Sada samolepek** (`t.me/addstickers/…`, `searchStickerSet`) otevře tentýž sheet jako ťuknutí
  na samolepku.
- **Proxy** (`t.me/proxy`, `t.me/socks`) se jen nabídne dialogem — viz „Proxy".
- **Člověk podle čísla (`t.me/+420…`) a kontaktní odkaz (`t.me/contact/…`)** jdou přes
  `searchUserByPhoneNumber` / `searchUserByToken` a `createPrivateChat` (lokálně — bez něj by
  TDLib chat neznal). Neexistující číslo vrátí `404 Not Found` a má vlastní větu
  (`LinkUserNotFoundException`), ne obecné „odkaz nejde otevřít": uživatel z ní pozná, že
  odkaz je v pořádku a jen ten člověk na Telegramu není (nebo číslo skrývá).
  Prošlý kontaktní odkaz má větu vlastní (`ContactLinkExpiredException`). Kontaktní odkaz na
  člověka mimo kontakty otevře profil s „Přidat do kontaktů" (viz „Kontaktní odkaz do profilu"
  v `hledani-a-zakladani-chatu.md`). Tentýž odkaz
  z jiné aplikace nebo z čtečky QR kódů otevírá shell, ne konverzace — viz „Odkazy z jiných
  aplikací (`LinkOpenRequests`)" v `hledani-a-zakladani-chatu.md`.
- **`?text=…` u chatu se jen VLOŽÍ do pole, nic se neodesílá** — text napsal autor odkazu. Jde
  přes `ShareRequests` s vybraným chatem (`ShareTarget(confirm = false)`), tedy cestou
  sdíleného textu: připojí se za rozepsaný koncept, a to až po jeho obnovení. Vlastní „request"
  holder by zdvojil přesně tu logiku. Shell sdílení s vybraným chatem nikam nenaviguje, takže
  chat otevírá dál konverzace (`openTarget`); odkaz na TENTÝŽ chat vloží text rovnou.
- **`?profile` otevře profil nad konverzací** (`ConversationNavEvent.OpenProfile`), text z odkazu
  se pak zahodí — patří chatu, ne profilu.
- **`t.me/share/url?url=…&text=…` je sdílení bez vybraného chatu** (`shareRequests.start`): shell
  otevře výběr chatu sám, jako u sdílení ze systému. Adresu i text TDLib složí do jednoho textu.
- **Odkazy na obrazovky aplikace** (`InternalLink.Screen`, `AppScreen`, `AppNavigation.openScreen`):
  `tg://settings[/sekce]` do Nastavení po krocích (zařízení, soukromí → Obecné, složky, úprava
  profilu, notifikace, vzhled; sekce, které Plume nemá — jazyk, úložiště, Premium — na kořen),
  `tg://new` a `tg://contacts` do Nové zprávy, `tg://new/group` a `tg://new/channel` do
  založení, `tg://chats/search` do hledání. Nastavení se otevře jako z menu účtu, nová konverzace
  nad otevřeným chatem — zpět vede do něj. Odkaz na videochat otevře chat (pruh vysvětlí, že
  připojit se jde jen v oficiální aplikaci). **Podoby odkazů určuje TDLib** a hádat se nevyplatí:
  `tg://newgroup` ani `tg://settings/edit_profile` nepozná (správně `tg://new/group`,
  `tg://settings/edit`) — seznam je v `td/telegram/LinkManager.cpp` (`get_internal_link_impl`).
- **Přihlašovací odkazy se z ťuknutí neotevírají** (`InternalLink.LoginWarning`,
  `LoginLinkWarningDialog`): kód (`t.me/login/…`, `tg://login?code=…`) i přihlášení jiného
  zařízení (`tg://login?token=…`). Dřív šly do prohlížeče, a ten je mohl předat oficiálnímu
  klientovi — přesně to, co podvodník chce. Dialog řekne proč a u QR kódu, že se jen skenuje
  v Nastavení → Zařízení (TDLib takové upozornění předepisuje). Ani mini aplikace je neotevře.
- **Ověřování přes `adb`: poslední zpráva v chatu s botem leží pod panelem jeho klávesnice.**
  Ťuknutí na ni trefilo panel a výsledek vypadal jako chyba odkazu; panel se napřed schová
  („Schovat klávesnici bota").
- **Ověřeno na emulátoru** odkazy od testovacího bota: `?text=` v témže chatu, `?profile`,
  `t.me/share` do Uložených zpráv, neexistující číslo (hláška) a vlastní číslo (Uložené zprávy
  s textem). **Kontaktní odkaz ověřený NENÍ** (chce token jiného účtu), Uložené zprávy z odkazu
  taky ne (bot `tg://` odkaz v textu neoznačí).

## Čas v médiu

„1:23" v textu (`TextEntityTypeMediaTimestamp` → `MessageEntityType.MediaTimestamp`) pustí
médium od toho místa: video v prohlížeči, hlasovku, skladbu a kolečko přímo v chatu
(`MessageAction.PlayMediaAt`, `ConversationViewModel.playMediaAt`).

- **Kam čas míří, říká TDLib**: médium téže zprávy (popisek videa), nebo zprávy, na kterou
  odpovídá (`Message.hasTimestampedMedia` = `false`). Pravidlo je čistá funkce
  `timestampTarget` (testy `MediaTimestampTest`); citovaná zpráva mimo načtenou historii se
  bere z dotažených citací.
- **Entitu vyrábí TDLib i u příchozích zpráv sám** — bot poslal holý text „Tady 0:02" jako
  odpověď na video a entita v něm byla. Server ji neposílá, takže se Bot API ověřit dá.
- **Video se otevře rovnou na celé obrazovce a hraje** (`MediaRoute.startSeconds`,
  `NO_START` = běžné otevření). V galerii by ho uživatel musel pustit sám a čas by nic neudělal.
  Přeskočí se hned po načtení (`seekTo` v `VideoPage`), u streamovaného videa taky.
- **Hlasovka a skladba jdou přes `VoicePlayer.play(startMs = …)`**; čas za koncem (překlep
  u krátké hlasovky) se ignoruje, jinak by přehrávač hned skončil. Kolečko se zvukem si start
  bere z `ConversationState.soundingVideoNoteFromMs`.
- **Klikatelný je čas jen v konverzaci** (`RichText(timestampsClickable)`), jinde (bio, popis)
  zůstane textem — vedl by nikam.
- **Video v náhledu odkazu se otevře na webu od toho místa** (`videoLinkAt`, testy
  `MediaTimestampTest`): YouTube dostane `t=43s` v dotazu (stávající `t` se přepíše), Vimeo kotvu
  `#t=43s`, jiný web holou adresu — přehrávač na něm neznáme a hádat parametr nestojí za to.
  V aplikaci se cizí video nepřehrává. Video pozná náhled podle druhu od serveru
  (`LinkPreview.isVideo`: `LinkPreviewTypeVideo` a `EmbeddedVideoPlayer`), u článku nebo fotky
  čas nevede nikam. Ověřeno botem: odpověď „Refrén je od 0:43" na zprávu s odkazem na YouTube
  otevřela aplikaci YouTube s `…&t=43s` (vidět v `capturedLink` v logcatu — `START` adresu
  zkrátí na `https://youtube.com/...`).
- **Ověřeno na emulátoru botem**: popisek videa, odpověď na video (prohlížeč na celé obrazovce
  od 0:02), odpověď na skladbu, na hlasovku (m4a přes `sendVoice`) a na kolečko — vše hraje
  od 0:02. **Neověřeno**: iOS (jen přeložené).

## Datum a čas v textu

Datum a čas, které pisatel označil (`TextEntityTypeDateTime` → `MessageEntityType.DateTime`),
se ukazují v místním pásmu a v jazyce aplikace: „středa 30. září 2026 14:23", „30. 9. 2026",
„před 2 hodinami" (`dateTimeText` v `core/ui/DateTimeText.kt`, testy `RelativeTimeTest`).

- **Text se v řetězci NENAHRAZUJE, kreslí se místo něj inline obsah** (`dateTimeRanges`
  v `RichText`), stejně jako vlastní emoji. Oficiální klienti text entity nahrazují, a to by
  posunulo offsety všech entit za ní, zvýraznění hledání i spoilerů. Inline obsah se do řádku
  vejde jen ve své velikosti, takže se formátovaný text změří (`rememberTextMeasurer`)
  a placeholder dostane jeho šířku. Datum se tím nezalomí uprostřed — přejde na další řádek celé.
- **Formátuje se v kompozici, ne při převodu zprávy**: jazyk se může změnit a relativní čas
  stárne. Počítá se vůči okamžiku složení, po vteřinách se nepřepočítává.
- **Relativní čas se zaokrouhluje, ne ořezává** (`relativeTime`): s ořezem vyšly 1 h 59 min
  jako „před 1 hodinou" — a hodiny telefonu o pár vteřin pozadu z „před 2 hodinami" udělaly
  hodinu. Co se zaokrouhlí na celou vyšší jednotku, přejde do ní („1 hodina", ne „60 minut").
  Česky jsou „za" a „před" vlastní plurály (`relative_in_*`, `relative_ago_*`): po „za" je
  4. pád, po „před" 7. — tvary délky (`duration_*`) jsou v 1. pádu a nehodí se.
- **Den v týdnu uprostřed věty má vlastní řetězce** (`weekday_in_text_*`). `day_long_*` jsou
  nadpisy předělů s velkým písmenem; česky se ve větě píše malým, anglicky velkým, takže
  `lowercase()` nestačí.
- **Bez formátu** (`formattingType == null`) zůstává text pisatele — tak to chce TDLib.
- **Kopírování, citace a náhled v seznamu chatů berou text pisatele**, ne formátované datum.
- **Ověřuje se botem**: Bot API umí entitu `date_time` s `unix_time` a `date_time_format`
  (`r` relativní, `w` den v týdnu, `d`/`D` krátké/dlouhé datum, `t`/`T` krátký/dlouhý čas).
  Offsety jsou v UTF-16 — s ručně spočítaným offsetem nahradí entita i sousední znak. Ověřeno
  na emulátoru anglicky i česky. **iOS jen přeložené.**

## Zpráva pro čtečku obrazovky

Bublina je pro TalkBack jeden uzel: text, jméno, čas a stav doručení a vlastní akce Odpovědět
a Reagovat (`MessageA11y.kt`, testy `MessageA11yTest` a headless `MessageA11ySemanticsTest`).

- **Jeden uzel je bublina už díky `contextClickable`** — klikání slučuje potomky. Fotka, přehrávač,
  odkaz a další potomci s vlastním klikáním zůstávají samostatné uzly, takže se k nim čtečka
  dostane dál. `clearAndSetSemantics` na bublině by je odřízl.
- **Popis se NESKLÁDÁ do `contentDescription`**: na sloučeném uzlu by přebil text zprávy (čtečka
  bere popis místo textu). Čas a stav jdou do `stateDescription`, čtou se za textem.
- **Jméno u navazující příchozí zprávy ve skupině** (`a11ySender`) — viditelně ho nese jen první
  bublina bloku. Jde do `text` uzlu: ten se slučuje SEZNAMEM a rodič je první, takže zazní před
  textem zprávy (ověřené headless testem, ne předpoklad). U první bubliny bloku ne, zaznělo by dvakrát.
- **Akce Odpovědět** jen tam, kam se smí psát (`canReply`), **Reagovat** je rychlá reakce
  z Nastavení → Chaty (popisek s jejím emoji, u vlastního emoji bez něj). Jinou reakci nabízí
  nabídka nad zprávou — popisek dlouhého stisku je teď „Možnosti zprávy". V režimu výběru akce
  nejsou: ťuknutí tam vybírá.
- **Odznak doručení pod bublinou je pro čtečku tichý** (kromě vykřičníku s „Poslat znovu"):
  stav už řekla bublina a odznak by ho zopakoval jako další položku.
- **Předěl dne a „Nepřečtené zprávy" jsou nadpisy** (`heading()`), takže se mezi dny dá skákat
  navigací po nadpisech. U ťukatelného předělu (kalendář) leží na nápisu samém, ne na obalu
  `CenteredNote` — obal je pro čtečku prázdný uzel. Časový předěl uvnitř dne nadpis není, jen
  čas.
- **Příchozí zprávu ohlásí živá oblast** (`NewMessageAnnouncer`, testy `NewMessageAnnouncerTest`):
  „Nová zpráva od Jana: ahoj". Bublina se do seznamu jen tiše vloží a TalkBack o ní neřekne nic.
  - Jen zpráva, která opravdu přišla: novější než dosud nejnovější a seznam přitom končil
    u přítomnosti (jako ukotvení dole, `rememberNewMessages`). Dotažená stránka, vlastní
    a servisní zprávy ne; z dávky naráz (album) jen nejnovější.
  - Ohlášení dělá ZMĚNA popisu uzlu s `liveRegion`. Uzel nese text 5 s a pak zůstane prázdný:
    trvalý popis by čtečka při procházení obrazovky četla jako další položku.
  - **Uzel nesmí být ZAKRYTÝ jiným uzlem.** Compose dává čtečce jen nezakryté uzly (podle
    pořadí kreslení). První verze seděla v levém horním rohu plochy a volala se před seznamem:
    logika proběhla (log), ale uzel ve stromu přístupnosti nebyl — přikryla ho horní lišta
    a seznam. Volá se proto POSLEDNÍ v `ConversationContent` a sedí uprostřed (1 dp, nic
    nekreslí).
  - **Ověřeno na emulátoru bez TalkBacku**: po zprávě od Notifikátoru ukázal `uiautomator dump`
    uzel s popisem „Nová zpráva od Notifikator: …" a po 6 s byl prázdný. Že ho TalkBack opravdu
    přečte, ověřené není (viz další bod).
  - Ohlašuje se i při čtení historie (bez posunu seznamu): je to jediná zpráva o tom, že přišlo
    něco nového, odznak „N nových zpráv" na tlačítku dolů je vidět jen očima.
- **TalkBack na emulátoru prakticky ověřit nejde**: injektované dotyky z `adb input` průzkum
  dotykem obchází (fokus se nepohne) a „zobrazit výstup řeči" je volba uvnitř TalkBacku, kterou
  bez roota nenastavíš. Sémantika je proto ověřená headless testem; skutečný TalkBack na telefonu
  ověřený NENÍ.

## Sbalení dlouhé zprávy

Text zprávy delší než 20 řádků (`COLLAPSED_TEXT_LINES`) se sbalí a pod ním je „Číst dál",
po rozbalení „Sbalit" (`LongText` v `items/BubbleMessageItem.kt`, pravidlo `collapsesLongText`,
testy v `MessageRowsTest`). Rozbalovač je týž jako u sbalitelné citace (`QuoteExpander`),
jen s vlastními popisky a bez dvojitého odsazení — pod textem žádný pás není.

- **Že se nevešla, říká layout** (`onOverflow`), ne počet znaků — záleží na šířce bubliny,
  velikosti písma a zalomení. Rozbalená drží, co bylo, a sbalená bere POSLEDNÍ měření (stejné
  pasti jako u citace).
- **Ořez bez výpustky** (`TextOverflow.Clip`, nový parametr `overflow` v `RichText`
  a `MessageText`). Android výpustku na posledním řádku nad `maxLines` dělá ZÁMĚNOU znaku,
  i když se řádek vejde: „Radek20" se ukázal jako „Radek2…" a vypadal jako překlep.
  Pokračování ohlásí rozbalovač.
- **Rozbalení se dorovná** (`bringIntoViewRequester`). Seznam je obrácený a drží spodní hranu,
  takže zpráva roste NAHORU: rozbalovač zůstane na místě, nad ním se objeví konec zprávy a řádek,
  u kterého čtení skončilo, odjede z obrazovky. Po rozbalení se proto požádá o místo ořezu (dva
  řádky kolem); seznam dorolluje co nejméně, takže když je vidět, nestane se nic.
  - **O horní `contentPadding` seznamu výš** (`LocalConversationTopInset` z `ConversationContent`):
    seznam zarovná obdélník k SVÉ hraně, a ta leží pod lištou a pruhem připnutých. Bez toho
    skončily řádky u ořezu právě pod nimi.
- **Nesbaluje se článek** (má „Číst celý článek") **a zpráva s nálezem hledání** — nález by mohl
  ležet ve schované části a skok na něj by ho neukázal.
- **Ověřeno na emulátoru** v testovací skupině: zpráva o 30 a o 60 řádcích sbalená na 20
  s „Číst dál", rozbalení té krátké bez posunu, té dlouhé s místem ořezu pod pruhem připnutých,
  „Sbalit" na konci zprávu vrátí. Kratší zpráva (18 řádků) se nesbalí. Sbalení podpisu u fotky,
  velké písmo zpráv a desktop ověřené nejsou.

## Profilovky ve skupině

Ve skupině má cizí zpráva vlevo tvář odesílatele, aby se odesílatelé nerozlišovali jen jménem.

- **Tvář je u POSLEDNÍ zprávy bloku**, ostatní zprávy bloku mají na jejím místě mezeru
  (`SenderAvatar.Spacer`). Bez mezery by bubliny téhož člověka skákaly do stran.
- **Poslední = novější soused je o index NÍŽ** (seznam jde od nejnovější). Blok končí jiným
  odesílatelem, jinou stranou, jiným dnem nebo servisním oznámením.
- **Kanál tvář nemá** (odesílatel = chat sám): u každého příspěvku by byla tatáž a nese ji
  hlavička.
- **A nemá ani jméno nad příspěvkem.** Kanál je pro TDLib taky `ChatTypeSupergroup`, takže
  `ChatInfo.isGroup` je u něj `true` — a jméno kanálu stálo nad každým příspěvkem, zatímco tvář
  schovala jiná podmínka (odesílatel = chat). O jménu i tváři proto rozhoduje `isGroup`
  v repository jako „skupina a NE kanál". Kdo se ptá na „skupinu", musí se rozhodnout, jestli
  kanál myslí taky.
- **Jméno i tvář jdou z jednoho `resolveSender`** (s cache), ne ze dvou dotazů.
- **Tvář je mimo posouvaný `Box`** — při tažení na odpověď jede jen bublina.
- **Zarovnaná ke spodní hraně bubliny.** U dlouhé zprávy by uprostřed nebylo poznat, kam patří.

## Podpis autora a štítek člena

Příspěvek kanálu s podpisy má v řádku pod sebou, před počty, ikonu člověka a jméno autora
(`ChatMessage.authorSignature`, `PostStatsRow`). Ve skupině stojí za jménem odesílatele
ztlumeně jeho štítek (`ChatMessage.senderTag`, `SenderName`).

- **Podpis znamená dvě různé věci podle chatu.** V kanálu je to autor příspěvku a patří do
  řádku pod příspěvkem; ve skupině ho nese anonymní správce (odesílatel = chat) a je to jeho
  titul, takže patří k JMÉNU jako štítek. Rozhoduje převod zprávy podle `isGroup`, ne UI.
- **Štítek člena je `Message.senderTag`** (TDLib 15; „custom title" správce ve stavu člena už
  není, viz „Profil chatu"). Kreslí se jen tam, kde se kreslí jméno — tedy ve skupině, u cizí
  zprávy a na začátku bloku.
- **Jméno i štítek mají váhu BEZ vyplnění** a oba se zkracují: s vyplněním by si dlouhé jméno
  a štítek rozdělily řádek napůl i tam, kde by se vešly celé.
- **Boosty odesílatele** (`Message.senderBoostCount`, `ChatMessage.senderBoostCount`): mezi
  jménem a štítkem blesk v barvě jména, počet až od dvou — „1" by opakovalo, co říká samotný
  blesk (jako počet u čipu reakce). Blesk se nezkracuje; čtečka obrazovky dostane „Boosty
  skupině: N". Jen ve skupině a u cizí zprávy, jako jméno.
- **Řádek pod příspěvkem se kreslí i bez počtů, když je podpis** (prázdné `PostStats`).
- **Podpis u přeposlané zprávy** („Kanál (Autor)") dělá `withSignature` odjinud, z původu.
- **Ověřeno na emulátoru sondou** (necommituje se): zprávy od testovacího bota začínající
  `#podpis` / `#stitek` / `#boost` převod dočasně obohatí o podpis a počty, resp. o jméno se štítkem
  a jiného odesílatele (jinak by zpráva navázala na blok a jméno by se nekreslilo). Skutečný
  kanál s podpisy ani skupina se štítky na testovacím účtu nejsou.

## Kontrola faktů

Příspěvek, ke kterému server přidal poznámku nezávislých ověřovatelů (`Message.factCheck`), má
pod sebou kartu „Kontrola faktů": ikona s nadpisem, text poznámky s odkazy a věta, že ji
doplnili ověřovatelé (se zemí, pro kterou platí). `ChatMessage.factCheck`, `FactCheckCard`.

- **Karta visí pod zprávou, ne v bublině** — přes `WithBelowMessage` (dřív `WithBotKeyboard`),
  stejně jako tlačítka bota, a nad nimi. **Zaoblení má jako bublina** (`BUBBLE_CORNER`), stejně
  jako varování před podvodem a navrhovaný příspěvek; každá měla vlastních 16 dp a pod bublinou
  s 18 dp to nesedělo. Pod holou fotkou žádná bublina není, a karta tak
  vypadá stejně u textu, fotky i alba; šířku má aspoň jako zpráva.
- **Terciární role**, jako popis webu u náhledu odkazu: je to cizí vložený obsah, ne slovo
  autora. Věta o ověřovatelích je nutná — bez ní by se poznámka dala číst jako dodatek kanálu.
- **Poznámku server přidává a mění až po zveřejnění** a přijde jen `UpdateMessageFactCheck`;
  `editedMessages` ho proto odebírá vedle úprav obsahu.
- **Přidávat kontroly faktů (`setMessageFactCheck`) smí jen ověřovatelé** — v aplikaci to není.
- **Ověřeno na emulátoru sondou** (necommituje se): zprávy od testovacího bota s `#fakta`
  převod dočasně obohatí o poznámku s odkazem a zemí; karta pod textem i pod fotkou s popiskem.
  Skutečný příspěvek s kontrolou faktů testovací účet nezná.

## Náhled odkazu

Pod textem se zprávou s odkazem se kreslí náhled: web, titulek, popis a obrázek.

- **Náhled skládá server**, ne klient (`MessageText.linkPreview`). Nic se nestahuje kromě
  obrázku, který k němu server přiložil — žádné připojení k cizí stránce z aplikace.
- **Obrázek jen z druhů, které nesou fotku**: článek, fotka, obal videa, náhled vloženého
  přehrávače, a u odkazu na Telegram profilovka chatu (kanál, skupina, člověk, bot, boost) nebo
  obrázek mini aplikace (`previewPhoto` v `MessageContent.kt`). Ostatní druhy (hlasovka,
  dokument…) zůstanou textové. **Úplně prázdný náhled se zahodí** — rámeček bez obsahu vypadá
  jako chyba načítání.
- **Odkaz na Telegram (`t.me/…`) říká, kam vede** (`LinkPreview.target`, `LinkPreviewTarget`,
  testy `LinkPreviewTargetTest`). Je to v Telegramu nejčastější odkaz a jeho náhled byl dřív jen
  text — `toDomain()` bral obrázek z pěti druhů stránek a profilovku chatu (`ChatPhoto`) zahodil.
  - **Profilovka je v kruhu a nikdy velká** (`roundPhoto`, `showsLargePhoto`): přes celou šířku
    karty by byla portrétem, ne poznávací značkou chatu.
  - **Pod popisem značka, co ťuknutí otevře** („Zobrazit kanál", „Napsat zprávu", „Otevřít
    bota", „Zobrazit samolepky"…), tentýž tvar jako „⚡ Rychlé zobrazení" (`PreviewMark`).
    Značka, ne tlačítko: otevírá ťuknutí na celý náhled, a to už do Plume vede
    (`resolveLink`). **Boost značku nemá** — `InternalLinkTypeChatBoost` Plume v aplikaci
    neotevře a značka by slibovala něco, co se nestane.
  - **Sada samolepek ukáže samolepky** (`stickers`, nejvýš `LINK_PREVIEW_STICKERS`), statickým
    náhledem `StickerPreview` jako obálky ve výběru — animace by běžely v každé zprávě s odkazem.
    Server v náhledu posílá často jen jednu.
  - Náhled uvnitř bubliny (`LinkPreviewCard`) bere popis z `PreviewInfo` jako karta; dřív ho měl
    opsaný a značky by mu chyběly.
  - Ověřeno na emulátoru: Notifikátor poslal `t.me/telegram` (kanál), `t.me/kotlin_lang`
    (skupina), `t.me/BotFather` (bot) a `t.me/addstickers/HotCherry`; ťuknutí na sadu otevřelo
    její sheet. Odkaz na člověka bez kanálu, mini aplikaci, zprávu a příběh ověřené nejsou.
- **Velký nebo malý obrázek určuje odesílatel** (`showLargeMedia`), jen se to respektuje.
- **Vzhled: karta ze dvou částí** (viz „Náhled odkazu jako karta"), v bublině tónovaný
  kontejner jako citace. Akcent se podává zvenku ze stejného důvodu jako u citace — v odchozí
  `primary` bublině by `primary` zmizela.
- **Ťuknutí jde přes `onLinkClick`** jako odkaz v textu; **dlouhý stisk se opakuje**, jinak by
  náhled nabídku nad zprávou spolkl.
- **Náhled nad textem** (`showAboveText`) je přání odesílatele, jen se respektuje — jako velikost
  obrázku. S ním se otáčí i to, který díl karty je poslední: **spodní odsazení nese ten, pod kterým
  už nic není** (počítá se v něm s místem na čipy reakcí). **Značka přeposlání zůstává nahoře
  vždycky** — je to údaj o zprávě, ne o odkazu, a pod náhledem by vypadala jako jeho popisek.
- **Náhled jde z bubliny ven do vlastní karty** — viz „Náhled odkazu jako karta".

## Soubory v chatu

Zpráva se souborem (`MessageMedia.Document`) má v bublině ikonu, název a velikost; ťuknutí soubor
stáhne a předá systému.

- **Stahuje se až na ťuknutí.** Soubory bývají velké a odběr `FileRepository.file()` sám spouští
  stahování — držet ho kvůli zobrazení by znamenalo stáhnout všechno, co kdo poslal.
- **Průběh je ve stavu podle `fileId`**, ne podle zprávy: tentýž soubor může být v chatu víckrát
  a bublina alba kreslí víc řádků. Druhé ťuknutí během stahování nic nespustí znovu.
- **Velikost se formátuje bez jednotky** (`formatFileSize`, testy v `FileSizeTest`); jednotku
  pojmenuje až UI z prostředků, protože pořadí čísla a zkratky se může lišit podle jazyka.
  Desetinné místo jen pod deset jednotek — „137,2 MB" je šum.
- **Desetinný oddělovač patří jazyku, ne číslu.** `FileSize` nese celou část a desetiny ZVLÁŠŤ
  a spojuje je až `fileSizeText` (`core/ui`) formátovacím řetězcem `decimal_number` — česky
  čárka, anglicky tečka, stejně jako „1,5×" u rychlosti hlasovek. Dokud `formatFileSize`
  vracel hotové „1.4", stála tečka i v české aplikaci — u souborů v chatu i v nastavení modelu
  („2.4 GB"). **Text velikosti skládá jen `fileSizeText`**; dřív si ho skládaly tři obrazovky
  a mapování jednotek bylo ve dvou kopiích.
- **Android: `ACTION_VIEW` přes `FileProvider`.** Od Androidu 7 je `file://` URI mimo aplikaci
  zakázané (`FileUriExposedException`). Provider `${applicationId}.documents` vydává adresář
  `tdlib-files` (a `cache/outgoing` u čerstvě odeslaných příloh), ale **oprávnění se uděluje jen
  na konkrétní URI** (`FLAG_GRANT_READ_URI_PERMISSION`). `FLAG_ACTIVITY_NEW_TASK` je nutný,
  protože se otevírá mimo naši úlohu.
- **Provider MUSÍ být vlastní třída** (`DocumentFileProvider : FileProvider()`), ne druhá
  deklarace `androidx.core.content.FileProvider` v manifestu. Providery se v procesu drží podle
  JMÉNA TŘÍDY, takže dvě deklarace téže třídy sdílejí jednu instanci — a ta zná jen tu autoritu,
  se kterou vznikla. Vedle provideru na obrázky do notifikací tedy požadavek na `…documents`
  spadl ještě uvnitř naší aplikace na `SecurityException: The authority … does not match the one
  of the contentProvider: ….notificationimages` a volající (Fotky) z toho ukázal jen „media not
  found". Každá další autorita proto potřebuje vlastní podtřídu.
- **`FILE_PROVIDER_PATHS` patří do manifestu i u vlastní třídy.** `getUriForFile` si strategii
  skládá z meta-dat, ne z konstruktoru — bez nich spadne na „Missing FILE_PROVIDER_PATHS
  meta-data".
- **Příznak v intentu některým příjemcům NESTAČÍ.** `FLAG_GRANT_READ_URI_PERMISSION` platí pro
  spuštěnou aktivitu, jenže aplikace jako Fotky soubor otevírají z jiné své části, která na grant
  z intentu nedosáhne — v logu je „Permission Denial: opening provider … that is not exported"
  a uživatel vidí „media not found". Proto se oprávnění ještě výslovně udělí každému, kdo intent
  zvládne (`queryIntentActivities` + `grantUriPermission`) — tatáž past jako u čteček notifikací
  u obrázku ve zprávě. Předchozí grant se při dalším otevření odebírá, aby nezůstal viset
  napořád — **ale jen když jde o jiné URI**. Odebrání u téhož souboru si vezme zpátky oprávnění,
  které jsme právě vydali, takže **první otevření prošlo a každé další hlásilo „media not found"**.
- **Ťuknutí musí umět stahování spustit ZNOVU** (`FileRepository.retryDownload`). `file()` si
  pamatuje, na co už request odešel, aby ho scroll nespouštěl při každém odběru; kdyby ale
  předchozí stahování skončilo v půli (spadlé spojení, TDLib soubor uvolnil), zůstal by ten stav
  viset a další ťuknutí by nedělalo nic. Výslovný pokyn uživatele tu paměť obchází.
- **Druhé ťuknutí stahování ZRUŠÍ i v TDLib** (`FileRepository.cancelDownload` →
  `cancelDownloadFile`), v konverzaci i v záložce Soubory profilu. Dřív se rušilo jen čekání
  v aplikaci: kolečko zmizelo, ale TDLib stahoval dál a soubor se potichu dostáhl. Zrušení
  zapomene i to, že se o soubor žádalo, takže další ťuknutí naváže na stažený kus. Ověřeno
  47MB souborem od bota: zrušeno ve 4. vteřině na 12,3 MB, po 20 s pořád 12,3 MB
  (`getFile` … `stahuje=false`); bez zrušení je soubor na emulátoru celý za 15 s.
- **iOS: `UIDocumentInteractionController`** (náhled, a z něj i sdílení). Kontroler i delegát se
  musí držet silnou referencí, dokud je náhled na obrazovce — stejná past jako u výběru příloh.
  Bez náhledu pro daný typ se ukáže nabídka „Otevřít v…".
- **Když soubor nemá čím otevřít, musí to jít vidět.** `FileOpener.open` vrací `false`
  a konverzace na to má snackbar; jinak by ťuknutí vypadalo, že nic neudělalo.
- **Obrázek a video poslané jako soubor s náhledem jsou VELKÉ, jako fotka** (`LargeDocumentPreview`,
  pravidlo `showsLargePreview` / `loadsOriginalPreview`, testy `MessageRowsTest`). Jdou na plochu
  bez bubliny (`showsBareMedia`), rámeček podle poměru stran náhledu (`photoFrame`; rozměry souboru
  TDLib u dokumentu nedává) a pod snímkem je vždycky název a velikost (`DocumentCaption`) — bez
  nich by soubor vypadal jako fotka. Ťuknutí otevře prohlížeč (s letem snímku), co ten neumí,
  systém s průběhem přes snímek; nabídka nad zprávou zůstává (Sdílet, Uložit jako…).
  - **Originál se stáhne sám jen u obrázku do stropu souborů podle sítě** (Běžně 5 MB, viz
    `prohlizec-medii.md`, „Automatické stahování"), který umí prohlížeč — jinak
    zůstane náhled od serveru (do 320 px), video vždycky. Ověřeno: 223 kB ostrý originál, 6,2 MB
    náhled, video se symbolem přehrání.
  - **Server náhled nevyrobí vždycky**: HEIC nikdy, a botem poslaný JPEG o 13 a 22 MB taky ne
    (6,2 MB ano). Bez náhledu zůstává řádek s ikonou — není z čeho vzít poměr stran.
  - **Citlivý soubor a album souborů zůstávají řádky.** Řádek umí rozostřit a odkrýt; album
    souborů je seznam, ne mřížka.
  - **S textem zprávy snímek využije celou šířku a ořízne se na střed**, jako fotka s popiskem
    (`fillWidth`); jen s řádkem názvu ne — ten má každý soubor a roztahoval by každý náhled.
- **Obrázek a video poslané jako soubor BEZ velkého náhledu mají v řádku náhled** místo ikony (`DocumentThumbnail`),
  video navíc symbol přehrání; bez náhledu aspoň ikonu podle druhu (`documentKind`, testy
  `DocumentKindTest`). Druh rozhoduje **přípona před `mimeType`** — ten posílá odesílatel
  a bývá `application/octet-stream` — a obojí se porovnává se seznamem formátů, ne s prefixem
  `image/`: TIFF, RAW ani PSD neotevřeme a ikona fotky by slibovala něco, co ťuknutí nesplní.
- **Obrázek a video poslané jako soubor se otevírají v prohlížeči médií**, jen když je platforma
  opravdu umí (`opensInViewer`, platformní `viewerUnsupportedFormats`): HEIC dekóduje až
  Android 9 a Skia v iOS ne, WebM a MKV přehraje ExoPlayer, AVPlayer ne. Co prohlížeč neumí, jde
  systému jako každý jiný soubor — ťuknutí, které skončí prázdnou stránkou, je horší.
- **V prohlížeči je soubor sám, bez listování a bez „N z M".** Listuje se přes
  `SearchMessagesFilterPhotoAndVideo`, kam dokument nepatří, takže načtení výřezu zprávu
  nenajde a prohlížeč spadne do stávající zálohy pro jednu zprávu. Pozice od TDLib by se
  počítala mezi fotkami, proto se u souboru zahazuje (`single`). Stránku staví `asViewerMedia`:
  soubor je ostrá verze, náhled okamžitý obrázek, rozměry z náhledu. Bez náhledu jsou
  nulové, karta je čtverec a obrázek se do ní vepíše (`Fit`), neořízne.
- **Náhled dokumentu vyrábí server, ale ne vždy** (ověřeno botem, `sendDocument`): JPEG i MP4
  ho dostanou, i s vypnutou detekcí typu, HEIC ne. Průběh stahování jde přes náhled bíle na
  závoji — akcent bubliny by na fotce zanikl.
- **MP4 bez zvuku poslané jako soubor se z Telegramu vrátí jako GIF** (`MessageAnimation`), ne
  jako dokument. Kdo chce testovat video-soubor, musí poslat soubor se zvukem, nebo
  `disable_content_type_detection=true` (`screenrecord` z emulátoru zvuk nemá).
- **Sdílet a Uložit jako… má soubor v nabídce nad zprávou** (`MessageMenuAction.ShareFile`
  a `SaveFileAs`, kandidáti jen u `MessageMedia.Document`, podle `canBeSaved`). Soubor, který
  obrázkem ani videem není, se do prohlížeče nedostane, takže ven jde odsud. Stahování je
  společné s otevřením (`withDocument`): stejný průběh v řádku, stejné hlášky. „Uložit jako…"
  potřebuje výběr umístění, takže ji z bloku zachytí obrazovka (`ScreenItemScope`) a ViewModelu
  předá `SaveAsLauncher`; na iOS se nenabízí (`saveAsSupported`).
- **Položka, jejíž práce trvá, musí nabídku zavřít SAMA** (`closeMenus()`). Nabídka se po
  ťuknutí sama nezavírá — to dělá každá akce ve ViewModelu. Bez toho zůstala otevřená přes
  celé stahování a hláška o výsledku zapadla pod závojem.
- **Odesílaný soubor se otevřít nedá** — ukazuje průběh nahrávání a ještě není kde.

## Přeposlaná zpráva

Nad obsahem přeposlané zprávy stojí „Přeposláno od …" s šipkou (`ForwardLabel` v `MessageFrame.kt`,
doména `ChatMessage.forwardOrigin` / `ForwardOrigin`). Ťuknutí vede ke zdroji: příspěvek kanálu
otevře kanál se skokem na původní zprávu (`ChatTarget(chatId, messageId)`), uživatel soukromý chat.

- **Původ jsou čtyři druhy a jen tři vedou někam** (`MessageOrigin*`). Skrytý uživatel
  (`MessageOriginHiddenUser`) nese jen jméno — zakázal si v soukromí odkaz na účet — takže se
  u něj značka nedá ťuknout. `when` je bez `else`: nový druh v novější TDLib rozbije překlad.
- **Značka je součást téže karty jako obsah, ne text nad ní.** Přeposlání je údaj O ZPRÁVĚ;
  volně nad holou fotkou (první verze) vypadala jako popisek něčeho jiného. V bublině je prvním
  řádkem, u holého snímku horní díl karty v barvě bubliny (`ForwardHeader`, protějšek popisku
  pod snímkem — `MediaWithCaption` má proto hlavičku, snímek a popisek se stejnou šířkou),
  a u karty odkazu v horním dílu s textem.
- **Přeposlaná samolepka, kolečko a pár emoji si bublinu vynutí** (`needsCard` v `messageItem()`,
  testy `ConversationItemsTest`), stejně jako odpověď. Pravidlo je obecné: **holý obsah jde na
  plochu jen sám; jakmile nese údaj navíc (popisek, citace, přeposlání), je z něj karta.** U snímku
  je kartou snímek s hlavičkou (citace, značka) a popiskem, u samolepky bublina. Samostatný
  štítek nad samolepkou (zkoušeno) byl třetí, vymyšlený tvar vedle bubliny a karty — a při revizi
  v listopadu 2026 se k němu málem vrátilo, proto to tu stojí výslovně.
- **Značka je u KAŽDÉ zprávy bloku**, ne jen u první jako jméno odesílatele. Přeposlané zprávy
  téhož člověka bývají každá odjinud.
- **Jméno zdroje jde z keše odesílatelů** (`resolveSender`, bere uživatele i chat). V kanálu,
  který přeposílá z pár zdrojů pořád dokola, je to dotaz na zdroj, ne na zprávu. Neznámé jméno
  (smazaný účet, chat, který TDLib nezná) = „Přeposlaná zpráva", ne značka pryč.
- **Barva je barva popředí plochy, ztlumená; jméno nese řez.** Barva zdroje by se musela hlídat
  proti každé ploše (bublina, zvýrazněná bublina, plocha konverzace pod holou fotkou), viz
  `accentOn`. Jméno se do šablony vkládá přes zástupný znak, aby šlo zvýraznit řezem a pořadí
  slov zůstalo na překladu.
- **Značka má vlastní klikání, takže opakuje dlouhý stisk** (`combinedClickable` s
  `requestMenu`) — jinak by nad ní nešla otevřít nabídka zprávy. Tatáž past jako u citace.
- **Ověřuje se botem**: `forwardMessage` z chatu s botem do téhož chatu vyrobí přeposlanou zprávu
  s původem „uživatel". Kanál jako zdroj bot bez přístupu ke kanálu nepřepošle.

## Počet členů v hlavičce

Skupina má v podtitulu hlavičky „128 členů, 12 online", kanál „3 400 odběratelů"
(`ChatRepository.groupMembers`, `ConversationState.members`). Dřív neměla skupina podtitul žádný.

- **Počet supergroupy se bere z plného info** (`getSupergroupFullInfo`, živě přes
  `updateSupergroupFullInfo`): `Supergroup.memberCount` je podle dokumentace známé jen u chatů
  z některých seznamů (podobné kanály, hledání…), jinak nula. Základní skupina má počet
  spolehlivě v `BasicGroup`.
- **Online pošle TDLib jen otevřenému chatu** (`updateChatOnlineMemberCount` po `openChat`)
  a klidně hned — dřív, než konverzace stihne začít poslouchat. Proto ho `TdlChatRepository`
  odebírá od startu do mapy po chatech a konverzace z ní jen čte.
- **„Online" až od dvou**: TDLib počítá i uživatele samotného, „1 online" by znamenalo „ty".
  U kanálu se online neukazuje vůbec.
- **Podtitul má pořadí**: výpadek spojení, název skupiny ve vlákně / tématu, přímé zprávy,
  dostupnost protějšku a teprve pak členové — ve vlákně komentářů tedy dál stojí, ve které
  skupině se diskutuje.
- **Ověřeno na emulátoru**: veřejná skupina @tdlibchat bez přidání „9 037 členů, 249 online",
  kanály @durov a @bbcrussian s počtem odběratelů, vlastní testovací skupina nejdřív
  „1 člen" ze zastaralého plného info a po jeho obnovení (otevřel ho profil) sama
  „2 členové" — update `updateSupergroupFullInfo` hlavičku přepíše bez znovuotevření.

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
- **Odkaz reklamy jde cestou odkazů z textu** (`ConversationLinks.openLink`), ne rovnou do
  prohlížeče: odkaz do Telegramu (`t.me/…`, `tg://…`) se otevře v aplikaci a webová adresa
  mimo domény Telegramu až po potvrzení v dialogu. Seznam domén bez potvrzení je ze
  specifikace (`isTrustedSponsorUrl`, testy `TrustedSponsorUrlTest`); hostitel smí mít jen
  písmena, číslice, tečky a pomlčky, takže `https://t.me@cizi.example` (přihlašovací údaje
  v adrese) ani port neprojdou. Platí pro kanál i bota — dřív šel odkaz z karty v kanálu
  rovnou do prohlížeče, i když vedl na kanál v Telegramu.
- **Formátování textu (`entities`) se kreslí** (`RichText`), ale odkaz v textu je taky ťuknutí
  na reklamu: kliknutí se musí nahlásit a cíl určuje reklama.
- **⋮ s nahlášením jen u reklamy s `can_be_reported`** — specifikace nahlášení jiné nedovolí.
- **„Informace o sponzorovi" jsou v ⋮** (`SponsoredMenu`), kdykoli Telegram pošle `sponsor.info`
  (kdo reklamu platí) nebo `additional_info` — specifikace je pak chce nabídnout povinně.
  Dialog (`SponsorInfoDialog`, `core/ui/SponsorInfo.kt`) je společný pro kartu v kanálu, pruh
  u bota i reklamu ve videu. Dřív `sponsor.info` chybělo úplně a `additional_info` stálo
  drobným písmem v kartě; teď je obojí na jednom místě a karta je kratší. Bez nahlášení
  i bez informací ⋮ chybí (`SponsoredMessage.hasMenu`, podle něj má pruh u bota okraj).
  **Žádná reklama v kanálech ověřených na emulátoru (BBC Russian, Meduza, RIA, TASS, Durov)
  informace nenesla** — sponzorované chaty v hledání ano („Advertiser…", „Ad funded by…").
  Volba a dialog ověřené s podstrčeným `sponsor.info`.
- **Prázdný seznam je normální stav**, ne chyba: reklamy se servírují jen v dost velkých veřejných
  kanálech a některých botech, nikdy uživatelům s Premium, a závisí na dostupné inzerci.
- **Nahlásit reklamu jde ⋮ vedle štítku „Sponzorováno"** (`reportChatSponsoredMessage`).
  Průvodce je TENTÝŽ `ReportFlow` a `ReportSheet` jako u chatu, jen druhá instance
  (`sponsoredReportFlow`) s jiným voláním; id reklamy jede jako jediná zpráva požadavku.
  Výsledek překládá `ReportSponsoredResult.toReportStep`, společný se sponzorovanými chaty
  v hledání (`hledani-a-zakladani-chatu.md`).
  Server se zeptá na důvod a mezi důvody bývá i „skrýt reklamy" — bez Premium skončí větou
  „Skrýt reklamy jde jen s Telegram Premium" a reklama zůstane. Pro takové konce má průvodce
  krok `ReportStep.Ended` (vlastní hláška, a jestli se má spustit, co čeká na nahlášení).
- **Nahlášená reklama z konverzace zmizí** (i po skrytí reklam a u reklamy, kterou server už
  nezná), jako v oficiálním klientovi. Skrýt reklamu sám od sebe klient nesmí; po nahlášení
  uživatelem je to odpověď na jeho akci, ne zásah do zobrazení.
- **Ověřeno na emulátoru** (kanál @bbcrussian bez odběru): ⋮ → „Nahlásit reklamu" otevře
  průvodce s důvody od serveru („What's wrong with this ad?", anglicky i v české aplikaci —
  text dodává server), volba „I don't want to see ads" bez Premium skončí hláškou o Premium
  a reklama zůstane. **Skutečné nahlášení ověřené není** — šlo by moderátorům na cizí reklamu.

### Reklama s médiem

Reklama v kanálu nese `MessageContent` jako běžná zpráva a **v praxi bývá fotka s popiskem**,
ne text. Repository dřív bralo jen `MessageText` („reklamy jsou v praxi text") a karta pak
ukazovala jen štítek, jméno a tlačítko — obsah reklamy chyběl celý. Zjištěno dočasným výpisem
v @bbcrussian (22. 10. 2026): obě reklamy „News Wire" byly `MessagePhoto` s popiskem.

- **Text je text, nebo popisek média** (`quotableText`), médium jen fotka, video a GIF
  (`sponsoredMedia`) — jiná specifikace reklam nezná.
- **Pořadí v kartě je ze specifikace**: štítek, jméno, text, médium, tlačítko. Médium kreslí
  `MessageAttachment` přes celou šířku karty (`fillWidth`), GIF hraje sám jako v bublině.
  Nabídka nad médiem (dlouhý stisk) není — reklama není zpráva chatu, nahlášení má ⋮.
- **Ťuknutí na médium se hlásí `isMediaClick = true`** a chová se podle druhu
  (`SponsoredMessage.mediaOpensLink`): fotka a GIF (= video bez zvuku) otevřou odkaz, video
  jde na celou obrazovku a odkaz se NEOTEVŘE. Tak to specifikace rozlišuje pro „videos with
  sound". `Video` z TDLib příznak zvuku nenese, takže se dělí podle typu: GIF je MP4 bez
  zvuku, video se bere jako video se zvukem.
- **GIF reklamy na ťuknutí neodpauzuje, ale otevře odkaz** (`AnimationBubble(onOpen = …)`);
  velký GIF, který se sám nestáhl, první ťuknutí stáhne jako v bublině.
- **Video reklamy jde do prohlížeče médií** (`MediaRoute(sponsored = true)`). Reklama není
  v historii chatu, takže ji prohlížeč načte znovu ze seznamu reklam (`loadSponsored`); TDLib
  ho drží pět minut v keši a vrátí tutéž reklamu se stejným id bez dotazu na server. Stránka
  je jediná a bez akcí (jako příloha ankety), místo data štítek „Sponzorováno".
- **Pod médiem v prohlížeči je tlačítko reklamy** (`SponsoredButtonBar`, na místě pruhu
  reakcí). Kliknutí se hlásí `fromFullscreen = true` (v `appScope` — prohlížeč se hned
  zavírá), prohlížeč se zavře a odkaz předá konverzaci pod sebou (`SponsoredLinkRequests`,
  stav jako `ConversationReplyRequests`): otevírání odkazů (do aplikace, cizí web po
  potvrzení) žije v konverzaci, ne v prohlížeči.
- **Ťuknutí na video na celé obrazovce odkaz neotevírá**, ačkoli specifikace by ho chtěla
  hlásit s oběma příznaky a otevřít odkaz: ťuknutí v prohlížeči skrývá ovládání a přehrávač,
  odkaz nese tlačítko. Kdyby to vadilo, patří to do `VideoPage(onTap)` jen u reklamy.
- **Ověřeno na emulátoru** (@bbcrussian, česky): karta s popiskem a fotkou, ťuknutí na fotku
  nahlásilo `isMediaClick` (`Ok`) a otevřelo mini aplikaci inzerenta (dotaz na otevření,
  zrušeno). Cesta videa ověřená dočasným přepnutím `mediaOpensLink` na `false` u fotky:
  prohlížeč s jménem, štítkem, popiskem a tlačítkem, tlačítko nahlásilo `fromFullscreen`
  (`Ok`), prohlížeč se zavřel a odkaz otevřela konverzace. **Neověřené:** skutečná video
  a GIF reklama (server je neposlal), iOS a desktop.

### Reklama u bota

`getChatSponsoredMessages` vrací reklamy i v chatu s botem a [specifikace](https://core.telegram.org/api/sponsored-messages)
je tam chce jinak než v kanálu: **pruh nad konverzací**, „akin to an action bar". Dřív se
kreslily jako karta pod zprávami, protože komentář ve ViewModelu tvrdil, že mimo kanály je
seznam prázdný.

- **Že jde o bota, rozhoduje repository** (`SponsoredMessage.inBotChat`): TDLib reklamy vrací
  jen kanálům a chatům s botem, takže soukromý chat (`ChatTypePrivate`) je bot. Neptá se
  `ChatInfo` — ta při příchodu reklam ještě nemusí být načtená a reklama by na okamžik
  naskočila jako karta.
- **Pruh je `SponsoredBar`** ve sloupci pruhů (`ConversationBars`) hned pod pruhem proti
  spamu, se sklem a tvarem `ChatBarSurface`. Ze seznamu zpráv reklama u bota vypadne
  (`conversationItems`).
- **Předponu „Reklama" / „Doporučeno" přidává klient** v barvě odkazů (`primary`; „modrá" ze
  specifikace je barva odkazů oficiálního klientu), jméno inzerenta za ní nese barvu reklamy
  a proužek vlevo taky.
- **Barva reklamy je `accent_color_id`** (`telegramAccentColor` v `core/ui/SenderColor.kt`):
  z Telegramu se bere jen ODSTÍN sedmi vestavěných barev, tón a sytost jsou jako u barev
  odesílatelů. Proti sklu pruhu se ověřuje `accentOn`. Vyšší id (barvy profilu) by se musela
  dočíst `updateAccentColors`; spadnou na vestavěnou podle zbytku.
- **Text se neořezává.** Zhlédnutí se smí hlásit, až je vidět celý text; hlásí se po vteřině
  zobrazení pruhu, jednou za relaci (`reportSponsoredSeen`), přes `viewMessages` jako u kanálu.
- **Fotka inzerenta (`sponsor.photo`) je náhled vpravo**, ⋮ s nahlášením za ní.
- **Keš 5 minut** ze specifikace drží TDLib sama; konverzace se ptá při každém otevření.
- **Stránka bota (`BotIntroPage`) pod pruh zajíždí a je zdrojem skla** (`hazeSource(listHaze)`
  s podkladem v barvě stránky, odsazení `topPadding` uvnitř posuvné plochy). Reklama se ukazuje
  hlavně v chatu s botem, který uživatel ještě nespustil — tedy nad touto stránkou, ne nad
  seznamem zpráv. Bez toho ležel avatar bota PŘES pruh a sklo bez zdroje ukazovalo prosvítající
  stín a světlejší obdélník v obrysu obsahu stránky.
- **Ověřeno na emulátoru** (česky, světlý i tmavý motiv): reklamu server vracel botům
  @notcoin_bot, @blum, @major, @tapswap_bot, @catizenbot, @DurgerKingBot, @SaveAsBot,
  @GPT4Telegrambot, @TonRocketBot a @send (ne pokaždé — reklamy se střídají a někdy přijde nic),
  ne @wallet, @HamsterKombat_bot, @utubebot, @like ani @vote. Pruh s vlastními emoji v textu,
  s fotkou i bez ní, `viewMessages` i `clickChatSponsoredMessage` server přijal (`Ok`), ťuknutí
  otevřelo bota inzerenta v aplikaci, ⋮ otevře průvodce s důvody od serveru (nahlášení se
  neodeslalo). @GPT4Telegrambot jednou poslal reklamu s `can_be_reported = false`. Karta
  v kanálu (@bbcrussian) beze změny. **Neověřené:** potvrzení u odkazu mimo domény Telegramu
  (žádná z viděných reklam takový neměla; jen testy) a iOS / desktop.

## Metriky zobrazení

TDLib PŘIKAZUJE posílat `sendMessageViewMetrics`, když zpráva zmizí z obrazovky na víc než
300 ms, viewport zanikne, nebo zobrazení přesáhne 5 minut. Je to statistika (jak dlouho, jak
aktivně a jak velký kus zprávy uživatel viděl), ne přečtení — to dál hlásí `viewMessages`.
Počítá to čistá třída `MessageViewMetricsTracker` (testy `MessageViewMetricsTrackerTest`),
snímky seznamu jí podává `ConversationViewMetrics` v `ConversationListEffects.kt` a odesílá
`ConversationViewModel.reportViewMetrics`.

- **Seznam v klidu snímky nevydává**, takže efekt tiká (100 ms): bez toho by se zmizení ani
  pět minut nepoznaly, dokud by se seznam nepohnul.
- **Viewport zaniká i odchodem do pozadí.** Kompozice tam žije dál, ale nikdo se nedívá;
  efekt proto běží jen ve stavu STARTED a při zastavení pošle rozpracované (`finally`).
- **Odchází v `appScope`**: poslední dávka jde při zavírání obrazovky, kdy se `viewModelScope`
  ruší. Ověřeno: po odchodu z chatu TDLib dávku přijal.
- **Viditelná část je bez odsazení seznamu** (`beforeContentPadding` / `afterContentPadding`):
  pod průsvitnou lištou a vstupním polem zprávu nikdo nečte. Položky mimo ni se nepočítají.
- **Aktivní čas** je do 15 s od posunu seznamu (`isScrollInProgress`). Ťuknutí a psaní se
  nepočítají — čtení je hlavně posouvání a TDLib aktivní čas chce na poznání odloženého telefonu.
- **Klíč položky není id zprávy** (u odeslaných se liší); převádí ho ViewModel a rozesílanou
  nebo neodeslanou zprávu (dočasné id) vynechá. U alba se hlásí položka, tedy nejstarší zpráva.
- **Diskrétní čtení metriky neposílá**, stejně jako přečtení.
- **Ověřeno na emulátoru** dočasným výpisem úspěšných volání: zprávy, které při posunu zmizely,
  se nahlásily do půl vteřiny, při odchodu z chatu odešel zbytek; TDLib všechny přijal.
  **Neověřené:** odchod do pozadí a pětiminutové zobrazení (jen testy), iOS a desktop.
