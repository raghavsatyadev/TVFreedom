"""Build the removal plan: facts from the TV -> guard -> this model's known lists -> agent for the rest.

The plan is written in the lists/ format (lists/<model>/ai-<date>/phaseN.txt) so tv.py apply/restore
and the restore menu work on it unchanged, plus plan.json with every decision and its reason.
"""
import datetime
import json
import os

from . import adbio, agent, guard, known


def facts(host):
    """Everything the classifier needs, read-only."""
    paths = adbio.packages(host, "-f")
    return {
        "paths": paths,
        "disabled": adbio.packages(host, "-d"),
        "user": adbio.packages(host, "-3"),
        "home": adbio.home_packages(host),
        "uids": adbio.uids(host),
    }


def build(info, f, agent_cmd=None, uninstall_user_apps=False):
    """Returns plan dict: remove [{pkg, action, phase, reason, source}], kept, conflicts, already_off."""
    keep = guard.user_keep()
    own = known.for_model(info["model"])
    examples = known.examples()
    remove, kept, conflicts, ask = [], {}, [], []
    already_off = sorted(p for p in f["paths"] if p in f["disabled"])

    def action_for(pkg, suggested="disable"):
        if pkg in f["user"] and (uninstall_user_apps and suggested == "uninstall"):
            return "uninstall"
        return "disable"  # always reversible with pm enable

    for pkg, apk in sorted(f["paths"].items()):
        if pkg in f["disabled"]:
            continue
        why = guard.keep_reason(pkg, apk, f["home"], info.get("ime", ""), keep, f["uids"].get(pkg))
        if why:
            kept[pkg] = why
            if pkg in examples:
                conflicts.append({"pkg": pkg, "kept_as": why,
                                  "earlier": f"{examples[pkg][0]} on {examples[pkg][3]}"})
            continue
        if pkg in own:
            act, phase, comment = own[pkg]
            remove.append({"pkg": pkg, "action": action_for(pkg, act), "phase": phase,
                           "reason": comment or "in this model's list", "source": "known list"})
            continue
        item = {"pkg": pkg, "apk": apk, "user_app": pkg in f["user"]}
        if pkg in examples:
            a, ph, c, m = examples[pkg]
            item["hint"] = f"{a} phase {ph} on {m}: {c}"
        ask.append(item)

    answers = agent.classify(info, ask, examples, keep, agent_cmd) if ask else {}
    for item in ask:
        pkg = item["pkg"]
        verdict, phase, reason = answers.get(pkg, ("keep", 0, "agent gave no answer"))
        if verdict == "remove":
            remove.append({"pkg": pkg, "action": action_for(pkg, "uninstall"), "phase": phase,
                           "reason": reason, "source": "agent"})
        else:
            kept[pkg] = f"agent: {reason}"
    return {"info": info, "remove": remove, "kept": kept, "conflicts": conflicts,
            "already_off": already_off, "unanswered": [i["pkg"] for i in ask if i["pkg"] not in answers]}


def save(plan, stamp=None):
    """Write phaseN.txt files + plan.json under lists/<model>/ai-<stamp>/. Returns the dir."""
    stamp = stamp or datetime.datetime.now().strftime("%Y-%m-%d-%H%M")
    d = os.path.join(known.model_dir(plan["info"]["model"]), f"ai-{stamp}")
    os.makedirs(d, exist_ok=True)
    info = plan["info"]
    for phase in sorted({r["phase"] for r in plan["remove"]}):
        rows = [r for r in plan["remove"] if r["phase"] == phase]
        with open(os.path.join(d, f"phase{phase}.txt"), "w", encoding="utf-8") as fh:
            fh.write(f"# {info['brand']} {info['model']} ({info['device']}, Android {info['android']})."
                     f" Phase {phase}: {agent.PHASES.get(phase, 'other')}. Planned by tvgo.py.\n"
                     f"# Undo: python tvgo.py restore (or python tv.py restore <this file>)\n")
            for r in rows:
                note = r["reason"] + ("; restore may need the Play Store" if r["action"] == "uninstall" else "")
                fh.write(f"{r['action']:<9} {r['pkg']:<48} # {note} [{r['source']}]\n")
    with open(os.path.join(d, "plan.json"), "w", encoding="utf-8") as fh:
        json.dump(plan, fh, indent=1, sort_keys=True)
    return d


def show(plan):
    """Short, phone-sized summary."""
    info, rm = plan["info"], plan["remove"]
    print(f"\n{info['brand']} {info['model']} at {info['host']}: {len(rm)} to remove,"
          f" {len(plan['kept'])} kept, {len(plan['already_off'])} already off.")
    for phase in sorted({r["phase"] for r in rm}):
        print(f"\nPhase {phase} ({agent.PHASES.get(phase, 'other')}):")
        for r in (r for r in rm if r["phase"] == phase):
            print(f"  {r['action'][:3]} {r['pkg']}  - {r['reason']}")
    if plan["conflicts"]:
        print("\nKept by the safety guard although an earlier list removed them:")
        for c in plan["conflicts"]:
            print(f"  {c['pkg']}  ({c['kept_as']}; earlier: {c['earlier']})")
    if plan["unanswered"]:
        print(f"\nAgent gave no answer for {len(plan['unanswered'])} packages: kept.")
