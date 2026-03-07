import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {Book} from '../../model/book.model';

export interface CheckboxClickEvent {
  index: number;
  book: Book;
  selected: boolean;
  shiftKey: boolean;
}

@Injectable({providedIn: 'root'})
export class BookSelectionService {
  private selectedBooksSubject = new BehaviorSubject<Set<number>>(new Set());
  private currentBooks: Book[] = [];
  private lastSelectedIndex: number | null = null;

  selectedBooks$: Observable<Set<number>> = this.selectedBooksSubject.asObservable();

  get selectedBooks(): Set<number> {
    return this.selectedBooksSubject.value;
  }

  get selectedCount(): number {
    return this.selectedBooksSubject.value.size;
  }

  setCurrentBooks(books: Book[]): void {
    this.currentBooks = books;
  }

  getCurrentBooks(): Book[] {
    return this.currentBooks;
  }

  selectBook(book: Book): void {
    const current = new Set(this.selectedBooksSubject.value);
    if (book.seriesBooks) {
      book.seriesBooks.forEach(b => current.add(b.id));
    } else {
      current.add(book.id);
    }
    this.selectedBooksSubject.next(current);
  }

  deselectBook(book: Book): void {
    const current = new Set(this.selectedBooksSubject.value);
    if (book.seriesBooks) {
      book.seriesBooks.forEach(b => current.delete(b.id));
    } else {
      current.delete(book.id);
    }
    this.selectedBooksSubject.next(current);
  }

  handleCheckboxClick(event: CheckboxClickEvent): void {
    const {index, book, selected, shiftKey} = event;

    if (!shiftKey || this.lastSelectedIndex === null) {
      this.handleBookSelection(book, selected);
      this.lastSelectedIndex = index;
    } else {
      const start = Math.min(this.lastSelectedIndex, index);
      const end = Math.max(this.lastSelectedIndex, index);
      const isUnselectingRange = !selected;

      for (let i = start; i <= end; i++) {
        const rangeBook = this.currentBooks[i];
        if (!rangeBook) continue;
        this.handleBookSelection(rangeBook, !isUnselectingRange);
      }
    }
  }

  handleBookSelection(book: Book, selected: boolean): void {
    if (selected) {
      this.selectBook(book);
    } else {
      this.deselectBook(book);
    }
  }

  selectAll(): void {
    if (!this.currentBooks || this.currentBooks.length === 0) return;

    const current = new Set(this.selectedBooksSubject.value);
    for (const book of this.currentBooks) {
      if (book.seriesBooks && book.seriesBooks.length > 0) {
        book.seriesBooks.forEach(b => current.add(b.id));
      } else {
        current.add(book.id);
      }
    }
    this.selectedBooksSubject.next(current);
  }

  deselectAll(): void {
    this.selectedBooksSubject.next(new Set());
    this.lastSelectedIndex = null;
  }

  setSelectedBooks(bookIds: Set<number>): void {
    this.selectedBooksSubject.next(new Set(bookIds));
  }

  hasSelection(): boolean {
    return this.selectedBooksSubject.value.size > 0;
  }
}
