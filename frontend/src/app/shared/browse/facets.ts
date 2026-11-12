import {type BrowseFacetGroup} from '../../core/data/browse.models';
import {type IconSelection} from '../icons/icon-selection';
import {formatRangeLabel, formatRangeToken, parseRangeToken} from './facet-ranges';

export type BrowseFacetState = 'included' | 'excluded';

export interface BrowseFilterValue {
  value: string;
  label: string;
  count: number | null;
  stars?: {value: number; max: number};
  icon?: IconSelection;
}

export interface BrowseFilterRange {
  min: number | null;
  max: number | null;
  boundsMin: number | null;
  boundsMax: number | null;
  fileSize: boolean;
}

export interface BrowseFilterGroup<K extends string = string> {
  key: K;
  labelKey: string;
  showAllValues?: boolean;
  range?: BrowseFilterRange;
  loading?: boolean;
  picks?: ReadonlyMap<string, BrowseFacetState>;
  multiValued: boolean;
  matchAll?: boolean;
  values: BrowseFilterValue[];
}

export interface BrowseFilterToggle<K extends string = string> {
  key: K;
  value: string;
  state: BrowseFacetState | null;
}

export interface BrowseFilterMatchAll<K extends string = string> {
  key: K;
  matchAll: boolean;
}

export interface BrowseFilterRangeCommit<K extends string = string> {
  key: K;
  min: number | null;
  max: number | null;
}

export interface BrowseFilterOpen<K extends string = string> {
  key: K;
  open: boolean;
}

export interface BrowseFilterSearch<K extends string = string> {
  key: K;
  term: string;
}

export interface BrowseFacetValues {
  readonly include: readonly string[];
  readonly exclude: readonly string[];
  readonly matchAll?: true;
}

export type BrowseFacetSelection<K extends string = string> = Readonly<Partial<Record<K, BrowseFacetValues>>>;

export type BrowseFacetValueOrder = 'asc' | 'desc' | readonly string[];

export type BrowseFacetKind = 'range';

const EMPTY_FACET_VALUES: BrowseFacetValues = {include: [], exclude: []};

export function browseFacetValues<K extends string>(selection: BrowseFacetSelection<K>, key: K): BrowseFacetValues {
  return selection[key] ?? EMPTY_FACET_VALUES;
}

export function withBrowseFacetValues<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  {include, exclude, matchAll}: {readonly include: readonly string[]; readonly exclude: readonly string[]; readonly matchAll?: boolean},
): BrowseFacetSelection<K> {
  const next: Partial<Record<K, BrowseFacetValues>> = {...selection};
  if (include.length > 0 || exclude.length > 0) {
    next[key] = matchAll && include.length > 0 ? {include, exclude, matchAll: true} : {include, exclude};
  } else {
    delete next[key];
  }
  return next;
}

export function browseFacetEntries<K extends string>(
  selection: BrowseFacetSelection<K>,
  isKey: (key: string) => key is K,
): [K, BrowseFacetValues][] {
  return Object.keys(selection).filter(isKey).flatMap<[K, BrowseFacetValues]>(key => {
    const values = selection[key];
    return values ? [[key, values]] : [];
  });
}

export function browseFacetPicks(values: BrowseFacetValues): ReadonlyMap<string, BrowseFacetState> {
  return new Map([
    ...values.include.map(value => [value, 'included'] as const),
    ...values.exclude.map(value => [value, 'excluded'] as const),
  ]);
}

export function withBrowseFacetState<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  value: string,
  state: BrowseFacetState | null,
  matchAll: boolean,
): BrowseFacetSelection<K> {
  const current = browseFacetValues(selection, key);
  const include = current.include.filter(item => item !== value);
  const exclude = current.exclude.filter(item => item !== value);
  return withBrowseFacetValues(selection, key, {
    include: state === 'included' ? [...include, value] : include,
    exclude: state === 'excluded' ? [...exclude, value] : exclude,
    matchAll,
  });
}

