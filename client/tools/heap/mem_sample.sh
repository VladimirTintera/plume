#!/bin/zsh
# Jeden řádek paměti DEBUG buildu Plume po vynuceném GC (ANDROID_SERIAL):
#
#   client/tools/heap/mem_sample.sh <štítek>
#   → 12:30:15 <štítek> alloc=69011 rss=82336 java=27328 pss=273581   (vše v kB)
#
# alloc = „Heap Alloc" nativní haldy (co malloc opravdu drží), rss = její RSS, java = Java
# heap (PSS), pss = celý proces. Na růst native heapu se dívá `alloc`: PSS i RSS kolísají
# podle toho, co zrovna systém odložil do zramu. Postup delšího běhu viz CLAUDE.md,
# „Výkon a paměť".
PKG=eu.tintera.plume; PID=$(adb shell pidof $PKG)
[ -z "$PID" ] && { echo "$PKG neběží"; exit 1; }
for i in 1 2 3; do adb shell run-as $PKG kill -10 $PID; sleep 1.5; done
M=$(adb shell dumpsys meminfo $PKG)
NA=$(echo "$M" | awk '/^ *Native Heap /{print $(NF-1)}' | head -1)
NR=$(echo "$M" | awk '/^ *Native Heap /{print $7}' | head -1)
JH=$(echo "$M" | awk '/Java Heap:/{print $3}')
TP=$(echo "$M" | awk '/TOTAL PSS:/{print $3}')
echo "$(date +%H:%M:%S) $1 alloc=$NA rss=$NR java=$JH pss=$TP"
