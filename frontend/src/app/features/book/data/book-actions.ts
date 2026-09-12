import {type User} from '../../settings/user-management/user.service';
import {type BookFileResponse, type BookSummary} from './book-response.models';

type BookActionPermissionSource = Pick<
  User['permissions'],
  | 'admin'
  | 'canDownload'
  | 'canEmailBook'
  | 'canEditMetadata'
  | 'canDeleteBook'
  | 'canBulkResetGrimmoryReadProgress'
  | 'canBulkResetKoReaderReadProgress'
  | 'canMoveOrganizeFiles'
>;

interface BookActionPermissions {
  readonly canDownload: boolean;
  readonly canEmailBook: boolean;
  readonly canEditMetadata: boolean;
  readonly canDeleteBook: boolean;
  readonly canResetGrimmoryProgress: boolean;
  readonly canResetKoreaderProgress: boolean;
  readonly canOrganizeFiles: boolean;
}

export function bookActionPermissions(
  permissions: BookActionPermissionSource | null | undefined,
): BookActionPermissions {
  const admin = !!permissions?.admin;
  return {
    canDownload: admin || !!permissions?.canDownload,
    canEmailBook: admin || !!permissions?.canEmailBook,
    canEditMetadata: admin || !!permissions?.canEditMetadata,
    canDeleteBook: admin || !!permissions?.canDeleteBook,
    canResetGrimmoryProgress: admin || !!permissions?.canBulkResetGrimmoryReadProgress,
    canResetKoreaderProgress: admin || !!permissions?.canBulkResetKoReaderReadProgress,
    canOrganizeFiles: admin || !!permissions?.canMoveOrganizeFiles,
  };
}

export type BookReadAction = 'read' | 'continueReading' | 'play' | 'continueListening';

export function bookGrimmoryProgress(
  book: BookSummary,
  file: BookFileResponse | undefined = book.primaryFile,
): number | null {
  switch (file?.bookType) {
    case 'EPUB':
    case 'FB2':
    case 'MOBI':
    case 'AZW3':
      return book.epubProgress?.percentage ?? null;
    case 'PDF':
      return book.pdfProgress?.percentage ?? null;
    case 'CBX':
      return book.cbxProgress?.percentage ?? null;
    case 'AUDIOBOOK':
      return book.audiobookProgress?.percentage ?? null;
    default:
      return null;
  }
}

export function bookProgressPercentage(
  book: BookSummary,
  file: BookFileResponse | undefined = book.primaryFile,
): number | null {
  const progress = bookGrimmoryProgress(book, file);
  if (file?.bookType === 'AUDIOBOOK') {
    return progress;
  }
  return progress ?? book.koreaderProgress?.percentage ?? book.koboProgress?.percentage ?? null;
}

function bookPartlyRead(book: BookSummary, file: BookFileResponse | undefined): boolean {
  const progress = bookProgressPercentage(book, file);
  return progress !== null && progress > 0 && progress < 100;
}

export function bookReadAction(
  book: BookSummary,
  file: BookFileResponse | undefined = book.primaryFile,
): BookReadAction {
  const audiobook = file?.bookType === 'AUDIOBOOK';
  if (bookPartlyRead(book, file)) {
    return audiobook ? 'continueListening' : 'continueReading';
  }
  return audiobook ? 'play' : 'read';
}
