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

# LiteRT-LM (Gemma v zařízení) si keep pravidla NEPŘIBALUJE — AAR 0.11.0 nemá `proguard.txt`.
# Nativní strana přitom sahá do Kotlinu podle JMÉNA: čte gettery konfigurace
# (`SamplerConfig.getTopK()`), vyhazuje `LiteRtLmJniException` a volá zpátky do callbacků.
# Ověřeno na emulátoru: bez tohohle release build při prvním použití Gemmy spadne na
# `NoSuchMethodError … SamplerConfig.getTopK()` a SIGABRT (a GPU chyba přijde jako
# `ClassNotFoundException`). Celý balíček, ne výčet tříd — JNI hranici určuje knihovna, ne my,
# a výčet by se při bumpu tiše rozešel. Až knihovna pravidla přibalí, tohle jde pryč.
-keep class com.google.ai.edge.litertlm.** { *; }

# Runtime modelu v zařízení (:client:ai-litert) leží v součásti z Google Play a základ aplikace
# ho vytváří REFLEXÍ podle jména (`LITERT_ENGINE_FACTORY`, LiteRtRuntime.kt v :client:ai-core) — přímý
# odkaz na něj by se nepřeložil. R8 zpracovává základ i součást dohromady, takže pravidlo patří
# sem; bez něj by třídu přejmenoval nebo zahodil a model by po stažení hlásil chybu.
-keep class eu.tintera.plume.ai.litert.LiteRtEngineFactory { public <init>(); }
-keep class eu.tintera.plume.ai.llamacpp.LlamaCppEngineFactory { public <init>(); }

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
