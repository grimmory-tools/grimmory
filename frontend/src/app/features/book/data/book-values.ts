import {BOOK_READ_STATUSES, type BookReadStatus} from './book-response.models';

function findBookValue<Value extends string | number>(
  values: readonly Value[],
  raw: string | number | null | undefined,
): Value | null {
  return raw == null ? null : values.find(value => String(value) === String(raw)) ?? null;
}

export const BOOK_READ_STATUS_LABEL_KEYS = {
  UNREAD: 'book.filter.readStatus.unread',
  READING: 'book.filter.readStatus.reading',
  RE_READING: 'book.filter.readStatus.reReading',
  READ: 'book.filter.readStatus.read',
  PARTIALLY_READ: 'book.filter.readStatus.partiallyRead',
  PAUSED: 'book.filter.readStatus.paused',
  WONT_READ: 'book.filter.readStatus.wontRead',
  ABANDONED: 'book.filter.readStatus.abandoned',
  UNSET: 'book.filter.readStatus.unset',
} as const satisfies Record<BookReadStatus, string>;

export const CLEAR_BOOK_READ_STATUS = 'UNSET' satisfies BookReadStatus;

export type BookReadStatusTarget = Exclude<BookReadStatus, typeof CLEAR_BOOK_READ_STATUS>;

export const BOOK_READ_STATUS_TARGETS: readonly BookReadStatusTarget[] = BOOK_READ_STATUSES.filter(
  (status): status is BookReadStatusTarget => status !== CLEAR_BOOK_READ_STATUS,
);

export function bookReadStatusLabelKey(raw: string | null | undefined): string | null {
  const status = findBookValue(BOOK_READ_STATUSES, raw);
  return status ? BOOK_READ_STATUS_LABEL_KEYS[status] : null;
}

export const BOOK_CONTENT_RATINGS = ['EVERYONE', 'TEEN', 'MATURE', 'ADULT', 'EXPLICIT'] as const;
export type BookContentRating = typeof BOOK_CONTENT_RATINGS[number];

export const BOOK_CONTENT_RATING_LABEL_KEYS = {
  EVERYONE: 'book.filter.contentRating.everyone',
  TEEN: 'book.filter.contentRating.teen',
  MATURE: 'book.filter.contentRating.mature',
  ADULT: 'book.filter.contentRating.adult',
  EXPLICIT: 'book.filter.contentRating.explicit',
} as const satisfies Record<BookContentRating, string>;

export function bookContentRatingLabelKey(raw: string | null | undefined): string | null {
  const rating = findBookValue(BOOK_CONTENT_RATINGS, raw?.toUpperCase());
  return rating ? BOOK_CONTENT_RATING_LABEL_KEYS[rating] : null;
}

export const BOOK_AGE_RATINGS = [0, 6, 10, 13, 16, 18, 21] as const;
export type BookAgeRating = typeof BOOK_AGE_RATINGS[number];

export const BOOK_AGE_RATING_LABEL_KEYS = {
  0: 'book.filter.ageRating.allAges',
  6: 'book.filter.ageRating.6plus',
  10: 'book.filter.ageRating.10plus',
  13: 'book.filter.ageRating.13plus',
  16: 'book.filter.ageRating.16plus',
  18: 'book.filter.ageRating.18plus',
  21: 'book.filter.ageRating.21plus',
} as const satisfies Record<BookAgeRating, string>;

export function bookAgeRatingLabelKey(raw: number | string | null | undefined): string | null {
  const rating = findBookValue(BOOK_AGE_RATINGS, raw);
  return rating == null ? null : BOOK_AGE_RATING_LABEL_KEYS[rating];
}

export interface BookMatchScoreBand {
  readonly min: number;
  readonly max?: number;
  readonly maxExclusive?: true;
  readonly labelKey: string;
}

export const BOOK_MATCH_SCORE_BANDS: readonly BookMatchScoreBand[] = [
  {min: 95, labelKey: 'book.filter.matchScore.outstanding'},
  {min: 90, max: 95, maxExclusive: true, labelKey: 'book.filter.matchScore.excellent'},
  {min: 80, max: 90, maxExclusive: true, labelKey: 'book.filter.matchScore.great'},
  {min: 70, max: 80, maxExclusive: true, labelKey: 'book.filter.matchScore.good'},
  {min: 50, max: 70, maxExclusive: true, labelKey: 'book.filter.matchScore.fair'},
  {min: 30, max: 50, maxExclusive: true, labelKey: 'book.filter.matchScore.weak'},
  {min: 0, max: 30, maxExclusive: true, labelKey: 'book.filter.matchScore.poor'},
];

export function findBookMatchScoreBand(score: number | null | undefined): BookMatchScoreBand | null {
  if (score == null) {
    return null;
  }
  return BOOK_MATCH_SCORE_BANDS.find(band => score >= band.min && (band.max == null || score < band.max)) ?? null;
}
