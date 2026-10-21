"""Hard keep rules. Code, not the agent, has the last word: a guard hit is never removed.

Kept: boot/core, input, network, the remote, Cast, voice, OTA, launchers (HOME) and the setup app,
the current keyboard, plus the user's own keep list (lists/keep.txt).
Brand-agnostic first (system uid, HOME resolvers, install path, name patterns), then known vendor
namespaces (MediaTek/Amlogic/Realtek SoCs, Sony, TCL, Hisense, Philips/TP Vision, Nvidia Shield).
"""
import os
import re

import tv

# (reason, regex on the package name). First match wins.
RULES = [
    ("core", r"^android$|^android\.|\.auto_generated_rro|overlay|^com\.android\.(systemui|settings|shell"
             r"|providers\.|server\.|internal\.|keychain|certinstaller|externalstorage|location"
             r"|packageinstaller|permissioncontroller|statementservice|dynsystem|se$|vndk|apex"
             r"|media(\.|$)|ext\.|devicelockcontroller|sdksandbox|uwb|nfc|phone)"
             r"|^com\.google\.android\.(gms|gsf|packageinstaller|permissioncontroller|ext\.|modulemetadata"
             r"|webview|trichromelibrary|configupdater|partnersetup|tvrecommendations)$"
             r"|^com\.android\.vending$|webview|framework|systemui|provider"),
    ("network", r"wifi|network|ethernet|tethering|connectivity|captiveportal|\.dns|vpn"),
    ("input/remote", r"input|keyboard|\.ime\b|latin|remote|\.rcu|keyhandler|keymap|bluetooth|\.bt\.|pairing"
                     r"|controller|gamepad|\.tv\.remote"),
    ("tv inputs", r"^com\.(google\.)?android\.tv$|tvinput|tv\.input|hdmi|\.cec|livetv|tvservice|^mitv\.service$|tuner|tvsettings"
                  r"|tv\.settings|tvprovider|\.tv\.framework"),
    ("cast", r"cast|mediashell|dial|miracast|airplay|\.dlna"),
    ("voice", r"katniss|voice|assistant|speech|\.tts|texttospeech|hotword|\.asr|mic\b"),
    ("OTA", r"\bota|update|upgrad|firmware|abupdate|recovery|\.fota"),
    ("launcher/setup", r"launcher|setup|wraith|\.home$|tvfreedom"),
    ("vendor HAL", r"^com\.(mediatek|mstar|droidlogic|amlogic|realtek|hisilicon|rockchip|qualcomm|qti"
                   r"|nvidia\.(shield\.remote|tegra|ota|blakepairing)|sony\.dtv\.(tvx|tvinput|hdmi|sonysystem)"
                   r"|tcl\.(tv$|tvinput|common|systemserver)|hisense\.(hiservice|tv$)|tpv\.|philips\.tv\.core)"
                   r"|^org\.droidtv\.(tv|settings|playtv|channels|euinstallersat|tvserver)|^vendor\.|^com\.android\.cts"),
]
_RULES = [(why, re.compile(rx)) for why, rx in RULES]
PROTECTED_PATHS = ("/apex/", "/system/framework/")


def user_keep():
    """Packages the user always keeps (lists/keep.txt; one package per line, # comments)."""
    path = os.path.join(tv.ROOT, "lists", "keep.txt")
    if not os.path.exists(path):
        return set()
    return {l.split("#", 1)[0].strip() for l in open(path, encoding="utf-8")} - {""}


def keep_reason(pkg, apk_path="", home=(), ime="", keep=(), uid=None):
    """Why this package must stay, or '' if the guard allows removing it."""
    if pkg in keep:
        return "your keep list"
    if pkg in home:
        return "launcher/setup (HOME)"
    if ime and ime.split("/")[0] == pkg:
        return "input/remote (current keyboard)"
    if uid is not None and uid < 10000:  # shares a system uid (android.uid.system, phone, bluetooth...)
        return "core (system uid)"
    if apk_path.startswith(PROTECTED_PATHS):
        return "core"
    for why, rx in _RULES:
        if rx.search(pkg):
            return why
    return ""
