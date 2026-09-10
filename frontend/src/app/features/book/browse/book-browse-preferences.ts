import {computed, inject, linkedSignal, type Signal} from '@angular/core';

import {
  type EntityViewPreference,
  type TableColumnPreference,
  UserService,
} from '../../settings/user-management/user.service';
import {
  entityViewMode,
  entityViewSortCriteria,
  entityViewSortPatch,
  findEntityViewPreferenceOverride,
  upsertEntityViewPreference,
  type EntityViewPreferenceContext,
} from '../../settings/user-management/entity-view-preferences';
import {browseSortCriteria} from '../../../shared/browse/sort';
import {
  DEFAULT_BOOK_SORT_TERMS,
  sortTermsToken,
  type BookQuerySortKey,
  type BookSortTerm,
} from '../data/book-query-params';
import {
  bookColumnOptions,
  bookVisibleColumnOptions,
  normalizeBookColumnPreferences,
  type BookBrowseColumnVisibilityChange,
} from './book-browse-columns';
import {bookSortTermsFromCriteria} from './book-browse-sort';
import {type BookBrowseUrlState, type BookBrowseViewMode} from './book-browse-url-state';

export interface BookBrowsePreferencesOptions {
  readonly context: Signal<EntityViewPreferenceContext | null>;
  readonly availableSortKeys: Signal<ReadonlySet<BookQuerySortKey>>;
  readonly urlState: BookBrowseUrlState;
}

export function createBookBrowsePreferences({context, availableSortKeys, urlState}: BookBrowsePreferencesOptions) {
  const userService = inject(UserService);
  const entityViewPreferences = computed(() => userService.currentUser()?.userSettings.entityViewPreferences);

  const viewMode = computed<BookBrowseViewMode>(() =>
    urlState.view() ?? (entityViewMode(entityViewPreferences(), context()) === 'TABLE' ? 'table' : 'grid'));
  const formatPill = computed(() => entityViewPreferences()?.global.overlayBookType ?? true);

  const defaultSortTerms = computed<readonly BookSortTerm[]>(() => {
    const preferences = entityViewPreferences();
    const available = availableSortKeys();
    const scope = context();
    const candidates = [
      scope ? findEntityViewPreferenceOverride(preferences, scope) : undefined,
      preferences?.global,
    ];
    for (const candidate of candidates) {
      if (!candidate) {
        continue;
      }
      const terms = bookSortTermsFromCriteria(entityViewSortCriteria(candidate))
        .filter(term => available.size === 0 || available.has(term.key));
      if (terms.length > 0) {
        return terms;
      }
    }
    return DEFAULT_BOOK_SORT_TERMS;
  });
  const sortTerms = computed<readonly BookSortTerm[]>(() => {
    const terms = urlState.sortTerms();
    return terms.length > 0 ? terms : defaultSortTerms();
  });
  const isDefaultSort = (terms: readonly BookSortTerm[]): boolean =>
    sortTermsToken(terms) === sortTermsToken(defaultSortTerms());

  const columnPreferences = linkedSignal<TableColumnPreference[]>(() =>
    normalizeBookColumnPreferences(
      userService.currentUser()?.userSettings.tableColumnPreference,
    ),
  );
  const columnOptions = computed(() => bookColumnOptions(columnPreferences()));
  const visibleColumns = computed(() => bookVisibleColumnOptions(columnPreferences()));

  function persistColumnPreferences(): void {
    const user = userService.currentUser();
    if (user) {
      userService.updateUserSetting(user.id, 'tableColumnPreference', columnPreferences());
    }
  }

  function updateViewPreference(patch: Partial<EntityViewPreference>): void {
    const user = userService.currentUser();
    if (!user) {
      return;
    }
    const preferences = upsertEntityViewPreference(
      user.userSettings.entityViewPreferences,
      context(),
      patch,
    );
    userService.updateUserSetting(user.id, 'entityViewPreferences', preferences);
  }

  return {
    viewMode,
    formatPill,
    sortTerms,
    defaultSortTerms,
    isDefaultSort,
    columnOptions,
    visibleColumns,

    setSortTerms(terms: readonly BookSortTerm[]): void {
      const next = terms.length > 0 ? terms : DEFAULT_BOOK_SORT_TERMS;
      const collapsesMultiSort = sortTerms().length > 1 && next.length === 1;
      urlState.setSortToken(isDefaultSort(next) ? null : sortTermsToken(next), !collapsesMultiSort);
    },

    setColumnVisibility(change: BookBrowseColumnVisibilityChange): void {
      const column = columnOptions().find(option => option.field === change.field);
      if (!column?.hideable) {
        return;
      }
      columnPreferences.update(preferences => preferences.map(preference =>
        preference.field === change.field
          ? {...preference, visible: change.visible}
          : preference,
      ));
      persistColumnPreferences();
    },

    resetColumns(): void {
      columnPreferences.set(normalizeBookColumnPreferences(undefined));
      persistColumnPreferences();
    },

    saveSortDefault(terms: readonly BookSortTerm[]): void {
      updateViewPreference(entityViewSortPatch(
        browseSortCriteria(terms.length > 0 ? terms : DEFAULT_BOOK_SORT_TERMS),
      ));
    },
  };
}
