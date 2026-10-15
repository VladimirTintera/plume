# Konverzace: pohyb v historii

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Otevřený chat (`openChat` / `closeChat`)

Otevřená konverzace to hlásí TDLib (`ConversationRepository.openChat`, protějšek při odchodu).
Dokud se to nedělalo, vypadal každý chat jako zavřený — a notifikace k chatu, na který se
uživatel právě díval, na liště problikla, než ji stihlo zrušit hlášení přečtení.

- **U otevřeného chatu se notifikace ODKLÁDÁ, neruší.** V `add_new_message_notification` je
  `min_delay_ms = 1000`, když je klient online a chat otevřený. To je celý ten rozdíl: během té
  vteřiny dorazí `viewMessages` z konverzace a notifikace se zahodí dřív, než se ukáže.
  **Otevřený chat notifikace NEPOTLAČUJE** — kdyby potlačoval, byl by chat nechaný „otevřený"
  na pozadí tichou ztrátou zpráv.
- **Proto se při odchodu do pozadí nezavírá.** Odklad se váže na `online`, a ten aplikace
  v pozadí vypíná sama (`ConnectionController`), takže na notifikace to nemá vliv. Zavírat
  a otevírat chat při každém přepnutí do pozadí by byla složitost bez užitku.
- **Pár musí sedět, takže obě volání jdou z `appScope`, ne z `viewModelScope`.** Ten se při
  odchodu z chatu ruší, takže otevření by se nemuselo odeslat, zatímco zavření (v `onCleared`)
  ano — a TDLib si otevření počítá. Zavření navíc čeká na doběhnutí otevření (`join`), jinak by
  se pořadí mohlo obrátit a chat by zůstal otevřený napořád.
- **Zavření je PRVNÍ věc v `onCleared`.** Kus pod ním končí předčasným `return` (nemá-li se co
  uložit z konceptu); pod ním by se na zavření při odchodu z chatu bez rozepsané zprávy nedošlo.
- **Zavřít neotevřený chat nevadí.** `close_dialog` s nulovým počtem se jen vrátí, takže na
  vyměněném klientovi (po odhlášení se počty resetují) to nic nerozbije.
- **Je to víc než notifikace.** Otevřený chat si TDLib nechává načtený v paměti, zvedá prioritu
  stahování jeho souborů, kratčeji odkládá ukládání konceptu a u supergroup a kanálů podle toho
  řídí doručování updatů.
