# Spam, nahlášení a blokování

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Blokování a nahlášení spamu

Spam se řeší třemi věcmi naráz — nahlásit, zablokovat, smazat — a jde to ze tří míst:
**pruh nad konverzací** u chatu od neznámého (`SpamBarCard`), **podmenu „Smazat"** v nabídce
nad chatem (a totéž jako sheet v profilu, `DeleteChatOptions`) a **„Nahlásit"** samostatně
(nabídka nad chatem, ⋮ v profilu, nabídka nad zprávou). Nahlášení je vždycky tentýž průvodce
(`ReportFlow` + `ReportSheet`, testy `ReportFlowTest`).

- **Blokování má JEDNU cestu, a je jí `ChatRepository`** (`setBlocked(chatId, …)` →
  `setMessageSenderBlockList` s `BlockListMain`). Profil chatu ji volá taky; dřív měl vlastní
  `ProfileRepository.setBlocked(userId, …)` a dvě cesty k témuž volání by se rozešly. Volající
  má chatId, ne userId — rozbalení na `ChatTypePrivate`/`ChatTypeSecret` patří za hranici.
- **Blokování chat NEMAŽE, a proto ho musí ohlásit snackbar.** V seznamu se po zablokování nic
  viditelného nezmění; bez potvrzení to vypadá, že ťuknutí nic neudělalo. Snackbar má „Vrátit",
  takže potvrzovací dialog před ním nemá co dělat — stejné pravidlo jako u archivace a odepnuté
  zprávy. (V profilu chatu dialog zůstává: tam se do blokování chodí vědomě a snackbar s „Vrátit"
  tam není odkud ukázat.)
- **Stav blokování a nahlášení se čte až při otevření nabídky** (`spamActions`), jako práva
  u nabídky nad zprávou: `ChatSummary` ho nenese a `getUserFullInfo` na každý chat v seznamu by
  byl dotaz za položku. Dokud odpověď nedorazí, položka se nekreslí — objevení ruší míň než
  zmizení. **Nezjištěno = nenabízet nic** (`SpamActions()`).

### Nahlášení je průvodce

- **`reportChat` NENÍ jedno volání.** Vrací `ReportChatResult`: `Ok`, nebo `OptionRequired`
  (volba důvodu), `TextRequired`, `MessagesRequired`. Jedním krokem (prázdné `optionId`
  i `messageIds`) projde jen chat se spamovým action barem (`ChatActionBarReportSpam`,
  `ChatActionBarReportAddBlock`); jinde je úvodní prázdný požadavek jen OTÁZKA — server vrátí
  důvody a nic se nenahlásí. Díky tomu jde průvodce ověřit na emulátoru bez skutečného nahlášení.
- **Dřív se proto „Nahlásit spam" nabízelo jen s action barem, a to byla ta chyba.** Action bar
  zmizí, jakmile uživatel protějšku cokoli odpoví, a bez průvodce pak nahlásit nešlo nic — v potvrzení
  mazání zbylo jen „Zablokovat taky". Teď se nabízí podle `Chat.canBeReported` NEBO action baru
  (`SpamActions.canReport`) a jen popisek se liší: s action barem „Nahlásit spam" (hotovo jedním
  ťuknutím), jinak „Nahlásit…" / „Nahlásit — vybereš důvod". Otázka po „Smazat" musí být
  ohlášená dopředu, jinak by vyskočila nečekaně.
