import {
  afterRenderEffect,
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {NgTemplateOutlet} from '@angular/common';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideChevronLeft, LucideChevronRight} from '@lucide/angular';
import {injectVirtualizer} from '@tanstack/angular-virtual';

import {type BookMenuComponent} from '../book-menu/book-menu.component';
import {BookCardComponent} from '../cards/book-card.component';
import {bookCardHeightForWidth} from '../cards/book-card.layout';
import {BookCardSkeletonComponent} from '../cards/book-card-skeleton.component';
import {type BookFileResponse, type BookSummary} from '../../data/book-response.models';
import {BookNavigationService} from '../../service/book-navigation.service';
import {UserService} from '../../../settings/user-management/user.service';
import {type BrowseRenderedRange, type BrowseStatus, sameRenderedRange} from '../../../../shared/browse/results';
import {createBrowseSkeletonDelay} from '../../../../shared/browse/skeleton-delay';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {CoverScalePreferenceService} from '../../../../shared/service/cover-scale-preference.service';
import {AppButtonComponent} from '../../../../shared/ui/button/app-button.component';

export interface BookRowCard {
  readonly book: BookSummary;
  readonly file?: BookFileResponse;
}

const SKELETONS = Array.from({length: 24});
export const BOOK_ROW_CARD_GAP = 16;
export const BOOK_ROW_CARD_WIDTH = 150;

@Component({
  selector: 'app-book-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppButtonComponent,
    BookCardComponent,
    BookCardSkeletonComponent,
    LucideChevronLeft,
    LucideChevronRight,
    NgTemplateOutlet,
    TranslocoPipe,
  ],
  host: {class: 'block'},
  templateUrl: './book-row.component.html',
})
export class BookRowComponent {
  private readonly userService = inject(UserService);
  private readonly coverScale = inject(CoverScalePreferenceService);
  protected readonly desktop = inject(LayoutService).isDesktop;
  private readonly bookNavigation = inject(BookNavigationService);

  readonly cards = input.required<readonly BookRowCard[]>();
  readonly status = input.required<BrowseStatus>();
  readonly bookMenu = input.required<BookMenuComponent>();
  readonly pageBleed = input(false, {transform: booleanAttribute});
  readonly retry = output<void>();
  readonly opened = output<BookSummary>();
  readonly renderedRange = output<BrowseRenderedRange>();

  protected readonly rowTitle = contentChild('rowTitle');
  private readonly strip = viewChild<ElementRef<HTMLElement>>('strip');

  protected readonly squareCovers = computed(() => {
    const cards = this.cards();
    return cards.length > 0 && cards.every(({book, file}) => (file ?? book.primaryFile)?.bookType === 'AUDIOBOOK');
  });
  protected readonly showFormatPill = computed(() =>
    this.userService.currentUser()?.userSettings.entityViewPreferences?.global.overlayBookType ?? true);
  protected readonly skeletonVisible = createBrowseSkeletonDelay(this.status, computed(() => this.cards().length > 0));
  protected readonly hiddenWhileLoading = computed(() =>
    this.status() === 'pending' && this.cards().length === 0 && !this.skeletonVisible());
  protected readonly cardWidth = computed(() =>
    this.desktop() ? Math.round(BOOK_ROW_CARD_WIDTH * this.coverScale.scaleFactor()) : this.coverScale.BASE_WIDTH);
  protected readonly cardHeight = computed(() =>
    bookCardHeightForWidth(this.cardWidth(), {square: this.squareCovers(), metaLines: 2}));
  protected readonly skeletons = SKELETONS;
  protected readonly arrowClass =
    'flex size-8 items-center justify-center rounded-full text-text-secondary outline-none transition-colors ' +
    'hover:text-text-strong focus-visible:ring-2 focus-visible:ring-primary disabled:pointer-events-none disabled:opacity-30';

  private readonly stripPadding = signal(0);
  protected readonly virtualizer = injectVirtualizer<HTMLElement, HTMLElement>(() => {
    const cards = this.cards();
    return {
      scrollElement: this.strip(),
      horizontal: true,
      count: cards.length,
      getItemKey: (index: number) => cards[index].book.id,
      estimateSize: () => this.cardWidth(),
      gap: BOOK_ROW_CARD_GAP,
      scrollMargin: this.stripPadding(),
    };
  });
  private readonly visibleRange = computed<BrowseRenderedRange | null>(() => {
    const items = this.virtualizer.getVirtualItems();
    return items.length > 0 ? {start: items[0].index, end: items[items.length - 1].index} : null;
  }, {equal: sameRenderedRange});

  protected readonly atStart = computed(() => (this.virtualizer.scrollOffset() ?? 0) <= 1);
  protected readonly atEnd = computed(() => {
    const viewport = this.virtualizer.scrollRect()?.width ?? 0;
    const maxOffset = this.virtualizer.getTotalSize() + 2 * this.stripPadding() - viewport;
    return maxOffset - (this.virtualizer.scrollOffset() ?? 0) <= 1;
  });
  protected readonly arrowsVisible = computed(() => this.desktop() && !(this.atStart() && this.atEnd()));

  constructor() {
    afterRenderEffect(() => {
      const strip = this.strip()?.nativeElement;
      this.virtualizer.scrollRect();
      if (strip) {
        this.stripPadding.set(parseFloat(getComputedStyle(strip).paddingLeft));
      }
    });

    effect(() => {
      this.cardWidth();
      this.virtualizer.measure();
    });

    effect(() => {
      const range = this.visibleRange();
      if (range) {
        this.renderedRange.emit(range);
      }
    });
  }

  protected readBook(card: BookRowCard): void {
    this.bookNavigation.readBook(card.book, {bookType: card.file?.bookType});
  }

  protected scrollPage(direction: -1 | 1): void {
    const strip = this.strip()?.nativeElement;
    strip?.scrollBy({left: direction * strip.clientWidth});
  }
}
