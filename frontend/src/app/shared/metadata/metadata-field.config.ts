import { MetadataProviderSpecificFields } from '../model/app-settings.model';

export type FieldType = 'string' | 'number' | 'array' | 'textarea' | 'boolean';

export interface MetadataFieldConfig {
  label: string;
  controlName: string;
  lockedKey: string;
  fetchedKey: string;
  type: FieldType;
  providerKey?: keyof MetadataProviderSpecificFields;
}

export const ALL_METADATA_FIELDS: MetadataFieldConfig[] = [
  { label: 'Title', controlName: 'title', lockedKey: 'titleLocked', fetchedKey: 'title', type: 'string' },
  { label: 'Subtitle', controlName: 'subtitle', lockedKey: 'subtitleLocked', fetchedKey: 'subtitle', type: 'string' },
  { label: 'Publisher', controlName: 'publisher', lockedKey: 'publisherLocked', fetchedKey: 'publisher', type: 'string' },
  { label: 'Published', controlName: 'publishedDate', lockedKey: 'publishedDateLocked', fetchedKey: 'publishedDate', type: 'string' },
  { label: 'Authors', controlName: 'authors', lockedKey: 'authorsLocked', fetchedKey: 'authors', type: 'array' },
  { label: 'Genres', controlName: 'categories', lockedKey: 'categoriesLocked', fetchedKey: 'categories', type: 'array' },
  { label: 'Moods', controlName: 'moods', lockedKey: 'moodsLocked', fetchedKey: 'moods', type: 'array' },
  { label: 'Tags', controlName: 'tags', lockedKey: 'tagsLocked', fetchedKey: 'tags', type: 'array' },
  { label: 'Description', controlName: 'description', lockedKey: 'descriptionLocked', fetchedKey: 'description', type: 'textarea' },
  { label: 'Series', controlName: 'seriesName', lockedKey: 'seriesNameLocked', fetchedKey: 'seriesName', type: 'string' },
  { label: 'Book #', controlName: 'seriesNumber', lockedKey: 'seriesNumberLocked', fetchedKey: 'seriesNumber', type: 'number' },
  { label: 'Total Books', controlName: 'seriesTotal', lockedKey: 'seriesTotalLocked', fetchedKey: 'seriesTotal', type: 'number' },
  { label: 'Language', controlName: 'language', lockedKey: 'languageLocked', fetchedKey: 'language', type: 'string' },
  { label: 'ISBN-10', controlName: 'isbn10', lockedKey: 'isbn10Locked', fetchedKey: 'isbn10', type: 'string' },
  { label: 'ISBN-13', controlName: 'isbn13', lockedKey: 'isbn13Locked', fetchedKey: 'isbn13', type: 'string' },
  { label: 'Pages', controlName: 'pageCount', lockedKey: 'pageCountLocked', fetchedKey: 'pageCount', type: 'number' },
  { label: 'Google ID', controlName: 'googleId', lockedKey: 'googleIdLocked', fetchedKey: 'googleId', type: 'string', providerKey: 'googleId' },
  { label: 'ASIN', controlName: 'asin', lockedKey: 'asinLocked', fetchedKey: 'asin', type: 'string', providerKey: 'asin' },
  { label: 'Amazon #', controlName: 'amazonReviewCount', lockedKey: 'amazonReviewCountLocked', fetchedKey: 'amazonReviewCount', type: 'number', providerKey: 'amazonReviewCount' },
  { label: 'Amazon ★', controlName: 'amazonRating', lockedKey: 'amazonRatingLocked', fetchedKey: 'amazonRating', type: 'number', providerKey: 'amazonRating' },
  { label: 'Goodreads ID', controlName: 'goodreadsId', lockedKey: 'goodreadsIdLocked', fetchedKey: 'goodreadsId', type: 'string', providerKey: 'goodreadsId' },
  { label: 'Goodreads #', controlName: 'goodreadsReviewCount', lockedKey: 'goodreadsReviewCountLocked', fetchedKey: 'goodreadsReviewCount', type: 'number', providerKey: 'goodreadsReviewCount' },
  { label: 'Goodreads ★', controlName: 'goodreadsRating', lockedKey: 'goodreadsRatingLocked', fetchedKey: 'goodreadsRating', type: 'number', providerKey: 'goodreadsRating' },
  { label: 'HC Book ID', controlName: 'hardcoverBookId', lockedKey: 'hardcoverBookIdLocked', fetchedKey: 'hardcoverBookId', type: 'number', providerKey: 'hardcoverBookId' },
  { label: 'Hardcover ID', controlName: 'hardcoverId', lockedKey: 'hardcoverIdLocked', fetchedKey: 'hardcoverId', type: 'string', providerKey: 'hardcoverId' },
  { label: 'Hardcover #', controlName: 'hardcoverReviewCount', lockedKey: 'hardcoverReviewCountLocked', fetchedKey: 'hardcoverReviewCount', type: 'number', providerKey: 'hardcoverReviewCount' },
  { label: 'Hardcover ★', controlName: 'hardcoverRating', lockedKey: 'hardcoverRatingLocked', fetchedKey: 'hardcoverRating', type: 'number', providerKey: 'hardcoverRating' },
  { label: 'Comicvine ID', controlName: 'comicvineId', lockedKey: 'comicvineIdLocked', fetchedKey: 'comicvineId', type: 'string', providerKey: 'comicvineId' },
  { label: 'LB ID', controlName: 'lubimyczytacId', lockedKey: 'lubimyczytacIdLocked', fetchedKey: 'lubimyczytacId', type: 'string', providerKey: 'lubimyczytacId' },
  { label: 'LB ★', controlName: 'lubimyczytacRating', lockedKey: 'lubimyczytacRatingLocked', fetchedKey: 'lubimyczytacRating', type: 'number', providerKey: 'lubimyczytacRating' },
  { label: 'Ranobedb ID', controlName: 'ranobedbId', lockedKey: 'ranobedbIdLocked', fetchedKey: 'ranobedbId', type: 'string', providerKey: 'ranobedbId' },
  { label: 'Ranobedb ★', controlName: 'ranobedbRating', lockedKey: 'ranobedbRatingLocked', fetchedKey: 'ranobedbRating', type: 'number', providerKey: 'ranobedbRating' },
  { label: 'Audible ID', controlName: 'audibleId', lockedKey: 'audibleIdLocked', fetchedKey: 'audibleId', type: 'string', providerKey: 'audibleId' },
  { label: 'Audible ★', controlName: 'audibleRating', lockedKey: 'audibleRatingLocked', fetchedKey: 'audibleRating', type: 'number', providerKey: 'audibleRating' },
  { label: 'Audible #', controlName: 'audibleReviewCount', lockedKey: 'audibleReviewCountLocked', fetchedKey: 'audibleReviewCount', type: 'number', providerKey: 'audibleReviewCount' }
];

