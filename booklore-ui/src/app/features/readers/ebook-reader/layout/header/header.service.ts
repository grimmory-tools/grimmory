import {inject, Injectable} from '@angular/core';
import {Location} from '@angular/common';
import {BehaviorSubject, Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {ReaderStateService} from '../../state/reader-state.service';
import {ReaderSidebarService} from '../sidebar/sidebar.service';
import {ReaderLeftSidebarService} from '../panel/panel.service';
import {BookService} from '../../../../book/service/book.service';
import {EbookViewerSetting} from '../../../../book/model/book.model';

@Injectable()
export class ReaderHeaderService {
  private stateService = inject(ReaderStateService);
  private sidebarService = inject(ReaderSidebarService);
  private leftSidebarService = inject(ReaderLeftSidebarService);
  private bookService = inject(BookService);
  private location = inject(Location);

  private destroy$ = new Subject<void>();
  private bookId!: number;
  private bookTitle = '';

  private _forceVisible = new BehaviorSubject<boolean>(false);
  forceVisible$ = this._forceVisible.asObservable();

  private _isCurrentCfiBookmarked = new BehaviorSubject<boolean>(false);
  isCurrentCfiBookmarked$ = this._isCurrentCfiBookmarked.asObservable();

  private _showControls = new Subject<void>();
  private _showMetadata = new Subject<void>();
  private _toggleFullscreen = new Subject<void>();
  private _showShortcutsHelp = new Subject<void>();
  private _isFullscreen = new BehaviorSubject<boolean>(false);
  showControls$ = this._showControls.asObservable();
  showMetadata$ = this._showMetadata.asObservable();
  toggleFullscreen$ = this._toggleFullscreen.asObservable();
  showShortcutsHelp$ = this._showShortcutsHelp.asObservable();
  fullscreenState$ = this._isFullscreen.asObservable();

  get currentState() {
    return this.stateService.currentState;
  }

  get title(): string {
    return this.bookTitle;
  }

  get isVisible(): boolean {
    return this._forceVisible.value;
  }

  initialize(bookId: number, title: string, destroy$: Subject<void>): void {
    this.bookId = bookId;
    this.bookTitle = title;
    this.destroy$ = destroy$;
  }

  setForceVisible(visible: boolean): void {
    this._forceVisible.next(visible);
  }

  setCurrentCfiBookmarked(bookmarked: boolean): void {
    this._isCurrentCfiBookmarked.next(bookmarked);
  }

  openSidebar(): void {
    this.sidebarService.open();
  }

  openLeftSidebar(tab?: 'search' | 'notes'): void {
    this.leftSidebarService.open(tab);
  }

  createBookmark(): void {
    this.sidebarService.toggleBookmark();
  }

  openControls(): void {
    this._showControls.next();
  }

  openMetadata(): void {
    this._showMetadata.next();
  }

  toggleFullscreen(): void {
    this._toggleFullscreen.next();
  }

  setFullscreen(isFullscreen: boolean): void {
    this._isFullscreen.next(isFullscreen);
  }

  showShortcutsHelp(): void {
    this._showShortcutsHelp.next();
  }

  close(): void {
    this.location.back();
  }

  toggleDarkMode(): void {
    this.stateService.toggleDarkMode();
    this.syncSettingsToBackend();
  }

  increaseFontSize(): void {
    this.stateService.updateFontSize(1);
    this.syncSettingsToBackend();
  }

  private syncSettingsToBackend(): void {
    const state = this.stateService.currentState;
    const setting: EbookViewerSetting = {
      lineHeight: state.lineHeight,
      justify: state.justify,
      hyphenate: state.hyphenate,
      maxColumnCount: state.maxColumnCount,
      gap: state.gap,
      fontSize: state.fontSize,
      theme: typeof state.theme === 'object' && 'name' in state.theme
        ? state.theme.name
        : (state.theme as any),
      maxInlineSize: state.maxInlineSize,
      maxBlockSize: state.maxBlockSize,
      fontFamily: state.fontFamily,
      isDark: state.isDark,
      flow: state.flow,
    };
    this.bookService.updateViewerSetting({ebookSettings: setting}, this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe();
  }

  reset(): void {
    this._forceVisible.next(false);
    this._isCurrentCfiBookmarked.next(false);
    this._isFullscreen.next(false);
    this.bookTitle = '';
  }
}
