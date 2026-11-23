import type {BookDetailComicMetadata, BookDetailMetadata} from '../../features/book/data/book-response.models';
import {
  BOOK_AGE_RATING_LABEL_KEYS,
  BOOK_AGE_RATINGS,
  BOOK_CONTENT_RATING_LABEL_KEYS,
  BOOK_CONTENT_RATINGS,
} from '../../features/book/data/book-values';
import type {BookQueryFacetKey} from '../../features/book/data/book-query-params';
import type {MetadataProviderId} from './metadata-providers';

type MetadataValueKind = 'string' | 'text' | 'date' | 'number' | 'boolean' | 'list' | 'set';
type MetadataLockOnlyKind = 'image' | 'reviews';
export type MetadataProviderFieldRole = 'id' | 'rating' | 'reviewCount';
type ComicCreatorRole = 'penciller' | 'inker' | 'colorist' | 'letterer' | 'coverArtist' | 'editor';

interface MetadataValueTypes {
  string: string;
  text: string;
  date: string;
  number: number;
  boolean: boolean;
  list: string[];
  set: string[];
}

interface Models {
  book: Omit<BookDetailMetadata, 'comicMetadata'>;
  comic: BookDetailComicMetadata;
}
type ModelName = keyof Models;

type LockName<Model extends ModelName> = Extract<keyof Models[Model], `${string}Locked`>;
type IsEqual<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
type KeysOfType<T, Value> = {
  [Key in keyof T]-?: IsEqual<Exclude<T[Key], undefined>, Value> extends true ? Key : never
}[keyof T];

interface FieldBase {
  readonly labelKey: string;
  readonly facet?: BookQueryFacetKey;
  readonly provider?: MetadataProviderId;
  readonly providerRole?: MetadataProviderFieldRole;
  readonly creatorRole?: ComicCreatorRole;
}

type ValueField<Model extends ModelName, Kind extends MetadataValueKind> = FieldBase & {
  readonly kind: Kind;
  readonly name: KeysOfType<Models[Model], MetadataValueTypes[Kind]>;
  readonly lockName: LockName<Model>;
};
type EnumValueKind = 'string' | 'number';
type EnumField<Model extends ModelName> = {
  [Kind in EnumValueKind]: FieldBase & {
    readonly kind: 'enum';
    readonly name: KeysOfType<Models[Model], MetadataValueTypes[Kind]>;
    readonly values: readonly MetadataValueTypes[Kind][];
    readonly labelKeys: Readonly<Record<MetadataValueTypes[Kind], string>>;
    readonly lockName: LockName<Model>;
  }
}[EnumValueKind];
type LockOnlyField<Model extends ModelName> = FieldBase & {
  readonly kind: MetadataLockOnlyKind;
  readonly name: string;
  readonly lockName: LockName<Model>;
};
type FieldDefinition<Model extends ModelName> =
  | {[Kind in MetadataValueKind]: ValueField<Model, Kind>}[MetadataValueKind]
  | EnumField<Model>
  | LockOnlyField<Model>;

function fieldGroup<const Key extends string, const Model extends ModelName, const Field extends FieldDefinition<Model>>(group: {
  readonly key: Key;
  readonly labelKey: string;
  readonly model: Model;
  readonly fields: readonly Field[];
}) {
  return {
    key: group.key,
    labelKey: group.labelKey,
    fields: group.fields.map((field): Field & FieldBase & {readonly model: Model} => ({...field, model: group.model})),
  };
}

