import type {IconType} from '../../../shared/icons/icon-selection';
import type {BookFileType} from '../../book/data/book-response.models';
import type {LibraryOrganizationMode, MetadataSource} from './library-query.models';

export interface CreateLibraryVariables {
  readonly name: string;
  readonly icon?: string;
  readonly iconType?: IconType;
  readonly paths: readonly {readonly path: string}[];
  readonly watch: boolean;
  readonly formatPriority?: readonly BookFileType[];
  readonly allowedFormats?: readonly BookFileType[];
  readonly metadataSource?: MetadataSource;
  readonly organizationMode?: LibraryOrganizationMode;
}

export interface UpdateLibraryVariables {
  readonly libraryId: number;
  readonly library: CreateLibraryVariables;
}

export interface SetFileNamingPatternVariables {
  readonly libraryId: number;
  readonly fileNamingPattern: string;
}
