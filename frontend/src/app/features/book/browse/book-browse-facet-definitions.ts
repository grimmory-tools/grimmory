import {type BrowseFacetDefinitions} from '../../../shared/browse/facets';
import {
  BOOK_QUERY_FACET_KEYS,
  isBookQueryFacetKey,
  type BookQueryFacetKey,
} from '../data/book-query-params';
import {type BookSummary} from '../data/book-response.models';
import {
  COLUMNS_BY_KEY,
  FACET_FIELDS,
  OPEN_RAIL_FACETS,
  type BookFacetEntity,
  type BookFacetLabelDeps,
} from './book-browse-fields';

export interface BookFacetLink {
  readonly key: BookQueryFacetKey;
  readonly value: string;
}

interface BookFacetEntityWithId extends BookFacetEntity {
  readonly id?: string | number;
}

export function bookFacetLabelDeps(
  shelves: readonly BookFacetEntityWithId[],
  libraries: readonly BookFacetEntityWithId[],
  translate: (key: string) => string,
): BookFacetLabelDeps {
  const byId = (entities: readonly BookFacetEntityWithId[]) =>
    new Map(entities.map(entity => [`${entity.id}`, entity]));
  const shelvesById = byId(shelves);
  const librariesById = byId(libraries);
  return {
    shelf: id => shelvesById.get(id),
    library: id => librariesById.get(id),
    translate,
  };
}

function registeredFacetLabelKey(key: BookQueryFacetKey): string {
  return FACET_FIELDS.get(key)!.labelKey;
}

export function bookFacetDefinitions(deps: BookFacetLabelDeps): BrowseFacetDefinitions<BookQueryFacetKey> {
  return {
    order: BOOK_QUERY_FACET_KEYS,
    isKey: isBookQueryFacetKey,
    labelKey: registeredFacetLabelKey,
    openByDefault: OPEN_RAIL_FACETS,
    kind: key => FACET_FIELDS.get(key)?.facet.kind,
    valueOrder: key => FACET_FIELDS.get(key)?.facet.order,
    valueDomain: key => FACET_FIELDS.get(key)?.facet.domain,
    valueBuckets: key => FACET_FIELDS.get(key)?.facet.buckets,
    fileSize: key => FACET_FIELDS.get(key)?.facet.fileSize ?? false,
    valueLabel: (key, value) => FACET_FIELDS.get(key)?.facet.valueLabel?.(value, deps) ?? null,
    valueIcon: (key, value) => FACET_FIELDS.get(key)?.facet.valueIcon?.(value, deps) ?? null,
  };
}

export function bookFacetLinks(
  book: BookSummary,
  columnField: string,
): readonly BookFacetLink[] {
  const facet = COLUMNS_BY_KEY.get(columnField)?.facet;
  if (!facet?.bookValues) {
    return [];
  }
  const key = facet.key;
  return facet.bookValues(book).map(value => ({key, value}));
}
