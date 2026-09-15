import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {
  bookCardHeightForWidth,
  BOOK_CARD_META_PADDING_TOP,
} from './book-card.layout';
import {BookCardSkeletonComponent} from './book-card-skeleton.component';

describe('BookCardSkeletonComponent', () => {
  let fixture: ComponentFixture<BookCardSkeletonComponent>;
  let host: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({imports: [BookCardSkeletonComponent]});
    fixture = TestBed.createComponent(BookCardSkeletonComponent);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  function rowHeights(): number[] {
    return Array.from(host.querySelectorAll<HTMLElement>('[data-testid="skeleton-row"]'))
      .map(row => Number.parseFloat(row.style.height));
  }

  function metaPaddingTop(): number {
    const meta = host.querySelector<HTMLElement>('[data-testid="skeleton-meta"]');
    return Number.parseFloat(meta!.style.paddingTop);
  }

  function setMetaLines(metaLines: 2 | 3): void {
    fixture.componentRef.setInput('metaLines', metaLines);
    fixture.detectChanges();
  }

  it('adds up to the height the grid estimates, for both meta-line counts', () => {
    const width = 144;
    fixture.componentRef.setInput('squareCovers', true);

    for (const metaLines of [2, 3] as const) {
      setMetaLines(metaLines);
      const rendered = width + metaPaddingTop() + rowHeights().reduce((sum, row) => sum + row, 0);

      expect(metaPaddingTop()).toBe(BOOK_CARD_META_PADDING_TOP);
      expect(rendered).toBe(bookCardHeightForWidth(width, {square: true, metaLines}));
    }
  });
});
