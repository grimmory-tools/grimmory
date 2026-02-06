import {Injectable, inject} from '@angular/core';
import {ActivatedRoute, ParamMap, Router} from '@angular/router';
import {SortDirection, SortOption} from '../../model/sort.model';
import {BookFilterMode, EntityViewPreference, EntityViewPreferences, SortCriterion} from '../../../settings/user-management/user.service';
import {EntityType} from './book-browser.component';

export const QUERY_PARAMS = {
  VIEW: 'view',
  SORT: 'sort',
  DIRECTION: 'direction',
  FILTER: 'filter',
  FMODE: 'fmode',
  SIDEBAR: 'sidebar',
  FROM: 'from',
} as const;

export const VIEW_MODES = {
  GRID: 'grid',
  TABLE: 'table',
} as const;

export const SORT_DIRECTION = {
  ASCENDING: 'asc',
  DESCENDING: 'desc',
} as const;

export interface BookBrowserQueryState {
  viewMode: 'grid' | 'table';
  sortField: string;
  sortDirection: SortDirection;
  filters: Record<string, string[]>;
  filterMode: BookFilterMode;
}

export interface QueryParseResult {
  viewMode: string;
  sortOption: SortOption;
  sortCriteria: SortOption[];
  filters: Record<string, string[]>;
  filterMode: BookFilterMode;
  viewModeFromToggle: boolean;
}

