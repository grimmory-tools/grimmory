import {inject, Injectable} from '@angular/core';
import {Book} from '../model/book.model';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {
  addBookToCache,
  invalidateAppBooksQueries,
  invalidateBookDetailQueries,
  invalidateBooksQuery,
  patchBooksInCache,
  removeBookQueries,
} from './book-query-cache';

@Injectable({
  providedIn: 'root',
})
export class BookSocketService {
  private queryClient = inject(QueryClient);

  handleNewlyCreatedBook(book: Book): void {
    addBookToCache(this.queryClient, book);
  }

  handleRemovedBookIds(removedBookIds: number[]): void {
    invalidateBooksQuery(this.queryClient);
    removeBookQueries(this.queryClient, removedBookIds);
  }

  handleBookUpdate(updatedBook: Book): void {
    patchBooksInCache(this.queryClient, [updatedBook]);
  }

  handleMultipleBookUpdates(updatedBooks: Book[]): void {
    patchBooksInCache(this.queryClient, updatedBooks);
  }

  handleBookMetadataUpdate(bookId: number): void {
    invalidateBooksQuery(this.queryClient);
    invalidateBookDetailQueries(this.queryClient, [bookId]);
  }

  handleMultipleBookCoverPatches(patches: { id: number; coverUpdatedOn: string }[]): void {
    if (!patches || patches.length === 0) return;
    const patchMap = new Map(patches.map(p => [p.id, p.coverUpdatedOn]));
    this.queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
      (current ?? []).map(book => {
        const coverUpdatedOn = patchMap.get(book.id);
        return coverUpdatedOn && book.metadata
          ? {...book, metadata: {...book.metadata, coverUpdatedOn}}
          : book;
      })
    );
    invalidateBookDetailQueries(this.queryClient, patches.map(p => p.id));
    invalidateAppBooksQueries(this.queryClient);
  }
}
