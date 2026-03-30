import {describe, expect, expectTypeOf, it} from 'vitest';

import {FetchMetadataRequest} from './fetch-metadata-request.model';

describe('fetch-metadata-request.model', () => {
  it('captures the metadata fetch payload shape', () => {
    const request: FetchMetadataRequest = {
      bookId: 12,
      providers: ['google', 'hardcover'],
      title: 'A Wizard of Earthsea',
      author: 'Ursula K. Le Guin',
      isbn: '9780547773742'
    };

    expect(request.providers).toContain('google');
    expect(request.bookId).toBe(12);
    expectTypeOf(request.providers).toEqualTypeOf<string[]>();
  });
});
