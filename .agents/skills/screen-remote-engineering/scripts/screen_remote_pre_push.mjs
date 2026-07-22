#!/usr/bin/env node

import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const ZERO_SHA = "0".repeat(40);
const REVIEW_MARKER = "Screen-Remote-Review: confirmed";
const CODEX_MODEL = "gpt-5.6-sol";
const CODEX_REASONING_EFFORT = "medium";

function run(cwd, args) {
  const result = spawnSync(args[0], args.slice(1), {
    cwd,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    stdio: ["ignore", "pipe", "pipe"],
  });
  return [result.status ?? 1, `${result.stdout ?? ""}${result.stderr ?? ""}`.trim()];
}

function runStreaming(cwd, args, onOutput) {
  return new Promise((resolve) => {
    const child = spawn(args[0], args.slice(1), {
      cwd,
      stdio: ["ignore", "pipe", "pipe"],
    });
    child.stdout.on("data", (chunk) => {
      process.stdout.write(chunk);
      onOutput?.(chunk.toString("utf8"));
    });
    child.stderr.on("data", (chunk) => {
      process.stderr.write(chunk);
      onOutput?.(chunk.toString("utf8"));
    });
    child.on("error", (error) => {
      console.error(`无法启动 ${args[0]}：${error.message}`);
      resolve([1, ""]);
    });
    child.on("close", (code) => resolve([code ?? 1, ""]));
  });
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

function withoutReviewMarker(text) {
  return text
    .split(/\r?\n/)
    .filter((line) => line.trim() !== REVIEW_MARKER)
    .join("\n")
    .trim();
}

function createSessionRecorder({ baseSha, contextPath, localSha }) {
  let buffer = "";
  let sessionId = null;
  return (chunk) => {
    if (sessionId) return;
    buffer = `${buffer}${chunk}`.replace(/\x1b\[[0-9;]*m/g, "").slice(-4096);
    const match = buffer.match(/session id:\s*([0-9a-f]{8}-[0-9a-f-]{27})/i);
    if (!match) return;
    sessionId = match[1];
    writeFileSync(contextPath, `${JSON.stringify({
      base_sha: baseSha,
      local_sha: localSha,
      model: CODEX_MODEL,
      reasoning_effort: CODEX_REASONING_EFFORT,
      session_id: sessionId,
      status: "running",
    }, null, 2)}\n`);
  };
}

function finishReview(result, { contextPath, messagePath, wikiRepo }) {
  if (!isCompleteResult(result)) throw new Error("Codex pre-push 未生成有效结果");
  if (result.wiki_action === "blocked") throw new Error(result.wiki_reason);

  const wikiDirty = wikiStatus(wikiRepo);
  if (result.wiki_action === "updated" && !wikiDirty) throw new Error("Codex 报告 Wiki 已更新，但外层 external/wiki/ 没有修改");
  if (result.wiki_action === "no_update" && wikiDirty) throw new Error("Codex 报告无需更新 Wiki，但外层 external/wiki/ 出现了修改");

  const subject = withoutReviewMarker(result.commit_subject);
  const body = withoutReviewMarker(result.commit_body);
  if (!subject) throw new Error("Codex 生成的 commit subject 仅包含确认标记");
  writeFileSync(messagePath, `${subject}${body ? `\n\n${body}` : ""}\n\n${REVIEW_MARKER}\n`);

  showMessage(messagePath);
  console.log(`\nWiki decision: ${result.wiki_action} — ${result.wiki_reason}`);
  if (result.wiki_pages.length) console.log(`Wiki pages: ${result.wiki_pages.join(", ")}`);
  writeFileSync(contextPath, "");
}

async function resumeInterruptedReview() {
  const appRepo = path.resolve(git(process.cwd(), "rev-parse", "--show-toplevel"));
  const outer = path.dirname(appRepo);
  const wikiRepo = path.join(outer, "external", "wiki");
  const skillDir = path.join(appRepo, ".agents", "skills", "screen-remote-engineering");
  const stateDir = path.resolve(appRepo, git(appRepo, "rev-parse", "--git-path", "codex-pre-push"));
  const messagePath = path.join(stateDir, "commit-message.txt");
  const resultPath = path.join(stateDir, "result.json");
  const contextPath = path.join(stateDir, "review-context.json");
  const context = readJson(contextPath);

  if (context?.status !== "running" || !context.base_sha || !context.local_sha || !context.session_id) {
    throw new Error("没有可恢复的 pre-push Wiki 审读任务");
  }
  if (git(appRepo, "rev-parse", "HEAD") !== context.local_sha) {
    throw new Error("当前 HEAD 已变化；请直接再次 git push，让 hook 审读新的提交范围");
  }
  if (!existsSync(path.join(skillDir, "SKILL.md")) || !existsSync(path.join(wikiRepo, ".git"))) {
    throw new Error("outer project, skill, or external/wiki repository not found");
  }

  const schemaPath = path.join(skillDir, "scripts", "pre_push_result.schema.json");
  const resumePrompt = `Continue the interrupted Screen-Remote pre-push review for ${context.base_sha}..${context.local_sha}. Reuse the context and tool results already present in this session instead of restarting the review. Inspect the existing Wiki working-tree changes, finish any incomplete bilingual updates, and return the required structured result. Keep ${appRepo} read-only and write only inside ${wikiRepo}. Write commit_subject and commit_body in English without the ${REVIEW_MARKER} trailer.`;
  writeFileSync(resultPath, "");
  console.log("\n▶ 正在恢复上次中断的 Codex pre-push 审读");
  console.log(`  范围：${context.base_sha.slice(0, 12)}..${context.local_sha.slice(0, 12)}`);
  console.log(`  模型：${CODEX_MODEL} / ${CODEX_REASONING_EFFORT}`);
  console.log(`  Session：${context.session_id}`);
  console.log(`  Wiki：${wikiRepo}\n`);
  const [code] = await runStreaming(wikiRepo, ["codex", "exec", "resume", "--model", CODEX_MODEL, "--config", `model_reasoning_effort="${CODEX_REASONING_EFFORT}"`, "--output-schema", schemaPath, "--output-last-message", resultPath, context.session_id, resumePrompt]);
  const result = readJson(resultPath);
  finishReview(result, { contextPath, messagePath, wikiRepo });
  if (code !== 0) console.warn("\n警告：Codex 返回非零状态，但已生成有效结果；继续处理本次结果。");
  console.log("\n恢复完成。请检查 Wiki 和生成的 commit message；该命令不会执行 git push。");
  return 0;
}

async function main() {
  if (process.argv[2] === "--resume") return resumeInterruptedReview();
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
  const existingContext = readJson(contextPath);
  if (existingContext?.status === "running"
      && existingContext.base_sha === baseSha
      && existingContext.local_sha === localSha
      && existingContext.session_id) {
    console.error(`检测到同一提交范围的中断任务，请运行：make -C ${outer} wiki-resume`);
    return 1;
  }

  if (process.env.SCREEN_REMOTE_PRE_PUSH_TEST === "1") {
    console.log(`self-test: would analyze ${baseSha.slice(0, 12)}..${localSha.slice(0, 12)} (${outgoingCount} commits)`);
    return 0;
  }
  if (wikiStatus(wikiRepo)) {
    console.error("push 已暂停：外层 external/wiki/ 已有未提交修改，无法安全区分本轮同步。");
    return 1;
  }
  const [codexCode] = run(appRepo, ["codex", "--version"]);
  if (codexCode !== 0) throw new Error("找不到 codex CLI");

  const schemaPath = path.join(skillDir, "scripts", "pre_push_result.schema.json");
  const skillPath = path.join(skillDir, "SKILL.md");
  const wikiSyncPath = path.join(skillDir, "references", "wiki-sync.md");
  const prompt = `Use $screen-remote-engineering by reading ${skillPath} and ${wikiSyncPath} completely.\n\nThis task was triggered by a human git push from the Screen-Remote app subrepository.\nTreat ${appRepo} as a read-only source repository and run app Git commands with git -C ${appRepo}. Your only writable project directory is the Wiki repository at ${wikiRepo}.\nAnalyze exactly the committed range ${baseSha}..${localSha} in ${appRepo}. It currently contains ${outgoingCount} outgoing commit(s), which the developer may squash after receiving your message.\nDo not use existing commit messages as your summary source.\nRead changed hunks, relevant contracts, and nearby tests. When documented knowledge changes, update complete Chinese/English page pairs only in ${wikiRepo}; use <name>.md and <name>-EN.md with reciprocal language links. Never create wiki pages inside the Screen-Remote app repository.\nDo not run git rebase, commit, amend, reset, push, or modify files outside wiki.\nReturn the requested structured result, including an English commit message derived from the code.\nWrite commit_subject and commit_body in English only. Do not include the ${REVIEW_MARKER} trailer in either field; the hook appends it exactly once.`;
  writeFileSync(contextPath, `${JSON.stringify({
    base_sha: baseSha,
    local_sha: localSha,
    model: CODEX_MODEL,
    reasoning_effort: CODEX_REASONING_EFFORT,
    session_id: null,
    status: "running",
  }, null, 2)}\n`);
  writeFileSync(resultPath, "");
  console.log("\n▶ Codex pre-push 审读已启动");
  console.log(`  范围：${baseSha.slice(0, 12)}..${localSha.slice(0, 12)}（${outgoingCount} commits）`);
  console.log(`  模型：${CODEX_MODEL} / ${CODEX_REASONING_EFFORT}`);
  console.log(`  Wiki：${wikiRepo}`);
  console.log(`  若任务中断：make -C ${outer} wiki-resume`);
  console.log("  以下为 Codex 实时输出：\n");
  const recordSession = createSessionRecorder({ baseSha, contextPath, localSha });
  const [code] = await runStreaming(wikiRepo, ["codex", "exec", "--model", CODEX_MODEL, "--config", `model_reasoning_effort="${CODEX_REASONING_EFFORT}"`, "--sandbox", "workspace-write", "--cd", wikiRepo, "--output-schema", schemaPath, "--output-last-message", resultPath, "--color", "always", prompt], recordSession);
  const result = readJson(resultPath);
  finishReview(result, { contextPath, messagePath, wikiRepo });
  if (code !== 0) console.warn("\n警告：Codex 返回非零状态，但已生成有效结果；继续处理本次结果。");

  console.log(`\n请先 rebase/squash，然后编辑上述 message（保留最后的 ${REVIEW_MARKER}）。`);
  console.log(`可从文件采用：git commit --amend -F ${messagePath}`);
  console.error("本次 push 已暂停；完成整理后再次 push，由 hook 做最终确认。");
  return 1;
}

try {
  process.exitCode = await main();
} catch (error) {
  console.error(`push 已暂停：${error.message}`);
  process.exitCode = 1;
}
