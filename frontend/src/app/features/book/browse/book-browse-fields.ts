import {
  formatRangeLabel,
  formatRangeToken,
  parseRangeToken,
  type BrowseFacetBucket,
} from '../../../shared/browse/facet-ranges';
import {
  type BrowseFacetKind,
  type BrowseFacetValueOrder,
} from '../../../shared/browse/facets';
import {type BrowseSortKind} from '../../../shared/browse/sort';
import {toIconSelection, type IconSelection, type IconType} from '../../../shared/icons/icon-selection';
import {formatFileSizeKb} from '../../../shared/util/file-size';
import {
  type BookQueryFacetKey,
  type BookQuerySortKey,
  type SortDirection,
} from '../data/book-query-params';
import {type BookSummary, type KnownBookReadStatus} from '../data/book-response.models';
import {MATCH_SCORE_BANDS} from '../model/book-value-labels';

export type BookColumnKind = 'text' | 'number' | 'rating' | 'date' | 'fileSize' | 'readStatus';
export type BookColumnValue = string | number | undefined;

export interface BookFacetEntity {
  readonly name: string;
  readonly icon?: string | null;
  readonly iconType?: IconType | null;
}

export interface BookFacetLabelDeps {
  readonly shelf: (id: string) => BookFacetEntity | undefined;
  readonly library: (id: string) => BookFacetEntity | undefined;
  readonly translate: (key: string) => string;
}

function entityIcon(entity: BookFacetEntity | undefined): IconSelection | null {
  return entity?.icon ? toIconSelection(entity.icon, entity.iconType) : null;
}

interface FacetField {
  readonly key: BookQueryFacetKey;
  readonly kind?: BrowseFacetKind;
  readonly order?: BrowseFacetValueOrder;
  readonly domain?: readonly string[];
  readonly buckets?: readonly BrowseFacetBucket[];
  readonly fileSize?: true;
  readonly openByDefault?: true;
  readonly bookValues?: (book: BookSummary) => readonly string[];
  readonly valueLabel?: (value: string, deps: BookFacetLabelDeps) => string | null;
  readonly valueIcon?: (value: string, deps: BookFacetLabelDeps) => IconSelection | null;
}

interface SortField {
  readonly key: BookQuerySortKey;
  readonly group: 'common' | 'more';
  readonly defaultDirection: SortDirection;
  readonly kind: BrowseSortKind;
  readonly directions?: readonly SortDirection[];
  readonly detailLine?: false;
}

interface ColumnField {
  readonly key: string;
  readonly group: BookBrowseColumnGroupId;
  readonly defaultVisible: boolean;
  readonly defaultWidth: number;
  readonly minWidth?: number;
  readonly maxWidth?: number;
  readonly hideable?: false;
  readonly kind?: Exclude<BookColumnKind, 'text'>;
  readonly value: (book: BookSummary) => BookColumnValue;
}

interface BookBrowseField {
  readonly labelKey: string;
  readonly facet?: FacetField;
  readonly sort?: SortField;
  readonly column?: ColumnField;
}

const RATING_BUCKETS: readonly BrowseFacetBucket[] = [
  {min: 0, max: 1, stars: 1},
  {min: 1, max: 2, stars: 2},
  {min: 2, max: 3, stars: 3},
  {min: 3, max: 4, stars: 4},
  {min: 4, max: 4.5, stars: 4.5},
  {min: 4.5, stars: 5},
];

const MATCH_SCORE_LABELS = new Map(
  MATCH_SCORE_BANDS.flatMap(band => {
    const token = formatRangeToken(band);
    return token == null ? [] : [[token, band.labelKey] as const];
  }),
);

const READ_STATUS_LABEL_KEYS = new Map<string, string>(Object.entries({
  UNREAD: 'unread',
  READING: 'reading',
  RE_READING: 'reReading',
  READ: 'read',
  PARTIALLY_READ: 'partiallyRead',
  PAUSED: 'paused',
  WONT_READ: 'wontRead',
  ABANDONED: 'abandoned',
  UNSET: 'unset',
} satisfies Record<KnownBookReadStatus, string>));

