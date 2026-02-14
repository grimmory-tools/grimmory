import {inject, Injectable} from '@angular/core';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {BookService} from './book.service';
import {AGE_RATING_OPTIONS, CONTENT_RATING_LABELS, readStatusLabels} from '../components/book-browser/book-filter/book-filter.config';
import {ReadStatus} from '../model/book.model';
import {ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {finalize} from 'rxjs';
import {LoadingService} from '../../../core/services/loading.service';
import {User} from '../../settings/user-management/user.service';
import {APIException} from '../../../shared/models/api-exception.model';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslocoService} from '@jsverse/transloco';

@Injectable({
  providedIn: 'root'
})
export class BookMenuService {

  confirmationService = inject(ConfirmationService);
  messageService = inject(MessageService);
  bookService = inject(BookService);
  loadingService = inject(LoadingService);
  private readonly t = inject(TranslocoService);

  getMetadataMenuItems(
    autoFetchMetadata: () => void,
    fetchMetadata: () => void,
    bulkEditMetadata: () => void,
    multiBookEditMetadata: () => void,
    regenerateCovers: () => void,
    generateCustomCovers: () => void,
    user: User | null): MenuItem[] {

    const permissions = user?.permissions;
    const items: MenuItem[] = [];

    if (permissions?.canBulkAutoFetchMetadata) {
      items.push({
        label: this.t.translate('book.menuService.menu.autoFetchMetadata'),
        icon: 'pi pi-bolt',
        command: autoFetchMetadata
      });
    }

    if (permissions?.canBulkCustomFetchMetadata) {
      items.push({
        label: this.t.translate('book.menuService.menu.customFetchMetadata'),
        icon: 'pi pi-sync',
        command: fetchMetadata
      });
    }

    if (permissions?.canBulkEditMetadata) {
      items.push({
        label: this.t.translate('book.menuService.menu.bulkMetadataEditor'),
        icon: 'pi pi-table',
        command: bulkEditMetadata
      });
      items.push({
        label: this.t.translate('book.menuService.menu.multiBookMetadataEditor'),
        icon: 'pi pi-clone',
        command: multiBookEditMetadata
      });
    }

    if (permissions?.canBulkRegenerateCover) {
      items.push({
        label: this.t.translate('book.menuService.menu.regenerateCovers'),
        icon: 'pi pi-image',
        command: regenerateCovers
      });
      items.push({
        label: this.t.translate('book.menuService.menu.generateCustomCovers'),
        icon: 'pi pi-palette',
        command: generateCustomCovers
      });
    }

    return items;
  }

