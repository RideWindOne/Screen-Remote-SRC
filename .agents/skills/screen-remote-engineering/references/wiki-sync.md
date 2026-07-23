# Screen-Remote Human Pre-push Workflow

Use this workflow only when the locally installed Git `pre-push` hook is triggered by a human push from this Screen-Remote subrepository.

## Contract

- Treat the remote SHA and local SHA supplied by Git as the source of truth.
- Treat the dadb SHA captured by the hook as the source of truth for exactly one dependency commit: `<dadb-sha>^..<dadb-sha>`.
- Analyze committed content only; ignore app and dadb working-tree changes and do not substitute a dadb remote comparison.
- A push without the marker may contain multiple outgoing commits. Analyze their combined committed result so the developer can squash them afterward.
- Never run `rebase`, `commit`, `commit --amend`, `reset`, `push`, or any other history-changing Git command.
- Update files only inside the outer-root `external/wiki/` GitHub Wiki repository when documentation is materially affected. Never create wiki pages inside `Screen-Remote/`.
- Return one commit message derived from the final code, not from the existing commit message.

Ignore uncommitted app and dadb changes. The hook reads the app outgoing range plus one locked dadb commit.

## Read the outgoing change

The hook prompt provides exact `<remote-sha>..<local-sha>` values. From `Screen-Remote/` inspect:

```bash
git diff --stat <remote-sha> <local-sha>
git diff --name-status <remote-sha> <local-sha>
git diff --find-renames <remote-sha> <local-sha> -- <selected paths>
git show --format=fuller --stat <local-sha>
```

Use names and changed hunks to choose context. Read owning contracts and nearby tests where necessary; do not load every changed file in full. Do not trust the existing commit subject/body as the summary source.

Load `connection-safety.md` when the range touches ADB, scrcpy, sockets, session runtime, controller, codec, decoder, or media.

The hook also provides an exact `<dadb-sha>`. Inspect only that commit from `../external/dadb/`:

```bash
git -C ../external/dadb diff --stat <dadb-sha>^ <dadb-sha>
git -C ../external/dadb diff --name-status <dadb-sha>^ <dadb-sha>
git -C ../external/dadb diff --find-renames <dadb-sha>^ <dadb-sha> -- <selected paths>
git -C ../external/dadb show --format=fuller --stat <dadb-sha>
```

Use the dadb commit to verify dependency behavior, helper protocols, Android support, and integration boundaries documented by the wiki. Do not summarize unrelated dadb-only work in the Screen Remote app commit message.

## Update bilingual canonical wiki knowledge

Read `../external/wiki/Documentation-Maintenance-Conventions.md` and use `../external/wiki/Technical-Documentation-Index.md` to locate existing canonical pages.

For every affected Chinese page, update or create its complete English counterpart in the same pass. Preserve established readable filenames and use the technical index and existing reciprocal links to resolve the pair; for a new page without an established mapping, `<name>.md` and `<name>-EN.md` are acceptable. Put reciprocal language links near the top of both pages. The English page must carry the same current knowledge, not merely an abbreviated summary. Include both paths in `wiki_pages`.

Update wiki when the outgoing code changes a documented behavior, boundary, workflow, configuration, dependency fact, operational constraint, troubleshooting signal, or user-visible capability.

Typical routing:

- package ownership or boundaries → module map and directory/boundary rules
- session/runtime/events → runtime main path and session state/event pages
- ADB, USB, mDNS, pairing, transport → ADB topic and matching guide
- server, sockets, control, reconnect → remote lifecycle, connection lifecycle, or socket/control pages
- codec, metadata, decoder, audio/video → encoding/decoding topic and matching analysis/guide
- management features → management topic and user docs when behavior is visible
- Compose/design-system/i18n → UI design, bilingual design, or engineering rules
- SDK/NDK/CMake/server/dependency changes → build entry, upgrade record, or recent key updates
- logging/diagnostics → logging topic, maintenance guide, or signal dictionary

Do not update wiki for formatting-only changes, internal renames with no documented effect, tests that only reinforce an existing invariant, or behavior-preserving simplification that leaves current knowledge true.

When no edit is necessary, return a concrete reason tied to the reviewed diff.

## Wiki editing rules

- Maintain Chinese and English page pairs and describe current truth rather than a chronological patch narrative.
- Replace obsolete facts instead of retaining compatibility descriptions.
- Prefer stable conclusions and paths over large code excerpts.
- Keep user instructions in user pages and engineering detail in developer pages.
- Update recent key updates only for durable facts future maintainers need.
- Do not commit wiki changes. The hook pauses so the developer can review and commit them.

## Commit-message marker gate

On every branch `git push`, first inspect the tip commit message. If it contains the exact `Screen-Remote-Review: confirmed` trailer, allow the push immediately without any additional checks.

Whenever the trailer is absent, analyze the complete outgoing range, update wiki when needed, generate the message, append the trailer, and reject that push. This rule does not depend on whether the push is the first, second, or a later attempt. The developer may then rebase/squash the outgoing history and edit the generated subject/body as desired while retaining the trailer.

The marker alone is the confirmation. Do not impose commit-count, code-tree, cache, or wiki-status checks after it is present. The developer only runs ordinary `git push`; this workflow has no optional mode parameters.

Before launching Codex, persist the exact remote base SHA and local SHA. If a later push finds the same pair still marked `running`, direct the developer to resume it instead of starting another review. A nonzero Codex exit must not discard a complete valid result.

Run the Codex review as a persistent Wiki-scoped session. Store its exact session ID with the reviewed SHA pair under the subrepository Git directory while the context is `running`. If the terminal is interrupted, keep the partial Wiki changes and resume that exact session with the outer-root `make wiki-resume` command. The resume path must reuse prior conversation and tool context, complete the structured result, and never run `git push` itself. Clear `review-context.json` after a valid message is generated successfully.

The subrepository `pre-commit` hook must reject commits whenever `review-context.json` is non-empty. Missing or empty context means there is no interrupted review and must not block a commit. This prevents history changes before an interrupted Wiki review has been resumed and completed.

## Generate the app commit message

Derive the message from observable app-range changes and tests, not from the old message or unrelated dadb-only work.

Use this format:

```text
<English subject, imperative, at most 72 characters>

- <major behavior or architecture change>
- <important correctness or cleanup detail>
- <tests or verification added, when material>

Screen-Remote-Review: confirmed
```

Write both the subject and body in English. Use a single subject without Markdown headings or version numbers unless the code change is specifically a release/version update. Keep the body concrete and omit low-value file-by-file narration. The developer may edit the subject and body, but the final commit must retain the exact trailer so the next push can proceed.

Do not return `Screen-Remote-Review: confirmed` inside `commit_subject` or `commit_body`. The hook owns that trailer and appends it exactly once when writing the message file.

## Return structured output

Return the schema requested by the hook:

- `commit_subject`
- `commit_body`
- `wiki_action`: `updated`, `no_update`, or `blocked`
- `wiki_pages`
- `wiki_reason`
- `change_summary`

Use `blocked` when the range cannot be understood safely or required source objects/files are unavailable. The hook will reject the push.
