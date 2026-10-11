# Plume — co dodělat

Živý dokument přes celou aplikaci. Nové nápady sem, hotové věci ven (a poučení z nich do
`docs/poznatky/`, obecná do `CLAUDE.md`). Pořadí uvnitř sekcí je doporučení, ne závazek.

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
  `docs/poznatky/release.md`, „R8").

## Komunitní překlad

Až bude repo veřejné. Volba: **Hosted Weblate, plán Libre** (zdarma pro veřejné projekty
s licencí schválenou OSI — MIT stačí; limit 160k řetězců, při ~2 200 klíčích tedy desítky
jazyků). Vlastní backend ani GitHub Pages potřeba nejsou. Záloha: Crowdin for Open Source.

- **Formát „Compose Multiplatform resources"**, ne „Android string resources" (Weblate ≥ 5.12).
  Liší se escapováním: Android formát by psal `\'` a to Compose Multiplatform nerozbalí
  (viz „Jazyky" v CLAUDE.md). Maska `client/shared/src/commonMain/composeResources/values-*/strings.xml`,
  základ `values/strings.xml`.
- **Schvalování**: hlasování o návrzích s prahem pro automatické přijetí, nebo vyhrazení
  schvalování recenzentům (u nových jazyků si vybrat rodilého mluvčího). Čeština zůstává
  v repu a na Weblate jen pro čtení, dokud ji píšeme sami.
- **Weblate posílá změny jako PR** (doplněk „Squash Git commits" + push do větve `weblate`).
  ~~Check „Release notes" by ho zamítl~~ — hotovo: PR jen s komunitními překlady poznámku
  nepotřebuje (`release_notes.py exempt`, CLAUDE.md „Poznámky k vydání").
- ~~**`verifyTranslations` umí jen `values-cs`.**~~ — hotovo: všechny `values-*`, komunitní
  jazyky chybějící klíč jen varováním, formátovací argumenty chybou u všech (CLAUDE.md, „Jazyky").
- ~~**Nový jazyk se dnes zapisuje ručně na čtyři místa**~~ — hotovo: seznam ze složek `values-*`
  s prahem 80 %, `locales_config.xml` i `APP_LANGUAGES` se generují, názvy jazyků dává systém
  (`docs/poznatky/nastaveni.md`, „Jazyk aplikace"). Zbývá iOS: `<kód>.lproj` se přidává ručně
  (build jen varuje) a `Localizable.strings` z obou `.lproj` jde na Weblate jako další
  komponenta (formát iOS strings).
- **Texty v Play** (`play/listing/`, poznámky k vydání) se komunitně nepřekládají — tam rozhoduje
  `PLAY_LANGS` a jazyk musí mít záznam v obchodě.

## iOS

Android je napřed; tohle je, čím se to dorovnává.

- **AI v zařízení není vůbec** (`NoopOnDeviceAi`), takže shrnutí chatu ani návrhy odpovědí na
  iOS nejsou. Apple Foundation Models jsou k mání od iOS 26 a dají se zapojit za stávající
  rozhraní `OnDeviceAi` — implementace musí přijít ze Swiftu, jako u hlásiče pádů.
- **Notifikace** — napojené (APNs, rozšíření notifikací, texty, oprávnění, úklid přečtených
  a odznak; `docs/poznatky/notifikace.md`, „Notifikace na iOS (APNs)"). Zbývá:
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

## Desktop

Běží na macOS (ověřeno: přihlášení QR kódem, seznam chatů, konverzace, odeslání zprávy).
Poznatky v `docs/poznatky/desktop.md`. Co chybí, zhruba podle důležitosti:

- **Ověřit Windows a Linux.** Přeložené a zabalitelné, ale nespuštěné. Linux chce glibc 2.38+.
- **TDLib wrapper: počkat na opravu [tdl-coroutines#163](https://github.com/g000sha256/tdl-coroutines/issues/163)**
  a pak z přepisu jaru vyhodit opravu dylibu a přibalené OpenSSL. Do té doby se na macOS
  staví jen s Homebrew `openssl@3` a desktop na Apple Silicon běží až od macOS 26 (`minos`
  binárky wrapperu — taky v issue).
- **Notifikace: ověřit Windows a Linux** (AWT `TrayIcon`, na Linuxu podle prostředí —
  lepší by byl `org.freedesktop.Notifications` přes D-Bus), odznak v Docku a zavření okna
  s návratem z Docku. Akce v oznámení (Odpovědět, Přečteno) zatím nejsou; na macOS by šly
  přes `UNNotificationCategory`. Na Windows a Linuxu zavření okna aplikaci ukončí — chce
  ikonu v oznamovací oblasti.
- **Zvuk**: přehrávání hlasovek (Opus) a hudby, nahrávání hlasovek. JVM sám Opus ani MP3
  neumí; kandidáti jsou nativní libopus přes JNA, nebo FFmpeg (JavaCV) pro všechno naráz.
- **Video**: přehrávač v aplikaci (vlcj chce VLC v systému, JavaCV nese FFmpeg). Dnes se video
  otevírá v aplikaci systému.
- **Trezor tajemství na Windows a Linuxu** (DPAPI, Secret Service / libsecret). Dnes soubor
  s právy jen pro vlastníka vedle databáze.
- **Vkládání obrázků ze schránky a přetažení souboru do okna** (`KeyboardContentReceiver`,
  AWT drag & drop).
- **Release pro macOS a Linux** (Windows MSI už je, `desktop-release.yml`): job na
  `macos-latest` s Homebrew `openssl@3` (DMG) a na Ubuntu (DEB). macOS chce verzi od 1.0.0.
- **Podpis instalátorů**: Windows (certifikát pro podepisování kódu, jinak SmartScreen),
  macOS podpis Developer ID a notarizace. Bez nich systém instalátor při prvním spuštění zastaví.
- **Aktualizace desktopu**: aplikace o nové verzi neví; šlo by to přes GitHub Releases API.
- **Jazyk aplikace jen pro aplikaci** (dnes jde za jazykem systému).
- **Úprava fotky, profilové video, mini aplikace (WebView), poloha, QR čtečka, kalendář,
  kontakty** — vše `actual`, které dnes říká „neumí".
- **Desktopové chování**: zkratky z nabídky okna, pamatovaná velikost okna, tray ikona,
  jedna instance aplikace.

## Obrazovky a sekce, které nejsou

Sekce jsou dnes dvě: Chaty a Nastavení (z menu účtu, rail ani lišta nejsou). Routy: `ChatsRoute`, `SettingsRoute`,
`SettingsSectionRoute`, `ArchiveRoute`, `ArchiveConversationRoute`, `ArchiveChatProfileRoute`,
`ConversationRoute`, `ChatProfileRoute`, `MediaRoute`, `ForwardRoute`, `ShareRoute`, `NewChatRoute`
(`core/navigation/Routes.kt`).

- ~~**Přihlášení: zapomenuté heslo je slepá ulička.**~~ — hotovo (kód na obnovovací e-mail,
  nové heslo s nápovědou, dialog u účtu bez e-mailu; `docs/poznatky/prihlaseni.md`, „Obnova hesla a cesta zpátky
  k číslu"). Zbývá:
  - **Ověřit se skutečným účtem** s heslem a obnovovacím e-mailem — zatím jen podstrčenou session.
  - **Reset účtu bez obnovovacího e-mailu** (`deleteAccount` s prázdným heslem, provede se po
    týdnu). Dnes dialog posílá do oficiální aplikace. Než se postaví: jak TDLib hlásí čekání
    (server má `2FA_CONFIRM_WAIT_…`), v jakém stavu automat zůstane a co ukázat, když se uživatel
    během týdne vrátí. Ověřit se to na testovacím účtu nedá.
  - **Telegram Passport** (`hasPassportData`): jestli obnova hesla uložené údaje smaže a má se
    to říct předem, ověřené není.

- ~~**Archiv**~~ — hotovo (seznam v menu účtu, archivace a vrácení z nabídky chatu i tahem do
  strany s „Vrátit", počet nepřečtených u vstupu, nastavení archivu). Poučení v `docs/poznatky/seznam-chatu.md`,
  „Archiv". Zbývá:
  - **Ověřit nastavení archivu proti druhému účtu.** Že se „archivovat od neznámých" a
    „nechávat chaty v archivu" opravdu projeví, se s jedním účtem nedá vidět — zapsání
    i přečtení hodnot ověřené je, jejich následek ne.
- ~~**Detail chatu / profil.**~~ — hotovo (ťuknutí na hlavičku: fotka, jméno, ztlumení, hledání,
  údaje, členové skupiny; `docs/poznatky/profil-chatu.md`, „Profil chatu"). Zbývá:
  - ~~**Sdílená média**~~ — hotovo (záložky Členové / Média / Soubory / Odkazy s dotahováním
    stránek; `docs/poznatky/profil-chatu.md`, „Sdílená média v profilu"). ~~Hledání uvnitř záložky~~ — hotovo
    (soubory, odkazy, hudba). ~~Velký náhled u souborů~~ — hotovo (obrázek a video
    poslané jako soubor se ze záložky Soubory otevřou v prohlížeči).
  - ~~**Akce navíc**~~ — hotovo: kopírovat odkaz (i pozvánku pro správce), zablokovat, smazat
    chat / opustit skupinu či kanál (⋮ v liště) a ~~**upravit skupinu**~~ (název, popis, fotka,
    práva členů; `docs/poznatky/profil-chatu.md`, „Úprava skupiny") a ~~**správa jednotlivých členů**~~ (dlouhý
    stisk na členovi: povýšit na správce a upravit jeho práva, sesadit, omezit s dobou, zrušit
    omezení, štítek, vyhodit s dobou, předat vlastnictví; `docs/poznatky/profil-chatu.md`, „Správa členů").
    ~~Sdílet kontakt~~ — hotovo (⋮ → Sdílet kontakt). ~~Správa pozvánek~~ — hotovo (odkazy
    s limitem, platností a schvalováním, zrušení a mazání, žádosti o vstup; `docs/poznatky/profil-chatu.md`,
    „Pozvánky a žádosti o vstup"; sdílení odkazu, odkazy ostatních správců, kdo se přidal
    a pruh s čekajícími žádostmi v konverzaci jsou hotové). U pozvánek zbývá **ověřit
    skutečnou žádost o vstup a „kdo se přidal" se skutečnými lidmi** (chce druhý účet).
    ~~Uložení úpravy skupiny, práv člena, omezení, vyhození a štítku~~ — ověřené v testovací
    skupině s botem. Zbývá **dokončený převod vlastnictví**: bot vlastníkem být nemůže
    a převod vyžaduje dvoufázové ověření, které testovací účet nemá.
  - ~~**Seznam členů velké skupiny**~~ — hotovo (stránky po 50, hledání, věta u skupiny se
    skrytými členy; `docs/poznatky/profil-chatu.md`, „Členové velké skupiny"). **Zbývá ověřit ve skupině, kde členy
    vidět jsou** — testovací účet v takové velké není; ověřené je to sondou s malou stránkou.
- ~~**Pořadí připnutých chatů**~~ — hotovo: „Změnit pořadí připnutých" v nabídce nad připnutým
  chatem, tažení za úchyt, v hlavním seznamu, archivu i složce (`docs/poznatky/seznam-chatu.md`, „Akce nad chatem").
- ~~**Složky chatů**~~ — zobrazení i správa hotové (řádek pod pilulkou, přepínání, nepřečtené;
  Nastavení → Obecné → Složky chatů: založit, upravit, smazat, přeřadit, doporučené; „Přidat do
  složky" nad chatem; `docs/poznatky/seznam-chatu.md`, „Složky chatů"). Zbývá:
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
  v `docs/poznatky/hledani-a-zakladani-chatu.md`, „Hledání chatů"). Zbývá, po krocích:
  1. ~~**Globální hledání a zprávy**~~ — hotovo (sekce Globálně a Zprávy, skok na nalezenou
     zprávu, „Přidat se" u veřejného chatu). Zbývá:
     - ~~**Filtry hledání zpráv**~~ — hotovo (čipy druhu chatu a období nad výsledky).
     - ~~**Jméno odesílatele u nalezené zprávy ve skupině**~~ — hotovo („Jana: …", „Vy: …").
     - ~~**Kanál po „Přidat se" pořád nabízí vstupní pole**~~ — hotovo: kanál bez práva
       publikovat má místo pole Ztlumit (`docs/poznatky/konverzace-psani.md`, „Práva psát").
  2. ~~**Klikatelné zmínky a hashtagy v textu**~~ — hotovo (`@jméno` otevře chat, `#tag`
     hledání zpráv). Odkazy na Telegram (chat, zpráva, pozvánka, sada samolepek) se otevírají
     v aplikaci (`docs/poznatky/konverzace-zobrazeni.md`, „Odkazy na Telegram"); ~~boti se `start`, mini aplikace, sdílené
     složky, člověk podle čísla a kontaktního odkazu, `?text=` a `?profile`, `t.me/share`,
     Uložené zprávy, proxy (`t.me/proxy`, `t.me/socks`)~~ hotové. ~~Odkazy do Nastavení
     (`tg://settings/…`), nová konverzace, skupina a kanál, kontakty, hledání, videochat
     a přihlašovací kódy~~ — hotovo (přihlašovací odkazy se jen vysvětlí, neotevřou). Do
     prohlížeče dál jdou platby, stories, dárky, témata vzhledu a jazykové balíčky. ~~**Veřejné příspěvky
     s tagem**~~ — hotovo (přepínač „Vaše chaty / Veřejné příspěvky" u hashtagu).
  3. ~~**Hledání v konverzaci**~~ — hotovo (lupa v hlavičce, šipky mezi výsledky, viz
     `docs/poznatky/konverzace-pohyb.md`, „Hledání v konverzaci"). Hledané slovo je v bublinách zvýrazněné.
     Ve skupině jde hledat i podle člena (čip v hlavičce hledání).
  4. ~~**Filtry obsahu** (média, soubory, odkazy), hlavně pro profil chatu~~ — hotovo (záložky
     profilu s hledáním). Zbývá hledání médií podle popisku, kdyby o něj byl zájem.

  ~~Na rozloženém Foldu by místo celoobrazovkového hledání sedělo `ExpandedDockedSearchBar`~~ —
  hotovo (panel pod pilulkou, `docs/poznatky/hledani-a-zakladani-chatu.md`, „Hledání chatů“); ~~zpět s otevřenou klávesnicí
  zavřel celé hledání~~ — opraveno (napřed schová klávesnici).
- ~~**Nová konverzace a kontakty.**~~ — hotovo (FAB „Nová zpráva“ v seznamu chatů,
  `NewChatRoute`, Uložené zprávy, kontakty z TDLib s dostupností, globální vyhledávání lidí podle
  @username; poučení v `docs/poznatky/hledani-a-zakladani-chatu.md`, „Nová konverzace").
  ~~**Založení skupiny a kanálu**~~ — hotovo („Nová skupina" / „Nový kanál" nad kontakty, výběr
  členů, název a fotka, u kanálu popis, ~~soukromý / veřejný s odkazem~~ a ~~pozvánka lidem,
  které server nepustil~~ a ~~veřejný odkaz v úpravě chatu~~; `docs/poznatky/hledani-a-zakladani-chatu.md`, „Založení skupiny
  a kanálu" a „Veřejný odkaz v úpravě chatu"). Zbývá ověřit pozvánku se skutečným člověkem,
  který přidávání zakazuje (dialog jen sondou). ~~Veřejná základní skupina~~ — hotovo
  (převod na supergroupu, `docs/poznatky/profil-chatu.md`, „Převod základní skupiny").
- **Ochrana před spamem.** ~~Zablokovat z nabídky nad chatem a při mazání rovnou zablokovat
  i nahlásit~~, ~~průvodce nahlášením s volbou důvodu~~ (i zprávy z nabídky nad zprávou),
  ~~pruh nad konverzací u chatu od neznámého~~ — hotovo (`docs/poznatky/spam-a-blokovani.md`, „Blokování a nahlášení
  spamu"). Zbývá:
  - ~~**„Vrátit mezi chaty" v pruhu**~~ — hotovo (`docs/poznatky/spam-a-blokovani.md`, „Pruh nad konverzací").
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
  expressive přestavba, ~~zpět z kódu a hesla k číslu~~, ~~text polí v Compose stavu místo
  `StateFlow`~~; `docs/poznatky/prihlaseni.md`, „Přihlašovací obrazovka").
- **Sdílení ze systému do aplikace.** ~~Android~~ — hotovo (text, odkaz, jeden i víc
  souborů; výběr chatu, obsah čeká nad vstupním polem). Poučení v `docs/poznatky/sdileni.md`, „Sdílení do
  aplikace". Zbývá:
  - ~~**Direct Share**~~ — hotovo, i na iOS (darování `INSendMessageIntent`, řádek návrhů
    v systémové nabídce). **Na skutečném iPhonu řádek návrhů ověřený není** — simulátor ho
    neukazuje.
  - ~~**Opakované sdílení do otevřené obrazovky „Komu: …" ji zavře**~~ — opraveno (obsah
    vzniká rovnou s chatem, obrazovka zahazuje jen své sdílení; `docs/poznatky/sdileni.md`, „Opakované sdílení").
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
     odmítnutý / zrušený, délka, video; `docs/poznatky/typy-zprav.md`, „Zpráva o hovoru"). Ťuknutí nevede nikam.
     **Skutečný hovor v historii ověřený není** — jen sondou v chatu s botem.
   - ~~**Příchozí hovor ohlásit**~~ — hotovo na Androidu: notifikace v kanálu Hovory
     s „Odmítnout" (`docs/poznatky/notifikace.md`, „Ohlášení příchozího hovoru"). **Skutečný hovor ověřený NENÍ**
     (chce druhý účet), jen sondou. Na iOS ohlásí hovor push od Telegramu sám (`PHONE_CALL_REQUEST`
     v textech pushe); v popředí aplikace se zatím neohlašuje nic.
   - ~~**Hovory ve skupině**~~ — hotovo: pruh „Probíhá videochat" / „Živé vysílání" / naplánovaný
     nad konverzací s počtem účastníků (`docs/poznatky/notifikace.md`, „Pruh videochatu"). Připojit se nejde;
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
z prototypu je v `docs/poznatky/ai-v-zarizeni.md`, sekce „AI v zařízení"; sem patří, co zbývá.

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
  varianta Gemini Nano (Gemma 4 **E4B**) přes ML Kit nejde (`Feature -1`, viz `docs/poznatky/ai-v-zarizeni.md`),
  takže přibyl druhý poskytovatel — Gemma 4 E2B / E4B, kterou si stáhne aplikace
  (`LiteRtOnDeviceAi`). Stažení, navázání po výpadku sítě, smazání, shrnutí, návrhy i release
  build (s keep pravidlem) prošly. Zbývá:
  - **vyzkoušet na Foldu E4B a GPU** a hlavně **porovnat češtinu E4B s Gemini Nano** — kvůli
    tomu to celé je; E2B na emulátoru vyšla kvalitou zhruba jako Nano,
  - **velikost APK**: nativní knihovny přidají ~25 MB na ABI každému, i kde se Gemma nenabídne
    → Play Feature Delivery,
  - ~~**stahování na pozadí**~~ — hotovo (úloha WorkManageru s notifikací průběhu a „Zastavit";
    `docs/poznatky/ai-v-zarizeni.md`, „AI v zařízení"). Zbývá ověřit v release buildu a na telefonu a **vyplnit
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
- ~~**Detekce podvodných zpráv.**~~ — hotovo: karta „Možný podvod" pod zprávou, předfiltr
  pravidly a posouzení modelem (`docs/poznatky/ai-v-zarizeni.md`, „Varování před podvodem").
  Původní zadání: „Naskenuj tenhle QR a ověř si účet", falešná výhra, odkaz na
  přihlášení. Nenápadné varování u zprávy. Je to klasifikace, tedy silná stránka, a **děje se to
  celé v telefonu** — obsah zprávy nikam neodchází, což je u téhle featury podstatné. Navazuje
  na pravidlo, které projekt už má (odkaz `tg://login` se z ťuknutí neotevírá, viz `docs/poznatky/prihlaseni.md`,
  „Přihlášení QR kódem"). Pozor na to, že varování u legitimní zprávy je horší než žádné:
  práh se musí nastavit tak, aby se raději mlčelo.
- **AI ikonka u zprávy s rozpoznaným kontextem** — první kolo hotovo: termín → kalendář nebo
  připomínka, číslo účtu → QR platba a kopírování, adresa → mapy, telefon a e-mail → uložit do
  kontaktů, číslo letu → stav letu a let do kalendáře, Wi-Fi s heslem → připojit a QR kód sítě, kód → zkopírovat,
  cizí měna → převod kurzem ČNB, jednotky druhé soustavy → převod, kód rezervace → zkopírovat,
  číslo zásilky (Česká pošta / S10, Zásilkovna) → sledování, z ikonky i z nabídky nad zprávou; model termín potvrzuje a doplní název a místo (`docs/poznatky/ai-v-zarizeni.md`,
  „Akce ze zpráv"). Zbývá:
  - **ověřit na telefonu**: editor události v kalendáři (emulátor bez účtu Google chce
    přihlášení), načtení QR platby bankovní aplikací, Gemini Nano místo Gemmy, iOS (jen přeložené),
  - **adresa bez jasné stavby** („sraz na Vinohradské 12", víceslovné ulice jako „Karla Engliše
    3") a místo bez termínu („u Kotvy") — pravidla je vědomě nechávají, chtělo by to předfiltr
    a model, jako u termínu,
  - **jméno u kontaktu** jen z jasné stavby („Jan Novák: 602…"); jinak by ho uměl model,
  - **letiště mimo tabulku** (~110 nejběžnějších) a měst mimo seznam českých a anglických
    názvů; u města s víc letišti se bere hlavní (Londýn → Heathrow), pokud zpráva nemá kód;
    správa rezervace u dopravce chce adresu pro každou společnost zvlášť,
  - **Wi-Fi na iOS** jen s kopírováním hesla a QR kódem — připojení chce entitlement Hotspot
    Configuration v profilu aplikace; víceslovný název sítě jen v uvozovkách,
  - **snackbar „Zkopírováno" leží pod sheetem** a vidět není (na Androidu 13+ se neukazuje
    vůbec, potvrdí to systémový náhled schránky; na iOS a starších Androidech nic),
  - **čísla PPL a DPD**: holé číslo od telefonu nerozeznáš, chtělo by to slovo dopravce vedle,
  - **místo od modelu bývá vágní** („letišti", „Kotvy") a mapy s ním neporadí — chtělo by to
    nabízet mapy jen u místa, které vypadá jako adresa nebo název,
  - **QR platbu sdílet jako obrázek** — ve stejném telefonu se z obrazovky nenaskenuje, část
    bankovních aplikací ale umí QR z galerie,
  - **`TextClassifier` / ML Kit Entity Extraction / `NSDataDetector`** místo vlastních pravidel —
    ověřit češtinu (ML Kit Entity Extraction ji v seznamu jazyků podle všeho nemá).
- **Triáž chatů.** Odznak „čeká na odpověď" vs. „jen informace" v seznamu chatů. Rozhodnutí
  ano/ne nad krátkým textem je rychlé (0,2–0,4 s), ale **počítá se pro každý chat zvlášť**,
  takže musí běžet nad načteným seznamem a výsledek se musí kešovat podle id zprávy.
- **Štítky Uložených zpráv.** Témata Uložených zpráv (dělení podle chatu) už jsou, štítky od
  Telegramu (`SavedMessagesTag`, jen s Premium) ne; automatické zatřídění by je nahradilo bez
  serverové podpory a je to zase klasifikace.
- ~~**Přepsat před odesláním**~~ — hotovo (podržení odeslání → „Přepsat…";
  `docs/poznatky/ai-v-zarizeni.md`, „Přepis před odesláním"). Původní zadání:
  („kratší", „zdvořileji", „bez překlepů"). Sloh drhne, ale uživatel
  výsledek VIDÍ a rozhodne — chyba je levná, na rozdíl od našeptávání, kde se text podepisuje
  naslepo. Patří do nabídky nad vstupním polem, ne do automatiky.
- ~~**Vysvětlit dlouhou cizí zprávu**~~ — hotovo („Vysvětlit" v nabídce nad zprávou;
  `docs/poznatky/ai-v-zarizeni.md`, „Vysvětlení dlouhé zprávy"). Původní zadání:
  („o co jde") — totéž co shrnutí, jen nad jednou zprávou,
  takže se vejde do vstupu a sloh vadí míň, protože to čte jen uživatel.

**Na překlad se Nano nehodí a nemá se používat.** ML Kit má vlastní offline Translation API,
stavěné přímo na to; pouštět na překlad jazykový model by bylo horší v kvalitě i v čase.

**Další nápady (říjen 2026)**, vybrané podle téhož pravidla:

- ~~**Hledání přirozeným jazykem.**~~ — hotovo („Hledat s AI" v hledání; `docs/poznatky/hledani-a-zakladani-chatu.md`,
  „Hledání přirozeným jazykem"). Odesílatel („od Jany") zatím ne — globální hledání zpráv ho
  neumí filtrovat; strukturovaný výstup Prompt API taky ne (řádky + pravidla). Původní zadání:
  „Fotky od Jany z loňského léta" → filtry, které hledání už
  umí (chat, druh obsahu, období, odesílatel — `MessageSearchFilter`, hledání podle člena).
  Model nic negeneruje, jen vyplní typ přes `GenerateTypedContentRequest`, takže je to přirozený
  první krok ke **strukturovanému výstupu** výš. Chybu uživatel vidí hned (čipy filtrů nad
  výsledky) a opraví ji ťuknutím. Dotaz bez rozpoznaných filtrů jde dál jako text.
- **„Připomenout mi" nad zprávou.** ~~Bez AI~~ hotovo (nabídka nad zprávou → „Připomenout…",
  naplánované přeposlání do Uložených zpráv; `docs/poznatky/konverzace-akce.md`, „Připomenutí
  zprávy"). ~~Termín z textu jako první volba~~ hotovo pravidly bez modelu (`reminderTimeFromText`,
  česky a anglicky), ~~a když nenajdou nic, model v zařízení~~ (Gemma i Gemini Nano, ověřeno
  s Gemmou na emulátoru). A notifikace připomínky říká „Uložené zprávy: <já>",
  ne „Připomínka" — TDLib ji od jiné zprávy v Uložených neodliší (přeposlaná vlastní zpráva).
- ~~**Kontrola tónu před odesláním**~~ — hotovo v sheetu „Zkontrolovat a přepsat…"
  (`docs/poznatky/ai-v-zarizeni.md`, „Kontrola tónu"); pasivní agrese vědomě ne. Původní zadání:
  („nezní to podrážděně?"). Klasifikace vlastního konceptu,
  nic se nepřepisuje a varování jde přejít. Stejná opatrnost jako u podvodných zpráv: radši
  mlčet než varovat zbytečně. Spouštět jen na vyžádání nebo u delších zpráv, ne na každý úhoz.
- **„Co jsem zmeškal".** Po otevření aplikace přehled nepřečtených chatů seřazený podle toho,
  co čeká na odpověď (rozšíření triáže chatů výš o souhrnnou obrazovku). Běží v popředí, takže
  omezení AICore nevadí; výsledek kešovat podle id poslední zprávy.
- **Popis obrázků** pro čtečku obrazovky a hledání fotek podle obsahu. **Ověřit napřed**, jestli
  Gemma přes LiteRT-LM 0.11.0 přijímá obrázky — Gemini Nano přes Prompt API zatím jen text.
- ~~**Offline překlad přes ML Kit Translation**~~ — hotovo na Androidu (přepínač „Překládat
  v telefonu" v sheetu překladu; `docs/poznatky/konverzace-akce.md`, „Překlad v telefonu").
  ~~Správa stažených balíčků~~ hotovo (Nastavení → Úložiště → Balíčky pro překlad).
  Zbývá **iOS** (Translation framework je jen pro SwiftUI — šlo by přes most ze Swiftu)
  a rozhodnout, jestli **+6–7 MB nativní knihovny**
  na stažení z Play za to stojí (jinak dynamický modul Play Feature Delivery).
- ~~**Lokální přepis řeči v zařízení (Speech-to-Text bez Premium).**~~ — hotovo přes Gemmu 4
  se zvukovým vstupem (`OnDeviceAi.transcribe`; `docs/poznatky/prohlizec-medii.md`, „Přepis
  v telefonu"). ~~Telefony bez Gemmy~~ napsané (systémový `SpeechRecognizer` v zařízení,
  Android 13+), ale **neověřené na skutečném telefonu** — emulátor ho nemá. Zbývá iOS
  (`SFSpeechRecognizer` s `requiresOnDeviceRecognition`)
  a ověření kolečka. ~~Přepis přes restart aplikace~~ hotovo (uložený v nastavení po účtech).
- ~~**Extrakce textu z fotek (OCR / Live Text).**~~ — hotovo (⋮ → „Text z fotky" v prohlížeči:
  zvýraznění řádků na snímku, výběr řádků ťuknutím, panel s výběrem a „Kopírovat vše / výběr";
  `docs/poznatky/prohlizec-medii.md`, „Text z fotky"). Zbývá: iOS (Vision) ověřit na simulátoru
  a release build s R8.

---

## Výkon a paměť

Nástroje a postup jsou v `docs/poznatky/vykon.md` („Výkon a paměť"). První analýza (reporty Compose
kompilátoru, LeakCanary, heap dumpy) únik nenašla a delší
běh ukázal, že se ustálí i native heap. Zbylo:

- **Fully drawn na Foldu** ověřit s první verzí, která `ReportDrawnWhen` nese: měřené
  hodnoty na telefonu jsou zatím jen první snímek.

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
- ~~**Reakce s výbuchem**~~ — hotovo: za emoji přidané reakce se otevře `SoftBurst` a čip
  povyskočí (`ReactionChip`).
- ~~**Expressive `LoadingIndicator` místo kolečka**~~ — hotovo: dotahování historie a první
  načtení seznamu ho už měly, zbylá neurčitá kolečka (tlačítka, náhled nahrávky) taky.

**Větší (spíš funkce než detail)**

- ~~**Výběr víc chatů v seznamu**~~ — hotovo: „Vybrat" v nabídce nad chatem, plovoucí lišta
  s archivací, ztlumením a přečtením (`ChatSelection`), v hlavním seznamu i archivu.
- ~~**Vlastní barva motivu**~~ — hotovo: Nastavení → Vzhled → Barva, osm vzorků kreslených
  z palety, kterou aplikace dostane (`ThemeColorPicker`, `AppSettings.themeColor`).
- ~~**Nápovědy u gest napoprvé**~~ — hotovo: `RichTooltip` se šipkou u odkrývání časů,
  podržení pro volby odeslání a podržení pro nahrávání, každá jednou (`GestureHints`).
- ~~**Barva konverzace**~~ — hotovo: profil chatu → Barva konverzace, tytéž vzorky jako barva
  aplikace a „Podle aplikace". Celá konverzace (i její profil a seznam témat) dostane paletu
  z té barvy, v seznamu chatů ji nese jen odznak nepřečtených (`ChatColorTheme`, `ChatColors`;
  `docs/poznatky/konverzace-zobrazeni.md`, „Barva konverzace"). Místo automatického podbarvení
  z profilové fotky: barvu dává uživatel, takže něco znamená. Zbývá: synchronizace mezi
  zařízeními (spolu s ostatním nastavením) a ověřit iOS.

## Vychytávky

Nápady z října 2026, nic z toho zatím nikdo nezačal.

- ~~**Bubliny konverzací (Android 11+)**~~ — hotovo (`BubbleActivity`, `BubbleShell`;
  `docs/poznatky/notifikace.md`, „Bubliny konverzací"). Profil, přeposlání a jiné chaty
  bublina zatím posílá do plné aplikace; ověřit na skutečném telefonu.
- ~~**Widget na plochu**~~ s připnutými a nepřečtenými chaty — hotovo na Androidu (Glance;
  `docs/poznatky/seznam-chatu.md`, „Widget na ploše"). ~~Fotky chatů~~ a ~~náhled ve výběru
  widgetů~~ — hotovo. Zbývá iOS (WidgetKit, vlastní rozšíření).
- **Fold a klávesnice.** ~~Klávesové zkratky (Ctrl+K hledání, Alt+↑/↓ mezi chaty, Esc zavře
  konverzaci nebo hledání)~~ a ~~Enter odesílá~~ — hotovo (`docs/poznatky/navigace.md`, „Klávesové
  zkratky"; na iOS jen Enter). ~~Přetažení obrázku či souboru z jiné aplikace na vstupní pole~~
  — hotovo na Androidu (`docs/poznatky/konverzace-psani.md`, „Přetažení z jiné aplikace");
  zbývá iOS (`UIDropInteraction`). ~~Seznam zkratek v systémovém přehledu (Meta+/)~~ — hotovo.
- ~~**Přehled rozepsaných konceptů**~~ — hotovo (menu účtu → Koncepty;
  `docs/poznatky/seznam-chatu.md`, „Přehled konceptů"). ~~Smazání konceptu přímo z přehledu~~ —
  hotovo (koš a „Vrátit"). ~~Koncepty v tématech fóra~~ — hotovo. Zbývá: koncepty ve vláknech
  komentářů (TDLib je nehlásí updatem, musely by se hledat jinak).
- ~~**Lokální odložení chatu (Snooze)**~~ — hotovo z nabídky nad chatem (`docs/poznatky/seznam-chatu.md`,
  „Odložení chatu"). ~~Odložení tahem~~ a ~~upozornění v čase vynoření~~ — hotovo. Zbývá iOS
  ověřit (lokální notifikace i tah).
- ~~**Čistič odkazů od sledování (Privacy URL Stripper).**~~ — hotovo (automatické odstraňování
  sledovacích a analytických parametrů `utm_*`, `fbclid`, `gclid`, YouTube/Spotify `si`, TikTok, Twitter/X
  při odesílání, úpravě zprávy i otevírání odkazů, volitelný přepínač v Nastavení → Soukromí a bezpečnost;
  `docs/poznatky/nastaveni.md`, „Čistič odkazů od sledování").
- ~~**Rychlý přepínač mezi aktivními chaty (Quick Switcher v konverzaci).**~~ — hotovo jako
  podržení šipky zpět: nabídka naposledy otevřených chatů a vršku seznamu, ťuknutí chat nahradí
  (`docs/poznatky/konverzace-pohyb.md`, „Rychlý přepínač chatů"). Tah po vstupním poli vědomě ne
  (pere se s posunem kurzoru a výběrem textu). Zbývá ověřit na iOS.

---

## Nastavení

Nastavení je list-detail: vlevo sekce (Obecné, Notifikace, Vzhled, Zařízení), vpravo jejich obsah. Obecné drží kartu
účtu (jméno, telefon, uživatelské jméno) a odhlášení; Notifikace oprávnění a volby po rozsazích; Vzhled motiv aplikace a vyčlenění nepřečtených chatů do vlastního bloku.

- ~~**Úprava vlastního profilu**~~ — hotovo (fotka, jméno, uživatelské jméno s kontrolou
  dostupnosti, bio; `docs/poznatky/nastaveni.md`, „Úprava profilu"). ~~Historie profilových fotek~~ — hotovo v profilu chatu i u vlastního účtu (`docs/poznatky/profil-chatu.md`, „Historie profilových fotek"), u vlastního i s nastavením starší fotky jako hlavní a smazáním jednotlivé fotky. ~~Ruční ořez
  fotky~~ — hotovo (`docs/poznatky/nastaveni.md`, „Ořez profilové fotky"). ~~Video jako profilovka~~ — hotovo
  (prvních 10 s bez zvuku, výřez nad prvním snímkem; `docs/poznatky/nastaveni.md`, „Video jako profilovka"). ~~Volba
  úseku videa a snímku pro statickou podobu~~ — hotovo (pás snímků pod ořezem). Zbývá ověření na iOS.
- ~~**Sekce Notifikace**~~ — hotovo, viz [Notifikace](#notifikace) níž.
- ~~**Sekce Zařízení**~~ — hotovo (seznam, odhlášení, weby, automatické odhlášení, „Byli jste
  to vy?" v seznamu chatů, přihlášení jiného zařízení QR kódem). Poučení v `docs/poznatky/nastaveni.md`,
  „Zařízení". Zbývá:
  - **Ověřit přihlášení QR kódem na zařízení** — na emulátoru se spustí jen čtečka; potvrzení
    (`confirmQrCodeAuthentication`) proti skutečnému kódu z Telegram Desktop / webu zatím nikdo
    neviděl. Na iOS se čtečka dá vyzkoušet jen na telefonu, simulátor kameru nemá.
  - **Připojení boti (Telegram Business)** v seznamu relací ani v kartě „Byli jste to vy?" nejsou.
- ~~**Proxy**~~ — hotovo (Obecné → Připojení → Proxy, odkazy `t.me/proxy`, přihlašovací
  obrazovka, stav v řádku a sponzorovaný chat; `docs/poznatky/databaze-spojeni-proxy.md`, „Proxy").
- ~~**Řádky sekcí nemají ikony.**~~ — hotovo (`SettingsSection.icon`).

**Nastavení, která chybí** (říjen 2026; v kódu ověřeno, že nic z toho není):

- ~~**Soukromí účtu.**~~ — hotovo (Obecné → Soukromí → Soukromí účtu: naposledy viděn, telefon
  a hledání podle čísla, profilovka, bio, narozeniny, odkaz v přeposlaných, hovory, P2P,
  pozvánky do skupin, hlasové zprávy a čas přečtení, s výjimkami; `docs/poznatky/nastaveni.md`, „Soukromí účtu").
  Zbývá **kdo smí psát** (`NewChatPrivacySettings`: jen kontakty / za hvězdy) — jiné API
  a placené zprávy Plume neumí.
- ~~**Dvoufázové ověření**~~ — hotovo (Obecné → Soukromí; `docs/poznatky/nastaveni.md`, „Dvoufázové ověření").
  **Skutečné nastavení hesla a kód z e-mailu ověřené NEJSOU** (průvodci jen podstrčeným
  repository) — ověří je uživatel zapnutím na zařízení, a tím se zároveň dá ověřit převod
  vlastnictví a obnova hesla při přihlášení.
- ~~**Zámek aplikace**~~ — hotovo (PIN, biometrie na Androidu i iOS, zamykání po době neaktivity,
  brzda pokusů, celoplošný `LockScreen`, skrytí v přehledu úloh přes `FLAG_SECURE` při aktivním zámku,
  nastavení v Nastavení → Soukromí; `docs/poznatky/zamek-aplikace.md`).
- ~~**Zámek aplikace: biometrie svázaná se seznamem otisků.**~~ — hotovo (klíč v Keystore se
  `setInvalidatedByBiometricEnrollment`, na iOS `evaluatedPolicyDomainState`;
  `docs/poznatky/zamek-aplikace.md`, „Biometrie"). Zbývá ověřit iOS na zařízení a volitelný
  šestimístný PIN.
- ~~**Automatické stahování médií**~~ — hotovo (Úložiště: úroveň pro Wi-Fi, mobilní data
  a roaming, spořič dat; `docs/poznatky/prohlizec-medii.md`, „Automatické stahování"). Zbývá
  ověřit spořič dat a roaming na telefonu a případně přednačítání videí (dnes se nepřednačítá nic).
- ~~**Úložiště.**~~ — hotovo (Nastavení → Úložiště: zabrané místo po druzích a chatech, mazání
  všude i v chatu, „Uchovávat média", model Gemmy; `docs/poznatky/nastaveni.md`, „Úložiště").
  Zbývá **strop velikosti mezipaměti** (mazat nejstarší nad N GB) a ověřit automatický úklid
  po dni na zařízení.
- ~~**Akce tahem v seznamu chatů pro každou stranu zvlášť**~~ — hotovo (Obecné → Chaty:
  archivovat, přečteno, ztlumit, připnout, nic; `docs/poznatky/seznam-chatu.md`, „Akce tahem").
- ~~**Menší přepínače**~~ — hotovo: rychlá reakce dvojitým ťuknutím, Enter odesílá (fyzická
  klávesnice), velikost písma zpráv, odkazy v aplikaci (Custom Tabs / Safari) a jazyk aplikace
  (`docs/poznatky/nastaveni.md`, „Menší přepínače"). Zbývá:
  - **Jazyk aplikace na Androidu do 12** — systém jazyk pro jednu aplikaci nezná a řádek se tam
    neukazuje. Šlo by to jen přes AppCompat a vlastní uložení.
  - **iOS ověřit na simulátoru** — Safari v aplikaci, otevření systémového Nastavení z řádku
    jazyka, dvojité ťuknutí.
- **Synchronizace nastavení mezi zařízeními** (barva motivu, vyčlenění nepřečtených, rychlost
  hlasovek…) přes CloudStorage mini aplikací (`sendWebAppCustomRequest`). Sonda ověřila, že to
  jde bez otevřené mini aplikace i u bota bez ní. Zbývá založit bota v BotFatheru (uživatel),
  podívat se do oficiálního klienta, jestli se bot nikde neukáže, a postavit to.
  Podrobnosti v `docs/poznatky/nastaveni.md`, „Synchronizace nastavení mezi zařízeními".
- ~~**Diskrétní čtení a psaní (Ghost Mode).**~~ — hotovo (Nastavení → Obecné → Soukromí;
  `docs/poznatky/nastaveni.md`, „Diskrétní čtení"), ~~i poslech hlasovek~~. Mizející média
  vědomě ne. Původní zadání: V Nastavení → Soukromí volba pro potlačení
  automatického odesílání potvrzení o přečtení (`viewMessages`) a indikátoru „Píše..."
  (`sendChatAction`). Zprávy se označí jako přečtené až při odeslání vlastní odpovědi v chatu
  nebo po ručním ťuknutí na plovoucí čip „Označit jako přečtené".

## Aktualizace z obchodu

Karta v seznamu chatů a Play In-App Updates na Androidu jsou hotové (poučení v `docs/poznatky/release.md`,
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
  a odkaz do systémového kanálu; poučení v `docs/poznatky/notifikace.md`, „Kanály a nastavení notifikací"). Zbývá:
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
  Androidu hotové (viz docs/poznatky/notifikace.md, „Akce z notifikace"). Chybí:
  - **iOS** — akce vyplní rozšíření notifikací; ověřit skutečným pushem (viz iOS výš).
