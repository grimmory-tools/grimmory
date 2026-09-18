import {describe, expect, it} from 'vitest';

import {BrowsePage, findBrowsePageLink} from './browse.models';

describe('browse models', () => {
  it('finds page links by an array relation', () => {
    const page: BrowsePage<string> = {
      content: [],
      page: {
        number: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        cursor: 'opaque-cursor',
      },
      links: [
        {rel: ['self'], href: '/api/v1/items/page', type: 'application/json'},
        {rel: ['next', 'collection'], href: '/api/v1/items/page?cursor=next', type: 'application/json'},
      ],
    };

    expect(findBrowsePageLink(page, 'next')?.href).toBe('/api/v1/items/page?cursor=next');
    expect(findBrowsePageLink(page, 'previous')).toBeUndefined();
  });
});
