import type {MetadataProviderId} from './metadata-providers';

export type MetadataProviderFieldRole = 'id' | 'rating' | 'reviewCount';

const PROVIDER_FIELD_TABLE = [
  {name: 'openlibraryId', valueType: 'string', labelKey: 'book.fields.openlibraryId', provider: 'OpenLibrary', role: 'id'},
  {name: 'asin', valueType: 'string', labelKey: 'book.fields.asin', provider: 'Amazon', role: 'id'},
  {name: 'amazonRating', valueType: 'number', labelKey: 'book.fields.amazonRating', provider: 'Amazon', role: 'rating'},
  {name: 'amazonReviewCount', valueType: 'number', labelKey: 'book.fields.amazonReviewCount', provider: 'Amazon', role: 'reviewCount'},
  {name: 'goodreadsId', valueType: 'string', labelKey: 'book.fields.goodreadsId', provider: 'GoodReads', role: 'id'},
  {name: 'goodreadsRating', valueType: 'number', labelKey: 'book.fields.goodreadsRating', provider: 'GoodReads', role: 'rating'},
  {name: 'goodreadsReviewCount', valueType: 'number', labelKey: 'book.fields.goodreadsReviewCount', provider: 'GoodReads', role: 'reviewCount'},
  {name: 'googleId', valueType: 'string', labelKey: 'book.fields.googleId', provider: 'Google', role: 'id'},
  {name: 'hardcoverId', valueType: 'string', labelKey: 'book.fields.hardcoverId', provider: 'Hardcover', role: 'id'},
  {name: 'hardcoverBookId', valueType: 'string', labelKey: 'book.fields.hardcoverBookId', provider: 'Hardcover', role: 'id'},
  {name: 'hardcoverRating', valueType: 'number', labelKey: 'book.fields.hardcoverRating', provider: 'Hardcover', role: 'rating'},
  {name: 'hardcoverReviewCount', valueType: 'number', labelKey: 'book.fields.hardcoverReviewCount', provider: 'Hardcover', role: 'reviewCount'},
  {name: 'comicvineId', valueType: 'string', labelKey: 'book.fields.comicvineId', provider: 'Comicvine', role: 'id'},
  {name: 'doubanId', valueType: 'string', labelKey: 'book.fields.doubanId', provider: 'Douban', role: 'id'},
  {name: 'doubanRating', valueType: 'number', labelKey: 'book.fields.doubanRating', provider: 'Douban', role: 'rating'},
  {name: 'doubanReviewCount', valueType: 'number', labelKey: 'book.fields.doubanReviewCount', provider: 'Douban', role: 'reviewCount'},
  {name: 'lubimyczytacId', valueType: 'string', labelKey: 'book.fields.lubimyczytacId', provider: 'Lubimyczytac', role: 'id'},
  {name: 'lubimyczytacRating', valueType: 'number', labelKey: 'book.fields.lubimyczytacRating', provider: 'Lubimyczytac', role: 'rating'},
  {name: 'ranobedbId', valueType: 'string', labelKey: 'book.fields.ranobedbId', provider: 'Ranobedb', role: 'id'},
  {name: 'ranobedbRating', valueType: 'number', labelKey: 'book.fields.ranobedbRating', provider: 'Ranobedb', role: 'rating'},
  {name: 'audibleId', valueType: 'string', labelKey: 'book.fields.audibleId', provider: 'Audible', role: 'id'},
  {name: 'audibleRating', valueType: 'number', labelKey: 'book.fields.audibleRating', provider: 'Audible', role: 'rating'},
  {name: 'audibleReviewCount', valueType: 'number', labelKey: 'book.fields.audibleReviewCount', provider: 'Audible', role: 'reviewCount'},
  {name: 'applebooksId', valueType: 'string', labelKey: 'book.fields.applebooksId', provider: 'AppleBooks', role: 'id'},
  {name: 'applebooksRating', valueType: 'number', labelKey: 'book.fields.applebooksRating', provider: 'AppleBooks', role: 'rating'},
  {name: 'applebooksReviewCount', valueType: 'number', labelKey: 'book.fields.applebooksReviewCount', provider: 'AppleBooks', role: 'reviewCount'},
] as const satisfies readonly {
  readonly name: string;
  readonly valueType: 'string' | 'number';
  readonly labelKey: string;
  readonly provider: MetadataProviderId;
  readonly role: MetadataProviderFieldRole;
}[];

type ProviderFieldRow = (typeof PROVIDER_FIELD_TABLE)[number];
type NameWithRole<Role extends MetadataProviderFieldRole> = Extract<ProviderFieldRow, {readonly role: Role}>['name'];

export type MetadataProviderFieldName = ProviderFieldRow['name'];
export type MetadataProviderIdFieldName = NameWithRole<'id'>;
export type MetadataProviderScoreFieldName = NameWithRole<'rating' | 'reviewCount'>;

export type MetadataProviderFieldRecord<T> = Record<MetadataProviderFieldName, T>;

export type MetadataProviderField = ProviderFieldRow & {readonly lockName: `${MetadataProviderFieldName}Locked`};
export type MetadataProviderScoreField = Extract<MetadataProviderField, {readonly role: 'rating' | 'reviewCount'}>;

export const METADATA_PROVIDER_FIELDS: readonly MetadataProviderField[] = PROVIDER_FIELD_TABLE.map(row => ({
  ...row,
  lockName: `${row.name}Locked`,
}));

export const METADATA_PROVIDER_SCORE_FIELDS: readonly MetadataProviderScoreField[] = METADATA_PROVIDER_FIELDS.flatMap(
  field => (field.role === 'id' ? [] : [field]),
);

export function providerFieldRecord<Field extends MetadataProviderField, T>(
  fields: readonly Field[],
  value: (field: Field) => T,
): Record<Field['name'], T> {
  return Object.fromEntries(fields.map(field => [field.name, value(field)])) as Record<Field['name'], T>;
}
