---
trigger: always_on
description: Commit and push finished, checked work to main; never force-push or rewrite pushed history.
---

# RULE: BRANCHES & PUSHING

This is a one-owner repo. The owner wants finished work committed and pushed without being asked.

1. **Finished and checked → commit and push.** When the change builds, `scripts/ci-local.sh`
   passes and (for app changes) it was tried on the phone, commit and push to `origin/main`.
   Working in a worktree branch: push the branch, then fast-forward `main`
   (`git push origin HEAD:main`). Do not ask first.
2. **Never force-push**, never rewrite pushed history, never delete remote branches you did not make.
3. **Ask first** before anything that changes a real TV, downloads files the task did not name,
   or touches the signing keystore.
4. **Commit messages:** Conventional Commits (`feat(tvfreedom): …`, `fix: …`, `docs: …`). Never commit
   `secret.properties`, `local.properties`, keystores or tokens.
5. **Pull requests** only when the user asks for one: `gh pr create --base main --body-file tmp/pr_body.md`
   (never inline `--body`). Never merge or approve a PR yourself.