@Injectable({providedIn: 'root'})
export class BookBrowserQueryParamsService {
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);

  parseQueryParams(
    queryParamMap: ParamMap,
    userPrefs: EntityViewPreferences | undefined,
    entityType: EntityType | undefined,
    entityId: number | undefined,
    sortOptions: SortOption[],
    defaultFilterMode: BookFilterMode
  ): QueryParseResult {
    const viewParam = queryParamMap.get(QUERY_PARAMS.VIEW);
    const sortParam = queryParamMap.get(QUERY_PARAMS.SORT);
    const directionParam = queryParamMap.get(QUERY_PARAMS.DIRECTION);
    const filterParams = queryParamMap.get(QUERY_PARAMS.FILTER);
    const filterModeParam = queryParamMap.get(QUERY_PARAMS.FMODE);
    const fromParam = queryParamMap.get(QUERY_PARAMS.FROM);

    const filterMode = (filterModeParam || defaultFilterMode) as BookFilterMode;

    // Parse filters
    const filters = this.deserializeFilters(filterParams);

    // Determine effective preferences
    const globalPrefs = userPrefs?.global;
    const currentEntityTypeStr = entityType?.toString().toUpperCase().replaceAll(' ', '_');
    const override = userPrefs?.overrides?.find(o =>
      o.entityType?.toUpperCase() === currentEntityTypeStr &&
      o.entityId === entityId
    );

    const effectivePrefs: EntityViewPreference = override?.preferences ?? globalPrefs ?? {
      sortKey: 'addedOn',
      sortDir: 'ASC',
      view: 'GRID',
      coverSize: 1.0,
      seriesCollapsed: false,
      overlayBookType: true
    };

    // Parse sort criteria - supports both legacy and new multi-sort format
    let sortCriteria: SortOption[];

    if (sortParam) {
      // Check if it's new multi-sort format (contains colons like "author:asc,title:desc")
      if (sortParam.includes(':')) {
        sortCriteria = this.deserializeSort(sortParam, sortOptions);
      } else {
        // Legacy format: separate sort and direction params
        const effectiveSortDir = directionParam
          ? (directionParam.toLowerCase() === SORT_DIRECTION.DESCENDING ? SortDirection.DESCENDING : SortDirection.ASCENDING)
          : SortDirection.ASCENDING;
        const matchedSort = sortOptions.find(opt => opt.field === sortParam);
        sortCriteria = matchedSort ? [{
          label: matchedSort.label,
          field: matchedSort.field,
          direction: effectiveSortDir
        }] : [];
      }
    } else {
      // Use user preferences
      if (effectivePrefs.sortCriteria && effectivePrefs.sortCriteria.length > 0) {
        sortCriteria = effectivePrefs.sortCriteria.map((c: SortCriterion) => {
          const matchedSort = sortOptions.find(opt => opt.field === c.field);
          return {
            label: matchedSort?.label ?? c.field,
            field: c.field,
            direction: c.direction === 'DESC' ? SortDirection.DESCENDING : SortDirection.ASCENDING
          };
        });
      } else {
        // Fall back to legacy single sort preference
        const userSortKey = effectivePrefs.sortKey;
        const userSortDir = effectivePrefs.sortDir?.toUpperCase() === 'DESC'
          ? SortDirection.DESCENDING
          : SortDirection.ASCENDING;
        const matchedSort = sortOptions.find(opt => opt.field === userSortKey);
        sortCriteria = matchedSort ? [{
          label: matchedSort.label,
          field: matchedSort.field,
          direction: userSortDir
        }] : [];
      }
    }

    // Ensure we have at least a default sort
    if (sortCriteria.length === 0) {
      sortCriteria = [{
        label: 'Added On',
        field: 'addedOn',
        direction: SortDirection.DESCENDING
      }];
    }

    // For backward compatibility, expose the first sort as sortOption
    const sortOption = sortCriteria[0];

    // Determine view mode
    const viewModeFromToggle = fromParam === 'toggle';
    const viewMode = viewModeFromToggle
      ? (viewParam === VIEW_MODES.TABLE || viewParam === VIEW_MODES.GRID
        ? viewParam
        : VIEW_MODES.GRID)
      : (effectivePrefs.view?.toLowerCase() ?? VIEW_MODES.GRID);

    return {
      viewMode,
      sortOption,
      sortCriteria,
      filters,
      filterMode,
      viewModeFromToggle
    };
  }

  updateViewMode(mode: 'grid' | 'table'): void {
    this.router.navigate([], {
      queryParams: {
        [QUERY_PARAMS.VIEW]: mode,
        [QUERY_PARAMS.FROM]: 'toggle'
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  updateSort(sortOption: SortOption): void {
    this.updateMultiSort([sortOption]);
  }

  updateMultiSort(sortCriteria: SortOption[]): void {
    const currentParams = this.activatedRoute.snapshot.queryParams;
    const newParams = {
      ...currentParams,
      [QUERY_PARAMS.SORT]: this.serializeSort(sortCriteria),
      [QUERY_PARAMS.DIRECTION]: null  // Remove legacy direction param
    };

    this.router.navigate([], {
      queryParams: newParams,
      replaceUrl: true
    });
  }

  serializeSort(criteria: SortOption[]): string {
    return criteria.map(c =>
      `${c.field}:${c.direction === SortDirection.ASCENDING ? 'asc' : 'desc'}`
    ).join(',');
  }

  deserializeSort(sortParam: string, sortOptions: SortOption[]): SortOption[] {
    const criteria: SortOption[] = [];

    sortParam.split(',').forEach(part => {
      const [field, dir] = part.split(':');
      if (field) {
        const matchedSort = sortOptions.find(opt => opt.field === field);
        if (matchedSort) {
          criteria.push({
            label: matchedSort.label,
            field: matchedSort.field,
            direction: dir?.toLowerCase() === 'desc' ? SortDirection.DESCENDING : SortDirection.ASCENDING
          });
        }
      }
    });

    return criteria;
  }

  updateFilters(filters: Record<string, string[]> | null): void {
    const queryParam = filters && Object.keys(filters).length > 0
      ? this.serializeFilters(filters)
      : null;

    if (queryParam !== this.activatedRoute.snapshot.queryParamMap.get(QUERY_PARAMS.FILTER)) {
      this.router.navigate([], {
        relativeTo: this.activatedRoute,
        queryParams: {[QUERY_PARAMS.FILTER]: queryParam},
        queryParamsHandling: 'merge',
        replaceUrl: false
      });
    }
  }

  updateFilterMode(mode: BookFilterMode, currentFilters: Record<string, string[]>): void {
    const params: Record<string, string | null> = {[QUERY_PARAMS.FMODE]: mode};

    // Clear filters if switching from multiple selected to single mode
    if (mode === 'single') {
      const categories = Object.keys(currentFilters);
      if (categories.length > 1 || (categories.length === 1 && currentFilters[categories[0]].length > 1)) {
        params[QUERY_PARAMS.FILTER] = null;
      }
    }

    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: false
    });
  }

  serializeFilters(filters: Record<string, string[]>): string {
    return Object.entries(filters)
      .map(([k, v]) => `${k}:${v.join('|')}`)
      .join(',');
  }

  deserializeFilters(filterParam: string | null): Record<string, string[]> {
    const parsedFilters: Record<string, string[]> = {};

    if (!filterParam) return parsedFilters;

    filterParam.split(',').forEach(pair => {
      const [key, ...valueParts] = pair.split(':');
      const value = valueParts.join(':');
      if (key && value) {
        parsedFilters[key] = value.split('|').map(v => v.trim()).filter(Boolean);
      }
    });

    return parsedFilters;
  }

  syncQueryParams(
    viewMode: string,
    sortCriteria: SortOption[],
    filterMode: BookFilterMode,
    filters: Record<string, string[]>
  ): void {
    const queryParams: Record<string, string | number | null | undefined> = {
      [QUERY_PARAMS.VIEW]: viewMode,
      [QUERY_PARAMS.SORT]: this.serializeSort(sortCriteria),
      [QUERY_PARAMS.DIRECTION]: null,  // Remove legacy direction param
      [QUERY_PARAMS.FMODE]: filterMode,
    };

    if (Object.keys(filters).length > 0) {
      queryParams[QUERY_PARAMS.FILTER] = this.serializeFilters(filters);
    }

    const currentParams = this.activatedRoute.snapshot.queryParams;
    const changed = Object.keys(queryParams).some(k => currentParams[k] !== queryParams[k]);

    if (changed) {
      const mergedParams = {...currentParams, ...queryParams};
      this.router.navigate([], {
        queryParams: mergedParams,
        replaceUrl: true
      });
    }
  }

  shouldForceExpandSeries(queryParamMap: ParamMap): boolean {
    const filterParam = queryParamMap.get(QUERY_PARAMS.FILTER);
    return (
      !!filterParam &&
      typeof filterParam === 'string' &&
      filterParam.split(',').some(pair => pair.trim().startsWith('series:'))
    );
  }
}
