const types = [
  { type: "feat", section: "Features" },
  { type: "fix", section: "Bug Fixes" },
  { type: "perf", section: "Performance" },
  { type: "refactor", section: "Refactors", release: "patch" },
  { type: "chore", section: "Chores", release: false },
  { type: "docs", section: "Documentation", release: false },
  { type: "ci", section: "CI", release: false },
  { type: "build", section: "Build", release: false },
  { type: "test", section: "Tests", release: false },
  { type: "style", section: "Style", release: false },
  { type: "revert", section: "Reverts", release: "patch" }
];

const noteKeywords = ["BREAKING CHANGE", "BREAKING CHANGES", "BREAKING"];

module.exports = {
  noteKeywords,
  types,
  writerSortFields: ["scope", "subject"]
};