// Audiobook content metadata fields (narrator/abridged) - now stored at top level of BookMetadata
export const AUDIOBOOK_METADATA_FIELDS: MetadataFieldConfig[] = [
  { label: 'Narrator', controlName: 'narrator', lockedKey: 'narratorLocked', fetchedKey: 'narrator', type: 'string' },
  { label: 'Abridged', controlName: 'abridged', lockedKey: 'abridgedLocked', fetchedKey: 'abridged', type: 'boolean' }
];

// Comic book metadata fields - stored nested under BookMetadata.comicMetadata
export const COMIC_TEXT_METADATA_FIELDS: MetadataFieldConfig[] = [
  { label: 'Issue #', controlName: 'comicIssueNumber', lockedKey: 'comicIssueNumberLocked', fetchedKey: 'issueNumber', type: 'string' },
  { label: 'Volume', controlName: 'comicVolumeName', lockedKey: 'comicVolumeNameLocked', fetchedKey: 'volumeName', type: 'string' },
  { label: 'Volume #', controlName: 'comicVolumeNumber', lockedKey: 'comicVolumeNumberLocked', fetchedKey: 'volumeNumber', type: 'number' },
  { label: 'Story Arc', controlName: 'comicStoryArc', lockedKey: 'comicStoryArcLocked', fetchedKey: 'storyArc', type: 'string' },
  { label: 'Arc #', controlName: 'comicStoryArcNumber', lockedKey: 'comicStoryArcNumberLocked', fetchedKey: 'storyArcNumber', type: 'number' },
  { label: 'Alt. Series', controlName: 'comicAlternateSeries', lockedKey: 'comicAlternateSeriesLocked', fetchedKey: 'alternateSeries', type: 'string' },
  { label: 'Alt. Issue', controlName: 'comicAlternateIssue', lockedKey: 'comicAlternateIssueLocked', fetchedKey: 'alternateIssue', type: 'string' },
  { label: 'Imprint', controlName: 'comicImprint', lockedKey: 'comicImprintLocked', fetchedKey: 'imprint', type: 'string' },
  { label: 'Format', controlName: 'comicFormat', lockedKey: 'comicFormatLocked', fetchedKey: 'format', type: 'string' },
  { label: 'Reading Dir.', controlName: 'comicReadingDirection', lockedKey: 'comicReadingDirectionLocked', fetchedKey: 'readingDirection', type: 'string' },
  { label: 'Web Link', controlName: 'comicWebLink', lockedKey: 'comicWebLinkLocked', fetchedKey: 'webLink', type: 'string' },
  { label: 'B&W', controlName: 'comicBlackAndWhite', lockedKey: 'comicBlackAndWhiteLocked', fetchedKey: 'blackAndWhite', type: 'boolean' },
  { label: 'Manga', controlName: 'comicManga', lockedKey: 'comicMangaLocked', fetchedKey: 'manga', type: 'boolean' },
];