export function withBrowseFacetMatchAll<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  matchAll: boolean,
): BrowseFacetSelection<K> {
  return withBrowseFacetValues(selection, key, {...browseFacetValues(selection, key), matchAll});
}

export function countBrowseFacetValues(selection: BrowseFacetSelection): number {
  return Object.values(selection).reduce((count, values) => count + (values ? values.include.length + values.exclude.length : 0), 0);
}

export function withBrowseFacetRange<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  min: number | null,
  max: number | null,
  bandTokens: ReadonlySet<string>,
): BrowseFacetSelection<K> {
  const clamp = (value: number | null) => (value == null ? null : Math.max(0, value));
  const current = browseFacetValues(selection, key);
  const kept = current.include.filter(value => bandTokens.has(value));
  const token = formatRangeToken({min: clamp(min), max: clamp(max)});
  return withBrowseFacetValues(selection, key, {...current, include: token == null ? kept : [...kept, token]});
}

export interface BrowseFacetDefinitions<K extends string> {
  readonly order: readonly K[];
  readonly isKey: (key: string) => key is K;
  readonly labelKey: (key: K) => string;
  readonly kind?: (key: K) => BrowseFacetKind | undefined;
  readonly valueOrder?: (key: K) => BrowseFacetValueOrder | undefined;
  readonly valueDomain?: (key: K) => readonly string[] | undefined;
  readonly banded?: (key: K) => boolean;
  readonly multiValued?: (key: K) => boolean;
  readonly starScale?: (key: K) => number | undefined;
  readonly fileSize?: (key: K) => boolean;
  readonly valueLabel?: (key: K, value: string) => string | null;
  readonly valueIcon?: (key: K, value: string) => IconSelection | null;
}

export function browseFilterGroups<K extends string>(
  available: ReadonlySet<string>,
  served: readonly BrowseFacetGroup[],
  definitions: BrowseFacetDefinitions<K>,
  selections: BrowseFacetSelection<K>,
): BrowseFilterGroup<K>[] {
  const servedByKey = new Map(served.map(group => [group.key, group]));
  return definitions.order
    .filter(key => available.has(key) || selections[key] !== undefined)
    .map(key => buildFacetGroup(key, servedByKey.get(key), browseFacetValues(selections, key), definitions));
}

function buildFacetGroup<K extends string>(
  key: K,
  servedGroup: BrowseFacetGroup | undefined,
  selected: BrowseFacetValues,
  definitions: BrowseFacetDefinitions<K>,
): BrowseFilterGroup<K> {
  const picks = browseFacetPicks(selected);
  const servedValues = servedGroup?.values ?? [];
  const kind = definitions.kind?.(key);
  const domain = definitions.valueDomain?.(key);
  const label = (value: string, servedLabel: string) => definitions.valueLabel?.(key, value) ?? servedLabel;
  const base = {key, labelKey: definitions.labelKey(key), multiValued: definitions.multiValued?.(key) ?? false};
  const range = kind === 'range'
    ? buildFacetRange(servedGroup, selected.include, definitions.fileSize?.(key) ?? false)
    : undefined;
  if (definitions.banded?.(key)) {
    const values = bandFacetValues(servedValues, label, definitions.starScale?.(key));
    return {...base, range, showAllValues: true, values};
  }
  if (kind === 'range') {
    return {...base, range, showAllValues: false, values: []};
  }
  const counts = new Map(servedValues.map(item => [item.value, item.count]));
  const complete = servedGroup?.complete ?? false;
  const values = plainFacetValues(
    servedValues, picks, label,
    value => definitions.valueIcon?.(key, value) ?? undefined,
    value => counts.get(value) ?? (complete || picks.get(value) === 'excluded' ? 0 : null),
  );
  if (domain) {
    return {...base, showAllValues: true, values: applyDomain(values, domain, label)};
  }
  const order = definitions.valueOrder?.(key);
  return {...base, showAllValues: false, values: order ? sortFacetValues(values, order) : values};
}

