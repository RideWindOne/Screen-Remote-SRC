#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const ZERO_SHA = "0".repeat(40);
const REVIEW_MARKER = "Screen-Remote-Review: confirmed";

function run(cwd, args, { stream = false } = {}) {
  const result = spawnSync(args[0], args.slice(1), {
    cwd,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    stdio: stream ? "inherit" : ["ignore", "pipe", "pipe"],
  });
  if (stream) return [result.status ?? 1, ""];
  return [result.status ?? 1, `${result.stdout ?? ""}${result.stderr ?? ""}`.trim()];
}

function git(repo, ...args) {
  const [code, output] = run(repo, ["git", ...args]);
  if (code !== 0) throw new Error(output || `git ${args.join(" ")} failed`);
  return output;
}

function parseRefUpdates(text) {
  const branches = text.split("\n").map((line) => line.trim().split(/\s+/)).filter((fields) => fields.length === 4 && fields[0].startsWith("refs/heads/") && fields[1] !== ZERO_SHA);
  if (!branches.length) return null;
  if (branches.length !== 1) throw new Error("一次只 push 一个分支，才能生成唯一的代码摘要和 commit message");
  return branches[0];
}

function resolveBase(appRepo, remoteName, remoteRef, remoteSha, localSha) {
  if (remoteSha !== ZERO_SHA) {
    git(appRepo, "cat-file", "-e", `${remoteSha}^{commit}`);
    return remoteSha;
  }
  const branch = remoteRef.replace(/^refs\/heads\//, "");
  const [code, trackingSha] = run(appRepo, ["git", "rev-parse", "--verify", `refs/remotes/${remoteName}/${branch}^{commit}`]);
  if (code === 0 && trackingSha) return trackingSha;
  return git(appRepo, "rev-parse", `${localSha}^`);
}

function wikiStatus(wikiRepo) {
  return git(wikiRepo, "status", "--porcelain");
}

function showMessage(messagePath) {
  console.log("\nCodex 生成的 commit message：");
  console.log("─".repeat(60));
  console.log(readFileSync(messagePath, "utf8").trimEnd());
  console.log("─".repeat(60));
  console.log(`消息文件：${messagePath}`);
}

function readJson(file) {
  if (!existsSync(file)) return null;
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

function isCompleteResult(result) {
  return result
    && typeof result.commit_subject === "string"
    && typeof result.commit_body === "string"
    && ["updated", "no_update", "blocked"].includes(result.wiki_action)
    && Array.isArray(result.wiki_pages)
    && typeof result.wiki_reason === "string"
    && typeof result.change_summary === "string";
}

function main() {
  if (process.argv.length < 4) {
    console.error("usage: screen_remote_pre_push.mjs <remote-name> <remote-url>");
    return 2;
  }
  const remoteName = process.argv[2];
  const updates = parseRefUpdates(readFileSync(0, "utf8"));
  if (!updates) return 0;
  const [, localSha, remoteRef, remoteSha] = updates;
  const appRepo = path.resolve(git(process.cwd(), "rev-parse", "--show-toplevel"));
  const commitMessage = git(appRepo, "show", "-s", "--format=%B", localSha);
  if (commitMessage.split("\n").some((line) => line.trim() === REVIEW_MARKER)) {
    console.log(`已检测到 ${REVIEW_MARKER}，允许本次 push。`);
    return 0;
  }
  const outer = path.dirname(appRepo);
  const wikiRepo = path.join(outer, "external", "wiki");
  const skillDir = path.join(appRepo, ".agents", "skills", "screen-remote-engineering");
  if (!existsSync(path.join(skillDir, "SKILL.md")) || !existsSync(path.join(wikiRepo, ".git"))) throw new Error("outer project, skill, or external/wiki repository not found");

  const baseSha = resolveBase(appRepo, remoteName, remoteRef, remoteSha, localSha);
  const outgoingCount = Number(git(appRepo, "rev-list", "--count", `${baseSha}..${localSha}`));
  if (outgoingCount === 0) return 0;

  const gitPathRaw = git(appRepo, "rev-parse", "--git-path", "codex-pre-push");
  const stateDir = path.resolve(appRepo, gitPathRaw);
  mkdirSync(stateDir, { recursive: true });
  const messagePath = path.join(stateDir, "commit-message.txt");
  const resultPath = path.join(stateDir, "result.json");
  const contextPath = path.join(stateDir, "review-context.json");
  const cachedContext = readJson(contextPath);
  const cachedResult = readJson(resultPath);
  const cacheMatches = cachedContext?.base_sha === baseSha
    && cachedContext?.local_sha === localSha
    && cachedContext?.status === "complete"
    && isCompleteResult(cachedResult);

  if (process.env.SCREEN_REMOTE_PRE_PUSH_TEST === "1") {
    console.log(`self-test: would analyze ${baseSha.slice(0, 12)}..${localSha.slice(0, 12)} (${outgoingCount} commits)`);
    return 0;
  }
  let result = cachedResult;
  if (cacheMatches) {
    console.log(`\n✓ 已复用相同范围的 Codex 审读结果：${baseSha.slice(0, 12)}..${localSha.slice(0, 12)}`);
  } else {
    if (wikiStatus(wikiRepo)) {
      console.error("push 已暂停：外层 external/wiki/ 已有未提交修改，无法安全区分本轮同步。");
      return 1;
    }
    const [codexCode] = run(appRepo, ["codex", "--version"]);
    if (codexCode !== 0) throw new Error("找不到 codex CLI");

  const schemaPath = path.join(skillDir, "scripts", "pre_push_result.schema.json");
  const prompt = `Use $screen-remote-engineering and read references/wiki-sync.md completely.\n\nThis task was triggered by a human git push from the Screen-Remote app subrepository.\nAnalyze exactly the committed range ${baseSha}..${localSha} in ${appRepo}. It currently contains ${outgoingCount} outgoing commit(s), which the developer may squash after receiving your message.\nDo not use existing commit messages as your summary source.\nRead changed hunks, relevant contracts, and nearby tests. When documented knowledge changes, update complete Chinese/English page pairs only in the outer-root GitHub Wiki repository at ${wikiRepo}; use <name>.md and <name>-EN.md with reciprocal language links. Never create wiki pages inside the Screen-Remote app repository.\nDo not run git rebase, commit, amend, reset, push, or modify files outside wiki.\nReturn the requested structured result, including a Chinese commit message derived from the code.`;
  const resolvedPrompt = `${prompt.replace("references/wiki-sync.md", path.join(skillDir, "references", "wiki-sync.md"))}\nWrite commit_subject and commit_body in English only.`;
  writeFileSync(contextPath, `${JSON.stringify({ base_sha: baseSha, local_sha: localSha, status: "running" }, null, 2)}\n`);
  writeFileSync(resultPath, "");
  console.log("\n▶ Codex pre-push 审读已启动");
  console.log(`  范围：${baseSha.slice(0, 12)}..${localSha.slice(0, 12)}（${outgoingCount} commits）`);
  console.log(`  Wiki：${wikiRepo}`);
  console.log("  以下为 Codex 实时输出：\n");
  const [code] = run(appRepo, ["codex", "exec", "--ephemeral", "--sandbox", "workspace-write", "--cd", appRepo, "--add-dir", wikiRepo, "--output-schema", schemaPath, "--output-last-message", resultPath, "--color", "always", resolvedPrompt], { stream: true });
  result = readJson(resultPath);
  if (!isCompleteResult(result)) throw new Error("Codex pre-push 未生成有效结果");
  writeFileSync(contextPath, `${JSON.stringify({ base_sha: baseSha, local_sha: localSha, status: "complete" }, null, 2)}\n`);
  if (code !== 0) console.warn("\n警告：Codex 返回非零状态，但已生成有效结果；继续处理本次结果。");
  }

  if (result.wiki_action === "blocked") throw new Error(result.wiki_reason);
  const wikiDirty = wikiStatus(wikiRepo);
  if (result.wiki_action === "updated" && !wikiDirty) throw new Error("Codex 报告 Wiki 已更新，但外层 external/wiki/ 没有修改");
  if (result.wiki_action === "no_update" && wikiDirty) throw new Error("Codex 报告无需更新 Wiki，但外层 external/wiki/ 出现了修改");

  const subject = result.commit_subject.trim();
  const body = result.commit_body.trim();
  writeFileSync(messagePath, `${subject}${body ? `\n\n${body}` : ""}\n\n${REVIEW_MARKER}\n`);

  showMessage(messagePath);
  console.log(`\nWiki decision: ${result.wiki_action} — ${result.wiki_reason}`);
  if (result.wiki_pages.length) console.log(`Wiki pages: ${result.wiki_pages.join(", ")}`);
  console.log(`\n请先 rebase/squash，然后编辑上述 message（保留最后的 ${REVIEW_MARKER}）。`);
  console.log(`可从文件采用：git commit --amend -F ${messagePath}`);
  console.error("本次 push 已暂停；完成整理后再次 push，由 hook 做最终确认。");
  return 1;
}

try {
  process.exitCode = main();
} catch (error) {
  console.error(`push 已暂停：${error.message}`);
  process.exitCode = 1;
}
