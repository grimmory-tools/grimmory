import type {BookDetailComicMetadata, BookDetailMetadata} from '../../features/book/data/book-response.models';
import type {BookQueryFacetKey} from '../../features/book/data/book-query-params';
import type {MetadataProviderFieldName} from './metadata-providers';

export type MetadataFieldValueType = 'string' | 'text' | 'date' | 'number' | 'boolean' | 'list' | 'set';

export interface MetadataLock<Name extends string = string> {
  readonly name: Name;
  readonly lockName: `${Name}Locked`;
}

export interface MetadataField<Name extends string = string> extends MetadataLock<Name> {
  readonly valueType: MetadataFieldValueType;
}

type LockedFieldsOf<Model> = Extract<keyof Model, `${string}Locked`> extends `${infer Name}Locked` ? Name : never;

interface FieldFacts {
  readonly suggestions?: BookQueryFacetKey;
  readonly options?: readonly (string | number)[];
}

type BookLockableName = Exclude<LockedFieldsOf<BookDetailMetadata>, MetadataProviderFieldName>;
type ComicLockableName = Exclude<LockedFieldsOf<BookDetailComicMetadata>, LockedFieldsOf<BookDetailMetadata>>;

const BOOK_FIELD_TABLE = [
  {name: 'title', valueType: 'string'},
  {name: 'subtitle', valueType: 'string'},
  {name: 'authors', valueType: 'list', suggestions: 'author'},
  {name: 'publisher', valueType: 'string', suggestions: 'publisher'},
  {name: 'publishedDate', valueType: 'date'},
  {name: 'categories', valueType: 'set', suggestions: 'genre'},
  {name: 'moods', valueType: 'set', suggestions: 'mood'},
  {name: 'tags', valueType: 'set', suggestions: 'tag'},
  {name: 'seriesName', valueType: 'string', suggestions: 'series'},
  {name: 'seriesNumber', valueType: 'number'},
  {name: 'seriesTotal', valueType: 'number'},
  {name: 'language', valueType: 'string'},
  {name: 'isbn10', valueType: 'string'},
  {name: 'isbn13', valueType: 'string'},
  {name: 'pageCount', valueType: 'number'},
  {name: 'ageRating', valueType: 'number', options: [0, 6, 10, 13, 16, 18, 21]},
  {name: 'contentRating', valueType: 'string', options: ['EVERYONE', 'TEEN', 'MATURE', 'ADULT', 'EXPLICIT']},
  {name: 'description', valueType: 'text'},
  {name: 'narrator', valueType: 'string'},
  {name: 'abridged', valueType: 'boolean'},
] as const satisfies readonly (FieldFacts & {readonly name: BookLockableName; readonly valueType: MetadataFieldValueType})[];

const COMIC_FIELD_TABLE = [
  {name: 'issueNumber', valueType: 'string'},
  {name: 'volumeName', valueType: 'string'},
  {name: 'volumeNumber', valueType: 'number'},
  {name: 'storyArc', valueType: 'string'},
  {name: 'storyArcNumber', valueType: 'number'},
  {name: 'alternateSeries', valueType: 'string'},
  {name: 'alternateIssue', valueType: 'string'},
  {name: 'imprint', valueType: 'string'},
  {name: 'format', valueType: 'string'},
  {name: 'readingDirection', valueType: 'string'},
  {name: 'webLink', valueType: 'string'},
  {name: 'blackAndWhite', valueType: 'boolean'},
  {name: 'manga', valueType: 'boolean'},
  {name: 'pencillers', valueType: 'set'},
  {name: 'inkers', valueType: 'set'},
  {name: 'colorists', valueType: 'set'},
  {name: 'letterers', valueType: 'set'},
  {name: 'coverArtists', valueType: 'set'},
  {name: 'editors', valueType: 'set'},
  {name: 'characters', valueType: 'set', suggestions: 'comic_character'},
  {name: 'teams', valueType: 'set', suggestions: 'comic_team'},
  {name: 'locations', valueType: 'set', suggestions: 'comic_location'},
  {name: 'notes', valueType: 'text'},
] as const satisfies readonly (FieldFacts & {readonly name: ComicLockableName; readonly valueType: MetadataFieldValueType})[];

export type BookFieldName = (typeof BOOK_FIELD_TABLE)[number]['name'];
export type ComicFieldName = (typeof COMIC_FIELD_TABLE)[number]['name'];
export type MetadataFieldName = BookFieldName | ComicFieldName | MetadataProviderFieldName;
export type BookLockName = BookFieldName | keyof typeof BOOK_NON_FIELD_LOCKS;

export interface BookField extends MetadataField<BookFieldName>, FieldFacts {}

export interface ComicField extends MetadataField<ComicFieldName>, FieldFacts {}

function lockFor<Name extends string>(name: Name): MetadataLock<Name> {
  return {name, lockName: `${name}Locked`};
}

export const BOOK_FIELDS: readonly BookField[] = BOOK_FIELD_TABLE.map(entry => ({...entry, ...lockFor(entry.name)}));

export function bookFieldsNamed<Name extends BookFieldName>(names: readonly Name[]): readonly (BookField & {readonly name: Name})[] {
  const wanted: readonly BookFieldName[] = names;
  return BOOK_FIELDS.filter((field): field is BookField & {readonly name: Name} => wanted.includes(field.name));
}

export const BOOK_NON_FIELD_LOCKS = {
  cover: lockFor('cover'),
  audiobookCover: lockFor('audiobookCover'),
  reviews: lockFor('reviews'),
} as const satisfies Partial<Record<BookLockableName, MetadataLock>>;

export const BOOK_LOCKS: readonly MetadataLock<BookLockName>[] = [...BOOK_FIELDS, ...Object.values(BOOK_NON_FIELD_LOCKS)];

export const COMIC_FIELDS: readonly ComicField[] = COMIC_FIELD_TABLE.map(entry => ({...entry, ...lockFor(entry.name)}));