export const COMIC_ARRAY_METADATA_FIELDS: MetadataFieldConfig[] = [
  { label: 'Pencillers', controlName: 'comicPencillers', lockedKey: 'comicPencillersLocked', fetchedKey: 'pencillers', type: 'array' },
  { label: 'Inkers', controlName: 'comicInkers', lockedKey: 'comicInkersLocked', fetchedKey: 'inkers', type: 'array' },
  { label: 'Colorists', controlName: 'comicColorists', lockedKey: 'comicColoristsLocked', fetchedKey: 'colorists', type: 'array' },
  { label: 'Letterers', controlName: 'comicLetterers', lockedKey: 'comicLetterersLocked', fetchedKey: 'letterers', type: 'array' },
  { label: 'Cover Artists', controlName: 'comicCoverArtists', lockedKey: 'comicCoverArtistsLocked', fetchedKey: 'coverArtists', type: 'array' },
  { label: 'Editors', controlName: 'comicEditors', lockedKey: 'comicEditorsLocked', fetchedKey: 'editors', type: 'array' },
  { label: 'Characters', controlName: 'comicCharacters', lockedKey: 'comicCharactersLocked', fetchedKey: 'characters', type: 'array' },
  { label: 'Teams', controlName: 'comicTeams', lockedKey: 'comicTeamsLocked', fetchedKey: 'teams', type: 'array' },
  { label: 'Locations', controlName: 'comicLocations', lockedKey: 'comicLocationsLocked', fetchedKey: 'locations', type: 'array' },
];

export const COMIC_TEXTAREA_METADATA_FIELDS: MetadataFieldConfig[] = [
  { label: 'Notes', controlName: 'comicNotes', lockedKey: 'comicNotesLocked', fetchedKey: 'notes', type: 'textarea' },
];

export const ALL_COMIC_METADATA_FIELDS: MetadataFieldConfig[] = [
  ...COMIC_TEXT_METADATA_FIELDS,
  ...COMIC_ARRAY_METADATA_FIELDS,
  ...COMIC_TEXTAREA_METADATA_FIELDS,
];

// Maps form lockedKey → ComicMetadata lock property name (1:1 per-field locks).
export const COMIC_FORM_TO_MODEL_LOCK: Record<string, string> = {
  'comicIssueNumberLocked': 'issueNumberLocked',
  'comicVolumeNameLocked': 'volumeNameLocked',
  'comicVolumeNumberLocked': 'volumeNumberLocked',
  'comicStoryArcLocked': 'storyArcLocked',
  'comicStoryArcNumberLocked': 'storyArcNumberLocked',
  'comicAlternateSeriesLocked': 'alternateSeriesLocked',
  'comicAlternateIssueLocked': 'alternateIssueLocked',
  'comicImprintLocked': 'imprintLocked',
  'comicFormatLocked': 'formatLocked',
  'comicBlackAndWhiteLocked': 'blackAndWhiteLocked',
  'comicMangaLocked': 'mangaLocked',
  'comicReadingDirectionLocked': 'readingDirectionLocked',
  'comicWebLinkLocked': 'webLinkLocked',
  'comicNotesLocked': 'notesLocked',
  'comicPencillersLocked': 'pencillersLocked',
  'comicInkersLocked': 'inkersLocked',
  'comicColoristsLocked': 'coloristsLocked',
  'comicLetterersLocked': 'letterersLocked',
  'comicCoverArtistsLocked': 'coverArtistsLocked',
  'comicEditorsLocked': 'editorsLocked',
  'comicCharactersLocked': 'charactersLocked',
  'comicTeamsLocked': 'teamsLocked',
  'comicLocationsLocked': 'locationsLocked',
};

export const TOP_FIELD_NAMES = ['title', 'subtitle', 'publisher', 'publishedDate'];
export const ARRAY_FIELD_NAMES = ['authors', 'categories', 'moods', 'tags'];
export const TEXTAREA_FIELD_NAMES = ['description'];
export const SERIES_FIELD_NAMES = ['seriesName', 'seriesNumber', 'seriesTotal'];
export const BOOK_DETAILS_FIELD_NAMES = ['language', 'isbn10', 'isbn13', 'pageCount'];

export function getTopFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => TOP_FIELD_NAMES.includes(f.controlName));
}

export function getArrayFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => f.type === 'array');
}

export function getTextareaFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => f.type === 'textarea');
}

export function getSeriesFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => SERIES_FIELD_NAMES.includes(f.controlName));
}

export function getBookDetailsFields(): MetadataFieldConfig[] {
  return ALL_METADATA_FIELDS.filter(f => BOOK_DETAILS_FIELD_NAMES.includes(f.controlName));
}

export function getProviderFields(enabledProviderFields?: MetadataProviderSpecificFields | null): MetadataFieldConfig[] {
  const providerFields = ALL_METADATA_FIELDS.filter(f => !!f.providerKey);

  if (enabledProviderFields) {
    return providerFields.filter(field =>
      !field.providerKey || enabledProviderFields[field.providerKey]
    );
  }

  return providerFields;
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
