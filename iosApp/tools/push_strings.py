#!/usr/bin/env python3
"""
Texty APNs notifikací pro iOS: iosApp/iosApp/{en,cs}.lproj/Localizable.strings.

Telegram posílá v pushi jen `loc-key` a `loc-args` (typ zprávy a jména, text); větu z nich skládá
iOS podle `Localizable.strings` aplikace. Chybějící klíč iOS ukáže doslova („MESSAGE_PHOTO"),
proto musí být v souboru každý klíč, který Telegram umí poslat.

Angličtina se bere z https://core.telegram.org/api/config.json (klíč `push`), čeština je tady.
Skript skončí chybou, když Telegram přidal klíč, který tu česky není, nebo když se liší argumenty.

Česky přítomným časem („Jana posílá fotku"): z pushe se rod odesílatele nepozná.

Spuštění z kořene repa:  python3 iosApp/tools/push_strings.py
"""
import json
import re
import sys
import urllib.request
from pathlib import Path

CS = {
"AUTH_REGION": "Nové přihlášení z neznámého zařízení {1}, místo: {2}",
"AUTH_UNKNOWN": "Nové přihlášení z neznámého zařízení {1}",
"CHANNEL_MESSAGES": "{1}: album",
"CHANNEL_MESSAGE_AUDIO": "{1}: hlasová zpráva",
"CHANNEL_MESSAGE_CONTACT": "{1}: kontakt {2}",
"CHANNEL_MESSAGE_DOC": "{1}: soubor",
"CHANNEL_MESSAGE_DOCS": "{1}: soubory ({2})",
"CHANNEL_MESSAGE_FWDS": "{1}: přeposlané zprávy ({2})",
"CHANNEL_MESSAGE_GAME": "{1} tě zve ke hře {2}",
"CHANNEL_MESSAGE_GAME_SCORE": "{1}: skóre {3} ve hře {2}",
"CHANNEL_MESSAGE_GEO": "{1}: poloha",
"CHANNEL_MESSAGE_GEOLIVE": "{1}: živá poloha",
"CHANNEL_MESSAGE_GIF": "{1}: GIF",
"CHANNEL_MESSAGE_GIVEAWAY": "{1}: soutěž o {2}× Premium na {3} měs.",
"CHANNEL_MESSAGE_GIVEAWAY_STARS": "{1}: soutěž o {3} hvězd ({2})",
"CHANNEL_MESSAGE_NOTEXT": "{1}: zpráva",
"CHANNEL_MESSAGE_PAID_MEDIA": "{1}: placený příspěvek za {2} hvězd",
"CHANNEL_MESSAGE_PHOTO": "{1}: fotka",
"CHANNEL_MESSAGE_PHOTOS": "{1}: fotky ({2})",
"CHANNEL_MESSAGE_PLAYLIST": "{1}: hudba ({2})",
"CHANNEL_MESSAGE_POLL": "{1}: anketa {2}",
"CHANNEL_MESSAGE_QUIZ": "{1}: kvíz {2}",
"CHANNEL_MESSAGE_ROUND": "{1}: videozpráva",
"CHANNEL_MESSAGE_STICKER": "{1}: samolepka {2}",
"CHANNEL_MESSAGE_STORY": "{1}: příběh",
"CHANNEL_MESSAGE_TEXT": "{1}: {2}",
"CHANNEL_MESSAGE_TODO": "{1}: seznam úkolů {2}",
"CHANNEL_MESSAGE_TODO_APPEND": "{1}: nové úkoly ({2})",
"CHANNEL_MESSAGE_TODO_DONE": "{1}: změněné úkoly ({2})",
"CHANNEL_MESSAGE_VIDEO": "{1}: video",
"CHANNEL_MESSAGE_VIDEOS": "{1}: videa ({2})",
"CHAT_ADD_MEMBER": "{1} přidává {3} do skupiny {2}",
"CHAT_ADD_YOU": "{1} tě přidává do skupiny {2}",
"CHAT_CREATED": "{1} tě přidává do skupiny {2}",
"CHAT_DELETE_MEMBER": "{1} odebírá {3} ze skupiny {2}",
"CHAT_DELETE_YOU": "{1} tě odebírá ze skupiny {2}",
"CHAT_JOINED": "{1} je nově ve skupině {2}",
"CHAT_LEFT": "{1} opouští skupinu {2}",
"CHAT_MESSAGES": "{1} @ {2}: album",
"CHAT_MESSAGE_AUDIO": "{1} @ {2}: hlasová zpráva",
"CHAT_MESSAGE_CONTACT": "{1} @ {2}: kontakt {3}",
"CHAT_MESSAGE_DOC": "{1} @ {2}: soubor",
"CHAT_MESSAGE_DOCS": "{1} @ {2}: soubory ({3})",
"CHAT_MESSAGE_FWDS": "{1} @ {2}: přeposlané zprávy ({3})",
"CHAT_MESSAGE_GAME": "{1} zve skupinu {2} ke hře {3}",
"CHAT_MESSAGE_GAME_SCORE": "{1} @ {2}: skóre {4} ve hře {3}",
"CHAT_MESSAGE_GEO": "{1} @ {2}: poloha",
"CHAT_MESSAGE_GEOLIVE": "{1} @ {2}: živá poloha",
"CHAT_MESSAGE_GIF": "{1} @ {2}: GIF",
"CHAT_MESSAGE_GIVEAWAY": "{1} @ {2}: soutěž o {3}× Premium na {4} měs.",
"CHAT_MESSAGE_GIVEAWAY_STARS": "{1} @ {2}: soutěž o {4} hvězd ({3})",
"CHAT_MESSAGE_INVOICE": "{1} @ {2}: faktura za {3}",
"CHAT_MESSAGE_NOTEXT": "{1} @ {2}: zpráva",
"CHAT_MESSAGE_PAID_MEDIA": "{1} @ {2}: placený příspěvek za {3} hvězd",
"CHAT_MESSAGE_PHOTO": "{1} @ {2}: fotka",
"CHAT_MESSAGE_PHOTOS": "{1} @ {2}: fotky ({3})",
"CHAT_MESSAGE_PLAYLIST": "{1} @ {2}: hudba ({3})",
"CHAT_MESSAGE_POLL": "{1} @ {2}: anketa {3}",
"CHAT_MESSAGE_QUIZ": "{1} @ {2}: kvíz {3}",
"CHAT_MESSAGE_ROUND": "{1} @ {2}: videozpráva",
"CHAT_MESSAGE_STICKER": "{1} @ {2}: samolepka {3}",
"CHAT_MESSAGE_STORY": "{1} sdílí příběh do skupiny",
"CHAT_MESSAGE_TEXT": "{1} @ {2}: {3}",
"CHAT_MESSAGE_TODO": "{1} @ {2}: seznam úkolů {3}",
"CHAT_MESSAGE_TODO_APPEND": "{1} @ {2}: nové úkoly ({3})",
"CHAT_MESSAGE_TODO_DONE": "{1} @ {2}: změněné úkoly ({3})",
"CHAT_MESSAGE_VIDEO": "{1} @ {2}: video",
"CHAT_MESSAGE_VIDEOS": "{1} @ {2}: videa ({3})",
"CHAT_PHOTO_EDITED": "{1} mění fotku skupiny {2}",
"CHAT_REACT_AUDIO": "{1} @ {2}: {3} na tvou hlasovou zprávu",
"CHAT_REACT_CONTACT": "{1} @ {2}: {3} na tvůj kontakt {4}",
"CHAT_REACT_DOC": "{1} @ {2}: {3} na tvůj soubor",
"CHAT_REACT_GAME": "{1} @ {2}: {3} na tvou hru",
"CHAT_REACT_GEO": "{1} @ {2}: {3} na tvou polohu",
"CHAT_REACT_GEOLIVE": "{1} @ {2}: {3} na tvou živou polohu",
"CHAT_REACT_GIF": "{1} @ {2}: {3} na tvůj GIF",
"CHAT_REACT_GIVEAWAY": "{1} @ {2}: {3} na tvou soutěž",
"CHAT_REACT_INVOICE": "{1} @ {2}: {3} na tvou fakturu",
"CHAT_REACT_NOTEXT": "{1} @ {2}: {3} na tvou zprávu",
"CHAT_REACT_PAID_MEDIA": "{1} @ {2}: {3} na tvůj placený příspěvek za {4} hvězd",
"CHAT_REACT_PHOTO": "{1} @ {2}: {3} na tvou fotku",
"CHAT_REACT_POLL": "{1} @ {2}: {3} na tvou anketu {4}",
"CHAT_REACT_QUIZ": "{1} @ {2}: {3} na tvůj kvíz {4}",
"CHAT_REACT_ROUND": "{1} @ {2}: {3} na tvou videozprávu",
"CHAT_REACT_STICKER": "{1} @ {2}: {3} na tvou samolepku {4}",
"CHAT_REACT_TEXT": "{1} @ {2}: {3} na „{4}“",
"CHAT_REACT_TODO": "{1} @ {2}: {3} na tvůj seznam úkolů {4}",
"CHAT_REACT_VIDEO": "{1} @ {2}: {3} na tvé video",
"CHAT_REQ_JOINED": "{2}: {1} je přijat(a) do skupiny",
"CHAT_RETURNED": "{1} se vrací do skupiny {2}",
"CHAT_TITLE_EDITED": "{1} přejmenovává skupinu na {2}",
"CHAT_VOICECHAT_END": "{1} končí hlasový chat ve skupině {2}",
"CHAT_VOICECHAT_INVITE": "{1} zve {3} do hlasového chatu ve skupině {2}",
"CHAT_VOICECHAT_INVITE_YOU": "{1} tě zve do hlasového chatu ve skupině {2}",
"CHAT_VOICECHAT_START": "{1} zahajuje hlasový chat ve skupině {2}",
"CONF_CALL_MISSED": "Zmeškaný hovor: {1}",
"CONTACT_JOINED": "{1} je nově na Telegramu",
"DC_UPDATE": "Otevři tuhle notifikaci pro aktualizaci nastavení",
"ENCRYPTED_MESSAGE": "Máš novou zprávu",
"ENCRYPTION_ACCEPT": "Máš novou zprávu",
"ENCRYPTION_REQUEST": "Máš novou zprávu",
"LOCKED_MESSAGE": "Máš novou zprávu",
"MESSAGES": "{1}: album",
"MESSAGE_AUDIO": "{1}: hlasová zpráva",
"MESSAGE_CONTACT": "{1}: kontakt {2}",
"MESSAGE_DOC": "{1}: soubor",
"MESSAGE_DOCS": "{1}: soubory ({2})",
"MESSAGE_FWDS": "{1}: přeposlané zprávy ({2})",
"MESSAGE_GAME": "{1} tě zve ke hře {2}",
"MESSAGE_GAME_SCORE": "{1}: skóre {3} ve hře {2}",
"MESSAGE_GEO": "{1}: poloha",
"MESSAGE_GEOLIVE": "{1}: živá poloha",
"MESSAGE_GIF": "{1}: GIF",
"MESSAGE_GIFTCODE": "{1}: dárkový kód na Telegram Premium ({2})",
"MESSAGE_GIVEAWAY": "{1}: soutěž o {2}× Premium na {3} měs.",
"MESSAGE_GIVEAWAY_STARS": "{1}: soutěž o {3} hvězd ({2})",
"MESSAGE_INVOICE": "{1}: faktura za {2}",
"MESSAGE_NOTEXT": "{1}: zpráva",
"MESSAGE_PAID_MEDIA": "{1}: placený příspěvek za {2} hvězd",
"MESSAGE_PHOTO": "{1}: fotka",
"MESSAGE_PHOTOS": "{1}: fotky ({2})",
"MESSAGE_PHOTO_SECRET": "{1}: mizející fotka",
"MESSAGE_PLAYLIST": "{1}: hudba ({2})",
"MESSAGE_POLL": "{1}: anketa {2}",
"MESSAGE_QUIZ": "{1}: kvíz {2}",
"MESSAGE_RECURRING_PAY": "{1} ti strhává {2}",
"MESSAGE_ROUND": "{1}: videozpráva",
"MESSAGE_SAME_WALLPAPER": "{1} nastavuje stejnou tapetu chatu",
"MESSAGE_SCREENSHOT": "{1} pořizuje snímek obrazovky",
"MESSAGE_STARGIFT": "{1}: dárek v hodnotě {2} hvězd",
"MESSAGE_STARGIFT_UNPACK_UPGRADE": "{1} rozbaluje dárek, který jsi pomohl(a) vylepšit",
"MESSAGE_STARGIFT_UPGRADE": "{1} vylepšuje tvůj dárek",
"MESSAGE_STICKER": "{1}: samolepka {2}",
"MESSAGE_STORY": "{1} s tebou sdílí příběh",
"MESSAGE_STORY_MENTION": "{1} tě zmiňuje v příběhu",
"MESSAGE_SUGGEST_BIRTHDAY": "{1} ti navrhuje datum narozenin",
"MESSAGE_TEXT": "{1}: {2}",
"MESSAGE_TODO": "{1}: seznam úkolů {2}",
"MESSAGE_UNIQUE_STARGIFT": "{1} ti předává dárek",
"MESSAGE_VIDEO": "{1}: video",
"MESSAGE_VIDEOS": "{1}: videa ({2})",
"MESSAGE_VIDEO_SECRET": "{1}: mizející video",
"MESSAGE_WALLPAPER": "{1} nastavuje novou tapetu chatu",
"OAUTH_REQUEST": "Žádost o přihlášení k {1} z {2}",
"PHONE_CALL_MISSED": "Zmeškaný hovor: {1}",
"PHONE_CALL_REQUEST": "{1} ti volá",
"PINNED_AUDIO": "{1} připíná hlasovou zprávu",
"PINNED_CONTACT": "{1} připíná kontakt {2}",
"PINNED_DOC": "{1} připíná soubor",
"PINNED_GAME": "{1} připíná hru",
"PINNED_GAME_SCORE": "{1} připíná skóre ve hře",
"PINNED_GEO": "{1} připíná polohu",
"PINNED_GEOLIVE": "{1} připíná živou polohu",
"PINNED_GIF": "{1} připíná GIF",
"PINNED_GIVEAWAY": "{1} připíná soutěž",
"PINNED_INVOICE": "{1} připíná fakturu",
"PINNED_NOTEXT": "{1} připíná zprávu",
"PINNED_PAID_MEDIA": "{1} připíná placený příspěvek za {2} hvězd",
"PINNED_PHOTO": "{1} připíná fotku",
"PINNED_POLL": "{1} připíná anketu {2}",
"PINNED_QUIZ": "{1} připíná kvíz {2}",
"PINNED_ROUND": "{1} připíná videozprávu",
"PINNED_STICKER": "{1} připíná samolepku {2}",
"PINNED_TEXT": "{1} připíná „{2}“",
"PINNED_TODO": "{1} připíná seznam úkolů {2}",
"PINNED_VIDEO": "{1} připíná video",
"REACT_AUDIO": "{1}: {2} na tvou hlasovou zprávu",
"REACT_CONTACT": "{1}: {2} na tvůj kontakt {3}",
"REACT_DOC": "{1}: {2} na tvůj soubor",
"REACT_GAME": "{1}: {2} na tvou hru",
"REACT_GEO": "{1}: {2} na tvou polohu",
"REACT_GEOLIVE": "{1}: {2} na tvou živou polohu",
"REACT_GIF": "{1}: {2} na tvůj GIF",
"REACT_GIVEAWAY": "{1}: {2} na tvou soutěž",
"REACT_HIDDEN": "Nová reakce na tvou zprávu",
"REACT_INVOICE": "{1}: {2} na tvou fakturu",
"REACT_NOTEXT": "{1}: {2} na tvou zprávu",
"REACT_PHOTO": "{1}: {2} na tvou fotku",
"REACT_POLL": "{1}: {2} na tvou anketu {3}",
"REACT_QUIZ": "{1}: {2} na tvůj kvíz {3}",
"REACT_ROUND": "{1}: {2} na tvou videozprávu",
"REACT_STICKER": "{1}: {2} na tvou samolepku {3}",
"REACT_STORY": "{1}: {2} na tvůj příběh",
"REACT_STORY_HIDDEN": "Nová reakce na tvůj příběh",
"REACT_TEXT": "{1}: {2} na „{3}“",
"REACT_TODO": "{1}: {2} na tvůj seznam úkolů {3}",
"REACT_VIDEO": "{1}: {2} na tvé video",
"STORY_HIDDEN_AUTHOR": "Nový příběh",
"STORY_LIVE": "zahajuje živé vysílání",
"STORY_NOTEXT": "přidává příběh",
}