- **Důvody i otázky píše server**, v jazyce účtu („What is wrong with this bot?", „Spam", „Scam
  or fraud"…). Vlastní seznam by se rozešel s tím, co server přijme; aplikace jen kreslí.
- **Nahlášení zprávy posílá `messageIds` v KAŽDÉM kroku**, ne jen v prvním (`ReportRequest`
  drží všechno, co už uživatel vybral). U alba celé album, jako mazání. Nabízí se podle
  `MessageProperties.canReportChat` — vlastní zprávy a Uložené zprávy ho nemají.
- **`MessagesRequired` z nabídky nad chatem dodat nejde** — průvodce řekne, kudy vede cesta
  (nabídka nad zprávou), místo slepé uličky.
- **Nahlásit jde i celý výběr víc zpráv** (vlajka v liště výběru, `MessageSelection.canReport`,
  `reportSelection`): tentýž průvodce s ids všech vybraných, jen když server dovolí nahlásit
  KAŽDOU z nich — stejné skládání práv přes `all` jako u přeposlání a mazání. Pozor, že
  `canReportChat` rozhoduje server: v chatu s botem ho má i VLASTNÍ zpráva (ověřeno — s `/help`
  ve výběru vlajka nezšedla), takže „vlastní zprávy nahlásit nejde" platí jen tam, kde to
  server řekne. Vlajka stojí před košem: spam se nejdřív nahlásí, pak maže.
- **Zavřený průvodce nesmí ožít** odpovědí na krok, který byl na cestě (`generation`).
- **`ReportFlow` je třída nad `CoroutineScope`, ne ViewModel.** Seznam chatů, profil
  i konverzace ji mají každý ve svém ViewModelu a kreslí `ReportHost` (sheet + snackbar).
  Server dostává jako funkci, takže se pořadí kroků testuje bez TDLib.

### Úklid spamu a pořadí

- **Pořadí je nahlásit → zablokovat → smazat a na první chybě se ZASTAVÍ.** Nahlášení běží
  průvodcem a teprve jeho úspěch spustí zbytek (`ReportFlow.thenAfterReport`); zablokovat →
  smazat drží `cleanUpSpam` (testy `SpamCleanupTest`). Nahlásit jde jen chat, který ještě
  existuje, takže mazání musí být poslední; zrušený průvodce nesmaže nic; a kdyby se chat smazal
  i po selhaném blokování, bot by dál psal a chat, na kterém by šlo akci zopakovat, by byl pryč.
- **Smazání je podmenu v sheetu, ne dialog** (`CleanupOptions`). Dialog nad sheetem zavřel
  nabídku, ze které vyšel, a volby v něm stály mimo to, čeho se týkaly. Rozbalené „Smazat" nese
  větu s následkem, zaškrtávátka a plné chybové tlačítko — potvrzením je rozbalení a ťuknutí na
  tlačítko v něm, jako doby ztlumení pod „Ztlumit". Profil má z ⋮ totéž jako samostatný sheet
  (`ChatDeleteSheet`), protože do obyčejného menu se podmenu rozbalit nedá.
- **Sheet s podmenu je jen rozbalený (`Hidden, Expanded`) a scrolluje se, a podmenu se dotáhne
  do pohledu** (`snapshotFlow { scroll.maxValue }`). Rozbaluje se na KONCI nabídky, takže
  u polovičního sheetu by tlačítko smazání skončilo pod okrajem obrazovky.
- **Zaškrtávátka jsou výchozí vypnutá** a blokovanému se „Zablokovat taky" nenabízí. Zablokovat
  někoho omylem při mazání chatu je horší než ťuknutí navíc.

### Pruh nad konverzací (`ChatActionBar`)

- **Tím Telegram spam řeší primárně**: nabídne se sám, jakmile cizí napíše. Kreslí se jen
  `ReportSpam` (Nahlásit spam), `ReportAddBlock` (Zablokovat, Nahlásit spam, Přidat kontakt)
  a `AddContact`; pruhy pro sdílení čísla, pozvání a žádost o vstup ne (`SpamBar`).
- **Živý přes `UpdateChatActionBar`**, odběr před prvním čtením; update nese pruh, ale čte se
  celý chat — kdo jde zablokovat, se pozná z typu chatu.
- **Zablokovat i Nahlásit otevřou sheet s volbami** (`SpamBarSheet`): hlavní akce je tlačítko,
  zbylé dvě zaškrtávátka (zablokovat / nahlásit a „Smazat i chat"). Tak to dělá i oficiální
  klient, jen dialogem. Smazaný chat konverzaci zavře (`chatGone`).
- **Přidat kontakt bere jméno z účtu protějšku** a svoje číslo NEsdílí (`sharePhoneNumber =
  false`) — to je samostatné rozhodnutí o soukromí. ✕ zavře pruh napořád (`removeChatActionBar`).
- **Nahlásit jde člověka JEN přes pruh.** `reportChat` pustí soukromý chat pouze s botem
  (`UserManager::can_report_user`: `is_bot`), skupinu nikdy, supergroup a kanál jen nevlastníkovi.
  Bez spamového pruhu u člověka nezbývá než zablokovat a smazat — a oficiální klient to má
  stejně. **Past:** když TDLib pruh zná a spam v něm není, prázdný `reportChat` vrátí
  `ReportChatResultOk` a NIC NEODEŠLE. Kdo by se ptal jen na výsledek, hlásil by úspěch.
- **Pruh se neztrácí časem, ale akcí na účtu.** TDLib si ho zjistí sám, jakmile chat poprvé
  vidí (`users.getFullUser` → `peerSettings`), a lokálně ho schová jen po nahlášení
  a zablokování. Jinak ho zhasne server: odpověď, přidání kontaktu nebo křížek z KTERÉHOKOLI
  klienta. Kdy ho server vůbec nastaví, dokumentované není.
- **Automaticky archivovaný chat má v pruhu „Vrátit mezi chaty" MÍSTO „Nahlásit spam"**
  (`canUnarchive`), jak to předepisuje dokumentace Telegramu. Nahlásit jde dál zaškrtávátkem
  v sheetu Zablokovat. Vrácení jsou DVĚ volání souběžně (`unarchiveFromSpamBar`):
  `addChatToList(ChatListMain)` a upozornění chatu na výchozí (`defaultChatNotificationSettings`,
  sdílené s obnovením výjimky v Nastavení) — server chat při archivaci i ztlumil. Pruh si pak
  TDLib upraví sám: nahlášení a blokování zmizí, přidání kontaktu zůstane.
- **Konverzace otevřená z archivu se po vrácení PŘESTĚHUJE do hlavního seznamu**
  (`moveConversationToMain`, testy `ChatsStackTest`), nezavře se. Zavřít by uživatele vyhodilo
  z chatu, o který právě řekl, že stojí; nechat by znamenalo konverzaci vedle archivu, ve kterém
  chat už není. Z hlavní větve se odebere i detail jiného chatu (profil, úprava) — jinak by zpět
  z vráceného chatu vedlo do profilu toho předchozího.
- **Údaje o protějšku jsou pod nadpisem pruhu** (`CounterpartAccount`, testy
  `CounterpartAccountTest`): od kdy je na Telegramu, země podle čísla s vlajkou a kdy změnil
  jméno a fotku. Chodí jen u `ReportAddBlock`, když cizí napíše poprvé. Neznámé hodnoty TDLib
  posílá jako nulu nebo prázdný řetězec, takže se vynechají — a bez jediné se neukáže nic.
  „Od března 2021" stojí na tvaru měsíce za dnem (`Month.name()`), protože po „od" je v češtině
  stejný 2. pád; nový sloupec nominativů by byl na jedno místo.
- **Vlajka je z kódu země, ne z tabulky** (`flagEmoji`): písmena se posunou na regionální
  indikátory a písmo je složí. Název země překládá systém (`localizedCountryName`), jako při
  přihlášení.
- **Pruh a připnuté zprávy se měří spolu** (jeden `Column` s `onSizeChanged`), seznam se odsazuje
  o výšku obou. Spam stojí nad připnutou zprávou.
- **Pruh nad konverzací je obecný `ChatBar`, ne jen spam** (`ChatRepository.chatBar`):
  `SpamBar` (spam, blokování, kontakt), `SharePhoneBar` (vzájemný kontakt nevidí vlastní číslo
  → `sharePhoneNumber`, po potvrzení dialogem — odchází osobní údaj) a `JoinRequestBar`
  (správce skupiny nebo kanálu, kam uživatel žádal o vstup — jinak by zpráva od něj vypadala
  jako spam). Nespamové pruhy kreslí `ChatNoticeBarCard` týmž tvarem; křížek je u všech
  `removeChatActionBar`. `InviteMembersBar` (čerstvě založená skupina) vede na výběr lidí,
  viz „Přidání členů".
- **Den z `asDayLabel` nepatří doprostřed věty** — je nadpisový („Včera"). U žádosti o vstup
  stojí proto na vlastním řádku za dvojtečkou („Žádost odeslána: Včera").
- **Text a tlačítka pruhu jsou v jednom sloupci, křížek vedle.** V řádku s `IconButton` (48 dp)
  zbyla mezi textem a tlačítky mezera a pruh vypadal rozpadle.

### Seznam blokovaných v Nastavení

Obecné → Soukromí → Blokovaní (řádek s počtem, `BlockedSettings`) otevře obrazovku
`BlockedSendersScreen` (`BlockedSendersRoute`, vrší se nad sekci jako úprava profilu). Je to
JEDINÉ místo, kde jde zrušit blokování někoho, s kým uživatel chat smazal — nabídka nad chatem
i profil stojí na chatu, a ten po smazání není.

- **Je to tentýž `ChatPicker` jako kontakty, přeposílání a sdílení** — hlavička, `SearchPill`,
  bloky zaoblených řádků, prázdný stav. **První verze byla sheet s holými `ListItem`y a tlačítkem
  „Odblokovat" u každého řádku**: vedle kontaktů vypadala jako jiná aplikace a měla jiné
  ovládání. Blokovaný se do výběru dostane jako `ChatSummary` (jméno, fotka, druh — zbytek jsou
  výchozí hodnoty bez významu). **Obecně: seznam lidí nebo chatů patří do `ChatPicker`, i když
  z něj nic nevybíráš.**
- **Akce nad položkou jako všude jinde: ťuknutí otevře nabídku** (sheet s tváří, jménem
  a „Odblokovat", tvarem jako nabídka nad chatem), vratná akce má snackbar s „Vrátit".
  `ChatPicker` kvůli tomu bere vlastní `snackbarHostState` — výchozí umí jen chyby.
- **Blokovaný je ODESÍLATEL, ne uživatel** (`getBlockedMessageSenders` vrací `MessageSender`):
  blokovat jde i kanál nebo skupinu. `BlockedSender.id` drží konvenci odesílatele zprávy — kladné
  id uživatel, záporné chat — a `setSenderBlocked` z něj skládá `MessageSender` zpátky.
  `setBlocked(chatId)` z nabídky nad chatem jde přes tutéž funkci, takže cesta k
  `setMessageSenderBlockList` zůstala jedna.
- **Čte se celý seznam naráz** (`collectBlockedSenders`, stránky po 100, testy
  `BlockedSendersStateTest`) — hledání je lokální a počet v Obecných má sedět. Server vrací jen
  id, jména a fotky se dočítají lokálně; kdo se dočíst nedá, ze stránky vypadne, proto se
  **konec pozná z `totalCount` od serveru, ne z délky stránky**. Tentýž odesílatel na dvou
  stránkách zůstane jednou (dva stejné klíče by `LazyColumn` shodily) a selhaná stránka je
  selhání celku — půlka seznamu by tvrdila, že zbytek blokovaný není.
- **Odblokovaný zmizí ze seznamu hned a pozdější čtení ho tam nevrátí**, dokud uživatel nedá
  „Vrátit" (`unblockedIds`, testy `BlockedSendersViewModelTest`). Na emulátoru zůstal odblokovaný
  bot po novém přečtení v seznamu, zatímco počet v Obecných už seděl — příčina (odpověď serveru
  těsně po změně?) zjištěná není, test proto hlídá chování, ne domněnku.
- **Počet v Obecných se čte znovu při každém návratu** (`LifecycleResumeEffect`) — po odblokování
  na obrazovce seznamu by jinak zůstal starý. Podtitul je počet ÚČTŮ („14 účtů"), ne
  „14 zablokovaných": pod nadpisem „Blokovaní" by to slovo stálo dvakrát.
- **Smazaný účet má v avataru iniciály „SÚ"** — `ChatPicker` je bere z názvu. Nehezké, ale
  vlastní avatar kvůli jednomu druhu řádku by znamenal rozbít společný řádek výběru.
- **„Zablokovat uživatele" je blok nad seznamem** (`header` výběru), výběr je další obrazovka
  (`BlockSenderRoute`, `BlockSenderScreen`) nad seznamem blokovaných: kontakty a při hledání
  ostatní známé chaty (`blockCandidates`, testy `BlockCandidatesTest`). **Jen soukromé chaty**
  (lidé a boti), bez Uložených zpráv a bez už blokovaných — kanál nebo skupinu jde zablokovat
  jako odesílatele u její zprávy, jako položka seznamu by slibovala něco, co blokování neumí.
- **Zablokování z výběru se potvrzuje dialogem**, přestože nabídka nad chatem má jen snackbar
  s „Vrátit": obrazovka po zablokování odchází, takže snackbar není kde ukázat, a ťuknutí vedle
  v dlouhém seznamu kontaktů by zablokovalo někoho jiného. Stejně jako v profilu.
- **Seznam blokovaných se čte při KAŽDÉM složení** (`LaunchedEffect`), ne v `init` ViewModelu.
  Pod výběrem k zablokování složený není (NavDisplay kreslí jen horní scénu), takže návrat ho
  složí znovu a nově zablokovaný je v něm hned; řádek v Obecných čte počet sám při návratu.
- **Ověřeno na emulátoru** (česky): 14 blokovaných na skutečném účtu jen prohlížením, hledání
  „sop" bez diakritiky, nabídka po ťuknutí zavřená bez akce; na testovacím botovi zablokovat
  z nabídky nad chatem → v seznamu odblokovat (řádek zmizí, snackbar) → „Vrátit" (řádek zpátky)
  → odblokovat → 14 účtů. **Ověřuj ťuknutím podle TEXTU z `uiautomator dump`, ne podle
  souřadnic**: seznam chatů se na živém účtu přeskládá s každou zprávou a heads-up notifikace
  zakryje tlačítko účtu — pevná souřadnice pak otevřela cizí konverzaci a označila zprávy jako
  přečtené.

### Ověření

- **Na emulátoru** (chat s testovacím botem, anglicky): „Report…" v nabídce nad chatem i v ⋮
  profilu, podmenu smazání se zaškrtávátky „Block them too" a „Report — you'll pick a reason",
  sheet smazání v profilu, „Report" v nabídce nad zprávou; průvodce ukáže důvody od serveru
  („What is wrong with this bot?" / „…this message?") a zavření nic neodešle.
- **Pruh nad konverzací se ověřoval PODSTRČENÝM stavem** (`spamBar` v `ConversationViewModel`
  pro chat s testovacím botem nahrazený pevným `SpamBar` i s `CounterpartAccount`, necommituje se)
  — chat od cizího na testovacím účtu není. Vzhled pruhu, údaje o protějšku (anglicky) a sheet
  „Report spam?" ověřené jsou, **skutečné `UpdateChatActionBar` ne.** Vrácení z archivu se na
  archivovaném botovi ťuknout dá, jenže podstrčený pruh zůstává i po něm.
- **Skutečné nahlášení, smazání s volbami a přidání kontaktu ověřené NEJSOU** — nahlášení by šlo
  moderátorům Telegramu na vlastního testovacího bota.
