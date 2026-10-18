# Přímé zprávy kanálu

Kanál si může zapnout **přímé zprávy**: odběratelé mu píšou jako do soukromého chatu, správce
je vidí rozdělené na vlákna po lidech. V TDLib je to samostatná supergroupa „monoforum"
(`Supergroup.isDirectMessagesGroup`, kanál ji najde v `SupergroupFullInfo.directMessagesChatId`),
vlákno je `DirectMessagesChatTopic` a zprávy v něm mají `MessageTopicDirectMessages`.

## Co aplikace umí

- **Zapnout** (Úprava kanálu → Nastavení kanálu → Přímé zprávy, `GroupToggle.DirectMessages`,
  `setChatDirectMessagesGroup`). Jen vlastník („requires owner privileges"), a vždycky zdarma —
  placené zprávy aplikace poslat ani nastavit neumí.
- **Odběratel napíše**: kanál, kde se jen čte, má místo pole vedle „Ztlumit" tlačítko „Napsat"
  (`MuteBar(onMessage)`, `ChatInfo.directMessagesChatId`). Otevře chat přímých zpráv jako
  obyčejnou konverzaci; TDLib v něm odběrateli psát dovolí bez vstupu do skupiny
  (`joinToSendMessages` je u přímých zpráv `false`, takže `writeAccess` vyjde `Allowed` už dnes).
- **Správce čte po vláknech**: `DirectTopicsRoute` se seznamem vláken (`DirectTopicsScreen`,
  `DirectTopicsRepository`), vlákno je `ConversationRoute(directTopicId = …)` a konverzace nad
  `inDirectTopic` — historie, nové zprávy, odeslání, koncept i přečtení patří vláknu. Podržením
  vlákna jde označit (ne)přečtené a smazat celé vlákno.

## Proč je to udělané takhle

- **Spravované přímé zprávy se samy nahradí seznamem vláken** (`ConversationNavigation.onViewAsDirectTopics`,
  efekt v `ConversationEvents`), místo aby o tom rozhodoval seznam chatů. Do chatu se dá dostat
  z mnoha míst — seznam, hledání, odkaz, notifikace, tlačítko „Napsat" — a hlídat každé zvlášť
  by znamenalo pět míst, ze kterých jedno zapomenuté pošle správce do konverzace, kde psaní
  bez vlákna TDLib odmítne. Nahrazuje se **na místě** (`replaceRoute`), ne `removeAll`: co leží
  pod konverzací (chat, ze kterého vedl odkaz), zůstane.
- **Vlákna chodí JEN updaty** (`UpdateDirectMessagesChatTopic`) jako témata Uložených zpráv,
  proto `TdlDirectTopicsRepository` vzniká eagerně (`createdAtStart`) a `load` jen říká TDLib,
  ať pošle další kus (404 = konec). Repository drží vlákna všech chatů najednou; seznam chatu
  je `null` (načítá se), dokud pro něj `load` jednou nedoběhl.
- **První stránka historie vlákna se NEČTE od nuly.** `getDirectMessagesChatTopicHistory`
  s `fromMessageId = 0` čekal na server přes 20 s (změřeno: 23,7 s, další stránka 0,2 s), i když
  zprávy v lokální databázi byly. Od poslední zprávy vlákna (`getDirectMessagesChatTopic` →
  `lastMessage`) to trvá 0,14 s. **`fromMessageId` je ve výsledku VÝLUČNĚ** — s `offset = 0`
  chyběla právě poslední zpráva (odpověď kanálu), proto `offset - 1`.
- **Bubliny bez jména a avataru** (`isGroup` v repository, `isDirectMessages` v položkách
  konverzace): technicky supergroupa, ale ve vlákně se baví dva, jako v soukromém chatu.
  `ChatInfo.isGroup` zůstává pravdivé — rozhoduje o nabídce (odejít ze skupiny, připnutí).
- **`isGroup` v repository je lehký** (lokální `getChat` + `getSupergroup`), volá se u každé
  stránky i nové zprávy. Dřív bral celé `chatInfo`, a to dnes navíc dotahuje vlákno
  a `SupergroupFullInfo`.
- **Chat přímých zpráv nese NÁZEV KANÁLU**, takže by v seznamu i v hlavičce vypadal jako kanál
  podruhé. Seznam ho odliší obálkou před názvem (`DirectMessagesMark`, `ChatSummary.isDirectMessages`),
  hlavička podtitulem „Přímé zprávy".
- **Nepřečtené značky (zmínky, reakce) ve vlákně počítá chat, ne vlákno** — vlákno nese jen
  `unreadReactionCount`, zmínky nemá. „Přečíst všechny reakce" ve vlákně volá
  `readAllDirectMessagesChatTopicReactions`.

## Ověřeno

Na dvou emulátorech (2026-10-18): …029 jako vlastník „Plume test kanal" přímé zprávy zapnul,
…527 jako odběratel ťukl na „Napsat" a poslal zprávu, …029 otevřel chat (nahradil se seznamem
vláken „Pixel 5" s nepřečtenou zprávou), vlákno a odpověděl; odpověď u …527 dorazila. Seznam
vláken po studeném startu čekal na spojení ~15 s, za běhu se načte do 50 ms.

## Co chybí

- Placené zprávy (`paidMessageStarCount`) — ani poslat, ani nastavit cenu.
- Navrhované příspěvky (`approveSuggestedPost` a spol.).
- Archivní větev: archivované přímé zprávy se otevřou jako konverzace (bez vláken).
- Tlačítko „Napsat" v profilu kanálu (dnes jen ve spodní liště konverzace).
- iOS a desktop jen přeložené.
