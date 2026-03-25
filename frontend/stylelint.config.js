module.exports = {
  extends: ["stylelint-config-standard-scss"],
  ignoreFiles: ["dist/**", "node_modules/**"],
  rules: {
    "at-rule-empty-line-before": null,
    "declaration-block-single-line-max-declarations": null,
    "declaration-property-value-keyword-no-deprecated": null,
    "keyframes-name-pattern": null,
    "no-descending-specificity": null,
    "no-invalid-position-declaration": null,
    "property-no-deprecated": null,
    "property-no-vendor-prefix": null,
    "selector-class-pattern": null,
    "selector-pseudo-element-no-unknown": [
      true,
      {
        ignorePseudoElements: ["host", "ng-deep"],
      },
    ],
    "scss/dollar-variable-empty-line-before": null,
    "scss/dollar-variable-pattern": null,
    "scss/load-partial-extension": null,
    "scss/load-no-partial-leading-underscore": null,
  },
};
