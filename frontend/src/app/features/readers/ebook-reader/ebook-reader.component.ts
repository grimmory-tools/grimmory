import {ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, DestroyRef, effect, HostListener, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {forkJoin, from, Observable, of, throwError} from 'rxjs';
import {catchError, map, switchMap, tap} from 'rxjs/operators';
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
import {AdditionalFile, Book, BookType} from '../../book/model/book.model';
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
import {ViewEvent} from './core/view-manager.service';

interface PendingInitialChapterRestore {
  href: string;
  contentSourceProgressPercent: number;
  attempts: number;
}

@Component({
  selector: 'app-ebook-reader',
  standalone: true,
  imports: [
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
  styleUrls: ['./ebook-reader.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EbookReaderComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private static readonly MAX_CHAPTER_PROGRESS_PERCENT = 99.9;
  private static readonly INITIAL_CHAPTER_RESTORE_RETRY_MS = 100;
  private static readonly INITIAL_CHAPTER_RESTORE_MAX_ATTEMPTS = 15;
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
  private messageService = inject(MessageService);

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
  private pendingInitialChapterRestore: PendingInitialChapterRestore | null = null;
  private pendingInitialChapterRestoreTimeout?: ReturnType<typeof setTimeout>;

  isLoading = signal(true);
  showQuickSettings = signal(false);
  showControls = signal(false);
  showMetadata = signal(false);
  forceNavbarVisible = signal(false);
  headerVisible = signal(false);
  book = signal<Book | null>(null);
  sectionFractions = signal<number[]>([]);
  isFullscreen = signal(false);
  showShortcutsHelp = signal(false);
  immersiveMode = signal(false);
  private immersiveAutoHideTimer?: ReturnType<typeof setTimeout>;
  protected progressData = signal<RelocateProgressData | null>(null);

  readonly readerState = this.stateService.state;
  readonly selectionState = this.selectionService.state;
  readonly noteDialogState = this.noteService.dialogState;
  readonly isCurrentCfiBookmarked = this.headerService.isCurrentCfiBookmarked;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.wakeLockService.disable();
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

      if (this.immersiveAutoHideTimer) clearTimeout(this.immersiveAutoHideTimer);
      if (this.relocateTimeout) clearTimeout(this.relocateTimeout);
      if (this.sectionFractionsTimeout) clearTimeout(this.sectionFractionsTimeout);
      if (this.pendingInitialChapterRestoreTimeout) clearTimeout(this.pendingInitialChapterRestoreTimeout);

      if (this._fileUrl) {
        URL.revokeObjectURL(this._fileUrl);
        this._fileUrl = null;
      }
    });

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

  ngOnInit() {
    this.visibilityManager = new ReaderHeaderFooterVisibilityManager(window.innerHeight);
    this.visibilityManager.onStateChange((state) => {
      this.headerVisible.set(state.headerVisible);
      this.headerService.setForceVisible(state.headerVisible);
      this.forceNavbarVisible.set(state.footerVisible);
    });

    this.sidebarService.showMetadata$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.showMetadata.set(true));

    this.headerService.showControls$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.showQuickSettings.set(true));

    this.headerService.showMetadata$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.showMetadata.set(true));

    this.headerService.toggleFullscreen$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.toggleFullscreen());

    this.headerService.showShortcutsHelp$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.showShortcutsHelp.set(true));

    // Enable wake lock after a short delay
    setTimeout(() => this.wakeLockService.enable(), 1000);

    this.isLoading.set(true);

    this.bookId = +this.route.snapshot.paramMap.get('bookId')!;
    this.altBookType = this.route.snapshot.queryParamMap.get('bookType') ?? undefined;

    // Parallelize Foliate script loading and initial book detail fetch
    forkJoin([
      this.initializeFoliate(),
      from(this.bookService.fetchFreshBookDetail(this.bookId, false))
    ]).pipe(
      switchMap(([, book]) => {
        this.book.set(book);
        const bookType = (this.altBookType as BookType | undefined) ?? book.primaryFile?.bookType;
        if (!bookType) {
          return throwError(() => new Error('Book type not found'));
        }

        let bookFileId: number | undefined;
        if (this.altBookType) {
          const altFile = book.alternativeFormats?.find((f: AdditionalFile) => f.bookType === this.altBookType);
          bookFileId = altFile?.id;
        } else {
          bookFileId = book.primaryFile?.id;
        }

        // Parallelize font loading/view setup and state initialization
        return forkJoin([
          this.epubCustomFontService.loadAndCacheFonts().pipe(
            tap(() => this.stateService.refreshCustomFonts())
          ),
          this.setupView().pipe(
            tap(() => this.subscribeToViewEvents())
          ),
          this.stateService.initializeState(this.bookId, bookFileId!).pipe(
            map(() => ({book, bookType, bookFileId}))
          )
        ]);
      }),
      switchMap(([, , {book, bookType, bookFileId}]) => {
        this.progressService.initialize(this.bookId, bookType, bookFileId);
        this.selectionService.initialize(this.bookId);
        this.headerService.initialize(this.bookId, book.metadata?.title || '');

        const useStreaming = this.route.snapshot.queryParamMap.get('streaming') === 'true';
        const loadBook$ = bookType === 'EPUB' && useStreaming
          ? this.viewManager.loadEpubStreaming(this.bookId, this.altBookType)
          : this.loadBookBlob();

        return loadBook$.pipe(
          tap(() => {
            this.applyStyles();
            this.sidebarService.initialize(this.bookId, book);
            this.leftSidebarService.initialize(this.bookId);
            this.noteService.initialize(this.bookId);
          }),
          switchMap(() => this.viewManager.getMetadata()),
          switchMap(() => {
            if (!this.hasLoadedOnce) {
              this.hasLoadedOnce = true;
              return this.restoreSavedPosition(book);
            }
            return of(undefined);
          })
        );
      }),
      tap(() => this.isLoading.set(false)),
      catchError((err) => {
        console.error('Failed to load ebook', err);
        this.isLoading.set(false);
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load book'});
        return of(null);
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe();
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
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event: ViewEvent) => {
        switch (event.type) {
          case 'load':
            this.applyStyles();
            this.sidebarService.updateChapters();
            this.updateSectionFractions();
            break;
          case 'relocate':
            if (this.handlePendingInitialChapterRestore(event.detail)) {
              break;
            }

            if (this.relocateTimeout) clearTimeout(this.relocateTimeout);
            this.relocateTimeout = setTimeout(() => {
              this.handleRelocateProgress(event.detail);
            }, 100);

            if (this.sectionFractionsTimeout) clearTimeout(this.sectionFractionsTimeout);
            this.sectionFractionsTimeout = setTimeout(() => {
              this.updateSectionFractions();
            }, 500);
            break;
          case 'middle-single-tap':
            if (this.immersiveMode()) {
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
            this.showShortcutsHelp.update(v => !v);
            break;
          case 'toggle-immersive':
            this.toggleImmersiveMode();
            break;
          case 'go-first-section':
            this.viewManager.goToSection(0).pipe(catchError(() => of(undefined))).subscribe();
            break;
          case 'go-last-section': {
            const s = this.progressService.currentProgressData?.section;
            if (s && s.total > 0) {
              this.viewManager.goToSection(s.total - 1).pipe(catchError(() => of(undefined))).subscribe();
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
            if (this.showShortcutsHelp()) {
              this.showShortcutsHelp.set(false);
            } else if (this.noteDialogState().visible) {
              this.noteService.closeDialog();
            } else if (this.showControls()) {
              this.showControls.set(false);
            } else if (this.showQuickSettings()) {
              this.showQuickSettings.set(false);
            } else if (this.showMetadata()) {
              this.showMetadata.set(false);
            } else if (this.isFullscreen()) {
              this.exitFullscreen();
            }
            break;
        }
      });
  }

  private updateSectionFractions(): void {
    this.sectionFractions.set(this.viewManager.getSectionFractions());
  }

  private restoreSavedPosition(book: Book): Observable<void> {
    this.pendingInitialChapterRestore = null;

    const progress = book.epubProgress;

    if (progress?.cfi) {
      return this.viewManager.goTo(progress.cfi);
    }

    if (progress?.href) {
      const chapterProgress = progress.contentSourceProgressPercent;
      if (typeof chapterProgress === 'number' && Number.isFinite(chapterProgress) && chapterProgress > 0) {
        this.pendingInitialChapterRestore = {
          href: this.normalizeHref(progress.href),
          contentSourceProgressPercent: chapterProgress,
          attempts: 0
        };
      }
      return this.viewManager.goTo(progress.href);
    }

    if (progress?.percentage && progress.percentage > 0) {
      return this.viewManager.goToFraction(progress.percentage / 100);
    }

    return this.viewManager.goTo(0);
  }

  private handlePendingInitialChapterRestore(detail: RelocateProgressData): boolean {
    const pendingRestore = this.pendingInitialChapterRestore;
    if (!pendingRestore) {
      return false;
    }

    const currentHref = this.normalizeHref(detail.pageItem?.href ?? detail.tocItem?.href ?? null);
    if (!currentHref) {
      return this.retryInitialRestore(detail);
    }
    if (!this.hrefsMatch(currentHref, pendingRestore.href)) {
      return this.retryInitialRestore(detail);
    }

    const targetFraction = this.resolveChapterFraction(
      detail.section?.current,
      pendingRestore.contentSourceProgressPercent
    );

    if (targetFraction === null) {
      return this.retryInitialRestore(detail);
    }

    if (typeof detail.fraction === 'number' && Math.abs(detail.fraction - targetFraction) < 0.0001) {
      this.pendingInitialChapterRestore = null;
      return false;
    }

    this.pendingInitialChapterRestore = null;
    if (this.pendingInitialChapterRestoreTimeout) {
      clearTimeout(this.pendingInitialChapterRestoreTimeout);
      this.pendingInitialChapterRestoreTimeout = undefined;
    }
    this.viewManager.goToFraction(targetFraction)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe();
    return true;
  }

  private retryInitialRestore(detail: RelocateProgressData): boolean {
    const pendingRestore = this.pendingInitialChapterRestore;
    if (!pendingRestore) {
      return false;
    }

    if (pendingRestore.attempts >= EbookReaderComponent.INITIAL_CHAPTER_RESTORE_MAX_ATTEMPTS) {
      this.pendingInitialChapterRestore = null;
      this.handleRelocateProgress(detail);
      return true;
    }

    pendingRestore.attempts += 1;
    if (this.pendingInitialChapterRestoreTimeout) clearTimeout(this.pendingInitialChapterRestoreTimeout);
    this.pendingInitialChapterRestoreTimeout = setTimeout(() => {
      this.updateSectionFractions();
      this.handlePendingInitialChapterRestore(detail);
    }, EbookReaderComponent.INITIAL_CHAPTER_RESTORE_RETRY_MS);
    return true;
  }

  private handleRelocateProgress(detail: RelocateProgressData): void {
    this.progressService.handleRelocateEvent(detail);
    this.progressData.set(this.progressService.currentProgressData);
    this.updateBookmarkIndicator();
  }

  private resolveChapterFraction(sectionIndex: number | undefined, chapterProgressPercent: number): number | null {
    if (sectionIndex === undefined) {
      return null;
    }

    const sectionFractions = this.viewManager.getSectionFractions();
    const start = sectionFractions[sectionIndex];
    const end = sectionFractions[sectionIndex + 1];

    if (start === undefined || end === undefined || end <= start) {
      return null;
    }

    const normalizedProgress = Math.min(
      Math.max(chapterProgressPercent, 0),
      EbookReaderComponent.MAX_CHAPTER_PROGRESS_PERCENT
    ) / 100;

    return start + ((end - start) * normalizedProgress);
  }

  private normalizeHref(href: string | null | undefined): string {
    return (href ?? '').split('#')[0].replace(/^(\.\/|\/)+/, '');
  }

  private hrefsMatch(leftHref: string, rightHref: string): boolean {
    return leftHref === rightHref
      || leftHref.endsWith(`/${rightHref}`)
      || rightHref.endsWith(`/${leftHref}`);
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
    this.isFullscreen.set(!!document.fullscreenElement);
    this.headerService.setFullscreen(this.isFullscreen());
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
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => of(undefined))
      )
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
    const newValue = !this.immersiveMode();
    this.immersiveMode.set(newValue);
    this.visibilityManager.setImmersive(newValue);
  }

  private immersiveTemporaryShow(): void {
    if (!this.immersiveMode()) return;
    this.visibilityManager.temporaryShow();
    if (this.immersiveAutoHideTimer) clearTimeout(this.immersiveAutoHideTimer);
    this.immersiveAutoHideTimer = setTimeout(() => {
      this.visibilityManager.hideTemporary();
    }, 3000);
  }

  handleSelectionAction(action: TextSelectionAction): void {
    if (action.type === 'note') {
      this.noteService.openNewNoteDialog();
    } else if (action.type === 'go-to-link') {
      const linkUrl = this.selectionState().linkUrl;
      if (linkUrl) {
        this.viewManager.goTo(linkUrl)
          .pipe(
            takeUntilDestroyed(this.destroyRef),
            catchError(() => of(undefined))
          )
          .subscribe();
      }
      this.selectionService.handleAction(action);
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