export const METADATA_FIELD_GROUPS = [
  fieldGroup({key: 'core', labelKey: 'book.fieldGroups.core', model: 'book', fields: [
    {name: 'title', lockName: 'titleLocked', kind: 'string', labelKey: 'book.fields.title'},
    {name: 'subtitle', lockName: 'subtitleLocked', kind: 'string', labelKey: 'book.fields.subtitle'},
    {name: 'authors', lockName: 'authorsLocked', kind: 'list', labelKey: 'book.fields.author', facet: 'author'},
    {name: 'description', lockName: 'descriptionLocked', kind: 'text', labelKey: 'book.fields.description'},
    {name: 'cover', lockName: 'coverLocked', kind: 'image', labelKey: 'book.fields.cover'},
    {name: 'audiobookCover', lockName: 'audiobookCoverLocked', kind: 'image', labelKey: 'book.fields.audiobookCover'},
  ]}),
  fieldGroup({key: 'series', labelKey: 'book.fieldGroups.series', model: 'book', fields: [
    {name: 'seriesName', lockName: 'seriesNameLocked', kind: 'string', labelKey: 'book.fields.series', facet: 'series'},
    {name: 'seriesNumber', lockName: 'seriesNumberLocked', kind: 'number', labelKey: 'book.fields.seriesNumber'},
    {name: 'seriesTotal', lockName: 'seriesTotalLocked', kind: 'number', labelKey: 'book.fields.seriesTotal'},
  ]}),
  fieldGroup({key: 'publishing', labelKey: 'book.fieldGroups.publishing', model: 'book', fields: [
    {name: 'publisher', lockName: 'publisherLocked', kind: 'string', labelKey: 'book.fields.publisher', facet: 'publisher'},
    {name: 'publishedDate', lockName: 'publishedDateLocked', kind: 'date', labelKey: 'book.fields.publishedDate', facet: 'published_year'},
    {name: 'language', lockName: 'languageLocked', kind: 'string', labelKey: 'book.fields.language', facet: 'language'},
    {name: 'isbn10', lockName: 'isbn10Locked', kind: 'string', labelKey: 'book.fields.isbn10'},
    {name: 'isbn13', lockName: 'isbn13Locked', kind: 'string', labelKey: 'book.fields.isbn13'},
    {name: 'pageCount', lockName: 'pageCountLocked', kind: 'number', labelKey: 'book.fields.pageCount', facet: 'page_count'},
  ]}),
  fieldGroup({key: 'categorization', labelKey: 'book.fieldGroups.categorization', model: 'book', fields: [
    {name: 'categories', lockName: 'categoriesLocked', kind: 'set', labelKey: 'book.fields.genre', facet: 'genre'},
    {name: 'moods', lockName: 'moodsLocked', kind: 'set', labelKey: 'book.fields.mood', facet: 'mood'},
    {name: 'tags', lockName: 'tagsLocked', kind: 'set', labelKey: 'book.fields.tag', facet: 'tag'},
    {name: 'ageRating', lockName: 'ageRatingLocked', kind: 'enum', values: BOOK_AGE_RATINGS, labelKeys: BOOK_AGE_RATING_LABEL_KEYS, labelKey: 'book.fields.ageRating', facet: 'age_rating'},
    {name: 'contentRating', lockName: 'contentRatingLocked', kind: 'enum', values: BOOK_CONTENT_RATINGS, labelKeys: BOOK_CONTENT_RATING_LABEL_KEYS, labelKey: 'book.fields.contentRating', facet: 'content_rating'},
  ]}),
  fieldGroup({key: 'audiobook', labelKey: 'book.fieldGroups.audiobook', model: 'book', fields: [
    {name: 'narrator', lockName: 'narratorLocked', kind: 'string', labelKey: 'book.fields.narrator', facet: 'narrator'},
    {name: 'abridged', lockName: 'abridgedLocked', kind: 'boolean', labelKey: 'book.fields.abridged'},
  ]}),
  fieldGroup({key: 'comicDetails', labelKey: 'book.fieldGroups.comicDetails', model: 'comic', fields: [
    {name: 'issueNumber', lockName: 'issueNumberLocked', kind: 'string', labelKey: 'book.fields.comicIssueNumber'},
    {name: 'volumeName', lockName: 'volumeNameLocked', kind: 'string', labelKey: 'book.fields.comicVolume'},
    {name: 'volumeNumber', lockName: 'volumeNumberLocked', kind: 'number', labelKey: 'book.fields.comicVolumeNumber'},
    {name: 'storyArc', lockName: 'storyArcLocked', kind: 'string', labelKey: 'book.fields.comicStoryArc'},
    {name: 'storyArcNumber', lockName: 'storyArcNumberLocked', kind: 'number', labelKey: 'book.fields.comicStoryArcNumber'},
    {name: 'alternateSeries', lockName: 'alternateSeriesLocked', kind: 'string', labelKey: 'book.fields.comicAlternateSeries'},
    {name: 'alternateIssue', lockName: 'alternateIssueLocked', kind: 'string', labelKey: 'book.fields.comicAlternateIssue'},
    {name: 'imprint', lockName: 'imprintLocked', kind: 'string', labelKey: 'book.fields.comicImprint'},
    {name: 'format', lockName: 'formatLocked', kind: 'string', labelKey: 'book.fields.comicFormat'},
    {name: 'readingDirection', lockName: 'readingDirectionLocked', kind: 'string', labelKey: 'book.fields.comicReadingDirection'},
    {name: 'blackAndWhite', lockName: 'blackAndWhiteLocked', kind: 'boolean', labelKey: 'book.fields.comicBlackAndWhite'},
    {name: 'manga', lockName: 'mangaLocked', kind: 'boolean', labelKey: 'book.fields.comicManga'},
    {name: 'webLink', lockName: 'webLinkLocked', kind: 'string', labelKey: 'book.fields.comicWebLink'},
    {name: 'notes', lockName: 'notesLocked', kind: 'text', labelKey: 'book.fields.comicNotes'},
  ]}),
  fieldGroup({key: 'comicCreators', labelKey: 'book.fieldGroups.comicCreators', model: 'comic', fields: [
    {name: 'pencillers', lockName: 'pencillersLocked', kind: 'set', labelKey: 'book.fields.comicPenciller', facet: 'comic_creator', creatorRole: 'penciller'},
    {name: 'inkers', lockName: 'inkersLocked', kind: 'set', labelKey: 'book.fields.comicInker', facet: 'comic_creator', creatorRole: 'inker'},
    {name: 'colorists', lockName: 'coloristsLocked', kind: 'set', labelKey: 'book.fields.comicColorist', facet: 'comic_creator', creatorRole: 'colorist'},
    {name: 'letterers', lockName: 'letterersLocked', kind: 'set', labelKey: 'book.fields.comicLetterer', facet: 'comic_creator', creatorRole: 'letterer'},
    {name: 'coverArtists', lockName: 'coverArtistsLocked', kind: 'set', labelKey: 'book.fields.comicCoverArtist', facet: 'comic_creator', creatorRole: 'coverArtist'},
    {name: 'editors', lockName: 'editorsLocked', kind: 'set', labelKey: 'book.fields.comicEditor', facet: 'comic_creator', creatorRole: 'editor'},
  ]}),
  fieldGroup({key: 'comicUniverse', labelKey: 'book.fieldGroups.comicUniverse', model: 'comic', fields: [
    {name: 'characters', lockName: 'charactersLocked', kind: 'set', labelKey: 'book.fields.comicCharacter', facet: 'comic_character'},
    {name: 'teams', lockName: 'teamsLocked', kind: 'set', labelKey: 'book.fields.comicTeam', facet: 'comic_team'},
    {name: 'locations', lockName: 'locationsLocked', kind: 'set', labelKey: 'book.fields.comicLocation', facet: 'comic_location'},
  ]}),
  fieldGroup({key: 'providers', labelKey: 'book.fieldGroups.providers', model: 'book', fields: [
    {name: 'openlibraryId', lockName: 'openlibraryIdLocked', kind: 'string', labelKey: 'book.fields.openlibraryId', provider: 'OpenLibrary', providerRole: 'id'},
    {name: 'asin', lockName: 'asinLocked', kind: 'string', labelKey: 'book.fields.asin', provider: 'Amazon', providerRole: 'id'},
    {name: 'amazonRating', lockName: 'amazonRatingLocked', kind: 'number', labelKey: 'book.fields.amazonRating', facet: 'amazon_rating', provider: 'Amazon', providerRole: 'rating'},
    {name: 'amazonReviewCount', lockName: 'amazonReviewCountLocked', kind: 'number', labelKey: 'book.fields.amazonReviewCount', provider: 'Amazon', providerRole: 'reviewCount'},
    {name: 'goodreadsId', lockName: 'goodreadsIdLocked', kind: 'string', labelKey: 'book.fields.goodreadsId', provider: 'GoodReads', providerRole: 'id'},
    {name: 'goodreadsRating', lockName: 'goodreadsRatingLocked', kind: 'number', labelKey: 'book.fields.goodreadsRating', facet: 'goodreads_rating', provider: 'GoodReads', providerRole: 'rating'},
    {name: 'goodreadsReviewCount', lockName: 'goodreadsReviewCountLocked', kind: 'number', labelKey: 'book.fields.goodreadsReviewCount', provider: 'GoodReads', providerRole: 'reviewCount'},
    {name: 'googleId', lockName: 'googleIdLocked', kind: 'string', labelKey: 'book.fields.googleId', provider: 'Google', providerRole: 'id'},
    {name: 'hardcoverId', lockName: 'hardcoverIdLocked', kind: 'string', labelKey: 'book.fields.hardcoverId', provider: 'Hardcover', providerRole: 'id'},
    {name: 'hardcoverBookId', lockName: 'hardcoverBookIdLocked', kind: 'string', labelKey: 'book.fields.hardcoverBookId', provider: 'Hardcover', providerRole: 'id'},
    {name: 'hardcoverRating', lockName: 'hardcoverRatingLocked', kind: 'number', labelKey: 'book.fields.hardcoverRating', facet: 'hardcover_rating', provider: 'Hardcover', providerRole: 'rating'},
    {name: 'hardcoverReviewCount', lockName: 'hardcoverReviewCountLocked', kind: 'number', labelKey: 'book.fields.hardcoverReviewCount', provider: 'Hardcover', providerRole: 'reviewCount'},
    {name: 'comicvineId', lockName: 'comicvineIdLocked', kind: 'string', labelKey: 'book.fields.comicvineId', provider: 'Comicvine', providerRole: 'id'},
    {name: 'doubanId', lockName: 'doubanIdLocked', kind: 'string', labelKey: 'book.fields.doubanId', provider: 'Douban', providerRole: 'id'},
    {name: 'doubanRating', lockName: 'doubanRatingLocked', kind: 'number', labelKey: 'book.fields.doubanRating', provider: 'Douban', providerRole: 'rating'},
    {name: 'doubanReviewCount', lockName: 'doubanReviewCountLocked', kind: 'number', labelKey: 'book.fields.doubanReviewCount', provider: 'Douban', providerRole: 'reviewCount'},
    {name: 'lubimyczytacId', lockName: 'lubimyczytacIdLocked', kind: 'string', labelKey: 'book.fields.lubimyczytacId', provider: 'Lubimyczytac', providerRole: 'id'},
    {name: 'lubimyczytacRating', lockName: 'lubimyczytacRatingLocked', kind: 'number', labelKey: 'book.fields.lubimyczytacRating', facet: 'lubimyczytac_rating', provider: 'Lubimyczytac', providerRole: 'rating'},
    {name: 'ranobedbId', lockName: 'ranobedbIdLocked', kind: 'string', labelKey: 'book.fields.ranobedbId', provider: 'Ranobedb', providerRole: 'id'},
    {name: 'ranobedbRating', lockName: 'ranobedbRatingLocked', kind: 'number', labelKey: 'book.fields.ranobedbRating', facet: 'ranobedb_rating', provider: 'Ranobedb', providerRole: 'rating'},
    {name: 'audibleId', lockName: 'audibleIdLocked', kind: 'string', labelKey: 'book.fields.audibleId', provider: 'Audible', providerRole: 'id'},
    {name: 'audibleRating', lockName: 'audibleRatingLocked', kind: 'number', labelKey: 'book.fields.audibleRating', facet: 'audible_rating', provider: 'Audible', providerRole: 'rating'},
    {name: 'audibleReviewCount', lockName: 'audibleReviewCountLocked', kind: 'number', labelKey: 'book.fields.audibleReviewCount', provider: 'Audible', providerRole: 'reviewCount'},
    {name: 'applebooksId', lockName: 'applebooksIdLocked', kind: 'string', labelKey: 'book.fields.applebooksId', provider: 'AppleBooks', providerRole: 'id'},
    {name: 'applebooksRating', lockName: 'applebooksRatingLocked', kind: 'number', labelKey: 'book.fields.applebooksRating', facet: 'applebooks_rating', provider: 'AppleBooks', providerRole: 'rating'},
    {name: 'applebooksReviewCount', lockName: 'applebooksReviewCountLocked', kind: 'number', labelKey: 'book.fields.applebooksReviewCount', provider: 'AppleBooks', providerRole: 'reviewCount'},
  ]}),
  fieldGroup({key: 'reviews', labelKey: 'book.fieldGroups.reviews', model: 'book', fields: [
    {name: 'reviews', lockName: 'reviewsLocked', kind: 'reviews', labelKey: 'book.fields.reviews'},
  ]}),
] as const;

