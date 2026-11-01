import {describe, expect, it} from 'vitest';

import {requireBookIds} from './book-command.models';

describe('requireBookIds', () => {
  it('rejects an empty selection before any request leaves', () => {
    expect(() => requireBookIds([])).toThrowError();
  });

  it('passes a non-empty selection through', () => {
    expect(requireBookIds([3, 4])).toEqual([3, 4]);
  });
});
