# Zamčené chaty

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

Chat zamčený v tomhle zařízení (⋮ → Další → Zamknout chat) zmizí ze všech seznamů a otevře se
jen přes PIN nebo biometrii zámku aplikace. Na server se nic neposílá; oficiální Telegram to
nemá (WhatsApp ano). Navazuje na [zamek-aplikace.md](zamek-aplikace.md) a co zámek aplikace
nechrání, neplatí ani tady.

## Kde se filtruje

**Jedno místo pro všechno, co chat ukazuje v seznamu: vrstva `core`.** Rozhraní `LockedChats`
(`core/accounts`, vzor `AccountVisibility`) dodává `shared` z nastavení účtu; hodinky ho nemají
a zamčeného nic nevidí.

- **`TdlChatRepository`**: `publish()` vynechá zamčené ze všech složek (hlavní, archiv, složky
  Telegramu) a skládá z nich `ChatFolder.Locked`; `summariesFor` je vynechá z hledání,
  nedávno hledaných, častých chatů, výběrů, historie hovorů a hledání zpráv. **`summaries()`
  (konkrétní chaty podle id) je NEFILTRUJE** — ptá se na ně konverzace, profil a hovor, kam
  zamčený chat patří. `all` (koncepty, „píše…", ztlumení) zamčené nese kvůli otevřené
  konverzaci; přehled konceptů je filtruje sám.
- **Počty nepřečtených posílá TDLib za celé složky**, takže filtr seznamu je nezmění. Repository
  drží surové hodnoty a veřejné z nich odečte, čím k nim zamčený chat přispívá (nepřečtený
  chat, neztlumený, jeho neztlumené zprávy v hlavním seznamu) — jinak by odznak na ikoně, v menu
  nebo u složky prozradil zprávu v zamčeném chatu. Zamčený chat, který repository nezná (archiv,
  který nikdo neotevřel), se při změně sady dočte, jinak by nešel odečíst.
- **`TdlNotificationRepository`**: notifikace ze zamčeného chatu vyrobí už začerněnou
  (`ChatNotification.isLocked`: „Zamčený chat — Nová zpráva", bez jména, fotek, obrázku a akcí);
  zamčení chatu pošle `NotificationUpdate.ChatLocked`, podle kterého presenter uklidí, co už
  na liště je. Hovor ze zamčeného chatu má volajícího „Hovor ze zamčeného chatu".
- **`TdlStoryRepository.active`**: lem u avataru a hromádka v pilulce by chat prozradily.
- **`TdlDownloadsRepository`**: seznam stahování nese jméno chatu.
- Ve `shared` navíc **záložky a popisy fotek** (nesou text a hledá se v nich) a **`ShareTargets`**
  (zamčený chat nedostane zkratku pro Direct Share ani na plochu).
- Widget, společný seznam víc účtů, rychlý přepínač chatů, triáž „čeká na odpověď", sdílení
  na iOS i výběry chatů berou seznamy z repository, takže jsou pokryté samy.

## Sada a její načtení

- **`AccountSettings.lockedChats` je `null`, dokud se nastavení nenačte.** S výchozí prázdnou
  sadou by se zamčené chaty po studeném startu na pár snímků ukázaly v seznamu i v konverzaci.
  `core` dostane tok až od první skutečné hodnoty (`filterNotNull`) a do té doby **seznamy ani
  počty nevydá** (`hiddenLoaded`); notifikace na sadu počká. Nastavení se načte dřív, než TDLib
  pošle první chaty, takže se v praxi nečeká.
- **Sada je v DataStore v čitelné podobě**, ne v `SecretStore`. Chrání před člověkem
  s odemčeným telefonem, ne před rootem — stejná mez jako u zámku aplikace.

## Odemčení

- **PIN a biometrie zámku aplikace** (`AppLock.verify`, `BiometricAuth`), jedna brzda pokusů,
  totéž svázání biometrie se seznamem otisků (`Invalidated` → `revokeBiometrics`). Zamknout chat
  jde jen se zapnutým zámkem aplikace; bez něj dialog řekne, kde ho zapnout.
- **`LockedChatsSession`** (globální): odemčené jsou všechny zamčené chaty všech účtů najednou
  a jen do odchodu do pozadí nebo zamčení aplikace. Delší doba by z půjčeného telefonu udělala
  přístup k zamčeným chatům.
- **Brána je v `ChatColorTheme(chatId)`** (`LockedChatGate`): přes tu funkci jde každá obrazovka
  jednoho chatu — konverzace, témata, profil, bublina —, takže nezáleží na tom, odkud se chat
  otevírá (notifikace, widget, odkaz, zkratka, obnovený zásobník). Pod zamčenou bránou se obsah
  vůbec neskládá: ViewModel konverzace nevznikne a nic se nehlásí jako přečtené. Po návratu
  z pozadí se otevřená konverzace schová znovu.
- **Přehled zamčených** je `LockedChatsRoute` v list panelu (jako historie hovorů), chat se
  otevírá v hlavním zásobníku. Položka v menu účtu je jen tehdy, když nějaký zamčený chat je,
  a **bez počtu nepřečtených**.

## Notifikace na Androidu

- **Bez `MessagingStyle`, zkratky konverzace a bubliny** (`showLocked`): z nich systém skládá
  konverzaci se jménem a fotkou, nabízí ji v Direct Share a Android Auto by ji přečetl nahlas.
  Bez akcí — Odpovědět i Přečteno by šly bez PINu.
- **Po zamčení** se zruší notifikace chatu (i z minulého běhu procesu — poznají se podle
  `shortcutId`) a jeho zkratky; připnutou na ploše smí aplikace jen vypnout
  (`disableShortcuts`).

## Co zamčení nechrání

- **iOS: alert z APNs ukáže systém dřív, než běží kód aplikace.** Začernit ho umí jen rozšíření
  notifikací (`NotificationService.swift`), a to by potřebovalo sadu zrcadlenou do App Group.
  Notifikace na iOS zatím čekají na certifikát, viz roadmapa.
- **Hodinky** sadu neznají (je v nastavení telefonu) a ukazují zamčené chaty normálně.
- **Obrazovka probíhajícího hovoru** v aplikaci (`CallOverlay`, služba hovoru) ukazuje jméno.
- **Kanál notifikací pro jeden chat** (vlastní nastavení upozornění) nese v systémovém
  nastavení jméno chatu.
- **Zmínky jinde**: přeposlaná zpráva, citace nebo poslední zpráva skupiny, kterou napsal
  zamčený bot nebo člověk, ho jmenují — filtruje se chat, ne jméno.
- **Odhlášení jiného účtu zahodí zámek aplikace** (`AppLock.reset`) a zamčené chaty ostatních
  účtů pak nejde otevřít, dokud se zámek nezapne znovu. Nový PIN je otevře — kdo drží
  odemčenou aplikaci, může tak zamčené chaty zpřístupnit odhlášením jiného účtu.

## Ověřeno na emulátoru

Plume_Barvy (Android 16, testovací účet, Notifikátor, zámek aplikace s dočasným PINem):
bez zámku aplikace dialog; po zamčení brána hned přes otevřenou konverzaci; chat zmizel ze
seznamu, v menu účtu přibyla položka; přehled za PINem a otevření chatu z něj; zpráva od bota
v pozadí → notifikace „Zamčený chat / Nová zpráva" bez zkratky a akcí; po návratu z pozadí
brána znovu, text zprávy ve stromu obrazovky nebyl; odemčení vrátilo chat do seznamu a položka
z menu zmizela. **Neověřeno**: Android Auto, widget, Direct Share po zamčení, desktop a iOS.
