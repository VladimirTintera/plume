# Android TV

Samostatná aplikace pro televize (`client/tvApp`). Plán a co zbývá: `docs/roadmap.md`, „Android TV".

## Skladba

- **Stojí jen na `:client:core`** (a přes něj `:client:resources`), stejně jako hodinky
  (`docs/poznatky/wear-os.md`). Vrstvu kolem TDLib dostane celou z `coreModule()`
  (`TvApplication`), UI telefonu (`shared`, `appModule()`) ani jeho knihovny do APK nejdou.
  TDLib se startuje s procesem a `ConnectionController` obnovuje spojení po návratu z pozadí.
- **TDLib na televizi běží bez úprav**: Android TV je obyčejný Android a wrapper balí nativní
  knihovnu pro všechna potřebná ABI.
- **ABI `arm64-v8a`, `armeabi-v7a` a `x86_64`.** Velká část televizí a přehrávačů (Chromecast
  s Google TV, spousta televizorů) jede 32bitový systém i na 64bitovém procesoru — mají málo
  paměti. Bez `armeabi-v7a` by se na ně aplikace vůbec nenainstalovala. `x86_64` je kvůli
  emulátoru Android TV na Intelu; TDLib má na ABI přes 15 MB.
- **Hook `TdlibJsonRepair`** dává plugin `plume.tdlib-android` jako u telefonu a hodinek
  (CLAUDE.md: každý vstupní bod s TDLib).
- **`applicationId` je stejné jako telefon** (`eu.tintera.plume`), aby šla televize v Play do
  téhož záznamu. Třídy mají vlastní balíček `eu.tintera.plume.tv`. **`versionCode` je posunutý
  o 200 000 000** (hodinky 100 000 000): Play nevezme dva bundly se stejným číslem.
- **Telefon televizní bundl nedostane**: manifest chce `android.software.leanback`
  (`required="true"`) a dotyk má `required="false"`. Vyšší `versionCode` tedy telefonní
  aplikaci nezastíní — stejná úvaha jako `uses-feature watch` u hodinek.
- **Banner v launcheru** (`drawable-xhdpi/tv_banner.png`, 320 × 180 dp) se generuje
  z `play/tools/icon.py` (`python3 play/tools/icon.py tv`) z téže geometrie pírka jako ikona.
  Google chce v banneru jméno aplikace; vektor text neumí, proto PNG se jménem v Bricolage.
- **Chat se intentem (`eu.tintera.plume.tv.CHAT_ID`) otevře jen v ladicím buildu**
  (`FLAG_DEBUGGABLE`). Aktivita je exportovaná a extra by mohla poslat kterákoli aplikace
  v televizi; otevřením se zprávy označí jako přečtené. Až chat bude otevírat notifikace nebo
  karta Google TV, půjde to přes `PendingIntent` a neexportovanou cestu.
- **Složka obnovená z uloženého stavu se ověří**: když se do pěti vteřin neobjeví mezi složkami
  účtu (smazaná v telefonu, než se proces obnovil), vrátí se hlavní seznam. Jinak záložky
  zvýraznily „Všechny chaty" a seznam zůstal prázdný.
- **Aktivita má `LEANBACK_LAUNCHER` i `LAUNCHER`.** První dává banner v launcheru televize,
  druhý ji ukáže v „Aplikacích" Google TV a dá se spustit `am start` i na emulátoru bez
  televizního launcheru.

## Skladba obrazovky (podle návodů Google)

