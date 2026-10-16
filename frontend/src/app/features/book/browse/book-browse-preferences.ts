import {computed, inject, linkedSignal, type Signal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';

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
  type BookQueryFacetKey,
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
import {type BrowseUrlState, type BrowseViewMode} from '../../../shared/browse/url-state';

export interface BookBrowsePreferencesOptions {
  readonly context: Signal<EntityViewPreferenceContext | null>;
  readonly urlState: BrowseUrlState<BookQueryFacetKey, BookQuerySortKey>;
}

export function createBookBrowsePreferences({context, urlState}: BookBrowsePreferencesOptions) {
  const userService = inject(UserService);
  const messages = inject(MessageService);
  const transloco = inject(TranslocoService);
  const entityViewPreferences = computed(() => userService.currentUser()?.userSettings.entityViewPreferences);

  const viewMode = computed<BrowseViewMode>(() =>
    urlState.view() ?? (entityViewMode(entityViewPreferences(), context()) === 'TABLE' ? 'table' : 'grid'));
  const formatPill = computed(() => entityViewPreferences()?.global.overlayBookType ?? true);

  function defaultSortTerms(available: ReadonlySet<BookQuerySortKey>): readonly BookSortTerm[] {
    const preferences = entityViewPreferences();
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
  }

  const columnPreferences = linkedSignal<TableColumnPreference[]>(() =>
    normalizeBookColumnPreferences(
      userService.currentUser()?.userSettings.tableColumnPreference,
    ),
  );
  const columnOptions = computed(() => bookColumnOptions(columnPreferences()));
  const visibleColumns = computed(() => bookVisibleColumnOptions(columnPreferences()));

  function onSaveError(): void {
    messages.add({
      severity: 'error',
      summary: transloco.translate('common.error'),
      detail: transloco.translate('settingsView.saveFailedDetail'),
    });
  }

  function persistColumnPreferences(): void {
    const user = userService.currentUser();
    if (user) {
      userService.updateUserSetting(user.id, 'tableColumnPreference', columnPreferences(), onSaveError);
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
    userService.updateUserSetting(user.id, 'entityViewPreferences', preferences, onSaveError);
  }

  return {
    viewMode,
    formatPill,
    defaultSortTerms,
    columnOptions,
    visibleColumns,

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
        browseSortCriteria(terms),
      ));
    },
  };
}
