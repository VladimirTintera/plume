# Notifikační kanály

Rozbor pro položku v [roadmap.md](roadmap.md#notifikace). Dnes má aplikace **jediný kanál
`messages`** (`NotificationPresenter.android.kt`). V systémovém nastavení proto nejde odlišit
soukromé zprávy od skupin a kanálů, třeba skupiny ztišit a soukromé zprávy nechat zvonit.

## Co dělá TDLib a co ne

**TDLib o Android kanálech neví nic.** Rozhodne, JESTLI se notifikace ukáže, kdy a jakým zvukem.
Jak ji aplikace zařadí v systému, je na ní. Data, podle kterých kanály rozdělit, ale dává:

- **Tři globální rozsahy.** `NotificationSettingsScopePrivateChats`, `…GroupChats` a
  `…ChannelChats` mají každý vlastní výchozí nastavení: ztlumení, zvuk (`soundId`),
  `showPreview` a nastavení pro stories. Čte se `getScopeNotificationSettings`, zapisuje
  `setScopeNotificationSettings` a synchronizuje se přes server na ostatní zařízení.
- **Nastavení chatu** (`ChatNotificationSettings`). Příznaky `useDefault*` v něm říkají, že chat
  jede podle svého rozsahu. Používá se už u ztlumení v seznamu chatů.
- **Metadata v `UpdateNotificationGroup`:**
  - `type` (`NotificationGroupType`): `Messages`, `Mentions` (zmínky a odpovědi na mě),
    `SecretChat`, `Calls`,
  - `notificationSettingsChatId`: chat, podle jehož nastavení se notifikace řídí,
  - `notificationSoundId`: zvuk, který TDLib vybrala z nastavení chatu a rozsahu,
  - `isSilent` u jednotlivé notifikace. Ten se už předává (`ChatNotification.isSilent`).
- **Vlastní zvuky** jsou soubory uložené na serveru (`getSavedNotificationSounds`).

`ChatNotification` dnes nenese ani typ skupiny, ani druh chatu. To je první, co by se muselo
přidat.

## Návrh kanálů

| Kanál | Kdy | Poznámka |
|---|---|---|
| Soukromé zprávy | rozsah `PrivateChats` | včetně tajných chatů, dokud je neumíme |
| Skupiny | rozsah `GroupChats` | |
| Kanály | rozsah `ChannelChats` | výchozí důležitost klidně nižší |
| Zmínky a odpovědi | `NotificationGroupType.Mentions` | v tiché skupině to jediné, co má zvonit |
| Hovory | `NotificationGroupType.Calls` | až budou hovory |

Kanály se sdruží do `NotificationChannelGroup`, pokud jich bude víc než pár. Druh chatu se zjistí
z `getChat(notificationSettingsChatId).type`. `TdlNotificationRepository` už `getChat` volá kvůli
názvu, takže dotaz navíc nebude.

## Past: kanál je po vytvoření neměnný

Po `createNotificationChannel` patří zvuk, důležitost, vibrace i světlo **uživateli**. Aplikace
je už nepřepíše; změnit smí jen název a popis (třeba po změně jazyka).

Pro propojení s nastavením Telegramu z toho plynou dvě cesty:

- **a) Zvuk a důležitost nechat systému (doporučeno).** Kanály se jen založí s rozumnými
  výchozími hodnotami a `notificationSoundId` se ignoruje. Je to jednoduché a nativní pro
  Android: uživatel nastavuje notifikace tam, kde je na Androidu nastavuje všude. Ztlumení
  a náhled dál řídí TDLib, protože o nich rozhoduje dřív, než notifikace vznikne.
- **b) Synchronizovat se serverem.** Změna zvuku v Telegramu (i z jiného zařízení) znamená
  **nový kanál s novým id** (verze v id, jako oficiální klient) a smazání starého. Tím se ale
  zahodí i to, co si uživatel u starého kanálu nastavil v systému. Vzniknou dva zdroje pravdy,
  které se snadno rozejdou. Stojí to za to, až bude v aplikaci výběr zvuku.

## Nastavení jednotlivého chatu už skoro máme

Vlastní kanál na každý chat **není potřeba**. Notifikace má `MessagingStyle` a dlouhověkou
zkratku (`setShortcutId`), takže ji Android 11+ bere jako **konverzaci**. U ní si uživatel
v systému nastaví „Prioritní", „Tichá" nebo vlastní zvuk jen pro ten chat. Android si k tomu
sám odvodí podkanál z rodičovského kanálu (`setConversationId`). Stojí za ověření, že to s
dnešním kanálem opravdu funguje, a odkázat na to z Nastavení.

## Vazba na Nastavení

Kanály a sekce Notifikace v Nastavení (viz roadmapa) jsou dvě půlky jedné obrazovky. Rozsah
TDLib a kanál si odpovídají jedna k jedné: u každého rozsahu jsou volby, které drží TDLib
(zapnutí, náhled, zmínky, připnuté), a pod nimi odkaz „Zvuk a vibrace" rovnou do jeho kanálu
(`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` s `EXTRA_CHANNEL_ID`). Proto dává smysl dělat
obojí najednou, nebo kanály dřív.

## Migrace

Starý kanál `messages` se po přechodu smaže (`deleteNotificationChannel`). Jinak zůstane
v systémovém nastavení viset jako prázdná položka. Nastavení, které si u něj uživatel udělal, se
nepřenese, protože kanál přenést nejde. U aplikace před prvním veřejným vydáním to nevadí.

## iOS

iOS kanály nemá. Obdobou je `threadIdentifier` (seskupení podle chatu) a `interruptionLevel`
(`passive` / `active` / `timeSensitive`); zmínky by šly jako `timeSensitive`. Přijde to na řadu
až s notifikacemi na iOS vůbec.

## Ověření

- `adb shell dumpsys notification | grep -A3 "NotificationChannel{"` vypíše kanály a jejich
  nastavení.
- Botem poslat zprávu do soukromého chatu a do skupiny (viz „Push se testuje botem" v
  `CLAUDE.md`), v systému ztišit kanál Skupiny: soukromá zpráva musí zvonit, skupinová ne.
- Zmínka ve ztišené skupině musí přijít kanálem Zmínky.
