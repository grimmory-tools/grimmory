import {Component, ElementRef, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import ePub, {EpubCFI} from 'epubjs';
import {Drawer} from 'primeng/drawer';
import {Button} from 'primeng/button';
import {NgForOf, NgIf} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Divider} from 'primeng/divider';
import {ActivatedRoute} from '@angular/router';
import {Book, BookSetting} from '../../../model/book.model';
import {BookService} from '../../../service/book.service';
import {forkJoin} from 'rxjs';
import {Select} from 'primeng/select';
import {UserService} from '../../../../settings/user-management/user.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {MessageService} from 'primeng/api';

const FALLBACK_EPUB_SETTINGS = {
  fontSize: 150,
  fontType: 'serif',
  theme: 'white',
  maxFontSize: 300,
  minFontSize: 50,
};

function flatten(chapters: any) {
  return [].concat.apply([], chapters.map((chapter: any) => [].concat.apply([chapter], flatten(chapter.subitems))));
}

export function getCfiFromHref(book: any, href: string): string {
  const [_, id] = href.split('#');
  const section = book.spine.get(href);
  const el = (id ? section.document.getElementById(id) : section.document.body) as Element;
  return section.cfiFromElement(el);
}

export function getChapter(book: any, location: any) {
  const locationHref = location.start.href;
  return flatten(book.navigation.toc)
    .filter((chapter: any) => {
      return book.canonical(chapter.href).includes(book.canonical(locationHref));
    })
    .reduce((result: any | null, chapter: any) => {
      const locationAfterChapter = EpubCFI.prototype.compare(location.start.cfi, getCfiFromHref(book, chapter.href)) > 0;
      return locationAfterChapter ? chapter : result;
    }, null);
}

@Component({
  selector: 'app-epub-viewer',
  templateUrl: './epub-viewer.component.html',
  styleUrls: ['./epub-viewer.component.scss'],
  imports: [Drawer, Button, NgForOf, FormsModule, Divider, Select, ProgressSpinner, NgIf],
  standalone: true
})
export class EpubViewerComponent implements OnInit, OnDestroy {
  @ViewChild('epubContainer', {static: false}) epubContainer!: ElementRef;

  isLoading = true;
  chapters: { label: string; href: string }[] = [];
  currentChapter = '';
  isDrawerVisible = false;
  isSettingsDrawerVisible = false;
  private book: any;
  private rendition: any;
  private keyListener: (e: KeyboardEvent) => void = () => {
  };

  fontSize = FALLBACK_EPUB_SETTINGS.fontSize;
  selectedFontType = FALLBACK_EPUB_SETTINGS.fontType;
  selectedTheme = FALLBACK_EPUB_SETTINGS.theme;

