# Konverzace — co dodělat

Živý dokument. Nové nápady sem, hotové věci ven (a poučení do `CLAUDE.md`).
Pořadí je doporučení, ne závazek.

## Stav dnes (září 2026)

Konverzace umí: historii se stránkováním, skok na první nepřečtenou, předěly dne, živé zprávy,
odesílání s letem bubliny, koncept uložený v TDLib, „píše…" oběma směry, úpravu vlastní zprávy,
odpovědi (i swipem) se skokem na citaci, reakce, nabídku nad zprávou (odpovědět, upravit,
kopírovat, připnout/odepnout, přeposlat, informace, smazat, oblíbené samolepky), připnuté
zprávy s odepínáním, formátování textu, klikatelné zmínky a hashtagy, náhledy odkazů, fotky
a video s prohlížečem (uložení, sdílení), alba a posílání příloh, soubory (otevření, sdílení,
uložení), hlasovky (přehrávání i nahrávání), hledání v konverzaci, samolepky (i animované
`.tgs`), animovaná emoji, výběr samolepek (sady, hledání, oblíbené, trendy) a emoji (hledání,
nedávná, odstíny pleti), profilovky ve skupinách, ankety a kvízy, sponzorované zprávy, stav doručení.

Aplikace má nastavení přes DataStore (`AppSettings`) a první testy (`commonTest`,
`./gradlew :client:shared:testAndroidHostTest`).

## Doporučené pořadí dalších kroků

0. **Přestavba seznamu zpráv na bloky**, které se samy vykreslí — cíl a postup
   v [architektura-konverzace.md](architektura-konverzace.md). ~~Krok 1: builder a bloky seznamu~~
   a ~~krok 2: rozpad `MessageBubble` na bloky podle obsahu~~ a ~~krok 3: akce jako data
   (`MessageAction`)~~ a ~~krok 4: náhled odkazu jako souvislá karta~~ — hotovo. Nové druhy
   zpráv a rozvržení stavět na tom: nový blok + větev v `messageItem()`.