export type MetadataFieldGroup = (typeof METADATA_FIELD_GROUPS)[number];
export type MetadataField = MetadataFieldGroup['fields'][number];

export type MetadataProviderField = Extract<MetadataField, {readonly provider: MetadataProviderId}>;
export type MetadataProviderScoreField = Extract<MetadataProviderField, {readonly providerRole: 'rating' | 'reviewCount'}>;
export type MetadataProviderFieldName = MetadataProviderField['name'];
export type MetadataProviderIdFieldName = Extract<MetadataProviderField, {readonly providerRole: 'id'}>['name'];
export type MetadataProviderScoreFieldName = MetadataProviderScoreField['name'];
export type MetadataProviderFieldRecord<T> = Record<MetadataProviderFieldName, T>;

export const METADATA_FIELDS: readonly MetadataField[] = METADATA_FIELD_GROUPS.flatMap((group): readonly MetadataField[] => group.fields);

export const METADATA_PROVIDER_FIELDS: readonly MetadataProviderField[] = METADATA_FIELDS.filter(
  (field): field is MetadataProviderField => 'provider' in field,
);

export const METADATA_PROVIDER_SCORE_FIELDS: readonly MetadataProviderScoreField[] = METADATA_PROVIDER_FIELDS.filter(
  (field): field is MetadataProviderScoreField => field.providerRole !== 'id',
);

export function providerFieldRecord<Field extends MetadataProviderField, T>(
  fields: readonly Field[],
  value: (field: Field) => T,
): Record<Field['name'], T> {
  // SAFETY: both provider lists are filtered from the same rows their types come from, so every name in Field is a key.
  return Object.fromEntries(fields.map(field => [field.name, value(field)])) as Record<Field['name'], T>;
}
