import {inject, Injectable, Type} from '@angular/core';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {DialogLauncherService, DialogSize, DialogStyle} from '../../../../shared/services/dialog-launcher.service';
import {MetadataRefreshType} from '../../../metadata/model/request/metadata-refresh-type.enum';
import {Book} from '../../model/book.model';

@Injectable({providedIn: 'root'})
export class BookDialogHelperService {

  private dialogLauncherService = inject(DialogLauncherService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  /** In-flight dialog opens keyed by intent, to dedupe double-clicks while a chunk loads. */
  private inflightOpens = new Map<string, Promise<DynamicDialogRef | null>>();

  private openDialog(component: Type<unknown>, options: object): DynamicDialogRef | null {
    return this.dialogLauncherService.openDialog(component, options);
  }

  private lazyOpen(
    key: string,
    importer: () => Promise<Type<unknown>>,
    options: object,
  ): Promise<DynamicDialogRef | null> {
    const existing = this.inflightOpens.get(key);
    if (existing) {
      return existing;
    }

    const promise = importer()
      .then(component => this.openDialog(component, options))
      .catch(error => {
        console.error(`[BookDialogHelper] Failed to load chunk for "${key}"`, error);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.chunkLoadFailedSummary'),
          detail: this.t.translate('common.chunkLoadFailedDetail'),
          life: 6000,
        });
        return null;
      })
      .finally(() => {
        this.inflightOpens.delete(key);
      });

    this.inflightOpens.set(key, promise);
    return promise;
  }

  openBookDetailsDialog(bookId: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `bookDetails:${bookId}`,
      async () => (await import('../../../metadata/component/book-metadata-center/book-metadata-center.component')).BookMetadataCenterComponent,
      {
        showHeader: false,
        styleClass: `book-details-dialog ${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
        data: {
          bookId: bookId,
        },
      },
    );
  }

  openShelfAssignerDialog(book: Book | null, bookIds: Set<number> | null): Promise<DynamicDialogRef | null> {
    const data: { isMultiBooks: boolean; book?: Book; bookIds?: Set<number> } = {
      isMultiBooks: false,
    };
    if (book !== null) {
      data.book = book;
    } else if (bookIds !== null) {
      data.isMultiBooks = true;
      data.bookIds = bookIds;
    } else {
      return Promise.resolve(null);
    }
    const key = book !== null ? `shelfAssigner:book:${book.id}` : `shelfAssigner:bulk:${[...(bookIds ?? [])].sort().join(',')}`;
    return this.lazyOpen(
      key,
      async () => (await import('../shelf-assigner/shelf-assigner.component')).ShelfAssignerComponent,
      {
        showHeader: false,
        data: data,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openShelfCreatorDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'shelfCreator',
      async () => (await import('../shelf-creator/shelf-creator.component')).ShelfCreatorComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openLockUnlockMetadataDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `lockUnlockMetadata:${[...bookIds].sort().join(',')}`,
      async () => (await import('./lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component')).LockUnlockMetadataDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
        data: {
          bookIds: Array.from(bookIds),
        },
      },
    );
  }

  openMetadataRefreshDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `metadataRefresh:${[...bookIds].sort().join(',')}`,
      async () => (await import('../../../metadata/component/multi-book-metadata-fetch/multi-book-metadata-fetch-component')).MultiBookMetadataFetchComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
        data: {
          bookIds: Array.from(bookIds),
          metadataRefreshType: MetadataRefreshType.BOOKS,
        },
      },
    );
  }

  openBulkMetadataEditDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `bulkMetadataEdit:${[...bookIds].sort().join(',')}`,
      async () => (await import('../../../metadata/component/bulk-metadata-update/bulk-metadata-update-component')).BulkMetadataUpdateComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
        data: {
          bookIds: Array.from(bookIds),
        },
      },
    );
  }

  openMultibookMetadataEditorDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `multiBookMetadataEditor:${[...bookIds].sort().join(',')}`,
      async () => (await import('../../../metadata/component/multi-book-metadata-editor/multi-book-metadata-editor-component')).MultiBookMetadataEditorComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
        data: {
          bookIds: Array.from(bookIds),
        },
      },
    );
  }

  openFileMoverDialog(bookIds: Set<number>): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `fileMover:${[...bookIds].sort().join(',')}`,
      async () => (await import('../../../../shared/components/file-mover/file-mover-component')).FileMoverComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
        maximizable: true,
        data: {
          bookIds: Array.from(bookIds),
        },
      },
    );
  }

  openCustomSendDialog(book: Book): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `customSend:${book.id}`,
      async () => (await import('../book-sender/book-sender.component')).BookSenderComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
        data: {
          book: book,
        },
      },
    );
  }

  openCoverSearchDialog(bookId: number, coverType?: 'ebook' | 'audiobook'): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `coverSearch:${bookId}:${coverType ?? 'ebook'}`,
      async () => (await import('../../../metadata/component/cover-search/cover-search.component')).CoverSearchComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
        data: {
          bookId: bookId,
          coverType: coverType,
        },
      },
    );
  }

  openAdditionalFileUploaderDialog(book: Book): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `additionalFileUploader:${book.id}`,
      async () => (await import('../additional-file-uploader/additional-file-uploader.component')).AdditionalFileUploaderComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          book: book,
        },
      },
    );
  }

  openBookFileAttacherDialog(sourceBook: Book): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `bookFileAttacher:${sourceBook.id}`,
      async () => (await import('../book-file-attacher/book-file-attacher.component')).BookFileAttacherComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          sourceBook: sourceBook,
        },
      },
    );
  }

  openBulkBookFileAttacherDialog(sourceBooks: Book[]): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `bulkBookFileAttacher:${sourceBooks.map(b => b.id).sort().join(',')}`,
      async () => (await import('../book-file-attacher/book-file-attacher.component')).BookFileAttacherComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          sourceBooks: sourceBooks,
        },
      },
    );
  }

  openDuplicateMergerDialog(libraryId: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `duplicateMerger:${libraryId}`,
      async () => (await import('../duplicate-merger/duplicate-merger.component')).DuplicateMergerComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
        data: {
          libraryId: libraryId,
        },
      },
    );
  }

  openAddPhysicalBookDialog(libraryId?: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `addPhysicalBook:${libraryId ?? 'none'}`,
      async () => (await import('../add-physical-book-dialog/add-physical-book-dialog.component')).AddPhysicalBookDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
        data: {
          libraryId: libraryId,
        },
      },
    );
  }

  openBulkIsbnImportDialog(libraryId?: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `bulkIsbnImport:${libraryId ?? 'none'}`,
      async () => (await import('../bulk-isbn-import-dialog/bulk-isbn-import-dialog.component')).BulkIsbnImportDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
        data: {
          libraryId: libraryId,
        },
      },
    );
  }
}
