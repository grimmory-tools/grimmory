import {rangeTokenLabel} from '../../../shared/browse/facet-ranges';
import {browseFieldIndex, type BrowseField, type BrowseMissingFieldKeys} from '../../../shared/browse/fields';
import {LIBRARY_FACET, type BrowseFacetEntityLookup} from '../../../shared/browse/entity-facets';
import {readStatusFacetLabel} from '../../book/components/book-read-status-options';
import {
  AUTHOR_QUERY_FACET_KEYS,
  AUTHOR_QUERY_SORT_KEYS,
  isAuthorQueryFacetKey,
  isAuthorQuerySortKey,
  type AuthorQueryFacetKey,
  type AuthorQuerySortKey,
} from '../data/author-query-params';

export interface AuthorFacetLabelDeps {
  readonly library: BrowseFacetEntityLookup;
  readonly translate: (key: string) => string;
}

type AuthorBrowseField = BrowseField<AuthorQueryFacetKey, AuthorQuerySortKey, AuthorFacetLabelDeps>;

const FIELDS = [
  {labelKey: 'author.fields.name', sort: {key: 'name', group: 'common', defaultDirection: 'asc', kind: 'alphabetical'}},
  {labelKey: 'author.fields.surname', sort: {key: 'sortName', group: 'common', defaultDirection: 'asc', kind: 'alphabetical'}},
  {
    labelKey: 'author.fields.bookCount',
    facet: {key: 'book_count', kind: 'range', valueLabel: value => rangeTokenLabel(value)},
    sort: {key: 'bookCount', group: 'common', defaultDirection: 'desc', kind: 'numeric'},
  },
  {labelKey: 'author.fields.seriesCount', sort: {key: 'seriesCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'}},
  {labelKey: 'book.fields.addedOn', sort: {key: 'addedOn', group: 'common', defaultDirection: 'desc', kind: 'calendar'}},
  {labelKey: 'book.fields.lastReadTime', sort: {key: 'lastReadTime', group: 'common', defaultDirection: 'desc', kind: 'clock'}},
  {labelKey: 'book.fields.personalRating', sort: {key: 'personalRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'}},
  {labelKey: 'book.fields.amazonRating', sort: {key: 'amazonRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'}},
  {labelKey: 'book.fields.goodreadsRating', sort: {key: 'goodreadsRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'}},
  {labelKey: 'book.fields.hardcoverRating', sort: {key: 'hardcoverRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'}},
  {labelKey: 'book.fields.ranobedbRating', sort: {key: 'ranobedbRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'}},
  {labelKey: 'book.fields.genre', facet: {key: 'genre', openByDefault: true}},
  {labelKey: 'book.fields.language', facet: {key: 'language'}},
  {
    labelKey: 'book.fields.library',
    facet: LIBRARY_FACET,
  },
  {
    labelKey: 'book.fields.readStatus',
    facet: {
      key: 'read_status',
      valueLabel: (value, deps) => readStatusFacetLabel(value, deps.translate),
    },
  },
  {labelKey: 'author.fields.hasPhoto', facet: {key: 'has_photo', kind: 'flag'}},
  {labelKey: 'author.fields.hasAsin', facet: {key: 'has_asin', kind: 'flag'}},
  {labelKey: 'author.fields.hasDescription', facet: {key: 'has_description', kind: 'flag'}},
] as const satisfies readonly AuthorBrowseField[];

type AssertNever<Value extends never> = Value;
export type AuthorFieldsComplete = AssertNever<
  BrowseMissingFieldKeys<(typeof FIELDS)[number], AuthorQueryFacetKey, AuthorQuerySortKey>
>;

export const AUTHOR_FIELDS = browseFieldIndex<AuthorBrowseField, AuthorQueryFacetKey, AuthorQuerySortKey, AuthorFacetLabelDeps>(
  FIELDS,
  {
    facetKeys: AUTHOR_QUERY_FACET_KEYS,
    sortKeys: AUTHOR_QUERY_SORT_KEYS,
    isFacetKey: isAuthorQueryFacetKey,
    isSortKey: isAuthorQuerySortKey,
  },
);
