import {inject, Injectable} from '@angular/core';
import {ConfirmationService, MessageService} from 'primeng/api';
import {MenuItem} from 'primeng/api';
import {BookService} from './book.service';
import {readStatusLabels} from '../components/book-browser/book-filter/book-filter.component';
import {ReadStatus} from '../model/book.model';
import {ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {finalize} from 'rxjs';
import {LoadingService} from '../../../core/services/loading.service';

@Injectable({
  providedIn: 'root'
})
export class BookMenuService {

  confirmationService = inject(ConfirmationService);
  messageService = inject(MessageService);
  bookService = inject(BookService);
  loadingService = inject(LoadingService);

  getMetadataMenuItems(
    autoFetchMetadata: () => void,
    fetchMetadata: () => void,
    bulkEditMetadata: () => void,
    multiBookEditMetadata: () => void,
    regenerateCovers: () => void): MenuItem[] {
    return [
      {
        label: 'Auto Fetch Metadata',
        icon: 'pi pi-bolt',
        command: autoFetchMetadata
      },
      {
        label: 'Custom Fetch Metadata',
        icon: 'pi pi-sync',
        command: fetchMetadata
      },
      {
        label: 'Bulk Metadata Editor',
        icon: 'pi pi-table',
        command: bulkEditMetadata
      },
      {
        label: 'Multi-Book Metadata Editor',
        icon: 'pi pi-clone',
        command: multiBookEditMetadata
      },
      {
        label: 'Regenerate Covers',
        icon: 'pi pi-image',
        command: regenerateCovers
      }
    ];
  }

  getTieredMenuItems(selectedBooks: Set<number>): MenuItem[] {
    const count = selectedBooks.size;

    return [
      {
        label: 'Update Read Status',
        icon: 'pi pi-book',
        items: Object.entries(readStatusLabels).map(([status, label]) => ({
          label,
          command: () => {
            this.confirmationService.confirm({
              message: `Are you sure you want to mark ${count} book(s) as "${label}"?`,
              header: 'Confirm Read Status Update',
              icon: 'pi pi-exclamation-triangle',
              acceptLabel: 'Yes',
              rejectLabel: 'No',
              accept: () => {
                const loader = this.loadingService.show(`Updating read status for ${count} book(s)...`);

                this.bookService.updateBookReadStatus(Array.from(selectedBooks), status as ReadStatus)
                  .pipe(finalize(() => this.loadingService.hide(loader)))
                  .subscribe({
                    next: () => {
                      this.messageService.add({
                        severity: 'success',
                        summary: 'Read Status Updated',
                        detail: `Marked as "${label}"`,
                        life: 2000
                      });
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Update Failed',
                        detail: 'Could not update read status.',
                        life: 3000
                      });
                    }
                  });
              }
            });
          }
        }))
      },
      {
        label: 'Reset Booklore Progress',
        icon: 'pi pi-undo',
        command: () => {
          this.confirmationService.confirm({
            message: `Are you sure you want to reset Booklore reading progress for ${count} book(s)?`,
            header: 'Confirm Reset',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Yes',
            rejectLabel: 'No',
            accept: () => {
              const loader = this.loadingService.show(`Resetting Booklore progress for ${count} book(s)...`);

              this.bookService.resetProgress(Array.from(selectedBooks), ResetProgressTypes.BOOKLORE)
                .pipe(finalize(() => this.loadingService.hide(loader)))
                .subscribe({
                  next: () => {
                    this.messageService.add({
                      severity: 'success',
                      summary: 'Progress Reset',
                      detail: 'Booklore reading progress has been reset.',
                      life: 1500
                    });
                  },
                  error: () => {
                    this.messageService.add({
                      severity: 'error',
                      summary: 'Failed',
                      detail: 'Could not reset progress.',
                      life: 1500
                    });
                  }
                });
            }
          });
        }
      },
      {
        label: 'Reset KOReader Progress',
        icon: 'pi pi-undo',
        command: () => {
          this.confirmationService.confirm({
            message: `Are you sure you want to reset KOReader reading progress for ${count} book(s)?`,
            header: 'Confirm Reset',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Yes',
            rejectLabel: 'No',
            accept: () => {
              const loader = this.loadingService.show(`Resetting KOReader progress for ${count} book(s)...`);

              this.bookService.resetProgress(Array.from(selectedBooks), ResetProgressTypes.KOREADER)
                .pipe(finalize(() => this.loadingService.hide(loader)))
                .subscribe({
                  next: () => {
                    this.messageService.add({
                      severity: 'success',
                      summary: 'Progress Reset',
                      detail: 'KOReader reading progress has been reset.',
                      life: 1500
                    });
                  },
                  error: () => {
                    this.messageService.add({
                      severity: 'error',
                      summary: 'Failed',
                      detail: 'Could not reset progress.',
                      life: 1500
                    });
                  }
                });
            }
          });
        }
      }
    ];
  }
}
