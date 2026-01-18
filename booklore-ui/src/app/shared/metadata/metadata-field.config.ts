import {MetadataProviderSpecificFields} from '../model/app-settings.model';

export type FieldType = 'string' | 'number' | 'array' | 'textarea';

export interface MetadataFieldConfig {
  label: string;
  controlName: string;
  lockedKey: string;
  fetchedKey: string;
  type: FieldType;
  providerKey?: keyof MetadataProviderSpecificFields;
}

export const ALL_METADATA_FIELDS: MetadataFieldConfig[] = [
  {label: 'Title', controlName: 'title', lockedKey: 'titleLocked', fetchedKey: 'title', type: 'string'},
  {label: 'Subtitle', controlName: 'subtitle', lockedKey: 'subtitleLocked', fetchedKey: 'subtitle', type: 'string'},
  {label: 'Publisher', controlName: 'publisher', lockedKey: 'publisherLocked', fetchedKey: 'publisher', type: 'string'},
  {label: 'Published', controlName: 'publishedDate', lockedKey: 'publishedDateLocked', fetchedKey: 'publishedDate', type: 'string'},
  {label: 'Authors', controlName: 'authors', lockedKey: 'authorsLocked', fetchedKey: 'authors', type: 'array'},
  {label: 'Genres', controlName: 'categories', lockedKey: 'categoriesLocked', fetchedKey: 'categories', type: 'array'},
  {label: 'Moods', controlName: 'moods', lockedKey: 'moodsLocked', fetchedKey: 'moods', type: 'array'},
  {label: 'Tags', controlName: 'tags', lockedKey: 'tagsLocked', fetchedKey: 'tags', type: 'array'},
  {label: 'Description', controlName: 'description', lockedKey: 'descriptionLocked', fetchedKey: 'description', type: 'textarea'},
  {label: 'Series', controlName: 'seriesName', lockedKey: 'seriesNameLocked', fetchedKey: 'seriesName', type: 'string'},
  {label: 'Book #', controlName: 'seriesNumber', lockedKey: 'seriesNumberLocked', fetchedKey: 'seriesNumber', type: 'number'},
  {label: 'Total Books', controlName: 'seriesTotal', lockedKey: 'seriesTotalLocked', fetchedKey: 'seriesTotal', type: 'number'},
  {label: 'Language', controlName: 'language', lockedKey: 'languageLocked', fetchedKey: 'language', type: 'string'},
  {label: 'ISBN-10', controlName: 'isbn10', lockedKey: 'isbn10Locked', fetchedKey: 'isbn10', type: 'string'},
  {label: 'ISBN-13', controlName: 'isbn13', lockedKey: 'isbn13Locked', fetchedKey: 'isbn13', type: 'string'},
  {label: 'Pages', controlName: 'pageCount', lockedKey: 'pageCountLocked', fetchedKey: 'pageCount', type: 'number'},
  {label: 'Google ID', controlName: 'googleId', lockedKey: 'googleIdLocked', fetchedKey: 'googleId', type: 'string', providerKey: 'googleId'},
  {label: 'ASIN', controlName: 'asin', lockedKey: 'asinLocked', fetchedKey: 'asin', type: 'string', providerKey: 'asin'},
  {label: 'Amazon #', controlName: 'amazonReviewCount', lockedKey: 'amazonReviewCountLocked', fetchedKey: 'amazonReviewCount', type: 'number', providerKey: 'amazonReviewCount'},
  {label: 'Amazon ★', controlName: 'amazonRating', lockedKey: 'amazonRatingLocked', fetchedKey: 'amazonRating', type: 'number', providerKey: 'amazonRating'},
  {label: 'Goodreads ID', controlName: 'goodreadsId', lockedKey: 'goodreadsIdLocked', fetchedKey: 'goodreadsId', type: 'string', providerKey: 'goodreadsId'},
  {label: 'Goodreads ★', controlName: 'goodreadsReviewCount', lockedKey: 'goodreadsReviewCountLocked', fetchedKey: 'goodreadsReviewCount', type: 'number', providerKey: 'goodreadsReviewCount'},
  {label: 'Goodreads #', controlName: 'goodreadsRating', lockedKey: 'goodreadsRatingLocked', fetchedKey: 'goodreadsRating', type: 'number', providerKey: 'goodreadsRating'},
  {label: 'HC Book ID', controlName: 'hardcoverBookId', lockedKey: 'hardcoverBookIdLocked', fetchedKey: 'hardcoverBookId', type: 'number', providerKey: 'hardcoverId'},
  {label: 'Hardcover ID', controlName: 'hardcoverId', lockedKey: 'hardcoverIdLocked', fetchedKey: 'hardcoverId', type: 'string', providerKey: 'hardcoverId'},
  {label: 'Hardcover #', controlName: 'hardcoverReviewCount', lockedKey: 'hardcoverReviewCountLocked', fetchedKey: 'hardcoverReviewCount', type: 'number', providerKey: 'hardcoverReviewCount'},
  {label: 'Hardcover ★', controlName: 'hardcoverRating', lockedKey: 'hardcoverRatingLocked', fetchedKey: 'hardcoverRating', type: 'number', providerKey: 'hardcoverRating'},
  {label: 'Comicvine ID', controlName: 'comicvineId', lockedKey: 'comicvineIdLocked', fetchedKey: 'comicvineId', type: 'string', providerKey: 'comicvineId'},
  {label: 'LB ID', controlName: 'lubimyczytacId', lockedKey: 'lubimyczytacIdLocked', fetchedKey: 'lubimyczytacId', type: 'string', providerKey: 'lubimyczytacId'},
  {label: 'LB ★', controlName: 'lubimyczytacRating', lockedKey: 'lubimyczytacRatingLocked', fetchedKey: 'lubimyczytacRating', type: 'number', providerKey: 'lubimyczytacRating'},
  {label: 'Ranobedb ID', controlName: 'ranobedbId', lockedKey: 'ranobedbIdLocked', fetchedKey: 'ranobedbId', type: 'string', providerKey: 'ranobedbId'},
  {label: 'Ranobedb ★', controlName: 'ranobedbRating', lockedKey: 'ranobedbRatingLocked', fetchedKey: 'ranobedbRating', type: 'number', providerKey: 'ranobedbRating'}
];

export const TOP_FIELD_NAMES = ['title', 'subtitle', 'publisher', 'publishedDate'];
export const ARRAY_FIELD_NAMES = ['authors', 'categories', 'moods', 'tags'];
export const TEXTAREA_FIELD_NAMES = ['description'];

export function getTopFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => TOP_FIELD_NAMES.includes(f.controlName));
}

export function getArrayFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => f.type === 'array');
}

export function getTextareaFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => f.type === 'textarea');
}

export function getBottomFields(enabledProviderFields?: MetadataProviderSpecificFields | null): MetadataFieldConfig[] {
  const bottomFields = ALL_METADATA_FIELDS.filter(f =>
    !TOP_FIELD_NAMES.includes(f.controlName) &&
    !ARRAY_FIELD_NAMES.includes(f.controlName) &&
    !TEXTAREA_FIELD_NAMES.includes(f.controlName)
  );

  if (enabledProviderFields) {
    return bottomFields.filter(field =>
      !field.providerKey || enabledProviderFields[field.providerKey]
    );
  }

  return bottomFields;
}
