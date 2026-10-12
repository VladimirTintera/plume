# Ankety a kvízy

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Ankety a kvízy

Anketa se kreslí jako vlastní blok (`PollMessageItem`) nad doménovým `ChatPoll` (`ChatMessage.poll`,
převod `MessagePoll.toChatPoll` v `MessageContent.kt`, pravidla a testy `ChatPollTest`): hlasování
jednou odpovědí ťuknutím, víc odpověďmi přes „Hlasovat", výsledky s pruhy a procenty, u kvízu
správná a vybraná špatná odpověď a vysvětlení. V nabídce „Zrušit hlas" a „Ukončit anketu",
v zápatí „Kdo hlasoval" (`PollVotersSheet`, `getPollVoters` po stránkách, testy `PollVotersTest`).

- **Popis „Anketa: otázka" nese `ChatMessage.description`** (jako `UiText`, viz „Jazyky")
  a blok ho jen nekreslí. Citace, připnutý pruh, náhled v seznamu chatů i shrnutí ho berou,
  když zpráva vlastní text nemá — anketa se tak nemusí doplňovat do každého z nich zvlášť.
  „Kopírovat" u ankety není (vlastní text nemá) a `composerEdit` ji vylučuje výslovně.
- **U ankety `canBeEdited` NEZNAMENÁ „jde upravit"**, ale „jde ukončit" (dokumentace
  `stopPoll`). Bez výjimky v `composerEdit` by se u vlastní ankety nabídlo „Upravit" a pole
  by upravovalo text „Anketa: …" voláním, které server odmítne.
- **Hlasuje se indexem v PŮVODNÍM pořadí**, ne v tom, jak se odpovědi ukazují. TDLib 15 posílá
  `optionOrder` (zamíchané pořadí); `PollAnswer.index` nese původní pozici a `setPollAnswer`
  i `getPollVoters` berou právě ji. Neplatné indexy v pořadí se zahodí a chybějící odpověď se
  připojí na konec — ztracená odpověď by byla horší než jiné pořadí.
- **Hlas se lokálně nepřepisuje.** Po `setPollAnswer` TDLib sám změní obsah zprávy
  (`UpdateMessageContent` → `editedMessages`): napřed s `isBeingChosen` (u odpovědi se točí
  indikátor), pak se skutečnými počty. **`UpdatePoll` je jen pro boty** — kdo na něj čeká,
  nedočká se.
- **Po hlasování jdou výsledky jen s `canSeeResults`.** Anketa si je může schovat do uzavření;
  pak zůstane vidět vlastní volba a věta „Výsledky uvidíš, až anketa skončí".
- **„Zrušit hlas" jen s `allowsRevoting`, u kvízu nikdy.** Rozhoduje o tom anketa sama
  (`canRetract`), ne příznaky od serveru — proto je to kandidát bloku, ne právo z nabídky.
- **Zakázaný řádek odpovědi NESMÍ mít `enabled = false`.** Vzal by s sebou i dlouhý stisk a nad
  anketou, kde se hlasovat nedá, by nešla otevřít nabídka. Řádek je `combinedClickable` vždycky,
  jen bez vlnky a s prázdným ťuknutím.
- **Pruhy výsledků vyjedou od nuly jen tehdy, když se výsledky odkryly před očima** (hlas,
  uzavření). Položka se při scrollu skládá znovu a pruhy by jinak jezdily při každém průletu.
- **Šířka ankety je pevná** (nejvýš 360 dp), ne podle obsahu: krátké odpovědi by udělaly úzkou
  bublinu, ve které rozdíl 30 a 40 % na pruhu nejde vidět.
- **`PollVotersViewModel` čte parametry Koinu INDEXEM** — `chatId` a `messageId` jsou dvě `Long`
  a podle typu by se zaměnily (tatáž past jako u `messageId` konverzace).
- **Ověřuje se testovacím botem**: `sendPoll` do soukromého chatu projde (anketa, kvíz
  s `correct_option_id` a `explanation`, `allows_multiple_answers`, `is_anonymous=false`),
  a `stopPoll` botem ukáže uzavřenou anketu živě. Ověřeno na emulátoru (česky, světlý i tmavý
  motiv): hlas jednou i víc odpověďmi, špatná odpověď na kvíz s vysvětlením, zrušení hlasu,
  „Kdo hlasoval", uzavření.
