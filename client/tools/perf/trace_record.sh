#!/bin/zsh
# Záznam Perfetto trace DEBUG buildu Plume s composition tracingem (ANDROID_SERIAL).
#
#   client/tools/perf/trace_record.sh <adresář> <jméno> <ms> ['příkaz scénáře']
#
# Zapne v aplikaci tracing (broadcast pro androidx.tracing.perfetto, jen debug build s
# runtime-tracing), spustí záznam na <ms>, mezitím pustí scénář (adb input, Bot API…) a stáhne
# <adresář>/<jméno>.pftrace. Rozbor: trace_analyze.py. Postup viz docs/poznatky/vykon.md, „Výkon a paměť".
T=${0:A:h}; OUT=${1:?adresář}; N=${2:?jméno}; D=${3:?ms}; shift 3
mkdir -p $OUT
adb shell am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING \
    eu.tintera.plume/androidx.tracing.perfetto.TracingReceiver | grep -oE 'exitCode":[0-9]+'
sed "s/DURATION/$D/" $T/trace.cfg | adb shell perfetto -c - --txt \
    -o /data/misc/perfetto-traces/$N.pftrace --background-wait >/dev/null
sleep 1
[ $# -gt 0 ] && eval "$@"
sleep $(( D / 1000 + 2 ))
adb pull /data/misc/perfetto-traces/$N.pftrace $OUT/$N.pftrace >/dev/null
adb shell rm /data/misc/perfetto-traces/$N.pftrace
echo $OUT/$N.pftrace
