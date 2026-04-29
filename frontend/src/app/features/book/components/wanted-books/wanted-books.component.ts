import {ChangeDetectorRef, Component, inject, OnDestroy, OnInit} from '@angular/core';
import {DatePipe} from '@angular/common';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {Tag} from 'primeng/tag';
import {Toast} from 'primeng/toast';
import {Dialog} from 'primeng/dialog';
import {InputText} from 'primeng/inputtext';
import {Tooltip} from 'primeng/tooltip';
import {FormsModule} from '@angular/forms';
import {MessageService} from 'primeng/api';
import {Message} from '@stomp/stompjs';
import {Subscription} from 'rxjs';
import {AcquisitionService, WantedBook, JobHistoryItem, AddToWantedRequest} from '../../../../core/services/acquisition.service';
import {RxStompService} from '../../../../shared/websocket/rx-stomp.service';

@Component({
  selector: 'app-wanted-books',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    Button,
    TableModule,
    Tag,
    Toast,
    Dialog,
    InputText,
    Tooltip,
  ],
  providers: [MessageService],
  templateUrl: './wanted-books.component.html',
  styleUrl: './wanted-books.component.scss'
})
export class WantedBooksComponent implements OnInit, OnDestroy {

  private readonly acquisitionService = inject(AcquisitionService);
  private readonly messageService = inject(MessageService);
  private readonly rxStompService = inject(RxStompService);
  private readonly cdr = inject(ChangeDetectorRef);

  wantedBooks: WantedBook[] = [];
  loading = false;
  searchingIds = new Set<number>();
  expandedRows: Record<number, boolean> = {};
  jobHistory: Record<number, JobHistoryItem[]> = {};
  private wsSubscription?: Subscription;

  showManualAddDialog = false;
  manualTitle = '';
  manualAuthor = '';
  manualIsbn13 = '';
  manualIsbn10 = '';

  ngOnInit(): void {
    this.loadWantedBooks();
    this.wsSubscription = this.rxStompService.watch('/topic/acquisition').subscribe((message: Message) => {
      try {
        const notification = JSON.parse(message.body);
        if (notification.wantedBookId) {
          const book = this.wantedBooks.find(b => b.id === notification.wantedBookId);
          if (book) {
            book.status = notification.status;
            if (notification.lastCheckedAt) {
              book.lastCheckedAt = notification.lastCheckedAt;
            }
            if (notification.status === 'DOWNLOADED') {
              this.messageService.add({severity: 'success', summary: 'Downloaded', detail: `Book downloaded: ${book.title}`});
            }
            this.cdr.detectChanges();
          }
        }
      } catch {
        // ignore malformed messages
      }
    });
  }

  ngOnDestroy(): void {
    this.wsSubscription?.unsubscribe();
  }

  loadWantedBooks(): void {
    this.loading = true;
    this.acquisitionService.getWantedBooks().subscribe({
      next: (data) => {
        this.wantedBooks = data;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load wanted books'});
      }
    });
  }

  triggerSearch(book: WantedBook): void {
    this.searchingIds.add(book.id);
    this.acquisitionService.triggerSearch(book.id).subscribe({
      next: () => {
        this.searchingIds.delete(book.id);
        this.cdr.detectChanges();
        this.messageService.add({severity: 'info', summary: 'Searching', detail: `Searching for "${book.title}"...`});
      },
      error: () => {
        this.searchingIds.delete(book.id);
        this.cdr.detectChanges();
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to trigger search'});
      }
    });
  }

  removeWanted(book: WantedBook): void {
    if (!confirm(`Remove "${book.title}" from wanted list?`)) return;
    this.acquisitionService.removeWanted(book.id).subscribe({
      next: () => {
        this.wantedBooks = this.wantedBooks.filter(b => b.id !== book.id);
        this.cdr.detectChanges();
        this.messageService.add({severity: 'success', summary: 'Removed', detail: `"${book.title}" removed`});
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to remove book'});
      }
    });
  }

  runAll(): void {
    this.acquisitionService.runAllNow().subscribe({
      next: () => this.messageService.add({severity: 'info', summary: 'Running', detail: 'Batch job triggered for all wanted books'}),
      error: () => this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to trigger batch job'})
    });
  }

  statusLabel(status: WantedBook['status']): string {
    const labels: Record<WantedBook['status'], string> = {
      WANTED: 'Wanted', SEARCHING: 'Searching', FOUND: 'Found',
      DOWNLOADING: 'Downloading', DOWNLOADED: 'Downloaded', IMPORTED: 'Imported',
      NOT_FOUND: 'Not Found', FAILED: 'Failed', FAILED_PERMANENT: 'Permanent Fail'
    };
    return labels[status] ?? status;
  }

  statusSeverity(status: WantedBook['status']): 'secondary' | 'info' | 'warn' | 'success' | 'danger' {
    switch (status) {
      case 'WANTED': return 'secondary';
      case 'SEARCHING': return 'info';
      case 'FOUND':
      case 'DOWNLOADING': return 'warn';
      case 'DOWNLOADED':
      case 'IMPORTED': return 'success';
      case 'NOT_FOUND': return 'warn';
      case 'FAILED':
      case 'FAILED_PERMANENT': return 'danger';
      default: return 'secondary';
    }
  }

  isSearching(book: WantedBook): boolean {
    return this.searchingIds.has(book.id);
  }

  toggleHistory(book: WantedBook): void {
    if (this.expandedRows[book.id]) {
      delete this.expandedRows[book.id];
    } else {
      this.expandedRows[book.id] = true;
      if (!this.jobHistory[book.id]) {
        this.loadHistory(book);
      }
    }
  }

  loadHistory(book: WantedBook): void {
    this.acquisitionService.getJobHistory(book.id).subscribe({
      next: (history) => {
        this.jobHistory[book.id] = history;
        this.cdr.detectChanges();
      },
      error: () => {
        this.jobHistory[book.id] = [];
        this.cdr.detectChanges();
      }
    });
  }

  historySeverity(status: string): 'success' | 'warn' | 'secondary' {
    switch (status) {
      case 'SENT': return 'success';
      case 'FAILED': return 'warn';
      default: return 'secondary';
    }
  }

  openManualAddDialog(): void {
    this.manualTitle = '';
    this.manualAuthor = '';
    this.manualIsbn13 = '';
    this.manualIsbn10 = '';
    this.showManualAddDialog = true;
  }

  submitManualAdd(): void {
    if (!this.manualTitle.trim()) {
      this.messageService.add({severity: 'warn', summary: 'Validation', detail: 'Title is required'});
      return;
    }
    const req: AddToWantedRequest = {
      title: this.manualTitle.trim(),
      author: this.manualAuthor.trim() || undefined,
      isbn13: this.manualIsbn13.trim() || undefined,
      isbn10: this.manualIsbn10.trim() || undefined,
      provider: 'Manual'
    };
    this.acquisitionService.addToWanted(req).subscribe({
      next: (book) => {
        this.showManualAddDialog = false;
        this.wantedBooks = [book, ...this.wantedBooks];
        this.cdr.detectChanges();
        this.messageService.add({severity: 'success', summary: 'Added', detail: `"${book.title}" added to wanted list`});
      },
      error: (err) => {
        const msg = err.status === 409 ? 'Already in wanted list' : 'Failed to add to wanted list';
        this.messageService.add({severity: err.status === 409 ? 'info' : 'error', summary: err.status === 409 ? 'Info' : 'Error', detail: msg});
      }
    });
  }
}
