import {Component, inject, Input, OnInit, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ReadingSessionApiService, ReadingSessionResponse} from '../../../../../shared/service/reading-session-api.service';
import {TableModule} from 'primeng/table';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
@Component({
  selector: 'app-book-reading-sessions',
  standalone: true,
  imports: [CommonModule, TableModule, ProgressSpinnerModule, TranslocoDirective],
  templateUrl: './book-reading-sessions.component.html',
  styleUrls: ['./book-reading-sessions.component.scss']
})
export class BookReadingSessionsComponent implements OnInit, OnChanges {
  @Input() bookId!: number;

  private readonly readingSessionService = inject(ReadingSessionApiService);
  private readonly t = inject(TranslocoService);

  sessions: ReadingSessionResponse[] = [];
  rows = 5;
  loading = false;

  get pageReportTemplate(): string {
    return this.t.translate('metadata.readingSessions.pageReport');
  }

  ngOnInit() {
    this.loadSessions();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['bookId'] && !changes['bookId'].firstChange) {
      this.loadSessions();
    }
  }

  loadSessions() {
    this.loading = true;
    this.readingSessionService.getSessionsByBookId(this.bookId, 0, 100)
      .subscribe({
        next: (response) => {
          this.sessions = response.content;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        }
      });
  }

  formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    }
    return `${secs}s`;
  }

  calculateActualDuration(session: ReadingSessionResponse): number {
    const startTime = new Date(session.startTime).getTime();
    const endTime = new Date(session.endTime).getTime();
    return Math.floor((endTime - startTime) / 1000);
  }

  getActualDuration(session: ReadingSessionResponse): string {
    const actualDuration = this.calculateActualDuration(session);
    const storedDuration = session.durationSeconds;
    
    if (Math.abs(actualDuration - storedDuration) > 1) {
      // Discrepancy detected - show both values
      return `${this.formatDuration(actualDuration)}`;
    }
    
    return this.formatDuration(actualDuration);
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString();
  }

  formatSessionDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  formatTime(dateString: string): string {
    return new Date(dateString).toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit'
    });
  }

  getBookTypeIcon(bookType: string): string {
    const iconMap: Record<string, string> = {
      'PDF': 'pi pi-file-pdf',
      'EPUB': 'pi pi-book',
      'CBX': 'pi pi-images',
      'CBZ': 'pi pi-images',
      'CBR': 'pi pi-images',
      'CB7': 'pi pi-images',
      'FB2': 'pi pi-file',
      'MOBI': 'pi pi-book',
      'AZW3': 'pi pi-book',
      'AUDIOBOOK': 'pi pi-headphones'
    };
    return iconMap[bookType] || 'pi pi-file';
  }

  formatBookType(bookType: string): string {
    if (bookType === 'AUDIOBOOK') return this.t.translate('metadata.readingSessions.audio');
    return bookType;
  }

  getProgressColor(delta: number): 'success' | 'secondary' | 'danger' {
    if (delta > 0) return 'success';
    if (delta < 0) return 'danger';
    return 'secondary';
  }

  getProgressDeltaClass(delta: number): string {
    if (delta > 0) return 'progress-positive';
    if (delta < 0) return 'progress-negative';
    return 'progress-neutral';
  }

  getProgressDeltaIcon(delta: number): string {
    if (delta > 0) return 'pi pi-arrow-up';
    if (delta < 0) return 'pi pi-arrow-down';
    return 'pi pi-minus';
  }

  isPageNumber(location: string | undefined): boolean {
    if (!location) return false;
    return !isNaN(Number(location)) && location.trim() !== '';
  }

  formatLocation(session: ReadingSessionResponse): string {
    const start = session.startLocation;
    const end = session.endLocation;

    if (!start || !end) return '-';

    if (session.bookType === 'AUDIOBOOK') {
      return `${start} → ${end}`;
    }

    if (this.isPageNumber(start)) {
      return `${this.t.translate('metadata.readingSessions.page')} ${start} → ${end}`;
    }

    return '-';
  }
}
