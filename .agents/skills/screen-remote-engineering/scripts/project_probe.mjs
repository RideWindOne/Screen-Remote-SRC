#!/usr/bin/env node

// Read-only probe for the Screen-Remote subrepository and its outer project context.

import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";

function run(cwd, args) {
  const result = spawnSync(args[0], args.slice(1), { cwd, encoding: "utf8" });
  return [result.status ?? 1, `${result.stdout ?? ""}${result.stderr ?? ""}`.trim()];
}

function findRoot(start) {
  let candidate = path.resolve(start);
  while (true) {
    if (
      existsSync(path.join(candidate, "AGENTS.md")) &&
      existsSync(path.join(candidate, "Screen-Remote", "settings.gradle.kts"))
    ) {
      return candidate;
    }
    const parent = path.dirname(candidate);
    if (parent === candidate) throw new Error("Screen Remote repository root not found");
    candidate = parent;
  }
}

function changedFiles(repo, base) {
  const probes = [
    ["staged", ["git", "diff", "--cached", "--name-only"]],
    ["unstaged", ["git", "diff", "--name-only"]],
  ];
  if (base) probes.push([`${base}...HEAD`, ["git", "diff", "--name-only", `${base}...HEAD`]]);
  for (const [label, command] of probes) {
    const [code, output] = run(repo, command);
    const files = output.split("\n").filter(Boolean);
    if (code === 0 && files.length) return [label, files];
  }
  return ["clean working diff", []];
}

function categories(files) {
  const rules = new Map([
    ["connection-runtime", ["infrastructure/scrcpy", "infrastructure/adb", "infrastructure/media", "service/Scrcpy"]],
    ["ui-feature", ["/feature/", "core/designsystem", "core/i18n"]],
    ["state-data", ["core/data", "core/domain", "viewmodel", "/session/"]],
    ["tests", ["src/test/", "src/androidTest/"]],
    ["build-native", ["build.gradle", "settings.gradle", "gradle/", "src/main/cpp/", "CMakeLists.txt"]],
    ["manifest-resources", ["AndroidManifest.xml", "src/main/res/"]],
  ]);
  const found = [...rules].filter(([, needles]) => files.some((file) => needles.some((needle) => file.includes(needle)))).map(([name]) => name);
  return found.length ? found : files.length ? ["other"] : [];
}

function printStatus(label, repo) {
  const [code, output] = run(repo, ["git", "status", "--short"]);
  console.log(code === 0 ? `${label}:` : `${label}: unavailable`);
  console.log(output || "  clean");
}

const args = process.argv.slice(2);
const baseIndex = args.indexOf("--base");
const rootIndex = args.indexOf("--root");
const base = baseIndex >= 0 ? args[baseIndex + 1] : undefined;
const root = findRoot(rootIndex >= 0 ? args[rootIndex + 1] : process.cwd());
const app = path.join(root, "Screen-Remote");
const dadb = path.join(root, "external", "dadb");
const [scope, files] = changedFiles(app, base);
const kinds = categories(files);

console.log("Screen Remote project probe");
console.log(`root: ${root}`);
console.log(`app repo: ${app}`);
console.log(`scope: ${scope} (${files.length} files)`);
for (const file of files.slice(0, 30)) console.log(`  ${file}`);
if (files.length > 30) console.log(`  ... ${files.length - 30} more`);
console.log(`categories: ${kinds.join(", ") || "none"}`);

const docs = ["../external/wiki/Module-Map-and-Boundaries.md"];
if (kinds.includes("connection-runtime")) docs.push("../external/wiki/Runtime-Main-Path.md", "../external/wiki/Session-Configuration-and-Connection-Lifecycle.md");
if (kinds.includes("ui-feature")) docs.push("../external/wiki/Engineering-and-Verification-Rules.md");
console.log("suggested context:");
for (const doc of [...new Set(docs)]) console.log(`  ${doc}`);

console.log("suggested verification:");
if (kinds.includes("connection-runtime")) console.log("  ./gradlew testDebugUnitTest --tests '*ConnectionSocketOrderTest'");
if (files.some((file) => !file.startsWith("app/src/test/"))) {
  console.log("  ./gradlew testDebugUnitTest");
  console.log("  ./gradlew assembleDebug");
} else if (files.length) {
  console.log("  ./gradlew testDebugUnitTest");
} else {
  console.log("  choose checks after a task scope is defined");
}

printStatus("outer status", root);
printStatus("app status", app);
printStatus("dadb status", dadb);
const [dadbHeadCode, dadbHead] = run(dadb, ["git", "log", "-1", "--format=%H %s"]);
console.log(dadbHeadCode === 0 ? `dadb latest commit: ${dadbHead}` : "dadb latest commit: unavailable");
