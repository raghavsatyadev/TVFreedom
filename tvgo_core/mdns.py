"""A tiny mDNS browser for adb's services, used when `adb mdns` isn't available (Termux's adb build).

Sends one PTR query per service to 224.0.0.251:5353 and reads SRV (port) and A (address) records
from the answers. No dependencies; enough for _adb._tcp, _adb-tls-connect._tcp and _adb-tls-pairing._tcp.
"""
import socket
import struct
import time

SERVICES = {"_adb._tcp.local": "connect", "_adb-tls-connect._tcp.local": "tls",
            "_adb-tls-pairing._tcp.local": "pairing"}
PTR, A, SRV = 12, 1, 33


def _query(name):
    body = b"".join(bytes([len(p)]) + p.encode() for p in name.split(".")) + b"\0"
    return struct.pack(">HHHHHH", 0, 0, 1, 0, 0, 0) + body + struct.pack(">HH", PTR, 1)


def _name(data, off):
    """Read a (possibly compressed) DNS name. Returns (name, offset after it)."""
    labels, end, hops = [], None, 0
    while off < len(data) and hops < 20:
        n = data[off]
        if n == 0:
            off += 1
            break
        if n >= 0xC0:  # pointer
            end = end or off + 2
            off = ((n & 0x3F) << 8) | data[off + 1]
            hops += 1
            continue
        labels.append(data[off + 1:off + 1 + n].decode("utf-8", "replace"))
        off += 1 + n
    return ".".join(labels), end or off


def parse(data):
    """[(type, name, value)] for every record in one mDNS packet (PTR: target, SRV: (port, host), A: ip)."""
    try:
        _id, _flags, qd, an, ns, ar = struct.unpack(">HHHHHH", data[:12])
        off = 12
        for _ in range(qd):
            off = _name(data, off)[1] + 4
        out = []
        for _ in range(an + ns + ar):
            name, off = _name(data, off)
            rtype, _cls, _ttl, rlen = struct.unpack(">HHIH", data[off:off + 10])
            off += 10
            rdata = data[off:off + rlen]
            if rtype == PTR:
                out.append((PTR, name, _name(data, off)[0]))
            elif rtype == SRV and rlen >= 6:
                out.append((SRV, name, (struct.unpack(">H", rdata[4:6])[0], _name(data, off + 6)[0])))
            elif rtype == A and rlen == 4:
                out.append((A, name, socket.inet_ntoa(rdata)))
            off += rlen
        return out
    except (struct.error, IndexError, ValueError):
        return []


def browse(timeout=3.0):
    """[(kind, ip:port, instance name)] like adbio's `adb mdns services` parser."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    s.settimeout(0.3)
    records = []
    try:
        for svc in SERVICES:
            s.sendto(_query(svc), ("224.0.0.251", 5353))
        end = time.time() + timeout
        while time.time() < end:
            try:
                data, (ip, _port) = s.recvfrom(9000)
            except socket.timeout:
                continue
            records += [(t, n, v, ip) for t, n, v in parse(data)]
    except OSError:
        return []
    finally:
        s.close()
    ips = {n: v for t, n, v, _ip in records if t == A}
    found = []
    for t, inst, (port, host), src in [r for r in records if r[0] == SRV]:
        svc = inst.split(".", 1)[1] if "." in inst else ""
        if svc in SERVICES:
            found.append((SERVICES[svc], f"{ips.get(host, src)}:{port}", inst.split(".", 1)[0]))
    return sorted(set(found))
