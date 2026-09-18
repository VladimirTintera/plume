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

1. **Přílohy.** Telegram víc příloh v jedné zprávě nenese — album je skupina samostatných zpráv
   se stejným `mediaAlbumId` (viz `CLAUDE.md`, „Alba"). Po krocích:
   1. ~~**Zobrazení přijatých alb**~~ — hotovo, mřížka v jedné bublině.
   2. ~~**Posílání jedné fotky nebo souboru**~~ — hotovo (výběr, zmenšení fotky, průběh nahrávání).
   3. ~~**Posílání více příloh jako album s popiskem**~~ — hotovo (`sendMessageAlbum`, přílohy
      nad polem v M3 carouselu, popisek z textu pole).
   4. ~~**Zrušení nahrávání**~~ — hotovo (křížek v průběhu, i u dlaždice alba).
   5. ~~**Videa** ve výběru i v albu~~ — hotovo (bez překódování; přehrávání viz bod 2).
2. **Přehrávání videa a hlasovek.** ~~Hlasovky~~ — hotovo (přehrávání v bublině, waveform
   s posunem, poslechnuto). U hlasovek zbývá rychlost přehrávání, automatické pokračování další
   neposlechnutou, sluchátko u ucha a audio focus / přerušení hovorem. ~~Nahrávání~~ — hotovo
   (ťuknutím); chybí podržení se zamčením, náhled před odesláním a průběh nahrávání hlasovky.
   ~~Video~~ — hotovo v prohlížeči (Media3 / AVPlayer). Zbývá **streamování** (dnes se stahuje
   celé), rychlost, ztlumení, obraz v obraze a GIFy (`MessageAnimation`, přehrávat bez zvuku
   přímo v bublině).
3. ~~**Přeposílání zpráv**~~ — hotovo (položka v nabídce, výběr chatu, album celé). Zbývá
   hledání na serveru (`searchChats`), víc cílů najednou, výběr víc zpráv a kopie bez odesílatele.
4. ~~**Otevírání dokumentů**~~ — hotovo (stažení s průběhem, `ACTION_VIEW` / `UIDocumentInteractionController`).
   Zbývá zrušení stahování, uložení do Souborů/Downloads a otevření audia (zatím se jen popisuje).
5. **Odlehčení vzhledu.** Hotové: ~~zaoblení na konci bloku bublin~~, ~~čas se stavem doručení
   z bubliny ven~~, ~~výška fotek~~, ~~fotky a videa na plochu~~, ~~náhled odkazu na plochu~~
   a ~~odkrývání časů tahem~~. Poučení v `CLAUDE.md` („Tvar bubliny a seskupení", „Čas a stav
   doručení", „Médium bez bubliny", „Rámeček fotky v bublině", „Náhled odkazu bez bubliny",
   „Odkrývání časů tahem"). Zbývají **reakce z bubliny ven** a **odznak přečtení na ťuknutí** —
   body 5 a 6 ve vlastní sekci níž.
6. Drobnosti ze sekcí níž (zásobník, nabídka, entity, média, emoji).

---

## Odlehčení vzhledu: co nepatří do bubliny

Inspirace je Google Messages. Samostatné kusy, dají se dělat nezávisle.

Body 1–4 jsou hotové, zbytky jednotlivých bodů jsou u nich a poučení v `CLAUDE.md`. Otevřené
zůstávají **5 a 6**.

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
  kontejner, takže kolem zúžené fotky žádná prázdná plocha nezbývá. Zůstalo jen to, že široký
  popisek může být širší než snímek nad ním — jako dva kusy jedné skupiny to ale vypadá správně,
  stejně jako v Google Messages.
- **Album zůstává ve čtvercích.** Je to vědomé rozhodnutí (`CLAUDE.md`, „Alba" — mřížka, ne
  mozaika podle poměrů stran), takže jeho změna je změna toho rozhodnutí, ne pokračování tohohle
  bodu. Řádek alba s jednou fotkou se ale ořezává pořád.
- **Náhled odkazu se ořezává dál.** Řeší ho bod 3, který ten obrázek stejně přestěhuje na plochu.

### 3. ~~Náhled odkazu: obrázek na plochu~~ — HOTOVO

Náhled s velkým obrázkem je skupina na ploše: text zprávy, snímek a pod ním web s titulkem
a popisem ve vlastním kontejneru (`showsBareLinkPreview`, `BareLinkPreview`). Šířku určuje
obrázek, takže blok nemá roztřepený okraj. Poučení v `CLAUDE.md`, „Náhled odkazu bez bubliny".

Co z toho zůstalo:

- **Malý náhled je pořád karta s proužkem uvnitř bubliny.** Čtvereček 48 dp vedle textu je řádek,
  ne snímek; na ploše by se rozpadl na dva kusy. Že je malý, rozhoduje odesílatel.
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

- **„Informace o zprávě" pořád není.** V nabídce je dnes jen čas; odkaz na zprávu, kdo ji viděl
  a kdy si ji přečetl (`canGetLink`, `canGetViewers`, `canGetReadDate`) je pořád v seznamu
  u sekce B).
- **Čas se neukazuje u samotného alba ani u sponzorované karty.** Album nese čas své nejnovější
  zprávy jako každá jiná položka; reklama žádný nemá, takže jen jede s plochou.

### 5. Reakce z bubliny ven

`ReactionRow` je dnes uvnitř bubliny, tedy poslední kus obsahu, který tam nepatří: čipy mají
vlastní pozadí a bublina je nemá co ohraničovat. Mají jít **pod bublinu**, ke stavu doručení.

- **Že to na ploše funguje, je už vidět.** U holého média (fotka, video, náhled odkazu) čipy na
  ploše stojí a čtou se dobře — viz kanál s jedenácti reakcemi. Po přesunu bude cesta pro všechny
  zprávy jedna, ne dvě.
- **Zarovnat ke straně odesílatele**, stejně jako odznak doručení a holé médium.
- **Široký řádek reakcí dnes roztahuje bublinu.** Venku poroste vedle ní — tak to dělá Telegram
  i Google Messages a je to důvod, proč tenhle bod vůbec dává smysl.
- **Rozhodnout: co s dírou v závoji nabídky.** `Box` kolem `Surface` je ukotvení popupu a vykrajuje
  se podle něj; reakce mimo něj by při otevřené nabídce zůstaly ztmavené. Buď jsou součástí zprávy
  (díra se rozšíří, ale tvar přestane být jedno zaoblené nároží), nebo zůstanou pod závojem
  (nabídka se ptá na zprávu, ne na reakce). Druhá varianta je levnější a nejspíš správná.
- **Past: posun při swipu na odpověď** se počítá na sloupci kolem toho `Box`u, ne na bublině samotné
  — proto s ním dnes jede i odznak doručení. Reakce patří do téhož sloupce, jinak by při tahu
  zůstaly stát.
- **Mizet musí i s místem** (`shrinkVertically`, ne jen `fadeOut`) a při odjezdu kreslit poslední
  známý stav — tatáž past jako u odznaku doručení a pruhu připnutých.

### 6. Ťuknutí na vlastní zprávu ukáže odznak přečtení

„Přečteno" dnes hlásí nejvýš **jedna** zpráva v chatu (`readBadgeRow`): nejnovější, a jen když je
moje — u starších odznak nic nedodává a odpověď protějšku přečtení dokazuje sama. Někdy ale člověk
chce ubezpečení právě u té jedné konkrétní zprávy. Ťuknutí na vlastní zprávu proto odznak ukáže,
druhé ťuknutí ho zase schová.

- **Data jsou k dispozici, server se ptát nemusí.** `lastReadOutboxMessageId` je posuvný ukazatel
  na chatu, takže stav libovolné odchozí zprávy se z něj odvodí porovnáním id.
- **Pravidlo zůstává na jednom místě.** `readBadgeRow` je čistá funkce s testy; vyžádaný odznak
  patří do ní jako další vstup (id zprávy, u které si o něj uživatel řekl), ne jako přebití
  výsledku v bublině. Jinak budou o témže rozhodovat dvě místa.
- **Jedno id, ne množina** — ťuknutí na jinou zprávu odznak přepne tam a předchozí schová. Stav
  patří do `ConversationState`, aby přežil rekompozici; odchod z chatu ho zahodí.
- **Past: krátké ťuknutí na bublinu je dnes prázdné** (`onClick = {}`) a potomci s vlastním
  klikáním ho k ní nepustí — odkazy v textu, citace, fotka, náhled odkazu i samolepka si ťuknutí
  berou samy. U zprávy, která je celá fotka, tedy nebude kam ťuknout; buď se s tím počítá, nebo
  musí gesto přibýt i těm potomkům (a to je přesně ten druh úpravy, po které přestane fungovat
  otevírání médií).
- **Čtečka obrazovky se ťuknutím nikam nedostane**, stejně jako tahem u časů. Patří to proto taky
  do „Informace o zprávě" (sekce B), kde má vedle času být i `canGetReadDate`.
- **Animace už hotová je**: odznak umí naskočit i zmizet i se svým místem a při odjezdu kreslí
  poslední známý stav (viz `CLAUDE.md`, „Čas a stav doručení").

---

## A) Odpověď swipem — HOTOVO

Tažení zprávy doprava otevře odpověď; vlevo od bubliny se přitom odkrývá šipka. Poučení je
v `CLAUDE.md`, sekce „Odpověď swipem". Kód je v `MessageBubble` v `ConversationScreen.kt`.

Co zůstalo otevřené:

- **Nekontroluje se, jestli jde na zprávu vůbec odpovědět.** `canBeReplied` známe teprve
  z `getMessageProperties`, a to se ptáme až při otevření nabídky — dotazovat se na každou zprávu
  v historii kvůli gestu, které se použije jednou za čas, nedává smysl. V praxi to nevadí, protože
  chaty jen pro čtení zatím neřešíme ani ve vstupním poli.

## B) Popup nad zprávou místo bottom sheetu — HOTOVO

Dlouhý stisk otevře pruh rychlých reakcí nad bublinou a nabídku akcí pod ní, zbytek obrazovky
je pod závojem s dírou na vybrané zprávě. Poučení je v `CLAUDE.md`, sekce „Nabídka nad zprávou".
Kód: `MessageMenu.kt`, `MessageMenu`/`PendingDelete` v `ConversationViewModel.kt`,
`messageActions`/`deleteMessages` v repository, `core/ui/TextClipboard.kt`.

V nabídce zatím: **Odpovědět, Upravit, Kopírovat, Připnout/Odepnout, Smazat**. Mrtvé položky se tam nedávají, takže zbytek
přibude s tím, co bude umět:

- **Přeposlat** — potřebuje výběr cílového chatu, tedy vlastní obrazovku.
- **Upravit popisek média** (`editMessageCaption`) a **zprávu s ručním formátováním** —
  vstupní pole umí jen holý text, takže úprava se u nich zatím nenabízí.
- **Vybrat víc zpráv** — hromadné mazání a přeposílání, znamená režim výběru v celé konverzaci.
- **Informace / odkaz na zprávu** (`canGetLink`, `canGetViewers`, `canGetReadDate`).

## C) Entity v textu (odkazy) — HOTOVO

Odkazy, tučné, kurzíva, podtržení, přeškrtnutí, kód a spoiler se vykreslují a odkaz se dá
otevřít. Poučení je v `CLAUDE.md`, sekce „Formátování textu (entity)". Kód: `MessageEntity.kt`,
`captionBody()` v `MessageContent.kt`, `core/ui/RichText.kt`.

Co z toho zůstalo nedodělané:

- **Zmínky, hashtagy a příkazy botů** se nemapují. Kliknutí by nemělo kam vést — potřebují
  vyhledání uvnitř aplikace (`searchPublicChat`, `searchChatMessages`), ne prohlížeč. Až bude kam,
  přibude sem typ entity a obarvení.
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
- **Uložení a sdílení** fotky ven z aplikace.
- **Samolepky `.webm`** (VP9 s průhledností) jsou pořád jen statický náhled — potřebují
  softwarový dekodér (libvpx), iOS WebM neumí vůbec.
- **Průběh stahování originálu.** `RemoteImage` ukazuje náhled z bubliny, dokud nedorazí ostrá
  verze, ale u velké fotky na pomalé síti není poznat, že se něco děje.
- **Let z bubliny na širokém rozvržení.** `LocalTwoPane` tam sdílené prvky vypíná kvůli seznamu
  vedle konverzace; pro prohlížeč to neplatí (ten překryje všechno), ale rozlišit to znamená
  zjemnit ten příznak.

## Zásobník: co jsem našel při čtení kódu

Seřazeno podle toho, jak moc to bolí.

**Chyby** — opraveno, poučení v `CLAUDE.md` („Hlášení přečtení a indexy" a „Odesílání
a scrollování" u duplicitního klíče po potvrzení odeslání).

**Chybějící základ**

- **Skok na první nepřečtenou** — hotovo, ale s **limitem pěti stránek historie**. Kdo má
  v chatu stovky nepřečtených, skok nedostane. Dořešit by to znamenalo stránkovat oběma směry
  (kotva uprostřed přes záporný `offset` v `getChatHistory`).
- **Označení části textu.** Kopíruje se jen celá zpráva z nabídky; vybrat úryvek
  (`SelectionContainer`) zatím nejde.

---

## Výběr samolepek a emoji

Hotové: záložky Nedávné, Oblíbené, Trendy a sady se swipem; hledání samolepek i emoji; oblíbené;
sada ze samolepky v chatu; emoji vkládané do textu včetně nedávných. Zbývá:

- **Dvě různé pleti u emoji s více osobami** (👫 s různým odstínem každé osoby) — nabízí se jen
  stejný odstín pro všechny.
- **Emoji novější než Unicode 15.0** — až je budou znát systémová písma podporovaných verzí.
