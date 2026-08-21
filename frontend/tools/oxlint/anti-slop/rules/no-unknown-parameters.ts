import { defineRule } from "@oxlint/plugins";
import type { ESTree } from "@oxlint/plugins";

type Parameter = ESTree.ParamPattern;
type ParameterOwner =
  | ESTree.ArrowFunctionExpression
  | ESTree.Function
  | ESTree.TSCallSignatureDeclaration
  | ESTree.TSConstructSignatureDeclaration
  | ESTree.TSConstructorType
  | ESTree.TSFunctionType
  | ESTree.TSMethodSignature;

function parameterAnnotation(parameter: Parameter): ESTree.TSTypeAnnotation | null | undefined {
  if (parameter.type === "TSParameterProperty") {
    return parameterAnnotation(parameter.parameter);
  }
  if (parameter.type === "RestElement") {
    return parameter.typeAnnotation ?? parameterAnnotation(parameter.argument);
  }
  if (parameter.type === "AssignmentPattern") {
    return parameter.typeAnnotation ?? parameter.left.typeAnnotation;
  }
  return parameter.typeAnnotation;
}

function parameterName(parameter: Parameter, sourceText: string): string {
  if (parameter.type === "TSParameterProperty") {
    return parameterName(parameter.parameter, sourceText);
  }
  if (parameter.type === "AssignmentPattern") {
    return parameterName(parameter.left, sourceText);
  }
  if (parameter.type === "RestElement") {
    return parameterName(parameter.argument, sourceText);
  }
  return parameter.type === "Identifier"
    ? parameter.name
    : sourceText.replace(/\s*:\s*unknown\s*$/u, "");
}

/** Disallow unknown inputs except explicitly named error-cause enrichment. */
export const noUnknownParametersRule = defineRule({
  meta: {
    type: "problem",
    docs: {
      description:
        "Disallow explicitly unknown function parameters except `cause`; decode unknown input at its I/O boundary instead.",
    },
    messages: {
      unknownParameter:
        "Parameter `{{parameter}}` leaves input unparsed. Accept a named domain type; run the expected schema or parser at the I/O boundary before calling this function.",
    },
  },
  createOnce(context) {
    // Vendored tailoring: functions named `parse*` (including function-typed
    // parameters and variables with such names) are boundary decoders — the
    // very functions this rule's message tells callers to run first.
    const hasParseName = (node: ParameterOwner): boolean => {
      if ("id" in node && node.id?.type === "Identifier" && node.id.name.startsWith("parse")) {
        return true;
      }
      const parent = node.parent;
      if (parent.type === "VariableDeclarator" && parent.id.type === "Identifier" && parent.id.name.startsWith("parse")) {
        return true;
      }
      if (parent.type === "TSTypeAnnotation" && parent.parent.type === "Identifier" && parent.parent.name.startsWith("parse")) {
        return true;
      }
      return false;
    };
    const checkParameters = (node: ParameterOwner) => {
      // Vendored tailoring: type guards and assertion functions ARE the
      // boundary decoders this rule points callers toward, so their own
      // `unknown` input is the one legitimate place it appears.
      if ("returnType" in node && node.returnType?.typeAnnotation.type === "TSTypePredicate") {
        return;
      }
      if (hasParseName(node)) {
        return;
      }
      for (const parameter of node.params) {
        const annotation = parameterAnnotation(parameter);
        if (annotation?.typeAnnotation.type !== "TSUnknownKeyword") continue;
        const name = parameterName(parameter, context.sourceCode.getText(parameter));
        // Vendored tailoring: error callbacks genuinely receive unknown values —
        // typescript/use-unknown-in-catch-callback-variable requires `unknown`
        // there, and callsites narrow immediately via getApiErrorMessage/guards.
        if (name === "cause" || name === "err" || name === "error") continue;
        context.report({
          node: annotation.typeAnnotation,
          messageId: "unknownParameter",
          data: { parameter: name },
        });
      }
    };

    return {
      ArrowFunctionExpression: checkParameters,
      FunctionDeclaration: checkParameters,
      FunctionExpression: checkParameters,
      TSCallSignatureDeclaration: checkParameters,
      TSConstructSignatureDeclaration: checkParameters,
      TSConstructorType: checkParameters,
      TSDeclareFunction: checkParameters,
      TSEmptyBodyFunctionExpression: checkParameters,
      TSFunctionType: checkParameters,
      TSMethodSignature: checkParameters,
    };
  },
});