def to_ios(template: str) -> str:
    # `{2}|{1}` je u Telegramu oddělovač titulku; bez rozšíření by se ukázal doslova.
    text = template.replace("|", ": ")
    text = text.replace("\\", "\\\\").replace('"', '\\"')
    return re.sub(r"\{(\d)\}", r"%\1$@", text)


def write(path: Path, strings: dict) -> None:
    lines = [
        "/* Vygenerováno iosApp/tools/push_strings.py — neupravovat ručně. */",
        "/* Texty APNs notifikací: iOS je skládá z loc-key a loc-args pushe od Telegramu. */",
        "",
    ]
    lines += [f'"{key}" = "{to_ios(value)}";' for key, value in sorted(strings.items())]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    with urllib.request.urlopen("https://core.telegram.org/api/config.json") as response:
        en = json.load(response)["push"]
    missing = sorted(set(en) - set(CS))
    if missing:
        sys.exit("Chybí česky: " + ", ".join(missing))
    for key, template in en.items():
        if set(re.findall(r"\{(\d)\}", template)) != set(re.findall(r"\{(\d)\}", CS[key])):
            sys.exit(f"Argumenty nesedí: {key}")
    root = Path(__file__).resolve().parent.parent / "iosApp"
    write(root / "en.lproj" / "Localizable.strings", en)
    write(root / "cs.lproj" / "Localizable.strings", {key: CS[key] for key in en})
    print(f"{len(en)} klíčů zapsáno.")


if __name__ == "__main__":
    main()