const CONTENT_RATING_LABEL_KEYS = new Map([
  ['EVERYONE', 'everyone'],
  ['TEEN', 'teen'],
  ['MATURE', 'mature'],
  ['ADULT', 'adult'],
  ['EXPLICIT', 'explicit'],
]);

function rangeTokenLabel(token: string): string | null {
  const range = parseRangeToken(token);
  return range ? formatRangeLabel(range) : null;
}

function fileSizeRangeLabel(token: string): string | null {
  const range = parseRangeToken(token);
  if (!range) {
    return null;
  }
  return formatRangeLabel({
    min: range.min == null ? null : formatFileSizeKb(range.min),
    max: range.max == null ? null : formatFileSizeKb(range.max),
  });
}

export function bookTitle(book: BookSummary): string {
  return book.metadata?.title || book.primaryFile?.fileName || '';
}

const FIELDS = [
  {
    labelKey: 'book.fields.random',
    sort: {key: 'random', group: 'common', defaultDirection: 'asc', kind: 'random', directions: ['asc'], detailLine: false},
  },
  {
    labelKey: 'book.fields.title',
    sort: {key: 'title', group: 'common', defaultDirection: 'asc', kind: 'alphabetical', detailLine: false},
    column: {key: 'title', group: 'publishing', defaultVisible: true,
      defaultWidth: 320, minWidth: 220, maxWidth: 520, hideable: false,
      value: bookTitle},
  },
  {
    labelKey: 'book.fields.author',
    facet: {key: 'author', openByDefault: true, bookValues: book => book.metadata?.authors ?? []},
    sort: {key: 'authorName', group: 'common', defaultDirection: 'asc', kind: 'alphabetical', detailLine: false},
    column: {key: 'authorName', group: 'publishing', defaultVisible: true,
      defaultWidth: 220, value: book => book.metadata?.authors?.join(', ') ?? ''},
  },
  {
    labelKey: 'book.fields.authorSurname',
    sort: {key: 'authorSortName', group: 'common', defaultDirection: 'asc', kind: 'alphabetical', detailLine: false},
  },
  {
    labelKey: 'book.fields.series',
    facet: {key: 'series', bookValues: book => book.metadata?.seriesName ? [book.metadata.seriesName] : []},
    sort: {key: 'seriesName', group: 'common', defaultDirection: 'asc', kind: 'alphabetical'},
    column: {key: 'seriesName', group: 'publishing', defaultVisible: true,
      defaultWidth: 190, value: book => book.metadata?.seriesName ?? ''},
  },
  {
    labelKey: 'book.fields.seriesNumber',
    sort: {key: 'seriesNumber', group: 'more', defaultDirection: 'asc', kind: 'numeric', detailLine: false},
    column: {key: 'seriesNumber', group: 'publishing', defaultVisible: true,
      defaultWidth: 168, kind: 'number', value: book => book.metadata?.seriesNumber},
  },
  {
    labelKey: 'book.fields.publisher',
    facet: {key: 'publisher', bookValues: book => book.metadata?.publisher ? [book.metadata.publisher] : []},
    sort: {key: 'publisher', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    column: {key: 'publisher', group: 'publishing', defaultVisible: false,
      defaultWidth: 180, value: book => book.metadata?.publisher ?? ''},
  },
  {
    labelKey: 'book.fields.genre',
    facet: {key: 'genre', openByDefault: true, bookValues: book => book.metadata?.categories ?? []},
    column: {key: 'categories', group: 'categorization', defaultVisible: true,
      defaultWidth: 220, value: book => book.metadata?.categories?.join(', ') ?? ''},
  },
  {
    labelKey: 'book.fields.tag',
    facet: {key: 'tag', openByDefault: true},
  },
  {
    labelKey: 'book.fields.mood',
    facet: {key: 'mood'},
  },
  {
    labelKey: 'book.fields.language',
    facet: {key: 'language', bookValues: book => book.metadata?.language ? [book.metadata.language] : []},
    sort: {key: 'language', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    column: {key: 'language', group: 'publishing', defaultVisible: true,
      defaultWidth: 112, value: book => book.metadata?.language ?? ''},
  },
  {
    labelKey: 'book.fields.narrator',
    facet: {key: 'narrator'},
    sort: {key: 'narrator', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
  },
  {
    labelKey: 'book.fields.fileType',
    facet: {key: 'file_type'},
  },
  {
    labelKey: 'book.fields.readStatus',
    facet: {
      key: 'read_status',
      valueLabel: (value, deps) => {
        const labelKey = READ_STATUS_LABEL_KEYS.get(value);
        return labelKey ? deps.translate(`book.filter.readStatus.${labelKey}`) : null;
      },
    },
    sort: {key: 'readStatus', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    column: {key: 'readStatus', group: 'reading', defaultVisible: true,
      defaultWidth: 132, kind: 'readStatus', value: book => book.readStatus},
  },
  {
    labelKey: 'book.fields.personalRating',
    facet: {key: 'personal_rating', domain: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10']},
    sort: {key: 'personalRating', group: 'common', defaultDirection: 'desc', kind: 'numeric'},
  },
  {
    labelKey: 'book.fields.amazonRating',
    facet: {key: 'amazon_rating', buckets: RATING_BUCKETS},
    sort: {key: 'amazonRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'amazonRating', group: 'ratings', defaultVisible: false,
      defaultWidth: 124, kind: 'rating', value: book => book.metadata?.amazonRating},
  },
  {
    labelKey: 'book.fields.amazonReviewCount',
    sort: {key: 'amazonReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'amazonReviewCount', group: 'ratings', defaultVisible: false,
      defaultWidth: 132, kind: 'number', value: book => book.metadata?.amazonReviewCount},
  },
  {
    labelKey: 'book.fields.goodreadsRating',
    facet: {key: 'goodreads_rating', buckets: RATING_BUCKETS},
    sort: {key: 'goodreadsRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'goodreadsRating', group: 'ratings', defaultVisible: false,
      defaultWidth: 124, kind: 'rating', value: book => book.metadata?.goodreadsRating},
  },
  {
    labelKey: 'book.fields.goodreadsReviewCount',
    sort: {key: 'goodreadsReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'goodreadsReviewCount', group: 'ratings', defaultVisible: false,
      defaultWidth: 132, kind: 'number', value: book => book.metadata?.goodreadsReviewCount},
  },
  {
    labelKey: 'book.fields.hardcoverRating',
    facet: {key: 'hardcover_rating', buckets: RATING_BUCKETS},
    sort: {key: 'hardcoverRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'hardcoverRating', group: 'ratings', defaultVisible: false,
      defaultWidth: 124, kind: 'rating', value: book => book.metadata?.hardcoverRating},
  },
  {
    labelKey: 'book.fields.hardcoverReviewCount',
    sort: {key: 'hardcoverReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'hardcoverReviewCount', group: 'ratings', defaultVisible: false,
      defaultWidth: 132, kind: 'number', value: book => book.metadata?.hardcoverReviewCount},
  },
  {
    labelKey: 'book.fields.ranobedbRating',
    facet: {key: 'ranobedb_rating', buckets: RATING_BUCKETS},
    sort: {key: 'ranobedbRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'ranobedbRating', group: 'ratings', defaultVisible: false,
      defaultWidth: 124, kind: 'rating', value: book => book.metadata?.ranobedbRating},
  },
  {
    labelKey: 'book.fields.lubimyczytacRating',
    facet: {key: 'lubimyczytac_rating', buckets: RATING_BUCKETS},
    sort: {key: 'lubimyczytacRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'lubimyczytacRating', group: 'ratings', defaultVisible: false,
      defaultWidth: 124, kind: 'rating', value: book => book.metadata?.lubimyczytacRating},
  },
  {
    labelKey: 'book.fields.audibleRating',
    facet: {key: 'audible_rating', buckets: RATING_BUCKETS},
    sort: {key: 'audibleRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'audibleRating', group: 'ratings', defaultVisible: false,
      defaultWidth: 124, kind: 'rating', value: book => book.metadata?.audibleRating},
  },
  {
    labelKey: 'book.fields.audibleReviewCount',
    sort: {key: 'audibleReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'audibleReviewCount', group: 'ratings', defaultVisible: false,
      defaultWidth: 132, kind: 'number', value: book => book.metadata?.audibleReviewCount},
  },
  {
    labelKey: 'book.fields.pageCount',
    facet: {key: 'page_count', kind: 'range', valueLabel: value => rangeTokenLabel(value)},
    sort: {key: 'pageCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    column: {key: 'pageCount', group: 'publishing', defaultVisible: true,
      defaultWidth: 104, kind: 'number', value: book => book.metadata?.pageCount},
  },
  {
    labelKey: 'book.fields.publishedDate',
    sort: {key: 'publishedDate', group: 'common', defaultDirection: 'desc', kind: 'calendar'},
    column: {key: 'publishedDate', group: 'publishing', defaultVisible: true,
      defaultWidth: 132, kind: 'date', value: book => book.metadata?.publishedDate},
  },
  {
    labelKey: 'book.fields.publishedYear',
    facet: {key: 'published_year', kind: 'range', valueLabel: value => rangeTokenLabel(value)},
  },
  {
    labelKey: 'book.fields.addedOn',
    sort: {key: 'addedOn', group: 'common', defaultDirection: 'desc', kind: 'calendar'},
    column: {key: 'addedOn', group: 'reading', defaultVisible: false,
      defaultWidth: 132, kind: 'date', value: book => book.addedOn},
  },
  {
    labelKey: 'book.fields.lastReadTime',
    sort: {key: 'lastReadTime', group: 'common', defaultDirection: 'desc', kind: 'clock'},
    column: {key: 'lastReadTime', group: 'reading', defaultVisible: false,
      defaultWidth: 132, kind: 'date', value: book => book.lastReadTime},
  },
  {
    labelKey: 'book.fields.dateFinished',
    sort: {key: 'dateFinished', group: 'more', defaultDirection: 'desc', kind: 'calendar'},
  },
  {
    labelKey: 'book.fields.readingProgress',
    sort: {key: 'readingProgress', group: 'common', defaultDirection: 'desc', kind: 'numeric'},
  },
  {
    labelKey: 'book.fields.fileName',
    column: {key: 'fileName', group: 'file', defaultVisible: false,
      defaultWidth: 240, value: book => book.primaryFile?.fileName ?? ''},
  },
  {
    labelKey: 'book.fields.fileSize',
    facet: {key: 'file_size', kind: 'range', fileSize: true, valueLabel: value => fileSizeRangeLabel(value)},
    column: {key: 'fileSizeKb', group: 'file', defaultVisible: false,
      defaultWidth: 112, kind: 'fileSize', value: book => book.primaryFile?.fileSizeKb},
  },
  {
    labelKey: 'book.fields.isbn',
    column: {key: 'isbn', group: 'publishing', defaultVisible: false,
      defaultWidth: 150, value: book => book.metadata?.isbn13 ?? book.metadata?.isbn10 ?? ''},
  },
  {
    labelKey: 'book.fields.library',
    facet: {
      key: 'library',
      valueLabel: (value, deps) => deps.library(value)?.name ?? null,
      valueIcon: (value, deps) => entityIcon(deps.library(value)),
    },
  },
  {
    labelKey: 'book.fields.shelf',
    facet: {
      key: 'shelf',
      valueLabel: (value, deps) => deps.shelf(value)?.name ?? null,
      valueIcon: (value, deps) => entityIcon(deps.shelf(value)),
    },
  },
  {labelKey: 'book.fields.ageRating', facet: {key: 'age_rating', order: 'asc'}},
  {
    labelKey: 'book.fields.contentRating',
    facet: {
      key: 'content_rating',
      order: ['EVERYONE', 'TEEN', 'MATURE', 'ADULT', 'EXPLICIT'],
      valueLabel: (value, deps) => {
        const labelKey = CONTENT_RATING_LABEL_KEYS.get(value.toUpperCase());
        return labelKey ? deps.translate(`book.filter.contentRating.${labelKey}`) : null;
      },
    },
  },
  {
    labelKey: 'book.fields.matchScore',
    facet: {
      key: 'match_score',
      kind: 'range',
      buckets: MATCH_SCORE_BANDS,
      valueLabel: (value, deps) => {
        const labelKey = MATCH_SCORE_LABELS.get(value);
        return labelKey ? deps.translate(`book.filter.matchScore.${labelKey}`) : rangeTokenLabel(value);
      },
    },
  },
  {
    labelKey: 'book.fields.shelfStatus',
    facet: {
      key: 'shelf_status',
      valueLabel: (value, deps) =>
        value === 'shelved' || value === 'unshelved'
          ? deps.translate(`book.filter.shelfStatus.${value}`)
          : null,
    },
  },
  {labelKey: 'book.fields.comicCharacter', facet: {key: 'comic_character'}},
  {labelKey: 'book.fields.comicTeam', facet: {key: 'comic_team'}},
  {labelKey: 'book.fields.comicLocation', facet: {key: 'comic_location'}},
  {labelKey: 'book.fields.comicCreator', facet: {key: 'comic_creator'}},
] as const satisfies readonly BookBrowseField[];

const COLUMN_ORDER = [
  'title',
  'authorName',
  'readStatus',
  'seriesName',
  'seriesNumber',
  'publishedDate',
  'pageCount',
  'language',
  'lastReadTime',
  'addedOn',
  'publisher',
  'categories',
  'fileName',
  'fileSizeKb',
  'isbn',
  'amazonRating',
  'amazonReviewCount',
  'goodreadsRating',
  'goodreadsReviewCount',
  'hardcoverRating',
  'hardcoverReviewCount',
  'ranobedbRating',
  'lubimyczytacRating',
  'audibleRating',
  'audibleReviewCount',
] as const satisfies readonly BookColumnKey[];

export const COLUMN_GROUP_ORDER = ['reading', 'publishing', 'file', 'categorization', 'ratings'] as const;

type RegisteredField = (typeof FIELDS)[number];
type SortKeyOf<Field> = Field extends {readonly sort: {readonly key: infer Key}} ? Key : never;
type FacetKeyOf<Field> = Field extends {readonly facet: {readonly key: infer Key}} ? Key : never;
type ColumnKeyOf<Field> = Field extends {readonly column: {readonly key: infer Key}} ? Key : never;
export type BookColumnKey = ColumnKeyOf<RegisteredField>;
type NoDetailLineSortKey = SortKeyOf<Extract<RegisteredField, {readonly sort: {readonly detailLine: false}}>>;
export type BookDetailLineSortKey = Exclude<BookQuerySortKey, NoDetailLineSortKey>;
export type BookBrowseColumnGroupId = (typeof COLUMN_GROUP_ORDER)[number];
type AssertNever<Value extends never> = Value;
export type BookFieldsComplete = [
  AssertNever<Exclude<BookQuerySortKey, SortKeyOf<RegisteredField>>>,
  AssertNever<Exclude<BookQueryFacetKey, FacetKeyOf<RegisteredField>>>,
  AssertNever<Exclude<BookColumnKey, (typeof COLUMN_ORDER)[number]>>,
];

type SortEntry = BookBrowseField & {readonly sort: SortField};
type FacetEntry = BookBrowseField & {readonly facet: FacetField};
interface ColumnEntry extends BookBrowseField {
  readonly key: BookColumnKey;
  readonly column: ColumnField;
}

export const SORT_FIELDS = new Map<BookQuerySortKey, SortEntry>(
  FIELDS.flatMap(field => 'sort' in field ? [[field.sort.key, field] as const] : []),
);
export const FACET_FIELDS = new Map<BookQueryFacetKey, FacetEntry>(
  FIELDS.flatMap(field => 'facet' in field ? [[field.facet.key, field] as const] : []),
);
export const COLUMNS_BY_KEY = new Map<string, ColumnEntry>(
  FIELDS.flatMap(field => 'column' in field ? [[field.column.key, {key: field.column.key, ...field}] as const] : []),
);
export const OPEN_RAIL_FACETS: ReadonlySet<BookQueryFacetKey> = new Set(
  [...FACET_FIELDS.values()].flatMap(field => field.facet.openByDefault ? [field.facet.key] : []),
);
export const COLUMNS: readonly ColumnEntry[] = COLUMN_ORDER.map(key => COLUMNS_BY_KEY.get(key)!);

export function bookReadStatusLabelKey(status: BookSummary['readStatus']): string | null {
  const key = status && status !== 'UNSET' ? READ_STATUS_LABEL_KEYS.get(status) : undefined;
  return key ? `book.filter.readStatus.${key}` : null;
}
