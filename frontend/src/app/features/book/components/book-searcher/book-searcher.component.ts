import {Component, ElementRef, HostListener, computed, inject, signal, ViewChild} from '@angular/core';
import {toObservable, toSignal} from '@angular/core/rxjs-interop';
import {debounceTime, distinctUntilChanged} from 'rxjs/operators';
import {Book} from '../../model/book.model';
import {FormsModule} from '@angular/forms';
import {InputTextModule} from 'primeng/inputtext';
import {BookService} from '../../service/book.service';
import {Button} from 'primeng/button';
import {SlicePipe} from '@angular/common';
import {Skeleton} from 'primeng/skeleton';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {CoverPlaceholderComponent} from '../../../../shared/components/cover-generator/cover-generator.component';
import {Router} from '@angular/router';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {filterBooksBySearchTerm} from '../book-browser/filters/HeaderFilter';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Popover} from 'primeng/popover';

@Component({
  selector: 'app-book-searcher',
  templateUrl: './book-searcher.component.html',
  imports: [
    FormsModule,
    InputTextModule,
    Button,
    SlicePipe,
    Skeleton,
    IconField,
    InputIcon,
    TranslocoDirective,
    CoverPlaceholderComponent,
    Popover,
  ],
  styleUrls: ['./book-searcher.component.scss'],
  standalone: true
})
export class BookSearcherComponent {
  searchQuery = '';
  activeIndex = -1;
  isSearchFocused = false;

  private readonly searchTerm = signal('');
  private readonly debouncedSearchTerm = toSignal(
    toObservable(this.searchTerm).pipe(
      debounceTime(200),
      distinctUntilChanged()
    ),
    {initialValue: ''}
  );
  private readonly filteredBooks = computed(() => {
    const term = this.debouncedSearchTerm().trim();
    if (term.length < 2) {
      return [];
    }

    return filterBooksBySearchTerm(this.bookService.books(), term).slice(0, 50);
  });

  private bookService = inject(BookService);
  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private readonly t = inject(TranslocoService);
  private elRef = inject(ElementRef);

  @ViewChild('resultsPopover') resultsPopover?: Popover;

  get books(): Book[] {
    return this.filteredBooks();
  }

  get isLoading(): boolean {
    const rawTerm = this.searchTerm().trim();
    return rawTerm.length >= 2 && rawTerm !== this.debouncedSearchTerm().trim();
  }

  getAuthorNames(authors: string[] | undefined): string {
    return authors?.join(', ') || this.t.translate('book.searcher.unknownAuthor');
  }

  getPublishedYear(publishedDate: string | undefined): string | null {
    if (!publishedDate) return null;
    const year = publishedDate.split('-')[0];
    return year && year.length === 4 ? year : null;
  }

  getSeriesInfo(seriesName: string | undefined, seriesNumber: number | null | undefined): string | null {
    if (!seriesName) return null;
    if (seriesNumber) {
      return `${seriesName} #${seriesNumber}`;
    }
    return seriesName;
  }

  onSearchInputChange(): void {
    this.searchTerm.set(this.searchQuery.trim());
    this.activeIndex = -1;

    if (this.isDropdownOpen) {
      setTimeout(() => this.showPopover());
    } else {
      this.resultsPopover?.hide();
    }
  }

  private showPopover(): void {
    if (this.resultsPopover) {
      const inputElement = this.elRef.nativeElement.querySelector('.search-input');
      this.resultsPopover.show(null, inputElement);
    }
  }

  onBookClick(book: Book): void {
    this.clearSearch();
    this.router.navigate(['/book', book.id], {
      queryParams: {tab: 'view'}
    });
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.searchTerm.set('');
    this.activeIndex = -1;
    this.resultsPopover?.hide();
  }

  get isDropdownOpen(): boolean {
    return this.books.length > 0 || this.isLoading;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (!this.elRef.nativeElement.contains(event.target)) {
      this.clearSearch();
      this.isSearchFocused = false;
    }
  }

  onKeydown(event: KeyboardEvent): void {
    if (!this.isDropdownOpen) return;

    switch (event.key) {
      case 'ArrowDown':
        this.activeIndex = Math.min(this.activeIndex + 1, this.books.length - 1);
        event.preventDefault();
        break;
      case 'ArrowUp':
        this.activeIndex = Math.max(this.activeIndex - 1, 0);
        event.preventDefault();
        break;
      case 'Enter':
        if (this.activeIndex >= 0 && this.activeIndex < this.books.length) {
          this.onBookClick(this.books[this.activeIndex]);
        }
        break;
      case 'Escape':
        this.clearSearch();
        (event.target as HTMLElement).blur();
        break;
    }
  }
}
