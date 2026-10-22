import {type BrowseFacetGroup} from '../../core/data/browse.models';
import {type IconSelection} from '../icons/icon-selection';
import {formatRangeLabel, formatRangeToken, parseRangeToken} from './facet-ranges';

export type BrowseFacetState = 'included' | 'excluded';

export interface BrowseFilterValue<K extends string = string> {
  facetKey: K;
  value: string;
  label: string;
  labelKey?: string;
  count: number | null;
  state: BrowseFacetState | null;
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
  picks?: ReadonlyMap<K, ReadonlyMap<string, BrowseFacetState>>;
  values: BrowseFilterValue<K>[];
}

export interface BrowseFilterToggle<K extends string = string> {
  key: K;
  value: string;
  state: BrowseFacetState | null;
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

export type BrowseFacetKey<K extends string = string> = K | `+${K}` | `-${K}`;

export type BrowseFacetSelection<K extends string = string> =
  Readonly<Partial<Record<BrowseFacetKey<K>, readonly string[]>>>;

export type BrowseFacetValueOrder = 'asc' | 'desc' | readonly string[];

export type BrowseFacetShape =
  | {
    readonly kind?: 'list';
    readonly order?: BrowseFacetValueOrder;
    readonly domain?: never;
    readonly stars?: never;
    readonly bands?: never;
    readonly fileSize?: never;
  }
  | {readonly kind: 'domain'; readonly domain: readonly string[]}
  | {readonly kind: 'stars'; readonly stars: number}
  | {readonly kind: 'range'; readonly bands?: true; readonly fileSize?: true}
  | {readonly kind: 'flag'};

export type BrowseFacetDescription = BrowseFacetShape & {readonly labelKey: string};

export function browseFacetValues<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: BrowseFacetKey<K>,
): readonly string[] {
  return selection[key] ?? [];
}

export function withBrowseFacetValues<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: BrowseFacetKey<K>,
  values: readonly string[],
): BrowseFacetSelection<K> {
  const next: Partial<Record<BrowseFacetKey<K>, readonly string[]>> = {...selection};
  if (values.length > 0) {
    next[key] = values;
  } else {
    delete next[key];
  }
  return next;
}

function includedBrowseFacetValues<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
): readonly string[] {
  return [...browseFacetValues(selection, key), ...browseFacetValues(selection, `+${key}`)];
}

export function hasBrowseFacetValues<K extends string>(selection: BrowseFacetSelection<K>, key: K): boolean {
  return includedBrowseFacetValues(selection, key).length > 0 || browseFacetValues(selection, `-${key}`).length > 0;
}

export function browseFacetPicks<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
): ReadonlyMap<string, BrowseFacetState> {
  return new Map([
    ...includedBrowseFacetValues(selection, key).map(value => [value, 'included'] as const),
    ...browseFacetValues(selection, `-${key}`).map(value => [value, 'excluded'] as const),
  ]);
}

export function browseFacetSelectionKeys<K extends string>(selection: BrowseFacetSelection<K>): BrowseFacetKey<K>[] {
  // SAFETY: a selection only holds BrowseFacetKey<K> keys, because every writer takes a typed key and parseFacetParams drops unknown ones.
  return Object.keys(selection) as BrowseFacetKey<K>[];
}

export function unmarkedBrowseFacetKey<K extends string>(marked: BrowseFacetKey<K>): K {
  // SAFETY: removing the + or - mark from a BrowseFacetKey<K> leaves K, which TypeScript cannot work out from slice.
  return (marked.startsWith('+') || marked.startsWith('-') ? marked.slice(1) : marked) as K;
}

function markedBrowseFacetKey<K extends string>(key: K, state: BrowseFacetState, matchAll: boolean): BrowseFacetKey<K> {
  if (state === 'excluded') {
    return `-${key}`;
  }
  return matchAll ? `+${key}` : key;
}

export function setBrowseFacetValue<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  value: string,
  state: BrowseFacetState | null,
  matchAll: boolean,
): BrowseFacetSelection<K> {
  let next = selection;
  for (const marked of [key, `+${key}`, `-${key}`] as const) {
    next = withBrowseFacetValues(next, marked, browseFacetValues(next, marked).filter(item => item !== value));
  }
  if (state === null) {
    return next;
  }
  const target = markedBrowseFacetKey(key, state, matchAll);
  return withBrowseFacetValues(next, target, [...browseFacetValues(next, target), value]);
}

export function browseFacetMatchAll(selection: BrowseFacetSelection): boolean | undefined {
  const keys = Object.keys(selection).filter(key => !key.startsWith('-'));
  if (keys.length === 0) {
    return undefined;
  }
  return keys.some(key => key.startsWith('+'));
}

