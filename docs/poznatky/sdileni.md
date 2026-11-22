# Sdílení do aplikace

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Sdílení do aplikace

Plume je v systémové nabídce „Sdílet" (Android): text, odkaz, jeden i víc souborů. Otevře se
obyčejný seznam chatů a nahoře visí čip „Sdílíte: …" (viz „Čip místo výběru chatu" níž); v otevřené
konverzaci se obsah ťuknutím na „Vložit sem" ukotví nad vstupním polem — nic se neposílá samo,
k souborům se píše popisek a uživatel vidí, co odejde.

- **`MainActivity` je `singleTask`, ne `singleTop`.** Výběr z nabídky „Sdílet" spouští activity
  v úloze VOLAJÍCÍ aplikace; se `singleTop` tak vznikla druhá instance Plume v úloze Fotek
  — dvě okna Plume v přehledu, každé s vlastní navigací. `singleTask` doručí sdílení do
  existující úlohy přes `onNewIntent` a zpět z kořene vrátí uživatele do aplikace, ze které
  sdílel. Ověřeno `dumpsys activity activities`: jedna úloha Plume před sdílením i po něm.
- **Soubory se kopírují HNED** (`ShareIntake`, stejná příprava jako výběr příloh — fotka se
  zmenší, z videa se přečte náhled). Oprávnění k `content://` URI je dočasné; až uživatel vybere
  chat, nemuselo by platit. Kopíruje se v `appScope`, ne v rozsahu aktivity.
