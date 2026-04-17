import { execFileSync } from "node:child_process";
import { writeFileSync } from "node:fs";
import releasePolicy from "../release-policy.cjs";

const repoRoot = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
const fromRef = requiredEnv("FROM_REF");
const toRef = process.env.TO_REF || "HEAD";
const outputJsonPath = process.env.OUTPUT_JSON || "range-release.json";
const outputNotesPath = process.env.OUTPUT_NOTES || "range-release-notes.md";
const baseTag = process.env.BASE_TAG || "";
const baseVersion = coerceVersion(process.env.BASE_VERSION || stripVersion(baseTag) || stripVersion(fromRef));
const repositoryUrl = normalizeRepositoryUrl(process.env.REPOSITORY_URL || defaultRepositoryUrl());
const compareBase = process.env.COMPARE_FROM_REF || fromRef;
const compareHead = process.env.COMPARE_TO_REF || toRef;

const policyByType = new Map(releasePolicy.types.map((entry) => [entry.type, entry]));
const sections = releasePolicy.types.map((entry) => entry.section);

const fromSha = runGit(["rev-parse", fromRef]).trim();
const toSha = runGit(["rev-parse", toRef]).trim();
const commits = parseCommits(listCommits(fromRef, toRef));
const groupedCommits = new Map();

for (const section of sections) {
  groupedCommits.set(section, []);
}
groupedCommits.set("Other Changes", []);

let releaseType = "none";
const breakingChanges = [];

for (const commit of commits) {
  groupedCommits.get(commit.section).push(commit);
  releaseType = maxReleaseType(releaseType, commit.releaseType);
  for (const note of commit.breakingNotes) {
    breakingChanges.push({ hash: commit.hash, note });
  }
}

const predictedVersion = releaseType !== "none" ? incrementVersion(baseVersion, releaseType) : "";
const compareUrl = repositoryUrl ? `${repositoryUrl}/compare/${compareBase}...${compareHead}` : "";
const releaseNotes = renderReleaseNotes(groupedCommits, breakingChanges, repositoryUrl);

const payload = {
  baseTag,
  baseVersion,
  compareUrl,
  fromRef,
  fromSha,
  nextGitTag: predictedVersion ? `v${predictedVersion}` : "",
  nextVersion: predictedVersion,
  releaseNotes,
  releaseType,
  released: releaseType !== "none",
  toRef,
  toSha
};

writeFileSync(outputJsonPath, `${JSON.stringify(payload, null, 2)}\n`);
writeFileSync(outputNotesPath, releaseNotes ? `${releaseNotes}\n` : "");

function runGit(args) {
  return execFileSync("git", args, { cwd: repoRoot, encoding: "utf8" });
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }

  return value;
}

function defaultRepositoryUrl() {
  if (process.env.GITHUB_SERVER_URL && process.env.GITHUB_REPOSITORY) {
    return `${process.env.GITHUB_SERVER_URL}/${process.env.GITHUB_REPOSITORY}`;
  }

  return "";
}

function normalizeRepositoryUrl(value) {
  if (!value) {
    return "";
  }

  return value.replace(/\.git$/, "");
}

function stripVersion(value) {
  if (!value) {
    return "";
  }

  const match = value.match(/^v?(\d+\.\d+\.\d+)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/);
  return match ? match[1] : "";
}

function coerceVersion(value) {
  if (!value) {
    return "0.0.0";
  }

  const match = value.match(/^v?(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/);
  return match ? match[1] + "." + match[2] + "." + match[3] : "0.0.0";
}

function listCommits(startRef, endRef) {
  return runGit([
    "log",
    "--reverse",
    "--no-merges",
    "--format=%H%x1f%s%x1f%b%x1e",
    `${startRef}..${endRef}`
  ]);
}

function parseCommits(rawLog) {
  return rawLog
    .split("\x1e")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [hash, subject, body = ""] = entry.split("\x1f");
      const conventional = parseConventionalSubject(subject);
      const type = conventional?.type || "";
      const policy = policyByType.get(type);

      return {
        body,
        breakingNotes: findBreakingNotes(body, subject),
        hash,
        releaseType: detectReleaseType(conventional, body),
        scope: conventional?.scope || "",
        section: policy?.section || "Other Changes",
        subject: conventional?.subject || subject,
        type
      };
    });
}

