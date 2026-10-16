# Konverzace: akce nad zprávami

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Nabídka nad zprávou

Dlouhý stisk otevře **nabídku u zprávy**, ne bottom sheet: rychlé reakce v pruhu nad bublinou
a nabídka akcí pod ní, zbytek obrazovky pod závojem. Sheet dole utrhl pozornost od zprávy,
které se týkal, a unesl jen pár položek — akcí bude postupně deset.

- **Vrstva v okně obrazovky, ne `Popup`** (`MessageMenuLayer`, nad Scaffoldem konverzace).
  `Popup` je na Androidu vlastní okno: kopii zprávy do něj šlo dostat jen jako snímek (samolepka
  stála, zpátky se skákalo), závoj potřeboval díru přes `BlendMode.Clear`, počátek okna se musel
  měřit a plný výběr reakcí byl vlastní napodobenina sheetu. Ve stejném okně nic z toho není třeba.
- **Zpět obsluhuje `NavigationBackHandler`** (`androidx.navigationevent`), ne `BackHandler` —
  ten by zabil predictive back. Při rozbaleném výběru reakcí je vypnutý, zpět si vezme sheet.
- **Zpráva se v nabídce složí znovu, živá** (`MenuCopy`: tentýž `MessageBox` a `MessageBodyScope`
  jako v seznamu, stejná šířka, bez gest).
- **Kopie dostává stejné OMEZENÍ šířky jako zpráva v seznamu, ne šířku zprávy napevno**
  (`MenuAnchor.maxWidth`, zapisuje ho rám v `layout`, a stejný `maxWidthFraction`). S šířkou
  zprávy jako stropem vyšlo jinak všechno, co se řídí dostupnou šířkou: fotka s popiskem využívá
  plnou dostupnou šířku, v kopii by se při pevném oříznutí chovala jinak. V seznamu se zprůhlední (`isInMenu`) a drží místo —
  po ní zůstane prázdno, jako v Google Zprávách.
- **Let tam i zpátky je ruční posun kopie (`Animatable`), NE sdílený prvek.** Sdílený prvek se během
  přechodu kreslí v překryvu nad celou aplikací, mimo ořezy rodičů, takže zpráva začínající pod
  hlavičkou chatu přeletěla PŘES hlavičku. `clipInOverlayDuringTransition` se volal, ale na
  výsledku nebylo nic vidět, a `renderInOverlayDuringTransition = false` kreslil stejně. Kopie je
  ve stejném okně jako seznam, takže sdílený prvek by tu dělal jen tentýž posun — ručně ho jde
  oříznout pod hlavičkou (`clipRect` na ploše kopie).
- **Vrstva po zavření ještě chvíli žije** (`lingering`): kopie letí zpátky na místo zprávy a teprve
  po doběhnutí vrstva zmizí a zpráva v seznamu se znovu ukáže. Zmizet hned, neměla by kopie
  odkud letět.
- **Odkud zpráva vyjíždí, hlásí zpráva sama při dlouhém stisku** (`onMenuAnchor`, souřadnice v okně).
  Rám drží `LayoutCoordinates` v obyčejném poli, ne ve stavu — zápis stavu při každém layoutu
  by položku rekomponoval.
- **Zpráva se do pohledu VYSUNE, nabídka je VŽDY POD NÍ** (`placeMenuMessage`, testy
  `MenuMessagePlacementTest`). Zpráva zasahující pod hlavičku nebo pod pole vyjede tak, aby nad
  ní byl pruh reakcí a pod ní nabídka. **Vysoká zpráva se zmenší** (nejvýš na `MIN_MESSAGE_SCALE`
  0,6, menší text nejde číst), a **když se nevejde ani tak, ořízne se** a dole vybledne — vidět je
  začátek, podle kterého se pozná; celý obsah je v seznamu. Dokud se vysoká zpráva jen vysunula,
  nabídka se nevešla pod ni ani nad ni a skončila PŘES její začátek i přes pruh reakcí.
- **Let, zmenšení i ořez jdou z jednoho průběhu 0 → 1** (`progress`), takže se zpráva zmenšuje
  a ořezává plynule a zpátky zase dorůstá. Zmenšuje se k té straně, u které stojí, a nahoře
  zarovnaná; ořez je rámeček s výškou viditelné části, uvnitř zpráva v plné výšce.
- **Plný výběr reakcí je STANDARDNÍ `ModalBottomSheet`** (`ReactionPickerSheet`) bez vlastního
  závoje (`scrimColor = Transparent`) — ztmavení už dělá nabídka pod ním a pruh reakcí i zpráva
  nad sheetem musí zůstat vidět. Bez napůl rozbaleného stavu a s mřížkou pevné výšky, jako výběr
  samolepek. Ve starém sheetu nešlo ťuknout na emoji (podezřelý byl `FlowRow` bez scrollování
  a napůl rozbalený stav); tady ťuknutí funguje, ověřeno na zařízení.
- **Závoj rozostřuje, neztmavuje** (Haze, `hazeSource` na Scaffoldu konverzace, `hazeEffect`
  na závoji). Compose sám umí rozostřit jen VLASTNÍ obsah (`Modifier.blur`, i s přechodem od
  1.13); rozostřit to, co leží POD vrstvou, umí Android až od 17 QPR2
  (`RenderNode.setBackdropRenderEffect`). Haze obsah pod vrstvou zaznamená do vlastní grafické
  vrstvy a vykreslí rozostřený, na Androidu 12+ i na iOS. **Jde to jen proto, že je nabídka
  ve stejném okně** — z `Popup` (vlastní okno) by Haze na Scaffold nedosáhl.
- **Poloměr 12 dp, ne výchozích 20–24.** Při 24 dp je z konverzace kaše a nepozná se ani
  hlavička, a ta má zůstat rozpoznatelná (viz horní mez nabídky). Rozostření odsouvá do pozadí
  samo, takže ztmavení kleslo ze 45 % na 20 %. **Kde rozostření není (Android 11 a starší),
  kreslí Haze `fallbackTint`**, a ten je nastavený na dřívější závoj — o nic se nepřijde.
- **Závoj se objevuje průhledností, poloměr se neanimuje.** Změna poloměru znamená nový
  `RenderEffect` v každém snímku; prolnutí ostrého a rozostřeného obrazu vypadá skoro stejně
  a nic nestojí. **Průhlednost jde přes `graphicsLayer`, ne přes `alpha(…)` ve stylu:** styl je
  v Haze 2 neměnný, takže by se v každém snímku animace musel skládat znovu — a čtení animované
  hodnoty v kompozici by rekomponovalo celou vrstvu nabídky.
- **Haze 2 (`haze` + `haze-blur`, vždy stejná verze), vstup `HazeInput.Sources`.** Dokumentace
  doporučuje `Backdrop`, ale ten bez experimentálního příznaku stejně spadne na `Sources`
  a s ním by se na Androidu 17 QPR2 tiše přepnul na nativní cestu, podle autora zatím dražší.
  Dvojka nezaznamenává zdroj, dokud ho žádný efekt nečte — `hazeSource` na Scaffoldu tedy se
  zavřenou nabídkou nic nestojí (v 1.x se konverzace kreslila do vrstvy pořád).
  **Kotlin projektu musí být ≥ tomu, ze kterého je Haze publikovaná** (2.0 → 2.4.20) — stejná
  podmínka jako u TDLib wrapperu. Neimportuje se `haze-blur-materials`: styl je pár řádků
  (`scrimStyle`) a modul by táhl material3 v jiné verzi.
- **Nabídka akcí před sheetem NEMIZÍ.** Sheet ji jen překryje a klikat se na ni nedá, protože je
  modální; je to pořád táž otevřená nabídka. Dřív se schovávala (`if (!expanded)`), zmizela
  natvrdo a po zasunutí sheetu bleskově naskočila.
- **Spodní mez je horní hrana vstupního pole, ne navigační lišta** (`contentBottom`, obrazovka ho
  měří na `bottomBar`). Pole leží přes seznam, takže zpráva vysunutá jen nad navigační lištu
  skončila pod polem. Kopie zprávy se ořezává mezi hlavičkou a polem, aby přes ně nepřeletěla.
- **Otevření nabídky schová klávesnici** (`LocalSoftwareKeyboardController.hide()`, fokus pole zůstává).
  S klávesnicí se na menším telefonu mezi hlavičku a pole nevešla ani samotná nabídka — a zpráva
  vyšla se zápornou výškou: `Size(804 x -2) is out of range`, pád aplikace. Tak to dělají i Telegram
  a Google Zprávy. **Na velkém emulátoru se to nereprodukuje**, místa je dost; ověřuje se na
  zmenšeném displeji (`adb shell wm size 1080x1700`, pak `wm size reset`).
- **Poloha zprávy je FUNKCE čtená každý snímek** (`onMenuAnchor(() -> Rect?)`), ne hodnota
  z okamžiku stisku. Když zmizí klávesnice, seznam se posune — nabídka musí vyjet i dosednout
  tam, kde zpráva je teď.
- **V těsném okně zůstane zpráva vidět** aspoň `MIN_MESSAGE_VISIBLE` (72 dp) a nabídka dostane
  strop výšky a posouvá se. Nabídka smí sahat až k navigační liště (vstupní pole je pod
  závojem), zpráva jen nad pole. Výšky se ve vrstvě navíc ořezávají na nulu — pružina přestřelí.
- **Horní mez je spodek hlavičky chatu, ne stavová lišta** (`contentTop` ze
  `ConversationItemScope`, obrazovka ho měří na seznamu). Vysunutá zpráva ani pruh reakcí nesmí
  hlavičku překrýt — ztratilo by se povědomí, s kým je chat otevřený. Google Zprávy to dělají stejně.
