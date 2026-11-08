# Návrh: víc účtů naráz

> **Stav:** návrh, nic z toho není postavené. Vychází z kódu po #1166. Na roadmapě to stálo jako
> „Víc účtů (L), rozhodnutí, jestli vůbec, zatím nepadlo". Rozhodnutí padlo: chceme to.

Víc přihlášených účtů Telegramu v jedné aplikaci, jako v oficiálních klientech. Jeden účet je
**aktivní** (ten na obrazovce), ostatní běží na pozadí: chodí z nich notifikace a vidět je jejich
počet nepřečtených.

## Co umí TDLib

Všechno potřebné. Ověřeno v `td_api.tl` (větev master, 8. 11. 2026) a ve zdrojích wrapperu
tdl-coroutines 15.0.0. Projekt jede na 17.0.0, ale engine má stejnou stavbu.

- **Každý účet je samostatný klient** (`td_create_client_id`) s vlastním spojením a vlastními
  `databaseDirectory` a `filesDirectory` v `setTdlibParameters`. Dva klienti nad jedním
  adresářem se poškodí. Jinak se klienti v jednom procesu nijak neomezují.
- **Engine a přijímací smyčka jsou společné.** Wrapper (`TdlEngine`) dává každému
  `TdlClient.create()` vlastní id a odpovědi i updaty rozděluje podle něj. Dva klienty po sobě
  Plume vyrábí už dnes (`recreate()` po odhlášení).
- **API počítá s víc účty v zařízení:**
  - `registerDevice(device_token, other_user_ids)` dostane id ostatních účtů a vrátí
    `PushReceiverId`, tedy identifikátor odběru pushů pro tenhle účet.
  - `getPushReceiverId(payload)` z obsahu pushe řekne, kterému účtu patří (dá se volat
    synchronně). Ten účet pak dostane `processPushNotification`.
  - `requestQrCodeAuthentication(other_user_ids)` nepustí k přihlášení QR kódem účet, který
    už v aplikaci je.
- **Proxy, volby (`setOption`) a jazykový balíček jsou nastavení klienta**, ne procesu. Každý
  účet je potřebuje nastavit zvlášť.
- Počet účtů TDLib neomezuje. Limit je rozhodnutí aplikace (viz otevřené otázky).

## Co dnes počítá s jedním účtem

### Vrstva kolem TDLib (`:client:core`)

- **`TelegramClientHolder` drží jednoho klienta na proces.** Bere ho 48 tříd a všechny jsou
  `single` v `coreModule()`, 16 z nich `createdAtStart`. Stav účtu drží v paměti hlavně:
  - `TdlChatRepository`: známé chaty, pozice, počty nepřečtených a `me`,
  - `TdlConversationRepository`: keše odesílatelů, botů a článků,
  - `TdlFileRepository`: stav stahování a nahrávání,
  - `TdlNotificationRepository`, `TdlCallRepository` a `TdlTelegramSession`.
- **Odhlášení = výměna klienta.** `holder.replaced` odebírá třináct tříd jako signál „uklidit
  stav pro další účet".
- **Cesty jsou pevné** (`TelegramPaths`): `tdlib` a `tdlib-files` ve `filesDir`, v Application
  Support na iOS a v datovém adresáři desktopu.
- **Klíč databáze je jeden:**
  - Android: Keystore, alias `plume_tdlib_database`, soubor `tdlib-key`.
  - iOS: Klíčenka `eu.tintera.plume.tdlib` / `database-key`.
  - Desktop: Klíčenka na macOS, jinde soubor v `secrets/`.
- **Keše podle `fileId` mimo repository:** `RevealedSpoilers`, `SelfDestructTimers`, obrysy
  samolepek (`TdlStickerRepository`), `knownPaths` v `RemoteImage` a keše odesílatelů
  v `TdlLiveStoryRepository` a `TdlVideoChatRepository`.
  - **`fileId` je číslo jednoho klienta.** Dva živí klienti ho mají pro různé soubory stejné,
    takže by se obrázky a stavy promíchaly.
  - Dnes to nevadí jen proto, že se část keší maže při odhlášení. Ostatní (obrysy samolepek,
    odesílatelé) by po přihlášení jiného účtu vracely data předchozího už teď.

### Aplikace (`:client:shared`, vstupní body)

