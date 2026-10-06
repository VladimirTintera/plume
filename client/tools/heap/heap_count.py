#!/usr/bin/env python3
"""Spočítá instance tříd v (hprof-conv převedeném) HPROF souboru. Výstup: počet<TAB>třída."""
import struct, sys, collections

def main(path):
    data = open(path, 'rb').read()
    end = data.index(b'\0')
    pos = end + 1
    ids = struct.unpack('>I', data[pos:pos+4])[0]; pos += 4 + 8
    idfmt = '>Q' if ids == 8 else '>I'
    def rid(p): return struct.unpack(idfmt, data[p:p+ids])[0]
    sizes = {2: ids, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
    strings = {}
    classname = {}
    counts = collections.Counter()
    arrcounts = collections.Counter()
    n = len(data)
    while pos < n:
        tag = data[pos]; length = struct.unpack('>I', data[pos+5:pos+9])[0]; pos += 9
        body = pos; pos += length
        if tag == 0x01:
            strings[rid(body)] = data[body+ids:body+length].decode('utf-8', 'replace')
        elif tag == 0x02:
            classname[rid(body+4)] = rid(body+8+ids)
        elif tag in (0x0C, 0x1C):
            p = body; e = body + length
            while p < e:
                st = data[p]; p += 1
                if st == 0xFF: p += ids
                elif st == 0x01: p += 2*ids
                elif st == 0x02: p += ids + 8
                elif st == 0x03: p += ids + 8
                elif st == 0x04: p += ids + 4
                elif st == 0x05: p += ids
                elif st == 0x06: p += ids + 4
                elif st == 0x07: p += ids
                elif st == 0x08: p += ids + 8
                elif st == 0x20:
                    p += ids + 4 + 6*ids + 4
                    cp = struct.unpack('>H', data[p:p+2])[0]; p += 2
                    for _ in range(cp):
                        t = data[p+2]; p += 3 + sizes[t]
                    ns = struct.unpack('>H', data[p:p+2])[0]; p += 2
                    for _ in range(ns):
                        t = data[p+ids]; p += ids + 1 + sizes[t]
                    ni = struct.unpack('>H', data[p:p+2])[0]; p += 2 + ni*(ids+1)
                elif st == 0x21:
                    cls = rid(p+ids+4)
                    nb = struct.unpack('>I', data[p+2*ids+4:p+2*ids+8])[0]
                    counts[cls] += 1
                    p += 2*ids + 8 + nb
                elif st == 0x22:
                    nel = struct.unpack('>I', data[p+ids+4:p+ids+8])[0]
                    arrcounts[rid(p+ids+8)] += 1
                    p += 2*ids + 8 + nel*ids
                elif st == 0x23:
                    nel = struct.unpack('>I', data[p+ids+4:p+ids+8])[0]
                    t = data[p+ids+8]
                    p += ids + 9 + nel*sizes[t]
                else:
                    raise SystemExit('neznámý subtag %x na %d' % (st, p))
    for cls, c in counts.items():
        name = strings.get(classname.get(cls), '?%x' % cls)
        print('%d\t%s' % (c, name))

main(sys.argv[1])
