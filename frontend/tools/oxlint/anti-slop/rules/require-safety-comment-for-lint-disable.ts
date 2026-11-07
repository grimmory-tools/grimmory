import { defineRule } from "@oxlint/plugins";

const lintDisableDirective = /^\s*(?:oxlint|eslint)-disable(?:-line|-next-line)?\b/u;
const safetyExplanation = /(?:^|[^\p{L}\p{N}_])SAFETY\s*:\s*\S/u;

export const requireSafetyCommentForLintDisableRule = defineRule({
  meta: {
    type: "problem",
    docs: {
      description: "Require a SAFETY explanation on every Oxlint or ESLint disable directive.",
    },
    messages: {
      missingSafetyComment:
        "This lint suppression has no `SAFETY:` explanation. State why leaving the finding in place is safe.",
    },
  },
  createOnce(context) {
    return {
      Program() {
        for (const comment of context.sourceCode.getAllComments()) {
          if (
            lintDisableDirective.test(comment.value)
            && !safetyExplanation.test(comment.value)
          ) {
            context.report({node: comment, messageId: "missingSafetyComment"});
          }
        }
      },
    };
  },
});
