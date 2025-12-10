import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild, NgZone} from '@angular/core';
import ePub from 'epubjs';
import {Drawer} from 'primeng/drawer';
import {Subscription} from 'rxjs';
import {Button} from 'primeng/button';

import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {Book, BookSetting} from '../../../book/model/book.model';
import {BookService} from '../../../book/service/book.service';
import {forkJoin} from 'rxjs';
import {Select} from 'primeng/select';
import {UserService} from '../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {MessageService} from 'primeng/api';
import {BookMarkService, BookMark} from '../../../../shared/service/book-mark.service';
import {Tooltip} from 'primeng/tooltip';
import {Slider} from 'primeng/slider';
import {FALLBACK_EPUB_SETTINGS, getChapter} from '../epub-reader-helper';
import {EpubThemeUtil} from '../epub-theme-util';
import {RadioButton} from 'primeng/radiobutton';
import { PageTitleService } from "../../../../shared/service/page-title.service";
import {Divider} from 'primeng/divider';

@Component({
  selector: 'app-epub-reader',
  templateUrl: './epub-reader.component.html',
  styleUrls: ['./epub-reader.component.scss'],
  imports: [Drawer, Button, FormsModule, Select, ProgressSpinner, Tooltip, Slider, RadioButton, Divider],
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
  private routeSubscription?: Subscription;

  public locationsReady = false;
  public approxProgress = 0;
  public exactProgress = 0;
  public progressPercentage = 0;

  showControls = !this.isMobileDevice();
  private hideControlsTimeout?: number;

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
    {label: "Book's Internal", value: null},
    {label: 'Serif', value: 'serif'},
    {label: 'Sans Serif', value: 'sans-serif'},
    {label: 'Roboto', value: 'roboto'},
    {label: 'Cursive', value: 'cursive'},
    {label: 'Monospace', value: 'monospace'},
  ];

  themes: any[] = [
    {label: 'White', value: 'white'},
    {label: 'Black', value: 'black'},
    {label: 'Grey', value: 'grey'},
    {label: 'Sepia', value: 'sepia'},
  ];

  private route = inject(ActivatedRoute);
  private userService = inject(UserService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);
  private ngZone = inject(NgZone);
  private pageTitle = inject(PageTitleService);
  private bookMarkService = inject(BookMarkService);

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
    if (!this.isMobileDevice() || !this.rendition) return;

    this.rendition.on('rendered', () => {
      const iframe = this.epubContainer.nativeElement.querySelector('iframe');
      if (iframe && iframe.contentDocument) {
        iframe.contentDocument.addEventListener('touchstart', () => {
          this.ngZone.run(() => {
            this.onBookTouch();
          });
        });
      }
    });
  }

  public isMobileDevice(): boolean {
    return window.innerWidth <= 768;
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

  navigateToChapter(chapter: { label: string; href: string; level: number }): void {
    if (this.book && chapter.href) {
      this.book.rendition.display(chapter.href);
    }
  }

  toggleDrawer(): void {
    this.isDrawerVisible = !this.isDrawerVisible;
  }

  toggleSettingsDrawer(): void {
    this.isSettingsDrawerVisible = !this.isSettingsDrawerVisible;
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
      }
    }).catch(() => {
      this.locationsReady = false;
    });
  }

  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
    
    if (this.rendition) {
      this.rendition.off('keyup', this.keyListener);
    }
    document.removeEventListener('keyup', this.keyListener);

    if (this.hideControlsTimeout) {
      window.clearTimeout(this.hideControlsTimeout);
    }
  }

  getThemeColor(themeKey: string | undefined): string {
    switch (themeKey) {
      case 'white':
        return '#ffffff';
      case 'black':
        return '#000000';
      case 'grey':
        return '#808080';
      case 'sepia':
        return '#704214';
      default:
        return '#ffffff';
    }
  }

  selectTheme(themeKey: string): void {
    this.selectedTheme = themeKey;
    this.changeThemes();
  }

  onBookTouch(): void {
    if (this.isMobileDevice()) {
      this.showControls = true;
      if (this.hideControlsTimeout) {
        window.clearTimeout(this.hideControlsTimeout);
      }
      this.hideControlsTimeout = window.setTimeout(() => {
        this.ngZone.run(() => {
          this.showControls = false;
        });
      }, 3000);
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
      this.rendition.display(bookmark.cfi);
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
}
