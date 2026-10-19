"""The repo's per-model lists as known-good examples.

lists/phaseN.txt is the Xiaomi Mi TV (MiTV-MOOR0); lists/<model>/phaseN.txt is any other model, named by
a slug of ro.product.model (lists/mibox4/ for MIBOX4). New models get their plan saved the same way.
"""
import glob
import os
import re

import tv

LISTS = os.path.join(tv.ROOT, "lists")
ROOT_MODEL = "mitv-moor0"  # the model whose lists sit directly in lists/


def slug(model):
    return re.sub(r"[^a-z0-9]+", "-", model.lower()).strip("-") or "unknown"


def model_dir(model):
    s = slug(model)
    return LISTS if s == ROOT_MODEL else os.path.join(LISTS, s)


def read_entries(path):
    """[(action, pkg, comment)] from a list file."""
    out = []
    for line in open(path, encoding="utf-8"):
        body, _, comment = line.partition("#")
        parts = body.split()
        if len(parts) == 2 and parts[0] in ("disable", "uninstall"):
            out.append((parts[0], parts[1], comment.strip()))
    return out


def phase_of(path):
    m = re.search(r"phase(\d+)", os.path.basename(path))
    return int(m.group(1)) if m else 3  # applied.txt from a run: phase unknown, treat as vendor extras


def phase_files(d):
    return sorted(glob.glob(os.path.join(d, "phase*.txt")))


def learned_files(d):
    """What earlier tvgo runs actually applied to this model (and you confirmed): reused next time."""
    return sorted(glob.glob(os.path.join(d, "ai-*", "applied.txt")))


def model_files(d):
    return phase_files(d) + learned_files(d)


def all_models():
    """{model slug: [phase files]} for every model that has lists."""
    models = {ROOT_MODEL: model_files(LISTS)}
    for d in sorted(glob.glob(os.path.join(LISTS, "*", ""))):
        if os.path.basename(os.path.dirname(d)).startswith("ai-"):
            continue
        files = model_files(d)
        if files:
            models[os.path.basename(os.path.dirname(d))] = files
    return models


def examples():
    """{pkg: (action, phase, comment, model)} across every model's lists."""
    ex = {}
    for model, files in all_models().items():
        for f in files:
            for action, pkg, comment in read_entries(f):
                ex.setdefault(pkg, (action, phase_of(f), comment, model))
    return ex


def for_model(model):
    """{pkg: (action, phase, comment)} from this model's own lists (empty for a new model)."""
    own = {}
    for f in all_models().get(slug(model), []):
        for action, pkg, comment in read_entries(f):
            own[pkg] = (action, phase_of(f), comment)
    return own
