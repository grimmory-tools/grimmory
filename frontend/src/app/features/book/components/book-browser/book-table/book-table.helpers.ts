import {BookMetadata} from '../../../model/book.model';

const RATING_FIELD_KEYS = [
  'rating',
  'amazonRating',
  'goodreadsRating',
  'hardcoverRating',
  'ranobedbRating',
  'lubimyczytacRating',
  'audibleRating',
] satisfies readonly (keyof BookMetadata)[];

export const RATING_FIELDS = new Set<keyof BookMetadata>(RATING_FIELD_KEYS);

export const LOCK_FIELDS = [
  'titleLocked',
  'subtitleLocked',
  'publisherLocked',
  'publishedDateLocked',
  'descriptionLocked',
  'seriesNameLocked',
  'seriesNumberLocked',
  'seriesTotalLocked',
  'isbn13Locked',
  'isbn10Locked',
  'asinLocked',
  'comicvineIdLocked',
  'goodreadsIdLocked',
  'hardcoverIdLocked',
  'hardcoverBookIdLocked',
  'googleIdLocked',
  'pageCountLocked',
  'languageLocked',
  'amazonRatingLocked',
  'amazonReviewCountLocked',
  'goodreadsRatingLocked',
  'goodreadsReviewCountLocked',
  'hardcoverRatingLocked',
  'hardcoverReviewCountLocked',
  'lubimyczytacIdLocked',
  'lubimyczytacRatingLocked',
  'ranobedbIdLocked',
  'ranobedbRatingLocked',
  'audibleIdLocked',
  'audibleRatingLocked',
  'audibleReviewCountLocked',
  'coverUpdatedOnLocked',
  'authorsLocked',
  'categoriesLocked',
  'moodsLocked',
  'tagsLocked',
  'coverLocked',
  'audiobookCoverLocked',
  'reviewsLocked',
  'narratorLocked',
  'abridgedLocked',
  'ageRatingLocked',
  'contentRatingLocked',
] satisfies readonly (keyof BookMetadata)[];

export function isMetadataFullyLocked(metadata: BookMetadata): boolean {
  if (typeof metadata.allMetadataLocked === 'boolean') {
    return metadata.allMetadataLocked;
  }
  return LOCK_FIELDS.every(field => metadata[field] === true);
}
