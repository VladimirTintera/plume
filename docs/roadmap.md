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
- **Multi-stack navigace.** Dnes je jeden zásobník pro všechny sekce a přepnutí sekce ho srovná
  na kořen. Pro Chaty/Nastavení to nevadí; jakmile přibude sekce s hlubší navigací, patří sem
  multi-stack (vzor: `MultiStackNavigationState` v mlogu).

## Nastavení

`SettingsScreen` má dnes kartu účtu (jméno, telefon, uživatelské jméno), stav oprávnění
k notifikacím s odkazem do systému a odhlášení. `AppSettings` (DataStore) drží jen poslední část
výběru emoji/samolepek, nedávná emoji a odstíny pleti — tedy nic, co by si uživatel vědomě
nastavil.

- **Úprava vlastního profilu** — fotka, jméno, uživatelské jméno, bio.
- **Vlastní volby aplikace.** Zatím žádná není; první kandidáti jsou chování notifikací
  a vzhled (téma se dnes řídí jen systémem).