- **Rozvržení nabídky se počítá z CÍLOVÉ polohy zprávy, ne z té, kde právě je.** Dokud se
  počítalo z aktuální polohy, pruh reakcí se v prvních snímcích (než je snímek zprávy hotový)
  nevešel nad zprávu, šel pod ni a pak přeskočil nahoru. Teď se o umístění rozhodne jednou
  a pruh i nabídka dojedou se zprávou (`travel`).
- **Past: `Modifier.clip(shape)` musí být PŘED `combinedClickable`.** `Surface` si tvarem ořezává
  jen svoje pozadí, a to až za modifikátory, které dostane zvenku — vlnka dlouhého stisku se tedy
  kreslila do obdélníku a přetékala přes zaoblená nároží bubliny.
- **Bez pruhu reakcí se pruh nepřesouvá** (`placeMenu`). Podmínka „pruh se nevejde nad zprávu“
  platila i s nulovou výškou pruhu, když zpráva stála u horní meze — tedy u každé vysoké zprávy
  zmenšené k horní mezi v chatu bez reakcí. Nabídka se tím posunula o mezeru níž, pod zprávu se
  nevešla, odskočila nad ni a ořízla se k horní mezi: přes zprávu. Test
  `bezPruhuReakciJeNabidkaPodZmensenouZpravou` prochází zprávy výšku po výšce.
- **Výšky obou kusů jsou konstanty, ne měření.** Kreslíme si je sami, takže je známe předem —
  ušetří se tím snímek, ve kterém by nabídka poskočila na správné místo.
- **Výška nabídky se počítá z UKÁZANÝCH položek, ne z kandidátů.** Blok nabízí kandidáty a server
  z nich část zakáže; s počtem kandidátů visela nabídka nad zprávou o řádek výš za každého
  zakázaného. Položky se proto sestaví jednou a z nich se počítá výška i kreslí nabídka.
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
- **Výběr části textu je SHEET z nabídky, ne výběr v bublině** (`SelectTextSheet`).
  `SelectionContainer` zahajuje výběr dlouhým stiskem, a ten v konverzaci už patří nabídce nad
  zprávou; kdyby se o gesto dělily, jedna by přestala fungovat — a nabídka je ta častější.
  Text v sheetu je holý (`copyableText`), ne `RichText`: odkazy mají vlastní klikání a se
  `SelectionContainer` by se o gesta praly. Kopírování obstará systémová nabídka nad výběrem,
  vlastní tlačítko by jen zdvojovalo, co platforma umí sama.
