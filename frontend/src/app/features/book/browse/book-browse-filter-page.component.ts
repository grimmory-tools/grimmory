import {Component, computed, inject, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute} from '@angular/router';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {injectQuery} from '@tanstack/angular-query-experimental';

import {BrowseFilterRailComponent} from '../../../shared/browse/filter-rail/filter-rail.component';
import {
  countBrowseFacetValues,
  toggleBrowseFacetValue,
  withBrowseFacetRange,
  type BrowseFilterRangeCommit,
  type BrowseFilterToggle,
} from '../../../shared/browse/facets';

import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {BrowseSearchInputComponent} from '../../../shared/browse/search-input/search-input.component';
import {debouncedSignal} from '../../../shared/util/debounced-signal';
import {SEARCH_DEBOUNCE_MS} from '../../../shared/util/search-terms';
import {AppPageHeaderComponent} from '../../../shared/layout/page-header/app.page-header.component';
import {type PageHeader} from '../../../shared/layout/page-header/page-header.service';
import {
  EMPTY_FACET_SELECTION,
  type BookQueryFacetKey,
  type FacetValueMap,
} from '../data/book-query-params';
import {type BookPage} from '../data/book-query.models';
import {BookQueryService} from '../data/book-query.service';
import {bookBrowseScope} from './book-browse-scope';
import {createBookBrowseQueries} from './book-browse-queries';
import {createBookBrowseUrlState} from './book-browse-url-state';

@Component({
  selector: 'app-book-browse-filter-page',
  imports: [TranslocoPipe, AppButtonComponent, AppPageHeaderComponent, BrowseFilterRailComponent, BrowseSearchInputComponent],
  template: `
    <div class="app-page pb-0!">
      <app-page-header [pageHeader]="pageHeader()">
        <div class="w-full">
          <app-browse-search-input
            [value]="stagedQuery()"
            [placeholder]="searchHint()"
            [ariaLabel]="searchHint()"
            [clearLabel]="'book.searcher.clearSearch' | transloco"
            (valueChange)="stagedQuery.set($event)"
            (cleared)="stagedQuery.set('')"
            (entered)="onCommit()" />
        </div>
      </app-page-header>

      <div class="-mx-1.5 pb-28">
        <app-browse-filter-rail
          alwaysShowBoxes
          [groups]="railGroups()"
          (toggleValue)="onToggle($event)"
          (commitRange)="onCommitRange($event)" />
      </div>

      <div
        class="fixed inset-x-0 bottom-0 z-20 flex items-center gap-3 border-t border-border bg-page px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <app-button
          variant="ghost"
          [label]="'browse.clearFilters' | transloco"
          [disabled]="!hasActiveFilters()"
          (clicked)="onClear()" />
        <app-button
          tone="primary"
          fluid
          [label]="'browse.filterPage.show' | transloco: {count: previewTotal() ?? '…'}"
          [loading]="previewLoading()"
          (clicked)="onCommit()" />
      </div>
    </div>
  `,
})
export class BookBrowseFilterPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly bookQuery = inject(BookQueryService);
  private readonly transloco = inject(TranslocoService);
  private readonly urlState = createBookBrowseUrlState();

  protected readonly staged = signal<FacetValueMap>(this.urlState.facets());
  protected readonly stagedQuery = signal(this.urlState.query());
  private readonly debouncedQuery = debouncedSignal(
    computed(() => this.stagedQuery().trim()), SEARCH_DEBOUNCE_MS,
  );
  protected readonly hasActiveFilters = computed(() =>
    countBrowseFacetValues(this.staged()) > 0 || this.stagedQuery().trim().length > 0);

  private readonly scope = computed(() =>
    bookBrowseScope(this.route.snapshot.paramMap, this.route.snapshot.data),
  );
  private readonly queries = createBookBrowseQueries({
    selection: this.staged,
    query: this.debouncedQuery,
    scope: this.scope,
  });
  protected readonly railGroups = this.queries.railGroups;
  protected readonly searchHint = this.queries.searchHint;

  private readonly previewQuery = injectQuery(() => ({
    ...this.bookQuery.page({...this.queries.collectionParams(), size: 1, sort: []}),
    placeholderData: (previous: BookPage | undefined) => previous,
  }));
  protected readonly previewTotal = computed(() => this.previewQuery.data()?.page.totalElements ?? null);
  protected readonly previewLoading = computed(() =>
    this.previewQuery.isPending() || this.previewQuery.isPlaceholderData());

  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  protected readonly pageHeader = computed<PageHeader>(() => {
    this.activeLang();
    return {
      title: this.transloco.translate('browse.filter'),
      breadcrumbs: [
        {
          label: this.queries.title(),
          commands: ['/', ...this.route.parent!.snapshot.url.map(segment => segment.path)],
          queryParamsHandling: 'preserve',
        },
        {label: this.transloco.translate('browse.filter')},
      ],
    };
  });

  protected onToggle(toggle: BrowseFilterToggle<BookQueryFacetKey>): void {
    this.staged.update(current =>
      toggleBrowseFacetValue(
        current,
        toggle.key,
        toggle.value,
        toggle.selected,
      ),
    );
  }

  protected onCommitRange(commit: BrowseFilterRangeCommit<BookQueryFacetKey>): void {
    this.staged.update(current =>
      withBrowseFacetRange(current, commit.key, commit.min, commit.max, this.queries.definitions()));
  }

  protected onClear(): void {
    this.staged.set(EMPTY_FACET_SELECTION);
    this.stagedQuery.set('');
  }

  protected onCommit(): void {
    this.urlState.applyFilters(this.staged(), this.stagedQuery().trim());
  }
}
