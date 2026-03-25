module.exports = {
  extends: ["stylelint-config-standard-scss"],
  ignoreFiles: ["dist/**", "node_modules/**"],
  rules: {
    "no-descending-specificity": null,
    "no-invalid-position-declaration": [
      true,
      {
        ignoreAtRules: ["media"],
      },
    ],
    "selector-pseudo-element-no-unknown": [
      true,
      {
        ignorePseudoElements: ["host", "ng-deep"],
      },
    ],
    "scss/load-partial-extension": null,
    "scss/load-no-partial-leading-underscore": null,
  },
};
