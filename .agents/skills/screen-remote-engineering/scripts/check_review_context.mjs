#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

function git(cwd, ...args) {
  const result = spawnSync("git", args, {
    cwd,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
  if (result.status !== 0) throw new Error(result.stderr.trim() || `git ${args.join(" ")} failed`);
  return result.stdout.trim();
}

function main() {
  const appRepo = path.resolve(git(process.cwd(), "rev-parse", "--show-toplevel"));
  const outer = path.dirname(appRepo);
  const contextPath = path.resolve(appRepo, git(appRepo, "rev-parse", "--git-path", "codex-pre-push/review-context.json"));
  if (!existsSync(contextPath)) return 0;

  const raw = readFileSync(contextPath, "utf8").trim();
  if (!raw) return 0;

  let context = null;
  try {
    context = JSON.parse(raw);
  } catch {
    // A non-empty unreadable context is unsafe to ignore.
  }

  console.error("commit 已暂停：上一次 pre-push Wiki 审读尚未成功收尾。");
  if (context?.session_id) console.error(`Codex session：${context.session_id}`);
  if (context?.base_sha && context?.local_sha) {
    console.error(`审读范围：${context.base_sha.slice(0, 12)}..${context.local_sha.slice(0, 12)}`);
  }
  console.error(`状态文件：${contextPath}`);
  if (context?.status === "running" && context?.session_id) {
    console.error(`请先运行：make -C ${outer} wiki-resume`);
  } else {
    console.error("状态文件非空但无法恢复；请先检查该文件和 Wiki 工作区。");
  }
  return 1;
}

try {
  process.exitCode = main();
} catch (error) {
  console.error(`commit 已暂停：${error.message}`);
  process.exitCode = 1;
}
