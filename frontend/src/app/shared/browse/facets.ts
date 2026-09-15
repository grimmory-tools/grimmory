import {type BrowseFacetGroup} from '../../core/data/browse.models';
import {type IconSelection} from '../icons/icon-selection';
import {
  bucketRangeTokens,
  formatRangeLabel,
  formatRangeToken,
  parseRangeToken,
  type BrowseFacetBucket,
} from './facet-ranges';

export interface BrowseFilterValue {
  value: string;
  label: string;
  count: number;
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
  defaultOpen: boolean;
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
  definitions: BrowseFacetDefinitions<K>,
): BrowseFacetSelection<K> {
  const clamp = (value: number | null) => (value == null ? null : Math.max(0, value));
  const bucketTokens = bucketRangeTokens(definitions.valueBuckets?.(key));
  const kept = browseFacetValues(selection, key).filter(value => bucketTokens.has(value));
  const token = formatRangeToken({min: clamp(min), max: clamp(max)});
  return withBrowseFacetValues(selection, key, token == null ? kept : [...kept, token]);
}

export interface BrowseFacetDefinitions<K extends string> {
  readonly order: readonly K[];
  readonly isKey: (key: string) => key is K;
  readonly labelKey: (key: K) => string;
  readonly openByDefault: ReadonlySet<K>;
  readonly kind?: (key: K) => BrowseFacetKind | undefined;
  readonly valueOrder?: (key: K) => BrowseFacetValueOrder | undefined;
  readonly valueDomain?: (key: K) => readonly string[] | undefined;
  readonly valueBuckets?: (key: K) => readonly BrowseFacetBucket[] | undefined;
  readonly fileSize?: (key: K) => boolean;
  readonly valueLabel?: (key: K, value: string) => string | null;
  readonly valueIcon?: (key: K, value: string) => IconSelection | null;
}

interface BrowseFrozenFacetValue {
  readonly value: string;
  readonly label: string;
}

export type BrowseFrozenFacetOrders = Readonly<Partial<Record<string, readonly BrowseFrozenFacetValue[]>>>;

export function browseFrozenFacetOrders<K extends string>(
  served: readonly BrowseFacetGroup[],
  definitions: BrowseFacetDefinitions<K>,
): BrowseFrozenFacetOrders {
  const entries: [string, readonly BrowseFrozenFacetValue[]][] = [];
  for (const group of served) {
    const key = group.key;
    if (!definitions.isKey(key)) {
      continue;
    }
    const values = group.values.map(value => ({
      value: value.value,
      label: definitions.valueLabel?.(key, value.value) ?? value.title,
    }));
    entries.push([key, values]);
  }
  return Object.fromEntries(entries);
}

function orderedBrowseFacetKeys<K extends string>(
  served: readonly BrowseFacetGroup[],
  frozen: BrowseFrozenFacetOrders | undefined,
  definitions: BrowseFacetDefinitions<K>,
): K[] {
  const available = new Set<string>(Object.keys(frozen ?? {}));
  served.forEach(group => available.add(group.key));
  return definitions.order.filter(key => available.has(key));
}

export function browseFilterGroups<K extends string>(
  served: readonly BrowseFacetGroup[],
  frozen: BrowseFrozenFacetOrders | undefined,
  definitions: BrowseFacetDefinitions<K>,
  selections: BrowseFacetSelection<K>,
): BrowseFilterGroup<K>[] {
  const servedByKey = new Map(served.map(group => [group.key, group]));
  return orderedBrowseFacetKeys(served, frozen, definitions).flatMap(key => {
    const group = buildFacetGroup(key, servedByKey.get(key), frozen?.[key], selections[key] ?? [], definitions);
    return group ? [group] : [];
  });
}

function buildFacetGroup<K extends string>(
  key: K,
  servedGroup: BrowseFacetGroup | undefined,
  frozenValues: readonly BrowseFrozenFacetValue[] | undefined,
  selected: readonly string[],
  definitions: BrowseFacetDefinitions<K>,
): BrowseFilterGroup<K> | null {
  const servedValues = servedGroup?.values ?? [];
  const kind = definitions.kind?.(key);
  const buckets = definitions.valueBuckets?.(key);
  const domain = definitions.valueDomain?.(key);
  const hasData = (frozenValues?.length ?? 0) > 0 || servedValues.length > 0 || selected.length > 0;
  if (!hasData) {
    return null;
  }
  const label = (value: string, servedLabel: string) => definitions.valueLabel?.(key, value) ?? servedLabel;
  const base = {key, labelKey: definitions.labelKey(key), defaultOpen: definitions.openByDefault.has(key)};
  const range = kind === 'range'
    ? buildFacetRange(servedValues, selected, buckets, definitions.fileSize?.(key) ?? false)
    : undefined;
  if (buckets) {
    return {...base, range, showAllValues: true, values: bucketFacetValues(buckets, servedValues, selected, label)};
  }
  if (kind === 'range') {
    return {...base, range, showAllValues: false, values: []};
  }
  const values = plainFacetValues(
    servedValues, frozenValues, selected, label, value => definitions.valueIcon?.(key, value) ?? undefined,
  );
  if (domain) {
    return {...base, showAllValues: true, values: applyDomain(values, domain, label)};
  }
  const ordered = orderFacetValues(values, definitions.valueOrder?.(key), frozenValues);
  return {...base, showAllValues: false, values: ordered};
}

