import {computed, linkedSignal, type Signal} from '@angular/core';

export interface BrowseSelectionItem {
  readonly id: number;
}

export interface BrowseSelectionDeps {
  filtersKey: Signal<string>;
  listKey: Signal<string>;
  items: Signal<readonly BrowseSelectionItem[]>;
  totalElements: Signal<number | null>;
}

export type BrowseSelectionState =
  | {mode: 'explicit'; ids: ReadonlySet<number>}
  | {mode: 'allMatching'; excludedIds: ReadonlySet<number>};

export interface BrowseSelection {
  readonly state: Signal<BrowseSelectionState>;
  readonly count: Signal<number>;
  readonly active: Signal<boolean>;
  readonly allMatchingSelected: Signal<boolean>;
  isSelected(id: number): boolean;
  toggle(item: BrowseSelectionItem, index: number, shiftKey: boolean): void;
  selectAll(): void;
  clear(): void;
  pruneDeleted(ids: readonly number[]): void;
}

export async function resolveSelectedIds(
  state: BrowseSelectionState,
  fetchIds: () => Promise<readonly number[]>,
): Promise<readonly number[]> {
  if (state.mode === 'explicit') {
    return [...state.ids];
  }
  const ids = await fetchIds();
  return state.excludedIds.size === 0
    ? ids
    : ids.filter(id => !state.excludedIds.has(id));
}

function withIds(set: ReadonlySet<number>, ids: readonly number[], present: boolean): Set<number> {
  const next = new Set(set);
  for (const id of ids) {
    if (present) {
      next.add(id);
    } else {
      next.delete(id);
    }
  }
  return next;
}

export function createBrowseSelection(deps: BrowseSelectionDeps): BrowseSelection {
  const state = linkedSignal<string, BrowseSelectionState>({
    source: deps.filtersKey,
    computation: () => ({mode: 'explicit', ids: new Set<number>()}),
  });
  const anchor = linkedSignal<string, number | null>({
    source: deps.listKey,
    computation: () => null,
  });

  const count = computed(() => {
    const current = state();
    return current.mode === 'explicit'
      ? current.ids.size
      : Math.max(0, (deps.totalElements() ?? 0) - current.excludedIds.size);
  });

  const active = computed(() => count() > 0);
  const allMatchingSelected = computed(() => count() > 0 && count() === deps.totalElements());

  function isSelected(id: number): boolean {
    const current = state();
    return current.mode === 'explicit'
      ? current.ids.has(id)
      : !current.excludedIds.has(id);
  }

  function setSelected(ids: readonly number[], selected: boolean): void {
    const current = state();
    state.set(current.mode === 'explicit'
      ? {mode: 'explicit', ids: withIds(current.ids, ids, selected)}
      : {mode: 'allMatching', excludedIds: withIds(current.excludedIds, ids, !selected)});
  }

  function selectLoadedRange(start: number, end: number, selected: boolean): void {
    setSelected(deps.items().slice(start, end + 1).map(item => item.id), selected);
  }

  function toggle(item: BrowseSelectionItem, index: number, shiftKey: boolean): void {
    const anchorId = anchor();
    if (shiftKey && anchorId !== null) {
      const anchorIndex = deps.items().findIndex(candidate => candidate.id === anchorId);
      if (anchorIndex >= 0) {
        selectLoadedRange(
          Math.min(anchorIndex, index),
          Math.max(anchorIndex, index),
          !isSelected(item.id),
        );
        return;
      }
    }

    setSelected([item.id], !isSelected(item.id));
    anchor.set(item.id);
  }

  function selectAll(): void {
    const total = deps.totalElements();
    if (total === null || total === 0) {
      return;
    }
    anchor.set(null);
    state.set({mode: 'allMatching', excludedIds: new Set<number>()});
  }

  function clear(): void {
    state.set({mode: 'explicit', ids: new Set<number>()});
    anchor.set(null);
  }

  function pruneDeleted(ids: readonly number[]): void {
    if (ids.length === 0) {
      return;
    }
    const current = state();
    state.set(current.mode === 'explicit'
      ? {mode: 'explicit', ids: withIds(current.ids, ids, false)}
      : {mode: 'allMatching', excludedIds: withIds(current.excludedIds, ids, false)});
  }

  return {
    state: state.asReadonly(),
    count,
    active,
    allMatchingSelected,
    isSelected,
    toggle,
    selectAll,
    clear,
    pruneDeleted,
  };
}
