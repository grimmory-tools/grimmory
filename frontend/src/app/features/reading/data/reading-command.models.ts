import type {BookFileType} from '../../book/data/book-response.models';
import type {AnnotationStyle, CbxViewerSettings, EbookViewerSettings, PdfViewerSettings} from './reading-query.models';

export interface CreateAnnotationVariables {
  readonly bookId: number;
  readonly cfi: string;
  readonly text: string;
  readonly color?: string;
  readonly style?: AnnotationStyle;
  readonly note?: string;
  readonly chapterTitle?: string;
}

export interface DeleteAnnotationVariables {
  readonly bookId: number;
  readonly annotationId: number;
}

export type CreateBookmarkVariables = {readonly bookId: number; readonly title?: string} & (
  | {readonly cfi: string; readonly pageNumber?: never; readonly positionMs?: never; readonly trackIndex?: never}
  | {readonly pageNumber: number; readonly cfi?: never; readonly positionMs?: never; readonly trackIndex?: never}
  | {readonly positionMs: number; readonly trackIndex?: number; readonly cfi?: never; readonly pageNumber?: never}
);

export interface DeleteBookmarkVariables {
  readonly bookId: number;
  readonly bookmarkId: number;
}

export interface CreateNoteVariables {
  readonly bookId: number;
  readonly cfi: string;
  readonly noteContent: string;
  readonly selectedText?: string;
  readonly color?: string;
  readonly chapterTitle?: string;
}

export interface UpdateNoteVariables {
  readonly bookId: number;
  readonly noteId: number;
  readonly noteContent: string;
  readonly color: string;
}

export interface DeleteNoteVariables {
  readonly bookId: number;
  readonly noteId: number;
}

export interface CreateBookNoteVariables {
  readonly bookId: number;
  readonly title?: string;
  readonly content: string;
}

export interface UpdateBookNoteVariables {
  readonly bookId: number;
  readonly bookNoteId: number;
  readonly title?: string;
  readonly content: string;
}

export interface DeleteBookNoteVariables {
  readonly bookId: number;
  readonly bookNoteId: number;
}

export interface SavePdfAnnotationsVariables {
  readonly bookId: number;
  readonly data: string;
}

export type SaveViewerSettingsVariables = {readonly bookId: number} & (
  | {readonly ebookSettings: EbookViewerSettings; readonly cbxSettings?: never; readonly pdfSettings?: never}
  | {readonly cbxSettings: CbxViewerSettings; readonly ebookSettings?: never; readonly pdfSettings?: never}
  | {readonly pdfSettings: PdfViewerSettings; readonly ebookSettings?: never; readonly cbxSettings?: never}
);

export interface RecordReadingSessionVariables {
  readonly bookId: number;
  readonly bookType: BookFileType;
  readonly startTime: string;
  readonly endTime: string;
  readonly durationSeconds: number;
  readonly startProgress?: number;
  readonly endProgress?: number;
  readonly progressDelta?: number;
  readonly startLocation?: string;
  readonly endLocation?: string;
}
