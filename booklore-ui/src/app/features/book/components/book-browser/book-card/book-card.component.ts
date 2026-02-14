import {ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, EventEmitter, inject, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewChild} from '@angular/core';
import {TooltipModule} from "primeng/tooltip";
import {AdditionalFile, Book, BookType, ReadStatus} from '../../../model/book.model';
import {Button} from 'primeng/button';
import {MenuModule} from 'primeng/menu';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {BookService} from '../../../service/book.service';
import {CheckboxChangeEvent, CheckboxModule} from 'primeng/checkbox';
import {FormsModule} from '@angular/forms';
import {MetadataRefreshType} from '../../../../metadata/model/request/metadata-refresh-type.enum';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {NgClass} from '@angular/common';
import {User, UserService} from '../../../../settings/user-management/user.service';
import {filter, Subject} from 'rxjs';
import {EmailService} from '../../../../settings/email-v2/email.service';
import {TieredMenu} from 'primeng/tieredmenu';
import {Router} from '@angular/router';
import {RouterLink} from '@angular/router';
import {ProgressBar} from 'primeng/progressbar';
import {take, takeUntil} from 'rxjs/operators';
import {readStatusLabels} from '../book-filter/book-filter.config';
import {ResetProgressTypes} from '../../../../../shared/constants/reset-progress-type';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {BookDialogHelperService} from '../book-dialog-helper.service';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {BookNavigationService} from '../../../service/book-navigation.service';
import {BookCardOverlayPreferenceService} from '../book-card-overlay-preference.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-book-card',
  templateUrl: './book-card.component.html',
  styleUrls: ['./book-card.component.scss'],
  imports: [Button, MenuModule, CheckboxModule, FormsModule, NgClass, TieredMenu, ProgressBar, TooltipModule, RouterLink, TranslocoPipe],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookCardComponent implements OnInit, OnChanges, OnDestroy {

  @Output() checkboxClick = new EventEmitter<{ index: number; book: Book; selected: boolean; shiftKey: boolean }>();
  @Output() menuToggled = new EventEmitter<boolean>();

  @Input() index!: number;
  @Input() book!: Book;
  @Input() isCheckboxEnabled: boolean = false;
  @Input() onBookSelect?: (book: Book, selected: boolean) => void;
  @Input() isSelected: boolean = false;
  @Input() bottomBarHidden: boolean = false;
  @Input() seriesViewEnabled: boolean = false;
  @Input() isSeriesCollapsed: boolean = false;
  @Input() overlayPreferenceService?: BookCardOverlayPreferenceService;
  @Input() forceEbookMode: boolean = false;

  @ViewChild('checkboxElem') checkboxElem!: ElementRef<HTMLInputElement>;

  items: MenuItem[] | undefined;
  isImageLoaded: boolean = false;
  isSubMenuLoading = false;
  private additionalFilesLoaded = false;

  private bookService = inject(BookService);
  private taskHelperService = inject(TaskHelperService);
  private userService = inject(UserService);
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  protected urlHelper = inject(UrlHelperService);
  private confirmationService = inject(ConfirmationService);
  private bookDialogHelperService = inject(BookDialogHelperService);
  private bookNavigationService = inject(BookNavigationService);
  private cdr = inject(ChangeDetectorRef);
  private appSettingsService = inject(AppSettingsService);
  private readonly t = inject(TranslocoService);

  protected _progressPercentage: number | null = null;
  protected _koProgressPercentage: number | null = null;
  protected _koboProgressPercentage: number | null = null;
  protected _displayTitle: string | undefined = undefined;
  protected _isSeriesViewActive: boolean = false;
  protected _coverImageUrl: string = '';
  protected _readStatusIcon: string = '';
  protected _readStatusClass: string = '';
  protected _readStatusTooltip: string = '';
  protected _shouldShowStatusIcon: boolean = false;
  protected _seriesCountTooltip: string = '';
  protected _titleTooltip: string = '';
  protected _hasProgress: boolean = false;
  protected _isAudiobook: boolean = false;

  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  private destroy$ = new Subject<void>();
  protected readStatusHelper = inject(ReadStatusHelper);
  private user: User | null = null;
  private diskType: string = 'LOCAL';
  private menuInitialized = false;

  showBookTypePill = true;

  private overlayPrefSub?: any;

  ngOnInit(): void {
    this.computeAllMemoizedValues();
    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(userState => {
        this.user = userState.user;
        this.metadataCenterViewMode = userState.user?.userSettings?.metadataCenterViewMode ?? 'route';
      });

    this.appSettingsService.appSettings$
      .pipe(
        filter(settings => !!settings),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(settings => {
        this.diskType = settings?.diskType ?? 'LOCAL';
      });

    if (this.overlayPreferenceService) {
      this.overlayPrefSub = this.overlayPreferenceService.showBookTypePill$.subscribe(val => {
        this.showBookTypePill = val;
        this.cdr.markForCheck();
      });
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['book'] || changes['forceEbookMode']) {
      this.computeAllMemoizedValues();
      if (changes['book'] && !changes['book'].firstChange && this.menuInitialized) {
        this.additionalFilesLoaded = false;
        this.initMenu();
      }
    }

    if (changes['seriesViewEnabled'] || changes['isSeriesCollapsed']) {
      this._isSeriesViewActive = this.seriesViewEnabled && !!this.book.seriesCount && this.book.seriesCount >= 1;
      this._displayTitle = (this.isSeriesCollapsed && this.book.metadata?.seriesName) ? this.book.metadata?.seriesName : this.book.metadata?.title;
      this._titleTooltip = this.t.translate('book.card.alt.titleTooltip', { title: this._displayTitle });
    }
  }

  private computeAllMemoizedValues(): void {
    this._progressPercentage = this.book.epubProgress?.percentage
      ?? this.book.pdfProgress?.percentage
      ?? this.book.cbxProgress?.percentage
      ?? null;

    this._koProgressPercentage = this.book.koreaderProgress?.percentage ?? null;
    this._koboProgressPercentage = this.book.koboProgress?.percentage ?? null;

    this._hasProgress = this._progressPercentage !== null || this._koProgressPercentage !== null || this._koboProgressPercentage !== null;

    this._isSeriesViewActive = this.seriesViewEnabled && !!this.book.seriesCount && this.book.seriesCount >= 1;
    this._displayTitle = (this.isSeriesCollapsed && this.book.metadata?.seriesName)
      ? this.book.metadata?.seriesName
      : this.book.metadata?.title;
    this._isAudiobook = this.book.primaryFile?.bookType === 'AUDIOBOOK' && !this.forceEbookMode;
    this._coverImageUrl = this._isAudiobook
      ? this.urlHelper.getAudiobookThumbnailUrl(this.book.id, this.book.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(this.book.id, this.book.metadata?.coverUpdatedOn);

    this._readStatusIcon = this.readStatusHelper.getReadStatusIcon(this.book.readStatus);
    this._readStatusClass = this.readStatusHelper.getReadStatusClass(this.book.readStatus);
    this._readStatusTooltip = this.readStatusHelper.getReadStatusTooltip(this.book.readStatus);
    this._shouldShowStatusIcon = this.readStatusHelper.shouldShowStatusIcon(this.book.readStatus);

    this._seriesCountTooltip = this.t.translate('book.card.alt.seriesCollapsed', { count: this.book.seriesCount });
    this._titleTooltip = this.t.translate('book.card.alt.titleTooltip', { title: this._displayTitle });
  }

  get hasProgress(): boolean {
    return this._hasProgress;
  }

  get seriesCountTooltip(): string {
    return this._seriesCountTooltip;
  }

  get titleTooltip(): string {
    return this._titleTooltip;
  }

  get readStatusTooltip(): string {
    return this._readStatusTooltip;
  }

  get displayTitle(): string | undefined {
    return this._displayTitle;
  }

  get coverImageUrl(): string {
    return this._coverImageUrl;
  }

  onImageLoad(): void {
    this.isImageLoaded = true;
    this.cdr.markForCheck();
  }

  readBook(book: Book): void {
    if (this.forceEbookMode && book.primaryFile?.bookType === 'AUDIOBOOK') {
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
    if (!this.menuInitialized) {
      this.menuInitialized = true;
      this.initMenu();
      this.cdr.markForCheck();
    }

    menu.toggle(event);

    if (!this.additionalFilesLoaded && !this.isSubMenuLoading && this.needsAdditionalFilesData()) {
      this.isSubMenuLoading = true;
      this.cdr.markForCheck();
      this.bookService.getBookByIdFromAPI(this.book.id, true).subscribe({
        next: (book) => {
          this.book = book;
          this.additionalFilesLoaded = true;
          this.isSubMenuLoading = false;
          this.initMenu();
          this.cdr.markForCheck();
        },
        error: () => {
          this.isSubMenuLoading = false;
          this.cdr.markForCheck();
        }
      });
    }
  }

  private needsAdditionalFilesData(): boolean {
    if (this.additionalFilesLoaded) {
      return false;
    }
    const hasNoAlternativeFormats = !this.book.alternativeFormats || this.book.alternativeFormats.length === 0;
    const hasNoSupplementaryFiles = !this.book.supplementaryFiles || this.book.supplementaryFiles.length === 0;
    const canDownload = !!this.user?.permissions.canDownload;
    const canDeleteBook = !!this.user?.permissions.canDeleteBook;
    return (canDownload || canDeleteBook) && hasNoAlternativeFormats && hasNoSupplementaryFiles;
  }

  private initMenu() {
    this.items = [
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
            this.openBookInfo(this.book);
          }, 150);
        },
      },
      ...this.getPermissionBasedMenuItems(),
      ...this.moreMenuItems(),
    ];
  }

  private getPermissionBasedMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    if (this.user?.permissions.canDownload) {
      const hasAdditionalFiles = (this.book.alternativeFormats && this.book.alternativeFormats.length > 0) ||
        (this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0);

      if (hasAdditionalFiles) {
        const downloadItems = this.getDownloadMenuItems();
        items.push({
          label: this.t.translate('book.card.menu.download'),
          icon: 'pi pi-download',
          items: downloadItems
        });
      } else if (this.additionalFilesLoaded) {
        items.push({
          label: this.t.translate('book.card.menu.download'),
          icon: 'pi pi-download',
          command: () => {
            this.bookService.downloadFile(this.book);
          }
        });
      } else {
        items.push({
          label: this.t.translate('book.card.menu.download'),
          icon: this.isSubMenuLoading ? 'pi pi-spin pi-spinner' : 'pi pi-download',
          items: [{label: this.t.translate('book.card.menu.loading'), disabled: true}]
        });
      }
    }

    if (this.user?.permissions.canDeleteBook) {
      const hasAdditionalFiles = (this.book.alternativeFormats && this.book.alternativeFormats.length > 0) ||
        (this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0);

      if (hasAdditionalFiles) {
        const deleteItems = this.getDeleteMenuItems();
        items.push({
          label: this.t.translate('book.card.menu.delete'),
          icon: 'pi pi-trash',
          items: deleteItems
        });
      } else if (this.additionalFilesLoaded) {
        items.push({
          label: this.t.translate('book.card.menu.delete'),
          icon: 'pi pi-trash',
          command: () => {
            this.confirmationService.confirm({
              message: this.t.translate('book.card.confirm.deleteBookMessage', {title: this.book.metadata?.title}),
              header: this.t.translate('book.card.confirm.deleteBookHeader'),
              icon: 'pi pi-exclamation-triangle',
              acceptIcon: 'pi pi-trash',
              rejectIcon: 'pi pi-times',
              acceptLabel: this.t.translate('common.delete'),
              rejectLabel: this.t.translate('common.cancel'),
              acceptButtonStyleClass: 'p-button-danger',
              rejectButtonStyleClass: 'p-button-outlined',
              accept: () => {
                this.bookService.deleteBooks(new Set([this.book.id])).subscribe();
              }
            });
          }
        });
      } else {
        items.push({
          label: this.t.translate('book.card.menu.delete'),
          icon: this.isSubMenuLoading ? 'pi pi-spin pi-spinner' : 'pi pi-trash',
          items: [{label: this.t.translate('book.card.menu.loading'), disabled: true}]
        });
      }
    }

    if (this.user?.permissions.canEmailBook) {
      items.push(
        {
          label: this.t.translate('book.card.menu.emailBook'),
          icon: 'pi pi-envelope',
          items: [{
            label: this.t.translate('book.card.menu.quickSend'),
            icon: 'pi pi-envelope',
            command: () => {
              this.emailService.emailBookQuick(this.book.id).subscribe({
                next: () => {
                  this.messageService.add({
                    severity: 'info',
                    summary: this.t.translate('common.success'),
                    detail: this.t.translate('book.card.toast.quickSendSuccessDetail'),
                  });
                },
                error: (err) => {
                  const errorMessage = err?.error?.message || this.t.translate('book.card.toast.quickSendErrorDetail');
                  this.messageService.add({
                    severity: 'error',
                    summary: this.t.translate('common.error'),
                    detail: errorMessage,
                  });
                },
              });
            }
          },
            {
              label: this.t.translate('book.card.menu.customSend'),
              icon: 'pi pi-envelope',
              command: () => {
                this.bookDialogHelperService.openCustomSendDialog(this.book);
              }
            }
          ]
        });
    }

    if (this.user?.permissions.canEditMetadata) {
      items.push({
        label: this.t.translate('book.card.menu.metadata'),
        icon: 'pi pi-database',
        items: [
          {
            label: this.t.translate('book.card.menu.searchMetadata'),
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
            label: this.t.translate('book.card.menu.autoFetch'),
            icon: 'pi pi-bolt',
            command: () => {
              this.taskHelperService.refreshMetadataTask({
                refreshType: MetadataRefreshType.BOOKS,
                bookIds: [this.book.id],
              }).subscribe();
            }
          },
          {
            label: this.t.translate('book.card.menu.customFetch'),
            icon: 'pi pi-sync',
            command: () => {
              this.bookDialogHelperService.openMetadataRefreshDialog(new Set([this.book!.id]))
            },
          },
          {
            label: this.t.translate('book.card.menu.regenerateCover'),
            icon: 'pi pi-image',
            command: () => {
              this.bookService.regenerateCover(this.book.id).subscribe({
                next: () => this.messageService.add({
                  severity: 'success',
                  summary: this.t.translate('common.success'),
                  detail: this.t.translate('book.card.toast.coverRegenSuccessDetail')
                }),
                error: (err) => this.messageService.add({
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
              this.bookService.generateCustomCover(this.book.id).subscribe({
                next: () => this.messageService.add({
                  severity: 'success',
                  summary: this.t.translate('common.success'),
                  detail: this.t.translate('book.card.toast.customCoverSuccessDetail')
                }),
                error: (err) => this.messageService.add({
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

    if (this.user?.permissions.canMoveOrganizeFiles && this.diskType === 'LOCAL') {
      moreActions.push({
        label: this.t.translate('book.card.menu.organizeFile'),
        icon: 'pi pi-arrows-h',
        command: () => {
          this.bookDialogHelperService.openFileMoverDialog(new Set([this.book.id]));
        }
      });
    }

    moreActions.push(
      {
        label: this.t.translate('book.card.menu.readStatus'),
        icon: 'pi pi-book',
        items: Object.entries(readStatusLabels).map(([status, label]) => ({
          label,
          command: () => {
            this.bookService.updateBookReadStatus(this.book.id, status as ReadStatus).subscribe({
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
        label: this.t.translate('book.card.menu.resetBookloreProgress'),
        icon: 'pi pi-undo',
        command: () => {
          this.bookService.resetProgress(this.book.id, ResetProgressTypes.BOOKLORE).subscribe({
            next: () => {
              this.messageService.add({
                severity: 'success',
                summary: this.t.translate('book.card.toast.progressResetSummary'),
                detail: this.t.translate('book.card.toast.progressResetBookloreDetail'),
                life: 1500
              });
            },
            error: () => {
              this.messageService.add({
                severity: 'error',
                summary: this.t.translate('book.card.toast.progressResetFailedSummary'),
                detail: this.t.translate('book.card.toast.progressResetBookloreFailedDetail'),
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
          this.bookService.resetProgress(this.book.id, ResetProgressTypes.KOREADER).subscribe({
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
    this.bookDialogHelperService.openShelfAssignerDialog(this.book, null);
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
    const allBookIds = this.bookNavigationService.getAvailableBookIds();
    if (allBookIds.length > 0) {
      this.bookNavigationService.setNavigationContext(allBookIds, book.id);
    }

    if (this.metadataCenterViewMode === 'route') {
      this.router.navigate(['/book', book.id], {
        queryParams: {tab: 'view'}
      });
    } else {
      this.bookDialogHelperService.openBookDetailsDialog(book.id);
    }
  }

  private getDownloadMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    items.push({
      label: `${this.book.fileName || 'Book File'}`,
      icon: 'pi pi-file',
      command: () => {
        this.bookService.downloadFile(this.book);
      }
    });

    if (this.hasAdditionalFiles()) {
      items.push({separator: true});
    }

    if (this.book.alternativeFormats && this.book.alternativeFormats.length > 0) {
      this.book.alternativeFormats.forEach(format => {
        const extension = this.getFileExtension(format.filePath);
        items.push({
          label: `${format.fileName} (${this.getFileSizeInMB(format)})`,
          icon: this.getFileIcon(extension),
          command: () => this.downloadAdditionalFile(this.book, format.id)
        });
      });
    }

    if (this.book.alternativeFormats && this.book.alternativeFormats.length > 0 &&
      this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0) {
      items.push({separator: true});
    }

    if (this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0) {
      this.book.supplementaryFiles.forEach(file => {
        const extension = this.getFileExtension(file.filePath);
        items.push({
          label: `${file.fileName} (${this.getFileSizeInMB(file)})`,
          icon: this.getFileIcon(extension),
          command: () => this.downloadAdditionalFile(this.book, file.id)
        });
      });
    }

    return items;
  }

  private getDeleteMenuItems(): MenuItem[] {
    const items: MenuItem[] = [];

    items.push({
      label: this.t.translate('book.card.menu.book'),
      icon: 'pi pi-book',
      command: () => {
        this.confirmationService.confirm({
          message: this.t.translate('book.card.confirm.deleteBookMessage', {title: this.book.metadata?.title}),
          header: this.t.translate('book.card.confirm.deleteBookHeader'),
          icon: 'pi pi-exclamation-triangle',
          acceptIcon: 'pi pi-trash',
          rejectIcon: 'pi pi-times',
          acceptLabel: this.t.translate('common.delete'),
          rejectLabel: this.t.translate('common.cancel'),
          acceptButtonStyleClass: 'p-button-danger',
          rejectButtonStyleClass: 'p-button-outlined',
          accept: () => {
            this.bookService.deleteBooks(new Set([this.book.id])).subscribe();
          }
        });
      }
    });

    if (this.hasAdditionalFiles()) {
      items.push({separator: true});
    }

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

    if (this.book.alternativeFormats && this.book.alternativeFormats.length > 0 &&
      this.book.supplementaryFiles && this.book.supplementaryFiles.length > 0) {
      items.push({separator: true});
    }

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

  private downloadAdditionalFile(book: Book, fileId: number): void {
    this.bookService.downloadAdditionalFile(book, fileId);
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
        this.bookService.deleteAdditionalFile(bookId, fileId).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('common.success'),
              detail: this.t.translate('book.card.toast.deleteFileSuccessDetail', {fileName})
            });
          },
          error: (error) => {
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

  getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop()?.toUpperCase() || null;
  }

  getDisplayFormat(): string | null {
    if (!this.book?.primaryFile) {
      return 'PHYSICAL';
    }
    if (this.forceEbookMode && this.book.primaryFile?.bookType === 'AUDIOBOOK') {
      const ebookType = this.getEbookType(this.book);
      if (ebookType) {
        return ebookType;
      }
    }
    const ext = this.book?.primaryFile?.extension;
    if (ext) {
      return ext.toUpperCase();
    }
    return this.getFileExtension(this.book?.primaryFile?.filePath);
  }

  hasDigitalFile(): boolean {
    return !!this.book?.primaryFile;
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
      book: this.book,
      selected: selected,
      shiftKey: shiftKey,
    });

    if (this.onBookSelect) {
      this.onBookSelect(this.book, selected);
    }

    this.lastMouseEvent = null;
  }

  toggleSelection(event: CheckboxChangeEvent): void {
    this.toggleCardSelection(event.checked);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.overlayPrefSub) {
      this.overlayPrefSub.unsubscribe();
    }
  }
}
