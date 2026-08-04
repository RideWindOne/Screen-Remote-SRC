# Change Workflow

Use this reference for specs, multi-file implementation, behavior-preserving simplification,
deletion/migration, debugging, and review inside the Screen-Remote subrepository.

## Define the scope

Before editing, state:

- requested behavior and what must remain unchanged;
- owning layer and source of truth;
- files or packages likely involved;
- risk level: local, cross-layer, runtime-chain, build/native, or external dependency;
- observable acceptance checks.

If requirements or ownership are ambiguous, resolve that ambiguity before coding. Do not create a
formal spec for a tiny local edit.

## Plan significant work

Write a compact spec before a new feature, architectural boundary, or change that crosses multiple
runtime stages. Include:

1. user-visible behavior;
2. non-goals;
3. state and ownership boundaries;
4. affected contracts and failure paths;
5. verification evidence;
6. ordered implementation slices.

Keep the spec in the conversation by default. Create a repository artifact only when the user
requests it or the task must survive across sessions.

## Implement incrementally

Prefer thin, risk-first slices:

1. lock an existing contract or regression with a focused test;
2. change the smallest owning unit;
3. run the focused test or compile check;
4. inspect the diff for scope growth;
5. continue only after the slice is stable.

Keep the tree compilable. Do not accumulate a large unverified rewrite. Do not commit between slices
unless the user explicitly asks for commits.

## Simplify without changing behavior

Before simplifying:

- identify inputs, outputs, side effects, ordering, cancellation, and error behavior;
- read callers and nearby tests;
- establish which abstraction owns the decision.

Prefer:

- deleting dead branches and migration residue;
- merging duplicate facts into one source of truth;
- naming state transitions and policy decisions;
- extracting a cohesive responsibility from a large file;
- inlining wrappers that add no policy, isolation, or reuse.

Avoid:

- line-count-only rewrites;
- broad renames mixed with behavior changes;
- compatibility shims or dual read/write paths;
- replacing explicit control flow with clever density;
- splitting one readable flow across many low-information files;
- new `Manager`, `Facade`, `Repository`, or `UseCase` types that only forward calls.

Existing tests should normally remain unchanged for a pure simplification. Change a test only when
it asserts an internal shape rather than observable behavior, and explain why.

## Remove deprecated or migrated code

This project does not preserve old versions, old data shapes, old storage paths, or historical
behavior by default.

For a removal:

1. find all readers, writers, entry points, flags, constants, tests, docs, and storage keys;
2. identify the current source of truth;
3. remove the old path end-to-end rather than leaving a dormant branch;
4. remove obsolete tests and add coverage for the surviving path where needed;
5. search again for names and semantic remnants;
6. report any external/device data consequence instead of inventing a migration layer.

## Review a change

Detect scope in this order: staged app diff, unstaged app diff, requested ref/branch diff, then
user-specified files. Include the outer repository only when it has relevant changes.

Review these axes:

1. **Correctness:** state transitions, null/error cases, races, resource ownership, Android
   lifecycle.
2. **Boundary:** correct placement among `core`, `infrastructure`, `feature`, `service`, and `app`.
3. **Single source of truth:** no duplicated configuration, capability, negotiated state, text, or
   constants.
4. **Complexity:** no unnecessary wrapper, indirection, long conditional appendage, or cross-file
   scattering.
5. **Android/Compose:** stable state ownership, lifecycle-aware collection, no heavy work during
   composition, design-system consistency.
6. **Runtime safety:** apply `connection-safety.md` when the remote-control chain is touched.
7. **Tests:** verify behavior and regression risk rather than implementation trivia.
8. **Scope:** reject unrelated formatting, drive-by cleanup, and accidental submodule changes.

Only report actionable findings. For each finding provide:

- exact evidence and location;
- concrete failure or maintenance consequence;
- smallest appropriate remedy;
- severity based on impact, not style preference.

If there are no findings, say so and identify residual verification risk.
