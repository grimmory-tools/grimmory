import {type BrowseSortDirection} from '../../core/data/browse.models';
import {parseSortTermsToken} from '../../core/data/browse-query-params';
import {type IconSelection} from '../icons/icon-selection';
import {type BrowseFacetDefinitions, type BrowseFacetShape} from './facets';
import {type BrowseSortDefinitions, type BrowseSortField, type BrowseSortKind} from './sort';

export type BrowseFacetField<FacetKey extends string, Deps> = BrowseFacetShape & {
  readonly key: FacetKey;
  readonly openByDefault?: true;
  readonly valueLabel?: (value: string, deps: Deps) => string | null;
  readonly valueIcon?: (value: string, deps: Deps) => IconSelection | null;
};

export interface BrowseSortFieldDef<SortKey extends string> {
  readonly key: SortKey;
  readonly group: 'common' | 'more';
  readonly defaultDirection: BrowseSortDirection;
  readonly kind: BrowseSortKind;
  readonly directions?: readonly BrowseSortDirection[];
}

export interface BrowseField<FacetKey extends string, SortKey extends string, Deps> {
  readonly labelKey: string;
  readonly facet?: BrowseFacetField<FacetKey, Deps>;
  readonly sort?: BrowseSortFieldDef<SortKey>;
}

export interface BrowseFieldKeys<FacetKey extends string, SortKey extends string> {
  readonly facetKeys: readonly FacetKey[];
  readonly sortKeys: readonly SortKey[];
  readonly isFacetKey: (key: string) => key is FacetKey;
  readonly isSortKey: (key: string) => key is SortKey;
}

type FacetKeyOf<Field> = Field extends {readonly facet: {readonly key: infer Key}} ? Key : never;
type SortKeyOf<Field> = Field extends {readonly sort: {readonly key: infer Key}} ? Key : never;
export type BrowseMissingFieldKeys<Field, FacetKey extends string, SortKey extends string> =
  Exclude<FacetKey, FacetKeyOf<Field>> | Exclude<SortKey, SortKeyOf<Field>>;

export function browseFieldIndex<
  Field extends BrowseField<FacetKey, SortKey, Deps>,
  FacetKey extends string,
  SortKey extends string,
  Deps,
>(
  fields: readonly Field[],
  keys: BrowseFieldKeys<FacetKey, SortKey>,
) {
  const facetFields = new Map(fields.flatMap(({labelKey, facet}) =>
    facet ? [[facet.key, {labelKey, facet}] as const] : []));
  const sortFields = new Map(fields.flatMap(field =>
    field.sort ? [[field.sort.key, {...field, sort: field.sort}] as const] : []));

  function fieldForSort(key: SortKey) {
    // SAFETY: every sort key has a field; each fields file checks this at compile time with BrowseMissingFieldKeys.
    return sortFields.get(key)!;
  }

  function sortField(key: SortKey): BrowseSortField {
    const {labelKey, sort: {group, defaultDirection, kind, directions}} = fieldForSort(key);
    return {labelKey, group, defaultDirection, kind, directions};
  }

  const sortDefinitions: BrowseSortDefinitions<SortKey> = {
    order: keys.sortKeys,
    field: sortField,
    parseToken: token => parseSortTermsToken(token, keys.isSortKey),
  };

  return {
    facetKeys: keys.facetKeys,
    isFacetKey: keys.isFacetKey,
    isSortKey: keys.isSortKey,
    fieldForSort,
    openRailFacets: new Set([...facetFields.values()].flatMap(({facet}) => facet.openByDefault ? [facet.key] : [])),
    sortDefinitions,
    sortField,

    facetDefinitions(deps: Deps): BrowseFacetDefinitions<FacetKey> {
      return {
        order: keys.facetKeys,
        isKey: keys.isFacetKey,
        field: key => {
          // SAFETY: every facet key has a field; each fields file checks this at compile time with BrowseMissingFieldKeys.
          const {labelKey, facet} = facetFields.get(key)!;
          return {...facet, labelKey};
        },
        valueLabel: (key, value) => facetFields.get(key)?.facet.valueLabel?.(value, deps) ?? null,
        valueIcon: (key, value) => facetFields.get(key)?.facet.valueIcon?.(value, deps) ?? null,
      };
    },
  };
}
