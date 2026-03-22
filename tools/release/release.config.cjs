module.exports = {
  repositoryUrl: "https://github.com/grimmory-tools/grimmory.git",
  branches: ["main"],
  tagFormat: "v${version}",
  plugins: [
    [
      "@semantic-release/commit-analyzer",
      {
        preset: "conventionalcommits",
        presetConfig: {
          types: [
            { type: "feat", section: "Features", hidden: false },
            { type: "fix", section: "Bug Fixes", hidden: false },
            { type: "perf", section: "Performance", hidden: false },
            { type: "refactor", section: "Refactors", hidden: false },
            { type: "chore", section: "Chores", hidden: false },
            { type: "docs", section: "Documentation", hidden: false },
            { type: "ci", section: "CI", hidden: false },
            { type: "build", section: "Build", hidden: false },
            { type: "test", section: "Tests", hidden: false },
            { type: "style", section: "Style", hidden: false },
            { type: "revert", section: "Reverts", hidden: false }
          ]
        },
        parserOpts: {
          noteKeywords: ["BREAKING CHANGE", "BREAKING CHANGES", "BREAKING"]
        },
        releaseRules: [
          { type: "refactor", release: "patch" },
          { type: "docs", release: false },
          { type: "ci", release: false },
          { type: "build", release: false },
          { type: "chore", release: false },
          { type: "test", release: false },
          { type: "style", release: false }
        ]
      }
    ],
    [
      "@semantic-release/release-notes-generator",
      {
        preset: "conventionalcommits",
        presetConfig: {
          types: [
            { type: "feat", section: "Features", hidden: false },
            { type: "fix", section: "Bug Fixes", hidden: false },
            { type: "perf", section: "Performance", hidden: false },
            { type: "refactor", section: "Refactors", hidden: false },
            { type: "chore", section: "Chores", hidden: false },
            { type: "docs", section: "Documentation", hidden: false },
            { type: "ci", section: "CI", hidden: false },
            { type: "build", section: "Build", hidden: false },
            { type: "test", section: "Tests", hidden: false },
            { type: "style", section: "Style", hidden: false },
            { type: "revert", section: "Reverts", hidden: false }
          ]
        },
        parserOpts: {
          noteKeywords: ["BREAKING CHANGE", "BREAKING CHANGES", "BREAKING"]
        },
        writerOpts: {
          commitsSort: ["scope", "subject"]
        }
      }
    ],
    [
      "@semantic-release/changelog",
      {
        changelogFile: "CHANGELOG.md"
      }
    ],
    [
      "@semantic-release/git",
      {
        assets: ["CHANGELOG.md"],
        message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}"
      }
    ],
    [
      "@semantic-release/github",
      {
        draftRelease: true,
        releaseNameTemplate: "Release <%= nextRelease.gitTag %>",
        successComment: false,
        failComment: false,
        releasedLabels: false
      }
    ]
  ]
};