  fontTypes: any[] = [
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
        next: (results) => {
          const myself = results[0];
          const epub = results[1];
          const epubData = results[2];
          const individualSetting = results[3]?.epubSettings;

          this.epub = epub;
          const fileReader = new FileReader();

          fileReader.onload = () => {
            this.book = ePub(fileReader.result as ArrayBuffer);

            this.book.loaded.navigation.then((nav: any) => {
              this.chapters = nav.toc.map((chapter: any) => ({
                label: chapter.label,
                href: chapter.href,
              }));
            });

            this.rendition = this.book.renderTo(this.epubContainer.nativeElement, {
              flow: 'paginated',
              width: '100%',
              height: '100%',
              allowScriptedContent: true,
            });

            if (this.epub?.epubProgress) {
              this.rendition.display(this.epub.epubProgress);
            } else {
              this.rendition.display();
            }

            this.themesMap.forEach((theme, name) => {
              this.rendition.themes.register(name, theme);
            });

            let globalOrIndividual = myself.bookPreferences.perBookSetting.epub;
            if (globalOrIndividual === 'Global') {
              this.selectedTheme = myself.bookPreferences.epubReaderSetting.theme || FALLBACK_EPUB_SETTINGS.theme;
              this.selectedFontType = myself.bookPreferences.epubReaderSetting.font || FALLBACK_EPUB_SETTINGS.fontType;
              this.fontSize = myself.bookPreferences.epubReaderSetting.fontSize || FALLBACK_EPUB_SETTINGS.fontSize;
            } else {
              this.selectedTheme = individualSetting?.theme || myself.bookPreferences.epubReaderSetting.theme || FALLBACK_EPUB_SETTINGS.theme;
              this.selectedFontType = individualSetting?.font || myself.bookPreferences.epubReaderSetting.font || FALLBACK_EPUB_SETTINGS.fontType;
              this.fontSize = individualSetting?.fontSize || myself.bookPreferences.epubReaderSetting.fontSize || FALLBACK_EPUB_SETTINGS.fontSize;
            }

            this.rendition.themes.select(this.selectedTheme);
            this.rendition.themes.fontSize(`${this.fontSize}%`);
            this.rendition.themes.font(this.selectedFontType);

            this.setupKeyListener();
            this.trackProgress();
            this.isLoading = false;
          };

          fileReader.readAsArrayBuffer(epubData);
        },
        error: () => {
          this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load the book'});
          this.isLoading = false;
        }
      });
    });
  }

  changeThemes(): void {
    if (this.rendition) {
      this.rendition.themes.select(this.selectedTheme);
      this.rendition.clear();
      this.rendition.start();
      this.updateViewerSetting();
    }
  }

  updateFontSize(): void {
    if (this.rendition) {
      this.rendition.themes.fontSize(`${this.fontSize}%`);
      this.updateViewerSetting();
    }
  }

  changeFontType(): void {
    if (this.rendition) {
      this.rendition.themes.font(this.selectedFontType);
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
    const bookSetting: BookSetting = {
      epubSettings: {
        theme: this.selectedTheme,
        font: this.selectedFontType,
        fontSize: this.fontSize,
      }
    }
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
    if (this.rendition) {
      this.rendition.on('relocated', (location: any) => {
        const currentChapter = getChapter(this.book, location);
        this.currentChapter = currentChapter?.label;
        this.bookService.saveEpubProgress(this.epub.id, location.start.cfi).subscribe();
      });
    }
  }


  ngOnDestroy(): void {
    if (this.rendition) {
      this.rendition.off('keyup', this.keyListener);
    }
    document.removeEventListener('keyup', this.keyListener);
    this.trackProgress();
  }

  themesMap = new Map<string, any>([
    [
      'black', {
      "body": {"background-color": "#000000", "color": "#f9f9f9"},
      "p": {"color": "#f9f9f9"},
      "h1, h2, h3, h4, h5, h6": {"color": "#f9f9f9"},
      "a": {"color": "#f9f9f9"},
      "img": {
        "-webkit-filter": "invert(1) hue-rotate(180deg)",
        "filter": "invert(1) hue-rotate(180deg)"
      },
      "code": {"color": "#00ff00", "background-color": "black"}
    }
    ],
    [
      'sepia', {
      "body": {"background-color": "#f4ecd8", "color": "#6e4b3a"},
      "p": {"color": "#6e4b3a"},
      "h1, h2, h3, h4, h5, h6": {"color": "#6e4b3a"},
      "a": {"color": "#8b4513"},
      "img": {
        "-webkit-filter": "sepia(1) contrast(1.5)",
        "filter": "sepia(1) contrast(1.5)"
      },
      "code": {"color": "#8b0000", "background-color": "#f4ecd8"}
    }
    ],
    [
      'white', {
      "body": {"background-color": "#ffffff", "color": "#000000"},
      "p": {"color": "#000000"},
      "h1, h2, h3, h4, h5, h6": {"color": "#000000"},
      "a": {"color": "#000000"},
      "img": {
        "-webkit-filter": "none",
        "filter": "none"
      },
      "code": {"color": "#d14", "background-color": "#f5f5f5"}
    }
    ],
    [
      'grey', {
      "body": {"background-color": "#404040", "color": "#d3d3d3"},
      "p": {"color": "#d3d3d3"},
      "h1, h2, h3, h4, h5, h6": {"color": "#d3d3d3"},
      "a": {"color": "#1e90ff"},
      "img": {
        "filter": "none"
      },
      "code": {"color": "#d14", "background-color": "#585858"}
    }
    ]
  ]);

}
