"""Golden files for the app's unit tests: what tvfreedom.py's guard and planner decide for data/packages-*.txt.

python tvfreedom-android/tools/make_golden.py   (from the repo root; rerun after changing guard.py, plan.py or lists/)
Writes tvfreedom-android/composeApp/src/androidHostTest/resources/golden/. The Kotlin tests must match these exactly.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, ROOT)

from tvfreedom_core import agent, guard, plan  # noqa: E402

OUT = os.path.join(ROOT, "tvfreedom-android", "composeApp", "src", "androidHostTest", "resources", "golden")


def read(name):
    return open(os.path.join(ROOT, "data", name), encoding="utf-8").read()


def facts():
    paths = dict((m[1], m[0]) for m in re.findall(r"^package:(.+)=([^=\s]+)\r?$", read("packages-all.txt"), re.M))
    names = lambda n: {l.strip() for l in read(n).splitlines() if l.strip()}  # noqa: E731
    return {"paths": paths, "disabled": names("packages-disabled.txt"), "user": names("packages-user.txt"),
            "home": {"com.google.android.tvlauncher"}, "uids": {"com.android.phone": 1001}}


INFO = {"host": "x", "brand": "Xiaomi", "device": "moor0", "android": "11", "sdk": "30", "serial": "s",
        "home": "", "ime": "com.google.android.inputmethod.latin/.LatinIME"}


def fake_agent(verdict):
    """Stands in for agent.classify: every asked package gets `verdict`, phase 2, a reason with a newline."""
    def classify(info, items, examples, keep, cmd=None, batch=None):
        return {i["pkg"]: (verdict, 2, "fake agent") for i in items}
    return classify


def summary(p):
    return {"remove": [{k: r[k] for k in ("pkg", "action", "phase", "reason", "source")} for r in p["remove"]],
            "kept": p["kept"], "conflicts": [c["pkg"] for c in p["conflicts"]], "already_off": p["already_off"]}


def main():
    os.makedirs(OUT, exist_ok=True)
    f = facts()
    keep = guard.user_keep()
    with open(os.path.join(OUT, "guard.tsv"), "w", encoding="utf-8", newline="\n") as fh:
        for pkg, apk in sorted(f["paths"].items()):
            why = guard.keep_reason(pkg, apk, f["home"], INFO["ime"], keep, f["uids"].get(pkg))
            fh.write(f"{pkg}\t{why}\n")
    cases = {"mitv-keep": ("MiTV-MOOR0", "keep"), "newtv-keep": ("Brand New TV", "keep"),
             "newtv-remove": ("Brand New TV", "remove"), "mibox4-remove": ("MIBOX4", "remove")}
    for name, (model, verdict) in cases.items():
        agent.classify = fake_agent(verdict)
        plan.agent.classify = agent.classify
        p = plan.build(dict(INFO, model=model), f, agent_cmd="fake")
        with open(os.path.join(OUT, f"plan-{name}.json"), "w", encoding="utf-8", newline="\n") as fh:
            json.dump(summary(p), fh, indent=1, sort_keys=True)
    print(f"golden files in {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
