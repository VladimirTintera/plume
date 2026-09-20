# Konverzace — co dodělat

Živý dokument. Nové nápady sem, hotové věci ven (a poučení do `CLAUDE.md`).
Pořadí je doporučení, ne závazek.

## Stav dnes (září 2026)

Konverzace umí: historii se stránkováním, skok na první nepřečtenou, předěly dne, živé zprávy,
odesílání s letem bubliny, koncept uložený v TDLib, „píše…" oběma směry, úpravu vlastní zprávy,
odpovědi (i swipem) se skokem na citaci, reakce, nabídku nad zprávou (odpovědět, upravit,
kopírovat, připnout/odepnout, smazat, oblíbené samolepky), připnuté zprávy s odepínáním,
formátování textu a náhledy odkazů, fotky s prohlížečem, samolepky (i animované `.tgs`),
animovaná emoji, výběr samolepek (sady, hledání, oblíbené, trendy) a emoji (hledání, nedávná,
odstíny pleti), profilovky ve skupinách, sponzorované zprávy, stav doručení.

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
   6. **Skryté a časované fotky (spoilery a sebedestrukce)** — zobrazení i odesílání (podrobněji sekce E níže).
2. **Přehrávání videa a hlasovek.** ~~Hlasovky~~ — hotovo (přehrávání v bublině, waveform
   s posunem, poslechnuto). U hlasovek zbývá rychlost přehrávání, automatické pokračování další
   neposlechnutou, sluchátko u ucha a audio focus / přerušení hovorem. ~~Nahrávání~~ — hotovo
   (ťuknutím); chybí podržení se zamčením, náhled před odesláním a průběh nahrávání hlasovky.
   ~~Video~~ — hotovo v prohlížeči (Media3 / AVPlayer). Zbývá **streamování** (dnes se stahuje
   celé), rychlost, ztlumení, obraz v obraze a GIFy (`MessageAnimation`, přehrávat bez zvuku
   přímo v bublině).
3. ~~**Přeposílání zpráv**~~ — hotovo (položka v nabídce, výběr chatu, album celé). Zbývá
   víc cílů najednou, výběr víc zpráv a kopie bez odesílatele.
4. ~~**Otevírání dokumentů**~~ — hotovo (stažení s průběhem, `ACTION_VIEW` / `UIDocumentInteractionController`).
   Zbývá zrušení stahování, uložení do Souborů/Downloads a otevření audia (zatím se jen popisuje).
5. **Odlehčení vzhledu.** Hotové: ~~zaoblení na konci bloku bublin~~, ~~čas se stavem doručení
   z bubliny ven~~, ~~výška fotek~~, ~~fotky a videa na plochu~~, ~~náhled odkazu na plochu~~
   a ~~odkrývání časů tahem~~. Poučení v `CLAUDE.md` („Tvar bubliny a seskupení", „Čas a stav
   doručení", „Médium bez bubliny", „Rámeček fotky v bublině", „Náhled odkazu jako karta",
   „Odkrývání časů tahem", „Reakce"), ~~reakce z bubliny ven~~ i ~~odznak přečtení na ťuknutí~~.
   Sekce je tím hotová celá.
6. Drobnosti ze sekcí níž (zásobník, nabídka, entity, média, emoji).

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
- **Přeposlaná zpráva se zatím nepozná.** Google Messages dává vedle takového snímku šipku
  (druhá předloha). Zdroj přeposlání (`forwardInfo`) doménový `ChatMessage` nenese vůbec, takže
  je to samostatný kus práce — ne důsledek téhle změny.

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
- **Náhled nad textem (`showAboveText`) se dál ignoruje.**

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
- **Detail nejde filtrovat podle emoji.** Telegram to umí (`reactionType`), sheet ale ukazuje
  všechny reakce pohromadě — jako v Google Messages.

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