export function withBrowseFacetMatchAll<K extends string>(
  selection: BrowseFacetSelection<K>,
  matchAll: boolean,
): BrowseFacetSelection<K> {
  let next: Partial<Record<BrowseFacetKey<K>, readonly string[]>> = {};
  for (const marked of browseFacetSelectionKeys(selection)) {
    const values = browseFacetValues(selection, marked);
    if (marked.startsWith('-')) {
      next = withBrowseFacetValues(next, marked, values);
      continue;
    }
    const key = unmarkedBrowseFacetKey(marked);
    const target: BrowseFacetKey<K> = matchAll ? `+${key}` : key;
    const merged = new Set([...browseFacetValues(next, target), ...values]);
    next = withBrowseFacetValues(next, target, [...merged]);
  }
  return next;
}

export function countBrowseFacetValues(selection: BrowseFacetSelection): number {
  return Object.values(selection).reduce((count, values) => count + (values?.length ?? 0), 0);
}

export interface BrowseFacetPin<K extends string = string> {
  readonly facetKey: K;
  readonly facetValue: string;
}

export function pinnedBrowseFacetSelection<K extends string>(
  selection: BrowseFacetSelection<K>,
  pin: BrowseFacetPin<K> | null,
): BrowseFacetSelection<K> {
  return pin ? requireBrowseFacetValue(selection, pin.facetKey, pin.facetValue) : selection;
}

export function requireBrowseFacetValue<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  value: string,
): BrowseFacetSelection<K> {
  const required = browseFacetValues(selection, `+${key}`);
  return required.includes(value) ? selection : withBrowseFacetValues(selection, `+${key}`, [...required, value]);
}

export function withBrowseFacetRange<K extends string>(
  selection: BrowseFacetSelection<K>,
  key: K,
  min: number | null,
  max: number | null,
  bandTokens: ReadonlySet<string>,
  matchAll: boolean,
): BrowseFacetSelection<K> {
  const clamp = (value: number | null) => (value == null ? null : Math.max(0, value));
  let next = selection;
  for (const marked of [key, `+${key}`] as const) {
    next = withBrowseFacetValues(next, marked, browseFacetValues(next, marked).filter(value => bandTokens.has(value)));
  }
  const token = formatRangeToken({min: clamp(min), max: clamp(max)});
  const target = markedBrowseFacetKey(key, 'included', matchAll);
  return token == null ? next : withBrowseFacetValues(next, target, [...browseFacetValues(next, target), token]);
}

export interface BrowseFacetDefinitions<K extends string> {
  readonly order: readonly K[];
  readonly isKey: (key: string) => key is K;
  readonly field: (key: K) => BrowseFacetDescription;
  readonly valueLabel?: (key: K, value: string) => string | null;
  readonly valueIcon?: (key: K, value: string) => IconSelection | null;
}

const FLAG_VALUE = 'true';
const FLAG_SECTION_LABEL_KEY = 'common.metadata';

function isFlagFacet<K extends string>(definitions: BrowseFacetDefinitions<K>, key: K): boolean {
  return definitions.field(key).kind === 'flag';
}

export function browseFacetSectionKeys<K extends string>(definitions: BrowseFacetDefinitions<K>, key: K): readonly K[] {
  return isFlagFacet(definitions, key) ? definitions.order.filter(member => isFlagFacet(definitions, member)) : [key];
}

export function browseFilterGroups<K extends string>(
  available: ReadonlySet<string>,
  served: readonly BrowseFacetGroup[],
  definitions: BrowseFacetDefinitions<K>,
  selections: BrowseFacetSelection<K>,
): BrowseFilterGroup<K>[] {
  const servedByKey = new Map(served.map(group => [group.key, group]));
  const groups = definitions.order
    .filter(key => available.has(key) || hasBrowseFacetValues(selections, key))
    .map(key => buildFacetGroup(key, servedByKey.get(key), browseFacetPicks(selections, key), definitions));
  const flags = groups.filter(group => isFlagFacet(definitions, group.key));
  return groups.flatMap(group => {
    if (!flags.includes(group)) {
      return [group];
    }
    return group === flags[0]
      ? [{
        key: browseFacetSectionKeys(definitions, group.key)[0],
        labelKey: FLAG_SECTION_LABEL_KEY,
        showAllValues: true,
        values: flags.flatMap(flag => flag.values),
      }]
      : [];
  });
}

