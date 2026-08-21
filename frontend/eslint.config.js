// @ts-check
const eslint = require("@eslint/js");
const tseslint = require("typescript-eslint");
const angular = require("angular-eslint");
const oxlint = require("eslint-plugin-oxlint");

module.exports = tseslint.config(
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      "*": "off",
      // Keep Angular's framework checks after general JS/TS linting moves to Oxlint.
      ...angular.configs.tsRecommended[1].rules,
      "@typescript-eslint/no-explicit-any": "error",
      "@angular-eslint/directive-selector": [
        "error",
        {
          type: "attribute",
          prefix: "app",
          style: "camelCase",
        },
      ],
      "@angular-eslint/component-selector": [
        "error",
        {
          type: "element",
          prefix: "app",
          style: "kebab-case",
        },
      ],
      "@typescript-eslint/no-inferrable-types": "off",
      "@angular-eslint/computed-must-return": "error",
      "@angular-eslint/no-async-lifecycle-method": "warn",
      "@angular-eslint/no-duplicates-in-metadata-arrays": "warn",
      "@angular-eslint/no-lifecycle-call": "error",
    },
  },
  {
    files: ["**/*.html"],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {
      "@angular-eslint/template/button-has-type": "warn",
      "@angular-eslint/template/no-any": "warn",
      "@angular-eslint/template/no-duplicate-attributes": "warn",
      "@angular-eslint/template/no-empty-control-flow": "error",
      "@angular-eslint/template/no-positive-tabindex": "error",
    },
  },
  ...oxlint.buildFromOxlintConfigFile("./.oxlintrc.json"),
);
