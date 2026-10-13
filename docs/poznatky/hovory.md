# Hovory

Poznatky k jedné oblasti aplikace. Obecná pravidla projektu a rejstřík ostatních souborů jsou
v [CLAUDE.md](../../CLAUDE.md); poučení z opravy v téhle oblasti patří sem.

Plume hovory zatím neumí — jen je ukazuje v historii a ohlásí příchozí (viz
`docs/poznatky/typy-zprav.md`, „Zpráva o hovoru", a `docs/poznatky/notifikace.md`, „Ohlášení
příchozího hovoru"). Tenhle soubor drží, co se o hovorech zjistilo průzkumem (13. 10. 2026);
plán je v `docs/roadmap.md`, „Hovory".

## Co dělá TDLib a co ne

TDLib obstará **signalizaci a klíč**: `createCall` / `acceptCall` s `CallProtocol`, stavy přes
`updateCall` (`Pending` → `ExchangingKeys` → `Ready`), v `CallStateReady` dohodnutou verzi,
servery, klíč, emoji k ověření a JSON `config`; signalizační pakety protějšku chodí
`updateNewCallSignalingData` a naše se posílají `sendCallSignalingData`. **Zvuk a obraz ne** —
to je práce knihovny nad WebRTC (tgcalls, ntgcalls).

## Ověřeno sondou na dvou účtech

Dočasná sonda v debug buildu (necommitnutá) na Pixelu 5 přijímala hovory z oficiálního
Telegramu pro Android na Pixelu 7 s různým `libraryVersions` a zapisovala `CallStateReady`:

- **Server vrátí JEDNU verzi**, ne seznam protějšku. `CallStateReady.protocol.libraryVersions`
  má jediný prvek — tu, na které se strany shodly.
- **Oficiální Android bere 10.0.0, 11.0.0 i 12.0.0**; z celé řady (13 … 7, 5.0.0, 2.7.7)
  vybral **8.0.0**. 10.0.0 a 11.0.0 jsou tedy pořád ve hře, ale preferované nejsou.
- **Bez společné verze skončí hovor hned v `CallStateError`** (nabídka „99.0.0"), oficiální
  klient ukáže „Canceled Call". Plume tedy nesmí nabízet verzi, kterou neumí — druhá strana
  by na ni přistoupila.
- **Servery jsou dva druhy pokaždé**: jeden `CallServerTypeWebrtc` (TURN i STUN Telegramu,
  se jménem a heslem, port 1400) a jeden `CallServerTypeTelegramReflector` (UDP, `peerTag`
  16 bajtů). Jestli hovor spojí jen TURN bez reflectoru, se bez mediální vrstvy ověřit nedá.
- **Oficiální klient pošle signalizaci hned po `Ready`** (5 paketů 1,5–2,3 kB v první
  desetině vteřiny, pak další) — zašifrované klíčem hovoru, čitelné až v knihovně.
- **Délku hovoru oficiální klient počítá od `Ready`**, i když žádná média neprotekla
  („Outgoing Call (8 seconds)"). Plume, který hovor přijme a nespojí, tak protějšku zapíše
  „uskutečněný" hovor — přijímat se smí, až bude čím ho spojit.

## Knihovny (stav 13. 10. 2026)

| | tgcalls | ntgcalls |
|---|---|---|
| Kdo | Telegram, používají ho všichni oficiální klienti i Telegram X a Unigram | pytgcalls, hlavně pro boty; na mobilu ho používá Mithka (Flutter + TDLib) |
| Licence | LGPL-3.0 | LGPL-3.0 |
| Verze | 2.7.7, 5.0.0, 7–13 (+14, 18, 19 nové) | **jen 8.0.0, 9.0.0, 12.0.0, 13.0.0** |
| WebRTC | fork Telegramu (`ali-fareed/webrtc`, ~M123 s patchi) | vlastní build m152 |
| Android | stavět sám (CMake nad tgcalls + forkem WebRTC) | **hotové AAR v Maven Central** (`io.github.pytgcalls:ntgcalls`, ~37 MB za 4 ABI) |
| iOS | xcframework z Telegram-iOS (Bazel), tak to dělá Mithka | není |
| Desktop | tgcalls nad `tg_owt` | C API, hotové knihovny pro Linux, Windows a macOS arm64 (ne Intel) |
| Klíč z TDLib | ano | ano (`skip_exchange(encryption_key, is_outgoing)`) |

- **Z Telegram X ani z Telegramu pro Android se nic nekopíruje** — obojí je GPL a Plume by
  přestal být MIT. LGPL knihovna dynamicky linkovaná (`.so` na Androidu, sdílená knihovna na
  desktopu) MIT nemění; statický xcframework na iOS je s LGPLv3 sporný (Mithka ho tak vydává).
- **Telegram X verzi 11.0.0 vyřadil** (2024: „unused and does not work"); tgcalls na ní teď
  intenzivně pracuje (základ verzí 18 a 19). Vlastní implementace 10/11 nad holým WebRTC
  (cesta B v roadmapě) by tak stála na nejméně stabilní části protokolu.
- **Aktuální `InstanceV2ReferenceImpl` se proti neupravenému WebRTC nepřeloží** — používá
  háčky z forku Telegramu (`dtls_transport_factory`, `external_transport_security`), za běhu
  volitelné (`network_use_mtproto`).
- **Protokol reflectorů není nikde popsaný** (core.telegram.org popisuje jen verze pod 7.0),
  jediná specifikace je `tgcalls/v2/ReflectorPort.cpp` a jeho port v ntgcalls.
- **Mithka s ntgcalls vypnula R8** — JNI hledá třídy podle jména. Plume R8 má, takže to chce
  keep pravidla, ne vypnutí (`docs/poznatky/release.md`, „R8").

## Neověřené (zjistit před psaním kódu)

- **16KB stránky**: Play od Androidu 15 chce nativní knihovny zarovnané na 16 kB; u AAR
  ntgcalls zkontrolovat (`zipalign -c -P 16`, `readelf -l`) dřív, než se na něm postaví.
- **VoIP pushe na iOS** pro aplikaci s vlastním `api_id` (`deviceTokenApplePushVoIP`) — bez
  nich hovor na zamčeném iPhonu nezazvoní; Plume zatím čeká i na běžný APNs certifikát.
- **Spojení jen přes TURN bez reflectoru** — až s mediální vrstvou.
