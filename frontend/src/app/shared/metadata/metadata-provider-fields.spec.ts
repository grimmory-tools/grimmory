import {describe, expect, it} from 'vitest';

import {METADATA_PROVIDER_FIELDS} from './metadata-provider-fields';

describe('provider fields', () => {
  it('names each lock after its field and types IDs as strings', () => {
    for (const field of METADATA_PROVIDER_FIELDS) {
      expect(field.lockName).toBe(`${field.name}Locked`);
      expect(field.valueType).toBe(field.role === 'id' ? 'string' : 'number');
    }
  });
});
