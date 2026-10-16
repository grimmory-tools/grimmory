import {CARD_META_LINE_HEIGHT, CARD_META_PADDING_TOP, CARD_META_TITLE_HEIGHT} from '../../../shared/components/cards/card.layout';

const AUTHOR_CARD_PHOTO_RATIO = 0.78;

export const AUTHOR_CARD_PHOTO_CLASS = 'mx-auto aspect-square w-[78%] rounded-full';

export function authorCardHeightForWidth(width: number): number {
  return Math.round(width * AUTHOR_CARD_PHOTO_RATIO)
    + CARD_META_PADDING_TOP
    + CARD_META_TITLE_HEIGHT
    + CARD_META_LINE_HEIGHT;
}
