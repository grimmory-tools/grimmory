import {describe, expect, it} from 'vitest';

import {providerFieldsOf} from './metadata-provider-fields';
import {METADATA_PROVIDER_LIST} from './metadata-providers';

const fields = providerFieldsOf(METADATA_PROVIDER_LIST);

describe('provider fields', () => {
  it('covers every provider field exactly once, in catalog order', () => {
    expect(fields.map(field => field.name)).toEqual([
      'openlibraryId',
      'asin', 'amazonRating', 'amazonReviewCount',
      'goodreadsId', 'goodreadsRating', 'goodreadsReviewCount',
      'googleId',
      'hardcoverId', 'hardcoverBookId', 'hardcoverRating', 'hardcoverReviewCount',
      'comicvineId',
      'lubimyczytacId', 'lubimyczytacRating',
      'ranobedbId', 'ranobedbRating',
      'audibleId', 'audibleRating', 'audibleReviewCount',
      'applebooksId', 'applebooksRating', 'applebooksReviewCount',
    ]);
  });

  it('names each lock after its field and types IDs as strings', () => {
    for (const field of fields) {
      expect(field.lockName).toBe(`${field.name}Locked`);
      expect(field.valueType).toBe(field.role === 'id' ? 'string' : 'number');
    }
  });

  it('labels by role, except where the definition names its own key', () => {
    const byName = new Map(fields.map(field => [field.name, field]));

    expect(byName.get('goodreadsRating')).toMatchObject({
      labelKey: 'metadata.providerFields.rating',
      providerLabelKey: 'metadata.providers.goodReads',
    });
    expect(byName.get('asin')?.labelKey).toBe('metadata.providerFields.asin');
    expect(byName.get('hardcoverBookId')?.labelKey).toBe('metadata.providerFields.hardcoverBookId');
  });
});
