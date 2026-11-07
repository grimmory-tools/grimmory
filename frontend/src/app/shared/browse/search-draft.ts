import {computed, effect, signal, untracked, type Signal} from '@angular/core';

import {debouncedSignal} from '../util/debounced-signal';
import {SEARCH_DEBOUNCE_MS} from '../util/search-terms';

export interface BrowseSearchDraftDeps {
  committed: Signal<string>;
  commit: (term: string) => void;
}

export interface BrowseSearchDraft {
  readonly value: Signal<string>;
  set(value: string): void;
}

export function createBrowseSearchDraft(deps: BrowseSearchDraftDeps): BrowseSearchDraft {
  const draft = signal(deps.committed());
  const trimmedDraft = computed(() => draft().trim());
  const debounced = debouncedSignal(trimmedDraft, SEARCH_DEBOUNCE_MS);

  effect(() => {
    const committed = deps.committed();
    untracked(() => {
      const current = trimmedDraft();
      if (debounced() === current && current !== committed) {
        draft.set(committed);
      }
    });
  });

  effect(() => {
    const settled = debounced();
    untracked(() => {
      if (settled !== trimmedDraft() || settled === deps.committed()) {
        return;
      }
      deps.commit(settled);
    });
  });

  return {
    value: draft.asReadonly(),
    set: value => draft.set(value),
  };
}
