# Štípnutí přes adb (`Pinch.java`)

`adb shell input` umí jen jeden prst, takže štípnutí (velikost písma v konverzaci, přiblížení
v prohlížeči) se jím ověřit nedá. `Pinch` vstříkne dvouprstové gesto stejnou cestou jako
scrcpy: běží přes `app_process` pod uživatelem `shell`, který smí `injectInputEvent`. Root
ani instrumentace nejsou potřeba, funguje i na fyzickém telefonu.

```bash
SDK=~/Library/Android/sdk
javac --release 11 -cp $SDK/platforms/android-36/android.jar -d /tmp/pinch Pinch.java
$SDK/build-tools/36.1.0/d8 --min-api 30 --output /tmp/pinch /tmp/pinch/Pinch.class
adb push /tmp/pinch/classes.dex /data/local/tmp/pinch.dex
# střed x y, rozestup prstů z → na (px), doba pohybu (ms), [držení před puštěním (ms)]
adb shell "CLASSPATH=/data/local/tmp/pinch.dex app_process / Pinch 540 1150 300 450 500 1500"
adb shell rm /data/local/tmp/pinch.dex
```

- **Od Androidu 14 je instance v `InputManagerGlobal`**; `InputManager.getInstance()` bez
  kontextu spadne na `NullPointerException` (proces pak hlásí jen „Killed", chyba je v logcatu).
- **Prsty jsou nad sebou** (svisle kolem středu). Snímek během držení ukáže stav uprostřed gesta.
- **Gesto se v aplikaci počítá až od chvíle, kdy ho pozná** (vzdálenost prstů se změní o víc
  než `touchSlop`), takže poměr je o kousek menší, než by vyšel z čísel na příkazové řádce.
