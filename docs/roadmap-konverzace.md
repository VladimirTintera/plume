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
5. **Odlehčení vzhledu.** ~~Zaoblení na konci bloku bublin~~ a ~~čas se stavem doručení
   z bubliny ven~~ — hotovo, poučení v `CLAUDE.md` („Tvar bubliny a seskupení", „Čas a stav
   doručení"). Zbývají fotky a náhledy odkazů na plochu, výška fotek a odkrývání časů tahem;
   vlastní sekce níž.
6. Drobnosti ze sekcí níž (zásobník, nabídka, entity, média, emoji).

---

## Odlehčení vzhledu: co nepatří do bubliny

Inspirace je Google Messages. Tři samostatné kusy, dají se dělat nezávisle.

Hotové z tohohle seznamu: tvar bubliny na konci bloku a čas se stavem doručení.
Poučení z obojího je v `CLAUDE.md`.

### 1. Fotky a videa na plochu, mimo bublinu

Mechanismus na to **už v projektu je** a nemusí se vymýšlet: `bare` v `MessageBubble` (dnes pro
velká emoji, samolepku a animované emoji) přepne kontejner na `Color.Transparent` a barvu popředí
na `onSurface`, protože ta pak patří ploše, ne bublině, která tam není. Rozšířit se to má na
fotku, video a album.

Co je na tom skutečná práce, není ta průhlednost, ale to, co v bublině viselo s fotkou:

- **Čas a stav doručení.** Dnes jsou uvnitř bubliny. Nad fotkou nebudou čitelné bez vlastního
  podkladu (Google Messages je dává mimo snímek). Čas už v bublině není vůbec a odznak doručení
  je pod ní, takže nad fotkou nezbylo nic — tahle část je tím vyřešená.
- **Popisek.** V Google Messages je pod fotkou ve vlastním kontejneru, ne na ploše — na
  screenshotu s houbami je to vidět. Takže „bez bubliny" platí pro snímek, ne pro popisek u něj.
- **Citace a reakce.** Citace bez pozadí se ztratí, stejný důvod, proč si ji dnes drží bublinu
  i zpráva z pár emoji. Fotka s odpovědí tedy pravděpodobně bublinu potřebuje dál.

### 2. Fotky mají respektovat výšku

Tady je potřeba rozlišit dva případy, protože se chovají jinak:

- **Jedna fotka bublinu na čtverec neláme.** Počítá se
  `height = (maxWidth / ratio).coerceAtMost(MAX_PHOTO_HEIGHT)` s `MAX_PHOTO_HEIGHT = 320.dp`
  a `ContentScale.Crop`. Poměr stran se tedy respektuje, **dokud se nenarazí na strop** — svislý
  snímek se o něj ořízne. Druhá polovina problému je `fillMaxWidth()`: úzká fotka se roztáhne
  na celou šířku bubliny a ořízne se i do stran.
- **Album do čtverců láme.** Dlaždice jsou
  `((maxWidth - ALBUM_GAP * (size - 1)) / size).coerceAtMost(ALBUM_TILE_MAX_HEIGHT)`
  s `ALBUM_TILE_MAX_HEIGHT = 200.dp`, tedy čtverce. Je to vědomé rozhodnutí (viz `CLAUDE.md`,
  „Alba" — mřížka, ne mozaika podle poměrů stran), takže jeho změna je změna toho rozhodnutí,
  ne oprava chyby.
- **Past, která tu už jednou byla:** `aspectRatio` se použít nedá, strop z `heightIn` neuplatní.
  Proto se výška počítá ručně v `BoxWithConstraints` — viz `CLAUDE.md`, „Gotchas". Cokoli se
  tady bude měnit, musí zůstat u ručního počítání.
- **Potvrzený případ:** snímek obrazovky telefonu (vysoký, poměr kolem 1:2). V bublině z něj
  není vidět nic než **prostřední pruh** — zbytek `Crop` odřízne nahoře i dole, takže ze
  screenshotu nejde přečíst ani co na něm je.
- **Ořez je to, co se má odstranit, ne strop.** Zvednutí stropu samo nepomůže: vysoká fotka
  s `fillMaxWidth()` bude pořád vyšší než rozumná bublina. Cesta je nechat fotku **zúžit**:
  z poměru stran spočítat výšku, zastropovat ji, a když se strop uplatní, dopočítat šířku zpátky
  jako `height * ratio` místo `fillMaxWidth()`. Fotka pak zabírá méně šířky, ale je vidět celá —
  přesně jak to dělá Telegram i Google Messages.
- Zůstat se musí u ručního počítání v `BoxWithConstraints`; s rámečkem, který už poměr stran
  respektuje, přestane být `ContentScale` podstatný.

### 3. Náhled odkazu: obrázek na plochu

Dnes je náhled uvnitř bubliny, s proužkem akcentu vlevo (`LINK_PREVIEW_STRIPE = 3.dp`), pod
textem, a obrázek se stropem `MAX_PHOTO_HEIGHT`. Obrázek má jít na plochu jako fotka, doplňkový
text (web, titulek) pod něj do vlastního kontejneru.

- **Barvu vytaženou z obrázku NEDĚLÁME.** Rozhodnuto — za tu podobu to nestojí. Kontejner tedy
  vezme roli z `colorScheme` (kandidát `surfaceContainerHighest`), která drží kontrast v obou
  režimech sama a nic nestojí.
- Poznámka, aby se to znovu nerozebíralo: **TDLib u náhledu žádnou barvu neposílá** (`LinkPreview`
  nese `url`, `displayUrl`, `siteName`, `title`, `description`, `author`, `type` a příznaky
  zobrazení), takže by se musela kvantizovat z obrázku. Šlo by to bez nové závislosti —
  `materialkolor` je v `commonMain` a má `ImageBitmap.themeColor()` — ale znamenalo by to keš na
  `fileId` a počítání mimo hlavní vlákno, jinak by to při scrollu drhlo. V Google Messages ta
  barva navíc nevypadá jako z fotky (pod černou plotýnkou zelená Alzy), takže by se předloze
  stejně netrefilo.
- **Do doplňkového textu patří `siteName`**, případně `displayUrl` — ten dnes doménový typ
  `LinkPreview` nenese a musel by se přidat v `core/telegram`.

### 4. Odkrývání časů tahem doleva

Čas z bubliny **už zmizel** (viz `CLAUDE.md`, „Čas a stav doručení"), takže se v konverzaci dá
přečíst jen z předělu dne a z plovoucího data při scrollu. Zbývá ho zpřístupnit na vyžádání.

Google Messages **posouvá celou plochu doleva** a odkryje vpravo časovou osu; po puštění se
zase schová. Tady je ta část, která si žádá rozhodnutí:

- **Je to gesto nad SEZNAMEM, ne nad položkou.** V Messages jedou všechny řádky společně;
  u nás je vodorovný `draggable` na každé bublině zvlášť (odpověď swipem).
- **Kolize je skutečná.** Bublina má `draggable(Orientation.Horizontal)` s
  `coerceIn(0f, maxDrag)`, takže tah doleva se nikam neprojeví — ale `draggable` si gesto po
  slopu vezme celé a **rodiči už ho nepustí**. Tah doleva se tedy dnes tiše zahazuje a
  seznamová varianta by se k němu nedostala.
- **Dvě cesty, ani jedna zdarma:**
  1. **Po řádcích** — povolit bublině i negativní posun a odkrývat čas u té jedné zprávy.
     Malá změna, ale není to, co dělá Messages, a u jedné bubliny to vypadá spíš jako druhá
     polovina swipu na odpověď.
  2. **Nad seznamem** — bublina nesmí tah doleva spolknout. `draggable` to neumí vyjádřit
     (po slopu je směrově agnostický), takže by to chtělo **vlastní detektor, který po slopu
     rozhodne jednou za gesto** — přesně ten vzor, který už v projektu je v prohlížeči médií
     (`CLAUDE.md`: „`detectTransformGestures` s pagerem nejde… vlastní detektor"). Doprava =
     odpověď (konzumuje), doleva = nekonzumuje a nechá to seznamu.
- **Formátovač času `asTimeOfDay()` je s odstraněním času smazaný** — byl to mrtvý kód. S tímhle
  bodem se musí vrátit (je to pět řádek nad `kotlinx.datetime`).
- **Čas musí zůstat dostupný i bez gesta.** Čtečka obrazovky se tahem nikam nedostane, takže
  patří do nabídky nad zprávou — váže se to na „Informace o zprávě", která je v seznamu
  u sekce B).

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

**Chyby** — opraveno, poučení v `CLAUDE.md` („Hlášení přečtení a indexy").

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
