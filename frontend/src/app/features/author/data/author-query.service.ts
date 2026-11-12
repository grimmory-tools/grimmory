import {Service} from '@angular/core';

import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {authorQueryKeys} from './author-query-keys';
import {AuthorQueryFacetKey, AuthorQuerySortKey, isAuthorQueryFacetKey} from './author-query-params';
import {AuthorSummary} from './author-query.models';

@Service()
export class AuthorQueryService extends BrowseQueryService<AuthorSummary, AuthorQueryFacetKey, AuthorQuerySortKey> {
  constructor() {
    super({path: '/api/v1/authors', keys: authorQueryKeys, isFacetKey: isAuthorQueryFacetKey});
  }
}
