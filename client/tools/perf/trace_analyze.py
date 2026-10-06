#!/usr/bin/env python3
"""Rozbor trace z trace_record.sh: snímky aplikace a nejčastěji skládané composables Plume.

    trace_analyze.py záznam.pftrace [kolik] [filtr]

Potřebuje Python balíček `perfetto` (pip install perfetto; trace_processor si stáhne sám).
Snímky = `Choreographer#doFrame` aplikace; `Recomposer:animation` bez skládání znamená, že si
o snímky říká animace nebo smyčka s `withFrameNanos`. Nula snímků v nečinnosti je cíl.
"""
import sys
from perfetto.trace_processor import TraceProcessor

tp = TraceProcessor(trace=sys.argv[1])
lim = int(sys.argv[2]) if len(sys.argv) > 2 else 25
flt = sys.argv[3] if len(sys.argv) > 3 else ''

def one(q):
    return list(tp.query(q))[0]

rows = list(tp.query("select upid from process where name = 'eu.tintera.plume'"))
if not rows:
    print('aplikace v trace nic nedělala (0 snímků)')
    sys.exit()
pid = rows[0].upid
base = f"from slice s join thread_track tt on s.track_id = tt.id join thread t using(utid) where t.upid = {pid}"
frames = one(f"select count(*) c {base} and s.name like 'Choreographer#doFrame%' and s.depth = 0").c
recompose = one(f"select count(*) c {base} and s.name = 'Recomposer:recompose'").c
animation = one(f"select count(*) c {base} and s.name = 'Recomposer:animation'").c
ours = one(f"select count(*) c {base} and s.name like 'eu.tintera.plume.%'").c
length = one("select (max(ts + dur) - min(ts)) / 1e9 d from slice").d
print(f"trvání {length:.1f} s  snímky {frames}  Recomposer:recompose {recompose}  "
      f"animation {animation}  naše composables {ours}")
for r in tp.query(f"select s.name name, count(*) n, sum(s.dur) / 1e6 ms {base} "
                  f"and s.name like 'eu.tintera.plume.%{flt}%' group by s.name order by n desc limit {lim}"):
    print(f"{r.n:6d} {r.ms:8.1f} ms  {r.name.replace('eu.tintera.plume.', '')[:140]}")
