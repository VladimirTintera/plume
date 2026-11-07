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

# Model v zařízení (knihovna ai-engine, external/ai-engine): keep pravidla pro LiteRT-LM (JNI
# sahá do Kotlinu podle jména) a pro továrny runtime, které základ vytváří REFLEXÍ ze součásti
# z Google Play, si nese knihovna sama jako consumer rules (:ai-litert, :ai-core). Platí i pro
# :ai-litert v součásti litertFeature — R8 zpracovává základ i součásti dohromady, s pravidly
# jejich závislostí (ověřeno v `seeds.txt` release buildu). Sem je nekopírovat, rozešla by se.

# Strukturovaný výstup Gemini Nano (core/ai, `OnDeviceAi.generateStructured`): odpověď do typu
# plní Gson přibalený v ML Kitu REFLEXÍ podle jmen polí, která jsou zároveň schématem pro model.
# Ověřeno v dexu release buildu: bez tohohle pole `replies` v `ReplySuggestionsOutput` zmizí úplně
# a návrhy by vyšly prázdné. **`-keepclassmembers … { <fields>; }` NESTAČÍ**: instanci staví jen
# Gson (bez konstruktoru), R8 v plném režimu třídu bere jako nikdy nevytvořenou, getter udělá
# abstraktní a pole zahodí i přes pravidlo (`usage.txt`). Drží se proto i konstruktor — tím je
# třída „vytvořitelná" a pole zůstane pod svým jménem. Provider samotný pravidlo nepotřebuje:
# R8 ho drží přes `META-INF/services` a soubor přejmenuje spolu s rozhraním. Nový typ odpovědi
# = další jméno v seznamu (viz `StructuredOutputProviders.kt`).
-keep class eu.tintera.plume.core.ai.ReplySuggestionsOutput,
            eu.tintera.plume.core.ai.ScamAnswer,
            eu.tintera.plume.core.ai.ToneAnswer,
            eu.tintera.plume.core.ai.TriageAnswer,
            eu.tintera.plume.core.ai.ReminderAnswer,
            eu.tintera.plume.core.ai.EventAnswer,
            eu.tintera.plume.core.ai.SearchAnswer { <init>(...); <fields>; }