- **Notifikace:**
  - `registerDevice` posílá prázdné `otherUserIds` a výsledek (`PushReceiverId`) zahazuje.
  - Push jde rovnou do jediného `processPushNotification`.
  - Id systémové notifikace je `notificationGroupId` z TDLib, které je unikátní jen v rámci
    účtu.
  - Konverzační kanály se jmenují `…_chat_<chatId>` a zvukové kanály nesou id tónu, které
    patří účtu.
  - Odhlášení smaže všechny notifikace, kanály a zástupce.
- **Vstupní body nesou jen id chatu, ne účet:**
  - extra `eu.tintera.plume.extra.CHAT_ID`,
  - akce z notifikací a `ReplyDeliveryWorker`,
  - zástupci Direct Share a připnutí na plochu (`chat_<chatId>`),
  - widget (`plume-widget://chat/<id>`, avatary podle `fileId`),
  - připomenutí odložených chatů, bublina,
  - iOS rozšíření pro sdílení a notifikace.
- **Id chatu NENÍ jednoznačné přes účty.** Soukromý chat má id protějšku, takže chat s Petrem
  má v obou účtech totéž id. Společná skupina má v obou účtech totéž id taky. Vstupní bod bez
  účtu by otevřel stejně pojmenovaný chat ve špatném účtu a nic by nespadlo.
- **`AppSettings` je jeden soubor.** Část voleb už nese účet: záložky, odložené chaty, barvy
  chatů, koncepty komentářů a uložené výsledky modelů. „Id účtu" je tam id Uložených zpráv, tedy
  vlastní user id. Bez účtu jsou volby, které účtu patří:
  - `confirmed_web_app_bots`,
  - `used_one_time_keyboards`,
  - `contact_sync_enabled`,
  - `auto_download_sync`,
  - `last_storage_cleanup`.
- **Zámek aplikace se maže při odhlášení** (`AppLock.reset`), protože „patří účtu, ne
  zařízení".
- **Desktop:** zámek jedné instance (`DesktopInstance`) zůstává. Víc klientů je v jednom procesu
  a druhá instance by narazila na zamčené databáze stejně jako dnes.
- **Wear OS** má vlastní TDLib a jeden účet. Přihlášení hodinek potvrzuje QR kód účet
  v telefonu.

## Architektura: kontejner účtů

**Jeden singleton `Accounts`, který drží účty a jejich životní cyklus.** Zbytek aplikace se
na účet ptá jeho prostřednictvím.

```kotlin
class Accounts {
    val all: StateFlow<List<AccountSession>>   // pořadí v menu účtu
    val active: StateFlow<AccountSession?>     // null = žádný přihlášený
    fun add(): AccountSession                  // nový slot, přihlašovací obrazovka
    fun switchTo(slot: AccountSlot)
    suspend fun remove(slot: AccountSlot)      // po odhlášení: kontejner zavřít, slot uklidit
}

class AccountSession(val slot: AccountSlot, private val koin: KoinApplication) {
    val userId: StateFlow<Long?>               // po přihlášení
    inline fun <reified T : Any> get(): T = koin.koin.get()
}
```

- **`AccountSession` je izolovaný kontejner Koinu** (`koinApplication { modules(accountModule(slot)) }`).
  Uvnitř je dnešní sada `single` z `coreModule()`, jen s cestami, klíčem databáze a holderem
  toho slotu. Koin 4.2.2 má `koinApplication(createEagerInstances = true)` i `close()`
  (ověřeno `javap`), takže `createdAtStart` funguje i v izolovaném kontejneru. Na tom závisí
  odběr updatů: seznam chatů musí vzniknout dřív, než TDLib pošle první dávku.
- **Proč ne Koin scopes:**
  - Kdo chce instanci ze scope, musí scope znát, takže by se přes něj musel ptát každý
    ViewModel a obrazovka.
  - Splete-li se scope, vrátí se instance cizího účtu bez chyby. To je stejná past jako
    ViewModel bez klíče s id chatu.
  - `createdAtStart` ve scope nefunguje.
  - Scopes jsou stavěné na krátké životní cykly (obrazovka, tok). Účet je celý obsahový
    kontext, který žije, dokud je přihlášený.
- **Proč ne ruční skládání:** 48 tříd s konstruktory, které se mění. Modul Koinu je už napsaný
  a izolovaný kontejner ho použije beze změny.
