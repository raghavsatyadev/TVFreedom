---
name: code-graph
description: Ask a local code graph (Graphify) instead of searching and opening files to learn how a repo fits together. Use gq query/explain/affected/path before a chain of rg/grep/Read calls when the question is "where does X live", "what uses X", "what breaks if I change X" or "how does A reach B". Built offline from tree-sitter ASTs, rebuilt by gq when code changes; savings show in /lm-savings.
---

# Code graph: ask the map, not the files

`gq` (in `~/.code-graph/`, Windows `%USERPROFILE%\.code-graph\`; run it with bash) wraps
[Graphify](https://github.com/Graphify-Labs/graphify). The first call in a repo builds
`graphify-out/graph.json` at the git top level: code only, offline, no LLM, about 5 s for 250 files.
Later calls rebuild it (incremental) when a source file is newer than the graph. `graphify-out/` goes into
`.git/info/exclude`, so it is never committed and the repo's `.gitignore` is not changed.

## When to use it

| Question | Command |
| --- | --- |
| Where does a feature live, how is it wired | `gq query "how does restore undo a run"` |
| One class or function and its neighbours | `gq explain "HomeOps"` |
| What uses X, before you change or rename it | `gq affected "Undo"` |
| How A reaches B | `gq path "MainActivity" "Palette"` |
| The hubs of an unknown repo | `gq god-nodes` |

- Use it **first** for structure questions in a repo you have not read in this session, instead of 3+
  rg/Read calls. Then open only the files and lines it names.
- An exact identifier or error string → plain `rg` (one call, no graph needed).
- An ambiguous name exits 3 and lists node ids: retry with the id.
- `query` output is capped at ~2000 tokens; narrow the question rather than raising `--budget`.

## Limits: confirm callers with rg

The graph is a map, not proof. Calls through an object are often missing, for example Kotlin
`storage.restoreOptions(info)` gets no edge to `Storage.restoreOptions`, so `gq affected` can say
"No affected nodes" when there are callers. Before a rename or delete, confirm with
`rg -n "\bname\("`.

## Savings

Each `gq` question adds a row (`graphify`) to `~/.local-model/usage.log`, so `/lm-savings` (local-model skill)
shows calls and saved tokens next to lm-ask and lm-gate. The estimate: bytes of the source files the answer
points at (first 5, `GQ_FILES`), as if they had been searched and opened, minus the answer's own bytes, at 4
bytes a token. It is an upper-end estimate: an agent without the graph might open fewer files.

## Setup

`bash global_skills/install.sh code-graph` installs Graphify (`uv tool install graphifyy`, Python 3.10+) and
copies `gq`. Graphify's own `/graphify` skill and hooks are not installed: they add a semantic LLM pass over
docs that costs tokens.
