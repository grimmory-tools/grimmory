import {inject, Injectable} from '@angular/core';
import {Subject} from 'rxjs';
import {TranslocoService} from '@jsverse/transloco';
import {BookPatchService} from '../../../book/service/book-patch.service';
import {ReadingSessionService} from '../../../../shared/service/reading-session.service';
import {PageInfo, ThemeInfo} from '../core/view-manager.service';
import {ReaderViewManagerService} from '../core/view-manager.service';
import {ReaderStateService} from './reader-state.service';
import {ReaderAnnotationHttpService} from '../features/annotations/annotation.service';
import {ReaderBookmarkService} from '../features/bookmarks/bookmark.service';
import {BookType} from '../../../book/model/book.model';

export interface ProgressState {
  cfi: string | null;
  href: string | null;
  chapterName: string | null;
  chapterHref: string | null;
  fraction: number;
  pageInfo: PageInfo | undefined;
  progressData: any;
}

@Injectable()
export class ReaderProgressService {
  private bookPatchService = inject(BookPatchService);
  private readingSessionService = inject(ReadingSessionService);
  private readonly t = inject(TranslocoService);
  private viewManager = inject(ReaderViewManagerService);
  private stateService = inject(ReaderStateService);
  private annotationService = inject(ReaderAnnotationHttpService);
  private bookmarkService = inject(ReaderBookmarkService);

  private bookId!: number;
  private bookType!: BookType;
  private bookFileId?: number;
  private hasStartedSession = false;

  private _currentCfi: string | null = null;
  private _currentChapterName: string | null = null;
  private _currentChapterHref: string | null = null;
  private _currentPageInfo: PageInfo | undefined;
  private _currentProgressData: any = null;

  private progressSubject = new Subject<ProgressState>();
  public progress$ = this.progressSubject.asObservable();

  get currentCfi(): string | null {
    return this._currentCfi;
  }

  get currentChapterName(): string | null {
    return this._currentChapterName;
  }

  get currentChapterHref(): string | null {
    return this._currentChapterHref;
  }

  get currentProgressData(): any {
    return this._currentProgressData;
  }

  get currentPageInfo(): PageInfo | undefined {
    return this._currentPageInfo;
  }

  initialize(bookId: number, bookType: BookType, bookFileId?: number): void {
    this.bookId = bookId;
    this.bookType = bookType;
    this.bookFileId = bookFileId;
    this.hasStartedSession = false;
  }

  handleRelocateEvent(detail: any): void {
    this._currentProgressData = detail;

    const cfi = detail?.cfi ?? null;
    const href = detail?.pageItem?.href ?? detail?.tocItem?.href ?? null;
    const percentage = typeof detail?.fraction === 'number' ? detail.fraction * 100 : null;

    if (!this.hasStartedSession && cfi && percentage !== null) {
      this.hasStartedSession = true;
      this.readingSessionService.startSession(this.bookId, this.bookType, cfi, percentage);
    }

    if (cfi && percentage !== null) {
      this.bookPatchService.saveEpubProgress(this.bookId, cfi, href, percentage, this.bookFileId);
      this.readingSessionService.updateProgress(cfi, percentage);
    }

    const chapterLabel = detail?.tocItem?.label;
    if (chapterLabel && chapterLabel !== this._currentChapterName) {
      this._currentChapterName = chapterLabel;
      this.annotationService.updateCurrentChapter(chapterLabel);
    }

    if (href && href !== this._currentChapterHref) {
      this._currentChapterHref = href;
    }

    if (detail?.section) {
      const percentCompleted = Math.round((detail.fraction * 100) * 10) / 10;
      const totalMinutes = detail.time?.section ?? 0;

      const hours = Math.floor(totalMinutes / 60);
      const minutes = Math.floor(totalMinutes % 60);
      const seconds = Math.round((totalMinutes - Math.floor(totalMinutes)) * 60);

      const parts: string[] = [];
      if (hours) parts.push(`${hours}h`);
      if (minutes) parts.push(`${minutes}m`);
      if (seconds || parts.length === 0) parts.push(`${seconds}s`);

      this._currentPageInfo = {
        percentCompleted,
        sectionTimeText: parts.join(' ')
      };
    }

    if (this.stateService.currentState.flow === 'paginated') {
      this.updateHeadersAndFooters();
    }

    if (cfi) {
      this._currentCfi = cfi;
      this.bookmarkService.updateCurrentPosition(cfi, chapterLabel);
    }

    this.progressSubject.next({
      cfi: this._currentCfi,
      href,
      chapterName: this._currentChapterName,
      chapterHref: this._currentChapterHref,
      fraction: detail?.fraction ?? 0,
      pageInfo: this._currentPageInfo,
      progressData: this._currentProgressData
    });
  }

  updateHeadersAndFooters(): void {
    const renderer = this.viewManager.getRenderer();
    if (!renderer) return;

    const theme: ThemeInfo = {
      fg: this.stateService.currentState.theme.fg || this.stateService.currentState.theme.light.fg,
      bg: this.stateService.currentState.theme.bg || this.stateService.currentState.theme.light.bg
    };

    const timeLabel = this.t.translate('readerEbook.headerFooterUtil.timeRemainingInSection', {
      time: this._currentPageInfo?.sectionTimeText ?? '0s'
    });

    this.viewManager.updateHeadersAndFooters(
      this._currentChapterName || '',
      this._currentPageInfo,
      theme,
      timeLabel
    );
  }

  endSession(): void {
    if (this.readingSessionService.isSessionActive()) {
      const progress = typeof this._currentProgressData?.fraction === 'number'
        ? Math.round(this._currentProgressData.fraction * 100 * 100) / 100
        : undefined;
      this.readingSessionService.endSession(this._currentCfi || undefined, progress);
    }
  }

  reset(): void {
    this._currentCfi = null;
    this._currentChapterName = null;
    this._currentChapterHref = null;
    this._currentPageInfo = undefined;
    this._currentProgressData = null;
    this.hasStartedSession = false;
  }
}
