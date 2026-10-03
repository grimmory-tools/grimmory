import {type BrowseFacetGroup} from '../../core/data/browse.models';
import {type IconSelection} from '../icons/icon-selection';
import {formatRangeLabel, formatRangeToken, parseRangeToken} from './facet-ranges';

export interface BrowseFilterValue {
  value: string;
  label: string;
  count: number | null;
  selected: boolean;
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
  picks?: ReadonlySet<string>;
  values: BrowseFilterValue[];
}

export interface BrowseFilterToggle<K extends string = string> {
  key: K;
  value: string;
  selected: boolean;
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

export type BrowseFacetSelection<K extends string = string> =
  Readonly<Partial<Record<K, readonly string[]>>>;

export type BrowseFacetValueOrder = 'asc' | 'desc' | readonly string[];

export type BrowseFacetKind = 'range';

export function browseFacetValues<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
): readonly string[] {
  return selection[key] ?? [];
}

export function withBrowseFacetValues<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  values: readonly string[],
): BrowseFacetSelection<K> {
  const next: Partial<Record<K, readonly string[]>> = {...selection};
  if (values.length > 0) {
    next[key] = values;
  } else {
    delete next[key];
  }
  return next;
}

export function toggleBrowseFacetValue<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  value: string,
  selected: boolean,
): BrowseFacetSelection<K> {
  const values = browseFacetValues(selection, key);
  if (selected === values.includes(value)) {
    return selection;
  }
  return withBrowseFacetValues(selection, key, selected ? [...values, value] : values.filter(item => item !== value));
}

export function countBrowseFacetValues(selection: BrowseFacetSelection): number {
  return Object.values(selection).reduce((count, values) => count + (values?.length ?? 0), 0);
}

export function pinBrowseFacetValue<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  value: string,
): BrowseFacetSelection<K> {
  return {...selection, [key]: [value]};
}

export function withBrowseFacetRange<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  min: number | null,
  max: number | null,
  bandTokens: ReadonlySet<string>,
): BrowseFacetSelection<K> {
  const clamp = (value: number | null) => (value == null ? null : Math.max(0, value));
  const kept = browseFacetValues(selection, key).filter(value => bandTokens.has(value));
  const token = formatRangeToken({min: clamp(min), max: clamp(max)});
  return withBrowseFacetValues(selection, key, token == null ? kept : [...kept, token]);
}

export interface BrowseFacetDefinitions<K extends string> {
  readonly order: readonly K[];
  readonly isKey: (key: string) => key is K;
  readonly labelKey: (key: K) => string;
  readonly kind?: (key: K) => BrowseFacetKind | undefined;
  readonly valueOrder?: (key: K) => BrowseFacetValueOrder | undefined;
  readonly valueDomain?: (key: K) => readonly string[] | undefined;
  readonly banded?: (key: K) => boolean;
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
    .filter(key => available.has(key) || (selections[key]?.length ?? 0) > 0)
    .map(key => buildFacetGroup(key, servedByKey.get(key), selections[key] ?? [], definitions));
}

function buildFacetGroup<K extends string>(
  key: K,
  servedGroup: BrowseFacetGroup | undefined,
  selected: readonly string[],
  definitions: BrowseFacetDefinitions<K>,
): BrowseFilterGroup<K> {
  const servedValues = servedGroup?.values ?? [];
  const kind = definitions.kind?.(key);
  const domain = definitions.valueDomain?.(key);
  const label = (value: string, servedLabel: string) => definitions.valueLabel?.(key, value) ?? servedLabel;
  const base = {key, labelKey: definitions.labelKey(key)};
  const range = kind === 'range'
    ? buildFacetRange(servedGroup, selected, definitions.fileSize?.(key) ?? false)
    : undefined;
  if (definitions.banded?.(key)) {
    const values = bandFacetValues(servedValues, selected, label, definitions.starScale?.(key));
    return {...base, range, showAllValues: true, values};
  }
  if (kind === 'range') {
    return {...base, range, showAllValues: false, values: []};
  }
  const counts = new Map(servedValues.map(item => [item.value, item.count]));
  const complete = servedGroup?.complete ?? false;
  const values = plainFacetValues(
    servedValues, selected, label,
    value => definitions.valueIcon?.(key, value) ?? undefined,
    value => counts.get(value) ?? (complete ? 0 : null),
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
  selected: readonly string[],
  label: (value: string, servedLabel: string) => string,
  starScale: number | undefined,
): BrowseFilterValue[] {
  const selectedSet = new Set(selected);
  return servedValues.map(item => {
    const range = parseRangeToken(item.value);
    return {
      value: item.value,
      label: label(item.value, (range && formatRangeLabel(range)) ?? item.title),
      count: item.count,
      selected: selectedSet.has(item.value),
      stars: starScale != null ? {value: range?.max ?? starScale, max: starScale} : undefined,
    };
  });
}

function plainFacetValues(
  servedValues: BrowseFacetGroup['values'],
  selected: readonly string[],
  label: (value: string, servedLabel: string) => string,
  icon: (value: string) => IconSelection | undefined,
  count: (value: string) => number | null,
): BrowseFilterValue[] {
  const selectedValues = new Set(selected);
  const labelsByValue = new Map(servedValues.map(option => [option.value, option.title]));
  for (const value of selectedValues) {
    if (!labelsByValue.has(value)) {
      labelsByValue.set(value, value);
    }
  }
  return Array.from(labelsByValue, ([value, title]) => ({
    value,
    label: label(value, title),
    icon: icon(value),
    count: count(value),
    selected: selectedValues.has(value),
  }));
}

function applyDomain(
  values: BrowseFilterValue[],
  domain: readonly string[],
  label: (value: string, servedLabel: string) => string,
): BrowseFilterValue[] {
  const byValue = new Map(values.map(item => [item.value, item]));
  return domain.map(value => byValue.get(value) ?? {value, label: label(value, value), count: 0, selected: false});
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
  readonly groupLabelKey: string;
  readonly valueLabel: string;
}

export function browseFilterChips<K extends string>(
  served: readonly BrowseFacetGroup[],
  definitions: BrowseFacetDefinitions<K>,
  selections: BrowseFacetSelection<K>,
): BrowseFilterChip<K>[] {
  const servedByKey = new Map(served.map(group => [group.key, group]));
  const keys = Object.keys(selections).filter(definitions.isKey);
  return keys.flatMap(key => {
    const values = selections[key] ?? [];
    const labels = new Map(servedByKey.get(key)?.values.map(option => [option.value, option.title]));
    return values.map(value => ({
      key,
      value,
      groupLabelKey: definitions.labelKey(key),
      valueLabel: definitions.valueLabel?.(key, value) ?? labels.get(value) ?? value,
    }));
  });
}
