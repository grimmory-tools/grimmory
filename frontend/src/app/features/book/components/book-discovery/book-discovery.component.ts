import {ChangeDetectorRef, Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {DatePipe} from '@angular/common';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Tag} from 'primeng/tag';
import {Toast} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {Subscription} from 'rxjs';
import {AcquisitionService, AddToWantedRequest, BookSearchResult} from '../../../../core/services/acquisition.service';

@Component({
  selector: 'app-book-discovery',
  imports: [
    FormsModule,
    DatePipe,
    Button,
    InputText,
    ProgressSpinner,
    Tag,
    Toast,
  ],
  providers: [MessageService],
  templateUrl: './book-discovery.component.html',
  styleUrl: './book-discovery.component.scss'
})
export class BookDiscoveryComponent implements OnInit, OnDestroy {

  private acquisitionService = inject(AcquisitionService);
  private messageService = inject(MessageService);
  private cdr = inject(ChangeDetectorRef);

  searchQuery = '';
  isIsbnSearch = false;
  results: BookSearchResult[] = [];
  libraryIsbns = new Set<string>();
  wantedIsbns = new Set<string>();
  loading = false;
  addedIds = new Set<string>();
  addingIds = new Set<string>();
  currentPage = 0;
  hasMore = false;
  private pageCache = new Map<string, BookSearchResult[]>();
  private prefetchSub?: Subscription;

  ngOnDestroy(): void {
    this.prefetchSub?.unsubscribe();
  }

  ngOnInit(): void {
    this.acquisitionService.getLibraryIsbns().subscribe({
      next: (isbns) => this.libraryIsbns = new Set(isbns)
    });
    this.acquisitionService.getWantedBooks().subscribe({
      next: (books) => {
        this.wantedIsbns = new Set(books.flatMap(b => [b.isbn13, b.isbn10].filter((v): v is string => !!v)));
      }
    });
  }

  search(): void {
    if (!this.searchQuery.trim()) return;
    this.loading = true;
    this.results = [];
    this.currentPage = 0;
    this.hasMore = false;
    this.pageCache.clear();

    const obs = this.isIsbnSearch
      ? this.acquisitionService.searchByIsbn(this.searchQuery.trim())
      : this.acquisitionService.searchBooks(this.searchQuery.trim(), 0);

    obs.subscribe({
      next: (data) => {
        this.results = data;
        this.hasMore = !this.isIsbnSearch && data.length === 20;
        this.loading = false;
        this.cdr.detectChanges();
        if (this.hasMore) this.prefetchPage(this.searchQuery.trim(), 1);
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Search failed'});
      }
    });
  }

  loadMore(): void {
    if (!this.searchQuery.trim() || this.loading) return;
    const nextPage = this.currentPage + 1;
    const cacheKey = `${this.searchQuery.trim()}::${nextPage}`;

    const cached = this.pageCache.get(cacheKey);
    if (cached) {
      this.currentPage = nextPage;
      this.results = [...this.results, ...cached];
      this.hasMore = cached.length === 20;
      if (this.hasMore) this.prefetchPage(this.searchQuery.trim(), this.currentPage + 1);
      return;
    }

    this.loading = true;
    this.currentPage = nextPage;
    this.acquisitionService.searchBooks(this.searchQuery.trim(), this.currentPage).subscribe({
      next: (data) => {
        this.results = [...this.results, ...data];
        this.hasMore = data.length === 20;
        this.loading = false;
        this.cdr.detectChanges();
        if (data.length === 0) {
          this.messageService.add({severity: 'info', summary: 'No more results', detail: 'All results loaded'});
        } else if (this.hasMore) {
          this.prefetchPage(this.searchQuery.trim(), this.currentPage + 1);
        }
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
        this.currentPage--;
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load more results'});
      }
    });
  }

  isInLibrary(book: BookSearchResult): boolean {
    const isbn = book.isbn13 || book.isbn;
    return isbn ? this.libraryIsbns.has(isbn) : false;
  }

  isAdded(book: BookSearchResult): boolean {
    if (this.addedIds.has(this.bookKey(book))) return true;
    const isbn = book.isbn13 || book.isbn;
    return isbn ? this.wantedIsbns.has(isbn) : false;
  }

  isAdding(book: BookSearchResult): boolean {
    return this.addingIds.has(this.bookKey(book));
  }

  addToWanted(book: BookSearchResult): void {
    const key = this.bookKey(book);
    this.addingIds.add(key);

    const req: AddToWantedRequest = {
      title: book.title,
      author: book.authors?.join(', ') || book.author,
      isbn13: book.isbn13 || book.isbn,
      provider: book.provider || 'GOOGLE',
      providerBookId: book.googleBookId || book.id,
      thumbnailUrl: book.thumbnailUrl
    };

    this.acquisitionService.addToWanted(req).subscribe({
      next: () => {
        this.addingIds.delete(key);
        this.addedIds.add(key);
        this.messageService.add({severity: 'success', summary: 'Added', detail: `"${book.title}" added to wanted list`});
      },
      error: (err: {status: number}) => {
        this.addingIds.delete(key);
        const msg = err.status === 409 ? 'Already in wanted list' : 'Failed to add to wanted list';
        this.messageService.add({severity: err.status === 409 ? 'info' : 'error', summary: err.status === 409 ? 'Info' : 'Error', detail: msg});
      }
    });
  }

  private prefetchPage(query: string, page: number): void {
    const cacheKey = `${query}::${page}`;
    if (this.pageCache.has(cacheKey)) return;
    this.prefetchSub?.unsubscribe();
    this.prefetchSub = this.acquisitionService.searchBooks(query, page).subscribe({
      next: (data) => this.pageCache.set(cacheKey, data)
    });
  }

  private bookKey(book: BookSearchResult): string {
    return book.isbn13 || book.isbn || book.title;
  }

  getAuthors(book: BookSearchResult): string {
    if (book.authors && Array.isArray(book.authors)) return book.authors.join(', ');
    return book.author || '';
  }
}
