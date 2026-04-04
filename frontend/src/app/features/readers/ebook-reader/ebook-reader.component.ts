import {Component, CUSTOM_ELEMENTS_SCHEMA, effect, HostListener, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {from, Observable, of, Subject, throwError} from 'rxjs';
import {catchError, map, switchMap, takeUntil, tap} from 'rxjs/operators';
import {MessageService} from 'primeng/api';
import {ReaderLoaderService} from './core/loader.service';
import {ReaderViewManagerService} from './core/view-manager.service';
import {ReaderStateService} from './state/reader-state.service';
import {ReaderStyleService} from './core/style.service';
import {ReaderBookmarkService} from './features/bookmarks/bookmark.service';
import {ReaderAnnotationHttpService} from './features/annotations/annotation.service';
import {ReaderProgressService} from './state/progress.service';
import {ReaderSelectionService} from './features/selection/selection.service';
import {ReaderSidebarService} from './layout/sidebar/sidebar.service';
import {ReaderLeftSidebarService} from './layout/panel/panel.service';
import {ReaderHeaderService} from './layout/header/header.service';
import {ReaderNoteService} from './features/notes/note.service';
import {BookService} from '../../book/service/book.service';
import {BookFileService} from '../../book/service/book-file.service';
import {ActivatedRoute} from '@angular/router';
import {Book, BookType} from '../../book/model/book.model';
import {ReaderHeaderComponent} from './layout/header/header.component';
import {ReaderSidebarComponent} from './layout/sidebar/sidebar.component';
import {ReaderLeftSidebarComponent} from './layout/panel/panel.component';
import {ReaderNavbarComponent} from './layout/footer/footer.component';
import {ReaderSettingsDialogComponent} from './dialogs/settings-dialog.component';
import {ReaderQuickSettingsComponent} from './layout/header/quick-settings.component';
import {ReaderBookMetadataDialogComponent} from './dialogs/metadata-dialog.component';
import {ReaderHeaderFooterVisibilityManager} from './shared/visibility.util';
import {EpubCustomFontService} from './features/fonts/custom-font.service';
import {TextSelectionAction, TextSelectionPopupComponent} from './shared/selection-popup.component';
import {NoteDialogResult, ReaderNoteDialogComponent} from './dialogs/note-dialog.component';
import {EbookShortcutsHelpComponent} from './dialogs/shortcuts-help.component';
import {TranslocoPipe} from '@jsverse/transloco';
import {RelocateProgressData} from './state/progress.service';
import {WakeLockService} from '../../../shared/service/wake-lock.service';

@Component({
  selector: 'app-ebook-reader',
  standalone: true,
  imports: [
    CommonModule,
    ReaderHeaderComponent,
    ReaderSettingsDialogComponent,
    ReaderQuickSettingsComponent,
    ReaderBookMetadataDialogComponent,
    ReaderSidebarComponent,
    ReaderLeftSidebarComponent,
    ReaderNavbarComponent,
    TextSelectionPopupComponent,
    ReaderNoteDialogComponent,
    EbookShortcutsHelpComponent,
    TranslocoPipe
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  providers: [
    MessageService,
    ReaderLoaderService,
    ReaderViewManagerService,
    ReaderStateService,
    ReaderStyleService,
    ReaderBookmarkService,
    ReaderAnnotationHttpService,
    ReaderProgressService,
    ReaderSelectionService,
    ReaderSidebarService,
    ReaderLeftSidebarService,
    ReaderHeaderService,
    ReaderNoteService
  ],
  templateUrl: './ebook-reader.component.html',
  styleUrls: ['./ebook-reader.component.scss']
})
export class EbookReaderComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private loaderService = inject(ReaderLoaderService);
  private styleService = inject(ReaderStyleService);
  private bookService = inject(BookService);
  private bookFileService = inject(BookFileService);
  private route = inject(ActivatedRoute);
  private epubCustomFontService = inject(EpubCustomFontService);
  private annotationService = inject(ReaderAnnotationHttpService);
  private progressService = inject(ReaderProgressService);
  private selectionService = inject(ReaderSelectionService);
  private headerService = inject(ReaderHeaderService);
  private noteService = inject(ReaderNoteService);
  private wakeLockService = inject(WakeLockService);

  public sidebarService = inject(ReaderSidebarService);
  public leftSidebarService = inject(ReaderLeftSidebarService);
  public viewManager = inject(ReaderViewManagerService);
  public stateService = inject(ReaderStateService);

  protected bookId!: number;
  protected altBookType?: string;

  private hasLoadedOnce = false;
  private _fileUrl: string | null = null;
  private visibilityManager!: ReaderHeaderFooterVisibilityManager;
  private relocateTimeout?: ReturnType<typeof setTimeout>;
  private sectionFractionsTimeout?: ReturnType<typeof setTimeout>;

  isLoading = true;
  showQuickSettings = false;
  showControls = false;
  showMetadata = false;
  forceNavbarVisible = false;
  headerVisible = false;
  book: Book | null = null;
  sectionFractions: number[] = [];
  isFullscreen = false;
  showShortcutsHelp = false;
  immersiveMode = false;
  private immersiveAutoHideTimer?: ReturnType<typeof setTimeout>;

  readonly readerState = this.stateService.state;
  readonly selectionState = this.selectionService.state;
  readonly noteDialogState = this.noteService.dialogState;
  readonly isCurrentCfiBookmarked = this.headerService.isCurrentCfiBookmarked;

  constructor() {
    effect(() => {
      this.stateService.state();
      this.applyStyles();
    });

    effect(
      () => {
        this.sidebarService.bookmarks();
        this.updateBookmarkIndicator();
      },
      {allowSignalWrites: true}
    );
  }

  get currentProgressData(): RelocateProgressData | null {
    return this.progressService.currentProgressData;
  }

  ngOnInit() {
    this.visibilityManager = new ReaderHeaderFooterVisibilityManager(window.innerHeight);
    this.visibilityManager.onStateChange((state) => {
      this.headerVisible = state.headerVisible;
      this.headerService.setForceVisible(state.headerVisible);
      this.forceNavbarVisible = state.footerVisible;
    });

    this.sidebarService.showMetadata$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.showMetadata = true);

    this.headerService.showControls$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.showQuickSettings = true);

    this.headerService.showMetadata$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.showMetadata = true);

    this.headerService.toggleFullscreen$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.toggleFullscreen());

    this.headerService.showShortcutsHelp$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.showShortcutsHelp = true);

    // Enable wake lock after a short delay
    setTimeout(() => this.wakeLockService.enable(), 1000);

    this.isLoading = true;
    this.initializeFoliate().pipe(
      switchMap(() => this.epubCustomFontService.loadAndCacheFonts()),
      tap(() => this.stateService.refreshCustomFonts()),
      switchMap(() => this.setupView()),
      tap(() => {
        this.subscribeToViewEvents();
      }),
      switchMap(() => this.loadBookFromAPI()),
      tap(() => this.isLoading = false),
      catchError(() => {
        this.isLoading = false;
        return of(null);
      }),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  ngOnDestroy(): void {
    this.wakeLockService.disable();
    this.destroy$.next();
    this.destroy$.complete();
    this.viewManager.destroy();
    this.annotationService.reset();
    this.progressService.endSession();
    this.progressService.reset();
    this.selectionService.reset();
    this.sidebarService.reset();
    this.leftSidebarService.reset();
    this.headerService.reset();
    this.noteService.reset();
    this.epubCustomFontService.cleanup();

    if (this.immersiveAutoHideTimer) {
      clearTimeout(this.immersiveAutoHideTimer);
    }
    if (this._fileUrl) {
      URL.revokeObjectURL(this._fileUrl);
      this._fileUrl = null;
    }
  }

  private initializeFoliate(): Observable<void> {
    return this.loaderService.loadFoliateScript().pipe(
      switchMap(() => this.loaderService.waitForCustomElement())
    );
  }

  private setupView(): Observable<void> {
    const container = document.getElementById('foliate-container');
    if (!container) {
      return throwError(() => new Error('Container not found'));
    }
    container.setAttribute('tabindex', '0');
    this.viewManager.createView(container);
    return of(undefined);
  }

  private loadBookFromAPI(): Observable<void> {
    this.bookId = +this.route.snapshot.paramMap.get('bookId')!;
    this.altBookType = this.route.snapshot.queryParamMap.get('bookType') ?? undefined;

    return from(this.bookService.ensureBookDetail(this.bookId, false)).pipe(
      switchMap((book) => {
        this.book = book;

        // Use alternative bookType from query param if provided, otherwise use primary
        const bookType = (this.altBookType as BookType | undefined) ?? book.primaryFile?.bookType;
        if (!bookType) {
          return throwError(() => new Error('Book type not found'));
        }

        // Determine which file ID to use for progress tracking
        let bookFileId: number | undefined;
        if (this.altBookType) {
          // Look for the alternative format file with matching type
          const altFile = book.alternativeFormats?.find(f => f.bookType === this.altBookType);
          bookFileId = altFile?.id;
        } else {
          // Use the primary file
          bookFileId = book.primaryFile?.id;
        }

        return this.stateService.initializeState(this.bookId, bookFileId!).pipe(
          map(() => ({book, bookType, bookFileId}))
        );
      }),
      switchMap(({book, bookType, bookFileId}) => {
        this.progressService.initialize(this.bookId, bookType, bookFileId);
        this.selectionService.initialize(this.bookId, this.destroy$);
        this.headerService.initialize(this.bookId, book.metadata?.title || '');

        // Use streaming for EPUB if query param is set, blob loading otherwise (default)
        const useStreaming = this.route.snapshot.queryParamMap.get('streaming') === 'true';
        const loadBook$ = bookType === 'EPUB' && useStreaming
          ? this.viewManager.loadEpubStreaming(this.bookId, this.altBookType)
          : this.loadBookBlob();

        return loadBook$.pipe(
          tap(() => {
            this.applyStyles();
            this.sidebarService.initialize(this.bookId, book, this.destroy$);
            this.leftSidebarService.initialize(this.bookId, this.destroy$);
            this.noteService.initialize(this.bookId, this.destroy$);
          }),
          switchMap(() => this.viewManager.getMetadata()),
          switchMap(() => {
            if (!this.hasLoadedOnce) {
              this.hasLoadedOnce = true;
              // Navigate to saved position if progress exists, otherwise go to first page
              if (book.epubProgress?.cfi) {
                return this.viewManager.goTo(book.epubProgress.cfi);
              } else if (book.epubProgress?.percentage && book.epubProgress.percentage > 0) {
                return this.viewManager.goToFraction(book.epubProgress.percentage / 100);
              } else {
                return this.viewManager.goTo(0);
              }
            }
            return of(undefined);
          })
        );
      })
    );
  }

  private loadBookBlob(): Observable<void> {
    return this.bookFileService.getFileContent(this.bookId, this.altBookType).pipe(
      switchMap(fileBlob => {
        const fileUrl = URL.createObjectURL(fileBlob);
        this._fileUrl = fileUrl;
        return this.viewManager.loadEpub(fileUrl);
      })
    );
  }

  private subscribeToViewEvents(): void {
    this.viewManager.events$
      .pipe(takeUntil(this.destroy$))
      .subscribe(event => {
        switch (event.type) {
          case 'load':
            this.applyStyles();
            this.sidebarService.updateChapters();
            this.updateSectionFractions();
            break;
          case 'relocate':
            if (this.relocateTimeout) clearTimeout(this.relocateTimeout);
            this.relocateTimeout = setTimeout(() => {
              this.progressService.handleRelocateEvent(event.detail);
              this.updateBookmarkIndicator();
            }, 100);

            if (this.sectionFractionsTimeout) clearTimeout(this.sectionFractionsTimeout);
            this.sectionFractionsTimeout = setTimeout(() => {
              this.updateSectionFractions();
            }, 500);
            break;
          case 'middle-single-tap':
            if (this.immersiveMode) {
              this.immersiveTemporaryShow();
            } else {
              this.toggleHeaderNavbarPinned();
            }
            break;
          case 'text-selected':
            this.selectionService.handleTextSelected(event.detail, event.popupPosition);
            break;
          case 'toggle-fullscreen':
            this.toggleFullscreen();
            break;
          case 'toggle-shortcuts-help':
            this.showShortcutsHelp = !this.showShortcutsHelp;
            break;
          case 'toggle-immersive':
            this.toggleImmersiveMode();
            break;
          case 'go-first-section':
            this.viewManager.goToSection(0).subscribe();
            break;
          case 'go-last-section': {
            const s = this.progressService.currentProgressData?.section;
            if (s && s.total > 0) {
              this.viewManager.goToSection(s.total - 1).subscribe();
            }
            break;
          }
          case 'toggle-toc':
            this.sidebarService.toggle('chapters');
            break;
          case 'toggle-search':
            this.leftSidebarService.toggle('search');
            break;
          case 'toggle-notes':
            this.leftSidebarService.toggle('notes');
            break;
          case 'escape-pressed':
            if (this.showShortcutsHelp) {
              this.showShortcutsHelp = false;
            } else if (this.noteDialogState().visible) {
              this.noteService.closeDialog();
            } else if (this.showControls) {
              this.showControls = false;
            } else if (this.showQuickSettings) {
              this.showQuickSettings = false;
            } else if (this.showMetadata) {
              this.showMetadata = false;
            } else if (this.isFullscreen) {
              this.exitFullscreen();
            }
            break;
        }
      });
  }

  private updateSectionFractions(): void {
    this.sectionFractions = this.viewManager.getSectionFractions();
  }

  private updateBookmarkIndicator(): void {
    const currentCfi = this.progressService.currentCfi;
    const isBookmarked = currentCfi
      ? this.sidebarService.bookmarks().some(bookmark => bookmark.cfi === currentCfi)
      : false;
    this.headerService.setCurrentCfiBookmarked(isBookmarked);
  }

  private applyStyles(): void {
    const renderer = this.viewManager.getRenderer();
    if (renderer) {
      const state = this.stateService.state();
      this.styleService.applyStylesToRenderer(renderer, state);
      if (state.flow) {
        renderer.setAttribute?.('flow', state.flow);
      }
    }
  }

  @HostListener('document:fullscreenchange')
  onFullscreenChange(): void {
    this.isFullscreen = !!document.fullscreenElement;
    this.headerService.setFullscreen(this.isFullscreen);
  }

  toggleFullscreen(): void {
    if (document.fullscreenElement) {
      this.exitFullscreen();
    } else {
      this.enterFullscreen();
    }
  }

  private enterFullscreen(): void {
    document.documentElement.requestFullscreen?.();
  }

  private exitFullscreen(): void {
    document.exitFullscreen?.();
  }

  onProgressChange(fraction: number): void {
    this.viewManager.goToFraction(fraction)
      .pipe(takeUntil(this.destroy$))
      .subscribe();
  }

  private toggleHeaderNavbarPinned(): void {
    this.visibilityManager.togglePinned();
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    this.visibilityManager.handleMouseMove(event.clientY);
  }

  @HostListener('document:mouseleave')
  onMouseLeave(): void {
    this.visibilityManager.handleMouseLeave();
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.visibilityManager.updateWindowHeight(window.innerHeight);
  }

  onHeaderTriggerZoneEnter(): void {
    this.visibilityManager.handleHeaderZoneEnter();
  }

  onFooterTriggerZoneEnter(): void {
    this.visibilityManager.handleFooterZoneEnter();
  }

  toggleImmersiveMode(): void {
    this.immersiveMode = !this.immersiveMode;
    if (this.immersiveMode) {
      this.visibilityManager.setImmersive(true);
    } else {
      this.visibilityManager.setImmersive(false);
    }
  }

  private immersiveTemporaryShow(): void {
    if (!this.immersiveMode) return;
    this.visibilityManager.temporaryShow();
    if (this.immersiveAutoHideTimer) clearTimeout(this.immersiveAutoHideTimer);
    this.immersiveAutoHideTimer = setTimeout(() => {
      this.visibilityManager.hideTemporary();
    }, 3000);
  }

  handleSelectionAction(action: TextSelectionAction): void {
    if (action.type === 'note') {
      this.noteService.openNewNoteDialog();
    } else {
      this.selectionService.handleAction(action);
    }
  }

  onNoteSave(result: NoteDialogResult): void {
    this.noteService.saveNote(result);
  }

  onNoteCancel(): void {
    this.noteService.closeDialog();
  }
}
