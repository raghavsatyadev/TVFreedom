"""Find Android TVs on the Wi-Fi: adb's mDNS (_adb._tcp, _adb-tls-connect, _adb-tls-pairing) plus a 5555 scan.

mDNS may come back empty on a phone (Termux can't hold a Wi-Fi multicast lock; LinXR's NAT drops
multicast), so the /24 scan and a typed ip:port are first-class paths, not fallbacks.
"""
import ipaddress
import re
import socket
from concurrent.futures import ThreadPoolExecutor

from . import adbio


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))  # no packet is sent; picks the Wi-Fi interface
        return s.getsockname()[0]
    except OSError:
        return ""
    finally:
        s.close()


def mdns():
    """[(kind, ip:port, name)] from `adb mdns services`; kind is connect, tls or pairing."""
    out = adbio.adb("mdns", "services", timeout=15)
    found = []
    for name, svc, addr in re.findall(r"^(\S+)\s+(_adb[\w-]*\._tcp)\.?\s+(\d+\.\d+\.\d+\.\d+:\d+)",
                                      out, re.M):
        kind = {"_adb._tcp": "connect", "_adb-tls-connect._tcp": "tls",
                "_adb-tls-pairing._tcp": "pairing"}.get(svc, svc)
        found.append((kind, addr, name))
    return found


def _open(ip, port, timeout=0.4):
    try:
        with socket.create_connection((ip, port), timeout=timeout):
            return True
    except OSError:
        return False


def scan(subnet=None, port=5555):
    """ip:port of every host on the /24 (or `subnet`) with `port` open."""
    me = local_ip()
    if not subnet and not me:
        print("No Wi-Fi address found: pass --subnet 192.168.1.0/24 or the TV's ip:port.")
        return []
    net = ipaddress.ip_network(subnet or f"{me}/24", strict=False)
    hosts = [str(h) for h in net.hosts() if str(h) != me]
    with ThreadPoolExecutor(64) as pool:
        hits = pool.map(lambda h: (h, _open(h, port)), hosts)
        return [f"{h}:{port}" for h, ok in hits if ok]


def find(subnet=None):
    """Candidate adb endpoints (deduplicated by ip, connect/tls preferred) and pairing endpoints."""
    me = local_ip()
    found, pairing = {}, []
    for kind, addr, name in mdns():
        ip = addr.split(":")[0]
        if ip == me:
            continue
        if kind == "pairing":
            pairing.append(addr)
        else:
            found.setdefault(ip, (addr, f"mDNS {kind} {name}"))
    for addr in scan(subnet):
        found.setdefault(addr.split(":")[0], (addr, "port 5555 open"))
    return list(found.values()), pairing


def identify_tvs(candidates):
    """Connect to each candidate and keep only TVs (leanback). Returns identify() dicts."""
    tvs = []
    for addr, why in candidates:
        print(f"trying {addr} ({why})")
        if not adbio.connect(addr, wait_s=45, wake=False):
            continue
        if not adbio.is_tv(addr):
            print(f"  {addr} is not a TV, skipped")
            adbio.adb("disconnect", addr)
            continue
        adbio.sh(addr, "input keyevent KEYCODE_WAKEUP")
        info = adbio.identify(addr)
        print(f"  TV: {info['brand']} {info['model']} ({info['device']}), Android {info['android']}")
        tvs.append(info)
    return tvs


def relocate(serial, subnet=None):
    """After a reboot a wireless-debugging port can change: find the TV with this serial again."""
    cands, _ = find(subnet)
    for addr, _why in cands:
        if adbio.connect(addr, wait_s=20, wake=False) and adbio.getprop(addr, "ro.serialno") == serial:
            return addr
    return ""
