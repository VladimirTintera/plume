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

- **`databaseEncryptionKey` je prázdný.** Patří tam klíč z Android Keystore / iOS Keychain.
  Dokud je prázdný, leží databáze TDLib na disku odšifrovaná.
- **Vlastní ikona notifikace.** Zatím se používá systémová `android.R.drawable.ic_dialog_email`;
  cílově monochromatická ikona v `androidApp/res/drawable`.
- **První AAB musí do Play nahrát člověk.** Dokud aplikace nemá žádný release, androidpublisher
  API upload odmítne a krok `upload-google-play` v release workflow se nepoužije.
- **`isShrinkResources` není zapnuté.** Je to samostatný krok od minifikace a umí uklidit
  composeResources; zapínat se má zvlášť, aby bylo poznat, co případný pád způsobilo.

## iOS

Android je napřed; tohle je, čím se to dorovnává.

- **Notifikace.** TDLib `registerDevice` + notification service extension. Firebase v Xcode
  projektu zatím vůbec není.
- **Hlášení pádů.** `NoopCrashReporter`. Implementace musí přijít ze Swiftu — `FirebaseCrashlytics`
  je ObjC knihovna, kterou Kotlin/Native nevidí, a Kotlin výjimka nese jen adresy stack trace.
  Vzor je `IosCrashlyticsLogger` v projektu com.profilog.telemetry.android.
- **Release link do CI jako samostatný cachovaný krok.** Kotlin/Native pouští LTO nad celou
  statickou TDLib a naměřeno je 15+ minut na M-series; do smyčky, kterou čeká vývojář, to nepatří.

## Obrazovky a sekce, které nejsou

Sekce v railu/baru jsou dnes dvě: Chaty a Nastavení. Routy: `ChatsRoute`, `SettingsRoute`,
`ArchiveRoute`, `ConversationRoute`, `MediaRoute`, `ForwardRoute`, `ShareRoute`.

