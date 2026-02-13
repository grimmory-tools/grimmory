import {Component, DestroyRef, inject, Input, OnChanges, OnInit, SimpleChanges, ViewChild} from '@angular/core';
import {Button} from 'primeng/button';
import {AsyncPipe, DecimalPipe, NgClass} from '@angular/common';
import {combineLatest, Observable} from 'rxjs';
import {BookService} from '../../../../book/service/book.service';
import {Rating, RatingRateEvent} from 'primeng/rating';
import {FormsModule} from '@angular/forms';
import {Book, BookFile, BookMetadata, BookRecommendation, BookType, ComicMetadata, FileInfo, ReadStatus} from '../../../../book/model/book.model';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {UserService} from '../../../../settings/user-management/user.service';
import {SplitButton} from 'primeng/splitbutton';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {EmailService} from '../../../../settings/email-v2/email.service';
import {Tooltip} from 'primeng/tooltip';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Editor} from 'primeng/editor';
import {ProgressBar} from 'primeng/progressbar';
import {MetadataRefreshType} from '../../../model/request/metadata-refresh-type.enum';
import {Router} from '@angular/router';
import {filter, map, switchMap, take, tap} from 'rxjs/operators';
import {Menu} from 'primeng/menu';
import {ResetProgressType, ResetProgressTypes} from '../../../../../shared/constants/reset-progress-type';
import {DatePicker} from 'primeng/datepicker';
import {ProgressSpinner} from 'primeng/progressspinner';
import {TieredMenu} from 'primeng/tieredmenu';
import {Image} from 'primeng/image';
import {BookDialogHelperService} from '../../../../book/components/book-browser/book-dialog-helper.service';
import {LibraryService} from '../../../../book/service/library.service';
import {TagColor, TagComponent} from '../../../../../shared/components/tag/tag.component';
import {TaskHelperService} from '../../../../settings/task-management/task-helper.service';
import {AGE_RATING_OPTIONS, CONTENT_RATING_LABELS, fileSizeRanges, matchScoreRanges, pageCountRanges} from '../../../../book/components/book-browser/book-filter/book-filter.config';
import {BookNavigationService} from '../../../../book/service/book-navigation.service';
import {BookMetadataHostService} from '../../../../../shared/service/book-metadata-host.service';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {DeleteBookFileEvent, DeleteSupplementaryFileEvent, DownloadAdditionalFileEvent, DownloadAllFilesEvent, DownloadEvent, MetadataTabsComponent, ReadEvent} from './metadata-tabs/metadata-tabs.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';


@Component({
  selector: 'app-metadata-viewer',
  standalone: true,
  templateUrl: './metadata-viewer.component.html',
  styleUrl: './metadata-viewer.component.scss',
  imports: [Button, AsyncPipe, Rating, FormsModule, SplitButton, NgClass, Tooltip, DecimalPipe, Editor, ProgressBar, Menu, DatePicker, ProgressSpinner, TieredMenu, Image, TagComponent, MetadataTabsComponent, TranslocoDirective, TranslocoPipe]
})
export class MetadataViewerComponent implements OnInit, OnChanges {
  @Input() book$!: Observable<Book | null>;
  @Input() recommendedBooks: BookRecommendation[] = [];
  @ViewChild(Editor) quillEditor!: Editor;
  private originalRecommendedBooks: BookRecommendation[] = [];

  private readonly t = inject(TranslocoService);
  private libraryService = inject(LibraryService);
  private bookDialogHelperService = inject(BookDialogHelperService)
  private emailService = inject(EmailService);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  private taskHelperService = inject(TaskHelperService);
  protected urlHelper = inject(UrlHelperService);
  protected userService = inject(UserService);
  private confirmationService = inject(ConfirmationService);

  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private dialogRef?: DynamicDialogRef;

  readMenuItems$!: Observable<MenuItem[]>;
  refreshMenuItems$!: Observable<MenuItem[]>;
  otherItems$!: Observable<MenuItem[]>;
  downloadMenuItems$!: Observable<MenuItem[]>;
  bookInSeries: Book[] = [];
  isExpanded = false;
  isComicSectionExpanded = true;
  isAudiobookSectionExpanded = true;
  showFilePath = false;
  isAutoFetching = false;
  private metadataCenterViewMode: 'route' | 'dialog' = 'route';
  selectedReadStatus: ReadStatus = ReadStatus.UNREAD;
  isEditingDateFinished = false;
  editDateFinished: Date | null = null;

  readStatusOptions: { value: ReadStatus, labelKey: string }[] = [
    {value: ReadStatus.UNREAD, labelKey: 'metadata.viewer.readStatusUnread'},
    {value: ReadStatus.PAUSED, labelKey: 'metadata.viewer.readStatusPaused'},
    {value: ReadStatus.READING, labelKey: 'metadata.viewer.readStatusReading'},
    {value: ReadStatus.RE_READING, labelKey: 'metadata.viewer.readStatusReReading'},
    {value: ReadStatus.READ, labelKey: 'metadata.viewer.readStatusRead'},
    {value: ReadStatus.PARTIALLY_READ, labelKey: 'metadata.viewer.readStatusPartiallyRead'},
    {value: ReadStatus.ABANDONED, labelKey: 'metadata.viewer.readStatusAbandoned'},
    {value: ReadStatus.WONT_READ, labelKey: 'metadata.viewer.readStatusWontRead'},
    {value: ReadStatus.UNSET, labelKey: 'metadata.viewer.readStatusUnset'},
  ];

  private bookNavigationService = inject(BookNavigationService);
  private metadataHostService = inject(BookMetadataHostService);
  private appSettingsService = inject(AppSettingsService);
  private appSettings$ = this.appSettingsService.appSettings$;
  amazonDomain = 'com';
  navigationState$ = this.bookNavigationService.getNavigationState();

