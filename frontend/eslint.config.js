// @ts-check
const eslint = require("@eslint/js");
const tseslint = require("typescript-eslint");
const angular = require("angular-eslint");
const oxlint = require("eslint-plugin-oxlint");

module.exports = tseslint.config(
  {
    linterOptions: {
      reportUnusedDisableDirectives: "error",
    },
  },
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: __dirname,
      },
    },
    processor: angular.processInlineTemplates,
    rules: {
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
      "@angular-eslint/inject-at-top": "error",
      "@angular-eslint/no-async-lifecycle-method": "error",
      "@angular-eslint/no-duplicates-in-metadata-arrays": "error",
      "@angular-eslint/no-lifecycle-call": "error",
      "@angular-eslint/prefer-output-emitter-ref": "error",
      "@angular-eslint/prefer-service-decorator": "error",
      "@angular-eslint/prefer-signals": "error",
      "@angular-eslint/reactive-context-must-read-signal": "error",
      "@angular-eslint/use-lifecycle-interface": "error",
    },
  },
  {
    files: ["**/*.html"],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {
      "@angular-eslint/template/button-has-type": "error",
      "@angular-eslint/template/no-any": "error",
      "@angular-eslint/template/no-duplicate-attributes": [
        "error",
        {
          allowStylePrecedenceDuplicates: true,
        },
      ],
      "@angular-eslint/template/no-empty-control-flow": "error",
      "@angular-eslint/template/no-non-null-assertion": "error",
      "@angular-eslint/template/no-outerhtml": "error",
      "@angular-eslint/template/no-positive-tabindex": "error",
      "@angular-eslint/template/prefer-at-else": "error",
      "@angular-eslint/template/prefer-at-empty": "error",
      "@angular-eslint/template/prefer-class-binding": "error",
      "@angular-eslint/template/prefer-self-closing-tags": "error",
      "@angular-eslint/template/prefer-style-binding": "error",
      "@angular-eslint/template/require-switch-default": "error",
    },
  },
  ...oxlint.buildFromOxlintConfigFile("./.oxlintrc.json"),
);
