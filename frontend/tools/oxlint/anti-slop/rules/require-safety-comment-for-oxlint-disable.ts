import { defineRule } from "@oxlint/plugins";

const oxlintDisableDirective = /^\s*oxlint-disable(?:-line|-next-line)?\b/u;
const safetyExplanation = /\bSAFETY\s*:/u;

/** Require every Oxlint suppression to explain why leaving the code is safe. */
export const requireSafetyCommentForOxlintDisableRule = defineRule({
  meta: {
    type: "problem",
    docs: {
      description: "Require a SAFETY explanation on every Oxlint disable directive.",
    },
    messages: {
      missingSafetyComment:
        "This Oxlint suppression has no `SAFETY:` explanation. State why leaving the finding in place is safe.",
    },
  },
  createOnce(context) {
    return {
      Program() {
        for (const comment of context.sourceCode.getAllComments()) {
          if (
            oxlintDisableDirective.test(comment.value)
            && !safetyExplanation.test(comment.value)
          ) {
            context.report({node: comment, messageId: "missingSafetyComment"});
          }
        }
      },
    };
  },
});
