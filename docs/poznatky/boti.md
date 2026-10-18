# Boti a mini aplikace

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Tlačítka botů pod zprávou

Inline klávesnice bota (`ReplyMarkupInlineKeyboard`) se kreslí pod zprávou jako blok zaoblených
tlačítek (`BotKeyboardView`, `WithBelowMessage` v `items/BotKeyboard.kt`; doména `BotKeyboard`
v `ChatMessage.keyboard`). Dokud nebyla, nešlo projít kontrolou proti botům, kterou si velké
skupiny dávají před vstup — odpověď napsaná textem se nepočítá, bot čeká na ťuknutí.

- **Data zpětného volání do domény NEJDOU.** Jsou to bajty od bota a klávesnice se mezi
  vykreslením a ťuknutím může změnit. Tlačítko se určuje POZICÍ (`MessageAction.PressBotButton`)
  a repository si data dočte ze zprávy (`pressBotButton`), včetně kontroly, že na tom místě je
  pořád tlačítko s tímtéž textem.
- **Bot klávesnici přepisuje bez změny obsahu** (odpočet pokusů, další krok menu) — přijde jen
  `UpdateMessageEdited`, ne `UpdateMessageContent`. `editedMessages` proto odebírá obojí.
- **Na odpověď se čeká u jednoho tlačítka naráz** (`pressingBotButton`, na tlačítku se točí
  indikátor). Druhé ťuknutí by zpětné volání poslalo podruhé a u kontrolní otázky s jediným
  pokusem by spálilo odpověď.
- **Odpověď bota má tři podoby** (`BotAnswer`): krátký text jako snackbar, upozornění
  (`showAlert`) jako dialog, který se musí potvrdit, a odkaz. Prázdná odpověď je běžná — bot
  jen přepíše zprávu.
- **Odkaz mimo Telegram se napřed potvrzuje** (`OpenLinkDialog` s celou adresou). U odkazu
  v textu uživatel adresu vidí, na tlačítku ne. Odkaz na Telegram (`resolveLink`) se otevře
  rovnou, jako odkaz v textu.
