import {describe, expect, it} from 'vitest';

import en from '../../../i18n/en.json';
import {METADATA_FIELD_GROUPS, METADATA_FIELDS} from './metadata-fields';

function english(key: string): unknown {
  return key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], en);
}

describe('metadata fields', () => {
  it('names every lock after its field', () => {
    expect(METADATA_FIELDS.filter(field => field.lockName !== `${field.name}Locked`)).toEqual([]);
  });

  it('has an English label for every group and field', () => {
    const keys = [...METADATA_FIELD_GROUPS.map(group => group.labelKey), ...METADATA_FIELDS.map(field => field.labelKey)];

    expect(keys.filter(key => typeof english(key) !== 'string')).toEqual([]);
  });
});