  getMoreActionsMenu(selectedBooks: Set<number>, user: User | null): MenuItem[] {
    const count = selectedBooks.size;
    const permissions = user?.permissions;
    const items: MenuItem[] = [];

    if (permissions?.canBulkResetBookReadStatus) {
      items.push({
        label: this.t.translate('book.menuService.menu.updateReadStatus'),
        icon: 'pi pi-book',
        items: Object.entries(readStatusLabels).map(([status, label]) => ({
          label,
          command: () => {
            this.confirmationService.confirm({
              message: this.t.translate('book.menuService.confirm.readStatusMessage', {count, label}),
              header: this.t.translate('book.menuService.confirm.readStatusHeader'),
              icon: 'pi pi-exclamation-triangle',
              acceptLabel: this.t.translate('common.yes'),
              rejectLabel: this.t.translate('common.no'),
              acceptButtonProps: {
                label: this.t.translate('common.yes'),
                severity: 'success'
              },
              rejectButtonProps: {
                label: this.t.translate('common.no'),
                severity: 'secondary'
              },
              accept: () => {
                const loader = this.loadingService.show(this.t.translate('book.menuService.loading.updatingReadStatus', {count}));

                this.bookService.updateBookReadStatus(Array.from(selectedBooks), status as ReadStatus)
                  .pipe(finalize(() => this.loadingService.hide(loader)))
                  .subscribe({
                    next: () => {
                      this.messageService.add({
                        severity: 'success',
                        summary: this.t.translate('book.menuService.toast.readStatusUpdatedSummary'),
                        detail: this.t.translate('book.menuService.toast.readStatusUpdatedDetail', {label}),
                        life: 2000
                      });
                    },
                    error: (err: HttpErrorResponse) => {
                      const apiError = err.error as APIException;
                      this.messageService.add({
                        severity: 'error',
                        summary: this.t.translate('book.menuService.toast.updateFailedSummary'),
                        detail: apiError?.message || this.t.translate('book.menuService.toast.readStatusFailedDetail'),
                        life: 3000
                      });
                    }
                  });
              }
            });
          }
        }))
      });
    }

    if (permissions?.canBulkEditMetadata) {
      items.push({
        label: this.t.translate('book.menuService.menu.setAgeRating'),
        icon: 'pi pi-user',
        items: [
          ...AGE_RATING_OPTIONS.map(option => ({
            label: option.label,
            command: () => {
              this.confirmationService.confirm({
                message: this.t.translate('book.menuService.confirm.ageRatingMessage', {label: option.label, count}),
                header: this.t.translate('book.menuService.confirm.ageRatingHeader'),
                icon: 'pi pi-exclamation-triangle',
                acceptLabel: this.t.translate('common.yes'),
                rejectLabel: this.t.translate('common.no'),
                accept: () => {
                  const loader = this.loadingService.show(this.t.translate('book.menuService.loading.settingAgeRating', {count}));
                  this.bookService.updateBooksMetadata({
                    bookIds: Array.from(selectedBooks),
                    ageRating: option.id
                  }).pipe(finalize(() => this.loadingService.hide(loader)))
                    .subscribe({
                      next: () => {
                        this.messageService.add({
                          severity: 'success',
                          summary: this.t.translate('book.menuService.toast.ageRatingUpdatedSummary'),
                          detail: this.t.translate('book.menuService.toast.ageRatingUpdatedDetail', {label: option.label}),
                          life: 2000
                        });
                      },
                      error: (err: HttpErrorResponse) => {
                        const apiError = err.error as APIException;
                        this.messageService.add({
                          severity: 'error',
                          summary: this.t.translate('book.menuService.toast.updateFailedSummary'),
                          detail: apiError?.message || this.t.translate('book.menuService.toast.ageRatingFailedDetail'),
                          life: 3000
                        });
                      }
                    });
                }
              });
            }
          })),
          {
            separator: true
          },
          {
            label: this.t.translate('book.menuService.menu.clearAgeRating'),
            icon: 'pi pi-times',
            command: () => {
              this.confirmationService.confirm({
                message: this.t.translate('book.menuService.confirm.clearAgeRatingMessage', {count}),
                header: this.t.translate('book.menuService.confirm.clearAgeRatingHeader'),
                icon: 'pi pi-exclamation-triangle',
                acceptLabel: this.t.translate('common.yes'),
                rejectLabel: this.t.translate('common.no'),
                accept: () => {
                  const loader = this.loadingService.show(this.t.translate('book.menuService.loading.clearingAgeRating', {count}));
                  this.bookService.updateBooksMetadata({
                    bookIds: Array.from(selectedBooks),
                    clearAgeRating: true
                  }).pipe(finalize(() => this.loadingService.hide(loader)))
                    .subscribe({
                      next: () => {
                        this.messageService.add({
                          severity: 'success',
                          summary: this.t.translate('book.menuService.toast.ageRatingClearedSummary'),
                          detail: this.t.translate('book.menuService.toast.ageRatingClearedDetail'),
                          life: 2000
                        });
                      },
                      error: (err: HttpErrorResponse) => {
                        const apiError = err.error as APIException;
                        this.messageService.add({
                          severity: 'error',
                          summary: this.t.translate('book.menuService.toast.updateFailedSummary'),
                          detail: apiError?.message || this.t.translate('book.menuService.toast.clearAgeRatingFailedDetail'),
                          life: 3000
                        });
                      }
                    });
                }
              });
            }
          }
        ]
      });

      items.push({
        label: this.t.translate('book.menuService.menu.setContentRating'),
        icon: 'pi pi-shield',
        items: [
          ...Object.entries(CONTENT_RATING_LABELS).map(([value, label]) => ({
            label: label,
            command: () => {
              this.confirmationService.confirm({
                message: this.t.translate('book.menuService.confirm.contentRatingMessage', {label, count}),
                header: this.t.translate('book.menuService.confirm.contentRatingHeader'),
                icon: 'pi pi-exclamation-triangle',
                acceptLabel: this.t.translate('common.yes'),
                rejectLabel: this.t.translate('common.no'),
                accept: () => {
                  const loader = this.loadingService.show(this.t.translate('book.menuService.loading.settingContentRating', {count}));
                  this.bookService.updateBooksMetadata({
                    bookIds: Array.from(selectedBooks),
                    contentRating: value
                  }).pipe(finalize(() => this.loadingService.hide(loader)))
                    .subscribe({
                      next: () => {
                        this.messageService.add({
                          severity: 'success',
                          summary: this.t.translate('book.menuService.toast.contentRatingUpdatedSummary'),
                          detail: this.t.translate('book.menuService.toast.contentRatingUpdatedDetail', {label}),
                          life: 2000
                        });
                      },
                      error: (err: HttpErrorResponse) => {
                        const apiError = err.error as APIException;
                        this.messageService.add({
                          severity: 'error',
                          summary: this.t.translate('book.menuService.toast.updateFailedSummary'),
                          detail: apiError?.message || this.t.translate('book.menuService.toast.contentRatingFailedDetail'),
                          life: 3000
                        });
                      }
                    });
                }
              });
            }
          })),
          {
            separator: true
          },
          {
            label: this.t.translate('book.menuService.menu.clearContentRating'),
            icon: 'pi pi-times',
            command: () => {
              this.confirmationService.confirm({
                message: this.t.translate('book.menuService.confirm.clearContentRatingMessage', {count}),
                header: this.t.translate('book.menuService.confirm.clearContentRatingHeader'),
                icon: 'pi pi-exclamation-triangle',
                acceptLabel: this.t.translate('common.yes'),
                rejectLabel: this.t.translate('common.no'),
                accept: () => {
                  const loader = this.loadingService.show(this.t.translate('book.menuService.loading.clearingContentRating', {count}));
                  this.bookService.updateBooksMetadata({
                    bookIds: Array.from(selectedBooks),
                    clearContentRating: true
                  }).pipe(finalize(() => this.loadingService.hide(loader)))
                    .subscribe({
                      next: () => {
                        this.messageService.add({
                          severity: 'success',
                          summary: this.t.translate('book.menuService.toast.contentRatingClearedSummary'),
                          detail: this.t.translate('book.menuService.toast.contentRatingClearedDetail'),
                          life: 2000
                        });
                      },
                      error: (err: HttpErrorResponse) => {
                        const apiError = err.error as APIException;
                        this.messageService.add({
                          severity: 'error',
                          summary: this.t.translate('book.menuService.toast.updateFailedSummary'),
                          detail: apiError?.message || this.t.translate('book.menuService.toast.clearContentRatingFailedDetail'),
                          life: 3000
                        });
                      }
                    });
                }
              });
            }
          }
        ]
      });
    }

    // Shelf Actions
    if (permissions?.canManageLibrary || permissions?.admin) { // Assuming these permissions cover shelf management for books
       items.push({
         label: this.t.translate('book.menuService.menu.removeFromAllShelves'),
         icon: 'pi pi-bookmark-fill', // Or bookmark-slash
         command: () => {
           this.confirmationService.confirm({
             message: this.t.translate('book.menuService.confirm.unshelveMessage', {count}),
             header: this.t.translate('book.menuService.confirm.unshelveHeader'),
             icon: 'pi pi-exclamation-triangle',
             acceptLabel: this.t.translate('common.yes'),
             rejectLabel: this.t.translate('common.no'),
             accept: () => {
               const loader = this.loadingService.show(this.t.translate('book.menuService.loading.removingFromShelves', {count}));
               const books = this.bookService.getBooksByIdsFromState(Array.from(selectedBooks));
               const allShelfIds = new Set<number>();
               books.forEach(b => b.shelves?.forEach(s => {
                 if (s.id) allShelfIds.add(s.id);
               }));

               if (allShelfIds.size === 0) {
                 this.loadingService.hide(loader);
                 this.messageService.add({ severity: 'info', summary: this.t.translate('common.info'), detail: this.t.translate('book.menuService.toast.noBooksOnShelvesDetail') });
                 return;
               }

               this.bookService.updateBookShelves(selectedBooks, new Set(), allShelfIds)
                 .pipe(finalize(() => this.loadingService.hide(loader)))
                 .subscribe({
                   next: () => {
                     this.messageService.add({severity: 'success', summary: this.t.translate('common.success'), detail: this.t.translate('book.menuService.toast.unshelveSuccessDetail')});
                   },
                   error: () => {
                     this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('book.menuService.toast.unshelveFailedDetail')});
                   }
                 });
             }
           });
         }
       });
    }

    if (permissions?.canBulkResetBookloreReadProgress) {
      items.push({
        label: this.t.translate('book.menuService.menu.resetBookloreProgress'),
        icon: 'pi pi-undo',
        command: () => {
          this.confirmationService.confirm({
            message: this.t.translate('book.menuService.confirm.resetBookloreMessage', {count}),
            header: this.t.translate('book.menuService.confirm.resetHeader'),
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: this.t.translate('common.yes'),
            rejectLabel: this.t.translate('common.no'),
            accept: () => {
              const loader = this.loadingService.show(this.t.translate('book.menuService.loading.resettingBookloreProgress', {count}));

              this.bookService.resetProgress(Array.from(selectedBooks), ResetProgressTypes.BOOKLORE)
                .pipe(finalize(() => this.loadingService.hide(loader)))
                .subscribe({
                  next: () => {
                    this.messageService.add({
                      severity: 'success',
                      summary: this.t.translate('book.menuService.toast.progressResetSummary'),
                      detail: this.t.translate('book.menuService.toast.bookloreProgressResetDetail'),
                      life: 1500
                    });
                  },
                  error: (err: HttpErrorResponse) => {
                    const apiError = err.error as APIException;
                    this.messageService.add({
                      severity: 'error',
                      summary: this.t.translate('book.menuService.toast.failedSummary'),
                      detail: apiError?.message || this.t.translate('book.menuService.toast.progressResetFailedDetail'),
                      life: 3000
                    });
                  }
                });
            }
          });
        }
      });
    }

    if (permissions?.canBulkResetKoReaderReadProgress) {
      items.push({
        label: this.t.translate('book.menuService.menu.resetKOReaderProgress'),
        icon: 'pi pi-undo',
        command: () => {
          this.confirmationService.confirm({
            message: this.t.translate('book.menuService.confirm.resetKOReaderMessage', {count}),
            header: this.t.translate('book.menuService.confirm.resetHeader'),
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: this.t.translate('common.yes'),
            rejectLabel: this.t.translate('common.no'),
            accept: () => {
              const loader = this.loadingService.show(this.t.translate('book.menuService.loading.resettingKOReaderProgress', {count}));

              this.bookService.resetProgress(Array.from(selectedBooks), ResetProgressTypes.KOREADER)
                .pipe(finalize(() => this.loadingService.hide(loader)))
                .subscribe({
                  next: () => {
                    this.messageService.add({
                      severity: 'success',
                      summary: this.t.translate('book.menuService.toast.progressResetSummary'),
                      detail: this.t.translate('book.menuService.toast.koreaderProgressResetDetail'),
                      life: 1500
                    });
                  },
                  error: (err: HttpErrorResponse) => {
                    const apiError = err.error as APIException;
                    this.messageService.add({
                      severity: 'error',
                      summary: this.t.translate('book.menuService.toast.failedSummary'),
                      detail: apiError?.message || this.t.translate('book.menuService.toast.progressResetFailedDetail'),
                      life: 3000
                    });
                  }
                });
            }
          });
        }
      });
    }

    return items;
  }
}
