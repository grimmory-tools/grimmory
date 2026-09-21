const providers = [
  {
    id: 'OpenLibrary',
    settingsKey: 'openLibrary',
    labelKey: 'metadata.providers.openLibrary',
    book: {ids: ['openlibraryId']},
  },
  {
    id: 'Amazon',
    settingsKey: 'amazon',
    labelKey: 'metadata.providers.amazon',
    hasOptions: true,
    supportsReviews: true,
    book: {ids: ['asin'], rating: 'amazonRating', reviewCount: 'amazonReviewCount', labelKeys: {asin: 'metadata.providerFields.asin'}},
  },
  {
    id: 'GoodReads',
    settingsKey: 'goodReads',
    labelKey: 'metadata.providers.goodReads',
    supportsReviews: true,
    book: {ids: ['goodreadsId'], rating: 'goodreadsRating', reviewCount: 'goodreadsReviewCount'},
  },
  {
    id: 'Google',
    settingsKey: 'google',
    labelKey: 'metadata.providers.google',
    hasOptions: true,
    book: {ids: ['googleId']},
  },
  {
    id: 'Hardcover',
    settingsKey: 'hardcover',
    labelKey: 'metadata.providers.hardcover',
    hasOptions: true,
    book: {ids: ['hardcoverId', 'hardcoverBookId'], rating: 'hardcoverRating', reviewCount: 'hardcoverReviewCount', labelKeys: {hardcoverBookId: 'metadata.providerFields.hardcoverBookId'}},
  },
  {
    id: 'Comicvine',
    settingsKey: 'comicvine',
    labelKey: 'metadata.providers.comicvine',
    hasOptions: true,
    book: {ids: ['comicvineId']},
  },
  {
    id: 'Douban',
    settingsKey: 'douban',
    labelKey: 'metadata.providers.douban',
    supportsReviews: true,
  },
  {
    id: 'Lubimyczytac',
    settingsKey: 'lubimyczytac',
    labelKey: 'metadata.providers.lubimyczytac',
    book: {ids: ['lubimyczytacId'], rating: 'lubimyczytacRating'},
  },
  {
    id: 'Ranobedb',
    settingsKey: 'ranobedb',
    labelKey: 'metadata.providers.ranobedb',
    hasOptions: true,
    book: {ids: ['ranobedbId'], rating: 'ranobedbRating'},
  },
  {
    id: 'Audible',
    settingsKey: 'audible',
    labelKey: 'metadata.providers.audible',
    hasOptions: true,
    book: {ids: ['audibleId'], rating: 'audibleRating', reviewCount: 'audibleReviewCount'},
  },
  {
    id: 'AppleBooks',
    settingsKey: 'appleBooks',
    labelKey: 'metadata.providers.appleBooks',
    hasOptions: true,
    book: {ids: ['applebooksId'], rating: 'applebooksRating', reviewCount: 'applebooksReviewCount'},
  },
] as const;

export type MetadataProviderId = typeof providers[number]['id'];

type BookAspect = Extract<typeof providers[number], {book: unknown}>['book'];
type FieldForRole<Aspect, Role extends string> = Aspect extends Record<Role, infer Name extends string> ? Name : never;

export type MetadataProviderIdFieldName = BookAspect['ids'][number];
export type MetadataProviderRatingFieldName = FieldForRole<BookAspect, 'rating'>;
export type MetadataProviderReviewCountFieldName = FieldForRole<BookAspect, 'reviewCount'>;

export type MetadataProviderScoreFieldName = MetadataProviderRatingFieldName | MetadataProviderReviewCountFieldName;
export type MetadataProviderFieldName = MetadataProviderIdFieldName | MetadataProviderScoreFieldName;

export interface MetadataProviderBookFields {
  readonly ids: readonly MetadataProviderIdFieldName[];
  readonly rating?: MetadataProviderRatingFieldName;
  readonly reviewCount?: MetadataProviderReviewCountFieldName;
  readonly labelKeys?: Partial<Record<MetadataProviderFieldName, string>>;
}

export interface MetadataProviderDescriptor {
  readonly id: MetadataProviderId;
  readonly settingsKey: Uncapitalize<MetadataProviderId>;
  readonly labelKey: string;
  readonly hasOptions?: boolean;
  readonly supportsReviews?: boolean;
  readonly book?: MetadataProviderBookFields;
}

export const METADATA_PROVIDER_LIST: readonly MetadataProviderDescriptor[] = providers;