- **Na blikání kvůli druhému zařízení to nemá vliv** — tam zprávu přečte ten druhý klient a ten
  o naší obrazovce nic neví (viz „Notifikace → Poučení", běžící emulátor).

## Skok na první nepřečtenou

Otevření chatu s nepřečtenými dotáhne historii až k hranici, skočí na první nepřečtenou zprávu
a nad ni nakreslí předěl „Nepřečtené zprávy".

- **Hranice se zachytí JEDNOU při otevření** (`lastReadInboxMessageId` z `getChat`) a už se nemění.
  Kdyby se posouvala podle toho, co se právě označilo za přečtené, předěl by uživateli utíkal pod
  rukama — a je to jediné místo, podle kterého pozná, kde přestal.
- **První nepřečtená je nejstarší PŘÍCHOZÍ zpráva za hranicí, ne nejstarší zpráva za ní**
  (`firstUnreadId`, testy `UnreadBoundaryTest`). Hranice je poslední přečtená PŘÍCHOZÍ, takže
  vlastní odpověď napsaná po ní má vyšší id. Předěl i skok to dřív braly jako „nad hranicí =
  nepřečtené": předěl chtěl staršího souseda s id ≤ hranici, a když jím byla vlastní odpověď,
  **neukázal se vůbec**; skok mířil na vlastní zprávu. Běžný případ — odepsal jsem, druhý
  odpověděl —, takže předěl chyběl právě tam, kde se čte nejčastěji.
- **V chatu s testovacím botem nepřečtený stav vyrobit NEJDE, když má účet otevřený Notifikátor
  jinde** (telefon): zprávu od bota přečte to druhé zařízení, v logu je notifikace „odložené=true"
  a pak zmizí bez „Zobrazuji", a chat v seznamu odznak nemá. Předěl i efekt nepřečtené se proto
  ověřují sondou, která v chatu s botem nastaví `unreadBoundaryId` na id zvolené zprávy (podle
  textu) a zavolá `loadUntilUnread()`. Necommituje se.
- **`unreadCount` je potřeba vedle ukazatele.** Z `lastReadInboxMessageId` samotného se nepřečtené
  odvodit nedají: v chatu, kde poslední zprávu napsal uživatel, je ukazatel starší než ta zpráva,
  a přesto není co číst.
- **Nejdřív pár stránek od nejnovější dozadu** (`MAX_UNREAD_PAGES`). Obvyklý případ tím pokryje
  a seznam zůstane souvislý až k přítomnosti. **Kdo má nepřečtených stovky, dostane okno kolem
  hranice** (`loadAround(requireTarget = false)`, viz „Okno v historii") — hranice je poslední
  přečtená zpráva a mohla být smazaná, takže okno se bere i bez ní.
- **Na zámek stránkování se čeká, nepřeskakuje se.** Dotažení kvůli skoku běží souběžně s prvním
  načtením při otevření chatu; kdyby se volání při probíhajícím načítání vzdalo, spotřebovalo by
  všechny pokusy naprázdno a skok by se nekonal.
- **`scrollToItem` v obráceném seznamu posadí položku ke SPODNÍ hraně**, protože tam je jeho
  začátek. U skoku na nepřečtené je to naopak, než je potřeba, takže se ještě dorovnává o výšku
  viewportu — a ta se **měří**, protože výška bubliny se zprávu od zprávy liší.
- **Viditelná plocha začíná NAD spodním odsazením seznamu** (`scrollToAnchor`). Dole je pod ním
  vstupní pole; dokud se horní hrana brala z celé výšky viewportu, zajela první nepřečtená
  o výšku pole pod horní okraj obrazovky.
- **Index v `LazyColumn` není index v `state.messages`.** Před zprávami stojí sponzorované
  bloky a mezi nimi předěly. Blok se proto hledá v seznamu bloků (`containsMessage`), ne
  v seznamu zpráv.
- **Předěl nepřečtených je akcentní, ne šedý jako datum.** Je to jediná věc, kterou uživatel po
  otevření chatu opravdu hledá.

## Připnuté zprávy

Pod hlavičkou visí **jeden řádek s jednou připnutou zprávou** (`PinnedMessagesBar`), ať je jich
kolik chce. Na začátku nejnovější; ťuknutí na ni skočí (stejně jako citace, i se zvýrazněním
a dotažením historie) a pruh posune o jednu starší, za nejstarší zase nejnovější. Všechny jsou
za ikonou seznamu v sheetu (`PinnedMessagesSheet`), s odepínáním.

- **Všechny připnuté přes `searchChatMessages` s `SearchMessagesFilterPinned`**, ne přes
  `getChatPinnedMessage` — ten vrací jen nejnovější. Stránkuje se po stovce, strop pět stránek.
  Vyhledávání potřebuje zapnutou databázi zpráv (`useMessageDatabase = true`).
- **Jedna, ne tři.** Kanály připnuté příspěvky neodepínají, jen přidávají další, takže tři
  řádky (dřívější podoba) visely nad konverzací jako trvalý blok, a s rozbalením „všech" to byla
  půl obrazovky. Že jich je víc a kde v řadě stojíme, říká titulek („Připnutá zpráva 3 z 5").
- **„Poslední připnutá" = nejnovější ZPRÁVA, ne poslední připnutí.** Čas připnutí Telegram
  neposílá vůbec; pořadí je podle id zprávy a `getChatPinnedMessage` vrací taky tu s nejvyšším
  id. U kanálu, který připíná čerstvé příspěvky, je to totéž; připne-li někdo starou zprávu,
  zařadí se podle svého stáří.
- **Která se ukáže, ROZHODUJE SCROLL** (`pinnedForScroll`, testy `PinnedMessagesBarTest`): ta,
  kterou uživatel právě míjí — nejnovější z připnutých nad viditelnou částí. Pruh tím slouží
  i jako orientace v historii, místo aby napořád visel na nejnovější. Nedrží se k tomu žádný
  stav; odvozuje se z klíče nejvyšší viditelné položky (ten už existoval kvůli plovoucímu datu),
  takže nová připnutá zpráva výběr nerozhodí.
- **„Nejvyšší viditelná" = nejvyšší POD pruhem, ne pod hlavičkou** (`topVisibleKey` bere jen
  položky pod horním odsazením seznamu). Seznam pod pruhem a hlavičkou pokračuje a pro
  `LazyColumn` jsou i tam položky viditelné. Skok z pruhu posadí připnutou těsně pod pruh
  a předchozí připnutá zůstala schovaná nad ní — pruh pak přeskočil o jednu (3 → 1 místo
  3 → 2). Ověřeno na složeném Foldu se čtyřmi připnutými.
- **Porovnává se STRIKTNĚ** (`it.id < top`). Připnutá, na kterou se právě skočilo, stojí nahoře
  viewportu; kdyby se počítala jako „nad", ukazoval by ji pruh dál a ťuknutí by vedlo pořád na
  ni — procházení by se zaseklo. Nad nejstarší připnutou se ukazuje ona sama, výš už žádná není.
- **Skok z pruhu kotví NAHORU, ne na střed** (`jumpToMessage(anchor = ScrollAnchor.Top)`). Se
  středem se nad cílem odkryje kus historie a pruh přeskočí rovnou k nejstarší připnuté — z 19
  → 16 → 14 se prostřední krok ztratí. Citace a hledání na středu zůstávají: tam je naopak
  potřeba vidět, na co zpráva navazuje.
- **Ukázaná zpráva se drží podle id, ne indexu** (`pinnedIndex`, testy `PinnedMessagesBarTest`).
  Nová připnutá by jinak posunula index a pruh by přeskočil na jinou. Odepnutá ukázaná spadne
  na nejnovější.
- **Křížek v pruhu je jen u jediné připnuté.** U víc zpráv je vpravo seznam a odepíná se v něm —
  křížek vedle jedné z mnoha svádí k domněnce, že zavře celý pruh.
- **Sheet se po odepnutí nezavírá** (odepíná se často víc zpráv po sobě), po ťuknutí na řádek
  ano — napřed se schová, pak se skáče.
- **„Odepnout vše" je v hlavičce sheetu**, od dvou připnutých a jen s právem odepínat
  (`unpinAllChatMessages`, v tématu skupiny `unpinAllForumTopicMessages` — repository zná
  téma z konstrukce). **Na rozdíl od jedné zprávy se ptá předem**: „Vrátit" by muselo připnout
  každou zprávu zvlášť. Pruh zmizí hned, při selhání se seznam přečte znovu. Ověřeno na
  emulátoru (dvě připnuté zprávy bota v „Plume test moderace"): dialog „Odepnout 2 zprávy?",
  po potvrzení pruh pryč i po restartu aplikace. Téma skupiny ověřené NENÍ.
- **Pruh je plovoucí pilulka z mléčného skla** (`CircleShape`, stín, `glassStyle`), ne karta se
  svislým proužkem vlevo. Proužek (dílek na zprávu) byl jazyk M2 a v aplikaci už jednou ustoupil
  ploše (citace, náhled odkazu); pilulka mluví stejně jako plovoucí lišty konverzace. Fotka,
  video a soubor s náhledem mají v řádku čtvereček (nejmenší varianta, ostrou kvůli 36 dp
  nestahovat).
- **Vlevo je špendlík v sedmistranné sušence** (`PinnedBadge`, `primaryContainer`), soustředný
  se zaoblením pilulky (odsazení = (výška − odznak) / 2). Při přechodu na jinou připnutou se
  sušenka pootočí o jednu vlnku pružinou s přeskokem, špendlík stojí — změna je vidět koutkem
  oka. Úhel je přímo `index × 360/7`, žádné počítadlo; po dosednutí má sušenka tentýž obrys.
  `Cookie7Sided` jinde nic neznamená (viz `ShapeMorphs`).
- **Krytí skla zůstává `GLASS_TEXT_TINT_ALPHA` (0,88)**, i když by pilulka s nižším vypadala víc
  „skleně". Pruh nese text a v tmavém režimu nad světlou fotkou by s nižším krytím spadl pod AA
  (viz „Vzhled konverzace", mléčné sklo). Rozostření je vidět nad fotkou nebo sytou bublinou.
- **Sheet všech připnutých je blok zaoblených řádků** (`groupItemShape`, `surfaceContainerLowest`,
  spára 2 dp), jako kontakty a odkazy v profilu; řádek je `ListItem` s náhledem, textem a dnem.
  Dřív holé řádky na ploše sheetu — jiný vzhled pro tentýž „seznam věcí".
- **Právo odepínat se zjišťuje jednou** (`canBePinned` první připnuté zprávy). Je v chatu jedno
  pro všechny zprávy; dotaz na každou by byl zbytečný. Bez práva křížek není.
- **Odepnutá zpráva z pruhu zmizí hned**, potvrzení dorazí updatem. Při selhání se seznam přečte
  znovu, takže se zpráva vrátí.
- **Místo potvrzovacího dialogu „Vrátit" ve snackbaru.** Odepnutí je vratné; dialog před vratnou
  akcí jen zdržuje. Vrácení připíná bez upozornění členům — je to oprava omylu, ne oznámení.
  Událost se ve stavu nuluje hned při zobrazení, jinak by se snackbar po otočení ukázal znovu.
- **Na změnu se seznam ČTE ZNOVU**, neodvozuje se z updatu. `UpdateMessageIsPinned` nese jednu
  zprávu a „odepnout vše" jich pošle dávku; `conflate` z ní udělá jedno čtení. Totéž po smazání
  připnuté zprávy. Úprava připnuté zprávy se do pruhu propisuje z `editedMessages`.
- **Odběr stojí PŘED prvním čtením** (`onStart { emit(Unit) }`), aby se změna mezi nimi neztratila.
- **Pruh plave nad seznamem, není v `topBar`.** Seznam se odsazuje o jeho změřenou výšku (dole
  o padding `bottomBar`) — jinak by při odjezdu pruhu obsah na konci animace poskočil.
  O stejnou výšku se posouvá i plovoucí datum.
- **Při odjezdu se kreslí poslední zpráva**, ne prázdný pruh (`AnimatedVisibility` dobíhá
  s obsahem, ale stav je už prázdný).
- **Připnout / Odepnout je i v nabídce nad zprávou**, podle `canBePinned` z `getMessageProperties`.
  Která z nich se nabídne, se bere ze seznamu připnutých ve stavu — `ChatMessage` příznak
  `isPinned` nenese a update o připnutí by ho stejně musel přemapovat v celé historii.
- **Připnutí má dialog s jednou volbou**, která mění, koho se dotkne: ve skupině a kanálu
  „upozornit členy/odběratele" (`disableNotification`), v soukromém chatu „uvidí i protějšek"
  (`onlyForSelf`). Upozornění už nikdo nevezme zpátky, proto se ptá dřív. V Uložených zprávách
  volba nemá smysl a připíná se rovnou. Soukromý chat připíná bez upozornění — protějšek uvidí
  servisní zprávu, oznámení navíc by byl šum.
- **Odepnutí z nabídky jde stejnou cestou jako křížek v pruhu**, včetně „Vrátit".
- **Totéž platí pro čtení textu hlášky.** Efekt u `notice` vynuloval událost a TEPRVE POTOM
  četl text (`getString` je `suspend`) — restart efektu po vynulování to čtení zrušil a hláška
  se ukázala, jen když byl text náhodou už načtený. Všechno, co se má po vynulování ještě
  udělat, patří do `snackbarScope.launch`.
- **Past: jednorázová událost ve stavu a `LaunchedEffect` klíčovaný tou událostí.** Když efekt
  událost hned vynuluje, změní tím vlastní klíč, restartuje se a zruší i to, co právě spustil.
  Snackbar „Vrátit" se tak neukázal nikdy. Snackbar proto běží v `rememberCoroutineScope`,
  mimo tělo efektu.

## Skok na citaci a tlačítko dolů

Ťuknutí na citaci v bublině odroluje na citovanou zprávu a krátce ji zvýrazní. Tlačítko „dolů"
se ukáže i bez nových zpráv, jakmile je uživatel o kus výš.

- **Načtená zpráva = okamžitý skok, jinak okno kolem ní** (`loadAround`, viz „Okno v historii").
  Skočit jde libovolně hluboko — citace na zprávu starou roky i výsledek hledání.
- **Na citaci a mezi výsledky hledání se DOJÍŽDÍ** (`animateToAnchor`, `slowSpatialSpec`),
  skok na první nepřečtenou a návrat dolů zůstávají okamžité. Okamžitý skok neukázal, kterým
  směrem a jak daleko zpráva je. Položka mimo obrazovku nemá změřenou polohu: dojede se na odhad
  z průměrné výšky viditelných položek a pak se doladí podle skutečného layoutu.
- **Ke vzdálené nebo nenačtené zprávě se PŘEJEDE** (`JumpTransition`): seznam odjede směrem
  k ní a zprůhlední, neviditelně se přesune (u nenačtené mezitím doběhne `loadAround`), nalezená
  zpráva se objeví rovnou na místě a zvýrazněná a sousedé k ní sjedou shora i zdola (vzdálenější
  z dál). Rolovat přes desítky zpráv (`MAX_ANIMATED_JUMP_ITEMS`, 40) by trvalo a skládalo by
  všechno mezi; do nenačtené historie dojet nejde vůbec.
- **Odjezd začíná HNED, ne až po načtení** (`ConversationState.pendingJump`). `jumpToMessage`
  ho ohlásí před `loadAround`, takže čekání na TDLib schová odjezd. Když se zpráva nenajde,
  `pendingJump` zmizí bez `scrollTarget` a seznam se vrátí (`restore`). Druhé volání `leave`
  nad rozjetým odjezdem jen pokračuje ze současné hodnoty — `Animatable` předchozí animaci přeruší.
- **Směr zná u nenačtené zprávy jen ViewModel** (`ScrollTarget.older`: menší id než nejstarší
  načtená). Po výměně okna je nový seznam dole a pozice by vždycky tvrdila „starší".
- **Sjíždění sousedů je modifikátor na kořeni KAŽDÉHO bloku** (`Modifier.gathered`, za
  `animateItem`). Obalit položku v `LazyColumn` dalším `Box`em nejde: `animateItem` musí být na
  kořeni položky, jinak jeho animace přestanou fungovat.
- **Dojíždí se jen ke zprávě, která v seznamu UŽ BYLA** (`ScrollTarget.animate`, rozhoduje
  `jumpToMessage`). Po výměně okna začíná nový seznam u spodní hrany okna, a tam se hned
  dotahují novější zprávy — ty se vkládají na začátek a posunou všechny indexy. Dojíždění
  z tohohle místa skončilo u úplně jiné zprávy a nalezená nebyla vidět.
- **Cíl se po dojetí hledá znovu podle id, ne podle indexu ze začátku jízdy** (`currentIndex`
  nad `rememberUpdatedState(items)`). Během jízdy se můžou dotáhnout stránky; `items`
  zachycené při spuštění efektu jsou v tu chvíli staré.
- **Zpráva, kterou okno neobsahuje, byla smazaná** — hláška „už není k dispozici" (u citace
  i u výsledku hledání vlastní věta). „Moc hluboko" už nenastane.

## Nepřečtené zmínky, reakce a hlasy v anketě

Nad tlačítkem dolů jsou tlačítka „@" (nepřečtené zmínky a odpovědi), „❤" (nepřečtené reakce na
vlastní zprávy) a anketa (nové hlasy ve vlastní anketě, TDLib 15) s počtem v odznaku
(`UnreadMarkButton`); v seznamu chatů je u chatu odznak s ikonou vedle počtu nepřečtených
(`UnreadBadges`). Počty drží TDLib (`Chat.unreadMentionCount`, `unreadReactionCount`,
`unreadPollVoteCount`), doména `UnreadMarks`, testy `UnreadMarksTest`.

- **Ťuknutí skočí na NEJSTARŠÍ** (`searchChatMessages` s `SearchMessagesFilterUnreadMention`
  / `UnreadReaction`, `oldestOf`), jako skok na nepřečtené. Výsledky jsou od nejnovější, bere se
  nejmenší id ze stránky po stovce.
- **Cíl se hlásí jako zhlédnutý hned po skoku** (`markRead`), nečeká se na obrazovku. Jinak by
  další ťuknutí během pár set milisekund našlo tutéž zprávu a tlačítko by nevedlo dál.
- **Reakce jsou na VLASTNÍCH zprávách, a ty se dřív jako zhlédnuté nehlásily vůbec** — hlášení
  přečtení filtruje odchozí zprávy. Reakce by tak zůstaly nepřečtené napořád a „❤" by
  nezhaslo, ani když na zprávu uživatel doskočil. Vlastní zpráva se proto hlásí, když nese
  nepřečtené reakce (`ChatMessage.hasUnreadReactions` z `Message.unreadReactions`, živě přes
  `UpdateMessageUnreadReactions`; pravidlo `messagesToView`). Zhlédnutí je přečte — ověřeno.
  **Totéž hlasy v anketě** (`ChatMessage.hasUnreadPollVotes` z `containsUnreadPollVotes`, živě
  přes `UpdateMessageContainsUnreadPollVotes`); obě změny nese jeden tok `unreadMarkChanges`.
  Že je zhlédnutí přečte, ověřené NENÍ — je to předpoklad podle reakcí.
- **Počty chodí ŠESTI updaty**: `UpdateChatUnreadMentionCount`, `UpdateMessageMentionRead`,
  `UpdateChatUnreadReactionCount`, `UpdateMessageUnreadReactions`,
  `UpdateChatUnreadPollVoteCount` a `UpdateMessageContainsUnreadPollVotes` (updaty zpráv nesou
  i počet).
  Konverzace si na každý z nich přečte chat znovu (`getChat` je lokální), seznam chatů bere
  počet přímo z updatu.
- **Podržení nabídne „Označit vše jako přečtené"** (`readAllChatMentions` /
  `readAllChatReactions` / `readAllChatPollVotes`), jako u oficiálních klientů.
- **Ve vlákně komentářů tlačítka nejsou** — počty patří celé diskusní skupině a vedly by do
  zpráv mimo vlákno.
- **Odznak v seznamu počet nenese** — jde o to, ŽE zmínka je; kolik, řekne konverzace.
- **Ověřuje se botem**: `setMessageReaction` na vlastní zprávu v chatu s botem vyrobí nepřečtenou
  reakci (změna emoji je nová nepřečtená). Ověřeno na emulátoru: odznak v seznamu, tlačítko
  s počtem, skok se zvýrazněním a zhasnutí, podržení a „Označit vše". **Skutečná zmínka ověřená
  v testovací skupině s botem** (`text_mention` účtu): tlačítko „@" s počtem, skok na zmínku
  a zhasnutí. Aby tlačítko vůbec naskočilo, musí zmínka přijít MIMO obrazovku (odrolovat nahoru,
  pak ji poslat) — viditelná se přečte hned.
- **Reakce na zprávu, která už JE vidět, se musí přečíst taky** (`unreadMarkedIds` ve
  `snapshotFlow` hlášení přečtení, test `zpravySNeprectenouReakciNeboHlasem`). Hlášení chodí jen
  na ZMĚNU viditelných položek a příchod reakce je nezmění — tlačítko „❤" pak viselo nad zprávou,
  na kterou se uživatel díval, dokud nezaroloval nebo na něj neťukl. Ověřeno pokusem: před
  opravou tlačítko po 10 s pořád, po ní se neukáže vůbec.
- **Hlas v anketě bot nevyrobí** (Bot API hlasovat neumí) a vlastní účet ve vlastní anketě
  nepočítá. Tlačítko s anketou i odznak v seznamu se proto ověřily podstrčeným počtem pro chat
  s testovacím botem; ťuknutí prošlo hledáním s `SearchMessagesFilterUnreadPollVote` bez chyby
  (prázdný výsledek). **Skutečný nový hlas ověřený se dvěma účty** (12. 10. 2026, neanonymní
  anketa ve skupině, hlasuje druhý účet): odznak „Nové hlasy v anketě" v seznamu chatů,
  v konverzaci tlačítko „1 nový hlas v anketě", ťuknutí skočí na anketu a tlačítko zmizí;
  zhlédnutá anketa odznak zhasne. **Aby tlačítko naskočilo, nesmí být z ankety vidět ani
  zápatí** — pod pruhem nahoře (třeba „Pozvi do nové skupiny lidi") vykukující kousek se počítá
  za zhlédnutí a hlas se přečte hned. Nový hlas vyrobí i změna hlasu (zrušit a hlasovat znovu).
- **Ťuk podle souřadnic ze STARÉHO dumpu otevřel cizí chat** — seznam se mezi dumpem a ťuknutím
  přeskládal. Dump se dělá TĚSNĚ před ťuknutím a po něm se ověřuje název v hlavičce
  (`uiautomator dump`), než se na obrazovce cokoli dalšího udělá.

## Hledání v konverzaci

Lupa v hlavičce chatu přepne hlavičku na **vyhledávací pilulku** a místo vstupního pole ukáže
**plovoucí toolbar** „N z M" se šipkami (`InChatSearchTopBar`, `InChatSearchNavigator`). Stav je
`ConversationState.search` (`InChatSearch`, čistá data a přechody, testy `InChatSearchTest`);
`null` = zavřené.

- **Že se hledá, musí být poznat TVAREM, ne textem.** Hlavička i spodek byly dřív v rolích, které
  na ploše stránky nejsou vidět: pole bylo `TextField` s průhledným kontejnerem i indikátorem,
  tedy k nerozeznání od nadpisu chatu (jediná změna oproti klidovému stavu byl zmizelý avatar),
  a navigátor byl pruh v `surfaceContainer`, což je v tmavém tématu rozdíl na hranici
  rozlišitelnosti — z celého pruhu byly vidět jen dvě šipky zavěšené v prázdnu. S prázdným
  dotazem tak na obrazovce nebyl JEDINÝ signál, že je aplikace v jiném režimu.
- **Pilulka je tentýž prvek jako hledání v seznamu chatů.** Jeden jazyk pro hledání v celé
  aplikaci; na rozloženém Foldu vypadá hlavička konverzace při hledání stejně jako hlavička
  seznamu vedle ní.
- **Pilulka nejde postavit na `TopAppBar`.** Ten titulek MĚŘÍ na celou šířku, ale UMÍSŤUJE ho
  16 dp od kraje, takže by pilulka o ten kus přetekla za pravý okraj. Hlavička hledání je proto
  vlastní řádek s ručními insety (`TopAppBarDefaults.windowInsets`) a výškou 64 dp — ta musí
  lícovat s hlavičkou konverzace, se kterou se střídá.
- **Pole v pilulce je `BasicTextField` s `DecorationBox`**, ze stejného důvodu jako vstupní pole
  konverzace: `TextField` si bere minimální výšku 56 dp a odsazení 16 dp napevno, takže by se
  vedle ikony do pilulky nevešel.
- **Výběr člena stojí VEDLE pilulky, ne v ní.** Je to akce nad hledáním, ne součást dotazu —
  a tři ikony uvnitř by na úzkém telefonu nenechaly místo na text.
- **Toolbar se bez kritérií neukazuje.** Prázdný dotaz nemá co počítat a šipky by byly jen
  zašedlé. Plovoucí prvek smí naskočit, protože nemá v layoutu pevné místo: výšku drží `Box`
  kolem něj, takže se seznam zpráv pod ním nepřeskládá.
- **Obsah plovoucí lišty se MUSÍ vystředit sám** (`Modifier.align(Alignment.CenterVertically)`).
  `HorizontalFloatingToolbar` centruje na výšku jen Row, do kterého skládá svůj vlastní
  `leadingContent` / `trailingContent`; ten NÁŠ obsah dostane vnořený `Row` BEZ
  `verticalAlignment`, tedy s výchozím `Alignment.Top`. Výšku lišty určí 48dp `IconButton`y
  a cokoli nižšího se přilepí k horní hraně — změřeno `uiautomator dump`: text „1 z 52" měl
  střed o 14 dp výš než šipky vedle něj a celá lišta působila rozházeně. U výběru zpráv to
  nebylo vidět, protože tam jsou všechny položky stejně vysoké `IconButton`y.
- **`animateContentSize` patří na POPISEK, ne na celou lištu.** Na toolbaru ho vlastní
  `clipToBounds` ořízne i o stín a s výchozím `Alignment.TopStart` se obsah po dobu animace
  lepí doleva — tedy mimo střed, kvůli kterému je lišta v centrovaném `Box`u. Na popisku
  s `alignment = Alignment.Center` se šířka mění od středu a lišta kolem něj jen povyroste.
  Minimální šířka popisku (`STATUS_MIN_WIDTH`) pak zabrání poskočení mezi indikátorem
  načítání a první pozicí.
- **Lišta je jeden kus pro hledání i výběr zpráv** (`ConversationFloatingBar`). Dvě kopie
  téhož `Box` + `AnimatedVisibility` + toolbaru se rozešly hned při prvním ladění odsazení,
  a rozdílná výška slotu by znamenala, že se spodek obrazovky mezi režimy hýbe.
- **Plocha konverzace při hledání USTOUPÍ, neposune se k bublinám** (`surfaceContainerLowest`).
  Výraznější tónování by šlo jen směrem k `surfaceContainerHigh` příchozí bubliny, tedy proti
  čitelnosti zpráv; takhle se rozdíl naopak o kousek zvětší a nálezy vyskočí. Je to proto vědomě
  jemné — silnější posun v tomhle směru role nenabízejí.
- **`searchChatMessages`, stránkované `nextFromMessageId`** (po 50). Další stránka se dotahuje,
  až k ní uživatel došipkuje — u „google" má dlouhý chat přes 1 600 výsledků.
- **Každý výsledek je skok přes `jumpToMessage`**, tedy i okno v historii (viz „Okno
  v historii"). Výsledek starý roky se tím otevře stejně rychle jako včerejší.
- **Šipka nahoru = starší**, protože tak výsledky leží v konverzaci nad sebou; pozice 1 je
  nejnovější. Tak to dělá i oficiální klient.
- **Počet je odhad serveru, ale nikdy menší než načtené** (`count`). Server u hledání vrací
  `totalCount` přibližně; `-1` = neví.
- **Dotaz s pauzou 300 ms a rušením předchozího** (`searchJob`). Stránka pro starý dotaz se
  zahodí (`withNextPage` porovná `resultsQuery`).
- **„Nic nenalezeno" jen k dotazu, na který server odpověděl** (`nothingFound` porovnává
  `resultsQuery` s oříznutým `query`) — během psaní dalšího znaku by jinak problikávalo.
- **Text drží pole samo** (`rememberSaveable`), ne stav ViewModelu: hodnota vrácená přes
  `StateFlow` o dispatch později by poskakovala kurzorem.
- **Zpět zavírá hledání** (`NavigationBackHandler` povolený jen s otevřeným hledáním), teprve
  další zpět odchází z chatu. Konverzace zůstane na posledním výsledku.
- **Ukázaný výsledek zůstává zvýrazněný** (barva a obíhající vlnitý okraj, viz „Vzhled
  konverzace") — po jednorázovém pulzu by se v konverzaci ztratil.
- **Ve skupině jde hledat podle člena** (ikona `person_search` v hlavičce hledání →
  `MemberPickerSheet`, `searchChatMembers` podle jména; `searchChatMessages` se `senderId`).
  Vybraný člen je čip před polem, ťuknutím se filtr zruší. S členem stačí prázdný dotaz —
  výsledkem jsou všechny jeho zprávy (`InChatSearch.hasCriteria`), text k tomu hledání zúží.
  V soukromém chatu a v kanálu se nenabízí: odesílatelé jsou dva, respektive jeden.
- **Výsledky patří dvojici dotaz + odesílatel** (`resultsQuery`, `resultsSenderId`): stránka
  i „nic nenalezeno" pro jinou dvojici se zahodí. Výběr člena hledá hned, bez pauzy — je to
  hotové rozhodnutí, ne psaní.
- **Členové jen podle jména, ne seznamem.** Server u velké skupiny vrátí jen část; anonymní
  správce (odesílatel = chat) filtrovat nejde, `searchMembers` bere jen uživatele.
- **Hledané slovo je v textu zvýrazněné u KAŽDÉ zprávy**, ne jen u ukázaného výsledku
  (`MessageText` → `RichText(highlights)`). Je vidět, proč se zpráva našla. Zvýrazňuje se dotaz,
  ke kterému patří výsledky (`resultsQuery`), ne rozepsaný text.
- **Dotaz jde do bublin přes `derivedStateOf`** (`ConversationItemScope.searchHighlight`). Čte
  se v kompozici textu; s celým stavem by každá změna konverzace překreslila text všech bublin.
- **Zvýraznění je obrácená bublina**: podklad v barvě textu, písmo v barvě bubliny. Funguje na
  odchozí, příchozí i zvýrazněné bublině bez jediné pevné barvy.
- **Podklad je zaoblená pilulka kreslená za textem** (`drawHighlightPills` z `TextLayoutResult`,
  po řádcích), ne `SpanStyle.background` — ten zaoblit nejde a hranaté obdélníky v zaoblených
  a zvlněných bublinách působily jako cizí prvek.
- **Slovo v zakrytém spoileru se nezvýrazní** (`visibleHighlights`, testy
  `SearchHighlightTest`) — zvýraznění by ho prozradilo.
- **`highlightRanges` / `foldDiacritics` jsou v `core/ui`** (`SearchHighlight.kt`), sdílí je
  hledání napříč chaty i konverzace. Obojí hledá bez ohledu na diakritiku jako TDLib.

## Skok na datum

V hledání v konverzaci je vedle výběru člena ikona kalendáře (`InChatSearchTopBar`,
`JumpToDateDialog`): vybraný den → první zpráva od jeho začátku (`firstMessageFrom`) → skok
jako na citaci (`jumpToMessage`, okno v historii), hledání se zavře.

- **`getChatMessageByDate` vrací POSLEDNÍ zprávu NEJPOZDĚJI v daný okamžik, ne první po něm.**
  Cíl je proto zpráva za ní: poslední zpráva před půlnocí vybraného dne + `getChatHistory`
  o kus novější, nejstarší z novějších (`firstMessageAfter`, testy `JumpToDateTest`).
- **TDLib napřed vrací, co má lokálně.** S oknem jedné novější přišla u kanálu se starou
  historií zpráva samotná a jedna STARŠÍ — novější vůbec, takže skok tiše skončil jako „nic
  tam není". Okno je proto pět novějších a dotaz se zopakuje až třikrát (na emulátoru stačil
  první).
- **Den starší než chat** (404 od `getChatMessageByDate`): historie od nejmenšího id serveru
  (`1 << 20`) směrem k novějším dá nejstarší zprávu. **Den novější než poslední zpráva**: nic
  novějšího není → hláška „Od toho dne v chatu nic není", hledání zůstane otevřené.
- **Den je MÍSTNÍ**: kalendář počítá v milisekundách UTC, den se z něj přečte přes UTC a pak se
  vezme jeho místní půlnoc. Budoucí dny vybrat nejde.
- **Jen v celém chatu** (`canJumpToDate`): ve vlákně komentářů a v tématu by
  `getChatMessageByDate` trefil zprávu mimo něj, tam se kalendář nenabízí.
- **Kde cíl skončí**: s kotvou nahoře stojí po výměně okna zhruba v půlce obrazovky s předělem
  dne a nad ním je poslední zpráva před vybraným dnem — je vidět, kde den začíná. Ověřeno na
  emulátoru v kanálu se starou historií: 1. 1. 2021 → příspěvek ze 17. února 2021 (nic mezi
  nimi), 1. 6. 2021 → 30. července 2021; v čerstvé skupině 1. 10. → „Skupina vytvořena".
- `ConversationScreen` dostal jen jeden argument (`onJumpToDate`) — je na hraně počtu registrů
  (viz CLAUDE.md), dialog i jeho stav žijí v `InChatSearchTopBar`.

## Okno v historii

Skok na zprávu mimo načtené (hledání, citace, stovky nepřečtených) NENAČÍTÁ historii od
nejnovější až k ní. Načte **okno kolem ní** (`getChatHistory` se záporným `offset`: půl stránky
novějších, půl starších) a tím **nahradí** celý seznam. Ke starším se stránkuje jako vždy, k novějším
u spodní hrany (`loadNewer`, `offset = -PAGE_SIZE`), dokud stránka nepřinese nic nového.

- **`ConversationState.reachedEnd`** = okno sahá k nejnovější zprávě. Dokud ne, **cizí nové zprávy
  se zahazují** (`withMessage`) — za oknem by udělaly díru — a dorazí se stránkováním. Vlastní
  ODESÍLANÁ zpráva seznam srovná na ni (`reachedEnd = true`, `ScrollAnchor.Bottom`), starší
  se dotáhnou obvyklým stránkováním; poslat z historie tedy znamená skočit dolů.
- **Tlačítko „dolů" je v okně vidět vždy** a vede přes `jumpToLatest` (seznam znovu od nejnovější),
  ne rolováním — nejnovější zprávy v okně nejsou.
- **Stránka se použije, jen když okno mezitím nikdo nevyměnil** (`withNewerPage` / `withOlderPage`
  kontrolují krajní id). Pomalá stránka ze starého okna by se jinak vmíchala do nového.
- **Dotažená stránka k přítomnosti není „nová zpráva"** (`NewestItem.live`): posune nejnovější
  klíč, ale nesmí ukotvit seznam dole (`requestScrollToItem(0)` by přeskočil všechno mezi) ani
  připsat odznak nových zpráv.
- **Stav seznamu patří jednomu oknu** (`ConversationState.window`, `rememberSaveable(window)` nad
  `LazyListState`). Při výměně celého seznamu odešly všechny položky naráz a `animateItem` je
  nechal doznít na místě: předěl „Dnes" visel přes zprávy z roku 2021 a jezdil se scrollem.
  **Past: `remember { derivedStateOf { listState… } }` BEZ klíče** si pak pamatuje původní stav
  seznamu — plovoucí datum hlásilo „Dnes" nad rokem 2021. Všechno, co z `listState` odvozuje,
  má `listState` v klíči.
- **Reklama se v okně nekreslí.** Stojí pod nejnovější zprávou a ta v okně není.
- **TDLib smí vrátit míň, než se chtělo**, takže konec se pozná jen podle toho, že stránka
  nepřinesla nic nového — stejně jako konec historie.
- **Citace míří na STŘED obrazovky, nepřečtené k horní hraně** (`ScrollAnchor`). U citace je
  potřeba vidět, na co zpráva navazuje; u nepřečtených se čte od první dolů.
- **Zvýraznění je nutné.** Po skoku doprostřed historie by jinak nebylo poznat, na kterou
  z bublin citace mířila. Je to pruh přes celou šířku, ne obarvení bubliny — odchozí `primary`
  by změnu nesla špatně a u příchozí by to vypadalo jako změna stavu zprávy.
- **Citace si opakuje dlouhý stisk.** Má vlastní klikání, takže by stisk k bublině nepustila
  a nad citací by nešla otevřít nabídka — stejná past jako u fotky.
- **Tlačítko dolů je uprostřed** (`FabPosition.Center`). Vpravo viselo přímo nad tlačítkem
  odeslání a mikrofonu a ťuknutí o kousek vedle spustilo místo skoku dolů nahrávání.
- **Tlačítko dolů je kruh, s počtem pilulka.** Mezera před počtem je v obsahu, ne ve `spacedBy`:
  skrytý `AnimatedVisibility` je pořád potomek a rozestup by kruh nafoukl.
- **Ukáže se až o pár položek výš** (`SCROLL_DOWN_THRESHOLD_ITEMS`), ne při prvním pixelu —
  u spodní hrany by blikalo pod prstem.
- **Dolů se jede `animateScrollBy` o spočítanou vzdálenost, ne `animateScrollToItem(0)`**
  (`animateScrollToBottom`). Ten o pár zpráv nad spodkem skončil poskočením: seznam dojel dolů,
  zastavil, a pak všechny bubliny kromě nejnovější v jednom snímku vyskočily o ~330 px nahoru
  a sjely zpátky (`animateItem`). Přichází to z jeho závěrečného `snapToItem` — na pozici, kde
  seznam už stojí, Compose animátor položek záměrně neresetuje a seznam je v lookahead scope
  `SharedTransitionLayout`; poslední krok mechanismu ověřený není. Z dálky se to neukazovalo.
  - **Výšky položek pod obrazovkou** si seznam nepamatuje, i když přes ně uživatel právě
    vyroloval. Pamatuje si je `rememberMeasuredSizes` (klíč → výška z `visibleItemsInfo`);
    vzdálenost = jejich součet + `mainAxisItemSpacing` + `firstVisibleItemScrollOffset`.
    Neznámá výška nebo víc než `MAX_ANIMATED_JUMP_ITEMS` položek → původní `animateScrollToItem`.
  - **Pružina BEZ odskoku.** Za spodní hranou seznam nepokračuje: překmit by se o ni zarazil
    a pružina by se pak vracela nahoru.
  - **Ověřeno záznamem obrazovky rozloženým po snímcích** (`screenrecord`, posun mezi snímky
    korelací svislého profilu, Swift + AVFoundation — ffmpeg na Macu není). Stará verze: +331 px
    ve dvou jízdách ze dvou, nová: nic. **Log polohy z `withFrameNanos` tu klame**: čte
    `layoutInfo` z PŘEDCHOZÍHO rozvržení a emulátor má snímky po 14 i 46 ms, takže plynulá
    pružina vypadá trhaně — a skok, který dělá `animateItem` (ne rolování), v něm není vůbec.

## Hlášení přečtení a indexy

- **Z viditelných položek se bere KLÍČ, ne index.** Index do `state.messages` nesedí: před
  zprávami stojí sponzorované položky, takže v kanálu s reklamou byl výběr posunutý o jejich
  počet a za přečtené se hlásily zprávy, které uživatel neviděl. Klíč zpráv je rovnou jejich id.
  Platí to i do budoucna — jakmile do seznamu přibude cokoli dalšího (datové předěly, předěl
  nepřečtených), indexy se rozejdou znovu, klíče ne.
- **Efekt s odpočtem nesmí záviset na `state.messages`.** `LaunchedEffect(listState, state.messages)`
  se restartoval při každé nové zprávě a 400ms odpočet začínal od nuly, takže se v živém chatu
  nemusel dopočítat vůbec.
- **Vlastní zprávy filtruje ViewModel, ne obrazovka.** Ta zná jen id viditelných položek;
  dohledávat si k nim zprávy by znamenalo držet je kvůli tomu v kompozici.
- **V `LazyColumn` se nehledá průchodem seznamu.** `indexOf(message)` uvnitř `items` a hledání
  citované zprávy přes `firstOrNull` znamenaly kvadratické chování při scrollu dlouhou historií.
  Index dává `itemsIndexed`, citace se hledá v mapě složené jednou na změnu seznamu.

## Jeden prvek pro hledání: `SearchPill`

Hledání v seznamu chatů, v konverzaci, ve výběru člena i ve výběru chatu (přeposlání, sdílení)
kreslí tentýž prvek — pilulka `surfaceContainerHigh` s ikonou, polem a ✕ (`core/ui/SearchPill.kt`).

- **Tři různě vypadající pole = uživatel poznává hledání na každé obrazovce znovu.** Ve výběru
  cíle byl `TextField` s podtržením, ve výběru člena `TextField` se zaoblením 28 dp a v konverzaci
  ruční pilulka; rozešly se hned, jak se jedna z nich doladila.
- **Text drží pole samo** (`initialQuery` je jen výchozí hodnota). Hodnota vrácená přes `StateFlow`
  by přišla o dispatch později a kurzor by poskakoval — tentýž důvod jako u vstupního pole.
- **Uvnitř je `BasicTextField` s `DecorationBox`**, ne `TextField`: ten má minimální výšku 56 dp
  a odsazení 16 dp napevno, takže by vedle ikony v pilulce nezbylo místo na text.
- **Sloty `leading` a `prefix` si nesou odsazení samy** — tlačítko má kolem glyfu svoje, holá
  lupa ne. `leading` je v konverzaci zpět (zavírá hledání, ne obrazovku), jinde lupa; `prefix`
  nese čip vybraného člena.

## Odkrývání časů tahem

Tah doleva posune celou plochu zpráv a vpravo odkryje čas u každé z nich; po puštění se plocha
vrátí. V bublině čas není (viz „Čas a stav doručení"), takže tohle je vedle předělu dne a plovoucího
data jediné místo, kde se dá přečíst. S myší ukáže čas u zprávy i najetí (viz „Akce po najetí myší"
v `konverzace-akce.md`).

- **Je to gesto nad SEZNAMEM, ne nad položkou.** Čas u jedné zprávy neřekne, kdy přišly ty okolo,
  a u jediné bubliny by to vypadalo jako druhá polovina swipu na odpověď. Jede proto všechno
  najednou, včetně sponzorované karty — ta jen nemá co ukázat.
- **Bublina si proto nesmí vzít každý vodorovný tah.** `draggable` je po slopu směrově agnostický:
  vezme si i tah doleva, nikam ho nepromítne (posun je `coerceIn(0f, maxDrag)`) a rodiči už ho
  nepustí. Tah doleva se tím tiše zahazoval. Odpověď swipem proto bere jen tah DOPRAVA
  (`SwipeDirection.Right`), odkrývání jen doleva; opačný směr nespotřebují a propadne dál.
- **Svislý scroll má přednost a detektor se rozhoduje pomalu** (`detectHorizontalSwipe`, viz
  „Jeden detektor tahu do strany"). Seznam je potomek, dostane událost dřív
  a jeho spotřebování detektor rodiče zruší — jenže s `detectHorizontalDragGestures` stačilo, aby
  prst ujel doleva o jeden touch slop dřív než nahoru, a palec levé ruky jde na začátku tahu
  obloukem doleva: na telefonu se místo scrollu vysouvaly časy. Teď si tah odkrývání vezme až po
  **dvojnásobku touch slopu** (svislý scroll má práh kratší, takže šikmý tah si seznam vezme dřív)
  a jen když jde **aspoň 2× víc do strany než svisle** a doleva. Na emulátoru se to ověřuje
  `input swipe` se screencapem uprostřed tahu (`input swipe … 1500 & sleep 1; screencap`).
- **Posun se čte až při kreslení** (`graphicsLayer { translationX = -reveal() }`, lambda, ne
  hodnota). Jinak by každý snímek gesta přeskládal celou konverzaci.
- **Čas čeká za pravým okrajem** a přijíždí s posunem. Sloupec má pevnou šířku a posun se o ni
  zastropuje — gumové tahání dál by jen mátlo.
- **Ořezává ho jeho VLASTNÍ pruh, ne seznam** (`RevealingTime`). Spoléhalo se na ořez seznamu,
  jenže na široké ploše je položka jen sloupec 840 dp uprostřed seznamu (okraj
  v `contentPadding`, `ConversationColumn`): čas „za okrajem" ležel v okraji vedle sloupce
  a byl vidět pořád, a když byl okraj užší než pruh časů, useknutý (konverzace vedle profilu,
  okno ~1700 px). Ověřeno na desktopu (Windows) při různých šířkách okna.
- **Předěly dne se neposouvají.** Jsou to nadpisy nad blokem, ne zprávy, a čas u nich nedává smysl.
- **Čtečka obrazovky se tahem nikam nedostane**, takže čas je i v nabídce nad zprávou, prvním
  řádkem (den a hodina). Není to položka nabídky, nedá se na ni ťuknout. **Výška nabídky se
  počítá, ne měří**, takže se hlavička musí připočíst — jinak by nabídka seděla vedle.
- **`asTimeOfDay()` není `@Composable`** — hodiny a minuty žádné prostředky nepotřebují a ve
  24hodinovém zápisu se pořadí mezi jazyky nemění. Den vedle nich už prostředky potřebuje
  (`asDayLabel`), a proto je v nabídce, ne u zprávy.

## Odpověď swipem

Tažení zprávy doprava otevře odpověď. Je to nejčastější akce nad zprávou a přes nabídku by na ni
vedly dva kroky.

- **Při scrollu se zakládala odpověď** — bublina si tah brala přes
  `awaitHorizontalTouchSlopOrCancellation`, tedy po JEDNOM touch slopu a jen podle vodorovné
  složky. Bublina je potomek seznamu a dostane dotyk dřív než on, takže u šikmého tahu vyhrála
  ona. Teď jede na sdíleném `detectHorizontalSwipe` (viz „Jeden detektor tahu do strany").
- **Bublina jede s prstem od položení** (první `onDrag` nese celý posun z rozhodování), takže
  práh `REPLY_THRESHOLD` (64 dp) je celá dráha prstu. Dřív bublina začínala až za slopem
  a prst ušel slop + 56 dp — práh se zvedl, aby dráha zůstala stejná.
- **Táhne se doprava u OBOU stran konverzace.** Zrcadlit směr podle odesílatele by znamenalo,
  že uživatel musí před každým gestem přemýšlet, čí zpráva to je. Odchozí bublina se přitom kusem
  vysune za pravý okraj — tak to dělá Telegram i WhatsApp.
- **Odpověď odchází při PUŠTĚNÍ, ne při překročení prahu.** Kdo si to rozmyslí, vrátí prst zpátky
  a nic se nestane. Příznak `armed` hlídá, aby se haptika ozvala jednou za každé překročení
  prahu a odpověď odešla nejvýš jednou.
- **Za prahem klade tah odpor** (30 %). Bez toho by šla bublina odtáhnout přes půl obrazovky
  a nebylo by poznat, že se dál už nic nestane.
- **Posun i průhlednost šipky se čtou až v layoutu a při kreslení** (`offset { }`,
  `graphicsLayer`), ne v kompozici. Jinak by každý snímek gesta znamenal přeskládání bubliny
  i s obsahem.
- **Šipka je uvnitř `Box`u kolem bubliny, posazená `offset`em mimo něj.** Do layoutu tím
  nezasahuje, takže nic neodsouvá a neovlivňuje šířku zprávy — a jede s bublinou, takže je
  jasné, čeho se gesto týká.
- **Vlevo od bubliny u obou stran**: tam vzniká mezera, když se táhne doprava.
- **U kraje obrazovky vyhrává systémové gesto zpět.** Bublina má odsazení 12 dp, takže se
  s ním u příchozích zpráv kousek překrývá. Nedá se s tím nic dělat a stejné to je i v Telegramu.

## Nápovědy gest napoprvé

Tři gesta, která jinak nikdo neobjeví, mají jednou za instalaci `RichTooltip` se šipkou
(`GestureHints.kt`, pravidlo `nextGestureHint`, testy `GestureHintTest`): odkrývání časů tahem
(bublina u pravého okraje seznamu), podržení odesílacího tlačítka pro volby (efekt, bez zvuku,
naplánovat) a podržení mikrofonu / kamery pro nahrávání. Co už uživatel viděl, si pamatuje
`AppSettings.seenGestureHints` (názvy výčtu `GestureHint`, neznámý se zahodí).

- **Jedna za návštěvu chatu a až po 1,5 s klidu** (`HINT_DELAY_MS`). Kandidát se počítá
  pořád znovu a každá změna podmínek čekání začne od nuly; vyskočit hned po otevření by
  přerušilo první pohled do chatu. Návštěva se pamatuje `rememberSaveable` podle chatu, takže
  otočení displeje neukáže hned další.
- **Viděná je od chvíle, kdy se UKÁZALA**, ne až po „Rozumím". Kdo ji zavře ťuknutím vedle,
  znovu ji vidět nechce. A **použité gesto ji zavře a označí taky** (`GestureHints.used`):
  tah odkrývání, podržení pro volby, začátek nahrávání.
- **Jen nad obrazovkou, na které nic nečeká** (`conversationHintContext`): načtený chat, žádná
  nabídka nad zprávou, výběr, hledání, úprava, sheet ani nahrávání. Když podmínka přestane
  platit (začne psát, otevře nabídku), bublina zmizí, i když ji uživatel ještě nedočetl —
  visela by nad něčím jiným. První verze podmínku na načtený chat neměla a nápověda vyskočila
  nad prázdnou hlavičkou „Konverzace".
- **Pořadí: volby odeslání, nahrávání, časy.** Volby jdou vidět jen při psaní, tedy vzácně;
  časy se týkají čtení a počkají.
- **Obrazovka podmínky NEČTE v kompozici.** `ConversationScreen` je na hraně (viz „Gotchas")
  a skládat ji kvůli nápovědě by bylo plýtvání: podmínky jdou přes `snapshotFlow` nad lambdami
  (`rememberGestureHints`) a bublinu čtou jen kotvy ve vlastních funkcích
  (`RevealTimesHintAnchor`, `ButtonGestureHint`). Co dělá tlačítko odeslání, ví jen vstupní pole,
  a tak ho do `GestureHints` zapisuje samo (`SideEffect`) a při odchodu pole vynuluje — jinak
  by hlášení viselo, když pole nahradí lišta výběru.
- **`TooltipBox` NEPŘEDÁ rodiči zarovnání.** `Modifier.align(Alignment.CenterEnd)` předaný jako
  `modifier` neudělal nic a kotva skončila v levém horním rohu seznamu (změřeno sondou:
  bounds 0,310). Bublina se pak „vešla" vpravo od ní a šipka ukazovala ven z obrazovky.
  Zarovnání proto nese vlastní `Box` kolem `TooltipBox`. Kotva je 16 dp od kraje, jinak leží
  šipka za okrajem.
- **Nápověda propouští dotyky** (nefokusovatelný popup): tah doleva přes ni rovnou odkryje
  časy a ťuknutí vedle ji zavře (`onDismissRequest`).
- **Bublina se kreslí z kořene kompozice** (`RootTooltipBox`, ne holý `TooltipBox`): nápověda
  visí, dokud ji uživatel nezavře, a kotva u tlačítka odeslání leží ve slotu `Scaffold`u. Změna
  šířky okna na desktopu by ji odebrala uprostřed měření a shodila vykreslování — viz
  `docs/poznatky/desktop.md`, „Bubliny (tooltip) z kořene kompozice". Šipka z boku (časy)
  funguje, protože v kořeni stojí náhradní kotva na mezích té skutečné.
- **Ověřeno na emulátoru** (česky, chat s testovacím botem): nahrávání s kamerou, volby
  odeslání s efekty nad tlačítkem, časy u pravého okraje (sondou, která časy brala jako
  neviděné) a jejich zavření tahem. iOS jen přeložené.

## Jeden detektor tahu do strany

Odkrývání časů, odpověď tahem i archivace v seznamu chatů jsou tah do strany nad svislým
seznamem a mají JEDNO pravidlo (`detectHorizontalSwipe` a `horizontalSwipeDecision`
v `core/ui/HorizontalSwipe.kt`, testy `HorizontalSwipeTest`). Dřív to byly tři kopie a dvě
z nich se opravily, zatímco třetí (odpověď) zůstala citlivá — přesně to, čemu má jedno místo
bránit.

- **Rozhoduje se až po DVOJNÁSOBKU touch slopu a jen u tahu aspoň 2× víc do strany než svisle**
  (~27°), jen ve zvoleném směru (`SwipeDirection`). Svislý scroll má práh kratší, takže šikmý
  tah si seznam vezme dřív, než se tu o čemkoli rozhodne.
- **Spotřebování se hlídá i ve fázi `Final`.** Položka seznamu (bublina, řádek chatu) dostává
  události ve fázi `Main` DŘÍV než seznam, takže rozjetý scroll ve `Main` nevidí; ve `Final`
  už ano. Stejně to dělá `awaitTouchSlopOrCancellation` z Compose.
- **Práh ohlašuje i TVAR, ne jen haptika** (`SwipeThresholdBadge` v `core/ui`, archivace
  i odpověď tahem): ikona pod taženou položkou je do prahu kolečko, za prahem naroste,
  morfne do sušenky (`ShapeMorphs.Rest` — „připraveno, pusť a stane se") a vyplní se akcentem.
  Haptiku nemá každý zapnutou a v ruce se dá přehlédnout.
  - **Tvar sleduje AKTUÁLNÍ polohu prstu** (`derivedStateOf { |drag| >= práh }`). Kdo prst
    vrátí před práh, vidí kolečko a ví, že po puštění se nic nestane. `derivedStateOf`, aby se
    nic nerekomponovalo každý snímek tahu.
  - **Haptika se ozve při KAŽDÉM překročení prahu, ne jen při prvním v tahu.** Příznak `armed`
    se dřív shazoval až při puštění, takže po vrácení prstu před práh a novém překročení se
    ikona změnila, ale vibrace nepřišla. Teď `armed` sleduje polohu stejně jako ikona
    (`armed = past`) a haptika odchází jen při přechodu z „před prahem" za práh. Zavírání
    prohlížeče tahem (`dismissArmed`) to tak dělalo odjakživa. Hystereze tu není schválně:
    haptika má sedět s ikonou, a ta se překlápí přesně na prahu.
  - **Pružina je rychlá (`StiffnessMediumLow`, tlumení 0,5)**, ne `morphSpring`: odezva patří
    k tahu a musí přijít s vibrací, ne o půl vteřiny později.
  - **Výplň se měří proti ploše pod ikonou** (`accentOn`). Na `primaryContainer` podkladu
    archivace `primary` neprojde AA a výplň je `onPrimaryContainer` s ikonou v barvě podkladu
    — ve světlém i tmavém motivu čitelné. U odpovědi (plocha konverzace) je `primary`.
  - **Archivace potřebuje šířku řádku i mimo `pointerInput`** (práh je podíl šířky), proto
    `onSizeChanged`. Ověřeno na emulátoru tahem přes `input motionevent` zastaveným před
    prahem a za ním a vráceným zpátky (nic se nearchivovalo ani nezaložila odpověď).
- **Plynulost a to, jestli to pod palcem sedí, se posuzuje na telefonu** — emulátor s `input
  swipe` ověří jen směr a pořadí rozhodnutí.

## Rychlý přepínač chatů

Podržení šipky zpět v hlavičce konverzace nabídne chaty, mezi kterými uživatel přechází
(`ChatSwitcher`, `ChatSwitcherBackButton`); ťuknutí na chat ho otevře místo současné konverzace.

- **Šipka zpět, ne tah po vstupním poli.** Roadmapa navrhovala tah doleva/doprava po poli nebo
  stažení hlavičky. Tah po poli se pere s posunem kurzoru, výběrem textu a odpovědí tahem;
  stažení hlavičky s posouváním historie. Podržení „zpět" zná uživatel z prohlížečů (historie
  stránek) a na šipce jinou funkci nemá.
- **Podržení se pozná z interakcí tlačítka.** `IconButton` dlouhý stisk neumí a obalit ho
  detektorem gest by mu vzalo stisk, a s ním změnu tvaru (viz „Icon buttony mění tvar při
  stisku"). `LaunchedEffect` proto poslouchá `PressInteraction.Press` a po
  `longPressTimeoutMillis` otevře nabídku (`collectLatest` čekání přeruší puštěním). Puštění po
  podržení pak dorazí jako `onClick` a musí se přejít (`heldDown`), jinak by se konverzace
  zavřela pod otevřenou nabídkou. Haptiku volá aplikace sama (`longPress()`), protože gesto
  detekuje sama. Čtečce nese akci `semantics { onLongClick(label = "Přepnout chat") }`;
  v `uiautomator` je pak uzel tlačítka `long-clickable="true"`.
- **Co nabídnout: naposledy otevřené, pak vršek hlavního seznamu** (`switcherChats`, testy
  `ChatSwitcherTest`), nejvýš šest položek bez otevřeného chatu. Historie je jen v paměti:
  nese „s kým si teď píšu" a po restartu ji nahradí pořadí v seznamu, které říká skoro totéž.
  Archivovaný chat jen z historie, sponzorovaný nikdy, Uložené zprávy ze seznamu jen připnuté
  (stejně jako v seznamu chatů). Id, které v seznamech není (chat z odkazu, jiný účet), se
  přeskočí: bez souhrnu nemá jméno ani fotku.
- **„Otevřený" se zapisuje z obrazovky (`LaunchedEffect(chatId)`), ne z ViewModelu.**
  ViewModel konverzace přežívá pod jinou obrazovkou; po návratu zpět by se znovu nezapsal
  a chat, na který se uživatel právě vrátil, by v nabídce chyběl na prvním místě. Téma nebo
  vlákno se zapíše jako svůj chat.
- **Přepnutí srovná zásobník Chatů na seznam** (`AppNavigation.switchChat`), jako by se na chat
  ťuklo v seznamu: zpět pak vede na seznam, ne do chatu, odkud se přepnulo. Přepínání sem a tam
  by jinak vršilo zásobník donekonečna. Funguje stejně z archivu (konverzace se otevře
  v hlavní větvi). Skupina s tématy otevře seznam témat, Uložené zprávy po chatech seznam svých
  témat — podle téže volby jako ťuknutí v seznamu.
- **V bublině konverzace se přepínač nenabízí** (`onSwitchChat = null`): bublina je jeden chat
  a jiný do ní nepatří.
- **Ověřeno na emulátoru Fold** (rozložený i složený): nabídka s fotkami a odznakem (ztlumený chat
  neutrálně), jedna vibrace `constant=0`, puštění po podržení konverzaci nezavřelo, přepnutí
  z tématu do chatu a z chatu do skupiny s tématy (otevřel se seznam témat), zpět z přepnutého
  chatu vede na seznam. **iOS jen přeložené.**
