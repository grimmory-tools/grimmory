import {
  formatRangeLabel,
  formatRangeToken,
  parseRangeToken,
  rangeTokenLabel,
} from '../../../shared/browse/facet-ranges';
import {browseFieldIndex, type BrowseField, type BrowseMissingFieldKeys} from '../../../shared/browse/fields';
import {browseFacetEntityIcon, LIBRARY_FACET, type BrowseFacetEntityLookup} from '../../../shared/browse/entity-facets';
import {formatFileSizeKb} from '../../../shared/util/file-size';
import {bookProgressPercentage} from '../data/book-actions';
import {
  BOOK_QUERY_FACET_KEYS,
  BOOK_QUERY_SORT_KEYS,
  isBookQueryFacetKey,
  isBookQuerySortKey,
  type BookQueryFacetKey,
  type BookQuerySortKey,
} from '../data/book-query-params';
import {type BookSummary} from '../data/book-response.models';
import {
  CLEAR_BOOK_READ_STATUS,
  readStatusFacetLabel,
  readStatusLabelKey,
} from '../components/book-read-status-options';
import {MATCH_SCORE_BANDS} from '../model/book-value-labels';

export type BookColumnKind = 'text' | 'number' | 'rating' | 'date' | 'fileSize' | 'readStatus';
export type BookColumnValue = string | number | undefined;

export interface BookFacetLabelDeps {
  readonly shelf: BrowseFacetEntityLookup;
  readonly library: BrowseFacetEntityLookup;
  readonly translate: (key: string) => string;
}

interface ColumnField {
  readonly key: string;
  readonly group: BookBrowseColumnGroupId;
  readonly defaultVisible: boolean;
  readonly defaultWidth: number;
  readonly minWidth?: number;
  readonly maxWidth?: number;
  readonly hideable?: false;
}

interface BookFieldDisplay {
  readonly kind: BookColumnKind;
  readonly value: (book: BookSummary) => BookColumnValue;
}

type BookBrowseField = BrowseField<BookQueryFacetKey, BookQuerySortKey, BookFacetLabelDeps> & {
  readonly facet?: {readonly itemValues?: (book: BookSummary) => readonly string[]};
  readonly sort?: {readonly detailLine?: false};
  readonly display?: BookFieldDisplay;
  readonly column?: ColumnField;
};

const MATCH_SCORE_LABELS = new Map(
  MATCH_SCORE_BANDS.flatMap(band => {
    const token = formatRangeToken(band);
    return token == null ? [] : [[token, band.labelKey] as const];
  }),
);

