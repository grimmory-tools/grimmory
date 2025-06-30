import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {of, Subject, Subscription} from 'rxjs';
import {catchError, debounceTime, distinctUntilChanged, switchMap} from 'rxjs/operators';
import {Book} from '../../model/book.model';
import {FormsModule} from '@angular/forms';
import {InputTextModule} from 'primeng/inputtext';
import {BookService} from '../../service/book.service';
import {Button} from 'primeng/button';
import {SlicePipe} from '@angular/common';
import {Divider} from 'primeng/divider';
import {UrlHelperService} from '../../../utilities/service/url-helper.service';
import {Router} from '@angular/router';
import {OverlayPanelModule} from 'primeng/overlaypanel';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';

@Component({
  selector: 'app-book-searcher',
  templateUrl: './book-searcher.component.html',
  imports: [
    FormsModule,
    InputTextModule,
    Button,
    SlicePipe,
    Divider,
    OverlayPanelModule,
    IconField,
    InputIcon
  ],
  styleUrls: ['./book-searcher.component.scss'],
  standalone: true
})
export class BookSearcherComponent implements OnInit, OnDestroy {
  searchQuery: string = '';
  books: Book[] = [];
  #searchSubject = new Subject<string>();
  #subscription!: Subscription;

  private bookService = inject(BookService);
  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);

  isSearchDropdownOpen = false;

  toggleSearchInputDropdown() {
    this.isSearchDropdownOpen = !this.isSearchDropdownOpen;
  }

  closeSearchDropdown() {
    this.isSearchDropdownOpen = false;
  }

  ngOnInit(): void {
    this.initializeSearch();
  }

  initializeSearch(): void {
    this.#subscription = this.#searchSubject.pipe(
      debounceTime(350),
      distinctUntilChanged(),
      switchMap((query) => {
        const result = this.bookService.searchBooks(query);
        return of(result);
      }),
      catchError((error) => {
        console.error('Error while searching books:', error);
        return of([]);
      })
    ).subscribe({
      next: (result: Book[]) => this.books = result,
      error: (error) => console.error('Subscription error:', error)
    });
  }

  getAuthorNames(authors: string[] | undefined): string {
    return authors?.join(', ') || 'Unknown Author';
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

  ngOnDestroy(): void {
    if (this.#subscription) {
      this.#subscription.unsubscribe();
    }
  }
}
