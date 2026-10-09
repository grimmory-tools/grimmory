import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {LucideChevronRight} from '@lucide/angular';
import {injectQuery} from '@tanstack/angular-query-experimental';

import {type BookMenuComponent} from '../../../book/components/book-menu/book-menu.component';
import {BookRowComponent} from '../../../book/components/book-row/book-row.component';
import {createBookBrowseScopeTitle} from '../../../book/browse/book-browse-queries';
import {scopedFacetSelection} from '../../../book/browse/book-browse-scope';
import {EMPTY_FACET_SELECTION} from '../../../book/data/book-query-params';
import {BookQueryService} from '../../../book/data/book-query.service';
import {type BookSummary} from '../../../book/data/book-response.models';
import {BookNavigationService} from '../../../book/service/book-navigation.service';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {dashboardContinueFile, DASHBOARD_ROW_SIZE, type DashboardRow} from '../../dashboard-rows';

const PROGRESS_QUERY_SIZE = DASHBOARD_ROW_SIZE * 2;

@Component({
  selector: 'app-dashboard-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BookRowComponent, LucideChevronRight, RouterLink],
  host: {class: 'block'},
  templateUrl: './dashboard-row.component.html',
})
export class DashboardRowComponent {
  readonly row = input.required<DashboardRow>();
  readonly bookMenu = input.required<BookMenuComponent>();

  private readonly bookQuery = inject(BookQueryService);
  private readonly bookNavigation = inject(BookNavigationService);
  protected readonly desktop = inject(LayoutService).isDesktop;

  protected readonly query = injectQuery(() => {
    const row = this.row();
    return this.bookQuery.page({
      facets: scopedFacetSelection(EMPTY_FACET_SELECTION, row.scope),
      sort: row.sort,
      size: row.fileTypes ? PROGRESS_QUERY_SIZE : DASHBOARD_ROW_SIZE,
    });
  });

  protected readonly title = createBookBrowseScopeTitle(() => this.row().scope);

  protected readonly cards = computed(() => {
    const {fileTypes} = this.row();
    const books = this.query.data()?.content ?? [];
    if (!fileTypes) {
      return books.map(book => ({book}));
    }
    return books.flatMap(book => {
      const file = dashboardContinueFile(book, fileTypes);
      return file ? [{book, file}] : [];
    }).slice(0, DASHBOARD_ROW_SIZE);
  });

  protected openBook(book: BookSummary): void {
    this.bookNavigation.openBook(book.id, this.cards().map(card => card.book.id));
  }
}
