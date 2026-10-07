# Plume — co dodělat

Živý dokument přes celou aplikaci. Nové nápady sem, hotové věci ven (a poučení z nich do
`CLAUDE.md`). Pořadí uvnitř sekcí je doporučení, ne závazek.

Detail chatu má vlastní, podrobnější seznam: [roadmap-konverzace.md](roadmap-konverzace.md).
Sem patří jen to, co je mimo něj.

---

## Rozpracované

Sem patří to, co leží v pracovním adresáři a ztratilo by se při přerušení práce: nedodělaná
větev, ladicí kód k vyhození, nedopsané poučení. Právě tohle se ztrácí nejsnadněji, protože to
není ani v kódu, ani v commitu.

Teď je prázdné.

---

## Než se dá vydat na veřejnost

- **Záznam v Play dokončit mimo API.** Texty a grafika (cs, en) jsou nahrané, workflow Play
  Listing je nahrává z `play/`. Ručně v Play Console zbývá kategorie, zásady ochrany soukromí (URL),
  hodnocení obsahu (IARC), cílová skupina, bezpečnost dat a prohlášení o reklamách.
- **Seznam chatů na snímcích obrazovky chybí.** Na účtu jsou skutečné osoby; snímek seznamu
  chce demo účet se smyšlenými chaty.
