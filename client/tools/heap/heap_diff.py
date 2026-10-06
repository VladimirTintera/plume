#!/usr/bin/env python3
"""Rozdíl počtu instancí mezi dvěma .counts: heap_diff.py před.counts po.counts [filtr] [kolik]"""
import sys
def load(p):
    d={}
    for l in open(p):
        c,n=l.rstrip('\n').split('\t',1); d[n]=d.get(n,0)+int(c)
    return d
a,b=load(sys.argv[1]),load(sys.argv[2])
rows=[(b.get(k,0)-a.get(k,0),a.get(k,0),b.get(k,0),k) for k in set(a)|set(b)]
flt=sys.argv[3] if len(sys.argv)>3 else ''
rows=[r for r in rows if flt in r[3]]
rows.sort(reverse=True)
for r in rows[:int(sys.argv[4]) if len(sys.argv)>4 else 40]:
    if r[0]>0: print('%+d\t%d→%d\t%s'%r)
