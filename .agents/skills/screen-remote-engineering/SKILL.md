---
name: screen-remote-engineering
description: Subrepository-scoped engineering workflow for the Screen Remote Android repository. Use only for work in the Screen-Remote Git subrepository, including understanding, feature implementation, behavior-preserving simplification, review, debugging, Kotlin, Compose, Gradle, CMake, ADB, scrcpy, media, session, external integration, and human-initiated pushes. Routes context narrowly, keeps changes incremental, preserves user edits, syncs affected outer wiki knowledge, generates commit messages from outgoing code, and enforces project validation and scrcpy socket-order constraints.
---

# Screen Remote Engineering

Work from the repository's actual boundaries and load only the context needed for the task.

## Start every task

1. Read the nearest `AGENTS.md`.
2. Run:

   ```bash
   node .agents/skills/screen-remote-engineering/scripts/project_probe.mjs
   ```

3. Treat the outer repository and `Screen-Remote/` app submodule as separate Git worktrees. Preserve existing changes in both.
4. State the detected scope before reading implementation files.
5. Do not recursively read `../external/`, all of `../external/wiki/`, or entire large UI files. Follow the routing below.

## Select the workflow

- **Understand or onboard:** Read `references/project-map.md`, then inspect one end-to-end path or one package boundary. Report facts, uncertainties, and likely next files. Do not edit.
- **Plan a new feature or significant change:** Read `references/project-map.md` and `references/change-workflow.md`. Define behavior, ownership, boundaries, acceptance checks, and slices before editing. Keep the spec in the conversation unless the user requests a file or the work spans multiple sessions.
- **Implement or fix:** Read `references/change-workflow.md` and `references/verification.md`. Make one coherent slice, run the narrowest meaningful check, then continue.
- **Simplify or remove code:** Read `references/change-workflow.md`. Lock observable behavior with existing or focused regression tests, remove obsolete paths completely, and avoid compatibility shims or parallel implementations.
- **Review:** Read `references/change-workflow.md`. Review only the detected diff plus the directly affected contracts and tests. Findings must include evidence, consequence, and a concrete remedy.
- **Handle a human push from this subrepository:** Read `references/wiki-sync.md`. Review the exact remote-SHA-to-local-SHA committed range supplied by the hook, update affected Chinese/English canonical page pairs in the outer-root `../external/wiki/` GitHub Wiki repository, and generate a commit message from the code. Never create wiki pages inside this repository, or rebase, amend, commit, or push from this workflow.
- **Touch ADB, scrcpy, socket, session runtime, codec, decoder, controller, or transport code:** Also read `references/connection-safety.md` before editing or reviewing. These rules are mandatory.

## Context routing

- Read `references/project-map.md` for ownership, layer placement, entry points, or unfamiliar code.
- Read `references/connection-safety.md` only for the remote-control runtime path.
- Read `references/change-workflow.md` for multi-file changes, refactors, removals, specs, and reviews.
- Read `references/verification.md` whenever code or build configuration changes.
- Read `references/wiki-sync.md` only when invoked by the `Screen-Remote/` pre-push hook or when maintaining that workflow.
- Prefer build files and current source/tests over wiki statements when versions or behavior disagree. Note the discrepancy instead of silently copying stale documentation.

## Non-negotiable project rules

- Do not preserve old versions, old data shapes, old storage paths, or historical behavior unless the user explicitly changes this rule.
- Establish scrcpy channels sequentially in protocol order: `video`, optional `audio`, then `control`. Never create them concurrently.
- Keep configuration state separate from runtime state, and device capability separate from user preference.
- Keep UI out of transport/protocol orchestration. Keep low-level infrastructure independent of feature UI.
- Reuse the project's design-system components and module-specific i18n text objects.
- Avoid speculative layers such as empty `UseCase`, `Repository`, `Manager`, or `Facade` wrappers.
- Do not modify external submodules unless the requested behavior genuinely belongs upstream or at the dependency boundary.
- Do not commit, stage, push, or update submodule pointers unless the user asks.
- During the `Screen-Remote/` pre-push workflow, analyze committed outgoing code only. Do not include uncommitted app changes, rewrite history, or perform Git writes. Update wiki files when required and return a generated message plus the wiki decision to the hook.

## Change discipline

1. Identify the single source of truth and the smallest ownership boundary.
2. Prefer a vertical or risk-first slice that remains compilable.
3. Add or update focused tests for rules, parsing, state transitions, protocol ordering, and regressions.
4. Run targeted tests first; broaden verification according to risk.
5. Re-read the diff for accidental scope growth, stale compatibility code, duplicate facts, and unrelated formatting.
6. Stop and explain when device-only behavior cannot be verified locally.

## Completion report

Report:

- behavior changed or preserved;
- files and architectural boundary affected;
- checks run and their results;
- device/manual verification still needed;
- pre-existing dirty files that were left untouched.