- **`coreModule()` se rozdělí na globální a účetní část:**
  - **Globální:** `APP_SCOPE`, `ConnectionController`, `NetworkMonitor`, `AppForeground`,
    platformní bezpečné úložiště a `TdlibJsonRepair` (je pro celý proces, hook vidí odpovědi
    všech klientů).
  - **Účetní** (`accountModule(slot)`): holder, `TelegramPaths` slotu, klíč databáze slotu,
    session a všechny repository. Sem patří i keše podle `fileId` z předchozí kapitoly. Ty, které
    dnes visí mimo repository (`RevealedSpoilers`, `SelfDestructTimers`, `knownPaths`), se
    přesunou do kontejneru, nebo dostanou slot do klíče.

### Dva druhy spotřebitelů

1. **UI aktivního účtu.** ViewModely dál dostávají `ChatRepository` a ostatní z globálního
   Koinu. Globální definice se jen přesměrují na aktivní účet:
   `factory<ChatRepository> { get<Accounts>().requireActive().get() }`.
   - **Přepnutí účtu zahodí celý strom obrazovek** včetně ViewModelů a zásobníků navigace:
     kořen kompozice za přihlášením má jako klíč slot (`key(slot) { … }`). Takhle se chovají
     i oficiální aplikace a ViewModely ani routy se nemusí měnit. Routa s holým `chatId` je
     v pořádku, protože zásobník patří jednomu účtu.
   - **Uložený stav navigace** (`rememberNavBackStack`) se ukládá pod slot, aby se po zabití
     procesu neotevřel chat v jiném účtu.
   - **Past:** ViewModel nebo singleton, který si repository zachytí do `val` a přežije
     přepnutí, by dál mluvil se starým účtem. ViewModely to nehrozí, protože se zahodí se
     stromem. Globální singletony v `appModule()` (`NotificationActions`, `ChatSwitcher`,
     `ShareTargets`, `MusicQueue`…) je potřeba projít jeden po druhém: buď přejdou do kontejneru
     účtu, nebo začnou pracovat se slotem výslovně.
2. **Věci přes všechny účty** pracují s `accounts.all` a slot nesou výslovně:
   - notifikace a směrování pushů,
   - odznak nepřečtených a tečka u jiného účtu v pilulce,
   - widget, cíle sdílení a menu účtu,
   - hovory: jeden hovor naráz přes všechny účty, příchozí do jiného účtu během hovoru je
     „obsazeno".

## Co musí žít s účtem a co s obrazovkou

Singleton tu neznamená „jeden na aplikaci", ale **„jeden na účet, žije s klientem TDLib"**.
Že se odběry řídí tím, kde v aplikaci uživatel je, platí už dnes pro většinu z nich. Výjimky
mají konkrétní důvod. Repository jsou trojího druhu:

