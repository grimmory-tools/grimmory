import {booleanAttribute, ChangeDetectionStrategy, Component, computed, input} from '@angular/core';

import {
  bookCardAspectClass,
  BOOK_CARD_META_ACCESSORY_HEIGHT,
  BOOK_CARD_META_AUTHOR_HEIGHT,
  BOOK_CARD_META_PADDING_TOP,
  BOOK_CARD_META_TITLE_HEIGHT,
  BOOK_CARD_RADIUS_CLASS,
} from './book-card.layout';

@Component({
  selector: 'app-book-card-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'block'},
  template: `
    <div class="w-full min-w-0" aria-hidden="true">
      <div [class]="coverClass()" data-testid="skeleton-cover"></div>
      <div class="px-0.5" [style.paddingTop.px]="metaPaddingTop" data-testid="skeleton-meta">
        <div class="flex items-center" [style.height.px]="titleHeight" data-testid="skeleton-row">
          <div [class]="barClass + ' h-[11px] w-3/4'"></div>
        </div>
        <div class="flex items-center" [style.height.px]="authorHeight" data-testid="skeleton-row">
          <div [class]="barClass + ' h-[9px] w-1/2'"></div>
        </div>
        @if (metaLines() === 3) {
          <div class="flex items-center" [style.height.px]="accessoryHeight" data-testid="skeleton-row">
            <div [class]="barClass + ' h-[9px] w-1/3'"></div>
          </div>
        }
      </div>
    </div>
  `,
})
export class BookCardSkeletonComponent {
  readonly squareCovers = input(false, {transform: booleanAttribute});
  readonly metaLines = input<2 | 3>(2);

  protected readonly metaPaddingTop = BOOK_CARD_META_PADDING_TOP;
  protected readonly titleHeight = BOOK_CARD_META_TITLE_HEIGHT;
  protected readonly authorHeight = BOOK_CARD_META_AUTHOR_HEIGHT;
  protected readonly accessoryHeight = BOOK_CARD_META_ACCESSORY_HEIGHT;

  protected readonly barClass = 'rounded bg-skeleton-base animate-skeleton motion-reduce:animate-none';
  protected readonly coverClass = computed(() =>
    `w-full ${BOOK_CARD_RADIUS_CLASS} bg-skeleton-base animate-skeleton motion-reduce:animate-none ${
      bookCardAspectClass(this.squareCovers())
    }`,
  );
}
