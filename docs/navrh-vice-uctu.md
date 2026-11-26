# Návrh: víc účtů naráz

> **Stav:** fáze 0 hotová, z fáze 1 přidání, odebrání a přepnutí účtu, společný seznam chatů
> účet v konverzaci a v notifikacích, jeden hovor přes všechny účty (viz „Postup" na konci). Vychází z kódu po #1166. Na roadmapě to
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
- **ViewModel dostane repository z účtu své routy, protože v jeho kontejneru vzniká.** Definice
  `viewModel` jsou v části účtu (`appAccountModule`) a dál si berou repository přes `get()`.
  Původně měly zůstat globální s pomocníkem `accountViewModel { account, params -> … }`; to by
  znamenalo přepsat 91 definic a nic by to nepřineslo.
  **Klíč ViewModelu slot obsahovat nemusí**: úložiště ViewModelů má každá položka zásobníku
  vlastní a v klíči položky (`contentKey`) slot je, protože je součástí routy. Slot do klíče
  přijde až tam, kde jedna položka skládá obsah víc účtů (společný seznam).
- **`AccountScope` pro composables.** Shell skládá obsah každé `AccountRoute` v kontejneru jejího
  účtu (`inRouteAccount`, přes `KoinIsolatedContext`), takže `koinInject` a `koinViewModel`
  (přes 230 míst) zůstaly beze změny a míří do správného kontejneru. `LocalAccount` nese účet
  pro ty, kdo se ptají na slot nebo staví routu. Ve společném seznamu ho nastaví každý řádek
  zvlášť, protože řádky jsou z různých účtů. **Bez `AccountScope` je to chyba:** `LocalAccount`
  vyhodí výjimku a `koinInject` repository v globálním Koinu nenajde, tichý návrat k výchozímu
  účtu neexistuje.
- **Kontejner účtu vidí do globálního Koinu, naopak to nejde** (`openAccount`, rozšíření
  `ResolutionExtension` z Koinu). Účet tak dostane nastavení, síť nebo scope aplikace bez seznamu
  předávaných typů. Globální definice proto nesmí při vzniku sáhnout na repository ani na
  `Accounts`: druhé otevření kontejneru by znamenalo druhého klienta TDLib nad tímtéž adresářem,
  a tak ho `coreModule` odmítne výjimkou.
- **Globální singletony v `appModule()` se projdou jeden po druhém.** Ty, které drží stav účtu
  (`ChatSwitcher`, `PendingChatDeletes`, `LoadedHistory`, `MessageContexts`…), přejdou do
  kontejneru účtu. Ty, které jsou opravdu globální (`MusicQueue`: hraje jedna skladba, ať je
  z kteréhokoli účtu), nesou slot u každé položky.
- **Věci přes všechny účty** pracují s `accounts.all`:
  - notifikace a směrování pushů,
  - společný seznam chatů, odznak nepřečtených, widget a cíle sdílení,
  - hovory: jeden hovor naráz přes všechny účty, příchozí do jiného účtu během hovoru je
    „obsazeno" (vyšlo „odmítnuto", viz krok 7 v „Postup").

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

- **Filtr účtů** je v menu účtu: „Všechny účty" a jednotlivé účty (původně čipy nad seznamem,
  viz krok 4 v „Postup"). Ukazuje se jen s víc než jedním účtem. Volba se pamatuje
  (`AppSettings`).
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
  síť. Paměť roste s počtem účtů — změřeno v kroku 15 (asi 22 MB nativní paměti a desítky
  milisekund startu na účet). Baterie v pozadí skoro ne: zmražený proces nestojí nic a push
  probudí všechny klienty, ale přidají k němu desítky procent, ne násobek (krok 23).
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

- **Nejvýš tři účty.** Zvednout jde podle změřené paměti (krok 15); baterie v pozadí limit nedrží (krok 23).
- **Připnuté chaty všech účtů jsou nahoře i v režimu Vše.**
- **Odznak nepřečtených na ikoně aplikace je součet všech účtů**, nezávisle na filtru.
- **Chování seznamu a konverzace je společné** pro všechny účty. Blok nepřečtených
  (`separate_unread_chats`) z aplikace zmizel.

## Zbývá ověřit

- Kde přesně leží `user_id` v pushi na iOS (první skutečný push s certifikátem APNs).
- Kolik baterie stojí další klient TDLib v popředí a na mobilních datech (v pozadí na Wi-Fi
  změřeno v kroku 23).
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
4. **Repository jen v kontejneru účtu a slot v routách** — hotovo:
   - Oba mosty z kroku 2 jsou pryč. V globálním Koinu (`appModule`) zbylo jen to, co je v procesu
     opravdu jednou: nastavení, zámek, model v zařízení, přehrávač hlasovek, média hovorů
     a mosty od platformy (`ChatOpenRequests`, `LinkOpenRequests`, `CallRequests`,
     `ShareRequests`). Všechno ostatní, i ViewModely, je v `appAccountModule`.
   - Routy s id (45 tříd) jsou `AccountRoute` s povinným `slot`, takže překladač našel všechna
     místa, která je staví. `RouteSlotTest` projde zásobník s jiným než primárním slotem: s jedním
     účtem by se špatně předaný slot jinak nikde neukázal.
   - `SAVED_ROUTES_VERSION`: zásobník uložený před změnou by se přečetl jen s výjimkou, takže se
     po aktualizaci začíná od kořene.
5. **Slot ve vstupních bodech** — hotovo, přesně podle kapitoly „Vstupní body":
   - Extra `eu.tintera.plume.extra.ACCOUNT` nesou ťuknutí na notifikaci, její akce (odpověď,
     přečteno, ztlumit, odhrnout, odmítnout hovor), bublina, připomenutí odloženého chatu, widget
     a zástupci. **Primární účet ho nenese**, takže staré intenty, zástupci a naplánované úlohy
     znamenají totéž co dřív. Totéž platí pro id zkratek (`chat_<chatId>`, jinak
     `chat_<slot>_<chatId>`), jména úloh a data intentů.
   - `ChatOpenRequests` a `ShareRequests` nesou slot, konverzace si sdílený obsah vyzvedne jen
     ve svém účtu. Soukromý chat má v obou účtech totéž id, proto i request code PendingIntentu
     a miniatura widgetu slot obsahují.
   - **Účet, který už není, nic neotevře a nic neshodí** (`Accounts.find`, `AccountSlot.parse`):
     notifikace a zástupce můžou přežít odhlášení a extra do exportované aktivity může poslat
     kdokoli. Sdílení přes takového zástupce se zahodí.
   - Komu definice patří, řekne `get<AccountSlot>()` v kontejneru účtu.

Fáze 1 jde taky po krocích. Hotové kroky 1–3, jeden PR:

1. **Start po účtech a služby přes všechny účty** — hotovo:
   - Klienta TDLib startuje `Accounts` s kontejnerem účtu; co má běžet s účtem od startu procesu
     (presenter notifikací, `ConnectionController`, úklid úložiště, automatické stahování,
     registrace pushů), startuje aplikace přes `Accounts.forEachOpened`, i pro účet přidaný
     později.
   - **Každý účet má vlastní scope** podaný v kontejneru jako `APP_SCOPE`
     (`accountScopeModule`), takže se s odebraným účtem zastaví i jeho odběry. Globální scope
     by je nechal běžet nad zavřeným klientem.
   - Služby v popředí (hovor, hudba, poloha, videochat) berou účet, který je spustil
     (`ServiceOwner`): ovládání v kontejneru účtu nastaví slot těsně před `startService`, protože
     služba si závislosti bere líně už v `onCreate`, dřív než dorazí intent.
   - Push: `registerDevice` za každý účet s `other_user_ids` ostatních, `PushReceiverId` se ukládá
     ke slotu (do seznamu účtů, push budí i mrtvý proces). Push jde účtu podle
     `getPushReceiverId`, s jediným účtem jako dřív i bez známého příjemce. Ověřeno na Pixelu 5
     se dvěma účty: zpráva od bota do zabitého procesu přišla každému účtu jako jeho notifikace
     (druhý účet s tagem slotu, primární bez tagu), obě vedle sebe.
   - Notifikace mají tag podle slotu (primární bez tagu), úklid po odhlášení maže jen notifikace,
     kanály konverzací a zkratky toho účtu. Zvukové kanály od kroku 11 nesou slot taky a mažou
     se s účtem.
   - Zámek aplikace patří zařízení: zahodí se až s odhlášením posledního účtu.
   - Okna a pruhy hovoru (`CallAccountScope`) se skládají v účtu, který volá.
2. **Seznam účtů, přidání a odebrání** — hotovo (`Accounts`, `AccountsTest`):
   - Seznam je v `accounts/accounts.json` (`AccountRegistry`), ne v `AppSettings`: potřebuje
     ho vrstva kolem TDLib dřív, než vznikne cokoli z aplikace. Chybějící nebo nečitelný soubor
     znamená jen primární účet, takže se dnešní instalace nemusí nikam stěhovat.
   - Přidávaný účet (`adding`) má vlastní slot a kontejner, ale do seznamu přijde až v `Ready`.
     Účet, který v aplikaci už je, se pozná podle user id a odhlásí se (`alreadySignedIn`); QR kód
     to zastaví sám (`other_user_ids` z `TelegramSession`).
   - Odhlášený účet vedle jiných zmizí: po odhlášení TDLib vyrobí klienta u zadání čísla
     (`WaitPhoneNumber`), ten se ukončí (`TelegramSession.shutdown`, `destroy`) a smažou se
     adresáře i klíč. Poslední účet zůstává a ukáže přihlášení jako dřív.
   - **Kontejner se nezavírá** (`KoinApplication.close`): obrazovka, která ho ještě skládá, by si
     z něj při rekompozici brala instance a spadla. Práci zastaví zrušený scope, zbytek uklidí GC.
   - Globální definice nesmí `Accounts` chtít při vzniku, ani nepřímo: `CallMedia` vzniká uvnitř
     otevírání kontejneru (eager `TdlCallRepository` si ho bere z globálu) a sahala na `Accounts`
     hned. Aplikace pak padala při startu na kontrole „Accounts se otevírá znovu".
3. **Menu účtů a přepnutí** — hotovo:
   - Menu účtu ukáže s víc účty přihlášené účty (fotka, jméno, fajfka u zobrazeného) a „Přidat
     účet" vedle Můj QR kód. Ťuknutí na účet ho nastaví jako výchozí. Na iOS „Přidat účet" není
     (`canAddAccounts`, fáze 2).
   - **Seznam chatů zatím ukazuje jen výchozí účet**: filtr s režimem Vše je krok 4. Shell se
     skládá ve `DefaultAccountScope` a se změnou výchozího účtu se složí znovu od kořene.
   - **ViewModely mají úložiště po účtech** (`AccountViewModels`): `koinViewModel()` bez klíče
     hledá podle třídy a kořenové obrazovky by po přepnutí dostaly ViewModel předchozího účtu.
   - Otevření chatu a sdílení do chatu jiného účtu (notifikace, zástupce) napřed přepne výchozí
     účet; požadavek pak vyřídí shell toho účtu. Díky tomu jsou v zásobníku jen routy jednoho
     účtu a porovnávání podle id chatu (`closeChat`, `isOpenConversation`) zatím stačí.
   - Přihlášení přidávaného účtu jde přes celou plochu nad shellem a zavírá se křížkem nebo zpět
     z prvního kroku (`AuthScreen(onClose)`), což přidání zruší i s daty.
   - **Ověřeno na Pixelu 5** (účty …527 a …029): aktualizace přes stávající instalaci nechala
     účet jako primární slot, přidání druhého účtu číslem, přepínání v menu oběma směry,
     notifikace druhého účtu a ťuknutí na ni (přepne účet a otevře chat), zrušení přidání
     i úklid osiřelého slotu při startu. Odhlášení druhého účtu z jeho Nastavení: TDLib ho
     odhlásil, úklid smazal jeho notifikace a zkratky, nový klient u zadání čísla se ukončil
     natrvalo, slot zmizel ze seznamu i z disku a shell se složil znovu pro zbylý účet.
     **Neověřené:** desktop.

4. **Společný seznam chatů** — hotovo:
   - **Výběr je v menu účtu, ne čipy nad seznamem**: nahoře „Všechny účty" (fotky účtů přes
     sebe), pod tím jednotlivé účty, fajfka u toho, co seznam ukazuje. Čipy nad seznamem první
     verze jen opakovaly, co menu účtu už umělo. V režimu Vše má i fotka v pilulce podobu
     fotek všech účtů přes sebe (`StackedAccountAvatars`), ať je bez otevření menu vidět, že
     seznam ukazuje víc účtů. Účet z menu ho udělá výchozím a režim Vše vypne. Režim Vše je jedna
     volba (`AppSettings.chatListAllAccounts`), ne množina slotů, jak počítal návrh: s nejvýš
     třemi účty stačí „všechny, nebo výchozí".
   - **Obrazovka zůstává obrazovkou výchozího účtu**, společné jsou jen řádky
     (`MergedChatListContent`). Složky, menu a karty nahoře jsou výchozího účtu; hledání a archiv
     se sloučily v kroku 8.
     `ChatsViewModel` je velký a skoro všechno v něm patří jednomu účtu; slučovat ho celý by
     znamenalo přepsat obrazovku.
   - **Každý účet má v režimu Vše vlastní `ChatsViewModel`** z kontejneru svého účtu
     (`rememberChatSources`, klíč se slotem). Akce řádku (ťuknutí, nabídka, tah, výběr) jdou do
     něj a nabídka nad chatem i hlášky („Vrátit") se kreslí za každý účet (`ChatActionsHost`).
     Kořenový scope kontejneru je interní API Koinu (`scopeRegistry.rootScope`, opt-in
     s komentářem): `AccountScope` podá kontejner jen obsahu uvnitř sebe, ne jako hodnotu.
   - **Řádek se kreslí v `AccountScope` svého účtu**, takže fotky jdou z jeho souborů; odznak účtu
     je malá fotka účtu vlevo dole na avataru (`ChatRowMarks.account`). Klíč položky i klíče letu
     avataru a názvu (`chatAvatarKey`, `chatTitleKey`) nesou slot: společná skupina je v seznamu
     dvakrát.
   - **`AccountScope` podává id Uložených zpráv svého účtu** (`LocalSavedMessagesChatId`). Dokud
     ho podával jen shell, ukázal společný seznam chat VT s VT2 jako „Uložené zprávy": Uložené
     zprávy mají id vlastního účtu, a chat druhého účtu s ním má právě tohle id.
   - Řazení `mergedChatGroups` (testy `MergedChatGroupsTest`): sponzorované a vynořené nahoře,
     pak připnuté všech účtů v pořadí účtů, pak ostatní podle data poslední zprávy.
   - **Výběr víc chatů a uspořádání připnutých patří jednomu účtu**: když běží v některém účtu,
     seznam po tu dobu ukazuje jen jeho chaty (`busySource`) a lišta výběru jde do jeho
     ViewModelu. Míchat účty ve výběru (návrh to dovoloval) by znamenalo rozdělit akce lišty.
   - **Zásobník porovnává slot** (`belongsTo`, `closeChat`, `closeConversationOf`,
     `isOpenConversation`…; testy v `RouteSlotTest`), protože v něm můžou ležet chaty dvou účtů
     se stejným id. Routy odhlášeného účtu z obou zásobníků zmizí.
   - V režimu Vše notifikace a sdílení do chatu jiného účtu výchozí účet nepřepínají, chat se
     otevře rovnou.
   - **Ověřeno na Pixelu 5** se dvěma účty: výběr v menu (i složená fotka v pilulce), společný
     seznam s odznaky, režim Vše přežije
     restart, otevření konverzace jiného účtu, nabídka nad chatem jiného účtu, výběr v něm (seznam
     jen jeho chaty) a návrat, notifikace jiného účtu v režimu Vše. Desktop jen přeložený.

5. **Kterým účtem se píše** — hotovo:
   - S víc účty má avatar v hlavičce konverzace odznak účtu (`AccountBadge`, tentýž jako v řádku
     společného seznamu) a vstupní pole napíše „Zpráva jako VT" (`rememberShownAccount`). Ve
     skupině, kde jsou oba účty, se tak neodpoví ze špatného.
   - Pole proto, že avatar v hlavičce u tématu ani vlákna komentářů není, a do pole se píše vždycky.
     Účet se čte nad nápovědou, ne v ní: nápověda se skládá znovu s každým vymazáním pole a jméno
     by pokaždé naskočilo se zpožděním.
   - **Ověřeno na Pixelu 5** se dvěma účty (chat VT i VT2). S jedním účtem má zůstat pole „Zpráva"
     a hlavička bez odznaku (`rememberShownAccount` vrací `null`); na zařízení s jedním účtem
     tenhle build vyzkoušený není.
   - **Od listopadu 2026 jen tam, kde se dá splést** (`rememberConversationAccount`). Napřed to
     platilo s víc účty vždycky, i mimo režim Vše, a v chatu výchozího účtu to byl jen šum:
     kdo jsem, říká fotka v pilulce seznamu a k chatům jiného účtu se mimo režim Vše z UI
     nedostaneš. Dnes:
     - **režim Vše, nebo chat jiného než výchozího účtu** (hledání, routa ze zásobníku):
       odznak v hlavičce i pole, jako dřív;
     - **chat výchozího účtu mimo režim Vše**: odznak v hlavičce není; pole jmenuje účet jen
       u chatu, do kterého se dá psát i z jiného přihlášeného účtu (společná skupina, tentýž
       člověk). Pozná to `ChatRepository.takesPartIn` ostatních účtů: id takového chatu je
       v obou účtech stejné, dotaz jde jen do místní databáze TDLib (člen skupiny, u soukromého
       chatu poslední zpráva). Seznam načtených chatů (`positions`) na to nestačí, druhý účet má
       mimo režim Vše načtenou jen první stránku. Uložené zprávy se nepočítají — ve druhém účtu
       je to chat s tímhle účtem, ne totéž.
   - **Původní důvod, proč účet vždycky, řeší ohlášení**: notifikace nebo zástupce chatu jiného
     účtu mimo režim Vše přepne výchozí účet potichu a vede rovnou do konverzace. Konverzace to
     teď jednou ohlásí snackbarem „Přepnuto na účet VT2" (`AccountSwitchNotices`, zapisuje
     `AppShell` před `setDefault`, vyzvedne `AccountSwitchSnackbar` v `ConversationSnackbars`).
     Ohlášení po 15 s propadne: chat se otevřít nemusel a pozdější otevření téhož chatu by
     hlásilo přepnutí, které se nestalo. Sdílení přes Direct Share účet taky přepíná, ale vede
     na „Komu: …", ne do konverzace — ohlášení tam není.
   - **Ověřeno na emulátoru `Plume_Galerie_Fold`** (rozložený, dva účty): chat jen hlavního
     účtu („Plume test triaze") bez odznaku a s polem „Message", Notifikátor (píše mu i druhý
     účet) bez odznaku s „Message as …", režim Vše odznak i pole, intent notifikace druhého
     účtu přepnul výchozí účet a konverzace ukázala „Switched to VT". **Snackbar `uiautomator
     dump` nezachytil** ani jednou za šest výpisů, i když běžel celé 4 s — ověřovat snímkem.

6. **Komu notifikace patří** — hotovo:
   - **Android: jméno účtu je ve jménu konverzace** („Notifikator · VT2", `shortcutLabel`).
     Podtext (`setSubText`) konverzační notifikace nekreslí, ve skupině ani rozbalená, a jméno
     konverzace bere Android 11+ ze zkratky, ne z `conversationTitle` (ověřeno na Pixelu 5,
     Android 14). Proto má účet i název zkratky, a tutéž zkratku skládá stejně i nabídka Sdílet
     (`ShareTargets`) a zástupce na ploše — jinak by se název přepisoval tam a zpátky. Podtext
     a `conversationTitle` účet nesou taky (hodinky, auto, starší Android). Po odhlášení účtu
     zůstanou zkratkám ostatních účtů jejich názvy s účtem, dokud je notifikace nebo sdílení
     nepřepíše.
   - **Desktop: jméno účtu v podtitulu oznámení** (za jménem odesílatele ve skupině).
   - **Desktop měl s víc účty chybu v oznámeních**, opravená tady:
     - `DesktopNotifier` je v procesu jeden a `onActivated` si přepisoval presenter každého účtu,
       takže odpověď, Přečteno nebo Ztlumit z oznámení šlo do účtu, který se nastartoval poslední.
       Oznámení teď nese slot v argumentech (`account=…`, primární účet nic, testy
       v `ToastXmlTest`) a aktivace jde přes rozcestník `NotificationActivations`.
     - Id a vlákno oznámení nesou slot (`groupId` je jedinečné jen v rámci účtu).
     - Odhlášení jednoho účtu stáhne jen jeho oznámení, ne všechna.
     - Odznak nepřečtených na hlavním panelu je součet účtů (`DesktopTaskbar.setUnread(slot, …)`),
       dřív ho každý účet přepisoval svým číslem.
   - **Ověřeno na Pixelu 5** se dvěma účty. Desktop jen přeložený a otestovaný testy.

7. **Jeden hovor přes všechny účty** — hotovo:
   - Má-li hovor jiný účet (zvoní, vytáčí se, spojuje, běží; `Accounts.inCallElsewhere`), příchozí
     hovor se odmítne, ještě než se ukáže (`TdlCallRepository.busyElsewhere`): nezazvoní,
     nevznikne systémový hovor a notifikaci hovoru, kterou TDLib vydá vlastní cestou, presenter
     zahodí (`callBusy`).
   - **Volající uvidí „odmítnuto", ne „obsazeno".** Server o hovoru druhého účtu neví, takže
     obsazeno sám neohlásí jako v rámci jednoho účtu, a `discardCall` důvod nastavit nedovolí:
     nepřijatý příchozí hovor TDLib odešle jako odmítnutý.
   - **Hovor jde všem zařízením účtu**: odmítnutí z Plume ho ukončí i jinde, kde je účet
     přihlášený. Tam stihne na okamžik zazvonit (ověřeno: Pixel 7 s týmž účtem prozvonil
     a přestal).
   - **Odmítnutí nesmí zastavit média** (`declineBusy` bez `media.stop()`, na rozdíl od
     `hangUp`): `CallMedia` je v procesu jedna a běží v ní hovor druhého účtu.
   - Vytočit hovor, když hovor má jiný účet, nejde: ťuknutí na Zavolat ukáže dialog „Už máš
     hovor v jiném účtu" (`rememberStartCall`). Selhání v repository by se zapsalo do pruhu
     hovoru, jenže ten ukazuje účet s živým hovorem, takže by ťuknutí nic neudělalo (ověřeno).
   - Testy `AccountsTest` (které fáze blokují). **Ověřeno na Pixelu 5 a Pixelu 7**:
     - hovor z VT na VT2 v téže aplikaci: odchozí hovor VT je živý, příchozí pro VT2 se odmítl
       bez notifikace (deník `Declined as busy`, `Ringing notification skipped`, u VT
       `Discarded: declined`),
     - Pixel 7 (VT2) volá VT, Pixel 5 zvoní, a Zavolat v chatu VT2 ukáže dialog místo vytáčení.
   - **Oficiální Telegram na tomtéž telefonu se do zkoušek plete**: je-li v něm přihlášený
     tentýž účet, zvoní taky a jeho oznámení hovoru překrývá horní část obrazovky.

8. **Hledání a archiv přes všechny účty** — hotovo:
   - **Hledání se pouští v `ChatSearch` každého účtu** (ViewModely z `rememberChatSources`)
     a stavy slévá `mergedSearchState` (testy `MergedSearchStateTest`): vlastní chaty účet po
     účtu, veřejné chaty jednou (výchozí účet jde první, takže se otevřou v něm), zprávy přes
     server podle data napříč účty. Výsledek nese účet v `ChatSummary.slot` (`null` = účet
     obrazovky), řádek výsledku se kreslí v `AccountScope` svého účtu a má odznak účtu na
     avataru; klíč řádku i ťuknutí nesou slot (`onAccountMessageClick`).
   - **Návrhy pod prázdným dotazem jsou z výchozího účtu** (nejčastější, nedávné, hashtagy,
     doporučené kanály, reklamy): patří účtu na serveru a sloučit „nejčastější" dvou účtů
     nemá jasné pořadí. Dotaz, filtr a další stránka zpráv jdou do všech účtů.
   - **Archiv v režimu Vše slučuje archivy všech účtů** stejně jako hlavní seznam
     (`rememberMergedChats(ChatFolder.Archive)`, vlastní ViewModel za účet a složku). „Přečíst
     vše" jde do všech účtů; nastavení archivu (co dělat s odztlumenými a nepřečtenými) je
     na serveru po účtech, takže se v režimu Vše neukazuje.
   - Archivace z řádku zavře otevřenou konverzaci jen tehdy, je-li téhož účtu
     (`onChatArchived(slot, …)`): chat se stejným id ve druhém účtu otevřený zůstane.
   - **Výběr víc chatů a uspořádání připnutých v hlavním seznamu brala horní lišta z výchozího
     účtu**, i když výběr běžel v jiném; opraveno spolu s tímhle (`listViewModel`).
   - **Ověřeno na Pixelu 5** se dvěma účty (VT a výchozí VT2): „Notifi" najde Notifikátor
     v obou účtech s odznaky a ten z VT se otevře v VT („Zpráva jako VT"); hledání zpráv ukáže
     zprávu z obou účtů a skočí na ni ve správném účtu; veřejný chat je ve výsledcích jednou,
     s VT2. Archiv ukáže archivované chaty obou účtů (chat VT2 archivovaný tahem přibyl vedle
     chatu VT) a tah ho vrátí. Desktop jen přeložený.

9. **Zkratky Alt+↓/↑ a Ctrl+Tab ve společném seznamu** — hotovo:
   - Procházejí společný seznam v pořadí, jak je vidět (hlavní seznam i archiv), a chat otevřou
     v účtu řádku. `ChatSwitchShortcuts` a `neighbourChat` jsou obecné nad klíčem: v seznamu
     jednoho účtu je klíčem id chatu, ve společném id s účtem — Notifikátor ve VT a ve VT2 jsou
     dva sousední chaty, ne jeden (test `sousedniChatSUctem`).
   - Seznam za chatem otevřeným zkratkou dojede jako v seznamu jednoho účtu; index počítá za
     kartami nahoře (`headerItemCount`).
   - **Ověřeno na Pixelu 5 na šířku** (list-detail, zkratky přes `adb shell input
     keycombination KEYCODE_ALT_LEFT KEYCODE_DPAD_DOWN`): Pixel 7 Pro (VT) → Pixel 5 (VT2) →
     … → Notifikátor VT pod okrajem (seznam dojel), Alt+↑ zpět na Notifikátor VT2; pole zprávy
     ukazuje vždy účet otevřeného chatu.

10. **Sbalený sloupec seznamu v režimu Vše** — hotovo:
    - Sloupec (`CollapsedChatList`) ukazuje avatary všech účtů v pořadí společného seznamu
      (`mergedChatGroups`), každý v kontejneru svého účtu a s odznakem účtu. Ťuknutí otevře chat
      v účtu řádku, podržení nabídku nad chatem z ViewModelu toho účtu (kreslí ji
      `MergedChatActionsHosts`, ten je nad sloupcem i nad plným seznamem).
    - Výběr víc chatů patří jednomu účtu jako v plném seznamu: po dobu výběru sloupec ukazuje
      jen chaty toho účtu (ViewModel `listViewModel`, `CollapsedAccounts.listAccount`).
    - Vybraný chat se porovnává i s účtem. Dřív sloupec bral jen id, takže chat se stejným id
      v jiném účtu (otevřený z notifikace) by se zvýraznil i mimo režim Vše.
    - `AccountBadge` si posun přes okraj avataru nese sám. Druhý posun navíc ho zasunul do
      zaobleného rohu dlaždice (72 dp, ořez `LargeIncreased`) a ořez odznak usekl (ověřeno).
    - **Ověřeno na Pixelu 5 na šířku**, seznam sbalený táhlem: avatary VT i VT2 s odznaky,
      ťuknutí otevře chat ve správném účtu („Zpráva jako VT" / „VT2") a zvýraznění jde za ním,
      podržení chatu VT2 → Vybrat ukáže jen chaty VT2, ✕ vrátí společný sloupec.

11. **Zvukové kanály po účtech** — hotovo:
    - Kanál se zvukem z Telegramu (bez zvuku, tón účtu) nese slot: primární účet beze změny
      (`private_tone_5`, kanály dnešních instalací i s nastavením zůstávají), další účty
      `@<slot>` za zvukem (`private_tone_5@fs2gc9`, `soundChannelKey`). `@` není v id slotu ani
      v základu kanálu, takže `soundChannelSlot` přečte účet z id jednoznačně, i u kanálu
      konverzace pod ním (testy `SoundChannelTest`). Výchozí kanály druhů chatů zůstávají
      společné, jak návrh určil.
    - Odhlášení smaže kanály se zvukem jen toho účtu (`deleteSoundChannels(slot)`), dřív se
      mazaly až s posledním účtem. Smazání tónu bere kanály tónu jen u účtu, který ho smazal.
    - **Kopie tónu je jedna pro všechny účty** (`notification_sounds/<id>`): id tónu je id
      dokumentu, takže tentýž tón ve dvou účtech je tentýž soubor. Maže se, až ho nepoužívá
      žádný kanál (`deleteUnusedTones`).
    - S víc účty nese název kanálu se zvukem i jméno účtu („Soukromé chaty · bez zvuku · VT2"):
      v systémovém nastavení by jinak byly dva stejně pojmenované kanály. Název se dává jen při
      založení; kanál, který vznikl s jedním účtem, jméno nemá.
    - „Zvuk a vibrace" v Nastavení i v profilu chatu otevírá kanál se zvukem svého účtu
      (`openChannelSettings(slot, …)`, `NotificationSettingsViewModel` a
      `NotificationSoundsViewModel` dostávají slot z kontejneru účtu).
    - **Přechod:** kanály, které druhý účet založil před touhle změnou (`private_silent` bez
      slotu), zůstanou a mazat je bude až odhlášení primárního účtu; druhý účet si založí nové.
      Co si v nich uživatel nastavil, se nepřenese.
    - **Ověřeno na Pixelu 5**: chatu Notifikátor ve VT2 nastavený „Bez zvuku", zpráva od bota
      přišla kanálem `private_silent@fs2gc9` s názvem „Private chats · no sound · VT2" (tag
      notifikace `fs2gc9`); nastavení chatu vráceno na výchozí. **Úklid po odhlášení na zařízení
      ověřený není** (znamenal by odhlásit testovací účet) — jen testy, které z id čtou účet.

12. **Odkazy `tg://` a `t.me` s víc účty** — hotovo:
    - Odkaz zvenčí (`LinkOpenRequest.slot == null`) rozparsuje výchozí účet. Pozvánka, sdílená
      složka a mini aplikace (`actsAsAccount`, testy `LinkAccountTest`) se s víc účty zeptají,
      kterým účtem (`AccountChoiceSheet`, výchozí předvybraný); ostatní (chat, zpráva, příběh,
      samolepky) otevře výchozí účet bez ptaní.
    - **Přihlášení na webu z odkazu je mini aplikace**: `t.me/oauth?startapp=…` TDLib rozpozná
      jako mini aplikaci „OAuth Bot" (`docs/poznatky/prihlaseni.md`), takže se ptá s ní. Ptá se
      i u ostatních mini aplikací — jednají za účet (přihlášení, platby, data bota).
    - **Host odkazů je za každý účet** (`ExternalLinkHost` v shellu, ViewModel z kontejneru účtu
      přes `AccountScope` + `AccountViewModels`). Zvolený jiný účet dostane odkaz znovu přes
      `LinkOpenRequests` se svým slotem a rozparsuje si ho sám: náhled pozvánky i to, jestli je
      už členem, se liší podle účtu. Cíl se otevře v jeho zásobníku (`openExternal(slot, …)`).
    - **Odkaz z obrazovky účtu nese její slot** a na účet se neptá: Instant View, oznámení účtu
      (`rememberNoticeLinkOpener`) a čtečka QR v nové konverzaci. Dřív šel do výchozího účtu.
    - **Ověřeno na Pixelu 5** (výchozí VT2): pozvánka do testovací skupiny zvenčí (`am start -a
      VIEW -d https://t.me/+…`) ukázala výběr s předvybraným VT2; VT2 → náhled s „Join"; znovu
      a VT → náhled, „Join" přidal do skupiny VT (Bot API `getChatMember`: VT `member`, VT2
      `left`) a otevřela se skupina s tématy. `tg://resolve?domain=…` otevřel chat ve VT2 bez
      ptaní. Úklid: VT ze skupiny odebraný botem, testovací pozvánka zrušená. Mini aplikace
      z odkazu a sdílená složka na zařízení vyzkoušené nejsou.