1. **Sklad stavu účtu, který krmí updaty** (`createdAtStart`, dnes 16 tříd): seznam chatů
   a počty nepřečtených, soubory, notifikace, hovory, příběhy, koncepty témat, zařízení.
   - **TDLib posílá stav jednou a dál jen změny.** `UpdateNewChat` přijde jednou. Kdo ho
     nezachytí, protože zrovna nebyl na obrazovce seznamu, má seznam prázdný napořád
     (CLAUDE.md, „Repository seznamu chatů MUSÍ vznikat eagerně"). Tyhle odběry proto nesmí
     záviset na routě. Musí běžet, dokud běží klient.
   - **Část jich pracuje bez obrazovky:** notifikace, příchozí hovor, widget, odznak
     nepřečtených. U účtu na pozadí žádná obrazovka není nikdy.
2. **Brána bez stavu** (statistiky, pozvánky, profil, nálepky…): požadavky na TDLib a studené
   toky přes `holder.updates`. **Odběr vzniká, až když ho sbírá ViewModel**, a končí s ním,
   takže už dnes se řídí routou. Že je instance `single`, nestojí skoro nic. Na délce života
   tu nezáleží, jen na tom, který klient je za ní.
3. **Stav obrazovky** (historie konverzace, výsledky hledání, formuláře): patří do ViewModelu,
   a tím k routě. To už platí.

Druh 1 je tedy skutečně vázaný na účet a kontejner je jeho správný domov. Druh 2 by mohl být
`factory`, ale nic tím nezíská. Za zvážení stojí jen **keše uvnitř bran** (odesílatelé
a články v `TdlConversationRepository`, obrysy samolepek): ty dnes žijí s procesem, i když by
mohly žít s obrazovkou. To je ale optimalizace paměti nezávislá na víc účtech.

## Identita účtu: slot

- **Slot je místní id** (krátký řetězec, například `a1b2c3`). Vzniká při „Přidat účet", tedy
  dřív, než je známé user id, a podle něj se jmenují adresáře, klíč databáze, kanály
  a zástupci. User id se k slotu doplní po přihlášení.
- **Dnešní instalace je slot `0` na dnešních cestách.** `filesDir/tdlib`, alias
  `plume_tdlib_database`, zástupci `chat_<chatId>` i kanály `…_chat_<chatId>` zůstanou, jak
  jsou. Nové sloty dostanou `accounts/<slot>/tdlib` a alias `plume_tdlib_database_<slot>`.
  Odpadne tím migrace gigabajtů dat i přejmenovávání zástupců a kanálů, které uživatel mohl
  upravit (zvuk, důležitost).
- **Stejný účet dvakrát:**
  - U QR kódu to zastaví TDLib (`other_user_ids`).
  - U přihlášení číslem se to pozná až v `Ready`: user id se shoduje s jiným slotem. Nový slot
    se pak odhlásí, aplikace přepne na existující účet a řekne proč.
- **Odhlášení jednoho účtu:**
  1. TDLib smaže data.
  2. Kontejner se zavře (`close()`).
  3. Smaže se adresář slotu, klíč a jeho kanály, zástupci a notifikace.
  4. Aktivní je další účet.
  
  Přihlašovací obrazovka přijde až po odhlášení posledního účtu.

## Notifikace

- **Směrování pushe:**
  1. `getPushReceiverId(payload)` (synchronně, na kterémkoli klientu).
  2. Najde se slot, jehož uložený `PushReceiverId` z `registerDevice` se shoduje.
  3. Tomu slotu jde `processPushNotification`.
  
  `PushReceiverId` se ukládá ke slotu a každý účet se registruje s `other_user_ids` ostatních.
  Push, který nepatří žádnému slotu (účet odhlášený jinde), se zahodí.
- **Id notifikace = tag + id.** `NotificationManager.notify(tag, id, …)` umí tag, takže tag je
  slot a id zůstane `notificationGroupId`. Kolize se tím vyřeší bez hashování a slot `0` může mít
  tag `null`, tedy přesně jako dnes.
- **Kanály:**
  - Kanály podle druhu chatu (soukromé, skupiny, kanály, zmínky) zůstanou společné pro všechny
    účty. Kanály po účtech by se v systémovém nastavení množily s každým účtem.
  - Konverzační kanály a zvukové kanály dostanou slot do jména (slot `0` beze změny).
- **Text notifikace jmenuje účet**, když je jich víc (`setSubText` s jménem účtu). Jinak není
  poznat, komu zpráva přišla.
- **Akce z notifikace** (odpověď, přečteno, ztlumit, zavřít, hovor) nesou extra s účtem
  (`eu.tintera.plume.extra.ACCOUNT`, bez něj slot `0`). `NotificationActions` dostane slot jako
  parametr.
- **Unikátní jména úloh WorkManageru** dostanou slot (`notification-reply-<slot>-<chat>-<msg>`,
  `notification-delivery-<slot>`).
- **iOS:**
  - APNs payload TDLib nešifruje a rozšíření pro notifikace z něj dnes čte jen chat.
  - **Jestli payload nese příjemce, ověřené NENÍ.** Bez něj by klepnutí na notifikaci nevědělo,
    do kterého účtu jít, a `threadIdentifier` by se mezi účty slil.
  - Ověřit se to dá až s certifikátem APNs (viz poznatky k notifikacím).

## Vstupní body

**Pravidlo: co nese id chatu, nese i slot.** Chybějící slot znamená slot `0`, takže staré
intenty, zástupci a připomenutí fungují dál.

- **Intent extra** `eu.tintera.plume.extra.ACCOUNT` vedle `CHAT_ID`. `ChatOpenRequests`
  a `ShareRequests` nesou slot. Je-li to jiný než aktivní účet, shell nejdřív přepne a pak
  otevře chat.
- **Zástupci Direct Share a připnutí** `chat_<slot>_<chatId>`, slot `0` beze změny. Navrhuje se
  jen aktivní účet. Připnutí na plochu je vázané na účet a po jeho odhlášení se zakáže
  (`disableShortcuts`), ne jen vynechá.
- **Widget** v první fázi ukazuje aktivní účet. Volba účtu u widgetu (konfigurace) je fáze 2.
  Avatary dostanou slot do jména souboru, protože `fileId` je číslo klienta.
- **Odkazy `tg://` a `t.me`** otevře aktivní účet. Volba účtu u odkazu (pozvánka, přihlášení na
  webu) může přijít později, pokud bude chybět.
- **Připomenutí odloženého chatu a bublina** nesou slot jako ostatní.

## UI

- **Menu účtu** (pilulka s fotkou): nahoře přihlášené účty s tečkou nebo počtem nepřečtených,
  ťuknutí přepne. Pod nimi „Přidat účet". Zkratka podržením fotky v pilulce (přepnout na další
  účet) je možnost, ne podmínka.
