# Návrh: víc účtů naráz

> **Stav:** fáze 0 rozpracovaná (viz „Postup" na konci). Vychází z kódu po #1166. Na roadmapě to
> stálo jako „Víc účtů (L), rozhodnutí, jestli vůbec, zatím nepadlo". Rozhodnutí padlo: chceme to.

Víc přihlášených účtů Telegramu v jedné aplikaci. **Na rozdíl od oficiálních klientů není
žádný účet „aktivní":** seznam chatů umí ukázat chaty všech účtů najednou a filtr nad ním
zúží pohled na jeden účet, na druhý, nebo nechá všechny. Notifikace chodí ze všech účtů
pořád.

**Jak to mají jiní** (stav 11/2026):
- **Telegram pro Android a iOS** má víc účtů od roku 2017, přepíná je v bočním menu
  a notifikace pouští ze všech účtů (výchozí volba).
- **Telegram Desktop** má tři účty, s Premium čtyři. Od verze 4.6.3 umí otevřít další účet
  v samostatném okně.
- **Společný seznam chatů** z víc účtů žádný oficiální klient nemá. Nabízejí ho jen placené
  nástroje pro firemní podporu nad API. Mimo Telegram je to běžné u e-mailu (společná
  schránka).

**Zvážená a zamítnutá varianta: jeden aktivní účet a přepínání**, jak to dělají oficiální
klienti. Byla by levnější: ViewModely a routy by se neměnily, přepnutí by zahodilo celé UI.
Zamítnutá je, protože nedovolí vidět všechno najednou. Navíc stojí na skrytém přesměrování
„na aktivní účet", ve kterém se po chybě vrátí cizí účet a nic nespadne.

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
  - Vyřešeno ve fázi 0 (krok 3 v „Postup").

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
    val all: StateFlow<List<AccountSession>>   // přihlášené účty v pořadí uživatele
    val default: StateFlow<AccountSlot?>       // pro akce bez zdroje (nová zpráva, odkaz); null = nikdo
    operator fun get(slot: AccountSlot): AccountSession
    fun add(): AccountSession                  // nový slot, přihlašovací obrazovka
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
  - `createdAtStart` ve scope nefunguje, a na něm stojí sklad stavu účtu (viz „Co musí žít
    s účtem").
  - Scope je uvnitř globálního Koinu, takže `get()` bez scope tiše vrátí globální instanci,
    nebo spadne až za běhu. Izolovaný kontejner globální definice účtu vůbec nemá.
  - Scopes jsou stavěné na krátké životní cykly (obrazovka, tok). Účet je celý obsahový
    kontext, který žije, dokud je přihlášený.
- **Proč ne ruční skládání:** 48 tříd s konstruktory, které se mění. Modul Koinu je už napsaný
  a izolovaný kontejner ho použije beze změny.
- **`coreModule()` se rozdělí na globální a účetní část:**
  - **Globální:** `APP_SCOPE`, `ConnectionController`, `NetworkMonitor`, `AppForeground`,
    platformní bezpečné úložiště a `TdlibJsonRepair` (je pro celý proces, hook vidí odpovědi
    všech klientů).
  - **Účetní** (`accountModule(slot)`): holder, `TelegramPaths` slotu, klíč databáze slotu,
    session a všechny repository. Sem patří i keše podle `fileId` z předchozí kapitoly
    (`RevealedSpoilers`, `SelfDestructTimers`, `KnownImagePaths`).

### Účet nese routa

**V globálním Koinu žádné repository účtu nejsou.** Kdo s účtem pracuje, má jeho slot
výslovně: routa, položka seznamu, extra v intentu. Globální přesměrování „na aktivní účet"
neexistuje, takže se nedá splést potichu: bez slotu se repository vůbec nedostane.

- **Každá routa s obsahem účtu má `slot`** (`ConversationRoute(slot, chatId)`,
  `ChatProfileRoute(slot, chatId)`, `AccountSettingsRoute(slot)`…). Dnes je v `Routes.kt`
  48 rout s parametry, a ty s id chatu, uživatele, zprávy nebo souboru dostanou slot všechny.
  Serializace (`NavSavedStateConfiguration`) ho uloží, takže chat po zabití procesu otevře
  správný účet.
- **ViewModel dostane repository z účtu své routy.** Definice v Koinu zůstanou globální
  (ViewModel je věc obrazovky), jen repository berou ze slotu:
  `viewModel { (route: ConversationRoute) -> ConversationViewModel(accounts[route.slot].get(), …) }`.
  Na 91 definic `viewModel` v `AppModule` se vyplatí malý pomocník (`accountViewModel { account, params -> … }`),
  ať se slot nevytahuje ručně. **Klíč ViewModelu obsahuje slot** (`conversation-$slot-$chatId`),
  jinak by dva účty se stejným id chatu sdílely ViewModel (viz CLAUDE.md o klíči ViewModelu).
- **`LocalAccount` pro composables.** Dekorátor položky `NavDisplay` ho nastaví ze slotu
  routy. Composables, které si dnes berou repository samy (`RemoteImage` přes
  `FileRepository`, avatary, vlastní emoji; `koinInject` je dnes ve sdíleném UI na 104
  místech), ho čtou z `LocalAccount`. Ve společném seznamu ho
  nastavuje každý řádek zvlášť, protože řádky jsou z různých účtů. **Bez `LocalAccount` je to
  chyba** (`error()` v `current`), ne tichý návrat k výchozímu účtu.
- **Globální singletony v `appModule()` se projdou jeden po druhém.** Ty, které drží stav účtu
  (`ChatSwitcher`, `PendingChatDeletes`, `LoadedHistory`, `MessageContexts`…), přejdou do
  kontejneru účtu. Ty, které jsou opravdu globální (`MusicQueue`: hraje jedna skladba, ať je
  z kteréhokoli účtu), nesou slot u každé položky.
- **Věci přes všechny účty** pracují s `accounts.all`:
  - notifikace a směrování pushů,
  - společný seznam chatů, odznak nepřečtených, widget a cíle sdílení,
  - hovory: jeden hovor naráz přes všechny účty, příchozí do jiného účtu během hovoru je
    „obsazeno".

**Cena:** projít všechny ViewModely a routy s id. Je to mechanická práce a překladač ji
vede. Když dostane routa povinný parametr `slot`, chyba překladu ukáže každé místo, které ji
staví (stejný postup jako přejmenování `ChatMessage.text` u `UiText`).

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
     nepřečtených. Účet mimo filtr seznamu chatů nemá na obrazovce nic, a notifikace z něj chodit musí.
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
    se pak odhlásí a aplikace řekne, že ten účet už přihlášený je.
- **Odhlášení jednoho účtu:**
  1. TDLib smaže data.
  2. Kontejner se zavře (`close()`).
  3. Smaže se adresář slotu, klíč a jeho kanály, zástupci a notifikace.
  4. Ze zásobníků navigace zmizí routy s jeho slotem. Byl-li výchozí, výchozím se stane
     první zbylý.
  
  Přihlašovací obrazovka přijde až po odhlášení posledního účtu.

## Společný seznam chatů

Seznam chatů je jediná obrazovka, která míchá účty. Všechno, co se z ní otevře, už patří
jednomu účtu podle řádku.

- **Filtr účtů** nad seznamem: čipy s fotkou účtu (jeden, druhý…) a „Vše". Ukazuje se jen
  s víc než jedním účtem. Volba se pamatuje (`AppSettings`, množina slotů).
- **Řádek nese účet:** malá fotka účtu u avataru chatu (jako odznak), jen v režimu víc účtů.
  Barva účtu by se pletla s barvami chatů (`ChatColors`), proto fotka.
- **Stejný chat ve dvou účtech se ukáže dvakrát.** Společná skupina, nebo chat s Petrem
  z obou účtů. Sloučit je nejde: mají jiný stav přečtení, jinou historii a u starých skupin
  i jiná id zpráv. Odznak účtu je rozliší.
- **Řazení podle data poslední zprávy.** `ChatPosition.order` z TDLib je číslo uvnitř jednoho
  účtu a mezi účty se porovnat nedá.
- **Připnuté chaty všech účtů jsou nahoře i v režimu Vše** (rozhodnuto): napřed připnuté
  podle pořadí účtů a uvnitř účtu podle TDLib, pak ostatní podle data. Co si uživatel připne,
  je pro něj důležité a nesmí to zmizet jen proto, že se dívá na všechny účty.
- **Složky patří účtu** (jsou na serveru). Pilulky složek se ukazují, jen když filtr vybírá
  jeden účet. V režimu Vše je hlavní seznam a archiv. Spojovat složky podle názvu by bylo
  křehké.
- **Archiv** v režimu Vše slučuje archivy všech účtů stejným pravidlem.
- **„Čeká na odpověď" a odložené chaty** fungují nad sloučeným seznamem beze změny: pracují
  s řádky a každý řádek ví, čí je. **Blok nepřečtených** (`separate_unread_chats`) se nebude
  řešit: byl to nadstandard proti Telegramu a z aplikace zmizel ještě před víc účty
  (`docs/poznatky/seznam-chatu.md`, „Blok nepřečtených zmizel").
- **Výběr víc chatů** (archivovat, přečíst, ztlumit) smí míchat účty. Akce se rozdělí podle
  slotu řádků.
- **Hledání** v režimu Vše hledá ve všech účtech a výsledky označí účtem. Globální hledání
  (veřejné chaty) a hledání zpráv přes server se pustí za každý účet zvlášť a výsledky se
  slijí. Veřejný chat nalezený oběma účty se ukáže jednou, s výchozím účtem.
- **Co nemá zdroj v řádku**, bere výchozí účet a dovolí ho změnit:
  - nová zpráva a kontakty (výběr účtu nahoře, předvolený výchozí),
  - Uložené zprávy, Moje příběhy, hovory a stahování v menu účtu (po účtech),
  - hromádka příběhů v pilulce hledání (příběhy všech účtů ve filtru, odesílatel zná slot).
- **Rychlý přepínač chatů** (`ChatSwitcher`) nese slot u každé položky.

## Notifikace

- **Push nese, komu patří.** Dokumentace pushů Telegramu
  ([core.telegram.org/api/push-updates](https://core.telegram.org/api/push-updates)) má pole
  `user_id`: „ID účtu, kterému se má notifikace doručit, pro klienty s víc účty aktivními
  naráz". Šifrovaný push (`p`) nese na začátku id klíče, kterým je zašifrovaný, a klíč patří
  jednomu účtu. Totéž čte TDLib v `NotificationManager::get_push_receiver_id` (zdroj z větve
  master): u šifrovaného pushe id klíče z prvních 12 znaků `p`, jinak `user_id`.
- **Směrování pushe:**
  1. `getPushReceiverId(payload)` (synchronně, na kterémkoli klientu).
  2. Najde se slot, jehož uložený `PushReceiverId` z `registerDevice` se shoduje. Na Androidu
     dnes Plume push šifruje (`encrypt` u FCM), takže se porovnává id klíče. U nešifrovaného
     pushe vrátí `getPushReceiverId` přímo user id a porovná se s user id slotu.
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
  - Běžný APNs push TDLib šifrovat neumí (`deviceTokenApplePush` nemá `encrypt`, na rozdíl od
    FCM a VoIP). Rozšíření pro notifikace ho čte samo bez TDLib a dnes z něj bere jen chat
    (`custom`).
  - Účet určí `user_id` z pushe: `threadIdentifier` bude `<user_id>:<chatId>` a klepnutí
    otevře chat ve slotu s tím user id.
  - **Kde přesně `user_id` v APNs payloadu leží** (vedle `aps` a `custom`, nebo uvnitř), se
    ověří až na skutečném pushi: rozšíření v ladicím buildu ukládá poslední push do App Group
    (`push/last.json`), jen zatím čeká na certifikát APNs (viz poznatky k notifikacím).

## Vstupní body

**Pravidlo: co nese id chatu, nese i slot.** Chybějící slot znamená slot `0`, takže staré
intenty, zástupci a připomenutí fungují dál.

- **Intent extra** `eu.tintera.plume.extra.ACCOUNT` vedle `CHAT_ID`. `ChatOpenRequests`
  a `ShareRequests` nesou slot a shell otevře routu s ním. Nic se nepřepíná.
- **Zástupci Direct Share a připnutí** `chat_<slot>_<chatId>`, slot `0` beze změny. Navrhují
  se chaty všech účtů a název zástupce nese jméno účtu, když je jich víc. Připnutí na plochu
  je vázané na účet a po jeho odhlášení se zakáže (`disableShortcuts`), ne jen vynechá.
- **Sdílení do aplikace** („Komu: …") ukáže společný seznam s odznakem účtu. Odešle se z účtu
  vybraného chatu.
- **Widget** ukazuje chaty podle filtru ze seznamu chatů. Volba účtů přímo u widgetu
  (konfigurace) je fáze 2. Avatary dostanou slot do jména souboru, protože `fileId` je číslo
  klienta.
- **Odkazy `tg://` a `t.me`** otevře výchozí účet. Pozvánka do skupiny a přihlášení na webu
  se s víc účty zeptají, kterým účtem (výchozí předvolený), protože tam na účtu záleží.
- **Připomenutí odloženého chatu a bublina** nesou slot jako ostatní.

## UI

- **Menu účtu** (pilulka s fotkou): nahoře přihlášené účty s počtem nepřečtených. Ťuknutí na
  účet nastaví filtr seznamu na něj (stejně jako čip nad seznamem). Pod nimi „Přidat účet"
  a položky, které patří účtu (Uložené zprávy, Archiv, Hovory…), za výchozí účet nebo za účet
  ve filtru, když je jeden.
- **Fotka v pilulce** je fotka výchozího účtu, v režimu Vše s malou druhou fotkou za ní.
- **Přidat účet** otevře přihlašovací obrazovku s tlačítkem zpět (dnes ho nemá, protože se z ní
  nebylo kam vrátit).
- **Hlavička konverzace** ukáže, kterým účtem píšeš, když je účtů víc (podtitul nebo fotka
  účtu u vstupního pole). Bez toho se snadno odpoví ze špatného účtu ve skupině, kde jsou
  oba.
- **Nastavení:**
  - Nahoře karty přihlášených účtů. Každá otevře nastavení toho účtu: profil, soukromí,
    notifikace (rozsahy a výjimky jsou na serveru), zařízení, složky, blokovaní, úložiště
    databáze a odhlášení.
  - Pod nimi nastavení aplikace (vzhled, jazyk, AI v zařízení, zámek, tiché hodiny), společné
    pro všechny účty.
  - „Výchozí účet" jako volba v nastavení aplikace.

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
- **Chování seznamu a konverzace zůstane společné pro všechny účty** (rozhodnuto): gesta
  tahem, dvojité ťuknutí, varování před podvodem, návrhy odpovědí, triáž. Je to chování
  aplikace, ne účtu. Co účtu patří, drží server po účtech už teď: **kterou** reakci přidá
  dvojité ťuknutí, je nastavení účtu (`setDefaultReactionType`, `QuickReaction`); Plume si
  pamatuje jen to, jestli dvojité ťuknutí reaguje vůbec. Ztlumení a výjimky notifikací jsou
  u chatu, tedy taky po účtech.
- **Zámek aplikace se stane zámkem zařízení.** Maže se až s odhlášením posledního účtu. Únikovou
  cestou při zapomenutém PINu zůstává odhlášení, jen všech účtů.

## Výkon a paměť

- **Každý účet je živý klient TDLib:** vlastní spojení, vlastní databáze a vlastní vlákno pro
  síť. Paměť i baterie rostou s počtem účtů. Kolik přesně, je potřeba změřit (heap dump
  a `dumpsys meminfo` s jedním a třemi účty, viz `docs/poznatky/vykon.md`).
- **Online (volba `online`) mají v popředí všechny účty ve filtru.** Uživatel jejich chaty
  vidí, takže je online i pro protějšky. Účet mimo filtr online není. Typ sítě
  (`setNetworkType`) dostávají všechny. Při přihlášení nového klienta se pošle hned, ne až
  s další změnou sítě: tahle mezera je i dnes po `recreate()`.
- **Všechny účty drží sklad stavu** (seznam chatů, soubory, notifikace, hovory), protože
  společný seznam a notifikace ho potřebují pořád. Brány bez stavu nestojí nic, dokud je
  neodebírá obrazovka. Úspora líným vytvářením tedy skoro není; napřed změřit, kolik stojí
  jeden klient TDLib s velkým účtem.

## Fáze

0. **Kontejner a slot v routách, s jedním účtem, bez změny chování:**
   - `Accounts` a rozdělení `coreModule()` na globální a účetní část,
   - slot v routách, ViewModelech, `LocalAccount` a ve vstupních bodech,
   - keše podle `fileId` do kontejneru,
   - slot `0` na dnešních cestách.
   
   Aplikace se chová stejně. Je to největší kus práce a ověří, že žádné repository nejde
   dostat bez slotu.
1. **Druhý účet na Androidu a desktopu:**
   - přidání a odhlášení jednoho účtu, výchozí účet,
   - společný seznam s filtrem účtů a odznakem účtu u řádku,
   - směrování pushů, tagy notifikací, slot v akcích z notifikací,
   - nastavení po účtech, účet v hlavičce konverzace,
   - widget a cíle sdílení přes všechny účty,
   - limit tří účtů (rozhodnuto; zvednout jde podle změřené paměti).
2. **iOS** (rozšíření pro notifikace a sdílení, App Group po slotech, odznak jako součet),
   volba účtů u widgetu, sloučené hledání zpráv přes server.
3. **Hodinky:** výběr účtu při přihlášení hodinek. Hodinky samy zůstanou na jednom účtu.

## Rozhodnuto

- **Nejvýš tři účty.** Zvednout jde, až bude změřené, kolik stojí další klient TDLib.
- **Připnuté chaty všech účtů jsou nahoře i v režimu Vše.**
- **Odznak nepřečtených na ikoně aplikace je součet všech účtů**, nezávisle na filtru.
- **Chování seznamu a konverzace je společné** pro všechny účty. Blok nepřečtených
  (`separate_unread_chats`) z aplikace zmizel.

## Zbývá ověřit

- Kde přesně leží `user_id` v pushi na iOS (první skutečný push s certifikátem APNs).
- Kolik paměti a baterie stojí další klient TDLib (fáze 1, s druhým účtem).
- Že wrapper 17.0.0 dělí odpovědi podle klienta stejně jako 15.0.0 (fáze 0).

## Postup

Fáze 0 jde po krocích, každý v aplikaci beze změny chování:

1. **Slot účtu a hranice modulů** — hotovo:
   - `AccountSlot` (`core/accounts`): primární slot `0` dává dnešní jména (`tdlib`,
     `tdlib-files`, `plume_tdlib_database`, `tdlib-key`, `database-key`), další sloty
     `accounts/<slot>/…` a příponu. Test `AccountSlotTest` hlídá hlavně to první: jiné jméno
     u primárního slotu by po aktualizaci ztratilo databázi i klíč a odhlásilo uživatele.
   - `TelegramPaths` bere slot na všech platformách.
   - `coreModule()` je `coreGlobalModule()` (scope aplikace, síť, popředí) a
     `accountModule(slot)` (klient, cesty a klíč slotu, session, všechny repository,
     `ConnectionController`, `RevealedSpoilers`, `SelfDestructTimers`). Obě části jsou zatím
     ve stejném, globálním Koinu.
2. **Kontejner `Accounts`** — hotovo:
   - `Accounts` a `AccountSession` (`core/accounts`): primární účet má izolovaný kontejner
     (`koinApplication(createEagerInstances = true)` nad `accountModule`). `Accounts` je
     v globálním Koinu eager, protože v kontejneru vznikají eager repository.
   - **Most dovnitř** (`globalBridgeModule`): kontejner účtu globální definice nevidí, takže mu
     scope aplikace, síť, popředí a mediální vrstvu hovorů podává tenhle modul. Volitelné
     definice hovorů se přebírají jen, když je aplikace má, jinak by `getOrNull`
     v `TdlCallRepository` přestal poznat, že hovory nejsou. Na Androidu si část účtu bere
     `Context` z globálního Koinu, `androidContext()` izolovaný kontejner nemá.
   - **Most ven** (`accountBridgeModule`, dočasný): všech 52 definic účtu je v globálním Koinu
     jako `single { primary().get() }`, takže ViewModely, `koinInject` a služby fungují beze
     změny. `AccountBridgeTest` porovná klíče mapování obou modulů (`Module.mappings`,
     `@KoinInternalApi`); vynechaný řádek mostu test shodí (ověřeno sondou).
3. **Keše podle `fileId`** — hotovo:
   - `knownPaths` z `RemoteImage` (globální mapa v UI) je `KnownImagePaths` v kontejneru účtu.
     `RemoteImage` si ho bere přes `koinInject` (zatím mostem), úklid po odhlášení a po
     vyčištění úložiště volá `forget()` na instanci účtu.
   - Obrysy samolepek a odesílatelé ve vysílání a ve videochatu jsou v `ClientCache`: pamatuje
     si klienta, od kterého hodnoty má, a s jiným (po odhlášení a přihlášení jiného účtu) začne
     od prázdna. Dřív tu vracely data předchozího účtu. Keš odesílatelů v konverzaci to už
     uměla po svém.
   - `RevealedSpoilers` a `SelfDestructTimers` jsou v kontejneru od kroku 2, ostatní keše podle
     `fileId` jsou v repository, takže taky.
   - Rozparsované animace samolepek (`AnimatedSticker`) zůstávají globální: klíčem je cesta
     a každý slot má vlastní adresář souborů, takže se účty nepotkají.
4. **Slot v routách, ViewModelech, `LocalAccount` a vstupních bodech**, most pryč.
