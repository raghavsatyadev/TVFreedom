"""Build the removal plan: facts from the TV -> guard -> this model's known lists -> agent for the rest.

Same rules as the TVFreedom app (core/Planner.kt): phases 4 and 5 of a model's lists (home screen and
screensaver) are never planned here or shown to the agent as examples. Turning off the TV's own launcher
without installing a new one leaves the TV with no home screen; the app's home-screen setup swaps them safely.

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


HOME_OWNED = "home screen: phase 4 or 5 swaps it safely"
APP_HINT = ("This tool does phases 1-3. For phases 4-6 (a new home screen, the screensaver, company branding"
            " and extras), use the TVFreedom app on your phone.")


def build(info, f, agent_cmd=None, uninstall_user_apps=False, stop_updates=False):
    """Returns plan dict: remove [{pkg, action, phase, reason, source}], kept, conflicts, already_off, user_apps.

    stop_updates lets this model's own list turn off what the guard keeps as OTA (firmware updates), nothing else.
    """
    keep = guard.user_keep()
    own_all = known.for_model(info["model"])
    all_examples = known.examples()
    own = {k: v for k, v in own_all.items() if v[1] in agent.PHASES}
    examples = {k: v for k, v in all_examples.items() if v[1] in agent.PHASES}
    home_owned = {k for k, v in list(own_all.items()) + list(all_examples.items()) if v[1] not in agent.PHASES}
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
        if why and not (why == "OTA" and stop_updates and pkg in own):
            kept[pkg] = why
            if pkg in examples:
                conflicts.append({"pkg": pkg, "kept_as": why,
                                  "earlier": f"{examples[pkg][0]} on {examples[pkg][3]}"})
            continue
        if pkg in home_owned:
            kept[pkg] = HOME_OWNED
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
            "already_off": already_off, "unanswered": [i["pkg"] for i in ask if i["pkg"] not in answers],
            "user_apps": sorted(f["user"])}


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
    removed = {r["pkg"] for r in rm}
    stay = [p for p in plan.get("user_apps", []) if p not in removed and p not in plan["already_off"]]
    if stay:
        print(f"\nYour apps that stay ({len(stay)}): {', '.join(stay)}")
    if plan["conflicts"]:
        print("\nKept by the safety guard although an earlier list removed them:")
        for c in plan["conflicts"]:
            print(f"  {c['pkg']}  ({c['kept_as']}; earlier: {c['earlier']})")
    if plan["unanswered"]:
        print(f"\nAgent gave no answer for {len(plan['unanswered'])} packages: kept.")
    print("\n" + APP_HINT)
    home = sorted(p for p, why in plan["kept"].items() if why == HOME_OWNED)
    if home:
        print(f"  The app swaps these safely; kept here: {', '.join(home)}")
