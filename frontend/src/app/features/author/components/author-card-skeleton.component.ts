import {ChangeDetectionStrategy, Component} from '@angular/core';

import {
  CARD_META_LINE_HEIGHT,
  CARD_META_PADDING_TOP,
  CARD_META_TITLE_HEIGHT,
} from '../../../shared/components/cards/card.layout';
import {AUTHOR_CARD_PHOTO_CLASS} from './author-card.layout';

@Component({
  selector: 'app-author-card-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'block', 'aria-hidden': 'true'},
  template: `
    <div [class]="photoClass"></div>
    <div class="flex flex-col items-center px-0.5" [style.paddingTop.px]="metaPaddingTop">
      <div class="flex w-full items-center justify-center" [style.height.px]="nameHeight">
        <div [class]="barClass + ' h-[11px] w-2/3'"></div>
      </div>
      <div class="flex w-full items-center justify-center" [style.height.px]="countHeight">
        <div [class]="barClass + ' h-[9px] w-1/3'"></div>
      </div>
    </div>
  `,
})
export class AuthorCardSkeletonComponent {
  protected readonly metaPaddingTop = CARD_META_PADDING_TOP;
  protected readonly nameHeight = CARD_META_TITLE_HEIGHT;
  protected readonly countHeight = CARD_META_LINE_HEIGHT;

  protected readonly barClass = 'rounded bg-skeleton-base animate-skeleton motion-reduce:animate-none';
  protected readonly photoClass =
    `${AUTHOR_CARD_PHOTO_CLASS} bg-skeleton-base animate-skeleton motion-reduce:animate-none`;
}
