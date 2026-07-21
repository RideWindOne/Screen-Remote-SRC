#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { chmodSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const skillDir = path.dirname(scriptDir);
const appRepo = path.resolve(skillDir, "../../..");
const hooks = path.join(skillDir, "hooks");
const hook = path.join(hooks, "pre-push");

if (!existsSync(hook) || !existsSync(path.join(appRepo, ".git"))) throw new Error("Screen-Remote repository or pre-push hook not found");
chmodSync(hook, 0o755);

const configure = spawnSync("git", ["config", "--local", "core.hooksPath", hooks], { cwd: appRepo, encoding: "utf8" });
if (configure.status !== 0) throw new Error(configure.stderr.trim() || "Unable to configure core.hooksPath");

console.log(`Installed Screen-Remote hooksPath: ${hooks}`);