- **Přidat účet** otevře přihlašovací obrazovku s tlačítkem zpět (dnes ho nemá, protože se z ní
  nebylo kam vrátit).
- **Nastavení:** karta účtu patří aktivnímu účtu. Odhlášení odhlásí jen jeho a věta v dialogu
  řekne, na který účet se přepne.
- **Přepnutí** je prolnutí celé obrazovky (`AppMotion`), ne posun. Je to jiný obsah, ne
  navigace.

## Nastavení (`AppSettings`)

- **Globální zůstane všechno, co popisuje zařízení nebo vzhled:** motiv, písmo, jazyk, AI
  v zařízení, tiché hodiny, stahování médií, gesta.
- **Volby, které patří účtu, dostanou slot nebo user id do klíče:**
  - `confirmed_web_app_bots` (souhlas s botem dal účet),
  - `used_one_time_keyboards`,
  - `contact_sync_enabled` (nahrává kontakty do účtu),
  - `auto_download_sync`,
  - `last_storage_cleanup` (úklid je podle databáze).
- **Záznamy, které už účet nesou** (záložky, odložené chaty, barvy, koncepty komentářů,
  výsledky modelů, úložiště mini aplikací), zůstávají, jak jsou. Klíčem je tam user id, které
  slot po přihlášení zná.
- **Chování seznamu a konverzace** (`separate_unread_chats`, rychlá reakce, varování před
  podvodem…) zůstane v první fázi globální. Rozdělit ho podle účtu je rozhodnutí o produktu,
  ne podmínka.
- **Zámek aplikace se stane zámkem zařízení.** Maže se až s odhlášením posledního účtu. Únikovou
  cestou při zapomenutém PINu zůstává odhlášení, jen všech účtů.

## Výkon a paměť

- **Každý účet je živý klient TDLib:** vlastní spojení, vlastní databáze a vlastní vlákno pro
  síť. Paměť i baterie rostou s počtem účtů. Kolik přesně, je potřeba změřit (heap dump
  a `dumpsys meminfo` s jedním a třemi účty, viz `docs/poznatky/vykon.md`).
- **Online je jen aktivní účet** (volba `online`). Typ sítě (`setNetworkType`) dostávají všechny.
  Při přihlášení nového klienta se pošle hned, ne až s další změnou sítě: tahle mezera je
  i dnes po `recreate()`.
- **Účet na pozadí nepotřebuje repository UI.** Stačí mu session, notifikace, počty
  nepřečtených a hovory. Šetřit by šlo tak, že se zbytek vyrobí líně až při přepnutí. Napřed
  ale měřit, ne předpokládat.

## Fáze

0. **Kontejner s jedním účtem, bez změny chování.** `Accounts`, rozdělení `coreModule()`,
   globální definice přes aktivní účet, keše podle `fileId` do kontejneru, slot `0` na dnešních
   cestách. Aplikace se chová stejně. Ověří se tím, že nikde nezůstal zachycený singleton.
1. **Druhý účet na Androidu a desktopu:**
   - přidání, přepnutí a odhlášení jednoho účtu,
   - směrování pushů, tagy notifikací, slot ve vstupních bodech a v akcích z notifikací,
   - widget a cíle sdílení pro aktivní účet,
   - limit počtu účtů.
2. **iOS** (rozšíření pro notifikace a sdílení, App Group po slotech, odznak jako součet),
   volba účtu u widgetu a nastavení chování po účtech, pokud bude chtěné.
3. **Hodinky:** výběr účtu při přihlášení hodinek. Hodinky samy zůstanou na jednom účtu.

## Otevřené otázky

- **Limit počtu účtů.** Navrhuju tři jako výchozí, podle změřené paměti.
- **Nese APNs payload příjemce?** Bez toho iOS notifikaci k účtu nepřiřadí (fáze 2).
- **Odznak nepřečtených na ikoně aplikace:** součet přes účty, nebo jen aktivní? Navrhuju
  součet.
- **Nastavení chování po účtech** (seznam chatů, rychlá reakce): stačí globálně?
