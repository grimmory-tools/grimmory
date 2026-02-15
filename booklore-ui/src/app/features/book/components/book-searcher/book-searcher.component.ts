import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {BehaviorSubject, of, Subscription} from 'rxjs';
import {catchError, switchMap} from 'rxjs/operators';
import {Book} from '../../model/book.model';
import {FormsModule} from '@angular/forms';
import {InputTextModule} from 'primeng/inputtext';
import {BookService} from '../../service/book.service';
import {Button} from 'primeng/button';
import {SlicePipe} from '@angular/common';
import {Divider} from 'primeng/divider';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {Router} from '@angular/router';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {HeaderFilter} from '../book-browser/filters/HeaderFilter';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-book-searcher',
  templateUrl: './book-searcher.component.html',
  imports: [
    FormsModule,
    InputTextModule,
    Button,
    SlicePipe,
    Divider,
    IconField,
    InputIcon,
    TranslocoDirective,
  ],
  styleUrls: ['./book-searcher.component.scss'],
  standalone: true
})
export class BookSearcherComponent implements OnInit, OnDestroy {
  searchQuery: string = '';
  books: Book[] = [];
  #searchSubject = new BehaviorSubject<string>('');
  #subscription!: Subscription;
  isSearchFocused = false;

  private bookService = inject(BookService);
  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private readonly t = inject(TranslocoService);
  private headerFilter = new HeaderFilter(this.#searchSubject.asObservable());

  ngOnInit(): void {
    this.#subscription = this.bookService.bookState$.pipe(
      switchMap(bookState => this.headerFilter.filter(bookState)),
      catchError(() => of({books: [], loaded: true, error: null}))
    ).subscribe({
      next: (filteredState) => {
        const term = this.searchQuery.trim();
        this.books = term.length >= 2
          ? (filteredState.books || []).slice(0, 50)
          : [];
      }
    });
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
    this.#searchSubject.next(this.searchQuery.trim());
  }

  onBookClick(book: Book): void {
    this.clearSearch();
    this.router.navigate(['/book', book.id], {
      queryParams: {tab: 'view'}
    });
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.books = [];
  }

  get isDropdownOpen(): boolean {
    return this.books.length > 0;
  }

  onSearchBlur(): void {
    setTimeout(() => {
      if (!this.isDropdownOpen) {
        this.isSearchFocused = false;
      }
    }, 200);
  }

  ngOnDestroy(): void {
    if (this.#subscription) {
      this.#subscription.unsubscribe();
    }
  }
}