13. **Jedna hudební fronta přes všechny účty** — hotovo:
    - `MusicQueue` je v globálním Koinu vedle `VoicePlayer`, jako `MusicControls` (Android)
      a `NowPlayingControls` (iOS). Skladba nese účet (`Current.slot`, `NowPlaying.slot`)
      a sousedy, soubory i hlášení poslechu bere fronta z repository toho účtu
      (`MusicRepositories` přes `Accounts.find`, až za běhu — v globálním modulu se na účty při
      vzniku definice sahat nesmí). Poslech se hlásí s účtem (`ListeningTracker<ListenedFile>`):
      `fileId` je číslo klienta.
    - **Proč:** fronta za každý účet se přetahovala o jeden přehrávač a jednu službu. Skladba
      z druhého účtu zastavila frontu prvního, služba (vázaná na frontu, která ji spustila)
      viděla konec hudby a skončila — hudba druhého účtu hrála bez notifikace i bez služby
      v popředí. `MusicPlaybackService` teď žádnému účtu nepatří (bez `ServiceOwner`), chat
      v notifikaci otevře v účtu skladby.
    - Konverzace frontu dostává přes `forAccount(slot)` (stejné `MusicQueueControl`, testy
      konverzace beze změny), profil a hudba v profilu předávají slot samy.
    - **Dock v konverzaci skladbu jiného účtu neukazuje**: id chatu se mezi účty potkávají (chat
      s týmž člověkem) a z konverzace jde chat otevřít jen v jejím účtu. Ovládá se z docku nad
      seznamem chatů (otevře chat v účtu skladby, `onAccountMessageClick`) a ze systému.
    - **Ověřeno na Pixelu 5**: bot poslal oběma účtům skladbu; puštěná ve VT, hned potom ve VT2 —
      služba zůstala tatáž (`dumpsys activity services`), relace přepnula na „Skladba VT2"
      a poslech skladby VT se nahlásil (6 s). Dock konverzace VT skladbu VT2 neukázal.
      Ťuknutí na notifikaci a iOS vyzkoušené nejsou.

