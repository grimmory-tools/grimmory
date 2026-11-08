import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {LucideChevronRight} from '@lucide/angular';
import {injectQuery} from '@tanstack/angular-query-experimental';

import {type BookMenuComponent} from '../../../book/components/book-menu/book-menu.component';
import {BookRowComponent} from '../../../book/components/book-row/book-row.component';
import {createBookBrowseScopeTitle} from '../../../book/browse/book-browse-queries';
import {EMPTY_FACET_SELECTION} from '../../../book/data/book-query-params';
import {BookQueryService} from '../../../book/data/book-query.service';
import {type BookSummary} from '../../../book/data/book-response.models';
import {BookNavigationService} from '../../../book/service/book-navigation.service';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {type DashboardRow} from '../../dashboard-rows';

@Component({
  selector: 'app-dashboard-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BookRowComponent, LucideChevronRight, RouterLink],
  host: {class: 'block'},
  templateUrl: './dashboard-row.component.html',
})
export class DashboardRowComponent {
  private readonly bookQuery = inject(BookQueryService);
  private readonly bookNavigation = inject(BookNavigationService);
  protected readonly desktop = inject(LayoutService).isDesktop;

  readonly row = input.required<DashboardRow>();
  readonly bookMenu = input.required<BookMenuComponent>();

  protected readonly query = injectQuery(() => {
    const row = this.row();
    return this.bookQuery.page({
      facets: EMPTY_FACET_SELECTION,
      scope: row.scope.facets,
      sort: row.sort,
      size: row.querySize,
    });
  });

  protected readonly title = createBookBrowseScopeTitle(() => this.row().scope);

  protected readonly cards = computed(() => this.row().cards(this.query.data()?.content ?? []));

  protected openBook(book: BookSummary): void {
    this.bookNavigation.openBook(book.id, this.cards().map(card => card.book.id));
  }
}