- **Přidat odpověď do cizí ankety jde, když to server dovolí** (`Poll.canAddOption`, tlačítko
  „Přidat odpověď“ pod odpověďmi → `AddPollOptionDialog` → `addPollOption`). Právo nese anketa
  sama (autor ho zapnul a účet smí), aplikace nic nedopočítává — jen u uzavřené ankety tlačítko
  schová, i kdyby příznak zůstal. Nová odpověď se lokálně nepřidává: přijde úpravou obsahu
  zprávy jako hlas, a server k ní pošle servisní zprávu. Strop délky je týž jako ve formuláři
  (`POLL_ANSWER_MAX`). Ověřuje se `sendPoll` botem s `allow_adding_options=true`
  a `is_anonymous=false`; `getUpdates` pak ukáže `added_by_user` a `poll_option_added`.
- **Přidaná odpověď smí nést přílohu** — tatáž ikona a nabídka jako u odpovědi ve formuláři
  (`MediaSlot`, výběr GIFu, samolepky, odkazu a místa je společný `PollMediaChooser`), TDLib
  u `addPollOption` bere tytéž druhy. Příloha žije jen v dialogu (přežít otočení nemusí).
  **Výběry se otevírají NAD dialogem**: sheet i systémový výběr fotek jsou vlastní okna
  přidaná později, takže leží nahoře a po výběru se vrací do dialogu. Ověřeno samolepkou:
  sheet nad dialogem, náhled v poli, odpověď s kachnou v anketě a Bot API ukázalo
  `options[].media.sticker`.
- **V zápatí jsou tváře posledních hlasujících** (`Poll.recentVoterIds` → `ChatPoll.recentVoters`,
  nejvýš `MAX_RECENT_VOTERS` = 3, přes sebe, nejnovější navrchu, s lemem v barvě bubliny;
  `FaceStack` v `core/ui`, společný s čipem komentářů).
  Server je posílá jen u neanonymní ankety. Jména a fotky dohledává repository z keše
  odesílatelů (`resolveSender`) při převodu zprávy — `toChatPoll` je čistá a nechává je prázdné.
  Čtečce obrazovky nic neříkají: kdo hlasoval, je v „Kdo hlasoval". Víc tváří než vlastní jeden
  účet nevyrobí — přes sebe se ověřovaly sondou, která seznam doplnila botem a iniciálami.
  **Dvě skutečné tváře ověřené se dvěma účty** (12. 10. 2026): „V" a „P5" přes sebe, „2 hlasy".
- **Zakládání je obrazovka `PollComposerRoute`** (sponka → Anketa), bez metadat scény jako
  přeposílání: jde přes celou plochu z hlavního seznamu i archivu, zpět umí sám `NavDisplay`.
  Pravidla formuláře jsou čisté funkce (`pollDraftOf`, `normalizedAnswers`,
  `correctAfterRemoval`, testy `PollComposerTest`).
- **Do soukromého chatu s ČLOVĚKEM anketa nejde** — TDLib: *„Polls can be sent to a private
  chat only if the chat is a chat with a bot or the Saved Messages chat."* Server by ji odmítl
  až po odeslání, proto o tom rozhoduje `ContentAccess.polls` (právo `canSendPolls` A druh
  chatu, `pollsAllowedIn`) a sponka ji jinde nenabízí. Chat s botem je proto i místo, kde se
  zakládání dá ověřit.
- **`ContentAccess.polls` nemá výchozí hodnotu, a schválně.** S `= true` se starý test skupiny
  „jen text" tiše změnil ve skupinu, kde se smí anketa, a sponka ožila. Každé místo, které práva
  skládá, musí o anketě rozhodnout výslovně.
- **Na konci odpovědí je vždycky jedno prázdné pole**, dokud se nedojde ke stropu — tlačítko
  „Přidat odpověď" by bylo ťuknutí navíc u každé odpovědi. Strop počtu posílá server
  (`poll_answer_count_max`, na emulátoru 12). Prázdné pole UPROSTŘED zůstává, odebírá se
  křížkem; správná odpověď kvízu se po odebrání drží TEXTU, ne pozice.
- **Kvíz v TDLib 15 smí mít víc správných odpovědí** (`correctOptionIds` je seznam) — nabízí se
  s „Víc odpovědí" (zaškrtávátka místo koleček).
