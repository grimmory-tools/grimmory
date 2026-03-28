import {computed, Injectable, signal} from '@angular/core';
import {AuthorSummary} from '../model/author.model';

export interface AuthorCheckboxClickEvent {
  index: number;
  author: AuthorSummary;
  selected: boolean;
  shiftKey: boolean;
}

@Injectable({providedIn: 'root'})
export class AuthorSelectionService {
  private readonly _selectedAuthors = signal<Set<number>>(new Set());
  readonly selectedAuthors = this._selectedAuthors.asReadonly();
  readonly selectedCount = computed(() => this._selectedAuthors().size);

  private currentAuthors: AuthorSummary[] = [];
  private lastSelectedIndex: number | null = null;

  setCurrentAuthors(authors: AuthorSummary[]): void {
    this.currentAuthors = authors;
  }

  handleCheckboxClick(event: AuthorCheckboxClickEvent): void {
    const {index, author, selected, shiftKey} = event;

    if (!shiftKey || this.lastSelectedIndex === null) {
      if (selected) {
        this.select(author.id);
      } else {
        this.deselect(author.id);
      }
      this.lastSelectedIndex = index;
    } else {
      const start = Math.min(this.lastSelectedIndex, index);
      const end = Math.max(this.lastSelectedIndex, index);
      for (let i = start; i <= end; i++) {
        const a = this.currentAuthors[i];
        if (!a) continue;
        if (selected) {
          this.select(a.id);
        } else {
          this.deselect(a.id);
        }
      }
    }
  }

  selectAll(): void {
    this._selectedAuthors.update(current => {
      const next = new Set(current);
      for (const author of this.currentAuthors) {
        next.add(author.id);
      }
      return next;
    });
  }

  deselectAll(): void {
    this._selectedAuthors.set(new Set());
    this.lastSelectedIndex = null;
  }

  getSelectedIds(): number[] {
    return Array.from(this.selectedAuthors());
  }

  private select(id: number): void {
    this._selectedAuthors.update(current => {
      const next = new Set(current);
      next.add(id);
      return next;
    });
  }

  private deselect(id: number): void {
    this._selectedAuthors.update(current => {
      const next = new Set(current);
      next.delete(id);
      return next;
    });
  }
}