- ~~**`isShrinkResources` není zapnuté.**~~ — hotovo (release proklikaný na emulátoru;
  `CLAUDE.md`, „R8").

## iOS

Android je napřed; tohle je, čím se to dorovnává.

- **AI v zařízení není vůbec** (`NoopOnDeviceAi`), takže shrnutí chatu ani návrhy odpovědí na
  iOS nejsou. Apple Foundation Models jsou k mání od iOS 26 a dají se zapojit za stávající
  rozhraní `OnDeviceAi` — implementace musí přijít ze Swiftu, jako u hlásiče pádů.
- **Notifikace** — napojené (APNs, rozšíření notifikací, texty, oprávnění, úklid přečtených
  a odznak; `CLAUDE.md`, „Notifikace na iOS (APNs)"). Zbývá:
  - **Certifikát APNs u Telegramu** (my.telegram.org) a Push Notifications v Apple Developer účtu.
  - **Ověřit skutečným pushem na telefonu**: jestli Telegram posílá `mutable-content` (běží
    rozšíření → akce a seskupení), tvar `custom` (`push/last.json` v App Group) a češtinu šablon.
  - **Titulek zvlášť** (jméno chatu jako titulek, text pod ním) — dnes je celá věta v těle,
    jako „Jana: ahoj". Šlo by to v rozšíření, až bude jisté, že běží.
  - **Přečtení a smazání jinde v reálném čase** (`READ_HISTORY`, `MESSAGE_DELETED`) — dnes se
    uklidí až s otevřením aplikace.
  - **Vysvětlení před dotazem na oprávnění ověřit na čistém simulátoru** (na testovacím bylo
    oprávnění vyřízené dřív).
- **Hlášení pádů.** `NoopCrashReporter`. Implementace musí přijít ze Swiftu — `FirebaseCrashlytics`
  je ObjC knihovna, kterou Kotlin/Native nevidí, a Kotlin výjimka nese jen adresy stack trace.
  Vzor je `IosCrashlyticsLogger` v projektu com.profilog.telemetry.android.
- **Release link do CI jako samostatný cachovaný krok.** Kotlin/Native pouští LTO nad celou
  statickou TDLib a naměřeno je 15+ minut na M-series; do smyčky, kterou čeká vývojář, to nepatří.

## Obrazovky a sekce, které nejsou

Sekce jsou dnes dvě: Chaty a Nastavení (z menu účtu, rail ani lišta nejsou). Routy: `ChatsRoute`, `SettingsRoute`,
`SettingsSectionRoute`, `ArchiveRoute`, `ArchiveConversationRoute`, `ArchiveChatProfileRoute`,
`ConversationRoute`, `ChatProfileRoute`, `MediaRoute`, `ForwardRoute`, `ShareRoute`, `NewChatRoute`
(`core/navigation/Routes.kt`).

- ~~**Přihlášení: zapomenuté heslo je slepá ulička.**~~ — hotovo (kód na obnovovací e-mail,
  nové heslo s nápovědou, dialog u účtu bez e-mailu; `CLAUDE.md`, „Obnova hesla a cesta zpátky
  k číslu"). Zbývá:
  - **Ověřit se skutečným účtem** s heslem a obnovovacím e-mailem — zatím jen podstrčenou session.
  - **Reset účtu bez obnovovacího e-mailu** (`deleteAccount` s prázdným heslem, provede se po
    týdnu). Dnes dialog posílá do oficiální aplikace. Než se postaví: jak TDLib hlásí čekání
    (server má `2FA_CONFIRM_WAIT_…`), v jakém stavu automat zůstane a co ukázat, když se uživatel
    během týdne vrátí. Ověřit se to na testovacím účtu nedá.
  - **Telegram Passport** (`hasPassportData`): jestli obnova hesla uložené údaje smaže a má se
    to říct předem, ověřené není.

- ~~**Archiv**~~ — hotovo (seznam v menu účtu, archivace a vrácení z nabídky chatu i tahem do
  strany s „Vrátit", počet nepřečtených u vstupu, nastavení archivu). Poučení v `CLAUDE.md`,
  „Archiv". Zbývá:
  - **Ověřit nastavení archivu proti druhému účtu.** Že se „archivovat od neznámých" a
    „nechávat chaty v archivu" opravdu projeví, se s jedním účtem nedá vidět — zapsání
    i přečtení hodnot ověřené je, jejich následek ne.
- ~~**Detail chatu / profil.**~~ — hotovo (ťuknutí na hlavičku: fotka, jméno, ztlumení, hledání,
  údaje, členové skupiny; `CLAUDE.md`, „Profil chatu"). Zbývá:
  - ~~**Sdílená média**~~ — hotovo (záložky Členové / Média / Soubory / Odkazy s dotahováním
    stránek; `CLAUDE.md`, „Sdílená média v profilu"). ~~Hledání uvnitř záložky~~ — hotovo
    (soubory, odkazy, hudba). ~~Velký náhled u souborů~~ — hotovo (obrázek a video
    poslané jako soubor se ze záložky Soubory otevřou v prohlížeči).
  - ~~**Akce navíc**~~ — hotovo: kopírovat odkaz (i pozvánku pro správce), zablokovat, smazat
    chat / opustit skupinu či kanál (⋮ v liště) a ~~**upravit skupinu**~~ (název, popis, fotka,
    práva členů; `CLAUDE.md`, „Úprava skupiny") a ~~**správa jednotlivých členů**~~ (dlouhý
    stisk na členovi: povýšit na správce a upravit jeho práva, sesadit, omezit s dobou, zrušit
    omezení, štítek, vyhodit s dobou, předat vlastnictví; `CLAUDE.md`, „Správa členů").
    ~~Sdílet kontakt~~ — hotovo (⋮ → Sdílet kontakt). ~~Správa pozvánek~~ — hotovo (odkazy
    s limitem, platností a schvalováním, zrušení a mazání, žádosti o vstup; `CLAUDE.md`,
    „Pozvánky a žádosti o vstup"; sdílení odkazu, odkazy ostatních správců, kdo se přidal
    a pruh s čekajícími žádostmi v konverzaci jsou hotové). U pozvánek zbývá **ověřit
    skutečnou žádost o vstup a „kdo se přidal" se skutečnými lidmi** (chce druhý účet).
    Uložení úpravy skupiny **není ověřené na zařízení**: mění skutečnou skupinu. Totéž platí
    pro uložení práv člena, omezení, vyhození, štítek a dokončený převod vlastnictví — ověřené
    je jen to, co skutečnou skupinu nemění (viz „Správa členů" v `CLAUDE.md`). Převod navíc
    vyžaduje zapnuté dvoufázové ověření, které testovací účet nemá.
  - ~~**Seznam členů velké skupiny**~~ — hotovo (stránky po 50, hledání, věta u skupiny se
    skrytými členy; `CLAUDE.md`, „Členové velké skupiny"). **Zbývá ověřit ve skupině, kde členy
    vidět jsou** — testovací účet v takové velké není; ověřené je to sondou s malou stránkou.
- ~~**Pořadí připnutých chatů**~~ — hotovo: „Změnit pořadí připnutých" v nabídce nad připnutým
  chatem, tažení za úchyt, v hlavním seznamu, archivu i složce (`CLAUDE.md`, „Akce nad chatem").
- ~~**Složky chatů**~~ — zobrazení i správa hotové (řádek pod pilulkou, přepínání, nepřečtené;
  Nastavení → Obecné → Složky chatů: založit, upravit, smazat, přeřadit, doporučené; „Přidat do
  složky" nad chatem; `CLAUDE.md`, „Složky chatů"). Zbývá:
  - **„Všechny chaty" jinde než první** (`mainChatListPosition`, jen Premium) — správa ho drží
    na místě, kde je.
  - ~~**Sdílené složky**~~ — hotovo: náhled a přidání z odkazu, odkazy na vlastní složku i jejich
    úprava, nové chaty v přidané složce a chaty k opuštění při jejím smazání. Skutečné přidání
    cizí složky a nové chaty od autora ověřené nejsou (chce to druhý účet).
  - **Štítky složek u chatů** (`toggleChatFolderTags`, jen Premium) a barva složky.
  - ~~**Ikona složky**~~ a ~~**„Nechávat chaty ze složek v archivu"**~~ — hotovo. Zápis volby
    archivu ověřený není (mění nastavení účtu).
- **Hledání.** ~~Chaty ve všech složkách (lokálně i na serveru), historie hledání a nejčastější
  lidé, totéž ve výběru pro přeposílání a sdílení~~ — hotovo (`ChatSearch`, poučení
  v `CLAUDE.md`, „Hledání chatů"). Zbývá, po krocích:
  1. ~~**Globální hledání a zprávy**~~ — hotovo (sekce Globálně a Zprávy, skok na nalezenou
     zprávu, „Přidat se" u veřejného chatu). Zbývá:
     - ~~**Filtry hledání zpráv**~~ — hotovo (čipy druhu chatu a období nad výsledky).
     - ~~**Jméno odesílatele u nalezené zprávy ve skupině**~~ — hotovo („Jana: …", „Vy: …").
     - ~~**Kanál po „Přidat se" pořád nabízí vstupní pole**~~ — hotovo: kanál bez práva
       publikovat má místo pole Ztlumit (`CLAUDE.md`, „Práva psát").
  2. ~~**Klikatelné zmínky a hashtagy v textu**~~ — hotovo (`@jméno` otevře chat, `#tag`
     hledání zpráv). Odkazy na Telegram (chat, zpráva, pozvánka, sada samolepek) se otevírají
     v aplikaci (`CLAUDE.md`, „Odkazy na Telegram"); ~~boti se `start`, mini aplikace, sdílené
     složky, člověk podle čísla a kontaktního odkazu, `?text=` a `?profile`, `t.me/share`,
     Uložené zprávy, proxy (`t.me/proxy`, `t.me/socks`)~~ hotové. ~~Odkazy do Nastavení
     (`tg://settings/…`), nová konverzace, skupina a kanál, kontakty, hledání, videochat
     a přihlašovací kódy~~ — hotovo (přihlašovací odkazy se jen vysvětlí, neotevřou). Do
     prohlížeče dál jdou platby, stories, dárky, témata vzhledu a jazykové balíčky. ~~**Veřejné příspěvky
     s tagem**~~ — hotovo (přepínač „Vaše chaty / Veřejné příspěvky" u hashtagu).
  3. ~~**Hledání v konverzaci**~~ — hotovo (lupa v hlavičce, šipky mezi výsledky, viz
     `CLAUDE.md`, „Hledání v konverzaci"). Hledané slovo je v bublinách zvýrazněné.
     Ve skupině jde hledat i podle člena (čip v hlavičce hledání).
  4. ~~**Filtry obsahu** (média, soubory, odkazy), hlavně pro profil chatu~~ — hotovo (záložky
     profilu s hledáním). Zbývá hledání médií podle popisku, kdyby o něj byl zájem.

  ~~Na rozloženém Foldu by místo celoobrazovkového hledání sedělo `ExpandedDockedSearchBar`~~ —
  hotovo (panel pod pilulkou, `CLAUDE.md`, „Hledání chatů“); ~~zpět s otevřenou klávesnicí
  zavřel celé hledání~~ — opraveno (napřed schová klávesnici).
- ~~**Nová konverzace a kontakty.**~~ — hotovo (FAB „Nová zpráva“ v seznamu chatů,
  `NewChatRoute`, Uložené zprávy, kontakty z TDLib s dostupností, globální vyhledávání lidí podle
  @username; poučení v `CLAUDE.md`, „Nová konverzace").
  ~~**Založení skupiny a kanálu**~~ — hotovo („Nová skupina" / „Nový kanál" nad kontakty, výběr
  členů, název a fotka, u kanálu popis, ~~soukromý / veřejný s odkazem~~ a ~~pozvánka lidem,
  které server nepustil~~ a ~~veřejný odkaz v úpravě chatu~~; `CLAUDE.md`, „Založení skupiny
  a kanálu" a „Veřejný odkaz v úpravě chatu"). Zbývá ověřit pozvánku se skutečným člověkem,
  který přidávání zakazuje (dialog jen sondou), a veřejnou základní skupinu (chce převod na
  supergroupu, `upgradeBasicGroupChatToSupergroupChat`).
- **Ochrana před spamem.** ~~Zablokovat z nabídky nad chatem a při mazání rovnou zablokovat
  i nahlásit~~, ~~průvodce nahlášením s volbou důvodu~~ (i zprávy z nabídky nad zprávou),
  ~~pruh nad konverzací u chatu od neznámého~~ — hotovo (`CLAUDE.md`, „Blokování a nahlášení
  spamu"). Zbývá:
  - ~~**„Vrátit mezi chaty" v pruhu**~~ — hotovo (`CLAUDE.md`, „Pruh nad konverzací").
    **Zbývá ověřit na zařízení**, že se konverzace otevřená z archivu přestěhuje do hlavního
    seznamu a chat se odtlumí; emulátor při zkoušce spadl a odhlásil se.
  - **Zbylé druhy pruhu nad chatem.** `ChatActionBar` má v TDLib 15 šest druhů a „akce, které
    musí jít z pruhu udělat" (dokumentace TDLib); Plume kreslí jen `ReportSpam`, `ReportAddBlock`
    a `AddContact`. Chybí:
    - ~~**Údaje o protějšku u `ReportAddBlock`**~~ — hotovo (`CounterpartAccount`). Česká
      podoba řádků ověřená na zařízení není, jen anglická.
    - ~~**Upozornění na emoji status** u `ReportSpam` / `ReportAddBlock`~~ — hotovo spolu
      s emoji statusy (`SpamBar.warnsEmojiStatus`), ověřené podstrčeným pruhem.
    - ~~**`SharePhoneNumber`**~~ a ~~**`JoinRequest`**~~ — hotovo (`ChatNoticeBarCard`, ověřené
      podstrčeným pruhem; skutečné nasdílení čísla ověřené není).
    - ~~**`InviteMembers`**~~ — hotovo (`InviteMembersBar`, výběr `AddMembersScreen`, i z ⋮
      v profilu). Pruh ověřený podstrčeným stavem, přidání testovacím botem; odmítnutí kvůli
      soukromí jen sondou — skutečný člověk s takovým nastavením ověřený není.
    - **Pruh firemního bota** (`UpdateChatBusinessBotManageBar`, jen účty s Telegram Business):
      pozastavit bota v chatu a odkaz na jeho správu. Je to samostatný update, ne `ChatActionBar`.
  - ~~**Nahlásit z režimu výběru víc zpráv**~~ — hotovo (vlajka v liště výběru).
  - **Ověřit skutečný pruh (`UpdateChatActionBar`)** na chatu od cizího — zatím jen podstrčeným stavem.
  - ~~**Seznam blokovaných v Nastavení**~~ — hotovo (Obecné → Soukromí → Blokovaní), ~~i se
    „Zablokovat uživatele"~~ (kontakty a známé soukromé chaty, s potvrzením).
- **Přihlašovací obrazovka** — hotová (výběr země, formátování čísla, návrat z QR na číslo,
  expressive přestavba, ~~zpět z kódu a hesla k číslu~~; `CLAUDE.md`, „Přihlašovací obrazovka").
  Zbývá:
  - **Pole přihlášení jedou přes `StateFlow`**, a rychlý `adb input text` v nich na pomalém
    emulátoru přeháže znaky. Na zařízení nepozorované; kdyby se ukázalo, patří text do Compose
    stavu ve ViewModelu jako u úpravy profilu.
- **Sdílení ze systému do aplikace.** ~~Android~~ — hotovo (text, odkaz, jeden i víc
  souborů; výběr chatu, obsah čeká nad vstupním polem). Poučení v `CLAUDE.md`, „Sdílení do
  aplikace". Zbývá:
  - ~~**Direct Share**~~ — hotovo. Na iOS jsou naposledy použité chaty nahoře v listu rozšíření;
    řádek kontaktů v systémové nabídce (darování `INSendMessageIntent`) zatím není.
  - ~~**Opakované sdílení do otevřené obrazovky „Komu: …" ji zavře**~~ — opraveno (obsah
    vzniká rovnou s chatem, obrazovka zahazuje jen své sdílení; `CLAUDE.md`, „Opakované sdílení").
  - ~~**iOS: Share Extension**~~ — hotovo (výběr chatu v rozšíření, App Group). Zbývá
    ověřit na zařízení (App Group v Developer účtu).
  - **Sdílení jako kontext místo samostatného výběru** (nápad k rozmyšlení, nahradil by
    `ShareScreen`). Po sdílení se neotevře zvláštní výběr, ale **normální aplikace se seznamem
    chatů** — s plnohodnotným hledáním, archivem a vším ostatním. Nahoře visí **čip „Sdílíte:
    fotka.jpg"** (s náhledem a ✕), který se s uživatelem nese po aplikaci. V otevřené konverzaci
    jde obsah jedním ťuknutím na čip **ukotvit nad vstupní pole** a poslat jako každou přílohu.
    - **Přináší druhý případ užití:** kdo sdílení nedokončí hned (vrátí se do Plume, přepne
      jinam), o obsah nepřijde — čip zůstane, dokud ho nepošle nebo ✕ nezahodí.
    - **Oprávnění k souboru tomu nebrání.** `ShareIntake` soubory kopíruje do cache hned při
      přijetí (oprávnění k `content://` je dočasné); kopie vydrží, jak dlouho je potřeba. Na iOS
      jsou soubory v inboxu App Group a přesunou se do `outgoing` stejně.
    - **Stav už existuje:** `ShareRequests` drží obsah do vyzvednutí (`SharedContent`); chybí
      jen `targetChatId` nahradit volbou v konverzaci a čip v shellu (nad seznamem i konverzací,
      na compactu i v dvoupanelu).
    - **Rozmyslet:** co ✕ na čipu vs. zpět (dnes zpět vrací do původní aplikace — s čipem by
      zpět mohl zůstat normální navigací, protože obsah se neztratí); jak dlouho kopii držet
      (po restartu procesu? `ShareRequests` je jen v paměti — trvalejší by chtělo soubor
      s manifestem jako na iOS); co s Direct Share (tam je chat vybraný předem, čip nepotřebuje);
      a iOS rozšíření, které dnes chat vybírá samo — mohlo by jen předat obsah a výběr nechat
      aplikaci, pak by na obou platformách fungovalo sdílení stejně.

## Hovory

Hovory nejsou vůbec. Zpráva o hovoru je v konverzaci bublina s holým „Hovor" / „Videohovor"
(`MessageCall` v `MessageContent.kt`) a příchozí hovor (`updateCall`) aplikace neodebírá —
kdo má jen Plume, o hovoru se nedozví.

**TDLib umí jen signalizaci, ne přenos zvuku a obrazu.** Založení, přijetí, zavěšení
(`createCall`, `acceptCall`, `discardCall`), stav přes `updateCall`, výměnu klíčů s emoji
k ověření, seznam serverů a předávání signalizačních dat (`sendCallSignalingData`,
`updateNewCallSignalingData`); totéž pro skupinové hovory a videochaty (`joinGroupCall`…).
Média obstarává samostatná knihovna **tgcalls** (C++, nad WebRTC), kterou si přibalují všichni
klienti včetně Telegram X na TDLib.

Dá se to rozdělit na dva kroky, které na sobě nezávisí:

1. **Hovory v historii a příchozí hovor bez přenosu** (malé, jen nad TDLib):
   - ~~**Zpráva o hovoru jako řádek, ne bublina s jedním slovem**~~ — hotovo (směr, zmeškaný /
     odmítnutý / zrušený, délka, video; `CLAUDE.md`, „Zpráva o hovoru"). Ťuknutí nevede nikam.
     **Skutečný hovor v historii ověřený není** — jen sondou v chatu s botem.
   - ~~**Příchozí hovor ohlásit**~~ — hotovo na Androidu: notifikace v kanálu Hovory
     s „Odmítnout" (`CLAUDE.md`, „Ohlášení příchozího hovoru"). **Skutečný hovor ověřený NENÍ**
     (chce druhý účet), jen sondou. Na iOS ohlásí hovor push od Telegramu sám (`PHONE_CALL_REQUEST`
     v textech pushe); v popředí aplikace se zatím neohlašuje nic.
   - ~~**Hovory ve skupině**~~ — hotovo: pruh „Probíhá videochat" / „Živé vysílání" / naplánovaný
     nad konverzací s počtem účastníků (`CLAUDE.md`, „Pruh videochatu"). Připojit se nejde;
     ťuknutí řekne, kde to jde.
2. **Skutečné hovory** (největší samostatný kus práce v projektu). Přenos jde dvěma cestami:
   - **A) Přeložit tgcalls s WebRTC** pro Android (ABI) i iOS a napojit přes JNI / cinterop
     nebo Swift. Ověřená kompatibilita (tgcalls používají oficiální klienti), ale překlad
     velkého C++ projektu na obě platformy.
   - **B) Experiment: holé libwebrtc a vlastní tenká vrstva Telegramu.** tgcalls WebRTC
     nepřepisuje, linkuje libwebrtc od Googlu a přidává nad ním jen to, co je specifické pro
     Telegram (zjištěno čtením [zdrojáků](https://github.com/TelegramMessenger/tgcalls/tree/development/tgcalls)):
     - **signalizace jako JSON**, ne SDP (`v2/Signaling.cpp`: `@type` `InitialSetup`,
       `NegotiateChannels`, `Candidates`, `MediaState`), přenášená přes
       `sendCallSignalingData`;
     - **šifrování signalizace klíčem hovoru** (`v2/SignalingEncryption.cpp` →
       `EncryptedConnection`) — tím se emoji k ověření vztahují i na DTLS fingerprinty média;
     - **relay servery Telegramu** („reflector", `v2/ReflectorPort.cpp`,
       `v2/MtProtoIceTransport.cpp`), které nejsou standardní TURN;
     - **víc verzí protokolu vedle sebe** a vyjedná se společná (`CallProtocol.libraryVersions`):
       `InstanceImpl` 2.7.7 a 5.0.0, `InstanceV2Impl` 7, 8, 9, 12, 13,
       `InstanceV2ReferenceImpl` 10.0.0 a 11.0.0.

     **`InstanceV2ReferenceImpl` (10.0.0 / 11.0.0) stojí na běžném `CreatePeerConnection`
     s DTLS** a mezi JSON signalizací a SDP překládá (`SignalingTranslator`). Experiment by tedy
     byl: hotové libwebrtc (Android `org.webrtc`, iOS `WebRTC.xcframework`), vlastní JSON
     signalizace s jejím šifrováním a převodem na SDP, nabízet jen 10.0.0 a 11.0.0. Protokol
     není nikde popsaný, jediná specifikace je zdroják tgcalls — a ten se s verzemi mění.
     **Ověřit dřív, než se začne psát:**
     - **Nabízejí oficiální klienti 10.0.0 / 11.0.0 pořád?** Jinak se s nimi náš klient
       nedomluví. Pozná se z `CallProtocol` protějšku v `updateCall` / `CallStateReady`
       (hovor z oficiálního klienta na účet s Plume stačí jen přijmout do stavu Ready).
     - **Stačí bez reflectorů TURN servery, které dá TDLib** (`CallServerTypeWebrtc`
       se jménem a heslem)? Když ne, musel by se napsat i reflector port.

     **Velikost aplikace se volbou cesty nezmění** — většinu váhy nese samotné libwebrtc
     (desítky MB na ABI), takže u obou cest platí stejná úvaha o Play Feature Delivery jako
     u LiteRT-LM.
   - **Platformní obálka:** Android `ConnectionService` (a foreground služba typu
     `phoneCall`/`microphone`/`camera`), iOS **CallKit** a **VoIP pushe přes PushKit** — bez
     nich příchozí hovor nezazvoní na zamčeném telefonu, a Apple je aplikaci s hovory předepisuje
     (VoIP push, který neohlásí hovor CallKitu, systém trestá ukončením aplikace).
   - **Obrazovka hovoru:** stav, emoji k ověření, ztlumit / reproduktor / kamera / zavěsit,
     obraz v obraze pro video. Oprávnění mikrofonu už máme (hlasovky), kamery ne.
   - **Skupinové hovory** až potom — jiná signalizace a víc účastníků. Tady je cesta přes holé
     WebRTC realističtější než u hovoru ve dvou: prostředníkem je server Telegramu (SFU) a data
     pro `joinGroupCall` jsou blízko běžnému WebRTC (ufrag, pwd, fingerprinty, SSRC v JSONu);
     komunitní knihovny (pytgcalls / ntgcalls) to tak dělají.
   - Ověřování chce druhý účet nebo druhé zařízení; bot volat neumí.

## AI v zařízení

Shrnutí chatu a návrhy odpovědí běží na Gemini Nano přes ML Kit Prompt API (Android). Poučení
z prototypu je v `CLAUDE.md`, sekce „AI v zařízení"; sem patří, co zbývá.

**Model se NEUČÍ a učit ho nejde.** Žádný fine-tuning ani doladění na uživatelových datech:
balík spravuje systém a aplikace ho jen volá. Co API nabízí místo toho, je kešovaný kontext
(`Caches`, níž) a system prompt — a hlavně personalizace na NAŠÍ straně: co si aplikace
o uživateli pamatuje, vloží do promptu. Kdo čeká, že se model časem „naučí, jak uživatel píše",
hledá něco, co tam není.

**Pravidlo pro výběr featur: model drží fakta, ale drhne mu sloh.** Čísla, jména a termíny
drží spolehlivě — na ověřovacím testu (ceny, číslo účtu, datum, jména) si nevymyslel nic —,
zatímco věty komolí („telefon nebudou mít vibrace", „Pěče pod Sněžkou") a mezi třemi návrhy
odpovědi bývá jeden nesmysl. **Prompt už vyladěný je; tohle je strop modelu, ne zadání.**
Z toho plyne pořadí, ve kterém se vyplatí featury stavět:

- **Klasifikace, extrakce a rozhodování jsou silná stránka** — výstupem je štítek, datum nebo
  ano/ne, takže na slohu nezáleží a chyba se pozná.
- **Úprava textu, který uživatel schválí, je únosná** — chybu vidí a opraví, než odešle.
- **Generování textu k odeslání je ta nejslabší kategorie.** Shrnutí i našeptávání odpovědí
  z ní obě jsou; zajímavé věci leží v první a zatím z ní nemáme nic.

### Co zbývá u toho, co běží

- **Větší model: Gemma přes LiteRT-LM běží, ověřená na emulátoru (E2B na CPU).** Preview
  varianta Gemini Nano (Gemma 4 **E4B**) přes ML Kit nejde (`Feature -1`, viz `CLAUDE.md`),
  takže přibyl druhý poskytovatel — Gemma 4 E2B / E4B, kterou si stáhne aplikace
  (`LiteRtOnDeviceAi`). Stažení, navázání po výpadku sítě, smazání, shrnutí, návrhy i release
  build (s keep pravidlem) prošly. Zbývá:
  - **vyzkoušet na Foldu E4B a GPU** a hlavně **porovnat češtinu E4B s Gemini Nano** — kvůli
    tomu to celé je; E2B na emulátoru vyšla kvalitou zhruba jako Nano,
  - **velikost APK**: nativní knihovny přidají ~25 MB na ABI každému, i kde se Gemma nenabídne
    → Play Feature Delivery,
  - ~~**stahování na pozadí**~~ — hotovo (úloha WorkManageru s notifikací průběhu a „Zastavit";
    `CLAUDE.md`, „AI v zařízení"). Zbývá ověřit v release buildu a na telefonu a **vyplnit
    v Play Console deklaraci služby v popředí `dataSync`**.
- **Beta ML Kitu sledovat.** `1.0.0-beta4` je zatím nejnovější; novější verze může `Feature -1`
  opravit a preview zpřístupnit bez vlastního runtime. Stojí za to to při bumpu zkusit znovu.
- **Kešování kontextu a `warmup()` nejsou využité.** Prompt API je nabízí (`Caches`,
  `CachedContext`, `isCachingFeatureAvailable`) a u opakovaného shrnování téhož chatu by mohly
  ušetřit čas. Zatím netestované.
- **Shrnutí bere jen to, co se vejde do jednoho promptu.** U chatu s tisíci nepřečtenými se
  zkrátí na limit vstupu (8 192 tokenů) a zbytek se zahodí. Postupné shrnování po částech
  („map-reduce") zatím není.
- **Strop výstupu je kompromis.** `MAX_OUTPUT_TOKENS = 800` stačí na shrnutí několika desítek
  zpráv; delší konverzace se useknou a UI to sice přizná, ale lépe by bylo shrnutí zkrátit
  instrukcí než ho nechat uříznout uprostřed.

### Co by šlo postavit

Seřazené podle toho, jak sedí na pravidlo výš, ne podle nápaditosti. **Každá z nich má cenu,
se kterou se musí počítat předem: inferenci pustí AICore jen aplikaci v POPŘEDÍ.** Z notifikace
ani z úlohy na pozadí tedy nic z toho běžet nemůže — featura, která má vyhodnotit zprávu
v okamžiku, kdy přijde, narazí na tohle, ne na model.

- **Strukturovaný výstup jako první krok.** Prompt API umí vrátit rovnou instanci Kotlin třídy
  (`GenerateTypedContentRequest<T>` s `outputClass`, `TypedCandidate<T>.response`), ne text.
  Tím padá celá třída problémů, se kterou se dnes pereme: `parseReplySuggestions` je tolerantní
  parser právě proto, že model nedrží formát odrážek. **A každá featura z té silné kategorie
  na tom stojí** — datum, štítek i skóre chceme jako typ, ne jako větu. Zlepší tedy i to, co už
  máme, takže má smysl začít tady.
- **Detekce podvodných zpráv.** „Naskenuj tenhle QR a ověř si účet", falešná výhra, odkaz na
  přihlášení. Nenápadné varování u zprávy. Je to klasifikace, tedy silná stránka, a **děje se to
  celé v telefonu** — obsah zprávy nikam neodchází, což je u téhle featury podstatné. Navazuje
  na pravidlo, které projekt už má (odkaz `tg://login` se z ťuknutí neotevírá, viz `CLAUDE.md`,
  „Přihlášení QR kódem"). Pozor na to, že varování u legitimní zprávy je horší než žádné:
  práh se musí nastavit tak, aby se raději mlčelo.
- **Termín a úkol ze zprávy.** „Sraz v 15:00 na Černém Mostě" → nabídka přidat do kalendáře
  nebo připomínku. Čistá extrakce a data model prokazatelně drží. Nabízí se jen tehdy, když si
  je modelem jistý; nabídka u každé druhé zprávy by byla otrava.
- **Triáž chatů.** Odznak „čeká na odpověď" vs. „jen informace" v seznamu chatů. Rozhodnutí
  ano/ne nad krátkým textem je rychlé (0,2–0,4 s), ale **počítá se pro každý chat zvlášť**,
  takže musí běžet nad načteným seznamem a výsledek se musí kešovat podle id zprávy.
- **Štítky Uložených zpráv.** Témata Uložených zpráv (dělení podle chatu) už jsou, štítky od
  Telegramu (`SavedMessagesTag`, jen s Premium) ne; automatické zatřídění by je nahradilo bez
  serverové podpory a je to zase klasifikace.
- **Přepsat před odesláním** („kratší", „zdvořileji", „bez překlepů"). Sloh drhne, ale uživatel
  výsledek VIDÍ a rozhodne — chyba je levná, na rozdíl od našeptávání, kde se text podepisuje
  naslepo. Patří do nabídky nad vstupním polem, ne do automatiky.
- **Vysvětlit dlouhou cizí zprávu** („o co jde") — totéž co shrnutí, jen nad jednou zprávou,
  takže se vejde do vstupu a sloh vadí míň, protože to čte jen uživatel.

**Na překlad se Nano nehodí a nemá se používat.** ML Kit má vlastní offline Translation API,
stavěné přímo na to; pouštět na překlad jazykový model by bylo horší v kvalitě i v čase.

---

## Výkon a paměť

Nástroje a postup jsou v `CLAUDE.md` („Výkon a paměť"). První analýza (reporty Compose
kompilátoru, LeakCanary, heap dumpy) únik nenašla. Zbylo:

- **Další kandidáti na stabilitu** (`compose-stability.conf`, viz `CLAUDE.md`): `ForumTopic`
  (řádky seznamu témat se při každé změně tématu mohou přeskládat všechny, jako dřív seznam
  chatů — neměřeno, testovací skupina témata nemá), `MemberDetail`, `InviteLink`. Napřed změřit
  (`client/tools/perf/`), pak teprve přidávat.
- **Příchozí zpráva přeskládá v každém snímku starší bublinu** po dobu animace nároží (~0,6 s,
  `animateDpAsState` v `MessageFrame` se čte v kompozici). Na emulátoru ~1 ms za snímek.
  Šlo by číst až při kreslení (tvar čtoucí stav v `createOutline`), ale je to zásah do
  vzhledu bubliny; nízká priorita.
- **Fully drawn na Foldu** ověřit s první verzí, která `ReportDrawnWhen` nese: měřené
  hodnoty na telefonu jsou zatím jen první snímek.
- **Native heap** rostl během testu z ~80 na ~130 MB. Ověřit delším během, jestli se ustálí
  (TDLib, kodeky, Chromium z mini aplikace), nebo roste dál.

---

## Haptika

Slovník je hotový (`core/ui/Haptics.kt`: `threshold`, `confirm`, `reject`, `toggle`, `tick`)
a poučení z něj v `CLAUDE.md`, „Haptika". Odezvu dnes mají: prahy tahů (archivace, odpověď,
zavírací tah v prohlížeči médií), dlouhý stisk odkazu, odeslání zprávy, samolepky i GIFu,
start a konec nahrávání hlasovky i její zahození, reakce (pruh i plný výběr), přidání
a odebrání z výběru zpráv, oblíbení samolepky, přepínače v Nastavení, v profilu (ztlumení),
v nastavení archivu, v úpravě skupiny a v Zařízeních, skok mezi výsledky hledání v konverzaci,
tažení po waveformu hlasovky, přepnutí rychlosti přehrávání a chyba po akci uživatele.

Vede se to tady, ne v [roadmap-konverzace.md](roadmap-konverzace.md): je to jeden slovník přes
celou aplikaci a rozdělený na dvě místa by se rozešel. Zbývá:

- **Procházení po dílcích** (`tick`): ~~tažení po waveformu hlasovky~~ — hotovo (tiká po
  sloupcích, ne po pixelech) —, ~~posuvník videa~~ (po dvacetinách délky) a ~~přeskočení
  stránky v prohlížeči médií~~ (jen po tahu prstem) — hotovo; zbývá pruh rychlých reakcí. Je to jediná skupina, kde hrozí, že odezva bude otravná — chce to
  ladit rukou na telefonu, ne od stolu. **Tažení po waveformu ověřené rukou NENÍ**, jen
  z `dumpsys vibrator_manager`.
- **Ověřit rukou na telefonu.** Emulátor nevibruje; z `dumpsys vibrator_manager` se pozná jen,
  že volání odešlo a které (ověřeno: odeslání 16, dlouhý stisk jediná 0, reakce 21,
  přepínač 21/22). Jestli dvě haptiky nejdou nepříjemně po sobě — třeba práh tahu a hned
  potvrzení akce — se od stolu zjistit nedá.
- **Ověřit na starém zařízení, ne na emulátoru** (API 26–33), kde `Confirm`, `Reject`,
  `ToggleOn`/`ToggleOff`, `GestureThresholdActivate` ani `SegmentTick` neexistují
  a `HapticFeedbackConstantsCompat` je nahrazuje bližším typem, nebo neudělá nic. **Emulátor
  s Androidem 9 na to nestačí: nemá vibrátor vůbec** — nezaznamená ani dlouhý stisk launcheru.
  Ověřené je jen to, že tam nic nespadne.
- **iOS ověřit na zařízení.** Sada se tam mapuje na čtyři generátory, takže zapnutí a vypnutí
  přepínače od sebe uživatel nerozezná; jestli to vadí, se pozná až rukou.
- **Vlastní přepínač v Nastavení** zatím ne — vypnout odezvu jde v systému. Přidat, až o něj
  někdo řekne.

## Vzhled (Material 3 Expressive)

Nápady na doladění vzhledu; nic z toho zatím nikdo nezačal. Navazují na slovník, který
aplikace už má (`CLAUDE.md`, „Material 3 / vzhled" a `ShapeMorphs`): tvar nese stav, vlnka
znamená „teď zní", terciární role „živé". Seřazené od nejlepšího poměru přínosu a práce.
Hotové jsou FAB menu, vlnitý posuvník videa, tvary tlačítek při stisku, přetáčení počtů a přepínače s ikonou (poučení v `CLAUDE.md`).

**Malé**

- ~~**Tlačítka v hlavičkách měknou při stisku**~~ — hotovo pro všechna tlačítka s ikonou
  (`IconButtonDefaults.shapes()`, VladimirTintera/telegram#498).
- ~~**Počet nepřečtených se přetočí**~~ — hotovo (`RollingNumber`, VladimirTintera/telegram#500):
  odznaky, počty u složek a archivu, „N nových zpráv".
- ~~**Přepínače s ikonou**~~ — hotovo (`IconSwitch`: fajfka / křížek v jezdci, všechny
  přepínače v aplikaci).

**Střední**

- ~~**FAB „Nová zpráva" jako `FloatingActionButtonMenu`**~~ — hotovo: Nová zpráva, Nová
  skupina a Nový kanál (Kontakty ne — Nová zpráva je seznam kontaktů), VladimirTintera/telegram#496.
- ~~**Vlnitý posuvník videa, dokud hraje**~~ — hotovo (VladimirTintera/telegram#497).
- ~~**Tvarová odezva na prahu tahu**~~ — hotovo: archivace i odpověď tahem, kolečko za prahem
  naroste do sušenky a vyplní se (`SwipeThresholdBadge`).
- **Reakce s výbuchem**: za emoji se na okamžik otevře `SoftBurst` (živý signál) a čip pružně
  povyskočí. Ne konfety — ty patří výhře v kostce.
- **Expressive `LoadingIndicator` místo kolečka** tam, kde kolečko zbylo (dotahování historie,
  první načtení seznamu).

**Větší (spíš funkce než detail)**

- **Výběr víc chatů v seznamu** s plovoucí lištou jako výběr zpráv v konverzaci
  (`HorizontalFloatingToolbar`): archivovat, ztlumit, přečíst několik chatů naráz.
- **Vlastní barva motivu** v Nastavení → Vzhled: řada vzorků ve tvarech `MaterialShapes`, jako
  „Tapeta a styl" na Pixelu. Na iOS a Androidu bez Material You je barva dnes napevno; paletu
  ze zvolené barvy už umí materialkolor (`nonDynamicThemeMain`).
- **Nápovědy u gest napoprvé** (`RichTooltip` se šipkou): odkrývání časů tahem, podržení pro
  efekt, podržení pro hlasovku. Gesta dnes nikdo neobjeví, dokud o nich neví.

---

## Nastavení

Nastavení je list-detail: vlevo sekce (Obecné, Notifikace, Vzhled, Zařízení), vpravo jejich obsah. Obecné drží kartu
účtu (jméno, telefon, uživatelské jméno) a odhlášení; Notifikace oprávnění a volby po rozsazích; Vzhled motiv aplikace a vyčlenění nepřečtených chatů do vlastního bloku.

- ~~**Úprava vlastního profilu**~~ — hotovo (fotka, jméno, uživatelské jméno s kontrolou
  dostupnosti, bio; `CLAUDE.md`, „Úprava profilu"). ~~Historie profilových fotek~~ — hotovo v profilu chatu i u vlastního účtu (`CLAUDE.md`, „Historie profilových fotek"), u vlastního i s nastavením starší fotky jako hlavní a smazáním jednotlivé fotky. ~~Ruční ořez
  fotky~~ — hotovo (`CLAUDE.md`, „Ořez profilové fotky"). ~~Video jako profilovka~~ — hotovo
  (prvních 10 s bez zvuku, výřez nad prvním snímkem; `CLAUDE.md`, „Video jako profilovka"). Zbývá
  volba úseku videa a snímku pro statickou podobu (dnes vždy začátek) a ověření na iOS.
- ~~**Sekce Notifikace**~~ — hotovo, viz [Notifikace](#notifikace) níž.
- ~~**Sekce Zařízení**~~ — hotovo (seznam, odhlášení, weby, automatické odhlášení, „Byli jste
  to vy?" v seznamu chatů, přihlášení jiného zařízení QR kódem). Poučení v `CLAUDE.md`,
  „Zařízení". Zbývá:
  - **Ověřit přihlášení QR kódem na zařízení** — na emulátoru se spustí jen čtečka; potvrzení
    (`confirmQrCodeAuthentication`) proti skutečnému kódu z Telegram Desktop / webu zatím nikdo
    neviděl. Na iOS se čtečka dá vyzkoušet jen na telefonu, simulátor kameru nemá.
  - **Připojení boti (Telegram Business)** v seznamu relací ani v kartě „Byli jste to vy?" nejsou.
- ~~**Proxy**~~ — hotovo (Obecné → Připojení → Proxy, odkazy `t.me/proxy`, přihlašovací
  obrazovka, stav v řádku a sponzorovaný chat; `CLAUDE.md`, „Proxy").
- ~~**Řádky sekcí nemají ikony.**~~ — hotovo (`SettingsSection.icon`).

## Aktualizace z obchodu

Karta v seznamu chatů a Play In-App Updates na Androidu jsou hotové (poučení v `CLAUDE.md`,
„Aktualizace z obchodu"). Zbývá:

- **Ověřit celou cestu přes Play.** Debug build aktualizaci nenajde, takže stahování, průběh
  v kartě a restart zatím nikdo neviděl. Postup: internal app sharing, nainstalovat verzi
  s nižším `versionCode`, pak nahrát vyšší a otevřít seznam chatů. Vzhled karty ověřený je
  (podstrčeným stavem na emulátoru).
- **iOS.** API na aktualizace nemá. Až bude aplikace v App Store, dá se verze zjistit dotazem na
  `itunes.apple.com/lookup?bundleId=…` a karta otevře stránku aplikace v App Store. Dnes
  `AppUpdates` na iOS vrací napořád `None`.

## Notifikace

- ~~**Kanály podle druhu chatu a sekce Notifikace v Nastavení**~~ — hotovo (kanály Soukromé
  chaty / Skupiny / Kanály / Zmínky, u každého rozsahu zapnutí, náhled, připnuté, zmínky, výjimky
  a odkaz do systémového kanálu; poučení v `CLAUDE.md`, „Kanály a nastavení notifikací"). Zbývá:
  - ~~**Ověřit kanály Skupiny, Kanály a Zmínky skutečnou zprávou**~~ — ověřeno testovacím botem
    v testovací skupině a kanálu (při tom opravené přidání bota do kanálu jako správce).
  - ~~**Upozornění jednoho chatu v profilu**~~ — hotovo (doba ztlumení, náhled, připnuté,
    zmínky, obnovení, zvuk přes kanál konverzace) a doby ztlumení i v nabídce chatu v seznamu.
  - **Stories** (`muteStories`, `showStoryPoster`), až budou stories.
  - **Zvuk z Telegramu** (`notificationSoundId`) se nesynchronizuje. Šlo by to jen novým kanálem
    s verzí v id při každé změně (jako oficiální klient) — a tím zahodit, co si uživatel nastavil
    v systému. Stojí za to, až bude v aplikaci výběr zvuku.
  - **iOS** kanály nemá; obdobou je `threadIdentifier` a `interruptionLevel` (zmínky jako
    `timeSensitive`). Přijde na řadu s notifikacemi na iOS vůbec.
- **Rychlé akce z notifikace.** Odpověď, označení za přečtené a ztlumení na hodinu jsou na
  Androidu hotové (viz CLAUDE.md, „Akce z notifikace"). Chybí:
  - **iOS** — akce vyplní rozšíření notifikací; ověřit skutečným pushem (viz iOS výš).
