# Global skills

Skills that belong in each developer's **global** skill folders, not in this repo's
`.agents/skills/`: they work in every project (for example `local-model`). No agent loads this folder
directly. It is the copy that new team members install from.

Install or update all of them. On Windows use Git Bash: from cmd or PowerShell, plain `bash` can
be WSL's, which fails; run `"C:/Program Files/Git/bin/bash.exe" global_skills/install.sh`.

```bash
bash global_skills/install.sh            # all skills
bash global_skills/install.sh local-model   # one skill
```

It copies each skill folder to:

| Agent | Global folder |
| --- | --- |
| Claude Code | `~/.claude/skills/<skill>/` |
| Gemini CLI / Antigravity | `~/.gemini/skills/<skill>/` |
| Codex (ChatGPT) and other agents | `~/.agents/skills/<skill>/` |

A skill with its own `install.sh` runs it after the copy (`local-model`: commands in `~/.local-model/`,
SessionStart/SessionEnd hooks, the output gate, loop stop, request size hint and the pre-review diff
checks in `~/.claude/settings.json`; `headless-emulator`: `emu` in `~/.headless-emu/`; `code-graph`:
Graphify via uv and `gq` in `~/.code-graph/`). Restart the agents afterwards.

| Skill | What it does |
| --- | --- |
| `local-model` | Local Ollama model for verdicts, code search and diff checks; `/lm-savings` |
| `headless-emulator` | Android emulators with no window, driven from a browser tab or adb; idle pause and stop |
| `code-graph` | Graphify code graph: callers, impact and paths without opening files; savings in `/lm-savings` |

To add a skill: put its folder here (`<skill>/SKILL.md`, plus an optional `install.sh`), run the
installer, and do not also keep a copy in `.agents/skills/`.