  ngOnInit(): void {
    this.readMenuItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null),
      map((book): MenuItem[] => {
        const items: MenuItem[] = [];
        const primaryType = book.primaryFile?.bookType;
        if (primaryType === 'EPUB') {
          items.push({
            label: this.t.translate('metadata.viewer.menuStreamingReader'),
            icon: 'pi pi-play',
            command: () => this.read(book.id, 'epub-streaming')
          });
        }

        // Get readable alternative formats and group by type
        const readableAlternatives = book.alternativeFormats?.filter(f =>
          f.bookType && ['PDF', 'EPUB', 'FB2', 'MOBI', 'AZW3', 'CBX', 'AUDIOBOOK'].includes(f.bookType)
        ) ?? [];

        // Get unique format types from alternatives
        const uniqueAltTypes = [...new Set(readableAlternatives.map(f => f.bookType))];

        if (uniqueAltTypes.length > 0) {
          if (items.length > 0) {
            items.push({separator: true});
          }

          uniqueAltTypes.forEach(formatType => {
            if (formatType === 'EPUB') {
              // For EPUB, offer both standard and streaming readers
              items.push({
                label: formatType,
                icon: this.getFileIcon(formatType),
                items: [
                  {
                    label: this.t.translate('metadata.viewer.menuStandardReader'),
                    icon: 'pi pi-book',
                    command: () => this.read(book.id, undefined, formatType)
                  },
                  {
                    label: this.t.translate('metadata.viewer.menuStreamingReader'),
                    icon: 'pi pi-play',
                    command: () => this.read(book.id, 'epub-streaming', formatType)
                  }
                ]
              });
            } else {
              // For other formats, just show the type
              items.push({
                label: formatType,
                icon: this.getFileIcon(formatType ?? null),
                command: () => this.read(book.id, undefined, formatType)
              });
            }
          });
        }

        return items;
      })
    );

    this.downloadMenuItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null &&
        ((book.alternativeFormats !== undefined && book.alternativeFormats.length > 0) ||
          (book.supplementaryFiles !== undefined && book.supplementaryFiles.length > 0))),
      map((book): MenuItem[] => {
        const items: MenuItem[] = [];

        // Add alternative formats with type and size
        if (book.alternativeFormats && book.alternativeFormats.length > 0) {
          book.alternativeFormats.forEach(format => {
            items.push({
              label: `${format.bookType ?? 'File'} · ${this.formatFileSize(format)}`,
              icon: this.getFileIcon(format.bookType ?? null),
              command: () => this.downloadAdditionalFile(book, format.id)
            });
          });
        }

        // Add separator if both types exist
        if (book.alternativeFormats && book.alternativeFormats.length > 0 &&
          book.supplementaryFiles && book.supplementaryFiles.length > 0) {
          items.push({separator: true});
        }

        // Add supplementary files
        if (book.supplementaryFiles && book.supplementaryFiles.length > 0) {
          book.supplementaryFiles.forEach(file => {
            const extension = this.getFileExtension(file.filePath);
            items.push({
              label: `${this.truncateFileName(file.fileName, 20)} · ${this.formatFileSize(file)}`,
              icon: this.getFileIcon(extension),
              tooltipOptions: {tooltipLabel: file.fileName, tooltipPosition: 'left'},
              command: () => this.downloadAdditionalFile(book, file.id)
            });
          });
        }

        return items;
      })
    );

    this.otherItems$ = this.book$.pipe(
      filter((book): book is Book => book !== null),
      switchMap(book =>
        combineLatest([
          this.userService.userState$.pipe(take(1)),
          this.appSettingsService.appSettings$.pipe(take(1))
        ]).pipe(
          map(([userState, appSettings]) => {
            const items: MenuItem[] = [];

            items.push({
              label: this.t.translate('metadata.viewer.menuShelf'),
              icon: 'pi pi-folder',
              command: () => this.assignShelf(book.id)
            });

            // Add allowed submenus based on user permissions

            if (userState?.user?.permissions.canUpload || userState?.user?.permissions.admin) {
              items.push({
                label: this.t.translate('metadata.viewer.menuUploadFile'),
                icon: 'pi pi-upload',
                command: () => {
                  this.bookDialogHelperService.openAdditionalFileUploaderDialog(book);
                },
              });
            }

            const hasFiles = this.hasAnyFiles(book);

            if (hasFiles && (userState?.user?.permissions.canManageLibrary || userState?.user?.permissions.admin) && appSettings?.diskType === 'LOCAL') {
              items.push({
                label: this.t.translate('metadata.viewer.menuOrganizeFiles'),
                icon: 'pi pi-arrows-h',
                command: () => {
                  this.openFileMoverDialog(book.id);
                },
              });
            }

            if (hasFiles && (userState?.user?.permissions.canEmailBook || userState?.user?.permissions.admin)) {
              items.push({
                label: this.t.translate('metadata.viewer.menuSendBook'),
                icon: 'pi pi-send',
                items: [
                  {
                    label: this.t.translate('metadata.viewer.menuQuickSend'),
                    icon: 'pi pi-bolt',
                    command: () => this.quickSend(book.id)
                  },
                  {
                    label: this.t.translate('metadata.viewer.menuCustomSend'),
                    icon: 'pi pi-cog',
                    command: () => {
                      this.bookDialogHelperService.openCustomSendDialog(book);
                    }
                  }
                ]
              });
            }

            // Show "Attach to Another Book" for single-file books (detached books) - not for physical books
            // Hide in single-format libraries where attaching provides no cross-format benefit
            const isSingleFileBook = hasFiles && !book.alternativeFormats?.length;
            const library = this.libraryService.findLibraryById(book.libraryId);
            const isMultiFormatLibrary = !library?.allowedFormats?.length || library.allowedFormats.length > 1;
            if (isSingleFileBook && isMultiFormatLibrary && (userState?.user?.permissions.canManageLibrary || userState?.user?.permissions.admin)) {
              items.push({
                label: this.t.translate('metadata.viewer.menuAttachToAnotherBook'),
                icon: 'pi pi-link',
                command: () => {
                  this.bookDialogHelperService.openBookFileAttacherDialog(book);
                },
              });
            }

            if (userState?.user?.permissions.canDeleteBook || userState?.user?.permissions.admin) {
              // Delete File Formats submenu - allows deleting individual book format files
              const deleteFormatItems: MenuItem[] = [];
              const hasMultipleFormats = (book.alternativeFormats?.length ?? 0) > 0;

              // Add primary file if it exists
              if (book.primaryFile) {
                const extension = this.getFileExtension(book.primaryFile.filePath);
                const isPrimaryOnly = !hasMultipleFormats;
                const truncatedName = this.truncateFileName(book.primaryFile.fileName, 25);
                deleteFormatItems.push({
                  label: `${truncatedName} (${this.formatFileSize(book.primaryFile)}) [Primary]`,
                  icon: this.getFileIcon(extension),
                  tooltipOptions: {tooltipLabel: book.primaryFile.fileName, tooltipPosition: 'left'},
                  command: () => this.deleteBookFile(book, book.primaryFile!.id, book.primaryFile!.fileName || 'file', true, isPrimaryOnly)
                });
              }

              // Add alternative formats
              if (book.alternativeFormats && book.alternativeFormats.length > 0) {
                book.alternativeFormats.forEach(format => {
                  const extension = this.getFileExtension(format.filePath);
                  const truncatedName = this.truncateFileName(format.fileName, 25);
                  deleteFormatItems.push({
                    label: `${truncatedName} (${this.formatFileSize(format)})`,
                    icon: this.getFileIcon(extension),
                    tooltipOptions: {tooltipLabel: format.fileName, tooltipPosition: 'left'},
                    command: () => this.deleteBookFile(book, format.id, format.fileName || 'file', false, false)
                  });
                });
              }

              if (deleteFormatItems.length > 0) {
                items.push({
                  label: this.t.translate('metadata.viewer.menuDeleteFileFormats'),
                  icon: 'pi pi-file',
                  items: deleteFormatItems
                });
              }

              // Delete Supplementary Files submenu - for non-book files
              if (book.supplementaryFiles && book.supplementaryFiles.length > 0) {
                const deleteSupplementaryItems: MenuItem[] = [];
                book.supplementaryFiles.forEach(file => {
                  const extension = this.getFileExtension(file.filePath);
                  const truncatedName = this.truncateFileName(file.fileName, 25);
                  deleteSupplementaryItems.push({
                    label: `${truncatedName} (${this.formatFileSize(file)})`,
                    icon: this.getFileIcon(extension),
                    tooltipOptions: {tooltipLabel: file.fileName, tooltipPosition: 'left'},
                    command: () => this.deleteAdditionalFile(book.id, file.id, file.fileName || 'file')
                  });
                });

                items.push({
                  label: this.t.translate('metadata.viewer.menuDeleteSupplementaryFiles'),
                  icon: 'pi pi-paperclip',
                  items: deleteSupplementaryItems
                });
              }

              // Delete Book & All Files - deletes the entire book entity
              const allFormats: string[] = [];
              if (book.primaryFile?.fileName) {
                allFormats.push(book.primaryFile.fileName);
              }
              book.alternativeFormats?.forEach(f => {
                if (f.fileName) allFormats.push(f.fileName);
              });
              book.supplementaryFiles?.forEach(f => {
                if (f.fileName) allFormats.push(f.fileName);
              });

              const isPhysical = !hasFiles;
              const fileListMessage = allFormats.length > 0
                ? `\n\nThe following files will be permanently deleted:\n• ${allFormats.join('\n• ')}`
                : '';

              const deleteLabel = isPhysical ? this.t.translate('metadata.viewer.menuDeleteBook') : this.t.translate('metadata.viewer.menuDeleteBookAllFiles');
              const deleteMessage = isPhysical
                ? this.t.translate('metadata.viewer.confirm.deleteBookMessage', { title: book.metadata?.title })
                : this.t.translate('metadata.viewer.confirm.deleteBookAllFilesMessage', { title: book.metadata?.title, fileList: fileListMessage });
              const deleteAcceptLabel = isPhysical ? this.t.translate('common.delete') : this.t.translate('metadata.viewer.confirm.deleteEverythingBtn');

              items.push({
                label: deleteLabel,
                icon: 'pi pi-trash',
                command: () => {
                  this.confirmationService.confirm({
                    message: deleteMessage,
                    header: deleteLabel,
                    icon: 'pi pi-exclamation-triangle',
                    acceptIcon: 'pi pi-trash',
                    rejectIcon: 'pi pi-times',
                    acceptLabel: deleteAcceptLabel,
                    rejectLabel: this.t.translate('common.cancel'),
                    acceptButtonStyleClass: 'p-button-danger',
                    rejectButtonStyleClass: 'p-button-outlined',
                    accept: () => {
                      this.bookService.deleteBooks(new Set([book.id])).subscribe({
                        next: () => {
                          if (this.metadataCenterViewMode === 'route') {
                            this.router.navigate(['/dashboard']);
                          } else {
                            this.dialogRef?.close();
                          }
                        },
                        error: () => {
                        }
                      });
                    }
                  });
                },
              });
            }

            return items;
          })
        )
      )
    );

    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1)
      )
      .subscribe(userState => {
        this.metadataCenterViewMode = userState.user?.userSettings.metadataCenterViewMode ?? 'route';
      });

    this.book$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        filter((book): book is Book => book != null && book.metadata != null)
      )
      .subscribe(book => {
        const metadata = book.metadata;
        this.isAutoFetching = false;
        this.loadBooksInSeriesAndFilterRecommended(metadata!.bookId);
        if (this.quillEditor?.quill) {
          this.quillEditor.quill.root.innerHTML = metadata!.description;
        }
        this.selectedReadStatus = book.readStatus ?? ReadStatus.UNREAD;
      });

    this.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        this.amazonDomain = settings?.metadataProviderSettings?.amazon?.domain ?? 'com';
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['recommendedBooks']) {
      this.originalRecommendedBooks = [...this.recommendedBooks];
      this.withCurrentBook(book => this.filterRecommendations(book));
    }
  }

  private withCurrentBook(callback: (book: Book | null) => void): void {
    this.book$.pipe(take(1)).subscribe(callback);
  }

  private loadBooksInSeriesAndFilterRecommended(bookId: number): void {
    this.bookService.getBooksInSeries(bookId).pipe(
      tap(series => {
        series.sort((a, b) => (a.metadata?.seriesNumber ?? 0) - (b.metadata?.seriesNumber ?? 0));
        this.bookInSeries = series;
        this.originalRecommendedBooks = [...this.recommendedBooks];
      }),
      switchMap(() => this.book$.pipe(take(1))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(book => this.filterRecommendations(book));
  }

  private filterRecommendations(book: Book | null): void {
    if (!this.originalRecommendedBooks) return;
    const bookInSeriesIds = new Set(this.bookInSeries.map(book => book.id));
    this.recommendedBooks = this.originalRecommendedBooks.filter(
      rec => !bookInSeriesIds.has(rec.book.id)
    );
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  read(bookId: number | undefined, reader?: "epub-streaming", bookType?: BookType): void {
    if (bookId) this.bookService.readBook(bookId, reader, bookType);
  }

  isInProgressStatus(): boolean {
    return [ReadStatus.READING, ReadStatus.PAUSED, ReadStatus.RE_READING].includes(this.selectedReadStatus);
  }

  getReadButtonLabel(book: Book): string {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    if (this.isInProgressStatus()) {
      return isAudiobook ? this.t.translate('metadata.viewer.continueBtn') : this.t.translate('metadata.viewer.continueReadingBtn');
    }
    return isAudiobook ? this.t.translate('metadata.viewer.playBtn') : this.t.translate('metadata.viewer.readBtn');
  }

  getReadButtonIcon(book: Book): string {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    return (isAudiobook || this.isInProgressStatus()) ? 'pi pi-play' : 'pi pi-book';
  }

  download(book: Book) {
    this.bookService.downloadFile(book);
  }

  downloadAdditionalFile(book: Book, fileId: number) {
    this.bookService.downloadAdditionalFile(book, fileId);
  }

  // Event handlers for MetadataTabsComponent
  onReadBook(event: ReadEvent): void {
    this.read(event.bookId, event.reader, event.bookType);
  }

  onDownloadBook(event: DownloadEvent): void {
    this.download(event.book);
  }

  onDownloadFile(event: DownloadAdditionalFileEvent): void {
    this.downloadAdditionalFile(event.book, event.fileId);
  }

  onDownloadAllFiles(event: DownloadAllFilesEvent): void {
    this.bookService.downloadAllFiles(event.book);
  }

  onDeleteBookFile(event: DeleteBookFileEvent): void {
    this.deleteBookFile(event.book, event.fileId, event.fileName, event.isPrimary, event.isOnlyFormat);
  }

  onDeleteSupplementaryFile(event: DeleteSupplementaryFileEvent): void {
    this.deleteAdditionalFile(event.bookId, event.fileId, event.fileName);
  }

  deleteAdditionalFile(bookId: number, fileId: number, fileName: string) {
    this.confirmationService.confirm({
      message: this.t.translate('metadata.viewer.confirm.deleteSupplementaryMessage', { fileName }),
      header: this.t.translate('metadata.viewer.confirm.deleteSupplementaryHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      rejectButtonStyleClass: 'p-button-secondary',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookService.deleteAdditionalFile(bookId, fileId).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('metadata.viewer.toast.deleteSupplementarySuccessSummary'),
              detail: this.t.translate('metadata.viewer.toast.deleteSupplementarySuccessDetail', { fileName })
            });
          },
          error: (error) => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('metadata.viewer.toast.deleteSupplementaryErrorSummary'),
              detail: this.t.translate('metadata.viewer.toast.deleteSupplementaryErrorDetail', { error: error.message || 'Unknown error' })
            });
          }
        });
      }
    });
  }

  deleteBookFile(book: Book, fileId: number, fileName: string, isPrimary: boolean, isOnlyFormat: boolean) {
    let message: string;
    let header: string;

    if (isOnlyFormat) {
      message = this.t.translate('metadata.viewer.confirm.deleteOnlyFormatMessage', { fileName });
      header = this.t.translate('metadata.viewer.confirm.deleteOnlyFormatHeader');
    } else if (isPrimary) {
      message = this.t.translate('metadata.viewer.confirm.deletePrimaryFormatMessage', { fileName });
      header = this.t.translate('metadata.viewer.confirm.deletePrimaryFormatHeader');
    } else {
      message = this.t.translate('metadata.viewer.confirm.deleteAltFormatMessage', { fileName });
      header = this.t.translate('metadata.viewer.confirm.deleteAltFormatHeader');
    }

    this.confirmationService.confirm({
      message,
      header,
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptLabel: this.t.translate('metadata.viewer.confirm.deleteFileBtn'),
      rejectLabel: this.t.translate('common.cancel'),
      rejectButtonStyleClass: 'p-button-secondary',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookService.deleteBookFile(book.id, fileId, isPrimary).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('metadata.viewer.toast.deleteFormatSuccessSummary'),
              detail: this.t.translate('metadata.viewer.toast.deleteFormatSuccessDetail', { fileName })
            });
          },
          error: (error) => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('metadata.viewer.toast.deleteFormatErrorSummary'),
              detail: this.t.translate('metadata.viewer.toast.deleteFormatErrorDetail', { error: error.message || 'Unknown error' })
            });
          }
        });
      }
    });
  }

  quickRefresh(bookId: number) {
    this.isAutoFetching = true;

    this.taskHelperService.refreshMetadataTask({
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: [bookId],
    }).subscribe();

    setTimeout(() => {
      this.isAutoFetching = false;
    }, 15000);
  }

  quickSend(bookId: number) {
    this.emailService.emailBookQuick(bookId).subscribe({
      next: () => this.messageService.add({
        severity: 'info',
        summary: this.t.translate('metadata.viewer.toast.quickSendSuccessSummary'),
        detail: this.t.translate('metadata.viewer.toast.quickSendSuccessDetail'),
      }),
      error: (err) => this.messageService.add({
        severity: 'error',
        summary: this.t.translate('metadata.viewer.toast.quickSendErrorSummary'),
        detail: err?.error?.message || this.t.translate('metadata.viewer.toast.quickSendErrorDetail'),
      })
    });
  }

  assignShelf(bookId: number) {
    this.bookDialogHelperService.openShelfAssignerDialog((this.bookService.getBookByIdFromState(bookId) as Book), null);
  }

  updateReadStatus(status: ReadStatus): void {
    if (!status) {
      return;
    }

    this.book$.pipe(take(1)).subscribe(book => {
      if (!book || !book.id) {
        return;
      }

      this.bookService.updateBookReadStatus(book.id, status).subscribe({
        next: (updatedBooks) => {
          this.selectedReadStatus = status;
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('metadata.viewer.toast.readStatusUpdatedSummary'),
            detail: this.t.translate('metadata.viewer.toast.readStatusUpdatedDetail', { status: this.getStatusLabel(status) }),
            life: 2000
          });
        },
        error: (err) => {
          console.error('Failed to update read status:', err);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('metadata.viewer.toast.readStatusFailedSummary'),
            detail: this.t.translate('metadata.viewer.toast.readStatusFailedDetail'),
            life: 3000
          });
        }
      });
    });
  }

  resetProgress(book: Book, type: ResetProgressType): void {
    this.confirmationService.confirm({
      message: this.t.translate('metadata.viewer.confirm.resetProgressMessage', { title: book.metadata?.title }),
      header: this.t.translate('metadata.viewer.confirm.resetProgressHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.bookService.resetProgress(book.id, type).subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('metadata.viewer.toast.progressResetSummary'),
              detail: this.t.translate('metadata.viewer.toast.progressResetDetail'),
              life: 1500
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('metadata.viewer.toast.progressResetFailedSummary'),
              detail: this.t.translate('metadata.viewer.toast.progressResetFailedDetail'),
              life: 1500
            });
          }
        });
      }
    });
  }

  onPersonalRatingChange(book: Book, {value: personalRating}: RatingRateEvent): void {
    this.bookService.updatePersonalRating(book.id, personalRating).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.viewer.toast.ratingSavedSummary'),
          detail: this.t.translate('metadata.viewer.toast.ratingSavedDetail')
        });
      },
      error: err => {
        console.error('Failed to update personal rating:', err);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.viewer.toast.ratingFailedSummary'),
          detail: this.t.translate('metadata.viewer.toast.ratingFailedDetail')
        });
      }
    });
  }

  resetPersonalRating(book: Book): void {
    this.bookService.resetPersonalRating(book.id).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'info',
          summary: this.t.translate('metadata.viewer.toast.ratingResetSummary'),
          detail: this.t.translate('metadata.viewer.toast.ratingResetDetail')
        });
      },
      error: err => {
        console.error('Failed to reset personal rating:', err);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.viewer.toast.ratingResetFailedSummary'),
          detail: this.t.translate('metadata.viewer.toast.ratingResetFailedDetail')
        });
      }
    });
  }

  goToAuthorBooks(author: string): void {
    this.handleMetadataClick('author', author);
  }

  goToCategory(category: string): void {
    this.handleMetadataClick('category', category);
  }

  goToMood(mood: string): void {
    this.handleMetadataClick('mood', mood);
  }

  goToTag(tag: string): void {
    this.handleMetadataClick('tag', tag);
  }

  goToSeries(seriesName: string): void {
    const encodedSeriesName = encodeURIComponent(seriesName);
    this.router.navigate(['/series', encodedSeriesName]);
  }

  goToPublisher(publisher: string): void {
    this.handleMetadataClick('publisher', publisher);
  }

  goToLibrary(libraryId: number): void {
    if (this.metadataCenterViewMode === 'dialog') {
      this.dialogRef?.close();
      setTimeout(() => this.router.navigate(['/library', libraryId, 'books']), 200);
    } else {
      this.router.navigate(['/library', libraryId, 'books']);
    }
  }

  goToPublishedYear(publishedDate: string): void {
    const year = this.extractYear(publishedDate);
    if (year) {
      this.handleMetadataClick('publishedDate', year);
    }
  }

  goToLanguage(language: string): void {
    this.handleMetadataClick('language', language);
  }

  goToFileType(filePath: string | undefined): void {
    const fileType = this.getFileExtension(filePath);
    if (fileType) {
      let filterValue = fileType.toUpperCase();
      if (['CBR', 'CBZ', 'CB7', 'CBT'].includes(filterValue)) {
        filterValue = 'CBX';
      }
      this.handleMetadataClick('bookType', filterValue);
    }
  }

  goToReadStatus(status: ReadStatus): void {
    this.handleMetadataClick('readStatus', status);
  }

  goToPageCountRange(pageCount: number): void {
    const range = pageCountRanges.find(r => pageCount >= r.min && pageCount < r.max);
    if (range) {
      this.handleMetadataClick('pageCount', range.id.toString());
    }
  }

  goToFileSizeRange(fileSizeKb: number): void {
    const range = fileSizeRanges.find(r => fileSizeKb >= r.min && fileSizeKb < r.max);
    if (range) {
      this.handleMetadataClick('fileSize', range.id.toString());
    }
  }

  goToMatchScoreRange(score: number): void {
    const normalizedScore = score > 1 ? score / 100 : score;
    const range = matchScoreRanges.find(r => normalizedScore >= r.min && normalizedScore < r.max);
    if (range) {
      this.handleMetadataClick('matchScore', range.id.toString());
    }
  }

  goToAgeRating(ageRating: number): void {
    this.handleMetadataClick('ageRating', ageRating.toString());
  }

  goToContentRating(contentRating: string): void {
    this.handleMetadataClick('contentRating', contentRating);
  }

  getAgeRatingLabel(ageRating: number | null | undefined): string {
    if (ageRating == null) return '-';
    const match = AGE_RATING_OPTIONS.find(r => r.id === ageRating);
    return match?.label ?? `${ageRating}+`;
  }

  getContentRatingLabel(contentRating: string | null | undefined): string {
    if (!contentRating) return '-';
    return CONTENT_RATING_LABELS[contentRating] ?? contentRating;
  }

  private extractYear(dateString: string | null | undefined): string | null {
    if (!dateString) return null;
    const yearMatch = dateString.match(/\d{4}/);
    return yearMatch ? yearMatch[0] : null;
  }

  private navigateToFilteredBooks(filterKey: string, filterValue: string): void {
    this.router.navigate(['/all-books'], {
      queryParams: {
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: `${filterKey}:${encodeURIComponent(filterValue)}`
      }
    });
  }

  private handleMetadataClick(filterKey: string, filterValue: string): void {
    if (this.metadataCenterViewMode === 'dialog') {
      this.dialogRef?.close();
      setTimeout(() => this.navigateToFilteredBooks(filterKey, filterValue), 200);
    } else {
      this.navigateToFilteredBooks(filterKey, filterValue);
    }
  }

  isMetadataFullyLocked(metadata: BookMetadata): boolean {
    const lockedKeys = Object.keys(metadata).filter(k => k.endsWith('Locked'));
    return lockedKeys.length > 0 && lockedKeys.every(k => metadata[k] === true);
  }

  formatFileSize(fileInfo: FileInfo | null | undefined): string {
    const sizeKb = fileInfo?.fileSizeKb;
    if (sizeKb == null) return '-';

    const units = ['KB', 'MB', 'GB', 'TB'];
    let size = sizeKb;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    const decimals = size >= 100 ? 0 : size >= 10 ? 1 : 2;
    return `${size.toFixed(decimals)} ${units[unitIndex]}`;
  }

  truncateFileName(fileName: string | undefined, maxLength: number = 30): string {
    if (!fileName) return '';
    if (fileName.length <= maxLength) return fileName;

    const lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex === -1) {
      // No extension - just truncate
      return fileName.substring(0, maxLength - 3) + '...';
    }

    const extension = fileName.substring(lastDotIndex);
    const nameWithoutExt = fileName.substring(0, lastDotIndex);
    const availableLength = maxLength - extension.length - 3; // 3 for "..."

    if (availableLength <= 0) {
      return '...' + extension;
    }

    return nameWithoutExt.substring(0, availableLength) + '...' + extension;
  }

  getProgressPercent(book: Book): number | null {
    if (book.epubProgress?.percentage != null) {
      return book.epubProgress.percentage;
    }
    if (book.pdfProgress?.percentage != null) {
      return book.pdfProgress.percentage;
    }
    if (book.cbxProgress?.percentage != null) {
      return book.cbxProgress.percentage;
    }
    if (book.audiobookProgress?.percentage != null) {
      return book.audiobookProgress.percentage;
    }
    return null;
  }

  getKoProgressPercent(book: Book): number | null {
    if (book.koreaderProgress?.percentage != null) {
      return book.koreaderProgress.percentage;
    }
    return null;
  }

  getKoboProgressPercent(book: Book): number | null {
    if (book.koboProgress?.percentage != null) {
      return book.koboProgress.percentage;
    }
    return null;
  }

  getProgressCount(book: Book): number {
    let count = 0;
    if (this.getProgressPercent(book) !== null) count++;
    if (this.getKoProgressPercent(book) !== null) count++;
    if (this.getKoboProgressPercent(book) !== null) count++;
    return count;
  }

  getFileExtension(filePath?: string): string | null {
    if (!filePath) return null;
    const parts = filePath.split('.');
    if (parts.length < 2) return null;
    return parts.pop()?.toUpperCase() || null;
  }

  getUniqueAlternativeFormatTypes(book: Book): BookType[] {
    if (!book.alternativeFormats?.length) return [];
    const primaryType = book.primaryFile?.bookType;
    const uniqueTypes = new Set<BookType>();
    for (const format of book.alternativeFormats) {
      if (format.bookType && format.bookType !== primaryType) {
        uniqueTypes.add(format.bookType);
      }
    }
    return [...uniqueTypes];
  }

  getDisplayFormat(bookFile?: BookFile | null): string | null {
    if (bookFile?.extension) {
      return bookFile.extension.toUpperCase();
    }
    return this.getFileExtension(bookFile?.filePath);
  }

  getUniqueAlternativeFormats(book: Book): string[] {
    if (!book.alternativeFormats?.length) return [];
    const primaryFormat = this.getDisplayFormat(book.primaryFile);
    const uniqueFormats = new Set<string>();
    for (const format of book.alternativeFormats) {
      const formatType = this.getDisplayFormat(format);
      if (formatType && formatType !== primaryFormat) {
        uniqueFormats.add(formatType);
      }
    }
    return [...uniqueFormats];
  }

  getFileIcon(fileType: string | null): string {
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
      case 'audiobook':
      case 'm4b':
      case 'm4a':
      case 'mp3':
        return 'pi pi-headphones';
      default:
        return 'pi pi-file';
    }
  }

  getFileTypeBgColor(fileType: string | null | undefined): string {
    if (!fileType) return 'var(--p-gray-500)';
    const type = fileType.toLowerCase();
    return `var(--book-type-${type}-color, var(--p-gray-500))`;
  }

  getStarColorScaled(rating?: number | null, maxScale: number = 5): string {
    if (rating == null) {
      return 'rgb(203, 213, 225)';
    }
    const normalized = rating / maxScale;
    if (normalized >= 0.9) {
      return 'rgb(34, 197, 94)';
    } else if (normalized >= 0.75) {
      return 'rgb(52, 211, 153)';
    } else if (normalized >= 0.6) {
      return 'rgb(234, 179, 8)';
    } else if (normalized >= 0.4) {
      return 'rgb(249, 115, 22)';
    } else {
      return 'rgb(239, 68, 68)';
    }
  }


  getMatchScoreColor(score: number): TagColor {
    if (score >= 0.95) return 'emerald';
    if (score >= 0.90) return 'green';
    if (score >= 0.80) return 'lime';
    if (score >= 0.70) return 'yellow';
    if (score >= 0.60) return 'amber';
    if (score >= 0.50) return 'orange';
    if (score >= 0.40) return 'red';
    if (score >= 0.30) return 'rose';
    return 'pink';
  }

  getStatusColor(status: string | null | undefined): TagColor {
    const normalized = status?.toUpperCase() ?? '';
    switch (normalized) {
      case 'UNREAD':
        return 'gray';
      case 'PAUSED':
        return 'zinc';
      case 'READING':
        return 'blue';
      case 'RE_READING':
        return 'indigo';
      case 'READ':
        return 'green';
      case 'PARTIALLY_READ':
        return 'yellow';
      case 'ABANDONED':
        return 'red';
      case 'WONT_READ':
        return 'pink';
      default:
        return 'gray';
    }
  }

  getProgressColor(progress: number | null | undefined): TagColor {
    if (progress == null) return 'gray';
    return 'blue';
  }

  getKoProgressColor(progress: number | null | undefined): TagColor {
    if (progress == null) return 'gray';
    return 'amber';
  }

  getKOReaderPercentage(book: Book): number | null {
    const p = book?.koreaderProgress?.percentage;
    return p != null ? Math.round(p * 10) / 10 : null;
  }

  getRatingTooltip(book: Book, source: 'amazon' | 'goodreads' | 'hardcover' | 'lubimyczytac' | 'ranobedb' | 'audible'): string {
    const meta = book?.metadata;
    if (!meta) return '';

    switch (source) {
      case 'amazon':
        return meta.amazonRating != null
          ? `★ ${meta.amazonRating} | ${meta.amazonReviewCount?.toLocaleString() ?? '0'} reviews`
          : '';
      case 'goodreads':
        return meta.goodreadsRating != null
          ? `★ ${meta.goodreadsRating} | ${meta.goodreadsReviewCount?.toLocaleString() ?? '0'} reviews`
          : '';
      case 'hardcover':
        return meta.hardcoverRating != null
          ? `★ ${meta.hardcoverRating} | ${meta.hardcoverReviewCount?.toLocaleString() ?? '0'} reviews`
          : '';
      case 'lubimyczytac':
        return meta.lubimyczytacRating != null
          ? `★ ${meta.lubimyczytacRating}`
          : '';
      case 'ranobedb':
        return meta.ranobedbRating != null
          ? `★ ${meta.ranobedbRating}`
          : '';
      case 'audible':
        return meta.audibleRating != null
          ? `★ ${meta.audibleRating} | ${meta.audibleReviewCount?.toLocaleString() ?? '0'} reviews`
          : '';
      default:
        return '';
    }
  }

  getRatingPercent(rating: number | null | undefined): number {
    if (rating == null) return 0;
    return Math.round((rating / 5) * 100);
  }

  readStatusMenuItems = this.readStatusOptions.map(option => ({
    label: this.t.translate(option.labelKey),
    command: () => this.updateReadStatus(option.value)
  }));

  getStatusLabel(value: string): string {
    const option = this.readStatusOptions.find(o => o.value === value);
    return option ? this.t.translate(option.labelKey).toUpperCase() : 'UNSET';
  }


  formatDate(dateString: string | undefined): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  toggleDateFinishedEdit(book: Book): void {
    if (this.isEditingDateFinished) {
      this.isEditingDateFinished = false;
      this.editDateFinished = null;
    } else {
      this.isEditingDateFinished = true;
      this.editDateFinished = book.dateFinished ? new Date(book.dateFinished) : new Date();
    }
  }

  saveDateFinished(book: Book): void {
    if (!book) return;

    const dateToSave = this.editDateFinished ? this.editDateFinished.toISOString() : null;

    this.bookService.updateDateFinished(book.id, dateToSave).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.viewer.toast.dateUpdatedSummary'),
          detail: this.t.translate('metadata.viewer.toast.dateUpdatedDetail'),
          life: 1500
        });
        this.isEditingDateFinished = false;
        this.editDateFinished = null;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.viewer.toast.dateUpdateFailedSummary'),
          detail: this.t.translate('metadata.viewer.toast.dateUpdateFailedDetail'),
          life: 3000
        });
      }
    });
  }

  cancelDateFinishedEdit(): void {
    this.isEditingDateFinished = false;
    this.editDateFinished = null;
  }

  openFileMoverDialog(bookId: number): void {
    this.bookDialogHelperService.openFileMoverDialog(new Set([bookId]));
  }

  protected readonly ResetProgressTypes = ResetProgressTypes;
  protected readonly ReadStatus = ReadStatus;

  canNavigatePrevious(): boolean {
    return this.bookNavigationService.canNavigatePrevious();
  }

  canNavigateNext(): boolean {
    return this.bookNavigationService.canNavigateNext();
  }

  navigatePrevious(): void {
    const prevBookId = this.bookNavigationService.getPreviousBookId();
    if (prevBookId) {
      this.navigateToBook(prevBookId);
    }
  }

  navigateNext(): void {
    const nextBookId = this.bookNavigationService.getNextBookId();
    if (nextBookId) {
      this.navigateToBook(nextBookId);
    }
  }

  private navigateToBook(bookId: number): void {
    this.bookNavigationService.updateCurrentBook(bookId);
    if (this.metadataCenterViewMode === 'route') {
      this.router.navigate(['/book', bookId], {
        queryParams: {tab: 'view'}
      });
    } else {
      this.metadataHostService.switchBook(bookId);
    }
  }

  getNavigationPosition(): string {
    const position = this.bookNavigationService.getCurrentPosition();
    return position ? this.t.translate('metadata.viewer.navigationPosition', { current: position.current, total: position.total }) : '';
  }

  hasDigitalFile(book: Book): boolean {
    return !!book?.primaryFile;
  }

  hasAnyFiles(book: Book): boolean {
    return !!book?.primaryFile || (book?.alternativeFormats?.length ?? 0) > 0;
  }

  isPhysicalBook(book: Book): boolean {
    return !this.hasAnyFiles(book);
  }

  getBookCoverUrl(book: Book): string {
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    return isAudiobook
      ? this.urlHelper.getAudiobookCoverUrl(book.id, book.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getCoverUrl(book.id, book.metadata?.coverUpdatedOn);
  }

  // Comic metadata helpers
  isComicBook(book: Book): boolean {
    return book?.primaryFile?.bookType === 'CBX';
  }

  hasComicMetadata(book: Book): boolean {
    const comic = book?.metadata?.comicMetadata;
    if (!comic) return false;
    return !!(
      comic.issueNumber ||
      comic.volumeName ||
      comic.storyArc ||
      comic.characters?.length ||
      comic.teams?.length ||
      comic.locations?.length ||
      comic.pencillers?.length ||
      comic.inkers?.length ||
      comic.colorists?.length ||
      comic.letterers?.length ||
      comic.coverArtists?.length ||
      comic.editors?.length ||
      comic.manga ||
      comic.blackAndWhite ||
      comic.webLink ||
      comic.notes
    );
  }

  hasAnyCreators(comic: ComicMetadata): boolean {
    return !!(
      comic.pencillers?.length ||
      comic.inkers?.length ||
      comic.colorists?.length ||
      comic.letterers?.length ||
      comic.coverArtists?.length ||
      comic.editors?.length
    );
  }

  formatWebLink(url: string): string {
    if (!url) return '';
    try {
      const parsed = new URL(url);
      const path = parsed.pathname.length > 30
        ? parsed.pathname.substring(0, 30) + '...'
        : parsed.pathname;
      return parsed.hostname + path;
    } catch {
      return url.length > 50 ? url.substring(0, 50) + '...' : url;
    }
  }

  goToCharacter(character: string): void {
    this.handleMetadataClick('comicCharacter', character);
  }

  goToTeam(team: string): void {
    this.handleMetadataClick('comicTeam', team);
  }

  goToLocation(location: string): void {
    this.handleMetadataClick('comicLocation', location);
  }

  goToCreator(name: string, role: string): void {
    this.handleMetadataClick('comicCreator', `${name}:${role}`);
  }

  // Audiobook metadata helpers
  isAudiobook(book: Book): boolean {
    return book?.primaryFile?.bookType === 'AUDIOBOOK';
  }

  hasAudiobookMetadata(book: Book): boolean {
    const audio = book?.metadata?.audiobookMetadata;
    if (!audio) return false;
    return !!(
      audio.durationSeconds ||
      audio.bitrate ||
      audio.sampleRate ||
      audio.channels ||
      audio.codec ||
      audio.chapterCount ||
      book.metadata?.narrator ||
      book.metadata?.abridged != null
    );
  }

  formatDuration(seconds: number): string {
    if (!seconds) return '-';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  }

  formatSampleRate(sampleRate: number): string {
    if (!sampleRate) return '-';
    return `${(sampleRate / 1000).toFixed(1)} kHz`;
  }

  getChannelLabel(channels: number): string {
    switch (channels) {
      case 1:
        return this.t.translate('metadata.viewer.channelMono');
      case 2:
        return this.t.translate('metadata.viewer.channelStereo');
      default:
        return this.t.translate('metadata.viewer.channelMultiple', { count: channels });
    }
  }

  goToNarrator(narrator: string): void {
    this.handleMetadataClick('narrator', narrator);
  }
}
