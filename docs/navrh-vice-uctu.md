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
   - Platí s víc účty v obou režimech, ne jen v režimu Vše: konverzace otevřená z notifikace
     nebo odkazu patří účtu, o kterém uživatel nemusí vědět.
   - **Ověřeno na Pixelu 5** se dvěma účty (chat VT i VT2). S jedním účtem má zůstat pole „Zpráva"
     a hlavička bez odznaku (`rememberShownAccount` vrací `null`); na zařízení s jedním účtem
     tenhle build vyzkoušený není.

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

**Zbývá pro fázi 1:**
   - Stránky bez id zatím slot nemají a skládají se v účtu shellu (výchozím): stránky nastavení
     účtu, `ArchiveRoute`, `CallsRoute`, `DownloadsRoute`, `MyStoriesRoute`, `NewChatRoute`.
     Určí to UI nastavení po účtech; s krokem 3 je to v pořádku, protože shell je vždycky
     výchozí účet.
   - `VoicePlayer` klíčuje přehrávání `VoiceKey(chatId, messageId)` bez účtu: hlasovka se stejným
     klíčem v jiném účtu (společný kanál) by v bublině ukázala, že hraje.
   - Widget má jeden soubor podkladů a miniatury maže jeden updater: se dvěma účty by se
     přepisovaly. Cíle sdílení (Direct Share) se publikují za každý účet zvlášť.
   - Online (`setOption online`) mají v popředí všechny účty, ne jen ty ve filtru.
   - Volby `AppSettings`, které patří účtu (`confirmed_web_app_bots`…), zatím slot nemají.
   - Hodinky si přihlášení potvrzují výchozím účtem telefonu.
   - iOS slot přijímá, ale neukládá (identifikátory připomenutí, rozšíření pro sdílení); fáze 2.
   - Kolik paměti a baterie stojí další klient TDLib, změřit. I start: na Pixelu 5 (debug build)
     se dvěma účty došly oba klienty do `Ready` za 3,7 s od startu a do té doby je vidět
     „Připojování"; srovnání s jedním účtem na tomtéž zařízení chybí.