1. **Přílohy.** Telegram víc příloh v jedné zprávě nenese — album je skupina samostatných zpráv
   se stejným `mediaAlbumId` (viz `CLAUDE.md`, „Alba"). Po krocích:
   1. ~~**Zobrazení přijatých alb**~~ — hotovo, mřížka v jedné bublině.
   2. ~~**Posílání jedné fotky nebo souboru**~~ — hotovo (výběr, zmenšení fotky, průběh nahrávání).
   3. ~~**Posílání více příloh jako album s popiskem**~~ — hotovo (`sendMessageAlbum`, přílohy
      nad polem v M3 carouselu, popisek z textu pole).
   4. ~~**Zrušení nahrávání**~~ — hotovo (křížek v průběhu, i u dlaždice alba).
   5. ~~**Videa** ve výběru i v albu~~ — hotovo (bez překódování; přehrávání viz bod 2).
   6. ~~**Skryté a časované fotky (spoilery a sebedestrukce)**~~ — hotovo (viz sekce E níže).
2. **Přehrávání videa a hlasovek.** ~~Hlasovky~~ — hotovo (přehrávání v bublině, waveform
   s posunem, poslechnuto, ~~rychlost 1×/1,5×/2×~~, ~~pokračování další neposlechnutou~~
   i ~~audio focus a přerušení hovorem~~ a ~~sluchátko u ucha~~; `CLAUDE.md`, „Hlasovky"
   a „Hlasovka u ucha"; sluchátko u ucha ověřeno na Pixelu 11 Pro Foldu). **Hlasovky jsou tím
   hotové celé.** ~~Nahrávání~~ — hotovo, ~~podržením se zamčením~~ i ~~s náhledem před
   odesláním~~ (`CLAUDE.md`, „Nahrávání"). Zbývá náhled u kolečka a navázání v nahrávání po
   poslechu náhledu.
   ~~Video~~ — hotovo v prohlížeči (Media3 / AVPlayer). ~~GIFy~~ — hotovo (hrají v bublině ve
   smyčce bez zvuku; `CLAUDE.md`, „GIFy"). ~~GIFy z klávesnice~~ — hotovo na Androidu (Gboard;
   `CLAUDE.md`, „GIFy a samolepky z klávesnice"); ~~výběr GIFů v aplikaci~~ — hotovo
   (uložené, populární, hledání přes `@gif`; `CLAUDE.md`, „Výběr GIFů"). ~~Vložení ze schránky~~ — hotovo
   na Androidu i iOS (položka kontextové nabídky pole). Na iOS chybí GIFy ověřené na zařízení
   (~~vložení bez dotazu „Povolit vložení?"~~ — hotovo, `UIPasteControl` nad polem). ~~Streamování~~ — hotovo na Androidu (`CLAUDE.md`,
   „Video"); iOS čeká na `AVAssetResourceLoaderDelegate`. ~~Rychlost a ztlumení~~ — hotovo
   (`CLAUDE.md`, „Video"; přehrávání samo na emulátoru neověřené). Zbývá obraz v obraze.
3. ~~**Přeposílání zpráv**~~ — hotovo (položka v nabídce, výběr chatu, album celé,
   ~~víc cílů najednou~~ i ~~výběr víc zpráv~~). ~~Kopie bez odesílatele~~ (i bez popisků) — hotovo.
4. ~~**Otevírání dokumentů**~~ — hotovo (stažení s průběhem, `ACTION_VIEW` / `UIDocumentInteractionController`).
   ~~Zrušení stahování~~ — hotovo i v TDLib; ~~uložení~~ („Uložit jako…") i ~~audio~~ (hraje
   v bublině) jsou hotové dřív.
5. **Odlehčení vzhledu.** Hotové: ~~zaoblení na konci bloku bublin~~, ~~čas se stavem doručení
   z bubliny ven~~, ~~výška fotek~~, ~~fotky a videa na plochu~~, ~~náhled odkazu na plochu~~
   a ~~odkrývání časů tahem~~. Poučení v `CLAUDE.md` („Tvar bubliny a seskupení", „Čas a stav
   doručení", „Médium bez bubliny", „Rámeček fotky v bublině", „Náhled odkazu jako karta",
   „Odkrývání časů tahem", „Reakce"), ~~reakce z bubliny ven~~ i ~~odznak přečtení na ťuknutí~~.
   Sekce je tím hotová celá.
6. Drobnosti ze sekcí níž (zásobník, nabídka, entity, média, emoji).

**Haptika se vede v [roadmap.md](roadmap.md#haptika)**, i když většina chybějících míst je
právě tady (odeslání, reakce, výběr zpráv, nahrávání hlasovky, prahy tahů). Je to jeden slovník
přes celou aplikaci a na dvou seznamech by se rozešel.

---

## Odlehčení vzhledu: co nepatří do bubliny

Inspirace je Google Messages. Samostatné kusy, dají se dělat nezávisle.

Hotové je z tohohle seznamu **všechno**; zbytky jednotlivých bodů jsou u nich a poučení
v `CLAUDE.md`.

### 1. ~~Fotky a videa na plochu, mimo bublinu~~ — HOTOVO

Fotka, video i album se kreslí přímo na plochu konverzace (`showsBareMedia` v `MessageRows.kt`,
testy v `MessageRowsTest`); popisek pod nimi dostal vlastní kontejner v barvě bubliny a se snímkem
tvoří skupinu se staženým nárožím. Poučení v `CLAUDE.md`, „Médium bez bubliny".

Co z toho zůstalo:

- **Obrázek v náhledu odkazu je pořád v bublině.** Řeší ho bod 3, kde se navíc mění celý tvar
  náhledu — sjednocovat to dřív by znamenalo dělat tu práci dvakrát.
- **Fotka s citací si bublinu drží** a nejspíš to tak zůstane: citace bez pozadí se ztratí.
  Kdyby se to mělo změnit, potřebuje citace vlastní kontejner nad snímkem, ne průhledné pozadí.
- ~~**Přeposlaná zpráva se zatím nepozná.**~~ — hotovo: „Přeposláno od …" nad obsahem, i nad
  holým snímkem, ťuknutí vede ke zdroji (`CLAUDE.md`, „Přeposlaná zpráva"). Zbývá: přeposlání
  z importu (`importInfo`), oznámení veřejné služby (`publicServiceAnnouncementType`) a značka
  v náhledu seznamu chatů a v citaci.

### 2. ~~Fotky mají respektovat výšku~~ — HOTOVO

Fotka i video v bublině se **zužují, neořezávají**: výška se spočítá z poměru stran, zastropuje
se, a když se strop uplatní, dopočítá se šířka zpátky (`photoFrame` v `MediaFrame.kt`, testy
v `MediaFrameTest`). Svislý snímek obrazovky je tím vidět celý. Poučení v `CLAUDE.md`,
„Rámeček fotky v bublině".

Co z toho zůstalo:

- ~~**Popisek táhne bublinu širší, než je fotka.**~~ Vyřešil to bod 1: popisek má vlastní
  kontejner, takže kolem zúžené fotky žádná prázdná plocha nezbývá. A od té doby, co je snímek
  s popiskem jedna karta, se popisek zalamuje na šířku snímku.
- **Album zůstává ve čtvercích.** Je to vědomé rozhodnutí (`CLAUDE.md`, „Alba" — mřížka, ne
  mozaika podle poměrů stran), takže jeho změna je změna toho rozhodnutí, ne pokračování tohohle
  bodu. Řádek alba s jednou fotkou se ale ořezává pořád.
- **Náhled odkazu se ořezává dál.** Řeší ho bod 3, který ten obrázek stejně přestěhuje na plochu.

### 3. ~~Náhled odkazu: obrázek na plochu~~ — HOTOVO

Náhled s velkým obrázkem je skupina na ploše: text zprávy, snímek a pod ním web s titulkem
a popisem ve vlastním kontejneru. Šířku určuje obrázek, takže blok nemá roztřepený okraj.
Poučení v `CLAUDE.md`, „Náhled odkazu jako karta".

Co z toho zůstalo:

- ~~**Malý náhled je pořád karta s proužkem uvnitř bubliny.**~~ — hotovo: každý náhled u zprávy
  bez přílohy a citace je karta ze dvou částí (`showsLinkPreviewCard`), malý obrázek je čtvereček
  v popisu. V bublině (citace, příloha) je náhled tónovaný kontejner jako citace.
- **Barva z obrázku se nedělá** a dělat nebude — kontejnery berou barvu bubliny. Důvody zůstávají
  platné: TDLib barvu neposílá, kvantizace by chtěla keš a vlákno navíc, a předloze by se stejně
  netrefila.
- **`displayUrl` doménový `LinkPreview` pořád nenese.** Do doplňkového textu se dnes bere
  `siteName`; bez něj (a bez titulku i popisu) se kontejner nekreslí vůbec.
- ~~**Náhled nad textem** (`showAboveText`)~~ — hotovo, náhled se kreslí nad textem, když
  si to odesílatel přál.

### 4. ~~Odkrývání časů tahem doleva~~ — HOTOVO

Tah doleva posune celou plochu a vpravo odkryje čas u každé zprávy; po puštění se vrátí. Odpověď
swipem kvůli tomu jede na vlastním detektoru, který rozhodne jednou za gesto: doprava spotřebuje,
doleva pustí gesto seznamu. Čas je i v nabídce nad zprávou, prvním řádkem — čtečka obrazovky se
tahem nikam nedostane. Poučení v `CLAUDE.md`, „Odkrývání časů tahem".

Co z toho zůstalo:

- **Čas se neukazuje u samotného alba ani u sponzorované karty.** Album nese čas své nejnovější
  zprávy jako každá jiná položka; reklama žádný nemá, takže jen jede s plochou.

### 5. ~~Reakce z bubliny ven~~ — HOTOVO

Čip je kolečko s emoji a počtem jako odznakem v rohu (od dvou výš) a leží PŘES dolní nároží
bubliny na straně odesílatele — dolní půlka visí ven, jako v Google Messages. Jeden řádek,
který se posouvá do strany. Ťuknutí otevře
**detail „kdo reagoval"** (`ReactionDetailsSheet` nad `getMessageAddedReactions`); přidat a odebrat
jde jen z nabídky nad zprávou. Poučení v `CLAUDE.md`, „Reakce".

Co z toho zůstalo:

- **V kanálu detail není** (`BROADCAST_FORBIDDEN`), takže tam čip nereaguje na ťuknutí. Kdyby
  přibyla práva admina, dá se to pustit podle nich — dnes se rozhoduje jen podle
  `ChatInfo.isChannel`.
- **Detail je jedna stránka po padesáti řádcích.** Stránkování by znamenalo držet offset ve stavu
  sheetu; u příspěvku s tisíci reakcemi to stejně nikdo neprojde.
- ~~**Detail nejde filtrovat podle emoji.**~~ — hotovo (tlačítka nad seznamem, filtruje server).

### 6. ~~Ťuknutí na vlastní zprávu ukáže odznak přečtení~~ — HOTOVO

Ťuknutí na vlastní zprávu ukáže odznak přečtení i u starší zprávy, druhé ho schová. Automatický
odznak u nejnovější zprávy zůstává — `readBadgeRows` vrací obojí. Poučení v `CLAUDE.md`,
„Čas a stav doručení".

Co z toho zůstalo:

- **U zprávy složené z fotky není kam ťuknout.** Potomci s vlastním klikáním (fotka, odkaz,
  citace, náhled odkazu, samolepka) si ťuknutí berou sami; přidat jim ho by znamenalo vzít ťuknutí
  těm akcím. Komu to vadí, má stav doručení i čas přečtení v „Informace" v nabídce nad zprávou.

---

## A) Odpověď swipem — HOTOVO

Tažení zprávy doprava otevře odpověď; vlevo od bubliny se přitom odkrývá šipka. Poučení je
v `CLAUDE.md`, sekce „Odpověď swipem". Kód je v `MessageFrame` (`items/MessageFrame.kt`).

Co zůstalo otevřené:

- **Na úrovni zprávy se `canBeReplied` nekontroluje.** Známe ho teprve z `getMessageProperties`,
  a to se ptáme až při otevření nabídky — dotazovat se na každou zprávu v historii kvůli gestu,
  které se použije jednou za čas, nedává smysl. Kde se do chatu psát nesmí vůbec (kanál bez
  práva publikovat, omezená skupina), swipe nic neudělá (`ConversationState.canWrite`).

## B) Popup nad zprávou místo bottom sheetu — HOTOVO

Dlouhý stisk otevře pruh rychlých reakcí nad bublinou a nabídku akcí pod ní, zbytek obrazovky
je pod závojem s dírou na vybrané zprávě. „+" v pruhu rozbalí mřížku se všemi nabídnutými emoji —
**v témže popupu**, ne v bottom sheetu, aby vybraná zpráva zůstala vidět. Poučení je
v `CLAUDE.md`, sekce „Nabídka nad zprávou".
Kód: `MessageMenu.kt`, `MessageMenu`/`PendingDelete` v `ConversationViewModel.kt`,
`messageActions`/`deleteMessages` v repository, `core/ui/TextClipboard.kt`.

V nabídce zatím: **Odpovědět, Upravit, Kopírovat, Připnout/Odepnout, Přeposlat, Informace,
Smazat**, u samolepky oblíbené a u souboru Sdílet a Uložit jako…. Mrtvé položky se tam nedávají,
takže zbytek přibude s tím, co bude umět:

- ~~**Přeposlat**~~ — hotovo (výběr cílového chatu `ForwardRoute`, viz bod 3 nahoře).
- ~~**Upravit popisek média**~~ — hotovo (`editMessageCaption`; popisek jde přidat, změnit
  i odebrat, viz `CLAUDE.md`, „Úprava zprávy"). ~~Zprávu s ručním formátováním~~ — hotovo
  (pole formátování i odkaz s popiskem nese); dál ne zprávu se zmínkou jménem nebo vlastním emoji.
  Zbývá ověřit popisek ALBA (nabídka míří na zprávu s popiskem) a u hlasovky.
- ~~**Vybrat víc zpráv**~~ — hotovo: „Vybrat" v nabídce přepne konverzaci do režimu výběru,
  vybrané jde zkopírovat, přeposlat i smazat naráz (`CLAUDE.md`, „Výběr víc zpráv").
- ~~**Informace / odkaz na zprávu**~~ — hotovo: sheet s časem odeslání a úpravy, časem přečtení,
  seznamem zhlédnutí a odkazem ke zkopírování. Poučení v `CLAUDE.md`, „Informace o zprávě".
  Zbývá jen `getMessageStatistics` u příspěvků v kanálech — jiná data a vlastní obrazovka.

~~**Rozostřený závoj** i pod dalšími plochami~~ — hotovo: pruh pod vstupním polem, pruh
připnutých zpráv i plovoucí datum jsou mléčné sklo (`Glass.kt`, poučení v `CLAUDE.md`, „Vzhled
konverzace"). Zbývá:

- ~~**Ověřit vzhled bez rozostření**~~ — ověřeno na emulátoru s Androidem 9 (API 28): pás pod
  polem kryje jako dřív, plovoucí datum je úplně krycí a čitelné, závoj nabídky jen ztmavuje.
- **Změřit plynulost na zařízení**, hlavně na slabším s Androidem 12. Na emulátoru to nejde:
  `dumpsys gfxinfo` hlásí 98 % zadrhnutých snímků i v seznamu chatů BEZ skla, takže se tím měří
  hostitel, ne naše změna.
- **Respektovat systémové „omezit rozostření"** (Android 16 QPR) — jak se to aplikaci hlásí,
  zatím nevíme.
- **Sklo i jinde**: hlavička konverzace a hlavička seznamu chatů jsou taky plochy, pod které
  obsah zajíždí. Nejdřív ale ověřit cenu toho, co je hotové.

## C) Entity v textu (odkazy) — HOTOVO

Odkazy, tučné, kurzíva, podtržení, přeškrtnutí, kód a spoiler se vykreslují a odkaz se dá
otevřít. Poučení je v `CLAUDE.md`, sekce „Formátování textu (entity)". Kód: `MessageEntity.kt`,
`captionBody()` v `MessageContent.kt`, `core/ui/RichText.kt`.

Co z toho zůstalo nedodělané:

- ~~**Zmínky a hashtagy**~~ — hotovo (poučení v `CLAUDE.md`, „Formátování textu"). Příkazy
  botů taky (viz „Příkaz bota" níž); nemapují se jen cashtagy.
- ~~**Citace v textu**~~ (`BlockQuote`, `ExpandableBlockQuote`) — hotovo: tónovaný blok kreslený
  za textem z `TextLayoutResult` (`drawQuoteBlocks`), takže rozpad textu na víc `Text`ů nebyl
  potřeba. ~~Sbalení dlouhé `ExpandableBlockQuote`~~ — hotovo, jen u citace přes celý text
  zprávy (`collapsesQuote`); citace uprostřed delší zprávy se nesbaluje, na to by rozpad textu
  potřeba byl.
- ~~**Náhled odkazu nad textem** (`showAboveText`)~~ — hotovo (`CLAUDE.md`, „Náhled odkazu
  jako karta"); značka přeposlání zůstává nahoře i tak.
- ~~**Vlastní emoji** (`TextEntityTypeCustomEmoji`)~~ — hotovo v textu zprávy (`CLAUDE.md`,
  „Vlastní emoji v textu“).

## D) Otevření média — HOTOVO pro fotky, video a dokumenty

Ťuknutí na fotku nebo video je otevře přes celou obrazovku; štípnutí a dvojité ťuknutí přibližují,
tah dolů zavírá, do stran se listuje fotkami a videi chatu. Dokumenty se stáhnou a předají systému
(`CLAUDE.md`, „Soubory v chatu"). Poučení je v `CLAUDE.md`, sekce „Prohlížeč médií". Kód: `features/media/`,
`MediaRoute` v `Routes.kt`, `fullFileId` v `MessageMedia.Photo`.

Co zbývá:

- **Obrázky a videa poslané jako soubor.** ~~Náhled místo ikony v řádku souboru~~ a
  ~~rozpoznání obrázku a videa~~ a ~~otevření v prohlížeči médií~~ — hotovo (`documentKind`,
  `DocumentThumbnail`, `opensInViewer`, `asViewerMedia`). Zbývá:
  - **Ověřit na iOS**, že HEIC, WebM a MKV opravdu jdou systému a ostatní do prohlížeče.
    Seznam nepodporovaných formátů (`viewerUnsupportedFormats`) je zatím z dokumentace, ne
    z pokusu.
  - ~~**Velký náhled v bublině**~~ — hotovo (`CLAUDE.md`, „Velký náhled souboru"). Zbývá:
    obrázek nad 5 MB zůstává u rozmazaného náhledu (originál až na ťuknutí), a **velký obrázek
    bez náhledu od serveru** (u 13 i 22 MB ho server nevyrobil) je dál řádek.
  - **HEIC náhled nemá**, takže je v řádku jen ikona obrázku. Vlastní náhled by znamenal
    stáhnout celý soubor (fotka z iPhonu má jednotky MB); rámeček by pak chtěl rozměry, které
    TDLib u dokumentu nedává.
- ~~**Uložení do galerie, sdílení a „Uložit jako…"** z prohlížeče~~ — hotovo (`MediaSaver`,
  `MediaSharer`, `rememberSaveAs`). Zbývá:
  - **Ověřit na iOS** (`PHPhotoLibrary` s dotazem na oprávnění, `UIActivityViewController`
    i jako popover na iPadu). ~~Android 9 a starší~~ — ověřeno na emulátoru s API 28: přijde
    dotaz na `WRITE_EXTERNAL_STORAGE`, soubor skončí v `/sdcard/Pictures/Plume/` pod jménem
    `Plume_<datum>_<čas>.jpg` a zaregistruje se i v MediaStore (`bucket_display_name=Plume`).
  - ~~**Sdílet a Uložit jako… u souborů v konverzaci**~~ — hotovo (položky nabídky nad
    zprávou `ShareFile` a `SaveFileAs`). Ověřit na iOS, kde je jen Sdílet.
- ~~**Odpověď z prohlížeče**~~ — hotovo (šipka vlevo v dolní liště, `ConversationReplyRequests`).
- **Přiblížení dvojitým ťuknutím a štípnutím po přestavbě na karty** je ověřené jen
  čtením kódu; `adb input` dvojité ťuknutí nestihne a štípnutí neumí.
- **Samolepky `.webm`** (VP9 s průhledností) jsou pořád jen statický náhled — potřebují
  softwarový dekodér (libvpx), iOS WebM neumí vůbec.
- **Průběh stahování originálu.** `RemoteImage` ukazuje náhled z bubliny, dokud nedorazí ostrá
  verze, ale u velké fotky na pomalé síti není poznat, že se něco děje.
- **Let z bubliny na širokém rozvržení.** `LocalTwoPane` tam sdílené prvky vypíná kvůli seznamu
  vedle konverzace; pro prohlížeč to neplatí (ten překryje všechno), ale rozlišit to znamená
  zjemnit ten příznak.

## E) Skryté fotky a časovaná média — HOTOVO

Spoiler (`hasSpoiler`) i mizející média (jednorázové zhlédnutí a časovač) se zobrazují
i odesílají. Mizení se nabízí jen jedné příloze v soukromém chatu, zbývající čas se bere
od TDLib (`selfDestructIn`) a prohlížeč s mizejícím médiem má na Androidu `FLAG_SECURE`.
Poučení v `CLAUDE.md`, „Spoilery a mizející média".

Co zbývá:

- **Odesílací strana je ověřená na emulátoru**, příjmová ne. Ověřeno: nabídka režimů
  v soukromém chatu (a její nepřítomnost v Uložených zprávách i ve skupině), viditelné vypnutí
  mizení u alba se snackbarem, odeslání obou režimů a `FLAG_SECURE` (příznak `SECURE`
  v `dumpsys window`, ne jen černý snímek). Postup je v `CLAUDE.md`, „Jak to ověřit".
- **Příjem zatím nikdo neviděl a potřebuje druhý uživatelský účet.** Do bota se mizející
  médium poslat DÁ (chat s botem je soukromý chat a server ho přijme), ale bot ho neodešle —
  Bot API mizení nezná, `sendPhoto` nemá `ttl_seconds`. U vlastní odeslané zprávy se odpočet
  z principu nespustí, takže přes ni se příjem ověřit nedá. Hlavně jde o to, jestli jednorázové
  zhlédnutí TDLib nevyprší hned po `openMessageContent`, ještě v otevřeném prohlížeči.
- **Odpočet je jen v paměti a podle `messageId`.** Po restartu ho zachrání `selfDestructIn` od
  TDLib; spotřebované jednorázové médium ale ukáže vypršení až ve chvíli, kdy TDLib vymění obsah
  za `MessageExpiredPhoto`.
- **iOS snímky obrazovky nezakáže** (API na to nemá). Umí jen zpětně zjistit, že snímek vznikl
  (`UIApplication.userDidTakeScreenshotNotification`); co s tím, je otevřené.

## Zásobník: co jsem našel při čtení kódu

Seřazeno podle toho, jak moc to bolí.

**Chyby** — opraveno, poučení v `CLAUDE.md` („Hlášení přečtení a indexy" a „Odesílání
a scrollování" u duplicitního klíče po potvrzení odeslání).

- ~~**Po přepnutí jazyka aplikace zůstanou servisní zprávy (a další „náš" text) ve starém
  jazyce.**~~ — opraveno: převedené zprávy nesou `UiText` a překládají se až v UI (`CLAUDE.md`,
  „Jazyky"). Zbývá náhradní název souboru bez jména a štítky médií uvnitř článku.

**Chybějící základ**

- ~~**Práva po druzích obsahu.**~~ — hotovo (`ContentAccess` ve `WriteAccess.kt`: sponka,
  výběr samolepek, mikrofon i vložení z klávesnice se řídí právy chatu; `CLAUDE.md`, „Práva psát").
- ~~**Pruh připnutých podle scrollu.**~~ — hotovo (`pinnedForScroll`; `CLAUDE.md`, „Připnuté
  zprávy"). Skok z pruhu kotví nahoru, takže procházení jde krok za krokem.
- ~~**Označení části textu.**~~ — hotovo („Vybrat text" v nabídce otevře sheet s textem;
  `CLAUDE.md`, „Nabídka nad zprávou").

---

## ~~Volby náhledu odkazu při psaní~~ — HOTOVO

Náhled nad polem, volby velikosti a polohy, vypnutí; volba se ukládá s konceptem. Poučení
v `CLAUDE.md`, „Náhled odkazu při psaní". Co zbývá:

- ~~**Který odkaz**, když je jich v textu víc.~~ — hotovo: sekce „Odkaz" v sheetu voleb
  (`CLAUDE.md`, „Náhled odkazu při psaní").
- ~~**Volba při úpravě zprávy**~~ — hotovo: úprava volbu z původní zprávy přebírá a jde ji
  i měnit (hlavička náhledu a sheet jako při psaní). Zapnout náhled zpět u zprávy poslané
  bez něj při úpravě nejde — stejně jako při psaní ho ✕ vypne do vyprázdnění pole.
- **Náhled na iOS** ověřený není (kód je společný, sheet i hlavička jsou v commonMain).

---

## ~~Odesílání formátovaného textu~~ — HOTOVO

Tučné, kurzíva, podtržení, přeškrtnutí, odkaz s popiskem, spoiler, kód a citace z lišty s ikonami, která
vyjede pod polem s označeným textem; formátování jde se zprávou, s konceptem i s úpravou (formátované zprávy se tím dají
upravovat). Poučení v `CLAUDE.md`, „Formátování při psaní". Zbývá:

- **Ověřit iOS** (jen přeložené) — hlavně jestli lištu pod polem nepřekryje systémová nabídka
  výběru nebo návrhy klávesnice.
- ~~**Psaní s formátem bez výběru**~~ — hotovo: ikona „Aa" v poli otevře lištu, formát platí pro další psaní (`CLAUDE.md`, „Formátování při psaní").
- ~~**Sbalitelná citace** z lišty a **jazyk bloku kódu**~~ — hotovo (`CLAUDE.md`, „Formátování při psaní").
- **`Modifier.contentReceiver`** místo obchvatu přes `InterceptPlatformTextInput` u GIFů
  z klávesnice by chtěl pole nad `TextFieldState`; formátování na to nepotřebovalo čekat
  (vlastní rozsahy nad `TextFieldValue`), takže přechod zůstává samostatný úkol.

---

## ~~Odpověď s citací části zprávy~~ — HOTOVO

Text kopie zprávy v nabídce jde označit a „Odpovědět" se změní na „Citovat"; přijaté citace se
kreslí s úryvkem (`CLAUDE.md`, „Citace z nabídky"). Zbývá:

- **Označení v kopii vysoké zprávy** zmenšené a oříznuté v nabídce (`placeMenuMessage`) ověřené
  není — jde vybírat jen z viditelné části a úchyty na zmenšené vrstvě nikdo neviděl.
- **Sheet „Vybrat text"** je teď skoro zbytečný (kopírovat úryvek jde přímo v nabídce). Zvážit,
  jestli ho nevyhodit; zůstává pro zmenšené a oříznuté zprávy.
- **iOS** — kód je společný, na simulátoru ověřené není (lišta výběru tam kreslí systém jinak).

---

## Tlačítka botů

~~**Tlačítka pod zprávou**~~ — hotovo: zpětné volání s odpovědí bota, odkaz s potvrzením,
kopírování textu, uživatel, zašedlé tlačítko a barvy od bota (`CLAUDE.md`, „Tlačítka botů pod
zprávou"). Zbývá:

- **Druhy, které Plume zatím jen vysvětlí**: mini aplikace (`openWebApp` — WebView s předáním
  dat), přihlášení na web přes Telegram (`getLoginUrlInfo` / `getLoginUrl`, dialog s doménou
  a právem psát), inline dotaz do jiného chatu (`switchInline` — výběr chatu a vložení
  `@bot dotaz` do pole), platba, hra a zpětné volání s heslem dvoufázového ověření.
- ~~**Vlastní klávesnice** a **vynucená odpověď**~~ — hotovo, klávesnice nad polem
  (`CLAUDE.md`, „Vlastní klávesnice bota"). Zbývá: tlačítka polohy, ankety, výběru lidí
  a chatů (`shareUsersWithBot`, `shareChatWithBot`), mini aplikace a zakládání botů; ověřit
  odeslání čísla; jednorázová klávesnice se po znovuotevření chatu ukáže rozbalená (TDLib
  neříká, že už byla použitá).
- ~~**Vlastní emoji v popisku tlačítka** (`iconCustomEmojiId`)~~ — hotovo.
- **Ověřit na iOS** — kód je společný, na simulátoru to nikdo neviděl.

## Výběr samolepek a emoji

Hotové: záložky Nedávné, Oblíbené, Trendy a sady se swipem; hledání samolepek i emoji; oblíbené;
sada ze samolepky v chatu; emoji vkládané do textu včetně nedávných. Zbývá:

- **Dvě různé pleti u emoji s více osobami** (👫 s různým odstínem každé osoby) — nabízí se jen
  stejný odstín pro všechny.
- **Emoji novější než Unicode 15.0** — až je budou znát systémová písma podporovaných verzí.

## Co TDLib umí a Plume zatím ne (audit září 2026)

Prošlo se všech 105 podtypů `MessageContent` v `tdl-coroutines 15.0.0`, pole `Message`,
entity textu a klávesnice botů proti tomu, co čte `core/telegram`. **Každý typ má aspoň
pravdivý popis** (`describe()` / `serviceText()`), nic tedy nekončí jako „neumíme zobrazit" —
ale řada věcí je jen řádek textu, a pole níž se zahazují úplně. Seřazeno podle toho, jak
snadno na to uživatel narazí.

**Obsah, který se dnes ukazuje jen jako řádek textu nebo nehybný náhled**

- ~~**Ankety a kvízy**~~ — hotovo (hlasování, výsledky, kvíz s vysvětlením, zrušení hlasu,
  ukončení, kdo hlasoval; `CLAUDE.md`, „Ankety a kvízy"). Zbývá:
  - ~~**Zakládání anket**~~ — hotovo (sponka → Anketa, i tlačítko bota, jako odpověď, s dobou
    trvání nebo přesným časem konce, popisem, „výsledky až po skončení" a v kanálu omezením na
    členy a země). Zbývá ověřit v kanálu (omezení hlasujících) a ve skupině.
  - ~~**Média v anketě a v odpovědích**~~ — hotovo (zobrazení všech druhů, otevření
    v prohlížeči; formulář nabízí fotku, video, GIF, samolepku, odkaz a místo). ~~Média ve
    vysvětlení kvízu (`explanationMedia`)~~ — hotovo (zobrazení i formulář).
  - ~~**Přidání odpovědi**~~ — hotovo (`canAddOption`, `addPollOption`, dialog pod odpověďmi).
    ~~Přidání odpovědi s médiem~~ — hotovo (tatáž nabídka příloh jako ve formuláři). Zbývá
    ověřit ve skupině.
  - ~~**Tváře posledních hlasujících**~~ — hotovo (`recentVoterIds`, až tři přes sebe v zápatí).
    Zbývá ověřit se skutečnými hlasy víc lidí (testovací účet hlasuje jen sám za sebe).
- ~~**Video kolečka** (`MessageVideoNote`)~~ — hotovo: kulatě bez bubliny, samy potichu ve
  smyčce, zvuk s průběhem po ťuknutí, zhlédnutí (`CLAUDE.md`, „Video kolečka"). ~~**Nahrávání
  kolečka**~~ — hotovo (přepnutí ťuknutím, nahrávání podržením s tahem na zrušení a zámek;
  `CLAUDE.md`, „Nahrávání koleček"). Zbývá přepis řeči (`speechRecognitionResult`), ověřit
  zhlédnutí u odesílatele, **nahrávání na iOS ověřit na telefonu** (simulátor kameru nemá),
  **přepnutí kamery během nahrávání na iOS** (chce `AVAssetWriter` místo
  `AVCaptureMovieFileOutput`) a na Androidu ověřit přepnutí na zadní kameru. Náhled před odesláním (přehrát si zamčené kolečko) zatím není.
- ~~**Hudba** (`MessageAudio`)~~ — hotovo: přehrávání v bublině s obalem, interpretem, posunem
  a rychlostí, navazování další skladby, sdílení a uložení (`CLAUDE.md`, „Hudba"). ~~Hudba
  v profilu (záložka Hudba)~~ — hotovo. ~~Ovládání z notifikace / zamykací obrazovky, přehrávání
  na pozadí~~ — hotovo (`MusicQueue`, Media3 MediaSession, na iOS Now Playing). Zbývá ověřit
  iOS a zamykací obrazovku na telefonu. Hlasovky se na pozadí nepřehrávají, s odchodem
  z konverzace dál končí.
- ~~**Poloha, živá poloha a místo**~~ — hotovo: statická mapa od Telegramu, ťuknutí do map
  platformy, u živé polohy do kdy se sdílí, odeslání polohy nebo místa ze sponky. Zbývá
  sdílení živé polohy (běh na pozadí) a upozornění na blízkost (`proximityAlertRadius` — má
  smysl až s vlastním sdílením). ~~Směr pohybu (`heading`)~~ — hotovo (šipka místo špendlíku).
- ~~**Kontakt** (`MessageContact`)~~ — hotovo: karta s tváří, jménem a číslem, „Napsat
  zprávu" (`CLAUDE.md`, „Kontakt"). Zbývá **přidat do kontaktů** (mění adresář, ověřit jen se
  souhlasem) a ~~**sdílet kontakt** ze sponky~~ (hotovo, s potvrzením čísla). ~~Sdílet vlastní
  kontakt~~ — hotovo (karta „Můj kontakt" nad kontakty).
- ~~**Hovory** (`MessageCall`)~~ — hotovo: řádek se směrem, zmeškáním a délkou
  (`CLAUDE.md`, „Zpráva o hovoru").
- ~~**Kostka** (`MessageDice`)~~ — hotovo: animace hodu od serveru, automat po vrstvách
  (`CLAUDE.md`, „Hod kostkou"). Samotné 🎲 (a ostatní emoji
  hodu) z pole odejde jako hod. ~~Efekt výhry~~ — hotovo (konfety na snímku výhry, jen u vlastního hodu).
- ~~**Placená média** (`MessagePaidMedia`) — rozmazaný náhled s cenou~~ — hotovo (`CLAUDE.md`,
  „Placená média"). Zbývá odemknutí za hvězdy (placení nemáme) a mřížka víc zaplacených položek.
- **Sdílený příběh** (`MessageStory`), **dárky, soutěže, faktury, hry** — dnes text. Stojí na
  stories a hvězdách, které aplikace nemá; zvážit aspoň kartu s náhledem.
- ~~**Článek** (`MessageRichMessage`)~~ — hotovo jako text s formátováním (`CLAUDE.md`, „Článek"). Zbývá kreslit média článku (dnes „[Fotka]" s popiskem), sbalitelné bloky a posílání článků (jen s Premium).

**Údaje o zprávě, které TDLib posílá a my zahazujeme**

- ~~**Zhlédnutí u příspěvků kanálu**~~ — hotovo: zhlédnutí, přeposlání a počet komentářů pod
  příspěvkem (`CLAUDE.md`, „Počty pod příspěvkem"). Počet komentářů ověřený na zařízení není
  (testovací účet neodebírá kanál s diskusí).
- ~~**Komentáře u příspěvků kanálu**~~ — hotovo: čip „N komentářů" pod příspěvkem otevře
  vlákno v diskusní skupině (`CLAUDE.md`, „Komentáře"). Zbývá: **odeslání komentáře ověřit**
  (veřejné, jen se souhlasem), komentovat bez vstupu do skupiny (`joinToSendMessages`), ~~tváře
  posledních komentujících (`recentReplierIds`)~~ — hotovo, hledání a koncept ve vlákně (`topicId`),
  ~~skok na první nepřečtený komentář~~ — hotovo (`CLAUDE.md`, „Komentáře").
- ~~**Odpověď na zprávu z jiného chatu**~~ — hotovo (citace z `origin` a `content`, ťuknutí
  otevře ten chat; `CLAUDE.md`, „Odpovědi"). **Ověřené jen sondou**, skutečnou odpověď
  z jiného chatu zatím nikdo neviděl. Zbývá **odpověď na příběh** (`MessageReplyToStory`),
  až budou příběhy. ~~Náhled obrázku v citaci~~ — hotovo (i u odpovědi z jiného chatu).
- ~~**Citlivý obsah**~~ — hotovo: fotka, video i GIF rozostřené se štítkem „Citlivý obsah",
  odkrytí ťuknutím (`CLAUDE.md`, „Citlivý obsah"). Zbývá:
  - ~~**přepínač „Zobrazovat citlivý obsah"** v Nastavení~~ — hotovo (Obecné → Soukromí);
    samotné přepnutí na účtu ověřené není, jen čtení volby,
  - ~~citlivý **chat** (`Supergroup.restrictionInfo`, `User.restrictionInfo`) — oficiální klient
    varuje už při otevření kanálu~~ — hotovo (varování s „Přesto zobrazit"),
  - ~~náhled odkazu, náhled souboru a samolepka v citlivé zprávě zůstávají ostré~~ — hotovo,
  - ~~**omezený obsah** (`restrictionReason` bez citlivosti): věta od serveru místo obsahu~~ — hotovo
    (`CLAUDE.md`, „Omezený obsah"). ~~Omezený CHAT~~ (`User` / `Supergroup.restrictionInfo`) — hotovo.
- ~~**„přes @bot"**~~ (`viaBotUserId`) — hotovo (`CLAUDE.md`, „Inline boti"). ~~**Podpis autora**
  v kanálu (`authorSignature`) a **štítek člena** (`senderTag`)~~ — hotovo (`CLAUDE.md`, „Podpis
  autora a štítek člena", ověřené jen sondou). ~~**Boosty odesílatele**~~ (`senderBoostCount`)
  — hotovo, blesk s počtem u jména (ověřené jen sondou).
- ~~**Efekty zprávy**~~ (`effectId` → `getMessageEffect`) — hotovo: značka vedle zprávy,
  animace po příchodu a ťuknutím (`CLAUDE.md`, „Efekty zprávy"). Zbývá:
  - ~~**poslat zprávu s efektem**~~ — hotovo (podržení odesílacího tlačítka → „Odeslat
    s efektem…"). ~~Náhled animace ve výběru~~ — hotovo (podržením). Zbývá prémiové efekty s Premium,
  - ~~přehrát efekt i u NEPŘEČTENÉ zprávy~~ — hotovo,
  - ověřit u efektu prémiové samolepky (odchozí zpráva ověřená).
- ~~**Kontrola faktů** (`factCheck`)~~ — hotovo: karta pod příspěvkem (`CLAUDE.md`, „Kontrola faktů"), ověřená jen sondou.
- ~~**Automatické mazání** (`autoDeleteIn`)~~ — hotovo: čas smazání v „Informace", odznak chatu v hlavičce.

**Entity v textu**

- ~~**Vlastní emoji v textu zprávy**~~ a ~~**mimo text**~~ (reakce, emoji status u jména, náhled
  v seznamu chatů, citace, ikona tlačítka bota) — hotovo (`CLAUDE.md`, „Vlastní emoji v textu“
  a „Vlastní emoji mimo text“). Zbývá **posílání** (jen s Premium, výběr vlastních emoji a reakcí
  mimo nabídnuté) a vlastní emoji ve výsledcích hledání zpráv (ukázka se ořezává, offsety by se
  musely posunout). V notifikaci zůstává náhradní znak záměrně. **Skutečná data od člověka
  ověřená nejsou** — bot nic z toho poslat nesmí (viz CLAUDE.md).
- ~~**Příkaz bota** (`BotCommand`)~~ — hotovo: ťuknutí příkaz pošle (`CLAUDE.md`, „Příkazy bota“).
- ~~**Čas v médiu**~~ — hotovo: „1:23" pustí video, hlasovku, skladbu i kolečko od toho místa,
  v téže zprávě i v odpovědi (`CLAUDE.md`, „Čas v médiu"). Zbývá video v náhledu odkazu.
- ~~**Datum a čas** (`DateTime`)~~ — hotovo: v místním pásmu a jazyce aplikace, jako inline
  obsah místo textu pisatele, takže se offsety neposouvají (`CLAUDE.md`, „Datum a čas v textu").
  Karta a cashtag jsou okrajové.

**Konverzace jako celek**

- ~~**Témata ve skupinách (fóra)**~~ — hotovo: seznam témat místo konverzace, konverzace tématu,
  psaní do tématu, „Zobrazit jako zprávy", cesty do tématu z notifikace, odkazu, hledání a zmínky, „píše…" jen z tématu (`CLAUDE.md`, „Témata ve skupinách"). ~~Zakládání,
  přejmenování, uzavření, připnutí, ztlumení a smazání tématu, skrytí obecného tématu, hledání
  témat podle názvu a zapnutí témat ve skupině~~ — hotovo (`CLAUDE.md`, „Správa témat"). Zbývá:
  **ikona tématu vlastním emoji** (výběr emoji; jen s Premium), **podrobná upozornění tématu**
  (náhled, zmínky — dnes jen ztlumit), **pořadí připnutých** (`setPinnedForumTopics`), **téma
  v notifikaci na iOS**, **psaní do tématu ověřit** (teď už jde v testovací skupině „Plume test
  pozvánek" se zapnutými tématy) a **dvoupanel na širokém displeji**.
- ~~**Nepřečtené zmínky a reakce**~~ — hotovo: tlačítka „@" a „❤" nad tlačítkem dolů, odznaky
  v seznamu chatů, „Označit vše" podržením (`CLAUDE.md`, „Nepřečtené zmínky, reakce a hlasy v anketě"). Zbývá:
  **ověřit skutečnou zmínku ve skupině** a **skutečný nový hlas v anketě** (tlačítko s anketou je
  hotové, ověřené jen podstrčeným počtem). ~~Zvýraznění, KDO reagoval~~ — hotovo (`CLAUDE.md`,
  „Reakce"); jména ve skutečné skupině ověřená nejsou.
- ~~**Prázdný chat s botem**~~ — hotovo: profil bota (tvář, jméno, popis), tlačítko Start
  i parametr ze startovacího odkazu (`CLAUDE.md`, „Spuštění bota"). ~~Klikatelné odkazy
  v popisu~~ — hotovo. ~~Startovací odkaz do skupiny (`t.me/bot?startgroup=…`)~~ — hotovo (výběr skupiny a potvrzení; samotné přidání ověřené není, chce testovací skupinu). Zbývá ověřit obrázek a GIF od bota.
- **Napovídání při psaní**: ~~`/` příkazy bota~~ — hotovo i s tlačítkem seznamu příkazů
  (`CLAUDE.md`, „Příkazy bota“; ve skupině ověřit příponu `@bot`). ~~`@` členové skupiny~~ —
  hotovo i se zmínkou jménem (`CLAUDE.md`, „Napovídání zmínek"; ve skutečné skupině ověřit).
  Zbývá tlačítko nabídky bota s mini aplikací (`BotMenuButton`). ~~Emoji podle slov~~ — hotovo
  (`CLAUDE.md`, „Emoji podle slov"). ~~Inline boti
  (`@gif kočka` v poli)~~ — hotovo i s „přes @bot" u zprávy (`CLAUDE.md`, „Inline boti"); zbývá
  ověřit tlačítko „nastavit u bota", bota s polohou a inline bota ve skupině.
- ~~**Naplánované zprávy a odeslání bez zvuku**~~ — hotovo: podržení odesílacího tlačítka,
  seznam naplánovaných za hodinami u pole (`CLAUDE.md`, „Naplánované zprávy"). ~~Plánování
  hlasovky a ankety~~ — hotovo. ~~Plánování samolepky~~ — hotovo (tlačítko u podržené
  samolepky i GIFu ve výběru), ~~anketa naplánovaná jako odpověď~~ (hotovo — formulář
  odpověď ohlásí jako spotřebovanou), opakované připomínky
  (`repeatPeriod`, jen s Premium) a ověřit „až bude online" s člověkem.
- ~~**Ověření, podvod, falešný účet**~~ — hotovo: štítek u jména v seznamu chatů, v hlavičce
  konverzace a v profilu, u podvodu a falešného s vysvětlením (`CLAUDE.md`, „Ověření a štítek
  podvodu"). ~~Značka ověření od bota (`botVerificationIconCustomEmojiId`, vlastní emoji)~~
  — hotovo i s důvodem v profilu (ověřené jen sondou) a ~~štítek u jména odesílatele ve skupině~~
  (hotovo, ověřené jen sondou).
- ~~**Emoji status** u jména~~ — hotovo. ~~**Časovač automatického mazání chatu**~~ — hotovo
  (odznak v hlavičce, nastavení v profilu, „Smaže se" v Informace; `CLAUDE.md`, „Automatické
  mazání zpráv"). ~~Výchozí doba pro nové chaty~~ — hotovo (Nastavení → Obecné → Soukromí;
  samotný zápis ověřený není, mění účet). Zbývá ověřit ve skupině se správcovskými právy.
- ~~**Překlad zprávy**~~ (`translateMessageText`) — hotovo, sheet z nabídky nad zprávou
  (`CLAUDE.md`, „Překlad zprávy"). ~~Volba cílového jazyka~~ — hotovo. Zbývá překlad celého chatu
  (`UpdateChatIsTranslatable`, Premium) a skrýt „Přeložit" u zprávy, která už je v jazyce
  aplikace (chce to rozpoznání jazyka v zařízení). **Shrnutí od serveru** (`summarizeMessage`,
  `summaryLanguageCode`) — vedle shrnutí v zařízení.
- **Vědomě NE**: tapeta a téma chatu (`Chat.background`, `theme`) a barvy jmen podle profilu
  (`accentColorId`) — aplikace má vlastní Material vzhled a `senderColor`; kdyby se přidávaly,
  je to rozhodnutí o vzhledu, ne chybějící funkce.
