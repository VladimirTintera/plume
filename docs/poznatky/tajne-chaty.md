# Tajné chaty

Tajný chat (`ChatTypeSecret`) je šifrovaný mezi DVĚMA ZAŘÍZENÍMI, ne mezi účty: běží jen na
zařízení, které ho založilo, a na tom, které ho na druhé straně přijalo. Plume je umí přijmout,
založit (profil člověka → ⋮ → „Začít tajný chat"), psát do nich, nastavit časovač zpráv
a ukončit je (profil tajného chatu → ⋮ → „Ukončit tajný chat").

- **Bez `useSecretChats = true` v `setTdlibParameters` TDLib tajné chaty vůbec nepřijme ani
  nezaloží.** Plume ho měl vypnuté, takže tajný chat od protějšku přijalo jiné zařízení účtu
  a v Plume nebyl. Hodnota se dá přepnout u stávající databáze; TDLib ji čte při každém startu.
- **Kdo tajný chat přijme, rozhoduje rychlost a nastavení relace.** Dostane ho první zařízení
  účtu, které je online a má zapnuté „Přijímat tajné chaty" (Nastavení → Zařízení → relace).
  Při ověřování se proto oficiální aplikaci na obou telefonech přijímání vypnulo.
- **Id tajného chatu NENÍ id uživatele** (je to `-2 000 000 000 000 + secretChatId`).
  Kód, který bral id soukromého chatu jako id protějšku, u tajného chatu mlčel: dostupnost
  v hlavičce (`presenceUpdates`) se proto bere z `ChatTypeSecret.userId`. **Ještě to tak dělají**
  dostupnost a ověření v seznamu chatů (`userStatusUpdates`, `userUpdates` → `edit(userId)`) —
  u tajného chatu se nepromítnou (zapsané v roadmapě).
- **Stav tajného chatu je druh práva psát** (`WriteAccess.SecretPending` / `SecretClosed`
  v `chatAccess`, živě přes `secretChatUpdates` v `accessUpdates`). Místo vstupního pole pak
  stojí věta — „Čeká se, až se protějšek připojí…" / „Tajný chat je ukončený…", stejně jako
  u uzavřeného tématu. Vlastní pruh nad konverzací by zdvojoval to, co pole stejně musí říct.
- **Prázdný tajný chat má pod jménem v seznamu svůj stav** (`secretChatPreview`, „Čeká se…",
  „Tajný chat je připravený", „…ukončený"), živě přes `secretChatUpdates`. Bez toho by řádek
  měl jen jméno — a protějšek s tajným chatem má obvykle i obyčejný chat se stejným jménem.
- **Zámek před jménem** (`SecretChatLock`) v seznamu i v hlavičce, barvou štítku ověření.
- **Zákaz snímků obrazovky** (`SecureScreen`) celé konverzace. Volá se z lambdy hlavičky, ne
  z těla `ConversationScreen` — ta je v dexu na hraně registrů (viz CLAUDE.md, Gotchas).
- **Časovač zpráv** je `setChatMessageAutoDeleteTime` jako u obyčejného chatu, jen v tajném
  chatu bere libovolné sekundy a běží od PŘEČTENÍ. Výběr nabízí 10 s, 30 s, 1 min, 1 h, den
  a týden (`autoDeleteOptions(secret = true)`) s vlastní větou. Kvůli tomu převod doby na text
  umí sekundy (`DurationUnit.Seconds`; dřív se pod minutu zaokrouhlovalo na minutu).
- **Každé „Začít tajný chat" založí NOVÝ** (`createNewSecretChat`); starý se neobnovuje — je
  vázaný na zařízení. Proto se nabízí i z profilu jiného tajného chatu.
- **Notifikace o novém tajném chatu** (`NotificationTypeNewSecretChat`) se ukazuje jako
  „Začíná s tebou tajný chat" (bez zprávy, `ChatNotification.messageId == null`).
  **Push o novém tajném chatu TDLib zpracovat neumí** — `processPushNotification` vrátí
  „New secret chat notification is not supported" (hláška je přímo v knihovně). Chat dorazí
  updatem po připojení; chyba se proto loguje jen informativně, ne do Crashlytics.
- **Ukončení z protějšku dorazí se zpožděním, když je Plume v pozadí** (zmrazený proces):
  ověřeno, že po návratu do popředí se stav do chvíle přepne. V popředí okamžitě.

## Šifrovací klíč

Profil tajného chatu → „Šifrovací klíč" (řádek s malým obrázkem) → list s obrázkem 12 × 12,
šestnáctkovými čísly a větou, s kým a co porovnat (`SecretChatKey`, `SecretChatKeySheet`).

- **Podoba je daná protokolem a oficiálními klienty, ne vkusem.** `keyHash` (36 bajtů) se kreslí
  po dvou bitech od NEJNIŽŠÍCH bitů prvního bajtu, zleva doprava a shora dolů, ve čtyřech
  pevných barvách FFFFFF, D5E6F3, 2D5775, 2F99C9 (dokumentace TDLib). Barvy jsou proto natvrdo
  — výjimka z pravidla „barvy jen z `colorScheme`": porovnává se s obrázkem v jiné aplikaci.
- **Čísla taky jako oficiální Android klient**: prvních 32 bajtů, osm na řádek, velkými písmeny,
  uprostřed širší mezera („26 FA BB DD  03 6D 15 37"). První verze psala „26fa bbdd" — stejné
  hodnoty, ale při porovnání s oficiální aplikací vypadaly jako jiné.
- **List se otevírá rovnou celý** (`enabledValues = Hidden, Expanded`): napůl otevřený schoval
  právě větu, která říká, co s obrázkem dělat.
- Prázdný `keyHash` (chat ještě není dohodnutý) → řádek se neukazuje. Starý 16bajtový hash
  (vrstva pod 46) se kreslí 8 × 8.
- **Ověřeno proti oficiální aplikaci** (12. 10. 2026): Plume na Pixelu 5 ↔ oficiální Telegram
  na Pixelu 7 (Plume tam zastavený, aby chat přijala oficiální aplikace) — obrázek shodný bod
  po bodu, čísla shodná. Plume ↔ Plume taky.

## Ověření

Se dvěma účty na dvou telefonech (12. 10. 2026, Plume ↔ Plume, oficiální aplikaci přijímání
tajných chatů vypnuté): založení z Pixelu 5 (zámek v hlavičce, `SECURE` v `dumpsys window`),
přijetí na Pixelu 7 (zámek v seznamu), zprávy oběma směry i „přečteno", časovač 10 s (servisní
zpráva, zpráva zmizela na obou stranách po přečtení), čekání na protějšek (Plume na druhé
straně zastavený → věta místo pole, po spuštění pole do 4 s), notifikace o novém tajném chatu,
ukončení z obou stran (věta místo pole u obou). **Neověřeno**: média a hlasovky v tajném chatu,
iOS (jen přeložené), desktop.
