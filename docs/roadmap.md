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
`ConversationRoute`, `MediaRoute`, `ForwardRoute`.

- **Archiv.** Archivovaný chat ze seznamu jen zmizí (`ChatPosition.order == 0`) a není kudy se
  k němu dostat. Vedlejší důsledek, který už je vidět: chat mimo hlavní seznam neukáže v hlavičce
  „píše…", protože se činnost bere ze `ChatRepository`.
- **Detail chatu / profil.** Není kam ťuknout na hlavičku konverzace — chybí společná média,
  členové skupiny, popis, ztlumení a hledání v chatu na jednom místě.
- **Hledání.** Ani chatů (`searchChats`), ani zpráv v chatu (`searchChatMessages`), ani veřejných
  (`searchPublicChat`). Váže na sebe víc věcí: hledání v přeposílání jde dnes jen po načteném
  seznamu chatů a zmínky s hashtagy se v textu schválně nemapují, protože kliknutí nemá kam vést.
- **Nová konverzace a kontakty.** Napsat se dá jen tomu, s kým už chat existuje.

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
