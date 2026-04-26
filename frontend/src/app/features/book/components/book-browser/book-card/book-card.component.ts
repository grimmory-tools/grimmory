import {ChangeDetectionStrategy, Component, computed, inject, input, output, signal} from '@angular/core';
import {Tooltip} from 'primeng/tooltip';
import {AdditionalFile, Book, BookType, ReadStatus} from '../../../model/book.model';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {BookService} from '../../../service/book.service';
import {BookFileService} from '../../../service/book-file.service';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {CheckboxChangeEvent, Checkbox} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {MetadataRefreshType} from '../../../../metadata/model/request/metadata-refresh-type.enum';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {CoverPlaceholderComponent} from '../../../../../shared/components/cover-generator/cover-generator.component';
import {NgClass} from '@angular/common';
import {UserService} from '../../../../settings/user-management/user.service';
import {EmailService} from '../../../../settings/email-v2/email.service';
import {TieredMenu} from 'primeng/tieredmenu';
import {Router, RouterLink} from '@angular/router';
import {readStatusLabels} from '../book-filter/book-filter.config';
import {ResetProgressTypes} from '../../../../../shared/constants/reset-progress-type';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {BookDialogHelperService} from '../book-dialog-helper.service';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {BookNavigationService} from '../../../service/book-navigation.service';
import {BookCardOverlayPreferenceService} from '../book-card-overlay-preference.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {QueryClient} from '@tanstack/angular-query-experimental';