- **Nekontroluje se, jestli jde na zprávu vůbec odpovědět.** `canBeReplied` známe teprve
  z `getMessageProperties`, a to se ptáme až při otevření nabídky — dotazovat se na každou zprávu
  v historii kvůli gestu, které se použije jednou za čas, nedává smysl. V praxi to nevadí, protože
  chaty jen pro čtení zatím neřešíme ani ve vstupním poli.

## B) Popup nad zprávou místo bottom sheetu — HOTOVO

Dlouhý stisk otevře pruh rychlých reakcí nad bublinou a nabídku akcí pod ní, zbytek obrazovky
je pod závojem s dírou na vybrané zprávě. „+" v pruhu rozbalí mřížku se všemi nabídnutými emoji —
**v témže popupu**, ne v bottom sheetu, aby vybraná zpráva zůstala vidět. Poučení je
v `CLAUDE.md`, sekce „Nabídka nad zprávou".
Kód: `MessageMenu.kt`, `MessageMenu`/`PendingDelete` v `ConversationViewModel.kt`,
`messageActions`/`deleteMessages` v repository, `core/ui/TextClipboard.kt`.

V nabídce zatím: **Odpovědět, Upravit, Kopírovat, Připnout/Odepnout, Smazat**. Mrtvé položky se tam nedávají, takže zbytek
přibude s tím, co bude umět:

- **Přeposlat** — potřebuje výběr cílového chatu, tedy vlastní obrazovku.
- **Upravit popisek média** (`editMessageCaption`) a **zprávu s ručním formátováním** —
  vstupní pole umí jen holý text, takže úprava se u nich zatím nenabízí.
- **Vybrat víc zpráv** — hromadné mazání a přeposílání, znamená režim výběru v celé konverzaci.
- ~~**Informace / odkaz na zprávu**~~ — hotovo: sheet s časem odeslání a úpravy, časem přečtení,
  seznamem zhlédnutí a odkazem ke zkopírování. Poučení v `CLAUDE.md`, „Informace o zprávě".
  Zbývá jen `getMessageStatistics` u příspěvků v kanálech — jiná data a vlastní obrazovka.

**Rozostřený závoj** (Haze) je zatím jen pod nabídkou nad zprávou. Kandidáti na totéž:
průsvitný pruh pod vstupním polem, pruh připnutých zpráv a plovoucí datum — plochy, pod které
zajíždí obsah. Otevřené: respektovat systémové „omezit rozostření" (Android 16 QPR; jak se
aplikaci hlásí, zatím nevíme) a změřit plynulost na slabším zařízení s Androidem 12.

## C) Entity v textu (odkazy) — HOTOVO

Odkazy, tučné, kurzíva, podtržení, přeškrtnutí, kód a spoiler se vykreslují a odkaz se dá
otevřít. Poučení je v `CLAUDE.md`, sekce „Formátování textu (entity)". Kód: `MessageEntity.kt`,
`captionBody()` v `MessageContent.kt`, `core/ui/RichText.kt`.

Co z toho zůstalo nedodělané:

- ~~**Zmínky a hashtagy**~~ — hotovo (poučení v `CLAUDE.md`, „Formátování textu"). **Příkazy
  botů** (`/start`) a cashtagy se dál nemapují — příkaz by ťuknutím odeslal zprávu botovi a to
  chce vlastní rozmyšlení.
- **Citace v textu** (`BlockQuote`, `ExpandableBlockQuote`) se vykreslují jako obyčejný text.
  Svislý pruh vlevo nejde udělat uvnitř jednoho `Text`; znamená to rozpad textu na bloky
  a `Column` místo jedné `Text` — samostatný kus práce.
- **Náhled odkazu nad textem** (`showAboveText`) — náhled se zatím kreslí vždycky pod ním.
- **Vlastní emoji** (`TextEntityTypeCustomEmoji`) jsou samolepky uvnitř textu; patří k médiím.

## D) Otevření média — HOTOVO pro fotky

Ťuknutí na fotku ji otevře přes celou obrazovku; štípnutí a dvojité ťuknutí přibližují, tah dolů
zavírá, do stran se listuje fotkami chatu. Poučení je v `CLAUDE.md`, sekce „Prohlížeč médií". Kód: `features/media/`,
`MediaRoute` v `Routes.kt`, `fullFileId` v `MessageMedia.Photo`.

Co zbývá:

- **Ostatní typy médií.** GIF a dokumenty se pořád jen popisují. Prohlížeč
  u nich zatím řekne, že je neumíme otevřít. Přehrávání je samostatná fáze (potřebuje platformní
  přehrávač přes `expect`/`actual`); u dokumentů stačí míň — stáhnout a předat systému.
- **Obrázky a videa poslané jako soubor.** ~~Náhled místo ikony v řádku souboru~~ a
  ~~rozpoznání obrázku a videa~~ a ~~otevření v prohlížeči médií~~ — hotovo (`documentKind`,
  `DocumentThumbnail`, `opensInViewer`, `asViewerMedia`). Zbývá:
  - **Ověřit na iOS**, že HEIC, WebM a MKV opravdu jdou systému a ostatní do prohlížeče.
    Seznam nepodporovaných formátů (`viewerUnsupportedFormats`) je zatím z dokumentace, ne
    z pokusu.
  - **Velký náhled v bublině** jako u fotky (`photoFrame`), pod ním řádek s názvem
    a velikostí. **Rozměry TDLib u dokumentu nedává**, poměr stran se bere z náhledu
    (`Thumbnail.width` / `height`). Náhled má do 320 px, ve velké bublině bude rozmazaný;
    originál bývá desítky MB. Stahovat ho automaticky jen pod nějakým stropem, jinak až na
    ťuknutí, s průběhem přes náhled jako u videa.
  - **HEIC náhled nemá**, takže je v řádku jen ikona obrázku. Vlastní náhled by znamenal
    stáhnout celý soubor (fotka z iPhonu má jednotky MB) — až s velkým náhledem výš.
- ~~**Uložení do galerie, sdílení a „Uložit jako…"** z prohlížeče~~ — hotovo (`MediaSaver`,
  `MediaSharer`, `rememberSaveAs`). Zbývá:
  - **Ověřit na iOS** (`PHPhotoLibrary` s dotazem na oprávnění, `UIActivityViewController`
    i jako popover na iPadu) a na Androidu 9 a starším (`WRITE_EXTERNAL_STORAGE`, veřejný
    adresář); vyzkoušené je jen Android 16.
  - ~~**Sdílet a Uložit jako… u souborů v konverzaci**~~ — hotovo (položky nabídky nad
    zprávou `ShareFile` a `SaveFileAs`). Ověřit na iOS, kde je jen Sdílet.
- **Odpověď z prohlížeče.** Google Zprávy mají vlevo v dolní liště tlačítko odpovědi. U nás by
  muselo vrátit do konverzace s připravenou citací — konverzace je v zásobníku pod prohlížečem,
  takže je potřeba cesta, jak jí požadavek předat (jako `ChatOpenRequests`).
- **Přiblížení dvojitým ťuknutím a štípnutím po přestavbě na karty** je ověřené jen
  čtením kódu; `adb input` dvojité ťuknutí nestihne a štípnutí neumí.
- **Samolepky `.webm`** (VP9 s průhledností) jsou pořád jen statický náhled — potřebují
  softwarový dekodér (libvpx), iOS WebM neumí vůbec.
- **Průběh stahování originálu.** `RemoteImage` ukazuje náhled z bubliny, dokud nedorazí ostrá
  verze, ale u velké fotky na pomalé síti není poznat, že se něco děje.
- **Let z bubliny na širokém rozvržení.** `LocalTwoPane` tam sdílené prvky vypíná kvůli seznamu
  vedle konverzace; pro prohlížeč to neplatí (ten překryje všechno), ale rozlišit to znamená
  zjemnit ten příznak.

## E) Skryté fotky a časovaná média (spoilery a sebedestrukce)

V TDLib jde o dva odlišné mechanismy, které se dosud v modelu ani UI nezpracovávají:

### 1. Skryté fotky se spoilerem (`hasSpoiler`)
Pouze vizuální překrytí citlivého obsahu, fotka se nemaže a zůstává v historii chatu.
- **Příjem a zobrazení**: `MessagePhoto.hasSpoiler`. V bublině i mřížce alba překrýt snímek
  třpytivým šumem (spoiler animace / blur), ťuknutím se fotka trvale odhalí.
- **Odesílání**: přepínač „Skrýt spoilerem“ u náhledu přílohy v carouselu nad vstupním polem
  (`InputMessagePhoto(..., hasSpoiler = true)`). Podporováno v 1-on-1 chatech, skupinách i kanálech
  (ne v tajných chatech).
- *(Souvislost)*: textové spoilery v popisku i těle zprávy (`TextEntityTypeSpoiler`) již aplikace umí.

### 2. Časované a mizející fotky (View-once a self-destruct timer)
Efemérní média s ochranou soukromí určená výhradně pro soukromé (1-on-1) chaty.
- **Model a typy v TDLib**:
  - `MessageSelfDestructTypeImmediately` — jednorázové zhlédnutí (View-once, po zavření zaniká).
  - `MessageSelfDestructTypeTimer(selfDestructTime)` — odpočet po zadaný počet sekund od otevření.
  - `MessagePhoto.isSecret` — příznak pro rozostřený náhled v bublině s ikonou plamínku / „1“ (tap-to-view).
  - `Message.selfDestructIn` — zbývající čas v sekundách do zničení.
- **Životní cyklus a otevření**:
  - Při otevření musí klient zavolat `client.openMessageContent(chatId, messageId)`, což v TDLib spustí
    odpočet a vyvolá `UpdateMessageContentOpened`.
- **Expirace**:
  - Po vypršení času nebo zavření view-once fotky TDLib změní obsah zprávy na `MessageExpiredPhoto`.
  - V bublině se místo fotky zobrazí stav vypršení („Fotka vypršela“ / ikona plamínku); soubor na disku
    TDLib smaže automaticky (`FileTypeSelfDestructingPhoto`).
- **Ochrana soukromí**:
  - TDLib v `MessageProperties` nastavuje `canBeSaved = false`, `canBeForwarded = false`, `canBeCopied = false`.
  - Zákaz screenshotů v UI (na Androidu `FLAG_SECURE` na okně při zobrazení secret média).
- **Odesílání**:
  - Tlačítko časovače / view-once u přílohy nad vstupním polem v soukromých chatech (`InputMessagePhoto.selfDestructType`).

## Zásobník: co jsem našel při čtení kódu

Seřazeno podle toho, jak moc to bolí.

**Chyby** — opraveno, poučení v `CLAUDE.md` („Hlášení přečtení a indexy" a „Odesílání
a scrollování" u duplicitního klíče po potvrzení odeslání).

**Chybějící základ**

- **Pruh připnutých podle scrollu.** Dnes ukazuje nejnovější a ťuknutí posouvá ke starší.
  Oficiální klient navíc při scrollu historií ukazuje tu připnutou, která je nejblíž NAD
  viditelnou částí — pruh pak slouží i jako orientace. Stačilo by id nejvyšší viditelné zprávy
  (klíč bloku) porovnat se seznamem připnutých.
- **Označení části textu.** Kopíruje se jen celá zpráva z nabídky; vybrat úryvek
  (`SelectionContainer`) zatím nejde.

---

## Výběr samolepek a emoji

Hotové: záložky Nedávné, Oblíbené, Trendy a sady se swipem; hledání samolepek i emoji; oblíbené;
sada ze samolepky v chatu; emoji vkládané do textu včetně nedávných. Zbývá:

- **Dvě různé pleti u emoji s více osobami** (👫 s různým odstínem každé osoby) — nabízí se jen
  stejný odstín pro všechny.
- **Emoji novější než Unicode 15.0** — až je budou znát systémová písma podporovaných verzí.
