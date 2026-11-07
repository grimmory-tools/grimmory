export const BOOK_CARD_COVER_ASPECT = 7 / 5;

export const BOOK_CARD_META_PADDING_TOP = 8;
export const BOOK_CARD_META_TITLE_HEIGHT = 17;
export const BOOK_CARD_META_AUTHOR_HEIGHT = 16;
export const BOOK_CARD_META_ACCESSORY_HEIGHT = 15;

export const BOOK_CARD_RADIUS_CLASS = 'rounded-[clamp(6px,calc(var(--card-width,150px)*0.08),12px)]';

export function bookCardAspectClass(square: boolean): string {
  return square ? 'aspect-square' : 'aspect-[5/7]';
}

export function bookCardHeightForWidth(
  width: number,
  {square, metaLines}: {square: boolean; metaLines: 2 | 3},
): number {
  const coverHeight = Math.round(width * (square ? 1 : BOOK_CARD_COVER_ASPECT));
  const metaHeight = BOOK_CARD_META_PADDING_TOP
    + BOOK_CARD_META_TITLE_HEIGHT
    + BOOK_CARD_META_AUTHOR_HEIGHT
    + (metaLines - 2) * BOOK_CARD_META_ACCESSORY_HEIGHT;
  return coverHeight + metaHeight;
}