@Component({
  selector: 'app-book-card',
  templateUrl: './book-card.component.html',
  styleUrls: ['./book-card.component.scss'],
  imports: [Checkbox, FormsModule, NgClass, TieredMenu, Tooltip, RouterLink, TranslocoPipe, CoverPlaceholderComponent],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookCardComponent {

  // --- Inputs ---
  readonly book = input.required<Book>();
  readonly index = input.required<number>();
  readonly isCheckboxEnabled = input(false);
  readonly onBookSelect = input<((book: Book, selected: boolean) => void) | undefined>();
  readonly isSelected = input(false);
  readonly bottomBarHidden = input(false);
  readonly seriesViewEnabled = input(false);
  readonly isSeriesCollapsed = input(false);
  readonly overlayPreferenceService = input<BookCardOverlayPreferenceService | undefined>();
  private readonly overlayService = inject(BookCardOverlayPreferenceService);
  readonly showBookTypePill = computed(() => (this.overlayPreferenceService() ?? this.overlayService).showBookTypePill());
  readonly forceEbookMode = input(false);
  readonly useSquareCovers = input(false);
  readonly priorityImage = input(false);

  readonly imageLoading = computed(() => this.priorityImage() ? 'eager' : 'lazy');
  readonly imageFetchPriority = computed(() => this.priorityImage() ? 'high' : 'low');

  readonly checkboxClick = output<{ index: number; book: Book; selected: boolean; shiftKey: boolean }>();
  readonly menuToggled = output<boolean>();

  readonly items = signal<MenuItem[] | undefined>(undefined);
  readonly readStatusMenuItems = signal<MenuItem[]>([]);
  readonly isSubMenuLoading = signal(false);
  private readonly additionalFilesLoaded = signal(false);
  /** Tracks the book id for which the menu was last built, to auto-rebuild on virtual-scroller reuse. */
  private menuBookId: number | undefined;
  private menuInitialized = false;

  private bookService = inject(BookService);
  private bookFileService = inject(BookFileService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private taskHelperService = inject(TaskHelperService);
  private userService = inject(UserService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private urlHelper = inject(UrlHelperService);
  private confirmationService = inject(ConfirmationService);
  private bookDialogHelperService = inject(BookDialogHelperService);
  private bookNavigationService = inject(BookNavigationService);
  private appSettingsService = inject(AppSettingsService);
  private readonly t = inject(TranslocoService);
  private queryClient = inject(QueryClient);
  protected readStatusHelper = inject(ReadStatusHelper);

  private readonly currentUser = computed(() => this.userService.currentUser());
  private readonly metadataCenterViewMode = computed(() => this.currentUser()?.userSettings?.metadataCenterViewMode ?? 'route' as 'route' | 'dialog');
  private readonly diskType = computed(() => this.appSettingsService.appSettings()?.diskType ?? 'LOCAL');



  readonly progressPercentage = computed(() => {
    const b = this.book();
    return b.epubProgress?.percentage ?? b.pdfProgress?.percentage ?? b.cbxProgress?.percentage ?? null;
  });

  readonly koProgressPercentage = computed(() => this.book().koreaderProgress?.percentage ?? null);
  readonly koboProgressPercentage = computed(() => this.book().koboProgress?.percentage ?? null);

  readonly hasProgress = computed(() =>
    this.progressPercentage() !== null || this.koProgressPercentage() !== null || this.koboProgressPercentage() !== null
  );

  readonly isSeriesViewActive = computed(() =>
    this.seriesViewEnabled() && !!this.book().seriesCount && this.book().seriesCount! >= 1
  );

  readonly displayTitle = computed(() =>
    (this.isSeriesCollapsed() && this.book().metadata?.seriesName)
      ? this.book().metadata?.seriesName
      : this.book().metadata?.title
  );

  readonly isAudiobook = computed(() =>
    this.book().primaryFile?.bookType === 'AUDIOBOOK' && !this.forceEbookMode()
  );

  readonly hasAudiobookFormat = computed(() => {
    const primaryFile = this.book().primaryFile;
    const alternativeFormats = this.book().alternativeFormats ?? [];
    return primaryFile?.bookType === 'AUDIOBOOK' || alternativeFormats.some(file => file.bookType === 'AUDIOBOOK');
  });

  readonly coverImageUrl = computed(() =>
    (this.isAudiobook() || (this.useSquareCovers() && this.hasAudiobookFormat()))
      ? this.urlHelper.getAudiobookThumbnailUrl(this.book().id, this.book().metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(this.book().id, this.book().metadata?.coverUpdatedOn)
  );

  readonly readStatusIcon = computed(() => this.readStatusHelper.getReadStatusIcon(this.book().readStatus));
  readonly readStatusClass = computed(() => this.readStatusHelper.getReadStatusClass(this.book().readStatus));
  readonly readStatusTooltip = computed(() => this.readStatusHelper.getReadStatusTooltip(this.book().readStatus));

  readonly seriesCountTooltip = computed(() =>
    this.t.translate('book.card.alt.seriesCollapsed', {count: this.book().seriesCount})
  );

  readonly titleTooltip = computed(() =>
    this.t.translate('book.card.alt.titleTooltip', {title: this.displayTitle()})
  );

  readonly progressTooltip = computed(() => {
    const parts: string[] = [];
    const p = this.progressPercentage();
    const ko = this.koProgressPercentage();
    const kobo = this.koboProgressPercentage();
    if (p !== null) parts.push(`${p}% (Grimmory)`);
    if (ko !== null) parts.push(`${ko}% (KOReader)`);
    if (kobo !== null) parts.push(`${kobo}% (Kobo)`);
    return parts.join(' | ');
  });

  readonly isContinueReading = computed(() => {
    const max = Math.max(
      this.progressPercentage() ?? 0,
      this.koProgressPercentage() ?? 0,
      this.koboProgressPercentage() ?? 0
    );
    return max > 0 && max < 100;
  });

  readonly readButtonIcon = computed(() => {
    if (this.isAudiobook()) {
      return this.isContinueReading() ? 'pi pi-forward' : 'pi pi-play';
    }
    return this.isContinueReading() ? 'pi pi-forward' : 'pi pi-book';
  });

  readonly displayFormat = computed<string | null>(() => {
    const b = this.book();
    if (!b.primaryFile) return 'PHY';
    if (this.forceEbookMode() && b.primaryFile.bookType === 'AUDIOBOOK') {
      const ebookType = this.getEbookType(b);
      if (ebookType) return ebookType;
    }
    const ext = b.primaryFile.extension;
    if (ext) return ext.toUpperCase();
    return this.getFileExtension(b.primaryFile.filePath);
  });

  readonly hasDigitalFile = computed(() => !!this.book().primaryFile);

  readonly readingUrl = computed(() => this.urlHelper.getBookPrimaryReadingUrl(this.book()));

  private buildReadStatusMenuItems(): void {
    this.readStatusMenuItems.set(Object.entries(readStatusLabels).map(([status, label]) => ({
      label,
      command: () => {
        this.bookService.updateBookReadStatus(this.book().id, status as ReadStatus).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.card.toast.readStatusUpdatedSummary'),
              detail: this.t.translate('book.card.toast.readStatusUpdatedDetail', {label}),
              life: 2000
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.card.toast.readStatusFailedSummary'),
              detail: this.t.translate('book.card.toast.readStatusFailedDetail'),
              life: 3000
            });
          }
        });
      }
    })));
  }

  toggleReadStatusMenu(event: Event, menu: TieredMenu): void {
    event.stopPropagation();
    if (this.readStatusMenuItems().length === 0) {
      this.buildReadStatusMenuItems();
    }
    menu.toggle(event);
  }

  readBook(book: Book): void {
    if (this.forceEbookMode() && book.primaryFile?.bookType === 'AUDIOBOOK') {
      const ebookType = this.getEbookType(book);
      if (ebookType) {
        this.bookService.readBook(book.id, undefined, ebookType);
        return;
      }
    }
    this.bookService.readBook(book.id);
  }

  private getEbookType(book: Book): BookType | undefined {
    if (book.epubProgress) return 'EPUB';
    if (book.pdfProgress) return 'PDF';
    if (book.cbxProgress) return 'CBX';
    const alternativeFormat = book.alternativeFormats?.find(f =>
      f.bookType && ['EPUB', 'PDF', 'CBX', 'FB2', 'MOBI', 'AZW3'].includes(f.bookType)
    );
    return alternativeFormat?.bookType;
  }

  onMenuShow(): void {
    this.menuToggled.emit(true);
  }

  onMenuHide(): void {
    this.menuToggled.emit(false);
  }

  onMenuToggle(event: Event, menu: TieredMenu): void {
    const currentBookId = this.book().id;

    if (!this.menuInitialized || this.menuBookId !== currentBookId) {
      this.menuInitialized = true;
      this.menuBookId = currentBookId;
      this.additionalFilesLoaded.set(false);
      this.initMenu();
    }

    menu.toggle(event);

    if (!this.additionalFilesLoaded() && !this.isSubMenuLoading() && this.needsAdditionalFilesData()) {
      this.isSubMenuLoading.set(true);
      const requestedBookId = currentBookId;
      void this.queryClient.fetchQuery(this.bookService.bookDetailQueryOptions(requestedBookId, true))
        .then((fetchedBook) => {
          if (this.book().id !== requestedBookId) return;
          this.additionalFilesLoaded.set(true);
          this.initMenu(fetchedBook);
        })
        .finally(() => {
          if (this.book().id === requestedBookId) {
            this.isSubMenuLoading.set(false);
          }
        });
    }
  }

  private needsAdditionalFilesData(): boolean {
    if (this.additionalFilesLoaded()) return false;
    const b = this.book();
    const hasNoAlternativeFormats = !b.alternativeFormats || b.alternativeFormats.length === 0;
    const hasNoSupplementaryFiles = !b.supplementaryFiles || b.supplementaryFiles.length === 0;
    const canDownload = !!this.currentUser()?.permissions.canDownload;
    const canDeleteBook = !!this.currentUser()?.permissions.canDeleteBook;
    return (canDownload || canDeleteBook) && hasNoAlternativeFormats && hasNoSupplementaryFiles;
  }

  private initMenu(bookOverride?: Book) {
    const b = bookOverride ?? this.book();
    this.items.set([
      {
        label: this.t.translate('book.card.menu.assignShelf'),
        icon: 'pi pi-folder',
        command: () => this.openShelfDialog()
      },
      {
        label: this.t.translate('book.card.menu.viewDetails'),
        icon: 'pi pi-info-circle',
        command: () => {
          setTimeout(() => {
            this.openBookInfo(this.book());
          }, 150);
        },
      },
      ...this.getPermissionBasedMenuItems(b),
      ...this.moreMenuItems(),
    ]);
  }

  private getPermissionBasedMenuItems(b: Book): MenuItem[] {
    const items: MenuItem[] = [];

    if (this.currentUser()?.permissions.canDownload) {
      const hasAdditional = (b.alternativeFormats && b.alternativeFormats.length > 0) ||
        (b.supplementaryFiles && b.supplementaryFiles.length > 0);

      if (hasAdditional) {
        items.push({
          label: this.t.translate('book.card.menu.download'),
          icon: 'pi pi-download',
          items: this.getDownloadMenuItems(b)
        });
      } else if (this.additionalFilesLoaded()) {
        items.push({
          label: this.t.translate('book.card.menu.download'),
          icon: 'pi pi-download',
          command: () => this.bookFileService.downloadFile(this.book())
        });
      } else {
        items.push({
          label: this.t.translate('book.card.menu.download'),
          icon: this.isSubMenuLoading() ? 'pi pi-spin pi-spinner' : 'pi pi-download',
          items: [{label: this.t.translate('book.card.menu.loading'), disabled: true}]
        });
      }
    }

    if (this.currentUser()?.permissions.canDeleteBook) {
      const hasAdditional = (b.alternativeFormats && b.alternativeFormats.length > 0) ||
        (b.supplementaryFiles && b.supplementaryFiles.length > 0);

      if (hasAdditional) {
        items.push({
          label: this.t.translate('book.card.menu.delete'),
          icon: 'pi pi-trash',
          items: this.getDeleteMenuItems(b)
        });
      } else if (this.additionalFilesLoaded()) {
        items.push({
          label: this.t.translate('book.card.menu.delete'),
          icon: 'pi pi-trash',
          command: () => {
            this.confirmationService.confirm({
              message: this.t.translate('book.card.confirm.deleteBookMessage', {title: this.book().metadata?.title}),
              header: this.t.translate('book.card.confirm.deleteBookHeader'),
              icon: 'pi pi-exclamation-triangle',
              acceptIcon: 'pi pi-trash',
              rejectIcon: 'pi pi-times',
              acceptLabel: this.t.translate('common.delete'),
              rejectLabel: this.t.translate('common.cancel'),
              acceptButtonStyleClass: 'p-button-danger',
              rejectButtonStyleClass: 'p-button-outlined',
              accept: () => {
                this.bookService.deleteBooks(new Set([this.book().id])).subscribe();
              }
            });
          }
        });
      } else {
        items.push({
          label: this.t.translate('book.card.menu.delete'),
          icon: this.isSubMenuLoading() ? 'pi pi-spin pi-spinner' : 'pi pi-trash',
          items: [{label: this.t.translate('book.card.menu.loading'), disabled: true}]
        });
      }
    }

    if (this.currentUser()?.permissions.canEmailBook) {
      items.push({
        label: this.t.translate('book.card.menu.emailBook'),
        icon: 'pi pi-envelope',
        items: [{
          label: this.t.translate('book.card.menu.quickSend'),
          icon: 'pi pi-envelope',
          command: () => {
            const doSend = () => {
              this.emailService.emailBookQuick(this.book().id).subscribe({
                next: () => {
                  this.messageService.add({
                    severity: 'info',
                    summary: this.t.translate('common.success'),
                    detail: this.t.translate('book.card.toast.quickSendSuccessDetail'),
                  });
                },
                error: (err: {error?: {message?: string}}) => {
                  const errorMessage = err?.error?.message || this.t.translate('book.card.toast.quickSendErrorDetail');
                  this.messageService.add({
                    severity: 'error',
                    summary: this.t.translate('common.error'),
                    detail: errorMessage,
                  });
                },
              });
            };

            const currentBook = this.book();
            if (currentBook.primaryFile?.fileSizeKb && currentBook.primaryFile.fileSizeKb > 25 * 1024) {
              this.confirmationService.confirm({
                message: this.t.translate('book.card.confirm.largeFileMessage'),
                header: this.t.translate('book.card.confirm.largeFileHeader'),
                icon: 'pi pi-exclamation-triangle',
                acceptLabel: this.t.translate('book.card.confirm.sendAnyway'),
                rejectLabel: this.t.translate('common.cancel'),
                acceptButtonProps: {severity: 'warn'},
                rejectButtonProps: {severity: 'secondary'},
                accept: doSend,
              });
            } else {
              doSend();
            }
          }
        },
          {
            label: this.t.translate('book.card.menu.customSend'),
            icon: 'pi pi-envelope',
            command: () => this.bookDialogHelperService.openCustomSendDialog(this.book())
          }
        ]
      });
    }

    if (this.currentUser()?.permissions.canEditMetadata) {
      items.push({
        label: this.t.translate('book.card.menu.metadata'),
        icon: 'pi pi-database',
        items: [
          {
            label: this.t.translate('book.card.menu.searchMetadata'),
            icon: 'pi pi-sparkles',
            command: () => {
              setTimeout(() => {
                this.router.navigate(['/book', this.book().id], {queryParams: {tab: 'match'}});
              }, 150);
            },
          },
          {
            label: this.t.translate('book.card.menu.autoFetch'),
            icon: 'pi pi-bolt',
            command: () => {
              this.taskHelperService.refreshMetadataTask({
                refreshType: MetadataRefreshType.BOOKS,
                bookIds: [this.book().id],
              }).subscribe();
            }
          },
          {
            label: this.t.translate('book.card.menu.customFetch'),
            icon: 'pi pi-sync',
            command: () => this.bookDialogHelperService.openMetadataRefreshDialog(new Set([this.book().id])),
          },
          {
            label: this.t.translate('book.card.menu.regenerateCover'),
            icon: 'pi pi-image',
            command: () => {
              this.bookMetadataManageService.regenerateCover(this.book().id).subscribe({
                next: () => this.messageService.add({
                  severity: 'success',
                  summary: this.t.translate('common.success'),
                  detail: this.t.translate('book.card.toast.coverRegenSuccessDetail')
                }),
                error: (err: {error?: {message?: string}}) => this.messageService.add({
                  severity: 'error',
                  summary: this.t.translate('common.error'),
                  detail: err?.error?.message || this.t.translate('book.card.toast.coverRegenFailedDetail')
                })
              });
            }
          },
          {
            label: this.t.translate('book.card.menu.generateCustomCover'),
            icon: 'pi pi-palette',
            command: () => {
              this.bookMetadataManageService.generateCustomCover(this.book().id).subscribe({
                next: () => this.messageService.add({
                  severity: 'success',
                  summary: this.t.translate('common.success'),
                  detail: this.t.translate('book.card.toast.customCoverSuccessDetail')
                }),
                error: (err: {error?: {message?: string}}) => this.messageService.add({
                  severity: 'error',
                  summary: this.t.translate('common.error'),
                  detail: err?.error?.message || this.t.translate('book.card.toast.customCoverFailedDetail')
                })
              });
            }
          }
        ]
      });
    }

    return items;
  }

  private moreMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];
    const moreActions: MenuItem[] = [];

    if (this.currentUser()?.permissions.canMoveOrganizeFiles && this.diskType() === 'LOCAL') {
      moreActions.push({
        label: this.t.translate('book.card.menu.organizeFile'),
        icon: 'pi pi-arrows-h',
        command: () => this.bookDialogHelperService.openFileMoverDialog(new Set([this.book().id]))
      });
    }

    moreActions.push(
      {
        label: this.t.translate('book.card.menu.readStatus'),
        icon: 'pi pi-book',
        items: Object.entries(readStatusLabels).map(([status, label]) => ({
          label,
          command: () => {
            this.bookService.updateBookReadStatus(this.book().id, status as ReadStatus).subscribe({
              next: () => {
                this.messageService.add({
                  severity: 'success',
                  summary: this.t.translate('book.card.toast.readStatusUpdatedSummary'),
                  detail: this.t.translate('book.card.toast.readStatusUpdatedDetail', {label}),
                  life: 2000
                });
              },
              error: () => {
                this.messageService.add({
                  severity: 'error',
                  summary: this.t.translate('book.card.toast.readStatusFailedSummary'),
                  detail: this.t.translate('book.card.toast.readStatusFailedDetail'),
                  life: 3000
                });
              }
            });
          }
        }))
      },
      {
        label: this.t.translate('book.card.menu.resetGrimmoryProgress'),
        icon: 'pi pi-undo',
        command: () => {
          this.bookService.resetProgress(this.book().id, ResetProgressTypes.GRIMMORY).subscribe({
            next: () => {
              this.messageService.add({
                severity: 'success',
                summary: this.t.translate('book.card.toast.progressResetSummary'),
                detail: this.t.translate('book.card.toast.progressResetGrimmoryDetail'),
                life: 1500
              });
            },
            error: () => {
              this.messageService.add({
                severity: 'error',
                summary: this.t.translate('book.card.toast.progressResetFailedSummary'),
                detail: this.t.translate('book.card.toast.progressResetGrimmoryFailedDetail'),
                life: 1500
              });
            }
          });
        },
      },
      {
        label: this.t.translate('book.card.menu.resetKOReaderProgress'),
        icon: 'pi pi-undo',
        command: () => {
          this.bookService.resetProgress(this.book().id, ResetProgressTypes.KOREADER).subscribe({
            next: () => {
              this.messageService.add({
                severity: 'success',
                summary: this.t.translate('book.card.toast.progressResetSummary'),
                detail: this.t.translate('book.card.toast.progressResetKOReaderDetail'),
                life: 1500
              });
            },
            error: () => {
              this.messageService.add({
                severity: 'error',
                summary: this.t.translate('book.card.toast.progressResetFailedSummary'),
                detail: this.t.translate('book.card.toast.progressResetKOReaderFailedDetail'),
                life: 1500
              });
            }
          });
        },
      }
    );

    items.push({
      label: this.t.translate('book.card.menu.moreActions'),
      icon: 'pi pi-ellipsis-h',
      items: moreActions
    });

    return items;
  }

  private openShelfDialog(): void {
    this.bookDialogHelperService.openShelfAssignerDialog(this.book(), null);
  }

  openSeriesInfo(): void {
    const b = this.book();
    const seriesName = b.metadata?.seriesName;
    if (this.isSeriesCollapsed() && seriesName) {
      this.router.navigate(['/series', seriesName]);
    } else {
      this.openBookInfo(b);
    }
  }

  openBookInfo(book: Book): void {
    const allBookIds = this.bookNavigationService.availableBookIds();
    if (allBookIds.length > 0) {
      this.bookNavigationService.setNavigationContext(allBookIds, book.id);
    }

    if (this.metadataCenterViewMode() === 'route') {
      this.router.navigate(['/book', book.id], {
        queryParams: {tab: 'view'}
      });
    } else {
      this.bookDialogHelperService.openBookDetailsDialog(book.id);
    }
  }

  private getDownloadMenuItems(b: Book): MenuItem[] {
    const items: MenuItem[] = [];

    items.push({
      label: `${b.fileName || 'Book File'}`,
      icon: 'pi pi-file',
      command: () => this.bookFileService.downloadFile(this.book())
    });

    if (this.hasAdditionalFilesForBook(b)) {
      items.push({separator: true});
    }

    if (b.alternativeFormats && b.alternativeFormats.length > 0) {
      for (const format of b.alternativeFormats) {
        const extension = this.getFileExtension(format.filePath);
        items.push({
          label: `${format.fileName} (${this.getFileSizeInMB(format)})`,
          icon: this.getFileIcon(extension),
          command: () => this.downloadAdditionalFile(this.book(), format.id)
        });
      }
    }

    if (b.alternativeFormats && b.alternativeFormats.length > 0 &&
      b.supplementaryFiles && b.supplementaryFiles.length > 0) {
      items.push({separator: true});
    }

    if (b.supplementaryFiles && b.supplementaryFiles.length > 0) {
      for (const file of b.supplementaryFiles) {
        const extension = this.getFileExtension(file.filePath);
        items.push({
          label: `${file.fileName} (${this.getFileSizeInMB(file)})`,
          icon: this.getFileIcon(extension),
          command: () => this.downloadAdditionalFile(this.book(), file.id)
        });
      }
    }

    return items;
  }

  private getDeleteMenuItems(b: Book): MenuItem[] {
    const items: MenuItem[] = [];

    items.push({
      label: this.t.translate('book.card.menu.book'),
      icon: 'pi pi-book',
      command: () => {
        this.confirmationService.confirm({
          message: this.t.translate('book.card.confirm.deleteBookMessage', {title: this.book().metadata?.title}),
          header: this.t.translate('book.card.confirm.deleteBookHeader'),
          icon: 'pi pi-exclamation-triangle',
          acceptIcon: 'pi pi-trash',
          rejectIcon: 'pi pi-times',
          acceptLabel: this.t.translate('common.delete'),
          rejectLabel: this.t.translate('common.cancel'),
          acceptButtonStyleClass: 'p-button-danger',
          rejectButtonStyleClass: 'p-button-outlined',
          accept: () => {
            this.bookService.deleteBooks(new Set([this.book().id])).subscribe();
          }
        });
      }
    });

    if (this.hasAdditionalFilesForBook(b)) {
      items.push({separator: true});
    }

    if (b.alternativeFormats && b.alternativeFormats.length > 0) {
      for (const format of b.alternativeFormats) {
        const extension = this.getFileExtension(format.filePath);
        items.push({
          label: `${format.fileName} (${this.getFileSizeInMB(format)})`,
          icon: this.getFileIcon(extension),
          command: () => this.deleteAdditionalFile(this.book().id, format.id, format.fileName || 'file')
        });
      }
    }

    if (b.alternativeFormats && b.alternativeFormats.length > 0 &&
      b.supplementaryFiles && b.supplementaryFiles.length > 0) {
      items.push({separator: true});
    }

    if (b.supplementaryFiles && b.supplementaryFiles.length > 0) {
      for (const file of b.supplementaryFiles) {
        const extension = this.getFileExtension(file.filePath);
        items.push({
          label: `${file.fileName} (${this.getFileSizeInMB(file)})`,
          icon: this.getFileIcon(extension),
          command: () => this.deleteAdditionalFile(this.book().id, file.id, file.fileName || 'file')
        });
      }
    }

    return items;
  }

  private hasAdditionalFilesForBook(b: Book): boolean {
    return !!(b.alternativeFormats && b.alternativeFormats.length > 0) ||
      !!(b.supplementaryFiles && b.supplementaryFiles.length > 0);
  }

  private downloadAdditionalFile(book: Book, fileId: number): void {
    this.bookFileService.downloadAdditionalFile(book, fileId);
  }

  private deleteAdditionalFile(bookId: number, fileId: number, fileName: string): void {
    this.confirmationService.confirm({
      message: this.t.translate('book.card.confirm.deleteFileMessage', {fileName}),
      header: this.t.translate('book.card.confirm.deleteFileHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookFileService.deleteAdditionalFile(bookId, fileId).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('common.success'),
              detail: this.t.translate('book.card.toast.deleteFileSuccessDetail', {fileName})
            });
          },
          error: (error: {message?: string}) => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('common.error'),
              detail: this.t.translate('book.card.toast.deleteFileErrorDetail', {error: error.message || 'Unknown error'})
            });
          }
        });
      }
    });
  }

  private getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop()?.toUpperCase() || null;
  }

  private getFileIcon(fileType: string | null): string {
    if (!fileType) return 'pi pi-file';
    switch (fileType.toLowerCase()) {
      case 'pdf':
        return 'pi pi-file-pdf';
      case 'epub':
      case 'mobi':
      case 'azw3':
      case 'fb2':
        return 'pi pi-book';
      case 'cbz':
      case 'cbr':
      case 'cbx':
        return 'pi pi-image';
      case 'audiobook':
      case 'm4b':
      case 'm4a':
      case 'mp3':
      case 'opus':
        return 'pi pi-headphones';
      default:
        return 'pi pi-file';
    }
  }

  private getFileSizeInMB(fileInfo: AdditionalFile): string {
    const sizeKb = fileInfo?.fileSizeKb;
    return sizeKb != null ? `${(sizeKb / 1024).toFixed(2)} MB` : '-';
  }

  private lastMouseEvent: MouseEvent | null = null;

  captureMouseEvent(event: MouseEvent): void {
    this.lastMouseEvent = event;
  }

  onCardClick(event: Event): void {
    if (!(event instanceof MouseEvent || event instanceof KeyboardEvent) || !event.ctrlKey) {
      return;
    }

    this.toggleCardSelection(!this.isSelected());
  }

  toggleCardSelection(selected: boolean): void {
    if (!this.isCheckboxEnabled()) {
      return;
    }

    const shiftKey = this.lastMouseEvent?.shiftKey ?? false;

    this.checkboxClick.emit({
      index: this.index(),
      book: this.book(),
      selected,
      shiftKey,
    });

    const selectFn = this.onBookSelect();
    if (selectFn) {
      selectFn(this.book(), selected);
    }

    this.lastMouseEvent = null;
  }

  toggleSelection(event: CheckboxChangeEvent): void {
    this.toggleCardSelection(event.checked);
  }
}
