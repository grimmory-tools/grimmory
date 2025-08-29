import {SortOption} from './sort.model';

export type LibraryScanMode = 'FILE_AS_BOOK' | 'FOLDER_AS_BOOK';
export type BookFileType = 'PDF' | 'EPUB' | 'CBX';

export interface Library {
  id?: number;
  name: string;
  icon: string;
  watch: boolean;
  fileNamingPattern?: string;
  sort?: SortOption;
  paths: LibraryPath[];
  scanMode?: LibraryScanMode;
  defaultBookFormat?: BookFileType;
}

export interface LibraryPath {
  id?: number;
  path: string;
}
