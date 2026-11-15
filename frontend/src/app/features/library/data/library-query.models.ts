import type {IconType} from '../../../shared/icons/icon-selection';
import type {BookFileType} from '../../book/data/book-response.models';

export type LibraryOrganizationMode = 'BOOK_PER_FILE' | 'BOOK_PER_FOLDER' | 'AUTO_DETECT';

export type MetadataSource = 'EMBEDDED' | 'SIDECAR' | 'PREFER_SIDECAR' | 'PREFER_EMBEDDED' | 'NONE';

export interface LibraryPath {
  readonly id: number;
  readonly path: string;
}

export interface Library {
  readonly id: number;
  readonly name: string;
  readonly icon?: string;
  readonly iconType?: IconType;
  readonly fileNamingPattern?: string;
  readonly watch: boolean;
  readonly paths: readonly LibraryPath[];
  readonly formatPriority?: readonly BookFileType[];
  readonly allowedFormats?: readonly BookFileType[];
  readonly organizationMode?: LibraryOrganizationMode;
  readonly metadataSource?: MetadataSource;
}
