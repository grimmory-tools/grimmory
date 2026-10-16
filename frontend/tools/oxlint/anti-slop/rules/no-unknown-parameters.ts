import { defineRule } from "@oxlint/plugins";
import type { ESTree } from "@oxlint/plugins";

import {
  containsUnknownType,
  functionParameterBindingName,
  functionParameterTypeAnnotation,
} from "../shared/function-parameters.ts";
type ParameterOwner =
  | ESTree.ArrowFunctionExpression
  | ESTree.Function
  | ESTree.TSCallSignatureDeclaration
  | ESTree.TSConstructSignatureDeclaration
  | ESTree.TSConstructorType
  | ESTree.TSFunctionType
  | ESTree.TSMethodSignature;

function isTypePredicateSubject(owner: ParameterOwner, parameterName: string): boolean {
  const predicate = owner.returnType?.typeAnnotation;
  return (
    predicate?.type === "TSTypePredicate" &&
    predicate.parameterName.type === "Identifier" &&
    predicate.parameterName.name === parameterName
  );
}

function calleeName(callee: ESTree.Expression): string | null {
  if (callee.type === "Identifier") return callee.name;
  if (callee.type === "MemberExpression" && !callee.computed && callee.property.type === "Identifier") {
    return callee.property.name;
  }
  return null;
}

function isErrorHandler(owner: ParameterOwner): boolean {
  if (owner.type !== "ArrowFunctionExpression" && owner.type !== "FunctionExpression") return false;
  const parent = owner.parent;
  if (parent.type === "CallExpression") {
    const name = calleeName(parent.callee);
    if ((name === "catch" || name === "catchError") && parent.arguments[0] === owner) return true;
    return name === "then" && parent.arguments[1] === owner;
  }
  return (
    parent.type === "Property" &&
    parent.value === owner &&
    parent.key.type === "Identifier" &&
    parent.key.name === "error"
  );
}

/** Disallow unknown inputs except explicitly named error-cause enrichment. */
export const noUnknownParametersRule = defineRule({
  meta: {
    type: "problem",
    docs: {
      description:
        "Disallow explicitly unknown function parameters except `cause` and type-predicate subjects; decode unknown input at its I/O boundary instead.",
    },
    messages: {
      unknownParameter:
        "Parameter `{{parameter}}` leaves input unparsed. Accept a named domain type; run the expected schema or parser at the I/O boundary before calling this function.",
    },
  },
  createOnce(context) {
    const hasParseName = (node: ParameterOwner): boolean => {
      if ("id" in node && node.id?.type === "Identifier" && node.id.name.startsWith("parse")) {
        return true;
      }
      if (node.type === "TSMethodSignature" && node.key.type === "Identifier" && node.key.name.startsWith("parse")) {
        return true;
      }
      const parent = node.parent;
      if (parent.type === "MethodDefinition" && parent.key.type === "Identifier" && parent.key.name.startsWith("parse")) {
        return true;
      }
      if (parent.type === "VariableDeclarator" && parent.id.type === "Identifier" && parent.id.name.startsWith("parse")) {
        return true;
      }
      if (parent.type === "TSTypeAnnotation" && parent.parent.type === "Identifier" && parent.parent.name.startsWith("parse")) {
        return true;
      }
      return false;
    };
    const checkParameters = (node: ParameterOwner) => {
      if (hasParseName(node)) {
        return;
      }
      for (const parameter of node.params) {
        const annotation = functionParameterTypeAnnotation(parameter);
        if (annotation === null || annotation === undefined) continue;
        if (!containsUnknownType(annotation.typeAnnotation)) continue;
        const name = functionParameterBindingName(parameter, context.sourceCode);
        if (name === "cause" || name === "error" || name === "err") continue;
        if (isTypePredicateSubject(node, name)) continue;
        if (parameter === node.params[0] && isErrorHandler(node)) continue;
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
