import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import ePub from 'epubjs';
import {Drawer} from 'primeng/drawer';
import {Button} from 'primeng/button';

import {FormsModule} from '@angular/forms';
import {ActivatedRoute} from '@angular/router';
import {Book, BookSetting} from '../../../model/book.model';
import {BookService} from '../../../service/book.service';
import {forkJoin} from 'rxjs';
import {Select} from 'primeng/select';
import {UserService} from '../../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {MessageService} from 'primeng/api';
import {Tooltip} from 'primeng/tooltip';
import {Slider} from 'primeng/slider';
import {FALLBACK_EPUB_SETTINGS, getChapter} from '../epub-reader-helper';
import {EpubThemeUtil} from '../epub-theme-util';
import {RadioButton} from 'primeng/radiobutton';
import {Divider} from 'primeng/divider';

@Component({
  selector: 'app-epub-viewer',
  templateUrl: './epub-viewer.component.html',
  styleUrls: ['./epub-viewer.component.scss'],
  imports: [Drawer, Button, FormsModule, Select, ProgressSpinner, Tooltip, Slider, RadioButton, Divider],
  standalone: true
})
export class EpubViewerComponent implements OnInit, OnDestroy {
  @ViewChild('epubContainer', {static: false}) epubContainer!: ElementRef;

  isLoading = true;
  chapters: { label: string; href: string }[] = [];
  currentChapter = '';
  isDrawerVisible = false;
  isSettingsDrawerVisible = false;

  public locationsReady = false;
  public approxProgress = 0;
  public exactProgress = 0;

  private book: any;
  private rendition: any;
  private keyListener: (e: KeyboardEvent) => void = () => {
  };

  fontSize?: number = 100;
  selectedFlow?: string = 'paginated';
  selectedTheme?: string = 'white';
  selectedFontType?: string | null = null;
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

  epub!: Book;

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.isLoading = true;
      const bookId = +params.get('bookId')!;

      const myself$ = this.userService.getMyself();
      const epub$ = this.bookService.getBookByIdFromAPI(bookId, false);
      const epubData$ = this.bookService.getFileContent(bookId);
      const bookSetting$ = this.bookService.getBookSetting(bookId);

      forkJoin([myself$, epub$, epubData$, bookSetting$]).subscribe({
        next: ([myself, epub, epubData, bookSetting]) => {
          this.epub = epub;
          const individualSetting = bookSetting?.epubSettings;
          const fileReader = new FileReader();

          fileReader.onload = () => {
            this.book = ePub(fileReader.result as ArrayBuffer);

            this.book.loaded.navigation.then((nav: any) => {
              this.chapters = nav.toc.map((chapter: any) => ({
                label: chapter.label,
                href: chapter.href,
              }));
            });

            const settingScope = myself.userSettings.perBookSetting.epub;
            const globalSettings = myself.userSettings.epubReaderSetting;

            const resolvedFlow = settingScope === 'Global' ? globalSettings.flow : individualSetting?.flow;
            const resolvedFontSize = settingScope === 'Global' ? globalSettings.fontSize : individualSetting?.fontSize;
            const resolvedFontFamily = settingScope === 'Global' ? globalSettings.font : individualSetting?.font;
            const resolvedTheme = settingScope === 'Global' ? globalSettings.theme : individualSetting?.theme;
            const resolvedLineHeight = settingScope === 'Global' ? globalSettings.lineHeight : individualSetting?.lineHeight;
            const resolvedLetterSpacing = settingScope === 'Global' ? globalSettings.letterSpacing : individualSetting?.letterSpacing;

            if (resolvedTheme != null) this.selectedTheme = resolvedTheme;
            if (resolvedFontFamily != null) this.selectedFontType = resolvedFontFamily;
            if (resolvedFontSize != null) this.fontSize = resolvedFontSize;
            if (resolvedLineHeight != null) this.lineHeight = resolvedLineHeight;
            if (resolvedLetterSpacing != null) this.letterSpacing = resolvedLetterSpacing;
            if (resolvedFlow != null) this.selectedFlow = resolvedFlow;

            this.rendition = this.book.renderTo(this.epubContainer.nativeElement, {
              flow: this.selectedFlow ?? 'paginated',
              manager: this.selectedFlow === 'scrolled' ? 'continuous' : 'default',
              width: '100%',
              height: '100%',
              allowScriptedContent: true,
            });

            const baseTheme = EpubThemeUtil.themesMap.get(this.selectedTheme ?? 'black') || {};
            const combinedTheme = {
              ...baseTheme,
              body: {
                ...baseTheme.body,
                ...(this.selectedFontType ? {'font-family': this.selectedFontType} : {}),
                ...(this.fontSize != null ? {'font-size': `${this.fontSize}%`} : {}),
                ...(this.lineHeight != null ? {'line-height': this.lineHeight} : {}),
                ...(this.letterSpacing != null ? {'letter-spacing': `${this.letterSpacing}em`} : {}),
              },
              '*': {
                ...baseTheme['*'],
                ...(this.lineHeight != null ? {'line-height': this.lineHeight} : {}),
                ...(this.letterSpacing != null ? {'letter-spacing': `${this.letterSpacing}em`} : {}),
              },
            };

            this.rendition.themes.register('custom', combinedTheme);
            this.rendition.themes.select('custom');

            const displayPromise = this.epub?.epubProgress?.cfi
              ? this.rendition.display(this.epub.epubProgress.cfi)
              : this.rendition.display();

            displayPromise.then(() => {
              this.setupKeyListener();
              this.trackProgress();
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
      allowScriptedContent: true,
    });

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
      this.rendition.themes.fontSize(`${this.fontSize}%`);
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

  navigateToChapter(chapter: { label: string; href: string }): void {
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
      const cfi = location.end.cfi;
      const currentIndex = location.start.index;
      const totalSpineItems = this.book.spine.items.length;
      let percentage: number;
      if (this.locationsReady) {
        percentage = this.book.locations.percentageFromCfi(cfi);
        this.exactProgress = Math.round(percentage * 1000) / 10;
      } else {
        if (totalSpineItems > 0) {
          percentage = currentIndex / totalSpineItems;
        } else {
          percentage = 0;
        }
        this.approxProgress = Math.round(percentage * 1000) / 10;
      }
      this.currentChapter = getChapter(this.book, location)?.label;
      this.bookService.saveEpubProgress(this.epub.id, cfi, Math.round(percentage * 1000) / 10).subscribe();
    });
    this.book.ready.then(() => this.book.locations.generate(10000)).then(() => {
      this.locationsReady = true;
    });
  }

  ngOnDestroy(): void {
    if (this.rendition) {
      this.rendition.off('keyup', this.keyListener);
    }
    document.removeEventListener('keyup', this.keyListener);
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
}
