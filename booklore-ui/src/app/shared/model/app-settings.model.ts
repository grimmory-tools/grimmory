import {MetadataRefreshOptions} from '../../features/metadata/model/request/metadata-refresh-options.model';

export interface MetadataMatchWeights {
  title: number;
  subtitle: number;
  description: number;
  authors: number;
  publisher: number;
  publishedDate: number;
  seriesName: number;
  seriesNumber: number;
  seriesTotal: number;
  isbn13: number;
  isbn10: number;
  language: number;
  pageCount: number;
  categories: number;
  amazonRating: number;
  amazonReviewCount: number;
  goodreadsRating: number;
  goodreadsReviewCount: number;
  hardcoverRating: number;
  hardcoverReviewCount: number;
  doubanRating: number;
  doubanReviewCount: number;
  lubimyczytacRating: number;
  ranobedbRating: number;
  coverImage: number;
}

export interface OidcProviderDetails {
  providerName: string;
  clientId: string;
  issuerUri: string;
  claimMapping: {
    username: string;
    email: string;
    name: string;
  };
}

export interface OidcAutoProvisionDetails {
  enableAutoProvisioning: boolean;
  defaultPermissions: string[];
  defaultLibraryIds: number[];
}

export interface MetadataProviderSettings {
  amazon: Amazon;
  google: Google;
  goodReads: Goodreads;
  ranobedb: Ranobedb;
  hardcover: Hardcover;
  comicvine: Comicvine;
  douban: Douban;
  lubimyczytac: Lubimyczytac;
}

export interface Amazon {
  enabled: boolean;
  cookie: string;
  domain: string;
}

export interface Google {
  enabled: boolean;
  language: string;
}

export interface Goodreads {
  enabled: boolean;
}

export interface Ranobedb {
  enabled: boolean;
}

export interface Hardcover {
  enabled: boolean;
  apiKey: string;
}

export interface Comicvine {
  enabled: boolean;
  apiKey: string;
}

export interface Douban {
  enabled: boolean;
}

export interface Lubimyczytac {
  enabled: boolean;
}

export interface FormatWriteSettings {
  enabled: boolean;
  maxFileSizeInMb: number;
}

export interface SaveToOriginalFileSettings {
  epub: FormatWriteSettings;
  pdf: FormatWriteSettings;
  cbx: FormatWriteSettings;
}

export interface MetadataPersistenceSettings {
  moveFilesToLibraryPattern: boolean;
  saveToOriginalFile: SaveToOriginalFileSettings;
  convertCbrCb7ToCbz: boolean;
}

export interface ReviewProviderConfig {
  provider: string;
  enabled: boolean;
  maxReviews: number;
}

export interface PublicReviewSettings {
  downloadEnabled: boolean;
  autoDownloadEnabled: boolean;
  providers: ReviewProviderConfig[];
}

export interface KoboSettings {
  convertToKepub: boolean;
  conversionLimitInMb: number;
  conversionImageCompressionPercentage: number;
  convertCbxToEpub: boolean;
  conversionLimitInMbForCbx: number;
  forceEnableHyphenation: boolean;
}

export interface CoverCroppingSettings {
  verticalCroppingEnabled: boolean;
  horizontalCroppingEnabled: boolean;
  aspectRatioThreshold: number;
  smartCroppingEnabled: boolean;
}

export interface AppSettings {
  autoBookSearch: boolean;
  similarBookRecommendation: boolean;
  defaultMetadataRefreshOptions: MetadataRefreshOptions;
  libraryMetadataRefreshOptions: MetadataRefreshOptions[];
  uploadPattern: string;
  opdsServerEnabled: boolean;
  komgaApiEnabled: boolean;
  komgaGroupUnknown: boolean;
  remoteAuthEnabled: boolean;
  oidcEnabled: boolean;
  oidcProviderDetails: OidcProviderDetails;
  oidcAutoProvisionDetails: OidcAutoProvisionDetails;
  maxFileUploadSizeInMb: number;
  metadataProviderSettings: MetadataProviderSettings;
  metadataMatchWeights: MetadataMatchWeights;
  metadataPersistenceSettings: MetadataPersistenceSettings;
  metadataPublicReviewsSettings: PublicReviewSettings;
  koboSettings: KoboSettings;
  coverCroppingSettings: CoverCroppingSettings;
  metadataDownloadOnBookdrop: boolean;
  telemetryEnabled: boolean;
  metadataProviderSpecificFields: MetadataProviderSpecificFields;
}

export interface MetadataProviderSpecificFields {
  asin: boolean;
  amazonRating: boolean;
  amazonReviewCount: boolean;
  googleId: boolean;
  goodreadsId: boolean;
  goodreadsRating: boolean;
  goodreadsReviewCount: boolean;
  hardcoverId: boolean;
  hardcoverBookId: boolean;
  hardcoverRating: boolean;
  hardcoverReviewCount: boolean;
  comicvineId: boolean;
  lubimyczytacId: boolean;
  lubimyczytacRating: boolean;
  ranobedbId: boolean;
  ranobedbRating: boolean;
}

export enum AppSettingKey {
  QUICK_BOOK_MATCH = 'QUICK_BOOK_MATCH',
  AUTO_BOOK_SEARCH = 'AUTO_BOOK_SEARCH',
  SIMILAR_BOOK_RECOMMENDATION = 'SIMILAR_BOOK_RECOMMENDATION',
  LIBRARY_METADATA_REFRESH_OPTIONS = 'LIBRARY_METADATA_REFRESH_OPTIONS',
  UPLOAD_FILE_PATTERN = 'UPLOAD_FILE_PATTERN',
  OPDS_SERVER_ENABLED = 'OPDS_SERVER_ENABLED',
  KOMGA_API_ENABLED = 'KOMGA_API_ENABLED',
  KOMGA_GROUP_UNKNOWN = 'KOMGA_GROUP_UNKNOWN',
  OIDC_ENABLED = 'OIDC_ENABLED',
  OIDC_PROVIDER_DETAILS = 'OIDC_PROVIDER_DETAILS',
  OIDC_AUTO_PROVISION_DETAILS = 'OIDC_AUTO_PROVISION_DETAILS',
  MAX_FILE_UPLOAD_SIZE_IN_MB = 'MAX_FILE_UPLOAD_SIZE_IN_MB',
  METADATA_PROVIDER_SETTINGS = 'METADATA_PROVIDER_SETTINGS',
  METADATA_MATCH_WEIGHTS = 'METADATA_MATCH_WEIGHTS',
  METADATA_PERSISTENCE_SETTINGS = 'METADATA_PERSISTENCE_SETTINGS',
  METADATA_DOWNLOAD_ON_BOOKDROP = 'METADATA_DOWNLOAD_ON_BOOKDROP',
  METADATA_PUBLIC_REVIEWS_SETTINGS = 'METADATA_PUBLIC_REVIEWS_SETTINGS',
  KOBO_SETTINGS = 'KOBO_SETTINGS',
  COVER_CROPPING_SETTINGS = 'COVER_CROPPING_SETTINGS',
  TELEMETRY_ENABLED = 'TELEMETRY_ENABLED',
  METADATA_PROVIDER_SPECIFIC_FIELDS = 'METADATA_PROVIDER_SPECIFIC_FIELDS',
}
