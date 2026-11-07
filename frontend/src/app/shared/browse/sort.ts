import {
  LucideArrowDown10,
  LucideArrowDownZA,
  LucideArrowUp01,
  LucideArrowUpAZ,
  LucideCalendarArrowDown,
  LucideCalendarArrowUp,
  LucideClockArrowDown,
  LucideClockArrowUp,
  LucideShuffle,
  type LucideIconData,
} from '@lucide/angular';

import {type BrowseSortDirection, type BrowseSortTerm} from '../../core/data/browse.models';

export type BrowseSortKind = 'alphabetical' | 'numeric' | 'calendar' | 'clock' | 'random';

export interface BrowseSortField {
  readonly labelKey: string;
  readonly group: 'common' | 'more';
  readonly defaultDirection: BrowseSortDirection;
  readonly kind: BrowseSortKind;
  readonly directions?: readonly BrowseSortDirection[];
}

export interface BrowseSortCriterion {
  field: string;
  direction: 'ASC' | 'DESC';
}

export interface BrowseSortOption<Key extends string = string> extends BrowseSortField {
  readonly id: Key;
  readonly directions: readonly BrowseSortDirection[];
}

export interface BrowseSortDefinitions<Key extends string> {
  readonly order: readonly Key[];
  readonly field: (key: Key) => BrowseSortField;
  readonly parseToken: (token: string | null) => readonly BrowseSortTerm<Key>[];
}

function browseSortOption<Key extends string>(
  key: Key,
  directions: readonly BrowseSortDirection[],
  definitions: BrowseSortDefinitions<Key>,
): BrowseSortOption<Key> {
  const field = definitions.field(key);
  return {
    ...field,
    id: key,
    defaultDirection: directions.includes(field.defaultDirection) ? field.defaultDirection : directions[0],
    directions,
  };
}

export function browseSortOptions<Key extends string>(
  serverSortTokens: readonly string[],
  definitions: BrowseSortDefinitions<Key>,
): BrowseSortOption<Key>[] {
  const directionsByKey = new Map<Key, BrowseSortDirection[]>();
  for (const token of serverSortTokens) {
    const term = definitions.parseToken(token).at(0);
    if (!term) {
      continue;
    }
    const directions = directionsByKey.get(term.key) ?? [];
    directions.push(term.direction);
    directionsByKey.set(term.key, directions);
  }
  return definitions.order.flatMap(key => {
    const allowed = definitions.field(key).directions;
    const directions = directionsByKey.get(key)
      ?.filter(direction => allowed === undefined || allowed.includes(direction));
    return directions?.length ? [browseSortOption(key, directions, definitions)] : [];
  });
}

export function browseSortTermsFromCriteria<Key extends string>(
  criteria: readonly BrowseSortCriterion[],
  isKey: (value: string) => value is Key,
): BrowseSortTerm<Key>[] {
  return criteria.flatMap(criterion => isKey(criterion.field)
    ? [{key: criterion.field, direction: criterion.direction === 'DESC' ? 'desc' : 'asc'}]
    : []);
}

export function browseSortCriteria(terms: readonly BrowseSortTerm[]): BrowseSortCriterion[] {
  return terms.map(term => ({
    field: term.key,
    direction: term.direction === 'desc' ? 'DESC' : 'ASC',
  }));
}

export function browseSortDirectionIcon(
  kind: BrowseSortKind,
  direction: BrowseSortDirection,
): LucideIconData {
  const ascending = direction === 'asc';

  switch (kind) {
    case 'alphabetical':
      return ascending ? LucideArrowUpAZ.icon : LucideArrowDownZA.icon;
    case 'numeric':
      return ascending ? LucideArrowUp01.icon : LucideArrowDown10.icon;
    case 'calendar':
      return ascending ? LucideCalendarArrowUp.icon : LucideCalendarArrowDown.icon;
    case 'clock':
      return ascending ? LucideClockArrowUp.icon : LucideClockArrowDown.icon;
    case 'random':
      return LucideShuffle.icon;
  }
}