Podle [TV components](https://developer.android.com/design/ui/tv/guides/components) a
[Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts), procházeno
27. 11. 2026. Co z nich plyne pro Plume:

- **Seznam chatů je svislý dvouřádkový seznam s avatary** (`ListItem`). Přesně to
  Google doporučuje na kolekci, ze které se vybírá jedna položka (lidé a entity kulatě).
  **Immersive list** (řada karet a velký náhled na pozadí, který sleduje fokus) je podle návodu
  pro pár vybraných položek (novinky, populární), ne pro dlouhý seznam — na chaty se nehodí.
  Karty v řadách jsou pro vizuální katalog (objevování kanálů, feed příspěvků).
- **Tvar avataru nese druh chatu jako na telefonu** (`ChatKind.avatarShape()` v `ChatAvatar.kt`):
  člověk kruh, skupina čtyřlaločná sušenka (`MaterialShapes.Cookie4Sided`), kanál zaoblený
  čtverec (`MaterialShapes.Square`). `MaterialShapes` dává `material3`, kterou televize má kvůli
  `LoadingIndicator`; telefonní `avatarShape` ve `:client:shared` je pro televizi mimo dosah,
  proto kopie se stejným mapováním (při změně na telefonu změnit obě). Tvar dostane avatar
  všude, kde se ví druh chatu: seznam, náhled, hlavička konverzace a feedu kanálu, hledání,
  přeposlání, výběr chatu spořiče a řada a prohlížeč příběhů (i zaostřená plocha v řadě má tvar
  autora). Odesílatel (reakce) zůstává kulatý jako na telefonu. Avatar se NEmorfuje: tvar
  nese druh, ne stav — fokus má prstenec a zvětšení.
- **Lem příběhů jde po tvaru avataru**, ne po kruhu (`storyRingSegments`, port `storyRing`
  z telefonu): obrys tvaru přes `PathMeasure`, horní střed se hledá vzorkováním (každý tvar
  začíná obrys jinde) a dílky jdou po směru hodinových ručiček. Ověřeno na emulátoru
  s podstrčenými příběhy u skupiny i soukromých chatů.
- **Čas u chatu je krátký sloupec jako na telefonu** (`asRelativeTime` z `core`: dnes hodina,
  pak „včera", zkratka dne a „26. 11."), i u zpráv ve výsledcích hledání. Dřív tam bylo celé
  datum („26. listopadu", `asDate`): seznam je úzký (kolem 360 dp) a datum bralo místo názvu,
  při písmu systému 1,3 z „Short Drama BL | AI Dramas" zbylo „Short Drama BL | …". S krátkým
  sloupcem „Short Drama BL | AI Dra…". Ověřeno 27. 11. 2026 na emulátoru při 1,3 i 1,0
  (dnešní, včerejší, „út", „so", „17. 11."); výsledky hledání jen přeložené (táž funkce).
- **Z immersive listu si seznam bere náhled**: vpravo zaostřený chat (`ChatPreview`), jeho
  poslední fotka nebo video přes pozadí celé obrazovky (prolíná se i pod záložky) a poslední
  zprávy. Obraz je zarovnaný doprava nahoru (Google: hlavní motiv vpravo nahoře) a zleva
  a zespodu ho kryje přechod do plochy, takže text zůstává čitelný. Načítá se se zpožděním
  300 ms (`getChatHistory`, 20 zpráv) — rychlé projíždění šipkou nemá číst historii každého
  řádku. `getChatHistory` chat neotevírá ani nehlásí přečtení. **Historie se dočítá po
  stránkách** (nejvýš tři čtení, `PREVIEW_PAGES`): TDLib vrací napoprvé jen to, co má v lokální
  databázi, po startu aplikace často jedinou zprávu, a náhled pak ukázal jen ji (ověřeno 28. 11.
  2026, po opravě čtyři zprávy u Notifikátora, Pixelu 7 Pro i skupiny). Mezivýsledek se ukáže
  hned, ale nepřebije bohatší náhled z mezipaměti se stejnou poslední zprávou — návrat na chat
  by jinak bliknul na jednu zprávu.
- **Složky jsou záložky nahoře** (`TabRow` s pilulkou), ne lišta: záložky jsou podle návodu
  pro rovnocenné sekce jedné obrazovky a smí jich být kolik je potřeba. `NavigationDrawer` je
  „páteř" aplikace jen pro hlavní cíle (pět až šest, s ikonami) — první verze ho měla na složky
  a s víc složkami by přetekl. Lišta se vrátí, až budou hlavní cíle (Chaty, Hledání,
  Nastavení). Záložka se vybírá už zaostřením (`onFocus`), jako všude na TV.
- **Všechny záložky mají stejnou výšku obsahu** (`TAB_HEIGHT`, 32 dp; text na střed přes
  `heightIn` + `wrapContentHeight`). `TabRow` kreslí záložky od HORNÍ hrany řádku, ale pilulku
  vybrané nebo zaostřené záložky (její výšky) zarovná ke SPODNÍ. Záložka s lupou (64 px)
  byla vyšší než textové (58 px: Figtree 14 sp s oříznutým prokladem a 6 dp odsazení), takže
  text seděl o 3 dp nad středem pilulky. Změřeno z `uiautomator dump`: řádek i záložka
  s lupou 104–168, textová 104–162, pilulka na snímku 110–168.
- **Text v poli s minimální výškou patří na střed** (`contentAlignment = CenterStart`
  v `decorationBox`). Pole psaní zprávy má `heightIn(min = 56.dp)`, což je víc než řádek
  s odsazením, a `Box` kreslí obsah od horní hrany: text seděl o 4,5 dp nad středem (pole
  494–606 px, text 522–560). Pole bez minimální výšky (heslo, hledání) to nemají.
- **Barvy uvnitř zaostřeného řádku a karty se počítají s plochou `inverseSurface`** (světlou).
  Co bere barvu obsahu (`LocalContentColor`), přepne se samo. Barva nastavená výslovně ne:
  odznak nepřečtených v `primary` (v tmavém schématu taky světlý tón) se zaostřeným řádkem
  skoro splýval, a tečka bez počtu by zmizela. Zaostřený řádek mu proto dává `primaryContainer`,
  jako má avatar vedle. Totéž řeší lem příběhů (`storyRing(onInverse)`).
- **Bezpečné okraje 58 dp po stranách a 28 dp nahoře a dole** (`SAFE_HORIZONTAL`,
  `SAFE_VERTICAL`; 5 % z 960 × 540 dp). Návod má na téže stránce i 48 / 27 dp; drží se
  přísnější varianta. Pozadí okraje nerespektuje (Google: „fill the full screen").
- **Návrh v 960 × 540 dp** (televize 1080p má hustotu 320), mřížka 12 sloupců po 52 dp
  s mezerou 20 dp; šířky karet pro N vedle sebe: 1 = 844, 2 = 412, 3 = 268, 4 = 196 dp.
- **Přehrávač návod nepopisuje** (stránka o médiích mezi komponentami není); ovládání je
  podle systémových přehrávačů, viz „Video".

## UI

- **Compose for TV (`androidx.tv:tv-material` 1.1)**, ne M3 z telefonu: televize se ovládá
  fokusem a šipkami ovladače, komponenty telefonu stojí na dotyku (tvar měnící se při stisku,
  tahy). Zaostřený prvek se v tv-material zvětší a změní plochu — fokus je na televizi jediný
  „kurzor", který uživatel vidí, takže každý ovládací prvek musí jít zaostřit a být vidět, kde
  fokus je.
- **Seznamy jsou z běžného `foundation`** (`LazyColumn`), ne `androidx.tv.foundation`: lazy
  seznamy pro TV jsou zastaralé, běžné od Compose 1.7 fokus a posun za ním umí samy.
- **Jediná závislost na M3 telefonu je `LoadingIndicator`** (`libs.compose.material3`):
  tv-material indikátor čekání nemá. Barvu dostává výslovně z tv schématu — výchozí by si vzal
  z `MaterialTheme` telefonu, který tu není nastavený.
- **Téma je vždy tmavé**: světlá plocha v tmavém pokoji oslňuje a Google ji na TV
  nedoporučuje. Barvy z tapety televize nemá.
- **Paleta je z barvy Plume, ne výchozí tv-material** (`TvColors`). Holé `darkColorScheme()`
  je základní paleta M3: fialová primární barva a plochy s fialovým nádechem, takže televize
  jako jediná část Plume neměla barvy aplikace. Schéma dopočítá materialkolor z téhož seedu
  a stylu jako telefon (`ThemeColor.BLUE`, `Vibrant`; seed je kopie, `shared` televize
  nevidí) a role se přepíšou do schématu tv-material jedna k jedné (`outline` → `border`).
  Volba barvy z telefonu sem nejde: televize má vlastní sezení a nastavení telefonu nevidí.
- **Schéma tv-material nemá stupně `surfaceContainer*`**, proto je nese `TvSurfaces` vedle
  něj. Dialog stojí na `surfaceContainerHigh` jako `AlertDialog` na telefonu. Dřív měl
  `surfaceVariant`, tedy stejnou barvu jako výchozí nezaostřené `Button` z tv-material
  (`surfaceVariant` s průhledností 0,8, ověřeno v bajtkódu `ButtonDefaults.colors`).
  Tlačítka s plochou splývala a jako tlačítko bylo vidět jen to zaostřené (bílé `onSurface`).
- **Navigace jsou dvě úrovně ve stavu** (`TvNavigation`): seznam a otevřená konverzace, zpět
  tlačítkem ovladače (`BackHandler`). Knihovna navigace by na dvě obrazovky jen přibyla.
- **Seznam po návratu z konverzace stojí, kde stál, a fokus je na chatu, ze kterého uživatel
  odešel.** `LazyListState` (zvlášť pro každou složku) a poslední otevřený chat proto drží
  navigace, ne obrazovka — seznam s konverzací z kompozice odchází. Bez vráceného fokusu nebyl
  fokus po návratu nikde a první šipka skočila do lišty složek.
- **Fokus se přenastavuje, dokud uživatel sám nepohne šipkou.** Složka se dočítá po dávkách
  a chaty s vyšším pořadím přibývají NAD zaostřeným řádkem; `LazyColumn` drží první viditelný
  řádek podle klíče, takže zaostřený odjel nad horní okraj a fokus nebyl vidět — první šipka
  dolů pak „naskočila" kdesi uprostřed seznamu (ověřeno na emulátoru při prvním otevření složky
  Kanály). Řešení: dokud fokus nedostane jiný řádek, každá změna seznamu znovu posune
  zaostřený řádek do obrazu a vyžádá fokus (`navigated` v `ChatList`).
- **Při procházení záložek si seznam fokus nebere** (`takeFocus`): přenastavování fokusu výš
  by ho při každém přepnutí složky (záložka se vybírá už zaostřením) vytáhlo ze záložek dolů.
- **Konverzace je stav v kompozici, ne ViewModel** (`ConversationState`): ViewModel by žil
  v úložišti aktivity i po zavření chatu a `closeChat` by TDLib nikdy nedostala. Zprávy jsou
  zaostřitelné jednotlivě (`focusable` s rámečkem), seznam je obrácený (nejnovější dole)
  a starší historie se dočítá, když se fokus blíží horní hraně. Předěl dne je nezaostřitelný
  — šipka ho přeskočí. **Zpráva vyšší než obrazovka** se šipkou napřed odroluje (viz „Dlouhé
  zprávy").
- **Písma jsou tatáž jako na telefonu** (`tvTypography`: Figtree a Bricolage z
  `:client:resources`, proložení podle `theme/Typography.kt`). Výchozí škála tv-material je
  Roboto a vedle zbytku Plume vypadala cize.
- **Heslo dvoufázového ověření je `BasicTextField`** v plošce s rámečkem fokusu: tv-material
  textové pole nemá. Píše se systémovou klávesnicí televize (na Google TV i hlasem z ovladače).
  Telefonní číslo a kód se na televizi nezadávají — přihlášení je jen QR kódem, jako na hodinkách.

## Formátovaný text a spoilery

- **Text zpráv se kreslí s entitami** (`rememberRichText` v `RichText.kt`): tučné, kurzíva,
  podtržení, přeškrtnutí, kód (neproporcionální písmo na tónovaném podkladu), citace
  (podklad a kurzíva), odkazy, zmínky, tagy a příkazy v barvě motivu. Dřív byl všechno holý
  text. V bublině konverzace, v popisku média a v detailu příspěvku kanálu.
- **Odkaz na Telegram otevře OK na zprávě** (`telegramLinkOf`: první `t.me/…`, `telegram.me/…`,
  `tg://…` nebo `@zmínka` v textu; `MediaActions.openLink`). Pořadí OK: médium, odkaz, skok na
  citovanou zprávu — v odpovědi s odkazem je odkaz to nové. Cíl rozhodne `resolveLink`: chat
  se otevře, odkaz na zprávu skočí na ni; co televize neumí (pozvánka, bot se startem,
  samolepky), ohlásí toast. **Odkazy na web se nechávají být**: prohlížeč televize obvykle
  nemá a OK by jen ohlásilo „neotevřu" a vzalo zprávě skok na citaci. V odchozí bublině jsou
  odkazy v barvě textu s podtržením — `primary` na `primaryContainer` nemá kontrast.
- **Zpět vrací do chatu, ze kterého se odkazem přišlo**, na zprávu s odkazem (zásobník
  `linkHistory` v `TvNavigation`); až když je prázdný, na seznam. Otevření chatu ze seznamu
  nebo z hledání zásobník zahodí. V detailu příspěvku kanálu otevírá odkaz OK, když příspěvek
  nemá médium. Ověřeno na emulátoru: odkaz `t.me/DurgerKingBot` z chatu s testovacím botem
  otevřel Durger King, Zpět vrátil na zprávu s odkazem.
- **Spoiler je nečitelný blok (průhledné písmo na podkladu), první OK na zprávě ho odkryje**;
  teprve další OK dělá, co zpráva umí (médium, skok na citaci). V detailu příspěvku stejně.
  Jiná barva písma místo průhledné by obsah vyzradila.
- **Kde se text kreslí holý, spoiler se zamaskuje znaky „▒"** (`maskSpoilers`): řádek seznamu
  chatů, náhled zaostřeného chatu, výsledky hledání a karta příspěvku ve feedu. Bez toho by
  tam spoiler byl čitelný, přestože ho bublina skrývá.
- Vlastní emoji se kreslí jako obrázek (viz „Vlastní emoji"), datum a čas textem od pisatele.
- Ověřeno na emulátoru se zprávou od testovacího bota (`parse_mode=HTML`, všechny styly,
  `tg-spoiler`, `blockquote`): bublina, maska v náhledu chatu, odkrytí OK. Detail příspěvku
  kanálu se spoilerem ověřený není.

## Spoilery, citlivý obsah a mizející média

Fotky a videa se spoilerem, citlivým obsahem nebo s časovačem byly na televizi do 28. 11. 2026
vidět ostře — v konverzaci, ve feedu kanálu, v prohlížeči i na pozadí náhledu chatu. Filtroval je
jen spořič. Společné kousky jsou v `HiddenMedia.kt`.

- **Spoiler a citlivé médium se kreslí jako rozmazaná zmenšenina ze zprávy** (`minithumbnail`,
  kolem 40 px, přišla se zprávou, nic se nestahuje) pod tmavým závojem se štítkem „Spoiler" /
  „Citlivý obsah" a „Zobrazíš tlačítkem OK" (`MediaCoverBox`). Dlaždice alba, karta ve feedu
  a Rozkoukaná videa mají jen ikonu, štítek se tam nevejde. **Zakryté médium se ani nestahuje**
  (`rememberImage(null)`), odkrytí ho teprve načte.
- **Citlivý obsah vede `core` i jako spoiler** (`hasSpoiler = content.hasSpoiler || hideSensitive`),
  takže televize ho schová, kdykoli by ho schoval telefon; `isSensitive` mění jen štítek. Volba
  „Zobrazovat citlivý obsah" je v nastavení účtu na serveru, televize ji jen respektuje.
- **První OK na zprávě odkryje VŠECHNO skryté naráz**: spoiler v textu i zakrytá média, u alba
  všechny položky. Teprve další OK pustí video nebo otevře prohlížeč. Stejně v detailu příspěvku
  kanálu. Po jednom OK na každou položku alba by se šipky do strany (výběr položky) a odkrývání
  pletly.
- **Odkrytí platí pro celý běh aplikace a všude** — `RevealedSpoilers` z `core` (jako na telefonu),
  ne `remember` u řádku: odkrytá fotka zůstane odkrytá po odrolování, v prohlížeči i na kartě
  feedu. Po restartu aplikace je zase zakrytá.
- **Prohlížeč zakrývá taky** — šipkami se v něm dá dojít na neodkryté médium jinde v chatu. OK
  na zakrytém médiu ho odkryje (nepřiblíží ani nepustí).
- **Mizející a tajná média televize neukazuje vůbec** (`isSelfDestruct`: `isSecret` nebo
  `selfDestructType` v médiu, nebo `selfDestructType` na zprávě — jednorázová hlasovka a kolečko
  ho mají jen tam). Fotka a video mají plochu bez zmenšeniny se štítkem „Mizející fotka/video"
  a „Zobrazí se jen v telefonu", hlasovka a kolečko místo přehrávače oznámení
  (`SelfDestructNotice`). OK nic nedělá (`mediaOpenAction` vrátí `null`) a do prohlížeče ani
  fronty videí nejdou: otevření by zprávu spotřebovalo (odpočet běží od zobrazení) a na velké
  obrazovce v obýváku by ji viděl kdokoli.
- **Kde uživatel nic neodkrývá, skryté médium vůbec není** (`showsUncovered`): pozadí náhledu
  chatu vezme nejnovější médium, které skryté není, a fronta „kanál jako pořad" spoiler video
  přeskočí — po dohrání předchozího by se ukázalo bez odkrytí.
- **`Modifier.blur` rozostří až od Androidu 12**; na starší televizi zbude zvětšená zmenšenina,
  která je rozmazaná sama (stejná úvaha jako `BlurredPlaceholder` na telefonu).
- **Ověřeno 28. 11. 2026 na emulátoru `Plume_TV_Play`** s fotkou, videem a albem se spoilerem
  od testovacího bota (`has_spoiler` v Bot API): zakrytí v bublině, kartě i dlaždicích alba, OK
  odkryje a nepustí, druhé OK pustí; album odkryté jedním OK; v prohlížeči zakrytá neodkrytá
  fotka a OK ji odkryje i v konverzaci; pozadí náhledu chatu přeskočí nejnovější fotku se
  spoilerem; po restartu aplikace zase zakryté. **Neověřené**: mizející médium (bot ho poslat
  neumí a výběr souborů na emulátoru telefonu nereagoval), citlivý obsah (posílá ho jen server)
  a feed kanálu (testovací účet nemá kanál se spoilerem) — jdou stejnými funkcemi.

## Náhled odkazu a Instant View

- **Náhled odkazu pod textem zprávy** (`LinkPreviewCard`): web, nadpis a popis v tónovaném
  bloku, v konverzaci i v detailu příspěvku. Dřív televize náhled nekreslila vůbec a zpráva
  s článkem byla jen adresa. **Obrázek náhledu se vynechává** — bublina by s ním byla vyšší
  než obrazovka a na rozhodnutí, jestli článek otevřít, stačí nadpis.
- **Stránka s Instant View má na kartě výzvu „OK: číst v aplikaci"** a OK na zprávě otevře
  čtečku (`InstantViewScreen`, `MediaActions.openArticle`; pořadí OK: médium, článek, odkaz na
  Telegram, citace). Stránku dává `InstantViewRepository` z `core` jako text s entitami
  a médii (`MessageBody` + `Article.media`), tentýž převod jako u článku ve zprávě.
- **Čtečka**: sloupec na čtení (900 dp) uprostřed, nadpis, text s formátováním
  (`rememberRichText`) rozdělený v místech médií (úsek textu, který médium zastupuje, se
  nahradí obrázkem; entity se do každého kusu ořežou a posunou), fotky a náhledy videí.
  Šipky nahoru a dolů rolují, Zpět zavře a fokus se vrátí na zprávu. Média se jen ukazují —
  ovladačem by se mezi nimi v textu nedalo vybírat. Neúplný článek končí větou, že zbytek je
  na webu; stránka bez Instant View hláškou.
- Ověřeno na emulátoru s `telegra.ph/api` od testovacího bota: karta s výzvou, čtečka
  s formátováním, rolování, návrat Zpět. Článek s obrázky ověřený není.

## Video

- **Přehrávání za běhu stahování je v `:client:core`** (`core/video`: `StreamingSource`,
  `streamOriginalVideo`, `downloadHeadsTo` v `commonMain`, `PartialFileDataSource` v
  `androidMain`). Dřív bylo ve `shared` a televize by ho musela kopírovat. Balíček zůstal
  stejný, takže telefon se změnou importů nemusel měnit. `core` kvůli tomu táhne jen
  `media3-datasource` (`api`, typ je ve veřejném API zdroje), přehrávač si přidává, kdo přehrává.
- **`VideoPlayerScreen`**: ExoPlayer, obraz v `PlayerView` bez vlastního ovládání (jen povrch
  a poměr stran), ovládání v Compose. OK / Přehrát-Pozastavit přepíná, šipky vlevo a vpravo
  (i přetáčení na ovladači) přetáčejí po 10 s (viz „Přetáčení s náhledem snímku"), Zpět zavře. Ovládání se po 4 s přehrávání schová,
  vrátí se s každým stiskem; první OK při schovaném ovládání ho jen ukáže, jako u systémových
  přehrávačů. Fokus zvuku a „sluchátka vytažena" řeší ExoPlayer (`handleAudioFocus`).
- **Tlačítka médií na ovladači přepínají vždycky**, i se schovaným ovládáním: klávesa se
  v `onPreviewKeyEvent` spotřebuje a `MediaSession` ji už nedostane, takže pravidlo „první stisk
  jen ukáže ovládání" (správné pro OK) by pauzu z ovladače napoprvé spolklo.
- **Zavřené video se přestane stahovat** (`cancelDownload` při odchodu, když se ještě stahuje):
  odběr `files.file` stahování spustil a sám ho nezruší. Telefon to má stejně, ale televize
  mívají kolem 8 GB a náhled pár vteřin videa z kanálu by stáhl celé.
- **`MediaSession` (media3-session)** dává přehrávač systému: tlačítka médií na ovladači
  a karta „Právě hraje" na Google TV. Ověřeno `dumpsys media_session` (stav PAUSED s pozicí).
- **Přehrávač leží NAD konverzací**, ta zůstává v kompozici — po zavření videa se historie
  nenačítá znovu. Zpět zavře nejdřív video: jeho `BackHandler` je složený později.
- **Po zavření videa se fokus vrací na zprávu s videem** (`focusTarget` v `MessageList`).
  Přehrávač fokus odnesl a bez vrácení skončil na jiné zprávě (ověřeno na emulátoru).
- **Zpráva s videem je `clickable` s `indication = null`**: na televizi bere i DPAD_CENTER,
  ale výchozí vlnka zabarvila celou šířku řádku vedle bubliny. Fokus nese prstenec kolem
  bubliny (viz „Fokus zprávy“).

## Stream, který se nerozběhne

- **Selže-li stream dřív, než video poprvé hrálo, čeká se na celý soubor** (jako telefon,
  `VideoPage`, „Video bez příznaku supportsStreaming" v `prohlizec-medii.md`). Dřív televize
  rovnou ukázala „Video nejde přehrát". Týká se hlavně MP4 bez příznaku streamování, které se
  streamuje taky (`streamOriginalVideo`), a souborů se špatně postaveným indexem.
- **Jak:** `onPlayerError` při `streamingLoaded && !hasPlayed` nastaví `streamFailedFor` na
  soubor; efekt načtení (klíč `chosenId, streamFailedFor`) pak u toho souboru nestreamuje,
  počká na `Ready` a pustí hotový soubor od místa, kde stream skončil. Jednou na soubor —
  selže-li i hotový, je to chyba. **Chyba po rozběhnutí zůstává chybou**: bývá to výpadek sítě
  a čekání na celé video by trvalo.
- **Čekání ukazuje procenta** („Video nejde přehrát během stahování. Stahuji ho celé… 84 %"),
  ne holé kolečko: u velkého souboru by vypadalo jako zamrzlé.
- **Listener přehrávače čte jen stav (`mutableStateOf`, `rememberUpdatedState`)**: je založený
  jednou s přehrávačem, takže obyčejná lokální proměnná by v něm zůstala z první kompozice
  (po výměně kvality by znal starý soubor).
- **Ověřeno 28. 11. 2026 sondou** (`PartialFileDataSource.open` vždy hodil `IOException`,
  vráceno): MP4 od testovacího bota bez `supports_streaming`, v logu jednou „stream se
  nerozběhl, čeká se na celý soubor", na obrazovce věta s procenty (5 % → 84 %), po stažení
  video hrálo z hotového souboru až do konce bez chyby.

## Přetáčení s náhledem snímku

- **Šipka posouvá CÍL, skočí se až 0,7 s po posledním stisku** (`scrubMs` ve
  `VideoPlayerScreen`, `SCRUB_COMMIT_MS`). Dřív každý stisk hned volal `seekBack/seekForward`:
  pět stisků k minutě dál znamenalo pět skoků a video se pětkrát začalo načítat (u streamu
  z rozestavěného souboru každý skok přesune stahování). Mezitím video hraje dál, průběh a čas
  vlevo ukazují cíl.
- **Nad průběhem stojí náhled** (`SeekPreview.kt`): snímek ze storyboardu, který server
  vyrábí k delším videím (`MessageVideo.storyboards`), a pod ním čas, kam se skočí. Video bez
  storyboardu (krátké) má jen čas. Parser mapy (`parseStoryboardMap`, `frameAt`, testy
  `StoryboardTest`) se kvůli tomu přesunul ze `shared` do `core`, balíček zůstal.
- **Obrázek s dlaždicemi se dekóduje v původní velikosti** (`rememberImage(…, Int.MAX_VALUE)`):
  zmenšený by posunul souřadnice z mapy. Kreslí se výřez (`drawImage` se `srcOffset`
  a `srcSize`), obrázek je jeden pro celé video. **`rememberImage` s cílem 0 by se zacyklil**
  (`min / (sample · 2) >= 0` platí vždycky) — „bez zmenšení" je proto nekonečný cíl, ne nula.
- **Náhled místo v řádku nebere** (`seekPreviewAbove`: měří se bez omezení a klade nad pruh,
  sevřený do jeho šířky). Jinak by řádek s časy při přetáčení poskočil.
- **Podržená šipka po osmi opakováních přetáčí po 30 s** (`FAST_SCRUB_AFTER`), hodinové video
  by jinak šlo přetáčet minuty. **Neověřené** — `adb shell input` opakování klávesy neumí.
- **`MediaSession` přetáčí dál po 10 s okamžitě** (`setSeekBack/ForwardIncrementMs`): tlačítka
  z karty „Právě hraje" na Google TV jdou mimo klávesy aplikace.
- **Ověřeno 28. 11. 2026** na emulátoru v kanálu Pavla Durova: video 1:15 se storyboardem —
  tři stisky vpravo, náhled snímku z 0:30 nad průběhem s časem „0:30", po chvíli skok a video
  hraje od 0:30; šipka vlevo z 1:01 na 0:51 s náhledem. Video 0:21 bez storyboardu: jen čas
  „0:10" nad průběhem.

## Kvalita videa

- **Tatáž pravidla jako v prohlížeči na telefonu** (`docs/poznatky/prohlizec-medii.md`,
  „Kvalita videa"): `videoQualities` a `pickVideoQuality` se kvůli tomu přesunuly ze `shared` do
  `:client:core` (`core/video/VideoQualities.kt`, testy `VideoQualitiesTest` s nimi), balíček
  zůstal. Dekodér z `MediaCodecList` je `mediaCodecVideoDecoder` v `androidMain` modulu `core`;
  telefonní `actual fun videoDecoder` ho jen volá, takže kopie pravidel nevznikla.
- **Dřív televize hrála vždycky původní soubor**, i když server nabízel přepočítané kvality.
  Původní soubor bývá, jak ho odesílatel nahrál (index na konci, vysoký datový tok); přepočítaný
  server dělá pro streamování a z AV1 a h264 téže velikosti se nabídne ten s hardwarovým
  dekodérem — na slabé televizi rozdíl mezi plynulým a trhaným obrazem.
- **Automatika** (`rememberPlayedQuality` ve `VideoQualityPicker.kt`): televize nastavení
  automatického stahování nemá, platí výchozí úrovně (`AutoDownloadSettings()`): Wi-Fi a ethernet
  bez stropu (přepočítaná ve stejném rozlišení jako původní, jinak původní), mobilní data 720p,
  spořič dat 480p. Už stažený původní soubor vyhrává. Přepočítá se i za běhu: emulátor během
  testu sám přešel z Wi-Fi na mobilní data a video se přepnulo z 1080p na 720p.
- **Výpadek sítě (`NetworkKind.None`) automatiku nepřepíná** (`runningReduce` nad stavem sítě,
  platí poslední známá). Na telefonu `None` znamená úroveň mobilních dat; v přehrávači by krátký
  výpadek zrušil rozstahovanou kvalitu a začal stahovat jinou, a bez sítě se stejně nic nestáhne.
  Jen v logice, výpadek se na emulátoru vyvolat nepodařilo.
- **Ruční volba: šipka nahoru** při vidět ovládání otevře dialog (Automaticky s tím, co by
  vybrala, pak kvality od nejlepší s velikostí). V liště vpravo je pilulka se šipkou a hrající
  kvalitou — jen když má video víc kvalit. Dialog, ne nabídka u tlačítka: přehrávač nemá tlačítka
  jako cíle fokusu, klávesy bere celý. První stisk při schovaném ovládání ho jen ukáže, jako OK.
- **Volbu drží `TvNavigation`**, ne přehrávač: každé video z fronty je nová instance přehrávače
  (`key(fileId)`) a volba má platit pro další videa, dokud je přehrávač otevřený (jako
  v prohlížeči na telefonu). Po zavření zase Automaticky.
- **Výměna za běhu**: tentýž `ExoPlayer` dostane nový zdroj a skočí na pozici, kde starý byl;
  `playWhenReady` se nemění, takže hrající video hraje dál. Do rozběhnutí se točí čekání
  (`switching`). Telefon nechává do první dávky dat hrát starý soubor; tady se čeká na černé
  ploše, na televizi to vadí míň a kód je o polovinu kratší. Opuštěná kvalita se přestane
  stahovat (`DisposableEffect(chosenId)`, totéž co zavření přehrávače).
- **Stav souboru (`MutableStateFlow`) je pro každou kvalitu nový** (`remember(chosenId)`):
  se sdíleným by zdroj nové kvality na okamžik viděl cestu a velikost starého souboru — past,
  na kterou narazil telefon (viz „Past: `produceState`…" níž a totéž v prohlížeči telefonu).
- **Ověřeno 28. 11. 2026** na emulátoru v kanálu Pavla Durova: video s původním 1920p hraje
  původní (přepočítané jsou nejvýš 1080p), další video z fronty 1080p h264 místo původního;
  dialog (Automaticky · Podle sítě: 1080p, Původní · 29 MB, 1080p 24 MB, 720p 11 MB, 480p
  5,7 MB), volba 480p na 0:16 hraje dál od 0:16, další video z fronty taky 480p, po zavření
  a novém spuštění zase Automaticky. Log `TvVideo` („hraje 480p h264, volba Lines(480)").
  **Na skutečné televizi neověřené** — hlavně přednost hardwarového dekodéru (emulátor dekóduje
  softwarově).

## Fokus zprávy

- **Zaostřená zpráva má světlý prstenec KOLEM sebe** (`focusRing` ve `FocusRing.kt`: 3 dp
  v `onSurface`, 3 dp od okraje, rohy soustředné přes `focusRingShape`). Platí pro bublinu,
  holé médium, kartu s médiem, servisní pilulku, kolečko a text příspěvku v detailu kanálu.
- **Dřív to byl 3dp rámeček v barvě `border` uvnitř tvaru zprávy** a ze tří metrů byl
  nevýrazný: středně šedá na šedé příchozí bublině skoro splývala a rámeček ukrajoval okraj
  fotky nebo citace. Návod Google chce „okamžitou a zřetelnou“ odezvu na ovladač a Google TV
  má fokus bílý. Prstenec stojí na ploše stránky, takže kontrast nezávisí na barvě bubliny.
- **Bez zvětšení**, které mají karty tv-material: široká bublina by přetekla do okraje a text
  ve zvětšené vrstvě by se rozmazal.
- **Prstenec se kreslí mimo hranice zprávy** (`drawWithContent`, modifikátor PŘED `clip`),
  takže seznam potřebuje aspoň `FOCUS_RING_OUTSET` (6 dp) mezi položkami i na krajích:
  konverzace má mezery 8 dp a okraje, seznam komentářů v kanálu dostal `contentPadding`.
  U zprávy vyšší než obrazovka ho seznam na kraji ořízne, boky zůstanou vidět.

## Dlouhé zprávy

- **Zpráva vyšší než seznam se šipkou napřed odroluje** (`TallItemScroll`, v konverzaci
  i v komentářích): dokud zaostřená zpráva přečnívá přes kraj seznamu ve směru šipky, šipka
  posune seznam o 60 % výšky (nejvýš o to, co zbývá do kraje zprávy), teprve pak pustí fokus
  dál. Dřív fokus skákal po celých zprávách a seznam ukázal jen kraj zaostřené — prostředek
  dlouhé zprávy nebo komentáře se nedal přečíst. Detail příspěvku kanálu to řeší vlastním
  rolováním textu (`PostBody`).
- **Při vstupu do vysoké zprávy** ukáže seznam (výchozí `bringIntoView`) její bližší kraj:
  zdola spodek, shora začátek. Šipka pak čte dál týmž směrem, takže se nic nepřeskočí.
- **Kraj se měří přes polohu v okně a výšku, ne `boundsInWindow`** — ta je oříznutá rodiči,
  takže přečnívající zpráva by vypadala, že se vejde. Seznam se měří bez odsazení obsahu.
- **Instance pomocníka se nesmí měnit se změnou odsazení.** Tlačítko psaní se ukáže až po
  načtení práv chatu, spodní odsazení se změní — a s `remember(…, odsazení)` vznikla nová
  instance, která nevěděla, která zpráva je zaostřená (změna fokusu, která to hlásí, už
  proběhla), takže šipka zprávu přeskočila (ověřeno logem na emulátoru). Odsazení se proto jen
  přepisuje do stávající instance.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem na zprávě o 47 řádcích od testovacího bota:
  šipka nahoru roluje zprávou až k jejímu začátku, další nahoru jde na předchozí video, dolů
  zpátky na začátek dlouhé zprávy a rolováním až na její konec. **Komentáře** (týž pomocník)
  na zařízení ověřené nejsou.

## Úklid mezipaměti

- **Automatický úklid stažených médií** (`AutoStorageCleaner` v `core/storage`, androidMain):
  média, na která se nesáhlo 3 dny, a pak nejdéle nepoužitá nad **1 GB**. Levné televize mají
  kolem 8 GB a videa z kanálů (s přehráváním po sobě) a příběhy by je zaplnila. Strop je výš
  než na hodinkách (100 MB): video má desítky MB a mezipaměť se pak obnovuje pořád dokola.
  Rozkoukané video smazáním souboru nepřijde o pozici (ta je zvlášť) a právě přehrávané video
  úklid nesmaže (TDLib nechá soubory mladší než hodina).
- **Společný s hodinkami**: dřív `WearStorageCleaner` ve `wearApp`, teď tatáž třída v `core`
  s mezemi od aplikace (hodinky dál `wear_storage`, 3 dny a 100 MB; televize `tv_storage`).
  Dvě kopie téhož úklidu by se rozešly.
- **Kontroluje se po přihlášení a pak každou hodinu, dokud proces běží** (úklid sám nejvýš
  jednou za 24 h). Televize nechává aplikaci v paměti celé dny — jen po startu procesu by
  úklid neproběhl nikdy. Kontrola je jen čtení času z `SharedPreferences`.
- **Ruční vyčištění televize nemá** (nemá nastavení); hodinky ho mají v Nastavení.
- **Ověřeno 27. 11. 2026**: po startu „Úklid úložiště (tv_storage) uvolnil 0 B" (vše mladší než
  3 dny a pod stropem, `files` 91 MB) a zapsaný čas v `tv_storage.xml`.
- **Úklid nad stropem ověřen 28. 11. 2026 sondou** (strop dočasně 40 MB místo 1 GB, smazaný
  `tv_storage.xml`, aby úklid nečekal 24 h; vráceno): `files/tdlib-files` 124 MB, v logu
  „Úklid úložiště (tv_storage) uvolnil 127791104 B", po úklidu 39 MB — zůstal dnešní testovací
  soubor (mladší než hodina) a jedno novější video, starší videa, skladby a fotky zmizely.
  Aplikace dál funguje: smazaná fotka karty se po otevření kanálu stáhla znovu, smazané video
  z karty se stáhlo a přehrálo a po něm navázalo další z fronty. **Na skutečné televizi
  s plným úložištěm neověřené.**
- **Ověřuje se snížením stropu, ne čekáním**: úklid běží nejvýš jednou za 24 h podle
  `last_cleanup` v `shared_prefs/tv_storage.xml`. `run-as eu.tintera.plume rm
  shared_prefs/tv_storage.xml` a nový start procesu ho spustí hned po přihlášení.

## Skok na zprávu

- **Okno historie kolem zprávy** (`ConversationState.jumpTo`, `loadAround`): `getChatHistory`
  od zprávy se záporným posunem (polovina starších, polovina novějších). Je-li zpráva už
  načtená, jen se na ni přesune fokus. Obrazovka žádost spotřebuje (`JumpRequest` s pořadím —
  týž cíl dvakrát po sobě musí skočit znovu), posune se na bublinu, počká snímek a vezme fokus.
- **V okně uprostřed historie** (`hasNewer`) se novější zprávy dočítají, jak se fokus blíží
  spodku (`loadNewer`, prázdná stránka = konec), a **živé nové zprávy se do okna nepřidávají** —
  stály by za mezerou v historii; přijdou s dočtením. Odeslání zprávy z okna načte konec historie
  (`loadLatest`), jinak by nová zpráva visela za mezerou.
- **Odkud se skáče**: zpráva ve výsledcích hledání (chat se otevře rovnou na ní,
  `TvNavigation.openedAt`), „Zobrazit v chatu" u rozkoukaného videa (dřív jen „Otevřít chat"),
  OK na zprávě bez média, která na něco odpovídá, a „Zobrazit citovanou zprávu" v nabídce nad
  zprávou (podržení OK) — u zprávy s médiem OK dál otevírá médium.
- **Kanál skočí rovnou do detailu příspěvku**, ve kterém zpráva je; po Zpět je fokus ve feedu
  na tomtéž příspěvku. Feed dočítá novější příspěvky u horního okraje stejně jako konverzace.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem: hledání „testovaci" → nejstarší výsledek
  (12:28) → konverzace otevřená na něm s fokusem, šipkou dolů dočtení až k nejnovější zprávě
  (17:36) a na „Napsat zprávu"; OK na odpovědi „Test odpovedi z TV" → fokus na citované zprávě.
  **Neověřené na zařízení**: „Zobrazit v chatu" z Rozkoukaných (táž cesta jako z hledání),
  skok v kanálu do detailu příspěvku a odeslání zprávy z okna.

## Kanál jako pořad (další video samo)

- **Žádost o přehrání nese frontu** (`VideoRequest.queue`, `next()`): po dohrání naskočí vpravo
  dole karta dalšího videa s náhledem, popiskem a šestivteřinovým odpočtem, pak se pustí samo.
  OK přehraje hned, Zpět odpočet zruší (video zůstane na konci, druhé Zpět zavře), jiná klávesa
  ho zruší a udělá, co dělá vždycky (šipka vlevo skočí zpět). Tlačítko „další" na ovladači pustí
  další video kdykoli. Ovládání během odpočtu ustoupí kartě — jinak ji překrývalo.
- **Směr podle toho, jak se obrazovka čte**: v konverzaci novější zprávy (`mediaOpenAction`
  z `allMessages`), v kanálu STARŠÍ příspěvky, protože feed jde od nejnovějšího dolů
  (`ChannelFeedScreen` předá frontu sám přes `videosAfter`). Ve frontě jsou jen videa, která
  přehrávač pustí (ne mizející a tajná), nejvýš 50.
- **Fronta se doplňuje z toho, co obrazovka dočetla během přehrávání** (`VideoRequest.more`,
  `MediaActions.withMoreVideos`). Skládá se při spuštění z načtené části historie a dřív tam
  končila: kanál s videem jen občas (Pavel Durov: tři videa ve 39 příspěvcích) skončil po
  druhém. Když fronta dojde, `next()` se zeptá obrazovky pod přehrávačem na videa za právě
  dohraným — podle AKTUÁLNÍCH zpráv, ne těch z doby ťuknutí. Fronta jako hodnota zůstala:
  karta „Další za 5 s" ji potřebuje znát dopředu a prohlížeč s ní pracuje dál beze změny.
- **Obrazovka ví, které video hraje** (`playingMessage` z `TvNavigation`, jen v otevřeném chatu)
  a feed podle něj dočítá starší příspěvky: když je video u konce načteného feedu nebo za ním
  nejsou aspoň dvě videa, dočte se stránka — a znovu, dokud dvě nejsou, nejvýš čtyři stránky
  na jedno video (kanál, kde další video už není, se nesmí dočíst celý). Konverzace dočítá
  novější zprávy, když hraje nejnovější načtená (okno uprostřed historie po skoku).
- **Dočítá se ve scope obrazovky, ne efektu.** Efekt (`LaunchedEffect(playingMessage, posts)`)
  se s dalším videem ruší, a zrušené `loadOlder` uprostřed čtení by v `ConversationState`
  nechalo `loadingOlder = true` — feed by se už nikdy nedočetl, ani šipkami.
- **Zpět z přehrávače stojí na příspěvku (zprávě) POSLEDNÍHO přehraného videa**, ne tam, odkud
  se pouštělo. Během přehrávání se na něj přesouvá cíl fokusu (`focusTarget`), otevřený detail
  příspěvku se přepne na jeho příspěvek; po zavření se k němu mřížka (seznam) dosune, je-li mimo
  obrazovku, a teprve pak dostane fokus. Dřív Zpět vracelo na první video, i když se mezitím
  dívalo o deset příspěvků níž. Přehrávač je jedna vrstva nad obrazovkou a každé video z fronty
  je nová instance (`key(fileId)`) téže vrstvy.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem na dvou patnáctivteřinových videích od
  testovacího bota: po dohrání karta „Další za 5 s" s náhledem a popiskem videa B, samo pustí B;
  Zpět během odpočtu zůstane na konci A; tlačítko „další" (`KEYCODE_MEDIA_NEXT`) pustí B hned;
  Zpět z B vrátí fokus na zprávu s A (to platilo do 28. 11., teď by stál na B).
- **Kanál ověřen 28. 11. 2026** na kanálu Pavla Durova: video z karty (21. 11.), dvakrát
  „další" a Zpět — fokus na kartě třetího videa (31. 8.), ne na první. Pak ještě pětkrát „další"
  až k videu ze 14. 5., které při spuštění načtené nebylo (feed byl načtený do konce srpna), po
  dohrání karta dalšího videa a Zpět s fokusem na kartě ze 14. 5. **Konverzace (novější zprávy,
  návrat na poslední video) jen přeložená** — chat s víc videi po sobě v testu nebyl.

## Rozkoukaná videa

- **Tatáž pravidla a tentýž seznam jako na telefonu** (`docs/poznatky/prohlizec-medii.md`,
  „Pozice přehrávání se pamatuje"): klíč je soubor na serveru (`MessageMedia.Video.fileKey`),
  jen videa od 2 min, ne prvních 10 s ani posledních 15 s, dohrané se zapomene. Data a čisté
  funkce (`VideoPosition`, `withPosition`) se kvůli tomu přesunuly do `:client:core`
  (`core/video/VideoPosition.kt`); `VideoPositions` nad `AppSettings` zůstal ve `shared`.
  Balíček je stejný, telefon importy neměnil.
- **Uložené v `SharedPreferences`** (`TvVideoPositions`), ne v DataStore — televize stojí jen
  na `core` a jeden seznam do sto kB DataStore nepotřebuje.
- **Synchronizace s telefonem a desktopem** (`TvPositionSync`): tatáž skupina
  `plume_positions` v úložišti bota `@PlumeSyncBot`, týž formát a totéž trojcestné sloučení
  jako `CloudSync` (`docs/poznatky/nastaveni.md`, „Synchronizace nastavení"). Formát, sloučení
  a čtení i zápis skupin jsou proto v `core` (`core/settings/sync/CloudGroups.kt`, `CloudStore.kt`);
  ve `shared` zůstaly jen skupiny, které televize nemá (záložky, barvy chatů, štítky, zkratky,
  volby). Televize čte a píše JEN svou skupinu — každá skupina má v úložišti vlastní klíče,
  takže ostatní nepřepíše. Synchronizuje se po `Ready`, **po návratu do popředí** (televize
  nechává aplikaci v pozadí celé dny a telefon mezitím sledoval dál), po místní změně
  s prodlevou 15 s a každých 15 minut. Základ sloučení drží `TvVideoPositions.syncBase`.
- **Video se pustí od pamatované pozice** a nahoře na 6 s od rozběhnutí svítí „Pokračuje od
  12:34" s tlačítkem Od začátku. Tlačítko nebere fokus (ten drží přehrávač kvůli šipkám), OK
  dokud je nápis vidět vrátí na začátek — jako „Přeskočit úvod" u streamovacích služeb. Pauza
  jde i tehdy tlačítkem médií. Bez pamatované pozice se začíná od `startSeconds` (přeposlání
  „od 1:23").
- **Zápis**: každých 10 s hraní, při dohrání, zavření přehrávače a `ON_STOP` (tlačítko Domů).
  Jen u videa, které opravdu hrálo — otevření a zavření bez puštění by zapsalo začátek, a tím
  smazalo pozici z telefonu. Pozice z posledního tiknutí, přehrávač už může být uvolněný.
- **Proužek zhlédnutého a „0:23 / 1:02:08"** v bublině videa i na kartě příspěvku kanálu
  (`rememberWatched`, `WatchedBar`), jako na telefonu.
- **Záložka Rozkoukaná videa** hned za Všemi chaty, jen když nějaká jsou (vybraná zůstane
  i s prázdným seznamem, ať odebrání posledního videa neschová záložku pod fokusem). Mřížka karet
  po třech (`ContinueWatchingPane`): OK pustí video rovnou, podržení OK nabídne Otevřít chat
  a Odebrat ze seznamu. Zpráva se dočte jednou a pamatuje se; napřed `summaries` nad chaty —
  video rozkoukané na telefonu bývá z chatu, který televize ještě nenačetla, a `getMessage` by
  bez něj selhal. Smazaná zpráva nebo zpráva bez videa se tiše nevykreslí. „Zobrazit v chatu"
  jako na telefonu to není: skok na zprávu konverzace na televizi zatím neumí.
- **Zamčené chaty televize nemá** (`LockedChats` dodává jen aplikace telefonu), takže v seznamu
  jsou i videa ze zamčených chatů — stejně jako ty chaty v seznamu chatů.
- **Šipka nahoru ze seznamu jde na VYBRANOU záložku** (`focusRestorer` na `TabRow`). Dřív šla
  na záložku nejblíž nad zaostřeným řádkem, a protože se záložka vybírá už zaostřením, šipka
  nahoru tiše přepnula složku (s novou záložkou na druhém místě pokaždé na Rozkoukaná videa).
- **Na plochu Google TV (řada „Pokračovat ve sledování") to nejde**, prozkoumáno 27. 11. 2026:
  - **Engage SDK** (`com.google.android.engage:engage-tv`, cesta, kterou Google do té řady
    dokumentuje) chce přihlášku a schválení Googlem, aplikace na Play v podporované kategorii
    a velikost (Media Experience Program nebo 100 tisíc denních uživatelů). **Zasílání zpráv
    mezi podporovanými kategoriemi výslovně není.**
  - **Starší Watch Next** (`androidx.tvprovider`, řada „Play Next") schválení nechce, ale pravidla
    říkají přidávat jen klasické filmy a seriály, ne klipy a krátká videa — přesně to, co je
    v chatech. Jestli se na Google TV ještě ukazuje v řadě „Pokračovat ve sledování", dokumentace
    neříká. Riziko, že kontrola kvality pro Android TV v Play aplikaci kvůli tomu vrátí, je
    větší než užitek.

## Příběhy

- **Řada příběhů nad Všemi chaty** (`StoryRowContent`), ne hromádka v pilulce jako na telefonu:
  avatar v řádku chatu nejde ovladačem zaostřit zvlášť (OK otevře chat) a kontakt bez chatu by
  se jinak k příběhům nedostal. Je to PRVNÍ POLOŽKA seznamu, takže s pohybem dolů odjede
  a chatům místo nebere; jen v hlavním seznamu a jen když nějaké příběhy jsou. Pořadí TDLib
  (`storyListOrder`), lem z dílků po tvaru avataru (barevné nezhlédnuté, tenčí šedé zhlédnuté), vlastní
  příběh pod nadpisem „Moje příběhy" s fotkou účtu.
- **Řada přibude až s updaty od TDLib, kdy už seznam stojí.** Seznam drží první viditelnou
  položku podle klíče, takže řada zůstala nad horním okrajem a šipka nahoru ukázala jen avatar
  bez nadpisu (ověřeno na emulátoru). Dokud se uživatel nepohnul, seznam se vrátí na začátek.
  Indexy chatů jsou o řadu posunuté (`header` v `ChatList`).
- **Fokus v řadě znamená „uživatel se pohnul"** (`navigated`): bez toho by mu ho seznam při
  každé změně chatů (nová zpráva) vzal zpátky na chat. A **když nad seznamem leží prohlížeč nebo
  přehrávač, seznam si fokus nebere vůbec** (`overlayOpen`): změna chatů by ho prohlížeči
  ukradla a šipky by šly do seznamu pod ním. Po zavření jde fokus na avatar, ze kterého se
  příběhy otevřely.
- **Prohlížeč** (`StoriesViewerScreen`): příběh uprostřed na výšku (9 : 16, výška obrazovky bez
  bezpečných okrajů), nahoře dílky průběhu, autor a čas, dole popisek. Autoři i příběhy se při
  otevření zmrazí — `StoriesState` a pohyb (`next`, `previous`, `showPoster`) jsou tytéž čisté
  funkce jako na telefonu, kvůli tomu přesunuté do `:client:core` (`core/stories/StoryCursor.kt`).
  Šipky vlevo a vpravo předchozí a další příběh (za posledním autora na dalšího, za posledním
  autorem se zavře), nahoru a dolů rovnou jiný autor, OK pauza, Zpět zavře.
- **Otevírá se rovnou na vybraném autorovi** (index v počátečním stavu). Přes nastavení až po
  složení by se na okamžik otevřel první autor seznamu a TDLib by jeho příběh započítal jako
  zhlédnutý — autor by to viděl.
- **`openStory` / `closeStory`** při každém přechodu (`DisposableEffect` nad aktuálním příběhem,
  zavření přes aplikační scope). Načítá se obsah aktuálního a dalšího příběhu.
- **Fotka stojí 6 s od načtení, video hraje celé** — stáhne se napřed celé (příběhy mají do
  minuty a jednotky MB), pak ExoPlayer s `RESIZE_MODE_ZOOM` jako `Crop` u fotky. Fokus zvuku
  vezme přehrávači hudby (pozastaví se). Živé vysílání televize neumí: věta a po 6 s dál, stejně
  jako příběh, který už není.
- **Lem u avataru v řádku chatu a v hlavičce konverzace** (`Modifier.storyRing`, kreslení
  společné s řadou příběhů v `storyRingSegments` / `drawStoryRing`). Kreslí se VEN z plochy
  avataru (`drawWithCache` před ořezem), takže řádek s lemem i bez něj má stejnou výšku. Jen ukazatel — OK na řádku
  otevře chat, příběhy se otevírají z řady nad seznamem. Uložené zprávy lem nemají (záložka
  místo fotky účtu, „Moje příběhy" jsou v řadě).
- **Na zaostřeném řádku (inverzní plocha, v tmavém motivu světlá) je lem `inversePrimary`**:
  `primary` tam skoro nebylo vidět. Fokus předává řádek (`onInverse`); odvodit ho
  z `LocalContentColor` uvnitř avataru na emulátoru nefungovalo — ani přesná shoda
  s `inverseOnSurface`, ani světlost barvy lem nepřebarvily.
- **Ověřeno na emulátoru s podstrčenými příběhy** (dočasná úprava, necommitnutá: Notifikátor
  se třemi příběhy, jeden zhlédnutý): lem v nezaostřeném i zaostřeném řádku a v hlavičce
  konverzace. Na skutečném účtu v době ověření nikdo aktivní příběh neměl.
- **Zatím ne**: reakce a odpověď na příběh, ztlumení (hlasitost má ovladač), skrytí autora,
  vlastní příběh (diváci, správa).

## Hledání

- **První záložka, jen s lupou** — jako horní navigace samotné Google TV (hledání vlevo ikonou,
  pak sekce). Lišta s hlavními cíli (`NavigationDrawer`), se kterou počítala roadmapa, by kvůli
  dvěma cílům vzala šířku seznamu. Co ukazuje obrazovka pod záložkami, nese `ListSection`
  (Chaty, Rozkoukaná videa, Hledání); složka zůstává zvlášť.
- **Tytéž dotazy jako na telefonu** z `core`: `searchChats` napřed lokálně a pak i na serveru,
  `searchPublicChats` (globální výsledky) a `searchMessages` napříč chaty. Bez dotazu nedávno
  nalezené chaty (`recentlyFoundChats`). Vpravo náhled zaostřeného výsledku jako v seznamu chatů.
- **Části výsledků chodí, jak doběhnou dotazy, a chaty bývají až po globálních.** Seznam drží
  první viditelnou položku podle klíče, takže chaty i s nadpisem zůstaly nad horním okrajem a
  vypadalo to, že vlastní chat nenašel (ověřeno na emulátoru). Dokud v seznamu není fokus, drží
  se na začátku. **A výsledek se zapíše, jen když dotaz pořád platí**: volání TDLib nemusí na
  zrušení korutiny reagovat a výsledek staršího dotazu („Notifik" při psaní po částech) by
  přepsal novější.
- **Stav hledání drží navigace** (`TvSearchState`): z výsledku se otevírá chat, seznam i s
  hledáním odchází z kompozice a po návratu musí stát dotaz, výsledky i fokus na položce, ze
  které se odcházelo — bez nového dotazu.
- **Šipku vlevo si pole bere na posun kurzoru**, takže se z něj na mikrofon nedalo dostat. Na
  začátku textu šipka vlevo z pole odejde (`TextFieldValue` kvůli kurzoru); šipka dolů se posílá
  výslovně jako v okně odpovědi.
- **Hlasové zadání je systémové** (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`): na Google TV ho
  obslouží aplikace Google vlastním okénkem a vrátí text. Od Androidu 11 je k tomu potřeba
  `<queries>` v manifestu, jinak `resolveActivity` vrátí `null` a tlačítko se nikdy neukáže.
  **Jazyk se předává z aplikace** (`EXTRA_LANGUAGE`): na emulátoru s anglickým systémem
  a českou aplikací poslouchalo rozpoznávání anglicky.
- **Tlačítko hledání na ovladači** (`KEYCODE_SEARCH`, má ho část ovladačů) odkudkoli zavře, co je
  otevřené, přepne na hledání a spustí hlasové zadání; fokus jde na mikrofon (jinak zůstal na
  záložce a šipka vpravo přepnula složku). **Mikrofon na ovladači Google TV si ale bere Asistent
  systému**, aplikace ho nedostane — globální hledání přes Asistenta („najdi … v Plume") by
  chtělo `searchable` a obsahového poskytovatele, zatím není.
- **Zpráva z výsledků otevře chat rovnou na sobě** (viz „Skok na zprávu").

## Spořič obrazovky s fotkami

- **Systémový spořič** (`PhotoDreamService`, `DreamService`): fotky z Uložených zpráv, nebo
  z chatu vybraného v nastavení spořiče (`DreamSettingsActivity`, otevírá ho systém přes
  `android:settingsActivity` v `res/xml/photo_dream.xml`; volba v `SharedPreferences`). Fotka
  po fotce po 10 s, prolnutí a pomalé přiblížení (efekt Kena Burnse), vpravo dole hodiny a datum
  na ztmavení zespodu. Bez přihlášení jen hodiny. Neinteraktivní: první stisk spořič ukončí.
- **Fotky přes `sharedMedia(Media)`** z `core`, nejvýš 200 (10 stránek), v náhodném pořadí.
  **Bez spoileru, citlivého obsahu a mizejících fotek** — spořič běží v obýváku pro každého, kdo
  se dívá. Ze všech chatů naráz (`galleryMedia`) se vědomě nebere: na televizi by se ukazovalo
  cokoli, co kdo do kterého chatu poslal.
- **`DreamService` není `LifecycleOwner` ani `SavedStateRegistryOwner`**, které `ComposeView`
  hledá ve stromu oken (bez nich spadne při připojení). Spořič je proto dává sám a vede jejich
  stav podle snění (`onDreamingStarted` / `Stopped`, `onDetachedFromWindow`).
- **Proti vypálení**: obraz se pořád pomalu hýbe a hodiny se každou minutu posunou o pár bodů.
  Ztmavení pod hodinami jde přes celou šířku zespodu — s přechodem jen do strany mělo
  viditelnou vodorovnou hranu.
- **Kde jde spořič vybrat**: na Android TV v nastavení systému (Spořič obrazovky). **Google TV
  v nastavení nabízí jen svůj Ambientní režim** a spořič třetí strany se tam podle všeho vybrat
  nedá (na skutečném zařízení neověřeno); jde nastavit přes `adb`
  (`settings put secure screensaver_components eu.tintera.plume/eu.tintera.plume.tv.PhotoDreamService`).
- **Ověření na emulátoru**: `cmd dreams start-dreaming` chce root (obraz s Google Play ho nemá)
  a `Somnambulator` spořič nespustí. Funguje to přirozeně: `screensaver_enabled`,
  `screensaver_components`, `screensaver_activate_on_sleep` = 1, `dumpsys battery set ac 1`,
  `stay_on_while_plugged_in` = 0 a krátký `screen_off_timeout`. **Emulátor `Plume_TV` má po startu
  `screen_off_timeout` nastavený na „nikdy"** (`2147483647`), takže to po restartu znovu. Po testu
  vrátit (`settings delete`, `dumpsys battery reset`, timeout a `stay_on` zpátky).
- **Ověřeno 27. 11. 2026**: nastavení spořiče (Uložené zprávy zaškrtnuté, výběr Notifikátoru
  uložen), spořič s fotkami Notifikátoru (9 fotek, střídání po 10 s podle dočasného logu
  a snímků), přiblížení, hodiny s datem a jejich posun v nové minutě, ukončení stiskem OK.

## Fotky a alba

- **Prohlížeč přes celou obrazovku** (`MediaViewerScreen`): šipky vlevo a vpravo jdou po VŠECH
  fotkách a videích načtené konverzace (jako prohlížeč na telefonu), ne jen po albu; OK na
  videu ho pustí (přehrávač leží nad prohlížečem), Zpět zavře. Popisek a „3 / 12" se po 4 s
  schovají, u krajů šipky naznačují, že je kam jít. Fotka se ukáže hned ve variantě z bubliny
  (bývá stažená) a vymění se za plnou (`fullFileId`, dekódovaná na 1080 px), až dorazí.
- **Vrstvy v `TvNavigation`: chat → prohlížeč → přehrávač.** Zpět zavírá od horní (pozdější
  `BackHandler` má přednost). Fokus si po zavření bere vrstva, která zbyla: chat jen když je
  zavřené obojí (`overlayOpen`), jinak by po zavření přehrávače ukradl fokus prohlížeči.
- **Co udělá OK na zprávě, rozhoduje jedno místo** (`mediaOpenAction`): samotné video se rovnou
  přehraje (bez mezikroku přes prohlížeč), fotka nebo album otevře prohlížeč, zpráva bez média
  jen drží fokus. Platí v konverzaci, v komentářích i v detailu příspěvku.
- **Album je v konverzaci i v komentářích jedna bublina** (`albums`: po sobě jdoucí zprávy se
  stejným `mediaAlbumId`) s mřížkou 2 × 2 a „+N" na posledním náhledu. Popisek alba nese jen
  jedna zpráva, odesílatel a čas jsou z první. V detailu příspěvku je pod hlavním médiem pás
  dalších náhledů alba (jen na ukázku, prochází se v prohlížeči).
- **Šipky vlevo a vpravo na zaostřeném albu vyberou položku, OK otevře prohlížeč rovnou na
  ní** (`AlbumPick`, drží ho položka seznamu a dostává ho `mediaOpenAction` i bublina). Dřív se
  otevíral vždy na první a k desáté fotce se šlo šipkami v prohlížeči. Vybraný náhled má rámeček
  a nahoře počítadlo „5 / 6"; položka za posledním náhledem („+N") zvýrazní ten poslední.
  Na krajích šipka zůstává v albu (fokus neuteče stranou). V konverzaci i v komentářích;
  karta příspěvku ve feedu kanálu výběr nemá. Ověřeno na emulátoru s albem šesti barevných
  fotek od testovacího bota (`sendMediaGroup`): 5 / 6 a OK otevře pátou.
- **`MediaActions`** (přehrát video, otevřít prohlížeč) jde cestou od navigace ke zprávě jako
  jedna skupina, ne dva callbacky v každé funkci.
- **Přiblížení fotky v prohlížeči**: OK na fotce ji přiblíží na 2×, další OK na 3×, třetí zpátky.
  V přiblížení šipky fotku posouvají (místo přechodu na jinou), Zpět napřed přiblížení zruší
  (`BackHandler` složený po zavíracím má přednost). Vpravo nahoře „2×", šipky u krajů zmizí.
  Posun je podíl největšího možného posunu (-1..1) a **omezuje se na to, o kolik přiblížená
  fotka skutečně přesahuje plochu** (`ContentScale.Fit`: fotka 4 : 3 je užší než obrazovka) —
  s posunem počítaným z velikosti plochy fotka vyjela a odkryla černý pruh (ověřeno na emulátoru).
  Přiblížená fotka se dekóduje ve 2160 px, v 1080 px by při 3× byla rozmazaná.
- **Ověřeno 27. 11. 2026** na fotce 4 : 3 od testovacího bota: 2×, posun vpravo a dolů až na kraj
  fotky (bez černého pruhu), 3×, Zpět zpátky na celou fotku a druhé Zpět zavře prohlížeč.
- **Fotka, video a album bez bubliny, jako na telefonu** („Médium bez bubliny"
  v `konverzace-zobrazeni.md`). Bez popisku a citace leží snímek holý na ploše (`BareMedia`):
  tvar bubliny, rámeček fokusu po něm, čas na tmavé pilulce v pravém dolním rohu (délka videa
  proto jde nahoru vlevo), reakce pod snímkem na ploše, jméno odesílatele nad ním odsazené jako
  text v bublině. Dřív ležel snímek v bublině s odsazením 16 dp a vlastním zaoblením — rám, který
  nic neohraničoval.
- **S popiskem nebo citací je to JEDNA karta** (`MediaCard`): citace nahoře, snímek od kraje ke
  kraji (bez vlastního zaoblení, ořezává karta), pod ním popisek, reakce a čas. **Popisek má
  šířku snímku**: karta je `IntrinsicSize.Min` — snímek má pevnou šířku, text jen nejdelší slovo —
  jinak by se dlouhý popisek roztáhl vedle úzké fotky. Uvnitř karty není nic se
  `SubcomposeLayout`, intrinsické míry tedy jdou.
- **Ověřeno 27. 11. 2026** v chatu s Notifikátorem (fotka a video bez popisku poslané testovacím
  botem): holá fotka s časem v rohu, holé video s délkou nahoře a časem dole, rámeček fokusu po
  tvaru snímku; video a album s popiskem jako karta, popisek na šířku snímku. Citace nad snímkem
  a jméno odesílatele ve skupině na zařízení ověřené nejsou.

## Vlastní emoji

- **Kreslí se statický obrázek** (`CustomEmoji.stillFileId` z `CustomEmojiRepository` v `core`:
  `.webp`, u animovaných náhled), ne animace: `.tgs` a `.webm` by na televizi chtěly přehrávač
  na každé emoji a kanál plný emoji by na slabém procesoru trhal rolování. Jednobarevné emoji
  (`tinted`) má barvu textu. Dokud se obrázek nestáhne, nebo když ho server nedá, zůstane
  náhradní emoji.
- **V textu je to vložený obsah nad PŮVODNÍM úsekem** (`rememberRichText` přidá značku
  `INLINE_CONTENT_TAG` — tutéž, jakou píše `appendInlineContent` — nad náhradní znak), obrázek
  dosadí `Text(inlineContent = rememberCustomEmojiContent(…))`. Text se nemění, takže offsety
  ostatních entit (tučné, odkazy) sedí; bez `inlineContent` zůstane náhrada. Ve skrytém spoileru
  se značka nepřidá — obrázek by ho prozradil. Platí pro bublinu, detail příspěvku a článek
  Instant View; karta ve feedu, náhledy a citace jsou holý text s náhradou.
- **Čipy reakcí a „Kdo reagoval" kreslí vlastní emoji taky** (`ReactionText`, `ReactionEmoji`),
  dřív se vynechávaly. Reakce nese jen id, ne emoji, za které stojí — náhrada je hvězdička.
- **Patička detailu příspěvku se zalomí** (`FlowRow`): s reakcemi z vlastních emoji přetekla
  řada (sloupec kolem 470 dp) a třetí reakce přišla o počet (ověřeno na emulátoru).
- **Ověřeno 28. 11. 2026** na emulátoru v kanálu Pavla Durova: reakce z vlastních emoji
  (postavička, kočka, srdce) v patičce detailu s počty, po opravě zalomené na dva řádky.
  **Vlastní emoji v textu zprávy na zařízení ověřené NENÍ** — na účtu se zpráva s nimi
  nenašla; čip reakce jde přes tentýž `rememberCustomEmojiContent` se značkou v textu.

## Reakce a odpovědi

- **Reakce jsou pod zprávou jako čipy** (emoji a počet, moje zvýrazněná). Citovaná zpráva se
  bere z načtené historie a co v ní není, dočte se jednou z TDLib (`ConversationState.quote`,
  jako na hodinkách). Vlastní emoji jsou v čipech obrázkem (viz „Vlastní emoji"); rychlé reakce
  v nabídce je dál vynechávají (dát je chce Premium a výběr na televizi neřešíme).
- **Odpověď je karta ze dvou částí, jako na telefonu**: nahoře citace v tónované ploše
  (`tertiaryContainer`; ikona uvozovek, jméno, první řádek), pod ní odpověď v barvě bubliny.
  První verze měla svislý proužek v barvě motivu — jazyk M2, který telefon dávno nahradil
  (`konverzace-zobrazeni.md`, „Odpověď je karta ze dvou částí"). Jméno odesílatele ve skupině
  je v horním dílu, ne nad kartou. Obě části mají šířku té širší (`IntrinsicSize.Max` — uvnitř
  bubliny nic se `SubcomposeLayout` není). Text citace je `onTertiaryContainer`, ne `tertiary`:
  to je jediná dvojice, kterou M3 na té ploše garantuje. V nabídce a v dialogu odpovědi je
  citace samostatná tónovaná kartička (`QuoteCard`).
- **Nabídka nad zprávou je PODRŽENÍ OK** (`combinedClickable` s `onLongClick` — dlouhý stisk
  klávesy pozná sám; v adb `input keyevent --longpress KEYCODE_DPAD_CENTER`). Krátké OK zůstává
  médiu (video, prohlížeč, zvuk). V nabídce rychlé reakce ze sady chatu (`availableReactions`,
  nejvýš 7 — víc se do řady nevejde) a „Odpovědět", jen když se smí psát (`writeAccess`).
- **Kdo reagoval: tlačítko „Reakce" v nabídce u zprávy s reakcemi** otevře `ReactorsDialog`
  (`reactionDetails` z `core`, jedna stránka od nejnovější): avatar, jméno („Vy" u vlastní)
  a emoji, vlastní emoji obrázkem jako v čipech. Řádky jsou zaostřitelné jen kvůli rolování.
  V kanálu server seznam nedá (reakce jsou anonymní) — dialog to řekne větou; tlačítko se
  neschovává, zpráva sama neví, v jakém chatu leží.
- **Citace zprávy bez textu říká, co to je** („Fotka", „Video zpráva", „Hlasová zpráva"…,
  `mediaLabel`). `description` mají jen popisné a servisní zprávy, takže citace fotky nebo alba
  v nabídce a v dialozích ukazovala „Tuhle zprávu zatím ukáže jen telefon", přestože ji
  televize ukáže (všimnuto při ověřování reakcí na albu).
- **Nabídka i odpověď jsou `Dialog`** (vlastní okno): fokus z nich šipkou neuteče na zprávy pod
  nimi, Zpět zavře dialog, ne konverzaci, a po zavření se fokus vrátí na zprávu v hlavním okně.
  **`usePlatformDefaultWidth = false`**: výchozí šířka dialogu je kolem 440 dp, takže strop
  panelu 640 dp se neuplatnil a sedmé emoji v řadě rychlých reakcí bylo uříznuté.
- **Reakce se přepne hned** (`toggleReaction`, optimisticky), potvrdí ji update interakcí;
  nepovedená se vrátí — jen u té zprávy (první verze vracela celý seznam z doby před reakcí
  a zahodila by, co mezitím přišlo). **Běží ve scope obrazovky, ne nabídky**: nabídka se po
  výběru hned zavře a s jejím `rememberCoroutineScope` by se zrušil i rozběhnutý požadavek.
- **Pole odpovědi je jednořádkové a šipka dolů ho výslovně posílá na Odeslat.** Víceřádkové
  pole si šipku dolů bralo na pohyb kurzoru a z pole se ovladačem nedalo odejít (ověřeno na
  emulátoru). Pole dostane fokus hned, takže naskočí systémová klávesnice; její „odeslat"
  zprávu rovnou pošle.
- **Zprávu jde nadiktovat**: vlevo od pole je mikrofon (`MicButton`, tentýž systémový
  `RecognizerIntent` jako v hledání, sdílený přes `rememberVoiceInput` ve `VoiceInput.kt`).
  Šipka vlevo na začátku textu z pole odejde na mikrofon. Nadiktovaný text se **připojí** za
  napsaný (u úpravy za text zprávy), ne že ho přepíše — diktuje se po větách a oprava jedné
  věty by smazala předchozí. Kurzor jde na konec a fokus zpátky do pole, takže systémová
  klávesnice rovnou odešle. Kde rozpoznávání nic neobslouží, mikrofon chybí. **Na emulátoru se
  otevřelo okénko Google v češtině s výzvou „Řekněte zprávu"; skutečné nadiktování (zvuk) se
  ověřit nedalo**, vrácení výsledku je týž kód jako v hledání.
- **Po odeslání jde fokus na novou vlastní zprávu** a seznam k ní odroluje; jinak zůstal na
  zprávě, na kterou se odpovídalo, a nová visela pod spodním okrajem. Jen po odeslání z televize
  (`ConversationState.sentCount`): zpráva poslaná z telefonu téhož účtu by jinak odrolovala
  čtení starší historie.
- **Nová zpráva je tlačítko „Napsat zprávu" POD seznamem** (`ComposeButton`), ne pole: šipka
  dolů z nejnovější zprávy na něj skočí a OK otevře tentýž dialog jako odpověď, jen bez citace.
  Pole přímo pod konverzací by na televizi otevíralo klávesnici při každém průchodu fokusu.
  Tlačítko je jen tam, kde se smí psát (kanál ho nemá); pod komentáři „Napsat komentář".
  Seznam má, když je tlačítko vidět, dole jen malou mezeru — bezpečný okraj a místo pro dock
  nese tlačítko.
- **V komentářích pod příspěvkem totéž** (vlastní `MessageInteractions`); odpověď bez citace
  jde kořeni vlákna (`ThreadConversationRepository.send`), citace kořene se v bublině nekreslí.

## Úprava a smazání vlastní zprávy

- **V nabídce nad zprávou (podržení OK) přibude „Upravit" a „Smazat"** podle toho, co dovolí
  server (`messageActions` → `canEdit`, `canDeleteForSelf` / `canDeleteForAll`). Práva se
  dočítají až při otevření nabídky (`produceState`), tlačítka proto naskočí o chvilku později
  než reakce — u cizí zprávy nenaskočí vůbec.
- **Úprava je tentýž dialog jako odpověď** (`ReplyDialog(editing = …)`), předvyplněný textem
  zprávy; u média upravuje popisek (`editCaption`). Nezměněný text se neposílá. Výsledek přijde
  updatem úprav, takže seznam se překreslí sám.
- **Formátování při úpravě zůstává**, přestože pole na televizi je holý text: formátování
  zprávy (`composedFormatsOf` z `core`) se jen posune podle rozdílu starého a nového textu
  (`afterEdit`, tentýž výpočet jako pole na telefonu). Tučné slovo zůstane tučné, i když se za
  něj dopíše věta. Zprávu, jejíž formátování z holého textu znovu poznat nejde (zmínka jménem,
  datum, vlastní emoji — `composedFormatsOf` vrátí `null`), televize upravit nenabídne
  (`canEditKeepingFormat`): úprava by ho tiše smazala. Dřív se formátování úpravou ztrácelo.
  Ověřeno na emulátoru vlastní zprávou s tučným prvním slovem (poslanou dočasně upraveným
  buildem, televize formátování psát neumí): po dopsání „ok" zůstalo tučné.
- **Kurzor stojí na KONCI předvyplněného textu** (`TextFieldValue` s `TextRange(délka)`).
  S holým řetězcem byl na začátku a dopsat slovo znamenalo napřed dojet šipkami na konec —
  ovladačem po znaku (ověřeno na emulátoru).
- **Upravená zpráva má před časem „upraveno"**, jako na telefonu.
- **Smazání se potvrzuje dialogem** se Zrušit a tlačítky podle práv: „Smazat pro všechny"
  a „Smazat jen pro mě" (ve skupině a kanálu bývá jen jedno). **Výchozí fokus je na Zrušit**:
  dialog naskočí pod prstem hned po výběru „Smazat" v nabídce a s fokusem na smazání stačilo
  OK zmáčknuté o jednou víc — při ověřování na emulátoru tak zmizela pro všechny cizí zpráva. Album se maže celé (všechny zprávy se stejným
  `mediaAlbumId`), jinak by v bublině zůstal zbytek mřížky. Zpráva zmizí ze seznamu hned po
  úspěchu, update o smazání ji pak už nenajde.
- **Po smazání fokus nezmizí**: přejde na sousední prvek (tlačítko „Napsat zprávu" nebo
  předchozí zprávu), šipka nahoru dál vede do historie (ověřeno na emulátoru).

## Přeposlání

- **„Přeposlat" v nabídce nad zprávou** (jen s `MessageActions.canForward` — chráněný obsah ho
  zakazuje) otevře `ForwardDialog`: citace toho, co se posílá, a chaty ze seznamu, **Uložené
  zprávy první** jako na telefonu. OK na řádku zprávu rovnou přepošle; album celé
  (`ConversationState.albumIds`, společné se smazáním), id vzestupně, jak to chce TDLib.
- **Nad seznamem je hledání s mikrofonem** (`ForwardSearchField`): s dotazem se místo seznamu
  chatů ukážou nalezené (`searchChats` napřed lokálně, pak i ze serveru; 300 ms po posledním
  písmenu), Uložené zprávy jen když odpovídají. Šipka nahoru z prvního řádku jde do pole,
  šipka dolů z pole do výsledků, vlevo na začátku textu na mikrofon. Fokus při otevření zůstává
  na Uložených zprávách — pole by hned otevřelo klávesnici.
- **Víc cílů: podržení OK na řádku ho označí** a dialog přejde do výběru: OK pak řádky
  označuje a odznačuje (fajfka a plocha vybraného řádku) a pod polem hledání se objeví tlačítko
  „Přeposlat do N chatů". Rychlá cesta do jednoho chatu tak zůstala na jedno OK, výběr se
  zapíná gestem, které v dialogu jinak nic nedělalo, a věta pod nadpisem o něm říká. Výběr
  přežije hledání (chat z výsledků se přidá k vybraným ze seznamu). Posílá se po jednom;
  co se povedlo, z výběru zmizí, takže po chybě zůstanou označené jen chaty, kam zpráva
  neodešla, a tlačítko zkusí jen je. Uložené zprávy mají ve výběru vlastní klíč (`SAVED_KEY`),
  id TDLib nemusí znát.
- **Bez voleb (skrýt odesílatele, komentář).** Kam psát nejde (cizí kanál), se nefiltruje —
  telefon to dělá stejně a chyba se ukáže v dialogu.
- **Potvrzení je systémový toast** („Přeposláno do: …"): konverzace pod dialogem žádnou změnu
  neukáže, zpráva odešla jinam. Spouští se ze scope obrazovky — dialog se po úspěchu zavře.
- **Řádek během odesílání NEZAKAZOVAT** (`enabled = false`): zakázaný `ListItem` přestane být
  zaostřitelný a fokus by z dialogu utekl. Druhé OK hlídá příznak `sending`.
- **Výšku dialogu hlídá panel, ne seznam.** `DialogPanel` má strop výšky obrazovka minus
  bezpečné okraje (na TV 540 − 2 × 28 = 484 dp) a seznam v něm `weight(1f, fill = false)`:
  dostane, co zbude pod nadpisem, citací a polem hledání. Pevný strop seznamu nestačil.
  S 360 dp sahal dialog ke spodní hraně, a když přibylo pole hledání, měl panel se stropem
  280 dp 511 dp (52–1075 px v `uiautomator dump`). Snížení na 248 dp vydrželo jen
  s normálním písmem: při písmu systému 1,3 roste i nadpis, citace a pole a panel měl 493 dp.
  Totéž platí pro dialog „Kdo reagoval“.
- **Uložené zprávy TDLib nemusí znát**, dokud se neotevřou: bez id se nejdřív zavolá
  `openSavedMessages()`.
- **Přeposlaná zpráva má nad obsahem „Přeposláno od …"** (ikona a jméno z `forwardOrigin`, bez
  jména „Přeposlaná zpráva"). Přeposlaná fotka nebo video bez popisku jde do karty místo
  holého média — na holé fotce by štítek nebyl čitelný.
- Ověřeno na emulátoru: testovací zpráva z chatu s botem přeposlaná do Uložených zpráv, toast
  i štítek „Přeposláno od Notifikator" v cíli. **Víc cílů 28. 11. 2026**: podržení OK na
  Uložených zprávách (fajfka, „Přeposlat do 1 chatu"), hledání „Notifik", OK na výsledku
  přidá druhý („do 2 chatů"), tlačítko: toast „Přeposláno do 2 chatů", zpráva v obou chatech.
- **Přeposlání do otevřeného chatu se pod dialogem neukáže samo**: nová zpráva přibude pod
  zaostřenou a seznam ji drží pod okrajem, stejně jako příchozí zprávu při čtení (fokus se
  nepřesouvá). Po šipce dolů nebo novém otevření je vidět.

## Hlasovky a hudba

- **Jeden přehrávač zvuku pro celou aplikaci** (`TvAudioPlayer`, žije v `TvNavigation` a dává se
  dál přes `LocalAudioPlayer`): hraje dál při přechodu mezi chaty a seznamem, dva zvuky se
  nepřou. OK na bublině hlasovky nebo skladby ji pustí, na právě hrající ji pozastaví. Po
  dohrání hraje další stejného druhu z konverzace (hlasovky a hudba se nemíchají).
- **Skladba hraje za běhu stahování** (`StreamingSource` + `PartialFileDataSource` z `core`,
  stejně jako video). Dřív se čekalo na celý soubor: hodinový mix nebo audiokniha mají desítky
  MB a na pomalejší síti to byly minuty ticha. Přetočení za stažený úsek přesune stahování tam
  (`requestFrom` → `downloadFrom`). **Hlasovka se stáhne celá**, pak hraje — má desítky kB
  a stáhne se dřív, než by se stream rozběhl. Dokud se čeká na první data, ukazuje tlačítko
  `LoadingIndicator`.
- **Opuštěná skladba se přestane stahovat** (`abandonDownload` při výběru jiné položky
  a `stop`): odběr `files.file` stahování spustil a sám ho nezruší. Se streamováním by se
  hodinová skladba přeskočená po minutě stahovala dál celá. Rozstahovaná část zůstane v `temp/`
  do úklidu mezipaměti.
- **Ověřeno 28. 11. 2026** na emulátoru se skladbami od testovacího bota (20 min / 19 MB
  a 25 min / 36 MB, MP3): druhá hrála po ~11 s se staženými 8,8 MB z 36 („Stahování souboru …
  se přesouvá na 0" v logu = čte zdroj streamu), šedesát šipek vpravo na 10:52 přesunulo
  stahování na 15,6 MB a skladba hrála dál, přepnutí na jinou skladbu „Ruším stahování"
  a soubor v `temp/` přestal růst. **První soubor čekal na začátek stahování přes minutu**
  — to je TDLib (nejspíš první spojení na datové centrum souboru), ne přehrávač: druhý soubor
  od téhož bota začal za 11 s.
- **`MediaSession` dostává `ForwardingPlayer`, ne ExoPlayer přímo.** Fronta je v aplikaci
  a ExoPlayer má vždy jen jednu položku, takže session „další" a „předchozí" nenabídla —
  pauza z ovladače šla, další ne (ověřeno na emulátoru). Obal příkazy nabízí a posílá do fronty.
  „Předchozí" po třech vteřinách přehrávání vrací na začátek položky, jako všude.
- **Položka, která nejde stáhnout ani přehrát, se přeskočí** (`skipUnplayable`), na konci fronty
  přehrávání skončí; jinak zůstala v docku s tlačítkem, které nic nedělalo.
- **Příchozí hlasovka se po spuštění nahlásí jako poslechnutá** (`openContent`), tečka
  „nepřehráno" v bublině zmizí.
- **Dock dole uprostřed** (`AudioDock`): co hraje, průběh, čas a že je ve frontě další. Není
  zaostřitelný — ovládá se tlačítky médií a OK na bublině; další cíl fokusu by na televizi
  jen překážel. Seznamy mají, dokud je dock vidět, spodní odsazení navíc (`audioDockInset`),
  jinak by zakryl poslední řádky. Při videu se dock schová a hudbu pozastaví fokus zvuku
  (`handleAudioFocus` u obou přehrávačů).
- **Přetáčení: šipky vlevo a vpravo na zaostřené hrající bublině** (`TvAudioPlayer.seekBy`),
  hlasovka o 5 s, skladba o 10 s; pod obsahem bubliny je pak nápověda „◀ ▶ přetáčení".
  Jinde šipka do strany v konverzaci nic nedělá (zprávy jsou pod sebou), takže ji nikomu
  nebere. Tlačítka přetáčení na ovladači (`MEDIA_REWIND` / `MEDIA_FAST_FORWARD`) jdou přes
  `MediaSession` o 10 s (`setSeekBack/ForwardIncrementMs`). **Dopředu nejvýš vteřinu před
  konec**: skok na samý konec položku dohrál a pustil další z fronty, opakované šipky vpravo
  by tak nečekaně přeskakovaly na další hlasovky. Ověřeno na emulátoru se skladbou poslanou
  testovacím botem (0:02 → 0:33 → 0:24, strop 0:59, `MEDIA_REWIND` −20 s).
- **Délku skladby nemusí odesílatel uvést** (bot bez `duration` → 0): bublina pak délku ukáže
  až z přehrávače a do té doby žádnou, ne „0:00".

## Přepis hlasovek a koleček

- **„Přepsat do textu" je v nabídce podržením OK** u hlasovky a kolečka (`TranscriptAction`
  v `MessageActionsDialog`), přepis se ukáže pod záznamem: v bublině hlasovky, u kolečka v kartě
  v barvě bubliny pod ním (jako na telefonu). Druhé podržení nabídne „Skrýt přepis".
- **Přepisuje server (`recognizeSpeech`), pravidla jsou z `core`** (`transcribeOffer`,
  `SpeechTranscript`): s Premium vždycky, bez něj podle zkušebních přepisů. Výsledek nepřijde
  z volání, ale úpravou obsahu zprávy (`editedMessages`, televize je už aplikuje): napřed
  rozpracovaný text kurzívou, pak hotový, nebo chyba. Kde server odmítne (došlé přepisy, moc
  dlouhý záznam, bez Premium), zůstane tlačítko a pod záznamem věta proč — telefon to dělá
  stejně. **Přepis v zařízení televize nemá** (Gemma ani systémový rozpoznávač na ní nejsou).
- **Co je vidět, je stav obrazovky** (`Transcripts` v `MessageInteractions`), výsledek stav
  zprávy. Hotový přepis, který ve zprávě už je (přepsal ho telefon), je vidět hned; schování
  si pamatuje i ten. Znovu se server ptá, jen když přepis ve zprávě není nebo minule selhal
  obecnou chybou.
- **Ověřeno 28. 11. 2026** na emulátoru s hlasovkou od testovacího bota (`say -v Zuzana`,
  Opus): tlačítko v nabídce, po něm pod hlasovkou „Přepis do textu je v Telegramu jen
  s Premium." (účet na emulátoru Premium nemá), „Skrýt přepis" ho schová. **Skutečný přepis
  a přepis kolečka ověřené NEJSOU** — chtějí účet s Premium.

## Kolečka (video zprávy)

- **Kulatý náhled bez bubliny** (`VideoNoteBubble`, 300 dp), délka dole v kruhu, čas pod ním.
  Dřív televize kolečka neznala a ukázala jen popis zprávy.
- **OK ho přehraje na místě, v kruhu, se zvukem**; OK na hrajícím ho zastaví. Kolem kruhu
  prstenec průběhu, místo délky uběhlý čas. Hraje nejvýš jedno v celé aplikaci
  (`VideoNotePlayback` přes `LocalVideoNotes`, akce `MediaActions.toggleVideoNote`).
- **Přehrávač žije v bublině**: odjede-li bublina ze seznamu nebo se odejde z chatu, kolečko
  skončí s ní (ExoPlayer se uvolní v `onDispose`). Přes celou obrazovku by se čtvercové video
  384 px jen roztáhlo.
- **Obraz jde do `TextureView`, ne `SurfaceView`** (to bere `PlayerView`): `SurfaceView` je
  samostatná vrstva okna a oříznutí do kruhu by ignoroval.
- **Náhled zůstává nad obrazem až do prvního snímku** (`onRenderedFirstFrame`), jinak kruh
  při spuštění probliknul prázdnou plochou.
- **Soubor se stáhne celý, pak hraje** (jednotky MB, nejvýš minuta), jako hlasovka. Fokus
  zvuku řeší ExoPlayer: hrající hlasovka nebo hudba se pozastaví. Příchozí nezhlédnuté kolečko
  se po spuštění nahlásí jako zhlédnuté (`openContent`).
- Ověřeno na emulátoru s kolečkem poslaným testovacím botem (`sendVideoNote`, 384×384, 10 s):
  náhled, přehrání v kruhu s prstencem, návrat na náhled po dohrání, zastavení OK.

## Kanály: feed a komentáře

- **Kanál se otevírá jako feed příspěvků** (`ChannelFeedScreen`), ostatní chaty jako konverzace.
  O druhu rozhoduje `ChatInfo.isChannel` (`ChatScreen` v `TvNavigation.kt`), než se cokoli
  nakreslí. Karty jsou podle návodů Google obsah k vizuálnímu procházení — u kanálu, kde se
  čtou příspěvky, sedí líp než bubliny. Nejnovější nahoře, starší se dočítají ke konci.
- **Feed je mřížka karet podle návodu Google pro karty** ([Cards](https://developer.android.com/design/ui/tv/guides/components/cards),
  procházeno 27. 11. 2026): tři karty 16 : 9 vedle sebe (268 dp, mezera 20 dp — přesně šířka
  v bezpečných okrajích, 3 × 268 + 2 × 20 = 844 dp), zaostřená se zvětší o výchozích 10 %
  a dostane rámeček (`Card` z tv-material), stejně jako Rozkoukaná videa. Pod obrazem první řádek
  textu (nejvýš dva řádky) a datum, zhlédnutí („2,1 tis." — `compactCountText`, přesunuté kvůli
  tomu ze `shared` do `core`) a komentáře; reakce a zbytek textu jsou až v detailu. Dřív byl
  každý příspěvek karta přes celou šířku se 4–5 řádky textu: na obrazovku se vešly dva, a návod
  u karet pod sebou dlouhé popisy výslovně nedoporučuje. Teď je jich vidět šest.
- **Obraz karty**: fotka, náhled videa s délkou (nebo zhlédnutou částí), u alba „+N"; příspěvek
  bez média vezme obrázek náhledu odkazu (ne profilovku cíle odkazu na Telegram, `roundPhoto`).
  **Textový příspěvek má místo obrazu plošku s prvním řádkem** (`titleLarge`), který se zalomí,
  kolik se vejde; kanál jako Pavel Durov je skoro celý textový a prázdné šedé karty by se od
  sebe nedaly rozeznat. Zaostřená ploška zesvětlí na `inverseSurface` jako řádky jinde.
- **Karta nemá pevnou výšku.** Obraz je dán šířkou (dp), popisek pod ním roste s písmem systému
  a řada mřížky se natáhne podle nejvyšší karty. Dřív pevných 200 dp s textem v sp při větším
  písmu obsah ořízlo — návod k přístupnosti TV říká sp nepoužívat v nepružných komponentách.
  **Text v plošce 16 : 9 je omezený výškou a výpustku dostane sám**: Compose u
  `TextOverflow.Ellipsis` bez `maxLines` spočte řádky, které se do výšky vejdou. Ověřeno
  s `font_scale 1.3`: „…username on Teleg…" místo useknutého řádku.
- **Zaostřená karta musí do obrazu posunout celou BUŇKU, ne jen sebe** (`BringIntoViewRequester`
  na sloupci karty s popiskem, obdélník rozšířený o mezeru nahoře a bezpečný okraj dole).
  Fokus si řekne jen o zaostřitelnou kartu a mřížka bere oblast odsazení obsahu za viditelnou:
  karta ve druhé řadě stála u spodní hrany a popisek pod ní nebyl vidět vůbec.
- **Po zavření přehrávače se buňka dorovná, až se mřížka usadí** (`revealItem`). Fokus padne
  napřed na první kartu a mřížka se k ní rozjede; vrácený fokus pak kartu s videem zaostří, ale
  neodroluje (zaostřená plocha je v obraze aspoň zčásti) — karta zůstala napůl pod okrajem.
  Zjištěno logem `firstVisibleItemScrollOffset`: 115 → 0 hned po zavření.
- **Karta s videem: OK ho pustí rovnou, podržení OK otevře detail** s textem a komentáři
  (`playableVideo` v `ChannelFeedScreen`, `onLongClick` karty). Po videu hrají videa starších
  příspěvků jako z detailu (kanál jako pořad) a po zavření přehrávače jde fokus na kartu
  posledního přehraného (viz „Kanál jako pořad").
  Zaostřená karta s videem to napoví v hlavičce vpravo („OK: přehrát · podržet: příspěvek
  a komentáře", `ChatHeader(trailing)`) — na kartu do třetiny šířky se věta nevejde a měnila by
  výšku řady. Jen u příspěvku, který je jedno video (album a fotka otevírají detail). Ověřeno
  27. 11. 2026 na videu v kanálu Pavla Durova: přehrání z karty, Zpět s fokusem na kartě.
- **Album je jeden příspěvek** (`channelPosts`: po sobě jdoucí zprávy se stejným
  `mediaAlbumId`). Popisek nese jen jedna ze zpráv alba a počty (zhlédnutí, komentáře) jen ta,
  ke které server přivázal diskusi — hledají se proto napříč albem (`statsMessage`).
- **Detail příspěvku** (`PostDetail`): vlevo médium a celý text, vpravo komentáře. Text se roluje
  šipkami nahoru a dolů, dokud je kam (vlastní `onPreviewKeyEvent` nad `verticalScroll` —
  zaostřitelná je celá plocha, ne jednotlivé odstavce). U videa OK přehraje. Detail kartu
  NAHRADÍ, neleží nad ní: fokus by šipkou utekl na karty pod detailem. Pozice feedu a karta,
  na kterou se vrátí fokus, proto žijí nad oběma.
- **Komentáře jsou vlákno v diskusní skupině**: `commentThread` (`getMessageThread`) řekne
  skupinu a kořen, historii a živé zprávy dá `ThreadConversationRepository` z `core` (tentýž
  obal jako na telefonu; `rememberConversationState(threadRootId = …)`). Kořen vlákna (kopie
  příspěvku ve skupině) se mezi komentáři vynechává, je vlevo. Počet komentářů se živě
  aktualizuje (ověřeno: 64 → 67 během testu). Kanál bez diskuse (`postStats.comments == null`)
  ukáže větu, že komentáře nemá.
- **Vstup do komentářů padá na nejnovější** (`focusRestorer` s nejnovějším komentářem jako
  zálohou, příště naposledy zaostřený). Bez toho šipka z textu vybrala komentář podle polohy
  — kterýkoli, který zrovna ležel naproti.

## Živé změny: kotva seznamu podle klíče

Nalezeno při focení snímků do Play 28. 11. 2026 a opravené: nové zprávy se v otevřené konverzaci
neukázaly a chaty s novou příchozí zprávou „zmizely" ze seznamu. **Chyba byla ve vrstvě TV, ne
v `core`** — repository updaty doručovala správně (řádek chatu měl novou zprávu, po znovuotevření
byla zpráva na místě), telefon tím netrpí.

- **`LazyColumn` drží první viditelnou položku PODLE KLÍČE.** Přibude-li položka před ní, seznam
  stojí a nová položka skončí mimo obraz. V seznamu chatů chat s novou zprávou přeskočil na první
  místo NAD horní okraj (šipka nahoru ho ukázala, takže nezmizel), v obrácené konverzaci (index 0
  dole) vznikla nová zpráva POD spodním okrajem. Vlastní odeslaná zpráva v seznamu chatů chybu
  neukázala, protože po návratu z konverzace se seznam posouvá na otevřený chat.
- **Oprava: byl-li vidět začátek, seznam k němu dojede** (`shownTopChat` v `ChatList`,
  `shownNewestKey` v `MessageList`). Kdo čte starší historii nebo je v seznamu níž, toho nová
  položka neodroluje — ověřeno, snímek před zprávou a po ní byl shodný.
- **Na „byl vidět začátek" se nehodí `visibleItemsInfo`**: počítá i položku, která zasahuje do
  odsazení seznamu (`contentPadding`), takže „první viditelná" byla už ta nová a podmínka
  nesepnula. Rozhoduje `firstVisibleItemIndex` po jednom snímku (`withFrameNanos`): dosavadní
  první položka drží místo, jen má o jedno vyšší index.
- **Zaostřený chat, který přeskládání odneslo z obrazu, se do něj vrátí** — fokus je na televizi
  jediný kurzor a jinak by zůstal mimo obrazovku.
- **TDLib po potvrzení odeslané zprávě ZMĚNÍ id.** S klíčem položky podle id zmizela položka
  s fokusem a vznikla nová: fokus odskočil na starší zprávu a zůstal jen předěl „Dnes". Klíč je
  proto dočasné id i po potvrzení (`ConversationState.itemKeys`, totéž jako na telefonu).
- **Dva posuny naráz se přeruší.** Posun po odeslání (`sentCount`) a dojetí na novou zprávu jedou
  ke stejnému konci; druhý přerušil první a fokus vyžádaný potom zprávu dorovnal jen k hraně
  seznamu, do odsazení — prstenec fokusu byl dole useknutý. Dojetí proto stojí, když seznam už
  roluje (`isScrollInProgress`).
- **Náhled vpravo má klíč i podle poslední zprávy** (`rememberChatPreview(chatId, lastMessageId)`)
  a u téhož chatu se čte bez prodlevy 300 ms. Dřív byl klíčem jen chat a náhled dostával chat
  z doby zaostření, takže zůstal u staré zprávy, zatímco řádek ukazoval novou.
- **„Šipka dolů po reakci nedojela na poslední zprávu"** se po opravě nevyvolala (reakce na
  předposlední i poslední zprávu, šipka dolů na poslední). Nejspíš šlo o tutéž zprávu schovanou
  pod okrajem.
- **Ověřeno 28. 11. 2026 na `Plume_TV_Play`** (testovací účet): zprávy od Notifikátora přes Bot
  API do jeho chatu a do skupiny triáže při fokusu níž v seznamu, náhled zaostřeného chatu,
  příchozí zpráva v otevřené konverzaci, dvě vlastní zprávy z dialogu psaní (fokus na nové
  i po potvrzení serverem), reakce a šipka dolů, nová zpráva při čtení starší historie.

## Past: `produceState` hodnotu při změně klíče nevynuluje

Stav uvnitř `produceState` je `remember` BEZ klíčů; klíče jen znovu spustí producenta. Kdo
čte obrázek podle `fileId` a hlídá `if (value == null)`, ukáže po změně klíče pořád ten první:
náhled chatu i pozadí tak měly fotku prvního zaostřeného chatu u všech dalších. Producent musí
hodnotu vynulovat sám (`rememberImage` v `ChatAvatar.kt`). **Hodinky to mají taky**
(`ChatAvatar.kt` ve `wearApp`: při změně na `null` zůstane stará fotka) — v seznamu bez klíčů
položek se to může projevit cizím avatarem u recyklované karty; neověřené.

## Lint

`./gradlew :client:tvApp:lintDebug` prochází bez chyb (od 28. 11. 2026; předtím 29 chyb, které
nikdo neviděl — lint televize CI na PR nespouští).

- **Media3 „UnstableApi“ chce `@OptIn(UnstableApi::class)`** (`androidx.annotation.OptIn`, jako
  telefon): `ForwardingPlayer`, `PlayerView` s `resizeMode` / barvou závěrky, vlastní
  `DataSource` a `ProgressiveMediaSource`. Bez anotace hlásí lint `UnsafeOptInUsageError` jako
  chybu. Při bumpu media3 se tahle API můžou změnit — to je smysl té značky.
- **`@OptIn` u vlastnosti nepokryje tělo anonymního objektu.** `private val x = object :
  ForwardingPlayer(...) { override … }` hlásil chybu u každého `override` dál; pomohla pojmenovaná
  `inner class` s anotací (`QueueSessionPlayer` v `AudioPlayer.kt`).
- **Konfigurace z `LocalConfiguration`, ne z `LocalContext.current.resources`** (jazyk hlasového
  zadání): čtení přes kontext se po změně konfigurace nesloží znovu.
- **`StateFlow.value` v kompozici je chyba** (`PrimaryAccountScope`): výchozí účet se čte
  `collectAsState()` a obsah má `key(account.slot)` jako `DefaultAccountScope` na telefonu.
  Hodinky měly totéž `.value` ve `WearApp.kt`, opravené spolu s jejich lintem
  (`docs/poznatky/wear-os.md`, „Lint").

## Vydání

Do Play z téhož tagu jako telefon a hodinky, track `tv:internal`, za repo proměnnou
`TV_RELEASE` — postup, banner do obchodu, snímky a ověření release buildu jsou v
`docs/poznatky/release.md`, „Televize (Android TV)".

## Ověření

- **Hledání ověřeno 27. 11. 2026**: záložka s lupou, pole se systémovou klávesnicí, dotaz
  „Notifikator" psaný po částech → Chaty (vlastní Notifikátor) a Globální výsledky, šipka dolů
  na výsledek a náhled vpravo, otevření chatu a Zpět s fokusem na výsledku; „TV" → Zprávy
  s časy; šipka vlevo z pole na mikrofon; mikrofon i `input keyevent KEYCODE_SEARCH` otevřou
  okénko Googlu s výzvou „Co hledáte?" česky. Skutečný hlas a text vrácený rozpoznáváním ověřené
  nejsou (emulátor mikrofon nemá připojený).
- **Příběhy ověřeny 27. 11. 2026 jen částečně**: účet v tu chvíli neměl žádné aktivní příběhy
  (řada se správně neukázala) a příběhy lidí se na skutečném účtu otevírat nesmí (autor vidí,
  kdo je zhlédl). Dočasným ladicím háčkem (odstraněným): řada s kanálem Pavla Durova a dvěma
  vymyšlenými autory — nadpis, lemy (částečně zhlédnutý, zhlédnutý, nezhlédnutý), fokus,
  návrat fokusu na avatar po zavření; prohlížeč nad pěti příběhy z profilu téhož kanálu —
  video i fotka, dílky průběhu, autor a datum, popisek, pauza, šipkou dál, Zpět. Přechod mezi
  autory (nahoru a dolů, dohrání posledního příběhu autora) na zařízení ověřený NENÍ.
- **Rozkoukaná videa ověřena 27. 11. 2026** s tříminutovým videem od Notifikátora: po startu
  synchronizace stáhla pozice z telefonu (záložka s videem rozkoukaným na Foldu, „0:26 /
  1:02:08"), přehrání 28 s → proužek a „0:27 / 3:00" v bublině, znovu otevřené „Pokračuje od
  0:27", OK → od začátku, posun a zavření → nahráno do úložiště (log `TvPositionSync`), karta
  v záložce, přehrání z karty od 1:23 a fokus zpátky na kartě, podržení → Odebrat (zmizí
  i z úložiště, videa z telefonu zůstala). Že se pozice z televize ukáže na telefonu, ověřené
  není (telefon v tu chvíli neběžel); formát i sloučení jsou tytéž funkce z `core`.
- **Nová zpráva ověřena 27. 11. 2026** v chatu s Notifikátorem: šipka dolů na tlačítko,
  dialog „Nová zpráva", odeslání, fokus na nové zprávě. Pod komentáři jen přeloženo.
- **Reakce a odpovědi ověřeny 27. 11. 2026** v chatu s Notifikátorem: podržení OK, reakce 👍
  (čip hned), odpověď přes systémovou klávesnici, citace v odeslané bublině, fokus na nové
  zprávě. V komentářích pod veřejným příspěvkem jen přeloženo (reakce i komentář by byly vidět
  všem odběratelům).
- **Hlasovky a hudba ověřeny 27. 11. 2026** (hlasovka a dvě skladby od Notifikátora): vlna
  s průběhem, dock, hraní dál po odchodu do seznamu, pauza a „další" z ovladače
  (`input keyevent KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_MEDIA_NEXT`), stav v `dumpsys media_session`.
- **Fotky a alba ověřeny 27. 11. 2026**: album (tři fotky a video, poslané Notifikátorem do jeho
  chatu) jako bublina s mřížkou, prohlížeč od první fotky alba, šipkami na video, přehrání, Zpět
  do prohlížeče a do konverzace s fokusem na albu. Pás alba v detailu příspěvku je jen přeložený
  (odebírané kanály s alby v testu nebyly).
- **Mřížka karet ověřena 27. 11. 2026 večer** (Pavel Durov, Short Drama): textové plošky,
  fotky s popisky a počty komentářů, šipky mezi řadami s celou buňkou v obraze, nápověda
  u videa, přehrání z karty a návrat, detail s komentáři a návrat, dočítání starších příspěvků,
  `font_scale 1.3` (a zpět 1.0).
- **Feed kanálu ověřen 27. 11. 2026** na veřejných kanálech, které účet odebírá a měl
  přečtené (Pavel Durov bez komentářů, Short Drama s komentáři a reakcemi): karty, detail,
  rolování textu, vstup do komentářů, zpět s fokusem na kartě. Kanál se otevře i intentem
  (`--el eu.tintera.plume.tv.CHAT_ID -1001006503122` je kanál Pavla Durova).

- **Emulátor `Plume_TV`**: obraz telefonu (`android-34`, `google_apis_playstore`) s displejem
  televize — 1920 × 1080, hustota 320 (tedy 960 × 540 dp jako televize 1080p), `hw.dPad=yes`,
  `hw.screen=no-touch`, naležato. Televizní obraz systému v SDK není (a `sdkmanager` taky ne);
  aplikaci to nevadí, `uses-feature` instalace přes `adb` nehlídá, filtruje jen Play. Televizní
  launcher a banner se na něm ověřit nedají. Šipky a Enter z klávesnice Macu jdou jako D-pad,
  z příkazové řádky `adb shell input keyevent KEYCODE_DPAD_DOWN` / `KEYCODE_DPAD_CENTER` / `KEYCODE_BACK`.
- **Ověřeno 27. 11. 2026** (česky): QR přihlášení, seznam chatů s avatary a časy, rozbalení
  lišty šipkou vlevo, přepnutí složky (fokus na první chat), ztlumený odznak, konverzace kanálu
  s předělem „Dnes", posun po zprávách šipkou, zpět s fokusem na otevřeném chatu. Heslo
  dvoufázového ověření a fotky v bublině na emulátoru vyvolané nejsou (účet heslo nemá, otevřený
  kanál fotky neměl); skutečná televize ověřená není.
- **Ověřeno 27. 11. 2026 odpoledne** (česky): záložky složek s počty, přepnutí složky
  zaostřením záložky (fokus zůstane v záložkách, náhled se vynuluje), náhled zaostřeného chatu
  s pozadím z posledního média a živým „píše…", přehrání 40s videa (poslané Notifikátorem do
  jeho chatu) za běhu stahování, skok o 10 s, pauza, schování ovládání, `MediaSession`, zpět
  s fokusem na zprávě s videem. Otevření chatu intentem:
  `am start -n eu.tintera.plume/eu.tintera.plume.tv.MainActivity --el eu.tintera.plume.tv.CHAT_ID 8875105705`
  (Notifikátor) — do testovacího chatu se neťuká přes seznam, ten se mezi snímkem a stiskem
  přeskládá.
