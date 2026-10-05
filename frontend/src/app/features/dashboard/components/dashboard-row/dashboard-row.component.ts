import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideChevronLeft, LucideChevronRight} from '@lucide/angular';
import {injectQuery} from '@tanstack/angular-query-experimental';

import {type BookMenuComponent} from '../../../book/components/book-menu/book-menu.component';
import {BookCardComponent} from '../../../book/components/cards/book-card.component';
import {bookCardHeightForWidth} from '../../../book/components/cards/book-card.layout';
import {BookCardSkeletonComponent} from '../../../book/components/cards/book-card-skeleton.component';
import {createBookBrowseScopeTitle} from '../../../book/browse/book-browse-queries';
import {EMPTY_FACET_SELECTION} from '../../../book/data/book-query-params';
import {BookQueryService} from '../../../book/data/book-query.service';
import {type BookSummary} from '../../../book/data/book-response.models';
import {BookNavigationService} from '../../../book/service/book-navigation.service';
import {UserService} from '../../../settings/user-management/user.service';
import {createBrowseSkeletonDelay} from '../../../../shared/browse/skeleton-delay';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {CoverScalePreferenceService} from '../../../../shared/service/cover-scale-preference.service';
import {AppButtonComponent} from '../../../../shared/ui/button/app-button.component';
import {type DashboardRow, type DashboardRowCard} from '../../dashboard-rows';

const SKELETONS = Array.from({length: 24});
export const DASHBOARD_CARD_GAP = 16;
export const DASHBOARD_CARD_WIDTH = 150;

@Component({
  selector: 'app-dashboard-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppButtonComponent,
    BookCardComponent,
    BookCardSkeletonComponent,
    LucideChevronLeft,
    LucideChevronRight,
    RouterLink,
    TranslocoPipe,
  ],
  host: {class: 'block'},
  templateUrl: './dashboard-row.component.html',
})
export class DashboardRowComponent {
  private readonly bookQuery = inject(BookQueryService);
  private readonly userService = inject(UserService);
  private readonly coverScale = inject(CoverScalePreferenceService);
  protected readonly desktop = inject(LayoutService).isDesktop;
  private readonly bookNavigation = inject(BookNavigationService);

  readonly row = input.required<DashboardRow>();
  readonly bookMenu = input.required<BookMenuComponent>();
  private readonly strip = viewChild<ElementRef<HTMLElement>>('strip');
  private readonly cardElements = viewChildren<BookCardComponent, ElementRef<HTMLElement>>(BookCardComponent, {read: ElementRef});

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
  protected readonly squareCovers = computed(() => {
    const cards = this.cards();
    return cards.length > 0 && cards.every(({book, file}) => (file ?? book.primaryFile)?.bookType === 'AUDIOBOOK');
  });
  protected readonly showFormatPill = computed(() =>
    this.userService.currentUser()?.userSettings.entityViewPreferences?.global.overlayBookType ?? true);
  protected readonly status = computed(() => this.query.status());
  protected readonly skeletonVisible = createBrowseSkeletonDelay(this.status, computed(() => this.cards().length > 0));
  protected readonly cardWidth = computed(() =>
    this.desktop() ? Math.round(DASHBOARD_CARD_WIDTH * this.coverScale.scaleFactor()) : this.coverScale.BASE_WIDTH);
  protected readonly cardHeight = computed(() =>
    bookCardHeightForWidth(this.cardWidth(), {square: this.squareCovers(), metaLines: 2}));
  protected readonly skeletons = SKELETONS;
  protected readonly arrowClass =
    'flex size-8 items-center justify-center rounded-full text-text-secondary outline-none transition-colors ' +
    'hover:text-text-strong focus-visible:ring-2 focus-visible:ring-primary disabled:pointer-events-none disabled:opacity-30';

  protected readonly atStart = signal(true);
  protected readonly atEnd = signal(true);

  constructor() {
    afterRenderEffect(onCleanup => {
      const strip = this.strip()?.nativeElement;
      const cards = this.cardElements();
      if (!strip || cards.length === 0) {
        return;
      }
      const first = cards[0].nativeElement;
      const last = cards[cards.length - 1].nativeElement;
      const observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
          const inView = entry.intersectionRatio === 1;
          if (entry.target === first) {
            this.atStart.set(inView);
          }
          if (entry.target === last) {
            this.atEnd.set(inView);
          }
        }
      }, {root: strip, threshold: 1});
      observer.observe(first);
      observer.observe(last);
      onCleanup(() => observer.disconnect());
    });
  }

  protected readBook(card: DashboardRowCard): void {
    this.bookNavigation.readBook(card.book, {bookType: card.file?.bookType});
  }

  protected scrollPage(direction: -1 | 1): void {
    const strip = this.strip()?.nativeElement;
    strip?.scrollBy({left: direction * strip.clientWidth});
  }

  protected openBook(book: BookSummary): void {
    this.bookNavigation.openBook(book.id, this.cards().map(card => card.book.id));
  }
}
