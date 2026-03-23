import {computed, inject, Injectable, signal} from '@angular/core';
import {Location} from '@angular/common';
import {Subject} from 'rxjs';
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

  private bookId!: number;
  private readonly _bookTitle = signal('');
  readonly bookTitle = this._bookTitle.asReadonly();

  private readonly _forceVisible = signal(false);
  readonly forceVisible = this._forceVisible.asReadonly();

  private readonly _isCurrentCfiBookmarked = signal(false);
  readonly isCurrentCfiBookmarked = this._isCurrentCfiBookmarked.asReadonly();

  private _showControls = new Subject<void>();
  private _showMetadata = new Subject<void>();
  private _toggleFullscreen = new Subject<void>();
  private _showShortcutsHelp = new Subject<void>();
  private readonly _isFullscreen = signal(false);
  readonly isFullscreen = this._isFullscreen.asReadonly();
  showControls$ = this._showControls.asObservable();
  showMetadata$ = this._showMetadata.asObservable();
  toggleFullscreen$ = this._toggleFullscreen.asObservable();
  showShortcutsHelp$ = this._showShortcutsHelp.asObservable();
  readonly theme = computed(() => this.stateService.state().theme);

  initialize(bookId: number, title: string): void {
    this.bookId = bookId;
    this._bookTitle.set(title);
  }

  setForceVisible(visible: boolean): void {
    this._forceVisible.set(visible);
  }

  setCurrentCfiBookmarked(bookmarked: boolean): void {
    this._isCurrentCfiBookmarked.set(bookmarked);
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
    this._isFullscreen.set(isFullscreen);
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
    const state = this.stateService.state();
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
    this.bookService.updateViewerSetting({ebookSettings: setting}, this.bookId).subscribe();
  }

  reset(): void {
    this._forceVisible.set(false);
    this._isCurrentCfiBookmarked.set(false);
    this._isFullscreen.set(false);
    this._bookTitle.set('');
  }
}