function parseConventionalSubject(subject) {
  const match = subject.match(/^(?<type>[a-z]+)(?:\((?<scope>[^)]+)\))?(?<breaking>!)?: (?<subject>.+)$/i);
  if (!match?.groups) {
    return null;
  }

  return {
    breaking: Boolean(match.groups.breaking),
    scope: match.groups.scope || "",
    subject: match.groups.subject,
    type: match.groups.type.toLowerCase()
  };
}

function findBreakingNotes(body, subject) {
  const lines = body.split(/\r?\n/);
  const notes = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) {
      continue;
    }

    for (const keyword of releasePolicy.noteKeywords) {
      const prefix = `${keyword}:`;
      if (trimmed.startsWith(prefix)) {
        notes.push(trimmed.slice(prefix.length).trim() || subject);
      }
    }
  }

  return notes;
}

function detectReleaseType(conventional, body) {
  if (!conventional) {
    return "none";
  }

  if (conventional.breaking || findBreakingNotes(body, conventional.subject).length > 0) {
    return "major";
  }

  if (conventional.type === "feat") {
    return "minor";
  }

  if (conventional.type === "fix" || conventional.type === "perf" || conventional.type === "revert") {
    return "patch";
  }

  if (conventional.type === "refactor") {
    return "patch";
  }

  const policy = policyByType.get(conventional.type);
  if (policy?.release === false) {
    return "none";
  }

  return "none";
}

function maxReleaseType(current, next) {
  const rank = new Map([
    ["none", 0],
    ["patch", 1],
    ["minor", 2],
    ["major", 3]
  ]);

  return rank.get(next) > rank.get(current) ? next : current;
}

function incrementVersion(version, releaseType) {
  const [major, minor, patch] = version.split(".").map((value) => Number.parseInt(value, 10) || 0);

  if (releaseType === "major") {
    return `${major + 1}.0.0`;
  }

  if (releaseType === "minor") {
    return `${major}.${minor + 1}.0`;
  }

  if (releaseType === "patch") {
    return `${major}.${minor}.${patch + 1}`;
  }

  return "";
}

function renderReleaseNotes(grouped, breakingNotes, repository) {
  const lines = [];

  if (breakingNotes.length > 0) {
    lines.push("## Breaking Changes", "");
    for (const entry of breakingNotes) {
      lines.push(`- ${entry.note} (${formatCommitRef(entry.hash, repository)})`);
    }
    lines.push("");
  }

  for (const [section, commits] of grouped.entries()) {
    if (commits.length === 0) {
      continue;
    }

    const sortedCommits = [...commits].sort((left, right) => {
      const leftKey = `${left.scope}\u0000${left.subject}`.toLowerCase();
      const rightKey = `${right.scope}\u0000${right.subject}`.toLowerCase();
      return leftKey.localeCompare(rightKey);
    });

    lines.push(`## ${section}`, "");
    for (const commit of sortedCommits) {
      const scopePrefix = commit.scope ? `**${commit.scope}:** ` : "";
      lines.push(`- ${scopePrefix}${commit.subject} (${formatCommitRef(commit.hash, repository)})`);
    }
    lines.push("");
  }

  return lines.join("\n").trim();
}

function formatCommitRef(hash, repository) {
  const shortHash = hash.slice(0, 7);
  if (!repository) {
    return `\`${shortHash}\``;
  }

  return `[\`${shortHash}\`](${repository}/commit/${hash})`;
}