- **Na desktopu se vybírá přímo v bublině** (viz „Výběr textu myší v bublině"); sheet a výběr
  v kopii v nabídce zůstávají pro dotyk.
- **„Vybrat text" stojí na stejném právu jako „Kopírovat"** (`canCopy`). Chráněný obsah nesmí
  ven ani po kouskách a bez textu není co vybírat; obojí hlídá test.

### Skupiny a „Další"

Nabídka byla jeden plochý seznam a u běžné cizí zprávy ve skupině měla i 17 řádků (přes 800 dp),
víc, než se pod zprávu vešlo. Teď jsou položky ve **skupinách M3 Expressive** (`DropdownMenuGroup`,
stejný vzhled jako `ExpressiveMenu`) ve **dvou úrovních**: nahoře, kvůli čemu se nabídka u zprávy
otevírá, zbytek pod „Další". Návrh i varianty prošly náčrtem s uživatelem.

- **Kam položka patří, říká `MessageMenuAction.slot`** (`MenuSlot`, exhaustivní `when`): nová akce
  bez místa se nepřeloží. Pořadí skupin dává pořadí `MenuSlot`, uvnitř skupiny pořadí kandidátů
  z `menuActions`. Rozdělení dělá čistá funkce `arrangeMenu` (testy `MessageMenuLevelsTest`).
- **Nahoře:** hlavička, hlavní akce (Upravit u vlastní, Odpovědět, Kopírovat, Přeposlat, Přeložit
  u cizojazyčné, Sdílet a Uložit jako… u souboru, Do oblíbených u samolepky), akce ankety, nálezy
  v textu (nejvýš tři řádky), „Další" a Smazat. **Pod „Další":** Vybrat text, Shrnout, Vysvětlit;
  Záložka, Připomenout, Připnout; Vybrat víc zpráv; Nahlásit.
- **Pevná pravidla, ne pořadí podle četnosti.** Přeskládávání podle používání by rozbilo svalovou
  paměť; co je nahoře, se řídí jen druhem zprávy a právy od serveru.
- **Informace jsou hlavička**, ne řádek: ikona Informací, čas a stav doručení, ťuknutím sheet
  Informace. Podrobnosti o doručení člověk hledá právě u času. Ušetří to řádek.
- **Ikona VPŘEDU, ne šipka vzadu.** Text v řádku `DropdownMenuItem` nemá váhu: dlouhé datum
  („9. října 2025, 8:53 · Přečteno") srazilo koncovou šipku na nulovou šířku a zmizela (viděno
  na snímku z `runComposeUiTest`). Ikona vpředu má místo vždycky.
- **Nevratné akce zůstávají nahoře** ve vlastní skupině, i Smazat odpověď ankety — mazání se nehledá.
- **Jediná položka pod „Další" jde nahoru.** „Další" a pak zpět kvůli jedné položce jsou dva kroky
  navíc a nahoře zabere tentýž řádek.
- **Telefon: „Další" vymění obsah na místě** (`AnimatedContent`, posun do strany a prolnutí),
  první řádek je šipka zpět. Systémové zpět vede z „Další" o úroveň výš, ne pryč. Kaskáda do
  strany na telefonu nejde: vedle nabídky široké 220 dp místo není a material3 by ji položil přes ni.
- **Desktop: „Další" se otevře do strany** po najetí myší nebo kliknutím (`submenusCascade`).
  Material3 1.12.0-alpha03 na to nemá komponentu, jen vzor (`MenuWithCascadingMenusSample`, KDoc
  `DropdownMenuPopup` na něj odkazuje): vnořený `DropdownMenuPopup` s `MenuAnchorPosition.End`
  v `Box` s položkou, `PopupProperties(focusable = false)` a TENTÝŽ `interactionSource` pro řádek
  i skupiny podnabídky, takže cesta myší k podnabídce ji nezavře. Hlavní nabídka zůstává ve vrstvě.
- **Nabídka dostane výšku VYŠŠÍ z obou úrovní**, ne té, která je vidět. Přepnutí úrovně by jinak
  nabídku natáhlo pod prstem a s ní by poskočila i zpráva, která jí uvolňuje místo. Obsah se drží
  strany u zprávy (nad zprávou dole, pod ní nahoře) a roste od ní pryč; prázdný zbytek rezervy
  dotyky nechytá, ťuknutí do něj zavře nabídku jako ťuknutí do závoje.
- **Výška položky je PEVNÁ (44 dp), svislé odsazení obsahu nulové.** `DropdownMenuItem` má jinak
  odsazení 12 dp nahoře i dole kolem textu, takže výška roste s velikostí písma — a nabídka se
  rozmisťuje z konstant (`menuLevelHeight`), ne z měření. Hodnoty skupin jsou z material3
  (odsazení 2 dp, mezera 2 dp); `DropdownMenuGroupVerticalPadding` je interní, proto vlastní konstanta.
- **Pozor při bumpu material3:** v alpha03 má `DropdownMenuItem` šipku v parametru `trailingIcon`,
  novější androidx ho přejmenoval na `trailingContent`.

### Citace z nabídky

Text **kopie zprávy v nabídce jde označit** (dlouhým stiskem na slovo, pak úchyty). S označeným
úryvkem se „Odpovědět" přejmenuje na **„Citovat"** a odpověď pak míří jen na úryvek
(`InputMessageReplyToMessage.quote`); „Kopírovat" zkopíruje jen úryvek. Tak to dělá i Telegram
na iOS. Dlouhý stisk v SEZNAMU dál otevírá nabídku — o gesto se nedělí, výběr začíná až v kopii.

- **Sledovat výběr jde veřejným API**: Compose 1.12 má `SelectionContainer(state = SelectionState)`
  a `SelectionState.selectedTexts` je snapshot stav, takže nabídka se přejmenuje hned při tažení
  úchytu (`derivedStateOf`). Ověřeno `javap` nad `foundation-desktop-1.12.0`, bez opt-inu.
- **Položka se PŘEJMENUJE, nepřibývá** (`MessageMenuAction.presentation(menu, selection)`,
  `withSelection`). Výška nabídky se počítá z ukázaných položek, takže položka navíc by nabídku
  pod prstem natáhla; zašedlé položky jinde v nabídce nejsou.
- **Citovat jde jen text TÉHLE zprávy** (`quoteSelection`, testy `QuoteSelectionTest`). Kopie
  nese i jméno odesílatele, značku přeposlání a hlavičku citace nad odpovědí, a výběr přes ně
  vrací víc kusů; bere se nejdelší kus, který v textu zprávy je. Označený úryvek z hlavičky
  citace (text JINÉ zprávy) nechá „Odpovědět" být.
- **Kopie dostává scope, který akce zahazuje** (`InertItemScope`). S označitelným textem na ni
  jdou dotyky, a ťuknutí na fotku, odkaz nebo citaci by z nabídky otevřelo prohlížeč, web nebo skok.
- **Zavírání ťuknutím se NESMÍ ptát na spotřebování.** `SelectionContainer` si ťuknutí bere i bez
  výběru, takže s `waitForUpOrCancellation` nešla nabídka zavřít ťuknutím na zprávu (ověřeno na
  emulátoru). Rozhoduje jen: byl výběr při dotyku, jak dlouho prst ležel, jestli ujel.
- **Během označování pruh reakcí zmizí.** Systémová lišta výběru (Kopírovat, Vybrat vše, Přeložit)
  se kreslí nad označeným textem, tedy přesně přes pruh reakcí.
- **Označovat jde jen s `canCopy`** — chráněný obsah nesmí ven ani citací přes úryvek.
- **Barvy výběru podává plocha bubliny, ne `SelectionContainer`** (`BubbleSurface`,
  `MessageBodyScope.selectionColors`). Výchozí podklad výběru je `primary` s alfou 0,4 — na
  odchozí `primary` bublině tedy barva na barvě a označený úsek nebyl vidět vůbec. Podklad si
  `BasicText` čte z `LocalTextSelectionColors` SÁM, v místě textu, takže ho jde podat až na
  ploše, kde text leží. Pravidlo je tatáž `accentOn` jako u akcentu: `primary`, dokud je na
  bublině vidět, jinak barva popředí bubliny.
- **Úchyty výběru nastavuje nabídka, kolem `SelectionContainer`** — čte je kontejner, ne text.
  Visí přes spodní okraj bubliny, takže se musí odlišit od bubliny I od plochy konverzace
  (`distinctOnBoth`, testy `AccentColorTest`): `primary`, dokud má proti oběma aspoň 3 : 1
  (WCAG pro netextové prvky), jinak z `inversePrimary` / `onSurface` ta s lepším horším
  kontrastem. Pevná náhrada nevyjde v obou režimech: u odchozí zprávy vyhraje v tmavém
  `inversePrimary` (bublina světlá, plocha tmavá), ve světlém `onSurface`. Barvu bubliny
  sdílí nabídka s rámem přes `bubbleColorFor`. **Každá nová plocha v barvě bubliny jde přes
  `BubbleSurface`**, ne přes holý `Surface(color = bubbleColor)` — jinak na ní výběr zmizí znovu.
- **Formátování úryvku se MUSÍ přenést** (`quoteInput`, testy `ReplyQuoteTest`). TDLib: tučné,
  kurzíva, podtržení, přeškrtnutí, spoiler a vlastní emoji „must be kept in the quote". Repository
  si proto přečte původní zprávu (`getMessage`, lokální), entity ořízne na úryvek a posune.
  Odkazy, zmínky a kód do citace nepatří a odpadnou.
- **`position` je jen odhad** („approximate" v dokumentaci TDLib); repository si místo dohledá
  kolem něj, pak kdekoli. Úryvek, který v textu není, znamená odpověď na celou zprávu, ne chybu.
- **Strop 1024 znaků** (`MAX_QUOTE_LENGTH`, server `message_reply_quote_length_max`): delší výběr
  se zkrátí na začátek, i ten je platný úryvek.
- **Ověřeno na emulátoru (Fold, složený) i na serveru**: Bot API u odeslané zprávy vrátilo
  `quote: {text, position: 24, is_manual: true}`; odpověď bota s `reply_parameters.quote` se
  nakreslila s úryvkem; koncept s citací přežil odchod z chatu.
- **Schránka je vlastní `expect`/`actual`, ne Compose API.** `LocalClipboardManager` je zastaralý
  a jeho náhrada pracuje s `ClipEntry`, což je `expect class` bez společné továrny na text —
  v commonMain se prostě nedá vyrobit.
- **Prázdná nabídka emoji není chyba.** V Uložených zprávách Telegram místo reakcí používá tagy;
  dřív se kvůli tomu nabídka neotevřela vůbec a nešlo ani odpovědět. Teď se jen nevykreslí pruh.
- **Rozhoduje HIERARCHIE, ne to, kolik se toho má vejít.** Položka nabídky je jednorázová akce:
  nabídku ukončí a co si otevře, je samostatná věc — „Informace" je modální sheet a funguje
  správně, včetně zkopírování odkazu. Plný výběr reakcí je proti tomu **podnabídka tlačítka „+"**
  v pruhu rychlých reakcí, takže patří DOVNITŘ plovoucí nabídky. Sheet nad ní z podnabídky dělal
  něco, co ukončí nabídku, ke které pořád patří.
- **Nad sheetem zůstává pruh rychlých reakcí i vybraná zpráva** (nabídka akcí pod sheetem viz výš).
  Z pruhu je pořád vidět, čeho se výběr týká a co je už použité. Pruh zůstane
  u zprávy, dokud se nad sheet vejde, jinak se posadí těsně nad něj — a posun se animuje, aby to
  nevypadalo jako přeskládání. Rozvržení počítá `reactionSheetLayout` (testy `ReactionSheetTest`),
  čistá funkce mimo composable jako `photoFrame`: samý hraniční případ (zpráva dole, nahoře,
  nízké i vysoké okno). Počítá se i se sbaleným sheetem — při odjezdu `expanded` už neplatí
  a výška by mu pod rukama skočila na podlahu.
- **Smazaná zpráva se ze seznamu neodstraňuje ručně.** TDLib pošle `UpdateDeleteMessages` a ten se
  už zpracovává; předběhnout server by znamenalo riskovat, že zpráva zmizí z obrazovky a na
  serveru zůstane.

## Akce po najetí myší

Na zprávě pod kurzorem se na volné straně bubliny ukáže pilulka s časem, Odpovědět a ⋮
(`MessageHoverActions`). ⋮ otevře tutéž nabídku jako podržení nebo pravé tlačítko. Na desktopu
se čas ani odpověď jinak nedaly najít: v bublině čas není a obojí je tah (časy doleva, odpověď
doprava), na který myší nikdo nepřijde. Vybraná varianta ze dvou (náčrt s uživatelem): jen
zpráva pod kurzorem, ne časy u všech — konverzace při přejíždění myší nebliká a nic se
neposouvá; časy u všech dál ukáže tah doleva.

- **Najetí se hlídá na ŘÁDKU, ne na bublině** (`hoverable` na `Row` v `MessageFrame`). Řádek jde
  přes celou šířku, takže pilulka na volné straně v něm leží a cesta kurzoru k ní ji neschová.
  Dotyk najetí nemá; pero ano (ukáže ji při vznášení, nevadí).
- **Do layoutu nezasahuje** (`outsideBubble`): v `Box` bubliny zabere šířku 0 u spodního rohu
  volné strany a obsah se položí ven, za značky efektu a kontextu. Jinak by se zpráva při
  najetí přeskládala.
- **Z řádku nevyjede.** Široká bublina na úzkém displeji nechá volné straně méně místa, než
  pilulka potřebuje — na emulátoru přetekla za okraj a z času zbylo „12". Při pokládání se
  proto posune dovnitř řádku a kus bubliny překryje; stín (`shadowElevation` 2 dp) ji od bubliny
  oddělí. Souřadnice řádku se čtou až při pokládání (pole, ne stav), takže nic nerekomponuje.
- **Odpovědět jen tam, kam se smí psát** (`ConversationItemScope.canReply`, tentýž `canWrite`
  jako příkazy); `replyTo` by jinde nic neudělal. Akce je `SwipeReply`, tatáž jako u tahu.
- **Ve výběru víc zpráv a u zprávy s otevřenou nabídkou se neukazuje.**
- **Plocha je `secondaryContainer`, ne `surfaceContainerHigh`.** Tu má příchozí bublina, takže
  pilulka vedle ní (a přes ni, když se posune dovnitř) splývala — uživatel ji na desktopu
  nerozeznal. Ikona doručení dostává barvu popředí pilulky a chyba jde přes `accentOn`
  (v paletě se zvýšeným kontrastem je container světlý, viz „Akcent na tónované ploše").
- **Bez minimální dotykové plochy** (`LocalMinimumInteractiveComponentSize` = `Unspecified`)
  a extra malá tlačítka M3 (32 dp): pilulka je jen pro myš a s plochou 48 dp by byla vyšší než
  jednořádková bublina.
- **Ověřeno na emulátoru virtuální myší přes `uinput`** (`EV_REL` + `BTN_LEFT`; `adb input`
  pohyb bez kliknutí neumí). Pozor na zrychlení ukazatele: velké skoky ujedou, kurzor se napřed
  zarazí do rohu (velké záporné posuny) a pak jde po malých krocích (zisk ~3,4 na krok 2–5).
  Snímek se musí udělat, DOKUD zařízení existuje (`uinput … & sleep N; screencap; wait`) —
  po konci skriptu kurzor zmizí a najetí skončí. Pilulka u odchozí ankety (posunutá dovnitř),
  klik na Odpovědět připravil odpověď.
- **Ověřeno na Windows 11** (ladicí běh, skutečná myš, uživatel). macOS neověřený.

## Výběr textu myší v bublině

Na desktopu (`bubbleTextSelectable`, jen JVM) je text zprávy v `SelectionContainer` přímo
v bublině (`MessageText`): tažení vybírá, dvojklik slovo, trojklik odstavec, Ctrl+C kopíruje
a pravé tlačítko nad textem ukáže běžnou textovou nabídku Compose (Kopírovat, Vybrat vše). Jako
v Telegram Desktop. Na dotyku to nejde: výběr tam začíná dlouhým stiskem a ten patří nabídce
nad zprávou (viz „Nabídka nad zprávou").

Aby se výběr nepral s ostatním, se na desktopu v konverzaci vzdalo několik věcí — všechno, co
dřív dělaly, má pilulka po najetí myší (viz „Akce po najetí myší"):

- **Tah myší nezakládá odpověď ani neodkrývá časy** (`detectHorizontalSwipe(mouse = false)`).
  Detektor sám končí, když událost spotřebuje výběr, jenže mimo text (okraj bubliny, prázdný
  řádek) by tah myší dál jel.
- **Ťuknutí na zprávu nic nedělá a stisk nemá vlnku.** Ťuknutí na vlastní zprávu jinak ukáže
  odznak „přečteno" POD bublinou, ten přidá řádek, zpráva poskočí a rozjetý výběr ujede
  (nahlásil uživatel). Stav doručení proto nese pilulka (`DeliveryStateIcon`, tytéž ikony jako
  odznak). Vlnka by blikla přes celou bublinu při každém začátku výběru.
- **Pravé tlačítko na bublině nabídku nad zprávou NEotvírá** (`contextClickable(rightClick =
  false)`) a odkaz v textu nemá vlastní podržení: nad textem patří pravé tlačítko běžné textové
  nabídce, ne naší (přání uživatele — do nabídky nad zprávou vede ⋮ v pilulce). Přílohy, fotky,
  samolepky, ankety a seznamy úkolů pravé tlačítko na nabídku nad zprávou mají dál — výběr
  v nich není.
- **Dvojklik na text vybere slovo**, ne rychlou reakci.
- **⋮ v pilulce dostane vybraný úryvek** (`BubbleTextSelection` → `ShowMenu(selection)` →
  `MessageMenu.bubbleSelection`): „Kopírovat" zkopíruje úryvek, „Odpovědět" je „Citovat",
  stejně jako výběr v kopii v nabídce. Klik na ⋮ výběr nezruší (tlačítko si fokus nebere).
  Stejné právo jako výběr v kopii (`canCopy`); v chatu s chráněným obsahem
  (`protectsContent`) výběr v bublině vůbec není.
- **Jen text zprávy**, ne náš popis (anketa) a ne kopie v nabídce (`selectableText()` vrací
  `false` pro `InertItemScope`); výběr vždy v jedné zprávě. Text článku po kusech má víc
  `SelectionContainer` a platí první neprázdný.
- **Nabídka Compose pro výběr textu má na desktopu dvě cesty** (`ComposeFoundationFlags
  .isNewContextMenuEnabled`, v 1.12 na desktopu vypnutá → `LocalTextContextMenu.current.Area`).
  Kdyby ji bylo potřeba vypnout, stačí podat `TextContextMenu`, jehož `Area` jen vykreslí obsah.
- Na Androidu ověřeno, že se nic nezměnilo (výběr v bublině není, tah na odpověď funguje).
- **Ověřeno na Windows 11** (ladicí běh, skutečná myš, uživatel): výběr tažením, Ctrl+C,
  textová nabídka na pravém tlačítku, ⋮ s úryvkem. macOS neověřený.

## Smazání cizích zpráv správcem

Správce supergroupy, který maže zprávy jednoho člena (z nabídky i z výběru), dostane
v potvrzení pod textem volby navíc jako u oficiálních klientů: **Nahlásit spam**, **Smazat všechny
zprávy autora ve skupině**, **Smazat nedávné reakce autora** a **Vyhodit autora ze skupiny**
(`PendingDelete.moderation`, `MessageDeleteDialog`). Kombinují se zaškrtnutím, potvrzuje se tlačítkem Smazat.

- **Co se nabídne, je čistá funkce** `senderModerationRights` (`MemberManagement.kt`, testy
  v `MemberManagementTest`): smazat vše chce právo mazat cizí zprávy (TDLib: „requires
  canDeleteMessages"), smazat reakce TOTÉŽ právo (`deleteAllRecentMessageReactionsFromSender`:
  „requires canDeleteMessages administrator right"), vyhodit totéž pravidlo jako „Vyhodit" nad
  členem (`memberActions`),
  spam rozhoduje server (`MessageProperties.canReportSupergroupSpam` u všech zpráv).
- **Jen u běžného člena a jen u zpráv JEDNOHO autora.** Na vlastníka a správce volby nemíří
  (spammer správcem nebývá a omyl by bolel víc); výběr zpráv od víc lidí je jen mazání.
- **Dočítá se až po otevření dialogu** (`loadSenderModeration`, repository `senderModeration`
  čte zprávy, člena a vlastnosti zpráv) a jen když jde mazat všem — jinak nejde o cizí zprávy
  ve skupině, kde je uživatel správce. Volby proto do dialogu přibudou se zpožděním
  (`animateContentSize`). Prohlížeč médií je nedočítá.
- **Pořadí kroků** (`deleteAsModerator`): nahlášení napřed (zprávy musí ještě existovat), pak
  smazání reakcí (ještě před vyhozením — dokumentace neříká, jestli server maže reakce i tomu,
  kdo už ve skupině není), vyhození (navždy, `revokeMessages = false`), nakonec smazání — všeho od autora
  (`deleteChatMessagesBySender`), nebo jen vybraných zpráv. Selhání jednoho kroku další nezastaví.
- **Ověřeno na emulátoru** (14. 10. 2026, „Plume test moderace", zprávy bota Notifikátor poslané
  přes Bot API): dialog ukázal všechny tři volby, „smazat vše" a „vyhodit" smazaly obě zprávy bota
  a bota vyhodily („Odebral(a) jsi Notifikator"). **Nahlášení spamu se nezkoušelo** — nahlásilo by
  testovacího bota doopravdy.
- **Smazání z výběru ověřené taky** (14. 10. 2026, build z `main` po sloučení): tři vybrané
  zprávy bota → „Smazat 3 zprávy?“ s „Autor zpráv: Notifikator“ a všemi třemi volbami; jen se
  zaškrtnutým „Vyhodit“ zmizely právě vybrané zprávy (`deleteMessages`, ne `…BySender`) a bot byl
  vyhozený. Volby dočte `deleteSelection` ve ViewModelu (`loadSenderModeration`), ne výběr sám.
- **Smazání reakcí ověřené** (15. 10. 2026, „Plume test moderace"): bot přes Bot API
  (`setMessageReaction`) dal 👍 na svou zprávu, pak se smazala jiná jeho zpráva jen se
  zaškrtnutým „Smazat nedávné reakce autora". **Reakce nezmizí hned, ale za pár vteřin**: update
  o reakcích přijde až s obnovou zobrazených zpráv, ne jako odpověď na volání. Kdo to bude ověřovat
  znovu, ať počká, než usoudí, že to nefunguje. Funguje jen v supergroupě — dialog volby navíc
  v základní skupině nenabízí vůbec, i když TDLib smazání reakcí umí i tam.

## Překlad zprávy

„Přeložit" v nabídce nad zprávou otevře sheet s překladem do jazyka aplikace
(`translateMessageText`, `TranslationSheet`, `MessageMenuAction.Translate`).

- **Sheet, ne náhrada textu v bublině**, jako u oficiálního Android klienta. Překlad je
  jednorázové nahlédnutí; v bublině by se musel pamatovat u každé zprávy a přežít úpravu.
- **Stojí na témže právu jako kopírování** (`canCopy`): bez textu není co překládat a chráněný
  obsah ven nesmí ani jinou řečí. Anketa ho nemá (její text je náš popis).
- **Výchozí cílový jazyk je `platformLanguageCode`** — tentýž zdroj jako jazyk pro TDLib, tedy
  i jazyk nastavený jen pro aplikaci. Název jazyka dává systém (`localizedLanguageName`, jako
  `localizedCountryName`).
- **Jiný jazyk jde vybrat ťuknutím na „Do jazyka: …"** (`TranslationLanguageSheet`, seznam
  a řazení `translationLanguageOptions`, testy `TranslationLanguagesTest`) a platí pro celou
  aplikaci (`AppSettings.translationLanguage`). **Jazyk aplikace se neukládá kódem, ale jako
  `null`**: po přepnutí jazyka aplikace se pak překládá do nového, ne do toho, který v ní byl
  kdysi. Nahoře seznamu je jazyk aplikace, pak ostatní abecedně; hledá se bez diakritiky.
- **Výběr je sheet NAD sheetem překladu**, jako detail sady nad výběrem samolepek: gesto zpět
  zavře jen výběr. Obsah vyměněný uvnitř jednoho sheetu by zpět zavřelo celé.
- **Seznam jazyků drží aplikace, TDLib ho lokálně nekontroluje** (v knihovně žádný není) —
  je z dokumentace `translateText`, bez aliasů (`iw`, `in`, `ji`); Java u hebrejštiny
  a indonéštiny vrací právě ty zastaralé kódy, proto `normalizedLanguageCode`.
- **Čínština podle regionu (`zh-CN`, `zh-TW`), ne podle písma.** `getDisplayName` pojmenuje
  `zh-Hant` „Čínština (han (tradiční))"; `zh-TW` je „Čínština (Tchaj-wan)" a server ho bere
  (ověřeno: přišla tradiční znaková sada). `localizedLanguageName` proto bere celý kód i s regionem
  (`getDisplayName`, na iOS `NSLocaleIdentifier`), ne jen jazyk.
- **`translateMessageText` má v TDLib 15 čtvrtý parametr `tone`**; prázdný = výchozí.
  Bez Premium přijde prostý text, s ním i formátování.
- **Odkazy, zmínky a hashtagy se v překladu nekreslí jako klikací** — sheet nemá konverzaci,
  kam by vedly. Zůstane jen formátování.
- **Kopírování sheet zavře a potvrdí snackbarem** — pod modálním sheetem by vidět nebyl.
- **Server překládá doslova i značky**: literální `**tučně**` v anglické zprávě vrátil jako
  `<b>tučně</b>`. Je to vlastnost překladu, ne chyba vykreslení.
- **Ověřeno na emulátoru** (aplikace přepnutá do češtiny, anglická zpráva od testovacího bota):
  položka v nabídce, načítání, překlad, kopírování se snackbarem; výběr jazyka s hledáním
  („cin"), překlad do čínštiny (Tchaj-wan), zapamatování po znovuotevření, zpět zavře jen výběr,
  a po volbě češtiny a přepnutí aplikace do angličtiny „To English". **iOS jen přeložené.**
- **Zpráva, která už JE v jazyce překladu, „Přeložit" nemá** (`MessageMenu.inTranslationLanguage`,
  pravidla `alreadyInLanguage` a `languageDetectionText`, testy `AlreadyInLanguageTest`). Jazyk
  rozpoznává zařízení (`LanguageDetector` v `core/language`: Android `TextClassifier.detectLanguage`
  od Androidu 10, iOS `NLLanguageRecognizer`); nic se neposílá a nic se nestahuje. Starší Android
  rozpoznávat neumí a položku nabízí vždycky.
  - **Rozpoznává se při otevření nabídky, souběžně s právy**, se stropem 300 ms — nabídka čeká na
    všechny úlohy a pomalý první start modelu ji nesmí zdržet. Nestihnuté = nabídnout.
  - **Chyba směrem „schovat" je horší než „nabídnout"**, proto: krátký text (pod deset písmen —
    „ok", „Zítra?") se nerozpoznává vůbec, a čínština se neschovává nikdy (cíl je písmo
    `zh-CN` / `zh-TW`, rozpoznání ho nerozliší).
  - **Práh jistoty je 0,7, ne 0,8** (`MIN_LANGUAGE_CONFIDENCE`). Změřeno na emulátoru: „Dneska je
    krásné počasí, pojďme se projít do parku." dostala češtinu jen 0,76 (zbytek slovenština),
    přitom slovenskou a polskou větu model pozná na 0,99 a češtinu u jiných vět na 0,998.
    Hypotézy se dají vypsat dočasným logem v `AndroidLanguageDetector` (`getLocale(i)`,
    `getConfidenceScore`); necommituje se.
  - **Ověřeno na emulátoru** (aplikace česky): dvě české věty bez „Přeložit", slovenská, polská,
    anglická a krátké „Zítra?" s ním. **iOS jen přeložené.**
  - **Pozor při ověřování přes `adb`**: zpět po zavřené nabídce odejde z konverzace a další dlouhé
    stisky trefí řádky seznamu chatů (se skutečnými lidmi). Před zpět ověřit dumpem, že je nabídka
    otevřená (text „Odpovědět").
- **Dlouhý stisk přes `adb` podle polohy z dumpu se může trefit do odkazu**, když se seznam mezi
  dumpem a stiskem posune — pak se místo nabídky otevře prohlížeč. Poloha se bere těsně před
  stiskem a mimo řádek s odkazem.

### Překlad v telefonu

Přepínač „Překládat v telefonu" v sheetu překladu (`AppSettings.translateOnDevice`) přeloží zprávu
ML Kit Translation přímo v telefonu (`core/translation`, `OnDeviceTranslator`, `MlKitTranslator`);
text neodchází na server. Volba se pamatuje pro další překlady, výchozí je server.

- **Jen Android.** iOS má Translation framework jen pro SwiftUI; v DI tam překladač není
  a přepínač se nekreslí (`TranslationSheet.onDevice == null`). Shrnutí od serveru ho nemá
  nikdy — shrnutí dělá jen server.
- **Jazykové balíčky se stahují jen na výslovné „Stáhnout"** (~40 MB na jazyk, bez podmínky
  Wi-Fi, protože o stažení uživatel ví). Než je potvrdí, sheet nabízí i „Přeložit na serveru".
  Na emulátoru stažení čeština + angličtina trvalo pod 20 s.
- **ML Kit překládá přes angličtinu** a anglický model má vestavěný, proto ho `hasModels`
  nekontroluje. Pár „polština → čeština" chce dva stažené balíčky.
- **Jazyk zprávy rozpozná `LanguageDetector`** (tentýž jako u „Přeložit" v nabídce). Krátký nebo
  nejistý text nerozpozná — pak chyba s tlačítkem „Přeložit na serveru", ne tichý pád na server:
  uživatel si překlad v telefonu zapnul právě proto, aby text na server nešel.
- **Kódy jazyků jen bez regionu** (`deviceTranslationCode`, testy `DeviceTranslationCodeTest`):
  „pt-BR" → „pt". Tradiční čínštinu (`zh-TW`) ML Kit nemá a jeho „zh" je zjednodušená, takže
  `zh-TW` vrací `null` = „tyhle jazyky telefon neumí".
- **Kvalita je znát**: „…let's go for a walk in the park and grab a coffee" → v telefonu „pojďme
  na procházku v parku a chytit kávu", server „pojďme se projít parkem a dát si kávu". Proto
  je výchozí server a přepínač říká, co za soukromí dostaneš (text neodejde na server).
- **APK +10 MB** (`libtranslate_jni.so`, 16 MB nerozbalená na arm64) — ke stažení z Play ~6–7 MB
  na ABI. Vědomé rozhodnutí, viz roadmapa.
- **Odpověď serveru, která dorazí po přepnutí na telefon, se zahodí** (`onDevice != true`
  v kontrole otevřeného sheetu), jinak by serverový překlad přepsal ten z telefonu.
- **Ověřeno na emulátoru Fold** (Android 17, česky): anglická zpráva od testovacího bota,
  přepínač, výzva ke stažení, stažení, překlad v telefonu, zapamatování po znovuotevření (bez
  výzvy, hned), vypnutí zpět na server. **Neověřeno**: nerozpoznaný jazyk a nepodporovaný pár
  v praxi (logika je jen přeložená), iOS (přepínač tam není).

## Shrnutí od serveru

„Shrnout" v nabídce nad dlouhou zprávou otevře tentýž sheet jako překlad, jen s jinými texty
(`TranslationSheet.summary`, `MessageMenuAction.Summarize`, `summarizeMessage`). Je to jiná věc
než „Shrnout" v hlavičce konverzace (model v zařízení, viz „AI v zařízení") — tohle shrnuje
JEDNU zprávu a počítá to Telegram.

- **Kdy se nabídne, rozhoduje server** (`Message.summaryLanguageCode` → `ChatMessage.summaryLanguage`,
  prázdný = shrnout nejde). Aplikace délku zprávy neposuzuje; v kanálu s krátkými hlášeními ho
  měl jediný dlouhý příspěvek.
- **Nabízí se i u chráněného obsahu**, na rozdíl od překladu — dostupnost řídí server. Jen se
  tam výsledek nedá zkopírovat (`TranslationSheet.canCopy`): chráněný obsah ven nesmí ani
  převyprávěný. Testovací kanál GFN Catalog chráněný je, a „Shrnout" se tam napřed neukázalo,
  protože stálo na `canCopy` jako překlad.
- **Jazyk je tentýž jako u překladu** (`AppSettings.translationLanguage`, výběr ve sheetu ho
  mění pro obojí). Když se shoduje s jazykem zprávy, posílá se prázdný — server by jinak
  „překládal" sám do sebe.
- **Bez Premium je shrnutí omezené**: `SUMMARY_FLOOD_PREMIUM` má vlastní větu v `userMessage`.
  Sheet proto nese důvod (`TranslationSheet.error: UiText?`), ne jen příznak selhání — platí to
  i pro překlad. **Testovací účet Premium nemá a chybu dostal hned u prvního pokusu**, takže
  samotné shrnutí (text, změna jazyka, kopírování) ověřené NENÍ.
- **Pod shrnutím je věta, že ho psal Telegram a může něco vynechat** — text nepíše odesílatel.

## Automatické mazání zpráv

Chat může mít nastavené, že nové zprávy po době samy zmizí (`Chat.messageAutoDeleteTime`).
Hlavička konverzace to ukazuje odznakem s hodinami za názvem (`AutoDeleteBadge`, „⏱ 1 d"),
„Informace" u zprávy říkají, kdy se smaže (`ChatMessage.autoDeleteAt`), a v profilu chatu je
řádek s výběrem doby (`AutoDeleteSheet`, `setChatMessageAutoDeleteTime`).

- **Doba je živá** (`autoDeleteTime` nad `UpdateChatMessageAutoDeleteTime`, odběr před prvním
  čtením) — mění ji i protějšek nebo jiné zařízení.
- **`autoDeleteIn` je zbývající čas, převádí se na okamžik** při převodu zprávy. Zbývající
  sekundy by se počítaly od doby načtení a v „Informace" by lhaly.
- **Budoucí čas jde přes `asUpcomingTime`**, ne `asDayLabel` (ten je na minulost).
- **Kdo smí měnit**: soukromý chat (i s botem) ano, Uložené zprávy ne, tajný chat ano, dokud
  běží — tam je to časovač po přečtení v sekundách až týdnech (`docs/poznatky/tajne-chaty.md`), skupina a kanál jen s právem měnit údaje chatu (`canChangeInfo`),
  jak to vyžaduje TDLib. Kdo měnit nesmí, vidí řádek jen při zapnutém mazání, bez klikání.
- **Nabízí se vypnuto, den, týden a měsíc** — jako oficiální klienti. **Měsíc je 31 dní**
  (2 678 400 s), tak ho Telegram posílá; jiná doba nastavená jinde se do výběru zařadí
  (`autoDeleteOptions`, testy `AutoDeleteOptionsTest`).
- **Délka se píše jedním formátovačem** (`durationText` / `durationString` / `durationShortText`
  v `core/ui/Durations.kt`, testy `DurationUnitTest`), sdíleným s dobou ankety. Servisní zpráva
  proto říká i dobu („Automatické mazání zpráv: 1 den"), dřív jen „Zprávy se budou samy mazat".
- **Věta s „po" by v češtině chtěla 6. pád** („po 1 dni"), plurály jsou ale v 1. pádu — texty
  jsou proto s dvojtečkou („…: 1 den").
- **Výchozí doba pro NOVÉ chaty je v Nastavení → Obecné → Soukromí** (`DefaultAutoDeleteSettings`
  nad `get`/`setDefaultMessageAutoDeleteTime`, `DefaultAutoDeleteViewModel`, testy
  `DefaultAutoDeleteViewModelTest`). Tentýž `AutoDeleteSheet` jako v profilu, jen s větou, že se
  týká chatů založených odteď — existující chaty nemění, a bez té věty by se to dalo čekat. Je
  to nastavení ÚČTU jako citlivý obsah: volba se pohne hned, po selhání se vrátí s větou pod
  řádkem, a dokud doba nedorazí, řádek se nekreslí. Stejná volba se nezapisuje. **Zápis mění
  skutečný účet, takže se ověřuje jen čtení a otevření výběru** (zavřít gestem zpět).
- **Ověřeno na emulátoru** v chatu s testovacím botem (česky): řádek v profilu, sheet, nastavení
  1 dne (odznak v hlavičce, servisní zpráva s dobou), „Smaže se: zítra 20:54" u nové zprávy
  a vypnutí (odznak zmizel, servisní zpráva „vypnuto"). **Ve skupině ověřené** (testovací
  skupina, vlastník): nastavení i vypnutí s odznakem a servisními zprávami. Kanál a správce bez
  vlastnictví ověřené nejsou, **iOS jen přeložené**.

## Výběr víc zpráv

Položka „Vybrat" v nabídce nad zprávou přepne konverzaci do režimu výběru: hlavička ukazuje ✕
a počet, místo vstupního pole je plovoucí toolbar s akcemi (kopírovat, přeposlat, smazat) a každá
zpráva má na volné straně kolečko. Stav je `ConversationState.selection` (`MessageSelection`,
čistá data a odvozená práva, testy `MessageSelectionTest`); `null` = režim neběží.

- **Vybírají se BLOKY seznamu, ne zprávy** (`MessageSelection.keys` jsou klíče položek). Album je
  v konverzaci jedna věc a přeposlat nebo smazat půlku alba nedává smysl; na id zpráv se klíče
  rozbalí až při akci (`selectedMessageIds`), stejně jako u nabídky nad jednou zprávou.
- **Klíč bloku je klíč jeho NEJSTARŠÍ zprávy** (`blockKeyOf`), takže ťuknutí na kteroukoli fotku
  alba vybere celé album. Ťuknutá fotka sama klíč bloku nenese.
- **Vstupem je položka nabídky, ne rovnou dlouhý stisk.** Dlouhý stisk už patří nabídce a ta je
  ta častější; zároveň by z něj nešlo poznat, že se vybírá. „Vybrat" se přitom nabízí VŽDY,
  jako „Informace" — na právu od serveru nestojí a co výběr dovolí, se ukáže až na jeho liště.
- **Práva se načítají pro každý vybraný blok zvlášť** (`getMessageProperties` při přidání do
  výběru) a skládají se přes `all`, ne `any`: akce platí pro CELÝ výběr, takže přeposlat půlku
  vybraného a o druhé mlčet by znamenalo, že uživatel neví, co odešlo. Dokud nedorazí všechna,
  jsou akce zašedlé.
- **Tlačítka v toolbaru NEMIZÍ, jen zšednou.** Práva chodí po jednom, takže by se toolbar při
  každém ťuknutí přeskládal pod prstem — a zmizelá akce neřekne, že ji zrovna tenhle výběr
  nedovoluje. Výjimka z pravidla „co server nedovolí, se nenabízí": tam jde o nabídku, která se
  otevře hotová, tady o lištu, která žije s měnícím se výběrem.
- **Ťuknutí bere PŘEKRYV nad celým řádkem**, ne `onTap` bubliny. Potomci s vlastním klikáním
  (fotka, GIF, odkaz, citace, náhled odkazu, samolepka) si ťuknutí berou sami a k rámu ho
  nepustí — bez překryvu by ťuknutí na fotku v režimu výběru otevřelo prohlížeč. Překryv bere
  i dlouhý stisk (taky přepíná výběr) a **tah na odpověď se v režimu výběru vypíná úplně**:
  překryvu by propadl a založil odpověď uprostřed vybírání.
- **Vybraná zpráva má pruh přes celou šířku, ne obarvenou bublinu.** Odchozí bublina je sytá
  `primary` a barvu by nenesla, a zpráva bez bubliny (fotka, samolepka) nemá co obarvit.
  `secondaryContainer` je tatáž role jako vybraná položka v seznamu chatů — výběr je v M3
  selekční stav, ne zvýraznění obsahu. Průsvitný (`SELECTION_TINT_ALPHA`), aby text zůstal
  čitelný, a zahrnuje i mezeru nad zprávou, takže z vybraných sousedů vznikne souvislý blok.
- **Kolečko stojí na VOLNÉ straně zprávy** — u odchozí vlevo, u příchozí vpravo. Volný pruh tam
  je vždycky (zpráva zabere nejvýš `MESSAGE_MAX_WIDTH_FRACTION` řádku), takže se kvůli němu nic
  neposouvá; Telegram místo toho obsah odsouvá a kolečko vsouvá zleva. Samotné podbarvení
  nestačí: u jediné vybrané zprávy by z něj nebylo poznat, že jdou vybírat i další.
- **`state.selection` MUSÍ být v klíčích `remember` nad builderem bloků**, jinak se seznam
  nepřestaví a zaškrtnutí se neobjeví. Chytlo se to až na zařízení: první zpráva se označila
  (vstup do výběru zároveň zavřel nabídku, a `messageMenu` v klíčích je), další už ne. V klíčích
  jsou jen KLÍČE výběru — práva k němu chodí po jednom a seznam bloků se jimi nemění.
- **Odebrání poslední vybrané režim ukončí.** Zůstat v něm s nulou vybraných je stav, ve kterém
  nejde nic dělat a není jasné proč.
- **Zpět ukončí výběr**, teprve další odejde z chatu — `NavigationBackHandler`, jako u hledání.
- **Strop je sto zpráv** (`MAX_SELECTED_MESSAGES`). `deleteMessages` i `forwardMessages` berou
  v TDLib nejvýš sto naráz, takže vyšší výběr by skončil chybou až u akce.
- **Kopíruje se chronologicky, s prázdným řádkem mezi zprávami.** Zprávy bez textu se vynechají;
  popisek alba nemusí být na ťuknuté fotce, takže se text hledá v celém bloku.
- **Kopírovat nabídne tři podoby** (`ExpressiveMenu` nad tlačítkem): „Jen text" jako dřív, „S časem
  a jménem" (`selectionTranscript`, testy `SelectionTranscriptTest`) — řádek na zprávu,
  `[10. 10. 2026 14:22] Jana: text`, pro jazykový model — a „Markdown" pro poznámky
  (`selectionMarkdown`, převod `markdownOf`, testy `MessageMarkdownTest`).
  - **U každé zprávy celé datum**, ne jen čas: kus přepisu vytržený z kontextu musí říct, kdy se
    to stalo. Datum v pořadí jazyka (`date_numeric`), čas 24hodinově.
  - **Jména jako přepis pro AI shrnutí**: odchozí „Já" (`summary_me`), příchozí jméno
    odesílatele, a kde ho TDLib nevyplňuje (soukromý chat, bot), název chatu.
  - **Příloha v hranatých závorkách** („[Fotka] popisek", samotné „[Fotka]"). Popis přílohy je
    proto `MessageMedia.describeText()` jako `UiText` — dřív jen `@Composable describe()`, který
    ViewModel přeložit nemohl. U kostky, kontaktu a animovaného emoji popis závorky nemá, je
    zároveň textem.
  - **Markdown má hlavičku na vlastním řádku** (`**Jana** · 10. 10. 2026 14:22`, pod ní text,
    mezi zprávami prázdný řádek): blok kódu a citace musí v Markdownu začínat řádek, za
    „Jana: " by se nepoznaly. Ze stejného důvodu `markdownOf` před blok kódu nebo citaci
    uprostřed řádku vloží zalomení a citaci dá `> ` na každý řádek.
  - **Převádí se jen to, co Markdown umí**: tučné `**`, kurzíva `_`, přeškrtnuté `~~`, kód
    a blok kódu s jazykem, odkaz s popiskem, spoiler `||`, citace. Podtržení Markdown nemá,
    zmínky a hashtagy jsou čitelné samy. Odkaz psaný přímo v textu se nebalí (`[x](x)`).
  - **Značka v řádku obejde mezery na krajích úseku** — `** tučně**` Markdown za tučné nevezme,
    a Telegram mezeru do entity klidně zahrne.
  - **Značky se stejnými kraji se zavírají obráceně, než se otevřely** (pořadí otevření v klíči
    řazení zavírání). Bez toho vyšlo z tučné kurzívy přes celé slovo `_**ab_**`.
  - **Znaky Markdownu v textu se neescapují** — `\*` by poznámková aplikace ukázala doslova.
    Za cenu, že výjimečně něco omylem zformátuje.
  - Ověřeno na emulátoru (Fold): tři zprávy (vlastní a dvě od bota), vložené do pole a přečtené
    z `uiautomator`. Markdown na zprávě od bota s HTML formátováním (`parse_mode=HTML`): tučné,
    kurzíva, odkaz s popiskem a kód vyšly správně. Schránku `adb` na novém Androidu číst neumí, vložení do vstupního pole
    (Ctrl+V) ano — **pole po kontrole vyčistit**, jinak zůstane koncept, který se synchronizuje
    na ostatní zařízení. **Po kopírování pár vteřin počkat**: Android ukáže vlevo dole překryv
    schránky a ťuknutí do vstupního pole trefí jeho „Sdílet" (otevřelo nastavení Quick Share).
- **Tělo potvrzení mazání má vlastní věty pro víc zpráv** (`delete_messages_*`), ne plurál nad
  jednou větou: čeština i angličtina v nich mění víc než jedno slovo. Titulek plurál má
  (`conversation_delete_selected_title`), protože nese počet.
- **`PendingDelete.count` NENÍ `messageIds.size`.** Album je v bublině jedna věc, i když je to
  pět zpráv — titulek „Smazat 5 zpráv?" nad jedním albem by lhal.

## Informace o zprávě

Položka „Informace" v nabídce nad zprávou otevře bottom sheet: kdy zpráva odešla, kdy byla
upravená, kdy ji protějšek přečetl, kdo ji zhlédl a odkaz na ni (`MessageInfoSheet`,
`messageInfo` v `core/telegram`).

- **Sheet, ne popup ani řádky v nabídce.** Údajů je proměnlivý počet a zhlédnutí je celý seznam
  lidí, takže se v tom musí dát listovat. **Je to jednorázová akce, ne podnabídka** — proto
  nabídku ukončí a otevře samostatný sheet, na rozdíl od výběru reakcí, který zůstává uvnitř
  popupu (viz „Nabídka nad zprávou").
- **Jako jediná položka nabídky se nabízí VŽDY.** Nestojí na právu od serveru: čas odeslání má
  konverzace sama, takže sheet má co ukázat i u zprávy, u které server odkaz, zhlédnutí ani čas
  přečtení nevydá.
- **Tři doplňky, tři práva, jeden dotaz naráz.** `canGetReadDate`, `canGetViewers` a `canGetLink`
  se čtou z `getMessageProperties` a příslušná volání jdou **současně** se zprávou samotnou.
  Selhání jednoho ostatní nezahodí — server řekne ne u kdečeho a prázdný řádek je lepší než
  prázdný sheet.
- **Načítá se až při otevření.** Skoro žádná zpráva nemá všechna tři práva; ptát se dopředu by
  znamenalo tři dotazy na každou zprávu v historii kvůli sheetu, který se otevře jednou za čas.
- **Čas přečtení má PĚT podob, ne „čas nebo nic"** (`MessageReadState`): přečteno, nepřečteno,
  server už čas nedrží, protějšek má časy skryté, a „skrýváš je sám, takže je nevidíš ani ty".
  Každá je vlastní věta — „—" by uživateli neřeklo, jestli zprávu nikdo nepřečetl, nebo jestli je
  čas jen skrytý. Poslední dvě se od sebe liší tím, kdo to může změnit. Podoba „přečteno"
  ověřená se dvěma účty (12. 10. 2026): „Odesláno 17:54, Přečteno 17:56" sedělo na minutu
  s tím, kdy protějšek zprávu přečetl.
- **Ťuknutí na odkaz ho zkopíruje a sheet zavře.** Snackbar s potvrzením by pod modálním sheetem
  zůstal schovaný a nebylo by poznat, že se něco stalo. Na Androidu 13+ snackbar není vůbec —
  zkopírování tam potvrdí systémový náhled schránky (viz „Hláška „Zkopírováno"" v CLAUDE.md);
  iOS a starší Androidy ho mají. Otevírat odkaz na zprávu, u které uživatel právě stojí,
  nedává smysl.
- **`getMessageLink` má sedm parametrů** (`mediaTimestamp`, `checklistTaskId`, `pollOptionId`,
  `forAlbum`, `inMessageThread`) — pojmenované argumenty, jako u všeho generovaného.
- **Typ odesílatele se jmenuje `SenderInfo`, ne `ReactionSender`.** Používají ho i zhlédnutí
  a název podle reakcí by u nich lhal.

## Záložky zpráv

„Přidat do záložek" v nabídce nad zprávou označí zprávu jen pro uživatele, v tomhle zařízení
(`MessageBookmarks` v `core/bookmarks`, uložené v `AppSettings`). Seznam je v profilu chatu
(řádek „Záložky" s počtem, `BookmarksSheet`); ťuknutí profil zavře a konverzace skočí na zprávu.

- **Lokálně, ne přes server.** Telegram nic jako soukromou záložku na zprávu nemá — připnutí
  vidí všichni a chce práva, Uložené zprávy jsou kopie (přeposlání), ne odkaz na místo v chatu.
  Synchronizace přes CloudStorage mini aplikace by šla (viz „Synchronizace nastavení mezi zařízeními bez vlastního backendu"
  v `nastaveni.md`), ale za cenu bota navíc; záložky proto zůstávají v zařízení.
- **Záložka nese otisk zprávy** (text, odesílatel, čas) z doby uložení. Seznam se ukáže hned,
  bez načítání zpráv po jedné, a záložka smazané zprávy pořád říká, co to bylo. Úprava zprávy
  se do otisku nepropíše. Text je `transcriptBody` jako u kopírování výběru — u fotky bez
  popisku tedy „[Fotka]", ne prázdno.
- **Účet je id Uložených zpráv** (`ChatRepository.savedMessagesChatId`): je to chat s vlastním
  účtem a jeho id je id uživatele. Po odhlášení a přihlášení jiného účtu se záložky nepromíchají;
  dokud účet známý není, přidat nejde (snackbar nic neohlásí, položka se jen nepřepne).
- **Skok na zprávu jde přes `ConversationJumpRequests`, ne přes routu.** Konverzace z profilu
  je obvykle už otevřená pod ním (profil se otevírá z ní) a nová `ConversationRoute` s `messageId`
  by ji nezaložila znovu — ViewModel téhož chatu přežívá a parametr z routy by ignoroval (viz
  „ViewModel na detailu MUSÍ mít klíč obsahující id" v CLAUDE.md). Profil proto požadavek
  zapíše, sám se ze zásobníku odebere a otevřená konverzace si požadavek vezme (`consume`)
  a skočí; když otevřená není, přidá se routa a požadavek si vezme nově založený ViewModel.
- **Poslední odebraná záložka sheet zavře a řádek z profilu zmizí** — řádek se ukazuje jen při
  nenulovém počtu, jako záložky sdílených médií („Nabízejí se jen neprázdné záložky").
- **Ověřeno na emulátoru** (Fold rozložený, česky): přidání a odebrání z nabídky nad zprávou,
  řádek „1 zpráva / Záložky" v profilu, sheet, skok na zprávu se zvýrazněním, odebrání ze sheetu.
  Hledání záložek zatím není. **iOS jen přeložené.**

## Připomenutí zprávy

„Připomenout…" v nabídce nad zprávou (`MessageMenuAction.Remind`, `RemindSheet`) nabídne
časy jako odložení chatu (za hodinu, dnes večer, (zítra) ráno — `SnoozeOptionRows`) a „Vybrat
datum a čas" (tytéž dialogy jako plánování zprávy, s titulkem „Připomenout v"). Zpráva se pak
**naplánovaně přepošle do Uložených zpráv** (`ForwardOptions.schedule`, `forwardMessages`
s `schedulingState`).

- **Server, ne budík v telefonu.** Takhle dělají „Připomínky" oficiální klienti: naplánovaná
  zpráva v Uložených zprávách přijde v čase s upozorněním. Žádné oprávnění k přesným budíkům,
  žádná vlastní notifikace, připomínka přežije přeinstalaci a přijde i na jiném zařízení.
  Naplánovaná je vidět v Uložených zprávách pod hodinami u pole a jde zrušit nebo přeplánovat
  jako každá naplánovaná zpráva.
- **Přeposlání, ne odkaz.** Odkaz na zprávu (`getMessageLink`) mají jen skupiny a kanály;
  přeposlaná zpráva nese obsah i „Přeposláno od …", které vede zpátky do chatu. Proto se
  položka nabízí jen tam, kde server přeposlání dovolí (`canForward`) — u chráněného obsahu
  ne. Album jde celé (`albumMemberIds`).
- **Nejdřív za minutu** (`scheduleValid`): čas vybraný ve dialogu na minuty, který je blíž,
  se odmítne snackbarem „čas už minul" — při testu na emulátoru se na to snadno narazí.
- **Výsledná notifikace má příznak `SILENT`, a přesto upozornila.** Připomínka přijde pushem:
  TDLib napřed vydá dočasnou notifikaci z pushe (ta upozorní) a za pár vteřin ji odebere
  a přidá skutečnou k téže zprávě, kterou presenter pozná jako náhradu a pustí potichu
  (`recentlyRemoved`). `dumpsys notification` ukazuje jen poslední stav; jestli zvonilo, říká
  `logcat -b events | grep notification_alert` (třetí pole 1 = zvuk). Kvůli tomu je
  v presenteru ladicí řádek „Notifikace potichu: od TDLib=…, jen náhrady=…, ticho=…".
- **Termín ze zprávy je první volba** („Podle zprávy · zítra 15:00", `RemindRequest.suggestedAt`).
  Vytahují ho pravidla (`reminderTimeFromText`, testy `ReminderTimeTest`), ne model: je to
  okamžité, testovatelné a nic se kvůli tomu nestahuje. Česky i anglicky, bez diakritiky
  (`foldDiacritics`, takže v kódu nejsou české literály a „zitra" platí jako „zítra").
  - **Vztažná slova se počítají od ODESLÁNÍ zprávy**, ne od teď: „zítra v 10" napsané včera
    je dnes v 10. Termín, který už minul (nebo je blíž než minuta), se nenabídne.
  - **Čas bez dne** je den odeslání, a když tehdy už minul, další den („v 8" večer = ráno).
    **Den bez času** dostane ráno 8:00 nebo část dne ze zprávy („v pátek večer" → 19:00).
    **Týž den v týdnu** jen s časem, který ještě nebyl; bez času příští týden.
  - **Radši nic než nesmysl**: čas chce dvojtečku, am/pm, nebo předložku („v 9.30", „v 15") —
    „Verze 5.10", „Skóre 3:1" ani „přijde 5 lidí" termín nejsou. Datum chce tečku i za měsícem
    („12. 10."), takže „5.10" je číslo. Samotná část dne („ráno super") termín není.
  - **Pořadí rozhoduje**: datum se z textu vyřízne dřív, než se hledá čas („12.10." by jinak
    bylo 12:10), a am/pm dřív než dvojtečka („3:30pm" by jinak bylo 3:30 ráno).
  - Ověřeno na emulátoru: „Sraz zítra v 15:00 u kina" → „Podle zprávy · zítra 15:00",
    u zprávy bez termínu řádek není.
- **Když pravidla nenajdou nic, zeptá se model v zařízení** (`ReminderTimeModel`, prompt
  `reminderPrompt` a čtení `parseReminderTime` v `core/ai/ReminderPrompt.kt`, testy
  `ReminderPromptTest`). Jen se zapnutou AI v zařízení a s modelem `Ready` — kvůli připomínce se
  nic nestahuje. Termín, který pravidla našla a který už minul, se modelu nedává (nespraví ho).
  - **Sheet se otevře hned** a nahoře se točí „Hledám termín ve zprávě…"; ostatní volby jdou
    vybrat mezitím. Návrh od modelu má ikonu AI místo kalendáře — je to odhad, ne pravidlo.
    Selhání nebo `NONE` = řádek zmizí, žádná chyba. Strop 60 s.
  - **Výstup je `YYYY-MM-DD HH:MM` nebo `NONE`** a čte se tolerantně (věta kolem, „T"), ale
    přísně k obsahu: termín musí být po odeslání zprávy a nejvýš rok po něm.
  - **Části dne musí prompt vyjmenovat i s časy.** Bez toho Gemma „za týden po obědě" vrátila
    týden dopředu v 08:00 (výchozí „jen den"); s výčtem 12:00 — „po obědě" bere jako oběd,
    13:00 by sedělo líp. Na E2B únosné, víc se z promptu vymáčknout nepovedlo bez rizika.
  - **Ověřeno na emulátoru s Gemmou 4 E2B** (Fold AVD s 8 GB RAM, CPU): „Sejdeme se za týden po
    obědě u fontány" (odesláno v sobotu 10. 10.) → „Podle zprávy · 17. října 12:00" za 8–9 s
    (první volání po startu 13 s i s načtením enginu); „Díky, to je super nápad" → `NONE`, řádek
    zmizel. **Gemini Nano a iOS neověřené** (iOS model nemá, `NoopOnDeviceAi`).
- **Ověřeno na emulátoru Fold**: nabídka nad zprávou v chatu s Notifikátorem, sheet s časy,
  vlastní čas za dvě minuty, snackbar, připomínka v čase v Uložených zprávách s upozorněním
  (zvuk podle `notification_alert`) a s tlačítky Odpovědět / Označit jako přečtené / Ztlumit.
  „Za hodinu" a „ráno" sdílí kód s odložením chatu, v čase neověřené. **iOS jen přeložené.**

## Přeposílání

„Přeposlat" v nabídce nad zprávou otevře výběr cílového chatu (`ForwardRoute`, vlastní obrazovka);
po odeslání se otevře cílový chat, takže je vidět, že zpráva dorazila.

- **Nabízí se podle `canBeForwarded`** z `getMessageProperties`, jako ostatní položky nabídky.
  Kanál s chráněným obsahem přeposílání zakazuje a nabídnout akci, která skončí chybou, je horší
  než ji nenabídnout.
- **Album se přeposílá celé** (`albumMemberIds`, stejně jako mazání) — v bublině je to jedna věc
  a v cíli má albem zůstat. **Id musí být vzestupně**, jinak TDLib volání odmítne.
- **Výběr je obrazovka, ne sheet.** Seznam chatů je dlouhý a listuje se v něm; sheet by navíc
  při hledání zápasil s klávesnicí. Kreslí ho `ChatPicker` z `core/ui`, společný se sdílením
  i novou konverzací (viz „Jeden výběr chatu").
- **Bez dotazu je ve výběru živý hlavní seznam, s dotazem výsledky `ChatSearch`** — ze všech
  chatů, i archivovaných (`pickerChats`, viz „Hledání chatů").
- **Výběr vypadá jako seznam chatů, ne jako systémový dialog**: hledá se v `SearchPill`
  a chaty jsou blok zaoblených řádků (`groupItemShape`, `surfaceContainerLowest`, spára 2 dp)
  s tváří podle druhu chatu. Vybraný cíl nese `secondaryContainer` a je zaoblený celý — výběr
  je v M3 selekční stav, tatáž role jako u vybraného chatu v dvoupanelu; samotné zaškrtávátko
  na konci řádku se v dlouhém seznamu hledá hůř než barva. Dřív to byl `TextField` s podtržením
  a holé `ListItem`y na pozadí, tedy dva vzhledy pro „seznam věcí" v jedné aplikaci.
- **Prázdný výsledek je `IntroEmptyState`**, jako u hledání v chatech a prázdného archivu.
- **Během odesílání se seznam nedá ťuknout podruhé** — dvě ťuknutí by poslala zprávu dvakrát.
- **Výchozí je přeposlání, kopie je volba** — přepínací tlačítka pod hledáním: „Skrýt
  odesílatele" (`sendCopy`, v cíli není „Přeposláno od …") a s ním „Skrýt popisky"
  (`removeCaption`). Pravidlo je čistá funkce `ForwardOptions.allowedBy` (testy
  `ForwardOptionsTest`).
- **Popisky jdou skrýt JEN u kopie.** TDLib `removeCaption` bez `sendCopy` ignoruje, takže se
  druhé tlačítko ukáže až se zapnutým prvním — a jen když přeposílané zprávy nějaký popisek
  nesou (médium s textem; u typů bez přílohy je text naše věta, ne popisek).
- **Kopii nabízí jen `canBeCopied`** u VŠECH zpráv (`forwardCapabilities`, zjišťuje se jednou
  při otevření, zpráva a příznaky souběžně). Volba se ukáže až po odpovědi — zmizelá by
  vypadala jako chyba; když se zjistit nepodaří, nenabídne se.
- **Volby se nepamatují.** Přeposlání bez odesílatele je rozhodnutí o konkrétní zprávě; zapnuté
  napořád by příště tiše vydávalo cizí zprávu za vlastní.
- **`animateContentSize` na FAB mu UŘÍZNE stín a spodní nároží.** Modifikátor ořezává na svoje
  hranice, takže plovoucí tlačítko vyšlo dole hranaté, jako by bylo useknuté. Počet cílů se
  proto animuje na POPISKU, ne na tlačítku — tatáž past jako u plovoucí lišty konverzace.
- **Seznam má dole místo na tlačítko** (`FAB_CLEARANCE`), jinak zůstane zaškrtávátko posledního
  chatu schované pod ním. Přidává se, jen když se vybírá (u sdílení tlačítko není).
- **Spodní inset patří do `contentPadding` seznamu, ne na kontejner.** S paddingem ze Scaffoldu
  na sloupci skončil seznam nad navigační lištou ostrou hranou a poslední chat byl useknutý —
  tentýž vzor jako seznam chatů a výsledky hledání: chaty jedou pod lištu a poslední se nad ni
  doroluje. Ze Scaffoldu se na sloupec bere jen horní a boční padding.
- **Scaffold výběru má `contentWindowInsets = safeDrawing`, tedy i klávesnici.** Podle spodního
  insetu staví Scaffold tlačítko a snackbar, takže s výchozími systémovými lištami odesílací
  tlačítko při hledání zmizelo pod klávesnicí. Seznam bere týž inset (`safeDrawing`), aby se
  poslední chat dal doscrollovat i nad ni.
- **Cílový chat se do zásobníku přidá jen, když na něm zásobník už nestojí** — přeposlání do
  téhož chatu by jinak otevřelo druhou kopii konverzace.
- **Přeposílá se do VÍC cílů naráz**, takže ťuknutí na chat ho VYBERE (zaškrtávátko) a odesílá
  se až tlačítkem s počtem. Je to o ťuknutí víc než dřív, ale poslat totéž do víc chatů je běžný
  případ a výběr se dá opravit; stejně to dělá i oficiální klient.
- **Sdílení ze systému zůstává na jednom cíli** (`ChatPicker(selected = null)`). Obsah tam čeká
  nad vstupním polem, takže „vyber a potvrď" by byl krok navíc před dalším krokem.
- **Po odeslání se otevře PRVNÍ vybraný cíl.** Otevřít poslední nebo neotevřít nic by bylo
  svévolné; první vybraný je ten, na který uživatel myslel nejdřív. Množina cílů si proto drží
  pořadí výběru (`Set.plus` vrací `LinkedHashSet`).
- **Do cílů se posílá POSTUPNĚ, ne souběžně.** Pořadí zpráv v cílech tím zůstane dané a server
  nedostane deset přeposlání naráz. Selhání jednoho cíle ostatní nezastaví — co odešlo, odešlo,
  a chyba se ohlásí na konci.
