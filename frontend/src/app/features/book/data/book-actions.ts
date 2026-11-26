import {type User} from '../../settings/user-management/user.service';
import {type BookCoverTarget} from './book-command.models';
import {type BookFileResponse, type BookSummary} from './book-response.models';

export function bookActionPermissions(
  permissions: User['permissions'] | null | undefined,
) {
  const admin = !!permissions?.admin;
  return {
    canDownload: admin || !!permissions?.canDownload,
    canEmailBook: admin || !!permissions?.canEmailBook,
    canEditMetadata: admin || !!permissions?.canEditMetadata,
    canLockUnlockMetadata: admin || !!permissions?.canBulkLockUnlockMetadata,
    canDeleteBook: admin || !!permissions?.canDeleteBook,
    canOrganizeFiles: admin || !!permissions?.canMoveOrganizeFiles,
  };
}

export function bookBulkActionPermissions(
  permissions: User['permissions'] | null | undefined,
) {
  const admin = !!permissions?.admin;
  return {
    canResetReadStatus: admin || !!permissions?.canBulkResetBookReadStatus,
    canAutoFetchMetadata: admin || !!permissions?.canBulkAutoFetchMetadata,
    canCustomFetchMetadata: admin || !!permissions?.canBulkCustomFetchMetadata,
    canEditMetadata: admin || !!permissions?.canBulkEditMetadata,
    canRegenerateCover: admin || !!permissions?.canBulkRegenerateCover,
    canLockUnlockMetadata: admin || !!permissions?.canBulkLockUnlockMetadata,
    canResetGrimmoryProgress: admin || !!permissions?.canBulkResetGrimmoryReadProgress,
    canResetKoreaderProgress: admin || !!permissions?.canBulkResetKoReaderReadProgress,
    canOrganizeFiles: admin || !!permissions?.canMoveOrganizeFiles,
    canAttachFiles: admin || !!permissions?.canManageLibrary,
    canDeleteBook: admin || !!permissions?.canDeleteBook,
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

export function bookCoverTargets(
  book: BookSummary,
  kind: 'regenerate' | 'generate',
): readonly BookCoverTarget[] {
  const files = [
    ...(book.primaryFile ? [book.primaryFile] : []),
    ...(book.alternativeFormats ?? []),
  ];
  const hasAudiobook = files.some(file => file.bookType === 'AUDIOBOOK');
  const hasEbook = files.some(file => file.bookType !== 'AUDIOBOOK');
  const targets: BookCoverTarget[] = [];
  if (hasEbook || (kind === 'generate' && !hasAudiobook)) {
    targets.push('ebook');
  }
  if (hasAudiobook) {
    targets.push('audiobook');
  }
  return targets;
}
