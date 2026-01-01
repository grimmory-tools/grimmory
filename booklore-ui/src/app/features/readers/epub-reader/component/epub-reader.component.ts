import {Component, ElementRef, inject, NgZone, OnDestroy, OnInit, ViewChild} from '@angular/core';
import ePub from 'epubjs';
import {Drawer} from 'primeng/drawer';
import {forkJoin, Subscription} from 'rxjs';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {CommonModule, Location} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {Book, BookSetting} from '../../../book/model/book.model';
import {BookService} from '../../../book/service/book.service';
import {Select} from 'primeng/select';
import {UserService} from '../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {MessageService, PrimeTemplate} from 'primeng/api';
import {BookMark, BookMarkService, UpdateBookMarkRequest} from '../../../../shared/service/book-mark.service';
import {Tooltip} from 'primeng/tooltip';
import {Slider} from 'primeng/slider';
import {FALLBACK_EPUB_SETTINGS, getChapter} from '../epub-reader-helper';
import {ReadingSessionService} from '../../../../shared/service/reading-session.service';
import {EpubTheme, EpubThemeUtil} from '../epub-theme-util';
import {PageTitleService} from "../../../../shared/service/page-title.service";
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {BookmarkEditDialogComponent} from './bookmark-edit-dialog.component';
import {BookmarkViewDialogComponent} from './bookmark-view-dialog.component';

@Component({
  selector: 'app-epub-reader',
  templateUrl: './epub-reader.component.html',
  styleUrls: ['./epub-reader.component.scss'],
  imports: [CommonModule, FormsModule, Drawer, Button, Select, ProgressSpinner, Tooltip, Slider, PrimeTemplate, Tabs, TabList, Tab, TabPanels, TabPanel, IconField, InputIcon, BookmarkEditDialogComponent, BookmarkViewDialogComponent, InputText],
  standalone: true
})
export class EpubReaderComponent implements OnInit, OnDestroy {
  @ViewChild('epubContainer', {static: false}) epubContainer!: ElementRef;

  isLoading = true;
  chapters: { label: string; href: string; level: number }[] = [];
  currentChapter = '';
  currentChapterHref: string | null = null;
  isDrawerVisible = false;
  isSettingsDrawerVisible = false;
  bookmarks: BookMark[] = [];
  currentCfi: string | null = null;
  isBookmarked = false;
  isAddingBookmark = false;
  isDeletingBookmark = false;
  isEditingBookmark = false;
  private routeSubscription?: Subscription;

  filterText = '';
  viewDialogVisible = false;
  selectedBookmark: BookMark | null = null;

  public locationsReady = false;
  public approxProgress = 0;
  public exactProgress = 0;
  public progressPercentage = 0;

  showControls = !this.isMobileDevice();
  showHeader = true;
  private hideControlsTimeout?: number;
  private hideHeaderTimeout?: number;
  private isMouseInTopRegion = false;
  private headerShownByMobileTouch = false;
  private currentIframe: HTMLIFrameElement | null = null;
  private iframeTouchHandler: ((event: TouchEvent) => void) | null = null;
  private iframeClickHandler: ((event: MouseEvent) => void) | null = null;

  editingBookmark: BookMark | null = null;
  showEditBookmarkDialog = false;

  private book: any;
  private rendition: any;
  private keyListener: (e: KeyboardEvent) => void = () => {
  };

  fontSize?: number = 100;
  selectedFlow?: string = 'paginated';
  selectedTheme?: string = 'white';
  selectedFontType?: string | null = null;
  selectedSpread?: string = 'double';
  lineHeight?: number;
  letterSpacing?: number;

  fontTypes: any[] = [
    {label: "Publisher's Default", value: null},
    {label: 'Serif', value: 'serif'},
    {label: 'Sans Serif', value: 'sans-serif'},
    {label: 'Roboto', value: 'roboto'},
    {label: 'Cursive', value: 'cursive'},
    {label: 'Monospace', value: 'monospace'},
  ];

