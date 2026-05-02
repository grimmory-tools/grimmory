import {BookMetadata} from '../../../model/book.model';

export const RATING_FIELDS = new Set([
  'rating',
  'amazonRating',
  'goodreadsRating',
  'hardcoverRating',
  'ranobedbRating',
  'lubimyczytacRating',
  'audibleRating',
]);

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
] satisfies ReadonlyArray<keyof BookMetadata>;

export function isMetadataFullyLocked(metadata: BookMetadata): boolean {
  if (typeof metadata.allMetadataLocked === 'boolean') {
    return metadata.allMetadataLocked;
  }
  return LOCK_FIELDS.every(field => metadata[field] === true);
}