function buildFacetRange(
  servedValues: BrowseFacetGroup['values'],
  selected: readonly string[],
  buckets: readonly BrowseFacetBucket[] | undefined,
  fileSize: boolean,
): BrowseFilterRange {
  const numeric = servedValues
    .map(item => Number(item.value))
    .filter(value => Number.isFinite(value));
  const bucketTokens = bucketRangeTokens(buckets);
  const token = selected.find(value => !bucketTokens.has(value));
  const parsed = token != null ? parseRangeToken(token) : null;
  return {
    min: parsed?.min ?? null,
    max: parsed?.max ?? null,
    boundsMin: numeric.length > 0 ? Math.floor(Math.min(...numeric)) : null,
    boundsMax: numeric.length > 0 ? Math.ceil(Math.max(...numeric)) : null,
    fileSize,
  };
}

function bucketFacetValues(
  buckets: readonly BrowseFacetBucket[],
  servedValues: BrowseFacetGroup['values'],
  selected: readonly string[],
  label: (value: string, servedLabel: string) => string,
): BrowseFilterValue[] {
  const selectedSet = new Set(selected);
  const starScale = Math.max(0, ...buckets.map(bucket => bucket.stars ?? 0));
  return buckets.flatMap(bucket => {
    const value = formatRangeToken(bucket);
    const bucketLabel = formatRangeLabel(bucket);
    if (value === null || bucketLabel === null) {
      return [];
    }
    return [{
      value,
      label: label(value, bucketLabel),
      count: countWithinBucket(servedValues, bucket),
      selected: selectedSet.has(value),
      stars: bucket.stars != null ? {value: bucket.stars, max: starScale} : undefined,
    }];
  });
}

function countWithinBucket(servedValues: BrowseFacetGroup['values'], bucket: BrowseFacetBucket): number {
  return servedValues.reduce((sum, item) => {
    const parsed = Number(item.value);
    if (Number.isNaN(parsed)) return sum;
    if (bucket.min != null && parsed < bucket.min) return sum;
    if (bucket.max != null && parsed > bucket.max) return sum;
    return sum + item.count;
  }, 0);
}

function knownFacetLabels(
  servedValues: BrowseFacetGroup['values'],
  frozenValues: readonly BrowseFrozenFacetValue[] | undefined,
): Map<string, string> {
  const labelsByValue = new Map<string, string>();
  for (const option of frozenValues ?? []) {
    labelsByValue.set(option.value, option.label);
  }
  for (const option of servedValues) {
    if (!labelsByValue.has(option.value)) {
      labelsByValue.set(option.value, option.title);
    }
  }
  return labelsByValue;
}

function plainFacetValues(
  servedValues: BrowseFacetGroup['values'],
  frozenValues: readonly BrowseFrozenFacetValue[] | undefined,
  selected: readonly string[],
  label: (value: string, servedLabel: string) => string,
  icon: (value: string) => IconSelection | undefined,
): BrowseFilterValue[] {
  const selectedValues = new Set(selected);
  const countsByValue = new Map(servedValues.map(option => [option.value, option.count]));
  const labelsByValue = knownFacetLabels(servedValues, frozenValues);
  for (const value of selectedValues) {
    if (!labelsByValue.has(value)) {
      labelsByValue.set(value, value);
    }
  }
  return Array.from(labelsByValue, ([value, title]) => ({
    value,
    label: label(value, title),
    icon: icon(value),
    count: countsByValue.get(value) ?? 0,
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

function orderFacetValues(
  values: BrowseFilterValue[],
  order: BrowseFacetValueOrder | undefined,
  frozenValues: readonly BrowseFrozenFacetValue[] | undefined,
): BrowseFilterValue[] {
  const ordered = order ? sortFacetValues(values, order) : values;
  const sinkUnavailable = frozenValues !== undefined && order === undefined;
  const zeroCountSelections: BrowseFilterValue[] = [];
  const remainingValues: BrowseFilterValue[] = [];
  const unavailableValues: BrowseFilterValue[] = [];
  for (const item of ordered) {
    if (item.selected && item.count === 0) {
      zeroCountSelections.push(item);
    } else if (sinkUnavailable && item.count === 0) {
      unavailableValues.push(item);
    } else {
      remainingValues.push(item);
    }
  }
  return [...zeroCountSelections, ...remainingValues, ...unavailableValues];
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
  frozen: BrowseFrozenFacetOrders | undefined,
  definitions: BrowseFacetDefinitions<K>,
  selections: BrowseFacetSelection<K>,
): BrowseFilterChip<K>[] {
  const servedByKey = new Map(served.map(group => [group.key, group]));
  const definedKeys = orderedBrowseFacetKeys(served, frozen, definitions);
  const definedKeySet = new Set<string>(definedKeys);
  const selectionKeys = Object.keys(selections).filter(definitions.isKey);
  const keys = [
    ...definedKeys,
    ...selectionKeys.filter(key => !definedKeySet.has(key)),
  ];
  return keys.flatMap(key => {
    const values = selections[key] ?? [];
    if (values.length === 0) {
      return [];
    }
    const frozenValues = frozen?.[key] ?? [];
    const frozenIndex = new Map(frozenValues.map((item, index) => [item.value, index]));
    const labels = knownFacetLabels(servedByKey.get(key)?.values ?? [], frozenValues);
    return [...values]
      .sort((a, b) => {
        const indexA = frozenIndex.get(a) ?? Number.MAX_SAFE_INTEGER;
        const indexB = frozenIndex.get(b) ?? Number.MAX_SAFE_INTEGER;
        return indexA - indexB || a.localeCompare(b);
      })
      .map(value => ({
        key,
        value,
        groupLabelKey: definitions.labelKey(key),
        valueLabel: definitions.valueLabel?.(key, value) ?? labels.get(value) ?? value,
      }));
  });
}