- **Text se nad strop kousek pustí** (a Odeslat zašedne, počítadlo zčervená), aby šel vložit
  delší text a zkrátit ho; utnout ho hned by vložení tiše usekalo.
- **V kanálu je anketa vždycky anonymní** — přepínač se nekreslí a repository pošle
  `isAnonymous = true` i tak (neanonymní server do kanálu nepustí).
- **Odpověď anketou: formulář rozepsanou odpověď PŘEVEZME a po odeslání ji ohlásí jako
  spotřebovanou.** Routa nese `replyToMessageId` a citovaný úryvek, formulář ukáže citaci nad
  otázkou (křížek = anketa odejde bez odpovědi). Po úspěšném odeslání zavolá
  `ConversationReplyRequests.replySent` — STAV, jako požadavek odpovědi z prohlížeče — a
  konverzace odpověď zahodí, když pořád míří na tutéž zprávu (`replyConsumedBy`). **Dřív to
  konverzace poznávala z příchozí zprávy** (vlastní anketa ve stavu `Sending`), jenže
  NAPLÁNOVANÁ anketa do historie nepřijde (viz „Naplánované zprávy") a odpověď nad polem pak
  visela. Odpověď se nezahazuje při OTEVŘENÍ formuláře — kdo ho zavře, má ji mít dál.
- **Doba trvání je `openPeriod`, přesný čas `closeDate` — a jen jedno z nich.** Doba nezávisí
  na hodinách telefonu, proto je výchozí; nabídka (5 minut až týden) se ořízne stropem serveru
  `poll_open_period_max` — na emulátoru 2 628 000 s, tedy měsíc; bez stropu se volba nekreslí.
  Poslední položka nabídky („Vybrat datum a čas…", vlastní skupina) otevře výběr data a pak
  času; řádek se pak jmenuje „Konec ankety" — „Doba trvání: zítra 18:48" nesedělo.
  „Výsledky až po skončení" (`hideResultsUntilCloses`) se nabízí jen s koncem ankety.
- **Vlastní čas se hlídá PŘED odesláním** (`closeDateValid`, testy `PollComposerTest`):
  nejdřív za 5 minut (`POLL_MIN_CLOSE_AHEAD_SECONDS` — těsný čas by během vyplňování vypršel),
  nejpozději za strop serveru. Neplatný čas zůstane v řádku červeně s větou, dokdy smí anketa
  skončit, a Odeslat zašedne. **Čas stárne i bez úhozu**, takže obrazovka `now` ve ViewModelu
  obnovuje každých 15 s, dokud je vlastní čas nastavený.
- **Výběr data vrací půlnoc v UTC, hodina platí v MÍSTNÍM pásmu** (`closeDateOf`,
  `closeDaySelectable`). Tatáž past jako ve filtrech hledání. Strop 2 628 000 s je 30 dní
  a 10 hodin, takže nejzazší čas přeskočí přes změnu letního času — zkontrolováno: 8:51 v září
  → „25. října 17:51".
- **Popis ankety** (`description`) je pole pod otázkou v jejím bloku; strop je
  `message_caption_length_max` (1 024). Odkazy v něm se dohledávají `getTextEntities`, jako
  u popisku přílohy — bez toho by odešly jako mrtvý text.
- **Kdo smí hlasovat jen v kanálu** („for channel chats only"): `membersOnly` (v kanálu déle
  než den) a `countryCodes`, nejvýš `poll_country_count_max` zemí (na emulátoru 12). Mimo kanál
  se blok nekreslí a repository omezení neposílá, ani kdyby ve formuláři zůstala. Země vybírá
  tentýž `CountryPickerSheet` jako přihlášení, v režimu víc zemí (fajfka místo předvolby, nad
  stropem zašedlé) — proto se přestěhoval z `features/auth` do `core/ui`.
- **Konec ankety je v podtitulu bubliny** („končí zítra 14:30", `asUpcomingTime`), jen dokud
  nenastal; pak server anketu uzavře a podtitul řekne „Konečné výsledky". Nepřepočítává se
  každou vteřinu — uzavření přijde updatem obsahu.
- **Tlačítko bota „vytvořit anketu"** (`KeyboardButtonTypeRequestPoll`, třeba @QuizBot) otevře
  týž formulář; `forceQuiz` / `forceRegular` schová přepínač kvízu (`PollComposerMode`).
  Ověřuje se `sendMessage` s `reply_markup.keyboard[…].request_poll`.
- **Ověřeno na emulátoru** v chatu s botem: kvíz se třemi odpověďmi, správnou a vysvětlením
  odešel, odpověď na vlastní kvíz v odchozí bublině (barvy správně / špatně tam ustoupí bílé
  a rozdíl nesou ikony), „Ukončit anketu" z nabídky s potvrzením, tlačítko bota s vynuceným
  kvízem, anketa jako odpověď s dobou 1 hodina (citace v bublině, „končí 08:30", odpověď nad
  polem zmizela), popis s odkazem a vlastní čas konce („končí zítra 18:48"). **Blok „Kdo smí
  hlasovat" jen podstrčeným `isChannel = true`** (sonda, necommituje se): výběr zemí, „2 země",
  neplatný čas. **V testovacím kanálu a skupině ověřené**: anketa ve skupině (odeslání,
  hlas, 100 % / 0 %), přidání odpovědi do cizí ankety ve skupině (bot `sendPoll`
  s `allow_adding_options`, „Sushi" přibylo) a anketa v kanálu s „jen členové" (server ji přijal).
  Že omezení opravdu platí, ověřit nejde — hlasovat by musel nečlen.
- **Sheet se na snímku ukáže až po chvíli.** `screencap` hned po ťuknutí zachytí obrazovku bez
  něj a vypadá to, že tlačítko nefunguje; `uiautomator dump` už ho vidí.

### Média v anketě a v odpovědích

Otázka i každá odpověď mohou nést přílohu (TDLib 15 `PollMedia`, doména `PollAttachment`).

- **Fotka, video, GIF, samolepka a soubor jsou `MessageMedia`**, stejně jako příloha zprávy
  (`PollMedia.toAttachment`). U otázky je proto kreslí tentýž `MessageAttachment` (rámeček podle
  poměru stran, GIF hraje sám, let do prohlížeče); odpověď má 44dp čtvereček (`AnswerMedia`).
  **Místo je `MessageMedia.Location`, tedy mapa jako u zprávy s polohou** (viz „Poloha
  a mapy"), u otázky s názvem a adresou pod ní, u odpovědi mapa ve čtverečku. Odkaz obdobu
  u zprávy nemá: řádek s ikonou, ťuknutí otevře web.
- **Náhled u odpovědi je samostatný cíl ťuknutí.** Řádek ťuknutím hlasuje; bez toho by nešla
  fotka odpovědi prohlédnout, aniž by se pro ni hlasovalo. Co otevřít nejde (soubor, zvuk),
  ťuknutí propustí řádku.
- **Příloha vysvětlení kvízu** (TDLib 15 `explanationMedia`, `PollQuiz.explanationMedia`) je
  pod vysvětlením, stejně velká jako příloha otázky (`PollQuestionMedia(slot = …)`), a stejně
  jako vysvětlení až po odpovědi. Do prohlížeče letí pod vlastním místem
  `POLL_EXPLANATION_MEDIA` (−3; −2 je `NO_POLL_MEDIA`), popiskem je text vysvětlení. Bot API
  ji pošle (`sendPoll` s `explanation_media`).
- **Formulář ji nabízí ikonou v poli vysvětlení**, se stejnou nabídkou jako u otázky: TDLib
  bere u vysvětlení tutéž sadu (animace, zvuk, soubor, poloha, fotka, místo, video), samolepku
  a odkaz ne — pravidlo je tedy `allowedForQuestion` a pojistka v `pollDraftOf`. Výběr jde přes
  `mediaTarget = POLL_EXPLANATION_MEDIA`. **Mimo kvíz se neposílá** (`explanationMedia.takeIf
  { quiz }`), ale ve formuláři zůstane: přepnutí na anketu a zpět ji nemá zahodit. Ověřeno na
  emulátoru kvízem s fotkou u vysvětlení v chatu s botem: Bot API ukázalo
  `explanation_media.photo` a po špatné odpovědi je fotka pod vysvětlením.
- **Pod jedním id zprávy je obrázků víc, takže klíč letu nese i přílohu**
  (`LocalPollMediaFlight` v `photoKey`, `MediaRoute.pollMedia`). Samotné id zprávy by spárovalo
  fotku odpovědi s fotkou otázky. Index odpovědi je PŮVODNÍ (`PollAnswer.index`), otázka
  `POLL_QUESTION_MEDIA`.
- **Prohlížeč ukazuje přílohu ankety samotnou a bez akcí** (`loadPollMedia`). Reakce, sdílení,
  přeposlání i smazání by šly na CELOU anketu — přeposlat fotku odpovědi by odeslalo anketu.
  Popiskem je otázka, nebo text odpovědi.
- **Formulář nabízí všechny druhy, které TDLib bere** — ikona v poli otevře nabídku: fotka nebo
  video (systémový výběr), GIF a samolepka (tentýž `StickerPickerSheet` jako v konverzaci, jen
  s jedinou částí — `modes`), odkaz (dialog, `pollLinkOf`: bez schématu `https://`, jen
  http(s), doména s tečkou) a místo. **Otázka smí jen fotku, video, GIF a místo**
  (`PollDraftMedia.allowedForQuestion`) — samolepku a odkaz TDLib u otázky nebere a server by
  odmítl celou anketu; nabídka u otázky je proto kratší. GIF a samolepka se posílají jako
  `InputFileId` souboru, který server už má. **Příloha odpovědi jen s textem**: prázdné pole se
  neposílá a příloha by zmizela potichu; po vymazání textu se proto zahodí i ona. Přílohy patří
  TEXTU, ne pozici (`afterRemoval`, testy `PollComposerTest`).
- **Místa hledá inline bot, kterého jmenuje server** (`venue_search_bot_username`, `PlaceRepository`)
  — pomocník `InlineBot` je společný s GIF botem. **Bez polohy uživatele bot nevrací nic
  použitelného**: na „Staroměstské náměstí Praha" i na samotné „Praha" přišel supermarket
  z amerického předměstí. Hledá se proto kolem polohy zařízení; prázdný dotaz vrací místa
  v okolí a „Moje poloha" (holá `InputPollMediaLocation`) je vždycky první řádek.
- **Poloha zařízení je nová hranice `core/location`** (`rememberDeviceLocation`): jen PŘIBLIŽNÁ
  (`ACCESS_COARSE_LOCATION`, iOS kilometr) a až ve chvíli, kdy uživatel přidává místo. Android
  `LocationManager` bez Play služeb (čerstvá poslední známá poloha, jinak jedno zjištění
  s limitem 10 s), iOS `CLLocationManager` s delegátem drženým silnou referencí,
  `NSLocationWhenInUseUsageDescription` v projektu. `uses-feature` polohy je `required=false`,
  jinak by oprávnění udělalo z polohy povinný hardware a aplikace by zmizela z Play pro zařízení
  bez ní. Bez polohy sheet řekne, proč místa najít nejde.
- **`adb emu geo fix` polohu emulátoru nezměnil** (`dumpsys location` hlásí dál výchozí Mountain
  View, `fused` i `gps`). Na ověření to stačí — výsledky jsou místa kolem té polohy —, jen se
  nečekej Praha.
- **Bot API to umí od verze 10.0** (`sendPoll` s `media`, `options[].media`; odkaz u odpovědi od
  10.1), a tudy se ověřují druhy, které formulář nenabízí: místo u otázky, odkaz, poloha a GIF
  u odpovědí, GIF u otázky a samolepka u odpovědi.
- **Ověřeno na emulátoru** v chatu s botem: anketa s fotkou u otázky i u odpovědi odešla, fotka
  otázky i náhled odpovědi se otevřou v prohlížeči (s popiskem, bez akcí), ťuknutí na náhled
  nehlasuje, náhled je i ve výsledcích; od bota místo, odkaz s náhledem webu, poloha, GIF
  (s ikonou přehrání u odpovědi, hrající u otázky) a samolepka. Z formuláře odešla anketa s místem
  z hledání, odkazem (server doplní obrázek webu) a samolepkou u odpovědí a s GIFem u otázky
  a „Moje poloha" u odpovědi; dotaz na přibližnou polohu se ukáže až při „Místo". Mapa u otázky
  i u odpovědi otevře Google Mapy a ťuknutí na ni nehlasuje. **Ťuknutí na odkaz (otevření webu)
  ověřené není** — jde toutéž cestou jako odkaz v textu.
