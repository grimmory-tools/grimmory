import {BookType} from '../../features/book/model/book.model';
import {ALL_COMIC_METADATA_FIELDS} from './metadata-field.config';

const EBOOK_EMBEDDABLE: ReadonlySet<string> = new Set([
  'title', 'subtitle', 'authors', 'publisher', 'publishedDate', 'language',
  'categories', 'description', 'seriesName', 'seriesNumber', 'seriesTotal',
  'isbn10', 'isbn13', 'moods', 'tags', 'ageRating', 'contentRating', 'pageCount',
  'asin', 'amazonRating', 'amazonReviewCount', 'googleId',
  'goodreadsId', 'goodreadsRating', 'goodreadsReviewCount',
  'hardcoverId', 'hardcoverBookId', 'hardcoverRating', 'hardcoverReviewCount',
  'lubimyczytacId', 'lubimyczytacRating',
  'comicvineId', 'ranobedbId', 'ranobedbRating',
  'audibleId', 'audibleRating', 'audibleReviewCount',
]);

const CBX_EMBEDDABLE: ReadonlySet<string> = new Set([
  'title', 'description', 'publisher', 'publishedDate', 'language',
  'authors', 'categories', 'tags', 'seriesName', 'seriesNumber', 'seriesTotal',
  'pageCount', 'isbn13', 'ageRating',
  ...ALL_COMIC_METADATA_FIELDS.map(f => f.controlName),
]);

const AUDIOBOOK_EMBEDDABLE: ReadonlySet<string> = new Set([
  'title', 'authors', 'narrator', 'description', 'publisher', 'publishedDate',
  'categories', 'language', 'seriesName', 'seriesNumber', 'seriesTotal',
]);

const EMBEDDABLE_FIELDS: Partial<Record<BookType, ReadonlySet<string>>> = {
  EPUB: EBOOK_EMBEDDABLE,
  PDF: EBOOK_EMBEDDABLE,
  CBX: CBX_EMBEDDABLE,
  AUDIOBOOK: AUDIOBOOK_EMBEDDABLE,
};

export function isFieldEmbeddable(bookType: BookType | undefined, controlName: string): boolean {
  if (!bookType) return false;
  return EMBEDDABLE_FIELDS[bookType]?.has(controlName) ?? false;
}

export function hasMetadataWriter(bookType: BookType | undefined): boolean {
  if (!bookType) return false;
  return bookType in EMBEDDABLE_FIELDS;
}