function buildFacetGroup<K extends string>(
  key: K,
  servedGroup: BrowseFacetGroup | undefined,
  picks: ReadonlyMap<string, BrowseFacetState>,
  definitions: BrowseFacetDefinitions<K>,
): BrowseFilterGroup<K> {
  const state = (value: string) => picks.get(value) ?? null;
  const included = [...picks.keys()].filter(value => picks.get(value) === 'included');
  const servedValues = servedGroup?.values ?? [];
  const field = definitions.field(key);
  const label = (value: string, servedLabel: string) => definitions.valueLabel?.(key, value) ?? servedLabel;
  const base = {key, labelKey: field.labelKey};
  const plainValues = () => {
    const counts = new Map(servedValues.map(item => [item.value, item.count]));
    const complete = servedGroup?.complete ?? false;
    return plainFacetValues(
      key, servedValues, [...picks.keys()], state, label,
      value => definitions.valueIcon?.(key, value) ?? undefined,
      value => counts.get(value) ?? (complete || picks.get(value) === 'excluded' ? 0 : null),
    );
  };
  switch (field.kind) {
    case 'stars':
      return {...base, showAllValues: true, values: bandFacetValues(key, servedValues, state, label, field.stars)};
    case 'range': {
      const range = buildFacetRange(servedGroup, included, field.fileSize ?? false);
      return field.bands
        ? {...base, range, showAllValues: true, values: bandFacetValues(key, servedValues, state, label, undefined)}
        : {...base, range, showAllValues: false, values: []};
    }
    case 'domain':
      return {...base, showAllValues: true, values: applyDomain(key, plainValues(), field.domain, label)};
    case 'flag':
      return {
        ...base,
        showAllValues: true,
        values: applyDomain(key, plainValues(), [FLAG_VALUE], label).map(item => ({...item, labelKey: field.labelKey})),
      };
    case 'list':
    case undefined: {
      const values = plainValues();
      return {...base, showAllValues: false, values: field.order ? sortFacetValues(values, field.order) : values};
    }
  }
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

function bandFacetValues<K extends string>(
  key: K,
  servedValues: BrowseFacetGroup['values'],
  state: (value: string) => BrowseFacetState | null,
  label: (value: string, servedLabel: string) => string,
  starScale: number | undefined,
): BrowseFilterValue<K>[] {
  return servedValues.map(item => {
    const range = parseRangeToken(item.value);
    return {
      facetKey: key,
      value: item.value,
      label: label(item.value, (range && formatRangeLabel(range)) ?? item.title),
      count: item.count,
      state: state(item.value),
      stars: starScale != null ? {value: range?.max ?? starScale, max: starScale} : undefined,
    };
  });
}

function plainFacetValues<K extends string>(
  key: K,
  servedValues: BrowseFacetGroup['values'],
  picks: readonly string[],
  state: (value: string) => BrowseFacetState | null,
  label: (value: string, servedLabel: string) => string,
  icon: (value: string) => IconSelection | undefined,
  count: (value: string) => number | null,
): BrowseFilterValue<K>[] {
  const labelsByValue = new Map(servedValues.map(option => [option.value, option.title]));
  for (const value of picks) {
    if (!labelsByValue.has(value)) {
      labelsByValue.set(value, value);
    }
  }
  return Array.from(labelsByValue, ([value, title]) => ({
    facetKey: key,
    value,
    label: label(value, title),
    icon: icon(value),
    count: count(value),
    state: state(value),
  }));
}

function applyDomain<K extends string>(
  key: K,
  values: BrowseFilterValue<K>[],
  domain: readonly string[],
  label: (value: string, servedLabel: string) => string,
): BrowseFilterValue<K>[] {
  const byValue = new Map(values.map(item => [item.value, item]));
  return domain.map(value =>
    byValue.get(value) ?? {facetKey: key, value, label: label(value, value), count: 0, state: null});
}

function sortFacetValues<K extends string>(values: BrowseFilterValue<K>[], order: BrowseFacetValueOrder): BrowseFilterValue<K>[] {
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
  readonly valueLabelKey?: string;
}

export function browseFilterChips<K extends string>(
  served: readonly BrowseFacetGroup[],
  definitions: BrowseFacetDefinitions<K>,
  selections: BrowseFacetSelection<K>,
): BrowseFilterChip<K>[] {
  const servedByKey = new Map(served.map(group => [group.key, group]));
  return browseFacetSelectionKeys(selections).flatMap(marked => {
    const key = unmarkedBrowseFacetKey(marked);
    if (!definitions.isKey(key)) {
      return [];
    }
    const values = browseFacetValues(selections, marked);
    const labels = new Map(servedByKey.get(key)?.values.map(option => [option.value, option.title]));
    const field = definitions.field(key);
    return values.map(value => ({
      key,
      value,
      excluded: marked.startsWith('-'),
      valueLabel: definitions.valueLabel?.(key, value) ?? labels.get(value) ?? value,
      ...(field.kind === 'flag' && value === FLAG_VALUE
        ? {groupLabelKey: FLAG_SECTION_LABEL_KEY, valueLabelKey: field.labelKey}
        : {groupLabelKey: field.labelKey}),
    }));
  });
}
