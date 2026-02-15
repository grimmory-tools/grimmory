import {inject, Injectable} from '@angular/core';
import {BookStateService} from './book-state.service';
import {BookCacheService} from './book-cache.service';
import {Book, BookMetadata} from '../model/book.model';

@Injectable({
  providedIn: 'root',
})
export class BookSocketService {
  private bookStateService = inject(BookStateService);
  private bookCacheService = inject(BookCacheService);

  handleNewlyCreatedBook(book: Book): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const updatedBooks = currentState.books ? [...currentState.books] : [];
    const bookIndex = updatedBooks.findIndex(existingBook => existingBook.id === book.id);
    if (bookIndex > -1) {
      updatedBooks[bookIndex] = book;
    } else {
      updatedBooks.push(book);
    }
    this.bookStateService.updateBookState({...currentState, books: updatedBooks});
    this.bookCacheService.put(book);
  }

  handleRemovedBookIds(removedBookIds: number[]): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const filteredBooks = (currentState.books || []).filter(book => !removedBookIds.includes(book.id));
    this.bookStateService.updateBookState({...currentState, books: filteredBooks});
    this.bookCacheService.deleteMany(removedBookIds);
  }

  handleBookUpdate(updatedBook: Book): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const updatedBooks = (currentState.books || []).map(book =>
      book.id === updatedBook.id ? updatedBook : book
    );
    this.bookStateService.updateBookState({...currentState, books: updatedBooks});
    this.bookCacheService.put(updatedBook);
  }

  handleMultipleBookUpdates(updatedBooks: Book[]): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const currentBooks = currentState.books || [];

    const updatedMap = new Map(updatedBooks.map(book => [book.id, book]));

    const mergedBooks = currentBooks.map(book =>
      updatedMap.has(book.id) ? updatedMap.get(book.id)! : book
    );

    this.bookStateService.updateBookState({...currentState, books: mergedBooks});
    this.bookCacheService.putAll(updatedBooks);
  }

  handleBookMetadataUpdate(bookId: number, updatedMetadata: BookMetadata): void {
    const currentState = this.bookStateService.getCurrentBookState();
    const updatedBooks = (currentState.books || []).map(book => {
      return book.id === bookId ? {...book, metadata: updatedMetadata} : book;
    });
    this.bookStateService.updateBookState({...currentState, books: updatedBooks});

    // Mirror metadata update to cache
    const updatedBook = updatedBooks.find(b => b.id === bookId);
    if (updatedBook) {
      this.bookCacheService.put(updatedBook);
    }
  }

  handleMultipleBookCoverPatches(patches: { id: number; coverUpdatedOn: string }[]): void {
    if (!patches || patches.length === 0) return;
    const currentState = this.bookStateService.getCurrentBookState();
    const books = currentState.books || [];
    const patchedBooks: Book[] = [];
    patches.forEach(p => {
      const index = books.findIndex(b => b.id === p.id);
      if (index !== -1 && books[index].metadata) {
        books[index].metadata.coverUpdatedOn = p.coverUpdatedOn;
        patchedBooks.push(books[index]);
      }
    });
    this.bookStateService.updateBookState({...currentState, books});
    if (patchedBooks.length > 0) {
      this.bookCacheService.putAll(patchedBooks);
    }
  }
}

