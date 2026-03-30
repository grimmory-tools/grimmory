import {QueryClient} from '@tanstack/angular-query-experimental';
import {describe, expect, it} from 'vitest';

import {AUTHORS_QUERY_KEY} from './author-query-keys';
import {patchAuthorInCache} from './author-query-cache';

describe('patchAuthorInCache', () => {
  it('patches the matching author while keeping the rest intact', () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(AUTHORS_QUERY_KEY, [
      {id: 1, name: 'Ada', bookCount: 1, hasPhoto: false},
      {id: 2, name: 'Bert', bookCount: 2, hasPhoto: true},
    ]);

    patchAuthorInCache(queryClient, 2, {name: 'Bert Updated', hasPhoto: false});

    expect(queryClient.getQueryData(AUTHORS_QUERY_KEY)).toEqual([
      {id: 1, name: 'Ada', bookCount: 1, hasPhoto: false},
      {id: 2, name: 'Bert Updated', bookCount: 2, hasPhoto: false},
    ]);
  });

  it('initializes an empty cache when there is no existing author list', () => {
    const queryClient = new QueryClient();

    patchAuthorInCache(queryClient, 3, {name: 'Cy'});

    expect(queryClient.getQueryData(AUTHORS_QUERY_KEY)).toEqual([]);
  });
});
