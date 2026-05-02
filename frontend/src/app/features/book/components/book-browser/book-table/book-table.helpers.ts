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

export function isMetadataFullyLocked(metadata: BookMetadata): boolean {
  if (typeof metadata.allMetadataLocked === 'boolean') {
    return metadata.allMetadataLocked;
  }
  const lockedKeys = Object.keys(metadata).filter(key => key.endsWith('Locked') && key !== 'allMetadataLocked');
  if (lockedKeys.length === 0) return false;
  const metadataRecord = metadata as Record<string, unknown>;
  return lockedKeys.every(key => metadataRecord[key] === true);
}
