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
`SettingsSectionRoute`, `ArchiveRoute`, `ArchiveConversationRoute`, `ArchiveChatProfileRoute`,
`ConversationRoute`, `ChatProfileRoute`, `MediaRoute`, `ForwardRoute`, `ShareRoute`, `NewChatRoute`
(`core/navigation/Routes.kt`).

- ~~**Archiv**~~ — hotovo (seznam v menu ⋮, archivace a vrácení z nabídky chatu s „Vrátit").
  Poučení v `CLAUDE.md`, „Archiv". Zbývá:
  - **Nastavení archivu** (`ArchiveChatListSettings`): automatická archivace chatů od
    neznámých a to, jestli ztlumené chaty s novou zprávou zůstávají v archivu.
  - **Počet nepřečtených u vstupu do archivu** (`UpdateUnreadChatCount` pro `ChatListArchive`).
  - **Archivovat tahem** po položce seznamu; dnes jen z nabídky po dlouhém stisku.
- ~~**Detail chatu / profil.**~~ — hotovo (ťuknutí na hlavičku: fotka, jméno, ztlumení, hledání,
  údaje, členové skupiny; `CLAUDE.md`, „Profil chatu"). Zbývá:
  - ~~**Sdílená média**~~ — hotovo (záložky Členové / Média / Soubory / Odkazy s dotahováním
    stránek; `CLAUDE.md`, „Sdílená média v profilu"). Zbývá: filtry hledání uvnitř záložky
    a velký náhled u souborů.
  - **Akce navíc**: opustit / smazat chat, zablokovat uživatele, sdílet kontakt, upravit skupinu
    (správce), pozvánka do skupiny.
  - **Seznam členů velké skupiny** se stránkováním a hledáním; dnes prvních 200.
- **Hledání.** ~~Chaty ve všech složkách (lokálně i na serveru), historie hledání a nejčastější
  lidé, totéž ve výběru pro přeposílání a sdílení~~ — hotovo (`ChatSearch`, poučení
  v `CLAUDE.md`, „Hledání chatů"). Zbývá, po krocích:
  1. ~~**Globální hledání a zprávy**~~ — hotovo (sekce Globálně a Zprávy, skok na nalezenou
     zprávu, „Přidat se" u veřejného chatu). Zbývá:
     - **Filtry hledání zpráv** (jen soukromé / skupiny / kanály, rozsah dat) — `searchMessages`
       je umí (`chatTypeFilter`, `minDate`, `maxDate`).
     - **Jméno odesílatele u nalezené zprávy ve skupině**; dnes je vidět jen chat.
     - ~~**Kanál po „Přidat se" pořád nabízí vstupní pole**~~ — hotovo: kanál bez práva
       publikovat má místo pole Ztlumit (`CLAUDE.md`, „Práva psát").
  2. ~~**Klikatelné zmínky a hashtagy v textu**~~ — hotovo (`@jméno` otevře chat, `#tag`
     hledání zpráv). Odkazy na Telegram (chat, zpráva, pozvánka, sada samolepek) se otevírají
     v aplikaci (`CLAUDE.md`, „Odkazy na Telegram"); zbývající druhy (boti se `start`,
     proxy, sdílené složky, přihlašovací kódy…) jdou dál do prohlížeče. Zbývá **veřejné příspěvky s tagem**
     (`searchPublicMessagesByTag`) jako další sekce u hashtagového dotazu.
  3. ~~**Hledání v konverzaci**~~ — hotovo (lupa v hlavičce, šipky mezi výsledky, viz
     `CLAUDE.md`, „Hledání v konverzaci"). Hledané slovo je v bublinách zvýrazněné.
     Ve skupině jde hledat i podle člena (čip v hlavičce hledání).
  4. **Filtry obsahu** (média, soubory, odkazy), hlavně pro profil chatu.

  Na rozloženém Foldu by místo celoobrazovkového hledání sedělo `ExpandedDockedSearchBar`.
  Nastavení má pořád `LargeFlexibleTopAppBar` se stejnou vadou po sbalení jako měly Chaty.
- ~~**Nová konverzace a kontakty.**~~ — hotovo (M3 Extended FAB s popiskem napojený na scroll,
  `NewChatRoute`, Uložené zprávy, kontakty z TDLib s dostupností, globální vyhledávání lidí podle
  @username; poučení v `CLAUDE.md`, „Nová konverzace").
- **Přihlašovací obrazovka** — hotová (výběr země, formátování čísla, návrat z QR na číslo,
  expressive přestavba; `CLAUDE.md`, „Přihlašovací obrazovka"). Zbývá:
  - **Zpět na přihlašovací obrazovce ukončí aplikaci.** Přihlášení není v `NavDisplay`, takže
    z kódu ani hesla nevede cesta zpátky k číslu jinak než dokončením nebo restartem.
- **Sdílení ze systému do aplikace.** ~~Android~~ — hotovo (text, odkaz, jeden i víc
  souborů; výběr chatu, obsah čeká nad vstupním polem). Poučení v `CLAUDE.md`, „Sdílení do
  aplikace". Zbývá:
  - ~~**Direct Share**~~ — hotovo. Na iOS jsou naposledy použité chaty nahoře v listu rozšíření;
    řádek kontaktů v systémové nabídce (darování `INSendMessageIntent`) zatím není.
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

## Nastavení

Nastavení je list-detail: vlevo sekce (Obecné, Vzhled, Zařízení), vpravo jejich obsah. Obecné drží kartu
účtu (jméno, telefon, uživatelské jméno), stav oprávnění k notifikacím s odkazem do systému
a odhlášení; Vzhled motiv aplikace a vyčlenění nepřečtených chatů do vlastního bloku.

- **Úprava vlastního profilu** — fotka, jméno, uživatelské jméno, bio.
- **Sekce Notifikace** — viz [Notifikace](#notifikace) níž.
- ~~**Sekce Zařízení**~~ — hotovo (seznam, odhlášení, weby, automatické odhlášení, „Byli jste
  to vy?" v seznamu chatů, přihlášení jiného zařízení QR kódem). Poučení v `CLAUDE.md`,
  „Zařízení". Zbývá:
  - **Ověřit přihlášení QR kódem na zařízení** — na emulátoru se spustí jen čtečka; potvrzení
    (`confirmQrCodeAuthentication`) proti skutečnému kódu z Telegram Desktop / webu zatím nikdo
    neviděl. Na iOS se čtečka dá vyzkoušet jen na telefonu, simulátor kameru nemá.
  - **Připojení boti (Telegram Business)** v seznamu relací ani v kartě „Byli jste to vy?" nejsou.
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
- **Rychlé akce z notifikace.** Odpověď z notifikace na Androidu je hotová (viz CLAUDE.md,
  „Odpověď z notifikace"). Chybí:
  - **Označit jako přečtené** (`viewMessages` s `forceRead`), notifikaci pak stáhne sama TDLib.
    Stejný příjemce a úloha jako u odpovědi, jen bez textu.
  - **Ztlumit** chat (třeba na hodinu), stejným objektem `ChatNotificationSettings` jako
    v nabídce chatu.
  - **Odpověď na iOS** (`UNTextInputNotificationAction`) — až budou na iOS notifikace vůbec.
