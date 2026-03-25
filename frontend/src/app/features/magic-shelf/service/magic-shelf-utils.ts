import {RuleOperator} from '../component/magic-shelf-component';

export const MULTI_VALUE_OPERATORS: RuleOperator[] = [
  'includes_any',
  'includes_all',
  'excludes_all'
];

export const EMPTY_CHECK_OPERATORS: RuleOperator[] = [
  'is_empty',
  'is_not_empty'
];

export const RELATIVE_DATE_OPERATORS: RuleOperator[] = [
  'within_last',
  'older_than',
  'this_period'
];

export function parseValue(val: unknown, type: 'string' | 'number' | 'decimal' | 'date' | 'boolean' | undefined): unknown {
  if (val == null) return null;
  if (type === 'number' || type === 'decimal') {
    const num = Number(val);
    return isNaN(num) ? null : num;
  }
  if (type === 'date') {
    const d = new Date(val as string | number | Date);
    return isNaN(d.getTime()) ? null : d;
  }
  return val;
}

export function removeNulls(obj: unknown): unknown {
  if (Array.isArray(obj)) {
    return obj.map(removeNulls);
  } else if (typeof obj === 'object' && obj !== null) {
    return Object.entries(obj).reduce((acc: Record<string, unknown>, [key, value]) => {
      const cleanedValue = removeNulls(value);
      if (cleanedValue !== null && cleanedValue !== undefined) {
        acc[key] = cleanedValue;
      }
      return acc;
    }, {});
  }
  return obj;
}

export function serializeDateRules(ruleOrGroup: unknown): unknown {
  if (ruleOrGroup !== null && typeof ruleOrGroup === 'object' && 'rules' in ruleOrGroup) {
    const group = ruleOrGroup as { rules: unknown[] };
    return {
      ...(ruleOrGroup as Record<string, unknown>),
      rules: group.rules.map(serializeDateRules)
    };
  }

  const rule = ruleOrGroup as { field?: string; operator?: string; value?: unknown; valueStart?: unknown; valueEnd?: unknown; [key: string]: unknown };
  const isRelativeDateOp = RELATIVE_DATE_OPERATORS.includes(rule.operator as RuleOperator);
  const isDateField = !isRelativeDateOp && (rule.field === 'publishedDate' || rule.field === 'dateFinished' || rule.field === 'addedOn' || rule.field === 'lastReadTime');
  const serialize = (val: unknown) => (val instanceof Date ? val.toISOString().split('T')[0] : val);

  return {
    ...(ruleOrGroup as Record<string, unknown>),
    value: isDateField ? serialize(rule.value) : rule.value,
    valueStart: isDateField ? serialize(rule.valueStart) : rule.valueStart,
    valueEnd: isDateField ? serialize(rule.valueEnd) : rule.valueEnd
  };
}