- ~~**Archiv**~~ — hotovo (seznam v menu ⋮, archivace a vrácení z nabídky chatu s „Vrátit").
  Poučení v `CLAUDE.md`, „Archiv". Zbývá:
  - **Nastavení archivu** (`ArchiveChatListSettings`): automatická archivace chatů od
    neznámých a to, jestli ztlumené chaty s novou zprávou zůstávají v archivu.
  - **Počet nepřečtených u vstupu do archivu** (`UpdateUnreadChatCount` pro `ChatListArchive`).
  - **Archivovat tahem** po položce seznamu; dnes jen z nabídky po dlouhém stisku.
- **Detail chatu / profil.** Není kam ťuknout na hlavičku konverzace — chybí společná média,
  členové skupiny, popis, ztlumení a hledání v chatu na jednom místě.
- **Hledání.** ~~Chaty ve všech složkách (lokálně i na serveru), historie hledání a nejčastější
  lidé, totéž ve výběru pro přeposílání a sdílení~~ — hotovo (`ChatSearch`, poučení
  v `CLAUDE.md`, „Hledání chatů"). Zbývá, po krocích:
  1. ~~**Globální hledání a zprávy**~~ — hotovo (sekce Globálně a Zprávy, skok na nalezenou
     zprávu, „Přidat se" u veřejného chatu). Zbývá:
     - **Filtry hledání zpráv** (jen soukromé / skupiny / kanály, rozsah dat) — `searchMessages`
       je umí (`chatTypeFilter`, `minDate`, `maxDate`).
     - **Jméno odesílatele u nalezené zprávy ve skupině**; dnes je vidět jen chat.
     - **Kanál po „Přidat se" pořád nabízí vstupní pole**, stejně jako každý kanál — práva
       psát konverzace zatím neřeší vůbec.
  2. ~~**Klikatelné zmínky a hashtagy v textu**~~ — hotovo (`@jméno` otevře chat, `#tag`
     hledání zpráv). Zbývá **interní `t.me` odkazy** (pozvánka, odkaz na zprávu, `t.me/jméno`)
     otevírat v aplikaci místo prohlížeče a **veřejné příspěvky s tagem**
     (`searchPublicMessagesByTag`) jako další sekce u hashtagového dotazu.
  3. **Hledání v konverzaci** (lupa v hlavičce, šipky mezi výsledky) přes `searchChatMessages`.
  4. **Filtry obsahu** (média, soubory, odkazy), hlavně pro profil chatu.

  Na rozloženém Foldu by místo celoobrazovkového hledání sedělo `ExpandedDockedSearchBar`.
  Nastavení má pořád `LargeFlexibleTopAppBar` se stejnou vadou po sbalení jako měly Chaty.
- **Nová konverzace a kontakty.** Napsat se dá jen tomu, s kým už chat existuje.
- **Sdílení ze systému do aplikace.** ~~Android~~ — hotovo (text, odkaz, jeden i víc
  souborů; výběr chatu, obsah čeká nad vstupním polem). Poučení v `CLAUDE.md`, „Sdílení do
  aplikace". Zbývá:
  - ~~**Direct Share**~~ — hotovo. Na iOS jsou naposledy použité chaty nahoře v listu rozšíření;
    řádek kontaktů v systémové nabídce (darování `INSendMessageIntent`) zatím není.
  - ~~**iOS: Share Extension**~~ — hotovo (výběr chatu v rozšíření, App Group). Zbývá
    ověřit na zařízení (App Group v Developer účtu) a ikona aplikace, bez které má Plume
    v nabídce sdílení zástupnou ikonu.
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

## Nastavení

Nastavení je list-detail: vlevo sekce (Obecné, Vzhled), vpravo jejich obsah. Obecné drží kartu
účtu (jméno, telefon, uživatelské jméno), stav oprávnění k notifikacím s odkazem do systému
a odhlášení; Vzhled motiv aplikace a vyčlenění nepřečtených chatů do vlastního bloku.

- **Úprava vlastního profilu** — fotka, jméno, uživatelské jméno, bio.
- **Sekce Notifikace** — viz [Notifikace](#notifikace) níž.
- **Sekce Zařízení** (aktivní relace). TDLib i wrapper umí všechno, co je potřeba:
  - **seznam**: `getActiveSessions` → `Sessions` (+ `inactiveSessionTtlDays`); `Session` nese
    `isCurrent`, `deviceType` (ikona: Android, iPhone, Mac, Chrome…), aplikaci a verzi,
    `isOfficialApplication`, model, systém, `logInDate`, `lastActiveDate`, IP a polohu.
    Aktuální zařízení nahoře zvlášť, ostatní podle poslední aktivity;
  - **odhlášení**: `terminateSession(id)` a „Odhlásit ostatní" (`terminateAllOtherSessions`),
    obojí s potvrzením. Z čerstvě přihlášeného zařízení (asi 24 h) server odmítne s
    `FRESH_RESET_AUTHORISATION_FORBIDDEN` — přeložit v `userMessage` na vlastní větu, jinak
    tlačítko vypadá rozbitě;
  - **automatické odhlášení nečinných** (`setInactiveSessionTtl`) a přepínače hovorů / tajných
    chatů u zařízení (`toggleSessionCanAcceptCalls`, `…SecretChats`);
  - **„Byl jsi to ty?"** — nové přihlášení chodí jako `UpdateUnconfirmedSession`; karta
    s Potvrdit (`confirmSession`) / Odhlásit (`terminateSession`);
  - **weby přihlášené přes Telegram**: `getConnectedWebsites`, `disconnectWebsite`,
    `disconnectAllWebsites`;
  - **přihlásit jiné zařízení QR kódem** (Desktop, web): naskenovaný `tg://login?token=…` jde do
    `confirmQrCodeAuthentication(link)`. Samo volání je triviální, práce je čtečka v aplikaci —
    kamera + dekodér (Android CameraX s ML Kitem nebo zxing, který už máme na generování;
    iOS `AVCaptureMetadataOutput`) a oprávnění ke kameře (iOS `NSCameraUsageDescription`).
    Odkaz rozpozná i `getInternalLinkType` (`InternalLinkTypeQrCodeAuthentication`), takže
    by šel zachytit i z ťuknutí na odkaz.
- **Sekce Obecné nemá skupiny.** Vzhled je má (`SettingsGroup`), Obecné zatím ne — až v něm budou
  víc než tři položky, patří do skupin taky.
- **Řádky sekcí nemají ikony.** Dva řádky se poznají i tak, s další sekcí to začne být znát;
  chybí k tomu Material Symbols v `composeResources`.

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

- **Kanály podle druhu chatu.** Dnes je jediný kanál `messages`, takže v systému nejde ztišit
  skupiny a nechat zvonit soukromé zprávy. Návrh: Soukromé zprávy / Skupiny / Kanály podle
  rozsahu TDLib a samostatný kanál Zmínky (`NotificationGroupType.Mentions`). TDLib o kanálech
  neví nic, jen dává data k rozdělení. Kanál je po vytvoření neměnný, proto se zvuk z Telegramu
  zatím nesynchronizuje a nechává se systému. Rozbor: [notifikacni-kanaly.md](notifikacni-kanaly.md).
- **Sekce Notifikace v Nastavení.** Dnes o notifikacích rozhoduje jen TDLib a aplikace k tomu
  nemá jedinou volbu. Všechno níž jde přes TDLib a synchronizuje se přes server na ostatní
  zařízení. Obrazovka se skládá po rozsazích (soukromé / skupiny / kanály), každý s vlastními
  volbami a pod nimi odkaz „Zvuk a vibrace" do SVÉHO systémového kanálu
  (`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`). Proto patří dohromady s kanály výš.
  - zapnutí rozsahu (`muteFor`) a náhled textu (`showPreview`), `setScopeNotificationSettings`,
  - notifikace o připnutých zprávách (`disablePinnedMessageNotifications`) a o zmínkách
    (`disableMentionNotifications`); zmínky projdou i ztlumeným chatem,
  - stories (`muteStories`, `showStoryPoster`), až budou stories,
  - **výjimky**: chaty s vlastním nastavením (`getChatNotificationSettingsExceptions`) — jediné
    místo, kde je vidět, co kde je ztlumené,
  - „kontakt se připojil k Telegramu" (volba `disable_contact_registered_notifications`),
  - oprávnění k notifikacím s odkazem do systému se sem přestěhuje z Obecných.

  Zvuky a vibrace vlastní volbou NEBUDOU: na Androidu je vlastní kanál a aplikace je po jeho
  vytvoření stejně přepsat nemůže (viz rozbor kanálů).