  themes: any[] = [
    {label: 'White', value: EpubTheme.WHITE},
    {label: 'Black', value: EpubTheme.BLACK},
    {label: 'Grey', value: EpubTheme.GREY},
    {label: 'Sepia', value: EpubTheme.SEPIA},
    {label: 'Green', value: EpubTheme.GREEN},
    {label: 'Lavender', value: EpubTheme.LAVENDER},
    {label: 'Cream', value: EpubTheme.CREAM},
    {label: 'Light Blue', value: EpubTheme.LIGHT_BLUE},
    {label: 'Peach', value: EpubTheme.PEACH},
    {label: 'Mint', value: EpubTheme.MINT},
    {label: 'Dark Slate', value: EpubTheme.DARK_SLATE},
    {label: 'Dark Olive', value: EpubTheme.DARK_OLIVE},
    {label: 'Dark Purple', value: EpubTheme.DARK_PURPLE},
    {label: 'Dark Teal', value: EpubTheme.DARK_TEAL},
    {label: 'Dark Brown', value: EpubTheme.DARK_BROWN},
  ];

  private route = inject(ActivatedRoute);
  private location = inject(Location);
  private userService = inject(UserService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);
  private ngZone = inject(NgZone);
  private pageTitle = inject(PageTitleService);
  private bookMarkService = inject(BookMarkService);
  private readingSessionService = inject(ReadingSessionService);

  epub!: Book;