- **Požadavek je STAV** (`ShareRequests`), jako `ChatOpenRequests`: sdílet jde i do aplikace,
  která neběží nebo visí na přihlášení. Čip se ukáže hned, soubory dobíhají
  (`attachments == null` = „Připravuji soubory…") a konverzace si obsah vyzvedne, až je hotový.
- **Fotky a videa jdou jako média, jen když jsou médii všechny.** Telegram fotky se soubory do
  alba nesloží; smíšené sdílení (fotka + PDF) odejde celé jako soubory, ať se nic neztratí.
- **Sdílený text rozepsaný koncept DOPLNÍ, nepřepíše**, a vkládá se až po obnovení konceptu
  z TDLib (`draftRestored`) — koncept jde jen do prázdného pole, takže sdílení před ním by ho
  vytlačilo.
- **Koncept a sdílený text přijdou do pole HNED PO SOBĚ, takže obrazovka nuluje jen to vložení,
  které provedla** (`consumeInputFill(fill)`, porovnává instanci). Bezpodmínečné vynulování
  smazalo sdílený text dřív, než ho obrazovka uviděla: pole zůstalo prázdné, ViewModel ho přitom
  vedl jako koncept a uložil do TDLib — a další sdílení ho připojilo podruhé. Vyvolat se to na
  emulátoru nepodařilo, oprava stojí na čtení kódu; ověřené je, že koncept + sdílený odkaz vyjdou
  jako dva řádky.
- **U sdílení TEXTU se `ClipData` nečte** (`ShareIntake.streams`). Chrome ke sdílenému odkazu
  přikládá do `ClipData` obrázek náhledu stránky (`text/uri-list`, favicon nebo `og:image`) jen
  kvůli náhledu v systémové nabídce. Dokud se `ClipData` bral jako záloha za `EXTRA_STREAM`, byl
  z odkazu „1 soubor": výběr chatu ho tak hlásil, Direct Share otevřel „Komu: …" s logem webu
  a odešla fotka s odkazem jako popiskem, bez náhledu odkazu. Skutečný soubor nese `EXTRA_STREAM`
  (systém ho do `ClipData` jen zrcadlí) a typ intentu pak není `text/*`. **`adb shell am start`
  `ClipData` nastavit neumí**, takže sdílení ze skutečného Chrome se ověřuje v Chrome (menu →
  Share…); v logu je vidět `clip={text/uri-list … {U(content)}}` u `START … act=SEND`.
- **Odchod obrazovky „Komu: …" bez odeslání sdílení zahodí**, a to v `onDispose` obrazovky, ne
  na šipce. Systémové gesto zpět jde přes `NavDisplay`, ne přes obrazovku; se zahazováním jen na
  šipce zůstalo po gestu sdílení viset. (Dřív totéž platilo pro výběr chatu `ShareRoute`, který
  nahradil čip — ten se zahazuje jen ✕.)
- **Opakované sdílení** (nové sdílení, zatímco čip nebo obrazovka „Komu: …" ještě visí) dřív
  obrazovku zavřelo a nové sdílení se neukázalo vůbec. Příčiny byly tři a každá stačila sama
  (popsané ještě s výběrem chatu, který dnes nahradil čip):
  - **Mezistav bez chatu.** Obsah vznikal `start` a chat dostával až dalším voláním
    (`confirm`). Otevřená obrazovka „Komu: …" mezitím viděla obsah bez chatu, vzala
    ho jako „obsah zmizel" a odešla přes `moveTaskToBack`. **Cíl se proto zakládá spolu
    s obsahem** (`ShareRequests.start(target = ShareTarget(…))`) — na Androidu i v iOS rozšíření.
  - **Odchod zahazoval cizí sdílení.** Kvůli novému sdílení shell starou obrazovku nahrazuje
    (`popToRoot`), a její `onDispose` volal `abandon()` nad AKTUÁLNÍM obsahem — tedy nad tím
    novým. **`abandon(id)` zahazuje jen sdílení, které obrazovka ukazovala**; „Komu: …" si
    pamatuje to svoje (`ShareToChatState.contentId`). Stejně ✕ na čipu (`discard(id)`).
  - **Nové sdílení do TÉHOŽ chatu se nepropsalo.** Shell obrazovku nechává (tatáž routa)
    a ViewModel přebíral přílohy jen jednou (`if (attachments == null)`). Teď se s novým `id`
    přenastaví celý stav a obsah obrazovky se skládá znovu (`key(state.contentId)`), jinak by
    staré snímky z carouselu „odcházely" animací a galerie držela neplatné zaškrtnutí.
  - Obrazovka „Komu: …" proto odchází sama (`gone`) už **jen když obsah zmizel úplně**; sdílení
    jinam ji nechá být, nahradí ji shell.
  - **Ověřeno na emulátoru** dvojicí `am start SEND` po sobě: tentýž chat (obrazovka zůstane,
    ukáže nový snímek), jiný chat (Uložené zprávy), sdílení bez chatu nad „Komu: …" (tehdy výběr
    chatu s novým obsahem, dnes čip), Direct Share nad výběrem chatu; pak odeslání a ✕ s návratem.
  - **Na iOS simulátoru** sdílení po sobě do Notifikátora, Uložených zpráv a znovu Notifikátora
    (pokaždé rovnou správný chat s novou fotkou a popiskem), ze studeného startu a odeslání.
    **Ověřuje se zápisem do inboxu App Group, ne ťukáním v rozšíření**: složka
    `share/inbox/<čas>-<uuid>/` s obrázkem a `manifest.json` (`{"chatId", "text", "items":
    [{"file", "kind": "image"}]}`) zapsaným POSLEDNÍ, pak přepnout do jiné aplikace a Plume
    spustit (`simctl launch`). Inbox se čte při návratu do popředí — `simctl openurl` nad běžící
    aplikací ukáže jen systémový dialog a do pozadí ji nepošle, takže se nic nestane.
  - **Seznam chatů v rozšíření se po otevření přeskládá** (dočte naposledy použité). Ťuknutí
    podle souřadnic ze snímku tak trefilo jiný chat — skutečného člověka; fotka naštěstí jen
    čekala nad polem. Proto zápis do inboxu, ne ťukání.
- **Uložené zprávy v hlavičce „Komu: …" mají vlastní název**, ne jméno účtu, pod kterým je
  vede TDLib — tatáž past jako u `chatTitle` jinde.
- **Obrazovka „Komu: …" má ✕, ne šipku zpět, a ✕ i gesto zpět vrací do aplikace, ze které se
  sdílelo** (`rememberReturnToCaller` → `moveTaskToBack`, `closeShare` v `AppShell`).
  `singleTask` drží Plume ve vlastní úloze, takže pod ní je právě aplikace, ze které se sdílelo.
  Obrazovka se napřed odebere ze zásobníku, aby příští otevření Plume nezačínalo v ní. **Pro čip
  to NEPLATÍ**: obsah se zpět neztrácí, takže zpět je tam obyčejná navigace po Plume.
- **Direct Share (chaty přímo v nabídce „Sdílet") stojí na zkratkách konverzací**, týchž, které
  zakládají notifikace (`conversationShortcut`). Podmínky jsou tři a chybějící se neprojeví
  chybou, jen se nic neukáže: kategorie `SHARE_CATEGORY` u zkratky, `share-target` se stejnou
  kategorií v `res/xml/shortcuts.xml` a `meta-data android.app.shortcuts` na `MainActivity`.
- **Pořadí chatů si skládá systém** z toho, jak často a nedávno aplikace zkratku publikuje
  (`pushDynamicShortcut` se počítá jako použití). Proto se hlásí KAŽDÉ odeslání i přeposlání
  (`ShareTargets.reportUsed`, v rozsahu aplikace, název a profilovku si dohledá samo).
  Zkratky z dřívějška kategorii nemají a do nabídky se dostanou až při příští notifikaci nebo
  odeslání — ověřuje se `dumpsys shortcut` (`categories={…SHARE_TARGET}`).
- **Kontakt v náhledu screenshotu NENÍ systémový výběr, ale Android System Intelligence**
  (`com.google.android.as`, chytré akce screenshotu). Intent spouští on, s typem `*`/`*`,
  `image/png` jen v popisu `ClipData` a **bez `FLAG_GRANT_READ_URI_PERMISSION`** — ke snímku
  sám přístup nemá, takže nemá co předat. Plume pak dostane `SecurityException: … has no access
  to content://media/…` a obejít to nejde (oprávnění ke knihovně fotek kvůli tomu nechceme).
  Proto: typ se bere i z typu intentu a popisu `ClipData`, když ho `MediaProvider` bez přístupu
  nevydá (`declaredTypes`) — jinak by screenshot otevřel konverzaci místo „Komu: …" —, selhání
  na oprávnění se počítá zvlášť (`SharedContent.denied`) a obrazovka „Komu: …" místo obecné
  chyby radí vybrat snímek v galerii pod polem, kde je nejnovější (`AccessDenied`). Zjištěno
  z logu Foldu (`START … from uid … (com.google.android.as)`, `flg=0x10000000`). Na emulátoru
  se to navodí `am start -a SEND -t image/png --eu android.intent.extra.STREAM
  content://media/external/images/media/<id>` se zkratkou chatu — `adb` přístup taky nepředá.
- **`EXTRA_SUBJECT` je náhrada textu jen bez souborů.** Screenshot ho nese jako „Screenshot
  (datum)" a končil v popisku každého sdíleného snímku.
- **Sdílení přes zkratku nese `EXTRA_SHORTCUT_ID`** a výběr chatu přeskočí: fotka a video jdou
  na potvrzovací obrazovku (viz „Direct Share" níž), zbytek rovnou do konverzace
  (`ShareIntake`: `choose(chatId)` + `ChatOpenRequests`).
- **Chat bez fotky má ve zkratce iniciálu** (`initialIcon`), jinak ukazoval systém prázdné
  kolečko — ve sdílení i ve velkém kruhu notifikace. Barvy ze systémové palety Material You
  (`system_accent1_100/900`): zkratka se kreslí mimo aplikaci, téma aplikace tam není. Paleta je
  od Androidu 12, na starším zůstane ikona prázdná.
- **Co se sdílí, je PLOCHA, ne řádek textu na pozadí** — dřív karta ve výběru chatu, dnes čip.
  Role je `tertiaryContainer`: je to cizí vložený obsah, tedy totéž co citace nad vstupním polem
  nebo popis webu u náhledu odkazu. **Náhled nebo ikona říká druh** (první fotka nebo náhled
  videa, sponka u souborů, řetěz u odkazu, stránka u textu), a dokud se soubory připravují, je
  místo nich indikátor — „Připravuji soubory…" je průběh, ne popis.

### Cizí URI nesmí ukazovat do vlastních dat

Sdílení, klávesnice (vložení obrázku), schránka i přetažení čtou soubor **pod identitou Plume**.
Cizí aplikace bez jakéhokoli oprávnění tak mohla podstrčit cestu k soukromým datům Plume
(`file:///data/user/0/eu.tintera.plume/…`, `content://eu.tintera.plume.documents/…`) a přes
`EXTRA_SHORTCUT_ID` předvybrat svůj chat. Odeslat musel uživatel, ale u `file://` viděl jen
obecné „soubor". Ve hře byla stažená média (i z tajných chatů), databáze TDLib a nastavení.

- **`sharedStreamAllowed`** (`core/share/SharedStreams.kt`, testy `SharedStreamsTest`),
  na Androidu `foreignUriAllowed` v `OwnShares.kt` — volá ho `ShareIntake`, `drop`
  i společné `prepare` klávesnice a schránky v `KeyboardContent.android.kt`.
- **Jen `content://`.** `file://` jiná aplikace od Androidu 7 sdílet nesmí
  (`FileUriExposedException`), takže zbyl jen útok.
- **Vlastní poskytovatel jen pro to, co Plume sám právě vydal** (`OwnShares`, v paměti
  procesu): „Sdílet" v prohlížeči (`MediaSharer.share`, kopie v `cache/shared`) a kopírování
  obrázku do schránky (`MediaSharer.copy`). Uživatel si ve výběru aplikací smí vybrat zase
  Plume, nebo obrázek vložit zpátky do pole zprávy.
- **Vlastní = podle `resolveContentProvider`** (balíček nebo uid), ne podle jména autority —
  autorita se mění s `applicationId`.
- **Autorita se posuzuje bez čísla uživatele** (`0@eu.tintera.plume.documents`):
  `ContentResolver` takovou adresu otevře u téhož poskytovatele a kontrolu by obešla.
- **Odmítnuté soubory se počítají mezi nepovedené** (`failed` v `ShareRequests.prepared`);
  když jiné nejsou, sdílení vůbec nevznikne.
- **Kontrola cesty deskriptoru (`/proc/self/fd`) potřeba není**: cizí poskytovatel běží pod
  svým uid a soubor z dat Plume otevřít neumí, a vlastní poskytovatele odmítá kontrola autority.
- **Ověřeno na emulátoru** (Plume_A11, Android 11, debug):
  - stará verze přijala `am start … -a android.intent.action.SEND --eu android.intent.extra.STREAM
    file:///data/user/0/eu.tintera.plume/shared_prefs/…` — čip „Sdílíte: soubor";
  - nová odmítne `file://`, `content://eu.tintera.plume.documents/…` i
    `content://0@eu.tintera.plume.documents/…` (log `Sdílení odmítnuto`, žádný čip);
  - fotku z galerie (`content://media/…`) filtr pustí. Přečíst ji nešlo, protože `adb shell`
    nemá jak předat přístup k úložišti médií, ale to by stejně dopadlo i bez opravy.
  - **Neověřeno:** sdílení z prohlížeče médií Plume zpátky do Plume, vložení zkopírovaného
    obrázku, přetažení a klávesnice. Na nich stojí jen `OwnShares` a společná funkce.

### Čip místo výběru chatu

Sdílení BEZ předem vybraného chatu (nabídka „Sdílet", odkaz `t.me/share`, „Poslat do chatu"
u sady samolepek) neotevře zvláštní výběr, ale **obyčejnou aplikaci se seznamem chatů** —
s hledáním, archivem i složkami. Nahoře visí čip „Sdílíte: …" (`SharedContentStrip`
v `features/share`, náhled, popis, ✕), který se s uživatelem nese po aplikaci. Výběr chatu
(`ShareRoute`, `ShareScreen`, `ShareViewModel`) je pryč — nic jiného ho nepoužívalo.

- **Do konverzace se obsah dostane AŽ ťuknutím na „Vložit sem"** (`ShareRequests.choose`, dál
  stará cesta přes `deliveries`). Samotné otevření chatu nic nevkládá — uživatel se má moct
  porozhlédnout, než se rozhodne. Mimo konverzaci čip jen připomíná, co se sdílí.
- **Pruh zabírá vlastní místo, nic nepřekrývá.** Leží pod stavovou lištou a obrazovkám pod ním
  shell zkonzumuje horní inset (`consumeWindowInsets(safeDrawing.only(Top))` na obalu
  `NavDisplay`). Dole by se pral s plovoucí lištou složek a se vstupním polem, přes hlavičku by
  zakryl pilulku s hledáním. Ve dvoupanelu je jeden přes celou šířku (se stropem
  `ContentWidth.Sheet`), ne dva v obou panelech.
- **Místo pod stavovou lištou (`Spacer`) drží pruh po celou dobu, kdy je ve stromu, i během
  animace** (`MutableTransitionState`, `chipPresent = currentState || targetState`). Jinak by
  obrazovka pod pruhem na začátku nebo na konci animace poskočila o výšku stavové lišty:
  inset by se buď konzumoval, nebo ne, a pruh by byl teprve nulový.
- **Kde čip visí a kam vkládá, jsou čisté funkce** (`showsShareChip`, `shareInsertChat`
  v `ChatsStack.kt`, testy `ShareChipPlacementTest`): jen nad položkou panelu (seznam, archiv,
  konverzace, profil), ne nad obrazovkou přes celou plochu (prohlížeč, „Komu: …") ani
  v Nastavení. „Vložit sem" jen do konverzace, která je VIDĚT — na telefonu navrchu, vedle
  seznamu i pod profilem; seznam navrchu (archiv nad konverzací hlavního seznamu) znamená
  placeholder.
- **Seznam chatů se ukáže JEDNOU za sdílení** (`ShareRequests.arrival` → `showChatList`,
  `arrivalShown`). S klíčem efektu jen podle id obsahu by obnova aktivity (otočení, rozložení
  Foldu) zavřela, co uživatel mezitím otevřel. Sdílení obnovené po zabití procesu se neohlašuje
  vůbec — zásobník se obnovil s ním.
- **✕ obsah zahodí i s kopiemi souborů** (`discard(id)`, jen to sdílení, které čip ukazoval).
  **Zpět je obyčejná navigace**, obsah se tím neztrácí.
- **Opakované sdílení čip nahradí** a kopie souborů nahrazeného obsahu smaže — nikdo je už
  nepošle. Totéž, když příprava souborů doběhne pro sdílení, které mezitím zmizelo.
- **Čip nezmizí hned po „Vložit sem"** (`carried` zůstává i s vybraným chatem), až když si obsah
  konverzace vyzvedne. Vkládat jde jen hotový obsah (soubory připravené), jinak by čip zmizel
  a obsah by tiše čekal.
- **Vloží ho jen jedna konverzace** (`consume` vrací `true` jen prvnímu). Tentýž chat může být
  v zásobníku dvakrát (zmínka tam a zpátky) a obě konverzace odebírají `deliveries` téhož chatu
  — `StateFlow` doručí oběma a dřív by se obsah vložil do obou.
- **Přežije zabití procesu, nejdéle 24 hodin** (`ShareStore`, `FileShareStore`, testy
  v `ShareRequestsTest`). Manifest `shared-content.json` (kotlinx.serialization) leží v
  `cache/outgoing` vedle kopií, které `ShareIntake` dělá hned při příjmu — obdoba inboxu iOS
  rozšíření. Načte se synchronně při vzniku `ShareRequests`, zapisuje se na pozadí po jednom
  (`limitedParallelism(1)`) a **stav se čte až při zápisu**, takže pozdější zápis nese poslední
  stav, i když se změny potkaly ve dvou vláknech (příprava souborů běží na `Dispatchers.Default`).
  - **Ukládá se jen čip s připravenými soubory.** Rozpracovaná příprava se dokončit nedá
    (oprávnění k `content://` zabití procesu nepřežije) a sdílení s chatem vybraným předem
    (Direct Share, iOS) čeká na obrazovce, kterou obnova stejně zavře.
  - **Obnovené sdílení je vždycky zase na čipu**, i když ho uživatel těsně před zabitím vkládal
    — cíl se neukládá. **Spotřebované (`consume`) se neobnoví**: vyzvednutí manifest smaže,
    soubory ale nechá (převzala je konverzace).
  - **Po 24 hodinách zmizí i s kopiemi** — při startu (obnova) a při návratu aplikace do popředí
    (`dropExpired` z `LifecycleStartEffect` v shellu); `delay` by v hlubokém spánku neběžel.
  - **Ztracená kopie** (cache smí systém vyčistit) se při obnově počítá jako soubor, který
    nevyšel; chybějící náhled videa video nezahodí. Poškozený manifest se smaže.
  - **Maže se jen uvnitř `cache/outgoing`** (podsložka každé kopie), ať chyba v záznamu nestojí
    cizí soubor.
  - **iOS a desktop úložiště nemají** (`ShareStore` jen v Koin modulu Androidu): iOS rozšíření
    vybírá chat samo, čip tam nesou jen sdílení zevnitř aplikace (odkaz `t.me/share`, sada
    samolepek), a ta žijí v paměti.
- **Ověřeno na emulátoru (Pixel Fold, rozložený i složený)**: text i fotka ze `am start SEND`,
  čip nad seznamem i konverzací ve dvoupanelu a na telefonu, otevření chatu nic nevloží,
  „Vložit sem" a odeslání do chatu s botem, ✕ smaže manifest i kopii, dvě sdílení po sobě
  (druhé nahradí první a seznam se srovná), zpět z konverzace čip nechá, `force-stop`
  i `am kill` na pozadí čip obnoví (i fotku, která pak odešla), tři cykly rozložení a složení
  bez pádu, Direct Share („Komu: …", ✕ vrací do launcheru) beze změny a bez manifestu.
  **Neověřeno:** vypršení po 24 h (jen testy), sdílení víc souborů, tmavý motiv a paleta se
  zvýšeným kontrastem (tlačítko `tertiary` na `tertiaryContainer`).

### Direct Share: obrazovka „Komu: …"

Sdílení fotky nebo videa na konkrétní chat ze systémové nabídky (Direct Share) neotevře rovnou
konverzaci, ale **potvrzovací obrazovku** (`ShareToChatRoute`, `ShareToChatScreen`): hlavička
„Komu: …" s tváří chatu, snímek velký uprostřed, popisek a odeslání. Vzor je Google Zprávy
sdílející z Fotek.

- **Jen fotky a videa.** Soubory, text a odkaz dál otevřou konverzaci a čekají nad polem
  (`ShareIntake`): u odkazu není co ukazovat a pole na popisek by k němu byl krok navíc.
- **Rozhoduje se z MIME typů, ne z připravených příloh** — příprava běží na pozadí a obrazovka
  se má ukázat hned. Dokud soubory nejsou hotové, je místo snímku „Připravuji soubory…".
- **Odesílá obrazovka, ne konverzace.** Sdílení je proto ve stavu `awaitingConfirmation`
  (`ShareRequests.confirm`) a `deliveries` ho vynechává — jinak by si ho konverzace vyzvedla
  nad vstupní pole a odešlo by dvakrát.
- **Po odeslání se otevře konverzace**, jako u přeposílání: je vidět, že zpráva dorazila.
  Odchod bez odeslání sdílení zahodí a vrátí do aplikace, ze které se sdílelo (tentýž
  `closeShare` jako u výběru chatu).
- **Past: `consume` zhasne `incoming`, takže vlastní úspěch vypadá jako „obsah zmizel".** Odběr,
  který z prázdného `incoming` dělá „zavřít", proto musí vynechat vlastní odesílání — jinak
  aplikace po odeslání skočí na plochu místo do chatu. Ověřeno na emulátoru: bez toho se to
  stalo pokaždé. Stav se navíc nastavuje PŘED `consume`.
- **Spoiler, mizení i „jako soubor" nabízí tentýž `AttachmentModeSheet`** jako nad vstupním
  polem — jedno místo, kde se o způsobu odeslání rozhoduje, a platí i tytéž meze
  (`selfDestructAllowed`: mizení jen u jediné fotky v soukromém chatu, v Uložených zprávách ne).
- **Snímky kreslí TENTÝŽ `MediaCarousel` jako prohlížeč médií**: sousedé vykukují u okrajů,
  rozostřené pozadí jede s tahem, karta se vepisuje přes `MediaPageLayout` mezi hlavičku a pole
  na popisek (carousel jde přes celou obrazovku, `reservedBottom` je vzdálenost horní hrany
  řádku s polem od spodku, takže s klávesnicí se karta zmenší). Obrazovka dodává jen obsah
  karty (snímek, ✕, volba režimu) a pozadí stránky. **Dřív tu byl vlastní pager přes celou
  šířku, vlastní rámeček karty a vlastní prolínané pozadí** — sdílel se jen styl rozostření,
  a jakmile se doladil prohlížeč (sousedé, strop šířky), vypadalo sdílení jako jiná aplikace.
- **Karta má rozměry SNÍMKU, ne plochy** — zaoblení i ✕ patří fotce. To teď zařizuje
  `MediaPageLayout` z poměru stran přílohy.
- **Past: `Avatar(backdrop = …)` s barvou mimo schéma.** `contentColorFor` zná jen role, u vlastní
  barvy vrátí `Unspecified` — a záložka Uložených zpráv se nakreslila černá na černém pozadí.
  Kde roli nezná, rozhoduje `LocalContentColor`.
- **Ťuknutí na snímek přepne na celou obrazovku, jako v prohlížeči médií**: hlavička, pole
  i galerie zmizí, fotka jde přiblížit (štípnutí, dvojité ťuknutí), video přehrát; zpátky vede
  ťuknutí, tah dolů nebo gesto zpět. **Stránky jsou tytéž jako v prohlížeči** —
  `ZoomableMediaPage` a obecná `VideoPage` (zdroj souboru, náhled a ovládání navíc jsou
  parametry) — jen nakrmené souborem na disku místo zprávy. Dokud byla gesta i přehrávání
  napevno spojené se zprávou, karta ve sdílení byla jen obrázek a zkontrolovat, co odchází,
  nešlo. Na celé obrazovce je spoiler odkrytý (je to volba odesílatele, ne cizí obsah). Tytéž
  stránky nese i náhled přílohy nad vstupním polem (`StagedAttachmentPreview`).
- **Galerie na celé obrazovce není složená** — `SurfaceView` leží nad vším a snímek by zakryla.
  Po návratu se session pickeru otevře znovu a zaškrtnutí obnoví z příloh jako po každém zavření.
- **Chrome mizí jen průhledností (`AnimatedVisibility` s `fadeOut`) a jeho rozměry se po dobu
  celé obrazovky NEměří znovu** (`headerHeight`, `captionTop`). Karta se při návratu vepíše
  tam, odkud odjela; s přeměřením by v půlce přechodu poskočila.
- **Víc snímků je `HorizontalPager` s „N z M" v hlavičce**, ne mřížka: album se stejně posílá celé
  a pruh miniatur by zdvojoval to, co umí swipe. ✕ ubírá snímek; odebrání posledního obrazovku
  zavře, protože není co poslat.
- **Pod popiskem je galerie, přes kterou jde dávku doplnit** (`GalleryPanel` + tentýž
  `EmbeddedMediaPicker` jako sheet v konverzaci), podle vzoru Google Zpráv. **Sbalená jen
  vykukuje** a rozvržení s ní počítá (snímek nad ní je celý vidět); vybírat v ní jde, posouvat
  ne. **Rozbalená vyjede PŘES snímek i pole až pod hlavičku**, nic nezmenšuje — první verze
  panel natahovala do sloupce a snímek se smrskl na známku. V hlavičce je pak místo ✕ šipka
  dolů, která panel sbalí; gesto zpět taky nejdřív sbalí (`NavigationBackHandler`).
- **Galerie má DVĚ ukotvené polohy a zavřít nejde** — dole a nahoře pod hlavičkou. „Hotovo"
  v pickeru ji jen vrátí dolů. Jediné, čemu ustoupí, je klávesnice při psaní popisku (zabírá
  totéž místo); po jejím schování je galerie zpátky sama, takže ikona „přidat fotky" v poli
  tam nemá co dělat.
- **Kde vestavěná galerie není** (Android pod 14, nebo bez SDK Extensions 15), je v poli na
  popisek ikona, která otevře **systémový výběr** (`rememberAttachmentPicker`) — jinak by šlo
  poslat jen to, co přišlo sdílením. Vybrané jdou za sdílené stejně jako z galerie. Systémový
  výběr fotky vydá pod vlastními URI, takže tu nejde poznat, že uživatel vybral sdílenou fotku
  podruhé. Ověřeno na emulátoru s Androidem 14 (Extensions 7): ikona otevře
  `PhotoPickerActivity`, vybraná fotka přibude a carousel na ni přejede; na Foldu s galerií
  ikona není.
- **Úchyt je úchyt, ne tlačítko.** Panel jede s prstem (výška sleduje tah, sevřená mezi obě
  polohy) a po puštění dosedne podle švihnutí, jinak podle toho, kam dojel (přes půl cesty
  nahoru). Ťuknutí prstem nedělá nic — první verze úchytem jen přepínala a uživatel správně
  namítl, že úchyt, který nejde táhnout, je tlačítko převlečené za něco jiného. Čtečka obrazovky
  tahat neumí, proto má úchyt rozbalení a sbalení jako akce (`semantics { expand / collapse }`).
- **Tah se chytá jen na úchytu**, ne na mřížce — tu kreslí cizí okno (`SurfaceView`), které si
  dotyky bere samo. Obsah pickeru za panelem při tahu a animaci o kus zaostává (picker se
  překresluje po `notifyResized`), po dosednutí sedí.
- **Tah se na emulátoru ověřuje `input motionevent DOWN/MOVE`, snímek, pak `UP`** — `input swipe`
  je hotové gesto a mezistav s panelem pod prstem nezachytí.
- **Dobrané snímky jdou ZA sdílené** a pager na ně přejede. Odznačení v galerii obrazovku
  nezavírá ani u posledního snímku (je to oprava výběru), ✕ na posledním snímku ano. Po ✕ se
  galerie otevře znovu (`removals` jako klíč): zaškrtnutí si obnovuje jen při otevření.
- **Sdílená fotka v galerii zaškrtnutá NENÍ a být nemůže.** MediaProvider předvýběr odmítne
  („MediaQuery:PreSelection: … package does not have permission for the uri"): předvybrat jde
  jen fotku, ke které aplikace dostala oprávnění PŘES PICKER, a sdílená přišla s oprávněním
  k URI jiné aplikace. Obejít by to šlo jen oprávněním ke čtení celé knihovny fotek, a to kvůli
  jednomu zaškrtnutí nestojí za to (a Play ho hlídá). Galerie ji ale pozná (`SharedContent.sources`,
  `pickerUriFor`): picker drží místní fotky jako `content://media/picker/<uživatel>/
  com.android.providers.media.photopicker/media/<id MediaStore>`, takže MediaStore URI i URI
  zabalené v cestě Fotek Google jde převést. Ťuknutí na sdílenou fotku ji proto nepřidá podruhé
  a odznačení ji z dávky odebere.
- **Odebraný snímek odchází animací, ne skokem** (`ShownAttachments`, `SharedPhoto(leaving)`).
  Pager odebrání stránky animovat neumí — chybějící stránka zmizí a soused naskočí na její
  místo. Odebraný snímek (odznačený v galerii i ✕) proto v carouselu chvíli zůstane: karta se
  stáhne a zprůhlední, a byl-li zobrazený, carousel dojede na souseda (další, u posledního
  předchozí); teprve pak stránka zmizí. Pager drží pozici podle klíče, takže odebrání stránky,
  ze které už odjel, s ním nepohne. Stránky tím nejsou 1:1 s přílohami — index do ViewModelu
  (✕, režim) se hledá podle cesty. Znovu zaškrtnutý snímek se během odchodu vrátí.
- **Zpomalené animace pro snímky ukládej mimo `/sdcard`** (`/data/local/tmp`): PNG v `/sdcard`
  MediaStore zaindexuje a objeví se v testované galerii.
- **Hláška (snackbar) stojí nad polem**, ne u spodku — galerie je `SurfaceView` nad vším.
- **Ověřeno na emulátoru** (jedna fotka i dvě z Fotek): obrazovka, spoiler, mizení v chatu s botem
  a jeho nepřítomnost v Uložených zprávách, popisek, odeslání s otevřením konverzace, ✕ s návratem
  do zdrojové aplikace. Galerie (profil Pixel 10 Pro Fold, API 37.2, rozložený i složený): výběr
  ve sbalené, tah s panelem pod prstem a dosednutí do obou poloh, tah dolů ze spodní polohy
  galerii nezavře, ťuknutí na úchyt nic nedělá, scroll v rozbalené, šipka i gesto zpět,
  znovuotevření bez zdvojení a odeslání alba dvou snímků s popiskem. **Přes `adb` jde poslat jen JEDNA příloha** — `EXTRA_STREAM` jako seznam
  `am start` neumí (`--eu` je jedna hodnota) a `--grant-read-uri-permission` platí pro `data`
  URI, ne pro extras: bez `-d <totéž URI>` aplikace soubor nepřečte, `getType` vrátí `null`
  a sdílení se zachová jako u dokumentu. Víc fotek se musí proklikat z Fotek.

- **Fotku jde před odesláním upravit** (tužka vlevo nahoře na kartě) — týž editor a tatáž
  pravidla jako u příloh v konverzaci, viz „Úprava fotky před odesláním" v `konverzace-psani.md`
  (tam i proč se během úpravy schovává galerie a jak sdílení ověřit z Google Fotek).

### Import chatu z jiné aplikace

Export chatu z WhatsAppu (Android: „Exportovat chat" sdílí `.txt` a média jako víc souborů
najednou) jde do Plume jako každé jiné sdílení: čip „Sdílíte…", otevřít chat, „Vložit sem".
Teprve konverzace (`ConversationImport`) se podívá, jestli mezi soubory není `.txt`, který
Telegram pozná jako export, a místo příloh nabídne import.

- **Export pozná server, ne my.** Prvních sto řádků souboru jde do `getMessageFileType`; formát
  exportu se liší jazykem i verzí WhatsAppu a parsovat ho sami by znamenalo honit každou změnu.
  Neznámý soubor (`messageFileTypeUnknown`) jde obvyklou cestou jako příloha. Ostatní sdílené
  soubory jsou přílohy exportu, server je páruje podle jména („IMG-…jpg (file attached)").
- **Strop výběru příloh (deset) by import ořízl o média**, proto se u sdílení víc souborů, mezi
  kterými je `.txt`, bere až 300 (`EXPORT_FILE_LIMIT` v `ShareIntake.android.kt`). Jestli je to
  opravdu export, se tam ještě neví.
- **Druh exportu se hlídá PŘED dialogem.** Soukromý export (`messageFileTypePrivate`) jde jen do
  soukromého chatu, skupinový (`messageFileTypeGroup`) jen do skupiny. `getMessageImportConfirmationText`
  to nekontroluje — větu k potvrzení vydá do každého chatu, kde smíme importovat, a soukromý
  export do skupiny odmítl až samotný `importMessages` (`IMPORT_PEER_TYPE_INVALID`), tedy po
  potvrzení a nahrání. Repository proto porovná druh s typem chatu a nehodí-li se, dialog řekne
  proč a nabídne export „Vložit jako soubor" — tak, jak by šel bez rozpoznání.
- **Věta k potvrzení je od serveru a ukazuje se** („Imported messages will appear at the time…").
  Není to chybová hláška, ale text, který má uživatel před importem vidět (jako podmínky
  služby); je v jazyce balíčku Telegramu, tedy zatím anglicky.
- **Zpráva z importu má za odesílatele zástupný účet „Imported Message"** se šedou tváří; kdo ji
  napsal, nese jen `Message.importInfo.senderName` (holé jméno z exportu). Bublina proto ukáže
  to jméno a místo tváře zástupného účtu iniciály.
- **Server po importu hlásí nepřečtené zmínky** (odznak „@ 2" u dvou importovaných zpráv
  v testovací skupině) — jeho rozhodnutí, aplikace s tím nic nedělá.
- **Neumí:** export jako `.zip` (WhatsApp na iOS), import do chatu jinak než ze sdílení.
  iOS rozšíření sdílení předává soubory stejnou cestou, ověřené tam ale nic není.
- **Ověřeno na emulátoru** podstrčeným sdílením (víc souborů `adb` sdílet neumí, viz výš):
  skupinový export s fotkou naimportovaný do vlastní testovací skupiny (zprávy ve „Včera 18:02",
  fotka spárovaná, jména z exportu), týž export v soukromém chatu (věta „Skupinový chat jde
  naimportovat jen do skupiny…" a vložení jako dva soubory nad pole), soukromý export v chatu
  s botem (bez věty od serveru). Skutečný export z WhatsAppu ověřený není.

### iOS: rozšíření sdílení

Na iOS je sdílení **rozšíření** (`iosApp/ShareExtension`, SwiftUI): list „Sdílet do" s chaty
přímo v nabídce sdílení, jako Telegram nebo WhatsApp. Po výběru chatu rozšíření otevře Plume
a obsah čeká nad vstupním polem, stejně jako na Androidu.

- **Rozšíření je samostatný proces s vlastním limitem paměti; TDLib v něm neběží** a databázi
  aplikace nevidí. Obě strany si předávají soubory přes kontejner App Group
  (`group.eu.tintera.plume$(TEAM_ID)`, klíč `PlumeAppGroup` v Info.plist obou): aplikace píše
  snímek chatů (`share/targets.json` + profilovky, `IosShareSnapshot`), rozšíření píše sdílení
  (`share/inbox/<čas>-<uuid>/`, `manifest.json` POSLEDNÍ). Formát drží `ShareTargets.ios.kt`
  a `ShareModels.swift` — **změna jedné strany musí jít i do druhé**.
- **Snímek chatů se po odhlášení maže** (`holder.replaced`), i s nevyzvednutým inboxem —
  rozšíření by jinak nabízelo chaty předchozího účtu. Nahoře jsou naposledy použité
  (`ShareTargets.reportUsed`, obdoba Direct Share).
- **Aplikace si inbox projde při každé aktivaci** (`IosShareInbox` nad `AppForeground`), ne
  jen při otevření přes URL: kdyby otevření z rozšíření selhalo, sdílení počká na nejbližší
  spuštění. Soubory se přesunou do `outgoing` a připraví stejně jako výběr příloh
  (`preparePhotoData`, `prepareVideoAt`, `prepareDocument`); složka inboxu se hned maže,
  kontejner App Group systém neuklízí.
- **Otevřít aplikaci z rozšíření sdílení oficiálně nejde.** `UIApplication.shared` ani `open`
  v rozšíření nejsou a **Xcode 27 u rozšíření vynucuje `APPLICATION_EXTENSION_API_ONLY = YES`**
  (s `NO` build selže). `openURL:options:completionHandler:` se proto volá přes Objective-C
  runtime na instanci `UIApplication` z řetězce responderů (`method(for:)` +
  `@convention(c)`). Na iOS 26 v simulátoru funguje; URL schéma aplikace je `eu.tintera.plume`.
- **Rozšíření fotky nezmenšuje** — limit paměti rozšíření je malý a zmenšení umí aplikace.
- **Simulátor bere entitlementy ze sekce `__TEXT,__entitlements` binárky**, ne z podpisu
  (ad-hoc, `codesign -d --entitlements` ukáže prázdný slovník). Ověření:
  `otool -s __TEXT __entitlements <binárka>`. S `CODE_SIGNING_ALLOWED=NO` tam nejsou a kontejner
  App Group neexistuje — pro zkoušení sdílení se staví s výchozím podepisováním. Kontejner:
  `xcrun simctl get_app_container <zařízení> eu.tintera.plume groups`.
- **Na zařízení musí App Group existovat v Apple Developer účtu.** Automatické podepisování
  ho s vyplněným `TEAM_ID` založí samo; bez týmu se build na zařízení nepodepíše.
- **Target rozšíření se do projektu přidával gemem `xcodeproj`** (`LANG=en_US.UTF-8`, jinak
  spadne na diakritice v pbxproj). Projekt je ve formátu Xcode 16 se synchronizovanými
  složkami; gem ho načte i uloží bez ztrát. Entitlementy aplikace jsou v `Configuration/`,
  ne v synchronizované složce `iosApp/` — odtud by je Xcode kopíroval jako resource. Obojí je „vyber, kam s tím".
- **Ověřování přes `am start` pozor na uvozovky**: `adb shell am start … --es … "dvě slova"`
  rozdělí text na dva argumenty a z druhého slova udělá balíček. Celý příkaz patří do jedněch
  uvozovek: `adb shell "am start … --es android.intent.extra.TEXT 'dvě slova' …"`. A hned po
  `adb install` sdílení někdy spolkne systémové znovuspuštění aplikace (`PackageUpdateActivity`)
  — zkoušet až s běžící aplikací.

#### Návrhy chatů v systémové nabídce (obdoba Direct Share)

Každé odeslání a přeposlání (`ShareTargets.reportUsed` → `IosShareTargetPublisher`) daruje
systému `INInteraction` s `INSendMessageIntent`; z toho iOS skládá řádek návrhů nahoře
v nabídce sdílení. Ťuknutí na návrh otevře rozšíření s `extensionContext.intent` a rozšíření
výběr chatu přeskočí — rovnou zapíše inbox a otevře aplikaci.

- **Podmínky jsou tři a chybějící se neprojeví chybou**, jen se nic neukáže (jako u Androidu):
  `NSUserActivityTypes` s `INSendMessageIntent` v Info.plist APLIKACE, `IntentsSupported`
  s týmž v `NSExtensionAttributes` rozšíření a `conversationIdentifier` u darovaného intentu
  (= id chatu, rozšíření ho čte zpátky).
- **Konverzace se daruje jako „skupina" (`speakableGroupName` + obrázek pro ten parametr)
  i u soukromého chatu.** Recipient `INPerson` chce handle nebo kontakt, které o protějšku
  nemáme. Obrázek jde jako data (`imageWithImageData`), ne URL souboru TDLib — ten leží
  v kontejneru aplikace, kam systém nedosáhne. Uložené zprávy obrázek nemají.
- **Po odhlášení se darované konverzace mažou** (`deleteAllInteractionsWithCompletion`, vedle
  smazání snímku chatů) — nesou jména a fotky chatů předchozího účtu.
- **Chat z návrhu, který snímek nezná, se pošle podle id** — aplikace ho zná, i když vypadl
  z padesáti chatů snímku.
- **Řádek návrhů se na simulátoru NEUKÁŽE.** Darování projde bez chyby, ale `sharingd`
  hlásí „People suggestions are not expected" a coreduetd v simulátoru neodpovídá. Ověřuje se
  proto po částech: darování dočasným `NSLog` v completion (`error=null`, v
  `log show --predicate 'eventMessage CONTAINS "…"'`), cesta rozšíření dočasně podstrčeným
  intentem (`context?.intent ?? INSendMessageIntent(…)`) a hlídáním inboxu App Group smyčkou
  v shellu (aplikace manifest při návratu do popředí hned vyzvedne). Sondy se necommitují.
- **Na simulátoru se po spuštění nejdřív ukáže zadání čísla** — to je krok `Connecting`
  (patří k zadání čísla, viz „Přihlašovací obrazovka"), ne odhlášení. Rozhoduje, co je vidět
  o pár vteřin později.
