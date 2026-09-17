# Pravidla pro R8. Většinu si nesou knihovny samy:
#
#  - `tdl-coroutines` dodává v AAR `proguard.txt`, který drží JNI hranici
#    (`org.drinkless.tdlib.JsonClient` + jeho native metody a callback na logy). Tohle je ta
#    nejrizikovější část projektu a je ošetřená upstream — nekopírovat sem, jinak by se to
#    při bumpu wrapperu rozešlo.
#  - kotlinx.serialization, DataStore, Coil a Firebase svoje pravidla přibalují taky.
#
# Sem patří JEN to, co se ověřeně rozbilo na zařízení. Preventivní `-keep` pravidla škodí:
# vypnou zkracování tam, kde nemusí, a zakonzervují chybu, která se mezitím opravila jinde.

# Čitelné stack trace z release buildu. Bez tohohle je hlášení pádu od testera k ničemu —
# obfuskované názvy tříd se zpětně přeloží jen s mapping souborem, ale čísla řádků
# se bez těchhle atributů do APK vůbec nedostanou.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
