import {inject, Injectable, signal} from '@angular/core';
import {Location} from '@angular/common';
import {Subject} from 'rxjs';
import {CbxSidebarService} from '../sidebar/cbx-sidebar.service';

export interface CbxHeaderState {
  isFullscreen: boolean;
  isSlideshowActive: boolean;
  isMagnifierActive: boolean;
}

@Injectable()
export class CbxHeaderService {
  private readonly defaultState: CbxHeaderState = {
    isFullscreen: false,
    isSlideshowActive: false,
    isMagnifierActive: false
  };

  private sidebarService = inject(CbxSidebarService);
  private location = inject(Location);

  private readonly _bookTitle = signal('');
  readonly bookTitle = this._bookTitle.asReadonly();

  private readonly _forceVisible = signal(true);
  readonly forceVisible = this._forceVisible.asReadonly();

  private readonly _state = signal<CbxHeaderState>(this.defaultState);
  readonly state = this._state.asReadonly();

  private _showQuickSettings = new Subject<void>();
  showQuickSettings$ = this._showQuickSettings.asObservable();

  private _toggleBookmark = new Subject<void>();
  toggleBookmark$ = this._toggleBookmark.asObservable();

  private _openNoteDialog = new Subject<void>();
  openNoteDialog$ = this._openNoteDialog.asObservable();

  private _toggleFullscreen = new Subject<void>();
  toggleFullscreen$ = this._toggleFullscreen.asObservable();

  private _toggleSlideshow = new Subject<void>();
  toggleSlideshow$ = this._toggleSlideshow.asObservable();

  private _toggleMagnifier = new Subject<void>();
  toggleMagnifier$ = this._toggleMagnifier.asObservable();

  private _showShortcutsHelp = new Subject<void>();
  showShortcutsHelp$ = this._showShortcutsHelp.asObservable();

  initialize(title: string | undefined): void {
    this._bookTitle.set(title || '');
  }

  setForceVisible(visible: boolean): void {
    this._forceVisible.set(visible);
  }

  updateState(partial: Partial<CbxHeaderState>): void {
    this._state.update(current => ({...current, ...partial}));
  }

  openSidebar(): void {
    this.sidebarService.open();
  }

  openQuickSettings(): void {
    this._showQuickSettings.next();
  }

  toggleBookmark(): void {
    this._toggleBookmark.next();
  }

  openNoteDialog(): void {
    this._openNoteDialog.next();
  }

  toggleFullscreen(): void {
    this._toggleFullscreen.next();
  }

  toggleSlideshow(): void {
    this._toggleSlideshow.next();
  }

  toggleMagnifier(): void {
    this._toggleMagnifier.next();
  }

  showShortcutsHelp(): void {
    this._showShortcutsHelp.next();
  }

  close(): void {
    this.location.back();
  }

  reset(): void {
    this._forceVisible.set(true);
    this._state.set(this.defaultState);
    this._bookTitle.set('');
  }
}
