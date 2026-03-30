import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.resolve(scriptDir, '..');
const reportPath = path.resolve(frontendRoot, 'coverage/grimmory/coverage-final.json');
const rootPrefix = path.resolve(frontendRoot, 'src/app') + path.sep;
const report = JSON.parse(readFileSync(reportPath, 'utf8'));

function summarizeCounts(counts) {
  if (!counts) {
    return {covered: 0, total: 0, pct: 100};
  }
  const values = Object.values(counts);
  const covered = values.filter(value => value > 0).length;
  const total = values.length;

  return {covered, total, pct: total === 0 ? 100 : Number(((covered / total) * 100).toFixed(2))};
}

function summarizeBranches(branches) {
  if (!branches) {
    return {covered: 0, total: 0, pct: 100};
  }

  const values = Object.values(branches).flatMap(branchHits => Array.isArray(branchHits) ? branchHits : []);
  const covered = values.filter(value => value > 0).length;
  const total = values.length;

  return {covered, total, pct: total === 0 ? 100 : Number(((covered / total) * 100).toFixed(2))};
}

function summarizeLines(entry) {
  const lineKeys = new Set();
  const coveredLineKeys = new Set();

  for (const [statementId, location] of Object.entries(entry.statementMap ?? {})) {
    for (let line = location.start.line; line <= location.end.line; line += 1) {
      lineKeys.add(line);
      if ((entry.s?.[statementId] ?? 0) > 0) {
        coveredLineKeys.add(line);
      }
    }
  }

  const total = lineKeys.size;
  const covered = coveredLineKeys.size;

  return {covered, total, pct: total === 0 ? 100 : Number(((covered / total) * 100).toFixed(2))};
}

function summarizeFile(entry) {
  return {
    statements: summarizeCounts(entry.s),
    branches: summarizeBranches(entry.b),
    functions: summarizeCounts(entry.f),
    lines: summarizeLines(entry),
  };
}

const buckets = new Map();
const worstFiles = [];
let totals = {
  statements: {covered: 0, total: 0},
  branches: {covered: 0, total: 0},
  functions: {covered: 0, total: 0},
  lines: {covered: 0, total: 0},
};

for (const [absolutePath, entry] of Object.entries(report)) {
  if (!absolutePath.startsWith(rootPrefix) || absolutePath.endsWith('.spec.ts')) {
    continue;
  }

  const relativePath = absolutePath.slice(rootPrefix.length).replaceAll(path.sep, '/');
  const [topLevel, featureName] = relativePath.split('/');
  const bucketName = topLevel === 'features'
    ? `features/${featureName}`
    : ['core', 'shared'].includes(topLevel)
      ? topLevel
      : 'app-root';
  const fileSummary = summarizeFile(entry);

  worstFiles.push({path: relativePath, branchPct: fileSummary.branches.pct});

  for (const metric of ['statements', 'branches', 'functions', 'lines']) {
    totals[metric].covered += fileSummary[metric].covered;
    totals[metric].total += fileSummary[metric].total;
  }

  const bucket = buckets.get(bucketName) ?? {
    statements: {covered: 0, total: 0},
    branches: {covered: 0, total: 0},
    functions: {covered: 0, total: 0},
    lines: {covered: 0, total: 0},
    files: 0,
  };

  for (const metric of ['statements', 'branches', 'functions', 'lines']) {
    bucket[metric].covered += fileSummary[metric].covered;
    bucket[metric].total += fileSummary[metric].total;
  }
  bucket.files += 1;

  buckets.set(bucketName, bucket);
}

function toPct({covered, total}) {
  return total === 0 ? 100 : Number(((covered / total) * 100).toFixed(2));
}

console.log('Global coverage');
for (const metric of ['statements', 'branches', 'functions', 'lines']) {
  console.log(`- ${metric}: ${toPct(totals[metric])}% (${totals[metric].covered}/${totals[metric].total})`);
}

console.log('\nCoverage by bucket');
for (const [bucketName, bucket] of [...buckets.entries()].sort((a, b) => toPct(a[1].branches) - toPct(b[1].branches))) {
  console.log(
    `- ${bucketName}: statements ${toPct(bucket.statements)}%, branches ${toPct(bucket.branches)}%, functions ${toPct(bucket.functions)}% across ${bucket.files} files`
      + `, lines ${toPct(bucket.lines)}%`
  );
}

console.log('\nWorst files by branch coverage');
for (const file of worstFiles.sort((a, b) => a.branchPct - b.branchPct).slice(0, 15)) {
  console.log(`- ${file.path}: ${file.branchPct}%`);
}