  ngOnInit(): void {
    this.routeSubscription = this.route.paramMap.subscribe((params) => {
      this.isLoading = true;
      const bookId = +params.get('bookId')!;

      const myself$ = this.userService.getMyself();
      const epub$ = this.bookService.getBookByIdFromAPI(bookId, false);
      const epubData$ = this.bookService.getFileContent(bookId);
      const bookSetting$ = this.bookService.getBookSetting(bookId);
      const bookmarks$ = this.bookMarkService.getBookmarksForBook(bookId);

      forkJoin([myself$, epub$, epubData$, bookSetting$, bookmarks$]).subscribe({
        next: ([myself, epub, epubData, bookSetting, bookmarks]) => {
          this.epub = epub;
          this.bookmarks = bookmarks;
          this.updateBookmarkStatus();
          const individualSetting = bookSetting?.epubSettings;
          const fileReader = new FileReader();

          this.pageTitle.setBookPageTitle(epub);

          fileReader.onload = () => {
            this.book = ePub(fileReader.result as ArrayBuffer);

            this.book.loaded.navigation.then((nav: any) => {
              this.chapters = this.extractChapters(nav.toc, 0);
            });

            const settingScope = myself.userSettings.perBookSetting.epub;
            const globalSettings = myself.userSettings.epubReaderSetting;

            const resolvedFlow = settingScope === 'Global' ? globalSettings.flow : individualSetting?.flow;
            const resolvedFontSize = settingScope === 'Global' ? globalSettings.fontSize : individualSetting?.fontSize;
            const resolvedFontFamily = settingScope === 'Global' ? globalSettings.font : individualSetting?.font;
            const resolvedTheme = settingScope === 'Global' ? globalSettings.theme : individualSetting?.theme;
            const resolvedLineHeight = settingScope === 'Global' ? globalSettings.lineHeight : individualSetting?.lineHeight;
            const resolvedLetterSpacing = settingScope === 'Global' ? globalSettings.letterSpacing : individualSetting?.letterSpacing;
            const resolvedSpread = settingScope === 'Global' ? globalSettings.spread || 'double' : individualSetting?.spread || 'double';

            if (resolvedTheme != null) this.selectedTheme = resolvedTheme;
            if (resolvedFontFamily != null) this.selectedFontType = resolvedFontFamily;
            if (resolvedFontSize != null) this.fontSize = resolvedFontSize;
            if (resolvedLineHeight != null) this.lineHeight = resolvedLineHeight;
            if (resolvedLetterSpacing != null) this.letterSpacing = resolvedLetterSpacing;
            if (resolvedFlow != null) this.selectedFlow = resolvedFlow;
            if (resolvedSpread != null) this.selectedSpread = resolvedSpread;

            this.rendition = this.book.renderTo(this.epubContainer.nativeElement, {
              flow: this.selectedFlow ?? 'paginated',
              manager: this.selectedFlow === 'scrolled' ? 'continuous' : 'default',
              width: '100%',
              height: '100%',
              spread: this.selectedFlow === 'paginated' && !this.isMobileDevice() ? (this.selectedSpread === 'single' ? 'none' : this.selectedSpread) : 'none',
              allowScriptedContent: true,
            });

            const baseTheme = EpubThemeUtil.themesMap.get(this.selectedTheme ?? 'black') || {};
            const combinedTheme = {
              ...baseTheme,
              body: {
                ...baseTheme.body,
                ...(this.selectedFontType ? {'font-family': this.selectedFontType} : {}),
                ...(this.lineHeight != null ? {'line-height': this.lineHeight} : {}),
                ...(this.letterSpacing != null ? {'letter-spacing': `${this.letterSpacing}em`} : {}),
              },
              '*': {
                ...baseTheme['*'],
                ...(this.lineHeight != null ? {'line-height': this.lineHeight} : {}),
                ...(this.letterSpacing != null ? {'letter-spacing': `${this.letterSpacing}em`} : {}),
              },
            };

            this.rendition.themes.override('font-size', `${this.fontSize}%`);
            this.rendition.themes.register('custom', combinedTheme);
            this.rendition.themes.select('custom');

            const displayPromise = this.epub?.epubProgress?.cfi
              ? this.rendition.display(this.epub.epubProgress.cfi)
              : this.rendition.display();

            displayPromise.then(() => {
              this.updateCurrentChapter(this.rendition.currentLocation());
              this.setupKeyListener();
              this.trackProgress();
              this.setupTouchListener();
              this.isLoading = false;
              this.startHeaderAutoHide();
            });

          };

          fileReader.readAsArrayBuffer(epubData);
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to load the book',
          });
          this.isLoading = false;
        },
      });
    });
  }

  get filteredBookmarks(): BookMark[] {
    let filtered = this.bookmarks;

    if (this.filterText && this.filterText.trim()) {
      const lowerFilter = this.filterText.toLowerCase().trim();
      filtered = filtered.filter(b =>
        (b.title && b.title.toLowerCase().includes(lowerFilter)) ||
        (b.notes && b.notes.toLowerCase().includes(lowerFilter))
      );
    }

    return [...filtered].sort((a, b) => {
      const priorityA = a.priority ?? 3;
      const priorityB = b.priority ?? 3;

      if (priorityA !== priorityB) {
        return priorityA - priorityB;
      }

      const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0;

      return dateB - dateA;
    });
  }

  openViewDialog(bookmark: BookMark): void {
    this.selectedBookmark = bookmark;
    this.viewDialogVisible = true;
  }

  updateThemeStyle(): void {
    this.applyCombinedTheme();
    this.updateViewerSetting();
  }

  changeScrollMode(): void {
    if (!this.rendition || !this.book) return;

    const cfi = this.rendition.currentLocation()?.start?.cfi;
    this.rendition.destroy();

    this.rendition = this.book.renderTo(this.epubContainer.nativeElement, {
      flow: this.selectedFlow,
      manager: this.selectedFlow === 'scrolled' ? 'continuous' : 'default',
      width: '100%',
      height: '100%',
      spread: this.selectedFlow === 'paginated' && !this.isMobileDevice() ? (this.selectedSpread === 'single' ? 'none' : this.selectedSpread) : 'none',
      allowScriptedContent: true,
    });

    this.rendition.themes.override('font-size', `${this.fontSize}%`);
    this.applyCombinedTheme();
    this.setupKeyListener();
    this.rendition.display(cfi || undefined);
    this.updateViewerSetting();
  }

  changeSpreadMode(): void {
    if (!this.rendition || !this.book || this.selectedFlow === 'scrolled' || this.isMobileDevice()) return;

    const cfi = this.rendition.currentLocation()?.start?.cfi;
    this.rendition.destroy();

    this.rendition = this.book.renderTo(this.epubContainer.nativeElement, {
      flow: this.selectedFlow,
      manager: 'default',
      width: '100%',
      height: '100%',
      spread: this.selectedSpread === 'single' ? 'none' : this.selectedSpread,
      allowScriptedContent: true,
    });

    this.rendition.themes.override('font-size', `${this.fontSize}%`);
    this.applyCombinedTheme();
    this.setupKeyListener();
    this.rendition.display(cfi || undefined);
    this.updateViewerSetting();
  }

  changeThemes(): void {
    this.applyCombinedTheme();
    this.updateViewerSetting();
  }

  private applyCombinedTheme(): void {
    EpubThemeUtil.applyTheme(
      this.rendition,
      this.selectedTheme ?? 'white',
      this.selectedFontType ?? undefined,
      this.fontSize,
      this.lineHeight,
      this.letterSpacing
    );
  }

  updateFontSize(): void {
    if (this.rendition) {
      this.rendition.themes.override('font-size', `${this.fontSize}%`);
      this.updateViewerSetting();
    }
  }

  changeFontType(): void {
    if (this.rendition) {
      if (this.selectedFontType) {
        this.rendition.themes.font(this.selectedFontType);
      } else {
        this.rendition.themes.font('');
      }
      this.updateViewerSetting();
    }
  }

  increaseFontSize(): void {
    this.fontSize = Math.min(Number(this.fontSize) + 10, FALLBACK_EPUB_SETTINGS.maxFontSize);
    this.updateFontSize();
  }

  decreaseFontSize(): void {
    this.fontSize = Math.max(Number(this.fontSize) - 10, FALLBACK_EPUB_SETTINGS.minFontSize);
    this.updateFontSize();
  }

  private updateViewerSetting(): void {
    const epubSettings: any = {};

    if (this.selectedTheme) epubSettings.theme = this.selectedTheme;
    if (this.selectedFontType) epubSettings.font = this.selectedFontType;
    if (this.fontSize) epubSettings.fontSize = this.fontSize;
    if (this.selectedFlow) epubSettings.flow = this.selectedFlow;
    if (this.selectedSpread === 'single' || this.selectedSpread === 'double') epubSettings.spread = this.selectedSpread;
    if (this.lineHeight) epubSettings.lineHeight = this.lineHeight;
    if (this.letterSpacing) epubSettings.letterSpacing = this.letterSpacing;

    const bookSetting: BookSetting = {
      epubSettings: epubSettings
    };

    this.bookService.updateViewerSetting(bookSetting, this.epub.id).subscribe();
  }

  private setupKeyListener(): void {
    this.keyListener = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowLeft':
          this.prevPage();
          break;
        case 'ArrowRight':
          this.nextPage();
          break;
        default:
          break;
      }
    };
    if (this.rendition) {
      this.rendition.on('keyup', this.keyListener);
    }
    document.addEventListener('keyup', this.keyListener);
  }

  private setupTouchListener(): void {
    if (!this.isMobileDevice()) return;

    const container = this.epubContainer.nativeElement;

    container.addEventListener('touchend', (event: TouchEvent) => {
      event.stopPropagation();
      this.ngZone.run(() => {
        this.handleTouchEnd(event);
      });
    }, { passive: false });

    container.addEventListener('click', (event: MouseEvent) => {
      event.stopPropagation();
      this.ngZone.run(() => {
        this.handleClickAsTap(event);
      });
    }, { capture: true });

    this.rendition.on('rendered', () => {
      const iframe = this.epubContainer.nativeElement.querySelector('iframe');

      if (iframe && iframe !== this.currentIframe) {
        if (this.currentIframe && this.currentIframe.contentDocument) {
          if (this.iframeTouchHandler) {
            this.currentIframe.contentDocument.removeEventListener('touchend', this.iframeTouchHandler, true);
          }
          if (this.iframeClickHandler) {
            this.currentIframe.contentDocument.removeEventListener('click', this.iframeClickHandler, true);
          }
        }

        this.currentIframe = iframe;

        if (iframe.contentDocument) {
          this.iframeTouchHandler = (event: TouchEvent) => {
            event.stopPropagation();
            event.preventDefault();
            this.ngZone.run(() => {
              this.handleTouchEnd(event);
            });
          };

          this.iframeClickHandler = (event: MouseEvent) => {
            event.stopPropagation();
            event.preventDefault();
            this.ngZone.run(() => {
              this.handleClickAsTap(event);
            });
          };

          iframe.contentDocument.addEventListener('touchend', this.iframeTouchHandler, { passive: false, capture: true });
          iframe.contentDocument.addEventListener('click', this.iframeClickHandler, { capture: true });
        }
      }
    });
  }

  public handleTouchEnd(event: TouchEvent): void {
    if (!this.isMobileDevice()) return;

    const touch = event.changedTouches[0];
    let touchX = touch.clientX;
    let touchY = touch.clientY;

    if (event.target && (event.target as HTMLElement).ownerDocument !== document) {
      const iframe = this.currentIframe;
      if (iframe) {
        const iframeRect = iframe.getBoundingClientRect();
        touchX = touch.clientX + iframeRect.left;
        touchY = touch.clientY + iframeRect.top;
      }
    }

    const screenWidth = window.innerWidth;
    const screenHeight = window.innerHeight;

    const isTopTouch = touchY < screenHeight * 0.2;
    const isInTapZoneVertically = touchY >= screenHeight * 0.2;

    if (isTopTouch) {
      if (!this.showHeader) {
        this.showHeader = true;
        this.headerShownByMobileTouch = true;
        this.clearHeaderTimeout();
        this.startHeaderAutoHide();
      } else if (this.headerShownByMobileTouch) {
        this.showHeader = false;
        this.headerShownByMobileTouch = false;
        this.clearHeaderTimeout();
      }
      return;
    }

    if (isInTapZoneVertically) {
      const leftZoneEnd = screenWidth / 3;
      const rightZoneStart = screenWidth * (2 / 3);

      if (touchX < leftZoneEnd) {
        this.prevPage();
        return;
      }

      if (touchX > rightZoneStart) {
        this.nextPage();
        return;
      }

      this.showControlsTemporarily();
    }
  }

  private handleClickAsTap(event: MouseEvent): void {
    if (!this.isMobileDevice()) return;

    let clickX = event.clientX;
    let clickY = event.clientY;

    if (event.target && (event.target as HTMLElement).ownerDocument !== document) {
      const iframe = this.currentIframe;
      if (iframe) {
        const iframeRect = iframe.getBoundingClientRect();
        clickX = event.clientX + iframeRect.left;
        clickY = event.clientY + iframeRect.top;
      }
    }

    const screenWidth = window.innerWidth;
    const screenHeight = window.innerHeight;

    const isTopClick = clickY < screenHeight * 0.2;
    const isInTapZoneVertically = clickY >= screenHeight * 0.2;

    if (isTopClick) {
      if (!this.showHeader) {
        this.showHeader = true;
        this.headerShownByMobileTouch = true;
        this.clearHeaderTimeout();
        this.startHeaderAutoHide();
      } else if (this.headerShownByMobileTouch) {
        this.showHeader = false;
        this.headerShownByMobileTouch = false;
        this.clearHeaderTimeout();
      }
      return;
    }

    if (isInTapZoneVertically) {
      const leftZoneEnd = screenWidth / 3;
      const rightZoneStart = screenWidth * (2 / 3);

      if (clickX < leftZoneEnd) {
        this.prevPage();
        return;
      }

      if (clickX > rightZoneStart) {
        this.nextPage();
        return;
      }

      this.showControlsTemporarily();
    }
  }

  closeReader(): void {
    if (this.readingSessionService.isSessionActive()) {
      this.readingSessionService.endSession(
        this.currentCfi || undefined,
        this.progressPercentage
      );
    }
    this.location.back();
  }

  toggleDrawer(): void {
    this.isDrawerVisible = !this.isDrawerVisible;
    if (this.isDrawerVisible) {
      this.clearHeaderTimeout();
    } else {
      this.startHeaderAutoHide();
    }
  }

  toggleSettingsDrawer(): void {
    this.isSettingsDrawerVisible = !this.isSettingsDrawerVisible;
    if (this.isSettingsDrawerVisible) {
      this.clearHeaderTimeout();
    } else {
      this.startHeaderAutoHide();
    }
  }

  navigateToChapter(chapter: { label: string; href: string; level: number }): void {
    if (this.rendition) {
      this.rendition.display(chapter.href).then(() => {
        const location = this.rendition.currentLocation();
        this.readingSessionService.updateProgress(
          location?.start?.cfi,
          this.progressPercentage
        );
      });
    }
  }

  prevPage(): void {
    if (this.rendition) {
      this.rendition.prev();
    }
  }

  nextPage(): void {
    if (this.rendition) {
      this.rendition.next();
    }
  }

  isMobileDevice(): boolean {
    return window.innerWidth <= 768;
  }

  private trackProgress(): void {
    if (!this.book || !this.rendition) return;
    this.rendition.on('relocated', (location: any) => {
      this.updateCurrentChapter(location);
      const cfi = location.end.cfi;
      this.currentCfi = location.start.cfi;
      this.updateBookmarkStatus();
      const currentIndex = location.start.index;
      const totalSpineItems = this.book.spine.items.length;
      let percentage: number;

      if (this.locationsReady && this.book.locations.total > 0) {
        percentage = this.book.locations.percentageFromCfi(cfi);
        this.exactProgress = Math.round(percentage * 1000) / 10;
        this.progressPercentage = Math.round(percentage * 1000) / 10;
      } else {
        if (totalSpineItems > 0) {
          percentage = (currentIndex + 1) / totalSpineItems;
        } else {
          percentage = 0;
        }
        this.approxProgress = Math.round(percentage * 1000) / 10;
        this.progressPercentage = Math.round(percentage * 1000) / 10;
      }

      this.bookService.saveEpubProgress(this.epub.id, cfi, Math.round(percentage * 1000) / 10).subscribe();

      this.readingSessionService.updateProgress(
        location.start.cfi,
        this.progressPercentage
      );
    });

    this.book.ready.then(() => {
      return this.book.locations.generate(1600);
    }).then(() => {
      this.locationsReady = true;
      if (this.rendition.currentLocation()) {
        const location = this.rendition.currentLocation();
        const cfi = location.end.cfi;
        const percentage = this.book.locations.percentageFromCfi(cfi);
        this.progressPercentage = Math.round(percentage * 1000) / 10;

        this.readingSessionService.startSession(this.epub.id, "EPUB", location.start.cfi, this.progressPercentage);
      }
    }).catch(() => {
      this.locationsReady = false;
      const location = this.rendition.currentLocation();
      if (location) {
        this.readingSessionService.startSession(this.epub.id, "EPUB", location.start.cfi, this.progressPercentage);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.readingSessionService.isSessionActive()) {
      this.readingSessionService.endSession(
        this.currentCfi || undefined,
        this.progressPercentage
      );
    }

    this.routeSubscription?.unsubscribe();

    if (this.currentIframe && this.currentIframe.contentDocument) {
      if (this.iframeTouchHandler) {
        this.currentIframe.contentDocument.removeEventListener('touchend', this.iframeTouchHandler, true);
      }
      if (this.iframeClickHandler) {
        this.currentIframe.contentDocument.removeEventListener('click', this.iframeClickHandler, true);
      }
    }

    if (this.rendition) {
      this.rendition.off('keyup', this.keyListener);
    }
    document.removeEventListener('keyup', this.keyListener);

    if (this.hideControlsTimeout) {
      window.clearTimeout(this.hideControlsTimeout);
    }

    this.clearHeaderTimeout();
  }

  selectTheme(themeKey: string): void {
    this.selectedTheme = themeKey;
    this.changeThemes();
  }

  getThemeColor(themeKey: string | undefined): string {
    return EpubThemeUtil.getThemeColor(themeKey);
  }

  onBookClick(event: MouseEvent): void {

    const clickX = event.clientX;
    const clickY = event.clientY;
    const screenWidth = window.innerWidth;
    const screenHeight = window.innerHeight;

    if (this.isMobileDevice()) {
      this.handleClickAsTap(event);
    } else {
      const isTopClick = clickY < screenHeight * 0.1;
      if (isTopClick) {
        this.showHeader = true;
        this.startHeaderAutoHide();
      }
    }
  }

  onHeaderZoneEnter(): void {
    if (this.isMobileDevice()) return;
    this.isMouseInTopRegion = true;
    this.showHeader = true;
    this.clearHeaderTimeout();
  }

  onHeaderZoneLeave(): void {
    if (this.isMobileDevice()) return;
    this.isMouseInTopRegion = false;
    this.startHeaderAutoHide();
  }

  onHeaderZoneClick(event: MouseEvent): void {
    if (!this.showHeader) {
      this.showHeader = true;
      this.clearHeaderTimeout();
    }
  }

  onBookMouseMove(event: MouseEvent): void {
    if (this.isDrawerVisible || this.isSettingsDrawerVisible || this.isMobileDevice()) {
      return;
    }

    const mouseY = event.clientY;
    const screenHeight = window.innerHeight;
    const isInTopRegion = mouseY < screenHeight * 0.1;

    if (isInTopRegion && !this.isMouseInTopRegion) {
      this.isMouseInTopRegion = true;
      this.showHeader = true;
      this.clearHeaderTimeout();
    } else if (!isInTopRegion && this.isMouseInTopRegion) {
      this.isMouseInTopRegion = false;
      this.startHeaderAutoHide();
    }
  }

  onBookTouch(event?: TouchEvent): void {
    if (this.isMobileDevice()) {
      if (event) {
        this.handleTouchEnd(event);
      } else {
        this.showControlsTemporarily();
      }
    }
  }

  private showControlsTemporarily(): void {
    this.showControls = true;
    this.showHeader = true;
    this.headerShownByMobileTouch = false;
    this.clearHeaderTimeout();

    if (this.hideControlsTimeout) {
      window.clearTimeout(this.hideControlsTimeout);
    }

    this.hideControlsTimeout = window.setTimeout(() => {
      this.ngZone.run(() => {
        this.showControls = false;
        if (!this.isDrawerVisible && !this.isSettingsDrawerVisible) {
          this.showHeader = false;
        }
      });
    }, 2000);
  }

  startHeaderAutoHide(): void {
    this.clearHeaderTimeout();

    if (this.isDrawerVisible || this.isSettingsDrawerVisible || this.isMouseInTopRegion) {
      return;
    }

    this.hideHeaderTimeout = window.setTimeout(() => {
      this.ngZone.run(() => {
        if (!this.isDrawerVisible && !this.isSettingsDrawerVisible && !this.isMouseInTopRegion) {
          this.showHeader = false;
          this.headerShownByMobileTouch = false;
        }
      });
    }, this.isMobileDevice() ? 2000 : 1000);
  }

  private clearHeaderTimeout(): void {
    if (this.hideHeaderTimeout) {
      window.clearTimeout(this.hideHeaderTimeout);
      this.hideHeaderTimeout = undefined;
    }
  }

  private updateCurrentChapter(location: any): void {
    if (!location) return;
    const chapter = getChapter(this.book, location);
    if (chapter) {
      if (chapter.label) {
        this.currentChapter = chapter.label;
      }
      this.currentChapterHref = chapter.href;
    }
  }

  private extractChapters(toc: any[], level: number): { label: string; href: string; level: number }[] {
    const chapters: { label: string; href: string; level: number }[] = [];

    for (const item of toc) {
      chapters.push({
        label: item.label,
        href: item.href,
        level: level
      });

      if (item.subitems && item.subitems.length > 0) {
        chapters.push(...this.extractChapters(item.subitems, level + 1));
      }
    }

    return chapters;
  }

  addBookmark(): void {
    if (!this.currentCfi || this.isAddingBookmark) {
      return;
    }

    if (this.isCurrentLocationBookmarked()) {
      this.messageService.add({
        severity: 'info',
        summary: 'Info',
        detail: 'Bookmark already exists at this location',
      });
      return;
    }

    this.isAddingBookmark = true;
    const title = this.currentChapter || 'Untitled Bookmark';
    const request = {
      bookId: this.epub.id,
      cfi: this.currentCfi,
      title: title
    };

    this.bookMarkService.createBookmark(request).subscribe({
      next: (bookmark) => {
        this.bookmarks.push(bookmark);
        this.bookmarks = [...this.bookmarks];
        this.updateBookmarkStatus();
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Bookmark created successfully',
        });
        this.isAddingBookmark = false;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to create bookmark',
        });
        this.isAddingBookmark = false;
      },
    });
  }

  deleteBookmark(bookmarkId: number): void {
    if (this.isDeletingBookmark) {
      return;
    }
    if (!confirm('Are you sure you want to delete this bookmark?')) {
      return;
    }

    this.isDeletingBookmark = true;
    this.bookMarkService.deleteBookmark(bookmarkId).subscribe({
      next: () => {
        this.bookmarks = this.bookmarks.filter(b => b.id !== bookmarkId);
        this.updateBookmarkStatus();
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Bookmark deleted successfully',
        });
        this.isDeletingBookmark = false;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to delete bookmark',
        });
        this.isDeletingBookmark = false;
      },
    });
  }

  navigateToBookmark(bookmark: BookMark): void {
    if (this.rendition && bookmark.cfi) {
      this.rendition.display(bookmark.cfi).then(() => {
        const location = this.rendition.currentLocation();
        this.readingSessionService.updateProgress(
          location?.start?.cfi,
          this.progressPercentage
        );
      });
    }
  }

  isCurrentLocationBookmarked(): boolean {
    if (!this.currentCfi) return false;
    return this.bookmarks.some(b => b.cfi === this.currentCfi);
  }

  private updateBookmarkStatus(): void {
    this.isBookmarked = this.currentCfi
      ? this.bookmarks.some(b => b.cfi === this.currentCfi)
      : false;
  }

  openEditBookmarkDialog(bookmark: BookMark): void {
    this.editingBookmark = {...bookmark};
    this.showEditBookmarkDialog = true;
  }

  onBookmarkSave(updateRequest: UpdateBookMarkRequest): void {
    if (!this.editingBookmark || this.isEditingBookmark) {
      return;
    }

    this.isEditingBookmark = true;

    this.bookMarkService.updateBookmark(this.editingBookmark.id, updateRequest).subscribe({
      next: (updatedBookmark) => {
        const index = this.bookmarks.findIndex(b => b.id === this.editingBookmark!.id);
        if (index !== -1) {
          this.bookmarks[index] = updatedBookmark;
          this.bookmarks = [...this.bookmarks];
        }
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Bookmark updated successfully',
        });
        this.showEditBookmarkDialog = false;
        this.editingBookmark = null;
        this.isEditingBookmark = false;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update bookmark',
        });
        this.showEditBookmarkDialog = false;
        this.editingBookmark = null;
        this.isEditingBookmark = false;
      }
    });
  }

  onBookmarkCancel(): void {
    this.showEditBookmarkDialog = false;
    this.editingBookmark = null;
  }
}
