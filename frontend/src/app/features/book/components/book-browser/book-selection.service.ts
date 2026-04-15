import {computed, Injectable, signal} from '@angular/core';
import {Book} from '../../model/book.model';

export interface CheckboxClickEvent {
  index: number;
  book: Book;
  selected: boolean;
  shiftKey: boolean;
}

@Injectable({providedIn: 'root'})
export class BookSelectionService {
  private readonly _selectedBooks = signal<Set<number>>(new Set());
  readonly selectedBooks = this._selectedBooks.asReadonly();
  readonly selectedCount = computed(() => this._selectedBooks().size);

  private currentBooks: Book[] = [];
  private lastSelectedIndex: number | null = null;

  setCurrentBooks(books: Book[]): void {
    this.currentBooks = books;
  }

  selectBook(book: Book): void {
    this._selectedBooks.update(current => {
      const next = new Set(current);
      if (book.seriesBooks) {
        book.seriesBooks.forEach(seriesBook => next.add(seriesBook.id));
      } else {
        next.add(book.id);
      }
      return next;
    });
  }

  deselectBook(book: Book): void {
    this._selectedBooks.update(current => {
      const next = new Set(current);
      if (book.seriesBooks) {
        book.seriesBooks.forEach(seriesBook => next.delete(seriesBook.id));
      } else {
        next.delete(book.id);
      }
      return next;
    });
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

  selectAll(allBookIds?: number[]): void {
    if (allBookIds && allBookIds.length > 0) {
      this._selectedBooks.set(new Set(allBookIds));
      return;
    }

    if (!this.currentBooks || this.currentBooks.length === 0) return;

    this._selectedBooks.update(current => {
      const next = new Set(current);
      for (const book of this.currentBooks) {
        next.add(book.id);
      }
      return next;
    });
  }

  deselectAll(): void {
    this._selectedBooks.set(new Set());
    this.lastSelectedIndex = null;
  }

  setSelectedBooks(bookIds: Set<number>): void {
    this._selectedBooks.set(new Set(bookIds));
  }
}