- **Co Plume neumí, je vidět a ťuknutí to vysvětlí** (`BotButtonKind.Unsupported`: platba,
  hra, heslo, inline dotaz s cílem daným odkazem). Mini aplikace a přihlášení na web už umí
  (viz níž a „Mini aplikace botů"). Schované tlačítko by
  z klávesnice bota vykouslo řádek nebo jeho část a změnilo, co bot nabízí.
- **Inline dotaz tlačítkem** (`BotButtonKind.SwitchInline`, `SwitchInlineTarget`) dá do pole
  `@bot dotaz` — v tomhle chatu, nebo v chatu z výběru (`SwitchInlinePickerScreen`). Nic se
  neposílá, výsledek si uživatel vybere z napovídání nad polem (viz „Inline boti").
  - **Jméno bota tlačítko nenese**: je to bot, „přes" kterého zpráva přišla, jinak odesílatel.
    Dohledává se při převodu zprávy, a jen u klávesnice s takovým tlačítkem. **Past:**
    v `toChatMessage` je `senderId` už převedený na číslo (`plainId`), takže přetypování na
    `MessageSenderUser` tiše vrátilo `null` a tlačítko spadlo do „neumím" — kladné číslo je
    uživatel.
  - **Výběr nabízí jen druhy chatu, které bot dovolil, a jen chaty, kam se smí psát**
    (`inlineQueryTargets`, `SwitchInlineTarget.Chosen.allows`). Druh se bere z položky seznamu
    a TDLib se ptá jen tam, kde chat projít může: dotaz na každý chat v seznamu trval na
    emulátoru přes minutu. Bota od člověka rozliší `getUser`, jen když bot dovolil jedno bez
    druhého.
  - **Dotaz do vybraného chatu je STAV** (`InlineQueryRequests`), jako odpověď z prohlížeče:
    konverzace se teprve skládá, a i otevřená si ho vezme až po obnovení konceptu — koncept jde
    do prázdného pole a dotaz by jinak vytlačil. Rozepsanou zprávu dotaz nepřepíše (stejné
    pravidlo jako ťuknutí na „přes @bot").
  - **Ověřeno na emulátoru** zprávou od testovacího bota (`switch_inline_query_current_chat`,
    `switch_inline_query`, `switch_inline_query_chosen_chat` jen se skupinami): pole v témž
    chatu, výběr jen se skupinami, kam smí psát, výběr všeho s Uloženými zprávami. Testovací bot
    inline režim nemá, takže se pod polem výsledky neukážou — **napovídání po tlačítku ověřené
    není**, jde ale cestou napsaného `@bot dotaz`.
- **Přihlášení na web** (`BotButtonKind.LoginUrl`, `getLoginUrlInfo` → `LoginUrlPrompt`):
  server buď řekne „otevřít" (jako odkaz, s potvrzením adresy, pokud ho nevypne
  `skipConfirmation`), nebo „zeptat se" — pak `LoginUrlDialog` s doménou v nadpisu, větou,
  co web dostane (jméno, uživatelské jméno, fotku), a u `requestWriteAccess` zaškrtávátkem
  „dovolit botovi psát" (výchozí zapnuté, jako u oficiálních klientů). Potvrzení vezme adresu
  z `getLoginUrl` a otevře ji.
  - **Chyba = obyčejný odkaz**, tak to předepisuje TDLib. Chyba `getLoginUrl` až po potvrzení
    otevře web bez přihlášení a řekne to hláškou — uživatel web chtěl.
  - **Tlačítko s `login_url` bot pošle, jen když má v BotFatheru doménu** (`/setdomain`), jinak
    Bot API vrátí `BOT_DOMAIN_INVALID`. Testovací bot ji nemá, takže se ověřovalo sondou:
    obyčejné odkazové tlačítko vydávané za přihlašovací (TDLib pak vrátí „Button not found"
    a nabídne se odkaz) a podstrčené `LoginUrlPrompt.Confirm` pro vzhled dialogu.
- **Tlačítka jsou vysoká rovnou 48 dp.** S nižším je `Surface` dorovná na dotykovou velikost
  PRŮHLEDNÝM okrajem a svislé mezery mezi řádky vyšly o 8 dp větší než vodorovné.
- **Šířka klávesnice: aspoň jako zpráva, víc jen když potřebuje**, do stropu šířky zprávy —
  vlastní `Layout`, ne `IntrinsicSize` (zpráva bývá fotka v `BoxWithConstraints`).
- **Barvy od bota** (`ButtonStyle` v TDLib 15): výchozí tonální `secondaryContainer`, „primary"
  `primary`, „danger" `errorContainer`, „success" `tertiaryContainer` — vlastní roli pro úspěch
  M3 nemá.
- **Ověřuje se testovacím botem přes Bot API**: `sendMessage` s `reply_markup.inline_keyboard`
  (i `copy_text`, `style`), ťuknutí v aplikaci a `getUpdates` → `answerCallbackQuery` (s `text`,
  `show_alert`). **`getUpdates` si update potvrdí až dalším voláním s `offset`** — skript, který
  skončí hned po odpovědi, dostane příště TENTÝŽ callback znovu a `answerCallbackQuery` na něj
  vrátí 400. Přepsání klávesnice se zkouší `editMessageReplyMarkup`.
- **Ve skutečných skupinách na tlačítka neťukat** — antispamoví boti mají pod zprávami
  „Skrýt" / „Za co?" a ťuknutí jedná jménem účtu.

## Vlastní klávesnice bota

Klávesnice, kterou bot posílá místo systémové (`ReplyMarkupShowKeyboard`, třeba BotFather),
a vynucená odpověď (`ReplyMarkupForceReply`). Kreslí je `ReplyKeyboardPanel` NAD vstupním
polem; doména `ChatReplyMarkup`, zdroj `ConversationRepository.replyMarkup`.

- **Nad polem, ne místo systémové klávesnice** (rozhodnutí uživatele). Výměna systémové
  klávesnice za panel tak, aby pole neposkočilo, je nejkřehčí část Compose — tady se nic
  nepřepíná a jde psát i ťukat zároveň. Cena je výška: strop (`MAX_HEIGHT`) s rolováním
  a šipka, která panel sbalí. Sbalení se pamatuje pro jednu klávesnici (klíč je její zpráva).
- **Klávesnice patří CHATU, ne zprávě.** Ze které zprávy se bere, drží TDLib
  (`Chat.replyMarkupMessageId`) a mění ji `UpdateChatReplyMarkup` (nová zpráva bota, jiná
  klávesnice, vynucená odpověď). Odběr stojí před prvním čtením.
- **Jednorázová klávesnice se po použití SCHOVÁ, NEZMIZÍ.** Bot API: „the keyboard will still
  be available … the user can press a special button to see it again". `deleteChatReplyMarkup`,
  které TDLib po klientovi chce, update nepošle a klávesnice v chatu zůstává; panel se proto
  po použití jen sbalí. Kdo čeká, že klávesnice zmizí, hledá chybu, která tam není.
- **Že byla jednorázová klávesnice použitá, si pamatuje aplikace** (`AppSettings.usedOneTimeKeyboards`,
  chat → id zprávy s klávesnicí, nejvýš 200 chatů). TDLib to neví: klávesnice v chatu zůstává
  a po znovuotevření chatu se ukázala rozbalená, jako by ji nikdo nepoužil. Oficiální klient na
  Androidu to řeší stejně (`hidekeyboard_<dialog>` v preferencích). Pamatuje se ZPRÁVA, ne
  příznak: nová klávesnice od bota má jiné id a ukáže se rozbalená. Ověřeno na emulátoru
  i po `force-stop`.
- **Text z tlačítka nemaže rozepsaný koncept** (`sendKeyboardText` s `clearDraft = false`).
  Běžné odeslání (`send`) koncept maže, protože posílá obsah pole — tlačítko ne.
- **Ve skupině jde text tlačítka jako odpověď** na zprávu s klávesnicí, jako u oficiálního
  klienta; v soukromém chatu je citace šum.
- **Sdílení čísla je za potvrzením** (`SharePhoneDialog`) — údaj o uživateli nejde odeslat na
  jedno ťuknutí. Zakládání botů (`RequestManagedBot`) se jen vysvětlí, jako u tlačítek pod
  zprávou; anketa otevře formulář, mini aplikace se otevře (viz „Mini aplikace botů").
- **Poloha z tlačítka je PŘESNÁ a za potvrzením** (`ShareLocationDialog`,
  `rememberDeviceLocation(precise = true)`). Bot s ní volá taxi nebo kurýra; kilometrová
  přesnost, která stačí na místa v okolí, by tam nic neřekla. Android žádá přesnou spolu
  s přibližnou (uživatel smí zvolit přibližnou a pošle se ta), iOS `kCLLocationAccuracyBest`.
  Když čerstvá nepřijde (GPS v budově), pošle se poslední známá do deseti minut, jinak hláška.
  - **GPS emulátoru nové fixy samo nedává a `adb emu geo fix` ho nezmění.** Polohu podstrčí
    testovací poskytovatel: `adb shell appops set com.android.shell android:mock_location allow`,
    `cmd location providers add-test-provider fused`, `set-test-provider-enabled fused true`,
    `set-test-provider-location fused --location 50.0875,14.4208`. Po testu
    `remove-test-provider fused` a appop vrátit na `deny`.
- **Výběr lidí a chatu je obrazovka** (`BotShareRoute`, `BotShareScreen` nad `ChatPicker`,
  `shareUsersWithBot` / `shareChatWithBot`). Lidí se vybírá víc (nejvýš `max_quantity`) a sdílí
  tlačítkem, chat se po ťuknutí potvrzuje dialogem. Nabízí se jen to, co seznam ví
  (`botShareCandidates`, testy `BotShareCandidatesTest`): soukromé chaty, nebo skupiny / kanály
  s tématy či bez nich; zbytek omezení (bot, prémiový účet, vlastník, veřejné jméno) ověří
  server při výběru (`onlyCheck`) a nevyhovující řádek se ohlásí hned, ne po odeslání.
  - **Že se bot do chatu PŘIDÁ a dostane práva správce, říká karta i dialog výslovně**
    (`botIsMember`, `botAdministratorRights`) — z tlačítka „vyber skupinu" to nikdo nečeká.
  - **`request_name` / `request_title` / `request_photo` klient NEVIDÍ.** V TDLib jsou „bots
    only" a uživatelský klient je dostane vždycky `false` — ověřeno: bot o název žádal, TDLib
    hlásil `requestTitle=false` a bot název přesto dostal. Karta proto říká, co bot dostat MŮŽE
    (jméno / název, uživatelské jméno, fotku), ne co žádá.
  - **Bot API chce u `bot_administrator_rights` i `user_administrator_rights`** (podmnožinu),
    jinak `USER_RIGHTS_MISSING`.
  - **Ověřuje se tlačítkem, které chce BOTY** (`user_is_bot: true`): sdílení lidí by testovacímu
    botovi poslalo účty skutečných kontaktů. Ověřeno na emulátoru: odmítnutí bota u tlačítka
    pro lidi, sdílení dvou botů (`users_shared`), vlastní testovací skupiny (`chat_shared`)
    a dialog u skupiny s botem jako správcem (zrušený — bot se do skupiny nepřidal).
- **Vynucená odpověď přepne pole na odpověď sama, jednou na zprávu**, a jen když se zrovna
  neupravuje ani neodpovídá jinam. Zpráva bota do seznamu dorazí klidně až po updatu, proto se
  na ni chvíli čeká. Po odeslání odpovědi se odebere (`deleteChatReplyMarkup`).
- **Nápověda do pole od bota** (`inputFieldPlaceholder`, `ConversationState.inputPlaceholder`,
  testy `ReplyMarkupTest`) platí u vynucené odpovědi jen tehdy, když se odpovídá právě na ni.
- **Ověřeno testovacím botem** (`sendMessage` s `reply_markup.keyboard`, `one_time_keyboard`,
  `input_field_placeholder`, `request_contact`, `request_location`; pak `force_reply`).
  **Odeslání čísla ověřené není**, jen dialog.

## Příkazy bota

`/příkaz` v textu zprávy je klikatelný a ťuknutí ho pošle (`MessageEntityType.BotCommand`),
po napsání „/" nad polem napovídá seznam příkazů a v chatu s botem je u pole tlačítko s robotem,
které seznam otevře celý (`BotCommandsPanel`, doména `BotCommandItem`, pravidla
`commandSuggestions` a `botCommandText`, testy `BotCommandsTest`).

- **Příkazy se čtou z plných údajů** (`botCommands`): v soukromém chatu `UserFullInfo.botInfo
  .commands`, ve skupině `BotCommands` všech botů z `BasicGroupFullInfo` / `SupergroupFullInfo`.
  Kanál a chat s člověkem žádné — tlačítko se tam neukáže. Čtou se jednou při otevření chatu.
- **Ve skupině se posílá `/příkaz@jmeno_bota`.** Botů tam může být víc a holý příkaz by dostali
  všichni, nebo žádný (podle soukromí bota). Tak to dělají i oficiální klienti. Příkaz ťuknutý
  v textu jde tak, jak v textu stojí.
- **Příkaz v textu je klikatelný jen tam, kam se smí psát** (`ConversationItemScope
  .canSendCommands`, `RichText(commandsClickable)`). Jinde — kanál bez práva psát, profil,
  anketa — zůstane obyčejným textem: modrý text, po kterém se nic nestane, je horší.
- **Ťuknutí na příkaz v textu nemaže koncept** (`sendKeyboardText`, jako tlačítko vlastní
  klávesnice). Návrh z napovídání naopak pole vyprázdní i s konceptem — rozepsané „/st" byl ten
  příkaz.
- **Napovídání je jen pro text bez mezery** (`commandSuggestions`): po mezeře uživatel píše
  argument nebo větu, která náhodou začíná lomítkem. Část za `@` se ignoruje.
- **Nad polem, ne ve sheetu**, jako vlastní klávesnice bota: napovídání se mění s každým
  písmenem a sheet by se o klávesnici přetahoval. Seznam z tlačítka zavře psaní i gesto zpět.
- **Ikona je robot (`smart_toy`)** — oficiální klient má pilulku „Menu", jenže tři čárky se
  v Material čtou jako navigační zásuvka.
- **Ověřeno testovacím botem**: `setMyCommands` (tři příkazy), zpráva s příkazy v textu. Seznam
  z tlačítka, napovídání po „/n", ťuknutí na návrh (pole prázdné) i na příkaz v textu — všechny
  tři příkazy dorazily botovi (`getUpdates`). V Uložených zprávách tlačítko není. **Ve skupině
  ověřené** (testovací skupina s botem): seznam nabízí `/stav@NoTiBoBoBot` a odejde s příponou.
  **`getUpdates` testovacího bota si občas vybere jiný proces** (sdílený token), takže update
  zmizí dřív, než se na něj dojde — bublina v konverzaci je pak spolehlivější doklad.
- **Tlačítko nabídky bota (`BotMenuButton`) stojí na místě tlačítka příkazů** — viz „Mini
  aplikace botů". Příkazy pak napovídá jen „/", jako u oficiálních klientů.

## Mizející zprávy botů

Ve skupině může bot poslat zprávu, kterou vidí jen jeden člen (Bot API 10.2, TDLib
`Message.receiverId`), a člen botovi zpátky taky — jen mizející příkaz a odpověď na mizející
zprávu (`sendEphemeralMessage`). Ostatní členové ji nevidí vůbec. Doména `Ephemeral`
(`ChatMessage.ephemeral`), převod `TdlEphemeral.kt`, štítek `EphemeralFooter`, testy
`EphemeralCommandTest`.

- **Pod zprávou stojí, kdo ji vidí**: „Vidíš to jen ty" u zprávy od bota, „Vidí to jen
  Notifikator" u vlastní. Bez štítku by vypadala jako běžná zpráva skupiny a uživatel by čekal,
  že ji čtou všichni. Řádek pod zprávou, ne v bublině (jako počty pod příspěvkem).
- **Bot umí obsah běžné zprávy nahradit jen pro uživatele** (po ťuknutí na tlačítko,
  `replace_callback_query_message`). TDLib to pošle jako `Message.ephemeralContent` s pokynem
  „ukaž místo běžného obsahu" a mění updatem `messageEphemeralContentUpdates` (zapojený do
  `editedMessages`). Převod si proto napřed udělá kopii zprávy s náhradním obsahem, tlačítky
  a `canBeSaved` (`withEphemeralContent`) a zbytek převodu o mizejícím obsahu neví. Pod zprávou
  je čip **„Zobrazit původní"** (`deleteMessageEphemeralContent`).
- **Kopie zprávy je postavená pojmenovanými argumenty BEZ výchozích hodnot.** Pole, které
  novější TDLib do `Message` přidá, rozbije překlad tady, místo aby se v kopii tiše ztratilo.
- **Obal nad převodem, ne větev v něm** (`toChatMessage` volá `toChatMessageContent`): převod
  je obří suspend funkce a další větev se suspend voláním ji už jednou rozbila v ART (viz
  `externalReply`).
- **Mizející cestu volí repository, ne obrazovka** (`sendEphemeralText`): odpověď na zprávu
  s `receiverId` od cizího odesílatele jde zpátky tomu botovi, příkaz, který bot označil
  `isEphemeral`, jde tomu botovi (`ephemeralCommandBot`). Se jménem bota (`/cmd@bot`) rozhoduje
  ten bot. Bez jména jde příkaz mizející cestou, jen když ho zná jediný bot ve skupině, jinak
  jako běžná zpráva. Platí pro psaní do pole (`send`) i pro seznam příkazů a ťuknutí na příkaz
  v textu (`sendKeyboardText`). Na to druhé se napoprvé zapomnělo a příkaz ze seznamu odešel
  celé skupině.
- **Koncept se po mizejícím odeslání maže zvlášť, ne přes `clearDraft`.** S `clearDraft = true`
  ho TDLib smazal jen u sebe a na serveru zůstal. Seznam chatů koncept neukazoval, ale po
  restartu aplikace (i na jiném zařízení) se text vrátil do pole. Smazat ho potom ručně
  (`setChatDraftMessage(null)`) nepomohlo: TDLib už koncept neměl, změnu neviděl a server se
  nic nedozvěděl. Proto se posílá s `clearDraft = false` a koncept se maže až po úspěšném
  odeslání, dokud o něm TDLib ještě ví.
- **Bot pošle mizející zprávu jen jako správce skupiny** (Bot API `BOT_NOT_ADMIN`). Testovací
  bot je proto správcem „Plume test triaze".
- **Příkazy skupiny drží TDLib v plných údajích a změnu `setMyCommands` hned nezjistí.** Nový
  příkaz se v seznamu ukázal až po restartu aplikace, opětovné otevření chatu nestačilo.
- **Ověřeno na emulátoru (Fold, hlavní účet, „Plume test triaze")**:
  - Mizející zpráva od bota se štítkem.
  - Odpověď na ni: bot ji dostal jako mizející odpověď na svou zprávu (`getUpdates`:
    `ephemeral_message_id`, `receiver_user`).
  - Nahrazený obsah po ťuknutí na tlačítko i s novými tlačítky a „Zobrazit původní", které
    vrátí původní zprávu.
  - Mizející příkaz `/tajne` ze seznamu příkazů došel botovi jako mizející, příkaz bez
    `is_ephemeral` jako běžná zpráva.
  - Koncept po mizející odpovědi zmizel i po restartu.
  - **Neověřené:** iOS a desktop (jen přeložené), mizející zpráva s médii, odpověď v tématu
    skupiny.

## Spuštění bota

Prázdný chat s botem je jeho **profil** (`BotIntroPage`): velká tvář nahoře uprostřed, jméno,
„Bot" a pod tím popis (`BotInfo`), obrázek nebo GIF od bota až pod textem. Místo vstupního pole
je tlačítko **Start** (`sendBotStartMessage`). Startovací odkaz `t.me/bot?start=…` pošle botovi
parametr (`InternalLink.BotStart`).

- **Profil, ne karta uprostřed.** První verze byla karta „Co tento bot umí?" uprostřed prázdné
  konverzace; uživatel chtěl, aby to vypadalo jako profil — tvář a jméno představí bota dřív
  než text. Kreslí se i u bota bez popisu (`showsBotIntro`): tvář a jméno řeknou, s kým chat je.
- **Hlavička nad profilem nenabízí hledání ani shrnutí** — v prázdném chatu není co hledat
  a lupa nad profilem působila jako ovládání stránky.
- **Tvář je nad profilem jediná**: hlavička avatar schová (`AnimatedVisibility`, po Startu se
  vrátí) a velká tvář převezme, co nesl — tečku „online". Výpadek spojení nenese ani ona, je
  v podtitulu hlavičky, která nad profilem zůstává (viz „Stav spojení a obnova po pozadí"
  v `databaze-spojeni-proxy.md`). Mezera mezi
  avatarem a jménem v hlavičce je proto odsazení AVATARU, ne `spacedBy` řádku — schovaný avatar
  by jinak nechal před jménem díru. Ověřeno letovým režimem.

- **Kdy Start, je čistá funkce** `showsBotStart` (testy `BotStartTest`): chat s botem
  (`botIntro != null`) a buď čekající parametr z odkazu, nebo PRÁZDNÁ historie. Prázdnou
  historii dokazuje až `reachedStart` — výchozí stav je taky prázdný a `loading` v něm ještě
  neplatí, takže by se u každého bota na okamžik ukázal Start.
- **Parametr z odkazu je STAV, ne událost** (`BotStartRequests`, jako `ChatOpenRequests`):
  odkaz se ťukne v jedné konverzaci a Start je v jiné, která ještě nemusí existovat. Přes routu
  se nepředává — změnila by se serializace zásobníku i parametry ViewModelu kvůli jedné hodnotě.
- **`autostart` rozhoduje TDLib**, ne my: u bota, se kterým chat už existuje, vrátí
  `InternalLinkTypeBotStart.autostart = true` a `/start` s parametrem odejde hned, bez
  tlačítka (tak to dělají i oficiální klienti). Tlačítko s čekajícím parametrem se tedy ukáže
  jen u bota, se kterým se ještě nepsalo.
- **V chatu je „/start" bez parametru**, parametr dostane jen bot — TDLib zprávu takhle vrací.
- **Odkazy, zmínky, hashtagy a příkazy v popisu jsou klikatelné** (`BotIntro.descriptionEntities`).
  Bot popis nastavuje jako holý text, entity dohledává TDLib lokálně (`getTextEntities`), jako
  u popisu skupiny v profilu. Ťuknutí jde TOUŽ cestou jako v textu zprávy (`MessageAction` přes
  scope obrazovky): `/help` z popisu odejde botovi, `@jméno` otevře chat nad konverzací. Ověřeno
  popisem nastaveným testovacímu botovi (`setMyDescription`) a sondou v `showsBotIntro`
  (necommituje se); popis se po ověření vrátil.
- **Ověřeno na emulátoru**: startovací odkaz na testovacího bota (autostart, bot dostal
  `/start hello`), prázdný chat s `@gif` (profil s popisem od serveru, bez lupy, a Start — **nestisknuté**,
  bot není náš) a tlačítko Start v chatu s testovacím botem přes sondu v `showsBotStart`
  (bot dostal `/start`; sonda se necommituje). **Neověřeno**: obrázek a GIF v profilu (testovací
  bot je nemá — Bot API je nastavit neumí, jen BotFather), čekající parametr u nespuštěného
  bota, iOS (jen přeložené).

### Přidání bota do skupiny

Odkaz `t.me/bot?startgroup=…` (`InternalLink.BotStartInGroup`) otevře výběr skupiny
(`AddBotToGroupRoute`, `AddBotToGroupScreen` nad `ChatPicker`) a po potvrzení bota přidá
a pošle mu `/start` s parametrem (`ChatRepository.addBotToGroup`). Pak se otevře skupina.

- **Nabízí se jen skupina, kam bota přidat JDE** (`canAddBot`, testy `BotInGroupTest`):
  vlastník vždy, správce s právem zvát (a s `&admin=…` s právem jmenovat správce), člen jen
  když skupina členům zvát dovolí, a nikdy jako správce. Seznam, ve kterém by většina skupin
  skončila chybou od serveru, je horší než kratší seznam — na emulátoru z něj zmizely velké
  skupiny, kde účet jen čte, a s `&admin=` i skupina, kde účet správce jmenovat nesmí.
- **Skupiny se berou z načtených seznamů** (hlavní a archiv). Archiv se načítá až při otevření,
  takže archivovaná skupina ve výběru chybí, dokud ho uživatel neotevřel.
- **Práva správce jsou přesně ta z odkazu** (`administratorRights`); potvrzení to říká vlastní
  větou. V ZÁKLADNÍ skupině jde jmenovat jen člena, takže se bot napřed přidá
  (`addChatMember`) a pak jmenuje; supergroupa ho přidá rovnou `setChatMemberStatus`.
- **Přidání se potvrzuje dialogem** — bot tím dostane přístup ke zprávám skupiny.
- **Ověřeno na emulátoru jen po potvrzovací dialog** (zprávou od testovacího bota s oběma
  odkazy, anglicky i česky, zrušení i zpět). **Samotné přidání a `/start` ověřené v testovací
  skupině** (bot z ní napřed odešel přes Bot API `leaveChat`): „Do skupiny přibyli členové",
  `/start@bot` a Bot API `getChatMember` → `member`. Do skutečných skupin se nic nepřidává.

## Inline boti

`@bot dotaz` na začátku pole (`@gif kočka`, `@wiki Praha`, `@pic cat`) ukáže nad polem výsledky
bota; ťuknutí výsledek pošle a u zprávy pak stojí „přes @bot" (`InlineResultsPanel`, doména
`InlineQuery.kt`, převod `InlineResultsMapping.kt`, pravidla `inlineQueryOf` a `InlineQueryState`,
testy `InlineQueryTest`).

- **Dotaz se pozná podle TVARU textu, ne podle bota**: zavináč na začátku, jméno a MEZERA. Do
  mezery může jít o zmínku; jestli `@jméno` je bot, se zjistí až dotazem (`inlineBot`,
  `searchPublicChat` + `getUser`, pamatuje se pro instanci klienta).
- **Jestli bot inline dotazy umí, TDLib 15 neřekne** — `userTypeBot` nemá `is_inline`
  (v knihovně ten řetězec je, ve wrapperu 15.0.0 pole ne). Pozná se to až odpovědí: chyba
  „Bot doesn't support inline queries" (ověřeno `strings libtdjsonjava.so`) panel schová a bot
  se do konce konverzace už neptá (`notInlineBots`). Ověřeno na `@BotFather`.
- **Dotaz jde po pauze 400 ms** a další písmeno ho zruší. Předchozí výsledky mezitím zůstávají
  vidět — stránka patří dotazu, na který se čeká, a stránka pro starý dotaz se zahodí
  (`InlineQueryState.withPage`), tentýž vzor jako výsledky GIFů.
- **Pole se čte v obrazovce, ne v `onDraftChanged`** (`LaunchedEffect(inlineQueryOf(draft.text))`).
  Text se do pole dostává i mimo psaní — vrácený koncept, sdílení, ťuknutí na „přes @bot" —
  a dotaz musí zareagovat na všechno z toho.
- **Posílá se `sendInlineQueryResultMessage` (queryId + id výsledku), ne obsah.** Zprávu
  si připravil bot a může se od náhledu lišit (článek odejde jako odkaz s náhledem webu).
  Proto i GIF z `@gif` odchází s „přes @gif" — na rozdíl od výběru GIFů, který stejného bota
  jen používá k hledání a posílá soubor.
- **Nad polem, ne ve sheetu**, jako napovídání příkazů: výsledky se mění s každým písmenem
  a klávesnice musí zůstat. Mřížka pro GIFy, fotky, obaly videí a samolepky; řádky přes celou
  šířku pro článek, soubor, hudbu, kontakt, místo a hru. Rozhoduje se po výsledcích — jeden bot
  vrací obojí. GIF buňka je tatáž jako ve výběru GIFů (`GifCell`), samolepka `StickerPreview`.
- **Tlačítko nad výsledky** (`InlineQueryResultsButton`): „nastavit u bota" otevře chat s botem
  a spustí ho s parametrem (`openBotStart` s `autostart`), mini aplikace jen vysvětlí, že ji
  Plume neumí (`@pic` má „Create image with Alice AI").
- **Bot s `needLocation` dostane přibližnou polohu** (`rememberDeviceLocation`); o oprávnění se
  žádá až u takového bota.
- **Dotaz na takového bota se pošle až s polohou** (`inlineLocationResolved`), nebo když je jasné,
  že nebude (nedovolená, nezjištěná do limitu — `request` pak vrátí `null`). Dřív odešel první
  dotaz hned bez polohy a s její příchodem druhý: bot odpověděl dvakrát a výsledky se pod prstem
  vyměnily (ověřeno v logu bota: `location None`, pak souřadnice). Že je bot „polohový", se
  pozná až z `getUser` (`InlineBotInfo.needLocation`), proto se čeká až po něm.
- **„přes @bot" je údaj o původu, stejně jako přeposlání** (`OriginLabel`, `OriginHeader`,
  `hasOriginLabel` v `MessageFrame.kt`): holý obsah s ním je karta, GIF od `@gif` má značku
  v hlavičce téže karty. Jméno bota se dohledává `getUser` (lokální, pamatuje se). Ťuknutí na
  značku dá `@bot ` do pole (`MessageAction.UseInlineBot`) — ale **rozepsanou zprávu
  nepřepíše**, to jen ohlásí; přepíše prázdné pole nebo jiný dotaz na bota.
- **Nápověda bota** (`inlineQueryPlaceholder`, „Search GIFs") je první řádek panelu, dokud je
  dotaz prázdný — do pole ji dát nejde, nápověda pole se ukazuje jen v prázdném poli.
- **Ověřeno na emulátoru** (Uložené zprávy): `@gif kočka` (mřížka hrajících GIFů, odeslání,
  „via @gif"), ťuknutí na „via @gif" (`@gif ` v poli, nápověda a populární), `@wiki Prague`
  (řádky, odeslání jako karta odkazu s „via @wiki"), `@pic cat` (fotky a tlačítko mini
  aplikace), `@BotFather x` (bez panelu). **Neověřeno**: video a samolepky ve výsledcích a iOS
  (jen přeložené).
- **Tlačítko „nastavit u bota" a bot s polohou ověřené testovacím botem.** BotFather mu zapnul
  inline režim (`/setinline`) a polohu (`/setinlinegeo`), na dotazy odpovídal skript přes Bot API
  (`getUpdates` s `allowed_updates: ["inline_query"]`, `answerInlineQuery` s `button.start_parameter`).
  Ťuknutí na tlačítko poslalo botovi `/start nastaveni`; dotaz s polohou odešel jednou, rovnou se
  souřadnicemi; po odmítnutí oprávnění odešel bez nich. Poloha emulátoru z testovacího
  poskytovatele (viz „Vlastní klávesnice bota"). Inline režim zůstal botovi zapnutý. **Ve skupině ověřené** (`@gif cat` v testovací skupině, GIF odešel). Obraz GIFů
  ve výsledcích i ve zprávě zůstal na emulátoru prázdný — selhává dekodér
  (`queueInputBuffer() failed`), viz „GIFy".
- **`chat_<id>` v `EXTRA_SHORTCUT_ID` se sdíleným textem je nejrychlejší cesta, jak dostat dotaz
  do pole** (`am start -a android.intent.action.SEND … --es android.intent.extra.TEXT '@gif kočka'`),
  i s diakritikou. Pozor na id testovacího bota — viz „Na snímcích nesmí být skuteční lidé".

## Mini aplikace botů

Tlačítko pod zprávou (`web_app`), tlačítko vlastní klávesnice bota, tlačítko nad výsledky inline
bota, tlačítko nabídky bota, dlaždice „Otevřít" v profilu bota a odkazy `t.me/bot?startapp`
a `t.me/bot/app` otevřou mini aplikaci přes celou obrazovku (`WebAppRoute`, `WebAppScreen`, `WebAppViewModel`).
Adresu i podepsaná data uživatele skládá TDLib (`WebAppRepository`); aplikace stránku jen
zobrazí a zprostředkuje její události.

- **`core/webview` je hranice** jako `core/camera`: `android.webkit` a `platform.WebKit` jen tam.
  WebView stránce vystavuje JEDINOU věc, `window.TelegramWebviewProxy.postEvent(typ, data)` —
  Android `addJavascriptInterface`, iOS stejně pojmenovaný objekt vložený skriptem na začátku
  dokumentu, který posílá do `WKScriptMessageHandler`. Soubory zařízení vypnuté, jiná schémata
  než http(s) stránka sama neotevře (má na to `web_app_open_link`).
- **Události překládá čistý most** (`WebAppBridge.kt`, testy `WebAppBridgeTest`), WebView data
  jen přenáší. Zpět se doručují `Telegram.WebView.receiveEvent(typ, data)`; typ i data jdou do
  skriptu jako JSON literály, nic se neescapuje ručně.
- **Na co stránka čeká odpověď, na to se odpoví, i když to Plume neumí** (telefon, zápis, faktura,
  celá obrazovka, plocha): „zrušeno" / „nepodporováno". Jinak by mini aplikace visela.
  Schránku mini aplikacím nedáváme (`data: null`).
- **Motiv jsou barvy aplikace** (`webAppTheme`: stránka `surface`, sekce `surfaceContainerLowest`
  jako bloky seznamů, tlačítka `primary`) — do adresy přes TDLib i do `theme_changed` po změně
  motivu. Hlavička a hlavní tlačítko jsou naše, přebarvit je smí stránka; popředí se pak volí
  podle světlosti.
- **`applicationName` je `android` / `ios`**, TDLib ho dá stránce jako `tgWebAppPlatform`. Vlastní
  jméno by mini aplikace nepoznaly.
- **Poprvé u každého bota souhlas** (`WebAppConsentDialog`, `AppSettings.confirmedWebAppBots`) —
  TDLib u `openWebApp` předepisuje jednou ukázat, že mini aplikace posílá data botovi.
  **Ptá se obrazovka mini aplikace, ne místo, odkud se otevírá** (`WebAppViewModel.start`, nic
  se nenačte, dokud se nepotvrdí; „Zrušit" obrazovku zavře). Dokud se ptala konverzace, musel by
  si souhlas hlídat každý nový vstup zvlášť — profil bota i odkaz by na něj zapomněly. Dialog
  čeká na jméno bota (lokální `getUser`), jinak by na okamžik jmenoval „Mini aplikaci".
- **Spuštění přes `openWebApp` se musí zavřít** (`closeWebApp` v `onCleared` přes `appScope`).
  Klávesnice a inline tlačítko jdou přes `getWebAppUrl` a spuštění nemají; **data botovi**
  (`web_app_data_send`) smí poslat jen mini aplikace z vlastní klávesnice (`keyboardButtonText`).
- **Zpět jde napřed do mini aplikace**, když má vlastní „zpět" (šipka v hlavičce místo ✕),
  jinak zavírá — s potvrzením, když si ho stránka vyžádala (`need_confirmation`).
  `NavigationBackHandler`, ne `BackHandler`.
- **Odkaz na Telegram z mini aplikace** (`web_app_open_tg_link`) ji zavře a otevře chat nad
  konverzací pod ní (`WebAppRoute.archive` říká, do které větve); jiné druhy odkazů jdou do
  prohlížeče.
- **Tlačítko nabídky bota** (`BotInfo.menuButton`, `BotMenuApp`, `BotMenuAppButton`) stojí
  u pole MÍSTO tlačítka příkazů a otevírá mini aplikaci přes `openWebApp`, jako tlačítko pod
  zprávou — TDLib adresu z `botMenuButton` bere právě tam. Jen v soukromém chatu s botem;
  čte se jednou při otevření chatu, jako příkazy.
  - **V prázdném poli plná pilulka s textem od bota, při psaní (a s přílohami) jen ikona** —
    text bývá dlouhý a na úzkém telefonu by poli ukousl místo, zrovna když se píše. Sbalená má
    barvu ostatních ikon v kartě, ne plnou plochu: vedle plného odesílacího tlačítka by se
    dvě sytá kolečka prala. Text se zkracuje na 120 dp.
  - **Adresa typu `t.me/bot/app` se otevírá jako odkaz** (`BotMenuApp.isLink`), ne přes
    `openWebApp` — tak to TDLib u tlačítka předepisuje.
  - **Adresa tlačítka NENÍ adresa stránky**, ale `menu://<adresa>` (ověřeno sondou). Server ji
    pošle takhle a `openWebApp` si ji přeloží sám. **Odkaz `t.me/bot/app` nastavený přes Bot API
    server zahodí** — přijde holé `menu://` a `openWebApp` skončí `URL_INVALID`. Cestu
    s `isLink` proto Bot API ověřit nejde (asi ji vyrobí jen BotFather); stojí na dokumentaci.
  - **Ověřuje se `setChatMenuButton` botem** s `chat_id` vlastního účtu (jen pro náš chat,
    ne globálně) a po ověření vrátit `{"type":"default"}`. Ověřeno na emulátoru (Fold, světlý
    i tmavý motiv): pilulka, sbalení s konceptem, souhlas a otevření ukázky `cafe`.
- **Hlavní mini aplikace bota** (`getMainWebApp`, `WebAppRoute.main`): dlaždice „Otevřít" v profilu
  bota (`ChatProfile.hasMainWebApp` z `UserTypeBot.hasMainWebApp`), první v řadě — u bota
  s aplikací je to to, kvůli čemu do profilu přišel. A odkaz `t.me/bot?startapp=…`
  (`InternalLink.MainWebApp`, parametr jde do `getMainWebApp`).
  - **Spuštění nemá, takže se nezavírá** — `getMainWebApp` vrací jen adresu.
  - **Bot bez hlavní mini aplikace se z odkazu otevře jako chat**, ne chybou; tak to dělají
    i oficiální klienti. Režim z odkazu (kompaktní / celá obrazovka) se neřeší, Plume kreslí
    vždy přes celou obrazovku. Panel s podmínkami botů v nabídce příloh (`getAttachmentMenuBot`)
    taky ne — nabídku příloh botů Plume nemá.
  - **Odkaz na jinou mini aplikaci z mini aplikace otevře chat jejího bota**, ne druhou mini
    aplikaci nad první — vrstvené by zpět mátlo.
  - **Ověřuje se na @BotFatherovi** — má hlavní mini aplikaci (správa vlastních botů), testovací
    bot ji mít nemůže (nastavuje se jen v BotFatheru). V ní nic neměnit, jen otevřít a zavřít.
    Ověřeno na emulátoru: odkaz se souhlasem i bez něj, „Zrušit" vrátí do konverzace, dlaždice
    v profilu.
- **Pojmenovaná mini aplikace z odkazu `t.me/bot/app?startapp=…`** (`InternalLink.WebApp`,
  `WebAppRoute.shortName`): napřed `searchWebApp` (jestli ji bot má — jinak věta „neexistuje",
  ne obecná chyba), pak `getWebAppLinkUrl`. Spuštění nemá, nezavírá se.
  - **Ptá se i u potvrzeného bota, když to server chce** (`needsWebAppConsent`, testy
    `WebAppConsentTest`): `skipConfirmation = false` znamená, že odkaz může poslat kdokoli, a
    `requestWriteAccess` se rozhoduje pokaždé zaškrtávátkem „Dovolit … posílat mi zprávy"
    (výchozí zapnuté, jako u přihlášení na web).
  - **Ověřeno na emulátoru** s `t.me/DurgerKingBot/menu` (ukázka Telegramu, žádá o psaní —
    zaškrtávátko se při ověřování VYPÍNÁ, jinak smí bot účtu psát) a s neexistující
    `t.me/BotFather/neexistuje`.
- **Druhé tlačítko** (`web_app_setup_secondary_button`, `WebAppBottomBar`) stojí vůči hlavnímu,
  kde si stránka řekne: vlevo a vpravo sdílí řádek napůl, nahoře a dole jsou pod sebou;
  viditelné jen jedno jde přes celou šířku. Bez barvy od stránky je hlavní plné (`primary`)
  a druhé tonální (`secondaryContainer`) — dvě plná vedle sebe by neřekla, které je hlavní.
  Barvu lišty smí stránka změnit (`web_app_set_bottom_bar_color`: hex, nebo klíč z motivu
  v TÉMŽE poli — rozliší se podle „#").
  - **Žádná dostupná mini aplikace druhé tlačítko neukazuje** (Durger King i BotFather ho
    posílají s `is_visible: false`), takže se ověřovalo sondou: po `web_app_ready` ViewModel
    sám podstrčí události, jaké by poslala stránka, a projde všechny polohy. Necommituje se.
    Že ťuknutí dorazí do stránky, se pozná z logu skriptu Telegramu v konzoli WebView
    (`adb logcat | grep receiveEvent` → `secondary_button_pressed`).
  - **Server po prvním otevření pojmenované mini aplikace vrací `skipConfirmation`** a o psaní
    už nežádá — souhlas se zaškrtávátkem se tak ukáže jen napoprvé. Kdo ověřuje souhlas,
    potřebuje aplikaci, kterou účet ještě neotevřel.
- **Tmavá hlavička od stránky přepne ikony stavové lišty na světlé** (`DarkSystemBars`).
  BotFather si nastaví tmavou hlavičku i ve světlém motivu aplikace a tmavé ikony na ní nebyly
  vidět. Rozhoduje táž světlost, podle které se volí popředí hlavičky.
- **Ověřeno na emulátoru** s ukázkou Telegramu `webappcontent.telegram.org/cafe` (bot ji pošle
  jako `web_app` tlačítko): souhlas jen poprvé, motiv, hlavní tlačítko v barvě od stránky a změna
  jeho textu, vlastní „zpět" v hlavičce i systémovým gestem, potvrzení zavření s plným košíkem,
  otevření z vlastní klávesnice (ukázka tam hlásí „zavřeno" — objednávat dovolí jen s `query_id`
  z `openWebApp`, to je vlastnost ukázky). **Neověřeno**: popup, vlastní metody, `web_app_data_send`,
  odkaz na Telegram z mini aplikace a iOS (jen přeložené).
