---
name: screen-remote-engineering
description: Subrepository-scoped engineering workflow for the Screen Remote Android repository. Use only for work in the Screen-Remote Git subrepository, including understanding, feature implementation, behavior-preserving simplification, review, debugging, Kotlin, Compose, Gradle, CMake, ADB, scrcpy, media, session, external integration, and human-initiated pushes. Routes context narrowly, keeps changes incremental, preserves user edits, syncs affected outer wiki knowledge from the outgoing app range and latest committed dadb change, generates app commit messages, and enforces project validation and scrcpy socket-order constraints.
---

# Screen Remote Engineering

Work from the repository's actual boundaries and load only the context needed for the task.

## Start every task

1. This skill runs from the `Screen-Remote/` subrepository root. Read the Android rules at
   `AGENTS.md`, then use `../AGENTS.md` only for aggregate-repository routing and Git boundaries.
2. Run:

   ```bash
   sed -n '1,320p' AGENTS.md
   sed -n '1,240p' ../AGENTS.md
   node .agents/skills/screen-remote-engineering/scripts/project_probe.mjs
   ```

3. Treat the outer repository, `Screen-Remote/` app submodule, `external/dadb/`, and
   `external/wiki-android/` as separate Git worktrees. Preserve existing changes in all of them.
4. State the detected scope before reading implementation files.
5. Do not recursively read `../external/`, all of `../external/wiki-android/`, or entire large UI
   files. Follow the routing below.

## Select the workflow

- **Understand or onboard:** Read `references/project-map.md`, then inspect one end-to-end path or
  one package boundary. Report facts, uncertainties, and likely next files. Do not edit.
- **Plan a new feature or significant change:** Read `references/project-map.md` and
  `references/change-workflow.md`. Define behavior, ownership, boundaries, acceptance checks, and
  slices before editing. Keep the spec in the conversation unless the user requests a file or the
  work spans multiple sessions.
- **Implement or fix:** Read `references/change-workflow.md` and `references/verification.md`. Make
  one coherent slice, run the narrowest meaningful check, then continue.
- **Simplify or remove code:** Read `references/change-workflow.md`. Lock observable behavior with
  existing or focused regression tests, remove obsolete paths completely, and avoid compatibility
  shims or parallel implementations.
- **Review:** Read `references/change-workflow.md`. Review only the detected diff plus the directly
  affected contracts and tests. Findings must include evidence, consequence, and a concrete remedy.
- **Handle a human push from this subrepository:** Read `references/wiki-sync.md`. Review the exact
  remote-SHA-to-local-SHA committed app range supplied by the hook and the single locked
  `../external/dadb/` `HEAD^..HEAD` commit, update affected Chinese/English canonical page pairs in
  the outer-root `../external/wiki-android/` GitHub Wiki repository, and generate an app commit
  message from the app range. Never create wiki pages inside this repository, or rebase, amend,
  commit, or push from this workflow.
- **Touch ADB, scrcpy, socket, session runtime, codec, decoder, controller, or transport code:**
  Also read `references/connection-safety.md` before editing or reviewing. These rules are
  mandatory.

## Context routing

- Read `references/project-map.md` for ownership, layer placement, entry points, or unfamiliar code.
- Read `references/connection-safety.md` only for the remote-control runtime path.
- Read `references/change-workflow.md` for multi-file changes, refactors, removals, specs, and
  reviews.
- Read `references/verification.md` whenever code or build configuration changes.
- Read `references/wiki-sync.md` only when invoked by the `Screen-Remote/` pre-push hook or when
  maintaining that workflow.
- Prefer build files and current source/tests over wiki statements when versions or behavior
  disagree. Note the discrepancy instead of silently copying stale documentation.

## Non-negotiable project rules

- Do not preserve old versions, old data shapes, old storage paths, or historical behavior unless
  the user explicitly changes this rule.
- Establish scrcpy channels sequentially in protocol order: `video`, optional `audio`, then
  `control`. Never create them concurrently.
- Keep configuration state separate from runtime state, and device capability separate from user
  preference.
- Keep UI out of transport/protocol orchestration. Keep low-level infrastructure independent of
  feature UI.
- Reuse the project's design-system components and module-specific i18n text objects.
- Keep detailed Android documentation in `../external/wiki-android/`. The aggregate root owns its
  public README, release material, media, and cross-platform contracts; do not change those from an
  Android-only task unless explicitly requested.
- Avoid speculative layers such as empty `UseCase`, `Repository`, `Manager`, or `Facade` wrappers.
- Do not modify external submodules unless the requested behavior genuinely belongs upstream or at
  the dependency boundary.
- Do not commit, stage, push, or update submodule pointers unless the user asks.
- During the `Screen-Remote/` pre-push workflow, analyze only the committed outgoing app range and
  the locked latest dadb commit. Ignore uncommitted app and dadb changes and do not compare dadb
  against a remote branch. Use dadb only as dependency evidence for wiki accuracy; do not place
  unrelated dadb-only work in the app commit message. Do not rewrite history or perform Git writes
  outside the wiki.

## Change discipline

1. Identify the single source of truth and the smallest ownership boundary.
2. Prefer a vertical or risk-first slice that remains compilable.
3. Add or update focused tests for rules, parsing, state transitions, protocol ordering, and
   regressions.
4. Run targeted tests first; broaden verification according to risk.
5. Re-read the diff for accidental scope growth, stale compatibility code, duplicate facts, and
   unrelated formatting.
6. Stop and explain when device-only behavior cannot be verified locally.

## Completion report

Report:

- behavior changed or preserved;
- files and architectural boundary affected;
- checks run and their results;
- device/manual verification still needed;
- pre-existing dirty files that were left untouched.
