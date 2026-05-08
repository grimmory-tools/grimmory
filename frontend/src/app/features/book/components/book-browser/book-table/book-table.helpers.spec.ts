import {describe, expect, it} from 'vitest';

import {BookMetadata} from '../../../model/book.model';
import {LOCK_FIELDS, isMetadataFullyLocked} from './book-table.helpers';

describe('book-table helpers', () => {
  it('uses the explicit allMetadataLocked value when present', () => {
    expect(isMetadataFullyLocked({bookId: 1, allMetadataLocked: true})).toBe(true);
    expect(isMetadataFullyLocked({bookId: 1, allMetadataLocked: false, titleLocked: true})).toBe(false);
  });

  it('does not infer a full lock from partial metadata', () => {
    expect(isMetadataFullyLocked({bookId: 1, titleLocked: true, authorsLocked: true})).toBe(false);
  });

  it('requires every metadata lock field to be true when the explicit flag is absent', () => {
    const metadata = {
      bookId: 1,
      ...Object.fromEntries(LOCK_FIELDS.map(field => [field, true])),
    } satisfies BookMetadata;

    expect(isMetadataFullyLocked(metadata)).toBe(true);
    expect(isMetadataFullyLocked({...metadata, titleLocked: false})).toBe(false);
  });
});
