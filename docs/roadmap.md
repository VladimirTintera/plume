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
  - **Přeposílání a sdílení nabízí jen hlavní seznam**; archivovaný chat v nich nenajdeš,
    dokud nebude serverové hledání.
- **Detail chatu / profil.** Není kam ťuknout na hlavičku konverzace — chybí společná média,
  členové skupiny, popis, ztlumení a hledání v chatu na jednom místě.
- **Hledání.** Pilulka v hlavičce seznamu chatů hledá zatím jen v načteném seznamu podle názvu.
  Chybí serverové hledání chatů (`searchChats`), zpráv (`searchMessages`, `searchChatMessages`)
  i veřejných (`searchPublicChat`); výsledky patří do rozbaleného `ExpandedFullScreenSearchBar`.
  Na rozloženém Foldu by místo celoobrazovkového hledání sedělo `ExpandedDockedSearchBar`.
  Nastavení má pořád `LargeFlexibleTopAppBar` se stejnou vadou po sbalení jako měly Chaty. Váže na sebe víc věcí: hledání v přeposílání jde dnes jen po načteném
  seznamu chatů a zmínky s hashtagy se v textu schválně nemapují, protože kliknutí nemá kam vést.
- **Nová konverzace a kontakty.** Napsat se dá jen tomu, s kým už chat existuje.
- **Sdílení ze systému do aplikace.** ~~Android~~ — hotovo (text, odkaz, jeden i víc
  souborů; výběr chatu, obsah čeká nad vstupním polem). Poučení v `CLAUDE.md`, „Sdílení do
  aplikace". Zbývá:
  - ~~**Direct Share**~~ — hotovo. Na iOS jsou naposledy použité chaty nahoře v listu rozšíření;
    řádek kontaktů v systémové nabídce (darování `INSendMessageIntent`) zatím není.
  - ~~**iOS: Share Extension**~~ — hotovo (výběr chatu v rozšíření, App Group). Zbývá
    ověřit na zařízení (App Group v Developer účtu) a ikona aplikace, bez které má Plume
    v nabídce sdílení zástupnou ikonu.

## Nastavení

Nastavení je list-detail: vlevo sekce (Obecné, Vzhled), vpravo jejich obsah. Obecné drží kartu
účtu (jméno, telefon, uživatelské jméno), stav oprávnění k notifikacím s odkazem do systému
a odhlášení; Vzhled motiv aplikace a vyčlenění nepřečtených chatů do vlastního bloku.

- **Úprava vlastního profilu** — fotka, jméno, uživatelské jméno, bio.
- **Sekce Notifikace** — viz [Notifikace](#notifikace) níž.
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
