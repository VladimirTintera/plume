# Pravidla pro R8 na hodinkách. Většinu si nesou knihovny samy (`tdl-coroutines` drží JNI
# hranici TDLib vlastním `proguard.txt` v AAR) — sem patří JEN to, co se ověřeně rozbilo na
# zařízení, stejně jako v client/androidApp/proguard-rules.pro.

# Čitelné stack trace z release buildu: bez těchhle atributů se čísla řádků do APK nedostanou.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