14. **Videochat přes všechny účty** — hotovo:
    - **Pruh nad aplikací se skládá v účtu videochatu** (`VideoChatAccountScope`, jako
      `CallAccountScope` u hovoru); dřív ho skládal shell, tedy výchozí účet, a videochat jiného
      účtu pruh neměl. Ťuknutí otevře obrazovku videochatu v jeho účtu.
    - **Běží nejvýš jeden videochat** (`VideoChats.running`): sezení má každý účet vlastní, přenos
      (`LiveStreamMedia`) je ale v procesu jeden. `VideoChatSession.join` napřed opustí videochat
      v ostatních účtech (`leaveElsewhere`, test `joiningLeavesVideoChatElsewhere`).
    - **Služba v popředí žádnému účtu nepatří** (bez `ServiceOwner`): řídí ji `VideoChats.running`
      přes všechny účty, „Zavěsit" opustí videochat účtu, ve kterém posluchač je, a ťuknutí
      otevře skupinu v jeho účtu. Se službou vázanou na účet by připojení ve druhém účtu
      skončilo bez služby (stejná past jako u hudby, krok 13).
    - **Pruh ustoupí hovoru v kterémkoli účtu**, ne jen ve svém — pruh hovoru leží na tomtéž
      místě a skládá se v účtu hovoru. Hovor a videochat se dál navzájem neblokují, stejně jako
      v jednom účtu.
    - **Ověřeno na Pixelu 5** (výchozí VT2) se sondou, která ve VT založila videochat v „Plume test
      moderace" a za 10 minut ho ukončila (necommituje se): připojení ve VT a zpět → pruh nad
      aplikací (dřív by chyběl), služba v popředí; připojení téhož videochatu ve VT2 → VT odešel
      (1 účastník, na obrazovce „Vy" za VT2), služba tatáž (`dumpsys activity services`);
      Opustit → pruh i služba pryč. Ve skupině po sondě zůstanou servisní zprávy. Souběh
      s hovorem a „Zavěsit" v notifikaci vyzkoušené nejsou.

15. **Měření paměti a startu** — hotovo (Pixel 5, Android 14, 7,3 GB RAM; postupy viz
    `docs/poznatky/vykon.md`, „Víc účtů: paměť a start"):

    | | 2 účty | 3 účty |
    |---|---|---|
    | Nativní halda (`alloc`, debug build po GC) | ~61 MB | ~83 MB |
    | Java halda | ~21 MB | ~21 MB |
    | PSS procesu | ~294 MB | ~320 MB |
    | Seznam chatů hotový („Fully drawn", medián) | 971 ms | 1 037 ms |
    | Všechny TDLib připravené (`AuthorizationState -> Ready`) | 328 ms | 402 ms |
    | Všechny účty připojené (`ConnectionState -> Ready`) | 530 ms | 646 ms |

    - **Další účet stojí asi 22 MB nativní paměti** (klient TDLib s databází a spojeními);
      Java halda se nemění. Třetí účet (osobní) byl čerstvě přihlášený s databází 21 MB, takže
      u účtu s načtenými chaty bude číslo o něco vyšší.
    - **Start se s účty skoro neprodlužuje**: klienti startují souběžně, třetí účet přidal
      ~70 ms do připravenosti a ~120 ms do připojení. Dřívější „3,7 s do Ready" bylo naměřené na
      debug buildu a se studeným startem bez `speed` kompilace — srovnatelné není.
    - **Společný seznam proti seznamu jednoho účtu** (dva účty, týž build): hotový za 971 proti
      833 ms; rozdíl je v rozptylu měření (jednotlivé běhy 767–1076 ms).
    - Limit tří účtů tím pádem není otázka paměti; zvednout ho jde, až bude proč.
    - **Měřením se našly dvě chyby, opravené tamtéž:**
      - Společný seznam neohlásil „Fully drawn" (`ReportDrawnWhen` měl jen seznam jednoho
        účtu) — v režimu Všechny účty Play vitals start nikdy neviděly hotový.
      - **Změna výchozího účtu v režimu Všechny účty shodila aplikaci** (volba účtu v menu
        i přihlášení dalšího účtu, které ho udělá výchozím): `DefaultAccountScope` přes
        `key(slot)` složí celý shell znovu a starý i nový shell měly ve snímku výměny stejné
        klíče sdílených prvků (avatar, název, plocha řádku). `SharedTransitionStateMachine.
        configureActiveMatch` pak spadl na `targetBoundsProvider!!`. Klíče teď nesou instanci
        shellu (`LocalSharedElementGeneration`, `sharedKey`) a klíč plochy řádku i účet
        (`chatContainerKey`), jako avatar a název.

16. **Widget s chaty přes všechny účty** — hotovo:
    - **`ChatsWidgetUpdater` je jeden v globálním Koinu**, ne za každý účet. Soubor podkladů
      (`widget/chats.json`) i stav widgetu v Glance jsou jedny a updatery po účtech si je
      přepisovaly: widget ukazoval chaty účtu, který se změnil naposled, a každý mazal miniatury
      fotek ostatních účtů (`WidgetAvatars.prune` jen podle svých chatů).
    - **Widget ukazuje chaty podle filtru seznamu**, jak návrh určil: v režimu Všechny účty všech
      účtů (`mergedWidgetChats`: připnuté po účtech, pak nepřečtené a zbytek podle času přes účty;
      s jedním účtem přesně `widgetChats`, testy `WidgetChatsTest`), jinak výchozího účtu.
      Zapisuje se, až mají seznam všechny zobrazené účty, jinak by widget na chvíli ukázal jen
      část chatů. Odhlášený účet se vynechá; odhlášení všech widget vyprázdní jako dřív.
    - Ťuknutí na chat ve widgetu otevíralo chat v jeho účtu už dřív (`WidgetChat.slot`).
    - **Cíle sdílení (Direct Share) se nepřepisují**: `pushDynamicShortcut` zkratky přidává,
      takže zkratky víc účtů jsou vedle sebe (systém hlídá jen strop počtu).
    - **Ověřeno na Pixelu 5** (tři účty) podle podkladů widgetu (`run-as … cat
      files/widget/chats.json`): v režimu Všechny účty 12 chatů ze tří účtů a miniatury fotek
      všech účtů; po volbě VT2 jen jeho chaty a miniatury; po návratu zase všechny. **Widget na
      ploše** ukázal chaty tří účtů a součet nepřečtených, ťuknutí otevřelo chat ve správném
      účtu a po přečtení se widget sám přerovnal. Při tom se našla starší chyba: ťuknutí
      ukazovalo nad chatem „odkaz nejde otevřít" (`receiveLink`, viz poznatky k widgetu).
    - **Odznak účtu na avataru**, když widget ukazuje víc účtů (`WidgetChat.accountInitial`,
      `accountAvatar`): fotka účtu jako kulatá miniatura (`WidgetAvatars`, maže se spolu
      s miniaturami chatů), bez fotky iniciála, v kroužku barvy pozadí widgetu — jako v seznamu
      chatů. Bez něj nešly rozlišit řádky téhož chatu ve dvou účtech (Telegram, společná
      skupina). S jedním účtem se nekreslí.
    - **Klíč řádku widgetu nese účet** (`WidgetChat.itemId`; primární účet má dál id chatu):
      týž chat ve dvou účtech má stejné id a `LazyColumn` widgetu chce klíče jedinečné.
    - Ověřeno na ploše Pixelu 5: odznaky fotek VT a osobního účtu, iniciála „V" u VT2.

17. **Nová zpráva s výběrem účtu** — hotovo:
    - `NewChatRoute`, `NewContactRoute` a `NewGroupMembersRoute` jsou `AccountRoute`, takže se celá
      větev nové konverzace (kontakty, hledání, tajný chat, nový kontakt, nová skupina i kanál)
      skládá v kontejneru zvoleného účtu. **`slot` má výchozí hodnotu** (primární účet): dřív
      to byly `data object` a uložený zásobník se tak přečte bez zvednutí `SAVED_ROUTES_VERSION`.
    - **Řádek „Psát jako"** nahoře v Nové zprávě (jen s víc účty, `ChoiceRow` s nabídkou účtů),
      výchozí účet předvybraný. Jiný účet vymění routu na tomtéž místě zásobníku za routu
      s jeho slotem; zpět pak vede rovnou do seznamu, ne přes Novou zprávu každého účtu.
      Při výběru pro tajný chat se nenabízí — chat se zakládá v účtu, ve kterém výběr začal.
    - Nová skupina a nový kanál přímo z FAB menu jdou dál výchozím účtem; z Nové zprávy účtem
      zvoleným v ní.
    - **Ověřeno na Pixelu 5** (výchozí VT2): Nová zpráva z FAB ukázala „Psát jako VT2", volba
      osobního účtu přinesla jeho kontakty, volba VT a Uložené zprávy otevřely Uložené zprávy
      VT („Zpráva jako VT"), zpět vedlo do seznamu chatů.

18. **Online jen účty ve filtru** — hotovo:
    - `ConnectionController` posílá `setOption online` jen v popředí a jen za účet, který seznam
      chatů ukazuje (`AccountVisibility`): v režimu Všechny účty všechny, jinak výchozí. Dřív
      byly v popředí online všechny přihlášené účty, takže protějšky viděly online i účet, který
      uživatel zrovna nepoužíval.
    - **Filtr je v `:client:shared`** (`AppSettings.chatListAllAccounts`, výchozí účet), kam `core`
      nevidí: rozhraní `AccountVisibility` je v `core`, implementace v globálním `appModule`.
      Hodinky ji nemají a účet je tam online v popředí jako dřív.
    - Obnova spojení při návratu do popředí (`setNetworkType`) zůstává pro všechny účty — jde
      o síť, ne o to, kdo je vidět.
    - **Ověřeno na Pixelu 5**: seznam jen s osobním účtem — v jeho chatu s VT hlavička „naposledy
      dnes ve 20:08" (VT po přepnutí přestal být online); režim Všechny účty — VT má na avataru
      tečku online. Stav viděl osobní účet, tedy server, ne Plume samo.

19. **Volby, které patří účtu** — hotovo:
    - `AccountSettings` (`AppSettings.forAccount(slot)`, `single` v kontejneru účtu): souhlas
      s mini aplikací (`confirmed_web_app_bots`), použité jednorázové klávesnice, synchronizace
      adresáře, stav synchronizace automatického stahování a čas posledního úklidu médií. Klíč
      s příponou slotu, primární účet na dnešních klíčích.
    - Nejhorší byla synchronizace adresáře: jedna volba pro všechny `ContactSyncer`y znamenala,
      že zapnutí v Nastavení nahrálo kontakty do všech přihlášených účtů.
    - Jednorázová klávesnice: chat s botem má v každém účtu totéž id (id bota), ale jiné zprávy,
      takže použití v jednom účtu přepsalo záznam druhého a klávesnice se tam rozbalila znovu.
    - **Uložené zprávy ve výsledcích hledání přes víc účtů:** TDLib je najde pod jménem účtu,
      takže hledání „VT" vracelo „VT" a „VT2" se záložkou místo fotky. Vyřazuje je
      `mergedSearchState` podle id Uložených zpráv každého účtu; obrazovka znala jen id účtu
      obrazovky, a pod ním je v ostatních účtech soukromý chat s ním — ten teď nezmizí. Řádek
      „Uložené zprávy" dává hledání účtu obrazovky jako dřív; název chatu ve výsledku se bere
      až v kontejneru jeho účtu (`chatTitle`).
    - **Další účty po aktualizaci stav synchronizace stahování nemají** (`null`), takže se
      zachovají jako aktualizace z verze bez synchronizace: vybrané úrovně pošlou na svůj server,
      výchozí úrovně nahradí předvolbami ze svého serveru. Na Pixelu 5 si oba další účty stáhly
      předvolby shodné s výchozími (`auto_download_sync_<slot>` v souboru nastavení).
    - **Ověřeno na Pixelu 5** (režim Všechny účty): hledání „VT" ukázalo soukromé chaty
      osobního účtu s VT a VT2 s jejich fotkami (dřív Uložené zprávy obou účtů se záložkou),
      hledání „Saved" jeden řádek Uložených zpráv. Přepínač synchronizace adresáře se na
      zařízení nezapínal (nahrál by kontakty); rozdělení klíčů hlídá `DataStoreAppSettingsTest`.

20. **Klíč přehrávání s účtem** — hotovo:
    - `VoiceKey(slot, chatId, messageId, part)`; `VoicePlayer` zůstává jeden pro proces (hraje
      nejvýš jeden zvuk). Filtrovat jeho stav po účtech by nešlo: hudbu z jiného účtu ukazuje
      dock nad seznamem chatů a ten by ji přestal vidět.
    - Zastavení při zavření chatu (`stop(slot to chatId)`) a dock (`docksIn(slot, chatId)`) se
      ptají na účet; skladba z jiného účtu do docku konverzace nepatří (dřív to řešilo porovnání
      s `NowPlaying.slot` v obrazovce).
    - **Skutečná chyba byla v bublině** (`ScreenItemScope.voicePlayback`): porovnávala jen id
      zprávy, takže by ji samotný klíč s účtem neopravil.
    - **Ověřeno na Pixelu 5** na skladbě od testovacího bota ve skupině „Plume test moderace"
      (VT i VT2 členy, zpráva má v obou účtech totéž id): skladba puštěná za VT2 v bublině za
      VT nehraje (▶, 3:00), za VT2 ukazuje průběh; před opravou ukazovala průběh v obou.

21. **Hodinky a účty telefonu** — hotovo (hodinky samy zůstávají na jednom účtu):
    - Žádost hodinek o přihlášení (`WatchLoginService`, `answerWatchLogin`) čeká na všechny účty
      zvlášť a nabídne každý přihlášený: s jedním Zamítnout / Přihlásit jako dřív, s víc účty
      tlačítko s křestním jménem za každý. Víc než tři akce notifikace neukáže, takže při třech
      účtech Zamítnout ustoupí (odhrnutí je zamítnutí taky).
    - Akce nese slot (`EXTRA_SLOT`) a každá má vlastní request code: intenty lišící se jen extra
      systém při hledání vydané akce nerozlišuje a všechna tlačítka by nesla poslední slot.
      Odhlášený účet z tlačítka = nezdar, ne potvrzení jiným účtem.
    - Přepis hlasovky z hodinek (`WatchTranscriptionService`) bere účet podle user id z žádosti
      (id Uložených zpráv), ne výchozí: hodinky přihlášené druhým účtem dřív dostaly `WrongAccount`.
    - **Ověřeno na Pixelu 5** (tři účty) dočasným buildem, který při startu zavolal
      `answerWatchLogin` s vymyšleným odkazem: notifikace s tlačítky VT, VT2 a Vladimír bez
      Zamítnout; ťuknutí na VT potvrzovalo slotem `0`, ačkoli výchozí je VT2 (`fs2gc9`), server
      kód odmítl (`AUTH_TOKEN_INVALID`). Se skutečnými hodinkami a přepis hlasovky neověřené.

22. **Hovory a Stahování přes všechny účty** — hotovo:
    - V režimu Všechny účty jsou historie hovorů a Stahování společné, s odznakem účtu
      u řádku (`rememberCallSources` / `rememberDownloadSources` nad
      `rememberAllAccountsEntries`, ViewModel každého účtu z jeho kontejneru jako
      u `rememberChatSources`). Řádek se skládá v `AccountScope` svého účtu a otevírá chat
      v něm. Podrobnosti v `hovory.md` („Historie hovorů") a `prohlizec-medii.md` („Stahování").
    - Moje příběhy zůstávají výchozího účtu (rozhodnuto): příběhy jsou „moje" jednoho účtu.
      Nastavení se otevírají za výchozí účet, ale od listopadu 2026 mají pod kartou účtu řádky
      se všemi účty a přepnou se na kterýkoli, aniž by se změnil výchozí účet
      (`docs/poznatky/nastaveni.md`, „Nastavení jiného účtu"). Moje příběhy, a mimo režim Vše i Hovory
      a Stahování, mají s víc účty pod nadpisem jméno účtu (`AccountTopAppBar`).
    - Archiv v režimu jednoho účtu je archiv výchozího účtu, a to je ten zobrazený — správně.
    - **Ověřeno na Pixelu 5** (tři účty, režim Vše): historie hovorů ze všech tří účtů proložená
      podle data s odznaky, řádek VT2 otevřel chat ve VT2 („Message as VT2"); Stahování
      s dokumentem staženým za VT i VT2, „Vymazat stažené" jedním snackbarem a „Vrátit"
      vrátilo oba i po uplynutí lhůty; Moje příběhy s „VT2" pod nadpisem.

23. **Baterie dalších klientů TDLib** — změřeno na Pixelu 5 (tři účty, build bez `debuggable`,
    zhasnutý displej, hodina na kolo, Wi-Fi). Postup a pasti: `vykon.md`, „Víc účtů: baterie".
    - **Mimo pushe stojí další klienti nula.** Proces je celou hodinu zmražený, bez buzení,
      budíků a úloh; Android ho rozmrazí jen pushem.
    - **Push probudí proces se všemi klienty a každý se znovu připojí k serveru**: se třemi
      klienty 10–28 kB dat na push, s jedním kolem 3–5 kB.
    - Spotřeba Plume za hodinu (odhad `batterystats`, ne naměřený proud):

      | Kolo | Klienti | Pushe (zpracované) | mAh | CPU | Wakelock | Úlohy | Data |
      |---|---|---|---|---|---|---|---|
      | B1 | 3 | 6 (6) | 0,50 | 10,4 s | 25 s | 6 | 169 kB |
      | A1 | 1, osobní účet | 6 (0) | 0,18 | 3,2 s | 25 s | 0 | 30 kB |
      | B2 | 3 | 11 (11) | 0,67 | 12,5 s | 1 min 34 s | 9 | 194 kB |
      | V1 | 1, VT | 17 (6) | 0,59 | 11,9 s | 30 s | 6 | 46 kB |
      | B4 | 3 | 20 (20) | 2,43 | 47 s | 2 min 51 s | 24 | 195 kB |

      Testovací pushe patří VT. A1 je proto zahodil (osobní účet je nerozšifruje) a vyšel
      levný z jiného důvodu; V1 je zpracoval a zahodil jedenáct pushů osobního účtu. Ve V1
      a B4 svítil část hodiny displej (zhasínání po 30 min), Plume ale zůstala v pozadí. Kola
      A2 (vypadl kabel), B3 (statistiky se nevynulovaly) a V2 (Plume deset minut v popředí)
      vyřazená.
    - **Zpracovaný push stojí se třemi klienty 0,06–0,12 mAh, s jedním asi 0,04 mAh** (V1 po
      odečtení zahozených pushů po ~0,03 mAh jako v A1). Další dva klienti tedy přidají
      desítky procent, ne násobek; sto pushů denně dělá řádově 2 mAh navíc, kolem 0,05 %
      baterie. Pro srovnání displej spálil za tutéž hodinu 27–285 mAh, když svítil.
    - **Víc než počet klientů rozhoduje čekání na doručení notifikace.** Push s odloženou
      notifikací (účet je online jinde) drží telefon vzhůru 8 s ve službě a pak
      `NotificationDeliveryWorker` 5–10 s. V nejrušnější hodině (B4) to bylo 24 úloh a skoro
      tři minuty wakelocku; s jedním účtem by to bylo stejné.
    - Limit tří účtů kvůli baterii zvedat ani držet netřeba, rozhoduje paměť (krok 15).
    - **Neověřené:** popředí (Pixel 5 se po zhasnutí zamyká kódem), mobilní data a velký účet
      ve všech třech slotech.

**Zbývá pro fázi 1:**
   - iOS slot přijímá, ale neukládá (identifikátory připomenutí, rozšíření pro sdílení); fáze 2.
