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
- **Z profilu kanálu**: první dlaždice „Napsat" (odběratel), nebo „Přímé zprávy" (správce;
  `Profile.directMessagesChatId` a `directMessagesAdmin`). Otevře chat přímých zpráv jako
  „Napsat" v liště, takže správce dál vede přesměrování na vlákna — nad profilem, zpět vede
  do profilu.
- **V archivu taky**: `ArchiveDirectTopicsRoute` a `ArchiveConversationRoute.directTopicId`,
  obdoba témat skupiny v archivní větvi. Archivní konverzace dostává šestý parametr ViewModelu
  (`directTopicId`) a pátý (téma Uložených) je `null` — Uložené zprávy archivovat nejdou.

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
- **Jestli je uživatel správce přímých zpráv, pozná profil z chatu přímých zpráv**
  (`getChat` + `getSupergroup`, obojí lokální). Když ho TDLib ještě nezná, dlaždice řekne
  „Napsat" i správci — vede ale na totéž místo a přesměrování na vlákna ji opraví.
- **Nepřečtené značky (zmínky, reakce) ve vlákně počítá chat, ne vlákno** — vlákno nese jen
  `unreadReactionCount`, zmínky nemá. „Přečíst všechny reakce" ve vlákně volá
  `readAllDirectMessagesChatTopicReactions`.
- **Kdo ze správců odpověděl, ukáže „Informace o zprávě"** (řádek „Odeslal(a)" s fotkou,
  ťuknutí otevře profil; `MessageInfo.author`, `getMessageAuthor`). Odpověď správce jde za
  kanál, takže ji ostatní správci od sebe jinak nerozeznají.
  - **Jen tady, ne u příspěvků kanálu.** Audit to vedl jako „autor příspěvku kanálu pro
    správce", jenže `MessagesManager::can_get_message_author` (TDLib master) pustí
    `canGetAuthor` jen ve SPRAVOVANÉM monoforu (`is_admined_monoforum_channel`) a jen u zprávy,
    jejímž odesílatelem je kanál. Ve vlastním kanálu bez podpisů přišlo na emulátoru `false`.
  - **Řádek se ptá až s právem** (`canGetAuthor` z `getMessageProperties`, které sheet čte tak
    jako tak), selhání `getMessageAuthor` ho jen vynechá — jako odkaz a zhlédnutí.
  - **Na zařízení neověřené**: přímé zprávy testovacího kanálu jsou vypnuté a druhý účet, který
    by do nich napsal, na emulátoru není. Ověřeno jen, že u běžného příspěvku kanálu řádek
    nevznikne.

## Ověřeno

Na dvou emulátorech (2026-10-18): …029 jako vlastník „Plume test kanal" přímé zprávy zapnul,
…527 jako odběratel ťukl na „Napsat" a poslal zprávu, …029 otevřel chat (nahradil se seznamem
vláken „Pixel 5" s nepřečtenou zprávou), vlákno a odpověděl; odpověď u …527 dorazila. Seznam
vláken po studeném startu čekal na spojení ~15 s, za běhu se načte do 50 ms.
Dlaždice v profilu: „Napsat" u …527 otevřela přímé zprávy, „Direct messages" u …029 seznam
vláken a zpět vedlo do profilu. Archiv: …029 přímé zprávy archivoval, z archivu se otevřel
seznam vláken i vlákno, pak se chat vrátil mezi chaty.

## Navrhované příspěvky

Odběratel může v přímých zprávách kanálu **navrhnout příspěvek** (podržet Odeslat → „Navrhnout
jako příspěvek", `SendOptions.suggestPost` → `MessageSendOptions.suggestedPostInfo`). Zpráva pak
nese `suggestedPostInfo` a pod bublinou kartu „Navrhovaný příspěvek" (`SuggestedPostCard`, jako
kontrola faktů pod zprávou): stav, cena, termín a správci **Schválit / Odmítnout**
(`approveSuggestedPost`, `declineSuggestedPost` s nepovinným komentářem v dialogu).

- **Jen zdarma a bez termínu** (`InputSuggestedPostInfo(price = null, sendDate = 0)`): placené
  návrhy Plume neumí poslat ani zaplatit. Cenu cizího návrhu (hvězdy, TON) karta ukáže.
- **Schválení s `sendDate = 0`** zveřejní příspěvek hned (návrh termín nemá); karta pak ukáže
  „Zveřejnit: <čas>", který doplnil server.
- **Stav se mění updatem `UpdateMessageSuggestedPostInfo`**, obsah zprávy ne — proto je mezi
  updaty `editedMessages` a zpráva se dočte celá. Server odpovídá pomalu: odmítnutí se
  u správce projevilo až po ~15 s a schválení do minuty — karta do té doby čeká, chyba to není.
- **Nabízí se jen odběrateli** (`SendMenuOffer.suggestPost`: přímé zprávy, ne jako správce) —
  správce kanálu publikuje sám.
- **Servisní zpráva o odmítnutí nese komentář správce** („Navržený příspěvek byl odmítnut:
  „…""), dřív se zahazoval a autor nevěděl proč.
- **„Schválit" je plné tlačítko**: tónové má `secondaryContainer`, tutéž barvu jako karta,
  a splynulo s ní.
- **Ověřeno na dvou emulátorech** (…527 odběratel, …029 vlastník „Plume test kanal"): tři návrhy;
  první schválen (zveřejněn v kanálu, přeposlán do diskuse), druhý odmítnut s komentářem
  „Tohle se nehodi", třetí bez komentáře. U odběratele se karty přepnuly živě, servisní zprávy
  s komentářem u obou. **Neověřené:** placený návrh od oficiálního klienta, „addOffer" (změna
  ceny a termínu).

## Co chybí

- Placené zprávy (`paidMessageStarCount`) — ani poslat, ani nastavit cenu; totéž placené
  návrhy příspěvků a protinávrh ceny či termínu (`addOffer`).
- Nabídka nad chatem přímých zpráv v seznamu nabízí „Odejít" jako u skupiny; co to správci
  udělá, ověřené není.
- Bublina konverzace (Android bubbles) přesměrování na vlákna nemá — správce v ní dostane celý
  chat.
- iOS a desktop jen přeložené.
