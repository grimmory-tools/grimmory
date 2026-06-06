import { computed, signal, Component } from '@angular/core';
import {TranslocoDirective} from '@jsverse/transloco';

const MAX_TITLE_LENGTH = 50;

@Component({
  selector: 'app-library-loading',
  standalone: true,
  imports: [TranslocoDirective],
  templateUrl: './library-loading.component.html',
  styleUrl: './library-loading.component.scss'
})
export class LibraryLoadingComponent {
  bookTitle = signal('');
  current = signal(0);
  total = signal(0);
  isComplete = signal(false);

  readonly percentage = computed(() => this.total() > 0 ? Math.round((this.current() / this.total()) * 100) : 0);

  truncateTitle(title: string): string {
    if (title.length > MAX_TITLE_LENGTH) {
      return title.substring(0, MAX_TITLE_LENGTH) + "...";
    }

    return title;
  }

  updateProgress(bookTitle: string, current: number, total: number): void {
    this.bookTitle.set(this.truncateTitle(bookTitle));
    this.current.set(current);
    this.total.set(total);
    this.isComplete.set(current >= total);
  }

  onReload(): void {
    window.location.reload();
  }
}

