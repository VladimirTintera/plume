# Zámek aplikace

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

## Co zámek chrání a co ne

`AppLock` (`core/lock`) zamyká PINem, případně biometrií, **jen obrazovku**. Chrání před někým,
kdo drží odemčený telefon — ne před rootnutým zařízením, které umí smazat soubor se záznamem
(stejná mez jako u klíče k databázi, viz [databaze-spojeni-proxy.md](databaze-spojeni-proxy.md),
„Šifrovaná databáze TDLib").

- **Klíč k databázi zámek NEPOUŽÍVÁ a ověření nevyžaduje.** Push budí proces, když je telefon
  v kapse, a TDLib potřebuje databázi dřív, než se složí UI; klíč za PINem by znamenal, že
  z pozadí nechodí notifikace vůbec.
- **Notifikace se zámkem nemění.** Co ukazují, řídí volba náhledu v Nastavení → Notifikace;
  druhé tlačítko na totéž by se s ní jen přetahovalo.
- **Odhlášení zámek zahodí** (`AuthStage.LoggingOut` i `WaitPhoneNumber`). Zámek patří účtu:
  nový účet na tomtéž telefonu by jinak zdědil cizí PIN. Zároveň je to cesta ven pro toho, kdo
  PIN zapomněl — odhlásit se, přihlásit znovu.

## Záznam

Všechno, co zámek ví, je **jeden záznam v `AppLockStore`** (Keystore / Keychain, viz
`SecretStore`), ne v `AppSettings`: `v1;iterace;sůl;otisk;doba;biometrie;chyby;brzdaDo`.

- **Jeden záznam = jeden zápis.** Zapnutí, doba, biometrie a počítadlo chyb se mění spolu, takže
  nemůže vzniknout stav „zámek zapnutý, ale PIN chybí". Záznam neexistuje = zámek je vypnutý.
- **Poškozený nebo neznámý záznam se zahodí a zámek je vypnutý.** Druhá možnost — zamknout
  natrvalo — by uživatele dostala ven jen odhlášením, a to kvůli chybě v aplikaci.
- **Otisk PINu je PBKDF2-HMAC-SHA256** (`PinHash.kt`, vlastní nad `hmacSha256` z Okio, ověřené
  vektory RFC 7914 v `PinHashTest`). Platformní implementace by znamenaly dvě kopie výpočtu, které
  se musí shodovat bajt po bajtu. Čtyřmístný PIN má deset tisíc možností a pomalý výpočet z něj
  silné heslo neudělá — hlavní ochrana je šifrování záznamu klíčem z Keystore a brzda pokusů;
  iterace (10 000) jsou pojistka pro případ, že by ven unikl záznam i s klíčem.
- **Porovnání otisků je bez předčasného konce** (`constantTimeEquals`).

## Kdy se zamyká

- **Studený start se zapnutým zámkem začíná zamčený**, v pozadí se zamkne po zvolené době
  (1 min, 5 min, 1 h, 5 h). **„Hned" není**: zamkl by aplikaci každý výběr fotky nebo souboru,
  protože systémový výběr ji pošle do pozadí.
- **Doba v pozadí se měří DVĚMA hodinami a bere se delší** (`shouldLockAfter`). Monotónní čas
  procesu v hlubokém spánku stojí (telefon ležel přes noc → „uběhlo pár vteřin"), čas na zdi
  jde přestavit dozadu. Každé samo jde obejít, obě naráz ne.
- **Brzda pokusů:** pět chyb projde, pak 30 s a každá další chyba dvojnásobek, nejvýš hodina
  (`blockFor`). **Počítadlo je v záznamu a přežije restart**, jinak by brzdu obešlo zabití
  aplikace. Konec brzdy je čas na zdi, a ten jde přestavit dopředu i dozadu — proto se brzda
  ořízne na nejvýš tolik, kolik za daný počet chyb smí trvat (`effectiveBlockedUntil`); hodiny
  vrácené o den ji tak na den neprotáhnou.
- **Ověření PINu v Nastavení počítá do brzdy taky** (`verify`). Jinak by se PIN dal hádat přes
  Nastavení odemčené aplikace — a odemčenou ji může držet kdokoli, komu ji majitel půjčil.
- **Biometrie odemkne i během brzdy a brzdu zruší.** Otisk hádáním nikdo neobejde a kdo PIN
  zapomněl, dostal by se jinak dovnitř jen odhlášením.

## Stav

Hotové je jádro (`AppLock`, záznam, brzda, napojení na popředí a odhlášení v Koinu) s testy
`AppLockTest` a `PinHashTest`. **Obrazovka zámku, biometrie, skrytí v přehledu úloh a Nastavení
ještě nejsou** — co zbývá, je v [docs/roadmap.md](../roadmap.md), „Zámek aplikace".
