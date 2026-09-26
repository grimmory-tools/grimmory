import {ChangeDetectionStrategy, Component, computed, inject, input, viewChild} from '@angular/core';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';

import {BookCardComponent} from '../../../book/components/cards/book-card.component';
import {BOOK_CARD_COVER_ASPECT, bookCardHeightForWidth} from '../../../book/components/cards/book-card.layout';
import {BookCardSkeletonComponent} from '../../../book/components/cards/book-card-skeleton.component';
import {BookMenuComponent} from '../../../book/components/book-menu/book-menu.component';
import {BookQueryService} from '../../../book/data/book-query.service';
import {type BookSummary} from '../../../book/data/book-response.models';
import {BookNavigationService} from '../../../book/service/book-navigation.service';
import {UserService} from '../../../settings/user-management/user.service';
import {createBrowseSkeletonDelay} from '../../../../shared/browse/skeleton-delay';
import {ArtworkRevealGroupDirective} from '../../../../shared/components/cover/artwork-reveal-group.directive';
import {AppButtonComponent} from '../../../../shared/ui/button/app-button.component';
import {dashboardRowBooks, dashboardRowQueryParams} from '../../dashboard-row-query';
import {type ScrollerConfig, ScrollerType} from '../../models/dashboard-config.model';

const CARD_BASE_WIDTH = 140;
const SKELETONS = Array.from({length: 8});
type ScrollerViewState = 'idle' | 'skeleton' | 'error' | 'empty' | 'books';

@Component({
  selector: 'app-dashboard-scroller',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-scroller.component.html',
  styleUrls: ['./dashboard-scroller.component.scss'],
  imports: [
    AppButtonComponent,
    ArtworkRevealGroupDirective,
    BookCardComponent,
    BookCardSkeletonComponent,
    BookMenuComponent,
    TranslocoDirective,
    TranslocoPipe
  ],
  standalone: true
})
export class DashboardScrollerComponent {

  readonly config = input.required<ScrollerConfig>();

  private readonly bookQuery = inject(BookQueryService);
  private readonly userService = inject(UserService);
  protected readonly bookNavigation = inject(BookNavigationService);
  protected readonly bookMenu = viewChild(BookMenuComponent);

  private readonly params = computed(() => dashboardRowQueryParams(this.config()));
  private readonly rowQuery = injectQuery(() => {
    const params = this.params();
    return {
      ...this.bookQuery.page(params ?? {facets: {}, facetLogic: 'or', sort: [], size: 1}),
      enabled: params !== null,
    };
  });

  protected readonly title = computed(() => this.config().title);
  protected readonly isMagicShelf = computed(() => this.config().type === ScrollerType.MAGIC_SHELF);
  protected readonly squareCovers = computed(() => this.config().type === ScrollerType.LAST_LISTENED);
  protected readonly cardWidth = computed(() =>
    this.squareCovers() ? Math.round(CARD_BASE_WIDTH * BOOK_CARD_COVER_ASPECT) : CARD_BASE_WIDTH);
  protected readonly cardHeight = computed(() =>
    bookCardHeightForWidth(this.cardWidth(), {square: this.squareCovers(), metaLines: 2}));
  protected readonly showFormatPill = computed(() =>
    this.userService.currentUser()?.userSettings.entityViewPreferences?.global.overlayBookType ?? true);

  protected readonly rowBooks = computed(() =>
    dashboardRowBooks(this.config(), this.params() === null ? [] : this.rowQuery.data()?.content ?? []));
  protected readonly books = computed(() => this.rowBooks().map(({book}) => book));
  private readonly skeletonVisible = createBrowseSkeletonDelay(
    computed(() => this.rowQuery.status()),
    computed(() => this.rowBooks().length > 0));

  protected readonly viewState = computed<ScrollerViewState>(() => {
    if (this.rowBooks().length > 0) {
      return 'books';
    }
    if (this.params() === null) {
      return 'empty';
    }
    switch (this.rowQuery.status()) {
      case 'error':
        return 'error';
      case 'success':
        return 'empty';
      default:
        return this.skeletonVisible() ? 'skeleton' : 'idle';
    }
  });
  protected readonly menuOpenBookId = computed(() => this.bookMenu()?.openBookId() ?? null);
  protected readonly skeletons = SKELETONS;
  protected readonly stripClass =
    'relative z-[2] -mx-4 flex gap-4 overflow-x-auto overflow-y-hidden px-4 pt-2 pb-5';
  protected readonly noticeClass =
    'relative z-[2] flex flex-col items-center gap-7 px-7 py-10 text-center';

  protected retry(): void {
    void this.rowQuery.refetch();
  }

  protected openBook(book: BookSummary): void {
    this.bookNavigation.openBook(book.id, this.books().map(({id}) => id));
  }
}