function buildFacetRange(
  servedGroup: BrowseFacetGroup | undefined,
  selected: readonly string[],
  fileSize: boolean,
): BrowseFilterRange {
  const bandTokens = new Set(servedGroup?.values.map(item => item.value));
  const token = selected.find(value => !bandTokens.has(value));
  const parsed = token != null ? parseRangeToken(token) : null;
  return {
    min: parsed?.min ?? null,
    max: parsed?.max ?? null,
    boundsMin: servedGroup?.min != null ? Math.floor(servedGroup.min) : null,
    boundsMax: servedGroup?.max != null ? Math.ceil(servedGroup.max) : null,
    fileSize,
  };
}

function bandFacetValues(
  servedValues: BrowseFacetGroup['values'],
  label: (value: string, servedLabel: string) => string,
  starScale: number | undefined,
): BrowseFilterValue[] {
  return servedValues.map(item => {
    const range = parseRangeToken(item.value);
    return {
      value: item.value,
      label: label(item.value, (range && formatRangeLabel(range)) ?? item.title),
      count: item.count,
      stars: starScale != null ? {value: range?.max ?? starScale, max: starScale} : undefined,
    };
  });
}

function plainFacetValues(
  servedValues: BrowseFacetGroup['values'],
  picks: ReadonlyMap<string, BrowseFacetState>,
  label: (value: string, servedLabel: string) => string,
  icon: (value: string) => IconSelection | undefined,
  count: (value: string) => number | null,
): BrowseFilterValue[] {
  const labelsByValue = new Map(servedValues.map(option => [option.value, option.title]));
  for (const value of picks.keys()) {
    if (!labelsByValue.has(value)) {
      labelsByValue.set(value, value);
    }
  }
  return Array.from(labelsByValue, ([value, title]) => ({
    value,
    label: label(value, title),
    icon: icon(value),
    count: count(value),
  }));
}

function applyDomain(
  values: BrowseFilterValue[],
  domain: readonly string[],
  label: (value: string, servedLabel: string) => string,
): BrowseFilterValue[] {
  const byValue = new Map(values.map(item => [item.value, item]));
  return domain.map(value => byValue.get(value) ?? {value, label: label(value, value), count: 0});
}

function sortFacetValues(values: BrowseFilterValue[], order: BrowseFacetValueOrder): BrowseFilterValue[] {
  if (order === 'asc' || order === 'desc') {
    const direction = order === 'asc' ? 1 : -1;
    return [...values].sort((a, b) =>
      direction * (numericFacetValue(a.value) - numericFacetValue(b.value)));
  }
  const position = new Map(order.map((value, index) => [value, index]));
  return [...values].sort((a, b) =>
    (position.get(a.value) ?? order.length) - (position.get(b.value) ?? order.length));
}

function numericFacetValue(value: string): number {
  const parsed = Number(value);
  return Number.isNaN(parsed) ? Number.MAX_VALUE : parsed;
}

export interface BrowseFilterChip<K extends string = string> {
  readonly key: K;
  readonly value: string;
  readonly excluded: boolean;
  readonly groupLabelKey: string;
  readonly valueLabel: string;
}

export function browseFilterChips<K extends string>(
  served: readonly BrowseFacetGroup[],
  definitions: BrowseFacetDefinitions<K>,
  selections: BrowseFacetSelection<K>,
): BrowseFilterChip<K>[] {
  const servedByKey = new Map(served.map(group => [group.key, group]));
  return browseFacetEntries(selections, definitions.isKey).flatMap(([key, values]) => {
    const labels = new Map(servedByKey.get(key)?.values.map(option => [option.value, option.title]));
    return [...browseFacetPicks(values)].map(([value, state]) => ({
      key,
      value,
      excluded: state === 'excluded',
      groupLabelKey: definitions.labelKey(key),
      valueLabel: definitions.valueLabel?.(key, value) ?? labels.get(value) ?? value,
    }));
  });
}
