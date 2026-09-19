import {describe, expect, it} from 'vitest';

import {hasMetadataWriter, isFieldEmbeddable} from './embeddable-fields.config';

const NO_PROVIDER_FIELDS: ReadonlySet<string> = new Set();

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
    expect(isFieldEmbeddable('EPUB', 'title', NO_PROVIDER_FIELDS)).toBe(true);
    expect(isFieldEmbeddable('EPUB', 'narrator', NO_PROVIDER_FIELDS)).toBe(false);
    expect(isFieldEmbeddable('AUDIOBOOK', 'narrator', NO_PROVIDER_FIELDS)).toBe(true);
    expect(isFieldEmbeddable('CBX', 'comicIssueNumber', NO_PROVIDER_FIELDS)).toBe(true);
    expect(isFieldEmbeddable(undefined, 'title', NO_PROVIDER_FIELDS)).toBe(false);
  });

  it('embeds the provider fields it is given into ebooks only', () => {
    const providerFieldNames = new Set(['goodreadsId']);

    expect(isFieldEmbeddable('EPUB', 'goodreadsId', providerFieldNames)).toBe(true);
    expect(isFieldEmbeddable('PDF', 'goodreadsId', providerFieldNames)).toBe(true);
    expect(isFieldEmbeddable('CBX', 'goodreadsId', providerFieldNames)).toBe(false);
    expect(isFieldEmbeddable('EPUB', 'goodreadsId', NO_PROVIDER_FIELDS)).toBe(false);
  });
});
