import {describe, expect, it} from 'vitest';
import {RELATIVE_DATE_OPERATORS, MULTI_VALUE_OPERATORS, EMPTY_CHECK_OPERATORS, parseValue, serializeDateRules, removeNulls} from './magic-shelf-utils';

describe('magic-shelf-utils', () => {

  describe('RELATIVE_DATE_OPERATORS', () => {
    it('should contain within_last, older_than, this_period', () => {
      expect(RELATIVE_DATE_OPERATORS).toEqual(['within_last', 'older_than', 'this_period']);
    });

    it('should not overlap with MULTI_VALUE_OPERATORS', () => {
      RELATIVE_DATE_OPERATORS.forEach(op => {
        expect(MULTI_VALUE_OPERATORS).not.toContain(op);
      });
    });

    it('should not overlap with EMPTY_CHECK_OPERATORS', () => {
      RELATIVE_DATE_OPERATORS.forEach(op => {
        expect(EMPTY_CHECK_OPERATORS).not.toContain(op);
      });
    });
  });

  describe('serializeDateRules', () => {
    it('should serialize Date values for regular date operators', () => {
      const rule = {
        field: 'dateFinished',
        operator: 'equals',
        value: new Date('2024-06-15T00:00:00Z'),
        valueStart: null,
        valueEnd: null
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe('2024-06-15');
    });

    it('should serialize Date values for in_between operator on date fields', () => {
      const rule = {
        field: 'addedOn',
        operator: 'in_between',
        value: null,
        valueStart: new Date('2024-01-01T00:00:00Z'),
        valueEnd: new Date('2024-12-31T00:00:00Z')
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['valueStart']).toBe('2024-01-01');
      expect(result['valueEnd']).toBe('2024-12-31');
    });

    it('should NOT serialize values for within_last operator on date fields', () => {
      const rule = {
        field: 'dateFinished',
        operator: 'within_last',
        value: 30,
        valueStart: null,
        valueEnd: 'days'
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe(30);
      expect(result['valueEnd']).toBe('days');
    });

    it('should NOT serialize values for older_than operator on date fields', () => {
      const rule = {
        field: 'lastReadTime',
        operator: 'older_than',
        value: 6,
        valueStart: null,
        valueEnd: 'months'
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe(6);
      expect(result['valueEnd']).toBe('months');
    });

    it('should NOT serialize values for this_period operator on date fields', () => {
      const rule = {
        field: 'addedOn',
        operator: 'this_period',
        value: 'year',
        valueStart: null,
        valueEnd: null
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe('year');
    });

    it('should NOT serialize values for publishedDate with within_last', () => {
      const rule = {
        field: 'publishedDate',
        operator: 'within_last',
        value: 2,
        valueStart: null,
        valueEnd: 'years'
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe(2);
      expect(result['valueEnd']).toBe('years');
    });

    it('should recurse through group rules', () => {
      const group = {
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'dateFinished',
            operator: 'within_last',
            value: 7,
            valueStart: null,
            valueEnd: 'days'
          },
          {
            field: 'addedOn',
            operator: 'equals',
            value: new Date('2024-03-15T00:00:00Z'),
            valueStart: null,
            valueEnd: null
          }
        ]
      };

      const result = serializeDateRules(group) as { rules: Record<string, unknown>[] };
      expect(result.rules[0]['value']).toBe(7);
      expect(result.rules[0]['valueEnd']).toBe('days');
      expect(result.rules[1]['value']).toBe('2024-03-15');
    });

    it('should not affect non-date fields', () => {
      const rule = {
        field: 'title',
        operator: 'contains',
        value: 'Harry Potter',
        valueStart: null,
        valueEnd: null
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe('Harry Potter');
    });
  });

  describe('parseValue', () => {
    it('should parse number values correctly', () => {
      expect(parseValue(42, 'number')).toBe(42);
      expect(parseValue('42', 'number')).toBe(42);
      expect(parseValue(3.5, 'decimal')).toBe(3.5);
    });

    it('should return null for NaN number values', () => {
      expect(parseValue('not a number', 'number')).toBeNull();
    });

    it('should parse date values correctly', () => {
      const result = parseValue('2024-06-15', 'date');
      expect(result).toBeInstanceOf(Date);
      expect((result as Date).getFullYear()).toBe(2024);
    });

    it('should return null for invalid date values', () => {
      expect(parseValue('not a date', 'date')).toBeNull();
    });

    it('should return null for null input', () => {
      expect(parseValue(null, 'number')).toBeNull();
      expect(parseValue(null, 'date')).toBeNull();
      expect(parseValue(null, undefined)).toBeNull();
    });

    it('should return value as-is for undefined type', () => {
      expect(parseValue('hello', undefined)).toBe('hello');
      expect(parseValue('true', 'boolean' as any)).toBe('true');
    });

    it('should return arrays as-is for undefined type', () => {
      const arr = ['a', 'b'];
      expect(parseValue(arr, undefined)).toBe(arr);
    });
  });

  describe('removeNulls', () => {
    it('should remove null values from flat object', () => {
      expect(removeNulls({a: 1, b: null, c: 'hello'})).toEqual({a: 1, c: 'hello'});
    });

    it('should remove undefined values from flat object', () => {
      expect(removeNulls({a: 1, b: undefined})).toEqual({a: 1});
    });

    it('should keep falsy non-null values', () => {
      expect(removeNulls({a: 0, b: '', c: false})).toEqual({a: 0, b: '', c: false});
    });

    it('should recurse into nested objects', () => {
      expect(removeNulls({a: {b: null, c: 1}, d: 2})).toEqual({a: {c: 1}, d: 2});
    });

    it('should recurse into arrays', () => {
      expect(removeNulls([{a: null, b: 1}, {c: null}])).toEqual([{b: 1}, {}]);
    });

    it('should handle empty objects', () => {
      expect(removeNulls({})).toEqual({});
    });

    it('should handle empty arrays', () => {
      expect(removeNulls([])).toEqual([]);
    });

    it('should pass through primitive values', () => {
      expect(removeNulls('hello')).toBe('hello');
      expect(removeNulls(42)).toBe(42);
    });

    it('should handle deeply nested structures', () => {
      expect(removeNulls({a: {b: {c: null, d: {e: null, f: 1}}}})).toEqual({a: {b: {d: {f: 1}}}});
    });
  });

  describe('serializeDateRules edge cases', () => {
    it('should handle lastReadTime date field serialization', () => {
      const rule = {
        field: 'lastReadTime',
        operator: 'equals',
        value: new Date('2024-08-20T00:00:00Z'),
        valueStart: null,
        valueEnd: null
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe('2024-08-20');
    });

    it('should preserve string values on date fields', () => {
      const rule = {
        field: 'dateFinished',
        operator: 'equals',
        value: '2024-06-15',
        valueStart: null,
        valueEnd: null
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe('2024-06-15');
    });

    it('should handle null value on date field', () => {
      const rule = {
        field: 'dateFinished',
        operator: 'is_empty',
        value: null,
        valueStart: null,
        valueEnd: null
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBeNull();
    });

    it('should handle undefined value on date field', () => {
      const rule = {
        field: 'addedOn',
        operator: 'is_empty',
        value: undefined,
        valueStart: null,
        valueEnd: null
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBeUndefined();
    });

    it('should handle deeply nested groups', () => {
      const nested = {
        type: 'group',
        join: 'and',
        rules: [
          {
            type: 'group',
            join: 'or',
            rules: [
              {
                field: 'addedOn',
                operator: 'equals',
                value: new Date('2024-11-01T00:00:00Z'),
                valueStart: null,
                valueEnd: null
              }
            ]
          }
        ]
      };

      const result = serializeDateRules(nested) as { rules: { rules: Record<string, unknown>[] }[] };
      expect(result.rules[0].rules[0]['value']).toBe('2024-11-01');
    });

    it('should handle rule with no field property', () => {
      const rule = {
        operator: 'equals',
        value: 'test'
      };

      const result = serializeDateRules(rule) as Record<string, unknown>;
      expect(result['value']).toBe('test');
    });

    it('should handle empty rules array in group', () => {
      const group = {
        type: 'group',
        rules: []
      };

      const result = serializeDateRules(group) as { rules: unknown[] };
      expect(result.rules).toEqual([]);
    });
  });

  describe('parseValue edge cases', () => {
    it('should parse 0 as valid number', () => {
      expect(parseValue(0, 'number')).toBe(0);
    });

    it('should parse empty string as 0 for number', () => {
      expect(parseValue('', 'number')).toBe(0);
    });

    it('should handle undefined input', () => {
      expect(parseValue(undefined, 'number')).toBeNull();
    });

    it('should handle Date object input for date type', () => {
      const result = parseValue(new Date('2024-01-01'), 'date');
      expect(result).toBeInstanceOf(Date);
    });

    it('should handle numeric timestamp for date type', () => {
      const result = parseValue(1718409600000, 'date');
      expect(result).toBeInstanceOf(Date);
    });

    it('should parse negative numbers', () => {
      expect(parseValue(-5, 'number')).toBe(-5);
    });

    it('should parse decimal string as number', () => {
      expect(parseValue('3.14', 'decimal')).toBe(3.14);
    });

    it('should return string type value as-is', () => {
      expect(parseValue('hello', 'string')).toBe('hello');
    });
  });

  describe('MULTI_VALUE_OPERATORS', () => {
    it('should contain exactly includes_any, includes_all, excludes_all', () => {
      expect(MULTI_VALUE_OPERATORS).toEqual(['includes_any', 'includes_all', 'excludes_all']);
    });

    it('should have length 3', () => {
      expect(MULTI_VALUE_OPERATORS.length).toBe(3);
    });
  });

  describe('EMPTY_CHECK_OPERATORS', () => {
    it('should contain exactly is_empty, is_not_empty', () => {
      expect(EMPTY_CHECK_OPERATORS).toEqual(['is_empty', 'is_not_empty']);
    });

    it('should have length 2', () => {
      expect(EMPTY_CHECK_OPERATORS.length).toBe(2);
    });
  });
});
