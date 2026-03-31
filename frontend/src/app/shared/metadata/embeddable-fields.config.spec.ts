import {describe, expect, it} from 'vitest';

import {hasMetadataWriter, isFieldEmbeddable} from './embeddable-fields.config';

describe('embeddable-fields.config', () => {
  it('reports writer support only for embeddable book types', () => {
    expect(hasMetadataWriter('EPUB')).toBe(true);
    expect(hasMetadataWriter('PDF')).toBe(true);
    expect(hasMetadataWriter('CBX')).toBe(true);
    expect(hasMetadataWriter('AUDIOBOOK')).toBe(true);
    expect(hasMetadataWriter('FB2')).toBe(false);
    expect(hasMetadataWriter(undefined)).toBe(false);
  });

  it('checks embeddable field membership by book type', () => {
    expect(isFieldEmbeddable('EPUB', 'title')).toBe(true);
    expect(isFieldEmbeddable('EPUB', 'narrator')).toBe(false);
    expect(isFieldEmbeddable('AUDIOBOOK', 'narrator')).toBe(true);
    expect(isFieldEmbeddable('CBX', 'comicIssueNumber')).toBe(true);
    expect(isFieldEmbeddable(undefined, 'title')).toBe(false);
  });
});
