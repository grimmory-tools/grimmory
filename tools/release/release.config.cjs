const releasePolicy = require("./release-policy.cjs");

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
          types: releasePolicy.types.map(({ section, type }) => ({ hidden: false, section, type }))
        },
        parserOpts: {
          noteKeywords: releasePolicy.noteKeywords
        },
        releaseRules: releasePolicy.types
          .filter((entry) => Object.prototype.hasOwnProperty.call(entry, "release"))
          .map(({ release, type }) => ({ release, type }))
      }
    ],
    [
      "@semantic-release/release-notes-generator",
      {
        preset: "conventionalcommits",
        presetConfig: {
          types: releasePolicy.types.map(({ section, type }) => ({ hidden: false, section, type }))
        },
        parserOpts: {
          noteKeywords: releasePolicy.noteKeywords
        },
        writerOpts: {
          commitsSort: releasePolicy.writerSortFields
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
