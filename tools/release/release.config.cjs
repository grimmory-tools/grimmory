const releasePolicy = require("./release-policy.cjs");

module.exports = {
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
