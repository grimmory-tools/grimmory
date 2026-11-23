import {computed, inject, linkedSignal, type Signal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from '@openng/optimus-ui/api';

import {
  type EntityViewPreference,
  type TableColumnPreference,
  UserService,
} from '../../settings/user-management/user.service';
import {
  entityViewCardDetail,
  entityViewMode,
  entityViewSeriesCollapsed,
  entityViewSortCriteria,
  entityViewSortPatch,
  findEntityViewPreferenceOverride,
  upsertEntityViewPreference,
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
import {injectHiddenProviderFieldKeys, type BookDetailLineSortKey} from './book-browse-fields';
import {type BookBrowsePreferenceContext} from './book-browse-scope';
import {bookCardDetailOptions, bookSortTermsFromCriteria} from './book-browse-sort';
import {type BrowseUrlState, type BrowseViewMode} from '../../../shared/browse/url-state';

export interface BookBrowsePreferencesOptions {
  readonly context: Signal<BookBrowsePreferenceContext>;
  readonly urlState: BrowseUrlState<BookQueryFacetKey, BookQuerySortKey>;
}

export function createBookBrowsePreferences({context: pageContext, urlState}: BookBrowsePreferencesOptions) {
  const context = computed(() => {
    const current = pageContext();
    return typeof current === 'string' ? null : current;
  });
  const savesPreferences = computed(() => pageContext() !== 'none');
  const userService = inject(UserService);
  const messages = inject(MessageService);
  const transloco = inject(TranslocoService);
  const entityViewPreferences = computed(() => userService.currentUser()?.userSettings.entityViewPreferences);

  const viewMode = computed<BrowseViewMode>(() =>
    urlState.view() ?? (entityViewMode(entityViewPreferences(), context()) === 'TABLE' ? 'table' : 'grid'));
  const formatPill = computed(() => entityViewPreferences()?.global.overlayBookType ?? true);
  const seriesCollapsed = linkedSignal(() => entityViewSeriesCollapsed(entityViewPreferences(), context()));
  const hiddenProviderFields = injectHiddenProviderFieldKeys();
  const cardDetailOptions = computed(() => bookCardDetailOptions(hiddenProviderFields()));
  const cardDetail = linkedSignal(() => {
    const key = entityViewCardDetail(entityViewPreferences(), context());
    return key !== null && hiddenProviderFields().has(key) ? null : key;
  });

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
        .filter(term => !hiddenProviderFields().has(term.key) && (available.size === 0 || available.has(term.key)));
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
  const columnOptions = computed(() => bookColumnOptions(columnPreferences(), hiddenProviderFields()));
  const visibleColumns = computed(() => bookVisibleColumnOptions(columnPreferences(), hiddenProviderFields()));

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
    if (!user || !savesPreferences()) {
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
    savesPreferences,
    viewMode,
    formatPill,
    cardDetail,
    cardDetailOptions,
    seriesCollapsed: seriesCollapsed.asReadonly(),
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

    setCardDetail(value: BookDetailLineSortKey | null): void {
      cardDetail.set(value);
      updateViewPreference({cardDetail: value});
    },

    setSeriesCollapsed(collapsed: boolean): void {
      seriesCollapsed.set(collapsed);
      updateViewPreference({seriesCollapsed: collapsed});
    },

    saveSortDefault(terms: readonly BookSortTerm[]): void {
      updateViewPreference(entityViewSortPatch(
        browseSortCriteria(terms),
      ));
    },
  };
}
