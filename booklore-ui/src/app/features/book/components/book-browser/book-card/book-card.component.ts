import {Component, ElementRef, EventEmitter, inject, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewChild} from '@angular/core';
import {TooltipModule} from "primeng/tooltip";
import {AdditionalFile, Book, ReadStatus} from '../../../model/book.model';
import {Button} from 'primeng/button';
import {MenuModule} from 'primeng/menu';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {ShelfAssignerComponent} from '../../shelf-assigner/shelf-assigner.component';
import {BookService} from '../../../service/book.service';
import {CheckboxChangeEvent, CheckboxModule} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {MetadataRefreshType} from '../../../../metadata/model/request/metadata-refresh-type.enum';
import {MetadataRefreshRequest} from '../../../../metadata/model/request/metadata-refresh-request.model';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {NgClass} from '@angular/common';
import {UserService} from '../../../../settings/user-management/user.service';
import {filter, Subject} from 'rxjs';
import {EmailService} from '../../../../settings/email/email.service';
import {TieredMenu} from 'primeng/tieredmenu';
import {BookSenderComponent} from '../../book-sender/book-sender.component';
import {Router} from '@angular/router';
import {ProgressBar} from 'primeng/progressbar';
import {BookMetadataCenterComponent} from '../../../../metadata/component/book-metadata-center/book-metadata-center.component';
import {take, takeUntil} from 'rxjs/operators';
import {readStatusLabels} from '../book-filter/book-filter.component';
import {ResetProgressTypes} from '../../../../../shared/constants/reset-progress-type';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {BookDialogHelperService} from '../BookDialogHelperService';
import {MetadataFetchOptionsComponent} from '../../../../metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';

@Component({
  selector: 'app-book-card',
  templateUrl: './book-card.component.html',
  styleUrls: ['./book-card.component.scss'],
  imports: [Button, MenuModule, CheckboxModule, FormsModule, NgClass, TieredMenu, ProgressBar, TooltipModule],
  standalone: true
})
export class BookCardComponent implements OnInit, OnChanges, OnDestroy {

  @Output() checkboxClick = new EventEmitter<{ index: number; bookId: number; selected: boolean; shiftKey: boolean }>();

  @Input() index!: number;
  @Input() book!: Book;
  @Input() isCheckboxEnabled: boolean = false;
  @Input() onBookSelect?: (bookId: number, selected: boolean) => void;
  @Input() isSelected: boolean = false;
  @Input() bottomBarHidden: boolean = false;
  @Input() readButtonHidden: boolean = false;
  @Input() isSeriesCollapsed: boolean = false;

  @ViewChild('checkboxElem') checkboxElem!: ElementRef<HTMLInputElement>;

  items: MenuItem[] | undefined;
  isHovered: boolean = false;
  isImageLoaded: boolean = false;
  isSubMenuLoading = false;
  private additionalFilesLoaded = false;

