#!/usr/bin/env python3
"""Nejkratší SILNÁ cesta od GC kořene k instancím třídy. Použití: heap_path.py dump.hprof Třída [max]"""
import struct, sys, collections

path, target = sys.argv[1], sys.argv[2]
limit = int(sys.argv[3]) if len(sys.argv) > 3 else 5
data = open(path, 'rb').read()
pos = data.index(b'\0') + 1
ids = struct.unpack('>I', data[pos:pos+4])[0]; pos += 12
idfmt = '>Q' if ids == 8 else '>I'
U = struct.Struct(idfmt).unpack_from
sizes = {2: ids, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
strings, cname = {}, {}
classes = {}      # id -> (super, [(name,type)], [static ref ids with names])
inst = {}         # id -> (cls, off, len)
arrs = {}         # id -> (off, n)
roots = []        # (id, kind)
ROOTK = {0xFF: 'unknown', 0x01: 'jni-global', 0x02: 'jni-local', 0x03: 'java-frame', 0x04: 'native-stack',
         0x05: 'sticky-class', 0x06: 'thread-block', 0x07: 'monitor', 0x08: 'thread'}
n = len(data)
while pos < n:
    tag = data[pos]; length = struct.unpack_from('>I', data, pos+5)[0]; pos += 9
    body = pos; pos += length
    if tag == 0x01:
        strings[U(data, body)[0]] = data[body+ids:body+length].decode('utf-8', 'replace')
    elif tag == 0x02:
        cname[U(data, body+4)[0]] = U(data, body+8+ids)[0]
    elif tag in (0x0C, 0x1C):
        p = body; e = body + length
        while p < e:
            st = data[p]; p += 1
            if st in ROOTK:
                roots.append((U(data, p)[0], ROOTK[st]))
                p += {0xFF: ids, 0x01: 2*ids, 0x02: ids+8, 0x03: ids+8, 0x04: ids+4, 0x05: ids,
                      0x06: ids+4, 0x07: ids, 0x08: ids+8}[st]
            elif st == 0x20:
                cid = U(data, p)[0]; sup = U(data, p+ids+4)[0]
                p += ids + 4 + 6*ids + 4
                cp = struct.unpack_from('>H', data, p)[0]; p += 2
                for _ in range(cp):
                    t = data[p+2]; p += 3 + sizes[t]
                ns = struct.unpack_from('>H', data, p)[0]; p += 2
                statics = []
                for _ in range(ns):
                    nm = U(data, p)[0]; t = data[p+ids]
                    if t == 2: statics.append((nm, U(data, p+ids+1)[0]))
                    p += ids + 1 + sizes[t]
                ni = struct.unpack_from('>H', data, p)[0]; p += 2
                fields = []
                for _ in range(ni):
                    fields.append((U(data, p)[0], data[p+ids])); p += ids + 1
                classes[cid] = (sup, fields, statics)
            elif st == 0x21:
                oid = U(data, p)[0]; cls = U(data, p+ids+4)[0]
                nb = struct.unpack_from('>I', data, p+2*ids+4)[0]
                inst[oid] = (cls, p+2*ids+8, nb); p += 2*ids + 8 + nb
            elif st == 0x22:
                oid = U(data, p)[0]; nel = struct.unpack_from('>I', data, p+ids+4)[0]
                arrs[oid] = (p+2*ids+8, nel); p += 2*ids + 8 + nel*ids
            elif st == 0x23:
                nel = struct.unpack_from('>I', data, p+ids+4)[0]; t = data[p+ids+8]
                p += ids + 9 + nel*sizes[t]
            else:
                raise SystemExit('subtag %x' % st)

def name(cid): return strings.get(cname.get(cid), '?')
layout_cache = {}
def layout(cid):
    if cid in layout_cache: return layout_cache[cid]
    out = []; c = cid
    while c and c in classes:
        out.append(classes[c][1]); c = classes[c][0]
    layout_cache[cid] = out
    return out
refcls = {c for c in classes if name(c) in ('java.lang.ref.Reference',)}
def is_ref(cid):
    c = cid
    while c and c in classes:
        if c in refcls: return True
        c = classes[c][0]
    return False

def edges(oid):
    if oid in inst:
        cls, off, nb = inst[oid]; weak = is_ref(cls); p = off
        for flds in layout(cls):
            for nm, t in flds:
                if t == 2:
                    v = U(data, p)[0]
                    fname = strings.get(nm, '?')
                    if v and not (weak and fname == 'referent'):
                        yield fname, v
                p += sizes[t]
    elif oid in arrs:
        off, nel = arrs[oid]
        for i in range(nel):
            v = U(data, off + i*ids)[0]
            if v: yield '[%d]' % i, v
    elif oid in classes:
        for nm, v in classes[oid][2]:
            if v: yield 'static ' + strings.get(nm, '?'), v

parent = {}
dq = collections.deque()
for rid, kind in roots:
    if rid not in parent:
        parent[rid] = (None, 'ROOT ' + kind); dq.append(rid)
while dq:
    o = dq.popleft()
    for f, v in edges(o):
        if v not in parent:
            parent[v] = (o, f); dq.append(v)

def label(o):
    if o in inst: return name(inst[o][0])
    if o in arrs: return 'Object[]'
    if o in classes: return 'class ' + name(o)
    return '?'
targets = [o for o, (c, _, _) in inst.items() if name(c) == target]
print('%d instancí %s, %d dosažitelných silně' % (len(targets), target, sum(1 for t in targets if t in parent)))
for t in targets[:limit]:
    if t not in parent: print('  nedosažitelná (jen slabě / čeká na GC)'); continue
    chain = []; o = t
    while o is not None:
        par, f = parent[o]; chain.append((label(o), f)); o = par
    print('--- cesta k 0x%x' % t)
    for lbl, f in reversed(chain):
        print('   %s   <- %s' % (lbl, f))
