import {inject, Injectable} from '@angular/core';
import {ParamMap} from '@angular/router';
import {Book} from '../../model/book.model';
import {SortOption} from '../../model/sort.model';
import {SortService} from '../../service/sort.service';
import {filterBooksBySearchTerm} from './filters/HeaderFilter';
import {filterBooksByFilters} from './filters/sidebar-filter';
import {SeriesCollapseFilter} from './filters/SeriesCollapseFilter';
import {QUERY_PARAMS} from './book-browser-query-params.service';
import {BookFilterMode} from '../../../settings/user-management/user.service';

@Injectable({providedIn: 'root'})
export class BookFilterOrchestrationService {
  private sortService = inject(SortService);

  applyFilters(
    books: Book[],
    searchTerm: string,
    activeFilters: Record<string, unknown[]> | null,
    filterMode: BookFilterMode,
    seriesCollapseFilter: SeriesCollapseFilter,
    isSeriesCollapsed: boolean,
    forceExpandSeries: boolean,
    sortCriteria: SortOption[]
  ): Book[] {
    const searchedBooks = filterBooksBySearchTerm(books, searchTerm);
    const filteredBooks = filterBooksByFilters(searchedBooks, activeFilters, filterMode);
    const collapsedBooks = seriesCollapseFilter.collapseBooks(filteredBooks, forceExpandSeries, isSeriesCollapsed);
    return this.sortService.applyMultiSort(collapsedBooks, sortCriteria);
  }

  shouldForceExpandSeries(queryParamMap: ParamMap): boolean {
    const filterParam = queryParamMap.get(QUERY_PARAMS.FILTER);
    return !!filterParam && filterParam.split(',').some(pair => pair.trim().startsWith('series:'));
  }
}