  private bookService = inject(BookService);
  private dialogService = inject(DialogService);
  private userService = inject(UserService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private confirmationService = inject(ConfirmationService);
  private bookDialogHelperService = inject(BookDialogHelperService);

  private userPermissions: any;
  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  private destroy$ = new Subject<void>();
  protected readStatusHelper = inject(ReadStatusHelper);

  ngOnInit(): void {
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(userState => {
        this.userPermissions = userState.user?.permissions;
        this.metadataCenterViewMode = userState?.user?.userSettings.metadataCenterViewMode ?? 'route';
        this.initMenu();
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['book'] && !changes['book'].firstChange) {
      this.additionalFilesLoaded = false;
      this.initMenu();
    }
  }

  get progressPercentage(): number | null {
    if (this.book.epubProgress?.percentage != null) {
      return this.book.epubProgress.percentage;
    }
    if (this.book.pdfProgress?.percentage != null) {
      return this.book.pdfProgress.percentage;
    }
    if (this.book.cbxProgress?.percentage != null) {
      return this.book.cbxProgress.percentage;
    }
    return null;
  }

  get koProgressPercentage(): number | null {
    if (this.book.koreaderProgress?.percentage != null) {
      return this.book.koreaderProgress.percentage;
    }
    return null;
  }

  get displayTitle(): string | undefined {
    return (this.isSeriesCollapsed && this.book.metadata?.seriesName) ? this.book.metadata?.seriesName : this.book.metadata?.title;
    // return (this.isSeriesCollapsed && this.book.metadata?.seriesName) ? this.book.metadata.seriesName : this.book.metadata?.title;
  }

  onImageLoad(): void {
    this.isImageLoaded = true;
  }

  readBook(book: Book): void {
    this.bookService.readBook(book.id);
  }

  onMenuToggle(event: Event, menu: TieredMenu): void {
    menu.toggle(event);

    // Load additional files if not already loaded and needed
    if (!this.additionalFilesLoaded && !this.isSubMenuLoading && this.needsAdditionalFilesData()) {
      this.isSubMenuLoading = true;
      this.bookService.getBookByIdFromAPI(this.book.id, true).subscribe({
        next: (book) => {
          this.book = book;
          this.additionalFilesLoaded = true;
          this.isSubMenuLoading = false;
          this.initMenu();
        },
        error: () => {
          this.isSubMenuLoading = false;
        }
      });
    }
  }

  private needsAdditionalFilesData(): boolean {
    // Don't need to load if already loaded
    if (this.additionalFilesLoaded) {
      return false;
    }

    const hasNoAlternativeFormats = !this.book.alternativeFormats || this.book.alternativeFormats.length === 0;
    const hasNoSupplementaryFiles = !this.book.supplementaryFiles || this.book.supplementaryFiles.length === 0;
    return (this.hasDownloadPermission() || this.hasDeleteBookPermission()) &&
      hasNoAlternativeFormats && hasNoSupplementaryFiles;
  }

  private initMenu() {
    this.items = [
      {
        label: 'Assign Shelf',
        icon: 'pi pi-folder',
        command: () => this.openShelfDialog()
      },
      {
        label: 'View Details',
        icon: 'pi pi-info-circle',
        command: () => {
          setTimeout(() => {
            if (this.metadataCenterViewMode === 'route') {
              this.router.navigate(['/book', this.book.id], {
                queryParams: {tab: 'view'}
              });
            } else {
              this.dialogService.open(BookMetadataCenterComponent, {
                width: '95%',
                data: {bookId: this.book.id},
                modal: true,
                dismissableMask: true,
                showHeader: false
              });
            }
          }, 150);
        },
      },
      ...this.getPermissionBasedMenuItems(),
      ...this.moreMenuItems(),
    ];
  }

  private getPermissionBasedMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    if (this.hasDownloadPermission()) {
      const hasAdditionalFiles = (this.book.alternativeFormats && this.book.alternativeFormats.length > 0) ||
        (this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0);

      if (hasAdditionalFiles) {
        const downloadItems = this.getDownloadMenuItems();
        items.push({
          label: 'Download',
          icon: 'pi pi-download',
          items: downloadItems
        });
      } else if (this.additionalFilesLoaded) {
        // Data has been loaded but no additional files exist
        items.push({
          label: 'Download',
          icon: 'pi pi-download',
          command: () => {
            this.bookService.downloadFile(this.book.id);
          }
        });
      } else {
        // Data not loaded yet
        items.push({
          label: 'Download',
          icon: this.isSubMenuLoading ? 'pi pi-spin pi-spinner' : 'pi pi-download',
          items: [{label: 'Loading...', disabled: true}]
        });
      }
    }

    if (this.hasDeleteBookPermission()) {
      const hasAdditionalFiles = (this.book.alternativeFormats && this.book.alternativeFormats.length > 0) ||
        (this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0);

      if (hasAdditionalFiles) {
        const deleteItems = this.getDeleteMenuItems();
        items.push({
          label: 'Delete',
          icon: 'pi pi-trash',
          items: deleteItems
        });
      } else if (this.additionalFilesLoaded) {
        // Data has been loaded but no additional files exist - show delete book option
        items.push({
          label: 'Delete',
          icon: 'pi pi-trash',
          command: () => {
            this.confirmationService.confirm({
              message: `Are you sure you want to delete "${this.book.metadata?.title}"?`,
              header: 'Confirm Deletion',
              icon: 'pi pi-exclamation-triangle',
              acceptIcon: 'pi pi-trash',
              rejectIcon: 'pi pi-times',
              acceptButtonStyleClass: 'p-button-danger',
              accept: () => {
                this.bookService.deleteBooks(new Set([this.book.id])).subscribe();
              }
            });
          }
        });
      } else {
        // Data not loaded yet
        items.push({
          label: 'Delete',
          icon: this.isSubMenuLoading ? 'pi pi-spin pi-spinner' : 'pi pi-trash',
          items: [{label: 'Loading...', disabled: true}]
        });
      }
    }

    if (this.hasEmailBookPermission()) {
      items.push(
        {
          label: 'Send Book',
          icon: 'pi pi-envelope',
          items: [{
            label: 'Quick Send',
            icon: 'pi pi-envelope',
            command: () => {
              this.emailService.emailBookQuick(this.book.id).subscribe({
                next: () => {
                  this.messageService.add({
                    severity: 'info',
                    summary: 'Success',
                    detail: 'The book sending has been scheduled.',
                  });
                },
                error: (err) => {
                  const errorMessage = err?.error?.message || 'An error occurred while sending the book.';
                  this.messageService.add({
                    severity: 'error',
                    summary: 'Error',
                    detail: errorMessage,
                  });
                },
              });
            }
          },
            {
              label: 'Custom Send',
              icon: 'pi pi-envelope',
              command: () => {
                this.dialogService.open(BookSenderComponent, {
                  header: 'Send Book to Email',
                  modal: true,
                  closable: true,
                  style: {
                    position: 'absolute',
                    top: '15%',
                  },
                  data: {
                    bookId: this.book.id,
                  }
                });
              }
            }
          ]
        });
    }

    if (this.hasEditMetadataPermission()) {
      items.push({
        label: 'Metadata',
        icon: 'pi pi-database',
        items: [
          {
            label: 'Search Metadata',
            icon: 'pi pi-sparkles',
            command: () => {
              setTimeout(() => {
                this.router.navigate(['/book', this.book.id], {
                  queryParams: {tab: 'match'}
                })
              }, 150);
            },
          },
          {
            label: 'Auto Fetch',
            icon: 'pi pi-bolt',
            command: () => {
              const metadataRefreshRequest: MetadataRefreshRequest = {
                refreshType: MetadataRefreshType.BOOKS,
                bookIds: [this.book.id],
              };
              this.bookService.autoRefreshMetadata(metadataRefreshRequest).subscribe();
            },
          },
          {
            label: 'Custom Fetch',
            icon: 'pi pi-sync',
            command: () => {
              this.dialogService.open(MetadataFetchOptionsComponent, {
                header: 'Metadata Refresh Options',
                modal: true,
                closable: true,
                data: {
                  bookIds: [this.book!.id],
                  metadataRefreshType: MetadataRefreshType.BOOKS,
                },
              });
            },
          }
        ]
      });
    }

    return items;
  }

  private moreMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    if (this.hasEditMetadataPermission()) {
      items.push({
        label: 'More Actions',
        icon: 'pi pi-ellipsis-h',
        items: [
          {
            label: 'Organize File',
            icon: 'pi pi-arrows-h',
            command: () => {
              this.bookDialogHelperService.openFileMoverDialog(new Set([this.book.id]));
            }
          },
          {
            label: 'Read Status',
            icon: 'pi pi-book',
            items: Object.entries(readStatusLabels).map(([status, label]) => ({
              label,
              command: () => {
                this.bookService.updateBookReadStatus(this.book.id, status as ReadStatus).subscribe({
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
            }))
          },
          {
            label: 'Reset Booklore Progress',
            icon: 'pi pi-undo',
            command: () => {
              this.bookService.resetProgress(this.book.id, ResetProgressTypes.BOOKLORE).subscribe({
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
                    detail: 'Could not reset Booklore progress.',
                    life: 1500
                  });
                }
              });
            },
          },
          {
            label: 'Reset KOReader Progress',
            icon: 'pi pi-undo',
            command: () => {
              this.bookService.resetProgress(this.book.id, ResetProgressTypes.KOREADER).subscribe({
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
                    detail: 'Could not reset KOReader progress.',
                    life: 1500
                  });
                }
              });
            },
          }
        ]
      });
    }

    return items;
  }

  private openShelfDialog(): void {
    this.dialogService.open(ShelfAssignerComponent, {
      header: `Update Book's Shelves`,
      modal: true,
      closable: true,
      contentStyle: {overflow: 'auto'},
      baseZIndex: 10,
      style: {
        position: 'absolute',
        top: '15%',
      },
      data: {
        book: this.book,
      },
    });
  }

  openSeriesInfo(): void {
    const seriesName = this.book?.metadata?.seriesName;
    if (this.isSeriesCollapsed && seriesName) {
      const encodedSeriesName = encodeURIComponent(seriesName);
      this.router.navigate(['/series', encodedSeriesName]);
    } else {
      this.openBookInfo(this.book);
    }
  }

  openBookInfo(book: Book): void {
    if (this.metadataCenterViewMode === 'route') {
      this.router.navigate(['/book', book.id], {
        queryParams: {tab: 'view'}
      });
    } else {
      this.dialogService.open(BookMetadataCenterComponent, {
        width: '85%',
        data: {bookId: book.id},
        modal: true,
        dismissableMask: true,
        showHeader: false
      });
    }
  }

  private getDownloadMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    // Add main book file first
    items.push({
      label: `${this.book.fileName || 'Book File'}`,
      icon: 'pi pi-file',
      command: () => {
        this.bookService.downloadFile(this.book.id);
      }
    });

    // Add separator if there are additional files
    if (this.hasAdditionalFiles()) {
      items.push({separator: true});
    }

    // Add alternative formats
    if (this.book.alternativeFormats && this.book.alternativeFormats.length > 0) {
      this.book.alternativeFormats.forEach(format => {
        const extension = this.getFileExtension(format.filePath);
        items.push({
          label: `${format.fileName} (${this.getFileSizeInMB(format)})`,
          icon: this.getFileIcon(extension),
          command: () => this.downloadAdditionalFile(this.book.id, format.id)
        });
      });
    }

    // Add separator if both alternative formats and supplementary files exist
    if (this.book.alternativeFormats && this.book.alternativeFormats.length > 0 &&
      this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0) {
      items.push({separator: true});
    }

    // Add supplementary files
    if (this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0) {
      this.book.supplementaryFiles.forEach(file => {
        const extension = this.getFileExtension(file.filePath);
        items.push({
          label: `${file.fileName} (${this.getFileSizeInMB(file)})`,
          icon: this.getFileIcon(extension),
          command: () => this.downloadAdditionalFile(this.book.id, file.id)
        });
      });
    }

    return items;
  }

  private getDeleteMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    // Add main book deletion
    items.push({
      label: 'Book',
      icon: 'pi pi-book',
      command: () => {
        this.confirmationService.confirm({
          message: `Are you sure you want to delete "${this.book.metadata?.title}"?`,
          header: 'Confirm Deletion',
          icon: 'pi pi-exclamation-triangle',
          acceptIcon: 'pi pi-trash',
          rejectIcon: 'pi pi-times',
          acceptButtonStyleClass: 'p-button-danger',
          accept: () => {
            this.bookService.deleteBooks(new Set([this.book.id])).subscribe();
          }
        });
      }
    });

    // Add separator if there are additional files
    if (this.hasAdditionalFiles()) {
      items.push({separator: true});
    }

    // Add alternative formats
    if (this.book.alternativeFormats && this.book.alternativeFormats.length > 0) {
      this.book.alternativeFormats.forEach(format => {
        const extension = this.getFileExtension(format.filePath);
        items.push({
          label: `${format.fileName} (${this.getFileSizeInMB(format)})`,
          icon: this.getFileIcon(extension),
          command: () => this.deleteAdditionalFile(this.book.id, format.id, format.fileName || 'file')
        });
      });
    }

    // Add separator if both alternative formats and supplementary files exist
    if (this.book.alternativeFormats && this.book.alternativeFormats.length > 0 &&
      this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0) {
      items.push({separator: true});
    }

    // Add supplementary files
    if (this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0) {
      this.book.supplementaryFiles.forEach(file => {
        const extension = this.getFileExtension(file.filePath);
        items.push({
          label: `${file.fileName} (${this.getFileSizeInMB(file)})`,
          icon: this.getFileIcon(extension),
          command: () => this.deleteAdditionalFile(this.book.id, file.id, file.fileName || 'file')
        });
      });
    }

    return items;
  }

  private hasAdditionalFiles(): boolean {
    return !!(this.book.alternativeFormats && this.book.alternativeFormats.length > 0) ||
      !!(this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0);
  }

  private downloadAdditionalFile(bookId: number, fileId: number): void {
    this.bookService.downloadAdditionalFile(bookId, fileId);
  }

  private deleteAdditionalFile(bookId: number, fileId: number, fileName: string): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete the additional file "${fileName}"?`,
      header: 'Confirm File Deletion',
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookService.deleteAdditionalFile(bookId, fileId).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Success',
              detail: `Additional file "${fileName}" deleted successfully`
            });
          },
          error: (error) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Error',
              detail: `Failed to delete additional file: ${error.message || 'Unknown error'}`
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
        return 'pi pi-book';
      case 'cbz':
      case 'cbr':
      case 'cbx':
        return 'pi pi-image';
      default:
        return 'pi pi-file';
    }
  }

  private getFileSizeInMB(fileInfo: AdditionalFile): string {
    const sizeKb = fileInfo?.fileSizeKb;
    return sizeKb != null ? `${(sizeKb / 1024).toFixed(2)} MB` : '-';
  }

  private isAdmin(): boolean {
    return this.userPermissions?.admin ?? false;
  }

  private hasEditMetadataPermission(): boolean {
    return this.isAdmin() || (this.userPermissions?.canEditMetadata ?? false);
  }

  private hasDownloadPermission(): boolean {
    return this.isAdmin() || (this.userPermissions?.canDownload ?? false);
  }

  private hasEmailBookPermission(): boolean {
    return this.isAdmin() || (this.userPermissions?.canEmailBook ?? false);
  }

  private hasDeleteBookPermission(): boolean {
    return this.isAdmin() || (this.userPermissions?.canDeleteBook ?? false);
  }

  private lastMouseEvent: MouseEvent | null = null;

  captureMouseEvent(event: MouseEvent): void {
    this.lastMouseEvent = event;
  }

  onCardClick(event: MouseEvent): void {
    if (!event.ctrlKey) {
      return;
    }

    this.toggleCardSelection(!this.isSelected)
  }

  toggleCardSelection(selected: boolean): void {
    if (!this.isCheckboxEnabled) {
      return;
    }

    this.isSelected = selected;
    const shiftKey = this.lastMouseEvent?.shiftKey ?? false;

    this.checkboxClick.emit({
      index: this.index,
      bookId: this.book.id,
      selected: selected,
      shiftKey: shiftKey,
    });

    if (this.onBookSelect) {
      this.onBookSelect(this.book.id, selected);
    }

    this.lastMouseEvent = null;
  }

  toggleSelection(event: CheckboxChangeEvent): void {
    this.toggleCardSelection(event.checked);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getReadStatusIcon(): string {
    return this.readStatusHelper.getReadStatusIcon(this.book.readStatus);
  }

  getReadStatusClass(): string {
    return this.readStatusHelper.getReadStatusClass(this.book.readStatus);
  }

  getReadStatusTooltip(): string {
    return this.readStatusHelper.getReadStatusTooltip(this.book.readStatus);
  }

  shouldShowStatusIcon(): boolean {
    return this.readStatusHelper.shouldShowStatusIcon(this.book.readStatus);
  }
}
