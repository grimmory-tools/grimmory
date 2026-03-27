import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import {TranslocoDirective} from '@jsverse/transloco';

@Component({
  selector: 'app-library-loading',
  standalone: true,
  imports: [CommonModule, TranslocoDirective],
  templateUrl: './library-loading.component.html',
  styleUrl: './library-loading.component.scss'
})
export class LibraryLoadingComponent {
  bookTitle = '';
  current = 0;
  total = 0;
  isComplete = false;

  get percentage(): number {
    return this.total > 0 ? Math.round((this.current / this.total) * 100) : 0;
  }

  get truncatedTitle(): string {
    const maxLength = 50;
    return this.bookTitle.length <= maxLength
      ? this.bookTitle
      : this.bookTitle.substring(0, maxLength) + '...';
  }

  updateProgress(bookTitle: string, current: number, total: number): void {
    this.bookTitle = bookTitle;
    this.current = current;
    this.total = total;
    this.isComplete = current >= total;
  }

  onReload(): void {
    window.location.reload();
  }
}

