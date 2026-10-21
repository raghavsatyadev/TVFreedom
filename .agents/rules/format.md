---
trigger: glob
globs: "**/*.kt"
description: Kotlin formatting. ktfmt is OFF here (FORMAT_CHECK=0); keep the style of the file you edit.
---

# RULE: KOTLIN FORMATTING

`FORMAT_CHECK=0` in `agent-kit.env`: ktfmt is off for this repo. The code base uses 4-space
indents and lines up to about 150 columns, and a Google-style reformat would push files past the
500-line limit.

- Match the style of the file you edit. Do not reformat lines you did not change.
- Never run a whole-tree reformat unless the user asks for it.
- If the user turns ktfmt on (`FORMAT_CHECK=1`): `bash scripts/ci-local.sh --fix --format-only`,
  never a `ktfmt` from your PATH.
