import type {PagedResponse} from '../../../core/data/page.models';
import type {BookFileType} from '../../book/data/book-response.models';

export type AnnotationStyle = 'highlight' | 'underline' | 'strikethrough' | 'squiggly';

export interface Annotation {
  readonly id: number;
  readonly bookId: number;
  readonly cfi: string;
  readonly text: string;
  readonly color: string;
  readonly style: AnnotationStyle;
  readonly note: string | null;
  readonly chapterTitle: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface Bookmark {
  readonly id: number;
  readonly bookId: number;
  readonly cfi: string | null;
  readonly pageNumber: number | null;
  readonly positionMs: number | null;
  readonly trackIndex: number | null;
  readonly title: string | null;
  readonly color: string | null;
  readonly notes: string | null;
  readonly priority: number | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface Note {
  readonly id: number;
  readonly bookId: number;
  readonly cfi: string;
  readonly selectedText: string | null;
  readonly noteContent: string;
  readonly color: string;
  readonly chapterTitle: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface BookNote {
  readonly id: number;
  readonly bookId: number;
  readonly title: string | null;
  readonly content: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface EbookViewerSettings {
  readonly fontFamily: string | null;
  readonly fontSize: number;
  readonly lineHeight: number;
  readonly gap: number;
  readonly justify: boolean;
  readonly hyphenate: boolean;
  readonly isDark: boolean;
  readonly theme: string;
  readonly flow: 'paginated' | 'scrolled';
  readonly maxColumnCount: number;
  readonly maxInlineSize: number;
  readonly maxBlockSize: number;
}

export interface CbxViewerSettings {
  readonly pageSpread: 'EVEN' | 'ODD';
  readonly pageViewMode: 'SINGLE_PAGE' | 'TWO_PAGE';
  readonly fitMode: 'ACTUAL_SIZE' | 'FIT_PAGE' | 'FIT_WIDTH' | 'FIT_HEIGHT' | 'AUTO';
  readonly scrollMode: 'PAGINATED' | 'INFINITE' | 'LONG_STRIP';
  readonly backgroundColor: 'GRAY' | 'BLACK' | 'WHITE';
}

export type StoredCbxViewerSettings = Omit<CbxViewerSettings, 'fitMode' | 'scrollMode' | 'backgroundColor'> & {
  readonly fitMode: CbxViewerSettings['fitMode'] | null;
  readonly scrollMode: CbxViewerSettings['scrollMode'] | null;
  readonly backgroundColor: CbxViewerSettings['backgroundColor'] | null;
};

export interface PdfViewerSettings {
  readonly zoom: string;
  readonly spread: 'none' | 'even' | 'odd' | 'off';
  readonly isDarkTheme: boolean;
}

export interface ViewerSettings {
  readonly ebookSettings: EbookViewerSettings | null;
  readonly cbxSettings: StoredCbxViewerSettings | null;
  readonly pdfSettings: PdfViewerSettings | null;
}

export interface ReadingSession {
  readonly id: number;
  readonly bookId: number;
  readonly bookTitle: string;
  readonly bookType: BookFileType;
  readonly startTime: string;
  readonly endTime: string;
  readonly durationSeconds: number;
  readonly startProgress: number | null;
  readonly endProgress: number | null;
  readonly progressDelta: number | null;
  readonly startLocation: string | null;
  readonly endLocation: string | null;
  readonly createdAt: string;
}

export interface ReadingSessionPageParams {
  readonly page: number;
  readonly size: number;
}

export type ReadingSessionPage = PagedResponse<ReadingSession>;

export const NOTEBOOK_ENTRY_TYPES = ['HIGHLIGHT', 'NOTE', 'BOOKMARK'] as const;
export type NotebookEntryType = typeof NOTEBOOK_ENTRY_TYPES[number];

export interface NotebookEntry {
  readonly id: number;
  readonly type: NotebookEntryType;
  readonly bookId: number;
  readonly bookTitle: string;
  readonly text: string | null;
  readonly note: string | null;
  readonly color: string | null;
  readonly style: AnnotationStyle | null;
  readonly chapterTitle: string | null;
  readonly primaryBookType: BookFileType | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface NotebookFilterParams {
  readonly types: readonly NotebookEntryType[];
  readonly bookId: number | null;
  readonly search: string;
  readonly sort: 'asc' | 'desc';
}

export interface NotebookPageParams extends NotebookFilterParams {
  readonly page: number;
  readonly size: number;
}

export type NotebookPage = PagedResponse<NotebookEntry>;

export interface NotebookBook {
  readonly bookId: number;
  readonly bookTitle: string;
}

export interface EpubSpineItem {
  readonly idref: string;
  readonly href: string;
  readonly mediaType: string;
  readonly linear: boolean;
}

export interface EpubManifestItem {
  readonly id: string;
  readonly href: string;
  readonly mediaType: string;
  readonly properties: readonly string[] | null;
  readonly size: number;
}

export interface EpubTocItem {
  readonly label: string;
  readonly href: string | null;
  readonly children: readonly EpubTocItem[] | null;
}

export interface EpubMetadata {
  readonly title?: string;
  readonly creator?: string;
  readonly language?: string;
  readonly publisher?: string;
  readonly identifier?: string;
  readonly description?: string;
  readonly 'rendition:layout'?: string;
  readonly 'rendition:orientation'?: string;
  readonly 'rendition:spread'?: string;
  readonly 'media:duration'?: string;
  readonly 'page-progression-direction'?: string;
}

export interface EpubInfo {
  readonly containerPath: string;
  readonly rootPath: string;
  readonly spine: readonly EpubSpineItem[];
  readonly manifest: readonly EpubManifestItem[];
  readonly toc: EpubTocItem | null;
  readonly metadata: EpubMetadata;
  readonly coverPath: string | null;
}

export interface ComicPageInfo {
  readonly pageNumber: number;
  readonly displayName: string;
}

export interface ComicPageDimensions {
  readonly pageNumber: number;
  readonly width: number;
  readonly height: number;
  readonly wide: boolean;
}

export interface AudiobookChapter {
  readonly index: number;
  readonly title: string;
  readonly startTimeMs: number;
  readonly endTimeMs: number;
  readonly durationMs: number;
}

export interface AudiobookTrack {
  readonly index: number;
  readonly fileName: string;
  readonly title: string;
  readonly durationMs: number | null;
  readonly fileSizeBytes: number;
  readonly cumulativeStartMs: number;
}

interface AudiobookInfoBase {
  readonly bookId: number;
  readonly bookFileId: number;
  readonly title: string | null;
  readonly author: string | null;
  readonly narrator: string | null;
  readonly durationMs: number | null;
  readonly bitrate: number | null;
  readonly codec: string | null;
  readonly sampleRate: number | null;
  readonly channels: number | null;
  readonly totalSizeBytes: number | null;
}

export type AudiobookInfo = AudiobookInfoBase & (
  | {readonly folderBased: false; readonly chapters: readonly AudiobookChapter[] | null; readonly tracks: null}
  | {readonly folderBased: true; readonly chapters: null; readonly tracks: readonly AudiobookTrack[]}
);