const CONTENT_RATING_LABEL_KEYS = new Map([
  ['EVERYONE', 'everyone'],
  ['TEEN', 'teen'],
  ['MATURE', 'mature'],
  ['ADULT', 'adult'],
  ['EXPLICIT', 'explicit'],
]);

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
    display: {kind: 'text', value: bookTitle},
    column: {key: 'title', group: 'publishing', defaultVisible: true, defaultWidth: 320, minWidth: 220, maxWidth: 520, hideable: false},
  },
  {
    labelKey: 'book.fields.author',
    facet: {key: 'author', openByDefault: true, itemValues: book => book.metadata?.authors ?? []},
    sort: {key: 'authorName', group: 'common', defaultDirection: 'asc', kind: 'alphabetical', detailLine: false},
    display: {kind: 'text', value: book => book.metadata?.authors?.join(', ') ?? ''},
    column: {key: 'authorName', group: 'publishing', defaultVisible: true, defaultWidth: 220},
  },
  {
    labelKey: 'book.fields.subtitle',
    sort: {key: 'subtitle', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    display: {kind: 'text', value: book => book.metadata?.subtitle},
  },
  {
    labelKey: 'book.fields.authorSurname',
    sort: {key: 'authorSortName', group: 'common', defaultDirection: 'asc', kind: 'alphabetical', detailLine: false},
  },
  {
    labelKey: 'book.fields.series',
    facet: {key: 'series', itemValues: book => book.metadata?.seriesName ? [book.metadata.seriesName] : []},
    sort: {key: 'seriesName', group: 'common', defaultDirection: 'asc', kind: 'alphabetical'},
    display: {kind: 'text', value: book => book.metadata?.seriesName ?? ''},
    column: {key: 'seriesName', group: 'publishing', defaultVisible: true, defaultWidth: 190},
  },
  {
    labelKey: 'book.fields.seriesNumber',
    sort: {key: 'seriesNumber', group: 'more', defaultDirection: 'asc', kind: 'numeric', detailLine: false},
    display: {kind: 'number', value: book => book.metadata?.seriesNumber},
    column: {key: 'seriesNumber', group: 'publishing', defaultVisible: true, defaultWidth: 168},
  },
  {
    labelKey: 'book.fields.publisher',
    facet: {key: 'publisher', itemValues: book => book.metadata?.publisher ? [book.metadata.publisher] : []},
    sort: {key: 'publisher', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    display: {kind: 'text', value: book => book.metadata?.publisher ?? ''},
    column: {key: 'publisher', group: 'publishing', defaultVisible: false, defaultWidth: 180},
  },
  {
    labelKey: 'book.fields.genre',
    facet: {key: 'genre', openByDefault: true, itemValues: book => book.metadata?.categories ?? []},
    display: {kind: 'text', value: book => book.metadata?.categories?.join(', ') ?? ''},
    column: {key: 'categories', group: 'categorization', defaultVisible: true, defaultWidth: 220},
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
    facet: {key: 'language', itemValues: book => book.metadata?.language ? [book.metadata.language] : []},
    sort: {key: 'language', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    display: {kind: 'text', value: book => book.metadata?.language ?? ''},
    column: {key: 'language', group: 'publishing', defaultVisible: true, defaultWidth: 112},
  },
  {
    labelKey: 'book.fields.narrator',
    facet: {key: 'narrator'},
    sort: {key: 'narrator', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    display: {kind: 'text', value: book => book.metadata?.narrator},
  },
  {
    labelKey: 'book.fields.fileType',
    facet: {key: 'file_type'},
  },
  {
    labelKey: 'book.fields.readStatus',
    facet: {
      key: 'read_status',
      valueLabel: (value, deps) => readStatusFacetLabel(value, deps.translate),
    },
    sort: {key: 'readStatus', group: 'more', defaultDirection: 'asc', kind: 'alphabetical'},
    display: {kind: 'readStatus', value: book => book.readStatus},
    column: {key: 'readStatus', group: 'reading', defaultVisible: true, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.personalRating',
    facet: {key: 'personal_rating', kind: 'domain', domain: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10']},
    sort: {key: 'personalRating', group: 'common', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.personalRating},
  },
  {
    labelKey: 'book.fields.amazonRating',
    facet: {key: 'amazon_rating', kind: 'stars', stars: 5},
    sort: {key: 'amazonRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.metadata?.amazonRating},
    column: {key: 'amazonRating', group: 'ratings', defaultVisible: false, defaultWidth: 124},
  },
  {
    labelKey: 'book.fields.amazonReviewCount',
    sort: {key: 'amazonReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'number', value: book => book.metadata?.amazonReviewCount},
    column: {key: 'amazonReviewCount', group: 'ratings', defaultVisible: false, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.goodreadsRating',
    facet: {key: 'goodreads_rating', kind: 'stars', stars: 5},
    sort: {key: 'goodreadsRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.metadata?.goodreadsRating},
    column: {key: 'goodreadsRating', group: 'ratings', defaultVisible: false, defaultWidth: 124},
  },
  {
    labelKey: 'book.fields.goodreadsReviewCount',
    sort: {key: 'goodreadsReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'number', value: book => book.metadata?.goodreadsReviewCount},
    column: {key: 'goodreadsReviewCount', group: 'ratings', defaultVisible: false, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.hardcoverRating',
    facet: {key: 'hardcover_rating', kind: 'stars', stars: 5},
    sort: {key: 'hardcoverRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.metadata?.hardcoverRating},
    column: {key: 'hardcoverRating', group: 'ratings', defaultVisible: false, defaultWidth: 124},
  },
  {
    labelKey: 'book.fields.hardcoverReviewCount',
    sort: {key: 'hardcoverReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'number', value: book => book.metadata?.hardcoverReviewCount},
    column: {key: 'hardcoverReviewCount', group: 'ratings', defaultVisible: false, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.ranobedbRating',
    facet: {key: 'ranobedb_rating', kind: 'stars', stars: 5},
    sort: {key: 'ranobedbRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.metadata?.ranobedbRating},
    column: {key: 'ranobedbRating', group: 'ratings', defaultVisible: false, defaultWidth: 124},
  },
  {
    labelKey: 'book.fields.lubimyczytacRating',
    facet: {key: 'lubimyczytac_rating', kind: 'stars', stars: 5},
    sort: {key: 'lubimyczytacRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.metadata?.lubimyczytacRating},
    column: {key: 'lubimyczytacRating', group: 'ratings', defaultVisible: false, defaultWidth: 124},
  },
  {
    labelKey: 'book.fields.audibleRating',
    facet: {key: 'audible_rating', kind: 'stars', stars: 5},
    sort: {key: 'audibleRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.metadata?.audibleRating},
    column: {key: 'audibleRating', group: 'ratings', defaultVisible: false, defaultWidth: 124},
  },
  {
    labelKey: 'book.fields.audibleReviewCount',
    sort: {key: 'audibleReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'number', value: book => book.metadata?.audibleReviewCount},
    column: {key: 'audibleReviewCount', group: 'ratings', defaultVisible: false, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.applebooksRating',
    facet: {key: 'applebooks_rating', kind: 'stars', stars: 5},
    sort: {key: 'applebooksRating', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'rating', value: book => book.metadata?.applebooksRating},
    column: {key: 'applebooksRating', group: 'ratings', defaultVisible: false, defaultWidth: 124},
  },
  {
    labelKey: 'book.fields.applebooksReviewCount',
    sort: {key: 'applebooksReviewCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'number', value: book => book.metadata?.applebooksReviewCount},
    column: {key: 'applebooksReviewCount', group: 'ratings', defaultVisible: false, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.pageCount',
    facet: {key: 'page_count', kind: 'range', valueLabel: value => rangeTokenLabel(value)},
    sort: {key: 'pageCount', group: 'more', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'number', value: book => book.metadata?.pageCount},
    column: {key: 'pageCount', group: 'publishing', defaultVisible: true, defaultWidth: 104},
  },
  {
    labelKey: 'book.fields.publishedDate',
    sort: {key: 'publishedDate', group: 'common', defaultDirection: 'desc', kind: 'calendar'},
    display: {kind: 'date', value: book => book.metadata?.publishedDate},
    column: {key: 'publishedDate', group: 'publishing', defaultVisible: true, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.publishedYear',
    facet: {key: 'published_year', kind: 'range', valueLabel: value => rangeTokenLabel(value)},
  },
  {
    labelKey: 'book.fields.addedOn',
    sort: {key: 'addedOn', group: 'common', defaultDirection: 'desc', kind: 'calendar'},
    display: {kind: 'date', value: book => book.addedOn},
    column: {key: 'addedOn', group: 'reading', defaultVisible: false, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.lastReadTime',
    sort: {key: 'lastReadTime', group: 'common', defaultDirection: 'desc', kind: 'clock'},
    display: {kind: 'date', value: book => book.lastReadTime},
    column: {key: 'lastReadTime', group: 'reading', defaultVisible: false, defaultWidth: 132},
  },
  {
    labelKey: 'book.fields.dateFinished',
    sort: {key: 'dateFinished', group: 'more', defaultDirection: 'desc', kind: 'calendar'},
    display: {kind: 'date', value: book => book.dateFinished},
  },
  {
    labelKey: 'book.fields.readingProgress',
    sort: {key: 'readingProgress', group: 'common', defaultDirection: 'desc', kind: 'numeric'},
    display: {kind: 'text', value: book => {
      const percentage = bookProgressPercentage(book);
      return percentage == null ? undefined : `${Math.round(percentage)}%`;
    }},
  },
  {
    labelKey: 'book.fields.fileName',
    display: {kind: 'text', value: book => book.primaryFile?.fileName ?? ''},
    column: {key: 'fileName', group: 'file', defaultVisible: false, defaultWidth: 240},
  },
  {
    labelKey: 'book.fields.fileSize',
    facet: {key: 'file_size', kind: 'range', fileSize: true, valueLabel: value => fileSizeRangeLabel(value)},
    display: {kind: 'fileSize', value: book => book.primaryFile?.fileSizeKb},
    column: {key: 'fileSizeKb', group: 'file', defaultVisible: false, defaultWidth: 112},
  },
  {
    labelKey: 'book.fields.isbn',
    display: {kind: 'text', value: book => book.metadata?.isbn13 ?? book.metadata?.isbn10 ?? ''},
    column: {key: 'isbn', group: 'publishing', defaultVisible: false, defaultWidth: 150},
  },
  {
    labelKey: 'book.fields.library',
    facet: LIBRARY_FACET,
  },
  {
    labelKey: 'book.fields.shelf',
    facet: {
      key: 'shelf',
      valueLabel: (value, deps) => deps.shelf(value)?.name ?? null,
      valueIcon: (value, deps) => browseFacetEntityIcon(deps.shelf(value)),
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
      bands: true,
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
  'applebooksRating',
  'applebooksReviewCount',
] as const satisfies readonly BookColumnKey[];

export const COLUMN_GROUP_ORDER = ['reading', 'publishing', 'file', 'categorization', 'ratings'] as const;

type RegisteredField = (typeof FIELDS)[number];
type SortKeyOf<Field> = Field extends {readonly sort: {readonly key: infer Key}} ? Key : never;
type ColumnKeyOf<Field> = Field extends {readonly column: {readonly key: infer Key}} ? Key : never;
export type BookColumnKey = ColumnKeyOf<RegisteredField>;
type NoDetailLineSortKey = SortKeyOf<Extract<RegisteredField, {readonly sort: {readonly detailLine: false}}>>;
type DisplayedSortKey = SortKeyOf<Extract<RegisteredField, {readonly display: unknown}>>;
export type BookDetailLineSortKey = Exclude<BookQuerySortKey, NoDetailLineSortKey>;
export type BookBrowseColumnGroupId = (typeof COLUMN_GROUP_ORDER)[number];
type AssertNever<Value extends never> = Value;
export type BookFieldsComplete = [
  AssertNever<BrowseMissingFieldKeys<RegisteredField, BookQueryFacetKey, BookQuerySortKey>>,
  AssertNever<Exclude<BookColumnKey, (typeof COLUMN_ORDER)[number]>>,
  AssertNever<Exclude<BookDetailLineSortKey, DisplayedSortKey>>,
];

export const BOOK_FIELDS = browseFieldIndex<BookBrowseField, BookQueryFacetKey, BookQuerySortKey, BookFacetLabelDeps>(
  FIELDS,
  {
    facetKeys: BOOK_QUERY_FACET_KEYS,
    sortKeys: BOOK_QUERY_SORT_KEYS,
    isFacetKey: isBookQueryFacetKey,
    isSortKey: isBookQuerySortKey,
  },
);

interface ColumnEntry extends BookBrowseField {
  readonly key: BookColumnKey;
  readonly column: ColumnField;
  readonly display: NonNullable<BookBrowseField['display']>;
}

export const COLUMNS_BY_KEY = new Map<string, ColumnEntry>(
  FIELDS.flatMap(field => 'column' in field ? [[field.column.key, {key: field.column.key, ...field}] as const] : []),
);
export function bookColumn(key: BookColumnKey): ColumnEntry {
  // SAFETY: BookColumnKey is derived from the fields that declare a column, so every key has an entry.
  return COLUMNS_BY_KEY.get(key)!;
}

export const COLUMNS: readonly ColumnEntry[] = COLUMN_ORDER.map(bookColumn);

export function bookReadStatusLabelKey(status: BookSummary['readStatus']): string | null {
  return status && status !== CLEAR_BOOK_READ_STATUS ? readStatusLabelKey(status) : null;
}
