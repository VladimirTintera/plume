#!/bin/zsh
# Heap dump DEBUG buildu Plume na připojeném zařízení / emulátoru (ANDROID_SERIAL).
#
#   client/tools/heap/heap_dump.sh <adresář> <jméno>
#
# Vynutí GC (SIGUSR1 přes run-as, jde jen u debuggable buildu), uloží `dumpsys meminfo`,
# heap dump převede hprof-conv do standardního HPROF a spočítá instance tříd
# (<jméno>.counts: počet<TAB>třída). Dva dumpy se porovnají heap_diff.py, cestu k instanci
# ukáže heap_path.py. Postup viz docs/poznatky/vykon.md, „Výkon a paměť".
set -e
T=${0:A:h}; OUT=${1:?adresář}; NAME=${2:?jméno}; PKG=eu.tintera.plume
HPROF_CONV=${HPROF_CONV:-$HOME/Library/Android/sdk/platform-tools/hprof-conv}
mkdir -p $OUT
PID=$(adb shell pidof $PKG)
for i in 1 2 3; do adb shell run-as $PKG kill -10 $PID; sleep 2; done
adb shell dumpsys meminfo $PKG > $OUT/$NAME.meminfo
adb shell am dumpheap $PID /data/local/tmp/$NAME.hprof
sleep 8
adb pull /data/local/tmp/$NAME.hprof $OUT/$NAME.raw.hprof >/dev/null
adb shell rm /data/local/tmp/$NAME.hprof
$HPROF_CONV $OUT/$NAME.raw.hprof $OUT/$NAME.hprof
rm $OUT/$NAME.raw.hprof
python3 $T/heap_count.py $OUT/$NAME.hprof | sort -t$'\t' -k2 > $OUT/$NAME.counts
grep -E "TOTAL PSS|Java Heap:|Native Heap:|Views:|WebViews" $OUT/$NAME.meminfo
